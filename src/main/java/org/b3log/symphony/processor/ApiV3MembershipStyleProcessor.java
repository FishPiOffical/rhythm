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

import org.b3log.latke.http.*;
import org.b3log.latke.ioc.*;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3MembershipStyleQueryService;
import org.b3log.symphony.util.*;
import java.util.Set;

@Singleton
public class ApiV3MembershipStyleProcessor {
    @Inject private ApiV3MembershipStyleQueryService query;
    public static void register(){final var b=BeanManager.getInstance();registerRoutes(b.getReference(ApiV3MembershipStyleProcessor.class),b.getReference(ApiV3AuthMidware.class));}
    public static void registerRoutes(ApiV3MembershipStyleProcessor p,ApiV3AuthMidware auth){Dispatcher.get("/api/v3/membership/styles",p::styles,auth::profileRead);}
    public void styles(RequestContext context){ApiV3Requests.read(context,()->{
        final String raw=context.getRequest().getQueryString();
        if(raw!=null&&raw.length()>128)throw ApiV3Requests.invalid();
        final String version;
        try{version=raw==null||raw.isEmpty()?null:ApiV3OAuthForms.parse(raw,Set.of("version")).get("version");}
        catch(ApiV3Exception error){throw ApiV3Requests.invalid();}
        return query.styles(ApiV3Requests.viewer(context).getString("oId"),ApiV3MembershipStyleQueryService.version(version));
    });}
}
