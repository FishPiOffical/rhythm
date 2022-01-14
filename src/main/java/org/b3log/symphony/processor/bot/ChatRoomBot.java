package org.b3log.symphony.processor.bot;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.processor.ChatroomProcessor;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.service.NotificationMgmtService;
import org.b3log.symphony.util.JSONs;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Objects;

/**
 * 人工智障
 * 监督复读机、刷活跃、抢红包、无用消息
 */
public class ChatRoomBot {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(ChatRoomBot.class);

    /**
     * 记录并分析消息是否可疑
     * @param context
     */
    public static boolean record(final RequestContext context) {
        // ==? 前置参数 ?==
        final JSONObject requestJSONObject = (JSONObject) context.attr(Keys.REQUEST);
        String content = requestJSONObject.optString(Common.CONTENT);
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        final String userName = currentUser.optString(User.USER_NAME);
        // ==! 前置参数 !==

        try {
            JSONObject checkContent = new JSONObject(content);
            if (checkContent.optString("msgType").equals("redPacket")) {
                // 判定恶意发送非法红包
                sendBotMsg("监测到 @" + userName + " 伪造发送红包数据包，警告一次。");
            }
        } catch (Exception ignored) {
        }

        return true;
    }

    // 以人工智障的身份发送消息
    public static void sendBotMsg(String content) {
        final long time = System.currentTimeMillis();
        JSONObject msg = new JSONObject();
        msg.put(User.USER_NAME, "摸鱼派官方巡逻机器人");
        msg.put(UserExt.USER_AVATAR_URL, "https://pwl.stackoverflow.wiki/2022/01/robot3-89631199.png");
        msg.put(Common.CONTENT, content);
        msg.put(Common.TIME, time);
        msg.put(UserExt.USER_NICKNAME, "人工智障");
        msg.put("sysMetal", "");
        // 聊天室内容保存到数据库
        final BeanManager beanManager = BeanManager.getInstance();
        ChatRoomRepository chatRoomRepository = beanManager.getReference(ChatRoomRepository.class);
        final Transaction transaction = chatRoomRepository.beginTransaction();
        try {
            String oId = chatRoomRepository.add(new JSONObject().put("content", msg.toString()));
            msg.put("oId", oId);
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Cannot save ChatRoom bot message to the database.", e);
        }
        transaction.commit();
        msg = msg.put("md", msg.optString(Common.CONTENT)).put(Common.CONTENT, ChatroomProcessor.processMarkdown(msg.optString(Common.CONTENT)));
        final JSONObject pushMsg = JSONs.clone(msg);
        pushMsg.put(Common.TIME, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(msg.optLong(Common.TIME)));
        ChatroomChannel.notifyChat(pushMsg);

        try {
            ChatroomProcessor chatroomProcessor = beanManager.getReference(ChatroomProcessor.class);
            NotificationMgmtService notificationMgmtService = beanManager.getReference(NotificationMgmtService.class);
            final List<JSONObject> atUsers = chatroomProcessor.atUsers(msg.optString(Common.CONTENT), "admin");
            if (Objects.nonNull(atUsers) && !atUsers.isEmpty()) {
                for (JSONObject user : atUsers) {
                    final JSONObject notification = new JSONObject();
                    notification.put(Notification.NOTIFICATION_USER_ID, user.optString("oId"));
                    notification.put(Notification.NOTIFICATION_DATA_ID, msg.optString("oId"));
                    notificationMgmtService.addChatRoomAtNotification(notification);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "notify user failed", e);
        }
    }
}
