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

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.LongArticleRead;
import org.b3log.symphony.model.LongArticleReadSettlementClaim;
import org.b3log.symphony.model.LongArticleReadWindowPage;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.PointtransferRedcRepository;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ArticlePublicationCaches;
import java.math.BigDecimal;
import org.b3log.symphony.model.ProfessionSourceEvent;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.LongArticleReadHistoryRepository;
import org.b3log.symphony.repository.LongArticleReadSettlementRepository;
import org.b3log.symphony.repository.LongArticleReadWindowRepository;
import org.b3log.symphony.repository.ProfessionSourceEventRepository;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 结算已关闭的长篇阅读窗口。 */
@Service
public class LongArticleReadSettlementService {

    private static final Logger LOGGER = LogManager.getLogger(LongArticleReadSettlementService.class);
    private static final long WINDOW_MILLIS = TimeUnit.HOURS.toMillis(6);
    private static final int ANONYMOUS_REWARD_CAP = 100;
    private static final int REGISTERED_REWARD = 10;
    private static final int ANONYMOUS_REWARD = 8;
    private static final int PAGE_SIZE = 100;

    @Inject
    private ArticleRepository articleRepository;

    @Inject
    private LongArticleReadWindowRepository windowRepository;

    @Inject
    private LongArticleReadSettlementRepository settlementRepository;

    @Inject
    private LongArticleReadHistoryRepository historyRepository;

    @Inject
    private ProfessionSourceEventRepository sourceEventRepository;

    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    @Inject
    private LongArticleReadSettlementMessageService messageService;

    @Inject
    private UserRepository users;

    @Inject
    private PointtransferRedcRepository indexes;

    public void settleAll() {
        settle(null);
    }

    public void settle(final String articleId) {
        if (windowRepository.hasTransactionBegun()) throw new IllegalStateException("settlement owns its root transaction");
        try { settleWindows(articleId); }
        finally { ApiV3Transactions.releaseThreadResources(); }
    }

    private void settleWindows(final String articleId) {
        final long currentWindowStart = currentWindowStart();
        long cursorWindowStart = 0;
        String cursorId = "";
        while (true) {
            final List<JSONObject> windows = loadPage(articleId, currentWindowStart, cursorWindowStart, cursorId);
            if (windows.isEmpty()) {
                return;
            }
            for (final JSONObject window : windows) {
                settleWindow(window, System.currentTimeMillis());
                cursorWindowStart = window.optLong(LongArticleRead.WINDOW_START);
                cursorId = window.optString(Keys.OBJECT_ID);
            }
            if (windows.size() < PAGE_SIZE) {
                return;
            }
        }
    }

    private List<JSONObject> loadPage(final String articleId, final long currentWindowStart,
                                      final long cursorWindowStart, final String cursorId) {
        try {
            return windowRepository.getOpenBefore(new LongArticleReadWindowPage(
                    StringUtils.isBlank(articleId) ? null : articleId, currentWindowStart,
                    cursorWindowStart, cursorId, PAGE_SIZE));
        } catch (final RepositoryException e) {
            throw new IllegalStateException("读取长篇阅读结算窗口失败", e);
        }
    }

    private void settleWindow(final JSONObject window, final long now) {
        try {
            final JSONObject result = settleWindowInTransaction(window, now);
            if (null != result) {
                messageService.send(result);
            }
        } catch (final Exception e) {
            LOGGER.error("结算长篇阅读窗口失败 [windowId={}]", window.optString(Keys.OBJECT_ID), e);
        }
    }

    private JSONObject settleWindowInTransaction(final JSONObject window, final long now) throws Exception {
        final String articleId = window.optString(LongArticleRead.ARTICLE_ID);
        final String author = articleRepository.getCommentPublicationAuthor(articleId);
        if (StringUtils.isBlank(author)) throw new RepositoryException("结算目标文章不存在");
        // Capture locks users before article/window. Keep the same order and acquire the
        // shared point pair before any SQL lock, including legacy transfers on this pair.
        return pointtransferMgmtService.executeWithTransferLocks(Pointtransfer.ID_C_SYS, List.of(author), () -> {
            try (final var cache = ArticlePublicationCaches.open(windowRepository::hasTransactionBegun)) {
                ArticlePublicationCaches.afterTransaction("long-read-user:" + author, () -> {
                    try { users.refreshSettingsCache(author); }
                    catch (final Exception error) { users.evictSettingsCaches(author); }
                });
                final Transaction transaction = ApiV3Transactions.strict(windowRepository.beginTransaction());
                try {
                    if (users.getForUpdate(author) == null) throw new RepositoryException("结算作者不存在");
                    final JSONObject article = articleRepository.getForUpdate(articleId);
                    if (article == null || !author.equals(article.optString(Article.ARTICLE_AUTHOR_ID)))
                        throw new RepositoryException("结算目标归属变化");
                    return settleClaimedWindow(new SettlementAttempt(window, article, now, transaction));
                } catch (final Exception error) {
                    rollback(transaction);
                    throw error;
                }
            }
        });
    }

