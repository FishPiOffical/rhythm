package org.b3log.symphony.util;

import java.util.regex.Pattern;

public final class MdSanitizer {
    private static final Pattern CF = Pattern.compile("\\p{Cf}+");

    public static String stripFormatChars(String s) {
        if (s == null) return null;
        return CF.matcher(s).replaceAll("");
    }
}
