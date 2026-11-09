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
import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.processor.channel.ArticleChannel;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.util.ApiV3ReactionDto;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 首次写入确认提交后，尽力发布当前状态事件。
 */
@Service
public class ApiV3ReactionEventService {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3ReactionEventService.class);
    @Inject
    private ApiV3ReactionTargetService targets;
    @Inject
    private ReactionQueryService reactions;
    @Inject
    private UserQueryService users;

    public void publish(final JSONObject actor, final String type, final String id) {
        try {
            final JSONObject article = targets.read(actor, type, id);
            final JSONObject result = ApiV3ReactionDto.from(reactions.buildOperationResultOrThrow(type, id, actor.optString(Keys.OBJECT_ID)));
            final JSONArray summary = result.getJSONArray("summary");
            for (int i = 0; i < summary.length(); i++) summary.getJSONObject(i).put("selected", false);
            final JSONObject push = new JSONObject().put("targetId", id).put("targetType", type).put("groupType", Reaction.GROUP_EMOJI)
                    .put("summary", summary).put("actorUserId", actor.optString(Keys.OBJECT_ID)).put("actorReaction", result.getString("currentUserReaction"));
            if (Reaction.TARGET_TYPE_CHAT.equals(type)) {
                push.put(Common.TYPE, "chatReaction").put("oId", id);
                ChatroomChannel.notifyChat(push);
                return;
            }
            final String articleId = article.optString(Keys.OBJECT_ID);
            push.put(Article.ARTICLE_T_ID, articleId).put(Common.TYPE, Reaction.TARGET_TYPE_ARTICLE.equals(type) ? "articleReaction" : "commentReaction");
            if (Reaction.TARGET_TYPE_COMMENT.equals(type)) push.put(Comment.COMMENT_T_ID, id);
            final String json = push.toString();
            for (final WebSocketSession session : ArticleChannel.SESSIONS) {
                if (!articleId.equals(session.getParameter(Article.ARTICLE_T_ID))) continue;
                try {
                    targets.read(viewer(session), type, id);
                    session.sendText(json);
                } catch (final Exception deniedOrDisconnected) {
                    // 此会话无权读取或已断开，跳过发送。
                }
            }
        } catch (final Exception e) {
            LOGGER.warn("Failed to publish committed v3 reaction update");
        }
    }

    private JSONObject viewer(final WebSocketSession session) {
        JSONObject user = null;
        if (null != session.getHttpSession()) {
            final String json = session.getHttpSession().getAttribute(User.USER);
            if (null != json) user = new JSONObject(json);
        }
        final String key = session.getParameter("apiKey");
        if (null != key && !key.isEmpty()) user = ApiProcessor.getUserByKey(key);
        if (null != user) user = users.getUser(user.optString(Keys.OBJECT_ID));
        if (null != user && (UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS) || UserExt.USER_STATUS_C_DEACTIVATED == user.optInt(UserExt.USER_STATUS)))
            return null;
        return user;
    }
}
