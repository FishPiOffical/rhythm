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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.censor.CensorFactory;
import org.b3log.symphony.censor.CensorResult;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;

/**
 * AI 内容审核测试接口
 * 提供文本和图片审核的测试能力
 */
@Singleton
public class CensorTestProcessor {

    private static final Logger LOGGER = LogManager.getLogger(CensorTestProcessor.class);

    /**
     * 注册路由
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final CensorTestProcessor processor = beanManager.getReference(CensorTestProcessor.class);

        // 文本审核测试接口
        Dispatcher.get("/api/censor/text", processor::testTextCensor);
        // 图片审核测试接口（URL）
        Dispatcher.get("/api/censor/image", processor::testImageCensor);
    }

    /**
     * 测试文本审核
     * GET /api/censor/text?text=要审核的文本内容
     */
    public void testTextCensor(final RequestContext context) {
        JSONObject result = new JSONObject();

        try {
            String text = context.param("text");

            if (text == null || text.isEmpty()) {
                result.put("code", StatusCodes.ERR);
                result.put("msg", "text 参数不能为空");
                context.renderJSON(result);
                return;
            }

            long startTime = System.currentTimeMillis();
            CensorResult censorResult = CensorFactory.getTextCensor().censor(text);
            long endTime = System.currentTimeMillis();

            result.put("code", StatusCodes.SUCC);
            result.put("msg", "审核完成");
            result.put("data", new JSONObject()
                    .put("action", censorResult.getAction())
                    .put("type", censorResult.getType())
                    .put("doAction", censorResult.getDoAction())
                    .put("bannedWords", censorResult.getBannedWords())
                    .put("analysis", censorResult.getAnalysis())
                    .put("isBlocked", censorResult.isBlocked())
                    .put("costMs", endTime - startTime)
            );

        } catch (Exception e) {
            LOGGER.error("Text censor test error", e);
            result.put("code", StatusCodes.ERR);
            result.put("msg", "审核失败: " + e.getMessage());
        }

        context.renderJSON(result);
    }

    /**
     * 测试图片审核
     * GET /api/censor/image?url=图片URL
     */
    public void testImageCensor(final RequestContext context) {
        JSONObject result = new JSONObject();

        try {
            String imageUrl = context.param("url");

            if (imageUrl == null || imageUrl.isEmpty()) {
                result.put("code", StatusCodes.ERR);
                result.put("msg", "url 参数不能为空");
                context.renderJSON(result);
                return;
            }

            long startTime = System.currentTimeMillis();
            CensorResult censorResult = CensorFactory.getImageCensor().censor(imageUrl);
            long endTime = System.currentTimeMillis();

            result.put("code", StatusCodes.SUCC);
            result.put("msg", "审核完成");
            result.put("data", new JSONObject()
                    .put("imageUrl", imageUrl)
                    .put("action", censorResult.getAction())
                    .put("type", censorResult.getType())
                    .put("doAction", censorResult.getDoAction())
                    .put("bannedWords", censorResult.getBannedWords())
                    .put("analysis", censorResult.getAnalysis())
                    .put("isBlocked", censorResult.isBlocked())
                    .put("costMs", endTime - startTime)
            );

        } catch (Exception e) {
            LOGGER.error("Image censor test error", e);
            result.put("code", StatusCodes.ERR);
            result.put("msg", "审核失败: " + e.getMessage());
        }

        context.renderJSON(result);
    }
}
