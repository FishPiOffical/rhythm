package org.b3log.symphony.util;

import org.json.JSONObject;
import org.b3log.symphony.model.RepeaterContent;

/** Public plain-text repeater projection; persistence fields never escape. */
public final class ApiV3RepeaterDto {
    private ApiV3RepeaterDto() { }
    public static ApiV3Exception unavailable() { return new ApiV3Exception(503,"temporarily_unavailable","复读机内容暂不可用"); }
    public static JSONObject item(final JSONObject row) {
        final String type=text(row,"type",4), content=text(row,"content",500);
        if (!RepeaterContent.isSupportedType(type)) throw unavailable();
        final JSONObject result=new JSONObject().put("id",id(row,"id")).put("type",type).put("content",content)
                .put("likeCount",number(row,"likeCount")).put("createdTime",number(row,"createdTime")).put("liked",number(row,"liked")>0);
        result.put("author",JSONObject.NULL);
        if (!row.optString("authorId").isEmpty()) result.put("author",new JSONObject().put("id",id(row,"authorId")).put("name",text(row,"authorName",64)));
        return result;
    }
    private static String id(final JSONObject row,final String key) { final String v=text(row,key,32);if(!v.matches("[0-9]{1,32}"))throw unavailable();return v; }
    private static String text(final JSONObject row,final String key,final int max) {
        final Object raw=row.opt(key);if(!(raw instanceof String value)||value.length()>max)throw unavailable();
        for(int i=0;i<value.length();i++){final char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))throw unavailable();}else if(Character.isLowSurrogate(c)||Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t')throw unavailable();}
        return value;
    }
    private static long number(final JSONObject row,final String key) {
        final Object raw=row.opt(key);
        if(!(raw instanceof Number))throw unavailable();
        try {final long value=new java.math.BigDecimal(raw.toString()).longValueExact();if(value<0)throw unavailable();return value;}
        catch(final NumberFormatException|ArithmeticException e){throw unavailable();}
    }
}
