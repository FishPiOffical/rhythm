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

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** 复用现有极验四代配置和签名协议，单独保留供应商故障与验证失败的区别。 */
@Service
public class ApiV3PetCaptchaService {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public boolean verify(final String proof) {
        try {
            final String captchaId = Symphonys.get("jiyan.id");
            final String secret = Symphonys.get("jiyan.key");
            if (captchaId == null || !captchaId.matches("[A-Za-z0-9_-]{1,128}") || secret == null || secret.isBlank())
                throw unavailable();
            final JSONObject input = new JSONObject(proof);
            final String lot = input.getString("lot_number");
            final String signature = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secret).hmacHex(lot);
            final String form = "lot_number=" + encode(lot) + "&captcha_output=" + encode(input.getString("captcha_output"))
                    + "&pass_token=" + encode(input.getString("pass_token")) + "&gen_time=" + encode(input.getString("gen_time"))
                    + "&sign_token=" + encode(signature);
            // 验证目的地由服务端固定，客户端不能指定网址，也不能经重定向发送证明材料。
            final HttpRequest request = HttpRequest.newBuilder(URI.create("https://gcaptcha4.geetest.com/validate?captcha_id=" + encode(captchaId)))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            // 在读取过程中限制正文大小，截止时间覆盖响应头与正文，避免慢响应占住验证流程。
            final var pending = CLIENT.sendAsync(request, HttpResponse.BodyHandlers.limiting(
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), 16_384));
            final HttpResponse<String> response;
            try {
                response = pending.get(5, TimeUnit.SECONDS);
            } finally {
                if (!pending.isDone()) pending.cancel(true);
            }
            if (response.statusCode() != 200) throw unavailable();
            final String result = new JSONObject(response.body()).optString("result");
            if ("success".equals(result)) return true;
            if ("fail".equals(result)) return false;
            throw unavailable();
        } catch (ApiV3Exception error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception error) {
            // 不记录证明、密钥、供应商正文，也不把通信故障记作用户作弊。
            throw unavailable();
        }
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "验证服务暂不可用，请稍后再试");
    }
}
