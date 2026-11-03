package org.b3log.symphony.service;

import java.sql.*;
import org.b3log.latke.Latkes;
import java.util.*;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.*;
import org.testng.Assert;

/** Actual service and query text execute through real JDBC; no application connection or legacy query delegation. */
public class ApiV3PublicColumnQueryTest {
    static class Fixture implements AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3PublicColumnQueryService service = new ApiV3PublicColumnQueryService();
        boolean fail;
        Fixture() throws Exception {
            sql("CREATE TABLE x_user(oId TEXT PRIMARY KEY,userStatus INT)");
            sql("CREATE TABLE x_column(oId TEXT PRIMARY KEY,columnTitle TEXT,columnCoverURL TEXT,columnAuthorId TEXT,columnStatus INT,columnArticleCount INT,columnUpdateTime INT)");
            sql("CREATE TABLE x_article(oId TEXT PRIMARY KEY,articleAuthorId TEXT,articleStatus INT,articleType INT,articleAnonymous INT,articleShowInList INT,articleTags TEXT,articleAnonymousView INT,articleTitle TEXT,articlePermalink TEXT,articleCreateTime INT,articleUpdateTime INT,articleContent TEXT)");
            sql("CREATE TABLE x_chapter(oId TEXT PRIMARY KEY,columnId TEXT,articleId TEXT UNIQUE,chapterNo INT,chapterCreateTime INT)");
            inject("columns", new LongArticleColumnRepository(){public String getName(){return "x_column";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}});
            inject("chapters", new LongArticleChapterRepository(){public String getName(){return "x_chapter";}});
            inject("articles", new ArticleRepository(){public String getName(){return "x_article";}});
            inject("users", new UserRepository(){public String getName(){return "x_user";}});
            sql("INSERT INTO x_user VALUES ('u',0),('blocked',1),('stopped',4),('login-only',3)");
            column("1","u"); column("2","u"); column("3","blocked"); column("4","u");
            article("101","1","u",1); article("102","1","u",2); article("201","2","u",1); article("301","3","blocked",1); article("401","4","u",1);
            sql("UPDATE x_article SET articleShowInList=0 WHERE oId='401'");
        }
        void inject(String field,Object value)throws Exception{var f=ApiV3PublicColumnQueryService.class.getDeclaredField(field);f.setAccessible(true);f.set(service,value);}
        void sql(String q,Object...a)throws SQLException{try(var s=db.prepareStatement(q)){for(int i=0;i<a.length;i++)s.setObject(i+1,a[i]);s.execute();}}
        List<JSONObject> query(String q,Object...a)throws RepositoryException {if(fail)throw new RepositoryException("fixture");try(var s=db.prepareStatement(q)){for(int i=0;i<a.length;i++)s.setObject(i+1,a[i]);try(var rs=s.executeQuery()){List<JSONObject> result=new ArrayList<>();var m=rs.getMetaData();while(rs.next()){var row=new JSONObject();for(int i=1;i<=m.getColumnCount();i++)row.put(m.getColumnLabel(i),rs.getObject(i));result.add(row);}return result;}}catch(SQLException e){throw new RepositoryException(e);}}
        void column(String id,String owner)throws SQLException{sql("INSERT INTO x_column VALUES (?,?,'',?,0,999,999999)",id,"Column "+id,owner);}
        void article(String id,String col,String owner,int no)throws SQLException{sql("INSERT INTO x_article VALUES (?,?,0,6,0,1,'tag',0,?,?,100,?,?)",id,owner,"Chapter "+id,"/article/"+id,Integer.parseInt(id),"<b>visible</b><script>not HTML</script>");sql("INSERT INTO x_chapter VALUES (?,?,?,?,100)",id,col,id,no);}
        public void close()throws SQLException{db.close();}
    }
    static JSONArray list(JSONObject p){return p.getJSONArray("list");}
    static void status(int expected,Runnable r){try{r.run();throw new AssertionError("expected "+expected);}catch(ApiV3Exception e){Assert.assertEquals(e.status,expected);}}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            var page=f.service.list(null,1,1,true);Assert.assertEquals(page.getJSONObject("pagination").getLong("total"),2L);Assert.assertEquals(list(page).getJSONObject(0).getString("id"),"1");Assert.assertEquals(list(page).getJSONObject(0).getLong("articleCount"),2L);Assert.assertEquals(list(page).getJSONObject(0).getLong("updatedAt"),102L);Assert.assertEquals(f.service.detail(null,"1").getJSONArray("chapters").getJSONObject(0).getString("id"),"101");
            System.out.println("PASS public column 1 current visible counts, hot order and ascending detail replace stale stored totals/time");
        }
        for(String predicate:List.of("articleAnonymous=1","articleShowInList=0","articleStatus=1","articleType=0","articleTags='Sandbox'","articleTags='B3log-private'")){
            try(var f=new Fixture()){f.sql("UPDATE x_article SET "+predicate+" WHERE oId='102'");var detail=f.service.detail(null,"1");Assert.assertEquals(detail.getJSONArray("chapters").length(),1);var card=detail.getJSONObject("column");Assert.assertEquals(card.getLong("articleCount"),1L);Assert.assertEquals(card.getLong("updatedAt"),101L);Assert.assertFalse(detail.toString().contains("Chapter 102"));}
        }
        System.out.println("PASS public column 2 anonymous, hidden, invalid, non-long, sandbox and external-sync chapters excluded before every projection/count");
        try(var f=new Fixture()){f.sql("UPDATE x_article SET articleAnonymousView=1 WHERE oId='201'");Assert.assertEquals(f.service.list(null,1,20,false).getJSONObject("pagination").getLong("total"),1L);status(404,()->f.service.detail(null,"2"));status(404,()->f.service.chapters(null,"2",1,20));Assert.assertEquals(f.service.list(new JSONObject(),1,20,false).getJSONObject("pagination").getLong("total"),2L);Assert.assertEquals(list(f.service.chapters(new JSONObject(),"2",1,20)).length(),1);}
        System.out.println("PASS public column 3 current anonymous-view restriction applies to board, existence, detail and paginated chapters");
        try(var f=new Fixture()){f.sql("UPDATE x_user SET userStatus=4 WHERE oId='u'");Assert.assertEquals(list(f.service.list(null,1,20,false)).length(),0);status(404,()->f.service.detail(null,"1"));}
        try(var f=new Fixture()){f.sql("UPDATE x_article SET articleAuthorId='login-only' WHERE oId='102'");Assert.assertEquals(f.service.detail(null,"1").getJSONArray("chapters").length(),1);f.sql("UPDATE x_column SET columnStatus=1 WHERE oId='1'");status(404,()->f.service.detail(null,"1"));}
        for(int state:List.of(1,2,4,99)){try(var f=new Fixture()){f.sql("UPDATE x_user SET userStatus=? WHERE oId='u'",state);Assert.assertEquals(list(f.service.list(null,1,20,false)).length(),0);}}
        try(var f=new Fixture()){f.sql("UPDATE x_user SET userStatus=3 WHERE oId='u'");Assert.assertEquals(list(f.service.list(null,1,20,false)).length(),2);f.sql("UPDATE x_article SET articleStatus=2 WHERE oId='101'");Assert.assertEquals(f.service.detail(null,"1").getJSONArray("chapters").length(),2);}
        System.out.println("PASS public column 4 inactive authors, mismatched ownership and invalid/empty columns cannot reveal chapter associations");
        try(var f=new Fixture()){for(int i=3;i<=22;i++)f.article(""+(100+i),"1","u",i);var page=f.service.chapters(null,"1",2,20);Assert.assertEquals(page.getJSONObject("pagination").getLong("total"),22L);Assert.assertEquals(list(page).length(),2);Assert.assertEquals(list(page).getJSONObject(0).getInt("chapterNo"),21);Assert.assertEquals(list(f.service.chapters(null,"1",Integer.MAX_VALUE,50)).length(),0);var card=f.service.detail(null,"1").getJSONObject("column");Assert.assertEquals(card.getJSONArray("chapters").getJSONObject(0).getInt("chapterNo"),22);}
        System.out.println("PASS public column 5 genuine chapter pagination, two visible latest previews and long-safe extreme offsets");
        try(var f=new Fixture()){var text=f.service.detail(null,"1").toString();for(String secret:List.of("columnAuthorId","articleContent","userStatus","articleAnonymousView","999999"))Assert.assertFalse(text.contains(secret));Assert.assertFalse(f.service.detail(null,"1").getJSONObject("column").getJSONArray("chapters").getJSONObject(0).getString("preview").contains("<script>"));status(422,()->f.service.detail(null,"x' OR 1=1"));status(422,()->f.service.chapters(null,"1",0,20));}
        System.out.println("PASS public column 6 whitelist, plain preview sanitization, bound target and page validation");
        try(var f=new Fixture()){f.fail=true;status(503,()->f.service.list(null,1,20,false));status(503,()->f.service.detail(null,"1"));status(503,()->f.service.chapters(null,"1",1,20));}
        System.out.println("PASS public column 7 SQL failures never become fake empty boards or missing columns");
        try(var f=new Fixture()){var latest=f.service.list(null,1,1,false);Assert.assertEquals(list(latest).getJSONObject(0).getString("id"),"2");f.sql("UPDATE x_article SET articleShowInList=0 WHERE oId='201'");var after=f.service.list(null,1,1,false);Assert.assertEquals(after.getJSONObject("pagination").getLong("total"),1L);Assert.assertEquals(list(after).getJSONObject(0).getString("id"),"1");Assert.assertEquals(list(f.service.list(null,2,1,false)).length(),0);}
        System.out.println("PASS public column 8 eligibility precedes board page/count so hidden-only columns leave no holes or raw-count signals");
        try(var f=new Fixture()){
            var context=f.service.articleContext(null,"101");Assert.assertEquals(context.getString("articleId"),"101");Assert.assertEquals(context.getInt("chapterNo"),1);Assert.assertTrue(context.isNull("previous"));Assert.assertEquals(context.getJSONObject("next").getString("id"),"102");Assert.assertEquals(context.getJSONObject("column").getInt("articleCount"),2);Assert.assertFalse(context.toString().contains("articleContent"));Assert.assertFalse(context.getJSONObject("column").has("chapters"));
            Assert.assertEquals(f.service.articleContext(null,"102").getJSONObject("previous").getString("id"),"101");
            f.sql("UPDATE x_article SET articleShowInList=0 WHERE oId='101'");Assert.assertTrue(f.service.articleContext(null,"101").isNull("column"));context=f.service.articleContext(null,"102");Assert.assertTrue(context.isNull("previous"));Assert.assertEquals(context.getJSONObject("column").getInt("articleCount"),1);
        }
        System.out.println("PASS public column 10 bounded current article context and neighbors use the visible chapter order/count");
        try(var f=new Fixture()){
            f.sql("DELETE FROM x_chapter WHERE articleId='101'");Assert.assertTrue(f.service.articleContext(null,"101").isNull("column"));f.sql("UPDATE x_article SET articleType=0 WHERE oId='102'");Assert.assertTrue(f.service.articleContext(null,"102").isNull("column"));status(404,()->f.service.articleContext(null,"999"));status(422,()->f.service.articleContext(null,"1' OR 1=1"));f.sql("UPDATE x_user SET userStatus=4 WHERE oId='u'");status(404,()->f.service.articleContext(null,"101"));
        }
        System.out.println("PASS public column 11 unassigned/non-long return null, missing/inactive authors 404, malformed article ID 422");
        try(var f=new Fixture()){
            f.sql("UPDATE x_article SET articleAnonymousView=1 WHERE oId='101'");status(404,()->f.service.articleContext(null,"101"));Assert.assertFalse(f.service.articleContext(new JSONObject(),"101").isNull("column"));
            f.sql("UPDATE x_article SET articleType=1,articleContent='private @invited ' WHERE oId='101'");status(404,()->f.service.articleContext(new JSONObject().put("userName","other"),"101"));Assert.assertTrue(f.service.articleContext(new JSONObject().put("userName","invited"),"101").isNull("column"));Assert.assertTrue(f.service.articleContext(new JSONObject().put("oId","u"),"101").isNull("column"));
        }
        System.out.println("PASS public column 12 anonymous-view and discussion permissions checked before disclosing even absent associations");
        try(var f=new Fixture()){
            for(int i=3;i<100;i++)f.article(""+(100+i),"1","u",i);var context=f.service.articleContext(null,"150");Assert.assertEquals(context.getJSONObject("previous").getString("id"),"149");Assert.assertEquals(context.getJSONObject("next").getString("id"),"151");Assert.assertTrue(context.toString().length()<2000);f.fail=true;status(503,()->f.service.articleContext(null,"101"));
        }
        System.out.println("PASS public column 13 context size independent of full directory and SQL failure is 503");

    }
}
