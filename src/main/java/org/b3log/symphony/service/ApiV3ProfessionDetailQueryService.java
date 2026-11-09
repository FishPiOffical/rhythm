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
import org.b3log.symphony.repository.UserProfessionContributionRepository;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3ProfessionDetailDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 只读查询本人已有的职业进度、路线和近期经验。
 */
@Service
public class ApiV3ProfessionDetailQueryService {
    private static final int MAX_DETAILS = 512;
    private static final long RETENTION = TimeUnit.DAYS.toMillis(90);
    @Inject
    private UserRepository users;
    @Inject
    private ProfessionRepository professions;
    @Inject
    private ProfessionRevisionRepository revisions;
    @Inject
    private ProfessionLevelRepository levels;
    @Inject
    private ProfessionLevelSchemeRepository schemes;
    @Inject
    private UserProfessionRepository progress;
    @Inject
    private UserProfessionContributionRepository contributions;
    @Inject
    private ProfessionEventEffectRepository effects;

    @FunctionalInterface
    private interface Read {
        JSONObject run() throws RepositoryException;
    }

    private JSONObject read(final Read action) {
        try {
            return action.run();
        } catch (final RepositoryException | org.json.JSONException e) {
            LogManager.getLogger(getClass()).warn("Profession detail read unavailable");
            throw ApiV3ProfessionDetailDto.unavailable();
        }
    }

    public static void id(final String value) {
        if (value == null || !value.matches("[0-9]{1,19}")) throw ApiV3Requests.invalid();
    }

    private void actor(final String actor) throws RepositoryException {
        id(actor);
        if (users.select("SELECT oId FROM `" + users.getName() + "` WHERE oId=? AND userStatus=? LIMIT 2", actor,
                UserExt.USER_STATUS_C_VALID).size() != 1) throw ApiV3Requests.notFound();
    }

    private JSONObject current(final String actor, final String professionId) throws RepositoryException {
        final var rows = progress.select("SELECT up.oId AS progressId,up.professionId,up.professionRevisionId,up.levelSchemeId,"
                + "p.professionCode,p.status AS professionStatus,r.status AS revisionStatus,s.status AS schemeStatus,"
                + "r.displayName,r.shortName,r.description,up.totalExperience,up.currentLevelCode AS levelCode,"
                + "l.displayName AS levelName,l.requiredTotalExperience AS requiredExperience FROM `" + progress.getName()
                + "` up LEFT JOIN `" + professions.getName() + "` p ON p.oId=up.professionId LEFT JOIN `" + revisions.getName()
                + "` r ON r.oId=up.professionRevisionId AND r.professionId=up.professionId LEFT JOIN `" + schemes.getName()
                + "` s ON s.oId=up.levelSchemeId AND s.professionId=up.professionId LEFT JOIN `" + levels.getName()
                + "` l ON l.schemeId=up.levelSchemeId AND l.levelCode=up.currentLevelCode WHERE up.userId=? AND up.professionId=? LIMIT 2", actor, professionId);
        if (rows.isEmpty()) throw ApiV3Requests.notFound();
        if (rows.size() != 1) throw ApiV3ProfessionDetailDto.unavailable();
        final JSONObject value = rows.getFirst();
        for (final String key : Set.of("professionStatus", "revisionStatus", "schemeStatus")) {
            if (!Set.of("PUBLISHED", "RETIRED").contains(value.optString(key)))
                throw ApiV3ProfessionDetailDto.unavailable();
        }
        for (final String key : Set.of("progressId", "professionId", "professionRevisionId", "levelSchemeId")) {
            if (!(value.opt(key) instanceof String s) || !s.matches("[0-9]{1,19}"))
                throw ApiV3ProfessionDetailDto.unavailable();
        }
        ApiV3ProfessionDetailDto.profession(value);
        return value;
    }

