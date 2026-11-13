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
import org.b3log.symphony.model.ApiV3Request;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.PetRiskStateRepository;
import org.b3log.symphony.repository.PetRiskAuditRepository;
import org.b3log.symphony.repository.PetRiskProofRepository;
import org.b3log.symphony.util.AccountCaptchaProof;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Json;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;
import java.util.UUID;

/** 宠物账号级保护：拒绝和挑战独立落库，不封禁 IP，不改变养成奖励。 */
@Service
public class ApiV3PetRiskService {
    private static final long MINUTE = 60_000L;
    private static final long COMMAND_TTL = 86_400_000L;
    private static final long CHALLENGE_TTL = 5 * MINUTE;
    private static final long GRANT_TTL = 15 * MINUTE;
    private static final int COMMAND_LIMIT = 128;
    private static final Set<String> OPERATIONS = Set.of("adoption", "readoption", "story-claim", "name", "action",
            "supplies", "purchase", "craft", "activity-start", "activity-claim");
    private static final Set<String> BUSINESS_DENIALS = Set.of("care_cooldown", "already_full", "not_enough_energy",
            "not_enough_coins", "not_enough_hunger", "not_enough_water", "not_enough_items", "not_enough_food",
            "income_limit", "activity_running", "activity_claimed", "supply_claimed", "pet_resting", "pet_busy", "story_claimed", "collection_owned");
    @Inject private PetRiskStateRepository states;
    @Inject private PetRiskAuditRepository audits;
    @Inject private PetRiskProofRepository proofUses;
    @Inject private ApiV3RequestRepository requests;
    @Inject private ApiV3SecurityProofService proofs;
    @Inject private AccountCaptchaService options;
    @Inject private ApiV3PetCaptchaService captcha;

    protected long now() { return System.currentTimeMillis(); }
    protected int minuteLimit() { return 30; }
    protected int burstLimit() { return 12; }
    protected int denialLimit() { return 8; }
    protected int transportLimit() { return 240; }

    public record Admission(JSONObject receipt) { }
    private record Outcome(JSONObject value, ApiV3Exception failure) { }
    @FunctionalInterface private interface Locked { Outcome apply(String actor, JSONObject state, long clock) throws Exception; }

    /** 成功回执在业务风险门槛之前核对；原操作和摘要必须完全一致，不能相信客户端恢复标志。 */
    public Admission before(final JSONObject identity, final String operation, final String key, final JSONObject input) {
        validateCommand(operation, key);
        final String hash = bodyHash(input);
        final JSONObject response = locked(identity, (actor, state, clock) -> {
            final Outcome transport = transport(state, clock, "transport", transportLimit());
            if (transport != null) return transport;
            final String id = commandId(actor, operation, key);
            final JSONObject receipt = requests.getForUpdate(id);
            if (receipt != null) {
                if (!hash.equals(receipt.optString(ApiV3Request.REQUEST_HASH))) return conflict(actor, state, operation, id, hash, clock);
                return success(new JSONObject().put("receipt", new JSONObject(receipt.getString(ApiV3Request.RESPONSE_JSON))));
            }
            JSONObject command = command(state, id);
            if (command != null && !hash.equals(command.getString("hash")))
                return conflict(actor, state, operation, id, hash, clock);
            if (command == null) {
                command = new JSONObject().put("id", id).put("operation", operation).put("key", key).put("hash", hash)
                        .put("seenAt", clock).put("admitted", false).put("failed", false).put("grantUntil", 0L);
                appendCommand(state, command);
                final int burst = incrementWindow(state, "burst", clock, 10_000);
                final int minute = incrementWindow(state, "minute", clock, MINUTE);
                if (burst > burstLimit() || minute > minuteLimit()) {
                    state.put("retryAt", Math.max(state.optLong("retryAt"), clock + MINUTE));
                    audit(actor, operation, id, hash, "rate_limited", clock);
                }
            }
            if (command.optLong("grantUntil") > clock) {
                command.put("admitted", true);
                return success(new JSONObject());
            }
            if (state.optLong("retryAt") > clock) return rateLimited();
            if (state.optBoolean("verificationRequired")) {
                if (!command.optBoolean("challenged")) {
                    command.put("challenged", true);
                    audit(actor, operation, id, hash, "verification_required", clock);
                }
                return failure(403, "pet_verification_required", "请完成验证后继续原操作");
            }
            command.put("admitted", true);
            return success(new JSONObject());
        });
        return new Admission(response.optJSONObject("receipt"));
    }

