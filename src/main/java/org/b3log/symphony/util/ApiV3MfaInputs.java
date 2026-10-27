package org.b3log.symphony.util;

import org.json.JSONObject;
import java.util.Set;

public final class ApiV3MfaInputs {
    private ApiV3MfaInputs(){ }
    public record Proof(String currentPassword,String currentTotp,String preparationId,String newTotp){ }
    public static Proof parse(JSONObject body,boolean enable){
        final Set<String> allowed=enable?Set.of("currentPassword","currentTotp","preparationId","newTotp"):Set.of("currentPassword","currentTotp");
        if(body==null||!allowed.containsAll(body.keySet()))throw ApiV3WriteInputs.invalid();
        final String password=ApiV3SecurityInputs.digest(body.opt("currentPassword"));
        final String current=ApiV3SecurityInputs.totp(body.opt("currentTotp"));
        if(!enable)return new Proof(password,current,"","");
        if(!(body.opt("preparationId") instanceof String handle)||!handle.matches("[A-Za-z0-9_-]{43}"))throw ApiV3WriteInputs.invalid();
        final String next=ApiV3SecurityInputs.totp(body.opt("newTotp"));if(next.isEmpty())throw ApiV3WriteInputs.invalid();
        return new Proof(password,current,handle,next);
    }
}
