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

import com.google.common.collect.Collections2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.*;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.CollectionUtils;
import org.b3log.symphony.repository.ChatListRepository;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ChatListService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(ChatListService.class);

    @Inject
    private ChatListRepository chatListRepository;

    public List<JSONObject> getChatList(final String userId) throws RepositoryException {
        final String sql = String.format("SELECT * FROM symphony_chat_list AS info WHERE substring_index(info.sessionId,'_',1) = '%s' OR substring_index(substring_index(info.sessionId,'_',2),'_',-1) = '%s' order by lastMessageId desc limit 128;", userId, userId);
        List<JSONObject> lists = chatListRepository.select(sql);
        if (Objects.isNull(lists) || lists.size() == 0) {
            return new ArrayList<>();
        }
        return lists;
    }

    private void add(final JSONObject message) {
        Transaction transaction = chatListRepository.beginTransaction();
        try {
            chatListRepository.add(message);
            transaction.commit();
        } catch (Exception e) {
            LOGGER.error("add chat list error", e);
            transaction.rollback();
        }

    }

    public void update(final JSONObject message) {
        String sessionId = message.optString("user_session");
        String time = message.optString("time");
        String lastMessageId = message.optString("oId");
        JSONObject chatList = new JSONObject();
        chatList.put("sessionId", sessionId);
        chatList.put("time", time);
        chatList.put("lastMessageId", lastMessageId);
        JSONObject oldData = null;
        try {
            oldData = chatListRepository.getFirst(new Query().setFilter(new PropertyFilter("sessionId", FilterOperator.EQUAL, sessionId)));
        } catch (Exception e) {
            LOGGER.error("get chat list error", e);
            return;
        }

        if (Objects.isNull(oldData)) {
            add(chatList);
        } else {
            Transaction transaction = chatListRepository.beginTransaction();
            try {
                chatListRepository.update(oldData.optString("oId"), chatList);
                transaction.commit();
            } catch (Exception e) {
                LOGGER.error("update chat list error", e);
                transaction.rollback();
            }

        }

    }
}
