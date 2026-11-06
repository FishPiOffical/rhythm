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
import org.json.JSONObject;
import org.testng.Assert;
import java.sql.*;
import java.util.*;

/** Actual grant core, socket repository SQL and scope predicate with SQLite persistence adapters. */
public class ApiV3SocketTicketServiceTest {
    static Fixture current;
    public static class Users extends AbstractRepository {
        public Users(){super("user");}@Override public String getName(){return "symphony_user";}
    }
    public static class Articles extends AbstractRepository {
        public Articles(){super("article");}@Override public String getName(){return "symphony_article";}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{
            if(current.fail.equals("target.select"))throw new RepositoryException("article permission source unavailable");
            return query(sql,args);
        }
    }
    static class Tokens extends ApiV3TokenRepository {
        @Override public String getName(){return "symphony_api_v3_token";}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(sql,args);}
    }
    static class Clients extends ApiV3ClientRepository {@Override public String getName(){return "symphony_api_v3_client";}}
    static class Tickets extends ApiV3SocketTicketRepository {
        @Override public String getName(){return "symphony_api_v3_socket_ticket";}
        @Override public boolean hasTransactionBegun(){return current.active();}
        @Override public Transaction beginTransaction(){Assert.assertFalse(current.active());try{current.db.setAutoCommit(false);}catch(Exception e){throw new RuntimeException(e);}return new Transaction(){boolean open=true;public boolean isActive(){return open;}public void commit(){if(current.fail.equals("commit"))throw new IllegalStateException("commit failure");finish(true);}public void rollback(){finish(false);}void finish(boolean commit){try{if(commit)current.db.commit();else current.db.rollback();current.db.setAutoCommit(true);open=false;}catch(Exception e){throw new RuntimeException(e);}}};}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{Assert.assertTrue(current.active());return query(sql,args);}
        @Override public String add(JSONObject row)throws RepositoryException {
            Assert.assertTrue(current.active());if(current.fail.equals("add"))throw new RepositoryException("insert failure");
            write("INSERT INTO symphony_api_v3_socket_ticket VALUES(?,?,?,?,?,?,?,?)",row.getString("oId"),row.getString("userId"),row.getString("parentTokenId"),row.getLong("createdAt"),row.getLong("expiresAt"),row.getLong("consumedAt"),row.getString("channel"),row.getString("targetId"));return row.getString("oId");
        }
        @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException {
            Assert.assertTrue(current.active());Assert.assertEquals(fields,new String[]{"consumedAt"});Assert.assertEquals(row.keySet(),Set.of("consumedAt"));
            if(current.fail.equals("update"))throw new RepositoryException("update failure");write("UPDATE symphony_api_v3_socket_ticket SET consumedAt=? WHERE oId=?",row.getLong("consumedAt"),id);
        }
        @Override public void remove(String id)throws RepositoryException{Assert.assertTrue(current.active());if(current.fail.equals("remove"))throw new RepositoryException("delete failure");write("DELETE FROM symphony_api_v3_socket_ticket WHERE oId=?",id);}
    }
    static class Core extends ApiV3SocketTicketService {@Override protected long now(){return current.clock;}}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");final Core core=new Core();final Tickets tickets=new Tickets();long clock=5_000;String fail="";
        Fixture()throws Exception {
            current=this;write("CREATE TABLE symphony_user(oId TEXT PRIMARY KEY,userStatus INT,userName TEXT,userRole TEXT)");write("CREATE TABLE symphony_api_v3_client(clientId TEXT PRIMARY KEY,status INT)");
            write("CREATE TABLE symphony_api_v3_token(oId TEXT PRIMARY KEY,userId TEXT,clientId TEXT,tokenType TEXT,state INT,scope TEXT,expiresAt BIGINT)");
            write("CREATE TABLE symphony_api_v3_socket_ticket(oId TEXT PRIMARY KEY,userId TEXT,parentTokenId TEXT,createdAt BIGINT,expiresAt BIGINT,consumedAt BIGINT,channel TEXT DEFAULT 'notifications',targetId TEXT DEFAULT '')");
            write("INSERT INTO symphony_user VALUES('123',0,'Reader','defaultRole'),('321',0,'Author','defaultRole')");
            write("CREATE TABLE symphony_article(oId TEXT PRIMARY KEY,articleAuthorId TEXT,articleType INT,articleStatus INT,articleAnonymousView INT,articleContent TEXT)");
            write("INSERT INTO symphony_article VALUES('789','321',0,0,0,'PRIVATE-article'),('987','321',0,0,0,'OTHER-private')");
            var targets=new ApiV3RealtimeTargetService();
            for(var entry:Map.<String,Object>of("articles",new ArticleRepository(),"users",new UserRepository()).entrySet()){var field=ApiV3RealtimeTargetService.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(targets,entry.getValue());}
            write("INSERT INTO symphony_api_v3_client VALUES('website_v3',0)");
            write("INSERT INTO symphony_api_v3_token VALUES('456','123','website_v3','access',0,'profile.read notifications.read',125000),('654','321','website_v3','access',0,'notifications.read',125000)");
            for(var entry:Map.<String,Object>of("targets",targets,"tickets",tickets,"tokens",new Tokens(),"clients",new Clients(),"users",new UserRepository()).entrySet()){var field=ApiV3SocketTicketService.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(core,entry.getValue());}
        }
        boolean active(){try{return !db.getAutoCommit();}catch(Exception e){throw new RuntimeException(e);}}
        String snapshot()throws Exception{return query("SELECT * FROM symphony_api_v3_socket_ticket ORDER BY oId").toString();}
        @Override public void close()throws Exception{db.close();current=null;}
    }
    public static List<JSONObject> query(String sql,Object...args)throws RepositoryException {
        if(current.fail.equals("select"))throw new RepositoryException("read failure");
        try(var s=current.db.prepareStatement(sql.replace(" FOR UPDATE",""))){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));out.add(row);}return out;}}catch(Exception e){throw new RepositoryException(e);}
    }
    public static void write(String sql,Object...args)throws RepositoryException {try(var s=current.db.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}catch(Exception e){throw new RepositoryException(e);}}
    interface Checked{void run()throws Exception;}
    static void unauthorized(Checked work)throws Exception{boolean failed=false;try{work.run();}catch(ApiV3Exception e){failed=true;Assert.assertEquals(e.status,401);}Assert.assertTrue(failed);}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");for(String key:List.of("latkeProps","localProps")){var field=Latkes.class.getDeclaredField(key);field.setAccessible(true);field.set(null,props);}
        try(var f=new Fixture()) {
            var issued=f.core.issue("123","456");Assert.assertEquals(issued.expiresIn(),60L);Assert.assertTrue(ApiV3TokenUtil.isValidFormat(issued.ticket()));
            var row=query("SELECT * FROM symphony_api_v3_socket_ticket").getFirst();Assert.assertEquals(row.keySet(),Set.of("oId","userId","parentTokenId","createdAt","expiresAt","consumedAt","channel","targetId"));Assert.assertEquals(row.getString("oId"),ApiV3TokenUtil.hash(issued.ticket()));Assert.assertFalse(row.toString().contains(issued.ticket()));
            var grant=f.core.consume(issued.ticket());Assert.assertEquals(grant.userId(),"123");Assert.assertEquals(grant.parentTokenId(),"456");Assert.assertEquals(grant.expiresAt(),125000L);Assert.assertTrue(f.core.authorized(grant));unauthorized(()->f.core.consume(issued.ticket()));
            var other=f.core.consume(f.core.issue("321","654").ticket());Assert.assertEquals(other.userId(),"321");
            var second=f.core.issue("123","456");Assert.assertNotEquals(second.ticket(),issued.ticket());Assert.assertTrue(f.core.authorized(grant));
        }
        System.out.println("PASS actual SQL/core issues hash-only grants, atomically consumes once, isolates actors and ties connection lifetime to its original access token");
        for(String update:List.of("UPDATE symphony_api_v3_token SET state=2 WHERE oId='456'","UPDATE symphony_api_v3_token SET expiresAt=5000 WHERE oId='456'","UPDATE symphony_api_v3_token SET tokenType='refresh' WHERE oId='456'","UPDATE symphony_api_v3_token SET scope='notifications.write' WHERE oId='456'","UPDATE symphony_user SET userStatus=4 WHERE oId='123'","UPDATE symphony_api_v3_client SET status=1"))try(var f=new Fixture()) {
            var one=f.core.issue("123","456");var grant=f.core.consume(one.ticket());var pending=f.core.issue("123","456");write(update);Assert.assertFalse(f.core.authorized(grant));unauthorized(()->f.core.issue("123","456"));unauthorized(()->f.core.consume(pending.ticket()));Assert.assertEquals(query("SELECT consumedAt FROM symphony_api_v3_socket_ticket WHERE oId=?",ApiV3TokenUtil.hash(pending.ticket())).getFirst().getLong("consumedAt"),0L);
        }
        System.out.println("PASS parent revocation/expiry/type/scope and current user/client status reject issuance, pending consumption and existing connection authorization");
        try(var f=new Fixture()) {
            unauthorized(()->f.core.issue("123","654"));unauthorized(()->f.core.issue("999","456"));unauthorized(()->f.core.consume("bad"));unauthorized(()->f.core.consume(ApiV3TokenUtil.generate()));
            write("UPDATE symphony_api_v3_token SET expiresAt=7000 WHERE oId='456'");var issued=f.core.issue("123","456");Assert.assertEquals(issued.expiresIn(),2L);f.clock=7000;unauthorized(()->f.core.consume(issued.ticket()));
        }
        System.out.println("PASS wrong actor/parent, malformed/unknown/expired ticket and parent-bounded authentication deadline fail closed");
        for(String failure:List.of("add","commit"))try(var f=new Fixture()) {
            f.fail=failure;boolean failed=false;try{f.core.issue("123","456");}catch(Exception expected){failed=true;}Assert.assertTrue(failed);Assert.assertFalse(f.active());f.fail="";Assert.assertEquals(query("SELECT * FROM symphony_api_v3_socket_ticket").size(),0);f.core.issue("123","456");
        }
        for(String failure:List.of("update","commit"))try(var f=new Fixture()) {
            var issued=f.core.issue("123","456");String before=f.snapshot();f.fail=failure;boolean failed=false;try{f.core.consume(issued.ticket());}catch(Exception expected){failed=true;}Assert.assertTrue(failed);Assert.assertFalse(f.active());f.fail="";Assert.assertEquals(f.snapshot(),before);f.core.consume(issued.ticket());unauthorized(()->f.core.consume(issued.ticket()));
        }
        System.out.println("PASS insert/consume/commit-before-durable failures rollback the grant and permit one valid retry");
        try(var f=new Fixture()) {
            try{f.tickets.getForUpdate("hash");Assert.fail();}catch(IllegalStateException expected){}
            var tx=f.tickets.beginTransaction();try{boolean failed=false;try{f.core.issue("123","456");}catch(IllegalStateException expected){failed=true;}Assert.assertTrue(failed);}finally{tx.rollback();}
            var grant=f.core.consume(f.core.issue("123","456").ticket());f.clock=125000;Assert.assertFalse(f.core.authorized(grant));Assert.assertFalse(f.core.authorized(null));
        }
        System.out.println("PASS transaction guards and original connection expiry cannot be bypassed by caller nesting or missing grant");
        try(var f=new Fixture()){
            f.core.issue("123","456");f.clock=86_500_000;String before=f.snapshot();f.fail="remove";
            try{f.core.removeExpired();Assert.fail();}catch(RepositoryException expected){}f.fail="";Assert.assertEquals(f.snapshot(),before);
            Assert.assertEquals(f.core.removeExpired(),1);Assert.assertEquals(f.core.removeExpired(),0);
        }
        System.out.println("PASS expired grant cleanup uses a bounded transaction and rolls back a failed deletion");
        try(var f=new Fixture()){
            write("UPDATE symphony_api_v3_token SET scope='content.read' WHERE oId='456'");
            var issued=f.core.issueArticle("123","456","789");var grant=f.core.consume(issued.ticket());
            Assert.assertEquals(grant.channel(),"article");Assert.assertEquals(grant.targetId(),"789");Assert.assertTrue(f.core.authorized(grant));
            unauthorized(()->f.core.issue("123","456"));
            Assert.assertFalse(f.core.authorized(new ApiV3SocketTicketService.Grant("123","456",125000,"notifications","789")));
            Assert.assertFalse(f.core.authorized(new ApiV3SocketTicketService.Grant("123","456",125000,"unknown","789")));
            Assert.assertFalse(f.core.authorized(new ApiV3SocketTicketService.Grant("123","456",125000,"article","789' OR 1=1")));
            write("UPDATE symphony_api_v3_token SET scope='notifications.read' WHERE oId='456'");Assert.assertFalse(f.core.authorized(grant));
            unauthorized(()->f.core.issueArticle("123","456","789"));
        }
        System.out.println("PASS article grants persist immutable resource binding and require content.read; notification scope, unknown channels and injected targets cannot substitute");
        for(String mutation:List.of("UPDATE symphony_article SET articleStatus=1 WHERE oId='789'",
                "DELETE FROM symphony_article WHERE oId='789'","UPDATE symphony_user SET userStatus=1 WHERE oId='321'",
                "UPDATE symphony_user SET userStatus=4 WHERE oId='321'"))try(var f=new Fixture()){
            write("UPDATE symphony_api_v3_token SET scope='content.read' WHERE oId='456'");
            var grant=f.core.consume(f.core.issueArticle("123","456","789").ticket());var pending=f.core.issueArticle("123","456","789");
            write(mutation);Assert.assertFalse(f.core.authorized(grant));unauthorized(()->f.core.consume(pending.ticket()));
            Assert.assertEquals(query("SELECT consumedAt FROM symphony_api_v3_socket_ticket WHERE oId=?",ApiV3TokenUtil.hash(pending.ticket())).getFirst().getLong("consumedAt"),0L);
            try{f.core.issueArticle("123","456","789");Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,404);}
        }
        System.out.println("PASS article removal/invalidity and current author blocking deny issuance, pending ticket use and established grant without cached article/user reads");
        try(var f=new Fixture()){
            write("UPDATE symphony_api_v3_token SET scope='content.read' WHERE oId='456'");
            int discussion=org.b3log.symphony.model.Article.ARTICLE_TYPE_C_DISCUSSION;
            write("UPDATE symphony_article SET articleType=?,articleContent=? WHERE oId='789'",discussion,"@Reader invited");
            var grant=f.core.consume(f.core.issueArticle("123","456","789").ticket());Assert.assertTrue(f.core.authorized(grant));
            write("UPDATE symphony_user SET userName='Renamed' WHERE oId='123'");Assert.assertFalse(f.core.authorized(grant));
            write("UPDATE symphony_article SET articleContent='@Renamed<br>invited' WHERE oId='789'");Assert.assertTrue(f.core.authorized(grant));
            write("UPDATE symphony_article SET articleContent='@RenamedX not an invitation' WHERE oId='789'");Assert.assertFalse(f.core.authorized(grant));
            write("UPDATE symphony_user SET userRole=? WHERE oId='123'",org.b3log.symphony.model.Role.ROLE_ID_C_ADMIN);Assert.assertTrue(f.core.authorized(grant));
            write("UPDATE symphony_user SET userRole='defaultRole' WHERE oId='123'");Assert.assertFalse(f.core.authorized(grant));
            write("UPDATE symphony_article SET articleAuthorId='123' WHERE oId='789'");Assert.assertTrue(f.core.authorized(grant));
        }
        System.out.println("PASS discussion invitation/name/role changes use current rows; exact shared legacy mention parsing and author/admin exceptions remain");
        try(var f=new Fixture()){
            write("UPDATE symphony_api_v3_token SET scope='content.read' WHERE oId='456'");
            var issued=f.core.issueArticle("123","456","789");String before=f.snapshot();f.fail="target.select";
            try{f.core.consume(issued.ticket());Assert.fail();}catch(RepositoryException expected){}
            try{f.core.issueArticle("123","456","789");Assert.fail();}catch(RepositoryException expected){}
            f.fail="";Assert.assertEquals(f.snapshot(),before);Assert.assertFalse(f.active());
            f.core.consume(issued.ticket());
            write("INSERT INTO symphony_api_v3_socket_ticket(oId,userId,parentTokenId,createdAt,expiresAt,consumedAt) VALUES(?,?,?,5000,65000,0)",ApiV3TokenUtil.hash("a".repeat(43)),"321","654");
            var old=f.core.consume("a".repeat(43));Assert.assertEquals(old.channel(),"notifications");Assert.assertEquals(old.targetId(),"");
        }
        System.out.println("PASS source failures roll back pending consumption and migrated pre-article grants retain the original notification scope/binding");
        for(String channel:List.of("unknown","article",""))try(var f=new Fixture()){
            var issued=f.core.issue("123","456");write("UPDATE symphony_api_v3_socket_ticket SET channel=?,targetId='789'",channel);
            unauthorized(()->f.core.consume(issued.ticket()));Assert.assertEquals(query("SELECT consumedAt FROM symphony_api_v3_socket_ticket").getFirst().getLong("consumedAt"),0L);
        }
        System.out.println("PASS persisted channel/target corruption cannot broaden a notification-only token or consume its invalid grant");
        try(var f=new Fixture()){
            unauthorized(()->f.core.issueChat("123","456"));
            write("UPDATE symphony_api_v3_token SET scope='content.read' WHERE oId='456'");
            var issued=f.core.issueChat("123","456");var grant=f.core.consume(issued.ticket());
            Assert.assertEquals(grant.channel(),"chat");Assert.assertEquals(grant.targetId(),"");Assert.assertTrue(f.core.authorized(grant));
            unauthorized(()->f.core.consume(issued.ticket()));
            Assert.assertFalse(f.core.authorized(new ApiV3SocketTicketService.Grant("123","456",125000,"chat","789")));
            var pending=f.core.issueChat("123","456");write("UPDATE symphony_user SET userStatus=4 WHERE oId='123'");
            Assert.assertFalse(f.core.authorized(grant));unauthorized(()->f.core.consume(pending.ticket()));
            Assert.assertEquals(query("SELECT consumedAt FROM symphony_api_v3_socket_ticket WHERE oId=?",ApiV3TokenUtil.hash(pending.ticket())).getFirst().getLong("consumedAt"),0L);
        }
        System.out.println("PASS chat grants require content.read, persist an empty public-room binding, consume once and reject disabled users or forged resource targets");

        try(var f=new Fixture()){
            unauthorized(()->f.core.issueMessages("123","456"));
            write("UPDATE symphony_api_v3_token SET scope='messages.write' WHERE oId='456'");unauthorized(()->f.core.issueMessages("123","456"));
            write("UPDATE symphony_api_v3_token SET scope='messages.read' WHERE oId='456'");
            unauthorized(()->f.core.issueMessages("321","456"));
            var ticket=f.core.issueMessages("123","456");var grant=f.core.consume(ticket.ticket());
            Assert.assertEquals(grant.channel(),"messages");Assert.assertEquals(grant.targetId(),"");Assert.assertTrue(f.core.authorized(grant));
            Assert.assertFalse(f.core.authorized(new ApiV3SocketTicketService.Grant("123","456",125000,"messages","321")));
            unauthorized(()->f.core.consume(ticket.ticket()));
            var pending=f.core.issueMessages("123","456");write("UPDATE symphony_api_v3_socket_ticket SET targetId='321' WHERE oId=?",ApiV3TokenUtil.hash(pending.ticket()));unauthorized(()->f.core.consume(pending.ticket()));
        }
        for(String change:List.of("UPDATE symphony_api_v3_token SET state=2 WHERE oId='456'","UPDATE symphony_api_v3_token SET scope='messages.write' WHERE oId='456'","UPDATE symphony_user SET userStatus=4 WHERE oId='123'","UPDATE symphony_api_v3_client SET status=1","UPDATE symphony_api_v3_token SET expiresAt=5000 WHERE oId='456'"))try(var f=new Fixture()){
            write("UPDATE symphony_api_v3_token SET scope='messages.read' WHERE oId='456'");
            var grant=f.core.consume(f.core.issueMessages("123","456").ticket());var pending=f.core.issueMessages("123","456");
            write(change);Assert.assertFalse(f.core.authorized(grant));unauthorized(()->f.core.consume(pending.ticket()));unauthorized(()->f.core.issueMessages("123","456"));
        }
        System.out.println("PASS private grants require messages.read on the exact parent owner, consume once, reject target substitution and recheck token/scope/user/client/expiry before issue, consume and delivery");

    }
}
