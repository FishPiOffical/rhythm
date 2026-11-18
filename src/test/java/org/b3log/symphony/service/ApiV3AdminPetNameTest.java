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
import org.b3log.latke.http.Request;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.repository.PetCoffeeRepository;
import org.b3log.symphony.repository.UserPetRepository;
import org.b3log.symphony.repository.UserRepository;
import org.json.JSONObject;
import org.testng.Assert;

import java.util.List;

import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 隔离数据库覆盖宠物昵称管理权限、审计及并发昵称冲突。 */
public class ApiV3AdminPetNameTest {
    static final String PET_ID = "1800000123456";

    static class Fixture extends ApiV3ManagedBubbleTest.Fixture {
        final ApiV3AdminPetService management = new ApiV3AdminPetService();

        Fixture() throws Exception {
            sql(db, "UPDATE u SET userName='owner-test',userNickname='小猫的主人' WHERE oId='2'");
            sql(db, "CREATE TABLE user_pet(oId TEXT PRIMARY KEY,userId TEXT,name TEXT,speciesId TEXT,adoptedAt BIGINT,updatedAt BIGINT)");
            sql(db, "INSERT INTO user_pet VALUES(?, '2', '待处理测试名', 'yubingmiao', ?, ?)", PET_ID, NOW, NOW);
            sql(db, "INSERT INTO user_pet VALUES('1800000123457', '1', '100% 可可', 'hexiaotun', ?, ?)", NOW - 1, NOW);
            inject(management, "admin", ApiV3QqSettingsTest.field(bubbles, ApiV3BubbleStyleService.class, "admin"));
            sql(db, "CREATE TABLE pet_coffee(oId TEXT PRIMARY KEY,snapshotJson TEXT)");
            sql(db, "INSERT INTO pet_coffee VALUES (?, ?)", "settings", new ApiV3PetCoffee.Offer(true, 64, 1).json().toString());
            final var coffee = new ApiV3PetCoffee();
            inject(coffee, "repository", new PetCoffeeRepository() {
                public String getName() { return "pet_coffee"; }
                public List<JSONObject> select(String query, Object... parameters) throws RepositoryException {
                    return selectSQL(query, parameters);
                }
                public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                    modify(getName(), id, row, fields);
                }
            });
            inject(management, "coffee", coffee);
            inject(management, "users", new UserRepository() {
                public String getName() { return "u"; }
            });
            inject(management, "pets", new UserPetRepository() {
                public String getName() { return "user_pet"; }
                public List<JSONObject> select(String query, Object... parameters) throws RepositoryException {
                    return selectSQL(query, parameters);
                }
                public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                    modify(getName(), id, row, fields);
                }
            });
        }

        JSONObject coffee(JSONObject actor, boolean enabled, int price, int version, String key) throws Exception {
            final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/api/v3/admin/pets/coffee");
            try {
                raw.headers().set("X-Forwarded-For", "127.0.0.1");
                return management.updateCoffee(actor, new JSONObject().put("enabled", enabled)
                        .put("price", price).put("version", version), key, new Request(null, raw));
            } finally {
                raw.release();
            }
        }

        JSONObject moderate(JSONObject actor, String id, String expectedName, String key) throws Exception {
            final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/");
            try {
                raw.headers().set("X-Forwarded-For", "127.0.0.1");
                return management.moderateName(actor, id, new JSONObject().put("expectedName", expectedName),
                        key, new Request(null, raw));
            } finally {
                raw.release();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ApiV3ManagedBubbleTest.initialize();
        try (final var f = new Fixture()) {
            final var owner = identity();
            final var ordinary = new JSONObject().put("tokenId", "102").put("user", new JSONObject().put("oId", "2"));
            error(403, () -> f.management.list(ordinary, "", 1, 20));
            error(403, () -> f.moderate(ordinary, PET_ID, "待处理测试名", "wrong-role"));
            Assert.assertEquals(f.management.list(owner, "", 2, 1).getJSONArray("list").length(), 1);
            for (String term : List.of("owner-test", "小猫的主人", PET_ID, "待处理", "2")) {
                Assert.assertEquals(f.management.list(owner, term, 1, 20).getJSONArray("list")
                        .getJSONObject(0).getString("petId"), PET_ID);
            }
            Assert.assertEquals(f.management.list(owner, "%", 1, 20).getJSONArray("list").length(), 1);
            Assert.assertEquals(f.management.list(owner, "_", 1, 20).getJSONArray("list").length(), 0);
            error(404, () -> f.moderate(owner, "1", "不存在", "missing"));
            sql(f.db, "UPDATE user_pet SET name='期间新名字' WHERE oId=?", PET_ID);
            error(409, () -> f.moderate(owner, PET_ID, "待处理测试名", "stale"));
            Assert.assertEquals(f.count("bubble_audit"), 0);

            final var receipt = f.moderate(owner, PET_ID, "期间新名字", "moderate");
            Assert.assertEquals(receipt.getString("moderatedName"), "违规昵称123456");
            Assert.assertTrue(receipt.similar(f.moderate(owner, PET_ID, "期间新名字", "moderate")));
            Assert.assertTrue(receipt.similar(f.moderate(owner, PET_ID, "期间新名字", "again")));
            Assert.assertTrue(f.management.list(owner, PET_ID, 1, 20).getJSONArray("list").getJSONObject(0).getBoolean("moderated"));
            Assert.assertEquals(f.count("bubble_audit"), 1);
            final var audit = rows(f.db, "SELECT operationCode,operationDataId,operationUserId FROM bubble_audit").getFirst();
            Assert.assertEquals(audit.getInt("operationCode"), Operation.OPERATION_CODE_C_MODERATE_PET_NAME);
            Assert.assertEquals(audit.getString("operationDataId"), PET_ID);
            Assert.assertEquals(audit.getString("operationUserId"), "1");

            error(403, () -> f.management.coffeeSettings(ordinary));
            error(403, () -> f.coffee(ordinary, false, 80, 1, "coffee-ordinary"));
            Assert.assertEquals(f.management.coffeeSettings(owner).getInt("price"), 64);
            error(422, () -> f.coffee(owner, false, 0, 1, "coffee-invalid"));
            final var sale = f.coffee(owner, false, 80, 1, "coffee-off");
            Assert.assertTrue(sale.similar(f.coffee(owner, false, 80, 1, "coffee-off")));
            Assert.assertFalse(sale.getBoolean("enabled"));
            Assert.assertEquals(sale.getInt("version"), 2);
            error(409, () -> f.coffee(owner, true, 96, 1, "coffee-stale"));
            Assert.assertEquals(f.count("bubble_audit"), 2);
            final var enabled = f.coffee(owner, true, 96, 2, "coffee-on");
            Assert.assertTrue(enabled.similar(f.management.coffeeSettings(owner)));
            Assert.assertEquals(enabled.getInt("price"), 96);
            Assert.assertEquals(f.count("bubble_audit"), 3);
        }
        System.out.println("PASS 宠物管理筛选分页、管理员权限、过期昵称拒绝、系统替换和审计去重");
        System.out.println("PASS 宠物咖啡后台权限、调价、上下架、过期设置拒绝与幂等审计");
    }
}
