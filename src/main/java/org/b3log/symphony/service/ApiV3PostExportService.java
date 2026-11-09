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
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 私人导出文件和费用在同一个严格数据库事务中提交。 */
@Service
public class ApiV3PostExportService {
    @Inject private ApiV3SecurityProofService proofs;
    @Inject private UserRepository users;
    @Inject private ApiV3PostExportSnapshotService snapshots;
    @Inject private ApiV3PostExportRepository exports;
    @Inject private ApiV3PostExportChunkRepository chunks;
    @Inject private ApiV3RequestRepository requests;
    @Inject private PointtransferMgmtService points;
    @Inject private PointtransferRepository transfers;
    @Inject private PointtransferRedcRepository index;
    private static final String OPERATION="posts.export";
    private static final long RETENTION=86_400_000L;
    private final AtomicBoolean started=new AtomicBoolean();
    protected long now(){return System.currentTimeMillis();}
    public void startCleanup(){
        if(started.compareAndSet(false,true))Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("v3-post-export-cleanup").factory()).scheduleWithFixedDelay(()->{
            try{for(int batch=0;batch<20;batch++)if(purgeExpired()<100)break;}
            catch(Exception ignored){org.apache.logging.log4j.LogManager.getLogger(getClass()).warn("Private export cleanup delayed");}
            finally{try{ApiV3Transactions.releaseThreadResources();}catch(RuntimeException ignored){}}
        },1,1,TimeUnit.MINUTES);
    }
    public JSONObject create(JSONObject identity,JSONObject body,String key,String ip)throws Exception {
        if(body==null||!Set.of("currentPassword","currentTotp").containsAll(body.keySet()))throw ApiV3Requests.invalid();
        final String password=ApiV3SecurityInputs.digest(body.opt("currentPassword")),totp=ApiV3SecurityInputs.totp(body.opt("currentTotp"));
        if(key==null||!key.matches("[A-Za-z0-9._:-]{1,128}"))throw ApiV3Requests.invalid();
        proofs.checkAttempts(identity, ip);
        noRoot();
        final String actor = actor(identity), receiptId = ApiV3TokenUtil.hash("request-key\0" + actor + "\0" + OPERATION + "\0" + key);
        final JSONObject normalized = new JSONObject().put("formatVersion", 1);
        final String requestHash = ApiV3TokenUtil.hash("request-body\0" + ApiV3Json.canonical(normalized));
        final String initialBinding;
        JSONObject previous;
        Transaction tx = begin();
        try {
            initialBinding=binding(requireCreation(identity,password,totp));
            previous=requests.getForUpdate(receiptId);
            if(previous!=null&&!requestHash.equals(previous.optString("requestHash")))throw new ApiV3Exception(409,"idempotency_conflict","相同幂等键不能用于不同请求内容");
            tx.commit();
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            throw unavailable();
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
        if (previous != null) {
            try {
                return replay(identity, actor, new JSONObject(previous.getString("responseJson")), key);
            } catch (ApiV3Exception e) {
                throw e;
            } catch (Exception e) {
                throw unavailable();
            }
        }
        try (var files = new ApiV3PostExportFiles()) {
            // 先在独立的读取事务中生成一致性快照，再进入积分与文件写入事务。
            final var snapshot=snapshots.generate(actor,files,clock());
            final String jobId=Ids.genTimeMillisId(),billingId=billing(actor,key);
            final JSONObject receipt=points.executeWithTransferLocks(actor,List.of(Pointtransfer.ID_C_SYS),()->{
                try(final var caches=ArticlePublicationCaches.open(requests::hasTransactionBegun)) {
                    final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                        JSONObject current;

                        public Transaction beginTransaction() {
                            return ApiV3Transactions.strict(requests.beginTransaction());
                        }

                        public boolean hasTransactionBegun() {
                            return requests.hasTransactionBegun();
                        }

                        public JSONObject getForUpdate(String id) throws RepositoryException {
                            current = requireCreation(identity, password, totp);
                            return requests.getForUpdate(id);
                        }

                        public String add(JSONObject row) throws RepositoryException {
                            return requests.add(row);
                        }

                        public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                            requests.update(id, row, fields);
                        }
                    };
                    return ApiV3IdempotencyMgmtService.runAtomic(guarded,actor,OPERATION,key,normalized,()->{
                        // 扣费事务内重新读取凭据，并与生成快照前的凭据绑定比对。
                        final JSONObject current=requireCreation(identity,password,totp);
                        if(!initialBinding.equals(binding(current)))throw new ApiV3Exception(409,"security_changed","账号安全状态已变化，请重新发起导出");
                        final int price = Pointtransfer.TRANSFER_SUM_C_DATA_EXPORT;
                        if (price <= 0)
                            throw unavailable();
                        var balances=users.select("SELECT userPoint FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",actor);
                        if(balances.size()!=1)throw unavailable();
                        if(ApiV3PostExportSnapshotService.number(balances.getFirst(),"userPoint")<price)throw new ApiV3Exception(409,"insufficient_points","积分不足");
                        final long readyAt = clock(), expiresAt = Math.addExact(readyAt, RETENTION);
                        int count = writeChunks(actor, jobId, files, snapshot);
                        final String transfer=points.transferInCurrentTransaction(actor,Pointtransfer.ID_C_SYS,Pointtransfer.TRANSFER_TYPE_C_DATA_EXPORT,price,jobId,readyAt,"导出本人文章和评论",new PointtransferSource("","",OPERATION,billingId),false);
                        if(transfer==null||!transfer.matches("[1-9][0-9]{0,18}"))throw unavailable();
                        if(ApiV3TransactionStatements.execute("INSERT INTO `"+exports.getName()+"` (oId,userId,createdAt,capturedAt,expiresAt,articleCount,commentCount,sourceBytes,byteLength,sha256,chunkCount,paidPoints) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",jobId,actor,readyAt,snapshot.capturedAt(),expiresAt,snapshot.articles(),snapshot.comments(),snapshot.sourceBytes(),snapshot.bytes(),snapshot.sha256(),count,price)!=1)throw unavailable();
                        if(!initialBinding.equals(binding(requireCreation(identity,password,totp))))throw new ApiV3Exception(409,"security_changed","账号安全状态已变化，请重新发起导出");
                        return new JSONObject().put("jobId",jobId).put("paidPoints",price).put("indexTransferId",transfer);
                    });
                }
            });
            return replay(identity,actor,receipt,key);
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            throw unavailable();
        }
    }
    private JSONObject replay(JSONObject identity,String actor,JSONObject receipt,String key)throws Exception {
        if(!receipt.keySet().equals(Set.of("jobId","paidPoints","indexTransferId")))throw unavailable();
        final String job=ApiV3PostExportSnapshotService.id(receipt.opt("jobId")),transfer=ApiV3PostExportSnapshotService.id(receipt.opt("indexTransferId"));
        final long price=ApiV3PostExportSnapshotService.number(receipt,"paidPoints");if(price<=0||price>Integer.MAX_VALUE)throw unavailable();
        repairIndex(actor,billing(actor,key),transfer);
        try{return status(identity,job);}catch(ApiV3Exception e){if(e.status==404||e.status==410)return new JSONObject().put("jobId",job).put("status","expired").put("paidPoints",price);throw e;}
    }
    private int writeChunks(String actor,String job,ApiV3PostExportFiles files,ApiV3PostExportSnapshotService.Snapshot snapshot)throws Exception {
        if(Files.size(files.zip)!=snapshot.bytes()||!ApiV3PostExportFiles.sha256(files.zip).equals(snapshot.sha256()))throw unavailable();
        int seq=0;long total=0;
        try(var in=Files.newInputStream(files.zip)){
            byte[] data;while((data=in.readNBytes(ApiV3PostExportFiles.CHUNK_BYTES)).length>0){
                final String id=ApiV3TokenUtil.hash("post-export-chunk\0"+job+"\0"+seq);
                if(ApiV3TransactionStatements.execute("INSERT INTO `"+chunks.getName()+"` (oId,jobId,userId,sequenceNo,byteLength,data) VALUES(?,?,?,?,?,?)",id,job,actor,seq,data.length,Base64.getEncoder().encodeToString(data))!=1)throw unavailable();
                seq++;total+=data.length;
            }
        }
        if(total!=snapshot.bytes()||seq<1)throw unavailable();return seq;
    }
    public JSONObject status(JSONObject identity,String job)throws Exception {
        noRoot();try {proofs.read(identity);JSONObject row=find(actor(identity),job,false);JSONObject result=project(row);proofs.read(identity);return result;}
        catch(ApiV3Exception e){throw e;}catch(Exception e){throw unavailable();}
    }
    public byte[] download(JSONObject identity,String job)throws Exception {
        noRoot();try {
            proofs.read(identity);final String actor=actor(identity);JSONObject row=find(actor,job,false);
            final long expected=ApiV3PostExportSnapshotService.number(row,"byteLength"),count=ApiV3PostExportSnapshotService.number(row,"chunkCount");
            if(expected<=0||expected>ApiV3PostExportFiles.ZIP_LIMIT||count!=(expected+ApiV3PostExportFiles.CHUNK_BYTES-1)/ApiV3PostExportFiles.CHUNK_BYTES)throw unavailable();
            final var output=new ByteArrayOutputStream((int)expected);
            for(int seq=0;seq<count;seq++){
                final var found=chunks.select("SELECT oId,jobId,userId,sequenceNo,byteLength,data FROM `"+chunks.getName()+"` WHERE jobId=? AND userId=? AND sequenceNo=? LIMIT 2",job,actor,seq);
                if(found.size()!=1)throw unavailable();var chunk=found.getFirst();
                if(!ApiV3TokenUtil.hash("post-export-chunk\0"+job+"\0"+seq).equals(chunk.optString("oId"))||!job.equals(chunk.optString("jobId"))||!actor.equals(chunk.optString("userId"))||ApiV3PostExportSnapshotService.number(chunk,"sequenceNo")!=seq)throw unavailable();
                String encoded=ApiV3PostExportSnapshotService.text(chunk,"data");if(encoded.length()>65536)throw unavailable();byte[] data=Base64.getDecoder().decode(encoded);
                int size=seq==count-1?(int)(expected-(count-1)*ApiV3PostExportFiles.CHUNK_BYTES):ApiV3PostExportFiles.CHUNK_BYTES;
                if(data.length!=size||ApiV3PostExportSnapshotService.number(chunk,"byteLength")!=size||!Base64.getEncoder().encodeToString(data).equals(encoded))throw unavailable();output.write(data);
            }
            final var counts=chunks.select("SELECT COUNT(*) AS total FROM `"+chunks.getName()+"` WHERE jobId=? AND userId=?",job,actor);
            if(counts.size()!=1||ApiV3PostExportSnapshotService.number(counts.getFirst(),"total")!=count)throw unavailable();
            byte[] result=output.toByteArray();if(result.length!=expected||!ApiV3PostExportFiles.sha256(result).equals(ApiV3PostExportSnapshotService.text(row,"sha256")))throw unavailable();
            // 文件组装完成后再次确认第一方授权、有效期和元数据，然后交由处理器发送。
            proofs.read(identity);
            JSONObject after = find(actor, job, false);
            if (!ApiV3Json.canonical(row).equals(ApiV3Json.canonical(after)))
                throw unavailable();
            return result;
        }catch(ApiV3Exception e){throw e;}catch(Exception e){throw unavailable();}
    }
    private JSONObject find(String actor,String job,boolean locked)throws Exception {
        if(job==null||!job.matches("[1-9][0-9]{0,18}"))throw ApiV3Requests.invalid();
        var found=exports.select("SELECT oId,userId,createdAt,capturedAt,expiresAt,articleCount,commentCount,sourceBytes,byteLength,sha256,chunkCount,paidPoints FROM `"+exports.getName()+"` WHERE oId=? AND userId=?"+(locked?" FOR UPDATE":""),job,actor);
        if(found.isEmpty())throw ApiV3Requests.notFound();if(found.size()!=1)throw unavailable();JSONObject row=found.getFirst();
        if(!job.equals(row.optString("oId"))||!actor.equals(row.optString("userId")))throw unavailable();project(row);
        if(ApiV3PostExportSnapshotService.number(row,"expiresAt")<=clock())throw new ApiV3Exception(410,"export_expired","导出文件已过期");return row;
    }
    private JSONObject project(JSONObject row){
        long created=ApiV3PostExportSnapshotService.number(row,"createdAt"),captured=ApiV3PostExportSnapshotService.number(row,"capturedAt"),expiry=ApiV3PostExportSnapshotService.number(row,"expiresAt"),articles=ApiV3PostExportSnapshotService.number(row,"articleCount"),comments=ApiV3PostExportSnapshotService.number(row,"commentCount"),source=ApiV3PostExportSnapshotService.number(row,"sourceBytes"),bytes=ApiV3PostExportSnapshotService.number(row,"byteLength"),count=ApiV3PostExportSnapshotService.number(row,"chunkCount"),price=ApiV3PostExportSnapshotService.number(row,"paidPoints");
        if(captured<=0||created<captured||expiry-created!=RETENTION||articles<0||comments<0||source<0||source>ApiV3PostExportFiles.SOURCE_LIMIT||bytes<=0||bytes>ApiV3PostExportFiles.ZIP_LIMIT||count!=(bytes+ApiV3PostExportFiles.CHUNK_BYTES-1)/ApiV3PostExportFiles.CHUNK_BYTES||price<=0||!ApiV3PostExportSnapshotService.text(row,"sha256").matches("[0-9a-f]{64}"))throw unavailable();
        return new JSONObject().put("jobId",ApiV3PostExportSnapshotService.id(row.opt("oId"))).put("status","ready").put("createdAt",created).put("capturedAt",captured).put("expiresAt",expiry).put("articleCount",articles).put("commentCount",comments).put("byteLength",bytes).put("sha256",row.getString("sha256")).put("paidPoints",price);
    }
    public int purgeExpired()throws Exception {
        noRoot();Transaction tx=ApiV3Transactions.strict(exports.beginTransaction());
        try {
            var expired=exports.select("SELECT oId,userId FROM `"+exports.getName()+"` WHERE expiresAt<=? ORDER BY oId ASC LIMIT 100 FOR UPDATE",clock());
            for(JSONObject row:expired){String job=ApiV3PostExportSnapshotService.id(row.opt("oId")),actor=ApiV3PostExportSnapshotService.id(row.opt("userId"));ApiV3TransactionStatements.execute("DELETE FROM `"+chunks.getName()+"` WHERE jobId=? AND userId=?",job,actor);if(ApiV3TransactionStatements.execute("DELETE FROM `"+exports.getName()+"` WHERE oId=? AND userId=?",job,actor)!=1)throw unavailable();}
            tx.commit();return expired.size();
        }finally{if(tx.isActive())tx.rollback();}
    }
    private void repairIndex(String actor,String billing,String transfer) {
        Transaction tx=null;try{noRoot();tx=ApiV3Transactions.strict(transfers.beginTransaction());
            var found=transfers.select("SELECT oId FROM `"+transfers.getName()+"` WHERE oId=? AND fromId=? AND toId=? AND type=? AND sourceScene=? AND sourceRequestId=? LIMIT 2 FOR UPDATE",transfer,actor,Pointtransfer.ID_C_SYS,Pointtransfer.TRANSFER_TYPE_C_DATA_EXPORT,OPERATION,billing);
            if(found.size()!=1||!transfer.equals(found.getFirst().optString("oId")))throw unavailable();
            for(String user:List.of(actor,Pointtransfer.ID_C_SYS)){var existing=index.select("SELECT oId FROM `"+index.getName()+"` WHERE userId=? AND rawId=? LIMIT 2 FOR UPDATE",user,transfer);if(existing.isEmpty()&&ApiV3TransactionStatements.execute("INSERT INTO `"+index.getName()+"` (oId,userId,rawId) VALUES(?,?,?)",Ids.genTimeMillisId(),user,transfer)!=1)throw unavailable();}tx.commit();
        }catch(Exception ignored){org.apache.logging.log4j.LogManager.getLogger(getClass()).warn("Private export transfer indexing delayed");}
        finally{if(tx!=null&&tx.isActive())try{tx.rollback();}catch(RuntimeException ignored){}}
    }
    private JSONObject requireCreation(JSONObject identity,String password,String totp)throws RepositoryException {
        JSONObject current=proofs.requireInCurrentTransaction(identity,password,totp);proofs.read(identity);return current;
    }
    private static String actor(JSONObject identity){var user=identity==null?null:identity.optJSONObject("user");if(user==null||!user.optString("oId").matches("[1-9][0-9]{0,18}"))throw ApiV3Requests.forbidden();return user.getString("oId");}
    private static String binding(JSONObject proof)throws Exception{return ApiV3TokenUtil.hash(ApiV3Json.canonical(proof));}
    private static String billing(String actor,String key)throws Exception{return ApiV3TokenUtil.hash(OPERATION+"\0"+actor+"\0"+key);}
    private long clock(){long time=now();if(time<=0)throw unavailable();return time;}
    private Transaction begin(){try{return ApiV3Transactions.strict(requests.beginTransaction());}catch(Exception e){throw unavailable();}}
    private void noRoot(){if(exports.hasTransactionBegun()||requests.hasTransactionBegun())throw new IllegalStateException("export root required");}
    public static ApiV3Exception unavailable(){return ApiV3PostExportSnapshotService.unavailable();}
}
