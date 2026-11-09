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
import org.jsoup.Jsoup;
import java.util.List;
import java.util.Set;

/** Typed report contract, normalized before idempotency hashing. */
public final class ApiV3ReportInputs {
    private static final List<Integer> CONTENT_REASONS = List.of(0, 1, 2, 3, 4, 49);
    private static final List<Integer> USER_REASONS = List.of(5, 6, 7, 49);

    private ApiV3ReportInputs() { }

    public static List<Integer> reasonsFor(final int targetType) {
        return switch (targetType) {
            case 0, 1, 3 -> CONTENT_REASONS;
            case 2 -> USER_REASONS;
            default -> throw ApiV3Requests.invalid();
        };
    }

    public static JSONObject normalize(final JSONObject body) {
        if(body==null || !Set.of("targetType","targetId","reason","memo").containsAll(body.keySet())
                || !body.has("targetType") || !body.has("targetId") || !body.has("reason")) throw ApiV3Requests.invalid();
        final int type=ApiV3WriteInputs.integer(body,"targetType",-1,0,3);
        final int reason=ApiV3WriteInputs.integer(body,"reason",-1,0,49);
        if(!(body.opt("targetId") instanceof String target) || !target.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
        if(!reasonsFor(type).contains(reason)) throw ApiV3Requests.invalid();
        final String raw=ApiV3WriteInputs.string(body,"memo",10240,true);
        try{ApiV3Json.canonical(raw);}catch(IllegalArgumentException e){throw ApiV3Requests.invalid();}
        final String memo=Jsoup.parseBodyFragment(raw).wholeText().trim();
        if(org.apache.commons.lang.StringEscapeUtils.escapeHtml(memo).length()>10240 || memo.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t')) throw ApiV3Requests.invalid();
        try{ApiV3Json.canonical(memo);}catch(IllegalArgumentException e){throw ApiV3Requests.invalid();}
        return new JSONObject().put("targetType",type).put("targetId",target).put("reason",reason).put("memo",memo);
    }
}
