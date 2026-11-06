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

import org.b3log.symphony.model.Comment;
import org.json.JSONObject;
import org.testng.Assert;

public class ApiV3CommentInputsTest {
    public static void main(final String[] args) {
        final String text = "    Markdown\n\t代码 <b>原文</b>";
        final var request = ApiV3CommentInputs.edit(new JSONObject().put("content", text));
        Assert.assertEquals(request.getString(Comment.COMMENT_CONTENT), text);
        Assert.assertFalse(request.has(Comment.COMMENT_VISIBLE));
        Assert.assertEquals(ApiV3CommentInputs.edit(new JSONObject().put("content", "a").put("visible", true)).getInt(Comment.COMMENT_VISIBLE), Comment.COMMENT_VISIBLE_C_AUTHOR);
        Assert.assertEquals(ApiV3CommentInputs.edit(new JSONObject().put("content", "a").put("visible", false)).getInt(Comment.COMMENT_VISIBLE), Comment.COMMENT_VISIBLE_C_ALL);
        ApiV3CommentInputs.edit(new JSONObject().put("content", "a".repeat(Comment.MAX_COMMENT_CONTENT_LENGTH)));
        System.out.println("PASS typed comment edit preserves source, requires content and retains omitted visibility");
        for (final var body : new JSONObject[]{new JSONObject(), new JSONObject().put("visible", true),
                new JSONObject().put("content", 1), new JSONObject().put("content", " \n\t "),
                new JSONObject().put("content", "a".repeat(Comment.MAX_COMMENT_CONTENT_LENGTH + 1)),
                new JSONObject().put("content", "a\u0000b"), new JSONObject().put("content", "a\u001eb"),
                new JSONObject().put("content", "body").put("visible", 1),
                new JSONObject().put("content", "body").put("visible", JSONObject.NULL)}) reject(body);
        for (final String forbidden : new String[]{"authorId", "articleId", "anonymous", "status", "ip", "ua", "bypassCensor", "paragraphId"}) {
            reject(new JSONObject().put("content", "body").put(forbidden, "injected"));
        }
        System.out.println("PASS content bounds/control/type errors and identity/status/paragraph injection rejected");
    }
    private static void reject(final JSONObject body) {
        try { ApiV3CommentInputs.edit(body); Assert.fail(); }
        catch (final ApiV3Exception e) { Assert.assertEquals(e.status, 422); }
    }
}
