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
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.PartnerMemberRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.FollowRepository;
import org.b3log.symphony.repository.GameWalletLedgerRepository;
import org.b3log.symphony.repository.PartnerEventRepository;
import org.b3log.symphony.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class PartnerService {
    @Inject private PartnerEventRepository events;
    @Inject private FollowRepository follows;
    @Inject private GameWalletLedgerRepository ledger;
    @Inject private PartnerMemberRepository members;
    @Inject private UserRepository users;
    @Inject private ApiV3RequestRepository requests;
    @Inject private ApiV3SecurityProofService proofs;
    @Inject private GameWalletService wallet;

    private static ApiV3Exception conflict(String message) {
        return new ApiV3Exception(409, "partner_changed", message);
    }

    private static boolean joined(JSONObject row) { return row != null && row.getInt("joined") == 1; }
    static int quote(JSONObject row) {
        return row.getString("holder").isEmpty() ? 100 : row.getInt("price") + 20;
    }
    @Inject private PartnerOperationsService operations;
    private static JSONArray meters(JSONObject row) { return row.getJSONArray("meters"); }

    /** 同一事务共享对象，互为搭子时也只结算每个账号一次。零号位置记录本人被请的分成。 */
    private class Round {
        final Map<String, JSONObject> accounts = new LinkedHashMap<>();
        final long now = System.currentTimeMillis();
        final String reference = Ids.genTimeMillisId();

        JSONObject account(String id) throws RepositoryException {
            if (accounts.containsKey(id)) return accounts.get(id);
            var found = members.select("SELECT * FROM " + members.getName() + " WHERE oId=? FOR UPDATE", id);
            if (found.isEmpty()) return null;
            JSONObject row = found.getFirst();
            row.put("meters", new JSONArray(row.getString("meters")));
            accounts.put(id, row);
            int earnings = 0;
            for (int i = 0; i < meters(row).length(); i++)
                earnings += PartnerIncome.settle(meters(row).getJSONObject(i), now);
            wallet.change(id, earnings, "partner-income", reference, now);
            return row;
        }
        void change(JSONObject row, int amount, String operation) throws RepositoryException {
            wallet.change(row.getString("oId"), amount, operation, reference, now);
        }
        void save() throws RepositoryException {
            for (JSONObject row : accounts.values()) {
                var saved = new JSONObject(row.toString()).put("meters", meters(row).toString());
                members.update(row.getString("oId"), saved, "joined", "holder", "price", "revision", "meters");
            }
        }
    }

    private JSONObject name(String id) throws RepositoryException {
        var found = users.select("SELECT userName,userNickname FROM " + users.getName() + " WHERE oId=?", id);
        if (found.isEmpty()) return new JSONObject().put("name", "已注销鱼友");
        return new JSONObject().put("name", display(found.getFirst()));
    }
    private static String display(JSONObject row) {
        String nickname = row.optString("userNickname");
        return nickname.isBlank() ? row.getString("userName") : nickname;
    }

    private static void validId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
    }

    private JSONObject directoryCard(JSONObject row, String actor, JSONObject policy, long transfers) {
        int price = quote(row);
        return new JSONObject().put("id", row.getString("oId")).put("name", display(row))
                .put("userName", row.getString("userName")).put("avatar", ApiV3CommunityDto.safeUrl(row.optString("userAvatarURL")))
                .put("rate", PartnerOperationsService.rate(policy, Math.min(price, 200))).put("rateVersion", policy.getInt("version"))
                .put("displayValue", PartnerOperationsService.displayValue(transfers))
                .put("price", Math.min(price, 200)).put("full", price > 200).put("mine", actor.equals(row.getString("holder")))
                .put("following", row.optInt("followed") == 1).put("revision", row.getLong("revision"));
    }

    private String directoryFrom() {
        return " FROM " + members.getName() + " p JOIN " + users.getName()
                + " u ON u.oId COLLATE utf8mb4_0900_ai_ci=p.oId WHERE p.joined=1 AND u.userStatus=0";
    }

    private static final String CARD = "SELECT p.oId,p.holder,p.price,p.revision,u.userName,u.userNickname,u.userAvatarURL";

    public JSONObject card(JSONObject identity, String peer) throws Exception {
        validId(peer);
        Transaction tx = PartnerTransactions.begin(requests::beginTransaction);
        try {
            String actor = proofs.authorizePartnerInCurrentTransaction(identity, "profile.read").getString("oId");
            var rows = members.select(CARD + directoryFrom() + " AND p.oId=?", peer);
            var result = new JSONObject().put("card", rows.isEmpty() ? JSONObject.NULL : directoryCard(rows.getFirst(), actor, operations.policy(false),
                    operations.effectiveTransfers(java.util.List.of(peer)).getOrDefault(peer, 0L)));
            tx.commit();
            return result;
        } finally { if (tx.isActive()) tx.rollback(); }
    }

    public JSONObject activity(JSONObject identity, String kind, String before) throws Exception {
        if (!"events".equals(kind) && !"wallet".equals(kind)) throw ApiV3WriteInputs.invalid();
        if (before != null && !before.matches("[0-9]{1,32}")) throw ApiV3WriteInputs.invalid();
        Transaction tx = PartnerTransactions.begin(requests::beginTransaction);
        try {
            String actor = proofs.authorizePartnerInCurrentTransaction(identity, "profile.read").getString("oId");
            String cursor = before == null ? "" : " AND oId<?";
            Object[] params = before == null ? new Object[]{actor} : new Object[]{actor, before};
            String tail = cursor + " ORDER BY oId DESC LIMIT 21";
            var rows = "events".equals(kind)
                    ? events.select("SELECT oId,createdAt,summary,price,amount FROM " + events.getName() + " WHERE userId=?" + tail, params)
                    : ledger.select("SELECT oId,createdAt,operation,coinDelta AS amount FROM " + ledger.getName()
                            + " WHERE userId=? AND operation LIKE 'partner-%'" + tail, params);
            var result = new JSONObject().put("list", new JSONArray(rows.stream().limit(20).toList()))
                    .put("next", rows.size() > 20 ? rows.get(19).getString("oId") : "");
            tx.commit();
            return result;
        } finally { if (tx.isActive()) tx.rollback(); }
    }

    private void event(String viewer, String summary, int price, int amount, long now) throws RepositoryException {
        events.add(new JSONObject().put("oId", Ids.genTimeMillisId()).put("userId", viewer).put("createdAt", now)
                .put("summary", summary).put("price", price).put("amount", amount));
    }

    public JSONObject state(JSONObject identity, String after, String sort, String peer) throws Exception {
        boolean social = "following".equals(sort);
        if (sort != null && !social) throw ApiV3WriteInputs.invalid();
        if (peer != null) validId(peer);
        if (after != null && (!after.matches(social ? "[01]:[1-9][0-9]{0,18}" : "[1-9][0-9]{0,18}") || peer != null))
            throw ApiV3WriteInputs.invalid();
        Transaction tx = PartnerTransactions.begin(requests::beginTransaction);
        try {
            String actor = proofs.authorizePartnerInCurrentTransaction(identity, "profile.read").getString("oId");
            Round round = new Round();
            JSONObject own = round.account(actor);
            JSONArray slots = new JSONArray(), list = new JSONArray();
            JSONObject result = new JSONObject().put("joined", joined(own)).put("revision", own == null ? 0 : own.getLong("revision"))
                    .put("holderName", "").put("slots", slots).put("list", list);
            long today = 0;
            if (own != null) {
                if (!own.getString("holder").isEmpty()) result.put("holderName", name(own.getString("holder")).getString("name"));
                for (int i = 0; i < meters(own).length(); i++) {
                    JSONObject m = meters(own).getJSONObject(i);
                    today += m.getLong("today");
                    if (i == 0) continue;
                    String slotPeer = m.getString("peer");
                    JSONObject slot = new JSONObject().put("peer", slotPeer).put("rate", m.getInt("rate"))
                            .put("today", m.getLong("today")).put("usedMs", m.getLong("used"));
                    if (!slotPeer.isEmpty()) slot.put("name", name(slotPeer).getString("name"));
                    slots.put(slot);
                }
                round.save();
            }
            // 旧客户端继续按用户编号翻页；关注优先使用“关注分组、用户编号”双字段游标。
            String sql = CARD + (social ? ",EXISTS(SELECT 1 FROM " + follows.getName()
                    + " f WHERE f.followerId=? AND f.followingType=0 AND f.followingId COLLATE utf8mb4_0900_ai_ci=p.oId) AS followed" : "")
                    + directoryFrom() + " AND p.oId<>?";
            var params = new java.util.ArrayList<Object>();
            if (social) params.add(actor);
            params.add(actor);
            if (peer != null) {
                sql += " AND p.oId=?";
                params.add(peer);
            } else if (after != null && social) {
                sql += " HAVING followed<? OR (followed=? AND p.oId>?)";
                int section = after.charAt(0) - '0';
                params.add(section);
                params.add(section);
                params.add(after.substring(2));
            } else if (after != null) {
                sql += " AND p.oId>?";
                params.add(after);
            }
            var rows = members.select(sql + (social ? " ORDER BY followed DESC,p.oId" : " ORDER BY p.oId") + " LIMIT 21", params.toArray());
            JSONObject policy = operations.policy(false);
            var page = rows.stream().limit(20).toList();
            var peers = new java.util.ArrayList<String>();
            for (var row : page) peers.add(row.getString("oId"));
            if (own != null) peers.add(actor);
            var counts = operations.effectiveTransfers(peers);
            if (own != null) result.put("displayValue", PartnerOperationsService.displayValue(counts.getOrDefault(actor, 0L)));
            for (var row : page) {
                list.put(directoryCard(row, actor, policy, counts.getOrDefault(row.getString("oId"), 0L)));
            }
            Long coins = wallet.balance(actor);
            String next = "";
            if (rows.size() > 20) {
                JSONObject last = rows.get(19);
                next = (social ? last.getInt("followed") + ":" : "") + last.getString("oId");
            }
            result.put("coins", coins == null ? 0 : coins).put("today", today).put("next", next);
            tx.commit();
            return result;
        } finally { if (tx.isActive()) tx.rollback(); }
    }

    public JSONObject command(JSONObject identity, String action, String key, JSONObject input) throws Exception {
        Set<String> fields = switch (action) {
            case "join" -> Set.of();
            case "hire" -> input.has("rateVersion") ? Set.of("peer", "revision", "rateVersion") : Set.of("peer", "revision");
            case "release" -> Set.of("peer", "revision");
            case "leave", "unlock" -> Set.of("revision");
            default -> throw ApiV3Requests.notFound();
        };
        if (!input.keySet().equals(fields)) throw ApiV3WriteInputs.invalid();
        if (fields.contains("peer") && !ApiV3WriteInputs.string(input, "peer", 19, false).matches("[1-9][0-9]{0,18}"))
            throw ApiV3WriteInputs.invalid();
        if (fields.contains("rateVersion")) ApiV3WriteInputs.integer(input, "rateVersion", -1, 1, Integer.MAX_VALUE);
        if (fields.contains("revision")) ApiV3WriteInputs.integer(input, "revision", -1, 0, Integer.MAX_VALUE);
        if (identity == null || identity.optJSONObject("user") == null) throw ApiV3Requests.forbidden();
        String actor = identity.getJSONObject("user").getString("oId");
        var guarded = new ApiV3MutationStore() {
            public Transaction beginTransaction() { return PartnerTransactions.begin(requests::beginTransaction); }
            public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
            public JSONObject getForUpdate(String id) throws RepositoryException {
                proofs.authorizePartnerInCurrentTransaction(identity, "settings.write");
                return requests.getForUpdate(id);
            }
            public String add(JSONObject row) throws RepositoryException { return requests.add(row); }
            public void update(String id, JSONObject row, String... fields) throws RepositoryException { requests.update(id, row, fields); }
        };
        return ApiV3IdempotencyMgmtService.runAtomicWithOutcome(guarded, actor, "partner." + action, key, input,
                () -> apply(actor, action, input)).result();
    }

    private JSONObject apply(String actor, String action, JSONObject input) throws Exception {
        Round r = new Round();
        JSONObject own = r.account(actor);
        if (action.equals("join")) {
            if (joined(own)) throw conflict("你已经加入搭子社");
            if (own == null) {
                wallet.open(actor);
                var initial = new JSONArray().put(PartnerIncome.meter(r.now)).put(PartnerIncome.meter(r.now));
                members.add(new JSONObject().put("oId",actor).put("joined",1).put("holder","")
                        .put("price",0).put("revision",1).put("meters",initial.toString()));
            } else {
                own.put("joined", 1);
                bump(own);
            }
        } else {
            if (!joined(own)) throw conflict("请先加入搭子社");
            if (!action.equals("hire") && own.getLong("revision") != input.getLong("revision"))
                throw conflict("搭子状态已经变化，请重新确认");
            switch (action) {
                case "unlock" -> {
                    int positions = meters(own).length() - 1;
                    if (positions >= 3) throw conflict("搭子位已全部开启");
                    r.change(own, positions == 1 ? -600 : -1600, "partner-slot");
                    meters(own).put(PartnerIncome.meter(r.now));
                    bump(own);
                }
                case "hire" -> hire(r, own, input);
                case "release" -> {
                    JSONObject peer = r.account(input.getString("peer"));
                    if (!joined(peer) || !actor.equals(peer.getString("holder"))) throw conflict("这位搭子已经离开");
                    release(r, own, peer, false);
                }
                case "leave" -> {
                    if (!own.getString("holder").isEmpty()) release(r, r.account(own.getString("holder")), own, true);
                    for (int i = 1; i < meters(own).length(); i++) {
                        String peer = meters(own).getJSONObject(i).getString("peer");
                        if (!peer.isEmpty()) release(r, own, r.account(peer), true);
                    }
                    own.put("joined", 0);
                    bump(own);
                }
                default -> throw ApiV3Requests.notFound();
            }
        }
        r.save();
        return new JSONObject().put("action", action);
    }

    private static void bump(JSONObject row) { row.put("revision", row.getLong("revision") + 1); }
    private static JSONObject slot(JSONObject owner, String peer) {
        JSONObject best = null;
        for (int i = 1; i < meters(owner).length(); i++) {
            JSONObject slot = meters(owner).getJSONObject(i);
            if (!peer.equals(slot.getString("peer"))) continue;
            if (!peer.isEmpty()) return slot;
            // 优先使用今日剩余时间最多的空位，避免已满八小时的位置遮住可营业的位置。
            if (best == null || slot.getLong("used") < best.getLong("used")) best = slot;
        }
        if (best != null) return best;
        throw conflict(peer.isEmpty() ? "请先腾出一个搭子位" : "搭子位状态已变化");
    }
    private void release(Round r, JSONObject owner, JSONObject peer, boolean leaving) throws Exception {
        String ownerId = owner.getString("oId"), peerId = peer.getString("oId");
        end(r, owner, peer, 100);
        String reason = leaving ? "因退出搭子社而结束" : "已结束";
        event(ownerId, "你与" + name(peerId).getString("name") + "的搭班" + reason + "，退回保证金", 0, 100, r.now);
        event(peerId, "你与" + name(ownerId).getString("name") + "的搭班" + reason, 0, 0, r.now);
    }

    private void end(Round r, JSONObject owner, JSONObject peer, int refund) throws Exception {
        slot(owner, peer.getString("oId")).put("peer", "").put("rate", 0);
        meters(peer).getJSONObject(0).put("rate", 0);
        peer.put("holder", "").put("price", 0);
        r.change(owner, refund, "partner-return");
        bump(owner);
        bump(peer);
    }
    private void hire(Round r, JSONObject own, JSONObject input) throws Exception {
        String id = input.getString("peer"), actor = own.getString("oId");
        if (actor.equals(id)) throw conflict("不能请自己当搭子");
        JSONObject peer = r.account(id);
        if (!joined(peer) || peer.getLong("revision") != input.getLong("revision")
                || actor.equals(peer.getString("holder"))) throw conflict("这张名片已变化，请查看最新请人费用");
        if (users.select("SELECT oId FROM " + users.getName() + " WHERE oId=? AND userStatus=0", id).isEmpty())
            throw conflict("这位鱼友目前不可邀请");
        int price = quote(peer);
        if (price > 200) throw conflict("这位搭子已满价");
        JSONObject policy = operations.hirePolicy();
        int hourly = PartnerOperationsService.rate(policy, price);
        // 旧客户端展示固定费率，只在它展示的报价仍然准确时允许成交。
        if (input.has("rateVersion") ? input.getInt("rateVersion") != policy.getInt("version") : hourly != 40 + (price - 100) / 10)
            throw conflict("营业费率已更新，请刷新名片后重新确认");
        JSONObject free = slot(own, "");
        r.change(own, -price, "partner-hire");
        String previous = peer.getString("holder");
        String buyerName = name(actor).getString("name"), peerName = name(id).getString("name");
        if (!previous.isEmpty()) {
            String previousName = name(previous).getString("name");
            event(actor, "你从" + previousName + "手中请到了" + peerName, price, -price, r.now);
            event(id, buyerName + "从" + previousName + "手中请走了你，获得分成", price, 4, r.now);
            event(previous, buyerName + "请走了你的搭子" + peerName + "，退回本金 " + (price - 20) + "，赚取差价 14", price, price - 6, r.now);
            // 旧邀请人的成交本金及差价分成由新邀请人付款，名片只保留基础保证金。
            end(r, r.account(peer.getString("holder")), peer, price - 20 + 14);
            r.change(peer, 4, "partner-share");
        } else {
            event(actor, "你请到了" + peerName, price, -price, r.now);
            event(id, buyerName + "请你搭班", price, 0, r.now);
        }
        operations.trade(r.reference, actor, previous, id, r.now);
        free.put("peer", id).put("rate", hourly);
        peer.put("price", price).put("holder", actor);
        meters(peer).getJSONObject(0).put("rate", 2);
        bump(own);
        bump(peer);
    }
}
