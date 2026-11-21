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

import org.b3log.symphony.model.UserExt;
import org.json.JSONObject;
import org.testng.Assert;
import java.time.*;
import java.util.*;

/** Business transition only; no claim about persistence, daily checkin production or HTTP. */
public class CheckinPatchMutationTest {
    public static void main(String[] args){
        for(LocalDate today:List.of(LocalDate.of(2026,3,1),LocalDate.of(2027,1,1),LocalDate.of(2024,3,1),LocalDate.of(2026,10,18))){
            final LocalDate end=today.minusDays(2),start=end.minusDays(19);
            Assert.assertEquals(CheckinPatchMutation.patchStart(day(start),day(end),today),day(start));
            Assert.assertEquals(CheckinPatchMutation.patchStart(day(start),day(today.minusDays(3)),today),day(today.minusDays(1)));
            var user=user(today,today,start,end);var bag=bag(today.minusDays(1));String before=user.toString(),bagBefore=bag.toString();
            var result=CheckinPatchMutation.apply(user,bag,today);
            Assert.assertEquals(result.changes().getInt(UserExt.USER_CURRENT_CHECKIN_STREAK_START),day(start));Assert.assertEquals(result.changes().getInt(UserExt.USER_CURRENT_CHECKIN_STREAK),22);
            Assert.assertEquals(result.bag().getInt("patchCheckinCard"),1);Assert.assertFalse(result.bag().has("patchStart"));Assert.assertEquals(result.bag().getInt("other"),7);
            Assert.assertEquals(user.toString(),before);Assert.assertEquals(bag.toString(),bagBefore);
        }
        System.out.println("PASS patch hints and application restore adjacent prior runs across month/year/leap boundaries with one card and immutable input snapshots");
        var today=LocalDate.of(2026,10,18);var user=user(today.minusDays(2),today,today.minusDays(31),today.minusDays(12));
        var result=CheckinPatchMutation.apply(user,bag(today.minusDays(3)),today);
        Assert.assertEquals(result.changes().getInt(UserExt.USER_CURRENT_CHECKIN_STREAK),4);Assert.assertEquals(result.changes().getInt(UserExt.USER_LONGEST_CHECKIN_STREAK),20);
        Assert.assertFalse(result.changes().has(UserExt.USER_LONGEST_CHECKIN_STREAK_START));Assert.assertFalse(result.changes().has(UserExt.USER_CURRENT_CHECKIN_STREAK_END));Assert.assertFalse(result.changes().has(UserExt.USER_CHECKIN_TIME));
        Assert.assertEquals(result.changes().keySet(),Set.of(UserExt.USER_CURRENT_CHECKIN_STREAK_START,UserExt.USER_CURRENT_CHECKIN_STREAK,UserExt.USER_LONGEST_CHECKIN_STREAK));
        System.out.println("PASS one-day extension preserves unrelated longest runs, checkin time/end and all non-streak user fields");
        final var original=TimeZone.getDefault();
        try{for(String zone:List.of("Asia/Shanghai","America/New_York","Europe/Berlin")){
            TimeZone.setDefault(TimeZone.getTimeZone(zone));var end=LocalDate.of(2026,3,10);var u=user(end,end,LocalDate.of(2026,3,1),LocalDate.of(2026,3,8));
            Assert.assertEquals(CheckinPatchMutation.apply(u,bag(LocalDate.of(2026,3,9)),end).changes().getInt(UserExt.USER_CURRENT_CHECKIN_STREAK),10);
        }}finally{TimeZone.setDefault(original);}
        System.out.println("PASS natural-day counts remain stable across daylight-saving zones and do not divide elapsed milliseconds");
        for(var reason:List.of(CheckinPatchMutation.Reason.NO_CARD,CheckinPatchMutation.Reason.NO_RECORD,CheckinPatchMutation.Reason.LONGEST,CheckinPatchMutation.Reason.NOT_TODAY)){
            var u=new JSONObject(user.toString());var b=bag(today.minusDays(3));
            switch(reason){case NO_CARD->b.put("patchCheckinCard",0);case NO_RECORD->b.remove("patchStart");case LONGEST->u.put(UserExt.USER_LONGEST_CHECKIN_STREAK_START,u.get(UserExt.USER_CURRENT_CHECKIN_STREAK_START));case NOT_TODAY->u.put(UserExt.USER_CURRENT_CHECKIN_STREAK_END,day(today.minusDays(1)));default->{}}
            reject(reason,u,b,today);
        }
        System.out.println("PASS missing cards/hints, current longest streak and no checkin today reject without consuming cards or hints");
        for(Object value:List.of(20260230,20261301,20260000,20261019,"00000101","2026-10-17","10%",JSONObject.NULL))reject(CheckinPatchMutation.Reason.INVALID_RECORD,user,bag(today.minusDays(3)).put("patchStart",value),today);
        for(Object count:List.of(-1,1.5,"2147483648",true,JSONObject.NULL))reject(CheckinPatchMutation.Reason.INVALID_RECORD,user,bag(today.minusDays(3)).put("patchCheckinCard",count),today);
        System.out.println("PASS malformed/impossible/future dates and corrupt card quantities fail closed without mutating either snapshot");
    }
    static int day(LocalDate d){return CheckinPatchMutation.day(d);}
    static JSONObject user(LocalDate start,LocalDate end,LocalDate longestStart,LocalDate longestEnd){return new JSONObject().put(UserExt.USER_CURRENT_CHECKIN_STREAK_START,day(start)).put(UserExt.USER_CURRENT_CHECKIN_STREAK_END,day(end)).put(UserExt.USER_LONGEST_CHECKIN_STREAK_START,day(longestStart)).put(UserExt.USER_LONGEST_CHECKIN_STREAK_END,day(longestEnd)).put("userEmail","private");}
    static JSONObject bag(LocalDate start){return new JSONObject().put("patchCheckinCard",2).put("patchStart",day(start)).put("other",7);}
    static void reject(CheckinPatchMutation.Reason reason,JSONObject u,JSONObject b,LocalDate today){String us=u.toString(),bs=b.toString();try{CheckinPatchMutation.apply(u,b,today);Assert.fail();}catch(CheckinPatchMutation.Rejected e){Assert.assertEquals(e.reason,reason);}Assert.assertEquals(u.toString(),us);Assert.assertEquals(b.toString(),bs);}
}
