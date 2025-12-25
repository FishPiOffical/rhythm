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
import org.b3log.latke.Keys;
import org.b3log.latke.event.Event;
import org.b3log.latke.event.EventManager;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.WebSocketSession;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.util.Paginator;
import org.b3log.symphony.event.EventTypes;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.channel.ChatChannel;
import org.b3log.symphony.processor.channel.UserChannel;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.processor.middleware.UserCheckMidware;
import org.b3log.symphony.repository.ChatInfoRepository;
import org.b3log.symphony.repository.ChatUnreadRepository;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * User processor.
 * <ul>
 * <li>User articles (/member/{userName}), GET</li>
 * <li>User anonymous articles (/member/{userName}/anonymous</li>
 * <li>User comments (/member/{userName}/comments), GET</li>
 * <li>User anonymous comments (/member/{userName}/comments/anonymous</li>
 * <li>User following users (/member/{userName}/following/users), GET</li>
 * <li>User following tags (/member/{userName}/following/tags), GET</li>
 * <li>User following articles (/member/{userName}/following/articles), GET</li>
 * <li>User followers (/member/{userName}/followers), GET</li>
 * <li>User points (/member/{userName}/points), GET</li>
 * <li>User breezemoons (/member/{userName}/breezemoons), GET</li>
 * <li>List usernames (/users/names), GET</li>
 * <li>List frequent emotions (/users/emotions), GET</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://ld246.com/member/ZephyrJung">Zephyr</a>
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 0.2.0
 */
@Singleton
public class UserProcessor {

    /**
     * User management service.
     */
    @Inject
    private UserMgmtService userMgmtService;

    /**
     * Article management service.
     */
    @Inject
    private ArticleQueryService articleQueryService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Comment query service.
     */
    @Inject
    private CommentQueryService commentQueryService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Follow query service.
     */
    @Inject
    private FollowQueryService followQueryService;

    /**
     * Emotion query service.
     */
    @Inject
    private EmotionQueryService emotionQueryService;

    /**
     * Emotion management service.
     */
    @Inject
    private EmotionMgmtService emotionMgmtService;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Avatar query service.
     */
    @Inject
    private AvatarQueryService avatarQueryService;

    /**
     * Pointtransfer query service.
     */
    @Inject
    private PointtransferQueryService pointtransferQueryService;

    /**
     * Pointtransfer management service.
     */
    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * Notification management service.
     */
    @Inject
    private NotificationMgmtService notificationMgmtService;

    /**
     * Option query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

    /**
     * Role query service.
     */
    @Inject
    private RoleQueryService roleQueryService;

    /**
     * Breezemoon query service.
     */
    @Inject
    private BreezemoonQueryService breezemoonQueryService;

    /**
     * System settings service.
     */
    @Inject
    private SystemSettingsService systemSettingsService;

    /**
     * Liveness query service.
     */
    @Inject
    private LivenessQueryService livenessQueryService;

    /**
     * Cloud service.
     */
    @Inject
    private CloudService cloudService;

    @Inject
    private ChatInfoRepository chatInfoRepository;

    @Inject
    private ChatUnreadRepository chatUnreadRepository;

    @Inject
    private EventManager eventManager;

    @Inject
    private MembershipQueryService membershipQueryService;

    /**
     * Cache for liveness.
     */
    public static final Map<String, Float> livenessCache = Collections.synchronizedMap(new LinkedHashMap<String, Float>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 2000;
        }
    });

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final AnonymousViewCheckMidware anonymousViewCheckMidware = beanManager.getReference(AnonymousViewCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);
        final UserCheckMidware userCheckMidware = beanManager.getReference(UserCheckMidware.class);
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);

        final UserProcessor userProcessor = beanManager.getReference(UserProcessor.class);
        Dispatcher.get("/member/{userName}", userProcessor::showHome, anonymousViewCheckMidware::handle, userCheckMidware::handle);
        Dispatcher.group().middlewares(anonymousViewCheckMidware::handle, userCheckMidware::handle, csrfMidware::fill).router().get().uris(new String[]{"/member/{userName}/breezemoons", "/member/{userName}/breezemoons/{breezemoonId}"}).handler(userProcessor::showHomeBreezemoons);
        Dispatcher.get("/member/{userName}/comments/anonymous", userProcessor::showHomeAnonymousComments, userCheckMidware::handle);
        Dispatcher.get("/member/{userName}/articles/anonymous", userProcessor::showAnonymousArticles, userCheckMidware::handle);
        Dispatcher.get("/member/{userName}/comments", userProcessor::showHomeComments, anonymousViewCheckMidware::handle, userCheckMidware::handle);
        Dispatcher.get("/member/{userName}/following/users", userProcessor::showHomeFollowingUsers, anonymousViewCheckMidware::handle, userCheckMidware::handle);
        Dispatcher.get("/member/{userName}/following/tags", userProcessor::showHomeFollowingTags, anonymousViewCheckMidware::handle, userCheckMidware::handle);
        Dispatcher.get("/member/{userName}/following/articles", userProcessor::showHomeFollowingArticles, anonymousViewCheckMidware::handle, userCheckMidware::handle);
        Dispatcher.get("/member/{userName}/watching/articles", userProcessor::showHomeWatchingArticles, anonymousViewCheckMidware::handle, userCheckMidware::handle);
        Dispatcher.get("/member/{userName}/followers", userProcessor::showHomeFollowers, anonymousViewCheckMidware::handle, userCheckMidware::handle);
        Dispatcher.get("/member/{userName}/points", userProcessor::showHomePoints, anonymousViewCheckMidware::handle, userCheckMidware::handle);
        Dispatcher.post("/users/names", userProcessor::listNames);
        Dispatcher.get("/users/emotions", userProcessor::getFrequentEmotions);
        Dispatcher.get("/user/{userName}", userProcessor::getUserInfo);
        Dispatcher.get("/user/liveness", userProcessor::getLiveness, loginCheck::handle);
        Dispatcher.post("/user/liveness", userProcessor::getUserLiveness);
        Dispatcher.get("/user/{userName}/medal", userProcessor::getUserMedal, userCheckMidware::handle);
        Dispatcher.get("/user/{userName}/point", userProcessor::getUserPoint);
        Dispatcher.post("/user/query/latest-login-ip", userProcessor::getLatestLoginIp);
        Dispatcher.post("/user/edit/give-metal", userProcessor::giveMetal);
        Dispatcher.post("/user/edit/remove-metal", userProcessor::removeMetal);
        Dispatcher.post("/user/edit/remove-metal-by-user-id", userProcessor::removeMetalByUserId);
        Dispatcher.post("/user/query/items", userProcessor::getItem);
        Dispatcher.post("/user/edit/items", userProcessor::adjustItem);
        Dispatcher.post("/user/edit/points", userProcessor::adjustPoint);
        Dispatcher.post("/user/identify", userProcessor::submitIdentify, loginCheck::handle);
        Dispatcher.get("/api/user/{userName}/articles", userProcessor::userArticles, loginCheck::handle);
        Dispatcher.get("/api/user/{userName}/breezemoons", userProcessor::userBreezemoons, loginCheck::handle);
    }

    // 根据用户名获取用户活跃度
    public void getUserLiveness(final RequestContext context) {
        JSONObject requestJSONObject = context.requestJSON();
        final String goldFingerKey = requestJSONObject.optString("goldFingerKey");
        final String livenessKey = Symphonys.get("gold.finger.liveness");
        if (goldFingerKey.equals(livenessKey)) {
            final String userName = requestJSONObject.optString(User.USER_NAME);
            final JSONObject user = userQueryService.getUserByName(userName);
            if (null == user) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("用户不存在");
                return;
            }
            final String userId = user.optString(Keys.OBJECT_ID);
            final int livenessMax = Symphonys.ACTIVITY_YESTERDAY_REWARD_MAX;
            final float currentLivenessPoint = livenessQueryService.getCurrentLivenessPoint(userId);
            float liveness = (float) (Math.round((float) currentLivenessPoint / livenessMax * 100 * 100)) / 100;
            final JSONObject ret = new JSONObject();
            ret.put("liveness", liveness);
            context.renderJSON(ret);
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("金手指(liveness类型)不正确。");
        }
    }

    /**
     * 获取用户清风明月列表
     *
     * @param context
     */
    public void userBreezemoons(final RequestContext context) {
        try {
            final int pageNum = Integer.parseInt(context.param("p"));
            final int pageSize = Integer.parseInt(context.param("size"));
            final String userName = StringUtils.isNotBlank(context.pathVar("userName")) ? context.pathVar("userName") : "";
            final int windowSize = 15;
            JSONObject user = userQueryService.getUserByName(userName);
            if (Objects.isNull(user)) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("用户不存在");
                return;
            }
            final JSONObject result = breezemoonQueryService.getBreezemoons("", user.optString(Keys.OBJECT_ID), pageNum, pageSize, windowSize);
            final List<JSONObject> bms = (List<JSONObject>) result.opt(Breezemoon.BREEZEMOONS);
            // 结果去敏
            for (int i = 0; i < bms.size(); i++) {
                bms.get(i).remove("breezemoonIP");
                bms.get(i).remove("breezemoonUA");
                bms.get(i).remove("breezemoonAuthorId");
                bms.get(i).remove("breezemoonStatus");
            }
            context.renderJSON(new JSONObject().put("data", result)).renderCode(StatusCodes.SUCC);
        } catch (Exception e) {
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
        }
    }

    /**
     * 获取用户帖子列表
     *
     * @param context
     */
    public void userArticles(final RequestContext context) {
        final Request request = context.getRequest();
        final Map<String, Object> dataModel = new HashMap<>();
        final int pageNum = Paginator.getPage(request);
        final String size = context.param("size");
        int pageSize = StringUtils.isBlank(size) ? 0 : Integer.parseInt(size);
        pageSize = pageSize <= 0 ? Symphonys.ARTICLE_LIST_CNT : pageSize;
        final String userName = StringUtils.isNotBlank(context.pathVar("userName")) ? context.pathVar("userName") : "";
        try {
            JSONObject user = userQueryService.getUserByName(userName);
            if (Objects.isNull(user)) {
                context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("用户不存在");
                return;
            }
            final List<JSONObject> userArticles = articleQueryService.getUserArticles(user.optString(Keys.OBJECT_ID), Article.ARTICLE_ANONYMOUS_C_PUBLIC, pageNum, pageSize);
            int recordCount = 0;
            int pageCount = 0;
            if (!userArticles.isEmpty()) {
                final JSONObject first = userArticles.get(0);
                pageCount = first.optInt(Pagination.PAGINATION_PAGE_COUNT);
                recordCount = first.optInt(Pagination.PAGINATION_RECORD_COUNT);
            }
            final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;
            final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
            JSONObject pageInfo = new JSONObject();
            dataModel.put("pagination", pageInfo);
            pageInfo.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
            pageInfo.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
            pageInfo.put(Pagination.PAGINATION_RECORD_COUNT, recordCount);
            dataModel.put("articles", DesensitizeUtil.articlesDesensitize(userArticles));
            context.renderJSON(new JSONObject().put("data", dataModel)).renderCode(StatusCodes.SUCC);
        } catch (Exception e) {
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
        }

    }

    public void submitIdentify(final RequestContext context) {
        final JSONObject requestJSONObject = context.requestJSON();
        JSONObject user = Sessions.getUser();
        try {
            user = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        requestJSONObject.put("userName", user.optString(User.USER_NAME));
        try {
            String content = "" +
                    "\uD83C\uDF1F 官方认证申请\n\n" +
                    "* 社区ID：" + requestJSONObject.optString("userName") + "\n" +
                    "* 申请类型：" + requestJSONObject.optString("type") + "\n" +
                    "* 证件照：![](" + requestJSONObject.optString("idCert") + ")\n" +
                    "* 证明：![](" + requestJSONObject.optString("idId") + ")";
            String fromId = userQueryService.getUserByName("admin").optString(Keys.OBJECT_ID);
            String toId = userQueryService.getUserByName("adlered").optString(Keys.OBJECT_ID);
            String chatHex = Strings.uniqueId(new String[]{fromId, toId});
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            content = StringUtils.trim(content);
            if (StringUtils.isBlank(content) || content.length() > 1024) {
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("提交失败，输入不合法");
                return;
            }
            // 存入数据库
            JSONObject chatInfo = new JSONObject();
            chatInfo.put("fromId", fromId);
            chatInfo.put("toId", toId);
            chatInfo.put("user_session", chatHex);
            chatInfo.put("time", time);
            chatInfo.put("content", content);
            String chatInfoOId;
            try {
                Transaction transaction = chatInfoRepository.beginTransaction();
                chatInfoOId = chatInfoRepository.add(chatInfo);
                transaction.commit();
            } catch (RepositoryException e) {
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("提交失败 " + e.getMessage());
                return;
            }
            JSONObject chatUnread = new JSONObject();
            chatUnread.put("fromId", fromId);
            chatUnread.put("toId", toId);
            chatUnread.put("user_session", chatHex);
            try {
                Transaction transaction = chatUnreadRepository.beginTransaction();
                chatUnreadRepository.add(chatUnread);
                transaction.commit();
            } catch (RepositoryException e) {
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("提交失败 " + e.getMessage());
                return;
            }
            // 格式化并发送给WS用户
            try {
                JSONObject info = chatInfoRepository.get(chatInfoOId);
                // 渲染 Markdown
                String markdown = info.optString("content");
                String html = ChatProcessor.processMarkdown(markdown);
                info.put("content", html);
                info.put("markdown", markdown);
                info.put("preview", ChatProcessor.makePreview(markdown));
                // 嵌入用户信息
                JSONObject senderJSON = userQueryService.getUser(fromId);
                info.put("senderUserName", senderJSON.optString(User.USER_NAME));
                info.put("senderAvatar", senderJSON.optString(UserExt.USER_AVATAR_URL));
                JSONObject receiverJSON = userQueryService.getUser(toId);
                if (!toId.equals("1000000000086")) {
                    info.put("receiverUserName", receiverJSON.optString(User.USER_NAME));
                    info.put("receiverAvatar", receiverJSON.optString(UserExt.USER_AVATAR_URL));
                } else {
                    info.put("receiverUserName", "文件传输助手");
                    info.put("receiverAvatar", "https://file.fishpi.cn/2022/06/e1541bfe4138c144285f11ea858b6bf6-ba777366.jpeg");
                }
                // 返回给发送者同样的拷贝
                final Set<WebSocketSession> senderSessions = ChatChannel.SESSIONS.get(chatHex);
                if (senderSessions != null) {
                    for (final WebSocketSession session : senderSessions) {
                        session.sendText(info.toString());
                    }
                }
                // 给接收者发送通知
                final JSONObject cmd = new JSONObject();
                cmd.put(UserExt.USER_T_ID, toId);
                cmd.put(Common.COMMAND, "newIdleChatMessage");
                cmd.put("senderUserName", info.optString("senderUserName"));
                cmd.put("senderAvatar", info.optString("senderAvatar"));
                cmd.put("preview", info.optString("preview"));
                UserChannel.sendCmd(cmd);
            } catch (RepositoryException e) {
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("提交失败 " + e.getMessage());
                return;
            }

            eventManager.fireEventAsynchronously(new Event<>(EventTypes.PRIVATE_CHAT, chatInfo));

            context.renderJSON(StatusCodes.SUCC);
            context.renderMsg("提交成功，我们会在5个工作日内将认证结果通过私信的方式通知您 :)");
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("提交失败，输入不合法 " + e.getMessage());
        }
    }

    /**
     * 金手指：调整积分
     */
    public void adjustPoint(final RequestContext context) {
        JSONObject requestJSONObject = context.requestJSON();
        final String goldFingerKey = requestJSONObject.optString("goldFingerKey");
        final String itemKey = Symphonys.get("gold.finger.point");
        if (goldFingerKey.equals(itemKey)) {
            try {
                final String userName = requestJSONObject.optString("userName");
                JSONObject user = userQueryService.getUserByName(userName);
                try {
                    int point = requestJSONObject.optInt("point");
                    String memo = requestJSONObject.optString("memo");
                    if (memo.isEmpty()) {
                        context.renderJSON(StatusCodes.ERR);
                        context.renderMsg("转账失败：交易备注不得为空。");
                        return;
                    }
                    final String userId = user.optString(Keys.OBJECT_ID);
                    // 打印转账信息
                    System.out.println("金手指-积分：用户: " + userName + ", 积分: " + point + ", 描述: " + memo);
                    if (point > 0) {
                        // 增加积分
                        final String transferId = pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, userId,
                                Pointtransfer.TRANSFER_TYPE_C_ACCOUNT2ACCOUNT, point, userId, System.currentTimeMillis(), memo);
                        if (transferId == null) {
                            context.renderJSON(StatusCodes.ERR);
                            context.renderMsg("转账失败：无法完成交易。");
                            return;
                        }
                        final JSONObject notification = new JSONObject();
                        notification.put(Notification.NOTIFICATION_USER_ID, userId);
                        notification.put(Notification.NOTIFICATION_DATA_ID, transferId);
                        notificationMgmtService.addPointTransferNotification(notification);
                        // === 记录日志 ===
                        LogsService.simpleLog(context, "增加积分", "用户: " + userName + ", 积分: " + point + ", 备注: " + memo);
                        // === 记录日志 ===
                        context.renderJSON(StatusCodes.SUCC);
                        context.renderMsg("转账成功，交易oId为：" + transferId);
                        return;
                    } else if (point < 0) {
                        // 减少积分
                        point = -point;
                        final String transferId = pointtransferMgmtService.transfer(userId, Pointtransfer.ID_C_SYS,
                                Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT, point, memo, System.currentTimeMillis(), "");
                        if (transferId == null) {
                            context.renderJSON(StatusCodes.ERR);
                            context.renderMsg("转账失败：无法完成交易。");
                            return;
                        }
                        final JSONObject notification = new JSONObject();
                        notification.put(Notification.NOTIFICATION_USER_ID, userId);
                        notification.put(Notification.NOTIFICATION_DATA_ID, transferId);
                        notificationMgmtService.addAbusePointDeductNotification(notification);
                        // === 记录日志 ===
                        LogsService.simpleLog(context, "扣除积分", "用户: " + userName + ", 积分: " + point + ", 备注: " + memo);
                        // === 记录日志 ===
                        context.renderJSON(StatusCodes.SUCC);
                        context.renderMsg("扣款成功，交易oId为：" + transferId);
                        return;
                    }
                } catch (Exception e) {
                    context.renderJSON(StatusCodes.ERR);
                    context.renderMsg("转账失败：" + e.getMessage());
                }
            } catch (Exception e) {
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("用户不存在，请检查用户名。");
            }
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("金手指(point类型)不正确。");
        }
        context.renderJSON(StatusCodes.ERR);
        context.renderMsg("转账失败。");
    }

    /**
     * 金手指：调整用户背包
     *
     * @param context
     */
    public void adjustItem(final RequestContext context) {
        JSONObject requestJSONObject = context.requestJSON();
        final String goldFingerKey = requestJSONObject.optString("goldFingerKey");
        final String itemKey = Symphonys.get("gold.finger.item");
        if (goldFingerKey.equals(itemKey)) {
            try {
                final String userName = requestJSONObject.optString("userName");
                JSONObject user = userQueryService.getUserByName(userName);
                final String userId = user.optString(Keys.OBJECT_ID);
                final String item = requestJSONObject.optString("item");
                final int sum = requestJSONObject.optInt("sum");
                cloudService.putBag(userId, item, sum, Integer.MAX_VALUE);
                // === 记录日志 ===
                LogsService.simpleLog(context, "调整用户背包", "用户: " + userName + ", 物品: " + item + ", 数量: " + sum);
                // === 记录日志 ===
                context.renderJSON(StatusCodes.SUCC);
                context.renderMsg("背包调整成功，附当前用户背包内容，请检查数据。");
                context.renderData(new JSONObject(cloudService.getBag(userId)));
            } catch (Exception e) {
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("用户不存在，请检查用户名。");
            }
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("金手指(item类型)不正确。");
        }
    }

    /**
     * 金手指：获取用户背包内容
     *
     * @param context
     */
    public void getItem(final RequestContext context) {
        JSONObject requestJSONObject = context.requestJSON();
        final String goldFingerKey = requestJSONObject.optString("goldFingerKey");
        final String itemKey = Symphonys.get("gold.finger.item");
        if (goldFingerKey.equals(itemKey)) {
            try {
                final String userName = requestJSONObject.optString("userName");
                JSONObject user = userQueryService.getUserByName(userName);
                final String userId = user.optString(Keys.OBJECT_ID);
                context.renderJSON(StatusCodes.SUCC);
                context.renderData(new JSONObject(cloudService.getBag(userId)));
            } catch (Exception e) {
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("用户不存在，请检查用户名。");
            }
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("金手指(item类型)不正确。");
        }
    }

    /**
     * 金手指：移除勋章
     *
     * @param context
     */
    public void removeMetal(final RequestContext context) {
        JSONObject requestJSONObject = context.requestJSON();
        final String goldFingerKey = requestJSONObject.optString("goldFingerKey");
        final String metalKey = Symphonys.get("gold.finger.metal");
        if (goldFingerKey.equals(metalKey)) {
            final String userName = requestJSONObject.optString("userName");
            JSONObject user = userQueryService.getUserByName(userName);
            final String userId = user.optString(Keys.OBJECT_ID);
            final String name = requestJSONObject.optString("name");
            cloudService.removeMedal(userId, name);
            // === 记录日志 ===
            LogsService.simpleLog(context, "移除勋章", "用户: " + userName + ", 勋章名称: " + name);
            // === 记录日志 ===
            context.renderJSON(StatusCodes.SUCC);
            context.renderMsg("勋章移除成功。");
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("金手指(metal类型)不正确。");
        }
    }

    public void removeMetalByUserId(final RequestContext context) {
        JSONObject requestJSONObject = context.requestJSON();
        final String goldFingerKey = requestJSONObject.optString("goldFingerKey");
        final String metalKey = Symphonys.get("gold.finger.metal");
        if (goldFingerKey.equals(metalKey)) {
            final String userId = requestJSONObject.optString("userId");
            JSONObject user = userQueryService.getUser(userId);
            String userName = user.optString(User.USER_NAME);
            final String name = requestJSONObject.optString("name");
            cloudService.removeMedal(userId, name);
            // === 记录日志 ===
            LogsService.simpleLog(context, "移除勋章(使用UserId)", "用户: " + userName + ", 勋章名称: " + name);
            // === 记录日志 ===
            context.renderJSON(StatusCodes.SUCC);
            context.renderMsg("勋章移除成功。");
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("金手指(metal类型)不正确。");
        }
    }

    /**
     * 金手指：添加勋章
     *
     * @param context
     */
    public void giveMetal(final RequestContext context) {
        JSONObject requestJSONObject = context.requestJSON();
        final String goldFingerKey = requestJSONObject.optString("goldFingerKey");
        final String metalKey = Symphonys.get("gold.finger.metal");
        if (goldFingerKey.equals(metalKey)) {
            final String userName = requestJSONObject.optString("userName");
            JSONObject user = userQueryService.getUserByName(userName);
            final String userId = user.optString(Keys.OBJECT_ID);
            final String name = requestJSONObject.optString("name");
            final String description = requestJSONObject.optString("description");
            final String attr = requestJSONObject.optString("attr");
            final String data = requestJSONObject.optString("data");
            final String expireDate = Dates.calExpire(requestJSONObject.optString("expireDate"));
            cloudService.giveMedal(userId, name, description, attr, data, expireDate);
            // === 记录日志 ===
            LogsService.simpleLog(context, "添加勋章", "用户: " + userName + ", 勋章名称: " + name + ", 勋章描述: " + description + ", 勋章属性: " + attr + ", 勋章有效期: " + expireDate);
            // === 记录日志 ===
            context.renderJSON(StatusCodes.SUCC);
            context.renderMsg("勋章安装成功。");
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("金手指(metal类型)不正确。");
        }
    }

    /**
     * 金手指：查询用户最近登录的IP
     *
     * @param context
     */
    public void getLatestLoginIp(final RequestContext context) {
        JSONObject requestJSONObject = context.requestJSON();
        final String goldFingerKey = requestJSONObject.optString("goldFingerKey");
        final String queryKey = Symphonys.get("gold.finger.query");
        if (goldFingerKey.equals(queryKey)) {
            final String userName = requestJSONObject.optString("userName");
            try {
                JSONObject user = userQueryService.getUserByName(userName);
                context.renderJSON(StatusCodes.SUCC);
                context.renderData(new JSONObject().put("userLatestLoginIp", user.optString(UserExt.USER_LATEST_LOGIN_IP)).put("userId", user.optString(Keys.OBJECT_ID)));
            } catch (Exception e) {
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("查询失败，请检查用户名是否正确。");
            }
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("金手指(query类型)不正确。");
        }
    }

    /**
     * 获取用户已启用勋章
     *
     * @param context
     */
    public void getUserMedal(final RequestContext context) {
        final String userName = context.pathVar("userName");
        final JSONObject user = userQueryService.getUserByName(userName);
        String userId = user.optString(Keys.OBJECT_ID);
        JSONObject metal = new JSONObject(cloudService.getEnabledMedal(userId));
        context.renderJSON(StatusCodes.SUCC).renderData(metal);
    }

    /**
     * 获取用户活跃度
     *
     * @param context
     */
    SimpleCurrentLimiter livenessApiQueryCurrentLimiter = new SimpleCurrentLimiter(60 * 9, 1);

    public void getLiveness(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        String userId = currentUser.optString(Keys.OBJECT_ID);
        if (context.param("apiKey") == null || !livenessApiQueryCurrentLimiter.access(userId)) {
            context.sendStatus(500);
            return;
        }
        context.renderJSON(StatusCodes.SUCC).renderJSON(new JSONObject().put("liveness", getLiveness(userId)));
    }

    public static float getLiveness(String userId) {
        final BeanManager beanManager = BeanManager.getInstance();
        final LivenessQueryService livenessQueryService = beanManager.getReference(LivenessQueryService.class);
        if (livenessCache.containsKey(userId)) {
            float liveness = livenessCache.get(userId);
            return liveness;
        } else {
            final int livenessMax = Symphonys.ACTIVITY_YESTERDAY_REWARD_MAX;
            final int currentLiveness = livenessQueryService.getCurrentLivenessPoint(userId);
            float liveness = (float) (Math.round((float) currentLiveness / livenessMax * 100 * 100)) / 100;
            livenessCache.put(userId, liveness);
            return liveness;
        }
    }

    /**
     * Get user info api.
     *
     * @param context
     */
    public void getUserInfo(final RequestContext context) {
        final String userName = context.pathVar("userName");
        final JSONObject user = userQueryService.getUserByName(userName);
        if (null == user) {
            context.renderJSON(StatusCodes.ERR);
            return;
        }
        final JSONObject filteredUserProfile = new JSONObject();
        filteredUserProfile.put(User.USER_NAME, user.optString(User.USER_NAME));
        filteredUserProfile.put(UserExt.USER_ONLINE_FLAG, user.optBoolean(UserExt.USER_ONLINE_FLAG));
        filteredUserProfile.put(UserExt.ONLINE_MINUTE, user.optInt(UserExt.ONLINE_MINUTE));
        filteredUserProfile.put(User.USER_URL, user.optString(User.USER_URL));
        filteredUserProfile.put(UserExt.USER_NICKNAME, user.optString(UserExt.USER_NICKNAME));
        try {
            if (user.optInt(UserExt.USER_GEO_STATUS) == UserExt.USER_GEO_STATUS_C_PUBLIC) {
                filteredUserProfile.put(UserExt.USER_CITY, user.optString(UserExt.USER_CITY));
            } else {
                filteredUserProfile.put(UserExt.USER_CITY, "");
            }
        } catch (Exception ignored) {
            filteredUserProfile.put(UserExt.USER_CITY, "");
        }
        filteredUserProfile.put(UserExt.USER_AVATAR_URL, user.optString(UserExt.USER_AVATAR_URL));
        avatarQueryService.fillUserAvatarURL(filteredUserProfile);
        filteredUserProfile.put(UserExt.USER_POINT, user.optInt(UserExt.USER_POINT));
        filteredUserProfile.put(UserExt.USER_INTRO, user.optString(UserExt.USER_INTRO));
        filteredUserProfile.put(Keys.OBJECT_ID, user.optString(Keys.OBJECT_ID));
        filteredUserProfile.put(UserExt.USER_NO, user.optString(UserExt.USER_NO));
        filteredUserProfile.put(UserExt.USER_APP_ROLE, user.optString(UserExt.USER_APP_ROLE));
        filteredUserProfile.put("sysMetal", cloudService.getEnabledMedal(user.optString(Keys.OBJECT_ID)));
        filteredUserProfile.put("allMetalOwned", cloudService.getMedal(user.optString(Keys.OBJECT_ID)));
        final String userId = user.optString(Keys.OBJECT_ID);
        final long followerCnt = followQueryService.getFollowerCount(userId, Follow.FOLLOWING_TYPE_C_USER);
        filteredUserProfile.put("followerCount", followerCnt);
        final long followingUserCnt = followQueryService.getFollowingCount(userId, Follow.FOLLOWING_TYPE_C_USER);
        filteredUserProfile.put("followingUserCount", followingUserCnt);
        final String userRoleId = user.optString(User.USER_ROLE);
        final JSONObject role = roleQueryService.getRole(userRoleId);
        final String roleName = role.optString(Role.ROLE_NAME);
        filteredUserProfile.put(User.USER_ROLE, roleName);
        filteredUserProfile.put("mbti", user.optString("mbti"));
        // 获取用户个性化设定
        final JSONObject systemSettings = systemSettingsService.getByUsrId(user.optString(Keys.OBJECT_ID));
        if (Objects.isNull(systemSettings)) {
            filteredUserProfile.put("cardBg", "");
        } else {
            final String settingsJson = systemSettings.optString(SystemSettings.SETTINGS);
            final JSONObject settings = new JSONObject(settingsJson);
            final String cardBg = settings.optString("cardBg");
            if (StringUtils.isBlank(cardBg)) {
                filteredUserProfile.put("cardBg", "");
            } else {
                filteredUserProfile.put("cardBg", cardBg);
            }
        }
        // 检查用户是否关注过这个用户
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
            } catch (NullPointerException ignored) {
            }
            if (currentUser == null) {
                // 用户未登录
                filteredUserProfile.put("canFollow", "hide");
            } else {
                final String currentUserId = currentUser.optString(Keys.OBJECT_ID);
                if (currentUserId.equals(userId)) {
                    // 看的是自己
                    filteredUserProfile.put("canFollow", "hide");
                } else {
                    final boolean isFollowing = followQueryService.isFollowing(currentUserId, userId, Follow.FOLLOWING_TYPE_C_USER);
                    if (isFollowing) {
                        filteredUserProfile.put("canFollow", "no");
                    } else {
                        filteredUserProfile.put("canFollow", "yes");
                    }
                }
            }
        } catch (Exception e) {
            filteredUserProfile.put("canFollow", "hide");
        }

        context.renderJSON(StatusCodes.SUCC).renderJSON(filteredUserProfile);
    }

    /**
     * Shows user home breezemoons page.
     *
     * @param context the specified context
     */
    public void showHomeBreezemoons(final RequestContext context) {
        final String breezemoonId = context.pathVar("breezemoonId");
        final Request request = context.getRequest();

        final JSONObject user = (JSONObject) context.attr(User.USER);

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/breezemoons.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        final int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.USER_HOME_LIST_CNT;
        final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;

        fillHomeUser(dataModel, user, roleQueryService);

        avatarQueryService.fillUserAvatarURL(user);

        final String followingId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, followingId);

        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        JSONObject currentUser;
        String currentUserId = null;
        if (isLoggedIn) {
            currentUser = Sessions.getUser();
            currentUserId = currentUser.optString(Keys.OBJECT_ID);

            final boolean isFollowing = followQueryService.isFollowing(currentUserId, followingId, Follow.FOLLOWING_TYPE_C_USER);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);
        }

        final JSONObject result = breezemoonQueryService.getBreezemoons(currentUserId, followingId, pageNum, pageSize, windowSize);
        List<JSONObject> bms = (List<JSONObject>) result.opt(Breezemoon.BREEZEMOONS);
        dataModel.put(Common.USER_HOME_BREEZEMOONS, bms);

        final JSONObject pagination = result.optJSONObject(Pagination.PAGINATION);
        final int recordCount = pagination.optInt(Pagination.PAGINATION_RECORD_COUNT);
        final int pageCount = (int) Math.ceil(recordCount / (double) pageSize);
        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }
        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put(Pagination.PAGINATION_RECORD_COUNT, recordCount);

        dataModel.put(Common.TYPE, Breezemoon.BREEZEMOONS);

        if (StringUtils.isNotBlank(breezemoonId)) {
            dataModel.put(Common.IS_SINGLE_BREEZEMOON_URL, true);
            final JSONObject breezemoon = breezemoonQueryService.getBreezemoon(breezemoonId);
            if (null == breezemoon) {
                context.sendError(404);
                return;
            }

            bms = Collections.singletonList(breezemoon);
            breezemoonQueryService.organizeBreezemoons("admin", bms);
            dataModel.put(Common.USER_HOME_BREEZEMOONS, bms);
        } else {
            dataModel.put(Common.IS_SINGLE_BREEZEMOON_URL, false);
        }
    }

    /**
     * Shows user home anonymous comments page.
     *
     * @param context the specified context
     */
    public void showHomeAnonymousComments(final RequestContext context) {
        final Request request = context.getRequest();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/comments.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);

        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        JSONObject currentUser = null;
        if (isLoggedIn) {
            currentUser = Sessions.getUser();
        }

        final JSONObject user = (JSONObject) context.attr(User.USER);

        if (null == currentUser || (!currentUser.optString(Keys.OBJECT_ID).equals(user.optString(Keys.OBJECT_ID)))
                && !Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE))) {
            context.sendError(404);
            return;
        }

        final int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.USER_HOME_LIST_CNT;
        final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;

        fillHomeUser(dataModel, user, roleQueryService);

        avatarQueryService.fillUserAvatarURL(user);

        final String followingId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, followingId);

        if (isLoggedIn) {
            currentUser = Sessions.getUser();
            final String followerId = currentUser.optString(Keys.OBJECT_ID);

            final boolean isFollowing = followQueryService.isFollowing(followerId, followingId, Follow.FOLLOWING_TYPE_C_USER);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);
        }

        final List<JSONObject> userComments = commentQueryService.getUserComments(user.optString(Keys.OBJECT_ID), Comment.COMMENT_ANONYMOUS_C_ANONYMOUS, pageNum, pageSize, currentUser);
        dataModel.put(Common.USER_HOME_COMMENTS, userComments);

        int recordCount = 0;
        int pageCount = 0;
        if (!userComments.isEmpty()) {
            final JSONObject first = userComments.get(0);
            pageCount = first.optInt(Pagination.PAGINATION_PAGE_COUNT);
            recordCount = first.optInt(Pagination.PAGINATION_RECORD_COUNT);
        }

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put(Pagination.PAGINATION_RECORD_COUNT, recordCount);

        dataModel.put(Common.TYPE, "commentsAnonymous");
    }

    /**
     * Shows user home anonymous articles page.
     *
     * @param context the specified context
     */
    public void showAnonymousArticles(final RequestContext context) {
        final String userName = context.pathVar("userName");
        final Request request = context.getRequest();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/home.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);

        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        JSONObject currentUser = null;
        if (isLoggedIn) {
            currentUser = Sessions.getUser();
        }

        final JSONObject user = (JSONObject) context.attr(User.USER);

        if (null == currentUser || (!currentUser.optString(Keys.OBJECT_ID).equals(user.optString(Keys.OBJECT_ID)))
                && !Role.ROLE_ID_C_ADMIN.equals(currentUser.optString(User.USER_ROLE))) {
            context.sendError(404);
            return;
        }

        final int pageNum = Paginator.getPage(request);
        final String followingId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, followingId);

        fillHomeUser(dataModel, user, roleQueryService);

        avatarQueryService.fillUserAvatarURL(user);

        if (isLoggedIn) {
            final String followerId = currentUser.optString(Keys.OBJECT_ID);

            final boolean isFollowing = followQueryService.isFollowing(followerId, followingId, Follow.FOLLOWING_TYPE_C_USER);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);
        }

        final int pageSize = Symphonys.USER_HOME_LIST_CNT;
        final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;

        final List<JSONObject> userArticles = articleQueryService.getUserArticles(user.optString(Keys.OBJECT_ID), Article.ARTICLE_ANONYMOUS_C_ANONYMOUS, pageNum, pageSize);
        dataModel.put(Common.USER_HOME_ARTICLES, userArticles);

        int recordCount = 0;
        int pageCount = 0;
        if (!userArticles.isEmpty()) {
            final JSONObject first = userArticles.get(0);
            pageCount = first.optInt(Pagination.PAGINATION_PAGE_COUNT);
            recordCount = first.optInt(Pagination.PAGINATION_RECORD_COUNT);
        }

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put(Pagination.PAGINATION_RECORD_COUNT, recordCount);

        dataModel.put(Common.IS_MY_ARTICLE, userName.equals(currentUser.optString(User.USER_NAME)));

        dataModel.put(Common.TYPE, "articlesAnonymous");
    }

    /**
     * Shows user home page.
     *
     * @param context the specified context
     */
    public void showHome(final RequestContext context) {
        final String userName = context.pathVar("userName");
        final Request request = context.getRequest();

        final JSONObject user = (JSONObject) context.attr(User.USER);
        final int pageNum = Paginator.getPage(request);
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/home.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);

        final String followingId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, followingId);

        fillHomeUser(dataModel, user, roleQueryService);

        avatarQueryService.fillUserAvatarURL(user);

        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        if (isLoggedIn) {
            final JSONObject currentUser = Sessions.getUser();
            final String followerId = currentUser.optString(Keys.OBJECT_ID);

            final boolean isFollowing = followQueryService.isFollowing(followerId, followingId, Follow.FOLLOWING_TYPE_C_USER);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);
        }

        final int pageSize = Symphonys.USER_HOME_LIST_CNT;
        final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;

        final List<JSONObject> userArticles = articleQueryService.getUserArticles(user.optString(Keys.OBJECT_ID), Article.ARTICLE_ANONYMOUS_C_PUBLIC, pageNum, pageSize);
        dataModel.put(Common.USER_HOME_ARTICLES, userArticles);

        int recordCount = 0;
        int pageCount = 0;
        if (!userArticles.isEmpty()) {
            final JSONObject first = userArticles.get(0);
            pageCount = first.optInt(Pagination.PAGINATION_PAGE_COUNT);
            recordCount = first.optInt(Pagination.PAGINATION_RECORD_COUNT);
        }

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put(Pagination.PAGINATION_RECORD_COUNT, recordCount);

        final JSONObject currentUser = Sessions.getUser();
        if (null == currentUser) {
            dataModel.put(Common.IS_MY_ARTICLE, false);
        } else {
            dataModel.put(Common.IS_MY_ARTICLE, userName.equals(currentUser.optString(User.USER_NAME)));
        }

        dataModel.put(Common.TYPE, "home");
    }

    /**
     * Shows user home comments page.
     *
     * @param context the specified context
     */
    public void showHomeComments(final RequestContext context) {
        final Request request = context.getRequest();

        final JSONObject user = (JSONObject) context.attr(User.USER);

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/comments.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        final int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.USER_HOME_LIST_CNT;
        final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;

        fillHomeUser(dataModel, user, roleQueryService);

        avatarQueryService.fillUserAvatarURL(user);

        final String followingId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, followingId);

        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        JSONObject currentUser = null;
        if (isLoggedIn) {
            currentUser = Sessions.getUser();
            final String followerId = currentUser.optString(Keys.OBJECT_ID);

            final boolean isFollowing = followQueryService.isFollowing(followerId, followingId, Follow.FOLLOWING_TYPE_C_USER);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);
        }

        final List<JSONObject> userComments = commentQueryService.getUserComments(user.optString(Keys.OBJECT_ID), Comment.COMMENT_ANONYMOUS_C_PUBLIC, pageNum, pageSize, currentUser);
        dataModel.put(Common.USER_HOME_COMMENTS, userComments);

        int recordCount = 0;
        int pageCount = 0;
        if (!userComments.isEmpty()) {
            final JSONObject first = userComments.get(0);
            pageCount = first.optInt(Pagination.PAGINATION_PAGE_COUNT);
            recordCount = first.optInt(Pagination.PAGINATION_RECORD_COUNT);
        }

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put(Pagination.PAGINATION_RECORD_COUNT, recordCount);

        dataModel.put(Common.TYPE, "comments");
    }

    /**
     * Shows user home following users page.
     *
     * @param context the specified context
     */
    public void showHomeFollowingUsers(final RequestContext context) {
        final Request request = context.getRequest();

        final JSONObject user = (JSONObject) context.attr(User.USER);

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/following-users.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        final int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.USER_HOME_LIST_CNT;
        final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;

        fillHomeUser(dataModel, user, roleQueryService);

        final String followingId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, followingId);

        avatarQueryService.fillUserAvatarURL(user);

        final JSONObject followingUsersResult = followQueryService.getFollowingUsers(followingId, pageNum, pageSize);
        final List<JSONObject> followingUsers = (List<JSONObject>) followingUsersResult.opt(Keys.RESULTS);
        dataModel.put(Common.USER_HOME_FOLLOWING_USERS, followingUsers);

        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        if (isLoggedIn) {
            final JSONObject currentUser = Sessions.getUser();
            final String followerId = currentUser.optString(Keys.OBJECT_ID);

            final boolean isFollowing = followQueryService.isFollowing(followerId, followingId, Follow.FOLLOWING_TYPE_C_USER);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);

            for (final JSONObject followingUser : followingUsers) {
                final String homeUserFollowingUserId = followingUser.optString(Keys.OBJECT_ID);

                followingUser.put(Common.IS_FOLLOWING, followQueryService.isFollowing(followerId, homeUserFollowingUserId, Follow.FOLLOWING_TYPE_C_USER));
            }
        }

        final int followingUserCnt = followingUsersResult.optInt(Pagination.PAGINATION_RECORD_COUNT);
        final int pageCount = (int) Math.ceil((double) followingUserCnt / (double) pageSize);

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put(Pagination.PAGINATION_RECORD_COUNT, followingUserCnt);

        dataModel.put(Common.TYPE, "followingUsers");
    }

    /**
     * Shows user home following tags page.
     *
     * @param context the specified context
     */
    public void showHomeFollowingTags(final RequestContext context) {
        final Request request = context.getRequest();

        final JSONObject user = (JSONObject) context.attr(User.USER);

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/following-tags.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        final int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.USER_HOME_LIST_CNT;
        final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;

        fillHomeUser(dataModel, user, roleQueryService);

        final String followingId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, followingId);

        avatarQueryService.fillUserAvatarURL(user);

        final JSONObject followingTagsResult = followQueryService.getFollowingTags(followingId, pageNum, pageSize);
        final List<JSONObject> followingTags = (List<JSONObject>) followingTagsResult.opt(Keys.RESULTS);
        dataModel.put(Common.USER_HOME_FOLLOWING_TAGS, followingTags);

        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        if (isLoggedIn) {
            final JSONObject currentUser = Sessions.getUser();
            final String followerId = currentUser.optString(Keys.OBJECT_ID);

            final boolean isFollowing = followQueryService.isFollowing(followerId, followingId, Follow.FOLLOWING_TYPE_C_USER);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);

            for (final JSONObject followingTag : followingTags) {
                final String homeUserFollowingTagId = followingTag.optString(Keys.OBJECT_ID);

                followingTag.put(Common.IS_FOLLOWING, followQueryService.isFollowing(followerId, homeUserFollowingTagId, Follow.FOLLOWING_TYPE_C_TAG));
            }
        }

        final int followingTagCnt = followingTagsResult.optInt(Pagination.PAGINATION_RECORD_COUNT);
        final int pageCount = (int) Math.ceil(followingTagCnt / (double) pageSize);

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put(Pagination.PAGINATION_RECORD_COUNT, followingTagCnt);

        dataModel.put(Common.TYPE, "followingTags");
    }

    /**
     * Shows user home following articles page.
     *
     * @param context the specified context
     */
    public void showHomeFollowingArticles(final RequestContext context) {
        final Request request = context.getRequest();

        final JSONObject user = (JSONObject) context.attr(User.USER);

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/following-articles.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        final int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.USER_HOME_LIST_CNT;
        final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;

        fillHomeUser(dataModel, user, roleQueryService);

        final String followingId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, followingId);

        avatarQueryService.fillUserAvatarURL(user);

        final JSONObject followingArticlesResult = followQueryService.getFollowingArticles(followingId, pageNum, pageSize);
        final List<JSONObject> followingArticles = (List<JSONObject>) followingArticlesResult.opt(Keys.RESULTS);
        dataModel.put(Common.USER_HOME_FOLLOWING_ARTICLES, followingArticles);

        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        if (isLoggedIn) {
            final JSONObject currentUser = Sessions.getUser();
            final String followerId = currentUser.optString(Keys.OBJECT_ID);

            final boolean isFollowing = followQueryService.isFollowing(followerId, followingId, Follow.FOLLOWING_TYPE_C_USER);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);

            for (final JSONObject followingArticle : followingArticles) {
                final String homeUserFollowingArticleId = followingArticle.optString(Keys.OBJECT_ID);

                followingArticle.put(Common.IS_FOLLOWING, followQueryService.isFollowing(followerId, homeUserFollowingArticleId, Follow.FOLLOWING_TYPE_C_ARTICLE));
            }
        }

        final int followingArticleCnt = followingArticlesResult.optInt(Pagination.PAGINATION_RECORD_COUNT);
        final int pageCount = (int) Math.ceil(followingArticleCnt / (double) pageSize);

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put(Pagination.PAGINATION_RECORD_COUNT, followingArticleCnt);

        dataModel.put(Common.TYPE, "followingArticles");
    }

    /**
     * Shows user home watching articles page.
     *
     * @param context the specified context
     */
    public void showHomeWatchingArticles(final RequestContext context) {
        final Request request = context.getRequest();

        final JSONObject user = (JSONObject) context.attr(User.USER);

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/watching-articles.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        final int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.USER_HOME_LIST_CNT;
        final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;

        fillHomeUser(dataModel, user, roleQueryService);

        final String followingId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, followingId);

        avatarQueryService.fillUserAvatarURL(user);

        final JSONObject followingArticlesResult = followQueryService.getWatchingArticles(followingId, pageNum, pageSize);
        final List<JSONObject> followingArticles = (List<JSONObject>) followingArticlesResult.opt(Keys.RESULTS);
        dataModel.put(Common.USER_HOME_FOLLOWING_ARTICLES, followingArticles);

        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        if (isLoggedIn) {
            final JSONObject currentUser = Sessions.getUser();
            final String followerId = currentUser.optString(Keys.OBJECT_ID);

            final boolean isFollowing = followQueryService.isFollowing(followerId, followingId, Follow.FOLLOWING_TYPE_C_USER);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);

            for (final JSONObject followingArticle : followingArticles) {
                final String homeUserFollowingArticleId = followingArticle.optString(Keys.OBJECT_ID);

                followingArticle.put(Common.IS_FOLLOWING, followQueryService.isFollowing(followerId, homeUserFollowingArticleId, Follow.FOLLOWING_TYPE_C_ARTICLE_WATCH));
            }
        }

        final int followingArticleCnt = followingArticlesResult.optInt(Pagination.PAGINATION_RECORD_COUNT);
        final int pageCount = (int) Math.ceil(followingArticleCnt / (double) pageSize);

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put(Pagination.PAGINATION_RECORD_COUNT, followingArticleCnt);

        dataModel.put(Common.TYPE, "watchingArticles");
    }

    /**
     * Shows user home follower users page.
     *
     * @param context the specified context
     */
    public void showHomeFollowers(final RequestContext context) {
        final Request request = context.getRequest();

        final JSONObject user = (JSONObject) context.attr(User.USER);

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/followers.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        final int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.USER_HOME_LIST_CNT;
        final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;

        fillHomeUser(dataModel, user, roleQueryService);

        final String followingId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, followingId);

        final JSONObject followerUsersResult = followQueryService.getFollowerUsers(followingId, pageNum, pageSize);
        final List<JSONObject> followerUsers = (List) followerUsersResult.opt(Keys.RESULTS);
        dataModel.put(Common.USER_HOME_FOLLOWER_USERS, followerUsers);

        avatarQueryService.fillUserAvatarURL(user);

        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        if (isLoggedIn) {
            final JSONObject currentUser = Sessions.getUser();
            final String followerId = currentUser.optString(Keys.OBJECT_ID);

            final boolean isFollowing = followQueryService.isFollowing(followerId, followingId, Follow.FOLLOWING_TYPE_C_USER);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);

            for (final JSONObject followerUser : followerUsers) {
                final String homeUserFollowerUserId = followerUser.optString(Keys.OBJECT_ID);

                followerUser.put(Common.IS_FOLLOWING, followQueryService.isFollowing(followerId, homeUserFollowerUserId, Follow.FOLLOWING_TYPE_C_USER));
            }

            if (followerId.equals(followingId)) {
                notificationMgmtService.makeRead(followingId, Notification.DATA_TYPE_C_NEW_FOLLOWER);
            }
        }

        final int followerUserCnt = followerUsersResult.optInt(Pagination.PAGINATION_RECORD_COUNT);
        final int pageCount = (int) Math.ceil((double) followerUserCnt / (double) pageSize);

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);
        dataModel.put(Pagination.PAGINATION_RECORD_COUNT, followerUserCnt);

        dataModel.put(Common.TYPE, "followers");

        notificationMgmtService.makeRead(followingId, Notification.DATA_TYPE_C_NEW_FOLLOWER);
    }

    /**
     * Shows user home points page.
     *
     * @param context the specified context
     */
    public void showHomePoints(final RequestContext context) {
        final Request request = context.getRequest();

        final JSONObject user = (JSONObject) context.attr(User.USER);

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/points.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        final int pageNum = Paginator.getPage(request);
        final int pageSize = Symphonys.USER_HOME_LIST_CNT;
        final int windowSize = Symphonys.USER_HOME_LIST_WIN_SIZE;

        fillHomeUser(dataModel, user, roleQueryService);

        avatarQueryService.fillUserAvatarURL(user);

        final String followingId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, followingId);

        final JSONObject userPointsResult = pointtransferQueryService.getUserPoints(user.optString(Keys.OBJECT_ID), pageNum, pageSize);
        JSONArray arr = userPointsResult.optJSONArray(Keys.RESULTS);
        List<JSONObject> userPoints = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                userPoints.add(arr.getJSONObject(i));
            }
        }
        dataModel.put(Common.USER_HOME_POINTS, userPoints);

        final boolean isLoggedIn = (Boolean) dataModel.get(Common.IS_LOGGED_IN);
        if (isLoggedIn) {
            final JSONObject currentUser = Sessions.getUser();
            final String followerId = currentUser.optString(Keys.OBJECT_ID);

            final boolean isFollowing = followQueryService.isFollowing(followerId, user.optString(Keys.OBJECT_ID), Follow.FOLLOWING_TYPE_C_USER);
            dataModel.put(Common.IS_FOLLOWING, isFollowing);
        }

        final int pointsCnt = userPointsResult.optInt(Pagination.PAGINATION_RECORD_COUNT);
        final int pageCount = (int) Math.ceil((double) pointsCnt / (double) pageSize);

        final List<Integer> pageNums = Paginator.paginate(pageNum, pageSize, pageCount, windowSize);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        dataModel.put(Common.TYPE, "points");
    }

    /**
     * List usernames.
     *
     * @param context the specified context
     */
    public void listNames(final RequestContext context) {
        final JSONObject result = Results.newSucc();
        context.renderJSON(result);

        final JSONObject requestJSON = context.requestJSON();
        final String namePrefix = requestJSON.optString("name");
        if (StringUtils.isBlank(namePrefix)) {
            final List<JSONObject> admins = userQueryService.getAdmins();
            final List<JSONObject> userNames = new ArrayList<>();
            for (final JSONObject admin : admins) {
                final JSONObject userName = new JSONObject();
                userName.put(User.USER_NAME, admin.optString(User.USER_NAME));
                final String avatar = avatarQueryService.getAvatarURLByUser(admin, "20");
                userName.put(UserExt.USER_AVATAR_URL, avatar);
                avatarQueryService.fillUserAvatarURL(userName);

                userNames.add(userName);
            }

            result.put(Common.DATA, userNames);
            return;
        }

        final List<JSONObject> userNames = userQueryService.getUserNamesByPrefix(namePrefix);
        for (JSONObject user : userNames) {
            avatarQueryService.fillUserAvatarURL(user);
        }
        result.put(Common.DATA, userNames);
    }

    /**
     * 获取用户积分余额
     *
     * @param context the specified context
     */
    public void getUserPoint(final RequestContext context) {
        final String userName = context.pathVar("userName");
        final JSONObject user = userQueryService.getUserByName(userName);
        if (null == user) {
            context.renderJSON(StatusCodes.ERR).renderMsg("用户不存在");
            return;
        }
        final JSONObject result = new JSONObject();
        result.put(User.USER_NAME, userName);
        result.put(UserExt.USER_POINT, user.optInt(UserExt.USER_POINT));
        context.renderJSON(StatusCodes.SUCC).renderData(result);
    }

    /**
     * List frequent emotions.
     *
     * @param context the specified context
     */
    public void getFrequentEmotions(final RequestContext context) {
        final JSONObject result = Results.newSucc();
        context.renderJSON(result);

        final List<JSONObject> data = new ArrayList<>();
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        if (null == currentUser) {
            result.put(Common.DATA, data);
            return;
        }

        final String userId = currentUser.optString(Keys.OBJECT_ID);
        String emotions = emotionQueryService.getEmojis(userId);
        final String[] emojis = emotions.split(",");
        for (final String emoji : emojis) {
            String emojiChar = Emotions.toUnicode(":" + emoji + ":");
            if (StringUtils.contains(emojiChar, ":")) {
                final String suffix = "huaji".equals(emoji) ? ".gif" : ".png";
                emojiChar = "https://file.fishpi.cn/emoji/graphics/" + emoji + suffix;
            }

            data.add(new JSONObject().put(emoji, emojiChar));
        }

        result.put(Common.DATA, data);
    }

    /**
     * Fills home user.
     *
     * @param dataModel the specified data model
     * @param user      the specified user
     */
    static void fillHomeUser(final Map<String, Object> dataModel, final JSONObject user, final RoleQueryService roleQueryService) {
        Escapes.escapeHTML(user);
        dataModel.put(User.USER, user);
        final BeanManager beanManager = BeanManager.getInstance();
        FollowQueryService followQueryService = beanManager.getReference(FollowQueryService.class);
        final String userId = user.optString(Keys.OBJECT_ID);
        dataModel.put(Follow.FOLLOWING_ID, userId);
        final long followerCnt = followQueryService.getFollowerCount(userId, Follow.FOLLOWING_TYPE_C_USER);
        dataModel.put("followerCount", followerCnt);
        final long followingUserCnt = followQueryService.getFollowingCount(userId, Follow.FOLLOWING_TYPE_C_USER);
        dataModel.put("followingUserCount", followingUserCnt);
        UserQueryService userQueryService = beanManager.getReference(UserQueryService.class);
        dataModel.put(UserExt.ONLINE_MINUTE, userQueryService.getOnlineMinute(userId));

        final String roleId = user.optString(User.USER_ROLE);
        final JSONObject role = roleQueryService.getRole(roleId);
        user.put(Role.ROLE_NAME, role.optString(Role.ROLE_NAME));
        if (user.has(UserExt.USER_LATEST_LOGIN_TIME) && Objects.nonNull(user.get(UserExt.USER_LATEST_LOGIN_TIME))) {
            user.put(UserExt.USER_LATEST_LOGIN_TIME, new Date(user.getLong(UserExt.USER_LATEST_LOGIN_TIME)));
        }
        user.put(UserExt.USER_T_CREATE_TIME, new Date(user.optLong(Keys.OBJECT_ID)));

        // 获取用户个性化设定
        final SystemSettingsService systemSettingsService = beanManager.getReference(SystemSettingsService.class);
        final JSONObject systemSettings = systemSettingsService.getByUsrId(user.optString(Keys.OBJECT_ID));
        if (Objects.isNull(systemSettings)) {
            user.put("cardBg", "");
        } else {
            final String settingsJson = systemSettings.optString(SystemSettings.SETTINGS);
            final JSONObject settings = new JSONObject(settingsJson);
            final String cardBg = settings.optString("cardBg");
            if (StringUtils.isBlank(cardBg)) {
                user.put("cardBg", "");
            } else {
                user.put("cardBg", cardBg);
            }
        }
    }
}
