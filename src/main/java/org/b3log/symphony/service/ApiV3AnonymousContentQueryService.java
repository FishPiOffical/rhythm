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
package org.b3log.symphony.service;

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3AnonymousContentDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.UserMentionCandidates;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

/** Owner conditions are repeated in count/page SQL; GET never mutates or returns bodies. */
@Service
public class ApiV3AnonymousContentQueryService {
    @Inject private UserRepository users;
    @Inject private ArticleRepository articles;
    @Inject private CommentRepository comments;
    // Work budgets, never silent truncation or a substitute for honest pagination.
    private static final int DISCUSSION_BUDGET = 512;
    private static final long DISCUSSION_BYTES = 2L * 1024 * 1024;
    private void requireActor(final String actor) throws RepositoryException {
        if (actor == null || !actor.matches("[1-9][0-9]{0,31}")) throw ApiV3Requests.invalid();
        if (users.select("SELECT oId FROM `" + users.getName() + "` WHERE oId=? AND userStatus=?", actor,
                UserExt.USER_STATUS_C_VALID).size() != 1) throw ApiV3Requests.notFound();
    }
    public JSONObject articles(final String actor, final int page, final int size) {
        if (page < 1 || page > Integer.MAX_VALUE / 50 || size < 1 || size > 50) throw ApiV3Requests.invalid();
        try {
            requireActor(actor);
            final String from = " FROM `" + articles.getName() + "` a JOIN `" + users.getName()
                    + "` u ON u.oId=a.articleAuthorId WHERE a.articleAuthorId=? AND u.userStatus=?"
                    + " AND a.articleAnonymous=? AND a.articleStatus<>?";
            final var arguments = new Object[]{actor, UserExt.USER_STATUS_C_VALID,
                    Article.ARTICLE_ANONYMOUS_C_ANONYMOUS, Article.ARTICLE_STATUS_C_INVALID};
            final var totals = articles.select("SELECT COUNT(*) AS total" + from, arguments);
            if (totals.size() != 1) throw ApiV3AnonymousContentDto.unavailable();
            final var pageArguments = new Object[]{actor, UserExt.USER_STATUS_C_VALID,
                    Article.ARTICLE_ANONYMOUS_C_ANONYMOUS, Article.ARTICLE_STATUS_C_INVALID, size, (page - 1) * size};
            final var rows = articles.select("SELECT a.oId,a.articleTitle,a.articleType,a.articleCreateTime,a.articleUpdateTime"
                    + from + " ORDER BY a.articleCreateTime DESC,a.oId DESC LIMIT ? OFFSET ?", pageArguments);
            final var list = new JSONArray();
            for (final var row : rows) list.put(ApiV3AnonymousContentDto.article(row));
            requireActor(actor);
            return ApiV3Requests.pagination(list, page, size, totals.getFirst().getLong("total"));
        } catch (final RepositoryException | org.json.JSONException error) {
            LogManager.getLogger(getClass()).warn("Anonymous content read unavailable");
            throw ApiV3AnonymousContentDto.unavailable();
        }
    }

    public JSONObject comments(final String actor, final int page, final int size) {
        if (page < 1 || page > Integer.MAX_VALUE / 50 || size < 1 || size > 50) throw ApiV3Requests.invalid();
        try {
            final JSONObject viewer = viewer(actor);
            final var allowed = allowedDiscussions(actor, viewer);
            final StringBuilder from = new StringBuilder(" FROM `").append(comments.getName()).append("` c JOIN `")
                    .append(articles.getName()).append("` a ON a.oId=c.commentOnArticleId JOIN `")
                    .append(users.getName()).append("` au ON au.oId=a.articleAuthorId JOIN `")
                    .append(users.getName()).append("` v ON v.oId=c.commentAuthorId WHERE c.commentAuthorId=?")
                    .append(" AND v.userStatus=? AND v.userName=? AND v.userRole=? AND c.commentAnonymous=? AND c.commentStatus=?")
                    .append(" AND a.articleStatus<>? AND au.userStatus NOT IN (?,?)")
                    .append(" AND (a.articleType<>? OR a.articleAuthorId=v.oId OR ?=1");
            final List<Object> args = new ArrayList<>(List.of(actor, UserExt.USER_STATUS_C_VALID,
                    viewer.getString("userName"), viewer.getString("userRole"), Comment.COMMENT_ANONYMOUS_C_ANONYMOUS,
                    Comment.COMMENT_STATUS_C_VALID, Article.ARTICLE_STATUS_C_INVALID, UserExt.USER_STATUS_C_INVALID,
                    UserExt.USER_STATUS_C_DEACTIVATED, Article.ARTICLE_TYPE_C_DISCUSSION,
                    Role.ROLE_ID_C_ADMIN.equals(viewer.getString("userRole")) ? 1 : 0));
            for (final JSONObject permission : allowed) {
                from.append(" OR (a.oId=? AND SHA2(a.articleContent,256)=?)");
                args.add(permission.getString("oId")); args.add(permission.getString("contentHash"));
            }
            from.append(')');
            final var totals = comments.select("SELECT COUNT(*) AS total" + from, args.toArray());
            if (totals.size() != 1) throw ApiV3AnonymousContentDto.unavailable();
            final var pageArgs = new ArrayList<>(args); pageArgs.add(size); pageArgs.add((page - 1) * size);
            final var rows = comments.select("SELECT c.oId,a.oId AS articleId,a.articleTitle,c.commentCreateTime,c.commentVisible"
                    + from + " ORDER BY c.commentCreateTime DESC,c.oId DESC LIMIT ? OFFSET ?", pageArgs.toArray());
            final var list = new JSONArray();
            for (final var row : rows) list.put(ApiV3AnonymousContentDto.comment(row));
            final JSONObject current = viewer(actor);
            if (!viewer.getString("userName").equals(current.getString("userName"))
                    || !viewer.getString("userRole").equals(current.getString("userRole"))) throw ApiV3AnonymousContentDto.unavailable();
            return ApiV3Requests.pagination(list, page, size, totals.getFirst().getLong("total"));
        } catch (final RepositoryException | org.json.JSONException error) {
            LogManager.getLogger(getClass()).warn("Anonymous comments read unavailable");
            throw ApiV3AnonymousContentDto.unavailable();
        }
    }

