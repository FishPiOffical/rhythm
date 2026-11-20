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

import org.b3log.latke.Latkes;
import org.b3log.latke.http.Cookie;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** 设备 Cookie 只标记浏览器，既不是登录凭据，也不能用来证明当前用户。 */
public final class WebsiteDevice {
    private static final String SECURE_NAME = "__Host-fp_web_device";
    private static final String LOCAL_NAME = "fp_web_device";
    private static final Pattern PROOF = Pattern.compile("[a-f0-9]{32}(?:\\.[a-f0-9]{32}\\.[a-f0-9]{32})?\\.[a-f0-9]{64}");
    private static final Pattern HINT = Pattern.compile("(?i)[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}");
    private final String nonce;
    private final String label;
    private final String boundActor;
    private final String boundId;
    private final boolean fresh;

    private WebsiteDevice(String nonce, String label, String boundActor, String boundId, boolean fresh) {
        this.nonce = nonce;
        this.label = label;
        this.boundActor = boundActor;
        this.boundId = boundId;
        this.fresh = fresh;
    }

    public static boolean validHint(Object value) {
        return value instanceof String hint && HINT.matcher(hint).matches();
    }

    public static boolean cookieName(String name) {
        return SECURE_NAME.equals(name) || LOCAL_NAME.equals(name);
    }

    private static String sign(String value) {
        try {
            final var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Symphonys.COOKIE_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("website-device-v1:" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("设备标识签名失败", e);
        }
    }

    private static boolean secure() {
        return "https".equalsIgnoreCase(Latkes.getServerScheme());
    }

    public static WebsiteDevice from(RequestContext context, String hint) {
        final String name = secure() ? SECURE_NAME : LOCAL_NAME;
        final String label = describe(context.header("User-Agent"));
        for (Cookie cookie : context.getRequest().getCookies()) {
            if (!name.equals(cookie.getName())) continue;
            final String value = cookie.getValue();
            if (value == null || !PROOF.matcher(value).matches()) break;
            final int lastDot = value.lastIndexOf('.');
            final String payload = value.substring(0, lastDot);
            if (!MessageDigest.isEqual(sign("cookie:" + payload).getBytes(StandardCharsets.US_ASCII),
                    value.substring(lastDot + 1).getBytes(StandardCharsets.US_ASCII))) break;
            final String[] fields = payload.split("\\.");
            return new WebsiteDevice(fields[0], label, fields.length == 3 ? fields[1] : "",
                    fields.length == 3 ? fields[2] : "", false);
        }
        // 即使两个客户端提交同一个 deviceId，也不能借此合并或认领另一台设备。
        return new WebsiteDevice(sign("new:" + ApiV3TokenUtil.generate() + ":" + (hint == null ? "" : hint)).substring(0, 32), label, "", "", true);
    }

    public String id(String actor) {
        if (!boundActor.isEmpty() && boundActor.equals(sign("owner:" + actor).substring(0, 32))) return boundId;
        return sign("account:" + actor + ":" + nonce).substring(0, 32);
    }

    public String label() {
        return label;
    }

    public boolean needsCookie(String actor, String verifiedId) {
        return fresh || !id(actor).equals(verifiedId);
    }

    public void attach(Response response, String actor, String verifiedId) {
        // 设备 Cookie 单独过期时，只能用已验证的同账号家族恢复原来的分组。
        final boolean restore = !id(actor).equals(verifiedId);
        final String owner = restore ? sign("owner:" + actor).substring(0, 32) : boundActor;
        final String group = restore ? verifiedId : boundId;
        final String payload = owner.isEmpty() ? nonce : nonce + "." + owner + "." + group;
        final boolean secure = secure();
        final var cookie = new Cookie(secure ? SECURE_NAME : LOCAL_NAME, payload + "." + sign("cookie:" + payload));
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setSameSite("Lax");
        cookie.setMaxAge(365 * 24 * 60 * 60);
        response.addCookie(cookie);
    }

    private static String describe(String agent) {
        String ua = agent == null ? "" : agent.substring(0, Math.min(agent.length(), 1024)).toLowerCase(Locale.ROOT);
        String browser = "浏览器";
        if (ua.contains("edg/") || ua.contains("edgios/") || ua.contains("edga/")) browser = "Edge";
        else if (ua.contains("opr/")) browser = "Opera";
        else if (ua.contains("firefox/") || ua.contains("fxios/")) browser = "Firefox";
        else if (ua.contains("chrome/") || ua.contains("crios/")) browser = "Chrome";
        else if (ua.contains("safari/")) browser = "Safari";
        String system = "";
        if (ua.contains("iphone")) system = "iPhone";
        else if (ua.contains("ipad")) system = "iPad";
        else if (ua.contains("android")) system = "Android";
        else if (ua.contains("windows")) system = "Windows";
        else if (ua.contains("macintosh")) system = "macOS";
        else if (ua.contains("linux")) system = "Linux";
        return system.isEmpty() ? browser : browser + " · " + system;
    }
}
