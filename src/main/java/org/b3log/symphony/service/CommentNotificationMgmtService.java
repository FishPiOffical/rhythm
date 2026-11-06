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
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.AccountPairLocks;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ArticlePublicationCaches;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Delayed comment events must recheck and insert under the same current target lock as deletion. */
@Service
public class CommentNotificationMgmtService {
    @Inject private CommentRepository comments;
    @Inject private ArticleRepository articles;
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private CommentThankMgmtService targets;
    @Inject private ArticleMgmtService articleWrites;
    @Inject private ApiV3ContentQueryService visibility;
    @Inject private NotificationMgmtService notifications;

    public boolean send(final String recipient, final String articleId, final String commentId, final int type) throws Exception {
        if (null == recipient || recipient.isBlank()) return false;
        final var target = targets.target(commentId);
        if (null == target || !articleId.equals(target.article())) return false;
        final String articleAuthor = articles.getCommentPublicationAuthor(articleId);
        if (articleAuthor.isBlank()) return false;
        return articleWrites.withPublicationLock(target.author(), () -> {
            final Transaction transaction = comments.beginTransaction();
            try {
                final boolean inserted = sendInCurrentTransaction(recipient, target, articleAuthor, type);
                transaction.commit(); return inserted;
            } finally { if (transaction.isActive()) transaction.rollback(); }
        }, inserted -> {
            if (inserted) {
                try { notifications.publishReadStateRefresh(recipient); }
                catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Refresh committed comment notification failed", e); }
            }
        });
    }

    public boolean sendInCurrentTransaction(final String recipient, final CommentThankMgmtService.Target target,
                                             final String articleAuthor, final int type) throws Exception {
        if (!Set.of(Notification.DATA_TYPE_C_COMMENT, Notification.DATA_TYPE_C_AT, Notification.DATA_TYPE_C_COMMENTED,
                Notification.DATA_TYPE_C_REPLY, Notification.DATA_TYPE_C_FOLLOWING_ARTICLE_COMMENT).contains(type)) throw new IllegalArgumentException("invalid comment notification type");
        if (!comments.hasTransactionBegun() || !ArticlePublicationCaches.active()
                || !AccountPairLocks.lock(target.author(), Pointtransfer.ID_C_SYS).isHeldByCurrentThread()) throw new IllegalStateException("comment notification transaction and account lock required");
        if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
        for (final String user : new TreeSet<>(List.of(target.author(), articleAuthor, recipient))) users.getForUpdate(user);
        final var viewer = users.getForUpdate(recipient);
        if (null == viewer || viewer.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) return false;
        final var author = users.getForUpdate(target.author());
        if (null == author || author.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) return false;
        final var article = articles.getForUpdate(target.article());
        final var comment = comments.get(target.id());
        if (null == article || null == comment || !articleAuthor.equals(article.optString(Article.ARTICLE_AUTHOR_ID))
                || !target.article().equals(comment.optString(Comment.COMMENT_ON_ARTICLE_ID))
                || !target.author().equals(comment.optString(Comment.COMMENT_AUTHOR_ID))
                || comment.optInt(Comment.COMMENT_STATUS) != Comment.COMMENT_STATUS_C_VALID) return false;
        if (null == visibility.viewableArticle(article.optString(Keys.OBJECT_ID), viewer)
                || !ApiV3ContentUtil.canViewCommentContent(comment, article, viewer)) return false;
        return notifications.addCommentTargetNotificationInCurrentTransaction(recipient, target.id(), type);
    }
}
