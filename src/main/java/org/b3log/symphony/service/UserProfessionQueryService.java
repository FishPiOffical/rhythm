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
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserProfession;
import org.b3log.symphony.model.UserProfessionContribution;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.b3log.symphony.repository.ProfessionEffectDetailQuery;
import org.b3log.symphony.repository.ProfessionRankingQuery;
import org.b3log.symphony.repository.UserProfessionContributionRepository;
import org.b3log.symphony.repository.UserProfessionRepository;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 用户职业汇总、90 天明细、贡献、稀有度与排行查询。 */
@Service
public class UserProfessionQueryService {

    private static final long DETAIL_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(90);
    private static final int MAX_PAGE_SIZE = 100;

    @Inject private UserProfessionRepository userProfessionRepository;
    @Inject private ProfessionEventEffectRepository effectRepository;
    @Inject private UserProfessionContributionRepository contributionRepository;

    public List<JSONObject> getUserProgress(final String userId) throws RepositoryException {
        validateId(userId, "用户");
        final Query query = new Query().setFilter(new PropertyFilter(UserProfession.USER_ID,
                FilterOperator.EQUAL, userId)).addSort(UserProfession.TOTAL_EXPERIENCE, SortDirection.DESCENDING);
        return userProfessionRepository.getList(query);
    }

    public List<JSONObject> getRecentEffects(final DetailQuery request) throws RepositoryException {
        if (null == request) {
            throw new IllegalArgumentException("职业明细查询不能为空");
        }
        validateId(request.userId(), "用户");
        validateId(request.professionId(), "职业");
        validateLimit(request.limit());
        if (request.beforeOccurredAt() > 0L) {
            validateId(request.beforeEffectId(), "职业效果");
        }
        final long start = System.currentTimeMillis() - DETAIL_RETENTION_MILLIS;
        return effectRepository.getRecentApplied(new ProfessionEffectDetailQuery(request.userId(),
                request.professionId(), start, request.beforeOccurredAt(), request.beforeEffectId(), request.limit()));
    }

    public List<JSONObject> getContributions(final String userId, final String professionId)
            throws RepositoryException {
        validateId(userId, "用户");
        validateId(professionId, "职业");
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(UserProfessionContribution.USER_ID, FilterOperator.EQUAL, userId),
                new PropertyFilter(UserProfessionContribution.PROFESSION_ID, FilterOperator.EQUAL, professionId),
                new PropertyFilter(UserProfessionContribution.EVENT_COUNT, FilterOperator.GREATER_THAN, 0L)))
                .addSort(UserProfessionContribution.EXPERIENCE_SUM, SortDirection.DESCENDING);
        return contributionRepository.getList(query);
    }

    public JSONObject getRarity(final String userId, final String professionId) throws RepositoryException {
        final JSONObject progress = getProgress(userId, professionId);
        if (null == progress) {
            return new JSONObject().put("totalUsers", 0).put("rank", 0).put("surpassedPercent", 0D);
        }
        return getRarity(professionId, progress.getLong(UserProfession.TOTAL_EXPERIENCE));
    }

    public JSONObject getRarity(final String professionId, final long experience) throws RepositoryException {
        validateId(professionId, "职业");
        final JSONObject statistics = userProfessionRepository.getRarityStats(professionId, experience);
        final long total = statistics.optLong("totalUsers");
        final long greater = statistics.optLong("greaterUsers");
        final long lower = statistics.optLong("lowerUsers");
        final double surpassed = 0L == total ? 0D : lower * 100D / total;
        return new JSONObject().put("totalUsers", total).put("rank", greater + 1L)
                .put("surpassedPercent", surpassed);
    }

    public List<JSONObject> getRanking(final RankingQuery request) throws RepositoryException {
        if (null == request) {
            throw new IllegalArgumentException("职业排行查询不能为空");
        }
        validateId(request.professionId(), "职业");
        validateLimit(request.limit());
        if (null == request.beforeUserId()) {
            throw new IllegalArgumentException("排行用户游标不能为空");
        }
        if (!request.beforeUserId().isBlank()) {
            validateId(request.beforeUserId(), "排行用户");
            if (request.beforeExperience() < 0L) {
                throw new IllegalArgumentException("排行经验游标不合法");
            }
        }
        return userProfessionRepository.getRanking(new ProfessionRankingQuery(request.professionId(),
                request.beforeExperience(), request.beforeUserId(), request.limit()));
    }

    private JSONObject getProgress(final String userId, final String professionId) throws RepositoryException {
        validateId(userId, "用户");
        validateId(professionId, "职业");
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter(UserProfession.USER_ID, FilterOperator.EQUAL, userId),
                new PropertyFilter(UserProfession.PROFESSION_ID, FilterOperator.EQUAL, professionId)));
        return userProfessionRepository.getFirst(query);
    }

    private void validateLimit(final int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("明细数量不合法");
        }
    }

    private void validateId(final String value, final String name) {
        if (null == value || !value.matches("^[0-9]{1,19}$")) {
            throw new IllegalArgumentException(name + "标识不合法");
        }
    }

    public record DetailQuery(String userId, String professionId, long beforeOccurredAt,
                              String beforeEffectId, int limit) {
    }

    public record RankingQuery(String professionId, long beforeExperience, String beforeUserId, int limit) {
    }
}
