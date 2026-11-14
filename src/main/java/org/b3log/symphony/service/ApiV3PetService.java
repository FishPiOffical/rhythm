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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.PetAccountRepository;
import org.b3log.symphony.repository.PetActivityRepository;
import org.b3log.symphony.repository.PetSkillRepository;
import org.b3log.symphony.repository.PetJobProgressRepository;
import org.b3log.symphony.repository.PetIncomeAccountRepository;
import org.b3log.symphony.repository.PetActivityResultRepository;
import org.b3log.symphony.repository.PetInventoryRepository;
import org.b3log.symphony.repository.PetCollectionRepository;
import org.b3log.symphony.repository.PetJournalRepository;
import org.b3log.symphony.repository.PetStoryProgressRepository;
import org.b3log.symphony.repository.PetArchiveRepository;
import org.b3log.symphony.repository.PetLedgerRepository;
import org.b3log.symphony.repository.UserPetRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3PetRules;
import org.b3log.symphony.util.ApiV3PetActivities;
import org.b3log.symphony.util.ApiV3PetWork;
import org.b3log.symphony.util.ApiV3PetExploration;
import org.b3log.symphony.util.ApiV3PetJournal;
import org.b3log.symphony.util.ApiV3PetStories;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Set;

/** 宠物钱包、背包、照顾与回执同事务；站点积分和经典客户端不参与本玩法。 */
@Service
public class ApiV3PetService {
    @Inject private PetAccountRepository accounts;
    @Inject private UserPetRepository pets;
    @Inject private PetInventoryRepository inventory;
    @Inject private PetCollectionRepository collections;
    @Inject private PetJournalRepository journal;
    @Inject private PetStoryProgressRepository storyProgress;
    @Inject private PetArchiveRepository archives;
    @Inject private PetLedgerRepository ledger;
    @Inject private PetActivityRepository activities;
    @Inject private PetSkillRepository skills;
    @Inject private PetJobProgressRepository jobs;
    @Inject private PetIncomeAccountRepository income;
    @Inject private PetActivityResultRepository results;
    @Inject private ApiV3RequestRepository requests;
    @Inject private ApiV3SecurityProofService proofs;
    @Inject private ApiV3PetRiskService risk;

    private static final String[] PET_FIELDS = {"name", "hunger", "water", "mood", "energy", "experience", "friendship",
            "lastCalculatedAt", "activityKind", "activityStartedAt", "activityEndsAt", "petAvailableAt", "playAvailableAt", "updatedAt"};
    private static final String[] ACCOUNT_FIELDS = {"coins", "supplyDate", "growthDate", "growthCount", "updatedAt"};

    protected long now() {
        return System.currentTimeMillis();
    }

    protected void checkName(final String name) {
        if (CensorFactory.getTextCensor().censor(name).isBlocked())
            throw new ApiV3Exception(422, "content_rejected", "宠物名字中含有不适宜的内容");
    }

    public JSONObject catalog(final JSONObject identity) {
        return readTransaction(identity, () -> ApiV3PetActivities.extendCatalog(ApiV3PetRules.catalog()));
    }

    public JSONObject read(final JSONObject identity) {
        return readTransaction(identity, () -> {
            final long current = now();
            final String today = ApiV3PetRules.today(current);
            final JSONObject account = account(actor(identity));
            final JSONObject pet = pet(actor(identity));
            if ((account == null) != (pet == null)) throw unavailable();
            final JSONObject result = new JSONObject().put("serverNow", current).put("today", today)
                    .put("catalogVersion", ApiV3PetRules.VERSION)
                    .put("collections", collectionState(actor(identity)))
                    .put("stories", storyState(actor(identity)))
                    .put("income", incomeState(incomeRow(actor(identity)), today))
                    .put("wallet", new JSONObject().put("coins", account == null ? 0 : account.getInt("coins")))
                    .put("dailySupply", new JSONObject().put("claimed", account != null && today.compareTo(account.getString("supplyDate")) <= 0)
                            .put("coins", ApiV3PetRules.DAILY_COINS).put("food", ApiV3PetRules.DAILY_FOOD));
            if (pet == null) return result.put("pet", JSONObject.NULL).put("inventory", new JSONArray());
            final JSONObject projected = ApiV3PetRules.publicPet(ApiV3PetRules.settle(pet, current), account, today)
                    .put("skills", skillState(actor(identity), pet.getString("oId")))
                    .put("jobs", jobState(actor(identity), pet.getString("oId")));
            final JSONObject active = activeActivity(actor(identity), pet.getString("oId"));
            if (active != null) {
                if (!projected.isNull("activity")) throw unavailable();
                projected.put("activity", ApiV3PetActivities.publicActivity(active, current));
            }
            return result.put("pet", projected)
                    .put("inventory", inventoryState(actor(identity)));
        });
    }

    public JSONObject adopt(final JSONObject identity, final JSONObject body, final String key) {
        exact(body, Set.of("speciesId", "name"));
        final String species = string(body, "speciesId");
        if (!ApiV3PetRules.SPECIES.contains(species)) throw ApiV3WriteInputs.invalid();
        final String name = ApiV3PetRules.name(body.opt("name"));
        final JSONObject input = new JSONObject().put("speciesId", species).put("name", name);
        return mutate(identity, "adoption", key, input, () -> {
            final String actor = actor(identity);
            if (account(actor) != null || pet(actor) != null)
                throw ApiV3PetRules.conflict("pet_exists", "你已经有一只宠物了");
            checkName(name);
            final long current = now();
            final String id = Ids.genTimeMillisId();
            accounts.add(new JSONObject().put("oId", actor).put("coins", ApiV3PetRules.INITIAL_COINS)
                    .put("supplyDate", "").put("growthDate", ApiV3PetRules.today(current)).put("growthCount", 0)
                    .put("createdAt", current).put("updatedAt", current));
            inventory.add(new JSONObject().put("oId", actor + ":food").put("userId", actor).put("itemId", "food")
                    .put("quantity", ApiV3PetRules.INITIAL_FOOD).put("updatedAt", current));
            pets.add(new JSONObject().put("oId", id).put("userId", actor).put("speciesId", species).put("name", name)
                    .put("hunger", 100.0).put("water", 100.0).put("mood", 100.0).put("energy", 100.0)
                    .put("experience", 0).put("friendship", 0).put("lastCalculatedAt", current)
                    .put("activityKind", "").put("activityStartedAt", 0L).put("activityEndsAt", 0L)
                    .put("petAvailableAt", 0L).put("playAvailableAt", 0L).put("adoptedAt", current).put("updatedAt", current));
            record(actor, id, "adoption", ApiV3PetRules.INITIAL_COINS, ApiV3PetRules.INITIAL_FOOD, current);
            return receipt("adoption", id, current);
        });
    }