    private void unchanged(final String actor, final String professionId, final JSONObject before) throws RepositoryException {
        // 详情由多次查询组成，须确认各结果来自同一进度版本。
        final JSONObject after = current(actor, professionId);
        if (!before.similar(after)) throw ApiV3ProfessionDetailDto.unavailable();
        actor(actor);
    }

    public JSONObject detail(final String actor, final String professionId) {
        id(actor);
        id(professionId);
        return read(() -> {
            actor(actor);
            final JSONObject state = current(actor, professionId);
            final var route = levels.select("SELECT levelCode,sortOrder,requiredTotalExperience,displayName,shortName,description,achievementDescription,isTopLevel FROM `"
                    + levels.getName() + "` WHERE schemeId=? ORDER BY sortOrder ASC,oId ASC LIMIT ?", state.getString("levelSchemeId"), MAX_DETAILS + 1);
            if (route.isEmpty() || route.size() > MAX_DETAILS) throw ApiV3ProfessionDetailDto.unavailable();
            final JSONArray routeDto = new JSONArray();
            final Set<String> codes = new HashSet<>();
            for (final JSONObject row : route) {
                final JSONObject dto = ApiV3ProfessionDetailDto.level(row);
                if (!codes.add(dto.getString("levelCode"))) throw ApiV3ProfessionDetailDto.unavailable();
                routeDto.put(dto);
            }
            if (!codes.contains(state.getString("levelCode"))) throw ApiV3ProfessionDetailDto.unavailable();
            final var aggregate = contributions.select("SELECT actionCode,experienceSum,eventCount,lastOccurredAt FROM `" + contributions.getName()
                            + "` WHERE userId=? AND professionId=? AND eventCount>0 ORDER BY experienceSum DESC,actionCode ASC,oId ASC LIMIT ?",
                    actor, professionId, MAX_DETAILS + 1);
            if (aggregate.size() > MAX_DETAILS) throw ApiV3ProfessionDetailDto.unavailable();
            final Set<String> actions = new HashSet<>();
            final JSONArray aggregateDto = new JSONArray();
            for (final JSONObject row : aggregate) {
                final JSONObject dto = ApiV3ProfessionDetailDto.contribution(row);
                if (!actions.add(dto.getString("actionCode"))) throw ApiV3ProfessionDetailDto.unavailable();
                aggregateDto.put(dto);
            }
            unchanged(actor, professionId, state);
            return new JSONObject().put("profession", ApiV3ProfessionDetailDto.profession(state))
                    .put("levels", routeDto).put("contributions", aggregateDto);
        });
    }

    public JSONObject records(final String actor, final String professionId, final int p, final int size) {
        id(actor);
        id(professionId);
        if (p < 1 || p > Integer.MAX_VALUE / 50 || size < 1 || size > 50) throw ApiV3Requests.invalid();
        return read(() -> {
            actor(actor);
            final JSONObject state = current(actor, professionId);
            final long cutoff = System.currentTimeMillis() - RETENTION;
            final String from = " FROM `" + effects.getName() + "` WHERE userId=? AND professionId=? AND status='APPLIED' AND actualExperienceDelta<>0 AND occurredAt>=?";
            final var count = effects.select("SELECT COUNT(*) AS total" + from, actor, professionId, cutoff);
            if (count.size() != 1) throw ApiV3ProfessionDetailDto.unavailable();
            final long total = ApiV3ProfessionDetailDto.number(count.getFirst(), "total", false);
            final var rows = effects.select("SELECT actionCode,actualExperienceDelta,beforeExperience,afterExperience,beforeLevelCode,afterLevelCode,occurredAt"
                    + from + " ORDER BY occurredAt DESC,oId DESC LIMIT ? OFFSET ?", actor, professionId, cutoff, size, (p - 1) * size);
            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows) list.put(ApiV3ProfessionDetailDto.record(row));
            unchanged(actor, professionId, state);
            return ApiV3Requests.pagination(list, p, size, total);
        });
    }
}
