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
import org.b3log.latke.model.User;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ArticleSubmissionPolicy;
import org.b3log.symphony.util.ArticleSubmission;
import org.json.JSONObject;
import java.time.ZoneId;
import java.util.ArrayList;

/** Submission policy and weekly credit join the article's transaction. HTTP validation remains at each boundary. */
@Service
public class ArticleSubmissionMgmtService {
    @Inject private ArticleMgmtService articles;
    @Inject private ArticleRepository repository;
    @Inject private OptionRepository options;
    @Inject private UserRepository users;
    @Inject private PointtransferMgmtService points;

    public String submit(final JSONObject request) throws ServiceException {
        try {
            final var publication = articles.withPublicationLock(request.optString(Article.ARTICLE_AUTHOR_ID), () -> {
                final Transaction tx = repository.beginTransaction();
                try {
                    final var result = submitInCurrentTransaction(request);
                    tx.commit();
                    return result;
                } finally { if (tx.isActive()) tx.rollback(); }
            }, articles::afterArticlePublished);
            return publication.article().optString(Keys.OBJECT_ID);
        } catch (final ServiceException e) { throw e; }
        catch (final ArticleSubmissionPolicy.Rejected e) { throw new ServiceException(e.getMessage()); }
        catch (final Exception e) { throw new ServiceException(e); }
    }

    public ArticleMgmtService.Publication submitInCurrentTransaction(final JSONObject request) throws Exception {
        final String actor = request.optString(Article.ARTICLE_AUTHOR_ID);
        final var result = ArticleSubmission.apply(new ArticleSubmission.Store<ArticleMgmtService.Publication>() {
            public boolean inTransaction() { return repository.hasTransactionBegun(); }
            public void lockPublicationState() throws Exception {
                // Same ordering as addArticleInCurrentTransaction, including legacy and future v3 submitters.
                if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
            }
            public ArticleSubmissionPolicy.Submission submission() throws Exception {
                final JSONObject author = users.getForUpdate(actor);
                if (null == author) throw new ServiceException("用户不存在");
                String tags = request.optString(Article.ARTICLE_TAGS);
                if (!Role.ROLE_ID_C_ADMIN.equals(author.optString(User.USER_ROLE))) tags = articles.filterReservedTags(tags);
                if (Article.ARTICLE_TYPE_C_DISCUSSION == request.optInt(Article.ARTICLE_TYPE) && tags.isBlank()) tags = "机要";
                request.put(Article.ARTICLE_TAGS, tags);
                return new ArticleSubmissionPolicy.Submission(!author.optString("userPhone").isEmpty(),
                        request.optString(Article.ARTICLE_TITLE), request.optString(Article.ARTICLE_CONTENT), tags);
            }
            public ArticleSubmissionPolicy.History history(final ArticleSubmissionPolicy.Submission submission) throws Exception {
                return new ArticleSubmissionPolicy.History(repository.latestPublicArticleIdForUpdate(actor),
                        ArticleSubmissionPolicy.weeklyTitleCandidate(submission.title()) && null != repository.getByTitleForUpdate(submission.title()));
            }
            public String creditWeekly(final int amount, final long now) {
                return points.transferInCurrentTransaction(Pointtransfer.ID_C_SYS, actor,
                        Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_SEND_TGIF, amount, "", now, "", PointtransferSource.EMPTY, false);
            }
            public ArticleMgmtService.Publication publish() throws Exception { return articles.addArticleInCurrentTransaction(request); }
        }, System::currentTimeMillis, ZoneId.systemDefault(), Latkes.RuntimeMode.DEVELOPMENT == Latkes.getRuntimeMode());
        final var publication = result.publication();
        final String weeklyTransfer = result.weeklyTransferId();
        if (null == weeklyTransfer) return publication;
        final var transfers = new ArrayList<>(publication.transferIds());
        transfers.add(weeklyTransfer);
        // Redundant indexing records both account IDs, independent of transfer direction.
        return new ArticleMgmtService.Publication(publication.article(), publication.author(), publication.request(), transfers);
    }
}
