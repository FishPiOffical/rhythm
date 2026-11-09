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
import org.b3log.symphony.util.ApiV3Votes;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;

/** 验证实际的 v3 评论投票、变更逻辑、幂等和 SQL。运行环境和可见性查询由适配器提供。 */
public class ApiV3CommentVoteServiceTest {
    public static void main(String[] args)throws Exception {
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");
        for(String name:List.of("latkeProps","localProps")){var field=Latkes.class.getDeclaredField(name);field.setAccessible(true);field.set(null,props);}
        Assert.assertEquals(ApiV3Votes.choice(new JSONObject().put("vote","up")),Vote.TYPE_C_UP);
        Assert.assertEquals(ApiV3Votes.choice(new JSONObject().put("vote","down")),Vote.TYPE_C_DOWN);
        for(Object value:List.of("","UP"," up","down ","invalid",true,0,JSONObject.NULL,new JSONObject())){
            expect(422,()->ApiV3Votes.choice(new JSONObject().put("vote",value)));
        }
        expect(422,()->ApiV3Votes.choice(new JSONObject()));
        expect(422,()->ApiV3Votes.choice(new JSONObject().put("vote","up").put("authorId","1")));
        try {
            ApiV3Votes.commentReadSql("comment;DROP TABLE user", "vote");
            Assert.fail();
        } catch (IllegalArgumentException expected) {
        }
        System.out.println("PASS typed vote input accepts only exact up/down, rejects null/extra/coerced values, and validates internal table identifiers");
        try(var f=new V3Votes()){
            f.exec("INSERT INTO vote VALUES('article-vote','30',0,'older',0)");
            Assert.assertTrue(f.api.read(f.viewer("older"),"30").isNull("vote"));
            f.api.set(f.viewer("older"),"30",Vote.TYPE_C_UP,"typed-comment");
            var other = f.api.read(f.viewer("outsider"), "30");
            Assert.assertTrue(other.isNull("vote"));
            Assert.assertEquals(other.getInt("upCount"), 1);
            expect(404,()->f.api.read(f.viewer("older"),"30 OR 1=1"));
            f.api.set(f.viewer("older"),"30",-1,"typed-clear");
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM vote"), 1L);
            Assert.assertEquals(f.text("SELECT oId FROM vote"), "article-vote");
        }
        System.out.println("PASS actor and resource-type SQL binding separates other readers and article votes with the same ID; clear preserves unrelated votes");
        try(var f=new V3Votes()){
            final var viewer = f.viewer("older");
            var empty = f.api.read(viewer, "30");
            Assert.assertEquals(empty.keySet(), Set.of("id", "vote", "upCount", "downCount"));
            Assert.assertTrue(empty.has("vote"));
            Assert.assertTrue(empty.isNull("vote"));
            var first = f.api.set(viewer, "30", Vote.TYPE_C_UP, "up-first");
            Assert.assertEquals(first.getString("vote"), "up");
            Assert.assertEquals(first.getInt("upCount"), 1);
            Assert.assertEquals(f.api.read(viewer,"30").toString(),first.toString());
            Assert.assertEquals(f.api.set(viewer,"30",Vote.TYPE_C_UP,"up-first").toString(),first.toString());
            Assert.assertEquals(f.api.set(viewer,"30",Vote.TYPE_C_UP,"up-new-key").toString(),first.toString());
            Assert.assertEquals(f.activityEvents, 1);
            Assert.assertEquals(f.refreshed, List.of("owner"));
            String before = f.snapshot();
            expect(409, () -> f.api.set(viewer, "30", Vote.TYPE_C_DOWN, "up-first"));
            Assert.assertEquals(f.snapshot(), before);
            expect(409, () -> f.api.set(viewer, "30", -1, "up-first"));
            Assert.assertEquals(f.snapshot(), before);
            var down = f.api.set(viewer, "30", Vote.TYPE_C_DOWN, "down-new");
            Assert.assertEquals(down.getInt("upCount"), 0);
            Assert.assertEquals(down.getInt("downCount"), 1);
            Assert.assertEquals(f.api.set(viewer,"30",Vote.TYPE_C_UP,"up-first").toString(),first.toString());
            Assert.assertEquals(f.api.read(viewer, "30").toString(), down.toString());
            Assert.assertEquals(f.activityEvents, 2);
            var cleared = f.api.set(viewer, "30", -1, "clear-first");
            Assert.assertTrue(cleared.isNull("vote"));
            Assert.assertEquals(cleared.getInt("downCount"), 0);
            f.api.set(viewer, "30", -1, "clear-first");
            f.api.set(viewer, "30", -1, "clear-new");
            Assert.assertEquals(f.activityEvents, 2);
            Assert.assertFalse(cleared.toString().contains("SECRET"));
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM vote"), 0L);
            f.transaction(() -> f.remove("owner", "30"));
            Assert.assertEquals(f.api.set(viewer, "30", Vote.TYPE_C_UP, "up-first").toString(), first.toString());
            expect(404, () -> f.api.read(viewer, "30"));
            expect(404, () -> f.api.set(viewer, "30", Vote.TYPE_C_UP, "missing-new"));
        }
        System.out.println("PASS full v3 service reads projected state, sets rather than toggles, rejects key conflicts, preserves newer state on old replay and replays after deletion without writes/events");
        for(String failure:List.of("vote.select","vote.add","comment.update","notification.add")){
            try(var f=new V3Votes()){
                String before = f.snapshot();
                f.fail = failure;
                try {
                    f.api.set(f.viewer("older"), "30", Vote.TYPE_C_UP, "failed-write");
                    Assert.fail();
                } catch (RepositoryException rejected) {
                }
                Assert.assertEquals(f.snapshot(), before);
                Assert.assertEquals(f.activityEvents, 0);
                Assert.assertTrue(f.refreshed.isEmpty());
            }
        }
        try(var f=new V3Votes()){
            String before = f.snapshot();
            f.failResults = true;
            try {
                f.api.set(f.viewer("older"), "30", Vote.TYPE_C_UP, "failed-result");
                Assert.fail();
            } catch (RepositoryException rejected) {
            }
            Assert.assertEquals(f.snapshot(), before);
            Assert.assertEquals(f.activityEvents, 0);
            Assert.assertTrue(f.refreshed.isEmpty());
            f.failResults = false;
            f.api.set(f.viewer("older"), "30", Vote.TYPE_C_UP, "failed-result");
            Assert.assertEquals(f.activityEvents, 1);
        }
        System.out.println("PASS actual aggregation and idempotency roll vote/counters/notification/result back on repository or receipt persistence failure and allow one later retry");
        try(var f=new V3Votes()){
            f.exec("UPDATE comment SET commentVisible=1 WHERE oId='30'");
            expect(404, () -> f.api.read(f.viewer("outsider"), "30"));
            expect(404, () -> f.api.set(f.viewer("outsider"), "30", Vote.TYPE_C_UP, "private-denied"));
            Assert.assertTrue(f.api.read(f.viewer("owner"), "30").isNull("vote"));
            f.api.set(f.viewer("older"), "30", Vote.TYPE_C_UP, "article-author-private");
            String before = f.snapshot();
            expect(422, () -> f.api.set(f.viewer("owner"), "30", Vote.TYPE_C_UP, "self-denied"));
            Assert.assertEquals(f.snapshot(), before);
        }
        try(var f=new V3Votes()){
            f.exec("UPDATE article SET articleType=1,articleContent='@nobody ' WHERE oId='10'");
            expect(404, () -> f.api.read(f.viewer("outsider"), "30"));
            expect(404, () -> f.api.set(f.viewer("outsider"), "30", Vote.TYPE_C_UP, "discussion-denied"));
            f.exec("UPDATE article SET articleContent='@outsider ' WHERE oId='10'");
            f.api.set(f.viewer("outsider"), "30", Vote.TYPE_C_DOWN, "discussion-invited");
            Assert.assertEquals(f.api.read(f.viewer("outsider"),"30").getString("vote"),"down");
        }
        System.out.println("PASS current private and discussion visibility protects reads and mutations, article author may read private content, ordinary self-vote fails (visibility adapter)");
        for(String change:List.of("UPDATE comment SET commentStatus=1 WHERE oId='30'","UPDATE user SET userStatus=1 WHERE oId='owner'","DELETE FROM comment WHERE oId='30'")){
            try(var f=new V3Votes()){
                f.exec(change);
                String before = f.snapshot();
                expect(404, () -> f.api.read(f.viewer("older"), "30"));
                expect(404, () -> f.api.set(f.viewer("older"), "30", Vote.TYPE_C_UP, "invalid-target"));
                Assert.assertEquals(f.snapshot(),before);
            }
        }
        try(var f=new V3Votes()){
            expect(401, () -> f.api.read(null, "30"));
            expect(401, () -> f.api.set(null, "30", Vote.TYPE_C_UP, "no-identity"));
            f.exec("UPDATE user SET userRole='adminRole' WHERE oId='owner'");
            f.api.set(f.viewer("owner"), "30", Vote.TYPE_C_UP, "admin-self");
            Assert.assertTrue(f.refreshed.isEmpty());
            f.exec("INSERT INTO vote VALUES('duplicate','30',1,'owner',0)");
            try {
                f.api.read(f.viewer("owner"), "30");
                Assert.fail();
            } catch (IllegalStateException rejected) {
            }
            f.exec("UPDATE comment SET commentVisible=1 WHERE oId='30'");
            expect(404,()->f.api.read(f.viewer("outsider"),"30"));
        }
        System.out.println("PASS absent identity, invalid/missing targets and corrupt duplicates cannot leak a public DTO; admin self-vote exception remains");
    }
    @FunctionalInterface interface Checked {Object run()throws Exception;}
    static void expect(int status,Checked operation)throws Exception{try{operation.run();Assert.fail("expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    static class V3Votes extends CommentNotificationServiceTest.Notices {
        final ApiV3CommentVoteService api=new ApiV3CommentVoteService();
        V3Votes()throws Exception {
            final var journal=new ApiV3IdempotencyMgmtService();var store=ApiV3IdempotencyMgmtService.class.getDeclaredField("requestRepository");store.setAccessible(true);store.set(journal,requestStore());
            for(Object bean:List.of(new CommentRepository(),new VoteRepository(),new UserRepository(),thanks,voting,new ArticleMgmtService(),journal,new ApiV3ContentQueryService())){
                String field=switch(bean.getClass().getSimpleName()){case "CommentRepository"->"comments";case "VoteRepository"->"votes";case "UserRepository"->"users";case "CommentThankMgmtService"->"targets";case "CommentVoteMgmtService"->"mutations";case "ArticleMgmtService"->"articleWrites";case "ApiV3IdempotencyMgmtService"->"idempotency";default->"visibility";};
                var target=ApiV3CommentVoteService.class.getDeclaredField(field);target.setAccessible(true);target.set(api,bean);
            }
        }
        JSONObject viewer(String id)throws Exception{return new UserRepository().get(id);}
    }
}
