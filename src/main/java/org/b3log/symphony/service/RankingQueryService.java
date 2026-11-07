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

import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.PointtransferRepository;
import org.b3log.symphony.repository.SponsorRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3RankingDto;
import org.b3log.symphony.util.Vocation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Shared data queries for the legacy and v3 aggregate rankings. */
@Singleton
public class RankingQueryService {
    @Inject private PointtransferRepository pointtransferRepository;
    @Inject private SponsorRepository sponsorRepository;
    @Inject private ArticleRepository articleRepository;
    @Inject private UserQueryService userQueryService;
    @Inject private AvatarQueryService avatarQueryService;

    public List<JSONObject> invite() throws Exception {
        return profiles(pointtransferRepository.select("select toId,count(toId) as c from " + pointtransferRepository.getName()
                + " where type = 6 group by toId order by c desc limit 64"), "toId");
    }
    public JSONObject donate() throws Exception {
        final List<JSONObject> totals = sponsorRepository.select("select sum(amount) as totalAmount from " + sponsorRepository.getName() + " limit 1");
        final JSONObject total = totals.isEmpty() ? new JSONObject() : totals.get(0);
        final double amount = total.optDouble("totalAmount", 0);
        total.put("totalAmount", amount).put("donateMakeDays", BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(5), 0, RoundingMode.HALF_UP).doubleValue());
        final List<JSONObject> rows = profiles(sponsorRepository.select("select userId as userId,sum(amount) as total,count(*) as totalCount from "
                + sponsorRepository.getName() + " group by userId order by total desc limit 64"), "userId");
        return new JSONObject().put("data", rows).put("totalData", total).put("type", "donate");
    }
    public JSONObject perfect() throws Exception {
        final List<JSONObject> rows = profiles(articleRepository.select("select " + Article.ARTICLE_AUTHOR_ID + " as userId,count(*) as count from "
                + articleRepository.getName() + " where " + Article.ARTICLE_PERFECT + " = " + Article.ARTICLE_PERFECT_C_PERFECT
                + " group by userId order by count desc limit 64"), "userId");
        return new JSONObject().put("data", rows).put("type", "perfect");
    }
    private List<JSONObject> profiles(final List<JSONObject> rows, final String userIdKey) {
        final List<JSONObject> result = new ArrayList<>();
        for (final JSONObject row : rows) {
            final JSONObject found = userQueryService.getUser(row.optString(userIdKey));
            if (null == found) continue;
            final JSONObject user = new JSONObject(found.toString());
            avatarQueryService.fillUserAvatarURL(user);
            // Keep the legacy projection unchanged; v3 has its own stricter DTO.
            for (final String key : new String[]{"userPassword", "userLatestLoginIP", "userPhone", "userQQ", "userCity", "userCountry",
                    "userEmail", "secret2fa", "apiKey", "token", "accessToken", "refreshToken"}) user.remove(key);
            row.put("profile", user);
            result.add(row);
        }
        return result;
    }
    public List<JSONObject> xiaoice(final String type) {
        ApiV3RankingDto.subType("xiaoice", type);
        try {
            final HttpResponse response = HttpRequest.get("https://pwl.yuis.cc/GetXiaoIceGameRank?key=xiaoIceGame&type=" + type)
                    .connectionTimeout(3000).timeout(7000).header("User-Agent", Vocation.UA).send();
            if (200 != response.statusCode()) throw unavailable();
            response.charset("UTF-8");
            final String body = response.bodyText();
            if (null == body || body.length() > 1024 * 1024) throw unavailable();
            final JSONArray data = new JSONObject(body).optJSONArray("data");
            if (null == data || data.length() > 1000) throw unavailable();
            final List<JSONObject> result = new ArrayList<>();
            final String[] levels = {"黄阶低级", "黄阶中级", "黄阶高级", "玄阶低级", "玄阶中级", "玄阶高级", "地阶低级", "地阶中级", "地阶高级", "天阶低级", "天阶中级", "天阶高级"};
            for (int i = 0; i < data.length(); i++) {
                final JSONObject row = data.optJSONObject(i);
                if (null == row) continue;
                final JSONObject user = userQueryService.getUserByName(row.optString("uname"));
                if (null == user) continue;
                final Object rawFamily = row.opt("family");
                final JSONObject family;
                try { family = rawFamily instanceof JSONObject object ? object : new JSONObject(String.valueOf(rawFamily)); }
                catch (final Exception ignored) { continue; }
                final int ancestry = family.optInt("ancestry", -1), gongfa = family.optInt("gongfa", -1);
                if (ancestry < 0 || ancestry >= levels.length || gongfa < 0 || gongfa >= levels.length) continue;
                row.put("ancestry", levels[ancestry]).put("gongfa", levels[gongfa]).remove("family");
                row.put(UserExt.USER_AVATAR_URL, user.optString(UserExt.USER_AVATAR_URL)).put(UserExt.USER_INTRO, user.optString(UserExt.USER_INTRO))
                        .put(User.USER_URL, user.optString(User.USER_URL)).put(UserExt.USER_NO, user.optInt(UserExt.USER_NO)).put(UserExt.USER_APP_ROLE, user.optInt(UserExt.USER_APP_ROLE));
                avatarQueryService.fillUserAvatarURL(row);
                result.add(row);
            }
            return result;
        } catch (final Exception e) { throw unavailable(); }
    }
    private static ApiV3Exception unavailable() { return new ApiV3Exception(503, "ranking_unavailable", "排行榜暂不可用"); }
}
