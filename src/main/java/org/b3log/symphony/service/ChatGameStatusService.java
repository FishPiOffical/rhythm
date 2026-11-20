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
        final var ids = ids(rawIds);
        final var tx = PartnerTransactions.begin(requests::beginTransaction);
        try {
            // 复用游戏名片的第一方权限；仅从公开状态投影，不触发停车访问或搭班结算。
            proofs.authorizePartnerInCurrentTransaction(identity, "profile.read");
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
