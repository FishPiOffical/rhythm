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
import org.b3log.latke.http.handler.RouteHandler;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.service.ApiV3MembershipStyleQueryService;
import org.json.*;
import io.netty.handler.codec.http.*;
import java.util.*;
import static org.b3log.symphony.processor.ApiV3MembershipTest.*;

/** Production SQL, route/auth and style projection; only JDBC dialect and token/envelope dependencies adapted. */
public final class ApiV3MembershipStyleTest {
    static final String PATH="/api/v3/membership/styles";
    static final class F implements AutoCloseable {
        long time = 1000;
        final Fixture db=new Fixture();
        final ApiV3MembershipStyleQueryService query=new ApiV3MembershipStyleQueryService(){protected long now(){return time;}};
        final ApiV3MembershipStyleProcessor processor=new ApiV3MembershipStyleProcessor();
        F()throws Exception{
            injectSuper(query,"users",new UserRepository(){public String getName(){return "user";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return db.read(q,a);}});
            injectSuper(query,"memberships",new MembershipRepository(){public String getName(){return "membership";}public List<JSONObject> select(String q,Object...a)throws RepositoryException{return db.read(q,a);}});
            injectSuper(query,"levels",new MembershipLevelRepository(){public String getName(){return "membership_level";}});
            inject(processor,"query",query);ApiV3MembershipStyleProcessor.registerRoutes(processor,db.auth);Dispatcher.mapping();
        }
        void level(String code,String benefits)throws Exception{db.exec("INSERT INTO membership_level VALUES(?,?,'name',100,'MONTH',30,?,'PRIVATE-token')",String.valueOf(System.nanoTime()),code,benefits);}
        void membership(String id,String code,long expires,String config)throws Exception{db.membership(id,1,expires,config);db.exec("UPDATE membership SET lvCode=? WHERE userId=?",code,id);}
        JSONObject http(String token,String params)throws Exception{
            final var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,PATH+(params==null?"":"?"+params));
            try{
                raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);
                final var request=new Request(null,raw);request.setParams(new HashMap<>(Map.of("userIds","2","apiKey","legacy")));
                final var context=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));
                context.attr("user",new JSONObject().put("oId","2"));context.addHandler(new RouteHandler());context.addHandler(processor::styles);context.handle();
                final var result=new JSONObject().put("status",context.attr("test.status"));
                if(context.attr("test.data")!=null)result.put("data",context.attr("test.data"));
                if(result.getInt("status")==200){equal(context.getResponse().getHeader("Cache-Control"),"no-store");check(!result.toString().contains("PRIVATE"),"closed style response");}
                return result;
            }finally{raw.release();}
        }
        public void close()throws Exception{db.close();}
    }
    static void injectSuper(Object target,String field,Object value)throws Exception{var f=target.getClass().getSuperclass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);}
    static String revision(JSONObject response) { return data(response).getString("version"); }
    static JSONObject item(JSONObject response, String id) {
        for (Object value : data(response).getJSONArray("list")) if (((JSONObject) value).getString("userId").equals(id)) return (JSONObject) value;
        throw new AssertionError("Missing user " + id);
    }
    static JSONObject config(JSONObject response, String id) { return new JSONObject(item(response,id).getString("configJson")); }
    static void invalidate() { org.b3log.symphony.util.MembershipStyleCache.invalidate(); }
    public static void main(String[] args) throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("runtimeCache","LOCAL_LRU");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new F()) {
            for(String token:Arrays.asList(null,"bad","content")) equal(f.http(token,null).getInt("status"),token==null||token.equals("bad")?401:403);
            check(f.db.sql.isEmpty(),"denied before SQL");
            for(String params:List.of("userIds=1","version=","version=x","version="+"A".repeat(64),"version="+"a".repeat(64)+"&version="+"a".repeat(64),"apiKey=legacy","version=%C0%AF","version=%")) equal(f.http("profile",params).getInt("status"),422);
            check(f.db.sql.isEmpty(),"invalid query before SQL");
        }
        System.out.println("PASS snapshot 1 actual route/profile scope, no Cookie/apiKey fallback and strict optional version rejects pollution/legacy userIds before SQL");
        try(var f=new F()) {
            f.level("VIP4_YEAR","{\"bold\":true,\"underline\":false,\"color\":\"#FFFFFF\",\"autoCheckin\":true}");
            f.membership("1","VIP4_YEAR",0,"{\"bold\":false,\"underline\":true,\"color\":\"galaxy\",\"secret\":\"PRIVATE\"}");
            f.membership("2","VIP4_YEAR",2000,"{\"color\":\"#123456\"}");
            final var response=f.http("profile",null); equal(data(response).getJSONArray("list").length(),2);
            equal(config(response,"1").keySet(),Set.of("bold","underline","color")); equal(config(response,"1").getString("color"),"galaxy"); check(!config(response,"1").getBoolean("bold"),"false preserved");
            check(item(response,"1").keySet().equals(Set.of("userId","configJson")),"only normalized display config, no level/expiry/secrets");
            equal(data(response).getLong("maxAgeMs"),1000L); equal(f.db.sql.size(),2);
            final var unchanged=f.http("profile","version="+revision(response)); check(data(unchanged).getBoolean("unchanged"),"content unchanged"); check(!data(unchanged).has("list"),"no redundant directory"); equal(f.db.sql.size(),3);
            f.time=2000; final var expired=f.http("profile","version="+revision(response)); check(!data(expired).getBoolean("unchanged"),"natural expiry changes version without a write"); equal(data(expired).getJSONArray("list").length(),1);
            f.db.exec("UPDATE membership SET configJson='{\"color\":\"#ABCDEF\"}' WHERE userId='1'"); invalidate(); final var changed=f.http("profile","version="+revision(expired)); check(!revision(changed).equals(revision(expired)),"same member count but changed config changes content version"); equal(config(changed,"1").getString("color"),"#ABCDEF");
            f.db.exec("UPDATE user SET userStatus=4 WHERE oId='1'"); equal(f.http("profile","version="+revision(changed)).getInt("status"),404);
        }
        System.out.println("PASS snapshot 2 full sorted member directory, normalized config, shared cache/unchanged response, same-count edits, natural expiry and current actor authorization");
        try(var f=new F()) {
            f.level("VIP4_MONTH","{\"bold\":true}"); f.membership("1","VIP4_MONTH",0,"{"); f.membership("2","VIP4_MONTH",0,"{\"bold\":true}");
            final var bad=f.http("profile",null); equal(bad.getInt("status"),200); check(item(bad,"1").isNull("configJson"),"invalid row explicitly unknown"); check(config(bad,"2").getBoolean("bold"),"other user remains available");
            f.db.exec("UPDATE membership SET configJson='{\"bold\":false}' WHERE userId='1'"); invalidate(); final var fixed=f.http("profile","version="+revision(bad)); check(!data(fixed).getBoolean("unchanged"),"repair invalidates version"); check(!config(fixed,"1").getBoolean("bold"),"repaired config");
            f.db.exec("INSERT INTO membership SELECT * FROM membership WHERE userId='1'"); invalidate(); check(item(f.http("profile",null),"1").isNull("configJson"),"duplicate binding not arbitrary choice");
            f.db.exec("UPDATE membership SET expiresAt=-1 WHERE userId='2'"); invalidate(); check(item(f.http("profile",null),"2").isNull("configJson"),"bad expiry unknown");
            f.db.failTable="membership"; invalidate(); equal(f.http("profile",null).getInt("status"),503);
        }
        System.out.println("PASS snapshot 3 corrupt/duplicate configurations and bad expiry isolated per ID, repairs update version, repository failures remain explicit 503");
        try(var f=new F()) {
            f.level("VIP1_MONTH","{\"color\":\"#FFFFFF\"}");f.membership("1","VIP1_MONTH",0,"{\"bold\":true,\"color\":\"galaxy\"}");
            final var empty=config(f.http("profile",null),"1"); check(empty.isEmpty(),"no unsupported fields or VIP4 effects granted");
            f.db.exec("UPDATE membership SET configJson='{\"color\":\"#112233\"}'"); f.time+=30000; final var reread=f.http("profile",null); equal(config(reread,"1").getString("color"),"#112233");
            final int reads=f.db.sql.size(); f.http("other","version="+revision(reread)); equal(f.db.sql.size(),reads+1); check(f.db.sql.stream().allMatch(q->q.startsWith("SELECT ")),"all reads, no state creation");
        }
        System.out.println("PASS snapshot 4 entitlement projection, 30-second refresh for other nodes/out-of-band writes, per-reader auth with shared snapshot and no writes");
        try(var f=new F()) {
            injectSuper(f.query,"memberships",new MembershipRepository(){public String getName(){return "membership";}public List<JSONObject> select(String q,Object...a){return Collections.nCopies(10001,new JSONObject());}});
            equal(f.http("profile",null).getInt("status"),503);
        }
        System.out.println("PASS snapshot 5 oversized complete directory fails explicitly instead of reporting truncated success");
        try(var f=new F()) {
            f.level("VIP4_MONTH","{\"color\":\"#FFFFFF\"}");f.membership("1","VIP4_MONTH",0,"{\"color\":\"#112233\"}");
            final var cache=new org.b3log.symphony.cache.MembershipCache();
            final var row=new JSONObject().put("userId","1").put("lvCode","VIP4_MONTH").put("state",1).put("expiresAt",0).put("configJson","{\"color\":\"#112233\"}");
            cache.put(row);final long generation=org.b3log.symphony.util.MembershipStyleCache.generation();cache.put(new JSONObject(row.toString()));equal(org.b3log.symphony.util.MembershipStyleCache.generation(),generation);
            final var before=f.http("profile",null);
            f.db.exec("UPDATE membership SET configJson='{\"color\":\"#445566\"}'");row.put("configJson","{\"color\":\"#445566\"}");cache.put(row);
            final var after=f.http("profile","version="+revision(before));check(!data(after).getBoolean("unchanged"),"actual cache.put invalidates snapshot");equal(config(after,"1").getString("color"),"#445566");
            f.db.exec("UPDATE membership SET state=0");cache.remove("1");equal(data(f.http("profile",null)).getJSONArray("list").length(),0);
        }
        System.out.println("PASS snapshot 6 actual MembershipCache put/remove invalidates snapshots, identical read warming does not churn the generation, removal returns a complete empty directory");
        try(var f=new F()) {
            f.level("VIP4_YEAR","{}");
            final String config=new JSONObject().put("padding","x".repeat(70000)).toString();
            for(int i=100;i<130;i++){f.db.exec("INSERT INTO user VALUES(?,0,'PRIVATE')",String.valueOf(i));f.membership(String.valueOf(i),"VIP4_YEAR",0,config);}
            equal(f.http("profile",null).getInt("status"),503);
        }
        System.out.println("PASS snapshot 7 source byte budget is enforced by SQL before materializing raw configurations, rather than returning a partial directory");
    }
}
