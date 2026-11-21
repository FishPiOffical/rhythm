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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** 停车费率在出车时冻结，结算只看服务端时钟。 */
public final class ParkingRules {
    public static final long HOUR = 3_600_000L;
    public static final long MAX_DURATION = 8 * HOUR;

    public record Car(String id, String name, int price, int rate, boolean starter) { }

    public static final List<Car> CARS = List.of(
            new Car("punch", "派小鱼·打卡号", 600, 24, true),
            new Car("creek", "摸摸獭·溪岸皮卡", 600, 24, true),
            new Car("lotus", "荷小豚·荷塘慢车", 600, 24, true),
            new Car("waffle", "鱼饼喵·午后餐车", 1200, 32, false),
            new Car("tide", "青潮", 1800, 42, false),
            new Car("nightbus", "夜班末车", 2400, 50, false),
            new Car("pine", "松岭", 3000, 60, false),
            new Car("moon", "清风明月·望月跑车", 4200, 75, false),
            new Car("fin", "橙鳍", 6000, 90, false),
            new Car("scarlet", "绯红八六", 6000, 90, false));

    private ParkingRules() { }

    public static Car car(final String id) {
        return CARS.stream().filter(c -> c.id.equals(id)).findFirst().orElseThrow(ApiV3WriteInputs::invalid);
    }

    public static JSONArray catalog() {
        final JSONArray result = new JSONArray();
        for (Car c : CARS) result.put(new JSONObject().put("id", c.id).put("name", c.name)
                .put("price", c.price).put("rate", c.rate).put("starter", c.starter));
        return result;
    }

    public static int payout(final int rate, final long start, final long end) {
        if (end < start) throw new ApiV3Exception(409, "clock_changed", "服务器时间正在校准，请稍后再试");
        return Math.toIntExact(Math.min(MAX_DURATION, end - start) * rate / HOUR);
    }
}
