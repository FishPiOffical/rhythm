package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.model.Follow;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3DiscoveryQueryService;
import org.b3log.symphony.service.ApiV3FollowMgmtService;
import org.b3log.symphony.service.ApiV3FollowQueryService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

import java.util.Set;

@Singleton
public class ApiV3FollowProcessor {
    @Inject private ApiV3FollowMgmtService follows;
    @Inject private ApiV3FollowQueryService query;
    @Inject private ApiV3DiscoveryQueryService discovery;
    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3FollowProcessor p = beans.getReference(ApiV3FollowProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);
        registerRoutes(p, auth, writes);
    }
    public static void registerRoutes(ApiV3FollowProcessor p, ApiV3AuthMidware auth, ApiV3WriteMidware writes) {
        Dispatcher.get("/api/v3/me/follows/users", p::users, auth::profileRead);
        Dispatcher.get("/api/v3/me/follows/users/{targetId}", p::userState, auth::profileRead);
        Dispatcher.get("/api/v3/me/follows/tags", p::tags, auth::contentRead);
        Dispatcher.get("/api/v3/me/follows/tags/{targetId}", p::tagState, auth::contentRead);
        Dispatcher.get("/api/v3/me/collections/{targetId}", p::collectionState, auth::contentRead);
        Dispatcher.get("/api/v3/me/subscriptions/{targetId}", p::subscriptionState, auth::contentRead);
        Dispatcher.get("/api/v3/me/collections", p::collections, auth::contentRead);
        Dispatcher.get("/api/v3/me/subscriptions", p::subscriptions, auth::contentRead);
        Dispatcher.put("/api/v3/me/follows/users/{targetId}", p::followUser, auth::followsWrite, writes::handle);
        Dispatcher.delete("/api/v3/me/follows/users/{targetId}", p::unfollowUser, auth::followsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/follows/tags/{targetId}", p::followTag, auth::followsWrite, writes::handle);
        Dispatcher.delete("/api/v3/me/follows/tags/{targetId}", p::unfollowTag, auth::followsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/collections/{targetId}", p::collect, auth::followsWrite, writes::handle);
        Dispatcher.delete("/api/v3/me/collections/{targetId}", p::uncollect, auth::followsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/subscriptions/{targetId}", p::subscribe, auth::followsWrite, writes::handle);
        Dispatcher.delete("/api/v3/me/subscriptions/{targetId}", p::unsubscribe, auth::followsWrite, writes::handle);
    }
    public void userState(final RequestContext c) {
        ApiV3Requests.read(c, () -> query.userState(ApiV3Requests.viewer(c), ApiV3Requests.id(c, "targetId")));
    }
    public void tagState(final RequestContext c) { ApiV3Requests.read(c, () -> query.tagState(ApiV3Requests.viewer(c), ApiV3Requests.id(c, "targetId"))); }
    public void collectionState(final RequestContext c) { articleState(c, false); }
    public void subscriptionState(final RequestContext c) { articleState(c, true); }
    private void articleState(final RequestContext c, final boolean watch) {
        ApiV3Requests.read(c, () -> query.articleState(ApiV3Requests.viewer(c), ApiV3Requests.id(c, "targetId"), watch));
    }
    public void users(final RequestContext c) { relations(c, "users"); }
    public void tags(final RequestContext c) { relations(c, "tags"); }
    private void relations(final RequestContext c, final String kind) {
        ApiV3Requests.read(c, () -> discovery.userRelations(actor(c), ApiV3Requests.page(c), ApiV3Requests.size(c), kind));
    }
    public void collections(final RequestContext c) { articles(c, false); }
    public void subscriptions(final RequestContext c) { articles(c, true); }
    private void articles(final RequestContext c, final boolean watch) {
        ApiV3Requests.read(c, () -> query.articles(ApiV3Requests.viewer(c), watch, ApiV3Requests.page(c), ApiV3Requests.size(c)));
    }
    public void followUser(final RequestContext c) { set(c, Follow.FOLLOWING_TYPE_C_USER, true); }
    public void unfollowUser(final RequestContext c) { set(c, Follow.FOLLOWING_TYPE_C_USER, false); }
    public void followTag(final RequestContext c) { set(c, Follow.FOLLOWING_TYPE_C_TAG, true); }
    public void unfollowTag(final RequestContext c) { set(c, Follow.FOLLOWING_TYPE_C_TAG, false); }
    public void collect(final RequestContext c) { set(c, Follow.FOLLOWING_TYPE_C_ARTICLE, true); }
    public void uncollect(final RequestContext c) { set(c, Follow.FOLLOWING_TYPE_C_ARTICLE, false); }
    public void subscribe(final RequestContext c) { set(c, Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH, true); }
    public void unsubscribe(final RequestContext c) { set(c, Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH, false); }
    private void set(final RequestContext c, final int type, final boolean enabled) {
        ApiV3Requests.respond(c, () -> {
            ApiV3WriteInputs.body(c, Set.of(), true);
            return follows.set(ApiV3Requests.viewer(c), ApiV3Requests.id(c, "targetId"), type, enabled, ApiV3WriteInputs.idempotencyKey(c));
        });
    }
    private static String actor(final RequestContext c) { return ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID); }
}
