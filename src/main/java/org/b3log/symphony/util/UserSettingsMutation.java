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
package org.b3log.symphony.util;

import org.b3log.latke.repository.RepositoryException;
import org.json.JSONObject;
import org.b3log.symphony.model.UserExt;

/** Settings transaction body shared by legacy wrappers and v3 idempotent writes. */
public final class UserSettingsMutation {
    private UserSettingsMutation() { }
    public interface Store {
        boolean hasTransaction();
        JSONObject lockOwner(String actor) throws RepositoryException;
        String canonicalSkin(String skin, String device);
        String escapeText(String text);
        void validatePersonality(String actor, boolean quota);
        void validateProfile(String actor, JSONObject merged, boolean quota);
        void update(String actor, JSONObject changes) throws RepositoryException;
    }
    public static JSONObject apply(final Store store, final String actor, final String group, final JSONObject requested,
                                   final boolean quota, final long now) throws RepositoryException {
        if (!store.hasTransaction()) throw new IllegalStateException("settings transaction required");
        if (!ApiV3Settings.fields(group).values().containsAll(requested.keySet()) || requested.isEmpty()) throw new IllegalArgumentException("unknown settings field");
        final JSONObject current = store.lockOwner(actor);
        if (null == current) throw new ApiV3Exception(404, "not_found", "资源不存在");
        final JSONObject changes = new JSONObject(requested.toString());
        for (final String key : changes.keySet()) {
            if ("userSkin".equals(key) || "userMobileSkin".equals(key)) {
                final String skin = store.canonicalSkin(changes.getString(key), "userSkin".equals(key) ? "pc" : "mobile");
                if (null == skin || skin.length() > 32) throw ApiV3WriteInputs.invalid();
                changes.put(key, skin);
            }
        }
        if ("personality".equals(group)) {
            if (current.optInt("userStatus", -1) != UserExt.USER_STATUS_C_VALID) throw new ApiV3Exception(404, "not_found", "资源不存在");
            changes.put("mbti", ApiV3Personality.normalize(changes.getString("mbti")));
            store.validatePersonality(actor, quota);
            changes.put("userUpdateTime", now);
        }
        if ("avatar".equals(group)) {
            if (current.optInt("userStatus", -1) != UserExt.USER_STATUS_C_VALID) throw new ApiV3Exception(404, "not_found", "资源不存在");
            changes.put("userAvatarURL", ApiV3Settings.avatarURL(changes.getString("userAvatarURL")));
            changes.put("userAvatarType", UserExt.USER_AVATAR_TYPE_C_UPLOAD);
            changes.put("userUpdateTime", now);
        }
        if ("profile".equals(group)) {
            for (final String field : new String[]{"userNickname", "userIntro"}) if (changes.has(field)) {
                final String encoded = store.escapeText(changes.getString(field));
                if (encoded.length() > ("userNickname".equals(field) ? 20 : 255)) throw ApiV3WriteInputs.invalid();
                changes.put(field, encoded);
            }
            final JSONObject merged = new JSONObject(current.toString());
            for (final String field : changes.keySet()) merged.put(field, changes.get(field));
            store.validateProfile(actor, merged, quota);
            changes.put("userUpdateTime", now);
        }
        store.update(actor, changes);
        for (final String field : changes.keySet()) current.put(field, changes.get(field));
        return ApiV3Settings.dto(group, current);
    }
}