    public JSONObject readopt(final JSONObject identity, final JSONObject body, final String key) {
        exact(body, Set.of("petId", "speciesId", "name", "catalogVersion", "resetAccepted"));
        final String previousId = petId(body), species = string(body, "speciesId");
        if (!ApiV3PetRules.SPECIES.contains(species) || !Boolean.TRUE.equals(body.opt("resetAccepted")))
            throw ApiV3WriteInputs.invalid();
        final String name = ApiV3PetRules.name(body.opt("name"));
        final int version = ApiV3WriteInputs.integer(body, "catalogVersion", -1, 1, Integer.MAX_VALUE);
        final JSONObject input = new JSONObject().put("petId", previousId).put("speciesId", species).put("name", name)
                .put("catalogVersion", version).put("resetAccepted", true);
        return mutate(identity, "readoption", key, input, () -> {
            final String actor = actor(identity);
            final JSONObject account = requiredAccount(actor);
            final long current = now();
            final JSONObject previous = ApiV3PetRules.settle(owned(actor, previousId), current);
            requireActivityFree(actor, previousId);
            if ("rest".equals(previous.getString("activityKind")))
                throw ApiV3PetRules.conflict("pet_resting", "先让宠物醒来，再重新领养吧");
            if (current < previous.getLong("lastCalculatedAt"))
                throw ApiV3PetRules.conflict("clock_changed", "时间正在校准，请稍后再领养");
            if (version != ApiV3PetRules.VERSION)
                throw ApiV3PetRules.conflict("catalog_changed", "领养费用已更新，请重新查看");
            if (account.getInt("coins") < ApiV3PetRules.READOPTION_COINS)
                throw ApiV3PetRules.conflict("not_enough_coins", "宠物币不足");
            checkName(name);
            final String id = Ids.genTimeMillisId();
            // 原主体先归档再替换，新旧宠物使用不同 ID；历史技能、工作、活动和日志不删除。
            archives.add(new JSONObject().put("oId", previousId).put("userId", actor).put("retiredAt", current)
                    .put("snapshotJson", previous.toString()));
            pets.remove(previousId);
            pets.add(new JSONObject().put("oId", id).put("userId", actor).put("speciesId", species).put("name", name)
                    .put("hunger", 100.0).put("water", 100.0).put("mood", 100.0).put("energy", 100.0)
                    .put("experience", 0).put("friendship", 0).put("lastCalculatedAt", current)
                    .put("activityKind", "").put("activityStartedAt", 0L).put("activityEndsAt", 0L)
                    .put("petAvailableAt", previous.getLong("petAvailableAt")).put("playAvailableAt", previous.getLong("playAvailableAt"))
                    .put("adoptedAt", current).put("updatedAt", current));
            // 只扣领养费用，不重置补给、成长日限、工作预算或首次奖励，也不赠新手物品。
            account.put("coins", account.getInt("coins") - ApiV3PetRules.READOPTION_COINS).put("updatedAt", current);
            accounts.update(actor, account, ACCOUNT_FIELDS);
            final String speciesName = switch (species) {
                case "momota" -> "摸摸獭";
                case "hexiaotun" -> "荷小豚";
                case "yubingmiao" -> "鱼饼喵";
                case "paixiaoyu" -> "派小鱼";
                default -> throw unavailable();
            };
            appendJournal(actor, id, "readoption", "readoption", "重新领养 · " + speciesName,
                    new JSONArray(), -ApiV3PetRules.READOPTION_COINS, ApiV3PetJournal.zeroEffects(), null, current);
            record(actor, id, "readoption", -ApiV3PetRules.READOPTION_COINS, 0, current);
            return receipt("readoption", id, current).put("previousPetId", previousId);
        });
    }

    public JSONObject rename(final JSONObject identity, final JSONObject body, final String key) {
        exact(body, Set.of("petId", "name"));
        final String id = petId(body), name = ApiV3PetRules.name(body.opt("name"));
        final JSONObject input = new JSONObject().put("petId", id).put("name", name);
        return mutate(identity, "name", key, input, () -> {
            final JSONObject pet = owned(actor(identity), id);
            checkName(name);
            pet.put("name", name).put("updatedAt", now());
            pets.update(id, pet, "name", "updatedAt");
            return receipt("name", id, pet.getLong("updatedAt"));
        });
    }

    public JSONObject action(final JSONObject identity, final JSONObject body, final String key) {
        if (body == null || !Set.of("petId", "action", "itemId").containsAll(body.keySet())) throw ApiV3WriteInputs.invalid();
        final String id = petId(body), action = string(body, "action");
        if (!ApiV3PetRules.ACTIONS.contains(action)) throw ApiV3WriteInputs.invalid();
        final String itemId = body.has("itemId") ? string(body, "itemId") : null;
        final ApiV3PetExploration.Item item = itemId == null ? null : ApiV3PetExploration.item(itemId);
        if ("feed".equals(action)) {
            if (item == null || !"food".equals(item.category())) throw ApiV3WriteInputs.invalid();
        } else if ("water".equals(action)) {
            if (item != null && !"drink".equals(item.category())) throw ApiV3WriteInputs.invalid();
        } else if (item != null) throw ApiV3WriteInputs.invalid();
        final JSONObject input = new JSONObject().put("petId", id).put("action", action);
        if (itemId != null) input.put("itemId", itemId);
        return mutate(identity, "action", key, input, () -> {
            final String actor = actor(identity);
            final JSONObject account = requiredAccount(actor);
            final long current = now();
            final JSONObject pet = ApiV3PetRules.settle(owned(actor, id), current);
            requireActivityFree(actor, id);
            if ("rest".equals(pet.getString("activityKind")) && !"wake".equals(action))
                throw ApiV3PetRules.conflict("pet_resting", "宠物正在休息，醒来后再照顾它吧");
            final JSONObject before = new JSONObject(pet.toString());
            final JSONArray itemChanges = new JSONArray();
            switch (action) {
                case "feed" -> {
                    if ("food".equals(itemId) ? pet.getDouble("hunger") > 99 + 1e-9 : !itemHasEffect(item, pet))
                        throw ApiV3PetRules.conflict("already_full", "宠物现在不需要这份食物");
                    consumeItem(actor, item, pet, current);
                    itemChanges.put(itemChange(itemId, -1));
                    record(actor, id, "feed", 0, "food".equals(itemId) ? -1 : 0, current);
                }
                case "water" -> {
                    if (item == null ? pet.getDouble("water") > 99 + 1e-9 : !itemHasEffect(item, pet))
                        throw ApiV3PetRules.conflict("already_full", "宠物现在不需要饮水");
                    if (item == null) {
                        // 免费饮水继续沿用原来的增量；指定饮料才消费背包。
                        pet.put("water", Math.min(100, pet.getDouble("water") + ApiV3PetRules.WATER_GAIN));
                    } else {
                        consumeItem(actor, item, pet, current);
                        itemChanges.put(itemChange(itemId, -1));
                    }
                }
                case "pet" -> {
                    cooldown(pet, "petAvailableAt", current);
                    pet.put("mood", Math.min(100, pet.getDouble("mood") + ApiV3PetRules.PET_MOOD))
                            .put("petAvailableAt", current + ApiV3PetRules.CARE_COOLDOWN);
                }
                case "play" -> {
                    cooldown(pet, "playAvailableAt", current);
                    if (pet.getDouble("energy") < ApiV3PetRules.PLAY_ENERGY)
                        throw ApiV3PetRules.conflict("not_enough_energy", "精力不足，先让宠物休息一下");
                    pet.put("energy", pet.getDouble("energy") - ApiV3PetRules.PLAY_ENERGY)
                            .put("mood", Math.min(100, pet.getDouble("mood") + ApiV3PetRules.PLAY_MOOD))
                            .put("playAvailableAt", current + ApiV3PetRules.CARE_COOLDOWN);
                }
                case "rest" -> pet.put("activityKind", "rest").put("activityStartedAt", current)
                        .put("activityEndsAt", current + ApiV3PetRules.REST_DURATION);
                case "wake" -> {
                    // 休息已自然结束时仍允许醒来，避免客户端恰好跨到结束时间时误报失败。
                    ApiV3PetRules.clearActivity(pet);
                }
                default -> throw ApiV3WriteInputs.invalid();
            }
            if (Set.of("feed", "water", "pet", "play").contains(action)) grow(pet, account, current);
            pet.put("updatedAt", current);
            account.put("updatedAt", current);
            pets.update(id, pet, PET_FIELDS);
            accounts.update(actor, account, ACCOUNT_FIELDS);
            // 与状态/消耗同一事务写日志；净变化从离线结算之后算起，不把离线衰减算成此次操作效果。
            appendJournal(actor, id, "care", action, ApiV3PetJournal.actionName(action)
                    + (item == null ? "" : " · " + item.name()), itemChanges, 0,
                    ApiV3PetJournal.effects(before, pet), null, current);
            advanceStories(actor, "care", action, "", "", current);
            return receipt(action, id, current);
        });
    }

