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
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/** 积分购买、续期和装备共享账号锁，消费与权益及回执一起提交。 */
@Service
public class ApiV3AppearanceShopService {
    @Inject private AppearanceProductRepository products;
    @Inject private AppearanceEntitlementRepository entitlements;
    @Inject private AppearancePurchaseRepository purchases;
    @Inject private ApiV3RequestRepository requests;
    @Inject private UserRepository users;
    @Inject private PointtransferRedcRepository transferIndex;
    @Inject private PointtransferMgmtService points;
    @Inject private ApiV3SecurityProofService proofs;

    protected long now() {
        return System.currentTimeMillis();
    }

    public JSONObject catalog() throws RepositoryException {
        final JSONArray list = new JSONArray();
        for (final var row : products.select("SELECT oId,price7,price30,enabled,revision FROM `" + products.getName() + "` ORDER BY oId")) {
            final var item = ApiV3AppearanceCatalog.find(row.optString("oId"));
            if (item == null) continue;
            validatePrice(row);
            list.put(new JSONObject().put("id", item.id()).put("kind", item.kind()).put("styleId", item.styleId())
                    .put("name", item.name()).put("version", 1).put("price7", row.getInt("price7"))
                    .put("price30", row.getInt("price30")).put("enabled", row.getInt("enabled") == 1)
                    .put("revision", row.getLong("revision")));
        }
        return new JSONObject().put("list", list);
    }

    public JSONObject inventory(JSONObject identity) throws Exception {
        final String actor = actor(identity);
        if (requests.hasTransactionBegun()) throw new IllegalStateException("外观库存需要根事务");
        Transaction tx = ApiV3Transactions.strict(requests.beginTransaction());
        try {
            if (!actor.equals(proofs.authorizeFirstPartyInCurrentTransaction(identity, "profile.read").optString("oId"))) throw ApiV3Requests.forbidden();
            final JSONObject owner = owner(actor);
            final JSONArray list = new JSONArray();
            String mountId = "";
            final long time = now();
            for (final var row : entitlements.select("SELECT productId,expiresAt,equipped FROM `" + entitlements.getName() + "` WHERE userId=?", actor)) {
                final var item = ApiV3AppearanceCatalog.find(row.optString("productId"));
                if (item == null) continue;
                final long expiry = row.getLong("expiresAt");
                list.put(new JSONObject().put("productId", item.id()).put("expiresAt", expiry));
                if (item.kind().equals("mount") && row.optInt("equipped") == 1 && expiry > time) mountId = item.styleId();
            }
            final JSONObject result = new JSONObject().put("list", list).put("mountId", mountId)
                    .put("serverNow", time).put("balance", owner.getLong("userPoint"));
            tx.commit();
            return result;
        } finally {
            if (tx.isActive()) tx.rollback();
        }
    }

