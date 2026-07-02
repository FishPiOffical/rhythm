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
package org.b3log.symphony.model.feed;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

/**
 * RSS XML 工具。
 */
final class RSSXmls {

    private static final int XML_TAB = 0x9;

    private static final int XML_LINE_FEED = 0xA;

    private static final int XML_CARRIAGE_RETURN = 0xD;

    private static final int XML_TEXT_START = 0x20;

    private static final int XML_BMP_BEFORE_SURROGATE = 0xD7FF;

    private static final int XML_BMP_AFTER_SURROGATE = 0xE000;

    private static final int XML_BMP_END = 0xFFFD;

    private static final int XML_SUPPLEMENTARY_START = 0x10000;

    private static final int XML_SUPPLEMENTARY_END = 0x10FFFF;

    private static final String CDATA_START = "<![CDATA[";

    private static final String CDATA_END = "]]>";

    private static final String CDATA_END_SPLIT = "]]]]><![CDATA[>";

    private RSSXmls() {
    }

    static String escape(final String text) {
        return StringEscapeUtils.escapeXml(sanitize(text));
    }

    static String cdata(final String text) {
        return CDATA_START + sanitize(text).replace(CDATA_END, CDATA_END_SPLIT) + CDATA_END;
    }

    static String sanitize(final String text) {
        if (StringUtils.isEmpty(text)) {
            return "";
        }

        final StringBuilder ret = new StringBuilder(text.length());
        text.codePoints().filter(RSSXmls::isXml10Char).forEach(ret::appendCodePoint);

        return ret.toString();
    }

    private static boolean isXml10Char(final int codePoint) {
        return XML_TAB == codePoint
                || XML_LINE_FEED == codePoint
                || XML_CARRIAGE_RETURN == codePoint
                || codePoint >= XML_TEXT_START && codePoint <= XML_BMP_BEFORE_SURROGATE
                || codePoint >= XML_BMP_AFTER_SURROGATE && codePoint <= XML_BMP_END
                || codePoint >= XML_SUPPLEMENTARY_START && codePoint <= XML_SUPPLEMENTARY_END;
    }
}