    /** 只对不同原命令的明确业务拒绝计数；网络、数据库、验证码故障与同键重试不加分。 */
    public void denied(final JSONObject identity, final String operation, final String key, final JSONObject input, final ApiV3Exception error) {
        if (!BUSINESS_DENIALS.contains(error.code)) return;
        locked(identity, (actor, state, clock) -> {
            final String id = commandId(actor, operation, key);
            final JSONObject command = command(state, id);
            if (command == null || command.optBoolean("failed") || !bodyHash(input).equals(command.optString("hash"))) return success(new JSONObject());
            command.put("failed", true);
            final JSONArray denied = recent(state.optJSONArray("denied"), clock - 5 * MINUTE);
            denied.put(clock);
            state.put("denied", denied);
            audit(actor, operation, id, command.getString("hash"), "business_" + error.code, clock);
            if (denied.length() >= denialLimit()) state.put("verificationRequired", true);
            return success(new JSONObject());
        });
    }

    public JSONObject state(final JSONObject identity) {
        return locked(identity, (actor, state, clock) -> {
            final Outcome quota = transport(state, clock, "control", 60);
            return quota != null ? quota : success(new JSONObject().put("serverNow", clock)
                    .put("retryAt", state.optLong("retryAt")).put("verificationRequired", state.optBoolean("verificationRequired")));
        });
    }

    public JSONObject challenge(final JSONObject identity, final String operation, final String key) {
        validateCommand(operation, key);
        final JSONObject widget = options.options();
        return locked(identity, (actor, state, clock) -> {
            final Outcome quota = transport(state, clock, "control", 60);
            if (quota != null) return quota;
            final JSONObject command = command(state, commandId(actor, operation, key));
            if (command == null) return failure(409, "pet_challenge_unavailable", "当前操作不需要验证");
            if (state.optLong("retryAt") > clock && command.optLong("grantUntil") <= clock) return rateLimited();
            // 首次可能先被冷却拦下。冷却结束后仍只接受已经登记并冻结了摘要的原命令。
            if (!command.optBoolean("challenged")) {
                if (!state.optBoolean("verificationRequired")) return failure(409, "pet_challenge_unavailable", "当前操作不需要验证");
                command.put("challenged", true);
                audit(actor, operation, command.getString("id"), command.getString("hash"), "verification_required", clock);
            }
            JSONObject challenge = command.optJSONObject("challenge");
            if (challenge == null || challenge.optLong("expiresAt") <= clock
                    || (challenge.optInt("attempts") >= 5 && challenge.optLong("verifyingUntil") <= clock)) {
                challenge = new JSONObject().put("id", UUID.randomUUID().toString()).put("expiresAt", clock + CHALLENGE_TTL)
                        .put("attempts", 0).put("verifyingUntil", 0L);
                command.put("challenge", challenge);
                audit(actor, operation, command.getString("id"), command.getString("hash"), "challenge_issued", clock);
            }
            return success(binding(command).put("serverNow", clock).put("verified", command.optLong("grantUntil") > clock)
                    .put("challengeId", challenge.getString("id")).put("expiresAt", command.optLong("grantUntil") > clock
                            ? command.getLong("grantUntil") : challenge.getLong("expiresAt"))
                    .put("provider", widget.getString("provider")).put("captchaId", widget.getString("captchaId")));
        });
    }

