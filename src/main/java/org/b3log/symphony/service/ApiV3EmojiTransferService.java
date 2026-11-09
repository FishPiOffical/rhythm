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
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;

/**
 * 创建不可变的分享快照，独立导入分组并迁移本人云端数据。所有写入由当前事务统一提交。
 */
@Service
public class ApiV3EmojiTransferService {
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
    private EmojiShareRepository shares;
    @Inject
    private CloudRepository cloud;
    @Inject
    private ApiV3RequestRepository requests;
    @Inject
    private ApiV3EmojiItemService members;
    @Inject
    private ApiV3EmojiGroupService groupWrites;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public JSONObject share(String actor, String groupId, String key) {
        id(groupId);
        return execute(actor, "share", new JSONObject().put("groupId", groupId), key);
    }

    public JSONObject importShare(String actor, JSONObject body, String key) {
        return execute(actor, "import", new JSONObject().put("shareCode", ApiV3EmojiTransferInputs.code(body)), key);
    }

    public JSONObject migrate(String actor, String key) {
        return execute(actor, "migrate", new JSONObject(), key);
    }

    private JSONObject execute(String actor, String operation, JSONObject input, String key) {
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
                    // 读取回执前先按统一顺序锁定表情命名空间和账号。
                    lockNamespaceAndActor(actor);
                    return requests.getForUpdate(receipt);
                }

                public String add(JSONObject row) throws RepositoryException {
                    return requests.add(row);
                }

