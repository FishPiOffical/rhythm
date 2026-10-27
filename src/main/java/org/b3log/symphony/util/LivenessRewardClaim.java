package org.b3log.symphony.util;

import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.LongSupplier;

/** Shared claim transition. The caller owns the transaction and the account-pair transfer lock. */
public final class LivenessRewardClaim {
    private LivenessRewardClaim() { }
    public record Period(String date, long start, long end) {
        public static Period at(final long now, final ZoneId zone) {
            final LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
            return new Period(today.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE),
                    today.atStartOfDay(zone).toInstant().toEpochMilli(), today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli());
        }
    }
    public interface Store {
        void lockOwner() throws Exception;
        boolean alreadyClaimed(Period period) throws Exception;
        int amount(Period period) throws Exception;
        String transfer(int amount, long now) throws Exception;
    }
    public record Result(String date, int amount, String transferId) {
        public JSONObject dto() { return new JSONObject().put("date", date).put("amount", amount).put("claimed", true); }
    }
    public static final class Unavailable extends RuntimeException { }
    public static final class DateChanged extends RuntimeException { }
    public static boolean validExpectedDate(final String value) {
        if (value == null || !value.matches("[0-9]{8}")) return false;
        try { return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).getYear() > 0; }
        catch (final java.time.DateTimeException e) { return false; }
    }
    public static Result claim(final Store store, final long now, final ZoneId zone) throws Exception {
        return claim(store, () -> now, zone);
    }
    public static Result claim(final Store store, final LongSupplier clock, final ZoneId zone) throws Exception {
        return claim(store, clock, zone, null);
    }
    public static Result claim(final Store store, final LongSupplier clock, final ZoneId zone, final String expectedDate) throws Exception {
        if (expectedDate != null && !validExpectedDate(expectedDate)) throw new IllegalArgumentException("invalid reward date");
        store.lockOwner();
        final long now = clock.getAsLong();
        final Period period = Period.at(now, zone);
        if (expectedDate != null && !expectedDate.equals(period.date())) throw new DateChanged();
        if (store.alreadyClaimed(period)) throw new Unavailable();
        final int amount = store.amount(period);
        if (amount <= 0) throw new Unavailable();
        final String transfer = store.transfer(amount, now);
        if (null == transfer || transfer.isEmpty()) throw new IllegalStateException("liveness reward transfer failed");
        return new Result(period.date(), amount, transfer);
    }
    /** Table names come only from repositories; values remain bound by the caller. */
    public static String claimedSql(final String table, final boolean lock) {
        return "SELECT oId FROM " + table + " WHERE fromId=? AND toId=? AND type=? AND time>=? AND time<?"
                + " ORDER BY time DESC,oId DESC LIMIT 1" + (lock ? " FOR UPDATE" : "");
    }
    public static String livenessSql(final String table, final boolean lock) {
        return "SELECT * FROM " + table + " WHERE livenessUserId=? AND livenessDate=? LIMIT 1" + (lock ? " FOR UPDATE" : "");
    }
}
