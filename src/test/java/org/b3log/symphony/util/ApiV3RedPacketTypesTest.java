package org.b3log.symphony.util;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** No database, server, payment provider or legacy mutation is invoked. */
public final class ApiV3RedPacketTypesTest {
    public static void main(final String[] args) {
        final JSONObject catalog = ApiV3RedPacketTypes.catalog();
        check(catalog.keySet().equals(Set.of("types", "creationSupported", "claimSupported")), "root whitelist");
        check(catalog.getBoolean("creationSupported") && catalog.getBoolean("claimSupported"), "safe-type v3 writes are available");
        final Map<String, Boolean> risks = new HashMap<>();
        for (final Object value : catalog.getJSONArray("types")) {
            final JSONObject type = (JSONObject) value;
            check(type.keySet().equals(Set.of("id", "name", "mayLosePoints")), "type whitelist");
            check(!type.getString("name").isBlank(), "display name");
            check(risks.put(type.getString("id"), type.getBoolean("mayLosePoints")) == null, "unique type");
        }
        check(risks.equals(Map.of("random", false, "average", false, "specify", false,
                "heartbeat", true, "rockPaperScissors", true)), "known supported types and negative-point risks");
        check(!risks.containsKey("dice"), "disabled dice is not offered");
        catalog.getJSONArray("types").getJSONObject(0).put("id", "changed");
        catalog.put("creationSupported", false);
        final JSONObject next = ApiV3RedPacketTypes.catalog();
        check(next.getJSONArray("types").getJSONObject(0).getString("id").equals("random"), "independent response trees");
        check(next.getBoolean("creationSupported"), "capabilities cannot be changed by response mutation");
        System.out.println("PASS red-packet catalog: type/risk whitelist, disabled dice, available safe-type writes, independent responses");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
