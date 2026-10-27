package org.b3log.symphony.util;

import org.apache.commons.lang.StringUtils;
import org.b3log.symphony.model.RepeaterContent;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import java.util.Set;

/** Keeps the existing repeater text normalization and checks typed, bounded input. */
public final class ApiV3RepeaterPublicationInputs {
    private ApiV3RepeaterPublicationInputs() { }
    public static JSONObject normalize(final JSONObject body) {
        if (body == null || !body.keySet().equals(Set.of("type", "content"))
                || !(body.opt("type") instanceof String type) || !(body.opt("content") instanceof String content)
                || type.length() > 32 || content.length() > 8192) throw ApiV3WriteInputs.invalid();
        validateUnicode(type); validateUnicode(content);
        final String normalizedType = StringUtils.trimToEmpty(type);
        final String normalizedContent = Jsoup.clean(StringUtils.trimToEmpty(content), Safelist.none()).replaceAll("\\s+", " ");
        if (!RepeaterContent.isSupportedType(normalizedType) || normalizedContent.length() < RepeaterContent.MIN_CONTENT_LENGTH
                || normalizedContent.length() > RepeaterContent.MAX_CONTENT_LENGTH) throw ApiV3WriteInputs.invalid();
        validateUnicode(normalizedContent);
        return new JSONObject().put("type", normalizedType).put("content", normalizedContent);
    }
    private static void validateUnicode(final String text) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw ApiV3WriteInputs.invalid(); }
            else if (Character.isLowSurrogate(c) || c == 0) throw ApiV3WriteInputs.invalid();
        }
    }
}
