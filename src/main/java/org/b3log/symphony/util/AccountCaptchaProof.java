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

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.json.JSONObject;

/** Bounded opaque proof from the existing captcha widget; no user-supplied validation URL. */
public final class AccountCaptchaProof {
    private AccountCaptchaProof(){ }
    public static String validate(String proof){
        if(proof==null||proof.isEmpty()||proof.getBytes(StandardCharsets.UTF_8).length>4096)throw ApiV3WriteInputs.invalid();
        final JSONObject body;
        try{body=ApiV3Json.parse(proof.getBytes(StandardCharsets.UTF_8));}catch(IllegalArgumentException e){throw ApiV3WriteInputs.invalid();}
        if(!body.keySet().equals(Set.of("lot_number","captcha_output","pass_token","gen_time")))throw ApiV3WriteInputs.invalid();
        for(String key:body.keySet()){
            if(!(body.opt(key) instanceof String value)||value.isEmpty()||value.length()>2048
                    ||value.codePoints().anyMatch(c->Character.isISOControl(c)))throw ApiV3WriteInputs.invalid();
        }
        return body.toString();
    }
}
