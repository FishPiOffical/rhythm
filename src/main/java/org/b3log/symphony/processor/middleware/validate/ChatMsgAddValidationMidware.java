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
package org.b3log.symphony.processor.middleware.validate;

import org.b3log.latke.Keys;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.service.ChatMessageValidationService;
import org.b3log.symphony.util.ChatMessageValidation;
import org.b3log.symphony.util.ChatTopicInputs;
import org.b3log.symphony.util.Sessions;
import org.json.JSONObject;

/**
 * Validates for chat message adding.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 1.4.0
 */
@Singleton
public class ChatMsgAddValidationMidware {

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    @Inject private ChatMessageValidationService validation;

    public void handle(final RequestContext context) {
        final Request request = context.getRequest();
        final JSONObject requestJSONObject = context.requestJSON();
        request.setAttribute(Keys.REQUEST, requestJSONObject);
        JSONObject currentUser = Sessions.getUser();
        if(ChatTopicInputs.isEnvelope(requestJSONObject.optString(Common.CONTENT)))currentUser=(JSONObject)context.attr(User.USER);
        else try {
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        try {
            requestJSONObject.put(Common.CONTENT, validation.prepare(currentUser, requestJSONObject.optString(Common.CONTENT)));
        } catch (final ChatMessageValidation.Rejected e) {
            context.renderJSON(new JSONObject().put(Keys.MSG, "too_frequent".equals(e.code)
                    ? langPropsService.get("tooFrequentCmtLabel") : e.getMessage()));
            context.abort();
            return;
        }
        context.handle();
    }
}
