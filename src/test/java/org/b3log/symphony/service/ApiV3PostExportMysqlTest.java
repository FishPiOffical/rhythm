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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;

import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * 在真实 MySQL 上执行生产导出操作，继承辅助表、仓库和缓存的测试适配器。
 */
public class ApiV3PostExportMysqlTest extends ApiV3PostExportTest {
    static class Mysql extends F {
        boolean hold, readCommitted, snapshotObserved, regularObserved;
        String release;

        Mysql() throws Exception {
            super();
            System.setProperty("rhythm.mysql.fixtureSetup", "true");
        }

        @Override
        Transaction begin() {
            Transaction result = super.begin();
            if (readCommitted) try {
                writer.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            if (!hold && !readCommitted) return result;
            Connection previous = writer;
            writer = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, (p, m, a) -> {
                try {
                    if (m.getName().equals("commit") && readCommitted) {
                        if (roots == 2) {
                            Assert.assertEquals(previous.getTransactionIsolation(), Connection.TRANSACTION_REPEATABLE_READ);
                            snapshotObserved = true;
                        } else {
                            Assert.assertEquals(previous.getTransactionIsolation(), Connection.TRANSACTION_READ_COMMITTED);
                            if (roots == 3) regularObserved = true;
                        }
                    }
                    if (m.getName().equals("commit") && hold && roots == 3) {
                        System.out.println("BEFORE_FINAL_COMMIT");
                        System.out.flush();
                        long end = System.currentTimeMillis() + 20000;
                        while (!Files.exists(Path.of(release))) {
                            if (System.currentTimeMillis() > end) throw new SQLException("release timeout");
                            Thread.sleep(20);
                        }
                    }
                    return m.invoke(previous, a);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
            try {
                var field = JdbcTransaction.class.getDeclaredField("connection");
                field.setAccessible(true);
                field.set(tx, writer);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return result;
        }
    }

    static void committed(Mysql f) throws Exception {
        Assert.assertEquals(f.balance(), 400);
        Assert.assertEquals(f.n("artifacts"), 1);
        Assert.assertEquals(f.n("export_receipts"), 1);
        Assert.assertEquals(f.n("transfers"), 1);
        Assert.assertTrue(f.n("chunks") >= 1);
        Assert.assertEquals(f.n("transfer_index"), 2);
    }

    public static void main(String[] args) throws Exception {
        var p = new Properties();
        p.setProperty("runtimeDatabase", "NONE");
        p.setProperty("contextPath", "");
        Latkes.setLatkeProps(p);
        Latkes.setLocalProps(p);
        boolean attached = Set.of("worker", "verify").contains(args[0]);
        System.setProperty("rhythm.mysql.fixtureSetup", String.valueOf(!attached));
        try (var f = new Mysql()) {
            var env = rows(f.db, "SELECT VERSION() AS version,@@transaction_isolation AS isolation,@@sql_mode AS mode").getFirst();
            Assert.assertTrue(env.getString("version").startsWith("8.4."));
            Assert.assertEquals(env.getString("isolation"), "REPEATABLE-READ");
            Assert.assertTrue(env.getString("mode").contains("STRICT_TRANS_TABLES"));
            Assert.assertEquals(rows(f.db, "SELECT DATA_TYPE AS kind FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='artifacts' AND COLUMN_NAME='paidPoints'").getFirst().getString("kind"), "int");
            Assert.assertEquals(rows(f.db, "SELECT DATA_TYPE AS kind FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chunks' AND COLUMN_NAME='data'").getFirst().getString("kind"), "mediumtext");
            switch (args[0]) {
                case "setup" -> System.out.println("SETUP_READY");
                case "normal" -> {
                    f.readCommitted = true;
                    byte[] data = new byte[300000];
                    new Random(7).nextBytes(data);
                    String body = Base64.getEncoder().encodeToString(data);
                    sql(f.db, "UPDATE articles SET articleContent=? WHERE oId='1'", body);
                    JSONObject job = f.create("mysql");
                    Assert.assertTrue(f.snapshotObserved);
                    Assert.assertTrue(f.regularObserved);
                    committed(f);
                    Assert.assertTrue(f.n("chunks") > 1);
                    var zip = unzip(f.export.download(identity(), job.getString("jobId")));
                    Assert.assertEquals(zip.get("articles/1.md"), body);
                    Assert.assertEquals(zip.get("articles/6.reward.md"), "PAID_SECRET");
                    Assert.assertEquals(new JSONObject(zip.get("manifest.json")).getJSONArray("comments").length(), 8);
                    Assert.assertEquals(f.create("mysql").getString("jobId"), job.getString("jobId"));
                    committed(f);
                    System.out.println("PASS export mysql 1 exact artifact/chunk DDL, real per-root READ_COMMITTED baseline upgraded to RR snapshot/UTF8 ZIP/multi-chunk hash and atomic 100-point charge/receipt; same-key replay once " + env);
                }
                case "fault" -> {
                    f.exportFault = args[1];
                    try {
                        f.create("fault");
                        Assert.fail("fault must fail");
                    } catch (ApiV3Exception e) {
                        Assert.assertEquals(e.status, 503);
                    }
                    if (args[1].equals("ack")) {
                        Assert.assertEquals(f.balance(), 400);
                        Assert.assertEquals(f.n("artifacts"), 1);
                    } else {
                        Assert.assertEquals(f.balance(), 500);
                        for (String table : List.of("artifacts", "chunks", "transfers", "export_receipts"))
                            Assert.assertEquals(f.n(table), 0L, table);
                    }
                    f.exportFault = "";
                    var job = f.create("fault");
                    committed(f);
                    Assert.assertEquals(f.export.download(identity(), job.getString("jobId")).length, job.getInt("byteLength"));
                    System.out.println("PASS export mysql fault " + args[1] + " actual JDBC rollback/lost-ack and same-key recovery with one complete ready artifact and charge");
                }
                case "worker" -> {
                    f.hold = args[1].equals("holder");
                    f.release = args[2];
                    System.out.println("STARTING_CREATE");
                    System.out.flush();
                    var job = f.create("race");
                    System.out.println("RESULT=" + job);
                }
                case "verify" -> {
                    committed(f);
                    Assert.assertEquals(rows(f.db, "SELECT SUM(sum) AS total FROM transfers").getFirst().getInt("total"), 100);
                    System.out.println("PASS export mysql final committed artifact/chunks/receipt/100-point ledger verified from independent JDBC connection");
                }
                default -> throw new IllegalArgumentException(args[0]);
            }
        }
    }
}
