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
import org.testng.Assert;

public class ApiV3CommentPublicationTest {
    public static void main(final String[] args) {
        final String anchor = "a".repeat(64);
        final JSONObject viewer = new JSONObject().put(Keys.OBJECT_ID, "reader");
        final JSONObject article = new JSONObject().put(Keys.OBJECT_ID, "10").put(Article.ARTICLE_AUTHOR_ID, "owner")
                .put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_LONG);
        final var defaults = ApiV3CommentInputs.create(new JSONObject().put("content", " source "));
        Assert.assertEquals(defaults.getString(Comment.COMMENT_CONTENT), " source ");
        Assert.assertEquals(defaults.getInt(Comment.COMMENT_ANONYMOUS), Comment.COMMENT_ANONYMOUS_C_PUBLIC);
        Assert.assertEquals(defaults.getInt(Comment.COMMENT_VISIBLE), Comment.COMMENT_VISIBLE_C_ALL);
        for (final JSONObject bad : new JSONObject[]{new JSONObject().put("content", "x").put("anonymous", 1),
                new JSONObject().put("content", "x").put("paragraphId", "A".repeat(64)),
                new JSONObject().put("content", "x").put("parentId", "1 OR 1=1"),
                new JSONObject().put("content", "x").put("parentId", JSONObject.NULL),
                new JSONObject().put("content", "x").put("authorId", "injected"),
                new JSONObject().put("content", "x").put("paragraphSnapshot", "injected")}) {
            reject(422, () -> ApiV3CommentInputs.create(bad));
        }
        System.out.println("PASS publish defaults, typed flags, parent/anchor formats and server-field injection");

        final var root = ApiV3CommentInputs.create(new JSONObject().put("content", "段评").put("paragraphId", anchor));
        ApiV3CommentTarget.prepare(root, article, null, viewer,
                id -> new ApiV3CommentTarget.Paragraph(id, "p", 2, "server snapshot"));
        Assert.assertEquals(root.getString(Comment.COMMENT_PARAGRAPH_SNAPSHOT), "server snapshot");
        Assert.assertEquals(root.getInt(Comment.COMMENT_TYPE), Comment.COMMENT_TYPE_C_PARAGRAPH);
        reject(404, () -> ApiV3CommentTarget.prepare(root, article, null, viewer, id -> null));
        reject(404, () -> ApiV3CommentTarget.prepare(root, new JSONObject(article.toString()).put(Article.ARTICLE_TYPE, 0), null, viewer, id -> { throw new AssertionError(); }));
        System.out.println("PASS paragraph snapshot is server-derived, missing/non-long targets are rejected");

        final var reply = ApiV3CommentInputs.create(new JSONObject().put("content", "回复").put("parentId", "20"));
        final var parent = new JSONObject().put(Keys.OBJECT_ID, "20").put(Comment.COMMENT_ON_ARTICLE_ID, "10")
                .put(Comment.COMMENT_AUTHOR_ID, "third").put(Comment.COMMENT_STATUS, Comment.COMMENT_STATUS_C_VALID);
        ApiV3CommentTarget.prepare(reply, article, parent, viewer, id -> { throw new AssertionError(); });
        reject(404, () -> ApiV3CommentTarget.prepare(reply, article, null, viewer, id -> null));
        reject(404, () -> ApiV3CommentTarget.prepare(reply, article, new JSONObject(parent.toString()).put(Comment.COMMENT_ON_ARTICLE_ID, "11"), viewer, id -> null));
        reject(404, () -> ApiV3CommentTarget.prepare(reply, article, new JSONObject(parent.toString()).put(Comment.COMMENT_STATUS, 1), viewer, id -> null));
        parent.put(Comment.COMMENT_VISIBLE, Comment.COMMENT_VISIBLE_C_AUTHOR);
        reject(404, () -> ApiV3CommentTarget.prepare(reply, article, parent, viewer, id -> null));
        ApiV3CommentTarget.prepare(reply, article, parent, new JSONObject().put(Keys.OBJECT_ID, "owner"), id -> null);
        ApiV3CommentTarget.prepare(reply, article, parent, new JSONObject().put(Keys.OBJECT_ID, "third"), id -> null);
        System.out.println("PASS replies require valid same-article readable parents, including private parent owners");

