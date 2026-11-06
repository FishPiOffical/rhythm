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
import java.util.Set;

/** Server-owned registration proof binding. An empty value is an existing legacy SMS proof. */
public final class RegistrationProofContext {
    private RegistrationProofContext(){ }
    public static String encode(String name,String referral,String referralId,String inviteId){
        if(UserNameRules.invalid(name)||referral==null||!referral.isEmpty()&&UserNameRules.invalid(referral)
                ||referralId==null||!referralId.isEmpty()&&!referralId.matches("[1-9][0-9]{0,18}")||referral.isEmpty()&&!referralId.isEmpty()
                ||inviteId==null||!inviteId.isEmpty()&&!inviteId.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("registration proof context");
        return new JSONObject().put("version",1).put("name",name).put("referral",referral).put("referralId",referralId).put("inviteId",inviteId).toString();
    }
    public static boolean matches(String value,String name,String referral,String referralId,String inviteId){
        if(value==null||value.isEmpty())return true;
        if(value.length()>512||name==null||referral==null||referralId==null||inviteId==null)return false;
        try{
            final var context=ApiV3Json.parse(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(!context.keySet().equals(Set.of("version","name","referral","referralId","inviteId"))||!(context.opt("version") instanceof Number version)
                    ||!version.toString().equals("1")||!(context.opt("name") instanceof String expectedName)
                    ||!(context.opt("referral") instanceof String expectedReferral)||!(context.opt("referralId") instanceof String expectedReferralId)||!(context.opt("inviteId") instanceof String expectedInvite))return false;
            encode(expectedName,expectedReferral,expectedReferralId,expectedInvite);
            return expectedName.equals(name)&&expectedReferral.equals(referral)&&expectedReferralId.equals(referralId)&&expectedInvite.equals(inviteId);
        }catch(RuntimeException ignored){return false;}
    }
}
