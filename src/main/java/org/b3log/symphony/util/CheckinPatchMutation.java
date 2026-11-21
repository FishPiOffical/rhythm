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

import org.b3log.symphony.model.UserExt;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Pure natural-day patch transition. Callers must persist bag and user changes in one transaction. */
public final class CheckinPatchMutation {
    public static final String CARD = "patchCheckinCard";
    public static final String START = "patchStart";
    private CheckinPatchMutation() { }
    public enum Reason {
        NO_CARD(-1), LONGEST(-2), NOT_TODAY(-3), NO_RECORD(-4), INVALID_RECORD(-4);
        public final int legacyCode;
        Reason(final int code) { legacyCode = code; }
    }
    public static final class Rejected extends RuntimeException {
        public final Reason reason;
        public Rejected(final Reason reason) { super(reason.name()); this.reason = reason; }
    }
    public record Result(JSONObject bag, JSONObject changes) { }
    public static int day(final LocalDate date) {
        return Integer.parseInt(DateTimeFormatter.BASIC_ISO_DATE.format(date));
    }
    private static LocalDate date(final Object value) {
        final String text = String.valueOf(value);
        if (!text.matches("[0-9]{8}")) throw new Rejected(Reason.INVALID_RECORD);
        try {
            final LocalDate parsed = LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE);
            if (parsed.getYear() < 1000) throw new Rejected(Reason.INVALID_RECORD);
            return parsed;
        }
        catch (final DateTimeParseException e) { throw new Rejected(Reason.INVALID_RECORD); }
    }
    private static int cards(final JSONObject bag) {
        if (!bag.has(CARD)) return 0;
        final String value = String.valueOf(bag.opt(CARD));
        if (!value.matches("[0-9]{1,10}")) throw new Rejected(Reason.INVALID_RECORD);
        try { return Integer.parseInt(value); }
        catch (final NumberFormatException e) { throw new Rejected(Reason.INVALID_RECORD); }
    }
    /** Producer hint: restore the previous run only when precisely one natural day was missed. */
    public static int patchStart(final int previousStart, final int previousEnd, final LocalDate today) {
        final LocalDate start = date(previousStart), end = date(previousEnd);
        final long distance = ChronoUnit.DAYS.between(end, today);
        if (start.isAfter(end) || distance < 2) throw new Rejected(Reason.INVALID_RECORD);
        return day(distance == 2 ? start : today.minusDays(1));
    }
    public static Result apply(final JSONObject user, final JSONObject source, final LocalDate today) {
        final int cards = cards(source);
        if (cards < 1) throw new Rejected(Reason.NO_CARD);
        if (!source.has(START)) throw new Rejected(Reason.NO_RECORD);
        LocalDate after = date(source.opt(START));
        final LocalDate currentStart = date(user.opt(UserExt.USER_CURRENT_CHECKIN_STREAK_START));
        final LocalDate currentEnd = date(user.opt(UserExt.USER_CURRENT_CHECKIN_STREAK_END));
        final LocalDate longestStart = date(user.opt(UserExt.USER_LONGEST_CHECKIN_STREAK_START));
        final LocalDate longestEnd = date(user.opt(UserExt.USER_LONGEST_CHECKIN_STREAK_END));
        if (currentStart.equals(longestStart)) throw new Rejected(Reason.LONGEST);
        if (!today.equals(currentEnd)) throw new Rejected(Reason.NOT_TODAY);
        final LocalDate missing = currentStart.minusDays(1);
        if (currentStart.isAfter(currentEnd) || longestStart.isAfter(longestEnd) || longestEnd.isAfter(today)
                || after.isAfter(missing)) throw new Rejected(Reason.INVALID_RECORD);
        if (longestEnd.plusDays(1).equals(missing) && longestStart.isBefore(after)) after = longestStart;
        final int streak = Math.toIntExact(ChronoUnit.DAYS.between(after, currentEnd) + 1);
        final int longest = Math.toIntExact(ChronoUnit.DAYS.between(longestStart, longestEnd) + 1);
        final JSONObject changes = new JSONObject().put(UserExt.USER_CURRENT_CHECKIN_STREAK_START, day(after))
                .put(UserExt.USER_CURRENT_CHECKIN_STREAK, streak).put(UserExt.USER_LONGEST_CHECKIN_STREAK, Math.max(streak, longest));
        if (streak > longest) changes.put(UserExt.USER_LONGEST_CHECKIN_STREAK_START, day(after))
                .put(UserExt.USER_LONGEST_CHECKIN_STREAK_END, day(currentEnd));
        final JSONObject bag = new JSONObject(source.toString()).put(CARD, cards - 1);
        bag.remove(START);
        return new Result(bag, changes);
    }
}
