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
import org.testng.Assert;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Real claim transition and prepared SQL, temporary SQLite ledger; not the full points service or MySQL locks. */
public class LivenessRewardClaimTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long NOW = Instant.parse("2026-10-17T04:00:00Z").toEpochMilli();
    public static void main(final String[] args) throws Exception {
        final var period = LivenessRewardClaim.Period.at(NOW, ZONE);
        Assert.assertEquals(period.date(), "20261016");
        Assert.assertEquals(period.start(), Instant.parse("2026-10-16T16:00:00Z").toEpochMilli());
        Assert.assertEquals(LivenessRewardClaim.Period.at(period.end(), ZONE).date(), "20261017");
        final var dst = LivenessRewardClaim.Period.at(Instant.parse("2026-03-08T12:00:00Z").toEpochMilli(), ZoneId.of("America/New_York"));
        Assert.assertEquals(dst.end() - dst.start(), 23L * 60 * 60 * 1000);
        System.out.println("PASS server-day boundaries, previous date and daylight-saving transition");
        Assert.assertTrue(LivenessRewardClaim.validExpectedDate("20240229"));
        for (String value : new String[]{"20230229", "00000101", "2026101", "20261016\n", "２０２６１０１６", "20261301"}) Assert.assertFalse(LivenessRewardClaim.validExpectedDate(value));
        try (final Fixture f = new Fixture()) {
            f.begin();
            try { LivenessRewardClaim.claim(f, () -> period.end(), ZONE, "20261016"); Assert.fail(); }
            catch (final LivenessRewardClaim.DateChanged expected) { Assert.assertTrue(f.locked); f.rollback(); }
            Assert.assertEquals(f.balance(), 100); Assert.assertTrue(f.rows("SELECT * FROM ledger").isEmpty());
            f.begin(); final var exact = LivenessRewardClaim.claim(f, () -> NOW, ZONE, "20261016"); f.commit();
            Assert.assertEquals(exact.date(), "20261016"); Assert.assertEquals(f.balance(), 150);
        }
        System.out.println("PASS optional expected date exact validation, date check after owner lock and crossed-midnight refusal without credit");
        try (final Fixture f = new Fixture()) {
            f.begin();
            final var first = LivenessRewardClaim.claim(f, () -> { Assert.assertTrue(f.locked); return NOW; }, ZONE); f.commit();
            Assert.assertEquals(first.amount(), 50); Assert.assertEquals(f.balance(), 150);
            Assert.assertEquals(first.dto().keySet(), java.util.Set.of("date", "amount", "claimed"));
            Assert.assertFalse(first.dto().toString().contains(first.transferId()));
            f.begin();
            try { LivenessRewardClaim.claim(f, NOW + 1, ZONE); Assert.fail(); } catch (final LivenessRewardClaim.Unavailable expected) { f.rollback(); }
            Assert.assertEquals(f.balance(), 150); Assert.assertEquals(f.rows("SELECT * FROM ledger").size(), 1);
            System.out.println("PASS first claim, DTO whitelist and repeat claim without second credit");
            f.exec("DELETE FROM ledger"); f.exec("UPDATE account SET balance=100");
            f.failTransfer = true; f.begin();
            try { LivenessRewardClaim.claim(f, NOW, ZONE); Assert.fail(); } catch (final IllegalStateException expected) { f.rollback(); }
            Assert.assertEquals(f.balance(), 100); Assert.assertTrue(f.rows("SELECT * FROM ledger").isEmpty());
            f.failTransfer = false; f.eligible = 0; f.begin();
            try { LivenessRewardClaim.claim(f, NOW, ZONE); Assert.fail(); } catch (final LivenessRewardClaim.Unavailable expected) { f.rollback(); }
            Assert.assertEquals(f.balance(), 100);
            f.eligible = -1; f.begin();
            try { LivenessRewardClaim.claim(f, NOW, ZONE); Assert.fail(); } catch (final LivenessRewardClaim.Unavailable expected) { f.rollback(); }
            System.out.println("PASS failed transfer rolls balance/ledger back; zero and negative eligibility refused");
            f.exec("INSERT INTO ledger VALUES ('before','sys','owner',18,?,50)", period.start()-1);
            f.exec("INSERT INTO ledger VALUES ('end','sys','owner',18,?,50)", period.end());
            f.exec("INSERT INTO ledger VALUES ('other','sys','someone',18,?,50)", NOW);
            f.exec("INSERT INTO ledger VALUES ('type','sys','owner',17,?,50)", NOW);
            f.exec("INSERT INTO ledger VALUES ('direction','owner','sys',18,?,50)", NOW);
            f.begin(); f.lockOwner(); Assert.assertFalse(f.alreadyClaimed(period)); f.rollback();
            f.exec("INSERT INTO ledger VALUES ('start','sys','owner',18,?,50)", period.start());
            f.begin(); f.lockOwner(); Assert.assertTrue(f.alreadyClaimed(period)); f.rollback();
            f.exec("INSERT INTO liveness VALUES ('owner','20261016',40),('owner','20261015',30),('someone','20261016',90)");
            final var rows = f.rows(LivenessRewardClaim.livenessSql("liveness", false), "owner", period.date());
            Assert.assertEquals(rows.size(), 1); Assert.assertEquals(rows.getFirst().getInt("livenessPoint"), 40);
            Assert.assertTrue(f.rows(LivenessRewardClaim.livenessSql("liveness", false), "owner' OR '1'='1", period.date()).isEmpty());
            System.out.println("PASS actual bound SQL isolates owner, direction, type, period and liveness date");
        }
    }
    private static class Fixture implements LivenessRewardClaim.Store, AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        boolean locked, failTransfer; int eligible = 50;
        Fixture() throws Exception {
            exec("CREATE TABLE ledger(oId TEXT PRIMARY KEY,fromId TEXT,toId TEXT,type INT,time BIGINT,sum INT)");
            exec("CREATE TABLE account(balance INT)"); exec("INSERT INTO account VALUES (100)");
            exec("CREATE TABLE liveness(livenessUserId TEXT,livenessDate TEXT,livenessPoint INT)");
        }
        void begin() throws Exception { db.setAutoCommit(false); locked = false; }
        void commit() throws Exception { db.commit(); db.setAutoCommit(true); locked = false; }
        void rollback() throws Exception { db.rollback(); db.setAutoCommit(true); locked = false; }
        public void lockOwner() throws Exception { Assert.assertFalse(db.getAutoCommit()); locked = true; }
        public boolean alreadyClaimed(final LivenessRewardClaim.Period period) throws Exception {
            Assert.assertTrue(locked);
            return !rows(LivenessRewardClaim.claimedSql("ledger", true), "sys", "owner", 18, period.start(), period.end()).isEmpty();
        }
        public int amount(final LivenessRewardClaim.Period period) { Assert.assertTrue(locked); return eligible; }
        public String transfer(final int amount, final long time) throws Exception {
            Assert.assertTrue(locked); final String id = "transfer-" + time;
            exec("UPDATE account SET balance=balance+?", amount);
            exec("INSERT INTO ledger VALUES (?,'sys','owner',18,?,?)", id, time, amount);
            return failTransfer ? null : id;
        }
        int balance() throws Exception { return rows("SELECT balance FROM account").getFirst().getInt("balance"); }
        void exec(final String sql, final Object... args) throws Exception {
            try (final var statement = db.prepareStatement(sql)) { for (int i=0;i<args.length;i++) statement.setObject(i+1,args[i]); statement.executeUpdate(); }
        }
        List<JSONObject> rows(final String sql, final Object... args) throws Exception {
            try (final var statement = db.prepareStatement(sql.replace(" FOR UPDATE", ""))) {
                for (int i=0;i<args.length;i++) statement.setObject(i+1,args[i]); final List<JSONObject> rows = new ArrayList<>();
                try (final var rs = statement.executeQuery()) {
                    while (rs.next()) { final var row = new JSONObject(); for (int i=1;i<=rs.getMetaData().getColumnCount();i++) row.put(rs.getMetaData().getColumnLabel(i),rs.getObject(i)); rows.add(row); }
                }
                return rows;
            }
        }
        public void close() throws Exception { db.close(); }
    }
}
