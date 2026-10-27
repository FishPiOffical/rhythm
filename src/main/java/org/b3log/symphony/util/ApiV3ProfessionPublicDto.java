package org.b3log.symphony.util;

import org.json.JSONObject;

/** Exact public profession projections, with experience independently omitted throughout. */
public final class ApiV3ProfessionPublicDto {
    private ApiV3ProfessionPublicDto() { }
    public static String text(final JSONObject row,final String key,final int max,final boolean empty) {
        return ApiV3ProfessionDetailDto.text(row,key,max,empty);
    }
    public static long number(final JSONObject row,final String key,final boolean signed) {
        return ApiV3ProfessionDetailDto.number(row,key,signed);
    }
    public static String id(final JSONObject row,final String key) {
        final String id=text(row,key,19,false);
        if(!id.matches("[0-9]{1,19}"))throw ApiV3ProfessionDetailDto.unavailable();
        return id;
    }
    public static JSONObject definition(final JSONObject row) {
        return new JSONObject().put("professionId",id(row,"professionId"))
                .put("professionCode",text(row,"professionCode",64,false)).put("displayName",text(row,"displayName",64,false))
                .put("shortName",text(row,"shortName",32,true)).put("description",text(row,"description",1024,true));
    }
    public static JSONObject progress(final JSONObject row,final boolean experience) {
        final JSONObject value=definition(row).put("state","PUBLISHED".equals(row.getString("professionStatus"))?"ACTIVE":"RETIRED")
                .put("levelCode",text(row,"levelCode",64,false)).put("levelName",text(row,"levelName",64,false));
        // Validate even hidden numeric data without exposing it through another module.
        final long total=number(row,"totalExperience",false), required=number(row,"requiredExperience",false);
        if(experience)value.put("totalExperience",total).put("requiredExperience",required);
        return value;
    }
    public static String action(final JSONObject row) {
        final String value=text(row,"actionCode",64,false);
        if(!value.matches("[a-z][a-z0-9._-]{0,63}"))throw ApiV3ProfessionDetailDto.unavailable();
        return value;
    }
    public static JSONObject contribution(final JSONObject row,final boolean experience) {
        final JSONObject value=new JSONObject().put("professionId",id(row,"professionId")).put("actionCode",action(row))
                .put("eventCount",number(row,"eventCount",false)).put("lastOccurredAt",number(row,"lastOccurredAt",false));
        final long sum=number(row,"experienceSum",true);
        if(experience)value.put("experienceSum",sum);
        return value;
    }
}
