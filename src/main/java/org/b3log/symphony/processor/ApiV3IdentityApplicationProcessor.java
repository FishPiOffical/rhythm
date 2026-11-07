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
import org.b3log.latke.http.ApiV3GameSaveRawBody;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3IdentityApplicationService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Locale;
import java.util.Set;

@Singleton
public class ApiV3IdentityApplicationProcessor {
    @Inject private ApiV3IdentityApplicationService applications;
    public static void register(){
        ApiV3OAuthTransportLogFilter.requireInstalled();
        final var beans=BeanManager.getInstance();registerRoutes(beans.getReference(ApiV3IdentityApplicationProcessor.class),
                beans.getReference(ApiV3AuthMidware.class),beans.getReference(ApiV3WriteMidware.class));
    }
    public static void registerRoutes(ApiV3IdentityApplicationProcessor p,ApiV3AuthMidware auth,ApiV3WriteMidware writes){
        Dispatcher.post("/api/v3/me/identity-applications/enterprise",p::create,auth::settingsWrite,writes::handle);
        Dispatcher.get("/api/v3/me/identity-applications",p::list,auth::profileRead);
        Dispatcher.get("/api/v3/me/identity-applications/{applicationId}",p::detail,auth::profileRead);
        Dispatcher.get("/api/v3/review/identity-applications",p::reviewList,auth::profileRead);
        Dispatcher.post("/api/v3/review/identity-applications/{applicationId}/material/open",p::open,auth::settingsWrite,writes::handle);
        Dispatcher.put("/api/v3/review/identity-applications/{applicationId}/decision",p::decide,auth::settingsWrite,writes::handle);
    }
    private static JSONObject identity(RequestContext c){return (JSONObject)c.attr(ApiV3Processor.IDENTITY);}
    public void create(RequestContext c){ApiV3Requests.respond(c,()->{
        final String media=c.header("Content-Type");
        final String type=media==null?"":media.split(";",2)[0].strip().toLowerCase(Locale.ROOT);
        final String key=ApiV3WriteInputs.idempotencyKey(c);
        final byte[] bytes;
        try { bytes=ApiV3GameSaveRawBody.copy(c.getRequest(),ApiV3IdentityImage.MAX_BYTES); }
        catch(ApiV3GameSaveRawBody.TooLarge error){throw new ApiV3Exception(413,"body_too_large","证明图片最多5MiB");}
        return applications.create(identity(c),bytes,type,key);
    });}
    public void list(RequestContext c){ApiV3Requests.read(c,()->applications.list(identity(c),ApiV3Requests.page(c),ApiV3Requests.size(c),false,""));}
    public void detail(RequestContext c){ApiV3Requests.read(c,()->applications.detail(identity(c),ApiV3Requests.id(c,"applicationId")));}
    public void reviewList(RequestContext c){ApiV3Requests.read(c,()->applications.list(identity(c),ApiV3Requests.page(c),ApiV3Requests.size(c),true,
            ApiV3Requests.choice(c,"status","pending",Set.of("pending","approved","rejected"))));}
    public void decide(RequestContext c){ApiV3Requests.respond(c,()->{
        final byte[] bytes=c.getRequest().getBytes();
        if(bytes!=null&&bytes.length>4096)throw new ApiV3Exception(413,"body_too_large","请求体过大");
        return applications.decide(identity(c),ApiV3Requests.id(c,"applicationId"),
                ApiV3WriteInputs.body(c,Set.of("status","note"),false),ApiV3WriteInputs.idempotencyKey(c));
    });}
    public void open(RequestContext c){
        final var response=c.getResponse();response.setHeader("Cache-Control","no-store");response.setHeader("Vary","Authorization");
        response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Content-Security-Policy","sandbox; default-src 'none'");
        try{
            ApiV3WriteInputs.body(c,Set.of(),true);
            final String id=ApiV3Requests.id(c,"applicationId");
            final var opened=applications.open(identity(c),id);
            response.setContentType(opened.mediaType());
            response.setHeader("Content-Disposition","attachment; filename=\"enterprise-proof-"+id+("image/png".equals(opened.mediaType())?".png":".jpg")+"\"");
            response.sendBytes(opened.bytes());
        }catch(ApiV3Exception error){ApiV3Processor.error(c,error.status,error.code,error.getMessage());}
        catch(Exception error){ApiV3Processor.error(c,503,"temporarily_unavailable","证明图片暂不可用");}
    }
}
