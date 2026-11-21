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

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.repository.LogsRepository;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ReportQueryServiceTest {
    private final Map<String, Object> previous = new HashMap<>();

    @BeforeClass
    public void setup() throws Exception {
        final Properties properties = new Properties();
        properties.setProperty("runtimeDatabase", "NONE");
        for (final String name : List.of("latkeProps", "localProps")) {
            final var field = Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            previous.put(name, field.get(null));
            field.set(null, properties);
        }
    }

    @AfterClass(alwaysRun = true)
    public void restore() throws Exception {
        for (final var entry : previous.entrySet()) {
            final var field = Latkes.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(null, entry.getValue());
        }
    }

    private record Call(String sql, Object[] args) { }

    private static final class Fixture {
        private final ReportQueryService service = new ReportQueryService();
        private final List<Call> calls = new ArrayList<>();
        private final JSONObject row = new JSONObject().put("oId", "123");

        private Fixture(final int hit, final boolean fail) throws Exception {
            final var field = ReportQueryService.class.getDeclaredField("logsRepository");
            field.setAccessible(true);
            field.set(service, new LogsRepository() {
                @Override
                public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
                    calls.add(new Call(sql, args));
                    if (fail) throw new RepositoryException("query failed");
                    return calls.size() == hit ? List.of(row) : List.of();
                }
            });
        }
    }

    @Test(timeOut = 60000)
    public void exactMatchDoesNotRunFallback() throws Exception {
        final Fixture f = new Fixture(1, false);
        Assert.assertSame(f.service.findChatRevokeLog("123"), f.row);
        Assert.assertEquals(f.calls.size(), 1);
        Assert.assertTrue(f.calls.getFirst().sql().contains("WHERE type=? AND key3=?"));
        Assert.assertEquals(f.calls.getFirst().args(), new Object[]{"cr-revoke", "123"});
    }

    @Test(timeOut = 60000)
    public void historicalMatchAndMissingLogKeepOriginalResults() throws Exception {
        final Fixture f = new Fixture(2, false);
        Assert.assertSame(f.service.findChatRevokeLog("123"), f.row);
        Assert.assertEquals(f.calls.size(), 2);
        Assert.assertTrue(f.calls.get(1).sql().contains("WHERE type=? AND data LIKE ? ESCAPE '!'"));
        Assert.assertTrue(f.calls.get(1).sql().endsWith("ORDER BY oId DESC LIMIT 1"));
        Assert.assertEquals(f.calls.get(1).args(), new Object[]{"cr-revoke", "%\"oId\":\"123\"%"});
        Assert.assertNull(new Fixture(0, false).service.findChatRevokeLog("123"));
    }

    @Test(timeOut = 60000)
    public void maliciousIdsRemainBoundDataAndCannotExpandLikePattern() throws Exception {
        final String id = "x' OR SLEEP(5)--%_!";
        final Fixture f = new Fixture(0, false);
        Assert.assertNull(f.service.findChatRevokeLog(id));
        Assert.assertEquals(f.calls.size(), 2);
        for (final Call call : f.calls) {
            Assert.assertFalse(call.sql().contains("SLEEP"));
            Assert.assertFalse(call.sql().contains(id));
        }
        Assert.assertEquals(f.calls.getFirst().args(), new Object[]{"cr-revoke", id});
        Assert.assertEquals(f.calls.get(1).args(), new Object[]{"cr-revoke", "%\"oId\":\"x' OR SLEEP(5)--!%!_!!\"%"});
    }

    @Test(timeOut = 60000)
    public void queryFailureReachesExistingCallerErrorHandling() throws Exception {
        final Fixture f = new Fixture(0, true);
        Assert.expectThrows(RepositoryException.class, () -> f.service.findChatRevokeLog("123"));
        Assert.assertEquals(f.calls.size(), 1);
    }
}
