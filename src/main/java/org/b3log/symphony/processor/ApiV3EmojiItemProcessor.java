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

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3EmojiItemService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3EmojiItemProcessor {
    @Inject private ApiV3EmojiItemService service;
    public static void register() {
        final var beans=BeanManager.getInstance();final var p=beans.getReference(ApiV3EmojiItemProcessor.class);
        final var auth=beans.getReference(ApiV3AuthMidware.class);final var writes=beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.post("/api/v3/me/settings/emojis", p::upload, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/me/settings/emoji-groups/{groupId}/emojis", p::add, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/settings/emoji-groups/{groupId}/emojis/{itemId}", p::update, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/me/settings/emoji-groups/{groupId}/emojis/{itemId}", p::remove, auth::settingsWrite, writes::handle);
    }
    public void upload(final RequestContext c) { ApiV3Requests.respond(c,()->service.upload(actor(c),body(c,Set.of("url","name","sort"),false),ApiV3WriteInputs.idempotencyKey(c))); }
    public void add(final RequestContext c) { ApiV3Requests.respond(c,()->service.add(actor(c),ApiV3Requests.id(c,"groupId"),body(c,Set.of("url","emojiId","name","sort"),false),ApiV3WriteInputs.idempotencyKey(c))); }
    public void update(final RequestContext c) { ApiV3Requests.respond(c,()->service.update(actor(c),ApiV3Requests.id(c,"groupId"),ApiV3Requests.id(c,"itemId"),body(c,Set.of("name","sort"),false),ApiV3WriteInputs.idempotencyKey(c))); }
    public void remove(final RequestContext c) { ApiV3Requests.respond(c,()->{body(c,Set.of(),true);return service.remove(actor(c),ApiV3Requests.id(c,"groupId"),ApiV3Requests.id(c,"itemId"),ApiV3WriteInputs.idempotencyKey(c));}); }
    private static String actor(RequestContext c){return ApiV3Requests.viewer(c).getString(Keys.OBJECT_ID);}
    private static JSONObject body(RequestContext c,Set<String> allowed,boolean empty){final byte[] bytes=c.getRequest().getBytes();if(bytes!=null&&bytes.length>16384)throw new ApiV3Exception(413,"body_too_large","请求体过大");return ApiV3WriteInputs.body(c,allowed,empty);}
}
