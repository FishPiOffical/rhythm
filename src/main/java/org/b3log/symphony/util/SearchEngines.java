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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Search engine verification and source detection.
 *
 * @author Zephyr
 * @since 3.9.0
 */
public final class SearchEngines {

    private static final Cache<String, Verification> VERIFIED_CRAWLERS = Caffeine.newBuilder()
            .expireAfterWrite(6, TimeUnit.HOURS).maximumSize(10000).build();

    private static final Cache<String, Verification> REJECTED_CRAWLERS = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(10000).build();

    private SearchEngines() {
    }

    /** 记录实际校验结果；缓存命中保留原校验时间，不为展示再次查询 DNS。 */
    public record Verification(Engine engine, boolean verified, String reason, String ptr, String forward,
                               long checkedAt, boolean cached) {
        public Verification {
            reason = diagnosticText(reason);
            ptr = diagnosticText(ptr);
            forward = diagnosticText(forward);
        }

        private Verification fromCache() {
            return new Verification(engine, verified, reason, ptr, forward, checkedAt, true);
        }

        private static String diagnosticText(final String value) {
            if (value == null) return "";
            final String clean = value.replaceAll("[\\p{Cntrl}]", " ");
            return clean.length() > 1024 ? clean.substring(0, 1024) + "…" : clean;
        }
    }

    public enum Engine {
        GOOGLE("google", "Google"),
        BAIDU("baidu", "百度"),
        BING("bing", "Bing"),
        SOGOU("sogou", "搜狗"),
        YANDEX("yandex", "Yandex"),
        SO360("360", "360"),
        DUCKDUCKGO("duckduckgo", "DuckDuckGo");

        private final String key;
        private final String displayName;

        Engine(final String key, final String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String key() {
            return key;
        }

        public String displayName() {
            return displayName;
        }
    }

    public static Engine detectCrawler(final String ua) {
        if (StringUtils.isBlank(ua)) {
            return null;
        }

        final String value = ua.toLowerCase(Locale.ROOT);
        for (final Engine engine : Engine.values()) {
            if (matchesCrawlerToken(engine, value)) {
                return engine;
            }
        }
        return null;
    }

    public static Engine detectReferer(final String referer) {
        if (StringUtils.isBlank(referer)) {
            return null;
        }

        try {
            final String host = new URI(referer).getHost();
            if (StringUtils.isBlank(host)) {
                return null;
            }
            return detectRefererHost(host.toLowerCase(Locale.ROOT));
        } catch (final Exception ignored) {
            return null;
        }
    }

    public static Engine verifiedCrawler(final String ip, final String ua) {
        final Verification result = crawlerVerification(ip, ua);
        return result != null && result.verified() ? result.engine() : null;
    }

    public static Verification crawlerVerification(final String ip, final String ua) {
        final Engine engine = detectCrawler(ua);
        if (null == engine) {
            return null;
        }
        if (StringUtils.isBlank(ip)) {
            return new Verification(engine, false, "来源 IP 为空", "", "", System.currentTimeMillis(), false);
        }

        final String cacheKey = engine.key() + ':' + ip;
        final Verification verified = VERIFIED_CRAWLERS.getIfPresent(cacheKey);
        if (null != verified) {
            return verified.fromCache();
        }
        final Verification rejected = REJECTED_CRAWLERS.getIfPresent(cacheKey);
        if (null != rejected) {
            return rejected.fromCache();
        }

        final Verification result = verifyCrawler(ip, engine);
        if (result.verified()) {
            VERIFIED_CRAWLERS.put(cacheKey, result);
        } else {
            REJECTED_CRAWLERS.put(cacheKey, result);
        }
        return result;
    }

    public static boolean isVerifiedCrawler(final String ip, final String ua) {
        return null != verifiedCrawler(ip, ua);
    }

    private static Verification verifyCrawler(final String ip, final Engine engine) {
        if (Engine.DUCKDUCKGO == engine) {
            final boolean verified = SearchEngineBotIpRanges.containsDuckDuckGo(ip);
            return new Verification(engine, verified, verified ? "本地网段校验通过" : "本地网段校验未通过",
                    "", "", System.currentTimeMillis(), false);
        }
        if (supportsDnsVerification(engine)) {
            return SearchEngineDnsVerifier.verify(ip, engine);
        }
        return new Verification(engine, false, "未支持此爬虫校验", "", "", System.currentTimeMillis(), false);
    }

    private static boolean supportsDnsVerification(final Engine engine) {
        return Engine.GOOGLE == engine || Engine.BAIDU == engine || Engine.BING == engine
                || Engine.SOGOU == engine || Engine.YANDEX == engine || Engine.SO360 == engine;
    }

    private static Engine detectRefererHost(final String host) {
        if (isGoogleReferer(host)) return Engine.GOOGLE;
        if (isBaiduReferer(host)) return Engine.BAIDU;
        if (hasDnsSuffix(host, "bing.com")) return Engine.BING;
        if (hasDnsSuffix(host, "sogou.com")) return Engine.SOGOU;
        if (isYandexReferer(host)) return Engine.YANDEX;
        if (hasDnsSuffix(host, "so.com") || hasDnsSuffix(host, "360.cn")) return Engine.SO360;
        if (hasDnsSuffix(host, "duckduckgo.com")) return Engine.DUCKDUCKGO;
        return null;
    }

    private static boolean matchesCrawlerToken(final Engine engine, final String ua) {
        switch (engine) {
            case GOOGLE:
                return ua.contains("googlebot");
            case BAIDU:
                return ua.contains("baiduspider");
            case BING:
                return ua.contains("bingbot") || ua.contains("msnbot") || ua.contains("bingpreview");
            case SOGOU:
                return ua.contains("sogou web spider");
            case YANDEX:
                return ua.contains("yandexbot");
            case SO360:
                return ua.contains("360spider");
            case DUCKDUCKGO:
                return ua.contains("duckduckbot") || ua.contains("duckassistbot");
            default:
                return false;
        }
    }

    private static boolean isGoogleReferer(final String host) {
        return hasDnsSuffix(host, "google.com") || hasSearchHostPrefix(host, "google.");
    }

    private static boolean isBaiduReferer(final String host) {
        return hasDnsSuffix(host, "baidu.com") || hasDnsSuffix(host, "baidu.jp")
                || hasSearchHostPrefix(host, "baidu.");
    }

    private static boolean isYandexReferer(final String host) {
        return hasDnsSuffix(host, "yandex.ru") || hasDnsSuffix(host, "yandex.net")
                || hasDnsSuffix(host, "yandex.com") || hasSearchHostPrefix(host, "yandex.");
    }

    static boolean hasDnsSuffix(final String hostname, final String suffix) {
        return hostname.equals(suffix) || hostname.endsWith("." + suffix);
    }

    private static boolean hasSearchHostPrefix(final String host, final String prefix) {
        return host.startsWith("www." + prefix) || host.startsWith("m." + prefix);
    }
}
