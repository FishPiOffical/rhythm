package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** Actual QR persistence SQL, first-party authorization and strict commit; token pair insertion is an explicit transaction port. */
public class ApiV3AppLoginServiceTest {
    static final String RAW=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    static final String PROOF=Base64.getUrlEncoder().withoutPadding().encodeToString("01234567890123456789012345678901".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    static Object field(Object target,String name)throws Exception {Class<?> c=target.getClass();while(c!=null){try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(name);}
    static class Fixture extends F {
        org.b3log.symphony.processor.ApiV3AppLoginProcessor endpoint;long clock=NOW;int issued;String issueFault="";boolean changeHint,realGrant;
        final ApiV3AppLoginService service=new ApiV3AppLoginService(){
            protected long now(){return clock;}
            protected String handle(){return RAW;}
            protected JSONObject issue(String actor,String scope)throws Exception {
                issued++;
                if(realGrant)return super.issue(actor,scope);
                sql(writer,"INSERT INTO tokens(oId,userId,clientId,tokenType,state,expiresAt,scope,updatedAt) VALUES('900',?,'fishpi_app_v3','access',0,?,?,?)",actor,NOW+900000,scope,NOW);
                if(issueFault.equals("half"))throw new RepositoryException("private failed token pair");
                sql(writer,"INSERT INTO tokens(oId,userId,clientId,tokenType,state,expiresAt,scope,updatedAt) VALUES('901',?,'fishpi_app_v3','refresh',0,?,?,?)",actor,NOW+2592000000L,scope,NOW);
                return new JSONObject().put("accessToken",issueFault.equals("response")?"bad":"A".repeat(43)).put("refreshToken","B".repeat(43))
                    .put("scope",scope).put("tokenType","Bearer").put("expiresIn",900).put("refreshExpiresIn",2592000).put("internal","omit");
            }
        };
        Fixture()throws Exception {
            sql(db,"ALTER TABLE u ADD COLUMN userLatestLoginTime BIGINT DEFAULT 7");
            sql(db,"ALTER TABLE u ADD COLUMN userName TEXT");sql(db,"ALTER TABLE u ADD COLUMN userNickname TEXT");sql(db,"ALTER TABLE u ADD COLUMN userAvatarURL TEXT");
            sql(db,"UPDATE u SET userName='owner',userNickname='Owner',userAvatarURL='https://images.example/avatar.png' WHERE oId='1'");
            sql(db,"INSERT INTO clients VALUES('fishpi_app_v3',0,'profile.read settings.write')");
            sql(db,"CREATE TABLE qr(oId TEXT PRIMARY KEY,userId TEXT,issuerTokenId TEXT,credentialEpoch BIGINT,scope TEXT,state TEXT,codeChallenge TEXT,createdAt BIGINT,expiresAt BIGINT,updatedAt BIGINT)");
            final var repository=new ApiV3AppLoginRequestRepository(){
                public String getName(){return "qr";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){if(fault.equals("begin"))throw new IllegalStateException("private connection acquisition failure");return begin();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}
                public JSONObject hint(String hash)throws RepositoryException {final JSONObject result=super.hint(hash);if(changeHint){changeHint=false;sql(db,"UPDATE qr SET scope='profile.read' WHERE oId=?",hash);}return result;}
            };
            inject(service,"requests",repository);inject(service,"proofs",proof);
            for(String column:List.of("tokenHash TEXT","familyId TEXT","parentId TEXT","createdAt BIGINT","lastUsedAt BIGINT"))sql(db,"ALTER TABLE tokens ADD COLUMN "+column);
            final var nativeTokens=new ApiV3TokenRepository(){
                public String getName(){return "tokens";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return selectSQL(q,a);}
                public String add(JSONObject row)throws RepositoryException {
                    sql(writer,"INSERT INTO tokens(oId,userId,clientId,tokenType,state,expiresAt,scope,updatedAt,tokenHash,familyId,parentId,createdAt,lastUsedAt) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        row.getString("oId"),row.getString("userId"),row.getString("clientId"),row.getString("tokenType"),row.getInt("state"),row.getLong("expiresAt"),row.getString("scope"),row.getLong("updatedAt"),row.getString("tokenHash"),row.getString("familyId"),row.getString("parentId"),row.getLong("createdAt"),row.getLong("lastUsedAt"));return row.getString("oId");
                }
                public void update(String id,JSONObject row,String...fields)throws RepositoryException {sql(writer,"UPDATE tokens SET state=?,updatedAt=?,lastUsedAt=? WHERE oId=?",row.getInt("state"),row.getLong("updatedAt"),row.optLong("lastUsedAt"),id);}
            };
            final var nativeGrants=new ApiV3TokenMgmtService(){protected long now(){return clock;}};
            inject(nativeGrants,"apiV3TokenRepository",nativeTokens);inject(nativeGrants,"apiV3ClientRepository",field(proof,"clients"));inject(nativeGrants,"userRepository",field(proof,"users"));inject(service,"grants",nativeGrants);
            for(String name:List.of("clients","tokens","states","users"))inject(service,name,field(proof,name));
        }
        String create(){final var value=service.create(identity());Assert.assertEquals(value.getString("request"),RAW);Assert.assertEquals(value.getLong("expiresAt"),NOW+120000);Assert.assertEquals(value.keySet(),Set.of("request","qrPayload","expiresAt","state"));return RAW;}
        void approved(){create();service.claim(RAW,ApiV3AppLoginProtocol.challenge(PROOF));service.appDecision(RAW,PROOF,"confirm");service.webDecision(identity(),RAW,"approve");}
        JSONObject row()throws Exception{return rows(db,"SELECT * FROM qr").getFirst();}
        int pairs()throws Exception{return rows(db,"SELECT COUNT(*) AS n FROM tokens WHERE clientId='fishpi_app_v3'").getFirst().getInt("n");}
        org.b3log.latke.http.RequestContext post(String token,String suffix,String body)throws Exception {
            final var raw=new io.netty.handler.codec.http.DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/auth/app-login/"+suffix,io.netty.buffer.Unpooled.copiedBuffer(body,java.nio.charset.StandardCharsets.UTF_8));
            try {raw.headers().set("Content-Type","application/json");raw.headers().set("X-Real-IP","127.0.0.51");raw.headers().set("Cookie","legacy-cookie");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new org.b3log.latke.http.Request(null,raw);request.setBytes(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));request.setParams(new HashMap<>(Map.of("apiKey","legacy")));
                final var c=new org.b3log.latke.http.RequestContext(request,new org.b3log.latke.http.Response(null,new io.netty.handler.codec.http.DefaultHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpResponseStatus.OK)));
                c.attr("user",new JSONObject().put("oId","2"));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{switch(suffix){case "requests"->endpoint.create(context);case "status"->endpoint.webStatus(context);case "decision"->endpoint.webDecision(context);case "claim"->endpoint.claim(context);case "app-status"->endpoint.appStatus(context);case "app-decision"->endpoint.appDecision(context);case "exchange"->endpoint.exchange(context);default->throw new AssertionError("unhandled QR route");}});c.handle();return c;
            }finally{raw.release();}
        }
    }
    public static void main(String[] args)throws Exception {
        final var properties=new Properties();properties.setProperty("runtimeDatabase","NONE");properties.setProperty("contextPath","");Latkes.setLatkeProps(properties);Latkes.setLocalProps(properties);
        try(var f=new Fixture()){
            f.create();Assert.assertEquals(f.row().getString("oId"),ApiV3AppLoginProtocol.handleHash(RAW));Assert.assertFalse(f.row().toString().contains(RAW));
            Assert.assertEquals(f.service.webStatus(identity(),RAW).getString("state"),"waiting");
            error(409,()->f.service.webDecision(identity(),RAW,"approve"));error(403,()->f.service.appStatus(RAW,PROOF));
            final var claimed=f.service.claim(RAW,ApiV3AppLoginProtocol.challenge(PROOF));Assert.assertEquals(claimed.getJSONObject("account").keySet(),Set.of("id","userName","userNickname","userAvatarURL"));
            Assert.assertEquals(f.service.appStatus(RAW,PROOF).getString("state"),"claimed");
            error(409,()->f.service.claim(RAW,ApiV3AppLoginProtocol.challenge(RAW)));error(403,()->f.service.appDecision(RAW,RAW,"confirm"));
            f.service.appDecision(RAW,PROOF,"confirm");error(409,()->f.service.exchange(RAW,PROOF));f.service.webDecision(identity(),RAW,"approve");
            final var result=f.service.exchange(RAW,PROOF);Assert.assertEquals(result.keySet(),Set.of("accessToken","refreshToken","tokenType","expiresIn","refreshExpiresIn","scope","clientId"));Assert.assertEquals(result.getString("clientId"),"fishpi_app_v3");
            Assert.assertEquals(rows(f.db,"SELECT userLatestLoginTime FROM u WHERE oId='1'").getFirst().getLong("userLatestLoginTime"),f.clock);
            Assert.assertEquals(f.row().getString("state"),"CONSUMED");Assert.assertEquals(f.pairs(),2);Assert.assertEquals(f.issued,1);error(410,()->f.service.exchange(RAW,PROOF));Assert.assertEquals(f.pairs(),2);Assert.assertFalse(f.row().toString().contains(PROOF));
        }System.out.println("PASS app login service 1 real owner/epoch/issuer SQL, ordered double consent and same-root single token-pair consume; metadata/token response whitelists");
        for(String mutation:List.of("UPDATE tokens SET state=2 WHERE oId='100'","UPDATE tokens SET expiresAt=1 WHERE oId='100'","UPDATE tokens SET clientId='third' WHERE oId='100'","UPDATE clients SET status=1 WHERE clientId='website_v3'","UPDATE clients SET status=1 WHERE clientId='fishpi_app_v3'","UPDATE u SET userStatus=4 WHERE oId='1'","INSERT INTO states VALUES('1',1,0)","UPDATE tokens SET scope='profile.read' WHERE oId='100'","UPDATE clients SET scope='settings.write' WHERE clientId='fishpi_app_v3'"))try(var f=new Fixture()){
            f.approved();sql(f.db,mutation);error(403,()->f.service.exchange(RAW,PROOF));Assert.assertEquals(f.pairs(),0);Assert.assertEquals(f.row().getString("state"),"WEB_APPROVED");
        }System.out.println("PASS app login service 2 current issuer expiry/revocation/client binding, owner status/epoch and native/website scope rechecked before issuance");
        for(String failure:List.of("half","response","commit","activity"))try(var f=new Fixture()){
            f.approved();if(failure.equals("commit"))f.fault="commit";else if(failure.equals("activity"))f.fault="password";else f.issueFault=failure;
            error(503,()->f.service.exchange(RAW,PROOF));f.fault="";f.issueFault="";Assert.assertEquals(f.pairs(),0);Assert.assertEquals(f.row().getString("state"),"WEB_APPROVED");Assert.assertEquals(rows(f.db,"SELECT userLatestLoginTime FROM u WHERE oId='1'").getFirst().getLong("userLatestLoginTime"),7L);
            f.service.exchange(RAW,PROOF);Assert.assertEquals(f.pairs(),2);
        }
        try(var f=new Fixture()){f.approved();f.fault="ack";error(503,()->f.service.exchange(RAW,PROOF));f.fault="";Assert.assertEquals(f.pairs(),2);Assert.assertEquals(f.row().getString("state"),"CONSUMED");error(410,()->f.service.exchange(RAW,PROOF));Assert.assertEquals(f.issued,1);}
        System.out.println("PASS app login service 3 token half-write/invalid response/strict commit rollback; lost commit ACK stays consumed without another issuance");
        try(var f=new Fixture()){f.create();f.clock=NOW+120000;error(410,()->f.service.claim(RAW,ApiV3AppLoginProtocol.challenge(PROOF)));Assert.assertEquals(f.row().getString("state"),"WAITING");}
        try(var f=new Fixture()){f.create();f.service.webDecision(identity(),RAW,"deny");error(410,()->f.service.claim(RAW,ApiV3AppLoginProtocol.challenge(PROOF)));Assert.assertEquals(f.pairs(),0);}
        try(var f=new Fixture()){f.create();f.service.claim(RAW,ApiV3AppLoginProtocol.challenge(PROOF));f.service.appDecision(RAW,PROOF,"deny");error(410,()->f.service.webDecision(identity(),RAW,"approve"));}
        System.out.println("PASS app login service 4 exact two-minute expiry and either-side denial never restore a pending grant");
        try(var f=new Fixture()){f.create();final int roots=f.roots;error(404,()->f.service.webStatus(new JSONObject().put("tokenId","102").put("user",new JSONObject().put("oId","2")),RAW));Assert.assertEquals(f.roots,roots);f.changeHint=true;error(403,()->f.service.claim(RAW,ApiV3AppLoginProtocol.challenge(PROOF)));Assert.assertEquals(f.row().getString("state"),"WAITING");}
        for(String raw:Arrays.asList(null,"login:"+RAW,"https://example.com",RAW+" ",RAW+"="))try(var f=new Fixture()){error(422,()->f.service.claim(raw,ApiV3AppLoginProtocol.challenge(PROOF)));Assert.assertEquals(f.roots,0);}
        try(var f=new Fixture()) {f.fault="begin";error(503,()->f.service.create(identity()));f.fault="";Assert.assertEquals(rows(f.db,"SELECT COUNT(*) AS n FROM qr").getFirst().getInt("n"),0);f.create();}
        System.out.println("PASS app login service 5 foreign-owner hint filtering, current immutable binding race, malformed capabilities and begin failure mapped to503 without any request row");
        try(var f=new Fixture()) {
            f.realGrant=true;f.approved();final JSONObject pair=f.service.exchange(RAW,PROOF);Assert.assertEquals(f.pairs(),2);
            final var rows=rows(f.db,"SELECT * FROM tokens WHERE clientId='fishpi_app_v3' ORDER BY tokenType");
            Assert.assertEquals(rows.get(0).getString("familyId"),rows.get(1).getString("familyId"));Assert.assertEquals(rows.get(0).getString("parentId"),"");
            Assert.assertFalse(f.row().toString().contains(pair.getString("accessToken")));Assert.assertFalse(rows.get(0).toString().contains(pair.getString("accessToken")));
            Assert.assertEquals(rows.get(0).getString("tokenHash"),ApiV3TokenUtil.hash(pair.getString("accessToken")));
            final long signedIn=f.clock;f.clock+=100;final var grants=(ApiV3TokenMgmtService)field(f.service,"grants");final JSONObject refreshed=grants.refreshForClient("fishpi_app_v3",pair.getString("refreshToken"));Assert.assertEquals(f.pairs(),4);Assert.assertEquals(rows(f.db,"SELECT userLatestLoginTime FROM u WHERE oId='1'").getFirst().getLong("userLatestLoginTime"),signedIn,"native refresh must not record another login");
            try {grants.refreshForClient("fishpi_app_v3",pair.getString("refreshToken"));Assert.fail("rotated refresh must reject");}catch(org.b3log.latke.service.ServiceException expected){}
            Assert.assertEquals(rows(f.db,"SELECT COUNT(*) AS n FROM tokens WHERE clientId='fishpi_app_v3' AND state=0").getFirst().getInt("n"),0);
            Assert.assertEquals(rows(f.db,"SELECT state FROM tokens WHERE oId='100'").getFirst().getInt("state"),0);
        }System.out.println("PASS app login service 6 real production token issue/savepoint/random hashes/family, native refresh rotation and replay family revocation preserve website issuer");
        try(var f=new Fixture()) {
            final var endpoint=new org.b3log.symphony.processor.ApiV3AppLoginProcessor();inject(endpoint,"service",f.service);f.endpoint=endpoint;final boolean[] permit={false};final int[] checks={0};
            final var anonymous=new org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware(){public void handle(org.b3log.latke.http.RequestContext c){checks[0]++;if(!permit[0]){org.b3log.symphony.processor.ApiV3Processor.error(c,302,"captcha","fixture anonymous gate");c.abort();return;}c.handle();}};
            org.b3log.symphony.processor.ApiV3AppLoginProcessor.registerRoutes(endpoint,f.auth,f.writes,anonymous);org.b3log.latke.http.Dispatcher.mapping();
            Assert.assertEquals(f.post(null,"requests","{}").attr("test.status"),401);Assert.assertEquals(f.post("wrong","requests","{}").attr("test.status"),403);Assert.assertEquals(f.roots,0);
            Assert.assertEquals(f.post("owner","requests","{\"userId\":\"2\"}").attr("test.status"),422);Assert.assertEquals(f.post("owner","requests","{\"x\":1,\"x\":2}").attr("test.status"),400);
            final var created=f.post("owner","requests","{}");Assert.assertEquals(created.attr("test.status"),200);Assert.assertEquals(created.getResponse().getHeader("Cache-Control"),"no-store");
            final String claim=new JSONObject().put("request",RAW).put("codeChallenge",ApiV3AppLoginProtocol.challenge(PROOF)).toString();
            Assert.assertEquals(f.post(null,"claim",claim).attr("test.status"),302);Assert.assertEquals(f.row().getString("state"),"WAITING");permit[0]=true;
            Assert.assertEquals(f.post(null,"claim",claim).attr("test.status"),200);
            final String nativeBody=new JSONObject().put("request",RAW).put("codeVerifier",PROOF).toString();
            Assert.assertEquals(f.post(null,"app-status",nativeBody).attr("test.status"),200);
            Assert.assertEquals(f.post(null,"app-decision",new JSONObject(nativeBody).put("decision","confirm").toString()).attr("test.status"),200);
            Assert.assertEquals(f.post("owner","status",new JSONObject().put("request",RAW).toString()).attr("test.status"),200);
            Assert.assertEquals(f.post("owner","decision",new JSONObject().put("request",RAW).put("decision","approve").toString()).attr("test.status"),200);
            Assert.assertEquals(f.post(null,"exchange",nativeBody).attr("test.status"),200);Assert.assertEquals(f.pairs(),2);Assert.assertEquals(checks[0],5);
        }System.out.println("PASS app login service 7 actual seven POST Dispatcher/JSON/auth/write paths, native anonymous-gate adapter called, proof-only exchange and no-store; Cookie/apiKey do not authorize website operations");
    }
}
