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
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.LongArticleReadClaim;
import org.b3log.symphony.model.LongArticleReadWindowIncrement;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.LongArticleReadAnonRepository;
import org.b3log.symphony.repository.LongArticleReadUserRepository;
import org.b3log.symphony.repository.LongArticleReadWindowRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    private LongArticleReadAnonRepository anonymousRepository;

    @Inject
    private LongArticleReadWindowRepository windowRepository;

    public void record(final LongArticleReadCaptureRequest request) {
        if (!isLongArticle(request.articleId())) {
            return;
        }
        final long now = System.currentTimeMillis();
        recordAt(request, now, now / WINDOW_MILLIS * WINDOW_MILLIS);
    }

    private boolean isLongArticle(final String articleId) {
        if (StringUtils.isBlank(articleId)) {
            return false;
        }
        try {
            final var article = articleRepository.get(articleId);
            return null != article && Article.ARTICLE_TYPE_C_LONG == article.optInt(Article.ARTICLE_TYPE);
        } catch (final RepositoryException e) {
            throw new IllegalStateException("读取长篇文章失败", e);
        }
    }

    private void recordAt(final LongArticleReadCaptureRequest request, final long now, final long windowStart) {
        final Transaction transaction = windowRepository.hasTransactionBegun() ? null : windowRepository.beginTransaction();
        try {
            final boolean claimed = StringUtils.isNotBlank(request.userId())
                    ? userRepository.claim(new LongArticleReadClaim(Ids.genTimeMillisId(), request.articleId(),
                    request.userId(), windowStart, now))
                    : claimAnonymous(request, windowStart, now);
            if (!claimed) {
                commit(transaction);
                return;
            }
            final int registeredIncrement = StringUtils.isNotBlank(request.userId()) ? 1 : 0;
            final int anonymousIncrement = registeredIncrement == 0 ? 1 : 0;
            if (!windowRepository.increment(new LongArticleReadWindowIncrement(Ids.genTimeMillisId(),
                    request.articleId(), windowStart, registeredIncrement, anonymousIncrement, now))) {
                throw new RepositoryException("长篇阅读窗口已进入结算");
            }
            commit(transaction);
        } catch (final Exception e) {
            rollback(transaction);
            throw new IllegalStateException("记录长篇阅读失败", e);
        }
    }

    private boolean claimAnonymous(final LongArticleReadCaptureRequest request, final long windowStart, final long now)
            throws RepositoryException {
        final String readerHash = hashReader(request.ip(), request.userAgent());
        if (StringUtils.isBlank(readerHash)) {
            return false;
        }
        return anonymousRepository.claim(new LongArticleReadClaim(Ids.genTimeMillisId(), request.articleId(),
                readerHash, windowStart, now));
    }

    private String hashReader(final String ip, final String ua) {
        if (StringUtils.isBlank(ip) && StringUtils.isBlank(ua)) {
            return null;
        }
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final String content = StringUtils.defaultString(ip) + '|' + StringUtils.defaultString(ua);
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private void rollback(final Transaction transaction) {
        if (null != transaction && transaction.isActive()) {
            transaction.rollback();
        }
    }

    private void commit(final Transaction transaction) {
        if (null != transaction && transaction.isActive()) {
            transaction.commit();
        }
    }
}
