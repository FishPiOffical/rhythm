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

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.*;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;

/** Actual services and idempotency, with explicit SQLite/points/cache adapters. */
public class CheckinMgmtServiceTest {
    static Fixture current;
    static final long NOW=Instant.parse("2026-03-01T12:00:00Z").toEpochMilli();
    public static void main(String[] args)throws Exception{
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));var props=new Properties();props.setProperty("runtimeDatabase","NONE");
        for(String n:List.of("latkeProps","localProps")){var f=Latkes.class.getDeclaredField(n);f.setAccessible(true);f.set(null,props);}
        try(var f=new Fixture()){
            Assert.assertEquals(f.core.dailyLegacy("a"),20);Assert.assertEquals(f.scalar("SELECT userPoint FROM user"),520L);
            Assert.assertEquals(f.scalar("SELECT userCurrentCheckinStreak FROM user"),1L);Assert.assertEquals(f.bag().getInt("patchStart"),20260201);Assert.assertEquals(f.indexed,1);
            var result=f.api.usePatch("a","patch-one");Assert.assertEquals(result.keySet(),Set.of("itemCode","streak","streakStart","streakEnd","longestStreak","bag"));Assert.assertEquals(result.getInt("streak"),29);
            Assert.assertEquals(f.scalar("SELECT userCurrentCheckinStreak FROM user"),29L);Assert.assertEquals(f.scalar("SELECT userPoint FROM user"),520L);Assert.assertFalse(f.bag().has("patchStart"));Assert.assertEquals(f.bag().getInt("patchCheckinCard"),1);
            Assert.assertFalse(result.toString().contains("PRIVATE"));Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM pointtransfer"),1L);
            Assert.assertEquals(f.api.usePatch("a","patch-one").toString(),result.toString());expect(409,()->f.api.usePatch("a","new-key"));Assert.assertEquals(f.core.dailyLegacy("a"),-1);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM api_v3_request"),1L);Assert.assertEquals(f.bag().getInt("patchCheckinCard"),1);Assert.assertEquals(f.cache.getInt("userCurrentCheckinStreak"),29);
        }
        System.out.println("PASS daily gap hint, v3 patch, replay/new-key rejection and repeat daily share one owner state; patch grants no extra points or private fields");
        for(String failure:List.of("cloud.update","user.update","receipt.add","receipt.update","commit")){
            try(var f=new Fixture()){
                f.readyPatch();String before=f.snapshot();f.fail=failure;try{f.api.usePatch("a","failed");Assert.fail(failure);}catch(Exception rejected){}
                Assert.assertEquals(f.snapshot(),before,failure);Assert.assertFalse(f.active());Assert.assertEquals(f.indexed,0);f.fail="";f.api.usePatch("a","failed");Assert.assertEquals(f.bag().getInt("patchCheckinCard"),1);
            }
        }
        System.out.println("PASS bag/user/receipt/commit failures roll card, hint, streak and journal back, with same-key retry succeeding once");
        for(String failure:List.of("pointtransfer.select","transfer.partial","cloud.update","user.update","commit")){
            try(var f=new Fixture()){String before=f.snapshot();f.fail=failure;Assert.assertEquals(f.core.dailyLegacy("a"),-1);Assert.assertEquals(f.snapshot(),before,failure);Assert.assertEquals(f.indexed,0);Assert.assertFalse(f.active());}
        }
        try(var f=new Fixture()){
            f.exec("UPDATE user SET userCurrentCheckinStreakStart=20260220,userCurrentCheckinStreakEnd=20260228,userLongestCheckinStreakStart=20260220,userLongestCheckinStreakEnd=20260228");
            f.fail="bonus.partial";String before=f.snapshot();Assert.assertEquals(f.core.dailyLegacy("a"),-1);Assert.assertEquals(f.snapshot(),before);Assert.assertEquals(f.indexed,0);
        }
        System.out.println("PASS daily transfer/hint/streak/commit and second bonus transfer failures roll back all balances and both reward records");
        try(var f=new Fixture()){
            f.exec("UPDATE user SET userCurrentCheckinStreakStart=20260220,userCurrentCheckinStreakEnd=20260228,userLongestCheckinStreakStart=20260220,userLongestCheckinStreakEnd=20260228");
            Assert.assertEquals(f.core.dailyLegacy("a"),20);Assert.assertEquals(f.scalar("SELECT userPoint FROM user"),620L);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM pointtransfer"),2L);Assert.assertEquals(f.indexed,2);
            Assert.assertEquals(f.scalar("SELECT userCurrentCheckinStreak FROM user"),10L);Assert.assertEquals(f.core.dailyLegacy("a"),-1);Assert.assertEquals(f.indexed,2);
        }
        try(var f=new Fixture()){
            f.exec("UPDATE user SET userCurrentCheckinStreakStart=0,userCurrentCheckinStreakEnd=0,userLongestCheckinStreakStart=0,userLongestCheckinStreakEnd=0");
            Assert.assertEquals(f.core.dailyLegacy("a"),20);Assert.assertEquals(f.scalar("SELECT userCurrentCheckinStreak FROM user"),1L);Assert.assertEquals(f.scalar("SELECT userLongestCheckinStreak FROM user"),1L);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM pointtransfer"),1L);
        }
        System.out.println("PASS first checkin and tenth natural-day streak preserve base and bonus fees exactly once");
        for(int legacy:List.of(-1,-2,-3,-4)){
            try(var f=new Fixture()){
                f.readyPatch();switch(legacy){case -1->f.setBag(f.bag().put("patchCheckinCard",0));case -2->f.exec("UPDATE user SET userLongestCheckinStreakStart=20260301");case -3->f.exec("UPDATE user SET userCurrentCheckinStreakEnd=20260228");case -4->f.setBag(f.bag().put("patchStart",20260230));}
                String before=f.snapshot();Assert.assertEquals(f.core.patchLegacy("a"),legacy);Assert.assertEquals(f.snapshot(),before);expect(409,()->f.api.usePatch("a","unavailable"));Assert.assertEquals(f.snapshot(),before);
            }
        }
        try(var f=new Fixture()){f.readyPatch();Assert.assertEquals(f.core.patchLegacy("a"),0);Assert.assertEquals(f.bag().getInt("patchCheckinCard"),1);Assert.assertEquals(f.core.patchLegacy("a"),-4);}
        System.out.println("PASS legacy success/error codes remain and every rejected patch retains the card, hint and streak; legacy/v3 share the same core");
        try(var f=new Fixture()){
            f.exec("INSERT INTO pointtransfer VALUES('old-payment','sys','a',8,20,'a',"+NOW+")");String before=f.snapshot();Assert.assertEquals(f.core.dailyLegacy("a"),-1);Assert.assertEquals(f.snapshot(),before);
        }
        for(String sql:List.of("UPDATE user SET userStatus=1","DELETE FROM user"))try(var f=new Fixture()){f.exec(sql);String before=f.snapshot();Assert.assertEquals(f.core.dailyLegacy("a"),-1);expect(404,()->f.api.usePatch("a","inactive"));Assert.assertEquals(f.snapshot(),before);}
        System.out.println("PASS current raw daily transfer dedup and missing/disabled owner checks prevent duplicate payouts or unauthorized patches");
        try(var f=new Fixture()){
            try{f.core.patchInCurrentTransaction("a");Assert.fail();}catch(IllegalStateException expected){}
            var tx=new Rows("user").beginTransaction();try{f.core.patchInCurrentTransaction("a");Assert.fail();}catch(IllegalStateException expected){}finally{tx.rollback();}
            f.readyPatch();f.fail="cache";Assert.assertEquals(f.core.patchLegacy("a"),0);Assert.assertEquals(f.evicted,1);Assert.assertEquals(f.bag().getInt("patchCheckinCard"),1);
        }
        System.out.println("PASS caller transaction/transfer/cache boundaries are mandatory; cache refresh failure evicts after a committed successful patch");
        try(var f=new Fixture()){
            f.exec("DELETE FROM cloud");Assert.assertEquals(f.core.dailyLegacy("a"),20);Assert.assertEquals(f.bag().getInt("patchStart"),20260201);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM cloud"),1L);
        }
        try(var f=new Fixture()){
            f.exec("DELETE FROM cloud");f.fail="cloud.add";String before=f.snapshot();Assert.assertEquals(f.core.dailyLegacy("a"),-1);Assert.assertEquals(f.snapshot(),before);
        }
        try(var f=new Fixture()){
            f.readyPatch();f.exec("INSERT INTO cloud VALUES('aaa-old','a','sys-bag','{\"patchCheckinCard\":999}')");f.api.usePatch("a","duplicate-bag");Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM cloud"),1L);Assert.assertEquals(f.bag().getInt("patchCheckinCard"),1);Assert.assertEquals(f.bag().getInt("other"),7);
        }
        System.out.println("PASS daily hint creates a missing bag atomically, creation failures undo payment, and patch canonicalizes duplicate bag rows without losing unrelated items");
        try(var f=new Fixture()){
            f.readyPatch();f.setBag(f.bag().put("checkin1day",2).put("sysCheckinRemain",3));
            var exemption=f.api.useExemption("a","checkin1day","same-key");var patch=f.api.usePatch("a","same-key");
            Assert.assertEquals(exemption.getInt("remainingDays"),4);Assert.assertEquals(f.api.useExemption("a","checkin1day","same-key").toString(),exemption.toString());Assert.assertEquals(f.api.usePatch("a","same-key").toString(),patch.toString());
            Assert.assertEquals(f.bag().getInt("checkin1day"),1);Assert.assertEquals(f.bag().getInt("sysCheckinRemain"),4);Assert.assertEquals(f.bag().getInt("patchCheckinCard"),1);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM api_v3_request"),2L);
            expect(422,()->f.api.usePatch("a","invalid key"));Assert.assertFalse(f.active());
        }
        System.out.println("PASS exemption and patch retain independent idempotency operations while sharing bag writes and rejecting malformed keys");
        try(var f=new Fixture()){
            f.readyPatch();var receipt=f.api.usePatch("a","yesterday-patch");f.clock=NOW+86400000L;
            Assert.assertEquals(f.core.dailyLegacy("a"),20);Assert.assertEquals(f.scalar("SELECT userCurrentCheckinStreak FROM user"),30L);Assert.assertEquals(f.scalar("SELECT userPoint FROM user"),620L);
            Assert.assertEquals(f.api.usePatch("a","yesterday-patch").toString(),receipt.toString());Assert.assertEquals(f.scalar("SELECT userCurrentCheckinStreak FROM user"),30L);Assert.assertEquals(f.bag().getInt("patchCheckinCard"),1);
            Assert.assertEquals(f.core.dailyLegacy("a"),-1);Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM pointtransfer"),2L);
        }
        System.out.println("PASS next-day daily checkin extends a patched run with the original milestone reward while historical patch replay cannot overwrite the newer streak");
    }
    @FunctionalInterface interface Checked{Object run()throws Exception;}
    static void expect(int status,Checked run)throws Exception{try{run.run();Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    static void inject(Object bean,String name,Object value)throws Exception{var f=bean.getClass().getDeclaredField(name);f.setAccessible(true);f.set(bean,value);}
    static class Fixed extends CheckinMgmtService{@Override protected long now(){return current.clock;}@Override protected int dailyAmount(){return 20;}}
    static class Fixture implements AutoCloseable{
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");String fail="";int indexed,evicted;JSONObject cache;long clock=NOW;
        final List<String> oldNames=new ArrayList<>();
        final List<String> queryLog=new ArrayList<>();
        final CheckinMgmtService core=new Fixed();final ApiV3BagMgmtService api=new ApiV3BagMgmtService();
        Fixture()throws Exception{
            current=this;
            exec("CREATE TABLE user(oId TEXT PRIMARY KEY,userStatus INT,userPoint INT,userCheckinTime BIGINT,userCurrentCheckinStreakStart INT,userCurrentCheckinStreakEnd INT,userCurrentCheckinStreak INT,userLongestCheckinStreakStart INT,userLongestCheckinStreakEnd INT,userLongestCheckinStreak INT,userEmail TEXT)");
            exec("CREATE TABLE cloud(oId TEXT PRIMARY KEY,userId TEXT,gameId TEXT,data TEXT)");exec("CREATE TABLE pointtransfer(oId TEXT PRIMARY KEY,fromId TEXT,toId TEXT,type INT,sum INT,dataId TEXT,time BIGINT)");exec("CREATE TABLE api_v3_request(oId TEXT PRIMARY KEY,userId TEXT,operation TEXT,requestHash TEXT,responseJson TEXT,createdAt BIGINT)");
            exec("INSERT INTO user VALUES('a',0,500,0,20260201,20260227,27,20260201,20260227,27,'PRIVATE-email')");exec("INSERT INTO cloud VALUES('bag','a','sys-bag',?)",new JSONObject().put("patchCheckinCard",2).put("other",7).put("opaque","PRIVATE-value").toString());
            var bagService=new BagMgmtService();inject(bagService,"cloudRepository",new Cloud());inject(bagService,"userRepository",new UserRepository());
            for(var entry:Map.<String,Object>of("users",new UserRepository(),"transfers",new PointtransferRepository(),"bags",bagService,"points",new PointtransferMgmtService()).entrySet()){
                var field=CheckinMgmtService.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(core,entry.getValue());
            }
            var journal=new ApiV3IdempotencyMgmtService();inject(journal,"requestRepository",new Requests());inject(api,"bags",bagService);inject(api,"idempotency",journal);inject(api,"checkins",core);
        }
        void readyPatch()throws Exception{exec("UPDATE user SET userCurrentCheckinStreakStart=20260301,userCurrentCheckinStreakEnd=20260301,userCurrentCheckinStreak=1,userCheckinTime=?",NOW);setBag(bag().put("patchStart",20260201));}
        boolean active(){try{return !db.getAutoCommit();}catch(Exception e){throw new RuntimeException(e);}}
        void exec(String sql,Object...args)throws Exception{Rows.write(sql,args);}
        long scalar(String sql)throws Exception{return Rows.query(sql).getFirst().getLong(Rows.query(sql).getFirst().keySet().iterator().next());}
        JSONObject bag()throws Exception{return new JSONObject(Rows.query("SELECT data FROM cloud WHERE oId='bag'").getFirst().getString("data"));}
        void setBag(JSONObject bag)throws Exception{exec("UPDATE cloud SET data=? WHERE oId='bag'",bag.toString());}
        String snapshot()throws Exception{StringBuilder out=new StringBuilder();for(String table:List.of("user","cloud","pointtransfer","api_v3_request"))out.append(Rows.query("SELECT * FROM "+table+" ORDER BY oId"));return out.toString();}
        @Override public void close()throws Exception{db.close();current=null;}
    }
    public static void refresh(String actor)throws RepositoryException{Assert.assertFalse(current.active());Assert.assertFalse(ArticlePublicationCaches.active());if("cache".equals(current.fail))throw new RepositoryException("cache failure");current.cache=Rows.first(Rows.query("SELECT * FROM user WHERE oId=?",actor));}
    public static void evict(String actor){Assert.assertFalse(current.active());current.evicted++;current.cache=null;}
    public static void evictName(String actor,String oldName){Assert.assertFalse(current.active());current.oldNames.add(oldName);evict(actor);}
    public static class Rows extends AbstractRepository{
        final String table;public Rows(String t){super(t);table=t;}@Override public String getName(){return table;}@Override public boolean hasTransactionBegun(){return current.active();}
        public static JSONObject first(List<JSONObject> rows){return rows.isEmpty()?null:rows.getFirst();}
        @Override public Transaction beginTransaction(){Assert.assertFalse(current.active());try{current.db.setAutoCommit(false);}catch(Exception e){throw new RuntimeException(e);}return new Transaction(){boolean open=true;public boolean isActive(){return open;}public void commit(){if("commit".equals(current.fail))throw new IllegalStateException("commit failure");finish(true);}public void rollback(){finish(false);}void finish(boolean commit){try{if(commit)current.db.commit();else current.db.rollback();current.db.setAutoCommit(true);open=false;}catch(Exception e){throw new RuntimeException(e);}}};}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{current.queryLog.add(sql);if((table+".select").equals(current.fail))throw new RepositoryException("injected lookup failure");return query(sql,args);}
        @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException{
            Assert.assertTrue(current.active());if((table+".update").equals(current.fail))throw new RepositoryException("injected update failure");
            var names=fields.length==0?new ArrayList<>(row.keySet()):Arrays.asList(fields);for(String name:names)Assert.assertTrue(name.matches("[A-Za-z0-9_]+"));
            var values=new ArrayList<Object>();for(String name:names)values.add(row.get(name));values.add(id);
            write("UPDATE "+table+" SET "+String.join(",",names.stream().map(n->n+"=?").toList())+" WHERE oId=?",values.toArray());
        }
        @Override public void remove(String id)throws RepositoryException{Assert.assertTrue(current.active());write("DELETE FROM "+table+" WHERE oId=?",id);}
        public static List<JSONObject> query(String sql,Object...args)throws RepositoryException{try(var s=current.db.prepareStatement(sql.replace(" FOR UPDATE",""))){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));out.add(row);}return out;}}catch(Exception e){throw new RepositoryException(e);}}
        public static void write(String sql,Object...args)throws RepositoryException{try(var s=current.db.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}catch(Exception e){throw new RepositoryException(e);}}
    }
    static class Cloud extends CloudRepository{
        final Rows rows=new Rows("cloud");@Override public String getName(){return "cloud";}@Override public boolean hasTransactionBegun(){return current.active();}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{current.queryLog.add(sql);return Rows.query(sql,args);}@Override public void update(String id,JSONObject row,String...fields)throws RepositoryException{rows.update(id,row,fields);}@Override public void remove(String id)throws RepositoryException{rows.remove(id);}
        @Override public String add(JSONObject row)throws RepositoryException{Assert.assertTrue(current.active());if("cloud.add".equals(current.fail))throw new RepositoryException("cloud add failure");String id="bag";Rows.write("INSERT INTO cloud VALUES(?,?,?,?)",id,row.getString("userId"),row.getString("gameId"),row.getString("data"));return id;}
    }
    static class Requests extends ApiV3RequestRepository{
        @Override public String getName(){return "api_v3_request";}@Override public boolean hasTransactionBegun(){return current.active();}@Override public Transaction beginTransaction(){return new Rows("api_v3_request").beginTransaction();}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return Rows.query(sql,args);}
        @Override public String add(JSONObject row)throws RepositoryException{if("receipt.add".equals(current.fail))throw new RepositoryException("receipt failure");Rows.write("INSERT INTO api_v3_request VALUES(?,?,?,?,?,?)",row.getString("oId"),row.getString("userId"),row.getString("operation"),row.getString("requestHash"),row.getString("responseJson"),row.getLong("createdAt"));return row.getString("oId");}
        @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException{if("receipt.update".equals(current.fail))throw new RepositoryException("receipt result failure");new Rows("api_v3_request").update(id,row,fields);}
    }
    public static class Payments{
        public <T>T executeWithTransferLock(String from,String to,Callable<T> work)throws Exception{return AccountPairLocks.execute(List.of(new AccountPairLocks.Pair(from,to)),work);}
        public String transferInCurrentTransaction(String from,String actor,int type,int amount,String data,long now,String memo,PointtransferSource source,boolean index){
            Assert.assertTrue(current.active());Assert.assertTrue(ArticlePublicationCaches.active());Assert.assertTrue(AccountPairLocks.lock(from,actor).isHeldByCurrentThread());Assert.assertFalse(index);Assert.assertEquals(from,"sys");Assert.assertEquals(data,actor);
            try{Rows.write("UPDATE user SET userPoint=userPoint+? WHERE oId=?",amount,actor);if("transfer.partial".equals(current.fail)||("bonus.partial".equals(current.fail)&&type==11))return null;
                String id="transfer-"+(current.scalar("SELECT COUNT(*) FROM pointtransfer")+1);Rows.write("INSERT INTO pointtransfer VALUES(?,?,?,?,?,?,?)",id,from,actor,type,amount,data,now);return id;
            }catch(Exception e){throw new RuntimeException(e);}
        }
        public void indexTransferAsync(String from,String actor,String id){Assert.assertFalse(current.active());Assert.assertFalse(ArticlePublicationCaches.active());current.indexed++;}
    }
}