    public JSONObject verify(final JSONObject identity, final String challengeId, final String rawProof) {
        if (challengeId == null || !challengeId.matches("[a-f0-9-]{36}")) throw ApiV3WriteInputs.invalid();
        final String proof = AccountCaptchaProof.validate(rawProof);
        final long proofTime;
        try {
            proofTime = Math.multiplyExact(Long.parseLong(new JSONObject(proof).getString("gen_time")), 1000L);
        } catch (Exception error) {
            throw new ApiV3Exception(422, "invalid_captcha", "请重新完成验证");
        }
        final String lotHash = digest("pet-captcha-lot\0" + new JSONObject(proof).getString("lot_number"));
        final JSONObject prepared = locked(identity, (actor, state, clock) -> {
            final Outcome quota = transport(state, clock, "verification", 10);
            if (quota != null) return quota;
            final JSONObject command = challengedCommand(state, challengeId);
            if (command == null) return expired();
            if (command.optLong("grantUntil") > clock) return success(verified(command, clock));
            if (proofTime < clock - 10 * MINUTE || proofTime > clock + MINUTE)
                return failure(422, "invalid_captcha", "验证证明已过期，请重新验证");
            final JSONObject challenge = command.getJSONObject("challenge");
            if (challenge.getLong("expiresAt") <= clock) return expired();
            if (challenge.optLong("verifyingUntil") > clock) return rateLimited();
            if (challenge.optInt("attempts") >= 5) return failure(429, "pet_rate_limited", "验证尝试过于频繁，请稍后重试");
            // 先持久化批次占用再访问供应商；相同批次不可用于另一个挑战或另一个账号。
            final var used = proofUses.select("SELECT * FROM `" + proofUses.getName() + "` WHERE oId=? FOR UPDATE", lotHash);
            if (!used.isEmpty()) return failure(422, "invalid_captcha", "请重新完成验证");
            proofUses.add(new JSONObject().put("oId", lotHash).put("userId", actor).put("challengeId", challengeId)
                    .put("expiresAt", clock + COMMAND_TTL));
            challenge.put("attempts", challenge.optInt("attempts") + 1).put("verifyingUntil", clock + 15_000);
            return success(new JSONObject().put("prepared", true));
        });
        if (!prepared.optBoolean("prepared")) return prepared;
        final boolean valid;
        try {
            valid = captcha.verify(proof);
        } catch (ApiV3Exception error) {
            finishVerification(identity, challengeId, null);
            throw error;
        }
        return finishVerification(identity, challengeId, valid);
    }

    private JSONObject finishVerification(final JSONObject identity, final String challengeId, final Boolean valid) {
        return locked(identity, (actor, state, clock) -> {
            final JSONObject command = challengedCommand(state, challengeId);
            if (command == null || command.getJSONObject("challenge").getLong("expiresAt") <= clock) return expired();
            final JSONObject challenge = command.getJSONObject("challenge");
            challenge.put("verifyingUntil", 0L);
            if (valid == null) {
                // 供应商失败不消耗失败次数，不修改风险结论；用户可以重新获取证明。
                challenge.put("attempts", Math.max(0, challenge.optInt("attempts") - 1));
                audit(actor, command.getString("operation"), command.getString("id"), command.getString("hash"), "verification_unavailable", clock);
                return success(new JSONObject());
            }
            if (!valid) {
                audit(actor, command.getString("operation"), command.getString("id"), command.getString("hash"), "verification_failed", clock);
                return failure(422, "invalid_captcha", "请重新完成验证");
            }
            command.put("grantUntil", clock + GRANT_TTL);
            // 验证只授权这个被冻结的命令；其他新操作仍需通过账号风险检查。
            audit(actor, command.getString("operation"), command.getString("id"), command.getString("hash"), "verification_passed", clock);
            return success(verified(command, clock));
        });
    }

