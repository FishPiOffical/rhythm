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

import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Reward;
import org.json.JSONObject;

/** Existing self-service removal rules; I/O failures must propagate rather than imply no rewards. */
public final class ArticleRemovalPolicy {
    private ArticleRemovalPolicy() { }
    @FunctionalInterface public interface Rewards { long count(int type) throws Exception; }
    public static final class Rejected extends RuntimeException {
        public final String label;
        private Rejected(final String label) { super(label); this.label = label; }
    }
    /** Same restrictions already enforced by the legacy page controller. */
    public static void checkTiming(final JSONObject article, final long now) {
        if (article.optString(Article.ARTICLE_TITLE).startsWith("摸鱼周报 ")) reject("weekly_report");
        final long created = article.optLong(Article.ARTICLE_CREATE_TIME);
        if (created > now - 86_400_000L) reject("too_young");
    }
    public static void check(final JSONObject article, final Rewards rewards) throws Exception {
        if (Article.ARTICLE_STATUS_C_VALID != article.optInt(Article.ARTICLE_STATUS)) reject("articleLockedLabel");
        if (article.optLong(Article.ARTICLE_COMMENT_CNT) > 0) reject("removeArticleFoundCmtLabel");
        for (final String field : new String[]{Article.ARTICLE_WATCH_CNT, Article.ARTICLE_COLLECT_CNT,
                Article.ARTICLE_GOOD_CNT, Article.ARTICLE_BAD_CNT}) {
            if (article.optLong(field) > 0) reject("removeArticleFoundWatchEtcLabel");
        }
        if (rewards.count(Reward.TYPE_C_ARTICLE) > 0) reject("removeArticleFoundRewardLabel");
        if (rewards.count(Reward.TYPE_C_THANK_ARTICLE) > 0) reject("removeArticleFoundThankLabel");
    }
    private static void reject(final String label) { throw new Rejected(label); }
}
