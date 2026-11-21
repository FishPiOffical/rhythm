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

import java.time.Duration;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.PetBrawlSeasonRepository;
import org.b3log.symphony.repository.PetBrawlSeasonRewardRepository;
import org.b3log.symphony.repository.PetBrawlStandingRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3PetRules;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONArray;
import org.json.JSONObject;

/** 赛季战绩与战报同事务提交，结束后只允许写领奖记录。 */
@Service
public class ApiV3PetSeasons {
    private static final long PERIOD = Duration.ofDays(90).toMillis();
    private static final int INITIAL_RATING = 1000;
    private static final int REQUIRED_GAMES = 10;
    private static final int REQUIRED_OPPONENTS = 5;
    private static final int PAGE_SIZE = 20;
    private static final String RANK_ORDER = "rating DESC,wins DESC,reachedAt ASC,userId ASC";

    @Inject private PetBrawlSeasonRepository seasons;
    @Inject private PetBrawlStandingRepository standings;
    @Inject private PetBrawlSeasonRewardRepository rewards;
    @Inject private UserRepository users;

    record Round(int season, long startedAt) {}

    private JSONObject clock(final String lock) throws RepositoryException {
        return seasons.select("SELECT startsAt,settledThrough FROM `" + seasons.getName()
                + "` WHERE oId='1'" + lock).getFirst();
    }

    private static int seasonNumber(final JSONObject clock, final long now) {
        final int scheduled = Math.toIntExact(Math.max(0, now - clock.getLong("startsAt")) / PERIOD + 1);
        return Math.max(scheduled, clock.getInt("settledThrough") + 1);
    }

    Round beginRound(final java.util.function.LongSupplier time) throws RepositoryException {
        // 对局共享赛季锁直到战报提交，结算排他锁等待在途对局；结算后时钟回拨也不能写回旧季。
        final JSONObject clock = clock(" LOCK IN SHARE MODE");
        final long now = time.getAsLong();
        return new Round(seasonNumber(clock, now), now);
    }

    public void settle() throws RepositoryException {
        try {
            settle(System::currentTimeMillis);
        } finally {
            ApiV3Transactions.releaseThreadResources();
        }
    }

    void settle(final java.util.function.LongSupplier time) throws RepositoryException {
        final Transaction transaction = ApiV3Transactions.strict(seasons.beginTransaction());
        try {
            final JSONObject clock = clock(" FOR UPDATE");
            final int ended = seasonNumber(clock, time.getAsLong()) - 1;
            if (ended > clock.getInt("settledThrough")) {
                seasons.update("1", new JSONObject().put("settledThrough", ended), "settledThrough");
            }
            transaction.commit();
        } finally {
            if (transaction.isActive()) transaction.rollback();
        }
    }

    void record(final Round round, final String player, final String opponent, final int winner) throws RepositoryException {
        // 调用方已锁住双方账号。读取本行最新值，避免取到候选搜索建立的旧事务快照。
        final JSONObject first = standing(round.season(), player, " FOR UPDATE");
        final JSONObject second = standing(round.season(), opponent, " FOR UPDATE");
        final int playerRating = first == null ? INITIAL_RATING : first.getInt("rating");
        final int opponentRating = second == null ? INITIAL_RATING : second.getInt("rating");
        final double expected = 1 / (1 + Math.pow(10, (opponentRating - playerRating) / 400.0));
        final double actual = winner < 0 ? 0.5 : winner == 0 ? 1 : 0;
        final int change = (int) Math.round(24 * (actual - expected));
        save(round, player, opponent, first, playerRating + change, winner == 0);
        save(round, opponent, player, second, opponentRating - change, winner == 1);
    }

    private void save(final Round round, final String user, final String opponent, final JSONObject previous,
                      final int rating, final boolean won) throws RepositoryException {
        final JSONArray opponents = previous == null ? new JSONArray() : new JSONArray(previous.getString("opponents"));
        if (opponents.length() < REQUIRED_OPPONENTS && !opponents.toList().contains(opponent)) opponents.put(opponent);
        final int games = previous == null ? 1 : previous.getInt("games") + 1;
        final int wins = (previous == null ? 0 : previous.getInt("wins")) + (won ? 1 : 0);
        final long reachedAt = previous != null && previous.getInt("rating") == rating
                ? previous.getLong("reachedAt") : round.startedAt();
        final String id = rowId(round.season(), user);
        final JSONObject row = new JSONObject().put("oId", id).put("season", round.season()).put("userId", user)
                .put("rating", rating).put("wins", wins).put("games", games).put("reachedAt", reachedAt)
                .put("eligible", games >= REQUIRED_GAMES && opponents.length() == REQUIRED_OPPONENTS ? 1 : 0)
                .put("opponents", opponents.toString());
        if (previous == null) standings.add(row);
        else standings.update(id, row, "rating", "wins", "games", "reachedAt", "eligible", "opponents");
    }

