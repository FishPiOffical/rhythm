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
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Set;

public class ApiV3ContentUtilTest {
    private JSONObject article() {
        return new JSONObject().put(Article.ARTICLE_AUTHOR_ID, "author")
                .put(Article.ARTICLE_STATUS, Article.ARTICLE_STATUS_C_VALID)
                .put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_NORMAL);
    }

    private JSONObject viewer(final String id, final String name) {
        return new JSONObject().put(Keys.OBJECT_ID, id).put(User.USER_NAME, name);
    }

    @Test
    public void anonymousArticleBoundary() {
        final JSONObject article = article();
        Assert.assertTrue(ApiV3ContentUtil.canViewArticle(article, null, Set.of()));
        article.put(Article.ARTICLE_ANONYMOUS_VIEW, Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW);
        Assert.assertFalse(ApiV3ContentUtil.canViewArticle(article, null, Set.of()));
        Assert.assertTrue(ApiV3ContentUtil.canViewArticle(article, viewer("reader", "Reader"), Set.of()));
        article.put(Article.ARTICLE_STATUS, Article.ARTICLE_STATUS_C_INVALID);
        Assert.assertFalse(ApiV3ContentUtil.canViewArticle(article, viewer("author", "Author"), Set.of()));
        Assert.assertFalse(ApiV3ContentUtil.canViewArticle(null, null, Set.of()));
    }

    @Test
    public void discussionRequiresAuthorAdminOrInvitation() {
        final JSONObject article = article().put(Article.ARTICLE_TYPE, Article.ARTICLE_TYPE_C_DISCUSSION);
        Assert.assertFalse(ApiV3ContentUtil.canViewArticle(article, null, Set.of("Reader")));
        Assert.assertFalse(ApiV3ContentUtil.canViewArticle(article, viewer("other", "Other"), Set.of("Reader")));
        Assert.assertTrue(ApiV3ContentUtil.canViewArticle(article, viewer("reader", "Reader"), Set.of("Reader")));
        Assert.assertTrue(ApiV3ContentUtil.canViewArticle(article, viewer("author", "Author"), Set.of()));
        Assert.assertTrue(ApiV3ContentUtil.canViewArticle(article,
                viewer("admin", "Admin").put(User.USER_ROLE, Role.ROLE_ID_C_ADMIN), Set.of()));
    }

    @Test
    public void privateCommentIsNotAnAdminPrivilege() {
        final JSONObject comment = new JSONObject().put(Comment.COMMENT_AUTHOR_ID, "commenter")
                .put(Comment.COMMENT_VISIBLE, Comment.COMMENT_VISIBLE_C_AUTHOR);
        Assert.assertFalse(ApiV3ContentUtil.canViewCommentContent(comment, article(), null));
        Assert.assertFalse(ApiV3ContentUtil.canViewCommentContent(comment, article(), viewer("other", "Other")));
        Assert.assertFalse(ApiV3ContentUtil.canViewCommentContent(comment, article(),
                viewer("admin", "Admin").put(User.USER_ROLE, Role.ROLE_ID_C_ADMIN)));
        Assert.assertTrue(ApiV3ContentUtil.canViewCommentContent(comment, article(), viewer("author", "Author")));
        Assert.assertTrue(ApiV3ContentUtil.canViewCommentContent(comment, article(), viewer("commenter", "Commenter")));
    }

    @Test
    public void privateProfileListsRequireOwnerOrAdmin() {
        final JSONObject target = viewer("target", "Target").put(UserExt.USER_ARTICLE_STATUS, UserExt.USER_XXX_STATUS_C_DISABLED);
        Assert.assertFalse(ApiV3ContentUtil.canViewUserList(target, null, UserExt.USER_ARTICLE_STATUS));
        Assert.assertFalse(ApiV3ContentUtil.canViewUserList(target, viewer("other", "Other"), UserExt.USER_ARTICLE_STATUS));
        Assert.assertTrue(ApiV3ContentUtil.canViewUserList(target, viewer("target", "Target"), UserExt.USER_ARTICLE_STATUS));
        Assert.assertTrue(ApiV3ContentUtil.canViewUserList(target,
                viewer("admin", "Admin").put(User.USER_ROLE, Role.ROLE_ID_C_ADMIN), UserExt.USER_ARTICLE_STATUS));
        target.put(UserExt.USER_ARTICLE_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        Assert.assertTrue(ApiV3ContentUtil.canViewUserList(target, null, UserExt.USER_ARTICLE_STATUS));
    }

    @Test
    public void anonymousAuthorCannotRevealIdentityEvenToSelf() {
        final JSONObject author = viewer("secret-id", "SecretName").put(UserExt.USER_NICKNAME, "SecretNick")
                .put(User.USER_EMAIL, "private@example.test").put(User.USER_PASSWORD, "private-password")
                .put("apiKey", "secret-key").put("userPhone", "secret-phone").put("userLatestLoginIP", "secret-ip");
        final JSONObject anonymous = ApiV3ContentUtil.authorDto(author, true);
        Assert.assertEquals(anonymous.optString("id"), "");
        Assert.assertFalse(anonymous.toString().contains("secret"));
        final JSONObject publicAuthor = ApiV3ContentUtil.authorDto(author, false);
        Assert.assertEquals(publicAuthor.keySet(), Set.of("id", "userName", "userNickname", "userAvatarURL"));
        Assert.assertEquals(author.optString("apiKey"), "secret-key");
    }
}
