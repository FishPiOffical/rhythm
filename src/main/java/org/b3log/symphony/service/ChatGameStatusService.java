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
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.ParkingGarageRepository;
import org.b3log.symphony.repository.PartnerMemberRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.PartnerTransactions;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class ChatGameStatusService {
    @Inject private ApiV3RequestRepository requests;
    @Inject private ApiV3SecurityProofService proofs;
    @Inject private UserRepository users;
    @Inject private ParkingGarageRepository garages;
    @Inject private PartnerMemberRepository members;
    @Inject private PartnerOperationsService operations;

    static List<String> ids(final String raw) {
        if (raw == null || raw.length() > 999) throw ApiV3Requests.invalid();
        final var ids = raw.split(",", -1);
        if (ids.length > 50) throw ApiV3Requests.invalid();
        for (final String id : ids) {
            if (!id.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
        }
        return Arrays.stream(ids).distinct().toList();
    }

    public JSONObject list(final JSONObject identity, final String rawIds) throws RepositoryException {
        // 复用游戏名片的第一方权限；仅从公开状态投影，不触发停车访问或搭班结算。
        return list(ids(rawIds), () -> proofs.authorizePartnerInCurrentTransaction(identity, "profile.read"));
    }

    public JSONObject listForSession(final JSONObject cookieUser, final String rawIds) throws RepositoryException {
        if (cookieUser == null) throw new ApiV3Requests.ApiException(401, "unauthorized", "请先登录");
        final String actor = cookieUser.optString("oId");
        return list(ids(rawIds), () -> {
            // 旧站凭据由页面会话校验，再按当前账号状态读取同一份公开摘要。
            if (users.select("SELECT oId FROM " + users.getName() + " WHERE oId=? AND userStatus=0", actor).isEmpty()) {
                throw ApiV3Requests.forbidden();
            }
        });
    }

    @FunctionalInterface
    private interface ViewerCheck {
        void verify() throws RepositoryException;
    }

    private JSONObject list(final List<String> ids, final ViewerCheck viewer) throws RepositoryException {
        final var tx = PartnerTransactions.begin(requests::beginTransaction);
        try {
            viewer.verify();
            final String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            final String uid = "u.oId COLLATE utf8mb4_0900_ai_ci";
            final var rows = users.select("SELECT u.oId, g.occupant, p.holder, p.price FROM " + users.getName()
                    + " u LEFT JOIN " + garages.getName() + " g ON g.oId=" + uid
                    + " LEFT JOIN " + members.getName() + " p ON p.oId=" + uid + " AND p.joined=1"
                    + " WHERE u.userStatus=0 AND u.oId IN (" + placeholders + ")"
                    + " AND (g.oId IS NOT NULL OR p.oId IS NOT NULL)", ids.toArray());
            final var partners = rows.stream().filter(row -> !row.isNull("holder"))
                    .map(row -> row.getString("oId")).toList();
            final var transfers = operations.effectiveTransfers(partners);
            final var list = new JSONArray();
            for (final var row : rows) {
                final String id = row.getString("oId");
                final var entry = new JSONObject().put("userId", id);
                if (!row.isNull("occupant")) entry.put("parkingFree", row.getString("occupant").isEmpty());
                if (!row.isNull("holder")) {
                    entry.put("partner", new JSONObject().put("full", PartnerService.quote(row) > 200)
                            .put("displayValue", PartnerOperationsService.displayValue(transfers.getOrDefault(id, 0L))));
                }
                list.put(entry);
            }
            tx.commit();
            return new JSONObject().put("list", list);
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }
}
