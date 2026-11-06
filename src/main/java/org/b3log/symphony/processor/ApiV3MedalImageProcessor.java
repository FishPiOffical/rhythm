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
import org.b3log.latke.http.*;
import org.b3log.latke.ioc.*;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3MedalImageService;
import org.b3log.symphony.util.*;
import java.util.Set;

@Singleton
public class ApiV3MedalImageProcessor {
    @Inject private ApiV3MedalImageService images;
    public static void register(){var beans=BeanManager.getInstance();var p=beans.getReference(ApiV3MedalImageProcessor.class);var auth=beans.getReference(ApiV3AuthMidware.class);
        Dispatcher.get("/api/v3/me/medals/image",p::mine,auth::profileRead);
        Dispatcher.get("/api/v3/users/{userName}/medals/image",p::displayed,auth::profileRead);
    }
    public void mine(RequestContext c){send(c,"");}
    public void displayed(RequestContext c){send(c,c.pathVar("userName"));}
    private void send(RequestContext c,String name){
        var response=c.getResponse();response.setHeader("Cache-Control","no-store");response.setHeader("Vary","Authorization");
        response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Content-Security-Policy","sandbox; default-src 'none'; img-src data:; style-src 'unsafe-inline'");
        try{String size=ApiV3Requests.choice(c,"size","full",Set.of("full","mini"));
            byte[] svg=images.image(ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID),name,c.param("medalId"),"mini".equals(size));
            response.setContentType("image/svg+xml; charset=UTF-8");response.setHeader("Content-Disposition","inline; filename=\"medal.svg\"");response.sendBytes(svg);
        }catch(ApiV3Exception e){ApiV3Processor.error(c,e.status,e.code,e.getMessage());}
        catch(Exception e){ApiV3Processor.error(c,503,"temporarily_unavailable","勋章图片暂不可用");}
    }
}