    /** 在新业务提交的同一用户行锁内复核授权；幂等回放不会执行此回调。 */
    public void authorizeMutation(final String actor, final String operation, final String key, final JSONObject input) throws RepositoryException {
        final var rows = states.select("SELECT * FROM `" + states.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.isEmpty()) throw new ApiV3Exception(503, "temporarily_unavailable", "宠物操作保护暂不可用");
        final JSONObject row = rows.getFirst(), state = new JSONObject(row.getString("stateJson"));
        final JSONObject command = command(state, commandId(actor, operation, key));
        if (command == null || !bodyHash(input).equals(command.optString("hash")) || !command.optBoolean("admitted"))
            throw new ApiV3Exception(409, "pet_admission_expired", "操作已过期，请重新继续原操作");
        final boolean granted = command.optLong("grantUntil") > now();
        if (!granted && state.optLong("retryAt") > now()) throw rateLimited().failure;
        if (!granted && state.optBoolean("verificationRequired"))
            throw new ApiV3Exception(403, "pet_verification_required", "请完成验证后继续原操作");
        if (granted) {
            // 只有原命令实际提交才恢复账号正常状态；业务失败时这次修改随事务回滚。
            command.put("grantUntil", 0L);
            state.put("verificationRequired", false).put("denied", new JSONArray());
            row.put("stateJson", state.toString()).put("updatedAt", now());
            states.update(actor, row, "stateJson", "updatedAt");
        }
    }

    private JSONObject locked(final JSONObject identity, final Locked operation) {
        Transaction transaction = null;
        try {
            if (states.hasTransactionBegun()) throw new IllegalStateException("pet risk root transaction required");
            transaction = ApiV3Transactions.strict(states.beginTransaction());
            // 与宠物写事务使用同一真实用户锁顺序，跨进程共享额度；第一方权限在每次事务内重新验证。
            final String actor = proofs.authorizeFirstPartyInCurrentTransaction(identity, "settings.write").getString("oId");
            final long clock = now();
            final var rows = states.select("SELECT * FROM `" + states.getName() + "` WHERE oId=? FOR UPDATE", actor);
            final JSONObject row = rows.isEmpty() ? new JSONObject().put("oId", actor).put("stateJson", "{}") : rows.getFirst();
            final JSONObject state = new JSONObject(row.getString("stateJson"));
            pruneCommands(state, clock);
            final Outcome outcome = operation.apply(actor, state, clock);
            row.put("stateJson", state.toString()).put("updatedAt", clock);
            if (rows.isEmpty()) states.add(row);
            else states.update(actor, row, "stateJson", "updatedAt");
            transaction.commit();
            if (outcome.failure != null) throw outcome.failure;
            return outcome.value;
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw new ApiV3Exception(503, "temporarily_unavailable", "宠物操作保护暂不可用，请稍后重试");
        } finally {
            if (transaction != null && transaction.isActive()) transaction.rollback();
        }
    }

    private Outcome conflict(final String actor, final JSONObject state, final String operation,
                             final String id, final String hash, final long clock) throws RepositoryException {
        final String fingerprint = digest(id + "\0" + hash);
        final JSONArray conflicts = state.optJSONArray("conflicts") == null ? new JSONArray() : state.getJSONArray("conflicts");
        boolean seen = false;
        for (int index = 0; index < conflicts.length(); index++) if (fingerprint.equals(conflicts.getString(index))) seen = true;
        if (!seen) {
            if (conflicts.length() >= 32) conflicts.remove(0);
            conflicts.put(fingerprint);
            state.put("conflicts", conflicts);
            audit(actor, operation, id, hash, "idempotency_conflict", clock);
        }
        return failure(409, "idempotency_conflict", "相同幂等键不能用于不同请求内容");
    }

    private void audit(final String actor, final String operation, final String command, final String hash,
                       final String reason, final long clock) throws RepositoryException {
        audits.add(new JSONObject().put("oId", UUID.randomUUID().toString()).put("userId", actor).put("operation", operation)
                .put("commandHash", command).put("bodyHash", hash).put("reason", reason).put("occurredAt", clock));
        // 按账号最多保留最近二百条保护事件；不让换键请求无限放大审计表。
        final var old = audits.select("SELECT oId FROM `" + audits.getName() + "` WHERE userId=? ORDER BY occurredAt DESC,oId DESC LIMIT 200 OFFSET 200", actor);
        for (JSONObject row : old) audits.remove(row.getString("oId"));
        final var expired = proofUses.select("SELECT oId FROM `" + proofUses.getName() + "` WHERE userId=? AND expiresAt<? LIMIT 100", actor, clock);
        for (JSONObject row : expired) proofUses.remove(row.getString("oId"));
    }