        parent.put(Comment.COMMENT_TYPE, Comment.COMMENT_TYPE_C_PARAGRAPH).put(Comment.COMMENT_PARAGRAPH_ID, anchor)
                .put(Comment.COMMENT_PARAGRAPH_STATUS, Comment.COMMENT_PARAGRAPH_STATUS_C_ORPHANED);
        ApiV3CommentTarget.prepare(reply, article, parent, new JSONObject().put(Keys.OBJECT_ID, "owner"), id -> { throw new AssertionError(); });
        final var wrong = new JSONObject(reply.toString()).put(Comment.COMMENT_PARAGRAPH_ID, "b".repeat(64));
        reject(404, () -> ApiV3CommentTarget.prepare(wrong, article, parent, new JSONObject().put(Keys.OBJECT_ID, "owner"), id -> null));
        System.out.println("PASS paragraph replies inherit the locked parent, including orphaned threads; conflicting anchor denied");

        final var source = new JSONObject(parent.toString()).put(Comment.COMMENT_CONTENT, "PRIVATE TEXT")
                .put(Article.ARTICLE, new JSONObject().put(Article.ARTICLE_CONTENT, "old snapshot"));
        final var forbidden = CommentRealtimeVisibility.forRecipient(source, article, viewer);
        Assert.assertEquals(forbidden.getString(Comment.COMMENT_CONTENT), "");
        Assert.assertEquals(CommentRealtimeVisibility.forRecipient(source, article, new JSONObject().put(Keys.OBJECT_ID, "owner")).getString(Comment.COMMENT_CONTENT), "PRIVATE TEXT");
        Assert.assertEquals(CommentRealtimeVisibility.forRecipient(source, article, new JSONObject().put(Keys.OBJECT_ID, "third")).getString(Comment.COMMENT_CONTENT), "PRIVATE TEXT");
        Assert.assertEquals(source.getString(Comment.COMMENT_CONTENT), "PRIVATE TEXT");
        forbidden.getJSONObject(Article.ARTICLE).put("injected", true);
        Assert.assertFalse(article.has("injected"));
        Assert.assertFalse(source.getJSONObject(Article.ARTICLE).has("injected"));
        System.out.println("PASS recipient-local private content removal preserves authorized body and isolates article/source state");
        final var anonymousSource = new JSONObject(source.toString()).put(Comment.COMMENT_ANONYMOUS, Comment.COMMENT_ANONYMOUS_C_ANONYMOUS);
        final var authorized = CommentRealtimeVisibility.forRecipient(anonymousSource, article, new JSONObject().put(Keys.OBJECT_ID, "third"));
        final var payload = CommentRealtimeVisibility.publicPayload(authorized);
        Assert.assertEquals(payload.getString(Comment.COMMENT_AUTHOR_ID), "");
        Assert.assertEquals(payload.getString(Comment.COMMENT_CONTENT), "PRIVATE TEXT");
        Assert.assertEquals(authorized.getString(Comment.COMMENT_AUTHOR_ID), "third");
        Assert.assertEquals(anonymousSource.getString(Comment.COMMENT_AUTHOR_ID), "third");
        Assert.assertEquals(CommentRealtimeVisibility.publicPayload(source).getString(Comment.COMMENT_AUTHOR_ID), "third");
        System.out.println("PASS anonymous public author ID is empty only after authorization; private body and internal identity stay intact");
    }
    private static void reject(final int status, final Runnable action) {
        try { action.run(); Assert.fail("Expected rejection"); }
        catch (final ApiV3Exception e) { Assert.assertEquals(e.status, status); }
    }
}
