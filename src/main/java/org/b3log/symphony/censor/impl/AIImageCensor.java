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
package org.b3log.symphony.censor.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.symphony.ai.AIProviderFactory;
import org.b3log.symphony.ai.ModelNotSupportException;
import org.b3log.symphony.ai.OpenAIProvider;
import org.b3log.symphony.ai.Provider;
import org.b3log.symphony.censor.CensorResult;
import org.b3log.symphony.censor.ImageCensor;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 图片内容审核实现
 * 使用支持视觉的 AI 模型进行图片审核（主动审核模式）
 */
public class AIImageCensor implements ImageCensor {

    private static final Logger LOGGER = LogManager.getLogger(AIImageCensor.class);

    /**
     * 审核结果缓存（LRU，最多 500 条，图片 URL 作为 key）
     */
    private static final Map<String, CensorResult> cache = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 500;
        }
    });

    /**
     * 系统提示词（从配置文件读取，支持热修改）
     */
    public static String getSystemPrompt() {
        String prompt = Symphonys.get("censor.ai.image.prompt");
        if (prompt == null || prompt.isEmpty()) {
            // 默认提示词
            return DEFAULT_PROMPT;
        }
        // 将 \\n 转换为真正的换行符
        return prompt.replace("\\n", "\n");
    }

    /**
     * 默认提示词（配置文件未配置时使用）
     */
    private static final String DEFAULT_PROMPT = """
            你是一个图片内容审核助手。请审核图片内容，检查是否包含：
            - 政治敏感内容（涉政、敏感人物、敏感标语等）
            - 色情内容（裸露、性暗示等）
            - 暴恐内容（暴力、血腥、恐怖等）
            - 违禁品信息（毒品、武器等）
            - 垃圾广告（二维码广告、营销内容等）

            审核规则：
            1. 如果图片内容正常，返回 action 为 "pass"
            2. 如果图片内容违规，返回 action 为 "block"，并指出违规类型
            3. 如果图片内容存疑但不确定，返回 action 为 "review"

            直接返回纯JSON，不要使用markdown代码块，不要添加任何解释文字：
            {"action":"pass/block/review","type":"违规类型","bannedWords":["违规描述"],"analysis":"简要描述图片内容和判断理由"}

            违规类型可选值：正常、涉政、色情、暴恐、违禁、广告
            """;

    @Override
    public CensorResult censor(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return CensorResult.pass();
        }

        // 检查缓存
        if (cache.containsKey(imageUrl)) {
            CensorResult cached = cache.get(imageUrl);
            System.out.println("[AI图片审核] 命中缓存: " + imageUrl);
            return cached;
        }

        try {
            // 构建包含图片的消息
            // 从 URL 推断图片的 MIME 类型
            String mimeType = inferMimeType(imageUrl);

            // 从配置文件读取 prompt，支持热修改
            var messages = OpenAIProvider.Message.of(
                    new OpenAIProvider.Message.System(getSystemPrompt()),
                    new OpenAIProvider.Message.User(new Provider.Content.Array(List.of(
                            new Provider.ContentType.Image(imageUrl, mimeType),
                            new Provider.ContentType.Text("请审核这张图片的内容")
                    )))
            );

            // 使用配置的 AI Provider（非流式模式，确保响应完整）
            OpenAIProvider provider = AIProviderFactory.createProviderNoStream(messages);

            // 发送请求并获取响应
            StringBuilder responseBuilder = new StringBuilder();
            AIProviderFactory.send(provider).forEach(json -> {
                String content = extractContent(json);
                responseBuilder.append(content);
            });

            String response = responseBuilder.toString();
            CensorResult result = CensorResult.fromAIResponse(response);

            // 打印调试信息
            System.out.println("[AI图片审核] 图片URL: " + imageUrl);
            System.out.println("[AI图片审核] AI原始响应: " + response);
            System.out.println("[AI图片审核] 解析结果: " + result);
            if (result != null && result.getAnalysis() != null && !result.getAnalysis().isEmpty()) {
                System.out.println("[AI图片审核] 内容分析: " + result.getAnalysis());
            }

            if (result == null) {
                // AI 返回格式不正确，记录日志，默认通过
                LOGGER.warn("AI image censor returned invalid JSON, defaulting to pass. URL: {}, Response: {}", imageUrl, response);
                System.out.println("[AI图片审核] JSON格式无效，默认通过");
                return CensorResult.pass();
            }

            // 缓存结果
            cache.put(imageUrl, result);

            LOGGER.debug("AI image censor result for {}: {}", imageUrl, result);
            return result;

        } catch (ModelNotSupportException e) {
            LOGGER.error("Model not support image censor", e);
            return CensorResult.pass();
        } catch (Exception e) {
            LOGGER.error("AI image censor error for URL: {}", imageUrl, e);
            // 出错时默认通过，避免影响正常使用
            return CensorResult.pass();
        }
    }

    @Override
    public boolean isCallbackMode() {
        return false; // AI 是主动审核模式
    }

    /**
     * 从 URL 推断图片的 MIME 类型
     */
    private String inferMimeType(String imageUrl) {
        String lowerUrl = imageUrl.toLowerCase();
        if (lowerUrl.endsWith(".png")) {
            return "image/png";
        } else if (lowerUrl.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerUrl.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerUrl.endsWith(".bmp")) {
            return "image/bmp";
        }
        // 默认 JPEG
        return "image/jpeg";
    }

    /**
     * 从 JSON 响应中提取内容
     */
    private String extractContent(JSONObject json) {
        try {
            var choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                // 流式响应 (delta)
                var delta = choices.getJSONObject(0).optJSONObject("delta");
                if (delta != null) {
                    return delta.optString("content", "");
                }
                // 非流式响应 (message)
                var message = choices.getJSONObject(0).optJSONObject("message");
                if (message != null) {
                    return message.optString("content", "");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Parse AI response error", e);
        }
        return "";
    }
}
