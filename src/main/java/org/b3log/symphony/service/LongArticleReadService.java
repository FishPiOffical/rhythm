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
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.LongArticleRead;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.LongArticleReadAnonRepository;
import org.b3log.symphony.repository.LongArticleReadHistoryRepository;
import org.b3log.symphony.repository.LongArticleReadStatRepository;
import org.b3log.symphony.repository.LongArticleReadUserRepository;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Long article read service.
 *
 * @author Zephyr
 * @since 3.7.0
 */
@Service
public class LongArticleReadService {

    private static final Logger LOGGER = LogManager.getLogger(LongArticleReadService.class);

    private static final long WINDOW_MILLIS = TimeUnit.HOURS.toMillis(6);

    private static final int ANON_CAP = 100;

    @Inject
    private ArticleRepository articleRepository;

    @Inject
    private LongArticleReadStatRepository statRepository;

    @Inject
    private LongArticleReadHistoryRepository historyRepository;

    @Inject
    private LongArticleReadUserRepository userRepository;

    @Inject
    private LongArticleReadAnonRepository anonRepository;

    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * Record a read for long article if applicable (async).
     *
     * @param articleId article id
     * @param userId    user id (blank for anonymous)
     * @param ip        visitor ip
     * @param ua        visitor ua
     */
    public void record(final String articleId, final String userId, final String ip, final String ua) {
        Symphonys.EXECUTOR_SERVICE.submit(() -> {
            try {
                recordInternal(articleId, userId, ip, ua);
            } catch (final Exception e) {
                LOGGER.error("Record long article read failed", e);
            }
        });
    }

    /**
     * Get stat view object.
     *
     * @param articleId article id
     * @return stat json
     */
    public JSONObject getStat(final String articleId) {
        final JSONObject ret = buildEmptyStat();
        try {
            final Query query = new Query().setFilter(new PropertyFilter(LongArticleRead.ARTICLE_ID, FilterOperator.EQUAL, articleId)).setPageCount(1);
            final JSONObject stat = statRepository.getFirst(query);
            if (null == stat) {
                return ret;
            }
            ret.put(LongArticleRead.REGISTERED_UNSETTLED, stat.optInt(LongArticleRead.REGISTERED_UNSETTLED));
            ret.put(LongArticleRead.ANON_UNSETTLED, stat.optInt(LongArticleRead.ANON_UNSETTLED));
            ret.put(LongArticleRead.REGISTERED_TOTAL, stat.optInt(LongArticleRead.REGISTERED_TOTAL));
            ret.put(LongArticleRead.ANON_TOTAL, stat.optInt(LongArticleRead.ANON_TOTAL));
        } catch (final Exception e) {
            LOGGER.error("Load long article stat failed [articleId={}]", articleId, e);
        }
        return ret;
    }

    /**
     * Settle all long article stats.
     */
    public void settleAll() {
        settle(null);
    }

