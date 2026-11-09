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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** 仅定义登录状态转换，持久化、当前账号授权和签发由调用方在事务中完成。 */
public final class ApiV3AppLoginProtocol {
    public static final long TTL_MILLIS = 120_000L;
    public static final String QR_PREFIX = "fishpi-login:v3:";
    private ApiV3AppLoginProtocol() { }
    public enum State { WAITING, CLAIMED, APP_CONFIRMED, WEB_APPROVED, CONSUMED, DENIED }
    public enum Reason { INVALID, EXPIRED, BOUND_TO_OTHER_APP, BAD_PROOF, WRONG_STATE, ALREADY_USED, DENIED }
    public static final class Rejected extends IllegalArgumentException {
        private final Reason reason;
        private Rejected(final Reason reason) {
            super("App login request rejected: " + reason.name());
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }
    /** 持久化快照省略原始扫码句柄、APP 校验器和已签发令牌。 */
    public record Snapshot(State state, long createdAt, long expiresAt, String challenge) {
        public Snapshot {
            if (state == null || createdAt <= 0 || expiresAt <= createdAt
                    || expiresAt - createdAt != TTL_MILLIS || challenge == null
                    || (!challenge.isEmpty() && !canonical32(challenge))
                    || (state == State.WAITING && !challenge.isEmpty())
                    || (state != State.WAITING && state != State.DENIED && challenge.isEmpty()))
                throw reject(Reason.INVALID);
        }
    }
    public static Snapshot create(final long now) {
        if (now <= 0 || now > Long.MAX_VALUE - TTL_MILLIS) throw reject(Reason.INVALID);
        return new Snapshot(State.WAITING, now, now + TTL_MILLIS, "");
    }
    /** 只接受规定的扫码载荷格式，禁止内嵌 URL、账号凭据或其他登录格式。 */
    public static String qr(final String handle) {
        if (!canonical32(handle))
            throw reject(Reason.INVALID);
        return QR_PREFIX + handle;
    }
    public static String parseQr(final String payload) {
        if (payload == null || !payload.startsWith(QR_PREFIX)) throw reject(Reason.INVALID);
        final String handle = payload.substring(QR_PREFIX.length());
        if (!canonical32(handle)) throw reject(Reason.INVALID);
        return handle;
    }
    public static String challenge(final String verifier) {
        if (!canonical32(verifier)) throw reject(Reason.INVALID);
        return encode(sha256(verifier));
    }
    public static String handleHash(final String handle) {
        if (!canonical32(handle)) throw reject(Reason.INVALID);
        return encode(sha256("rhythm-app-login-v3:" + handle));
    }
    public static Snapshot claim(final Snapshot current, final String challenge, final long now) {
        active(current, now);
        if (!canonical32(challenge)) throw reject(Reason.INVALID);
        if (current.state() == State.WAITING) return move(current, State.CLAIMED, challenge);
        if (!equal(current.challenge(), challenge)) throw reject(Reason.BOUND_TO_OTHER_APP);
        // 同一 APP 重试时，保留已有确认状态和原到期时间。
        return current;
    }
    public static Snapshot confirmApp(final Snapshot current, final String verifier, final long now) {
        proof(current, verifier, now);
        if (current.state() == State.CLAIMED) return move(current, State.APP_CONFIRMED, current.challenge());
        if (current.state() == State.APP_CONFIRMED || current.state() == State.WEB_APPROVED) return current;
        throw reject(Reason.WRONG_STATE);
    }
    /** 网页确认或拒绝之前，调用方须独立校验当前第一方所有者的权限。 */
    public static Snapshot approveWeb(final Snapshot current, final long now) {
        active(current, now);
        if (current.state() == State.APP_CONFIRMED) return move(current, State.WEB_APPROVED, current.challenge());
        if (current.state() == State.WEB_APPROVED) return current;
        throw reject(Reason.WRONG_STATE);
    }
    public static Snapshot denyWeb(final Snapshot current, final long now) {
        active(current, now);
        return move(current, State.DENIED, current.challenge());
    }
    public static Snapshot denyApp(final Snapshot current, final String verifier, final long now) {
        proof(current, verifier, now);
        return move(current, State.DENIED, current.challenge());
    }
    /** 调用方须将本次状态转换与唯一一组 APP 令牌在同一事务中提交。 */
    public static Snapshot consume(final Snapshot current, final String verifier, final long now) {
        proof(current, verifier, now);
        if (current.state() != State.WEB_APPROVED) throw reject(Reason.WRONG_STATE);
        return move(current, State.CONSUMED, current.challenge());
    }
    private static void active(final Snapshot value, final long now) {
        if (value == null || now < value.createdAt()) throw reject(Reason.INVALID);
        if (now >= value.expiresAt()) throw reject(Reason.EXPIRED);
        if (value.state() == State.CONSUMED) throw reject(Reason.ALREADY_USED);
        if (value.state() == State.DENIED) throw reject(Reason.DENIED);
    }
    private static void proof(final Snapshot value, final String verifier, final long now) {
        active(value, now);
        if (value.state() == State.WAITING) throw reject(Reason.WRONG_STATE);
        if (!canonical32(verifier) || !equal(value.challenge(), challenge(verifier))) throw reject(Reason.BAD_PROOF);
    }
    private static Snapshot move(final Snapshot old, final State next, final String challenge) {
        return new Snapshot(next, old.createdAt(), old.expiresAt(), challenge);
    }
    private static boolean equal(final String left, final String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }
    public static boolean canonical32(final String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}"))
            return false;
        try {
            final byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == 32 && encode(decoded).equals(value);
        } catch (final IllegalArgumentException ignored) {
            return false;
        }
    }
    private static String encode(final byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static byte[] sha256(final String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
    private static Rejected reject(final Reason reason) {
        return new Rejected(reason);
    }
}
