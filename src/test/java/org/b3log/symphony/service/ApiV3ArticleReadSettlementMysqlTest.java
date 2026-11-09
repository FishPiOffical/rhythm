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

import org.b3log.latke.repository.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.latke.util.Ids;
import org.json.JSONObject;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** 使用实际结算、积分操作和阅读统计 DDL，缓存与通知使用测试适配器。 */
public class ApiV3ArticleReadSettlementMysqlTest extends ApiV3ArticleReadMysqlTest {
 static final long START=21600000L;
 static class S extends F {
  final LongArticleReadSettlementService settle=new LongArticleReadSettlementService();
  int messages,unsafeIndexes,refreshes;boolean failMessage,holdAuthor;String releaseGate;
  @Override
  List<JSONObject> query(String q, Object... a) throws RepositoryException {
   var out = super.query(q, a);
   // 真实查询持有作者行锁后通知协调进程，保留锁直到另一进程观察到等待。
   if (holdAuthor && q.contains("`users`") && q.endsWith("FOR UPDATE")) {
    holdAuthor = false;
    System.out.println("HOLDING_AUTHOR");
    System.out.flush();
    long end = System.currentTimeMillis() + 20000;
    try {
     while (!java.nio.file.Files.exists(java.nio.file.Path.of(releaseGate))) {
      if (System.currentTimeMillis() > end) throw new IllegalStateException("release timeout");
      Thread.sleep(20);
     }
    } catch (InterruptedException e) {
     Thread.currentThread().interrupt();
     throw new RepositoryException(e);
    }
   }
   return out;
  }
  S()throws Exception{
   super();
   windows=new LongArticleReadWindowRepository(){public String getName(){return "symphony_article_long_read_window";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}public JSONObject get(String id)throws RepositoryException{return one("SELECT * FROM `"+getName()+"` WHERE oId=?",id);}};
   var users=new UserRepository(){public String getName(){return "users";}public boolean hasTransactionBegun(){return active();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}public void update(String id,JSONObject row,String...cols)throws RepositoryException{write("UPDATE users SET userPoint=?,userUsedPoint=? WHERE oId=?",row.getInt("userPoint"),row.optInt("userUsedPoint"),id);if(fault.equals("points"))throw new RepositoryException("after point update");}public void refreshSettingsCache(String id){ok(!active());refreshes++;}public void evictSettingsCaches(String id){ok(!active());}};
   var articles=new ArticleRepository(){public String getName(){return "articles";}public boolean hasTransactionBegun(){return active();}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}public JSONObject get(String id)throws RepositoryException{return one("SELECT * FROM articles WHERE oId=?",id);}};
   var transfers=new PointtransferRepository(){public String getName(){return "transfers";}public boolean hasTransactionBegun(){return active();}public Transaction beginTransaction(){return begin();}public String add(JSONObject r)throws RepositoryException{return insert("transfers",r);}};
   var indexes=new PointtransferRedcRepository(){public String getName(){return "transfer_index";}public String add(JSONObject r)throws RepositoryException{return insert("transfer_index",r);}};
   var points=new PointtransferMgmtService(){public void indexTransferAsync(String from,String to,String id){if(active())unsafeIndexes++;}};
   inject(points,"pointtransferRepository",transfers);inject(points,"userRepository",users);
   inject(settle,"articleRepository",articles);inject(settle,"windowRepository",windows);inject(settle,"settlementRepository",new LongArticleReadSettlementRepository(){public String getName(){return "symphony_article_long_read_settlement";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return query(q,a);}public void complete(String id,String history,long now)throws RepositoryException{super.complete(id,history,now);if(fault.equals("settlement"))throw new RepositoryException("after settlement");}public String discardForMissingArticle(String id,String article,long start,long now)throws RepositoryException{if(fault.equals("discard"))throw new RepositoryException("during orphan repair");return super.discardForMissingArticle(id,article,start,now);}});
   inject(settle,"historyRepository",new LongArticleReadHistoryRepository(){public String add(JSONObject r)throws RepositoryException{return insert("symphony_article_long_read_history",r);}});
   inject(settle,"sourceEventRepository",new ProfessionSourceEventRepository(){public String add(JSONObject r)throws RepositoryException{return insert("events",r);}});
   inject(settle,"pointtransferMgmtService",points);inject(settle,"messageService",new LongArticleReadSettlementMessageService(){public void send(JSONObject row){ok(!active());messages++;if(failMessage)throw new IllegalStateException("after committed message");}});
   optional("users",users);optional("indexes",indexes);
  }
  void optional(String field,Object value)throws Exception{try{inject(settle,field,value);}catch(NoSuchFieldException ignored){}}
  JSONObject one(String q,Object...a)throws RepositoryException{var r=query(q,a);return r.isEmpty()?null:r.getFirst();}
  void write(String q,Object...a)throws RepositoryException{try{sql(writer,q,a);}catch(Exception e){throw new RepositoryException(e);}}
  String insert(String table,JSONObject r)throws RepositoryException{if(!r.has("oId"))r.put("oId",Ids.genTimeMillisId());var cols=new ArrayList<>(r.keySet());String q="INSERT INTO `"+table+"` (`"+String.join("`,`",cols)+"`) VALUES("+String.join(",",Collections.nCopies(cols.size(),"?"))+")";write(q,cols.stream().map(r::get).toArray());if(fault.equals(table.equals("symphony_article_long_read_history")?"history":table.equals("transfer_index")?"index":table))throw new RepositoryException("after "+table);return r.getString("oId");}
  void reset()throws Exception{
   for(String table:List.of("transfer_index","transfers","events","symphony_article_long_read_settlement","symphony_article_long_read_history","symphony_article_long_read_user","symphony_article_long_read_window"))sql(db,"DELETE FROM "+table);
   sql(db,"DELETE FROM users");sql(db,"INSERT INTO users(oId,userStatus,userPoint,userUsedPoint) VALUES('1',0,1000,0),('2',0,1000,0),('3',0,1000,0)");sql(db,"DELETE FROM articles");sql(db,"INSERT INTO articles(oId,articleAuthorId,articleStatus,articleType,articleAnonymous,articleShowInList,articleTags,articleAnonymousView,articleTitle) VALUES('10','1',0,6,0,1,'tag',0,'test long article')");messages=0;unsafeIndexes=0;
  }
  void seed(int registered,int anon)throws Exception{sql(db,"INSERT INTO symphony_article_long_read_window VALUES('100','10',?,?,?,'OPEN','',0,?,?)",START,registered,anon,START,START);}
  int balance()throws Exception{return rows(db,"SELECT userPoint n FROM users WHERE oId='1'").getFirst().getInt("n");}
  void run(){settle.settle("10");}
  void untouched()throws Exception{ok(balance()==1000);for(String t:List.of("transfers","events","transfer_index","symphony_article_long_read_history","symphony_article_long_read_settlement"))ok(count(t)==0);ok(messages==0);ok(one("SELECT state FROM symphony_article_long_read_window").getString("state").equals("OPEN"));}
  void committed(int reward)throws Exception{ok(balance()==1000+reward);ok(count("transfers")==1);ok(count("events")==1);ok(count("symphony_article_long_read_history")==1);ok(count("symphony_article_long_read_settlement")==1);ok(one("SELECT state FROM symphony_article_long_read_window").getString("state").equals("SETTLED"));}
 }
 public static void main(String[]args)throws Exception{
  var p=new Properties();p.setProperty("runtimeDatabase","NONE");p.setProperty("contextPath","");org.b3log.latke.Latkes.setLatkeProps(p);org.b3log.latke.Latkes.setLocalProps(p);
  try(var f=new S()){
   if(args.length>0){
    switch(args[0]){
     case "setup" -> {f.reset();f.seed(2,0);System.out.println("SETUP_READY");}
     case "worker" -> {f.holdAuthor=args[1].equals("holder");f.releaseGate=args[2];System.out.println("STARTING_SETTLEMENT");System.out.flush();f.run();System.out.println("SETTLEMENT_DONE");}
     case "verify" -> {f.committed(20);ok(f.count("transfer_index")==2);System.out.println("PASS read settlement mysql 8 independent JVM contention commits one payout/history/event and two indexes");}
     default -> throw new IllegalArgumentException(args[0]);
    }
    return;
   }
   f.reset();f.seed(2,150);f.run();f.committed(820);var e=f.one("SELECT * FROM events");ok(e.getString("eventType").equals("long_article.read_settled"));ok(e.getString("subjectUserId").equals("1"));var payload=new JSONObject(e.getString("payloadJson"));ok(payload.getInt("countedAnonymousReaderCount")==100);ok(payload.getInt("rewardPoint")==820);f.run();f.committed(820);ok(f.messages==1);
   System.out.println("PASS read settlement mysql 1 actual formula, anonymous cap, author credit/history/profession payload and repeat once");
   for(int[] counts:new int[][]{{-1,0},{0,-1},{Integer.MAX_VALUE,0},{Integer.MAX_VALUE,150}}){f.reset();f.seed(counts[0],counts[1]);f.run();f.untouched();}
   System.out.println("PASS read settlement mysql 2 corrupt negative/overflow counts fail closed without subtracting author points or consuming window");
   for(String fault:List.of("history","points","transfers","events","index","settlement","commit")){f.reset();f.seed(2,0);f.fault=fault;f.run();f.fault="";f.untouched();f.run();f.committed(20);ok(f.count("transfer_index")==2);ok(f.unsafeIndexes==0);}
   System.out.println("PASS read settlement mysql 3 every write and real commit failure rolls back history/credit/event/index/gate with no premature notification, then retries once");
   f.reset();f.seed(2,0);f.fault="ack";f.run();f.fault="";f.committed(20);ok(f.messages==0);f.run();f.committed(20);ok(f.count("transfer_index")==2);ok(f.messages==0);
   System.out.println("PASS read settlement mysql 4 lost commit acknowledgement never publishes false completion or duplicates payout/index/event on retry");
   f.reset();f.seed(2,0);f.failMessage=true;f.run();f.failMessage=false;f.committed(20);f.run();f.committed(20);ok(f.messages==1);
   System.out.println("PASS read settlement mysql 5 post-commit notification failure does not change or repeat completed payout");
   f.reset();f.seed(0,0);f.run();ok(f.balance()==1000);ok(f.count("transfers")==0);ok(f.count("transfer_index")==0);ok(f.count("events")==1);ok(f.count("symphony_article_long_read_history")==1);
   System.out.println("PASS read settlement mysql 6 zero window retains historical event/history behavior without zero ledger/index");
   f.reset();f.seed(2,0);var pool=Executors.newFixedThreadPool(2);try{var gate=new CountDownLatch(1);var jobs=new ArrayList<Future<?>>();for(int i=0;i<2;i++)jobs.add(pool.submit(()->{try(var s=new S()){gate.await();s.run();}catch(Exception error){throw new RuntimeException(error);}}));gate.countDown();for(var j:jobs)j.get(20,TimeUnit.SECONDS);f.committed(20);ok(f.count("transfer_index")==2);}finally{pool.shutdownNow();}
   System.out.println("PASS read settlement mysql 7 competing independent JDBC roots settle one history, ledger, event and two indexes");
   f.reset();f.seed(2,0);var owner=f.begin();try{f.run();throw new AssertionError("nested settlement allowed");}catch(IllegalStateException expected){ok(owner.isActive());}finally{owner.rollback();}f.untouched();
   System.out.println("PASS read settlement mysql 9 nested caller root rejected before touching or ending the owner");
   f.reset();f.seed(2,0);sql(f.db,"DELETE FROM articles WHERE oId='10'");f.run();
   ok(f.balance()==1000);ok(f.count("symphony_article_long_read_history")==0);ok(f.count("transfers")==0);ok(f.count("events")==0);
   ok(f.one("SELECT state FROM symphony_article_long_read_window").getString("state").equals("SETTLED"));
   ok(f.one("SELECT state FROM symphony_article_long_read_settlement").getString("state").equals("DISCARDED"));
   f.run();ok(f.count("symphony_article_long_read_settlement")==1);
   System.out.println("PASS read settlement mysql 10 missing article is transactionally discarded once and does not retry forever");
   f.reset();f.seed(2,0);f.fault="discard";sql(f.db,"DELETE FROM articles WHERE oId='10'");f.run();f.fault="";
   ok(f.one("SELECT state FROM symphony_article_long_read_window").getString("state").equals("OPEN"));ok(f.count("symphony_article_long_read_settlement")==0);f.run();
   ok(f.one("SELECT state FROM symphony_article_long_read_window").getString("state").equals("SETTLED"));ok(f.count("symphony_article_long_read_settlement")==1);
   System.out.println("PASS read settlement mysql 11 orphan repair failure rolls back and later retry repairs the window");
  }
 }
}