    JSONObject board(final String user, final Integer requested, final int page, final long now) throws RepositoryException {
        final JSONObject clock = clock("");
        final int current = seasonNumber(clock, now);
        final int season = requested == null ? current : requested;
        if (season > current) throw ApiV3PetRules.conflict("brawl_season_future", "该赛季还未开始");
        final long starts = clock.getLong("startsAt") + (season - 1L) * PERIOD;
        final boolean settled = season <= clock.getInt("settledThrough");
        final int offset = (page - 1) * PAGE_SIZE;
        final String query = "SELECT s.rating,s.wins,s.games,s.userId,u.userName,u.userNickname "
                + "FROM `" + standings.getName() + "` s LEFT JOIN `" + users.getName() + "` u ON u.oId=s.userId AND u.userStatus=0 "
                + "WHERE s.season=? AND s.eligible=1 ORDER BY s." + RANK_ORDER.replace(",", ",s.")
                + " LIMIT ? OFFSET ?";
        final var rows = standings.select(query, season, PAGE_SIZE + 1, offset);
        final JSONArray list = new JSONArray();
        for (int index = 0; index < Math.min(rows.size(), PAGE_SIZE); index++) {
            final JSONObject row = rows.get(index);
            list.put(stats(row).put("rank", offset + index + 1).put("mine", user.equals(row.getString("userId")))
                    .put("name", row.optString("userName")).put("nickname", row.optString("userNickname")));
        }
        final JSONObject personal = standing(season, user, "");
        final int rank = personal == null || personal.getInt("eligible") == 0 ? 0 : rank(season, personal);
        final JSONObject mine = personal == null ? new JSONObject().put("rating", INITIAL_RATING).put("games", 0).put("wins", 0)
                .put("opponents", 0) : stats(personal).put("opponents", new JSONArray(personal.getString("opponents")).length());
        final JSONObject claim = settled ? rewards.get(rowId(season, user)) : null;
        return new JSONObject().put("season", season).put("current", current).put("startsAt", starts)
                .put("endsAt", starts + PERIOD).put("settled", settled).put("page", page)
                .put("hasNext", rows.size() > PAGE_SIZE).put("list", list)
                .put("mine", mine.put("rank", rank == 0 ? JSONObject.NULL : rank)
                        .put("reward", settled && rank != 0 ? materials(rank) : 0).put("claimed", claim != null));
    }

    JSONObject claim(final String user, final int season, final long now) throws RepositoryException {
        final String id = rowId(season, user);
        final var existing = rewards.select("SELECT place,materials,claimedAt FROM `" + rewards.getName()
                + "` WHERE oId=? FOR UPDATE", id);
        if (!existing.isEmpty()) return existing.getFirst().put("credited", false);
        if (season > clock("").getInt("settledThrough"))
            throw ApiV3PetRules.conflict("brawl_season_open", "赛季尚未结算");
        final JSONObject personal = standing(season, user, "");
        if (personal == null || personal.getInt("eligible") == 0)
            throw ApiV3PetRules.conflict("brawl_season_unranked", "本赛季未达到上榜条件");
        final int place = rank(season, personal);
        final JSONObject result = new JSONObject().put("place", place).put("materials", materials(place)).put("claimedAt", now);
        rewards.add(new JSONObject(result.toString()).put("oId", id).put("userId", user).put("season", season));
        return result.put("credited", true);
    }

    private int rank(final int season, final JSONObject entry) throws RepositoryException {
        final int rating = entry.getInt("rating");
        final int wins = entry.getInt("wins");
        final long at = entry.getLong("reachedAt");
        // 排行与领奖必须用同一套完整排序。账号 ID 只用于保证最终同分时的顺序稳定。
        return 1 + standings.select("SELECT COUNT(*) AS total FROM `" + standings.getName()
                + "` WHERE season=? AND eligible=1 AND (rating>? OR (rating=? AND (wins>? OR "
                + "(wins=? AND (reachedAt<? OR (reachedAt=? AND userId<?))))))",
                season, rating, rating, wins, wins, at, at, entry.getString("userId")).getFirst().getInt("total");
    }

    private JSONObject standing(final int season, final String user, final String lock) throws RepositoryException {
        final var rows = standings.select("SELECT * FROM `" + standings.getName() + "` WHERE oId=?" + lock, rowId(season, user));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static JSONObject stats(final JSONObject row) {
        return new JSONObject().put("rating", row.getInt("rating")).put("wins", row.getInt("wins")).put("games", row.getInt("games"));
    }

    private static String rowId(final int season, final String user) {
        return season + ":" + user;
    }

    private static int materials(final int rank) {
        return rank == 1 ? 40 : rank <= 10 ? 30 : rank <= 50 ? 20 : 10;
    }
}