    private JSONObject settleClaimedWindow(final SettlementAttempt attempt) throws Exception {
        final String settlementId = Ids.genTimeMillisId();
        final JSONObject window = attempt.window();
        if (!settlementRepository.claim(new LongArticleReadSettlementClaim(settlementId,
                window.optString(LongArticleRead.ARTICLE_ID), window.optLong(LongArticleRead.WINDOW_START), attempt.now()))) {
            attempt.transaction().commit();
            return null;
        }
        if (!windowRepository.startSettlement(window.optString(Keys.OBJECT_ID), settlementId, attempt.now())) {
            rollback(attempt.transaction());
            return null;
        }
        final JSONObject lockedWindow = windowRepository.getForUpdate(window.optString(Keys.OBJECT_ID));
        if (lockedWindow == null || !window.optString(LongArticleRead.ARTICLE_ID).equals(lockedWindow.optString(LongArticleRead.ARTICLE_ID))
                || window.optLong(LongArticleRead.WINDOW_START) != lockedWindow.optLong(LongArticleRead.WINDOW_START)
                || !"SETTLING".equals(lockedWindow.optString(LongArticleRead.STATE))
                || !settlementId.equals(lockedWindow.optString(LongArticleRead.SETTLEMENT_ID)))
            throw new RepositoryException("结算窗口绑定不一致");
        final SettlementContext context = SettlementContext.from(lockedWindow, attempt.article(), settlementId, attempt.now());
        final JSONObject result = persistSettlement(context);
        attempt.transaction().commit();
        return result;
    }

    private JSONObject persistSettlement(final SettlementContext context) throws Exception {
        final String historyId = persistHistory(context);
        persistPoint(context);
        persistSourceEvent(context);
        settlementRepository.complete(context.settlementId(), historyId, context.now());
        windowRepository.completeSettlement(context.window().optString(Keys.OBJECT_ID), context.now());
        return buildResult(context);
    }

    private String persistHistory(final SettlementContext context) throws RepositoryException {
        final String historyId = Ids.genTimeMillisId();
        final JSONObject history = new JSONObject();
        history.put(Keys.OBJECT_ID, historyId);
        history.put(LongArticleRead.ARTICLE_ID, context.articleId());
        history.put(LongArticleRead.WINDOW_START, context.windowStart());
        history.put(LongArticleRead.WINDOW_END, context.windowStart() + WINDOW_MILLIS);
        history.put(LongArticleRead.REGISTERED_CNT, context.registeredCount());
        history.put(LongArticleRead.ANON_CNT, context.anonymousCount());
        history.put(LongArticleRead.ANON_CAPPED_CNT, context.countedAnonymous());
        history.put(LongArticleRead.REWARD_POINT, context.reward());
        history.put(LongArticleRead.SETTLED_AT, context.now());
        historyRepository.add(history);
        return historyId;
    }

    private void persistPoint(final SettlementContext context) throws RepositoryException {
        if (context.reward() == 0) {
            return;
        }
        final String transferId = pointtransferMgmtService.transferInCurrentTransaction(Pointtransfer.ID_C_SYS,
                context.article().optString(Article.ARTICLE_AUTHOR_ID), Pointtransfer.TRANSFER_TYPE_C_LONG_ARTICLE_READ_REWARD,
                context.reward(), context.articleId(), context.now(), "长文阅读奖励", PointtransferSource.EMPTY, false);
        if (StringUtils.isBlank(transferId)) {
            throw new RepositoryException("长篇阅读积分转账失败");
        }
        // A completed gate must never outlive its ledger indexes. No pre-commit worker.
        for (final String userId : List.of(Pointtransfer.ID_C_SYS, context.article().optString(Article.ARTICLE_AUTHOR_ID))) {
            indexes.add(new JSONObject().put("userId", userId).put("rawId", transferId));
        }
    }

