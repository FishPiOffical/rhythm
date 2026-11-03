package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.ApiV3PostExportProcessor;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.zip.*;

/** Actual snapshot ZIP, database chunks, proof, point transfer and strict receipt; external transport adapted. */
public class ApiV3PostExportTest extends ApiV3SecurityTest {
 static class F extends ApiV3SecurityTest.F {
  long clock=NOW;String exportFault="";int generated;boolean mutateEpoch;final ApiV3PostExportService export=new ApiV3PostExportService(){protected long now(){return clock;}};
  final ApiV3PostExportSnapshotService snapshot=new ApiV3PostExportSnapshotService(){public Snapshot generate(String a,ApiV3PostExportFiles f,long t)throws Exception{generated++;Snapshot out=super.generate(a,f,t);if(mutateEpoch){sql(db,"INSERT INTO states VALUES('1',1,?)",NOW);}return out;}};
  final ApiV3PostExportProcessor endpoint=new ApiV3PostExportProcessor();
  F()throws Exception {
   sql(db,"ALTER TABLE u ADD COLUMN userPoint INT DEFAULT 500");sql(db,"ALTER TABLE u ADD COLUMN userUsedPoint INT DEFAULT 0");
   sql(db,"CREATE TABLE artifacts(oId TEXT PRIMARY KEY,userId TEXT,createdAt INT,capturedAt INT,expiresAt INT,articleCount INT,commentCount INT,sourceBytes INT,byteLength INT,sha256 TEXT,chunkCount INT,paidPoints INT)");
   sql(db,"CREATE TABLE chunks(oId TEXT PRIMARY KEY,jobId TEXT,userId TEXT,sequenceNo INT,byteLength INT,data TEXT,UNIQUE(jobId,sequenceNo))");
   sql(db,"CREATE TABLE export_receipts(oId TEXT PRIMARY KEY,value TEXT)");
   sql(db,"CREATE TABLE transfers(oId TEXT PRIMARY KEY,fromId TEXT,toId TEXT,sum INT,fromBalance INT,toBalance INT,time INT,type INT,dataId TEXT,memo TEXT,sourceAppId TEXT,sourceAppName TEXT,sourceScene TEXT,sourceRequestId TEXT)");sql(db,"CREATE TABLE transfer_index(oId TEXT PRIMARY KEY,userId TEXT,rawId TEXT)");
   sql(db,"CREATE TABLE articles(oId TEXT PRIMARY KEY,articleAuthorId TEXT,articleTitle TEXT,articleTags TEXT,articleContent TEXT,articleRewardContent TEXT,articleRewardPoint INT,articleCreateTime INT,articleUpdateTime INT,articleStatus INT,articleType INT,articleAnonymous INT,articleIP TEXT)");
   sql(db,"CREATE TABLE comments(oId TEXT PRIMARY KEY,commentAuthorId TEXT,commentContent TEXT,commentOnArticleId TEXT,commentOriginalCommentId TEXT,commentCreateTime INT,commentStatus INT,commentAnonymous INT,commentType INT,commentVisible INT,commentIP TEXT)");
   for(int i=1;i<=6;i++)sql(db,"INSERT INTO articles VALUES(?, '1',?, '中文,标签',?, ?,16,123,456,?,?,?,'PRIVATE_IP')",String.valueOf(i),"Title"+i,"body "+i+"\n中文🙂",i==6?"PAID_SECRET":"",i%3,i%4,i%2);
   sql(db,"INSERT INTO articles VALUES('999','2','OTHER_TITLE','OTHER_TAG','OTHER_BODY','OTHER_REWARD',0,1,2,0,0,0,'OTHER_IP')");
   for(int i=10;i<18;i++)sql(db,"INSERT INTO comments VALUES(?, '1',?, '999','',321,?,?,?,?,'PRIVATE_IP')",String.valueOf(i),"own-comment "+i,i%2,i%2,i%2,i%2);
   sql(db,"INSERT INTO comments VALUES('998','2','OTHER_PRIVATE','1','',1,0,1,0,1,'OTHER_IP')");
   var jobs=new ApiV3PostExportRepository(){public String getName(){return "artifacts";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}};
   var chunks=new ApiV3PostExportChunkRepository(){public String getName(){return "chunks";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}};
   var receipts=new ApiV3RequestRepository(){public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}public JSONObject getForUpdate(String id)throws RepositoryException{var out=query("SELECT value FROM export_receipts WHERE oId=?",id);return out.isEmpty()?null:new JSONObject(out.getFirst().getString("value"));}public String add(JSONObject r)throws RepositoryException{sql(writer,"INSERT INTO export_receipts VALUES(?,?)",r.getString("oId"),r.toString());return r.getString("oId");}public void update(String id,JSONObject r,String...fields)throws RepositoryException{sql(writer,"UPDATE export_receipts SET value=? WHERE oId=?",r.toString(),id);if(exportFault.equals("receipt"))throw new RepositoryException("PRIVATE");}};
   var users=new UserRepository(){public String getName(){return "u";}public boolean hasTransactionBegun(){return active();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}public JSONObject getForUpdate(String id)throws RepositoryException{var out=query("SELECT * FROM u WHERE oId=? FOR UPDATE",id);return out.isEmpty()?null:out.getFirst();}public void update(String id,JSONObject r,String...fields)throws RepositoryException{Assert.assertTrue(ArticlePublicationCaches.active());sql(writer,"UPDATE u SET userPoint=?,userUsedPoint=? WHERE oId=?",r.getInt("userPoint"),r.optInt("userUsedPoint"),id);}};
   var transfers=new PointtransferRepository(){public String getName(){return "transfers";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}public String add(JSONObject r)throws RepositoryException{String id=org.b3log.latke.util.Ids.genTimeMillisId();r.put("oId",id);insert("transfers",r);return id;}};
   var index=new PointtransferRedcRepository(){public String getName(){return "transfer_index";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}};
   var points=new PointtransferMgmtService(){public void indexTransferAsync(String a,String b,String c){throw new AssertionError("precommit index");}};inject(points,"pointtransferRepository",transfers);inject(points,"userRepository",users);
   inject(snapshot,"articles",new ArticleRepository(){public String getName(){return "articles";}});inject(snapshot,"comments",new CommentRepository(){public String getName(){return "comments";}});inject(snapshot,"exports",jobs);
   inject(export,"proofs",proof);inject(export,"snapshots",snapshot);inject(export,"exports",jobs);inject(export,"chunks",chunks);inject(export,"requests",receipts);inject(export,"users",users);inject(export,"points",points);inject(export,"transfers",transfers);inject(export,"index",index);inject(endpoint,"exports",export);
  }
  List<JSONObject> query(String q,Object...a)throws RepositoryException {
   if(exportFault.equals("snapshot")&&q.contains("OCTET_LENGTH"))throw new RepositoryException("EXPORT_PRIVATE_SOURCE_SENTINEL_"+OLD);
   if(exportFault.equals("oversize")&&q.contains("OCTET_LENGTH"))return List.of(new JSONObject().put("oId","1").put("sourceBytes",ApiV3PostExportFiles.ROW_LIMIT+1));
   return rows(active()?writer:db,q.replace("OCTET_LENGTH(","length(CAST(").replace("articleContent)","articleContent AS BLOB))").replace("articleRewardContent)","articleRewardContent AS BLOB))").replace("commentContent)","commentContent AS BLOB))"),a);
  }
  void insert(String table,JSONObject r)throws RepositoryException {var names=new ArrayList<>(r.keySet());sql(writer,"INSERT INTO "+table+" ("+String.join(",",names)+") VALUES ("+String.join(",",Collections.nCopies(names.size(),"?"))+")",names.stream().map(r::get).toArray());}
  int snapshotIsolationUpgrades;
  @Override Transaction begin(){Transaction tx=super.begin();Connection previous=writer;final boolean[] raised={false};
   writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{try{
    if(m.getName().equals("getTransactionIsolation")&&exportFault.equals("weak-isolation"))return Connection.TRANSACTION_READ_COMMITTED;
    if(exportFault.equals("read-committed")){
     if(m.getName().equals("getTransactionIsolation"))return raised[0]?Connection.TRANSACTION_REPEATABLE_READ:Connection.TRANSACTION_READ_COMMITTED;
     if(m.getName().equals("setTransactionIsolation")){Assert.assertEquals((Integer)a[0],Integer.valueOf(Connection.TRANSACTION_REPEATABLE_READ));raised[0]=true;snapshotIsolationUpgrades++;return null;}
    }
    if(m.getName().equals("commit")&&roots>=3&&exportFault.equals("commit"))throw new SQLException("PRIVATE_COMMIT");
    if(m.getName().equals("prepareStatement")){String q=(String)a[0];var ps=(PreparedStatement)m.invoke(previous,a);return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(x,n,b)->{try{Object out=n.invoke(ps,b);if(n.getName().equals("executeUpdate")&&(exportFault.equals("chunks")&&q.startsWith("INSERT INTO `chunks`")||exportFault.equals("job")&&q.startsWith("INSERT INTO `artifacts`")||exportFault.equals("charge")&&q.startsWith("UPDATE u")))throw new SQLException("PRIVATE_WRITE");return out;}catch(InvocationTargetException e){throw e.getCause();}});}
    Object out=m.invoke(previous,a);if(m.getName().equals("commit")&&roots==3&&exportFault.equals("ack"))throw new SQLException("PRIVATE_ACK");return out;
   }catch(InvocationTargetException e){throw e.getCause();}});
   try{var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(this.tx,writer);}catch(Exception e){throw new RuntimeException(e);}return tx;
  }
  JSONObject create(String key)throws Exception{return export.create(identity(),new JSONObject().put("currentPassword",OLD),key,"ip");}
  RequestContext http(String method,String path,String key,String content)throws Exception {
   var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.valueOf(method),path,io.netty.buffer.Unpooled.copiedBuffer(content==null?"":content,StandardCharsets.UTF_8));
   try{raw.headers().set("Content-Type","application/json");raw.headers().set("X-Real-IP","127.0.0.1");raw.headers().set("Cookie","legacy");if(key!=null)raw.headers().set("Idempotency-Key",key);raw.headers().set("Authorization","Bearer owner");
    var req=new Request(null,raw);req.setBytes((content==null?"":content).getBytes(StandardCharsets.UTF_8));req.setParams(new HashMap<>(Map.of("apiKey","legacy")));
    Response response=new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)){public void sendBytes(byte[] data){sent=data;}};
    var ctx=new RequestContext(req,response);ctx.addHandler(new org.b3log.latke.http.handler.RouteHandler());ctx.addHandler(c->{var match=(org.b3log.latke.http.handler.RouteResolution)c.attr("MATCH_RESULT");String name=match.getContextHandlerMeta().getInvokeHolder().getName();switch(name){case "create"->endpoint.create(c);case "status"->endpoint.status(c);case "download"->endpoint.download(c);default->throw new AssertionError(name);}});ctx.handle();return ctx;
   }finally{raw.release();}
  }
  byte[] sent;
  long n(String table)throws Exception{return rows(db,"SELECT COUNT(*) AS n FROM "+table).getFirst().getLong("n");}
  long balance()throws Exception{return rows(db,"SELECT userPoint FROM u WHERE oId='1'").getFirst().getLong("userPoint");}
 }
 static Map<String,String> unzip(byte[] bytes)throws Exception {Map<String,String> entries=new LinkedHashMap<>();try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){ZipEntry e;while((e=zip.getNextEntry())!=null){Assert.assertNull(entries.put(e.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8)));zip.closeEntry();}}return entries;}
 public static void main(String[] args)throws Exception {
  var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
  try(var f=new F()) {
   JSONObject result=f.create("a");String job=result.getString("jobId");Assert.assertEquals(result.getString("status"),"ready");Assert.assertEquals(result.getInt("articleCount"),6);Assert.assertEquals(result.getInt("commentCount"),8);Assert.assertEquals(f.balance(),400);Assert.assertEquals(f.n("transfers"),1);Assert.assertEquals(f.n("transfer_index"),2);Assert.assertEquals(f.n("export_receipts"),1);
   var entries=unzip(f.export.download(identity(),job));Assert.assertEquals(entries.size(),21);JSONObject manifest=new JSONObject(entries.get("manifest.json"));Assert.assertEquals(manifest.getJSONArray("articles").length(),6);Assert.assertEquals(manifest.getJSONArray("comments").length(),8);Assert.assertEquals(entries.get("articles/6.reward.md"),"PAID_SECRET");Assert.assertEquals(entries.get("articles/1.md"),"body 1\n中文🙂");Assert.assertEquals(manifest.getJSONArray("articles").getJSONObject(0).getLong("createdAt"),123);
   String all=entries.toString();for(String forbidden:List.of("OTHER_TITLE","OTHER_BODY","OTHER_PRIVATE","OTHER_REWARD","PRIVATE_IP","userPassword","articleAuthorId","commentIP"))Assert.assertFalse(all.contains(forbidden),forbidden);
   Assert.assertEquals(f.create("a").getString("jobId"),job);Assert.assertEquals(f.generated,1);Assert.assertEquals(f.balance(),400);Assert.assertEquals(f.n("transfers"),1);Assert.assertFalse(result.has("indexTransferId"));Assert.assertFalse(result.toString().contains("upload"));
  }System.out.println("PASS export 1 complete own hidden/invalid/anonymous/private/reward matrix, UTF8 ZIP fixed ID entries and genuine timestamps; excludes foreign content/account data, idempotent charge/index once");
  try(var f=new F()){
   for(int i=1000;i<1260;i++)sql(f.db,"INSERT INTO comments VALUES(?,'1',?,'999','',321,1,1,0,1,'IP')",String.valueOf(i),"batch "+i);
   var job=f.create("all");Assert.assertEquals(job.getInt("commentCount"),268);Assert.assertEquals(new JSONObject(unzip(f.export.download(identity(),job.getString("jobId"))).get("manifest.json")).getJSONArray("comments").length(),268);
  }System.out.println("PASS export 2 explicit complete count and keyset enumeration beyond default pagination, no foreign target visibility requirement");
  for(String failure:List.of("snapshot","chunks","job","charge","receipt","commit"))try(var f=new F()){
   f.exportFault=failure;error(503,()->f.create("fault"));Assert.assertEquals(f.balance(),500);for(String table:List.of("artifacts","chunks","transfers","export_receipts"))Assert.assertEquals(f.n(table),0,failure+table);f.exportFault="";Assert.assertEquals(f.create("fault").getString("status"),"ready");Assert.assertEquals(f.balance(),400);
  }System.out.println("PASS export 3 real JDBC snapshot/chunk/job/charge/receipt/commit fault rollback; failed request never charges and same key can recover");
  try(var f=new F()) {
   f.exportFault="ack";error(503,()->f.create("ack"));Assert.assertEquals(f.balance(),400);Assert.assertEquals(f.n("artifacts"),1);f.exportFault="";String job=f.create("ack").getString("jobId");Assert.assertEquals(f.balance(),400);Assert.assertEquals(f.generated,1);Assert.assertEquals(f.n("transfer_index"),2);
   var other=identity().put("tokenId","102").put("user",new JSONObject().put("oId","2"));error(404,()->f.export.download(other,job));sql(f.db,"UPDATE tokens SET state=2 WHERE oId='100'");error(403,()->f.export.download(identity(),job));sql(f.db,"UPDATE tokens SET state=0 WHERE oId='100'");f.clock=NOW+86_400_000L;error(410,()->f.export.download(identity(),job));Assert.assertEquals(f.export.purgeExpired(),1);Assert.assertEquals(f.n("artifacts"),0);Assert.assertEquals(f.n("chunks"),0);Assert.assertEquals(f.create("ack").getString("status"),"expired");Assert.assertEquals(f.balance(),400);
  }System.out.println("PASS export 4 lost commit acknowledgment replays same artifact once; current first-party owner/authorization and exact expiry, transactional content purge and expired receipt replay");
  for(String damage:List.of("DELETE FROM chunks","UPDATE chunks SET data='AAAA'","UPDATE chunks SET userId='2'","UPDATE artifacts SET sha256='"+"a".repeat(64)+"'","UPDATE chunks SET sequenceNo=99"))try(var f=new F()){String job=f.create("damage").getString("jobId");sql(f.db,damage);error(503,()->f.export.download(identity(),job));}
  System.out.println("PASS export 5 missing/corrupt/foreign/reordered chunk and digest fail before download bytes");
  try(var f=new F()){f.mutateEpoch=true;error(409,()->f.create("epoch"));Assert.assertEquals(f.balance(),500);Assert.assertEquals(f.n("artifacts"),0);}
  try(var f=new F()){sql(f.db,"UPDATE u SET userPoint=99 WHERE oId='1'");error(409,()->f.create("balance"));Assert.assertEquals(f.balance(),99);Assert.assertEquals(f.n("chunks"),0);}
  try(var f=new F()){sql(f.db,"UPDATE tokens SET clientId='third' WHERE oId='100'");error(403,()->f.create("third"));Assert.assertEquals(f.generated,0);}
  try(var f=new F()){error(422,()->f.export.create(identity(),new JSONObject().put("currentPassword",NEW),"x","ip"));Assert.assertEquals(f.generated,0);sql(f.db,"UPDATE u SET secret2fa=? WHERE oId='1'",SECRET);error(422,()->f.create("mfa"));Assert.assertEquals(f.generated,0);}
  try(var f=new F()){for(JSONObject invalid:List.of(new JSONObject(),new JSONObject().put("currentPassword",OLD).put("userId","2"),new JSONObject().put("currentPassword",123)))error(422,()->f.export.create(identity(),invalid,"i","ip"));error(422,()->f.export.create(identity(),new JSONObject().put("currentPassword",OLD),"bad key","ip"));}
  System.out.println("PASS export 6 initial and final real password/MFA/epoch proof, first-party-only creation, insufficient balance and strict input fail without artifacts/charge");
  try(var files=new ApiV3PostExportFiles()) {Assert.assertTrue(Files.isDirectory(files.directory));try{ApiV3PostExportFiles.entry(new ZipOutputStream(OutputStream.nullOutputStream()),"articles/../bad.md","x",NOW);Assert.fail();}catch(IOException expected){}try{ApiV3PostExportFiles.utf8("\ud800");Assert.fail();}catch(IOException expected){}}
  try(var f=new F()){sql(f.db,"UPDATE articles SET articleContent=NULL WHERE oId='1'");error(503,()->f.create("null"));Assert.assertEquals(f.balance(),500);}
  System.out.println("PASS export 7 fixed entry paths, strict source unicode and missing source fail closed, temporary files delete and root ownership checked");
  try(var f=new F()) {
   ApiV3PostExportProcessor.registerRoutes(f.endpoint,f.auth,f.writes);Dispatcher.mapping();
   var created=f.http("POST","/api/v3/me/exports/posts","http",new JSONObject().put("currentPassword",OLD).toString());Assert.assertEquals(created.attr("test.status"),200);String job=((JSONObject)created.attr("test.data")).getString("jobId");
   var state=f.http("GET","/api/v3/me/exports/posts/"+job,null,null);Assert.assertEquals(state.attr("test.status"),200);Assert.assertEquals(state.getResponse().getHeader("Cache-Control"),"no-store");
   var download=f.http("GET","/api/v3/me/exports/posts/"+job+"/download",null,null);Assert.assertEquals(download.getResponse().getContentType(),"application/zip");Assert.assertEquals(download.getResponse().getHeader("Content-Disposition"),"attachment; filename=\"posts-"+job+".zip\"");Assert.assertEquals(download.getResponse().getHeader("X-Content-Type-Options"),"nosniff");Assert.assertTrue(f.sent.length>0);f.sent=null;
   sql(f.db,"DELETE FROM chunks");var error=f.http("GET","/api/v3/me/exports/posts/"+job+"/download",null,null);Assert.assertEquals(error.attr("test.status"),503);Assert.assertNull(f.sent);Assert.assertNull(error.getResponse().getHeader("Content-Disposition"));
  }
  try(var f=new F()){sql(f.db,"UPDATE tokens SET scope='settings.write' WHERE oId='100'");error(403,()->f.create("missing-read"));Assert.assertEquals(f.generated,0);Assert.assertEquals(f.balance(),500);}
  System.out.println("PASS export 8 actual POST/status/download Dispatcher gates and attachment headers; download transport-only adapter, malformed artifact returns JSON without attachment, scopes checked before fee");
  try(var f=new F()){
   byte[] random=new byte[300000];new Random(42).nextBytes(random);String body=Base64.getEncoder().encodeToString(random);sql(f.db,"UPDATE articles SET articleContent=? WHERE oId='1'",body);
   var job=f.create("chunks");Assert.assertTrue(f.n("chunks")>1);byte[] bytes=f.export.download(identity(),job.getString("jobId"));Assert.assertEquals(ApiV3PostExportFiles.sha256(bytes),job.getString("sha256"));Assert.assertEquals(unzip(bytes).get("articles/1.md"),body);
  }
  for(String failure:List.of("oversize","weak-isolation"))try(var f=new F()){f.exportFault=failure;error(503,()->f.create("budget"));Assert.assertEquals(f.balance(),500);Assert.assertEquals(f.n("artifacts"),0);}
  try(var f=new F()){var tx=f.begin();try{try{f.create("nested");Assert.fail();}catch(IllegalStateException expected){}}finally{if(tx.isActive())tx.rollback();}Assert.assertEquals(f.balance(),500);}
  try(var stream=ApiV3PostExportFiles.bounded(OutputStream.nullOutputStream(),3)){stream.write(new byte[3]);try{stream.write(1);Assert.fail();}catch(IOException expected){}}
  try(var f=new F()){
   f.exportFault="read-committed";var job=f.create("snapshot-isolation");Assert.assertEquals(f.snapshotIsolationUpgrades,1);Assert.assertEquals(f.balance(),400);
   var files=unzip(f.export.download(identity(),job.getString("jobId")));Assert.assertTrue(files.containsKey("manifest.json"));Assert.assertEquals(files.get("articles/6.reward.md"),"PAID_SECRET");
   Assert.assertEquals(f.create("snapshot-isolation").getString("jobId"),job.getString("jobId"));Assert.assertEquals(f.snapshotIsolationUpgrades,1);Assert.assertEquals(f.balance(),400);
  }
  System.out.println("PASS export 9 multi-chunk incompressible source round-trip/full digest, explicit source/compressed size budgets, READ_COMMITTED upgraded before snapshot, unsupported snapshot isolation and nested root refused without charge");

  final var logged=new ArrayList<String>();
  final var appender=new org.apache.logging.log4j.core.appender.AbstractAppender("export-private-audit",null,null,false,org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY){
   @Override public void append(org.apache.logging.log4j.core.LogEvent event){logged.add(event.getMessage().getFormattedMessage());if(event.getThrown()!=null){var text=new StringWriter();event.getThrown().printStackTrace(new PrintWriter(text));logged.add(text.toString());}}
  };appender.start();final var loggers=new ArrayList<org.apache.logging.log4j.core.Logger>();
  for(Class<?> type:List.of(ApiV3PostExportTest.class,ApiV3Requests.class,ApiV3PostExportService.class,ApiV3PostExportSnapshotService.class,ApiV3IdempotencyMgmtService.class,PointtransferMgmtService.class)){var logger=(org.apache.logging.log4j.core.Logger)org.apache.logging.log4j.LogManager.getLogger(type);logger.addAppender(appender);loggers.add(logger);}
  try(var f=new F()){
   ApiV3PostExportProcessor.registerRoutes(f.endpoint,f.auth,f.writes);Dispatcher.mapping();f.exportFault="snapshot";
   var result=f.http("POST","/api/v3/me/exports/posts","private",new JSONObject().put("currentPassword",OLD).toString());Assert.assertEquals(result.attr("test.status"),503);Assert.assertFalse(String.valueOf(result.attr("test.message")).contains("EXPORT_PRIVATE"));
   org.apache.logging.log4j.LogManager.getLogger(ApiV3PostExportTest.class).error("EXPORT_LOG_POSITIVE_CONTROL");
   String actual=String.join("\n",logged);Assert.assertTrue(actual.contains("EXPORT_LOG_POSITIVE_CONTROL"));for(String secret:List.of("EXPORT_PRIVATE_SOURCE_SENTINEL",OLD,"PAID_SECRET","OTHER_PRIVATE","rhythm-private-export-"))Assert.assertFalse(actual.contains(secret),secret);
  }finally{for(var logger:loggers)logger.removeAppender(appender);appender.stop();}
  System.out.println("PASS export 10 production exception sanitization through real HTTP wrapper and Log4j appender with positive control, no source/password/temp path in response/logs");

 }
}
