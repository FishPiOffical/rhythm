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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.EmojiGroupRepository;
import org.b3log.symphony.repository.EmojiGroupItemRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

import java.math.BigDecimal;

/**
 * 本人表情分组修改与重放回执同严格事务提交，已有读取入口保持只读。
 */
@Service
public class ApiV3EmojiGroupService {
    @Inject
    private UserRepository users;
    @Inject
    private EmojiGroupRepository groups;
    @Inject
    private EmojiGroupItemRepository items;
    @Inject
    private ApiV3RequestRepository requests;

    public JSONObject create(final String actor, final JSONObject body, final String key) {
        return execute(actor, "", "create", ApiV3EmojiGroupInputs.normalize(body), key);
    }

    public JSONObject update(final String actor, final String groupId, final JSONObject body, final String key) {
        id(groupId);
        return execute(actor, groupId, "update", ApiV3EmojiGroupInputs.normalize(body), key);
    }

    public JSONObject delete(final String actor, final String groupId, final String key) {
        id(groupId);
        return execute(actor, groupId, "delete", new JSONObject(), key);
    }

    private JSONObject execute(final String actor, final String groupId, final String operation, final JSONObject values, final String key) {
        id(actor);
        final JSONObject request = new JSONObject(values.toString()).put("groupId", groupId);
        try {
            final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                public Transaction beginTransaction() {
                    return ApiV3Transactions.strict(requests.beginTransaction());
                }

                public boolean hasTransactionBegun() {
                    return requests.hasTransactionBegun();
                }

                public JSONObject getForUpdate(final String receipt) throws RepositoryException {
                    actorInCurrentTransaction(actor);
                    return requests.getForUpdate(receipt);
                }

                public String add(final JSONObject row) throws RepositoryException {
                    return requests.add(row);
                }

                public void update(final String receipt, final JSONObject row, final String... fields) throws RepositoryException {
                    requests.update(receipt, row, fields);
                }
            };
            final JSONObject stored = ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "emoji.group." + operation, key, request,
                    () -> applyInCurrentTransaction(actor, groupId, operation, values));
            return result(stored, operation.equals("delete"));
        } catch (final ApiV3Exception error) {
            throw error;
        } catch (final Exception error) {
            throw unavailable();
        }
    }

    public JSONObject createInCurrentTransaction(final String actor, final JSONObject body) throws Exception {
        return applyInCurrentTransaction(actor, "", "create", ApiV3EmojiGroupInputs.normalize(body));
    }

    public JSONObject updateInCurrentTransaction(final String actor, final String groupId, final JSONObject body) throws Exception {
        id(groupId);
        return applyInCurrentTransaction(actor, groupId, "update", ApiV3EmojiGroupInputs.normalize(body));
    }

    public JSONObject deleteInCurrentTransaction(final String actor, final String groupId) throws Exception {
        id(groupId);
        return applyInCurrentTransaction(actor, groupId, "delete", new JSONObject());
    }

    private JSONObject applyInCurrentTransaction(final String actor, final String groupId, final String operation, final JSONObject values) throws Exception {
        id(actor);
        if (!requests.hasTransactionBegun()) throw new IllegalStateException("emoji group transaction required");
        return ApiV3TransactionSavepoint.run(() -> {
            actorInCurrentTransaction(actor);
            if (!operation.equals("create")) {
                final var rows = groups.select("SELECT oId,emojiGroupUserId,emojiGroupType FROM `" + groups.getName()
                        + "` WHERE oId=? AND emojiGroupUserId=? FOR UPDATE", groupId, actor);
                if (rows.size() != 1 || !groupId.equals(rows.getFirst().optString("oId"))
                        || !actor.equals(rows.getFirst().optString("emojiGroupUserId"))) throw ApiV3Requests.notFound();
                final long type = exact(rows.getFirst(), "emojiGroupType");
                if (type == 1) throw new ApiV3Exception(409, "default_group_protected", "不能修改或删除全部分组");
                if (type != 0) throw unavailable();
            }
            if (operation.equals("delete")) {
                ApiV3TransactionStatements.execute("DELETE FROM `" + items.getName() + "` WHERE emojiGroupItemGroupId=?", groupId);
                if (ApiV3TransactionStatements.execute("DELETE FROM `" + groups.getName()
                        + "` WHERE oId=? AND emojiGroupUserId=? AND emojiGroupType=0", groupId, actor) != 1)
                    throw unavailable();
                return new JSONObject().put("id", groupId).put("deleted", true);
            }
            final var duplicate = groups.select("SELECT oId FROM `" + groups.getName()
                    + "` WHERE emojiGroupUserId=? AND emojiGroupName=? AND oId<>? LIMIT 1 FOR UPDATE", actor, values.getString("name"), groupId);
            if (!duplicate.isEmpty()) throw new ApiV3Exception(409, "group_name_exists", "分组名已存在");
            final long now = System.currentTimeMillis();
            final String id = operation.equals("create") ? Ids.genTimeMillisId() : groupId;
            final int changed;
            if (operation.equals("create")) {
                changed = ApiV3TransactionStatements.execute("INSERT INTO `" + groups.getName()
                                + "` (oId,emojiGroupUserId,emojiGroupName,emojiGroupSort,emojiGroupCreateTime,emojiGroupUpdateTime,emojiGroupType) VALUES(?,?,?,?,?,?,0)",
                        id, actor, values.getString("name"), values.getInt("sort"), now, now);
            } else {
                changed = ApiV3TransactionStatements.execute("UPDATE `" + groups.getName()
                                + "` SET emojiGroupName=?,emojiGroupSort=?,emojiGroupUpdateTime=? WHERE oId=? AND emojiGroupUserId=? AND emojiGroupType=0",
                        values.getString("name"), values.getInt("sort"), now, id, actor);
            }
            if (changed != 1) throw unavailable();
            return new JSONObject().put("id", id).put("name", values.getString("name")).put("sort", values.getInt("sort")).put("type", 0);
        });
    }

    private void actorInCurrentTransaction(final String actor) throws RepositoryException {
        final var rows = users.select("SELECT oId,userStatus FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.size() != 1 || !actor.equals(rows.getFirst().optString("oId"))
                || exact(rows.getFirst(), "userStatus") != UserExt.USER_STATUS_C_VALID) throw ApiV3Requests.notFound();
    }

    private static JSONObject result(final JSONObject row, final boolean deleted) {
        id(row.optString("id"));
        if (deleted) {
            if (!Boolean.TRUE.equals(row.opt("deleted"))) throw unavailable();
            return new JSONObject().put("id", row.getString("id")).put("deleted", true);
        }
        if (exact(row, "type") != 0) throw unavailable();
        final JSONObject values = ApiV3EmojiGroupInputs.normalize(new JSONObject().put("name", row.opt("name")).put("sort", row.opt("sort")));
        return new JSONObject().put("id", row.getString("id")).put("name", values.getString("name")).put("sort", values.getInt("sort")).put("type", 0);
    }

    private static long exact(final JSONObject row, final String key) {
        if (!(row.opt(key) instanceof Number number)) throw unavailable();
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (final ArithmeticException | NumberFormatException error) {
            throw unavailable();
        }
    }

    private static void id(final String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "表情分组操作暂不可用");
    }
}
