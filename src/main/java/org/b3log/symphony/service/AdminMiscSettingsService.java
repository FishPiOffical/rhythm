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

import org.b3log.latke.Keys;
import org.b3log.latke.http.Request;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.repository.OperationRepository;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminMiscSettingsService {
    private static final Map<String, String> PERMISSIONS = Map.of(
            Option.ID_C_MISC_ALLOW_REGISTER, Permission.PERMISSION_ID_C_MISC_REGISTER_METHOD,
            Option.ID_C_MISC_ALLOW_ANONYMOUS_VIEW, Permission.PERMISSION_ID_C_MISC_ALLOW_ANONYMOUS_VIEW,
            Option.ID_C_MISC_ALLOW_ADD_ARTICLE, Permission.PERMISSION_ID_C_MISC_ALLOW_ADD_ARTICLE,
            Option.ID_C_MISC_ALLOW_ADD_COMMENT, Permission.PERMISSION_ID_C_MISC_ALLOW_ADD_COMMENT,
            Option.ID_C_MISC_LANGUAGE, Permission.PERMISSION_ID_C_MISC_LANGUAGE);
    private static final Map<String, List<String>> VALUES = Map.of(
            Option.ID_C_MISC_ALLOW_REGISTER, List.of("0", "1", "2"),
            Option.ID_C_MISC_ALLOW_ANONYMOUS_VIEW, List.of("0", "1"),
            Option.ID_C_MISC_ALLOW_ADD_ARTICLE, List.of("0", "1"),
            Option.ID_C_MISC_ALLOW_ADD_COMMENT, List.of("0", "1"),
            Option.ID_C_MISC_LANGUAGE, List.of("0", "zh_CN", "en_US"),
            Option.ID_C_MISC_ARTICLE_VISIT_COUNT_MODE, List.of("0", "1"));

    @Inject private OptionRepository options;
    @Inject private OptionQueryService queries;
    @Inject private OperationRepository operations;

    public int updateLegacy(final JSONObject changes, final Set<String> permissions,
                            final Request request, final JSONObject actor) throws RepositoryException {
        final var tx = ApiV3Transactions.strict(options.beginTransaction());
        try {
            final int updated = updateInCurrentTransaction(changes, permissions);
            if (updated != 0) operations.add(Operation.newOperation(request, Operation.OPERATION_CODE_C_UPDATE_MISC, "", actor));
            tx.commit();
            afterCommit(changes);
            return updated;
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    public int updateInCurrentTransaction(final JSONObject changes, final Set<String> permissions) throws RepositoryException {
        if (!options.hasTransactionBegun()) throw new IllegalStateException("配置写入需要活动事务");
        for (final String id : changes.keySet().stream().sorted().toList()) {
            final List<String> allowed = VALUES.get(id);
            if (allowed == null || !(changes.opt(id) instanceof String value) || !allowed.contains(value)) {
                throw new ApiV3Exception(422, "invalid_option", "配置项或配置值无效");
            }
            if (!allowed(id, permissions)) throw ApiV3Requests.forbidden();
            final JSONObject current = options.getForUpdate(id);
            if (current == null || !Option.CATEGORY_C_MISC.equals(current.optString(Option.OPTION_CATEGORY))) {
                throw new ApiV3Exception(422, "invalid_option", "配置项不在站点杂项中");
            }
            options.updateValueInCurrentTransaction(id, value);
        }
        return changes.length();
    }

    public void afterCommit(final JSONObject changes) {
        for (final String id : changes.keySet()) options.invalidateValue(id);
    }

    public JSONArray list(final Set<String> permissions) {
        final List<JSONObject> rows = queries.getMisc();
        if (rows == null) throw ApiV3AdminService.unavailable();
        final JSONArray result = new JSONArray();
        for (final JSONObject row : rows) {
            final String id = row.optString(Keys.OBJECT_ID);
            final List<String> allowed = VALUES.getOrDefault(id, List.of());
            result.put(new JSONObject(row.toString())
                    .put("writable", !allowed.isEmpty() && allowed(id, permissions))
                    .put("allowedValues", allowed));
        }
        return result;
    }

    private static boolean allowed(final String id, final Set<String> permissions) {
        if (Option.ID_C_MISC_ARTICLE_VISIT_COUNT_MODE.equals(id)) {
            return permissions.containsAll(PERMISSIONS.values());
        }
        final String permission = PERMISSIONS.get(id);
        return permission != null && permissions.contains(permission);
    }
}
