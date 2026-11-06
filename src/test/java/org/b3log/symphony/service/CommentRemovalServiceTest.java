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
import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.symphony.util.AccountPairLocks;
import org.b3log.symphony.util.CommentRemoval;
import org.b3log.symphony.util.CommentRemovalQueries;
import org.b3log.symphony.util.ContentNotificationReferences;
import org.json.JSONObject;
import org.testng.Assert;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

/** Actual service and coordinator, using explicit SQLite adapters instead of the app/points/cache runtime. */
public class CommentRemovalServiceTest {
    static Fixture current;
    public static void main(final String[] args) throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");
        for(final String name:List.of("latkeProps","localProps")){final var field=Latkes.class.getDeclaredField(name);field.setAccessible(true);field.set(null,props);}
        try(final var f=new Fixture()){
            AccountPairLocks.execute(List.of(new AccountPairLocks.Pair("owner",Pointtransfer.ID_C_SYS)),()->{
                try{f.remove("owner","30");Assert.fail();}catch(final IllegalStateException expected){Assert.assertTrue(expected.getMessage().contains("transaction"));}
                f.db.setAutoCommit(false);try{f.remove("owner","30");Assert.fail();}catch(final IllegalStateException expected){Assert.assertTrue(expected.getMessage().contains("transaction"));}finally{f.db.rollback();f.db.setAutoCommit(true);}
                return null;
            });
            f.db.setAutoCommit(false);
            try(final var scope=ArticlePublicationCaches.open(f::active)){
                try{f.remove("owner","30");Assert.fail();}catch(final IllegalStateException expected){Assert.assertTrue(expected.getMessage().contains("account-pair"));}
                finally{f.db.rollback();f.db.setAutoCommit(true);}
            }
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),0L);
        }
        System.out.println("PASS actual removal service requires caller transaction and publication cache scope before any charge/write");

        try(final var f=new Fixture()){
            final var result=f.transaction(()->{
                final var removed=f.remove("owner","30");
                try{f.service.afterRemoved(removed);Assert.fail();}catch(final IllegalStateException expected){}
                return removed;
            });
            Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='owner'"),400L);
            Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='sys'"),100L);
            Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='older'"),900L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM comment WHERE oId='30'"),0L);
            Assert.assertEquals(f.scalar("SELECT commentReplyCnt FROM comment WHERE oId='20'"),0L);
            Assert.assertEquals(f.scalar("SELECT userCommentCount FROM user WHERE oId='owner'"),0L);
            Assert.assertEquals(f.scalar("SELECT userLatestCmtTime FROM user WHERE oId='owner'"),777L);
            Assert.assertEquals(f.scalar("SELECT articleCommentCount FROM article WHERE oId='10'"),1L);
            Assert.assertEquals(f.scalar("SELECT articleLatestCmtTime FROM article WHERE oId='10'"),20L);
            Assert.assertEquals(f.text("SELECT articleLatestCmterName FROM article WHERE oId='10'"),UserExt.ANONYMOUS_USER_NAME);
            Assert.assertEquals(f.scalar("SELECT optionValue FROM option WHERE oId='statisticCmtCount'"),4L);
            Assert.assertEquals(f.scalar("SELECT MIN(tagCommentCount) FROM tag"),9L);
            Assert.assertEquals(f.scalar("SELECT MIN(articleCommentCount) FROM tag_article"),1L);
            Assert.assertEquals(f.scalar("SELECT MIN(articleLatestCmtTime) FROM tag_article"),20L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM revision"),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM reaction"),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification"),3L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM profession_event"),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),1L);
            Assert.assertEquals(new HashSet<>(result.notificationUsers()),Set.of("owner","older"));
            Assert.assertFalse(result.toString().contains("SECRET"));
            try{result.notificationUsers().clear();Assert.fail();}catch(final UnsupportedOperationException expected){}
            Assert.assertEquals(f.indexEvents,0);Assert.assertTrue(f.refreshed.isEmpty());
            f.service.afterRemoved(result);Assert.assertEquals(f.indexEvents,1);Assert.assertEquals(f.refreshed.size(),2);
        }
        System.out.println("PASS actual service commits fee, cleanup/counters/parent/tags/profession together; anonymous latest name is hidden and effects wait for commit");

        for(final String stage:List.of("profession","comment.remove","comment.update","user.update","article.update","option.update","tag.update","tag_article.update","revision.remove","reaction.remove","notification.remove","reward.select")){
            try(final var f=new Fixture()){
                final String before=f.snapshot();f.fail=stage;
                try{f.transaction(()->f.remove("owner","30"));Assert.fail("Expected "+stage);}catch(final RepositoryException expected){}
                Assert.assertEquals(f.snapshot(),before,stage);Assert.assertEquals(f.indexEvents,0);Assert.assertTrue(f.refreshed.isEmpty());
            }
        }
        System.out.println("PASS real service SQL failures at every deletion stage/reward lookup roll back fee, counters, cleanup and profession records");

        for(final String stage:List.of("debit-null","debit-blank","debit-failure")){
            try(final var f=new Fixture()){
                final String before=f.snapshot();f.fail=stage;
                try{f.transaction(()->f.remove("owner","30"));Assert.fail();}catch(final IllegalStateException expected){}
                Assert.assertEquals(f.snapshot(),before);
            }
        }
        System.out.println("PASS null/blank/failed debit cannot authorize deletion, including rollback after adapter already changed balances");

        final Map<String,String> denials=Map.ofEntries(
            Map.entry("UPDATE comment SET commentReplyCnt=1 WHERE oId='30'","has_replies"),
            Map.entry("INSERT INTO comment(oId,commentOnArticleId,commentAuthorId,commentOriginalCommentId) VALUES('99','11','other','30')","has_replies"),
            Map.entry("UPDATE comment SET commentGoodCnt=1 WHERE oId='30'","has_votes"),
            Map.entry("UPDATE comment SET commentBadCnt=1 WHERE oId='30'","has_votes"),
            Map.entry("INSERT INTO vote VALUES('v','30',1)","has_votes"),
            Map.entry("UPDATE comment SET commentThankCnt=1 WHERE oId='30'","has_thanks"),
            Map.entry("INSERT INTO reward VALUES('r','30',1)","has_thanks"),
            Map.entry("UPDATE comment SET commentQnAOffered=1 WHERE oId='30'","accepted"),
            Map.entry("UPDATE user SET userPoint=99 WHERE oId='owner'","insufficient_balance"));
        for(final var entry:denials.entrySet())try(final var f=new Fixture()){
            f.exec(entry.getKey());final String before=f.snapshot();
            try{f.transaction(()->f.remove("owner","30"));Assert.fail();}catch(final CommentRemoval.Rejected expected){Assert.assertEquals(expected.code,entry.getValue());}
            Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS stored counters and actual replies/votes/thanks/accepted state reject before fee, including cross-article legacy children");

        for(final String[] input:List.of(new String[]{"owner","31"},new String[]{"older","30"},new String[]{"owner","30' OR 1=1 --"}))try(final var f=new Fixture()){
            final String before=f.snapshot();try{f.transaction(input[0],()->f.remove(input[0],input[1]));Assert.fail();}catch(final CommentRemoval.Rejected expected){Assert.assertEquals(expected.code,"not_found");}
            Assert.assertEquals(f.snapshot(),before);
        }
        try(final var f=new Fixture()){
            final String before=f.snapshot();
            try{f.transaction(()->f.service.removeInCurrentTransaction("owner","30",(actor,article,comment)->{throw new CommentRemoval.Rejected("forbidden");}));Assert.fail();}
            catch(final CommentRemoval.Rejected expected){Assert.assertEquals(expected.code,"forbidden");}
            Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS owner/ID binding, missing/injected targets and explicit target denial cannot charge or touch another user's records");

        try(final var f=new Fixture()){
            f.exec("DELETE FROM comment WHERE oId='20'");f.exec("UPDATE comment SET commentOriginalCommentId='',commentAnonymous=1,commentVisible=1 WHERE oId='30'");
            f.exec("UPDATE article SET articleCommentCount=1");
            f.transaction(()->f.remove("owner","30"));
            Assert.assertEquals(f.scalar("SELECT articleLatestCmtTime FROM article"),10L);Assert.assertEquals(f.text("SELECT articleLatestCmterName FROM article"),"");
            Assert.assertEquals(f.scalar("SELECT articleCommentCount FROM article"),0L);Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='owner'"),400L);
        }
        System.out.println("PASS last-comment fallback restores article time/empty latest name; anonymous/private deletion retains the existing fee");

        try(final var f=new Fixture()){
            final var result=f.transaction(()->f.remove("owner","30"));f.fail="index-event";f.service.afterRemoved(result);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM comment WHERE oId='30'"),0L);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),1L);Assert.assertEquals(f.refreshed.size(),2);
        }
        for(final String invalid:List.of("30%","30_","30' OR 1=1 --"))try{ContentNotificationReferences.target("notification",invalid,false,true);Assert.fail();}catch(final IllegalArgumentException expected){}
        try{CommentRemovalQueries.latest("comment;DROP TABLE user");Assert.fail();}catch(final IllegalArgumentException expected){}
        System.out.println("PASS after-commit effect failure cannot undo committed data; notification type/prefix and table-name guards preserve unrelated resources");

        try(final var f=new Fixture()){
            f.exec("INSERT INTO article(oId,articleAuthorId) VALUES('30','older')");
            f.exec("INSERT INTO notification VALUES('mention','owner','30',2),('article-notice','owner','30',0)");
            final var result=f.transaction(()->f.remove("owner","30"));
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification WHERE oId='mention'"),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification WHERE oId='article-notice'"),1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification WHERE oId IN('n1','n2','n3')"),0L);
            Assert.assertEquals(new HashSet<>(result.notificationUsers()),Set.of("owner","older"));
        }
        System.out.println("PASS actual owner removal preserves ambiguous AT/article references on ID collisions and refreshes only affected recipients");

        try(final var f=new Fixture()){
            final var first=f.idempotent("remove-one",new JSONObject());Assert.assertTrue(first.applied());
            Assert.assertEquals(first.result().getString("id"),"30");Assert.assertTrue(first.result().getBoolean("removed"));
            final var replay=f.idempotent("remove-one",new JSONObject());Assert.assertFalse(replay.applied());Assert.assertEquals(replay.result().toString(),first.result().toString());
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM requests"),1L);
            Assert.assertEquals(f.mutations,1);Assert.assertEquals(f.indexEvents,1);Assert.assertEquals(f.refreshed.size(),2);
            try{f.idempotent("remove-one",new JSONObject().put("changed",true));Assert.fail();}catch(final org.b3log.symphony.util.ApiV3Exception expected){Assert.assertEquals(expected.status,409);}
            try{f.idempotent("remove-two",new JSONObject());Assert.fail();}catch(final CommentRemoval.Rejected expected){Assert.assertEquals(expected.code,"not_found");}
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM requests"),1L);
        }
        System.out.println("PASS actual idempotency coordinator replays deleted target without charging/cleanup/events, rejects conflicts and does not persist missing-target results");

        try(final var f=new Fixture()){
            final String before=f.snapshot();f.failResults=true;
            try{f.idempotent("result-failure",new JSONObject());Assert.fail();}catch(final RepositoryException expected){}
            Assert.assertEquals(f.snapshot(),before);Assert.assertEquals(f.indexEvents,0);Assert.assertTrue(f.refreshed.isEmpty());
            f.failResults=false;Assert.assertTrue(f.idempotent("result-failure",new JSONObject()).applied());
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM requests"),1L);
        }
        System.out.println("PASS result persistence failure rolls deletion/points/profession back in the same SQLite transaction and permits one later successful retry");
        try(final var f=new Fixture()){
            final var lockField=CommentRemovalMgmtService.class.getDeclaredField("articleWrites");lockField.setAccessible(true);lockField.set(f.service,new ArticleMgmtService());
            f.exec("UPDATE comment SET commentReplyCnt=1 WHERE oId='30'");
            final String before=f.snapshot();
            try{f.service.remove("owner","30",(actor,article,comment)->{});Assert.fail();}catch(final CommentRemoval.Rejected expected){Assert.assertEquals(expected.code,"has_replies");}
            Assert.assertEquals(f.snapshot(),before);Assert.assertFalse(f.active());Assert.assertEquals(f.indexEvents,0);
            f.exec("UPDATE comment SET commentReplyCnt=0 WHERE oId='30'");
            f.fail="article.update";final String beforeFailure=f.snapshot();
            try{f.service.remove("owner","30",(actor,article,comment)->{});Assert.fail();}catch(final RepositoryException expected){}
            Assert.assertEquals(f.snapshot(),beforeFailure);Assert.assertFalse(f.active());Assert.assertEquals(f.indexEvents,0);
            f.fail="";final var result=f.service.remove("owner","30",(actor,article,comment)->{});
            Assert.assertEquals(result.id(),"30");Assert.assertFalse(f.active());Assert.assertEquals(f.indexEvents,1);
            Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='owner'"),400L);
        }
        System.out.println("PASS legacy transaction wrapper commits once, rolls denials/write failures back and runs effects after commit (publication-lock adapter)");
    }

    @FunctionalInterface interface Work {CommentRemoval.Result run()throws Exception;}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final CommentRemovalMgmtService service=new CommentRemovalMgmtService();String fail="";int indexEvents,mutations,activityEvents;boolean failResults;final List<String> refreshed=new ArrayList<>(),activityFields=new ArrayList<>();
        Fixture()throws Exception{
            current=this;
            exec("CREATE TABLE user(oId TEXT PRIMARY KEY,userName TEXT,userStatus INT,userPoint INT,userCommentCount INT,userLatestCmtTime INT,userEmail TEXT)");
            exec("CREATE TABLE article(oId TEXT PRIMARY KEY,articleAuthorId TEXT,articleCommentCount INT,articleLatestCmtTime INT,articleLatestCmterName TEXT,articleTags TEXT,articleTitle TEXT)");
            exec("CREATE TABLE comment(oId TEXT PRIMARY KEY,commentAuthorId TEXT,commentOnArticleId TEXT,commentOriginalCommentId TEXT,commentReplyCnt INT DEFAULT 0,commentGoodCnt INT DEFAULT 0,commentBadCnt INT DEFAULT 0,commentThankCnt INT DEFAULT 0,commentQnAOffered INT DEFAULT 0,commentAnonymous INT DEFAULT 0,commentVisible INT DEFAULT 0,commentContent TEXT)");
            exec("CREATE TABLE option(oId TEXT PRIMARY KEY,optionValue INT)");exec("CREATE TABLE tag(oId TEXT PRIMARY KEY,tagTitle TEXT,tagCommentCount INT,tagRandomDouble DOUBLE)");
            exec("CREATE TABLE tag_article(oId TEXT PRIMARY KEY,article_oId TEXT,articleCommentCount INT,articleLatestCmtTime INT)");
            exec("CREATE TABLE revision(oId TEXT PRIMARY KEY,revisionDataId TEXT,revisionDataType INT)");exec("CREATE TABLE reaction(oId TEXT PRIMARY KEY,reactionTargetType TEXT,reactionTargetId TEXT)");
            exec("CREATE TABLE notification(oId TEXT PRIMARY KEY,userId TEXT,dataId TEXT,dataType INT)");exec("CREATE TABLE reward(oId TEXT PRIMARY KEY,dataId TEXT,type INT)");exec("CREATE TABLE vote(oId TEXT PRIMARY KEY,dataId TEXT,dataType INT)");
            exec("CREATE TABLE ledger(oId TEXT PRIMARY KEY,actor TEXT,amount INT,recipient TEXT,transferType INT,dataId TEXT)");exec("CREATE TABLE profession_event(oId TEXT PRIMARY KEY)");
            exec("CREATE TABLE requests(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            exec("INSERT INTO user VALUES('owner','Owner',0,500,1,777,'SECRET-email'),('older','SECRET-real-name',0,900,1,888,'SECRET-other'),('sys','sys',0,0,0,0,'')");
            exec("INSERT INTO article VALUES('10','older',2,30,'Owner','a,b','Title')");
            exec("INSERT INTO comment(oId,commentAuthorId,commentOnArticleId,commentOriginalCommentId,commentReplyCnt,commentAnonymous,commentContent) VALUES('20','older','10','',1,1,'old'),('30','owner','10','20',0,0,'SECRET-body')");
            exec("INSERT INTO option VALUES('statisticArticleCount',1),('statisticCmtCount',5)");exec("INSERT INTO tag VALUES('t1','a',10,0),('t2','b',10,0)");exec("INSERT INTO tag_article VALUES('ta1','10',2,30),('ta2','10',2,30)");
            exec("INSERT INTO revision VALUES('r1','30',1),('r2','30',1),('r3','30',0)");exec("INSERT INTO reaction VALUES('e1','comment','30'),('e2','article','30')");
            exec("INSERT INTO notification VALUES('n1','owner','30',3),('n2','older','30',13),('n3','owner','30-owner',25),('n4','owner','30',42),('n5','older','300',3),('n6','older','300-owner',25)");
            for(final Object bean:List.of(new CommentRepository(),new ArticleRepository(),new UserRepository(),new OptionRepository(),new RewardRepository(),new VoteRepository(),new ReactionRepository(),new RevisionRepository(),new NotificationRepository(),new TagRepository(),new TagArticleRepository(),new PointtransferMgmtService(),new ProfessionSourceEventCaptureService(),new NotificationMgmtService())){
                final String field=switch(bean.getClass().getSimpleName()){
                    case "CommentRepository"->"comments";case "ArticleRepository"->"articles";case "UserRepository"->"users";case "OptionRepository"->"options";case "RewardRepository"->"rewards";case "VoteRepository"->"votes";case "ReactionRepository"->"reactions";case "RevisionRepository"->"revisions";case "NotificationRepository"->"notifications";case "TagRepository"->"tags";case "TagArticleRepository"->"relations";case "PointtransferMgmtService"->"points";case "ProfessionSourceEventCaptureService"->"professions";default->"notificationEvents";};
                final var target=CommentRemovalMgmtService.class.getDeclaredField(field);target.setAccessible(true);target.set(service,bean);
            }
        }
        boolean active(){try{return !db.getAutoCommit();}catch(Exception e){throw new IllegalStateException(e);}}
        CommentRemoval.Result remove(String actor,String id)throws Exception{return service.removeInCurrentTransaction(actor,id,(user,article,comment)->{Assert.assertEquals(user.getString("oId"),actor);Assert.assertEquals(article.getString("oId"),"10");});}
        CommentRemoval.Result transaction(Work work)throws Exception{return transaction("owner",work);}
        CommentRemoval.Result transaction(String actor,Work work)throws Exception{return AccountPairLocks.execute(List.of(new AccountPairLocks.Pair(actor,Pointtransfer.ID_C_SYS)),()->{db.setAutoCommit(false);try(final var caches=ArticlePublicationCaches.open(this::active)){try{final var result=work.run();db.commit();return result;}finally{db.rollback();db.setAutoCommit(true);}}});}
        ApiV3IdempotencyMgmtService.Execution idempotent(String key,JSONObject body)throws Exception{
            return AccountPairLocks.execute(List.of(new AccountPairLocks.Pair("owner",Pointtransfer.ID_C_SYS)),()->{
                final ApiV3IdempotencyMgmtService.Execution outcome;final var removed=new CommentRemoval.Result[1];
                try(final var cache=ArticlePublicationCaches.open(this::active)){
                    outcome=ApiV3IdempotencyMgmtService.runAtomicWithOutcome(new Requests(),"owner","comments.remove:30",key,body,()->{
                        mutations++;removed[0]=remove("owner","30");return new JSONObject().put("id","30").put("removed",true);
                    });
                }
                if(outcome.applied())service.afterRemoved(removed[0]);return outcome;
            });
        }
        void exec(String sql)throws Exception{try(final var statement=db.createStatement()){statement.execute(sql);}}
        long scalar(String sql)throws Exception{return Long.parseLong(text(sql));}
        String text(String sql)throws Exception{try(final var statement=db.createStatement();final var result=statement.executeQuery(sql)){result.next();return result.getString(1);}}
        String snapshot()throws Exception{final var value=new JSONObject();for(final String table:List.of("user","article","comment","option","tag","tag_article","revision","reaction","notification","reward","vote","ledger","profession_event","requests"))value.put(table,Rows.query("SELECT * FROM `"+table+"` ORDER BY oId"));return value.toString();}
        ApiV3RequestRepository requestStore(){return new Requests();}
        private class Requests extends ApiV3RequestRepository {
            @Override public String getName(){return "requests";}
            @Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return Rows.query(sql,args);}
            @Override public String add(JSONObject row)throws RepositoryException{Rows.write("INSERT INTO requests VALUES(?,?,?,?,?,?)",row.getString("oId"),row.getString("userId"),row.getString("operation"),row.getString("requestHash"),row.getString("responseJson"),row.getLong("createdAt"));return row.getString("oId");}
            @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException{if(failResults)throw new RepositoryException("fixture result failure");Rows.write("UPDATE requests SET responseJson=? WHERE oId=?",row.getString("responseJson"),id);}
            @Override public Transaction beginTransaction(){
                try{Assert.assertFalse(active());db.setAutoCommit(false);}catch(Exception e){throw new IllegalStateException(e);}
                return new Transaction(){boolean open=true;public boolean isActive(){return open;}public void commit(){finish(true);}public void rollback(){finish(false);}private void finish(boolean commit){try{if(commit)db.commit();else db.rollback();db.setAutoCommit(true);open=false;}catch(Exception e){throw new IllegalStateException(e);}}};
            }
        }
        @Override public void close()throws Exception{db.close();current=null;}
    }

    public static class Rows extends AbstractRepository {
        private final String table;public Rows(String table){super(table);this.table=table;}
        @Override public String getName(){return table;}
        @Override public boolean hasTransactionBegun(){return current.active();}
        @Override public Transaction beginTransaction(){return current.new Requests().beginTransaction();}
        public static JSONObject first(List<JSONObject> rows){return rows.isEmpty()?null:rows.getFirst();}
        @Override public JSONObject get(String id)throws RepositoryException{return first(select("SELECT * FROM `"+table+"` WHERE oId=?",id));}
        public JSONObject getForUpdate(String id)throws RepositoryException{Assert.assertTrue(hasTransactionBegun());return first(select("SELECT * FROM `"+table+"` WHERE oId=? FOR UPDATE",id));}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{fail(table+".select");return query(sql,args);}
        @Override public String add(JSONObject row)throws RepositoryException{
            Assert.assertTrue(hasTransactionBegun());fail(table+".add");
            if("vote".equals(table)){
                final String id=org.b3log.latke.util.Ids.genTimeMillisId();
                write("INSERT INTO vote(oId,userId,dataId,dataType,type) VALUES(?,?,?,?,?)",id,row.getString("userId"),row.getString("dataId"),row.getInt("dataType"),row.getInt("type"));return id;
            }
            Assert.assertEquals(table,"reward");
            write("INSERT INTO reward(oId,senderId,dataId,type) VALUES(?,?,?,?)",row.getString("oId"),row.getString("senderId"),row.getString("dataId"),row.getInt("type"));return row.getString("oId");
        }
        public static List<JSONObject> query(String sql,Object...args)throws RepositoryException{try(final var statement=current.db.prepareStatement(sql.replace(" FOR UPDATE",""))){for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);final var rows=new ArrayList<JSONObject>();try(final var result=statement.executeQuery()){while(result.next()){final var row=new JSONObject();for(int i=1;i<=result.getMetaData().getColumnCount();i++)row.put(result.getMetaData().getColumnLabel(i),result.getObject(i));rows.add(row);}}return rows;}catch(final Exception e){throw new RepositoryException(e);}}
        static void fail(String stage)throws RepositoryException{if(stage.equals(current.fail))throw new RepositoryException("fixture "+stage);}
        @Override public void remove(String id)throws RepositoryException{Assert.assertTrue(hasTransactionBegun());fail(table+".remove");write("DELETE FROM `"+table+"` WHERE oId=?",id);}
        @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException{
            Assert.assertTrue(hasTransactionBegun());fail(table+".update");
            if("tag_article".equals(table)&&"ta2".equals(id)&&"tag_article.second".equals(current.fail))throw new RepositoryException("fixture second relation update");
            final Set<String> allowed=switch(table){case "comment"->Set.of("commentReplyCnt","commentThankCnt","commentQnAOffered","commentGoodCnt","commentBadCnt","commentScore");case "user"->Set.of("userCommentCount");case "article"->Set.of("articleCommentCount","articleLatestCmtTime","articleLatestCmterName","articleGoodCnt","articleBadCnt","redditScore","articleThankCnt");case "option"->Set.of("optionValue");case "tag"->Set.of("tagCommentCount","tagRandomDouble");case "tag_article"->Set.of("articleCommentCount","articleLatestCmtTime","redditScore");default->throw new AssertionError(table);};
            Assert.assertFalse(fields.length==0);Assert.assertTrue(allowed.containsAll(Arrays.asList(fields)));
            final var args=new ArrayList<Object>();for(String field:fields)args.add(row.opt(field));args.add(id);
            write("UPDATE `"+table+"` SET "+String.join(",",Arrays.stream(fields).map(f->f+"=?").toList())+" WHERE oId=?",args.toArray());
        }
        static void write(String sql,Object...args)throws RepositoryException{try(final var statement=current.db.prepareStatement(sql)){for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);statement.executeUpdate();}catch(final Exception e){throw new RepositoryException(e);}}
    }
    public static class Payments {
        public String transferInCurrentTransaction(String from,String to,int type,int amount,String data,long now,String description,PointtransferSource source,boolean index){
            Assert.assertTrue(current.active());Assert.assertFalse(index);
            if(type==Pointtransfer.TRANSFER_TYPE_C_DEL_COMMENT)Assert.assertEquals(to,Pointtransfer.ID_C_SYS);
            else if(type==Pointtransfer.TRANSFER_TYPE_C_COMMENT_REWARD){Assert.assertTrue(data.endsWith("-10"));}
            else {Assert.assertTrue(Set.of(Pointtransfer.TRANSFER_TYPE_C_QNA_OFFER,Pointtransfer.TRANSFER_TYPE_C_ARTICLE_THANK,Pointtransfer.TRANSFER_TYPE_C_ARTICLE_REWARD).contains(type));Assert.assertTrue(data.matches("[0-9]+"));}
            try {if(Rows.query("SELECT userPoint FROM user WHERE oId=?",from).getFirst().optInt("userPoint")<amount)return null;}catch(Exception e){throw new IllegalStateException(e);}
            final String payment;
            try{long count=current.scalar("SELECT COUNT(*) FROM ledger");payment=count==0?"payment":"payment-"+(count+1);
                Rows.write("UPDATE user SET userPoint=userPoint-? WHERE oId=?",amount,from);Rows.write("UPDATE user SET userPoint=userPoint+? WHERE oId=?",amount,to);Rows.write("INSERT INTO ledger VALUES(?,?,?,?,?,?)",payment,from,amount,to,type,data);
            }catch(Exception e){throw new IllegalStateException(e);}
            if("debit-null".equals(current.fail))return null;if("debit-blank".equals(current.fail))return "";if("debit-failure".equals(current.fail))throw new IllegalStateException("fixture debit");return payment;
        }
        public void indexTransferAsync(String actor,String recipient,String id){Assert.assertFalse(current.active());Assert.assertFalse(ArticlePublicationCaches.active());if("index-event".equals(current.fail))throw new IllegalStateException("fixture index");current.indexEvents++;}
    }
    public static class Professions {
        public void targetRemoved(String type,String id)throws RepositoryException{Assert.assertTrue(current.active());Assert.assertEquals(type,"comment");Rows.fail("profession");Rows.write("INSERT INTO profession_event VALUES(?)",id);}
    }
    public static class NotificationEvents {
        public void addArticleRewardNotificationInCurrentTransaction(String recipient,String reward,int type)throws RepositoryException{
            Assert.assertTrue(current.active());Assert.assertTrue(Set.of(Notification.DATA_TYPE_C_POINT_ARTICLE_THANK,Notification.DATA_TYPE_C_POINT_ARTICLE_REWARD).contains(type));Rows.fail("notification.add");
            Rows.write("INSERT INTO notification VALUES(?,?,?,?)","article-reward-"+reward,recipient,reward,type);
        }
        public boolean addCommentTargetNotificationInCurrentTransaction(String recipient,String comment,int type)throws RepositoryException{
            Assert.assertTrue(current.active());Rows.fail("notification.select");
            if(!Rows.query("SELECT oId FROM notification WHERE userId=? AND dataId=? AND dataType=?",recipient,comment,type).isEmpty())return false;
            Rows.fail("notification.add");Rows.write("INSERT INTO notification VALUES(?,?,?,?)","comment-"+recipient+"-"+comment+"-"+type,recipient,comment,type);
            Rows.fail("notification.afterAdd");return true;
        }
        public boolean addCommentVoteNotificationInCurrentTransaction(String recipient,String comment,String actor)throws RepositoryException{
            return addVoteNotification(recipient,comment,actor,Notification.DATA_TYPE_C_COMMENT_VOTE_UP);
        }
        public boolean addArticleVoteNotificationInCurrentTransaction(String recipient,String article,String actor)throws RepositoryException{
            return addVoteNotification(recipient,article,actor,Notification.DATA_TYPE_C_ARTICLE_VOTE_UP);
        }
        private boolean addVoteNotification(String recipient,String resource,String actor,int type)throws RepositoryException{
            Assert.assertTrue(current.active());String data=resource+"-"+actor;
            if(!Rows.query("SELECT oId FROM notification WHERE userId=? AND dataId=? AND dataType=?",recipient,data,type).isEmpty())return false;
            Rows.fail("notification.add");Rows.write("INSERT INTO notification VALUES(?,?,?,?)","vote-"+type+"-"+data,recipient,data,type);return true;
        }
        public void addCommentRewardNotificationInCurrentTransaction(String recipient,String reward,int type)throws RepositoryException{
            Assert.assertTrue(current.active());Assert.assertTrue(Set.of(Notification.DATA_TYPE_C_POINT_COMMENT_THANK,Notification.DATA_TYPE_C_POINT_COMMENT_ACCEPT).contains(type));Rows.fail("notification.add");
            Rows.write("INSERT INTO notification(oId,userId,dataId,dataType) VALUES(?,?,?,?)","reward-"+reward,recipient,reward,type);
        }
        public void publishReadStateRefresh(String user){Assert.assertFalse(current.active());Assert.assertFalse(ArticlePublicationCaches.active());if("refresh-event".equals(current.fail))throw new IllegalStateException("fixture refresh");current.refreshed.add(user);}
    }
    public static class ActivityEvents {
        public void incLiveness(String actor,String field){Assert.assertFalse(current.active());Assert.assertFalse(ArticlePublicationCaches.active());Assert.assertTrue(Set.of(Liveness.LIVENESS_THANK,Liveness.LIVENESS_ACCEPT_ANSWER,Liveness.LIVENESS_VOTE,Liveness.LIVENESS_REWARD).contains(field));current.activityFields.add(field);current.activityEvents++;}
    }
}
