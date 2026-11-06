/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.service;

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.UserProfessionPrivacyRepository;
import org.b3log.symphony.repository.UserProfessionRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.ProfessionRevisionRepository;
import org.b3log.symphony.repository.ProfessionLevelSchemeRepository;
import org.b3log.symphony.repository.ProfessionLevelRepository;
import org.b3log.symphony.repository.UserProfessionContributionRepository;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.b3log.symphony.repository.ProfessionSourceEventRepository;
import org.b3log.symphony.repository.ProfessionRewardGrantRepository;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.CommentRepository;
import org.b3log.symphony.repository.BreezemoonRepository;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.util.ApiV3ContentUtil;
import org.b3log.symphony.util.ApiV3ProfessionDetailDto;
import org.b3log.symphony.util.ApiV3ProfessionPublicDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.UserMentionCandidates;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Public profiles are read-only bounded metadata projections, with current viewer and independent module privacy. */
@Service
public class ApiV3ProfessionPublicQueryService {
    private static final Set<String> MODULES=Set.of("primaryProfession","allProfessions","experience","rarity","majorContributions",
            "contributionStats","activityFeed","levelHistory","rewards","ranking");
    private static final Set<String> SCOPES=Set.of("PUBLIC","LOGGED_IN","SELF","HIDDEN");
    private static final int LIMIT=512;
    private record CheckedRead(AbstractRepository repository,String sql,Object[] args,List<JSONObject> rows) { }
    private static List<JSONObject> checked(final List<CheckedRead> checks,final AbstractRepository repository,final String sql,final Object...args)throws RepositoryException {
        final List<JSONObject> rows=repository.select(sql,args);
        checks.add(new CheckedRead(repository,sql,args,rows));
        return rows;
    }
    private static void recheck(final List<CheckedRead> checks)throws RepositoryException {
        for(CheckedRead read:checks)if(!new JSONArray(read.rows()).similar(new JSONArray(read.repository().select(read.sql(),read.args()))))throw unavailable();
    }
    @Inject private UserRepository users;
    @Inject private UserProfessionPrivacyRepository privacy;
    @Inject private UserProfessionRepository progress;
    @Inject private ProfessionRepository professions;
    @Inject private ProfessionRevisionRepository revisions;
    @Inject private ProfessionLevelSchemeRepository schemes;
    @Inject private ProfessionLevelRepository levels;
    @Inject private UserProfessionContributionRepository contributions;
    @Inject private ProfessionEventEffectRepository effects;
    @Inject private ProfessionSourceEventRepository sources;
    @Inject private ProfessionRewardGrantRepository rewards;
    @Inject private ArticleRepository articles;
    @Inject private CommentRepository comments;
    @Inject private BreezemoonRepository moons;
    @Inject private RepeaterContentRepository repeaters;
    @Inject private ChatRoomRepository chats;
    @Inject private ApiV3ProfessionRankingQueryService ranking;
    private static RuntimeException unavailable(){return ApiV3ProfessionDetailDto.unavailable();}
    private static List<JSONObject> bounded(final List<JSONObject> rows){if(rows.size()>LIMIT)throw unavailable();return rows;}
    private JSONObject user(final String key,final String value,final boolean actor)throws RepositoryException {
        final var rows=users.select("SELECT oId,userName,userRole,userStatus FROM `"+users.getName()+"` WHERE "+key+"=? AND userStatus"
                +(actor?"=?":" IN (?,?)")+" LIMIT 2",actor?new Object[]{value,UserExt.USER_STATUS_C_VALID}:
                new Object[]{value,UserExt.USER_STATUS_C_VALID,UserExt.USER_STATUS_C_INVALID_LOGIN});
        if(rows.size()!=1)throw ApiV3Requests.notFound();
        return rows.getFirst();
    }
    private JSONObject state(final String owner)throws RepositoryException {
        final var rows=privacy.select("SELECT primaryProfessionId,moduleVisibilityJson FROM `"+privacy.getName()+"` WHERE userId=? LIMIT 2",owner);
        if(rows.size()>1)throw unavailable();
        if(rows.isEmpty()){
            final JSONObject visibility=new JSONObject();for(String key:MODULES)visibility.put(key,"PUBLIC");
            return new JSONObject().put("primaryProfessionId","").put("moduleVisibilityJson",visibility.toString());
        }
        final JSONObject state=rows.getFirst();
        final String primary=ApiV3ProfessionPublicDto.text(state,"primaryProfessionId",19,true);
        if(!primary.isEmpty()&&!primary.matches("[0-9]{1,19}"))throw unavailable();
        final JSONObject visibility=new JSONObject(ApiV3ProfessionPublicDto.text(state,"moduleVisibilityJson",4096,false));
        if(!visibility.keySet().equals(MODULES))throw unavailable();
        for(String key:MODULES)if(!(visibility.opt(key) instanceof String value)||!SCOPES.contains(value))throw unavailable();
        return state;
    }
    private boolean visible(final JSONObject visibility,final String key,final String owner,final JSONObject actor){
        return switch(visibility.getString(key)){
            case "PUBLIC"->true;case "LOGGED_IN"->actor!=null;case "SELF"->actor!=null&&owner.equals(actor.getString("oId"));case "HIDDEN"->false;
            default->throw unavailable();
        };
    }
    private List<JSONObject> progress(final String owner)throws RepositoryException {
        final var rows=bounded(progress.select("SELECT up.professionId,p.professionCode,p.status AS professionStatus,r.status AS revisionStatus,s.status AS schemeStatus,"
                +"r.displayName,r.shortName,r.description,up.totalExperience,up.currentLevelCode AS levelCode,l.displayName AS levelName,l.requiredTotalExperience AS requiredExperience,l.levelCode AS actualLevelCode"
                +" FROM `"+progress.getName()+"` up LEFT JOIN `"+professions.getName()+"` p ON p.oId=up.professionId LEFT JOIN `"+revisions.getName()
                +"` r ON r.oId=up.professionRevisionId AND r.professionId=up.professionId LEFT JOIN `"+schemes.getName()
                +"` s ON s.oId=up.levelSchemeId AND s.professionId=up.professionId LEFT JOIN `"+levels.getName()
                +"` l ON l.schemeId=up.levelSchemeId AND l.levelCode=up.currentLevelCode WHERE up.userId=? ORDER BY up.totalExperience DESC,up.professionId ASC LIMIT ?",owner,LIMIT+1));
        final Set<String> ids=new HashSet<>();
        for(JSONObject row:rows){
            if(!ids.add(ApiV3ProfessionPublicDto.id(row,"professionId")))throw unavailable();
            for(String key:List.of("professionStatus","revisionStatus","schemeStatus"))if(!Set.of("PUBLISHED","RETIRED").contains(row.optString(key)))throw unavailable();
            if(!row.getString("levelCode").equals(row.optString("actualLevelCode")))throw unavailable();
            ApiV3ProfessionPublicDto.progress(row,true);
        }
        return rows;
    }
    public JSONObject profile(final String name,final String actorId){
        if(name==null||!name.matches("[A-Za-z0-9_-]{1,64}")||actorId==null||(!actorId.isEmpty()&&!actorId.matches("[0-9]{1,19}")))throw ApiV3Requests.invalid();
        try{
            final JSONObject owner=user("userName",name,false),actor=actorId.isEmpty()?null:user("oId",actorId,true);
            final String ownerId=owner.getString("oId");final JSONObject state=state(ownerId),visibility=new JSONObject(state.getString("moduleVisibilityJson"));
            if(MODULES.stream().noneMatch(key->visible(visibility,key,ownerId,actor)))throw ApiV3Requests.notFound();
            final boolean experience=visible(visibility,"experience",ownerId,actor);
            final var rows=progress(ownerId);final JSONObject result=new JSONObject();final List<CheckedRead> checks=new ArrayList<>();
            if(visible(visibility,"primaryProfession",ownerId,actor))appendPrimary(result,state.getString("primaryProfessionId"),rows,experience);
            if(visible(visibility,"allProfessions",ownerId,actor)){
                final JSONArray list=new JSONArray();for(JSONObject row:rows)list.put(ApiV3ProfessionPublicDto.progress(row,experience));result.put("professions",list);
            }
            if(visible(visibility,"majorContributions",ownerId,actor)||visible(visibility,"contributionStats",ownerId,actor))appendContributions(result,ownerId,rows,visibility,actor,experience);
            if(visible(visibility,"rewards",ownerId,actor))appendRewards(result,ownerId);
            if(visible(visibility,"activityFeed",ownerId,actor)||visible(visibility,"levelHistory",ownerId,actor))appendActivity(result,ownerId,visibility,actor,experience,checks);
            appendRanking(result,rows,visibility,ownerId,actor);
            // Prevent a multi-query response from mixing privacy/account/progress versions.
            if(!owner.similar(user("userName",name,false))||!(actor==null?actorId.isEmpty():actor.similar(user("oId",actorId,true)))
                    ||!state.similar(state(ownerId))||!new JSONArray(rows).similar(new JSONArray(progress(ownerId))))throw unavailable();
            recheck(checks);
            if(result.isEmpty())throw ApiV3Requests.notFound();
            return result;
        }catch(RepositoryException|org.json.JSONException e){LogManager.getLogger(getClass()).warn("Public profession read unavailable");throw unavailable();}
    }
    private void appendRanking(final JSONObject result,final List<JSONObject> rows,final JSONObject visibility,final String owner,final JSONObject actor) {
        final boolean showRarity=visible(visibility,"rarity",owner,actor), showRanking=visible(visibility,"ranking",owner,actor);
        if(!showRarity&&!showRanking)return;
        final JSONArray ranks=new JSONArray();
        final var budget=new ApiV3ProfessionRankingQueryService.CandidateBudget();
        for(JSONObject row:rows){
            if(!"PUBLISHED".equals(row.optString("professionStatus")))continue;
            final String id=row.getString("professionId");
            final var summary=ranking.summary(id,actor==null?"":actor.getString("oId"),owner,budget);
            if(summary.isEmpty())continue;
            if(showRanking)ranks.put(new JSONObject(summary.get().toString()).put("professionId",id));
            if(showRarity){
                final JSONObject primary=result.optJSONObject("primaryProfession");
                if(primary!=null&&id.equals(primary.optString("professionId")))primary.put("rarity",new JSONObject(summary.get().toString()));
                final JSONArray list=result.optJSONArray("professions");
                if(list!=null)for(Object item:list){final JSONObject profession=(JSONObject)item;if(id.equals(profession.optString("professionId")))profession.put("rarity",new JSONObject(summary.get().toString()));}
            }
        }
        if(showRanking)result.put("ranking",ranks);
    }
    private void appendPrimary(final JSONObject result,final String primary,final List<JSONObject> rows,final boolean experience)throws RepositoryException {
        if(primary.isEmpty())return;
        for(JSONObject row:rows)if(primary.equals(row.getString("professionId"))){result.put("primaryProfession",ApiV3ProfessionPublicDto.progress(row,experience));return;}
        final var definitions=professions.select("SELECT p.oId AS professionId,p.professionCode,r.displayName,r.shortName,r.description FROM `"+professions.getName()
                +"` p JOIN `"+revisions.getName()+"` r ON r.oId=p.currentRevisionId AND r.professionId=p.oId WHERE p.oId=? AND p.status='PUBLISHED' AND r.status='PUBLISHED' LIMIT 2",primary);
        if(definitions.size()>1)throw unavailable();
        if(!definitions.isEmpty())result.put("primaryProfession",ApiV3ProfessionPublicDto.definition(definitions.getFirst()).put("state","SELECTED"));
    }
    private void appendContributions(final JSONObject result,final String owner,final List<JSONObject> progressRows,final JSONObject visibility,final JSONObject actor,final boolean experience)throws RepositoryException {
        final var rows=bounded(contributions.select("SELECT professionId,actionCode,experienceSum,eventCount,lastOccurredAt FROM `"+contributions.getName()
                +"` WHERE userId=? AND eventCount>0 ORDER BY professionId ASC,experienceSum DESC,actionCode ASC,oId ASC LIMIT ?",owner,LIMIT+1));
        final Set<String> known=new HashSet<>(),keys=new HashSet<>();for(JSONObject row:progressRows)known.add(row.getString("professionId"));
        final JSONArray details=new JSONArray(),stats=new JSONArray();final Map<String,JSONObject> aggregates=new LinkedHashMap<>();
        for(JSONObject row:rows){
            final JSONObject dto=ApiV3ProfessionPublicDto.contribution(row,experience);final String profession=dto.getString("professionId");
            if(!known.contains(profession)||!keys.add(profession+":"+dto.getString("actionCode")))throw unavailable();
            details.put(dto);final JSONObject aggregate=aggregates.computeIfAbsent(profession,id->new JSONObject().put("professionId",id).put("eventCount",0L).put("lastOccurredAt",0L).put("experienceSum",0L));
            try{aggregate.put("eventCount",Math.addExact(aggregate.getLong("eventCount"),dto.getLong("eventCount")));
                aggregate.put("experienceSum",Math.addExact(aggregate.getLong("experienceSum"),ApiV3ProfessionPublicDto.number(row,"experienceSum",true)));}
            catch(ArithmeticException e){throw unavailable();}
            aggregate.put("lastOccurredAt",Math.max(aggregate.getLong("lastOccurredAt"),dto.getLong("lastOccurredAt")));
        }
        for(JSONObject row:aggregates.values()){if(!experience)row.remove("experienceSum");stats.put(row);}
        if(visible(visibility,"majorContributions",owner,actor))result.put("majorContributions",details);
        if(visible(visibility,"contributionStats",owner,actor))result.put("contributionStats",stats);
    }
    private void appendRewards(final JSONObject result,final String owner)throws RepositoryException {
        final var rows=bounded(rewards.select("SELECT professionId,levelCode,rewardCode,rewardType,status,grantedAt FROM `"+rewards.getName()+"` WHERE userId=? ORDER BY grantedAt DESC,oId DESC LIMIT ?",owner,LIMIT+1));
        final JSONArray list=new JSONArray();
        for(JSONObject row:rows){
            final String type=ApiV3ProfessionPublicDto.text(row,"rewardType",16,false),status=ApiV3ProfessionPublicDto.text(row,"status",16,false);
            if(!Set.of("POINT","MEDAL").contains(type)||!Set.of("PENDING","GRANTED","REVOKED").contains(status))throw unavailable();
            list.put(new JSONObject().put("professionId",ApiV3ProfessionPublicDto.id(row,"professionId")).put("levelCode",ApiV3ProfessionPublicDto.text(row,"levelCode",64,false))
                    .put("rewardCode",ApiV3ProfessionPublicDto.text(row,"rewardCode",64,false)).put("rewardType",type).put("status",status).put("grantedAt",ApiV3ProfessionPublicDto.number(row,"grantedAt",false)));
        }result.put("rewards",list);
    }
    private void appendActivity(final JSONObject result,final String owner,final JSONObject visibility,final JSONObject actor,final boolean experience,final List<CheckedRead> checks)throws RepositoryException {
        final var rows=bounded(effects.select("SELECT professionId,sourceEventId,actionCode,actualExperienceDelta,beforeLevelCode,afterLevelCode,occurredAt,status AS effectStatus FROM `"+effects.getName()
                +"` WHERE userId=? AND status='APPLIED' AND occurredAt>=? ORDER BY occurredAt DESC,oId DESC LIMIT ?",owner,System.currentTimeMillis()-TimeUnit.DAYS.toMillis(90),LIMIT+1));
        final JSONArray feed=new JSONArray(),history=new JSONArray();
        final long[] bodyBudget={2L*1024*1024};
        for(JSONObject row:rows){
            if(!"APPLIED".equals(row.opt("effectStatus")))throw unavailable();
            if(!sourceVisible(ApiV3ProfessionPublicDto.id(row,"sourceEventId"),owner,actor,bodyBudget,checks))continue;
            final JSONObject dto=new JSONObject().put("professionId",ApiV3ProfessionPublicDto.id(row,"professionId")).put("actionCode",ApiV3ProfessionPublicDto.action(row)).put("occurredAt",ApiV3ProfessionPublicDto.number(row,"occurredAt",false));
            final long delta=ApiV3ProfessionPublicDto.number(row,"actualExperienceDelta",true);if(experience)dto.put("experienceDelta",delta);
            if(feed.length()<30)feed.put(dto);
            final String before=ApiV3ProfessionPublicDto.text(row,"beforeLevelCode",64,true),after=ApiV3ProfessionPublicDto.text(row,"afterLevelCode",64,true);
            if(!before.equals(after)&&history.length()<30)history.put(new JSONObject().put("professionId",dto.getString("professionId")).put("beforeLevelCode",before).put("afterLevelCode",after).put("occurredAt",dto.getLong("occurredAt")));
        }
        if(visible(visibility,"activityFeed",owner,actor))result.put("activityFeed",feed);
        if(visible(visibility,"levelHistory",owner,actor))result.put("levelHistory",history);
    }
    private boolean sourceVisible(final String source,final String owner,final JSONObject actor,final long[] bodyBudget,final List<CheckedRead> checks)throws RepositoryException {
        final var rows=checked(checks,sources,"SELECT subjectUserId,visibilityClass,targetType,targetId FROM `"+sources.getName()+"` WHERE oId=? LIMIT 2",source);
        if(rows.size()>1)throw unavailable();if(rows.isEmpty()||!"PUBLIC".equals(rows.getFirst().optString("visibilityClass")))return false;
        final JSONObject row=rows.getFirst();
        if(!owner.equals(row.optString("subjectUserId")))throw unavailable();
        if(!Set.of("article","comment","breezemoon","repeater","chatroom_message").contains(row.optString("targetType")))return false;
        final String target=ApiV3ProfessionPublicDto.id(row,"targetId");
        return switch(row.optString("targetType")){
            case "article"->articleVisible(target,actor,bodyBudget,checks);
            case "comment"->commentVisible(target,actor,bodyBudget,checks);
            case "breezemoon"->moonVisible(target,actor,checks);
            case "repeater"->checked(checks,repeaters,"SELECT r.oId,r.repeaterContentAuthorId,u.userStatus FROM `"+repeaters.getName()+"` r JOIN `"+users.getName()
                    +"` u ON u.oId=r.repeaterContentAuthorId WHERE r.oId=? AND r.repeaterContentStatus=0 AND u.userStatus IN (?,?) LIMIT 2",target,UserExt.USER_STATUS_C_VALID,UserExt.USER_STATUS_C_INVALID_LOGIN).size()==1;
            case "chatroom_message"->actor!=null&&checked(checks,chats,"SELECT oId FROM `"+chats.getName()+"` WHERE oId=? LIMIT 2",target).size()==1;
            default->false;
        };
    }
    private boolean articleVisible(final String id,final JSONObject actor,final long[] bodyBudget,final List<CheckedRead> checks)throws RepositoryException {
        final var rows=checked(checks,articles,"SELECT a.oId,a.articleAuthorId,a.articleType,a.articleStatus,a.articleAnonymous,a.articleAnonymousView,"
                +"CASE WHEN a.articleType=1 THEN OCTET_LENGTH(a.articleContent) ELSE 0 END AS bodyBytes,CASE WHEN a.articleType=1 THEN SHA2(a.articleContent,256) ELSE '' END AS bodyHash,u.userStatus AS authorStatus FROM `"+articles.getName()+"` a JOIN `"+users.getName()
                +"` u ON u.oId=a.articleAuthorId WHERE a.oId=? AND a.articleStatus IN (0,2) AND a.articleAnonymous=0 AND u.userStatus IN (?,?)"
                +" LIMIT 2",id,UserExt.USER_STATUS_C_VALID,UserExt.USER_STATUS_C_INVALID_LOGIN);
        if(rows.size()>1)throw unavailable();if(rows.isEmpty())return false;
        final JSONObject article=rows.getFirst();
        final long type=ApiV3ProfessionPublicDto.number(article,"articleType",false),anonymousView=ApiV3ProfessionPublicDto.number(article,"articleAnonymousView",false);
        if(type>6||anonymousView>1)throw unavailable();
        Set<String> invited=Set.of();
        if(article.optInt("articleType")==Article.ARTICLE_TYPE_C_DISCUSSION){
            final long bytes=ApiV3ProfessionPublicDto.number(article,"bodyBytes",false);
            if(bytes>bodyBudget[0])throw unavailable();
            bodyBudget[0]-=bytes;
            final var bodies=articles.select("SELECT articleContent FROM `"+articles.getName()+"` WHERE oId=? AND articleType=1 AND OCTET_LENGTH(articleContent)=? AND SHA2(articleContent,256)=? LIMIT 2",id,bytes,article.getString("bodyHash"));
            if(bodies.size()!=1)throw unavailable();
            invited=UserMentionCandidates.parse(bodies.getFirst().getString("articleContent"));
        }
        return ApiV3ContentUtil.canViewArticle(article,actor,invited);
    }
    private boolean moonVisible(final String id,final JSONObject actor,final List<CheckedRead> checks)throws RepositoryException {
        final var rows=checked(checks,moons,"SELECT b.oId,b.breezemoonAuthorId,u.oId AS authorId,u.userStatus,u.userBreezemoonStatus FROM `"+moons.getName()+"` b JOIN `"+users.getName()
                +"` u ON u.oId=b.breezemoonAuthorId WHERE b.oId=? AND b.breezemoonStatus=0 AND u.userStatus IN (?,?) LIMIT 2",id,UserExt.USER_STATUS_C_VALID,UserExt.USER_STATUS_C_INVALID_LOGIN);
        if(rows.size()>1)throw unavailable();if(rows.isEmpty())return false;
        final JSONObject row=rows.getFirst();final long setting=ApiV3ProfessionPublicDto.number(row,"userBreezemoonStatus",false);
        if(setting>1)throw unavailable();
        return ApiV3ContentUtil.canViewUserList(new JSONObject().put("oId",row.getString("authorId")).put("userBreezemoonStatus",setting),actor,"userBreezemoonStatus");
    }
    private boolean commentVisible(final String id,final JSONObject actor,final long[] bodyBudget,final List<CheckedRead> checks)throws RepositoryException {
        final var rows=checked(checks,comments,"SELECT c.commentAuthorId,c.commentOnArticleId,c.commentVisible,u.userStatus AS authorStatus FROM `"+comments.getName()+"` c JOIN `"+users.getName()
                +"` u ON u.oId=c.commentAuthorId WHERE c.oId=? AND c.commentStatus=0 AND c.commentAnonymous=0 AND u.userStatus IN (?,?) LIMIT 2",id,UserExt.USER_STATUS_C_VALID,UserExt.USER_STATUS_C_INVALID_LOGIN);
        if(rows.size()>1)throw unavailable();if(rows.isEmpty())return false;
        final JSONObject row=rows.getFirst();
        final long commentVisible=ApiV3ProfessionPublicDto.number(row,"commentVisible",false);
        if(commentVisible>1)throw unavailable();
        if(!articleVisible(row.getString("commentOnArticleId"),actor,bodyBudget,checks))return false;
        if(commentVisible==0)return true;
        if(actor==null)return false;
        if(actor.getString("oId").equals(row.getString("commentAuthorId")))return true;
        return checked(checks,articles,"SELECT oId FROM `"+articles.getName()+"` WHERE oId=? AND articleAuthorId=? LIMIT 2",row.getString("commentOnArticleId"),actor.getString("oId")).size()==1;
    }
}
