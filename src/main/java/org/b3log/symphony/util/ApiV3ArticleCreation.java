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

import org.b3log.symphony.model.ArticleDraft;
import org.json.JSONObject;

/** Atomic creation body; the store binds Bearer identity, validators and current-transaction services. */
public final class ApiV3ArticleCreation {
    private ApiV3ArticleCreation() { }
    public record Created(String id, String permalink) { }
    public interface Store {
        boolean inTransaction();
        void lockAndAuthorize() throws Exception;
        void validate(JSONObject request) throws Exception;
        Created publish(JSONObject request) throws Exception;
        void removeDraft(String draftId) throws Exception;
    }
    public static JSONObject apply(final Store store, final JSONObject normalized) throws Exception {
        if (!store.inTransaction() || !ArticlePublicationCaches.active()) throw new IllegalStateException("article creation transaction required");
        store.lockAndAuthorize();
        final JSONObject request = new JSONObject(normalized.toString());
        store.validate(request);
        final Created created = store.publish(request);
        final String draft = normalized.optString(ArticleDraft.ARTICLE_DRAFT_ID);
        if (!draft.isEmpty()) store.removeDraft(draft);
        return new JSONObject().put("id", created.id()).put("permalink", created.permalink());
    }
}
