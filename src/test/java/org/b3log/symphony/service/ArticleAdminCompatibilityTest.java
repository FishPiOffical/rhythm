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
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.*;
import java.util.concurrent.Callable;

/** 运行真实文章领域逻辑；仓库与外部事件为内存适配器，不替代 SQL/浏览器验收。 */
public class ArticleAdminCompatibilityTest {
    static { final Properties p = new Properties(); p.setProperty("runtimeDatabase", "NONE");
        org.b3log.latke.Latkes.setLatkeProps(p); org.b3log.latke.Latkes.setLocalProps(p);
        org.b3log.latke.ioc.BeanManager.start(new ArrayList<>(List.of(org.b3log.symphony.cache.TagCache.class))); }
    static final class Fixture {
        boolean active;
        JSONObject article;
        JSONObject author = new JSONObject().put("oId", "1").put("userName", "author").put("userArticleCount", 0);
        JSONObject tag = new JSONObject().put("oId", "3").put("tagTitle", "测试").put("tagReferenceCount", 0).put("tagCommentCount", 0);
        int statistic; int relations; int sources; int postAdds; int postUpdates; int notices; int rewards; int covers; int migrations;
        final List<String> locks = new ArrayList<>();
        final ArticleMgmtService service = new ArticleMgmtService() {
            @Override public void afterAdminArticleAdded(final JSONObject value) { Assert.assertFalse(active); Assert.assertFalse(ArticlePublicationCaches.active()); postAdds++; }
            @Override public void afterAdminArticleUpdated(final JSONObject value) { Assert.assertFalse(active); Assert.assertFalse(ArticlePublicationCaches.active()); postUpdates++; super.afterAdminArticleUpdated(value); }
        };
        final ArticleRepository repo = new ArticleRepository() {
            @Override public boolean hasTransactionBegun() { return active; }
            @Override public Transaction beginTransaction() {
                active = true;
                return new Transaction() {
                    public String getId() { return "fixture"; } public boolean isActive() { return active; }
                    public void commit() { active = false; } public void rollback() { active = false; }
                };
            }
            @Override public JSONObject get(final String id) { return article == null ? null : new JSONObject(article.toString()); }
            @Override public JSONObject getForUpdate(final String id) { locks.add("article"); return get(id); }
            @Override public List<JSONObject> select(final String sql, final Object... args) { return List.of(new JSONObject().put("articleAuthorId", "1")); }
            @Override public String add(final JSONObject value) { Assert.assertTrue(active); article = new JSONObject(value.toString()); return value.getString("oId"); }
            @Override public void update(final String id, final JSONObject value, final String... fields) { Assert.assertTrue(active); article = new JSONObject(value.toString()); }
        };
        Fixture() throws Exception {
            inject("articleRepository", repo);
            inject("userRepository", new UserRepository() {
                @Override public JSONObject getByName(final String name) { return new JSONObject(author.toString()); }
                @Override public JSONObject getForUpdate(final String id) { locks.add("author"); return new JSONObject(author.toString()); }
                @Override public List<JSONObject> select(final String sql, final Object... args) { return List.of(new JSONObject().put("oId", "1")); }
                @Override public void update(final String id, final JSONObject value, final String... fields) { author = new JSONObject(value.toString()); }
            });
            inject("optionRepository", new OptionRepository() {
                @Override public JSONObject getForUpdate(final String id) { locks.add("statistic"); return get(id); }
                @Override public JSONObject get(final String id) { return new JSONObject().put("optionValue", statistic); }
                @Override public void update(final String id, final JSONObject value, final String... fields) { statistic = value.getInt("optionValue"); }
            });
            inject("tagRepository", new TagRepository() {
                @Override public JSONObject getByTitle(final String title) { return new JSONObject(tag.toString()); }
                @Override public List<JSONObject> getByArticleId(final String id) { return List.of(new JSONObject(tag.toString())); }
                @Override public void update(final String id, final JSONObject value, final String... fields) { tag = new JSONObject(value.toString()); }
            });
            inject("tagArticleRepository", new TagArticleRepository() {
                @Override public String add(final JSONObject value) { relations++; return "4"; }
                @Override public List<JSONObject> getByArticleId(final String id) { return List.of(); }
            });
            inject("userTagRepository", new UserTagRepository() {
                @Override public void removeByUserIdAndTagId(final String user, final String tag, final int type) { }
                @Override public String add(final JSONObject value) { return "5"; }
            });
            inject("longArticleColumnMgmtService", new LongArticleColumnMgmtService() {
                @Override public void syncChapterInCurrentTransaction(final JSONObject value, final JSONObject request) { Assert.assertTrue(active); }
            });
            inject("professionSourceEventCaptureService", new ProfessionSourceEventCaptureService() {
                @Override public void articlePublished(final JSONObject value) { Assert.assertTrue(active); sources++; }
            });
            inject("notificationMgmtService", new NotificationMgmtService() {
                @Override public void addPerfectArticleNotification(final JSONObject value) { Assert.assertFalse(active); notices++; }
            });
            inject("searchMgmtService", new SearchMgmtService() {
                @Override public void updateAlgoliaDocument(final JSONObject value) { Assert.assertFalse(active); }
                @Override public void updateESDocument(final JSONObject value, final String type) { Assert.assertFalse(active); }
            });
            inject("columnCoverMgmtService", new ColumnCoverMgmtService() {
                @Override public JSONObject updateOwnedCoverURLInCurrentTransaction(final String user, final String column, final String url) {
                    Assert.assertTrue(active); covers++; return new JSONObject();
                }
            });
            inject("longArticleParagraphService", new LongArticleParagraphService() {
                @Override public void migrateCommentsInCurrentTransaction(final String id, final String before, final String after) {
                    Assert.assertTrue(active); migrations++;
                }
            });
            inject("pointtransferMgmtService", new PointtransferMgmtService() {
                @Override public String transfer(final String from, final String to, final int type, final int sum,
                                                 final String data, final long time, final String memo) {
                    Assert.assertFalse(active); rewards++; return "6";
                }
                @Override public <T> T executeWithTransferLocks(final String actor, final List<String> destinations, final Callable<T> work) throws Exception { return work.call(); }
            });
        }
        void inject(final String name, final Object value) throws Exception {
            final var field = ArticleMgmtService.class.getDeclaredField(name); field.setAccessible(true); field.set(service, value);
        }
        JSONObject input() { return new JSONObject().put("userName", "author").put("time", 1234567890123L)
                .put("articleTitle", "标题").put("articleContent", "正文").put("articleTags", "测试"); }
    }
    @Test public void legacyAndV3CreateShareCountsLocksAndPostCommitBoundary() throws Exception {
        final Fixture legacy = new Fixture();
        final String oldId = legacy.service.addArticleByAdmin(legacy.input());
        Assert.assertEquals(oldId, "1234567890123"); Assert.assertEquals(legacy.postAdds, 1);
        final Fixture v3 = new Fixture();
        final JSONObject result = v3.service.withPublicationLock("1", () -> {
            final Transaction tx = v3.repo.beginTransaction();
            final JSONObject value = v3.service.addArticleByAdminInCurrentTransaction(v3.input());
            tx.commit(); return value;
        }, v3.service::afterAdminArticleAdded);
        Assert.assertNotEquals(result.getString("oId"), oldId);
        for (final Fixture f : List.of(legacy, v3)) {
            Assert.assertEquals(f.statistic, 1); Assert.assertEquals(f.author.getInt("userArticleCount"), 1);
            Assert.assertEquals(f.tag.getInt("tagReferenceCount"), 1); Assert.assertEquals(f.relations, 1);
            Assert.assertEquals(f.sources, 1); Assert.assertEquals(f.postAdds, 1);
            Assert.assertEquals(f.locks, List.of("statistic", "author"));
        }
    }
    @Test public void unchangedTagsDoNotGainReferencesAndStickRemainsPermanent() throws Exception {
        final Fixture f = new Fixture(); final String id = f.service.addArticleByAdmin(f.input());
        final JSONObject edit = new JSONObject(f.article.toString()).put("articleTitle", "修改标题");
        f.locks.clear(); f.service.updateArticleByAdmin(id, edit);
        Assert.assertEquals(f.article.getString("articleTitle"), "修改标题");
        Assert.assertEquals(f.tag.getInt("tagReferenceCount"), 1); Assert.assertEquals(f.relations, 1);
        Assert.assertEquals(f.locks, List.of("statistic", "author", "article")); Assert.assertEquals(f.postUpdates, 0);
        f.active = true;
        f.service.adminStick(id); Assert.assertEquals(f.article.getLong("articleStick"), Long.MAX_VALUE);
        f.service.adminCancelStick(id); Assert.assertEquals(f.article.getLong("articleStick"), 0L);
        f.service.setArticleStickInCurrentTransaction(id, "1", true);
        Assert.assertEquals(f.article.getLong("articleStick"), Long.MAX_VALUE); f.active = false;
    }
    @Test public void perfectTransitionAndLongChapterEffectsAreShared() throws Exception {
        Latkes.setRuntimeMode(Latkes.RuntimeMode.DEVELOPMENT);
        final Fixture f = new Fixture(); final String id = f.service.addArticleByAdmin(f.input());
        final JSONObject patch = new JSONObject(f.article.toString()).put("articlePerfect", Article.ARTICLE_PERFECT_C_PERFECT)
                .put("articleType", Article.ARTICLE_TYPE_C_LONG).put("articleContent", "新正文");
        final JSONObject column = new JSONObject().put("columnId", "7").put("columnCoverURL", "");
        f.service.updateArticleByAdmin(id, patch, column);
        Assert.assertEquals(f.notices, 1); Assert.assertEquals(f.rewards, 1);
        Assert.assertEquals(f.covers, 1); Assert.assertEquals(f.migrations, 1);
        f.service.withPublicationLock("1", () -> {
            final Transaction tx = f.repo.beginTransaction();
            final JSONObject outcome = f.service.updateArticleByAdminInCurrentTransaction(id,
                    new JSONObject().put("articleTitle", "再次编辑"), column);
            tx.commit(); return outcome;
        }, f.service::afterAdminArticleUpdated);
        Assert.assertEquals(f.notices, 1, "已优选文章再次编辑不得重复通知");
        Assert.assertEquals(f.rewards, 1, "已优选文章再次编辑不得重复奖励");
        Assert.assertEquals(f.covers, 2); Assert.assertEquals(f.migrations, 1);
        Assert.assertEquals(f.article.getInt("articleAnonymousView"), Article.ARTICLE_ANONYMOUS_VIEW_C_ALLOW);
    }

    public static void main(final String[] args) throws Exception {
        final var test = new ArticleAdminCompatibilityTest();
        test.legacyAndV3CreateShareCountsLocksAndPostCommitBoundary();
        test.unchangedTagsDoNotGainReferencesAndStickRemainsPermanent();
        test.perfectTransitionAndLongChapterEffectsAreShared();
        System.out.println("PASS: real legacy/V3 article core, legacy ID, counts, unchanged tag references, lock order, post-commit boundary, permanent stick");
    }
}
