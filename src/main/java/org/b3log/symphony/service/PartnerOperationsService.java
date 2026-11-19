package org.b3log.symphony.service;

import org.b3log.latke.http.Request;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.repository.GameWalletLedgerRepository;
import org.b3log.symphony.repository.PartnerMemberRepository;
import org.b3log.symphony.repository.PartnerRateRepository;
import org.b3log.symphony.repository.PartnerTradeRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PartnerOperationsService {
    @Inject private ApiV3AdminService admin;
    @Inject private PartnerRateRepository rates;
    @Inject private PartnerTradeRepository trades;
    @Inject private PartnerMemberRepository members;
    @Inject private GameWalletLedgerRepository ledger;

    JSONObject policy(boolean lock) throws RepositoryException {
        var rows = rates.select("SELECT baseRate,stepRate,version,sinceAt FROM " + rates.getName()
                + " WHERE oId='rate'" + (lock ? " FOR UPDATE" : ""));
        if (rows.isEmpty()) throw new ApiV3Exception(503, "partner_migration_required", "搭子营业配置尚未就绪");
        return rows.getFirst();
    }

    static int rate(JSONObject policy, int price) {
        return policy.getInt("baseRate") + ((price - 100) / 20) * policy.getInt("stepRate");
    }

    void trade(String ref, String buyer, String seller, String peer, long now) throws RepositoryException {
        trades.add(new JSONObject().put("oId", ref).put("buyer", buyer).put("seller", seller)
                .put("peer", peer).put("createdAt", now));
    }

    public JSONObject report(JSONObject identity) throws RepositoryException {
        var tx = admin.begin();
        try {
            admin.authorizeInCurrentTransaction(identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN, true);
            var policy = policy(false);
            long now = System.currentTimeMillis();
            var zone = ZoneId.of("Asia/Shanghai");
            long from = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli();
            // 零号位置是本人被请的计时，只有已购买的邀请位置参与满位计算。
            var occupancy = members.select("SELECT COUNT(*) AS joinedCount,COALESCE(SUM(emptySlots=0),0) AS fullCount"
                    + " FROM (SELECT m.oId,SUM(j.peer='') AS emptySlots FROM " + members.getName()
                    + " m JOIN JSON_TABLE(m.meters,'$[*]' COLUMNS(pos FOR ORDINALITY,peer VARCHAR(19) PATH '$.peer')) j"
                    + " ON j.pos>1 WHERE m.joined=1 GROUP BY m.oId) occupied").getFirst();
            // 收入按实际入账日归属。保证金和转手本金仍属于玩家，不能算作系统增发或回收。
            var money = ledger.select("SELECT COALESCE(SUM(IF(operation='partner-income',coinDelta,0)),0) AS income,"
                    + " COUNT(DISTINCT IF(operation='partner-income',userId,NULL),FLOOR((createdAt+28800000)/86400000)) AS earningDays,"
                    + " COALESCE(SUM(CASE operation WHEN 'partner-income' THEN coinDelta WHEN 'partner-slot' THEN coinDelta"
                    + " WHEN 'partner-share' THEN -2 ELSE 0 END),0) AS netIssued FROM " + ledger.getName()
                    + " WHERE operation LIKE 'partner-%' AND createdAt BETWEEN ? AND ?", from, now).getFirst();
            // 同一名片前后两次恰好反向转手，且相隔不超过半小时；窗口前多读半小时以识别跨界样本。
            var pairs = trades.select("SELECT COUNT(*) AS transfers,COALESCE(SUM(IF(lastBuyer=seller AND lastSeller=buyer"
                    + " AND createdAt-lastAt<=1800000,1,0)),0) AS quickReturns FROM (SELECT buyer,seller,createdAt,"
                    + " LAG(buyer) OVER w AS lastBuyer,LAG(seller) OVER w AS lastSeller,LAG(createdAt) OVER w AS lastAt"
                    + " FROM " + trades.getName() + " WHERE createdAt BETWEEN ? AND ? WINDOW w AS"
                    + " (PARTITION BY peer ORDER BY createdAt,oId)) history WHERE seller<>'' AND createdAt>=?",from-1800000,now,from).getFirst();
            tx.commit();
            return new JSONObject().put("asOf",now).put("from",from).put("policy",policy)
                    .put("occupancy",occupancy).put("money",money).put("pairs",pairs);
        } finally { ApiV3AdminService.rollbackIfActive(tx); }
    }

    public JSONObject ledger(JSONObject identity, String uid, String ref, int page, int size) throws RepositoryException {
        var tx = admin.begin();
        try {
            admin.authorizeInCurrentTransaction(identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN, true);
            var args = new ArrayList<Object>();
            String from = " FROM " + ledger.getName() + " WHERE operation LIKE 'partner-%'";
            if (uid != null && !uid.isEmpty()) {
                if (!uid.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
                from += " AND userId=?"; args.add(uid);
            }
            if (ref != null && !ref.isEmpty()) {
                if (!ref.matches("[0-9]{1,32}")) throw ApiV3WriteInputs.invalid();
                from += " AND referenceId=?"; args.add(ref);
            }
            long total = ledger.select("SELECT COUNT(*) AS n" + from,args.toArray()).getFirst().getLong("n");
            args.add(size);
            args.add((page-1L)*size);
            var rows = ledger.select("SELECT oId,referenceId,operation,userId,createdAt,coinDelta" + from
                    + " ORDER BY createdAt DESC,oId DESC LIMIT ? OFFSET ?",args.toArray());
            tx.commit();
            return ApiV3Requests.pagination(new JSONArray(rows),page,size,total);
        } finally { ApiV3AdminService.rollbackIfActive(tx); }
    }

    public JSONObject update(JSONObject identity, JSONObject body, String key, Request request) throws Exception {
        if (!body.keySet().equals(java.util.Set.of("baseRate","stepRate","version"))) throw ApiV3WriteInputs.invalid();
        int base = ApiV3WriteInputs.integer(body,"baseRate",-1,1,1000);
        int step = ApiV3WriteInputs.integer(body,"stepRate",-1,0,100);
        int version = ApiV3WriteInputs.integer(body,"version",-1,1,Integer.MAX_VALUE);
        var actor = new AtomicReference<ApiV3AdminService.AuthorizedActor>();
        return ApiV3IdempotencyMgmtService.runAtomic(admin.guardedWrite(identity,null,true,actor),
                ApiV3AdminService.actorId(identity),"admin.partner.rate",key,body,() -> {
                    var active = policy(true);
                    if (version != active.getInt("version")) throw new ApiV3Exception(409,"partner_rate_changed","费率已变更，请核对最新设置");
                    int next = Math.incrementExact(version);
                    rates.update("rate",active.put("baseRate",base).put("stepRate",step).put("version",next),"baseRate","stepRate","version");
                    admin.recordOperation(request, Operation.OPERATION_CODE_C_PARTNER_RATE, "rate", actor.get().user());
                    return new JSONObject().put("partnerRateVersion",next);
                });
    }
}
