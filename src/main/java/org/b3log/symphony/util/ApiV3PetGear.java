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
