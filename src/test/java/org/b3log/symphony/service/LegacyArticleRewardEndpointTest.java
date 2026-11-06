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
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.*;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;

/** Exact extracted controller methods plus the actual payment core; HTTP, queries and HTML are adapters. */
public class LegacyArticleRewardEndpointTest {
    static ApiV3ArticleRewardServiceTest.Rewards fixture;
    static int renders;
    static Runnable afterPurchase=()->{};
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");
        for(String name:List.of("latkeProps","localProps")){var f=Latkes.class.getDeclaredField(name);f.setAccessible(true);f.set(null,props);}
        try(var f=new ApiV3ArticleRewardServiceTest.Rewards()){
            fixture=f;renders=0;
            for(int i=0;i<2;i++){var ctx=request(f.viewer("outsider"),"10");new LegacyArticleRewardEndpoint().rewardArticle(ctx);success(ctx);}
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),1L);Assert.assertEquals(renders,2);
            var owner=request(f.viewer("older"),"10");new LegacyArticleRewardEndpoint().rewardArticle(owner);success(owner);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),1L);
        }
        System.out.println("PASS legacy purchase/repeat/owner responses keep the HTML field and never charge twice");
        for(String sql:List.of("UPDATE user SET userStatus=1 WHERE oId='outsider'","UPDATE user SET userStatus=1 WHERE oId='older'","UPDATE article SET articleRewardPoint=0","UPDATE article SET articleType=1,articleContent='@nobody '","UPDATE article SET articleStatus=1","UPDATE user SET userPoint=0 WHERE oId='outsider'")){
            try(var f=new ApiV3ArticleRewardServiceTest.Rewards()){
                fixture=f;renders=0;var stale=f.viewer("outsider");f.exec(sql);String before=f.snapshot();
                var ctx=request(stale,"10");new LegacyArticleRewardEndpoint().rewardArticle(ctx);denied(ctx);Assert.assertEquals(renders,0);Assert.assertEquals(f.snapshot(),before);
            }
        }
        System.out.println("PASS legacy unavailable/disabled/uninvited/insufficient-balance requests expose no paid body and make no partial charge");
        try(var f=new ApiV3ArticleRewardServiceTest.Rewards()){
            fixture=f;renders=0;f.exec("UPDATE article SET articleType=1,articleContent='@outsider ',articleStatus=2");
            var ctx=request(f.viewer("outsider"),"10");new LegacyArticleRewardEndpoint().rewardArticle(ctx);success(ctx);Assert.assertEquals(renders,1);
        }
        System.out.println("PASS authenticated attribute reader can buy invited locked discussions without any session fallback");
        for(String sql:List.of("UPDATE article SET articleType=1,articleContent='@nobody '","UPDATE user SET userStatus=1 WHERE oId='outsider'","UPDATE article SET articleRewardPoint=0","DELETE FROM article","UPDATE article SET articleAuthorId='outsider'")){
            try(var f=new ApiV3ArticleRewardServiceTest.Rewards()){
                fixture=f;renders=0;afterPurchase=()->{try{f.exec(sql);}catch(Exception e){throw new RuntimeException(e);}};
                var ctx=request(f.viewer("outsider"),"10");new LegacyArticleRewardEndpoint().rewardArticle(ctx);denied(ctx);Assert.assertEquals(renders,0);
            }finally{afterPurchase=()->{};}
        }
        System.out.println("PASS response snapshot is reauthorized after payment before HTML rendering when access or target changes");
        try(var f=new ApiV3ArticleRewardServiceTest.Rewards()){
            fixture=f;renders=0;for(String id:List.of("", "10 OR 1=1", "1".repeat(33))){var ctx=request(f.viewer("outsider"),id);new LegacyArticleRewardEndpoint().rewardArticle(ctx);Assert.assertEquals(ctx.http,400);}
            var ctx=request(null,"10");new LegacyArticleRewardEndpoint().rewardArticle(ctx);Assert.assertEquals(ctx.http,403);Assert.assertEquals(renders,0);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),0L);
        }
        System.out.println("PASS missing authenticated attribute and malformed IDs stop before purchase/rendering");
    }
    static Adapters.RequestContext request(JSONObject user,String id){return new Adapters.RequestContext(user,id);}
    static void success(Adapters.RequestContext c){Assert.assertEquals(c.code,0);Assert.assertEquals(c.body.keySet(),Set.of("articleRewardContent"));Assert.assertEquals(c.body.getString("articleRewardContent"),"<p>SECRET-paid-body</p>");}
    static void denied(Adapters.RequestContext c){Assert.assertNotEquals(c.code,0);Assert.assertFalse(c.body.has("articleRewardContent"));}
    public static class Adapters {
        final Purchases articleMgmtService=new Purchases();final Users userQueryService=new Users();final Articles articleQueryService=new Articles();final Labels langPropsService=new Labels();
        static class StatusCodes{static final int ERR=-1,SUCC=0;}
        static class StringUtils{static String trimToEmpty(String s){return s==null?"":s.trim();}}
        static class RequestContext{
            final JSONObject user,body=new JSONObject();final String id;int code=-9,http=200;
            RequestContext(JSONObject u,String i){user=u;id=i;}Object attr(String k){Assert.assertEquals(k,User.USER);return user;}String param(String k){return id;}
            RequestContext renderJSON(int c){code=c;return this;}RequestContext renderJSONValue(String k,Object v){body.put(k,v);return this;}void renderMsg(String m){}void sendError(int s){http=s;}
        }
        static class Purchases{
            ArticleRewardMgmtService.Result rewardForViewer(String id,String actor,ArticleRewardMgmtService.TargetCheck check)throws ServiceException{
                Assert.assertEquals(id,"10");try{var result=fixture.core(actor,ArticleRewardMgmtService.Kind.REWARD,check);afterPurchase.run();return result;}catch(Exception e){throw new ServiceException(e);}
            }
        }
        static class Users{
            JSONObject getUser(String id){try{return CommentRemovalServiceTest.Rows.query("SELECT * FROM user WHERE oId=?",id).stream().findFirst().orElse(null);}catch(Exception e){throw new RuntimeException(e);}}
            Set<String> getUserNames(String content){var names=new HashSet<String>();var m=java.util.regex.Pattern.compile("@([A-Za-z0-9]+)").matcher(content);while(m.find())names.add(m.group(1));return names;}
        }
        static class Articles{
            JSONObject getArticle(String id){try{return CommentRemovalServiceTest.Rows.query("SELECT * FROM article WHERE oId=?",id).stream().findFirst().orElse(null);}catch(Exception e){throw new RuntimeException(e);}}
            void processArticleContentForViewer(JSONObject article,JSONObject reader,java.util.function.Consumer<String> playback){Assert.assertNotNull(reader);renders++;article.put(Article.ARTICLE_REWARD_CONTENT,"<p>"+article.getString(Article.ARTICLE_REWARD_CONTENT)+"</p>");}
        }
        static class Labels{String get(String key){return key;}}
    }
}
