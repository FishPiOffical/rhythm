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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.MedalRepository;
import org.b3log.symphony.repository.UserMedalRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MedalDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Owner sees hidden grants; public profile sees only currently displayed, unexpired grants. No cleanup writes. */
@Service
public class ApiV3MedalQueryService {
    @Inject private UserRepository users;
    @Inject private MedalRepository medals;
    @Inject private UserMedalRepository grants;
    public JSONObject mine(final String actor, final int page, final int size) {
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
        return read(actor, true, page, size);
    }
    public JSONObject displayed(final String name, final int page, final int size) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,64}")) throw ApiV3Requests.invalid();
        return read(name, false, page, size);
    }
    private JSONObject read(final String identity, final boolean owner, final int page, final int size) {
        if (page < 1 || page > Integer.MAX_VALUE / 50 || size < 1 || size > 50) throw ApiV3Requests.invalid();
        try {
            final String userKey = owner ? "oId" : "userName";
            final String statuses = owner ? "=?" : " IN (?,?)";
            final Object[] userArgs = owner ? new Object[]{identity, UserExt.USER_STATUS_C_VALID}
                    : new Object[]{identity, UserExt.USER_STATUS_C_VALID, UserExt.USER_STATUS_C_INVALID_LOGIN};
            if (users.select("SELECT oId FROM `" + users.getName() + "` WHERE " + userKey + "=? AND userStatus" + statuses,
                    userArgs).size() != 1) throw ApiV3Requests.notFound();
            // Apply the same account, owner, visibility and time predicates to count and page.
            final long now = System.currentTimeMillis();
            final String from = " FROM `" + grants.getName() + "` um JOIN `" + medals.getName()
                    + "` m ON m.medal_id=um.medal_id JOIN `" + users.getName() + "` u ON u.oId=um.user_id"
                    + " WHERE u." + userKey + "=? AND u.userStatus" + statuses
                    + " AND (um.expire_time" + (owner ? "<=0" : "=0") + " OR um.expire_time>?)"
                    + (owner ? "" : " AND um.display=?");
            final Object[] args = owner ? new Object[]{identity, UserExt.USER_STATUS_C_VALID, now}
                    : new Object[]{identity, UserExt.USER_STATUS_C_VALID, UserExt.USER_STATUS_C_INVALID_LOGIN, now, true};
            final long total = grants.select("SELECT COUNT(*) AS total" + from, args).getFirst().optLong("total");
            final Object[] pageArgs = new Object[args.length + 2];
            System.arraycopy(args, 0, pageArgs, 0, args.length); pageArgs[args.length] = size; pageArgs[args.length + 1] = (page - 1) * size;
            final List<JSONObject> rows = grants.select("SELECT m.medal_id,m.medal_name,m.medal_type,m.medal_description,um.data,um.expire_time,um.display,um.display_order"
                    + from + " ORDER BY um.display_order ASC,um.oId ASC,m.oId ASC LIMIT ? OFFSET ?", pageArgs);
            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows) list.put(ApiV3MedalDto.project(row, owner));
            return ApiV3Requests.pagination(list, page, size, total);
        } catch (final RepositoryException e) {
            LogManager.getLogger(getClass()).warn("Medal reads unavailable");
            throw new ApiV3Exception(503, "temporarily_unavailable", "勋章暂不可用");
        }
    }
}
