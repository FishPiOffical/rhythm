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
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.model.Breezemoon;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.channel.UserChannel;
import org.b3log.symphony.util.ApiV3BreezemoonInputs;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.BreezemoonWriteQuota;
import org.b3log.symphony.util.ReservedWords;
import org.json.JSONObject;

import java.util.Set;

@Service
public class ApiV3BreezemoonMgmtService {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3BreezemoonMgmtService.class);
    @Inject
    private ApiV3IdempotencyMgmtService idempotency;
    @Inject
    private BreezemoonMgmtService management;
    @Inject
    private ApiV3DiscoveryQueryService discovery;
    @Inject
    private UserQueryService users;
    @Inject
    private RoleQueryService roles;

    public JSONObject create(final JSONObject viewer, final String raw, final String key, final String ip, final String ua, final String city) throws Exception {
        permission(viewer, Permission.PERMISSION_ID_C_COMMON_ADD_BREEZEMOON);
        final String actor = viewer.optString(Keys.OBJECT_ID);
        final ApiV3IdempotencyMgmtService.Execution execution = idempotency.executeWithOutcome(actor, "breezemoons.create", key, new JSONObject().put("content", raw), () -> {
            if (viewer.optString("userPhone").isEmpty())
                throw new ApiV3Exception(403, "phone_required", "请先绑定手机号码");
            if (!BreezemoonWriteQuota.access(actor))
                throw new ApiV3Exception(429, "too_many_requests", "操作过于频繁，请稍后再试");
            final String content = ApiV3BreezemoonInputs.publishable(ReservedWords.processReservedWord(raw));
            if (CensorFactory.getTextCensor().censor(content).isBlocked())
                throw new ApiV3Exception(422, "content_rejected", "内容审核未通过，请修改后重试");
            final JSONObject row = management.addBreezemoonInCurrentTransaction(new JSONObject().put(Breezemoon.BREEZEMOON_CONTENT, content)
                    .put(Breezemoon.BREEZEMOON_AUTHOR_ID, actor).put(Breezemoon.BREEZEMOON_IP, ip).put(Breezemoon.BREEZEMOON_UA, ua)
                    .put(Breezemoon.BREEZEMOON_CITY, city));
            return discovery.breezemoonDto(row);
        });
        if (execution.applied()) broadcast(execution.result(), actor);
        return execution.result();
    }

    public JSONObject update(final JSONObject viewer, final String id, final String raw, final String key, final String ip, final String ua) throws Exception {
        permission(viewer, Permission.PERMISSION_ID_C_COMMON_UPDATE_BREEZEMOON);
        return idempotency.execute(viewer.optString(Keys.OBJECT_ID), "breezemoons.update:" + id, key, new JSONObject().put("content", raw), () -> {
            final String content = ApiV3BreezemoonInputs.editable(ReservedWords.processReservedWord(raw));
            final JSONObject request = new JSONObject().put(Breezemoon.BREEZEMOON_CONTENT, content)
                    .put(Breezemoon.BREEZEMOON_IP, ip).put(Breezemoon.BREEZEMOON_UA, ua);
            final JSONObject row = management.updateOwnedInCurrentTransaction(viewer.optString(Keys.OBJECT_ID), id, request, () -> {
                if (!BreezemoonWriteQuota.access(viewer.optString(Keys.OBJECT_ID)))
                    throw new ApiV3Exception(429, "too_many_requests", "操作过于频繁，请稍后再试");
                if (CensorFactory.getTextCensor().censor(content).isBlocked())
                    throw new ApiV3Exception(422, "content_rejected", "内容审核未通过，请修改后重试");
            });
            return discovery.breezemoonDto(row);
        });
    }

    public JSONObject remove(final JSONObject viewer, final String id, final String key) throws Exception {
        permission(viewer, Permission.PERMISSION_ID_C_COMMON_REMOVE_BREEZEMOON);
        return idempotency.execute(viewer.optString(Keys.OBJECT_ID), "breezemoons.delete:" + id, key, new JSONObject(), () -> {
            management.removeOwnedInCurrentTransaction(viewer.optString(Keys.OBJECT_ID), id);
            return new JSONObject();
        });
    }

    private void permission(final JSONObject viewer, final String permission) throws Exception {
        if (!Permission.hasPermission(Set.of(permission), roles.getPermissions(viewer.optString(User.USER_ROLE))))
            throw ApiV3Requests.forbidden();
    }

    private void broadcast(final JSONObject result, final String actor) {
        try {
            final JSONObject user = users.getUser(actor);
            if (null == user || UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS)
                    || UserExt.USER_STATUS_C_DEACTIVATED == user.optInt(UserExt.USER_STATUS) || UserExt.USER_XXX_STATUS_C_ENABLED != user.optInt(UserExt.USER_BREEZEMOON_STATUS))
                return;
            final JSONObject author = result.getJSONObject("author");
            final JSONObject data = new JSONObject().put("oId", result.getString("id")).put("breezemoonContent", result.getString("contentHtml"))
                    .put("breezemoonAuthorName", author.optString("userName")).put("breezemoonAuthorThumbnailURL48", author.optString("userAvatarURL"));
            UserChannel.sendCmdToAll(new JSONObject().put("bz", data).put("command", "bz-update"));
        } catch (final Exception e) {
            LOGGER.warn("Failed to publish committed v3 breezemoon update");
        }
    }
}
