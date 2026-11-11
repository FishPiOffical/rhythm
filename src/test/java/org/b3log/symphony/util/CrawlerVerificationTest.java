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
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.naming.CommunicationException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.ServiceUnavailableException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

@Test(singleThreaded = true, timeOut = 60_000)
public class CrawlerVerificationTest {
    private static final String IP = "192.0.2.13";
    private static final SearchEngines.Engine ENGINE = SearchEngines.Engine.BAIDU;

    public void onlyTemporaryDnsFailuresUseShortCache() {
        for (final NamingException exception : List.of(new CommunicationException("private resolver"),
                new ServiceUnavailableException("private resolver"), new NameNotFoundException("private resolver"),
                new NamingException("private resolver"))) {
            for (final boolean forward : List.of(false, true)) {
                final var result = SearchEngineDnsVerifier.verify(IP, ENGINE, (name, attrs) -> {
                    if (forward && attrs[0].equals("PTR")) return List.of("spider.baidu.com");
                    throw exception;
                });
                Assert.assertFalse(result.verified());
                Assert.assertEquals(result.transientFailure(), exception instanceof CommunicationException
                        || exception instanceof ServiceUnavailableException);
                Assert.assertTrue(result.reason().startsWith(forward ? "正向查询异常" : "反向查询异常"));
                Assert.assertFalse(result.toString().contains("private resolver"));
            }
        }
    }

    public void transientCacheExpiresAt15SecondsWithoutReadExtension() {
        final AtomicLong clock = new AtomicLong();
        final Cache<String, SearchEngines.Verification> cache = SearchEngines.newRejectedCache(clock::get);
        final var temporary = new SearchEngines.Verification(ENGINE, false, "临时异常", "", "", 1L, false, true);
        final var mismatch = new SearchEngines.Verification(ENGINE, false, "身份不符", "", "", 1L, false);
        cache.put("temporary", temporary);
        cache.put("mismatch", mismatch);
        clock.set(TimeUnit.SECONDS.toNanos(14));
        Assert.assertSame(cache.getIfPresent("temporary"), temporary);
        clock.set(TimeUnit.SECONDS.toNanos(15));
        Assert.assertNull(cache.getIfPresent("temporary"));
        Assert.assertSame(cache.getIfPresent("mismatch"), mismatch);
        clock.set(TimeUnit.MINUTES.toNanos(10));
        Assert.assertNull(cache.getIfPresent("mismatch"));

        cache.put("updated", mismatch);
        cache.put("updated", temporary);
        clock.addAndGet(TimeUnit.SECONDS.toNanos(15));
        Assert.assertNull(cache.getIfPresent("updated"));
        cache.put("updated", temporary);
        cache.put("updated", mismatch);
        clock.addAndGet(TimeUnit.SECONDS.toNanos(15));
        Assert.assertSame(cache.getIfPresent("updated"), mismatch);
    }

    @SuppressWarnings("unchecked")
    public void expiredTemporaryFailureReverifiesInsteadOfTrustingFailedResult() throws Exception {
        final Field field = SearchEngines.class.getDeclaredField("REJECTED_CRAWLERS");
        field.setAccessible(true);
        final Cache<String, SearchEngines.Verification> cache =
                (Cache<String, SearchEngines.Verification>) field.get(null);
        final String ip = "42.236.12.123";
        final String key = "360:" + ip;
        final var temporary = new SearchEngines.Verification(SearchEngines.Engine.SO360, false,
                "临时异常", "", "", 123L, false, true);
        try {
            cache.put(key, temporary);
            final var cached = SearchEngines.crawlerVerification(ip, "360Spider");
            Assert.assertFalse(cached.verified());
            Assert.assertTrue(cached.cached());
            Assert.assertTrue(cached.transientFailure());
            Assert.assertEquals(cached.checkedAt(), 123L);
            // 到期后走真实官方网段校验，不以缓存失效本身作为放行依据。
            cache.policy().expireVariably().orElseThrow().setExpiresAfter(key, 0, TimeUnit.NANOSECONDS);
            final var recovered = SearchEngines.crawlerVerification(ip, "360Spider");
            Assert.assertTrue(recovered.verified());
            Assert.assertFalse(recovered.cached());
            Assert.assertFalse(recovered.transientFailure());
            Assert.assertEquals(recovered.reason(), "360 官方网段校验通过");
        } finally {
            cache.invalidate(key);
            final Field verifiedField = SearchEngines.class.getDeclaredField("VERIFIED_CRAWLERS");
            verifiedField.setAccessible(true);
            ((Cache<String, SearchEngines.Verification>) verifiedField.get(null)).invalidate(key);
        }
    }

