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
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.repository.PetCoffeeRepository;
import org.b3log.symphony.repository.PointtransferRedcRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3PetRules;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.Callable;

@Service
public class ApiV3PetCoffee {
    public static final int ENERGY = 30;
    public static final int DAILY_LIMIT = 3;
    public static final int MAX_PRICE = 100_000;
    private static final String SETTINGS = "settings";
    @Inject private PetCoffeeRepository repository;
    @Inject private UserRepository users;
    @Inject private PointtransferMgmtService points;
    @Inject private PointtransferRedcRepository transferIndex;

    record Offer(boolean enabled, int price, int version) {
        JSONObject json() {
            return new JSONObject().put("product", "coffee").put("enabled", enabled).put("price", price).put("version", version);
        }
    }

    record Usage(String date, int used) { }

    private JSONObject snapshot(final String id, final boolean lock) throws RepositoryException {
        final var rows = repository.select("SELECT snapshotJson FROM `" + repository.getName() + "` WHERE oId=?"
                + (lock ? " FOR UPDATE" : ""), id);
        return rows.isEmpty() ? null : new JSONObject(rows.getFirst().getString("snapshotJson"));
    }

    Offer offer(final boolean lock) throws RepositoryException {
        final JSONObject row = snapshot(SETTINGS, lock);
        if (row == null || !(row.opt("enabled") instanceof Boolean) || row.optInt("price", 0) < 1
                || row.optInt("price") > MAX_PRICE || row.optInt("version", 0) < 1) throw unavailable();
        return new Offer(row.getBoolean("enabled"), row.getInt("price"), row.getInt("version"));
    }

    void saveOffer(final Offer value) throws RepositoryException {
        save(SETTINGS, value.json(), true);
    }

    Usage usage(final String owner, final boolean lock) throws RepositoryException {
        final JSONObject row = snapshot(owner, lock);
        if (row == null) return null;
        final Usage usage = new Usage(row.getString("date"), row.getInt("used"));
        if (usage.used() < 1 || usage.used() > DAILY_LIMIT) throw unavailable();
        return usage;
    }

    int usedToday(final Usage usage, final String today) {
        return usage == null || today.compareTo(usage.date()) > 0 ? 0 : usage.used();
    }

    void recordUse(final String owner, final Usage previous, final String today, final int used) throws RepositoryException {
        save(owner, new JSONObject().put("date", today).put("used", used + 1), previous != null);
    }

    private void save(final String id, final JSONObject snapshot, final boolean exists) throws RepositoryException {
        final JSONObject row = new JSONObject().put("oId", id).put("snapshotJson", snapshot.toString());
        if (exists) repository.update(id, row, "snapshotJson");
        else repository.add(row);
    }

    JSONObject withPointLocks(final String owner, final Callable<JSONObject> operation) throws Exception {
        return points.executeWithTransferLocks(owner, List.of(Pointtransfer.ID_C_SYS), operation);
    }

    int balance(final String owner, final boolean lock) throws RepositoryException {
        final var rows = users.select("SELECT userPoint FROM `" + users.getName() + "` WHERE oId=?"
                + (lock ? " FOR UPDATE" : ""), owner);
        if (rows.size() != 1) throw unavailable();
        return rows.getFirst().getInt("userPoint");
    }

    void charge(final String owner, final int price, final long now) throws RepositoryException {
        if (balance(owner, true) < price) throw ApiV3PetRules.conflict("insufficient_points", "积分余额不足");
        final String transfer = points.transferInCurrentTransaction(owner, Pointtransfer.ID_C_SYS,
                Pointtransfer.TRANSFER_TYPE_C_PET_COFFEE, price, "宠物咖啡", now, "",
                new PointtransferSource("", "", "pet.coffee", Ids.genTimeMillisId()), false);
        if (transfer == null) throw unavailable();
        for (String user : List.of(owner, Pointtransfer.ID_C_SYS)) {
            transferIndex.add(new JSONObject().put("oId", Ids.genTimeMillisId()).put("userId", user).put("rawId", transfer));
        }
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "宠物咖啡暂不可用");
    }
}
