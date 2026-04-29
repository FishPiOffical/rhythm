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

/**
 * Search engine crawler IP ranges.
 *
 * @author Zephyr
 * @since 3.9.0
 */
final class SearchEngineBotIpRanges {

    private static final Logger LOGGER = LogManager.getLogger(SearchEngineBotIpRanges.class);
    private static final String DUCKDUCKGO_RESOURCE = "/search-engines/duckduckgo-bot-prefixes.json";
    private static volatile List<CidrBlock> duckDuckGoRanges;

    private SearchEngineBotIpRanges() {
    }

    static boolean containsDuckDuckGo(final String ip) {
        try {
            final InetAddress address = InetAddress.getByName(ip);
            for (final CidrBlock range : duckDuckGoRanges()) {
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
                ranges = loadDuckDuckGoRanges();
                duckDuckGoRanges = ranges;
            }
        }
        return ranges;
    }

    private static List<CidrBlock> loadDuckDuckGoRanges() {
        try (InputStream in = SearchEngineBotIpRanges.class.getResourceAsStream(DUCKDUCKGO_RESOURCE)) {
            if (null == in) {
                LOGGER.log(Level.ERROR, "DuckDuckGo crawler IP range resource is missing [{}]", DUCKDUCKGO_RESOURCE);
                return Collections.emptyList();
            }

            final String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            final JSONArray prefixes = new JSONObject(text).optJSONArray("prefixes");
            if (null == prefixes) {
                LOGGER.log(Level.ERROR, "DuckDuckGo crawler IP range resource has no prefixes");
                return Collections.emptyList();
            }
            return parsePrefixes(prefixes);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Loads DuckDuckGo crawler IP ranges failed", e);
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
