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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.LogsRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Fixed-size discovery and metadata-only logs. No legacy handlers, stored credentials or log bodies. */
@Service
public class ApiV3DiscoveryExtrasQueryService {
    public static final List<String> LOG_CATEGORIES = List.of("增加积分", "扣除积分", "发送系统通知", "调整用户背包", "移除勋章",
            "移除勋章(使用UserId)", "添加勋章", "解封IP", "封禁IP", "发放工资", "上传摸鱼大闯关数据", "上传摸鱼小闯关数据",
            "发送弹幕", "发帖并标记为有奖励积分", "应用发放积分", "应用扣除积分");
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final String USER_FIELDS = "oId,userStatus,userName,userNickname,userAvatarURL";
    private static final String ARTICLE_FIELDS = "a.oId,a.articleAuthorId,a.articleType,a.articleAnonymous,a.articleTitle,a.articleTags,a.articlePermalink,a.articleCreateTime,a.articleUpdateTime,a.articleContent,a.articleCommentCount,a.articleViewCount,a.articleStick";
    @Inject private UserRepository users;
    @Inject private ArticleRepository articles;
    @Inject private LogsRepository logs;
    @Inject private ApiV3ContentDtoService dto;

    public JSONObject recent() {
        try {
            final JSONArray list = new JSONArray();
            for (final JSONObject user : users.select("SELECT " + USER_FIELDS + " FROM `" + users.getName()
                    + "` WHERE userStatus=? ORDER BY oId DESC LIMIT 20", UserExt.USER_STATUS_C_VALID)) list.put(dto.author(user, false));
            return new JSONObject().put("list", list);
        } catch (final RepositoryException error) { throw unavailable(); }
    }
    protected double pivot() { return ThreadLocalRandom.current().nextDouble(); }
    protected void shuffle(final List<JSONObject> rows) { Collections.shuffle(rows); }
    public JSONObject random(final JSONObject viewer, final int size, final String excluded) {
        if (size < 1 || size > 20 || excluded == null || !excluded.isEmpty() && !excluded.matches("[0-9]{1,32}")) throw ApiV3Requests.invalid();
        final double pivot = pivot();
        if (!Double.isFinite(pivot) || pivot < 0 || pivot >= 1) throw new IllegalStateException("Invalid random pivot");
        try {
            final String base = " FROM `" + articles.getName() + "` a JOIN `" + users.getName()
                    + "` u ON u.oId=a.articleAuthorId WHERE a.articleStatus<>? AND a.articleType<>?"
                    + " AND a.articleShowInList<>? AND a.articleTags<>? AND a.articleTags NOT LIKE ? AND u.userStatus IN (?,?)"
                    + (viewer == null ? " AND a.articleAnonymousView<>?" : "") + (excluded.isEmpty() ? "" : " AND a.oId<>?");
            final List<Object> args = new ArrayList<>(List.of(Article.ARTICLE_STATUS_C_INVALID, Article.ARTICLE_TYPE_C_DISCUSSION,
                    Article.ARTICLE_SHOW_IN_LIST_C_NOT, Tag.TAG_TITLE_C_SANDBOX, "B3log%", UserExt.USER_STATUS_C_VALID, UserExt.USER_STATUS_C_INVALID_LOGIN));
            if (viewer == null) args.add(Article.ARTICLE_ANONYMOUS_VIEW_C_NOT_ALLOW);
            if (!excluded.isEmpty()) args.add(excluded);
            final LinkedHashMap<String, JSONObject> selected = new LinkedHashMap<>();
            for (int segment = 0; segment < 2 && selected.size() < size; segment++) {
                final List<Object> bound = new ArrayList<>(args);
                if (segment == 0) { bound.add(pivot); bound.add(1D); } else { bound.add(0D); bound.add(pivot); }
                bound.add(size - selected.size());
                for (final JSONObject article : articles.select("SELECT " + ARTICLE_FIELDS + base + " AND a.articleRandomDouble>=? AND a.articleRandomDouble"
                        + (segment == 0 ? "<=?" : "<?") + " ORDER BY a.articleRandomDouble ASC,a.oId ASC LIMIT ?", bound.toArray())) {
                    selected.putIfAbsent(article.getString("oId"), article);
                }
            }
            final List<JSONObject> output = new ArrayList<>();
            for (final JSONObject article : selected.values()) {
                final List<JSONObject> current = users.select("SELECT " + USER_FIELDS + " FROM `" + users.getName()
                        + "` WHERE oId=? AND userStatus IN (?,?)", article.optString("articleAuthorId"),
                        UserExt.USER_STATUS_C_VALID, UserExt.USER_STATUS_C_INVALID_LOGIN);
                if (current.size() == 1) output.add(dto.summary(article, current.getFirst()));
            }
            shuffle(output);
            return new JSONObject().put("list", new JSONArray(output));
        } catch (final RepositoryException error) { throw unavailable(); }
    }
    public JSONObject logs(final int p, final int size) {
        if (p < 1 || p > Integer.MAX_VALUE / 50 || size < 1 || size > 50) throw ApiV3Requests.invalid();
        try {
            final String from = " FROM `" + logs.getName() + "` WHERE `public`=? AND BINARY `type`=? AND BINARY key3 IN ("
                    + String.join(",", Collections.nCopies(LOG_CATEGORIES.size(), "?")) + ")";
            final List<Object> args = new ArrayList<>(List.of(true, "simple")); args.addAll(LOG_CATEGORIES);
            final long total = logs.select("SELECT COUNT(*) AS total" + from, args.toArray()).getFirst().getLong("total");
            args.add(size); args.add((long) (p - 1) * size);
            final JSONArray list = new JSONArray();
            for (final JSONObject row : logs.select("SELECT oId,key1,key3" + from + " ORDER BY oId DESC LIMIT ? OFFSET ?", args.toArray())) {
                final String id = row.optString("oId"), time = row.optString("key1"), category = row.optString("key3");
                if (!id.matches("[1-9][0-9]{0,18}") || !LOG_CATEGORIES.contains(category) || time.length() != 19) throw unavailable();
                try { LocalDateTime.parse(time, LOG_TIME); } catch (final RuntimeException badTime) { throw unavailable(); }
                list.put(new JSONObject().put("id", id).put("time", time).put("category", category));
            }
            return ApiV3Requests.pagination(list, p, size, total);
        } catch (final RepositoryException error) { throw unavailable(); }
    }
    private ApiV3Exception unavailable() {
        LogManager.getLogger(getClass()).warn("Discovery extra reads unavailable");
        return new ApiV3Exception(503, "temporarily_unavailable", "读取暂不可用");
    }
}
