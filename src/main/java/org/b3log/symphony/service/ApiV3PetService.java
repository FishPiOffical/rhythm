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
import org.b3log.symphony.repository.PetLedgerRepository;
import org.b3log.symphony.repository.UserPetRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3PetRules;
import org.b3log.symphony.util.ApiV3PetActivities;
import org.b3log.symphony.util.ApiV3PetWork;
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
    @Inject private PetLedgerRepository ledger;
    @Inject private PetActivityRepository activities;
    @Inject private PetSkillRepository skills;
    @Inject private PetJobProgressRepository jobs;
    @Inject private PetIncomeAccountRepository income;
    @Inject private PetActivityResultRepository results;
    @Inject private ApiV3RequestRepository requests;
    @Inject private ApiV3SecurityProofService proofs;

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
                    .put("inventory", new JSONArray().put(new JSONObject().put("itemId", "food").put("quantity", food(actor(identity)).getInt("quantity"))));
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
        if ("feed".equals(action)) {
            if (!"food".equals(string(body, "itemId"))) throw ApiV3WriteInputs.invalid();
        } else if (body.has("itemId")) throw ApiV3WriteInputs.invalid();
        final JSONObject input = new JSONObject().put("petId", id).put("action", action);
        if ("feed".equals(action)) input.put("itemId", "food");
        return mutate(identity, "action", key, input, () -> {
            final String actor = actor(identity);
            final JSONObject account = requiredAccount(actor);
            final long current = now();
            final JSONObject pet = ApiV3PetRules.settle(owned(actor, id), current);
            requireActivityFree(actor, id);
            if ("rest".equals(pet.getString("activityKind")) && !"wake".equals(action))
                throw ApiV3PetRules.conflict("pet_resting", "宠物正在休息，醒来后再照顾它吧");
            switch (action) {
                case "feed" -> {
                    if (pet.getDouble("hunger") > 99 + 1e-9) throw ApiV3PetRules.conflict("already_full", "宠物现在还不饿");
                    final JSONObject food = food(actor);
                    if (food.getInt("quantity") < 1) throw ApiV3PetRules.conflict("not_enough_food", "口粮不足，先去商店补充吧");
                    changeFood(food, -1, current);
                    pet.put("hunger", Math.min(100, pet.getDouble("hunger") + ApiV3PetRules.FOOD_HUNGER));
                    record(actor, id, "feed", 0, -1, current);
                }
                case "water" -> {
                    if (pet.getDouble("water") > 99 + 1e-9) throw ApiV3PetRules.conflict("already_full", "宠物现在还不渴");
                    pet.put("water", Math.min(100, pet.getDouble("water") + ApiV3PetRules.WATER_GAIN));
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
        if (!"food".equals(string(body, "itemId"))) throw ApiV3WriteInputs.invalid();
        final int quantity = ApiV3WriteInputs.integer(body, "quantity", -1, 1, 20);
        final int version = ApiV3WriteInputs.integer(body, "catalogVersion", -1, 1, Integer.MAX_VALUE);
        final JSONObject input = new JSONObject().put("petId", id).put("itemId", "food")
                .put("quantity", quantity).put("catalogVersion", version);
        return mutate(identity, "purchase", key, input, () -> {
            final String actor = actor(identity);
            final JSONObject account = requiredAccount(actor);
            owned(actor, id);
            if (version != ApiV3PetRules.VERSION) throw ApiV3PetRules.conflict("catalog_changed", "商品价格已更新，请重新查看");
            final int price = Math.multiplyExact(quantity, ApiV3PetRules.FOOD_PRICE);
            if (account.getInt("coins") < price) throw ApiV3PetRules.conflict("not_enough_coins", "宠物币不足");
            final long current = now();
            account.put("coins", account.getInt("coins") - price).put("updatedAt", current);
            changeFood(food(actor), quantity, current);
            accounts.update(actor, account, ACCOUNT_FIELDS);
            record(actor, id, "purchase", -price, quantity, current);
            return receipt("purchase", id, current);
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
            pets.update(id, pet, PET_FIELDS);
            activity.put("claimedAt", current).put("slotKey", "claimed:" + activityId);
            activities.update(activityId, activity, "claimedAt", "slotKey");
            // 保存实际入账与心情净增量，历史页不能把计划薪酬或被上限截断的心情当成实际成果。
            results.add(new JSONObject().put("oId", activityId).put("userId", actor).put("petId", id).put("actualJson", actual.toString()));
            record(actor, id, "activity-claim", actual.getInt("coins"), rewards.getInt("food"), current);
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
        pet.put("experience", Math.addExact(pet.getInt("experience"), 10))
                .put("friendship", Math.min(100, pet.getInt("friendship") + 2));
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
            return ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "pet." + operation, key, input, mutation);
        } catch (ApiV3Exception error) {
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
