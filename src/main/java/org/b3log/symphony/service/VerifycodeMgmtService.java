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
package org.b3log.symphony.service;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Transactional;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Verifycode;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.VerifycodeRepository;
import org.b3log.symphony.util.Mails;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.util.*;

/**
 * Verifycode management service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.2.0.1, Jun 12, 2018
 * @since 1.3.0
 */
@Service
public class VerifycodeMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(VerifycodeMgmtService.class);

    /**
     * Verifycode repository.
     */
    @Inject
    private VerifycodeRepository verifycodeRepository;

    /**
     * User repository.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Removes a verifycode with the specified code.
     *
     * @param code the specified code
     */
    @Transactional
    public void removeByCode(final String code) {
        final Query query = new Query().setFilter(new PropertyFilter(Verifycode.CODE, FilterOperator.EQUAL, code));
        try {
            verifycodeRepository.remove(query);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Removes by code [" + code + "] failed", e);
        }
    }

    /**
     * Adds a verifycode with the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          {
     *                          "userId"; "",
     *                          "type": int,
     *                          "bizType": int,
     *                          "receiver": "",
     *                          "code": "",
     *                          "status": int,
     *                          "expired": long
     *                          }
     * @return verifycode id
     * @throws ServiceException service exception
     */
    @Transactional
    public String addVerifycode(final JSONObject requestJSONObject) throws ServiceException {
        try {
            return verifycodeRepository.add(requestJSONObject);
        } catch (final RepositoryException e) {
            final String msg = "Adds verifycode failed";
            LOGGER.log(Level.ERROR, msg, e);

            throw new ServiceException(msg);
        }
    }

    /**
     * Removes expired verifycodes.
     */
    @Transactional
    public void removeExpiredVerifycodes() {
        final Query query = new Query().setFilter(new PropertyFilter(Verifycode.EXPIRED,
                FilterOperator.LESS_THAN, new Date().getTime()));
        try {
            verifycodeRepository.remove(query);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Expires verifycodes failed", e);
        }
    }

    /**
     * Sends email verifycode.
     */
    @Transactional
    public void sendEmailVerifycode() {
        final List<Filter> filters = new ArrayList<>();
        filters.add(new PropertyFilter(Verifycode.TYPE, FilterOperator.EQUAL, Verifycode.TYPE_C_EMAIL));
        filters.add(new PropertyFilter(Verifycode.STATUS, FilterOperator.EQUAL, Verifycode.STATUS_C_UNSENT));
        final Query query = new Query().setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters));
        try {
            final List<JSONObject> verifycodes = verifycodeRepository.getList(query);
            for (final JSONObject verifycode : verifycodes) {
                final String userId = verifycode.optString(Verifycode.USER_ID);
                final JSONObject user = userRepository.get(userId);
                if (null == user) {
                    continue;
                }

                final Map<String, Object> dataModel = new HashMap<>();

                final String userName = user.optString(User.USER_NAME);
                dataModel.put(User.USER_NAME, userName);

                final String toMail = verifycode.optString(Verifycode.RECEIVER);
                final String code = verifycode.optString(Verifycode.CODE);
                String subject;

                final int bizType = verifycode.optInt(Verifycode.BIZ_TYPE);
                switch (bizType) {
                    case Verifycode.BIZ_TYPE_C_REGISTER:
                        dataModel.put(Common.URL, Latkes.getServePath() + "/register?code=" + code);
                        subject = langPropsService.get("registerEmailSubjectLabel", Latkes.getLocale());
                        break;
                    case Verifycode.BIZ_TYPE_C_RESET_PWD:
                        dataModel.put(Common.URL, Latkes.getServePath() + "/reset-pwd?code=" + code);
                        subject = langPropsService.get("forgetEmailSubjectLabel", Latkes.getLocale());
                        break;
                    case Verifycode.BIZ_TYPE_C_BIND_EMAIL:
                        dataModel.put(Keys.CODE, code);
                        subject = langPropsService.get("bindEmailSubjectLabel", Latkes.getLocale());
                        break;
                    default:
                        LOGGER.warn("Send email verify code failed with wrong biz type [" + bizType + "]");
                        continue;
                }

                verifycode.put(Verifycode.STATUS, Verifycode.STATUS_C_SENT);
                verifycodeRepository.update(verifycode.optString(Keys.OBJECT_ID), verifycode);

                final String fromName = langPropsService.get("symphonyEnLabel")
                        + " " + langPropsService.get("verifycodeEmailFromNameLabel", Latkes.getLocale());
                Mails.sendHTML(fromName, subject, toMail, Mails.TEMPLATE_NAME_VERIFYCODE, dataModel);
            }
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Sends verifycode failed", e);
        }
    }

    public boolean sendVerifyCodeSMS(String phone, String code) {
        if (Latkes.getRuntimeMode().equals(Latkes.RuntimeMode.DEVELOPMENT)) {
            LOGGER.log(Level.INFO, "开发模式不真正发送短信，发送的手机号为：{}，当前短信验证码为：{}", phone, code);
            return true;
        } else {
            switch (Symphonys.SMS_ENABLE) {
                case "tencent":
                    return sendMsgTencentCloud(new String[]{phone}, new String[]{code});
                case "bao":
                    return sendMsgSmsBao(phone, code);
                default:
                    LOGGER.log(Level.ERROR, "短信服务未配置");
                    return false;
            }
        }
    }

    public boolean sendMsgSmsBao(String phone, String code) {
        StringBuilder url = new StringBuilder();
        url.append("https://api.smsbao.com/sms");
        url.append("?u=").append(Symphonys.BAO_SMS_U);
        url.append("&p=").append(Symphonys.BAO_SMS_P);
        url.append("&m=").append(phone);
        url.append("&c=").append("【北京白与画科技】您的验证码是" + code + "。如非本人操作，请忽略本短信");
        final HttpResponse response = HttpRequest.get(url.toString())
                .connectionTimeout(30000).timeout(70000).header(Common.USER_AGENT, Symphonys.USER_AGENT_BOT)
                .send();
        if (200 == response.statusCode() && "0".equals(response.bodyText())) {
            LOGGER.info("SMS provider accepted request");
            return true;
        } else {
            LOGGER.warn("SMS provider rejected request");
            return false;
        }
    }

    /** Account API delivery confirms acceptance for the one exact E.164 recipient. */
    public boolean sendAccountVerifyCodeSMS(String phone,String code) {
        if(phone==null||!phone.matches("1[0-9]{10}")||code==null||!code.matches("[0-9]{6}"))return false;
        if(Latkes.getRuntimeMode().equals(Latkes.RuntimeMode.DEVELOPMENT))return true;
        return switch(Symphonys.SMS_ENABLE){
            case "bao" -> sendMsgSmsBao(phone,code);
            case "tencent" -> {
                final String target="+86"+phone;
                final var response=requestTencentSms(new String[]{target},new String[]{code});
                final var statuses=response==null?null:response.getSendStatusSet();
                yield statuses!=null&&statuses.length==1&&statuses[0]!=null
                        &&target.equals(statuses[0].getPhoneNumber())&&"Ok".equals(statuses[0].getCode());
            }
            default -> false;
        };
    }

    /** Preserve the old provider success contract; account APIs use the confirmed variant. */
    public boolean sendMsgTencentCloud(String[] phoneNumber,String[] templateParam) {
        return requestTencentSms(phoneNumber,templateParam)!=null;
    }

    protected SendSmsResponse requestTencentSms(String[] phoneNumber,String[] templateParam) {
        Credential cred=new Credential(Symphonys.TEN_SMS_SECRET_ID,Symphonys.TEN_SMS_SECRET_KEY);
        SmsClient client=new SmsClient(cred,Symphonys.TEN_SMS_DIYU);
        SendSmsRequest req=new SendSmsRequest();
        req.setSmsSdkAppId(Symphonys.TEN_SMS_SDK_APPID);req.setSignName(Symphonys.TEN_SMS_SIGN_NAME);req.setTemplateId(Symphonys.TEN_SMS_TEMPLATE_ID);
        req.setPhoneNumberSet(phoneNumber);req.setTemplateParamSet(templateParam);
        try{return client.SendSms(req);}
        catch(TencentCloudSDKException e){LOGGER.warn("SMS provider request failed");return null;}
    }
}
