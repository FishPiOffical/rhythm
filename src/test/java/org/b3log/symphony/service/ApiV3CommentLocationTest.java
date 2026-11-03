package org.b3log.symphony.service;

import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.util.ApiV3CommentLocationQueries;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import java.sql.Connection;
import java.util.List;

/** Actual service/current authorization/SQL over SQLite or disposable MySQL; formatter alias fixture only. */
public final class ApiV3CommentLocationTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    private static void sql(Connection db,String sql)throws Exception{try(var p=db.createStatement()){p.execute(sql);}db.commit();}
    private static void inject(Object target,String field,Object value)throws Exception{var f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);}
    @FunctionalInterface interface Action{void run()throws Exception;}
    private static void denied(Action action,int status)throws Exception{try{action.run();throw new AssertionError("expected rejection");}catch(ApiV3Exception e){check(e.status==status);}}
    public static void run(Connection db,ApiV3ContentQueryService content,CommentRepository comments)throws Exception{
        for(String field:List.of("commentParagraphId","commentParagraphKind"))sql(db,"ALTER TABLE comments ADD COLUMN "+field+" VARCHAR(64)");
        for(String field:List.of("commentParagraphStatus","commentParagraphIndex"))sql(db,"ALTER TABLE comments ADD COLUMN "+field+" INTEGER DEFAULT 0");
        sql(db,"INSERT INTO articles(oId,articleAuthorId,articleStatus,articleType,articleAnonymousView,articleContent,articleTitle,articleAnonymous,articleRewardPoint,articleRewardContent) VALUES('11','1',0,0,0,'PUBLIC','Location',0,0,''),('12','1',0,6,0,'LONG','Long',0,0,'')");
        sql(db,"UPDATE users SET userStatus=0,userName='reader' WHERE oId='2'");sql(db,"UPDATE users SET userStatus=0 WHERE oId='1'");
        var service=new ApiV3ParagraphQueryService();inject(service,"content",content);inject(service,"comments",comments);inject(service,"paragraphs",new LongArticleParagraphService());
        for(int n=1;n<=45;n++)sql(db,"INSERT INTO comments(oId,commentAuthorId,commentOnArticleId,commentStatus,commentVisible,commentAnonymous,commentType,commentContent) VALUES('"+(1000+n)+"','3','11',0,0,0,0,'BODY')");
        var row=service.location("1021",null);check(row.getInt("page")==2&&row.getInt("size")==20&&row.isNull("paragraph"));check(row.length()==6);
        check(service.location("1020",null).getInt("page")==1);check(service.location("1041",null).getInt("page")==3);
        sql(db,"UPDATE comments SET commentVisible=1,commentAnonymous=1,commentContent='PRIVATE_CONTENT' WHERE oId='1021'");
        row=service.location("1021",null);check(row.getInt("page")==2&&!row.toString().contains("PRIVATE_CONTENT")&&!row.toString().contains("author"));
        sql(db,"DELETE FROM comments WHERE oId='1001'");check(service.location("1021",null).getInt("page")==1);
        System.out.println("PASS comment location 1 actual ordinary ID-order page boundaries, metadata-only private/anonymous targets and current deletion recalculate without scanning pages");
        sql(db,"UPDATE comments SET commentStatus=1 WHERE oId='1021'");denied(()->service.location("1021",null),404);sql(db,"UPDATE comments SET commentStatus=0 WHERE oId='1021'");
        sql(db,"UPDATE articles SET articleStatus=1 WHERE oId='11'");denied(()->service.location("1021",null),404);sql(db,"UPDATE articles SET articleStatus=0,articleAnonymousView=1 WHERE oId='11'");
        denied(()->service.location("1021",null),404);var viewer=new JSONObject().put("oId","2");check(service.location("1021",viewer).getInt("page")==1);
        sql(db,"UPDATE articles SET articleAnonymousView=0,articleType=1,articleContent='@reader' WHERE oId='11'");
        check(service.location("1021",viewer).getInt("page")==1);sql(db,"UPDATE articles SET articleContent='INVITATION_REVOKED' WHERE oId='11'");denied(()->service.location("1021",viewer),404);
        sql(db,"UPDATE articles SET articleType=0 WHERE oId='11'");denied(()->service.location("missing",null),404);
        System.out.println("PASS comment location 2 current article invalidity, anonymous restriction, discussion invitation withdrawal and missing comment fail closed despite previous successful reads");
        String legacy="a".repeat(64),canonical="c".repeat(64),other="d".repeat(64);
        LongArticleParagraphService.groups.put(legacy,List.of(legacy,canonical));LongArticleParagraphService.groups.put(canonical,List.of(legacy,canonical));
        for(int n=1;n<=21;n++)sql(db,"INSERT INTO comments(oId,commentAuthorId,commentOnArticleId,commentStatus,commentVisible,commentAnonymous,commentType,commentContent,commentParagraphId,commentParagraphStatus) VALUES('"+(2000+n)+"','3','12',0,0,0,1,'PARAGRAPH','"+(n%2==0?canonical:legacy)+"',0)");
        for(int n=1;n<=25;n++)sql(db,"INSERT INTO comments(oId,commentAuthorId,commentOnArticleId,commentStatus,commentVisible,commentAnonymous,commentType,commentContent,commentParagraphId,commentParagraphStatus) VALUES('"+(1900+n)+"','3','12',0,0,0,1,'OTHER','"+other+"',0)");
        row=service.location("2021",null);check(row.getInt("page")==2);check(row.getJSONObject("paragraph").getString("id").equals(canonical));check(row.getJSONObject("paragraph").getString("state").equals("active"));
        sql(db,"UPDATE comments SET commentParagraphStatus=1 WHERE oId='2021'");row=service.location("2021",null);check(row.getInt("page")==1&&row.getJSONObject("paragraph").getString("state").equals("orphaned"));check(row.getJSONObject("paragraph").getString("id").equals(legacy));
        System.out.println("PASS comment location 3 current paragraph alias groups share oldest rank; other paragraphs and active/orphaned states never contaminate page count");
        sql(db,"UPDATE comments SET commentParagraphId='invalid' WHERE oId='2021'");denied(()->service.location("2021",null),404);
        sql(db,"UPDATE comments SET commentParagraphId='"+legacy+"',commentParagraphStatus=9 WHERE oId='2021'");denied(()->service.location("2021",null),404);
        sql(db,"UPDATE comments SET commentParagraphStatus=0 WHERE oId='2021'");sql(db,"UPDATE articles SET articleType=0 WHERE oId='12'");denied(()->service.location("2021",null),404);
        denied(()->ApiV3CommentLocationQueries.page(new JSONObject().put("position",1).put("targetPresent",0)),404);
        denied(()->ApiV3CommentLocationQueries.page(new JSONObject().put("position",Long.MAX_VALUE).put("targetPresent",1)),503);
        System.out.println("PASS comment location 4 malformed/stale paragraph state, changed article kind, vanished rank target and oversized page never fabricate a location");
    }
}
