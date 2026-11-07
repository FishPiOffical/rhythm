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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ApiV3Votes;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicReference;

/** Bearer-explicit comment votes: projected reads and idempotent set/clear mutations. */
@Service
public class ApiV3CommentVoteService {
    @Inject private CommentRepository comments;
    @Inject private VoteRepository votes;
    @Inject private UserRepository users;
    @Inject private CommentThankMgmtService targets;
    @Inject private CommentVoteMgmtService mutations;
    @Inject private ArticleMgmtService articleWrites;
    @Inject private ApiV3IdempotencyMgmtService idempotency;
    @Inject private ApiV3ContentQueryService visibility;

    public JSONObject read(final JSONObject viewer, final String id) throws Exception {
        final String actor = actor(viewer);
        final var rows = comments.select(ApiV3Votes.commentReadSql(comments.getName(), votes.getName()), actor, Vote.DATA_TYPE_C_COMMENT, id);
        if (rows.isEmpty()) throw missing();
        final var comment = rows.getFirst();
        final var article = visibility.viewableArticle(comment.optString(Comment.COMMENT_ON_ARTICLE_ID), viewer);
        if (null == article) throw missing();
        readable(viewer, article, comment);
        if (rows.size() != 1) throw new IllegalStateException("duplicate stored comment votes");
        final int current = !comment.has("currentVote") || comment.isNull("currentVote") ? -1 : comment.getInt("currentVote");
        return ApiV3Votes.dto(id, current, comment.optInt(Comment.COMMENT_GOOD_CNT), comment.optInt(Comment.COMMENT_BAD_CNT));
    }

    public JSONObject set(final JSONObject viewer, final String id, final int choice, final String key) throws Exception {
        final String actor = actor(viewer);
        final var normalized = new JSONObject().put("vote", ApiV3Votes.value(choice));
        final var expected = targets.target(id);
        final var result = new AtomicReference<CommentVoteMgmtService.Result>();
        final var outcome = articleWrites.withPublicationLock(actor, () -> idempotency.executeWithOutcome(actor,
                "comments.vote:" + id, key, normalized, () -> {
            if (null == expected) throw missing();
            try {
                final var mutation = mutations.setInCurrentTransaction(actor, expected, choice, false, true, (voter, article, comment) -> {
                    if (null == visibility.viewableArticle(article.optString(Keys.OBJECT_ID), voter)) throw missing();
                    readable(voter, article, comment);
                });
                result.set(mutation);
                return ApiV3Votes.dto(id, mutation.current(), mutation.upCount(), mutation.downCount());
            } catch (final CommentVoteMgmtService.Rejected e) {
                if ("not_found".equals(e.code)) throw missing();
                if ("forbidden".equals(e.code)) throw new ApiV3Exception(403, e.code, "没有操作权限");
                if ("target_changed".equals(e.code)) throw new ApiV3Exception(409, e.code, "评论已变化，请重试");
                throw new ApiV3Exception(422, e.code, "不能对本人评论投票");
            }
        }), execution -> { if (execution.applied()) mutations.afterVote(actor, result.get()); });
        return outcome.result();
    }

    private void readable(final JSONObject viewer, final JSONObject article, final JSONObject comment) throws Exception {
        final var author = users.get(comment.optString(Comment.COMMENT_AUTHOR_ID));
        if (comment.optInt(Comment.COMMENT_STATUS) != Comment.COMMENT_STATUS_C_VALID
                || null == author || author.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID
                || !ApiV3ContentUtil.canViewCommentContent(comment, article, viewer)) throw missing();
    }
    private static String actor(final JSONObject viewer) {
        if (null == viewer || viewer.optString(Keys.OBJECT_ID).isBlank()) throw new ApiV3Exception(401, "unauthorized", "需要登录");
        return viewer.getString(Keys.OBJECT_ID);
    }
    private static ApiV3Exception missing() { return new ApiV3Exception(404, "not_found", "资源不存在或不可查看"); }
}
