package org.b3log.symphony.util;

import org.json.JSONObject;
import java.util.Set;

/** Security proofs are validated but never normalized into ordinary idempotency receipts. */
public final class ApiV3SecurityInputs {
    private ApiV3SecurityInputs(){ }
    public record Password(String currentPassword,String newPassword,String currentTotp){ }
    public static Password password(final JSONObject body){
        if(body==null||!Set.of("currentPassword","newPassword","currentTotp").containsAll(body.keySet()))throw ApiV3Requests.invalid();
        return new Password(digest(body.opt("currentPassword")),digest(body.opt("newPassword")),totp(body.opt("currentTotp")));
    }
    public static String digest(final Object value){if(!(value instanceof String text)||!text.matches("[a-f0-9]{32}"))throw ApiV3Requests.invalid();return text;}
    public static String totp(final Object value){if(value==null)return "";if(!(value instanceof String text)||!text.matches("[0-9]{6}"))throw ApiV3Requests.invalid();return text;}
}
