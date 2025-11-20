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
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Liveness;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.processor.middleware.validate.Activity1A0001CollectValidationMidware;
import org.b3log.symphony.processor.middleware.validate.Activity1A0001ValidationMidware;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

import java.util.*;

/**
 * Activity processor.
 * <ul>
 * <li>Shows activities (/activities), GET</li>
 * <li>Daily checkin (/activity/daily-checkin), GET</li>
 * <li>Shows 1A0001 (/activity/1A0001), GET</li>
 * <li>Bets 1A0001 (/activity/1A0001/bet), POST</li>
 * <li>Collects 1A0001 (/activity/1A0001/collect), POST</li>
 * <li>Shows character (/activity/character), GET</li>
 * <li>Submit character (/activity/character/submit), POST</li>
 * <li>Shows eating snake (/activity/eating-snake), GET</li>
 * <li>Starts eating snake (/activity/eating-snake/start), POST</li>
 * <li>Collects eating snake(/activity/eating-snake/collect), POST</li>
 * <li>Shows gobang (/activity/gobang), GET</li>
 * <li>Starts gobang (/activity/gobang/start), POST</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://ld246.com/member/ZephyrJung">Zephyr</a>
 * @version 2.0.0.1, Jun 20, 2020
 * @since 1.3.0
 */
