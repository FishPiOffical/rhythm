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

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.BagMutation;
import org.json.JSONObject;
import java.util.List;

/** All bag writers share the owner row lock, including when the bag does not exist yet. */
@Service
public class BagMgmtService {
    @Inject private CloudRepository cloudRepository;
    @Inject private UserRepository userRepository;
    @FunctionalInterface private interface Operation<T> { T run() throws RepositoryException; }
    private <T> T atomic(final Operation<T> operation) throws RepositoryException {
        if (cloudRepository.hasTransactionBegun()) throw new IllegalStateException("use bag current-transaction primitive");
        final Transaction tx = cloudRepository.beginTransaction();
        try { final T result = operation.run(); tx.commit(); return result; }
        catch (final RepositoryException | RuntimeException e) { if (tx.isActive()) tx.rollback(); throw e; }
    }
    public JSONObject read(final String actor) throws RepositoryException {
        final List<JSONObject> rows = cloudRepository.getBagRows(actor, false);
        return rows.isEmpty() ? new JSONObject() : new JSONObject(rows.getFirst().getString("data"));
    }
    private List<JSONObject> lock(final String actor) throws RepositoryException {
        if (!cloudRepository.hasTransactionBegun()) throw new IllegalStateException("bag transaction required");
        if (null == userRepository.getForUpdate(actor)) throw new RepositoryException("bag owner not found");
        return cloudRepository.getBagRows(actor, true);
    }
    private static JSONObject contents(final List<JSONObject> rows) {
        return rows.isEmpty() ? new JSONObject() : new JSONObject(rows.getFirst().getString("data"));
    }
    private void persist(final String actor, final List<JSONObject> rows, final JSONObject bag) throws RepositoryException {
        final JSONObject record = new JSONObject().put("userId", actor).put("gameId", BagMutation.GAME_ID).put("data", bag.toString());
        if (rows.isEmpty()) cloudRepository.add(record);
        else {
            cloudRepository.update(rows.getFirst().getString(Keys.OBJECT_ID), record);
            for (int i = 1; i < rows.size(); i++) cloudRepository.remove(rows.get(i).getString(Keys.OBJECT_ID));
        }
    }
    public void save(final String actor, final JSONObject bag) throws RepositoryException {
        atomic(() -> { final var rows = lock(actor); persist(actor, rows, bag); return null; });
    }
    public int adjust(final String actor, final String item, final int number, final int capacity) throws RepositoryException {
        return atomic(() -> {
            final var rows = lock(actor);
            final var result = BagMutation.adjust(contents(rows), item, number, capacity);
            if (result.changed()) persist(actor, rows, result.bag());
            return result.status();
        });
    }
    public void remove(final String actor, final String item) throws RepositoryException {
        atomic(() -> {
            final var rows = lock(actor); final JSONObject bag = contents(rows);
            if (bag.has(item)) { bag.remove(item); persist(actor, rows, bag); }
            return null;
        });
    }
    public JSONObject useExemption(final String actor, final String item) throws RepositoryException {
        return atomic(() -> useExemptionInCurrentTransaction(actor, item));
    }
    public JSONObject useExemptionInCurrentTransaction(final String actor, final String item) throws RepositoryException {
        final var rows = lock(actor);
        final JSONObject bag = BagMutation.useExemption(contents(rows), item);
        persist(actor, rows, bag);
        return bag;
    }

    /** Caller-owned transaction; retains the owner-before-bag lock order for compound mutations. */
    public JSONObject mutateInCurrentTransaction(final String actor, final java.util.function.UnaryOperator<JSONObject> mutation) throws RepositoryException {
        final var rows = lock(actor);
        final JSONObject bag = java.util.Objects.requireNonNull(mutation.apply(contents(rows)));
        persist(actor, rows, bag);
        return bag;
    }
}
