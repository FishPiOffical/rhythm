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
import org.b3log.symphony.censor.CensorResult;
import org.b3log.symphony.censor.TextCensor;
import org.b3log.symphony.util.QiniuTextCensor;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 文本内容审核实现
 * 使用 AIProviderFactory 调用 AI 模型进行审核
 */
public class AITextCensor implements TextCensor {

    private static final Logger LOGGER = LogManager.getLogger(AITextCensor.class);

    /**
     * 审核结果缓存（LRU）
     */
    private static final Map<String, CensorResult> cache = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 2000;
        }
    });

    /**
     * 系统提示词（从配置文件读取，支持热修改）
     */
    public static String getSystemPrompt() {
        String prompt = Symphonys.get("censor.ai.text.prompt");
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
            你是一个内容审核助手。请审核以下文本内容，检查是否包含：
            - 政治敏感内容（涉政、敏感人物、敏感事件、敏感国家）
            - 色情内容（裸露、性暗示等）
            - 暴恐内容（暴力、血腥、恐怖等）
            - 违禁品信息（毒品、武器等）
            - 赌博、诈骗等任何非法信息
            - 其它违法内容
            - 垃圾广告（二维码广告、营销内容等）
            - 辱骂

            审核规则：
            1. 如果内容正常，返回 action 为 "pass"
            2. 如果内容违规，返回 action 为 "block"，并指出违规类型和违规词汇
            3. 如果内容存疑但不确定，返回 action 为 "review"

            直接返回纯JSON，不要使用markdown代码块，不要添加任何解释文字。
            {"action":"pass/block/review","type":"违规类型","bannedWords":["违规词1","违规词2"]}
            """;

    @Override
    public CensorResult censor(String text) {
        if (text == null || text.isEmpty()) {
            return CensorResult.pass();
        }

        // 检查缓存
        String md5 = convertToMD5(text);
        if (md5 != null && cache.containsKey(md5)) {
            return cache.get(md5);
        }

        CensorResult result = null;

        try {
            // 调用 AI 审核（从配置文件读取 prompt，支持热修改）
            String response = AIProviderFactory.chatSync(getSystemPrompt(), text);
            result = CensorResult.fromAIResponse(response);

            // 打印调试信息
            System.out.println("[AI文本审核] 输入: " + (text.length() > 100 ? text.substring(0, 100) + "..." : text));
            System.out.println("[AI文本审核] AI原始响应: " + response);
            System.out.println("[AI文本审核] 解析结果: " + result);
            if (result != null && result.getAnalysis() != null && !result.getAnalysis().isEmpty()) {
                System.out.println("[AI文本审核] 内容分析: " + result.getAnalysis());
            }

            if (result == null) {
                // AI 返回格式不正确，记录日志
                LOGGER.warn("AI text censor returned invalid JSON, falling back to Qiniu. Response: {}", response);
                System.out.println("[AI文本审核] JSON格式无效，fallback到七牛审核");
            }
        } catch (Exception e) {
            LOGGER.error("AI text censor error, falling back to Qiniu", e);
            System.out.println("[AI文本审核] 异常: " + e.getMessage() + "，fallback到七牛审核");
        }

        // 如果 AI 审核失败，fallback 到七牛审核
        if (result == null) {
            result = fallbackToQiniu(text);
        }

        // 缓存结果
        if (md5 != null && result != null) {
            cache.put(md5, result);
        }

        LOGGER.debug("Text censor result: {}", result);
        return result != null ? result : CensorResult.pass();
    }

    /**
     * Fallback 到七牛文本审核
     */
    private CensorResult fallbackToQiniu(String text) {
        try {
            JSONObject qiniuResult = QiniuTextCensor.censor(text);
            CensorResult result = CensorResult.fromQiniuResult(qiniuResult);
            LOGGER.info("Qiniu text censor fallback result: {}", result);
            return result;
        } catch (Exception e) {
            LOGGER.error("Qiniu text censor fallback error", e);
            return CensorResult.pass();
        }
    }

    /**
     * 计算 MD5 哈希
     */
    private String convertToMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] inputBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : inputBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("MD5 calculation error", e);
            return null;
        }
    }
}
