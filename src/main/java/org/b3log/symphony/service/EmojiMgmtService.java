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


import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
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
import org.b3log.symphony.model.EmojiShare;
import org.b3log.symphony.repository.EmojiGroupItemRepository;
import org.b3log.symphony.repository.EmojiGroupRepository;
import org.b3log.symphony.repository.EmojiRepository;
import org.b3log.symphony.repository.EmojiShareRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Emoji management service.
 *
 * @author <a href="https://github.com/yourname">Your Name</a>
 * @version 1.0.0.0, Jan 26, 2026
 * @since 3.7.0
 */
@Service
public class EmojiMgmtService {

    private static final int NAME_MAX_LEN = 20;
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s_\\-。，！？!?:：；;（）()\\[\\]{}·]+$");
    private static final Pattern SHARE_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{8,16}$");

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

    @Inject
    private EmojiShareRepository emojiShareRepository;


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
    public void updateGroup(final String groupId, final String newName,final int newSort) throws ServiceException {
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
            if (existingGroup != null && !existingGroup.optString(Keys.OBJECT_ID).equals(groupId)) {
                throw new ServiceException("分组名已存在");
            }

            group.put(EmojiGroup.EMOJI_GROUP_NAME, newName);
            group.put(EmojiGroup.EMOJI_GROUP_SORT, newSort);
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

            int finalSort = sort;
            if (sort <= 0) {
                Integer maxSort = emojiGroupItemRepository.getMaxSortInGroup(groupId);
                finalSort = maxSort == null ? 1 : maxSort + 1;
            }

            JSONObject groupItem = new JSONObject();
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_GROUP_ID, groupId);
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_EMOJI_ID, emojiId);
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_SORT, finalSort);
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
            String url1 = url.split("\\?")[0];
            // 检查url是否存在
            JSONObject existingEmoji = emojiRepository.getByUrl(url1);
            if (existingEmoji != null) {
                return existingEmoji.optString(Keys.OBJECT_ID);
            }
            JSONObject object = new JSONObject();
            object.put(Emoji.EMOJI_URL, url1);
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
    public void updateEmoji(final String groupEmojiId, final String newName, final int newSort) throws ServiceException {
        try {
            JSONObject groupItem = emojiGroupItemRepository.getByItemId(groupEmojiId);
            if (groupItem == null) {
                throw new ServiceException("表情不存在");
            }
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_NAME, newName);
            groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_SORT, newSort);
            emojiGroupItemRepository.update(groupEmojiId, groupItem);
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

    public JSONObject createShareSnapshot(final String userId, final String groupId) throws ServiceException {
        final Transaction transaction = emojiShareRepository.beginTransaction();
        try {
            final JSONObject group = emojiGroupRepository.getById(groupId);
            if (group == null || !userId.equals(group.optString(EmojiGroup.EMOJI_GROUP_USER_ID))) {
                throw new ServiceException("分组不存在或无权分享");
            }
            if (group.optInt(EmojiGroup.EMOJI_GROUP_TYPE) == EmojiGroup.EMOJI_GROUP_TYPE_ALL) {
                throw new ServiceException("请切换到具体分组后再分享");
            }

            final JSONArray snapshot = buildSnapshot(groupId);
            if (snapshot.length() == 0) {
                throw new ServiceException("当前分组没有可分享的表情");
            }

            final long now = System.currentTimeMillis();
            final String shareCode = generateUniqueShareCode();
            final JSONObject share = new JSONObject();
            share.put(EmojiShare.SHARE_CODE, shareCode);
            share.put(EmojiShare.OWNER_USER_ID, userId);
            share.put(EmojiShare.GROUP_ID, groupId);
            share.put(EmojiShare.GROUP_NAME, sanitizeGroupName(group.optString(EmojiGroup.EMOJI_GROUP_NAME), "未命名分组"));
            share.put(EmojiShare.SNAPSHOT, snapshot.toString());
            share.put(EmojiShare.EMOJI_COUNT, snapshot.length());
            share.put(EmojiShare.IMPORTED_COUNT, 0);
            share.put(EmojiShare.CREATED_TIME, now);
            emojiShareRepository.add(share);
            transaction.commit();

            final JSONObject result = new JSONObject();
            result.put("shareCode", shareCode);
            result.put("groupName", share.optString(EmojiShare.GROUP_NAME));
            result.put("emojiCount", snapshot.length());
            result.put("createdTime", now);
            return result;
        } catch (final ServiceException e) {
            rollbackQuietly(transaction);
            throw e;
        } catch (final Exception e) {
            rollbackQuietly(transaction);
            LOGGER.log(Level.ERROR, "Create emoji share snapshot failed", e);
            throw new ServiceException("生成分享码失败");
        }
    }

    public JSONObject importShareSnapshot(final String userId, final String shareCode) throws ServiceException {
        final String normalizedShareCode = normalizeShareCode(shareCode);
        if (StringUtils.isBlank(normalizedShareCode) || !SHARE_CODE_PATTERN.matcher(normalizedShareCode).matches()) {
            throw new ServiceException("分享码格式不正确");
        }

        final Transaction transaction = emojiShareRepository.beginTransaction();
        try {
            final JSONObject share = emojiShareRepository.getByShareCode(normalizedShareCode);
            if (share == null) {
                throw new ServiceException("分享码不存在");
            }

            final String snapshotText = share.optString(EmojiShare.SNAPSHOT);
            if (StringUtils.isBlank(snapshotText)) {
                throw new ServiceException("该分享码不包含可导入内容");
            }

            final JSONArray snapshot = new JSONArray(snapshotText);
            if (snapshot.length() == 0) {
                throw new ServiceException("该分享码不包含可导入内容");
            }

            final String sourceGroupName = sanitizeGroupName(share.optString(EmojiShare.GROUP_NAME), "导入表情集");
            final String importGroupName = buildImportedGroupName(userId, sourceGroupName);
            final String allGroupId = ensureAllGroup(userId);
            final String importGroupId = addCustomGroup(userId, importGroupName, getNextGroupSort(userId));

            int importedCount = 0;
            for (int i = 0; i < snapshot.length(); i++) {
                final JSONObject item = snapshot.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                final String url = normalizeUrl(item.optString("url"));
                if (StringUtils.isBlank(url)) {
                    continue;
                }

                final String emojiId = findOrCreateEmoji(url, userId);
                if (StringUtils.isBlank(emojiId)) {
                    continue;
                }

                final String emojiName = sanitizeEmojiName(item.optString("name"));
                final int sort = item.optInt("sort", 0);
                addEmojiToGroupInternal(importGroupId, emojiId, sort, emojiName);
                addEmojiToGroupInternal(allGroupId, emojiId, sort, emojiName);
                importedCount++;
            }

            if (importedCount == 0) {
                throw new ServiceException("分享快照中没有可导入的表情");
            }

            share.put(EmojiShare.IMPORTED_COUNT, share.optInt(EmojiShare.IMPORTED_COUNT) + 1);
            emojiShareRepository.update(share.optString(Keys.OBJECT_ID), share);
            transaction.commit();

            final JSONObject result = new JSONObject();
            result.put("groupId", importGroupId);
            result.put("groupName", importGroupName);
            result.put("emojiCount", importedCount);
            result.put("sourceGroupName", sourceGroupName);
            result.put("shareCode", normalizedShareCode);
            return result;
        } catch (final ServiceException e) {
            rollbackQuietly(transaction);
            throw e;
        } catch (final Exception e) {
            rollbackQuietly(transaction);
            LOGGER.log(Level.ERROR, "Import emoji share snapshot failed", e);
            throw new ServiceException("导入分享码失败");
        }
    }

    private JSONArray buildSnapshot(final String groupId) throws RepositoryException {
        final JSONArray snapshot = new JSONArray();
        final List<JSONObject> groupItems = emojiGroupItemRepository.getByGroupId(groupId);
        for (JSONObject groupItem : groupItems) {
            final JSONObject emoji = emojiRepository.getById(groupItem.optString(EmojiGroupItem.EMOJI_GROUP_ITEM_EMOJI_ID));
            if (emoji == null) {
                continue;
            }
            final String url = normalizeUrl(emoji.optString(Emoji.EMOJI_URL));
            if (StringUtils.isBlank(url)) {
                continue;
            }

            final JSONObject item = new JSONObject();
            item.put("url", url);
            item.put("name", sanitizeEmojiName(groupItem.optString(EmojiGroupItem.EMOJI_GROUP_ITEM_NAME)));
            item.put("sort", groupItem.optInt(EmojiGroupItem.EMOJI_GROUP_ITEM_SORT));
            snapshot.put(item);
        }
        return snapshot;
    }

    private String generateUniqueShareCode() throws RepositoryException, ServiceException {
        for (int i = 0; i < 50; i++) {
            final String shareCode = RandomStringUtils.randomAlphanumeric(12).toUpperCase(Locale.ROOT);
            if (emojiShareRepository.getByShareCode(shareCode) == null) {
                return shareCode;
            }
        }
        throw new ServiceException("生成分享码失败，请稍后重试");
    }

    private String ensureAllGroup(final String userId) throws RepositoryException {
        JSONObject allGroup = emojiGroupRepository.getAllGroup(userId);
        if (allGroup != null) {
            return allGroup.optString(Keys.OBJECT_ID);
        }
        return addAllGroup(userId);
    }

    private String addAllGroup(final String userId) throws RepositoryException {
        final long now = System.currentTimeMillis();
        final JSONObject group = new JSONObject();
        group.put(EmojiGroup.EMOJI_GROUP_USER_ID, userId);
        group.put(EmojiGroup.EMOJI_GROUP_NAME, "全部");
        group.put(EmojiGroup.EMOJI_GROUP_SORT, 0);
        group.put(EmojiGroup.EMOJI_GROUP_CREATE_TIME, now);
        group.put(EmojiGroup.EMOJI_GROUP_UPDATE_TIME, now);
        group.put(EmojiGroup.EMOJI_GROUP_TYPE, EmojiGroup.EMOJI_GROUP_TYPE_ALL);
        return emojiGroupRepository.add(group);
    }

    private String addCustomGroup(final String userId, final String groupName, final int sort) throws RepositoryException {
        final long now = System.currentTimeMillis();
        final JSONObject group = new JSONObject();
        group.put(EmojiGroup.EMOJI_GROUP_USER_ID, userId);
        group.put(EmojiGroup.EMOJI_GROUP_NAME, groupName);
        group.put(EmojiGroup.EMOJI_GROUP_SORT, sort);
        group.put(EmojiGroup.EMOJI_GROUP_CREATE_TIME, now);
        group.put(EmojiGroup.EMOJI_GROUP_UPDATE_TIME, now);
        group.put(EmojiGroup.EMOJI_GROUP_TYPE, EmojiGroup.EMOJI_GROUP_TYPE_CUSTOM);
        return emojiGroupRepository.add(group);
    }

    private int getNextGroupSort(final String userId) throws RepositoryException {
        final List<JSONObject> groups = emojiGroupRepository.getByUserId(userId);
        if (groups == null || groups.isEmpty()) {
            return 1;
        }
        return groups.get(groups.size() - 1).optInt(EmojiGroup.EMOJI_GROUP_SORT, 0) + 1;
    }

    private String buildImportedGroupName(final String userId, final String sourceName) throws RepositoryException {
        final String baseName = sanitizeGroupName(sourceName, "导入表情集");
        if (emojiGroupRepository.getByUserIdAndName(userId, baseName) == null) {
            return baseName;
        }

        for (int i = 1; i <= 999; i++) {
            final String suffix = i == 1 ? "-导入" : "-导入" + i;
            final String candidate = trimForSuffix(baseName, suffix, NAME_MAX_LEN) + suffix;
            if (emojiGroupRepository.getByUserIdAndName(userId, candidate) == null) {
                return candidate;
            }
        }
        throw new RepositoryException("No available imported group name");
    }

    private String findOrCreateEmoji(final String url, final String uploaderId) throws RepositoryException {
        final JSONObject existingEmoji = emojiRepository.getByUrl(url);
        if (existingEmoji != null) {
            return existingEmoji.optString(Keys.OBJECT_ID);
        }

        final JSONObject object = new JSONObject();
        object.put(Emoji.EMOJI_URL, url);
        object.put(Emoji.EMOJI_UPLOADER_ID, uploaderId);
        object.put(Emoji.EMOJI_CREATE_TIME, System.currentTimeMillis());
        return emojiRepository.add(object);
    }

    private void addEmojiToGroupInternal(final String groupId, final String emojiId, final int sort, final String name)
            throws RepositoryException {
        final JSONObject existingItem = emojiGroupItemRepository.getByGroupIdAndEmojiId(groupId, emojiId);
        if (existingItem != null) {
            return;
        }

        int finalSort = sort;
        if (finalSort <= 0) {
            final Integer maxSort = emojiGroupItemRepository.getMaxSortInGroup(groupId);
            finalSort = maxSort == null ? 1 : maxSort + 1;
        }

        final JSONObject groupItem = new JSONObject();
        groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_GROUP_ID, groupId);
        groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_EMOJI_ID, emojiId);
        groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_SORT, finalSort);
        groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_NAME, sanitizeEmojiName(name));
        groupItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_CREATE_TIME, System.currentTimeMillis());
        emojiGroupItemRepository.add(groupItem);
    }

    private String sanitizeGroupName(final String groupName, final String fallback) {
        String normalized = StringUtils.defaultIfBlank(groupName, fallback).trim();
        if (normalized.length() > NAME_MAX_LEN) {
            normalized = normalized.substring(0, NAME_MAX_LEN);
        }
        if (!SAFE_NAME_PATTERN.matcher(normalized).matches()) {
            return fallback;
        }
        return normalized;
    }

    private String sanitizeEmojiName(final String emojiName) {
        if (StringUtils.isBlank(emojiName)) {
            return "";
        }
        final String normalized = emojiName.trim();
        if (normalized.length() > NAME_MAX_LEN) {
            return "";
        }
        if (!SAFE_NAME_PATTERN.matcher(normalized).matches()) {
            return "";
        }
        return normalized;
    }

    private String trimForSuffix(final String baseName, final String suffix, final int maxLen) {
        if (baseName.length() + suffix.length() <= maxLen) {
            return baseName;
        }
        final int availableLen = Math.max(1, maxLen - suffix.length());
        return baseName.substring(0, availableLen);
    }

    private String normalizeShareCode(final String shareCode) {
        return StringUtils.trimToEmpty(shareCode).toUpperCase(Locale.ROOT);
    }

    private String normalizeUrl(final String url) {
        if (StringUtils.isBlank(url)) {
            return "";
        }
        return url.trim().split("\\?")[0];
    }

    private void rollbackQuietly(final Transaction transaction) {
        if (transaction != null && transaction.isActive()) {
            transaction.rollback();
        }
    }



}
