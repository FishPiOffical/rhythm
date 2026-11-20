package org.b3log.symphony.service;

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.repository.ApiV3TokenRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;
import org.testng.Assert;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 在本地 JDBC 数据库中验证设备分组、权限和退出请求重放。 */
public class ApiV3DeviceSessionTest {
    private static void session(final F f, final String id, final String actor, final String client, final String device,
                                final String family, final String type) throws Exception {
        sql(f.db, "INSERT INTO tokens (oId,userId,clientId,tokenType,state,expiresAt,scope,updatedAt,"
                + "websiteDeviceId,websiteDeviceLabel,familyId,createdAt,lastUsedAt) VALUES (?,?,?,?,0,?,?,0,?,'手机',?,5,15)",
                id, actor, client, type, Long.MAX_VALUE, "profile.read settings.write", device, family);
    }

    private static void fails(final String code, final A run) throws Exception {
        try {
            run.run();
            Assert.fail("应拒绝此操作");
        } catch (final ApiV3Exception e) {
            Assert.assertEquals(e.code, code);
        }
    }

    private static void activityAcrossExpiry(final F f, final ApiV3DeviceSessionService devices,
                                             final JSONObject owner) throws Exception {
        // 同一台手机最后访问在 900，刷新令牌签发在 100，另一台设备的活动在两者之间。
        session(f, "209", "1", "website_v3", "phone", "new", "access");
        sql(f.db, "UPDATE tokens SET createdAt=100,lastUsedAt=0 WHERE oId='208'");
        sql(f.db, "UPDATE tokens SET createdAt=100,lastUsedAt=900 WHERE oId='209'");
        sql(f.db, "UPDATE tokens SET lastUsedAt=600 WHERE oId='100'");
        final var before = devices.list(owner).getJSONArray("list");
        final var first = before.getJSONObject(0);
        final String handle = first.getString("id");
        Assert.assertFalse(first.getBoolean("current"));
        Assert.assertEquals(first.getLong("lastActiveAt"), 900L);
        Assert.assertEquals(before.getJSONObject(1).getLong("lastActiveAt"), 600L);

        // 已结束的家族即使有更新的记录，也不能参与这次手机登录的时间或退出编号。
        sql(f.db, "UPDATE tokens SET lastUsedAt=1100 WHERE oId='201'");
        session(f, "210", "1", "website_v3", "phone", "ended", "access");
        sql(f.db, "UPDATE tokens SET lastUsedAt=1200,expiresAt=1300 WHERE oId='210'");
        session(f, "212", "2", "website_v3", "phone", "new", "access");
        session(f, "213", "1", "third", "phone", "new", "access");
        sql(f.db, "UPDATE tokens SET lastUsedAt=1800 WHERE oId IN ('212','213')");
        sql(f.db, "UPDATE tokens SET expiresAt=1000 WHERE oId='209'");
        final var after = devices.list(owner).getJSONArray("list");
        Assert.assertEquals(after.length(), 2);
        final var top = after.getJSONObject(0);
        final var phone = top.getBoolean("current") ? after.getJSONObject(1) : top;
        Assert.assertEquals(phone.getLong("lastActiveAt"), 900L, "访问令牌到期后手机的最近活动应保留");
        Assert.assertEquals(phone.getString("id"), handle);
        Assert.assertFalse(top.getBoolean("current"), "最近使用的手机仍应排第一");

        sql(f.db, "UPDATE tokens SET expiresAt=1400 WHERE oId='208'");
        final var remaining = devices.list(owner).getJSONArray("list");
        Assert.assertEquals(remaining.length(), 1);
        Assert.assertTrue(remaining.getJSONObject(0).getBoolean("current"));
        session(f, "211", "1", "website_v3", "phone", "next-login", "refresh");
        final var fresh = devices.list(owner).getJSONArray("list").getJSONObject(1);
        Assert.assertEquals(fresh.getLong("lastActiveAt"), 15L);
        Assert.assertNotEquals(fresh.getString("id"), handle);
        fails("device_changed", () -> devices.revoke(owner, handle));
        Assert.assertTrue(devices.revoke(owner, fresh.getString("id")).getBoolean("revoked"));
        Assert.assertEquals(devices.list(owner).getJSONArray("list").length(), 1);
    }

