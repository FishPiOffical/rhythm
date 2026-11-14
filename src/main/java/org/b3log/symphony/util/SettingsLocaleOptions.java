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

import org.b3log.latke.util.TimeZones;
import org.json.JSONArray;
import org.json.JSONObject;

/** The same language/timezone catalog used by the classic settings page. */
public final class SettingsLocaleOptions {
    private SettingsLocaleOptions() { }
    public static JSONObject dto() {
        final JSONArray timezones = new JSONArray();
        for (final TimeZones.TimeZoneWithDisplayNames zone : TimeZones.getInstance().getTimeZones()) {
            timezones.put(new JSONObject().put("id", zone.getTimeZone().getID()).put("name", zone.getDisplayName()));
        }
        return new JSONObject().put("languages", Languages.getAvailableLanguages()).put("timezones", timezones);
    }
}
