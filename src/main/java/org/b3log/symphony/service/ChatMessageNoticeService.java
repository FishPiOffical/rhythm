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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.processor.ChatroomProcessor;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;

/** Best-effort system notices after a confirmed publication; never impersonates its user's profession/activity. */
@Service
public class ChatMessageNoticeService {
    @Inject private ChatRoomRepository messages;
    @Inject private ChatroomProcessor delivery;
    public void send(final String text) throws RepositoryException {
        if (messages.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("notice before publication commit");
        try {
            final JSONObject message = new JSONObject().put("userName", "马库斯").put("userNickname", "RK200")
                    .put("userAvatarURL", "https://file.fishpi.cn/2022/01/robot3-89631199.png")
                    .put("userOId", 0L).put("sysMetal", "").put("client", "Other/Robot")
                    .put("content", text).put("time", System.currentTimeMillis());
            final Transaction transaction = ApiV3Transactions.strict(messages.beginTransaction());
            try {
                message.put(Keys.OBJECT_ID, messages.add(new JSONObject().put("content", message.toString())));
                transaction.commit();
            } finally { if (transaction.isActive()) transaction.rollback(); }
            delivery.afterApiBotNoticePublished(message);
        } finally { ApiV3Transactions.releaseThreadResources(); }
    }
}
