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
import java.sql.*;
import java.util.*;
import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.*;
import org.testng.Assert;

/** 执行实际查询服务及通用 DTO，隔离 SQLite 仅适配 MySQL 的一元 BINARY 语法。 */
public class ApiV3DiscoveryExtrasQueryTest {
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3DiscoveryExtrasQueryService service=new ApiV3DiscoveryExtrasQueryService(){protected double pivot(){return .5;}protected void shuffle(List<JSONObject> rows){}};
        boolean fail; final List<String> queries=new ArrayList<>();
        Fixture()throws Exception {
            sql("CREATE TABLE x_user(oId TEXT PRIMARY KEY,userStatus INT,userName TEXT,userNickname TEXT,userAvatarURL TEXT,userPassword TEXT,userPhone TEXT)");
            sql("CREATE TABLE x_article(oId TEXT PRIMARY KEY,articleAuthorId TEXT,articleStatus INT,articleType INT,articleAnonymous INT,articleShowInList INT,articleTags TEXT,articleAnonymousView INT,articleTitle TEXT,articlePermalink TEXT,articleCreateTime INT,articleUpdateTime INT,articleContent TEXT,articleRandomDouble REAL,articleRewardContent TEXT,articleIP TEXT,articleCommentCount INT DEFAULT 0,articleViewCount INT DEFAULT 0,articleStick INT DEFAULT 0)");
            sql("CREATE TABLE x_logs(oId TEXT PRIMARY KEY,`public` INT,`type` TEXT,key1 TEXT,key2 TEXT,key3 TEXT,data TEXT)");
            inject(service,"users",new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}});
            inject(service,"articles",new ArticleRepository(){public String getName(){return "x_article";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}});
            inject(service,"logs",new LogsRepository(){public String getName(){return "x_logs";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}});
            var dto=new ApiV3ContentDtoService();inject(dto,"userRepository",new UserRepository(){public String getName(){return "x_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}});inject(dto,"avatarQueryService",new AvatarQueryService());inject(service,"dto",dto);
            for(int i=1;i<=25;i++)sql("INSERT INTO x_user VALUES (?,0,?,?,?,'PRIVATE_PASSWORD','PRIVATE_PHONE')",String.format("%03d",i),"User"+i,"Nickname "+i,"https://example.test/"+i+".png");
        }
        static void inject(Object target, String field, Object value) throws Exception {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    var f = type.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            throw new IllegalStateException(field);
        }

        void sql(String q, Object... a) throws SQLException {
            try (var s = db.prepareStatement(q)) {
                for (int i = 0; i < a.length; i++) s.setObject(i + 1, a[i]);
                s.execute();
            }
        }

        List<JSONObject> query(String q, Object... a) throws RepositoryException {
            if (fail) throw new RepositoryException("fixture");
            // 先留存实际查询供断言，再将二进制比较语法适配到 SQLite。
            queries.add(q);
            q = q.replace("BINARY `type`", "`type` COLLATE BINARY").replace("BINARY key3", "key3 COLLATE BINARY");
            try (var s = db.prepareStatement(q)) {
                for (int i = 0; i < a.length; i++) s.setObject(i + 1, a[i]);
                try (var rs = s.executeQuery()) {
                    var out = new ArrayList<JSONObject>();
                    var m = rs.getMetaData();
                    while (rs.next()) {
                        var row = new JSONObject();
                        for (int i = 1; i <= m.getColumnCount(); i++) row.put(m.getColumnLabel(i), rs.getObject(i));
                        out.add(row);
                    }
                    return out;
                }
            } catch (SQLException e) {
                throw new RepositoryException(e);
            }
        }
        void article(String id,double random)throws SQLException{sql("INSERT INTO x_article VALUES (?,'001',0,0,0,1,'tag',0,?,?,100,200,?,?,'PRIVATE_REWARD','PRIVATE_IP',0,0,0)",id,"Article "+id,"/article/"+id,"<b>public preview</b>",random);}
        void log(String id,int pub,String type,String category,String time)throws SQLException{sql("INSERT INTO x_logs VALUES (?,?,?,?, 'PRIVATE_IP',?,'PRIVATE_LOG_BODY')",id,pub,type,time,category);}
        public void close()throws SQLException{db.close();}
    }
    static JSONArray list(JSONObject p){return p.getJSONArray("list");}
    static Set<String> ids(JSONObject p){var result=new HashSet<String>();for(Object value:list(p))result.add(((JSONObject)value).getString("id"));return result;}
    static void status(int n,Runnable r){try{r.run();throw new AssertionError("expected "+n);}catch(ApiV3Exception e){Assert.assertEquals(e.status,n);}}
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){f.sql("UPDATE x_user SET userStatus=2 WHERE oId='025'");var out=f.service.recent();Assert.assertEquals(list(out).length(),20);Assert.assertEquals(list(out).getJSONObject(0).getString("id"),"024");Assert.assertEquals(list(out).getJSONObject(19).getString("id"),"005");for(Object value:list(out))Assert.assertEquals(((JSONObject)value).keySet(),Set.of("id","userName","userNickname","userAvatarURL"));Assert.assertFalse(out.toString().contains("PRIVATE"));}
        System.out.println("PASS extras 1 newest twenty currently activated users, complete ordered rows and four-field public DTO only");
        try(var f=new Fixture()){f.sql("UPDATE x_user SET userStatus=3");Assert.assertEquals(list(f.service.recent()).length(),0);f.fail=true;status(503,()->f.service.recent());}
        System.out.println("PASS extras 2 restricted-login/pending/stopped users excluded from recent registration; SQL failure differs from valid empty list");
        try(var f=new Fixture()){f.article("101",.7);f.article("102",.5);f.article("103",.1);f.article("104",.9);var out=f.service.random(null,4,"");Assert.assertEquals(ids(out),Set.of("101","102","103","104"));Assert.assertEquals(list(out).length(),4);Assert.assertEquals(list(out).getJSONObject(0).getString("id"),"102");Assert.assertEquals(list(out).getJSONObject(3).getString("id"),"103");Assert.assertEquals(f.queries.stream().filter(q->q.startsWith("SELECT a.oId,")).count(),2L);Assert.assertFalse(out.toString().contains("PRIVATE"));}
        System.out.println("PASS extras 3 random pivot boundary included once, wrap fills remainder, no duplicates and actual common article projection omits raw private fields");
        for(String assignment:List.of("articleStatus=1","articleType="+org.b3log.symphony.model.Article.ARTICLE_TYPE_C_DISCUSSION,"articleShowInList=0","articleTags='Sandbox'","articleTags='B3log-private'","articleRandomDouble=-1","articleRandomDouble=2")){
            try(var f=new Fixture()){f.article("101",.7);f.article("102",.9);f.sql("UPDATE x_article SET "+assignment+" WHERE oId='101'");Assert.assertEquals(ids(f.service.random(null,20,"")),Set.of("102"));}
        }
        System.out.println("PASS extras 4 unavailable/discussion/hidden/sandbox/sync and out-of-range random candidates excluded before limits");
        try(var f=new Fixture()){f.article("101",.7);f.sql("UPDATE x_article SET articleAnonymousView=1");Assert.assertEquals(list(f.service.random(null,8,"")).length(),0);Assert.assertEquals(list(f.service.random(new JSONObject(),8,"")).length(),1);for(int state:List.of(1,2,4,99)){f.sql("UPDATE x_user SET userStatus=? WHERE oId='001'",state);Assert.assertEquals(list(f.service.random(new JSONObject(),8,"")).length(),0);}}
        System.out.println("PASS extras 5 visitor restriction and current author state hold for both random segments");
        try(var f=new Fixture()){f.article("101",.7);f.article("102",.8);f.sql("UPDATE x_article SET articleAnonymous=1 WHERE oId='101'");var out=f.service.random(null,8,"102");Assert.assertEquals(ids(out),Set.of("101"));var author=list(out).getJSONObject(0).getJSONObject("author");Assert.assertEquals(author.getString("id"),"");Assert.assertEquals(author.getString("userName"),"匿名用户");Assert.assertFalse(out.toString().contains("User1"));status(422,()->f.service.random(null,0,""));status(422,()->f.service.random(null,21,""));status(422,()->f.service.random(null,8,"1' OR 1=1"));}
        System.out.println("PASS extras 6 explicit exclude ID is filtered at SQL, anonymous identity remains hidden and input bounds reject injection");
        try(var f=new Fixture()){f.article("101",.7);f.fail=true;status(503,()->f.service.random(null,8,""));}
        System.out.println("PASS extras 7 discovery storage failure never becomes a fake recommendation-empty success");
        try(var f=new Fixture()){for(int i=1;i<=22;i++)f.log(String.valueOf(100+i),1,"simple","增加积分","2026-10-28 01:02:03");f.log("999",0,"simple","增加积分","2026-10-28 01:02:03");f.log("998",1,"censor-log","增加积分","2026-10-28 01:02:03");f.log("997",1,"simple","UNKNOWN_PRIVATE_CATEGORY","2026-10-28 01:02:03");f.log("996",1,"simple","封禁ip","2026-10-28 01:02:03");var out=f.service.logs(2,20);Assert.assertEquals(out.getJSONObject("pagination").getInt("total"),22);Assert.assertEquals(list(out).length(),2);Assert.assertEquals(list(out).getJSONObject(0).getString("id"),"102");Assert.assertEquals(list(out).getJSONObject(0).keySet(),Set.of("id","time","category"));Assert.assertFalse(out.toString().contains("PRIVATE"));Assert.assertTrue(f.queries.stream().filter(q->q.contains("x_logs")).noneMatch(q->q.contains("key2")||q.contains(",data")||q.startsWith("SELECT *")));}
        System.out.println("PASS extras 8 public simple exact-category filtering precedes log count/page; query and response never select log body, address or unknown category");
        try(var f=new Fixture()){f.log("101",1,"simple","增加积分","2026-02-30 01:02:03");status(503,()->f.service.logs(1,20));f.fail=true;status(503,()->f.service.logs(1,20));status(422,()->f.service.logs(0,20));status(422,()->f.service.logs(1,51));}
        System.out.println("PASS extras 9 malformed legacy time and SQL failures fail closed without echoing raw log data");
        try(var f=new Fixture()){Assert.assertEquals(list(f.service.logs(1,20)).length(),0);Assert.assertEquals(list(f.service.random(null,20,"")).length(),0);}
        System.out.println("PASS extras 10 legitimate empty records differ from read failures, without mock output");
    }
}
