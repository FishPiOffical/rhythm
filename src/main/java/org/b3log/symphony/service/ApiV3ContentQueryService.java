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
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.model.Vote;
import org.b3log.symphony.repository.ReactionRepository;
import org.b3log.symphony.repository.RewardRepository;
import org.b3log.symphony.repository.VoteRepository;
import org.b3log.symphony.util.ApiV3Votes;
import org.b3log.symphony.util.Symphonys;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * API v3 内容读取模型，按显式传入的身份校验可见性。
 */
@Service
public class ApiV3ContentQueryService {
    @Inject
    private UserRepository userRepository;
    @Inject
    private VoteRepository interactionVotes;
    @Inject
    private RewardRepository interactionRewards;
    @Inject
    private ReactionRepository interactionReactions;
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

    /**
     * 资源不存在或不可见时统一返回 null，避免输出元数据。
     */
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
            } catch (final RepositoryException e) {
                throw new IllegalStateException("Paragraph title snapshot failed", e);
            }
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
        final List<JSONObject> items = new ArrayList<>();
        for (final JSONObject comment : rows) {
            items.add(commentDto(comment, article, viewer));
        }
        fillCommentInteractions(rows, items, viewer);
        for (final JSONObject item : items) {
            list.put(item);
        }
        return paginated(list, p, size, commentRepository.count(new Query().setFilter(filter)));
    }

    /**
     * 仅对本页正文可见的评论读取互动状态。票数和感谢数来自分页行，
     * 本人记录与表态汇总按评论 ID 集合批量查询，避免逐条读取用户资料。
     */
    public void fillCommentInteractions(final List<JSONObject> rows, final List<JSONObject> items,
                                        final JSONObject viewer) throws RepositoryException {
        final String actor = viewer == null ? "" : viewer.optString(Keys.OBJECT_ID);
        final Map<String, JSONObject> snapshots = new HashMap<>();
        for (final JSONObject item : items) {
            item.put("interactions", JSONObject.NULL);
            if (item.getBoolean("contentVisible")) {
                snapshots.put(item.getString("id"), new JSONObject());
            }
        }
        if (snapshots.isEmpty()) {
            return;
        }

        final var ids = new ArrayList<>(snapshots.keySet());
        final String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        final Map<String, Integer> ownVotes = new HashMap<>();
        final Set<String> ownThanks = new java.util.HashSet<>();
        if (!actor.isEmpty()) {
            final List<Object> voteArgs = new ArrayList<>(List.of(actor, Vote.DATA_TYPE_C_COMMENT));
            voteArgs.addAll(ids);
            for (final JSONObject row : interactionVotes.select("SELECT dataId,type FROM `" + interactionVotes.getName()
                    + "` WHERE userId=? AND dataType=? AND dataId IN (" + placeholders + ")", voteArgs.toArray())) {
                if (ownVotes.putIfAbsent(row.getString("dataId"), row.getInt("type")) != null) {
                    throw new IllegalStateException("duplicate stored comment votes");
                }
            }
            final List<Object> thankArgs = new ArrayList<>(List.of(actor, Reward.TYPE_C_COMMENT));
            thankArgs.addAll(ids);
            for (final JSONObject row : interactionRewards.select("SELECT dataId FROM `" + interactionRewards.getName()
                    + "` WHERE senderId=? AND type=? AND dataId IN (" + placeholders + ")", thankArgs.toArray())) {
                ownThanks.add(row.getString("dataId"));
            }
        }

        // 列表只返回表态计数和本人选择。单条接口仍提供表态用户明细。
        final Map<String, JSONArray> summaries = new HashMap<>();
        final Map<String, String> selections = new HashMap<>();
        final List<Object> reactionArgs = new ArrayList<>(List.of(actor, Reaction.TARGET_TYPE_COMMENT, Reaction.GROUP_EMOJI));
        reactionArgs.addAll(ids);
        final var reactions = interactionReactions.select("SELECT reactionTargetId,reactionValue,COUNT(*) AS total,"
                + "MAX(CASE WHEN reactionUserId=? THEN 1 ELSE 0 END) AS ownSelection FROM `" + interactionReactions.getName()
                + "` WHERE reactionTargetType=? AND reactionGroup=? AND reactionTargetId IN (" + placeholders
                + ") GROUP BY reactionTargetId,reactionValue ORDER BY reactionTargetId,MIN(oId)", reactionArgs.toArray());
        for (final JSONObject row : reactions) {
            final String id = row.getString("reactionTargetId");
            final String value = row.getString("reactionValue");
            if (!Reaction.isValidEmojiValue(value)) {
                continue;
            }
            final boolean selected = !actor.isEmpty() && row.getInt("ownSelection") != 0;
            if (selected) {
                selections.put(id, value);
            }
            summaries.computeIfAbsent(id, ignored -> new JSONArray()).put(new JSONObject()
                    .put("value", value).put("emoji", Reaction.emojiOf(value)).put("count", row.getLong("total"))
                    .put("selected", selected).put("users", new JSONArray()));
        }
        for (int i = 0; i < rows.size(); i++) {
            final JSONObject row = rows.get(i);
            final JSONObject item = items.get(i);
            final String id = item.getString("id");
            final JSONObject snapshot = snapshots.get(id);
            if (snapshot == null) {
                continue;
            }
            final JSONObject vote = ApiV3Votes.dto(id, ownVotes.getOrDefault(id, -1),
                    row.getInt(Comment.COMMENT_GOOD_CNT), row.getInt(Comment.COMMENT_BAD_CNT));
            final JSONObject thank = new JSONObject().put("id", id).put("thankCount", row.getInt(Comment.COMMENT_THANK_CNT));
            if (actor.isEmpty()) {
                vote.remove("vote");
            } else {
                final boolean thanked = ownThanks.contains(id);
                thank.put("thanked", thanked).put("canThank", !thanked && !actor.equals(row.optString(Comment.COMMENT_AUTHOR_ID)))
                        .put("thankPoint", item.getBoolean("anonymous") ? 0 : Symphonys.POINT_THANK_COMMENT);
            }
            snapshot.put("vote", vote).put("thanks", thank).put("reactions", new JSONObject()
                    .put("targetId", id).put("targetType", Reaction.TARGET_TYPE_COMMENT).put("groupType", Reaction.GROUP_EMOJI)
                    .put("currentUserReaction", selections.getOrDefault(id, ""))
                    .put("summary", summaries.getOrDefault(id, new JSONArray())));
            item.put("interactions", snapshot);
        }
    }

    /**
     * 查询可发现文章上的公开署名评论，先应用全部可见性条件，再分页。
     */
    public JSONObject userComments(final String owner, final JSONObject viewer, final int p, final int size) throws RepositoryException {
        final ApiV3ProfileQueries.Fragment fragment = ApiV3ProfileQueries.comments(commentRepository.getName(), articleRepository.getName(),
                userRepository.getName(), owner, null == viewer, UserExt.USER_STATUS_C_INVALID, UserExt.USER_STATUS_C_DEACTIVATED, Tag.TAG_TITLE_C_SANDBOX);
        final long total = commentRepository.select("SELECT COUNT(1) AS total" + fragment.sql(), fragment.args().toArray()).get(0).optLong("total");
        final List<Object> args = new ArrayList<>(fragment.args());
        args.add((p - 1) * size);
        args.add(size);
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
