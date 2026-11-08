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

/** Verifies that cached latest-login IP mappings follow user updates. */
public class UserCacheTest {
    private static final String OLD_IP = "198.51.100.10";
    private static final String NEW_IP = "198.51.100.11";

    private final UserCache cache = new UserCache();

    @BeforeMethod
    @AfterMethod
    public void clearCaches() throws Exception {
        map("ID_CACHE").clear();
        map("NAME_CACHE").clear();
        map("IP_CACHE").clear();
    }

    @Test
    public void replacingUserMovesLatestLoginIpMapping() {
        cache.putUser(user("u1", "tester", OLD_IP));
        cache.putUser(user("u1", "tester", NEW_IP));

        Assert.assertFalse(UserCache.hasUserByIP(OLD_IP));
        Assert.assertTrue(UserCache.hasUserByIP(NEW_IP));
        Assert.assertEquals(UserCache.getUserNameByIP(NEW_IP), "tester");
    }

    @Test
    public void removingUserPreservesSharedIpMapping() {
        cache.putUser(user("u1", "first", OLD_IP));
        cache.putUser(user("u2", "second", OLD_IP));
        cache.removeUser(user("u2", "second", OLD_IP));

        Assert.assertTrue(UserCache.hasUserByIP(OLD_IP));
        Assert.assertEquals(UserCache.getUserNameByIP(OLD_IP), "first");
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
