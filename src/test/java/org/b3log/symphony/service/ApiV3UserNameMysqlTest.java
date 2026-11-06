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
import org.b3log.latke.repository.jdbc.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import sun.misc.Unsafe;
import java.sql.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real MySQL/Connector-J/FOR UPDATE with production rename, ledger and receipt coordinators. */
public class ApiV3UserNameMysqlTest {
    static String url;static int price;
    static final ThreadLocal<String> fault=ThreadLocal.withInitial(()->"");
    static final AtomicInteger lockingQueries=new AtomicInteger();
    static final List<Connection> connections=new CopyOnWriteArrayList<>();
    static final List<String> namespaceOrder=new CopyOnWriteArrayList<>();
    static CountDownLatch namespaceHeld,releaseNamespace,namespaceAttempt;
    static Connection connect()throws SQLException {Connection c=DriverManager.getConnection(url,"root","");connections.add(c);return c;}
    static void inject(Object o,String n,Object v)throws Exception{for(Class<?> t=o.getClass();t!=null;t=t.getSuperclass())try{var f=t.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
    static boolean active(){var tx=JdbcRepository.TX.get();return tx!=null&&tx.isActive();}
    static Connection writer(){Assert.assertTrue(active(),"SQL requires live owner transaction");return JdbcRepository.TX.get().getConnection();}
    static void execute(Connection c,String sql,Object...args)throws RepositoryException {
        try(var ps=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);ps.executeUpdate();}
        catch(SQLException e){throw new RepositoryException(e);}
    }
    static List<JSONObject> rows(Connection c,String sql,Object...args)throws RepositoryException {
        if(sql.contains("FOR UPDATE"))lockingQueries.incrementAndGet();
        try(var ps=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);try(var rs=ps.executeQuery()){var result=new ArrayList<JSONObject>();while(rs.next()){var row=new JSONObject();for(int i=1;i<=rs.getMetaData().getColumnCount();i++)row.put(rs.getMetaData().getColumnLabel(i),rs.getObject(i));result.add(row);}return result;}}
        catch(SQLException e){throw new RepositoryException(e);}
    }
    static Transaction begin(){
        try{
            Assert.assertFalse(active());Connection real=connect();real.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);real.setAutoCommit(false);
            Connection wrapped=(Connection)Proxy.newProxyInstance(ApiV3UserNameMysqlTest.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                try{
                    if(m.getName().equals("commit")&&fault.get().equals("commit"))throw new SQLException("injected before commit");
                    Object value=m.invoke(real,a);
                    if(m.getName().equals("commit")&&fault.get().equals("lost-ack"))throw new SQLException("injected after commit");
                    return value;
                }catch(InvocationTargetException e){throw e.getCause();}
            });
            var access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);var tx=(JdbcTransaction)((Unsafe)access.get(null)).allocateInstance(JdbcTransaction.class);
            var f=JdbcTransaction.class.getDeclaredField("connection");f.setAccessible(true);f.set(tx,wrapped);f=JdbcTransaction.class.getDeclaredField("isActive");f.setAccessible(true);f.setBoolean(tx,true);JdbcRepository.TX.set(tx);return ApiV3Transactions.strict(tx);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    static void insert(String table,JSONObject row)throws RepositoryException {
        var names=new ArrayList<>(row.keySet());execute(writer(),"INSERT INTO `"+table+"` (`"+String.join("`,`",names)+"`) VALUES("+String.join(",",Collections.nCopies(names.size(),"?"))+")",names.stream().map(row::get).toArray());
    }
    static void update(String table,String id,JSONObject row,String...fields)throws RepositoryException {
        var names=fields.length==0?new ArrayList<>(row.keySet()):new ArrayList<>(List.of(fields));names.remove("oId");var args=new ArrayList<>();for(String n:names)args.add(row.get(n));args.add(id);
        execute(writer(),"UPDATE `"+table+"` SET "+String.join(",",names.stream().map(n->"`"+n+"`=?").toList())+" WHERE oId=?",args.toArray());
    }
    static void failAfter(String stage)throws RepositoryException{if(fault.get().equals(stage))throw new RepositoryException("injected after "+stage);}
    static class Fixture {
        final ApiV3UserNameService service=new ApiV3UserNameService(){protected boolean allowed(String actor){Assert.assertFalse(active());return true;}protected void inspect(String name){Assert.assertFalse(active());}};
        Fixture()throws Exception {
            var users=new UserRepository(){
                public boolean hasTransactionBegun(){return active();}
                public String getName(){return "user";}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return rows(writer(),q,a);}
                public JSONObject getForUpdate(String id)throws RepositoryException {var r=select("SELECT * FROM `user` WHERE oId=? FOR UPDATE",id);return r.isEmpty()?null:r.getFirst();}
                public void updateColumnsInCurrentTransaction(String id,JSONObject row)throws RepositoryException{ApiV3UserNameMysqlTest.update("user",id,row,row.keySet().toArray(new String[0]));failAfter("name");}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException{Assert.assertTrue(ArticlePublicationCaches.active());ApiV3UserNameMysqlTest.update("user",id,row,fields);failAfter("points");}
                public void evictUserNameCaches(String id,String old){Assert.assertFalse(active());}
                public void refreshSettingsCache(String id){Assert.assertFalse(active());}
                public void evictSettingsCaches(String id){Assert.assertFalse(active());}
            };
            var options=new OptionRepository(){
                public boolean hasTransactionBegun(){return active();}
                public String getName(){return "option";}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException {
                    if(Thread.currentThread().getName().equals("rename-B")&&namespaceAttempt!=null)namespaceAttempt.countDown();
                    var result=rows(writer(),q,a);namespaceOrder.add(Thread.currentThread().getName());
                    if(Thread.currentThread().getName().equals("rename-A")&&namespaceHeld!=null&&namespaceHeld.getCount()!=0){namespaceHeld.countDown();try{if(!releaseNamespace.await(10,TimeUnit.SECONDS))throw new AssertionError("namespace release timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RepositoryException(e);}}
                    return result;
                }
            };
            var requests=new ApiV3RequestRepository(){
                public Transaction beginTransaction(){return begin();}public boolean hasTransactionBegun(){return active();}public String getName(){return "api_v3_request";}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return rows(writer(),q,a);}
                public String add(JSONObject row)throws RepositoryException{insert(getName(),row);return row.getString("oId");}
                public void update(String id,JSONObject row,String...fields)throws RepositoryException{ApiV3UserNameMysqlTest.update(getName(),id,row,fields);failAfter("receipt");}
            };
            var transfers=new PointtransferRepository(){
                public Transaction beginTransaction(){return begin();}public boolean hasTransactionBegun(){return active();}public String getName(){return "pointtransfer";}
                public List<JSONObject> select(String q,Object...a)throws RepositoryException{return rows(writer(),q,a);}
                public String add(JSONObject row)throws RepositoryException{String id=org.b3log.latke.util.Ids.genTimeMillisId();row.put("oId",id);insert(getName(),row);failAfter("ledger");return id;}
            };
            var index=new PointtransferRedcRepository(){public String getName(){return "pointtransfer_redc";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return rows(writer(),q,a);}};
            var names=new UserNameMgmtService();inject(names,"users",users);inject(names,"options",options);
            var points=new PointtransferMgmtService();inject(points,"userRepository",users);inject(points,"pointtransferRepository",transfers);
            inject(service,"users",users);inject(service,"names",names);inject(service,"requests",requests);inject(service,"points",points);inject(service,"transfers",transfers);inject(service,"index",index);
        }
        JSONObject rename(String actor,String key,String name)throws Exception{return service.rename(actor,new JSONObject().put("userName",name),key);}
    }
    static long scalar(Connection c,String sql)throws Exception{return rows(c,sql).getFirst().getLong("v");}
    static void seed(Connection c)throws Exception {for(String table:List.of("pointtransfer_redc","api_v3_request","pointtransfer","user","option"))execute(c,"DELETE FROM `"+table+"`");execute(c,"INSERT INTO `option`(oId,optionValue) VALUES(?,?)",Option.ID_C_STATISTIC_MEMBER_COUNT,"2");execute(c,"INSERT INTO `user`(oId,userName,userStatus,userPoint,userUsedPoint) VALUES('101','Old',0,?,0),('102','Other',0,?,0)",price*5,price*5);}
    interface Work{void run()throws Exception;}
    static void reject(int status,Work work)throws Exception{try{work.run();Assert.fail("Expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    static String name(Connection c,String actor)throws Exception{return rows(c,"SELECT userName FROM `user` WHERE oId=?",actor).getFirst().getString("userName");}
    public static void main(String[] args)throws Exception {
        url=args[0];price=Integer.parseInt(args[2]);Assert.assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/rhythm_v3_username_[a-f0-9]+\\?.*"));Assert.assertEquals(price,Symphonys.POINT_CHANGE_USERNAME);
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(Connection observer=connect()){
            for(String statement:Files.readString(Path.of(args[1])).split(";"))if(!statement.isBlank())execute(observer,statement);
            var version=rows(observer,"SELECT VERSION() AS version,@@sql_mode AS mode,@@transaction_isolation AS isolation").getFirst();Assert.assertTrue(version.getString("mode").contains("STRICT_TRANS_TABLES"));System.out.println("MySQL "+version+" Connector-J "+observer.getMetaData().getDriverVersion()+" real configured price="+price);
            Fixture f=new Fixture();seed(observer);var first=f.rename("101","first","New");Assert.assertEquals(first.getInt("paidPoints"),price);Assert.assertEquals(name(observer,"101"),"New");Assert.assertEquals(scalar(observer,"SELECT userPoint AS v FROM `user` WHERE oId='101'"),(long)price*4);Assert.assertEquals(scalar(observer,"SELECT COUNT(*) AS v FROM pointtransfer"),1L);Assert.assertEquals(scalar(observer,"SELECT COUNT(*) AS v FROM api_v3_request"),1L);Assert.assertEquals(scalar(observer,"SELECT COUNT(*) AS v FROM pointtransfer_redc"),2L);
            f.rename("101","later","Newest");Assert.assertEquals(f.rename("101","first","New").toString(),first.toString());Assert.assertEquals(name(observer,"101"),"Newest");Assert.assertEquals(scalar(observer,"SELECT userPoint AS v FROM `user` WHERE oId='101'"),(long)price*3);reject(409,()->f.rename("101","first","Different"));
            System.out.println("PASS mysql 1 real configured fee, name/ledger/receipt commit, index and idempotent replay preserve later name");
            for(String stage:List.of("name","points","ledger","receipt","commit")){seed(observer);fault.set(stage);reject(503,()->f.rename("101","rollback","New"));fault.remove();Assert.assertEquals(name(observer,"101"),"Old");Assert.assertEquals(scalar(observer,"SELECT userPoint AS v FROM `user` WHERE oId='101'"),(long)price*5);for(String table:List.of("pointtransfer","api_v3_request","pointtransfer_redc"))Assert.assertEquals(scalar(observer,"SELECT COUNT(*) AS v FROM "+table),0L);f.rename("101","rollback","New");}
            System.out.println("PASS mysql 2 real JDBC rollback after name/debit/ledger/receipt and commit faults, same-key retry recovers");
            seed(observer);fault.set("lost-ack");reject(503,()->f.rename("101","ack","New"));fault.remove();Assert.assertEquals(name(observer,"101"),"New");Assert.assertEquals(f.rename("101","ack","New").getString("userName"),"New");Assert.assertEquals(scalar(observer,"SELECT COUNT(*) AS v FROM pointtransfer"),1L);Assert.assertEquals(scalar(observer,"SELECT COUNT(*) AS v FROM pointtransfer_redc"),2L);
            System.out.println("PASS mysql 3 post-commit acknowledgement loss replays one durable charge and repairs index");
            for(String next:List.of("-","---","abc-def","n".repeat(64))){seed(observer);String old="p".repeat(64);execute(observer,"UPDATE `user` SET userName=? WHERE oId='101'",old);f.rename("101","bounded",next);JSONObject row=rows(observer,"SELECT * FROM pointtransfer").getFirst();Assert.assertEquals(row.getString("dataId"),next);Assert.assertEquals(row.getString("memo"),old);Assert.assertEquals(UserNamePointData.description(row,"{oldName} -> {newName}"),old+" -> "+next);Assert.assertEquals(name(observer,"101"),next);}
            System.out.println("PASS mysql 4 real VARCHAR lengths accept hyphens and 64-to-64 rename, production formatter preserves names");
            seed(observer);namespaceHeld=new CountDownLatch(1);releaseNamespace=new CountDownLatch(1);namespaceAttempt=new CountDownLatch(1);namespaceOrder.clear();var pool=Executors.newFixedThreadPool(2);try{
                Future<JSONObject> a=pool.submit(()->{Thread.currentThread().setName("rename-A");return new Fixture().rename("101","race-A","Shared");});Assert.assertTrue(namespaceHeld.await(10,TimeUnit.SECONDS));Future<Integer> b=pool.submit(()->{Thread.currentThread().setName("rename-B");try{new Fixture().rename("102","race-B","Shared");return 200;}catch(ApiV3Exception e){return e.status;}});Assert.assertTrue(namespaceAttempt.await(10,TimeUnit.SECONDS));Thread.sleep(200);Assert.assertFalse(b.isDone(),"second real connection must wait for namespace row");releaseNamespace.countDown();Assert.assertEquals(a.get(15,TimeUnit.SECONDS).getString("userName"),"Shared");Assert.assertEquals(b.get(15,TimeUnit.SECONDS),Integer.valueOf(409));
            }finally{releaseNamespace.countDown();pool.shutdownNow();Assert.assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));namespaceHeld=null;namespaceAttempt=null;}
            Assert.assertEquals(name(observer,"101"),"Shared");Assert.assertEquals(name(observer,"102"),"Other");Assert.assertEquals(scalar(observer,"SELECT userPoint AS v FROM `user` WHERE oId='102'"),(long)price*5);Assert.assertEquals(scalar(observer,"SELECT COUNT(*) AS v FROM pointtransfer"),1L);Assert.assertEquals(scalar(observer,"SELECT COUNT(*) AS v FROM api_v3_request"),1L);Assert.assertTrue(namespaceOrder.indexOf("rename-A")<namespaceOrder.indexOf("rename-B"));
            System.out.println("PASS mysql 5 actual FOR UPDATE namespace blocks competing connection; one name/charge/receipt wins and loser remains unchanged");
            Assert.assertTrue(lockingQueries.get()>50);System.out.println("Executed "+lockingQueries+" unmodified FOR UPDATE queries; adapter caches/censor, no HTTP/IoC end-to-end claim");
        }finally{fault.remove();for(Connection c:connections)if(!c.isClosed())c.close();JdbcRepository.TX.remove();}
    }
}
