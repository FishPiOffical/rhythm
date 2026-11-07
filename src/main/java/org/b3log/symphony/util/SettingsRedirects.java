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

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact origin and context-path boundary for the homepage preference; no network requests. */
public final class SettingsRedirects {
    private static final Pattern ESCAPE = Pattern.compile("%([0-9A-Fa-f]{2})");
    private SettingsRedirects() { }
    public static String normalize(final String value, final String site) {
        if (value.isEmpty()) return "";
        if (value.length() > 255) throw ApiV3WriteInputs.invalid();
        try {
            final URI base = new URI(site).normalize(), target = new URI(value);
            if (!("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme()))
                    || null == target.getHost() || null != target.getUserInfo()
                    || !target.getScheme().equalsIgnoreCase(base.getScheme()) || !target.getHost().equalsIgnoreCase(base.getHost())
                    || port(target) != port(base) || value.codePoints().anyMatch(cp -> Character.isWhitespace(cp) || Character.isISOControl(cp))) throw ApiV3WriteInputs.invalid();
            final String basePath = null == base.getPath() ? "" : base.getPath().replaceAll("/+$", "");
            final String path = null == target.getPath() ? "" : target.getPath();
            String decoded = path;
            // Each decoding shortens the path. Validate every interpretation so nested encoded traversal
            // cannot cross the deployment boundary, while ordinary encoded filenames remain valid.
            for (int round = 0; round <= path.length(); round++) {
                if (decoded.contains("\\") || decoded.startsWith("//") || decoded.codePoints().anyMatch(Character::isISOControl)) throw ApiV3WriteInputs.invalid();
                final String canonical = new URI(null, null, decoded, null).normalize().getPath();
                if (!(basePath.isEmpty() || canonical.equals(basePath) || canonical.startsWith(basePath + "/"))) throw ApiV3WriteInputs.invalid();
                final Matcher matcher = ESCAPE.matcher(decoded);
                final String next = matcher.replaceAll(match -> Matcher.quoteReplacement(String.valueOf((char)Integer.parseInt(match.group(1), 16))));
                if (next.equals(decoded)) break;
                decoded = next;
            }
            if ((path.equals(basePath) || path.equals(basePath + "/")) && (null == target.getRawFragment() || target.getRawFragment().isEmpty())) return "";
            return target.toString();
        } catch (final java.net.URISyntaxException e) { throw ApiV3WriteInputs.invalid(); }
    }
    private static int port(final URI uri) { return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80; }
}
