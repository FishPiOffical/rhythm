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
package org.b3log.symphony.repository;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.*;
import org.b3log.symphony.service.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import org.sqlite.Function;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Actual owned state SQL, strict idempotency and HTTP chain; SQLite/pool/token/effects adapted. */
public class PrivateMessageReadStateTest {
    static class Reads extends Fixture {
        final PrivateMessageRevisionService revisions=new PrivateMessageRevisionService();
        final ApiV3PrivateRevisionRepository revisionRows;
        final PrivateMessageReadStateService state=new PrivateMessageReadStateService();
        int effects;boolean arrival,failRead,routesRegistered;
        final ApiV3PrivateMessageStateService service=new ApiV3PrivateMessageStateService(){@Override protected void afterMarked(String actor){Assert.assertFalse(active());Assert.assertNull(org.b3log.latke.repository.jdbc.JdbcRepository.TX.get());Assert.assertEquals(actor,"1");effects++;}};
        final ApiV3PrivateMessageProcessor controller=new ApiV3PrivateMessageProcessor();final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        String scope="messages.write";final String table;
        Reads()throws Exception{this("");}
        Reads(String prefix)throws Exception{
            table=prefix+"private_unread";
            exec(writer,"CREATE TABLE private_revision(oId TEXT PRIMARY KEY,revision TEXT,updatedAt BIGINT)");
            revisionRows=new ApiV3PrivateRevisionRepository(){
                @Override public String getName(){return "private_revision";}@Override public boolean hasTransactionBegun(){return active();}
                @Override protected String upsertSql(){return "INSERT INTO private_revision(oId,revision,updatedAt) VALUES(?,?,?) ON CONFLICT(oId) DO UPDATE SET revision=excluded.revision,updatedAt=excluded.updatedAt";}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(active()?writer:reader,sql,args);}
            };
            inject(revisions,PrivateMessageRevisionService.class,"revisions",revisionRows);inject(state,PrivateMessageReadStateService.class,"revisions",revisions);
            for(var c:List.of(writer,reader))Function.create(c,"regexp",new Function(){@Override protected void xFunc()throws java.sql.SQLException{String value=value_text(1);result(value!=null&&value.matches(value_text(0))?1:0);}});
            exec(writer,"ALTER TABLE user ADD COLUMN userStatus INTEGER");exec(writer,"UPDATE user SET userStatus=0");
            exec(writer,"CREATE TABLE "+table+"(oId TEXT PRIMARY KEY,fromId TEXT,toId TEXT,user_session TEXT)");
            exec(writer,"INSERT INTO "+table+" VALUES('100','2','1','forged'),('101','2','1','forged'),('102','3','1','forged'),('103','1','2','1_2'),('104','2','3','1_2'),('105','1','1','self'),('106','bad','1','bad')");
            exec(writer,"CREATE TABLE requests(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            inject(state,PrivateMessageReadStateService.class,"users",new UserRepository(){@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(writer,sql,args);}});
            inject(state,PrivateMessageReadStateService.class,"unread",new ChatUnreadRepository(){
                @Override public String getName(){return table;}@Override public boolean hasTransactionBegun(){return active();}
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{
                    if(failRead)throw new RepositoryException("PRIVATE_DRIVER_MESSAGE");var result=query(writer,sql,args);
                    if(arrival){arrival=false;exec(writer,"INSERT INTO "+table+" VALUES('999','2','1','new')");}return result;
                }
            });
            final var idempotency=new ApiV3IdempotencyMgmtService();
            inject(idempotency,ApiV3IdempotencyMgmtService.class,"requestRepository",new ApiV3RequestRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public Transaction beginTransaction(){try{begin();return ApiV3Transactions.strict(tx);}catch(Exception e){throw new IllegalStateException(e);}}
                @Override public JSONObject getForUpdate(String id)throws RepositoryException{var rows=query(writer,"SELECT * FROM requests WHERE oId=?",id);return rows.isEmpty()?null:rows.getFirst();}
                @Override public String add(JSONObject row)throws RepositoryException{String id=row.getString("oId");exec(writer,"INSERT INTO requests VALUES(?,?,?,?,?,?)",id,row.getString("userId"),row.getString("operation"),row.getString("requestHash"),row.getString("responseJson"),row.getLong("createdAt"));return id;}
                @Override public void update(String id,JSONObject row,String...names)throws RepositoryException{if(faults.contains("receipt"))throw new RepositoryException("PRIVATE_RECEIPT_ERROR");exec(writer,"UPDATE requests SET responseJson=? WHERE oId=?",row.getString("responseJson"),id);}
            });
            inject(service,ApiV3PrivateMessageStateService.class,"state",state);inject(service,ApiV3PrivateMessageStateService.class,"idempotency",idempotency);
            inject(controller,ApiV3PrivateMessageProcessor.class,"state",service);
            inject(auth,ApiV3AuthMidware.class,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){@Override public JSONObject authenticate(String token){return "VALID".equals(token)?new JSONObject().put("user",new JSONObject().put("oId","1")).put("scope",scope):null;}});
        }
        JSONObject mark(String peer,String key)throws Exception{return service.mark(new JSONObject().put("oId","1"),peer,key);}
        String data()throws Exception{return query(reader,"SELECT * FROM "+table+" ORDER BY oId").toString();}
        List<String> ids()throws Exception{return query(reader,"SELECT oId FROM "+table+" ORDER BY oId").stream().map(row->row.getString("oId")).toList();}
        JSONObject http(String peer,String bearer,String key,String body,String query,String type,boolean routes)throws Exception{
            final String path=peer==null?"/api/v3/me/conversations/read":"/api/v3/me/conversations/"+peer+"/read";
            final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,path+(query==null?"":"?"+query));
            try{
                raw.headers().set("Cookie","legacy");if(bearer!=null)raw.headers().set("Authorization",bearer);if(key!=null)raw.headers().set("Idempotency-Key",key);if(type!=null)raw.headers().set("Content-Type",type);
                var request=new Request(null,raw){@Override public String getRemoteAddr(){return "192.0.2.150";}};raw.content().clear().writeBytes(body.getBytes(StandardCharsets.UTF_8));request.setBytes(body.getBytes(StandardCharsets.UTF_8));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.pathVars(new HashMap<>());c.attr("user",new JSONObject().put("oId","2"));if(peer!=null)c.pathVar("peerId",peer);
                if(routes){
                    if(!routesRegistered){PrivateReadRouteBindings.register(controller,auth,new ApiV3WriteMidware());routesRegistered=true;}
                    final var registry=Dispatcher.class.getDeclaredField("routerGroups");registry.setAccessible(true);boolean found=false;
                    for(Object group:(List<?>)registry.get(null))for(Object route:(List<?>)field(group,"routers")){
                        final var paths=(List<?>)field(route,"uriTemplates");if(!paths.equals(List.of(peer==null?path:"/api/v3/me/conversations/{peerId}/read")))continue;
                        Assert.assertEquals(((List<?>)field(route,"httpRequestMethods")).getFirst(),org.b3log.latke.http.HttpMethod.POST);
                        @SuppressWarnings("unchecked") final var chain=new ArrayList<org.b3log.latke.http.function.Handler>((List<org.b3log.latke.http.function.Handler>)field(group,"middlewares"));Assert.assertEquals(chain.size(),2);chain.add((org.b3log.latke.http.function.Handler)field(route,"handler"));c.addHandlers(chain);found=true;
                    }
                    Assert.assertTrue(found);c.handle();
                }
                else{c.addHandler(new ApiV3WriteMidware()::handle);c.addHandler(peer==null?controller::markAllRead:controller::markRead);auth.messagesWrite(c);}
                var out=new JSONObject().put("status",c.attr("test.status"));if(c.attr("test.data")!=null)out.put("data",c.attr("test.data"));if(c.attr("test.code")!=null)out.put("code",c.attr("test.code"));if(out.getInt("status")==200)Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");return out;
            }finally{raw.release();}
        }
    }
    static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    interface Work{void run()throws Exception;}
    static void reject(Work work,int status)throws Exception{try{work.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    public static void main(String[] args)throws Exception{
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Reads()){
            try{f.state.markInCurrentTransaction("1","2");Assert.fail();}catch(IllegalStateException expected){}
            Assert.assertEquals(f.mark("2","peer-read").getLong("changed"),2L);Assert.assertEquals(f.ids(),List.of("102","103","104","105","106"));Assert.assertEquals(f.effects,1);
            exec(f.writer,"INSERT INTO "+f.table+" VALUES('200','2','1','new')");String before=f.data();Assert.assertEquals(f.mark("2","peer-read").getLong("changed"),2L);Assert.assertEquals(f.data(),before);Assert.assertEquals(f.effects,2);
            Assert.assertEquals(f.mark(null,"all-read").getLong("changed"),2L);Assert.assertEquals(f.ids(),List.of("103","104","105","106"));
            Assert.assertEquals(f.mark("9","missing-pair").getLong("changed"),0L);
        }
        System.out.println("PASS private read state uses actual toId/fromId ownership instead of forged sessions, removes only the selected peer or own valid incoming backlog, preserves all foreign/self/malformed records and stores replay receipts without clearing later arrivals");
        try(var f=new Reads("custom_")){f.arrival=true;Assert.assertEquals(f.mark("2","cutoff").getLong("changed"),2L);Assert.assertTrue(f.ids().contains("999"));}
        System.out.println("PASS fixed locked cutoff preserves a newly inserted higher-ID arrival in the same transaction and actual repository prefixes are used");
        for(String fault:List.of("receipt","commit","delete","query"))try(var f=new Reads()){
            String before=f.data();if(fault.equals("delete"))exec(f.writer,"CREATE TRIGGER reject_private_delete BEFORE DELETE ON "+f.table+" BEGIN SELECT RAISE(ABORT,'PRIVATE_SQL_ERROR'); END");else if(fault.equals("query"))f.failRead=true;else f.faults.add(fault);
            reject(()->f.mark("2","fault"),503);Assert.assertEquals(f.data(),before);Assert.assertTrue(query(f.reader,"SELECT * FROM requests").isEmpty());Assert.assertEquals(f.effects,0);Assert.assertFalse(f.active());
        }
        System.out.println("PASS actual SQL deletion, private storage read, receipt write and strict commit failures roll back unread state and receipts together and never run post-commit refresh");
        try(var f=new Reads()){
            exec(f.writer,"UPDATE user SET userStatus=4 WHERE oId='1'");String before=f.data();reject(()->f.mark("2","disabled"),404);Assert.assertEquals(f.data(),before);Assert.assertEquals(f.effects,0);
        }
        try(var f=new Reads()){for(String bad:List.of("1","0","01","1 OR 1=1"))reject(()->f.mark(bad,"bad"),422);reject(()->f.mark("2",null),422);}
        System.out.println("PASS current database activation overrides stale viewer identity, self/injection/malformed peer IDs and missing idempotency keys cannot mutate state");
        try(var f=new Reads()){
            String before=f.data();for(String bearer:Arrays.asList(null,"Basic ignored","Bearer invalid")){Assert.assertEquals(f.http("2",bearer,"http","{}",null,"application/json",false).getInt("status"),401);Assert.assertEquals(f.data(),before);}
            f.scope="messages.read content.write notifications.write";Assert.assertEquals(f.http("2","Bearer VALID","http","{}",null,"application/json",false).getInt("status"),403);Assert.assertEquals(f.data(),before);f.scope="messages.write";
            Assert.assertEquals(f.http("2","Bearer VALID","http","{\"userId\":\"2\"}",null,"application/json",false).getInt("status"),422);
            Assert.assertEquals(f.http("2","Bearer VALID","http","{}","apiKey=ignored","application/json",false).getInt("status"),422);
            Assert.assertEquals(f.http("2","Bearer VALID","http","{}",null,"text/plain",false).getInt("status"),415);
            Assert.assertEquals(f.http("2","Bearer VALID","http"," ".repeat(1025),null,"application/json",false).getInt("status"),413);Assert.assertEquals(f.data(),before);
            Assert.assertEquals(f.http("2","Bearer VALID","http","{}",null,"application/json",true).getJSONObject("data").getLong("changed"),2L);
            Assert.assertEquals(f.http(null,"Bearer VALID","http-all","",null,null,true).getJSONObject("data").getLong("changed"),1L);
        }
        System.out.println("PASS real HTTP controller, production route declarations and actual Bearer/write middleware require messages.write, ignore ambient Cookie/attr identity, validate empty bounded bodies and select per-peer versus all read routes correctly");
    }
}
