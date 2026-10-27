package org.b3log.symphony.util;

import org.json.JSONObject;
import java.util.Set;

/** Only a registered plan and optional exact coupon are selected by the authenticated purchaser. */
public final class ApiV3MembershipOpeningInputs {
    private ApiV3MembershipOpeningInputs() { }
    public record Opening(String levelId, String couponCode) {
        public JSONObject normalized() { return new JSONObject().put("levelId",levelId).put("couponCode",couponCode); }
    }
    public static Opening parse(final JSONObject body) {
        if (body == null || !Set.of("levelId","couponCode").containsAll(body.keySet())
                || !(body.opt("levelId") instanceof String id) || !id.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
        String code = "";
        if (body.has("couponCode")) {
            if (!(body.opt("couponCode") instanceof String value) || value.length()>128) throw ApiV3WriteInputs.invalid();
            if (value.codePoints().anyMatch(Character::isISOControl)) throw ApiV3WriteInputs.invalid();
            code=value.trim();
            if (!code.isEmpty() && (code.length()<8 || code.length()>32)) throw ApiV3WriteInputs.invalid();
            if (code.codePoints().anyMatch(Character::isISOControl)) throw ApiV3WriteInputs.invalid();
            try { ApiV3Json.canonical(code); } catch (IllegalArgumentException e) { throw ApiV3WriteInputs.invalid(); }
        }
        return new Opening(id,code);
    }
}
