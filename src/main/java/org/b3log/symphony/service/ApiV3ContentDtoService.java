package org.b3log.symphony.service;

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ApiV3ArticleStickState;
import org.b3log.symphony.util.Symphonys;
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.Markdowns;
import org.json.JSONObject;
import org.jsoup.Jsoup;

/** Common public content DTOs; no list queries or viewer identity resolution. */
@Service
public class ApiV3ContentDtoService {
    @Inject private UserQueryService userQueryService;
    @Inject private AvatarQueryService avatarQueryService;

    public JSONObject summary(final JSONObject article) {
        final JSONObject author = userQueryService.getUser(article.optString(Article.ARTICLE_AUTHOR_ID));
        return summary(article, author);
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
                .put("author", author(author, anonymous)).put("permalink", article.optString(Article.ARTICLE_PERMALINK))
                .put("createdAt", article.optLong(Article.ARTICLE_CREATE_TIME))
                .put("updatedAt", article.optLong(Article.ARTICLE_UPDATE_TIME))
                .put("commentCount", article.optInt(Article.ARTICLE_COMMENT_CNT))
                .put("viewCount", article.optInt(Article.ARTICLE_VIEW_CNT))
                .put("stick", ApiV3ArticleStickState.metadata(article.opt(Article.ARTICLE_STICK),
                        System.currentTimeMillis(), Symphonys.STICK_ARTICLE_TIME));
    }

    public JSONObject author(final JSONObject user, final boolean anonymous) {
        // Copy before filling the avatar: query services may return a shared cached user.
        final JSONObject dto = ApiV3ContentUtil.authorDto(user, anonymous);
        if (!anonymous && null != user) dto.put("userAvatarURL", avatarQueryService.getAvatarURLByUser(user, "48"));
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
