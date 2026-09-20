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

import org.b3log.latke.Keys;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.service.MedalBatchService;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 新版勋章批量接口。
 */
@Singleton
public class MedalBatchProcessor {

    private static final int MAX_USER_COUNT = 100;
    private static final int MAX_USER_ID_LENGTH = 19;
    private static final int MAX_MEDAL_ID_LENGTH = 128;
    private static final int MAX_DATA_LENGTH = 128;

    @Inject
    private MedalApiAuth medalApiAuth;

    @Inject
    private MedalBatchService medalBatchService;

    public static void register() {
        final MedalBatchProcessor processor = BeanManager.getInstance().getReference(MedalBatchProcessor.class);
        Dispatcher.post("/api/medal/admin/holds", processor::getHoldStatuses,
                processor.medalApiAuth::handleAdmin);
        Dispatcher.post("/api/medal/admin/grant-batch", processor::grantBatch,
                processor.medalApiAuth::handleAdmin);
    }

    public void getHoldStatuses(final RequestContext context) {
        if (null == medalApiAuth.requireAdmin(context)) {
            return;
        }
        try {
            final JSONObject request = request(context);
            final List<String> userIds = userIds(request);
            final String medalId = requiredText(request, "medalId", MAX_MEDAL_ID_LENGTH);
            success(context, new JSONArray(medalBatchService.getHoldStatuses(userIds, medalId)));
        } catch (final IllegalArgumentException | ServiceException e) {
            error(context, e.getMessage());
        }
    }

    public void grantBatch(final RequestContext context) {
        if (null == medalApiAuth.requireAdmin(context)) {
            return;
        }
        try {
            final JSONObject request = request(context);
            final List<String> userIds = userIds(request);
            final String medalId = requiredText(request, "medalId", MAX_MEDAL_ID_LENGTH);
            final long expireTime = expireTime(request);
            final String data = optionalString(request, "data", MAX_DATA_LENGTH);
            medalBatchService.grantBatch(new MedalBatchService.GrantRequest(
                    userIds, new MedalBatchService.MedalGrant(medalId, expireTime, data)));
            success(context, new JSONObject().put("grantedCount", userIds.size()));
        } catch (final IllegalArgumentException | ServiceException e) {
            error(context, e.getMessage());
        }
    }

    private JSONObject request(final RequestContext context) {
        final JSONObject request = context.requestJSON();
        if (null == request) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return request;
    }

    private List<String> userIds(final JSONObject request) {
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        if (request.has("userId")) {
            result.add(validateUserId(stringValue(request.opt("userId"), "userId")));
        }
        final JSONArray values = request.optJSONArray("userIds");
        if (request.has("userIds") && null == values) {
            throw new IllegalArgumentException("userIds 必须是数组");
        }
        if (null != values) {
            for (int index = 0; index < values.length(); index++) {
                result.add(validateUserId(stringValue(values.opt(index), "userIds")));
            }
        }
        if (result.isEmpty() || result.size() > MAX_USER_COUNT) {
            throw new IllegalArgumentException("用户数量必须在 1 到 " + MAX_USER_COUNT + " 之间");
        }
        return new ArrayList<>(result);
    }

    private String validateUserId(final String userId) {
        if (userId.isEmpty() || userId.length() > MAX_USER_ID_LENGTH
                || !userId.chars().allMatch(character -> character >= '0' && character <= '9')) {
            throw new IllegalArgumentException("用户 ID 格式错误");
        }
        return userId;
    }

    private String requiredText(final JSONObject request, final String key, final int maxLength) {
        final String value = limitedText(stringValue(request.opt(key), key), maxLength, key);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return value;
    }

    private String stringValue(final Object value, final String key) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(key + " 必须是字符串");
        }
        return ((String) value).trim();
    }

    private String limitedText(final String value, final int maxLength, final String key) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(key + " 不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private String optionalString(final JSONObject request, final String key, final int maxLength) {
        if (!request.has(key)) {
            return "";
        }
        return limitedText(stringValue(request.opt(key), key), maxLength, key);
    }

    private long expireTime(final JSONObject request) {
        if (!request.has("expireTime")) {
            return 0L;
        }
        final Object rawValue = request.opt("expireTime");
        if (!(rawValue instanceof Byte || rawValue instanceof Short
                || rawValue instanceof Integer || rawValue instanceof Long)) {
            throw new IllegalArgumentException("expireTime 必须是毫秒时间戳");
        }
        final long value = ((Number) rawValue).longValue();
        if (value < 0L || value > 0L && value <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("expireTime 必须为 0 或未来的毫秒时间戳");
        }
        return value;
    }

    private void success(final RequestContext context, final Object data) {
        context.renderJSON(new JSONObject()
                .put(Keys.CODE, StatusCodes.SUCC)
                .put(Keys.MSG, "")
                .put(Keys.DATA, data));
    }

    private void error(final RequestContext context, final String message) {
        context.renderJSON(new JSONObject()
                .put(Keys.CODE, StatusCodes.ERR)
                .put(Keys.MSG, message));
    }
}
