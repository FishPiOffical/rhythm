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

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Client user agent visit source detection.
 *
 * @author Zephyr
 * @since 3.9.0
 */
public final class ClientVisitSources {

    private static final char KEY_SEPARATOR = ':';

    private ClientVisitSources() {
    }

    public enum Source {
        CHROME("browser:chrome", "Chrome", "visitSourceChrome"),
        SAFARI("browser:safari", "Safari", "visitSourceSafari"),
        EDGE("browser:edge", "Edge", "visitSourceEdge"),
        FIREFOX("browser:firefox", "Firefox", "visitSourceFirefox"),
        OPERA("browser:opera", "Opera", "visitSourceOpera"),
        SAMSUNG("browser:samsung", "Samsung", "visitSourceSamsung"),
        IPHONE("device:iphone", "iPhone", "visitSourceApple"),
        ANDROID("device:android", "Android", "visitSourceAndroid");

        private final String key;
        private final String displayName;
        private final String icon;

        Source(final String key, final String displayName, final String icon) {
            this.key = key;
            this.displayName = displayName;
            this.icon = icon;
        }

        public String key() {
            return key;
        }

        public String displayName() {
            return displayName;
        }

        public String icon() {
            return icon;
        }

        public String type() {
            return key.substring(0, key.indexOf(KEY_SEPARATOR));
        }

        public String css() {
            return key.substring(key.indexOf(KEY_SEPARATOR) + 1);
        }
    }

    public static List<Source> detect(final String ua) {
        if (StringUtils.isBlank(ua)) {
            return Collections.emptyList();
        }

        final String value = ua.toLowerCase(Locale.ROOT);
        final List<Source> ret = new ArrayList<>();
        final Source browser = detectBrowser(value);
        if (null != browser) {
            ret.add(browser);
        }
        appendDevice(ret, value);
        return ret;
    }

    private static Source detectBrowser(final String ua) {
        if (containsAny(ua, "edg/", "edge/", "edgios", "edga/")) return Source.EDGE;
        if (containsAny(ua, "firefox/", "fxios/")) return Source.FIREFOX;
        if (containsAny(ua, "opr/", "opera/")) return Source.OPERA;
        if (ua.contains("samsungbrowser/")) return Source.SAMSUNG;
        if (containsAny(ua, "chrome/", "crios/", "chromium/")) return Source.CHROME;
        if (ua.contains("safari/")) return Source.SAFARI;
        return null;
    }

    private static void appendDevice(final List<Source> ret, final String ua) {
        if (containsAny(ua, "iphone", "ipad", "ipod")) {
            ret.add(Source.IPHONE);
            return;
        }
        if (ua.contains("android")) {
            ret.add(Source.ANDROID);
        }
    }

    private static boolean containsAny(final String ua, final String... tokens) {
        for (final String token : tokens) {
            if (ua.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
