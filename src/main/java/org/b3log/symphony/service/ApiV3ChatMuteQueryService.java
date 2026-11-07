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
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3CommunityDto;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Read actual per-account restrictions, never the legacy username display snapshot. */
@Service
public class ApiV3ChatMuteQueryService {
    @Inject private CloudRepository cloud;
    @Inject private UserRepository users;
    private static final int READ_BUDGET = 10000;
    protected long now() { return System.currentTimeMillis(); }
    protected List<JSONObject> rows() throws RepositoryException {
        return cloud.select("SELECT c.userId,c.gameId,c.data,u.oId,u.userName,u.userNickname,u.userAvatarURL FROM `"
                + cloud.getName() + "` c INNER JOIN `" + users.getName() + "` u ON u.oId=c.userId"
                + " WHERE c.gameId=? AND u.userStatus=0 ORDER BY c.oId LIMIT " + (READ_BUDGET + 1), "sys-mute");
    }
    public JSONObject list(final int page, final int size) {
        if(page < 1 || page > Integer.MAX_VALUE / 50 || size < 1 || size > 50) throw ApiV3Requests.invalid();
        try {
            final var rows = rows();
            if(rows.size() > READ_BUDGET) throw unavailable();
            final long now = now();
            final var seen = new HashSet<String>();
            final var active = new ArrayList<JSONObject>();
            for(final var row : rows) {
                final String id = row.optString("oId"), value = row.optString("data"), name = row.optString("userName");
                if(!id.matches("[1-9][0-9]{0,18}") || !id.equals(row.optString("userId"))
                        || !"sys-mute".equals(row.optString("gameId")) || !seen.add(id)
                        || !value.matches("[0-9]{1,19}")) throw unavailable();
                final long until;
                try { until = Long.parseLong(value); } catch(NumberFormatException e) { throw unavailable(); }
                if(until <= now) continue;
                if(!name.matches("[A-Za-z0-9_-]{1,64}")) throw unavailable();
                final String nickname = row.optString("userNickname");
                active.add(new JSONObject().put("id",id).put("userName",name)
                        .put("userNickname",nickname.length() <= 255 && nickname.codePoints().noneMatch(Character::isISOControl) ? nickname : "")
                        .put("userAvatarURL",ApiV3CommunityDto.safeUrl(row.optString("userAvatarURL"))).put("expiresAt",until));
            }
            active.sort(Comparator.comparingLong((JSONObject row) -> row.getLong("expiresAt")).thenComparing(row -> row.getString("id")));
            final int start = Math.min((page-1)*size, active.size());
            final JSONArray list = new JSONArray();
            for(final var row : active.subList(start,Math.min(start+size,active.size()))) list.put(row);
            return ApiV3Requests.pagination(list,page,size,active.size()).put("hasMore",start+list.length()<active.size());
        } catch(RepositoryException e) { throw unavailable(); }
    }
    private static ApiV3Exception unavailable() { return new ApiV3Exception(503,"temporarily_unavailable","思过崖暂不可用"); }
}