    public JSONObject supplies(final JSONObject identity, final JSONObject body, final String key) {
        exact(body, Set.of("expectedDate"));
        final String date = string(body, "expectedDate");
        try {
            if (!LocalDate.parse(date).toString().equals(date)) throw ApiV3WriteInputs.invalid();
        } catch (java.time.DateTimeException error) {
            throw ApiV3WriteInputs.invalid();
        }
        return mutate(identity, "supplies", key, new JSONObject().put("expectedDate", date), () -> {
            final String actor = actor(identity);
            final JSONObject account = requiredAccount(actor), pet = owned(actor, null);
            final long current = now();
            if (!date.equals(ApiV3PetRules.today(current)))
                throw ApiV3PetRules.conflict("date_changed", "日期已更新，请刷新后领取今天的补给");
            if (date.compareTo(account.getString("supplyDate")) <= 0)
                throw ApiV3PetRules.conflict("supply_claimed", "今天的补给已经领取了");
            account.put("coins", Math.addExact(account.getInt("coins"), ApiV3PetRules.DAILY_COINS))
                    .put("supplyDate", date).put("updatedAt", current);
            changeFood(food(actor), ApiV3PetRules.DAILY_FOOD, current);
            accounts.update(actor, account, ACCOUNT_FIELDS);
            record(actor, pet.getString("oId"), "supplies", ApiV3PetRules.DAILY_COINS, ApiV3PetRules.DAILY_FOOD, current);
            return receipt("supplies", pet.getString("oId"), current);
        });
    }

    public JSONObject purchase(final JSONObject identity, final JSONObject body, final String key) {
        exact(body, Set.of("petId", "itemId", "quantity", "catalogVersion"));
        final String id = petId(body);
        final String itemId = string(body, "itemId");
        final ApiV3PetExploration.Item item = ApiV3PetExploration.item(itemId);
        if (item.price() == null) throw ApiV3PetRules.invalid("这个材料只能通过探索获得");
        final int quantity = ApiV3WriteInputs.integer(body, "quantity", -1, 1, 20);
        final int version = ApiV3WriteInputs.integer(body, "catalogVersion", -1, 1, Integer.MAX_VALUE);
        final JSONObject input = new JSONObject().put("petId", id).put("itemId", itemId)
                .put("quantity", quantity).put("catalogVersion", version);
        return mutate(identity, "purchase", key, input, () -> {
            final String actor = actor(identity);
            final JSONObject account = requiredAccount(actor);
            owned(actor, id);
            if (version != ApiV3PetRules.VERSION) throw ApiV3PetRules.conflict("catalog_changed", "商品价格已更新，请重新查看");
            final int price = Math.multiplyExact(quantity, item.price());
            if (account.getInt("coins") < price) throw ApiV3PetRules.conflict("not_enough_coins", "宠物币不足");
            final long current = now();
            account.put("coins", account.getInt("coins") - price).put("updatedAt", current);
            changeItem(actor, itemId, quantity, current);
            accounts.update(actor, account, ACCOUNT_FIELDS);
            record(actor, id, "purchase", -price, "food".equals(itemId) ? quantity : 0, current);
            appendJournal(actor, id, "purchase", "purchase", "购买 · " + item.name(),
                    new JSONArray().put(itemChange(itemId, quantity)), -price, ApiV3PetJournal.zeroEffects(), null, current);
            return receipt("purchase", id, current);
        });
    }

    public JSONObject craft(final JSONObject identity, final JSONObject body, final String key) {
        exact(body, Set.of("petId", "recipeId", "catalogVersion"));
        final String id = petId(body), recipeId = string(body, "recipeId");
        final int version = ApiV3WriteInputs.integer(body, "catalogVersion", -1, 1, Integer.MAX_VALUE);
        final JSONObject input = new JSONObject().put("petId", id).put("recipeId", recipeId).put("catalogVersion", version);
        return mutate(identity, "craft", key, input, () -> {
            final String actor = actor(identity);
            final JSONObject account = requiredAccount(actor);
            final long current = now();
            final JSONObject pet = ApiV3PetRules.settle(owned(actor, id), current);
            requireActivityFree(actor, id);
            if ("rest".equals(pet.getString("activityKind")))
                throw ApiV3PetRules.conflict("pet_resting", "宠物正在休息，醒来后再制作吧");
            if (version != ApiV3PetRules.VERSION)
                throw ApiV3PetRules.conflict("catalog_changed", "配方已更新，请重新查看");
            final JSONObject recipe = ApiV3PetExploration.recipe(recipeId).json();
            final String collectionId = recipe.getString("collectionId");
            final JSONArray owned = collectionState(actor);
            for (int index = 0; index < owned.length(); index++) {
                if (collectionId.equals(owned.getJSONObject(index).getString("id")))
                    throw ApiV3PetRules.conflict("collection_owned", "这件纪念收藏已经制作过了");
            }
            requireSkills(actor, id, recipe.getJSONArray("requirements"));
            final int cost = recipe.getInt("coins");
            if (account.getInt("coins") < cost) throw ApiV3PetRules.conflict("not_enough_coins", "宠物币不足");
            final JSONArray ingredients = recipe.getJSONArray("ingredients"), changes = new JSONArray();
            // 用户锁串行化所有背包写入，逐项校验与扣除仍在同一事务，失败时不会留下部分消耗。
            for (int index = 0; index < ingredients.length(); index++) {
                final JSONObject ingredient = ingredients.getJSONObject(index);
                final String itemId = ingredient.getString("itemId");
                final int quantity = ingredient.getInt("quantity");
                changeItem(actor, itemId, -quantity, current);
                changes.put(itemChange(itemId, -quantity));
            }
            account.put("coins", account.getInt("coins") - cost).put("updatedAt", current);
            accounts.update(actor, account, ACCOUNT_FIELDS);
            collections.add(new JSONObject().put("oId", actor + ":" + collectionId).put("userId", actor)
                    .put("collectionId", collectionId).put("unlockedAt", current));
            appendJournal(actor, id, "craft", "craft", "制作 · " + recipe.getString("name"), changes, -cost,
                    ApiV3PetJournal.zeroEffects(), collectionId, current);
            record(actor, id, "craft", -cost, 0, current);
            return receipt("craft", id, current);
        });
    }

    public JSONObject claimStory(final JSONObject identity, final JSONObject body, final String key) {
        exact(body, Set.of("petId", "storyId", "catalogVersion"));
        final String id = petId(body), storyId = string(body, "storyId");
        if (!ApiV3PetStories.known(storyId)) throw ApiV3WriteInputs.invalid();
        final int version = ApiV3WriteInputs.integer(body, "catalogVersion", -1, 1, Integer.MAX_VALUE);
        final JSONObject input = new JSONObject().put("petId", id).put("storyId", storyId).put("catalogVersion", version);
        return mutate(identity, "story-claim", key, input, () -> {
            final String actor = actor(identity);
            requiredAccount(actor);
            final long current = now();
            final JSONObject pet = ApiV3PetRules.settle(owned(actor, id), current);
            requireActivityFree(actor, id);
            if ("rest".equals(pet.getString("activityKind")))
                throw ApiV3PetRules.conflict("pet_resting", "先让宠物醒来，再领取事件奖励吧");
            if (version != ApiV3PetRules.VERSION)
                throw ApiV3PetRules.conflict("catalog_changed", "事件目录已更新，请重新查看");
            final JSONObject row = storyRows(actor).stream().filter(item -> storyId.equals(item.getString("storyId")))
                    .findFirst().orElse(null);
            if (row == null || row.getLong("completedAt") == 0)
                throw ApiV3PetRules.conflict("story_incomplete", "这段事件还没有完成");
            if (row.getLong("claimedAt") > 0)
                throw ApiV3PetRules.conflict("story_claimed", "这段事件的奖励已经领取了");
            if (current < row.getLong("updatedAt") || current < pet.getLong("lastCalculatedAt"))
                throw ApiV3PetRules.conflict("clock_changed", "时间正在校准，请稍后领取");
            final JSONObject snapshot = ApiV3PetStories.snapshot(row, actor);
            final JSONArray items = snapshot.getJSONObject("rewards").getJSONArray("items"), changes = new JSONArray();
            int foodDelta = 0;
            // 事件奖励只给固定物品，不产币、不侵入工作收入额度，且同账号整链只领取一次。
            for (int index = 0; index < items.length(); index++) {
                final JSONObject item = items.getJSONObject(index);
                final String itemId = item.getString("itemId");
                final int quantity = item.getInt("quantity");
                changeItem(actor, itemId, quantity, current);
                changes.put(itemChange(itemId, quantity));
                if ("food".equals(itemId)) foodDelta = Math.addExact(foodDelta, quantity);
            }
            row.put("claimedAt", current).put("updatedAt", current);
            storyProgress.update(row.getString("oId"), row, "claimedAt", "updatedAt");
            appendJournal(actor, id, "story", "story-claim", "事件奖励 · " + snapshot.getString("name"),
                    changes, 0, ApiV3PetJournal.zeroEffects(), null, current);
            record(actor, id, "story-claim", 0, foodDelta, current);
            return receipt("story-claim", id, current).put("storyId", storyId);
        });
    }

