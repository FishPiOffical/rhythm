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

import jodd.http.HttpException;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;

import org.assertj.core.util.Lists;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.PointtransferRepository;
import org.b3log.symphony.repository.SponsorRepository;
import org.b3log.symphony.service.*;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.b3log.symphony.util.Vocation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Top ranking list processor.
 * <ul>
 * <li>Shows top (/top), GET</li>
 * <li>Top balance ranking list (/top/balance), GET</li>
 * <li>Top consumption ranking list (/top/consumption), GET</li>
 * <li>Top checkin ranking list (/top/checkin), GET</li>
 * <li>Top link ranking list (/top/link), GET</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.0.0.0, Feb 11, 2020
 * @since 1.3.0
 */
@Singleton
public class TopProcessor {

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Pointtransfer query service.
     */
    @Inject
    private PointtransferQueryService pointtransferQueryService;

    /**
     * Activity query service.
     */
    @Inject
    private ActivityQueryService activityQueryService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Link query service.
     */
    @Inject
    private LinkQueryService linkQueryService;

    /**
     * Point transfer repository.
     */
    @Inject
    private PointtransferRepository pointtransferRepository;

    /**
     * Sponsor repository.
     */
    @Inject
    private SponsorRepository sponsorRepository;

    /**
     * Sponsor repository.
     */
    @Inject
    private ArticleRepository articleRepository;

    @Inject
    private AvatarQueryService avatarQueryService;

    @Inject
    private ProfessionRankingQueryService professionRankingQueryService;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final AnonymousViewCheckMidware anonymousViewCheckMidware = beanManager.getReference(AnonymousViewCheckMidware.class);
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);

        final TopProcessor topProcessor = beanManager.getReference(TopProcessor.class);
        Dispatcher.get("/api/top/any", topProcessor::getAnyTop, loginCheck::handle);
        Dispatcher.get("/top", topProcessor::showTop, loginCheck::handle);
        Dispatcher.get("/top/profession", topProcessor::showProfession, loginCheck::handle);
