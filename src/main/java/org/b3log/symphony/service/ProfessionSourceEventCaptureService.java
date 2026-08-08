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

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Breezemoon;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.ProfessionSourceEvent;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.repository.ProfessionSourceEventRepository;
import org.json.JSONObject;

/** 在业务写入事务中记录已注册的职业来源事件。 */
@Service
public class ProfessionSourceEventCaptureService {

    @Inject private ProfessionSourceEventRepository sourceEventRepository;

    public void articlePublished(final JSONObject article) throws RepositoryException {
        final JSONObject payload = new JSONObject().put("articleId", article.getString(Keys.OBJECT_ID))
                .put("authorUserId", article.getString(Article.ARTICLE_AUTHOR_ID))
                .put("articleType", article.getInt(Article.ARTICLE_TYPE))
                .put("tagCount", tagCount(article.optString(Article.ARTICLE_TAGS)))
                .put("wordCount", article.optString(Article.ARTICLE_CONTENT).codePointCount(0,
                        article.optString(Article.ARTICLE_CONTENT).length()))
                .put("publishedAt", article.getLong(Article.ARTICLE_CREATE_TIME));
        persist("article.published", article.getString(Keys.OBJECT_ID), article.getString(Article.ARTICLE_AUTHOR_ID),
                "article", article.getString(Keys.OBJECT_ID), payload, articleVisibility(article), article.getLong(Article.ARTICLE_CREATE_TIME));
    }

    public void commentPublished(final JSONObject comment, final JSONObject article) throws RepositoryException {
        final JSONObject payload = new JSONObject().put("commentId", comment.getString(Keys.OBJECT_ID))
                .put("articleId", comment.getString(Comment.COMMENT_ON_ARTICLE_ID))
                .put("authorUserId", comment.getString(Comment.COMMENT_AUTHOR_ID))
                .put("parentCommentId", comment.optString(Comment.COMMENT_ORIGINAL_COMMENT_ID))
                .put("publishedAt", comment.getLong(Comment.COMMENT_CREATE_TIME));
        persist("comment.published", comment.getString(Keys.OBJECT_ID), comment.getString(Comment.COMMENT_AUTHOR_ID),
                "comment", comment.getString(Keys.OBJECT_ID), payload, commentVisibility(comment, article),
                comment.getLong(Comment.COMMENT_CREATE_TIME));
    }

    public void breezemoonPublished(final JSONObject breezemoon) throws RepositoryException {
        final String id = breezemoon.getString(Keys.OBJECT_ID);
        final JSONObject payload = new JSONObject().put("breezemoonId", id)
                .put("authorUserId", breezemoon.getString(Breezemoon.BREEZEMOON_AUTHOR_ID))
                .put("publishedAt", breezemoon.getLong(Breezemoon.BREEZEMOON_CREATED));
        persist("breezemoon.published", id, breezemoon.getString(Breezemoon.BREEZEMOON_AUTHOR_ID),
                "breezemoon", id, payload, "PUBLIC", breezemoon.getLong(Breezemoon.BREEZEMOON_CREATED));
    }

    public void repeaterPublished(final JSONObject content) throws RepositoryException {
        final String id = content.getString(Keys.OBJECT_ID);
        final JSONObject payload = new JSONObject().put("contentId", id)
                .put("authorUserId", content.getString(RepeaterContent.AUTHOR_ID))
                .put("contentType", content.getString(RepeaterContent.TYPE))
                .put("publishedAt", content.getLong(RepeaterContent.CREATED_TIME));
        persist("repeater.published", id, content.getString(RepeaterContent.AUTHOR_ID), "repeater", id,
                payload, "PUBLIC", content.getLong(RepeaterContent.CREATED_TIME));
    }

    public void chatroomMessagePublished(final JSONObject message, final String userId) throws RepositoryException {
        final String id = message.getString(Keys.OBJECT_ID);
        final String content = message.optString("content");
        final JSONObject payload = new JSONObject().put("messageId", id).put("authorUserId", userId)
                .put("messageType", "TEXT").put("contentLength", content.codePointCount(0, content.length()))
                .put("sentAt", message.getLong("time"));
        persist("chatroom.message.published", id, userId, "chatroom_message", id, payload, "PUBLIC",
                message.getLong("time"));
    }

    /** 记录在线时长结算事实，经验计算完全由职业自动化规则决定。 */
    public void onlineMinutesSettled(final String userId, final int beforeMinute, final int afterMinute,
                                     final long occurredAt) throws RepositoryException {
        final int minuteDelta = afterMinute - beforeMinute;
        if (minuteDelta <= 0) {
            return;
        }
        final String eventId = Ids.genTimeMillisId();
        final JSONObject payload = new JSONObject().put("userId", userId)
                .put("onlineMinuteBefore", beforeMinute).put("onlineMinuteAfter", afterMinute)
                .put("onlineMinuteDelta", minuteDelta).put("settledAt", occurredAt);
        persistIfAbsent("user.online.settled", eventId, userId, "user", userId, payload, occurredAt,
                userId + ':' + beforeMinute + ':' + afterMinute);
    }

    public void targetRemoved(final String targetType, final String targetId) throws RepositoryException {
        final long now = System.currentTimeMillis();
        for (final JSONObject original : sourceEventRepository.getPositiveByTarget(targetType, targetId)) {
            persistReversal(original, now);
        }
    }

