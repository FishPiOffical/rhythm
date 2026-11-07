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

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3QqSettingsService;
import org.b3log.symphony.service.ApiV3ProfileTagsService;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3OAuthTransportLogFilter;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3ProfileSettingsProcessor {
    @Inject private ApiV3QqSettingsService qq;
    @Inject private ApiV3ProfileTagsService tags;
    public static void register() {
        ApiV3OAuthTransportLogFilter.requireInstalled();
        final var beans = BeanManager.getInstance();
        final var p = beans.getReference(ApiV3ProfileSettingsProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var writes = beans.getReference(ApiV3WriteMidware.class);
        registerRoutes(p, auth, writes);
    }
    public static void registerRoutes(ApiV3ProfileSettingsProcessor p, ApiV3AuthMidware auth, ApiV3WriteMidware writes) {
        Dispatcher.get("/api/v3/me/settings/qq", p::qq, auth::profileRead);
        Dispatcher.put("/api/v3/me/settings/qq", p::updateQq, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/me/settings/tags", p::tags, auth::profileRead);
        Dispatcher.put("/api/v3/me/settings/tags", p::updateTags, auth::settingsWrite, writes::handle);
    }
    public void qq(RequestContext context) { ApiV3Requests.read(context, () -> qq.read(identity(context))); }
    public void updateQq(RequestContext context) { ApiV3Requests.respond(context, () -> {
        final byte[] bytes = context.getRequest().getBytes();
        if (bytes != null && bytes.length > 4096) throw new ApiV3Exception(413, "body_too_large", "请求体过大");
        return qq.update(identity(context), ApiV3WriteInputs.body(context, Set.of("qq"), false), ApiV3WriteInputs.idempotencyKey(context));
    }); }
    public void tags(RequestContext context) { ApiV3Requests.read(context, () -> tags.read(ApiV3Requests.viewer(context).getString("oId"))); }
    public void updateTags(RequestContext context) { ApiV3Requests.respond(context, () -> {
        final byte[] bytes = context.getRequest().getBytes();
        if (bytes != null && bytes.length > 4096) throw new ApiV3Exception(413, "body_too_large", "请求体过大");
        return tags.update(ApiV3Requests.viewer(context).getString("oId"), ApiV3WriteInputs.body(context, Set.of("tags"), false), ApiV3WriteInputs.idempotencyKey(context));
    }); }
    private static JSONObject identity(RequestContext context) { return (JSONObject) context.attr(ApiV3Processor.IDENTITY); }
}