    public JSONObject buy(JSONObject identity, JSONObject input, String key) throws Exception {
        final String actor = actor(identity);
        final String productId = input.optString("productId");
        final var product = ApiV3AppearanceCatalog.find(productId);
        if (product == null || !(input.opt("days") instanceof Number days) || days.doubleValue() != days.intValue()
                || !Set.of(7, 30).contains(days.intValue()) || !(input.opt("revision") instanceof Number revision)
                || revision.longValue() < 1 || revision.doubleValue() != revision.longValue()) throw ApiV3WriteInputs.invalid();
        final JSONObject normalized = new JSONObject().put("productId", productId).put("days", days.intValue()).put("revision", revision.longValue());
        return points.executeWithTransferLocks(actor, List.of(Pointtransfer.ID_C_SYS), () -> {
            try (final var boundary = ArticlePublicationCaches.open(requests::hasTransactionBegun)) {
                return ApiV3IdempotencyMgmtService.runAtomic(store(identity), actor, "appearance.buy", key, normalized, () -> {
                    final JSONObject user = owner(actor);
                    final var prices = products.select("SELECT oId,price7,price30,enabled,revision FROM `" + products.getName() + "` WHERE oId=? FOR UPDATE", productId);
                    if (prices.size() != 1) throw ApiV3Requests.notFound();
                    final JSONObject price = prices.getFirst();
                    validatePrice(price);
                    if (price.getInt("enabled") != 1) throw new ApiV3Exception(409, "product_unavailable", "该外观已下架");
                    if (price.getLong("revision") != revision.longValue()) throw new ApiV3Exception(409, "quote_changed", "价格已更新，请重新确认");
                    final int paid = price.getInt(days.intValue() == 7 ? "price7" : "price30");
                    if (user.getLong("userPoint") < paid) throw new ApiV3Exception(409, "insufficient_points", "积分不足");
                    final String entitlementId = entitlementId(actor, productId);
                    final var previous = entitlements.select("SELECT expiresAt FROM `" + entitlements.getName() + "` WHERE oId=? FOR UPDATE", entitlementId);
                    final long time = now();
                    final long expiry = ApiV3AppearanceCatalog.extend(time, previous.isEmpty() ? 0 : previous.getFirst().getLong("expiresAt"), days.intValue());
                    final String purchaseId = Ids.genTimeMillisId();
                    final String transferId = points.transferInCurrentTransaction(actor, Pointtransfer.ID_C_SYS,
                            Pointtransfer.TRANSFER_TYPE_C_APPEARANCE_PURCHASE, paid, "购买外观：" + product.name() + "（" + days.intValue() + "天）",
                            time, "", new PointtransferSource("", "", "appearance.buy", purchaseId), false);
                    if (transferId == null) throw unavailable();
                    // 积分流水索引也在本事务落库，不在提交后补查或异步写入。
                    for (final String account : List.of(actor, Pointtransfer.ID_C_SYS)) {
                        transferIndex.add(new JSONObject().put("oId", Ids.genTimeMillisId()).put("userId", account).put("rawId", transferId));
                    }
                    if (previous.isEmpty()) {
                        entitlements.add(new JSONObject().put("oId", entitlementId).put("userId", actor).put("productId", productId)
                                .put("expiresAt", expiry).put("equipped", 0).put("updatedAt", time));
                    } else {
                        entitlements.update(entitlementId, new JSONObject().put("expiresAt", expiry).put("updatedAt", time), "expiresAt", "updatedAt");
                    }
                    purchases.add(new JSONObject().put("oId", purchaseId).put("userId", actor).put("productId", productId)
                            .put("days", days.intValue()).put("paidPoints", paid).put("expiresAt", expiry).put("transferId", transferId).put("createdAt", time));
                    return new JSONObject().put("id", purchaseId).put("productId", productId).put("paidPoints", paid).put("expiresAt", expiry);
                });
            }
        });
    }

    public JSONObject equipMount(JSONObject identity, String productId, String key) throws Exception {
        final String actor = actor(identity);
        final var product = ApiV3AppearanceCatalog.find(productId);
        if (!productId.isEmpty() && (product == null || !product.kind().equals("mount"))) throw ApiV3WriteInputs.invalid();
        return ApiV3IdempotencyMgmtService.runAtomic(store(identity), actor, "appearance.mount", key,
                new JSONObject().put("productId", productId), () -> {
                    if (product != null && !owns(actor, product.id(), now())) throw new ApiV3Exception(403, "appearance_required", "请先购买或续期该外观");
                    for (final var row : entitlements.select("SELECT oId,productId,equipped FROM `" + entitlements.getName() + "` WHERE userId=? FOR UPDATE", actor)) {
                        final var item = ApiV3AppearanceCatalog.find(row.optString("productId"));
                        if (item == null || !item.kind().equals("mount")) continue;
                        entitlements.update(row.getString("oId"), new JSONObject().put("equipped", item.id().equals(productId) ? 1 : 0).put("updatedAt", now()), "equipped", "updatedAt");
                    }
                    return new JSONObject().put("mountId", product == null ? "" : product.styleId());
                });
    }

