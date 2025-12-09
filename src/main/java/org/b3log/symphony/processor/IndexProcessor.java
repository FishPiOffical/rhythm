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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.http.renderer.TextHtmlRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.util.CollectionUtils;
import org.b3log.latke.util.Locales;
import org.b3log.latke.util.Paginator;
import org.b3log.latke.util.Stopwatchs;
import org.b3log.symphony.Server;
import org.b3log.symphony.cache.ArticleCache;
import org.b3log.symphony.model.*;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.repository.SponsorRepository;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Index processor.
 * <ul>
 * <li>Shows index (/), GET</li>
 * <li>Show recent articles (/recent), GET</li>
 * <li>Show question articles (/qna), GET</li>
 * <li>Show watch relevant pages (/watch/*), GET</li>
 * <li>Show hot articles (/hot), GET</li>
 * <li>Show perfect articles (/perfect), GET</li>
 * <li>Shows about (/about), GET</li>
 * <li>Shows kill browser (/kill-browser), GET</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 0.2.0
 */
@Singleton
public class IndexProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(IndexProcessor.class);

    /**
     * Article query service.
     */
    @Inject
    private ArticleQueryService articleQueryService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * User management service.
     */
    @Inject
    private UserMgmtService userMgmtService;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Activity query service.
     */
    @Inject
    private ActivityQueryService activityQueryService;

    /**
     * Liveness query service.
     */
    @Inject
    private LivenessQueryService livenessQueryService;

    /**
     * Pointtransfer management service.
     */
    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * Pointtransfer query service.
     */
    @Inject
    private PointtransferQueryService pointtransferQueryService;

    /**
     * Article cache.
     */
    @Inject
    private ArticleCache articleCache;

    /**
     * Breezemoon query service.
     */
    @Inject
    private BreezemoonQueryService breezemoonQueryService;

    @Inject
    private SponsorRepository sponsorRepository;

    @Inject
    private SponsorService sponsorService;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final AnonymousViewCheckMidware anonymousViewCheckMidware = beanManager.getReference(AnonymousViewCheckMidware.class);

        final IndexProcessor indexProcessor = beanManager.getReference(IndexProcessor.class);
        Dispatcher.get("/CHANGE_LOGS.html", indexProcessor::showChangelogs);
        Dispatcher.group().middlewares(loginCheck::handle).router().get().uris(new String[]{"/qna", "/qna/unanswered", "/qna/reward", "/qna/hot"}).handler(indexProcessor::showQnA);
        Dispatcher.group().middlewares(loginCheck::handle).router().get().uris(new String[]{"/watch", "/watch/users"}).handler(indexProcessor::showWatch);
        Dispatcher.get("/", indexProcessor::showIndex, anonymousViewCheckMidware::handle);
        Dispatcher.group().middlewares(loginCheck::handle).router().get().uris(new String[]{"/recent", "/recent/hot", "/recent/good", "/recent/reply"}).handler(indexProcessor::showRecent);
        Dispatcher.get("/about", indexProcessor::showAbout);
        Dispatcher.get("/kill-browser", indexProcessor::showKillBrowser);
        Dispatcher.get("/hot", indexProcessor::showHotArticles, loginCheck::handle);
        Dispatcher.get("/perfect", indexProcessor::showPerfectArticles, loginCheck::handle);
        Dispatcher.get("/charge/point", indexProcessor::showChargePoint, loginCheck::handle);
        Dispatcher.get("/games/handle/", indexProcessor::showHandle, loginCheck::handle);
        Dispatcher.get("/games/adarkroom/", indexProcessor::showADarkRoom, loginCheck::handle);
        Dispatcher.get("/games/lifeRestart/view/", indexProcessor::showLifeRestart, loginCheck::handle);
        Dispatcher.get("/games/emojiPair", indexProcessor::showEmojiPair, loginCheck::handle);
        Dispatcher.get("/games/evolve/", indexProcessor::showEvolve, loginCheck::handle);
        Dispatcher.get("/user/checkedIn", indexProcessor::isCheckedIn, loginCheck::handle);
        Dispatcher.get("/download", indexProcessor::showDownload, anonymousViewCheckMidware::handle);
        Dispatcher.get("/breezemoons", indexProcessor::showBreezemoons, loginCheck::handle);
        Dispatcher.get("/privacy", indexProcessor::showPrivacy, anonymousViewCheckMidware::handle);
        Dispatcher.get("/agreement", indexProcessor::showAgreement, anonymousViewCheckMidware::handle);
    }


    /**
     * 用户协议
     *
     * @param context
     */
    public void showAgreement(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "user-agreement.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }


    /**
     * 隐私政策
     *
     * @param context
     */
    public void showPrivacy(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "privacy.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    /**
     * 清风明月大列表
     */
    public void showBreezemoons(final RequestContext context) {
        final Request request = context.getRequest();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "breezemoons.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final int pageNum = Paginator.getPage(request);
        final int pageSize = 32;
        final int windowSize = 15;

        final JSONObject result = breezemoonQueryService.getBreezemoons("", "", pageNum, pageSize, windowSize);
        final List<JSONObject> bms = (List<JSONObject>) result.opt(Breezemoon.BREEZEMOONS);
        dataModel.put(Breezemoon.BREEZEMOONS, bms);

        final JSONObject pagination = result.optJSONObject(Pagination.PAGINATION);
        final int pageCount = pagination.optInt(Pagination.PAGINATION_PAGE_COUNT);
        final JSONArray pageNums = pagination.optJSONArray(Pagination.PAGINATION_PAGE_NUMS);
        dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.opt(0));
        dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.opt(pageNums.length() - 1));
        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, CollectionUtils.jsonArrayToList(pageNums));

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        dataModel.put(Common.CURRENT, StringUtils.substringAfter(context.requestURI(), "/recent"));
        dataModel.put(Common.SELECTED, Breezemoon.BREEZEMOONS);
    }

    /**
     * 检测用户是否签到
     *
     * @param context
     */
    public void isCheckedIn(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        context.renderJSON(StatusCodes.SUCC).renderJSON(new JSONObject().put("checkedIn", activityQueryService.isCheckedinToday(userId)));
    }

    public void showEvolve(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "games/evolve/index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    public void showEmojiPair(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "games/emojiPair/index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    public void showLifeRestart(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "games/lifeRestart/view/index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    public void showADarkRoom(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "games/adarkroom/index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    public void showHandle(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "games/handle/index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        // 发放积分
        JSONObject currentUser = Sessions.getUser();
        // 校验该用户今日是否已经获得汉兜每日奖励
        List<JSONObject> list = pointtransferQueryService.getLatestPointtransfers(currentUser.optString(Keys.OBJECT_ID), Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_PLAY_HANDLE, 1);
        if (list.isEmpty() || !DateUtils.isSameDay(new Date(), new Date(list.get(0).optLong("time")))) {
            pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, currentUser.optString(Keys.OBJECT_ID),
                    Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_PLAY_HANDLE,
                    60, "", System.currentTimeMillis(), "");
        }
    }

    /**
     * Show changelogs.
     *
     * @param context the specified context
     */
    public void showChangelogs(final RequestContext context) {
        try {
            final TextHtmlRenderer renderer = new TextHtmlRenderer();
            context.setRenderer(renderer);
            try (final InputStream resourceAsStream = IndexProcessor.class.getResourceAsStream("/CHANGE_LOGS.md")) {
                final String content = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8);
                renderer.setContent(Markdowns.toHTML(content));
            }
        } catch (final Exception e) {
            context.sendStatus(500);
        }
    }

    /**
     * Shows question articles.
     *
     * @param context the specified context
     */
    public void showQnA(final RequestContext context) {
        final Request request = context.getRequest();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "qna.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        final int pageNum = Paginator.getPage(request);
        int pageSize = Symphonys.ARTICLE_LIST_CNT;
        final JSONObject user = Sessions.getUser();
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);

            if (!UserExt.finishedGuide(user)) {
                context.sendRedirect(Latkes.getServePath() + "/guide");
                return;
            }
        }

        String sortModeStr = StringUtils.substringAfter(context.requestURI(), "/qna");
        int sortMode;
        switch (sortModeStr) {
            case "":
                sortMode = 0;
                break;
            case "/unanswered":
                sortMode = 1;
                break;
            case "/reward":
                sortMode = 2;
                break;
            case "/hot":
                sortMode = 3;
                break;
            default:
                sortMode = 0;
        }

        dataModel.put(Common.SELECTED, Common.QNA);
        final JSONObject result = articleQueryService.getQuestionArticles(sortMode, pageNum, pageSize);
        final List<JSONObject> allArticles = (List<JSONObject>) result.get(Article.ARTICLES);
        dataModel.put(Common.LATEST_ARTICLES, allArticles);

        final JSONObject pagination = result.getJSONObject(Pagination.PAGINATION);
        final int pageCount = pagination.optInt(Pagination.PAGINATION_PAGE_COUNT);
        final List<Integer> pageNums = (List<Integer>) pagination.get(Pagination.PAGINATION_PAGE_NUMS);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        dataModel.put(Common.CURRENT, StringUtils.substringAfter(context.requestURI(), "/qna"));
    }

    /**
     * Shows watch articles or users.
     *
     * @param context the specified context
     */
    public void showWatch(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "watch.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        int pageSize = Symphonys.ARTICLE_LIST_CNT;
        final JSONObject user = Sessions.getUser();
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);

            if (!UserExt.finishedGuide(user)) {
                context.sendRedirect(Latkes.getServePath() + "/guide");
                return;
            }
        }

        dataModel.put(Common.WATCHING_ARTICLES, Collections.emptyList());
        String sortModeStr = StringUtils.substringAfter(context.requestURI(), "/watch");
        switch (sortModeStr) {
            case "":
                if (null != user) {
                    final List<JSONObject> followingTagArticles = articleQueryService.getFollowingTagArticles(user.optString(Keys.OBJECT_ID), 1, pageSize);
                    dataModel.put(Common.WATCHING_ARTICLES, followingTagArticles);
                }
                break;
            case "/users":
                if (null != user) {
                    final List<JSONObject> followingUserArticles = articleQueryService.getFollowingUserArticles(user.optString(Keys.OBJECT_ID), 1, pageSize);
                    dataModel.put(Common.WATCHING_ARTICLES, followingUserArticles);
                }
                break;
        }

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        dataModel.put(Common.SELECTED, Common.WATCH);
        dataModel.put(Common.CURRENT, StringUtils.substringAfter(context.requestURI(), "/watch"));
    }

    private static final Map<String, Object> indexModelCache = Collections.synchronizedMap(new HashMap<>());

    public synchronized void loadIndexData() {
        Map<String, Object> dataModel = new HashMap<>();

        // 签到排行
        final List<JSONObject> users = activityQueryService.getTopCheckinUsers(10);
        dataModel.put(Common.TOP_CHECKIN_USERS, users);

        // 在线时间排行
        final List<JSONObject> onlineTopUsers = activityQueryService.getTopOnlineTimeUsers(9);
        dataModel.put("onlineTopUsers", onlineTopUsers);

        // 热议
        final List<JSONObject> hotArticles = articleQueryService.getHotArticles(11);
        dataModel.put(Common.HOT, hotArticles);

        // 问题文章
        final JSONObject result = articleQueryService.getQuestionArticles(0, 1, 12);
        final List<JSONObject> qaArticles = (List<JSONObject>) result.get(Article.ARTICLES);
        dataModel.put(Common.QNA,qaArticles);

        // 最近文章
        final List<JSONObject> recentArticles = articleQueryService.getIndexRecentArticles(18, 1);
        dataModel.put(Common.RECENT_ARTICLES, recentArticles);

        // 最近文章第二列
        final List<JSONObject> recentArticles2 = articleQueryService.getIndexRecentArticles(18, 2);
        dataModel.put("recentArticles2", recentArticles2);

        // 最近文章（移动端）
        final List<JSONObject> recentArticlesMobile = articleQueryService.getIndexRecentArticles(15, 1);
        dataModel.put("recentArticlesMobile", recentArticlesMobile);

        // 活跃用户
        final List<JSONObject> niceUsers = userQueryService.getNiceUsers(10);
        dataModel.put(Common.NICE_USERS, niceUsers);

        // 优选文章
        final List<JSONObject> perfectArticles = articleQueryService.getIndexPerfectArticles();
        dataModel.put(Common.PERFECT_ARTICLES, perfectArticles);

        // 摸鱼派版本
        dataModel.put(Common.FISHING_PI_VERSION, Server.FISHING_PI_VERSION);

        // 假期信息
        dataModel.put("vocationData", Vocation.vocationData);

        // 聊天室信息
        List<JSONObject> messages = ChatroomProcessor.getMessages(1,"html");
        for (JSONObject message : messages) {
            String content = message.optString("content");
            Document doc = Jsoup.parse(content);

            // 获取所有的 img 标签
            Elements imgElements = doc.select("img");
            for (Element img : imgElements) {
                String src = img.attr("src");
                // 在 src 后面加上 ?imageView2/0/w/150/h/150/interlace/0/q/90
                img.attr("src", src + "?imageView2/0/w/150/h/150/interlace/0/q/90");
            }

            // 更新 message 中的 content 字段
            message.put("content", doc.html());
        }
        dataModel.put(Common.MESSAGES, messages);

        // 最近注册的新人
        dataModel.put("recentRegUsers", userQueryService.getRecentRegisteredUsers(20));

        // 主页标签
        dataModelService.fillIndexTags(dataModel);

        // TGIF
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        if (calendar.get(Calendar.DAY_OF_WEEK) == 6 && calendar.get(Calendar.HOUR_OF_DAY) > 8) {
            // 周五
            String date = DateFormatUtils.format(new Date(), "yyyyMMdd");
            String articleTitle = "摸鱼周报 " + date;
            JSONObject article = articleQueryService.getArticleByTitle(articleTitle);
            if (article == null) {
                dataModel.put("TGIF", "0");
                dataModel.put("yyyyMMdd", date);
            } else {
                dataModel.put("TGIF", Latkes.getServePath() + article.optString(Article.ARTICLE_PERMALINK));
            }
        } else {
            // 不是周五
            dataModel.put("TGIF", "-1");
        }

        //indexModelCache.clear();
        indexModelCache.putAll(dataModel);
        LOGGER.log(Level.INFO, "Refreshed index model cache.");
    }

    public synchronized void makeIndexData(Map<String, Object> dataModel) {
        if (indexModelCache.isEmpty()) {
            loadIndexData();
        }

        dataModel.putAll(new HashMap<>(indexModelCache));
    }

    /**
     * Shows index.
     *
     * @param context the specified context
     */
    public synchronized void showIndex(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final JSONObject currentUser = Sessions.getUser();
        if (null != currentUser) {
            dataModel.put(UserExt.CHAT_ROOM_PICTURE_STATUS, currentUser.optInt(UserExt.CHAT_ROOM_PICTURE_STATUS));
            // 是否领取过昨日奖励
            final String userId = currentUser.optString(Keys.OBJECT_ID);
            dataModel.put("collectedYesterdayLivenessReward",
                    (
                            // 没领取过，返回true
                            (!activityQueryService.isCollectedYesterdayLivenessReward(userId))
                            // 有奖励，返回true
                            && livenessQueryService.getYesterdayLiveness(userId) != null
                    ) ? 0 : 1);

            dataModel.put("checkedIn", activityQueryService.isCheckedinToday(userId) ? 1 : 0);

            // 用户手机号
            dataModel.put("userPhone", currentUser.optString("userPhone"));

            // 提示绑定2FA
            boolean isAdmin = DataModelService.hasPermission(currentUser.optString(User.USER_ROLE), 3);
            if (isAdmin) {
                String secret = userQueryService.getSecret2fa(userId);
                if (secret.isEmpty()) {
                    dataModel.put("need2fa", "yes");
                } else {
                    dataModel.put("need2fa", "no");
                }
            } else {
                dataModel.put("need2fa", "no");
            }
        } else {
            dataModel.put(UserExt.CHAT_ROOM_PICTURE_STATUS, UserExt.USER_XXX_STATUS_C_ENABLED);
            // 是否领取过昨日奖励
            dataModel.put("collectedYesterdayLivenessReward", 1);
            dataModel.put("checkedIn", 0);
            dataModel.put("userPhone", "not-logged");
            // 提示绑定2FA
            dataModel.put("need2fa", "no");
        }

        makeIndexData(dataModel);

        dataModelService.fillHeaderAndFooter(context, dataModel);

        dataModel.put(Common.SELECTED, Common.INDEX);
    }

    /**
     * Shows recent articles.
     *
     * @param context the specified context
     */
    public void showRecent(final RequestContext context) {
        final Request request = context.getRequest();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "recent.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final int pageNum = Paginator.getPage(request);
        int pageSize = Symphonys.ARTICLE_LIST_CNT;
        final JSONObject user = Sessions.getUser();
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);

            if (!UserExt.finishedGuide(user)) {
                context.sendRedirect(Latkes.getServePath() + "/guide");
                return;
            }
        }

        String sortModeStr = StringUtils.substringAfter(context.requestURI(), "/recent");
        int sortMode;
        switch (sortModeStr) {
            case "":
                sortMode = 0;
                break;
            case "/hot":
                sortMode = 1;
                break;
            case "/good":
                sortMode = 2;
                break;
            case "/reply":
                sortMode = 3;
                break;
            default:
                sortMode = 0;
        }

        dataModel.put(Common.SELECTED, Common.RECENT);
        final JSONObject result = articleQueryService.getRecentArticles(sortMode, pageNum, pageSize);
        final List<JSONObject> allArticles = (List<JSONObject>) result.get(Article.ARTICLES);
        final List<JSONObject> stickArticles = new ArrayList<>();
        final Iterator<JSONObject> iterator = allArticles.iterator();
        while (iterator.hasNext()) {
            final JSONObject article = iterator.next();
            final boolean stick = article.optInt(Article.ARTICLE_T_STICK_REMAINS) > 0;
            article.put(Article.ARTICLE_T_IS_STICK, stick);
            if (stick) {
                stickArticles.add(article);
                iterator.remove();
            }
        }

        dataModel.put(Common.STICK_ARTICLES, stickArticles);
        dataModel.put(Common.LATEST_ARTICLES, allArticles);

        final JSONObject pagination = result.getJSONObject(Pagination.PAGINATION);
        final int pageCount = pagination.optInt(Pagination.PAGINATION_PAGE_COUNT);
        final List<Integer> pageNums = (List<Integer>) pagination.get(Pagination.PAGINATION_PAGE_NUMS);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        dataModel.put(Common.CURRENT, StringUtils.substringAfter(context.requestURI(), "/recent"));
    }

    /**
     * Shows hot articles.
     *
     * @param context the specified context
     */
    public void showHotArticles(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "hot.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        int pageSize = Symphonys.ARTICLE_LIST_CNT;
        final JSONObject user = Sessions.getUser();
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);
        }

        final List<JSONObject> indexArticles = articleQueryService.getHotArticles(pageSize);
        dataModel.put(Common.INDEX_ARTICLES, indexArticles);
        dataModel.put(Common.SELECTED, Common.HOT);

        Stopwatchs.start("Fills");
        try {
            dataModelService.fillHeaderAndFooter(context, dataModel);
            dataModelService.fillRandomArticles(dataModel);
            dataModelService.fillSideHotArticles(dataModel);
            dataModelService.fillSideTags(dataModel);
            dataModelService.fillLatestCmts(dataModel);
        } finally {
            Stopwatchs.end();
        }
    }

    /**
     * Shows perfect articles.
     *
     * @param context the specified context
     */
    public void showPerfectArticles(final RequestContext context) {
        final Request request = context.getRequest();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "perfect.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final int pageNum = Paginator.getPage(request);
        int pageSize = Symphonys.ARTICLE_LIST_CNT;
        final JSONObject user = Sessions.getUser();
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);
            if (!UserExt.finishedGuide(user)) {
                context.sendRedirect(Latkes.getServePath() + "/guide");
                return;
            }
        }

        final JSONObject result = articleQueryService.getPerfectArticles(pageNum, pageSize);
        final List<JSONObject> perfectArticles = (List<JSONObject>) result.get(Article.ARTICLES);
        dataModel.put(Common.PERFECT_ARTICLES, perfectArticles);
        dataModel.put(Common.SELECTED, Common.PERFECT);
        final JSONObject pagination = result.getJSONObject(Pagination.PAGINATION);
        final int pageCount = pagination.optInt(Pagination.PAGINATION_PAGE_COUNT);
        final List<Integer> pageNums = (List<Integer>) pagination.get(Pagination.PAGINATION_PAGE_NUMS);
        if (!pageNums.isEmpty()) {
            dataModel.put(Pagination.PAGINATION_FIRST_PAGE_NUM, pageNums.get(0));
            dataModel.put(Pagination.PAGINATION_LAST_PAGE_NUM, pageNums.get(pageNums.size() - 1));
        }

        dataModel.put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum);
        dataModel.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        dataModel.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Shows download.
     *
     * @param context the specified context
     */
    public void showDownload(final RequestContext context) {
        //下载客户端
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "/download.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }


    /**
     * Shows about.
     *
     * @param context the specified context
     */
    public void showAbout(final RequestContext context) {
        // 关于页主要描述社区愿景、行为准则、内容协议等，并介绍社区的功能
        // 这些内容请搭建后自行编写发布，然后再修改这里进行重定向
        context.sendRedirect(Latkes.getServePath() + "/article/1630569106133");
    }

    /**
     * Shows kill browser page with the specified context.
     *
     * @param context the specified context
     */
    public void showKillBrowser(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "other/kill-browser.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final Map<String, String> langs = langPropsService.getAll(Locales.getLocale());
        dataModel.putAll(langs);
        Keys.fillRuntime(dataModel);
        dataModelService.fillMinified(dataModel);
    }

    /**
     * Shows charge point.
     *
     * @param context the specified context
     */
    public void showChargePoint(final RequestContext context) {
        /*if (context.param("out_trade_no") != null) {
            // 触发交易检查
            AlipayProcessor.checkTrades();
            context.sendRedirect(Latkes.getServePath() + "/charge/point");
            return;
        }*/
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "charge-point.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSponsors(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
        // 个人捐助信息
        dataModel.put("isSponsor", false);
        JSONObject currentUser = Sessions.getUser();
        if (currentUser != null) {
            try {
                String userId = currentUser.optString(Keys.OBJECT_ID);
                List<JSONObject> sponsor = sponsorRepository.listByUserId(userId);
                if (sponsor.size() > 0) {
                    dataModel.put("isSponsor", true);
                    dataModel.put("donateTimes", sponsor.size());
                    double sum = sponsorService.getSum(userId);
                    dataModel.put("donateCount", sum);
                    BigDecimal donateMakeDaysBigDecimal = new BigDecimal(String.valueOf(sum / 13.33));
                    double donateMakeDays = donateMakeDaysBigDecimal.setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
                    dataModel.put("donateMakeDays", donateMakeDays);
                    sponsor = sponsor.stream().peek(x -> {
                        x.put(Common.DATE, new SimpleDateFormat("yyyy-MM-dd").format(x.optLong(Common.TIME)));
                        x.put(Common.TIME, new SimpleDateFormat("HH:mm:ss").format(x.optLong(Common.TIME)));
                        x.remove(UserExt.USER_T_ID);
                    }).collect(Collectors.toList());
                    dataModel.put("donateList", sponsor);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
