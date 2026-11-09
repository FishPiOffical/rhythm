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
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.EmotionRepository;
import org.b3log.symphony.repository.EmojiGroupRepository;
import org.b3log.symphony.repository.EmojiGroupItemRepository;
import org.b3log.symphony.repository.EmojiRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3CommunityDto;
import org.b3log.symphony.util.ApiV3EmotionOptions;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * GET 只投影数据库记录，跳过默认分组创建和图片地址访问。
 */
@Service
public class ApiV3EmotionQueryService {
    @Inject
    private UserRepository users;
    @Inject
    private EmotionRepository emotions;
    @Inject
    private EmojiGroupRepository groups;
    @Inject
    private EmojiGroupItemRepository items;
    @Inject
    private EmojiRepository emojis;

    @FunctionalInterface
    private interface Read {
        JSONObject run() throws RepositoryException;
    }

    private JSONObject read(final Read action) {
        try {
            return action.run();
        } catch (final RepositoryException e) {
            LogManager.getLogger(getClass()).warn("Emoji settings read unavailable");
            throw new ApiV3Exception(503, "temporarily_unavailable", "表情设置暂不可用");
        }
    }

    private void requireActor(final String actor) throws RepositoryException {
        id(actor);
        if (users.select("SELECT oId FROM `" + users.getName() + "` WHERE oId=? AND userStatus=?", actor,
                UserExt.USER_STATUS_C_VALID).size() != 1) throw ApiV3Requests.notFound();
    }

    private static void id(final String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,31}")) throw ApiV3Requests.invalid();
    }

    private static void pagination(final int page, final int size) {
        if (page < 1 || page > Integer.MAX_VALUE / 50 || size < 1 || size > 50) throw ApiV3Requests.invalid();
    }

    private static String bounded(final String value, final int max) {
        return value.length() > max || value.codePoints().anyMatch(Character::isISOControl) ? "" : value;
    }

    public JSONObject emotions(final String actor) {
        return read(() -> {
            requireActor(actor);
            final List<JSONObject> rows = emotions.select("SELECT emotionContent FROM `" + emotions.getName()
                    + "` WHERE emotionUserId=? AND emotionType=0 ORDER BY emotionSort ASC,oId ASC LIMIT 4097", actor);
            if (rows.size() > 4096) throw new ApiV3Exception(503, "temporarily_unavailable", "表情设置暂不可用");
            final JSONArray aliases = new JSONArray();
            boolean hasContent = false;
            for (final JSONObject row : rows) {
                final String value = row.optString("emotionContent");
                if (!value.isBlank()) hasContent = true;
                if (!value.isBlank() && value.length() <= 64 && value.codePoints().noneMatch(Character::isISOControl))
                    aliases.put(value);
            }
            if (!hasContent) for (final String value : ApiV3EmotionOptions.DEFAULTS.split(",")) aliases.put(value);
            return new JSONObject().put("emotions", aliases);
        });
    }

    public JSONObject groups(final String actor, final int page, final int size) {
        pagination(page, size);
        return read(() -> {
            requireActor(actor);
            final String table = "`" + groups.getName() + "`";
            final long total = groups.select("SELECT COUNT(*) AS total FROM " + table + " WHERE emojiGroupUserId=?", actor).getFirst().optLong("total");
            final List<JSONObject> rows = groups.select("SELECT oId,emojiGroupName,emojiGroupSort,emojiGroupType FROM " + table
                    + " WHERE emojiGroupUserId=? ORDER BY emojiGroupSort ASC,oId ASC LIMIT ? OFFSET ?", actor, size, (page - 1) * size);
            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows)
                list.put(new JSONObject().put("id", row.optString("oId"))
                        .put("name", bounded(row.optString("emojiGroupName"), 64)).put("sort", row.optInt("emojiGroupSort")).put("type", row.optInt("emojiGroupType")));
            return ApiV3Requests.pagination(list, page, size, total);
        });
    }

    public JSONObject items(final String actor, final String groupId, final int page, final int size) {
        id(groupId);
        pagination(page, size);
        return read(() -> {
            requireActor(actor);
            if (groups.select("SELECT oId FROM `" + groups.getName() + "` WHERE oId=? AND emojiGroupUserId=?", groupId, actor).size() != 1)
                throw ApiV3Requests.notFound();
            // 数据和总数查询都带所有者条件，确保先前的归属读取失效时也不会越权。
            final String from = " FROM `" + items.getName() + "` i JOIN `" + groups.getName()
                    + "` g ON g.oId=i.emojiGroupItemGroupId JOIN `" + emojis.getName()
                    + "` e ON e.oId=i.emojiGroupItemEmojiId WHERE g.oId=? AND g.emojiGroupUserId=? AND e.emojiStatus=0";
            final long total = items.select("SELECT COUNT(*) AS total" + from, groupId, actor).getFirst().optLong("total");
            final List<JSONObject> rows = items.select("SELECT i.oId,e.oId AS emojiId,i.emojiGroupItemName,i.emojiGroupItemSort,e.emojiUrl" + from
                    + " ORDER BY i.emojiGroupItemSort ASC,i.oId ASC LIMIT ? OFFSET ?", groupId, actor, size, (page - 1) * size);
            final JSONArray list = new JSONArray();
            for (final JSONObject row : rows)
                list.put(new JSONObject().put("id", row.optString("oId")).put("emojiId", row.optString("emojiId"))
                        .put("name", bounded(row.optString("emojiGroupItemName"), 255)).put("sort", row.optInt("emojiGroupItemSort"))
                        .put("url", ApiV3CommunityDto.safeUrl(row.optString("emojiUrl"))));
            return ApiV3Requests.pagination(list, page, size, total);
        });
    }
}
