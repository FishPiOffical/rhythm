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
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import sun.misc.Unsafe;
import java.sql.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Production routes, scope/admin checks, registry, idempotency and strict JDBC roots on isolated SQLite. */
public final class ApiV3ClientManagementTest {
    static final String BASE="/api/v3/admin/oauth/clients", URI="https://App.example:8443/Callback?mode=exact%2Bvalue";
    interface Work { void run() throws Exception; }
    static final class Fixture implements AutoCloseable {
        final Path file=Files.createTempFile("rhythm-client-management-",".sqlite");
        final Connection writer=DriverManager.getConnection("jdbc:sqlite:"+file),reader=DriverManager.getConnection("jdbc:sqlite:"+file);
        final ApiV3ClientManagementService service=new ApiV3ClientManagementService();
        final ApiV3ClientManagementProcessor processor=new ApiV3ClientManagementProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        final Set<String> faults=new HashSet<>();final List<String> locks=new ArrayList<>();
        JdbcTransaction tx;boolean development;boolean clientNamespaceLocked;int disposals;Work beforeBegin;
        final ApiV3ClientRepository clients=new ApiV3ClientRepository(){
            @Override public String getName(){return "clients";}
            @Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return read("client",sql,args);}
            @Override public String add(JSONObject row)throws RepositoryException{insert("clients",row);fault("client.after");return row.getString("oId");}
        };
        final ApiV3ClientRedirectRepository redirects=new ApiV3ClientRedirectRepository(){
            @Override public String getName(){return "redirects";}
            @Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return read("redirect",sql,args);}
            @Override public String add(JSONObject row)throws RepositoryException{insert("redirects",row);fault("redirect.after");return row.getString("oId");}
        };
        final ApiV3TokenRepository tokens=new ApiV3TokenRepository(){
            @Override public String getName(){return "tokens";}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return read("token",sql,args);}
        };
        final ApiV3RequestRepository receipts=new ApiV3RequestRepository(){
            @Override public String getName(){return "requests";}
            @Override public boolean hasTransactionBegun(){return active();}
            @Override public Transaction beginTransaction(){try{if(beforeBegin!=null){var hook=beforeBegin;beforeBegin=null;hook.run();}begin();return ApiV3Transactions.strict(tx);}catch(Exception e){throw new IllegalStateException(e);}}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return read("request",sql,args);}
            @Override public String add(JSONObject row)throws RepositoryException{insert("requests",row);fault("receipt.add.after");return row.getString("oId");}
            @Override public void update(String id,JSONObject row,String...names)throws RepositoryException{exec(writer,"UPDATE requests SET responseJson=? WHERE oId=?",row.getString("responseJson"),id);fault("receipt.update.after");}
        };
        final ApiV3ClientRegistrationService registration=new ApiV3ClientRegistrationService(){
            @Override protected boolean development(){return development;}
        };
        Fixture()throws Exception{
            exec(writer,"CREATE TABLE clients(oId TEXT PRIMARY KEY,clientId TEXT UNIQUE,name TEXT,scope TEXT,status INTEGER,secret TEXT)");
            exec(writer,"CREATE TABLE redirects(oId TEXT PRIMARY KEY,clientId TEXT,redirectUri TEXT,status INTEGER,secret TEXT)");
            exec(writer,"CREATE TABLE tokens(oId TEXT PRIMARY KEY,userId TEXT,clientId TEXT,tokenType TEXT,state INTEGER,expiresAt BIGINT,scope TEXT,tokenHash TEXT)");
            exec(writer,"CREATE TABLE user(oId TEXT PRIMARY KEY,userRole TEXT,userStatus INTEGER,userPassword TEXT)");
            exec(writer,"CREATE TABLE requests(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            exec(writer,"INSERT INTO clients VALUES('1','website_v3','Internal','settings.write content.read',0,'PRIVATE-client-secret')");
            exec(writer,"INSERT INTO clients VALUES('2','external_app','External','settings.write content.read',0,'PRIVATE-client-secret')");
            exec(writer,"INSERT INTO user VALUES('1','adminRole',0,'PRIVATE-password'),('2','defaultRole',0,'PRIVATE-password')");
            for(String id:List.of("11","12","13","14"))exec(writer,"INSERT INTO tokens VALUES(?,?,?,?,?,?,?,?)",id,id.equals("13")?"2":"1",id.equals("12")?"external_app":"website_v3","access",0,System.currentTimeMillis()+3600000,id.equals("14")?"content.read":"settings.write", "PRIVATE-token-hash");
            inject(service,ApiV3ClientManagementService.class,"clients",clients);inject(service,ApiV3ClientManagementService.class,"redirects",redirects);
            inject(service,ApiV3ClientManagementService.class,"tokens",tokens);inject(service,ApiV3ClientManagementService.class,"requests",receipts);
            inject(service,ApiV3ClientManagementService.class,"registration",registration);
            inject(service,ApiV3ClientManagementService.class,"users",new UserRepository(){
                @Override public String getName(){return "user";}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return read("user",sql,args);}
            });
            inject(registration,ApiV3ClientRegistrationService.class,"clients",clients);inject(registration,ApiV3ClientRegistrationService.class,"redirects",redirects);
            inject(processor,ApiV3ClientManagementProcessor.class,"management",service);
            inject(auth,ApiV3AuthMidware.class,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){
                @Override public JSONObject authenticate(String token){return Set.of("11","12","13","14").contains(token)?identity(token):null;}
            });
            // Each fixture isolates the shared rate-budget state; the actual gate still runs on its POST routes.
            var counter=ApiV3WriteMidware.class.getDeclaredField("COUNTS");counter.setAccessible(true);
            ((com.github.benmanes.caffeine.cache.Cache<?,?>)counter.get(null)).invalidateAll();
            ApiV3ClientManagementProcessor.registerRoutes(processor,auth,new ApiV3WriteMidware());Dispatcher.mapping();
        }
        JSONObject identity(String token){return new JSONObject().put("user",new JSONObject().put("oId",token.equals("13")?"2":"1").put("userRole","adminRole"))
                .put("tokenId",token).put("clientId","website_v3").put("scope",token.equals("14")?"content.read":"settings.write");}
        boolean active(){try{return !writer.getAutoCommit();}catch(SQLException e){throw new IllegalStateException(e);}}
        void fault(String key)throws RepositoryException{if(faults.contains(key))throw new RepositoryException("PRIVATE-injected storage failure");}
        void begin()throws Exception{
            check(!active()&&JdbcRepository.TX.get()==null,"clean root start");writer.setAutoCommit(false);
            final Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                try{
                    switch(m.getName()){
                        case "commit"->{if(faults.contains("commit"))throw new SQLException("PRIVATE-injected root commit rejection");if(faults.remove("commit.after")){m.invoke(writer,a);throw new SQLException("PRIVATE-lost commit acknowledgement");}}
                        case "releaseSavepoint"->{if(faults.remove("release.after")){m.invoke(writer,a);throw new SQLException("PRIVATE-lost release acknowledgement");}}
                        case "close"->{disposals++;writer.rollback();writer.setAutoCommit(true);return null;}
                    }
                    return m.invoke(writer,a);
                }catch(InvocationTargetException e){throw e.getCause();}
            });
            var access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);tx=(JdbcTransaction)((Unsafe)access.get(null)).allocateInstance(JdbcTransaction.class);
            inject(tx,JdbcTransaction.class,"connection",connection);inject(tx,JdbcTransaction.class,"isActive",true);JdbcRepository.TX.set(tx);
        }
        List<JSONObject> read(String kind,String sql,Object...args)throws RepositoryException{
            if(sql.contains(" FOR UPDATE"))locks.add(kind);
            if(sql.contains("ORDER BY clientId FOR UPDATE"))clientNamespaceLocked=true;
            check(!sql.contains("tokenHash")&&!sql.contains("userPassword")&&!sql.contains("secret"),"credential column not queried");
            fault(kind+".read");return query(active()?writer:reader,sql,args);
        }
        void insert(String table,JSONObject row)throws RepositoryException{
            String columns=String.join(",",new TreeSet<>(row.keySet()));var args=new ArrayList<Object>();for(String key:columns.split(","))args.add(row.get(key));
            exec(writer,"INSERT INTO "+table+"("+columns+") VALUES("+String.join(",",Collections.nCopies(args.size(),"?"))+")",args.toArray());
            if(faults.contains("observe"))check(query(reader,"SELECT clientId FROM clients WHERE clientId LIKE 'app_%'").isEmpty(),"uncommitted application invisible");
        }
        String snapshot()throws Exception{return query(reader,"SELECT clientId,name,scope,status FROM clients ORDER BY clientId")+"|"+query(reader,"SELECT * FROM redirects ORDER BY oId")+"|"+query(reader,"SELECT * FROM requests ORDER BY oId");}
        JSONObject create(String key)throws Exception{return service.create(identity("11"),input(),key);}
        JSONObject request(String method,String path,String token,String body,String key,Map<String,String> params)throws Exception{
            var bytes=body==null?new byte[0]:body.getBytes(StandardCharsets.UTF_8);
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.valueOf(method),path,Unpooled.wrappedBuffer(bytes));
            var response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
            try{
                raw.headers().set("Content-Type","application/json").set("Cookie","PRIVATE-fake-admin");if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                var request=new Request(null,raw){@Override public byte[] getBytes(){return bytes;}@Override public String getRemoteAddr(){return "192.0.2.17";}};
                request.setParams(new HashMap<>(params));var c=new RequestContext(request,new Response(null,response));c.attr("user",new JSONObject().put("oId","1").put("userRole","adminRole"));
                c.addHandler(new RouteHandler());c.addHandler(context->{var resolution=(RouteResolution)context.attr("MATCH_RESULT");try{resolution.getContextHandlerMeta().getInvokeHolder().invoke(processor,context);}catch(ReflectiveOperationException e){throw new AssertionError(e);}});c.handle();
                var result=new JSONObject().put("status",c.attr("test.status"));if(c.attr("test.data")!=null)result.put("data",c.attr("test.data"));if(c.attr("test.code")!=null)result.put("code",c.attr("test.code"));
                if(!Set.of(401).contains(result.getInt("status"))&&!"14".equals(token)){equal(response.headers().get("Cache-Control"),"no-store");equal(response.headers().get("Vary"),"Authorization");}
                check(!result.toString().contains("PRIVATE"),"no secrets in response");return result;
            }finally{raw.release();}
        }
        JSONObject get(String path,String token)throws Exception{return request("GET",path,token,null,null,Map.of());}
        JSONObject post(String body,String key)throws Exception{return request("POST",BASE,"11",body,key,Map.of());}
        public void close()throws Exception{if(tx!=null&&tx.isActive())tx.rollback();JdbcRepository.TX.remove();writer.close();reader.close();Files.deleteIfExists(file);}
    }
    static ApiV3ClientManagementInputs.Registration input(){return new ApiV3ClientManagementInputs.Registration(" App <name> ","profile.read content.read profile.read",List.of(URI,"com.example.client:/oauth2redirect"));}
    static String body(){return input().normalized().toString();}
    static void inject(Object bean,Class<?> type,String key,Object value)throws Exception{var field=type.getDeclaredField(key);field.setAccessible(true);field.set(bean,value);}
    static void exec(Connection connection,String sql,Object...args)throws RepositoryException{try(var s=connection.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}catch(SQLException e){throw new RepositoryException(e);}}
    static List<JSONObject> query(Connection connection,String sql,Object...args)throws RepositoryException{
        try(var s=connection.prepareStatement(sql.replace(" FOR UPDATE",""))){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var rows=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));rows.add(row);}return rows;}}catch(SQLException e){throw new RepositoryException(e);}
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void equal(Object actual,Object expected){check(Objects.equals(actual,expected),"expected="+expected+" actual="+actual);}
    static JSONObject data(JSONObject value){equal(value.getInt("status"),200);return value.getJSONObject("data");}
    static void denied(int status,Work work)throws Exception{try{work.run();throw new AssertionError("expected refusal");}catch(ApiV3Exception e){equal(e.status,status);}}
    public static void main(String[]args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            for(String token:Arrays.asList(null,"invalid"))equal(f.get(BASE,token).getInt("status"),401);
            for(String token:List.of("12","13","14"))equal(f.get(BASE,token).getInt("status"),403);
            equal(f.request("POST",BASE,"12",body(),"third",Map.of()).getInt("status"),403);
            check(query(f.reader,"SELECT * FROM requests").isEmpty(),"denied users create no receipt");
            var page=data(f.get(BASE,"11"));equal(page.getJSONArray("list").length(),1);equal(page.getJSONArray("list").getJSONObject(0).keySet(),Set.of("clientId","name","scope","status"));
            equal(f.get(BASE+"/website_v3","11").getInt("status"),404);equal(f.get(BASE+"/missing","11").getInt("status"),404);
        }
        System.out.println("PASS 1 real routes require settings.write plus current first-party admin; Cookie/query identity and third-party admin tokens fail, website client is hidden");
        try(var f=new Fixture()){
            for(String change:List.of("UPDATE user SET userRole='defaultRole' WHERE oId='1'","UPDATE user SET userStatus=4 WHERE oId='1'","UPDATE tokens SET state=2 WHERE oId='11'","UPDATE tokens SET tokenType='refresh' WHERE oId='11'","UPDATE tokens SET expiresAt=1 WHERE oId='11'","UPDATE tokens SET clientId='external_app' WHERE oId='11'","UPDATE tokens SET userId='2' WHERE oId='11'","UPDATE tokens SET scope='content.read' WHERE oId='11'","UPDATE clients SET status=1 WHERE clientId='website_v3'")){
                exec(f.writer,change);equal(f.get(BASE,"11").getInt("status"),403);
                exec(f.writer,"UPDATE user SET userRole='adminRole',userStatus=0 WHERE oId='1'");exec(f.writer,"UPDATE tokens SET userId='1',clientId='website_v3',tokenType='access',state=0,scope='settings.write',expiresAt=? WHERE oId='11'",System.currentTimeMillis()+3600000);exec(f.writer,"UPDATE clients SET status=0 WHERE clientId='website_v3'");
            }
            f.faults.add("user.read");equal(f.get(BASE,"11").getInt("status"),503);
        }
        System.out.println("PASS 2 current DB role/account/token ownership, type, state, expiry, website and scope override stale authentication snapshots; admin-gate errors are private and storage failure is 503");
        try(var f=new Fixture()){
            f.faults.add("observe");var result=data(f.post(body(),"create"));String id=result.getString("clientId");
            check(id.matches("app_[A-Za-z0-9_-]{43}"),"server generated client ID");equal(result.keySet(),Set.of("clientId","name","scope","status","redirectUris"));equal(result.getString("name"),"App <name>");equal(result.getString("scope"),"content.read profile.read");equal(result.getJSONArray("redirectUris").getString(0),URI);
            equal(query(f.reader,"SELECT * FROM redirects WHERE clientId=?",id).size(),2);equal(query(f.reader,"SELECT * FROM requests").size(),1);
            check(!f.active()&&JdbcRepository.TX.get()==null,"successful root disposed");
            check(f.clientNamespaceLocked,"client namespace locked in sorted order before new registration");
            check(f.locks.indexOf("client")<f.locks.indexOf("user")&&f.locks.indexOf("user")<f.locks.indexOf("token")&&f.locks.indexOf("token")<f.locks.indexOf("request"),"client-user-token-receipt lock order");
            f.faults.remove("observe");var replay=data(f.post(body(),"create"));equal(ApiV3Json.canonical(replay),ApiV3Json.canonical(result));equal(query(f.reader,"SELECT * FROM requests").size(),1);
            equal(f.post(new JSONObject(body()).put("name","Other").toString(),"create").getInt("status"),409);
            var detail=data(f.get(BASE+"/"+id,"11"));equal(detail.keySet(),Set.of("clientId","name","scope","status","redirects"));equal(detail.getJSONObject("redirects").getJSONArray("list").length(),2);
            for(Object item:detail.getJSONObject("redirects").getJSONArray("list"))equal(((JSONObject)item).keySet(),Set.of("uri","status"));
        }
        System.out.println("PASS 3 POST uses actual approved-registration primitive and strict idempotency root: generated ID/exact callbacks, atomic visibility, lock order, committed replay, conflict and bounded detail whitelist");
        try(var f=new Fixture()){
            f.create("replay");String before=f.snapshot();exec(f.writer,"UPDATE user SET userRole='defaultRole' WHERE oId='1'");denied(403,()->f.create("replay"));equal(f.snapshot(),before);check(!f.active()&&JdbcRepository.TX.get()==null,"replay refusal root disposed");
            exec(f.writer,"UPDATE user SET userRole='adminRole' WHERE oId='1'");exec(f.writer,"UPDATE tokens SET state=2 WHERE oId='11'");denied(403,()->f.create("replay"));equal(f.snapshot(),before);
            exec(f.writer,"UPDATE tokens SET state=0 WHERE oId='11'");f.beforeBegin=()->exec(f.writer,"UPDATE user SET userRole='defaultRole' WHERE oId='1'");equal(f.post(body(),"raced").getInt("status"),403);equal(f.snapshot(),before);
        }
        System.out.println("PASS 4 replay always rechecks current admin/token under its strict root; role change after HTTP preflight cannot register or replay a receipt");
        for(String fault:List.of("client.after","redirect.after","receipt.add.after","receipt.update.after","release.after","commit"))try(var f=new Fixture()){
            String before=f.snapshot();f.faults.add(fault);denied(503,()->f.create("failed"));equal(f.snapshot(),before);check(!f.active()&&JdbcRepository.TX.get()==null,"failed root rolled back/disposed "+fault);check(f.disposals>0,"framework close invoked");
            f.faults.clear();f.create("failed");equal(query(f.reader,"SELECT * FROM requests").size(),1);
        }
        System.out.println("PASS 5 client/callback/receipt writes, savepoint release and real strict JDBC commit rejection roll back every row, dispose root resources and permit a safe retry");
        try(var f=new Fixture()){
            String before=f.snapshot();
            for(String bad:List.of("{}","{\"name\":3,\"scope\":\"content.read\",\"redirectUris\":[\"https://app.example/cb\"]}",new JSONObject(body()).put("clientId","chosen").toString(),new JSONObject(body()).put("status",0).toString(),new JSONObject(body()).put("userId","1").toString(),new JSONObject(body()).put("redirectUris",new JSONArray().put(12)).toString()))equal(f.post(bad,"invalid").getInt("status"),422);
            equal(f.post("{\"name\":\"one\",\"name\":\"two\"}","bad-json").getInt("status"),400);equal(f.post(body(),null).getInt("status"),422);
            for(String scope:List.of("","*","content.read unknown","content.read  profile.read"))denied(422,()->f.service.create(f.identity("11"),new ApiV3ClientManagementInputs.Registration("App",scope,List.of(URI)),"scope"));
            for(String uri:List.of("https://app.example/cb#fragment","https://user:pwd@app.example/cb","http://app.example/cb","myapp:/cb","com.example.app://cb","https://app.example/cb?%63ode=x"))denied(422,()->f.service.create(f.identity("11"),new ApiV3ClientManagementInputs.Registration("App","content.read",List.of(uri)),"uri"));
            denied(422,()->f.service.create(f.identity("11"),new ApiV3ClientManagementInputs.Registration("App","content.read",List.of(URI,URI)),"duplicate"));
            equal(f.snapshot(),before);
        }
        System.out.println("PASS 6 strict HTTP field/types/JSON/key boundaries plus primitive scope/name/callback rules reject supplied IDs, credentials/fragments/reserved OAuth params/duplicate callbacks without partial receipt");
        try(var f=new Fixture()){
            for(int i=0;i<52;i++)exec(f.writer,"INSERT INTO clients VALUES(?,?,?,?,0,'PRIVATE-secret')","20"+i,String.format("client_%03d",i),"Name","content.read");
            var first=data(f.request("GET",BASE,"11",null,null,Map.of("size","50")));equal(first.getJSONArray("list").length(),50);equal(first.getJSONObject("pagination").getLong("total"),53L);
            var second=data(f.request("GET",BASE,"11",null,null,Map.of("size","50","p","2")));equal(second.getJSONArray("list").length(),3);
            for(int i=0;i<53;i++)exec(f.writer,"INSERT INTO redirects VALUES(?,?,?,0,'PRIVATE-secret')",String.format("%03d",i),"external_app","https://App.example/Callback?index="+i);
            var callbacks=data(f.request("GET",BASE+"/external_app","11",null,null,Map.of("size","50","p","2"))).getJSONObject("redirects");equal(callbacks.getJSONArray("list").length(),3);equal(callbacks.getJSONObject("pagination").getLong("total"),53L);
            equal(f.request("GET",BASE,"11",null,null,Map.of("size","51")).getInt("status"),422);
            f.faults.add("redirect.read");equal(f.get(BASE+"/external_app","11").getInt("status"),503);
        }
        System.out.println("PASS 7 client and callback directories independently paginate beyond fifty without exposing credential columns; bad bounds reject and storage failures cannot become empty success");
        try(var f=new Fixture()){
            for(String name:List.of("x".repeat(129),"line\nname","")){exec(f.writer,"UPDATE clients SET name=? WHERE clientId='external_app'",name);equal(f.get(BASE,"11").getInt("status"),503);}
            exec(f.writer,"UPDATE clients SET name='External' WHERE clientId='external_app'");
            for(String scope:List.of("unknown.read","content.read  profile.read","x".repeat(256))){exec(f.writer,"UPDATE clients SET scope=? WHERE clientId='external_app'",scope);equal(f.get(BASE,"11").getInt("status"),503);}
            exec(f.writer,"UPDATE clients SET scope='content.read',status=8 WHERE clientId='external_app'");equal(f.get(BASE,"11").getInt("status"),503);exec(f.writer,"UPDATE clients SET status=0 WHERE clientId='external_app'");
            for(String uri:List.of("https://user:PRIVATE@app.example/cb","https://app.example/cb#fragment","https://app.example/"+"x".repeat(2048),"https://app.example/cb\n")){
                exec(f.writer,"DELETE FROM redirects");exec(f.writer,"INSERT INTO redirects VALUES('one','external_app',?,0,'PRIVATE')",uri);equal(f.get(BASE+"/external_app","11").getInt("status"),503);
            }
            exec(f.writer,"UPDATE redirects SET redirectUri=?",URI);equal(data(f.get(BASE+"/external_app","11")).getJSONObject("redirects").getJSONArray("list").getJSONObject(0).getString("uri"),URI);
        }
        System.out.println("PASS 8 stored DTO validates bounded fields, scope/status enums and callback shape, rejects credential-bearing/bad records and preserves valid URI case/query byte-for-byte");
        try(var f=new Fixture()){
            var callbacks=new JSONArray();for(int i=0;i<20;i++)callbacks.put("https://app.example/cb/"+i);
            var payload=new JSONObject(body()).put("redirectUris",callbacks);var result=data(f.post(payload.toString(),"twenty"));equal(result.getJSONArray("redirectUris").length(),20);
            String before=f.snapshot();callbacks.put("https://app.example/cb/20");equal(f.post(payload.toString(),"too-many").getInt("status"),422);equal(f.snapshot(),before);
            String base="https://app.example/";String longest=base+"x".repeat(2048-base.length());
            equal(data(f.post(new JSONObject(body()).put("redirectUris",new JSONArray().put(longest)).toString(),"longest")).getJSONArray("redirectUris").getString(0),longest);
            before=f.snapshot();equal(f.post(new JSONObject(body()).put("redirectUris",new JSONArray().put(longest+"x")).toString(),"too-long").getInt("status"),422);equal(f.snapshot(),before);
            f.development=true;equal(data(f.post(new JSONObject(body()).put("redirectUris",new JSONArray().put("http://127.0.0.1:8081/cb")).toString(),"loopback")).getJSONArray("redirectUris").getString(0),"http://127.0.0.1:8081/cb");
        }
        System.out.println("PASS 9 user-approved 20 callbacks/2048 URI boundary enforced before mutation, 21 and 2049 rejected; development loopback remains valid and exact");
        try(var f=new Fixture()){
            f.faults.add("commit.after");denied(503,()->f.create("ack"));var persisted=query(f.reader,"SELECT clientId FROM clients WHERE clientId LIKE 'app_%'");equal(persisted.size(),1);equal(query(f.reader,"SELECT * FROM requests").size(),1);
            var id=persisted.getFirst().getString("clientId");exec(f.writer,"UPDATE clients SET name='Later change' WHERE clientId=?",id);
            var replay=f.create("ack");equal(replay.getString("clientId"),id);equal(replay.getString("name"),"App <name>");equal(query(f.reader,"SELECT name FROM clients WHERE clientId=?",id).getFirst().getString("name"),"Later change");equal(query(f.reader,"SELECT clientId FROM clients WHERE clientId LIKE 'app_%'").size(),1);
        }
        System.out.println("PASS 10 real JDBC commit acknowledgement loss retains durable client/callback/receipt and same-key recovery returns original identity without recreating or overwriting current registration");
        System.out.println("Client management isolated tests passed: 10 groups");
    }
}
