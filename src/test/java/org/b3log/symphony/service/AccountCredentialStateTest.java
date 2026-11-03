package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.*;
import org.b3log.latke.util.Crypts;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.model.*;
import org.b3log.symphony.util.*;
import org.b3log.symphony.util.Sessions;
import org.b3log.latke.http.Cookie;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.util.*;

/** Real independent epoch/revocation SQL and real AES Cookie/API and HMAC OpenID paths; no production traffic. */
public class AccountCredentialStateTest extends ApiV3ProfessionPrimaryTest {
    static void wire(Object target,String name,Object value)throws Exception{Class<?> c=target.getClass();while(c!=null){try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(name);}
    static final String PASSWORD="a".repeat(32);
    static class Credentials extends Fixture {
        final AccountCredentialStateRepository states;
        final ApiV3TokenMgmtService tokenService=new ApiV3TokenMgmtService();
        final AccountCredentialMgmtService revoke=new AccountCredentialMgmtService();
        final OpenIdRefreshTokenMgmtService openid=new OpenIdRefreshTokenMgmtService();
        final AccountPasswordResetService reset=new AccountPasswordResetService();
        final AccountVerificationService verification=new AccountVerificationService();
        final JSONObject cached;
        Credentials()throws Exception {
            sql(observer,"ALTER TABLE x_user ADD COLUMN userPhone TEXT");sql(observer,"UPDATE x_user SET userPassword=?,userPhone='13800000001' WHERE oId=?",PASSWORD,actor);
            cached=rows(observer,"SELECT * FROM x_user WHERE oId=?",actor).getFirst();
            sql(observer,"CREATE TABLE credentials(oId TEXT PRIMARY KEY,credentialEpoch BIGINT,updatedAt BIGINT)");
            sql(observer,"CREATE TABLE openid(oId TEXT PRIMARY KEY,tokenHash TEXT,userId TEXT,realm TEXT,scope TEXT,familyId TEXT,parentId TEXT,state INT,createdAt INT,updatedAt INT,lastUsedAt INT,idleExpiresAt INT,maxExpiresAt INT,credentialEpoch INT DEFAULT 0)");
            sql(observer,"CREATE TABLE tokens(oId TEXT PRIMARY KEY,userId TEXT,state INT,updatedAt INT,tokenHash TEXT,tokenType TEXT,clientId TEXT,scope TEXT,familyId TEXT,parentId TEXT,createdAt INT,lastUsedAt INT,expiresAt INT)");
            sql(observer,"CREATE TABLE codes(oId TEXT PRIMARY KEY,userId TEXT,consumedAt INT)");
            sql(observer,"CREATE TABLE consents(oId TEXT PRIMARY KEY,userId TEXT,decision INT)");
            sql(observer,"CREATE TABLE clients(clientId TEXT PRIMARY KEY,oId TEXT,name TEXT,scope TEXT,status INT)");sql(observer,"INSERT INTO clients(clientId) VALUES('a'),('b')");
            sql(observer,"INSERT INTO clients VALUES('website_v3','100','website_v3','profile.read content.read settings.write',0)");
            sql(observer,"CREATE TABLE verifycodes(oId TEXT PRIMARY KEY,userId TEXT,receiver TEXT,type INT,bizType INT,code TEXT,status INT,expired BIGINT)");
            var userRows=new UserRepository(){
                public String getName(){return "x_user";}public boolean hasTransactionBegun(){return active();}
                public JSONObject get(String id){return id.equals(actor)?new JSONObject(cached.toString()):null;}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}
                public void updateColumnsInCurrentTransaction(String id,JSONObject v)throws RepositoryException{sql(writer,"UPDATE x_user SET userPassword=? WHERE oId=?",v.getString("userPassword"),id);}
            };
            states=new AccountCredentialStateRepository(){public String getName(){return "credentials";}public boolean hasTransactionBegun(){return active();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}};wire(states,"users",userRows);
            var oldTokens=new OpenIdRefreshTokenRepository(){
                public String getName(){return "openid";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}
                public JSONObject getByTokenHash(String h)throws RepositoryException{var r=rows(observer,"SELECT * FROM openid WHERE tokenHash=?",h);return r.isEmpty()?null:r.getFirst();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}
                public String add(JSONObject r)throws RepositoryException{var cols=new ArrayList<>(r.keySet());sql(writer,"INSERT INTO openid("+String.join(",",cols)+") VALUES("+String.join(",",Collections.nCopies(cols.size(),"?"))+")",cols.stream().map(r::get).toArray());return r.getString("oId");}
                public void update(String id,JSONObject r,String...f)throws RepositoryException{sql(writer,"UPDATE openid SET state=?,updatedAt=?,lastUsedAt=? WHERE oId=?",r.getInt("state"),r.getLong("updatedAt"),r.getLong("lastUsedAt"),id);}
            };
            var v3=new ApiV3TokenRepository(){public String getName(){return "tokens";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}
                public JSONObject getByHash(String hash)throws RepositoryException{var r=rows(observer,"SELECT * FROM tokens WHERE tokenHash=?",hash);return r.isEmpty()?null:r.getFirst();}
                public String add(JSONObject row)throws RepositoryException{insert("tokens",row);return row.getString("oId");}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException{sql(writer,"UPDATE tokens SET state=?,updatedAt=? WHERE oId=?",row.getInt("state"),row.getLong("updatedAt"),id);}
            };
            var consents=new ApiV3AuthorizationRequestRepository(){public String getName(){return "consents";}public boolean hasTransactionBegun(){return active();}};
            var codes=new ApiV3AuthorizationCodeRepository(){public String getName(){return "codes";}public boolean hasTransactionBegun(){return active();}};
            var clients=new ApiV3ClientRepository(){public String getName(){return "clients";}public boolean hasTransactionBegun(){return active();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}public JSONObject getByClientId(String id)throws RepositoryException{var r=rows(observer,"SELECT * FROM clients WHERE clientId=?",id);return r.isEmpty()?null:r.getFirst();}};
            var sms=new VerifycodeRepository(){public String getName(){return "verifycodes";}public boolean hasTransactionBegun(){return active();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}public void remove(String id)throws RepositoryException{sql(writer,"DELETE FROM verifycodes WHERE oId=?",id);}};
            wire(verification,"users",userRows);wire(verification,"codes",sms);
            wire(revoke,"users",userRows);wire(revoke,"states",states);wire(revoke,"openid",oldTokens);wire(revoke,"tokens",v3);wire(revoke,"requests",consents);wire(revoke,"codes",codes);
            wire(openid,"openIdRefreshTokenRepository",oldTokens);wire(openid,"states",states);wire(openid,"userQueryService",new UserQueryService(){public JSONObject getUser(String id){return new JSONObject(cached.toString());}});
            wire(tokenService,"apiV3TokenRepository",v3);wire(tokenService,"apiV3ClientRepository",clients);wire(tokenService,"userRepository",userRows);
            wire(reset,"users",userRows);wire(reset,"verification",verification);wire(reset,"clients",clients);wire(reset,"tokens",v3);wire(reset,"requests",consents);wire(reset,"codes",codes);wire(reset,"credentials",revoke);wire(reset,"states",states);
            BeanManager.put(AccountCredentialStateRepository.class,states);BeanManager.put(UserRepository.class,userRows);BeanManager.put(UserMgmtService.class,new UserMgmtService());
            AccountCredentialVersions.endHttp();Sessions.invalidate(actor);
        }
        void insert(String table,JSONObject row)throws RepositoryException{var cols=new ArrayList<>(row.keySet());sql(writer,"INSERT INTO "+table+"("+String.join(",",cols)+") VALUES("+String.join(",",Collections.nCopies(cols.size(),"?"))+")",cols.stream().map(row::get).toArray());}
        boolean active(){return delegate!=null&&delegate.isActive();}
        List<JSONObject> query(String q,Object...a)throws RepositoryException {if(fault.equals("read"))throw new RepositoryException("private read");return rows(active()?writer:observer,q,a);}
        long epoch()throws Exception{return AccountCredentialStateRepository.epoch(states.current(actor).opt("credentialEpoch"));}
        void change()throws Exception{var tx=begin();try{revoke.revokeInCurrentTransaction(actor);tx.commit();}finally{if(tx.isActive())tx.rollback();}}
        String api(boolean version,long epoch)throws Exception {JSONObject value=new JSONObject().put("oId",actor).put("token",PASSWORD+":"+"r".repeat(16));if(version)value.put("credentialEpoch",epoch);return Crypts.encryptByAES(value.toString(),Symphonys.COOKIE_SECRET_API);}
        String cookie(boolean version,long epoch)throws Exception {JSONObject value=new JSONObject().put("oId",actor).put("token",PASSWORD+":"+"r".repeat(16)).put("rememberLogin",true);if(version)value.put("credentialEpoch",epoch);return Crypts.encryptByAES(value.toString(),Symphonys.COOKIE_SECRET);}
        JSONObject cookieUser(String value){var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/");try{var request=new Request(null,raw){public Set<Cookie> getCookies(){return Set.of(new Cookie(Sessions.COOKIE_NAME,value));}public String getRemoteAddr(){return "192.0.2.1";}};return Sessions.currentCookieUser(request);}finally{raw.release();}}
        String webApi(String cookie,String query,String body)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/getApiKeyInWeb");
            try {
                var request=new Request(null,raw){public Set<Cookie> getCookies(){return Set.of(new Cookie(Sessions.COOKIE_NAME,cookie));}public String getRemoteAddr(){return "192.0.2.1";}};
                if(query!=null)request.setParameter("apiKey",query);if(body!=null)request.setJSON(new JSONObject().put("apiKey",body));
                var response=new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK));var context=new RequestContext(request,response);
                context.attr("user",cached);new ApiProcessor().getApiKeyInWeb(context);
                return ((org.b3log.latke.http.renderer.JsonRenderer)context.getRenderer()).getJSONObject().getString("apiKey");
            }finally{raw.release();}
        }
        JSONObject grant(long expected)throws Exception{return openid.issueToken(actor,Set.of("profile.read"),"https://game.example",expected);}
        void rejected(String key){try{ApiProcessor.getUserByKey(key);Assert.fail("expected revoked");}catch(NullPointerException expected){ }}
        long count(String table)throws Exception{return scalar("SELECT COUNT(*) AS value FROM "+table);}
    }
    interface Work{void run()throws Exception;}
    static void refused(Work work)throws Exception{try{work.run();Assert.fail("expected rejection");}catch(org.b3log.latke.service.ServiceException expected){ }}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");props.setProperty("serverScheme","https");props.setProperty("serverHost","fishpi.cn");props.setProperty("serverPort","443");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Credentials()) {
            Assert.assertEquals(f.epoch(),0);Assert.assertEquals(f.count("credentials"),0);String oldCookie=f.cookie(false,0),oldApi=f.api(false,0);
            Assert.assertNotNull(f.cookieUser(oldCookie));Assert.assertNotNull(ApiProcessor.getUserByKey(oldApi));Assert.assertEquals(Sessions.get(f.actor).getString("userPassword"),PASSWORD);
            var grant=f.grant(0);String access=grant.getString("access_token");Assert.assertNotNull(OpenIdUtil.parseAccessToken(access));
            sql(f.observer,"INSERT INTO tokens(oId,userId,state,updatedAt) VALUES('1',?,0,0),('2',?,0,0),('3','2',0,0)",f.actor,f.actor);
            sql(f.observer,"INSERT INTO codes VALUES('1',?,0),('2',?,1)",f.actor,f.actor);sql(f.observer,"INSERT INTO consents VALUES('1',?,'pending'),('2',?,'approved')",f.actor,f.actor);
            f.change();Assert.assertEquals(f.epoch(),1);Assert.assertNull(f.cookieUser(oldCookie));f.rejected(oldApi);Assert.assertNull(OpenIdUtil.parseAccessToken(access));refused(()->f.openid.refresh(grant.getString("refresh_token")));refused(()->f.grant(0));
            Assert.assertEquals(f.scalar("SELECT COUNT(*) AS value FROM tokens WHERE userId='"+f.actor+"' AND state=0"),0);Assert.assertEquals(f.count("codes"),1);Assert.assertEquals(f.count("consents"),1);
            Assert.assertEquals(f.cached.getString("userPassword"),PASSWORD);Assert.assertNull(f.cookieUser(oldCookie));Assert.assertEquals(f.count("credentials"),1);
        }
        System.out.println("PASS 1 legacy epoch-zero Cookie/API/OpenID accepted without creating state, atomic first bump rejects cached Cookie/API/access/refresh/pending issuance and revokes all v3 grants");
        try(var f=new Credentials()) {
            f.change();String key=f.api(true,1);Assert.assertNotNull(ApiProcessor.getUserByKey(key));Assert.assertNotNull(f.cookieUser(f.cookie(true,1)));
            AccountCredentialVersions.remember(new AccountCredentialVersions.Proof(f.actor,1,PASSWORD));var response=new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK));String fresh=Sessions.login(response,f.actor,true);Assert.assertNotNull(fresh);Assert.assertNotEquals(fresh.length(),224);Assert.assertNotNull(f.cookieUser(fresh));
            AccountCredentialVersions.remember(new AccountCredentialVersions.Proof(f.actor,1,PASSWORD));f.change();Assert.assertNull(Sessions.login(response,f.actor,true));Assert.assertNull(AccountCredentialVersions.snapshot(f.actor));Assert.assertNull(f.cookieUser(fresh));f.rejected(key);Assert.assertNotNull(f.cookieUser(f.cookie(true,2)));
        }
        System.out.println("PASS 2 bounded new Cookie/API formats preserve authenticated epoch; concurrent second bump blocks issuance instead of upgrading old identity");
        try(var f=new Credentials()) {
            var first=f.grant(0);var second=f.openid.refresh(first.getString("refresh_token"));Assert.assertNotNull(OpenIdUtil.parseAccessToken(second.getString("access_token")));refused(()->f.openid.refresh(first.getString("refresh_token")));refused(()->f.openid.refresh(second.getString("refresh_token")));
            f.change();var active=f.grant(1);Assert.assertNotNull(OpenIdUtil.parseAccessToken(active.getString("access_token")));f.change();Assert.assertNull(OpenIdUtil.parseAccessToken(active.getString("access_token")));refused(()->f.openid.refresh(active.getString("refresh_token")));
        }
        System.out.println("PASS 3 actual old OpenID refresh rotation/replay and post-bump issuance bind epoch and cannot mint new-epoch access from old refresh");
        for(String fault:List.of("commit","read"))try(var f=new Credentials()) {f.fault=fault;try{f.change();Assert.fail();}catch(Exception expected){}f.fault="";Assert.assertEquals(f.epoch(),0);Assert.assertEquals(f.count("credentials"),0);Assert.assertNotNull(f.cookieUser(f.cookie(false,0)));}
        try(var f=new Credentials()){sql(f.observer,"INSERT INTO credentials VALUES(?,9223372036854775807,0)",f.actor);try{f.change();Assert.fail();}catch(Exception expected){}Assert.assertEquals(f.epoch(),Long.MAX_VALUE);}
        System.out.println("PASS 4 version read/strict commit failure rolls back first state creation and credential revocation; version overflow fails closed");
        try(var f=new Credentials()) {
            Assert.assertNotNull(ApiProcessor.getUserByKey(f.api(false,0)));Assert.assertNotNull(AccountCredentialVersions.snapshot(f.actor));f.rejected("invalid");Assert.assertNull(AccountCredentialVersions.snapshot(f.actor));
            AccountCredentialVersions.remember(new AccountCredentialVersions.Proof(f.actor,0,PASSWORD));Sessions.clearThreadLocalData();Assert.assertNull(AccountCredentialVersions.snapshot(f.actor));
            sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);Assert.assertNull(f.cookieUser(f.cookie(false,0)));f.rejected(f.api(false,0));
        }
        System.out.println("PASS 5 malformed credentials and disabled current SQL owner fail closed; failed auth and HTTP cleanup clear internal proof");
        try(var f=new Credentials()) {
            sql(f.observer,"INSERT INTO verifycodes VALUES('1',?,'13800000001',1,1,'123456',1,?)",f.actor,System.currentTimeMillis()+60000);
            f.reset.complete("13800000001","123456","b".repeat(32));Assert.assertEquals(f.epoch(),1);Assert.assertEquals(f.count("verifycodes"),0);var proof=AccountCredentialVersions.snapshot(f.actor);Assert.assertNotNull(proof);Assert.assertEquals(proof.epoch(),1);Assert.assertEquals(proof.password(),"b".repeat(32));
            var response=new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK));String resetCookie=Sessions.login(response,f.actor,true);Assert.assertNotNull(resetCookie);Assert.assertNotNull(f.cookieUser(resetCookie));f.change();Assert.assertNull(Sessions.login(response,f.actor,true));
        }
        System.out.println("PASS 6 real password reset code/password/epoch/revocation commit together and post-reset automatic Cookie uses this exact committed proof");
        try(var f=new Credentials()) {
            refused(()->f.tokenService.issue(f.actor));Assert.assertEquals(f.count("tokens"),0);
            AccountCredentialVersions.remember(new AccountCredentialVersions.Proof(f.actor,0,PASSWORD));var issued=f.tokenService.issue(f.actor);Assert.assertFalse(issued.has("credentialEpoch"));Assert.assertNotNull(f.tokenService.authenticate(issued.getString("accessToken")));
            AccountCredentialVersions.remember(new AccountCredentialVersions.Proof(f.actor,0,PASSWORD));f.change();refused(()->f.tokenService.issue(f.actor));Assert.assertNull(f.tokenService.authenticate(issued.getString("accessToken")));refused(()->f.tokenService.refresh(issued.getString("refreshToken")));
            AccountCredentialVersions.remember(new AccountCredentialVersions.Proof(f.actor,1,PASSWORD));var fresh=f.tokenService.issue(f.actor);Assert.assertNotNull(f.tokenService.authenticate(fresh.getString("accessToken")));
        }
        System.out.println("PASS 7 actual first-party v3 Token service rejects missing/stale authenticated proof and revoked access/refresh, accepts new proof without returning epoch");
        try(var f=new Credentials()) {
            var cookieSocket=new org.b3log.latke.http.CredentialSocket();cookieSocket.state.setAttribute("user",f.cached.toString());
            Assert.assertNotNull(Sessions.currentSocketUser(cookieSocket));
            var apiSocket=new org.b3log.latke.http.CredentialSocket();apiSocket.key=f.api(false,0);apiSocket.state.setAttribute("fromId",f.actor);
            Assert.assertNotNull(Sessions.currentSocketUser(apiSocket));Assert.assertNull(AccountCredentialVersions.saved());
            final String peer="2",hex=org.b3log.symphony.util.Strings.uniqueId(new String[]{peer,f.actor});
            var guard=new org.b3log.symphony.processor.channel.CredentialChatGuard();
            guard.SESSIONS.put(hex,Set.of(apiSocket));guard.sendMsg(f.actor,peer,"message");Assert.assertEquals(apiSocket.sent,1);
            guard.onMessage(apiSocket.message("p"));Assert.assertFalse(apiSocket.closed);
            f.change();Assert.assertNull(Sessions.currentSocketUser(cookieSocket));
            guard.sendMsg(f.actor,peer,"private-message");Assert.assertTrue(apiSocket.closed);Assert.assertEquals(apiSocket.sent,1);
            apiSocket.closed=false;guard.onMessage(apiSocket.message("p"));Assert.assertTrue(apiSocket.closed);
            cookieSocket.state.setAttribute("accountCredentialEpoch","1");Assert.assertNotNull(Sessions.currentSocketUser(cookieSocket));
            AccountCredentialVersions.beginHttp();AccountCredentialVersions.remember(AccountCredentialVersions.current(f.actor));
            var saved=AccountCredentialVersions.saved();var root=f.begin();
            Assert.assertNull(Sessions.currentSocketUser(apiSocket));Assert.assertSame(AccountCredentialVersions.saved(),saved);Assert.assertTrue(root.isActive());
            root.rollback();AccountCredentialVersions.endHttp();
            root=f.begin();Assert.assertNull(Sessions.currentSocketUser(apiSocket));Assert.assertTrue(root.isActive());root.rollback();
            Assert.assertNull(AccountCredentialVersions.saved());guard.SESSIONS.clear();
        }
        System.out.println("PASS 8 actual socket Cookie/API version checks and ChatChannel send method/authentication prefix reject revoked sessions before push or message processing, preserve HTTP proof and caller transaction, clear non-HTTP proof");
        try(var f=new Credentials()) {
            var connection=new org.b3log.latke.http.CredentialSocket();connection.key=f.api(false,0);
            org.b3log.symphony.processor.channel.CredentialSocketCleanup.sendText(connection,"hello");Assert.assertEquals(connection.sent,1);
            var cleanup=new org.b3log.symphony.processor.channel.CredentialSocketCleanup();cleanup.CONNECTION_OWNERS.put(connection,f.actor);cleanup.SESSIONS.put(f.actor,new HashSet<>(Set.of(connection)));
            cleanup.userOnline.put(f.actor,new JSONObject().put("timeStamp",System.currentTimeMillis()-60000).put("onlineMinute",5));
            var shop=new org.b3log.symphony.processor.channel.CredentialSocketCleanup.Shop();shop.CONNECTION_OWNERS.put(connection,f.actor);shop.SESSIONS.put(f.actor,new HashSet<>(Set.of(connection)));
            f.change();org.b3log.symphony.processor.channel.CredentialSocketCleanup.sendText(connection,"private");Assert.assertEquals(connection.sent,1);Assert.assertTrue(connection.closed);
            var management=BeanManager.getInstance().getReference(UserMgmtService.class);cleanup.closeUser(connection);cleanup.closeUser(connection);shop.closeShop(connection);shop.closeShop(connection);
            Assert.assertTrue(cleanup.SESSIONS.get(f.actor).isEmpty());Assert.assertTrue(cleanup.CONNECTION_OWNERS.isEmpty());Assert.assertTrue(cleanup.userOnline.isEmpty());Assert.assertEquals(management.settled,1);Assert.assertEquals(management.onlineUpdates,1);Assert.assertTrue(shop.SESSIONS.get(f.actor).isEmpty());Assert.assertTrue(shop.CONNECTION_OWNERS.isEmpty());
        }
        System.out.println("PASS 9 actual chatroom send rechecks credential; revoked socket cleanup uses recorded ownership without reauthentication and settles online time exactly once");
        try(var f=new Credentials()) {
            final String cookie=f.cookie(false,0),api=f.api(false,0);
            for(String[] options:new String[][]{{null,null},{"invalid",null},{api,null},{null,api}}){
                Assert.assertNotNull(f.cookieUser(cookie));String issued=f.webApi(cookie,options[0],options[1]);Assert.assertEquals(ApiProcessor.getUserByKey(issued).getString("oId"),f.actor);
                JSONObject value=new JSONObject(Crypts.decryptByAES(issued,Symphonys.COOKIE_SECRET_API));Assert.assertEquals(value.getLong("credentialEpoch"),0);
            }
            f.change();Assert.assertNotNull(f.cookieUser(f.cookie(true,1)));String issued=f.webApi(f.cookie(true,1),null,null);Assert.assertEquals(ApiProcessor.getUserByKey(issued).getString("oId"),f.actor);
        }
        System.out.println("PASS 10 actual Cookie-to-apiKey handler preserves proof with absent query, reauthenticates real Cookie after bad key, accepts query/body API identities and issues current bounded credentials");
        try(var f=new Credentials()) {
            AccountCredentialVersions.remember(new AccountCredentialVersions.Proof(f.actor,0,PASSWORD));var issued=f.tokenService.issue(f.actor);Assert.assertNotNull(f.tokenService.authenticate(issued.getString("accessToken")));
            Assert.assertEquals(f.cached.getInt("userStatus"),0);sql(f.observer,"UPDATE x_user SET userStatus=4 WHERE oId=?",f.actor);
            Assert.assertEquals(f.cached.getInt("userStatus"),0,"fixture deliberately retains a valid stale cached user");Assert.assertNull(f.tokenService.authenticate(issued.getString("accessToken")));refused(()->f.tokenService.refresh(issued.getString("refreshToken")));
        }
        System.out.println("PASS 11 stale valid UserRepository.get cache cannot authorize a currently deactivated account or website refresh");
        System.out.println("Credential state isolated tests passed: 11 groups");
    }
}
