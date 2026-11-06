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

import java.util.function.BooleanSupplier;

/** Explicit restriction expiries, without legacy negative-second sentinel collisions or database writes. */
public final class ChatMessageRestrictions {
    public record State(long globalMuteUntil, long personalMuteUntil, long riskUntil) { }
    private ChatMessageRestrictions() { }
    public static void check(final State state, final boolean operator, final String content, final long now,
                             final BooleanSupplier confirmation) {
        if (state.riskUntil() > now) {
            if (content.startsWith("![") && content.endsWith(")")) {
                throw new ApiV3Exception(403, "risk_content_rejected", "风控期间不能发送单图片消息");
            }
            if (content.length() < 5) throw new ApiV3Exception(403, "risk_content_rejected", "风控期间消息至少需要5个字符");
            if (!confirmation.getAsBoolean()) throw new ApiV3Exception(409, "confirmation_required", "请使用原请求再次发送，确认本条消息");
        }
        // Preserve existing global-mute priority and operator exemption while a global mute is active.
        if (state.globalMuteUntil() > now) {
            if (!operator) throw new ApiV3Exception(403, "chat_muted", "聊天室正在全员禁言");
        } else if (state.personalMuteUntil() > now) {
            throw new ApiV3Exception(403, "chat_muted", "当前账号正在禁言中");
        }
    }
}
