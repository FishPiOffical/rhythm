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

import com.github.benmanes.caffeine.cache.Cache;
import org.b3log.latke.Latkes;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Test(singleThreaded = true, timeOut = 60_000)
public class FirewallCcWhitelistTest {
    private static final String TRUSTED = "198.51.100.211";
    private static final String OTHER = "198.51.100.212";

    private static Object field(final String name) throws Exception {
        final Field field = Firewall.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    @BeforeMethod
    public void prepare() {
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
    }

    @SuppressWarnings("unchecked")
    @AfterMethod(alwaysRun = true)
    public void cleanup() throws Exception {
        ((Set<String>) field("CC_WHITELIST")).remove(TRUSTED);
        ((Map<String, ?>) field("COUNTERS")).remove(TRUSTED);
        ((Map<String, ?>) field("COUNTERS")).remove(OTHER);
        ((Cache<String, Boolean>) field("BANNED")).invalidate(TRUSTED);
        Firewall.setEnabled(true);
    }

    @SuppressWarnings("unchecked")
    public void whitelistExemptsCountingButNotManualBans() throws Exception {
        Firewall.setEnabled(true);
        Firewall.addCcWhitelist(TRUSTED);
        for (int i = 0; i < 500; i++) {
            Assert.assertTrue(Firewall.recordAndMaybeBan(TRUSTED));
        }
        Assert.assertFalse(((Map<String, ?>) field("COUNTERS")).containsKey(TRUSTED));
        ((Cache<String, Boolean>) field("BANNED")).put(TRUSTED, true);
        Assert.assertFalse(Firewall.recordAndMaybeBan(TRUSTED));
        Assert.assertTrue(Firewall.recordAndMaybeBan(OTHER));
        Assert.assertTrue(((Map<String, ?>) field("COUNTERS")).containsKey(OTHER));
    }
}
