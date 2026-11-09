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
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

/** 仅校验约定的 OpenID 登录 URL，不请求该地址或验证站点归属。 */
public final class ApiV3FishGameSubmissionInputs {
    private ApiV3FishGameSubmissionInputs() { }
    private static final String NS="http://specs.openid.net/auth/2.0";
    private static final String IDENTITY=NS+"/identifier_select";
    private static final Set<String> FIELDS=Set.of("name","description","url","iconUrl","oauthLoginUrl");
    private static final Set<String> QUERY=Set.of("openid.ns","openid.mode","openid.return_to","openid.realm","openid.identity","openid.claimed_id","fishpi.scope");
    private static final Set<String> SCOPES=Set.of("profile.read","profile.detail.read","points.read","articles.read","membership.read");
    public record Submission(String name, String description, String url, String iconUrl, String oauthLoginUrl) {
        public JSONObject normalized() {
            return new JSONObject().put("name", name).put("description", description)
                    .put("url", url).put("iconUrl", iconUrl).put("oauthLoginUrl", oauthLoginUrl);
        }
    }
    public static Submission parse(JSONObject body,String developmentProvider) {
        if(body==null||!body.keySet().equals(FIELDS))throw ApiV3WriteInputs.invalid();
        String name=text(body,"name",80),description=text(body,"description",1000);
        String url=text(body,"url",512),icon=text(body,"iconUrl",512);
        URI game = https(url, 512);
        https(icon, 512);
        String login=ApiV3WriteInputs.string(body,"oauthLoginUrl",4096,true);
        if(login.isEmpty()||login.codePoints().anyMatch(Character::isISOControl))throw ApiV3WriteInputs.invalid();
        try {
            URI provider=new URI(login);
            if(provider.getRawUserInfo()!=null||provider.getRawFragment()!=null||provider.getRawQuery()==null)throw ApiV3WriteInputs.invalid();
            String endpoint=login.substring(0,login.indexOf('?'));
            if(!"https://fishpi.cn/openid/login".equals(endpoint)&&!endpoint.equals(developmentProvider))throw ApiV3WriteInputs.invalid();
            var params=new HashMap<String,String>();
            for(String pair:provider.getRawQuery().split("&",-1)) {
                int at = pair.indexOf('=');
                if (at <= 0)
                    throw ApiV3WriteInputs.invalid();
                String key=decode(pair.substring(0,at)),value=decode(pair.substring(at+1));
                if(!QUERY.contains(key)||params.putIfAbsent(key,value)!=null||value.codePoints().anyMatch(Character::isISOControl))throw ApiV3WriteInputs.invalid();
            }
            if(!NS.equals(params.get("openid.ns"))||!"checkid_setup".equals(params.get("openid.mode"))
                    ||!IDENTITY.equals(params.get("openid.identity"))||!IDENTITY.equals(params.get("openid.claimed_id")))throw ApiV3WriteInputs.invalid();
            String callback=params.get("openid.return_to"),realm=params.get("openid.realm");
            URI target=https(callback,2048),trust=https(realm,2048);
            rejectCredentials(target);
            rejectCredentials(trust);
            if(!origin(game).equals(origin(target))||!origin(target).equals(origin(trust))||!callback.startsWith(realm))throw ApiV3WriteInputs.invalid();
            String scope=params.getOrDefault("fishpi.scope","profile.read");
            if(scope.isBlank())scope="profile.read";
            var granted=new java.util.HashSet<String>();
            for (String item : scope.trim().split("[ ,]+")) {
                if (!SCOPES.contains(item))
                    throw ApiV3WriteInputs.invalid();
                granted.add(item);
            }
            if(granted.isEmpty())throw ApiV3WriteInputs.invalid();
            // 保留提交时的编码供管理员审核，不解码重建 state 或目标地址。
            return new Submission(name,description,url,icon,login);
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            throw ApiV3WriteInputs.invalid();
        }
    }
    private static String text(JSONObject body,String key,int max) {
        String value=Jsoup.clean(ApiV3WriteInputs.string(body,key,16384,true),Safelist.none()).trim();
        if (value.isEmpty() || value.length() > max)
            throw ApiV3WriteInputs.invalid();
        return value;
    }
    private static void rejectCredentials(URI uri) {
        if(uri.getRawQuery()==null)return;
        for(String pair:uri.getRawQuery().split("&",-1)) {
            String key=decode(pair.split("=",2)[0]).toLowerCase(Locale.ROOT);
            if(Set.of("token","password","userpassword","access_token","refresh_token","apikey","secret").contains(key))throw ApiV3WriteInputs.invalid();
        }
    }
    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
    private static URI https(String value,int max) {
        try {
            if(value==null||value.isEmpty()||value.length()>max||value.codePoints().anyMatch(Character::isISOControl))throw ApiV3WriteInputs.invalid();
            URI uri=new URI(value);
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getHost().isBlank()||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null
                    ||uri.getPort()==0||uri.getPort()>65535||uri.getHost().endsWith("."))throw ApiV3WriteInputs.invalid();
            return uri;
        } catch (ApiV3Exception e) {
            throw e;
        } catch (Exception e) {
            throw ApiV3WriteInputs.invalid();
        }
    }

    private static String origin(URI value) {
        return value.getScheme().toLowerCase(Locale.ROOT) + "://" + value.getHost().toLowerCase(Locale.ROOT)
                + ":" + (value.getPort() == -1 ? 443 : value.getPort());
    }
}