    private void persist(final String type, final String eventId, final String userId, final String targetType,
                         final String targetId, final JSONObject payload, final String visibility, final long occurredAt)
            throws RepositoryException {
        final long now = System.currentTimeMillis();
        final JSONObject event = new JSONObject();
        event.put(Keys.OBJECT_ID, eventId);
        event.put(ProfessionSourceEvent.EVENT_KEY, type + ':' + eventId);
        event.put(ProfessionSourceEvent.EVENT_TYPE, type);
        event.put(ProfessionSourceEvent.SCHEMA_VERSION, 1);
        event.put(ProfessionSourceEvent.SUBJECT_USER_ID, userId);
        event.put(ProfessionSourceEvent.ACTOR_USER_ID, userId);
        event.put(ProfessionSourceEvent.TARGET_TYPE, targetType);
        event.put(ProfessionSourceEvent.TARGET_ID, targetId);
        event.put(ProfessionSourceEvent.OCCURRED_AT, occurredAt);
        event.put(ProfessionSourceEvent.CAPTURED_AT, now);
        event.put(ProfessionSourceEvent.PAYLOAD_JSON, payload.toString());
        event.put(ProfessionSourceEvent.VISIBILITY_CLASS, visibility);
        event.put(ProfessionSourceEvent.SOURCE_SYSTEM, "rhythm");
        event.put(ProfessionSourceEvent.STATUS, "PENDING");
        event.put(ProfessionSourceEvent.CREATED_AT, now);
        event.put(ProfessionSourceEvent.UPDATED_AT, now);
        sourceEventRepository.add(event);
    }

    private void persistIfAbsent(final String type, final String eventId, final String userId, final String targetType,
                                 final String targetId, final JSONObject payload, final long occurredAt,
                                 final String idempotencySuffix) throws RepositoryException {
        final long now = System.currentTimeMillis();
        final JSONObject event = new JSONObject();
        event.put(Keys.OBJECT_ID, eventId);
        event.put(ProfessionSourceEvent.EVENT_KEY, type + ':' + idempotencySuffix);
        event.put(ProfessionSourceEvent.EVENT_TYPE, type);
        event.put(ProfessionSourceEvent.SCHEMA_VERSION, 1);
        event.put(ProfessionSourceEvent.SUBJECT_USER_ID, userId);
        event.put(ProfessionSourceEvent.ACTOR_USER_ID, userId);
        event.put(ProfessionSourceEvent.TARGET_TYPE, targetType);
        event.put(ProfessionSourceEvent.TARGET_ID, targetId);
        event.put(ProfessionSourceEvent.OCCURRED_AT, occurredAt);
        event.put(ProfessionSourceEvent.CAPTURED_AT, now);
        event.put(ProfessionSourceEvent.PAYLOAD_JSON, payload.toString());
        event.put(ProfessionSourceEvent.VISIBILITY_CLASS, "SELF");
        event.put(ProfessionSourceEvent.SOURCE_SYSTEM, "rhythm");
        event.put(ProfessionSourceEvent.STATUS, "PENDING");
        event.put(ProfessionSourceEvent.CREATED_AT, now);
        event.put(ProfessionSourceEvent.UPDATED_AT, now);
        sourceEventRepository.addIfAbsent(event);
    }

    private void persistReversal(final JSONObject original, final long occurredAt) throws RepositoryException {
        final long now = System.currentTimeMillis();
        final JSONObject event = new JSONObject();
        event.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        event.put(ProfessionSourceEvent.EVENT_KEY, "profession.reversal:" + original.getString(Keys.OBJECT_ID));
        event.put(ProfessionSourceEvent.EVENT_TYPE, "profession.source.reversed");
        event.put(ProfessionSourceEvent.SCHEMA_VERSION, 1);
        event.put(ProfessionSourceEvent.SUBJECT_USER_ID, original.getString(ProfessionSourceEvent.SUBJECT_USER_ID));
        event.put(ProfessionSourceEvent.ACTOR_USER_ID, original.optString(ProfessionSourceEvent.ACTOR_USER_ID));
        event.put(ProfessionSourceEvent.TARGET_TYPE, original.optString(ProfessionSourceEvent.TARGET_TYPE));
        event.put(ProfessionSourceEvent.TARGET_ID, original.optString(ProfessionSourceEvent.TARGET_ID));
        event.put(ProfessionSourceEvent.OCCURRED_AT, occurredAt);
        event.put(ProfessionSourceEvent.CAPTURED_AT, now);
        event.put(ProfessionSourceEvent.PAYLOAD_JSON, new JSONObject().put("reversedEventKey",
                original.getString(ProfessionSourceEvent.EVENT_KEY)).toString());
        event.put(ProfessionSourceEvent.VISIBILITY_CLASS, "SELF");
        event.put(ProfessionSourceEvent.REVERSAL_OF_EVENT_KEY, original.getString(ProfessionSourceEvent.EVENT_KEY));
        event.put(ProfessionSourceEvent.SOURCE_SYSTEM, "rhythm");
        event.put(ProfessionSourceEvent.STATUS, "PENDING");
        event.put(ProfessionSourceEvent.CREATED_AT, now);
        event.put(ProfessionSourceEvent.UPDATED_AT, now);
        sourceEventRepository.addIfAbsent(event);
    }

    private String articleVisibility(final JSONObject article) {
        return article.optInt(Article.ARTICLE_ANONYMOUS) == Article.ARTICLE_ANONYMOUS_C_PUBLIC
                && article.optInt(Article.ARTICLE_ANONYMOUS_VIEW) != Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW
                ? "PUBLIC" : "SELF";
    }

    private String commentVisibility(final JSONObject comment, final JSONObject article) {
        return comment.optInt(Comment.COMMENT_ANONYMOUS) == Comment.COMMENT_ANONYMOUS_C_PUBLIC
                && comment.optInt(Comment.COMMENT_VISIBLE) == Comment.COMMENT_VISIBLE_C_ALL
                && "PUBLIC".equals(articleVisibility(article)) ? "PUBLIC" : "SELF";
    }

    private int tagCount(final String tags) {
        return tags.isBlank() ? 0 : tags.split(",").length;
    }
}
