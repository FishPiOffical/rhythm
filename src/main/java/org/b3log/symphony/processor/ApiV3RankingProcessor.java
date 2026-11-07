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
import org.b3log.symphony.service.ApiV3RankingQueryService;
import org.b3log.symphony.util.ApiV3RankingDto;
import org.b3log.symphony.util.ApiV3Requests;

@Singleton
public class ApiV3RankingProcessor {
    @Inject private ApiV3RankingQueryService rankings;
    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3RankingProcessor processor = beans.getReference(ApiV3RankingProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final AnonymousViewCheckMidware anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/rankings/{type}", processor::ranking, auth::optionalContent, anonymous::handle);
    }
    public void ranking(final RequestContext context) {
        ApiV3Requests.read(context, () -> {
            final String type = ApiV3Requests.text(context.pathVar("type"), 16, true);
            return rankings.ranking(type, ApiV3RankingDto.subType(type, context.param("subType")), ApiV3Requests.page(context), ApiV3Requests.size(context));
        });
    }
}
