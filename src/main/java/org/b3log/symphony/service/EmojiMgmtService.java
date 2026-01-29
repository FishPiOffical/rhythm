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
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.annotation.Transactional;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Emoji;
import org.b3log.symphony.model.EmojiGroup;
import org.b3log.symphony.model.EmojiGroupItem;
import org.b3log.symphony.repository.EmojiGroupItemRepository;
import org.b3log.symphony.repository.EmojiGroupRepository;
import org.b3log.symphony.repository.EmojiRepository;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

/**
 * Emoji management service.
 *
 * @author <a href="https://github.com/yourname">Your Name</a>
 * @version 1.0.0.0, Jan 26, 2026
 * @since 3.7.0
 */
@Service
public class EmojiMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(EmojiMgmtService.class);

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
     * Creates the default "all" group for a user.
     *
     * @param userId the user id
     * @return group id
     * @throws ServiceException service exception
     */
    @Transactional
    public String createAllGroup(final String userId) throws ServiceException {
        try {
            // Check if "all" group already exists
            JSONObject existingGroup = emojiGroupRepository.getAllGroup(userId);
            if (existingGroup != null) {
                return existingGroup.optString(EmojiGroup.EMOJI_GROUP_ID);
            }

            // Create "all" group
            JSONObject group = new JSONObject();
            group.put(EmojiGroup.EMOJI_GROUP_USER_ID, userId);
            group.put(EmojiGroup.EMOJI_GROUP_NAME, "全部");
            group.put(EmojiGroup.EMOJI_GROUP_SORT, 0);
            group.put(EmojiGroup.EMOJI_GROUP_CREATE_TIME, System.currentTimeMillis());
            group.put(EmojiGroup.EMOJI_GROUP_UPDATE_TIME, System.currentTimeMillis());
            group.put(EmojiGroup.EMOJI_GROUP_TYPE, EmojiGroup.EMOJI_GROUP_TYPE_ALL);

            return emojiGroupRepository.add(group);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Create all group failed", e);
            throw new ServiceException(e);
        }
    }






    /**
     * Creates a custom emoji group for a user.
     *
     * @param userId the user id
     * @param groupName the group name
     * @param sort the sort order
     * @return group id
     * @throws ServiceException service exception
     */
    @Transactional
    public String createGroup(final String userId, final String groupName, final int sort) throws ServiceException {
        try {
            // Check if group name already exists for this user
            JSONObject existingGroup = emojiGroupRepository.getByUserIdAndName(userId, groupName);
            if (existingGroup != null) {
                throw new ServiceException("分组名已存在");
            }

            JSONObject group = new JSONObject();
            group.put(EmojiGroup.EMOJI_GROUP_USER_ID, userId);
            group.put(EmojiGroup.EMOJI_GROUP_NAME, groupName);
            group.put(EmojiGroup.EMOJI_GROUP_SORT, sort);
            group.put(EmojiGroup.EMOJI_GROUP_CREATE_TIME, System.currentTimeMillis());
            group.put(EmojiGroup.EMOJI_GROUP_UPDATE_TIME, System.currentTimeMillis());
            group.put(EmojiGroup.EMOJI_GROUP_TYPE, EmojiGroup.EMOJI_GROUP_TYPE_CUSTOM);
            return   emojiGroupRepository.add(group);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Create group failed", e);
            throw new ServiceException(e);
        }
    }









    /**
     * Updates a group name.
     *
     * @param groupId the group id
     * @param newName the new name
     * @throws ServiceException service exception
     */
    @Transactional
    public void updateGroupName(final String groupId, final String newName) throws ServiceException {
        try {
            JSONObject group = emojiGroupRepository.getById(groupId);
            if (group == null) {
                throw new ServiceException("分组不存在");
            }

            // Cannot rename "all" group
            if (group.optInt(EmojiGroup.EMOJI_GROUP_TYPE) == EmojiGroup.EMOJI_GROUP_TYPE_ALL) {
                throw new ServiceException("不能更新 全部 分组");
            }

            // Check if new name already exists
            String userId = group.optString(EmojiGroup.EMOJI_GROUP_USER_ID);
            JSONObject existingGroup = emojiGroupRepository.getByUserIdAndName(userId, newName);
            if (existingGroup != null && !existingGroup.optString(EmojiGroup.EMOJI_GROUP_ID).equals(groupId)) {
                throw new ServiceException("分组名已存在");
            }

            group.put(EmojiGroup.EMOJI_GROUP_NAME, newName);
            group.put(EmojiGroup.EMOJI_GROUP_UPDATE_TIME, System.currentTimeMillis());
            emojiGroupRepository.update(group.optString(Keys.OBJECT_ID), group);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "更新分组名失败", e);
            throw new ServiceException(e);
        }
    }

    // 更新分组排序
    @Transactional
    public void updateGroupSort(final String groupId, final int newSort) throws ServiceException {
        try {
            JSONObject group = emojiGroupRepository.getById(groupId);
            if (group == null) {
                throw new ServiceException("分组不存在");
            }

            // Cannot rename "all" group
            if (group.optInt(EmojiGroup.EMOJI_GROUP_TYPE) == EmojiGroup.EMOJI_GROUP_TYPE_ALL) {
                throw new ServiceException("不能更新 全部 分组");
            }

            group.put(EmojiGroup.EMOJI_GROUP_SORT, newSort);
            group.put(EmojiGroup.EMOJI_GROUP_UPDATE_TIME, System.currentTimeMillis());
            emojiGroupRepository.update(group.optString(Keys.OBJECT_ID), group);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Update group sort failed", e);
            throw new ServiceException(e);
        }
    }

    // 批量修改分组排序
    @Transactional
    public void batchUpdateGroupSort(final List<JSONObject> groups) throws ServiceException {
        try {
            for (JSONObject group : groups) {
                emojiGroupRepository.update(group.optString(Keys.OBJECT_ID), group);
            }
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Batch update group sort failed", e);
            throw new ServiceException(e);
        }
    }



    /**
     * Adds an emoji to a group.
     *
     * @param groupId the group id
     * @param emojiId the emoji id
     * @param sort the sort order within group
     * @param name the custom name for this emoji in the group (can be empty)
     * @throws ServiceException service exception
     */
    @Transactional
    public void addEmojiToGroup(final String groupId, final String emojiId, final int sort, final String name) throws ServiceException {
        try {
            // Check if emoji already in group
            JSONObject existingItem = emojiGroupItemRepository.getByGroupIdAndEmojiId(groupId, emojiId);
            if (existingItem != null) {
                return; // Already in group
            }

            JSONObject groupItem = new JSONObject();
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_GROUP_ID, groupId);
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_EMOJI_ID, emojiId);
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_SORT, sort);
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_NAME, name);
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_CREATE_TIME, System.currentTimeMillis());

            emojiGroupItemRepository.add(groupItem);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Add emoji to group failed", e);
            throw new ServiceException(e);
        }
    }

    // 通过url 添加到emoji
    @Transactional
    public String addEmojiByUrl( final String url,final String uploaderId ) throws ServiceException {
        try {
            // 检查url是否存在
            JSONObject existingEmoji = emojiRepository.getByUrl(url);
            if (existingEmoji != null) {
                return existingEmoji.optString(Keys.OBJECT_ID);
            }
            JSONObject object = new JSONObject();
            object.put(Emoji.EMOJI_URL, url);
            object.put(Emoji.EMOJI_UPLOADER_ID, uploaderId);
            object.put(Emoji.EMOJI_CREATE_TIME, System.currentTimeMillis());
            return emojiRepository.add(object);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Add emoji to group failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * Removes an emoji from a group.
     *
     * @param groupId the group id
     * @param emojiId the emoji id
     * @throws ServiceException service exception
     */
    @Transactional
    public void removeEmojiFromGroup(final String groupId, final String emojiId) throws ServiceException {
        try {
            emojiGroupItemRepository.removeByGroupIdAndEmojiId(groupId, emojiId);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Remove emoji from group failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * Deletes a group (cannot delete "all" group).
     *
     * @param groupId the group id
     * @throws ServiceException service exception
     */
    @Transactional
    public void deleteGroup(final String groupId) throws ServiceException {
        try {
            JSONObject group = emojiGroupRepository.getById(groupId);
            if (group == null) {
                throw new ServiceException("分组不存在");
            }

            // Cannot delete "all" group
            if (group.optInt(EmojiGroup.EMOJI_GROUP_TYPE) == EmojiGroup.EMOJI_GROUP_TYPE_ALL) {
                throw new ServiceException("不能删除 全部 分组");
            }

            Transaction transaction = emojiGroupRepository.beginTransaction();
            try {
                // Remove all emojis from the group
                emojiGroupItemRepository.removeByGroupId(groupId);
                // Remove the group
                emojiGroupRepository.remove(groupId);
                transaction.commit();
            } catch (final Exception e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Delete group failed", e);
            throw new ServiceException(e);
        }
    }

    //更新分组内emoji的名字
    @Transactional
    public void updateEmojiName(final String groupId, final String groupEmojiId, final String newName) throws ServiceException {
        try {
            JSONObject groupItem = emojiGroupItemRepository.getByGroupIdAndEmojiId(groupId, groupEmojiId);
            if (groupItem == null) {
                throw new ServiceException("表情不存在");
            }
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_NAME, newName);
            emojiGroupItemRepository.update(groupItem.optString(Keys.OBJECT_ID), groupItem);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Update emoji name failed", e);
            throw new ServiceException(e);
        }
    }

    // 更新分组内emoji的排序
    @Transactional
    public void updateEmojiSort(final String groupId, final String groupEmojiId, final int newSort) throws ServiceException {
        try {
            JSONObject groupItem = emojiGroupItemRepository.getByGroupIdAndEmojiId(groupId, groupEmojiId);
            if (groupItem == null) {
                throw new ServiceException("表情不存在");
            }
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_SORT, newSort);
            emojiGroupItemRepository.update(groupItem.optString(Keys.OBJECT_ID), groupItem);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Update emoji name failed", e);
            throw new ServiceException(e);
        }
    }

    // 批量更新分组内的表情排序
    @Transactional
    public void batchUpdateEmojiSort(final List<JSONObject> groupItems) throws ServiceException {
        try {
            for (JSONObject groupItem : groupItems) {
                emojiGroupItemRepository.update(groupItem.optString(Keys.OBJECT_ID), groupItem);
            }
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Batch update emoji sort failed", e);
            throw new ServiceException(e);
        }
    }



}
