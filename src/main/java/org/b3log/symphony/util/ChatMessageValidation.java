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

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.json.JSONObject;
import java.util.function.UnaryOperator;

/** Existing chat envelope validation, independent of sessions, commands and transaction ownership. */
public final class ChatMessageValidation {
    public static final int MAX_CONTENT_LENGTH=4096;
    private ChatMessageValidation() { }
    public static final class Rejected extends RuntimeException {
        public final int status;public final String code;
        public Rejected(int status,String code,String message){super(message);this.status=status;this.code=code;}
    }
    public static String prepare(JSONObject actor,String input,long now,long minimumInterval,UnaryOperator<String> reservedWords){
        if(now-actor.optLong(UserExt.USER_LATEST_CMT_TIME)<minimumInterval&&!Role.ROLE_ID_C_ADMIN.equals(actor.optString(User.USER_ROLE)))
            throw new Rejected(429,"too_frequent","操作过于频繁，请稍候重试。");
        final String content=StringUtils.trim(reservedWords.apply(input));
        if(StringUtils.isBlank(content)||content.length()>MAX_CONTENT_LENGTH)
            throw new Rejected(422,"invalid_content","聊天消息内容为空或过多（大于4096字符）");
        return content;
    }
}
