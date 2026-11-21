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

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.repository.ParkingObservationRepository;
import org.b3log.symphony.repository.ParkingSessionRepository;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;

@Service
public class ParkingMetricsService {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    @Inject private ParkingObservationRepository observations;
    @Inject private ParkingSessionRepository sessions;
    @Inject private ApiV3AdminService admin;

    private static long day(long now) {
        return Instant.ofEpochMilli(now).atZone(BEIJING).toLocalDate().toEpochDay();
    }

    private String table() {
        return "`" + observations.getName() + "`";
    }

    private void observe(String actor, String kind, long now, boolean daily, int outcome, String hash) throws RepositoryException {
        final long day = day(now);
        final String id = actor + ":" + kind + (daily ? ":" + day : "");
        final String insert = "INSERT INTO " + table()
                + " (oId,actorId,kind,observedAt,dayNo,outcome,commandHash) VALUES(?,?,?,?,?,?,?)";
        if ("firstPark".equals(kind)) {
            // 失败的事务回滚后才记录；并发时按到达时间取首笔，同一命令恢复成功只更新结果。
            ApiV3TransactionStatements.execute(insert + " ON DUPLICATE KEY UPDATE"
                    + " outcome=IF(commandHash=?,GREATEST(outcome,?),IF(?<observedAt,?,outcome)),"
                    + " commandHash=IF(?<observedAt,?,commandHash),dayNo=IF(?<observedAt,?,dayNo),"
                    + " observedAt=LEAST(observedAt,?)",
                    id, actor, kind, now, day, outcome, hash, hash, outcome, now, outcome, now, hash, now, day, now);
        } else {
            ApiV3TransactionStatements.execute(insert + " ON DUPLICATE KEY UPDATE oId=oId",
                    id, actor, kind, now, day, outcome, hash);
        }
    }

    void milestone(String actor, String kind, long now) throws RepositoryException {
        observe(actor, kind, now, false, 1, "");
    }

    void ticket(String driver, long now) throws RepositoryException {
        observe(driver, "ticket", now, true, 1, "");
    }

    void firstPark(String actor, long now, String hash, boolean success) throws RepositoryException {
        observe(actor, "firstPark", now, false, success ? 1 : 0, hash);
    }

    void rejectedPark(String actor, long attemptedAt, String hash) {
        // 原停车事务已经回滚；记录业务拒绝不能改变玩家收到的错误。
        try {
            final var tx = observations.beginTransaction();
            try {
                firstPark(actor, attemptedAt, hash, false);
                tx.commit();
            } finally {
                if (tx.isActive()) tx.rollback();
            }
        } catch (Exception e) {
            LogManager.getLogger(ParkingMetricsService.class).warn("停车失败样本未记录", e);
        }
    }

    void visit(String actor, long now, boolean eligible, boolean following, boolean empty) throws RepositoryException {
        observe(actor, "visit", now, true, 1, "");
        if (eligible) observe(actor, following ? "findFollowing" : "findAll", now, true, empty ? 1 : 0, "");
    }

    private JSONObject summary(String query, Object... args) throws RepositoryException {
        return observations.select(query, args).getFirst();
    }

    public JSONObject report(JSONObject identity) throws RepositoryException {
        final var tx = admin.begin();
        try {
            admin.authorizeInCurrentTransaction(identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN, true);
            final long now = System.currentTimeMillis();
            final var today = Instant.ofEpochMilli(now).atZone(BEIJING).toLocalDate();
            final long start = today.minusDays(6).atStartOfDay(BEIJING).toInstant().toEpochMilli();
            final String records = table();
            final String newDrivers = " JOIN " + records + " e ON e.oId=CONCAT(v.actorId,':starter') AND e.observedAt<=v.observedAt ";
            final var first = summary("SELECT COUNT(*) AS samples,COALESCE(SUM(v.outcome),0) AS hits FROM "
                    + records + " v" + newDrivers + "WHERE v.kind='firstPark' AND v.observedAt BETWEEN ? AND ?", start, now);
            final var empty = summary("SELECT COUNT(*) AS samples,COALESCE(SUM(outcome),0) AS hits FROM "
                    + records + " WHERE kind IN ('findAll','findFollowing') AND observedAt BETWEEN ? AND ?", start, now);
            final var upgrade = summary("SELECT COUNT(*) AS samples,AVG(v.observedAt-e.observedAt) AS meanMs FROM "
                    + records + " v" + newDrivers + "WHERE v.kind='upgrade' AND v.observedAt BETWEEN ? AND ?", start, now);
            // 必须完整经过次日，才将该车主当天被贴条的记录计入分母。
            final var returns = summary("SELECT COUNT(*) AS samples,COUNT(r.oId) AS hits FROM " + records + " t LEFT JOIN "
                    + records + " r ON r.oId=CONCAT(t.actorId,':visit:',t.dayNo+1)"
                    + " WHERE t.kind='ticket' AND t.observedAt>=? AND t.dayNo<=?", start, today.toEpochDay()-2);
            final var pairs = summary("SELECT COALESCE(SUM(n),0) AS samples,"
                    + "COALESCE(SUM(IF(outbound>=2 AND n-outbound>=2,n,0)),0) AS hits FROM ("
                    + "SELECT COUNT(*) AS n,SUM(driverId<hostId) AS outbound FROM `" + sessions.getName()
                    + "` WHERE startedAt BETWEEN ? AND ? GROUP BY LEAST(driverId,hostId),GREATEST(driverId,hostId)) pairsInWindow", start, now);
            if (upgrade.isNull("meanMs")) upgrade.put("meanMs", JSONObject.NULL);
            final var report = new JSONObject().put("from",start).put("asOf",now).put("firstPark",first)
                    .put("emptySearch",empty).put("upgrade",upgrade).put("nextDay",returns).put("repeatPairs",pairs);
            tx.commit();
            return report;
        } finally {
            ApiV3AdminService.rollbackIfActive(tx);
        }
    }
}
