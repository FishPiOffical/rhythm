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
import org.jsoup.safety.Safelist;
import java.util.Set;

/** The requested amount includes the fee; the caller never selects a debit account. */
public final class ApiV3PointTransferInputs {
    private ApiV3PointTransferInputs() { }
    public record Transfer(String userName, int amount, String memo) {
        public JSONObject normalized() { return new JSONObject().put("userName",userName).put("amount",amount).put("memo",memo); }
    }
    public static Transfer parse(final JSONObject body) {
        if(body==null || !Set.of("userName","amount","memo").containsAll(body.keySet())
                || !body.has("userName") || !body.has("amount")) throw ApiV3WriteInputs.invalid();
        final String name=ApiV3WriteInputs.string(body,"userName",64,true);
        if(!name.matches("[A-Za-z0-9_-]{1,64}")) throw ApiV3WriteInputs.invalid();
        final int amount=ApiV3WriteInputs.integer(body,"amount",0,2,Integer.MAX_VALUE);
        final String rawMemo=ApiV3WriteInputs.string(body,"memo",128,true);
        try { ApiV3Json.canonical(rawMemo); }
        catch(IllegalArgumentException e) { throw ApiV3WriteInputs.invalid(); }
        if(rawMemo.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t')) throw ApiV3WriteInputs.invalid();
        final String memo=Jsoup.clean(rawMemo,Safelist.none());
        if(memo.length()>255) throw ApiV3WriteInputs.invalid();
        return new Transfer(name,amount,memo);
    }
    public static int fee(final int amount,final boolean exempt) {
        if(amount<2) throw ApiV3WriteInputs.invalid();
        return exempt?0:Math.max(1,amount/100);
    }
}
