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

import org.b3log.symphony.processor.ChatroomProcessor;

/** Approved topic policies: v3 rejects oversized input; legacy retains filtering and bounded truncation. */
public final class ChatTopicInputs {
    public static final String OPEN="[setdiscuss]",CLOSE="[/setdiscuss]";
    private ChatTopicInputs(){ }
    public static boolean isEnvelope(final String value){
        if(value==null)return false;final String text=value.trim();return text.startsWith(OPEN)&&text.endsWith(CLOSE);
    }
    public static String unwrap(final String value){
        if(!isEnvelope(value))throw invalid();final String text=value.trim();return text.substring(OPEN.length(),text.length()-CLOSE.length());
    }
    public static String v3(final String raw){
        if(raw==null||raw.length()>64)throw invalid();
        try{ApiV3Json.canonical(raw);}catch(IllegalArgumentException e){throw invalid();}
        final String value=ChatroomProcessor.filterDiscussString(raw).strip();if(value.isBlank())throw invalid();return value;
    }
    public static String legacy(final String raw){
        if(raw==null||raw.length()>ApiV3Json.MAX_BODY_BYTES)throw invalid();
        String value=ChatroomProcessor.filterDiscussString(raw);
        if(value.length()>64){int end=64;if(Character.isHighSurrogate(value.charAt(end-1)))end--;value=value.substring(0,end);}
        if(value.isBlank())throw invalid();
        try{ApiV3Json.canonical(value);}catch(IllegalArgumentException e){throw invalid();}return value;
    }
    private static ApiV3Exception invalid(){return new ApiV3Exception(422,"invalid_request","话题不能为空且最多64字符");}
}
