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

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Provider acknowledgement is not a promise of inbox delivery. */
public final class AccountMailResponses {
    private AccountMailResponses(){ }
    public static boolean accepted(String channel,int status,String body) {
        if(status!=200||body==null||body.length()>16384)return false;
        try {
            JSONObject value=ApiV3Json.parse(body.getBytes(StandardCharsets.UTF_8));
            return switch(channel) {
                case "sendcloud" -> Boolean.TRUE.equals(value.opt("result"))&&value.opt("statusCode") instanceof Number n&&"200".equals(n.toString());
                case "aliyun" -> !value.has("Code")&&value.opt("RequestId") instanceof String request&&!request.isBlank()
                        &&value.opt("EnvId") instanceof String event&&!event.isBlank();
                default -> false;
            };
        }catch(Exception ignored){return false;}
    }
}
