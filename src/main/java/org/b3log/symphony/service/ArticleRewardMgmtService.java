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
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.AccountPairLocks;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Article thanks and paid-content purchase share one transfer/reward/notification transaction. */
@Service
public class ArticleRewardMgmtService {
    @Inject private ArticleRepository articles;
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private RewardRepository rewards;
    @Inject private PointtransferMgmtService points;
    @Inject private NotificationMgmtService notifications;
    @Inject private LivenessMgmtService liveness;
    public enum Kind { THANK, REWARD }
    public enum State { APPLIED, ALREADY, OWN, UNAVAILABLE, NOT_ENABLED }
    /** Receipt metadata only, never the purchased body or a private persistence object. */
    public record Result(String actor, String recipient, Kind kind, State state, String transferId) {
        public boolean applied() { return state == State.APPLIED; }
        /** Purchase/ownership eligibility only; the caller must also authorize current article visibility. */
        public boolean mayExposeRewardContent() { return kind == Kind.REWARD && (state == State.APPLIED || state == State.ALREADY || state == State.OWN); }
    }
    public static final class Rejected extends RuntimeException {
        public final String code;
        public Rejected(final String code) { super(code); this.code = code; }
    }
    @FunctionalInterface public interface TargetCheck { void check(JSONObject actor, JSONObject article) throws Exception; }
    public static int rewardType(final Kind kind) { return kind == Kind.THANK ? Reward.TYPE_C_THANK_ARTICLE : Reward.TYPE_C_ARTICLE; }

    public Result applyInCurrentTransaction(final String actor, final String id, final String expectedAuthor,
                                             final Kind kind, final TargetCheck check) throws Exception {
        Objects.requireNonNull(kind); Objects.requireNonNull(check);
        if (!articles.hasTransactionBegun() || !ArticlePublicationCaches.active()
                || !AccountPairLocks.lock(actor, Pointtransfer.ID_C_SYS).isHeldByCurrentThread()
                || !AccountPairLocks.lock(actor, expectedAuthor).isHeldByCurrentThread()) throw new IllegalStateException("article reward transaction and account-pair set required");
        if (null == options.getForUpdate(Option.ID_C_STATISTIC_ARTICLE_COUNT)) throw new IllegalStateException("article statistic option missing");
        for (final String participant : new TreeSet<>(List.of(actor, expectedAuthor))) users.getForUpdate(participant);
        final var sender = users.getForUpdate(actor);
        final var recipient = users.getForUpdate(expectedAuthor);
        if (null == sender || null == recipient || sender.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID
                || recipient.optInt(UserExt.USER_STATUS) != UserExt.USER_STATUS_C_VALID) return result(actor, expectedAuthor, kind, State.UNAVAILABLE, null);
        final var article = articles.getForUpdate(id);
        if (null == article || article.optInt(Article.ARTICLE_STATUS) == Article.ARTICLE_STATUS_C_INVALID) return result(actor, expectedAuthor, kind, State.UNAVAILABLE, null);
        if (!expectedAuthor.equals(article.optString(Article.ARTICLE_AUTHOR_ID))) throw new Rejected("target_changed");
        check.check(sender, article);
        final int amount = kind == Kind.THANK ? Pointtransfer.TRANSFER_SUM_C_ARTICLE_THANK : article.optInt(Article.ARTICLE_REWARD_POINT);
        if (kind == Kind.REWARD && amount < 1) return result(actor, expectedAuthor, kind, State.NOT_ENABLED, null);
        if (amount < 0) throw new IllegalStateException("negative article thank fee");
        if (actor.equals(expectedAuthor)) return result(actor, expectedAuthor, kind, State.OWN, null);
        final int anonymous = article.optInt(Article.ARTICLE_ANONYMOUS);
        if (anonymous != Article.ARTICLE_ANONYMOUS_C_PUBLIC && anonymous != Article.ARTICLE_ANONYMOUS_C_ANONYMOUS) throw new IllegalStateException("invalid stored article anonymity");
        // The locked parent serializes old and v3 writers; query failures cannot authorize another charge.
        if (!rewards.select("SELECT oId FROM `" + rewards.getName() + "` WHERE senderId=? AND dataId=? AND `type`=? LIMIT 1 FOR UPDATE",
                actor, id, rewardType(kind)).isEmpty()) return result(actor, expectedAuthor, kind, State.ALREADY, null);
        final String rewardId = Ids.genTimeMillisId();
        String transferId = null;
        if (anonymous == Article.ARTICLE_ANONYMOUS_C_PUBLIC) {
            transferId = points.transferInCurrentTransaction(actor, expectedAuthor,
                    kind == Kind.THANK ? Pointtransfer.TRANSFER_TYPE_C_ARTICLE_THANK : Pointtransfer.TRANSFER_TYPE_C_ARTICLE_REWARD,
                    amount, rewardId, System.currentTimeMillis(), "", PointtransferSource.EMPTY, false);
            if (null == transferId || transferId.isBlank()) throw new Rejected("transfer_failed");
        }
        if (kind == Kind.THANK) {
            final int count = article.optInt(Article.ARTICLE_THANK_CNT);
            if (count < 0) throw new IllegalStateException("negative stored article thank count");
            article.put(Article.ARTICLE_THANK_CNT, Math.addExact(count, 1));
            articles.update(id, article, Article.ARTICLE_THANK_CNT);
        }
        rewards.add(new JSONObject().put(Keys.OBJECT_ID, rewardId).put(Reward.SENDER_ID, actor).put(Reward.DATA_ID, id).put(Reward.TYPE, rewardType(kind)));
        notifications.addArticleRewardNotificationInCurrentTransaction(expectedAuthor, rewardId,
                kind == Kind.THANK ? Notification.DATA_TYPE_C_POINT_ARTICLE_THANK : Notification.DATA_TYPE_C_POINT_ARTICLE_REWARD);
        return result(actor, expectedAuthor, kind, State.APPLIED, transferId);
    }
    private static Result result(final String actor, final String recipient, final Kind kind, final State state, final String transfer) {
        return new Result(actor, recipient, kind, state, transfer);
    }
    public void afterApplied(final Result result) {
        if (articles.hasTransactionBegun() || ArticlePublicationCaches.active()) throw new IllegalStateException("article reward effects before commit");
        if (!result.applied()) return;
        if (null != result.transferId()) {
            try { points.indexTransferAsync(result.actor(), result.recipient(), result.transferId()); }
            catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Index committed article reward failed", e); }
        }
        try { notifications.publishReadStateRefresh(result.recipient()); }
        catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Refresh committed article reward failed", e); }
        // Both historical article actions count as reward activity, including thanks.
        try { liveness.incLiveness(result.actor(), Liveness.LIVENESS_REWARD); }
        catch (final RuntimeException e) { LogManager.getLogger(getClass()).error("Record committed article reward activity failed", e); }
    }
}
