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

import org.b3log.latke.Keys;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Common;
import org.json.JSONObject;
import java.util.List;

/** Shared comment edit transition. The caller retains transaction, account and cache ownership. */
public final class CommentEditing {
    private CommentEditing() { }
    public static String ownedSql(final String table, final boolean locking) {
        if (null == table || !table.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("invalid internal table identifier");
        return "SELECT " + (locking ? "*" : "oId,commentOnArticleId") + " FROM `" + table
                + "` WHERE oId=? AND commentAuthorId=?" + (locking ? " FOR UPDATE" : " LIMIT 1");
    }
    public record State(JSONObject comment, JSONObject article) { }
    /** Internal persistence objects, never an API response. */
    public record Result(JSONObject comment, JSONObject article, List<String> transfers) {
        public Result { transfers = List.copyOf(transfers); }
    }
    public interface Store {
        boolean inTransaction();
        State lockAndLoad(String actor, String id) throws Exception;
        String normalize(String content);
        int balance() throws Exception;
        void save(String id, JSONObject comment) throws Exception;
        void revision(String id, String actor, String content) throws Exception;
        String charge(String id, int fee, long now) throws Exception;
    }
    public static final class Rejected extends RuntimeException {
        public final String code;
        public Rejected(final String code) { super(code); this.code = code; }
    }
    public static Result apply(final Store store, final String actor, final String id, final JSONObject request,
                               final long now, final int fee) throws Exception {
        if (!store.inTransaction() || !ArticlePublicationCaches.active()) throw new IllegalStateException("comment edit transaction required");
        if (fee < 0) throw new IllegalArgumentException("negative comment edit fee");
        final State state = store.lockAndLoad(actor, id);
        if (null == state || null == state.comment() || null == state.article()
                || !actor.equals(state.comment().optString(Comment.COMMENT_AUTHOR_ID))
                || !id.equals(state.comment().optString(Keys.OBJECT_ID))
                || !state.article().optString(Keys.OBJECT_ID).equals(state.comment().optString(Comment.COMMENT_ON_ARTICLE_ID))) {
            throw new Rejected("not_found");
        }
        final JSONObject current = state.comment();
        final var edited = new JSONObject(current.toString());
        final String content = store.normalize(request.optString(Comment.COMMENT_CONTENT));
        edited.put(Comment.COMMENT_CONTENT, content).put(Comment.COMMENT_IP, request.optString(Comment.COMMENT_IP));
        final String ua = request.optString(Comment.COMMENT_UA);
        edited.put(Comment.COMMENT_UA, ua.substring(0, Math.min(ua.length(), Common.MAX_LENGTH_UA)));
        if (request.has(Comment.COMMENT_VISIBLE)) edited.put(Comment.COMMENT_VISIBLE, request.optInt(Comment.COMMENT_VISIBLE));
        final boolean pastWindow = now - current.optLong(Keys.OBJECT_ID) > 300_000L;
        final boolean changed = !current.optString(Comment.COMMENT_CONTENT).replaceAll("\\s+", "").equals(content.replaceAll("\\s+", ""));
        final boolean charge = pastWindow && current.optInt(Comment.COMMENT_ANONYMOUS) == Comment.COMMENT_ANONYMOUS_C_PUBLIC
                && state.article().optInt(Article.ARTICLE_ANONYMOUS) == Article.ARTICLE_ANONYMOUS_C_PUBLIC;
        if (charge && store.balance() < fee) throw new Rejected("insufficient_balance");
        store.save(id, edited);
        if (pastWindow && changed) store.revision(id, actor, content);
        final String transfer = charge ? store.charge(id, fee, now) : null;
        if (charge && (null == transfer || transfer.isBlank())) throw new IllegalStateException("comment edit transfer failed");
        return new Result(edited, state.article(), charge ? List.of(transfer) : List.of());
    }
}
