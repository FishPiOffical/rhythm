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

import java.util.ArrayList;
import java.util.List;

/** Existing edit fees, including the distinct preflight balance threshold during the free-edit window. */
public final class ArticleEditPoints {
    private ArticleEditPoints() { }
    public static int fee(final int base, final long followers, final long collections, final long watchers) {
        if (base < 0 || followers < 0 || collections < 0 || watchers < 0) throw new IllegalArgumentException("negative edit input");
        final long subscriptions = Math.multiplyExact(Math.addExact(collections, watchers), 2L);
        return Math.toIntExact(Math.addExact(Math.addExact((long) base, Math.round(Math.sqrt(followers))), subscriptions));
    }
    public static boolean pastFreeWindow(final long created, final long now) { return now - created > 300_000L; }
    public static ArticlePublishPoints.Plan plan(final boolean anonymous, final boolean pastFreeWindow,
                                                final int editFee, final boolean enableReward, final int activationFee,
                                                final int editType, final int rewardType) {
        if (editFee < 0 || activationFee < 0) throw new IllegalArgumentException("negative edit fee");
        if (anonymous) return new ArticlePublishPoints.Plan(0, List.of());
        final var debits = new ArrayList<ArticlePublishPoints.Debit>();
        if (pastFreeWindow) debits.add(new ArticlePublishPoints.Debit(editType, editFee));
        if (enableReward) debits.add(new ArticlePublishPoints.Debit(rewardType, activationFee));
        return new ArticlePublishPoints.Plan(editFee, debits);
    }
}
