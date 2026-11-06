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
import org.b3log.latke.repository.RepositoryException;
import org.json.JSONObject;
import org.testng.Assert;
import java.sql.*;
import java.util.*;

/** Actual repository deletion methods; JDBC and transaction ownership are SQLite adapters. */
public class ContentInteractionRemovalTest {
    static Connection db;
    static String failure="";
    static int removed;
    static VoteRepository votes;
    static RewardRepository rewards;
    static ReactionRepository reactions;
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");
        for(String name:List.of("latkeProps","localProps")){var f=Latkes.class.getDeclaredField(name);f.setAccessible(true);f.set(null,props);}
        votes=new Votes();rewards=new Rewards();reactions=new Reactions();
        try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:")){
            db=connection;write("CREATE TABLE vote(oId TEXT PRIMARY KEY,dataId TEXT,dataType INT)");write("CREATE TABLE reward(oId TEXT PRIMARY KEY,dataId TEXT,type INT)");
            write("CREATE TABLE reaction(oId TEXT PRIMARY KEY,reactionTargetType TEXT,reactionTargetId TEXT)");write("CREATE TABLE target(oId TEXT PRIMARY KEY)");
            seed();db.setAutoCommit(false);
            for(int type:List.of(0,3,4))rewards.removeByDataId("10",type);
            votes.removeByDataId("10",0);db.commit();db.setAutoCommit(true);
            Assert.assertEquals(count("vote","dataId='10' AND dataType=0"),0L);Assert.assertEquals(count("vote","dataId='10'"),3L);
            Assert.assertEquals(count("reward","dataId='10'"),2L);Assert.assertEquals(count("reward","dataId='10' AND type IN(1,2)"),2L);
            Assert.assertEquals(count("vote","dataId='11'"),4L);Assert.assertEquals(count("reward","dataId='11'"),5L);
            Assert.assertEquals(removed,304); // >100 rows in the large selected types, no implicit query page truncation.
            System.out.println("PASS article cleanup removes every matching article interaction while preserving same-ID comment/user/tag and other-article rows");
            seed();db.setAutoCommit(false);removed=0;failure="reward-10-3";
            try{rewards.removeByDataId("10",0);rewards.removeByDataId("10",3);Assert.fail();}catch(RepositoryException expected){Assert.assertTrue(removed>0);}finally{db.rollback();db.setAutoCommit(true);failure="";}
            Assert.assertEquals(count("vote","1=1"),108L);Assert.assertEquals(count("reward","1=1"),210L);
            System.out.println("PASS a later reward-type failure rolls back earlier deletions in the caller transaction");
            seed();db.setAutoCommit(false);removed=0;failure="vote-10-0-2";
            try{for(int type:List.of(0,3,4))rewards.removeByDataId("10",type);votes.removeByDataId("10",0);Assert.fail();}catch(RepositoryException expected){Assert.assertTrue(removed>204);}finally{db.rollback();db.setAutoCommit(true);failure="";}
            Assert.assertEquals(count("vote","1=1"),108L);Assert.assertEquals(count("reward","1=1"),210L);
            System.out.println("PASS partial vote deletion failure rolls back both reward and vote deletions");
            db.setAutoCommit(false);votes.removeByDataId("10' OR '1'='1",0);rewards.removeByDataId("10' OR '1'='1",0);db.commit();db.setAutoCommit(true);
            Assert.assertEquals(count("vote","1=1"),108L);Assert.assertEquals(count("reward","1=1"),210L);
            for(int type=0;type<4;type++){db.setAutoCommit(false);votes.removeByDataId("11",type);db.commit();db.setAutoCommit(true);}
            for(int type=0;type<5;type++){db.setAutoCommit(false);rewards.removeByDataId("11",type);db.commit();db.setAutoCommit(true);}
            Assert.assertEquals(count("vote","dataId='11'"),0L);Assert.assertEquals(count("reward","dataId='11'"),0L);
            System.out.println("PASS typed removal binds external IDs and supports every declared target/reward type independently");
            for(boolean vote:List.of(true,false)){
                try{if(vote)votes.removeByDataId("10",0);else rewards.removeByDataId("10",0);Assert.fail();}catch(IllegalStateException expected){}
                db.setAutoCommit(false);try{if(vote)votes.removeByDataId("10",99);else rewards.removeByDataId("10",99);Assert.fail();}catch(IllegalArgumentException expected){}finally{db.rollback();db.setAutoCommit(true);}
            }
            System.out.println("PASS missing caller transaction and unknown types fail before deletion");
            for(String kind:List.of("article","comment","chat")){
                seedReactions();db.setAutoCommit(false);reactions.removeTargetInCurrentTransaction(kind,"10");db.commit();db.setAutoCommit(true);
                Assert.assertEquals(count("reaction","reactionTargetType='"+kind+"' AND reactionTargetId='10'"),0L);
                Assert.assertEquals(count("reaction","reactionTargetId='10'"),1200L);Assert.assertEquals(count("reaction","reactionTargetId='11'"),1L);
            }
            System.out.println("PASS reaction removal clears 600 rows per selected target kind, preserves colliding article/comment/chat IDs and other targets");
            seedReactions();db.setAutoCommit(false);removed=0;failure="article-050";
            try{write("DELETE FROM target WHERE oId='10'");reactions.removeTargetInCurrentTransaction("article","10");Assert.fail();}catch(RepositoryException expected){Assert.assertTrue(removed>0);}finally{db.rollback();db.setAutoCommit(true);failure="";}
            Assert.assertEquals(count("reaction","1=1"),1801L);Assert.assertEquals(count("target","oId='10'"),1L);
            System.out.println("PASS partial reaction cleanup failure rolls back already removed reactions and the caller's target deletion");
            try{reactions.removeTargetInCurrentTransaction("article","10");Assert.fail();}catch(IllegalStateException expected){}
            db.setAutoCommit(false);for(String kind:Arrays.asList(null,"user","article OR 1=1")){try{reactions.removeTargetInCurrentTransaction(kind,"10");Assert.fail();}catch(IllegalArgumentException expected){}}
            reactions.removeTargetInCurrentTransaction("article","10' OR '1'='1");db.commit();db.setAutoCommit(true);Assert.assertEquals(count("reaction","1=1"),1801L);
            System.out.println("PASS reaction cleanup requires a caller transaction, validates kind and binds target IDs");
        }
    }
    static void seedReactions()throws Exception{
        write("DELETE FROM reaction");write("DELETE FROM target");write("INSERT INTO target VALUES('10')");
        for(String kind:List.of("article","comment","chat"))for(int i=0;i<600;i++)write("INSERT INTO reaction VALUES(?,?,?)",String.format("%s-%03d",kind,i),kind,"10");
        write("INSERT INTO reaction VALUES('other','article','11')");
    }
    static void seed()throws Exception{
        write("DELETE FROM vote");write("DELETE FROM reward");removed=0;
        for(String id:List.of("10","11")){
            for(int type=0;type<4;type++)write("INSERT INTO vote VALUES(?,?,?)","vote-"+id+"-"+type,id,type);
            for(int type=0;type<5;type++)write("INSERT INTO reward VALUES(?,?,?)","reward-"+id+"-"+type,id,type);
        }
        for(int i=0;i<100;i++){write("INSERT INTO vote VALUES(?,?,?)","vote-10-0-"+i,"10",0);for(int type:List.of(0,3))write("INSERT INTO reward VALUES(?,?,?)","reward-10-"+type+"-"+i,"10",type);}
    }
    static long count(String table,String where)throws Exception{try(var s=db.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table+" WHERE "+where)){return r.getLong(1);}}
    static boolean active(){try{return !db.getAutoCommit();}catch(Exception e){throw new RuntimeException(e);}}
    static List<JSONObject> query(String sql,Object...args)throws RepositoryException{
        Assert.assertTrue(active());Assert.assertTrue(sql.endsWith(" FOR UPDATE"));
        try(var s=db.prepareStatement(sql.replace(" FOR UPDATE",""))){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next())out.add(new JSONObject().put("oId",r.getString(1)));return out;}}catch(Exception e){throw new RepositoryException(e);}
    }
    static void erase(String table,String id)throws RepositoryException{Assert.assertTrue(active());if(failure.equals(id))throw new RepositoryException("injected deletion failure");try{write("DELETE FROM "+table+" WHERE oId=?",id);removed++;}catch(Exception e){throw new RepositoryException(e);}}
    static void write(String sql,Object...args)throws Exception{try(var s=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}}
    static class Votes extends VoteRepository{
        @Override public String getName(){return "vote";}@Override public boolean hasTransactionBegun(){return active();}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(sql,args);}@Override public void remove(String id)throws RepositoryException{erase("vote",id);}
    }
    static class Rewards extends RewardRepository{
        @Override public String getName(){return "reward";}@Override public boolean hasTransactionBegun(){return active();}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(sql,args);}@Override public void remove(String id)throws RepositoryException{erase("reward",id);}
    }
    static class Reactions extends ReactionRepository{
        @Override public String getName(){return "reaction";}@Override public boolean hasTransactionBegun(){return active();}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(sql,args);}@Override public void remove(String id)throws RepositoryException{erase("reaction",id);}
    }
}
