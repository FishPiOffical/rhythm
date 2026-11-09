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
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.json.JSONObject;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ApiV3MarkdownTest {
    static void check(boolean v) {
        if (!v) throw new AssertionError();
    }

    static RequestContext request(String token, String mime, byte[] body) throws Exception {
        var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.POST,
                "/api/v3/markdown/preview?apiKey=old", Unpooled.wrappedBuffer(body));
        try {
            if (token != null) raw.headers().set("Authorization", token);
            if (mime != null) raw.headers().set("Content-Type", mime);
            raw.headers().set("Cookie", "legacy=valid");
            var req = new Request(null, raw) {
                public String getRemoteAddr() {
                    return "192.0.2.62";
                }
            };
            req.setParams(new HashMap<>(Map.of("apiKey", "old")));
            var c = new RequestContext(req, new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
            c.addHandler(new org.b3log.latke.http.handler.RouteHandler());
            c.addHandler(new ApiV3MarkdownProcessor()::preview);
            c.handle();
            return c;
        } finally {
            raw.release();
        }
    }
    static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static RequestContext json(String body)throws Exception{return request("Bearer good","application/json; charset=UTF-8",bytes(body));}
    static int status(RequestContext c){return (Integer)c.attr("status");}
    public static void main(String[] args)throws Exception{
        var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        props.setProperty("serverScheme", "https");
        props.setProperty("serverHost", "fishpi.cn");
        props.setProperty("serverPort", "443");
        props.setProperty("runtimeMode", "PRODUCTION");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);
        Latkes.setServerScheme("https");
        Latkes.setServerHost("fishpi.cn");
        Latkes.setServerPort("443");
        Latkes.setContextPath("");
        var auth = new ApiV3AuthMidware();
        var f = ApiV3AuthMidware.class.getDeclaredField("apiV3TokenMgmtService");
        f.setAccessible(true);
        f.set(auth, new ApiV3TokenMgmtService() {
            public JSONObject authenticate(String token) {
                return Set.of("good", "wrong").contains(token)
                        ? new JSONObject().put("user", new JSONObject().put("oId", "1"))
                                .put("scope", token.equals("good") ? "content.read" : "profile.read")
                        : null;
            }
        });
        ApiV3MarkdownProcessor.registerRoutes(new ApiV3MarkdownProcessor(), auth);
        Dispatcher.mapping();
        for(String token:Arrays.asList(null,"Basic good","Bearer expired","Bearer revoked"))check(status(request(token,"application/json",bytes("{}")))==401);
        check(status(request("Bearer wrong","application/json",bytes("{}")))==403);
        System.out.println("PASS preview 1 actual registered route, required content.read, no Cookie/apiKey fallback");
        for(String mime:Arrays.asList(null,"text/plain","application/x-www-form-urlencoded"))check(status(request("Bearer good",mime,bytes("{\"content\":\"x\"}")))==415);
        for(String input:List.of("{}","{\"content\":null}","{\"content\":1}","{\"content\":\"x\",\"userId\":\"2\"}","{\"content\":\"\\u0000\"}","{\"content\":\"\\u007f\"}"))check(status(json(input))==422);
        for(String input:List.of("[]","{\"content\":\"a\",\"content\":\"b\"}","{\"content\":\"\\ud800\"}","{\"content\":\"x\",}"))check(status(json(input))==400);
        check(status(request("Bearer good","application/json",new byte[]{'{','"','c','o','n','t','e','n','t','"',':','"',(byte)0xc3,0x28,'"','}'}))==400);
        check(status(json(new JSONObject().put("content","x".repeat(1024001)).toString()))==422);
        check(status(request("Bearer good","application/json",new byte[8*1024*1024+1]))==413);
        System.out.println("PASS preview 2 MIME, strict raw UTF8/JSON, unknown/typed fields, controls and length boundaries");
        var empty=json("{\"content\":\"\"}");check(status(empty)==200);check(((JSONObject)empty.attr("data")).getString("html").isEmpty());
        var c=json(new JSONObject().put("content","# 中文😀\n\n**bold**\n\n| a | b |\n| --- | --- |\n| c | d |\n\n<script>alert(1)</script><img src=x onerror=alert(2)><a href=javascript:alert(3)>bad</a><iframe src=https://evil.invalid></iframe>").toString());check(status(c)==200);
        var data = (JSONObject) c.attr("data");
        check(data.keySet().equals(Set.of("html")));
        var html = data.getString("html");
        check(html.contains("<h1>中文😀</h1>") && html.contains("<strong>bold</strong>") && html.contains("<table>"));
        check(!html.contains("<script") && !html.contains("onerror") && !html.contains("javascript:") && !html.contains("iframe"));
        check(c.getResponse().getHeader("Cache-Control").equals("no-store"));check(c.getResponse().getHeader("Vary").equals("Authorization"));check(c.getResponse().getHeader("Set-Cookie")==null);
        System.out.println("PASS preview 3 actual private Flexmark/Jsoup Markdown and XSS cleaning, empty text, no-store whitelist response");
        var counts = ApiV3MarkdownProcessor.class.getDeclaredField("COUNTS");
        counts.setAccessible(true);
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) counts.get(null)).invalidateAll();
        for(int i=0;i<120;i++)check(status(json("{\"content\":\"x\"}"))==200);
        var limited=json("{\"content\":\"x\"}");check(status(limited)==429);check("60".equals(limited.getResponse().getHeader("Retry-After")));
        // 清空请求计数，再占满渲染许可，单独验证并发忙碌和释放后的恢复。
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) counts.get(null)).invalidateAll();
        var sem = ApiV3MarkdownProcessor.class.getDeclaredField("RENDERS");
        sem.setAccessible(true);
        var permits = (java.util.concurrent.Semaphore) sem.get(null);
        permits.acquire(4);
        try {
            check(status(json("{\"content\":\"x\"}")) == 503);
        } finally {
            permits.release(4);
        }
        check(status(json("{\"content\":\"x\"}")) == 200);
        System.out.println("PASS preview 4 independent bounded rate and concurrent-render guard, recovery after busy");
    }
}
