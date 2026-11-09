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
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Votes;
import org.json.JSONObject;
import org.testng.Assert;

import java.util.*;

/**
 * 用共用的 SQLite 适配器验证实际的文章票数聚合、投票逻辑、SQL 和回执。HTTP、MySQL 和 IoC 不在本用例覆盖范围内。
 */
public class ApiV3ArticleVoteServiceTest {
    public static void main(String[] args) throws Exception {
        final var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        for (String name : List.of("latkeProps", "localProps")) {
            var field = Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, props);
        }
        try (var f = new Articles()) {
            var viewer = f.viewer("outsider");
            var empty = f.articleApi.read(viewer, "10");
            Assert.assertEquals(empty.keySet(), Set.of("id", "vote", "upCount", "downCount"));
            Assert.assertTrue(empty.isNull("vote"));
            var first = f.articleApi.set(viewer, "10", Vote.TYPE_C_UP, "first-up");
            Assert.assertEquals(first.getInt("upCount"), 1);
            Assert.assertTrue(f.articleApi.read(viewer, "10").similar(first.getJSONObject("interaction")));
            f.articleApi.set(viewer, "10", Vote.TYPE_C_UP, "first-up");
            f.articleApi.set(viewer, "10", Vote.TYPE_C_UP, "new-up");
            Assert.assertEquals(f.activityEvents, 1);
            Assert.assertEquals(f.refreshed, List.of("older"));
            Assert.assertEquals(f.text("SELECT articleContent FROM article WHERE oId='10'"), "SECRET-article");
            Assert.assertEquals(f.scalar("SELECT articleCommentCount FROM article WHERE oId='10'"), 2L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM tag_article WHERE articleCommentCount=2 AND articleLatestCmtTime=30"), 2L);
            Assert.assertEquals(Double.parseDouble(f.text("SELECT redditScore FROM article WHERE oId='10'")), -30083.0);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM tag_article WHERE redditScore=-30083"), 2L);
            String before = f.snapshot();
            expect(409, () -> f.articleApi.set(viewer, "10", Vote.TYPE_C_DOWN, "first-up"));
            expect(409, () -> f.articleApi.set(viewer, "10", -1, "first-up"));
            Assert.assertEquals(f.snapshot(), before);
            var down = f.articleApi.set(viewer, "10", Vote.TYPE_C_DOWN, "down");
            Assert.assertEquals(down.getInt("upCount"), 0);
            Assert.assertEquals(down.getInt("downCount"), 1);
            // 原键回放历史回执，随后读到的仍是后来设置的反对票。
            var replay = f.articleApi.set(viewer, "10", Vote.TYPE_C_UP, "first-up");
            Assert.assertTrue(replay.getJSONObject("interaction").similar(down.getJSONObject("interaction")));
            replay.remove("interaction");
            Assert.assertTrue(replay.similar(first.getJSONObject("interaction")));
            Assert.assertTrue(f.articleApi.read(viewer, "10").similar(down.getJSONObject("interaction")));
            var clear = f.articleApi.set(viewer, "10", -1, "clear");
            Assert.assertTrue(clear.isNull("vote"));
            f.articleApi.set(viewer, "10", -1, "clear-new");
            Assert.assertEquals(f.activityEvents, 2);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM vote"), 0L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"), 0L);
            Assert.assertFalse(first.toString().contains("SECRET"));
            f.exec("DELETE FROM article WHERE oId='10'");
            var deletedReplay = f.articleApi.set(viewer, "10", Vote.TYPE_C_UP, "first-up");
            Assert.assertTrue(deletedReplay.has("interaction") && deletedReplay.isNull("interaction"));
            deletedReplay.remove("interaction");
            Assert.assertTrue(deletedReplay.similar(first.getJSONObject("interaction")));
            expect(404, () -> f.articleApi.read(viewer, "10"));
            expect(404, () -> f.articleApi.set(viewer, "10", Vote.TYPE_C_UP, "missing-new"));
        }
        System.out.println("PASS real article aggregation sets/clears/replays atomically without toggle, preserves later state and non-vote fields, synchronizes tag scores and does not replay events");
        for (String failure : List.of("vote.select", "vote.add", "article.update", "tag_article.select", "tag_article.update", "tag_article.second", "notification.add")) {
            try (var f = new Articles()) {
                String before = f.snapshot();
                f.fail = failure;
                try {
                    f.articleApi.set(f.viewer("outsider"), "10", Vote.TYPE_C_UP, "failed");
                    Assert.fail(failure);
                } catch (RepositoryException rejected) {
                }
                Assert.assertEquals(f.snapshot(), before, failure);
                Assert.assertEquals(f.activityEvents, 0);
                Assert.assertTrue(f.refreshed.isEmpty());
                Assert.assertFalse(f.active());
            }
        }
        try (var f = new Articles()) {
            String before = f.snapshot();
            f.failResults = true;
            try {
                f.articleApi.set(f.viewer("outsider"), "10", Vote.TYPE_C_UP, "failed-receipt");
                Assert.fail();
            } catch (RepositoryException rejected) {
            }
            Assert.assertEquals(f.snapshot(), before);
            Assert.assertEquals(f.activityEvents, 0);
            f.failResults = false;
            f.articleApi.set(f.viewer("outsider"), "10", Vote.TYPE_C_UP, "failed-receipt");
            Assert.assertEquals(f.activityEvents, 1);
            before = f.snapshot();
            f.fail = "vote.remove";
            try {
                f.articleApi.set(f.viewer("outsider"), "10", Vote.TYPE_C_DOWN, "failed-remove");
                Assert.fail();
            } catch (RepositoryException rejected) {
            }
            Assert.assertEquals(f.snapshot(), before);
        }
        System.out.println("PASS vote/article/association/notification/receipt faults roll every write back, including a failure after the first association was updated");
        try (var f = new Articles()) {
            f.exec("INSERT INTO comment(oId,commentAuthorId,commentOnArticleId,commentOriginalCommentId,commentContent) VALUES('10','owner','10','','same ID')");
            var viewer = f.viewer("outsider");
            f.api.set(viewer, "10", Vote.TYPE_C_UP, "shared-key");
            Assert.assertTrue(f.articleApi.read(viewer, "10").isNull("vote"));
            f.articleApi.set(viewer, "10", Vote.TYPE_C_UP, "shared-key");
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM vote"), 2L);
            Assert.assertTrue(f.articleApi.read(f.viewer("owner"), "10").isNull("vote"));
            f.articleApi.set(viewer, "10", -1, "clear-article");
            Assert.assertEquals(f.api.read(viewer, "10").getString("vote"), "up");
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM vote"), 1L);
            expect(404, () -> f.articleApi.read(viewer, "10 OR 1=1"));
            try {
                ApiV3Votes.articleReadSql("article;DROP TABLE user", "vote");
                Assert.fail();
            } catch (IllegalArgumentException rejected) {
            }
        }
        System.out.println("PASS article/comment resource and receipt isolation with identical IDs/keys, reader binding and SQL parameter/table guards preserve unrelated votes");
        try (var f = new Articles()) {
            var up = f.articleMutations.toggle("outsider", "10", Vote.TYPE_C_UP, true);
            Assert.assertEquals(up.previous(), -1);
            Assert.assertEquals(up.current(), Vote.TYPE_C_UP);
            var cancel = f.articleMutations.toggle("outsider", "10", Vote.TYPE_C_UP, true);
            Assert.assertEquals(cancel.previous(), Vote.TYPE_C_UP);
            Assert.assertEquals(cancel.current(), -1);
            f.articleMutations.toggle("outsider", "10", Vote.TYPE_C_DOWN, true);
            f.articleMutations.toggle("outsider", "10", Vote.TYPE_C_UP, true);
            Assert.assertEquals(f.scalar("SELECT articleGoodCnt FROM article WHERE oId='10'"), 1L);
            Assert.assertEquals(f.scalar("SELECT articleBadCnt FROM article WHERE oId='10'"), 0L);
            Assert.assertEquals(f.activityEvents, 3);
            Assert.assertEquals(f.refreshed, List.of("older"));
            try {
                f.articleMutations.voteUp("outsider", "10", Vote.DATA_TYPE_C_COMMENT);
                Assert.fail();
            } catch (IllegalArgumentException rejected) {
            }
        }
        try (var f = new Articles()) {
            f.exec("INSERT INTO article(oId,articleAuthorId,articleCommentCount,articleGoodCnt,articleBadCnt) VALUES('1353790195000','older',0,2,0)");
            f.articleMutations.toggle("outsider", "1353790195000", Vote.TYPE_C_UP, false);
            Assert.assertEquals(Double.parseDouble(f.text("SELECT redditScore FROM article WHERE oId='1353790195000'")), 0.477121254719662, 1e-12);
        }
        System.out.println("PASS legacy toggle and previous-type behavior, no down notification, admin-independent article-only wrappers and original integral ranking time component");
        try (var f = new Articles()) {
            expect(422, () -> f.articleApi.set(f.viewer("older"), "10", Vote.TYPE_C_UP, "self"));
            expect(401, () -> f.articleApi.read(null, "10"));
            f.exec("UPDATE article SET articleType=1,articleContent='@nobody ' WHERE oId='10'");
            expect(404, () -> f.articleApi.read(f.viewer("outsider"), "10"));
            expect(404, () -> f.articleApi.set(f.viewer("outsider"), "10", Vote.TYPE_C_UP, "not-invited"));
            f.exec("UPDATE article SET articleContent='@outsider ' WHERE oId='10'");
            f.articleApi.set(f.viewer("outsider"), "10", Vote.TYPE_C_UP, "invited");
            f.exec("UPDATE user SET userRole='adminRole' WHERE oId='older'");
            f.articleApi.set(f.viewer("older"), "10", Vote.TYPE_C_DOWN, "admin-self");
        }
        for (String sql : List.of("UPDATE article SET articleStatus=1 WHERE oId='10'", "UPDATE user SET userStatus=1 WHERE oId='older'", "DELETE FROM article WHERE oId='10'")) {
            try (var f = new Articles()) {
                f.exec(sql);
                String before = f.snapshot();
                expect(404, () -> f.articleApi.read(f.viewer("outsider"), "10"));
                expect(404, () -> f.articleApi.set(f.viewer("outsider"), "10", Vote.TYPE_C_UP, "invalid-target"));
                Assert.assertEquals(f.snapshot(), before);
            }
        }
        System.out.println("PASS explicit identity, ordinary self-vote rejection, admin exception and current discussion/invalid-article/invalid-author visibility (visibility adapter)");
        try (var f = new Articles()) {
            f.exec("UPDATE article SET articleStatus=2 WHERE oId='10'");
            Assert.assertTrue(f.articleApi.read(f.viewer("outsider"), "10").isNull("vote"));
            Assert.assertEquals(f.articleApi.set(f.viewer("outsider"), "10", Vote.TYPE_C_UP, "locked-readable").getString("vote"), "up");
        }
        for (String sql : List.of("INSERT INTO vote VALUES('d1','10',0,'outsider',0),('d2','10',0,'outsider',1)", "INSERT INTO vote VALUES('bad','10',0,'outsider',7)", "UPDATE article SET articleGoodCnt=-1 WHERE oId='10'", "UPDATE article SET articleGoodCnt=2147483647 WHERE oId='10'")) {
            try (var f = new Articles()) {
                f.exec(sql);
                String before = f.snapshot();
                try {
                    f.articleApi.set(f.viewer("outsider"), "10", Vote.TYPE_C_UP, "corrupt");
                    Assert.fail();
                } catch (IllegalStateException | ArithmeticException rejected) {
                }
                Assert.assertEquals(f.snapshot(), before);
                Assert.assertEquals(f.activityEvents, 0);
                Assert.assertTrue(f.refreshed.isEmpty());
            }
        }
        System.out.println("PASS duplicate/invalid article votes, negative counters and overflow fail without partial results or business writes");
        try (var f = new Articles()) {
            String before = f.snapshot();
            try {
                f.articleMutations.setInCurrentTransaction("outsider", "10", "older", Vote.TYPE_C_UP, false, true, (a, b) -> {
                });
                Assert.fail();
            } catch (IllegalStateException rejected) {
            }
            try {
                f.core("owner", (a, b) -> {
                });
                Assert.fail();
            } catch (VoteMgmtService.Rejected rejected) {
                Assert.assertEquals(rejected.code, "target_changed");
            }
            expect(403, () -> f.core("older", (a, b) -> {
                throw new ApiV3Exception(403, "forbidden", "denied");
            }));
            Assert.assertEquals(f.snapshot(), before);
            f.fail = "refresh-event";
            f.articleApi.set(f.viewer("outsider"), "10", Vote.TYPE_C_UP, "refresh-failure");
            Assert.assertEquals(f.scalar("SELECT articleGoodCnt FROM article WHERE oId='10'"), 1L);
            Assert.assertEquals(f.activityEvents, 1);
            Assert.assertFalse(f.active());
        }
        System.out.println("PASS explicit transaction/read authorization and author binding protect the core; post-commit refresh failure does not undo a committed article vote");
    }

    @FunctionalInterface
    interface Checked {
        Object run() throws Exception;
    }

    static void expect(int status, Checked operation) throws Exception {
        try {
            operation.run();
            Assert.fail("expected " + status);
        } catch (ApiV3Exception e) {
            Assert.assertEquals(e.status, status);
        }
    }

    static class Articles extends ApiV3CommentVoteServiceTest.V3Votes {
        final ApiV3ArticleVoteService articleApi = new ApiV3ArticleVoteService();
        final VoteMgmtService articleMutations = new VoteMgmtService();

        Articles() throws Exception {
            exec("ALTER TABLE article ADD COLUMN articleGoodCnt INT DEFAULT 0");
            exec("ALTER TABLE article ADD COLUMN articleBadCnt INT DEFAULT 0");
            exec("ALTER TABLE article ADD COLUMN redditScore DOUBLE DEFAULT 0");
            exec("ALTER TABLE tag_article ADD COLUMN redditScore DOUBLE DEFAULT 0");
            exec("UPDATE article SET articleContent='SECRET-article' WHERE oId='10'");
            for (Object bean : List.of(new VoteRepository(), new ArticleRepository(), new TagArticleRepository(), new UserRepository(), new OptionRepository(), new ArticleMgmtService(), new NotificationMgmtService(), new LivenessMgmtService())) {
                String field = switch (bean.getClass().getSimpleName()) {
                    case "VoteRepository" -> "voteRepository";
                    case "ArticleRepository" -> "articleRepository";
                    case "TagArticleRepository" -> "tagArticleRepository";
                    case "UserRepository" -> "users";
                    case "OptionRepository" -> "options";
                    case "ArticleMgmtService" -> "articleWrites";
                    case "NotificationMgmtService" -> "notifications";
                    default -> "livenessMgmtService";
                };
                var target = VoteMgmtService.class.getDeclaredField(field);
                target.setAccessible(true);
                target.set(articleMutations, bean);
            }
            final var journal = new ApiV3IdempotencyMgmtService();
            var store = ApiV3IdempotencyMgmtService.class.getDeclaredField("requestRepository");
            store.setAccessible(true);
            store.set(journal, requestStore());
            for (Object bean : List.of(new ArticleRepository(), new VoteRepository(), new UserRepository(), articleMutations, new ArticleMgmtService(), journal, new ApiV3ContentQueryService())) {
                String field = switch (bean.getClass().getSimpleName()) {
                    case "ArticleRepository" -> "articles";
                    case "VoteRepository" -> "votes";
                    case "UserRepository" -> "users";
                    case "VoteMgmtService" -> "mutations";
                    case "ArticleMgmtService" -> "articleWrites";
                    case "ApiV3IdempotencyMgmtService" -> "idempotency";
                    default -> "visibility";
                };
                var target = ApiV3ArticleVoteService.class.getDeclaredField(field);
                target.setAccessible(true);
                target.set(articleApi, bean);
            }
        }

        VoteMgmtService.Result core(String expectedAuthor, VoteMgmtService.TargetCheck check) throws Exception {
            return new ArticleMgmtService().withPublicationLock("outsider", () -> {
                var tx = new ArticleRepository().beginTransaction();
                try {
                    var result = articleMutations.setInCurrentTransaction("outsider", "10", expectedAuthor, Vote.TYPE_C_UP, false, true, check);
                    tx.commit();
                    return result;
                } finally {
                    if (tx.isActive()) tx.rollback();
                }
            }, ignored -> {
            });
        }
    }
}
