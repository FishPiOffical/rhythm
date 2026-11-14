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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Cookie;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.symphony.util.ApiV3RefreshCookies;
import java.util.Properties;

public class RefreshCookieEchoTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        Latkes.setLocalProps(new Properties());Latkes.setLatkeProps(new Properties());Latkes.setLatkeProperty("contextPath","");
        Latkes.setServerScheme("https");Latkes.setEnabledSession(true);
        var server=Class.forName("org.b3log.latke.http.ServerHandler");var ctor=server.getDeclaredConstructor();ctor.setAccessible(true);var handler=ctor.newInstance();
        var decode=server.getDeclaredMethod("handleCookie",Request.class,Response.class);decode.setAccessible(true);
        for(String path:new String[]{"/api/v3/auth/refresh","/api/v3/me","/api/user","/test"}){
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.POST,path);raw.headers().set("Cookie","fp_v3_refresh=synthetic-fixture; unrelated=preserve");
            try{
                var request=new Request(null,raw);var response=new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK));
                decode.invoke(handler,request,response);
                var echoed=response.getCookies().stream().filter(c->c.getName().equals(ApiV3RefreshCookies.NAME)).findFirst().orElseThrow();
                check(!echoed.isHttpOnly()&&!echoed.isSecure());check(echoed.getMaxAge()==2592000);
                var original=request.getCookies().stream().filter(c->c.getName().equals(ApiV3RefreshCookies.NAME)).findFirst().orElseThrow();
                int before=response.getCookies().size();ApiV3RefreshCookies.discardFrameworkEcho(new RequestContext(request,response));
                check(response.getCookies().size()==before-1);check(response.getCookies().stream().noneMatch(c->c.getName().equals(ApiV3RefreshCookies.NAME)));
                check(response.getCookies().stream().anyMatch(c->c.getName().equals("unrelated")&&c.getValue().equals("preserve")));
                check(request.getCookies().contains(original)&&original.getValue().equals("synthetic-fixture"));
                response.addCookie(ApiV3RefreshCookies.create("new-fixture",300));
                var issued=response.getCookies().stream().filter(c->c.getName().equals(ApiV3RefreshCookies.NAME)).findFirst().orElseThrow();
                check(issued.isHttpOnly()&&issued.isSecure()&&"Strict".equals(issued.getSameSite()));check(issued.getPath().equals("/api/v3/auth")&&issued.getMaxAge()==300);
                response.addCookie(ApiV3RefreshCookies.create("",0));
                var cleared=response.getCookies().stream().filter(c->c.getName().equals(ApiV3RefreshCookies.NAME)).findFirst().orElseThrow();
                check(cleared.getValue().isEmpty()&&cleared.getMaxAge()==0&&cleared.isHttpOnly()&&cleared.isSecure()&&"Strict".equals(cleared.getSameSite()));
            }finally{raw.release();}
        }
        System.out.println("PASS refresh cookie echo actual bundled ServerHandler reproduces unsafe prefill; v3, old API and captcha routes remove only response refresh cookie while request credential and other cookies remain");
        System.out.println("PASS refresh cookie echo explicit login/rotation and logout Cookie retain scoped path, HttpOnly, Secure, Strict and requested TTL after early hook");
    }
}