                public void update(String receipt, JSONObject row, String... fields) throws RepositoryException {
                    requests.update(receipt, row, fields);
                }
            };
            final JSONObject row = ApiV3IdempotencyMgmtService.runAtomic(store, actor, "emoji." + operation, key, input, () -> applyInCurrentTransaction(actor, operation, input));
            return result(operation, row);
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw ApiV3EmojiTransferInputs.unavailable();
        }
    }

    public JSONObject applyInCurrentTransaction(String actor, String operation, JSONObject input) throws Exception {
        id(actor);
        if (!Set.of("share", "import", "migrate").contains(operation)) throw ApiV3WriteInputs.invalid();
        if (!requests.hasTransactionBegun()) throw new IllegalStateException("emoji transfer transaction required");
        return ApiV3TransactionSavepoint.run(() -> {
            lockNamespaceAndActor(actor);
            final var owned = groups.select("SELECT oId,emojiGroupUserId,emojiGroupName,emojiGroupSort,emojiGroupType FROM `" + groups.getName() + "` WHERE emojiGroupUserId=? ORDER BY oId LIMIT 4097 FOR UPDATE", actor);
            if (owned.size() > 4096) throw ApiV3EmojiTransferInputs.unavailable();
            int allCount = 0;
            for (var group : owned) {
                id(group.optString("oId"));
                if (!actor.equals(group.optString("emojiGroupUserId")))
                    throw ApiV3EmojiTransferInputs.unavailable();
                long type = number(group, "emojiGroupType");
                if (type == 1)
                    allCount++;
                else if (type != 0)
                    throw ApiV3EmojiTransferInputs.unavailable();
            }
            if (allCount > 1) throw ApiV3EmojiTransferInputs.unavailable();
            return switch (operation) {
                case "share" -> createShare(actor, input.getString("groupId"), owned);
                case "import" -> importSnapshot(actor, ApiV3EmojiTransferInputs.code(input), owned);
                default -> migrateCloud(actor);
            };
        });
    }

    private JSONObject createShare(String actor, String groupId, List<JSONObject> owned) throws Exception {
        id(groupId);
        JSONObject group = null;
        for (var value : owned)
            if (groupId.equals(value.getString("oId")))
                group = value;
        if (group == null) throw ApiV3Requests.notFound();
        if (number(group, "emojiGroupType") != 0)
            throw new ApiV3Exception(409, "default_group_protected", "全部分组不能分享");
        final String name = groupName(group.opt("emojiGroupName"));
        final var rows = items.select("SELECT oId,emojiGroupItemEmojiId,emojiGroupItemName,emojiGroupItemSort FROM `" + items.getName() + "` WHERE emojiGroupItemGroupId=? ORDER BY emojiGroupItemSort,oId LIMIT 257 FOR UPDATE", groupId);
        if (rows.size() > ApiV3EmojiTransferInputs.MAX_ITEMS) throw ApiV3EmojiTransferInputs.unavailable();
        if (rows.isEmpty()) throw new ApiV3Exception(409, "empty_group", "分组没有可分享的表情");
        final LinkedHashMap<String, JSONObject> entries = new LinkedHashMap<>();
        for (var row : rows) {
            final String emojiId = row.optString("emojiGroupItemEmojiId");
            id(emojiId);
            final var image = emojis.select("SELECT oId,emojiUrl,emojiStatus FROM `" + emojis.getName() + "` WHERE oId=? FOR UPDATE", emojiId);
            if (image.size() != 1 || !emojiId.equals(image.getFirst().optString("oId")) || number(image.getFirst(), "emojiStatus") != 0)
                throw ApiV3WriteInputs.invalid();
            final JSONObject entry = ApiV3EmojiTransferInputs.entry(new JSONObject().put("url", image.getFirst().opt("emojiUrl")).put("name", row.opt("emojiGroupItemName")).put("sort", row.opt("emojiGroupItemSort")));
            entries.putIfAbsent(entry.getString("url"), entry);
        }
        final JSONArray snapshot = new JSONArray(entries.values());
        ApiV3EmojiTransferInputs.array(snapshot.toString());
        final String code = newCode();
        final long now = System.currentTimeMillis();
        if (ApiV3TransactionStatements.execute("INSERT INTO `" + shares.getName() + "` (oId,emojiShareCode,emojiShareOwnerUserId,emojiShareGroupId,emojiShareGroupName,emojiShareSnapshot,emojiShareEmojiCount,emojiShareImportedCount,emojiShareCreatedTime) VALUES(?,?,?,?,?,?,?,0,?)", Ids.genTimeMillisId(), code, actor, groupId, name, snapshot.toString(), snapshot.length(), now) != 1)
            throw ApiV3EmojiTransferInputs.unavailable();
        return new JSONObject().put("shareCode", code).put("groupName", name).put("emojiCount", snapshot.length()).put("createdTime", now);
    }

    protected String randomCode() {
        final var out = new StringBuilder(12);
        for (int i = 0; i < 12; i++)
            out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return out.toString();
    }

    private String newCode() throws RepositoryException {
        for (int i = 0; i < 16; i++) {
            final String code = randomCode();
            if (!code.matches("[A-Z0-9]{12}"))
                throw ApiV3EmojiTransferInputs.unavailable();
            if (shares.select("SELECT oId FROM `" + shares.getName() + "` WHERE emojiShareCode=? LIMIT 1 FOR UPDATE", code).isEmpty())
                return code;
        }
        throw ApiV3EmojiTransferInputs.unavailable();
    }

    private JSONObject importSnapshot(String actor, String code, List<JSONObject> owned) throws Exception {
        final var rows = shares.select("SELECT oId,emojiShareCode,emojiShareGroupName,SUBSTR(emojiShareSnapshot,1,1048577) AS emojiShareSnapshot,emojiShareImportedCount FROM `" + shares.getName() + "` WHERE emojiShareCode=? LIMIT 2 FOR UPDATE", code);
        if (rows.isEmpty())
            throw ApiV3Requests.notFound();
        if (rows.size() != 1)
            throw ApiV3EmojiTransferInputs.unavailable();
        final var share = rows.getFirst();
        if (!code.equals(share.optString("emojiShareCode"))) throw ApiV3Requests.notFound();
        final String sourceName = groupName(share.opt("emojiShareGroupName"));
        final JSONArray array = ApiV3EmojiTransferInputs.array(share.opt("emojiShareSnapshot"));
        final var entries = validatedEntries(array, false);
        if (entries.isEmpty()) throw ApiV3WriteInputs.invalid();
        final long counter = number(share, "emojiShareImportedCount");
        if (counter < 0 || counter >= Integer.MAX_VALUE)
            throw ApiV3EmojiTransferInputs.unavailable();
        final String name = importName(actor, sourceName);
        int sort = 0;
        for (var group : owned)
            sort = Math.max(sort, integer(group, "emojiGroupSort"));
        if (sort == Integer.MAX_VALUE)
            throw ApiV3EmojiTransferInputs.unavailable();
        final String groupId = groupWrites.createInCurrentTransaction(actor, new JSONObject().put("name", name).put("sort", sort + 1)).getString("id");
        for (var entry : entries.values()) members.addInCurrentTransaction(actor, groupId, entry, false);
        if (ApiV3TransactionStatements.execute("UPDATE `" + shares.getName() + "` SET emojiShareImportedCount=? WHERE oId=?", counter + 1, share.getString("oId")) != 1)
            throw ApiV3EmojiTransferInputs.unavailable();
        return new JSONObject().put("groupId", groupId).put("groupName", name).put("emojiCount", entries.size()).put("sourceGroupName", sourceName);
    }

    private JSONObject migrateCloud(String actor) throws Exception {
        final var rows = cloud.select("SELECT oId,SUBSTR(data,1,1048577) AS data FROM `" + cloud.getName() + "` WHERE userId=? AND gameId=? LIMIT 2 FOR UPDATE", actor, "emojis");
        if (rows.isEmpty())
            throw new ApiV3Exception(409, "no_legacy_emojis", "没有历史表情可迁移");
        if (rows.size() != 1)
            throw ApiV3EmojiTransferInputs.unavailable();
        final JSONArray array = ApiV3EmojiTransferInputs.array(rows.getFirst().opt("data"));
        final var entries = validatedEntries(array, true);
        if (entries.isEmpty())
            throw new ApiV3Exception(409, "no_legacy_emojis", "没有历史表情可迁移");
        final long before = allMemberCount(actor);
        String allId = "";
        for (var entry : entries.values())
            allId = members.addInCurrentTransaction(actor, "", entry, true).getString("groupId");
        final long after = allMemberCount(actor), added = after - before;
        if (added < 0 || added > entries.size()) throw ApiV3EmojiTransferInputs.unavailable();
        return new JSONObject().put("groupId", allId).put("processedCount", entries.size()).put("addedCount", added);
    }

    private LinkedHashMap<String, JSONObject> validatedEntries(JSONArray array, boolean urls) throws Exception {
        final var entries = new LinkedHashMap<String, JSONObject>();
        for (int i = 0; i < array.length(); i++) {
            final JSONObject entry = urls ? ApiV3EmojiItemInputs.addition(new JSONObject().put("url", array.opt(i)), true) : ApiV3EmojiTransferInputs.entry(array.opt(i));
            // 去重前逐项校验原始数据，重复项的元数据无效时也拒绝整批。
            final String url = entry.getString("url");
            final var existing = emojis.select("SELECT oId,emojiUrl,emojiStatus FROM `" + emojis.getName() + "` WHERE BINARY emojiUrl=BINARY ? LIMIT 2 FOR UPDATE", url);
            if (existing.size() > 1) throw ApiV3EmojiTransferInputs.unavailable();
            if (!existing.isEmpty() && (!url.equals(existing.getFirst().optString("emojiUrl")) || number(existing.getFirst(), "emojiStatus") != 0))
                throw ApiV3WriteInputs.invalid();
            entries.putIfAbsent(url, entry);
        }
        return entries;
    }

    private long allMemberCount(String actor) throws RepositoryException {
        final var rows = items.select("SELECT COUNT(*) AS total FROM `" + items.getName() + "` i JOIN `" + groups.getName() + "` g ON g.oId=i.emojiGroupItemGroupId WHERE g.emojiGroupUserId=? AND g.emojiGroupType=1 FOR UPDATE", actor);
        if (rows.size() != 1)
            throw ApiV3EmojiTransferInputs.unavailable();
        final long count = number(rows.getFirst(), "total");
        if (count < 0)
            throw ApiV3EmojiTransferInputs.unavailable();
        return count;
    }

    private String importName(String actor, String source) throws RepositoryException {
        for (int i = 0; i <= 999; i++) {
            final String suffix = i == 0 ? "" : i == 1 ? "-导入" : "-导入" + i;
            int end = Math.min(source.length(), 64 - suffix.length());
            if (end > 0 && end < source.length() && Character.isHighSurrogate(source.charAt(end - 1)))
                end--;
            final String candidate = source.substring(0, end) + suffix;
            if (groups.select("SELECT oId FROM `" + groups.getName() + "` WHERE emojiGroupUserId=? AND emojiGroupName=? LIMIT 1 FOR UPDATE", actor, candidate).isEmpty())
                return candidate;
        }
        throw ApiV3EmojiTransferInputs.unavailable();
    }

    private String groupName(Object value) {
        return ApiV3EmojiGroupInputs.normalize(new JSONObject().put("name", value)).getString("name");
    }

    private void lockNamespaceAndActor(String actor) throws RepositoryException {
        if (options.getForUpdate(Option.ID_C_STATISTIC_MEMBER_COUNT) == null)
            throw ApiV3EmojiTransferInputs.unavailable();
        final var rows = users.select("SELECT oId,userStatus FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.size() != 1 || !actor.equals(rows.getFirst().optString("oId")) || number(rows.getFirst(), "userStatus") != UserExt.USER_STATUS_C_VALID)
            throw ApiV3Requests.notFound();
    }

    private static JSONObject result(String operation, JSONObject row) {
        if (operation.equals("share")) {
            if (!row.optString("shareCode").matches("[A-Z0-9]{12}")) throw ApiV3EmojiTransferInputs.unavailable();
            return new JSONObject().put("shareCode", row.getString("shareCode")).put("groupName", row.getString("groupName")).put("emojiCount", integer(row, "emojiCount")).put("createdTime", number(row, "createdTime"));
        }
        id(row.optString("groupId"));
        if (operation.equals("import"))
            return new JSONObject().put("groupId", row.getString("groupId")).put("groupName", row.getString("groupName")).put("emojiCount", integer(row, "emojiCount")).put("sourceGroupName", row.getString("sourceGroupName"));
        return new JSONObject().put("groupId", row.getString("groupId")).put("processedCount", integer(row, "processedCount")).put("addedCount", integer(row, "addedCount"));
    }

    private static int integer(JSONObject row, String key) {
        long n = number(row, key);
        if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE)
            throw ApiV3EmojiTransferInputs.unavailable();
        return (int) n;
    }

    private static long number(JSONObject row, String key) {
        if (!(row.opt(key) instanceof Number n))
            throw ApiV3EmojiTransferInputs.unavailable();
        try {
            return new BigDecimal(n.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw ApiV3EmojiTransferInputs.unavailable();
        }
    }

    private static void id(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
    }
}
