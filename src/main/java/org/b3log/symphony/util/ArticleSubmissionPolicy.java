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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Shared submission rules formerly evaluated by the page controller; no persistence or external events. */
public final class ArticleSubmissionPolicy {
    public static final int WEEKLY_REWARD_POINTS = 1000;
    private ArticleSubmissionPolicy() { }
    public record Submission(boolean phoneBound, String title, String content, String tags) { }
    public record History(Long latestPublicArticleId, boolean weeklyTitleExists) { }
    public record Decision(boolean weeklyReward) { }
    public static final class Rejected extends RuntimeException {
        public final String code;
        public Rejected(final String code, final String message) { super(message); this.code = code; }
    }
    public static boolean weeklyTitleCandidate(final String title) { return title.startsWith("摸鱼周报") && title.length() == 13; }
    public static Decision check(final Submission request, final History history, final long now,
                                 final ZoneId zone, final boolean development) {
        if (!request.phoneBound()) throw new Rejected("phone_required", "未绑定手机号码，无法使用此功能。请至设置-账户中绑定手机号码。");
        if (null == history.latestPublicArticleId() && !request.tags().contains("新人报道") && !request.tags().contains("新人报到")) {
            throw new Rejected("introduction_required", "迈入社区第一步, 介绍下自己~ 请先发送一个新人报道(标签需要包含'新人报道')帖吧!");
        }
        final var local = Instant.ofEpochMilli(now).atZone(zone);
        final int hhmm = local.getHour() * 100 + local.getMinute();
        if (!development && (hhmm < 800 || hhmm > 1930) && null != history.latestPublicArticleId()
                && now - history.latestPublicArticleId() < 60L * 60 * 1000) {
            throw new Rejected("curfew_interval", "摸鱼派已进入宵禁模式, 充足的睡眠是摸鱼的关键, 良性的思考一定不会促使你高频的发帖, 为了你的身心健康，我们将调整发帖间隔为 1h (一小时)...感谢你的陪伴，我们明天再见，早点休息，(¦3[▓▓] 晚安");
        }
        if (!weeklyTitleCandidate(request.title())) return new Decision(false);
        final String expected = "摸鱼周报 " + local.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        if (!request.title().equals(expected)) throw new Rejected("weekly_date", "您的摸鱼周报日期不是今天哟~检查一下吧");
        if (local.getDayOfWeek() != DayOfWeek.FRIDAY || local.getHour() <= 8) {
            throw new Rejected("weekly_time", "还没到摸鱼周报的时间哟~周报时间是每周五9点以后哟~");
        }
        if (history.weeklyTitleExists()) return new Decision(false);
        if (request.content().length() < 128) {
            throw new Rejected("weekly_content", "您的摸鱼周报字数不合格，请认真对待！<br>请注意：根据摸鱼守则，摸鱼周报应该是抒发一周所想的灵感篇章，而不是为了水积分而随意撰写的一两句话，请保证摸鱼周报有个人情感、话题性，发送摸鱼周报带有明显水帖行为的，将取消摸鱼周报的帖子标识（恢复本周摸鱼周报的开放权限），并扣除奖励的积分，并处罚金 1000 积分。");
        }
        return new Decision(true);
    }

    /** Values remain bound by the repository adapter. Mirrors getUserArticles' public/non-invalid history. */
    public static String latestPublicSql(final String table, final boolean lock) {
        return "SELECT oId FROM `" + table + "` WHERE articleAuthorId=? AND articleAnonymous=? AND articleStatus<>?"
                + " ORDER BY articleCreateTime DESC LIMIT 1" + (lock ? " FOR UPDATE" : "");
    }
}
