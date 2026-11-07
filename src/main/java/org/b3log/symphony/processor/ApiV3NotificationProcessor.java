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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3NotificationMgmtService;
import org.b3log.symphony.service.ApiV3NotificationQueryService;
import org.b3log.symphony.service.NotificationMgmtService;
import org.b3log.symphony.util.ApiV3NotificationTypes;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

import java.util.Set;

@Singleton
public class ApiV3NotificationProcessor {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3NotificationProcessor.class);
    @Inject private ApiV3NotificationQueryService query;
    @Inject private ApiV3NotificationMgmtService management;
    @Inject private NotificationMgmtService notifications;

    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3NotificationProcessor processor = beans.getReference(ApiV3NotificationProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.get("/api/v3/me/notifications", processor::list, auth::notificationsRead);
        Dispatcher.get("/api/v3/me/notifications/unread-count", processor::unread, auth::notificationsRead);
        Dispatcher.get("/api/v3/me/notifications/{notificationId}", processor::get, auth::notificationsRead);
        Dispatcher.post("/api/v3/me/notifications/{notificationId}/read", processor::mark, auth::notificationsWrite, writes::handle);
        Dispatcher.post("/api/v3/me/notifications/read", processor::markCategory, auth::notificationsWrite, writes::handle);
        Dispatcher.delete("/api/v3/me/notifications/{notificationId}", processor::remove, auth::notificationsWrite, writes::handle);
        Dispatcher.delete("/api/v3/me/notifications", processor::removeCategory, auth::notificationsWrite, writes::handle);
    }

    public void list(final RequestContext context) {
        ApiV3Requests.read(context, () -> query.list(ApiV3Requests.viewer(context),
                ApiV3Requests.choice(context, "type", "all", ApiV3NotificationTypes.FILTERS),
                ApiV3Requests.choice(context, "status", "all", Set.of("all", "read", "unread")), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }
    public void unread(final RequestContext context) {
        ApiV3Requests.read(context, () -> query.unread(userId(context)));
    }
    public void get(final RequestContext context) {
        // Latke can resolve a static sibling through the dynamic node.
        if ("unread-count".equals(context.pathVar("notificationId"))) { unread(context); return; }
        ApiV3Requests.read(context, () -> query.get(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "notificationId")));
    }
    public void mark(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true);
            return refreshed(context, management.mark(userId(context), ApiV3Requests.id(context, "notificationId"), ApiV3WriteInputs.idempotencyKey(context)));
        });
    }
    public void remove(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true);
            return refreshed(context, management.remove(userId(context), ApiV3Requests.id(context, "notificationId"), ApiV3WriteInputs.idempotencyKey(context)));
        });
    }
    public void markCategory(final RequestContext context) { bulk(context, false); }
    public void removeCategory(final RequestContext context) { bulk(context, true); }
    private void bulk(final RequestContext context, final boolean remove) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, Set.of("type"), !remove);
            final String type = body.has("type") ? ApiV3WriteInputs.string(body, "type", 32, true) : (remove ? "" : "all");
            return refreshed(context, management.bulk(userId(context), type, remove, ApiV3WriteInputs.idempotencyKey(context)));
        });
    }
    private JSONObject refreshed(final RequestContext context, final JSONObject result) {
        // The database transaction has already committed. Refresh is safe to repeat on a replay.
        try { notifications.publishRefreshNotification(userId(context)); }
        catch (final RuntimeException e) { LOGGER.warn("Notification refresh could not be scheduled requestId={}", ApiV3Processor.requestId(context)); }
        return result;
    }
    private static String userId(final RequestContext context) { return ApiV3Requests.viewer(context).optString(Keys.OBJECT_ID); }
}
