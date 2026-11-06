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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Durable topic value only; entry points own their approved filtering policy before constructing it. */
public record ChatTopicValue(String text, String whoChanged, long revision) {
    public static final String OPTION_ID="chatRoomTopic";
    public static final int COST=16;
    public ChatTopicValue {
        if(text==null||text.isBlank()||text.length()>64||whoChanged==null||whoChanged.isBlank()||whoChanged.length()>64||revision<1)throw new IllegalArgumentException("topic state");
        // Reject malformed UTF-16 rather than silently persisting a truncated surrogate or ambiguous JSON.
        ApiV3Json.canonical(new JSONObject().put("text",text).put("whoChanged",whoChanged));
    }
    public String stored() {
        final String value=new JSONObject().put("text",text).put("whoChanged",whoChanged).put("revision",Long.toString(revision)).toString();
        if(value.length()>512)throw new IllegalArgumentException("topic state too long");
        return value;
    }
    public static ChatTopicValue parse(final String raw) {
        try {
            if(raw==null||raw.length()>512)throw new IllegalArgumentException("topic state length");
            final var value=ApiV3Json.parse(raw.getBytes(StandardCharsets.UTF_8));
            if(!value.keySet().equals(Set.of("text","whoChanged","revision"))||!(value.get("text")instanceof String)
                    ||!(value.get("whoChanged")instanceof String)||!(value.get("revision")instanceof String revision)
                    ||!revision.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("topic state shape");
            return new ChatTopicValue(value.getString("text"),value.getString("whoChanged"),new BigDecimal(revision).longValueExact());
        }catch(RuntimeException e){throw new IllegalStateException("invalid stored chat topic",e);}
    }
}
