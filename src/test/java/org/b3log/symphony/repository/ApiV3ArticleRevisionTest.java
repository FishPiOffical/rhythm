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
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import sun.misc.Unsafe;
import org.b3log.latke.repository.*;
import org.b3log.symphony.model.*;
import org.b3log.symphony.service.ReactionMgmtService;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;
import org.testng.Assert;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** 用 SQLite 数据和缓存适配器验证实际的仓库钩子与共用表态服务。MySQL 和 HTTP 需另行验证。 */
public class ApiV3ArticleRevisionTest {
    interface Work { void run() throws Exception; }
    static void inject(Object bean,Class<?> owner,String field,Object value)throws Exception{var f=owner.getDeclaredField(field);f.setAccessible(true);f.set(bean,value);}
    static class Fixture implements AutoCloseable {
        final Path file=Files.createTempFile("rhythm-article-marker-",".sqlite");final String url="jdbc:sqlite:"+file;
        final Connection writer=DriverManager.getConnection(url),reader=DriverManager.getConnection(url),second=DriverManager.getConnection(url);
        String fail="";final Writer revisions=new Writer();final Articles articles=new Articles();final Comments comments=new Comments();final Reactions reactions=new Reactions();
        final ReactionMgmtService management=new ReactionMgmtService();
        Fixture()throws Exception{
            execute(writer,"CREATE TABLE marker(oId TEXT PRIMARY KEY,revision TEXT,updatedAt BIGINT)");
            execute(writer,"CREATE TABLE article(oId TEXT PRIMARY KEY,articleContent TEXT,articleTitle TEXT,articleViewCnt INT)");
            execute(writer,"CREATE TABLE comment(oId TEXT PRIMARY KEY,commentOnArticleId TEXT,commentContent TEXT,commentVisible INT,commentAnonymous INT,commentStatus INT,commentAuthorId TEXT)");
            execute(writer,"CREATE TABLE reaction(oId TEXT PRIMARY KEY,reactionUserId TEXT,reactionTargetType TEXT,reactionTargetId TEXT,reactionGroup TEXT,reactionValue TEXT,reactionCreatedTime BIGINT,reactionUpdatedTime BIGINT)");
            execute(writer,"CREATE TABLE user(oId TEXT PRIMARY KEY)");execute(writer,"INSERT INTO user VALUES('1')");execute(writer,"CREATE TABLE chat(oId TEXT PRIMARY KEY)");execute(writer,"INSERT INTO chat VALUES('9')");
            inject(articles,ArticleRepository.class,"realtimeRevisions",revisions);inject(articles,ArticleRepository.class,"articleCache",new org.b3log.symphony.cache.ArticleCache());
            inject(comments,CommentRepository.class,"realtimeRevisions",revisions);inject(comments,CommentRepository.class,"commentCache",new org.b3log.symphony.cache.CommentCache());
            inject(management,ReactionMgmtService.class,"chatEvents",new ApiV3ChatEventRepository(){@Override public long appendInCurrentTransaction(String id,String kind){Assert.assertEquals(id,"9");Assert.assertEquals(kind,"reactions");return 1;}});
            for(var e:Map.<String,Object>of("reactionRepository",reactions,"articleRepository",articles,"commentRepository",comments,"userRepository",new UserRepository(),"chatRoomRepository",new ChatRoomRepository(),"realtimeRevisions",revisions).entrySet())inject(management,ReactionMgmtService.class,e.getKey(),e.getValue());
        }
        boolean active(){try{return !writer.getAutoCommit();}catch(SQLException e){throw new RuntimeException(e);}}
        void begin()throws Exception{
            writer.setAutoCommit(false);var access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
            var tx=(JdbcTransaction)((Unsafe)access.get(null)).allocateInstance(JdbcTransaction.class);
            var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,writer);
            field=JdbcTransaction.class.getDeclaredField("isActive");field.setAccessible(true);field.setBoolean(tx,true);JdbcRepository.TX.set(tx);
        }
        void commit()throws Exception{writer.commit();writer.setAutoCommit(true);JdbcRepository.TX.remove();}
        void rollback()throws Exception{writer.rollback();writer.setAutoCommit(true);JdbcRepository.TX.remove();}
        void atomic(Work work)throws Exception{atomic(work,true);}
        void atomic(Work work,boolean boundary)throws Exception{
            begin();try(var cache=boundary?ArticlePublicationCaches.open(this::active):null){try{work.run();commit();}finally{if(active())rollback();}}
        }
        void article(String id)throws Exception{articles.add(new JSONObject().put("oId",id).put("articleContent","PRIVATE-content").put("articleTitle","title").put("articleHotScore",999));}
        void comment(String id,String article)throws Exception{comments.add(new JSONObject().put("oId",id).put("commentOnArticleId",article).put("commentContent","PRIVATE-comment").put("commentVisible",1).put("commentAnonymous",1));}
        String revision(String id)throws Exception{return snapshot(reader,List.of(id)).get(id);}
        Map<String,String> snapshot(Connection c,Collection<String> ids)throws Exception{
            return new ApiV3ArticleRevisionRepository(){@Override public String getName(){return "marker";}@Override public boolean hasTransactionBegun(){return false;}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(c,sql,args);}}.snapshot(ids);
        }
        void data(String table,String id,JSONObject row,String...fields)throws RepositoryException{
            Assert.assertTrue(active());if(fail.equals(table))throw new RepositoryException("injected data failure");
            var names=fields.length==0?new ArrayList<>(row.keySet()):new ArrayList<>(Arrays.asList(fields));names.remove("oId");
            var values=new ArrayList<Object>();for(String name:names){Assert.assertTrue(name.matches("[A-Za-z0-9_]+"));values.add(row.get(name));}values.add(id);
            execute(writer,"UPDATE "+table+" SET "+String.join(",",names.stream().map(n->n+"=?").toList())+" WHERE oId=?",values.toArray());
        }
        String insert(String table,JSONObject row)throws RepositoryException{
            Assert.assertTrue(active());if(fail.equals(table))throw new RepositoryException("injected data failure");
            var names=new ArrayList<>(row.keySet());for(String name:names)Assert.assertTrue(name.matches("[A-Za-z0-9_]+"));
            execute(writer,"INSERT INTO "+table+" ("+String.join(",",names)+") VALUES ("+String.join(",",Collections.nCopies(names.size(),"?"))+")",names.stream().map(row::get).toArray());return row.getString("oId");
        }
        void remove(String table,String id)throws RepositoryException{Assert.assertTrue(active());if(fail.equals(table))throw new RepositoryException("injected data failure");execute(writer,"DELETE FROM "+table+" WHERE oId=?",id);}
        class Writer extends ApiV3ArticleRevisionRepository {
            @Override public String getName(){return "marker";}@Override public boolean hasTransactionBegun(){return active();}@Override protected Connection currentConnection(){return writer;}
            @Override protected String upsertSql(){return fail.equals("marker")?"INSERT INTO missing_marker VALUES(?,?,?)":super.upsertSql().replace("ON DUPLICATE KEY UPDATE revision=VALUES(revision),updatedAt=VALUES(updatedAt)","ON CONFLICT(oId) DO UPDATE SET revision=excluded.revision,updatedAt=excluded.updatedAt");}
        }
        class Articles extends ArticleRepository {
            @Override public String getName(){return "article";}@Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(writer,sql.replace(" FOR UPDATE",""),args);}
            @Override public JSONObject get(String id)throws RepositoryException{var rows=select("SELECT * FROM article WHERE oId=?",id);return rows.isEmpty()?null:rows.getFirst();}
            @Override protected String insertArticle(JSONObject row)throws RepositoryException{return insert("article",row);}
            @Override protected void updateArticle(String id,JSONObject row,String...fields)throws RepositoryException{data("article",id,row,fields);}
            @Override protected void removeArticle(String id)throws RepositoryException{Fixture.this.remove("article",id);}
        }
        class Comments extends CommentRepository {
            @Override public String getName(){return "comment";}@Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(writer,sql.replace(" FOR UPDATE",""),args);}
            @Override public JSONObject get(String id)throws RepositoryException{var rows=select("SELECT * FROM comment WHERE oId=?",id);return rows.isEmpty()?null:rows.getFirst();}
            @Override protected String insertComment(JSONObject row)throws RepositoryException{return insert("comment",row);}
            @Override protected void updateComment(String id,JSONObject row,String...fields)throws RepositoryException{data("comment",id,row,fields);}
            @Override protected void removeCommentRow(String id)throws RepositoryException{Fixture.this.remove("comment",id);}
        }
        class Reactions extends ReactionRepository {
            @Override public String getName(){return "reaction";}@Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(writer,sql.replace(" FOR UPDATE",""),args);}
            @Override public String add(JSONObject row)throws RepositoryException{return insert("reaction",new JSONObject(row.toString()).put("oId",UUID.randomUUID().toString()));}
            @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException{data("reaction",id,row,fields);}
            @Override public void remove(String id)throws RepositoryException{Fixture.this.remove("reaction",id);}
        }
        public void close()throws Exception{if(active())rollback();writer.close();reader.close();second.close();Files.deleteIfExists(file);}
    }
    static List<JSONObject> query(Connection c,String sql,Object...args)throws RepositoryException{try(var s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));out.add(row);}return out;}}catch(SQLException e){throw new RepositoryException(e);}}
    static void execute(Connection c,String sql,Object...args)throws RepositoryException{try(var s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}catch(SQLException e){throw new RepositoryException(e);}}
    public static void main(String[] args) throws Exception {
        var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        for (String name : List.of("latkeProps", "localProps")) {
            var f = Latkes.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(null, props);
        }
        try (var f = new Fixture()) {
            f.begin();
            f.article("101");
            Assert.assertNull(f.revision("101"));
            Assert.assertTrue(query(f.reader, "SELECT * FROM article").isEmpty());
            f.commit();
            String first = f.revision("101");
            Assert.assertNotNull(first);
            Assert.assertEquals(f.snapshot(f.second, List.of("101")).get("101"), first);

            f.begin();
            try (var cache = ArticlePublicationCaches.open(f::active)) {
                f.articles.update("101", new JSONObject().put("articleContent", "changed"), "articleContent");
                Assert.assertEquals(f.revision("101"), first);
                f.rollback();
            }
            Assert.assertEquals(f.revision("101"), first);
            Assert.assertEquals(query(f.reader, "SELECT articleContent FROM article").getFirst().getString("articleContent"), "PRIVATE-content");
            f.atomic(() -> f.articles.update("101", new JSONObject().put("articleContent", "changed"), "articleContent"));
            Assert.assertNotEquals(f.revision("101"), first);
            String second = f.revision("101");
            f.atomic(() -> f.articles.remove("101"));
            Assert.assertNotEquals(f.revision("101"), second);
            Assert.assertTrue(query(f.reader, "SELECT * FROM article").isEmpty());
        }
        System.out.println("PASS real article add/edit/delete hooks couple markers to committed data, preserve generated-column filtering and hide uncommitted/rolled-back writes from independent readers");
        for (boolean boundary : List.of(false, true)) try (var f = new Fixture()) {
            f.atomic(() -> {
                f.article("101");
                f.article("202");
                f.comment("301", "101");
            }, boundary);
            String first = f.revision("101"), other = f.revision("202");
            // 正文更新没有写入所属文章列，真正迁移评论时才更新两篇文章的版本。
            f.atomic(() -> f.comments.update("301", new JSONObject().put("commentContent", "edited")
                    .put("commentOnArticleId", "202"), "commentContent"), boundary);
            Assert.assertNotEquals(f.revision("101"), first);
            Assert.assertEquals(f.revision("202"), other);
            first = f.revision("101");
            f.atomic(() -> f.comments.update("301", new JSONObject().put("commentOnArticleId", "202"), "commentOnArticleId"), boundary);
            Assert.assertNotEquals(f.revision("101"), first);
            Assert.assertNotEquals(f.revision("202"), other);
            first = f.revision("101");
            other = f.revision("202");
            f.atomic(() -> f.comments.remove("301"), boundary);
            Assert.assertEquals(f.revision("101"), first);
            Assert.assertNotEquals(f.revision("202"), other);
        }
        System.out.println("PASS comment creation/edit/delete signal its current article in both cache modes; partial fields cannot forge a parent, while an applied internal move signals both parents");
        for (String failure : List.of("article", "comment", "marker")) try (var f = new Fixture()) {
            f.atomic(() -> {
                f.article("101");
                f.comment("301", "101");
            });
            String before = f.revision("101");
            f.fail = failure;
            try {
                f.atomic(() -> {
                    f.articles.update("101", new JSONObject().put("articleContent", "bad"), "articleContent");
                    f.comments.remove("301");
                });
                Assert.fail();
            } catch (RepositoryException expected) {
            }
            Assert.assertEquals(f.revision("101"), before);
            Assert.assertEquals(query(f.reader, "SELECT articleContent FROM article").getFirst().getString("articleContent"), "PRIVATE-content");
            Assert.assertEquals(query(f.reader, "SELECT * FROM comment").size(), 1);
        }
        System.out.println("PASS article/comment/marker failures propagate and the owning transaction rolls back the whole data/marker mutation");
        for (String operation : List.of("article.add", "article.remove", "comment.add", "comment.update", "comment.remove"))
            try (var f = new Fixture()) {
                f.atomic(() -> {
                    f.article("101");
                    f.comment("301", "101");
                });
                String before = f.revision("101");
                String rows = query(f.reader, "SELECT * FROM article").toString() + query(f.reader, "SELECT * FROM comment");
                f.fail = "marker";
                try {
                    f.atomic(() -> {
                        switch (operation) {
                            case "article.add" -> f.article("202");
                            case "article.remove" -> f.articles.remove("101");
                            case "comment.add" -> f.comment("302", "101");
                            case "comment.update" -> f.comments.update("301", new JSONObject().put("commentContent", "bad"), "commentContent");
                            case "comment.remove" -> f.comments.remove("301");
                            default -> throw new AssertionError();
                        }
                    });
                    Assert.fail(operation);
                } catch (RepositoryException expected) {
                }
                Assert.assertEquals(f.revision("101"), before);
                Assert.assertNull(f.revision("202"));
                Assert.assertEquals(query(f.reader, "SELECT * FROM article").toString() + query(f.reader, "SELECT * FROM comment"), rows, operation);
            }
        System.out.println("PASS each article/comment add/update/delete marker failure rolls its preceding data write back without a new visible revision");
        try (var f = new Fixture()) {
            f.atomic(() -> {
                f.article("101");
                f.comment("301", "101");
            });
            String first = f.revision("101");
            f.atomic(() -> Assert.assertTrue(f.management.setInCurrentTransaction("1", "article", "101", "thumbsup", null)));
            Assert.assertNotEquals(f.revision("101"), first);
            first = f.revision("101");
            f.atomic(() -> Assert.assertFalse(f.management.setInCurrentTransaction("1", "article", "101", "thumbsup", null)));
            Assert.assertEquals(f.revision("101"), first);
            f.atomic(() -> Assert.assertTrue(f.management.setInCurrentTransaction("1", "comment", "301", "thumbsup", null)));
            Assert.assertNotEquals(f.revision("101"), first);
            first = f.revision("101");
            f.atomic(() -> Assert.assertTrue(f.management.setInCurrentTransaction("1", "comment", "301", "", null)));
            Assert.assertNotEquals(f.revision("101"), first);
            first = f.revision("101");
            f.atomic(() -> Assert.assertTrue(f.management.setInCurrentTransaction("1", "chat", "9", "thumbsup", null)));
            Assert.assertEquals(f.revision("101"), first);
            Assert.assertNull(f.revision("9"));
            f.fail = "marker";
            try {
                f.atomic(() -> f.management.setInCurrentTransaction("1", "article", "101", "heart", null));
                Assert.fail();
            } catch (RepositoryException expected) {
            }
            Assert.assertEquals(f.revision("101"), first);
            Assert.assertEquals(query(f.reader, "SELECT reactionValue FROM reaction WHERE reactionTargetType='article'")
                    .getFirst().getString("reactionValue"), "thumbsup");
        }
        System.out.println("PASS real shared reaction service couples article/comment changes to the parent marker, leaves no-op/chat untouched and rolls reaction back on marker failure");
        try (var f = new Fixture()) {
            try {
                f.article("101");
                Assert.fail();
            } catch (IllegalStateException expected) {
            }
            try {
                f.comments.remove("301");
                Assert.fail();
            } catch (IllegalStateException expected) {
            }
            f.begin();
            try {
                f.revisions.snapshot(List.of("101"));
                Assert.fail();
            } catch (IllegalStateException expected) {
            } finally {
                f.rollback();
            }
            f.atomic(() -> {
                for (int i = 0; i < 205; i++) f.revisions.touchInCurrentTransaction("" + i);
            });
            Assert.assertEquals(f.snapshot(f.reader, java.util.stream.IntStream.range(0, 205).mapToObj(i -> "" + i).toList()).size(), 205);
            Assert.assertTrue(f.snapshot(f.reader, List.of("101' OR 1=1 --")).isEmpty());
            Assert.assertFalse(ApiV3ArticleRevision.REPOSITORY.startsWith("symphony_"));
        }
        System.out.println("PASS write/snapshot transaction guards, bounded parameterized article snapshots and repository prefix invariants");
    }
}
