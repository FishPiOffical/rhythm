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
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.LongArticleColumn;
import org.b3log.symphony.model.Role;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.ArrayList;
import java.util.List;

/** Actual shared validator with moderation/tag adapters; does not call external moderation or live tag configuration. */
public class ArticleValidationTest {
    public static void main(final String[] args) {
        final var rules = new Rules();
        final JSONObject normal = body().put(Article.ARTICLE_TITLE, "  RESERVED  ")
                .put(Article.ARTICLE_CONTENT, "    RESERVED\n正文").put(Article.ARTICLE_TAGS, "ok,ok,disabled");
        validate(normal, Role.ROLE_ID_C_DEFAULT, true, rules);
        Assert.assertEquals(normal.getString(Article.ARTICLE_TITLE), "clean");
        Assert.assertEquals(normal.getString(Article.ARTICLE_CONTENT), "    clean\n正文");
        Assert.assertEquals(normal.getString(Article.ARTICLE_TAGS), "ok"); Assert.assertEquals(rules.reservedCalls, 3);
        Assert.assertEquals(rules.censored.size(), 2); Assert.assertEquals(rules.quotaCalls, 1);
        final JSONObject emptyTags = body().put(Article.ARTICLE_TAGS, ""); validate(emptyTags, Role.ROLE_ID_C_DEFAULT, true, new Rules());
        Assert.assertEquals(emptyTags.getString(Article.ARTICLE_TAGS), "待分类");
        System.out.println("PASS shared title/tag/body normalization, tag dedup/filter/default and preserved Markdown indentation");
        reject(body().put(Article.ARTICLE_TITLE, " "), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "invalid_article");
        reject(body().put(Article.ARTICLE_CONTENT, "abc"), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "invalid_article");
        reject(body().put(Article.ARTICLE_TITLE, "x".repeat(256)), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "invalid_article");
        reject(body().put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_THOUGHT), Role.ROLE_ID_C_ADMIN, true, new Rules(), "invalid_article");
        validate(body().put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_THOUGHT), Role.ROLE_ID_C_ADMIN, false, new Rules());
        reject(body().put(Article.ARTICLE_TYPE, 7), Role.ROLE_ID_C_ADMIN, true, new Rules(), "invalid_article");
        System.out.println("PASS title/body/type bounds and disabled thought creation without disabling historical edits");
        reject(body().put(Article.ARTICLE_TAGS, "bad!"), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "invalid_article");
        reject(body().put(Article.ARTICLE_TAGS, "reserved"), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "invalid_article");
        for (final String role : List.of(Role.ROLE_ID_C_ADMIN, Role.ROLE_ID_C_OP)) {
            final var r = new Rules(); validate(body().put(Article.ARTICLE_TAGS, "reserved,Q&A"), role, true, r); Assert.assertEquals(r.quotaCalls, 0);
        }
        final var noQuota = new Rules(); noQuota.allow = false;
        reject(body(), Role.ROLE_ID_C_DEFAULT, true, noQuota, "too_many_requests");
        Assert.assertTrue(noQuota.censored.isEmpty());
        System.out.println("PASS role-based reserved tags, tag whitelist and existing admin/OP quota exceptions");
        reject(body().put(Article.ARTICLE_BYPASS_CENSOR, true), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "forbidden");
        for (final String role : List.of(Role.ROLE_ID_C_ADMIN, Role.ROLE_ID_C_OP, Role.ROLE_ID_C_SUPER_MEMBER, Role.ROLE_ID_C_DISCIPLINE_COMMITTEE)) {
            final var r = new Rules(); validate(body().put(Article.ARTICLE_BYPASS_CENSOR, true), role, true, r);
            Assert.assertTrue(r.censored.isEmpty()); Assert.assertEquals(r.reservedCalls, 0);
        }
        final var superQuota = new Rules(); superQuota.allow = false;
        reject(body().put(Article.ARTICLE_BYPASS_CENSOR, true), Role.ROLE_ID_C_SUPER_MEMBER, true, superQuota, "too_many_requests");
        System.out.println("PASS actual bypass role list and bypass does not grant a quota exemption");
        reject(body().put(Article.ARTICLE_REWARD_POINT, -1), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "invalid_article");
        reject(body().put(Article.ARTICLE_QNA_OFFER_POINT, -1), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "invalid_article");
        reject(body().put(Article.ARTICLE_REWARD_CONTENT, "reward"), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "invalid_article");
        reject(body().put(Article.ARTICLE_REWARD_POINT, 1).put(Article.ARTICLE_REWARD_CONTENT, "abc"), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "invalid_article");
        // Preserve the old validator's historical upper bound; v3's typed input enforces its documented smaller limit.
        validate(body().put(Article.ARTICLE_REWARD_POINT, 1).put(Article.ARTICLE_REWARD_CONTENT, "x".repeat(102401)), Role.ROLE_ID_C_DEFAULT, true, new Rules());
        System.out.println("PASS reward prerequisites and preserved legacy upper bound without narrowing the old API");
        final var log = new ArrayList<String>(); final var blocked = new Rules();
        try { ArticleValidation.validate(body().put(Article.ARTICLE_CONTENT, "blocked_text"), actor(Role.ROLE_ID_C_DEFAULT), true, blocked, log::add); Assert.fail(); }
        catch (final ArticleValidation.Rejected e) { Assert.assertEquals(e.status, 422); Assert.assertEquals(e.code, "content_rejected"); }
        Assert.assertEquals(log.size(), 1); Assert.assertEquals(blocked.censored.size(), 2);
        reject(body().put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_LONG).put(LongArticleColumn.CHAPTER_NO, "0"), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "invalid_article");
        reject(body().put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_LONG).put(LongArticleColumn.COLUMN_COVER_URL, "javascript:alert(1)"), Role.ROLE_ID_C_DEFAULT, true, new Rules(), "invalid_article");
        System.out.println("PASS real moderation result rejection/audit callback and existing column cover/chapter validation");
        for (final String tag : List.of("新人报道", "新人报到附记")) {
            try { ApiV3ArticleInputs.validateGoodPost(new JSONObject().put(Article.ARTICLE_GOOD_POST, "yes").put(Article.ARTICLE_TAGS, tag)); Assert.fail(); }
            catch (final ApiV3Exception e) { Assert.assertEquals(e.code, "invalid_good_post"); }
        }
        ApiV3ArticleInputs.validateGoodPost(new JSONObject().put(Article.ARTICLE_TAGS, "新人报道"));
        ApiV3ArticleInputs.validateGoodPost(new JSONObject().put(Article.ARTICLE_GOOD_POST, "yes").put(Article.ARTICLE_TAGS, "日常"));
        System.out.println("PASS v3 reward flag follows the editor's introduction-post exclusion");
    }
    private static JSONObject body() { return new JSONObject().put(Article.ARTICLE_TITLE, "title").put(Article.ARTICLE_CONTENT, "正文内容").put(Article.ARTICLE_TAGS, "ok"); }
    private static JSONObject actor(final String role) { return new JSONObject().put(Keys.OBJECT_ID, "owner").put(User.USER_ROLE, role).put(User.USER_NAME, "owner"); }
    private static void validate(final JSONObject body, final String role, final boolean adding, final Rules rules) {
        ArticleValidation.validate(body, actor(role), adding, rules, ignored -> { });
    }
    private static void reject(final JSONObject body, final String role, final boolean adding, final Rules rules, final String code) {
        try { validate(body, role, adding, rules); Assert.fail(); }
        catch (final ArticleValidation.Rejected e) { Assert.assertEquals(e.code, code); }
    }
    private static final class Rules implements ArticleValidation.Rules {
        int quotaCalls, reservedCalls; boolean allow = true; final List<String> censored = new ArrayList<>();
        public String label(final String key) { return key; }
        public String reservedWords(final String text) { reservedCalls++; return text.replace("RESERVED", "clean"); }
        public String clearEmotions(final String text) { return text; }
        public String formatTags(final String text) { return text; }
        public List<String> invalidTags() { return List.of("disabled"); }
        public boolean whiteListedTag(final String tag) { return "Q&A".equals(tag); }
        public boolean validTag(final String tag) { return tag.matches("[a-z]+|待分类"); }
        public int maxTagLength() { return 12; }
        public boolean reservedTag(final String tag) { return "reserved".equals(tag); }
        public boolean allowWrite(final String actor) { quotaCalls++; Assert.assertEquals(actor, "owner"); return allow; }
        public CensorResult censor(final String text) { censored.add(text); return text.contains("blocked_text") ? CensorResult.block("test", List.of("blocked_text")) : CensorResult.pass(); }
    }
}
