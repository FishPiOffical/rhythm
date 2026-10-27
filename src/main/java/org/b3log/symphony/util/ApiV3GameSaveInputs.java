package org.b3log.symphony.util;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.math.BigDecimal;

/** Explicit game registry, transparent Unicode data and bounded exact revision contract. No generic cloud namespace. */
public final class ApiV3GameSaveInputs {
    private ApiV3GameSaveInputs() { }
    public static final String EVOLVE="evolve-save";
    public static final int MAX_UNITS=500_000,MAX_BYTES=2*1024*1024,MAX_REQUEST_BYTES=4*1024*1024;
    public static final long MAX_REVISION=9_007_199_254_740_991L;
    public record Content(String data,int byteLength,String sha256) { }
    public record Save(Content content,long expectedRevision) { }
    public static boolean registered(String game) { return EVOLVE.equals(game); }
    public static String game(String value) { if(!registered(value))throw ApiV3WriteInputs.invalid();return value; }
    public static JSONArray options() {
        return new JSONArray().put(new JSONObject().put("gameId",EVOLVE).put("name","Evolve").put("dataFormat","string")
                .put("maxLength",MAX_UNITS).put("maxBytes",MAX_BYTES).put("legacyMigration",true));
    }
    public static Save save(JSONObject body) {
        if(body==null||!body.keySet().equals(Set.of("data","expectedRevision")))throw ApiV3WriteInputs.invalid();
        return new Save(content(body.opt("data")),revision(body.opt("expectedRevision"),true));
    }
    public static Content content(Object value) {
        if(!(value instanceof String text)||text.length()>MAX_UNITS)throw ApiV3WriteInputs.invalid();
        try {ApiV3Json.canonical(text);}catch(IllegalArgumentException invalid){throw ApiV3WriteInputs.invalid();}
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw ApiV3WriteInputs.invalid();
        try{return new Content(text,bytes.length,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable");}
    }
    public static long revision(Object value,boolean zeroAllowed) {
        if(!(value instanceof Number number))throw ApiV3WriteInputs.invalid();
        try {long n=new BigDecimal(number.toString()).longValueExact();if(n<(zeroAllowed?0:1)||n>MAX_REVISION)throw ApiV3WriteInputs.invalid();return n;}
        catch(ArithmeticException|NumberFormatException invalid){throw ApiV3WriteInputs.invalid();}
    }
}
