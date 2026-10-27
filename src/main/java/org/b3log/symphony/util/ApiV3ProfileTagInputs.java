package org.b3log.symphony.util;

import org.b3log.symphony.model.Tag;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Validate every original item before normalization; never silently drop invalid tags. */
public final class ApiV3ProfileTagInputs {
    private ApiV3ProfileTagInputs() { }
    public static List<String> tokens(JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("tags")) || !(body.opt("tags") instanceof String raw) || raw.length() > 128) throw ApiV3WriteInputs.invalid();
        if (raw.isBlank()) return List.of();
        final String compact = raw.replaceAll("\\s+", "").replace('，', ',').replace('、', ',').replace('；', ',').replace(';', ',');
        final List<String> result = new ArrayList<>();
        for (String token : compact.split(",", -1)) {
            if (token.isEmpty()) continue; // Legacy separators may have empty runs; an all-empty input still fails below.
            requireTitle(token);
            if (result.stream().noneMatch(value -> value.equalsIgnoreCase(token))) result.add(token);
        }
        if (result.isEmpty()) throw ApiV3WriteInputs.invalid(); return result;
    }
    public static void requireTitle(String value) {
        if (value == null || value.isEmpty() || value.length() > 128 || value.codePoints().anyMatch(cp -> Character.isISOControl(cp) || Character.isWhitespace(cp))
                || value.indexOf('<') >= 0 || value.indexOf('>') >= 0 || value.indexOf(',') >= 0 || value.indexOf(';') >= 0) throw ApiV3WriteInputs.invalid();
        if (!Tag.TAG_TITLE_PATTERN.matcher(value).matches() || !Tag.containsWhiteListTags(value) && value.length() > Tag.MAX_TAG_TITLE_LENGTH) throw ApiV3WriteInputs.invalid();
    }
    public static JSONObject result(List<String> titles) {
        if (titles.size() > Tag.MAX_TAG_COUNT) throw ApiV3WriteInputs.invalid();
        for (String title : titles) requireTitle(title);
        final String value = String.join(",", titles); if (value.length() > 128) throw ApiV3WriteInputs.invalid();
        return new JSONObject().put("tags", value);
    }
}
