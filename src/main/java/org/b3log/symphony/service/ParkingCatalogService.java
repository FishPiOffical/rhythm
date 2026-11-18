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

import org.b3log.latke.http.Request;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Operation;
import org.b3log.symphony.model.Permission;
import static org.b3log.symphony.util.ParkingRules.*;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** 车行销售设置与停车收支查询。 */
@Service
public class ParkingCatalogService {
    @Inject private ApiV3AdminService admin;
    @Inject private org.b3log.symphony.repository.ParkingCatalogRepository catalog;
    @Inject private org.b3log.symphony.repository.GameWalletLedgerRepository ledger;

    private JSONObject config(boolean lock) throws RepositoryException {
        final var rows = catalog.select("SELECT settings,version FROM `" + catalog.getName()
                + "` WHERE oId='catalog'" + (lock ? " FOR UPDATE" : ""));
        if (rows.isEmpty()) throw new ApiV3Exception(503, "parking_migration_required", "请先完成车行配置迁移");
        return rows.getFirst();
    }

    private static JSONObject offer(final Car car, final JSONObject overrides) {
        final var settings = overrides.optJSONObject(car.id());
        return new JSONObject().put("id", car.id()).put("name", car.name()).put("starter", car.starter())
                .put("price", settings == null ? car.price() : settings.getInt("price"))
                .put("rate", settings == null ? car.rate() : settings.getInt("rate"))
                .put("enabled", settings == null || settings.getBoolean("enabled"));
    }

    JSONObject offer(String id) throws RepositoryException {
        return offer(car(id), new JSONObject(config(false).getString("settings")));
    }

    JSONObject models() throws RepositoryException {
        final var active = config(false);
        final var overrides = new JSONObject(active.getString("settings"));
        final var list = new JSONArray();
        for (final Car car : CARS) list.put(offer(car, overrides));
        return new JSONObject().put("models", list).put("version", active.getInt("version"));
    }

    public JSONObject read(final JSONObject identity, final boolean transactions, final String uid,
                           final int page, final int size) throws RepositoryException {
        final var tx = admin.begin();
        try {
            admin.authorizeInCurrentTransaction(identity, "profile.read", Permission.PERMISSION_ID_C_MENU_ADMIN, true);
            final JSONObject result;
            if (transactions) {
                final var args = new ArrayList<Object>();
                String from = " FROM `" + ledger.getName() + "` WHERE operation IN ('parking-income','parking-buy')";
                if (uid != null && !uid.isEmpty()) {
                    if (!uid.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
                    from += " AND userId=?";
                    args.add(uid);
                }
                final long count = ledger.select("SELECT COUNT(*) AS n" + from,args.toArray()).getFirst().getLong("n");
                args.add(size);
                args.add((page - 1L) * size);
                final var items = ledger.select("SELECT oId,userId,operation,referenceId,coinDelta,createdAt" + from
                        + " ORDER BY createdAt DESC,oId DESC LIMIT ? OFFSET ?", args.toArray());
                result = ApiV3Requests.pagination(new JSONArray(items),page,size,count);
            } else result = models();
            tx.commit();
            return result;
        } finally {
            ApiV3AdminService.rollbackIfActive(tx);
        }
    }

    public JSONObject update(final JSONObject identity, final String model, final JSONObject body,
                             final String key, final Request request) throws Exception {
        final Car item = car(model);
        if (!body.keySet().equals(Set.of("price","rate","enabled","version")) || !(body.opt("enabled") instanceof Boolean enabled))
            throw ApiV3WriteInputs.invalid();
        final int price = ApiV3WriteInputs.integer(body,"price",-1,1,1_000_000);
        final int rate = ApiV3WriteInputs.integer(body,"rate",-1,1,10_000);
        final int version = ApiV3WriteInputs.integer(body,"version",-1,1,Integer.MAX_VALUE);
        final var command = new JSONObject(body.toString()).put("model",model);
        final var actor = new AtomicReference<ApiV3AdminService.AuthorizedActor>();
        return ApiV3IdempotencyMgmtService.runAtomic(admin.guardedWrite(identity,null,true,actor),
                ApiV3AdminService.actorId(identity),"admin.parking.catalog",key,command,() -> {
                    final var active = config(true);
                    if (version != active.getInt("version")) throw new ApiV3Exception(409,"parking_catalog_changed","车行配置已变更，请刷新");
                    final var overrides = new JSONObject(active.getString("settings"));
                    overrides.put(item.id(),new JSONObject().put("price",price).put("rate",rate).put("enabled",enabled));
                    if (CARS.stream().noneMatch(c -> c.starter() && offer(c,overrides).getBoolean("enabled")))
                        throw new ApiV3Exception(409,"parking_starter_required","至少保留一辆在售初始车");
                    final int next = Math.incrementExact(version);
                    catalog.update("catalog",active.put("settings",overrides.toString()).put("version",next),"settings","version");
                    admin.recordOperation(request,Operation.OPERATION_CODE_C_PARKING_SETTINGS,model,actor.get().user());
                    return new JSONObject().put("parkingModel",model).put("version",next);
                });
    }
}