    public void allDnsEnginesQueryOnlyMatchingAddressFamilyWithoutAny() {
        final Map<SearchEngines.Engine, String> hosts = Map.of(
                SearchEngines.Engine.BAIDU, "spider.crawl.baidu.com",
                SearchEngines.Engine.BING, "msnbot.search.msn.com",
                SearchEngines.Engine.GOOGLE, "crawl.googlebot.com",
                SearchEngines.Engine.SOGOU, "spider.sogou.com",
                SearchEngines.Engine.YANDEX, "spider.yandex.com");
        for (final var entry : hosts.entrySet()) {
            for (final String ip : List.of(IP, "2001:db8::1")) {
                final String recordType = ip.contains(":") ? "AAAA" : "A";
                final AtomicInteger calls = new AtomicInteger();
                final var result = SearchEngineDnsVerifier.verify(ip, entry.getKey(), (name, attrs) -> {
                    calls.incrementAndGet();
                    // JNDI 多种类型会合并为 ANY，模拟生产拒绝 ANY 的 DNS。
                    Assert.assertEquals(attrs.length, 1, "每次 DNS 请求必须只有一个记录类型");
                    if (attrs[0].equals("PTR")) return List.of(entry.getValue());
                    Assert.assertEquals(attrs[0], recordType);
                    Assert.assertEquals(name, entry.getValue());
                    return List.of(ip);
                });
                Assert.assertTrue(result.verified(), entry.getKey() + " " + ip);
                Assert.assertEquals(calls.get(), 2);
            }
        }
    }

    public void so360RequiresOfficialRangeAndClaimedIdentityWithoutDns() {
        final List<String> networks = List.of("42.236.10.", "42.236.12.", "42.236.17.",
                "42.236.101.", "180.153.236.", "180.163.220.");
        for (final String network : networks) {
            for (final String suffix : List.of("0", "21", "80", "255")) {
                final String ip = network + suffix;
                final var result = SearchEngines.crawlerVerification(ip, "Mozilla/5.0; 360Spider");
                Assert.assertTrue(result.verified(), ip);
                Assert.assertEquals(result.engine(), SearchEngines.Engine.SO360);
                Assert.assertEquals(result.reason(), "360 官方网段校验通过");
                Assert.assertEquals(result.ptr(), "");
                Assert.assertEquals(result.forward(), "");
                Assert.assertNull(SearchEngines.crawlerVerification(ip, "Mozilla/5.0"));
            }
        }
        for (final String ip : List.of("180.153.235.255", "180.153.237.0", "42.236.11.1",
                "42.236.100.255", "42.236.102.0", "192.0.2.13", "2001:db8::1")) {
            final var result = SearchEngines.crawlerVerification(ip, "360Spider");
            Assert.assertFalse(result.verified(), ip);
            Assert.assertEquals(result.reason(), "未匹配 360 官方网段");
            Assert.assertEquals(result.ptr(), "");
        }
        final var cached = SearchEngines.crawlerVerification("180.153.236.80", "360Spider");
        Assert.assertTrue(cached.verified());
        Assert.assertTrue(cached.cached());
        Assert.assertEquals(SearchEngines.detectCrawler("360Spider-Image"), SearchEngines.Engine.SO360);
        Assert.assertEquals(SearchEngines.detectCrawler("360Spider-Video"), SearchEngines.Engine.SO360);
    }

    public void singleTypeLookupStillRejectsNoDataMismatchAndDnsErrors() {
        for (final String ip : List.of(IP, "2001:db8::1")) {
            for (final String outcome : List.of("empty", "mismatch", "exception")) {
                final var result = SearchEngineDnsVerifier.verify(ip, SearchEngines.Engine.BING, (name, attrs) -> {
                    Assert.assertEquals(attrs.length, 1);
                    if (attrs[0].equals("PTR")) return List.of("msnbot.search.msn.com");
                    Assert.assertEquals(attrs[0], ip.contains(":") ? "AAAA" : "A");
                    if (outcome.equals("exception")) throw new CommunicationException("test timeout");
                    if (outcome.equals("empty")) return List.of();
                    return List.of(ip.contains(":") ? "2001:db8::2" : "192.0.2.14");
                });
                Assert.assertFalse(result.verified());
                Assert.assertEquals(result.reason(), outcome.equals("exception")
                        ? "正向查询异常（CommunicationException）" : "正向解析未匹配来源 IP");
            }
        }
    }

