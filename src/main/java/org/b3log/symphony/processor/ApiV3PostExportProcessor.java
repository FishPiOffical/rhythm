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
import org.b3log.latke.util.Requests;
import org.b3log.symphony.processor.middleware.*;
import org.b3log.symphony.service.ApiV3PostExportService;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Set;

@Singleton
public class ApiV3PostExportProcessor {
    @Inject private ApiV3PostExportService exports;
    public static void register(){ApiV3OAuthTransportLogFilter.requireInstalled();var beans=BeanManager.getInstance();var p=beans.getReference(ApiV3PostExportProcessor.class);registerRoutes(p,beans.getReference(ApiV3AuthMidware.class),beans.getReference(ApiV3WriteMidware.class));p.exports.startCleanup();}
    public static void registerRoutes(ApiV3PostExportProcessor p,ApiV3AuthMidware auth,ApiV3WriteMidware writes){
        Dispatcher.post("/api/v3/me/exports/posts",p::create,auth::settingsWrite,auth::profileRead,writes::handle);
        Dispatcher.get("/api/v3/me/exports/posts/{jobId}",p::status,auth::profileRead);
        Dispatcher.get("/api/v3/me/exports/posts/{jobId}/download",p::download,auth::profileRead);
    }
    private static JSONObject identity(RequestContext c){return (JSONObject)c.attr(ApiV3Processor.IDENTITY);}
    public void create(RequestContext c){ApiV3Requests.respond(c,()->{
        byte[] body=c.getRequest().getBytes();if(body!=null&&body.length>4096)throw new ApiV3Exception(413,"body_too_large","请求体过大");
        return exports.create(identity(c),ApiV3WriteInputs.body(c,Set.of("currentPassword","currentTotp"),false),c.header("Idempotency-Key"),Requests.getRemoteAddr(c.getRequest()));
    });}
    public void status(RequestContext c){ApiV3Requests.read(c,()->exports.status(identity(c),ApiV3Requests.id(c,"jobId")));}
    public void download(RequestContext c){
        final var response=c.getResponse();response.setHeader("Cache-Control","no-store");response.setHeader("Vary","Authorization");response.setHeader("X-Content-Type-Options","nosniff");
        try{
            String id=ApiV3Requests.id(c,"jobId");byte[] data=exports.download(identity(c),id);
            response.setContentType("application/zip");response.setHeader("Content-Disposition","attachment; filename=\"posts-"+id+".zip\"");response.sendBytes(data);
        }catch(ApiV3Exception error){ApiV3Processor.error(c,error.status,error.code,error.getMessage());}
        catch(Exception error){ApiV3Processor.error(c,503,"temporarily_unavailable","导出下载暂不可用");}
    }
}
