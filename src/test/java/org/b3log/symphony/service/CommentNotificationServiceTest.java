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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;

/** Real delayed-notification service with SQLite repositories and an explicit visibility adapter. */
public class CommentNotificationServiceTest {
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");
        for(String name:List.of("latkeProps","localProps")){var field=Latkes.class.getDeclaredField(name);field.setAccessible(true);field.set(null,props);}
        final List<Integer> types=List.of(Notification.DATA_TYPE_C_COMMENT,Notification.DATA_TYPE_C_AT,Notification.DATA_TYPE_C_COMMENTED,Notification.DATA_TYPE_C_REPLY,Notification.DATA_TYPE_C_FOLLOWING_ARTICLE_COMMENT);
        try(var f=new Notices()){
            for(int type:types){Assert.assertTrue(f.notices.send("outsider","10","30",type));Assert.assertFalse(f.notices.send("outsider","10","30",type));}
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification"),5L);Assert.assertEquals(f.refreshed.size(),5);
            f.transaction(()->f.remove("owner","30"));Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification"),0L);
            Assert.assertFalse(f.notices.send("outsider","10","30",Notification.DATA_TYPE_C_AT));Assert.assertEquals(f.refreshed.size(),5);
        }
        System.out.println("PASS real delayed notification service inserts supported types once, refreshes after commit, and deletion cleans records without a later orphan insertion");
        try(var f=new Notices()){
            f.exec("UPDATE comment SET commentVisible=1 WHERE oId='30'");
            Assert.assertFalse(f.notices.send("outsider","10","30",Notification.DATA_TYPE_C_AT));
            Assert.assertTrue(f.notices.send("older","10","30",Notification.DATA_TYPE_C_AT));
            Assert.assertTrue(f.notices.send("owner","10","30",Notification.DATA_TYPE_C_REPLY));
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification"),2L);
        }
        try(var f=new Notices()){
            f.exec("UPDATE article SET articleType=1,articleContent='@Older ' WHERE oId='10'");
            Assert.assertFalse(f.notices.send("outsider","10","30",Notification.DATA_TYPE_C_AT));
            f.exec("UPDATE article SET articleContent='@outsider ' WHERE oId='10'");
            Assert.assertTrue(f.notices.send("outsider","10","30",Notification.DATA_TYPE_C_AT));
        }
        System.out.println("PASS current private-content and discussion visibility is checked at insertion rather than trusted from the old event snapshot (visibility adapter)");
        for(String sql:List.of("UPDATE comment SET commentStatus=1 WHERE oId='30'","UPDATE user SET userStatus=1 WHERE oId='outsider'","UPDATE user SET userStatus=1 WHERE oId='owner'","UPDATE user SET userStatus=1 WHERE oId='older'","DELETE FROM article WHERE oId='10'","UPDATE comment SET commentOnArticleId='other' WHERE oId='30'")){
            try(var f=new Notices()){
                f.exec(sql);String before=f.snapshot();Assert.assertFalse(f.notices.send("outsider","10","30",Notification.DATA_TYPE_C_AT));
                Assert.assertEquals(f.snapshot(),before);Assert.assertTrue(f.refreshed.isEmpty());
            }
        }
        System.out.println("PASS removed/invalid or moved targets and invalid recipients/authors cannot create delayed notification records");
        for(String failure:List.of("notification.select","notification.add","notification.afterAdd")){
            try(var f=new Notices()){
                String before=f.snapshot();f.fail=failure;
                try{f.notices.send("outsider","10","30",Notification.DATA_TYPE_C_AT);Assert.fail();}catch(RepositoryException rejected){}
                Assert.assertEquals(f.snapshot(),before);Assert.assertFalse(f.active());Assert.assertTrue(f.refreshed.isEmpty());
            }
        }
        try(var f=new Notices()){
            f.fail="refresh-event";Assert.assertTrue(f.notices.send("outsider","10","30",Notification.DATA_TYPE_C_AT));
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM notification"),1L);Assert.assertFalse(f.active());
        }
        System.out.println("PASS lookup and insertion faults roll back notifications, including failure after SQL insert; post-commit refresh failure preserves the committed row");
        try(var f=new Notices()){
            var expected=f.thanks.target("30");String before=f.snapshot();
            try{f.notices.sendInCurrentTransaction("outsider",expected,"older",Notification.DATA_TYPE_C_AT);Assert.fail();}catch(IllegalStateException rejected){}
            try{f.notices.send("outsider","10","30",Notification.DATA_TYPE_C_CUSTOM_SYS);Assert.fail();}catch(IllegalArgumentException rejected){}
            Assert.assertFalse(f.notices.send("outsider","wrong","30",Notification.DATA_TYPE_C_AT));Assert.assertFalse(f.notices.send("","10","30",Notification.DATA_TYPE_C_AT));
            Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS transaction/account guards, notification-type allowlist and explicit article binding reject before persistent effects");
    }
    static class Notices extends CommentVoteServiceTest.Votes {
        final CommentNotificationMgmtService notices=new CommentNotificationMgmtService();
        Notices()throws Exception {
            exec("DELETE FROM notification");exec("UPDATE comment SET commentThankCnt=0 WHERE oId='30'");
            exec("ALTER TABLE article ADD COLUMN articleStatus INT DEFAULT 0");exec("ALTER TABLE article ADD COLUMN articleType INT DEFAULT 0");
            exec("ALTER TABLE article ADD COLUMN articleAnonymousView INT DEFAULT 0");exec("ALTER TABLE article ADD COLUMN articleContent TEXT DEFAULT ''");
            exec("INSERT INTO user(oId,userName,userStatus,userPoint,userCommentCount,userRole) VALUES('outsider','outsider',0,100,0,'defaultRole')");
            for(Object bean:List.of(new CommentRepository(),new ArticleRepository(),new UserRepository(),new OptionRepository(),new ArticleMgmtService(),new ApiV3ContentQueryService(),new NotificationMgmtService(),thanks)){
                String field=switch(bean.getClass().getSimpleName()){case "CommentRepository"->"comments";case "ArticleRepository"->"articles";case "UserRepository"->"users";case "OptionRepository"->"options";case "ArticleMgmtService"->"articleWrites";case "ApiV3ContentQueryService"->"visibility";case "NotificationMgmtService"->"notifications";default->"targets";};
                var target=CommentNotificationMgmtService.class.getDeclaredField(field);target.setAccessible(true);target.set(notices,bean);
            }
        }
    }
    /** Uses the real pure visibility rules but substitutes mention extraction and the real query service. */
    public static class Visibility {
        public JSONObject viewableArticle(String id,JSONObject viewer)throws RepositoryException{
            final var article=new ArticleRepository().get(id);if(null==article)return null;
            final Set<String> invited=new HashSet<>();var matcher=java.util.regex.Pattern.compile("@([A-Za-z0-9_]+)").matcher(article.optString(Article.ARTICLE_CONTENT));while(matcher.find())invited.add(matcher.group(1));
            if(!ApiV3ContentUtil.canViewArticle(article,viewer,invited))return null;
            final var author=new UserRepository().get(article.optString(Article.ARTICLE_AUTHOR_ID));
            return null==author||author.optInt(UserExt.USER_STATUS)!=UserExt.USER_STATUS_C_VALID?null:article;
        }
    }
}
