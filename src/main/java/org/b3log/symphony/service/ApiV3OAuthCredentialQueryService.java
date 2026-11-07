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
package org.b3log.symphony.service;

import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

/** Read-only endpoint-local proof, not a Bearer token. Write services must always reauthorize in their own root. */
@Service
public class ApiV3OAuthCredentialQueryService {
    @Inject private ApiV3ClientRepository clients;
    @Inject private ApiV3ClientRedirectRepository redirects;
    @Inject private ApiV3AuthorizationCodeRepository codes;
    @Inject private ApiV3TokenRepository tokens;
    @Inject private UserRepository users;
    protected long now(){return System.currentTimeMillis();}
    private boolean client(String id)throws Exception{
        if(id==null||!id.matches("[\\x21-\\x7E]{1,64}")||ApiV3TokenMgmtService.WEBSITE_CLIENT.equals(id))return false;
        final var row=clients.getByClientId(id);return row!=null&&id.equals(row.optString(ApiV3Client.CLIENT_ID))&&row.optInt(ApiV3Client.STATUS,-1)==ApiV3Client.STATUS_ACTIVE;
    }
    private JSONObject user(String id)throws Exception{
        if(id==null||!id.matches("[1-9][0-9]{0,18}"))return null;
        final var rows=users.select("SELECT oId,userStatus FROM `"+users.getName()+"` WHERE oId=?",id);
        if(rows.size()!=1||!id.equals(rows.getFirst().optString(Keys.OBJECT_ID))||rows.getFirst().optInt(UserExt.USER_STATUS,-1)!=UserExt.USER_STATUS_C_VALID)return null;
        return new JSONObject().put(Keys.OBJECT_ID,id);
    }
    private boolean liveFamily(String client,String actor,String family,long time)throws Exception{
        if(family==null||!family.matches("[0-9a-f]{32}"))return false;
        return !tokens.select("SELECT oId FROM `"+tokens.getName()+"` WHERE clientId=? AND userId=? AND familyId=? AND state=? AND expiresAt>? LIMIT 1",client,actor,family,ApiV3Token.STATE_ACTIVE,time).isEmpty();
    }
    public JSONObject code(String client,String uri,String raw,String verifier)throws Exception{
        if(!ApiV3TokenUtil.isValidFormat(raw)||!ApiV3Pkce.validVerifier(verifier)||uri==null
                ||!ApiV3OAuthRedirects.valid(uri,Latkes.RuntimeMode.DEVELOPMENT==Latkes.getRuntimeMode())||!client(client))return null;
        if(redirects.getRegistered(client,uri)==null)return null;
        final String hash=ApiV3TokenUtil.hash("oauth-code\0"+raw);final var rows=codes.select("SELECT * FROM `"+codes.getName()+"` WHERE oId=?",hash);if(rows.size()!=1)return null;
        final var row=rows.getFirst();final long time=now(),created=row.optLong("createdAt",-1),expires=row.optLong("expiresAt",-1),consumed=row.optLong("consumedAt",-1);
        if(!hash.equals(row.optString("oId"))||!client.equals(row.optString("clientId"))||!uri.equals(row.optString("redirectUri"))||!ApiV3Pkce.matches(row.optString("codeChallenge"),verifier)
                ||created<=0||created>time||created>Long.MAX_VALUE-ApiV3AuthorizationCodeService.CODE_TTL_MILLIS||expires!=created+ApiV3AuthorizationCodeService.CODE_TTL_MILLIS||consumed<0||consumed>time)return null;
        final String actor=row.optString("userId");
        if(consumed==0){if(expires<=time||!row.optString("familyId").isEmpty())return null;}
        else if(!liveFamily(client,actor,row.optString("familyId"),time))return null;
        return user(actor);
    }
    public JSONObject token(String client,String raw,boolean revocation)throws Exception{
        if(!ApiV3TokenUtil.isValidFormat(raw)||!client(client))return null;
        final String hash=ApiV3TokenUtil.hash(raw);final var row=tokens.getByHash(hash);if(row==null||!hash.equals(row.optString(ApiV3Token.TOKEN_HASH))||!client.equals(row.optString(ApiV3Token.CLIENT_ID)))return null;
        final String type=row.optString(ApiV3Token.TOKEN_TYPE);if(!ApiV3Token.TYPE_REFRESH.equals(type)&&!(revocation&&ApiV3Token.TYPE_ACCESS.equals(type)))return null;
        final long time=now();final int state=row.optInt(ApiV3Token.STATE,-1);final String actor=row.optString(ApiV3Token.USER_ID);
        if(state==ApiV3Token.STATE_ACTIVE){if(row.optLong(ApiV3Token.EXPIRES_AT)<=time)return null;}
        else if(!ApiV3Token.TYPE_REFRESH.equals(type)||state!=ApiV3Token.STATE_ROTATED||!liveFamily(client,actor,row.optString(ApiV3Token.FAMILY_ID),time))return null;
        return user(actor);
    }
}
