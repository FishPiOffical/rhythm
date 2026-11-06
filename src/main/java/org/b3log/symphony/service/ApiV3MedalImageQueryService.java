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
import org.b3log.symphony.repository.MedalRepository;
import org.b3log.symphony.repository.UserMedalRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONObject;

/** Current grant and definition only; never calls the legacy query's expired-grant cleanup. */
@Service
public class ApiV3MedalImageQueryService {
    @Inject private UserRepository users;
    @Inject private MedalRepository medals;
    @Inject private UserMedalRepository grants;
    public JSONObject definition(final String actor, final String name, final String medalId) {
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}") || medalId == null || medalId.isBlank() || medalId.length()>128
                || medalId.codePoints().anyMatch(c -> Character.isISOControl(c) || c>=0xD800 && c<=0xDFFF)
                || name == null || !name.isEmpty() && !name.matches("[A-Za-z0-9_-]{1,64}")) throw ApiV3Requests.invalid();
        final boolean owner = name.isEmpty();
        try {
            final String sql = "SELECT DISTINCT m.medal_id,m.medal_name,m.medal_attr FROM `" + medals.getName()
                    + "` m JOIN `" + grants.getName() + "` g ON BINARY g.medal_id=BINARY m.medal_id JOIN `" + users.getName()
                    + "` u ON u.oId=g.user_id WHERE BINARY m.medal_id=? AND "
                    + (owner ? "u.oId=? AND u.userStatus=0 AND (g.expire_time<=0 OR g.expire_time>?)"
                    : "u.userName=? AND u.userStatus IN (0,3) AND g.display=? AND (g.expire_time=0 OR g.expire_time>?)")
                    + " AND EXISTS (SELECT 1 FROM `" + users.getName() + "` a WHERE a.oId=? AND a.userStatus=0) LIMIT 2";
            final var rows = owner ? medals.select(sql, medalId, actor, System.currentTimeMillis(), actor)
                    : medals.select(sql, medalId, name, true, System.currentTimeMillis(), actor);
            if (rows.isEmpty()) throw ApiV3Requests.notFound();
            if (rows.size() != 1) throw unavailable();
            return rows.getFirst();
        } catch (RepositoryException error) { throw unavailable(); }
    }
    private static ApiV3Exception unavailable() { return new ApiV3Exception(503,"temporarily_unavailable","勋章图片暂不可用"); }
}
