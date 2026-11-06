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
import org.b3log.latke.http.*;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.sql.*;
import java.util.*;

/** Actual current-owner SELECT, URL projection, HTTP controller, scope and Dispatcher route. SQLite/token/envelope adapted. */
public class ApiV3PersonalityQueryTest {
    static void inject(Object target,Class<?> type,String name,Object value)throws Exception {var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static class Fixture implements AutoCloseable {
        final Connection db=DriverManager.getConnection("jdbc:sqlite::memory:");final ApiV3PersonalityQueryService query=new ApiV3PersonalityQueryService();final ApiV3SettingsProcessor controller=new ApiV3SettingsProcessor();final ApiV3AuthMidware auth=new ApiV3AuthMidware();boolean fail;int reads;
        Fixture()throws Exception {
            sql("CREATE TABLE custom_user(oId TEXT PRIMARY KEY,userStatus INT,mbti TEXT,userPassword TEXT,userEmail TEXT)");
            sql("INSERT INTO custom_user VALUES('1',0,'INTJ-A','PRIVATE_PASSWORD','PRIVATE_EMAIL'),('2',0,'ENFP','OTHER_PASSWORD','OTHER_EMAIL')");
            inject(query,ApiV3PersonalityQueryService.class,"users",new UserRepository(){@Override public String getName(){return "custom_user";}@Override public List<JSONObject> select(String text,Object...args)throws RepositoryException {
                reads++;Assert.assertEquals(text,"SELECT mbti FROM `custom_user` WHERE oId=? AND userStatus=?");if(fail)throw new RepositoryException("PRIVATE_DRIVER_ERROR");
                try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){var out=new ArrayList<JSONObject>();while(r.next())out.add(new JSONObject().put("mbti",r.getString(1)));return out;}}
                catch(SQLException e){throw new RepositoryException(e);}
            }});
            inject(controller,ApiV3SettingsProcessor.class,"personalities",query);
            inject(auth,ApiV3AuthMidware.class,"apiV3TokenMgmtService",new ApiV3TokenMgmtService(){@Override public JSONObject authenticate(String token){return Set.of("reader","other","wrong").contains(token)?new JSONObject().put("user",new JSONObject().put("oId",token.equals("other")?"2":"1")).put("scope",token.equals("wrong")?"settings.write":"profile.read"):null;}});
        }
        void sql(String text,Object...args)throws Exception{try(var s=db.prepareStatement(text)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}}
        RequestContext http(String token,boolean route)throws Exception {return http(token,route,false);}
        RequestContext http(String token,boolean route,boolean options)throws Exception {
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/me/settings/personality"+(options?"-options":"")+"?userId=2&apiKey=old");
            try{
                raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                var request=new Request(null,raw);request.setParams(new HashMap<>(Map.of("userId","2","apiKey","old")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","2"));
                if(route){c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{var resolution=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");Assert.assertEquals(resolution.getContextHandlerMeta().getInvokeHolder().getName(),options?"personalityOptions":"personality");if(options)controller.personalityOptions(context);else controller.personality(context);});c.handle();}
                else{if(options)c.addHandler(controller::personalityOptions);else c.addHandler(controller::personality);auth.profileRead(c);}return c;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();}
    }
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Fixture()){
            Assert.assertEquals(f.query.read("1").keySet(),Set.of("mbti"));Assert.assertEquals(f.query.read("1").getString("mbti"),"INTJ-A");Assert.assertEquals(f.query.read("2").getString("mbti"),"ENFP");
            for(String bad:Arrays.asList(null,"","01","1' OR 1=1 --","1".repeat(20))){try{f.query.read(bad);Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,422);}}
            f.sql("UPDATE custom_user SET userStatus=4 WHERE oId='1'");try{f.query.read("1");Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,404);}
            try{f.query.read("9");Assert.fail();}catch(ApiV3Exception e){Assert.assertEquals(e.status,404);}
        }
        System.out.println("PASS personality reads only the current owner's MBTI with bound ID/status and actual repository prefix; rejects invalid IDs and unavailable accounts without selecting private columns");
        try(var f=new Fixture()){
            for(String value:List.of("INTJ-A-extra","<script>alert(1)</script>","userPassword","INFP\nT","INVALID","")){
                f.sql("UPDATE custom_user SET mbti=? WHERE oId='1'",value);Assert.assertEquals(f.query.read("1").getString("mbti"),"");
            }
            for(String value:List.of("INTJ","INTP-T","ESFP-A","ENFJ")){
                f.sql("UPDATE custom_user SET mbti=? WHERE oId='1'",value);Assert.assertEquals(f.query.read("1").getString("mbti"),value);
            }
        }
        System.out.println("PASS personality GET returns only known stored MBTI forms, suppresses malformed stored values and never selects private account columns");
        try(var f=new Fixture()){
            for(String token:Arrays.asList(null,"invalid","wrong")){int before=f.reads;var c=f.http(token,false);Assert.assertEquals(c.attr("test.status"),token==null||token.equals("invalid")?401:403);Assert.assertEquals(f.reads,before);}
            PersonalityRouteBindings.register(f.controller,f.auth);Dispatcher.mapping();
            var c=f.http("reader",true);Assert.assertEquals(c.attr("test.status"),200);var data=(JSONObject)c.attr("test.data");Assert.assertEquals(data.keySet(),Set.of("mbti"));Assert.assertEquals(data.getString("mbti"),"INTJ-A");Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");
            Assert.assertEquals(((JSONObject)f.http("other",false).attr("test.data")).getString("mbti"),"ENFP");
            int before=f.reads;var choices=f.http("reader",true,true);Assert.assertEquals(choices.attr("test.status"),200);Assert.assertEquals(((JSONObject)choices.attr("test.data")).getJSONArray("types").length(),16);Assert.assertEquals(choices.getResponse().getHeader("Cache-Control"),"no-store");Assert.assertEquals(f.reads,before);
            for(String token:Arrays.asList(null,"invalid","wrong"))Assert.assertEquals(f.http(token,true,true).attr("test.status"),token==null||token.equals("invalid")?401:403);
            Assert.assertEquals(f.reads,before);
            f.fail=true;c=f.http("reader",false);Assert.assertEquals(c.attr("test.status"),503);Assert.assertFalse(c.attr("test.code").toString().contains("PRIVATE"));Assert.assertNull(c.attr("test.data"));
        }
        System.out.println("PASS actual personality route/controller requires profile.read Bearer, ignores forged Cookie/query identities, returns a no-store MBTI-only DTO and sanitizes database availability failures");
        final JSONObject choices=org.b3log.symphony.util.ApiV3Personality.options();
        Assert.assertEquals(choices.keySet(),Set.of("types","variants"));Assert.assertEquals(choices.getJSONArray("types").length(),16);Assert.assertEquals(new HashSet<>(choices.getJSONArray("types").toList()).size(),16);
        Assert.assertEquals(choices.getJSONArray("variants").toList(),List.of("A","T"));
        for(Object type:choices.getJSONArray("types"))for(String suffix:List.of("","-A","-T"))Assert.assertEquals(org.b3log.symphony.util.ApiV3Personality.dto(new JSONObject().put("mbti",type+suffix)).getString("mbti"),type+suffix);
        System.out.println("PASS all 16 MBTI types and optional A/T variants round-trip through the actual options and DTO without hidden account fields");
    }
}
