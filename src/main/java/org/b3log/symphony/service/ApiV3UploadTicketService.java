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
import org.apache.logging.log4j.ThreadContext;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

/**
 * 签发短期上传凭据，业务重放回执不存储已签名凭据。
 */
@Service
public class ApiV3UploadTicketService {
    @Inject
    private UserRepository users;

    public JSONObject issue(final String actor) throws Exception {
        if (null == actor || !actor.matches("[0-9]{1,32}")) throw ApiV3Requests.notFound();
        final var rows = users.select("SELECT oId,userName,userNickname FROM `" + users.getName()
                + "` WHERE oId=? AND userStatus=? LIMIT 1", actor, UserExt.USER_STATUS_C_VALID);
        if (rows.isEmpty()) throw ApiV3Requests.notFound();
        final JSONObject user = rows.getFirst();
        if (!Symphonys.RHPIC_ENABLED) throw unavailable();
        final String endpoint;
        final String ticket;
        try {
            endpoint = ApiV3UploadTargets.endpoint(Symphonys.RHPIC_UPLOAD_URL);
            ticket = RhypicUploadTickets.sign(Symphonys.RHPIC_TICKET_PRIVATE_KEY, user.getString(Keys.OBJECT_ID),
                    user.getString(User.USER_NAME), user.optString(UserExt.USER_NICKNAME), System.currentTimeMillis() / 1000);
        } catch (final Exception e) {
            // 签名或配置失败时隐藏密钥和凭据内容。
            LogManager.getLogger(ApiV3UploadTicketService.class).warn("api_v3_upload_ticket result=unavailable requestId={}", ThreadContext.get("apiV3RequestId"));
            throw unavailable();
        }
        LogManager.getLogger(ApiV3UploadTicketService.class).info("api_v3_upload_ticket userId={} result=issued requestId={}", actor, ThreadContext.get("apiV3RequestId"));
        return new JSONObject().put("ticket", ticket).put("tokenType", "Bearer").put("uploadURL", endpoint)
                .put("fieldName", "file").put("expiresIn", RhypicUploadTickets.TTL_SECONDS);
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "upload_unavailable", "上传服务暂不可用");
    }
}