    public void dnsOfficialSuffixCannotBeSpoofedByAppendingAnotherDomain() {
        final Map<SearchEngines.Engine, String> hosts = Map.of(
                SearchEngines.Engine.BAIDU, "spider.baidu.com.fake.test",
                SearchEngines.Engine.BING, "msnbot.search.msn.com.fake.test",
                SearchEngines.Engine.GOOGLE, "crawl.googlebot.com.fake.test",
                SearchEngines.Engine.SOGOU, "spider.sogou.com.fake.test",
                SearchEngines.Engine.YANDEX, "spider.yandex.com.fake.test");
        for (final var entry : hosts.entrySet()) {
            final var result = SearchEngineDnsVerifier.verify(IP, entry.getKey(), (name, attrs) -> {
                Assert.assertEquals(attrs[0], "PTR", "非官方域名不应进入正向查询");
                return List.of(entry.getValue());
            });
            Assert.assertFalse(result.verified());
            Assert.assertEquals(result.reason(), "反向域名后缀不符");
        }
    }

    public void duckDuckGoKeepsExistingLocalRangeVerification() {
        for (final String ua : List.of("DuckDuckBot/1.1", "DuckAssistBot/1.2")) {
            final var allowed = SearchEngines.crawlerVerification("104.43.54.127", ua);
            Assert.assertTrue(allowed.verified());
            Assert.assertEquals(allowed.reason(), "本地网段校验通过");
            Assert.assertFalse(SearchEngines.crawlerVerification(IP, ua).verified());
        }
    }

    public void recordsSuccessAndEveryDnsFailureStage() {
        final var noPtr = SearchEngineDnsVerifier.verify(IP, ENGINE, (name, attrs) -> List.of());
        Assert.assertFalse(noPtr.verified());
        Assert.assertEquals(noPtr.reason(), "反向解析无记录");

        final var suffix = SearchEngineDnsVerifier.verify(IP, ENGINE, (name, attrs) -> List.of("baidu.com.fake.test."));
        Assert.assertFalse(suffix.verified());
        Assert.assertEquals(suffix.reason(), "反向域名后缀不符");
        Assert.assertEquals(suffix.ptr(), "baidu.com.fake.test");

        for (final boolean matches : List.of(false, true)) {
            final var result = SearchEngineDnsVerifier.verify(IP, ENGINE, (name, attrs) ->
                    attrs[0].equals("PTR") ? List.of("BaiduSpider-1.BAIDU.COM.")
                            : List.of(matches ? IP : "192.0.2.14"));
            Assert.assertEquals(result.verified(), matches);
            Assert.assertEquals(result.reason(), matches ? "双向 DNS 校验通过" : "正向解析未匹配来源 IP");
            Assert.assertEquals(result.ptr(), "baiduspider-1.baidu.com");
            Assert.assertTrue(result.forward().contains(matches ? IP : "192.0.2.14"));
            Assert.assertFalse(result.cached());
            Assert.assertTrue(result.checkedAt() > 0);
        }
    }

    public void recordsExceptionsWithoutLeakingResolverConfiguration() {
        for (final boolean forward : List.of(false, true)) {
            final var result = SearchEngineDnsVerifier.verify(IP, ENGINE, (name, attrs) -> {
                if (forward && attrs[0].equals("PTR")) return List.of("spider.baidu.com");
                throw new CommunicationException("resolver-private-value");
            });
            Assert.assertFalse(result.verified());
            Assert.assertEquals(result.reason(), (forward ? "正向查询" : "反向查询") + "异常（CommunicationException）");
            Assert.assertFalse(result.toString().contains("resolver-private-value"));
            Assert.assertEquals(result.ptr(), forward ? "spider.baidu.com" : "");
        }
    }