//        Dispatcher.get("/top/link", topProcessor::showLink, anonymousViewCheckMidware::handle);
//        Dispatcher.get("/api/top/link", topProcessor::getLink, anonymousViewCheckMidware::handle);
        Dispatcher.get("/top/balance", topProcessor::showBalance, loginCheck::handle);
        Dispatcher.get("/api/top/balance", topProcessor::getBalance, loginCheck::handle);
        Dispatcher.get("/top/consumption", topProcessor::showConsumption, loginCheck::handle);
        Dispatcher.get("/api/top/consumption", topProcessor::getConsumption, loginCheck::handle);
        Dispatcher.get("/top/checkin", topProcessor::showCheckin, loginCheck::handle);
        Dispatcher.get("/api/top/checkin", topProcessor::getCheckin, loginCheck::handle);
        Dispatcher.get("/top/online", topProcessor::showOnline, loginCheck::handle);
        Dispatcher.get("/api/top/online", topProcessor::getOnline, loginCheck::handle);
        Dispatcher.get("/top/adr", topProcessor::showADR, loginCheck::handle);
        Dispatcher.get("/api/top/adr", topProcessor::getADR, loginCheck::handle);
        Dispatcher.get("/top/mofish", topProcessor::showMofish, loginCheck::handle);
        Dispatcher.get("/api/top/mofish", topProcessor::getMofish, loginCheck::handle);
        Dispatcher.get("/top/smallmofish", topProcessor::showSmallMofish, loginCheck::handle);
        Dispatcher.get("/api/top/smallmofish", topProcessor::getSmallMofish, loginCheck::handle);
        Dispatcher.get("/top/lifeRestart", topProcessor::showLifeRestart, loginCheck::handle);
        Dispatcher.get("/api/top/lifeRestart", topProcessor::getLifeRestart, loginCheck::handle);
        Dispatcher.get("/top/evolve", topProcessor::showEvolve, loginCheck::handle);
        Dispatcher.get("/api/top/evolve", topProcessor::getEvolve, loginCheck::handle);
        Dispatcher.get("/top/emoji", topProcessor::showEmoji, loginCheck::handle);
        Dispatcher.get("/api/top/emoji", topProcessor::getEmoji, loginCheck::handle);
        Dispatcher.get("/top/xiaoice", topProcessor::showXiaoice, loginCheck::handle);
        Dispatcher.get("/api/top/xiaoice", topProcessor::getXiaoice, loginCheck::handle);
        Dispatcher.get("/top/invite", topProcessor::showInvite, loginCheck::handle);
        Dispatcher.get("/api/top/invite", topProcessor::getInvite, loginCheck::handle);
        Dispatcher.get("/top/donate", topProcessor::showDonate, loginCheck::handle);
        Dispatcher.get("/api/top/donate", topProcessor::getDonate, loginCheck::handle);
        Dispatcher.get("/top/perfect", topProcessor::showPerfect, loginCheck::handle);
        Dispatcher.get("/api/top/perfect", topProcessor::getPerfect, loginCheck::handle);
    }

    /**
     * Gets any top list.
     *
     * @param context the specified context
     */
    public void getAnyTop(final RequestContext context) {
      final JSONObject dataModel = new JSONObject();
      List<JSONObject> rankList = new ArrayList<>();
      final String types = context.param("types").toString();
      String[] typeList = types.split(",");//以逗号分割
        for (String type : typeList) {
            if(type.contains("|")){
              String[] subTypeList = type.split("\\|");
              rankList.add(getTopByType(context,subTypeList[0],subTypeList[1]));
            }
            else{
              rankList.add(getTopByType(context,type,null));
            }
        }
        dataModel.put("data", rankList);
        context.renderJSON(dataModel).renderCode(StatusCodes.SUCC);
    }

    /**
     * remove private datas.
     *
     * @param result rank's list data
     */
    private void removePrivateDatas(List<JSONObject> result) {
        for (int i = 0; i < result.size(); i++) {
            removePrivateData(result.get(i));
        }
    }

    /**
     * remove private data.
     *
     * @param result rank's data
     */
    private void removePrivateData(JSONObject result){
        result.remove("userPassword");
        result.remove("userLatestLoginIP");
        result.remove("userPhone");
        result.remove("userQQ");
        result.remove("userCity");
        result.remove("userCountry");
        result.remove("userEmail");
        result.remove("secret2fa");
    }

    /**
     * package rank list data.
     *
     * @param result rank's data
     * @param type rank's type
     */
    private JSONObject packageListData(List<JSONObject> result,final String type){
      JSONObject ret = new JSONObject();
      removePrivateDatas(result);
      ret.put("type",type);
      ret.put("data", result);
      return ret;
    }

    /**
     * package rank list data, remove usless value.
     *
     * @param context the specified context
     * @param type the rank's type //进化榜还是小冰榜
     * @param type the rank's subType //进化榜的成就榜还是知识榜
     */
    private JSONObject getTopByType(final RequestContext context,final String type,final String subType){
      JSONObject ret;
      List<JSONObject> result;
      switch (type){
        case Common.TOP_LINKS:
          result = linkQueryService.getTopLink(Symphonys.TOP_CNT);
          ret = packageListData(result,type);
          break;
        case Common.TOP_BALANCE_USERS:
          result = pointtransferQueryService.getTopBalanceUsers(Symphonys.TOP_CNT);
          ret = packageListData(result,type);
          break;
        case Common.TOP_CONSUMPTION_USERS:
          result = pointtransferQueryService.getTopConsumptionUsers(Symphonys.TOP_CNT);
          ret = packageListData(result,type);
          break;
        case Common.TOP_CHECKIN_USERS:
          result = activityQueryService.getTopCheckinUsers(Symphonys.TOP_CNT);
          ret = packageListData(result,type);
          break;
        case "topOnlineUsers":
          result = activityQueryService.getTopOnlineTimeUsers(Symphonys.TOP_CNT);
          ret = packageListData(result,type);
          break;
        case "adr":
          result = activityQueryService.getTopADR(Symphonys.TOP_CNT);
          ret = packageListData(result,type);
          break;
        case "mofish":
          result = activityQueryService.getTopMofish(Symphonys.TOP_CNT);
          ret = packageListData(result,type);
          break;
        case "smallmofish":
          result = activityQueryService.getTopSmallMofish(Symphonys.TOP_CNT);
          ret = packageListData(result,type);
          break;
        case "lifeRestart":
          result = activityQueryService.getTopLifeRestart(Symphonys.TOP_CNT);
          ret = packageListData(result,type);
          break;
        case "evolve":
          final String evoType = subType != null ?
                  subType : "achievement";
          result = activityQueryService.getEvolve(evoType, Symphonys.TOP_CNT);
          ret = packageListData(result,type);
          ret.put("subType", evoType);
          break;
        case "emoji":
          result = activityQueryService.getTopEmoji(Symphonys.TOP_CNT);
          ret = packageListData(result,type);
          break;
        case "xiaoice":
          final String xiaoiceType = subType != null ?
                  subType : "achievement";
          result = getXiaoiceData(subType);
          ret = packageListData(result,type);
          ret.put("subType", xiaoiceType);
          break;
        case "invite":
          result = getInviteData();
          ret = packageListData(result,type);
          break;
        case "donate":
          ret = getDonateData();
          break;
        case "perfect":
          ret = getPerfectData();
          break;
        default:
          ret = new JSONObject();
          ret.put("type",type);
          ret.put("data",new ArrayList<>());
          break;
      }
      return ret;
    }

    /**
     * Shows top.
     *
     * @param context the specified context
     */
    public void showTop(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/index.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModel.put(Common.SELECTED, Common.TOP);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    public void showProfession(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/profession.ftl");
        context.setRenderer(renderer);
        final Map<String, Object> dataModel = renderer.getDataModel();
        try {
            final JSONObject user = (JSONObject) context.attr(User.USER);
            final ProfessionRankingQueryService.RankingPage page = professionRankingQueryService.getPage(
                    context.param("professionId"), user.getString(org.b3log.latke.Keys.OBJECT_ID), false);
            dataModel.put("professionRankingDefinitions", page.definitions());
            dataModel.put("selectedProfessionId", page.selected().getString("professionId"));
            dataModel.put("professionRankingEntries", page.entries());
        } catch (final Exception e) {
            throw new IllegalStateException("读取职业排行失败", e);
        }
        dataModel.put(Common.SELECTED, Common.TOP);
        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Shows link ranking list.
     *
     * @param context the specified context
     */
    public void showLink(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/link.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final List<JSONObject> topLinks = linkQueryService.getTopLink(Symphonys.TOP_CNT);
        dataModel.put(Common.TOP_LINKS, topLinks);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Get link ranking list
     * @param context
     */
    public void getLink(final RequestContext context) {
      try {
          final List<JSONObject> result = linkQueryService.getTopLink(Symphonys.TOP_CNT);
          context.renderJSON(new JSONObject().put("data", new JSONArray(result))).renderCode(StatusCodes.SUCC);
          } catch (Exception e) {
              context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
          }
  }

    /**
     * Shows balance ranking list.
     *
     * @param context the specified context
     */
    public void showBalance(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/balance.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final List<JSONObject> users = pointtransferQueryService.getTopBalanceUsers(Symphonys.TOP_CNT);
        dataModel.put(Common.TOP_BALANCE_USERS, users);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * get balance ranking list.
     *
     * @param context the specified context
     */
    public void getBalance(final RequestContext context) {
      try {
        final List<JSONObject> result = pointtransferQueryService.getTopBalanceUsers(Symphonys.TOP_CNT);
        removePrivateDatas(result);
        context.renderJSON(new JSONObject().put("data", new JSONArray(result))).renderCode(StatusCodes.SUCC);
        } catch (Exception e) {
            context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
        }
    }

    /**
     * Shows consumption ranking list.
     *
     * @param context the specified context
     */
    public void showConsumption(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/consumption.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final List<JSONObject> users = pointtransferQueryService.getTopConsumptionUsers(Symphonys.TOP_CNT);
        dataModel.put(Common.TOP_CONSUMPTION_USERS, users);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Get consumption ranking list.
     *
     * @param context the specified context
     */
    public void getConsumption(final RequestContext context) {
      try{
          final List<JSONObject> result = pointtransferQueryService.getTopConsumptionUsers(Symphonys.TOP_CNT);
          removePrivateDatas(result);
            context.renderJSON(new JSONObject().put("data", new JSONArray(result))).renderCode(StatusCodes.SUCC);
          } catch (Exception e) {
              context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
          }
  }

    /**
     * Shows checkin ranking list.
     *
     * @param context the specified context
     */
    public void showCheckin(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/checkin.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final List<JSONObject> users = activityQueryService.getTopCheckinUsers(Symphonys.TOP_CNT);
        dataModel.put(Common.TOP_CHECKIN_USERS, users);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Get checkin ranking list
     * @param context
     */
    public void getCheckin(final RequestContext context) {
      try {
          final List<JSONObject> result = activityQueryService.getTopCheckinUsers(Symphonys.TOP_CNT);
          // 结果去敏
          removePrivateDatas(result);
          context.renderJSON(new JSONObject().put("data", new JSONArray(result))).renderCode(StatusCodes.SUCC);
          } catch (Exception e) {
              context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
          }
  }

    /**
     * Shows online ranking list.
     *
     * @param context the specified context
     */
    public void showOnline(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/online.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final List<JSONObject> users = activityQueryService.getTopOnlineTimeUsers(Symphonys.TOP_CNT);
        dataModel.put("onlineTopUsers", users);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Get online ranking list
     * @param context
     */
    public void getOnline(final RequestContext context) {
      try {
          final List<JSONObject> result = activityQueryService.getTopOnlineTimeUsers(Symphonys.TOP_CNT);
          // 结果去敏
          removePrivateDatas(result);
          JSONObject ret = new JSONObject();
          ret.put("data", result);
          context.renderJSON(ret).renderCode(StatusCodes.SUCC);
          } catch (Exception e) {
              context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
          }
  }

    /**
     * Shows ADR score ranking list.
     *
     * @param context
     */
    public void showADR(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/adr.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final List<JSONObject> users = activityQueryService.getTopADR(Symphonys.TOP_CNT);
        dataModel.put("topUsers", users);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Get ADR ranking list
     * @param context
     */
    public void getADR(final RequestContext context) {
      try {
          final List<JSONObject> result = activityQueryService.getTopADR(Symphonys.TOP_CNT);
          // 结果去敏
          removePrivateDatas(result);
          context.renderJSON(new JSONObject().put("data", new JSONArray(result))).renderCode(StatusCodes.SUCC);
          } catch (Exception e) {
              context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
          }
  }

    /**
     * Shows emoji score ranking list.
     *
     * @param context
     */
    public void showEmoji(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/emoji.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final List<JSONObject> users = activityQueryService.getTopEmoji(Symphonys.TOP_CNT);
        dataModel.put("topUsers", users);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Get emoji ranking list
     * @param context
     */
    public void getEmoji(final RequestContext context) {
      try {
          final List<JSONObject> result = activityQueryService.getTopEmoji(Symphonys.TOP_CNT);
          removePrivateDatas(result);
          context.renderJSON(new JSONObject().put("data", new JSONArray(result))).renderCode(StatusCodes.SUCC);
          } catch (Exception e) {
              context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
          }
  }

    /**
     * Shows Mofish score ranking list.
     *
     * @param context
     */
    public void showMofish(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/mofish.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final List<JSONObject> users = activityQueryService.getTopMofish(Symphonys.TOP_CNT);
        dataModel.put("topUsers", users);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Get Mofish ranking list
     * @param context
     */
    public void getMofish(final RequestContext context) {
      try {
          final List<JSONObject> result = activityQueryService.getTopMofish(Symphonys.TOP_CNT);
          // 结果去敏
          removePrivateDatas(result);
          context.renderJSON(new JSONObject().put("data", new JSONArray(result))).renderCode(StatusCodes.SUCC);
          } catch (Exception e) {
              context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
          }
  }

     /**
     * Shows SmallMofish score ranking list.
     *
     * @param context
     */
    public void showSmallMofish(final RequestContext context) {
      final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/smallmofish.ftl");
      final Map<String, Object> dataModel = renderer.getDataModel();
      final List<JSONObject> users = activityQueryService.getTopSmallMofish(Symphonys.TOP_CNT);
      dataModel.put("topUsers", users);

      dataModelService.fillHeaderAndFooter(context, dataModel);
      dataModelService.fillRandomArticles(dataModel);
      dataModelService.fillSideHotArticles(dataModel);
      dataModelService.fillSideTags(dataModel);
      dataModelService.fillLatestCmts(dataModel);
  }

  /**
     * Get Small Mofish ranking list
     * @param context
     */
    public void getSmallMofish(final RequestContext context) {
      try {
          final List<JSONObject> result = activityQueryService.getTopSmallMofish(Symphonys.TOP_CNT);
          // 结果去敏
          removePrivateDatas(result);
          context.renderJSON(new JSONObject().put("data", new JSONArray(result))).renderCode(StatusCodes.SUCC);
          } catch (Exception e) {
              context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
          }
  }

    /**
     * Shows Life Restart ranking list.
     *
     * @param context
     */
    public void showLifeRestart(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/lifeRestart.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        final List<JSONObject> users = activityQueryService.getTopLifeRestart(Symphonys.TOP_CNT);
        dataModel.put("topUsers", users);
        for (JSONObject user : users) {
            JSONObject profile = user.optJSONObject("profile");
            avatarQueryService.fillUserAvatarURL(profile);
            user.put("profile", profile);
        }

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Get Life Restart ranking list
     * @param context
     */
    public void getLifeRestart(final RequestContext context) {
      try {
          final List<JSONObject> result = activityQueryService.getTopLifeRestart(Symphonys.TOP_CNT);
          // 结果去敏
          removePrivateDatas(result);
          context.renderJSON(new JSONObject().put("data", new JSONArray(result))).renderCode(StatusCodes.SUCC);
          } catch (Exception e) {
              context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
          }
  }

    /**
     * Shows Evolve ranking list.
     *
     * @param context
     */
    public void showEvolve(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/evolve.ftl");
        final Request request = context.getRequest();
        final String type = request.getParameter("type") != null ?
                request.getParameter("type") : "achievement";
        final Map<String, Object> dataModel = renderer.getDataModel();
        final List<JSONObject> users = activityQueryService.getEvolve(type, Symphonys.TOP_CNT);
        for (JSONObject user : users) {
            JSONObject profile = user.optJSONObject("profile");
            avatarQueryService.fillUserAvatarURL(profile);
            user.put("profile", profile);
        }
        dataModel.put("topUsers", users);
        dataModel.put("type", type);

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Get Evolve ranking list
     * @param context
     */
    public void getEvolve(final RequestContext context) {
      try {
        final Request request = context.getRequest();
        final String type = request.getParameter("type") != null ?
                request.getParameter("type") : "achievement";
          final List<JSONObject> result = activityQueryService.getEvolve(type, Symphonys.TOP_CNT);
          JSONObject ret = new JSONObject();
          ret.put("data", new JSONArray(result));
          ret.put("type", type);
          context.renderJSON(ret).renderCode(StatusCodes.SUCC);
          } catch (Exception e) {
              context.renderJSON(new JSONObject()).renderCode(StatusCodes.ERR).renderMsg("请求非法");
          }
  }

    /**
     * Shows Xiaoice ranking list.
     *
     * @param context
     */
    public void showXiaoice(final RequestContext context) {
      final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/xiaoice.ftl");
      final Map<String, Object> dataModel = renderer.getDataModel();
      final Request request = context.getRequest();
      final String type = request.getParameter("type") != null ? request.getParameter("type") : "0";
      dataModel.put("type", type);
      HttpResponse response;
      try {
        response = HttpRequest
            .get("https://pwl.yuis.cc/GetXiaoIceGameRank?key=xiaoIceGame&type=" + type)
            .connectionTimeout(3000).timeout(7000).header("User-Agent", Vocation.UA)
            .send();
        if (200 == response.statusCode()) {
          response.charset("UTF-8");
          final JSONObject result = new JSONObject(response.bodyText());
          JSONArray dataList = result.optJSONArray("data");
          List<JSONObject> resultList = new ArrayList<>();
          for (int i = 0; i < dataList.length(); i++) {
            JSONObject data = dataList.optJSONObject(i);
            String uname = data.optString("uname");
            JSONObject family = new JSONObject(data.optString("family"));

            String[] lvFilter = new String[] { "黄阶低级", "黄阶中级", "黄阶高级", "玄阶低级", "玄阶中级", "玄阶高级", "地阶低级", "地阶中级", "地阶高级",
                "天阶低级", "天阶中级", "天阶高级" };
            int ancestry = family.optInt("ancestry");
            int gongfa = family.optInt("gongfa");
            data.put("ancestry", lvFilter[ancestry]);
            data.put("gongfa", lvFilter[gongfa]);
            data.remove("family");
            try {
              JSONObject user = userQueryService.getUserByName(uname);
              data.put("userAvatarURL", user.optString(UserExt.USER_AVATAR_URL));
              data.put("userIntro", user.optString(UserExt.USER_INTRO));
              data.put("userURL", user.optString(User.USER_URL));
              data.put("userNo", user.optInt(UserExt.USER_NO));
              data.put("userAppRole", user.optInt(UserExt.USER_APP_ROLE));
              avatarQueryService.fillUserAvatarURL(data);
            } catch (Exception e) {
              continue;
            }

            resultList.add(data);
          }
          dataModel.put("data", resultList);
        }
      } catch (HttpException e) {
        dataModel.put("data", Lists.emptyList());
      }
      dataModelService.fillHeaderAndFooter(context, dataModel);
      dataModelService.fillRandomArticles(dataModel);
      dataModelService.fillSideHotArticles(dataModel);
      dataModelService.fillSideTags(dataModel);
      dataModelService.fillLatestCmts(dataModel);
    }

    private List<JSONObject> getXiaoiceData(String type) {
      final JSONObject dataModel = new JSONObject();
      List<JSONObject> resultList = new ArrayList<>();
      type = type != null ?
              type : "0";
      dataModel.put("type", type);
      final HttpResponse response = HttpRequest.get("https://pwl.yuis.cc/GetXiaoIceGameRank?key=xiaoIceGame&type=" + type)
              .connectionTimeout(3000).timeout(7000).header("User-Agent", Vocation.UA)
              .send();
      if (200 == response.statusCode()) {
          response.charset("UTF-8");
          final JSONObject result = new JSONObject(response.bodyText());
          JSONArray dataList = result.optJSONArray("data");
          for (int i = 0; i < dataList.length(); i++) {
              JSONObject data = dataList.optJSONObject(i);
              String uname = data.optString("uname");
              JSONObject family = new JSONObject(data.optString("family"));

              String[] lvFilter = new String[]{"黄阶低级", "黄阶中级", "黄阶高级", "玄阶低级", "玄阶中级", "玄阶高级", "地阶低级", "地阶中级", "地阶高级", "天阶低级", "天阶中级", "天阶高级"};
              int ancestry = family.optInt("ancestry");
              int gongfa = family.optInt("gongfa");
              data.put("ancestry", lvFilter[ancestry]);
              data.put("gongfa", lvFilter[gongfa]);
              data.remove("family");
              try {
                  JSONObject user = userQueryService.getUserByName(uname);
                  data.put("userAvatarURL", user.optString(UserExt.USER_AVATAR_URL));
                  data.put("userIntro", user.optString(UserExt.USER_INTRO));
                  data.put("userURL", user.optString(User.USER_URL));
                  data.put("userNo", user.optInt(UserExt.USER_NO));
                  data.put("userAppRole", user.optInt(UserExt.USER_APP_ROLE));
                  avatarQueryService.fillUserAvatarURL(data);
              } catch (Exception e) {
                  continue;
              }

              resultList.add(data);
          }
        }
        return resultList;
    }

    /**
     * get Xiaoice ranking list.
     *
     * @param context
     */
    public void getXiaoice(final RequestContext context) {
      final JSONObject dataModel = new JSONObject();
      final String type = context.param("type");
      dataModel.put("data", getXiaoiceData(type));
      context.renderJSON(dataModel).renderCode(StatusCodes.SUCC);
  }

    /**
     * Shows Invite ranking list.
     *
     * @param context
     */
    public void showInvite(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/invite.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        try {
            List<JSONObject> list = pointtransferRepository.select("select toId,count(toId) as c " +
                    "from " + pointtransferRepository.getName() + " " +
                    "where type = 6 " +
                    "group by toId " +
                    "order by c desc " +
                    "limit 64;");
            List<JSONObject> result = new ArrayList<>();
            for (JSONObject user : list) {
                try {
                    JSONObject userData = userQueryService.getUser(user.optString("toId"));
                    avatarQueryService.fillUserAvatarURL(userData);
                    user.put("profile", userData);
                    result.add(user);
                } catch (Exception e) {
                    continue;
                }
            }
            dataModel.put("data", result);
        } catch (Exception ignored) {
        }

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Get Invite ranking data.
     *
     */
    private List<JSONObject> getInviteData() {
      List<JSONObject> result = new ArrayList<>();
      try{
        List<JSONObject> list = pointtransferRepository.select("select toId,count(toId) as c " +
                    "from " + pointtransferRepository.getName() + " " +
                    "where type = 6 " +
                    "group by toId " +
                    "order by c desc " +
                    "limit 64;");
            for (JSONObject user : list) {
                try {
                    JSONObject userData = userQueryService.getUser(user.optString("toId"));
                    avatarQueryService.fillUserAvatarURL(userData);
                    removePrivateData(userData);
                    user.put("profile", userData);
                    result.add(user);
                } catch (Exception e) {
                    continue;
                }
            }
            return result;
        } catch (Exception ignored) {
          return result;
        }
    }

    /**
     * Get Invite ranking list.
     *
     * @param context the specified context
     */
    public void getInvite(final RequestContext context) {
      final JSONObject dataModel = new JSONObject();
      try {
        List<JSONObject> result = getInviteData();
        dataModel.put("data", result);
        context.renderJSON(dataModel).renderCode(StatusCodes.SUCC);
      } catch (Exception ignored) {
        context.renderJSON(dataModel).renderCode(StatusCodes.ERR);
      }
  }

    /**
     * Shows Donate ranking list.
     *
     * @param context the specified context
     */
    public void showDonate(final RequestContext context) {
      final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/donate.ftl");
      final Map<String, Object> dataModel = renderer.getDataModel();
      try {
        List<JSONObject> totalList = sponsorRepository.select("select sum(amount) as totalAmount " +
                  "from " + sponsorRepository.getName() + " " +
                  "limit 1;");
        JSONObject totalJSON = totalList.get(0);
        double totalAmount = totalJSON.optDouble("totalAmount", 0d);
        BigDecimal donateMakeDaysBigDecimal = new BigDecimal(String.valueOf(totalAmount / 13.33));
        double donateMakeDays = donateMakeDaysBigDecimal.setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
        totalJSON.put("donateMakeDays", donateMakeDays);
        dataModel.put("totalData", totalJSON);
      }
      catch (Exception ignored) {
      }
      try {
          List<JSONObject> list = sponsorRepository.select("select userId as userId,sum(amount) as total,count(*) as totalCount " +
                  "from " + sponsorRepository.getName() + " " +
                  "group by userId " +
                  "order by total desc " +
                  "limit 10;");
          List<JSONObject> result = new ArrayList<>();
          for (JSONObject user : list) {
              try {
                  JSONObject userData = userQueryService.getUser(user.optString("userId"));
                  avatarQueryService.fillUserAvatarURL(userData);
                  user.put("profile", userData);
                  if (userData.optString("userName") != null && !userData.optString("userName").isEmpty() && !userData.optString("userName").equals("_")) {
                      result.add(user);
                  }
              } catch (Exception e) {
                  continue;
              }
          }
          dataModel.put("data", result);
      } catch (Exception ignored) {
      }

      dataModelService.fillHeaderAndFooter(context, dataModel);
      dataModelService.fillRandomArticles(dataModel);
      dataModelService.fillSideHotArticles(dataModel);
      dataModelService.fillSideTags(dataModel);
      dataModelService.fillLatestCmts(dataModel);
  }

    public static int getDonateRankByUserId(String userId) {
        final BeanManager beanManager = BeanManager.getInstance();
        final SponsorRepository sponsorRepository = beanManager.getReference(SponsorRepository.class);
        try {
            // 查询所有用户的捐赠总额，按总额降序排列
            List<JSONObject> list = sponsorRepository.select(
                    "select userId as userId, sum(amount) as total " +
                            "from " + sponsorRepository.getName() + " " +
                            "group by userId " +
                            "order by total desc;"
            );
            int rank = 1;
            for (JSONObject user : list) {
                String currentUserId = user.optString("userId");
                if (currentUserId != null && currentUserId.equals(userId)) {
                    return rank;
                }
                rank++;
            }
        } catch (Exception e) {
            // 查询失败
            e.printStackTrace();
        }
        // 没找到该用户
        return -1;
    }

  /**
     * Get Donate ranking data.
     *
     */
  private JSONObject getDonateData() {
    JSONObject dataModel = new JSONObject();
      try {
        List<JSONObject> totalList = sponsorRepository.select("select sum(amount) as totalAmount " +
                  "from " + sponsorRepository.getName() + " " +
                  "limit 1;");
        JSONObject totalJSON = totalList.get(0);
        double totalAmount = totalJSON.optDouble("totalAmount");
        BigDecimal donateMakeDaysBigDecimal = new BigDecimal(String.valueOf(totalAmount / 5));
        double donateMakeDays = donateMakeDaysBigDecimal.setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
        totalJSON.put("donateMakeDays", donateMakeDays);
        dataModel.put("totalData", totalJSON);
      }
      catch (Exception ignored) {
      }
      try {
          List<JSONObject> list = sponsorRepository.select("select userId as userId,sum(amount) as total,count(*) as totalCount " +
                  "from " + sponsorRepository.getName() + " " +
                  "group by userId " +
                  "order by total desc " +
                  "limit 64;");
          List<JSONObject> result = new ArrayList<>();
          for (JSONObject user : list) {
              try {
                  JSONObject userData = userQueryService.getUser(user.optString("userId"));
                  removePrivateData(userData);
                  avatarQueryService.fillUserAvatarURL(userData);
                  user.put("profile", userData);
                  result.add(user);
              } catch (Exception e) {
                  continue;
              }
          }
          dataModel.put("data", result);
          dataModel.put("type", "donate");
      } catch (Exception ignored) {
      }
    return dataModel;
  }

  /**
     * get Donate ranking list.
     *
     * @param context the specified context
     */
    public void getDonate(final RequestContext context) {
      final JSONObject dataModel = getDonateData();
      context.renderJSON(dataModel).renderCode(StatusCodes.SUCC);
    }

    /**
     * Shows Perfect ranking list.
     *
     * @param context
     */
    public void showPerfect(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "top/perfect.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        try {
            List<JSONObject> list = articleRepository.select("select "+ Article.ARTICLE_AUTHOR_ID +" as userId,count(*) as count " +
                    "from " + articleRepository.getName() + " " +
                    "where " + Article.ARTICLE_PERFECT + " = " + Article.ARTICLE_PERFECT_C_PERFECT+ " " +
                    "group by userId " +
                    "order by count desc " +
                    "limit 64;");
            List<JSONObject> result = new ArrayList<>();
            for (JSONObject user : list) {
                try {
                    JSONObject userData = userQueryService.getUser(user.optString("userId"));
                    avatarQueryService.fillUserAvatarURL(userData);
                    user.put("profile", userData);
                    result.add(user);
                } catch (Exception e) {
                    continue;
                }
            }
            dataModel.put("data", result);
        } catch (Exception ignored) {
        }

        dataModelService.fillHeaderAndFooter(context, dataModel);
        dataModelService.fillRandomArticles(dataModel);
        dataModelService.fillSideHotArticles(dataModel);
        dataModelService.fillSideTags(dataModel);
        dataModelService.fillLatestCmts(dataModel);
    }

    /**
     * Get Perfect ranking data.
     *
     */
    private JSONObject getPerfectData() {
      final JSONObject dataModel = new JSONObject();

      try {
        List<JSONObject> list = articleRepository.select("select "+ Article.ARTICLE_AUTHOR_ID +" as userId,count(*) as count " +
                "from " + articleRepository.getName() + " " +
                "where " + Article.ARTICLE_PERFECT + " = " + Article.ARTICLE_PERFECT_C_PERFECT+ " " +
                "group by userId " +
                "order by count desc " +
                "limit 64;");
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject user : list) {
            try {
                JSONObject userData = userQueryService.getUser(user.optString("userId"));
                avatarQueryService.fillUserAvatarURL(userData);
                user.put("profile", userData);
                removePrivateData(userData);
                result.add(user);
            } catch (Exception e) {
                continue;
            }
        }
        dataModel.put("data", result);
        dataModel.put("type", "perfect");
    } catch (Exception ignored) {
    }
    return dataModel;
    }

    /**
     * Get Perfect ranking list.
     *
     * @param context the specified context
     */
    public void getPerfect(final RequestContext context) {
      final JSONObject dataModel = getPerfectData();
      context.renderJSON(dataModel).renderCode(StatusCodes.SUCC);
    }
}
