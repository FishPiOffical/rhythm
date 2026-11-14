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
import java.util.regex.Pattern;

/**
 * Client user agent visit source detection.
 *
 * @author Zephyr
 * @since 3.9.0
 */
public final class ClientVisitSources {

    private static final char KEY_SEPARATOR = ':';
    private static final Pattern QQ_APP_TOKEN = Pattern.compile("(?:^|[\\s;(])qq/\\d");
    private static final Pattern WECOM_TOKEN = Pattern.compile("(?:^|[\\s;(])wxwork/\\d");
    private static final Pattern OPENHARMONY_TOKEN = Pattern.compile("(?:^|[\\s;(])openharmony(?=[\\s/;)]|$)");
    private static final Pattern HARMONYOS_TOKEN = Pattern.compile("(?:^|[\\s;(])harmonyos(?=[\\s/;)]|$)");
    private static final Pattern LINUX_TOKEN = Pattern.compile("(?:^|[\\s;(])linux(?=[\\s/;)]|$)");

    private ClientVisitSources() {
    }

    public enum Source {
        CHROME("browser:chrome", "Chrome", "visitSourceChrome"),
        SAFARI("browser:safari", "Safari", "visitSourceSafari"),
        EDGE("browser:edge", "Edge", "visitSourceEdge"),
        FIREFOX("browser:firefox", "Firefox", "visitSourceFirefox"),
        OPERA("browser:opera", "Opera", "visitSourceOpera"),
        SAMSUNG("browser:samsung", "三星浏览器", "visitSourceSamsung"),
        QQ_BROWSER("browser:qq-browser", "QQ浏览器", "visitSourceQQBrowser"),
        QUARK("browser:quark", "夸克", "visitSourceQuark"),
        UC("browser:uc", "UC浏览器", "visitSourceUC"),
        HUAWEI("browser:huawei", "华为浏览器", "visitSourceHuawei"),
        HONOR("browser:honor", "荣耀浏览器", "visitSourceHonor"),
        MIUI("browser:miui", "小米浏览器", "visitSourceMiui"),
        VIVO("browser:vivo", "vivo浏览器", "visitSourceVivo"),
        HEYTAP("browser:heytap", "欢太浏览器", "visitSourceHeyTap"),
        YANDEX("browser:yandex-browser", "Yandex浏览器", "visitSourceYandexBrowser"),
        VIVALDI("browser:vivaldi", "Vivaldi", "visitSourceVivaldi"),
        WECHAT("browser:wechat", "微信", "visitSourceWechat"),
        WECOM("browser:wecom", "企业微信", "visitSourceWecom"),
        QQ_APP("browser:qq-app", "QQ", "visitSourceQQApp"),
        BAIDU_APP("browser:baidu-app", "百度App", "visitSourceBaiduApp"),
        IPHONE("device:iphone", "iPhone", "visitSourceIphone"),
        IPAD("device:ipad", "iPad", "visitSourceIpad"),
        MACOS("device:macos", "macOS", "visitSourceMacos"),
        OPENHARMONY("device:openharmony", "OpenHarmony", "visitSourceOpenHarmony"),
        HARMONYOS("device:harmonyos", "HarmonyOS", "visitSourceHarmonyOS"),
        LINUX("device:linux", "Linux", "visitSourceLinux"),
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
        // 应用宿主和专属浏览器优先，不能被兼容 UA 中的 Chrome/Safari 提前归类。
        if (WECOM_TOKEN.matcher(ua).find()) {
            return Source.WECOM;
        }
        if (ua.contains("micromessenger/")) {
            return Source.WECHAT;
        }
        if (QQ_APP_TOKEN.matcher(ua).find()) {
            return Source.QQ_APP;
        }
        if (ua.contains("baiduboxapp/")) {
            return Source.BAIDU_APP;
        }
        if (containsAny(ua, "mqqbrowser/", "qqbrowser/")) {
            return Source.QQ_BROWSER;
        }
        if (containsAny(ua, "quark/", "quarkpc/")) {
            return Source.QUARK;
        }
        if (containsAny(ua, "ucbrowser/", "uc browser/", "ucweb/", "ucmobile/")) {
            return Source.UC;
        }
        if (ua.contains("huaweibrowser/")) {
            return Source.HUAWEI;
        }
        if (ua.contains("honorbrowser/")) {
            return Source.HONOR;
        }
        if (ua.contains("miuibrowser/")) {
            return Source.MIUI;
        }
        if (ua.contains("vivobrowser/")) {
            return Source.VIVO;
        }
        if (ua.contains("heytapbrowser/")) {
            return Source.HEYTAP;
        }
        if (ua.contains("yabrowser/")) {
            return Source.YANDEX;
        }
        if (ua.contains("vivaldi/")) {
            return Source.VIVALDI;
        }
        if (containsAny(ua, "edg/", "edge/", "edgios/", "edga/")) {
            return Source.EDGE;
        }
        if (containsAny(ua, "firefox/", "fxios/")) {
            return Source.FIREFOX;
        }
        if (containsAny(ua, "opr/", "opera/", "opera mini/", "opera mobi/", "opios/", "opt/")) {
            return Source.OPERA;
        }
        if (ua.contains("samsungbrowser/")) {
            return Source.SAMSUNG;
        }
        if (containsAny(ua, "chrome/", "crios/", "chromium/")) {
            return Source.CHROME;
        }
        if (ua.contains("safari/")) {
            return Source.SAFARI;
        }
        return null;
    }

    private static void appendDevice(final List<Source> ret, final String ua) {
        // 只认明确系统名；ArkWeb 和浏览器品牌不能证明设备运行哪个系统。
        if (OPENHARMONY_TOKEN.matcher(ua).find()) {
            ret.add(Source.OPENHARMONY);
            return;
        }
        if (HARMONYOS_TOKEN.matcher(ua).find()) {
            ret.add(Source.HARMONYOS);
            return;
        }
        if (ua.contains("ipad")) {
            ret.add(Source.IPAD);
            return;
        }
        // 旧 iPhone key 保留历史计数及 iPod 兼容，不将 like Mac OS X 当作 macOS。
        if (containsAny(ua, "iphone", "ipod")) {
            ret.add(Source.IPHONE);
            return;
        }
        if (ua.contains("android")) {
            ret.add(Source.ANDROID);
            return;
        }
        if (containsAny(ua, "macintosh", "mac os x")) {
            ret.add(Source.MACOS);
            return;
        }
        if (LINUX_TOKEN.matcher(ua).find()) {
            ret.add(Source.LINUX);
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
