package org.b3log.symphony.util;

import java.net.URI;

/** Stored avatar URL projection, preserving signed queries without fetching or page display transforms. */
public final class ApiV3AvatarUrl {
    private ApiV3AvatarUrl() { }
    public static String stored(final String raw) {
        final String value = raw == null ? "" : raw.trim();
        if (value.length() > 2048 || value.indexOf('\\') >= 0 || value.codePoints().anyMatch(Character::isISOControl)) return "";
        try {
            final URI uri = new URI(value);
            if (value.startsWith("/") && !value.startsWith("//") && uri.getRawAuthority() == null) return value;
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return "";
            return uri.getRawAuthority() != null && !uri.getRawAuthority().isBlank() && uri.getUserInfo() == null ? value : "";
        } catch (final Exception ignored) { return ""; }
    }
}
