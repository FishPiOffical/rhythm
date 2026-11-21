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
package org.b3log.symphony.cache;

import org.b3log.latke.Keys;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.UserExt;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.Map;

/** Verifies that profile updates still keep ID and username lookups consistent. */
public class UserCacheTest {
    private static final String OLD_IP = "198.51.100.10";
    private static final String NEW_IP = "198.51.100.11";

    private final UserCache cache = new UserCache();

    @BeforeMethod
    @AfterMethod
    public void clearCaches() throws Exception {
        map("ID_CACHE").clear();
        map("NAME_CACHE").clear();
    }

    @Test
    public void updatingUserRetainsLatestLoginIpAsProfileMetadata() {
        cache.putUser(user("u1", "tester", OLD_IP));
        cache.putUser(user("u1", "tester", NEW_IP));

        Assert.assertEquals(cache.getUser("u1").getString(UserExt.USER_LATEST_LOGIN_IP), NEW_IP);
        Assert.assertEquals(cache.getUserByName("tester").getString(UserExt.USER_LATEST_LOGIN_IP), NEW_IP);
    }

    @Test
    public void removingUserDoesNotRemoveAnotherProfile() {
        cache.putUser(user("u1", "first", OLD_IP));
        cache.putUser(user("u2", "second", OLD_IP));
        cache.removeUser(user("u2", "second", OLD_IP));

        Assert.assertNull(cache.getUser("u2"));
        Assert.assertNull(cache.getUserByName("second"));
        Assert.assertEquals(cache.getUserByName("first").getString(Keys.OBJECT_ID), "u1");
    }

    private static JSONObject user(final String id, final String name, final String ip) {
        return new JSONObject().put(Keys.OBJECT_ID, id).put(User.USER_NAME, name)
                .put(UserExt.USER_LATEST_LOGIN_IP, ip);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> map(final String name) throws Exception {
        final Field field = UserCache.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<String, ?>) field.get(null);
    }
}
