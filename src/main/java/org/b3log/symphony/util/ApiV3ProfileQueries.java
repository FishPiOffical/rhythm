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
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Follow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 使用绑定参数构造查询片段，表名仅来自 Repository#getName。 */
public final class ApiV3ProfileQueries {
    private ApiV3ProfileQueries() { }
    public record Fragment(String sql, List<Object> args) { }

    public static Fragment following(final String follows, final String tagArticles, final String tags,
                                     final String owner, final String source, final int validTagStatus) {
        if (!Set.of("all", "users", "tags", "articles").contains(source)) throw new IllegalArgumentException("Unknown following source");
        final List<Object> args = new ArrayList<>();
        final List<String> predicates = new ArrayList<>();
        if ("all".equals(source) || "users".equals(source)) {
            // 按作者发现文章时排除匿名文章，避免暴露它与被关注用户的关系。
            predicates.add("(a.articleAnonymous=? AND EXISTS (SELECT 1 FROM " + follows
                    + " fu WHERE fu.followerId=? AND fu.followingId=a.articleAuthorId AND fu.followingType=?))");
            args.add(Article.ARTICLE_ANONYMOUS_C_PUBLIC); args.add(owner); args.add(Follow.FOLLOWING_TYPE_C_USER);
        }
        if ("all".equals(source) || "tags".equals(source)) {
            predicates.add("EXISTS (SELECT 1 FROM " + follows + " ft JOIN " + tagArticles
                    + " ta ON ta.tag_oId=ft.followingId JOIN " + tags + " t ON t.oId=ta.tag_oId WHERE ft.followerId=?"
                    + " AND ft.followingType=? AND ta.article_oId=a.oId AND t.tagStatus=?)");
            args.add(owner); args.add(Follow.FOLLOWING_TYPE_C_TAG); args.add(validTagStatus);
        }
        if ("articles".equals(source)) {
            predicates.add("EXISTS (SELECT 1 FROM " + follows + " fw WHERE fw.followerId=? AND fw.followingId=a.oId AND fw.followingType=?)");
            args.add(owner); args.add(Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH);
        }
        return new Fragment(" AND (" + String.join(" OR ", predicates) + ")", List.copyOf(args));
    }

    /** 公开资料和文章发现列表都排除讨论帖。 */
    public static Fragment comments(final String comments, final String articles, final String users, final String owner,
                                    final boolean anonymousViewer, final int invalidUserStatus, final int deactivatedUserStatus, final String sandboxTag) {
        final StringBuilder from = new StringBuilder(" FROM ").append(comments).append(" c JOIN ").append(articles)
                .append(" a ON a.oId=c.commentOnArticleId JOIN ").append(users).append(" au ON au.oId=a.articleAuthorId WHERE ")
                .append("c.commentAuthorId=? AND c.commentAnonymous=? AND c.commentStatus=? AND a.articleStatus<>? AND a.articleType<>?")
                .append(" AND a.articleShowInList<>? AND a.articleTags<>? AND a.articleTags NOT LIKE ? AND au.userStatus NOT IN (?,?)");
        final List<Object> args = new ArrayList<>(List.of(owner, Comment.COMMENT_ANONYMOUS_C_PUBLIC, Comment.COMMENT_STATUS_C_VALID,
                Article.ARTICLE_STATUS_C_INVALID, Article.ARTICLE_TYPE_C_DISCUSSION, Article.ARTICLE_SHOW_IN_LIST_C_NOT,
                sandboxTag, "B3log%", invalidUserStatus, deactivatedUserStatus));
        if (anonymousViewer) {
            from.append(" AND a.articleAnonymousView<>?");
            args.add(Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW);
        }
        return new Fragment(from.toString(), List.copyOf(args));
    }
}
