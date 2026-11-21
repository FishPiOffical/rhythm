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
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

import java.math.BigDecimal;

/**
 * 按完整 URL 标识本人表情，全部分组镜像和回执在同一个严格事务中提交。
 */
@Service
public class ApiV3EmojiItemService {
    @Inject
    private UserRepository users;
    @Inject
    private OptionRepository options;
    @Inject
    private EmojiGroupRepository groups;
    @Inject
    private EmojiGroupItemRepository items;
    @Inject
    private EmojiRepository emojis;
    @Inject
    private ApiV3RequestRepository requests;

    public JSONObject add(final String actor, final String group, final JSONObject body, final String key) {
        id(group);
        return execute(actor, group, "", "add", ApiV3EmojiItemInputs.addition(body, false), key);
    }

    public JSONObject upload(final String actor, final JSONObject body, final String key) {
        return execute(actor, "", "", "upload", ApiV3EmojiItemInputs.addition(body, true), key);
    }

    public JSONObject update(final String actor, final String group, final String item, final JSONObject body, final String key) {
        id(group);
        id(item);
        return execute(actor, group, item, "update", ApiV3EmojiItemInputs.metadata(body), key);
    }

    public JSONObject remove(final String actor, final String group, final String item, final String key) {
        id(group);
        id(item);
        return execute(actor, group, item, "remove", new JSONObject(), key);
    }

    private JSONObject execute(final String actor, final String group, final String item, final String operation, final JSONObject values, final String key) {
        id(actor);
        try {
            final ApiV3MutationStore store = new ApiV3MutationStore() {
                public Transaction beginTransaction() {
                    return ApiV3Transactions.strict(requests.beginTransaction());
                }

                public boolean hasTransactionBegun() {
                    return requests.hasTransactionBegun();
                }

                public JSONObject getForUpdate(String receipt) throws RepositoryException {
                    namespaceAndActor(actor);
                    return requests.getForUpdate(receipt);
                }

                public String add(JSONObject row) throws RepositoryException {
                    return requests.add(row);
                }

                public void update(String receipt, JSONObject row, String... fields) throws RepositoryException {
                    requests.update(receipt, row, fields);
                }
            };
            final JSONObject request = new JSONObject(values.toString()).put("groupId", group).put("itemId", item);
            final JSONObject stored = ApiV3IdempotencyMgmtService.runAtomic(store, actor, "emoji.item." + operation, key, request,
                    () -> applyInCurrentTransaction(actor, group, item, operation, values));
            return result(stored, operation.equals("remove"));
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        }
    }

    /**
     * 命名空间锁须先于账号锁，组合调用也须遵循这一顺序。
     */
    public JSONObject addInCurrentTransaction(final String actor, final String group, final JSONObject body, final boolean allOnly) throws Exception {
        if (!allOnly) id(group);
        return applyInCurrentTransaction(actor, allOnly ? "" : group, "", allOnly ? "upload" : "add", ApiV3EmojiItemInputs.addition(body, allOnly));
    }

    public JSONObject updateInCurrentTransaction(final String actor, final String group, final String item, final JSONObject body) throws Exception {
        id(group);
        id(item);
        return applyInCurrentTransaction(actor, group, item, "update", ApiV3EmojiItemInputs.metadata(body));
    }

    public JSONObject removeInCurrentTransaction(final String actor, final String group, final String item) throws Exception {
        id(group);
        id(item);
        return applyInCurrentTransaction(actor, group, item, "remove", new JSONObject());
    }