    private Outcome transport(final JSONObject state, final long clock, final String prefix, final int limit) {
        return incrementWindow(state, prefix, clock, MINUTE) > limit ? rateLimited() : null;
    }

    private static int incrementWindow(final JSONObject state, final String prefix, final long clock, final long duration) {
        final long started = state.optLong(prefix + "Started");
        if (clock >= started + duration) state.put(prefix + "Started", clock).put(prefix + "Count", 0);
        final int count = Math.min(100_000, state.optInt(prefix + "Count") + 1);
        state.put(prefix + "Count", count);
        return count;
    }

    private static void pruneCommands(final JSONObject state, final long clock) {
        final JSONArray commands = state.optJSONArray("commands"), retained = new JSONArray();
        if (commands != null) for (int index = 0; index < commands.length(); index++) {
            final JSONObject command = commands.getJSONObject(index);
            if (command.getLong("seenAt") + COMMAND_TTL > clock) retained.put(command);
        }
        state.put("commands", retained).put("denied", recent(state.optJSONArray("denied"), clock - 5 * MINUTE));
    }

    private static JSONArray recent(final JSONArray source, final long after) {
        final JSONArray result = new JSONArray();
        if (source != null) for (int index = 0; index < source.length(); index++) if (source.getLong(index) > after) result.put(source.getLong(index));
        return result;
    }

    private static void appendCommand(final JSONObject state, final JSONObject value) {
        final JSONArray commands = state.getJSONArray("commands");
        if (commands.length() >= COMMAND_LIMIT) commands.remove(0);
        commands.put(value);
    }

    private static JSONObject command(final JSONObject state, final String id) {
        final JSONArray commands = state.getJSONArray("commands");
        for (int index = 0; index < commands.length(); index++) if (id.equals(commands.getJSONObject(index).getString("id"))) return commands.getJSONObject(index);
        return null;
    }

    private static JSONObject challengedCommand(final JSONObject state, final String id) {
        final JSONArray commands = state.getJSONArray("commands");
        for (int index = 0; index < commands.length(); index++) {
            final JSONObject command = commands.getJSONObject(index), challenge = command.optJSONObject("challenge");
            if (challenge != null && id.equals(challenge.getString("id"))) return command;
        }
        return null;
    }

    private static JSONObject binding(final JSONObject command) {
        return new JSONObject().put("operation", command.getString("operation")).put("idempotencyKey", command.getString("key"));
    }
    private static JSONObject verified(final JSONObject command, final long clock) {
        return binding(command).put("serverNow", clock).put("verified", true).put("expiresAt", command.getLong("grantUntil"));
    }
    private static String digest(final String value) {
        try {
            return ApiV3TokenUtil.hash(value);
        } catch (Exception error) {
            throw new ApiV3Exception(503, "temporarily_unavailable", "宠物操作保护暂不可用");
        }
    }
    private static String commandId(final String actor, final String operation, final String key) {
        return digest("request-key\0" + actor + "\0pet." + operation + "\0" + key);
    }
    private static String bodyHash(final JSONObject input) {
        return digest("request-body\0" + ApiV3Json.canonical(input));
    }
    private static void validateCommand(final String operation, final String key) {
        if (!OPERATIONS.contains(operation) || key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) throw ApiV3WriteInputs.invalid();
    }
    private static Outcome success(final JSONObject value) { return new Outcome(value, null); }
    private static Outcome failure(final int status, final String code, final String message) {
        return new Outcome(null, new ApiV3Exception(status, code, message));
    }
    private static Outcome rateLimited() { return failure(429, "pet_rate_limited", "操作过于频繁，请稍后继续原操作"); }
    private static Outcome expired() { return failure(409, "pet_challenge_expired", "验证已过期，请重新验证"); }
}
