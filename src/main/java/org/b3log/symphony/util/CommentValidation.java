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

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.model.User;
import org.b3log.symphony.censor.CensorResult;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Role;
import org.json.JSONObject;
import java.util.function.Consumer;

/** Separate preparation/moderation preserves legacy target checks before a paid moderation call. */
public final class CommentValidation {
    private CommentValidation() { }
    private static final String LEGACY_QUOTA_EXEMPT_ROLE = "1630552921050";
    public interface Rules {
        boolean allowWrite(String actor, boolean adding);
        String reservedWords(String text);
        String label(String key);
        CensorResult censor(String text);
    }
    public static final class Rejected extends RuntimeException {
        public final int status;
        public final String code;
        public Rejected(final int status, final String code, final String message) { super(message); this.status = status; this.code = code; }
    }
    public static void prepare(final JSONObject request, final JSONObject actor, final boolean adding, final Rules rules) {
        final String role = actor.optString(User.USER_ROLE);
        if (!Role.ROLE_ID_C_ADMIN.equals(role) && !LEGACY_QUOTA_EXEMPT_ROLE.equals(role)
                && !rules.allowWrite(actor.optString(Keys.OBJECT_ID), adding)) {
            throw new Rejected(429, "too_frequent", "操作过于频繁，请稍候重试。");
        }
        request.put(Comment.COMMENT_CONTENT, rules.reservedWords(request.optString(Comment.COMMENT_CONTENT)));
        final String content = StringUtils.trim(request.optString(Comment.COMMENT_CONTENT));
        if (StringUtils.isBlank(content) || content.length() > Comment.MAX_COMMENT_CONTENT_LENGTH) {
            throw new Rejected(422, "invalid_comment", rules.label("commentErrorLabel"));
        }
    }
    public static void moderate(final JSONObject request, final Rules rules, final Consumer<CensorResult> rejectedLog) {
        final String content = StringUtils.trim(request.optString(Comment.COMMENT_CONTENT));
        final CensorResult result = rules.censor(content);
        if (result.isBlocked()) {
            rejectedLog.accept(result);
            throw new Rejected(422, "content_rejected", "您的评论经过AI审查存在违规内容，请修改内容后重试。内容" + result.showBannedWords());
        }
    }
}
