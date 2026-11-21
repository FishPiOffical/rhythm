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
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3AvatarUrl;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

/**
 * 只投影当前有效账号保存的头像地址，保留原地址用于个人设置。
 */
@Service
public class ApiV3AvatarQueryService {
    @Inject
    private UserRepository users;

    public JSONObject read(String actor) throws RepositoryException {
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}"))
            throw ApiV3WriteInputs.invalid();
        final java.util.List<JSONObject> rows;
        try {
            rows = users.select("SELECT userAvatarURL FROM `" + users.getName() + "` WHERE oId=? AND userStatus=?",
                    actor, UserExt.USER_STATUS_C_VALID);
        } catch (RepositoryException e) {
            org.apache.logging.log4j.LogManager.getLogger(getClass()).warn("Avatar settings read unavailable");
            throw new ApiV3Exception(503, "temporarily_unavailable", "头像设置暂不可用");
        }
        if (rows.size() != 1)
            throw ApiV3Requests.notFound();
        return new JSONObject().put("avatarURL", ApiV3AvatarUrl.stored(rows.getFirst().optString("userAvatarURL")));
    }
}
