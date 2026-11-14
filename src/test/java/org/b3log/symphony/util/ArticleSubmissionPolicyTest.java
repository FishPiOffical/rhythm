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

import org.testng.Assert;
import java.time.Instant;
import java.time.ZoneId;

public class ArticleSubmissionPolicyTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long FRIDAY = time("2026-10-16T09:00:00+08:00");
    private static final ArticleSubmissionPolicy.History OLD = new ArticleSubmissionPolicy.History(FRIDAY - 86_400_000, false);
    public static void main(final String[] args) {
        reject("phone_required", new ArticleSubmissionPolicy.Submission(false, "普通文章", "内容正文", "新人报道"), OLD, FRIDAY, false);
        final var first = new ArticleSubmissionPolicy.History(null, false);
        reject("introduction_required", request("普通文章", "内容正文", "日常"), first, FRIDAY, false);
        for (final String tag : new String[]{"新人报道", "日常,新人报到", "新人报道附记"}) {
            Assert.assertFalse(check(request("普通文章", "内容正文", tag), first, FRIDAY, false).weeklyReward());
        }
        Assert.assertFalse(check(request("普通文章", "内容正文", "日常"), OLD, FRIDAY, false).weeklyReward());
        System.out.println("PASS bound phone and first-public-article introduction rules, including existing substring behavior");
        for (final String clock : new String[]{"2026-10-16T07:59:59+08:00", "2026-10-16T19:31:00+08:00"}) {
            final long now = time(clock);
            final var recent = new ArticleSubmissionPolicy.History(now - 60_000, false);
            reject("curfew_interval", request("普通文章", "内容正文", "日常"), recent, now, false);
            Assert.assertFalse(check(request("普通文章", "内容正文", "日常"), recent, now, true).weeklyReward());
            Assert.assertFalse(check(request("普通文章", "内容正文", "日常"), new ArticleSubmissionPolicy.History(now - 3_600_000, false), now, false).weeklyReward());
        }
        for (final String clock : new String[]{"2026-10-16T08:00:00+08:00", "2026-10-16T19:30:59+08:00"}) {
            final long now = time(clock);
            Assert.assertFalse(check(request("普通文章", "内容正文", "日常"), new ArticleSubmissionPolicy.History(now - 60_000, false), now, false).weeklyReward());
        }
        System.out.println("PASS curfew minute boundaries, exact hour interval and development-only curfew exemption");
        Assert.assertTrue(check(request("摸鱼周报 20261016", "文".repeat(128), "日常"), OLD, FRIDAY, false).weeklyReward());
        reject("weekly_content", request("摸鱼周报 20261016", "文".repeat(127), "日常"), OLD, FRIDAY, false);
        Assert.assertFalse(check(request("摸鱼周报 20261016", "四字正文", "日常"), new ArticleSubmissionPolicy.History(OLD.latestPublicArticleId(), true), FRIDAY, false).weeklyReward());
        reject("weekly_date", request("摸鱼周报 20261015", "文".repeat(128), "日常"), OLD, FRIDAY, false);
        reject("weekly_time", request("摸鱼周报 20261016", "文".repeat(128), "日常"), OLD, time("2026-10-16T08:59:59+08:00"), true);
        reject("weekly_time", request("摸鱼周报 20261017", "文".repeat(128), "日常"), OLD, time("2026-10-17T09:00:00+08:00"), true);
        Assert.assertFalse(check(request("摸鱼周报随想", "内容正文", "日常"), OLD, FRIDAY, false).weeklyReward());
        System.out.println("PASS weekly title/date/Friday time/content boundaries and existing-title no-repeat-credit rule");
        final var weekly = request("摸鱼周报 20261016", "文".repeat(128), "日常");
        try { ArticleSubmissionPolicy.check(weekly, OLD, FRIDAY, ZoneId.of("UTC"), true); Assert.fail(); }
        catch (final ArticleSubmissionPolicy.Rejected e) { Assert.assertEquals(e.code, "weekly_time"); }
        Assert.assertTrue(ArticleSubmissionPolicy.check(weekly, OLD, FRIDAY, ZONE, false).weeklyReward());
        System.out.println("PASS rules use explicit server timezone rather than client date or machine test timezone");
    }
    private static long time(final String time) { return Instant.parse(time).toEpochMilli(); }
    private static ArticleSubmissionPolicy.Submission request(final String title, final String content, final String tags) {
        return new ArticleSubmissionPolicy.Submission(true, title, content, tags);
    }
    private static ArticleSubmissionPolicy.Decision check(final ArticleSubmissionPolicy.Submission request, final ArticleSubmissionPolicy.History history,
                                                          final long now, final boolean development) {
        return ArticleSubmissionPolicy.check(request, history, now, ZONE, development);
    }
    private static void reject(final String code, final ArticleSubmissionPolicy.Submission request, final ArticleSubmissionPolicy.History history,
                               final long now, final boolean development) {
        try { check(request, history, now, development); Assert.fail(code); }
        catch (final ArticleSubmissionPolicy.Rejected e) { Assert.assertEquals(e.code, code); }
    }
}
