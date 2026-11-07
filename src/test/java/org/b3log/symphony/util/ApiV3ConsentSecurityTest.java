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

import org.testng.Assert;
import java.util.*;

public class ApiV3ConsentSecurityTest {
    public static void main(String[] args){
        String site="https://fishpi.cn/community";
        Assert.assertTrue(ApiV3ConsentSecurity.sameOrigin(site,"https://fishpi.cn",null,"same-origin"));
        Assert.assertTrue(ApiV3ConsentSecurity.sameOrigin(site,"https://FISHPI.cn:443",null,null));
        Assert.assertTrue(ApiV3ConsentSecurity.sameOrigin(site,null,"https://fishpi.cn/community/oauth?client=example",null));
        Assert.assertTrue(ApiV3ConsentSecurity.sameOrigin("http://localhost:8080","http://localhost:8080",null,"same-origin"));
        for(String origin:Arrays.asList("null","","http://fishpi.cn","https://fishpi.cn.evil","https://evil@fishpi.cn","https://fishpi.cn:444","https://fishpi.cn/","https://fishpi.cn?x=1","https://fishpi.cn#fragment","https://fishpi.cn https://evil","https://fishpi.cn:\n443"))Assert.assertFalse(ApiV3ConsentSecurity.sameOrigin(site,origin,"https://fishpi.cn/safe",null),String.valueOf(origin));
        for(String referer:Arrays.asList(null,"","https://fishpi.cn.evil/path","http://fishpi.cn/path","https://fishpi.cn:444/path","https://evil@fishpi.cn/path","https://fishpi.cn/path#fragment"))Assert.assertFalse(ApiV3ConsentSecurity.sameOrigin(site,null,referer,null),String.valueOf(referer));
        Assert.assertFalse(ApiV3ConsentSecurity.sameOrigin(site,"https://fishpi.cn",null,"cross-site"));Assert.assertFalse(ApiV3ConsentSecurity.sameOrigin(site,"https://fishpi.cn",null,"same-site"));
        Assert.assertFalse(ApiV3ConsentSecurity.sameOrigin("https://fishpi.cn.evil","https://fishpi.cn",null,null));
        System.out.println("PASS cookie consent origin checks use configured scheme/host/effective-port and fail closed for prefix hosts, credentials, paths, malformed/null origins and cross-site fetches");
        Assert.assertTrue(ApiV3ConsentSecurity.validCsrf("expected-token","expected-token"));
        for(String value:Arrays.asList(null,"","expected-token ","EXPECTED-TOKEN","other","x".repeat(257)))Assert.assertFalse(ApiV3ConsentSecurity.validCsrf("expected-token",value));
        Assert.assertFalse(ApiV3ConsentSecurity.validCsrf("\uD800","\uD801"));
        Assert.assertFalse(ApiV3ConsentSecurity.validCsrf("",""));Assert.assertFalse(ApiV3ConsentSecurity.validCsrf(null,"expected-token"));
        System.out.println("PASS consent CSRF requires a nonempty current-session token and compares the bounded original value without trim or identity fallback");
    }
}
