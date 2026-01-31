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
package org.b3log.symphony.processor;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.EmojiGroup;
import org.b3log.symphony.model.EmojiGroupItem;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.service.CloudService;
import org.b3log.symphony.service.EmojiMgmtService;
import org.b3log.symphony.service.EmojiQueryService;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONArray;
import org.json.JSONObject;
import org.b3log.latke.http.Dispatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Emoji processor.
 * <ul>
 * <li>Upload emoji (/emoji/upload), POST</li>
 * <li>Get user groups (/emoji/groups), GET</li>
 * <li>Create group (/emoji/group/create), POST</li>
 * <li>Update group (/emoji/group/update), POST</li>
 * <li>Delete group (/emoji/group/delete), POST</li>
 * <li>Add emoji to group (/emoji/group/add-emoji), POST</li>
 * <li>Remove emoji from group (/emoji/group/remove-emoji), POST</li>
 * <li>Get emojis in group (/emoji/group/emojis), GET</li>
 * </ul>
 *
 * @author <a href="https://github.com/yourname">Your Name</a>
 * @version 1.0.0.0, Jan 26, 2026
 * @since 3.7.0
 */
@Singleton
public class EmojiProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(EmojiProcessor.class);

    /**
     * Emoji management service.
     */
    @Inject
    private EmojiMgmtService emojiMgmtService;

    /**
     * Emoji query service.
     */
    @Inject
    private EmojiQueryService emojiQueryService;

    @Inject
    private CloudService cloudService;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);

        final EmojiProcessor emojiProcessor = beanManager.getReference(EmojiProcessor.class);

        //获取用户分组列表
        Dispatcher.get("/emoji/groups", emojiProcessor::getUserGroups, loginCheck::handle, csrfMidware::fill);
        //获取用户分组里的表情
        Dispatcher.get("/emoji/group/emojis", emojiProcessor::getGroupEmojis, loginCheck::handle, csrfMidware::fill);
        //一键上传表情
        Dispatcher.post("/emoji/upload", emojiProcessor::uploadEmoji, loginCheck::handle, csrfMidware::check);


        //用户添加分组
        Dispatcher.post("/emoji/group/create", emojiProcessor::createGroup, loginCheck::handle, csrfMidware::check);
        //用户修改分组（名称和排序）
        Dispatcher.post("/emoji/group/update", emojiProcessor::updateGroup, loginCheck::handle, csrfMidware::check);
        //用户删除分组
        Dispatcher.post("/emoji/group/delete",emojiProcessor::deleteGroup, loginCheck::handle, csrfMidware::check);


        //用户添加表情到分组
        Dispatcher.post("/emoji/group/add-emoji",emojiProcessor::addEmojiToGroup, loginCheck::handle, csrfMidware::check);
        //用户添加url表情进分组（需在全部分组里同步一份）
        Dispatcher.post("/emoji/group/add-url-emoji",emojiProcessor::addUrlEmojiToGroup, loginCheck::handle, csrfMidware::check);
        //用户从分组删除表情(如果是全部分组删除，则所有的分组里都删，如果不是全部分组，只删除当前分组的)
        Dispatcher.post("/emoji/group/remove-emoji",emojiProcessor::removeEmojiFromGroup, loginCheck::handle, csrfMidware::check);
        //用户修改表情名字（全部分组里编辑的时候，问是否要同步修改别的分组的）
        Dispatcher.post("/emoji/emoji/update", emojiProcessor::updateEmojiItem, loginCheck::handle, csrfMidware::check);
        //迁移历史表情包
        Dispatcher.post("/emoji/emoji/migrate",emojiProcessor::migrateOldEmoji,loginCheck::handle,csrfMidware::check);

        // ====== 无需 CSRF 的 API 版（便于客户端带 apiKey 调用）======
        Dispatcher.get("/api/emoji/groups", emojiProcessor::getUserGroups, loginCheck::handle);
        Dispatcher.get("/api/emoji/group/emojis", emojiProcessor::getGroupEmojis, loginCheck::handle);
        Dispatcher.post("/api/emoji/upload", emojiProcessor::uploadEmoji, loginCheck::handle);
        Dispatcher.post("/api/emoji/group/create", emojiProcessor::createGroup, loginCheck::handle);
        Dispatcher.post("/api/emoji/group/update", emojiProcessor::updateGroup, loginCheck::handle);
        Dispatcher.post("/api/emoji/group/delete", emojiProcessor::deleteGroup, loginCheck::handle);
        Dispatcher.post("/api/emoji/group/add-emoji", emojiProcessor::addEmojiToGroup, loginCheck::handle);
        Dispatcher.post("/api/emoji/group/add-url-emoji", emojiProcessor::addUrlEmojiToGroup, loginCheck::handle);
        Dispatcher.post("/api/emoji/group/remove-emoji", emojiProcessor::removeEmojiFromGroup, loginCheck::handle);
        Dispatcher.post("/api/emoji/emoji/update", emojiProcessor::updateEmojiItem, loginCheck::handle);
        Dispatcher.post("/api/emoji/emoji/migrate", emojiProcessor::migrateOldEmoji, loginCheck::handle);
    }

    /**
     * Gets user's emoji groups.
     *
     * @param context the specified context
     */
    public void getUserGroups(final RequestContext context) {
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final String userId = currentUser.optString(Keys.OBJECT_ID);

            // Ensure user has "all" group
            JSONObject allGroup = emojiQueryService.getTypeAllGroup(userId);
            if (allGroup == null) {
                emojiMgmtService.createAllGroup(userId);
            }

            List<JSONObject> groups = emojiQueryService.getUserGroups(userId);
            List<JSONObject> resultGroups = new ArrayList<>();
            for(JSONObject group : groups){
                JSONObject object = new JSONObject();
                object.put("oId", group.optString(Keys.OBJECT_ID));
                object.put("name", group.optString(EmojiGroup.EMOJI_GROUP_NAME));
                object.put("sort", group.optInt(EmojiGroup.EMOJI_GROUP_SORT));
                object.put("type", group.optInt(EmojiGroup.EMOJI_GROUP_TYPE));
                resultGroups.add(object);
            }
            final JSONObject result = new JSONObject();
            result.put("data", resultGroups);
            result.put("code",0);
            context.renderJSON(result);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Get user groups failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("获取用户分组失败");
        }
    }


    /**
     * Gets emojis in a group.
     *
     * @param context the specified context
     */
    public void getGroupEmojis(final RequestContext context) {
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            final String groupId = context.param("groupId");

            if (StringUtils.isBlank(groupId)) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("分组id不能为空");
                return;
            }

            //先判断这个分组是不是这个用户的
            JSONObject group = emojiQueryService.getGroupById(groupId);
            if(group == null || !userId.equals(group.optString(EmojiGroup.EMOJI_GROUP_USER_ID))){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到分组");
                return;
            }

            List<JSONObject> emojis = emojiQueryService.getGroupEmojis(groupId);
            final JSONObject result = new JSONObject();
            result.put("data", emojis);
            result.put("code",0);
            context.renderJSON(result);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Get group emojis failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("获取分组表情失败");
        }
    }


    // 一键上传表情，通过url上传，只传到全部分组
    public void uploadEmoji(final RequestContext context) {
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            final String url = context.param("url");

            JSONObject group = emojiQueryService.getTypeAllGroup(userId);
            if(group == null){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到分组");
                return;
            }
            final String groupId = group.optString(Keys.OBJECT_ID);

            String emojiId = emojiMgmtService.addEmojiByUrl(url, userId);

            emojiMgmtService.addEmojiToGroup(groupId, emojiId, 0, "");

            context.renderJSON(StatusCodes.SUCC);
        } catch (Exception e) {
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("一键上传失败");
        }
    }

    /**
     * Creates an emoji group.
     *
     * @param context the specified context
     */
    public void createGroup(final RequestContext context) {
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final JSONObject requestJSONObject = context.requestJSON();
            final String groupName = requestJSONObject.optString("name");
            final int sort = requestJSONObject.optInt("sort", 0);

            if (StringUtils.isBlank(groupName)) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请填写分组名称");
                return;
            }

            //检查分组名称是否已存在


            final String userId = currentUser.optString(Keys.OBJECT_ID);

            emojiMgmtService.createGroup(userId, groupName, sort);
            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, "Create group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg(e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Create group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("创建分组失败");
        }
    }

    /**
     * Updates an emoji group.
     *
     * @param context the specified context
     */
    public void updateGroup(final RequestContext context) {
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final JSONObject requestJSONObject = context.requestJSON();
            final String groupId = requestJSONObject.optString("groupId");
            final String newName = requestJSONObject.optString("name");
            final int newSort = requestJSONObject.optInt("sort",0);

            if (StringUtils.isBlank(groupId) || StringUtils.isBlank(newName)) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("缺少参数");
                return;
            }

            // 先判断这个分组是不是这个用户的
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            JSONObject group = emojiQueryService.getGroupById(groupId);
            if(group == null || !userId.equals(group.optString(EmojiGroup.EMOJI_GROUP_USER_ID))){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到分组");
                return;
            }

            emojiMgmtService.updateGroup(groupId, newName, newSort);

            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, "Update group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg(e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Update group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("更新分组失败");
        }
    }

    /**
     * Deletes an emoji group.
     *
     * @param context the specified context
     */
    public void deleteGroup(final RequestContext context) {
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final JSONObject requestJSONObject = context.requestJSON();
            final String groupId = requestJSONObject.optString("groupId");

            if (StringUtils.isBlank(groupId)) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("分组id不能为空");
                return;
            }

            // 先判断这个分组是不是这个用户的
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            JSONObject group = emojiQueryService.getGroupById(groupId);
            if(group == null || !userId.equals(group.optString(EmojiGroup.EMOJI_GROUP_USER_ID))){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到分组");
                return;
            }

            emojiMgmtService.deleteGroup(groupId);

            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, "Delete group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg(e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Delete group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("删除分组失败");
        }
    }

    // 批量修改分组排序
    public void batchUpdateGroupSort(final RequestContext context) {
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final JSONObject requestJSONObject = context.requestJSON();
            final JSONArray groupIds = requestJSONObject.optJSONArray("ids");
            final JSONArray sorts = requestJSONObject.optJSONArray("sorts");
            if (groupIds == null || sorts == null) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("缺少参数");
                return;
            }
            if (groupIds.length() != sorts.length()) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("参数不正确");
                return;
            }
            // 判断这些分组都是这个用户的
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            List<JSONObject> groups = new ArrayList<>();
            for (int i = 0; i < groupIds.length(); i++) {
                final String groupId = groupIds.optString(i);
                JSONObject group = emojiQueryService.getGroupById(groupId);
                if(group == null || !userId.equals(group.optString(EmojiGroup.EMOJI_GROUP_USER_ID))){
                    context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到分组");
                    return;
                }
                group.put(EmojiGroup.EMOJI_GROUP_SORT, sorts.optInt(i));
                groups.add(group);
            }
            emojiMgmtService.batchUpdateGroupSort(groups);
            context.renderJSON(StatusCodes.SUCC);
        } catch (Exception e) {
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("更新失败");
        }
    }





    /**
     * Adds an emoji to a group.
     *
     * @param context the specified context
     */
    public void addEmojiToGroup(final RequestContext context) {
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final JSONObject requestJSONObject = context.requestJSON();
            final String groupId = requestJSONObject.optString("groupId");
            final String emojiId = requestJSONObject.optString("emojiId");
            final int sort = requestJSONObject.optInt("sort", 0);
            final String name = requestJSONObject.optString("name", "");

            if (StringUtils.isBlank(groupId) || StringUtils.isBlank(emojiId)) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("缺少参数");
                return;
            }

            // 先判断这个分组是不是这个用户的
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            JSONObject group = emojiQueryService.getGroupById(groupId);
            if(group == null || !userId.equals(group.optString(EmojiGroup.EMOJI_GROUP_USER_ID))){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到分组");
                return;
            }
            // 判断是否有这个表情图片
            JSONObject emoji = emojiQueryService.getEmojiById(emojiId);
            if(emoji == null ){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到表情");
                return;
            }

            //判断这个表情是否在这个分组里
            if(emojiQueryService.isEmojiInGroup(groupId, emojiId) ){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("已经在分组里了！");
                return;
            }

            emojiMgmtService.addEmojiToGroup(groupId, emojiId, sort, name);

            //如果这个分组不是全部分组，需要往全部分组也放一份
            if(group.optInt(EmojiGroup.EMOJI_GROUP_TYPE)!=EmojiGroup.EMOJI_GROUP_TYPE_ALL){
                JSONObject groupAll = emojiQueryService.getTypeAllGroup(userId);
                emojiMgmtService.addEmojiToGroup(groupAll.optString(Keys.OBJECT_ID), emojiId, sort, name);
            }


            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, "Add emoji to group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg(e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Add emoji to group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("添加表情进分组失败");
        }
    }

    // 添加url表情到分组，如果不是全部分组，需要往全部分组也放一份
    public void addUrlEmojiToGroup(final RequestContext context) {
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            final JSONObject requestJSONObject = context.requestJSON();
            final String groupId = requestJSONObject.optString("groupId");
            final String url = requestJSONObject.optString("url");
            final int sort = requestJSONObject.optInt("sort", 0);
            final String name = requestJSONObject.optString("name", "");

            // 先判断这个分组是不是这个用户的

            JSONObject group = emojiQueryService.getGroupById(groupId);
            if(group == null || !userId.equals(group.optString(EmojiGroup.EMOJI_GROUP_USER_ID))){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("分组未找到");
                return;
            }
            // 根据url 获取图片

            String emojiId = emojiMgmtService.addEmojiByUrl(url, userId);

            //判断这个表情是否在这个分组里
            if(emojiQueryService.isEmojiInGroup(groupId, emojiId) ){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("表情已存在");
                return;
            }

            emojiMgmtService.addEmojiToGroup(groupId, emojiId, sort, name);

            //如果这个分组不是全部分组，需要往全部分组也放一份
            if(group.optInt(EmojiGroup.EMOJI_GROUP_TYPE)!=EmojiGroup.EMOJI_GROUP_TYPE_ALL){
                JSONObject groupAll = emojiQueryService.getTypeAllGroup(userId);
                emojiMgmtService.addEmojiToGroup(groupAll.optString(Keys.OBJECT_ID), emojiId, sort, name);
            }

            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, "Add emoji to group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg(e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Add emoji to group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("添加表情进分组失败");
        }
    }

    /**
     * Removes an emoji from a group.
     *
     * @param context the specified context
     */
    public void removeEmojiFromGroup(final RequestContext context) {
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final JSONObject requestJSONObject = context.requestJSON();
            final String groupId = requestJSONObject.optString("groupId");
            final String emojiId = requestJSONObject.optString("emojiId");

            if (StringUtils.isBlank(groupId) || StringUtils.isBlank(emojiId)) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("缺少参数");
                return;
            }
            // 先判断这个分组是不是这个用户的
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            JSONObject group = emojiQueryService.getGroupById(groupId);
            if(group == null || !userId.equals(group.optString(EmojiGroup.EMOJI_GROUP_USER_ID))){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到分组");
                return;
            }

            // 如果是全部分组，那么从所有分组里删除
            if(group.optInt(EmojiGroup.EMOJI_GROUP_TYPE)== EmojiGroup.EMOJI_GROUP_TYPE_ALL){
                // 获取全部的分组id
                List<JSONObject> groups = emojiQueryService.getUserGroups(userId);
                for (JSONObject userGroup : groups) {
                    emojiMgmtService.removeEmojiFromGroup(userGroup.optString(Keys.OBJECT_ID), emojiId);
                }
            }else{
                emojiMgmtService.removeEmojiFromGroup(groupId, emojiId);
            }
            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, "Remove emoji from group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg(e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Remove emoji from group failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("移除表情失败");
        }
    }

    // 用户修改表情名字
    /**
     * Updates an emoji item (name and sort).
     *
     * @param context specified context
     */
    public void updateEmojiItem(final RequestContext context) {
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                final JSONObject requestJSONObject = context.requestJSON();
                currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final JSONObject requestJSONObject = context.requestJSON();
            final String emojiItemId = requestJSONObject.optString("oId");
            final String emojiGroupId = requestJSONObject.optString("groupId");
            final String name = requestJSONObject.optString("name");
            final int sort = requestJSONObject.optInt("sort");

            if (StringUtils.isBlank(emojiItemId) || StringUtils.isBlank(emojiGroupId)) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("缺少参数");
                return;
            }

            // 先判断这个分组是不是这个用户的
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            JSONObject group = emojiQueryService.getGroupById(emojiGroupId);
            if(group == null || !userId.equals(group.optString(EmojiGroup.EMOJI_GROUP_USER_ID))){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到分组");
                return;
            }


            JSONObject item = emojiQueryService.getGroupItemById(emojiGroupId,emojiItemId);
            // 判断这个表情项是否在这个分组里
            if (item==null) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到表情");
                return;
            }
            emojiMgmtService.updateEmoji(emojiItemId, name,sort);

            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, "Update emoji failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg(e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Update emoji failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("更新表情失败");
        }
    }

    //批量排序用户表情
    public void batchUpdateEmojiItemSort(final RequestContext context) {
        try {
            final JSONObject requestJSONObject = context.requestJSON();
            final String emojiGroupId = requestJSONObject.optString("groupId");
            final JSONArray groupItemIds = requestJSONObject.optJSONArray("ids");
            final JSONArray sorts = requestJSONObject.optJSONArray("sorts");
            if (StringUtils.isBlank(emojiGroupId) || groupItemIds == null
                    || sorts == null|| sorts.isEmpty() || groupItemIds.isEmpty()|| groupItemIds.length() != sorts.length()) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("缺少参数");
                return;
            }
            // 先判断这个分组是不是这个用户的
            final JSONObject currentUser = Sessions.getUser();
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            JSONObject group = emojiQueryService.getGroupById(emojiGroupId);
            if (group == null || !userId.equals(group.optString(EmojiGroup.EMOJI_GROUP_USER_ID))) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到分组");
                return;
            }

            List<JSONObject> emojiItems = new ArrayList<>();
            for (int i = 0; i < groupItemIds.length(); i++) {
                if(!emojiQueryService.isEmojiInGroup(emojiGroupId, groupItemIds.optString(i))){
                    context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("未找到表情");
                    return;
                }
                JSONObject emojiItem = emojiQueryService.getGroupItemById(emojiGroupId,groupItemIds.optString(i));
                emojiItem.put(EmojiGroupItem.EMOJI_GROUP_ITEM_SORT, sorts.optInt(i));
                emojiItems.add(emojiItem);
            }
            emojiMgmtService.batchUpdateEmojiSort( emojiItems);
            context.renderJSON(StatusCodes.SUCC);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, "Update emoji name failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg(e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Update emoji name failed", e);
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("更新排序失败");
        }
    }

    // 迁移历史表情包
    public void migrateOldEmoji(final RequestContext context){
        JSONObject currentUser = Sessions.getUser();
        try {
            final JSONObject requestJSONObject = context.requestJSON();
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        String emojiJson = cloudService.getFromCloud(userId,"emojis");
        if (StringUtils.isBlank(emojiJson)) {
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("历史表情为空，无需迁移");
            return;
        }
        try {
            JSONArray array = new JSONArray(emojiJson);
            // 找到这个用户的全部分组id
            JSONObject groupAll = emojiQueryService.getTypeAllGroup(userId);
            if(groupAll==null){
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("获取分组失败");
                return;
            }
            String groupAllId = groupAll.optString(Keys.OBJECT_ID);
            for (int i = 0; i < array.length(); i++) {
                String url = array.getString(i);
                String emojiId = emojiMgmtService.addEmojiByUrl(url, userId);

                //判断这个表情是否在这个分组里
                if(emojiQueryService.isEmojiInGroup(groupAllId, emojiId) ){
                    continue;
                }

                emojiMgmtService.addEmojiToGroup(groupAllId, emojiId, 0, "");
            }
            context.renderJSON(StatusCodes.SUCC);
        }catch (Exception e){
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("表情迁移失败");
        }

    }


}
