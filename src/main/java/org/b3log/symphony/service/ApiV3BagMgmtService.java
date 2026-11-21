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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.BagMutation;
import org.b3log.symphony.util.CheckinPatchMutation;
import org.json.JSONObject;

@Service
public class ApiV3BagMgmtService {
    @Inject private BagMgmtService bags;
    @Inject private ApiV3IdempotencyMgmtService idempotency;
    @Inject private CheckinMgmtService checkins;
    @Inject private UserNameMgmtService userNames;
    public JSONObject useNameCard(final String actor, final String name, final String key) throws Exception {
        final var renamed = new java.util.concurrent.atomic.AtomicReference<UserNameMgmtService.Rename>();
        try {
            return idempotency.execute(actor, "bag.nameCard", key, new JSONObject().put("userName", name), () -> {
                try {
                    final var result = userNames.useCardInCurrentTransaction(actor, name);
                    renamed.set(result.rename());
                    return new JSONObject().put("itemCode", "nameCard").put("userName", result.rename().newName()).put("bag", BagMutation.dto(result.bag()));
                } catch (final UserNameMgmtService.Rejected e) {
                    throw switch (e.code) {
                        case "invalid_name" -> new ApiV3Exception(422, e.code, "用户名不符合注册规则");
                        case "name_taken" -> new ApiV3Exception(409, e.code, "用户名已被使用");
                        case "item_unavailable" -> new ApiV3Exception(409, e.code, "没有可用的改名卡");
                        case "not_found", "deactivated" -> new ApiV3Exception(404, "not_found", "用户不存在或不可用");
                        default -> e;
                    };
                }
            });
        } finally { userNames.afterTransaction(actor, renamed.get()); }
    }
    public JSONObject usePatch(final String actor, final String key) throws Exception {
        try {
            return checkins.withLock(actor, () -> idempotency.execute(actor, "bag.patchCheckin", key, new JSONObject(), () -> {
                try { return checkins.patchInCurrentTransaction(actor); }
                catch (final CheckinMgmtService.Unavailable e) { throw new ApiV3Exception(404, "not_found", "用户不存在或不可用"); }
                catch (final CheckinPatchMutation.Rejected e) {
                    throw new ApiV3Exception(409, switch (e.reason) {
                        case NO_CARD -> "item_unavailable";
                        case NO_RECORD -> "patch_unavailable";
                        case LONGEST -> "current_longest";
                        case NOT_TODAY -> "not_checked_in";
                        case INVALID_RECORD -> "patch_unavailable";
                    }, "当前补签条件不满足，卡片和记录未消耗");
                }
            }));
        } finally { checkins.refresh(actor); }
    }
    public JSONObject useExemption(final String actor, final String item, final String key) throws Exception {
        return idempotency.execute(actor, "bag.exemption:" + item, key, new JSONObject(), () -> {
            try {
                final JSONObject bag = bags.useExemptionInCurrentTransaction(actor, item);
                return new JSONObject().put("itemCode", item).put("remainingDays", bag.optInt(BagMutation.REMAIN)).put("bag", BagMutation.dto(bag));
            } catch (final BagMutation.Unavailable e) {
                throw new ApiV3Exception(409, "item_unavailable", "没有可用的免签卡，或剩余天数已达上限");
            }
        });
    }
}
