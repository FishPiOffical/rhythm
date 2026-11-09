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

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import sun.misc.Unsafe;
import org.json.JSONObject;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.processor.ApiV3ArticleReadProcessor;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.util.*;
import io.netty.handler.codec.http.*;

/** 在隔离 MySQL 上覆盖实际 SQL、阅读登记、严格根事务和保存点，当前用户及文章来源由夹具适配。 */
public class ApiV3ArticleReadMysqlTest {
 static final long W=21600000L,T=1793318400000L;
 static void ok(boolean v){if(!v)throw new AssertionError();}
 interface Work{void run()throws Exception;}
 static void status(int s,Work w)throws Exception{try{w.run();throw new AssertionError("expected "+s);}catch(ApiV3Exception e){ok(e.status==s);}}
 static void inject(Object o,String n,Object v)throws Exception{Class<?> c=o.getClass();while(c!=null){try{var f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
 static List<JSONObject> rows(Connection c,String q,Object...a)throws RepositoryException{try(var p=c.prepareStatement(q)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);try(var r=p.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next()){var j=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)j.put(r.getMetaData().getColumnLabel(i),r.getObject(i));out.add(j);}return out;}}catch(SQLException e){throw new RepositoryException(e);}}
 static void sql(Connection c,String q,Object...a)throws Exception{try(var p=c.prepareStatement(q)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);p.executeUpdate();}}
 static class F implements AutoCloseable{
  String url=System.getProperty("rhythm.read.mysqlUrl"),fault="";long clock=T;Connection db=DriverManager.getConnection(url),writer;JdbcTransaction tx;
  LongArticleReadCaptureService service=new LongArticleReadCaptureService(){protected long now(){return clock;}};
  LongArticleReadWindowRepository windows=new LongArticleReadWindowRepository(){public String getName(){return "symphony_article_long_read_window";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}};
  F()throws Exception{
   inject(service,"windowRepository",windows);
   inject(service,"userRepository",new LongArticleReadUserRepository(){public String getName(){return "symphony_article_long_read_user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}});
   inject(service,"articleRepository",new ArticleRepository(){public boolean hasTransactionBegun(){return active();}public String getName(){return "articles";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}});
   inject(service,"readers",new UserRepository(){public String getName(){return "users";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}});
  }
  void reset()throws Exception{for(String t:List.of("symphony_article_long_read_user","symphony_article_long_read_window","symphony_article_long_read_anon"))sql(db,"DELETE FROM "+t);sql(db,"DELETE FROM users");sql(db,"INSERT INTO users VALUES ('1',0),('2',0),('3',0)");sql(db,"DELETE FROM articles");sql(db,"INSERT INTO articles VALUES ('10','1',0,6,0,1,'tag',0)");}
  boolean active(){return tx!=null&&tx.isActive();}
  List<JSONObject> query(String q,Object...a)throws RepositoryException{return rows(active()?writer:db,q,a);}
  Transaction begin(){try{Connection real=DriverManager.getConnection(url);real.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);real.setAutoCommit(false);writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{try{
   if(m.getName().equals("commit")){if(fault.equals("commit"))throw new SQLException("injected commit");real.commit();if(fault.equals("ack"))throw new SQLException("injected ack");return null;}
   if(m.getName().equals("prepareStatement")){String q=(String)a[0];var st=real.prepareStatement(q);return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(z,n,b)->{try{Object v=n.invoke(st,b);if(n.getName().equals("executeUpdate")&&fault.equals("counter")&&q.contains("registeredCnt"))throw new SQLException("injected after counter");return v;}catch(InvocationTargetException e){throw e.getCause();}});}
   return m.invoke(real,a);
  }catch(InvocationTargetException e){throw e.getCause();}});
   var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);tx=(JdbcTransaction)((Unsafe)f.get(null)).allocateInstance(JdbcTransaction.class);f=JdbcTransaction.class.getDeclaredField("connection");f.setAccessible(true);f.set(tx,writer);f=JdbcTransaction.class.getDeclaredField("isActive");f.setAccessible(true);f.setBoolean(tx,true);JdbcRepository.TX.set(tx);return tx;
  }catch(Exception e){throw new IllegalStateException(e);}}
  long count(String t)throws Exception{return rows(db,"SELECT COUNT(*) n FROM "+t).getFirst().getLong("n");}
  public void close()throws Exception{if(active())tx.rollback();JdbcRepository.TX.remove();db.close();}
 }
 static RequestContext http(ApiV3ArticleReadProcessor p,String token,String body)throws Exception{return httpRaw(p,token,body.getBytes(StandardCharsets.UTF_8),"application/json");}
 static RequestContext httpRaw(ApiV3ArticleReadProcessor p,String token,byte[] body,String type)throws Exception{
  var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/articles/10/reads",io.netty.buffer.Unpooled.wrappedBuffer(body));
  try{if(token!=null)raw.headers().set("Authorization","Bearer "+token);raw.headers().set("Cookie","old-cookie");if(type!=null)raw.headers().set("Content-Type",type);raw.headers().set("X-Real-IP","127.0.0.1");var req=new Request(null,raw){public String getRemoteAddr(){return "127.0.0.1";}};var parse=Request.class.getDeclaredMethod("parseForm");parse.setAccessible(true);parse.invoke(req);req.setParams(new HashMap<>(Map.of("apiKey","legacy")));var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","1"));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(p::record);c.handle();return c;}finally{raw.release();}
 }
 public static void main(String[] a)throws Exception{
  var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
  try(var f=new F()){
   // 同一读者在同一窗口只计一次，跨窗口后可重新登记。
   f.reset();
   ok(f.service.recordCurrent("10", "2"));
   ok(!f.service.recordCurrent("10", "2"));
   ok(f.count("symphony_article_long_read_user") == 1);
   ok(rows(f.db, "SELECT registeredCnt n FROM symphony_article_long_read_window").getFirst().getInt("n") == 1);
   f.clock += W;
   ok(f.service.recordCurrent("10", "2"));
   ok(f.count("symphony_article_long_read_user") == 2);
   ok(f.count("symphony_article_long_read_window") == 2);
   System.out.println("PASS article read mysql 1 same-window dedup and next-window fresh claim/counter on exact migrated schema");
   f.reset();
   for (int state : List.of(1, 2, 3, 4, 99)) {
    sql(f.db, "UPDATE users SET userStatus=? WHERE oId='2'", state);
    status(404, () -> f.service.recordCurrent("10", "2"));
   }
   sql(f.db, "UPDATE users SET userStatus=0 WHERE oId='2'");
   status(409, () -> f.service.recordCurrent("10", "1"));
   status(401, () -> f.service.recordCurrent("10", null));
   status(422, () -> f.service.recordCurrent("10' OR 1=1", "2"));
   ok(f.count("symphony_article_long_read_user") == 0);
   System.out.println("PASS article read mysql 2 current active reader only, self/anonymous/injection rejected without writes");
   for(String change:List.of("articleType=0","articleType=1","articleAnonymous=1","articleStatus=1","articleStatus=2","articleShowInList=0","articleTags='Sandbox'","articleTags='B3log-private'")){f.reset();sql(f.db,"UPDATE articles SET "+change);status(404,()->f.service.recordCurrent("10","2"));ok(f.count("symphony_article_long_read_user")==0);}
   f.reset();sql(f.db,"UPDATE users SET userStatus=4 WHERE oId='1'");status(404,()->f.service.recordCurrent("10","2"));sql(f.db,"UPDATE users SET userStatus=3 WHERE oId='1'");ok(f.service.recordCurrent("10","2"));
   System.out.println("PASS article read mysql 3 current valid signed/listable long article and author eligibility before claim");
   for (String fault : List.of("counter", "commit")) {
    f.reset();
    f.fault = fault;
    status(503, () -> f.service.recordCurrent("10", "2"));
    f.fault = "";
    ok(f.count("symphony_article_long_read_user") == 0);
    ok(f.count("symphony_article_long_read_window") == 0);
    ok(f.service.recordCurrent("10", "2"));
   }
   // 已提交但确认丢失时，重试只能读到已有登记。
   f.reset();
   f.fault = "ack";
   status(503, () -> f.service.recordCurrent("10", "2"));
   f.fault = "";
   ok(!f.service.recordCurrent("10", "2"));
   ok(f.count("symphony_article_long_read_user") == 1);
   System.out.println("PASS article read mysql 4 post-counter/commit rollback and lost-ack retry never double counts");
   f.reset();
   var owner = f.begin();
   ok(f.service.recordCurrent("10", "2"));
   ok(owner.isActive());
   owner.rollback();
   ok(f.count("symphony_article_long_read_user") == 0);
   owner = f.begin();
   f.fault = "counter";
   status(503, () -> f.service.recordCurrent("10", "2"));
   f.fault = "";
   ok(owner.isActive());
   ok(rows(f.writer, "SELECT COUNT(*) n FROM symphony_article_long_read_user").getFirst().getInt("n") == 0);
   ApiV3Transactions.strict(owner).commit();
   ok(f.count("symphony_article_long_read_window") == 0);
   System.out.println("PASS article read mysql 5 nested operation never commits owner and savepoint failure rolls back only claim/counter");
   f.reset();ok(f.service.recordCurrent("10","3"));sql(f.db,"UPDATE symphony_article_long_read_window SET state='SETTLING'");status(503,()->f.service.recordCurrent("10","2"));ok(f.count("symphony_article_long_read_user")==1);ok(rows(f.db,"SELECT registeredCnt n FROM symphony_article_long_read_window").getFirst().getInt("n")==1);
   System.out.println("PASS article read mysql 6 CLIENT_FOUND_ROWS matched settling window cannot consume a reader claim");
   f.reset();var endpoint=new ApiV3ArticleReadProcessor();inject(endpoint,"reads",f.service);var auth=new ApiV3AuthMidware();inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService());ApiV3ArticleReadProcessor.registerRoutes(endpoint,auth,new ApiV3WriteMidware());Dispatcher.mapping();for(String token:Arrays.asList(null,"bad"))ok(http(endpoint,token,"{}").attr("test.status").equals(401));for(String token:List.of("read-only","activity-only"))ok(http(endpoint,token,"{}").attr("test.status").equals(403));ok(http(endpoint,"valid","{\"userId\":\"3\"}").attr("test.status").equals(422));var out=http(endpoint,"valid","{}");ok(out.attr("test.status").equals(200));ok(((JSONObject)out.attr("test.data")).keySet().equals(Set.of("articleId","recorded")));ok(out.getResponse().getHeader("Cache-Control").equals("no-store"));
   System.out.println("PASS article read mysql 7 actual Dispatcher/Bearer/scopes/write/input and response boundary; Cookie/apiKey cannot substitute identity");
   for(String malformed:List.of("{\"x\":1,\"x\":2}","not-json","[]"," ","{\"x\":"))ok(http(endpoint,"valid",malformed).attr("test.status").equals(400));
   ok(httpRaw(endpoint,"valid",new byte[]{(byte)255},"application/json").attr("test.status").equals(400));
   ok(httpRaw(endpoint,"valid"," ".repeat(129).getBytes(StandardCharsets.UTF_8),"application/json").attr("test.status").equals(422));
   ok(httpRaw(endpoint,"valid","{}".getBytes(StandardCharsets.UTF_8),"text/plain").attr("test.status").equals(415));
   ok(httpRaw(endpoint,"valid",new byte[0],null).attr("test.status").equals(200));
   System.out.println("PASS article read mysql 10 original Netty bytes survive real parseForm; duplicate/malformed/array/whitespace/UTF8/oversize and media checks cannot collapse into empty success");
   f.reset();var pool=Executors.newFixedThreadPool(2);try{var gate=new CountDownLatch(1);var tasks=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{try(var x=new F()){gate.await();return x.service.recordCurrent("10","2");}}));gate.countDown();int success=0;for(var task:tasks)if(task.get(15,TimeUnit.SECONDS))success++;ok(success==1);ok(f.count("symphony_article_long_read_user")==1);ok(rows(f.db,"SELECT registeredCnt n FROM symphony_article_long_read_window").getFirst().getInt("n")==1);}finally{pool.shutdownNow();}
   System.out.println("PASS article read mysql 8 independent JDBC transactions racing same reader commit one claim and one increment");
   f.reset();f.service.record(new LongArticleReadCaptureRequest("10", null, "ip", "ua"));f.service.record(new LongArticleReadCaptureRequest("10", "1", "ip", "ua"));ok(f.count("symphony_article_long_read_user")==0);ok(f.count("symphony_article_long_read_anon")==0);f.service.record(new LongArticleReadCaptureRequest("10", "2", "ip", "ua"));ok(!f.service.recordCurrent("10", "2"));ok(f.count("symphony_article_long_read_user")==1);
   sql(f.db,"UPDATE articles SET articleShowInList=0");f.service.record(new LongArticleReadCaptureRequest("10", "3", "ip", "ua"));ok(f.count("symphony_article_long_read_user")==1);
   System.out.println("PASS article read mysql 9 legacy wrapper skips ineligible/self/anonymous without affecting output and shares exact v3 claim");
  }
 }
}
