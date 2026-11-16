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

import org.b3log.latke.http.Request;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.repository.UserPetRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** 管理员可查询当前宠物，并用系统昵称替换确认违规的名字。 */
@Service
public class ApiV3AdminPetService {
    @Inject private ApiV3AdminService admin;
    @Inject private UserPetRepository pets;
    @Inject private UserRepository users;

    public JSONObject list(final JSONObject identity, final String query, final int page, final int size)
            throws RepositoryException {
        final var tx = admin.begin();
        try {
            admin.authorizeInCurrentTransaction(identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN, true);
            final String from = " FROM `" + pets.getName() + "` p LEFT JOIN `" + users.getName() + "` u ON u.oId=p.userId";
            final List<Object> params = new ArrayList<>();
            final String where;
            if (query.isEmpty()) {
                where = "";
            } else {
                final String term = "%" + ApiV3AdminService.escapeLike(query) + "%";
                where = " WHERE (p.oId=? OR p.userId=? OR p.name LIKE ? ESCAPE '!'"
                        + " OR u.userName LIKE ? ESCAPE '!' OR u.userNickname LIKE ? ESCAPE '!')";
                params.addAll(List.of(query, query, term, term, term));
            }
            final long total = pets.select("SELECT COUNT(*) AS total" + from + where, params.toArray())
                    .getFirst().getLong("total");
            params.add(size);
            params.add((page - 1L) * size);
            final var rows = pets.select("SELECT p.oId AS petId,p.userId,p.name,p.speciesId,u.userName,u.userNickname"
                    + from + where + " ORDER BY p.adoptedAt DESC,p.oId DESC LIMIT ? OFFSET ?", params.toArray());
            final JSONArray list = new JSONArray();
            for (final var row : rows) {
                final String id = row.getString("petId");
                final String name = row.getString("name");
                list.put(new JSONObject().put("petId", id).put("name", name).put("speciesId", row.getString("speciesId"))
                        .put("userId", row.getString("userId")).put("userName", row.optString("userName"))
                        .put("userNickname", row.optString("userNickname")).put("moderated", replacement(id).equals(name)));
            }
            tx.commit();
            return ApiV3Requests.pagination(list, page, size, total);
        } finally {
            ApiV3AdminService.rollbackIfActive(tx);
        }
    }

    public JSONObject moderateName(final JSONObject identity, final String id, final JSONObject body,
                                   final String key, final Request request) throws Exception {
        if (!id.matches("[1-9][0-9]{0,18}") || !body.keySet().equals(Set.of("expectedName"))
                || !(body.opt("expectedName") instanceof String expectedName)
                || expectedName.isBlank() || expectedName.length() > 256) throw ApiV3WriteInputs.invalid();
        final String name = replacement(id);
        final var input = new JSONObject().put("petId", id).put("expectedName", expectedName);
        final var actor = new AtomicReference<ApiV3AdminService.AuthorizedActor>();
        return ApiV3IdempotencyMgmtService.runAtomic(admin.guardedWrite(identity, null, true, actor),
                ApiV3AdminService.actorId(identity), "admin.pet.moderate-name", key, input, () -> {
                    final var rows = pets.select("SELECT name,updatedAt FROM `" + pets.getName() + "` WHERE oId=? FOR UPDATE", id);
                    if (rows.isEmpty()) throw ApiV3Requests.notFound();
                    final var pet = rows.getFirst();
                    final String currentName = pet.getString("name");
                    if (!name.equals(currentName)) {
                        // 管理员只处理已看到的昵称，不覆盖期间用户新提交并通过审核的昵称。
                        if (!expectedName.equals(currentName)) {
                            throw new ApiV3Exception(409, "pet_name_changed", "宠物昵称已变化，请刷新后核对");
                        }
                        final long now = Math.max(System.currentTimeMillis(), pet.getLong("updatedAt"));
                        pets.update(id, new JSONObject().put("name", name).put("updatedAt", now), "name", "updatedAt");
                        admin.recordOperation(request, Operation.OPERATION_CODE_C_MODERATE_PET_NAME, id, actor.get().user());
                    }
                    return new JSONObject().put("petId", id).put("moderatedName", name);
                });
    }

    private static String replacement(final String id) {
        return "违规昵称" + id.substring(Math.max(0, id.length() - 6));
    }
}
