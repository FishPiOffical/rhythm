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

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.latke.util.URLs;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.repository.TagRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.UserTagRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本人标签设置、标签及创建者和自用关系与重放回执在同一个根事务中提交。
 */
@Service
public class ApiV3ProfileTagsService {
    @Inject
    private UserRepository users;
    @Inject
    private TagRepository tags;
    @Inject
    private UserTagRepository relations;
    @Inject
    private OptionRepository options;
    @Inject
    private ApiV3RequestRepository requests;

    protected long now() {
        return System.currentTimeMillis();
    }

    protected void checkQuota(String actor) {
        if (!ProfileWriteQuota.access(actor))
            throw new ApiV3Exception(429, "too_many_requests", "操作过于频繁，请稍后重试");
    }

    protected void validateContent(String actor, JSONObject user, String value) {
        if (CensorFactory.getTextCensor().censor(user.optString("userNickname") + "\n" + user.optString("userIntro") + "\n" + value).isBlocked()
                || CensorFactory.getTextCensor().censor(user.optString("userURL")).isBlocked())
            throw new ApiV3Exception(422, "content_rejected", "个人信息中含违规内容");
    }

    public JSONObject read(String actor) {
        id(actor);
        try {
            final var rows = users.select("SELECT userTags FROM `" + users.getName() + "` WHERE oId=? AND userStatus=0", actor);
            if (rows.size() != 1) throw ApiV3Requests.notFound();
            if (!(rows.getFirst().opt("userTags") instanceof String value) || value.length() > 128) throw unavailable();
            return new JSONObject().put("tags", value);
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        }
    }

