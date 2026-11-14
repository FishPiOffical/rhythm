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
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.UserProfessionPrivacyRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.json.JSONObject;

import java.util.Set;

/**
 * 免费选择主职业，加入共享幂等事务并由其统一提交。
 */
@Service
public class ApiV3ProfessionPrimaryService {
    private static final Set<String> MODULES = Set.of("primaryProfession", "allProfessions", "experience", "rarity",
            "majorContributions", "contributionStats", "activityFeed", "levelHistory", "rewards", "ranking");
    @Inject
    private UserRepository users;
    @Inject
    private ProfessionRepository professions;
    @Inject
    private UserProfessionPrivacyRepository privacy;
    @Inject
    private ApiV3IdempotencyMgmtService idempotency;

    public JSONObject set(final String actor, final String professionId, final String key) {
        id(actor);
        id(professionId);
        try {
            // 回放不进入写入回调，须在读取回执前确认缓存凭据对应的账号当前有效。
            if (users.select("SELECT oId FROM `" + users.getName() + "` WHERE oId=? AND userStatus=?", actor,
                    UserExt.USER_STATUS_C_VALID).size() != 1) throw ApiV3Requests.notFound();
            return idempotency.execute(actor, "professions.primary", key,
                    new JSONObject().put("professionId", professionId),
                    () -> setInCurrentTransaction(actor, professionId));
        } catch (final ApiV3Exception error) {
            throw error;
        } catch (final Exception error) {
            LogManager.getLogger(getClass()).warn("Primary profession selection unavailable");
            throw new ApiV3Exception(503, "temporarily_unavailable", "职业设置暂不可用");
        }
    }

    /**
     * 在调用方事务中依次锁定当前用户、目标职业和隐私设置。
     */
    public JSONObject setInCurrentTransaction(final String actor, final String professionId) throws Exception {
        id(actor);
        id(professionId);
        if (!privacy.hasTransactionBegun())
            throw new IllegalStateException("profession selection transaction required");
        return ApiV3TransactionSavepoint.run(() -> {
            final var actors = users.select("SELECT oId,userStatus FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
            if (actors.size() != 1 || actors.getFirst().optInt("userStatus", -1) != UserExt.USER_STATUS_C_VALID)
                throw ApiV3Requests.notFound();
            final var targets = professions.select("SELECT oId,status FROM `" + professions.getName() + "` WHERE oId=? FOR UPDATE", professionId);
            if (targets.size() != 1 || !"PUBLISHED".equals(targets.getFirst().optString("status")))
                throw ApiV3Requests.notFound();
            final var states = privacy.select("SELECT oId,userProgressVersion FROM `" + privacy.getName()
                    + "` WHERE userId=? LIMIT 2 FOR UPDATE", actor);
            if (states.size() > 1) throw new RepositoryException("Duplicate profession privacy state");
            final long now = System.currentTimeMillis();
            if (states.isEmpty()) {
                final var visibility = new JSONObject();
                for (final String module : MODULES) visibility.put(module, "PUBLIC");
                final int count = ApiV3TransactionStatements.execute("INSERT INTO `" + privacy.getName()
                                + "` (oId,userId,onboardingState,primaryProfessionId,privacyPreset,moduleVisibilityJson,"
                                + "userProgressVersion,userPrivacyVersion,publicStatsVersion,createdAt,updatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        Ids.genTimeMillisId(), actor, "SELECTED", professionId, "ALL_PUBLIC", visibility.toString(),
                        1L, 0L, 0L, now, now);
                if (count != 1) throw new RepositoryException("Profession privacy insert failed");
            } else {
                final var current = states.getFirst();
                final long version = current.getLong("userProgressVersion");
                if (version < 0 || version == Long.MAX_VALUE)
                    throw new RepositoryException("Invalid profession progress version");
                final int count = ApiV3TransactionStatements.execute("UPDATE `" + privacy.getName()
                                + "` SET onboardingState=?,primaryProfessionId=?,userProgressVersion=?,updatedAt=? WHERE oId=? AND userId=?",
                        "SELECTED", professionId, version + 1L, now, current.getString("oId"), actor);
                if (count != 1) throw new RepositoryException("Profession privacy update failed");
            }
            return new JSONObject().put("onboardingState", "SELECTED").put("primaryProfessionId", professionId);
        });
    }

    private static void id(final String value) {
        if (value == null || !value.matches("[0-9]{1,19}")) throw ApiV3Requests.invalid();
    }
}
