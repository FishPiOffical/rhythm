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
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.json.JSONObject;

/** Existing registration defaults only. HTTP authorization/whitelisting belongs to the caller. */
public final class UserRegistrationDefaults {
    private UserRegistrationDefaults(){ }
    public static JSONObject create(JSONObject requestJSONObject,String mbti,String adminSpecifiedLang,long time){
        final String userEmail=requestJSONObject.optString(User.USER_EMAIL).trim().toLowerCase();
        final String userPhone=requestJSONObject.optString("userPhone");
        final String userName=requestJSONObject.optString(User.USER_NAME);
        final JSONObject user = new JSONObject();
        user.put(User.USER_NAME, userName);
        user.put("userPhone", userPhone);
        user.put(User.USER_EMAIL, userEmail);
        user.put(UserExt.USER_APP_ROLE, requestJSONObject.optInt(UserExt.USER_APP_ROLE));
        user.put(User.USER_PASSWORD, requestJSONObject.optString(User.USER_PASSWORD));
        user.put(User.USER_ROLE, requestJSONObject.optString(User.USER_ROLE, Role.ROLE_ID_C_DEFAULT));
        user.put(User.USER_URL, "");
        user.put(UserExt.USER_ARTICLE_COUNT, 0);
        user.put(UserExt.USER_COMMENT_COUNT, 0);
        user.put(UserExt.USER_TAG_COUNT, 0);
        user.put(UserExt.USER_INTRO, "");
        user.put(UserExt.USER_NICKNAME, "");
        user.put(UserExt.USER_AVATAR_TYPE, UserExt.USER_AVATAR_TYPE_C_UPLOAD);
        user.put(UserExt.USER_QQ, "");
        user.put(UserExt.USER_ONLINE_FLAG, false);
        user.put(UserExt.USER_LATEST_ARTICLE_TIME, 0L);
        user.put(UserExt.USER_LATEST_CMT_TIME, 0L);
        user.put(UserExt.USER_LATEST_LOGIN_TIME, 0L);
        user.put(UserExt.USER_LATEST_LOGIN_IP, "");
        user.put(UserExt.USER_CHECKIN_TIME, 0);
        user.put(UserExt.USER_CURRENT_CHECKIN_STREAK_START, 0);
        user.put(UserExt.USER_CURRENT_CHECKIN_STREAK_END, 0);
        user.put(UserExt.USER_LONGEST_CHECKIN_STREAK_START, 0);
        user.put(UserExt.USER_LONGEST_CHECKIN_STREAK_END, 0);
        user.put(UserExt.USER_LONGEST_CHECKIN_STREAK, 0);
        user.put(UserExt.USER_CURRENT_CHECKIN_STREAK, 0);
        user.put(UserExt.USER_POINT, 0);
        user.put(UserExt.USER_USED_POINT, 0);
        user.put(UserExt.USER_JOIN_POINT_RANK, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_JOIN_USED_POINT_RANK, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_TAGS, "");
        user.put(UserExt.USER_SKIN, Symphonys.SKIN_DIR_NAME);
        user.put(UserExt.USER_MOBILE_SKIN, Symphonys.MOBILE_SKIN_DIR_NAME);
        user.put(UserExt.USER_COUNTRY, "");
        user.put(UserExt.USER_PROVINCE, "");
        user.put(UserExt.USER_CITY, "");
        user.put(UserExt.USER_UPDATE_TIME, 0L);
        user.put(UserExt.USER_GEO_STATUS, UserExt.USER_GEO_STATUS_C_PUBLIC);
        final int status = requestJSONObject.optInt(UserExt.USER_STATUS, UserExt.USER_STATUS_C_NOT_VERIFIED);
        user.put(UserExt.USER_STATUS, status);
        user.put(UserExt.USER_COMMENT_VIEW_MODE, UserExt.USER_COMMENT_VIEW_MODE_C_REALTIME);
        user.put(UserExt.USER_ONLINE_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_ARTICLE_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_COMMENT_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_FOLLOWING_ARTICLE_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_WATCHING_ARTICLE_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_FOLLOWING_TAG_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_FOLLOWING_USER_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_FOLLOWER_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_BREEZEMOON_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.CHAT_ROOM_PICTURE_STATUS, UserExt.USER_XXX_STATUS_C_DISABLED);
        user.put(UserExt.USER_POINT_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_UA_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_NOTIFY_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_SUB_MAIL_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_LIST_PAGE_SIZE, Symphonys.ARTICLE_LIST_CNT);
        user.put(UserExt.USER_LIST_VIEW_MODE, UserExt.USER_LIST_VIEW_MODE_TITLE_ABSTRACT);
        user.put(UserExt.USER_AVATAR_VIEW_MODE, UserExt.USER_AVATAR_VIEW_MODE_C_ORIGINAL);
        user.put(UserExt.USER_SUB_MAIL_SEND_TIME, time);
        user.put(UserExt.USER_KEYBOARD_SHORTCUTS_STATUS, UserExt.USER_XXX_STATUS_C_DISABLED);
        user.put(UserExt.USER_REPLY_WATCH_ARTICLE_STATUS, UserExt.USER_SUB_MAIL_STATUS_DISABLED);
        user.put(UserExt.USER_FORWARD_PAGE_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
        user.put(UserExt.USER_INDEX_REDIRECT_URL, "");
        user.put(UserExt.ONLINE_MINUTE, 0);
        user.put("secret2fa", "");
        user.put("mbti", mbti);

        if ("0".equals(adminSpecifiedLang)) {
            user.put(UserExt.USER_LANGUAGE, requestJSONObject.optString(UserExt.USER_LANGUAGE, "zh_CN"));
        } else {
            user.put(UserExt.USER_LANGUAGE, adminSpecifiedLang);
        }

        user.put(UserExt.USER_TIMEZONE, requestJSONObject.optString(UserExt.USER_TIMEZONE, "Asia/Shanghai"));
        user.put(UserExt.USER_GUIDE_STEP, requestJSONObject.optInt(UserExt.USER_GUIDE_STEP, UserExt.USER_GUIDE_STEP_UPLOAD_AVATAR));

        return user;
    }
}
