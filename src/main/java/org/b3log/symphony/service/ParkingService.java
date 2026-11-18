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
import org.b3log.symphony.util.ApiV3Exception;
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

    public JSONObject state(final JSONObject identity, final String after) throws Exception {
        if (after != null && !after.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
        final Transaction tx = requests.beginTransaction();
        try {
            final String actor = proofs.authorizeFirstPartyInCurrentTransaction(identity, "profile.read").getString("oId");
            final long now = System.currentTimeMillis();
            final Long coins = wallet.balance(actor);
            final JSONObject garage = garage(actor);
            final JSONObject result = new JSONObject().put("serverNow", now).put("catalog", catalog())
                    .put("coins", coins == null ? 0L : coins).put("owned", garage == null ? new JSONArray() : new JSONArray(garage.getString("cars")))
                    .put("driving", activity(garage, "driving", now)).put("occupant", activity(garage, "occupant", now));
            final var available = garages.select("SELECT oId FROM `" + garages.getName()
                    + "` WHERE occupant='' AND oId<>? AND oId>? ORDER BY oId LIMIT 21", actor, after == null ? "" : after);
            final JSONArray spaces = new JSONArray();
            final List<String> ids = available.stream().limit(20).map(r -> r.getString("oId")).toList();
            if (!ids.isEmpty()) {
                final var names = users.select("SELECT oId,userName,userNickname FROM `" + users.getName()
                        + "` WHERE userStatus=0 AND oId IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")", ids.toArray());
                final Map<String,String> labels = new HashMap<>();
                for (JSONObject u : names) labels.put(u.getString("oId"), displayName(u));
                for (String id : ids) if (labels.containsKey(id)) spaces.put(new JSONObject().put("id", id).put("name", labels.get(id)));
            }
            final var recent = sessions.select("SELECT oId,car,endedAt,ending,paid FROM `" + sessions.getName()
                    + "` WHERE driverId=? AND endedAt>0 ORDER BY startedAt DESC LIMIT 5", actor);
            result.put("recent", new JSONArray(recent));
            result.put("spaces", spaces).put("next", available.size() > 20 ? ids.getLast() : "");
            tx.commit();
            return result;
        } finally {
            if (tx.isActive()) tx.rollback();
        }
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
        return new JSONObject().put("id", row.getString("oId")).put("car", row.getString("car"))
                .put("otherName", names.isEmpty() ? "已离开的玩家" : displayName(names.getFirst()))
                .put("startedAt", row.getLong("startedAt")).put("rate", row.getInt("hourlyRate"))
                .put("earned", payout(row.getInt("hourlyRate"), row.getLong("startedAt"), now));
    }

    public JSONObject command(final JSONObject identity, final String action, final String key, final JSONObject input) throws Exception {
        final Set<String> fields = switch (action) {
            case "starter", "buy" -> Set.of("car");
            case "park" -> Set.of("car", "hostId");
            case "collect", "ticket" -> Set.of("sessionId");
            default -> throw ApiV3Requests.notFound();
        };
        if (!fields.equals(input.keySet())) throw ApiV3WriteInputs.invalid();
        if (fields.contains("car")) car(ApiV3WriteInputs.string(input, "car", 24, false));
        for (String field : List.of("hostId", "sessionId")) {
            if (fields.contains(field) && !ApiV3WriteInputs.string(input, field, 19, false).matches("[1-9][0-9]{0,18}"))
                throw ApiV3WriteInputs.invalid();
        }
        if (identity == null || identity.optJSONObject("user") == null) throw ApiV3Requests.forbidden();
        final String actor = identity.getJSONObject("user").getString("oId");
        final String operation = "parking." + action;
        final String commandId = ApiV3TokenUtil.hash(actor + "/" + operation + "/" + key);
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
                        return apply(actor, action, input, key);
                    });
            LOG.info("parking_event actor={} action={} command={} outcome={} session={} amount={}", actor, action, commandId,
                    execution.applied() ? "committed" : "replayed", execution.result().optString("sessionId"), execution.result().getInt("amount"));
            return execution.result();
        } catch (ApiV3Exception e) {
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
            if (!model.starter() || own != null) throw conflict("starter_unavailable", "每人可免费选择一辆初始车");
            wallet.open(actor);
            garages.add(new JSONObject().put("oId", actor).put("cars", new JSONArray().put(model.id()).toString())
                    .put("driving", "").put("occupant", ""));
            return receipt;
        }
        if (own == null) throw conflict("garage_missing", "请先领取初始车");
        if ("collect".equals(action) || "ticket".equals(action)) return end(actor, action, input.getString("sessionId"), now, receipt);
        final Car model = car(input.getString("car"));
        final JSONArray owned = new JSONArray(own.getString("cars"));
        boolean hasCar = false;
        for (int i = 0; i < owned.length(); i++) if (model.id().equals(owned.getString(i))) hasCar = true;
        if ("buy".equals(action)) {
            if (hasCar) throw conflict("car_owned", "已拥有这辆车");
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
        sessions.add(new JSONObject().put("oId", id).put("driverId", actor).put("hostId", host).put("car", model.id())
                .put("hourlyRate", model.rate()).put("startedAt", now).put("endedAt", 0L).put("ending", "")
                .put("paid", 0).put("restartGapMs", gap).put("verified", verify ? 1 : 0));
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
        // 贴条也只给车主入账；与其他游戏对同一车主的钱包写入使用相同的锁。
        if (!driver.equals(actor) && users.getForUpdate(driver) == null) throw ApiV3Requests.notFound();
        final JSONObject own = garage(driver), space = garage(host);
        if (own == null || space == null || !id.equals(own.getString("driving")) || !id.equals(space.getString("occupant")))
            throw new IllegalStateException("停车占用记录不一致");
        final int amount = payout(row.getInt("hourlyRate"), row.getLong("startedAt"), now);
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
