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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Search engine crawler IP ranges.
 *
 * @author Zephyr
 * @since 3.9.0
 */
final class SearchEngineBotIpRanges {

    private static final Logger LOGGER = LogManager.getLogger(SearchEngineBotIpRanges.class);
    private static final String DUCKDUCKGO_RESOURCE = "/search-engines/duckduckgo-bot-prefixes.json";
    private static final String SO360_RESOURCE = "/search-engines/360-bot-prefixes.json";
    private static final List<CidrBlock> SO360_RANGES = loadRanges(SO360_RESOURCE, "360");
    private static volatile List<CidrBlock> duckDuckGoRanges;

    private SearchEngineBotIpRanges() {
    }

    static boolean containsDuckDuckGo(final String ip) {
        return contains(ip, duckDuckGoRanges());
    }

    static boolean contains360(final String ip) {
        return contains(ip, SO360_RANGES);
    }

    static boolean supportsSearchCrawler(final SearchEngines.Engine engine) {
        return engine == SearchEngines.Engine.OAI_SEARCH || engine == SearchEngines.Engine.CLAUDE_SEARCH
                || engine == SearchEngines.Engine.PERPLEXITY || engine == SearchEngines.Engine.MISTRAL_INDEX;
    }

    static boolean containsSearchCrawler(final String ip, final SearchEngines.Engine engine) {
        // 来源必须是地址字面量，不能把请求传入的主机名再解析为可信地址。
        if (ip == null || !(ip.matches("[0-9.]+") || (ip.contains(":") && ip.matches("[0-9a-fA-F:.]+")))) {
            return false;
        }
        return contains(ip, SearchCrawlerRanges.RANGES.getOrDefault(engine, List.of()));
    }

    // 各搜索角色独立加载官方快照，不合并训练或用户触发抓取器的网段；请求期间不下载列表。
    private static final class SearchCrawlerRanges {
        private static final Map<SearchEngines.Engine, List<CidrBlock>> RANGES = Map.of(
                SearchEngines.Engine.OAI_SEARCH, loadRanges("/search-engines/oai-search-bot-prefixes.json", "OAI-SearchBot"),
                SearchEngines.Engine.CLAUDE_SEARCH, loadRanges("/search-engines/claude-search-bot-prefixes.json", "Claude-SearchBot"),
                SearchEngines.Engine.PERPLEXITY, loadRanges("/search-engines/perplexity-bot-prefixes.json", "PerplexityBot"),
                SearchEngines.Engine.MISTRAL_INDEX, loadRanges("/search-engines/mistral-index-bot-prefixes.json", "MistralAI-Index"));
    }

    private static boolean contains(final String ip, final List<CidrBlock> ranges) {
        try {
            final InetAddress address = InetAddress.getByName(ip);
            for (final CidrBlock range : ranges) {
                if (range.contains(address)) {
                    return true;
                }
            }
        } catch (final Exception ignored) {
            return false;
        }
        return false;
    }

    private static List<CidrBlock> duckDuckGoRanges() {
        List<CidrBlock> ranges = duckDuckGoRanges;
        if (null != ranges) {
            return ranges;
        }

        synchronized (SearchEngineBotIpRanges.class) {
            ranges = duckDuckGoRanges;
            if (null == ranges) {
                ranges = loadRanges(DUCKDUCKGO_RESOURCE, "DuckDuckGo");
                duckDuckGoRanges = ranges;
            }
        }
        return ranges;
    }

    private static List<CidrBlock> loadRanges(final String resource, final String engine) {
        try (InputStream in = SearchEngineBotIpRanges.class.getResourceAsStream(resource)) {
            if (null == in) {
                LOGGER.log(Level.ERROR, "{} crawler IP range resource is missing [{}]", engine, resource);
                return Collections.emptyList();
            }

            final String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            final JSONArray prefixes = new JSONObject(text).optJSONArray("prefixes");
            if (null == prefixes) {
                LOGGER.log(Level.ERROR, "{} crawler IP range resource has no prefixes [{}]", engine, resource);
                return Collections.emptyList();
            }
            return parsePrefixes(prefixes);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Loads crawler IP ranges failed [" + resource + "]", e);
            return Collections.emptyList();
        }
    }

    private static List<CidrBlock> parsePrefixes(final JSONArray prefixes) throws Exception {
        final List<CidrBlock> ret = new ArrayList<>();
        for (int i = 0; i < prefixes.length(); i++) {
            final JSONObject item = prefixes.optJSONObject(i);
            if (null == item) {
                continue;
            }

            final String prefix = item.optString("ipv4Prefix", item.optString("ipv6Prefix", ""));
            if (!prefix.isEmpty()) {
                ret.add(CidrBlock.parse(prefix));
            }
        }
        return Collections.unmodifiableList(ret);
    }

    private static final class CidrBlock {
        private final byte[] network;
        private final int prefixLength;

        private CidrBlock(final byte[] network, final int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        static CidrBlock parse(final String cidr) throws Exception {
            final int slash = cidr.indexOf('/');
            if (slash <= 0) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr);
            }

            final byte[] network = InetAddress.getByName(cidr.substring(0, slash)).getAddress();
            final int prefixLength = Integer.parseInt(cidr.substring(slash + 1));
            if (prefixLength < 0 || prefixLength > network.length * 8) {
                throw new IllegalArgumentException("Invalid CIDR prefix: " + cidr);
            }
            return new CidrBlock(network, prefixLength);
        }

        boolean contains(final InetAddress address) {
            final byte[] bytes = address.getAddress();
            if (bytes.length != network.length) {
                return false;
            }

            final int fullBytes = prefixLength / 8;
            final int restBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (bytes[i] != network[i]) {
                    return false;
                }
            }
            if (restBits == 0) {
                return true;
            }

            final int mask = 0xFF << (8 - restBits);
            return (bytes[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
