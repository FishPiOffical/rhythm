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

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.Query;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.Sponsor;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.repository.SponsorRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.service.CloudService;
import org.b3log.symphony.service.NotificationMgmtService;
import org.b3log.symphony.service.PointtransferMgmtService;
import org.b3log.symphony.service.SponsorService;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Singleton
public class AlipayProcessor {

    final static String APP_ID = Symphonys.get("pay.alipay.appId");
    final static String APP_PRIVATE_KEY = Symphonys.get("pay.alipay.appPrivateKey");
    final static String ALIPAY_PUBLIC_KEY = Symphonys.get("pay.alipay.alipayPublicKey");
    // 勋章信息
    final static String L1_NAME = "摸鱼派粉丝";
    final static String L1_DESC = "捐助摸鱼派达16; 编号No.";
    final static String L1_ATTR = "url=https://file.fishpi.cn/2021/12/ht1-d8149de4.jpg&backcolor=ffffff&fontcolor=ff3030";
    final static String L2_NAME = "摸鱼派忠粉";
    final static String L2_DESC = "捐助摸鱼派达256; 编号No.";
    final static String L2_ATTR = "url=https://file.fishpi.cn/2021/12/ht2-bea67b29.jpg&backcolor=87cefa&fontcolor=efffff";
    final static String L3_NAME = "摸鱼派铁粉";
    final static String L3_DESC = "捐助摸鱼派达1024; 编号No.";
    final static String L3_ATTR = "url=https://file.fishpi.cn/2021/12/ht3-b97ea102.jpg&backcolor=ee3a8c&fontcolor=ffffff";

