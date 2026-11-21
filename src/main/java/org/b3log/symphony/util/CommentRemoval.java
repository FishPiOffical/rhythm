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
import org.json.JSONObject;
import java.util.List;

/** Paid owner removal. The caller owns account locks, transaction and publication cache boundary. */
public final class CommentRemoval {
    private CommentRemoval() { }
    public record State(JSONObject comment, JSONObject article) { }
    public record Result(String id, String actor, String articleId, String transferId, List<String> notificationUsers) {
        public Result { notificationUsers = List.copyOf(notificationUsers); }
    }
    public static final class Rejected extends RuntimeException {
        public final String code;
        public Rejected(final String code) { super(code); this.code = code; }
    }
    public interface Store {
        boolean active();
        State lockAndLoad(String actor, String id) throws Exception;
        boolean hasReplies(String id) throws Exception;
        boolean hasVotes(String id) throws Exception;
        boolean hasThanks(String id) throws Exception;
        int balance() throws Exception;
        String debit(String id, int fee, long now) throws Exception;
        List<String> erase(State state) throws Exception;
    }
    public static Result apply(final Store store, final String actor, final String id, final int fee, final long now) throws Exception {
        if (!store.active() || !ArticlePublicationCaches.active()) throw new IllegalStateException("comment removal transaction required");
        if (fee < 0) throw new IllegalArgumentException("negative comment removal fee");
        final State state = store.lockAndLoad(actor, id);
        if (null == state || null == state.comment() || null == state.article()
                || !actor.equals(state.comment().optString(Comment.COMMENT_AUTHOR_ID))
                || !id.equals(state.comment().optString(Keys.OBJECT_ID))
                || !state.article().optString(Keys.OBJECT_ID).equals(state.comment().optString(Comment.COMMENT_ON_ARTICLE_ID))) {
            throw new Rejected("not_found");
        }
        final JSONObject comment = state.comment();
        if (comment.optLong(Comment.COMMENT_REPLY_CNT) > 0 || store.hasReplies(id)) throw new Rejected("has_replies");
        if (comment.optLong(Comment.COMMENT_GOOD_CNT) > 0 || comment.optLong(Comment.COMMENT_BAD_CNT) > 0 || store.hasVotes(id)) {
            throw new Rejected("has_votes");
        }
        if (comment.optLong(Comment.COMMENT_THANK_CNT) > 0 || store.hasThanks(id)) throw new Rejected("has_thanks");
        if (comment.optInt(Comment.COMMENT_QNA_OFFERED) == Comment.COMMENT_QNA_OFFERED_C_YES) throw new Rejected("accepted");
        if (store.balance() < fee) throw new Rejected("insufficient_balance");
        final String transfer = store.debit(id, fee, now);
        if (null == transfer || transfer.isBlank()) throw new IllegalStateException("comment removal transfer failed");
        final List<String> recipients = store.erase(state);
        return new Result(id, actor, state.article().optString(Keys.OBJECT_ID), transfer, recipients);
    }
}