    public void preservesLookupOrderAndStopsOnOriginalFailureOrSuccess() {
        final AtomicInteger calls = new AtomicInteger();
        final var result = SearchEngineDnsVerifier.verify(IP, ENGINE, (name, attrs) -> {
            calls.incrementAndGet();
            if (attrs[0].equals("PTR")) return List.of("not-baidu.test", "spider.baidu.jp", "other.baidu.com");
            return List.of(IP);
        });
        Assert.assertTrue(result.verified());
        Assert.assertEquals(calls.get(), 2);
        final var ipv6 = SearchEngineDnsVerifier.verify("2001:db8::1", ENGINE, (name, attrs) -> {
            if (attrs[0].equals("PTR")) {
                Assert.assertTrue(name.endsWith("ip6.arpa"));
                return List.of("spider.baidu.com");
            }
            return List.of("2001:db8:0:0:0:0:0:1");
        });
        Assert.assertTrue(ipv6.verified());
    }

    @SuppressWarnings("unchecked")
    public void cachedResultKeepsOriginalReasonAndTimeWithoutDns() throws Exception {
        for (final boolean success : List.of(false, true)) {
            final Field field = SearchEngines.class.getDeclaredField(success ? "VERIFIED_CRAWLERS" : "REJECTED_CRAWLERS");
            field.setAccessible(true);
            final var cache = (Cache<String, SearchEngines.Verification>) field.get(null);
            final String key = "baidu:" + IP;
            final var original = new SearchEngines.Verification(ENGINE, success, "当时原因", "spider.baidu.com", IP, 123L, false);
            cache.put(key, original);
            try {
                final var result = SearchEngines.crawlerVerification(IP, "Baiduspider");
                Assert.assertEquals(result.reason(), original.reason());
                Assert.assertEquals(result.checkedAt(), 123L);
                Assert.assertTrue(result.cached());
                Assert.assertEquals(SearchEngines.isVerifiedCrawler(IP, "Baiduspider"), success);
                Assert.assertEquals(SearchEngines.verifiedCrawler(IP, "Baiduspider"), success ? ENGINE : null);
                Assert.assertFalse(original.cached());
            } finally {
                cache.invalidate(key);
            }
        }
        Assert.assertNull(SearchEngines.crawlerVerification(IP, "Mozilla/5.0"));
        Assert.assertFalse(SearchEngines.crawlerVerification("", "Baiduspider").verified());
    }

    @SuppressWarnings("unchecked")
    public void sourcesHaveIndependentCountsLatestDiagnosticsAndBoundedStorage() throws Exception {
        final Field field = CrawlerMonitor.class.getDeclaredField("GROUPS");
        field.setAccessible(true);
        final Map<?, ?> groups = (Map<?, ?>) field.get(null);
        groups.clear();
        try {
            final var first = new SearchEngines.Verification(ENGINE, false, "反向解析无记录", "", "", 123L, false);
            final var latest = new SearchEngines.Verification(ENGINE, false, "正向解析未匹配来源 IP", "spider.baidu.com", "192.0.2.14", 456L, true);
            CrawlerMonitor.record(first, IP, "/one");
            CrawlerMonitor.record(latest, IP, "/two");
            for (int i = 0; i < 1001; i++) CrawlerMonitor.record(first, "2001:db8::" + Integer.toHexString(i), "/");
            final var snapshot = CrawlerMonitor.snapshot("baidu-unverified");
            final var sources = (List<Map<String, Object>>) snapshot.get("sources");
            Assert.assertEquals(sources.size(), 1000);
            Assert.assertEquals(snapshot.get("sourceOmitted"), 2L);
            final var source = sources.stream().filter(row -> row.get("ip").equals(IP)).findFirst().orElseThrow();
            Assert.assertEquals(source.get("count"), 2L);
            Assert.assertEquals(source.get("reason"), latest.reason());
            Assert.assertEquals(source.get("cached"), true);
            Assert.assertEquals(source.get("ptr"), latest.ptr());
            Assert.assertTrue(((List<?>) CrawlerMonitor.snapshot("invalid").get("sources")).isEmpty());
        } finally {
            groups.clear();
        }
    }

    public void diagnosticTextIsBoundedAndControlCharactersRemoved() {
        final var result = new SearchEngines.Verification(ENGINE, false, "x\ny", "p".repeat(2048), "", 1L, false);
        Assert.assertEquals(result.reason(), "x y");
        Assert.assertEquals(result.ptr().length(), 1025);
    }
}
