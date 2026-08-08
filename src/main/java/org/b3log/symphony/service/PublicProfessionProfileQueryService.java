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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserProfession;
import org.b3log.symphony.model.UserProfessionContribution;
import org.b3log.symphony.model.UserProfessionPrivacy;
import org.b3log.symphony.repository.UserProfessionRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Optional;

/** 按职业隐私白名单组装公开职业资料。 */
@Service
public class PublicProfessionProfileQueryService {

    @Inject private ProfessionPrivacyMgmtService privacyMgmtService;
    @Inject private UserProfessionRepository userProfessionRepository;
    @Inject private UserProfessionQueryService userProfessionQueryService;
    @Inject private ProfessionCatalogQueryService catalogQueryService;
    @Inject private ProfessionProfileSummaryQueryService summaryQueryService;

    public Optional<JSONObject> get(final ViewerRequest request) throws RepositoryException {
        validate(request);
        final JSONObject privacy = privacyMgmtService.get(request.targetUserId());
        final JSONObject visibility = new JSONObject(privacy.getString(UserProfessionPrivacy.MODULE_VISIBILITY_JSON));
        final ProfileContext context = new ProfileContext(request, privacy, visibility);
        final JSONObject result = new JSONObject();
        appendPrimary(result, context);
        appendAllProfessions(result, context);
        appendContributions(result, context);
        appendSummaries(result, context);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        appendVersions(result, privacy);
        return Optional.of(result);
    }

    private void appendPrimary(final JSONObject result, final ProfileContext context) throws RepositoryException {
        if (!visible(context, "primaryProfession")) {
            return;
        }
        final String professionId = context.privacy().optString(UserProfessionPrivacy.PRIMARY_PROFESSION_ID);
        if (professionId.isBlank()) {
            return;
        }
        final JSONObject progress = userProfessionRepository.getByUserProfession(context.request().targetUserId(), professionId);
        if (null != progress) {
            result.put("primaryProfession", progressView(progress, context));
        } else if (catalogQueryService.isPublished(professionId)) {
            result.put("primaryProfession", zeroProgressView(professionId, context));
        }
    }

    private void appendAllProfessions(final JSONObject result, final ProfileContext context) throws RepositoryException {
        if (!visible(context, "allProfessions")) {
            return;
        }
        final JSONArray values = new JSONArray();
        boolean containsPrimary = false;
        final String primaryProfessionId = context.privacy().optString(UserProfessionPrivacy.PRIMARY_PROFESSION_ID);
        for (final JSONObject progress : userProfessionQueryService.getUserProgress(context.request().targetUserId())) {
            values.put(progressView(progress, context));
            containsPrimary |= primaryProfessionId.equals(progress.optString(UserProfession.PROFESSION_ID));
        }
        if (!primaryProfessionId.isBlank() && !containsPrimary && catalogQueryService.isPublished(primaryProfessionId)) {
            values.put(zeroProgressView(primaryProfessionId, context));
        }
        result.put("professions", values);
    }

    private JSONObject zeroProgressView(final String professionId, final ProfileContext context) throws RepositoryException {
        final JSONObject result = catalogQueryService.getZeroProgressView(professionId, context.request().positionCode(),
                context.request().darkMode());
        if (visible(context, "experience")) {
            result.put("totalExperience", 0L);
        } else {
            result.remove("totalExperience");
        }
        if (visible(context, "rarity")) {
            result.put("rarity", userProfessionQueryService.getRarity(professionId, 0L));
        }
        return result;
    }

    private JSONObject progressView(final JSONObject progress, final ProfileContext context) throws RepositoryException {
        final String professionId = progress.getString(UserProfession.PROFESSION_ID);
        final JSONObject result = catalogQueryService.getProgressView(progress, context.request().positionCode(),
                context.request().darkMode());
        result.remove("totalExperience");
        if (visible(context, "experience")) {
            result.put("totalExperience", progress.getLong(UserProfession.TOTAL_EXPERIENCE));
        }
        if (visible(context, "rarity")) {
            result.put("rarity", userProfessionQueryService.getRarity(professionId,
                    progress.getLong(UserProfession.TOTAL_EXPERIENCE)));
        }
        return result;
    }

    private void appendContributions(final JSONObject result, final ProfileContext context) throws RepositoryException {
        if (!visible(context, "majorContributions")) {
            return;
        }
        final JSONArray values = new JSONArray();
        for (final JSONObject progress : userProfessionQueryService.getUserProgress(context.request().targetUserId())) {
            final List<JSONObject> contributions = userProfessionQueryService.getContributions(
                    context.request().targetUserId(), progress.getString(UserProfession.PROFESSION_ID));
            for (final JSONObject contribution : contributions) {
                values.put(new JSONObject()
                        .put("professionId", progress.getString(UserProfession.PROFESSION_ID))
                        .put("actionCode", contribution.getString(UserProfessionContribution.ACTION_CODE))
                        .put("experienceSum", contribution.getLong(UserProfessionContribution.EXPERIENCE_SUM))
                        .put("eventCount", contribution.getLong(UserProfessionContribution.EVENT_COUNT))
                        .put("lastOccurredAt", contribution.getLong(UserProfessionContribution.LAST_OCCURRED_AT)));
            }
        }
        result.put("majorContributions", values);
    }

    private void appendSummaries(final JSONObject result, final ProfileContext context) throws RepositoryException {
        if (visible(context, "contributionStats")) {
            result.put("contributionStats", summaryQueryService.contributionStats(context.request().targetUserId()));
        }
        if (visible(context, "activityFeed")) {
            result.put("activityFeed", summaryQueryService.activityFeed(context.request().targetUserId(), isOwner(context)));
        }
        if (visible(context, "levelHistory")) {
            result.put("levelHistory", summaryQueryService.levelHistory(context.request().targetUserId(), isOwner(context)));
        }
        if (visible(context, "rewards")) {
            result.put("rewards", summaryQueryService.rewards(context.request().targetUserId()));
        }
        if (visible(context, "ranking")) {
            result.put("ranking", summaryQueryService.ranking(context.request().targetUserId()));
        }
    }

    private boolean visible(final ProfileContext context, final String module) {
        final String scope = context.visibility().getString(module);
        return switch (scope) {
            case "PUBLIC" -> true;
            case "LOGGED_IN" -> !context.request().viewerUserId().isBlank();
            case "SELF" -> context.request().targetUserId().equals(context.request().viewerUserId());
            case "HIDDEN" -> false;
            default -> throw new IllegalStateException("职业隐私范围异常");
        };
    }

    private boolean isOwner(final ProfileContext context) {
        return context.request().targetUserId().equals(context.request().viewerUserId());
    }

    private void appendVersions(final JSONObject result, final JSONObject privacy) {
        result.put("userProgressVersion", privacy.getLong(UserProfessionPrivacy.USER_PROGRESS_VERSION));
        result.put("publicStatsVersion", privacy.getLong(UserProfessionPrivacy.PUBLIC_STATS_VERSION));
    }

    private void validate(final ViewerRequest request) {
        if (null == request || !isId(request.targetUserId())
                || (!request.viewerUserId().isBlank() && !isId(request.viewerUserId()))) {
            throw new IllegalArgumentException("职业资料查询参数不合法");
        }
    }

    private boolean isId(final String value) {
        return null != value && value.matches("^[0-9]{1,19}$");
    }

    private record ProfileContext(ViewerRequest request, JSONObject privacy, JSONObject visibility) {
    }

    public record ViewerRequest(String targetUserId, String viewerUserId, String positionCode, boolean darkMode) {
    }
}
