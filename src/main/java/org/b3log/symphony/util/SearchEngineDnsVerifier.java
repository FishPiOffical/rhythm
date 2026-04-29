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

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
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

    static boolean verify(final String ip, final SearchEngines.Engine engine) {
        try {
            final InetAddress address = InetAddress.getByName(ip);
            for (final String host : lookupPtr(reverseLookupName(address))) {
                final String hostname = normalizeHostname(host);
                if (isAllowedHostname(engine, hostname) && forwardMatches(hostname, address)) {
                    return true;
                }
            }
        } catch (final Exception ignored) {
            return false;
        }
        return false;
    }

    private static boolean forwardMatches(final String hostname, final InetAddress address) throws NamingException {
        for (final String value : lookup(hostname, "A", "AAAA")) {
            try {
                if (InetAddress.getByName(value).equals(address)) {
                    return true;
                }
            } catch (final Exception ignored) {
            }
        }
        return false;
    }

    private static List<String> lookupPtr(final String reverseName) throws NamingException {
        return lookup(reverseName, "PTR");
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
