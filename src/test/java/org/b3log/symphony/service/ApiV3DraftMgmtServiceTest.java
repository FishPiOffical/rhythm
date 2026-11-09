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

import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.ArticleDraft;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.ArticleDraftRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/** 在同一个事务内存存储上运行实际草稿与幂等服务，覆盖范围限于服务层。 */
public class ApiV3DraftMgmtServiceTest {
    @Test
    public void createReadUpdateAndDeleteReplayTheOriginalResult() throws Exception {
        final Fixture f = fixture();
        final JSONObject body = new JSONObject().put("title", "  标题  ").put("content", "编辑器正文").put("thoughtContent", "思绪记录");
        final JSONObject first = f.api.save("user-a", null, "create", body);
        final String id = first.getString("id");
        Assert.assertEquals(f.api.save("user-a", null, "create", body).getString("id"), id);
        Assert.assertEquals(f.drafts.size(), 1);
        Assert.assertEquals(f.drafts.get(id).getString(ArticleDraft.CONTENT), "编辑器正文");
        Assert.assertEquals(f.drafts.get(id).getString(ArticleDraft.THOUGHT_CONTENT), "思绪记录");
        final long created = f.drafts.get(id).getLong(ArticleDraft.CREATED_TIME);
        f.api.save("user-a", id, "update", new JSONObject().put("title", "新标题"));
        Assert.assertEquals(f.drafts.get(id).getString(ArticleDraft.TITLE), "新标题");
        Assert.assertEquals(f.drafts.get(id).getString(ArticleDraft.CONTENT), "");
        Assert.assertEquals(f.drafts.get(id).getLong(ArticleDraft.CREATED_TIME), created);
        f.api.remove("user-a", id, "delete");
        f.api.remove("user-a", id, "delete");
        Assert.assertTrue(f.drafts.isEmpty());
        // 草稿删除后回放旧的创建请求，仍只返回原回执。
        Assert.assertEquals(f.api.save("user-a", null, "create", body).getString("id"), id);
        Assert.assertTrue(f.drafts.isEmpty());
    }

    @Test
    public void conflictAndOtherUserCannotOverwriteOrDeleteDraft() throws Exception {
        final Fixture f = fixture();
        final String id = f.api.save("user-a", null, "key", new JSONObject().put("title", "original")).getString("id");
        failure(409, () -> f.api.save("user-a", null, "key", new JSONObject().put("title", "changed")));
        failure(404, () -> f.api.save("user-b", id, "update", new JSONObject().put("title", "changed")));
        failure(404, () -> f.api.remove("user-b", id, "delete"));
        Assert.assertEquals(f.drafts.get(id).getString(ArticleDraft.TITLE), "original");
        Assert.assertEquals(f.requests.size(), 1);
    }

    @Test
    public void failedReplayResultWriteCannotLeaveOrphanDraft() throws Exception {
        final Fixture f = fixture();
        f.failResults = true;
        try { f.api.save("user-a", null, "retry", new JSONObject().put("title", "draft")); Assert.fail("Expected storage failure"); }
        catch (final RepositoryException expected) { }
        Assert.assertTrue(f.drafts.isEmpty());
        Assert.assertTrue(f.requests.isEmpty());
        f.failResults = false;
        f.api.save("user-a", null, "retry", new JSONObject().put("title", "draft"));
        Assert.assertEquals(f.drafts.size(), 1);
    }

    @Test
    public void legacySaveStillCommitsAndJoinedMethodsRequireTransaction() throws Exception {
        final Fixture f = fixture();
        final JSONObject result = f.legacy.saveDraft("user-a", new JSONObject().put(Article.ARTICLE_TITLE, "legacy"));
        final String id = result.getString(Keys.OBJECT_ID);
        Assert.assertEquals(f.drafts.get(id).getString(ArticleDraft.TITLE), "legacy");
        f.legacy.removeDraft("user-a", id);
        Assert.assertTrue(f.drafts.isEmpty());
        try { f.legacy.saveDraftInCurrentTransaction("user-a", new JSONObject()); Assert.fail("Expected transaction guard"); }
        catch (final IllegalStateException expected) { }
    }

