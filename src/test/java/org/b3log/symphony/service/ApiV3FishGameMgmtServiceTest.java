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
import org.b3log.symphony.model.FishGame;
import org.b3log.symphony.model.FishGameComment;
import org.b3log.symphony.model.FishGameVote;
import org.b3log.symphony.repository.FishGameRepository;
import org.b3log.symphony.repository.FishGameVoteRepository;
import org.b3log.symphony.repository.FishGameCommentRepository;
import org.json.JSONObject;
import org.testng.Assert;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/** Compiles the real whole FishGameMgmtService; actual SQL/transactions use temporary SQLite adapters. */
public class ApiV3FishGameMgmtServiceTest {
    public static void main(final String[] args) throws Exception {
        try (final Fixture f = fixture()) {
            try { f.service.setVoteInCurrentTransaction("actor", "approved", "like", () -> { }); Assert.fail(); } catch (final IllegalStateException expected) { }
            f.db.setAutoCommit(false);
            Assert.assertTrue(f.vote("actor", "like").getBoolean("changed"));
            Assert.assertFalse(f.vote("actor", "like").getBoolean("changed"));
            Assert.assertEquals(f.vote("other", "dislike").getLong(FishGame.DISLIKE_COUNT), 1L);
            final JSONObject replacement = f.vote("actor", "dislike");
            Assert.assertEquals(replacement.getLong(FishGame.LIKE_COUNT), 0L); Assert.assertEquals(replacement.getLong(FishGame.DISLIKE_COUNT), 2L);
            Assert.assertTrue(f.votes.getOwnedForUpdate("actor' OR 1=1 --", "approved").isEmpty());
            Assert.assertEquals(f.vote("actor", "").getLong(FishGame.DISLIKE_COUNT), 1L); Assert.assertFalse(f.vote("actor", "").getBoolean("changed"));
            f.db.commit(); f.db.setAutoCommit(true); System.out.println("PASS real service set/replace/clear, ownership, SQL counts and transaction guard");
            final JSONObject toggled = f.service.vote("actor", "approved", "like");
            Assert.assertEquals(toggled.keySet(), java.util.Set.of(FishGame.LIKE_COUNT, FishGame.DISLIKE_COUNT, "fishGameUserVote"));
            Assert.assertEquals(f.games.get("approved").getLong(FishGame.LIKE_COUNT), 1L);
            Assert.assertEquals(f.service.vote("actor", "approved", "like").getString("fishGameUserVote"), "");
            Assert.assertEquals(f.games.get("approved").getLong(FishGame.LIKE_COUNT), 0L);
            System.out.println("PASS legacy toggle owns its commit and preserves its response shape");
            f.db.setAutoCommit(false);
            f.vote("actor", "like"); f.db.commit();
            f.failGameUpdate = true;
            try { f.vote("actor", "dislike"); Assert.fail(); } catch (final RepositoryException expected) { f.db.rollback(); }
            Assert.assertEquals(f.votes.getOwnedForUpdate("actor", "approved").getFirst().getString(FishGameVote.VALUE), "like");
            Assert.assertEquals(f.games.get("approved").getLong(FishGame.LIKE_COUNT), 1L); f.db.rollback(); f.db.setAutoCommit(true); f.failGameUpdate = false;
            for (final String id : List.of("pending", "rejected", "disabled", "missing")) {
                try { f.service.vote("actor", id, "like"); Assert.fail(); } catch (final ServiceException expected) { }
                try { f.service.addComment("actor", id, "text"); Assert.fail(); } catch (final ServiceException expected) { }
            }
            Assert.assertEquals(f.select("SELECT COUNT(1) AS total FROM fish_game_comment").getFirst().getLong("total"), 0L);
            System.out.println("PASS real rollback restores both vote and counters; inaccessible parents reject vote/comment");
            final JSONObject comment = f.service.addComment("actor", "approved", "  <b>text</b>  ");
            Assert.assertEquals(comment.getString(FishGameComment.CONTENT), "text");
            Assert.assertEquals(f.select("SELECT COUNT(1) AS total FROM fish_game_comment").getFirst().getLong("total"), 1L);
            for (final String bad : List.of("", "<script>bad()</script>", "x".repeat(501))) {
                try { f.service.addComment("actor", "approved", bad); Assert.fail(); } catch (final ServiceException expected) { }
            }
            f.db.setAutoCommit(false); f.failCommentAdd = true;
            try { f.service.addCommentInCurrentTransaction("actor", "approved", "failed", () -> { }); Assert.fail(); } catch (final RepositoryException expected) { f.db.rollback(); }
            Assert.assertEquals(f.select("SELECT COUNT(1) AS total FROM fish_game_comment").getFirst().getLong("total"), 1L);
            f.db.rollback(); f.db.setAutoCommit(true); f.failCommentAdd = false;
            System.out.println("PASS real comment service strips HTML, validates normalized length and rolls back failed insert");
            final JSONObject admin = new JSONObject().put(FishGame.NAME, "name").put(FishGame.DESCRIPTION, "description")
                    .put(FishGame.URL, "https://game.example.test/").put(FishGame.ICON_URL, "https://game.example.test/icon.png");
            f.injectCountBeforeAdminWrite = true;
            f.service.updateByAdmin("approved", admin);
            Assert.assertEquals(f.games.get("approved").getLong(FishGame.LIKE_COUNT), 99L);
            System.out.println("PASS stale admin metadata updates do not overwrite newer vote counters");
        }
    }
    private static Fixture fixture() throws Exception {
        final var field = Latkes.class.getDeclaredField("localProps"); field.setAccessible(true); final Object original = field.get(null);
        final Properties p = new Properties(); p.setProperty("runtimeDatabase", "NONE");
        try { field.set(null, p); return new Fixture(); } finally { field.set(null, original); }
    }
    private static class Fixture implements AutoCloseable {
        final Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
        final Games games = new Games(); final Votes votes = new Votes(); final Comments comments = new Comments();
        final FishGameMgmtService service = new FishGameMgmtService();
        boolean failGameUpdate, failCommentAdd, injectCountBeforeAdminWrite;
        Fixture() throws Exception {
            exec("CREATE TABLE fish_game(oId TEXT PRIMARY KEY,fishGameStatus INTEGER,fishGameLikeCount INTEGER,fishGameDislikeCount INTEGER,fishGameUpdatedTime BIGINT,fishGameAuthorId TEXT,fishGameName TEXT,fishGameDescription TEXT,fishGameUrl TEXT,fishGameIconUrl TEXT)");
            exec("INSERT INTO fish_game(oId,fishGameStatus,fishGameLikeCount,fishGameDislikeCount,fishGameUpdatedTime) VALUES('approved',1,0,0,1),('pending',0,0,0,1),('rejected',2,0,0,1),('disabled',3,0,0,1)");
            exec("CREATE TABLE fish_game_vote(oId TEXT PRIMARY KEY,fishGameVoteGameId TEXT,fishGameVoteUserId TEXT,fishGameVoteValue TEXT,fishGameVoteCreatedTime BIGINT)");
            exec("CREATE TABLE fish_game_comment(oId TEXT PRIMARY KEY,fishGameCommentGameId TEXT,fishGameCommentAuthorId TEXT,fishGameCommentContent TEXT,fishGameCommentCreatedTime BIGINT)");
            inject("fishGameRepository", games); inject("fishGameVoteRepository", votes); inject("fishGameCommentRepository", comments); inject("fishGameValidationService", new FishGameValidationService());
        }
        void inject(final String key, final Object value) throws Exception { final var field = FishGameMgmtService.class.getDeclaredField(key); field.setAccessible(true); field.set(service, value); }
        JSONObject vote(final String actor, final String value) throws Exception { return service.setVoteInCurrentTransaction(actor, "approved", value, () -> { }); }
        Transaction begin() {
            try { if (!db.getAutoCommit()) throw new IllegalStateException("nested transaction"); db.setAutoCommit(false); } catch (final java.sql.SQLException e) { throw new IllegalStateException(e); }
            return new Transaction() {
                boolean active = true;
                public void commit() { try { db.commit(); db.setAutoCommit(true); active = false; } catch (Exception e) { throw new IllegalStateException(e); } }
                public void rollback() { try { db.rollback(); db.setAutoCommit(true); active = false; } catch (Exception e) { throw new IllegalStateException(e); } }
                public boolean isActive() { return active; }
            };
        }
        boolean active() { try { return !db.getAutoCommit(); } catch (Exception e) { throw new IllegalStateException(e); } }
        List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
            try (final var statement = db.prepareStatement(sql.replace(" FOR UPDATE", ""))) {
                for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); final List<JSONObject> rows = new ArrayList<>();
                try (final var rs = statement.executeQuery()) { while (rs.next()) { final JSONObject row = new JSONObject(); for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i)); rows.add(row); } } return rows;
            } catch (final Exception e) { throw new RepositoryException(e); }
        }
        void exec(final String sql, final Object... args) throws RepositoryException {
            try (final var statement = db.prepareStatement(sql)) { for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); statement.executeUpdate(); } catch (Exception e) { throw new RepositoryException(e); }
        }
        String add(final String table, final JSONObject row) throws RepositoryException {
            final List<String> keys = new ArrayList<>(row.keySet());
            exec("INSERT INTO " + table + " (" + String.join(",", keys) + ") VALUES (" + keys.stream().map(k -> "?").collect(Collectors.joining(",")) + ")", keys.stream().map(row::get).toArray());
            return row.getString(Keys.OBJECT_ID);
        }
        void update(final String table, final String id, final JSONObject row, final String... properties) throws RepositoryException {
            final List<String> keys = properties.length == 0 ? row.keySet().stream().filter(k -> !k.equals(Keys.OBJECT_ID)).toList() : Arrays.asList(properties);
            final List<Object> args = new ArrayList<>(); for (final String key : keys) args.add(row.opt(key)); args.add(id);
            exec("UPDATE " + table + " SET " + keys.stream().map(k -> k + "=?").collect(Collectors.joining(",")) + " WHERE oId=?", args.toArray());
        }
        class Games extends FishGameRepository {
            public String getName() { return "fish_game"; } public Transaction beginTransaction() { return begin(); } public boolean hasTransactionBegun() { return active(); }
            public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException { return Fixture.this.select(sql, args); }
            public JSONObject get(final String id) throws RepositoryException { final var rows = select("SELECT * FROM fish_game WHERE oId=?", id); return rows.isEmpty() ? null : rows.getFirst(); }
            public JSONObject getByUrl(final String url) { return null; }
            public void update(final String id, final JSONObject row, final String... properties) throws RepositoryException {
                if (injectCountBeforeAdminWrite) { injectCountBeforeAdminWrite = false; exec("UPDATE fish_game SET fishGameLikeCount=99 WHERE oId=?", id); }
                Fixture.this.update(getName(), id, row, properties); if (failGameUpdate) throw new RepositoryException("simulated game update failure");
            }
        }
        class Votes extends FishGameVoteRepository {
            public String getName() { return "fish_game_vote"; } public boolean hasTransactionBegun() { return active(); }
            public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException { return Fixture.this.select(sql, args); }
            public String add(final JSONObject row) throws RepositoryException { return Fixture.this.add(getName(), row); }
            public void update(final String id, final JSONObject row, final String... properties) throws RepositoryException { Fixture.this.update(getName(), id, row, properties); }
            public void remove(final String id) throws RepositoryException { exec("DELETE FROM fish_game_vote WHERE oId=?", id); }
        }
        class Comments extends FishGameCommentRepository {
            public String getName() { return "fish_game_comment"; } public Transaction beginTransaction() { return begin(); } public boolean hasTransactionBegun() { return active(); }
            public String add(final JSONObject row) throws RepositoryException { final String id = Fixture.this.add(getName(), row); if (failCommentAdd) throw new RepositoryException("simulated comment insert failure"); return id; }
        }
        public void close() throws Exception { db.close(); }
    }
}
