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

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.ProfessionRevisionRepository;
import org.b3log.symphony.repository.ProfessionLevelRepository;
import org.b3log.symphony.repository.ProfessionLevelSchemeRepository;
import org.b3log.symphony.repository.UserProfessionRepository;
import org.b3log.symphony.repository.UserProfessionPrivacyRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3ProfessionRankingDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;

/** Complete bounded candidates are authorized before counting, sorting, numbering and paging. No default rows are written. */
@Service
public class ApiV3ProfessionRankingQueryService {
    private static final int MAX_CANDIDATES = 4096;
    /** Request-local budget shared by public profiles across professions; count actual initial and verification reads. */
    public static final class CandidateBudget {
        private static final int MAX_READ_ROWS = 8192;
        private int readRows;
        private int nextReadLimit() { return Math.min(MAX_CANDIDATES + 1, MAX_READ_ROWS - readRows + 1); }
        private void consume(final int rows) {
            if (rows < 0 || rows > MAX_READ_ROWS - readRows) throw ApiV3ProfessionRankingDto.unavailable();
            readRows += rows;
        }
    }
    @Inject private UserRepository users;
    @Inject private ProfessionRepository professions;
    @Inject private ProfessionRevisionRepository revisions;
    @Inject private ProfessionLevelRepository levels;
    @Inject private ProfessionLevelSchemeRepository schemes;
    @Inject private UserProfessionRepository progress;
    @Inject private UserProfessionPrivacyRepository privacy;

