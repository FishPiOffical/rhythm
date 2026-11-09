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
package org.b3log.symphony.processor.middleware;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.b3log.latke.Keys;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.processor.ApiV3Processor;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 在全站防火墙之外，对 v3 写请求按用户及 IP 计入共享额度。 */
@Singleton
public class ApiV3WriteMidware {
    private static final int USER_LIMIT = 30;
    private static final int IP_LIMIT = 120;
    private static final Cache<String, AtomicInteger> COUNTS = Caffeine.newBuilder()
            .maximumSize(100000).expireAfterWrite(1, TimeUnit.MINUTES).build();

    public void handle(final RequestContext context) {
        final JSONObject user = ApiV3Requests.viewer(context);
        if (null == user) {
            ApiV3Processor.error(context, 401, "unauthorized", "登录凭据无效");
            context.abort();
            return;
        }
        final int userCount = COUNTS.get("user:" + user.optString(Keys.OBJECT_ID), key -> new AtomicInteger()).incrementAndGet();
        final int ipCount = COUNTS.get("ip:" + Requests.getRemoteAddr(context.getRequest()), key -> new AtomicInteger()).incrementAndGet();
        if (userCount > USER_LIMIT || ipCount > IP_LIMIT) {
            context.getResponse().setHeader("Retry-After", "60");
            ApiV3Processor.error(context, 429, "too_many_requests", "请求过于频繁，请稍后再试");
            context.abort();
            return;
        }
        context.handle();
    }
}
