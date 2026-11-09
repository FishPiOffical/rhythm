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
package org.b3log.symphony.util;

import org.b3log.symphony.model.Article;
import org.json.JSONObject;

/** 所有者编辑文章时的状态变更，响应只使用公开投影。 */
public final class ApiV3ArticleEditing {
    private ApiV3ArticleEditing() { }
    public record State(JSONObject article, JSONObject chapter) { }
    public record Saved(String id, String permalink) { }
    public interface Store {
        boolean inTransaction();
        State lockAndLoad() throws Exception;
        void validate(JSONObject request) throws Exception;
        Saved save(JSONObject request) throws Exception;
    }
    public static JSONObject apply(final Store store, final String actor, final JSONObject patch) throws Exception {
        if (!store.inTransaction() || !ArticlePublicationCaches.active()) throw new IllegalStateException("article edit transaction required");
        final State state = store.lockAndLoad();
        if (null == state || null == state.article() || !actor.equals(state.article().optString(Article.ARTICLE_AUTHOR_ID))) {
            throw new ApiV3Exception(404, "not_found", "文章不存在或不可编辑");
        }
        if (state.article().optInt(Article.ARTICLE_STATUS) != Article.ARTICLE_STATUS_C_VALID) throw new ApiV3Exception(403, "article_locked", "文章不可编辑");
        final JSONObject request = ApiV3ArticleEditInputs.merge(state.article(), state.chapter(), patch);
        store.validate(request);
        final Saved saved = store.save(request);
        return new JSONObject().put("id", saved.id()).put("permalink", saved.permalink());
    }
}
