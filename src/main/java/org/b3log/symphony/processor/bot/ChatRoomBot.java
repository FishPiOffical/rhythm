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
package org.b3log.symphony.processor.bot;

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.processor.ChatroomProcessor;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.repository.ChatRoomRepository;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.JSONs;
import org.b3log.symphony.util.NodeUtil;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONArray;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.text.SimpleDateFormat;
import java.util.*;

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
     * 警告记录池，不同的记录池有不同的次数
     */
    private static final SimpleCurrentLimiter RECORD_POOL_2_IN_24H = new SimpleCurrentLimiter(24 * 60 * 60, 1);
    private static final SimpleCurrentLimiter RECORD_POOL_6_IN_15M = new SimpleCurrentLimiter(15 * 60, 5);
    private static final SimpleCurrentLimiter RECORD_POOL_5_IN_24H = new SimpleCurrentLimiter(24 * 60 * 60, 4);
    private static final SimpleCurrentLimiter RECORD_POOL_5_IN_1M = new SimpleCurrentLimiter(60, 5);
    private static final SimpleCurrentLimiter RECORD_POOL_BARRAGER = new SimpleCurrentLimiter(60, 5);
    private static final SimpleCurrentLimiter RECORD_POOL_05_IN_1M = new SimpleCurrentLimiter(120, 1);


    /**
     * 对应关系池
     */
    private static final Map<String, String> RECORD_MAP = Collections.synchronizedMap(new LinkedHashMap<String, String>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 2000;
        }
    });

    private static String latestMessage = "";
    private static String allLatestMessage = "";

    // 记录并分析消息是否可疑
    public static boolean record(final RequestContext context) {
        boolean pass = true;
        String reason = "";
        final JSONObject requestJSONObject = (JSONObject) context.attr(Keys.REQUEST);
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }

        // ==? 前置参数 ?==
        String content = requestJSONObject.optString(Common.CONTENT);
        String userName = currentUser.optString(User.USER_NAME);
        String userId = currentUser.optString(Keys.OBJECT_ID);
        // ==! 前置参数 !==

        // ==? 判断是否在 Channel 中 ==?
        /*boolean atChannel = false;
        for (Map.Entry<WebSocketSession, JSONObject> onlineUser : ChatroomChannel.onlineUsers.entrySet()) {
            try {
                String uName = onlineUser.getValue().optString(User.USER_NAME);
                if (uName.equals(userName)) {
                    atChannel = true;
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        if (!atChannel) {
            context.renderJSON(StatusCodes.ERR).renderMsg("发送失败：当前未在聊天室中，请刷新页面。");
            return false;
        }*/
        // ==! 判断是否在 Channel 中 ==!

        // ==? 发弹幕频率限制 ?==
        if (!userName.equals("admin")) {
            if (content.startsWith("[barrager]") && content.endsWith("[/barrager]")) {
                if (!RECORD_POOL_BARRAGER.access(userName)) {
                    context.renderJSON(StatusCodes.ERR).renderMsg("弹幕发的太快啦！休息一下吧（每分钟最多5个）");
                    return false;
                }

                return true;
            }
        }
        // ==! 发弹幕频率限制 !==

        // ==? 指令 ?==
        if (DataModelService.hasPermission(currentUser.optString(User.USER_ROLE), 3)) {
            if (content.startsWith("执法") || content.startsWith("zf")) {
                try {
                    String cmd1 = content.replaceAll("(执法)(\\s)+", "").replaceAll("(zf)(\\s)+", "");
                    String cmd2 = cmd1.split("\\s")[0];
                    switch (cmd2) {
                        case "jy":
                            cmd2 = "禁言";
                            break;
                        case "qyjy":
                            cmd2 = "全员禁言";
                            break;
                        case "fk":
                            cmd2 = "风控";
                            break;
                        case "fwqzt":
                            cmd2 = "服务器状态";
                            break;
                        case "sxhc":
                            cmd2 = "刷新缓存";
                            break;
                        case "gbsz":
                            cmd2 = "广播设置";
                            break;
                        case "wh":
                            cmd2 = "维护";
                            break;
                        case "cf":
                            cmd2 = "处罚";
                            break;
                        case "dkhh":
                            cmd2 = "断开会话";
                            break;
                        case "dm":
                            cmd2 = "弹幕";
                            break;
                        case "jcts":
                            cmd2 = "进出提示";
                            break;
                    }
                    switch (cmd2) {
                        case "禁言":
                            try {
                                String user = cmd1.split("\\s")[1].replaceAll("^(@)", "");
                                String time = "";
                                try {
                                    time = cmd1.split("\\s")[2];
                                } catch (Exception ignored) {
                                }
                                final BeanManager beanManager = BeanManager.getInstance();
                                UserQueryService userQueryService = beanManager.getReference(UserQueryService.class);
                                JSONObject targetUser = userQueryService.getUserByName(user);
                                if (null == targetUser) {
                                    sendBotMsg("指令执行失败，用户不存在。");
                                    break;
                                }
                                String targetUserId = targetUser.optString(Keys.OBJECT_ID);
                                if (time.isEmpty()) {
                                    int muted = muted(targetUserId);
                                    // 是否全员禁言中
                                    boolean isAll = muted < 0 && muted != -1;
                                    if (isAll){
                                        // 回正
                                        muted *= -1;
                                    }
                                    if (muted != -1) {
                                        int muteDay = muted / (24 * 60 * 60);
                                        int muteHour = muted % (24 * 60 * 60) / (60 * 60);
                                        int muteMinute = muted % (24 * 60 * 60) % (60 * 60) / 60;
                                        int muteSecond = muted % (24 * 60 * 60) % (60 * 60) % 60;
                                        sendBotMsg("查询结果：该用户剩余禁言时间为：" + muteDay + " 天 " + muteHour + " 小时 " + muteMinute + " 分 " + muteSecond + " 秒。");
                                    } else if (isAll){
                                        int muteDay = muted / (24 * 60 * 60);
                                        int muteHour = muted % (24 * 60 * 60) / (60 * 60);
                                        int muteMinute = muted % (24 * 60 * 60) % (60 * 60) / 60;
                                        int muteSecond = muted % (24 * 60 * 60) % (60 * 60) % 60;
                                        sendBotMsg("查询结果：全员禁言中 剩余禁言时间为：" + muteDay + " 天 " + muteHour + " 小时 " + muteMinute + " 分 " + muteSecond + " 秒。");
                                    }else {
                                        sendBotMsg("查询结果：该用户当前未被禁言。");
                                    }
                                } else {
                                    int minute = Integer.parseInt(time);
                                    muteAndNotice(user, targetUserId, minute);
                                }
                            } catch (Exception e) {
                                sendBotMsg("指令执行失败，禁言命令的正确格式：\n执法 禁言 @[用户名] [时间 `单位: 分钟` `如不填此项将查询剩余禁言时间` `设置为0将解除禁言`]");
                            }
                            break;
                        case "全员禁言":
                            try {
                                String time = "";
                                try {
                                    time = cmd1.split("\\s")[1];
                                } catch (Exception ignored) {
                                    // 切出去. 什么也不干. 拿不到命令
                                }
                                // 目标特殊 key 全员禁言
                                String targetUserId = "all:fish:mute";
                                if (time.isEmpty()) {
                                    int muted = muted(targetUserId);
                                    if (muted != -1) {
                                        int muteDay = muted / (24 * 60 * 60);
                                        int muteHour = muted % (24 * 60 * 60) / (60 * 60);
                                        int muteMinute = muted % (24 * 60 * 60) % (60 * 60) / 60;
                                        int muteSecond = muted % (24 * 60 * 60) % (60 * 60) % 60;
                                        sendBotMsg("查询结果：全员剩余禁言时间为：" + muteDay + " 天 " + muteHour + " 小时 " + muteMinute + " 分 " + muteSecond + " 秒。");
                                    } else {
                                        sendBotMsg("查询结果：当前未开启全员禁言。");
                                    }
                                } else {
                                    int minute = Integer.parseInt(time);
                                    // 全员禁言
                                    allMuteAndNotice(currentUser.optString(User.USER_NAME), targetUserId, minute);
                                }
                            } catch (Exception e) {
                                sendBotMsg("指令执行失败，禁言命令的正确格式：\n执法 全员禁言 [时间 `单位: 分钟` `如不填此项将查询剩余禁言时间` `设置为0将解除禁言`]");
                            }
                            break;
                        case "风控":
                            try {
                                String user = cmd1.split("\\s")[1].replaceAll("^(@)", "");
                                String time = "";
                                try {
                                    time = cmd1.split("\\s")[2];
                                } catch (Exception ignored) {
                                }
                                final BeanManager beanManager = BeanManager.getInstance();
                                UserQueryService userQueryService = beanManager.getReference(UserQueryService.class);
                                JSONObject targetUser = userQueryService.getUserByName(user);
                                if (null == targetUser) {
                                    sendBotMsg("指令执行失败，用户不存在。");
                                    break;
                                }
                                String targetUserId = targetUser.optString(Keys.OBJECT_ID);
                                if (time.isEmpty()) {
                                    int risksControlled = risksControlled(targetUserId);
                                    if (risksControlled != -1) {
                                        int risksControlDay = risksControlled / (24 * 60 * 60);
                                        int risksControlHour = risksControlled % (24 * 60 * 60) / (60 * 60);
                                        int risksControlMinute = risksControlled % (24 * 60 * 60) % (60 * 60) / 60;
                                        int risksControlSecond = risksControlled % (24 * 60 * 60) % (60 * 60) % 60;
                                        sendBotMsg("查询结果：该用户剩余风控时间为：" + risksControlDay + " 天 " + risksControlHour + " 小时 " + risksControlMinute + " 分 " + risksControlSecond + " 秒。");
                                    } else {
                                        sendBotMsg("查询结果：该用户当前未被风控。");
                                    }
                                } else {
                                    int minute = Integer.parseInt(time);
                                    risksControlAndNotice(user, targetUserId, minute);
                                }
                            } catch (Exception e) {
                                sendBotMsg("指令执行失败，风控命令的正确格式：\n执法 风控 @[用户名] [时间 `单位：分钟` `如不填此项将查询剩余风控时间` `设置为0将解除风控`]\n\n" +
                                        "风控内容：\n" +
                                        "* 每次发消息，需要二次确认\n" +
                                        "* 限制发送最少字数、不允许单发一张图片\n" +
                                        "* 限制通过聊天室获取的活跃度，每15分钟仅有效1条\n" +
                                        "* 每30分钟只允许抢一次红包");
                            }
                            break;
                        case "服务器状态":
                            Map<String, Integer> sessionList = new HashMap<>();
                            for (WebSocketSession session : ChatroomChannel.SESSIONS) {
                                try {
                                    String uName = ChatroomChannel.onlineUsers.get(session).optString(User.USER_NAME);
                                    if (sessionList.containsKey(uName)) {
                                        sessionList.put(uName, sessionList.get(uName) + 1);
                                    } else {
                                        sessionList.put(uName, 1);
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                            StringBuilder userSessionList = new StringBuilder();
                            userSessionList.append("<details><summary>故障转移区（" + ChatroomChannel.SESSIONS.size() + "人）</summary>");
                            for (Map.Entry<String, Integer> s : sessionList.entrySet()) {
                                userSessionList.append(s.getKey() + " " + s.getValue() + "<br>");
                            }
                            userSessionList.append("</details>");
                            for (String key : NodeUtil.nodeNickNames.keySet()) {
                                String nickName = NodeUtil.nodeNickNames.get(key);
                                HashMap<String, Integer> map = NodeUtil.remoteUserPerNode.get(key);
                                if (null == map) {
                                    continue;
                                }
                                int count = 0;
                                for (String i : map.keySet()) {
                                    count += map.get(i);
                                }
                                userSessionList.append("<details><summary>" + nickName + "（" + count + "人）</summary>");
                                for (String i : map.keySet()) {
                                    int onlineNum = map.get(i);
                                    userSessionList.append(i + " " + onlineNum + "<br>");
                                }
                                userSessionList.append("</details>");
                            }
                            int sessions = ChatroomChannel.SESSIONS.size() + NodeUtil.remoteUsers.length();
                            sendBotMsg("" +
                                    "当前聊天室会话数：" + sessions + "\n" +
                                    userSessionList);
                            break;
                        case "刷新缓存":
                            ChatroomChannel.sendOnlineMsg();
                            int online = ChatroomChannel.SESSIONS.size();
                            int estimatedTime = online / 2;
                            clearScreen();
                            sendBotMsg("在线人数缓存刷新请求已提交，预计需要时间 **" + estimatedTime + "** 秒。\n" +
                                    "在线用户全体清屏请求已提交，预计需要时间 **" + estimatedTime + "** 秒。");
                            break;
                        case "广播设置":
                            try {
                                int notQuickCheck = Integer.parseInt(cmd1.split("\\s")[1]);
                                int notQuickSleep = Integer.parseInt(cmd1.split("\\s")[2]);
                                int quickCheck = Integer.parseInt(cmd1.split("\\s")[3]);
                                int quickSleep = Integer.parseInt(cmd1.split("\\s")[4]);
                                ChatroomChannel.notQuickCheck = notQuickCheck;
                                ChatroomChannel.notQuickSleep = notQuickSleep;
                                ChatroomChannel.quickCheck = quickCheck;
                                ChatroomChannel.quickSleep = quickSleep;
                                sendBotMsg("广播设置成功。");
                            } catch (Exception e) {
                                sendBotMsg("当前参数：" + ChatroomChannel.notQuickCheck + " " + ChatroomChannel.notQuickSleep + " " + ChatroomChannel.quickCheck + " " + ChatroomChannel.quickSleep);
                            }
                            break;
                        case "断开会话":
                            try {
                                String disconnectUser = cmd1.split("\\s")[1];
                                sendBotMsg("@" + disconnectUser + "  你的连接被管理员断开，请重新连接。");
                                Thread.startVirtualThread(() -> {
                                    try {
                                        Thread.sleep(2000);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    NodeUtil.sendKick(disconnectUser);
                                    List<WebSocketSession> senderSessions = new ArrayList<>();
                                    for (Map.Entry<WebSocketSession, JSONObject> entry : ChatroomChannel.onlineUsers.entrySet()) {
                                        try {
                                            String tempUserName = entry.getValue().optString(User.USER_NAME);
                                            if (tempUserName.equals(disconnectUser)) {
                                                senderSessions.add(entry.getKey());
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }
                                    for (WebSocketSession session : senderSessions) {
                                        ChatroomChannel.removeSession(session);
                                    }
                                    JdbcRepository.dispose();
                                }).start();
                            } catch (Exception e) {
                                sendBotMsg("参数错误。");
                            }
                            break;
                        case "维护":
                            Map<String, Long> result = ChatroomChannel.check();
                            StringBuilder stringBuilder = new StringBuilder();
                            if (result.isEmpty()) {
                                sendBotMsg("故障转移区：报告！没有超过6小时未活跃的成员，一切都很和谐~");
                            } else {
                                stringBuilder.append("故障转移区：报告！成功扫描超过6小时未活跃的成员，并已将他们断开连接：<br>");
                                stringBuilder.append("<details><summary>不活跃用户列表</summary>");
                                for (String i : result.keySet()) {
                                    long time = result.get(i);
                                    stringBuilder.append(i + " AFK " + time + "小时<br>");
                                }
                                stringBuilder.append("</details>");
                                sendBotMsg(stringBuilder.toString());
                            }
                            NodeUtil.sendClear();
                            break;
                        case "处罚":
                            try {
                                String user = cmd1.split("\\s")[1].replaceAll("^(@)", "");
                                int point = Integer.parseInt(cmd1.split("\\s")[2].replaceAll("[-.]", ""));
                                String reas0n = cmd1.split("\\s")[3];
                                final BeanManager beanManager = BeanManager.getInstance();
                                UserQueryService userQueryService = beanManager.getReference(UserQueryService.class);
                                JSONObject targetUser = userQueryService.getUserByName(user);
                                if (null == targetUser) {
                                    sendBotMsg("指令执行失败，用户不存在。");
                                    break;
                                }
                                String targetUserId = targetUser.optString(Keys.OBJECT_ID);

                                PointtransferMgmtService pointtransferMgmtService = beanManager.getReference(PointtransferMgmtService.class);
                                OperationMgmtService operationMgmtService = beanManager.getReference(OperationMgmtService.class);
                                NotificationMgmtService notificationMgmtService = beanManager.getReference(NotificationMgmtService.class);

                                final String transferId = pointtransferMgmtService.transfer(targetUserId, Pointtransfer.ID_C_SYS,
                                        Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT, point, reas0n, System.currentTimeMillis(), "");
                                operationMgmtService.addOperation(Operation.newOperation(context.getRequest(), Operation.OPERATION_CODE_C_DEDUCT_POINT, transferId));

                                final JSONObject notification = new JSONObject();
                                notification.put(Notification.NOTIFICATION_USER_ID, targetUserId);
                                notification.put(Notification.NOTIFICATION_DATA_ID, transferId);
                                notificationMgmtService.addAbusePointDeductNotification(notification);

                                sendBotMsg("成功扣除成员 " + user + " 的 " + point + " 积分，原因：" + reas0n);
                            } catch (Exception e) {
                                sendBotMsg("参数错误。");
                            }
                            break;
                        case "弹幕":
                            try {
                                int cost = Integer.parseInt(cmd1.split("\\s")[1]);
                                String unit = cmd1.split("\\s")[2];
                                ChatroomProcessor.barragerCost = cost;
                                ChatroomProcessor.barragerUnit = unit;
                                refreshBarrager(cost, unit);
                                sendBotMsg("弹幕价格设置为: **" + cost + "** " + unit + "/次。\n" +
                                        "弹幕价格将在下次重启服务器后自动恢复为默认值 (5积分/次)。\n" +
                                        "正在向成员推送新的弹幕价格，预计需要 **" + (ChatroomChannel.SESSIONS.size() / 2) + "** 秒。");
                            } catch (Exception e) {
                                sendBotMsg("参数错误。");
                            }
                            break;
                        case "进出提示":
                            try {
                                String user = cmd1.split("\\s")[1].replaceAll("^(@)", "");
                                if (user.equals("查询") || user.equals("cx")) {
                                    String uname = cmd1.split("\\s")[2];
                                    String join = ChatroomChannel.getCustomMessage(1, uname);
                                    if (join.isEmpty()) {
                                        join = "未设定";
                                    }
                                    String left = ChatroomChannel.getCustomMessage(0, uname);
                                    if (left.isEmpty()) {
                                        left = "未设定";
                                    }
                                    sendBotMsg("用户 **" + uname + "** 的进出提示设定如下：\n" +
                                            "进入：" + join + "\n" +
                                            "离开：" + left + "\n");
                                } else if (user.equals("帮助") || user.equals("bz")) {
                                    sendBotMsg("**进出提示变量**：\n" +
                                            "设置格式：执法 进出提示 [用户名] [进入内容及变量]&&&[退出内容及变量]" +
                                            "{userName} 用户名\n" +
                                            "{userNickName} 用户昵称\n" +
                                            "{userPoint} 用户积分余额\n" +
                                            "{userLevel} 用户VIP等级（如过期会为空）\n" +
                                            "{userNo} 用户编号\n" +
                                            "{userCity} 用户所在城市"
                                    );
                                } else {
                                    if (currentUser.optString(User.USER_ROLE).equals(Role.ROLE_ID_C_ADMIN)) {
                                        if (cmd1.split("\\s").length == 2) {
                                            ChatroomChannel.removeCustomMessage(user);
                                            sendBotMsg("用户 **" + user + "** 的进出提示已关闭。");
                                        } else if (cmd1.split("\\s").length >= 3) {
                                            String msg = content.replaceAll("执法 ", "")
                                                    .replaceAll("zf ", "")
                                                    .replaceAll("进出提示 ", "")
                                                    .replaceAll("jcts ", "")
                                                    .replaceAll(user + " ", "");
                                            ChatroomChannel.addCustomMessage(user, msg);
                                            sendBotMsg("用户 **" + user + "** 的进出提示已设置完毕。");
                                        }
                                    } else {
                                        sendBotMsg("操作失败，权限不足。");
                                    }
                                }
                            } catch (Exception e) {
                                sendBotMsg("参数错误。");
                            }
                            break;
                        default:
                            sendBotMsg("<details><summary>执法帮助菜单</summary>\n" +
                                    "如无特殊备注，则需要纪律委员及以上分组才可执行\n\n" +
                                    "* **禁言指定用户** 执法 禁言 [用户名] [时间 `单位: 分钟` `如不填此项将查询剩余禁言时间` `设置为0将解除禁言`]\n" +
                                    "* **全员禁言** 执法 全员禁言 [时间 `单位: 分钟` `如不填此项将查询剩余禁言时间` `设置为0将解除全员禁言`]\n" +
                                    "* **风控模式** 执法 风控 [用户名] [时间 `单位：分钟` `如不填此项将查询剩余风控时间` `设置为0将解除风控`]\n" +
                                    "* **查询服务器状态** 执法 服务器状态\n" +
                                    "* **刷新全体成员的聊天室缓存** 执法 刷新缓存\n" +
                                    "* **广播设置** 执法 广播设置 [普通消息数目检测阈值] [普通消息间隔毫秒] [特殊消息数目检测阈值] [特殊消息间隔毫秒]\n" +
                                    "* **检测聊天室内长时间不发言的成员，并将其移除** 执法 维护\n" +
                                    "* **扣除指定成员的积分** 执法 处罚 [用户名] [扣除积分数量] [理由]\n" +
                                    "* **断开指定用户的全部聊天室会话** 执法 断开会话 [用户名]\n" +
                                    "* **进出提示恢复默认（未经管理员允许禁止使用）** 执法 进出提示 [用户名]\n" +
                                    "* **设置进出提示（详细变量列表请输入：执法 进出提示 帮助）** 执法 进出提示 [用户名] [进入内容及变量]&&&[退出内容及变量]\n" +
                                    "* **查询进出提示** 执法 进出提示 查询 [用户名]\n" +
                                    "* **设置弹幕价格(服务器重启后失效)** 执法 弹幕 [价格] [单位]</details>\n" +
                                    "<p></p>");
                    }
                    return true;
                } catch (Exception ignored) {
                    sendBotMsg("指令执行失败。");
                }
            }
        }
        // ==! 指令 !==

        // ==? 风控 ?==
        int risksControlled = risksControlled(userId);
        if (risksControlled != -1) {
            int risksControlDay = risksControlled / (24 * 60 * 60);
            int risksControlHour = risksControlled % (24 * 60 * 60) / (60 * 60);
            int risksControlMinute = risksControlled % (24 * 60 * 60) % (60 * 60) / 60;
            int risksControlSecond = risksControlled % (24 * 60 * 60) % (60 * 60) % 60;
            // 单图片
            if (content.startsWith("![") && content.endsWith(")")) {
                context.renderJSON(StatusCodes.ERR).renderMsg("你的消息被机器人打回，原因：你处于风控名单，不允许发送单图片内容。剩余风控时间为：" + risksControlDay + " 天 " + risksControlHour + " 小时 " + risksControlMinute + " 分 " + risksControlSecond + " 秒。");
                return false;
            }
            // 字数
            if (content.length() < 5) {
                context.renderJSON(StatusCodes.ERR).renderMsg("你的消息被机器人打回，原因：你处于风控名单，发送消息字数必须大于5个字符。剩余风控时间为：" + risksControlDay + " 天 " + risksControlHour + " 小时 " + risksControlMinute + " 分 " + risksControlSecond + " 秒。");
                return false;
            }
            // 二次确认
            String key = userId + "_twice_confirm";
            if (RECORD_MAP.getOrDefault(key, "").equals(content)) {
                RECORD_MAP.remove(key);
            } else {
                context.renderJSON(StatusCodes.ERR).renderMsg("你的消息被机器人打回，原因：你处于风控名单，需要二次确认是否发送消息，请再次点击发送按钮确认发送。剩余风控时间为：" + risksControlDay + " 天 " + risksControlHour + " 小时 " + risksControlMinute + " 分 " + risksControlSecond + " 秒。");
                RECORD_MAP.put(key, content);
                return false;
            }
        }
        // ==! 风控 !==

        // ==? 是否禁言中 ?==
        int muted = muted(userId);
        // 是否全员禁言中
        boolean isAll = muted < 0 && muted != -1;
        if (isAll){
            // OP 豁免全员禁言
            if (DataModelService.hasPermission(currentUser.optString(User.USER_ROLE), 3)){
                muted = -1;
            }else {
                // 回正
                muted *= -1;
            }
        }
        int muteDay = muted / (24 * 60 * 60);
        int muteHour = muted % (24 * 60 * 60) / (60 * 60);
        int muteMinute = muted % (24 * 60 * 60) % (60 * 60) / 60;
        int muteSecond = muted % (24 * 60 * 60) % (60 * 60) % 60;
        if (muted != -1) {
            if (isAll){
                context.renderJSON(StatusCodes.ERR).renderMsg("你的消息被机器人打回，原因：全员禁言中，剩余时间 " + muteDay + " 天 " + muteHour + " 小时 " + muteMinute + " 分 " + muteSecond + " 秒。");
            }else {
                context.renderJSON(StatusCodes.ERR).renderMsg("你的消息被机器人打回，原因：正在禁言中，剩余时间 " + muteDay + " 天 " + muteHour + " 小时 " + muteMinute + " 分 " + muteSecond + " 秒。");
            }
            return false;
        }
        // ==! 是否禁言中 !==

        // ==? 判定恶意发送非法红包 ?==
        try {
            JSONObject checkContent = new JSONObject(content);
            if (checkContent.optString("msgType").equals("redPacket") || checkContent.optString("msgType").equals("weather")|| checkContent.optString("msgType").equals("music")) {
                if (RECORD_POOL_2_IN_24H.access(userName)) {
                    sendBotMsg("监测到 @" + userName + "  伪造发送红包/天气/音乐数据包，警告一次。");
                } else {
                    sendBotMsg("由于 @" + userName + "  第二次伪造发送红包/天气/音乐数据包，现处以扣除积分 50 的处罚。");
                    abusePoint(userId, 50, "机器人罚单-聊天室伪造发送红包/天气/音乐数据包");
                    RECORD_POOL_2_IN_24H.remove(userName);
                }
                return false;
            }
        } catch (Exception ignored) {
        }
        // ==! 判定恶意发送非法红包 !==

        // ==? 判定复读机 ?==
        if (!content.startsWith("[redpacket]") && !content.endsWith("[/redpacket]")) {
            if (content.equals(latestMessage)) {
                // 与上条内容相同
                if (RECORD_POOL_6_IN_15M.access(userName)) {
                    if (RECORD_POOL_6_IN_15M.get(userName).getFrequency() == 5) {
                        sendBotMsg("监测到 @" + userName + "  疑似使用自动复读机插件，请不要频繁复读。");
                    }
                } else {
                    sendBotMsg("由于 @" + userName + "  频繁复读，现处以禁言 15 分钟、扣除积分 30 的处罚。");
                    mute(userId, 15);
                    abusePoint(userId, 30, "机器人罚单-聊天室复读频率过高");
                    RECORD_POOL_6_IN_15M.remove(userName);
                }
            }
        }
        // ==! 判定复读机 !==

        // ==? 发红包频率限制 ?==
        if (!userName.equals("admin")) {
            if (content.startsWith("[redpacket]") && content.endsWith("[/redpacket]")) {
                if (!RECORD_POOL_5_IN_1M.access(userName)) {
                    context.renderJSON(StatusCodes.ERR).renderMsg("你的红包被机器人打回，原因：红包发送频率过快，每分钟仅允许发送5个红包，请稍候重试");
                    return false;
                }

                // 心跳红包限制
                String redpacketString = content.replaceAll("^\\[redpacket\\]", "").replaceAll("\\[/redpacket\\]$", "");
                JSONObject redpacket = new JSONObject(redpacketString);
                String type = redpacket.optString("type");
                int date = Integer.parseInt(DateFormatUtils.format(System.currentTimeMillis(), "HHmm"));
                /*if (type.equals("heartbeat")) {
                    if (date > 1800 || date < 830) {
                        context.renderJSON(StatusCodes.ERR).renderMsg("这个时段无法发送心跳红包！允许时间：08:30-18:00");
                        return false;
                    }
                }*/

                // 猜拳红包限制
                if (type.equals("rockPaperScissors")) {
                    boolean morning = date >= 830 && date <= 1130;
                    boolean afternoon = date >= 1330 && date <= 1800;
                    // 判断是否在上午或下午时段
                    if (morning || afternoon) {
                        // 每30秒每人只允许发送一条
                        if (!RECORD_POOL_05_IN_1M.access("rps+" + userId)) {
                            context.renderJSON(StatusCodes.ERR).renderMsg("现在是聊天高峰期，每人每2分钟只允许发送一个猜拳红包，请稍候重试。高峰期时段为：08:30-11:30、13:30-18:00");
                            return false;
                        }

                    }
                }
            }
        }
        // ==! 发红包频率限制 !==

        latestMessage = content;
        allLatestMessage = content;
        if (!pass) {
            context.renderJSON(StatusCodes.ERR).renderMsg("你的消息被机器人打回，原因：" + reason);
            return false;
        }
        return true;
    }

    private static boolean refreshBarragerLock = false;
    public static void refreshBarrager(int cost, String unit) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Common.TYPE, "refreshBarrager");
        jsonObject.put("cost", cost);
        jsonObject.put("unit", unit);
        String message = jsonObject.toString();
        if (!refreshBarragerLock) {
            refreshBarragerLock = true;
            Thread.startVirtualThread(() -> {
                int i = 0;
                for (WebSocketSession s : ChatroomChannel.SESSIONS) {
                    i++;
                    if (i % 1 == 0) {
                        try {
                            Thread.sleep(500);
                        } catch (Exception ignored) {
                        }
                    }
                    s.sendText(message);
                }
                refreshBarragerLock = false;
            });
        }
    }

    private static boolean clearScreenLock = false;
    public static void clearScreen() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(Common.TYPE, "refresh");
        String message = jsonObject.toString();
        if (!clearScreenLock) {
            clearScreenLock = true;
            Thread.startVirtualThread(() -> {
                int i = 0;
                for (WebSocketSession s : ChatroomChannel.SESSIONS) {
                    i++;
                    if (i % 1 == 0) {
                        try {
                            Thread.sleep(500);
                        } catch (Exception ignored) {
                        }
                    }
                    s.sendText(message);
                }
                clearScreenLock = false;
            });
        }
    }
    
    // 以人工智障的身份发送消息
    public static void sendBotMsg(String content) {
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            final long time = System.currentTimeMillis();
            JSONObject msg = new JSONObject();
            msg.put(User.USER_NAME, "马库斯");
            msg.put(UserExt.USER_AVATAR_URL, "https://file.fishpi.cn/2022/01/robot3-89631199.png");
            msg.put(Common.CONTENT, content);
            msg.put(Common.TIME, time);
            msg.put(UserExt.USER_NICKNAME, "RK200");
            msg.put("sysMetal", "");
            msg.put("userOId", 0L);
            msg.put("client", "Other/Robot");
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
            allLatestMessage = content;

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
        });
    }

    // 扣除积分
    public static void abusePoint(String userId, int point, String memo) {
        final BeanManager beanManager = BeanManager.getInstance();
        PointtransferMgmtService pointtransferMgmtService = beanManager.getReference(PointtransferMgmtService.class);
        final String transferId = pointtransferMgmtService.transfer(userId, Pointtransfer.ID_C_SYS,
                Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT, point, memo, System.currentTimeMillis(), "");

        NotificationMgmtService notificationMgmtService = beanManager.getReference(NotificationMgmtService.class);
        final JSONObject notification = new JSONObject();
        notification.put(Notification.NOTIFICATION_USER_ID, userId);
        notification.put(Notification.NOTIFICATION_DATA_ID, transferId);
        try {
            notificationMgmtService.addAbusePointDeductNotification(notification);
        } catch (ServiceException e) {
            LOGGER.log(Level.ERROR, "Unable to send abuse notify", e);
        }
    }

    public static void refreshSiGuo() {
        try {
            final BeanManager beanManager = BeanManager.getInstance();
            CloudRepository cloudRepository = beanManager.getReference(CloudRepository.class);
            Query cloudQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, "si:guo"),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, "record")
                    ));
            JSONObject result = cloudRepository.getFirst(cloudQuery);
            JSONArray array = new JSONArray();
            if (null != result) {
                // 删除旧记录
                Transaction transaction = cloudRepository.beginTransaction();
                cloudRepository.remove(cloudQuery);
                // 写入新记录
                JSONArray jsonArray = new JSONArray(result.optString("data"));
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.optJSONObject(i);
                    if (jsonObject.optLong("time") > System.currentTimeMillis()) {
                        array.put(jsonObject);
                    }
                }
                JSONObject cloudJSON = new JSONObject();
                cloudJSON.put("userId", "si:guo")
                        .put("gameId", "record")
                        .put("data", array.toString());
                cloudRepository.add(cloudJSON);
                transaction.commit();
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Refresh SiGuo failed", e);
        }
    }

    public static JSONArray getSiGuoList() {
        try {
            final BeanManager beanManager = BeanManager.getInstance();
            CloudRepository cloudRepository = beanManager.getReference(CloudRepository.class);
            Query cloudQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, "si:guo"),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, "record")
                    ));
            JSONObject result = cloudRepository.getFirst(cloudQuery);
            if (null != result) {
                return new JSONArray(result.optString("data"));
            } else {
                return new JSONArray();
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Get SiGuo failed", e);
            return new JSONArray();
        }
    }

    public static void registerSiGuo(String userId, long time) {
        try {
            JSONArray oldJSON = new JSONArray();
            final BeanManager beanManager = BeanManager.getInstance();
            CloudRepository cloudRepository = beanManager.getReference(CloudRepository.class);
            UserQueryService userQueryService = beanManager.getReference(UserQueryService.class);
            Query cloudQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, "si:guo"),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, "record")
                    ));
            JSONObject result = cloudRepository.getFirst(cloudQuery);
            if (null != result) {
                oldJSON = new JSONArray(result.optString("data"));
                Transaction transaction = cloudRepository.beginTransaction();
                cloudRepository.remove(cloudQuery);
                transaction.commit();
            }
            JSONArray data = new JSONArray();
            String userName = userQueryService.getUser(userId).optString(User.USER_NAME);
            for (int i = 0; i < oldJSON.length(); i++) {
                JSONObject json = oldJSON.optJSONObject(i);
                if (json.optLong("time") > System.currentTimeMillis()) {
                    if (!json.optString("userName").equals(userName)) {
                        data.put(json);
                    }
                }
            }
            if (time > System.currentTimeMillis()) {
                data.put(new JSONObject().put("userName", userName).put("time", time));
            }
            JSONObject cloudJSON = new JSONObject();
            cloudJSON.put("userId", "si:guo")
                    .put("gameId", "record")
                    .put("data", data.toString());
            Transaction transaction = cloudRepository.beginTransaction();
            cloudRepository.add(cloudJSON);
            transaction.commit();
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Register SiGuo failed", e);
        }
    }

    // 禁言
    public static void mute(String userId, int minute) {
        final BeanManager beanManager = BeanManager.getInstance();
        CloudRepository cloudRepository = beanManager.getReference(CloudRepository.class);
        long muteTime = (long) minute * 1000 * 60;
        try {
            final Transaction transaction = cloudRepository.beginTransaction();
            Query cloudDeleteQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_MUTE)
                    ));
            cloudRepository.remove(cloudDeleteQuery);
            JSONObject cloudJSON = new JSONObject();
            long time = System.currentTimeMillis() + muteTime;
            cloudJSON.put("userId", userId)
                    .put("gameId", CloudService.SYS_MUTE)
                    .put("data", "" + time);
            cloudRepository.add(cloudJSON);
            transaction.commit();
            registerSiGuo(userId, time);
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Unable to mute [userId={}]", userId);
        }
    }

    // 禁言并提醒
    public static void muteAndNotice(String username, String userId, int minute) {
        if (minute == 0){
            sendBotMsg("提醒：@" + username + "  被管理员 解除 禁言");
        } else {
            sendBotMsg("提醒：@" + username + "  被管理员 禁言 " + minute + " 分钟。\n *被禁言了也能聊天？* 试试发送一个 **弹幕** 吧！");
        }
        mute(userId, minute);
    }
    
    /**
     * 全员禁言并提醒
     * @param sourceUser
     * @param targetUserId
     * @param minute
     */
    private static void allMuteAndNotice(String sourceUser, String targetUserId, int minute) {
        if (minute == 0){
            sendBotMsg("提醒：管理员 "+sourceUser+" 已解除 全员禁言 。(如存在滥用执法情形, 请及时保留证据, 举报处理)");
        }else {
            sendBotMsg("提醒：管理员 "+sourceUser+" 已开启 全员禁言 " + minute + " 分钟。(如存在滥用执法情形, 请及时保留证据, 举报处理)");
        }
        // 全员禁言的 目标 id 是 specialId, 不存在当前体系
        mute(targetUserId, minute);
    }
    // 检查禁言
    public static int muted(String userId) {
        final BeanManager beanManager = BeanManager.getInstance();
        CloudService cloudService = beanManager.getReference(CloudService.class);
        CloudRepository cloudRepository = beanManager.getReference(CloudRepository.class);
        // 检查是否在全员禁言中  优先级高于个人禁言
        String allMute = cloudService.getFromCloud("all:fish:mute", CloudService.SYS_MUTE);
        // 全员禁言存在 且 有效. 直接返回对象
        if (!allMute.isEmpty()) {
            if ((System.currentTimeMillis() < Long.parseLong(allMute))){
                long remainMinute = (Long.parseLong(allMute) - System.currentTimeMillis()) / 1000;
                // 区别个人设置
                return (- (int) remainMinute);
            }
        }
        // 检查个人禁言
        String muteData = cloudService.getFromCloud(userId, CloudService.SYS_MUTE);
        if (muteData.isEmpty()) {
            return -1;
        } else {
            if (System.currentTimeMillis() > Long.parseLong(muteData)) {
                try {
                    final Transaction transaction = cloudRepository.beginTransaction();
                    Query cloudDeleteQuery = new Query()
                            .setFilter(CompositeFilterOperator.and(
                                    new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                                    new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_MUTE)
                            ));
                    cloudRepository.remove(cloudDeleteQuery);
                    transaction.commit();
                } catch (RepositoryException e) {
                    LOGGER.log(Level.ERROR, "Unable to unmute", e);
                }
                return -1;
            } else {
                long remainMinute = (Long.parseLong(muteData) - System.currentTimeMillis()) / 1000;
                return (int) remainMinute;
            }
        }
    }

    // 定期发送提醒
    public static void notice() {
        String msg = "摸鱼办第一纪律委提醒您：\n" +
                "聊天千万条，友善第一条；\n" +
                "灌水不规范，扣分两行泪。\n" +
                "我正在认真巡逻中，不要被我逮到哦～ :doge:\n" +
                "详细社区守则请看：[摸鱼守则](https://fishpi.cn/article/1631779202219)";
        if (!msg.equals(allLatestMessage)) {
            sendBotMsg(msg);
        }
    }

    // 风控
    public static void risksControl(String userId, int minute) {
        final BeanManager beanManager = BeanManager.getInstance();
        CloudRepository cloudRepository = beanManager.getReference(CloudRepository.class);
        long risksControlTime = (long) minute * 1000 * 60;
        try {
            final Transaction transaction = cloudRepository.beginTransaction();
            Query cloudDeleteQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                            new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_RISK)
                    ));
            cloudRepository.remove(cloudDeleteQuery);
            JSONObject cloudJSON = new JSONObject();
            cloudJSON.put("userId", userId)
                    .put("gameId", CloudService.SYS_RISK)
                    .put("data", ("" + (System.currentTimeMillis() + risksControlTime)));
            cloudRepository.add(cloudJSON);
            transaction.commit();
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Unable to risks control [userId={}]", userId);
        }
    }

    // 风控并提醒
    public static void risksControlAndNotice(String username, String userId, int minute) {
        sendBotMsg("提醒：@" + username + "  被管理员加入风控名单 " + minute + " 分钟。");
        risksControl(userId, minute);
    }

    // 检查风控
    public static int risksControlled(String userId) {
        final BeanManager beanManager = BeanManager.getInstance();
        CloudService cloudService = beanManager.getReference(CloudService.class);
        CloudRepository cloudRepository = beanManager.getReference(CloudRepository.class);

        String risksControlData = cloudService.getFromCloud(userId, CloudService.SYS_RISK);
        if (risksControlData.isEmpty()) {
            return -1;
        } else {
            if (System.currentTimeMillis() > Long.parseLong(risksControlData)) {
                try {
                    final Transaction transaction = cloudRepository.beginTransaction();
                    Query cloudDeleteQuery = new Query()
                            .setFilter(CompositeFilterOperator.and(
                                    new PropertyFilter("userId", FilterOperator.EQUAL, userId),
                                    new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_RISK)
                            ));
                    cloudRepository.remove(cloudDeleteQuery);
                    transaction.commit();
                } catch (RepositoryException e) {
                    LOGGER.log(Level.ERROR, "Unable to un-risks-control", e);
                }
                return -1;
            } else {
                long remainMinute = (Long.parseLong(risksControlData) - System.currentTimeMillis()) / 1000;
                return (int) remainMinute;
            }
        }
    }

    /**
     * 宵禁检测
     */
    public static void nightDisableCheck() {
        int now = Integer.parseInt(new SimpleDateFormat("HHmm").format(new Date()));
        switch (now) {
            case 800:
                sendBotMsg("早上好 ☀️\n" +
                        "新的一天开始啦～ 开始愉快的聊天吧 :D");
                break;
            case 1930:
                sendBotMsg("现在时间是 19:30 分，摸鱼派已进入宵禁模式，期间聊天消息将不会计为活跃度...\n" +
                        "感谢你的陪伴，我们明天再见，早点休息，晚安 \uD83D\uDCA4");
                break;
        }
    }
}
