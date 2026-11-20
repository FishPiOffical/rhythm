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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.ApiV3Token;
import org.b3log.symphony.repository.ApiV3TokenRepository;
import org.b3log.symphony.repository.ApiV3ClientRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 真实认证服务与 JDBC 更新，存储故障及并发由测试端口注入。 */
public class ApiV3TokenActivityTest {
    static void inject(final Object target, final Class<?> owner, final String name, final Object value) throws Exception {
        final var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    static class Tokens extends ApiV3TokenRepository {
        final AtomicInteger roots = new AtomicInteger(), writes = new AtomicInteger(), reads = new AtomicInteger();
        final JSONObject row = new JSONObject().put("oId", "123").put("tokenType", "access").put("state", 0)
                .put("expiresAt", Long.MAX_VALUE).put("clientId", "website_v3").put("userId", "456")
                .put("scope", "profile.read").put("lastUsedAt", 0L);
        volatile boolean failCommit, failWrite, failRead;
        public boolean hasTransactionBegun() { return false; }
        public JSONObject getByHash(final String hash) throws RepositoryException {
            if (failRead) throw new RepositoryException("测试读取失败");
            reads.incrementAndGet();
            return new JSONObject(row.toString());
        }
        public Transaction beginTransaction() {
            roots.incrementAndGet();
            return new Transaction() {
                boolean active = true;
                public void commit() { if (failCommit) throw new IllegalStateException("test commit failure"); active = false; }
                public void rollback() { active = false; }
                public boolean isActive() { return active; }
            };
        }
        public void markAccessUsedInCurrentTransaction(final String id, final long now) throws RepositoryException {
            writes.incrementAndGet();
            if (failWrite) throw new RepositoryException("test update failure");
        }
    }

    static class Service extends ApiV3TokenMgmtService {
        long time = 1_000_000;
        protected long now() { return time; }
    }

    public static void main(final String[] args) throws Exception {
        final var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
        final var tokens = new Tokens();
        final var service = new Service();
        final var users = new AtomicInteger();
        final var clientStatus = new AtomicInteger();
        final var userStatus = new AtomicInteger();
        inject(service, ApiV3TokenMgmtService.class, "apiV3TokenRepository", tokens);
        inject(service, ApiV3TokenMgmtService.class, "apiV3ClientRepository", new ApiV3ClientRepository() {
            public JSONObject getByClientId(final String client) { return new JSONObject().put("status", clientStatus.get()); }
        });
        inject(service, ApiV3TokenMgmtService.class, "userRepository", new UserRepository() {
            public List<JSONObject> select(final String sql, final Object... arguments) {
                users.incrementAndGet();
                return List.of(new JSONObject().put("oId", "456").put("userStatus", userStatus.get()));
            }
        });
        final String credential = ApiV3TokenUtil.generate();
        for (int i = 0; i < 100; i++) Assert.assertNotNull(service.authenticate(credential));
        Assert.assertEquals(tokens.roots.get(), 1);
        Assert.assertEquals(tokens.reads.get(), 100);
        Assert.assertEquals(users.get(), 100);
        service.time += 59_999;
        Assert.assertNotNull(service.authenticate(credential));
        Assert.assertEquals(tokens.roots.get(), 1);
        service.time++;
        try (final var pool = Executors.newFixedThreadPool(16)) {
            final var tasks = new java.util.ArrayList<Future<JSONObject>>();
            for (int i = 0; i < 64; i++) tasks.add(pool.submit(() -> service.authenticate(credential)));
            for (final var future : tasks) Assert.assertNotNull(future.get());
        }
        Assert.assertEquals(tokens.roots.get(), 2);
        clientStatus.set(1);
        Assert.assertNull(service.authenticate(credential));
        clientStatus.set(0); userStatus.set(1);
        Assert.assertNull(service.authenticate(credential));
        userStatus.set(0);
        tokens.row.put("state", 1);
        Assert.assertNull(service.authenticate(credential));
        tokens.row.put("state", 0).put("expiresAt", service.time);
        Assert.assertNull(service.authenticate(credential));
        tokens.row.put("expiresAt", Long.MAX_VALUE);
        service.time += 60_000;
        tokens.failCommit = true;
        Assert.assertNotNull(service.authenticate(credential));
        final int failedRoots = tokens.roots.get();
        tokens.failCommit = false;
        Assert.assertNotNull(service.authenticate(credential));
        Assert.assertEquals(tokens.roots.get(), failedRoots + 1);
        service.time += 60_000;
        tokens.failWrite = true;
        Assert.assertNotNull(service.authenticate(credential));
        final int failedWrite = tokens.roots.get();
        tokens.failWrite = false;
        Assert.assertNotNull(service.authenticate(credential));
        Assert.assertEquals(tokens.roots.get(), failedWrite + 1);
        service.time += 60_000;
        tokens.row.put("lastUsedAt", service.time - 1);
        final int freshRoots = tokens.roots.get();
        Assert.assertNotNull(service.authenticate(credential));
        Assert.assertEquals(tokens.roots.get(), freshRoots);
        System.out.println("PASS per-request authorization, minute boundary, concurrent coalescing and failed commit/update retry");

        tokens.failRead = true;
        try {
            service.authenticatePartner(credential);
            Assert.fail("存储异常应返回服务繁忙");
        } catch (final ApiV3Exception e) {
            Assert.assertEquals(e.status, 503);
            Assert.assertEquals(e.code, "partner_busy");
        }
        tokens.failRead = false;
        tokens.row.put("expiresAt", service.time);
        Assert.assertNull(service.authenticatePartner(credential));
        tokens.row.put("expiresAt", Long.MAX_VALUE);
        Assert.assertNotNull(service.authenticatePartner(credential));
        System.out.println("PASS partner storage failure, expired credential and recovery");

        try (final Connection db = DriverManager.getConnection("jdbc:h2:mem:tokenActivity;MODE=MySQL")) {
            db.createStatement().execute("CREATE TABLE token(oId VARCHAR(19) PRIMARY KEY,tokenType VARCHAR(16),state INT,expiresAt BIGINT,lastUsedAt BIGINT,updatedAt BIGINT)");
            db.createStatement().execute("INSERT INTO token VALUES('123','access',0,99999999,0,0)");
            db.setAutoCommit(false);
            final var repository = new ApiV3TokenRepository() {
                public String getName() { return "token"; }
                public boolean hasTransactionBegun() { return true; }
                protected Connection currentConnection() { return db; }
            };
            repository.markAccessUsedInCurrentTransaction("123", 100_000);
            db.commit();
            repository.markAccessUsedInCurrentTransaction("123", 100_001);
            db.commit();
            try (var rows = db.createStatement().executeQuery("SELECT lastUsedAt FROM token")) {
                rows.next(); Assert.assertEquals(rows.getLong(1), 100_000L);
            }
            repository.markAccessUsedInCurrentTransaction("123", 160_000);
            db.commit();
            db.createStatement().executeUpdate("UPDATE token SET state=1");
            db.commit();
            repository.markAccessUsedInCurrentTransaction("123", 220_000);
            db.commit();
            try (var rows = db.createStatement().executeQuery("SELECT state,lastUsedAt FROM token")) {
                rows.next(); Assert.assertEquals(rows.getInt(1), 1); Assert.assertEquals(rows.getLong(2), 160_000L);
            }
            db.createStatement().executeUpdate("UPDATE token SET state=0,expiresAt=220000"); db.commit();
            repository.markAccessUsedInCurrentTransaction("123", 220_000); db.commit();
            db.createStatement().executeUpdate("UPDATE token SET expiresAt=99999999,tokenType='refresh'"); db.commit();
            repository.markAccessUsedInCurrentTransaction("123", 220_000); db.commit();
            try (var rows = db.createStatement().executeQuery("SELECT lastUsedAt FROM token")) {
                rows.next(); Assert.assertEquals(rows.getLong(1), 160_000L);
            }
        }
        System.out.println("PASS real JDBC conditional activity update never overwrites revocation or a newer activity timestamp");
    }
}
