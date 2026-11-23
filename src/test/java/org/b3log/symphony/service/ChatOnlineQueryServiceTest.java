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
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.util.ChatOnlineSnapshot;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class ChatOnlineQueryServiceTest {
    private final Map<String, Object> previousProperties = new HashMap<>();

    @BeforeClass
    public void configureLatke() throws Exception {
        final var properties = new Properties();
        properties.setProperty("staticServerScheme", "https");
        properties.setProperty("staticServerHost", "example.org");
        properties.setProperty("staticServerPort", "443");
        properties.setProperty("staticPath", "");
        properties.setProperty("runtimeCache", "NONE");
        properties.setProperty("runtimeDatabase", "NONE");
        for (final var name : List.of("latkeProps", "localProps")) {
            final var field = Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            previousProperties.put(name, field.get(null));
            field.set(null, properties);
        }
    }

    @AfterClass(alwaysRun = true)
    public void restoreLatke() throws Exception {
        for (final var entry : previousProperties.entrySet()) {
            final var field = Latkes.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(null, entry.getValue());
        }
    }

    @Test
    public void classicAndV3IncludeMembersWithHiddenProfileStatus() throws Exception {
        final var query = fixture();
        final var frame = query.classicBroadcast("当前话题");
        Assert.assertEquals(frame.keySet(), Set.of("type", "onlineChatCnt", "users", "discussing"));
        Assert.assertEquals(frame.getString("type"), "online");
        Assert.assertEquals(frame.getString("discussing"), "当前话题");
        final var members = frame.getJSONArray("users");
        Assert.assertEquals(frame.getInt("onlineChatCnt"), 4);
        Assert.assertEquals(members.length(), 4);
        Assert.assertEquals(members.getJSONObject(0).getString("userName"), "classic-local");
        Assert.assertEquals(members.getJSONObject(1).getString("userName"), "classic-node");
        Assert.assertEquals(members.getJSONObject(2).getString("userName"), "hidden");
        final var v3Only = members.getJSONObject(3);
        Assert.assertEquals(v3Only.getString("userName"), "v3-only");
        Assert.assertTrue(v3Only.getString("homePage").endsWith("/member/v3-only"));
        Assert.assertEquals(v3Only.keySet(), Set.of("userName", "homePage", "userAvatarURL", "userAvatarURL20",
                "userAvatarURL48", "userAvatarURL210"));

        final var firstPage = query.list(1, 2);
        Assert.assertEquals(firstPage.getJSONObject("pagination").getInt("total"), frame.getInt("onlineChatCnt"));
        Assert.assertEquals(firstPage.getJSONArray("list").length(), 2);
        Assert.assertTrue(firstPage.getBoolean("hasMore"));
        Assert.assertEquals(firstPage.getLong("nodesSampledAt"), 42);
        Assert.assertFalse(firstPage.getBoolean("nodesComplete"));
        final var secondPage = query.list(2, 2);
        final var hiddenMember = secondPage.getJSONArray("list").getJSONObject(0);
        Assert.assertEquals(hiddenMember.getString("userName"), "hidden");
        Assert.assertEquals(hiddenMember.keySet(), Set.of("id", "userName", "userAvatarURL"));
        Assert.assertFalse(secondPage.getBoolean("hasMore"));

        query.ids = Set.of("1", "2");
        Assert.assertEquals(query.classicBroadcast("当前话题").getInt("onlineChatCnt"), 3);
    }

    @Test
    public void failedReadDoesNotProduceASuccessFrame() throws Exception {
        final var query = fixture();
        query.fail = true;
        Assert.expectThrows(RepositoryException.class, () -> query.classicBroadcast("当前话题"));
        query.fail = false;
        query.ids = Set.of();
        query.sample = new ChatOnlineSnapshot.Sample(List.of(), 43, true);
        final var empty = query.classicBroadcast("当前话题");
        Assert.assertEquals(empty.getInt("onlineChatCnt"), 0);
        Assert.assertEquals(empty.getJSONArray("users").length(), 0);
    }

    @Test
    public void classicBroadcastIgnoresMissingProfileStatusAndSanitizesAvatarUrls() throws Exception {
        final var query = fixture();
        query.records.get(0).remove(UserExt.USER_ONLINE_STATUS);
        query.records.get(2).put(UserExt.USER_AVATAR_URL, "data:text/plain,invalid-avatar");
        final var frame = query.classicBroadcast("当前话题");
        final var members = frame.getJSONArray("users");
        Assert.assertEquals(frame.getInt("onlineChatCnt"), 4);
        Assert.assertEquals(members.getJSONObject(0).getString("userName"), "classic-local");
        final var v3Only = members.getJSONObject(3);
        Assert.assertEquals(v3Only.getString("userName"), "v3-only");
        Assert.assertEquals(v3Only.getString(UserExt.USER_AVATAR_URL), "");
        Assert.assertFalse(v3Only.getString("userAvatarURL48").startsWith("data:"));
    }

    private static Fixture fixture() throws Exception {
        final var query = new Fixture();
        final var field = ChatOnlineQueryService.class.getDeclaredField("avatars");
        field.setAccessible(true);
        field.set(query, new AvatarQueryService());
        return query;
    }

    private static JSONObject member(final String id, final String name, final int visibility, final int status) {
        return new JSONObject().put(Keys.OBJECT_ID, id).put(User.USER_NAME, name)
                .put(UserExt.USER_AVATAR_URL, "https://example.org/avatar.png")
                .put(UserExt.USER_ONLINE_STATUS, visibility).put(UserExt.USER_STATUS, status);
    }

    private static final class Fixture extends ChatOnlineQueryService {
        private ChatOnlineSnapshot.Sample sample = new ChatOnlineSnapshot.Sample(
                List.of("classic-node", "classic-node", "hidden", "disabled", "missing"), 42, false);
        private Set<String> ids = Set.of("1", "2", "3", "4");
        private boolean fail;
        private final List<JSONObject> records = List.of(member("1", "classic-local", 0, 0),
                member("2", "classic-node", 0, 0), member("3", "v3-only", 0, 0),
                member("4", "hidden", 1, 0), member("5", "disabled", 0, 4));

        @Override
        protected ChatOnlineSnapshot.Sample remote() {
            return sample;
        }

        @Override
        protected Set<String> localIds() {
            return ids;
        }

        @Override
        protected List<JSONObject> lookup(final String column, final List<String> values) throws RepositoryException {
            if (fail) throw new RepositoryException("test read failure");
            final var matches = new ArrayList<JSONObject>();
            for (final var record : records) {
                if (values.contains(record.getString(column))) matches.add(record);
            }
            return matches;
        }
    }
}
