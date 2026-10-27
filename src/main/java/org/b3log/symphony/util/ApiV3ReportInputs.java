package org.b3log.symphony.util;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import java.util.Set;

/** Typed report contract, normalized before idempotency hashing. */
public final class ApiV3ReportInputs {
    private ApiV3ReportInputs() { }
    public static JSONObject normalize(final JSONObject body) {
        if(body==null || !Set.of("targetType","targetId","reason","memo").containsAll(body.keySet())
                || !body.has("targetType") || !body.has("targetId") || !body.has("reason")) throw ApiV3Requests.invalid();
        final int type=ApiV3WriteInputs.integer(body,"targetType",-1,0,3);
        final int reason=ApiV3WriteInputs.integer(body,"reason",-1,0,49);
        if(!(body.opt("targetId") instanceof String target) || !target.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
        if(type==3 ? reason!=49 : type==2 ? !Set.of(5,6,7,49).contains(reason) : !Set.of(0,1,2,3,4,49).contains(reason)) throw ApiV3Requests.invalid();
        final String raw=ApiV3WriteInputs.string(body,"memo",10240,true);
        try{ApiV3Json.canonical(raw);}catch(IllegalArgumentException e){throw ApiV3Requests.invalid();}
        final String memo=Jsoup.parseBodyFragment(raw).wholeText().trim();
        if(org.apache.commons.lang.StringEscapeUtils.escapeHtml(memo).length()>10240 || memo.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t')) throw ApiV3Requests.invalid();
        try{ApiV3Json.canonical(memo);}catch(IllegalArgumentException e){throw ApiV3Requests.invalid();}
        return new JSONObject().put("targetType",type).put("targetId",target).put("reason",reason).put("memo",memo);
    }
}
