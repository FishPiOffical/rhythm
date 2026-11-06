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
package org.b3log.symphony.util;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.*;
import org.b3log.symphony.processor.*;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.json.JSONObject;
import org.testng.Assert;
import io.netty.handler.codec.http.*;
import java.util.*;

/** Actual directory route/controller/auth; token lookup is an adapter, not a live server. */
public class ApiV3RedPacketRoutingTest {
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");props.setProperty("contextPath","");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        var auth=new ApiV3AuthMidware();var field=ApiV3AuthMidware.class.getDeclaredField("apiV3TokenMgmtService");field.setAccessible(true);field.set(auth,new ApiV3TokenMgmtService(){@Override public JSONObject authenticate(String token){return Set.of("reader","writer").contains(token)?new JSONObject().put("user",new JSONObject().put("oId","123")).put("scope",token.equals("reader")?"content.read":"content.write"):null;}});
        var processor=new ApiV3RedPacketProcessor();RedPacketRouteBindings.register(processor,auth);Dispatcher.mapping();
        for(String token:Arrays.asList(null,"invalid","writer","reader")){
            var raw=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,io.netty.handler.codec.http.HttpMethod.GET,"/api/v3/chat/red-packet-types?apiKey=legacy");
            try{raw.headers().set("Cookie","legacy");if(token!=null)raw.headers().set("Authorization","Bearer "+token);var request=new Request(null,raw){@Override public String getRemoteAddr(){return "192.0.2.178";}};request.setParams(new HashMap<>(Map.of("apiKey","legacy")));
                var c=new RequestContext(request,new Response(null,new DefaultHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)));c.attr("user",new JSONObject().put("oId","321"));c.addHandler(new org.b3log.latke.http.handler.RouteHandler());c.addHandler(context->{var r=(org.b3log.latke.http.handler.RouteResolution)context.attr("MATCH_RESULT");Assert.assertEquals(r.getContextHandlerMeta().getInvokeHolder().getName(),"types");processor.types(context);});c.handle();
                if("reader".equals(token)){Assert.assertEquals(c.attr("test.status"),200);Assert.assertEquals(((JSONObject)c.attr("test.data")).getJSONArray("types").length(),5);Assert.assertEquals(c.getResponse().getHeader("Cache-Control"),"no-store");}
                else{Assert.assertEquals(c.attr("test.status"),token==null||token.equals("invalid")?401:403);Assert.assertNull(c.attr("test.data"));}
            }finally{raw.release();}
        }
        System.out.println("PASS actual red-packet directory route/controller runs content.read Bearer before no-store fixed catalog; Cookie/apiKey and write-only scope do not authorize reading");
    }
}
