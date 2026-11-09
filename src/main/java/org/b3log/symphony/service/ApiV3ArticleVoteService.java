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
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 按 Bearer 身份检查文章可见性，设置或清除投票各使用一个幂等事务。
 */
@Service
public class ApiV3ArticleVoteService {
    @Inject
    private ArticleRepository articles;
    @Inject
    private VoteRepository votes;
    @Inject
    private UserRepository users;
    @Inject
    private VoteMgmtService mutations;
    @Inject
    private ArticleMgmtService articleWrites;
    @Inject
    private ApiV3IdempotencyMgmtService idempotency;
    @Inject
    private ApiV3ContentQueryService visibility;

    public JSONObject read(final JSONObject viewer, final String id) throws Exception {
        final String actor = actor(viewer);
        final var rows = articles.select(ApiV3Votes.articleReadSql(articles.getName(), votes.getName()), actor, Vote.DATA_TYPE_C_ARTICLE, id);
        if (rows.isEmpty()) throw missing();
        final var article = rows.getFirst();
        readable(viewer, article);
        if (rows.size() != 1) throw new IllegalStateException("duplicate stored article votes");
        final int current = !article.has("currentVote") || article.isNull("currentVote") ? -1 : article.getInt("currentVote");
        return ApiV3Votes.dto(id, current, article.optInt(Article.ARTICLE_GOOD_CNT), article.optInt(Article.ARTICLE_BAD_CNT));
    }

    public JSONObject set(final JSONObject viewer, final String id, final int choice, final String key) throws Exception {
        final String actor = actor(viewer);
        final var normalized = new JSONObject().put("vote", ApiV3Votes.value(choice));
        final String expectedAuthor = articles.getCommentPublicationAuthor(id);
        final var changed = new AtomicReference<VoteMgmtService.Result>();
        final var outcome = articleWrites.withPublicationLock(actor, () -> idempotency.executeWithOutcome(actor,
                "articles.vote:" + id, key, normalized, () -> {
                    if (expectedAuthor.isBlank()) throw missing();
                    try {
                        final var result = mutations.setInCurrentTransaction(actor, id, expectedAuthor, choice, false, true,
                                (voter, article) -> readable(voter, article));
                        changed.set(result);
                        return ApiV3Votes.dto(id, result.current(), result.upCount(), result.downCount());
                    } catch (final VoteMgmtService.Rejected e) {
                        if ("not_found".equals(e.code)) throw missing();
                        if ("forbidden".equals(e.code)) throw new ApiV3Exception(403, e.code, "没有操作权限");
                        if ("target_changed".equals(e.code)) throw new ApiV3Exception(409, e.code, "文章已变化，请重试");
                        throw new ApiV3Exception(422, e.code, "不能对本人文章投票");
                    }
                }), execution -> {
            if (execution.applied())
                mutations.afterVote(actor, changed.get());
        });
        final JSONObject receipt = outcome.result();
        // A replay confirms the original vote, while the nested state reflects the current vote.
        try {
            final JSONObject current = outcome.applied() ? new JSONObject(receipt.toString()) : read(viewer, id);
            receipt.put("interaction", current);
        } catch (final ApiV3Exception e) {
            if (e.status != 403 && e.status != 404) throw e;
            receipt.put("interaction", JSONObject.NULL);
        }
        return receipt;
    }

    private void readable(final JSONObject viewer, final JSONObject article) throws Exception {
        final var author = users.get(article.optString(Article.ARTICLE_AUTHOR_ID));
        if ((article.optInt(Article.ARTICLE_STATUS) != Article.ARTICLE_STATUS_C_VALID && article.optInt(Article.ARTICLE_STATUS) != Article.ARTICLE_STATUS_C_LOCKED)
                || null == author || author.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID
                || null == visibility.viewableArticle(article.optString(Keys.OBJECT_ID), viewer)) throw missing();
    }

    private static String actor(final JSONObject viewer) {
        if (null == viewer || viewer.optString(Keys.OBJECT_ID).isBlank())
            throw new ApiV3Exception(401, "unauthorized", "需要登录");
        return viewer.getString(Keys.OBJECT_ID);
    }

    private static ApiV3Exception missing() {
        return new ApiV3Exception(404, "not_found", "资源不存在或不可查看");
    }
}
