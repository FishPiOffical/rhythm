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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** 作者付费置顶的时长、扣费和重试回执在同一个严格事务中提交。 */
@Service
public class ApiV3ArticleStickService {
    private static final String OPERATION = "articles.stick";
    @Inject private UserRepository users;
    @Inject private ArticleRepository articles;
    @Inject private ApiV3RequestRepository requests;
    @Inject private PointtransferMgmtService points;
    @Inject private PointtransferRepository transfers;
    @Inject private PointtransferRedcRepository indexes;
    protected long now() { return System.currentTimeMillis(); }

    public JSONObject stick(JSONObject viewer, String articleId, String key) throws Exception {
        final String actor = viewer == null ? "" : viewer.optString("oId");
        if (!actor.matches("[1-9][0-9]{0,18}")) throw new ApiV3Exception(401, "unauthorized", "需要登录");
        if (articleId == null || !articleId.matches("[0-9]{1,32}")) throw ApiV3WriteInputs.invalid();
        try {
            final String sourceId = ApiV3TokenUtil.hash(OPERATION + "\0" + actor + "\0" + key);
            final JSONObject receipt = points.executeWithTransferLocks(actor, List.of(Pointtransfer.ID_C_SYS), () -> {
                try (final var cache = ArticlePublicationCaches.open(requests::hasTransactionBegun)) {
                    ArticlePublicationCaches.afterTransaction("stick-user:" + actor, () -> {
                        try { users.refreshSettingsCache(actor); } catch (Exception error) { users.evictSettingsCaches(actor); }
                    });
                    final ApiV3MutationStore store = new ApiV3MutationStore() {
                        public Transaction beginTransaction() { return ApiV3Transactions.strict(requests.beginTransaction()); }
                        public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
                        public JSONObject getForUpdate(String id) throws RepositoryException { requireActor(actor); return requests.getForUpdate(id); }
                        public String add(JSONObject row) throws RepositoryException { return requests.add(row); }
                        public void update(String id, JSONObject row, String... fields) throws RepositoryException { requests.update(id, row, fields); }
                    };
                    return ApiV3IdempotencyMgmtService.runAtomic(store, actor, OPERATION, key, new JSONObject().put("id", articleId),
                        () -> ApiV3TransactionSavepoint.run(() -> {
                            final JSONObject owner = requireActor(actor);
                            final JSONObject article = articles.getOwnedForUpdate(articleId, actor);
                            if (article == null || exact(article.opt("articleStatus")) != Article.ARTICLE_STATUS_C_VALID
                                    || !ApiV3ContentUtil.canViewArticle(article, owner, Set.of())) throw ApiV3Requests.notFound();
                            final long previous = exact(article.opt("articleStick"));
                            if (previous < 0) throw unavailable();
                            if (previous == Long.MAX_VALUE) throw new ApiV3Exception(409, "permanent_stick", "不能覆盖管理员永久置顶");
                            final int price = Pointtransfer.TRANSFER_SUM_C_STICK_ARTICLE;
                            if (price <= 0) throw unavailable();
                            if (exact(owner.opt("userPoint")) < price) throw new ApiV3Exception(409, "insufficient_points", "积分不足");
                            final long time = now();
                            if (time <= 0 || time == Long.MAX_VALUE) throw unavailable();
                            articles.update(articleId, new JSONObject().put("articleStick", time), "articleStick");
                            final String transferId = points.transferInCurrentTransaction(actor, Pointtransfer.ID_C_SYS,
                                    Pointtransfer.TRANSFER_TYPE_C_STICK_ARTICLE, price, articleId, time, "",
                                    new PointtransferSource("", "", OPERATION, sourceId), false);
                            if (!ledgerId(transferId)) throw unavailable();
                            return new JSONObject().put("id", articleId).put("stuckAt", time).put("paidPoints", price).put("indexTransferId", transferId);
                        }));
                }
            });
            final JSONObject result = project(receipt, articleId);
            repairIndex(actor, articleId, sourceId, receipt.getString("indexTransferId"));
            return result;
        } catch (ApiV3Exception error) { throw error; }
        catch (Exception error) { throw unavailable(); }
    }
    private JSONObject requireActor(String actor) throws RepositoryException {
        final JSONObject user = users.getForUpdate(actor);
        if (user == null || !actor.equals(user.optString("oId")) || exact(user.opt("userStatus")) != UserExt.USER_STATUS_C_VALID)
            throw ApiV3Requests.notFound();
        return user;
    }
    private static JSONObject project(JSONObject receipt, String articleId) {
        if (!receipt.keySet().equals(Set.of("id", "stuckAt", "paidPoints", "indexTransferId"))
                || !articleId.equals(receipt.optString("id")) || !ledgerId(receipt.optString("indexTransferId"))
                || exact(receipt.opt("stuckAt")) <= 0 || exact(receipt.opt("stuckAt")) == Long.MAX_VALUE
                || exact(receipt.opt("paidPoints")) <= 0 || exact(receipt.opt("paidPoints")) > Integer.MAX_VALUE) throw unavailable();
        return new JSONObject().put("id", articleId).put("stuckAt", receipt.getLong("stuckAt")).put("paidPoints", receipt.getInt("paidPoints"));
    }
    private void repairIndex(String actor, String articleId, String sourceId, String transferId) {
        Transaction transaction = null;
        try {
            if (transfers.hasTransactionBegun()) throw new IllegalStateException("committed stick ledger required");
            transaction = ApiV3Transactions.strict(transfers.beginTransaction());
            final var parent = transfers.select("SELECT oId FROM `" + transfers.getName()
                    + "` WHERE oId=? AND fromId=? AND toId=? AND type=? AND dataId=? AND sourceScene=? AND sourceRequestId=? LIMIT 2 FOR UPDATE",
                    transferId, actor, Pointtransfer.ID_C_SYS, Pointtransfer.TRANSFER_TYPE_C_STICK_ARTICLE, articleId, OPERATION, sourceId);
            if (parent.size() != 1 || !transferId.equals(parent.getFirst().optString("oId"))) throw unavailable();
            for (String user : List.of(actor, Pointtransfer.ID_C_SYS)) {
                final var found = indexes.select("SELECT oId FROM `" + indexes.getName() + "` WHERE userId=? AND rawId=? LIMIT 2 FOR UPDATE", user, transferId);
                if (found.isEmpty() && ApiV3TransactionStatements.execute("INSERT INTO `" + indexes.getName()
                        + "` (oId,userId,rawId) VALUES(?,?,?)", Ids.genTimeMillisId(), user, transferId) != 1) throw unavailable();
            }
            transaction.commit();
        } catch (Exception error) {
            if (transaction != null && transaction.isActive()) try { transaction.rollback(); } catch (RuntimeException ignored) { }
            org.apache.logging.log4j.LogManager.getLogger(getClass()).warn("Committed article stick indexing delayed");
        }
    }
    private static boolean ledgerId(String id) { return id != null && id.matches("[1-9][0-9]{0,18}"); }
    private static long exact(Object value) {
        if (!(value instanceof Number number)) throw unavailable();
        try { return new BigDecimal(number.toString()).longValueExact(); }
        catch (ArithmeticException | NumberFormatException error) { throw unavailable(); }
    }
    private static ApiV3Exception unavailable() { return new ApiV3Exception(503, "temporarily_unavailable", "文章置顶暂不可用"); }
}
