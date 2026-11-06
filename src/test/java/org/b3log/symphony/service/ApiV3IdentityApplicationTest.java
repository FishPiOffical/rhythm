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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import org.b3log.latke.http.*;
import org.b3log.symphony.processor.ApiV3IdentityApplicationProcessor;
import io.netty.handler.codec.http.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** Real first-party SQL authorization + strict transaction and one-shot material state; SQLite strips FOR UPDATE. */
public class ApiV3IdentityApplicationTest {
    static byte[] image(String format)throws Exception {
        final var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);final var out=new ByteArrayOutputStream();
        Assert.assertTrue(javax.imageio.ImageIO.write(image,format,out));return out.toByteArray();
    }
    static JSONObject reviewer(){return new JSONObject().put("tokenId","102").put("user",new JSONObject().put("oId","2"));}
    static class Fixture extends F {
        final ApiV3IdentityApplicationService service=new ApiV3IdentityApplicationService(){protected long now(){return NOW;}};
        final ApiV3IdentityApplicationProcessor endpoint=new ApiV3IdentityApplicationProcessor();byte[] sent;String writeFault="";
        Fixture()throws Exception {
            sql(db,"UPDATE tokens SET scope='profile.read settings.write' WHERE oId='102'");
            sql(db,"CREATE TABLE identity_app(oId TEXT PRIMARY KEY,userId TEXT,type TEXT,status TEXT,pendingOwnerId TEXT UNIQUE,createdAt BIGINT,materialReadAt BIGINT,reviewedAt BIGINT,note TEXT)");
            sql(db,"CREATE TABLE identity_material(oId TEXT PRIMARY KEY,userId TEXT,mediaType TEXT,byteLength INT,sha256 TEXT,data TEXT)");
            sql(db,"CREATE TABLE identity_reviewer(oId TEXT PRIMARY KEY,userId TEXT)");sql(db,"INSERT INTO identity_reviewer VALUES('enterprise','2')");
            sql(db,"CREATE TABLE receipt(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            final var apps=new ApiV3IdentityApplicationRepository(){public String getName(){return "identity_app";}public List<JSONObject> select(String q,Object...args)throws RepositoryException{return selectSQL(q,args);}};
            final var materials=new ApiV3IdentityMaterialRepository(){public String getName(){return "identity_material";}public List<JSONObject> select(String q,Object...args)throws RepositoryException{return selectSQL(q,args);}};
            final var reviewers=new ApiV3IdentityReviewerRepository(){public String getName(){return "identity_reviewer";}public List<JSONObject> select(String q,Object...args)throws RepositoryException{return selectSQL(q,args);}};
            final var requests=new ApiV3RequestRepository(){
                public Transaction beginTransaction(){return begin();}public boolean hasTransactionBegun(){return active();}
                public JSONObject getForUpdate(String id)throws RepositoryException{final var found=selectSQL("SELECT * FROM receipt WHERE oId=? FOR UPDATE",id);return found.isEmpty()?null:found.getFirst();}
                public String add(JSONObject row)throws RepositoryException {sql(writer,"INSERT INTO receipt VALUES(?,?,?,?,?,?)",row.getString("oId"),row.getString("userId"),row.getString("operation"),row.getString("requestHash"),row.getString("responseJson"),row.getLong("createdAt"));return row.getString("oId");}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException {sql(writer,"UPDATE receipt SET responseJson=? WHERE oId=?",row.getString("responseJson"),id);if(fault.equals("receipt"))throw new RepositoryException("private fixture fault");}
            };
            inject(service,"applications",apps);inject(service,"materials",materials);inject(service,"reviewers",reviewers);inject(service,"requests",requests);inject(service,"proofs",proof);
            inject(endpoint,"applications",service);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){
                if(!Set.of("owner","reviewer","scope").contains(token))return null;
                final JSONObject value=token.equals("reviewer")?reviewer():identity();
                value.put("scope",token.equals("scope")?"content.read":"profile.read settings.write");return value;
            }});
        }
        @Override Transaction begin(){
            final Transaction root=super.begin();final java.sql.Connection previous=writer;
            writer=(java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.Connection.class},(proxy,method,args)->{
                try{
                    if(method.getName().equals("prepareStatement")){
                        final String query=(String)args[0];final var statement=(java.sql.PreparedStatement)method.invoke(previous,args);
                        return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.PreparedStatement.class},(p,m,a)->{
                            try{final Object value=m.invoke(statement,a);
                                if(m.getName().equals("executeUpdate")&&!writeFault.isEmpty()&&query.startsWith(writeFault))throw new java.sql.SQLException("private write fault");
                                return value;
                            }catch(java.lang.reflect.InvocationTargetException error){throw error.getCause();}
                        });
                    }
                    return method.invoke(previous,args);
                }catch(java.lang.reflect.InvocationTargetException error){throw error.getCause();}
            });
            try{var field=org.b3log.latke.repository.jdbc.JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,writer);}
            catch(Exception error){throw new IllegalStateException(error);}return root;
        }
        RequestContext request(String method,String path,String token,String media,String key,byte[] bytes)throws Exception {
            sent=null;final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.valueOf(method),path);
            try{
                if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(media!=null)raw.headers().set("Content-Type",media);
                if(key!=null)raw.headers().set("Idempotency-Key",key);raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","127.0.0.1");
                raw.content().writeBytes(bytes==null?new byte[0]:bytes);
                final Request request=new Request(null,raw);
                // Exercise the bundled framework's lossy preprocessing instead of injecting pristine bytes.
                final var parse=Request.class.getDeclaredMethod("parseForm");parse.setAccessible(true);parse.invoke(request);
                if("image/png".equals(media)||"image/jpeg".equals(media)) {
                    if(bytes!=null&&bytes.length>0&&(bytes[0]&255)>=128)Assert.assertFalse(Arrays.equals(request.getBytes(),bytes),"fixture must expose framework binary rewriting");
                }
                request.setParams(new HashMap<>(Map.of("apiKey","legacy")));
                final Response response=new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)){public void sendBytes(byte[] body){sent=body;}};
                final RequestContext context=new RequestContext(request,response);context.addHandler(new org.b3log.latke.http.handler.RouteHandler());
                context.addHandler(c->{final var match=(org.b3log.latke.http.handler.RouteResolution)c.attr("MATCH_RESULT");
                    switch(match.getContextHandlerMeta().getInvokeHolder().getName()){
                        case "create"->endpoint.create(c);case "list"->endpoint.list(c);case "detail"->endpoint.detail(c);
                        case "reviewList"->endpoint.reviewList(c);case "open"->endpoint.open(c);case "decide"->endpoint.decide(c);default->throw new AssertionError();
                    }});context.handle();return context;
            }finally{raw.release();}
        }
        int count(String table)throws Exception{return rows(db,"SELECT COUNT(*) AS n FROM "+table).getFirst().getInt("n");}
        JSONObject submit(byte[] bytes,String key){return service.create(identity(),bytes,"image/png",key);}
    }
    public static void main(String[] args)throws Exception {
        final var p=new Properties();p.setProperty("runtimeDatabase","NONE");p.setProperty("contextPath","");Latkes.setLatkeProps(p);Latkes.setLocalProps(p);
        final byte[] png=image("png"),jpeg=image("jpg");
        Assert.assertEquals(ApiV3IdentityImage.validate(png,"image/png").bytes(),png);Assert.assertEquals(ApiV3IdentityImage.validate(jpeg,"image/jpeg").bytes(),jpeg);
        error(415,()->ApiV3IdentityImage.validate(png,"image/gif"));error(422,()->ApiV3IdentityImage.validate(png,"image/jpeg"));
        error(422,()->ApiV3IdentityImage.validate(new byte[]{-1,-40,-1,-39},"image/jpeg"));
        error(413,()->ApiV3IdentityImage.validate(new byte[ApiV3IdentityImage.MAX_BYTES+1],"image/png"));
        error(422,()->ApiV3IdentityImage.validate(Arrays.copyOf(png,png.length+4),"image/png"));
        System.out.println("PASS identity 1 real JPEG/PNG in-memory decoder, exact bytes and digest, MIME/magic/truncation/trailing payload and size boundaries");
        try(var f=new Fixture()) {
            final var first=f.submit(png,"same");Assert.assertEquals(first.keySet(),Set.of("id","createdAt"));
            Assert.assertEquals(f.count("identity_app"),1);Assert.assertEquals(f.count("identity_material"),1);Assert.assertEquals(f.count("receipt"),1);
            final var stored=rows(f.db,"SELECT * FROM identity_material").getFirst();Assert.assertEquals(Base64.getDecoder().decode(stored.getString("data")),png);
            final var status=f.service.detail(identity(),first.getString("id"));Assert.assertEquals(status.getString("status"),"pending");Assert.assertFalse(status.has("applicantId"));
            Assert.assertFalse(status.toString().contains("data"));Assert.assertFalse(status.toString().contains("sha256"));
            Assert.assertEquals(f.submit(png,"same").toString(),first.toString());Assert.assertEquals(f.count("identity_material"),1);
            error(409,()->f.submit(png,"new"));error(409,()->f.service.create(identity(),jpeg,"image/jpeg","same"));
            error(404,()->f.service.detail(reviewer(),first.getString("id")));
            error(403,()->f.service.list(identity(),1,20,true,"pending"));
            final var listed=f.service.list(reviewer(),1,20,true,"pending");Assert.assertEquals(listed.getJSONArray("list").getJSONObject(0).getString("applicantId"),"1");
            Assert.assertEquals(f.locks.subList(0,5),List.of("namespace","client","user","token","epoch"));
        }
        System.out.println("PASS identity 2 atomic metadata/material/receipt, same-key replay, one pending owner, owner isolation and stable reviewer-only list; no image/hash in metadata projection");
        try(var f=new Fixture()) {
            final var first=f.submit(png,"open");final String id=first.getString("id");
            error(403,()->f.service.open(identity(),id));Assert.assertEquals(f.count("identity_material"),1);
            final var opened=f.service.open(reviewer(),id);Assert.assertEquals(opened.bytes(),png);Assert.assertEquals(opened.mediaType(),"image/png");
            Assert.assertEquals(f.count("identity_material"),0);Assert.assertEquals(f.service.detail(identity(),id).getLong("materialReadAt"),NOW);
            Assert.assertEquals(f.service.detail(identity(),id).getString("status"),"pending");
            error(410,()->f.service.open(reviewer(),id));error(409,()->f.submit(png,"pending-viewed"));
            Assert.assertEquals(f.submit(png,"open").toString(),first.toString());Assert.assertEquals(f.count("identity_material"),0);
        }
        System.out.println("PASS identity 3 reviewer read commits material deletion and read time before returning bytes; second read410, applicant cannot read, create replay never restores consumed material and viewed application remains pending");
        for(String bad:List.of("UPDATE tokens SET clientId='third' WHERE oId='100'","UPDATE tokens SET state=2 WHERE oId='100'","UPDATE tokens SET scope='profile.read' WHERE oId='100'","UPDATE clients SET status=1 WHERE clientId='website_v3'","UPDATE u SET userStatus=1 WHERE oId='1'"))try(var f=new Fixture()) {
            sql(f.db,bad);error(403,()->f.submit(png,"denied"));Assert.assertEquals(f.count("identity_app"),0);Assert.assertEquals(f.count("receipt"),0);
        }
        try(var f=new Fixture()) {
            final var first=f.submit(png,"disable-replay");sql(f.db,"UPDATE tokens SET state=2 WHERE oId='100'");
            error(403,()->f.submit(png,"disable-replay"));error(403,()->f.service.detail(identity(),first.getString("id")));
        }
        System.out.println("PASS identity 4 real first-party current client/access/user/scope authorization, revoked identity cannot create, replay or read its former records");
        for(String fault:List.of("receipt","commit","read"))try(var f=new Fixture()) {
            f.fault=fault;error(503,()->f.submit(png,"fault"));Assert.assertEquals(f.count("identity_app"),0);Assert.assertEquals(f.count("identity_material"),0);Assert.assertEquals(f.count("receipt"),0);
        }
        try(var f=new Fixture()) {
            final var first=f.submit(png,"consume-fault");f.fault="commit";error(503,()->f.service.open(reviewer(),first.getString("id")));
            Assert.assertEquals(f.count("identity_material"),1);f.fault="";Assert.assertEquals(f.service.detail(identity(),first.getString("id")).getLong("materialReadAt"),0L);
            f.fault="ack";error(503,()->f.service.open(reviewer(),first.getString("id")));Assert.assertEquals(f.count("identity_material"),0);
            f.fault="";error(410,()->f.service.open(reviewer(),first.getString("id")));
        }
        System.out.println("PASS identity 5 metadata/material/receipt rollback together; failed consume commit retains bytes, lost successful ACK consumes forever without exposing a retry copy");
        try(var f=new Fixture()) {
            ApiV3IdentityApplicationProcessor.registerRoutes(f.endpoint,f.auth,f.writes);Dispatcher.mapping();
            final String create="/api/v3/me/identity-applications/enterprise";
            Assert.assertEquals(f.request("POST",create,null,"image/png","http",png).attr("test.status"),401);
            Assert.assertEquals(f.request("POST",create,"scope","image/png","http",png).attr("test.status"),403);
            Assert.assertEquals(f.request("POST",create,"owner","application/json","http",png).attr("test.status"),415);
            Assert.assertEquals(f.request("POST",create,"owner","image/png",null,png).attr("test.status"),422);
            final var created=f.request("POST",create,"owner","image/png","http",png);Assert.assertEquals(created.attr("test.status"),200);
            final String id=((JSONObject)created.attr("test.data")).getString("id");
            Assert.assertEquals(f.request("GET","/api/v3/me/identity-applications/"+id,"owner",null,null,null).attr("test.status"),200);
            Assert.assertEquals(f.request("GET","/api/v3/review/identity-applications","owner",null,null,null).attr("test.status"),403);
            Assert.assertEquals(f.request("GET","/api/v3/review/identity-applications","reviewer",null,null,null).attr("test.status"),200);
            final String open="/api/v3/review/identity-applications/"+id+"/material/open";
            final String decide="/api/v3/review/identity-applications/"+id+"/decision";
            final byte[] decision="{\"status\":\"approved\",\"note\":\"已核对\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Assert.assertEquals(f.request("PUT",decide,"reviewer","application/json","review-http",decision).attr("test.status"),409);
            final var response=f.request("POST",open,"reviewer",null,null,null);
            Assert.assertEquals(f.sent,png);Assert.assertEquals(response.getResponse().getContentType(),"image/png");
            Assert.assertEquals(response.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(response.getResponse().getHeader("X-Content-Type-Options"),"nosniff");
            Assert.assertTrue(response.getResponse().getHeader("Content-Disposition").endsWith(".png\""));
            Assert.assertEquals(f.request("POST",open,"reviewer",null,null,null).attr("test.status"),410);Assert.assertNull(f.sent);
            Assert.assertEquals(f.request("PUT",decide,"owner","application/json","review-http",decision).attr("test.status"),403);
            Assert.assertEquals(f.request("PUT",decide,"reviewer","text/plain","review-http",decision).attr("test.status"),415);
            final var conclusion=f.request("PUT",decide,"reviewer","application/json","review-http",decision);
            Assert.assertEquals(conclusion.attr("test.status"),200);Assert.assertEquals(((JSONObject)conclusion.attr("test.data")).getString("status"),"approved");
            Assert.assertEquals(conclusion.getResponse().getHeader("Cache-Control"),"no-store");
        }
        System.out.println("PASS identity 6 actual Dispatcher+Bearer+write gates, binary upload/MIME/key checks, owner/reviewer metadata and private no-store one-shot image response");
        final var filter=new ApiV3OAuthTransportLogFilter();
        for(String path:List.of("/api/v3/me/identity-applications/enterprise","%2Fapi%2Fv3%2Freview%2Fidentity-applications/1/material/open")) {
            final var event=org.apache.logging.log4j.core.impl.Log4jLogEvent.newBuilder().setLoggerName("org.b3log.latke.http.Request")
                    .setMessage(new org.apache.logging.log4j.message.SimpleMessage("PRIVATE_MATERIAL "+path)).build();
            Assert.assertEquals(filter.filter(event),org.apache.logging.log4j.core.Filter.Result.DENY);
        }
        System.out.println("PASS identity 7 actual transport filter suppresses malformed private upload/material request diagnostics, including encoded paths");
        try(var f=new Fixture()) {
            final String create="/api/v3/me/identity-applications/enterprise";
            Assert.assertEquals(f.request("POST",create,"owner","image/png","large",new byte[ApiV3IdentityImage.MAX_BYTES+1]).attr("test.status"),413);
            Assert.assertEquals(f.count("identity_app"),0);Assert.assertEquals(f.count("identity_material"),0);
            final var created=f.request("POST",create,"owner","image/jpeg","raw-jpeg",jpeg);
            Assert.assertEquals(created.attr("test.status"),200);
            final String id=((JSONObject)created.attr("test.data")).getString("id");
            final var response=f.request("POST","/api/v3/review/identity-applications/"+id+"/material/open","reviewer",null,null,null);
            Assert.assertEquals(f.sent,jpeg);Assert.assertEquals(response.getResponse().getContentType(),"image/jpeg");
            Assert.assertEquals(f.count("identity_material"),0);
        }
        System.out.println("PASS identity 12 raw Netty PNG/JPEG survive actual parseForm corruption; oversized raw body413 before storing application/material");
        for(String fault:List.of("INSERT INTO `identity_app`","INSERT INTO `identity_material`"))try(var f=new Fixture()) {
            f.writeFault=fault;error(503,()->f.submit(png,"partial-create"));
            Assert.assertEquals(f.count("identity_app"),0);Assert.assertEquals(f.count("identity_material"),0);Assert.assertEquals(f.count("receipt"),0);
        }
        for(String fault:List.of("DELETE FROM `identity_material`","UPDATE `identity_app` SET materialReadAt"))try(var f=new Fixture()) {
            final String id=f.submit(png,"partial-open").getString("id");f.writeFault=fault;error(503,()->f.service.open(reviewer(),id));
            Assert.assertEquals(f.count("identity_material"),1);f.writeFault="";Assert.assertEquals(f.service.detail(identity(),id).getLong("materialReadAt"),0L);
        }
        try(var f=new Fixture()) {
            final String id=f.submit(png,"corrupt").getString("id");sql(f.db,"UPDATE identity_material SET sha256='invalid'");
            error(503,()->f.service.open(reviewer(),id));Assert.assertEquals(f.count("identity_material"),1);
        }
        System.out.println("PASS identity 8 real JDBC faults after each metadata/material insert/delete/read-marker update roll back all partial writes; corrupt private hash fails without consuming material");
        for(String status:List.of("approved","rejected"))try(var f=new Fixture()) {
            final String id=f.submit(png,"decide").getString("id");final JSONObject body=new JSONObject().put("status",status).put("note","  已核对 \" & 😀  ");
            error(403,()->f.service.decide(identity(),id,body,"decision"));
            error(409,()->f.service.decide(reviewer(),id,body,"decision"));Assert.assertEquals(f.count("receipt"),1);Assert.assertEquals(f.count("identity_material"),1);
            f.service.open(reviewer(),id);final var result=f.service.decide(reviewer(),id,body,"decision");
            Assert.assertEquals(result.keySet(),Set.of("id","status","reviewedAt","note"));Assert.assertEquals(result.getString("status"),status);Assert.assertEquals(result.getString("note"),"已核对 \" & 😀");
            Assert.assertEquals(f.service.detail(identity(),id).getString("status"),status);Assert.assertEquals(f.count("identity_material"),0);
            Assert.assertEquals(f.service.decide(reviewer(),id,body,"decision").toString(),result.toString());
            error(409,()->f.service.decide(reviewer(),id,body,"new-decision"));
            error(409,()->f.service.decide(reviewer(),id,new JSONObject().put("status",status.equals("approved")?"rejected":"approved"),"decision"));
            final String next=f.submit(png,"next-application").getString("id");Assert.assertNotEquals(next,id);Assert.assertEquals(f.count("identity_app"),2);
            Assert.assertEquals(f.service.decide(reviewer(),id,body,"decision").toString(),result.toString());Assert.assertEquals(f.service.detail(identity(),next).getString("status"),"pending");
            Assert.assertEquals(rows(f.db,"SELECT userStatus,userPassword FROM u WHERE oId='1'").getFirst().getString("userPassword"),OLD);
        }
        System.out.println("PASS identity 9 both decisions require consumed material, release pending owner, retain conclusion only and replay once without changing later applications or account credentials");
        for(String fault:List.of("receipt","commit","UPDATE `identity_app` SET status"))try(var f=new Fixture()) {
            final String id=f.submit(png,"decision-fault").getString("id");f.service.open(reviewer(),id);
            if(fault.startsWith("UPDATE"))f.writeFault=fault;else f.fault=fault;
            error(503,()->f.service.decide(reviewer(),id,new JSONObject().put("status","approved"),"fail"));
            f.fault="";f.writeFault="";Assert.assertEquals(f.service.detail(identity(),id).getString("status"),"pending");Assert.assertEquals(f.count("receipt"),1);
            error(409,()->f.submit(png,"pending-after-failure"));
        }
        try(var f=new Fixture()) {
            final String id=f.submit(png,"decision-ack").getString("id");f.service.open(reviewer(),id);f.fault="ack";
            error(503,()->f.service.decide(reviewer(),id,new JSONObject().put("status","approved"),"ack"));f.fault="";
            Assert.assertEquals(f.service.decide(reviewer(),id,new JSONObject().put("status","approved"),"ack").getString("status"),"approved");Assert.assertEquals(f.count("receipt"),2);
        }
        System.out.println("PASS identity 10 decision update/receipt/commit failures retain pending eligibility; lost ACK recovers original conclusion without reapplying");
        try(var f=new Fixture()) {
            for(JSONObject bad:List.of(new JSONObject(),new JSONObject().put("status","pending"),new JSONObject().put("status","approved").put("extra",1),
                    new JSONObject().put("status","approved").put("note",JSONObject.NULL),new JSONObject().put("status","approved").put("note","x".repeat(257)),
                    new JSONObject().put("status","approved").put("note","<b>text</b>"),new JSONObject().put("status","approved").put("note","x\n"))) {
                error(422,()->f.service.decide(reviewer(),"1",bad,"bad"));
            }
        }
        System.out.println("PASS identity 11 decision whitelist/plain-text/UTF16/control/type validation before mutation");
    }
}
