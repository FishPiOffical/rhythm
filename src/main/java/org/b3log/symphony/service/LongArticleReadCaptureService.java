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

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.json.JSONObject;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.b3log.symphony.model.LongArticleReadClaim;
import org.b3log.symphony.model.LongArticleReadWindowIncrement;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.LongArticleReadUserRepository;
import org.b3log.symphony.repository.LongArticleReadWindowRepository;

import java.util.concurrent.TimeUnit;

/** 持久化长篇阅读去重与窗口计数。 */
@Service
public class LongArticleReadCaptureService {

    private static final long WINDOW_MILLIS = TimeUnit.HOURS.toMillis(6);

    @Inject
    private ArticleRepository articleRepository;

    @Inject
    private LongArticleReadUserRepository userRepository;

    @Inject
    private LongArticleReadWindowRepository windowRepository;

    @Inject
    private UserRepository readers;

    protected long now() { return System.currentTimeMillis(); }

    /** Current-reader eligibility, per-window claim and counter are one atomic operation. */
    public boolean recordCurrent(final String articleId, final String actor) {
        if (articleId == null || !articleId.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}"))
            throw new ApiV3Exception(401, "unauthorized", "需要登录");
        Transaction transaction = null;
        try {
            if (!windowRepository.hasTransactionBegun()) transaction = ApiV3Transactions.strict(windowRepository.beginTransaction());
            final boolean recorded = ApiV3TransactionSavepoint.run(() -> recordCurrentInTransaction(articleId, actor));
            if (transaction != null) transaction.commit();
            return recorded;
        } catch (final Exception error) {
            rollback(transaction);
            if (error instanceof ApiV3Exception policy) throw policy;
            LogManager.getLogger(getClass()).error("Long article read registration failed", error);
            throw new ApiV3Exception(503, "temporarily_unavailable", "阅读登记暂不可用");
        }
    }

    private boolean recordCurrentInTransaction(final String articleId, final String actor) throws Exception {
        // Resolve a lock hint only. Never treat an earlier article/user entity as authorization.
        final String author = articleRepository.getCommentPublicationAuthor(articleId);
        if (!author.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.notFound();
        JSONObject reader = null, writer = null;
        for (final String id : new TreeSet<>(List.of(actor, author))) {
            final JSONObject user = readers.getForUpdate(id);
            if (id.equals(actor)) reader = user;
            if (id.equals(author)) writer = user;
        }
        if (reader == null || !actor.equals(reader.optString("oId"))
                || reader.optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID)
            throw ApiV3Requests.notFound();
        if (writer == null || !author.equals(writer.optString("oId"))
                || (writer.optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_VALID
                && writer.optInt(UserExt.USER_STATUS, -1) != UserExt.USER_STATUS_C_INVALID_LOGIN))
            throw ApiV3Requests.notFound();
        final JSONObject article = articleRepository.getForUpdate(articleId);
        if (article == null || !author.equals(article.optString(Article.ARTICLE_AUTHOR_ID))
                || article.optInt(Article.ARTICLE_STATUS, -1) != Article.ARTICLE_STATUS_C_VALID
                || article.optInt(Article.ARTICLE_TYPE, -1) != Article.ARTICLE_TYPE_C_LONG
                || article.optInt(Article.ARTICLE_ANONYMOUS, -1) != Article.ARTICLE_ANONYMOUS_C_PUBLIC
                || article.optInt(Article.ARTICLE_SHOW_IN_LIST, -1) != Article.ARTICLE_SHOW_IN_LIST_C_YES
                || Tag.TAG_TITLE_C_SANDBOX.equals(article.optString(Article.ARTICLE_TAGS))
                || article.optString(Article.ARTICLE_TAGS).startsWith("B3log")
                || !ApiV3ContentUtil.canViewArticle(article, reader, Set.of())) throw ApiV3Requests.notFound();
        if (actor.equals(author)) throw new ApiV3Exception(409, "own_article", "本人阅读不计入统计");
        // Calculate after waiting for the current user/article locks, so the window cannot age while queued.
        final long time = now();
        if (time <= 0) throw new IllegalStateException("invalid capture clock");
        final long windowStart = time / WINDOW_MILLIS * WINDOW_MILLIS;
        if (!userRepository.claim(new LongArticleReadClaim(Ids.genTimeMillisId(), articleId, actor, windowStart, time))) return false;
        if (!windowRepository.increment(new LongArticleReadWindowIncrement(Ids.genTimeMillisId(), articleId, windowStart, 1, 0, time)))
            throw new RepositoryException("长篇阅读窗口已进入结算");
        return true;
    }

    /** Classic view and Markdown callers retain their output for ineligible readers. */
    public void record(final LongArticleReadCaptureRequest request) {
        try { recordCurrent(request.articleId(), request.userId()); }
        catch (final ApiV3Exception error) {
            if (error.status != 401 && error.status != 404
                    && !(error.status == 409 && "own_article".equals(error.code))) throw error;
        }
    }

    private void rollback(final Transaction transaction) {
        if (transaction != null && transaction.isActive()) transaction.rollback();
    }
}
