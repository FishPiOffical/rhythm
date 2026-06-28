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
import org.b3log.latke.model.Pagination;
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.Paginator;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Follow;
import org.b3log.symphony.model.Membership;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.SystemSettings;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.service.ActivityMgmtService;
import org.b3log.symphony.service.ArticleQueryService;
import org.b3log.symphony.service.CloudService;
import org.b3log.symphony.service.DataModelService;
import org.b3log.symphony.service.FollowQueryService;
import org.b3log.symphony.service.MembershipQueryService;
import org.b3log.symphony.service.OpenIdRefreshTokenMgmtService;
import org.b3log.symphony.service.PointtransferQueryService;
import org.b3log.symphony.service.RoleQueryService;
import org.b3log.symphony.service.SystemSettingsService;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.util.OpenIdUtil;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.StatusCodes;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Singleton
public class OpenIdProcessor {
    private static final Logger LOGGER = LogManager.getLogger(ActivityMgmtService.class);

    private static final String OPENID_NS_KEY = "openid.ns";
    private static final String OPENID_NS = "http://specs.openid.net/auth/2.0";
    private static final String OPENID_MODE_KEY = "openid.mode";
    private static final String OPENID_MODE_CHECKID = "checkid_setup";
    private static final String OPENID_MODE_RES = "id_res";
    private static final String OPENID_MODE_CHECK_AUTH = "check_authentication";
    private static final String OPENID_IDENTITY_KEY = "openid.identity";
    private static final String OPENID_IDENTITY = "http://specs.openid.net/auth/2.0/identifier_select";
    private static final String OPENID_CLAIMED_ID_KEY = "openid.claimed_id";
    private static final String OPENID_CLAIMED_ID = "http://specs.openid.net/auth/2.0/identifier_select";
    private static final String OPENID_RETURN_TO_KEY = "openid.return_to";
    private static final String OPENID_REALM_KEY = "openid.realm";
    private static final String OPENID_OP_ENDPOINT_KEY = "openid.op_endpoint";
    private static final String OPENID_ASSOC_HANDLE_KEY = "openid.assoc_handle";
    private static final String OPENID_ASSOC_HANDLE = Symphonys.get("openid.assocHandle");
    private static final String OPENID_RESPONSE_NONCE_KEY = "openid.response_nonce";
    private static final String OPENID_SIGNED_KEY = "openid.signed";
    private static final String OPENID_SIGNED = "op_endpoint,claimed_id,identity,return_to,response_nonce,assoc_handle";
    private static final String OPENID_SIG_KEY = "openid.sig";
    private static final String FISHPI_SCOPE_KEY = "fishpi.scope";
    private static final String FISHPI_AUTH_REQUEST_ID_KEY = "fishpi.authRequestId";
    private static final String SCOPE_PROFILE_READ = "profile.read";
    private static final String SCOPE_PROFILE_DETAIL_READ = "profile.detail.read";
    private static final String SCOPE_POINTS_READ = "points.read";
    private static final String SCOPE_ARTICLES_READ = "articles.read";
    private static final String SCOPE_MEMBERSHIP_READ = "membership.read";
    private static final long OPENID_TMP_EXPIRES = 5 * 60 * 1000L;
    private static final List<ScopeDefinition> SCOPE_DEFINITIONS = Arrays.asList(
            new ScopeDefinition(SCOPE_PROFILE_READ, "个人信息"),
            new ScopeDefinition(SCOPE_PROFILE_DETAIL_READ, "详细资料"),
            new ScopeDefinition(SCOPE_POINTS_READ, "积分信息"),
            new ScopeDefinition(SCOPE_ARTICLES_READ, "发帖信息"),
            new ScopeDefinition(SCOPE_MEMBERSHIP_READ, "VIP信息")
    );
    private static final Set<String> ALL_SCOPES = new LinkedHashSet<>(Arrays.asList(
            SCOPE_PROFILE_READ, SCOPE_PROFILE_DETAIL_READ, SCOPE_POINTS_READ, SCOPE_ARTICLES_READ,
            SCOPE_MEMBERSHIP_READ
    ));

    private static final Map<String, AuthRequest> authRequestMap = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, AuthResult> respNonceMap = Collections.synchronizedMap(new LinkedHashMap<>());


    @Inject
    private DataModelService dataModelService;

    @Inject
    private UserQueryService userQueryService;

    @Inject
    private PointtransferQueryService pointtransferQueryService;

    @Inject
    private ArticleQueryService articleQueryService;

    @Inject
    private OpenIdRefreshTokenMgmtService openIdRefreshTokenMgmtService;

    @Inject
    private CloudService cloudService;

