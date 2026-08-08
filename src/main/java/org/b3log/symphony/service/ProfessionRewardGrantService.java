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

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.ProfessionEventEffect;
import org.b3log.symphony.model.ProfessionRewardGrant;
import org.b3log.symphony.repository.ProfessionRewardGrantRepository;
import org.b3log.symphony.repository.UserMedalRepository;
import org.b3log.symphony.repository.UserRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** 在职业效果事务内幂等发放跨级奖励。 */
@Service
public class ProfessionRewardGrantService {

    @Inject private ProfessionRewardGrantRepository grantRepository;
    @Inject private PointtransferMgmtService pointtransferMgmtService;
    @Inject private UserMedalRepository userMedalRepository;
    @Inject private UserRepository userRepository;

    public void grantCrossed(final JSONObject effect, final List<JSONObject> crossedLevels, final long now)
            throws RepositoryException {
        for (final JSONObject level : crossedLevels) {
            final JSONArray rewards = level.getJSONArray("rewards");
            for (final Object item : rewards) {
                grant(new GrantContext(effect, level, (JSONObject) item, now));
            }
        }
    }

    private void grant(final GrantContext context) throws RepositoryException {
        final JSONObject grant = grantRecord(context);
        if (!grantRepository.addIfAbsent(grant)) {
            requireCompletedGrant(grant);
            return;
        }
        final String externalReference = switch (grant.getString(ProfessionRewardGrant.REWARD_TYPE)) {
            case "POINT" -> grantPoint(context, grant);
            case "MEDAL" -> grantMedal(context);
            default -> throw new IllegalStateException("未注册的职业奖励类型");
        };
        grant.put(ProfessionRewardGrant.EXTERNAL_REFERENCE_ID, externalReference);
        grant.put(ProfessionRewardGrant.STATUS, "GRANTED");
        grant.put(ProfessionRewardGrant.GRANTED_AT, context.now());
        grant.put(ProfessionRewardGrant.UPDATED_AT, context.now());
        grantRepository.update(grant.getString(Keys.OBJECT_ID), grant);
    }

    private void requireCompletedGrant(final JSONObject grant) throws RepositoryException {
        final JSONObject existing = grantRepository.getByIdentity(grant);
        if (null == existing || !List.of("GRANTED", "REVOKED").contains(
                existing.optString(ProfessionRewardGrant.STATUS))) {
            throw new IllegalStateException("职业奖励存在未完成的幂等记录");
        }
    }

    private JSONObject grantRecord(final GrantContext context) {
        final JSONObject effect = context.effect();
        final JSONObject reward = context.reward();
        final JSONObject grant = new JSONObject();
        grant.put(Keys.OBJECT_ID, Ids.genTimeMillisId());
        grant.put(ProfessionRewardGrant.USER_ID, effect.getString(ProfessionEventEffect.USER_ID));
        grant.put(ProfessionRewardGrant.PROFESSION_ID, effect.getString(ProfessionEventEffect.PROFESSION_ID));
        grant.put(ProfessionRewardGrant.SCHEME_ID, effect.getString(ProfessionEventEffect.LEVEL_SCHEME_ID));
        grant.put(ProfessionRewardGrant.LEVEL_CODE, context.level().getString("levelCode"));
        grant.put(ProfessionRewardGrant.REWARD_CODE, reward.getString("rewardCode"));
        grant.put(ProfessionRewardGrant.GRANT_CYCLE, reward.getString("grantPolicy"));
        grant.put(ProfessionRewardGrant.REWARD_TYPE, reward.getString("rewardType"));
        grant.put(ProfessionRewardGrant.REWARD_CONFIG_JSON, reward.getString("rewardConfigJson"));
        grant.put(ProfessionRewardGrant.SOURCE_EFFECT_ID, effect.getString(Keys.OBJECT_ID));
        grant.put(ProfessionRewardGrant.EXTERNAL_REFERENCE_ID, "");
        grant.put(ProfessionRewardGrant.STATUS, "PENDING");
        grant.put(ProfessionRewardGrant.GRANTED_AT, 0L);
        grant.put(ProfessionRewardGrant.REVOKED_AT, 0L);
        grant.put(ProfessionRewardGrant.CREATED_AT, context.now());
        grant.put(ProfessionRewardGrant.UPDATED_AT, context.now());
        return grant;
    }

    private String grantPoint(final GrantContext context, final JSONObject grant) {
        final JSONObject config = new JSONObject(context.reward().getString("rewardConfigJson"));
        final String memo = config.optString("memo", "职业升级奖励");
        final String transferId = pointtransferMgmtService.transferInCurrentTransaction(Pointtransfer.ID_C_SYS,
                context.effect().getString(ProfessionEventEffect.USER_ID),
                Pointtransfer.TRANSFER_TYPE_C_PROFESSION_LEVEL_REWARD, config.getInt("amount"),
                grant.getString(Keys.OBJECT_ID), context.now(), memo);
        if (StringUtils.isBlank(transferId)) {
            throw new IllegalStateException("职业积分奖励发放失败");
        }
        return transferId;
    }

    private String grantMedal(final GrantContext context) throws RepositoryException {
        final JSONObject config = new JSONObject(context.reward().getString("rewardConfigJson"));
        final String userId = context.effect().getString(ProfessionEventEffect.USER_ID);
        if (null == userRepository.getForUpdate(userId)) {
            throw new RepositoryException("职业奖励用户不存在");
        }
        final String medalId = config.getString("medalId");
        final Query query = new Query().setFilter(CompositeFilterOperator.and(
                new PropertyFilter("user_id", FilterOperator.EQUAL, userId),
                new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId)));
        final JSONObject existing = userMedalRepository.getFirst(query);
        final long duration = config.optLong("durationMillis", 0L);
        final long expireTime = 0L == duration ? 0L : Math.addExact(context.now(), duration);
        if (null == existing) {
            userMedalRepository.add(userMedal(new MedalGrantRequest(userId, medalId, expireTime,
                    config.optString("data"))));
        } else {
            final long mergedExpireTime = mergeExpireTime(existing.optLong("expire_time"), expireTime);
            if (mergedExpireTime != existing.optLong("expire_time")) {
                existing.put("expire_time", mergedExpireTime);
                userMedalRepository.update(existing.getString(Keys.OBJECT_ID), existing);
            }
        }
        return medalId;
    }

    private long mergeExpireTime(final long existing, final long candidate) {
        if (0L == existing || 0L == candidate) {
            return 0L;
        }
        return Math.max(existing, candidate);
    }

    private JSONObject userMedal(final MedalGrantRequest request) throws RepositoryException {
        final JSONObject result = new JSONObject();
        result.put("user_id", request.userId());
        result.put("medal_id", request.medalId());
        result.put("data", request.data());
        result.put("expire_time", request.expireTime());
        result.put("display", true);
        result.put("display_order", nextMedalOrder(request.userId()));
        return result;
    }

    private int nextMedalOrder(final String userId) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter("user_id", FilterOperator.EQUAL, userId));
        return userMedalRepository.getList(query).stream()
                .mapToInt(item -> item.optInt("display_order", 0)).max().orElse(0) + 1;
    }

    private record GrantContext(JSONObject effect, JSONObject level, JSONObject reward, long now) {
    }

    private record MedalGrantRequest(String userId, String medalId, long expireTime, String data) {
    }
}