    private void persistSourceEvent(final SettlementContext context) throws RepositoryException {
        final JSONObject event = new JSONObject();
        event.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        event.put(ProfessionSourceEvent.EVENT_KEY, "long-article-read:" + context.articleId() + ':' + context.windowStart());
        event.put(ProfessionSourceEvent.EVENT_TYPE, "long_article.read_settled");
        event.put(ProfessionSourceEvent.SCHEMA_VERSION, 1);
        event.put(ProfessionSourceEvent.SUBJECT_USER_ID, context.article().optString(Article.ARTICLE_AUTHOR_ID));
        event.put(ProfessionSourceEvent.TARGET_TYPE, "article");
        event.put(ProfessionSourceEvent.TARGET_ID, context.articleId());
        event.put(ProfessionSourceEvent.OCCURRED_AT, context.windowStart() + WINDOW_MILLIS);
        event.put(ProfessionSourceEvent.CAPTURED_AT, context.now());
        event.put(ProfessionSourceEvent.PAYLOAD_JSON, eventPayload(context));
        event.put(ProfessionSourceEvent.VISIBILITY_CLASS, "PUBLIC");
        event.put(ProfessionSourceEvent.SOURCE_SYSTEM, "long-article-read");
        event.put(ProfessionSourceEvent.STATUS, "PENDING");
        event.put(ProfessionSourceEvent.CREATED_AT, context.now());
        event.put(ProfessionSourceEvent.UPDATED_AT, context.now());
        sourceEventRepository.add(event);
    }

    private String eventPayload(final SettlementContext context) {
        final JSONObject payload = new JSONObject();
        payload.put("settlementId", context.settlementId());
        payload.put("articleId", context.articleId());
        payload.put("authorUserId", context.article().optString(Article.ARTICLE_AUTHOR_ID));
        payload.put("windowStart", context.windowStart());
        payload.put("windowEnd", context.windowStart() + WINDOW_MILLIS);
        payload.put("registeredReaderCount", context.registeredCount());
        payload.put("anonymousReaderCount", context.anonymousCount());
        payload.put("countedAnonymousReaderCount", context.countedAnonymous());
        payload.put("rewardPoint", context.reward());
        return payload.toString();
    }

    private JSONObject buildResult(final SettlementContext context) {
        final JSONObject result = new JSONObject();
        result.put(Article.ARTICLE_AUTHOR_ID, context.article().optString(Article.ARTICLE_AUTHOR_ID));
        result.put(Article.ARTICLE_T_ID, context.articleId());
        result.put(Article.ARTICLE_TITLE, context.article().optString(Article.ARTICLE_TITLE));
        result.put(LongArticleRead.REGISTERED_CNT, context.registeredCount());
        result.put(LongArticleRead.ANON_CNT, context.anonymousCount());
        result.put(LongArticleRead.ANON_CAPPED_CNT, context.countedAnonymous());
        result.put(LongArticleRead.REWARD_POINT, context.reward());
        result.put(LongArticleRead.WINDOW_START, context.windowStart());
        result.put(LongArticleRead.WINDOW_END, context.windowStart() + WINDOW_MILLIS);
        return result;
    }

    private long currentWindowStart() {
        final long now = System.currentTimeMillis();
        return now / WINDOW_MILLIS * WINDOW_MILLIS;
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    private record SettlementAttempt(JSONObject window, JSONObject article, long now, Transaction transaction) {
    }

    private record SettlementContext(JSONObject window, JSONObject article, String settlementId, long now,
                                     int registeredCount, int anonymousCount, int countedAnonymous, int reward) {

        private static SettlementContext from(final JSONObject window, final JSONObject article,
                                              final String settlementId, final long now) {
            final int registeredCount = count(window, LongArticleRead.REGISTERED_CNT);
            final int anonymousCount = count(window, LongArticleRead.ANON_CNT);
            final int countedAnonymous = Math.min(anonymousCount, ANONYMOUS_REWARD_CAP);
            final int reward = Math.addExact(Math.multiplyExact(registeredCount, REGISTERED_REWARD),
                    Math.multiplyExact(countedAnonymous, ANONYMOUS_REWARD));
            return new SettlementContext(window, article, settlementId, now, registeredCount, anonymousCount,
                    countedAnonymous, reward);
        }

        private static int count(final JSONObject window, final String key) {
            if (!(window.opt(key) instanceof Number number)) throw new IllegalStateException("invalid reading counter");
            final int count = new BigDecimal(number.toString()).intValueExact();
            if (count < 0) throw new IllegalStateException("negative reading counter");
            return count;
        }

        private String articleId() {
            return window.optString(LongArticleRead.ARTICLE_ID);
        }

        private long windowStart() {
            return window.optLong(LongArticleRead.WINDOW_START);
        }
    }
}
