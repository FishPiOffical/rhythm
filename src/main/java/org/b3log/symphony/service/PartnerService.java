package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.PartnerMemberRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class PartnerService {
    @Inject private PartnerMemberRepository members;
    @Inject private UserRepository users;
    @Inject private ApiV3RequestRepository requests;
    @Inject private ApiV3SecurityProofService proofs;
    @Inject private GameWalletService wallet;

    private static ApiV3Exception conflict(String message) {
        return new ApiV3Exception(409, "partner_changed", message);
    }

    private static boolean joined(JSONObject row) { return row != null && row.getInt("joined") == 1; }
    private static int quote(JSONObject row) {
        return row.getString("holder").isEmpty() ? 100 : row.getInt("price") + 20;
    }
    private static int rate(int price) { return 40 + (price - 100) / 10; }
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

    public JSONObject state(JSONObject identity, String after) throws Exception {
        if (after != null && !after.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
        Transaction tx = requests.beginTransaction();
        try {
            String actor = proofs.authorizeFirstPartyInCurrentTransaction(identity, "profile.read").getString("oId");
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
                    String peer = m.getString("peer");
                    JSONObject slot = new JSONObject().put("peer", peer).put("rate", m.getInt("rate"))
                            .put("today", m.getLong("today")).put("usedMs", m.getLong("used"));
                    if (!peer.isEmpty()) slot.put("name", name(peer).getString("name"));
                    slots.put(slot);
                }
                round.save();
            }
            var rows = members.select("SELECT p.oId,p.holder,p.price,p.revision,u.userName,u.userNickname,u.userAvatarURL FROM "
                    + members.getName() + " p JOIN " + users.getName() + " u ON u.oId COLLATE utf8mb4_0900_ai_ci=p.oId"
                    + " WHERE p.joined=1 AND p.oId<>? AND p.oId>? AND u.userStatus=0 ORDER BY p.oId LIMIT 21",
                    actor, after == null ? "" : after);
            for (var row : rows.stream().limit(20).toList()) {
                int price = quote(row);
                list.put(new JSONObject().put("id", row.getString("oId")).put("name", display(row))
                        .put("userName", row.getString("userName")).put("avatar", ApiV3CommunityDto.safeUrl(row.optString("userAvatarURL")))
                        .put("price", Math.min(price, 200)).put("full", price > 200).put("mine", actor.equals(row.getString("holder")))
                        .put("revision", row.getLong("revision")));
            }
            Long coins = wallet.balance(actor);
            result.put("coins", coins == null ? 0 : coins).put("today", today)
                    .put("next", rows.size() > 20 ? rows.get(19).getString("oId") : "");
            tx.commit();
            return result;
        } finally { if (tx.isActive()) tx.rollback(); }
    }

    public JSONObject command(JSONObject identity, String action, String key, JSONObject input) throws Exception {
        Set<String> fields = switch (action) {
            case "join" -> Set.of();
            case "hire", "release" -> Set.of("peer", "revision");
            case "leave", "unlock" -> Set.of("revision");
            default -> throw ApiV3Requests.notFound();
        };
        if (!input.keySet().equals(fields)) throw ApiV3WriteInputs.invalid();
        if (fields.contains("peer") && !ApiV3WriteInputs.string(input, "peer", 19, false).matches("[1-9][0-9]{0,18}"))
            throw ApiV3WriteInputs.invalid();
        if (fields.contains("revision")) ApiV3WriteInputs.integer(input, "revision", -1, 0, Integer.MAX_VALUE);
        if (identity == null || identity.optJSONObject("user") == null) throw ApiV3Requests.forbidden();
        String actor = identity.getJSONObject("user").getString("oId");
        var guarded = new ApiV3MutationStore() {
            public Transaction beginTransaction() { return requests.beginTransaction(); }
            public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
            public JSONObject getForUpdate(String id) throws RepositoryException {
                proofs.authorizeFirstPartyInCurrentTransaction(identity, "settings.write");
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
                    end(r, own, peer, 100);
                }
                case "leave" -> {
                    if (!own.getString("holder").isEmpty()) end(r, r.account(own.getString("holder")), own, 100);
                    for (int i = 1; i < meters(own).length(); i++) {
                        String peer = meters(own).getJSONObject(i).getString("peer");
                        if (!peer.isEmpty()) end(r, own, r.account(peer), 100);
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
                || actor.equals(peer.getString("holder"))) throw conflict("这张名片已变化，请查看最新身价");
        if (users.select("SELECT oId FROM " + users.getName() + " WHERE oId=? AND userStatus=0", id).isEmpty())
            throw conflict("这位鱼友目前不可邀请");
        int price = quote(peer);
        if (price > 200) throw conflict("这位搭子已满价");
        JSONObject free = slot(own, "");
        r.change(own, -price, "partner-hire");
        if (!peer.getString("holder").isEmpty()) {
            // 旧邀请人的成交本金及差价分成由新邀请人付款，名片只保留基础保证金。
            end(r, r.account(peer.getString("holder")), peer, price - 20 + 14);
            r.change(peer, 4, "partner-share");
        }
        free.put("peer", id).put("rate", rate(price));
        peer.put("price", price).put("holder", actor);
        meters(peer).getJSONObject(0).put("rate", 2);
        bump(own);
        bump(peer);
    }
}
