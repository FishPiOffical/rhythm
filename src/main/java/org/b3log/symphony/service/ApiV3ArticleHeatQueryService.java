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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.processor.channel.ArticleChannel;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

/**
 * 按数据库当前状态确认文章可见后，返回本节点的近似连接数。
 */
@Service
public class ApiV3ArticleHeatQueryService {
    @Inject
    private ApiV3RealtimeTargetService targets;
    @Inject
    private ApiV3RealtimeService realtime;

    public JSONObject read(final String actor, final String articleId) throws RepositoryException {
        if (actor == null || !actor.matches("[0-9]{1,32}") || articleId == null || !articleId.matches("[0-9]{1,32}"))
            throw ApiV3WriteInputs.invalid();
        try {
            if (!targets.articleVisible(actor, articleId)) throw ApiV3Requests.notFound();
        } catch (final RepositoryException e) {
            throw new ApiV3Exception(503, "temporarily_unavailable", "文章热度暂不可用");
        }
        final Integer legacy = ArticleChannel.ARTICLE_VIEWS.get(articleId);
        final long count = (legacy == null ? 0L : Math.max(0L, legacy.longValue())) + realtime.articleConnectionCount(articleId);
        return new JSONObject().put("articleId", articleId).put("heat", count);
    }
}
