package org.b3log.symphony.processor.middleware;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.User;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;
import pers.adlered.simplecurrentlimiter.main.SimpleCurrentLimiter;

/** 鱼游写入接口的 CSRF 与频率校验。 */
@Singleton
public class FishGameWriteSecurityMidware {
    private static final SimpleCurrentLimiter SUBMISSION_LIMITER = new SimpleCurrentLimiter(60 * 30, 10);
    private static final SimpleCurrentLimiter COMMENT_LIMITER = new SimpleCurrentLimiter(60 * 10, 5);
    private static final SimpleCurrentLimiter VOTE_LIMITER = new SimpleCurrentLimiter(60, 10);
    private static final String RATE_LIMIT_MESSAGE = "操作过于频繁，请稍候重试。";

    @Inject
    private CSRFMidware csrfMidware;

    public void checkWriteRequest(final RequestContext context) {
        final String apiKey = requestApiKey(context);
        if (StringUtils.isBlank(apiKey)) {
            csrfMidware.check(context);
            return;
        }
        try {
            ApiProcessor.getUserByKey(apiKey);
            context.handle();
        } catch (final NullPointerException e) {
            context.sendError(401);
            context.abort();
        }
    }

    public void checkSubmissionLimit(final RequestContext context) {
        checkRateLimit(SUBMISSION_LIMITER, context);
    }

    public void checkCommentLimit(final RequestContext context) {
        checkRateLimit(COMMENT_LIMITER, context);
    }

    public void checkVoteLimit(final RequestContext context) {
        checkRateLimit(VOTE_LIMITER, context);
    }

    private String requestApiKey(final RequestContext context) {
        final String parameterKey = context.param("apiKey");
        if (StringUtils.isNotBlank(parameterKey)) {
            return parameterKey;
        }
        return context.requestJSON().optString("apiKey");
    }

    private void checkRateLimit(final SimpleCurrentLimiter limiter, final RequestContext context) {
        final Object user = context.attr(User.USER);
        if (!(user instanceof JSONObject)) {
            context.sendError(401);
            context.abort();
            return;
        }
        final String userId = ((JSONObject) user).optString(Keys.OBJECT_ID);
        if (!limiter.access(userId)) {
            context.renderJSON(new JSONObject().put(Keys.CODE, StatusCodes.ERR).put(Keys.MSG, RATE_LIMIT_MESSAGE));
            context.abort();
            return;
        }
        context.handle();
    }
}
