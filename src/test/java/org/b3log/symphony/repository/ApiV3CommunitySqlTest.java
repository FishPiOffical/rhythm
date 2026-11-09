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
import org.b3log.symphony.model.FishGame;
import org.b3log.symphony.model.Milestone;
import org.b3log.symphony.util.ApiV3CommunityDto;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** 在隔离的 SQLite 上验证实际的已审核游戏查询，并验证公开元数据处理。 */
public class ApiV3CommunitySqlTest {
    @Test
    public void approvedGamesHaveRealPaginationAndBothClosedSortModes() throws Exception {
        try (final Fixture f = fixture()) {
            f.seed("01", 1, 10, 0, 100); f.seed("02", 1, 5, 100, 200); f.seed("03", 1, 10, 0, 101);
            f.seed("04", 0, 1000, 1000, 500); f.seed("05", 2, 1000, 1000, 500); f.seed("06", 3, 1000, 1000, 500);
            Assert.assertEquals(ids(f.games.getV3ApprovedPage(1, 2, "likes")), List.of("03", "01"));
            Assert.assertEquals(ids(f.games.getV3ApprovedPage(2, 2, "likes")), List.of("02"));
            Assert.assertTrue(f.games.getV3ApprovedPage(3, 2, "likes").isEmpty());
            Assert.assertEquals(ids(f.games.getV3ApprovedPage(1, 3, "votes")), List.of("02", "03", "01"));
            try { f.games.getV3ApprovedPage(1, 2, "fishGameName DESC"); Assert.fail("Arbitrary sort accepted"); }
            catch (final IllegalArgumentException expected) { }
        }
    }

    @Test
    public void gameDtoDoesNotLeakPendingEditsOrNestedUserSecrets() {
        final JSONObject row = new JSONObject().put(Keys.OBJECT_ID, "1").put(FishGame.NAME, "Game").put(FishGame.PENDING_URL, "private-pending")
                .put(FishGame.STATUS, 0).put(FishGame.AUTHOR_ID, "private-owner").put("apiKey", "private-key");
        final JSONObject author = new JSONObject().put("id", "public-id").put("userName", "user").put("userPassword", "private-password");
        final JSONObject dto = ApiV3CommunityDto.game(row, author, "invalid-secret-vote");
        Assert.assertFalse(dto.toString().contains("private-"));
        Assert.assertFalse(dto.toString().contains("invalid-secret"));
        Assert.assertEquals(dto.getJSONObject("author").keySet(), Set.of("id", "userName", "userNickname", "userAvatarURL"));
    }

    @Test
    public void publicUrlsRejectScriptSchemesCredentialsAndSchemeRelativePaths() {
        for (final String url : new String[]{"javascript:alert(1)", "data:image/svg+xml,abc", "//evil.example/x", "https://u:p@example.test/",
                "https://example.test/\nattack", "\\\\evil\\path", "/\\evil/path", "https:///missing-host"}) {
            Assert.assertEquals(ApiV3CommunityDto.safeUrl(url), "");
        }
        for (final String url : new String[]{"https://example.test/a?q=1", "http://localhost:8080/games/evolve/", "/images/icon.png"}) {
            Assert.assertEquals(ApiV3CommunityDto.safeUrl(url), url);
        }
    }

    @Test
    public void milestoneDtoOmitsReviewFieldsAndRawUntrustedHtml() {
        final JSONObject row = new JSONObject().put(Keys.OBJECT_ID, "2").put(Milestone.MILESTONE_TITLE, "Event")
                .put(Milestone.MILESTONE_CONTENT, "private-raw-html").put(Milestone.MILESTONE_AUTHOR_ID, "private-author")
                .put(Milestone.MILESTONE_STATUS, 1).put(Milestone.MILESTONE_MEDIA_URL, "javascript:alert(1)");
        final JSONObject dto = ApiV3CommunityDto.milestone(row, "<b>safe</b>", "safe");
        Assert.assertFalse(dto.toString().contains("private-"));
        Assert.assertEquals(dto.getJSONObject("media").getString("url"), "");
        Assert.assertEquals(dto.getString("contentHtml"), "<b>safe</b>");
        Assert.assertEquals(dto.getString("endDate"), "");
    }

    private List<String> ids(final List<JSONObject> rows) { return rows.stream().map(row -> row.optString(Keys.OBJECT_ID)).toList(); }
    private Fixture fixture() throws Exception {
        final var field = Latkes.class.getDeclaredField("localProps"); field.setAccessible(true);
        final Properties previous = (Properties) field.get(null);
        final Properties properties = new Properties(); if (null != previous) properties.putAll(previous);
        properties.setProperty("runtimeDatabase", "NONE"); Latkes.setLocalProps(properties);
        try { return new Fixture(); } finally { Latkes.setLocalProps(previous); }
    }
    private static final class Fixture implements AutoCloseable {
        private final Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        private final Games games = new Games();
        private Fixture() throws Exception {
            try (final var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE fish_game (oId TEXT PRIMARY KEY,fishGameStatus INTEGER,fishGameLikeCount INTEGER,fishGameDislikeCount INTEGER,fishGameCreatedTime INTEGER)");
            }
        }
        private void seed(final String id, final int status, final int likes, final int dislikes, final int created) throws Exception {
            try (final var statement = connection.prepareStatement("INSERT INTO fish_game VALUES (?,?,?,?,?)")) {
                statement.setString(1, id); statement.setInt(2, status); statement.setInt(3, likes); statement.setInt(4, dislikes); statement.setInt(5, created);
                statement.executeUpdate();
            }
        }
        @Override public void close() throws Exception { connection.close(); }
        private final class Games extends FishGameRepository {
            @Override public String getName() { return "fish_game"; }
            @Override public List<JSONObject> select(final String sql, final Object... args) throws RepositoryException {
                try (final var statement = connection.prepareStatement(sql)) {
                    for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                    try (final var result = statement.executeQuery()) {
                        final List<JSONObject> rows = new ArrayList<>();
                        while (result.next()) rows.add(new JSONObject().put(Keys.OBJECT_ID, result.getString("oId")));
                        return rows;
                    }
                } catch (final Exception e) { throw new RepositoryException(e); }
            }
        }
    }
    public static void main(final String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        final var test = new ApiV3CommunitySqlTest();
        test.approvedGamesHaveRealPaginationAndBothClosedSortModes();
        test.gameDtoDoesNotLeakPendingEditsOrNestedUserSecrets();
        test.publicUrlsRejectScriptSchemesCredentialsAndSchemeRelativePaths();
        test.milestoneDtoOmitsReviewFieldsAndRawUntrustedHtml();
        System.out.println("PASS: 4 community SQL/metadata tests; full HTTP/city/privacy/HTML rendering not covered");
    }
}
