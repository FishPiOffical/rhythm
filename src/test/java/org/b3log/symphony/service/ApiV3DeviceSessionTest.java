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
            System.out.println("通过：设备合并、当前设备、第一方权限、跨账号、刷新、重复退出与重新登录保护");
        }
    }
}