    public JSONObject update(String actor, JSONObject body, String key) {
        id(actor);
        final List<String> input = ApiV3ProfileTagInputs.tokens(body);
        final JSONObject normalized = new JSONObject().put("tags", String.join(",", input));
        boolean[] authorized = {false};
        try (var caches = ArticlePublicationCaches.open(requests::hasTransactionBegun)) {
            final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                public Transaction beginTransaction() {
                    if (requests.hasTransactionBegun()) throw new IllegalStateException("profile tags root required");
                    return ApiV3Transactions.strict(requests.beginTransaction());
                }

                public boolean hasTransactionBegun() {
                    return requests.hasTransactionBegun();
                }

                public JSONObject getForUpdate(String id) throws RepositoryException {
                    owner(actor);
                    authorized[0] = true;
                    return requests.getForUpdate(id);
                }

                public String add(JSONObject value) throws RepositoryException {
                    return requests.add(value);
                }

                public void update(String id, JSONObject value, String... fields) throws RepositoryException {
                    requests.update(id, value, fields);
                }
            };
            final JSONObject result = ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "settings.tags", key, normalized, () -> apply(actor, input));
            if (!result.keySet().equals(java.util.Set.of("tags")) || !(result.opt("tags") instanceof String value) || value.length() > 128)
                throw unavailable();
            return new JSONObject().put("tags", value);
        } catch (ApiV3Exception error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        } finally {
            // 退出事务后刷新设置缓存；刷新失败时尝试清除缓存。
            if (authorized[0])
                try {
                    users.refreshSettingsCache(actor);
                } catch (Exception ignored) {
                    try {
                        users.evictSettingsCaches(actor);
                    } catch (Exception ignoredEviction) {
                    }
                }
        }
    }

    private JSONObject apply(String actor, List<String> input) throws Exception {
        final JSONObject user = owner(actor);
        checkQuota(actor);
        // 用持久命名空间锁串行处理 API v3 标签查找和创建，部分旧写入口不遵循此锁顺序。
        final JSONObject statistic = options.getForUpdate(Option.ID_C_STATISTIC_TAG_COUNT);
        if (statistic == null) throw unavailable();
        final List<String> titles = new ArrayList<>();
        for (String token : input) {
            reserved(user, token);
            final String canonical = canonical(token);
            ApiV3ProfileTagInputs.requireTitle(canonical);
            reserved(user, canonical);
            if (titles.stream().noneMatch(value -> value.equalsIgnoreCase(canonical))) titles.add(canonical);
        }
        final JSONObject result = ApiV3ProfileTagInputs.result(titles);
        validateContent(actor, user, result.getString("tags"));
        final List<String> ids = new ArrayList<>();
        int added = 0;
        for (String title : titles) {
            JSONObject target = exact(title, false);
            if (target == null) {
                final String tagId = Ids.genTimeMillisId();
                target = newTag(tagId, title);
                final String actualId = tags.add(target);
                if (actualId == null || !actualId.matches("[1-9][0-9]{0,18}")) throw unavailable();
                target.put("oId", actualId);
                relation(actor, actualId, Tag.TAG_TYPE_C_CREATOR);
                added++;
            }
            ids.add(target.getString("oId"));
        }
        if (added > 0) {
            final String raw = statistic.optString(Option.OPTION_VALUE);
            if (!raw.matches("[0-9]{1,10}")) throw unavailable();
            final long count = Long.parseLong(raw) + added;
            if (count > Integer.MAX_VALUE)
                throw unavailable();
            statistic.put(Option.OPTION_VALUE, String.valueOf(count));
            options.update(Option.ID_C_STATISTIC_TAG_COUNT, statistic, Option.OPTION_VALUE);
        }
        ApiV3TransactionStatements.execute("DELETE FROM `" + relations.getName() + "` WHERE user_oId=? AND type=?", actor, Tag.TAG_TYPE_C_USER_SELF);
        for (String tagId : ids) relation(actor, tagId, Tag.TAG_TYPE_C_USER_SELF);
        if (!user.getString("userTags").equals(result.getString("tags"))) {
            final long time = now();
            if (time <= 0)
                throw unavailable();
            if (ApiV3TransactionStatements.execute("UPDATE `" + users.getName() + "` SET userTags=?,userUpdateTime=? WHERE oId=? AND userStatus=0", result.getString("tags"), time, actor) != 1)
                throw unavailable();
        }
        return result;
    }

    private String canonical(String title) throws RepositoryException {
        if (Tag.containsWhiteListTags(title)) {
            final JSONObject existing = exact(title, false);
            return existing == null ? title : existing.getString("tagTitle");
        }
        final var icons = tags.select("SELECT oId,tagTitle FROM `" + tags.getName() + "` WHERE tagStatus=0 AND tagIconPath<>'' AND CHAR_LENGTH(tagTitle)>=2 AND LOCATE(LOWER(tagTitle),LOWER(?))>0 ORDER BY CHAR_LENGTH(tagTitle) DESC,oId LIMIT 129 FOR UPDATE", title);
        if (icons.size() > 128) throw unavailable();
        for (JSONObject icon : icons)
            if (StringUtils.containsIgnoreCase(title, icon.optString("tagTitle"))) return icon.getString("tagTitle");
        final String alias = Tag.normalizeAlias(title);
        if (alias != null) {
            final JSONObject existing = exact(alias, false);
            return existing == null ? alias : existing.getString("tagTitle");
        }
        final JSONObject existing = exact(title, true);
        return existing == null ? title : existing.getString("tagTitle");
    }

    private JSONObject exact(String title, boolean includeURI) throws RepositoryException {
        final String query = "SELECT oId,tagTitle,tagURI,tagStatus FROM `" + tags.getName() + "` WHERE " + (includeURI ? "tagTitle=? OR tagURI=?" : "tagTitle=?") + " ORDER BY oId LIMIT 65 FOR UPDATE";
        final var candidates = includeURI ? tags.select(query, title, title) : tags.select(query, title);
        if (candidates.size() > 64) throw unavailable();
        JSONObject found = null;
        for (JSONObject row : candidates) {
            if (!title.equalsIgnoreCase(row.optString("tagTitle")) && !(includeURI && title.equalsIgnoreCase(row.optString("tagURI"))))
                continue;
            if (found != null) throw new ApiV3Exception(409, "ambiguous_tag", "标签数据存在冲突");
            if (!(row.opt("tagStatus") instanceof Number status) || status.doubleValue() != 0)
                throw ApiV3WriteInputs.invalid();
            ApiV3ProfileTagInputs.requireTitle(row.optString("tagTitle"));
            id(row.optString("oId"));
            found = row;
        }
        return found;
    }

    private void relation(String actor, String tag, int type) throws RepositoryException {
        relations.add(new JSONObject().put("oId", Ids.genTimeMillisId()).put("user_oId", actor).put("tag_oId", tag).put("type", type));
    }

    private JSONObject owner(String actor) throws RepositoryException {
        final var rows = users.select("SELECT oId,userStatus,userRole,userTags,userNickname,userIntro,userURL FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.size() != 1 || !actor.equals(rows.getFirst().optString("oId")) || !(rows.getFirst().opt("userStatus") instanceof Number status) || status.doubleValue() != 0)
            throw ApiV3Requests.notFound();
        if (!(rows.getFirst().opt("userTags") instanceof String))
            throw unavailable();
        return rows.getFirst();
    }

    private static void reserved(JSONObject user, String title) {
        if (!Role.ROLE_ID_C_ADMIN.equals(user.optString("userRole")) && java.util.Arrays.asList(Symphonys.RESERVED_TAGS).contains(title))
            throw ApiV3WriteInputs.invalid();
    }

    private static JSONObject newTag(String id, String title) {
        final JSONObject row = new JSONObject().put("oId", id).put("tagTitle", title).put("tagURI", URLs.encode(title).toLowerCase(Locale.ROOT));
        for (String field : List.of("tagCSS", "tagDescription", "tagIconPath", "tagAd")) row.put(field, "");
        for (String field : List.of("tagReferenceCount", "tagCommentCount", "tagFollowerCount", "tagLinkCount", "tagStatus", "tagGoodCnt", "tagBadCnt", "tagShowSideAd"))
            row.put(field, 0);
        for (String field : List.of("tagSeoTitle", "tagSeoKeywords", "tagSeoDesc")) row.put(field, title);
        return row.put("tagRandomDouble", Math.random());
    }

    private static void id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
    }

    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "个人标签设置暂不可用");
    }
}
