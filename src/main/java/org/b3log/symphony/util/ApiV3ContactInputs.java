package org.b3log.symphony.util;

import org.b3log.latke.util.Strings;
import org.json.JSONObject;
import java.util.Locale;
import java.util.Set;

/** Only the new contact is accepted as a verification target; no legacy session fields. */
public final class ApiV3ContactInputs {
    private ApiV3ContactInputs(){ }
    public record Input(String target,String password,String totp,String proof) { }
    public static Input parse(JSONObject body,boolean phone,boolean sending) {
        final Set<String> allowed=sending?Set.of("target","currentPassword","currentTotp","captcha"):Set.of("target","currentPassword","currentTotp","code");
        if(body==null||!allowed.containsAll(body.keySet()))throw ApiV3WriteInputs.invalid();
        final String target=target(required(body,"target",255),phone);
        final String password=required(body,"currentPassword",32);ApiV3SecurityInputs.digest(password);
        final String totp=body.has("currentTotp")?required(body,"currentTotp",6):"";
        if(!totp.isEmpty())ApiV3SecurityInputs.totp(totp);
        final String proof=required(body,sending?"captcha":"code",sending?4096:6);
        if(sending)return new Input(target,password,totp,AccountCaptchaProof.validate(proof));
        if(!proof.matches("[0-9]{6}"))throw ApiV3WriteInputs.invalid();
        return new Input(target,password,totp,proof);
    }
    public static String target(String raw,boolean phone) {
        if(raw==null||raw.length()>255)throw ApiV3WriteInputs.invalid();
        if(phone){if(!raw.matches("1[0-9]{10}"))throw ApiV3WriteInputs.invalid();return raw;}
        final String value=raw.trim().toLowerCase(Locale.ROOT);
        if(value.isEmpty()||value.length()>255||value.codePoints().anyMatch(c->Character.isISOControl(c)||Character.isWhitespace(c))||!Strings.isEmail(value))throw ApiV3WriteInputs.invalid();
        return value;
    }
    private static String required(JSONObject body,String key,int max) {
        if(!(body.opt(key) instanceof String value)||value.isEmpty()||value.length()>max)throw ApiV3WriteInputs.invalid();
        ApiV3Json.canonical(value);return value;
    }
}
