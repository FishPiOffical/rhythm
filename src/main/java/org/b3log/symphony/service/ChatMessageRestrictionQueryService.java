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
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.util.ChatMessageRestrictions;
import java.util.HashMap;
import java.util.Map;

/** Current locked restriction reads; expired records need no cleanup during a publication transaction. */
@Service
public class ChatMessageRestrictionQueryService {
    @Inject private CloudRepository cloud;
    public ChatMessageRestrictions.State readInCurrentTransaction(final String actor) throws RepositoryException {
        if (!cloud.hasTransactionBegun()) throw new IllegalStateException("chat restriction transaction required");
        if (actor == null || !actor.matches("[0-9]{1,32}")) throw new IllegalArgumentException("chat actor");
        final var rows = cloud.select("SELECT userId,gameId,data FROM `" + cloud.getName()
                + "` WHERE (userId=? AND gameId=?) OR (userId=? AND gameId=?) OR (userId=? AND gameId=?) ORDER BY oId DESC LIMIT 4 FOR UPDATE",
                "all:fish:mute", "sys-mute", actor, "sys-mute", actor, "sys-risk");
        final Map<String, Long> expiry = new HashMap<>();
        for (final var row : rows) {
            final String owner = row.getString("userId"), game = row.getString("gameId");
            final String key = owner + "/" + game;
            if (!(owner.equals("all:fish:mute") && game.equals("sys-mute") || owner.equals(actor) && (game.equals("sys-mute") || game.equals("sys-risk")))) {
                throw new RepositoryException("unexpected chat restriction owner");
            }
            final String data = row.optString("data");
            try {
                if (!data.matches("[0-9]{1,19}") || expiry.putIfAbsent(key, Long.parseLong(data)) != null) {
                    throw new IllegalArgumentException("invalid or duplicate restriction");
                }
            } catch (final IllegalArgumentException e) { throw new RepositoryException(e); }
        }
        return new ChatMessageRestrictions.State(expiry.getOrDefault("all:fish:mute/sys-mute", 0L),
                expiry.getOrDefault(actor + "/sys-mute", 0L), expiry.getOrDefault(actor + "/sys-risk", 0L));
    }
}
