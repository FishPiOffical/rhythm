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

import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3ArticlePolicy;

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.Filter;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Reward;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3ProfileQueries;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ApiV3ContentSnapshots;
import org.b3log.symphony.util.UserMentionCandidates;
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.Markdowns;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** V3 content read models, independent of session-dependent page enrichment. */
@Service
public class ApiV3ContentQueryService {
    @Inject
    private UserRepository userRepository;
    @Inject
    private ArticleRepository articleRepository;
    @Inject
    private CommentRepository commentRepository;
    @Inject
    private ApiV3ContentDtoService contentDtoService;
    @Inject
    private RewardQueryService rewardQueryService;
    @Inject
    private ApiV3DiscoveryQueryService discoveryQueryService;

    public JSONObject articles(final JSONObject viewer, final int p, final int size, final String sort,
                               final String type) throws RepositoryException {
        return discoveryQueryService.articles(viewer, p, size, sort, type, ApiV3DiscoveryQueryService.Selection.all());
    }

    /** Returns null for both missing and invisible resources, before serializing any metadata. */
    public JSONObject viewableArticle(final String id, final JSONObject viewer) throws RepositoryException {
        final JSONObject currentViewer = viewer == null ? null : currentViewer(viewer.optString(Keys.OBJECT_ID));
        if (viewer != null && isBlocked(currentViewer)) return null;
        final JSONObject article = currentArticle(id);
        if (null == article) return null;
        final Set<String> invited = null != currentViewer && Article.ARTICLE_TYPE_C_DISCUSSION == article.optInt(Article.ARTICLE_TYPE)
                ? UserMentionCandidates.parse(article.optString(Article.ARTICLE_CONTENT)) : Collections.emptySet();
        if (!ApiV3ContentUtil.canViewArticle(article, currentViewer, invited)) return null;
        final JSONObject author = ApiV3ContentSnapshots.author(article, Article.ARTICLE_AUTHOR_ID);
        return isBlocked(author) ? null : article;
    }

    public java.util.function.Function<String, LongArticleParagraphService.TitleSnapshot> paragraphTitleSnapshot() {
        return id -> {
            try {
                final JSONObject article = currentArticle(id);
                if (null == article) return new LongArticleParagraphService.TitleSnapshot(null, false);
                final boolean visible = ApiV3ContentUtil.canViewArticle(article, null, Collections.emptySet())
                        && !isBlocked(ApiV3ContentSnapshots.author(article, Article.ARTICLE_AUTHOR_ID));
                return new LongArticleParagraphService.TitleSnapshot(article.optString(Article.ARTICLE_TITLE), visible);
            } catch (final RepositoryException e) { throw new IllegalStateException("Paragraph title snapshot failed", e); }
        };
    }

