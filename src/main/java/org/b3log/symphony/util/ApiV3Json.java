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

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;

/** Strict bounded JSON input and stable fingerprints for v3 writes. */
public final class ApiV3Json {
    public static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DEPTH = 32;
    private ApiV3Json() { }

    public static JSONObject parse(final byte[] bytes) {
        if (null == bytes || bytes.length == 0 || bytes.length > MAX_BODY_BYTES) throw new IllegalArgumentException("body");
        try {
            final String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            final Parser parser = new Parser(text);
            parser.value(0);
            parser.space();
            if (parser.index != text.length() || !text.stripLeading().startsWith("{")) throw new IllegalArgumentException("object");
            final JSONObject result = new JSONObject(text);
            canonical(result); // Also rejects isolated UTF-16 surrogates and unsupported values.
            return result;
        } catch (final CharacterCodingException e) {
            throw new IllegalArgumentException("utf8", e);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("json", e);
        }
    }

    public static String canonical(final Object value) { return canonical(value, 0); }

    private static String canonical(final Object value, final int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("depth");
        if (null == value || JSONObject.NULL == value) return "null";
        if (value instanceof JSONObject object) {
            final StringBuilder out = new StringBuilder("{");
            for (final String key : new TreeSet<>(object.keySet())) {
                unicode(key);
                if (out.length() > 1) out.append(',');
                out.append(JSONObject.quote(key)).append(':').append(canonical(object.opt(key), depth + 1));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray array) {
            final StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) out.append(',');
                out.append(canonical(array.get(i), depth + 1));
            }
            return out.append(']').toString();
        }
        if (value instanceof String text) { unicode(text); return JSONObject.quote(text); }
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number number) return JSONObject.numberToString(number);
        throw new IllegalArgumentException("value");
    }

    private static void unicode(final String text) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i == text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IllegalArgumentException("unicode");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("unicode");
        }
    }

    /** org.json accepts JavaScript extensions; validate JSON grammar before invoking it. */
    private static final class Parser {
        private final String text;
        private int index;
        private Parser(final String text) { this.text = text; }
        private void space() {
            while (index < text.length() && " \r\n\t".indexOf(text.charAt(index)) >= 0) index++;
        }
        private char peek() { return index < text.length() ? text.charAt(index) : '\0'; }
        private boolean take(final char c) {
            space();
            if (peek() != c) return false;
            index++;
            return true;
        }
        private void value(final int depth) {
            if (depth > MAX_DEPTH) throw new IllegalArgumentException("depth");
            space();
            if (take('{')) {
                if (take('}')) return;
                do {
                    space(); string();
                    if (!take(':')) throw new IllegalArgumentException("colon");
                    value(depth + 1);
                    if (take('}')) return;
                } while (take(','));
                throw new IllegalArgumentException("object");
            }
            if (take('[')) {
                if (take(']')) return;
                do {
                    value(depth + 1);
                    if (take(']')) return;
                } while (take(','));
                throw new IllegalArgumentException("array");
            }
            if (peek() == '"') { string(); return; }
            for (final String literal : new String[]{"true", "false", "null"}) {
                if (text.startsWith(literal, index)) { index += literal.length(); return; }
            }
            number();
        }
        private void string() {
            if (peek() != '"') throw new IllegalArgumentException("string");
            index++;
            while (index < text.length()) {
                final char c = text.charAt(index++);
                if (c == '"') return;
                if (c < 0x20) throw new IllegalArgumentException("control");
                if (c != '\\') continue;
                if (index >= text.length()) throw new IllegalArgumentException("escape");
                final char escape = text.charAt(index++);
                if (escape == 'u') {
                    for (int i = 0; i < 4; i++) {
                        if (index >= text.length() || "0123456789abcdefABCDEF".indexOf(text.charAt(index++)) < 0) throw new IllegalArgumentException("unicode");
                    }
                } else if ("\"\\/bfnrt".indexOf(escape) < 0) throw new IllegalArgumentException("escape");
            }
            throw new IllegalArgumentException("string");
        }
        private void number() {
            final int start = index;
            if (peek() == '-') index++;
            if (peek() == '0') index++;
            else {
                if (peek() < '1' || peek() > '9') throw new IllegalArgumentException("number");
                digits();
            }
            if (peek() == '.') { index++; requiredDigits(); }
            if (peek() == 'e' || peek() == 'E') {
                index++;
                if (peek() == '+' || peek() == '-') index++;
                requiredDigits();
            }
            if (index == start || !(JSONObject.stringToValue(text.substring(start, index)) instanceof Number)) {
                throw new IllegalArgumentException("number");
            }
        }
        private void digits() { while (peek() >= '0' && peek() <= '9') index++; }
        private void requiredDigits() {
            final int start = index;
            digits();
            if (index == start) throw new IllegalArgumentException("number");
        }
    }
}
