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

import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

/** Public widget identifier only; never exposes its server verification key or configuration map. */
@Service
public class AccountCaptchaService {
    protected String configuredId(){return Symphonys.get("jiyan.id");}
    public JSONObject options(){
        final String value=configuredId();
        if(value==null||!value.matches("[A-Za-z0-9_-]{1,128}"))throw new ApiV3Exception(503,"temporarily_unavailable","验证组件暂不可用");
        return new JSONObject().put("provider","geetest4").put("captchaId",value);
    }
}
