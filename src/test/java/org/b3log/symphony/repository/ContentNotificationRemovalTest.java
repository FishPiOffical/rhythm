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
import org.b3log.symphony.model.Reward;
import org.b3log.symphony.util.ContentNotificationReferences;
import org.json.JSONObject;
import org.testng.Assert;
import java.sql.*;
import java.util.*;

/** Real notification/reward repository methods and SQL, using SQLite transaction/row adapters. */
public class ContentNotificationRemovalTest {
    static Connection db;static String failure="";static int removed;
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");
        for(String name:List.of("latkeProps","localProps")){var f=Latkes.class.getDeclaredField(name);f.setAccessible(true);f.set(null,props);}
        var notices=new Notices();var rewards=new Rewards();
        try(var conn=DriverManager.getConnection("jdbc:sqlite::memory:")){
            db=conn;write("CREATE TABLE pref_notification(oId TEXT PRIMARY KEY,userId TEXT,dataId TEXT,dataType INT)");write("CREATE TABLE pref_article(oId TEXT PRIMARY KEY)");write("CREATE TABLE pref_comment(oId TEXT PRIMARY KEY)");write("CREATE TABLE pref_reward(oId TEXT PRIMARY KEY,dataId TEXT,type INT)");
            for(boolean article:List.of(true,false)){
                seed();db.setAutoCommit(false);notices.removeContentReferences("10",article,article?"pref_comment":"pref_article");db.commit();db.setAutoCommit(true);
                var direct=article?Set.of(0,2,4,9,15,20,22):Set.of(1,2,3,13,21);var compound=article?Set.of(23,24,27,28):Set.of(25,26);
                for(int type=0;type<=42;type++)Assert.assertEquals(exists("n-"+type),!direct.contains(type),"direct type "+type);
                for(int type:List.of(23,24,25,26,27,28))Assert.assertEquals(exists("c-"+type),!compound.contains(type),"compound type "+type);
                Assert.assertEquals(count("pref_notification","dataId='11'"),43L);Assert.assertTrue(exists("prefix-other"));Assert.assertTrue(exists("same-prefix-custom"));
                Assert.assertEquals(count("pref_notification","oId LIKE 'many-%' AND dataType="+(article?0:1)),0L);
                Assert.assertEquals(count("pref_notification","oId LIKE 'many-%'"),105L);
            }
            System.out.println("PASS typed article/comment direct and compound references clean all matching notices, preserve other kinds/custom notices and avoid prefix overlap");
            for(boolean article:List.of(true,false)){
                seed();write("INSERT INTO "+(article?"pref_comment":"pref_article")+" VALUES(?)","10");db.setAutoCommit(false);notices.removeContentReferences("10",article,article?"pref_comment":"pref_article");db.commit();db.setAutoCommit(true);
                Assert.assertTrue(exists("n-2"));Assert.assertFalse(exists(article?"n-0":"n-1"));
            }
            System.out.println("PASS ambiguous legacy AT references survive opposite-kind ID collisions while unambiguous typed references are removed");
            seed();
            for(int type=0;type<5;type++){write("INSERT INTO pref_reward VALUES(?,?,?)","r"+type,"10",type);for(int nt=0;nt<=42;nt++)write("INSERT INTO pref_notification VALUES(?,?,?,?)","r"+type+"-n"+nt,"reader","r"+type,nt);}
            db.setAutoCommit(false);
            for(int type:List.of(0,3,4)){var ids=rewards.removeByDataId("10",type);Assert.assertEquals(ids,List.of("r"+type));for(String id:ids)notices.removeRewardReferences(id,type);}
            var commentIds=rewards.removeByDataId("10",Reward.TYPE_C_COMMENT);for(String id:commentIds)notices.removeRewardReferences(id,Reward.TYPE_C_COMMENT);db.commit();db.setAutoCommit(true);
            for(int type:List.of(0,1,3,4)){int nt=ContentNotificationReferences.rewardNotificationType(type);Assert.assertFalse(exists("r"+type+"-n"+nt));Assert.assertEquals(count("pref_notification","dataId='r"+type+"'"),42L);}
            Assert.assertEquals(count("pref_reward","type=2"),1L);Assert.assertEquals(count("pref_notification","dataId='r2'"),43L);
            System.out.println("PASS removed reward IDs remove only their exact purchase/thank/accept notice kinds, retaining user rewards and unrelated same-ID notifications");
            for(String fail:List.of("remove:many-050","lookup")){
                seed();String before=snapshot();db.setAutoCommit(false);failure=fail;
                try{notices.removeContentReferences("10",true,"pref_comment");Assert.fail();}catch(RepositoryException expected){}finally{db.rollback();db.setAutoCommit(true);failure="";}
                Assert.assertEquals(snapshot(),before);if(fail.startsWith("remove"))Assert.assertTrue(removed>0);
            }
            seed();write("INSERT INTO pref_reward VALUES('paid','10',0)");write("INSERT INTO pref_notification VALUES('paid-note','reader','paid',7)");String before=snapshot();db.setAutoCommit(false);failure="remove:paid-note";
            try{for(String id:rewards.removeByDataId("10",0))notices.removeRewardReferences(id,0);Assert.fail();}catch(RepositoryException expected){}finally{db.rollback();db.setAutoCommit(true);failure="";}
            Assert.assertEquals(snapshot(),before);Assert.assertEquals(count("pref_reward","oId='paid'"),1L);
            System.out.println("PASS target lookup, partial notice deletion and post-reward notice failures roll all affected rows back in the caller transaction");
            for(boolean article:List.of(true,false)){
                try{notices.removeContentReferences("10",article,"pref_comment");Assert.fail();}catch(IllegalStateException expected){}
                db.setAutoCommit(false);for(String bad:List.of("10%","10_","10' OR 1=1 --","1".repeat(33))){try{notices.removeContentReferences(bad,article,"pref_comment");Assert.fail();}catch(IllegalArgumentException expected){}}
                try{notices.removeContentReferences("10",article,"pref_comment; DROP TABLE pref_reward");Assert.fail();}catch(IllegalArgumentException expected){}db.rollback();db.setAutoCommit(true);
            }
            try{notices.removeRewardReferences("paid",0);Assert.fail();}catch(IllegalStateException expected){}
            db.setAutoCommit(false);try{notices.removeRewardReferences("paid",2);Assert.fail();}catch(IllegalArgumentException expected){}db.rollback();db.setAutoCommit(true);
            System.out.println("PASS transaction, content ID, table identifier and reward-kind guards reject malformed cleanup requests");
        }
    }
    static void seed()throws Exception{
        for(String table:List.of("pref_notification","pref_reward","pref_article","pref_comment"))write("DELETE FROM "+table);removed=0;
        for(int type=0;type<=42;type++){write("INSERT INTO pref_notification VALUES(?,?,?,?)","n-"+type,"reader","10",type);write("INSERT INTO pref_notification VALUES(?,?,?,?)","other-"+type,"reader","11",type);}
        for(int type:List.of(23,24,25,26,27,28))write("INSERT INTO pref_notification VALUES(?,?,?,?)","c-"+type,"reader","10-member",type);
        write("INSERT INTO pref_notification VALUES('prefix-other','reader','100-member',27)");write("INSERT INTO pref_notification VALUES('same-prefix-custom','reader','10-member',42)");
        for(int i=0;i<210;i++)write("INSERT INTO pref_notification VALUES(?,?,?,?)",String.format("many-%03d",i),"reader","10",i%2==0?0:1);
    }
    static boolean exists(String id)throws Exception{return count("pref_notification","oId='"+id+"'")==1;}
    static long count(String table,String where)throws Exception{try(var s=db.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table+" WHERE "+where)){return r.getLong(1);}}
    static String snapshot()throws Exception{var out=new StringBuilder();for(String t:List.of("pref_notification","pref_reward"))try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM "+t+" ORDER BY oId")){while(r.next())for(int i=1;i<=r.getMetaData().getColumnCount();i++)out.append(r.getObject(i)).append('|');}return out.toString();}
    static boolean active(){try{return !db.getAutoCommit();}catch(Exception e){throw new RuntimeException(e);}}
    static List<JSONObject> query(String sql,Object...args)throws RepositoryException{
        Assert.assertTrue(active());Assert.assertTrue(sql.endsWith(" FOR UPDATE"));if("lookup".equals(failure)&&sql.contains("pref_comment"))throw new RepositoryException("injected lookup failure");
        try(var s=db.prepareStatement(sql.replace(" FOR UPDATE",""))){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next()){var row=new JSONObject();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));out.add(row);}return out;}}catch(Exception e){throw new RepositoryException(e);}
    }
    static void erase(String table,String id)throws RepositoryException{Assert.assertTrue(active());if(failure.equals("remove:"+id))throw new RepositoryException("injected removal failure");try{write("DELETE FROM "+table+" WHERE oId=?",id);removed++;}catch(Exception e){throw new RepositoryException(e);}}
    static void write(String sql,Object...args)throws Exception{try(var s=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}}
    static class Notices extends NotificationRepository{
        @Override public String getName(){return "pref_notification";}@Override public boolean hasTransactionBegun(){return active();}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(sql,args);}@Override public void remove(String id)throws RepositoryException{erase("pref_notification",id);}
    }
    static class Rewards extends RewardRepository{
        @Override public String getName(){return "pref_reward";}@Override public boolean hasTransactionBegun(){return active();}
        @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(sql,args);}@Override public void remove(String id)throws RepositoryException{erase("pref_reward",id);}
    }
}
