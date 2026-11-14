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

import org.b3log.symphony.model.Breezemoon;
import org.json.JSONObject;

/**
 * 本人编辑时保留作者、创建时间、城市和审核状态。
 */
public final class ApiV3BreezemoonEdit {
    private ApiV3BreezemoonEdit() {
    }

    public static JSONObject apply(final JSONObject old, final String content, final String ip, final String ua,
                                   final long now, final int maxUa) {
        final String agent = null == ua ? "" : ua;
        return new JSONObject(old.toString()).put(Breezemoon.BREEZEMOON_CONTENT, content)
                .put(Breezemoon.BREEZEMOON_IP, ip).put(Breezemoon.BREEZEMOON_UA, agent.substring(0, Math.min(agent.length(), maxUa)))
                .put(Breezemoon.BREEZEMOON_UPDATED, now);
    }
}
