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
import org.testng.Assert;
import java.util.ArrayList;
import java.util.List;

public class ArticleRemovalPolicyTest {
    public static void main(final String[] args) throws Exception {
        final long now = 1_800_000_000_000L;
        ArticleRemovalPolicy.checkTiming(valid().put(Article.ARTICLE_CREATE_TIME, now - 86_400_000L), now);
        timing(valid().put(Article.ARTICLE_CREATE_TIME, now - 86_400_000L + 1), now, "too_young");
        timing(valid().put(Article.ARTICLE_CREATE_TIME, now + 1), now, "too_young");
        timing(valid().put(Article.ARTICLE_TITLE, "摸鱼周报 20261016"), now, "weekly_report");
        System.out.println("PASS self-service 24-hour exact boundary, future creation and weekly report restrictions");
        final var calls = new ArrayList<Integer>();
        ArticleRemovalPolicy.check(valid(), type -> { calls.add(type); return 0; });
        Assert.assertEquals(calls, List.of(Reward.TYPE_C_ARTICLE, Reward.TYPE_C_THANK_ARTICLE));
        System.out.println("PASS unengaged valid article checks both reward types and remains removable");
        reject(valid().put(Article.ARTICLE_STATUS, Article.ARTICLE_STATUS_C_INVALID), "articleLockedLabel");
        reject(valid().put(Article.ARTICLE_COMMENT_CNT, 1), "removeArticleFoundCmtLabel");
        for (final var field : new String[]{Article.ARTICLE_WATCH_CNT, Article.ARTICLE_COLLECT_CNT,
                Article.ARTICLE_GOOD_CNT, Article.ARTICLE_BAD_CNT}) {
            reject(valid().put(field, 1), "removeArticleFoundWatchEtcLabel");
        }
        System.out.println("PASS locked, commented, subscribed, collected and voted articles reject before reward queries");
        for (final int blocked : new int[]{Reward.TYPE_C_ARTICLE, Reward.TYPE_C_THANK_ARTICLE}) {
            try {
                ArticleRemovalPolicy.check(valid(), type -> type == blocked ? 4294967296L : 0);
                Assert.fail();
            } catch (final ArticleRemovalPolicy.Rejected e) {
                Assert.assertEquals(e.label, blocked == Reward.TYPE_C_ARTICLE ? "removeArticleFoundRewardLabel" : "removeArticleFoundThankLabel");
            }
        }
        System.out.println("PASS paid and thanked articles reject without narrowing reward counts to int");
        final var failure = new Exception("database unavailable");
        try { ArticleRemovalPolicy.check(valid(), type -> { throw failure; }); Assert.fail(); }
        catch (final Exception e) { Assert.assertSame(e, failure); }
        System.out.println("PASS reward query failure propagates instead of granting removal");
    }
    private static void timing(final JSONObject article, final long now, final String code) {
        try { ArticleRemovalPolicy.checkTiming(article, now); Assert.fail(); }
        catch (final ArticleRemovalPolicy.Rejected e) { Assert.assertEquals(e.label, code); }
    }
    private static JSONObject valid() { return new JSONObject().put(Article.ARTICLE_STATUS, Article.ARTICLE_STATUS_C_VALID); }
    private static void reject(final JSONObject article, final String label) throws Exception {
        try {
            ArticleRemovalPolicy.check(article, type -> { Assert.fail("rewards queried after an earlier rejection"); return 0; });
            Assert.fail();
        } catch (final ArticleRemovalPolicy.Rejected e) { Assert.assertEquals(e.label, label); }
    }
}