    private static void id(final String value) {
        if (value == null || !value.matches("[0-9]{1,19}")) throw ApiV3Requests.invalid();
    }
    private JSONObject actor(final String actor) throws RepositoryException {
        final var rows = users.select("SELECT oId,userStatus,userName FROM `" + users.getName() + "` WHERE oId=? LIMIT 2", actor);
        if (rows.size() != 1) throw ApiV3Requests.notFound();
        final JSONObject row = rows.getFirst();
        if (!actor.equals(row.opt("oId")) || ApiV3ProfessionRankingDto.number(row, "userStatus") != UserExt.USER_STATUS_C_VALID
                || "_".equals(row.opt("userName"))) throw ApiV3Requests.notFound();
        ApiV3ProfessionRankingDto.text(row, "userName", 255, false);
        return row;
    }
    private JSONObject definition(final String professionId) throws RepositoryException {
        final var rows = professions.select("SELECT p.oId AS professionId,p.status AS professionStatus,p.currentRevisionId,"
                + "r.oId AS revisionId,r.professionId AS revisionProfessionId,r.status AS revisionStatus FROM `" + professions.getName()
                + "` p LEFT JOIN `" + revisions.getName() + "` r ON r.oId=p.currentRevisionId WHERE p.oId=? LIMIT 2", professionId);
        if (rows.isEmpty()) throw ApiV3Requests.notFound();
        if (rows.size() != 1) throw ApiV3ProfessionRankingDto.unavailable();
        final JSONObject value = rows.getFirst();
        if (!professionId.equals(value.opt("professionId")) || !"PUBLISHED".equals(value.opt("professionStatus"))) throw ApiV3Requests.notFound();
        if (!"PUBLISHED".equals(value.opt("revisionStatus")) || !professionId.equals(value.opt("revisionProfessionId"))) throw ApiV3ProfessionRankingDto.unavailable();
        return value;
    }
    private List<JSONObject> candidates(final String professionId, final CandidateBudget budget) throws RepositoryException {
        final var rows = progress.select("SELECT up.oId AS progressId,up.userId,up.professionId,up.professionRevisionId,up.levelSchemeId,"
                + "up.totalExperience,up.currentLevelCode AS levelCode,u.oId AS actualUserId,u.userStatus,u.userName,u.userNickname,u.userAvatarURL,"
                + "p.professionCode,r.professionId AS revisionProfessionId,r.status AS revisionStatus,r.displayName AS professionName,"
                + "s.professionId AS schemeProfessionId,s.status AS schemeStatus,l.displayName AS levelName,l.schemeId AS actualLevelSchemeId,l.levelCode AS actualLevelCode,"
                + "pr.oId AS privacyId,pr.userId AS privacyUserId,pr.moduleVisibilityJson,pr.userPrivacyVersion"
                + " FROM `" + progress.getName() + "` up JOIN `" + users.getName() + "` u ON u.oId=up.userId"
                + " JOIN `" + professions.getName() + "` p ON p.oId=up.professionId"
                + " LEFT JOIN `" + revisions.getName() + "` r ON r.oId=up.professionRevisionId"
                + " LEFT JOIN `" + schemes.getName() + "` s ON s.oId=up.levelSchemeId"
                + " LEFT JOIN `" + levels.getName() + "` l ON l.schemeId=up.levelSchemeId AND l.levelCode=up.currentLevelCode"
                + " LEFT JOIN `" + privacy.getName() + "` pr ON pr.userId=up.userId"
                + " WHERE up.professionId=? AND u.userStatus=? ORDER BY up.userId ASC,up.oId ASC LIMIT ?",
                professionId, UserExt.USER_STATUS_C_VALID, budget.nextReadLimit());
        budget.consume(rows.size());
        if (rows.size() > MAX_CANDIDATES) throw ApiV3ProfessionRankingDto.unavailable();
        return rows;
    }
    public JSONObject ranking(final String actor, final String professionId, final int p, final int size) {
        id(actor); id(professionId);
        if (p < 1 || p > Integer.MAX_VALUE / 50 || size < 1 || size > 50) throw ApiV3Requests.invalid();
        final List<JSONObject> visible = snapshot(actor, professionId, new CandidateBudget());
        final long offset = (long) (p - 1) * size;
        final JSONArray list = new JSONArray();
        for (long i = offset; i < visible.size() && i < offset + size; i++) {
            final JSONObject row = visible.get((int) i);
            list.put(ApiV3ProfessionRankingDto.entry(row, i + 1, ApiV3ProfessionRankingDto.visible(
                    ApiV3ProfessionRankingDto.visibility(row), "experience", row.getString("userId"), actor)));
        }
        return ApiV3Requests.pagination(list, p, size, visible.size());
    }
    /** Shared visible-population statistic; anonymous callers must already have passed their HTTP entry gate. */
    public Optional<JSONObject> summary(final String professionId, final String viewerId, final String targetUserId) {
        return summary(professionId, viewerId, targetUserId, new CandidateBudget());
    }
    /** Public profile callers share one budget across every target profession in the response. */
    public Optional<JSONObject> summary(final String professionId, final String viewerId, final String targetUserId,
                                        final CandidateBudget budget) {
        id(professionId); id(targetUserId);
        if (viewerId == null || budget == null) throw ApiV3Requests.invalid();
        if (!viewerId.isEmpty()) id(viewerId);
        final List<JSONObject> visible = snapshot(viewerId, professionId, budget);
        for (int i = 0; i < visible.size(); i++) {
            if (targetUserId.equals(visible.get(i).opt("userId"))) {
                final int rank = i + 1, total = visible.size();
                return Optional.of(new JSONObject().put("rank", rank).put("totalUsers", total)
                        .put("surpassedPercent", (total - rank) * 100D / total));
            }
        }
        return Optional.empty();
    }
    private List<JSONObject> snapshot(final String actor, final String professionId, final CandidateBudget budget) {
        try {
            final JSONObject beforeActor = actor.isEmpty() ? null : actor(actor), beforeDefinition = definition(professionId);
            final List<JSONObject> candidates = candidates(professionId, budget), visible = new ArrayList<>();
            final Set<String> seen = new HashSet<>();
            for (final JSONObject row : candidates) {
                final String owner = ApiV3ProfessionRankingDto.text(row, "userId", 19, false);
                if (!owner.matches("[0-9]{1,19}") || !seen.add(owner) || !owner.equals(row.opt("actualUserId"))
                        || !professionId.equals(row.opt("professionId"))) throw ApiV3ProfessionRankingDto.unavailable();
                if ("_".equals(row.opt("userName"))) continue;
                if (ApiV3ProfessionRankingDto.number(row, "userStatus") != UserExt.USER_STATUS_C_VALID) continue;
                if (row.has("privacyId") && !row.isNull("privacyId") && !owner.equals(row.opt("privacyUserId"))) throw ApiV3ProfessionRankingDto.unavailable();
                final JSONObject visibility = ApiV3ProfessionRankingDto.visibility(row);
                if (!ApiV3ProfessionRankingDto.visible(visibility, "ranking", owner, actor)) continue;
                if (!professionId.equals(row.opt("revisionProfessionId")) || !professionId.equals(row.opt("schemeProfessionId"))
                        || !row.optString("levelSchemeId").equals(row.opt("actualLevelSchemeId"))
                        || !row.optString("levelCode").equals(row.opt("actualLevelCode"))) throw ApiV3ProfessionRankingDto.unavailable();
                ApiV3ProfessionRankingDto.number(row, "totalExperience");
                ApiV3ProfessionRankingDto.entry(row, 1, false);
                visible.add(row);
            }
            visible.sort(Comparator.<JSONObject>comparingLong(row -> ApiV3ProfessionRankingDto.number(row, "totalExperience")).reversed()
                    .thenComparing(row -> row.getString("userId")));
            // Detect changed privacy, candidates, owner status, historical metadata and current actor; never return mixed snapshots.
            final List<JSONObject> after = candidates(professionId, budget);
            if (candidates.size() != after.size()) throw ApiV3ProfessionRankingDto.unavailable();
            for (int i = 0; i < candidates.size(); i++) if (!candidates.get(i).similar(after.get(i))) throw ApiV3ProfessionRankingDto.unavailable();
            if (!beforeDefinition.similar(definition(professionId))
                    || (beforeActor != null && !beforeActor.similar(actor(actor)))) throw ApiV3ProfessionRankingDto.unavailable();
            return visible;
        } catch (final RepositoryException | org.json.JSONException e) {
            LogManager.getLogger(getClass()).warn("Profession ranking read unavailable");
            throw ApiV3ProfessionRankingDto.unavailable();
        }
    }
}
