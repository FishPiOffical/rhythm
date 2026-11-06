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
import org.b3log.symphony.repository.UserProfessionRepository;
import org.b3log.symphony.repository.UserProfessionPrivacyRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Set;

/** Project only display metadata and owner state; never return grants, effects or configuration JSON. */
@Service
public class ApiV3ProfessionQueryService {
    private static final Set<String> MODULES = Set.of("primaryProfession", "allProfessions", "experience", "rarity",
            "majorContributions", "contributionStats", "activityFeed", "levelHistory", "rewards", "ranking");
    private static final Set<String> SCOPES = Set.of("PUBLIC", "LOGGED_IN", "SELF", "HIDDEN");
    @Inject private UserRepository users;
    @Inject private ProfessionRepository professions;
    @Inject private ProfessionRevisionRepository revisions;
    @Inject private ProfessionLevelRepository levels;
    @Inject private UserProfessionRepository progress;
    @Inject private UserProfessionPrivacyRepository privacy;

    @FunctionalInterface private interface Read { JSONObject run() throws RepositoryException; }
    private JSONObject read(final Read action) {
        try { return action.run(); }
        catch (final RepositoryException | org.json.JSONException e) {
            LogManager.getLogger(getClass()).warn("Profession read unavailable");
            throw unavailable();
        }
    }
    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "职业资料暂不可用");
    }
    private static void page(final int p, final int size) {
        if (p < 1 || p > Integer.MAX_VALUE / 50 || size < 1 || size > 50) throw ApiV3Requests.invalid();
    }
    private void requireActor(final String actor) throws RepositoryException {
        if (actor == null || !actor.matches("[1-9][0-9]{0,31}")) throw ApiV3Requests.invalid();
        if (users.select("SELECT oId FROM `" + users.getName() + "` WHERE oId=? AND userStatus=?", actor,
                UserExt.USER_STATUS_C_VALID).size() != 1) throw ApiV3Requests.notFound();
    }
    private static String text(final JSONObject row, final String key) {
        final String value = row.optString(key);
        if (value.length() > 8192) throw unavailable();
        return value;
    }
    private static JSONObject definition(final JSONObject row) {
        if (row.optString("displayName").isBlank() || row.optString("professionId").isBlank()) throw unavailable();
        return new JSONObject().put("professionId", row.getString("professionId"))
                .put("professionCode", text(row, "professionCode")).put("displayName", text(row, "displayName"))
                .put("shortName", text(row, "shortName")).put("description", text(row, "description"));
    }
    public JSONObject catalog(final int p, final int size) {
        page(p, size);
        return read(() -> {
            final String from = " FROM `" + professions.getName() + "` p JOIN `" + revisions.getName()
                    + "` r ON r.oId=p.currentRevisionId AND r.professionId=p.oId WHERE p.status=? AND r.status='PUBLISHED'";
            final long total = professions.select("SELECT COUNT(*) AS total" + from, "PUBLISHED").getFirst().getLong("total");
            final var rows = professions.select("SELECT p.oId AS professionId,p.professionCode,r.displayName,r.shortName,r.description"
                    + from + " ORDER BY p.sortOrder ASC,p.oId ASC LIMIT ? OFFSET ?", "PUBLISHED", size, (p - 1) * size);
            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows) list.put(definition(row));
            return ApiV3Requests.pagination(list, p, size, total);
        });
    }
    public JSONObject mine(final String actor, final int p, final int size) {
        page(p, size);
        return read(() -> {
            requireActor(actor);
            final JSONObject state = state(actor);
            final String from = " FROM `" + progress.getName() + "` up LEFT JOIN `" + professions.getName()
                    + "` p ON p.oId=up.professionId LEFT JOIN `" + revisions.getName()
                    + "` r ON r.oId=up.professionRevisionId AND r.professionId=up.professionId LEFT JOIN `" + levels.getName()
                    + "` l ON l.schemeId=up.levelSchemeId AND l.levelCode=up.currentLevelCode WHERE up.userId=?";
            final long total = progress.select("SELECT COUNT(*) AS total FROM `" + progress.getName() + "` WHERE userId=?", actor)
                    .getFirst().getLong("total");
            final var rows = progress.select("SELECT up.professionId,p.professionCode,p.status AS professionStatus,"
                    + "r.displayName,r.shortName,r.description,r.status AS revisionStatus,up.totalExperience,up.currentLevelCode AS levelCode,"
                    + "l.displayName AS levelName,l.description AS levelDescription,l.requiredTotalExperience AS requiredExperience"
                    + from + " ORDER BY up.totalExperience DESC,up.professionId ASC LIMIT ? OFFSET ?", actor, size, (p - 1) * size);
            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows) {
                final long experience = row.getLong("totalExperience");
                if (experience < 0 || row.optString("levelName").isBlank()
                        || !Set.of("PUBLISHED", "RETIRED").contains(row.optString("revisionStatus"))) throw unavailable();
                list.put(definition(row).put("state", "PUBLISHED".equals(row.optString("professionStatus")) ? "ACTIVE" : "RETIRED")
                        .put("totalExperience", experience).put("levelCode", text(row, "levelCode"))
                        .put("levelName", text(row, "levelName")).put("levelDescription", text(row, "levelDescription"))
                        .put("requiredExperience", row.getLong("requiredExperience")));
            }
            // Current account revocation during the read must not return an owner-only response.
            requireActor(actor);
            return ApiV3Requests.pagination(list, p, size, total).put("state", state);
        });
    }
    private JSONObject state(final String actor) throws RepositoryException {
        final var rows = privacy.select("SELECT onboardingState,primaryProfessionId,privacyPreset,moduleVisibilityJson FROM `"
                + privacy.getName() + "` WHERE userId=? LIMIT 2", actor);
        if (rows.size() > 1) throw unavailable();
        final JSONObject stored = rows.isEmpty() ? null : rows.getFirst();
        final String onboarding = stored == null ? "UNDECIDED" : stored.getString("onboardingState");
        final String primary = stored == null ? "" : stored.getString("primaryProfessionId");
        final String preset = stored == null ? "ALL_PUBLIC" : stored.getString("privacyPreset");
        if (!Set.of("UNDECIDED", "SKIPPED", "SELECTED").contains(onboarding)
                || !Set.of("ALL_PUBLIC", "LEVEL_ONLY", "PRIMARY_ONLY", "SELF_ONLY", "FULLY_HIDDEN", "CUSTOM").contains(preset)
                || (!primary.isEmpty() && !primary.matches("[0-9]{1,19}"))) throw unavailable();
        final JSONObject visibility = new JSONObject();
        if (stored == null) {
            for (final String module : MODULES) visibility.put(module, "PUBLIC");
        } else {
            final String json = stored.getString("moduleVisibilityJson");
            if (json.length() > 4096) throw unavailable();
            final JSONObject raw = new JSONObject(json);
            if (!raw.keySet().equals(MODULES)) throw unavailable();
            for (final String module : MODULES) {
                if (!(raw.get(module) instanceof String) || !SCOPES.contains(raw.getString(module))) throw unavailable();
                visibility.put(module, raw.getString(module));
            }
        }
        String primaryState = "NONE";
        if (!primary.isEmpty()) {
            final var values = professions.select("SELECT status FROM `" + professions.getName() + "` WHERE oId=?", primary);
            primaryState = values.isEmpty() ? "DELETED" : "PUBLISHED".equals(values.getFirst().optString("status")) ? "ACTIVE" : "RETIRED";
        }
        return new JSONObject().put("onboardingState", onboarding).put("primaryProfessionId", primary)
                .put("primaryProfessionState", primaryState).put("privacyPreset", preset).put("moduleVisibility", visibility);
    }
}
