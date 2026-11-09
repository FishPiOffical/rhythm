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
package org.b3log.symphony.service;

import org.b3log.symphony.util.ApiV3ArticleStickState;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.Set;

/** 覆盖实际置顶元数据和文章摘要投影，渲染及作者来源使用测试适配器。 */
public final class ApiV3ArticleStickStateTest {
    interface Work { void run(); }

    static void invalid(Work work) {
        try {
            work.run();
            Assert.fail();
        } catch (ApiV3Exception error) {
            Assert.assertEquals(error.status, 503);
        }
    }

    public static void main(String[] args) throws Exception {
        final long now = 1700000000000L, duration = 1800000;
        final var none = ApiV3ArticleStickState.metadata(0L, now, duration);
        Assert.assertEquals(none.keySet(), Set.of("active", "permanent", "expiresAt"));
        Assert.assertFalse(none.getBoolean("active"));
        Assert.assertEquals(none.getLong("expiresAt"), 0);
        var active = ApiV3ArticleStickState.metadata(now - 1000, now, duration);
        Assert.assertTrue(active.getBoolean("active"));
        Assert.assertEquals(active.getLong("expiresAt"), now - 1000 + duration);
        Assert.assertTrue(ApiV3ArticleStickState.metadata(now - duration, now, duration).getBoolean("active"));
        Assert.assertFalse(ApiV3ArticleStickState.metadata(now - duration - 1, now, duration).getBoolean("active"));

        // 永久状态通过布尔值和空过期时间表达，内部标记值不进入 JSON。
        final var permanent = ApiV3ArticleStickState.metadata(Long.MAX_VALUE, now, duration);
        Assert.assertTrue(permanent.getBoolean("active"));
        Assert.assertTrue(permanent.getBoolean("permanent"));
        Assert.assertTrue(permanent.isNull("expiresAt"));
        Assert.assertFalse(permanent.toString().contains(Long.toString(Long.MAX_VALUE)));
        for (Object value : new Object[]{-1L, "1", 1.5, Long.MAX_VALUE - 1})
            invalid(() -> ApiV3ArticleStickState.metadata(value, now, duration));
        invalid(() -> ApiV3ArticleStickState.threshold(now, 0));
        System.out.println("PASS stick state 1 none/active/exact expiry/expired/permanent metadata, no unsafe integer sentinel, malformed state fails closed");
        var dto = new ApiV3ContentDtoService();
        var avatarField = ApiV3ContentDtoService.class.getDeclaredField("avatarQueryService");
        avatarField.setAccessible(true);
        avatarField.set(dto, new AvatarQueryService());
        var article = new JSONObject().put("oId", "10").put("articleAuthorId", "1").put("articleTitle", "Title")
                .put("articleContent", "Body").put("articleStick", Long.MAX_VALUE);
        var summary = dto.summary(article, new JSONObject().put("oId", "1").put("userStatus", 0)
                .put("userAvatarURL", "https://example.test/a.png"));
        Assert.assertTrue(summary.getJSONObject("stick").getBoolean("permanent"));
        Assert.assertTrue(summary.getJSONObject("stick").isNull("expiresAt"));
        Assert.assertFalse(summary.has("articleStick"));
        Assert.assertFalse(summary.toString().contains("PRIVATE"));
        System.out.println("PASS stick state 2 actual public article summary adds only whitelisted stick metadata");
    }
}
