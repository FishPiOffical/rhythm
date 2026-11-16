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
import org.b3log.symphony.repository.ClientDownloadRepository;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.RepeaterLikeRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.b3log.symphony.service.ApiV3SecurityTest.*;

/** 隔离数据库覆盖目录迁移、下架投影和后台复读写入。 */
public class ApiV3CommunityDirectoryTest {
    static class Fixture extends ApiV3ManagedBubbleTest.Fixture {
        final ApiV3ClientDownloadService directory = new ApiV3ClientDownloadService();
        final ApiV3RepeaterPublicationService repeater = new ApiV3RepeaterPublicationService();
        final ApiV3RepeaterQueryService query = new ApiV3RepeaterQueryService();
        boolean reject;

        Fixture() throws Exception {
            migrate();
            sql(db, "CREATE TABLE repeater(oId TEXT PRIMARY KEY,repeaterContentType TEXT,repeaterContent TEXT,"
                    + "repeaterContentAuthorId TEXT,repeaterContentSource TEXT,repeaterContentStatus INT,"
                    + "repeaterContentLikeCount INT,repeaterContentCreatedTime BIGINT,repeaterContentUpdatedTime BIGINT)");
            sql(db, "CREATE TABLE repeater_likes(repeaterLikeContentId TEXT,repeaterLikeUserId TEXT)");
            final var admin = ApiV3QqSettingsTest.field(bubbles, ApiV3BubbleStyleService.class, "admin");
            final var requests = ApiV3QqSettingsTest.field(service, ApiV3IdentityApplicationService.class, "requests");
            inject(directory, "admin", admin);
            inject(repeater, "admin", admin);
            inject(repeater, "requests", requests);
            inject(repeater, "moderationService", new RepeaterContentModerationService() {
                public void requireApproved(String content) {
                    if (reject) throw new ApiV3Exception(422, "content_rejected", "审核未通过");
                }
            });
            inject(directory, "clients", new ClientDownloadRepository() {
                public String getName() { return "client_download"; }
                public List<JSONObject> select(String query, Object... parameters) throws RepositoryException {
                    return selectSQL(query, parameters);
                }
                public String add(JSONObject row) throws RepositoryException { return insert(getName(), row); }
                public void update(String id, JSONObject row, String... fields) throws RepositoryException {
                    modify(getName(), id, row, fields);
                }
            });
            final var contents = new RepeaterContentRepository() {
                public String getName() { return "repeater"; }
                public List<JSONObject> select(String query, Object... parameters) throws RepositoryException {
                    return selectSQL(query, parameters);
                }
                public String add(JSONObject row) throws RepositoryException { return insert(getName(), row); }
            };
            inject(repeater, "contents", contents);
            inject(query, "contents", contents);
            inject(query, "users", new UserRepository() {
                public String getName() { return "u"; }
                public List<JSONObject> select(String query, Object... parameters) throws RepositoryException {
                    return selectSQL(query, parameters);
                }
            });
            inject(query, "likes", new RepeaterLikeRepository() {
                public String getName() { return "repeater_likes"; }
            });
        }

        void migrate() throws Exception {
            final String migration = Files.readString(Path.of("sql/20261116_client_download_catalog.sql"))
                    .replaceFirst("(?m)^--[^\n]*\n", "")
                    .replace("symphony_client_download", "client_download")
                    .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin", "")
                    .replace("INSERT IGNORE", "INSERT OR IGNORE");
            for (final String statement : migration.split(";")) {
                if (!statement.isBlank()) sql(db, statement);
            }
        }

        JSONObject change(JSONObject actor, String id, JSONObject body, String key) throws Exception {
            final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/");
            try {
                raw.headers().set("X-Forwarded-For", "127.0.0.1");
                return id == null ? repeater.publishByAdmin(actor, body, key, new Request(null, raw))
                        : directory.save(actor, id, body, key, new Request(null, raw));
            } finally {
                raw.release();
            }
        }
    }

    private static JSONObject find(JSONArray rows, String id) {
        for (final Object value : rows) {
            final JSONObject item = (JSONObject) value;
            if (id.equals(item.getString("id"))) return item;
        }
        throw new AssertionError("缺少目录条目 " + id);
    }

    private static JSONObject body(int revision, JSONObject client) {
        return new JSONObject().put("revision", revision).put("client", client);
    }

    public static void main(String[] args) throws Exception {
        ApiV3ManagedBubbleTest.initialize();
        try (final var f = new Fixture()) {
            final var admin = identity();
            final var member = new JSONObject().put("tokenId", "102").put("user", new JSONObject().put("oId", "2"));
            final var clients = f.directory.directory().getJSONArray("list");
            Assert.assertEquals(clients.length(), 15);
            Assert.assertTrue(find(clients, "android-recommended").getBoolean("recommended"));
            Assert.assertFalse(find(clients, "ios-retired").has("links"));
            error(403, () -> f.directory.adminList(member, 1, 20));
            final var input = find(f.directory.adminList(admin, 1, 20).getJSONArray("list"), "windows");
            input.remove("id");
            input.remove("revision");
            error(403, () -> f.change(member, "windows", body(1, input), "wrong-role"));
            final var unsafe = new JSONObject(input.toString()).put("links", new JSONArray()
                    .put(new JSONObject().put("href", "//invalid.example/path").put("label", "错误地址")));
            error(422, () -> f.change(admin, "windows", body(1, unsafe), "unsafe"));
            final var newClient = f.change(admin, "desktop-example", body(0, input), "new");
            Assert.assertTrue(newClient.similar(f.change(admin, "desktop-example", body(0, input), "new")));
            error(409, () -> f.change(admin, "desktop-example", body(0, input), "duplicate"));
            input.put("status", "retired").put("recommended", true);
            f.change(admin, "windows", body(1, input), "retire");
            f.migrate();
            final var retired = find(f.directory.directory().getJSONArray("list"), "windows");
            Assert.assertEquals(retired.getString("status"), "retired");
            Assert.assertFalse(retired.has("links"));
            Assert.assertFalse(retired.getBoolean("recommended"));
            final var saved = find(f.directory.adminList(admin, 1, 20).getJSONArray("list"), "windows");
            Assert.assertEquals(saved.getJSONArray("links").length(), 1);
            input.put("status", "active");
            f.change(admin, "windows", body(2, input), "republish");
            Assert.assertTrue(find(f.directory.directory().getJSONArray("list"), "windows").getBoolean("recommended"));

            final var text = new JSONObject().put("type", "fish").put("content", "<script>hidden</script>隔离发布");
            error(403, () -> f.change(member, null, text, "wrong-repeater-role"));
            f.reject = true;
            error(422, () -> f.change(admin, null, text, "repeater"));
            f.reject = false;
            final var entry = f.change(admin, null, text, "repeater");
            f.reject = true;
            Assert.assertTrue(entry.similar(f.change(admin, null, text, "repeater")));
            final var listed = f.repeater.adminList(admin, 1, 20).getJSONArray("list");
            Assert.assertEquals(listed.length(), 1);
            final var random = f.query.next("", "fish", "").getJSONObject("item");
            Assert.assertEquals(random.getString("id"), entry.getString("repeaterId"));
            Assert.assertEquals(random.getString("content"), "隔离发布");
            Assert.assertEquals(f.count("bubble_audit"), 4);
        }
        System.out.println("PASS 目录迁移重复执行、推荐、上下架、URL 校验、原键恢复、权限与后台复读公开读取");
    }
}
