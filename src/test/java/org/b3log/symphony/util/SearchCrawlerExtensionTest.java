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
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.naming.CommunicationException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Test(singleThreaded = true, timeOut = 60_000)
public class SearchCrawlerExtensionTest {

    private static final Map<SearchEngines.Engine, String> SEARCH_ROLES = Map.of(
            SearchEngines.Engine.OAI_SEARCH, "OAI-SearchBot",
            SearchEngines.Engine.CLAUDE_SEARCH, "Claude-SearchBot",
            SearchEngines.Engine.PERPLEXITY, "PerplexityBot",
            SearchEngines.Engine.APPLE, "Applebot",
            SearchEngines.Engine.MISTRAL_INDEX, "MistralAI-Index");

    public void recognizesOnlyCompleteSearchProductTokens() {
        for (final var role : SEARCH_ROLES.entrySet()) {
            for (final String ua : List.of(role.getValue(), role.getValue() + "/1.4",
                    "Mozilla/5.0 (compatible; " + role.getValue() + "/2.0; +https://example.test/bot)")) {
                Assert.assertEquals(SearchEngines.detectCrawler(ua), role.getKey());
                Assert.assertEquals(SearchEngines.detectCrawler(ua.toLowerCase(java.util.Locale.ROOT)), role.getKey());
            }
            for (final String ua : List.of("Fake" + role.getValue(), role.getValue() + "-User",
                    role.getValue() + "Extended", "https://example.test/" + role.getValue())) {
                Assert.assertNull(SearchEngines.detectCrawler(ua), ua);
            }
        }
    }

    public void userTrainingAndOtherAppleRolesAreNotTrustedEvenAtOfficialIp() {
        for (final String ua : List.of("GPTBot/1.0", "ChatGPT-User/1.0", "OAI-AdsBot/1.0",
                "ClaudeBot/1.0", "Claude-User/1.0", "Perplexity-User/1.0", "MistralAI-User/1.0",
                "MistralAI-Training/1.0", "Applebot-Extended", "iTMS", "Google-Extended")) {
            Assert.assertNull(SearchEngines.crawlerVerification("216.73.216.1", ua), ua);
        }
    }

    public void everyBundledOfficialPrefixIsAcceptedOnlyForItsSearchRole() throws Exception {
        for (final var entry : Map.of(
                SearchEngines.Engine.OAI_SEARCH, "oai-search",
                SearchEngines.Engine.CLAUDE_SEARCH, "claude-search",
                SearchEngines.Engine.PERPLEXITY, "perplexity",
                SearchEngines.Engine.MISTRAL_INDEX, "mistral-index").entrySet()) {
            try (var in = getClass().getResourceAsStream("/search-engines/" + entry.getValue() + "-bot-prefixes.json")) {
                Assert.assertNotNull(in);
                final var prefixes = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                        .getJSONArray("prefixes");
                Assert.assertTrue(prefixes.length() > 0);
                for (int i = 0; i < prefixes.length(); i++) {
                    final var prefix = prefixes.getJSONObject(i);
                    final String cidr = prefix.optString("ipv4Prefix", prefix.optString("ipv6Prefix"));
                    final String ip = cidr.substring(0, cidr.indexOf('/'));
                    final String ua = SEARCH_ROLES.get(entry.getKey()) + "/1.0";
                    final var result = SearchEngines.crawlerVerification(ip, ua);
                    Assert.assertTrue(result.verified(), entry.getKey() + " " + cidr);
                    Assert.assertEquals(result.engine(), entry.getKey());
                    Assert.assertEquals(result.reason(), "官方网段校验通过");
                    Assert.assertEquals(result.ptr(), "");
                    Assert.assertEquals(result.forward(), "");
                    final var cached = SearchEngines.crawlerVerification(ip, ua);
                    Assert.assertTrue(cached.cached());
                    Assert.assertEquals(cached.checkedAt(), result.checkedAt());
                    Assert.assertNull(SearchEngines.crawlerVerification(ip, "Mozilla/5.0"));
                }
            }
        }
    }

    public void cidrBoundariesAddressFamiliesAndIndependentRolesAreEnforced() {
        for (final String ip : List.of("104.210.140.128", "104.210.140.143")) {
            Assert.assertTrue(SearchEngineBotIpRanges.containsSearchCrawler(ip, SearchEngines.Engine.OAI_SEARCH));
        }
        for (final String ip : List.of("104.210.140.127", "104.210.140.144", "192.0.2.13",
                "2001:db8::1", "example.test", "", "999.1.1.1")) {
            Assert.assertFalse(SearchEngineBotIpRanges.containsSearchCrawler(ip, SearchEngines.Engine.OAI_SEARCH), ip);
        }
        for (final var role : SEARCH_ROLES.entrySet()) {
            if (role.getKey() == SearchEngines.Engine.APPLE) continue;
            Assert.assertFalse(SearchEngines.crawlerVerification("192.0.2.13", role.getValue()).verified());
            Assert.assertFalse(SearchEngines.crawlerVerification("2001:db8::1", role.getValue()).verified());
        }
        // 不能把其他厂商列表、用户抓取列表或可信缓存串到当前角色。
        Assert.assertFalse(SearchEngines.crawlerVerification("135.225.57.92", "OAI-SearchBot").verified());
        Assert.assertFalse(SearchEngines.crawlerVerification("104.210.140.128", "MistralAI-Index").verified());
    }