@Singleton
public class ActivityProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(ActivityProcessor.class);

    /**
     * Activity management service.
     */
    @Inject
    private ActivityMgmtService activityMgmtService;

    /**
     * Activity query service.
     */
    @Inject
    private ActivityQueryService activityQueryService;

    /**
     * Character query service.
     */
    @Inject
    private CharacterQueryService characterQueryService;

    /**
     * Pointtransfer query service.
     */
    @Inject
    private PointtransferQueryService pointtransferQueryService;

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
     * Pointtransfer management service.
     */
    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Record if eating snake game is started.
     */
    final static HashMap<String, Long> EATING_SNAKE_STARTED = new HashMap<>();

    /**
     * Record play times in eating snake game.
     */
    final static SimpleCurrentLimiter EATING_SNAKE_CURRENT_LIMITER = new SimpleCurrentLimiter(12 * 60 * 60, 5);

    /**
     * Record ADR submit times 48hrs/req.
     */
    public static SimpleCurrentLimiter ADRLimiter = new SimpleCurrentLimiter((48 * 60 * 60), 1);

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);
        final Activity1A0001ValidationMidware activity1A0001ValidationMidware = beanManager.getReference(Activity1A0001ValidationMidware.class);
        final Activity1A0001CollectValidationMidware activity1A0001CollectValidationMidware = beanManager.getReference(Activity1A0001CollectValidationMidware.class);

        final ActivityProcessor activityProcessor = beanManager.getReference(ActivityProcessor.class);
        Dispatcher.get("/activity/character", activityProcessor::showCharacter, loginCheck::handle, csrfMidware::fill);
        Dispatcher.post("/activity/character/submit", activityProcessor::submitCharacter, loginCheck::handle);
        Dispatcher.get("/activities", activityProcessor::showActivities, loginCheck::handle);
        // Dispatcher.get("/activity/daily-checkin-api", activityProcessor::dailyCheckinApi, loginCheck::handle);
        Dispatcher.get("/activity/yesterday-liveness-reward-api", activityProcessor::yesterdayLivenessRewardApi, loginCheck::handle);
        Dispatcher.post("/activity/yesterday-liveness-reward-api", activityProcessor::yesterdayLivenessRewardApiGold);
        // Dispatcher.get("/activity/1A0001", activityProcessor::show1A0001, csrfMidware::fill);
        // Dispatcher.post("/activity/1A0001/bet", activityProcessor::bet1A0001, loginCheck::handle, csrfMidware::check, activity1A0001ValidationMidware::handle);
        // Dispatcher.post("/activity/1A0001/collect", activityProcessor::collect1A0001, loginCheck::handle, activity1A0001CollectValidationMidware::handle);
        Dispatcher.get("/activity/eating-snake", activityProcessor::showEatingSnake, loginCheck::handle, csrfMidware::fill);
        Dispatcher.post("/activity/eating-snake/start", activityProcessor::startEatingSnake, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/activity/eating-snake/collect", activityProcessor::collectEatingSnake, loginCheck::handle, csrfMidware::fill);
        Dispatcher.get("/activity/gobang", activityProcessor::showGobang, loginCheck::handle, csrfMidware::fill);
        Dispatcher.post("/activity/gobang/start", activityProcessor::startGobang, loginCheck::handle);
        Dispatcher.post("/api/games/adarkroom/share", activityProcessor::shareADarkRoomScore, loginCheck::handle, csrfMidware::check);
        Dispatcher.post("/api/games/mofish/score", activityProcessor::shareMofishScore);
        Dispatcher.post("/api/games/smallmofish/score", activityProcessor::shareSmallMofishScore);
        Dispatcher.post("/api/games/emojiPair/score", activityProcessor::shareEmojiPairScore, loginCheck::handle, csrfMidware::check);
        Dispatcher.get("/activity/catch-the-cat", activityProcessor::showCatchTheCat, loginCheck::handle, csrfMidware::fill);
        Dispatcher.get("/activity/daxigua", activityProcessor::showDaxigua, loginCheck::handle, csrfMidware::fill);
        Dispatcher.get("/activity/2048", activityProcessor::show2048, loginCheck::handle, csrfMidware::fill);
        Dispatcher.get("/api/activity/is-collected-liveness", activityProcessor::isCollectedYesterdayLivenessRewardApi, loginCheck::handle);
    }

    /**
     * 查询用户是否已经领取昨日奖励
     * @param context
     */
    public void isCollectedYesterdayLivenessRewardApi(final RequestContext context) {
        JSONObject currentUser = Sessions.getUser();
        try {
            currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        boolean result = activityQueryService.isCollectedYesterdayLivenessReward(userId);
        context.renderJSON(StatusCodes.SUCC).renderJSON(new JSONObject().put("isCollectedYesterdayLivenessReward", result));
    }

    /**
     * Catch the cat game.
     *
     * @param context
     */
    public void showCatchTheCat(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "activity/catch-the-cat.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        final JSONObject user = Sessions.getUser();
        final String userId = user.optString(Keys.OBJECT_ID);
    }


    /**
     * 合成大西瓜 game.
     *
     * @param context
     */
    public void showDaxigua(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "activity/daxigua.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        final JSONObject user = Sessions.getUser();
        final String userId = user.optString(Keys.OBJECT_ID);
    }


    /**
     * 2048 game.
     *
     * @param context
     */
    public void show2048(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "activity/2048.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        final JSONObject user = Sessions.getUser();
        final String userId = user.optString(Keys.OBJECT_ID);
    }



    /**
     * 上传摸鱼大闯关游戏成绩
     *
     * @param context
     */
    public void shareMofishScore(final RequestContext context) {
        try {
            JSONObject requestJSONObject = context.requestJSON();
            final String goldFingerKey = requestJSONObject.optString("goldFingerKey");
            final String gameKey = Symphonys.get("gold.finger.game");
            if (goldFingerKey.equals(gameKey)) {
                final String userName = requestJSONObject.optString("userName");
                final int stage = requestJSONObject.optInt("stage");
                final long time = requestJSONObject.optLong("time");
                final JSONObject user = userQueryService.getUserByName(userName);
                final boolean succ = null != pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, user.optString(Keys.OBJECT_ID),
                        Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_MOFISH, stage,
                        time + "", System.currentTimeMillis(), "");
                if (!succ) {
                    throw new ServiceException(langPropsService.get("transferFailLabel"));
                }
                // === 记录日志 ===
                LogsService.simpleLog(context, "上传摸鱼大闯关数据", "用户: " + userName + ", 通过关卡: " + stage + ", 通关时间: " + time);
                // === 记录日志 ===
                context.renderJSON(StatusCodes.SUCC);
                context.renderMsg("数据上传成功！");
            } else {
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("金手指(game类型)不正确。");
            }
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("存储数据失败！原因：未知原因");
        }
    }

    /**
     * 上传摸鱼小闯关游戏成绩
     *
     * @param context
     */
    public void shareSmallMofishScore(final RequestContext context) {
      try {
          JSONObject requestJSONObject = context.requestJSON();
          final String goldFingerKey = requestJSONObject.optString("goldFingerKey");
          final String gameKey = Symphonys.get("gold.finger.game");
          if (goldFingerKey.equals(gameKey)) {
              final String userName = requestJSONObject.optString("userName");
              final int stage = requestJSONObject.optInt("stage");
              final long time = requestJSONObject.optLong("time");
              final JSONObject user = userQueryService.getUserByName(userName);
              final boolean succ = null != pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, user.optString(Keys.OBJECT_ID),
                      Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_SMALLMOFISH, stage,
                      time + "", System.currentTimeMillis(), "");
              if (!succ) {
                  throw new ServiceException(langPropsService.get("transferFailLabel"));
              }
              // === 记录日志 ===
              LogsService.simpleLog(context, "上传摸鱼小闯关数据", "用户: " + userName + ", 通过关卡: " + stage + ", 通关时间: " + time);
              // === 记录日志 ===
              context.renderJSON(StatusCodes.SUCC);
              context.renderMsg("数据上传成功！");
          } else {
              context.renderJSON(StatusCodes.ERR);
              context.renderMsg("金手指(game类型)不正确。");
          }
      } catch (Exception e) {
          context.renderJSON(StatusCodes.ERR);
          context.renderMsg("存储数据失败！原因：未知原因");
      }
  }


    /**
     * 上传emoji小黄脸游戏成绩
     *
     * @param context
     */
    public void shareEmojiPairScore(final RequestContext context) {
        try {
            final int founds = context.requestJSON().optInt("founds");
            final JSONObject user = Sessions.getUser();
            if (user == null) {
                context.renderJSON(StatusCodes.ERR);
                return;
            }
            int gives = founds;
            if (gives > 30) {
                gives = 30;
            }
            List<JSONObject> list = pointtransferQueryService.getLatestPointtransfers(user.optString(Keys.OBJECT_ID), Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_PLAY_EMOJI_PAIR, 1);
            if (list.isEmpty() || !DateUtils.isSameDay(new Date(), new Date(list.get(0).optLong("time")))) {
                pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, user.optString(Keys.OBJECT_ID),
                        Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_PLAY_EMOJI_PAIR, gives,
                        "" + founds, System.currentTimeMillis(), "");
                context.renderJSON(StatusCodes.SUCC);
                context.renderMsg("数据上传成功！");
            } else {
                pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, user.optString(Keys.OBJECT_ID),
                        Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_PLAY_EMOJI_PAIR, 0,
                        "" + founds, System.currentTimeMillis(), "");
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("存储数据失败！原因：本日已达奖励上限");
            }
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("存储数据失败！原因：未知原因");
        }
    }

    /**
     * 上传 ADarkRoom 游戏成绩
     *
     * @param context
     */
    public void shareADarkRoomScore(final RequestContext context) {
        try {
            JSONObject requestJSONObject = context.requestJSON();
            final int score = requestJSONObject.optInt("score");
            try {
                JSONObject currentUser = Sessions.getUser();
                if (ADRLimiter.access(currentUser.optString(User.USER_NAME))) {
                    int amout = 10;
                    if (score > 1000) {
                        amout = score / 1000;
                    }
                    if (amout > 200) {
                        amout = 200;
                    }
                    final boolean succ = null != pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, currentUser.optString(Keys.OBJECT_ID),
                            Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_ADR, amout,
                            score + "", System.currentTimeMillis(), "");
                    if (!succ) {
                        throw new ServiceException(langPropsService.get("transferFailLabel"));
                    }
                    context.renderJSON(StatusCodes.SUCC);
                    context.renderMsg("数据上传成功，恭喜你通关了！你获得了奖励 " + amout + " 积分！你可以在摸鱼派-总榜-ADarkRoom总分榜单中查看你的成绩！");
                } else {
                    context.renderJSON(StatusCodes.ERR);
                    context.renderMsg("存储数据失败！原因：每 48 小时只允许提交一次成绩！");
                }
            } catch (NullPointerException e) {
                context.renderJSON(StatusCodes.ERR);
                context.renderMsg("存储数据失败！原因：你还没有登录摸鱼派，请前往摸鱼派 https://fishpi.cn 登录账号后重试。");
            }
        } catch (Exception e) {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("存储数据失败！原因：请检查自己是否存在作弊行为！");
        }
    }

    /**
     * Shows 1A0001.
     *
     * @param context the specified context
     */
    public void showCharacter(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "activity/character.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        final JSONObject user = Sessions.getUser();
        final String userId = user.optString(Keys.OBJECT_ID);

        String activityCharacterGuideLabel = langPropsService.get("activityCharacterGuideLabel");

        final String character = characterQueryService.getUnwrittenCharacter(userId);
        if (StringUtils.isBlank(character)) {
            dataModel.put("noCharacter", true);
            return;
        }

        final int totalCharacterCount = characterQueryService.getTotalCharacterCount();
        final int writtenCharacterCount = characterQueryService.getWrittenCharacterCount();
        final String totalProgress = String.format("%.2f", (double) writtenCharacterCount / (double) totalCharacterCount * 100);
        dataModel.put("totalProgress", totalProgress);

        final int userWrittenCharacterCount = characterQueryService.getWrittenCharacterCount(userId);
        final String userProgress = String.format("%.2f", (double) userWrittenCharacterCount / (double) totalCharacterCount * 100);
        dataModel.put("userProgress", userProgress);

        activityCharacterGuideLabel = activityCharacterGuideLabel.replace("{character}", character);
        dataModel.put("activityCharacterGuideLabel", activityCharacterGuideLabel);
    }

    /**
     * Submits character.
     *
     * @param context the specified context
     */
    public void submitCharacter(final RequestContext context) {
        final Request request = context.getRequest();
        context.renderJSON(StatusCodes.ERR);

        JSONObject requestJSONObject;
        try {
            requestJSONObject = context.requestJSON();
            request.setAttribute(Keys.REQUEST, requestJSONObject);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Submits character failed", e);
            context.renderMsg(langPropsService.get("activityCharacterRecognizeFailedLabel"));
            return;
        }

        final JSONObject currentUser = Sessions.getUser();
        final String userId = currentUser.optString(Keys.OBJECT_ID);
        final String dataURL = requestJSONObject.optString("dataURL");
        final String dataPart = StringUtils.substringAfter(dataURL, ",");
        final String character = requestJSONObject.optString("character");

        final JSONObject result = activityMgmtService.submitCharacter(userId, dataPart, character);
        context.renderJSON(result);
    }

    /**
     * Shows activity page.
     *
     * @param context the specified context
     */
    public void showActivities(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "home/activities.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        dataModel.put("pointActivityCheckinMin", Pointtransfer.TRANSFER_SUM_C_ACTIVITY_CHECKIN_MIN);
        dataModel.put("pointActivityCheckinMax", Pointtransfer.TRANSFER_SUM_C_ACTIVITY_CHECKIN_MAX);
        dataModel.put("pointActivityCheckinStreak", Pointtransfer.TRANSFER_SUM_C_ACTIVITY_CHECKINT_STREAK);
        dataModel.put("activitYesterdayLivenessRewardMaxPoint", Symphonys.ACTIVITY_YESTERDAY_REWARD_MAX);
    }

    /**
     * Daily checkin.
     *
     * @param context the specified context
     */
    public void dailyCheckinApi(final RequestContext context) {
        final JSONObject user = Sessions.getUser();
        final String userId = user.optString(Keys.OBJECT_ID);
        context.renderJSON(new JSONObject().put("sum", activityMgmtService.dailyCheckin(userId)));
    }

    /**
     * Yesterday liveness reward.
     *
     * @param context the specified context
     */
    @Deprecated
    public void yesterdayLivenessReward(final RequestContext context) {
        final Request request = context.getRequest();
        final JSONObject user = Sessions.getUser();
        final String userId = user.optString(Keys.OBJECT_ID);

        activityMgmtService.yesterdayLivenessReward(userId);

        context.sendRedirect(Latkes.getServePath() + "/member/" + user.optString(User.USER_NAME) + "/points");
    }

    public void yesterdayLivenessRewardApiGold(final RequestContext context) {
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
            int sum = activityMgmtService.yesterdayLivenessRewardApi(userId);

            context.renderJSON(new JSONObject().put("sum", sum));
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("金手指(liveness类型)不正确。");
        }
    }

    /**
     * Yesterday liveness reward.
     *
     * @param context the specified context
     */
    public void yesterdayLivenessRewardApi(final RequestContext context) {
        JSONObject user = Sessions.getUser();
        try {
            user = ApiProcessor.getUserByKey(context.param("apiKey"));
        } catch (NullPointerException ignored) {
        }
        final String userId = user.optString(Keys.OBJECT_ID);

        int sum = activityMgmtService.yesterdayLivenessRewardApi(userId);

        context.renderJSON(new JSONObject().put("sum", sum));
    }

    /**
     * Shows 1A0001.
     *
     * @param context the specified context
     */
    public void show1A0001(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "activity/1A0001.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        final JSONObject currentUser = Sessions.getUser();
        final String userId = currentUser.optString(Keys.OBJECT_ID);

        final boolean closed = Symphonys.ACTIVITY_1A0001_CLOSED;
        dataModel.put(Common.CLOSED, closed);

        final Calendar calendar = Calendar.getInstance();
        final int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        final boolean closed1A0001 = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
        dataModel.put(Common.CLOSED_1A0001, closed1A0001);

        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final int minute = calendar.get(Calendar.MINUTE);
        final boolean end = hour > 14 || (hour == 14 && minute > 55);
        dataModel.put(Common.END, end);

        final boolean collected = activityQueryService.isCollected1A0001Today(userId);
        dataModel.put(Common.COLLECTED, collected);

        final boolean participated = activityQueryService.is1A0001Today(userId);
        dataModel.put(Common.PARTICIPATED, participated);

        while (true) {
            if (closed) {
                dataModel.put(Keys.MSG, langPropsService.get("activityClosedLabel"));
                break;
            }

            if (closed1A0001) {
                dataModel.put(Keys.MSG, langPropsService.get("activity1A0001CloseLabel"));
                break;
            }

            if (collected) {
                dataModel.put(Keys.MSG, langPropsService.get("activityParticipatedLabel"));
                break;
            }

            if (participated) {
                dataModel.put(Common.HOUR, hour);

                final List<JSONObject> records = pointtransferQueryService.getLatestPointtransfers(userId,
                        Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_1A0001, 1);
                final JSONObject pointtransfer = records.get(0);
                final String data = pointtransfer.optString(Pointtransfer.DATA_ID);
                final String smallOrLarge = data.split("-")[1];
                final int sum = pointtransfer.optInt(Pointtransfer.SUM);
                String msg = langPropsService.get("activity1A0001BetedLabel");
                final String small = langPropsService.get("activity1A0001BetSmallLabel");
                final String large = langPropsService.get("activity1A0001BetLargeLabel");
                msg = msg.replace("{smallOrLarge}", StringUtils.equals(smallOrLarge, "0") ? small : large);
                msg = msg.replace("{point}", String.valueOf(sum));

                dataModel.put(Keys.MSG, msg);
                break;
            }

            if (end) {
                dataModel.put(Keys.MSG, langPropsService.get("activityEndLabel"));
                break;
            }
            break;
        }

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Bets 1A0001.
     *
     * @param context the specified context
     */
    public void bet1A0001(final RequestContext context) {
        context.renderJSON(StatusCodes.ERR);
        final JSONObject requestJSONObject = (JSONObject) context.attr(Keys.REQUEST);
        final int amount = requestJSONObject.optInt(Common.AMOUNT);
        final int smallOrLarge = requestJSONObject.optInt(Common.SMALL_OR_LARGE);
        final JSONObject currentUser = Sessions.getUser();
        final String fromId = currentUser.optString(Keys.OBJECT_ID);
        final JSONObject ret = activityMgmtService.bet1A0001(fromId, amount, smallOrLarge);
        if (StatusCodes.SUCC == ret.optInt(Keys.CODE)) {
            String msg = langPropsService.get("activity1A0001BetedLabel");
            final String small = langPropsService.get("activity1A0001BetSmallLabel");
            final String large = langPropsService.get("activity1A0001BetLargeLabel");
            msg = msg.replace("{smallOrLarge}", smallOrLarge == 0 ? small : large);
            msg = msg.replace("{point}", String.valueOf(amount));
            context.renderCodeMsg(StatusCodes.SUCC, msg);
        }
    }

    /**
     * Collects 1A0001.
     *
     * @param context the specified context
     */
    public void collect1A0001(final RequestContext context) {
        final JSONObject currentUser = Sessions.getUser();
        final String userId = currentUser.optString(Keys.OBJECT_ID);

        final JSONObject ret = activityMgmtService.collect1A0001(userId);

        context.renderJSON(ret);
    }

    /**
     * Shows eating snake.
     *
     * @param context the specified context
     */
    public void showEatingSnake(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "activity/eating-snake.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);

        final List<JSONObject> maxUsers = activityQueryService.getTopEatingSnakeUsersMax(10);
        dataModel.put("maxUsers", maxUsers);

        final List<JSONObject> sumUsers = activityQueryService.getTopEatingSnakeUsersSum(10);
        dataModel.put("sumUsers", sumUsers);

        final JSONObject user = Sessions.getUser();
        final String userId = user.optString(Keys.OBJECT_ID);
        final int startPoint = activityQueryService.getEatingSnakeAvgPoint(userId);

        String pointActivityEatingSnake = langPropsService.get("activityStartEatingSnakeTipLabel");
        pointActivityEatingSnake = pointActivityEatingSnake.replace("{point}", String.valueOf(startPoint));
        dataModel.put("activityStartEatingSnakeTipLabel", pointActivityEatingSnake);
    }

    /**
     * Starts eating snake.
     *
     * @param context the specified context
     */
    public void startEatingSnake(final RequestContext context) {
        final Request request = context.getRequest();
        final JSONObject currentUser = Sessions.getUser();
        final String fromId = currentUser.optString(Keys.OBJECT_ID);
        if (EATING_SNAKE_CURRENT_LIMITER.access(fromId)) {
            EATING_SNAKE_STARTED.put(fromId, System.currentTimeMillis());
            final JSONObject ret = activityMgmtService.startEatingSnake(fromId);
            context.renderJSON(ret);
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("每12个小时只能玩5次哦！休息一下吧。");
        }
    }

    /**
     * Collects eating snake.
     *
     * @param context the specified context
     */
    public void collectEatingSnake(final RequestContext context) {
        JSONObject requestJSONObject;
        try {
            requestJSONObject = context.requestJSON();
            final int score = requestJSONObject.optInt("score");
            final JSONObject user = Sessions.getUser();
            final String userId = user.optString(Keys.OBJECT_ID);

            if (EATING_SNAKE_STARTED.containsKey(userId)) {
                long startedTime = EATING_SNAKE_STARTED.get(userId);
                EATING_SNAKE_STARTED.remove(userId);
                // 用户有点击游戏开始的记录，反作弊第一步通过
                if (score > 4) {
                    if (((System.currentTimeMillis() - startedTime) / 1000) > 10) {
                        // 分数大于4时，游戏时间超过十秒，反作弊第二步通过
                        final JSONObject ret = activityMgmtService.collectEatingSnake(userId, score);
                        context.renderJSON(ret);
                        return;
                    }
                } else {
                    // 分数小于4时，反作弊第二步直接通过
                    final JSONObject ret = activityMgmtService.collectEatingSnake(userId, score);
                    context.renderJSON(ret);
                    return;
                }
            }

            LOGGER.log(Level.ERROR, "User " + user.optString(User.USER_NAME) + " haven't started the snake game but uploaded a score...");
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("您的刷分行为已被记录！如这是您的第一次行为，我们仅对您作为警告，请诚实游戏哦 :)");
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Collects eating snake game failed", e);
            context.renderCodeMsg(StatusCodes.ERR, "err....");
        }
    }

    /**
     * Shows gobang.
     *
     * @param context the specified context
     */
    public void showGobang(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "activity/gobang.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);

        String pointActivityGobang = langPropsService.get("activityStartGobangTipLabel");
        pointActivityGobang = pointActivityGobang.replace("{point}", String.valueOf(Pointtransfer.TRANSFER_SUM_C_ACTIVITY_GOBANG_START));
        dataModel.put("activityStartGobangTipLabel", pointActivityGobang);
    }

    /**
     * Starts gobang.
     *
     * @param context the specified context
     */
    public void startGobang(final RequestContext context) {
        final JSONObject ret = new JSONObject().put(Keys.CODE, StatusCodes.SUCC);
        final JSONObject currentUser = Sessions.getUser();
        final boolean succ = currentUser.optInt(UserExt.USER_POINT) - Pointtransfer.TRANSFER_SUM_C_ACTIVITY_GOBANG_START >= 0;
        if (succ) {
            ret.put(Keys.CODE, StatusCodes.SUCC);
        }
        final String msg = succ ? "started" : langPropsService.get("activityStartGobangFailLabel");
        ret.put(Keys.MSG, msg);
        context.renderJSON(ret);
    }
}
