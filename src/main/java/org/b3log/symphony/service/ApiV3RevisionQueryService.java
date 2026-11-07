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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-only current authorization and bounded revision snapshots, without repository caches. */
@Service
public class ApiV3RevisionQueryService {
    private static final int MAX_REVISIONS=4096,MAX_BODY=1048576,MAX_HISTORY=8388608,HASH_BATCH=128;
    @Inject private UserRepository users;
    @Inject private ArticleRepository articles;
    @Inject private CommentRepository comments;
    @Inject private RevisionRepository revisions;
    @Inject private RolePermissionRepository permissions;
    private record Snapshot(JSONObject actor,JSONObject article,JSONObject comment,List<JSONObject> owners,List<JSONObject> grants,List<JSONObject> metas){ }
    public JSONObject list(final String actor,final int type,final String target,final int page,final int size) {
        inputs(actor,type,target);if(page<1||page>Integer.MAX_VALUE/50||size<1||size>50)throw ApiV3Requests.invalid();
        return execute(actor,type,target,null,page,size);
    }
    public JSONObject detail(final String actor,final int type,final String target,final String revision) {
        inputs(actor,type,target);if(!"current".equals(revision))id(revision);
        return execute(actor,type,target,revision,1,20);
    }
    private JSONObject execute(final String actor,final int type,final String target,final String revision,final int page,final int size) {
        try{
            final Snapshot initial=snapshot(actor,type,target);
            final List<JSONObject> items=new ArrayList<>();
            int index=0;
            for(final JSONObject meta:initial.metas())items.add(ApiV3RevisionDto.metadata(meta.getString("oId"),time(meta.getString("oId")),++index));
            final JSONObject current=type==0?initial.article():initial.comment();
            final JSONObject latest=initial.metas().isEmpty()?null:body(initial.metas().getLast(),type,target);
            if(latest!=null&&changed(latest,current,type))items.add(ApiV3RevisionDto.metadata("current",type==0?integer(current,"articleUpdateTime"):null,++index));
            final JSONObject result;
            if(revision==null){
                final long start=(long)(page-1)*size;final JSONArray selected=new JSONArray();
                for(long i=start;i<Math.min(start+size,items.size());i++)selected.put(items.get((int)i));
                result=new JSONObject().put("items",selected).put("pagination",new JSONObject().put("page",page).put("size",size).put("total",items.size()).put("pageCount",(items.size()+size-1)/size));
            }else{
                JSONObject meta=null;for(final JSONObject item:items)if(revision.equals(item.getString("id"))){meta=item;break;}
                if(meta==null)throw ApiV3Requests.notFound();
                final JSONObject data;
                if("current".equals(revision))data=current;
                else{JSONObject stored=null;for(final JSONObject m:initial.metas())if(revision.equals(m.getString("oId"))){stored=m;break;}if(stored==null)throw ApiV3Requests.notFound();data=body(stored,type,target);}
                result=ApiV3RevisionDto.detail(meta,data,type==0);
            }
            final Snapshot after=snapshot(actor,type,target);
            if(!fingerprint(initial).equals(fingerprint(after)))throw ApiV3RevisionDto.unavailable();
            return result;
        }catch(ApiV3Exception e){throw e;}catch(Exception e){throw ApiV3RevisionDto.unavailable();}
    }
    private Snapshot snapshot(final String actor,final int type,final String target)throws RepositoryException {
        final JSONObject viewer=user(actor,true);
        final JSONObject comment=type==1?one(comments.select("SELECT oId,commentAuthorId,commentOnArticleId,commentStatus,commentVisible,commentCreateTime,CHAR_LENGTH(commentContent) AS contentLength,OCTET_LENGTH(commentContent) AS contentBytes,CASE WHEN OCTET_LENGTH(commentContent)<=1048576 THEN SHA2(commentContent,256) ELSE NULL END AS contentHash,SUBSTR(commentContent,1,1048577) AS commentContent FROM `"+comments.getName()+"` WHERE oId=?",target)):new JSONObject();
        if(type==1){if(integer(comment,"commentStatus")!=0||!Set.of(0L,1L).contains(integer(comment,"commentVisible")))throw ApiV3Requests.notFound();textBudget(comment,"commentContent","contentLength");}
        final String articleId=type==0?target:text(comment,"commentOnArticleId");storedId(articleId);
        final JSONObject article=one(articles.select("SELECT oId,articleAuthorId,articleStatus,articleType,articleAnonymousView,articleUpdateTime,CHAR_LENGTH(articleContent) AS contentLength,OCTET_LENGTH(articleContent) AS contentBytes,CASE WHEN OCTET_LENGTH(articleContent)<=1048576 THEN SHA2(articleContent,256) ELSE NULL END AS contentHash,SUBSTR(articleContent,1,1048577) AS articleContent,SUBSTR(articleTitle,1,8193) AS articleTitle FROM `"+articles.getName()+"` WHERE oId=?",articleId));
        if(!Set.of(0L,2L).contains(integer(article,"articleStatus")))throw ApiV3Requests.notFound();
        if(integer(article,"articleType")<0||integer(article,"articleType")>6)throw ApiV3RevisionDto.unavailable();
        if(text(article,"articleTitle").length()>8192)throw ApiV3RevisionDto.unavailable();textBudget(article,"articleContent","contentLength");
        final List<JSONObject> owners=new ArrayList<>();owners.add(user(text(article,"articleAuthorId"),false));
        if(type==1&&!text(comment,"commentAuthorId").equals(text(article,"articleAuthorId")))owners.add(user(text(comment,"commentAuthorId"),false));
        final Set<String> invited=integer(article,"articleType")==1?UserMentionCandidates.parse(text(article,"articleContent")):Set.of();
        if(!ApiV3ContentUtil.canViewArticle(article,viewer,invited)||type==1&&!ApiV3ContentUtil.canViewCommentContent(comment,article,viewer))throw ApiV3Requests.notFound();
        final String owner=text(type==0?article:comment,type==0?"articleAuthorId":"commentAuthorId");
        final List<JSONObject> grants=new ArrayList<>();
        if(!actor.equals(owner)){
            final String role=text(viewer,"userRole");final String permission=type==0?"commonViewArticleHistory":"commonViewCommentHistory";
            if(role.isEmpty()||role.length()>128)throw ApiV3RevisionDto.unavailable();
            grants.addAll(permissions.select("SELECT oId,roleId,permissionId FROM `"+permissions.getName()+"` WHERE roleId=? AND permissionId=? ORDER BY oId LIMIT 3",role,permission));
            if(grants.size()>2)throw ApiV3RevisionDto.unavailable();
            boolean allowed=false;for(final JSONObject grant:grants)if(role.equals(text(grant,"roleId"))&&permission.equals(text(grant,"permissionId")))allowed=true;
            if(!allowed)throw new ApiV3Exception(403,"forbidden","没有历史查看权限");
        }
        final List<JSONObject> metas=revisions.select("SELECT oId,revisionDataId,revisionDataType,CHAR_LENGTH(revisionData) AS dataLength,OCTET_LENGTH(revisionData) AS dataBytes FROM `"+revisions.getName()+"` WHERE revisionDataId=? AND revisionDataType=? ORDER BY oId LIMIT 4097",target,type);
        if(metas.size()>MAX_REVISIONS)throw ApiV3RevisionDto.unavailable();long total=0;final Set<String> seen=new HashSet<>();
        for(final JSONObject row:metas){final String revision=text(row,"oId");time(revision);if(!seen.add(revision)||!target.equals(text(row,"revisionDataId"))||integer(row,"revisionDataType")!=type)throw ApiV3RevisionDto.unavailable();
            final long length=integer(row,"dataLength"),bytes=integer(row,"dataBytes");if(length<0||length>MAX_BODY||bytes<0||bytes>MAX_BODY||(total+=bytes)>MAX_HISTORY)throw ApiV3RevisionDto.unavailable();}
        hashMetadata(metas,type,target);
        return new Snapshot(viewer,article,comment,owners,grants,metas);
    }
    /** The first pass caps total hash input; each CASE rebinds its exact row size before SHA2. */
    private void hashMetadata(final List<JSONObject> metas,final int type,final String target)throws RepositoryException {
        for(int offset=0;offset<metas.size();offset+=HASH_BATCH){
            final List<JSONObject> batch=metas.subList(offset,Math.min(offset+HASH_BATCH,metas.size()));
            final StringBuilder expression=new StringBuilder("CASE");
            final List<Object> args=new ArrayList<>();
            for(final JSONObject expected:batch){
                expression.append(" WHEN oId=? AND revisionDataId=? AND revisionDataType=? AND CHAR_LENGTH(revisionData)=? AND OCTET_LENGTH(revisionData)=? THEN SHA2(revisionData,256)");
                args.add(text(expected,"oId"));args.add(target);args.add(type);args.add(integer(expected,"dataLength"));args.add(integer(expected,"dataBytes"));
            }
            expression.append(" ELSE NULL END AS dataHash");args.add(target);args.add(type);
            final String placeholders=String.join(",",Collections.nCopies(batch.size(),"?"));
            for(final JSONObject expected:batch)args.add(text(expected,"oId"));
            final List<JSONObject> actual=revisions.select("SELECT oId,revisionDataId,revisionDataType,CHAR_LENGTH(revisionData) AS dataLength,OCTET_LENGTH(revisionData) AS dataBytes,"+expression+" FROM `"+revisions.getName()+"` WHERE revisionDataId=? AND revisionDataType=? AND oId IN ("+placeholders+") ORDER BY oId",args.toArray());
            if(actual.size()!=batch.size())throw ApiV3RevisionDto.unavailable();
            final Map<String,JSONObject> byId=new HashMap<>();for(final JSONObject row:actual)if(byId.put(text(row,"oId"),row)!=null)throw ApiV3RevisionDto.unavailable();
            for(final JSONObject expected:batch){
                final JSONObject row=byId.get(text(expected,"oId"));if(row==null)throw ApiV3RevisionDto.unavailable();
                for(final String field:List.of("oId","revisionDataId","revisionDataType","dataLength","dataBytes"))if(!Objects.equals(expected.opt(field),row.opt(field)))throw ApiV3RevisionDto.unavailable();
                final String hash=text(row,"dataHash");if(!hash.matches("[a-fA-F0-9]{64}"))throw ApiV3RevisionDto.unavailable();expected.put("dataHash",hash);
            }
        }
    }
    private JSONObject body(final JSONObject meta,final int type,final String target)throws RepositoryException {
        final JSONObject row=one(revisions.select("SELECT oId,revisionDataId,revisionDataType,CHAR_LENGTH(revisionData) AS dataLength,OCTET_LENGTH(revisionData) AS dataBytes,CASE WHEN CHAR_LENGTH(revisionData)=? AND OCTET_LENGTH(revisionData)=? THEN SHA2(revisionData,256) ELSE NULL END AS dataHash,CASE WHEN CHAR_LENGTH(revisionData)=? AND OCTET_LENGTH(revisionData)=? THEN SUBSTR(revisionData,1,1048577) ELSE NULL END AS revisionData FROM `"+revisions.getName()+"` WHERE oId=? AND revisionDataId=? AND revisionDataType=?",integer(meta,"dataLength"),integer(meta,"dataBytes"),integer(meta,"dataLength"),integer(meta,"dataBytes"),text(meta,"oId"),target,type));
        for(final String field:List.of("oId","revisionDataId","revisionDataType","dataLength","dataBytes","dataHash"))if(!Objects.equals(meta.opt(field),row.opt(field)))throw ApiV3RevisionDto.unavailable();
        final String raw=text(row,"revisionData");if(raw.length()>MAX_BODY||raw.codePointCount(0,raw.length())!=integer(row,"dataLength")||raw.getBytes(StandardCharsets.UTF_8).length!=integer(row,"dataBytes"))throw ApiV3RevisionDto.unavailable();
        final JSONObject parsed;try{parsed=ApiV3Json.parse(raw.getBytes(StandardCharsets.UTF_8));}catch(IllegalArgumentException e){throw ApiV3RevisionDto.unavailable();}
        ApiV3RevisionDto.text(parsed,type==0?"articleTitle":"commentContent");if(type==0)ApiV3RevisionDto.text(parsed,"articleContent");return parsed;
    }
    private JSONObject user(final String owner,final boolean actor)throws RepositoryException {
        storedId(owner);final JSONObject row=one(users.select("SELECT oId,userStatus,userName,userRole FROM `"+users.getName()+"` WHERE oId=?",owner));
        final long status=integer(row,"userStatus");if(actor?status!=0:!Set.of(0L,2L,3L).contains(status))throw ApiV3Requests.notFound();return row;
    }
    private static boolean changed(final JSONObject old,final JSONObject current,final int type){for(final String field:type==0?List.of("articleTitle","articleContent"):List.of("commentContent"))if(!ApiV3RevisionDto.text(old,field).replaceAll("\\s+","").equals(ApiV3RevisionDto.text(current,field).replaceAll("\\s+","")))return true;return false;}
    private static String fingerprint(final Snapshot s){return ApiV3Json.canonical(new JSONObject().put("actor",s.actor()).put("article",s.article()).put("comment",s.comment()).put("owners",s.owners()).put("grants",s.grants()).put("metas",s.metas()));}
    private static void textBudget(final JSONObject row,final String text,final String length){final String value=text(row,text);if(value.length()>MAX_BODY||integer(row,length)<0||integer(row,length)>MAX_BODY||integer(row,"contentBytes")<0||integer(row,"contentBytes")>MAX_BODY||value.getBytes(StandardCharsets.UTF_8).length!=integer(row,"contentBytes")||value.codePointCount(0,value.length())!=integer(row,length)||!text(row,"contentHash").matches("[a-fA-F0-9]{64}"))throw ApiV3RevisionDto.unavailable();}
    private static long integer(final JSONObject row,final String field){if(!(row.opt(field) instanceof Number n))throw ApiV3RevisionDto.unavailable();try{return new java.math.BigDecimal(n.toString()).longValueExact();}catch(ArithmeticException e){throw ApiV3RevisionDto.unavailable();}}
    private static String text(final JSONObject row,final String field){if(!(row.opt(field) instanceof String text))throw ApiV3RevisionDto.unavailable();return text;}
    private static JSONObject one(final List<JSONObject> rows){if(rows.size()!=1)throw ApiV3Requests.notFound();return rows.getFirst();}
    private static void storedId(final String value){if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw ApiV3RevisionDto.unavailable();}
    private static long time(final String value){storedId(value);try{return Long.parseLong(value);}catch(NumberFormatException e){throw ApiV3RevisionDto.unavailable();}}
    private static void id(final String value){if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw ApiV3Requests.invalid();}
    private static void inputs(final String actor,final int type,final String target){id(actor);id(target);if(type!=0&&type!=1)throw ApiV3Requests.invalid();}
}
