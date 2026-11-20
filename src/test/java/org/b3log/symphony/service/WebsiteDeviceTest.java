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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.b3log.latke.http.Cookie;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.symphony.util.ApiV3RefreshCookies;
import org.b3log.symphony.util.WebsiteDevice;

import java.util.HashSet;
import java.util.Set;

public class WebsiteDeviceTest {
    private static RequestContext context(String ua, Cookie cookie) {
        var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/v3/auth/login");
        raw.headers().set("User-Agent", ua);
        var request = new Request(null, raw);
        request.setCookies(cookie == null ? new HashSet<>() : new HashSet<>(Set.of(cookie)));
        var response = new Response(null, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        return new RequestContext(request, response);
    }

    private static void check(boolean valid) {
        if (!valid) throw new AssertionError("网页登录设备校验失败");
    }

    public static void main(String[] args) throws Exception {
        var properties = new java.util.Properties();
        properties.setProperty("serverScheme", "http");
        for (String name : Set.of("latkeProps", "localProps")) {
            var field = org.b3log.latke.Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, properties);
        }
        String hint = java.util.UUID.randomUUID().toString();
        check(WebsiteDevice.validHint(hint) && !WebsiteDevice.validHint("系统设备名称") && !WebsiteDevice.validHint(null));
        var first = context("Mozilla iPhone Safari/605", null);
        var original = WebsiteDevice.from(first, hint);
        var separate = WebsiteDevice.from(context("Mozilla iPhone Safari/605", null), hint);
        check("Safari · iPhone".equals(original.label()));
        check(!original.id("123").equals(separate.id("123")));
        check(!original.id("123").equals(original.id("124")));
        original.attach(first.getResponse(), "123", original.id("123"));
        var proof = first.getResponse().getCookies().iterator().next();
        check(proof.isHttpOnly() && "/".equals(proof.getPath()));
        check(original.id("123").equals(WebsiteDevice.from(context("Mozilla", proof), null).id("123")));
        var tampered = new Cookie(proof.getName(), proof.getValue().charAt(0) == 'a'
                ? "b" + proof.getValue().substring(1) : "a" + proof.getValue().substring(1));
        check(!original.id("123").equals(WebsiteDevice.from(context("Mozilla", tampered), hint).id("123")));
        var recovery = context("Mozilla", null);
        var fresh = WebsiteDevice.from(recovery, null);
        fresh.attach(recovery.getResponse(), "123", original.id("123"));
        var restored = WebsiteDevice.from(context("Mozilla", recovery.getResponse().getCookies().iterator().next()), hint);
        check(original.id("123").equals(restored.id("123")) && fresh.id("124").equals(restored.id("124")));
        ApiV3RefreshCookies.discardFrameworkEcho(first);
        check(first.getResponse().getCookies().isEmpty());
        System.out.println("PASS 设备提示、浏览器隔离、账号隔离、签名 Cookie、属性与回写过滤");
    }
}