    private Fixture fixture() throws Exception {
        // 使用 NONE，确保实际 Repository 父类构造函数也无法连接数据库。
        final var field = Latkes.class.getDeclaredField("localProps");
        field.setAccessible(true);
        final Properties previous = (Properties) field.get(null);
        final Properties properties = new Properties();
        if (null != previous) properties.putAll(previous);
        properties.setProperty("runtimeDatabase", "NONE");
        Latkes.setLocalProps(properties);
        try { return new Fixture(); }
        finally { Latkes.setLocalProps(previous); }
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
    private void failure(final int status, final Action action) throws Exception {
        try { action.run(); Assert.fail("Expected access/protocol failure"); }
        catch (final ApiV3Exception e) { Assert.assertEquals(e.status, status); }
    }
    private static void inject(final Object target, final String name, final Object value) throws Exception {
        final var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Map<String, JSONObject> copy(final Map<String, JSONObject> source) {
        final Map<String, JSONObject> result = new HashMap<>();
        source.forEach((id, row) -> result.put(id, new JSONObject(row.toString())));
        return result;
    }

    private static final class Fixture {
        private Map<String, JSONObject> requests = new HashMap<>();
        private Map<String, JSONObject> drafts = new HashMap<>();
        private Tx current;
        private boolean failResults;
        private final AtomicInteger ids = new AtomicInteger(1000);
        private final ArticleDraftMgmtService legacy = new ArticleDraftMgmtService();
        private final ApiV3IdempotencyMgmtService replay = new ApiV3IdempotencyMgmtService();
        private final ApiV3DraftMgmtService api = new ApiV3DraftMgmtService();
        private Fixture() throws Exception {
            inject(legacy, "articleDraftRepository", new Drafts());
            inject(replay, "requestRepository", new Requests());
            inject(api, "draftMgmtService", legacy);
            inject(api, "idempotencyMgmtService", replay);
        }
        private Transaction begin() { Assert.assertNull(current); current = new Tx(); return current; }
        private final class Tx implements Transaction {
            private final Map<String, JSONObject> requestRows = copy(requests);
            private final Map<String, JSONObject> draftRows = copy(drafts);
            private boolean active = true;
            @Override public void commit() { requests = requestRows; drafts = draftRows; active = false; current = null; }
            @Override public void rollback() { active = false; current = null; }
            @Override public boolean isActive() { return active; }
        }
        private final class Requests extends ApiV3RequestRepository {
            @Override public Transaction beginTransaction() { return begin(); }
            @Override public boolean hasTransactionBegun() { return null != current; }
            @Override public JSONObject getForUpdate(final String id) { return current.requestRows.get(id); }
            @Override public String add(final JSONObject row) { final String id = row.getString(Keys.OBJECT_ID); current.requestRows.put(id, row); return id; }
            @Override public void update(final String id, final JSONObject row, final String... names) throws RepositoryException {
                if (failResults) throw new RepositoryException("result storage unavailable");
                current.requestRows.put(id, row);
            }
        }
        private final class Drafts extends ArticleDraftRepository {
            @Override public Transaction beginTransaction() { return begin(); }
            @Override public boolean hasTransactionBegun() { return null != current; }
            @Override public JSONObject getOwnedDraftForUpdate(final String user, final String id) {
                final JSONObject row = current.draftRows.get(id);
                return null != row && user.equals(row.optString(ArticleDraft.USER_ID)) ? row : null;
            }
            @Override public String add(final JSONObject row) {
                final String id = String.valueOf(ids.incrementAndGet()); row.put(Keys.OBJECT_ID, id); current.draftRows.put(id, row); return id;
            }
            @Override public void update(final String id, final JSONObject row, final String... names) { current.draftRows.put(id, row); }
            @Override public void remove(final String id) { current.draftRows.remove(id); }
        }
    }

    public static void main(final String[] args) throws Exception {
        final var test = new ApiV3DraftMgmtServiceTest();
        test.createReadUpdateAndDeleteReplayTheOriginalResult();
        test.conflictAndOtherUserCannotOverwriteOrDeleteDraft();
        test.failedReplayResultWriteCannotLeaveOrphanDraft();
        test.legacySaveStillCommitsAndJoinedMethodsRequireTransaction();
        System.out.println("PASS: 4 real draft service chain tests (transactional test adapter; no SQL/HTTP)");
    }
}
