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
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3CommunityQueryService;
import org.b3log.symphony.util.ApiV3Requests;

import java.util.Set;

@Singleton
public class ApiV3CommunityProcessor {
    @Inject
    private ApiV3CommunityQueryService community;

    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3CommunityProcessor processor = beans.getReference(ApiV3CommunityProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final AnonymousViewCheckMidware anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/fish-games", processor::games, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/fish-games/{gameId}/comments", processor::gameComments, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/fish-games/{gameId}", processor::game, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/milestones", processor::milestones, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/milestones/{milestoneId}", processor::milestone, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/statistics", processor::statistics, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/cities/{cityName}/users", processor::cityUsers, auth::optionalProfile, anonymous::handle);
        Dispatcher.get("/api/v3/cities/{cityName}/articles", processor::cityArticles, auth::optionalContent, anonymous::handle);
    }

    public void games(final RequestContext context) {
        ApiV3Requests.read(context, () -> community.games(ApiV3Requests.viewer(context), ApiV3Requests.page(context), ApiV3Requests.size(context),
                ApiV3Requests.choice(context, "sort", "votes", Set.of("likes", "votes"))));
    }

    public void game(final RequestContext context) {
        ApiV3Requests.read(context, () -> community.game(ApiV3Requests.id(context, "gameId"), ApiV3Requests.viewer(context)));
    }

    public void gameComments(final RequestContext context) {
        ApiV3Requests.read(context, () -> community.gameComments(ApiV3Requests.id(context, "gameId"), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void milestones(final RequestContext context) {
        ApiV3Requests.read(context, () -> community.milestones(ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void milestone(final RequestContext context) {
        ApiV3Requests.read(context, () -> community.milestone(ApiV3Requests.id(context, "milestoneId")));
    }

    public void statistics(final RequestContext context) {
        ApiV3Requests.read(context, community::statistics);
    }

    public void cityUsers(final RequestContext context) {
        ApiV3Requests.read(context, () -> community.cityUsers(city(context), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void cityArticles(final RequestContext context) {
        ApiV3Requests.read(context, () -> community.cityArticles(city(context), ApiV3Requests.viewer(context), ApiV3Requests.page(context), ApiV3Requests.size(context),
                ApiV3Requests.choice(context, "sort", "latest", Set.of("latest", "active", "hot", "comments"))));
    }

    private static String city(final RequestContext context) {
        return ApiV3Requests.text(context.pathVar("cityName"), 64, true);
    }
}
