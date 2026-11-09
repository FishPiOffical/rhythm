/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.processor;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.http.handler.RouteHandler;
import org.b3log.latke.http.handler.RouteResolution;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.symphony.repository.ApiV3AppLoginRequestRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.service.ApiV3ClientDisableService;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Json;
import org.json.JSONObject;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static org.b3log.symphony.processor.ApiV3ClientManagementTest.*;

/** 在临时 SQLite 上执行实际逻辑停用 SQL、生产管理员校验及路由，并覆盖随包 JDBC 的故障。 */
public final class ApiV3ClientDisableTest {
    static final String BASE = "/api/v3/admin/oauth/clients/";
    static final class DisableFixture implements AutoCloseable {
        final Fixture f = new Fixture();
        final ApiV3ClientDisableService service = new ApiV3ClientDisableService();
        final ApiV3ClientDisableProcessor processor = new ApiV3ClientDisableProcessor();
        final ApiV3RequestRepository receipts = new ApiV3RequestRepository() {
            @Override public boolean hasTransactionBegun() { return f.active(); }
            @Override public Transaction beginTransaction() {
                final Transaction root = f.receipts.beginTransaction();
                try {
                    final Connection actual = f.tx.getConnection();
                    final Connection observed = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        try {
                            final Object result = method.invoke(actual, args);
                            if (!method.getName().equals("prepareStatement")) return result;
                            final String sql = (String) args[0];
                            final PreparedStatement statement = (PreparedStatement) result;
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                                try {
                                    final Object value = m.invoke(statement, a);
                                    if (m.getName().equals("executeUpdate")) {
                                        if (f.faults.contains("observer")) equal(snapshot(), before);
                                        if (sql.startsWith("UPDATE `clients`")) f.fault("client.update.after");
                                        if (sql.startsWith("UPDATE `tokens`")) f.fault("token.update.after");
                                    }
                                    return value;
                                } catch (InvocationTargetException error) { throw error.getCause(); }
                            });
                        } catch (InvocationTargetException error) { throw error.getCause(); }
                    });
                    inject(f.tx, f.tx.getClass(), "connection", observed);
                    return root;
                } catch (Exception error) { if (root.isActive()) root.rollback(); throw new IllegalStateException(error); }
            }
            @Override public JSONObject getForUpdate(String id) throws RepositoryException { return f.receipts.getForUpdate(id); }
            @Override public String add(JSONObject row) throws RepositoryException { return f.receipts.add(row); }
            @Override public void update(String id, JSONObject row, String... names) throws RepositoryException { f.receipts.update(id,row,names); }
        };
        String before;
        DisableFixture() throws Exception {
            exec(f.writer,"CREATE TABLE app_logins(oId TEXT PRIMARY KEY,state TEXT)");
            exec(f.writer,"INSERT INTO app_logins VALUES('pending','APP_CONFIRMED'),('done','CONSUMED'),('denied','DENIED')");
            final var appLogins=new ApiV3AppLoginRequestRepository(){@Override public String getName(){return "app_logins";}@Override public boolean hasTransactionBegun(){return f.active();}};
            inject(service,ApiV3ClientDisableService.class,"appLogins",appLogins);

            exec(f.writer, "ALTER TABLE tokens ADD COLUMN updatedAt BIGINT DEFAULT 0");
            exec(f.writer, "INSERT INTO clients VALUES('3','other_app','Other','content.read',0,'PRIVATE')");
            exec(f.writer, "INSERT INTO redirects VALUES('31','external_app','https://app.example/callback',0,'PRIVATE')");
            for (int i=0;i<180;i++) exec(f.writer, "INSERT INTO tokens VALUES(?,?,?,?,?,?,?, ?,?)", "20"+i,
                    String.valueOf(i%3+1), "external_app", i%2==0?"access":"refresh", i%4, System.currentTimeMillis()+1000000,
                    "content.read", "PRIVATE-target", 0);
            exec(f.writer, "INSERT INTO tokens VALUES('4000','2','external_app','access',NULL,9999999999999,'content.read','PRIVATE-null',0)");
            exec(f.writer, "INSERT INTO tokens VALUES('5000','1','other_app','access',0,9999999999999,'content.read','PRIVATE-other',7)");
            inject(service,ApiV3ClientDisableService.class,"management",f.service);
            inject(service,ApiV3ClientDisableService.class,"clients",f.clients);
            inject(service,ApiV3ClientDisableService.class,"tokens",f.tokens);
            inject(service,ApiV3ClientDisableService.class,"requests",receipts);
            inject(processor,ApiV3ClientDisableProcessor.class,"service",service);
            inject(processor,ApiV3ClientDisableProcessor.class,"management",f.service);
            ApiV3ClientDisableProcessor.registerRoutes(processor,f.auth,new org.b3log.symphony.processor.middleware.ApiV3WriteMidware());
            Dispatcher.mapping();
            before=snapshot();
        }
        String snapshot() throws Exception {
            return query(f.reader,"SELECT clientId,status FROM clients ORDER BY clientId")+"|"
                    +query(f.reader,"SELECT oId,userId,clientId,tokenType,state,updatedAt FROM tokens ORDER BY oId")+"|"
                    +query(f.reader,"SELECT * FROM redirects ORDER BY oId")+"|"+query(f.reader,"SELECT * FROM requests ORDER BY oId");
        }
        JSONObject disable(String id,String key) throws Exception { return service.disable(f.identity("11"),id,key); }
        JSONObject http(String id,String token,String body,String key,Map<String,String> params) throws Exception {
            final byte[] bytes=body==null?new byte[0]:body.getBytes(StandardCharsets.UTF_8);
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.DELETE,BASE+id,Unpooled.wrappedBuffer(bytes));
            var response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
            try {
                raw.headers().set("Cookie","PRIVATE-fake-admin");
                if (body!=null) raw.headers().set("Content-Type","application/json");
                if (token!=null) raw.headers().set("Authorization","Bearer "+token);
                if (key!=null) raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw){@Override public byte[] getBytes(){return bytes;}@Override public String getRemoteAddr(){return "192.0.2.18";}};
                request.setParams(new HashMap<>(params));var c=new RequestContext(request,new Response(null,response));
                c.attr("user",new JSONObject().put("oId","1").put("userRole","adminRole"));
                c.addHandler(new RouteHandler());c.addHandler(context->{var match=(RouteResolution)context.attr("MATCH_RESULT");try{match.getContextHandlerMeta().getInvokeHolder().invoke(processor,context);}catch(ReflectiveOperationException error){throw new AssertionError(error);}});c.handle();
                var result=new JSONObject().put("status",c.attr("test.status"));
                if(c.attr("test.data")!=null)result.put("data",c.attr("test.data"));
                if(c.attr("test.code")!=null)result.put("code",c.attr("test.code"));
                if(result.getInt("status")!=401&&!"14".equals(token)){equal(response.headers().get("Cache-Control"),"no-store");equal(response.headers().get("Vary"),"Authorization");}
                check(!result.toString().contains("PRIVATE"),"no confidential information in reply");return result;
            } finally { raw.release(); }
        }
        public void close()throws Exception{f.close();}
    }
    public static void main(String[] args) throws Exception {
        var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        try (var x = new DisableFixture()) {
            for(String token:Arrays.asList(null,"invalid"))equal(x.http("external_app",token,null,"bad",Map.of("apiKey","fake")).getInt("status"),401);
            for(String token:List.of("12","13","14"))equal(x.http("external_app",token,null,"bad",Map.of()).getInt("status"),403);
            equal(x.snapshot(),x.before);
            var result=data(x.http("external_app","11",null,"disable",Map.of("clientId","website_v3","userId","2")));
            equal(result.keySet(), Set.of("clientId", "status"));
            equal(result.getString("clientId"), "external_app");
            equal(result.getInt("status"), 1);
            equal(query(x.f.reader,"SELECT status FROM clients WHERE clientId='external_app'").getFirst().getInt("status"),1);
            equal(query(x.f.reader,"SELECT * FROM tokens WHERE clientId='external_app' AND (state<>2 OR state IS NULL)").size(),0);
            equal(query(x.f.reader,"SELECT * FROM tokens WHERE clientId='external_app'").size(),182);
            equal(query(x.f.reader,"SELECT state,updatedAt FROM tokens WHERE oId='5000'").getFirst().getInt("state"),0);
            equal(query(x.f.reader,"SELECT state FROM tokens WHERE oId='11'").getFirst().getInt("state"),0);
            equal(query(x.f.reader,"SELECT * FROM redirects WHERE clientId='external_app'").size(),1);
            check(x.f.clientNamespaceLocked,"guard acquired sorted client namespace before user/token/receipt");
            check(!x.f.active()&&JdbcRepository.TX.get()==null,"committed root disposed");
        }
        System.out.println("PASS 1 actual DELETE route, current first-party admin and settings.write; fake identities fail, complete client-bound revocation exceeds 50 rows across users/types/states and preserves other clients/callbacks");
        try(var x=new DisableFixture()){
            var result = x.disable("external_app", "key");
            String after = x.snapshot();
            equal(ApiV3Json.canonical(x.disable("external_app", "key")), ApiV3Json.canonical(result));
            equal(x.snapshot(), after);
            denied(409, () -> x.disable("other_app", "key"));
            equal(x.snapshot(), after);
            exec(x.f.writer,"DELETE FROM clients WHERE clientId='external_app'");
            equal(x.disable("external_app","key").getString("clientId"),"external_app");
            denied(404,()->x.disable("external_app","new"));
            equal(query(x.f.reader,"SELECT * FROM requests").size(),1);
        }
        System.out.println("PASS 2 deterministic disable receipt replays without writes, target changes conflict and deleted-target old replay does not recreate; new-key missing target404 rolls back receipt");
        try(var x=new DisableFixture()){
            exec(x.f.writer,"UPDATE clients SET status=1 WHERE clientId='external_app'");
            data(x.http("external_app","11","{}","inactive",Map.of()));
            equal(query(x.f.reader,"SELECT * FROM tokens WHERE clientId='external_app' AND state<>2").size(),0);
            var beforeTokens=query(x.f.reader,"SELECT state,updatedAt FROM tokens WHERE clientId='external_app' ORDER BY oId").toString();
            data(x.http("external_app","11",null,"inactive-again",Map.of()));
            equal(query(x.f.reader,"SELECT state,updatedAt FROM tokens WHERE clientId='external_app' ORDER BY oId").toString(),beforeTokens);
            equal(query(x.f.reader,"SELECT * FROM requests").size(),2);
            data(x.http("external_app","11"," ".repeat(1022)+"{}","body-1024",Map.of()));
            equal(query(x.f.reader,"SELECT * FROM requests").size(),3);
        }
        System.out.println("PASS 3 already-disabled target succeeds, revokes residual grants, and a fresh repeat leaves revoked timestamps unchanged while producing its own receipt");
        try(var x=new DisableFixture()){
            for(String id:List.of("website_v3","missing"))equal(x.http(id,"11",null,"target",Map.of()).getInt("status"),404);
            for(String id:List.of("x'OR1=1","a".repeat(65),"",String.valueOf((char)0xD800)))denied(422,()->x.disable(id,"input"));
            for(String body:List.of("{\"status\":1}","{\"clientId\":\"other_app\"}","{\"userId\":\"2\"}","{\"enabled\":false}"))equal(x.http("external_app","11",body,"input",Map.of()).getInt("status"),422);
            equal(x.http("external_app","11","{\"a\":1,\"a\":2}","input",Map.of()).getInt("status"),400);
            equal(x.http("external_app","11",null,null,Map.of()).getInt("status"),422);
            equal(x.http("external_app","11"," ".repeat(1025),"big-body",Map.of()).getInt("status"),413);
            equal(x.http("external_app","11","{\"a\":\""+"鱼".repeat(400)+"\"}","utf8-body",Map.of()).getInt("status"),413);
            equal(x.snapshot(),x.before);
            exec(x.f.writer, "UPDATE clients SET status=9 WHERE clientId='external_app'");
            denied(503, () -> x.disable("external_app", "bad-state"));
            equal(query(x.f.reader,"SELECT * FROM requests").size(),0);
        }
        System.out.println("PASS 4 protected website/missing targets, bounded path and empty-object body/key validation reject before mutation; malformed stored status503 has no partial receipt");
        try(var x=new DisableFixture()){
            x.disable("external_app", "done");
            String before = x.snapshot();
            exec(x.f.writer, "UPDATE user SET userRole='defaultRole' WHERE oId='1'");
            denied(403, () -> x.disable("external_app", "done"));
            equal(x.snapshot(), before);
        }
        try(var x=new DisableFixture()){
            x.f.beforeBegin=()->exec(x.f.writer,"UPDATE tokens SET state=2 WHERE oId='11'");
            equal(x.http("external_app","11",null,"revoked-inflight",Map.of()).getInt("status"),403);
            equal(query(x.f.reader,"SELECT status FROM clients WHERE clientId='external_app'").getFirst().getInt("status"),0);
            equal(query(x.f.reader,"SELECT * FROM requests").size(),0);
        }
        System.out.println("PASS 5 replay and HTTP-preflight races recheck current administrator/token inside root; revoked authority cannot disable a client or recover a privileged receipt");
        for(String fault:List.of("client.update.after","token.update.after","receipt.add.after","receipt.update.after","release.after","commit"))try(var x=new DisableFixture()){
            x.f.faults.add(fault);
            denied(503, () -> x.disable("external_app", "failure"));
            equal(x.snapshot(), x.before);
            check(!x.f.active()&&JdbcRepository.TX.get()==null,"failure closes actual strict root "+fault);
            x.f.faults.clear();
            x.disable("external_app", "failure");
            equal(query(x.f.reader, "SELECT * FROM requests").size(), 1);
        }
        System.out.println("PASS 6 actual client/token/receipt update, savepoint-release and real strict JDBC commit failures roll back all rows, release root and allow safe same-key retry");
        try(var x=new DisableFixture()){
            x.f.faults.add("observer");
            x.disable("external_app", "observe");
            check(!x.snapshot().equals(x.before),"committed disable visible after root only");
        }
        try(var x=new DisableFixture()){
            // 提交已落盘但客户端未收到确认时，同键重放应恢复回执。
            x.f.faults.add("commit.after");
            denied(503, () -> x.disable("external_app", "ack"));
            equal(query(x.f.reader,"SELECT status FROM clients WHERE clientId='external_app'").getFirst().getInt("status"),1);
            equal(query(x.f.reader,"SELECT * FROM requests").size(),1);
            String persisted = x.snapshot();
            equal(x.disable("external_app", "ack").getInt("status"), 1);
            equal(x.snapshot(), persisted);
        }
        System.out.println("PASS 7 separate observer sees no partial disable; actual JDBC committed-but-lost acknowledgement retains durable state and replay recovers without rewriting timestamps");
        for(String fault:List.of("user.read","token.read","client.read","request.read"))try(var x=new DisableFixture()){
            x.f.faults.add(fault);
            denied(503, () -> x.disable("external_app", "read-fault"));
            equal(x.snapshot(), x.before);
        }
        System.out.println("PASS 8 current guard/target/receipt storage faults fail closed503 without state changes or leaked credentials");
        try(var x=new DisableFixture()){
            exec(x.f.writer,"CREATE TABLE tickets(oId TEXT PRIMARY KEY,userId TEXT,parentTokenId TEXT,channel TEXT,targetId TEXT,createdAt BIGINT,expiresAt BIGINT,consumedAt BIGINT)");
            exec(x.f.writer,"UPDATE tokens SET scope='content.read' WHERE oId='12'");
            final var socket=new org.b3log.symphony.service.ApiV3SocketTicketService();
            final var tickets=new org.b3log.symphony.repository.ApiV3SocketTicketRepository(){
                @Override public String getName(){return "tickets";}
                @Override public boolean hasTransactionBegun(){return x.f.active();}
                @Override public Transaction beginTransaction(){return x.f.receipts.beginTransaction();}
                @Override public java.util.List<JSONObject> select(String sql,Object...args)throws RepositoryException{return x.f.read("ticket",sql,args);}
                @Override public String add(JSONObject row)throws RepositoryException{x.f.insert("tickets",row);return row.getString("oId");}
                @Override public void update(String id,JSONObject row,String...names)throws RepositoryException{exec(x.f.writer,"UPDATE tickets SET consumedAt=? WHERE oId=?",row.getLong("consumedAt"),id);}
            };
            inject(socket, socket.getClass(), "tickets", tickets);
            inject(socket, socket.getClass(), "clients", x.f.clients);
            inject(socket, socket.getClass(), "tokens", x.f.tokens);
            inject(socket,socket.getClass(),"users",new org.b3log.symphony.repository.UserRepository(){@Override public String getName(){return "user";}});
            var issued = socket.issueChat("1", "12");
            var live = socket.consume(issued.ticket());
            check(socket.authorized(live), "issued grant currently authorized");
            var pending = socket.issueChat("1", "12");
            x.disable("external_app", "socket");
            check(!socket.authorized(live),"existing current-authority grant denied after disable");
            denied(401, () -> socket.consume(pending.ticket()));
            denied(401, () -> socket.issueChat("1", "12"));
            check(!x.f.active()&&JdbcRepository.TX.get()==null,"failed ticket root disposed");
        }
        System.out.println("PASS 9 actual SocketTicket current parent/client SQL rejects existing grants, unconsumed old tickets and new issuance after committed disable; no real socket/network claimed");
        System.out.println("Client disable isolated tests passed: 10 groups");

        try(var x=new DisableFixture()){
            exec(x.f.writer,"INSERT INTO clients VALUES('native','fishpi_app_v3','Native','profile.read',0,'PRIVATE')");
            x.disable("fishpi_app_v3","native-disable");
            equal(query(x.f.reader,"SELECT oId FROM app_logins ORDER BY oId").size(),2);
            equal(query(x.f.reader,"SELECT oId FROM app_logins WHERE oId='pending'").size(),0);
            equal(query(x.f.reader,"SELECT status FROM clients WHERE clientId='fishpi_app_v3'").getFirst().getInt("status"),1);
        }
        System.out.println("PASS 10 native APP disable atomically invalidates pending QR requests while preserving consumed/denied history");

    }
}
