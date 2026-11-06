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
import org.b3log.latke.ioc.BeanManager;
import org.b3log.symphony.util.AccountCredentialVersions;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Real token service and new bound SQL/current reads; base row persistence and pool are SQLite adapters. */
public class ApiV3TokenLifecycleTest {
    static class Tokens extends ApiV3TokenMgmtService {long clock=100_000;@Override protected long now(){return clock;}}
    static class Fixture extends ApiV3ChatEventRepositoryTest.Fixture {
        final Tokens service=new Tokens();final List<String> locks=new ArrayList<>();boolean revokeBeforeActivity;
        final ApiV3TokenRepository tokens=new ApiV3TokenRepository(){
            @Override public String getName(){return "tokens";}
            @Override public boolean hasTransactionBegun(){return active();}
            @Override public Transaction beginTransaction(){try{begin();return ApiV3Transactions.strict(tx);}catch(Exception e){throw new IllegalStateException(e);}}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{if(sql.endsWith(" FOR UPDATE"))locks.add("token");return query(active()?writer:reader,sql,args);}
            @Override public JSONObject getByHash(String hash)throws RepositoryException{var rows=select("SELECT * FROM tokens WHERE tokenHash=?",hash);return rows.isEmpty()?null:rows.getFirst();}
            @Override public String add(JSONObject row)throws RepositoryException{insert("tokens",row);if(faults.contains("add."+row.getString("tokenType")))throw new RepositoryException("lost token insert acknowledgement");return row.getString("oId");}
            @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException{exec(writer,"UPDATE tokens SET state=?,updatedAt=?,lastUsedAt=? WHERE oId=?",row.getInt("state"),row.getLong("updatedAt"),row.getLong("lastUsedAt"),id);if(faults.contains("rotate.after"))throw new RepositoryException("lost rotation acknowledgement");}
            @Override public int revokeFamilyInCurrentTransaction(String client,String user,String family,long now)throws RepositoryException{int changed=super.revokeFamilyInCurrentTransaction(client,user,family,now);if(faults.contains("family.after"))throw new RepositoryException("lost family revocation acknowledgement");return changed;}
        };
        final ApiV3ClientRepository clients=new ApiV3ClientRepository(){
            @Override public String getName(){return "clients";}
            @Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{if(sql.endsWith(" FOR UPDATE"))locks.add("client");return query(active()?writer:reader,sql,args);}
            @Override public JSONObject getByClientId(String id)throws RepositoryException{var rows=select("SELECT * FROM clients WHERE clientId=?",id);return rows.isEmpty()?null:rows.getFirst();}
            @Override public String add(JSONObject row)throws RepositoryException{insert("clients",row);return row.getString("oId");}
        };
        Fixture()throws Exception{
            exec(writer,"ALTER TABLE user ADD COLUMN userStatus INTEGER");exec(writer,"UPDATE user SET userStatus=0");exec(writer,"INSERT INTO user VALUES('2',0)");
            exec(writer,"ALTER TABLE user ADD COLUMN userPassword TEXT DEFAULT 'fixture-password'");
            exec(writer,"ALTER TABLE user ADD COLUMN userLatestLoginTime BIGINT DEFAULT 7");
            exec(writer,"CREATE TABLE credential_states(oId TEXT PRIMARY KEY,credentialEpoch BIGINT,updatedAt BIGINT)");
            var credentialUsers=new UserRepository(){public boolean hasTransactionBegun(){return active();}public String getName(){return "user";}public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(active()?writer:reader,sql,args);}};
            var credentialStates=new AccountCredentialStateRepository(){public boolean hasTransactionBegun(){return active();}public String getName(){return "credential_states";}public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(active()?writer:reader,sql,args);}};
            inject(credentialStates,AccountCredentialStateRepository.class,"users",credentialUsers);BeanManager.put(AccountCredentialStateRepository.class,credentialStates);
            AccountCredentialVersions.remember(AccountCredentialVersions.current("1"));
            exec(writer,"CREATE TABLE clients(oId TEXT PRIMARY KEY,clientId TEXT UNIQUE,name TEXT,scope TEXT,status INTEGER)");
            exec(writer,"CREATE TABLE tokens(oId TEXT PRIMARY KEY,tokenHash TEXT UNIQUE,tokenType TEXT,userId TEXT,clientId TEXT,scope TEXT,familyId TEXT,parentId TEXT,state INTEGER,createdAt BIGINT,updatedAt BIGINT,lastUsedAt BIGINT,expiresAt BIGINT)");
            inject(service,ApiV3TokenMgmtService.class,"apiV3TokenRepository",tokens);inject(service,ApiV3TokenMgmtService.class,"apiV3ClientRepository",clients);
            inject(service,ApiV3TokenMgmtService.class,"userRepository",new UserRepository(){@Override public String getName(){return "user";}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{
                locks.add("user");
                if(revokeBeforeActivity){revokeBeforeActivity=false;exec(writer,"UPDATE tokens SET state=2 WHERE tokenType='access'");}
                return query(active()?writer:reader,sql,args);
            }});
        }
        void insert(String table,JSONObject row)throws RepositoryException{
            var keys=new TreeSet<>(row.keySet());exec(writer,"INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(row::get).toArray());
        }
        void client(String id,String scope)throws RepositoryException{insert("clients",new JSONObject().put("oId",id).put("clientId",id).put("name","Application").put("scope",scope).put("status",0));}
        long loginTime()throws RepositoryException{return query(reader,"SELECT userLatestLoginTime FROM user WHERE oId='1'").getFirst().getLong("userLatestLoginTime");}
        String snapshot()throws RepositoryException{return query(reader,"SELECT * FROM clients ORDER BY oId").toString()+query(reader,"SELECT * FROM tokens ORDER BY oId")+query(reader,"SELECT * FROM user ORDER BY oId");}
        JSONObject token(String raw)throws Exception{return tokens.getByHash(ApiV3TokenUtil.hash(raw));}
        JSONObject grant(String client,String scope)throws Exception{begin();try{var result=service.issueForClientInCurrentTransaction("1",client,scope);commit();return result;}finally{if(active())rollback();}}
        Tokens otherInstance()throws Exception{
            final var other=new Tokens();other.clock=service.clock;
            for(String name:List.of("apiV3TokenRepository","apiV3ClientRepository","userRepository")){
                var field=ApiV3TokenMgmtService.class.getDeclaredField(name);field.setAccessible(true);inject(other,ApiV3TokenMgmtService.class,name,field.get(service));
            }
            return other;
        }
    }
    interface Work{void run()throws Exception;}
    static void denied(Work work)throws Exception{try{work.run();Assert.fail();}catch(ServiceException expected){}}
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            var first=f.service.issue("1");Assert.assertEquals(first.keySet(),Set.of("accessToken","refreshToken","expiresIn","refreshExpiresIn","scope","tokenType"));Assert.assertEquals(first.getLong("expiresIn"),900);Assert.assertEquals(first.getLong("refreshExpiresIn"),2592000);
            Assert.assertEquals(f.loginTime(),f.service.clock,"password token pair did not atomically record login time");
            var access=f.token(first.getString("accessToken"));Assert.assertEquals(access.getString("clientId"),"website_v3");Assert.assertFalse(f.snapshot().contains(first.getString("accessToken")));Assert.assertFalse(f.snapshot().contains(first.getString("refreshToken")));
            f.service.clock+=100;f.locks.clear();var next=f.otherInstance().refresh(first.getString("refreshToken"));Assert.assertEquals(f.locks,List.of("client","token","user"));Assert.assertEquals(f.token(next.getString("accessToken")).getString("familyId"),access.getString("familyId"));Assert.assertEquals(f.loginTime(),100_000L,"refresh must not count as a new login");Assert.assertEquals(f.token(first.getString("refreshToken")).getInt("state"),1);
            Assert.assertNotNull(f.service.authenticate(next.getString("accessToken")));denied(()->f.service.refresh(first.getString("refreshToken")));Assert.assertNull(f.service.authenticate(next.getString("accessToken")));Assert.assertNull(f.service.authenticate(first.getString("accessToken")));
        }
        System.out.println("PASS actual website issue/refresh DTO and TTLs stay stable, secrets persist only as hashes, client-before-token current reads rotate one family and replay revokes every active successor");
        try(var f=new Fixture()){
            f.client("external","content.read profile.read");String before=f.snapshot();f.begin();var result=f.service.issueForClientInCurrentTransaction("1","external","profile.read content.read profile.read");Assert.assertEquals(f.snapshot(),before);Assert.assertEquals(f.locks,List.of("client","user"));f.commit();Assert.assertEquals(result.getString("scope"),"content.read profile.read");Assert.assertEquals(f.token(result.getString("accessToken")).getString("clientId"),"external");
            String original=f.snapshot();denied(()->f.service.refresh(result.getString("refreshToken")));Assert.assertEquals(f.snapshot(),original);f.service.revokeRefresh(result.getString("refreshToken"));Assert.assertEquals(f.snapshot(),original);
            var next=f.service.refreshForClient("external",result.getString("refreshToken"));Assert.assertEquals(f.token(next.getString("refreshToken")).getString("clientId"),"external");f.service.revokeForClient("external",next.getString("refreshToken"));Assert.assertNull(f.service.authenticate(next.getString("accessToken")));
        }
        try(var f=new Fixture()){
            f.client("external","content.read");f.grant("external","content.read");Assert.assertEquals(f.loginTime(),7L,"third-party OAuth grant must not count as a login");
        }
        try(var f=new Fixture()){
            String before=f.snapshot();exec(f.writer,"CREATE TRIGGER reject_login BEFORE UPDATE OF userLatestLoginTime ON user BEGIN SELECT RAISE(ABORT,'login timestamp storage failure'); END");
            denied(()->f.service.issue("1"));Assert.assertEquals(f.snapshot(),before,"login-time failure must roll back both token rows and client creation");
        }
        System.out.println("PASS successful password login records time in token root; refresh/OAuth do not, and timestamp storage failure rolls back the complete pair");
        System.out.println("PASS caller-owned client grant commits only with its owner, normalizes permitted scopes and binds all rotated tokens to that client; website cookie paths cannot refresh or revoke a foreign client family");
        for(String fault:List.of("add.access","add.refresh","commit"))try(var f=new Fixture()){
            String before=f.snapshot();f.faults.add(fault);try{f.service.issue("1");Assert.fail(fault);}catch(ServiceException expected){}Assert.assertEquals(f.snapshot(),before,fault);Assert.assertFalse(f.active());
        }
        for(String fault:List.of("rotate.after","add.access","add.refresh","commit"))try(var f=new Fixture()){
            var first=f.service.issue("1");String before=f.snapshot();f.faults.add(fault);try{f.service.refresh(first.getString("refreshToken"));Assert.fail(fault);}catch(ServiceException expected){}Assert.assertEquals(f.snapshot(),before,fault);Assert.assertFalse(f.active());
        }
        for(String fault:List.of("add.access","add.refresh","release.after"))try(var f=new Fixture()){
            f.client("external","content.read");String before=f.snapshot();f.begin();f.faults.add(fault);try{f.service.issueForClientInCurrentTransaction("1","external","content.read");Assert.fail(fault);}catch(Exception expected){}if(f.active())f.commit();Assert.assertEquals(f.snapshot(),before,fault);
        }
        System.out.println("PASS issue/rotation insert, row-update and strict commit failures restore the complete state; a failed grant also removes a partially inserted pair when its owner catches and commits");
        try(var f=new Fixture()){
            var first=f.service.issue("1");var source=f.token(first.getString("accessToken"));String family=source.getString("familyId");
            for(int i=0;i<1005;i++)f.insert("tokens",new JSONObject(source.toString()).put("oId","bulk"+i).put("tokenHash","bulk-hash"+i));
            f.insert("tokens",new JSONObject(source.toString()).put("oId","other-client").put("tokenHash","other-client").put("clientId","other"));f.insert("tokens",new JSONObject(source.toString()).put("oId","other-user").put("tokenHash","other-user").put("userId","2"));
            f.service.revokeRefresh(first.getString("refreshToken"));Assert.assertEquals(query(f.reader,"SELECT * FROM tokens WHERE familyId=? AND userId='1' AND clientId='website_v3' AND state=0",family).size(),0);Assert.assertEquals(query(f.reader,"SELECT * FROM tokens WHERE state=0").size(),2);
        }
        try(var f=new Fixture()){var first=f.service.issue("1");String before=f.snapshot();f.faults.add("family.after");denied(()->f.service.revokeRefresh(first.getString("refreshToken")));Assert.assertEquals(f.snapshot(),before);}
        System.out.println("PASS family revocation executes the actual bound SQL without a 1000-row cap, preserves colliding family IDs belonging to other clients/users and rolls back failed revocations");
        try(var f=new Fixture()){
            var first=f.service.issue("1");f.revokeBeforeActivity=true;f.service.authenticate(first.getString("accessToken"));var stored=f.token(first.getString("accessToken"));Assert.assertEquals(stored.getInt("state"),2);Assert.assertEquals(stored.getLong("lastUsedAt"),0);Assert.assertNull(f.service.authenticate(first.getString("accessToken")));
        }
        try(var f=new Fixture()){
            var first=f.service.issue("1");f.service.clock+=100;Assert.assertNotNull(f.service.authenticate(first.getString("accessToken")));Assert.assertEquals(f.token(first.getString("accessToken")).getLong("lastUsedAt"),f.service.clock);
            String before=f.snapshot();f.service.revokeRefresh(first.getString("accessToken"));Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS access activity uses conditional column-only SQL, records valid use and cannot resurrect a revocation after an old authentication read; an access token is not accepted as a refresh revocation credential");
        for(int status:List.of(1,2,3,4))try(var f=new Fixture()){
            var first=f.service.issue("1");f.locks.clear();Assert.assertNotNull(f.service.authenticate(first.getString("accessToken")));Assert.assertTrue(f.locks.contains("user"),"authentication must query the current user store");
            exec(f.writer,"UPDATE user SET userStatus=? WHERE oId='1'",status);Assert.assertNull(f.service.authenticate(first.getString("accessToken")));
            denied(()->f.service.refresh(first.getString("refreshToken")));Assert.assertEquals(f.token(first.getString("refreshToken")).getInt("state"),2);
        }
        try(var f=new Fixture()){
            var first=f.service.issue("1");exec(f.writer,"DELETE FROM user WHERE oId='1'");Assert.assertNull(f.service.authenticate(first.getString("accessToken")));denied(()->f.service.refresh(first.getString("refreshToken")));
        }
        try(var f=new Fixture()){
            exec(f.writer,"ALTER TABLE user ADD COLUMN userNickname TEXT DEFAULT 'before'");var first=f.service.issue("1");Assert.assertEquals(f.service.authenticate(first.getString("accessToken")).getJSONObject("user").getString("userNickname"),"before");
            exec(f.writer,"UPDATE user SET userNickname='after' WHERE oId='1'");Assert.assertEquals(f.service.authenticate(first.getString("accessToken")).getJSONObject("user").getString("userNickname"),"after");
        }
        System.out.println("PASS current SQL user status rejects all four inactive states and deletion for Bearer/website refresh, and current profile updates do not reuse cached snapshots");
        for(String scope:List.of("settings.write","content.read\tprofile.read","content.read \\","content.read \""))try(var f=new Fixture()){
            f.client("external","content.read profile.read");String before=f.snapshot();denied(()->f.grant("external",scope));Assert.assertEquals(f.snapshot(),before);
        }
        try(var f=new Fixture()){
            f.client("external","content.read");exec(f.writer,"UPDATE user SET userStatus=1 WHERE oId='1'");String before=f.snapshot();denied(()->f.grant("external","content.read"));Assert.assertEquals(f.snapshot(),before);
            try{f.service.issueForClientInCurrentTransaction("1","external","content.read");Assert.fail();}catch(IllegalStateException expected){}
            f.begin();try{f.service.issue("1");Assert.fail();}catch(IllegalStateException expected){}f.rollback();
        }
        System.out.println("PASS unapproved/invalid scopes, current disabled user and missing or nested root ownership reject grants without leaking an access half or committing another caller's transaction");
        try(var f=new Fixture()){
            f.client("external","content.read");var other=f.grant("external","content.read");var website=f.service.issue("1");exec(f.writer,"UPDATE clients SET status=1 WHERE clientId='external'");
            denied(()->f.service.refreshForClient("external",other.getString("refreshToken")));Assert.assertEquals(query(f.reader,"SELECT * FROM tokens WHERE clientId='external' AND state=0").size(),0);Assert.assertNotNull(f.service.authenticate(website.getString("accessToken")));denied(()->f.grant("external","content.read"));
        }
        try(var f=new Fixture()){
            var first=f.service.issue("1");String before=f.snapshot();denied(()->f.service.refresh(first.getString("accessToken")));Assert.assertEquals(f.snapshot(),before);
            f.service.clock=f.token(first.getString("refreshToken")).getLong("expiresAt");denied(()->f.service.refresh(first.getString("refreshToken")));Assert.assertEquals(f.token(first.getString("refreshToken")).getInt("state"),3);Assert.assertEquals(query(f.reader,"SELECT * FROM tokens").size(),2);Assert.assertNull(f.service.authenticate(first.getString("accessToken")));
        }
        System.out.println("PASS disabled clients revoke only their own family, access credentials cannot rotate and exact-boundary refresh expiry is recorded without issuing new credentials");
        try(var f=new Fixture()){
            f.client("external","content.read profile.read");final var first=f.grant("external","content.read profile.read");exec(f.writer,"UPDATE clients SET scope='profile.read settings.write' WHERE clientId='external'");
            final var next=f.service.refreshForClient("external",first.getString("refreshToken"));Assert.assertEquals(next.getString("scope"),"profile.read");Assert.assertEquals(f.token(next.getString("accessToken")).getString("scope"),"profile.read");
            exec(f.writer,"UPDATE user SET userStatus=1 WHERE oId='1'");denied(()->f.service.refreshForClient("external",next.getString("refreshToken")));
        }
        System.out.println("PASS third-party refresh intersects the original grant with current client policy without gaining newly allowed scopes and rechecks current user status from the database");
        final String readScope=ApiV3TokenMgmtService.DEFAULT_SCOPES.replace(" messages.write","");
        final String oldScope=readScope.replace(" messages.read","");
        final String migration=java.nio.file.Files.readString(java.nio.file.Path.of("sql/20261019_api_v3_private_messages.sql"));
        final String update=migration.substring(migration.indexOf("UPDATE `"),migration.indexOf("COMMIT;")).strip()
                .replace("`symphony_api_v3_client`","clients").replace("CONCAT(`scope`, ' messages.read')","(`scope` || ' messages.read')");
        try(var f=new Fixture()){
            f.client("website_v3",oldScope);f.client("external",oldScope);
            final var oldWebsite=f.service.issue("1");final var oldExternal=f.grant("external","content.read");
            final String tokensBefore=query(f.reader,"SELECT * FROM tokens ORDER BY oId").toString();
            f.begin();exec(f.writer,update);f.commit();
            Assert.assertEquals(query(f.reader,"SELECT * FROM tokens ORDER BY oId").toString(),tokensBefore);
            Assert.assertEquals(query(f.reader,"SELECT scope FROM clients WHERE clientId='website_v3'").getFirst().getString("scope"),readScope);
            Assert.assertEquals(query(f.reader,"SELECT scope FROM clients WHERE clientId='external'").getFirst().getString("scope"),oldScope);
            String once=f.snapshot();f.begin();exec(f.writer,update);f.commit();Assert.assertEquals(f.snapshot(),once);
            Assert.assertFalse(ApiV3TokenMgmtService.hasScope(f.service.refresh(oldWebsite.getString("refreshToken")).getString("scope"),"messages.read"));
            exec(f.writer,"UPDATE clients SET scope=scope || ' messages.read' WHERE clientId='external'");
            Assert.assertFalse(ApiV3TokenMgmtService.hasScope(f.service.refreshForClient("external",oldExternal.getString("refreshToken")).getString("scope"),"messages.read"));
            Assert.assertTrue(ApiV3TokenMgmtService.hasScope(f.service.issue("1").getString("scope"),"messages.read"));
            Assert.assertTrue(ApiV3TokenMgmtService.hasScope(f.grant("external","messages.read").getString("scope"),"messages.read"));
        }
        try(var f=new Fixture()){
            f.client("website_v3","content.read");String original=f.snapshot();f.begin();exec(f.writer,update);f.commit();Assert.assertEquals(f.snapshot(),original);
        }
        System.out.println("PASS actual private scope migration is idempotent and only upgrades the exact old default website policy, leaves custom/third-party policies and token rows untouched; website and external refresh never gain private read access, only a fresh permitted grant does");
        final String writeMigration=java.nio.file.Files.readString(java.nio.file.Path.of("sql/20261020_api_v3_private_message_writes.sql"));
        final String writeUpdate=writeMigration.substring(writeMigration.indexOf("UPDATE `"),writeMigration.indexOf("COMMIT;")).strip()
                .replace("`symphony_api_v3_client`","clients").replace("CONCAT(`scope`, ' messages.write')","(`scope` || ' messages.write')");
        try(var f=new Fixture()){
            f.client("website_v3",readScope);f.client("external",readScope);final var website=f.service.issue("1");final var external=f.grant("external","messages.read");
            String beforeTokens=query(f.reader,"SELECT * FROM tokens ORDER BY oId").toString();f.begin();exec(f.writer,writeUpdate);f.commit();
            Assert.assertEquals(query(f.reader,"SELECT * FROM tokens ORDER BY oId").toString(),beforeTokens);
            Assert.assertEquals(query(f.reader,"SELECT scope FROM clients WHERE clientId='website_v3'").getFirst().getString("scope"),ApiV3TokenMgmtService.DEFAULT_SCOPES);
            Assert.assertEquals(query(f.reader,"SELECT scope FROM clients WHERE clientId='external'").getFirst().getString("scope"),readScope);
            String once=f.snapshot();f.begin();exec(f.writer,writeUpdate);f.commit();Assert.assertEquals(f.snapshot(),once);
            Assert.assertFalse(ApiV3TokenMgmtService.hasScope(f.service.refresh(website.getString("refreshToken")).getString("scope"),"messages.write"));
            exec(f.writer,"UPDATE clients SET scope=scope || ' messages.write' WHERE clientId='external'");
            Assert.assertFalse(ApiV3TokenMgmtService.hasScope(f.service.refreshForClient("external",external.getString("refreshToken")).getString("scope"),"messages.write"));
            Assert.assertTrue(ApiV3TokenMgmtService.hasScope(f.service.issue("1").getString("scope"),"messages.write"));
            Assert.assertTrue(ApiV3TokenMgmtService.hasScope(f.grant("external","messages.write").getString("scope"),"messages.write"));
        }
        try(var f=new Fixture()){f.client("website_v3","content.read");String before=f.snapshot();f.begin();exec(f.writer,writeUpdate);f.commit();Assert.assertEquals(f.snapshot(),before);}
        System.out.println("PASS private-write migration only expands the exact default website policy once, preserves custom policies/tokens and does not add write permission on website or external refresh; a new permitted grant is required");


    }
}
