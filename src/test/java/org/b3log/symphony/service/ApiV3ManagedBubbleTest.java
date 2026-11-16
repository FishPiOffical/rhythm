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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.repository.*;
import org.json.JSONObject;
import org.testng.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 使用独立 SQLite 数据库验证气泡目录、发布与消费。 */
public class ApiV3ManagedBubbleTest {
    static final String PET = "bubble-custom-momota";
    static final String PAID = "bubble-custom-test";
    static final String VIP = "bubble-custom-test-vip";

    static class Fixture extends ApiV3AppearanceShopTest.Fixture {
        final ApiV3BubbleStyleService bubbles = new ApiV3BubbleStyleService();

        Fixture() throws Exception {
            final String migration = Files.readString(Path.of("sql/20261116_chat_bubble_style.sql"))
                    .replaceFirst("(?m)^--.*\n", "").replace("symphony_chat_bubble_style", "bubble")
                    .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin", "")
                    .replace("INSERT IGNORE", "INSERT OR IGNORE");
            for (int run = 0; run < 2; run++) {
                for (final String statement : migration.split(";")) {
                    if (!statement.isBlank()) sql(db, statement);
                }
            }
            for (final String field : List.of("userName", "userNickname", "userRole", "userEmail", "userAvatarType", "userAvatarURL")) {
                sql(db, "ALTER TABLE u ADD COLUMN " + field + " TEXT DEFAULT ''");
            }
            sql(db, "UPDATE u SET userName='owner',userNickname='设计预览',userRole=? WHERE oId='1'", Role.ROLE_ID_C_ADMIN);
            sql(db, "UPDATE tokens SET scope='profile.read settings.write' WHERE oId='102'");
            sql(db, "CREATE TABLE bubble_audit(oId TEXT PRIMARY KEY,operationUserId TEXT,operationCreated BIGINT,operationCode INT,operationDataId TEXT,operationIP TEXT,operationUA TEXT)");

            final var manager = new ApiV3AdminService();
            inject(manager, "requests", ApiV3QqSettingsTest.field(service, ApiV3IdentityApplicationService.class, "requests"));
            inject(manager, "proofs", proof);
            inject(manager, "users", new UserRepository() {
                public String getName() { return "u"; }
                public List<JSONObject> select(String query, Object... parameters) throws RepositoryException {
                    return selectSQL(query, parameters);
                }
            });
            inject(manager, "reviewers", new ApiV3IdentityReviewerRepository() {
                public List<JSONObject> select(String query, Object... parameters) {
                    return List.of();
                }
            });
            inject(manager, "roleQueryService", new RoleQueryService() {
                public Set<String> getPermissions(String role) {
                    return Role.ROLE_ID_C_ADMIN.equals(role) ? Set.of(Permission.PERMISSION_ID_C_MENU_ADMIN) : Set.of();
                }
            });
            inject(manager, "operations", new OperationRepository() {
                public String add(JSONObject row) throws RepositoryException {
                    return insert("bubble_audit", row.put("oId", Ids.genTimeMillisId()));
                }
            });
            inject(bubbles, "admin", manager);
            inject(bubbles, "styles", new ChatBubbleStyleRepository() {
                public String getName() { return "bubble"; }
                public List<JSONObject> select(String query, Object... parameters) throws RepositoryException {
                    return selectSQL(query, parameters);
                }
                public String add(JSONObject row) throws RepositoryException { return insert("bubble", row); }
                public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                    modify("bubble", id, row, fields);
                }
            });
            final var prices = new AppearanceProductRepository() {
                public String getName() { return "product"; }
                public List<JSONObject> select(String query, Object... parameters) throws RepositoryException {
                    return selectSQL(query, parameters);
                }
                public String add(JSONObject row) throws RepositoryException { return insert("product", row); }
                public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                    modify("product", id, row, fields);
                }
            };
            inject(bubbles, "products", prices);
            inject(shop, "products", prices);
            inject(shop, "bubbles", bubbles);
            inject(appearance, "bubbles", bubbles);
        }

        JSONObject command(JSONObject actor, String id, JSONObject body, String key) throws Exception {
            final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/");
            try {
                raw.headers().set("X-Forwarded-For", "127.0.0.1").set("User-Agent", "Managed bubble test");
                return bubbles.update(actor, id, body, key, new Request(null, raw));
            } finally {
                raw.release();
            }
        }
    }

    static void initialize() {
        final var props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        props.setProperty("contextPath", "");
        Latkes.setLatkeProps(props);
        Latkes.setLocalProps(props);
    }

    static JSONObject action(String name, int revision) {
        return new JSONObject().put("action", name).put("revision", revision);
    }

    public static void main(String[] args) throws Exception {
        initialize();
        try (final var f = new Fixture()) {
            final var owner = identity();
            final var member = new JSONObject().put("tokenId", "102").put("user", new JSONObject().put("oId", "2"));
            final var drafts = f.bubbles.adminList(owner, 1, 20).getJSONArray("list");
            Assert.assertEquals(drafts.length(), 4);
            Assert.assertTrue(f.bubbles.available().getJSONArray("list").isEmpty());
            error(403, () -> f.bubbles.adminList(member, 1, 20));
            error(403, () -> f.command(member, PET, action("publish", 1), "unauthorized"));
            final var original = f.snapshot("10").getJSONObject("appearance");
            final var equipPet = ApiV3ChatAppearanceTest.input(PET, "none");
            error(409, () -> f.appearance.update(owner, equipPet, "draft"));

            final var published = f.command(owner, PET, action("publish", 1), "publish");
            Assert.assertEquals(published.getInt("publishedRevision"), 1);
            Assert.assertTrue(published.similar(f.command(owner, PET, action("publish", 1), "publish")));
            Assert.assertEquals(f.count("bubble_audit"), 1);
            error(409, () -> f.command(owner, PET, action("publish", 1), "stale"));
            f.appearance.update(owner, equipPet, "equip");
            final var first = f.snapshot("11").getJSONObject("appearance");
            Assert.assertEquals(first.getInt("bubbleRevision"), 1);

            final var draft = published.getJSONObject("draft");
            draft.getJSONObject("style").put("radius", 21);
            final var invalid = new JSONObject(draft.toString());
            invalid.getJSONObject("style").put("image", "https://outside.invalid/decoration.png");
            error(422, () -> f.command(owner, PET, action("save", 2).put("draft", invalid), "invalid"));
            final var weakContrast = new JSONObject(draft.toString());
            weakContrast.getJSONObject("style").getJSONObject("light").put("text", "#ffffff");
            error(422, () -> f.command(owner, PET, action("save", 2).put("draft", weakContrast), "contrast"));
            f.command(owner, PET, action("save", 2).put("draft", draft), "save");
            Assert.assertTrue(first.similar(f.snapshot("12").getJSONObject("appearance")));
            f.command(owner, PET, action("publish", 3), "publish-next");
            final var next = f.snapshot("13").getJSONObject("appearance");
            Assert.assertEquals(next.getJSONObject("bubbleStyle").getInt("radius"), 21);
            Assert.assertEquals(next.getInt("bubbleRevision"), 2);
            Assert.assertTrue(first.similar(f.projected("11").getJSONObject("appearance")));
            Assert.assertTrue(original.similar(f.projected("10").getJSONObject("appearance")));
            f.command(owner, PET, action("unpublish", 4), "down");
            Assert.assertTrue(f.bubbles.available().getJSONArray("list").isEmpty());
            f.appearance.update(owner, ApiV3ChatAppearanceTest.input(PET, "fishpond-blue"), "change-frame");
            Assert.assertEquals(f.snapshot("14").getJSONObject("appearance").getString("bubbleId"), PET);
            f.appearance.update(owner, ApiV3ChatAppearanceTest.input("classic", "none"), "switch-away");
            error(409, () -> f.appearance.update(owner, equipPet, "equip-down"));

            final var paid = new JSONObject(draft.toString()).put("access", "points").put("price7", 64).put("price30", 224);
            f.command(owner, PAID, action("save", 0).put("draft", paid), "paid-save");
            f.command(owner, PAID, action("publish", 1), "paid-publish");
            Assert.assertEquals(f.shop.catalog().getJSONArray("list").length(), 10);
            final var equipPaid = ApiV3ChatAppearanceTest.input(PAID, "none");
            error(403, () -> f.appearance.update(owner, equipPaid, "unpaid"));
            final var order = ApiV3AppearanceShopTest.purchase(PAID);
            final var receipt = f.shop.buy(owner, order, "purchase");
            Assert.assertTrue(receipt.similar(f.shop.buy(owner, order, "purchase")));
            Assert.assertEquals(f.balance(), 9936);
            f.appearance.update(owner, equipPaid, "equip-paid");
            Assert.assertEquals(f.snapshot("15").getJSONObject("appearance").getString("bubbleId"), PAID);
            f.command(owner, PAID, action("unpublish", 2), "paid-down");
            error(409, () -> f.shop.buy(owner, order, "purchase-down"));
            f.appearance.update(owner, equipPaid, "equip-owned-down");
            sql(f.db, "UPDATE entitlement SET expiresAt=? WHERE productId=?", NOW, PAID);
            Assert.assertEquals(f.snapshot("16").getJSONObject("appearance").getString("bubbleId"), "classic");
            Assert.assertEquals(f.projected("15").getJSONObject("appearance").getString("bubbleId"), PAID);

            final var vip = new JSONObject(draft.toString()).put("access", "vip");
            f.command(owner, VIP, action("save", 0).put("draft", vip), "vip-save");
            f.command(owner, VIP, action("publish", 1), "vip-publish");
            final var equipVip = ApiV3ChatAppearanceTest.input(VIP, "none");
            error(403, () -> f.appearance.update(owner, equipVip, "no-vip"));
            sql(f.db, "INSERT INTO member_data VALUES('1',1,?)", NOW + 100000L);
            f.appearance.update(owner, equipVip, "equip-vip");
            Assert.assertEquals(f.snapshot("17").getJSONObject("appearance").getString("bubbleId"), VIP);
            sql(f.db, "UPDATE member_data SET expiresAt=?", NOW);
            Assert.assertEquals(f.snapshot("18").getJSONObject("appearance").getString("bubbleId"), "classic");
        }
        System.out.println("PASS 四款草稿与迁移重入、管理员权限与幂等、发布和下架、历史快照、积分和会员资格");
    }
}
