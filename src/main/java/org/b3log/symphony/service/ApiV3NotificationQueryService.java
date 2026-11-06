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
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.service.LangPropsService;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.model.Reward;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.NotificationRepository;
import org.b3log.symphony.repository.RewardRepository;
import org.b3log.symphony.util.ApiV3NotificationTypes;
import org.b3log.symphony.util.ApiV3NotificationDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.Escapes;
import org.b3log.symphony.util.Markdowns;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

/** Owned notification DTOs. Related content is resolved with the explicit Bearer viewer. */
@Service
public class ApiV3NotificationQueryService {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3NotificationQueryService.class);
    @Inject private NotificationRepository notificationRepository;
    @Inject private CommentQueryService commentQueryService;
    @Inject private RewardRepository rewardRepository;
    @Inject private NotificationQueryService notificationQueryService;
    @Inject private LangPropsService langPropsService;
    @Inject private ApiV3ContentQueryService contentQueryService;
    @Inject private ApiV3ContentDtoService contentDtoService;
    @Inject private ChatRoomService chatRoomService;

    public JSONObject list(final JSONObject viewer, final String type, final String status, final int p, final int size) throws RepositoryException {
        final String userId = viewer.optString(Keys.OBJECT_ID);
        final JSONArray list = new JSONArray();
        for (final JSONObject row : notificationRepository.getV3Page(userId, type, status, p, size)) list.put(dto(row, viewer));
        return ApiV3Requests.pagination(list, p, size, notificationRepository.countV3(userId, type, status));
    }
    public JSONObject get(final JSONObject viewer, final String id) throws RepositoryException {
        final JSONObject row = notificationRepository.getOwned(viewer.optString(Keys.OBJECT_ID), id);
        if (null == row) throw ApiV3Requests.notFound();
        return dto(row, viewer);
    }
    public JSONObject unread(final String userId) throws RepositoryException {
        final JSONObject categories = new JSONObject();
        for (final String category : ApiV3NotificationTypes.CATEGORIES.keySet()) categories.put(category, 0L);
        categories.put("other", 0L);
        long total = 0;
        for (final JSONObject row : notificationRepository.countV3Unread(userId)) {
            final String category = ApiV3NotificationTypes.category(row.optInt(Notification.NOTIFICATION_DATA_TYPE));
            final long count = row.optLong("total");
            categories.put(category, categories.optLong(category) + count);
            total += count;
        }
        return new JSONObject().put("total", total).put("categories", categories);
    }

    private JSONObject dto(final JSONObject row, final JSONObject viewer) throws RepositoryException {
        final int type = row.optInt(Notification.NOTIFICATION_DATA_TYPE);
        final JSONObject dto = ApiV3NotificationDto.base(row);
        final String dataId = row.optString(Notification.NOTIFICATION_DATA_ID);
        try {
            if ("point".equals(dto.optString("category"))) {
                final String html = contentDescriptionType(type) ? currentContentDescription(row, viewer, dto)
                        : notificationQueryService.getPointNotificationDescription(row, viewer.optString(Keys.OBJECT_ID));
                if (null == html) return unavailable(dto);
                description(dto, html);
            } else if ("sys-announce".equals(dto.optString("category"))) {
                if (type == Notification.DATA_TYPE_C_SYS_ANNOUNCE_ARTICLE) {
                    final String html = currentContentDescription(row, viewer, dto);
                    if (html == null) return unavailable(dto);
                    description(dto, html);
                } else description(dto, notificationQueryService.getSysAnnouncementDescription(row));
            } else if (type == Notification.DATA_TYPE_C_CHAT_ROOM_AT || type == Notification.DATA_TYPE_C_RED_PACKET) {
                chat(dto, dataId, type);
            } else if (type == Notification.DATA_TYPE_C_NEW_FOLLOWER) {
                final JSONObject actor = contentQueryService.currentAuthor(dataId);
                if (null == actor) return unavailable(dto);
                dto.put("actor", contentDtoService.author(actor, false));
                dto.put("url", "/member/" + actor.optString(User.USER_NAME));
            } else if (type == Notification.DATA_TYPE_C_ARTICLE_NEW_FOLLOWER || type == Notification.DATA_TYPE_C_ARTICLE_NEW_WATCHER
                    || type == Notification.DATA_TYPE_C_COMMENT_VOTE_UP || type == Notification.DATA_TYPE_C_COMMENT_VOTE_DOWN
                    || type == Notification.DATA_TYPE_C_ARTICLE_VOTE_UP || type == Notification.DATA_TYPE_C_ARTICLE_VOTE_DOWN) {
                final String[] parts = dataId.split("-", 2);
                if (parts.length != 2) return unavailable(dto);
                final boolean comment = type == Notification.DATA_TYPE_C_COMMENT_VOTE_UP || type == Notification.DATA_TYPE_C_COMMENT_VOTE_DOWN;
                if (!(comment ? comment(dto, parts[0], viewer, false) : article(dto, parts[0], viewer))) return unavailable(dto);
                final JSONObject actor = contentQueryService.currentAuthor(parts[1]);
                if (null != actor) dto.put("actor", contentDtoService.author(actor, false));
            } else if (type == Notification.DATA_TYPE_C_COMMENT || type == Notification.DATA_TYPE_C_COMMENTED || type == Notification.DATA_TYPE_C_REPLY
                    || type == Notification.DATA_TYPE_C_FOLLOWING_ARTICLE_COMMENT) {
                if (!comment(dto, dataId, viewer, true)) return unavailable(dto);
            } else if (type == Notification.DATA_TYPE_C_AT) {
                final JSONObject comment = contentQueryService.currentComment(dataId);
                if (null != comment) {
                    if (!comment(dto, dataId, viewer, true)) return unavailable(dto);
                } else if (!article(dto, dataId, viewer)) return unavailable(dto);
            } else if ("broadcast".equals(dto.optString("category")) || "following".equals(dto.optString("category"))) {
                if (!article(dto, dataId, viewer)) return unavailable(dto);
            } else return unavailable(dto);
        } catch (final JSONException | NullPointerException | IndexOutOfBoundsException e) {
            // Historical notifications can outlive their target or contain old, malformed references.
            LOGGER.warn("Cannot render legacy notification id={}", row.optString(Keys.OBJECT_ID));
            return unavailable(dto);
        }
        if (dto.optString("descriptionHtml").isEmpty()) description(dto, dto.optString("title"));
        return dto;
    }

    private boolean article(final JSONObject dto, final String id, final JSONObject viewer) throws RepositoryException {
        final JSONObject article = contentQueryService.viewableArticle(id, viewer);
        if (null == article) return false;
        dto.put("article", contentDtoService.summary(article)).put("url", article.optString(Article.ARTICLE_PERMALINK));
        return true;
    }

    private boolean comment(final JSONObject dto, final String id, final JSONObject viewer, final boolean includeContent) throws RepositoryException {
        final JSONObject comment = contentQueryService.currentComment(id);
        if (null == comment || Comment.COMMENT_STATUS_C_VALID != comment.optInt(Comment.COMMENT_STATUS)) return false;
        final String articleId = comment.optString(Comment.COMMENT_ON_ARTICLE_ID);
        final JSONObject article = contentQueryService.viewableArticle(articleId, viewer);
        if (null == article) return false;
        final JSONObject publicComment = contentQueryService.commentDto(comment, article, viewer);
        dto.put("article", contentDtoService.summary(article)).put("comment", publicComment)
                .put("url", article.optString(Article.ARTICLE_PERMALINK) + "#" + comment.optString(Keys.OBJECT_ID));
        final int mode = viewer.optInt(UserExt.USER_COMMENT_VIEW_MODE) == UserExt.USER_COMMENT_VIEW_MODE_C_REALTIME
                ? UserExt.USER_COMMENT_VIEW_MODE_C_REALTIME : UserExt.USER_COMMENT_VIEW_MODE_C_TRADITIONAL;
        final String target = commentQueryService.getCommentURL(id, mode, Symphonys.ARTICLE_COMMENTS_CNT);
        if (null != target) {
            final var anchor = Jsoup.parseBodyFragment(target).selectFirst("a[href]");
            if (null != anchor) {
                try {
                    final String href = anchor.attr("href");
                    final var location = java.net.URI.create(href);
                    if (("/article/" + articleId).equals(location.getPath()) && id.equals(location.getFragment())) dto.put("url", href);
                } catch (final IllegalArgumentException ignored) { /* Keep the current target URL. */ }
            }
        }
        if (includeContent && publicComment.optBoolean("contentVisible")) description(dto, publicComment.optString("contentHtml"));
        return true;
    }

    /** Content descriptions must use the authorized current target, not legacy cached title/link generators. */
    private static boolean contentDescriptionType(final int type) {
        return type == Notification.DATA_TYPE_C_POINT_PERFECT_ARTICLE || type == Notification.DATA_TYPE_C_POINT_ARTICLE_THANK
                || type == Notification.DATA_TYPE_C_POINT_ARTICLE_REWARD || type == Notification.DATA_TYPE_C_POINT_COMMENT_ACCEPT
                || type == Notification.DATA_TYPE_C_POINT_COMMENT_THANK || type == Notification.DATA_TYPE_C_SYS_ANNOUNCE_ARTICLE;
    }

    private String currentContentDescription(final JSONObject row, final JSONObject viewer, final JSONObject dto) throws RepositoryException {
        final int type = row.optInt(Notification.NOTIFICATION_DATA_TYPE);
        String articleId = row.optString(Notification.NOTIFICATION_DATA_ID);
        String actorId = null;
        if (type != Notification.DATA_TYPE_C_POINT_PERFECT_ARTICLE && type != Notification.DATA_TYPE_C_SYS_ANNOUNCE_ARTICLE) {
            final JSONObject reward = rewardRepository.get(articleId);
            if (reward == null) return null;
            articleId = reward.optString(Reward.DATA_ID);
            actorId = reward.optString(Reward.SENDER_ID);
            if (type == Notification.DATA_TYPE_C_POINT_COMMENT_THANK) {
                final JSONObject comment = contentQueryService.currentComment(articleId);
                if (comment == null || comment.optInt(Comment.COMMENT_STATUS) != Comment.COMMENT_STATUS_C_VALID) return null;
                articleId = comment.optString(Comment.COMMENT_ON_ARTICLE_ID);
            }
        }
        final JSONObject article = contentQueryService.viewableArticle(articleId, viewer);
        if (article == null) return null;
        if (type == Notification.DATA_TYPE_C_SYS_ANNOUNCE_ARTICLE) {
            dto.put("article", contentDtoService.summary(article)).put("url", article.optString(Article.ARTICLE_PERMALINK));
        }
        final String label = switch (type) {
            case Notification.DATA_TYPE_C_POINT_ARTICLE_THANK -> "notificationArticleThankLabel";
            case Notification.DATA_TYPE_C_POINT_ARTICLE_REWARD -> "notificationArticleRewardLabel";
            case Notification.DATA_TYPE_C_POINT_COMMENT_ACCEPT -> "notificationCmtAcceptLabel";
            case Notification.DATA_TYPE_C_POINT_COMMENT_THANK -> "notificationCmtThankLabel";
            case Notification.DATA_TYPE_C_POINT_PERFECT_ARTICLE -> "notificationPointPerfectArticleLabel";
            case Notification.DATA_TYPE_C_SYS_ANNOUNCE_ARTICLE -> "notificationSysArticleLabel";
            default -> throw new IllegalArgumentException("content notification type");
        };
        final String link = "<a href=\"" + Escapes.escapeHTML(article.optString(Article.ARTICLE_PERMALINK)) + "\">"
                + Escapes.escapeHTML(article.optString(Article.ARTICLE_TITLE)) + "</a>";
        String html = langPropsService.get(label).replace("{article}", link);
        if (actorId != null) {
            if (type == Notification.DATA_TYPE_C_POINT_COMMENT_ACCEPT) actorId = article.optString(Article.ARTICLE_AUTHOR_ID);
            final JSONObject actor = contentQueryService.currentAuthor(actorId);
            if (actor == null) return null;
            final String name = Escapes.escapeHTML(actor.optString(User.USER_NAME));
            html = html.replace("{user}", "<a href=\"/member/" + name + "\" class=\"name-at\" aria-label=\"" + name + "\">" + name + "</a> ");
        }
        return html;
    }

    private void chat(final JSONObject dto, final String id, final int type) throws RepositoryException {
        final JSONObject row = chatRoomService.getChatMsg(id);
        if (null == row) { unavailable(dto); return; }
        final JSONObject message = new JSONObject(row.optString(Common.CONTENT));
        String text = message.optString(Common.CONTENT);
        boolean sourceText = type == Notification.DATA_TYPE_C_RED_PACKET;
        if (sourceText) text = new JSONObject(text).optString("msg");
        else if (text.stripLeading().startsWith("{")) {
            JSONObject envelope = null;
            try { envelope = new JSONObject(text); } catch (final JSONException ignored) { }
            if (null != envelope && !envelope.optString("msgType").isEmpty()) {
                text = envelope.optString("msg", dto.optString("title"));
                sourceText = true;
            }
        }
        description(dto, sourceText ? Escapes.escapeHTML(text) : text);
        final String actorId = message.optString("userOId");
        // Names in legacy messages may have been reassigned; only a stable ID identifies the actor.
        final JSONObject actor = actorId.matches("[1-9][0-9]{0,18}") ? contentQueryService.currentAuthor(actorId) : null;
        if (null != actor) dto.put("actor", contentDtoService.author(actor, false));
        dto.put("url", "/cr");
    }

    private static void description(final JSONObject dto, final String html) {
        final String safe = Markdowns.clean(Emotions.convert(html), "");
        dto.put("descriptionHtml", safe).put("description", Jsoup.parse(safe).text());
    }
    private static JSONObject unavailable(final JSONObject dto) {
        return ApiV3NotificationDto.unavailable(dto);
    }
}