    /** 查询目录只用于新的出发命令；已经开始的活动始终使用数据库冻结的成果。 */
    protected JSONObject activityEntry(final String kind, final String id) {
        return ApiV3PetActivities.find(kind, id);
    }

    public JSONObject startActivity(final JSONObject identity, final JSONObject body, final String key) {
        exact(body, Set.of("petId", "kind", "entryId", "activityVersion"));
        final String id = petId(body), kind = string(body, "kind"), entryId = string(body, "entryId");
        if (!Set.of("outing", "course", "work").contains(kind) || !entryId.matches("[a-z][a-z0-9-]{0,47}"))
            throw ApiV3WriteInputs.invalid();
        final int version = ApiV3WriteInputs.integer(body, "activityVersion", -1, 1, Integer.MAX_VALUE);
        final JSONObject input = new JSONObject().put("petId", id).put("kind", kind)
                .put("entryId", entryId).put("activityVersion", version);
        return mutate(identity, "activity-start", key, input, () -> {
            final String actor = actor(identity);
            final JSONObject account = requiredAccount(actor);
            final long current = now();
            final JSONObject pet = ApiV3PetRules.settle(owned(actor, id), current);
            requireActivityFree(actor, id);
            if ("rest".equals(pet.getString("activityKind")))
                throw ApiV3PetRules.conflict("pet_resting", "宠物正在休息，醒来后再出发吧");
            if (current < pet.getLong("lastCalculatedAt"))
                throw ApiV3PetRules.conflict("clock_changed", "时间正在校准，请稍后再出发");
            if (version != ApiV3PetActivities.VERSION)
                throw ApiV3PetRules.conflict("catalog_changed", "活动安排已更新，请重新查看");
            final JSONObject entry = activityEntry(kind, entryId), cost = entry.getJSONObject("cost");
            final String activityId = Ids.genTimeMillisId();
            final JSONObject row = new JSONObject().put("oId", activityId).put("userId", actor).put("petId", id)
                    .put("kind", kind).put("entryId", entryId).put("slotKey", "active:" + actor)
                    .put("startedAt", current).put("endsAt", Math.addExact(current, entry.getLong("durationMs")))
                    .put("claimedAt", 0L).put("snapshotJson", new JSONObject().put("version", version).put("entry", entry).toString());
            ApiV3PetActivities.snapshot(row);
            if ("work".equals(kind)) {
                final JSONObject budget = incomeRow(actor);
                checkIncomeClock(budget, current);
                if (incomeState(budget, ApiV3PetRules.today(current)).getInt("remaining") == 0)
                    throw ApiV3PetRules.conflict("income_limit", "今天的工作收入已达上限，明天再来吧");
                final JSONObject requirement = entry.getJSONObject("requirement");
                final int experience = skillRows(actor, id).stream()
                        .filter(progress -> requirement.getString("skillId").equals(progress.getString("skillId")))
                        .mapToInt(progress -> progress.getInt("experience")).findFirst().orElse(0);
                if (experience < requirement.getInt("experience"))
                    throw ApiV3PetRules.conflict("job_locked", "先完成对应的入门课程，再来尝试这份工作吧");
            }
            requireSkills(actor, id, entry.getJSONArray("requirements"));
            if (account.getInt("coins") < cost.getInt("coins"))
                throw ApiV3PetRules.conflict("not_enough_coins", "宠物币不足");
            for (String field : Set.of("energy", "hunger", "water")) {
                if (pet.getDouble(field) < cost.getInt(field))
                    throw ApiV3PetRules.conflict("not_enough_" + field, "宠物状态不足，先照顾好它再出发吧");
            }
            for (String field : Set.of("energy", "hunger", "water")) pet.put(field, pet.getDouble(field) - cost.getInt(field));
            pet.put("updatedAt", current);
            account.put("coins", account.getInt("coins") - cost.getInt("coins")).put("updatedAt", current);
            // 用户行锁与活动槽唯一键同时保护不同幂等键；费用、计时、快照和流水不可分开提交。
            activities.add(row);
            pets.update(id, pet, PET_FIELDS);
            accounts.update(actor, account, ACCOUNT_FIELDS);
            record(actor, id, "activity-start", -cost.getInt("coins"), 0, current);
            return receipt("activity-start", id, current).put("activityId", activityId);
        });
    }

