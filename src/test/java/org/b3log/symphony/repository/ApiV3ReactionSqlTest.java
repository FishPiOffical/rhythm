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
package org.b3log.symphony.repository;

import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.model.Reaction;
import org.b3log.symphony.util.ApiV3ReactionDto;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ReactionMutation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * 验证实际的共用表态变更逻辑和仓库 SQL。SQLite 适配移除 FOR UPDATE，HTTP 和实时通道需另行验证。
 */
public class ApiV3ReactionSqlTest {
    public static void main(final String[] args) throws Exception {
        final JSONArray catalog = ApiV3ReactionDto.emojis();
        Assert.assertEquals(catalog.length(), Reaction.emojiValues().size());
        for (final String value : Reaction.emojiValues())
            Assert.assertEquals(ApiV3ReactionDto.value(new JSONObject().put("value", value)), value);
        for (final Object value : List.of("", "unknown", "heart' OR 1=1", 123, JSONObject.NULL)) {
            try {
                ApiV3ReactionDto.value(new JSONObject().put("value", value));
                Assert.fail();
            } catch (final ApiV3Exception expected) {
                Assert.assertEquals(expected.status, 422);
            }
        }
        final JSONObject raw = new JSONObject().put("targetId", "12").put("targetType", "comment").put("currentUserReaction", "heart").put("token", "SECRET-token")
                .put("summary", new JSONArray().put(new JSONObject().put("value", "heart").put("emoji", "SECRET-spoof").put("count", 2).put("selected", true)
                        .put("users", List.of("SECRET-name")).put("userDetails", new JSONArray().put(new JSONObject().put("userName", "user").put("displayName", "Public")
                                .put("avatarURL", "javascript:SECRET()").put("userEmail", "SECRET-email")))));
        final JSONObject dto = ApiV3ReactionDto.from(raw);
        Assert.assertEquals(dto.keySet(), Set.of("targetId", "targetType", "groupType", "currentUserReaction", "summary"));
        Assert.assertFalse(dto.toString().contains("SECRET"));
        Assert.assertEquals(dto.getJSONArray("summary").getJSONObject(0).getString("emoji"), Reaction.emojiOf("heart"));
        Assert.assertEquals(dto.getJSONArray("summary").getJSONObject(0).getJSONArray("userDetails").getJSONObject(0).keySet(), Set.of("userName", "displayName", "avatarURL"));
        System.out.println("PASS closed catalog/typed values and nested response whitelist");
        final var field = Latkes.class.getDeclaredField("localProps");
        field.setAccessible(true);
        final Object original = field.get(null);
        final Properties props = new Properties();
        props.setProperty("runtimeDatabase", "NONE");
        final Rows repo;
        try {
            field.set(null, props);
            repo = new Rows();
        } finally {
            field.set(null, original);
        }
        try (final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            repo.db = db;
            exec(db, "CREATE TABLE users(oId TEXT PRIMARY KEY)");
            exec(db, "INSERT INTO users VALUES('actor'),('other')");
            exec(db, "CREATE TABLE targets(oId TEXT PRIMARY KEY)");
            exec(db, "INSERT INTO targets VALUES('target'),('other-target')");
            exec(db, "CREATE TABLE reaction(oId TEXT PRIMARY KEY,reactionUserId TEXT,reactionTargetType TEXT,reactionTargetId TEXT,reactionGroup TEXT,reactionValue TEXT,reactionCreatedTime BIGINT,reactionUpdatedTime BIGINT)");
            final Store store = new Store(repo);
            try {
                change(store, "actor", "comment", "target", "heart", false);
                Assert.fail();
            } catch (final IllegalStateException expected) {
            }
            db.setAutoCommit(false);
            for (final String type : List.of("article", "comment", "chat")) {
                Assert.assertTrue(change(store, "actor", type, "target", "heart", false));
                final long created = repo.getOwnedForUpdate("actor", type, "target", "emoji").getFirst().getLong(Reaction.CREATED_TIME);
                Assert.assertFalse(change(store, "actor", type, "target", "heart", false));
                Assert.assertTrue(change(store, "actor", type, "target", "fire", false));
                Assert.assertEquals(repo.getOwnedForUpdate("actor", type, "target", "emoji").getFirst().getLong(Reaction.CREATED_TIME), created);
                Assert.assertTrue(change(store, "actor", type, "target", "fire", true)); // old toggle clears
                Assert.assertTrue(repo.getOwnedForUpdate("actor", type, "target", "emoji").isEmpty());
                Assert.assertFalse(change(store, "actor", type, "target", "", false));
            }
            db.commit();
            System.out.println("PASS explicit set/replace/clear and legacy toggle across three target types");
            change(store, "actor", "article", "target", "heart", false);
            change(store, "other", "article", "target", "fire", false);
            change(store, "actor", "comment", "target", "star", false);
            change(store, "actor", "article", "other-target", "clap", false);
            Assert.assertTrue(repo.getOwnedForUpdate("actor' OR 1=1 --", "article", "target", "emoji").isEmpty());
            Assert.assertEquals(repo.getOwnedForUpdate("other", "article", "target", "emoji").getFirst().getString(Reaction.VALUE), "fire");
            exec(db, "INSERT INTO reaction SELECT 'duplicate',reactionUserId,reactionTargetType,reactionTargetId,reactionGroup,reactionValue,reactionCreatedTime,reactionUpdatedTime FROM reaction WHERE reactionUserId='actor' AND reactionTargetType='article' AND reactionTargetId='target'");
            Assert.assertTrue(change(store, "actor", "article", "target", "heart", false));
            Assert.assertEquals(repo.getOwnedForUpdate("actor", "article", "target", "emoji").size(), 1);
            db.commit();
            System.out.println("PASS actor/target/type isolation, bound SQL and duplicate repair");
            try {
                ReactionMutation.change(store, "actor", "comment", "target", "emoji", "fire", false, row -> {
                    throw new ApiV3Exception(404, "not_found", "denied");
                });
                Assert.fail();
            } catch (final ApiV3Exception expected) {
                db.rollback();
            }
            Assert.assertEquals(repo.getOwnedForUpdate("actor", "comment", "target", "emoji").getFirst().getString(Reaction.VALUE), "star");
            db.rollback();
            store.failAfterUpdate = true;
            try {
                change(store, "actor", "article", "target", "fire", false);
                Assert.fail();
            } catch (final RepositoryException expected) {
                db.rollback();
            }
            Assert.assertEquals(repo.getOwnedForUpdate("actor", "article", "target", "emoji").getFirst().getString(Reaction.VALUE), "heart");
            db.rollback();
            System.out.println("PASS denied target leaves state unchanged and failed update really rolls back");
        }
    }

