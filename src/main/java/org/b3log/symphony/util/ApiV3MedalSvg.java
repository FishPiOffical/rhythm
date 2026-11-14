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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;

/**
 * 仅允许有限的 SVG 图形元素和属性，拒绝 HTML、脚本、CSS 规则、外部资源和导航。
 */
public final class ApiV3MedalSvg {
    public static final int MAX_BYTES = 512 * 1024;
    private static final String NS = "http://www.w3.org/2000/svg";
    private static final Set<String> ELEMENTS = Set.of("svg", "g", "defs", "title", "desc", "rect", "path", "circle", "ellipse", "line", "polyline", "polygon", "text", "tspan", "image", "linearGradient", "radialGradient", "stop", "clipPath", "mask", "filter", "feGaussianBlur", "feOffset", "feColorMatrix", "feBlend", "feComposite", "feMerge", "feMergeNode", "animate", "animateTransform");
    private static final Set<String> ATTRS = Set.of("id", "x", "y", "x1", "x2", "y1", "y2", "cx", "cy", "r", "rx", "ry", "width", "height", "viewBox", "preserveAspectRatio", "d", "points", "transform", "opacity", "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-opacity", "stroke-linecap", "stroke-linejoin", "stroke-dasharray", "stroke-dashoffset", "font-family", "font-size", "font-weight", "text-anchor", "dominant-baseline", "letter-spacing", "dx", "dy", "offset", "stop-color", "stop-opacity", "gradientUnits", "gradientTransform", "spreadMethod", "clip-path", "clipPathUnits", "mask", "maskUnits", "filter", "filterUnits", "in", "in2", "result", "stdDeviation", "mode", "operator", "k1", "k2", "k3", "k4", "values", "type", "color-interpolation-filters");
    private static final Set<String> ANIMATED = Set.of("fill", "stroke", "opacity", "fill-opacity", "stroke-opacity", "transform", "x", "y", "cx", "cy", "r", "width", "height", "offset", "stop-color", "stop-opacity", "stroke-dashoffset");
    private static final Set<String> ANIMATION = Set.of("attributeName", "from", "to", "by", "values", "dur", "begin", "repeatCount", "repeatDur", "calcMode", "keyTimes", "keySplines", "additive", "accumulate", "type", "fill");

    private ApiV3MedalSvg() {
    }

    public static byte[] clean(byte[] raw) {
        if (raw == null || raw.length == 0 || raw.length > MAX_BYTES) throw bad();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "64");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            var doc = builder.parse(new ByteArrayInputStream(raw));
            var root = doc.getDocumentElement();
            if (!NS.equals(root.getNamespaceURI()) || !"svg".equals(root.getLocalName())) throw bad();
            // 根元素之外的处理指令和注释也一并丢弃。
            for (Node n = doc.getFirstChild(); n != null; ) {
                Node next = n.getNextSibling();
                if (n != root)
                    doc.removeChild(n);
                n = next;
            }
            ApiV3LegacyMedalSvg.convert(root);
            int[] count = {0};
            cleanElement(root, 0, count);
            root.setAttribute("xmlns", NS);
            var tf = TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = tf.newTransformer();
            transformer.setOutputProperty("omit-xml-declaration", "yes");
            var output = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(output));
            if (output.size() > MAX_BYTES)
                throw bad();
            return output.toByteArray();
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw bad();
        }
    }

    private static void cleanElement(Element e, int depth, int[] count) {
        if (depth > 32 || ++count[0] > 2000) throw bad();
        final boolean animated = e.getLocalName().startsWith("animate");
        if (animated && !ANIMATED.contains(e.getAttribute("attributeName"))) {
            e.getParentNode().removeChild(e);
            return;
        }
        var attrs = e.getAttributes();
        for (int i = attrs.getLength() - 1; i >= 0; i--) {
            var a = (Attr) attrs.item(i);
            String key = a.getName(), value = a.getValue();
            boolean allowed;
            if ("style".equals(key) && !animated) {
                // 行内样式逐项校验，只保留允许的属性和值。
                StringBuilder style = new StringBuilder();
                for (String declaration : value.split(";")) {
                    String[] parts = declaration.split(":", 2);
                    if (parts.length == 2 && ATTRS.contains(parts[0].trim()) && safeValue(parts[0].trim(), parts[1].trim()))
                        style.append(parts[0].trim()).append(':').append(parts[1].trim()).append(';');
                }
                if (style.length() > 0)
                    a.setValue(style.toString());
                else
                    e.removeAttributeNode(a);
                continue;
            }
            if ("href".equals(key) || "xlink:href".equals(key))
                allowed = "image".equals(e.getLocalName()) && dataImage(value);
            else
                allowed = a.getNamespaceURI() == null && (animated ? ANIMATION.contains(key) : ATTRS.contains(key)) && safeValue(key, value);
            if (!allowed) e.removeAttributeNode(a);
        }
        for (Node n = e.getFirstChild(); n != null; ) {
            Node next = n.getNextSibling();
            if (n instanceof Element child) {
                if (NS.equals(child.getNamespaceURI()) && ELEMENTS.contains(child.getLocalName()))
                    cleanElement(child, depth + 1, count);
                else
                    e.removeChild(n);
            } else if (n.getNodeType() != Node.TEXT_NODE)
                e.removeChild(n);
            n = next;
        }
    }

    static boolean dataImage(String value) {
        if (value.length() > 350000 || !value.matches("data:image/(png|jpeg|gif|webp);base64,[A-Za-z0-9+/]+={0,2}"))
            return false;
        try {
            byte[] b = Base64.getDecoder().decode(value.substring(value.indexOf(',') + 1));
            if (b.length < 8) return false;
            final String mime = value.substring(11, value.indexOf(';'));
            return switch (mime) {
                case "png" -> (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71;
                case "jpeg" -> (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
                case "gif" -> b[0] == 71 && b[1] == 73 && b[2] == 70;
                case "webp" -> b.length >= 16 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                        && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
                default -> false;
            };
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean safeValue(String key, String value) {
        if (value.length() > 16384 || value.codePoints().anyMatch(Character::isISOControl) || value.indexOf('\\') >= 0 || value.indexOf('<') >= 0 || value.indexOf('>') >= 0)
            return false;
        if ("id".equals(key)) return value.matches("[A-Za-z_][A-Za-z0-9_-]{0,127}");
        if ("begin".equals(key)) return value.matches("[0-9.]+(ms|s)");
        if ("dur".equals(key) || "repeatDur".equals(key)) return value.matches("[0-9.]+(ms|s)");
        if (Set.of("transform", "gradientTransform").contains(key))
            return value.matches("(?:\\s*(?:matrix|translate|scale|rotate|skewX|skewY)\\([0-9eE+., \\-]+\\)\\s*)+");
        String rest = value.replaceAll("url\\(#[A-Za-z_][A-Za-z0-9_-]{0,127}\\)", "");
        return !rest.contains("(") && !rest.contains(")") && !rest.contains(":") && !rest.contains("/") && !rest.contains("@") && !rest.contains("&") && !rest.contains("=") && !rest.contains("\"") && !rest.contains("'");
    }

    public static ApiV3Exception bad() {
        return new ApiV3Exception(503, "temporarily_unavailable", "勋章图片暂不可用");
    }
}
