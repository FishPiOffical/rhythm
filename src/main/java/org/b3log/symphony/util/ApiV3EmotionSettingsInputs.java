package org.b3log.symphony.util;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import java.util.Set;

/** Ordered aliases from the existing public picker; no template sentinel or arbitrary emoji names. */
public final class ApiV3EmotionSettingsInputs {
    private ApiV3EmotionSettingsInputs() { }
    public static Set<String> allowed() {
        final Set<String> aliases = new LinkedHashSet<>();
        for (final Object group : ApiV3EmotionOptions.options().getJSONArray("groups"))
            for (final Object alias : (JSONArray) group) aliases.add((String) alias);
        return Set.copyOf(aliases);
    }
    public static JSONObject normalize(final JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("emotions")) || !(body.opt("emotions") instanceof JSONArray values))
            throw ApiV3Requests.invalid();
        final Set<String> allowed = allowed(), seen = new LinkedHashSet<>();
        if (values.length() > allowed.size()) throw ApiV3Requests.invalid();
        final JSONArray result = new JSONArray();
        for (final Object value : values) {
            if (!(value instanceof String alias) || !allowed.contains(alias) || !seen.add(alias)) throw ApiV3Requests.invalid();
            result.put(alias);
        }
        return new JSONObject().put("emotions", result);
    }
    public static JSONObject effective(final JSONObject normalized) {
        final JSONArray aliases = normalized.getJSONArray("emotions");
        return new JSONObject().put("emotions", aliases.isEmpty()
                ? ApiV3EmotionOptions.options().getJSONArray("defaults") : new JSONArray(aliases.toString()));
    }
}