    /** 设置及发消息已持有用户行锁，批量读取两类权益，不改写历史快照。 */
    public JSONObject effective(String actor, JSONObject appearance, boolean reject) throws RepositoryException {
        final JSONObject result = new JSONObject(appearance.toString());
        final List<ApiV3AppearanceCatalog.Product> required = new ArrayList<>();
        for (final String kind : List.of("bubble", "frame")) {
            final var item = ApiV3AppearanceCatalog.style(kind, result.optString(kind + "Id"));
            if (item != null) required.add(item);
        }
        if (required.isEmpty()) return result;
        final Set<String> owned = new HashSet<>();
        for (final var row : entitlements.select("SELECT productId FROM `" + entitlements.getName() + "` WHERE userId=? AND expiresAt>?", actor, now())) owned.add(row.getString("productId"));
        for (final var item : required) {
            if (owned.contains(item.id())) continue;
            if (reject) throw new ApiV3Exception(403, "appearance_required", "请先购买或续期该外观");
            result.put(item.kind() + "Id", item.kind().equals("bubble") ? "classic" : "none");
        }
        return result;
    }

    /** 一批进场账号只读一次权益，有效账号校验由进场服务继续负责。 */
    public Map<String, ApiV3AppearanceCatalog.Product> mounts(Collection<String> actors, long time) throws RepositoryException {
        final Map<String, ApiV3AppearanceCatalog.Product> result = new HashMap<>();
        if (actors.isEmpty()) return result;
        final List<Object> params = new ArrayList<>(actors);
        params.add(time);
        for (final var row : entitlements.select("SELECT userId,productId FROM `" + entitlements.getName() + "` WHERE userId IN ("
                + String.join(",", Collections.nCopies(actors.size(), "?")) + ") AND equipped=1 AND expiresAt>?", params.toArray())) {
            final var item = ApiV3AppearanceCatalog.find(row.optString("productId"));
            if (item != null && item.kind().equals("mount")) result.put(row.getString("userId"), item);
        }
        return result;
    }

    private boolean owns(String actor, String product, long time) throws RepositoryException {
        return !entitlements.select("SELECT oId FROM `" + entitlements.getName() + "` WHERE oId=? AND expiresAt>?", entitlementId(actor, product), time).isEmpty();
    }

    private ApiV3MutationStore store(JSONObject identity) {
        return new ApiV3MutationStore() {
            public Transaction beginTransaction() { return ApiV3Transactions.strict(requests.beginTransaction()); }
            public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
            public JSONObject getForUpdate(String id) throws RepositoryException {
                authorize(identity);
                owner(actor(identity));
                return requests.getForUpdate(id);
            }
            public String add(JSONObject row) throws RepositoryException { return requests.add(row); }
            public void update(String id, JSONObject row, String... fields) throws RepositoryException { requests.update(id, row, fields); }
        };
    }

    private void authorize(JSONObject identity) throws RepositoryException {
        if (!actor(identity).equals(proofs.authorizeFirstPartyInCurrentTransaction(identity, "settings.write").optString("oId"))) throw ApiV3Requests.forbidden();
    }

    private JSONObject owner(String actor) throws RepositoryException {
        final var rows = users.select("SELECT oId,userPoint,userStatus FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.size() != 1 || rows.getFirst().optInt("userStatus", -1) != 0) throw ApiV3Requests.forbidden();
        return rows.getFirst();
    }

    private static String actor(JSONObject identity) {
        final String id = identity == null || identity.optJSONObject("user") == null ? "" : identity.getJSONObject("user").optString("oId");
        if (!id.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.forbidden();
        return id;
    }

    private static String entitlementId(String actor, String product) {
        try {
            return ApiV3TokenUtil.hash("appearance\0" + actor + "\0" + product);
        } catch (Exception failure) {
            throw unavailable();
        }
    }

    private static void validatePrice(JSONObject row) {
        for (final String field : List.of("price7", "price30", "revision")) {
            if (!(row.opt(field) instanceof Number number) || number.doubleValue() != number.longValue() || number.longValue() < 1
                    || (field.startsWith("price") && number.longValue() > 100000000)) throw unavailable();
        }
        if (!Set.of(0, 1).contains(row.optInt("enabled", -1))) throw unavailable();
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "外观商城暂不可用");
    }
}