    public void appleUsesBidirectionalDnsAndNeverAny() {
        for (final String ip : List.of("17.58.101.179", "2001:db8::17")) {
            final var result = SearchEngineDnsVerifier.verify(ip, SearchEngines.Engine.APPLE, (name, attrs) -> {
                Assert.assertEquals(attrs.length, 1);
                if (attrs[0].equals("PTR")) return List.of("17-58-101-179.Applebot.Apple.Com.");
                Assert.assertEquals(attrs[0], ip.contains(":") ? "AAAA" : "A");
                Assert.assertEquals(name, "17-58-101-179.applebot.apple.com");
                return List.of(ip);
            });
            Assert.assertTrue(result.verified());
            Assert.assertEquals(result.reason(), "双向 DNS 校验通过");
        }
    }

    public void cidrMatcherSupportsFutureOfficialIpv6ListsWithoutBroadeningAddressFamilies() throws Exception {
        final var type = Class.forName(SearchEngineBotIpRanges.class.getName() + "$CidrBlock");
        final var parse = type.getDeclaredMethod("parse", String.class);
        final var contains = type.getDeclaredMethod("contains", java.net.InetAddress.class);
        parse.setAccessible(true);
        contains.setAccessible(true);
        final Object block = parse.invoke(null, "2001:db8:1234::/48");
        for (final String ip : List.of("2001:db8:1234::", "2001:db8:1234:ffff:ffff:ffff:ffff:ffff")) {
            Assert.assertEquals(contains.invoke(block, java.net.InetAddress.getByName(ip)), true);
        }
        for (final String ip : List.of("2001:db8:1233:ffff:ffff:ffff:ffff:ffff", "2001:db8:1235::", "192.0.2.1")) {
            Assert.assertEquals(contains.invoke(block, java.net.InetAddress.getByName(ip)), false);
        }
    }

    public void appleRejectsWrongDomainForwardMismatchAndDnsFailures() {
        for (final String host : List.of("applebot.apple.com.fake.test", "applebot-apple.com", "crawler.apple.com")) {
            final var result = SearchEngineDnsVerifier.verify("17.58.101.179", SearchEngines.Engine.APPLE, (name, attrs) -> {
                Assert.assertEquals(attrs[0], "PTR");
                return List.of(host);
            });
            Assert.assertFalse(result.verified());
            Assert.assertEquals(result.reason(), "反向域名后缀不符");
        }
        for (final String outcome : List.of("empty", "mismatch", "ptrError", "forwardError")) {
            final var result = SearchEngineDnsVerifier.verify("17.58.101.179", SearchEngines.Engine.APPLE, (name, attrs) -> {
                if (outcome.equals("ptrError")) throw new CommunicationException("private resolver");
                if (attrs[0].equals("PTR")) return List.of("crawler.applebot.apple.com");
                if (outcome.equals("forwardError")) throw new CommunicationException("private resolver");
                return outcome.equals("empty") ? List.of() : List.of("17.58.101.180");
            });
            Assert.assertFalse(result.verified());
            Assert.assertFalse(result.toString().contains("private resolver"));
            if (outcome.endsWith("Error")) Assert.assertTrue(result.reason().contains("CommunicationException"));
        }
    }

    public void refererDetectionDoesNotInferNewCrawlerIdentities() {
        for (final String url : List.of("https://chatgpt.com/", "https://claude.ai/", "https://perplexity.ai/",
                "https://apple.com/", "https://mistral.ai/")) {
            Assert.assertNull(SearchEngines.detectReferer(url));
        }
    }

    public void newSearchRolesKeepSeparateVerifiedAndRejectedMonitorDetails() throws Exception {
        final var field = CrawlerMonitor.class.getDeclaredField("GROUPS");
        field.setAccessible(true);
        final Map<?, ?> groups = (Map<?, ?>) field.get(null);
        groups.clear();
        try {
            for (final var role : SEARCH_ROLES.entrySet()) {
                for (final boolean verified : List.of(true, false)) {
                    CrawlerMonitor.record(new SearchEngines.Verification(role.getKey(), verified, "诊断原因",
                            "", "", 123L, false), "192.0.2.13", "/article/1?apiKey=private");
                    final var snapshot = CrawlerMonitor.snapshot(role.getKey().key()
                            + (verified ? "-verified" : "-unverified"));
                    Assert.assertEquals(((List<?>) snapshot.get("sources")).size(), 1);
                    Assert.assertEquals(((List<?>) snapshot.get("paths")).size(), 1);
                    Assert.assertFalse(snapshot.toString().contains("private"));
                }
            }
            Assert.assertEquals(((List<?>) CrawlerMonitor.snapshot("").get("groups")).size(), 10);
        } finally {
            groups.clear();
        }
    }
}
