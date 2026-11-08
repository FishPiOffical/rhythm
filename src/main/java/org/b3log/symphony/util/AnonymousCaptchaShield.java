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
package org.b3log.symphony.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.cache.UserCache;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.processor.SkinRenderer;
import org.json.JSONObject;

import java.util.Set;

/**
 * 防扫描验证码盾：统一持有进程内状态、首访/周期策略、黑名单跳转和极验验证。
 * 匿名阅读权限、阅读页数、登录鉴权及 CC 限速不属于本类。
 *
 * <p>保护约定：未经阿达明确允许，不得修改、移动本类或其调用位置，
 * 不得删除调用或在调用前增加放行分支。获批修改后须同步版本、指纹和回归记录。
 * 指纹基线见 tools/captcha-shield-lock.json；注释本身不替代部署检查。
 *
 * <p>版本 1 为原逻辑搬迁：保留两小时缓存、首访文章豁免、每五次验证、
 * API 401、非 API 累计封禁及原有非原子计数；不在提取中调整业务规则。
 */
public final class AnonymousCaptchaShield {

    public static final int POLICY_VERSION = 1;

    private static final Logger LOGGER = LogManager.getLogger(AnonymousCaptchaShield.class);

    private AnonymousCaptchaShield() {
    }

    // 访问计数缓存，2 小时过期；窗口必须足够长，否则慢速抓取（间隔超过 5 分钟）会让计数反复归零从而绕过验证码
    private static final Cache<String, Integer> ipVisitCountCache = Caffeine.newBuilder()
            .expireAfterWrite(2, java.util.concurrent.TimeUnit.HOURS)
            .maximumSize(100000)
            .build();
    // 无时间过期；保留原有容量淘汰及进程重启清空行为。
    private static final Cache<String, Boolean> ipBlacklistCache = Caffeine.newBuilder()
            .maximumSize(100000)
            .build();
    // 管理员解封/已有用户 IP 豁免，仅本次进程内有效。
    private static final Set<String> whiteList = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * IP 首次访问时间缓存（用于 2 小时内首次访问需要验证码）
     */
    private static final Cache<String, Long> ipFirstVisitTimeCache = Caffeine.newBuilder()
            .expireAfterWrite(2, java.util.concurrent.TimeUnit.HOURS)
            .maximumSize(100000)
            .build();

    /**
     * IP 最近一次通过验证码的时间（通过后重置计数逻辑时使用）
     */
    private static final Cache<String, Long> ipLastCaptchaPassTimeCache = Caffeine.newBuilder()
            .expireAfterWrite(2, java.util.concurrent.TimeUnit.HOURS)
            .maximumSize(100000)
            .build();

    /**
     * Runtime enable flag for captcha checks (temporary, resets on restart).
     */
    private static volatile boolean enabled = true;

    /**
     * Whether to force captcha on the first visit within 2 hours (temporary, resets on restart).
     */
    private static volatile boolean firstVisitCaptchaEnabled = true;

    // 计数窗口为 2 小时，阈值需相应放大，避免正常用户 2 小时内浏览 100 个页面被误封
    private static final int AUTO_BAN_VISIT_THRESHOLD = 300;

    private static final int CAPTCHA_VISIT_INTERVAL = 5;

    private static final long FIRST_VISIT_CAPTCHA_WINDOW_MILLIS = 2L * 60L * 60L * 1000L;

