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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ApiV3Token;
import org.b3log.symphony.repository.ApiV3TokenRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

@Service
public class ApiV3DeviceSessionService {
    @Inject private ApiV3TokenRepository tokens;
    @Inject private ApiV3SecurityProofService proofs;

    private static final class Device {
        final TreeSet<String> families = new TreeSet<>();
        final String publicId;
        final String label;
        final long lastActiveAt;

        Device(final String actor, final JSONObject newest) throws Exception {
            publicId = ApiV3TokenUtil.hash("web-device:" + actor + ":" + newest.getString("websiteDeviceId"));
            label = newest.getString("activeLabel");
            lastActiveAt = newest.getLong("activity");
        }

        String handle() throws Exception {
            // 把当时的登录家族包含在操作编号中，避免迟到的退出指令踢掉后来重新登录的会话。
            return publicId + "." + ApiV3TokenUtil.hash(String.join(",", families));
        }
    }

    private Map<String, Device> activeDevices(final String actor, final long now) throws Exception {
        // 按家族判断会话是否仍有效；已到期的访问令牌也保存着这个家族的真实活动时间。
        final var rows = tokens.select("SELECT websiteDeviceId,familyId,"
                + " MAX(CASE WHEN state=? AND expiresAt>? THEN websiteDeviceLabel END) AS activeLabel,"
                + " MAX(GREATEST(createdAt,lastUsedAt)) AS activity FROM `" + tokens.getName()
                + "` WHERE clientId=? AND userId=? AND websiteDeviceId<>''"
                + " GROUP BY websiteDeviceId,familyId HAVING activeLabel IS NOT NULL"
                + " ORDER BY activity DESC,websiteDeviceId,familyId",
                ApiV3Token.STATE_ACTIVE, now, ApiV3TokenMgmtService.WEBSITE_CLIENT, actor);
        final Map<String, Device> devices = new LinkedHashMap<>();
        for (final var row : rows) {
            final String id = row.getString("websiteDeviceId");
            Device device = devices.get(id);
            if (device == null) {
                device = new Device(actor, row);
                devices.put(id, device);
            }
            device.families.add(row.getString("familyId"));
        }
        return devices;
    }

    private String current(final JSONObject identity, final String actor, final long now) throws Exception {
        final var rows = tokens.select("SELECT websiteDeviceId FROM `" + tokens.getName()
                + "` WHERE oId=? AND userId=? AND clientId=? AND tokenType=? AND state=? AND expiresAt>?",
                identity.getString("tokenId"), actor, ApiV3TokenMgmtService.WEBSITE_CLIENT,
                ApiV3Token.TYPE_ACCESS, ApiV3Token.STATE_ACTIVE, now);
        if (rows.size() != 1 || rows.getFirst().optString("websiteDeviceId").isBlank()) throw ApiV3Requests.forbidden();
        return rows.getFirst().getString("websiteDeviceId");
    }

    public JSONObject list(final JSONObject identity) throws Exception {
        final var tx = tokens.beginTransaction();
        try {
            final String actor = proofs.read(identity).getString("oId");
            final long now = System.currentTimeMillis();
            final String current = current(identity, actor, now);
            final var list = new JSONArray();
            for (final var entry : activeDevices(actor, now).entrySet()) {
                final var device = entry.getValue();
                list.put(new JSONObject().put("id", device.handle()).put("label", device.label)
                        .put("lastActiveAt", device.lastActiveAt).put("current", current.equals(entry.getKey())));
            }
            tx.commit();
            return new JSONObject().put("list", list);
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    public JSONObject revoke(final JSONObject identity, final String handle) throws Exception {
        if (handle == null || !handle.matches("[A-Za-z0-9_-]{43}\\.[A-Za-z0-9_-]{43}")) throw ApiV3Requests.invalid();
        final var tx = tokens.beginTransaction();
        try {
            final String actor = proofs.authorizeWebsiteDeviceInCurrentTransaction(identity).getString("oId");
            final long now = System.currentTimeMillis();
            final String current = current(identity, actor, now);
            for (final var entry : activeDevices(actor, now).entrySet()) {
                final var device = entry.getValue();
                if (!handle.startsWith(device.publicId + ".")) continue;
                if (current.equals(entry.getKey())) throw new ApiV3Exception(409, "current_device", "请使用退出登录退出当前设备");
                if (!handle.equals(device.handle())) throw new ApiV3Exception(409, "device_changed", "设备登录已变化，请刷新后重试");
                for (final String family : device.families) {
                    tokens.revokeFamilyInCurrentTransaction(ApiV3TokenMgmtService.WEBSITE_CLIENT, actor, family, now);
                }
                break;
            }
            tx.commit();
            return new JSONObject().put("revoked", true);
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }
}
