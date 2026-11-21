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

import org.b3log.latke.http.ApiV3GameSaveRawBody;
import org.b3log.symphony.util.ApiV3Json;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.service.CloudService;
import org.b3log.symphony.service.ApiV3GameSaveService;
import org.b3log.symphony.util.ApiV3GameSaveInputs;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.AccountCredentialVersions;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Singleton
public class CloudProcessor {

    @Inject
    private CloudService cloudService;

    @Inject
    private ApiV3GameSaveService gameSaves;

    @Inject
    private CloudRepository cloudRepository;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);

        final CloudProcessor cloudProcessor = beanManager.getReference(CloudProcessor.class);
        registerRoutes(cloudProcessor, loginCheck);
    }

    public static void registerRoutes(CloudProcessor cloudProcessor, LoginCheckMidware loginCheck) {
        Dispatcher.post("/api/cloud/sync", cloudProcessor::sync, loginCheck::handle);
        Dispatcher.post("/api/cloud/get", cloudProcessor::get, loginCheck::handle);
    }

    /**
     * 同步数据
     *
     * @param context
     */
    public void sync(final RequestContext context) {
        if (registeredGame(context, true)) return;
        try {
            JSONObject user = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                user = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            String userName = user.optString(User.USER_NAME);
            String userId = user.optString(Keys.OBJECT_ID);

            JSONObject requestJSONObject = context.requestJSON();
            String gameId = requestJSONObject.optString("gameId");
            if (gameId.equals("40")) {
                JSONObject data = requestJSONObject.optJSONObject("data");
                cloudService.sync(userId, gameId, data);
            } else {
                String data = requestJSONObject.optString("data");
                cloudService.sync(userId, gameId, data);
            }
            context.renderJSON(StatusCodes.SUCC).renderMsg("摸鱼派账号 " + userName + " - 游戏存档已同步至摸鱼派云服务");
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR).renderMsg("将游戏存档同步至摸鱼派失败");
        }
    }

    /**
     * 获取存档
     *
     * @param context
     */
    public void get(final RequestContext context) {
        if (registeredGame(context, false)) return;
        JSONObject user = Sessions.getUser();
        try {
            final JSONObject requestJSONObject = context.requestJSON();
            user = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        String userId = user.optString(Keys.OBJECT_ID);
        JSONObject requestJSONObject = context.requestJSON();
        String gameId = requestJSONObject.optString("gameId");
        String data = cloudService.getFromCloud(userId, gameId);

        context.renderJSON(StatusCodes.SUCC).renderData(data);
    }
    /** Only registered games move to the new slot; all other legacy cloud keys retain their existing handlers. */
    private boolean registeredGame(final RequestContext context, final boolean write) {
        JSONObject body;
        try { body=context.requestJSON(); } catch (Exception invalidLegacyBody) { return false; }
        if (!(body.opt("gameId") instanceof String game)||!ApiV3GameSaveInputs.registered(game)) return false;
        try {
            if(write){
                final String type=context.header("Content-Type");
                if(type==null||!"application/json".equalsIgnoreCase(type.split(";",2)[0].trim()))throw new ApiV3Exception(415,"unsupported_media_type","请求体必须使用 application/json");
                final byte[] bytes;
                try{bytes=ApiV3GameSaveRawBody.copy(context.getRequest(),ApiV3GameSaveInputs.MAX_REQUEST_BYTES);}
                catch(ApiV3GameSaveRawBody.TooLarge large){throw new ApiV3Exception(413,"body_too_large","请求体过大");}
                try{body=ApiV3Json.parse(bytes);}
                catch(IllegalArgumentException invalid){throw new ApiV3Exception(400,"invalid_json","JSON 请求体不合法");}
                if(!game.equals(body.opt("gameId")))throw new ApiV3Exception(422,"invalid_input","游戏标识不合法");
            }
            final JSONObject user=context.attr(User.USER) instanceof JSONObject current?current:null;
            if(user==null)throw new ApiV3Exception(401,"unauthorized","请重新登录");
            final String actor=user.optString(Keys.OBJECT_ID);
            final var proof=AccountCredentialVersions.snapshot(actor);
            if(write){
                gameSaves.legacySave(actor,proof,game,body.opt("data"));
                context.renderJSON(StatusCodes.SUCC).renderMsg("摸鱼派账号 "+user.optString(User.USER_NAME)+" - 游戏存档已同步至摸鱼派云服务");
            }else context.renderJSON(StatusCodes.SUCC).renderData(gameSaves.legacyGet(actor,proof,game));
        }catch(ApiV3Exception error){context.renderJSON(StatusCodes.ERR).renderMsg(error.getMessage());}
        catch(Exception error){context.renderJSON(StatusCodes.ERR).renderMsg("游戏存档暂不可用");}
        return true;
    }

}
