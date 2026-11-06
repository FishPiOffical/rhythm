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

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Append OAuth response parameters to an already validated, exact registered URI without fetching it. */
public final class ApiV3OAuthCallbacks {
    private static final Set<String> RESERVED=Set.of("code","state","error","error_description","error_uri","iss");
    private ApiV3OAuthCallbacks(){ }
    public static boolean hasResponseParameters(final URI uri){
        final String query=uri.getRawQuery();if(query==null)return false;
        for(String pair:query.split("&",-1)){
            final String key=pair.split("=",2)[0];
            try{if(RESERVED.contains(URLDecoder.decode(key,StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT)))return true;}
            catch(IllegalArgumentException e){return true;}
        }
        return false;
    }
    public static void validateState(final String state){
        if(state==null||state.length()>1024||state.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("OAuth state");
        ApiV3Json.canonical(state);if(state.getBytes(StandardCharsets.UTF_8).length>1024)throw new IllegalArgumentException("OAuth state bytes");
    }
    public static String result(final String registered,final String state,final String code,final boolean approved){
        if(!ApiV3OAuthRedirects.valid(registered,true))throw new IllegalArgumentException("callback binding");
        validateState(state);
        if(approved&&!ApiV3TokenUtil.isValidFormat(code)||!approved&&code!=null)throw new IllegalArgumentException("callback result");
        final String first=approved?"code="+encode(code):"error=access_denied";
        final String separator=!registered.contains("?")?"?":registered.endsWith("?")||registered.endsWith("&")?"":"&";
        return URI.create(registered+separator+first+(state.isEmpty()?"":"&state="+encode(state))).toASCIIString();
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
