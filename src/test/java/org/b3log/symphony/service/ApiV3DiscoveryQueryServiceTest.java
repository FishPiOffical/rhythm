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

import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.symphony.model.Domain;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.SystemSettingsRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Properties;
import java.util.Set;

public class ApiV3DiscoveryQueryServiceTest {
    @Test
    public void searchTreatsLikeMetacharactersLiterally() {
        Assert.assertEquals(ApiV3DiscoveryQueryService.escapeLike("100%_done!"), "100!%!_done!!");
        Assert.assertEquals(ApiV3DiscoveryQueryService.escapeLike("a'b\\c中文"), "a'b\\c中文");
    }

    @Test
    public void tagDtoCannotLeakCssAdsOrAuditFields() {
        final JSONObject raw = new JSONObject().put(Keys.OBJECT_ID, "1").put(Tag.TAG_TITLE, "Java")
                .put(Tag.TAG_DESCRIPTION, "description").put(Tag.TAG_STATUS, 1)
                .put(Tag.TAG_CSS, "private-css").put(Tag.TAG_AD, "private-ad").put("token", "secret");
        final JSONObject dto = ApiV3DiscoveryQueryService.tagDto(raw);
        Assert.assertEquals(dto.keySet(), Set.of("id", "title", "uri", "description", "iconURL", "articleCount", "followerCount"));
        Assert.assertFalse(dto.toString().contains("secret"));
        Assert.assertEquals(raw.optString(Tag.TAG_CSS), "private-css");
    }

    @Test
    public void domainDtoCannotLeakCssOrControlFields() {
        final JSONObject raw = new JSONObject().put(Keys.OBJECT_ID, "2").put(Domain.DOMAIN_TITLE, "编程")
                .put(Domain.DOMAIN_STATUS, 1).put(Domain.DOMAIN_CSS, "private-css").put("apiKey", "secret");
        final JSONObject dto = ApiV3DiscoveryQueryService.domainDto(raw);
        Assert.assertEquals(dto.keySet(), Set.of("id", "title", "uri", "description", "iconURL", "tagCount"));
        Assert.assertFalse(dto.toString().contains("secret"));
    }

    @Test
    public void memberProfileOnlyIncludesValidLoginTimeWhenRequested() throws Exception {
        final var settings = new Properties();
        settings.setProperty("runtimeDatabase", "NONE");
        Latkes.setLatkeProps(settings);
        Latkes.setLocalProps(settings);
        final var query = new ApiV3DiscoveryQueryService();
        final var target = new JSONObject().put(Keys.OBJECT_ID, "123").put("userName", "Alice")
                .put(UserExt.USER_POINT, 1).put(UserExt.ONLINE_MINUTE, 2)
                .put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID)
                .put(UserExt.USER_LATEST_LOGIN_TIME, 1794960000000L).put(UserExt.USER_LATEST_LOGIN_IP, "fixture ip");
        ApiV3SecurityTest.inject(query, "userRepository", new UserRepository() {
            @Override
            public List<JSONObject> select(String sql, Object... args) {
                return "Alice".equals(args[0]) ? List.of(target) : List.of();
            }
        });
        ApiV3SecurityTest.inject(query, "systemSettingsRepository", new SystemSettingsRepository() {
            @Override
            public List<JSONObject> select(String sql, Object... args) { return List.of(); }
        });
        ApiV3SecurityTest.inject(query, "contentDtoService", new ApiV3ContentDtoService() {
            @Override
            public JSONObject author(JSONObject user, boolean anonymous) {
                return new JSONObject().put("id", user.getString(Keys.OBJECT_ID)).put("userName", user.getString("userName"));
            }
        });

        final var account = query.targetUser("Alice");
        final var summary = query.profile(account, false);
        final var member = query.profile(account, true);
        Assert.assertFalse(summary.has(UserExt.USER_LATEST_LOGIN_TIME));
        Assert.assertEquals(member.getLong(UserExt.USER_LATEST_LOGIN_TIME), 1794960000000L);
        member.remove(UserExt.USER_LATEST_LOGIN_TIME);
        Assert.assertTrue(member.similar(summary));
        Assert.assertFalse(summary.has(UserExt.USER_LATEST_LOGIN_IP));
        for (long time : new long[]{0, -1, 1794960000L, Long.MAX_VALUE}) {
            target.put(UserExt.USER_LATEST_LOGIN_TIME, time);
            Assert.assertFalse(query.profile(account, true).has(UserExt.USER_LATEST_LOGIN_TIME));
        }
        target.remove(UserExt.USER_LATEST_LOGIN_TIME);
        Assert.assertFalse(query.profile(account, true).has(UserExt.USER_LATEST_LOGIN_TIME));
        target.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_DEACTIVATED);
        Assert.assertEquals(Assert.expectThrows(ApiV3Exception.class, () -> query.targetUser("Alice")).status, 404);
    }
}
