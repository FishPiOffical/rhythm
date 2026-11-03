package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.*;
import java.math.BigDecimal;

/** Complete, owner-only primary-table snapshot. No visibility predicates and no joined foreign content. */
@Service
public class ApiV3PostExportSnapshotService {
    @Inject private ArticleRepository articles;
    @Inject private CommentRepository comments;
    @Inject private ApiV3PostExportRepository exports;
    public record Snapshot(long articles,long comments,long sourceBytes,long bytes,String sha256,long capturedAt){ }
    public Snapshot generate(String actor,ApiV3PostExportFiles files,long capturedAt)throws Exception {
        if(actor==null||!actor.matches("[1-9][0-9]{0,18}")||exports.hasTransactionBegun())throw new IllegalStateException("snapshot root required");
        final Transaction tx;try{tx=ApiV3Transactions.strict(exports.beginTransaction());}catch(Exception e){throw unavailable();}
        try {
            final var jdbc=JdbcRepository.TX.get();
            if(jdbc==null)throw unavailable();
            final var connection=jdbc.getConnection();
            // Latke borrows connections as READ_COMMITTED by default. This dedicated root has
            // not queried any source rows yet; choose a stable snapshot before the first SELECT.
            // The pool/Latke reapplies its configured isolation on the next connection borrow.
            int isolation=connection.getTransactionIsolation();
            if(isolation!=java.sql.Connection.TRANSACTION_REPEATABLE_READ&&isolation!=java.sql.Connection.TRANSACTION_SERIALIZABLE){
                connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
                isolation=connection.getTransactionIsolation();
            }
            if(isolation!=java.sql.Connection.TRANSACTION_REPEATABLE_READ&&isolation!=java.sql.Connection.TRANSACTION_SERIALIZABLE)throw unavailable();
            final long articleCount=count(articles.getName(),"articleAuthorId",actor),commentCount=count(comments.getName(),"commentAuthorId",actor);
            long bytes=0,actualArticles=0,actualComments=0;
            try(var zip=new ZipOutputStream(ApiV3PostExportFiles.bounded(Files.newOutputStream(files.zip),ApiV3PostExportFiles.ZIP_LIMIT),StandardCharsets.UTF_8);
                var manifest=new BufferedWriter(new OutputStreamWriter(ApiV3PostExportFiles.bounded(Files.newOutputStream(files.manifest),ApiV3PostExportFiles.SOURCE_LIMIT*3),StandardCharsets.UTF_8))) {
                manifest.write("{\"formatVersion\":1,\"capturedAt\":"+capturedAt+",\"articleCount\":"+articleCount+",\"commentCount\":"+commentCount+",\"articles\":[");
                String cursor="";
                for(;;){
                    final var lengths=exports.select("SELECT oId,OCTET_LENGTH(articleContent)+OCTET_LENGTH(articleRewardContent) AS sourceBytes FROM `"+articles.getName()+"` WHERE articleAuthorId=? AND oId>? ORDER BY oId ASC LIMIT 1",actor,cursor);
                    if(lengths.isEmpty())break;if(lengths.size()!=1)throw unavailable();
                    String id=id(lengths.getFirst().opt("oId"));if(id.compareTo(cursor)<=0)throw unavailable();budget(number(lengths.getFirst(),"sourceBytes"),bytes);
                    final var rows=exports.select("SELECT oId,articleAuthorId,articleTitle,articleTags,articleContent,articleRewardContent,articleRewardPoint,articleCreateTime,articleUpdateTime,articleStatus,articleType,articleAnonymous FROM `"+articles.getName()+"` WHERE articleAuthorId=? AND oId=?",actor,id);
                    if(rows.size()!=1||!actor.equals(text(rows.getFirst(),"articleAuthorId"))||!id.equals(id(rows.getFirst().opt("oId"))))throw unavailable();
                    JSONObject row=rows.getFirst();String body=text(row,"articleContent"),reward=text(row,"articleRewardContent");long current=Math.addExact(ApiV3PostExportFiles.utf8(body).length,ApiV3PostExportFiles.utf8(reward).length);budget(current,bytes);bytes=Math.addExact(bytes,current);
                    JSONObject item=new JSONObject().put("id",id).put("title",text(row,"articleTitle")).put("tags",text(row,"articleTags")).put("content",body).put("rewardContent",reward)
                            .put("rewardPoints",number(row,"articleRewardPoint")).put("createdAt",number(row,"articleCreateTime")).put("updatedAt",number(row,"articleUpdateTime"))
                            .put("status",number(row,"articleStatus")).put("type",number(row,"articleType")).put("anonymous",number(row,"articleAnonymous")).put("file","articles/"+id+".md").put("rewardFile","articles/"+id+".reward.md");
                    ApiV3PostExportFiles.utf8(item.toString());if(actualArticles++>0)manifest.write(',');manifest.write(item.toString());
                    ApiV3PostExportFiles.entry(zip,"articles/"+id+".md",body,capturedAt);ApiV3PostExportFiles.entry(zip,"articles/"+id+".reward.md",reward,capturedAt);cursor=id;
                }
                manifest.write("],\"comments\":[");cursor="";
                for(;;){
                    final var lengths=exports.select("SELECT oId,OCTET_LENGTH(commentContent) AS sourceBytes FROM `"+comments.getName()+"` WHERE commentAuthorId=? AND oId>? ORDER BY oId ASC LIMIT 1",actor,cursor);
                    if(lengths.isEmpty())break;if(lengths.size()!=1)throw unavailable();String id=id(lengths.getFirst().opt("oId"));if(id.compareTo(cursor)<=0)throw unavailable();budget(number(lengths.getFirst(),"sourceBytes"),bytes);
                    final var rows=exports.select("SELECT oId,commentAuthorId,commentContent,commentOnArticleId,commentOriginalCommentId,commentCreateTime,commentStatus,commentAnonymous,commentType,commentVisible FROM `"+comments.getName()+"` WHERE commentAuthorId=? AND oId=?",actor,id);
                    if(rows.size()!=1||!actor.equals(text(rows.getFirst(),"commentAuthorId"))||!id.equals(id(rows.getFirst().opt("oId"))))throw unavailable();
                    JSONObject row=rows.getFirst();String body=text(row,"commentContent");long current=ApiV3PostExportFiles.utf8(body).length;budget(current,bytes);bytes=Math.addExact(bytes,current);
                    String original=text(row,"commentOriginalCommentId");if(!original.isEmpty())id(original);
                    JSONObject item=new JSONObject().put("id",id).put("content",body).put("articleId",id(row.opt("commentOnArticleId"))).put("originalCommentId",original)
                            .put("createdAt",number(row,"commentCreateTime")).put("status",number(row,"commentStatus")).put("anonymous",number(row,"commentAnonymous"))
                            .put("type",number(row,"commentType")).put("visible",number(row,"commentVisible")).put("file","comments/"+id+".md");
                    if(actualComments++>0)manifest.write(',');manifest.write(item.toString());ApiV3PostExportFiles.entry(zip,"comments/"+id+".md",body,capturedAt);cursor=id;
                }
                manifest.write("]}");manifest.flush();
                if(actualArticles!=articleCount||actualComments!=commentCount)throw unavailable();
                zip.putNextEntry(new ZipEntry("manifest.json"));Files.copy(files.manifest,zip);zip.closeEntry();
            }
            long archiveBytes=Files.size(files.zip);if(archiveBytes<=0||archiveBytes>ApiV3PostExportFiles.ZIP_LIMIT)throw unavailable();
            String sha=ApiV3PostExportFiles.sha256(files.zip);tx.commit();return new Snapshot(actualArticles,actualComments,bytes,archiveBytes,sha,capturedAt);
        }catch(ApiV3Exception error){throw error;}catch(Exception error){throw unavailable();}finally{if(tx.isActive())tx.rollback();}
    }
    private long count(String table,String owner,String actor)throws Exception {var rows=exports.select("SELECT COUNT(*) AS total FROM `"+table+"` WHERE "+owner+"=?",actor);if(rows.size()!=1)throw unavailable();long n=number(rows.getFirst(),"total");if(n<0)throw unavailable();return n;}
    private static void budget(long current,long total){if(current<0||current>ApiV3PostExportFiles.ROW_LIMIT||total>ApiV3PostExportFiles.SOURCE_LIMIT-current)throw unavailable();}
    static String text(JSONObject row,String field){if(!(row.opt(field) instanceof String text))throw unavailable();return text;}
    static String id(Object value){if(!(value instanceof String text)||!text.matches("[1-9][0-9]{0,18}"))throw unavailable();return text;}
    static long number(JSONObject row,String field){if(!(row.opt(field) instanceof Number n))throw unavailable();try{return new BigDecimal(n.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException e){throw unavailable();}}
    public static ApiV3Exception unavailable(){return new ApiV3Exception(503,"temporarily_unavailable","内容导出暂不可用，请稍后重试");}
}
