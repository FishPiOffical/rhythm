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
package org.b3log.symphony.repository;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import org.b3log.symphony.model.ApiV3ChatEvent;
import org.b3log.symphony.util.ApiV3ChatTransaction;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.service.ReactionMgmtService;
import org.json.JSONObject;
import org.testng.Assert;
import sun.misc.Unsafe;
import java.sql.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** 验证实际的写入钩子、保存点和事件 SQL，仅数据行持久化及框架连接池构造使用适配器。 */
public class ApiV3ChatEventRepositoryTest {
    interface Work{void run()throws Exception;}
    static void inject(Object target,Class<?> type,String name,Object value)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static class Fixture implements AutoCloseable {
        final Path file=Files.createTempFile("rhythm-chat-events-",".sqlite");final String url="jdbc:sqlite:"+file;
        final Connection writer=DriverManager.getConnection(url),reader=DriverManager.getConnection(url),second=DriverManager.getConnection(url);
        final Set<String> faults=new HashSet<>();int disposals;JdbcTransaction tx;
        final State state=new State();final Events events=new Events();final Messages messages=new Messages();
        final ReactionMgmtService reactions=new ReactionMgmtService();
        Fixture()throws Exception {
            exec(writer,"CREATE TABLE state(oId TEXT PRIMARY KEY,revision BIGINT,updatedAt BIGINT)");exec(writer,"INSERT INTO state VALUES('main',0,0)");
            exec(writer,"CREATE TABLE event(oId TEXT PRIMARY KEY,revision BIGINT UNIQUE,messageId TEXT,kind TEXT,createdAt BIGINT)");
            exec(writer,"CREATE TABLE chat(oId TEXT PRIMARY KEY,content TEXT)");exec(writer,"CREATE TABLE unrelated(oId TEXT PRIMARY KEY)");
            exec(writer,"CREATE TABLE user(oId TEXT PRIMARY KEY)");exec(writer,"INSERT INTO user VALUES('1')");
            exec(writer,"CREATE TABLE reaction(oId TEXT PRIMARY KEY,reactionUserId TEXT,reactionTargetType TEXT,reactionTargetId TEXT,reactionGroup TEXT,reactionValue TEXT,reactionCreatedTime BIGINT,reactionUpdatedTime BIGINT)");
            inject(events,ApiV3ChatEventRepository.class,"state",state);inject(messages,ChatRoomRepository.class,"changes",events);
            for(final var entry:Map.<String,Object>of("reactionRepository",new Reactions(),"userRepository",new UserRepository(),"chatRoomRepository",messages,"chatEvents",events).entrySet())inject(reactions,ReactionMgmtService.class,entry.getKey(),entry.getValue());
        }
        boolean active(){try{return !writer.getAutoCommit();}catch(SQLException e){throw new IllegalStateException(e);}}
        void begin()throws Exception {
            Assert.assertFalse(active());Assert.assertNull(JdbcRepository.TX.get());writer.setAutoCommit(false);
            final Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                try{
                    switch(method.getName()){
                        case "setSavepoint"->{if(faults.contains("savepoint"))throw new SQLException("injected savepoint failure");}
                        case "releaseSavepoint"->{if(faults.remove("release.after")){method.invoke(writer,args);throw new SQLException("injected lost release acknowledgement");}}
                        case "rollback"->{if(args!=null&&args.length==1&&faults.contains("rollback.point"))throw new SQLException("injected point rollback failure");}
                        case "commit"->{if(faults.contains("commit"))throw new SQLException("injected root commit rejection");}
                        case "close"->{disposals++;writer.rollback();writer.setAutoCommit(true);return null;}
                        case "prepareStatement"->{
                            if(((String)args[0]).startsWith("INSERT INTO `event`")){
                                if(faults.contains("event.before"))throw new SQLException("injected event insert failure");
                                final PreparedStatement statement=(PreparedStatement)method.invoke(writer,args);
                                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                                    try{Object value=m.invoke(statement,a);if(m.getName().equals("executeUpdate")&&faults.contains("event.after"))throw new SQLException("injected lost event statement acknowledgement");return value;}catch(InvocationTargetException e){throw e.getCause();}
                                });
                            }
                        }
                    }
                    return method.invoke(writer,args);
                }catch(InvocationTargetException e){throw e.getCause();}
            });
            var access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);tx=(JdbcTransaction)((Unsafe)access.get(null)).allocateInstance(JdbcTransaction.class);
            var field=JdbcTransaction.class.getDeclaredField("connection");field.setAccessible(true);field.set(tx,connection);field=JdbcTransaction.class.getDeclaredField("isActive");field.setAccessible(true);field.setBoolean(tx,true);JdbcRepository.TX.set(tx);
        }
        void commit()throws Exception {ApiV3Transactions.strict(tx).commit();Assert.assertFalse(active());Assert.assertNull(JdbcRepository.TX.get());}
        void rollback(){tx.rollback();Assert.assertFalse(active());Assert.assertNull(JdbcRepository.TX.get());}
        void atomic(Work work)throws Exception {begin();try{work.run();commit();}finally{if(tx.isActive())rollback();}}
        long head(Connection connection)throws Exception{return new ApiV3ChatStateRepository(){@Override public String getName(){return "state";}@Override public boolean hasTransactionBegun(){return false;}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(connection,sql,args);}}.head();}
        List<JSONObject> page(Connection connection,long from,long through,int limit)throws Exception{return new ApiV3ChatEventRepository(){@Override public String getName(){return "event";}@Override public boolean hasTransactionBegun(){return false;}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(connection,sql,args);}}.after(from,through,limit);}
        class State extends ApiV3ChatStateRepository {
            @Override public String getName(){return "state";}@Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(writer,sql,args);}
            @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException {
                Assert.assertEquals(fields,new String[]{"revision","updatedAt"});if(faults.contains("state.before"))throw new RepositoryException("injected head update failure");
                exec(writer,"UPDATE state SET revision=?,updatedAt=? WHERE oId=?",row.getLong("revision"),row.getLong("updatedAt"),id);
                if(faults.contains("state.after"))throw new RepositoryException("injected head update acknowledgement loss");
            }
        }
        class Events extends ApiV3ChatEventRepository {
            @Override public String getName(){return "event";}@Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(writer,sql,args);}
            @Override public Transaction beginTransaction(){try{begin();return ApiV3Transactions.strict(tx);}catch(Exception e){throw new IllegalStateException(e);}}
            @Override public void remove(String id)throws RepositoryException {if(faults.contains("cleanup.remove"))throw new RepositoryException("injected deletion failure");exec(writer,"DELETE FROM event WHERE oId=?",id);}
        }
        class Messages extends ChatRoomRepository {
            @Override public String getName(){return "chat";}@Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(writer,sql,args);}
            @Override protected String insertMessage(JSONObject row)throws RepositoryException {exec(writer,"INSERT INTO chat VALUES(?,?)",row.getString("oId"),row.getString("content"));dataFault();return row.getString("oId");}
            @Override protected void updateMessage(String id,JSONObject row,String...fields)throws RepositoryException {exec(writer,"UPDATE chat SET content=? WHERE oId=?",row.getString("content"),id);dataFault();}
            @Override protected void removeMessage(String id)throws RepositoryException {exec(writer,"DELETE FROM chat WHERE oId=?",id);dataFault();}
            void dataFault()throws RepositoryException {if(faults.contains("data.after"))throw new RepositoryException("injected data write acknowledgement loss");}
        }
        class Reactions extends ReactionRepository {
            @Override public String getName(){return "reaction";}@Override public boolean hasTransactionBegun(){return active();}
            @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(writer,sql,args);}
            @Override public Transaction beginTransaction(){try{begin();return ApiV3Transactions.strict(tx);}catch(Exception e){throw new IllegalStateException(e);}}
            @Override public String add(JSONObject row)throws RepositoryException{
                final String id=UUID.randomUUID().toString();
                exec(writer,"INSERT INTO reaction VALUES(?,?,?,?,?,?,?,?)",id,row.getString("reactionUserId"),row.getString("reactionTargetType"),row.getString("reactionTargetId"),row.getString("reactionGroup"),row.getString("reactionValue"),row.getLong("reactionCreatedTime"),row.getLong("reactionUpdatedTime"));return id;
            }
            @Override public void update(String id,JSONObject row,String...fields)throws RepositoryException{exec(writer,"UPDATE reaction SET reactionValue=?,reactionUpdatedTime=? WHERE oId=?",row.getString("reactionValue"),row.getLong("reactionUpdatedTime"),id);}
            @Override public void remove(String id)throws RepositoryException{exec(writer,"DELETE FROM reaction WHERE oId=?",id);}
        }
        void add(String id)throws Exception{messages.add(new JSONObject().put("oId",id).put("content","PRIVATE-message"));}
        String snapshot()throws Exception {return query(reader,"SELECT * FROM state").toString()+query(reader,"SELECT * FROM event ORDER BY revision")+query(reader,"SELECT * FROM chat ORDER BY oId");}
        public void close()throws Exception {if(tx!=null&&tx.isActive())rollback();JdbcRepository.TX.remove();writer.close();reader.close();second.close();Files.deleteIfExists(file);}
    }
    static List<JSONObject> query(Connection c,String sql,Object...args)throws RepositoryException {
        try(var s=c.prepareStatement(sql.replace(" FOR UPDATE",""))){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));out.add(row);}return out;}}catch(SQLException e){throw new RepositoryException(e);}
    }
    static void exec(Connection c,String sql,Object...args)throws RepositoryException {try(var s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}catch(SQLException e){throw new RepositoryException(e);}}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            f.begin();f.add("900");Assert.assertEquals(f.head(f.reader),0L);Assert.assertTrue(f.page(f.reader,0,100,200).isEmpty());Assert.assertTrue(query(f.reader,"SELECT * FROM chat").isEmpty());f.commit();Assert.assertEquals(f.head(f.reader),1L);Assert.assertEquals(f.head(f.second),1L);Assert.assertEquals(f.page(f.second,0,1,200).getFirst().keySet(),Set.of("revision","messageId","kind"));Assert.assertFalse(f.page(f.reader,0,1,200).toString().contains("PRIVATE"));
            f.atomic(()->f.add("100"));var page=f.page(f.reader,0,2,200);Assert.assertEquals(page.stream().map(r->r.getString("messageId")).toList(),List.of("900","100"));Assert.assertEquals(page.stream().map(r->r.getLong("revision")).toList(),List.of(1L,2L));
        }
        System.out.println("PASS actual chat insert/event/head share the owner transaction; independent readers see committed IDs only, with sequence order independent of message IDs");
        try(var f=new Fixture()){
            f.atomic(()->f.add("101"));String before=f.snapshot();f.begin();f.messages.update("101",new JSONObject().put("content","updated"));Assert.assertEquals(f.head(f.reader),1L);f.rollback();Assert.assertEquals(f.snapshot(),before);
            f.atomic(()->f.messages.update("101",new JSONObject().put("content","updated")));Assert.assertEquals(f.head(f.reader),2L);Assert.assertEquals(f.page(f.reader,1,2,200).getFirst().getString("kind"),"updated");
            f.atomic(()->f.messages.remove("101"));Assert.assertEquals(f.head(f.reader),3L);Assert.assertEquals(f.page(f.reader,2,3,200).getFirst().getString("kind"),"deleted");
            f.atomic(()->{f.messages.remove("101");f.messages.update("101",new JSONObject().put("content","missing"));});Assert.assertEquals(f.head(f.reader),3L);
        }
        System.out.println("PASS update/delete hooks commit only real targets; full rollback restores the previous message/event/head and no-op targets allocate no revision");
        for(String operation:List.of("add","update","remove"))for(String fault:List.of("data.after","event.before","event.after","state.before","state.after","savepoint"))try(var f=new Fixture()){
            f.atomic(()->f.add("101"));String before=f.snapshot();f.begin();exec(f.writer,"INSERT INTO unrelated VALUES('earlier')");f.faults.add(fault);boolean rejected=false;
            try{switch(operation){case "add"->f.add("102");case "update"->f.messages.update("101",new JSONObject().put("content","bad"));case "remove"->f.messages.remove("101");default->throw new AssertionError();}}catch(RepositoryException expected){rejected=true;}
            Assert.assertTrue(rejected,operation+"/"+fault);f.faults.clear();f.commit();Assert.assertEquals(f.snapshot(),before,operation+"/"+fault);Assert.assertEquals(query(f.reader,"SELECT * FROM unrelated").size(),1);
            f.atomic(()->f.add("103"));Assert.assertEquals(f.head(f.reader),2L);
        }
        System.out.println("PASS all data/event/head/savepoint failures locally undo add/update/delete even when a legacy caller catches and commits; earlier unrelated work survives and revision is reusable");
        try(var f=new Fixture()){
            f.begin();f.add("101");f.faults.add("state.after");
            try{f.add("102");Assert.fail();}catch(RepositoryException expected){}
            f.faults.clear();f.add("103");Assert.assertEquals(f.head(f.reader),0L);f.commit();
            Assert.assertEquals(f.head(f.reader),2L);Assert.assertEquals(f.page(f.reader,0,2,200).stream().map(row->row.getString("messageId")).toList(),List.of("101","103"));
            Assert.assertEquals(query(f.reader,"SELECT oId FROM chat ORDER BY oId").stream().map(row->row.getString("oId")).toList(),List.of("101","103"));
        }
        System.out.println("PASS a failed middle operation preserves earlier chat work in the same owner transaction and a later operation reuses only its uncommitted revision");
        for(String fault:List.of("release.after","rollback.point"))try(var f=new Fixture()){
            f.atomic(()->f.add("101"));String before=f.snapshot();f.begin();exec(f.writer,"INSERT INTO unrelated VALUES('earlier')");f.faults.add(fault);if(fault.equals("rollback.point"))f.faults.add("event.before");
            try{f.add("102");Assert.fail();}catch(RepositoryException expected){Assert.assertTrue(expected.getSuppressed().length>0);}
            Assert.assertFalse(f.tx.isActive());Assert.assertFalse(f.active());Assert.assertNull(JdbcRepository.TX.get());Assert.assertEquals(f.snapshot(),before);Assert.assertTrue(query(f.reader,"SELECT * FROM unrelated").isEmpty());
        }
        System.out.println("PASS lost savepoint release or failed savepoint rollback falls back to real bundled full rollback/disposal instead of permitting a partial owner commit");
        try(var f=new Fixture()){
            f.atomic(()->{for(int i=0;i<12;i++)f.add(""+(101+i));});Assert.assertEquals(f.head(f.reader),12L);Assert.assertEquals(f.page(f.reader,8,12,2).stream().map(r->r.getLong("revision")).toList(),List.of(9L,10L));Assert.assertEquals(f.page(f.reader,10,11,200).getFirst().getLong("revision"),11L);
            exec(f.writer,"UPDATE event SET createdAt=1 WHERE revision<=2");String before=f.snapshot();f.faults.add("cleanup.remove");try{f.events.removeExpired(2);Assert.fail();}catch(RepositoryException expected){}Assert.assertEquals(f.snapshot(),before);f.faults.clear();
            f.faults.add("commit");try{f.events.removeExpired(2);Assert.fail();}catch(IllegalStateException expected){}Assert.assertEquals(f.snapshot(),before);f.faults.clear();
            Assert.assertEquals(f.events.removeExpired(2),2);Assert.assertEquals(f.events.removeExpired(2),0);Assert.assertEquals(f.head(f.reader),12L);Assert.assertEquals(f.page(f.reader,0,12,200).getFirst().getLong("revision"),3L);f.atomic(()->f.add("999"));Assert.assertEquals(f.head(f.reader),13L);
        }
        System.out.println("PASS bounded numeric revision ranges and cleanup preserve the committed head, expose retention gaps and roll back delete/strict-root commit failures");
        try(var f=new Fixture()){
            try{f.add("101");Assert.fail();}catch(IllegalStateException expected){}
            f.begin();try{f.events.after(0,0,1);Assert.fail();}catch(IllegalStateException expected){}try{f.state.head();Assert.fail();}catch(IllegalStateException expected){}
            for(String id:List.of("bad","1' OR 1=1","1".repeat(33)))try{f.events.appendInCurrentTransaction(id,"created");Assert.fail();}catch(IllegalArgumentException expected){}f.rollback();
            for(long[] range:List.of(new long[]{-1,0,1},new long[]{2,1,1},new long[]{0,1,201}))try{f.events.after(range[0],range[1],(int)range[2]);Assert.fail();}catch(IllegalArgumentException expected){}
            exec(f.writer,"DELETE FROM state");f.begin();try{f.add("101");Assert.fail();}catch(RepositoryException expected){}f.commit();Assert.assertTrue(query(f.reader,"SELECT * FROM chat").isEmpty());
            Assert.assertFalse(ApiV3ChatEvent.REPOSITORY.startsWith("symphony_"));Assert.assertFalse(ApiV3ChatEvent.STATE_REPOSITORY.startsWith("symphony_"));
        }
        System.out.println("PASS transaction/range/target guards and missing room seed fail closed without message writes; model names omit the deployment table prefix");
        try(var f=new Fixture()){
            exec(f.writer,"UPDATE state SET revision=?",Long.MAX_VALUE);f.begin();try{f.add("101");Assert.fail();}catch(RepositoryException expected){}f.commit();
            Assert.assertEquals(f.head(f.reader),Long.MAX_VALUE);Assert.assertTrue(query(f.reader,"SELECT * FROM chat").isEmpty());Assert.assertTrue(f.page(f.reader,0,Long.MAX_VALUE,200).isEmpty());
        }
        System.out.println("PASS exhausted room sequence rejects the local message write instead of wrapping or allocating a negative revision");
        try(var f=new Fixture()){
            f.atomic(()->f.add("101"));f.atomic(()->Assert.assertTrue(f.reactions.setInCurrentTransaction("1","chat","101","thumbsup",null)));
            Assert.assertEquals(f.head(f.reader),2L);Assert.assertEquals(f.page(f.reader,1,2,200).getFirst().getString("kind"),"reactions");
            f.atomic(()->Assert.assertFalse(f.reactions.setInCurrentTransaction("1","chat","101","thumbsup",null)));Assert.assertEquals(f.head(f.reader),2L);
            f.begin();f.faults.add("state.after");try{f.reactions.setInCurrentTransaction("1","chat","101","heart",null);Assert.fail();}catch(RepositoryException expected){}f.faults.clear();f.commit();
            Assert.assertEquals(f.head(f.reader),2L);Assert.assertEquals(query(f.reader,"SELECT reactionValue FROM reaction").getFirst().getString("reactionValue"),"thumbsup");
            f.reactions.toggleReaction("1","chat","101","emoji","thumbsup");Assert.assertEquals(f.head(f.reader),3L);Assert.assertTrue(query(f.reader,"SELECT * FROM reaction").isEmpty());
            f.atomic(()->Assert.assertTrue(f.reactions.setInCurrentTransaction("1","chat","101","heart",null)));Assert.assertEquals(f.head(f.reader),4L);
        }
        System.out.println("PASS actual shared chat reaction set/clear/toggle couples row changes to committed events; unchanged values emit nothing and event failure rolls back even before an owner commit");
    }
}
