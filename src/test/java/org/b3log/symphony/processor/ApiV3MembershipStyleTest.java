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
        final Fixture db=new Fixture();
        final ApiV3MembershipStyleQueryService query=new ApiV3MembershipStyleQueryService(){protected long now(){return 1000;}};
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
    static JSONObject style(JSONObject response,int n){return data(response).getJSONArray("list").getJSONObject(n).getJSONObject("style");}
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new F()){
            for(String token:Arrays.asList(null,"bad","content"))equal(f.http(token,"userIds=1").getInt("status"),token==null||token.equals("bad")?401:403);
            check(f.db.sql.isEmpty(),"denied before SQL");
            for(String params:Arrays.asList(null,"","userIds=","userIds=1,1","userIds=01","userIds=0","userIds=1,","userIds=1%20","userIds=1%27","userIds=1&userIds=2","userIds=1&apiKey=legacy","userIds=1&userId=2","userIds=%C0%AF","userIds=%"))equal(f.http("profile",params).getInt("status"),422);
            check(f.db.sql.isEmpty(),"invalid query before SQL");
            equal(f.http("profile","userIds="+String.join(",",java.util.stream.IntStream.rangeClosed(1,101).mapToObj(String::valueOf).toList())).getInt("status"),422);
        }
        System.out.println("PASS styles 1 actual GET route/profile scope, no Cookie/apiKey fallback, strict raw query rejects pollution/duplicates/malformed IDs and size before SQL");
        try(var f=new F()){
            f.level("VIP4_YEAR","{\"bold\":true,\"underline\":false,\"color\":\"#FFFFFF\",\"autoCheckin\":true}");
            f.membership("1","VIP4_YEAR",0,"{\"bold\":false,\"underline\":true,\"color\":\"galaxy\",\"autoCheckin\":true,\"secret\":\"PRIVATE\"}");
            final var response=f.http("profile","userIds=2%2C1%2C999");final var list=data(response).getJSONArray("list");equal(list.length(),3);
            equal(list.getJSONObject(0).getString("userId"),"2");check(style(response,0).isEmpty(),"non-member empty");
            equal(style(response,1).keySet(),Set.of("bold","underline","color"));equal(style(response,1).getString("color"),"galaxy");check(!style(response,1).getBoolean("bold"),"explicit false preserved");
            check(style(response,2).isEmpty(),"unknown ID no account enumeration");equal(f.db.sql.size(),2);
            check(list.getJSONObject(1).keySet().equals(Set.of("userId","style")),"no level/expiry/raw config");
            f.db.exec("UPDATE membership SET expiresAt=1000 WHERE userId='1'");check(style(f.http("profile","userIds=1"),0).isEmpty(),"expiry exact boundary");
            f.db.exec("UPDATE membership SET expiresAt=0");f.db.exec("UPDATE user SET userStatus=4 WHERE oId='1'");check(style(f.http("other","userIds=1"),0).isEmpty(),"disabled target empty");equal(f.http("profile","userIds=1").getInt("status"),404);
        }
        System.out.println("PASS styles 2 requested-order batch joins current accounts and permanent/expired memberships; only three fields, unknown/nonmembers/disabled targets empty and disabled actor rejected");
        try(var f=new F()){
            f.level("VIP1_MONTH","{\"color\":\"#FFFFFF\"}");f.membership("1","VIP1_MONTH",2000,"{\"bold\":true,\"underline\":true,\"color\":\"galaxy\"}");check(style(f.http("profile","userIds=1"),0).isEmpty(),"unsupported fields and VIP4-only effect omitted");
            f.db.exec("UPDATE membership SET configJson='{\"color\":\"#123aBC\",\"bold\":\"true\"}'");equal(style(f.http("profile","userIds=1"),0).toString(),new JSONObject().put("color","#123aBC").toString());
            f.db.exec("UPDATE membership_level SET benefits='{}'");check(style(f.http("profile","userIds=1"),0).isEmpty(),"current supported definition re-read without cache");
            f.db.exec("DELETE FROM membership_level");check(style(f.http("profile","userIds=1"),0).isEmpty(),"missing level cannot grant style");
        }
        System.out.println("PASS styles 3 current level support and exact VIP4 effect entitlement constrain stored config; missing definition grants no display and no defaults invented");
        try(var f=new F()){
            f.level("VIP4_MONTH","{\"bold\":true}");f.membership("1","VIP4_MONTH",0,"{\"bold\":true}");
            for(String bad:List.of("{","{\"bold\":true,\"bold\":false}","x".repeat(102401))){f.db.exec("UPDATE membership SET configJson=?",bad);equal(f.http("profile","userIds=1").getInt("status"),503);}
            f.db.exec("UPDATE membership SET configJson='{}',expiresAt=-1");equal(f.http("profile","userIds=1").getInt("status"),503);
            f.db.exec("UPDATE membership SET expiresAt=0");f.level("VIP4_MONTH","{}");equal(f.http("profile","userIds=1").getInt("status"),503);
        }
        try(var f=new F()){
            f.level("VIP4_YEAR","{}");f.membership("1","VIP4_YEAR",0,"{}");f.db.exec("INSERT INTO membership SELECT * FROM membership");equal(f.http("profile","userIds=1").getInt("status"),503);
            f.db.failTable="membership";equal(f.http("profile","userIds=2").getInt("status"),503);
        }
        System.out.println("PASS styles 4 invalid/duplicate stored JSON, malformed expiry, duplicate level/membership bindings and repository faults fail explicitly without arbitrary choice or raw configuration");
        try(var f=new F()){
            final var ids=java.util.stream.IntStream.rangeClosed(1,100).mapToObj(String::valueOf).toList();final var list=data(f.http("profile","userIds="+String.join(",",ids))).getJSONArray("list");equal(list.length(),100);for(int i=0;i<100;i++)equal(list.getJSONObject(i).getString("userId"),ids.get(i));equal(f.db.sql.size(),2);
            check(f.db.sql.stream().allMatch(q->q.startsWith("SELECT ")),"read only, no default group/state creation");
        }
        System.out.println("PASS styles 5 100-ID boundary retains full requested order with two bounded projected reads and no writes");
    }
}
