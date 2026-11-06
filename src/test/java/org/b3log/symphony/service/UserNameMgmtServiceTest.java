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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.UserNameRules;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import java.sql.*;
import java.lang.reflect.*;
import sun.misc.Unsafe;
import static org.b3log.symphony.service.CheckinMgmtServiceTest.*;

/** Actual name/bag/idempotency services; SQLite adapters do not prove MySQL locking. */
public class UserNameMgmtServiceTest {
    static class Names extends Fixture {
        final UserNameMgmtService names=new UserNameMgmtService();
        Names()throws Exception {
            exec("ALTER TABLE user ADD COLUMN userName TEXT COLLATE NOCASE DEFAULT 'Original-A'");
            exec("CREATE TABLE option(oId TEXT PRIMARY KEY,optionValue INT)");
            exec("INSERT INTO option VALUES('statisticMemberCount',1)");
            setBag(bag().put("nameCard",2));
            var field=ApiV3BagMgmtService.class.getDeclaredField("bags");field.setAccessible(true);
            inject(names,"bags",field.get(api));inject(names,"users",new UserRepository());inject(names,"options",new OptionRepository());inject(api,"userNames",names);
        }
        String name()throws Exception{return Rows.query("SELECT userName FROM user WHERE oId='a'").getFirst().getString("userName");}
    }
    /** Real bundled commit/rollback/dispose; keep the SQLite fixture open only for post-transaction assertions. */
    static class JdbcNames extends Names {
        JdbcNames() throws Exception {
            inject(names,"users",new UserRepository(){
                @Override public Transaction beginTransaction(){
                    try{return beginJdbc();}catch(Exception e){throw new IllegalStateException(e);}
                }
            });
        }
        JdbcTransaction beginJdbc() throws Exception {
            Assert.assertFalse(active());db.setAutoCommit(false);
            Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                try{
                    if(method.getName().equals("commit")){
                        if(fail.equals("jdbc.commit.reject"))throw new SQLException("injected commit rejection");
                        db.commit();
                        if(fail.equals("jdbc.commit.unknown"))throw new SQLException("injected lost commit acknowledgement");
                        return null;
                    }
                    if(method.getName().equals("close")){
                        // A real connection close discards pending work. Preserve only the fixture handle.
                        db.rollback();db.setAutoCommit(true);return null;
                    }
                    return method.invoke(db,args);
                }catch(InvocationTargetException e){throw e.getCause();}
            });
            var access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
            var tx=(JdbcTransaction)((Unsafe)access.get(null)).allocateInstance(JdbcTransaction.class);
            var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,connection);
            field=JdbcTransaction.class.getDeclaredField("isActive");field.setAccessible(true);field.setBoolean(tx,true);
            JdbcRepository.TX.set(tx);return tx;
        }
        @Override public void close() throws Exception {
            try{var tx=JdbcRepository.TX.get();if(tx!=null&&tx.isActive())tx.rollback();}
            finally{JdbcRepository.TX.remove();super.close();}
        }
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");
        for(String n:List.of("latkeProps","localProps")){var field=Latkes.class.getDeclaredField(n);field.setAccessible(true);field.set(null,props);}
        for(String name:List.of("A","Fresh-Name","a".repeat(64)))Assert.assertFalse(UserNameRules.invalid(name),name);
        Assert.assertTrue(UserNameRules.invalid(null));
        for(String name:List.of(""," ","a_b","中文","<script>","a".repeat(65),"FileTransfer","ADMIN-user","Botany"))Assert.assertTrue(UserNameRules.invalid(name),name);
        System.out.println("PASS shared registration character, length and reserved-name rules");
        try(var f=new Names()) {
            var first=f.api.useNameCard("a","Fresh-Name","name-one");
            Assert.assertEquals(first.keySet(),Set.of("itemCode","userName","bag"));Assert.assertFalse(first.toString().contains("PRIVATE"));
            Assert.assertEquals(f.name(),"Fresh-Name");Assert.assertEquals(f.bag().getInt("nameCard"),1);Assert.assertEquals(f.bag().getInt("other"),7);
            Assert.assertEquals(f.oldNames,List.of("Original-A"));Assert.assertEquals(f.cache.getString("userName"),"Fresh-Name");
            Assert.assertEquals(f.api.useNameCard("a","Fresh-Name","name-one").toString(),first.toString());
            expect(409,()->f.api.useNameCard("a","Third-Name","name-one"));
            f.api.useNameCard("a","Second-Name","name-two");
            Assert.assertEquals(f.api.useNameCard("a","Fresh-Name","name-one").toString(),first.toString());
            Assert.assertEquals(f.name(),"Second-Name");Assert.assertEquals(f.cache.getString("userName"),"Second-Name");Assert.assertEquals(f.bag().getInt("nameCard"),0);
            Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM api_v3_request"),2L);
            int option=-1,user=-1,bag=-1;for(int i=0;i<f.queryLog.size();i++){String q=f.queryLog.get(i);if(option<0&&q.contains("FROM option"))option=i;if(user<0&&q.contains("FROM user"))user=i;if(bag<0&&q.contains("FROM cloud"))bag=i;}
            Assert.assertTrue(option>=0&&option<user&&user<bag,f.queryLog.toString());
        }
        System.out.println("PASS successful rename consumes once, whitelist DTO, namespace-owner-bag order, conflicting-key rejection and historical replay cannot restore an old name");
        for(String failure:List.of("option.select","user.select","user.update","cloud.update","receipt.add","receipt.update","commit"))try(var f=new Names()) {
            String before=f.snapshot();f.fail=failure;boolean failed=false;try{f.api.useNameCard("a","Fresh-Name","retry");}catch(Exception expected){failed=true;}
            Assert.assertTrue(failed,failure);Assert.assertEquals(f.snapshot(),before,failure);Assert.assertFalse(f.active());
            f.fail="";f.api.useNameCard("a","Fresh-Name","retry");Assert.assertEquals(f.bag().getInt("nameCard"),1);
        }
        System.out.println("PASS namespace/user/bag/receipt/commit failures roll back name, card and journal; retry succeeds once");
        try(var f=new Names()) {
            f.exec("INSERT INTO user(oId,userStatus,userName) VALUES('b',0,'Taken-Name')");
            String before=f.snapshot();expect(409,()->f.api.useNameCard("a","taken-name","collision"));expect(409,()->f.api.useNameCard("a","Original-A","same-name"));expect(422,()->f.api.useNameCard("a","bad_name","invalid"));Assert.assertEquals(f.snapshot(),before);
            f.setBag(f.bag().put("nameCard",0));before=f.snapshot();expect(409,()->f.api.useNameCard("a","Fresh-Name","no-card"));Assert.assertEquals(f.snapshot(),before);
        }
        try(var f=new Names()) {
            f.exec("INSERT INTO user(oId,userStatus,userName) VALUES('b',0,'Original-B')");
            f.exec("INSERT INTO cloud VALUES('bag-b','b','sys-bag','{\"nameCard\":2}')");
            f.api.useNameCard("a","Fresh-Name","shared-key");String before=f.snapshot();
            expect(409,()->f.api.useNameCard("b","Fresh-Name","shared-key"));Assert.assertEquals(f.snapshot(),before);
            f.api.useNameCard("b","Second-Name","shared-key");Assert.assertEquals(f.scalar("SELECT COUNT(*) FROM api_v3_request"),2L);Assert.assertEquals(f.bag().getInt("nameCard"),1);
        }
        for(String sql:List.of("UPDATE user SET userStatus=1","DELETE FROM user"))try(var f=new Names()){f.exec(sql);String before=f.snapshot();expect(404,()->f.api.useNameCard("a","Fresh-Name","inactive"));Assert.assertEquals(f.snapshot(),before);}
        System.out.println("PASS existing database collation governs duplicate rejection; invalid, unchanged, missing, disabled and no-card requests do not consume items");
        try(var f=new Names()) {
            f.names.useCardLegacy("a","Fresh-Name");Assert.assertEquals(f.bag().getInt("nameCard"),1);
            String before=f.snapshot();try{f.names.useCardLegacy("a","Fresh-Name");Assert.fail();}catch(UserNameMgmtService.Rejected e){Assert.assertEquals(e.code,"name_taken");}Assert.assertEquals(f.snapshot(),before);
            f.fail="cloud.update";try{f.names.useCardLegacy("a","Second-Name");Assert.fail();}catch(RuntimeException|RepositoryException expected){}Assert.assertEquals(f.snapshot(),before);
        }
        try(var f=new Names()){f.exec("UPDATE user SET userStatus=1");f.names.useCardLegacy("a","Fresh-Name");Assert.assertEquals(f.name(),"Fresh-Name");}
        try(var f=new Names()){f.fail="cache";f.api.useNameCard("a","Fresh-Name","cache-failure");Assert.assertEquals(f.name(),"Fresh-Name");Assert.assertTrue(f.evicted>0);}
        System.out.println("PASS legacy uses the same atomic core and preserves status policy; cache failure cannot undo a durable successful rename");
        try(var f=new JdbcNames()){
            String before=f.snapshot();f.fail="jdbc.commit.reject";
            try{f.names.useCardLegacy("a","Fresh-Name");Assert.fail("commit rejection returned success");}
            catch(IllegalStateException e){Assert.assertTrue(e.getCause() instanceof SQLException);}
            Assert.assertEquals(f.snapshot(),before);Assert.assertFalse(f.active());Assert.assertNull(JdbcRepository.TX.get());
            Assert.assertEquals(f.cache.getString("userName"),"Original-A");
            f.fail="";f.names.useCardLegacy("a","Fresh-Name");
            Assert.assertEquals(f.name(),"Fresh-Name");Assert.assertEquals(f.bag().getInt("nameCard"),1);
            Assert.assertEquals(f.cache.getString("userName"),"Fresh-Name");Assert.assertNull(JdbcRepository.TX.get());
        }
        System.out.println("PASS actual JDBC commit rejection propagates from legacy name-card service and rolls back both name/card before cache reload; retry commits once");
        try(var f=new JdbcNames()){
            f.fail="jdbc.commit.unknown";
            try{f.names.useCardLegacy("a","Fresh-Name");Assert.fail("unknown commit returned success");}
            catch(IllegalStateException e){Assert.assertTrue(e.getCause() instanceof SQLException);}
            Assert.assertEquals(f.name(),"Fresh-Name");Assert.assertEquals(f.bag().getInt("nameCard"),1);
            Assert.assertFalse(f.active());Assert.assertNull(JdbcRepository.TX.get());Assert.assertEquals(f.cache.getString("userName"),"Fresh-Name");
            f.fail="";try{f.names.useCardLegacy("a","Fresh-Name");Assert.fail();}
            catch(UserNameMgmtService.Rejected e){Assert.assertEquals(e.code,"name_taken");}
            Assert.assertEquals(f.bag().getInt("nameCard"),1);
        }
        System.out.println("PASS actual durable commit with lost acknowledgement reports uncertainty, reloads durable cache and cannot consume another card for the same name");
        try(var f=new Names()) {
            try{f.names.lockNamespaceInCurrentTransaction();Assert.fail();}catch(IllegalStateException expected){}
            f.exec("INSERT INTO user(oId,userStatus,userName) VALUES('0-unverified',2,'Original-A')");
            var tx=new Rows("user").beginTransaction();try{Assert.assertEquals(f.names.registrationTargetInCurrentTransaction("original-a").getString("oId"),"a");}finally{tx.rollback();}
            f.exec("DELETE FROM option");String before=f.snapshot();try{f.names.useCardLegacy("a","Fresh-Name");Assert.fail();}catch(IllegalStateException expected){}Assert.assertEquals(f.snapshot(),before);
        }
        System.out.println("PASS registration shares current namespace lookup; missing transaction or namespace row fails closed");
    }
}
