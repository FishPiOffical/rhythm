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
package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.b3log.symphony.processor.*;
import org.json.*;
import org.testng.Assert;
import java.util.*;
import java.sql.*;
import java.lang.reflect.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** Production save/migration/bridge SQL + current grant checks, adapted database IO and actual HTTP parsers/middleware. */
public class ApiV3GameSaveTest {
    static final String GAME="evolve-save",DATA="  A+/==\n<script>not HTML</script>\t\u0000鱼😀  ";
    static JSONObject input(String data,long revision){return new JSONObject().put("data",data).put("expectedRevision",revision);}
    static JSONObject other(){return new JSONObject().put("tokenId","102").put("user",new JSONObject().put("oId","2"));}
    static Object field(Object target,String name)throws Exception {Class<?> c=target.getClass();while(c!=null){try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(name);}
    static class Fixture extends F {
        String writeFault="";boolean revokeAfterRead;
        final ApiV3GameSaveService service=new ApiV3GameSaveService(){protected long now(){return NOW;}};
        final ApiV3GameSaveProcessor endpoint=new ApiV3GameSaveProcessor();final CloudProcessor oldEndpoint=new CloudProcessor();
        final CloudService oldCloud=new CloudService();
        Fixture()throws Exception {
            sql(db,"UPDATE clients SET scope='profile.read settings.write activity.read activity.write' WHERE clientId='website_v3'");
            sql(db,"UPDATE tokens SET scope='profile.read settings.write activity.read activity.write' WHERE oId IN ('100','102')");
            sql(db,"CREATE TABLE save_slots(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,revision BIGINT,updatedAt BIGINT,byteLength INT,sha256 TEXT,data TEXT,UNIQUE(userId,gameId))");
            sql(db,"CREATE TABLE legacy_cloud(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");
            sql(db,"CREATE TABLE receipts(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            final var saves=new ApiV3GameSaveRepository(){public String getName(){return "save_slots";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{final var result=selectSQL(q,a);if(revokeAfterRead&&!active()){revokeAfterRead=false;sql(db,"UPDATE tokens SET state=2 WHERE oId='100'");}return result;}};
            final var legacy=new CloudRepository(){public String getName(){return "legacy_cloud";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}};
            final var receipts=new ApiV3RequestRepository(){
                public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}
                public JSONObject getForUpdate(String id)throws RepositoryException{final var rows=selectSQL("SELECT * FROM receipts WHERE oId=? FOR UPDATE",id);return rows.isEmpty()?null:rows.getFirst();}
                public String add(JSONObject row)throws RepositoryException{sql(writer,"INSERT INTO receipts VALUES(?,?,?,?,?,?)",row.getString("oId"),row.getString("userId"),row.getString("operation"),row.getString("requestHash"),row.getString("responseJson"),row.getLong("createdAt"));return row.getString("oId");}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException{sql(writer,"UPDATE receipts SET responseJson=? WHERE oId=?",row.getString("responseJson"),id);if(writeFault.equals("receipt"))throw new RepositoryException("private failed receipt");}
            };
            inject(service,"saves",saves);inject(service,"legacy",legacy);inject(service,"requests",receipts);
            for(String name:List.of("clients","tokens","states","users"))inject(service,name,field(proof,name));
            inject(endpoint,"service",service);inject(oldEndpoint,"gameSaves",service);inject(oldEndpoint,"cloudService",oldCloud);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){if(!Set.of("owner","other","wrong").contains(token))return null;return (token.equals("other")?other():identity()).put("scope",token.equals("wrong")?"profile.read":"activity.read activity.write");}});
        }
        @Override List<JSONObject> selectSQL(String q,Object...a)throws RepositoryException{return super.selectSQL(q.replace("OCTET_LENGTH(data)","length(CAST(data AS BLOB))").replace("BINARY gameId","gameId COLLATE BINARY"),a);}
        @Override Transaction begin(){
            final Transaction root=super.begin();if(writeFault.isEmpty())return root;final Connection previous=writer;
            writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
                try{
                    final Object result=method.invoke(previous,args);
                    if(!method.getName().equals("prepareStatement"))return result;
                    final String query=(String)args[0];final PreparedStatement statement=(PreparedStatement)result;
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
                        try{final Object value=m.invoke(statement,a);if(m.getName().equals("executeUpdate")&&query.startsWith(writeFault))throw new SQLException("private save write failure");return value;}
                        catch(InvocationTargetException error){throw error.getCause();}
                    });
                }catch(InvocationTargetException error){throw error.getCause();}
            });
            try{final var f=org.b3log.latke.repository.jdbc.JdbcTransaction.class.getDeclaredField("connection");f.setAccessible(true);f.set(tx,writer);}catch(Exception e){throw new IllegalStateException(e);}return root;
        }
        AccountCredentialVersions.Proof legacyProof(){return new AccountCredentialVersions.Proof("1",0,OLD);}
        JSONObject save(String data,long revision,String key){return service.save(identity(),GAME,input(data,revision),key);}
        long count(String table)throws Exception{return rows(db,"SELECT COUNT(*) AS n FROM "+table).getFirst().getLong("n");}
        void legacy(String id,String actor,String game,String data)throws Exception{sql(db,"INSERT INTO legacy_cloud VALUES(?,?,?,?)",id,actor,game,data);}
        org.b3log.latke.http.RequestContext http(String method,String token,String path,String body,String key)throws Exception {
            return httpBytes(method,token,path,body==null?new byte[0]:body.getBytes(java.nio.charset.StandardCharsets.UTF_8),key,"application/json");
        }
        org.b3log.latke.http.RequestContext httpBytes(String method,String token,String path,byte[] body,String key,String type)throws Exception {
            final var raw=new io.netty.handler.codec.http.DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.valueOf(method),path,io.netty.buffer.Unpooled.wrappedBuffer(body));
            try{
                if(type!=null)raw.headers().set("Content-Type",type);raw.headers().set("X-Real-IP","127.0.0.61");if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
                final var request=new org.b3log.latke.http.Request(null,raw){@Override public String getRemoteAddr(){return "127.0.0.61";}};request.setBytes(body);if(body.length>0){final var parse=org.b3log.latke.http.Request.class.getDeclaredMethod("parseForm");parse.setAccessible(true);parse.invoke(request);}request.setParams(new HashMap<>(token!=null&&token.startsWith("query")?Map.of("apiKey",token):Map.of()));
                final var c=new org.b3log.latke.http.RequestContext(request,new org.b3log.latke.http.Response(null,new io.netty.handler.codec.http.DefaultHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpResponseStatus.OK)));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{
                    if(path.equals("/api/cloud/get"))oldEndpoint.get(context);else if(path.equals("/api/cloud/sync"))oldEndpoint.sync(context);
                    else if(path.equals("/api/v3/game-save-options"))endpoint.options(context);
                    else if(path.endsWith("/migrations"))endpoint.migrate(context);else if(method.equals("GET"))endpoint.get(context);else endpoint.save(context);
                });final int reader=raw.content().readerIndex();c.handle();Assert.assertEquals(raw.content().readerIndex(),reader);return c;
            }finally{raw.release();}
        }
        @Override public void close()throws Exception{AccountCredentialVersions.clear();super.close();}
    }
    static JSONObject legacyJSON(org.b3log.latke.http.RequestContext c){return ((org.b3log.latke.http.renderer.JsonRenderer)c.getRenderer()).getJSONObject();}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        Assert.assertEquals(ApiV3GameSaveInputs.options().length(),1);Assert.assertEquals(ApiV3GameSaveInputs.options().getJSONObject(0).getString("gameId"),GAME);
        for(String game:List.of("sys-bag","sys-risk","ai_memory","emojis","40","EVOLVE-SAVE"," evolve-save","unknown"))error(422,()->ApiV3GameSaveInputs.game(game));
        for(Object bad:List.of(new JSONObject(),1,true,"x".repeat(500001),"\ud800","\udc00"))error(422,()->ApiV3GameSaveInputs.content(bad));
        Assert.assertEquals(ApiV3GameSaveInputs.content(DATA).data(),DATA);Assert.assertEquals(ApiV3GameSaveInputs.content("").byteLength(),0);Assert.assertEquals(ApiV3GameSaveInputs.content("鱼".repeat(500000)).byteLength(),1500000);
        for(Object bad:List.of("0",-1,1.5,ApiV3GameSaveInputs.MAX_REVISION+1))error(422,()->ApiV3GameSaveInputs.save(new JSONObject().put("data","x").put("expectedRevision",bad)));
        System.out.println("PASS game saves 1 exact game whitelist excludes internal/score keys; transparent Unicode/NUL/HTML/whitespace, size limits and exact safe revision validation");
        try(var f=new Fixture()){
            error(404,()->f.service.get(identity(),GAME));Assert.assertEquals(f.count("save_slots"),0);Assert.assertEquals(f.roots,0);
            final var result=f.save(DATA,0,"first");Assert.assertEquals(result.keySet(),Set.of("gameId","revision","updatedAt","byteLength","sha256"));Assert.assertEquals(result.getLong("revision"),1);
            Assert.assertEquals(f.service.get(identity(),GAME).getString("data"),DATA);Assert.assertEquals(f.count("receipts"),1);Assert.assertFalse(rows(f.db,"SELECT responseJson FROM receipts").getFirst().getString("responseJson").contains("script"));
            error(409,()->f.save("different",0,"first"));error(409,()->f.save("stale",0,"other"));
            f.service.legacySave("1",f.legacyProof(),GAME,"legacy-new");Assert.assertEquals(f.service.get(identity(),GAME).getLong("revision"),2);
            final var replay=f.save(DATA,0,"first");Assert.assertEquals(replay.toString(),result.toString());Assert.assertEquals(f.service.get(identity(),GAME).getString("data"),"legacy-new");
            final String big="鱼".repeat(500000);f.save(big,2,"large");Assert.assertEquals(f.service.get(identity(),GAME).getString("data"),big);
            Assert.assertTrue(rows(f.db,"SELECT responseJson FROM receipts WHERE operation='game-saves.save'").stream().allMatch(r->r.getString("responseJson").length()<1000));
            f.save("",3,"empty");Assert.assertEquals(f.service.get(identity(),GAME).getString("data"),"");Assert.assertEquals(f.service.get(identity(),GAME).getLong("revision"),4);
            error(404,()->f.service.get(other(),GAME));
        }System.out.println("PASS game saves 2 real owner slot/CAS and metadata receipt, exact large/empty roundtrip, cross-owner isolation and old-write/new-key replay never overwrites a newer version");
        try(var f=new Fixture()){
            f.legacy("11","1",GAME,DATA);error(404,()->f.service.get(identity(),GAME));Assert.assertEquals(f.service.legacyGet("1",f.legacyProof(),GAME),DATA);Assert.assertEquals(f.count("save_slots"),0);
            final var migrated=f.service.migrate(identity(),GAME,"migration");Assert.assertTrue(migrated.getBoolean("migrated"));Assert.assertEquals(migrated.getLong("revision"),1);Assert.assertEquals(f.count("legacy_cloud"),1);
            f.service.legacySave("1",f.legacyProof(),GAME,"later");Assert.assertEquals(f.service.migrate(identity(),GAME,"migration").toString(),migrated.toString());Assert.assertEquals(f.service.get(identity(),GAME).getString("data"),"later");
            error(409,()->f.service.migrate(identity(),GAME,"new-migration"));
        }
        try(var f=new Fixture()){error(404,()->f.service.migrate(identity(),GAME,"missing"));f.legacy("11","1",GAME,"first");f.legacy("12","1",GAME,"second");error(409,()->f.service.migrate(identity(),GAME,"duplicate"));Assert.assertEquals(f.count("save_slots"),0);Assert.assertEquals(f.count("receipts"),0);}
        try(var f=new Fixture()){f.legacy("11","1","EVOLVE-SAVE","not-the-registered-key");error(404,()->f.service.migrate(identity(),GAME,"case"));}
        System.out.println("PASS game saves 3 explicit single-row legacy migration preserves source, detects duplicates/missing/case mismatch and never replaces an existing slot or later write");
        for(String mutation:List.of("UPDATE tokens SET state=2 WHERE oId='100'","UPDATE tokens SET expiresAt=1 WHERE oId='100'","UPDATE tokens SET userId='2' WHERE oId='100'","UPDATE clients SET status=1 WHERE clientId='website_v3'","UPDATE tokens SET scope='profile.read' WHERE oId='100'","UPDATE u SET userStatus=4 WHERE oId='1'"))try(var f=new Fixture()){
            f.save("original",0,"seed");sql(f.db,mutation);error(403,()->f.service.get(identity(),GAME));error(403,()->f.save("new",1,"write"));Assert.assertEquals(f.count("receipts"),1);
        }
        try(var f=new Fixture()){f.save("old",0,"seed");f.revokeAfterRead=true;error(403,()->f.service.get(identity(),GAME));}
        try(var f=new Fixture()){sql(f.db,"INSERT INTO states VALUES('1',1,0)");error(403,()->f.service.legacySave("1",f.legacyProof(),GAME,"old-auth"));Assert.assertEquals(f.count("save_slots"),0);}
        System.out.println("PASS game saves 4 current user/client/access/scope recheck inside writes and before/after reads; expired legacy credential epoch cannot write");
        for(String fault:List.of("INSERT INTO `save_slots`","receipt","commit"))try(var f=new Fixture()){
            if(fault.equals("commit"))f.fault=fault;else f.writeFault=fault;error(503,()->f.save(DATA,0,"failed"));f.fault="";f.writeFault="";Assert.assertEquals(f.count("save_slots"),0);Assert.assertEquals(f.count("receipts"),0);f.save(DATA,0,"failed");Assert.assertEquals(f.count("save_slots"),1);
        }
        try(var f=new Fixture()){f.save("old",0,"seed");f.writeFault="UPDATE `save_slots`";error(503,()->f.service.legacySave("1",f.legacyProof(),GAME,"failed"));f.writeFault="";Assert.assertEquals(f.service.get(identity(),GAME).getString("data"),"old");Assert.assertEquals(f.service.get(identity(),GAME).getLong("revision"),1);}
        try(var f=new Fixture()){f.fault="ack";error(503,()->f.save(DATA,0,"lost"));f.fault="";Assert.assertEquals(f.save(DATA,0,"lost").getLong("revision"),1);Assert.assertEquals(f.count("receipts"),1);}
        System.out.println("PASS game saves 5 slot/receipt/legacy update/strict commit rollback and lost-ACK same-key recovery preserve original data, one version and one receipt");
        try(var f=new Fixture()){
            f.save("original",0,"seed");sql(f.db,"UPDATE save_slots SET sha256=?","0".repeat(64));error(503,()->f.service.get(identity(),GAME));
        }
        System.out.println("PASS game saves 6 stored metadata/data integrity fails closed instead of returning partial or corrupted payloads");
        try(var f=new Fixture()){
            ApiV3GameSaveProcessor.registerRoutes(f.endpoint,f.auth,f.writes);CloudProcessor.registerRoutes(f.oldEndpoint,new org.b3log.symphony.processor.middleware.LoginCheckMidware());org.b3log.latke.http.Dispatcher.mapping();
            final String path="/api/v3/me/game-saves/"+GAME;
            Assert.assertEquals(f.http("GET",null,path,null,null).attr("test.status"),401);Assert.assertEquals(f.http("GET","wrong",path,null,null).attr("test.status"),403);
            final var options=f.http("GET","owner","/api/v3/game-save-options",null,null);Assert.assertEquals(options.attr("test.status"),200);Assert.assertFalse(((JSONObject)options.attr("test.data")).toString().contains("sys-"));
            Assert.assertEquals(f.http("PUT","owner",path,input(DATA,0).toString(),"http-save").attr("test.status"),200);
            Assert.assertEquals(f.http("GET","owner",path,null,null).attr("test.status"),200);Assert.assertEquals(f.http("GET","owner",path,null,null).getResponse().getHeader("Cache-Control"),"no-store");
            Assert.assertEquals(f.http("GET","owner","/api/v3/me/game-saves/sys-bag",null,null).attr("test.status"),422);
            Assert.assertEquals(f.http("PUT","owner",path,input("x",1).put("userId","2").toString(),"owner-forgery").attr("test.status"),422);
            org.b3log.symphony.util.Sessions.current=new JSONObject().put("oId","1").put("userName","owner");
            Assert.assertEquals(f.http("GET",null,path,null,null).attr("test.status"),401);
            final var oldGet=legacyJSON(f.http("POST",null,"/api/cloud/get",new JSONObject().put("gameId",GAME).toString(),null));Assert.assertEquals(oldGet.getInt("code"),0);Assert.assertEquals(oldGet.getString("data"),DATA);
            final var oldSave=legacyJSON(f.http("POST",null,"/api/cloud/sync",new JSONObject().put("gameId",GAME).put("data","old-http").toString(),null));Assert.assertEquals(oldSave.getInt("code"),0);Assert.assertEquals(f.service.get(identity(),GAME).getLong("revision"),2);
            org.b3log.symphony.util.Sessions.current=null;
            Assert.assertEquals(legacyJSON(f.http("POST","query2","/api/cloud/sync",new JSONObject().put("gameId",GAME).put("data","owner-two").toString(),null)).getInt("code"),0);Assert.assertEquals(f.service.get(other(),GAME).getString("data"),"owner-two");
            Assert.assertEquals(legacyJSON(f.http("POST",null,"/api/cloud/get",new JSONObject().put("apiKey","body1").put("gameId",GAME).toString(),null)).getString("data"),"old-http");
            org.b3log.symphony.util.Sessions.current=new JSONObject().put("oId","1").put("userName","owner");
            f.http("POST",null,"/api/cloud/sync",new JSONObject().put("gameId","40").put("data",new JSONObject().put("stats",new JSONObject())).toString(),null);Assert.assertEquals(f.oldCloud.calls,1);Assert.assertEquals(f.oldCloud.lastGame,"40");
            for(String mime:List.of("text/plain","application/x-www-form-urlencoded","")){
                final var rejected=legacyJSON(f.httpBytes("POST",null,"/api/cloud/sync",new JSONObject().put("gameId",GAME).put("data","bad-mime").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),null,mime.isEmpty()?null:mime));
                Assert.assertNotEquals(rejected.getInt("code"),0);
            }
            final byte[] badLegacy=new JSONObject().put("gameId",GAME).put("data","X").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for(int i=0;i<badLegacy.length;i++)if(badLegacy[i]=='X')badLegacy[i]=(byte)0xff;
            Assert.assertNotEquals(legacyJSON(f.httpBytes("POST",null,"/api/cloud/sync",badLegacy,null,"application/json")).getInt("code"),0);
            Assert.assertEquals(legacyJSON(f.httpBytes("POST",null,"/api/cloud/get",new JSONObject().put("gameId",GAME).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),null,"text/plain")).getString("data"),"old-http");
            final int calls=f.oldCloud.calls;
            f.httpBytes("POST",null,"/api/cloud/sync",new JSONObject().put("gameId","40").put("data",new JSONObject()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),null,"text/plain");Assert.assertEquals(f.oldCloud.calls,calls+1);
            Assert.assertEquals(f.service.get(identity(),GAME).getString("data"),"old-http");
            org.b3log.symphony.util.Sessions.current=null;
            final byte[] malformed="{\"data\":\"X\",\"expectedRevision\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8);malformed[9]=(byte)0xff;
            Assert.assertEquals(f.httpBytes("PUT","owner",path,malformed,"invalid-utf8","application/json").attr("test.status"),400);
            for(String invalid:List.of("{\"data\":\"a\",\"data\":\"b\",\"expectedRevision\":2}","%7B%22data%22%3A%22x%22%2C%22expectedRevision%22%3A2%7D","{\"data\":\"\\ud800\",\"expectedRevision\":2}"))
                Assert.assertEquals(f.http("PUT","owner",path,invalid,"invalid-json").attr("test.status"),400);
            Assert.assertEquals(f.httpBytes("PUT","owner",path,input("x",2).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),"wrong-mime","text/plain").attr("test.status"),415);
            Assert.assertEquals(f.httpBytes("PUT","owner",path,new byte[ApiV3GameSaveInputs.MAX_REQUEST_BYTES+1],"too-large","application/json").attr("test.status"),413);
            Assert.assertEquals(f.service.get(identity(),GAME).getLong("revision"),2);
            Assert.assertEquals(f.http("PUT","owner",path,input("\ufffd",2).toString(),"valid-replacement-char").attr("test.status"),200);
            Assert.assertEquals(f.service.get(identity(),GAME).getString("data"),"\ufffd");
        }
        System.out.println("PASS game saves 7 actual four v3 routes/strict auth/JSON and real legacy LoginCheck/CloudProcessor bridges use Cookie/query/body identity; score key keeps original branch");
    }
}
