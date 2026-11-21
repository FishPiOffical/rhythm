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

import org.b3log.latke.model.User;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.b3log.symphony.service.DataModelService;
import org.json.JSONObject;

import java.util.Set;

/** 网站操作入口的服务端能力提示；资源接口仍须按当前身份重新鉴权。 */
public final class ApiV3UserCapabilities {
    private ApiV3UserCapabilities() { }

    public static boolean website(final JSONObject identity) {
        return identity != null && ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(identity.optString("clientId"));
    }

    public static JSONObject project(final JSONObject user, final JSONObject identity, final Set<String> permissions) {
        final boolean firstParty = user != null && website(identity);
        final String role = user == null ? "" : user.optString(User.USER_ROLE);
        // 管理能力与 isAdmin 分开：纪律委员和 OP 沿用旧站规则，不能据此获得管理员专属权限。
        return new JSONObject()
                .put("chatRevokeOthers", firstParty && DataModelService.hasPermission(role, 3))
                .put("viewArticleHistory", firstParty && permissions.contains(Permission.PERMISSION_ID_C_COMMON_VIEW_ARTICLE_HISTORY))
                .put("viewCommentHistory", firstParty && permissions.contains(Permission.PERMISSION_ID_C_COMMON_VIEW_COMMENT_HISTORY))
                .put("canAccessAdmin", firstParty && permissions.contains(Permission.PERMISSION_ID_C_MENU_ADMIN));
    }
}
