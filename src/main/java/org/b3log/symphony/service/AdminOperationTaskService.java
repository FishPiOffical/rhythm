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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.AdminOperationTaskRepository;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.AnonymousCaptchaShield;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.Firewall;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** 数据库只记录意图和结果；外部副作用不在数据库事务中执行。 */
@Service
public class AdminOperationTaskService {
    private static final Logger LOGGER = LogManager.getLogger(AdminOperationTaskService.class);
    private static final long STALE_MILLIS = 120_000L;
    private final AtomicBoolean scheduled = new AtomicBoolean();
    @Inject private AdminOperationTaskRepository tasks;
    @Inject private ArticleRepository articles;
    @Inject private UserRepository users;
    @Inject private RoleQueryService roles;
    @Inject private SearchMgmtService search;

    public JSONObject createInCurrentTransaction(final String actorId, final String kind, final JSONObject payload) throws Exception {
        if (!tasks.hasTransactionBegun()) throw new IllegalStateException("任务必须随受理回执提交");
        if (tasks.lock("dispatcher") == null) throw new IllegalStateException("请先执行后台任务 SQL 迁移");
        final List<JSONObject> counts = tasks.select("SELECT COUNT(*) AS pending FROM `" + tasks.getName()
                + "` WHERE status IN (?,?)", "QUEUED", "RUNNING");
        if (counts.getFirst().optInt("pending") >= 100) {
            throw new org.b3log.symphony.util.ApiV3Exception(429, "task_queue_full", "后台任务队列已满");
        }
        final String id = Ids.genTimeMillisId();
        final long now = System.currentTimeMillis();
        final JSONObject row = new JSONObject().put(Keys.OBJECT_ID, id).put("actorId", actorId)
                .put("kind", kind).put("status", "QUEUED").put("payload", payload.toString())
                .put("result", new JSONObject().put("processedCount", 0).toString())
                .put("createdAt", now).put("updatedAt", now);
        tasks.add(row);
        return new JSONObject().put("taskId", id).put("accepted", true).put("triggeredAt", now);
    }

    public JSONObject read(final String id) throws Exception {
        final List<JSONObject> rows = tasks.select("SELECT oId,actorId,kind,status,result,createdAt,updatedAt FROM `"
                + tasks.getName() + "` WHERE oId=? AND kind<>?", id, "DISPATCHER");
        if (rows.isEmpty()) return null;
        final JSONObject row = rows.getFirst();
        final String storedStatus = row.optString("status");
        // 进程崩溃或提交结果未知时不宣称成功，也不自动重放可能已经执行的副作用。
        final String status = "RUNNING".equals(storedStatus)
                && System.currentTimeMillis() - row.optLong("updatedAt") > STALE_MILLIS ? "UNKNOWN" : storedStatus;
        return new JSONObject().put("taskId", id).put("kind", row.optString("kind"))
                .put("status", status).put("createdAt", row.optLong("createdAt"))
                .put("updatedAt", row.optLong("updatedAt")).put("result", new JSONObject(row.getString("result")));
    }

    /** 失败的搜索任务可以原键重试；IP/开关及未知结果绝不自动重复执行。 */
    public void dispatch(final String id) {
        try {
            final Transaction tx = ApiV3Transactions.strict(tasks.beginTransaction());
            try {
                final JSONObject task = tasks.lock(id);
                if (task != null && task.optString("kind").startsWith("REINDEX")
                        && "FAILED".equals(task.optString("status"))) {
                    task.put("status", "QUEUED").put("updatedAt", System.currentTimeMillis());
                    tasks.update(id, task, "status", "updatedAt");
                }
                tx.commit();
            } finally { if (tx.isActive()) tx.rollback(); }
            wake();
        } catch (final Exception e) {
            // 受理事务已经提交，调度故障不能把受理响应改成失败；查询仍能看到 QUEUED。
            LOGGER.error("后台任务调度暂不可用，taskId={}", id, e);
        }
    }

    public void wake() {
        if (!scheduled.compareAndSet(false, true)) return;
        try {
            Symphonys.EXECUTOR_SERVICE.submit(() -> {
                try {
                    for (;;) {
                        final JSONObject task = claim();
                        if (task == null) return;
                        execute(task);
                    }
                } catch (final Exception e) {
                    LOGGER.error("后台任务执行状态未确认，请检查持久任务记录", e);
                } finally {
                    scheduled.set(false);
                    ApiV3Transactions.releaseThreadResources();
                }
            });
        } catch (final RuntimeException e) {
            scheduled.set(false);
            LOGGER.error("后台任务队列拒绝调度，任务仍保留待执行", e);
        }
    }

