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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.UserMentionCandidates;
import org.json.JSONObject;
import java.util.Set;

/** 按数据库当前状态校验长连接订阅权限。 */
@Service
public class ApiV3RealtimeTargetService {
    @Inject private ArticleRepository articles;
    @Inject private UserRepository users;

    public boolean articleVisible(final String actor, final String articleId) throws RepositoryException {
        if (actor == null || !actor.matches("[0-9]{1,32}") || articleId == null || !articleId.matches("[0-9]{1,32}")) return false;
        final var rows = articles.select("SELECT a.oId,a.articleAuthorId,a.articleType,a.articleStatus,a.articleAnonymousView,"
                + "CASE WHEN a.articleType=? THEN a.articleContent ELSE '' END AS articleContent,"
                + "v.oId AS viewerId,v.userName AS viewerName,v.userRole AS viewerRole FROM `" + articles.getName()
                + "` a JOIN `" + users.getName() + "` u ON u.oId=a.articleAuthorId JOIN `" + users.getName()
                + "` v ON v.oId=? WHERE a.oId=? AND u.userStatus NOT IN (?,?) AND v.userStatus=? LIMIT 1",
                Article.ARTICLE_TYPE_C_DISCUSSION, actor, articleId, UserExt.USER_STATUS_C_INVALID,
                UserExt.USER_STATUS_C_DEACTIVATED, UserExt.USER_STATUS_C_VALID);
        if (rows.isEmpty()) return false;
        final JSONObject article = rows.getFirst();
        final JSONObject viewer = new JSONObject().put(Keys.OBJECT_ID, article.getString("viewerId"))
                .put(User.USER_NAME, article.optString("viewerName")).put(User.USER_ROLE, article.optString("viewerRole"));
        final Set<String> invited = Article.ARTICLE_TYPE_C_DISCUSSION == article.optInt(Article.ARTICLE_TYPE)
                ? UserMentionCandidates.parse(article.optString(Article.ARTICLE_CONTENT)) : Set.of();
        return ApiV3ContentUtil.canViewArticle(article, viewer, invited);
    }
}
