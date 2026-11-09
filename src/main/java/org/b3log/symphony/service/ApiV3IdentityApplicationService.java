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
import org.b3log.symphony.repository.ApiV3IdentityApplicationRepository;
import org.b3log.symphony.repository.ApiV3IdentityMaterialRepository;
import org.b3log.symphony.repository.ApiV3IdentityReviewerRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Base64;
import java.util.Set;

/** 管理私有企业认证审核记录，材料通过独立入口一次性递交。 */
@Service
public class ApiV3IdentityApplicationService {
    @Inject private ApiV3IdentityApplicationRepository applications;
    @Inject private ApiV3IdentityMaterialRepository materials;
    @Inject private ApiV3IdentityReviewerRepository reviewers;
    @Inject private ApiV3RequestRepository requests;
    @Inject private ApiV3SecurityProofService proofs;
    protected long now() {
        return System.currentTimeMillis();
    }

    private String columns() {
        return "oId,userId,type,status,createdAt,materialReadAt,reviewedAt,note";
    }
    public JSONObject create(JSONObject identity,byte[] bytes,String mediaType,String key) {
        final ApiV3IdentityImage.Image image=ApiV3IdentityImage.validate(bytes,mediaType);
        final String actor=actor(identity);
        final JSONObject normalized=new JSONObject().put("type","enterprise").put("mediaType",image.mediaType())
                .put("byteLength",image.bytes().length).put("sha256",image.sha256());
        try {
            final var receipt=ApiV3IdempotencyMgmtService.runAtomic(guarded(identity,"settings.write",false),actor,"identity.enterprise.create",key,normalized,()->{
                final var pending=applications.select("SELECT oId FROM `"+applications.getName()+"` WHERE pendingOwnerId=? FOR UPDATE",actor);
                if(!pending.isEmpty())throw new ApiV3Exception(409,"application_pending","已有待处理认证申请");
                final String id = Ids.genTimeMillisId();
                final long time = timestamp();
                change("INSERT INTO `"+applications.getName()+"` (oId,userId,type,status,pendingOwnerId,createdAt,materialReadAt,reviewedAt,note) VALUES(?,?,?,?,?,?,?,?,?)",
                        id,actor,"enterprise","pending",actor,time,0L,0L,"");
                change("INSERT INTO `"+materials.getName()+"` (oId,userId,mediaType,byteLength,sha256,data) VALUES(?,?,?,?,?,?)",
                        id,actor,image.mediaType(),image.bytes().length,image.sha256(),Base64.getEncoder().encodeToString(image.bytes()));
                return new JSONObject().put("id",id).put("createdAt",time);
            });
            if(!receipt.keySet().equals(Set.of("id","createdAt"))||!receipt.optString("id").matches("[1-9][0-9]{0,18}")||receipt.optLong("createdAt")<=0)throw unavailable();
            return new JSONObject().put("id",receipt.getString("id")).put("createdAt",receipt.getLong("createdAt"));
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        }
    }
    public JSONObject list(JSONObject identity,int page,int size,boolean review,String status) {
        if(page<1||page>Integer.MAX_VALUE/50||size<1||size>50)throw ApiV3WriteInputs.invalid();
        if(review&&!Set.of("pending","approved","rejected").contains(status))throw ApiV3WriteInputs.invalid();
        return read(identity,"profile.read",review,()->{
            final String where = review ? "status=?" : "userId=?";
            final String value = review ? status : actor(identity);
            final long count=applications.select("SELECT COUNT(*) AS total FROM `"+applications.getName()+"` WHERE "+where,value).getFirst().getLong("total");
            final var rows=applications.select("SELECT "+columns()+" FROM `"+applications.getName()+"` WHERE "+where+" ORDER BY oId DESC LIMIT ?,?",value,(page-1)*size,size);
            final JSONArray result = new JSONArray();
            for (JSONObject row : rows)
                result.put(project(row, review));
            return ApiV3Requests.pagination(result,page,size,count);
        });
    }
    public JSONObject detail(JSONObject identity,String id) {
        id(id);
        return read(identity,"profile.read",false,()->{
            final var rows=applications.select("SELECT "+columns()+" FROM `"+applications.getName()+"` WHERE oId=? AND userId=?",id,actor(identity));
            if (rows.size() != 1)
                throw ApiV3Requests.notFound();
            return project(rows.getFirst(), false);
        });
    }
    public record Opened(byte[] bytes,String mediaType) { }
    public JSONObject decide(JSONObject identity,String id,JSONObject body,String key) {
        id(id);
        if(body==null||!Set.of("status","note").containsAll(body.keySet())||!(body.opt("status") instanceof String status)
                ||!Set.of("approved","rejected").contains(status))throw ApiV3WriteInputs.invalid();
        final Object raw=body.has("note")?body.opt("note"):"";
        if(!(raw instanceof String input))throw ApiV3WriteInputs.invalid();
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (Character.isISOControl(c))
                throw ApiV3WriteInputs.invalid();
            if (Character.isHighSurrogate(c)) {
                if (++i >= input.length() || !Character.isLowSurrogate(input.charAt(i)))
                    throw ApiV3WriteInputs.invalid();
            } else if (Character.isLowSurrogate(c))
                throw ApiV3WriteInputs.invalid();
        }
        final String note=input.strip();
        if(note.length()>256||note.matches("(?s).*<[^>]*>.*"))throw ApiV3WriteInputs.invalid();
        final JSONObject normalized=new JSONObject().put("id",id).put("status",status).put("note",note);
        try{
            final JSONObject receipt=ApiV3IdempotencyMgmtService.runAtomic(guarded(identity,"settings.write",true),actor(identity),
                    "identity.enterprise.decide",key,normalized,()->{
                        final JSONObject application=application(id);
                        if(!"pending".equals(application.optString("status")))throw new ApiV3Exception(409,"application_reviewed","申请已有审核结论");
                        if(application.optLong("materialReadAt")<=0)throw new ApiV3Exception(409,"material_unread","请先读取证明图片");
                        if(!materials.select("SELECT oId FROM `"+materials.getName()+"` WHERE oId=? FOR UPDATE",id).isEmpty())throw unavailable();
                        final long time=timestamp();
                        change("UPDATE `"+applications.getName()+"` SET status=?,pendingOwnerId=NULL,reviewedAt=?,note=? WHERE oId=? AND status=? AND materialReadAt>0",
                                status,time,note,id,"pending");
                        return new JSONObject().put("id",id).put("status",status).put("reviewedAt",time).put("note",note);
                    });
            if(!receipt.keySet().equals(Set.of("id","status","reviewedAt","note"))||!id.equals(receipt.optString("id"))
                    ||!status.equals(receipt.optString("status"))||!note.equals(receipt.optString("note"))||receipt.optLong("reviewedAt")<=0)throw unavailable();
            return new JSONObject().put("id",id).put("status",status).put("reviewedAt",receipt.getLong("reviewedAt")).put("note",note);
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        }
    }
    /** POST 每次读取并提交时永久消费私有材料，因此不生成重放回执。 */
    public Opened open(JSONObject identity, String id) {
        id(id);
        Transaction transaction = null;
        try {
            transaction = begin();
            authorize(identity, "settings.write", true);
            final JSONObject application=application(id);
            if(!"pending".equals(application.optString("status"))||application.optLong("materialReadAt")!=0)throw gone();
            final var rows=materials.select("SELECT userId,mediaType,byteLength,sha256,data FROM `"+materials.getName()+"` WHERE oId=? FOR UPDATE",id);
            if (rows.size() != 1)
                throw unavailable();
            final JSONObject stored = rows.getFirst();
            if(!application.getString("userId").equals(stored.optString("userId")))throw unavailable();
            final int length = stored.getInt("byteLength");
            final String encoded = stored.getString("data");
            if(length<=0||length>ApiV3IdentityImage.MAX_BYTES||encoded.length()>((ApiV3IdentityImage.MAX_BYTES+2)/3)*4)throw unavailable();
            final byte[] decoded=Base64.getDecoder().decode(encoded);
            if(decoded.length!=length||!ApiV3IdentityImage.hash(decoded).equals(stored.optString("sha256"))
                    ||!Set.of("image/jpeg","image/png").contains(stored.optString("mediaType")))throw unavailable();
            // 材料删除和已读标记一起提交，提交成功后才将材料交给处理器。
            change("DELETE FROM `"+materials.getName()+"` WHERE oId=? AND userId=?",id,application.getString("userId"));
            change("UPDATE `"+applications.getName()+"` SET materialReadAt=? WHERE oId=? AND status=? AND materialReadAt=0",timestamp(),id,"pending");
            transaction.commit();
            return new Opened(decoded,stored.getString("mediaType"));
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            if (transaction != null && transaction.isActive())
                transaction.rollback();
        }
    }
    private ApiV3MutationStore guarded(JSONObject identity,String scope,boolean review) {
        return new ApiV3MutationStore(){
            public Transaction beginTransaction() {
                return begin();
            }

            public boolean hasTransactionBegun() {
                return requests.hasTransactionBegun();
            }

            public JSONObject getForUpdate(String id) throws RepositoryException {
                authorize(identity, scope, review);
                return requests.getForUpdate(id);
            }

            public String add(JSONObject row) throws RepositoryException {
                return requests.add(row);
            }

            public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                requests.update(id, row, fields);
            }
        };
    }
    private void authorize(JSONObject identity,String scope,boolean review)throws RepositoryException {
        final JSONObject current=proofs.authorizeFirstPartyInCurrentTransaction(identity,scope);
        if(!actor(identity).equals(current.optString("oId")))throw ApiV3Requests.forbidden();
        if(review){
            final var bindings=reviewers.select("SELECT userId FROM `"+reviewers.getName()+"` WHERE oId=?","enterprise");
            if(bindings.size()!=1||!current.getString("oId").equals(bindings.getFirst().optString("userId")))throw ApiV3Requests.forbidden();
        }
    }
    private JSONObject application(String id)throws RepositoryException {
        final var rows=applications.select("SELECT "+columns()+" FROM `"+applications.getName()+"` WHERE oId=? FOR UPDATE",id);
        if (rows.size() != 1)
            throw ApiV3Requests.notFound();
        return rows.getFirst();
    }
    private JSONObject project(JSONObject row,boolean review) {
        final String state = row.optString("status");
        if (!Set.of("pending", "approved", "rejected").contains(state) || !"enterprise".equals(row.optString("type")))
            throw unavailable();
        final JSONObject result=new JSONObject().put("id",row.getString("oId")).put("type","enterprise").put("status",state)
                .put("createdAt",row.getLong("createdAt")).put("materialReadAt",row.getLong("materialReadAt"))
                .put("reviewedAt",row.getLong("reviewedAt")).put("note",row.getString("note"));
        if (review)
            result.put("applicantId", row.getString("userId"));
        return result;
    }

    @FunctionalInterface
    private interface Read {
        JSONObject run() throws Exception;
    }
    private JSONObject read(JSONObject identity,String scope,boolean review,Read work) {
        Transaction transaction=null;
        try {
            transaction = begin();
            authorize(identity, scope, review);
            final JSONObject result = work.run();
            transaction.commit();
            return result;
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            if (transaction != null && transaction.isActive())
                transaction.rollback();
        }
    }

    private long timestamp() {
        final long value = now();
        if (value <= 0 || value > 9_007_199_254_740_991L)
            throw unavailable();
        return value;
    }

    private Transaction begin() {
        if (requests.hasTransactionBegun())
            throw new IllegalStateException("identity workflow root required");
        return ApiV3Transactions.strict(requests.beginTransaction());
    }

    private static void change(String sql, Object... args) throws RepositoryException {
        if (ApiV3TransactionStatements.execute(sql, args) != 1)
            throw unavailable();
    }

    private static String actor(JSONObject identity) {
        final var user = identity == null ? null : identity.optJSONObject("user");
        final String value = user == null ? "" : user.optString("oId");
        if (!value.matches("[1-9][0-9]{0,18}"))
            throw ApiV3Requests.forbidden();
        return value;
    }
    private static void id(String id){if(id==null||!id.matches("[1-9][0-9]{0,18}"))throw ApiV3WriteInputs.invalid();}
    private static ApiV3Exception gone() {
        return new ApiV3Exception(410, "material_consumed", "证明图片已被读取或已不可用");
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "认证申请暂不可用");
    }
}
