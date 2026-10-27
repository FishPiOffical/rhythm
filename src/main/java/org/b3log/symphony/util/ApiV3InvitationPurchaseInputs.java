package org.b3log.symphony.util;

import org.json.JSONObject;

/** A purchase always issues one server-priced code to the authenticated actor. */
public final class ApiV3InvitationPurchaseInputs {
    private ApiV3InvitationPurchaseInputs() { }
    public static JSONObject parse(final JSONObject body) {
        if (body == null || !body.isEmpty()) throw ApiV3WriteInputs.invalid();
        return new JSONObject();
    }
}