    private JSONObject applyInCurrentTransaction(final String actor, final String groupId, final String itemId, final String operation, final JSONObject values) throws Exception {
        id(actor);
        if (!requests.hasTransactionBegun()) throw new IllegalStateException("emoji member transaction required");
        return ApiV3TransactionSavepoint.run(() -> {
            namespaceAndActor(actor);
            final var owned = groups.select("SELECT oId,emojiGroupUserId,emojiGroupType FROM `" + groups.getName() + "` WHERE emojiGroupUserId=? ORDER BY oId LIMIT 4097 FOR UPDATE", actor);
            if (owned.size() > 4096) throw unavailable();
            JSONObject target = null, all = null;
            for (final var row : owned) {
                if (!actor.equals(row.optString("emojiGroupUserId")))
                    throw unavailable();
                id(row.optString("oId"));
                final long type = number(row, "emojiGroupType");
                if (type != 0 && type != 1)
                    throw unavailable();
                if (type == 1) {
                    if (all != null)
                        throw unavailable();
                    all = row;
                }
                if (groupId.equals(row.optString("oId"))) target = row;
            }
            if (!operation.equals("upload") && target == null) throw ApiV3Requests.notFound();
            if (operation.equals("remove") || operation.equals("update")) {
                final JSONObject current = itemById(groupId, itemId);
                final String emojiId = current.getString("emojiGroupItemEmojiId");
                id(emojiId);
                if (operation.equals("remove")) {
                    if (number(target, "emojiGroupType") == 1) {
                        // 从全部分组移除时，同步移除本人各分组中的成员关系。
                        for (final var row : owned)
                            ApiV3TransactionStatements.execute("DELETE FROM `" + items.getName() + "` WHERE emojiGroupItemGroupId=? AND emojiGroupItemEmojiId=?", row.getString("oId"), emojiId);
                    } else
                        ApiV3TransactionStatements.execute("DELETE FROM `" + items.getName() + "` WHERE oId=? AND emojiGroupItemGroupId=?", itemId, groupId);
                    return new JSONObject().put("id", itemId).put("groupId", groupId).put("deleted", true);
                }
                final JSONObject emoji = validEmoji(emojiId);
                if (ApiV3TransactionStatements.execute("UPDATE `" + items.getName() + "` SET emojiGroupItemName=?,emojiGroupItemSort=? WHERE oId=? AND emojiGroupItemGroupId=?", values.getString("name"), values.getInt("sort"), itemId, groupId) != 1)
                    throw unavailable();
                return member(itemId, groupId, emojiId, values.getString("name"), values.getInt("sort"), emoji.getString("emojiUrl"));
            }
            if (all == null) {
                final String newId = Ids.genTimeMillisId();
                final long now = System.currentTimeMillis();
                if (ApiV3TransactionStatements.execute("INSERT INTO `" + groups.getName() + "` (oId,emojiGroupUserId,emojiGroupName,emojiGroupSort,emojiGroupType,emojiGroupCreateTime,emojiGroupUpdateTime) VALUES(?,?,?,0,1,?,?)", newId, actor, "全部", now, now) != 1)
                    throw unavailable();
                all = new JSONObject().put("oId", newId).put("emojiGroupUserId", actor).put("emojiGroupType", 1);
            }
            if (operation.equals("upload")) target = all;
            final JSONObject emoji = values.has("emojiId") ? validEmoji(values.getString("emojiId")) : findOrCreateEmoji(values.getString("url"), actor);
            final String emojiId = emoji.getString("oId"), targetId = target.getString("oId");
            final JSONObject saved = ensureMember(targetId, emojiId, values, emoji.getString("emojiUrl"));
            if (!all.getString("oId").equals(targetId))
                ensureMember(all.getString("oId"), emojiId, values, emoji.getString("emojiUrl"));
            return saved;
        });
    }

    private JSONObject findOrCreateEmoji(final String url, final String actor) throws Exception {
        // 即使数据库排序规则忽略大小写，签名和路径也要用 BINARY 精确比较。
        final var rows = emojis.select("SELECT oId,emojiUrl,emojiStatus FROM `" + emojis.getName() + "` WHERE BINARY emojiUrl=BINARY ? LIMIT 2 FOR UPDATE", url);
        if (rows.size() > 1) throw unavailable();
        if (!rows.isEmpty()) {
            if (!url.equals(rows.getFirst().optString("emojiUrl")))
                throw unavailable();
            return checkedEmoji(rows.getFirst());
        }
        final String id = Ids.genTimeMillisId();
        if (ApiV3TransactionStatements.execute("INSERT INTO `" + emojis.getName() + "` (oId,emojiUrl,emojiUploaderId,emojiCreateTime,emojiStatus) VALUES(?,?,?,?,0)", id, url, actor, System.currentTimeMillis()) != 1)
            throw unavailable();
        return new JSONObject().put("oId", id).put("emojiUrl", url).put("emojiStatus", 0);
    }

    private JSONObject validEmoji(final String id) throws RepositoryException {
        final var rows = emojis.select("SELECT oId,emojiUrl,emojiStatus FROM `" + emojis.getName() + "` WHERE oId=? FOR UPDATE", id);
        if (rows.size() != 1 || !id.equals(rows.getFirst().optString("oId"))) throw ApiV3Requests.notFound();
        return checkedEmoji(rows.getFirst());
    }

