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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Emoji;
import org.b3log.symphony.model.EmojiGroup;
import org.b3log.symphony.model.EmojiGroupItem;
import org.b3log.symphony.repository.EmojiGroupItemRepository;
import org.b3log.symphony.repository.EmojiGroupRepository;
import org.b3log.symphony.repository.EmojiRepository;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Emoji query service.
 *
 * @author <a href="https://github.com/yourname">Your Name</a>
 * @version 1.0.0.0, Jan 26, 2026
 * @since 3.7.0
 */
@Service
public class EmojiQueryService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(EmojiQueryService.class);

    /**
     * Emoji repository.
     */
    @Inject
    private EmojiRepository emojiRepository;

    /**
     * Emoji group repository.
     */
    @Inject
    private EmojiGroupRepository emojiGroupRepository;

    /**
     * Emoji group item repository.
     */
    @Inject
    private EmojiGroupItemRepository emojiGroupItemRepository;




    /**
     * Gets user's all groups.
     *
     * @param userId the user id
     * @return group list
     */
    public List<JSONObject> getUserGroups(final String userId) {
        try {
            return emojiGroupRepository.getByUserId(userId);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Get user groups failed", e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets user's "all" group.
     *
     * @param userId the user id
     * @return "all" group object, returns {@code null} if not found
     */
    public JSONObject getAllGroup(final String userId) {
        try {
            return emojiGroupRepository.getAllGroup(userId);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Get all group failed", e);
            return null;
        }
    }

    /**
     * Gets emojis in a group.
     *
     * @param groupId the group id
     * @return emoji list with item names
     */
    public List<JSONObject> getGroupEmojis(final String groupId) {
        try {

            List<JSONObject> groupItems = emojiGroupItemRepository.getByGroupId(groupId);
            if (groupItems.isEmpty()) {
                return new ArrayList<>();
            }

            // Merge emoji info with group item name
            List<JSONObject> result = new ArrayList<>();
            for (int i = 0; i < groupItems.size(); i++) {
                JSONObject emoji = emojiRepository.getById(groupItems.get(i).optString(EmojiGroupItem.EMOJI_GROUP_ITEM_EMOJI_ID));
                JSONObject groupItem = groupItems.get(i);
                JSONObject item = new JSONObject();
                item.put("oId", groupItem.optString(Keys.OBJECT_ID));
                item.put("emojiId", emoji.optString(Keys.OBJECT_ID));
                item.put("name", groupItem.optString(EmojiGroupItem.EMOJI_GROUP_ITEM_NAME));
                item.put("sort", groupItem.optInt(EmojiGroupItem.EMOJI_GROUP_ITEM_SORT));
                item.put("url",emoji.optString(Emoji.EMOJI_URL));
                result.add(item);
            }

            return result;
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Get group emojis failed", e);
            return new ArrayList<>();
        }
    }

    /**
     * Checks if an emoji is in a group.
     *
     * @param groupId the group id
     * @param emojiId the emoji id
     * @return {@code true} if exists, {@code false} otherwise
     */
    public boolean isEmojiInGroup(final String groupId, final String emojiId) {
        try {
            JSONObject item = emojiGroupItemRepository.getByGroupIdAndEmojiId(groupId, emojiId);
            return item != null;
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Check emoji in group failed", e);
            return false;
        }
    }

    // 根据emojiId 获取表情图片
    public JSONObject getEmojiById(final String emojiId) {
        try {
            return emojiRepository.getById(emojiId);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Get emoji by id failed", e);
            return null;
        }
    }

    // 根据url获取表情图片
    public JSONObject getEmojiByUrl(final String url) {
        try {
            return emojiRepository.getByUrl(url);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Get emoji by url failed", e);
            return null;
        }
    }

    /**
     * Gets group by id.
     *
     * @param groupId the group id
     * @return group object
     */
    public JSONObject getGroupById(final String groupId) {
        try {
            return emojiGroupRepository.getById(groupId);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Get group by id failed", e);
            return null;
        }
    }

    // 根据emojiItemId 获取emojiItem
    public JSONObject getGroupItemById(final String groupId,final String emojiItemId) {
        try {
            return emojiGroupItemRepository.getByGroupIdAndItemId(groupId,emojiItemId);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Get emoji item by id failed", e);
            return null;
        }
    }
}
