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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.ParkingGarageRepository;
import org.b3log.symphony.repository.ParkingSessionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.UserPetRepository;
import org.b3log.symphony.repository.ParkingPassengerRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Json;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import static org.b3log.symphony.util.ParkingRules.*;

@Service
public class ParkingService {
    private static final Logger LOG = LogManager.getLogger(ParkingService.class);
    private final Cache<String, Set<String>> attempts = Caffeine.newBuilder()
            .maximumSize(20_000).expireAfterWrite(1, TimeUnit.MINUTES).build();

    private void admit(final String actor, final String commandId) {
        final Set<String> seen = attempts.get(actor, ignored -> new HashSet<>());
        synchronized (seen) {
            if (seen.contains(commandId)) return;
            if (seen.size() >= 30) throw new ApiV3Exception(429, "parking_rate_limited", "操作较频繁，请稍后再试");
            seen.add(commandId);
        }
    }

    @Inject private ParkingGarageRepository garages;
    @Inject private ParkingSessionRepository sessions;
    @Inject private ApiV3RequestRepository requests;
    @Inject private UserRepository users;
    @Inject private ApiV3SecurityProofService proofs;
    @Inject private ApiV3PetRiskService risk;
    @Inject private GameWalletService wallet;

    @Inject private UserPetRepository pets;
    @Inject private ParkingPassengerRepository passengers;
    @Inject private ParkingMetricsService metrics;
    @Inject private ParkingCatalogService offers;
    @Inject private ApiV3AppearanceShopService appearance;
    @Inject private org.b3log.symphony.repository.ParkingVisitRepository visits;
    @Inject private org.b3log.symphony.repository.FollowRepository follows;

    private static final Set<String> NOTES = Set.of("hello","thanks","coffee");
    private static final String JOIN_ID = " COLLATE utf8mb4_0900_ai_ci";

    public JSONObject weekly(final JSONObject identity) throws Exception {
        final Transaction tx = requests.beginTransaction();
        try {
            proofs.authorizeFirstPartyInCurrentTransaction(identity, "profile.read");
            final var zone = java.time.ZoneId.of("Asia/Shanghai");
            final var monday = java.time.LocalDate.now(zone).with(java.time.DayOfWeek.MONDAY);
            final long start = monday.atStartOfDay(zone).toInstant().toEpochMilli();
            final long end = monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli();
            final var rows = sessions.select("SELECT s.driverId,u.userName,u.userNickname,SUM(s.paid) AS income"
                    + " FROM `" + sessions.getName() + "` s JOIN `" + users.getName() + "` u ON u.oId" + JOIN_ID + "=s.driverId"
                    + " WHERE s.endedAt>=? AND s.endedAt<? AND s.paid>0 AND u.userStatus=0"
                    + " GROUP BY s.driverId,u.userName,u.userNickname ORDER BY income DESC,s.driverId LIMIT 50",start,end);
            final var list = new JSONArray();
            for (var row : rows) list.put(new JSONObject().put("userName",row.getString("userName"))
                    .put("name",displayName(row)).put("income",row.getLong("income")));
            tx.commit();
            return new JSONObject().put("weekStart",start).put("list",list);
        } finally { if (tx.isActive()) tx.rollback(); }
    }

