package org.b3log.symphony.util;

import org.json.JSONObject;
import java.util.Locale;
import java.util.Set;

/** The legacy direction enum is case-insensitive, with no whitespace trimming. */
public final class ApiV3MedalOrderInputs {
    private ApiV3MedalOrderInputs() { }
    public static String direction(final JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("direction")) || !(body.opt("direction") instanceof String value)) throw ApiV3WriteInputs.invalid();
        return direction(value);
    }
    public static String direction(final String value) {
        if (value == null || value.length() > 4) throw ApiV3WriteInputs.invalid();
        final String normalized = value.toLowerCase(Locale.ROOT);
        if (!Set.of("up", "down").contains(normalized)) throw ApiV3WriteInputs.invalid();
        return normalized;
    }
    public static int order(final Object value) {
        final long number = ApiV3MedalDisplay.expiry(value);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw ApiV3MedalDisplay.inconsistent();
        return (int) number;
    }
}