    /**
     * Settle specified article or all.
     *
     * @param articleId article id, null for all
     */
    public void settle(final String articleId) {
        try {
            final Query query = new Query();
            if (StringUtils.isNotBlank(articleId)) {
                query.setFilter(new PropertyFilter(LongArticleRead.ARTICLE_ID, FilterOperator.EQUAL, articleId));
            }
            final List<JSONObject> stats = statRepository.getList(query);
            final long now = System.currentTimeMillis();
            final long currentWindowStart = now / WINDOW_MILLIS * WINDOW_MILLIS;
            for (final JSONObject stat : stats) {
                try {
                    settleStat(stat, currentWindowStart, now);
                } catch (final Exception e) {
                    LOGGER.error("Settle long article stat failed [articleId={}]", stat.optString(LongArticleRead.ARTICLE_ID), e);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Settle long article stats failed", e);
        }
    }

    private void settleStat(final JSONObject stat, final long currentWindowStart, final long now) throws RepositoryException {
        final String articleId = stat.optString(LongArticleRead.ARTICLE_ID);
        final JSONObject article = articleRepository.get(articleId);
        if (null == article) {
            return;
        }
        final int registeredCnt = stat.optInt(LongArticleRead.REGISTERED_UNSETTLED, 0);
        final int anonCnt = stat.optInt(LongArticleRead.ANON_UNSETTLED, 0);
        final int cappedAnon = Math.min(anonCnt, ANON_CAP);
        final int reward = registeredCnt * 2 + cappedAnon;
        final long windowStart = stat.optLong(LongArticleRead.WINDOW_START);

        final Transaction tx = statRepository.beginTransaction();
        try {
            if (registeredCnt > 0 || anonCnt > 0) {
                final JSONObject history = new JSONObject();
                history.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
                history.put(LongArticleRead.ARTICLE_ID, articleId);
                history.put(LongArticleRead.WINDOW_START, windowStart);
                history.put(LongArticleRead.WINDOW_END, windowStart + WINDOW_MILLIS);
                history.put(LongArticleRead.REGISTERED_CNT, registeredCnt);
                history.put(LongArticleRead.ANON_CNT, anonCnt);
                history.put(LongArticleRead.ANON_CAPPED_CNT, cappedAnon);
                history.put(LongArticleRead.REWARD_POINT, reward);
                history.put(LongArticleRead.SETTLED_AT, now);
                historyRepository.add(history);

                if (reward > 0) {
                    final String authorId = article.optString(Article.ARTICLE_AUTHOR_ID);
                    pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, authorId,
                            Pointtransfer.TRANSFER_TYPE_C_LONG_ARTICLE_READ_REWARD, reward, articleId, now, "长文阅读奖励");
                }
            }

            stat.put(LongArticleRead.REGISTERED_UNSETTLED, 0);
            stat.put(LongArticleRead.ANON_UNSETTLED, 0);
            stat.put(LongArticleRead.WINDOW_START, currentWindowStart);
            stat.put(LongArticleRead.LAST_SETTLED_AT, now);
            stat.put(LongArticleRead.UPDATED_AT, now);
            statRepository.update(stat.optString(Keys.OBJECT_ID), stat);

            // cleanup anon dedupe of expired windows
            final Query cleanup = new Query().setFilter(CompositeFilterOperator.and(
                    new PropertyFilter(LongArticleRead.ARTICLE_ID, FilterOperator.EQUAL, articleId),
                    new PropertyFilter(LongArticleRead.WINDOW_START, FilterOperator.LESS_THAN, currentWindowStart)))
                    .setPageCount(1);
            anonRepository.remove(cleanup);

            tx.commit();
        } catch (final Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            if (e instanceof RepositoryException) {
                throw (RepositoryException) e;
            }
            throw new RepositoryException(e);
        }
    }

    private void recordInternal(final String articleId, final String userId, final String ip, final String ua) throws Exception {
        if (StringUtils.isBlank(articleId)) {
            return;
        }
        final JSONObject article = articleRepository.get(articleId);
        if (null == article || Article.ARTICLE_TYPE_C_LONG != article.optInt(Article.ARTICLE_TYPE)) {
            return;
        }

        final long now = System.currentTimeMillis();
        final long windowStart = now / WINDOW_MILLIS * WINDOW_MILLIS;
        if (StringUtils.isNotBlank(userId)) {
            final Query existed = new Query().setFilter(CompositeFilterOperator.and(
                    new PropertyFilter(LongArticleRead.ARTICLE_ID, FilterOperator.EQUAL, articleId),
                    new PropertyFilter(LongArticleRead.USER_ID, FilterOperator.EQUAL, userId)))
                    .setPageCount(1);
            if (userRepository.count(existed) > 0) {
                // already recorded
                ensureWindowSynced(articleId, windowStart, now);
                return;
            }
            final JSONObject record = new JSONObject();
            record.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
            record.put(LongArticleRead.ARTICLE_ID, articleId);
            record.put(LongArticleRead.USER_ID, userId);
            record.put(LongArticleRead.FIRST_READ_AT, now);
            userRepository.add(record);
            updateStat(articleId, windowStart, 1, 0, now);
        } else {
            final String readerHash = buildReaderHash(ip, ua);
            if (StringUtils.isBlank(readerHash)) {
                return;
            }
            final Query existed = new Query().setFilter(CompositeFilterOperator.and(
                    new PropertyFilter(LongArticleRead.ARTICLE_ID, FilterOperator.EQUAL, articleId),
                    new PropertyFilter(LongArticleRead.WINDOW_START, FilterOperator.EQUAL, windowStart),
                    new PropertyFilter(LongArticleRead.READER_HASH, FilterOperator.EQUAL, readerHash)))
                    .setPageCount(1);
            if (anonRepository.count(existed) > 0) {
                ensureWindowSynced(articleId, windowStart, now);
                return;
            }
            final JSONObject record = new JSONObject();
            record.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
            record.put(LongArticleRead.ARTICLE_ID, articleId);
            record.put(LongArticleRead.WINDOW_START, windowStart);
            record.put(LongArticleRead.READER_HASH, readerHash);
            record.put(LongArticleRead.FIRST_READ_AT, now);
            anonRepository.add(record);
            updateStat(articleId, windowStart, 0, 1, now);
        }
    }

    private void updateStat(final String articleId, final long windowStart, final int registeredInc, final int anonInc, final long now) throws RepositoryException {
        final Transaction tx = statRepository.beginTransaction();
        try {
            final Query query = new Query().setFilter(new PropertyFilter(LongArticleRead.ARTICLE_ID, FilterOperator.EQUAL, articleId)).setPageCount(1);
            JSONObject stat = statRepository.getFirst(query);
            if (null == stat) {
                stat = new JSONObject();
                stat.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
                stat.put(LongArticleRead.ARTICLE_ID, articleId);
                stat.put(LongArticleRead.WINDOW_START, windowStart);
                stat.put(LongArticleRead.REGISTERED_UNSETTLED, registeredInc);
                stat.put(LongArticleRead.ANON_UNSETTLED, anonInc);
                stat.put(LongArticleRead.REGISTERED_TOTAL, registeredInc);
                stat.put(LongArticleRead.ANON_TOTAL, anonInc);
                stat.put(LongArticleRead.LAST_SETTLED_AT, 0L);
                stat.put(LongArticleRead.CREATED_AT, now);
                stat.put(LongArticleRead.UPDATED_AT, now);
                statRepository.add(stat);
            } else {
                if (stat.optLong(LongArticleRead.WINDOW_START) != windowStart) {
                    stat.put(LongArticleRead.WINDOW_START, windowStart);
                    stat.put(LongArticleRead.REGISTERED_UNSETTLED, 0);
                    stat.put(LongArticleRead.ANON_UNSETTLED, 0);
                }
                stat.put(LongArticleRead.REGISTERED_UNSETTLED, stat.optInt(LongArticleRead.REGISTERED_UNSETTLED) + registeredInc);
                stat.put(LongArticleRead.ANON_UNSETTLED, stat.optInt(LongArticleRead.ANON_UNSETTLED) + anonInc);
                stat.put(LongArticleRead.REGISTERED_TOTAL, stat.optInt(LongArticleRead.REGISTERED_TOTAL) + registeredInc);
                stat.put(LongArticleRead.ANON_TOTAL, stat.optInt(LongArticleRead.ANON_TOTAL) + anonInc);
                stat.put(LongArticleRead.UPDATED_AT, now);
                statRepository.update(stat.optString(Keys.OBJECT_ID), stat);
            }
            tx.commit();
        } catch (final RepositoryException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    private void ensureWindowSynced(final String articleId, final long windowStart, final long now) {
        try {
            final Query query = new Query().setFilter(new PropertyFilter(LongArticleRead.ARTICLE_ID, FilterOperator.EQUAL, articleId)).setPageCount(1);
            final JSONObject stat = statRepository.getFirst(query);
            if (null == stat) {
                return;
            }
            if (stat.optLong(LongArticleRead.WINDOW_START) != windowStart) {
                stat.put(LongArticleRead.WINDOW_START, windowStart);
                stat.put(LongArticleRead.REGISTERED_UNSETTLED, 0);
                stat.put(LongArticleRead.ANON_UNSETTLED, 0);
                stat.put(LongArticleRead.UPDATED_AT, now);
                statRepository.update(stat.optString(Keys.OBJECT_ID), stat);
            }
        } catch (final Exception e) {
            LOGGER.error("Sync long article stat window failed [articleId={}]", articleId, e);
        }
    }

    private JSONObject buildEmptyStat() {
        final JSONObject ret = new JSONObject();
        ret.put(LongArticleRead.REGISTERED_UNSETTLED, 0);
        ret.put(LongArticleRead.ANON_UNSETTLED, 0);
        ret.put(LongArticleRead.REGISTERED_TOTAL, 0);
        ret.put(LongArticleRead.ANON_TOTAL, 0);
        return ret;
    }

    private String buildReaderHash(final String ip, final String ua) {
        if (StringUtils.isBlank(ip) && StringUtils.isBlank(ua)) {
            return null;
        }
        final String content = StringUtils.defaultString(ip) + "|" + StringUtils.defaultString(ua);
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] digest = md.digest(content.getBytes("UTF-8"));
            final StringBuilder sb = new StringBuilder();
            for (final byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (final Exception e) {
            LOGGER.error("Hash reader failed", e);
            return null;
        }
    }
}
