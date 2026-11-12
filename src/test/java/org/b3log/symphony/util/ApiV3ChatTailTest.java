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

import org.json.JSONObject;

/** 独立运行真实字素校验和消息 DTO，不访问外部服务或修改线上数据。 */
public final class ApiV3ChatTailTest {
    private static int checks;

    public static void main(final String[] args) {
        check(ApiV3ChatTail.text("鱼".repeat(24)).length() == 24);
        rejects(() -> ApiV3ChatTail.text("鱼".repeat(25)));
        for (String emoji : new String[]{"👨‍👩‍👧‍👦", "🇨🇳", "👍🏽", "e\u0301", "1️⃣"}) {
            check(ApiV3ChatTail.text(emoji.repeat(24)).equals(emoji.repeat(24)));
            rejects(() -> ApiV3ChatTail.text(emoji.repeat(25)));
        }
        check(ApiV3ChatTail.text("a" + "\u0301".repeat(511)).length() == 512);
        rejects(() -> ApiV3ChatTail.text("a" + "\u0301".repeat(512)));
        for (String bad : new String[]{"a\nb", "a\rb", "a\tb", "\u202Eabc", "\uD800", "\uDC00", "\u200D", "\u0301"})
            rejects(() -> ApiV3ChatTail.text(bad));
        check(ApiV3ChatTail.input(new JSONObject().put("enabled", false).put("text", "")).getString("text").isEmpty());
        rejects(() -> ApiV3ChatTail.input(new JSONObject().put("enabled", true).put("text", "  ")));
        rejects(() -> ApiV3ChatTail.input(new JSONObject().put("enabled", "true").put("text", "鱼")));
        rejects(() -> ApiV3ChatTail.input(new JSONObject().put("enabled", false).put("text", "鱼").put("userId", "1")));
        check(ApiV3ChatTail.text(" <b>鱼</b> ").equals("<b>鱼</b>"));
        for (String html : new String[]{"", "<p><img alt='有文字' src='a'></p>", "<img src='a'><img src='b'>", "<blockquote><p>引用</p></blockquote>", "<video>备用文字</video>"})
            check(!ApiV3ChatTail.eligibleHtml(html));
        for (String html : new String[]{"<p>文字</p>", "<p>🙂</p>", "<img src='a'>说明", "<blockquote>引用</blockquote><p>回复</p>"})
            check(ApiV3ChatTail.eligibleHtml(html));
        final JSONObject tail = new JSONObject().put("text", "<b>鱼</b>");
        final JSONObject normal = dto("正文", tail, "<p>正文</p>");
        check(normal.getString("content").equals("正文"));
        check(normal.getJSONObject("tail").getString("text").equals("<b>鱼</b>"));
        check(!dto("旧消息", null, "旧消息").has("tail"));
        check(!dto("正文", new JSONObject().put("text", "a\nb"), "正文").has("tail"));
        check(!dto("图片", tail, "<img src='a'>").has("tail"));
        check(!dto("引用", tail, "<blockquote>引用</blockquote>").has("tail"));
        for (String kind : new String[]{"redPacket", "music", "weather"})
            check(!dto(new JSONObject().put("msgType", kind).toString(), tail, "正文").has("tail"));
        check(ApiV3ChatTail.snapshot(new JSONObject().put("text", "鱼").put("secret", "不投影")).keySet().size() == 1);
        System.out.println("chat-tail: " + checks + " checks passed");
    }

    private static JSONObject dto(String content, JSONObject tail, String html) {
        final JSONObject source = new JSONObject().put("content", content).put("userOId", "1").put("userName", "test");
        if (tail != null) source.put("tail", tail);
        return ApiV3ChatMessageDto.from(new JSONObject().put("oId", "1").put("content", source.toString()), ignored -> html);
    }

    private static void check(boolean ok) {
        checks++;
        if (!ok) throw new AssertionError("检查失败：" + checks);
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (ApiV3Exception expected) { checks++; return; }
        throw new AssertionError("预期拒绝非法输入");
    }
}
