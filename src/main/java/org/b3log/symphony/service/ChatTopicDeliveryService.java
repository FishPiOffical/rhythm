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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.util.ChatTopicValue;
import org.json.JSONObject;

/** Projection of committed state for old pages and legacy notifications; recoverable by the background worker. */
@Service
public class ChatTopicDeliveryService {
    @Inject private ChatTopicMgmtService topics;
    private ChatTopicValue applied;
    /** Re-read after commit; never apply a delayed callback's older topic over a newer committed one. */
    public synchronized void refreshCommitted()throws RepositoryException {
        final ChatTopicValue current=topics.readCommitted();
        if(current==null)return;
        if(applied!=null){
            if(current.revision()<applied.revision())throw new IllegalStateException("chat topic revision regressed");
            if(current.revision()==applied.revision()){
                if(!current.equals(applied))throw new IllegalStateException("chat topic changed without a revision");
                return;
            }
        }
        ChatroomChannel.discussing=current.text();
        applied=current;
        // Committed canonical state remains readable even if best-effort legacy delivery fails.
        publishLegacy(current);
    }
    protected void publishLegacy(final ChatTopicValue current){
        ChatroomChannel.notifyChat(new JSONObject().put("type","discussChanged").put("whoChanged",current.whoChanged())
                .put("newDiscuss",current.text()).put("topicRevision",Long.toString(current.revision())));
    }
}
