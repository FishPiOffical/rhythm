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
import org.b3log.symphony.util.SettingsLocaleOptions;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.util.ApiV3CommunityDto;
import org.b3log.symphony.util.ApiV3AvatarUrl;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.b3log.symphony.model.SystemSettings;
import org.b3log.symphony.repository.SystemSettingsRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.latke.Keys;
import org.b3log.latke.repository.RepositoryException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Service
public class ApiV3SettingsMgmtService {
    @Inject
    private UserSettingsMgmtService settings;
    @Inject
    private ApiV3IdempotencyMgmtService idempotency;
    @Inject
    private SkinQueryService skins;
    @Inject
    private SystemSettingsRepository systemSettings;
    @Inject
    private UserRepository users;

    public JSONObject readCardBackground(final String actor) throws RepositoryException {
        return new JSONObject().put("cardBg", cardBackground(systemSettings, actor));
    }

    static String cardBackground(final SystemSettingsRepository repository, final String actor) throws RepositoryException {
        final var rows = repository.select("SELECT settings FROM `" + repository.getName()
                + "` WHERE userId=? LIMIT 2", actor);
        if (rows.isEmpty()) return "";
        if (rows.size() != 1) throw new ApiV3Exception(503, "temporarily_unavailable", "个性卡片配置暂不可用");
        final Object background = storedSettings(rows.getFirst()).opt("cardBg");
        return background instanceof String url ? ApiV3AvatarUrl.stored(url) : "";
    }

    private static JSONObject storedSettings(final JSONObject row) {
        try {
            return new JSONObject(row.getString(SystemSettings.SETTINGS));
        } catch (final JSONException e) {
            throw new ApiV3Exception(503, "temporarily_unavailable", "个性卡片配置暂不可用");
        }
    }

    public JSONObject updateCardBackground(final String actor, final JSONObject input, final String key) throws Exception {
        if (!input.has("cardBg")) throw ApiV3WriteInputs.invalid();
        final String value = ApiV3WriteInputs.string(input, "cardBg", 2048, false);
        if (!value.isEmpty()) {
            if (!value.equals(ApiV3AvatarUrl.stored(value)) || !value.regionMatches(true, 0, "https://", 0, 8))
                throw ApiV3WriteInputs.invalid();
            final java.net.URI uri = java.net.URI.create(value);
            if (uri.getHost() == null || uri.getPort() > 65535 || uri.getRawAuthority().endsWith(":")
                    || value.chars().anyMatch(ch -> ch == '\'' || ch == '"' || ch == '<' || ch == '>'))
                throw ApiV3WriteInputs.invalid();
        }
        final JSONObject changes = new JSONObject().put("cardBg", value);
        return idempotency.execute(actor, "settings.card-background", key, changes, () -> {
            if (users.getForUpdate(actor) == null) throw ApiV3Requests.notFound();
            // 与经典设置共用 JSON，仅改背景字段，保留其他设置。
            final var rows = systemSettings.select("SELECT oId, settings FROM `" + systemSettings.getName()
                    + "` WHERE userId=? FOR UPDATE", actor);
            if (rows.size() > 1) throw new RepositoryException("用户系统设置存在重复记录");
            final JSONObject existing = rows.isEmpty() ? null : rows.getFirst();
            final JSONObject data = existing == null ? new JSONObject() : storedSettings(existing);
            data.put("cardBg", value);
            final String json = data.toString();
            if (json.length() > 4096) throw ApiV3WriteInputs.invalid();
            final JSONObject row = new JSONObject().put(SystemSettings.SETTINGS, json);
            if (existing == null) systemSettings.add(row.put("userId", actor));
            else systemSettings.update(existing.getString(Keys.OBJECT_ID), row, SystemSettings.SETTINGS);
            return changes;
        });
    }

    public JSONObject read(final String actor, final String group) throws Exception {
        return settings.read(actor, group);
    }

    public JSONObject update(final String actor, final String group, final JSONObject changes, final String key) throws Exception {
        try {
            return idempotency.execute(actor, "settings." + group, key, changes,
                    () -> settings.updateInCurrentTransaction(actor, group, changes, true));
        } finally {
            settings.refresh(actor);
        }
    }

    public JSONObject localeOptions() {
        return SettingsLocaleOptions.dto();
    }

    public JSONObject skins() {
        final JSONArray list = new JSONArray();
        for (final String device : new String[]{SkinQueryService.DEVICE_PC, SkinQueryService.DEVICE_MOBILE}) {
            for (final JSONObject skin : skins.getSkins(device)) {
                if (skin.optString("dirName").length() > 32) continue;
                list.put(new JSONObject().put("id", skin.optString("dirName"))
                        .put("device", device).put("name", skin.optString("name")).put("description", skin.optString("memo"))
                        .put("previewURL", ApiV3CommunityDto.safeUrl(skin.optString("previewUrl"))).put("order", skin.optInt("order")));
            }
        }
        return new JSONObject().put("list", list);
    }
}
