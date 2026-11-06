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
import org.b3log.symphony.repository.FollowRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;
import java.sql.*;
import java.util.*;

/** Read-only owner/type exact SQL, without calling user caches or legacy isFollowing that swallows errors. */
public class ApiV3FollowStateTest {
    interface Work {void run()throws Exception;}
    static void error(int status,Work work)throws Exception{try{work.run();Assert.fail("expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);}}
    static void inject(Object target,String field,Object value)throws Exception{final var f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);}
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var db=DriverManager.getConnection("jdbc:sqlite::memory:")){
            try(var sql=db.createStatement()){
                sql.execute("CREATE TABLE users(oId TEXT PRIMARY KEY,userStatus INT,userPassword TEXT)");
                sql.execute("INSERT INTO users VALUES('1',0,'private'),('2',0,'private'),('3',0,'private'),('4',4,'private')");
                sql.execute("CREATE TABLE tags(oId TEXT PRIMARY KEY,tagStatus INT)");
                sql.execute("INSERT INTO tags VALUES('3',0),('4',1)");
                sql.execute("CREATE TABLE follows(oId TEXT PRIMARY KEY,followerId TEXT,followingId TEXT,followingType INT)");
                sql.execute("INSERT INTO follows VALUES('11','1','2',0),('12','1','2',0),('13','2','3',0),('14','1','3',1),('15','3','1',0)");
            }
            final var service=new ApiV3FollowQueryService();final boolean[] fail={false};
            inject(service,"users",new UserRepository(){public String getName(){return "users";}});
            inject(service,"follows",new FollowRepository(){
                public String getName(){return "follows";}
                public List<JSONObject> select(String sql,Object...args)throws RepositoryException{
                    if(fail[0])throw new RepositoryException("private database failure");
                    Assert.assertTrue(sql.startsWith("SELECT "));try(var statement=db.prepareStatement(sql)){
                        for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);
                        try(var rows=statement.executeQuery()){var values=new ArrayList<JSONObject>();while(rows.next())values.add(new JSONObject().put("targetId",rows.getString("targetId")).put("following",rows.getInt("following")));return values;}
                    }catch(SQLException e){throw new RepositoryException(e);}
                }
            });
            inject(service,"tags",new org.b3log.symphony.repository.TagRepository(){public String getName(){return "tags";}});
            inject(service,"content",new ApiV3ContentQueryService(){public JSONObject viewableArticle(String id,JSONObject viewer){return id.equals("3")?new JSONObject().put("oId",id):null;}});
            final JSONObject owner=new JSONObject().put("oId","1"),other=new JSONObject().put("oId","2");
            Assert.assertEquals(service.userState(owner,"2").keySet(),Set.of("targetId","following"));Assert.assertTrue(service.userState(owner,"2").getBoolean("following"));
            Assert.assertFalse(service.userState(owner,"3").getBoolean("following"));Assert.assertFalse(service.userState(other,"1").getBoolean("following"));Assert.assertTrue(service.userState(other,"3").getBoolean("following"));
            Assert.assertFalse(service.userState(owner,"1").getBoolean("following"));
            System.out.println("PASS follow state 1 one SQL snapshot projects only target/boolean, owner and user-type isolation, duplicate rows do not duplicate results");
            for(String invalid:List.of("0","2 OR 1=1","2/3","99999999999999999999"))error(422,()->service.userState(owner,invalid));
            error(404,()->service.userState(owner,"4"));error(404,()->service.userState(owner,"9"));error(404,()->service.userState(new JSONObject().put("oId","4"),"2"));
            System.out.println("PASS follow state 2 current actor/target status and strict numeric ids reject unavailable targets and injection");
            fail[0]=true;try{service.userState(owner,"2");Assert.fail();}catch(RepositoryException expected){}fail[0]=false;
            try(var rows=db.createStatement().executeQuery("SELECT COUNT(*) FROM follows")){Assert.assertTrue(rows.next());Assert.assertEquals(rows.getInt(1),5);}
            System.out.println("PASS follow state 3 database failure is not false/unfollowed; reads never mutate stored relations");
            Assert.assertTrue(service.tagState(owner,"3").getBoolean("following"));
            Assert.assertFalse(service.tagState(other,"3").getBoolean("following"));
            error(404,()->service.tagState(owner,"4"));error(404,()->service.tagState(owner,"9"));
            Assert.assertFalse(service.articleState(owner,"3",false).getBoolean("following"));
            Assert.assertFalse(service.articleState(owner,"3",true).getBoolean("following"));
            error(404,()->service.articleState(owner,"9",false));error(404,()->service.articleState(new JSONObject().put("oId","4"),"3",true));
            try(var sql=db.createStatement()){sql.execute("INSERT INTO follows VALUES('21','1','3',2),('22','1','3',2),('23','2','3',3)");}
            Assert.assertTrue(service.articleState(owner,"3",false).getBoolean("following"));Assert.assertFalse(service.articleState(owner,"3",true).getBoolean("following"));
            Assert.assertFalse(service.articleState(other,"3",false).getBoolean("following"));Assert.assertTrue(service.articleState(other,"3",true).getBoolean("following"));
            fail[0]=true;try{service.tagState(owner,"3");Assert.fail();}catch(RepositoryException expected){}try{service.articleState(owner,"3",true);Assert.fail();}catch(RepositoryException expected){}fail[0]=false;
            for(String invalid:List.of("0","3 OR 1=1","99999999999999999999")){error(422,()->service.tagState(owner,invalid));error(422,()->service.articleState(owner,invalid,false));}
            System.out.println("PASS follow state 5 tag status, current article visibility, owner/type isolation, duplicate relations and propagated database errors");
            final var endpoint=new org.b3log.symphony.processor.ApiV3FollowProcessor();inject(endpoint,"query",service);
            final var auth=new org.b3log.symphony.processor.middleware.ApiV3AuthMidware();inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String value){return value.equals("owner")||value.equals("wrong")?new JSONObject().put("user",owner).put("scope",value.equals("owner")?"profile.read":"content.read"):null;}});
            org.b3log.symphony.processor.ApiV3FollowProcessor.registerRoutes(endpoint,auth,new org.b3log.symphony.processor.middleware.ApiV3WriteMidware());org.b3log.latke.http.Dispatcher.mapping();
            for(String token:List.of("","wrong","owner")){
                final var raw=new io.netty.handler.codec.http.DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/me/follows/users/2");
                try{
                    if(!token.isEmpty())raw.headers().set("Authorization","Bearer "+token);raw.headers().set("Cookie","legacy");
                    final var request=new org.b3log.latke.http.Request(null,raw);request.setBytes(new byte[0]);request.setParams(new HashMap<>(Map.of("apiKey","legacy")));
                    final var context=new org.b3log.latke.http.RequestContext(request,new org.b3log.latke.http.Response(null,new io.netty.handler.codec.http.DefaultHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpResponseStatus.OK)));
                    context.addHandler(new org.b3log.latke.http.handler.RouteHandler());context.addHandler(endpoint::userState);context.handle();
                    Assert.assertEquals(context.attr("status"),token.isEmpty()?401:token.equals("wrong")?403:200);
                    if(token.equals("owner")){Assert.assertEquals(context.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertTrue(((JSONObject)context.attr("data")).getBoolean("following"));}
                }finally{raw.release();}
            }
            for(String path:List.of("/api/v3/me/follows/tags/3","/api/v3/me/collections/3","/api/v3/me/subscriptions/3"))for(String token:List.of("","owner","wrong")){
                final var raw=new io.netty.handler.codec.http.DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,path);
                try{
                    if(!token.isEmpty())raw.headers().set("Authorization","Bearer "+token);
                    final var request=new org.b3log.latke.http.Request(null,raw);request.setBytes(new byte[0]);request.setParams(new HashMap<>(Map.of("apiKey","legacy")));
                    final var context=new org.b3log.latke.http.RequestContext(request,new org.b3log.latke.http.Response(null,new io.netty.handler.codec.http.DefaultHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpResponseStatus.OK)));
                    context.addHandler(new org.b3log.latke.http.handler.RouteHandler());context.addHandler(path.contains("/tags/")?endpoint::tagState:path.contains("/collections/")?endpoint::collectionState:endpoint::subscriptionState);context.handle();
                    Assert.assertEquals(context.attr("status"),token.isEmpty()?401:token.equals("owner")?403:200);
                    if(token.equals("wrong")){Assert.assertEquals(context.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(((JSONObject)context.attr("data")).keySet(),Set.of("targetId","following"));}
                }finally{raw.release();}
            }
            System.out.println("PASS follow state 6 three actual Dispatcher/auth/controller endpoints require content.read, reject legacy credentials and return no-store whitelist");
            System.out.println("PASS follow state 4 actual route/auth/controller gates Cookie/query-only 401 and scope 403; owner GET white list and no-store");
        }
    }
}