    private JSONObject viewer(final String actor) throws RepositoryException {
        if (actor == null || !actor.matches("[1-9][0-9]{0,31}")) throw ApiV3Requests.invalid();
        final var rows = users.select("SELECT oId,userName,userRole FROM `" + users.getName()
                + "` WHERE oId=? AND userStatus=?", actor, UserExt.USER_STATUS_C_VALID);
        if (rows.size() != 1) throw ApiV3Requests.notFound();
        final var value = rows.getFirst();
        for (final String key : List.of("userName", "userRole")) {
            final String text = value.getString(key);
            if (text.isEmpty() || text.length() > 64 || text.codePoints().anyMatch(Character::isISOControl))
                throw ApiV3AnonymousContentDto.unavailable();
        }
        return value;
    }

    private List<JSONObject> allowedDiscussions(final String actor, final JSONObject viewer) throws RepositoryException {
        if (Role.ROLE_ID_C_ADMIN.equals(viewer.getString("userRole"))) return List.of();
        // Restrict candidate work to distinct foreign discussion articles referenced by this owner's valid anonymous comments.
        // Headers contain no bodies: their byte budget is checked before a bounded body projection is issued.
        final var headers = articles.select("SELECT a.oId,SHA2(a.articleContent,256) AS contentHash,OCTET_LENGTH(a.articleContent) AS contentBytes"
                + " FROM `" + articles.getName() + "` a JOIN `" + users.getName() + "` au ON au.oId=a.articleAuthorId"
                + " WHERE a.articleType=? AND a.articleAuthorId<>? AND a.articleStatus<>? AND au.userStatus NOT IN (?,?)"
                + " AND EXISTS (SELECT 1 FROM `" + comments.getName() + "` c WHERE c.commentOnArticleId=a.oId"
                + " AND c.commentAuthorId=? AND c.commentAnonymous=? AND c.commentStatus=?) ORDER BY a.oId ASC LIMIT ?",
                Article.ARTICLE_TYPE_C_DISCUSSION, actor, Article.ARTICLE_STATUS_C_INVALID, UserExt.USER_STATUS_C_INVALID,
                UserExt.USER_STATUS_C_DEACTIVATED, actor, Comment.COMMENT_ANONYMOUS_C_ANONYMOUS, Comment.COMMENT_STATUS_C_VALID,
                DISCUSSION_BUDGET + 1);
        if (headers.size() > DISCUSSION_BUDGET) throw ApiV3AnonymousContentDto.unavailable();
        if (headers.isEmpty()) return List.of();
        long bytes = 0;
        final StringBuilder where = new StringBuilder();
        final List<Object> args = new ArrayList<>();
        for (final var header : headers) {
            final long length = header.getLong("contentBytes");
            final String hash = header.getString("contentHash"), id = header.getString("oId");
            if (length < 0 || length > DISCUSSION_BYTES || (bytes += length) > DISCUSSION_BYTES
                    || !hash.matches("[a-f0-9]{64}") || !id.matches("[0-9]{1,32}")) throw ApiV3AnonymousContentDto.unavailable();
            if (!where.isEmpty()) where.append(" OR ");
            where.append("(a.oId=? AND SHA2(a.articleContent,256)=?)"); args.add(id); args.add(hash);
        }
        final var rows = articles.select("SELECT a.oId,a.articleContent,SHA2(a.articleContent,256) AS contentHash FROM `"
                + articles.getName() + "` a WHERE " + where, args.toArray());
        final var allowed = new ArrayList<JSONObject>();
        long actualBytes = 0;
        for (final var row : rows) {
            final String body = row.getString("articleContent");
            if ((actualBytes += body.getBytes(StandardCharsets.UTF_8).length) > DISCUSSION_BYTES) throw ApiV3AnonymousContentDto.unavailable();
            if (UserMentionCandidates.parse(body).contains(viewer.getString("userName")))
                allowed.add(new JSONObject().put("oId", row.getString("oId")).put("contentHash", row.getString("contentHash")));
        }
        return allowed;
    }
}