    private JSONObject claim() throws Exception {
        final Transaction tx = ApiV3Transactions.strict(tasks.beginTransaction());
        try {
            if (tasks.lock("dispatcher") == null) throw new IllegalStateException("请先执行后台任务 SQL 迁移");
            final List<JSONObject> running = tasks.select("SELECT oId,updatedAt FROM `" + tasks.getName()
                    + "` WHERE status=?", "RUNNING");
            if (!running.isEmpty()) {
                tx.commit();
                return null;
            }
            final List<JSONObject> queue = tasks.select("SELECT oId FROM `" + tasks.getName()
                    + "` WHERE status=? ORDER BY createdAt,oId LIMIT 1", "QUEUED");
            if (queue.isEmpty()) { tx.commit(); return null; }
            final JSONObject task = tasks.lock(queue.getFirst().getString(Keys.OBJECT_ID));
            task.put("status", "RUNNING").put("updatedAt", System.currentTimeMillis());
            tasks.update(task.getString(Keys.OBJECT_ID), task, "status", "updatedAt");
            tx.commit();
            return task;
        } finally { if (tx.isActive()) tx.rollback(); }
    }

    private void execute(final JSONObject task) throws Exception {
        final JSONObject payload = new JSONObject(task.getString("payload"));
        final JSONObject result = new JSONObject().put("processedCount", 0);
        final String kind = task.getString("kind");
        try {
            authorize(task);
            switch (kind) {
                case "IP" -> executeIps(task, payload, result);
                case "SWITCHES" -> executeSwitches(payload, result);
                case "REINDEX_ONE" -> indexOne(payload.getString("articleId"), result, true);
                case "REINDEX_ALL" -> indexAll(task, result);
                default -> throw new IllegalStateException("未知任务类型");
            }
        } catch (final Exception e) {
            // 不保存供应商响应、URL 或异常正文，避免把服务凭据及原始内容写入 API。
            result.put("error", "operation_failed");
            finish(task, kind.startsWith("REINDEX") ? "FAILED" : "UNKNOWN", result);
            LOGGER.error("后台外部操作未完成，taskId={} kind={}", task.optString(Keys.OBJECT_ID), kind);
            return;
        }
        finish(task, result.optBoolean("partial") ? "PARTIAL" : "SUCCEEDED", result);
    }

    private void authorize(final JSONObject task) throws Exception {
        final List<JSONObject> rows = users.select("SELECT userRole,userStatus FROM `" + users.getName()
                + "` WHERE oId=?", task.getString("actorId"));
        if (rows.isEmpty() || rows.getFirst().optInt("userStatus", -1) != UserExt.USER_STATUS_C_VALID) {
            throw new IllegalStateException("操作员不可用");
        }
        final JSONObject user = rows.getFirst();
        final Set<String> permissions = roles.getPermissions(user.getString("userRole"));
        final String kind = task.getString("kind");
        final String permission = switch (kind) {
            case "IP" -> Permission.PERMISSION_ID_C_IP_MANAGE;
            case "SWITCHES" -> Permission.PERMISSION_ID_C_MENU_ADMIN_MISC;
            default -> Permission.PERMISSION_ID_C_MENU_ADMIN_ARTICLES;
        };
        if (!permissions.contains(Permission.PERMISSION_ID_C_MENU_ADMIN) || !permissions.contains(permission)) {
            throw new IllegalStateException("操作员权限已变化");
        }
        if (kind.startsWith("REINDEX") && !permissions.contains("REINDEX_ALL".equals(kind)
                ? Permission.PERMISSION_ID_C_ARTICLE_REINDEX_ARTICLES_INDEX
                : Permission.PERMISSION_ID_C_ARTICLE_REINDEX_ARTICLE_INDEX)) {
            throw new IllegalStateException("缺少索引维护权限");
        }
        if ("SWITCHES".equals(kind) && !Role.ROLE_ID_C_ADMIN.equals(user.getString("userRole"))) {
            throw new IllegalStateException("缺少社区安全管理员权限");
        }
    }

