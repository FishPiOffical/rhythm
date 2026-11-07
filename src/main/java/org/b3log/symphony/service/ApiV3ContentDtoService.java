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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3AvatarUrl;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ApiV3ContentSnapshots;
import org.b3log.symphony.util.ApiV3ArticleStickState;
import org.b3log.symphony.util.Symphonys;
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.Markdowns;
import org.json.JSONObject;
import org.jsoup.Jsoup;

/** Common public content DTOs; no list queries or viewer identity resolution. */
@Service
public class ApiV3ContentDtoService {
    @Inject private UserRepository userRepository;
    @Inject private AvatarQueryService avatarQueryService;

    public JSONObject summary(final JSONObject article) {
        final JSONObject author = ApiV3ContentSnapshots.hasAuthor(article)
                ? ApiV3ContentSnapshots.author(article, Article.ARTICLE_AUTHOR_ID)
                : currentAuthor(article.optString(Article.ARTICLE_AUTHOR_ID));
        return summary(article, author);
    }

    private JSONObject currentAuthor(final String id) {
        try {
            final var rows = userRepository.select(ApiV3ContentSnapshots.author(userRepository.getName()), id);
            if (rows.size() > 1) throw new RepositoryException("Duplicate v3 public author");
            return rows.isEmpty() ? null : rows.getFirst();
        } catch (final RepositoryException error) {
            throw new ApiV3Exception(503, "temporarily_unavailable", "作者资料暂不可用");
        }
    }

    /** A caller with a current SQL author snapshot need not fall back to a cached user lookup. */
    public JSONObject summary(final JSONObject article, final JSONObject author) {
        final boolean blocked = isBlocked(author);
        final boolean anonymous = Article.ARTICLE_ANONYMOUS_C_ANONYMOUS == article.optInt(Article.ARTICLE_ANONYMOUS);
        final boolean thought = Article.ARTICLE_TYPE_C_THOUGHT == article.optInt(Article.ARTICLE_TYPE);
        // Thought replay records are not a readable preview and must not be flattened into one.
        final String preview = blocked || thought ? "" : Jsoup.parse(render(article.optString(Article.ARTICLE_CONTENT))).text();
        return new JSONObject().put("id", article.optString(Keys.OBJECT_ID))
                .put("title", blocked ? "内容不可用" : article.optString(Article.ARTICLE_TITLE))
                .put("preview", preview.substring(0, Math.min(150, preview.length())))
                .put("tags", blocked ? "" : article.optString(Article.ARTICLE_TAGS))
                .put("type", article.optInt(Article.ARTICLE_TYPE)).put("anonymous", anonymous)
                .put("perfect", article.optInt(Article.ARTICLE_PERFECT) == Article.ARTICLE_PERFECT_C_PERFECT)
                .put("author", author(author, anonymous)).put("permalink", article.optString(Article.ARTICLE_PERMALINK))
                .put("createdAt", article.optLong(Article.ARTICLE_CREATE_TIME))
                .put("updatedAt", article.optLong(Article.ARTICLE_UPDATE_TIME))
                .put("commentCount", article.optInt(Article.ARTICLE_COMMENT_CNT))
                .put("viewCount", article.optInt(Article.ARTICLE_VIEW_CNT))
                .put("stick", ApiV3ArticleStickState.metadata(article.opt(Article.ARTICLE_STICK),
                        System.currentTimeMillis(), Symphonys.STICK_ARTICLE_TIME));
    }

    public JSONObject author(final JSONObject user, final boolean anonymous) {
        final JSONObject dto = ApiV3ContentUtil.authorDto(user, anonymous);
        if (!anonymous && null != user) {
            final String url = ApiV3AvatarUrl.stored(user.optString(UserExt.USER_AVATAR_URL));
            dto.put("userAvatarURL", url.isEmpty() ? avatarQueryService.getDefaultAvatarURL("48") : url);
        }
        return dto;
    }

    private static boolean isBlocked(final JSONObject user) {
        return null == user || UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS)
                || UserExt.USER_STATUS_C_DEACTIVATED == user.optInt(UserExt.USER_STATUS);
    }

    private static String render(final String content) {
        return Markdowns.clean(Markdowns.toHTML(Emotions.convert(content)), "");
    }
}