    public JSONObject state(final JSONObject identity, final String after, final String scope, final String target) throws Exception {
        if (after != null && !after.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
        if (scope != null && !Set.of("following","all").contains(scope)) throw ApiV3WriteInputs.invalid();
        if (target != null && !target.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
        final Transaction tx = requests.beginTransaction();
        try {
            final String actor = proofs.authorizeFirstPartyInCurrentTransaction(identity, "profile.read").getString("oId");
            final long now = System.currentTimeMillis();
            final Long coins = wallet.balance(actor);
            final JSONObject garage = garage(actor);
            final JSONObject result = new JSONObject().put("serverNow", now).put("catalog", offers.models().getJSONArray("models"))
                    .put("coins", coins == null ? 0L : coins).put("owned", garage == null ? new JSONArray() : new JSONArray(garage.getString("cars")))
                    .put("driving", activity(garage, "driving", now)).put("occupant", activity(garage, "occupant", now));
            final List<Object> params = new ArrayList<>();
            String from = " FROM `" + garages.getName() + "` g JOIN `" + users.getName()
                    + "` u ON u.oId" + JOIN_ID + "=g.oId AND u.userStatus=0";
            if ("following".equals(scope)) {
                from += " WHERE EXISTS (SELECT 1 FROM `" + follows.getName() + "` f WHERE f.followingId"
                        + JOIN_ID + "=g.oId AND f.followerId=? AND f.followingType=0) AND ";
                params.add(actor);
            } else from += " WHERE ";
            from += "g.oId<>? AND g.oId>?";
            params.add(actor);
            params.add(after == null ? "" : after);
            if (target == null) from += " AND g.occupant=''";
            else {
                from += " AND g.oId=?";
                params.add(target);
            }
            final var available = garages.select("SELECT g.oId,g.occupant,u.userName,u.userNickname" + from
                    + " ORDER BY g.oId LIMIT 21",params.toArray());
            final JSONArray spaces = new JSONArray();
            for (var row : available.stream().limit(20).toList()) spaces.put(new JSONObject()
                    .put("id",row.getString("oId")).put("name",displayName(row)).put("free",row.getString("occupant").isEmpty()));
            final var recent = sessions.select("SELECT oId,car,endedAt,ending,paid FROM `" + sessions.getName()
                    + "` WHERE driverId=? AND endedAt>0 ORDER BY startedAt DESC LIMIT 5", actor);
            final var pet = garage == null ? null : ownPet(actor);
            result.put("availablePet", pet == null ? JSONObject.NULL : pet);
            metrics.visit(actor, now, garage != null && garage.getString("driving").isEmpty() && after == null && target == null,
                    "following".equals(scope), spaces.length() == 0);
            result.put("recent", new JSONArray(recent));
            result.put("spaces", spaces).put("next", available.size() > 20 ? available.get(19).getString("oId") : "");
            tx.commit();
            return result;
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    private JSONObject ownPet(String actor) throws RepositoryException {
        final var rows = pets.select("SELECT oId,speciesId FROM `" + pets.getName() + "` WHERE userId=?", actor);
        return rows.isEmpty() ? null : new JSONObject().put("id", rows.getFirst().getString("oId"))
                .put("speciesId", rows.getFirst().getString("speciesId"));
    }

    private static String displayName(final JSONObject user) {
        final String nickname = user.optString("userNickname");
        return nickname.isBlank() ? user.getString("userName") : nickname;
    }

    private JSONObject garage(final String id) throws RepositoryException {
        final var rows = garages.select("SELECT * FROM `" + garages.getName() + "` WHERE oId=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private JSONObject session(final String id) throws RepositoryException {
        final var rows = sessions.select("SELECT * FROM `" + sessions.getName() + "` WHERE oId=? FOR UPDATE", id);
        if (rows.isEmpty()) throw ApiV3Requests.notFound();
        return rows.getFirst();
    }

    private Object activity(final JSONObject garage, final String field, final long now) throws RepositoryException {
        if (garage == null || garage.getString(field).isEmpty()) return JSONObject.NULL;
        final JSONObject row = session(garage.getString(field));
        final String other = row.getString("driving".equals(field) ? "hostId" : "driverId");
        final var names = users.select("SELECT userName,userNickname FROM `" + users.getName() + "` WHERE oId=?", other);
        final var visit = visits.get(row.getString("oId"));
        final var passenger = passengers.get(row.getString("oId"));
        return new JSONObject().put("passengerSpecies", passenger == null ? JSONObject.NULL : passenger.getString("speciesId")).put("note", visit == null ? "" : visit.getString("note"))
                .put("mountStyle", visit == null ? "" : visit.getString("mountStyle"))
                .put("pardoned", visit != null && visit.getLong("pardonedAt") > 0).put("id", row.getString("oId")).put("car", row.getString("car"))
                .put("otherName", names.isEmpty() ? "已离开的玩家" : displayName(names.getFirst()))
                .put("startedAt", row.getLong("startedAt")).put("rate", row.getInt("hourlyRate"))
                .put("earned", payout(row.getInt("hourlyRate"), row.getLong("startedAt"), now));
    }

    public JSONObject command(final JSONObject identity, final String action, final String key, final JSONObject input) throws Exception {
        final Set<String> fields = switch (action) {
            case "starter" -> Set.of("car");
            case "buy" -> input.has("expectedPrice") ? Set.of("car","expectedPrice") : Set.of("car");
            case "park" -> {
                final Set<String> accepted = new HashSet<>(Set.of("car", "hostId"));
                if (input.has("note")) accepted.add("note");
                if (input.has("petId")) accepted.add("petId");
                yield accepted;
            }
            case "collect", "ticket", "pardon" -> Set.of("sessionId");
            default -> throw ApiV3Requests.notFound();
        };
        if (!fields.equals(input.keySet())) throw ApiV3WriteInputs.invalid();
        if (fields.contains("note") && !NOTES.contains(ApiV3WriteInputs.string(input,"note",16,false))) throw ApiV3WriteInputs.invalid();
        if (fields.contains("expectedPrice")) ApiV3WriteInputs.integer(input,"expectedPrice",-1,1,1_000_000);
        if (fields.contains("car")) car(ApiV3WriteInputs.string(input, "car", 24, false));
        for (String field : List.of("hostId", "sessionId", "petId")) {
            if (fields.contains(field) && !ApiV3WriteInputs.string(input, field, 19, false).matches("[1-9][0-9]{0,18}"))
                throw ApiV3WriteInputs.invalid();
        }
        if (identity == null || identity.optJSONObject("user") == null) throw ApiV3Requests.forbidden();
        final String actor = identity.getJSONObject("user").getString("oId");
        final String operation = "parking." + action;
        final String commandId = ApiV3TokenUtil.hash(actor + "/" + operation + "/" + key);
        final long attemptedAt = System.currentTimeMillis();
        final String parkSample = "park".equals(action) ? ApiV3TokenUtil.hash(commandId + "/" + ApiV3Json.canonical(input)) : null;
        // 原键回执也要重新确认本人权限；已提交的重试先返回，不能重复计入异常样本。
        final ApiV3MutationStore guarded = new ApiV3MutationStore() {
            public Transaction beginTransaction() { return requests.beginTransaction(); }
            public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
            public JSONObject getForUpdate(String id) throws RepositoryException {
                proofs.authorizeFirstPartyInCurrentTransaction(identity, "settings.write");
                return requests.getForUpdate(id);
            }
            public String add(JSONObject row) throws RepositoryException { return requests.add(row); }
            public void update(String id, JSONObject row, String... fields) throws RepositoryException { requests.update(id, row, fields); }
        };
        try {
            final var execution = ApiV3IdempotencyMgmtService.runAtomicWithOutcome(guarded, actor, operation, key, input,
                    () -> {
                        if (!action.equals("collect") && !action.equals("ticket")) admit(actor, commandId);
                        final var receipt = apply(actor, action, input, key);
                        if (parkSample != null) metrics.firstPark(actor, attemptedAt, parkSample, true);
                        return receipt;
                    });
            LOG.info("parking_event actor={} action={} command={} outcome={} session={} amount={}", actor, action, commandId,
                    execution.applied() ? "committed" : "replayed", execution.result().optString("sessionId"), execution.result().getInt("amount"));
            return execution.result();
        } catch (ApiV3Exception e) {
            if ("park".equals(action) && Set.of("space_unavailable", "own_space", "already_driving", "car_unowned", "passenger_changed").contains(e.code))
                metrics.rejectedPark(actor, attemptedAt, parkSample);
            if ("parking_verification_required".equals(e.code)) risk.registerParkingChallenge(identity, key, input);
            LOG.info("parking_event actor={} action={} command={} outcome={}", actor, action, commandId, e.code);
            throw e;
        }
    }

    private JSONObject apply(final String actor, final String action, final JSONObject input, final String key) throws Exception {
        final JSONObject own = garage(actor);
        final long now = System.currentTimeMillis();
        final JSONObject receipt = new JSONObject().put("action", action).put("amount", 0);
        if ("starter".equals(action)) {
            final Car model = car(input.getString("car"));
            if (!model.starter() || own != null || !offers.offer(model.id()).getBoolean("enabled")) throw conflict("starter_unavailable", "每人可免费选择一辆初始车");
            wallet.open(actor);
            metrics.milestone(actor, "starter", now);
            garages.add(new JSONObject().put("oId", actor).put("cars", new JSONArray().put(model.id()).toString())
                    .put("driving", "").put("occupant", ""));
            return receipt;
        }
        if (own == null) throw conflict("garage_missing", "请先领取初始车");
        if ("collect".equals(action) || "ticket".equals(action)) return end(actor, action, input.getString("sessionId"), now, receipt);
        if ("pardon".equals(action)) {
            final String id = input.getString("sessionId");
            final var row = session(id);
            if (!actor.equals(row.getString("hostId"))) throw ApiV3Requests.forbidden();
            if (!id.equals(own.getString("occupant")) || row.getLong("endedAt") != 0 || now >= row.getLong("startedAt") + MAX_DURATION)
                throw conflict("pardon_unavailable","本次停车已结束或已满时");
            final var visit = visits.get(id);
            if (visit == null) visits.add(new JSONObject().put("oId",id).put("note","").put("mountStyle","").put("pardonedAt",now));
            else {
                if (visit.getLong("pardonedAt") > 0) throw conflict("already_pardoned","已经放行这辆车");
                visits.update(id,visit.put("pardonedAt",now),"pardonedAt");
            }
            return receipt.put("sessionId",id);
        }
        final String modelId = input.getString("car");
        final var offer = offers.offer(modelId);
        final var model = new Car(modelId,offer.getString("name"),offer.getInt("price"),offer.getInt("rate"),offer.getBoolean("starter"));
        final JSONArray owned = new JSONArray(own.getString("cars"));
        boolean hasCar = false;
        for (int i = 0; i < owned.length(); i++) if (model.id().equals(owned.getString(i))) hasCar = true;
        if ("buy".equals(action)) {
            if (!offer.getBoolean("enabled")) throw conflict("car_off_sale","车辆已下架");
            if (input.optInt("expectedPrice",car(modelId).price()) != model.price()) throw conflict("parking_price_changed","车价已变更，请重新确认");
            if (hasCar) throw conflict("car_owned", "已拥有这辆车");
            if (!model.starter()) metrics.milestone(actor, "upgrade", now);
            wallet.change(actor, -model.price(), "parking-buy", model.id(), now);
            garages.update(actor, own.put("cars", owned.put(model.id()).toString()), "cars");
            return receipt.put("amount", -model.price());
        }
        if (!hasCar) throw conflict("car_unowned", "请先购买这辆车");
        return start(actor, input, key, own, model, now, receipt);
    }

    private JSONObject start(final String actor, final JSONObject input, final String key, final JSONObject own,
                             final Car model, final long now, final JSONObject receipt) throws Exception {
        final String host = input.getString("hostId");
        if (actor.equals(host)) throw conflict("own_space", "请选择其他玩家的车位");
        if (!own.getString("driving").isEmpty()) throw conflict("already_driving", "请先收回正在停放的车辆");
        final JSONObject space = garage(host);
        if (users.select("SELECT oId FROM `" + users.getName() + "` WHERE oId=? AND userStatus=0", host).isEmpty())
            throw conflict("space_unavailable", "车位未开放");
        if (space == null || !space.getString("occupant").isEmpty()) throw conflict("space_unavailable", "车位已被占用或未开放");
        final var recent = sessions.select("SELECT startedAt,endedAt,restartGapMs,verified FROM `" + sessions.getName()
                + "` WHERE driverId=? AND startedAt>=? ORDER BY startedAt DESC LIMIT 8", actor, now - 48 * HOUR);
        final long gap = recent.isEmpty() ? -1 : now - Math.min(recent.getFirst().getLong("endedAt"), recent.getFirst().getLong("startedAt") + MAX_DURATION);
        final boolean verify = needsVerification(actor, recent, gap, now);
        if (verify && !risk.consumeParkingGrant(actor, key, input))
            throw new ApiV3Exception(403, "parking_verification_required", "请完成验证后继续这次停车");
        final String id = Ids.genTimeMillisId();
        if (input.has("petId")) {
            final var pet = ownPet(actor);
            if (pet == null || !pet.getString("id").equals(input.getString("petId")))
                throw conflict("passenger_changed", "当前宠物已变更，请重新选择副驾");
            passengers.add(new JSONObject().put("oId",id).put("speciesId",pet.getString("speciesId")));
        }
        sessions.add(new JSONObject().put("oId", id).put("driverId", actor).put("hostId", host).put("car", model.id())
                .put("hourlyRate", model.rate()).put("startedAt", now).put("endedAt", 0L).put("ending", "")
                .put("paid", 0).put("restartGapMs", gap).put("verified", verify ? 1 : 0));
        final var mount = appearance.mounts(List.of(actor),now).get(actor);
        visits.add(new JSONObject().put("oId",id).put("note",input.optString("note"))
                .put("mountStyle",mount == null ? "" : mount.styleId()).put("pardonedAt",0L));
        garages.update(actor, own.put("driving", id), "driving");
        garages.update(host, space.put("occupant", id), "occupant");
        return receipt.put("sessionId", id);
    }

    /** 只用已经成功的停车计数；规律作息本身不足以要求验证码。 */
    private boolean needsVerification(final String actor, final List<JSONObject> recent, final long gap, final long now) throws RepositoryException {
        if (recent.size() < 4 || gap < 0 || gap > 60_000L) return false;
        int rapid = 0;
        long billed = 0;
        for (JSONObject row : recent) {
            if (row.getInt("verified") == 1 && now - row.getLong("startedAt") < 24 * HOUR) return false;
            if (row.getLong("restartGapMs") >= 0 && row.getLong("restartGapMs") <= 60_000L) rapid++;
            billed += Math.min(MAX_DURATION, row.getLong("endedAt") - row.getLong("startedAt"));
        }
        if (rapid < 3 || billed < 16 * HOUR) return false;
        if (recent.size() == 8 && now - recent.get(4).getLong("startedAt") < HOUR) return true;
        final Map<String,Set<String>> overlaps = new HashMap<>();
        for (JSONObject row : recent) {
            final long at = row.getLong("startedAt");
            final var peers = sessions.select("SELECT oId,driverId FROM `" + sessions.getName()
                    + "` WHERE startedAt BETWEEN ? AND ? AND driverId<>? LIMIT 32", at - 10_000, at + 10_000, actor);
            final Set<String> counted = new HashSet<>();
            for (JSONObject peer : peers) {
                final String other = peer.getString("driverId");
                final Set<String> starts = overlaps.computeIfAbsent(other, ignored -> new HashSet<>());
                if (!counted.contains(other) && starts.add(peer.getString("oId"))) {
                    counted.add(other);
                    if (starts.size() >= 3) return true;
                }
            }
        }
        return false;
    }

    private JSONObject end(final String actor, final String action, final String id, final long now, final JSONObject receipt) throws Exception {
        final JSONObject row = session(id);
        final String driver = row.getString("driverId"), host = row.getString("hostId");
        if (!actor.equals("collect".equals(action) ? driver : host)) throw ApiV3Requests.forbidden();
        if (row.getLong("endedAt") != 0) throw conflict("session_closed", "这次停车已结算");
        if ("ticket".equals(action) && now < row.getLong("startedAt") + MAX_DURATION) {
            final var visit = visits.get(id);
            if (visit != null && visit.getLong("pardonedAt") > 0) throw conflict("parking_pardoned","已放行，计费期满可清位");
        }
        // 贴条也只给车主入账；与其他游戏对同一车主的钱包写入使用相同的锁。
        if (!driver.equals(actor) && users.getForUpdate(driver) == null) throw ApiV3Requests.notFound();
        final JSONObject own = garage(driver), space = garage(host);
        if (own == null || space == null || !id.equals(own.getString("driving")) || !id.equals(space.getString("occupant")))
            throw new IllegalStateException("停车占用记录不一致");
        final int amount = payout(row.getInt("hourlyRate"), row.getLong("startedAt"), now);
        if ("ticket".equals(action)) metrics.ticket(driver, now);
        wallet.change(driver, amount, "parking-income", id, now);
        sessions.update(id, row.put("endedAt", now).put("ending", action).put("paid", amount), "endedAt", "ending", "paid");
        garages.update(driver, own.put("driving", ""), "driving");
        garages.update(host, space.put("occupant", ""), "occupant");
        return receipt.put("sessionId", id).put("amount", amount);
    }

    private static ApiV3Exception conflict(final String code, final String text) {
        return new ApiV3Exception(409, code, text);
    }
}
