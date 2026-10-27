package org.b3log.symphony.util;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.json.JSONObject;

/** A configured local page link only; never creates a payment or browser session. */
public final class ApiV3DonationPage {
    private ApiV3DonationPage() { }

    public static JSONObject link(final String configuredSite, final boolean development) {
        try {
            if (configuredSite == null || configuredSite.isEmpty() || configuredSite.length() > 2048
                    || configuredSite.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
                throw new IllegalArgumentException();
            }
            final URI base = URI.create(configuredSite);
            final String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
            if (base.getHost() == null || base.getRawUserInfo() != null || base.getRawQuery() != null
                    || base.getRawFragment() != null || base.getPort() == 0 || base.getPort() > 65535
                    || base.getRawAuthority().endsWith(":") || !base.normalize().equals(base)
                    || base.getRawPath().contains("//")
                    || !(scheme.equals("https") || scheme.equals("http") && development
                    && Set.of("localhost", "127.0.0.1", "[::1]").contains(base.getHost().toLowerCase(Locale.ROOT)))) {
                throw new IllegalArgumentException();
            }
            final String site = configuredSite.endsWith("/") ? configuredSite.substring(0, configuredSite.length() - 1) : configuredSite;
            return new JSONObject().put("url", site + "/charge/point");
        } catch (final IllegalArgumentException e) {
            throw new ApiV3Exception(503, "service_unavailable", "捐助页面暂不可用");
        }
    }
}