    // 捐助相关
    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(AlipayProcessor.class);
    /**
     * String:
     * out_trade_no
     * <p>
     * JSONObject:
     * total_amount: 交易金额
     * note: 备注
     * userId: 用户ID
     */
    public static Map<String, JSONObject> chargeTrades = new HashMap<>();
    final String CHARSET = "UTF-8";
    @Inject
    private UserRepository userRepository;
    @Inject
    private SponsorService sponsorService;
    @Inject
    private CloudService cloudService;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);

        final AlipayProcessor alipayProcessor = beanManager.getReference(AlipayProcessor.class);
        Dispatcher.get("/pay/alipay", alipayProcessor::pay, loginCheck::handle);
        Dispatcher.get("/get-rank", alipayProcessor::getRank);
    }

    /**
     * Query trade.
     *
     * @param out_trade_no
     * @return
     */
    public static JSONObject query(String out_trade_no) {
        try {
            AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do", APP_ID, APP_PRIVATE_KEY, "json", "GBK", ALIPAY_PUBLIC_KEY, "RSA2");
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            JSONObject biz = new JSONObject();
            biz.put("out_trade_no", out_trade_no);
            request.setBizContent(biz.toString());
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            return new JSONObject(response.getBody());
        } catch (AlipayApiException e) {
            LOGGER.log(Level.ERROR, "Query trade failed", e);
        }
        return new JSONObject().put("code", "-1").put("subMsg", "交易查询出错，请联系管理员检查支付接口配置");
    }

    /**
     * 检查是否有交易完成
     */
    public static synchronized void checkTrades() {
        try {
            Iterator<Map.Entry<String, JSONObject>> iterator = chargeTrades.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, JSONObject> entry = iterator.next();
                String out_trade_no = entry.getKey();
                // 查看是否过期
                long minute = (((System.currentTimeMillis() - Long.parseLong(out_trade_no)) / 1000) / 60);
                if (minute > 120) {
                    // 大于2个小时，直接删除
                    iterator.remove();
                    continue;
                }
                JSONObject result = query(out_trade_no);
                if (result.optJSONObject("alipay_trade_query_response").optString("trade_status").equals("TRADE_SUCCESS")) {
                    String total_amount = entry.getValue().optString("total_amount");
                    String note = entry.getValue().optString("note");
                    String userId = entry.getValue().optString("userId");
                    // 添加捐助信息
                    final BeanManager beanManager = BeanManager.getInstance();
                    PointtransferMgmtService pointtransferMgmtService = beanManager.getReference(PointtransferMgmtService.class);
                    NotificationMgmtService notificationMgmtService = beanManager.getReference(NotificationMgmtService.class);
                    SponsorService sponsorService = beanManager.getReference(SponsorService.class);
                    CloudService cloudService = beanManager.getReference(CloudService.class);
                    int point;
                    double total = Double.parseDouble(total_amount);
                    point = ((int) total) * 80;
                    if (point == 0) {
                        point = 1;
                    }
                    // 打积分
                    final String transferId = pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, userId,
                            Pointtransfer.TRANSFER_TYPE_C_CHARGE, point, total_amount, System.currentTimeMillis(), "");
                    // 通知
                    final JSONObject notification = new JSONObject();
                    notification.put(Notification.NOTIFICATION_USER_ID, userId);
                    notification.put(Notification.NOTIFICATION_DATA_ID, transferId);
                    notificationMgmtService.addPointChargeNotification(notification);
                    // 保存捐赠记录
                    final JSONObject record = new JSONObject();
                    record.put(UserExt.USER_T_ID, userId);
                    record.put("time", System.currentTimeMillis());
                    record.put(Sponsor.SPONSOR_MESSAGE, note);
                    record.put(Sponsor.AMOUNT, total);
                    sponsorService.add(record);
                    // 统计用户总积分
                    double sum = sponsorService.getSum(userId);
                    // 三清
                    cloudService.removeMedal(userId, L1_NAME);
                    cloudService.removeMedal(userId, L2_NAME);
                    cloudService.removeMedal(userId, L3_NAME);
                    // 赋予勋章
                    if (sum >= 1024) {
                        cloudService.giveMedal(userId, L3_NAME, L3_DESC + getNo(userId, 3), L3_ATTR, "");
                        cloudService.giveMedal(userId, L2_NAME, L2_DESC + getNo(userId, 2), L2_ATTR, "");
                        cloudService.giveMedal(userId, L1_NAME, L1_DESC + getNo(userId, 1), L1_ATTR, "");
                    } else if (sum >= 256) {
                        cloudService.giveMedal(userId, L2_NAME, L2_DESC + getNo(userId, 2), L2_ATTR, "");
                        cloudService.giveMedal(userId, L1_NAME, L1_DESC + getNo(userId, 1), L1_ATTR, "");
                    } else if (sum >= 16) {
                        cloudService.giveMedal(userId, L1_NAME, L1_DESC + getNo(userId, 1), L1_ATTR, "");
                    }
                    // 删除键
                    iterator.remove();
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static int getNo(String userId, int level) {
        try {
            final BeanManager beanManager = BeanManager.getInstance();
            final SponsorRepository sponsorRepository = beanManager.getReference(SponsorRepository.class);
            List<JSONObject> data = sponsorRepository.listAsc();
            HashMap<String, Double> map = new HashMap<>();
            List<String> rank = new ArrayList<>();
            List<String> ignores = new ArrayList<>();
            for (JSONObject i : data) {
                String id = i.optString("userId");
                double amount = i.optDouble("amount");
                if (!ignores.contains(id)) {
                    if (map.containsKey(id)) {
                        switch (level) {
                            case 1:
                                if (map.get(id) + amount >= 16) {
                                    rank.add(id);
                                    ignores.add(id);
                                } else {
                                    map.put(id, map.get(id) + amount);
                                }
                                break;
                            case 2:
                                if (map.get(id) + amount >= 256) {
                                    rank.add(id);
                                    ignores.add(id);
                                } else {
                                    map.put(id, map.get(id) + amount);
                                }
                                break;
                            case 3:
                                if (map.get(id) + amount >= 1024) {
                                    rank.add(id);
                                    ignores.add(id);
                                } else {
                                    map.put(id, map.get(id) + amount);
                                }
                                break;
                        }
                    } else {
                        switch (level) {
                            case 1:
                                if (amount >= 16) {
                                    rank.add(id);
                                    ignores.add(id);
                                } else {
                                    map.put(id, amount);
                                }
                                break;
                            case 2:
                                if (amount >= 256) {
                                    rank.add(id);
                                    ignores.add(id);
                                } else {
                                    map.put(id, amount);
                                }
                                break;
                            case 3:
                                if (amount >= 1024) {
                                    rank.add(id);
                                    ignores.add(id);
                                } else {
                                    map.put(id, amount);
                                }
                                break;
                        }
                    }
                }
            }
            for (int i = 0; i < rank.size(); i++) {
                String id = rank.get(i);
                if (id.equals(userId)) {
                    return i + 1;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Cannot get user No", e);
        }

        return -1;
    }

    public void getRank(final RequestContext context) {
        JSONObject user = Sessions.getUser();
        if (user.optString(User.USER_NAME).equals("adlered")) {
            String id = context.param("u");
            int level = Integer.parseInt(context.param("l"));
            context.renderJSON(StatusCodes.SUCC);
            context.renderMsg(String.valueOf(getNo(id, level)));
            try {
                List<JSONObject> users = userRepository.getList(new Query());
                for (JSONObject i : users) {
                    String username = i.optString(User.USER_NAME);
                    String userId = i.optString(Keys.OBJECT_ID);
                    System.out.println(i.optString(UserExt.USER_NO) + " " + username);
                    // 统计用户总积分
                    double sum = sponsorService.getSum(userId);
                    // 三清
                    cloudService.removeMedal(userId, L1_NAME);
                    cloudService.removeMedal(userId, L2_NAME);
                    cloudService.removeMedal(userId, L3_NAME);
                    // 赋予勋章
                    if (sum >= 1024) {
                        System.out.println(username + "捐助大于1024");
                        cloudService.giveMedal(userId, L3_NAME, L3_DESC + getNo(userId, 3), L3_ATTR, "");
                        cloudService.giveMedal(userId, L2_NAME, L2_DESC + getNo(userId, 2), L2_ATTR, "");
                        cloudService.giveMedal(userId, L1_NAME, L1_DESC + getNo(userId, 1), L1_ATTR, "");
                    } else if (sum >= 256) {
                        System.out.println(username + "捐助大于256");
                        cloudService.giveMedal(userId, L2_NAME, L2_DESC + getNo(userId, 2), L2_ATTR, "");
                        cloudService.giveMedal(userId, L1_NAME, L1_DESC + getNo(userId, 1), L1_ATTR, "");
                    } else if (sum >= 16) {
                        System.out.println(username + "捐助大于16");
                        cloudService.giveMedal(userId, L1_NAME, L1_DESC + getNo(userId, 1), L1_ATTR, "");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            context.renderJSON(StatusCodes.ERR);
            context.renderMsg("no");
        }
    }

    /**
     * Pay.
     *
     * @param context the specified context
     */
    public void pay(final RequestContext context) {
        Response response = context.getResponse();
        try {
            JSONObject currentUser = Sessions.getUser();
            try {
                currentUser = ApiProcessor.getUserByKey(context.param("apiKey"));
            } catch (NullPointerException ignored) {
            }
            final String userId = currentUser.optString(Keys.OBJECT_ID);

            if (context.param("total_amount") == null || context.param("subject_type") == null) {
                response.setContentType("text/html;charset=" + CHARSET);
                response.sendBytes("交易参数错误".getBytes(StandardCharsets.UTF_8));
                return;
            }
            final String OUT_TRADE_NO = "" + System.currentTimeMillis();
            String total_amount = context.param("total_amount");
            total_amount = String.format("%.2f", Double.parseDouble(total_amount));
            String subject_type = context.param("subject_type");
            String note = "没有备注 :)";
            if (context.param("note") != null) {
                note = context.param("note");
            }
            note = note.replaceAll("[^0-9a-zA-Z\\u4e00-\\u9fa5,，.。！!?？《》]", "");
            if (note.length() > 32) {
                note = note.substring(0, 32);
            }
            JSONObject biz = new JSONObject();
            biz.put("out_trade_no", OUT_TRADE_NO);
            biz.put("total_amount", total_amount);
            JSONObject trade = new JSONObject();
            trade.put("total_amount", total_amount);
            trade.put("note", note);
            trade.put("userId", userId);
            switch (subject_type) {
                case "001":
                    biz.put("subject", "捐助摸鱼派");
                    chargeTrades.put(OUT_TRADE_NO, trade);
                    break;
                default:
                    biz.put("subject", "捐助摸鱼派");
                    chargeTrades.put(OUT_TRADE_NO, trade);
                    break;
            }
            biz.put("product_code", "FAST_INSTANT_TRADE_PAY");

            AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do", APP_ID, APP_PRIVATE_KEY, "json", CHARSET, ALIPAY_PUBLIC_KEY, "RSA2");
            AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
            alipayRequest.setReturnUrl(Latkes.getStaticServePath() + "/charge/point");
            alipayRequest.setBizContent(biz.toString());
            String result = alipayClient.pageExecute(alipayRequest).getBody();

            response.setContentType("text/html;charset=" + CHARSET);
            response.sendBytes(result.getBytes(StandardCharsets.UTF_8));
        } catch (AlipayApiException e) {
            response.setContentType("text/html;charset=" + CHARSET);
            response.sendBytes("交易参数错误".getBytes(StandardCharsets.UTF_8));
            LOGGER.log(Level.ERROR, "Cannot trade", e);
        }
    }
}