    public JSONObject claimActivity(final JSONObject identity, final JSONObject body, final String key) {
        exact(body, Set.of("petId", "activityId"));
        final String id = petId(body), activityId = string(body, "activityId");
        if (!activityId.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
        final JSONObject input = new JSONObject().put("petId", id).put("activityId", activityId);
        return mutate(identity, "activity-claim", key, input, () -> {
            final String actor = actor(identity);
            final JSONObject account = requiredAccount(actor);
            final long current = now();
            final JSONObject pet = ApiV3PetRules.settle(owned(actor, id), current);
            final var rows = activities.select("SELECT * FROM `" + activities.getName() + "` WHERE oId=? AND userId=? FOR UPDATE", activityId, actor);
            if (rows.isEmpty()) throw ApiV3Requests.notFound();
            if (rows.size() != 1) throw unavailable();
            final JSONObject activity = rows.getFirst();
            if (!id.equals(activity.optString("petId"))) throw ApiV3Requests.notFound();
            final JSONObject rewards = ApiV3PetActivities.snapshot(activity).getJSONObject("rewards");
            if (activity.getLong("claimedAt") > 0)
                throw ApiV3PetRules.conflict("activity_claimed", "这次活动的成果已经领取了");
            if (!activityId.equals(requiredActivity(actor, id).getString("oId")) || !pet.getString("activityKind").isEmpty()) throw unavailable();
            if (current < activity.getLong("endsAt"))
                throw ApiV3PetRules.conflict("activity_running", "活动还没结束，等宠物回来再领取吧");
            if (current < pet.getLong("lastCalculatedAt"))
                throw ApiV3PetRules.conflict("clock_changed", "时间正在校准，请稍后再领取");
            final JSONObject actual = new JSONObject(rewards.toString());
            actual.put("mood", Math.min(100, pet.getDouble("mood") + rewards.getInt("mood")) - pet.getDouble("mood"));
            if ("work".equals(activity.getString("kind"))) {
                final int credited = settleIncome(actor, rewards.getInt("coins"), current);
                actual.put("coins", credited);
                account.put("coins", Math.addExact(account.getInt("coins"), credited)).put("updatedAt", current);
                accounts.update(actor, account, ACCOUNT_FIELDS);
                advanceJob(actor, id, activity.getString("entryId"), rewards.getInt("proficiency"), current);
            }
            // 到时仅变为待领取，读取和重连不发奖；已领取记录持久保留，超过回执保留期也不能重复领取。
            pet.put("experience", Math.addExact(pet.getInt("experience"), rewards.getInt("experience")))
                    .put("mood", Math.min(100, pet.getDouble("mood") + rewards.getInt("mood"))).put("updatedAt", current);
            final var existingSkills = skillRows(actor, id);
            final JSONArray gains = rewards.getJSONArray("skills");
            for (int index = 0; index < gains.length(); index++) {
                final JSONObject gain = gains.getJSONObject(index);
                final String skillId = gain.getString("id");
                final JSONObject progress = existingSkills.stream().filter(row -> row.getString("skillId").equals(skillId)).findFirst().orElse(null);
                if (progress == null) {
                    skills.add(new JSONObject().put("oId", id + ":" + skillId).put("userId", actor).put("petId", id)
                            .put("skillId", skillId).put("experience", gain.getInt("experience")).put("updatedAt", current));
                } else {
                    progress.put("experience", Math.addExact(progress.getInt("experience"), gain.getInt("experience"))).put("updatedAt", current);
                    skills.update(progress.getString("oId"), progress, "experience", "updatedAt");
                }
            }
            changeFood(food(actor), rewards.getInt("food"), current);
            final JSONArray materials = rewards.getJSONArray("items");
            for (int index = 0; index < materials.length(); index++) {
                final JSONObject material = materials.getJSONObject(index);
                changeItem(actor, material.getString("itemId"), material.getInt("quantity"), current);
            }
            pets.update(id, pet, PET_FIELDS);
            activity.put("claimedAt", current).put("slotKey", "claimed:" + activityId);
            activities.update(activityId, activity, "claimedAt", "slotKey");
            // 保存实际入账与心情净增量，历史页不能把计划薪酬或被上限截断的心情当成实际成果。
            results.add(new JSONObject().put("oId", activityId).put("userId", actor).put("petId", id).put("actualJson", actual.toString()));
            record(actor, id, "activity-claim", actual.getInt("coins"), rewards.getInt("food"), current);
            advanceStories(actor, "activity", "", activity.getString("kind"), activity.getString("entryId"), current);
            return receipt("activity-claim", id, current).put("activityId", activityId);
        });
    }

    public JSONObject history(final JSONObject identity, final String kind, final int page, final int size) {
        if (!Set.of("all", "outing", "course", "work").contains(kind) || page < 1 || page > 10_000 || size < 1 || size > 50)
            throw ApiV3WriteInputs.invalid();
        return readTransaction(identity, () -> {
            final String actor = actor(identity);
            final long current = now();
            final String where = " WHERE a.userId=?" + ("all".equals(kind) ? "" : " AND a.kind=?");
            final java.util.List<Object> params = new java.util.ArrayList<>();
            params.add(actor);
            if (!"all".equals(kind)) params.add(kind);
            final var totals = activities.select("SELECT COUNT(*) AS total FROM `" + activities.getName() + "` a" + where, params.toArray());
            if (totals.size() != 1) throw unavailable();
            nonnegative(totals.getFirst(), "total");
            final int total = totals.getFirst().getInt("total");
            params.add(size);
            params.add((page - 1) * size);
            // 分类和归属条件同时用于计数与列表，实际成果一次联查，不给每条日志增加请求或查询。
            final var rows = activities.select("SELECT a.*,r.actualJson AS actualJson,r.userId AS resultUserId,r.petId AS resultPetId FROM `"
                    + activities.getName() + "` a LEFT JOIN `" + results.getName() + "` r ON r.oId=a.oId" + where
                    + " ORDER BY a.startedAt DESC,a.oId DESC LIMIT ? OFFSET ?", params.toArray());
            final JSONArray list = new JSONArray();
            for (JSONObject row : rows) {
                final JSONObject item = ApiV3PetActivities.publicActivity(row, current).put("petId", row.getString("petId"));
                final long claimedAt = row.getLong("claimedAt");
                if (claimedAt > 0) item.put("status", "claimed");
                item.put("claimedAt", claimedAt > 0 ? claimedAt : JSONObject.NULL)
                        .put("actualRewards", actualResult(row));
                list.put(item);
            }
            return new JSONObject().put("serverNow", current).put("list", list)
                    .put("pagination", new JSONObject().put("page", page).put("pageSize", size).put("total", total)
                            .put("pageCount", Math.max(1, (total + size - 1) / size)));
        });
    }

    /** 计时活动与瞬时照顾统一分页；旧活动直接联查，不用迁移伪造旧照顾事件。 */
    public JSONObject journal(final JSONObject identity, final String kind, final int page, final int size) {
        if (!Set.of("all", "outing", "course", "work", "care", "craft", "purchase", "story", "readoption").contains(kind)
                || page < 1 || page > 10_000 || size < 1 || size > 50) throw ApiV3WriteInputs.invalid();
        return readTransaction(identity, () -> {
            final String actor = actor(identity);
            final long current = now();
            final java.util.List<Object> params = new java.util.ArrayList<>();
            final StringBuilder union = new StringBuilder();
            if (Set.of("all", "outing", "course", "work").contains(kind)) {
                union.append("SELECT 'activity' AS recordType,oId,startedAt AS eventTime FROM `")
                        .append(activities.getName()).append("` WHERE userId=?");
                params.add(actor);
                if (!"all".equals(kind)) {
                    union.append(" AND kind=?");
                    params.add(kind);
                }
            }
            if (Set.of("all", "care", "craft", "purchase", "story", "readoption").contains(kind)) {
                if (!union.isEmpty()) union.append(" UNION ALL ");
                union.append("SELECT 'event' AS recordType,oId,occurredAt AS eventTime FROM `")
                        .append(journal.getName()).append("` WHERE userId=?");
                params.add(actor);
                if (!"all".equals(kind)) {
                    union.append(" AND kind=?");
                    params.add(kind);
                }
            }
            final var totals = journal.select("SELECT COUNT(*) AS total FROM (" + union + ") pet_log", params.toArray());
            if (totals.size() != 1) throw unavailable();
            nonnegative(totals.getFirst(), "total");
            final int total = totals.getFirst().getInt("total");
            params.add(size);
            params.add((page - 1) * size);
            // 先在数据库合并排序和分页，再联查本页内容；不向应用层拉取全部历史、不逐条查询。
            final String query = "SELECT x.recordType,a.*,r.actualJson AS actualJson,r.userId AS resultUserId,r.petId AS resultPetId,"
                    + "j.oId AS eventId,j.userId AS eventUserId,j.petId AS eventPetId,j.kind AS eventKind,"
                    + "j.action AS eventAction,j.occurredAt AS eventOccurredAt,j.eventJson AS eventJson FROM ("
                    + union + " ORDER BY eventTime DESC,oId DESC,recordType DESC LIMIT ? OFFSET ?) x"
                    + " LEFT JOIN `" + activities.getName() + "` a ON x.recordType='activity' AND a.oId=x.oId"
                    + " LEFT JOIN `" + results.getName() + "` r ON x.recordType='activity' AND r.oId=x.oId"
                    + " LEFT JOIN `" + journal.getName() + "` j ON x.recordType='event' AND j.oId=x.oId"
                    + " ORDER BY x.eventTime DESC,x.oId DESC,x.recordType DESC";
            final JSONArray list = new JSONArray();
            for (JSONObject row : journal.select(query, params.toArray())) {
                if ("event".equals(row.getString("recordType"))) {
                    list.put(ApiV3PetJournal.read(row, actor));
                } else {
                    if (!actor.equals(row.optString("userId"))) throw unavailable();
                    final JSONObject item = ApiV3PetActivities.publicActivity(row, current)
                            .put("recordType", "activity").put("petId", row.getString("petId"));
                    final long claimedAt = row.getLong("claimedAt");
                    if (claimedAt > 0) item.put("status", "claimed");
                    list.put(item.put("claimedAt", claimedAt > 0 ? claimedAt : JSONObject.NULL)
                            .put("actualRewards", actualResult(row)));
                }
            }
            return new JSONObject().put("serverNow", current).put("list", list)
                    .put("pagination", new JSONObject().put("page", page).put("pageSize", size).put("total", total)
                            .put("pageCount", Math.max(1, (total + size - 1) / size)));
        });
    }

    private Object actualResult(final JSONObject row) {
        final boolean hasResult = row.opt("actualJson") instanceof String;
        if (!hasResult) {
            // 只有第一版已领取记录没有实际结果；新记录缺少结果表示存储异常，不能当成旧记录吞错。
            if (row.getLong("claimedAt") > 0 && new JSONObject(row.getString("snapshotJson")).getInt("version") >= 2)
                throw unavailable();
            return JSONObject.NULL;
        }
        if (row.getLong("claimedAt") == 0 || !row.getString("userId").equals(row.optString("resultUserId"))
                || !row.getString("petId").equals(row.optString("resultPetId"))) throw unavailable();
        try {
            final JSONObject planned = ApiV3PetActivities.snapshot(row).getJSONObject("rewards");
            final JSONObject actual = new JSONObject(row.getString("actualJson"));
            final int version = new JSONObject(row.getString("snapshotJson")).getInt("version");
            // 第三批及更早的实际结果没有材料字段，只补确定为空的投影，不回写历史数据。
            if (version < 3 && !actual.has("items")) actual.put("items", new JSONArray());
            if (!actual.getJSONArray("items").similar(planned.getJSONArray("items"))) throw unavailable();
            if (!actual.keySet().equals(planned.keySet())) throw unavailable();
            for (String field : Set.of("experience", "food", "coins", "proficiency")) nonnegative(actual, field);
            for (String field : Set.of("experience", "food", "proficiency"))
                if (actual.getInt(field) != planned.getInt(field)) throw unavailable();
            if (actual.getInt("coins") > planned.getInt("coins") || !actual.getJSONArray("skills").similar(planned.getJSONArray("skills"))) throw unavailable();
            if (!(actual.opt("mood") instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() < 0 || number.doubleValue() > planned.getInt("mood") + 1e-9) throw unavailable();
            return actual;
        } catch (Exception error) {
            throw unavailable();
        }
    }

    private JSONObject incomeRow(final String actor) throws RepositoryException {
        final var rows = income.select("SELECT * FROM `" + income.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) throw unavailable();
        final JSONObject row = rows.getFirst();
        nonnegative(row, "earned");
        try {
            final String date = row.getString("date");
            if (!LocalDate.parse(date).toString().equals(date) || row.getLong("lastSettlementAt") < 0
                    || !ApiV3PetRules.today(row.getLong("lastSettlementAt")).equals(date)) throw unavailable();
        } catch (Exception error) {
            throw unavailable();
        }
        return row;
    }

    private JSONObject incomeState(final JSONObject budget, final String today) {
        final boolean current = budget != null && today.compareTo(budget.getString("date")) <= 0;
        final int earned = current ? budget.getInt("earned") : 0;
        return new JSONObject().put("date", current ? budget.getString("date") : today).put("earned", earned)
                .put("limit", ApiV3PetWork.DAILY_LIMIT).put("remaining", Math.max(0, ApiV3PetWork.DAILY_LIMIT - earned));
    }

    private void checkIncomeClock(final JSONObject budget, final long current) {
        if (budget != null && (current < budget.getLong("lastSettlementAt")
                || ApiV3PetRules.today(current).compareTo(budget.getString("date")) < 0))
            throw ApiV3PetRules.conflict("clock_changed", "时间正在校准，请稍后再处理工作");
    }

    private int settleIncome(final String actor, final int planned, final long current) throws RepositoryException {
        final JSONObject previous = incomeRow(actor);
        checkIncomeClock(previous, current);
        final String date = ApiV3PetRules.today(current);
        final JSONObject state = incomeState(previous, date);
        final int credited = Math.min(planned, state.getInt("remaining"));
        final JSONObject next = new JSONObject().put("oId", actor).put("date", date)
                .put("earned", Math.addExact(state.getInt("earned"), credited)).put("lastSettlementAt", current);
        // 上限按领取日与账号计算，不随换宠物重置；满额仍允许领取已开始工作的其他成果并释放活动槽。
        if (previous == null) income.add(next);
        else income.update(actor, next, "date", "earned", "lastSettlementAt");
        return credited;
    }

    private java.util.List<JSONObject> jobRows(final String actor, final String id) throws RepositoryException {
        final var rows = jobs.select("SELECT * FROM `" + jobs.getName() + "` WHERE petId=? FOR UPDATE", id);
        final Set<String> seen = new java.util.HashSet<>();
        for (JSONObject row : rows) {
            final String job = row.optString("jobId");
            if (!actor.equals(row.optString("userId")) || !ApiV3PetWork.known(job) || !seen.add(job)) throw unavailable();
            nonnegative(row, "experience");
            nonnegative(row, "completed");
        }
        return rows;
    }

    private JSONArray jobState(final String actor, final String id) throws RepositoryException {
        final var rows = jobRows(actor, id);
        final JSONArray state = new JSONArray();
        for (var definition : ApiV3PetWork.JOBS) {
            final JSONObject row = rows.stream().filter(progress -> progress.getString("jobId").equals(definition.id())).findFirst().orElse(null);
            final int experience = row == null ? 0 : row.getInt("experience");
            state.put(new JSONObject().put("id", definition.id()).put("experience", experience)
                    .put("level", ApiV3PetActivities.level(experience)).put("completed", row == null ? 0 : row.getInt("completed")));
        }
        return state;
    }

    private void advanceJob(final String actor, final String id, final String job, final int experience, final long current) throws RepositoryException {
        final JSONObject progress = jobRows(actor, id).stream().filter(row -> row.getString("jobId").equals(job)).findFirst().orElse(null);
        if (progress == null) {
            jobs.add(new JSONObject().put("oId", id + ":" + job).put("userId", actor).put("petId", id).put("jobId", job)
                    .put("experience", experience).put("completed", 1).put("updatedAt", current));
        } else {
            progress.put("experience", Math.addExact(progress.getInt("experience"), experience))
                    .put("completed", Math.addExact(progress.getInt("completed"), 1)).put("updatedAt", current);
            jobs.update(progress.getString("oId"), progress, "experience", "completed", "updatedAt");
        }
    }

    private JSONObject activeActivity(final String actor, final String id) throws RepositoryException {
        final var rows = activities.select("SELECT * FROM `" + activities.getName() + "` WHERE userId=? AND claimedAt=0 FOR UPDATE", actor);
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) throw unavailable();
        final JSONObject row = rows.getFirst();
        if (!id.equals(row.optString("petId")) || !("active:" + actor).equals(row.optString("slotKey"))) throw unavailable();
        ApiV3PetActivities.snapshot(row);
        return row;
    }

    private JSONObject requiredActivity(final String actor, final String id) throws RepositoryException {
        final JSONObject active = activeActivity(actor, id);
        if (active == null) throw unavailable();
        return active;
    }

    private void requireActivityFree(final String actor, final String id) throws RepositoryException {
        if (activeActivity(actor, id) != null)
            throw ApiV3PetRules.conflict("pet_busy", "宠物正在活动或等待领取成果，完成后再照顾它吧");
    }

    private java.util.List<JSONObject> skillRows(final String actor, final String id) throws RepositoryException {
        final var rows = skills.select("SELECT * FROM `" + skills.getName() + "` WHERE petId=? FOR UPDATE", id);
        final Set<String> seen = new java.util.HashSet<>();
        for (JSONObject row : rows) {
            final String skill = row.optString("skillId");
            if (!actor.equals(row.optString("userId")) || !ApiV3PetActivities.skill(skill) || !seen.add(skill)) throw unavailable();
            nonnegative(row, "experience");
        }
        return rows;
    }

    private JSONArray skillState(final String actor, final String id) throws RepositoryException {
        final var rows = skillRows(actor, id);
        final JSONArray result = new JSONArray();
        for (var definition : ApiV3PetActivities.SKILLS) {
            final int experience = rows.stream().filter(row -> row.getString("skillId").equals(definition.id()))
                    .mapToInt(row -> row.getInt("experience")).findFirst().orElse(0);
            result.put(new JSONObject().put("id", definition.id()).put("experience", experience).put("level", ApiV3PetActivities.level(experience)));
        }
        return result;
    }

    private void grow(final JSONObject pet, final JSONObject account, final long current) {
        final String today = ApiV3PetRules.today(current);
        // 服务器时钟回拨跨日时不重复发奖或重置额度，等日期重新向前推进再开启新一天。
        if (today.compareTo(account.getString("growthDate")) > 0) account.put("growthDate", today).put("growthCount", 0);
        if (account.getInt("growthCount") >= ApiV3PetRules.DAILY_GROWTH) return;
        account.put("growthCount", account.getInt("growthCount") + 1);
        pet.put("experience", Math.addExact(pet.getInt("experience"), ApiV3PetRules.CARE_EXPERIENCE))
                .put("friendship", Math.min(100, pet.getInt("friendship") + ApiV3PetRules.CARE_FRIENDSHIP));
    }

    private static void cooldown(final JSONObject pet, final String field, final long current) {
        if (current < pet.getLong(field)) throw ApiV3PetRules.conflict("care_cooldown", "刚刚互动过，稍后再来吧");
    }

    private JSONObject account(final String actor) throws RepositoryException {
        final var rows = accounts.select("SELECT * FROM `" + accounts.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.size() > 1) throw unavailable();
        if (rows.isEmpty()) return null;
        final JSONObject row = rows.getFirst();
        nonnegative(row, "coins");
        nonnegative(row, "growthCount");
        if (row.getInt("growthCount") > ApiV3PetRules.DAILY_GROWTH || !(row.opt("supplyDate") instanceof String)
                || !(row.opt("growthDate") instanceof String)) throw unavailable();
        return row;
    }

    private JSONObject requiredAccount(final String actor) throws RepositoryException {
        final JSONObject account = account(actor);
        if (account == null) throw ApiV3PetRules.conflict("pet_required", "请先领养一只宠物");
        return account;
    }

    private JSONObject pet(final String actor) throws RepositoryException {
        final var rows = pets.select("SELECT * FROM `" + pets.getName() + "` WHERE userId=? FOR UPDATE", actor);
        if (rows.size() > 1) throw unavailable();
        if (rows.isEmpty()) return null;
        final JSONObject pet = rows.getFirst();
        if (!ApiV3PetRules.SPECIES.contains(pet.optString("speciesId")) || !(pet.opt("name") instanceof String)
                || !Set.of("", "rest").contains(pet.optString("activityKind", "invalid"))) throw unavailable();
        for (String field : Set.of("hunger", "water", "mood", "energy")) {
            if (!(pet.opt(field) instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() < 0 || number.doubleValue() > 100) throw unavailable();
        }
        for (String field : Set.of("experience", "friendship")) nonnegative(pet, field);
        if (pet.getInt("friendship") > 100) throw unavailable();
        for (String field : Set.of("lastCalculatedAt", "activityStartedAt", "activityEndsAt", "petAvailableAt", "playAvailableAt", "adoptedAt")) {
            if (!(pet.opt(field) instanceof Number number) || number.longValue() < 0) throw unavailable();
        }
        if ("rest".equals(pet.getString("activityKind")) && pet.getLong("activityEndsAt") - pet.getLong("activityStartedAt") != ApiV3PetRules.REST_DURATION)
            throw unavailable();
        return pet;
    }

    private JSONObject owned(final String actor, final String expectedId) throws RepositoryException {
        final JSONObject pet = pet(actor);
        if (pet == null || (expectedId != null && !expectedId.equals(pet.optString("oId")))) throw ApiV3Requests.notFound();
        return pet;
    }

    private java.util.List<JSONObject> storyRows(final String actor) throws RepositoryException {
        final var rows = storyProgress.select("SELECT * FROM `" + storyProgress.getName() + "` WHERE userId=? FOR UPDATE", actor);
        if (rows.size() > ApiV3PetStories.STORIES.size()) throw unavailable();
        final Set<String> seen = new java.util.HashSet<>();
        for (JSONObject row : rows) {
            ApiV3PetStories.snapshot(row, actor);
            if (!seen.add(row.getString("storyId"))) throw unavailable();
        }
        return rows;
    }

    private JSONArray storyState(final String actor) throws RepositoryException {
        final var rows = storyRows(actor);
        final JSONArray state = new JSONArray();
        for (var definition : ApiV3PetStories.STORIES) {
            final JSONObject row = rows.stream().filter(item -> definition.id().equals(item.getString("storyId")))
                    .findFirst().orElse(null);
            state.put(ApiV3PetStories.state(definition, row));
        }
        return state;
    }

    /** 仅当前步骤消费一次真实业务事件，越序动作不累计；进度写入与原动作成败一致。 */
    private void advanceStories(final String actor, final String trigger, final String action,
                                final String kind, final String entryId, final long current) throws RepositoryException {
        final var rows = storyRows(actor);
        for (var definition : ApiV3PetStories.STORIES) {
            final JSONObject previous = rows.stream().filter(item -> definition.id().equals(item.getString("storyId")))
                    .findFirst().orElse(null);
            final JSONObject story = previous == null ? definition.json() : ApiV3PetStories.snapshot(previous, actor);
            final JSONArray steps = story.getJSONArray("steps");
            final int completed = previous == null ? 0 : previous.getInt("completedSteps");
            if (completed >= steps.length() || !ApiV3PetStories.matches(steps.getJSONObject(completed), trigger, action, kind, entryId)) continue;
            if (previous != null && current < previous.getLong("updatedAt"))
                throw ApiV3PetRules.conflict("clock_changed", "时间正在校准，请稍后再试");
            final JSONObject row = previous == null
                    ? new JSONObject().put("oId", actor + ":" + definition.id()).put("userId", actor).put("storyId", definition.id())
                            .put("claimedAt", 0L).put("snapshotJson", ApiV3PetStories.freeze(definition))
                    : previous;
            row.put("completedSteps", completed + 1).put("completedAt", completed + 1 == steps.length() ? current : 0L)
                    .put("updatedAt", current);
            ApiV3PetStories.snapshot(row, actor);
            if (previous == null) storyProgress.add(row);
            else storyProgress.update(row.getString("oId"), row, "completedSteps", "completedAt", "updatedAt");
        }
    }

    private void requireSkills(final String actor, final String id, final JSONArray requirements) throws RepositoryException {
        if (requirements.isEmpty()) return;
        final var progress = skillRows(actor, id);
        for (int index = 0; index < requirements.length(); index++) {
            final JSONObject requirement = requirements.getJSONObject(index);
            final int experience = progress.stream()
                    .filter(row -> requirement.getString("skillId").equals(row.getString("skillId")))
                    .mapToInt(row -> row.getInt("experience")).findFirst().orElse(0);
            if (experience < requirement.getInt("experience"))
                throw ApiV3PetRules.conflict("skill_required", "先完成对应入门课程，再来尝试吧");
        }
    }

    private JSONArray collectionState(final String actor) throws RepositoryException {
        final JSONArray state = new JSONArray();
        final Set<String> seen = new java.util.HashSet<>();
        final var rows = collections.select("SELECT * FROM `" + collections.getName() + "` WHERE userId=? ORDER BY unlockedAt,oId", actor);
        if (rows.size() > ApiV3PetExploration.RECIPES.size()) throw unavailable();
        for (JSONObject row : rows) {
            final String id = row.optString("collectionId");
            if (!ApiV3PetExploration.knownCollection(id) || !seen.add(id) || row.getLong("unlockedAt") < 0) throw unavailable();
            state.put(new JSONObject().put("id", id).put("unlockedAt", row.getLong("unlockedAt")));
        }
        return state;
    }

    private JSONArray inventoryState(final String actor) throws RepositoryException {
        final var rows = inventory.select("SELECT * FROM `" + inventory.getName() + "` WHERE userId=? FOR UPDATE", actor);
        final Set<String> seen = new java.util.HashSet<>();
        for (JSONObject row : rows) {
            if (!ApiV3PetExploration.knownItem(row.optString("itemId")) || !seen.add(row.getString("itemId"))) throw unavailable();
            nonnegative(row, "quantity");
        }
        if (!seen.contains("food")) throw unavailable();
        final JSONArray state = new JSONArray();
        for (var definition : ApiV3PetExploration.ITEMS) {
            final int quantity = rows.stream().filter(row -> definition.id().equals(row.getString("itemId")))
                    .mapToInt(row -> row.getInt("quantity")).findFirst().orElse(0);
            state.put(new JSONObject().put("itemId", definition.id()).put("quantity", quantity));
        }
        return state;
    }

    private static boolean itemHasEffect(final ApiV3PetExploration.Item item, final JSONObject pet) {
        final JSONObject gains = item.effects();
        for (String field : Set.of("hunger", "water", "mood", "energy")) {
            if (gains.getInt(field) > 0 && pet.getDouble(field) < 100 - 1e-9) return true;
        }
        return false;
    }

    private void consumeItem(final String actor, final ApiV3PetExploration.Item item, final JSONObject pet,
                             final long current) throws RepositoryException {
        changeItem(actor, item.id(), -1, current);
        final JSONObject gains = item.effects();
        for (String field : Set.of("hunger", "water", "mood", "energy"))
            pet.put(field, Math.min(100, pet.getDouble(field) + gains.getInt(field)));
    }

    private void changeItem(final String actor, final String itemId, final int difference, final long current) throws RepositoryException {
        if (!ApiV3PetExploration.knownItem(itemId)) throw unavailable();
        final var rows = inventory.select("SELECT * FROM `" + inventory.getName() + "` WHERE userId=? AND itemId=? FOR UPDATE", actor, itemId);
        if (rows.size() > 1 || (rows.isEmpty() && "food".equals(itemId))) throw unavailable();
        final JSONObject previous = rows.isEmpty() ? null : rows.getFirst();
        if (previous != null) nonnegative(previous, "quantity");
        final int quantity = Math.addExact(previous == null ? 0 : previous.getInt("quantity"), difference);
        if (quantity < 0) throw ApiV3PetRules.conflict("food".equals(itemId) ? "not_enough_food" : "not_enough_items", "背包里的物品不足");
        if (previous == null) {
            inventory.add(new JSONObject().put("oId", actor + ":" + itemId).put("userId", actor).put("itemId", itemId)
                    .put("quantity", quantity).put("updatedAt", current));
        } else {
            previous.put("quantity", quantity).put("updatedAt", current);
            inventory.update(previous.getString("oId"), previous, "quantity", "updatedAt");
        }
    }

    private static JSONObject itemChange(final String itemId, final int quantity) {
        return new JSONObject().put("itemId", itemId).put("quantity", quantity);
    }

    private void appendJournal(final String actor, final String petId, final String kind, final String action, final String name,
                               final JSONArray itemChanges, final int coins, final JSONObject effects,
                               final String collectionId, final long current) throws RepositoryException {
        final String id = Ids.genTimeMillisId();
        final JSONObject event = new JSONObject().put("recordType", "event").put("id", id).put("petId", petId)
                .put("kind", kind).put("action", action).put("name", name).put("occurredAt", current)
                .put("itemChanges", itemChanges).put("coinsDelta", coins).put("effects", effects)
                .put("collectionId", collectionId == null ? JSONObject.NULL : collectionId);
        ApiV3PetJournal.validate(event);
        journal.add(new JSONObject().put("oId", id).put("userId", actor).put("petId", petId).put("kind", kind)
                .put("action", action).put("occurredAt", current).put("eventJson", event.toString()));
    }

    private JSONObject food(final String actor) throws RepositoryException {
        final var rows = inventory.select("SELECT * FROM `" + inventory.getName() + "` WHERE userId=? AND itemId=? FOR UPDATE", actor, "food");
        if (rows.size() != 1) throw unavailable();
        final JSONObject food = rows.getFirst();
        nonnegative(food, "quantity");
        return food;
    }

    private void changeFood(final JSONObject food, final int difference, final long current) throws RepositoryException {
        food.put("quantity", Math.addExact(food.getInt("quantity"), difference)).put("updatedAt", current);
        inventory.update(food.getString("oId"), food, "quantity", "updatedAt");
    }

    private void record(final String actor, final String petId, final String operation, final int coins,
                        final int food, final long current) throws RepositoryException {
        ledger.add(new JSONObject().put("oId", Ids.genTimeMillisId()).put("userId", actor).put("petId", petId)
                .put("operation", operation).put("coinDelta", coins).put("foodDelta", food).put("createdAt", current));
    }

    private JSONObject readTransaction(final JSONObject identity, final ApiV3IdempotencyMgmtService.Mutation read) {
        Transaction transaction = null;
        try {
            transaction = begin();
            authorize(identity, "profile.read");
            final JSONObject result = read.apply();
            transaction.commit();
            return result;
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            if (transaction != null && transaction.isActive()) transaction.rollback();
        }
    }

    private JSONObject mutate(final JSONObject identity, final String operation, final String key, final JSONObject input,
                              final ApiV3IdempotencyMgmtService.Mutation mutation) {
        final var admission = risk.before(identity, operation, key, input);
        if (admission.receipt() != null) return admission.receipt();
        final String actor = actor(identity);
        final ApiV3MutationStore guarded = new ApiV3MutationStore() {
            public Transaction beginTransaction() { return begin(); }
            public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
            public JSONObject getForUpdate(final String id) throws RepositoryException {
                // 同账号不同命令也先取得真实用户行锁；跨节点的领养、补给与消费因此不会相互覆盖。
                authorize(identity, "settings.write");
                return requests.getForUpdate(id);
            }
            public String add(final JSONObject row) throws RepositoryException { return requests.add(row); }
            public void update(final String id, final JSONObject row, final String... fields) throws RepositoryException {
                requests.update(id, row, fields);
            }
        };
        try {
            return ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "pet." + operation, key, input, () -> {
                risk.authorizeMutation(actor, operation, key, input);
                return mutation.apply();
            });
        } catch (ApiV3Exception error) {
            risk.denied(identity, operation, key, input, error);
            throw error;
        } catch (Exception error) {
            throw unavailable();
        }
    }

    private Transaction begin() {
        if (requests.hasTransactionBegun()) throw new IllegalStateException("pet root transaction required");
        return ApiV3Transactions.strict(requests.beginTransaction());
    }

    private void authorize(final JSONObject identity, final String scope) throws RepositoryException {
        if (!actor(identity).equals(proofs.authorizeFirstPartyInCurrentTransaction(identity, scope).optString("oId")))
            throw ApiV3Requests.forbidden();
    }

    private static String actor(final JSONObject identity) {
        final JSONObject user = identity == null ? null : identity.optJSONObject("user");
        final String id = user == null ? "" : user.optString("oId");
        if (!id.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.forbidden();
        return id;
    }

    private static String petId(final JSONObject body) {
        final String id = string(body, "petId");
        if (!id.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
        return id;
    }

    private static String string(final JSONObject body, final String field) {
        if (body == null || !(body.opt(field) instanceof String text) || text.isEmpty() || text.length() > 64)
            throw ApiV3WriteInputs.invalid();
        return text;
    }

    private static void exact(final JSONObject body, final Set<String> fields) {
        if (body == null || !body.keySet().equals(fields)) throw ApiV3WriteInputs.invalid();
    }

    private static void nonnegative(final JSONObject row, final String field) {
        if (!(row.opt(field) instanceof Number number) || number.longValue() < 0 || number.longValue() > Integer.MAX_VALUE
                || number.doubleValue() != number.intValue()) throw unavailable();
    }

    private static JSONObject receipt(final String operation, final String id, final long current) {
        // 回执仅确认命令，不补查公开状态；提交成功后即使后续读取失败，也不能再次消费。
        return new JSONObject().put("operation", operation).put("petId", id).put("committedAt", current);
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "宠物家园暂不可用，请稍后再试");
    }
}
