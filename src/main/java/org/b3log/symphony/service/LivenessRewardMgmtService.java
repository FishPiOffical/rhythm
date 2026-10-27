package org.b3log.symphony.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Liveness;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.repository.LivenessRepository;
import org.b3log.symphony.repository.PointtransferRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.LivenessRewardClaim;
import org.json.JSONObject;
import java.time.ZoneId;
import java.util.concurrent.Callable;

/** Atomic yesterday-reward primitive shared by legacy and Bearer clients. */
@Service
public class LivenessRewardMgmtService {
    private static final Logger LOGGER = LogManager.getLogger(LivenessRewardMgmtService.class);
    @Inject private UserRepository userRepository;
    @Inject private PointtransferRepository pointtransferRepository;
    @Inject private LivenessRepository livenessRepository;
    @Inject private PointtransferMgmtService points;

    public <T> T withTransferLock(final String actor, final Callable<T> operation) throws Exception {
        return points.executeWithTransferLock(Pointtransfer.ID_C_SYS, actor, operation);
    }
    public JSONObject status(final String actor) throws RepositoryException {
        final var period = LivenessRewardClaim.Period.at(System.currentTimeMillis(), ZoneId.systemDefault());
        final boolean claimed = claimed(actor, period, false);
        final int amount = amount(actor, period, false);
        return new JSONObject().put("date", period.date()).put("amount", amount)
                .put("claimed", claimed).put("claimable", !claimed && amount > 0);
    }
    public LivenessRewardClaim.Result claimInCurrentTransaction(final String actor) throws Exception {
        return claimInCurrentTransaction(actor, null);
    }
    public LivenessRewardClaim.Result claimInCurrentTransaction(final String actor, final String expectedDate) throws Exception {
        if (!pointtransferRepository.hasTransactionBegun()) throw new IllegalStateException("liveness reward transaction required");
        return LivenessRewardClaim.claim(new LivenessRewardClaim.Store() {
            public void lockOwner() throws RepositoryException {
                if (null == userRepository.getForUpdate(actor)) throw new LivenessRewardClaim.Unavailable();
            }
            public boolean alreadyClaimed(final LivenessRewardClaim.Period period) throws RepositoryException { return claimed(actor, period, true); }
            public int amount(final LivenessRewardClaim.Period period) throws RepositoryException { return LivenessRewardMgmtService.this.amount(actor, period, true); }
            public String transfer(final int amount, final long now) {
                return points.transferInCurrentTransaction(Pointtransfer.ID_C_SYS, actor,
                        Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_YESTERDAY_LIVENESS_REWARD, amount, actor, now, "", PointtransferSource.EMPTY, false);
            }
        }, System::currentTimeMillis, ZoneId.systemDefault(), expectedDate);
    }
    public int claimLegacy(final String actor) {
        try {
            return withTransferLock(actor, () -> {
                final Transaction tx = pointtransferRepository.beginTransaction();
                final LivenessRewardClaim.Result result;
                try { result = claimInCurrentTransaction(actor); tx.commit(); }
                catch (final Exception e) { if (tx.isActive()) tx.rollback(); throw e; }
                afterCommit(actor, result);
                return result.amount();
            });
        } catch (final LivenessRewardClaim.Unavailable e) { return -1; }
        catch (final Exception e) { LOGGER.error("Liveness reward claim failed", e); return -1; }
    }
    public void afterCommit(final String actor, final LivenessRewardClaim.Result result) {
        if (null == result) return;
        try { points.indexTransferAsync(Pointtransfer.ID_C_SYS, actor, result.transferId()); }
        catch (final Exception e) { LOGGER.error("Index committed liveness reward failed", e); }
    }
    private boolean claimed(final String actor, final LivenessRewardClaim.Period period, final boolean lock) throws RepositoryException {
        return !pointtransferRepository.select(LivenessRewardClaim.claimedSql(pointtransferRepository.getName(), lock),
                Pointtransfer.ID_C_SYS, actor, Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_YESTERDAY_LIVENESS_REWARD, period.start(), period.end()).isEmpty();
    }
    private int amount(final String actor, final LivenessRewardClaim.Period period, final boolean lock) throws RepositoryException {
        final var rows = livenessRepository.select(LivenessRewardClaim.livenessSql(livenessRepository.getName(), lock), actor, period.date());
        return rows.isEmpty() ? 0 : Math.max(0, Liveness.calcPoint(rows.getFirst()));
    }
}
