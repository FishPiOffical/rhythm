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
package org.b3log.symphony.util;

import java.time.ZoneId;
import java.util.function.LongSupplier;

/** Shared transaction body. The store binds the trusted author and existing business services. */
public final class ArticleSubmission {
    private ArticleSubmission() { }
    public interface Store<T> {
        boolean inTransaction();
        void lockPublicationState() throws Exception;
        ArticleSubmissionPolicy.Submission submission() throws Exception;
        ArticleSubmissionPolicy.History history(ArticleSubmissionPolicy.Submission submission) throws Exception;
        String creditWeekly(int amount, long now) throws Exception;
        T publish() throws Exception;
    }
    public record Result<T>(T publication, String weeklyTransferId) { }
    public static <T> Result<T> apply(final Store<T> store, final LongSupplier clock, final ZoneId zone,
                                     final boolean development) throws Exception {
        if (!store.inTransaction() || !ArticlePublicationCaches.active()) {
            throw new IllegalStateException("submission transaction and publication boundary required");
        }
        store.lockPublicationState();
        final var request = store.submission();
        final var history = store.history(request);
        final long now = clock.getAsLong();
        final var decision = ArticleSubmissionPolicy.check(request, history, now, zone, development);
        final String weeklyTransfer;
        if (decision.weeklyReward()) {
            weeklyTransfer = store.creditWeekly(ArticleSubmissionPolicy.WEEKLY_REWARD_POINTS, now);
            if (null == weeklyTransfer || weeklyTransfer.isBlank()) throw new IllegalStateException("weekly reward transfer failed");
        } else weeklyTransfer = null;
        return new Result<>(store.publish(), weeklyTransfer);
    }
}
