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
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.Paginator;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.Breezemoon;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.bot.ChatRoomBot;
import org.b3log.symphony.processor.channel.ChatChannel;
import org.b3log.symphony.processor.channel.UserChannel;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.processor.middleware.PermissionMidware;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Breezemoon processor. https://github.com/b3log/symphony/issues/507
 *
 * <ul>
 * <li>Shows watch breezemoons (/watch/breezemoons), GET</li>
 * <li>Adds a breezemoon (/breezemoon), POST</li>
 * <li>Updates a breezemoon (/breezemoon/{id}), PUT</li>
 * <li>Removes a breezemoon (/breezemoon/{id}), DELETE</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 2.8.0
 */
@Singleton
public class BreezemoonProcessor {

    /**
     * Breezemoon query service.
     */
    @Inject
    private BreezemoonQueryService breezemoonQueryService;

    /**
     * Breezemoon management service.
     */
    @Inject
    private BreezemoonMgmtService breezemoonMgmtService;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Optiona query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final PermissionMidware permissionMidware = beanManager.getReference(PermissionMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);
        final AnonymousViewCheckMidware anonymousViewCheckMidware = beanManager.getReference(AnonymousViewCheckMidware.class);

        final BreezemoonProcessor breezemoonProcessor = beanManager.getReference(BreezemoonProcessor.class);
        Dispatcher.get("/watch/breezemoons", breezemoonProcessor::showWatchBreezemoon, loginCheck::handle, csrfMidware::fill);
        Dispatcher.post("/breezemoon", breezemoonProcessor::addBreezemoon, loginCheck::handle, permissionMidware::check);
        Dispatcher.put("/breezemoon/{id}", breezemoonProcessor::updateBreezemoon, loginCheck::handle, csrfMidware::check, permissionMidware::check);
        Dispatcher.delete("/breezemoon/{id}", breezemoonProcessor::removeBreezemoon, loginCheck::handle, csrfMidware::check, permissionMidware::check);
        Dispatcher.get("/api/breezemoons", breezemoonProcessor::getBreezemoons);
    }

    /**
     * 清风明月API
     * @param context
     */
    public void getBreezemoons(final RequestContext context) {
        try {
            final int pageNum = Integer.parseInt(context.param("p"));
            final int pageSize = Integer.parseInt(context.param("size"));
            final int windowSize = 15;
            final JSONObject result = breezemoonQueryService.getBreezemoons("", "", pageNum, pageSize, windowSize);
            final List<JSONObject> bms = (List<JSONObject>) result.opt(Breezemoon.BREEZEMOONS);
            // 结果去敏
            for (int i = 0; i < bms.size(); i++) {
                bms.get(i).remove("breezemoonIP");
                bms.get(i).remove("breezemoonUA");
                bms.get(i).remove("breezemoonAuthorId");
                bms.get(i).remove("breezemoonStatus");
            }
            context.renderJSON(new JSONObject().put(Breezemoon.BREEZEMOONS, new JSONArray(bms))).renderCode(StatusCodes.SUCC);
        } catch (Exception e) {
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
        }
    }

    /**
     * Shows breezemoon page.
     *
     * @param context the specified context
     */
    public void showWatchBreezemoon(final RequestContext context) {
        final Request request = context.getRequest();

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "breezemoon.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final int pageNum = Paginator.getPage(request);
        int pageSize = Symphonys.ARTICLE_LIST_CNT;
        final JSONObject user = Sessions.getUser();
        String currentUserId = null;
        if (null != user) {
            pageSize = user.optInt(UserExt.USER_LIST_PAGE_SIZE);

            if (!UserExt.finishedGuide(user)) {
                context.sendRedirect(Latkes.getServePath() + "/guide");
                return;
            }

            currentUserId = user.optString(Keys.OBJECT_ID);
        }

        final int windowSize = Symphonys.ARTICLE_LIST_WIN_SIZE;
        final JSONObject result = breezemoonQueryService.getFollowingUserBreezemoons(currentUserId, pageNum, pageSize, windowSize);
        final List<JSONObject> bms = (List<JSONObject>) result.opt(Breezemoon.BREEZEMOONS);
        dataModel.put(Common.WATCHING_BREEZEMOONS, bms);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        dataModel.put(Common.SELECTED, Common.WATCH);
        dataModel.put(Common.CURRENT, StringUtils.substringAfter(context.requestURI(), "/watch"));
    }

    /**
     * Adds a breezemoon.
     * <p>
     * The request json object (breezemoon):
     * <pre>
     * {
     *   "breezemoonContent": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified context
     */
    final private static SimpleCurrentLimiter addBreezemoonLimiter = new SimpleCurrentLimiter(60 * 60, 5);
    public void addBreezemoon(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);

        final Request request = context.getRequest();
        final JSONObject requestJSONObject = context.requestJSON();
        if (isInvalid(context, requestJSONObject)) {
            return;
        }

        JSONObject user = Sessions.getUser();
        try {
            user = ApiProcessor.getUserByKey(requestJSONObject.optString("apiKey"));
        } catch (NullPointerException ignored) {
        }
        final String authorId = user.optString(Keys.OBJECT_ID);
        if (!addBreezemoonLimiter.access(authorId)) {
            context.renderJSON(StatusCodes.ERR).renderMsg("每小时只允许发送5个清风明月，稍安勿躁...");
            return;
        }
        final String userPhone = user.optString("userPhone");
        if (userPhone.isEmpty()) {
            context.renderJSON(StatusCodes.ERR).renderMsg("未绑定手机号码，无法使用此功能。请至设置-账户中绑定手机号码。");
            return;
        }

        final JSONObject breezemoon = new JSONObject();
        String breezemoonContent = requestJSONObject.optString(Breezemoon.BREEZEMOON_CONTENT);
        breezemoonContent = Jsoup.clean(breezemoonContent, Whitelist.none());
        breezemoonContent = StringUtils.trim(breezemoonContent);
        if (breezemoonContent.isEmpty()) {
            context.renderMsg("非法内容");
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
            return;
        }
        // 敏感词检测
        JSONObject censorResult = QiniuTextCensor.censor(breezemoonContent);
        if (censorResult.optString("do").equals("block")) {
            // 违规内容，不予显示
            String bannedWords = "内容" + QiniuTextCensor.showBannedWords(censorResult);
            ChatChannel.sendAdminMsg(user.optString(User.USER_NAME), "【AI审查】您由于上传违规清风明月，被处以 1 积分的处罚，请引以为戒。\n如误报请在此处回复我，审核后找回积分并获得补偿！");
            ChatRoomBot.abusePoint(user.optString(Keys.OBJECT_ID), 1, "[AI审查] [如有误报请联系管理员追回积分] 机器人罚单-上传违规内容（清风明月）");
            // 记录日志
            LogsService.censorLog(context, user.optString(Keys.OBJECT_ID), "用户：" + user.optString(User.USER_NAME) + " 违规上传清风明月：" + breezemoonContent + " 违规判定：" + censorResult);
            System.out.println("用户：" + user.optString(User.USER_NAME) + " 违规上传清风明月：" + breezemoonContent + " 违规判定：" + censorResult);
            context.renderMsg("您的内容存在违规内容，内容已被记录，管理员将会复审，请修改内容后重试。" + bannedWords);
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
            return;
        }

        breezemoon.put(Breezemoon.BREEZEMOON_CONTENT, breezemoonContent);
        breezemoon.put(Breezemoon.BREEZEMOON_AUTHOR_ID, authorId);
        final String ip = Requests.getRemoteAddr(request);
        breezemoon.put(Breezemoon.BREEZEMOON_IP, ip);
        final String ua = Headers.getHeader(request, Common.USER_AGENT, "");
        breezemoon.put(Breezemoon.BREEZEMOON_UA, ua);
        final JSONObject address = Geos.getAddressByGeoIP(ip);
        if (null != address) {
            breezemoon.put(Breezemoon.BREEZEMOON_CITY, address.optString(Common.CITY));
        }

        try {
            String oId = breezemoonMgmtService.addBreezemoon(breezemoon);
            List<JSONObject> list = new ArrayList<>();
            list.add(breezemoonQueryService.getBreezemoon(oId));
            breezemoonQueryService.organizeBreezemoons(authorId, list);
            JSONObject data = new JSONObject();
            data.put("breezemoonAuthorName", list.get(0).optString("breezemoonAuthorName"));
            data.put("breezemoonAuthorThumbnailURL48", list.get(0).optString("breezemoonAuthorThumbnailURL48"));
            data.put("breezemoonContent", list.get(0).optString("breezemoonContent"));
            data.put("oId", list.get(0).optString("oId"));

            if (UserExt.USER_XXX_STATUS_C_ENABLED == user.optInt(UserExt.USER_BREEZEMOON_STATUS)) {
                UserChannel.sendCmdToAll(new JSONObject().put("bz", data).put("command", "bz-update"));
            } else {
                context.renderMsg("您的清风明月发表成功，但由于您的隐私设置为不公开，将不会显示在公开列表。请至设置-隐私中，打开“公开清风明月列表”选项。");
                context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
                return;
            }

            context.renderData(data);
            context.renderJSONValue(Keys.CODE, StatusCodes.SUCC);
        } catch (final Exception e) {
            context.renderMsg(e.getMessage());
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
        }
    }

    /**
     * Updates a breezemoon.
     * <p>
     * The request json object (breezemoon):
     * <pre>
     * {
     *   "breezemoonContent": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified context
     */
    public void updateBreezemoon(final RequestContext context) {
        final String id = context.pathVar("id");
        context.renderJSON(StatusCodes.ERR);
        final Request request = context.getRequest();
        final JSONObject requestJSONObject = context.requestJSON();
        if (isInvalid(context, requestJSONObject)) {
            return;
        }

        try {
            final JSONObject old = breezemoonQueryService.getBreezemoon(id);
            if (null == old) {
                throw new ServiceException(langPropsService.get("queryFailedLabel"));
            }

            final JSONObject user = Sessions.getUser();
            if (!old.optString(Breezemoon.BREEZEMOON_AUTHOR_ID).equals(user.optString(Keys.OBJECT_ID))) {
                throw new ServiceException(langPropsService.get("sc403Label"));
            }

            final JSONObject breezemoon = new JSONObject();
            breezemoon.put(Keys.OBJECT_ID, id);
            final String breezemoonContent = requestJSONObject.optString(Breezemoon.BREEZEMOON_CONTENT);
            final String authorId = user.optString(Keys.OBJECT_ID);
            if (!addBreezemoonLimiter.access(authorId)) {
                context.renderJSON(StatusCodes.ERR).renderMsg("操作过于频繁，请稍候重试。");
                return;
            }
            // 敏感词检测
            JSONObject censorResult = QiniuTextCensor.censor(breezemoonContent);
            if (censorResult.optString("do").equals("block")) {
                // 违规内容，不予显示
                String bannedWords = "内容" + QiniuTextCensor.showBannedWords(censorResult);
                ChatChannel.sendAdminMsg(user.optString(User.USER_NAME), "【AI审查】您由于上传违规清风明月，被处以 1 积分的处罚，请引以为戒。\n如误报请在此处回复我，审核后找回积分并获得补偿！");
                ChatRoomBot.abusePoint(user.optString(Keys.OBJECT_ID), 1, "[AI审查] [如有误报请联系管理员追回积分] 机器人罚单-上传违规内容（清风明月）");
                // 记录日志
                LogsService.censorLog(context, user.optString(Keys.OBJECT_ID), "用户：" + user.optString(User.USER_NAME) + " 违规上传清风明月：" + breezemoonContent + " 违规判定：" + censorResult);
                System.out.println("用户：" + user.optString(User.USER_NAME) + " 违规上传清风明月：" + breezemoonContent + " 违规判定：" + censorResult);
                context.renderMsg("您的内容存在违规内容，内容已被记录，管理员将会复审，请修改内容后重试。" + bannedWords);
                context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
                return;
            }
            breezemoon.put(Breezemoon.BREEZEMOON_CONTENT, breezemoonContent);
            final String ip = Requests.getRemoteAddr(request);
            breezemoon.put(Breezemoon.BREEZEMOON_IP, ip);
            final String ua = Headers.getHeader(request, Common.USER_AGENT, "");
            breezemoon.put(Breezemoon.BREEZEMOON_UA, ua);

            breezemoonMgmtService.updateBreezemoon(breezemoon);

            context.renderJSONValue(Keys.CODE, StatusCodes.SUCC);
        } catch (final Exception e) {
            context.renderMsg(e.getMessage());
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
        }
    }

    /**
     * Removes a breezemoon.
     *
     * @param context the specified context
     */
    public void removeBreezemoon(final RequestContext context) {
        final String id = context.pathVar("id");
        context.renderJSON(StatusCodes.ERR);

        try {
            final JSONObject breezemoon = breezemoonQueryService.getBreezemoon(id);
            if (null == breezemoon) {
                throw new ServiceException(langPropsService.get("queryFailedLabel"));
            }

            final JSONObject user = Sessions.getUser();
            if (!breezemoon.optString(Breezemoon.BREEZEMOON_AUTHOR_ID).equals(user.optString(Keys.OBJECT_ID))) {
                throw new ServiceException(langPropsService.get("sc403Label"));
            }

            breezemoonMgmtService.removeBreezemoon(id);

            context.renderJSONValue(Keys.CODE, StatusCodes.SUCC);
        } catch (final Exception e) {
            context.renderMsg(e.getMessage());
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
        }
    }

    private boolean isInvalid(final RequestContext context, final JSONObject requestJSONObject) {
        requestJSONObject.put(Breezemoon.BREEZEMOON_CONTENT, ReservedWords.processReservedWord(requestJSONObject.optString(Breezemoon.BREEZEMOON_CONTENT)));
        String breezemoonContent = requestJSONObject.optString(Breezemoon.BREEZEMOON_CONTENT);
        breezemoonContent = StringUtils.trim(breezemoonContent);
        final long length = StringUtils.length(breezemoonContent);
        if (1 > length || 512 < length) {
            context.renderMsg(langPropsService.get("breezemoonLengthLabel"));
            context.renderJSONValue(Keys.CODE, StatusCodes.ERR);
            return true;
        }

        requestJSONObject.put(Breezemoon.BREEZEMOON_CONTENT, breezemoonContent);
        return false;
    }
}
