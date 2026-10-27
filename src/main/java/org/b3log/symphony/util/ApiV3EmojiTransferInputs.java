package org.b3log.symphony.util;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Stored shares and cloud payloads receive the same strict validation as new requests. */
public final class ApiV3EmojiTransferInputs {
    public static final int MAX_ITEMS=256, MAX_TEXT=1048576;
    private ApiV3EmojiTransferInputs() { }
    public static String code(final JSONObject body) {
        if(body==null||!body.keySet().equals(Set.of("shareCode"))||!(body.opt("shareCode") instanceof String code)||code.length()>128)throw ApiV3WriteInputs.invalid();
        final String normalized=code.strip().toUpperCase(Locale.ROOT);
        if(!normalized.matches("[A-Z0-9]{8,16}"))throw ApiV3WriteInputs.invalid();return normalized;
    }
    public static JSONArray array(final Object raw) {
        if(!(raw instanceof String text)||text.isBlank())throw ApiV3WriteInputs.invalid();
        if(text.length()>MAX_TEXT||text.getBytes(StandardCharsets.UTF_8).length>MAX_TEXT)throw unavailable();
        try {
            final JSONObject wrapped=ApiV3Json.parse(("{\"items\":"+text+"}").getBytes(StandardCharsets.UTF_8));
            if(!wrapped.keySet().equals(Set.of("items"))||!(wrapped.opt("items") instanceof JSONArray array))throw ApiV3WriteInputs.invalid();
            if(array.length()>MAX_ITEMS)throw unavailable();return array;
        }catch(ApiV3Exception error){throw error;}catch(Exception error){throw ApiV3WriteInputs.invalid();}
    }
    public static JSONObject entry(final Object raw) {
        if(!(raw instanceof JSONObject row)||!row.keySet().equals(Set.of("url","name","sort")))throw ApiV3WriteInputs.invalid();
        return ApiV3EmojiItemInputs.addition(row,true);
    }
    public static ApiV3Exception unavailable(){return new ApiV3Exception(503,"temporarily_unavailable","表情批量操作暂不可用");}
}
