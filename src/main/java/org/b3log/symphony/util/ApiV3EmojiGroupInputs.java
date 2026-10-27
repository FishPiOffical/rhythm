package org.b3log.symphony.util;

import org.json.JSONObject;
import java.util.Set;
import java.util.regex.Pattern;

/** Typed group settings; names are plain text, never markup. */
public final class ApiV3EmojiGroupInputs {
    private static final Pattern MARKUP = Pattern.compile("<\\s*(?:/?\\s*[a-zA-Z]|!|\\?)");
    private ApiV3EmojiGroupInputs() { }
    public static JSONObject normalize(final JSONObject body) {
        if (body == null || !Set.of("name", "sort").containsAll(body.keySet())
                || !(body.opt("name") instanceof String raw) || raw.length() > 4096) throw ApiV3WriteInputs.invalid();
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029') throw ApiV3WriteInputs.invalid();
            if (Character.isHighSurrogate(c)) {
                if (++i >= raw.length() || !Character.isLowSurrogate(raw.charAt(i))) throw ApiV3WriteInputs.invalid();
            } else if (Character.isLowSurrogate(c)) throw ApiV3WriteInputs.invalid();
        }
        final String name = raw.strip();
        if (name.isEmpty() || name.length() > 64 || MARKUP.matcher(name).find()) throw ApiV3WriteInputs.invalid();
        return new JSONObject().put("name", name).put("sort", ApiV3WriteInputs.integer(body, "sort", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }
}
