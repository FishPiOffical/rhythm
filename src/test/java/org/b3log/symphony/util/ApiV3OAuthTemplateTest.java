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

import freemarker.template.*;
import org.testng.Assert;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.*;

/** 用包含恶意内容的公开字段渲染两套实际模板，并输出隔离的浏览器测试夹具。 */
public class ApiV3OAuthTemplateTest {
    public static void main(String[] args)throws Exception {
        Path out=Path.of("/tmp/rhythm-v3-oauth-http-fixture");Files.createDirectories(out);
        var cfg=new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);cfg.setDefaultEncoding("UTF-8");
        for(String skin:List.of("pc","mobile"))for(boolean logged:List.of(false,true)){
            var source=Files.readString(Path.of("src/main/resources/skins/classic/"+skin+"/verify/oauth-consent.ftl"));var template=new Template("consent",new StringReader(source),cfg);
            Map<String,Object> model=new HashMap<>();model.put("oauthStyle","/oauth-consent.css");model.put("oauthHome","https://example.invalid");model.put("oauthLoggedIn",logged);model.put("oauthLogin","https://example.invalid/login?goto=%2Fauthorize");
            model.put("oauthUser","<script>window.executed=true</script>昵称");model.put("oauthClient","<img src=x onerror=window.executed=true> 应用 & App");model.put("oauthRedirect","com.example.app:/回调?test=1&other=2");
            model.put("oauthPermissions",List.of(Map.of("key","content.read","label","读取你有权访问的文章、评论与聊天室内容"),Map.of("key","<script>bad</script>","label","<svg onload=window.executed=true>测试权限")));
            model.put("oauthRequest","request\"/><img src=x>");model.put("oauthProof","proof<&\"");model.put("oauthCsrf","csrf<&\"");model.put("oauthAction","https://example.invalid/api/v3/oauth/consent");model.put("user",Map.of("userPassword","PRIVATE-password","userPhone","PRIVATE-phone"));
            var rendered=new StringWriter();template.process(model,rendered);String html=rendered.toString();Assert.assertTrue(html.contains("name=\"referrer\" content=\"same-origin\""),"Browser POST must retain its same-origin proof without sending referrers to external callbacks");Assert.assertFalse(html.contains("PRIVATE-"));Assert.assertFalse(html.contains("<script>"));Assert.assertFalse(html.contains("<img"));Assert.assertFalse(html.contains("<svg"));
            if(logged){Assert.assertTrue(html.contains("name=\"decision\" value=\"deny\""));Assert.assertTrue(html.contains("name=\"decision\" value=\"approve\""));Assert.assertFalse(html.contains("name=\"scope\""));Assert.assertFalse(html.contains("name=\"userId\""));}else Assert.assertFalse(html.contains("name=\"proof\""));
            Files.writeString(out.resolve(skin+(logged?"-consent":"-login")+".html"),html);
        }
        Files.copy(Path.of("src/main/resources/css/oauth-consent.css"),out.resolve("oauth-consent.css"),StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(out.resolve("index.html"),"<!doctype html><meta charset=utf-8><title>OAuth consent fixtures</title><h1>PC / mobile consent</h1><iframe title='PC consent' src='pc-consent.html' width='700' height='800'></iframe><iframe title='Mobile consent' src='mobile-consent.html' width='390' height='800'></iframe><p><a href='pc-login.html'>Login state</a></p>");
        System.out.println("PASS actual PC/mobile consent and login templates render safe public values, keep only request/proof/CSRF/explicit decision in forms, omit sensitive user objects and generate real browser fixtures");
    }
}