    @Inject
    private FollowQueryService followQueryService;

    @Inject
    private MembershipQueryService membershipQueryService;

    @Inject
    private RoleQueryService roleQueryService;

    @Inject
    private SystemSettingsService systemSettingsService;

    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);


        final OpenIdProcessor openIdProcessor = beanManager.getReference(OpenIdProcessor.class);
        Dispatcher.get("/openid/login", openIdProcessor::showLoginForm, loginCheck::handle,csrfMidware::fill);
        Dispatcher.post("/openid/confirm", openIdProcessor::confirm, loginCheck::handle,csrfMidware::fill);
        Dispatcher.post("/openid/verify", openIdProcessor::verify, csrfMidware::fill);
        Dispatcher.post("/openid/token", openIdProcessor::refreshToken);
        Dispatcher.get("/openid/user/profile", openIdProcessor::getProfile);
        Dispatcher.get("/openid/user/detail", openIdProcessor::getDetail);
        Dispatcher.get("/openid/user/points", openIdProcessor::getPoints);
        Dispatcher.get("/openid/user/articles", openIdProcessor::getArticles);
        Dispatcher.get("/openid/user/membership", openIdProcessor::getMembership);

        // 开启定时任务，清理过期的nonce
        Symphonys.SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(() -> {
            try {
                clearExpiredNonce();
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "清理nonce定时任务出错", e);
            } finally {
            }
        }, 0, 60 * 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * 显示授权页
     * @param context
     */
    public void showLoginForm(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, null);
        context.setRenderer(renderer);

        String ns = context.param(OPENID_NS_KEY);
        String mode = context.param(OPENID_MODE_KEY);
        String return_to = context.param(OPENID_RETURN_TO_KEY);
        String realm = context.param(OPENID_REALM_KEY);
        String identity = context.param(OPENID_IDENTITY_KEY);
        String claimed_id = context.param(OPENID_CLAIMED_ID_KEY);
        final Set<String> requestedScopes = parseScopes(context.param(FISHPI_SCOPE_KEY));
        if (requestedScopes.isEmpty()) {
            requestedScopes.add(SCOPE_PROFILE_READ);
        }

        if(ns==null||mode==null||return_to==null||realm==null||identity==null||claimed_id==null){
            context.sendError(500);
            return;
        }
        // 先验证参数是否正确，不正确就回首页
        if(!OPENID_NS.equals(ns)){
            context.sendError(500);
            return;
        }
        if(!OPENID_MODE_CHECKID.equals(mode)){
            context.sendError(500);
            return;
        }
        if(!OPENID_IDENTITY.equals(identity)){
            context.sendError(500);
            return;
        }
        if(!OPENID_CLAIMED_ID.equals(claimed_id)){
            context.sendError(500);
            return;
        }

        // 判断return url 是否是https 如果是localhost 和 127.0.0.1 也可以的
        try{
            URI returnTo = new URI(return_to);
            String scheme = returnTo.getScheme();
            String host = returnTo.getHost();

            boolean isAllowed = ("https".equalsIgnoreCase(scheme)) ||
                    ("localhost".equals(host)) ||
                    ("127.0.0.1".equals(host));
            if (!isAllowed) {
                context.sendError(500);
                return;
            }
        }catch (Exception e){
            context.sendError(500);
            return;
        }

        // 判断return_to 是否在 realm 下
        if(!return_to.startsWith(realm)){
            context.sendError(500);
            return;
        }

        // 取得目标站点名称 需要从realm 中取得开头那一段域名，去掉http或https，到第一个/结尾
        String realmName = realm.replaceFirst("^(https?://)?([^/]+).*", "$2");
