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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.cache.MilestoneCache;
import org.b3log.symphony.model.Milestone;
import org.b3log.symphony.repository.MilestoneRepository;
import org.b3log.symphony.util.ApiV3MilestoneDto;
import org.json.JSONObject;
import org.testng.Assert;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.stream.Collectors;

/** 在临时 SQLite 上执行实际创建服务、仓库 SQL 及 DTO，MySQL 与 HTTP 行为另行验收。 */
public class ApiV3MilestoneServiceTest {
    public static void main(final String[] args) throws Exception {
        final var prop = Latkes.class.getDeclaredField("localProps");
        prop.setAccessible(true);
        final Object original = prop.get(null);
        final Properties p = new Properties(); p.setProperty("runtimeDatabase", "NONE"); prop.set(null, p);
        try (final Fixture f = new Fixture()) {
            final JSONObject input = new JSONObject().put(Milestone.MILESTONE_TITLE, "Title").put(Milestone.MILESTONE_DATE, 1792108800000L)
                    .put(Milestone.MILESTONE_CONTENT, "raw body").put(Milestone.MILESTONE_AUTHOR_ID, "forged")
                    .put(Milestone.MILESTONE_STATUS, Milestone.STATUS_C_APPROVED).put("userPassword", "secret").put(Keys.OBJECT_ID, "chosen-id");
            try { f.service.addMilestoneInCurrentTransaction("owner", input); Assert.fail(); } catch (final IllegalStateException expected) { }
            final Transaction tx = f.begin();
            final JSONObject row = f.service.addMilestoneInCurrentTransaction("owner", input);
            Assert.assertEquals(row.getString(Milestone.MILESTONE_AUTHOR_ID), "owner"); Assert.assertEquals(row.getInt(Milestone.MILESTONE_STATUS), 1);
            Assert.assertFalse(row.has("userPassword")); Assert.assertNotEquals(row.getString("oId"), "chosen-id"); Assert.assertEquals(f.clears, 0);
            tx.rollback(); Assert.assertNull(f.repo.getOwned("owner", row.getString("oId")));
            Assert.assertEquals(input.getInt(Milestone.MILESTONE_STATUS), 2);
            System.out.println("PASS current-transaction guard, pending whitelist, immutable input and rollback");
            final JSONObject legacy = new JSONObject(input.toString()).put(Milestone.MILESTONE_AUTHOR_ID, "owner");
            f.service.addMilestone(legacy); Assert.assertEquals(f.clears, 1); Assert.assertFalse(f.active());
            f.failAdd = true;
            try { f.service.addMilestone(legacy); Assert.fail(); } catch (final ServiceException expected) { }
            Assert.assertEquals(f.clears, 1); Assert.assertEquals(f.repo.countOwned("owner", 0), 1L); Assert.assertFalse(f.active()); f.failAdd = false;
            System.out.println("PASS actual legacy service commits before cache clear and rolls back failed insert");
            f.exec("INSERT INTO milestone(oId,milestoneAuthorId,milestoneStatus,milestoneCreateTime,milestoneDate,milestoneTitle,milestoneContent) VALUES ('a','owner',2,3,1792108800000,'Approved','body'),('b','owner',3,4,1792108800000,'Rejected','body'),('c','other',1,5,1792108800000,'Other','body'),('d','owner',99,6,1792108800000,'Invalid','body')");
            Assert.assertEquals(f.repo.countOwned("owner", 0), 3L); Assert.assertEquals(f.repo.countOwned("owner", 1), 1L);
            Assert.assertEquals(f.repo.countOwned("owner", 2), 1L); Assert.assertEquals(f.repo.countOwned("owner", 3), 1L);
            Assert.assertNull(f.repo.getOwned("other", "a")); Assert.assertNull(f.repo.getOwned("owner", "c")); Assert.assertNull(f.repo.getOwned("owner", "d"));
            Assert.assertNull(f.repo.getOwned("owner' OR 1=1 --", "a"));
            final var page1 = f.repo.pageOwned("owner", 0, 1, 2);
            final var page2 = f.repo.pageOwned("owner", 0, 2, 2);
            Assert.assertEquals(page1.size(), 2); Assert.assertEquals(page2.size(), 1);
            Assert.assertTrue(f.repo.pageOwned("owner", 0, 3, 2).isEmpty()); Assert.assertNotEquals(page1.getFirst().getString("oId"), page2.getFirst().getString("oId"));
            System.out.println("PASS actual owner/status/count SQL, disjoint pages, empty page and injection isolation");
            final JSONObject raw = f.repo.getOwned("owner", "a").put("userEmail", "private").put("milestoneMediaUrl", "javascript:alert(1)");
            final JSONObject dto = ApiV3MilestoneDto.owned(raw);
            Assert.assertEquals(dto.getString("status"), "approved"); Assert.assertEquals(dto.getString("content"), "body");
            Assert.assertEquals(dto.keySet(), Set.of("id", "title", "date", "endDate", "content", "media", "link", "status", "createdAt", "updatedAt"));
            Assert.assertEquals(dto.getJSONObject("media").getString("url"), ""); Assert.assertFalse(dto.toString().contains("private"));
            Assert.assertFalse(dto.has("milestoneAuthorId")); Assert.assertFalse(dto.has("contentHtml"));
            System.out.println("PASS owner DTO whitelist, source body, status and unsafe URL filtering");
        } finally { prop.set(null, original); }
    }
    private static class Fixture implements AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        int clears, nextId; boolean failAdd;
        final Repo repo = new Repo();
        final MilestoneMgmtService service = new MilestoneMgmtService();
        Fixture() throws Exception {
            exec("CREATE TABLE milestone(oId TEXT PRIMARY KEY,milestoneTitle TEXT,milestoneDate BIGINT,milestoneEndDate BIGINT DEFAULT 0,milestoneContent TEXT,milestoneMediaUrl TEXT DEFAULT '',milestoneMediaType TEXT DEFAULT '',milestoneMediaCaption TEXT DEFAULT '',milestoneLink TEXT DEFAULT '',milestoneAuthorId TEXT,milestoneStatus INT,milestoneCreateTime BIGINT,milestoneUpdateTime BIGINT DEFAULT 0)");
            inject("milestoneRepository", repo); inject("milestoneCache", new MilestoneCache() { public void clearTimelineEventsCache() { Assert.assertFalse(active()); clears++; } });
        }
        void inject(final String key, final Object value) throws Exception { final var field = MilestoneMgmtService.class.getDeclaredField(key); field.setAccessible(true); field.set(service, value); }
        boolean active() { try { return !db.getAutoCommit(); } catch (final Exception e) { throw new IllegalStateException(e); } }
        Transaction begin() {
            try { if (active()) throw new IllegalStateException("nested transaction"); db.setAutoCommit(false); } catch (final Exception e) { throw new IllegalStateException(e); }
            return new Transaction() {
                boolean active = true;
                public void commit() { try { db.commit(); db.setAutoCommit(true); active = false; } catch (final Exception e) { throw new IllegalStateException(e); } }
                public void rollback() { try { db.rollback(); db.setAutoCommit(true); active = false; } catch (final Exception e) { throw new IllegalStateException(e); } }
                public boolean isActive() { return active; }
            };
        }
        void exec(final String sql, final Object... args) throws RepositoryException {
            try (final var statement = db.prepareStatement(sql)) { for (int i=0;i<args.length;i++) statement.setObject(i+1,args[i]); statement.executeUpdate(); }
            catch (final Exception e) { throw new RepositoryException(e); }
        }
        class Repo extends MilestoneRepository {
            public String getName() { return "milestone"; }
            public boolean hasTransactionBegun() { return active(); }
            public Transaction beginTransaction() { return begin(); }
            public String add(final JSONObject row) throws RepositoryException {
                final String id = "test-" + (++nextId);
                row.put("oId", id);
                final var keys = new ArrayList<>(row.keySet());
                exec("INSERT INTO milestone (" + String.join(",",keys) + ") VALUES (" + keys.stream().map(k -> "?").collect(Collectors.joining(",")) + ")", keys.stream().map(row::get).toArray());
                if (failAdd) throw new RepositoryException("simulated failure");
                return id;
            }
            public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
                try (final var statement = db.prepareStatement(sql)) {
                    for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                    final List<JSONObject> rows = new ArrayList<>();
                    try (final var rs = statement.executeQuery()) { while (rs.next()) { final JSONObject row = new JSONObject(); for (int i=1;i<=rs.getMetaData().getColumnCount();i++) row.put(rs.getMetaData().getColumnLabel(i),rs.getObject(i)); rows.add(row); } }
                    return rows;
                } catch (final Exception e) { throw new RepositoryException(e); }
            }
        }
        public void close() throws Exception { db.close(); }
    }
}
