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
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.repository.AdminOperationTaskRepository;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.Role;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/** 真实任务服务的故障与状态测试；存储适配器不代表 MySQL 或外部服务验收。 */
public class AdminOperationTaskServiceTest {
    static {
        final Properties properties = new Properties();
        properties.setProperty("runtimeDatabase", "NONE");
        org.b3log.latke.Latkes.setLatkeProps(properties);
        org.b3log.latke.Latkes.setLocalProps(properties);
    }
    static final class Store extends AdminOperationTaskRepository {
        final Map<String, JSONObject> rows = new LinkedHashMap<>();
        boolean active;
        boolean rejectCommit;
        Store() { rows.put("dispatcher", new JSONObject().put("oId", "dispatcher").put("status", "INTERNAL")); }
        @Override public boolean hasTransactionBegun() { return active; }
        @Override public Transaction beginTransaction() {
            if (active) throw new IllegalStateException("嵌套事务");
            active = true;
            final Map<String, JSONObject> before = new LinkedHashMap<>();
            rows.forEach((k, v) -> before.put(k, new JSONObject(v.toString())));
            return new Transaction() {
                public String getId() { return "test"; }
                public boolean isActive() { return active; }
                public void commit() {
                    if (rejectCommit) { rejectCommit = false; throw new IllegalStateException("提交故障"); }
                    active = false;
                }
                public void rollback() { rows.clear(); rows.putAll(before); active = false; }
            };
        }
        @Override public JSONObject lock(final String id) {
            final JSONObject row = rows.get(id);
            return row == null ? null : new JSONObject(row.toString());
        }
        @Override public String add(final JSONObject row) { rows.put(row.getString("oId"), new JSONObject(row.toString())); return row.getString("oId"); }
        @Override public void update(final String id, final JSONObject row, final String... fields) { rows.put(id, new JSONObject(row.toString())); }
        @Override public List<JSONObject> select(final String sql, final Object... args) {
            if (sql.contains("COUNT(*)")) return List.of(new JSONObject().put("pending", 0));
            if (sql.contains("WHERE oId=?")) {
                final JSONObject row = lock(args[0].toString());
                return row == null ? List.of() : List.of(row);
            }
            return rows.values().stream().filter(r -> args[0].equals(r.optString("status")))
                    .map(r -> new JSONObject(r.toString())).toList();
        }
    }
    static final class Search extends SearchMgmtService {
        int writes;
        boolean fail;
        Store store;
        @Override public void updateAdminArticleIndex(final JSONObject article) {
            Assert.assertFalse(store.active, "外部调用不可发生在数据库事务内");
            writes++;
            if (fail) throw new IllegalStateException("搜索失败");
        }
    }
    static final class Fixture {
        final Store store = new Store();
        final Search search = new Search();
        final AdminOperationTaskService service = new AdminOperationTaskService();
        Fixture() throws Exception {
            search.store = store;
            inject(service, "tasks", store);
            inject(service, "search", search);
            inject(service, "articles", new ArticleRepository() {
                @Override public JSONObject get(final String id) { return new JSONObject().put("oId", id).put("articleType", 0); }
            });
            inject(service, "users", new UserRepository() {
                @Override public List<JSONObject> select(final String sql, final Object... args) {
                    return List.of(new JSONObject().put("userName", "operator").put("userRole", Role.ROLE_ID_C_ADMIN).put("userStatus", 0));
                }
            });
            inject(service, "roles", new RoleQueryService() {
                @Override public Set<String> getPermissions(final String role) {
                    return Set.of(Permission.PERMISSION_ID_C_MENU_ADMIN, Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES,
                            Permission.PERMISSION_ID_C_ARTICLE_REINDEX_ARTICLE_INDEX);
                }
            });
        }
        String queue() throws Exception {
            final Transaction tx = store.beginTransaction();
            final String id = service.createInCurrentTransaction("1", "REINDEX_ONE", new JSONObject().put("articleId", "2")).getString("taskId");
            tx.commit();
            return id;
        }
    }
    @Test public void acceptsThenExecutesOutsideTransaction() throws Exception {
        final Fixture f = new Fixture(); final String id = f.queue();
        Assert.assertEquals(f.search.writes, 0);
        final JSONObject task = (JSONObject) call(f.service, "claim");
        Assert.assertNull(call(f.service, "claim"), "已有执行者时不得再次领取");
        call(f.service, "execute", task);
        Assert.assertEquals(f.search.writes, 1);
        Assert.assertEquals(f.service.read(id).getString("status"), "SUCCEEDED");
        Assert.assertNull(call(f.service, "claim"), "成功任务不得再次领取");
    }
    @Test public void externalFailureIsDurableAndDoesNotClaimSuccess() throws Exception {
        final Fixture f = new Fixture(); final String id = f.queue(); f.search.fail = true;
        call(f.service, "execute", (JSONObject) call(f.service, "claim"));
        Assert.assertEquals(f.service.read(id).getString("status"), "FAILED");
        Assert.assertEquals(f.service.read(id).getJSONObject("result").getString("error"), "operation_failed");
    }
    @Test public void resultCommitFailureLeavesUnknownWithoutReplay() throws Exception {
        final Fixture f = new Fixture(); final String id = f.queue();
        final JSONObject task = (JSONObject) call(f.service, "claim"); f.store.rejectCommit = true;
        try { call(f.service, "execute", task); Assert.fail("提交失败必须传播"); }
        catch (final IllegalStateException expected) { }
        f.store.rows.get(id).put("updatedAt", 0L);
        Assert.assertEquals(f.service.read(id).getString("status"), "UNKNOWN");
        Assert.assertNull(call(f.service, "claim"));
        Assert.assertEquals(f.search.writes, 1);
    }
    static void inject(final Object target, final String name, final Object value) throws Exception {
        final var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    static Object call(final Object target, final String name, final JSONObject... args) throws Exception {
        final var method = target.getClass().getDeclaredMethod(name, args.length == 0 ? new Class<?>[0] : new Class<?>[]{JSONObject.class});
        method.setAccessible(true);
        try { return method.invoke(target, (Object[]) args); }
        catch (final InvocationTargetException e) { throw (Exception) e.getCause(); }
    }
    public static void main(final String[] args) throws Exception {
        final var test = new AdminOperationTaskServiceTest();
        test.acceptsThenExecutesOutsideTransaction(); test.externalFailureIsDurableAndDoesNotClaimSuccess();
        test.resultCommitFailureLeavesUnknownWithoutReplay();
        System.out.println("PASS: task claim exclusivity, post-commit execution, external failure, uncertain commit and no blind replay");
    }
}
