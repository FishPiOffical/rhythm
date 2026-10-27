package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.MembershipRepository;
import org.b3log.symphony.repository.MembershipLevelRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Requested users only; no global list, cache side effects, arbitrary configuration or account metadata. */
@Service
public class ApiV3MembershipStyleQueryService {
    @Inject private UserRepository users;
    @Inject private MembershipRepository memberships;
    @Inject private MembershipLevelRepository levels;
    protected long now(){return System.currentTimeMillis();}

    public static List<String> ids(String input){
        if(input==null||input.isEmpty()||input.length()>1999)throw ApiV3Requests.invalid();
        final List<String> ids=List.of(input.split(",",-1));
        if(ids.size()>100||new HashSet<>(ids).size()!=ids.size()||ids.stream().anyMatch(id->!id.matches("[1-9][0-9]{0,18}")))throw ApiV3Requests.invalid();
        return ids;
    }

    public JSONObject styles(String actor,List<String> ids){
        if(actor==null||!actor.matches("[1-9][0-9]{0,18}"))throw ApiV3Requests.forbidden();
        if(ids==null||!ids.equals(ids(String.join(",",ids))))throw ApiV3Requests.invalid();
        try{
            final var current=users.select("SELECT oId FROM `"+users.getName()+"` WHERE oId=? AND userStatus=0 LIMIT 1",actor);
            if(current.size()!=1)throw ApiV3Requests.notFound();
            final String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
            final var rows=memberships.select("SELECT u.oId AS userId,m.lvCode,m.expiresAt,m.configJson,l.oId AS levelId,l.lvCode AS levelCode,l.benefits FROM `"
                    +users.getName()+"` u JOIN `"+memberships.getName()+"` m ON m.userId=u.oId AND m.state=1 LEFT JOIN `"
                    +levels.getName()+"` l ON l.lvCode=m.lvCode WHERE u.userStatus=0 AND u.oId IN ("+placeholders+") LIMIT 201",ids.toArray());
            if(rows.size()>100)throw unavailable();
            final Map<String,JSONObject> found=new HashMap<>();
            final Set<String> seen=new HashSet<>();final long time=now();
            for(final var row:rows){
                final String id=row.optString("userId");if(!ids.contains(id)||!seen.add(id))throw unavailable();
                final long expiry=expiry(row.opt("expiresAt"));
                if(expiry!=0&&expiry<=time)continue;
                if(!row.has("levelId")||row.isNull("levelId"))continue;
                final String code=row.optString("lvCode");
                if(code.isBlank()||code.length()>32||!code.equals(row.optString("levelCode")))throw unavailable();
                final JSONObject definition=ApiV3MembershipConfiguration.defaults(new JSONObject().put("lvCode",code).put("benefits",row.opt("benefits")));
                final JSONObject config=config(row.opt("configJson")),style=new JSONObject();
                for(final String field:Set.of("bold","underline"))if(definition.has(field)&&config.opt(field) instanceof Boolean flag)style.put(field,flag);
                if(definition.has("color")&&config.opt("color") instanceof String color
                        &&ApiV3MembershipConfiguration.colorAllowed(code,color))style.put("color",color);
                found.put(id,style);
            }
            final JSONArray list=new JSONArray();
            for(final String id:ids)list.put(new JSONObject().put("userId",id).put("style",found.getOrDefault(id,new JSONObject())));
            return new JSONObject().put("list",list);
        }catch(RepositoryException error){throw unavailable();}
    }
    private static JSONObject config(Object raw){
        if(!(raw instanceof String text)||text.length()>102400)throw unavailable();
        if(text.isBlank())return new JSONObject();
        if(text.getBytes(StandardCharsets.UTF_8).length>102400)throw unavailable();
        try{return ApiV3Json.parse(text.getBytes(StandardCharsets.UTF_8));}catch(IllegalArgumentException error){throw unavailable();}
    }
    private static long expiry(Object raw){
        if(!(raw instanceof Number number))throw unavailable();
        try{long value=new BigDecimal(number.toString()).longValueExact();if(value<0)throw unavailable();return value;}
        catch(ArithmeticException|NumberFormatException error){throw unavailable();}
    }
    private static ApiV3Exception unavailable(){return ApiV3MembershipConfiguration.unavailable();}
}
