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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.util.ApiV3RankingDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/** Public board snapshots. Cache keys are a closed set of type/subtype pairs. */
@Singleton
public class ApiV3RankingQueryService {
    @Inject private RankingQueryService rankings;
    @Inject private ActivityQueryService activities;
    @Inject private PointtransferQueryService points;
    @Inject private LinkQueryService links;
    @Inject private UserQueryService users;
    @Inject private ApiV3ContentDtoService dto;
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong SETTINGS_REVISION = new java.util.concurrent.atomic.AtomicLong();
    public static void invalidateSettings() { SETTINGS_REVISION.incrementAndGet(); }
    private record Snapshot(long at, long revision, String data) { }

    public JSONObject ranking(final String type, final String subType, final int p, final int size) throws Exception {
        final String normalizedSubType = ApiV3RankingDto.subType(type, subType);
        final JSONObject board = snapshot(type, normalizedSubType);
        final JSONArray rows = board.getJSONArray("list"), page = new JSONArray();
        final long start = (long) (p - 1) * size;
        for (long i = start; i < rows.length() && i < start + size; i++) page.put(rows.get((int) i));
        final JSONObject result = ApiV3Requests.pagination(page, p, size, rows.length()).put("type", type).put("subType", normalizedSubType);
        if (board.has("summary")) result.put("summary", board.getJSONObject("summary"));
        return result;
    }
    private JSONObject snapshot(final String type, final String subType) throws Exception {
        final String key = type + ":" + subType;
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            return load(type, subType, key);
        }
    }
    private JSONObject load(final String type, final String subType, final String key) throws Exception {
        final long now = System.nanoTime();
        final long revision = SETTINGS_REVISION.get();
        final Snapshot cached = snapshots.get(key);
        if (null != cached && cached.revision == revision && now - cached.at < TimeUnit.SECONDS.toNanos(10)) return new JSONObject(cached.data);
        final int count = Symphonys.TOP_CNT;
        JSONObject summary = null;
        final List<JSONObject> source;
        switch (type) {
            case "links" -> source = links.getTopLink(count);
            case "balance" -> source = points.getTopBalanceUsers(count);
            case "consumption" -> source = points.getTopConsumptionUsers(count);
            case "checkin" -> source = activities.getTopCheckinUsers(count);
            case "online" -> source = activities.getTopOnlineTimeUsers(count);
            case "adr" -> source = activities.getTopADR(count);
            case "mofish" -> source = activities.getTopMofish(count);
            case "smallmofish" -> source = activities.getTopSmallMofish(count);
            case "lifeRestart" -> source = activities.getTopLifeRestart(count);
            case "evolve" -> source = activities.getEvolve(subType, count);
            case "emoji" -> source = activities.getTopEmoji(count);
            case "xiaoice" -> source = rankings.xiaoice(subType);
            case "invite" -> source = rankings.invite();
            case "perfect" -> source = ApiV3RankingDto.rows(rankings.perfect().opt("data"));
            case "donate" -> {
                final JSONObject result = rankings.donate();
                source = ApiV3RankingDto.rows(result.opt("data"));
                summary = ApiV3RankingDto.summary(result.optJSONObject("totalData"));
            }
            default -> throw ApiV3Requests.invalid();
        }
        final JSONArray list = new JSONArray();
        for (final JSONObject row : source) {
            JSONObject user = row.optJSONObject("profile");
            if ("xiaoice".equals(type)) user = users.getUserByName(row.optString("uname"));
            else if (null == user) user = row;
            if (!"links".equals(type) && (null == user || UserExt.USER_STATUS_C_VALID != user.optInt(UserExt.USER_STATUS))) continue;
            list.put(ApiV3RankingDto.entry(type, subType, list.length() + 1, row, "links".equals(type) ? null : dto.author(user, false)));
        }
        final JSONObject result = new JSONObject().put("list", list);
        if (null != summary) result.put("summary", summary);
        snapshots.put(key, new Snapshot(now, revision, result.toString()));
        return result;
    }
}
