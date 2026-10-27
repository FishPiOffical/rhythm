package org.b3log.symphony.service;

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.UserProfessionPrivacyRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3ProfessionSettingsInputs;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.json.JSONObject;
import java.math.BigDecimal;

/** Independent column updates preserve primary selection and privacy across the other v3 setters. */
@Service
public class ApiV3ProfessionSettingsService {
    @Inject private UserRepository users;
    @Inject private UserProfessionPrivacyRepository privacy;
    @Inject private ApiV3RequestRepository requests;

    public JSONObject skip(final String actor, final String key) {
        return execute(actor, true, new JSONObject().put("onboardingState", "SKIPPED"), key);
    }
    public JSONObject privacy(final String actor, final JSONObject body, final String key) {
        return execute(actor, false, ApiV3ProfessionSettingsInputs.privacy(body), key);
    }
    private JSONObject execute(final String actor, final boolean skip, final JSONObject normalized, final String key) {
        id(actor);
        try {
            // Every attempt and replay locks the current account before its receipt.
            final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                public Transaction beginTransaction() { return ApiV3Transactions.strict(requests.beginTransaction()); }
                public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
                public JSONObject getForUpdate(final String receipt) throws RepositoryException {
                    requireActorInCurrentTransaction(actor);
                    return requests.getForUpdate(receipt);
                }
                public String add(final JSONObject row) throws RepositoryException { return requests.add(row); }
                public void update(final String receipt, final JSONObject row, final String... fields) throws RepositoryException {
                    requests.update(receipt, row, fields);
                }
            };
            return ApiV3IdempotencyMgmtService.runAtomic(guarded, actor,
                    skip ? "professions.onboarding" : "professions.privacy", key, normalized,
                    () -> applyInCurrentTransaction(actor, skip, normalized));
        } catch (final ApiV3Exception error) { throw error; }
        catch (final Exception error) {
            LogManager.getLogger(getClass()).warn("Profession settings unavailable");
            throw unavailable();
        }
    }
    public JSONObject skipInCurrentTransaction(final String actor) throws Exception {
        return applyInCurrentTransaction(actor, true, new JSONObject().put("onboardingState", "SKIPPED"));
    }
    public JSONObject privacyInCurrentTransaction(final String actor, final JSONObject body) throws Exception {
        return applyInCurrentTransaction(actor, false, ApiV3ProfessionSettingsInputs.privacy(body));
    }
    private JSONObject applyInCurrentTransaction(final String actor, final boolean skip, final JSONObject normalized) throws Exception {
        id(actor);
        if (!privacy.hasTransactionBegun()) throw new IllegalStateException("profession settings transaction required");
        return ApiV3TransactionSavepoint.run(() -> {
            requireActorInCurrentTransaction(actor);
            final String columns = skip ? "userProgressVersion" : "userPrivacyVersion,publicStatsVersion";
            final var rows = privacy.select("SELECT oId," + columns + " FROM `" + privacy.getName()
                    + "` WHERE userId=? LIMIT 2 FOR UPDATE", actor);
            if (rows.size() > 1) throw unavailable();
            final long now = System.currentTimeMillis();
            if (rows.isEmpty()) {
                final int changed = ApiV3TransactionStatements.execute("INSERT INTO `" + privacy.getName()
                        + "` (oId,userId,onboardingState,primaryProfessionId,privacyPreset,moduleVisibilityJson,"
                        + "userProgressVersion,userPrivacyVersion,publicStatsVersion,createdAt,updatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        Ids.genTimeMillisId(), actor, skip ? "SKIPPED" : "UNDECIDED", "", skip ? "ALL_PUBLIC" : normalized.getString("preset"),
                        skip ? ApiV3ProfessionSettingsInputs.presetVisibility("ALL_PUBLIC").toString() : normalized.getJSONObject("moduleVisibility").toString(),
                        skip ? 1L : 0L, skip ? 0L : 1L, skip ? 0L : 1L, now, now);
                if (changed != 1) throw unavailable();
            } else {
                final JSONObject row = rows.getFirst();
                final int changed;
                if (skip) {
                    changed = ApiV3TransactionStatements.execute("UPDATE `" + privacy.getName()
                            + "` SET onboardingState=?,primaryProfessionId=?,userProgressVersion=?,updatedAt=? WHERE oId=? AND userId=?",
                            "SKIPPED", "", nextVersion(row, "userProgressVersion"), now, row.getString("oId"), actor);
                } else {
                    changed = ApiV3TransactionStatements.execute("UPDATE `" + privacy.getName()
                            + "` SET privacyPreset=?,moduleVisibilityJson=?,userPrivacyVersion=?,publicStatsVersion=?,updatedAt=? WHERE oId=? AND userId=?",
                            normalized.getString("preset"), normalized.getJSONObject("moduleVisibility").toString(),
                            nextVersion(row, "userPrivacyVersion"), nextVersion(row, "publicStatsVersion"), now, row.getString("oId"), actor);
                }
                if (changed != 1) throw unavailable();
            }
            if (skip) return new JSONObject().put("onboardingState", "SKIPPED").put("primaryProfessionId", "");
            return new JSONObject().put("privacyPreset", normalized.getString("preset"))
                    .put("moduleVisibility", new JSONObject(normalized.getJSONObject("moduleVisibility").toString()));
        });
    }
    private void requireActorInCurrentTransaction(final String actor) throws RepositoryException {
        final var current = users.select("SELECT oId,userStatus FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (current.size() != 1 || !(current.getFirst().opt("userStatus") instanceof Number number)
                || !String.valueOf(UserExt.USER_STATUS_C_VALID).equals(number.toString())) throw ApiV3Requests.notFound();
    }
    private static long nextVersion(final JSONObject row, final String key) {
        final Object value = row.opt(key);
        if (!(value instanceof Number number)) throw unavailable();
        try {
            final long version = new BigDecimal(number.toString()).longValueExact();
            if (version < 0 || version == Long.MAX_VALUE) throw unavailable();
            return version + 1L;
        } catch (final ArithmeticException | NumberFormatException error) { throw unavailable(); }
    }
    private static void id(final String actor) {
        if (actor == null || !actor.matches("[0-9]{1,19}")) throw ApiV3Requests.invalid();
    }
    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "职业设置暂不可用");
    }
}