    private JSONObject currentViewer(final String id) throws RepositoryException {
        final var rows = userRepository.select(ApiV3ContentSnapshots.viewer(userRepository.getName()), id);
        if (rows.size() > 1) throw new IllegalStateException("duplicate viewer");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private JSONObject currentArticle(final String id) throws RepositoryException {
        final var rows = articleRepository.select(ApiV3ContentSnapshots.article(articleRepository.getName(), userRepository.getName(), commentRepository.getName()), id,
                UserExt.USER_STATUS_C_INVALID, UserExt.USER_STATUS_C_DEACTIVATED);
        if (rows.size() > 1) throw new IllegalStateException("duplicate article");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public JSONObject currentComment(final String id) throws RepositoryException {
        final var rows = commentRepository.select(ApiV3ContentSnapshots.comment(commentRepository.getName(), userRepository.getName()), id);
        if (rows.size() > 1) throw new IllegalStateException("duplicate comment");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public JSONObject currentAuthor(final String id) throws RepositoryException {
        final var rows = userRepository.select(ApiV3ContentSnapshots.author(userRepository.getName()), id);
        if (rows.size() > 1) throw new IllegalStateException("duplicate author");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public JSONObject detail(final JSONObject article, final JSONObject viewer) {
        final JSONObject dto = contentDtoService.summary(article);
        final JSONObject policy = ApiV3ArticlePolicy.metadata(article);
        dto.put("commentable", policy.get("commentable")).put("statement", policy.get("statement"));
        final boolean thought = Article.ARTICLE_TYPE_C_THOUGHT == article.optInt(Article.ARTICLE_TYPE);
        final String content = article.optString(Article.ARTICLE_CONTENT);
        dto.put("content", content).put("contentFormat", thought ? "thought" : "markdown")
                .put("contentHtml", thought ? "" : render(content));
        final String viewerId = null == viewer ? "" : viewer.optString(Keys.OBJECT_ID);
        final boolean rewarded = !viewerId.isEmpty() && (viewerId.equals(article.optString(Article.ARTICLE_AUTHOR_ID))
                || rewardQueryService.isRewarded(viewerId, article.optString(Keys.OBJECT_ID), Reward.TYPE_C_ARTICLE));
        final int rewardPoint = article.optInt(Article.ARTICLE_REWARD_POINT);
        dto.put("rewardPoint", rewardPoint).put("rewarded", rewarded);
        dto.put("rewardContent", rewardPoint > 0 && rewarded ? article.optString(Article.ARTICLE_REWARD_CONTENT) : "");
        dto.put("rewardContentHtml", rewardPoint > 0 && rewarded ? render(article.optString(Article.ARTICLE_REWARD_CONTENT)) : "");
        if (Article.ARTICLE_TYPE_C_QNA == article.optInt(Article.ARTICLE_TYPE)) {
            final int offer = article.getInt(Article.ARTICLE_QNA_OFFER_POINT);
            if (offer < 0) throw new ApiV3Exception(503, "temporarily_unavailable", "问答状态暂不可用");
            dto.put("qna", new JSONObject().put("offerPoint", offer).put("accepted", ApiV3ContentSnapshots.questionAccepted(article)));
        } else dto.put("qna", JSONObject.NULL);
        return dto;
    }

    public JSONObject comments(final JSONObject article, final JSONObject viewer, final int p, final int size,
                               final String sort) throws RepositoryException {
        final Filter filter = CompositeFilterOperator.and(
                new PropertyFilter(Comment.COMMENT_ON_ARTICLE_ID, FilterOperator.EQUAL, article.optString(Keys.OBJECT_ID)),
                new PropertyFilter(Comment.COMMENT_TYPE, FilterOperator.EQUAL, Comment.COMMENT_TYPE_C_ARTICLE),
                new PropertyFilter(Comment.COMMENT_STATUS, FilterOperator.EQUAL, Comment.COMMENT_STATUS_C_VALID));
        final List<JSONObject> rows = commentRepository.select(ApiV3ContentSnapshots.commentPage(commentRepository.getName(), userRepository.getName(), sort),
                article.optString(Keys.OBJECT_ID), Comment.COMMENT_TYPE_C_ARTICLE, Comment.COMMENT_STATUS_C_VALID, (long) (p - 1) * size, size);
        final JSONArray list = new JSONArray();
        for (final JSONObject comment : rows) list.put(commentDto(comment, article, viewer));
        return paginated(list, p, size, commentRepository.count(new Query().setFilter(filter)));
    }

    /** Public, non-anonymous comments on discoverable articles; all visibility filters precede pagination. */
    public JSONObject userComments(final String owner, final JSONObject viewer, final int p, final int size) throws RepositoryException {
        final ApiV3ProfileQueries.Fragment fragment = ApiV3ProfileQueries.comments(commentRepository.getName(), articleRepository.getName(),
                userRepository.getName(), owner, null == viewer, UserExt.USER_STATUS_C_INVALID, UserExt.USER_STATUS_C_DEACTIVATED, Tag.TAG_TITLE_C_SANDBOX);
        final long total = commentRepository.select("SELECT COUNT(1) AS total" + fragment.sql(), fragment.args().toArray()).get(0).optLong("total");
        final List<Object> args = new ArrayList<>(fragment.args()); args.add((p - 1) * size); args.add(size);
        final List<JSONObject> comments = commentRepository.select("SELECT c.*" + fragment.sql()
                + " ORDER BY c.commentCreateTime DESC,c.oId DESC LIMIT ?,?", args.toArray());
        final JSONArray list = new JSONArray();
        final JSONObject author = currentAuthor(owner);
        for (final JSONObject comment : comments) {
            final JSONObject article = viewableArticle(comment.optString(Comment.COMMENT_ON_ARTICLE_ID), viewer);
            if (null == article) continue;
            list.put(commentDto(comment, article, viewer, author).put("type", comment.optInt(Comment.COMMENT_TYPE))
                    .put("article", contentDtoService.summary(article)));
        }
        return ApiV3Requests.pagination(list, p, size, total);
    }

    public JSONObject commentDto(final JSONObject comment, final JSONObject article, final JSONObject viewer) throws RepositoryException {
        final JSONObject author = ApiV3ContentSnapshots.hasAuthor(comment)
                ? ApiV3ContentSnapshots.author(comment, Comment.COMMENT_AUTHOR_ID)
                : currentAuthor(comment.optString(Comment.COMMENT_AUTHOR_ID));
        return commentDto(comment, article, viewer, author);
    }

    private JSONObject commentDto(final JSONObject comment, final JSONObject article, final JSONObject viewer, final JSONObject author) {
        final boolean visible = Comment.COMMENT_STATUS_C_VALID == comment.optInt(Comment.COMMENT_STATUS) && !isBlocked(author) && ApiV3ContentUtil.canViewCommentContent(comment, article, viewer);
        final String content = visible ? comment.optString(Comment.COMMENT_CONTENT) : "";
        return new JSONObject().put("id", comment.optString(Keys.OBJECT_ID))
                .put("articleId", article.optString(Keys.OBJECT_ID))
                .put("parentId", comment.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID))
                .put("content", content).put("contentHtml", visible ? render(content) : "")
                .put("contentVisible", visible).put("createdAt", comment.optLong(Comment.COMMENT_CREATE_TIME))
                .put("anonymous", Comment.COMMENT_ANONYMOUS_C_ANONYMOUS == comment.optInt(Comment.COMMENT_ANONYMOUS))
                .put("author", contentDtoService.author(author, Comment.COMMENT_ANONYMOUS_C_ANONYMOUS == comment.optInt(Comment.COMMENT_ANONYMOUS)));
    }

    private static boolean isBlocked(final JSONObject user) {
        return null == user || UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS)
                || UserExt.USER_STATUS_C_DEACTIVATED == user.optInt(UserExt.USER_STATUS);
    }

    private static String render(final String content) {
        return Markdowns.clean(Markdowns.toHTML(Emotions.convert(content)), "");
    }

    private static JSONObject paginated(final JSONArray list, final int p, final int size, final long total) {
        return new JSONObject().put("list", list).put("pagination", new JSONObject().put("p", p).put("size", size)
                .put("total", total).put("pageCount", (total + size - 1) / size));
    }
}
