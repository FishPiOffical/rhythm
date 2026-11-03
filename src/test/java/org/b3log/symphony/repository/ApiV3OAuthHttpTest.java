package org.b3log.symphony.repository;

import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.symphony.processor.ApiV3OAuthProcessor;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.ApiV3AuthorizationCodeTest.*;

/** Actual controller/forms/CORS/consent/code/token core. Cookie loading/rendering and global captcha remain explicit adapters. */
public class ApiV3OAuthHttpTest {
    static class Controller extends ApiV3OAuthProcessor {
        JSONObject user=new JSONObject().put("oId","1").put("userName","Actual Cookie User");Map<String,Object> model;
        @Override protected JSONObject cookieUser(RequestContext c){return user;}
        @Override protected String csrf(RequestContext c){return "session-csrf";}
        @Override protected String site(){return "https://fishpi.cn";}
        @Override protected void render(RequestContext c,Map<String,Object> values){model=Map.copyOf(values);c.attr("html",true);}
    }
    static class Http extends ApiV3AuthorizationRequestTest.Consent {
        final Controller controller=new Controller();
        Http()throws Exception{
            var cors=new ApiV3OAuthCorsService();inject(cors,ApiV3OAuthCorsService.class,"clients",clients);inject(cors,ApiV3OAuthCorsService.class,"redirects",redirects);
            for(var e:Map.<String,Object>of("requests",consent,"codes",grants,"tokens",service,"cors",cors).entrySet())inject(controller,ApiV3OAuthProcessor.class,e.getKey(),e.getValue());
        }
        JSONObject call(Consumer<RequestContext> handler,String query,String body,Map<String,String> headers)throws Exception{
            var req=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,body==null?io.netty.handler.codec.http.HttpMethod.GET:io.netty.handler.codec.http.HttpMethod.POST,"/api/v3/oauth/test"+(query==null?"":"?"+query));
            var response=new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK);
            try{
                headers.forEach(req.headers()::set);var request=new Request(null,req);if(body!=null)request.setBytes(body.getBytes(StandardCharsets.UTF_8));
                var c=new RequestContext(request,new Response(null,response));c.attr("user",new JSONObject().put("oId","2"));handler.accept(c);
                var out=new JSONObject().put("status",response.status().code());
                if(c.getRenderer() instanceof JsonRenderer renderer)out.put("json",renderer.getJSONObject());
                out.put("policy",response.headers().get("Content-Security-Policy"));
                if(response.headers().contains("Location"))out.put("location",response.headers().get("Location"));
                if(response.headers().contains("Access-Control-Allow-Origin"))out.put("cors",response.headers().get("Access-Control-Allow-Origin"));
                Assert.assertEquals(response.headers().get("Cache-Control"),"no-store");Assert.assertEquals(response.headers().get("Referrer-Policy"),Boolean.TRUE.equals(c.attr("html"))?"same-origin":"no-referrer");Assert.assertEquals(response.headers().get("X-Frame-Options"),"DENY");Assert.assertNotNull(response.headers().get("X-Request-Id"));Assert.assertFalse(response.headers().contains("Access-Control-Allow-Credentials"));return out;
            }finally{req.release();}
        }
    }
    static String form(Map<String,String> values){var result=new ArrayList<String>();values.forEach((k,v)->result.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));return String.join("&",result);}
    static final Map<String,String> FORM=Map.of("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
    static final Map<String,String> CONSENT=Map.of("Content-Type","application/x-www-form-urlencoded","Origin","https://fishpi.cn","Sec-Fetch-Site","same-origin");
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);Latkes.setServerScheme("https");Latkes.setServerHost("fishpi.cn");Latkes.setServerPort("443");Latkes.setStaticServerScheme("https");Latkes.setStaticServerHost("fishpi.cn");Latkes.setStaticServerPort("443");Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);Latkes.setStaticResourceVersion("fixture");Latkes.setContextPath("");Latkes.setStaticPath("");
        var policyMethod=ApiV3OAuthProcessor.class.getDeclaredMethod("consentPolicy",String.class);policyMethod.setAccessible(true);
        var policyController=new Controller();
        Assert.assertTrue(((String)policyMethod.invoke(policyController,"https://client.example/callback?fixed=a%2Bb")).contains("form-action 'self' https://client.example;"));
        Assert.assertTrue(((String)policyMethod.invoke(policyController,"com.example.app:/oauth/callback")).contains("form-action 'self' com.example.app:;"));
        Latkes.setRuntimeMode(Latkes.RuntimeMode.DEVELOPMENT);
        try{
            Assert.assertTrue(((String)policyMethod.invoke(policyController,"http://127.0.0.1:18769/oauth/callback?fixed=A%2BB")).contains("form-action 'self' http://127.0.0.1:18769;"));
            Assert.assertTrue(((String)policyMethod.invoke(policyController,"http://[::1]:18769/oauth/callback")).contains("form-action 'self' http://[::1]:18769;"));
            for(String invalid:List.of("javascript:alert(1)","https://client.example/; form-action *", "https://user@client.example/cb","https://client.example/cb#fragment")){
                try{policyMethod.invoke(policyController,invalid);Assert.fail("unsafe callback policy");}catch(java.lang.reflect.InvocationTargetException expected){Assert.assertTrue(expected.getCause() instanceof ApiV3Exception);}
            }
        }finally{Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);}
        System.out.println("PASS consent document policy includes only its validated callback origin or native scheme, preserves loopback ports/IPv6 and rejects header/directive injection without relaxing server exact callback matching");
        final String authorize=form(Map.of("response_type","code","client_id",CLIENT,"redirect_uri",URI,"scope","content.read content.write","state","bound state +😀","code_challenge",CHALLENGE,"code_challenge_method","S256"));
        try(var f=new Http()){
            Assert.assertEquals(f.call(f.controller::authorize,authorize,"{\"apiKey\":\"forged\"}",Map.of("Content-Type","application/json")).getInt("status"),400);Assert.assertTrue(query(f.reader,"SELECT * FROM auth_requests").isEmpty());
            var consentPage=f.call(f.controller::authorize,authorize,null,Map.of());Assert.assertEquals(consentPage.getInt("status"),200);Assert.assertTrue(consentPage.getString("policy").contains("form-action 'self' https://client.example;"));Assert.assertFalse(consentPage.getString("policy").contains("*"));Assert.assertEquals(f.controller.model.get("oauthUser"),"Actual Cookie User");Assert.assertFalse(f.controller.model.containsKey("user"));Assert.assertEquals(query(f.reader,"SELECT userId FROM auth_requests").getFirst().getString("userId"),"1");
            String confirmation=form(Map.of("request",(String)f.controller.model.get("oauthRequest"),"proof",(String)f.controller.model.get("oauthProof"),"csrfToken","session-csrf","decision","approve"));
            var decision=f.call(f.controller::consent,null,confirmation,CONSENT);Assert.assertEquals(decision.getInt("status"),303);var params=ApiV3AuthorizationRequestTest.params(decision.getString("location"));Assert.assertEquals(params.get("state"),"bound state +😀");
            var exchanged=f.call(f.controller::token,null,form(Map.of("grant_type","authorization_code","client_id",CLIENT,"redirect_uri",URI,"code",params.get("code"),"code_verifier",VERIFIER)),FORM);Assert.assertEquals(exchanged.getInt("status"),200);var credentials=exchanged.getJSONObject("json");Assert.assertEquals(credentials.keySet(),Set.of("access_token","token_type","expires_in","refresh_token","refresh_expires_in","scope"));Assert.assertEquals(credentials.getString("token_type"),"Bearer");Assert.assertFalse(credentials.has("code"));Assert.assertNotNull(f.service.authenticate(credentials.getString("access_token")));
            var refreshed=f.call(f.controller::token,null,form(Map.of("grant_type","refresh_token","client_id",CLIENT,"refresh_token",credentials.getString("refresh_token"),"scope","content.read")),FORM);Assert.assertEquals(refreshed.getInt("status"),200);var rotated=refreshed.getJSONObject("json");Assert.assertEquals(rotated.getString("scope"),"content.read");
            var revoked=f.call(f.controller::revoke,null,form(Map.of("client_id",CLIENT,"token",rotated.getString("access_token"),"token_type_hint","access_token")),FORM);Assert.assertEquals(revoked.getInt("status"),200);Assert.assertEquals(revoked.getJSONObject("json").length(),0);Assert.assertNull(f.service.authenticate(rotated.getString("access_token")));Assert.assertEquals(f.call(f.controller::revoke,null,form(Map.of("client_id",CLIENT,"token","unknown")),FORM).getInt("status"),200);
        }
        System.out.println("PASS actual HTTP controller completes Cookie consent, bound state callback, S256 exchange, standards-shaped token response, narrowed refresh and client-bound access-token family revocation without page/apiKey identity fallback");
        try(var f=new Http()){
            f.controller.user=null;var login=f.call(f.controller::authorize,authorize,null,Map.of());Assert.assertEquals(login.getInt("status"),401);Assert.assertEquals(f.controller.model.get("oauthLoggedIn"),false);Assert.assertTrue(((String)f.controller.model.get("oauthLogin")).startsWith("https://fishpi.cn/login?goto="));Assert.assertTrue(query(f.reader,"SELECT * FROM auth_requests").isEmpty());
            Assert.assertEquals(f.call(f.controller::consent,null,"decision=approve",CONSENT).getInt("status"),401);
        }
        try(var f=new Http()){
            f.call(f.controller::authorize,authorize,null,Map.of());String confirmation=form(Map.of("request",(String)f.controller.model.get("oauthRequest"),"proof",(String)f.controller.model.get("oauthProof"),"csrfToken","session-csrf","decision","approve"));String before=f.every();
            for(String origin:List.of("https://fishpi.cn.evil","http://fishpi.cn","https://fishpi.cn:444","null"))Assert.assertEquals(f.call(f.controller::consent,null,confirmation,Map.of("Content-Type","application/x-www-form-urlencoded","Origin",origin)).getInt("status"),403);
            Assert.assertEquals(f.call(f.controller::consent,null,confirmation.replace("session-csrf","wrong"),CONSENT).getInt("status"),403);Assert.assertEquals(f.every(),before);
            Assert.assertEquals(f.call(f.controller::consent,"decision=approve",confirmation,CONSENT).getInt("status"),400);
        }
        System.out.println("PASS unsigned browser users receive a login continuation without creating a request, and exact source/session-CSRF checks reject before any approval while ignoring forged request user attrs");
        try(var f=new Http()){
            var code=f.issue("content.read");String request=form(Map.of("grant_type","authorization_code","client_id",CLIENT,"redirect_uri",URI,"code",code.code(),"code_verifier",VERIFIER));
            Assert.assertEquals(f.call(f.controller::token,null,request,Map.of("Content-Type","application/json")).getInt("status"),415);
            Assert.assertEquals(f.call(f.controller::token,"code=query",request,FORM).getInt("status"),400);
            Assert.assertEquals(f.call(f.controller::token,null,request+"&client_id=other",FORM).getInt("status"),400);
            Assert.assertEquals(f.call(f.controller::token,null,request+"&apiKey=legacy",FORM).getInt("status"),400);
            Assert.assertEquals(f.call(f.controller::token,null,request,Map.of("Content-Type","application/x-www-form-urlencoded","Authorization","Bearer legacy")).getInt("status"),401);
            Assert.assertEquals(f.call(f.controller::token,null,request,Map.of("Content-Type","application/x-www-form-urlencoded","Origin","https://client.example.evil")).getInt("status"),403);Assert.assertTrue(query(f.reader,"SELECT * FROM tokens").isEmpty());
            var allowed=f.call(f.controller::token,null,request,Map.of("Content-Type","application/x-www-form-urlencoded","Origin","https://client.example"));Assert.assertEquals(allowed.getInt("status"),200);Assert.assertEquals(allowed.getString("cors"),"https://client.example");
            var repeat=f.call(f.controller::token,null,request,FORM);Assert.assertEquals(repeat.getInt("status"),400);Assert.assertEquals(repeat.getJSONObject("json").getString("error"),"invalid_grant");
        }
        System.out.println("PASS protocol media/query/duplicate/unknown-field/header-identity boundaries reject before exchange, browser CORS is exact registered origin without credentials, and valid code replay has an unwrapped invalid_grant response");
        try(var f=new Http()){
            var pair=f.grant(CLIENT,"content.read");String raw=form(Map.of("grant_type","refresh_token","client_id",CLIENT,"refresh_token",pair.getString("refreshToken"),"scope","content.write"));
            var bad=f.call(f.controller::token,null,raw,FORM);Assert.assertEquals(bad.getInt("status"),400);Assert.assertEquals(bad.getJSONObject("json").getString("error"),"invalid_scope");Assert.assertNotNull(f.service.authenticate(pair.getString("accessToken")));Assert.assertTrue(f.service.refreshForClient(CLIENT,pair.getString("refreshToken")).has("accessToken"));
        }
        try(var f=new Http()){
            var pair=f.grant(CLIENT,"content.read");exec(f.writer,"UPDATE clients SET scope='' WHERE clientId=?",CLIENT);String before=f.all();
            var failed=f.call(f.controller::token,null,form(Map.of("grant_type","refresh_token","client_id",CLIENT,"refresh_token",pair.getString("refreshToken"))),FORM);Assert.assertEquals(failed.getInt("status"),400);Assert.assertEquals(failed.getJSONObject("json").getString("error"),"invalid_scope");Assert.assertEquals(f.all(),before);
        }
        try(var f=new Http()){
            var code=f.issue("content.read");f.faults.add("commit");var failed=f.call(f.controller::token,null,form(Map.of("grant_type","authorization_code","client_id",CLIENT,"redirect_uri",URI,"code",code.code(),"code_verifier",VERIFIER)),FORM);Assert.assertEquals(failed.getInt("status"),503);Assert.assertTrue(query(f.reader,"SELECT * FROM tokens").isEmpty());
        }
        for(String bad:List.of("a=1&a=2","a=%C0%AF","a=%ED%A0%80","a=%ZZ","a=%","a=%00","a=1&","unknown=1"))try{ApiV3OAuthForms.parse(bad,Set.of("a"));Assert.fail(bad);}catch(ApiV3Exception expected){}
        Assert.assertEquals(ApiV3OAuthForms.parse("a=state+%2B+%F0%9F%98%80",Set.of("a")),Map.of("a","state + 😀"));
        System.out.println("PASS denied refresh scope rolls back rotation so the original credential remains usable, strict commit errors report temporary failure without token halves, and malformed/overlong UTF-8 form encodings fail closed");
    }
}
