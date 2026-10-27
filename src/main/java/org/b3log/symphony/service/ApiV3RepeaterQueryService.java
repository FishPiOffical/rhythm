package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.b3log.symphony.repository.RepeaterLikeRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3RepeaterDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Read-only top-pool semantics shared with the existing public repeater feature. */
@Service
public class ApiV3RepeaterQueryService {
    @Inject private RepeaterContentRepository contents;
    @Inject private RepeaterLikeRepository likes;
    @Inject private UserRepository users;
    private void actor(final String actor) throws RepositoryException {
        if (actor.isEmpty()) return;
        if(!actor.matches("[0-9]{1,32}"))throw ApiV3Requests.invalid();
        if(users.select("SELECT oId FROM `"+users.getName()+"` WHERE oId=? AND userStatus=? LIMIT 2",actor,UserExt.USER_STATUS_C_VALID).size()!=1)throw ApiV3Requests.notFound();
    }
    private List<JSONObject> pool(final String actor,final String type,final int size) throws RepositoryException {
        final String columns="SELECT c.oId AS id,c.repeaterContentType AS type,SUBSTR(c.repeaterContent,1,501) AS content,c.repeaterContentLikeCount AS likeCount,c.repeaterContentCreatedTime AS createdTime,"
                +"CASE WHEN u.userStatus=0 THEN u.oId ELSE '' END AS authorId,CASE WHEN u.userStatus=0 THEN SUBSTR(u.userName,1,65) ELSE '' END AS authorName,"
                +"CASE WHEN ?<>'' AND EXISTS(SELECT 1 FROM `"+likes.getName()+"` k WHERE k.repeaterLikeContentId=c.oId AND k.repeaterLikeUserId=?) THEN 1 ELSE 0 END AS liked";
        return contents.select(columns+" FROM `"+contents.getName()+"` c LEFT JOIN `"+users.getName()+"` u ON u.oId=c.repeaterContentAuthorId WHERE c.repeaterContentStatus=0"
                +(type.isEmpty()?"":" AND c.repeaterContentType=?")+" ORDER BY c.repeaterContentLikeCount DESC,c.repeaterContentCreatedTime DESC,c.oId DESC LIMIT ?",
                type.isEmpty()?new Object[]{actor,actor,size}:new Object[]{actor,actor,type,size});
    }
    public JSONObject items(final String actor,final String type,final int size) {
        if(actor==null||type==null||!type.isEmpty()&&!RepeaterContent.isSupportedType(type)||size<1||size>50)throw ApiV3Requests.invalid();
        try{
            actor(actor);final List<JSONObject> rows=pool(actor,type,size);final JSONArray items=new JSONArray();final java.util.Set<String> seen=new java.util.HashSet<>();for(final JSONObject row:rows){final JSONObject item=ApiV3RepeaterDto.item(row);if(!seen.add(item.getString("id")))throw ApiV3RepeaterDto.unavailable();items.put(item);}
            actor(actor);if(!new JSONArray(rows).similar(new JSONArray(pool(actor,type,size))))throw ApiV3RepeaterDto.unavailable();actor(actor);
            return new JSONObject().put("items",items);
        }catch(final RepositoryException e){throw ApiV3RepeaterDto.unavailable();}
    }
    public JSONObject next(final String actor,final String type,final String excludeId) {
        if(excludeId==null||!excludeId.isEmpty()&&!excludeId.matches("[0-9]{1,32}"))throw ApiV3Requests.invalid();
        final JSONArray rows=items(actor,type,50).getJSONArray("items");final JSONArray candidates=new JSONArray();
        for(int i=0;i<rows.length();i++)if(!excludeId.equals(rows.getJSONObject(i).getString("id")))candidates.put(rows.getJSONObject(i));
        return new JSONObject().put("item",candidates.isEmpty()?JSONObject.NULL:candidates.getJSONObject(ThreadLocalRandom.current().nextInt(candidates.length())));
    }
}
