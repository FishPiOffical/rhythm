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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.AccountContactCodeRepository;
import org.b3log.symphony.repository.AccountCredentialStateRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 验证新的联系方式、更新账号并撤销已有凭据。验证码按账号和用途查询。
 */
@Service
public class ApiV3ContactSettingsService {
    @Inject
    private UserRepository users;
    @Inject
    private AccountContactCodeRepository codes;
    @Inject
    private ApiV3SecurityProofService proofs;
    @Inject
    private AccountCredentialMgmtService credentials;
    @Inject
    private VerifycodeMgmtService sms;
    private static final SecureRandom RANDOM = new SecureRandom();

    protected long now() {
        return System.currentTimeMillis();
    }

    protected String newCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    protected boolean captcha(String proof) {
        // 极验校验保护入口；未经阿达明确允许，不得修改、移动、删除或绕过此调用。
        return AnonymousCaptchaShield.verifyProof(proof);
    }

    protected boolean allowed(String ip, String actor, String target, boolean phone) {
        return !phone || AccountSmsQuota.access(ip, actor, target);
    }

    protected boolean send(String target, String code, boolean phone) {
        return phone ? sms.sendAccountVerifyCodeSMS(target, code) : Mails.sendAccountBindingCode(target, code);
    }

    public JSONObject request(JSONObject identity, ApiV3ContactInputs.Input input, boolean phone, String ip) throws Exception {
        input = validated(input, phone, true);
        proofs.checkAttempts(identity, ip);
        rootRequired();
        final String actor;
        final long epoch;
        final TargetSnapshot before;
        var tx = begin();
        try {
            final JSONObject owner = proofs.requireInCurrentTransaction(identity, input.password(), input.totp());
            actor = owner.getString("oId");
            epoch = version(owner);
            before = requireNewTarget(actor, input.target(), phone);
            tx.commit();
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
        if (!allowed(ip, before.userName(), input.target(), phone))
            throw new ApiV3Exception(429, "rate_limited", "验证码发送过于频繁，请稍后重试");
        if (!captcha(input.proof())) throw new ApiV3Exception(422, "invalid_captcha", "请重新完成验证");
        final String code = newCode();
        if (code == null || !code.matches("[0-9]{6}"))
            throw unavailable();
        // 在数据库事务外联系发送服务，发送失败时保留上一次成功发送的验证码。
        final boolean accepted;
        try {
            accepted = send(input.target(), code, phone);
        } catch (Exception error) {
            throw deliveryUnavailable();
        }
        if (!accepted)
            throw deliveryUnavailable();
        tx = begin();
        try {
            // 发送期间账号状态可能变化，持锁复核凭据版本和原联系方式后才保存新验证码。
            final JSONObject owner = proofs.requireInCurrentTransaction(identity, input.password(), input.totp());
            if (!actor.equals(owner.getString("oId")) || version(owner) != epoch) throw invalidProof();
            if (!before.previous().equals(requireNewTarget(actor, input.target(), phone).previous()))
                throw invalidProof();
            final long time = clock(), expiry = Math.addExact(time, 600_000L);
            final String purpose = phone ? "phone" : "email", id = rowId(actor, purpose);
            final var previous = codes.select("SELECT oId FROM `" + codes.getName() + "` WHERE userId=? AND purpose=? FOR UPDATE", actor, purpose);
            if (previous.size() > 1 || !previous.isEmpty() && !id.equals(previous.getFirst().optString("oId")))
                throw unavailable();
            ApiV3TransactionStatements.execute("DELETE FROM `" + codes.getName() + "` WHERE userId=? AND purpose=?", actor, purpose);
            final int inserted = ApiV3TransactionStatements.execute("INSERT INTO `" + codes.getName() + "` (oId,userId,purpose,clientId,credentialEpoch,target,codeHash,previousContactHash,expiresAt,createdAt) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    id, actor, purpose, ApiV3TokenMgmtService.WEBSITE_CLIENT, epoch, input.target(), codeHash(actor, purpose, input.target(), epoch, code), previousHash(actor, purpose, before.previous(), epoch), expiry, time);
            if (inserted != 1) throw unavailable();
            tx.commit();
            return new JSONObject().put("accepted", true);
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }

    public JSONObject change(JSONObject identity, ApiV3ContactInputs.Input input, boolean phone, String ip) throws Exception {
        input = validated(input, phone, false);
        proofs.checkAttempts(identity, ip);
        rootRequired();
        String actor = null;
        final var tx = begin();
        try {
            final JSONObject owner = proofs.requireInCurrentTransaction(identity, input.password(), input.totp());
            actor = owner.getString("oId");
            final TargetSnapshot before = requireNewTarget(actor, input.target(), phone);
            final long epoch = version(owner), time = clock();
            final String purpose = phone ? "phone" : "email";
            final var found = codes.select("SELECT oId,userId,purpose,clientId,credentialEpoch,target,codeHash,previousContactHash,expiresAt,createdAt FROM `" + codes.getName() + "` WHERE userId=? AND purpose=? FOR UPDATE", actor, purpose);
            if (found.size() > 1)
                throw unavailable();
            if (found.isEmpty())
                throw invalidProof();
            final JSONObject row = found.getFirst();
            for (final String field : new String[]{"oId", "userId", "purpose", "clientId", "target", "codeHash", "previousContactHash"})
                if (!(row.opt(field) instanceof String)) throw unavailable();
            if (!row.getString("codeHash").matches("[0-9a-f]{64}") || !row.getString("previousContactHash").matches("[0-9a-f]{64}"))
                throw unavailable();
            final long created = number(row, "createdAt"), expires = number(row, "expiresAt");
            if (created <= 0 || created > time || expires - created != 600_000L) throw invalidProof();
            if (!rowId(actor, purpose).equals(row.optString("oId")) || !actor.equals(row.optString("userId")) || !purpose.equals(row.optString("purpose"))
                    || !ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(row.optString("clientId")) || !input.target().equals(row.optString("target"))
                    || !previousHash(actor, purpose, before.previous(), epoch).equals(row.optString("previousContactHash")) || number(row, "credentialEpoch") != epoch || number(row, "expiresAt") <= time
                    || !MessageDigest.isEqual(codeHash(actor, purpose, input.target(), epoch, input.proof()).getBytes(StandardCharsets.US_ASCII), row.getString("codeHash").getBytes(StandardCharsets.US_ASCII)))
                throw invalidProof();
            // 联系方式写入、验证码消费和旧凭据撤销在同一个事务中提交。
            final String field = phone ? "userPhone" : "userEmail";
            if (ApiV3TransactionStatements.execute("UPDATE `" + users.getName() + "` SET " + field + "=? WHERE oId=?", input.target(), actor) != 1)
                throw unavailable();
            if (ApiV3TransactionStatements.execute("DELETE FROM `" + codes.getName() + "` WHERE userId=? AND purpose=?", actor, purpose) != 1)
                throw unavailable();
            credentials.revokeInCurrentTransaction(actor);
            tx.commit();
            return new JSONObject().put("changed", true).put("reauthenticationRequired", true);
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            try {
                if (tx.isActive())
                    tx.rollback();
            } finally {
                if (actor != null && !codes.hasTransactionBegun())
                    try {
                        users.evictSettingsCaches(actor);
                    } catch (RuntimeException ignored) {
                    }
            }
        }
    }

    private record TargetSnapshot(String previous, String userName) {
    }

    private TargetSnapshot requireNewTarget(String actor, String target, boolean phone) throws Exception {
        ApiV3ContactInputs.target(target, phone);
        final String field = phone ? "userPhone" : "userEmail";
        final var current = users.select("SELECT oId,userName," + field + " FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (current.size() != 1 || !actor.equals(current.getFirst().optString("oId"))) throw ApiV3Requests.forbidden();
        if (!(current.getFirst().opt(field) instanceof String previous)) throw unavailable();
        if (phone ? target.equals(previous) : target.equalsIgnoreCase(previous))
            throw new ApiV3Exception(422, "unchanged_contact", "请提供新的联系方式");
        if (!users.select("SELECT oId FROM `" + users.getName() + "` WHERE " + field + "=? ORDER BY oId LIMIT 2 FOR UPDATE", target).isEmpty())
            throw new ApiV3Exception(422, "contact_unavailable", "该联系方式不可用");
        if (!(current.getFirst().opt("userName") instanceof String userName) || userName.isEmpty() || userName.length() > 64)
            throw unavailable();
        return new TargetSnapshot(previous, userName);
    }

    private Transaction begin() {
        try {
            return ApiV3Transactions.strict(codes.beginTransaction());
        } catch (Exception error) {
            throw unavailable();
        }
    }

    private String previousHash(String actor, String purpose, String previous, long epoch) throws Exception {
        return codeHash(actor, "previous:" + purpose, previous, epoch, "");
    }

    private static ApiV3Exception deliveryUnavailable() {
        return new ApiV3Exception(503, "delivery_unavailable", "验证码发送暂不可用");
    }

    private void rootRequired() {
        if (users.hasTransactionBegun() || codes.hasTransactionBegun())
            throw new IllegalStateException("contact root required");
    }

    private long clock() {
        final long time = now();
        if (time <= 0)
            throw unavailable();
        return time;
    }

    private static long version(JSONObject owner) {
        return number(owner, "credentialEpoch");
    }

    private static long number(JSONObject row, String key) {
        return AccountCredentialStateRepository.epoch(row.opt(key));
    }

    private static ApiV3ContactInputs.Input validated(ApiV3ContactInputs.Input input, boolean phone, boolean sending) {
        if (input == null) throw ApiV3WriteInputs.invalid();
        final JSONObject body = new JSONObject().put("target", input.target()).put("currentPassword", input.password()).put(sending ? "captcha" : "code", input.proof());
        if (input.totp() != null && !input.totp().isEmpty()) body.put("currentTotp", input.totp());
        return ApiV3ContactInputs.parse(body, phone, sending);
    }

    private static String rowId(String actor, String purpose) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((actor + "\0" + purpose).getBytes(StandardCharsets.UTF_8)));
    }

    protected String codeHash(String actor, String purpose, String target, long epoch, String code) throws Exception {
        final String secret = Symphonys.COOKIE_SECRET;
        if (secret == null || secret.isEmpty())
            throw unavailable();
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(("contact\0" + actor + "\0" + purpose + "\0" + target + "\0" + epoch + "\0" + code).getBytes(StandardCharsets.UTF_8)));
    }

    private static ApiV3Exception invalidProof() {
        return new ApiV3Exception(422, "invalid_verification", "验证码无效或已失效");
    }

    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "联系方式设置暂不可用");
    }
}
