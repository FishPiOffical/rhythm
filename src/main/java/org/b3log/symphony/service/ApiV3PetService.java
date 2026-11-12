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
import org.b3log.symphony.repository.PetInventoryRepository;
import org.b3log.symphony.repository.PetLedgerRepository;
import org.b3log.symphony.repository.UserPetRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3PetRules;
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
        return readTransaction(identity, ApiV3PetRules::catalog);
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
                    .put("wallet", new JSONObject().put("coins", account == null ? 0 : account.getInt("coins")))
                    .put("dailySupply", new JSONObject().put("claimed", account != null && today.compareTo(account.getString("supplyDate")) <= 0)
                            .put("coins", ApiV3PetRules.DAILY_COINS).put("food", ApiV3PetRules.DAILY_FOOD));
            if (pet == null) return result.put("pet", JSONObject.NULL).put("inventory", new JSONArray());
            return result.put("pet", ApiV3PetRules.publicPet(ApiV3PetRules.settle(pet, current), account, today))
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
