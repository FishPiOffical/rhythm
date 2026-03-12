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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Notification content utilities.
 *
 * @author <a href="https://fishpi.cn/member/admin">admin</a>
 * @version 1.0.0.0, Mar 12, 2026
 * @since 1.0.0
 */
public final class NotificationContents {

    /**
     * Legacy dataId length limit.
     */
    public static final int LEGACY_CUSTOM_SYS_DATA_ID_LENGTH = 64;

    /**
     * Whitelist for custom system notifications.
     */
    private static final Whitelist CUSTOM_SYS_WHITELIST = Whitelist.none()
            .addTags("a", "br")
            .addAttributes("a", "href", "target", "rel")
            .addProtocols("a", "href", "http", "https");

    /**
     * Output settings.
     */
    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings().prettyPrint(false);

    /**
     * HTML tag detection pattern.
     */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(".*<\\s*/?\\s*[a-zA-Z][^>]*>.*", Pattern.DOTALL);

    /**
     * Private constructor.
     */
    private NotificationContents() {
    }

    /**
     * Normalizes custom system notification input.
     *
     * @param content the raw content
     * @return normalized content
     */
    public static String normalizeCustomSysContent(final String content) {
        return StringUtils.trimToEmpty(content)
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }

    /**
     * Markdown link detection pattern.
     */
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]\\r\\n]+)]\\((https?://[^\\s)]+)\\)");

    /**
     * Sanitizes custom system notification content. Only markdown links and line breaks are kept.
     *
     * @param content the raw content
     * @return sanitized HTML
     */
    public static String sanitizeCustomSysContent(final String content) {
        if (StringUtils.isBlank(content)) {
            return StringUtils.EMPTY;
        }

        final String normalized = normalizeCustomSysContent(content);
        final String withLinks = replaceMarkdownLinks(Escapes.escapeHTML(normalized));
        final String cleaned = Jsoup.clean(withLinks.replace("\n", "<br>"), StringUtils.EMPTY, CUSTOM_SYS_WHITELIST, OUTPUT_SETTINGS);
        final Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings(OUTPUT_SETTINGS);
        return document.body().html();
    }

    /**
     * Returns whether the content needs the dedicated content column.
     *
     * @param content the raw content
     * @return {@code true} if the legacy dataId field is not enough
     */
    public static boolean requiresDedicatedContentColumn(final String content) {
        if (StringUtils.length(content) > LEGACY_CUSTOM_SYS_DATA_ID_LENGTH) {
            return true;
        }

        final String safeContent = StringUtils.defaultString(content);
        return HTML_TAG_PATTERN.matcher(safeContent).matches() || MARKDOWN_LINK_PATTERN.matcher(safeContent).find();
    }

    /**
     * Checks whether a link is a safe HTTP/HTTPS URL.
     *
     * @param href the href to check
     * @return {@code true} if safe
     */
    private static boolean isSafeHttpUrl(final String href) {
        if (StringUtils.isBlank(href)) {
            return false;
        }

        try {
            final URL url = new URL(href);
            final String protocol = StringUtils.lowerCase(url.getProtocol());
            return "http".equals(protocol) || "https".equals(protocol);
        } catch (final MalformedURLException e) {
            return false;
        }
    }

    /**
     * Replaces markdown links with safe anchor HTML.
     *
     * @param content the escaped content
     * @return replaced content
     */
    private static String replaceMarkdownLinks(final String content) {
        final StringBuilder builder = new StringBuilder();
        final java.util.regex.Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(content);
        int lastEnd = 0;
        while (matcher.find()) {
            final String href = StringUtils.trimToEmpty(matcher.group(2));
            if (!isSafeHttpUrl(href)) {
                continue;
            }

            builder.append(content, lastEnd, matcher.start());
            builder.append("<a href=\"")
                    .append(href)
                    .append("\" target=\"_blank\" rel=\"nofollow noopener noreferrer\">")
                    .append(matcher.group(1))
                    .append("</a>");
            lastEnd = matcher.end();
        }

        builder.append(content, lastEnd, content.length());
        return builder.toString();
    }
}
