package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.*;
import org.testng.Assert;
import sun.misc.Unsafe;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.nio.file.*;

/** Actual parameterized SQL, coordinator, savepoints and strict bundled JDBC commit. */
public class ApiV3ReportPublicationTest {
 static void inject(Object t,String n,Object v)throws Exception{Class<?> c=t.getClass();while(c!=null){try{var f=c.getDeclaredField(n);f.setAccessible(true);f.set(t,v);return;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
 static List<JSONObject> rows(Connection c,String q,Object...a)throws RepositoryException {try(var p=c.prepareStatement(q.replace(" FOR UPDATE",""))){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);try(var r=p.executeQuery()){var result=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));result.add(row);}return result;}}catch(SQLException e){throw new RepositoryException(e);}}
 static void sql(Connection c,String q,Object...a)throws RepositoryException{try(var p=c.prepareStatement(q)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);p.executeUpdate();}catch(SQLException e){throw new RepositoryException(e);}}
 static class F implements AutoCloseable {
  Path file=Files.createTempFile("report-",".sqlite");String url="jdbc:sqlite:"+file;Connection db=DriverManager.getConnection(url),writer;JdbcTransaction tx;
  String fault="";int sequence=100,roots;List<String> locks=new ArrayList<>();boolean changeAuthor;
  ApiV3ReportPublicationService service=new ApiV3ReportPublicationService(){protected String newId(){return String.valueOf(++sequence);}};
  F()throws Exception {
   sql(db,"CREATE TABLE u(oId TEXT PRIMARY KEY,userStatus INT,userName TEXT,userRole TEXT)");sql(db,"INSERT INTO u VALUES('1',0,'reader','defaultRole'),('2',0,'writer','defaultRole'),('3',0,'parent','defaultRole'),('4',4,'disabled','defaultRole')");
   sql(db,"CREATE TABLE a(oId TEXT PRIMARY KEY,articleAuthorId TEXT,articleType INT,articleStatus INT,articleAnonymousView INT,articleContent TEXT)");sql(db,"INSERT INTO a VALUES('10','2',0,0,0,''),('11','2',1,0,0,'@reader '),('12','2',1,0,0,'other'),('13','1',0,0,0,'')");
   sql(db,"CREATE TABLE c(oId TEXT PRIMARY KEY,commentAuthorId TEXT,commentOnArticleId TEXT,commentStatus INT,commentVisible INT)");sql(db,"INSERT INTO c VALUES('20','2','10',0,0),('21','2','10',0,1),('22','1','10',0,0)");
   sql(db,"CREATE TABLE chat(oId TEXT PRIMARY KEY,content TEXT)");sql(db,"INSERT INTO chat VALUES('30','{\"userOId\":\"2\",\"userName\":\"reader\"}'),('31','{\"userName\":\"writer\"}')");
   sql(db,"CREATE TABLE r(oId TEXT PRIMARY KEY,reportUserId TEXT,reportDataId TEXT,reportDataType INT,reportType INT,reportMemo TEXT,reportHandled INT)");sql(db,"CREATE TABLE receipts(oId TEXT PRIMARY KEY,value TEXT)");
   inject(service,"users",new UserRepository(){public String getName(){return "u";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return F.this.select(q,a);}});
   inject(service,"articles",new ArticleRepository(){public String getName(){return "a";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return F.this.select(q,a);}});
   inject(service,"comments",new CommentRepository(){public String getName(){return "c";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return F.this.select(q,a);}});
   inject(service,"chat",new ChatRoomRepository(){public String getName(){return "chat";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return F.this.select(q,a);}});
   inject(service,"reports",new ReportRepository(){public String getName(){return "r";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return F.this.select(q,a);}});
   inject(service,"requests",new ApiV3RequestRepository(){public boolean hasTransactionBegun(){return tx!=null&&tx.isActive();}public Transaction beginTransaction(){return begin();}public JSONObject getForUpdate(String id)throws RepositoryException{locks.add("receipt");var r=rows(writer,"SELECT value FROM receipts WHERE oId=?",id);return r.isEmpty()?null:new JSONObject(r.getFirst().getString("value"));}public String add(JSONObject r)throws RepositoryException{sql(writer,"INSERT INTO receipts VALUES(?,?)",r.getString("oId"),r.toString());return r.getString("oId");}public void update(String id,JSONObject r,String...f)throws RepositoryException{sql(writer,"UPDATE receipts SET value=? WHERE oId=?",r.toString(),id);if(fault.equals("receipt"))throw new RepositoryException("PRIVATE");}});
  }
  List<JSONObject> select(String q,Object...a)throws RepositoryException {
   boolean lock=q.endsWith(" FOR UPDATE");if(!lock){if(fault.equals("hint"))throw new RepositoryException("PRIVATE");return rows(tx!=null&&tx.isActive()?writer:db,q,a);}
   Assert.assertNotNull(JdbcRepository.TX.get());if(q.contains("`u`"))locks.add("user:"+a[0]);else locks.add("target");
   if(fault.equals("read"))throw new RepositoryException("PRIVATE");
   if(changeAuthor&&q.contains("`a`")){changeAuthor=false;sql(writer,"UPDATE a SET articleAuthorId='3' WHERE oId='10'");}
   return rows(writer,q,a);
  }
  Transaction begin(){try{roots++;var real=DriverManager.getConnection(url);real.setAutoCommit(false);writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{try{
   if(m.getName().equals("commit")){if(fault.equals("commit"))throw new SQLException("PRIVATE");real.commit();if(fault.equals("ack"))throw new SQLException("PRIVATE_ACK");return null;}
   if(m.getName().equals("setSavepoint")&&fault.equals("savepoint"))throw new SQLException("PRIVATE");
   if(m.getName().equals("prepareStatement")){String q=(String)a[0];var ps=real.prepareStatement(q.replace(" FOR UPDATE",""));return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(x,n,b)->{try{var out=n.invoke(ps,b);if(n.getName().equals("executeUpdate")&&q.startsWith("INSERT INTO `r`")&&fault.equals("write"))throw new SQLException("PRIVATE");return out;}catch(InvocationTargetException e){throw e.getCause();}});}
   return m.invoke(real,a);
  }catch(InvocationTargetException e){throw e.getCause();}});
   var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);tx=(JdbcTransaction)((Unsafe)f.get(null)).allocateInstance(JdbcTransaction.class);f=JdbcTransaction.class.getDeclaredField("connection");f.setAccessible(true);f.set(tx,writer);f=JdbcTransaction.class.getDeclaredField("isActive");f.setAccessible(true);f.setBoolean(tx,true);JdbcRepository.TX.set(tx);return tx;
  }catch(Exception e){throw new IllegalStateException(e);}}
  JSONObject send(int type,String target,String key)throws Exception{return service.publish("1",body(type,target),key);}
  org.b3log.latke.http.RequestContext http(String token,String key,String json,boolean route)throws Exception {
   var controller=new org.b3log.symphony.processor.ApiV3ReportProcessor();inject(controller,"publication",service);
   var auth=new org.b3log.symphony.processor.middleware.ApiV3AuthMidware();inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String t){return Set.of("writer","reader").contains(t)?new JSONObject().put("user",new JSONObject().put("oId","1")).put("scope",t.equals("writer")?"content.write":"content.read"):null;}});
   var writes=new org.b3log.symphony.processor.middleware.ApiV3WriteMidware();
   var raw=new io.netty.handler.codec.http.DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/reports?apiKey=legacy",io.netty.buffer.Unpooled.copiedBuffer(json,java.nio.charset.StandardCharsets.UTF_8));
   try{raw.headers().set("Content-Type","application/json");raw.headers().set("Cookie","legacy");raw.headers().set("X-Real-IP","127.0.0.1");if(token!=null)raw.headers().set("Authorization","Bearer "+token);if(key!=null)raw.headers().set("Idempotency-Key",key);
    var req=new org.b3log.latke.http.Request(null,raw);req.setBytes(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));req.setParams(new HashMap<>(Map.of("apiKey","legacy")));var ctx=new org.b3log.latke.http.RequestContext(req,new org.b3log.latke.http.Response(null,new io.netty.handler.codec.http.DefaultHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpResponseStatus.OK)));ctx.attr("user",new JSONObject().put("oId","2"));
    if(route){var m=org.b3log.symphony.processor.ApiV3ReportProcessor.class.getDeclaredMethod("registerRoutes",org.b3log.symphony.processor.ApiV3ReportProcessor.class,auth.getClass(),writes.getClass());m.setAccessible(true);m.invoke(null,controller,auth,writes);org.b3log.latke.http.Dispatcher.mapping();ctx.addHandler(new org.b3log.latke.http.handler.RouteHandler());ctx.addHandler(controller::publish);ctx.handle();}
    else{ctx.addHandler(writes::handle);ctx.addHandler(controller::publish);auth.contentWrite(ctx);}return ctx;
   }finally{raw.release();}
  }
  int count(String table)throws Exception{return rows(db,"SELECT COUNT(*) AS n FROM "+table).getFirst().getInt("n");}
  public void close()throws Exception{if(tx!=null&&tx.isActive())tx.rollback();JdbcRepository.TX.remove();db.close();Files.deleteIfExists(file);}
 }
 static JSONObject body(int t,String id){return new JSONObject().put("targetType",t).put("targetId",id).put("reason",49).put("memo","");}
 interface A{void run()throws Exception;}static void error(int status,A a)throws Exception{try{a.run();Assert.fail("expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);Assert.assertFalse(e.getMessage().contains("PRIVATE"));}}
 public static void main(String[]a)throws Exception {
  var p=new Properties();p.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(p);Latkes.setLocalProps(p);
  try(var f=new F()){
   var first=f.send(0,"10","a");Assert.assertEquals(first.keySet(),Set.of("id"));Assert.assertEquals(f.count("r"),1);Assert.assertEquals(f.locks.subList(0,3),List.of("user:1","user:2","receipt"));
   Assert.assertEquals(f.send(0,"10","b").getString("id"),first.getString("id"));Assert.assertEquals(f.count("r"),1);
   sql(f.db,"UPDATE r SET reportHandled=1");Assert.assertNotEquals(f.send(0,"10","c").getString("id"),first.getString("id"));Assert.assertEquals(f.send(0,"10","a").getString("id"),first.getString("id"));Assert.assertEquals(f.count("r"),2);
   sql(f.db,"UPDATE u SET userStatus=4 WHERE oId='2'");Assert.assertEquals(f.send(0,"10","a").getString("id"),first.getString("id"));sql(f.db,"UPDATE u SET userStatus=0 WHERE oId='2'");
   sql(f.db,"UPDATE a SET articleContent=?,articleType=1 WHERE oId='10'","x".repeat(1048577));Assert.assertEquals(f.send(0,"10","a").getString("id"),first.getString("id"));
   sql(f.db,"DELETE FROM a WHERE oId='10'");sql(f.db,"DELETE FROM u WHERE oId='2'");Assert.assertEquals(f.send(0,"10","a").getString("id"),first.getString("id"));error(404,()->f.send(0,"10","fresh"));
   sql(f.db,"UPDATE u SET userStatus=4 WHERE oId='1'");error(404,()->f.send(0,"10","a"));
  }System.out.println("PASS publication 1 fixed user-before-receipt lock order, pending dedup, handled new-key, deleted target/author replay, actor revalidation");
  try(var f=new F()){
   f.send(0,"11","invited");error(404,()->f.send(0,"12","private"));error(404,()->f.send(0,"13","self"));f.send(1,"20","comment");error(404,()->f.send(1,"21","private-comment"));error(404,()->f.send(1,"22","self-comment"));f.send(2,"2","user");error(404,()->f.send(2,"4","disabled"));error(404,()->f.send(2,"1","self-user"));f.send(3,"30","chat");error(404,()->f.send(3,"31","chat-no-id"));
   Assert.assertEquals(f.count("r"),4);
  }System.out.println("PASS publication 2 four targets, explicit discussion invitations/private-comment author access/self bans, no chat nickname fallback");
  try(var f=new F()){
   var b=body(0,"10").put("memo","  <b>Hello</b> &lt;script&gt;alert(1)&lt;/script&gt;  ");f.service.publish("1",b,"xss");var memo=rows(f.db,"SELECT reportMemo FROM r").getFirst().getString("reportMemo");Assert.assertFalse(memo.contains("<"));Assert.assertTrue(memo.contains("&lt;script&gt;"));
   error(422,()->f.service.publish("1",body(0,"10").put("memo","&".repeat(3000)),"expanded"));
   error(409,()->f.service.publish("1",body(0,"10"),"xss"));
   for(var bad:List.of(body(3,"30").put("reason",0),body(0,"10").put("reason",5),body(2,"2").put("reason",1),body(0,"10").put("targetId","1 OR 1=1"),body(0,"10").put("memo","a".repeat(10241)),body(0,"10").put("userId","2")))error(422,()->f.service.publish("1",bad,"bad"));
  }System.out.println("PASS publication 3 typed reason/target/owner contract, storage-safe HTML encoding and entity expansion budget, idempotency body conflict");
  for(var fault:List.of("hint","read","write","receipt","commit","savepoint"))try(var f=new F()) {f.fault=fault;error(503,()->f.send(0,"10","fault"));Assert.assertEquals(f.count("r"),0);Assert.assertEquals(f.count("receipts"),0);f.fault="";f.send(0,"10","fault");Assert.assertEquals(f.count("r"),1);}
  System.out.println("PASS publication 4 real strict SQL rollback on hint/locked-read/business/receipt/commit/savepoint failure, same-key recovery");
  try(var f=new F()){f.fault="ack";error(503,()->f.send(0,"10","ack"));Assert.assertEquals(f.count("r"),1);f.fault="";f.send(0,"10","ack");Assert.assertEquals(f.count("r"),1);}
  try(var f=new F()){f.changeAuthor=true;error(503,()->f.send(0,"10","changed"));Assert.assertEquals(f.count("r"),0);Assert.assertEquals(rows(f.db,"SELECT articleAuthorId FROM a WHERE oId='10'").getFirst().getString("articleAuthorId"),"2");}
  System.out.println("PASS publication 5 lost commit acknowledgement replays durable ID, ownership changes never expand lock set and abort whole mutation");
  try(var f=new F()){
   for(String token:Arrays.asList(null,"bad","reader")){Assert.assertEquals(f.http(token,"auth",body(0,"10").toString(),false).attr("test.status"),"reader".equals(token)?403:401);Assert.assertEquals(f.roots,0);}
   Assert.assertEquals(f.http("writer",null,body(0,"10").toString(),false).attr("test.status"),422);
   Assert.assertEquals(f.http("writer","bad","{\"targetType\":0,\"targetId\":\"10\",\"reason\":49,\"userId\":\"2\"}",false).attr("test.status"),422);
   var response=f.http("writer","http",body(0,"10").toString(),true);Assert.assertEquals(response.attr("test.status"),200);Assert.assertEquals(response.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(response.getResponse().getHeader("Vary"),"Authorization");Assert.assertEquals(f.count("r"),1);
  }System.out.println("PASS publication 6 actual POST Dispatcher/auth/write middleware/controller, wrong scope and legacy identity rejected, body/key gate, no-store response");

  try(var f=new F()){
   sql(f.db,"INSERT INTO chat VALUES('32','{\"userOId\":2}'),('33','{\"userOId\":179000000000000001}')");
   sql(f.db,"INSERT INTO u VALUES('179000000000000001',0,'longWriter','defaultRole')");
   f.send(3,"32","integer-author");f.send(3,"33","long-author");Assert.assertEquals(f.count("r"),2);
   for(String invalid:List.of("1","2.0","2e0","-2","true","null","{}","[]","\"02\"","92233720368547758070")){
    sql(f.db,"INSERT OR REPLACE INTO chat VALUES('34',?)","{\"userOId\":"+invalid+"}");
    error(404,()->f.send(3,"34","bad-owner-"+Integer.toString(invalid.hashCode()).replace("-","n")));
   }
   Assert.assertEquals(f.count("r"),2);
  }System.out.println("PASS publication 7 actual integer and 18-digit chat author IDs, no floating-point loss, malformed/self owners remain denied");

 }
}
