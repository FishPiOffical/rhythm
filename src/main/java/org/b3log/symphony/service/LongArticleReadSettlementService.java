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

    public void settleAll() {
        settle(null);
    }

    public void settle(final String articleId) {
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
        final JSONObject article = articleRepository.get(articleId);
        if (null == article) {
            throw new RepositoryException("结算目标文章不存在");
        }
        final Transaction transaction = windowRepository.beginTransaction();
        try {
            return settleClaimedWindow(new SettlementAttempt(window, article, now, transaction));
        } catch (final Exception e) {
            rollback(transaction);
            throw e;
        }
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
        final JSONObject lockedWindow = windowRepository.get(window.optString(Keys.OBJECT_ID));
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
                context.reward(), context.articleId(), context.now(), "长文阅读奖励");
        if (StringUtils.isBlank(transferId)) {
            throw new RepositoryException("长篇阅读积分转账失败");
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
            final int registeredCount = window.optInt(LongArticleRead.REGISTERED_CNT);
            final int anonymousCount = window.optInt(LongArticleRead.ANON_CNT);
            final int countedAnonymous = Math.min(anonymousCount, ANONYMOUS_REWARD_CAP);
            final int reward = registeredCount * REGISTERED_REWARD + countedAnonymous * ANONYMOUS_REWARD;
            return new SettlementContext(window, article, settlementId, now, registeredCount, anonymousCount,
                    countedAnonymous, reward);
        }

        private String articleId() {
            return window.optString(LongArticleRead.ARTICLE_ID);
        }

        private long windowStart() {
            return window.optLong(LongArticleRead.WINDOW_START);
        }
    }
}
