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

import org.json.JSONObject;

/** 每个位置独立计时。跨日按北京时间切分，离线多天也只计算首尾两天与完整天数。 */
public final class PartnerIncome {
    private static final long HOUR = 3_600_000L, DAY = 24 * HOUR, LIMIT = 8 * HOUR;
    private PartnerIncome() { }

    public static JSONObject meter(long now) {
        return new JSONObject().put("at", now).put("used", 0L).put("fraction", 0L)
                .put("today", 0L).put("peer", "").put("rate", 0);
    }

    public static int settle(JSONObject meter, long now) {
        long start = meter.getLong("at");
        if (now < start) throw new ApiV3Exception(409, "clock_changed", "服务器时间正在校准");
        long startDay = (start + 8 * HOUR) / DAY, endDay = (now + 8 * HOUR) / DAY;
        long midnight = endDay * DAY - 8 * HOUR;
        long used = meter.getLong("used"), fraction = meter.getLong("fraction");
        long today = meter.getLong("today"), prior = 0;
        int rate = meter.getInt("rate");
        if (endDay > startDay) {
            long firstEnd = (startDay + 1) * DAY - 8 * HOUR;
            long past = Math.min(LIMIT - used, firstEnd - start) + (endDay - startDay - 1) * LIMIT;
            long scaled = past * rate + fraction;
            prior = scaled / HOUR;
            fraction = scaled % HOUR;
            today = used = 0;
            start = midnight;
        }
        long active = rate == 0 ? 0 : Math.min(now - start, LIMIT - used);
        long scaled = active * rate + fraction;
        meter.put("used", used + active).put("at", now).put("fraction", scaled % HOUR)
                .put("today", today + scaled / HOUR);
        return Math.toIntExact(prior + scaled / HOUR);
    }
}
