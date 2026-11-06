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

import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ChatOnlineSnapshot;
import org.json.*;
import org.testng.Assert;
import java.sql.*;
import java.util.*;
import static org.b3log.symphony.service.ApiV3ChatQueryServiceTest.*;

/** Actual membership lookup SQL and privacy DTO, using isolated SQLite and explicit connection sources. */
public class ApiV3ChatOnlineQueryServiceTest {
    static class Online extends ApiV3ChatOnlineQueryService {
        Set<String> ids=Set.of();
        @Override protected Set<String> localIds(){return ids;}
    }
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final Online service=new Online();
        final List<Integer> batches=new ArrayList<>(); boolean unavailable;
        Fixture()throws Exception {
            try(var s=db.createStatement()){s.execute("CREATE TABLE users(oId TEXT PRIMARY KEY,userName TEXT,userAvatarURL TEXT,userOnlineStatus INTEGER,userStatus INTEGER,userPassword TEXT)");}
            UserRepository repo=new UserRepository(){
                @Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException {
                    batches.add(args.length);if(unavailable)throw new RepositoryException("unavailable");
                    Assert.assertFalse(sql.contains("*"));Assert.assertFalse(sql.contains("userPassword"));
                    try(var s=db.prepareStatement(sql)){
                        for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);
                        try(var rows=s.executeQuery()){
                            var out=new ArrayList<JSONObject>();
                            while(rows.next())out.add(new JSONObject().put("oId",rows.getString(1)).put("userName",rows.getString(2)).put("userAvatarURL",rows.getString(3)).put("userOnlineStatus",rows.getInt(4)).put("userStatus",rows.getInt(5)));
                            return out;
                        }
                    }catch(SQLException e){throw new RepositoryException(e);}
                }
            };
            injectBase(service,"users",repo);
        }
        void add(String id,String name,int privacy,int status)throws Exception {
            try(var s=db.prepareStatement("INSERT INTO users VALUES(?,?,?,?,?,?)")){
                s.setString(1,id);s.setString(2,name);s.setString(3,"javascript:PRIVATE");s.setInt(4,privacy);s.setInt(5,status);s.setString(6,"PRIVATE-password");s.executeUpdate();
            }
        }
        public void close()throws Exception{db.close();ChatOnlineSnapshot.publish(List.of(),0,false);}
    }
    static void injectBase(Object bean,String key,Object value)throws Exception{var field=ApiV3ChatOnlineQueryService.class.getDeclaredField(key);field.setAccessible(true);field.set(bean,value);}
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");org.b3log.latke.Latkes.setLatkeProps(props);org.b3log.latke.Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            f.add("1","Alice",0,0);f.add("2","Bob",1,0);f.add("3","Charlie",0,1);
            var names=new ArrayList<>(List.of("Alice","Alice","Bob","Charlie","Unknown"));ChatOnlineSnapshot.publish(names,12345,false);names.clear();f.service.ids=Set.of("1","2","3");
            var viewer=new JSONObject().put("oId","9");var data=f.service.list(viewer,1,20);
            Assert.assertEquals(ids(data),List.of("1"));Assert.assertEquals(data.getJSONObject("pagination").getInt("total"),1);Assert.assertFalse(data.getBoolean("nodesComplete"));Assert.assertEquals(data.getLong("nodesSampledAt"),12345);
            Assert.assertEquals(data.getJSONArray("list").getJSONObject(0).keySet(),Set.of("id","userName","userAvatarURL"));noPrivate(data);Assert.assertEquals(data.getJSONArray("list").getJSONObject(0).getString("userAvatarURL"),"");
            Assert.assertEquals(ids(f.service.list(new JSONObject().put("oId","2"),1,20)),List.of("1","2"));
            Assert.assertEquals(ids(f.service.list(new JSONObject().put("userRole",Role.ROLE_ID_C_ADMIN),1,20)),List.of("1","2"));
            try(var s=f.db.createStatement()){s.executeUpdate("UPDATE users SET userOnlineStatus=1 WHERE oId='1'");}
            Assert.assertEquals(f.service.list(viewer,1,20).getJSONObject("pagination").getInt("total"),0);
        }
        System.out.println("PASS deduplicated local/node membership uses current active profiles, online privacy including self/admin, immutable samples and whitelisted safe avatars");
        try(var f=new Fixture()){
            var names=new ArrayList<String>();for(int i=0;i<405;i++){String name=String.format("User%03d",i);f.add(""+i,name,0,0);names.add(name);}
            names.add("' OR 1=1 --");ChatOnlineSnapshot.publish(names,123,true);
            var first=f.service.list(null,1,50);Assert.assertEquals(first.getJSONObject("pagination").getInt("total"),405);Assert.assertEquals(first.getJSONArray("list").length(),50);Assert.assertTrue(first.getBoolean("hasMore"));
            var last=f.service.list(null,9,50);Assert.assertEquals(last.getJSONArray("list").length(),5);Assert.assertFalse(last.getBoolean("hasMore"));Assert.assertEquals(last.getJSONArray("list").getJSONObject(0).getString("userName"),"User400");
            Assert.assertTrue(f.service.list(null,100,50).getJSONArray("list").isEmpty());Assert.assertTrue(f.batches.stream().allMatch(n->n<=200));
            for(int p:List.of(0,Integer.MAX_VALUE))reject(422,()->f.service.list(null,p,20));for(int s:List.of(0,51))reject(422,()->f.service.list(null,1,s));
            f.unavailable=true;try{f.service.list(null,1,20);Assert.fail();}catch(RepositoryException expected){}
        }
        System.out.println("PASS actual parameterized membership SQL batches beyond 200 without truncation, sorts/paginates after visibility filtering and propagates database failures");
        try(var history=new ApiV3ChatQueryServiceTest.Fixture();var f=new Fixture()){
            f.add("123","Reader",1,0);f.service.ids=Set.of("123");ChatOnlineSnapshot.publish(List.of(),0,false);inject(history.processor,"onlineQuery",f.service);
            Assert.assertEquals(history.request(null,Map.of(),null,history.processor::online).getInt("status"),401);
            history.scope="profile.read";Assert.assertEquals(history.request("Bearer VALID",Map.of(),null,history.processor::online).getInt("status"),403);history.scope="content.read";
            var data=history.request("Bearer VALID",Map.of(),null,history.processor::online).getJSONObject("data");Assert.assertEquals(ids(data),List.of("123"));Assert.assertFalse(data.getBoolean("nodesComplete"));
            Assert.assertEquals(history.request("Bearer VALID",Map.of("size","51"),null,history.processor::online).getInt("status"),422);
            Assert.assertEquals(history.request("Bearer VALID",Map.of("p","2147483647"),null,history.processor::online).getInt("status"),422);
        }
        System.out.println("PASS actual controller/auth requires content.read Bearer, uses its viewer rather than legacy Cookie/apiKey identity, sets private response headers and validates pagination");
        var local=new ApiV3ChatOnlineQueryService();injectBase(local,"realtime",new ApiV3RealtimeService());
        var connections=org.b3log.symphony.processor.channel.ChatroomChannel.onlineUsers;
        try {
            connections.put("first",new JSONObject().put("oId","123").put("userPassword","PRIVATE"));
            connections.put("second",new JSONObject().put("oId","123"));connections.put("empty",new JSONObject());
            Assert.assertEquals(local.localIds(),Set.of("123"));
        } finally {connections.clear();}
        System.out.println("PASS production legacy connection collector projects only nonempty user IDs and deduplicates multiple connections");
    }
}
