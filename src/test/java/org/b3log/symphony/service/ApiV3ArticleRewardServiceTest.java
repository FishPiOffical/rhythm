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
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.service.ArticleRewardMgmtService.Kind.*;

/** 使用 SQLite 执行实际文章打赏汇总、业务操作和回执，运行环境及可见性由夹具适配。 */
public class ApiV3ArticleRewardServiceTest {
    public static void main(String[] args) throws Exception {
        final var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        for (String name : List.of("latkeProps", "localProps")) {
            var field = Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, props);
        }
        try (var f = new Rewards()) {
            final var viewer = f.viewer("outsider");
            Assert.assertFalse(f.rewardApi.read(viewer, "10", THANK).getBoolean("thanked"));
            Assert.assertFalse(f.rewardApi.read(viewer, "10", REWARD).getBoolean("rewarded"));
            Assert.assertEquals(f.rewardApi.read(viewer,"10",THANK).getInt("thankPoint"),20);
            Assert.assertTrue(f.rewardApi.read(f.viewer("older"),"10",REWARD).getBoolean("rewarded"));
            // 感谢和购买可以使用同一键，各自生成回执并按业务类型记账。
            var thank = f.rewardApi.apply(viewer, "10", THANK, "same-key");
            var paid = f.rewardApi.apply(viewer, "10", REWARD, "same-key");
            Assert.assertEquals(thank.keySet(), Set.of("id", "thanked"));
            Assert.assertEquals(paid.keySet(), Set.of("id", "rewarded"));
            Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='outsider'"), 50L);
            Assert.assertEquals(f.scalar("SELECT userPoint FROM user WHERE oId='older'"), 950L);
            Assert.assertEquals(f.scalar("SELECT articleThankCnt FROM article WHERE oId='10'"), 1L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM reward"), 2L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger l JOIN reward r ON r.oId=l.dataId JOIN notification n ON n.dataId=r.oId"),2L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger l JOIN reward r ON r.oId=l.dataId JOIN notification n ON n.dataId=r.oId WHERE l.recipient='older' AND n.userId='older' AND ((r.type="+Reward.TYPE_C_THANK_ARTICLE+" AND l.transferType="+Pointtransfer.TRANSFER_TYPE_C_ARTICLE_THANK+" AND n.dataType="+Notification.DATA_TYPE_C_POINT_ARTICLE_THANK+") OR (r.type="+Reward.TYPE_C_ARTICLE+" AND l.transferType="+Pointtransfer.TRANSFER_TYPE_C_ARTICLE_REWARD+" AND n.dataType="+Notification.DATA_TYPE_C_POINT_ARTICLE_REWARD+"))"),2L);
            Assert.assertEquals(f.scalar("SELECT amount FROM ledger WHERE transferType="+Pointtransfer.TRANSFER_TYPE_C_ARTICLE_THANK),20L);
            Assert.assertEquals(f.scalar("SELECT amount FROM ledger WHERE transferType="+Pointtransfer.TRANSFER_TYPE_C_ARTICLE_REWARD),30L);
            Assert.assertEquals(f.activityFields, List.of(Liveness.LIVENESS_REWARD, Liveness.LIVENESS_REWARD));
            Assert.assertEquals(f.indexEvents, 2);
            Assert.assertEquals(f.refreshed, List.of("older", "older"));
            for (var kind : List.of(THANK, REWARD)) {
                f.rewardApi.apply(viewer, "10", kind, "same-key");
                f.rewardApi.apply(viewer, "10", kind, "new-key");
            }
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"), 2L);
            Assert.assertEquals(f.activityEvents, 2);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM requests WHERE responseJson LIKE '%SECRET%'"), 0L);
            Assert.assertTrue(f.rewardApi.read(viewer, "10", THANK).getBoolean("thanked"));
            Assert.assertTrue(f.rewardApi.read(viewer, "10", REWARD).getBoolean("rewarded"));
            f.exec("UPDATE article SET articleRewardPoint=70 WHERE oId='10'");
            f.rewardApi.apply(viewer, "10", REWARD, "changed-price");
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"), 2L);
            f.exec("UPDATE article SET articleType=1,articleContent='@nobody ' WHERE oId='10'");
            Assert.assertEquals(f.rewardApi.apply(viewer,"10",REWARD,"same-key").toString(),paid.toString());
            expect(404, () -> f.rewardApi.read(viewer, "10", REWARD));
            expect(404, () -> f.rewardApi.apply(viewer, "10", REWARD, "now-unreadable"));
        }
        System.out.println("PASS article thanks and purchase commit exact fees/counter/reward/notification once, preserve reward activity category, isolate kinds and never cache paid bodies in receipts");
        for (var kind : List.of(THANK, REWARD)) {
            for (String failure : List.of("reward.select", "reward.add", "notification.add", "debit-null", "debit-blank", "debit-failure")) {
                try (var f = new Rewards()) {
                    String before = f.snapshot();
                    f.fail = failure;
                    try {
                        f.rewardApi.apply(f.viewer("outsider"), "10", kind, "failed");
                        Assert.fail(failure);
                    } catch (Exception rejected) {
                    }
                    Assert.assertEquals(f.snapshot(), before, failure);
                    Assert.assertEquals(f.indexEvents, 0);
                    Assert.assertTrue(f.refreshed.isEmpty());
                    Assert.assertEquals(f.activityEvents, 0);
                    Assert.assertFalse(f.active());
                }
            }
            try (var f = new Rewards()) {
                String before = f.snapshot();
                f.failResults = true;
                try {
                    f.rewardApi.apply(f.viewer("outsider"), "10", kind, "failed-receipt");
                    Assert.fail();
                } catch (RepositoryException rejected) {
                }
                Assert.assertEquals(f.snapshot(), before);
                Assert.assertEquals(f.indexEvents, 0);
                f.failResults = false;
                f.rewardApi.apply(f.viewer("outsider"), "10", kind, "failed-receipt");
                Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"), 1L);
            }
        }
        try (var f = new Rewards()) {
            String before = f.snapshot();
            f.fail = "article.update";
            try {
                f.rewardApi.apply(f.viewer("outsider"), "10", THANK, "failed-count");
                Assert.fail();
            } catch (RepositoryException rejected) {
            }
            Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS lookup/transfer/counter/reward/notification/receipt failures roll all financial and related writes back, including partial debit and same-key retry");
        try (var f = new Rewards()) {
            f.exec("UPDATE article SET articleAnonymous=1 WHERE oId='10'");
            f.exec("UPDATE user SET userPoint=0 WHERE oId='outsider'");
            Assert.assertEquals(f.rewardApi.read(f.viewer("outsider"),"10",THANK).getInt("thankPoint"),0);
            for(var kind:List.of(THANK,REWARD))f.rewardApi.apply(f.viewer("outsider"),"10",kind,"anonymous");
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"), 0L);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM reward"), 2L);
            Assert.assertEquals(f.indexEvents, 0);
            Assert.assertEquals(f.activityEvents, 2);
        }
        for (var kind : List.of(THANK, REWARD)) {
            try (var f = new Rewards()) {
                f.exec("UPDATE user SET userPoint=0 WHERE oId='outsider'");
                String before = f.snapshot();
                expect(422, () -> f.rewardApi.apply(f.viewer("outsider"), "10", kind, "no-balance"));
                Assert.assertEquals(f.snapshot(), before);
                f.exec("UPDATE user SET userPoint=100 WHERE oId='outsider'");
                f.rewardApi.apply(f.viewer("outsider"), "10", kind, "no-balance");
                Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"), 1L);
            }
        }
        System.out.println("PASS anonymous articles retain no-transfer grants at zero balance; ordinary insufficient-balance requests cannot grant access and can retry after funding");
        try (var f = new Rewards()) {
            Assert.assertTrue(f.core("older",REWARD,(a,b)->{}).mayExposeRewardContent());
            Assert.assertFalse(f.core("older",THANK,(a,b)->{}).mayExposeRewardContent());
            expect(422, () -> f.rewardApi.apply(f.viewer("older"), "10", REWARD, "self"));
            expect(422, () -> f.rewardApi.apply(f.viewer("older"), "10", THANK, "self"));
            f.exec("UPDATE article SET articleRewardPoint=0 WHERE oId='10'");
            var disabled = f.core("outsider", REWARD, (a, b) -> {});
            Assert.assertEquals(disabled.state(), ArticleRewardMgmtService.State.NOT_ENABLED);
            Assert.assertFalse(disabled.mayExposeRewardContent());
            expect(422,()->f.rewardApi.apply(f.viewer("outsider"),"10",REWARD,"disabled"));
            f.exec("UPDATE article SET articleRewardPoint=30 WHERE oId='10'");
            f.exec("UPDATE user SET userStatus=1 WHERE oId='outsider'");
            var invalid = f.core("outsider", REWARD, (a, b) -> {});
            Assert.assertEquals(invalid.state(), ArticleRewardMgmtService.State.UNAVAILABLE);
            Assert.assertFalse(invalid.mayExposeRewardContent());
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM ledger"),0L);
        }
        System.out.println("PASS owner/no-op/disabled/unavailable outcomes explicitly distinguish safe legacy body exposure from purchases; v3 rejects self and disabled operations");
        try (var f = new Rewards()) {
            var stale = f.viewer("outsider");
            f.exec("UPDATE user SET userRole='noThank' WHERE oId='outsider'");
            String before = f.snapshot();
            expect(403, () -> f.rewardApi.apply(stale, "10", THANK, "revoked-thank"));
            Assert.assertEquals(f.snapshot(), before);
            Assert.assertFalse(f.rewardApi.read(f.viewer("outsider"), "10", THANK).getBoolean("thanked"));
            f.rewardApi.apply(f.viewer("outsider"),"10",REWARD,"purchase-not-post-permission");
        }
        for (var kind : List.of(THANK, REWARD)) {
            try (var f = new Rewards()) {
                f.exec("UPDATE article SET articleType=1,articleContent='@nobody ' WHERE oId='10'");
                String before = f.snapshot();
                expect(404, () -> f.rewardApi.apply(f.viewer("outsider"), "10", kind, "not-invited"));
                Assert.assertEquals(f.snapshot(), before);
                f.exec("UPDATE article SET articleContent='@outsider ',articleStatus=2 WHERE oId='10'");
                f.rewardApi.apply(f.viewer("outsider"), "10", kind, "locked-invited");
            }
        }
        System.out.println("PASS fresh thank permission, current discussion visibility and locked-but-readable article rules run before charging (role/visibility adapters)");
        for (String sql : List.of("UPDATE article SET articleStatus=1 WHERE oId='10'",
                "UPDATE user SET userStatus=1 WHERE oId='older'", "DELETE FROM article WHERE oId='10'")) {
            try (var f = new Rewards()) {
                f.exec(sql);
                String before = f.snapshot();
                for (var kind : List.of(THANK, REWARD)) {
                    expect(404, () -> f.rewardApi.read(f.viewer("outsider"), "10", kind));
                    expect(404, () -> f.rewardApi.apply(f.viewer("outsider"), "10", kind, "bad-target"));
                }
                Assert.assertEquals(f.snapshot(), before);
            }
        }
        for (String sql : List.of("UPDATE article SET articleAnonymous=7 WHERE oId='10'",
                "UPDATE article SET articleThankCnt=2147483647 WHERE oId='10'")) {
            try (var f = new Rewards()) {
                f.exec(sql);
                String before = f.snapshot();
                try {
                    f.rewardApi.apply(f.viewer("outsider"), "10", THANK, "corrupt");
                    Assert.fail();
                } catch (IllegalStateException | ArithmeticException rejected) {
                }
                Assert.assertEquals(f.snapshot(), before);
            }
        }
        try (var f = new Rewards()) {
            expect(401, () -> f.rewardApi.read(null, "10", THANK));
            expect(401, () -> f.rewardApi.apply(null, "10", REWARD, "no-identity"));
            String before = f.snapshot();
            try {
                f.mutations.applyInCurrentTransaction("outsider", "10", "older", THANK, (a, b) -> {});
                Assert.fail();
            } catch (IllegalStateException rejected) {
            }
            expect(403, () -> f.core("outsider", REWARD, (a, b) -> {
                throw new ApiV3Exception(403, "forbidden", "denied");
            }));
            Assert.assertEquals(f.snapshot(), before);
        }
        System.out.println("PASS missing identity/targets, invalid state, corrupt anonymity/overflow and transaction/explicit-read guards cannot create a grant or partial charge");
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

    static class Rewards extends ApiV3ArticleVoteServiceTest.Articles {
        final ArticleRewardMgmtService mutations = new ArticleRewardMgmtService();
        final ApiV3ArticleRewardService rewardApi = new ApiV3ArticleRewardService();

        Rewards() throws Exception {
            exec("ALTER TABLE article ADD COLUMN articleThankCnt INT DEFAULT 0");
            exec("ALTER TABLE article ADD COLUMN articleRewardPoint INT DEFAULT 30");
            exec("ALTER TABLE article ADD COLUMN articleRewardContent TEXT DEFAULT 'SECRET-paid-body'");
            exec("ALTER TABLE article ADD COLUMN articleAnonymous INT DEFAULT 0");
            for (Object bean : List.of(new ArticleRepository(), new UserRepository(), new OptionRepository(),
                    new RewardRepository(), new PointtransferMgmtService(), new NotificationMgmtService(), new LivenessMgmtService())) {
                String field = switch (bean.getClass().getSimpleName()) {
                    case "ArticleRepository" -> "articles";
                    case "UserRepository" -> "users";
                    case "OptionRepository" -> "options";
                    case "RewardRepository" -> "rewards";
                    case "PointtransferMgmtService" -> "points";
                    case "NotificationMgmtService" -> "notifications";
                    default -> "liveness";
                };
                var target = ArticleRewardMgmtService.class.getDeclaredField(field);
                target.setAccessible(true);
                target.set(mutations, bean);
            }
            final var journal = new ApiV3IdempotencyMgmtService();
            var store = ApiV3IdempotencyMgmtService.class.getDeclaredField("requestRepository");
            store.setAccessible(true);
            store.set(journal, requestStore());
            for (Object bean : List.of(new ArticleRepository(), new UserRepository(), new RewardRepository(), mutations,
                    new ArticleMgmtService(), journal, new ApiV3ContentQueryService(), new RoleQueryService())) {
                String field = switch (bean.getClass().getSimpleName()) {
                    case "ArticleRepository" -> "articles";
                    case "UserRepository" -> "users";
                    case "RewardRepository" -> "rewards";
                    case "ArticleRewardMgmtService" -> "mutations";
                    case "ArticleMgmtService" -> "articleWrites";
                    case "ApiV3IdempotencyMgmtService" -> "idempotency";
                    case "ApiV3ContentQueryService" -> "visibility";
                    default -> "roles";
                };
                var target = ApiV3ArticleRewardService.class.getDeclaredField(field);
                target.setAccessible(true);
                target.set(rewardApi, bean);
            }
        }

        ArticleRewardMgmtService.Result core(String actor, ArticleRewardMgmtService.Kind kind,
                ArticleRewardMgmtService.TargetCheck check) throws Exception {
            return new ArticleMgmtService().withPublicationLocks(actor, List.of(Pointtransfer.ID_C_SYS, "older"), () -> {
                var tx = new ArticleRepository().beginTransaction();
                try {
                    var result = mutations.applyInCurrentTransaction(actor, "10", "older", kind, check);
                    tx.commit();
                    return result;
                } finally {
                    if (tx.isActive()) tx.rollback();
                }
            }, ignored -> {});
        }
    }
}
