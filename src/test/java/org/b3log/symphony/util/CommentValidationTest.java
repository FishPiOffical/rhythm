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
import org.b3log.latke.model.User;
import org.b3log.symphony.censor.CensorResult;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Role;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.ArrayList;
import java.util.List;

/** Shared real validator with adapters; external moderation and HTTP are not exercised. */
public class CommentValidationTest {
    public static void main(final String[] args) {
        final var rules = new Rules();
        final var body = body("    RESERVED\n正文\t ").put("apiKey", "untouched");
        CommentValidation.prepare(body, actor(Role.ROLE_ID_C_DEFAULT), true, rules);
        Assert.assertEquals(body.getString(Comment.COMMENT_CONTENT), "    clean\n正文\t ");
        Assert.assertEquals(body.getString("apiKey"), "untouched");
        Assert.assertEquals(rules.ids, List.of("owner")); Assert.assertEquals(rules.adding, List.of(true));
        Assert.assertTrue(rules.censored.isEmpty());
        CommentValidation.moderate(body, rules, ignored -> Assert.fail());
        Assert.assertEquals(rules.censored, List.of("clean\n正文"));
        System.out.println("PASS reserved words before validation, preserved Markdown whitespace and trimmed moderation input");
        for (final String invalid : new String[]{"", "  \t\n", "x".repeat(Comment.MAX_COMMENT_CONTENT_LENGTH + 1)}) {
            reject(body(invalid), new Rules(), "invalid_comment", 422);
        }
        CommentValidation.prepare(body("x".repeat(Comment.MAX_COMMENT_CONTENT_LENGTH)), actor(Role.ROLE_ID_C_DEFAULT), true, new Rules());
        // Legacy bounds are checked after trim; do not silently tighten this contract during extraction.
        CommentValidation.prepare(body("  " + "x".repeat(Comment.MAX_COMMENT_CONTENT_LENGTH) + "  "), actor(Role.ROLE_ID_C_DEFAULT), true, new Rules());
        reject(new JSONObject(), new Rules(), "invalid_comment", 422);
        System.out.println("PASS blank/missing/overlong comments reject and legacy trimmed-length boundary remains accepted");
        final var denied = new Rules(); denied.allow = false;
        final var untouched = body("RESERVED"); reject(untouched, denied, "too_frequent", 429);
        Assert.assertEquals(untouched.getString(Comment.COMMENT_CONTENT), "RESERVED");
        Assert.assertTrue(denied.censored.isEmpty());
        final var edit = new Rules(); CommentValidation.prepare(body("text"), actor(Role.ROLE_ID_C_DEFAULT), false, edit);
        Assert.assertEquals(edit.adding, List.of(false));
        System.out.println("PASS quota denial precedes processing and publish/edit action reaches the shared adapter");
        for (final String role : new String[]{Role.ROLE_ID_C_ADMIN, "1630552921050"}) {
            final var exempt = new Rules(); exempt.allow = false;
            final var request = body("text"); CommentValidation.prepare(request, actor(role), true, exempt);
            Assert.assertTrue(exempt.ids.isEmpty()); CommentValidation.moderate(request, exempt, ignored -> Assert.fail());
            Assert.assertEquals(exempt.censored, List.of("text"));
        }
        System.out.println("PASS original exempt roles bypass quota while moderation remains required");
        final var blocked = new Rules(); blocked.block = true;
        final var log = new ArrayList<CensorResult>();
        final var request = body("  blocked  "); CommentValidation.prepare(request, actor(Role.ROLE_ID_C_DEFAULT), true, blocked);
        try { CommentValidation.moderate(request, blocked, log::add); Assert.fail(); }
        catch (final CommentValidation.Rejected e) {
            Assert.assertEquals(e.status, 422); Assert.assertEquals(e.code, "content_rejected");
            Assert.assertEquals(e.getMessage(), "您的评论经过AI审查存在违规内容，请修改内容后重试。内容违规：blocked");
        }
        Assert.assertEquals(log.size(), 1); Assert.assertTrue(log.getFirst().isBlocked());
        Assert.assertEquals(blocked.censored, List.of("blocked"));
        System.out.println("PASS actual blocked CensorResult calls rejection logger and preserves legacy response message");
    }
    private static JSONObject body(final String content) { return new JSONObject().put(Comment.COMMENT_CONTENT, content); }
    private static JSONObject actor(final String role) { return new JSONObject().put(Keys.OBJECT_ID, "owner").put(User.USER_ROLE, role); }
    private static void reject(final JSONObject body, final Rules rules, final String code, final int status) {
        try { CommentValidation.prepare(body, actor(Role.ROLE_ID_C_DEFAULT), true, rules); Assert.fail(); }
        catch (final CommentValidation.Rejected e) { Assert.assertEquals(e.code, code); Assert.assertEquals(e.status, status); }
    }
    private static final class Rules implements CommentValidation.Rules {
        boolean allow = true, block;
        final List<String> ids = new ArrayList<>(), censored = new ArrayList<>();
        final List<Boolean> adding = new ArrayList<>();
        public boolean allowWrite(final String id, final boolean add) { ids.add(id); adding.add(add); return allow; }
        public String reservedWords(final String text) { return text.replace("RESERVED", "clean"); }
        public String label(final String key) { return key; }
        public CensorResult censor(final String text) { censored.add(text); return block ? CensorResult.block("test", List.of("blocked")) : CensorResult.pass(); }
    }
}