    public static void main(String[] args) throws Exception {
        final var properties = new Properties();
        properties.setProperty("runtimeDatabase", "NONE");
        properties.setProperty("contextPath", "");
        Latkes.setLatkeProps(properties);
        Latkes.setLocalProps(properties);
        try (final var f = new F()) {
            sql(f.db, "ALTER TABLE tokens ADD COLUMN websiteDeviceId TEXT DEFAULT ''");
            sql(f.db, "ALTER TABLE tokens ADD COLUMN websiteDeviceLabel TEXT DEFAULT ''");
            sql(f.db, "ALTER TABLE tokens ADD COLUMN familyId TEXT DEFAULT ''");
            sql(f.db, "ALTER TABLE tokens ADD COLUMN createdAt INT DEFAULT 1");
            sql(f.db, "ALTER TABLE tokens ADD COLUMN lastUsedAt INT DEFAULT 0");
            sql(f.db, "UPDATE tokens SET websiteDeviceId='desktop',websiteDeviceLabel='电脑',familyId='a',expiresAt=? WHERE oId='100'", Long.MAX_VALUE);
            session(f, "201", "1", "website_v3", "phone", "b", "refresh");
            session(f, "202", "1", "website_v3", "phone", "b", "access");
            session(f, "203", "1", "website_v3", "phone", "c", "refresh");
            session(f, "204", "2", "website_v3", "different", "d", "access");
            session(f, "205", "1", "third", "third", "e", "access");
            session(f, "206", "1", "website_v3", "", "f", "access");
            final var devices = new ApiV3DeviceSessionService();
            final var tokens = new ApiV3TokenRepository() {
                public String getName() { return "tokens"; }
                public boolean hasTransactionBegun() { return f.active(); }
                public Transaction beginTransaction() { return ApiV3Transactions.strict(f.begin()); }
                public List<JSONObject> select(String query, Object... values) throws RepositoryException {
                    return f.selectSQL(query.replace("GREATEST(", "MAX("), values);
                }
            };
            inject(devices, "tokens", tokens);
            inject(devices, "proofs", f.proof);
            final var owner = identity();
            final var list = devices.list(owner).getJSONArray("list");
            Assert.assertEquals(list.length(), 2);
            final var phone = list.getJSONObject(0);
            final var desktop = list.getJSONObject(1);
            Assert.assertEquals(phone.keySet(), Set.of("id", "label", "lastActiveAt", "current"));
            Assert.assertTrue(desktop.getBoolean("current"));
            Assert.assertFalse(phone.getBoolean("current"));
            Assert.assertEquals(phone.getLong("lastActiveAt"), 15L);
            fails("current_device", () -> devices.revoke(owner, desktop.getString("id")));
            final String id = phone.getString("id");
            final var other = new JSONObject().put("tokenId", "204").put("user", new JSONObject().put("oId", "2"));
            devices.revoke(other, id);
            Assert.assertEquals(devices.list(owner).getJSONArray("list").length(), 2);
            error(403, () -> devices.list(identity().put("tokenId", "205")));
            error(403, () -> devices.revoke(identity().put("tokenId", "206"), id));
            error(422, () -> devices.revoke(owner, "missing"));
            session(f, "207", "1", "website_v3", "phone", "c", "access");
            Assert.assertEquals(devices.list(owner).getJSONArray("list").getJSONObject(0).getString("id"), id);
            Assert.assertTrue(devices.revoke(owner, id).getBoolean("revoked"));
            Assert.assertTrue(devices.revoke(owner, id).getBoolean("revoked"));
            Assert.assertEquals(devices.list(owner).getJSONArray("list").length(), 1);
            session(f, "208", "1", "website_v3", "phone", "new", "refresh");
            fails("device_changed", () -> devices.revoke(owner, id));
            Assert.assertEquals(rows(f.db,"SELECT oId FROM tokens WHERE state=0 AND oId IN ('100','204','205','206','208')").size(), 5);
            activityAcrossExpiry(f, devices, owner);
            System.out.println("通过：设备合并、当前设备、第一方权限、跨账号、刷新、重复退出与重新登录保护");
        }
    }
}