    private static boolean change(final Store s, final String actor, final String type, final String id, final String value, final boolean toggle) throws RepositoryException {
        return ReactionMutation.change(s, actor, type, id, "emoji", value, toggle, null);
    }

    private static void exec(final Connection db, final String sql, final Object... args) throws RepositoryException {
        try (final var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            statement.executeUpdate();
        } catch (final Exception e) {
            throw new RepositoryException(e);
        }
    }

    private static class Store implements ReactionMutation.Store {
        final Rows repo;
        int next;
        boolean failAfterUpdate;

        Store(final Rows repo) {
            this.repo = repo;
        }

        public boolean active() {
            return repo.hasTransactionBegun();
        }

        public boolean lockActor(final String actor) throws RepositoryException {
            return null != repo.lockEntity("users", actor);
        }

        public JSONObject lockTarget(final String type, final String id) throws RepositoryException {
            return repo.lockEntity("targets", id);
        }

        public List<JSONObject> existing(final String actor, final String type, final String id, final String group) throws RepositoryException {
            return repo.getOwnedForUpdate(actor, type, id, group);
        }

        public void add(final JSONObject row) throws RepositoryException {
            exec(repo.db, "INSERT INTO reaction VALUES(?,?,?,?,?,?,?,?)", "" + ++next, row.getString(Reaction.USER_ID), row.getString(Reaction.TARGET_TYPE), row.getString(Reaction.TARGET_ID),
                    row.getString(Reaction.GROUP), row.getString(Reaction.VALUE), row.getLong(Reaction.CREATED_TIME), row.getLong(Reaction.UPDATED_TIME));
        }

        public void update(final String id, final JSONObject row) throws RepositoryException {
            exec(repo.db, "UPDATE reaction SET reactionValue=?,reactionUpdatedTime=? WHERE oId=?", row.getString(Reaction.VALUE), row.getLong(Reaction.UPDATED_TIME), id);
            if (failAfterUpdate) throw new RepositoryException("simulated failure after write");
        }

        public void remove(final String id) throws RepositoryException {
            exec(repo.db, "DELETE FROM reaction WHERE oId=?", id);
        }
    }

    private static class Rows extends ReactionRepository {
        Connection db;

        @Override
        public String getName() {
            return "reaction";
        }

        @Override
        public boolean hasTransactionBegun() {
            try {
                return null != db && !db.getAutoCommit();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
            try (final var statement = db.prepareStatement(sql.replace(" FOR UPDATE", ""))) {
                for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                final List<JSONObject> rows = new ArrayList<>();
                try (final var rs = statement.executeQuery()) {
                    while (rs.next()) {
                        final JSONObject row = new JSONObject();
                        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                            row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                        rows.add(row);
                    }
                }
                return rows;
            } catch (final Exception e) {
                throw new RepositoryException(e);
            }
        }
    }
}