//        String realmName = realm.substring(realm.lastIndexOf("/")+1);

        final String authRequestId = UUID.randomUUID().toString().replace("-", "");
        authRequestMap.put(authRequestId, new AuthRequest(ns, mode, return_to, realm, identity, claimed_id, requestedScopes));

        renderer.setTemplateName("verify/openid.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModel.put("realmName",realmName);
        dataModel.put("fishpi_auth_request_id", authRequestId);
        dataModel.put("fishpiScopes", buildScopeView(requestedScopes));
        dataModel.put("openid_ns",ns);
        dataModel.put("openid_mode",mode);
        dataModel.put("openid_identity",identity);
        dataModel.put("openid_claimed_id",claimed_id);
        dataModel.put("openid_realm",realm);
        dataModel.put("openid_return_to",return_to);
        dataModelService.fillHeaderAndFooter(context, dataModel);

    }


    /**
     * 授权登录
     * @param context
     */
    public void confirm(final RequestContext context){

        Request request = context.getRequest();
        final String authRequestId = request.getParameter(FISHPI_AUTH_REQUEST_ID_KEY);
        final AuthRequest authRequest = authRequestMap.remove(authRequestId);

        if (null == authRequest) {
            redirectWithError(context, null);
            return;
        }

        if (authRequest.isExpired()) {
            redirectWithError(context, authRequest.returnTo);
            return;
        }

        if ("true".equals(request.getParameter("cancel"))) {
            redirectWithError(context, authRequest.returnTo);
            return;
        }

        final Set<String> grantedScopes = parseGrantedScopes(request);
        if (!grantedScopes.containsAll(authRequest.requestedScopes) || grantedScopes.isEmpty()) {
            redirectWithError(context, authRequest.returnTo);
            return;
        }

        Map<String, String> result = new LinkedHashMap<>();
        try {
            // 取得当前登录的用户
            JSONObject user = Sessions.getUser();
            String userId = user.optString("oId");
            result.put(OPENID_NS_KEY,OPENID_NS);
            result.put(OPENID_OP_ENDPOINT_KEY, Latkes.getServePath()+"/openid" );
            result.put(OPENID_MODE_KEY, OPENID_MODE_RES);
            result.put(OPENID_CLAIMED_ID_KEY,Latkes.getServePath() + "/openid/id/"+ userId);
            result.put(OPENID_IDENTITY_KEY,Latkes.getServePath() + "/openid/id/" +userId);
            result.put(OPENID_ASSOC_HANDLE_KEY, OPENID_ASSOC_HANDLE);
            String nonce = OpenIdUtil.generateNonce();
            result.put(OPENID_RESPONSE_NONCE_KEY, nonce);
            respNonceMap.put(nonce, new AuthResult(userId, authRequest.realm, grantedScopes));
            result.put(OPENID_RETURN_TO_KEY, authRequest.returnTo);
            result.put(OPENID_SIGNED_KEY,OPENID_SIGNED);
            result.put(OPENID_SIG_KEY, OpenIdUtil.sign(result));

            StringBuilder redirect = new StringBuilder(authRequest.returnTo);
            if (!authRequest.returnTo.contains("?")) {
                redirect.append("?");
            } else {
                redirect.append("&");
            }

            for (Map.Entry<String, String> entry : result.entrySet()) {
                redirect.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), "UTF-8"))
                        .append("&");
            }
            redirect.deleteCharAt(redirect.length() - 1);
            context.sendRedirect(redirect.toString());
        }catch (Exception e){
            LOGGER.log(Level.ERROR,e.getMessage(),e);
            redirectWithError(context, authRequest.returnTo);
        }

    }

    private void redirectWithError(final RequestContext context,String returnTo){
        if(returnTo==null||returnTo.isEmpty()){
            context.sendRedirect(Latkes.getServePath());
            return;
        }
        StringBuilder redirect = new StringBuilder(returnTo);
        if (!returnTo.contains("?")) {
            redirect.append("?");
        } else {
            redirect.append("&");
        }
        try {
            redirect.append("error=").append(URLEncoder.encode("登录失败","UTF-8"));
        }catch (Exception e){
            LOGGER.log(Level.ERROR,e.getMessage(),e);
        }
        context.sendRedirect(redirect.toString());

    }

    public void verify(final RequestContext context){
        final JSONObject requestJSONObject = context.requestJSON();
        final String ns = requestJSONObject.optString(OPENID_NS_KEY);
        final String mode = requestJSONObject.optString(OPENID_MODE_KEY);
        final String op_endpoint = requestJSONObject.optString(OPENID_OP_ENDPOINT_KEY);
        final String return_to = requestJSONObject.optString(OPENID_RETURN_TO_KEY);
        final String identity = requestJSONObject.optString(OPENID_IDENTITY_KEY);
        final String claimed_id = requestJSONObject.optString(OPENID_CLAIMED_ID_KEY);
        final String response_nonce = requestJSONObject.optString(OPENID_RESPONSE_NONCE_KEY);
        final String assoc_handle = requestJSONObject.optString(OPENID_ASSOC_HANDLE_KEY);
        final String sig = requestJSONObject.optString(OPENID_SIG_KEY);
        AuthResult authResult = null;

        if(response_nonce!=null){
            // 先验证时间
            try{
                Date nonceTime = OpenIdUtil.extractNonceTimestamp(response_nonce);
                long now = System.currentTimeMillis();
                long delta = Math.abs(now - nonceTime.getTime());
                if (delta > 5 * 60 * 1000) {
                    respNonceMap.remove(response_nonce);
//                    context.renderJSON(StatusCodes.ERR).renderMsg("验证失败1");
                    sendVerifyResult(context,false);
                    return;
                }
            }catch (Exception e){
                respNonceMap.remove(response_nonce);
//                context.renderJSON(StatusCodes.ERR).renderMsg("验证失败2");
                sendVerifyResult(context,false);
                return;
            }
        }

        if(!respNonceMap.containsKey(response_nonce)){
//            context.renderJSON(StatusCodes.ERR).renderMsg("验证失败3");
            sendVerifyResult(context,false);
            return;
        }

        authResult = respNonceMap.get(response_nonce);
        respNonceMap.remove(response_nonce);

        if(ns==null||mode==null||op_endpoint==null||return_to==null||identity==null||claimed_id==null||response_nonce==null||assoc_handle==null){
//            context.renderJSON(StatusCodes.ERR).renderMsg("验证失败4");
            sendVerifyResult(context,false);
            return;
        }

        Map<String, String> signMap = new LinkedHashMap<>();
        signMap.put(OPENID_OP_ENDPOINT_KEY,op_endpoint);
        signMap.put(OPENID_CLAIMED_ID_KEY,claimed_id);
        signMap.put(OPENID_IDENTITY_KEY,identity);
        signMap.put(OPENID_RETURN_TO_KEY,return_to);
        signMap.put(OPENID_RESPONSE_NONCE_KEY,response_nonce);
        signMap.put(OPENID_ASSOC_HANDLE_KEY,assoc_handle);
        signMap.put(OPENID_SIGNED_KEY,OPENID_SIGNED);
        try {
            if(!sig.equals(OpenIdUtil.sign(signMap))){
                sendVerifyResult(context,false);
//                context.renderJSON(StatusCodes.ERR).renderMsg("验证失败5");
                return;
            }
        } catch (Exception e) {
            LOGGER.log(Level.ERROR,e.getMessage(),e);
//            context.renderJSON(StatusCodes.ERR).renderMsg("验证失败6");
            sendVerifyResult(context,false);
            return;
        }

        if(!OPENID_NS.equals(ns)){
//            context.renderJSON(StatusCodes.ERR).renderMsg("验证失败7");
            sendVerifyResult(context,false);
            return;
        }
        if(!OPENID_MODE_CHECK_AUTH.equals(mode)){
//            context.renderJSON(StatusCodes.ERR).renderMsg("验证失败8");
            sendVerifyResult(context,false);
            return;
        }
        if(!op_endpoint.equals(Latkes.getServePath()+"/openid")){
//            context.renderJSON(StatusCodes.ERR).renderMsg("验证失败9");
            sendVerifyResult(context,false);
            return;
        }

        try{
            URI returnTo = new URI(return_to);
            String scheme = returnTo.getScheme();
            String host = returnTo.getHost();

            boolean isAllowed = ("https".equalsIgnoreCase(scheme)) ||
                    ("localhost".equals(host)) ||
                    ("127.0.0.1".equals(host));
            if (!isAllowed) {
                sendVerifyResult(context,false);
                //context.renderJSON(StatusCodes.ERR).renderMsg("验证失败10");
                return;
            }
        }catch (Exception e){
            //context.renderJSON(StatusCodes.ERR).renderMsg("验证失败10");
            sendVerifyResult(context,false);
            return;
        }

        if(!identity.equals(claimed_id)){
//            context.renderJSON(StatusCodes.ERR).renderMsg("验证失败11");
            sendVerifyResult(context,false);
            return;
        }
        if(null == authResult || authResult.userId == null){
//            context.renderJSON(StatusCodes.ERR).renderMsg("验证失败12");
            sendVerifyResult(context,false);
            return;
        }
        String requestUserId = identity.substring(identity.lastIndexOf("/")+1);
        if(!requestUserId.equals(authResult.userId)){
//            context.renderJSON(StatusCodes.ERR).renderMsg("验证失败13");
            sendVerifyResult(context,false);
            return;
        }

//        context.renderJSON(StatusCodes.SUCC).renderMsg("验证成功");
        sendVerifyResult(context,true, authResult);

    }

    private void sendVerifyResult(final RequestContext context,Boolean result){
        sendVerifyResult(context, result, null);
    }

    private void sendVerifyResult(final RequestContext context,Boolean result, final AuthResult authResult){
      // 根据result纯文本返回
        if(result){
            final StringBuilder builder = new StringBuilder("ns:http://specs.openid.net/auth/2.0\nis_valid:true\n");
            if (null != authResult && !authResult.scopes.isEmpty()) {
                try {
                    final JSONObject tokenData = openIdRefreshTokenMgmtService.issueToken(authResult.userId,
                            authResult.scopes, authResult.realm);
                    builder.append("scope:").append(tokenData.optString("scope")).append("\n");
                    builder.append("token_type:").append(tokenData.optString("token_type")).append("\n");
                    builder.append("access_token:").append(tokenData.optString("access_token")).append("\n");
                    builder.append("expires_in:").append(tokenData.optLong("expires_in")).append("\n");
                    builder.append("refresh_token:").append(tokenData.optString("refresh_token")).append("\n");
                    builder.append("refresh_expires_in:").append(tokenData.optLong("refresh_expires_in")).append("\n");
                } catch (final Exception e) {
                    LOGGER.log(Level.ERROR, "Generates OpenID token failed", e);
                    context.sendString("ns:http://specs.openid.net/auth/2.0\nis_valid:false\n");
                    return;
                }
            }
            context.sendString(builder.toString());
        }else{
            context.sendString("ns:http://specs.openid.net/auth/2.0\nis_valid:false\n");
        }

    }

    public void refreshToken(final RequestContext context) {
        JSONObject requestJSONObject = null;
        String grantType = StringUtils.trimToEmpty(context.param("grant_type"));
        if (StringUtils.isBlank(grantType)) {
            requestJSONObject = readRequestJSON(context);
            grantType = StringUtils.trimToEmpty(requestJSONObject.optString("grant_type"));
        }
        if (!"refresh_token".equals(grantType)) {
            renderError(context, "参数错误");
            return;
        }

        String refreshToken = StringUtils.trimToEmpty(context.param("refresh_token"));
        if (StringUtils.isBlank(refreshToken)) {
            if (null == requestJSONObject) {
                requestJSONObject = readRequestJSON(context);
            }
            refreshToken = StringUtils.trimToEmpty(requestJSONObject.optString("refresh_token"));
        }
        if (!OpenIdUtil.isRefreshTokenFormat(refreshToken)) {
            renderAccessDenied(context);
            return;
        }

        try {
            renderSucc(context, openIdRefreshTokenMgmtService.refresh(refreshToken));
        } catch (final ServiceException e) {
            renderAccessDenied(context);
        }
    }

    private JSONObject readRequestJSON(final RequestContext context) {
        try {
            final JSONObject ret = context.requestJSON();
            return null == ret ? new JSONObject() : ret;
        } catch (final Exception e) {
            return new JSONObject();
        }
    }

    public void getProfile(final RequestContext context) {
        final JSONObject user = requireOpenIdUser(context, SCOPE_PROFILE_READ);
        if (null == user) {
            return;
        }

        final JSONObject data = new JSONObject()
                .put("userId", user.optString(Keys.OBJECT_ID))
                .put("userName", user.optString(User.USER_NAME))
                .put("userNickname", user.optString(UserExt.USER_NICKNAME))
                .put("userAvatarURL", user.optString(UserExt.USER_AVATAR_URL));
        renderSucc(context, data);
    }

    public void getDetail(final RequestContext context) {
        final JSONObject user = requireOpenIdUser(context, SCOPE_PROFILE_DETAIL_READ);
        if (null == user) {
            return;
        }

        final String userId = user.optString(Keys.OBJECT_ID);
        final JSONObject data = new JSONObject();
        data.put(User.USER_NAME, user.optString(User.USER_NAME));
        data.put(UserExt.USER_ONLINE_FLAG, user.optBoolean(UserExt.USER_ONLINE_FLAG));
        data.put(UserExt.ONLINE_MINUTE, user.optInt(UserExt.ONLINE_MINUTE));
        data.put(User.USER_URL, user.optString(User.USER_URL));
        data.put(UserExt.USER_NICKNAME, user.optString(UserExt.USER_NICKNAME));
        data.put(UserExt.USER_CITY, getPublicCity(user));
        data.put(UserExt.USER_AVATAR_URL, user.optString(UserExt.USER_AVATAR_URL));
        data.put(UserExt.USER_POINT, user.optInt(UserExt.USER_POINT));
        data.put(UserExt.USER_INTRO, user.optString(UserExt.USER_INTRO));
        data.put(Keys.OBJECT_ID, userId);
        data.put(UserExt.USER_NO, user.optString(UserExt.USER_NO));
        data.put(UserExt.USER_APP_ROLE, user.optString(UserExt.USER_APP_ROLE));
        data.put("sysMetal", cloudService.getEnabledMedal(userId));
        data.put("followerCount", followQueryService.getFollowerCount(userId, Follow.FOLLOWING_TYPE_C_USER));
        data.put("followingUserCount", followQueryService.getFollowingCount(userId, Follow.FOLLOWING_TYPE_C_USER));
        data.put(User.USER_ROLE, getRoleName(user));
        data.put("cardBg", getCardBg(userId));
        renderSucc(context, data);
    }

    public void getPoints(final RequestContext context) {
        final JSONObject user = requireOpenIdUser(context, SCOPE_POINTS_READ);
        if (null == user) {
            return;
        }

        final String userId = user.optString(Keys.OBJECT_ID);
        final int pageNum = getPositiveIntParam(context, "p", 1);
        final int pageSize = Math.min(getPositiveIntParam(context, "size", Symphonys.USER_HOME_LIST_CNT), 200);
        final JSONObject userPointsResult = pointtransferQueryService.getUserPoints(userId, pageNum, pageSize);
        if (null == userPointsResult) {
            renderError(context, "查询失败");
            return;
        }

        final int recordCount = userPointsResult.optInt(Pagination.PAGINATION_RECORD_COUNT);
        final JSONObject data = new JSONObject()
                .put("userId", userId)
                .put("userPoint", user.optInt(UserExt.USER_POINT))
                .put("records", buildPointRecords(userPointsResult.opt(Keys.RESULTS)))
                .put(Pagination.PAGINATION, buildPagination(pageNum, pageSize, recordCount, Symphonys.USER_HOME_LIST_WIN_SIZE));
        renderSucc(context, data);
    }

    public void getArticles(final RequestContext context) {
        final JSONObject user = requireOpenIdUser(context, SCOPE_ARTICLES_READ);
        if (null == user) {
            return;
        }

        final String userId = user.optString(Keys.OBJECT_ID);
        final int pageNum = getPositiveIntParam(context, "p", 1);
        final int pageSize = Math.min(getPositiveIntParam(context, "size", Symphonys.ARTICLE_LIST_CNT), 100);
        final List<JSONObject> userArticles = articleQueryService.getUserArticles(userId, Article.ARTICLE_ANONYMOUS_C_PUBLIC, pageNum, pageSize);
        int recordCount = 0;
        if (!userArticles.isEmpty()) {
            recordCount = userArticles.get(0).optInt(Pagination.PAGINATION_RECORD_COUNT);
        }

        final JSONObject data = new JSONObject()
                .put("userId", userId)
                .put("articles", buildArticleRecords(userArticles))
                .put(Pagination.PAGINATION, buildPagination(pageNum, pageSize, recordCount, Symphonys.USER_HOME_LIST_WIN_SIZE));
        renderSucc(context, data);
    }

    public void getMembership(final RequestContext context) {
        final JSONObject user = requireOpenIdUser(context, SCOPE_MEMBERSHIP_READ);
        if (null == user) {
            return;
        }

        try {
            final JSONObject membership = membershipQueryService.getStatusByUserId(user.optString(Keys.OBJECT_ID));
            renderSucc(context, buildMembershipData(membership));
        } catch (final ServiceException e) {
            renderError(context, "查询失败");
        }
    }

    private JSONObject requireOpenIdUser(final RequestContext context, final String requiredScope) {
        String authorization = StringUtils.trim(context.header("Authorization"));
        if (StringUtils.isBlank(authorization) || !StringUtils.startsWithIgnoreCase(authorization, "Bearer ")) {
            renderAccessDenied(context);
            return null;
        }

        final String token = StringUtils.trim(authorization.substring("Bearer ".length()));
        final JSONObject payload = OpenIdUtil.parseAccessToken(token);
        if (null == payload) {
            renderAccessDenied(context);
            return null;
        }

        final Set<String> scopes = parseScopes(payload.optString("scope"));
        if (!scopes.contains(requiredScope)) {
            renderAccessDenied(context);
            return null;
        }

        final JSONObject user = userQueryService.getUser(payload.optString("userId"));
        if (null == user || UserExt.USER_STATUS_C_VALID != user.optInt(UserExt.USER_STATUS)) {
            renderAccessDenied(context);
            return null;
        }

        return user;
    }

    private String getPublicCity(final JSONObject user) {
        try {
            if (user.optInt(UserExt.USER_GEO_STATUS) == UserExt.USER_GEO_STATUS_C_PUBLIC) {
                return user.optString(UserExt.USER_CITY);
            }
        } catch (final Exception ignored) {
        }

        return "";
    }

    private String getRoleName(final JSONObject user) {
        final JSONObject role = roleQueryService.getRole(user.optString(User.USER_ROLE));
        if (null == role) {
            return "";
        }

        return role.optString(Role.ROLE_NAME);
    }

    private String getCardBg(final String userId) {
        final JSONObject systemSettings = systemSettingsService.getByUsrId(userId);
        if (null == systemSettings) {
            return "";
        }

        try {
            final JSONObject settings = new JSONObject(systemSettings.optString(SystemSettings.SETTINGS));
            return settings.optString("cardBg");
        } catch (final Exception ignored) {
            return "";
        }
    }

    private JSONObject buildMembershipData(final JSONObject membership) {
        if (null == membership) {
            return buildInactiveMembershipData();
        }

        final int state = membership.optInt(Membership.STATE, 0);
        final long expiresAt = membership.optLong(Membership.EXPIRES_AT, 0L);
        final boolean active = 1 == state && (0L == expiresAt || expiresAt > System.currentTimeMillis());
        if (!active) {
            return buildInactiveMembershipData();
        }

        return new JSONObject()
                .put("active", true)
                .put(Membership.STATE, state)
                .put(Membership.LV_CODE, membership.optString(Membership.LV_CODE))
                .put(Membership.EXPIRES_AT, expiresAt)
                .put(Membership.CONFIG_JSON, membership.optString(Membership.CONFIG_JSON));
    }

    private JSONObject buildInactiveMembershipData() {
        return new JSONObject()
                .put("active", false)
                .put(Membership.STATE, 0)
                .put(Membership.LV_CODE, "")
                .put(Membership.EXPIRES_AT, 0L)
                .put(Membership.CONFIG_JSON, "");
    }

    private Set<String> parseScopes(final String scopeText) {
        final Set<String> ret = new LinkedHashSet<>();
        if (StringUtils.isBlank(scopeText)) {
            return ret;
        }

        final String[] scopes = scopeText.split("[,\\s]+");
        for (final String scope : scopes) {
            final String normalized = StringUtils.trim(scope);
            if (ALL_SCOPES.contains(normalized)) {
                ret.add(normalized);
            }
        }

        return ret;
    }

    private Set<String> parseGrantedScopes(final Request request) {
        final Set<String> ret = new LinkedHashSet<>();
        for (final String scope : ALL_SCOPES) {
            if (StringUtils.isNotBlank(request.getParameter(FISHPI_SCOPE_KEY + "." + scope))) {
                ret.add(scope);
            }
        }

        return ret;
    }

    private List<Map<String, Object>> buildScopeView(final Set<String> requestedScopes) {
        final List<Map<String, Object>> ret = new ArrayList<>();
        for (final ScopeDefinition definition : SCOPE_DEFINITIONS) {
            final Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", definition.key);
            item.put("label", definition.label);
            item.put("requested", requestedScopes.contains(definition.key));
            ret.add(item);
        }

        return ret;
    }

    private JSONArray buildPointRecords(final Object pointsValue) {
        final JSONArray records = new JSONArray();
        if (pointsValue instanceof JSONArray) {
            final JSONArray points = (JSONArray) pointsValue;
            for (int i = 0; i < points.length(); i++) {
                appendPointRecord(records, points.opt(i));
            }
            return records;
        }

        if (pointsValue instanceof List) {
            for (final Object point : (List<?>) pointsValue) {
                appendPointRecord(records, point);
            }
        }

        return records;
    }

    private void appendPointRecord(final JSONArray records, final Object item) {
        if (!(item instanceof JSONObject)) {
            return;
        }

        final JSONObject point = (JSONObject) item;
        records.put(new JSONObject()
                .put(Keys.OBJECT_ID, point.optString(Keys.OBJECT_ID))
                .put(Pointtransfer.FROM_ID, point.optString(Pointtransfer.FROM_ID))
                .put(Pointtransfer.TO_ID, point.optString(Pointtransfer.TO_ID))
                .put(Pointtransfer.SUM, point.optInt(Pointtransfer.SUM))
                .put(Pointtransfer.TYPE, point.optInt(Pointtransfer.TYPE))
                .put(Pointtransfer.TIME, point.optLong(Pointtransfer.TIME))
                .put(Pointtransfer.DATA_ID, point.optString(Pointtransfer.DATA_ID))
                .put(Pointtransfer.MEMO, point.optString(Pointtransfer.MEMO))
                .put(Common.OPERATION, point.optString(Common.OPERATION))
                .put(Common.BALANCE, point.optInt(Common.BALANCE))
                .put(Common.DISPLAY_TYPE, point.optString(Common.DISPLAY_TYPE))
                .put(Common.DESCRIPTION, point.optString(Common.DESCRIPTION)));
    }

    private JSONArray buildArticleRecords(final List<JSONObject> articles) {
        final JSONArray records = new JSONArray();
        for (final JSONObject article : articles) {
            records.put(new JSONObject()
                    .put(Keys.OBJECT_ID, article.optString(Keys.OBJECT_ID))
                    .put(Article.ARTICLE_TITLE, article.optString(Article.ARTICLE_TITLE))
                    .put(Article.ARTICLE_PERMALINK, article.optString(Article.ARTICLE_PERMALINK))
                    .put(Article.ARTICLE_TAGS, article.optString(Article.ARTICLE_TAGS))
                    .put(Article.ARTICLE_CREATE_TIME, article.optLong(Article.ARTICLE_CREATE_TIME))
                    .put(Article.ARTICLE_UPDATE_TIME, article.optLong(Article.ARTICLE_UPDATE_TIME))
                    .put(Article.ARTICLE_COMMENT_CNT, article.optInt(Article.ARTICLE_COMMENT_CNT))
                    .put(Article.ARTICLE_VIEW_CNT, article.optInt(Article.ARTICLE_VIEW_CNT))
                    .put(Article.ARTICLE_TYPE, article.optInt(Article.ARTICLE_TYPE))
                    .put(Article.ARTICLE_PERFECT, article.optInt(Article.ARTICLE_PERFECT)));
        }

        return records;
    }

    private JSONObject buildPagination(final int pageNum, final int pageSize, final int recordCount, final int windowSize) {
        final int pageCount = (int) Math.ceil(recordCount / (double) pageSize);

        return new JSONObject()
                .put(Pagination.PAGINATION_CURRENT_PAGE_NUM, pageNum)
                .put(Pagination.PAGINATION_PAGE_SIZE, pageSize)
                .put(Pagination.PAGINATION_RECORD_COUNT, recordCount)
                .put(Pagination.PAGINATION_PAGE_COUNT, pageCount)
                .put(Pagination.PAGINATION_PAGE_NUMS, Paginator.paginate(pageNum, pageSize, pageCount, windowSize));
    }

    private int getPositiveIntParam(final RequestContext context, final String key, final int defaultValue) {
        final String value = StringUtils.trim(context.param(key));
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }

        try {
            final int ret = Integer.parseInt(value);
            return ret > 0 ? ret : defaultValue;
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }

    private void renderSucc(final RequestContext context, final JSONObject data) {
        context.renderJSON(new JSONObject()
                .put(Keys.CODE, StatusCodes.SUCC)
                .put(Keys.MSG, "")
                .put(Common.DATA, data));
    }

    private void renderAccessDenied(final RequestContext context) {
        renderError(context, "无权限");
    }

    private void renderError(final RequestContext context, final String msg) {
        context.renderJSON(new JSONObject()
                .put(Keys.CODE, StatusCodes.ERR)
                .put(Keys.MSG, msg)
                .put(Common.DATA, new JSONObject()));
    }

    private static final class ScopeDefinition {
        private final String key;
        private final String label;

        private ScopeDefinition(final String key, final String label) {
            this.key = key;
            this.label = label;
        }
    }

    private static final class AuthRequest {
        private final String returnTo;
        private final String realm;
        private final Set<String> requestedScopes;
        private final long createdAt = System.currentTimeMillis();

        private AuthRequest(final String ns, final String mode, final String returnTo, final String realm,
                            final String identity, final String claimedId, final Set<String> requestedScopes) {
            this.returnTo = returnTo;
            this.realm = realm;
            this.requestedScopes = new LinkedHashSet<>(requestedScopes);
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - createdAt > OPENID_TMP_EXPIRES;
        }
    }

    private static final class AuthResult {
        private final String userId;
        private final String realm;
        private final Set<String> scopes;

        private AuthResult(final String userId, final String realm, final Set<String> scopes) {
            this.userId = userId;
            this.realm = realm;
            this.scopes = new LinkedHashSet<>(scopes);
        }
    }

    // 清理过期的nonce
    private static void clearExpiredNonce(){
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();
        synchronized (respNonceMap) {
            for (String nonce : respNonceMap.keySet()) {
                try {
                    Date nonceTime = OpenIdUtil.extractNonceTimestamp(nonce);
                    long delta = Math.abs(now - nonceTime.getTime());
                    if (delta > OPENID_TMP_EXPIRES) {
                        toRemove.add(nonce);
                    }
                } catch (Exception e) {
                    toRemove.add(nonce);
                }
            }
            for (String nonce : toRemove) {
                respNonceMap.remove(nonce);
            }
        }

        synchronized (authRequestMap) {
            final List<String> requestIds = new ArrayList<>();
            for (Map.Entry<String, AuthRequest> entry : authRequestMap.entrySet()) {
                if (entry.getValue().isExpired()) {
                    requestIds.add(entry.getKey());
                }
            }
            for (final String requestId : requestIds) {
                authRequestMap.remove(requestId);
            }
        }
    }

}
