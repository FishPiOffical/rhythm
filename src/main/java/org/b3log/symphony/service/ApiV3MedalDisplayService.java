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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.MedalRepository;
import org.b3log.symphony.repository.UserMedalRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

/** Explicit owner display setting and replay receipt share the existing strict v3 root transaction. */
@Service
public class ApiV3MedalDisplayService {
    @Inject private UserRepository users;
    @Inject private MedalRepository medals;
    @Inject private UserMedalRepository grants;
    @Inject private ApiV3IdempotencyMgmtService idempotency;
    public JSONObject set(final String actor, final String medalId, final boolean displayed, final String key) throws Exception {
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
        ApiV3MedalDisplay.medalId(medalId);
        try {
            // Authentication may have used a cached account; replay must still require a current active owner.
            final var currentOwners = users.select("SELECT oId FROM `" + users.getName() + "` WHERE oId=? AND userStatus=?", actor, UserExt.USER_STATUS_C_VALID);
            if (currentOwners.size() != 1) throw ApiV3Requests.notFound();
            return idempotency.execute(actor, "medal.display", key, new JSONObject().put("medalId", medalId).put("displayed", displayed),
                    () -> setInCurrentTransaction(actor, medalId, displayed));
        } catch (final ApiV3Exception e) { throw e; }
        catch (final Exception e) {
            LogManager.getLogger(getClass()).warn("Medal display unavailable");
            throw new ApiV3Exception(503, "temporarily_unavailable", "勋章显示设置暂不可用");
        }
    }
    public JSONObject setInCurrentTransaction(final String actor, final String medalId, final boolean displayed) throws Exception {
        if (!grants.hasTransactionBegun()) throw new IllegalStateException("medal display transaction required");
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
        ApiV3MedalDisplay.medalId(medalId);
        return ApiV3TransactionSavepoint.run(() -> {
            final var owners = users.select("SELECT oId,userStatus FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
            if (owners.size() != 1 || owners.getFirst().optInt("userStatus", -1) != UserExt.USER_STATUS_C_VALID) throw ApiV3Requests.notFound();
            // Current-read the definition before locking its owned holding.
            final var definitions = medals.select("SELECT oId,medal_id FROM `" + medals.getName() + "` WHERE medal_id=? ORDER BY oId ASC LIMIT 2 FOR UPDATE", medalId);
            if (definitions.isEmpty()) throw ApiV3Requests.notFound();
            if (definitions.size() != 1) throw ApiV3MedalDisplay.inconsistent();
            final var holdings = grants.select("SELECT oId,user_id,medal_id,expire_time,display FROM `" + grants.getName()
                    + "` WHERE user_id=? AND medal_id=? ORDER BY oId ASC LIMIT 2 FOR UPDATE", actor, medalId);
            if (holdings.isEmpty()) throw ApiV3Requests.notFound();
            if (holdings.size() != 1) throw ApiV3MedalDisplay.inconsistent();
            final var current = holdings.getFirst();
            if (!actor.equals(current.optString("user_id")) || !medalId.equals(current.optString("medal_id"))) throw ApiV3MedalDisplay.inconsistent();
            final long expiry = ApiV3MedalDisplay.expiry(current.opt("expire_time"));
            if (expiry > 0 && expiry <= System.currentTimeMillis()) throw ApiV3Requests.notFound();
            if (ApiV3MedalDisplay.stored(current.opt("display")) != displayed) {
                final int changed = ApiV3TransactionStatements.execute("UPDATE `" + grants.getName()
                        + "` SET display=? WHERE oId=? AND user_id=? AND medal_id=?", displayed, current.getString("oId"), actor, medalId);
                if (changed != 1) throw ApiV3MedalDisplay.inconsistent();
            }
            return new JSONObject().put("id", medalId).put("displayed", displayed);
        });
    }
}
