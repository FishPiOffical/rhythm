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

import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.ApiV3ClientRepository;
import org.b3log.symphony.repository.ApiV3ClientRedirectRepository;
import org.b3log.symphony.util.*;

/**
 * 仅允许当前回调来源的浏览器请求跨源读取 OAuth 令牌和撤销响应，且不启用跨源凭据。
 */
@Service
public class ApiV3OAuthCorsService {
    @Inject
    private ApiV3ClientRepository clients;
    @Inject
    private ApiV3ClientRedirectRepository redirects;

    public boolean allowed(String client, String origin) throws Exception {
        if (origin == null) return true;
        if (client == null || client.isBlank() || client.length() > 64 || origin.length() > 4096) return false;
        final var registration = clients.getByClientId(client);
        if (registration == null || !client.equals(registration.optString(ApiV3Client.CLIENT_ID))
                || registration.optInt(ApiV3Client.STATUS, -1) != ApiV3Client.STATUS_ACTIVE || ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(client))
            return false;
        final boolean development = Latkes.RuntimeMode.DEVELOPMENT == Latkes.getRuntimeMode();
        for (var row : redirects.select("SELECT clientId,redirectUri FROM `" + redirects.getName() + "` WHERE clientId=? AND status=?", client, ApiV3ClientRedirect.ACTIVE)) {
            final String uri = row.optString(ApiV3ClientRedirect.REDIRECT_URI);
            if (client.equals(row.optString(ApiV3ClientRedirect.CLIENT_ID)) && ApiV3OAuthRedirects.valid(uri, development)
                    && ApiV3ConsentSecurity.sameOrigin(uri, origin, null, null)) return true;
        }
        return false;
    }
}
