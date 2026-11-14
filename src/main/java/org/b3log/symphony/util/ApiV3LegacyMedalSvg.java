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

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 将现网渲染器的勋章模板投影成纯 SVG。原始 HTML 和 CSS 不进入响应。 */
final class ApiV3LegacyMedalSvg {
    private static final String SVG = "http://www.w3.org/2000/svg";
    private static final String HTML = "http://www.w3.org/1999/xhtml";
    private static final Pattern PIXELS = Pattern.compile("([0-9]{1,4}(?:\\.[0-9]{1,3})?)(?:px)?");
    private static final Pattern IMAGE = Pattern.compile("background-image\\s*:\\s*url\\(\\s*['\"]?(data:image/[a-z]+;base64,[A-Za-z0-9+/]+={0,2})['\"]?\\s*\\)");
    private static final String COLOR_TOKEN = "#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})|rgb\\(\\s*([0-9]{1,3})\\s*,\\s*([0-9]{1,3})\\s*,\\s*([0-9]{1,3})\\s*\\)";
    private static final Pattern COLOR = Pattern.compile(COLOR_TOKEN);

    private ApiV3LegacyMedalSvg() {
    }

    static void convert(Element root) {
        final Element frame = child(root, SVG, "foreignObject", "");
        if (frame == null) return;
        // 只转换现网模板的单层画布，混合 SVG 仍走原有清洗规则。
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element && node != frame) return;
        }
        final Element html = child(frame, HTML, "html", "");
        if (html == null) return;
        final Element sheet = child(html, HTML, "style", "");
        final Element container = child(html, HTML, "div", "container");
        if (sheet == null || container == null) return;
        final Element bar = child(container, HTML, "div", "bar");
        final Element icon = child(container, HTML, "div", "img");
        if (bar == null && icon == null) return;

        final double width = pixels(frame.getAttribute("width"), 0);
        final double height = pixels(frame.getAttribute("height"), 0);
        if (width < 8 || width > 2048 || height < 8 || height > 256) return;

        final String css = sheet.getTextContent();
        final String iconStyle = rule(css, "\\.img");
        final String barStyle = rule(css, "\\.bar");
        final var embedded = IMAGE.matcher(iconStyle);
        final String image = icon != null && embedded.find() && ApiV3MedalSvg.dataImage(embedded.group(1))
                ? embedded.group(1) : "";
        final Element label = bar == null ? null : child(bar, HTML, "span", "");
        final String title = label == null ? "" : label.getTextContent().strip();
        if (title.length() > 128 || (title.isEmpty() && image.isEmpty())) return;

        root.removeChild(frame);
        root.setAttribute("viewBox", "0 0 " + width + " " + height);
        root.setAttribute("width", Double.toString(width));
        root.setAttribute("height", Double.toString(height));
        final Element defs = element(root, "defs", Map.of());

        final double iconSize = Math.min(Math.min(width, height) - 2, pixels(property(iconStyle, "width"), height - 4));
        final double iconX = title.isEmpty() ? (width - iconSize) / 2 : 2;
        final double iconY = (height - iconSize) / 2;
        if (!title.isEmpty()) {
            final double barHeight = Math.min(height - 1, pixels(property(barStyle, "height"), height - 4));
            final double barX = image.isEmpty() ? 2 : iconX + iconSize / 2;
            final double fontSize = Math.min(barHeight, pixels(property(barStyle, "font-size"), 12));
            element(root, "rect", Map.of("x", barX, "y", (height - barHeight) / 2, "width", Math.max(0, width - barX - 2),
                    "height", barHeight, "rx", barHeight / 2,
                    "fill", paint(defs, "medal-bar", property(barStyle, "background"), "#58677a")));
            final double textX = image.isEmpty() ? 7 : iconX + iconSize + 6;
            final Element text = element(root, "text", Map.of("x", textX, "y", height / 2,
                    "dominant-baseline", "central", "font-family", "sans-serif", "font-size", fontSize,
                    "font-weight", "700", "fill", paint(defs, "medal-text", property(rule(css, "span"), "background"), "#ffffff")));
            text.setTextContent(title);
        }
        if (!image.isEmpty() && iconSize > 0) {
            final Element clip = element(defs, "clipPath", Map.of("id", "medal-icon"));
            element(clip, "circle", Map.of("cx", iconX + iconSize / 2, "cy", iconY + iconSize / 2, "r", iconSize / 2));
            element(root, "image", Map.of("x", iconX, "y", iconY, "width", iconSize, "height", iconSize, "href", image,
                    "preserveAspectRatio", "xMidYMid slice", "clip-path", "url(#medal-icon)"));
        }
    }

    private static Element child(Element parent, String namespace, String tag, String className) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && namespace.equals(child.getNamespaceURI()) && tag.equals(child.getLocalName())
                    && className.equals(child.getAttribute("class"))) return child;
        }
        return null;
    }

    private static Element element(Element parent, String tag, Map<String, ?> attributes) {
        final Element child = parent.getOwnerDocument().createElementNS(SVG, tag);
        attributes.forEach((key, value) -> child.setAttribute(key, value.toString()));
        parent.appendChild(child);
        return child;
    }

    private static double pixels(String value, double fallback) {
        final var match = PIXELS.matcher(value);
        if (!match.matches()) return fallback;
        final double number = Double.parseDouble(match.group(1));
        return number > 0 ? number : fallback;
    }

    private static String rule(String sheet, String selector) {
        final var match = Pattern.compile("(?:^|[}\\s])" + selector + "\\s*\\{([^{}]*)}").matcher(sheet);
        return match.find() ? match.group(1) : "";
    }

    private static String property(String rule, String name) {
        final var match = Pattern.compile("(?:^|;)\\s*" + name + "\\s*:\\s*([^;]+)").matcher(rule);
        return match.find() ? match.group(1).strip() : "";
    }

    private static String paint(Element defs, String id, String value, String fallback) {
        final boolean gradient = value.startsWith("linear-gradient(") && value.endsWith(")");
        final var match = COLOR.matcher(value);
        if (!gradient && !match.matches()) return fallback;
        final List<String> colors = new ArrayList<>();
        match.reset();
        while (colors.size() < 8 && match.find()) {
            if (match.group(1) == null) {
                colors.add(match.group());
            } else {
                final int r = Integer.parseInt(match.group(1));
                final int g = Integer.parseInt(match.group(2));
                final int b = Integer.parseInt(match.group(3));
                if (r <= 255 && g <= 255 && b <= 255)
                    colors.add(String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b));
            }
        }
        if (colors.isEmpty()) return fallback;
        if (colors.size() == 1) return colors.getFirst();
        final boolean vertical = value.startsWith("linear-gradient(to bottom");
        final Element fill = element(defs, "linearGradient", Map.of("id", id, "x2", vertical ? "0%" : "100%", "y2", vertical ? "100%" : "0%"));
        for (int i = 0; i < colors.size(); i++) {
            element(fill, "stop", Map.of("offset", 100.0 * i / (colors.size() - 1) + "%", "stop-color", colors.get(i)));
        }
        return "url(#" + id + ")";
    }
}
