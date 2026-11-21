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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.ChatListRepository;
import org.b3log.symphony.processor.ChatProcessor;
import org.b3log.symphony.util.ApiV3PrivateMessages;
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
        final String sql = "SELECT * FROM `" + chatListRepository.getName() + "` AS info WHERE substring_index(info.sessionId,'_',1) = ? OR substring_index(substring_index(info.sessionId,'_',2),'_',-1) = ? order by lastMessageId desc limit 128";
        List<JSONObject> lists = chatListRepository.select(sql,userId,userId);
        if (Objects.isNull(lists) || lists.size() == 0) {
            return new ArrayList<>();
        }
        return lists;
    }

    @Inject private PrivateMessageListStateService projection;

    public void update(final JSONObject message) {
        try{
            projection.refreshLegacy(message.optString("oId"));
            final String from=message.optString("fromId"),to=message.optString("toId");
            if(ApiV3PrivateMessages.validId(from)&&ApiV3PrivateMessages.validId(to))ChatProcessor.invalidateChatListCache(from,to);
        }
        catch(Exception e){LOGGER.warn("Private message list refresh unavailable");}
    }
}
