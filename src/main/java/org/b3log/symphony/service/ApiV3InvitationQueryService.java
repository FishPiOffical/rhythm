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
import org.b3log.symphony.repository.InvitecodeRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Invitation;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONObject;

/** 查询单个指定邀请码的状态，隐藏预留和所有者字段。 */
@Service
public class ApiV3InvitationQueryService {
    @Inject private UserRepository users;
    @Inject private InvitecodeRepository invitations;
    private void requireActor(final String actor) throws RepositoryException {
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
        if (users.select("SELECT oId FROM `" + users.getName() + "` WHERE oId=? AND userStatus=?", actor,
                UserExt.USER_STATUS_C_VALID).size() != 1) throw ApiV3Requests.notFound();
    }
    public JSONObject status(final String actor, final String supplied) {
        final String code = ApiV3Invitation.code(supplied);
        try {
            requireActor(actor);
            final var rows = invitations.select("SELECT status FROM `" + invitations.getName() + "` WHERE code=? LIMIT 2", code);
            if (rows.isEmpty()) throw ApiV3Requests.notFound();
            if (rows.size() != 1) throw ApiV3Invitation.unavailable();
            final JSONObject result = ApiV3Invitation.status(rows.getFirst());
            requireActor(actor);
            return result;
        } catch (final RepositoryException e) {
            LogManager.getLogger(getClass()).warn("Invitation status read unavailable");
            throw ApiV3Invitation.unavailable();
        }
    }
}
