package org.b3log.symphony.processor;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.http.Cookie;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.b3log.symphony.util.ApiV3RefreshCookies;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3TokenMgmtService;
import org.b3log.symphony.service.ApiV3PublicColumnQueryService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.b3log.latke.util.Requests;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.util.UUID;
import java.util.Set;

/** Versioned public Web API. It does not delegate authentication to legacy API handlers. */
@Singleton
public class ApiV3Processor {
    private static final Logger LOGGER = LogManager.getLogger(ApiV3Processor.class);
    public static final String IDENTITY = "apiV3Identity";
    private static final String REFRESH_COOKIE = ApiV3RefreshCookies.NAME;
    private static final int MAX_LOGIN_FIELD = 256;
    private static final int MAX_AUTH_BODY_BYTES = 4096;

    @Inject
    private ApiV3TokenMgmtService apiV3TokenMgmtService;
    @Inject
    private ApiV3PublicColumnQueryService publicColumns;
    @Inject
    private LoginProcessor loginProcessor;

    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3Processor processor = beans.getReference(ApiV3Processor.class);
        ApiV3DiscoveryExtrasProcessor.register();
        ApiV3ContentProcessor.register();
        ApiV3ArticleWriteProcessor.register();
        ApiV3CommentProcessor.register();
        ApiV3CommentRewardProcessor.register();
        ApiV3MarkdownProcessor.register();
        ApiV3VoteProcessor.register();
        ApiV3ArticleRewardProcessor.register();
        ApiV3ArticleStickProcessor.register();
        ApiV3ArticleReadProcessor.register();
        ApiV3ParagraphProcessor.register();
        ApiV3DiscoveryProcessor.register();
        ApiV3UserSuggestionProcessor.register();
        ApiV3TagSuggestionProcessor.register();
        ApiV3PersonalProcessor.register();
        ApiV3DraftProcessor.register();
        ApiV3NotificationProcessor.register();
        ApiV3CommunityProcessor.register();
        ApiV3RankingProcessor.register();
        ApiV3FollowProcessor.register();
        ApiV3BreezemoonProcessor.register();
        ApiV3ReactionProcessor.register();
        ApiV3ChatProcessor.register();
        ApiV3ChatMuteProcessor.register();
        ApiV3PrivateMessageProcessor.register();
        ApiV3FishGameProcessor.register();
        ApiV3FishGameSubmissionProcessor.register();
        ApiV3ColumnProcessor.register();
        ApiV3ActivityProcessor.register();
        ApiV3BagProcessor.register();
        ApiV3UploadProcessor.register();
        ApiV3RealtimeProcessor.register();
        ApiV3ArticleHeatProcessor.register();
        ApiV3MilestoneProcessor.register();
        ApiV3SettingsProcessor.register();
        ApiV3ProfileSettingsProcessor.register();
        ApiV3UserNameProcessor.register();
        ApiV3PostExportProcessor.register();
        ApiV3IdentityApplicationProcessor.register();
        ApiV3SecurityProcessor.register();
        ApiV3MfaProcessor.register();
        ApiV3ContactSettingsProcessor.register();
        ApiV3EmotionProcessor.register();
        ApiV3EmotionSettingsProcessor.register();
        ApiV3RepeaterProcessor.register();
        ApiV3RepeaterPublicationProcessor.register();
        ApiV3RepeaterLikeProcessor.register();
        ApiV3ReportProcessor.register();
        ApiV3PointTransferProcessor.register();
        ApiV3RevisionProcessor.register();
        ApiV3EmojiGroupProcessor.register();
        ApiV3EmojiItemProcessor.register();
        ApiV3EmojiTransferProcessor.register();
        ApiV3RedPacketProcessor.register();
        ApiV3ChatRedPacketProcessor.register();
        ApiV3ProfessionProcessor.register();
        ApiV3ProfessionPrimaryProcessor.register();
        ApiV3ProfessionSettingsProcessor.register();
        ApiV3ProfessionDetailProcessor.register();
        ApiV3ProfessionGuideProcessor.register();
        ApiV3ProfessionRankingProcessor.register();
        ApiV3ProfessionPublicProcessor.register();
        ApiV3MedalProcessor.register();
        ApiV3MedalImageProcessor.register();
        ApiV3MedalDisplayProcessor.register();
        ApiV3MedalOrderProcessor.register();
        ApiV3MembershipProcessor.register();
        ApiV3MembershipStyleProcessor.register();
        ApiV3MembershipSettingsProcessor.register();
        ApiV3MembershipOpeningProcessor.register();
        ApiV3DonationProcessor.register();
        ApiV3OAuthProcessor.register();
        ApiV3AppLoginProcessor.register();
        ApiV3GameSaveProcessor.register();
        ApiV3ClientManagementProcessor.register();
        ApiV3ClientDisableProcessor.register();
        ApiV3ClientUpdateProcessor.register();
        ApiV3InvitationProcessor.register();
        ApiV3InvitationPurchaseProcessor.register();
        ApiV3AnonymousContentProcessor.register();
        ApiV3AccountProcessor.register();
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final AnonymousViewCheckMidware anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.post("/api/v3/auth/login", processor::login, anonymous::handle);
        Dispatcher.post("/api/v3/auth/refresh", processor::refresh, anonymous::handle);
        Dispatcher.post("/api/v3/auth/logout", processor::logout, anonymous::handle);
        Dispatcher.get("/api/v3/me", processor::me, auth::handle);
        Dispatcher.get("/api/v3/columns", processor::columns, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/columns/hot", processor::hotColumns, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/columns/{columnId}/articles", processor::columnArticles, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/columns/{columnId}", processor::column, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/articles/{articleId}/column", processor::articleColumn, auth::optionalContent, anonymous::handle);
    }

    public void login(final RequestContext context) {
        final JSONObject body = authenticationBody(context, Set.of("nameOrEmail", User.USER_PASSWORD, "mfaCode"), false);
        if (body == null) return;
        final String nameOrEmail = StringUtils.trimToEmpty(body.optString("nameOrEmail"));
        final String password = StringUtils.trimToEmpty(body.optString(User.USER_PASSWORD));
        if (nameOrEmail.length() > MAX_LOGIN_FIELD || password.length() != 32 || !password.matches("[a-f0-9]{32}")) {
            error(context, 422, "invalid_request", "请求参数不合法");
            return;
        }
        try {
            final String mfaCode = StringUtils.trimToEmpty(body.optString("mfaCode"));
            if (!mfaCode.matches("\\d{6,8}") && !mfaCode.isEmpty()) {
                error(context, 422, "invalid_request", "两步验证码格式不合法");
                return;
            }
            final LoginProcessor.LoginResult result = loginProcessor.authenticate(nameOrEmail, password, mfaCode,
                    Requests.getRemoteAddr(context.getRequest()));
            if (!result.success()) {
                final int status = "mfa_required".equals(result.code()) || "invalid_mfa".equals(result.code())
                        || "invalid_credentials".equals(result.code()) || "too_many_attempts".equals(result.code()) ? 401 : 403;
                error(context, status, result.code(), result.message());
                audit(context, "login", result.code(), null);
                return;
            }
            final JSONObject token = apiV3TokenMgmtService.issue(result.user().optString(Keys.OBJECT_ID));
            setRefreshCookie(context, token.optString("refreshToken"), token.optLong("refreshExpiresIn"));
            token.remove("refreshToken");
            audit(context, "login", "success", result.user().optString(Keys.OBJECT_ID));
            success(context, token);
        } catch (final ServiceException e) {
            audit(context, "login", "internal_error", null);
            error(context, 500, "internal_error", "登录服务暂不可用");
        }
    }

    public void refresh(final RequestContext context) {
        if (!isSameOrigin(context)) {
            error(context, 403, "forbidden", "请求来源不合法");
            return;
        }
        if (authenticationBody(context, Set.of(), true) == null) return;
        final String refreshToken = cookie(context, REFRESH_COOKIE);
        try {
            final JSONObject token = apiV3TokenMgmtService.refresh(refreshToken);
            setRefreshCookie(context, token.optString("refreshToken"), token.optLong("refreshExpiresIn"));
            token.remove("refreshToken");
            audit(context, "refresh", "success", null);
            success(context, token);
        } catch (final ServiceException e) {
            audit(context, "refresh", "unauthorized", null);
            clearRefreshCookie(context);
            error(context, 401, "unauthorized", "登录状态已失效");
        }
    }

    public void logout(final RequestContext context) {
        if (!isSameOrigin(context)) {
            error(context, 403, "forbidden", "请求来源不合法");
            return;
        }
        if (authenticationBody(context, Set.of(), true) == null) return;
        try {
            apiV3TokenMgmtService.revokeRefresh(cookie(context, REFRESH_COOKIE));
            audit(context, "logout", "success", null);
        } catch (final ServiceException ignored) {
            audit(context, "logout", "persistence_error", null);
            // Logout remains idempotent even if persistence is temporarily unavailable.
        }
        clearRefreshCookie(context);
        success(context, new JSONObject());
    }

    private static JSONObject authenticationBody(final RequestContext context, final Set<String> fields, final boolean emptyAllowed) {
        try {
            final JSONObject body = ApiV3WriteInputs.body(context, fields, emptyAllowed, MAX_AUTH_BODY_BYTES);
            for (final String field : body.keySet()) {
                if (!(body.opt(field) instanceof String)) throw ApiV3WriteInputs.invalid();
            }
            return body;
        } catch (final ApiV3Exception e) {
            error(context, e.status, e.code, e.getMessage());
            return null;
        }
    }

    public void me(final RequestContext context) {
        final JSONObject user = (JSONObject) context.attr(User.USER);
        final JSONObject identity = (JSONObject) context.attr(IDENTITY);
        if (!ApiV3TokenMgmtService.hasScope(identity.optString("scope"), "profile.read")) {
            error(context, 403, "insufficient_scope", "缺少所需权限");
            return;
        }
        context.getResponse().setHeader("Cache-Control", "no-store");
        success(context, userDto(user, true));
    }

    public void columns(final RequestContext context) {
        ApiV3Requests.read(context, () -> publicColumns.list(ApiV3Requests.viewer(context), page(context, 1), pageSize(context, 12), false));
    }
    public void hotColumns(final RequestContext context) {
        ApiV3Requests.read(context, () -> publicColumns.list(ApiV3Requests.viewer(context), page(context, 1), pageSize(context, 12), true));
    }
    public void column(final RequestContext context) {
        ApiV3Requests.read(context, () -> publicColumns.detail(ApiV3Requests.viewer(context), columnId(context)));
    }
    public void columnArticles(final RequestContext context) {
        ApiV3Requests.read(context, () -> publicColumns.chapters(ApiV3Requests.viewer(context), columnId(context), page(context, 1), pageSize(context, 20)));
    }
    public void articleColumn(final RequestContext context) {
        ApiV3Requests.read(context, () -> publicColumns.articleContext(ApiV3Requests.viewer(context), ApiV3Requests.id(context, "articleId")));
    }

    private static int pageSize(final RequestContext context, final int defaultSize) {
        int size = defaultSize;
        try { size = Integer.parseInt(context.param("size")); } catch (final Exception ignored) { }
        return Math.max(1, Math.min(size, 50));
    }

    private static int page(final RequestContext context, final int defaultPage) {
        int p = defaultPage;
        try { p = Integer.parseInt(context.param("p")); } catch (final Exception ignored) { }
        return Math.max(1, p);
    }

    private static String columnId(final RequestContext context) {
        final String columnId = StringUtils.trimToEmpty(context.pathVar("columnId"));
        return columnId.matches("[A-Za-z0-9_-]{1,64}") ? columnId : null;
    }

    public static void success(final RequestContext context, final Object data) {
        context.getResponse().setContentType("application/json; charset=UTF-8");
        context.getResponse().setHeader("X-Request-Id", requestId(context));
        context.renderJSON(new JSONObject().put("code", 0).put("msg", "").put("data", data));
    }

    public static void error(final RequestContext context, final int status, final String code, final String message) {
        context.getResponse().setContentType("application/json; charset=UTF-8");
        context.getResponse().setHeader("X-Request-Id", requestId(context));
        context.getResponse().setHeader("Cache-Control", "no-store");
        context.setStatus(status);
        context.renderJSON(new JSONObject().put("code", code).put("msg", message).put("data", JSONObject.NULL));
    }

    public static String requestId(final RequestContext context) {
        String id = (String) context.attr("apiV3RequestId");
        if (null == id) {
            id = UUID.randomUUID().toString().replace("-", "");
            context.attr("apiV3RequestId", id);
        }
        return id;
    }

    private JSONObject userDto(final JSONObject user, final boolean self) {
        final JSONObject dto = new JSONObject().put("id", user.optString(Keys.OBJECT_ID))
                .put("userName", user.optString(User.USER_NAME)).put("userNickname", user.optString(UserExt.USER_NICKNAME))
                .put("userAvatarURL", user.optString(UserExt.USER_AVATAR_URL)).put("userIntro", user.optString(UserExt.USER_INTRO))
                .put("userURL", user.optString(User.USER_URL)).put("userPoint", user.optInt(UserExt.USER_POINT));
        if (self) dto.put("userGuideStep", user.optInt(UserExt.USER_GUIDE_STEP));
        return dto;
    }

    private static String cookie(final RequestContext context, final String name) {
        for (final Cookie cookie : context.getRequest().getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return "";
    }

    private static boolean isSameOrigin(final RequestContext context) {
        final String origin = StringUtils.trimToEmpty(context.header("Origin"));
        final String referer = StringUtils.trimToEmpty(context.header("Referer"));
        final String host = StringUtils.trimToEmpty(context.header("Host"));
        if (StringUtils.isBlank(host)) return false;
        if (StringUtils.isNotBlank(origin)) return matchesHost(origin, host, false);
        return StringUtils.isNotBlank(referer) && matchesHost(referer, host, true);
    }

    private static void audit(final RequestContext context, final String action, final String result, final String userId) {
        LOGGER.info("api_v3_auth action={} result={} requestId={} userId={} remote={}", action, result,
                requestId(context), StringUtils.defaultString(userId, "-"),
                StringUtils.defaultString(Requests.getRemoteAddr(context.getRequest()), "-"));
    }

    private static boolean matchesHost(final String value, final String host, final boolean allowPath) {
        try {
            final URI uri = new URI(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return false;
            final String authority = uri.getRawAuthority();
            if (!host.equalsIgnoreCase(authority)) return false;
            return !allowPath || StringUtils.isNotBlank(uri.getRawPath());
        } catch (final Exception e) {
            return false;
        }
    }

    private static void setRefreshCookie(final RequestContext context, final String value, final long maxAgeSeconds) {
        context.getResponse().addCookie(ApiV3RefreshCookies.create(value, maxAgeSeconds));
    }

    private static void clearRefreshCookie(final RequestContext context) {
        context.getResponse().addCookie(ApiV3RefreshCookies.create("", 0));
    }
}
