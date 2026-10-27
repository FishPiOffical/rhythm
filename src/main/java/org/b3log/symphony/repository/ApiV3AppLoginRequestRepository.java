package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.ApiV3AppLoginRequest;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.json.JSONObject;

@Repository
public class ApiV3AppLoginRequestRepository extends AbstractRepository {
    public ApiV3AppLoginRequestRepository() { super(ApiV3AppLoginRequest.TABLE); }
    @Override public Transaction beginTransaction() { return ApiV3Transactions.strict(super.beginTransaction()); }
    /** Hint contains immutable bindings only; the owner must re-read them under the root lock. */
    public JSONObject hint(final String hash) throws RepositoryException {
        return one("SELECT oId,userId,issuerTokenId,credentialEpoch,scope FROM `" + getName() + "` WHERE oId=?", hash);
    }
    public JSONObject currentForUpdate(final String hash) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("app login transaction required");
        return one("SELECT * FROM `" + getName() + "` WHERE oId=? FOR UPDATE", hash);
    }
    /** Called after the native client namespace lock, in the administrator's existing strict root. */
    public int invalidatePendingInCurrentTransaction() throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("app login transaction required");
        return ApiV3TransactionStatements.execute("DELETE FROM `"+getName()+"` WHERE state IN (?,?,?,?)",
                "WAITING","CLAIMED","APP_CONFIRMED","WEB_APPROVED");
    }
    private JSONObject one(final String sql, final String hash) throws RepositoryException {
        if (hash == null || !hash.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("request hash");
        final var rows = select(sql, hash);
        if (rows.size() > 1) throw new RepositoryException("duplicate app login request");
        return rows.isEmpty() || !hash.equals(rows.getFirst().optString("oId")) ? null : rows.getFirst();
    }
}
