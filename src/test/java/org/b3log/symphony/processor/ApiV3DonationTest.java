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

import java.util.*;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import io.netty.handler.codec.http.*;

/** Actual route/auth/response boundaries; token persistence is an explicit adapter. */
public class ApiV3DonationTest {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static RequestContext request(String token) throws Exception {
        var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.GET,
                "/api/v3/payments/donation-page?redirect=https://evil.invalid&apiKey=legacy&host=evil.invalid");
        try {
            raw.headers().set("Host", "evil.invalid");raw.headers().set("Cookie", "legacy=valid");
            if(token!=null)raw.headers().set("Authorization", token);
            var req=new Request(null,raw);req.setParams(new HashMap<>(Map.of("redirect","https://evil.invalid","apiKey","legacy","host","evil.invalid")));
            var c=new RequestContext(req,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));
            c.attr("user",new JSONObject().put("oId","cookie-owner"));
            c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(new ApiV3DonationProcessor()::page);c.handle();return c;
        } finally { raw.release(); }
    }
    public static void main(String[] args) throws Exception {
        for(String site:List.of("https://fishpi.cn","https://fishpi.cn/","https://fishpi.cn/rhythm")) {
            var data=ApiV3DonationPage.link(site,false);check(data.keySet().equals(Set.of("url")));
            check(data.getString("url").equals(site.replaceAll("/$","")+"/charge/point"));
            data.put("url","evil");check(!ApiV3DonationPage.link(site,false).getString("url").equals("evil"));
        }
        check(ApiV3DonationPage.link("http://localhost:8080",true).getString("url").equals("http://localhost:8080/charge/point"));
        check(ApiV3DonationPage.link("http://127.0.0.1:8080",true).getString("url").contains("127.0.0.1"));
        check(ApiV3DonationPage.link("http://[::1]:8080",true).getString("url").contains("[::1]"));
        System.out.println("PASS donation 1 fixed page path, configured context prefix, production HTTPS/development loopback and isolated response");
        for(String site:Arrays.asList(null,"","https://user:pass@fishpi.cn","https://fishpi.cn?next=evil","https://fishpi.cn/#x","javascript:alert(1)","//evil.invalid","http://fishpi.cn","https://fishpi.cn:0","https://fishpi.cn:65536","https://fishpi.cn:","https://fishpi.cn/a/../b","https://fishpi.cn//a","https://fishpi.cn\n")) {
            try {ApiV3DonationPage.link(site,true);throw new AssertionError(site);}catch(ApiV3Exception e){check(e.status==503);check(!e.getMessage().contains("pass"));}
        }
        try{ApiV3DonationPage.link("http://localhost:8080",false);throw new AssertionError();}catch(ApiV3Exception e){check(e.status==503);}
        System.out.println("PASS donation 2 invalid site configuration fails closed without credentials or input disclosure");
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");props.setProperty("serverScheme","https");props.setProperty("serverHost","fishpi.cn");props.setProperty("serverPort","443");props.setProperty("runtimeMode","PRODUCTION");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);Latkes.setServerScheme("https");Latkes.setServerHost("fishpi.cn");Latkes.setServerPort("443");Latkes.setContextPath("");
        var auth=new ApiV3AuthMidware();var field=ApiV3AuthMidware.class.getDeclaredField("apiV3TokenMgmtService");field.setAccessible(true);field.set(auth,new ApiV3TokenMgmtService(){public JSONObject authenticate(String token){if(!Set.of("first","third","wrong").contains(token))return null;return new JSONObject().put("user",new JSONObject().put("oId","bearer-owner")).put("clientId",token.equals("first")?"website_v3":"third-party").put("scope",token.equals("wrong")?"content.read":"activity.read");}});
        ApiV3DonationProcessor.registerRoutes(new ApiV3DonationProcessor(),auth);Dispatcher.mapping();
        for(String token:Arrays.asList(null,"Basic first","Bearer bad","Bearer expired","Bearer revoked"))check(request(token).attr("test.status").equals(401));
        check(request("Bearer wrong").attr("test.status").equals(403));
        for(String token:List.of("first","third")){var c=request("Bearer "+token);check(c.attr("test.status").equals(200));var data=(JSONObject)c.attr("test.data");check(data.keySet().equals(Set.of("url")));check(data.getString("url").equals("https://fishpi.cn/charge/point"));check(c.getResponse().getHeader("Set-Cookie")==null);check(c.getResponse().getHeader("Location")==null);check(c.getResponse().getHeader("Cache-Control").equals("no-store"));check(c.getResponse().getHeader("Vary").equals("Authorization"));}
        System.out.println("PASS donation 3 actual GET route and Bearer scope guard, first/third party, no Cookie/apiKey fallback, Host/query ignored, no browser Cookie issued");
    }
}
