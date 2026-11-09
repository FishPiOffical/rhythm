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

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.UserNameRules;
import org.json.JSONArray;
import org.json.JSONObject;

/** 返回公开注册表单规则，实际注册仍需独立检查资格。 */
@Service
public class ApiV3RegistrationPolicyService {
    @Inject private OptionRepository options;
    @Inject private UserQueryService users;
    @Inject private RoleQueryService roles;

    public JSONObject options(String referral)throws Exception {
        final String name=referral==null?"":referral.strip();
        if(!name.isEmpty()&&UserNameRules.invalid(name))throw new ApiV3Exception(422,"invalid_request","邀请人用户名不合法");
        final var settings=options.select("SELECT optionValue FROM `"+options.getName()+"` WHERE oId=?",Option.ID_C_MISC_ALLOW_REGISTER);
        final JSONObject setting=settings.size()==1?settings.getFirst():null;
        final String value=setting==null?"":setting.optString(Option.OPTION_VALUE);
        final String mode=switch(value){case "0"->"open";case "1"->"closed";case "2"->"invitation";default->throw new ApiV3Exception(503,"temporarily_unavailable","注册状态暂不可用");};
        boolean accepted=false;
        // 沿用旧写入口的优先级，关闭注册时邀请码也无法开放注册。
        if(!"closed".equals(mode)&&!name.isEmpty()){
            final JSONObject inviter=users.getUserByName(name);
            if(inviter!=null){
                final var permissions=roles.getUserPermissionsGrantMap(inviter.optString(Keys.OBJECT_ID));
                final var grant=permissions==null?null:permissions.get(Permission.PERMISSION_ID_C_COMMON_USE_INVITATION_LINK);
                accepted=grant!=null&&grant.optBoolean(Permission.PERMISSION_T_GRANT);
            }
        }
        return new JSONObject().put("mode",mode).put("enabled",!"closed".equals(mode))
                .put("inviteCodeRequired","invitation".equals(mode)&&!accepted).put("referralAccepted",accepted)
                .put("username",new JSONObject().put("minLength",UserNameRules.MIN_LENGTH).put("maxLength",UserNameRules.MAX_LENGTH))
                .put("appRoles",new JSONArray().put(new JSONObject().put("value",UserExt.USER_APP_ROLE_C_HACKER).put("label","程序员"))
                        .put(new JSONObject().put("value",UserExt.USER_APP_ROLE_C_PAINTER).put("label","设计师")));
    }
}
