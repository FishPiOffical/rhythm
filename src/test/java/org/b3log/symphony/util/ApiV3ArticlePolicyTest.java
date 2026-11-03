package org.b3log.symphony.util;

import org.b3log.symphony.model.Article;
import org.json.JSONObject;
import org.testng.Assert;

/** Exercises the actual policy normalizer and mutation guard, not HTTP or idempotency storage. */
public final class ApiV3ArticlePolicyTest {
    public static void main(final String[] args) {
        for (Object input : new Object[]{true, 1, 1L}) {
            var article=new JSONObject().put(Article.ARTICLE_COMMENTABLE,input);
            Assert.assertEquals(ApiV3ArticlePolicy.commentable(article),Boolean.TRUE);
            ApiV3ArticlePolicy.requireCommenting(article);
        }
        for (Object input : new Object[]{false,0,0L,2,-1,0.5,"true","1",JSONObject.NULL}) {
            var article=new JSONObject().put(Article.ARTICLE_COMMENTABLE,input);
            Assert.assertNotEquals(ApiV3ArticlePolicy.commentable(article),Boolean.TRUE);
            try {ApiV3ArticlePolicy.requireCommenting(article);Assert.fail("must reject");}
            catch(ApiV3Exception expected){ }
        }
        System.out.println("PASS article policy 1 exact Boolean/JDBC 0/1 only; closed and unknown mutation guards reject");
        for(int n=0;n<=3;n++)Assert.assertEquals(ApiV3ArticlePolicy.statement(new JSONObject().put(Article.ARTICLE_STATEMENT,n)),Integer.valueOf(n));
        for(Object value:new Object[]{-1,4,1.5,"1",true,JSONObject.NULL})Assert.assertNull(ApiV3ArticlePolicy.statement(new JSONObject().put(Article.ARTICLE_STATEMENT,value)));
        var unknown=ApiV3ArticlePolicy.metadata(new JSONObject());
        Assert.assertTrue(unknown.isNull("commentable")&&unknown.isNull("statement"));
        Assert.assertEquals(unknown.length(),2);
        for (Object raw : new Object[]{"x","2","true","false"," 1",JSONObject.NULL}) {
            var row=new JSONObject().put(ApiV3ArticlePolicy.RAW_COMMENTABLE,raw).put(Article.ARTICLE_COMMENTABLE,true);
            Assert.assertNull(ApiV3ArticlePolicy.commentable(row));
        }
        Assert.assertEquals(ApiV3ArticlePolicy.commentable(new JSONObject().put(ApiV3ArticlePolicy.RAW_COMMENTABLE,"1")),Boolean.TRUE);
        Assert.assertEquals(ApiV3ArticlePolicy.commentable(new JSONObject().put(ApiV3ArticlePolicy.RAW_COMMENTABLE,"0")),Boolean.FALSE);
        System.out.println("PASS article policy 2 exact statement enum and null unknown metadata without extra fields");
    }
}
