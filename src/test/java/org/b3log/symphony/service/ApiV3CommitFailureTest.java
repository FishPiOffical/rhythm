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

import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import org.b3log.symphony.util.*;
import org.b3log.symphony.repository.ApiV3NotificationRevisionRepository;
import org.json.JSONObject;
import org.testng.Assert;
import sun.misc.Unsafe;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;

/** Actual bundled JdbcTransaction and coordinator, with SQLite commit fault injection; no application DB. */
public class ApiV3CommitFailureTest {
    enum Fault { NONE, BEFORE_COMMIT, AFTER_COMMIT }
    /** Skip only the pool-opening constructor; commit/rollback/dispose are real bundled methods. */
    static JdbcTransaction transaction(Connection connection)throws Exception {
        var access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        var tx=(JdbcTransaction)((Unsafe)access.get(null)).allocateInstance(JdbcTransaction.class);
        var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,connection);
        field=JdbcTransaction.class.getDeclaredField("isActive");field.setAccessible(true);field.setBoolean(tx,true);
        JdbcRepository.TX.set(tx);return tx;
    }
    static class Store implements ApiV3MutationStore,AutoCloseable {
        final Path file=Files.createTempFile("rhythm-commit-fault-",".sqlite");final String url="jdbc:sqlite:"+file;
        final Connection observer=DriverManager.getConnection(url);
        Fault fault=Fault.NONE;JdbcTransaction delegate;Connection writer;int commits,rollbacks;
        final ApiV3NotificationRevisionRepository markers=new ApiV3NotificationRevisionRepository(){
            @Override public String getName(){return "marker";}
            @Override public boolean hasTransactionBegun(){return Store.this.hasTransactionBegun();}
            @Override protected String upsertSql(){return super.upsertSql().replace("ON DUPLICATE KEY UPDATE revision=VALUES(revision),updatedAt=VALUES(updatedAt)","ON CONFLICT(oId) DO UPDATE SET revision=excluded.revision,updatedAt=excluded.updatedAt");}
        };
        Store()throws Exception {try(var s=observer.createStatement()){s.execute("CREATE TABLE wallet(balance INT)");s.execute("INSERT INTO wallet VALUES(500)");s.execute("CREATE TABLE receipt(oId TEXT PRIMARY KEY,requestHash TEXT,responseJson TEXT)");s.execute("CREATE TABLE marker(oId TEXT PRIMARY KEY,revision TEXT,updatedAt BIGINT)");}}
        Transaction begin(boolean strict){
            try{
                final Connection real=DriverManager.getConnection(url);real.setAutoCommit(false);
                writer=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                    try{
                        if(method.getName().equals("commit")){
                            commits++;if(fault==Fault.BEFORE_COMMIT)throw new SQLException("injected rejection before durable commit");
                            real.commit();if(fault==Fault.AFTER_COMMIT)throw new SQLException("injected lost acknowledgement after durable commit");return null;
                        }
                        if(method.getName().equals("rollback"))rollbacks++;
                        return method.invoke(real,args);
                    }catch(InvocationTargetException e){throw e.getCause();}
                });
                delegate=transaction(writer);return strict?ApiV3Transactions.strict(delegate):delegate;
            }catch(Exception e){throw new RuntimeException(e);}
        }
        public Transaction beginTransaction(){return begin(true);}
        public boolean hasTransactionBegun(){return delegate!=null&&delegate.isActive();}
        public JSONObject getForUpdate(String id)throws RepositoryException {
            try(var s=writer.prepareStatement("SELECT * FROM receipt WHERE oId=?")){s.setString(1,id);try(var r=s.executeQuery()){return r.next()?new JSONObject().put("oId",r.getString(1)).put("requestHash",r.getString(2)).put("responseJson",r.getString(3)):null;}}catch(SQLException e){throw new RepositoryException(e);}
        }
        public String add(JSONObject row)throws RepositoryException {
            try(var s=writer.prepareStatement("INSERT INTO receipt VALUES(?,?,?)")){s.setString(1,row.getString("oId"));s.setString(2,row.getString("requestHash"));s.setString(3,row.getString("responseJson"));s.executeUpdate();return row.getString("oId");}catch(SQLException e){throw new RepositoryException(e);}
        }
        public void update(String id,JSONObject row,String... fields)throws RepositoryException {
            try(var s=writer.prepareStatement("UPDATE receipt SET responseJson=? WHERE oId=?")){s.setString(1,row.getString("responseJson"));s.setString(2,id);s.executeUpdate();}catch(SQLException e){throw new RepositoryException(e);}
        }
        JSONObject debit()throws Exception{try(var s=writer.createStatement()){s.executeUpdate("UPDATE wallet SET balance=balance-100");}markers.touchInCurrentTransaction("123");return new JSONObject().put("balance",400);}
        long scalar(String sql)throws Exception{try(var s=observer.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
        String marker()throws Exception{try(var s=observer.createStatement();var r=s.executeQuery("SELECT revision FROM marker WHERE oId='123'")){return r.next()?r.getString(1):null;}}
        public void close()throws Exception{if(delegate!=null&&delegate.isActive())delegate.rollback();JdbcRepository.TX.remove();observer.close();Files.deleteIfExists(file);}
    }
    static ApiV3IdempotencyMgmtService.Execution execute(Store store)throws Exception {
        return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(store,"123","commit-test","same-key",new JSONObject(),store::debit);
    }
    public static void main(String[] args)throws Exception {
        var properties=new java.util.Properties();properties.setProperty("runtimeDatabase","NONE");properties.setProperty("jdbc.tablePrefix","symphony_");
        for(String name:new String[]{"latkeProps","localProps"}){var field=org.b3log.latke.Latkes.class.getDeclaredField(name);field.setAccessible(true);field.set(null,properties);}
        try(var s=new Store()){
            s.fault=Fault.BEFORE_COMMIT;var tx=s.begin(false);s.debit();tx.commit();
            Assert.assertFalse(tx.isActive());Assert.assertEquals(s.scalar("SELECT balance FROM wallet"),500L);Assert.assertEquals(s.commits,1);
        }
        System.out.println("PASS regression reproduced on actual bundled JdbcTransaction: commit returns after JDBC rejection and connection disposal");
        try(var s=new Store()){
            s.fault=Fault.BEFORE_COMMIT;var tx=s.begin(true);s.debit();boolean failed=false;
            try{tx.commit();}catch(IllegalStateException e){failed=true;Assert.assertTrue(e.getCause() instanceof SQLException);}
            Assert.assertTrue(failed);Assert.assertTrue(tx.isActive());tx.rollback();Assert.assertFalse(tx.isActive());Assert.assertEquals(s.rollbacks,1);Assert.assertEquals(s.scalar("SELECT balance FROM wallet"),500L);Assert.assertNull(JdbcRepository.TX.get());
        }
        System.out.println("PASS strict commit propagates JDBC failure, retains rollback ownership and clears actual framework state after rollback");
        try(var s=new Store()){
            s.fault=Fault.BEFORE_COMMIT;boolean failed=false;try{execute(s);}catch(IllegalStateException expected){failed=true;}
            Assert.assertTrue(failed);Assert.assertEquals(s.scalar("SELECT balance FROM wallet"),500L);Assert.assertEquals(s.scalar("SELECT COUNT(*) FROM receipt"),0L);Assert.assertNull(s.marker());
            s.fault=Fault.NONE;Assert.assertTrue(execute(s).applied());Assert.assertFalse(execute(s).applied());Assert.assertEquals(s.scalar("SELECT balance FROM wallet"),400L);
        }
        System.out.println("PASS real coordinator cannot return success on rejected JDBC commit; receipt/debit rollback and same-key retry applies once");
        try(var s=new Store()){
            s.fault=Fault.AFTER_COMMIT;boolean failed=false;try{execute(s);}catch(IllegalStateException expected){failed=true;}
            Assert.assertTrue(failed);Assert.assertEquals(s.scalar("SELECT balance FROM wallet"),400L);Assert.assertEquals(s.scalar("SELECT COUNT(*) FROM receipt"),1L);String marker=s.marker();Assert.assertNotNull(marker);
            s.fault=Fault.NONE;var replay=execute(s);Assert.assertFalse(replay.applied());Assert.assertEquals(replay.result().getInt("balance"),400);Assert.assertEquals(s.scalar("SELECT balance FROM wallet"),400L);Assert.assertEquals(s.scalar("SELECT COUNT(*) FROM receipt"),1L);Assert.assertEquals(s.marker(),marker);
        }
        System.out.println("PASS injected lost acknowledgement after real commit fails request; fresh same-key retry replays durable receipt without a second debit");
        try(var s=new Store()){
            var tx=s.begin(true);s.debit();tx.commit();Assert.assertEquals(s.scalar("SELECT balance FROM wallet"),400L);Assert.assertFalse(tx.isActive());Assert.assertNull(JdbcRepository.TX.get());
            try{tx.commit();Assert.fail();}catch(IllegalStateException expected){}
        }
        var other=new Transaction(){public void commit(){}public void rollback(){}public boolean isActive(){return false;}};Assert.assertSame(ApiV3Transactions.strict(other),other);
        System.out.println("PASS normal durable commit/disposal, inactive reuse guard and non-JDBC transaction behavior");
        try(var s=new Store()){
            s.begin(true);s.debit();final Connection read=DriverManager.getConnection(s.url);JdbcRepository.CONN.set(read);
            ApiV3Transactions.releaseThreadResources();Assert.assertFalse(s.hasTransactionBegun());Assert.assertEquals(s.scalar("SELECT balance FROM wallet"),500L);Assert.assertTrue(read.isClosed());Assert.assertNull(JdbcRepository.CONN.get());Assert.assertNull(JdbcRepository.TX.get());
        }
        System.out.println("PASS socket/background cleanup explicitly rolls back abandoned writes and releases the actual framework read connection/thread-locals");
    }
}
