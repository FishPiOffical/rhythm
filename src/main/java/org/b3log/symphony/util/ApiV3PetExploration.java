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

/** 进阶探索、消耗品和纪念收藏目录；所有奖励固定，材料不可兑换站点积分。 */
public final class ApiV3PetExploration {
    private ApiV3PetExploration() { }

    public static final List<Item> ITEMS = List.of(
            new Item("food", "口粮", "日常饱食补给", "food", ApiV3PetRules.FOOD_PRICE, ApiV3PetRules.FOOD_HUNGER, 0, 0, 0),
            new Item("fruit", "鲜果", "补充饱食，也让心情好一点", "food", 6, 20, 0, 8, 0),
            new Item("spring-water", "清泉饮", "清凉饮水与一点精力补充", "drink", 5, 0, 35, 0, 5),
            new Item("reed", "芦苇", "荷塘采集的柔韧材料，可制作芦苇小舟", "material", 6, 0, 0, 0, 0),
            new Item("forest-seed", "森林种子", "森林寻种带回的纪念材料", "material", null, 0, 0, 0, 0),
            new Item("sea-shell", "海贝", "潮汐拾贝带回的纪念材料", "material", null, 0, 0, 0, 0));

    public static final List<Exploration> PLACES = List.of(
            new Exploration("lotus-pond", "荷塘采集", "沿荷塘收集芦苇，做一只纪念小舟", "exploration", "reed"),
            new Exploration("deep-forest", "森林寻种", "走进林间，寻找制作风铃的种子", "vitality", "forest-seed"),
            new Exploration("tidal-flat", "潮汐拾贝", "观察潮汐，在安全的滩涂收集海贝", "knowledge", "sea-shell"));

    public static final List<Recipe> RECIPES = List.of(
            new Recipe("reed-boat", "芦苇小舟", "用芦苇编成一只小舟，收藏荷塘的记忆", "reed", "exploration"),
            new Recipe("forest-chime", "森林风铃", "把森林种子串成风铃，留住林间的声音", "forest-seed", "vitality"),
            new Recipe("shell-frame", "贝壳相框", "用海贝装饰相框，记下第一次潮汐之旅", "sea-shell", "knowledge"));

    public record Item(String id, String name, String description, String category, Integer price,
                       int hunger, int water, int mood, int energy) {
        public JSONObject effects() {
            return new JSONObject().put("hunger", hunger).put("water", water).put("mood", mood).put("energy", energy);
        }
        public JSONObject json() {
            return new JSONObject().put("id", id).put("name", name).put("description", description).put("category", category)
                    .put("price", price == null ? JSONObject.NULL : price).put("hunger", hunger).put("effects", effects());
        }
    }

    public record Exploration(String id, String name, String description, String skillId, String itemId) {
        public JSONObject json() {
            return new JSONObject().put("id", id).put("name", name).put("description", description).put("durationMs", 20 * 60_000L)
                    .put("requirements", requirements(skillId))
                    .put("cost", new JSONObject().put("coins", 5).put("energy", 12).put("hunger", 4).put("water", 6))
                    .put("rewards", new JSONObject().put("coins", 0).put("proficiency", 0).put("experience", 25).put("food", 0).put("mood", 6)
                            .put("skills", new JSONArray().put(new JSONObject().put("id", skillId).put("experience", 8)))
                            .put("items", new JSONArray().put(new JSONObject().put("itemId", itemId).put("quantity", 2))));
        }
    }

    public record Recipe(String id, String name, String description, String itemId, String skillId) {
        public JSONObject json() {
            return new JSONObject().put("id", id).put("name", name).put("description", description)
                    .put("requirements", requirements(skillId)).put("coins", 2).put("collectionId", id)
                    .put("ingredients", new JSONArray().put(new JSONObject().put("itemId", itemId).put("quantity", 2)));
        }
        public JSONObject collection() {
            return new JSONObject().put("id", id).put("name", name).put("description", description);
        }
    }

    private static JSONArray requirements(final String skillId) {
        return new JSONArray().put(new JSONObject().put("skillId", skillId).put("experience", 10));
    }

    public static Item item(final String id) {
        return ITEMS.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiV3PetRules.invalid("这个物品暂未开放"));
    }

    public static boolean knownItem(final String id) {
        return ITEMS.stream().anyMatch(item -> item.id().equals(id));
    }

    public static Recipe recipe(final String id) {
        return RECIPES.stream().filter(recipe -> recipe.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiV3PetRules.invalid("这个配方暂未开放"));
    }

    public static boolean knownCollection(final String id) {
        return RECIPES.stream().anyMatch(recipe -> recipe.id().equals(id));
    }

    public static JSONObject extendCatalog(final JSONObject catalog) {
        final JSONArray items = new JSONArray(), recipes = new JSONArray(), collections = new JSONArray();
        for (Item item : ITEMS) items.put(item.json());
        for (Recipe recipe : RECIPES) {
            recipes.put(recipe.json());
            collections.put(recipe.collection());
        }
        return catalog.put("items", items).put("recipes", recipes).put("collections", collections);
    }
}