    // [验证码链路指纹开始：executeIps]
    private void executeIps(final JSONObject task, final JSONObject payload, final JSONObject result) throws Exception {
        final boolean ban = "ban".equals(payload.getString("action"));
        final JSONArray items = new JSONArray();
        for (final Object ip : payload.getJSONArray("ipList")) {
            items.put(new JSONObject().put("ip", ip).put("status", "QUEUED"));
        }
        result.put("action", payload.getString("action")).put("items", items);
        for (int i = 0; i < items.length(); i++) {
            final JSONObject item = items.getJSONObject(i);
            item.put("status", "RUNNING");
            checkpoint(task, result);
            final String ip = item.getString("ip");
            try {
                if (ban) {
                    // 本轮已明确授权新增 V3 控制面；保持系统封禁前撤销验证码豁免。
                    AnonymousCaptchaShield.removeWhitelist(ip);
                    Firewall.banIp(ip);
                } else {
                    Firewall.unbanIp(ip);
                }
                final boolean verified = Firewall.verifyIpState(ip, ban);
                if (!ban && verified) {
                    // 系统解封确认后才清验证码状态并加白；不得绕过此顺序。
                    AnonymousCaptchaShield.administratorUnban(ip);
                }
                item.put("status", verified ? "SUCCEEDED" : "UNKNOWN");
                if (verified) result.put("processedCount", result.getInt("processedCount") + 1);
                else result.put("partial", true);
            } catch (final Exception e) {
                item.put("status", "UNKNOWN");
                result.put("partial", true);
            }
            checkpoint(task, result);
        }
    }
    // [验证码链路指纹结束：executeIps]

    // [验证码链路指纹开始：executeSwitches]
    private void executeSwitches(final JSONObject payload, final JSONObject result) {
        if (Latkes.RuntimeMode.DEVELOPMENT != Latkes.getRuntimeMode()) throw new IllegalStateException("生产只读");
        Firewall.setThreshold(payload.getInt("threshold"));
        Firewall.setEnabled(payload.getBoolean("firewallEnabled"));
        // 本轮已明确授权，保持原开关含义，不清理任何验证码状态。
        AnonymousCaptchaShield.setEnabled(payload.getBoolean("verificationEnabled"));
        AnonymousCaptchaShield.setFirstVisitCaptchaEnabled(payload.getBoolean("firstVisitCaptchaEnabled"));
        result.put("switches", new JSONObject(payload.toString()));
    }
    // [验证码链路指纹结束：executeSwitches]

    private void indexOne(final String id, final JSONObject result, final boolean pushToBaidu) throws Exception {
        final JSONObject article = articles.get(id);
        if (article == null) throw new IllegalStateException("文章不存在");
        if (article.optInt(Article.ARTICLE_TYPE) == Article.ARTICLE_TYPE_C_DISCUSSION
                || article.optInt(Article.ARTICLE_TYPE) == Article.ARTICLE_TYPE_C_THOUGHT) {
            result.put("skipped", true);
            return;
        }
        search.updateAdminArticleIndex(new JSONObject(article.toString()));
        if (pushToBaidu) ArticleMgmtService.pushAdminArticleToBaidu(article);
        result.put("processedCount", result.getInt("processedCount") + 1);
    }

    private void indexAll(final JSONObject task, final JSONObject result) throws Exception {
        search.rebuildAdminArticleIndex();
        String after = "";
        for (;;) {
            final List<JSONObject> rows = articles.select("SELECT oId FROM `" + articles.getName()
                    + "` WHERE articleStatus=? AND articleType IN (?,?) AND oId>? ORDER BY oId LIMIT 50",
                    Article.ARTICLE_STATUS_C_VALID, Article.ARTICLE_TYPE_C_NORMAL, Article.ARTICLE_TYPE_C_CITY_BROADCAST, after);
            if (rows.isEmpty()) return;
            for (final JSONObject row : rows) {
                after = row.getString(Keys.OBJECT_ID);
                checkpoint(task, result);
                indexOne(after, result, false);
            }
        }
    }

    private void checkpoint(final JSONObject task, final JSONObject result) throws Exception {
        finish(task, "RUNNING", result);
    }

    private void finish(final JSONObject task, final String status, final JSONObject result) throws Exception {
        final Transaction tx = ApiV3Transactions.strict(tasks.beginTransaction());
        try {
            final String id = task.getString(Keys.OBJECT_ID);
            final JSONObject stored = tasks.lock(id);
            if (stored == null || !"RUNNING".equals(stored.optString("status"))) {
                throw new IllegalStateException("任务执行所有权已失效");
            }
            stored.put("status", status).put("result", result.toString()).put("updatedAt", System.currentTimeMillis());
            tasks.update(id, stored, "status", "result", "updatedAt");
            tx.commit();
        } finally { if (tx.isActive()) tx.rollback(); }
    }
}