    private JSONObject checkedEmoji(final JSONObject row) {
        id(row.optString("oId"));
        if (number(row, "emojiStatus") != 0) throw ApiV3Requests.notFound();
        ApiV3EmojiItemInputs.url(row.opt("emojiUrl"));
        return row;
    }

    private JSONObject ensureMember(final String group, final String emoji, final JSONObject values, final String url) throws Exception {
        final var rows = items.select("SELECT oId,emojiGroupItemName,emojiGroupItemSort FROM `" + items.getName() + "` WHERE emojiGroupItemGroupId=? AND emojiGroupItemEmojiId=? LIMIT 2 FOR UPDATE", group, emoji);
        if (rows.size() > 1) throw unavailable();
        if (!rows.isEmpty()) {
            final var row = rows.getFirst();
            // 重复添加时沿用已有元数据，包括历史名称。
            if (!(row.opt("emojiGroupItemName") instanceof String name) || name.length() > 4096) throw unavailable();
            return member(row.getString("oId"), group, emoji, name, integer(row, "emojiGroupItemSort"), url);
        }
        int sort = values.getInt("sort");
        if (sort <= 0) {
            final var max = items.select("SELECT emojiGroupItemSort FROM `" + items.getName() + "` WHERE emojiGroupItemGroupId=? ORDER BY emojiGroupItemSort DESC,oId DESC LIMIT 1 FOR UPDATE", group);
            if (max.isEmpty())
                sort = 1;
            else {
                int last = integer(max.getFirst(), "emojiGroupItemSort");
                if (last == Integer.MAX_VALUE)
                    throw unavailable();
                sort = last + 1;
            }
        }
        final String id = Ids.genTimeMillisId();
        if (ApiV3TransactionStatements.execute("INSERT INTO `" + items.getName() + "` (oId,emojiGroupItemGroupId,emojiGroupItemEmojiId,emojiGroupItemName,emojiGroupItemSort,emojiGroupItemCreateTime) VALUES(?,?,?,?,?,?)", id, group, emoji, values.getString("name"), sort, System.currentTimeMillis()) != 1)
            throw unavailable();
        return member(id, group, emoji, values.getString("name"), sort, url);
    }

    private JSONObject itemById(final String group, final String item) throws RepositoryException {
        final var rows = items.select("SELECT oId,emojiGroupItemEmojiId FROM `" + items.getName() + "` WHERE oId=? AND emojiGroupItemGroupId=? FOR UPDATE", item, group);
        if (rows.size() != 1 || !item.equals(rows.getFirst().optString("oId")))
            throw ApiV3Requests.notFound();
        return rows.getFirst();
    }

    private void namespaceAndActor(final String actor) throws RepositoryException {
        if (options.getForUpdate(Option.ID_C_STATISTIC_MEMBER_COUNT) == null) throw unavailable();
        final var rows = users.select("SELECT oId,userStatus FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.size() != 1 || !actor.equals(rows.getFirst().optString("oId")) || number(rows.getFirst(), "userStatus") != UserExt.USER_STATUS_C_VALID)
            throw ApiV3Requests.notFound();
    }

    private static JSONObject member(String id, String group, String emoji, String name, int sort, String url) {
        return new JSONObject().put("id", id).put("groupId", group).put("emojiId", emoji).put("name", name).put("sort", sort).put("url", url);
    }

    private static JSONObject result(JSONObject row, boolean deleted) {
        id(row.optString("id"));
        id(row.optString("groupId"));
        if (deleted) {
            if (!Boolean.TRUE.equals(row.opt("deleted")))
                throw unavailable();
            return new JSONObject().put("id", row.getString("id")).put("groupId", row.getString("groupId")).put("deleted", true);
        }
        id(row.optString("emojiId"));
        if (!(row.opt("name") instanceof String name) || name.length() > 4096)
            throw unavailable();
        return member(row.getString("id"), row.getString("groupId"), row.getString("emojiId"), name, integer(row, "sort"), ApiV3EmojiItemInputs.url(row.opt("url")));
    }

    private static int integer(JSONObject row, String key) {
        long value = number(row, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw unavailable();
        return (int) value;
    }

    private static long number(JSONObject row, String key) {
        if (!(row.opt(key) instanceof Number n))
            throw unavailable();
        try {
            return new BigDecimal(n.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw unavailable();
        }
    }

    private static void id(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "表情成员操作暂不可用");
    }
}
