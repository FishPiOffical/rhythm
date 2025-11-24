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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.*;
import org.b3log.latke.util.Crypts;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.channel.ChatChannel;
import org.b3log.symphony.processor.channel.UserChannel;
import org.b3log.symphony.processor.middleware.ApiCheckMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.repository.ChatInfoRepository;
import org.b3log.symphony.repository.ChatUnreadRepository;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class ChatProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(ChatProcessor.class);

    /**
     * Chat list cache. Key: userId, Value: chat list data
     */
    private static final Cache<String, List<JSONObject>> CHAT_LIST_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    @Inject
    private ChatUnreadRepository chatUnreadRepository;

    @Inject
    private ChatInfoRepository chatInfoRepository;

    @Inject
    private ChatListService chatListService;

    @Inject
    private DataModelService dataModelService;

    @Inject
    private UserQueryService userQueryService;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final ApiCheckMidware apiCheck = beanManager.getReference(ApiCheckMidware.class);
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);

        final ChatProcessor chatProcessor = beanManager.getReference(ChatProcessor.class);
        Dispatcher.get("/chat", chatProcessor::showChat, loginCheck::handle);
        Dispatcher.get("/chat/has-unread", chatProcessor::hasUnreadChatMessage, apiCheck::handle);
        Dispatcher.get("/chat/get-list", chatProcessor::getList, apiCheck::handle);
        Dispatcher.get("/chat/get-message", chatProcessor::getMessage, apiCheck::handle);
        Dispatcher.get("/chat/mark-as-read", chatProcessor::markAsRead, apiCheck::handle);
        Dispatcher.get("/chat/mark-all-as-read", chatProcessor::markAllAsRead, apiCheck::handle);
        Dispatcher.get("/chat/revoke", chatProcessor::revoke, apiCheck::handle);
        Dispatcher.get("/chat/raw/{id}", chatProcessor::getRaw, apiCheck::handle);
    }

    public void getRaw(final RequestContext context) {
        JSONObject currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        String userId = currentUser.optString(Keys.OBJECT_ID);
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "raw.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        String id = context.pathVar("id");
        Query query = new Query().setFilter(new PropertyFilter(Keys.OBJECT_ID, FilterOperator.EQUAL, id));
        try {
            JSONObject object = chatInfoRepository.getFirst(query);
            if (object == null) {
                context.renderJSON(new JSONObject()
                        .put("result", -1)
                        .put("msg", "消息不存在。"));
                return;
            }
            if (userId.equals(object.optString("fromId")) || userId.equals(object.optString("toId"))) {
                String content = object.optString("content");
                dataModel.put("raw", content);
            } else {
                context.renderJSON(new JSONObject()
                        .put("result", -1)
                        .put("msg", "这不是你发的消息，你瞎看什么？记小本本了哦。"));
            }
        } catch (RepositoryException e) {
            context.renderCodeMsg(StatusCodes.ERR, "Invalid chat id.");
        }
    }

    public void revoke(final RequestContext context) {
        context.renderJSON(new JSONObject().put("result", 0));
        JSONObject currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        String userId = currentUser.optString(Keys.OBJECT_ID);
        try {
            String oId = context.param("oId");
            JSONObject msg = chatInfoRepository.get(oId);
            if (userId.equals(msg.optString("fromId"))) {
                LogsService.chatLog(context, oId, currentUser.optString(User.USER_NAME));
                final Transaction transaction = chatInfoRepository.beginTransaction();
                chatInfoRepository.remove(oId);
                transaction.commit();

                // 清除缓存
                String fromId = msg.optString("fromId");
                String toId = msg.optString("toId");
                invalidateChatListCache(fromId, toId);

                // 新增：删除与此条消息相关的未读记录（按 fromId/toId 匹配）
                try {
                    final Transaction unreadTx = chatUnreadRepository.beginTransaction();
                    Query q = new Query().setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("fromId", FilterOperator.EQUAL, msg.optString("fromId")),
                            new PropertyFilter("toId", FilterOperator.EQUAL, msg.optString("toId"))
                    ));
                    chatUnreadRepository.remove(q);
                    unreadTx.commit();

                    // 通知接收者刷新未读数
                    try {
                        Query queryUnread = new Query().setFilter(new PropertyFilter("toId", FilterOperator.EQUAL, msg.optString("toId")));
                        List<JSONObject> remain = chatUnreadRepository.getList(queryUnread);
                        final JSONObject cmd = new JSONObject();
                        cmd.put(UserExt.USER_T_ID, msg.optString("toId"));
                        cmd.put(Common.COMMAND, "chatUnreadCountRefresh");
                        cmd.put("count", remain == null ? 0 : remain.size());
                        UserChannel.sendCmd(cmd);
                    } catch (Exception ignored) {
                    }
                } catch (Exception ignored) {
                }

                ChatChannel.sendMsg(msg.optString("fromId"), msg.optString("toId"), oId);
            } else {
                context.renderJSON(new JSONObject()
                        .put("result", -1)
                        .put("msg", "撤回失败，这不是你发的消息。"));
            }
        } catch (Exception e) {
            context.renderJSON(new JSONObject()
                    .put("result", -1)
                    .put("msg", "撤回失败 " + e.getMessage()));
        }
    }

    public void markAllAsRead(final RequestContext context) {
        context.renderJSON(new JSONObject().put("result", 0));
        JSONObject currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        String userId = currentUser.optString(Keys.OBJECT_ID);
        try {
            Query query = new Query()
                    .setFilter(
                            new PropertyFilter("toId", FilterOperator.EQUAL, userId)
                    );
            List<JSONObject> unreadList = chatUnreadRepository.getList(query);
            List<String> unreadUserNameList = new ArrayList<>();
            for (JSONObject unread : unreadList) {
                String targetUserId = unread.optString("user_session").replaceAll(userId, "").replaceAll("_", "");
                try {
                    JSONObject targetUser = userQueryService.getUser(targetUserId);
                    String userName = targetUser.optString(User.USER_NAME);
                    if (!unreadUserNameList.contains(userName)) {
                        unreadUserNameList.add(userName);
                    }
                } catch (Exception ignored) {
                }
            }
            final Transaction transaction = chatUnreadRepository.beginTransaction();
            chatUnreadRepository.remove(query);
            transaction.commit();
            // 通知用户
            final JSONObject cmd = new JSONObject();
            cmd.put(UserExt.USER_T_ID, userId);
            cmd.put(Common.COMMAND, "chatUnreadCountRefresh");
            cmd.put("count", 0);
            UserChannel.sendCmd(cmd);
            context.renderJSON(new JSONObject().put("result", 0).put("users", unreadUserNameList));
        } catch (Exception e) {
            context.renderJSON(new JSONObject()
                    .put("result", -1)
                    .put("msg", "全部标记为已读失败 " + e.getMessage()));
        }
    }

    public void markAsRead(final RequestContext context) {
        context.renderJSON(new JSONObject().put("result", 0));
        JSONObject currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        String userId = currentUser.optString(Keys.OBJECT_ID);
        try {
            String fromUser = context.param("fromUser");
            JSONObject fromUserJSON = userQueryService.getUserByName(fromUser);
            String fromUserId = fromUserJSON.optString(Keys.OBJECT_ID);
            final Transaction transaction = chatUnreadRepository.beginTransaction();
            Query query = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("fromId", FilterOperator.EQUAL, fromUserId),
                            new PropertyFilter("toId", FilterOperator.EQUAL, userId)
                    ));
            chatUnreadRepository.remove(query);
            transaction.commit();
            // 通知用户
            Query queryUnread = new Query().setFilter(new PropertyFilter("toId", FilterOperator.EQUAL, userId));
            try {
                List<JSONObject> result = chatUnreadRepository.getList(queryUnread);
                final JSONObject cmd = new JSONObject();
                cmd.put(UserExt.USER_T_ID, userId);
                cmd.put(Common.COMMAND, "chatUnreadCountRefresh");
                cmd.put("count", result.size());
                UserChannel.sendCmd(cmd);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            context.renderJSON(new JSONObject()
                    .put("result", -1)
                    .put("msg", "标记为已读失败 " + e.getMessage()));
        }
    }

    public void getMessage(final RequestContext context) {
        context.renderJSON(new JSONObject().put("result", 0));
        JSONObject currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        String userId = currentUser.optString(Keys.OBJECT_ID);
        String sessionId = "";
        try {
            String toUser = context.param("toUser");
            String toUserOId;
            JSONObject receiverInfo = new JSONObject();
            if (toUser.equals("FileTransfer")) {
                toUserOId = "1000000000086";
                receiverInfo.put(User.USER_NAME, "文件传输助手");
                receiverInfo.put(UserExt.USER_AVATAR_URL, "https://file.fishpi.cn/2022/06/e1541bfe4138c144285f11ea858b6bf6-ba777366.jpeg");
            } else {
                try {
                    final JSONObject reciver = userQueryService.getUserByName(toUser);
                    toUserOId = reciver.optString(Keys.OBJECT_ID);
                    receiverInfo.put("receiverUserName", reciver.optString(User.USER_NAME));
                    receiverInfo.put("receiverAvatar", reciver.optString(UserExt.USER_AVATAR_URL));
                } catch (NullPointerException e) {
                    context.renderJSON(new JSONObject()
                            .put("result", -1)
                            .put("msg", "对象用户不存在 " + e.getMessage()));
                    return;
                }
            }
            sessionId = Strings.uniqueId(new String[]{userId, toUserOId});
            int page = Integer.parseInt(context.param("page"));
            int pageSize = Integer.parseInt(context.param("pageSize"));

            Query query = new Query()
                    .setFilter(new PropertyFilter("user_session", FilterOperator.EQUAL, sessionId))
                    .setPage(page, pageSize)
                    .addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
            final List<JSONObject> list = chatInfoRepository.getList(query);
            for (JSONObject info : list) {
                String fromId = info.optString("fromId");
                if (fromId.equals(userId)) {
                    //我自己发的
                    info.put("senderUserName", currentUser.optString(User.USER_NAME));
                    info.put("senderAvatar", currentUser.optString(UserExt.USER_AVATAR_URL));
                    info.put("receiverUserName", receiverInfo.optString("receiverUserName"));
                    info.put("receiverAvatar", receiverInfo.optString("receiverAvatar"));
                } else {
                    info.put("senderUserName", receiverInfo.optString("receiverUserName"));
                    info.put("senderAvatar", receiverInfo.optString("receiverAvatar"));
                    info.put("receiverUserName", currentUser.optString(User.USER_NAME));
                    info.put("receiverAvatar", currentUser.optString(UserExt.USER_AVATAR_URL));
                }
                // 将content过滤为纯文本
                String content = info.optString("content");
                info.put("preview", makePreview(content));
                String markdown = info.optString("content");
                String html = ChatProcessor.processMarkdown(markdown);
                info.put("content", html);
                info.put("markdown", markdown);
            }
            if (list.isEmpty()) {
                context.renderJSON(new JSONObject()
                        .put("result", -1)
                        .put("msg", "没有更多消息了"));
                return;
            }
            context.renderJSON(new JSONObject()
                    .put("result", 0)
                    .put("data", list));
        } catch (Exception e) {
            LOGGER.error("get chat message error in session [{}]", sessionId, e);
            context.renderJSON(new JSONObject()
                    .put("result", -1)
                    .put("msg", "获取列表失败 " + e.getMessage()));
        }
    }

    public static String makePreview(String content) {
        content = Jsoup.clean(content, Whitelist.none());
        content = StringUtils.trim(content);
        if (StringUtils.isBlank(content)) {
            content = "[聊天消息]";
        }
        return content.length() > 20 ? content.substring(0, 20) : content;
    }

    public void getList(final RequestContext context) {
        context.renderJSON(new JSONObject().put("result", 0));
        JSONObject currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        String userId = currentUser.optString(Keys.OBJECT_ID);

        // 尝试从缓存中获取
        List<JSONObject> cachedList = CHAT_LIST_CACHE.getIfPresent(userId);
        if (cachedList != null) {
            context.renderJSON(new JSONObject().put("result", 0)
                    .put("data", cachedList)
                    .put("cached", true));
            return;
        }

        List<JSONObject> infoList = new LinkedList<>();
        try {
            infoList = chatListService.getChatList(userId);
        } catch (Exception e) {
        }
        // 渲染用户信息
        final ArrayList<JSONObject> res = new ArrayList<>();
        for (JSONObject listItem : infoList) {
            String sessionId = listItem.optString("sessionId");
            String[] ids = sessionId.split("_");
            final Optional<String> otherIdOpt = Arrays.stream(ids).filter(id -> !id.equals(userId)).findAny();
            if (!otherIdOpt.isPresent()) {
                LOGGER.warn("sessionId [{}] does not contain other user id, userId [{}]", sessionId, userId);
                continue;
            }
            final String otherId = otherIdOpt.get();
            JSONObject otherUser = userQueryService.getUser(otherId);
            if (otherUser == null) {
                continue;
            }
            JSONObject info = null;
            final String lastMessageId = listItem.optString("lastMessageId");
            try {
                info = chatInfoRepository.get(lastMessageId);
                if (Objects.isNull(info)) {
                    Query query = new Query()
                            .setFilter(new PropertyFilter("user_session", FilterOperator.EQUAL, listItem.optString("sessionId")))
                            .addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
                    info = chatInfoRepository.getFirst(query);
                    if (Objects.isNull(info)) {
                        continue;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("get chat info error by id: [{}]", lastMessageId);
                continue;
            }
            info.put("senderUserName", currentUser.optString(User.USER_NAME));
            info.put("senderAvatar", currentUser.optString(UserExt.USER_AVATAR_URL) + "?imageView2/1/w/48/h/48/interlace/0/q/100");
            if (!otherId.equals("1000000000086")) {
                info.put("receiverUserName", otherUser.optString(User.USER_NAME));
                info.put("receiverAvatar", otherUser.optString(UserExt.USER_AVATAR_URL) + "?imageView2/1/w/48/h/48/interlace/0/q/100");
                info.put("receiverOnlineFlag", otherUser.optBoolean(UserExt.USER_ONLINE_FLAG));
            } else {
                info.put("receiverOnlineFlag", true);
                info.put("receiverUserName", "文件传输助手");
                info.put("receiverAvatar", "https://file.fishpi.cn/2022/06/e1541bfe4138c144285f11ea858b6bf6-ba777366.jpeg");
            }
            // 将content过滤为纯文本
            String content = info.optString("content");
            info.put("preview", makePreview(content));
            String markdown = info.optString("content");
            String html = ChatProcessor.processMarkdown(markdown);
            info.put("content", html);
            info.put("markdown", markdown);
            res.add(info);
        }
        Collections.sort(res, (o1, o2) -> {
            boolean flag1 = o1.optBoolean("receiverOnlineFlag", false);
            boolean flag2 = o2.optBoolean("receiverOnlineFlag", false);
            if (flag1 == flag2) return 0;
            return flag1 ? -1 : 1;
        });

        // 存入缓存
        CHAT_LIST_CACHE.put(userId, res);

        context.renderJSON(new JSONObject().put("result", 0)
                .put("data", res)
                .put("cached", false));
    }

    public void showChat(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "chat.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final JSONObject currentUser = Sessions.getUser();
        if (null == currentUser) {
            context.sendError(403);
            return;
        }
        // 放 ApiKey
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        final String userPassword = currentUser.optString(User.USER_PASSWORD);
        final JSONObject cookieJSONObject = new JSONObject();
        cookieJSONObject.put(Keys.OBJECT_ID, userId);
        final String random = RandomStringUtils.randomAlphanumeric(16);
        cookieJSONObject.put(Keys.TOKEN, userPassword + ApiProcessor.COOKIE_ITEM_SEPARATOR + random);
        final String key = Crypts.encryptByAES(cookieJSONObject.toString(), Symphonys.COOKIE_SECRET_API);
        dataModel.put("apiKey", key);

        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    public void hasUnreadChatMessage(RequestContext context) {
        context.renderJSON(new JSONObject().put("result", 0));
        JSONObject currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        String userId = currentUser.optString(Keys.OBJECT_ID);

        Query query = new Query().setFilter(new PropertyFilter("toId", FilterOperator.EQUAL, userId));
        try {
            List<JSONObject> result = chatUnreadRepository.getList(query);
            for (JSONObject info : result) {
                String fromId = info.optString("fromId");
                String toId = info.optString("toId");
                JSONObject senderJSON = userQueryService.getUser(fromId);
                if (!fromId.equals("1000000000086")) {
                    info.put("senderUserName", senderJSON.optString(User.USER_NAME));
                } else {
                    info.put("receiverUserName", "文件传输助手");
                }
                JSONObject receiverJSON = userQueryService.getUser(toId);
                if (!toId.equals("1000000000086")) {
                    info.put("receiverUserName", receiverJSON.optString(User.USER_NAME));
                } else {
                    info.put("receiverUserName", "文件传输助手");
                }
            }
            if (result != null) {
                context.renderJSON(new JSONObject().put("result", result.size())
                        .put("data", result));
            }
        } catch (RepositoryException ignored) {
        }
    }

    public static String processMarkdown(String content) {
        final BeanManager beanManager = BeanManager.getInstance();
        final ShortLinkQueryService shortLinkQueryService = beanManager.getReference(ShortLinkQueryService.class);
        content = shortLinkQueryService.linkArticle(content);
        content = Emotions.toAliases(content);
        content = Emotions.convert(content);
        content = Markdowns.toHTML(content);
        content = Markdowns.clean(content, "");
        content = MediaPlayers.renderAudio(content);
        content = MediaPlayers.renderVideo(content);

        return content;
    }

    /**
     * Invalidate chat list cache for related users.
     *
     * @param fromId sender user id
     * @param toId   receiver user id
     */
    public static void invalidateChatListCache(String fromId, String toId) {
        CHAT_LIST_CACHE.invalidate(fromId);
        CHAT_LIST_CACHE.invalidate(toId);
    }

    /**
     * Clear all chat list cache.
     */
    public static void clearChatListCache() {
        CHAT_LIST_CACHE.invalidateAll();
    }
}
