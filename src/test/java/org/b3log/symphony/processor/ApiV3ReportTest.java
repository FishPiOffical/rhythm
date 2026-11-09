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
package org.b3log.symphony.processor;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.*;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;

/** Real SQL projection and real Dispatcher/auth/controller; no running server or MySQL. */
public class ApiV3ReportTest {
    static void inject(Object target,String key,Object value)throws Exception{var f=target.getClass().getDeclaredField(key);f.setAccessible(true);f.set(target,value);}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");
        final ApiV3ReportQueryService query=new ApiV3ReportQueryService();
        final ApiV3ReportProcessor controller=new ApiV3ReportProcessor();
        final ApiV3AuthMidware auth=new ApiV3AuthMidware();
        boolean fail; int reads;
        Fixture()throws Exception {
            try(var s=db.createStatement()){s.execute("CREATE TABLE u(oId TEXT,userStatus INT,userPassword TEXT)");s.execute("INSERT INTO u VALUES('1',0,'PRIVATE'),('2',4,'PRIVATE')");}
            inject(query,"users",new UserRepository(){public String getName(){return "u";}public List<JSONObject> select(String q,Object...a)throws RepositoryException {
                reads++;Assert.assertEquals(q,"SELECT oId,userStatus FROM `u` WHERE oId=?");if(fail)throw new RepositoryException("PRIVATE_FAILURE");
                try(var p=db.prepareStatement(q)){p.setObject(1,a[0]);try(var r=p.executeQuery()){var rows=new ArrayList<JSONObject>();while(r.next())rows.add(new JSONObject().put("oId",r.getString(1)).put("userStatus",r.getInt(2)));return rows;}}catch(SQLException e){throw new RepositoryException(e);}
            }});
            inject(controller,"query",query);
            inject(auth,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){public JSONObject authenticate(String t){return Set.of("owner","disabled","wrong").contains(t)?new JSONObject().put("scope",t.equals("wrong")?"profile.read":"content.read").put("user",new JSONObject().put("oId",t.equals("disabled")?"2":"1")):null;}});
        }
        RequestContext http(String token)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/report/options?apiKey=legacy&userId=2");
            try{raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new Request(null,raw);request.setParams(new HashMap<>(Map.of("apiKey","legacy","userId","2")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(controller::options);c.handle();return c;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();}
    }
    interface Action{void run()throws Exception;}
    static void error(int status,Action a)throws Exception{try{a.run();Assert.fail("Expected "+status);}catch(ApiV3Exception e){Assert.assertEquals(e.status,status);Assert.assertFalse(e.getMessage().contains("PRIVATE"));}}
    public static void main(String[]args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            var data=f.query.options("1");Assert.assertEquals(data.keySet(),Set.of("targets"));var t=data.getJSONArray("targets");Assert.assertEquals(t.length(),4);
            int[][] values={{0,1,2,3,4,49},{0,1,2,3,4,49},{5,6,7,49},{0,1,2,3,4,49}};
            for(int i=0;i<4;i++){var target=t.getJSONObject(i);Assert.assertEquals(target.keySet(),Set.of("targetType","label","reasons"));Assert.assertEquals(target.getInt("targetType"),i);var r=target.getJSONArray("reasons");Assert.assertEquals(r.length(),values[i].length);for(int j=0;j<r.length();j++){Assert.assertEquals(r.getJSONObject(j).keySet(),Set.of("reason","label"));Assert.assertEquals(r.getJSONObject(j).getInt("reason"),values[i][j]);Assert.assertFalse(r.getJSONObject(j).getString("label").isBlank());}}
            Assert.assertFalse(data.toString().contains("PRIVATE"));t.remove(0);Assert.assertEquals(f.query.options("1").getJSONArray("targets").length(),4);
        }
        System.out.println("PASS 1 exact existing four targets/reason sets and labels, fresh whitelist DTO, no target or user disclosure");
        try(var f=new Fixture()){
            for(String id:Arrays.asList(null,"","0","1 OR 1=1","1".repeat(20)))error(422,()->f.query.options(id));Assert.assertEquals(f.reads,0);
            error(404,()->f.query.options("2"));error(404,()->f.query.options("9"));
            try(var s=f.db.createStatement()){s.execute("INSERT INTO u VALUES('1',0,'PRIVATE')");}error(404,()->f.query.options("1"));
            f.fail=true;error(503,()->f.query.options("1"));
        }
        System.out.println("PASS 2 parameterized actual SQL, invalid IDs before query, disabled/missing/duplicate actors and sanitized driver failures");
        try(var f=new Fixture()){
            ApiV3ReportProcessor.registerRoutes(f.controller,f.auth,new org.b3log.symphony.processor.middleware.ApiV3WriteMidware());Dispatcher.mapping();
            for(String token:Arrays.asList(null,"bad","wrong")){int reads=f.reads;Assert.assertEquals(f.http(token).attr("test.status"),"wrong".equals(token)?403:401);Assert.assertEquals(f.reads,reads);}
            var c=f.http("owner");Assert.assertEquals(c.attr("test.status"),200);Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(c.getResponse().getHeader("Vary"),"Authorization");Assert.assertEquals(((JSONObject)c.attr("test.data")).getJSONArray("targets").length(),4);Assert.assertEquals(f.http("disabled").attr("test.status"),404);
        }
        System.out.println("PASS 3 real Dispatcher/controller content.read gate, Cookie/apiKey cannot supply identity, no-store envelope");
        System.out.println("Report options isolated tests passed: 3 groups");
    }
}
