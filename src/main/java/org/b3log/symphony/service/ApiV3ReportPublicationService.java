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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Comment;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** New reports and their owner-bound replay receipts share a strict root transaction. */
@Service
public class ApiV3ReportPublicationService {
    @Inject private UserRepository users;
    @Inject private ArticleRepository articles;
    @Inject private CommentRepository comments;
    @Inject private ChatRoomRepository chat;
    @Inject private ReportRepository reports;
    @Inject private ApiV3RequestRepository requests;
    private record Target(JSONObject row,JSONObject article,String author,Set<String> owners) { }
    protected String newId(){return Ids.genTimeMillisId();}
    public JSONObject publish(final String actor,final JSONObject body,final String key)throws Exception {
        id(actor);final JSONObject normalized=ApiV3ReportInputs.normalize(body);
        try {
            final Map<String,JSONObject> lockedUsers=new HashMap<>();
            Target initial;Exception preflightFailure=null;
            try { initial=target(normalized,false); }
            catch(Exception e) { preflightFailure=e;initial=null; }
            final Target hint=initial;final Exception hintFailure=preflightFailure;
            final var owners=new TreeSet<String>();owners.add(actor);if(hint!=null)owners.addAll(hint.owners());
            final ApiV3MutationStore store=new ApiV3MutationStore(){
                public Transaction beginTransaction(){lockedUsers.clear();return ApiV3Transactions.strict(requests.beginTransaction());}
                public boolean hasTransactionBegun(){return requests.hasTransactionBegun();}
                public JSONObject getForUpdate(String receipt)throws RepositoryException {
                    // Lock the fixed account set before the receipt; never expand locks from fresh targets.
                    for(final String owner:owners)lockedUsers.put(owner,lockUser(owner,owner.equals(actor)));
                    return requests.getForUpdate(receipt);
                }
                public String add(JSONObject row)throws RepositoryException{return requests.add(row);}
                public void update(String receipt,JSONObject row,String...fields)throws RepositoryException{requests.update(receipt,row,fields);}
            };
            final JSONObject durable=ApiV3IdempotencyMgmtService.runAtomic(store,actor,"report.publish",key,normalized,()->ApiV3TransactionSavepoint.run(()->{
                if(hint==null){if(hintFailure!=null)throw hintFailure;throw ApiV3Requests.notFound();}
                final Target current=target(normalized,true);
                if(!current.author().equals(hint.author()) || !current.owners().equals(hint.owners())
                        || !current.article().optString("oId").equals(hint.article().optString("oId"))) throw unavailable();
                for(final String owner:current.owners())if(lockedUsers.get(owner)==null || integer(lockedUsers.get(owner),"userStatus")!=UserExt.USER_STATUS_C_VALID)throw ApiV3Requests.notFound();
                if(actor.equals(current.author()))throw ApiV3Requests.notFound();
                final JSONObject viewer=lockedUsers.get(actor);
                final int type=normalized.getInt("targetType");
                if(type==0 || type==1) {
                    final JSONObject article=current.article();
                    final Set<String> invited=article.optInt("articleType")==Article.ARTICLE_TYPE_C_DISCUSSION
                            ? UserMentionCandidates.parse(article.getString("articleContent")):Set.of();
                    if(!ApiV3ContentUtil.canViewArticle(article,viewer,invited)
                            || type==1&&!ApiV3ContentUtil.canViewCommentContent(current.row(),article,viewer)) throw ApiV3Requests.notFound();
                }
                final var pending=reports.select("SELECT oId FROM `"+reports.getName()+"` WHERE reportUserId=? AND reportDataType=? AND reportDataId=? AND reportHandled=0 ORDER BY oId LIMIT 2 FOR UPDATE",actor,type,normalized.getString("targetId"));
                // Existing duplicate pending rows remain untouched; the oldest is the stable receipt.
                if(!pending.isEmpty())return result(pending.getFirst().getString("oId"));
                final String reportId=newId();id(reportId);
                if(ApiV3TransactionStatements.execute("INSERT INTO `"+reports.getName()+"` (oId,reportUserId,reportDataId,reportDataType,reportType,reportMemo,reportHandled) VALUES(?,?,?,?,?,?,0)",
                        reportId,actor,normalized.getString("targetId"),type,normalized.getInt("reason"),org.apache.commons.lang.StringEscapeUtils.escapeHtml(normalized.getString("memo")))!=1)throw unavailable();
                return result(reportId);
            }));
            return result(durable.getString("id"));
        }catch(ApiV3Exception e){throw e;}
        catch(Exception e){throw unavailable();}
    }
    private JSONObject lockUser(final String owner,final boolean required)throws RepositoryException {
        id(owner);
        final var rows=users.select("SELECT oId,userStatus,userName,userRole FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",owner);
        if(rows.isEmpty()&&!required)return null;
        if(rows.size()!=1 || !owner.equals(rows.getFirst().optString("oId")))throw ApiV3Requests.notFound();
        if(required&&integer(rows.getFirst(),"userStatus")!=UserExt.USER_STATUS_C_VALID)throw ApiV3Requests.notFound();
        return rows.getFirst();
    }
    private Target target(final JSONObject request,final boolean lock)throws RepositoryException {
        final int type=request.getInt("targetType");final String target=request.getString("targetId");
        if(type==2) return new Target(new JSONObject().put("oId",target),new JSONObject(),target,Set.of(target));
        final String suffix=lock?" FOR UPDATE":"";
        JSONObject row,article=new JSONObject();String author;
        if(type==3) {
            row=one(chat.select("SELECT oId,SUBSTR(content,1,102401) AS content FROM `"+chat.getName()+"` WHERE oId=?"+suffix,target));
            final String raw=row.getString("content");if(raw.length()>102400)throw unavailable();
            final JSONObject envelope;
            try{envelope=ApiV3Json.parse(raw.getBytes(StandardCharsets.UTF_8));}catch(IllegalArgumentException e){throw ApiV3Requests.notFound();}
            final Object owner=envelope.opt("userOId");
            if(owner instanceof String value)author=value;
            else if(owner instanceof Integer || owner instanceof Long || owner instanceof java.math.BigInteger)author=owner.toString();
            else throw ApiV3Requests.notFound();
            idOrNotFound(author);
            return new Target(row,article,author,Set.of(author));
        }
        if(type==1) {
            if(!lock)row=comment(target,false);
            else {
                // Parent before child follows article deletion order. Verify the hint again after locking.
                final JSONObject hint=comment(target,false);article=article(hint.getString("commentOnArticleId"),true);
                row=comment(target,true);if(!article.getString("oId").equals(row.getString("commentOnArticleId")))throw unavailable();
            }
            if(integer(row,"commentStatus")!=Comment.COMMENT_STATUS_C_VALID || !Set.of(0,1).contains(integer(row,"commentVisible")))throw ApiV3Requests.notFound();
            if(!lock)article=article(row.getString("commentOnArticleId"),false);author=row.getString("commentAuthorId");
        }else{article=article(target,lock);row=article;author=article.getString("articleAuthorId");}
        idOrNotFound(author);final String parentAuthor=article.getString("articleAuthorId");idOrNotFound(parentAuthor);
        final Set<String> owners=new HashSet<>();owners.add(author);owners.add(parentAuthor);
        return new Target(row,article,author,owners);
    }
    private JSONObject comment(final String target,final boolean lock)throws RepositoryException {
        idOrNotFound(target);
        return one(comments.select("SELECT oId,commentAuthorId,commentOnArticleId,commentStatus,commentVisible FROM `"+comments.getName()+"` WHERE oId=?"+(lock?" FOR UPDATE":""),target));
    }
    private JSONObject article(final String target,final boolean lock)throws RepositoryException {
        idOrNotFound(target);
        final JSONObject row=one(articles.select("SELECT oId,articleAuthorId,articleType,articleStatus,articleAnonymousView,CASE WHEN articleType=1 THEN SUBSTR(articleContent,1,1048577) ELSE '' END AS articleContent FROM `"+articles.getName()+"` WHERE oId=?"+(lock?" FOR UPDATE":""),target));
        if(!Set.of(Article.ARTICLE_STATUS_C_VALID,Article.ARTICLE_STATUS_C_LOCKED).contains(integer(row,"articleStatus")))throw ApiV3Requests.notFound();
        final int type=integer(row,"articleType");if(type<0||type>6)throw unavailable();
        if(!(row.opt("articleContent") instanceof String content)||content.length()>1048576)throw unavailable();
        return row;
    }
    private static JSONObject one(final List<JSONObject> rows){if(rows.size()!=1)throw ApiV3Requests.notFound();return rows.getFirst();}
    private static int integer(final JSONObject row,final String name){if(!(row.opt(name) instanceof Number n)||n.doubleValue()!=n.intValue())throw unavailable();return n.intValue();}
    private static JSONObject result(final String id){id(id);return new JSONObject().put("id",id);}
    private static void id(final String value){if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw ApiV3Requests.invalid();}
    private static void idOrNotFound(final String value){if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw ApiV3Requests.notFound();}
    private static ApiV3Exception unavailable(){return new ApiV3Exception(503,"temporarily_unavailable","举报提交暂不可用");}
}
