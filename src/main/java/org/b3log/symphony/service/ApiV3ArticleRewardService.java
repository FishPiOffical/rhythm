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
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 已认证用户的文章感谢和购买。回执不含付费正文，读取详情时重新校验权限。
 */
@Service
public class ApiV3ArticleRewardService {
    @Inject
    private ArticleRepository articles;
    @Inject
    private UserRepository users;
    @Inject
    private RewardRepository rewards;
    @Inject
    private ArticleRewardMgmtService mutations;
    @Inject
    private ArticleMgmtService articleWrites;
    @Inject
    private ApiV3IdempotencyMgmtService idempotency;
    @Inject
    private ApiV3ContentQueryService visibility;
    @Inject
    private RoleQueryService roles;

    public JSONObject read(final JSONObject viewer, final String id, final ArticleRewardMgmtService.Kind kind) throws Exception {
        final String actor = actor(viewer);
        final var rows = articles.select("SELECT oId,articleAuthorId,articleStatus,articleAnonymous,articleThankCnt,articleRewardPoint FROM `" + articles.getName() + "` WHERE oId=? LIMIT 1", id);
        if (rows.isEmpty()) throw missing();
        final var article = rows.getFirst();
        readable(viewer, article);
        final boolean recorded = !rewards.select("SELECT oId FROM `" + rewards.getName() + "` WHERE senderId=? AND dataId=? AND `type`=? LIMIT 1",
                actor, id, ArticleRewardMgmtService.rewardType(kind)).isEmpty();
        if (kind == ArticleRewardMgmtService.Kind.THANK) {
            final int count = article.optInt(Article.ARTICLE_THANK_CNT);
            if (count < 0) throw new IllegalStateException("negative stored article thank count");
            return new JSONObject().put("id", id).put("thanked", recorded).put("thankCount", count)
                    .put("thankPoint", article.optInt(Article.ARTICLE_ANONYMOUS) == Article.ARTICLE_ANONYMOUS_C_ANONYMOUS ? 0 : Pointtransfer.TRANSFER_SUM_C_ARTICLE_THANK);
        }
        return new JSONObject().put("id", id).put("rewarded", recorded || actor.equals(article.optString(Article.ARTICLE_AUTHOR_ID)))
                .put("rewardPoint", article.optInt(Article.ARTICLE_REWARD_POINT));
    }

    public JSONObject apply(final JSONObject viewer, final String id, final ArticleRewardMgmtService.Kind kind, final String key) throws Exception {
        final String actor = actor(viewer);
        permission(viewer, kind);
        final String expectedAuthor = articles.getCommentPublicationAuthor(id);
        final var destinations = expectedAuthor.isBlank() ? List.of(Pointtransfer.ID_C_SYS) : List.of(Pointtransfer.ID_C_SYS, expectedAuthor);
        final var applied = new AtomicReference<ArticleRewardMgmtService.Result>();
        final var outcome = articleWrites.withPublicationLocks(actor, destinations,
                () -> idempotency.executeWithOutcome(actor, kind == ArticleRewardMgmtService.Kind.THANK ? "articles.thank:" + id : "articles.reward:" + id,
                        key, new JSONObject(), () -> {
                            if (expectedAuthor.isBlank()) throw missing();
                            try {
                                final var result = mutations.applyInCurrentTransaction(actor, id, expectedAuthor, kind, (sender, article) -> {
                                    permission(sender, kind);
                                    readable(sender, article);
                                });
                                switch (result.state()) {
                                    case UNAVAILABLE -> throw missing();
                                    case OWN -> throw new ApiV3Exception(422, "self", "不能感谢或打赏本人文章");
                                    case NOT_ENABLED ->
                                            throw new ApiV3Exception(422, "reward_not_enabled", "文章未启用打赏");
                                    default -> {
                                    }
                                }
                                applied.set(result);
                                return new JSONObject().put("id", id).put(kind == ArticleRewardMgmtService.Kind.THANK ? "thanked" : "rewarded", true);
                            } catch (final ArticleRewardMgmtService.Rejected e) {
                                if ("target_changed".equals(e.code))
                                    throw new ApiV3Exception(409, e.code, "文章已变化，请重试");
                                throw new ApiV3Exception(422, e.code, "转账条件未满足，请检查积分余额");
                            }
                        }), execution -> {
                    if (execution.applied())
                        mutations.afterApplied(applied.get());
                });
        return outcome.result();
    }

    private void permission(final JSONObject viewer, final ArticleRewardMgmtService.Kind kind) {
        if (kind == ArticleRewardMgmtService.Kind.THANK && !Permission.hasPermission(Set.of(Permission.PERMISSION_ID_C_COMMON_THANK_ARTICLE),
                roles.getPermissions(viewer.optString(User.USER_ROLE))))
            throw new ApiV3Exception(403, "forbidden", "没有操作权限");
    }

    private void readable(final JSONObject viewer, final JSONObject article) throws Exception {
        final var author = users.get(article.optString(Article.ARTICLE_AUTHOR_ID));
        final int status = article.optInt(Article.ARTICLE_STATUS);
        if ((status != Article.ARTICLE_STATUS_C_VALID && status != Article.ARTICLE_STATUS_C_LOCKED)
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
