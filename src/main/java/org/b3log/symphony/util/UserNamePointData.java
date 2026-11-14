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

import org.b3log.symphony.model.Pointtransfer;
import org.json.JSONObject;

/** New rename charges keep the two names in separate bounded ledger columns. */
public final class UserNamePointData {
    public static final String SCENE = "settings.user-name";
    private UserNamePointData() { }

    public static String description(JSONObject record, String template) {
        if (SCENE.equals(record.optString(Pointtransfer.SOURCE_SCENE))) {
            return template.replace("{oldName}", Escapes.escapeHTML(record.optString(Pointtransfer.MEMO)))
                    .replace("{newName}", Escapes.escapeHTML(record.optString(Pointtransfer.DATA_ID)));
        }
        // Preserve the interpretation of historical rows written by the legacy endpoint.
        final String[] names = record.optString(Pointtransfer.DATA_ID).split("-");
        return template.replace("{oldName}", names[0]).replace("{newName}", names[1]);
    }
}