    public static void setEnabled(final boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setFirstVisitCaptchaEnabled(final boolean value) {
        firstVisitCaptchaEnabled = value;
    }

    public static boolean isFirstVisitCaptchaEnabled() {
        return firstVisitCaptchaEnabled;
    }

    /** 原始黑名单状态，与盾开关无关；验证码页面仍按此状态展示。 */
    public static boolean isAwaitingCaptcha(final String ip) {
        return ipBlacklistCache.getIfPresent(ip) != null;
    }

    /** 实时连接的限制状态；关闭盾只停拦截，不清除原黑名单。 */
    public static boolean isRestricted(final String ip) {
        return enabled && isAwaitingCaptcha(ip);
    }

    /**
     * 全局入口必须在爬虫核实和 CC 检查之后、CORS/OPTIONS 之前调用。
     * 返回 true 表示已跳转并终止；本方法不会继续执行请求处理链。
     */
    public static boolean redirectIfChallenged(final RequestContext context, final boolean verifiedCrawler) {
        final String ip = Requests.getRemoteAddr(context.getRequest());
        if (!verifiedCrawler && isRestricted(ip)
                && !context.requestURI().equals("/test")
                && !context.requestURI().equals("/validateCaptcha")) {
            logShield(ip, context.requestURI(), "跳转", "已在验证黑名单", null);
            context.sendRedirect("/test");
            context.abort();
            SecurityMonitor.record(context, "captcha", "redirect", "黑名单跳转",
                    "BeforeRequestHandler -> AnonymousCaptchaShield");
            return true;
        }
        return false;
    }

    /** 验证成功只清验证码限制，不解除 Firewall/ipset 封禁，也不加入白名单。 */
    private static void recordCaptchaPass(final String ip) {
        ipBlacklistCache.invalidate(ip);
        ipVisitCountCache.invalidate(ip);
        ipLastCaptchaPassTimeCache.put(ip, System.currentTimeMillis());
    }

    /** 在管理员已完成系统解封后调用，保持原清理顺序和本次启动白名单策略。 */
    public static void administratorUnban(final String ip) {
        whiteList.add(ip);
        ipBlacklistCache.invalidate(ip);
        ipVisitCountCache.invalidate(ip);
        ipFirstVisitTimeCache.invalidate(ip);
        ipLastCaptchaPassTimeCache.put(ip, System.currentTimeMillis());
    }

    /** 管理员封禁前撤销验证码豁免，系统封禁仍由 Firewall 负责。 */
    public static void removeWhitelist(final String ip) {
        whiteList.remove(ip);
    }

    private static boolean isPublicInfoPage(final String requestURI) {
        final String contextPath = Latkes.getContextPath();
        return requestURI.equals(contextPath + "/about")
                || requestURI.equals(contextPath + "/download")
                || requestURI.equals(contextPath + "/privacy")
                || requestURI.equals(contextPath + "/agreement");
    }

    /**
     * 输出匿名访问风控决策日志，统一前缀便于检索：{@code [SHIELD]}
     *
     * @param ip      客户端 IP
     * @param uri     请求 URI
     * @param verdict 结果：放行 / 验证码 / 拒绝 / 跳转 / 封禁
     * @param reason  原因说明
     * @param extra   额外信息，可为 {@code null}
     */
    private static void logShield(final String ip, final String uri, final String verdict,
                                  final String reason, final String extra) {
        LOGGER.log(Level.INFO, "[SHIELD] ip={} uri={} 结果={} 原因={}{}",
                ip, uri, verdict, reason, null == extra ? "" : " " + extra);
    }

    /**
     * 在匿名阅读规则之前执行。currentUser/articleId 由原中间件按原逻辑解析，
     * shieldEnabled 是本次进入中间件时的开关快照，避免提取改变读取顺序。
     * 返回 false 表示已拒绝/跳转；true 仅允许继续匿名阅读检查，不代表最终放行。
     */
    public static boolean checkAnonymous(final RequestContext context, final JSONObject currentUser,
                                         final String articleId, final boolean shieldEnabled) {
        final Request request = context.getRequest();
        final String requestURI = context.requestURI();
        final boolean firstVisitArticle = StringUtils.isNotBlank(articleId);
        if (null == currentUser) {
            final String ip = Requests.getRemoteAddr(context.getRequest());
            final String ua = Headers.getHeader(request, Common.USER_AGENT, "");
            final boolean verifiedCrawler = null != SearchEngines.verifiedCrawler(ip, ua);
            // 匿名非蜘蛛：白名单直接放行，其余按「首访 / 每 5 次」判断；通过验证码只豁免首访规则，不豁免次数规则
            if (shieldEnabled && !verifiedCrawler) {
                if (isPublicInfoPage(requestURI)) {
                    logShield(ip, requestURI, "放行", "公开信息页", null);
                } else if (whiteList.contains(ip)) {
                    logShield(ip, requestURI, "放行", "白名单", null);
                } else if (UserCache.hasUserByIP(ip)) {
                    // 该 IP 上有用户登录过，按既定策略整台机器免验证
                    whiteList.add(ip);
                    logShield(ip, requestURI, "放行", "同IP有用户登录", null);
                } else {
                    final long now = System.currentTimeMillis();
                    Long firstVisitTime = ipFirstVisitTimeCache.getIfPresent(ip);
                    if (null == firstVisitTime) {
                        firstVisitTime = now;
                        ipFirstVisitTimeCache.put(ip, firstVisitTime);
                    }

                    final Integer visits = ipVisitCountCache.getIfPresent(ip);
                    final int count = null == visits ? 1 : visits + 1;
                    ipVisitCountCache.put(ip, count);

                    // API 调用方无法通过验证码，不做累计封禁，交给 Firewall 的速率阈值处理
                    if (!requestURI.contains("/api/")
                            && count >= AUTO_BAN_VISIT_THRESHOLD && Firewall.banIpIfAbsent(ip, "匿名累计超限")) {
                        logShield(ip, requestURI, "拒绝", "匿名累计超限", "次数=" + count);
                    }

                    // 2 小时内已通过验证码时，不再按「首访」弹窗，但每 5 次访问仍然需要验证
                    final boolean passedRecently = null != ipLastCaptchaPassTimeCache.getIfPresent(ip);

                    boolean needCaptcha = false;
                    String reason = "";
                    // 非文章页：2 小时内首次访问就需要验证码（可后台开关）；文章页豁免首访验证，便于搜索引擎落地用户直接阅读
                    if (!passedRecently && firstVisitCaptchaEnabled && !firstVisitArticle && count == 1
                            && (now - firstVisitTime) <= FIRST_VISIT_CAPTCHA_WINDOW_MILLIS) {
                        needCaptcha = true;
                        reason = "首次访问";
                    }
                    // 之后每访问 5 次需要一次验证码
                    if (!needCaptcha && count % CAPTCHA_VISIT_INTERVAL == 0) {
                        needCaptcha = true;
                        reason = "访问满5次";
                    }

                    if (needCaptcha) {
                        if (requestURI.contains("/api/")) {
                            // API 调用方无法完成验证码，直接拒绝且不拉黑，避免把服务端客户端永久卡死
                            logShield(ip, requestURI, "拒绝", "接口需验证码-" + reason, "次数=" + count);
                            context.sendError(401);
                            SecurityMonitor.record(context, "captcha", "deny", "接口需要验证码",
                                    "AnonymousViewCheckMidware -> AnonymousCaptchaShield");
                        } else {
                            logShield(ip, requestURI, "验证码", reason, "次数=" + count);
                            ipBlacklistCache.put(ip, true);
                            context.sendRedirect("/test");
                            SecurityMonitor.record(context, "captcha", "challenge", reason,
                                    "AnonymousViewCheckMidware -> AnonymousCaptchaShield");
                        }
                        context.abort();
                        return false;
                    }

                    if (count == 1 && firstVisitArticle) {
                        logShield(ip, requestURI, "放行", "文章页首次访问", "次数=1 文章=" + articleId);
                    } else if (passedRecently) {
                        logShield(ip, requestURI, "放行", "已通过验证码", "次数=" + count);
                    } else {
                        logShield(ip, requestURI, "放行", "正常访问", "次数=" + count);
                    }
                }
            }
        }

        return true;
    }

    /** 页面入口只根据原始验证码黑名单决定展示，保留盾关闭后直接访问 /test 的原行为。 */
    public static void showChallenge(final RequestContext context) {
        final String ip = Requests.getRemoteAddr(context.getRequest());
        // 黑名单判断
        if (isAwaitingCaptcha(ip)) {
            new SkinRenderer(context, "captcha.ftl");
        } else {
            context.sendRedirect("/");
        }
    }

    /** POST /validateCaptcha：供应商验证成功后才清状态，响应 code 与旧入口保持一致。 */
    public static void validateChallenge(final RequestContext context) {
        final JSONObject requestJSONObject = context.requestJSON();
        final String captcha = requestJSONObject.optString("captcha");
        final String ip = Requests.getRemoteAddr(context.getRequest());
        if (verifyProof(captcha)) {
            recordCaptchaPass(ip);
            LOGGER.log(Level.INFO, "[SHIELD] ip={} uri=/validateCaptcha 结果=放行 原因=验证码通过", ip);
            context.renderJSON(StatusCodes.SUCC);
        } else {
            LOGGER.log(Level.INFO, "[SHIELD] ip={} uri=/validateCaptcha 结果=拒绝 原因=验证码错误", ip);
            context.renderJSON(StatusCodes.ERR);
        }
    }

    /** 共用极验验证协议，注册/找回/设置入口也使用；任何解析或供应商异常仍返回失败。 */
    public static boolean verifyProof(final String captcha) {
        try {
            final String captchaId = Symphonys.get("jiyan.id");
            final String captchaKey = Symphonys.get("jiyan.key");
            final String domain = Symphonys.get("jiyan.domain");

            final JSONObject proof = new JSONObject(captcha);
            final String lotNumber = proof.optString("lot_number");
            final String captchaOutput = proof.optString("captcha_output");
            final String passToken = proof.optString("pass_token");
            final String genTime = proof.optString("gen_time");

            final String signToken = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, captchaKey).hmacHex(lotNumber);

            final String url = String.format(domain + "/validate" + "?captcha_id=%s", captchaId);
            final HttpResponse response = HttpRequest.post(url)
                    .form("lot_number", lotNumber)
                    .form("captcha_output", captchaOutput)
                    .form("pass_token", passToken)
                    .form("gen_time", genTime)
                    .form("sign_token", signToken)
                    .charset("UTF-8")
                    .timeout(5000)
                    .send();
            return response.statusCode() == 200
                    && "success".equals(new JSONObject(response.bodyText()).getString("result"));
        } catch (final Exception e) {
            // 保持既有失败语义，不记录验证码材料或供应商响应正文。
            return false;
        }
    }

}
