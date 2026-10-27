package org.b3log.symphony.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Pure transition rules. Persistence, current-account authorization and issuance belong to a transaction owner. */
public final class ApiV3AppLoginProtocol {
    public static final long TTL_MILLIS = 120_000L;
    public static final String QR_PREFIX = "fishpi-login:v3:";
    private ApiV3AppLoginProtocol() { }
    public enum State { WAITING, CLAIMED, APP_CONFIRMED, WEB_APPROVED, CONSUMED, DENIED }
    public enum Reason { INVALID, EXPIRED, BOUND_TO_OTHER_APP, BAD_PROOF, WRONG_STATE, ALREADY_USED, DENIED }
    public static final class Rejected extends IllegalArgumentException {
        private final Reason reason;
        private Rejected(final Reason reason) { super("App login request rejected: " + reason.name()); this.reason = reason; }
        public Reason reason() { return reason; }
    }
    /** No raw scan handle, APP verifier or issued token is part of a persisted snapshot. */
    public record Snapshot(State state, long createdAt, long expiresAt, String challenge) {
        public Snapshot {
            if (state == null || createdAt <= 0 || expiresAt <= createdAt
                    || expiresAt - createdAt != TTL_MILLIS || challenge == null
                    || (!challenge.isEmpty() && !canonical32(challenge))
                    || (state == State.WAITING && !challenge.isEmpty())
                    || (state != State.WAITING && state != State.DENIED && challenge.isEmpty())) throw reject(Reason.INVALID);
        }
    }
    public static Snapshot create(final long now) {
        if (now <= 0 || now > Long.MAX_VALUE - TTL_MILLIS) throw reject(Reason.INVALID);
        return new Snapshot(State.WAITING, now, now + TTL_MILLIS, "");
    }
    /** Exact opaque payload; no embedded URL, account credentials or alternate legacy login syntax. */
    public static String qr(final String handle) { if (!canonical32(handle)) throw reject(Reason.INVALID); return QR_PREFIX + handle; }
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
        return current; // Same APP retries do not reset either confirmation or the original expiry.
    }
    public static Snapshot confirmApp(final Snapshot current, final String verifier, final long now) {
        proof(current, verifier, now);
        if (current.state() == State.CLAIMED) return move(current, State.APP_CONFIRMED, current.challenge());
        if (current.state() == State.APP_CONFIRMED || current.state() == State.WEB_APPROVED) return current;
        throw reject(Reason.WRONG_STATE);
    }
    /** Caller must independently authorize the current first-party owner before either web decision. */
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
    /** Caller atomically commits this transition together with exactly one APP token family. */
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
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) return false;
        try { final byte[] decoded = Base64.getUrlDecoder().decode(value); return decoded.length == 32 && encode(decoded).equals(value); }
        catch (final IllegalArgumentException ignored) { return false; }
    }
    private static String encode(final byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static byte[] sha256(final String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)); }
        catch (final NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static Rejected reject(final Reason reason) { return new Rejected(reason); }
}
