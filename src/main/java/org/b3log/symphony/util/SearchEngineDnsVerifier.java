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

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.ServiceUnavailableException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

/**
 * Search engine crawler DNS verifier.
 *
 * @author Zephyr
 * @since 3.9.0
 */
final class SearchEngineDnsVerifier {

    private SearchEngineDnsVerifier() {
    }

    @FunctionalInterface
    interface DnsLookup {
        List<String> lookup(String name, String... attrs) throws NamingException;
    }

    static SearchEngines.Verification verify(final String ip, final SearchEngines.Engine engine) {
        return verify(ip, engine, SearchEngineDnsVerifier::lookup);
    }

    static SearchEngines.Verification verify(final String ip, final SearchEngines.Engine engine,
                                              final DnsLookup dns) {
        final List<String> ptr = new ArrayList<>();
        final List<String> forward = new ArrayList<>();
        String stage = "来源 IP 解析";
        try {
            final InetAddress address = InetAddress.getByName(ip);
            stage = "反向查询";
            final List<String> hosts = dns.lookup(reverseLookupName(address), "PTR");
            for (final String host : hosts) ptr.add(normalizeHostname(host));
            if (hosts.isEmpty()) return result(engine, false, "反向解析无记录", ptr, forward);
            boolean allowed = false;
            for (final String host : hosts) {
                final String hostname = normalizeHostname(host);
                if (!isAllowedHostname(engine, hostname)) continue;
                allowed = true;
                stage = "正向查询";
                if (forwardMatches(hostname, address, dns, forward)) {
                    return result(engine, true, "双向 DNS 校验通过", ptr, forward);
                }
            }
            return result(engine, false, allowed ? "正向解析未匹配来源 IP" : "反向域名后缀不符", ptr, forward);
        } catch (final Exception e) {
            // 只保留异常类型与查询阶段，避免把解析器配置等内部信息带入诊断。
            final boolean transientFailure = e instanceof CommunicationException
                    || e instanceof ServiceUnavailableException;
            return new SearchEngines.Verification(engine, false,
                    stage + "异常（" + e.getClass().getSimpleName() + "）", String.join(", ", ptr),
                    String.join("; ", forward), System.currentTimeMillis(), false, transientFailure);
        }
    }

    private static SearchEngines.Verification result(final SearchEngines.Engine engine, final boolean verified,
                                                      final String reason, final List<String> ptr,
                                                      final List<String> forward) {
        return new SearchEngines.Verification(engine, verified, reason, String.join(", ", ptr),
                String.join("; ", forward), System.currentTimeMillis(), false);
    }

    private static boolean forwardMatches(final String hostname, final InetAddress address, final DnsLookup dns,
                                          final List<String> forward) throws NamingException {
        // JNDI 同查 A/AAAA 会合成 ANY；只查来源地址族，避免解析器拒绝 ANY 而误拒真实爬虫。
        final String recordType = address.getAddress().length == 4 ? "A" : "AAAA";
        final List<String> values = dns.lookup(hostname, recordType);
        forward.add(hostname + " → " + (values.isEmpty() ? "无记录" : String.join(", ", values)));
        for (final String value : values) {
            try {
                if (InetAddress.getByName(value).equals(address)) {
                    return true;
                }
            } catch (final Exception ignored) {
            }
        }
        return false;
    }

    private static List<String> lookup(final String name, final String... attrs) throws NamingException {
        final Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "1000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        final DirContext context = new InitialDirContext(env);
        try {
            return readAttributes(context.getAttributes(name, attrs), attrs);
        } finally {
            context.close();
        }
    }

    private static List<String> readAttributes(final Attributes attributes, final String[] names) throws NamingException {
        final List<String> ret = new ArrayList<>();
        for (final String name : names) {
            final Attribute attr = attributes.get(name);
            if (null == attr) {
                continue;
            }

            final NamingEnumeration<?> values = attr.getAll();
            while (values.hasMore()) {
                ret.add(String.valueOf(values.next()));
            }
        }
        return ret;
    }

    private static String reverseLookupName(final InetAddress address) {
        final byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return ipv4ReverseName(bytes);
        }
        return ipv6ReverseName(bytes);
    }

    private static String ipv4ReverseName(final byte[] bytes) {
        return (bytes[3] & 0xFF) + "." + (bytes[2] & 0xFF) + "."
                + (bytes[1] & 0xFF) + "." + (bytes[0] & 0xFF) + ".in-addr.arpa";
    }

    private static String ipv6ReverseName(final byte[] bytes) {
        final StringBuilder ret = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; i--) {
            final int value = bytes[i] & 0xFF;
            ret.append(Integer.toHexString(value & 0x0F)).append('.');
            ret.append(Integer.toHexString((value >> 4) & 0x0F)).append('.');
        }
        return ret.append("ip6.arpa").toString();
    }

    private static boolean isAllowedHostname(final SearchEngines.Engine engine, final String hostname) {
        switch (engine) {
            case GOOGLE:
                return SearchEngines.hasDnsSuffix(hostname, "googlebot.com")
                        || SearchEngines.hasDnsSuffix(hostname, "google.com")
                        || SearchEngines.hasDnsSuffix(hostname, "googleusercontent.com");
            case BAIDU:
                return SearchEngines.hasDnsSuffix(hostname, "baidu.com")
                        || SearchEngines.hasDnsSuffix(hostname, "baidu.jp");
            case BING:
                return SearchEngines.hasDnsSuffix(hostname, "search.msn.com");
            case SOGOU:
                return SearchEngines.hasDnsSuffix(hostname, "sogou.com");
            case YANDEX:
                return SearchEngines.hasDnsSuffix(hostname, "yandex.ru")
                        || SearchEngines.hasDnsSuffix(hostname, "yandex.net")
                        || SearchEngines.hasDnsSuffix(hostname, "yandex.com");
            case APPLE:
                return SearchEngines.hasDnsSuffix(hostname, "applebot.apple.com");
            default:
                return false;
        }
    }

    private static String normalizeHostname(final String host) {
        String ret = StringUtils.trimToEmpty(host).toLowerCase(Locale.ROOT);
        if (ret.endsWith(".")) {
            ret = ret.substring(0, ret.length() - 1);
        }
        return ret;
    }
}
