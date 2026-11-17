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

/** 进阶目录和叠加预算，所有增益使用百分之一 HP 单位。 */
public final class ApiV3PetGear {
    public enum Product {
        ARTIFACT("神器进阶", 12, 2, 200),
        SLOT("解锁第二槽", 0, 0, 100),
        SOUL_VITAL("生息魂珠", 4, 0, 50, 100, 150),
        SOUL_EDGE("锐意魂珠", 0, 1, 50, 100, 150),
        RUNE_GUARD("护身符石", 3, 0, 50, 100, 150),
        RUNE_SPARK("火花符石", 0, 1, 50, 100, 150);

        public final String label;
        public final int hp;
        public final int attack;
        private final int[] costs;
        Product(final String label, final int hp, final int attack, final int... costs) {
            this.label = label;
            this.hp = hp;
            this.attack = attack;
            this.costs = costs;
        }
        public boolean weaponBound() { return this == ARTIFACT || this == SLOT; }
        public int maxLevel() { return costs.length; }
        public int cost(final int level) { return costs[level]; }
        public JSONObject json() {
            return new JSONObject().put("id", name()).put("name", label).put("hp", hp).put("attack", attack)
                    .put("costs", new org.json.JSONArray(costs));
        }
    }

    private ApiV3PetGear() {}
}
