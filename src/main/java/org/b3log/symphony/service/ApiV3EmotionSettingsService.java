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
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.EmotionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3EmotionSettingsInputs;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.util.ApiV3Transactions;
import org.json.JSONObject;

/** Replaces only the authenticated owner's type-zero picker selection and the receipt atomically. */
@Service
public class ApiV3EmotionSettingsService {
    @Inject private UserRepository users;
    @Inject private EmotionRepository emotions;
    @Inject private ApiV3RequestRepository requests;
    public JSONObject update(final String actor, final JSONObject body, final String key) throws Exception {
        id(actor);
        final JSONObject normalized = ApiV3EmotionSettingsInputs.normalize(body);
        try {
            final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                public Transaction beginTransaction() { return ApiV3Transactions.strict(requests.beginTransaction()); }
                public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
                public JSONObject getForUpdate(final String receipt) throws RepositoryException { owner(actor); return requests.getForUpdate(receipt); }
                public String add(final JSONObject row) throws RepositoryException { return requests.add(row); }
                public void update(final String receipt, final JSONObject row, final String... fields) throws RepositoryException { requests.update(receipt, row, fields); }
            };
            // Project stored results too: internal receipt objects are never an unrestricted response.
            final JSONObject result = ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "settings.emotions", key, normalized,
                    () -> updateInCurrentTransaction(actor, normalized));
            return ApiV3EmotionSettingsInputs.effective(ApiV3EmotionSettingsInputs.normalize(
                    new JSONObject().put("emotions", result.getJSONArray("emotions"))));
        } catch (final ApiV3Exception e) { throw e; }
        catch (final Exception e) { throw unavailable(); }
    }
    public JSONObject updateInCurrentTransaction(final String actor, final JSONObject body) throws Exception {
        id(actor);
        final JSONObject normalized = ApiV3EmotionSettingsInputs.normalize(body);
        if (!requests.hasTransactionBegun()) throw new IllegalStateException("emotion settings transaction required");
        return ApiV3TransactionSavepoint.run(() -> {
            owner(actor);
            ApiV3TransactionStatements.execute("DELETE FROM `" + emotions.getName() + "` WHERE emotionUserId=? AND emotionType=0", actor);
            final var aliases = normalized.getJSONArray("emotions");
            for (int i = 0; i < aliases.length(); i++) {
                final int changed = ApiV3TransactionStatements.execute("INSERT INTO `" + emotions.getName()
                        + "` (oId,emotionUserId,emotionContent,emotionSort,emotionType) VALUES(?,?,?,?,0)",
                        Ids.genTimeMillisId(), actor, aliases.getString(i), i);
                if (changed != 1) throw unavailable();
            }
            return ApiV3EmotionSettingsInputs.effective(normalized);
        });
    }
    private void owner(final String actor) throws RepositoryException {
        final var rows = users.select("SELECT oId,userStatus FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.size() != 1 || !actor.equals(rows.getFirst().optString("oId"))
                || !(rows.getFirst().opt("userStatus") instanceof Number status) || status.intValue() != UserExt.USER_STATUS_C_VALID
                || status.doubleValue() != UserExt.USER_STATUS_C_VALID) throw ApiV3Requests.notFound();
    }
    private static void id(final String actor) { if (actor == null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid(); }
    private static ApiV3Exception unavailable() { return new ApiV3Exception(503,"temporarily_unavailable","表情设置暂不可用"); }
}
