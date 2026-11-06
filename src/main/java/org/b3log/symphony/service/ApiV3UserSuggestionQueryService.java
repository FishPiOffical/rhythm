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
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3CommunityDto;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Current, bounded public projections; never use the legacy process username cache. */
@Service
public class ApiV3UserSuggestionQueryService {
    @Inject private UserRepository users;
    public static String prefix(String value) {
        if (value == null) return "";
        if (!value.isEmpty() && !value.matches("[A-Za-z0-9_-]{1,64}")) throw ApiV3Requests.invalid();
        return value.toLowerCase(Locale.ROOT);
    }
    public JSONObject suggestions(String actor, String raw) {
        final String prefix = prefix(raw);
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.forbidden();
        try {
            if (users.select("SELECT oId FROM `" + users.getName() + "` WHERE oId=? AND userStatus=0 LIMIT 1", actor).size() != 1)
                throw ApiV3Requests.notFound();
            final JSONArray list = new JSONArray();
            if (prefix.isEmpty()) return new JSONObject().put("list", list);
            final var rows = users.select("SELECT oId,userName,userNickname,userAvatarURL FROM `" + users.getName()
                    + "` WHERE userStatus=0 AND LOWER(userName) LIKE ? ESCAPE '=' ORDER BY LOWER(userName),userName,oId LIMIT 10",
                    prefix.replace("_", "=_") + "%");
            if (rows.size() > 10) throw unavailable();
            for (final var row : rows) {
                final String id = row.optString("oId"), name = row.optString("userName");
                if (!id.matches("[1-9][0-9]{0,18}") || !name.matches("[A-Za-z0-9_-]{1,64}") || !name.toLowerCase(Locale.ROOT).startsWith(prefix)) throw unavailable();
                final String nickname = row.optString("userNickname");
                list.put(new JSONObject().put("id", id).put("userName", name)
                        .put("userNickname", nickname.length() <= 255 && nickname.codePoints().noneMatch(Character::isISOControl) ? nickname : "")
                        .put("userAvatarURL", ApiV3CommunityDto.safeUrl(row.optString("userAvatarURL"))));
            }
            return new JSONObject().put("list", list);
        } catch (RepositoryException e) { throw unavailable(); }
    }
    private static ApiV3Exception unavailable() { return new ApiV3Exception(503, "temporarily_unavailable", "用户联想暂不可用"); }
}
