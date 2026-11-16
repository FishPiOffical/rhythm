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

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

/** 气泡只接受固定样式字段，公开快照可脱离当前目录独立渲染。 */
public final class ApiV3BubbleStyle {
    private static final Pattern ID = Pattern.compile("bubble-custom-[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern COLOR = Pattern.compile("#[a-fA-F0-9]{6}");
    private static final Set<String> MOTIFS = Set.of("none", "paw", "leaf", "bell", "fish");
    private static final Set<String> IMAGE_HOSTS = Set.of("file.fishpi.cn", "pic.fishpi.cn");
    private static final Set<String> PALETTE = Set.of("background", "ownBackground", "border", "ink", "text");
    private static final Set<String> FIELDS = Set.of("light", "dark", "radius", "borderWidth", "shadow", "motif", "image");

    private ApiV3BubbleStyle() {
    }

    public static boolean customId(String id) {
        return id != null && id.length() <= 32 && ID.matcher(id).matches();
    }

    public static JSONObject validate(JSONObject input) {
        if (input == null || !input.keySet().equals(FIELDS) || !(input.opt("shadow") instanceof Boolean shadow)
                || !(input.opt("motif") instanceof String motif) || !MOTIFS.contains(motif)
                || !(input.opt("image") instanceof String image) || !safeImage(image)) throw ApiV3WriteInputs.invalid();
        return new JSONObject().put("light", palette(input.optJSONObject("light"))).put("dark", palette(input.optJSONObject("dark")))
                .put("radius", integer(input, "radius", 6, 24)).put("borderWidth", integer(input, "borderWidth", 0, 2))
                .put("shadow", shadow).put("motif", motif).put("image", image);
    }

    private static JSONObject palette(JSONObject input) {
        if (input == null || !input.keySet().equals(PALETTE)) throw ApiV3WriteInputs.invalid();
        final JSONObject result = new JSONObject();
        for (final String field : PALETTE) {
            if (!(input.opt(field) instanceof String color) || !COLOR.matcher(color).matches()) throw ApiV3WriteInputs.invalid();
            result.put(field, color);
        }
        if (contrast(result.getString("background"), result.getString("text")) < 4.5
                || contrast(result.getString("ownBackground"), result.getString("text")) < 4.5) {
            throw new ApiV3Exception(422, "bubble_contrast", "气泡文字与背景对比度至少应为 4.5:1");
        }
        return result;
    }

    private static double contrast(String a, String b) {
        final double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }

    private static double luminance(String hex) {
        final int color = Integer.parseInt(hex.substring(1), 16);
        return .2126 * linear(color >> 16) + .7152 * linear(color >> 8 & 255) + .0722 * linear(color & 255);
    }

    private static double linear(int color) {
        final double channel = color / 255.0;
        return channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }

    private static boolean safeImage(String image) {
        if (image.isEmpty()) return true;
        if (image.length() > 512) return false;
        try {
            final URI url = URI.create(image);
            return "https".equals(url.getScheme()) && url.getHost() != null && IMAGE_HOSTS.contains(url.getHost())
                    && url.getPort() == -1 && url.getRawUserInfo() == null && url.getRawQuery() == null
                    && url.getRawFragment() == null && url.getRawPath().matches("/[a-zA-Z0-9/_-][a-zA-Z0-9/_.-]*[.](png|webp|jpg|jpeg|avif)")
                    && !url.getRawPath().contains("..");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static int integer(JSONObject value, String field, int min, int max) {
        if (!(value.opt(field) instanceof Number n) || n.doubleValue() != n.intValue()
                || n.intValue() < min || n.intValue() > max) throw ApiV3WriteInputs.invalid();
        return n.intValue();
    }
}
