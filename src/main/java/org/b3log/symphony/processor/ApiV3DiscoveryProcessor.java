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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3DiscoveryQueryService;
import org.b3log.symphony.service.ApiV3ContentQueryService;
import org.b3log.symphony.service.ApiV3DiscoveryQueryService.Selection;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Discovery and public profiles. */
@Singleton
public class ApiV3DiscoveryProcessor {
    private static final Set<String> ARTICLE_SORTS = Set.of("latest", "active", "hot", "comments");
    private static final Cache<String, Boolean> SEARCH_REQUESTS = Caffeine.newBuilder().maximumSize(100000)
            .expireAfterWrite(1, TimeUnit.SECONDS).build();
    @Inject private ApiV3DiscoveryQueryService discovery;
    @Inject private ApiV3ContentQueryService content;

    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3DiscoveryProcessor processor = beans.getReference(ApiV3DiscoveryProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final AnonymousViewCheckMidware anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/search", processor::search, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/tags", processor::tags, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/tags/{tagId}/articles", processor::tagArticles, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/tags/{tagId}", processor::tag, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/domains", processor::domains, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/domains/{domainId}/tags", processor::domainTags, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/domains/{domainId}/articles", processor::domainArticles, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/domains/{domainId}", processor::domain, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/users/{userName}/comments", processor::userComments, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/users/{userName}/watching/articles", processor::watchedArticles, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/users/{userName}/watching", processor::watching, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/users/{userName}/articles", processor::userArticles, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/users/{userName}/breezemoons", processor::userBreezemoons, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/users/{userName}/following/users", processor::followingUsers, auth::optionalProfile, anonymous::handle);
        Dispatcher.get("/api/v3/users/{userName}/following/tags", processor::followingTags, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/users/{userName}/following/articles", processor::followingArticles, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/users/{userName}/followers", processor::followers, auth::optionalProfile, anonymous::handle);
        Dispatcher.get("/api/v3/users/{userName}", processor::user, auth::optionalProfile, anonymous::handle);
        Dispatcher.get("/api/v3/breezemoons", processor::breezemoons, auth::optionalContent, anonymous::handle);
    }

    public void search(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final String keyword = ApiV3Requests.text(context.param("q"), 100, true);
            final int p = ApiV3Requests.page(context);
            final int size = ApiV3Requests.size(context);
            final String sort = sort(context);
            final String type = ApiV3Requests.choice(context, "type", "all", Set.of("all", "recent", "long"));
            // This query intentionally uses bound DB predicates so index lag cannot disclose hidden content.
            final JSONObject viewer = ApiV3Requests.viewer(context);
            final String key = null == viewer ? "ip:" + Requests.getRemoteAddr(context.getRequest())
                    : "user:" + viewer.optString(Keys.OBJECT_ID);
            if (null != SEARCH_REQUESTS.asMap().putIfAbsent(key, Boolean.TRUE)) {
                throw new ApiV3Requests.ApiException(429, "too_many_requests", "请求过于频繁，请稍后再试");
            }
            return discovery.articles(viewer, p, size, sort, type, new Selection(null, null, null, keyword));
        });
    }

    public void tags(final RequestContext context) {
        ApiV3Requests.read(context, () -> discovery.tags(ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void tag(final RequestContext context) {
        ApiV3Requests.read(context, () -> discovery.tag(ApiV3Requests.id(context, "tagId")));
    }

    public void tagArticles(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final String id = ApiV3Requests.id(context, "tagId");
            discovery.tag(id);
            return articles(context, new Selection(null, id, null, null), "all");
        });
    }

    public void domains(final RequestContext context) {
        ApiV3Requests.read(context, () -> discovery.domains(ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void domain(final RequestContext context) {
        ApiV3Requests.read(context, () -> discovery.domain(ApiV3Requests.id(context, "domainId")));
    }

    public void domainTags(final RequestContext context) {
        ApiV3Requests.read(context, () -> discovery.domainTags(ApiV3Requests.id(context, "domainId"),
                ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void domainArticles(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final String id = ApiV3Requests.id(context, "domainId");
            discovery.domain(id);
            return articles(context, new Selection(null, null, id, null), "all");
        });
    }

    public void user(final RequestContext context) {
        ApiV3Requests.read(context, () -> discovery.profile(targetUser(context)));
    }

    public void userArticles(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final JSONObject user = targetUser(context);
            discovery.requireUserList(user, ApiV3Requests.viewer(context), UserExt.USER_ARTICLE_STATUS);
            return articles(context, new Selection(user.optString(Keys.OBJECT_ID), null, null, null), "recent");
        });
    }

    public void userComments(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final JSONObject user = targetUser(context), viewer = ApiV3Requests.viewer(context);
            discovery.requireUserList(user, viewer, UserExt.USER_COMMENT_STATUS);
            return content.userComments(user.optString(Keys.OBJECT_ID), viewer, ApiV3Requests.page(context), ApiV3Requests.size(context));
        });
    }

    public void watching(final RequestContext context) { watching(context, false); }
    public void watchedArticles(final RequestContext context) { watching(context, true); }
    private void watching(final RequestContext context, final boolean watched) {
        ApiV3Requests.read(context, () -> {
            final JSONObject user = targetUser(context);
            discovery.requireUserList(user, ApiV3Requests.viewer(context), UserExt.USER_WATCHING_ARTICLE_STATUS);
            final String source = watched ? "articles" : ApiV3Requests.choice(context, "source", "all", Set.of("all", "users", "tags"));
            return articles(context, Selection.watching(user.optString(Keys.OBJECT_ID), source), "all");
        });
    }

    public void userBreezemoons(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final JSONObject user = targetUser(context);
            discovery.requireUserList(user, ApiV3Requests.viewer(context), UserExt.USER_BREEZEMOON_STATUS);
            return discovery.breezemoons(user.optString(Keys.OBJECT_ID), ApiV3Requests.page(context), ApiV3Requests.size(context));
        });
    }

    public void followingUsers(final RequestContext context) {
        relations(context, "users", UserExt.USER_FOLLOWING_USER_STATUS);
    }

    public void followingTags(final RequestContext context) {
        relations(context, "tags", UserExt.USER_FOLLOWING_TAG_STATUS);
    }

    public void followers(final RequestContext context) {
        relations(context, "followers", UserExt.USER_FOLLOWER_STATUS);
    }

    public void followingArticles(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final JSONObject user = targetUser(context);
            discovery.requireUserList(user, ApiV3Requests.viewer(context), UserExt.USER_FOLLOWING_ARTICLE_STATUS);
            return articles(context, new Selection(null, null, null, null, user.optString(Keys.OBJECT_ID)), "all");
        });
    }

    private void relations(final RequestContext context, final String kind, final String privacyField) {
        ApiV3Requests.read(context, () -> {
            final JSONObject user = targetUser(context);
            discovery.requireUserList(user, ApiV3Requests.viewer(context), privacyField);
            return discovery.userRelations(user.optString(Keys.OBJECT_ID), ApiV3Requests.page(context), ApiV3Requests.size(context), kind);
        });
    }

    public void breezemoons(final RequestContext context) {
        ApiV3Requests.read(context, () -> discovery.breezemoons(null, ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    private JSONObject targetUser(final RequestContext context) throws Exception {
        final String name = ApiV3Requests.text(context.pathVar("userName"), 64, true);
        if (!name.matches("[A-Za-z0-9_-]{1,64}")) throw ApiV3Requests.invalid();
        return discovery.targetUser(name);
    }

    private JSONObject articles(final RequestContext context, final Selection selection, final String defaultType) throws Exception {
        return discovery.articles(ApiV3Requests.viewer(context), ApiV3Requests.page(context), ApiV3Requests.size(context),
                sort(context), ApiV3Requests.choice(context, "type", defaultType, Set.of("all", "recent", "long")), selection);
    }

    private static String sort(final RequestContext context) {
        return ApiV3Requests.choice(context, "sort", "latest", ARTICLE_SORTS);
    }
}
