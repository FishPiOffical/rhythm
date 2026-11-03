package org.b3log.symphony.processor;

import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpMethod;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.Response;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Cookie;
import org.b3log.latke.service.ServiceException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Extracted production controller methods; original raw content survives actual framework preprocessing. */
public class AuthBodyTest {
    public static class Tokens {
        public int issued, refreshed, revoked;
        public String last;
        public boolean failure;
        private JSONObject pair() { return new JSONObject().put("accessToken","fixture-access").put("refreshToken","fixture-refresh").put("refreshExpiresIn",300); }
        public JSONObject issue(String actor)throws ServiceException{issued++;return pair();}
        public JSONObject refresh(String value)throws ServiceException{refreshed++;last=value;if(failure)throw new ServiceException("fixture");return pair();}
        public void revokeRefresh(String value)throws ServiceException{revoked++;last=value;if(failure)throw new ServiceException("fixture");}
    }
    private static final ApiV3Processor P=new ApiV3Processor();
    private static void check(boolean value){if(!value)throw new AssertionError();}
    private static int calls(){var t=ApiV3Processor.apiV3TokenMgmtService;return t.issued+t.refreshed+t.revoked+ApiV3Processor.loginProcessor.calls;}
    private record Result(int status,String code,JSONObject data,boolean cookies){}
    private static Result request(String endpoint,byte[] body,String media,String origin){
        var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.POST,"/api/v3/auth/"+endpoint);
        raw.headers().set("Host","localhost:8081").set("Cookie","fp_v3_refresh=existing-fixture");
        if(media!=null)raw.headers().set("Content-Type",media);if(origin!=null)raw.headers().set("Origin",origin);
        raw.content().writeBytes(body);
        var req=new Request(null,raw){@Override public String getRemoteAddr(){return "127.0.0.1";}};
        try{
            req.setCookies(new java.util.HashSet<>(java.util.Set.of(new Cookie("fp_v3_refresh","existing-fixture")))); // The HTTP dispatcher decodes cookies separately from parseForm.
            var parse=Request.class.getDeclaredMethod("parseForm");parse.setAccessible(true);parse.invoke(req);
            var response=new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK));var c=new RequestContext(req,response);
            switch(endpoint){case "login"->P.login(c);case "refresh"->P.refresh(c);case "logout"->P.logout(c);default->throw new AssertionError();}
            return new Result((int)c.attr("status"),(String)c.attr("code"),(JSONObject)c.attr("data"),!response.getCookies().isEmpty());
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}finally{raw.release();}
    }
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static void rejected(String endpoint,byte[] body,String media,int expected){int before=calls();var r=request(endpoint,body,media,"http://localhost:8081");check(r.status==expected);check(calls()==before);check(!r.cookies);}
    public static void main(String[] args){
        for(String endpoint:List.of("login","refresh","logout")){
            for(String bad:List.of("{'nameOrEmail':'fixture'}","{","[]","   ","{\"x\":1,\"x\":2}","{\"x\":1,\"\\u0078\":2}","{\"x\":\"\\ud800\"}"))rejected(endpoint,bytes(bad),"application/json",400);
            rejected(endpoint,new byte[]{123,34,120,34,58,34,(byte)0xc3,40,34,125},"application/json",400);
            rejected(endpoint,bytes("{}"),"text/plain",415);rejected(endpoint,bytes("{}"),null,415);
            rejected(endpoint,bytes(" ".repeat(4097)),"application/json",413);
            rejected(endpoint,bytes("{\"extra\":true}"),"application/json",422);
        }
        System.out.println("PASS auth body 1 original Netty/real parseForm rejects malformed/ambiguous/invalid UTF8, unknown fields, media and raw size before any credential or Cookie mutation");
        for(String field:List.of("nameOrEmail","userPassword","mfaCode"))for(String value:List.of("null","123","true","[]","{}"))rejected("login",bytes("{\""+field+"\":"+value+"}"),"application/json",422);
        rejected("login",new byte[0],"application/json",400);
        rejected("login",bytes("{\"nameOrEmail\":\"x\",\"userPassword\":\"bad\"}"),"application/json",422);
        rejected("login",bytes("{\"nameOrEmail\":\"x\",\"userPassword\":\""+"0".repeat(32)+"\",\"mfaCode\":\"bad\"}"),"application/json",422);
        System.out.println("PASS auth body 2 login accepts only the three string fields, preserves password/MFA format checks and never authenticates rejected requests");
        var login=request("login",bytes("{\"nameOrEmail\":\" 测试 🐟 \",\"userPassword\":\""+"0".repeat(32)+"\",\"mfaCode\":\"123456\"}"),"application/json; charset=UTF-8",null);
        check(login.status==200&&login.cookies&&!login.data.has("refreshToken"));check(ApiV3Processor.loginProcessor.name.equals("测试 🐟"));check(ApiV3Processor.loginProcessor.mfa.equals("123456"));
        System.out.println("PASS auth body 3 valid login retains Unicode/string normalization, delegates once and hides refresh token from JSON");
        for(String endpoint:List.of("refresh","logout")){
            for(byte[] body:List.of(new byte[0],bytes("{}"),bytes("{}"+" ".repeat(4094)))){var r=request(endpoint,body,body.length==0?null:"application/json","http://localhost:8081");check(r.status==200&&r.cookies);}
            check(ApiV3Processor.apiV3TokenMgmtService.last.equals("existing-fixture"));
            int before=calls();var denied=request(endpoint,bytes("{}"),"application/json","http://evil.example");check(denied.status==403&&!denied.cookies&&calls()==before);
            rejected(endpoint,bytes("{\"nameOrEmail\":\"fixture\"}"),"application/json",422);
        }
        System.out.println("PASS auth body 4 true empty or bounded JSON empty object refresh/logout retain Cookie behavior and current same-origin gate");
        ApiV3Processor.apiV3TokenMgmtService.failure=true;
        check(request("refresh",bytes("{}"),"application/json","http://localhost:8081").status==401);
        check(request("logout",new byte[0],null,"http://localhost:8081").status==200);
        System.out.println("PASS auth body 5 existing refresh failure and idempotent logout service-error behavior remain after valid input");
    }
}
