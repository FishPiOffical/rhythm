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
package org.b3log.symphony.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.symphony.ai.OpenAIProvider.Message;
import org.b3log.symphony.ai.OpenAIProvider.Options;
import org.b3log.symphony.ai.Provider.Authorize;
import org.b3log.symphony.ai.Provider.Content;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

/**
 * AI Provider 工厂类，用于创建和管理 AI Provider 实例。
 * 支持从 symphony.properties 配置文件读取配置。
 */
public final class AIProviderFactory {

    private static final Logger LOGGER = LogManager.getLogger(AIProviderFactory.class);

    /**
     * AI 功能是否启用
     */
    public static final boolean AI_ENABLED = "true".equalsIgnoreCase(Symphonys.get("ai.enabled"));

    /**
     * 默认 Provider 类型
     */
    public static final String DEFAULT_PROVIDER = Symphonys.get("ai.provider");

    // OpenAI 配置
    public static final String OPENAI_API_KEY = Symphonys.get("ai.openai.apiKey");
    public static final String OPENAI_BASE_URL = Symphonys.get("ai.openai.baseUrl");
    public static final String OPENAI_MODEL = Symphonys.get("ai.openai.model");
    public static final int OPENAI_MAX_TOKENS = getInt("ai.openai.maxTokens", 2048);
    public static final boolean OPENAI_STREAM = "true".equalsIgnoreCase(Symphonys.get("ai.openai.stream"));

    // 通义千问配置
    public static final String QWEN_API_KEY = Symphonys.get("ai.qwen.apiKey");
    public static final String QWEN_BASE_URL = Symphonys.get("ai.qwen.baseUrl");
    public static final String QWEN_MODEL = Symphonys.get("ai.qwen.model");
    public static final int QWEN_MAX_TOKENS = getInt("ai.qwen.maxTokens", 2048);
    public static final boolean QWEN_STREAM = "true".equalsIgnoreCase(Symphonys.get("ai.qwen.stream"));
    public static final boolean QWEN_ENABLE_SEARCH = "true".equalsIgnoreCase(Symphonys.get("ai.qwen.enableSearch"));

    // 聊天室 AI 功能配置
    public static final boolean CHATROOM_AI_ENABLED = "true".equalsIgnoreCase(Symphonys.get("ai.chatroom.enabled"));
    public static final boolean CHATROOM_RECAP_ENABLED = "true".equalsIgnoreCase(Symphonys.get("ai.chatroom.recap.enabled"));

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final AIClient AI_CLIENT = new AIClient(HTTP_CLIENT);

    /**
     * Provider 类型枚举
     */
    public enum ProviderType {
        OPENAI, QWEN
    }

    /**
     * 通用模型实现，支持文本和图像
     */
    private static class GenericModel implements Model, Model.Supported.Text, Model.Supported.Image {
        private final String name;

        public GenericModel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    /**
     * 获取默认的 Provider 类型
     */
    public static ProviderType getDefaultProviderType() {
        if ("openai".equalsIgnoreCase(DEFAULT_PROVIDER)) {
            return ProviderType.OPENAI;
        }
        return ProviderType.QWEN;
    }

    /**
     * 使用默认配置创建 Provider
     */
    public static OpenAIProvider createProvider(List<Message> messages) throws ModelNotSupportException {
        return createProvider(getDefaultProviderType(), messages);
    }

    /**
     * 创建指定类型的 Provider
     */
    public static OpenAIProvider createProvider(ProviderType type, List<Message> messages) throws ModelNotSupportException {
        return switch (type) {
            case OPENAI -> createOpenAIProvider(messages);
            case QWEN -> createQwenProvider(messages);
        };
    }

    /**
     * 创建 OpenAI Provider
     */
    public static OpenAIProvider createOpenAIProvider(List<Message> messages) throws ModelNotSupportException {
        return new OpenAIProvider(
                URI.create(OPENAI_BASE_URL),
                new GenericModel(OPENAI_MODEL),
                new Authorize.Token(OPENAI_API_KEY),
                messages,
                new Options.Stream(OPENAI_STREAM, true),
                new Options.MaxTokens(OPENAI_MAX_TOKENS)
        );
    }

    /**
     * 创建通义千问 Provider
     */
    public static OpenAIProvider createQwenProvider(List<Message> messages) throws ModelNotSupportException {
        var options = QWEN_ENABLE_SEARCH
                ? new Options[]{
                    new Options.Stream(QWEN_STREAM, true),
                    new Options.MaxTokens(QWEN_MAX_TOKENS),
                    new Options.EnableSearch(true)
                }
                : new Options[]{
                    new Options.Stream(QWEN_STREAM, true),
                    new Options.MaxTokens(QWEN_MAX_TOKENS)
                };

        return new OpenAIProvider(
                URI.create(QWEN_BASE_URL),
                new GenericModel(QWEN_MODEL),
                new Authorize.Token(QWEN_API_KEY),
                messages,
                options
        );
    }

    /**
     * 创建非流式 Provider（用于审核等需要完整响应的场景）
     */
    public static OpenAIProvider createProviderNoStream(List<Message> messages) throws ModelNotSupportException {
        return createProviderNoStream(getDefaultProviderType(), messages);
    }

    /**
     * 创建指定类型的非流式 Provider
     */
    public static OpenAIProvider createProviderNoStream(ProviderType type, List<Message> messages) throws ModelNotSupportException {
        return switch (type) {
            case OPENAI -> new OpenAIProvider(
                    URI.create(OPENAI_BASE_URL),
                    new GenericModel(OPENAI_MODEL),
                    new Authorize.Token(OPENAI_API_KEY),
                    messages,
                    new Options.Stream(false, true),
                    new Options.MaxTokens(OPENAI_MAX_TOKENS)
            );
            case QWEN -> new OpenAIProvider(
                    URI.create(QWEN_BASE_URL),
                    new GenericModel(QWEN_MODEL),
                    new Authorize.Token(QWEN_API_KEY),
                    messages,
                    new Options.Stream(false, true),
                    new Options.MaxTokens(QWEN_MAX_TOKENS)
            );
        };
    }

    /**
     * 快捷方法：简单文本对话（使用默认 Provider）
     *
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @return 响应流
     */
    public static Stream<JSONObject> chat(String systemPrompt, String userMessage)
            throws ModelNotSupportException, IOException, InterruptedException {
        return chat(getDefaultProviderType(), systemPrompt, userMessage);
    }

    /**
     * 快捷方法：简单文本对话
     *
     * @param type Provider 类型
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @return 响应流
     */
    public static Stream<JSONObject> chat(ProviderType type, String systemPrompt, String userMessage)
            throws ModelNotSupportException, IOException, InterruptedException {
        var messages = Message.of(
                new Message.System(systemPrompt),
                new Message.User(new Content.Text(userMessage))
        );
        var provider = createProvider(type, messages);
        return AI_CLIENT.send(provider);
    }

    /**
     * 快捷方法：仅用户消息对话（无系统提示词）
     *
     * @param userMessage 用户消息
     * @return 响应流
     */
    public static Stream<JSONObject> chat(String userMessage)
            throws ModelNotSupportException, IOException, InterruptedException {
        var messages = Message.of(
                new Message.User(new Content.Text(userMessage))
        );
        var provider = createProvider(messages);
        return AI_CLIENT.send(provider);
    }

    /**
     * 快捷方法：获取完整的文本响应（非流式处理）
     *
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @return 完整响应文本
     */
    public static String chatSync(String systemPrompt, String userMessage)
            throws ModelNotSupportException, IOException, InterruptedException {
        var sb = new StringBuilder();
        chat(systemPrompt, userMessage).forEach(json -> {
            var content = extractContent(json);
            sb.append(content);
        });
        return sb.toString();
    }

    /**
     * 非流式对话（用于审核等需要完整响应的场景）
     * 使用非流式 API 调用，确保响应完整
     *
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @return 完整响应文本
     */
    public static String chatSyncNoStream(String systemPrompt, String userMessage)
            throws ModelNotSupportException, IOException, InterruptedException {
        var messages = Message.of(
                new Message.System(systemPrompt),
                new Message.User(new Content.Text(userMessage))
        );
        var provider = createProviderNoStream(messages);
        var sb = new StringBuilder();
        AI_CLIENT.send(provider).forEach(json -> {
            var content = extractContent(json);
            sb.append(content);
        });
        return sb.toString();
    }

    /**
     * 流式对话回调处理器
     */
    @FunctionalInterface
    public interface StreamHandler {
        /**
         * 处理流式响应的每个 token
         *
         * @param token 当前收到的文本片段
         * @param fullContent 目前累积的完整内容
         */
        void onToken(String token, String fullContent);
    }

    /**
     * 流式对话（带回调）
     *
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @param handler 流式处理器，每收到一个 token 调用一次
     * @return 完整响应文本
     */
    public static String chatStream(String systemPrompt, String userMessage, StreamHandler handler)
            throws ModelNotSupportException, IOException, InterruptedException {
        return chatStream(getDefaultProviderType(), systemPrompt, userMessage, handler);
    }

    /**
     * 流式对话（带回调，指定 Provider）
     *
     * @param type Provider 类型
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @param handler 流式处理器
     * @return 完整响应文本
     */
    public static String chatStream(ProviderType type, String systemPrompt, String userMessage, StreamHandler handler)
            throws ModelNotSupportException, IOException, InterruptedException {
        var sb = new StringBuilder();
        chat(type, systemPrompt, userMessage).forEach(json -> {
            var token = extractContent(json);
            if (!token.isEmpty()) {
                sb.append(token);
                handler.onToken(token, sb.toString());
            }
        });
        return sb.toString();
    }

    /**
     * 流式对话（简化回调，仅处理 token）
     *
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @param onToken 每收到一个 token 时的回调
     * @return 完整响应文本
     */
    public static String chatStream(String systemPrompt, String userMessage, Consumer<String> onToken)
            throws ModelNotSupportException, IOException, InterruptedException {
        return chatStream(systemPrompt, userMessage, (token, full) -> onToken.accept(token));
    }

    /**
     * 流式对话（带完成回调）
     *
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @param onToken 每收到一个 token 时的回调
     * @param onComplete 完成时的回调，参数为完整响应
     */
    public static void chatStream(String systemPrompt, String userMessage,
                                  Consumer<String> onToken, Consumer<String> onComplete)
            throws ModelNotSupportException, IOException, InterruptedException {
        var result = chatStream(systemPrompt, userMessage, onToken);
        onComplete.accept(result);
    }

    /**
     * 流式对话（自定义消息列表）
     *
     * @param messages 消息列表
     * @param handler 流式处理器
     * @return 完整响应文本
     */
    public static String chatStream(List<Message> messages, StreamHandler handler)
            throws ModelNotSupportException, IOException, InterruptedException {
        var sb = new StringBuilder();
        var provider = createProvider(messages);
        AI_CLIENT.send(provider).forEach(json -> {
            var token = extractContent(json);
            if (!token.isEmpty()) {
                sb.append(token);
                handler.onToken(token, sb.toString());
            }
        });
        return sb.toString();
    }

    /**
     * 从 JSON 响应中提取内容
     */
    private static String extractContent(JSONObject json) {
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

    /**
     * 发送请求并返回响应流
     */
    public static Stream<JSONObject> send(OpenAIProvider provider) throws IOException, InterruptedException {
        return AI_CLIENT.send(provider);
    }

    /**
     * 获取 AIClient 实例
     */
    public static AIClient getClient() {
        return AI_CLIENT;
    }

    /**
     * 检查 AI 功能是否可用
     */
    public static boolean isAvailable() {
        if (!AI_ENABLED) {
            return false;
        }
        var type = getDefaultProviderType();
        return switch (type) {
            case OPENAI -> OPENAI_API_KEY != null && !OPENAI_API_KEY.isBlank();
            case QWEN -> QWEN_API_KEY != null && !QWEN_API_KEY.isBlank();
        };
    }

    /**
     * 检查聊天室 AI 功能是否可用
     */
    public static boolean isChatroomAIAvailable() {
        return isAvailable() && CHATROOM_AI_ENABLED;
    }

    /**
     * 检查聊天室回溯功能是否可用
     */
    public static boolean isChatroomRecapAvailable() {
        return isAvailable() && CHATROOM_RECAP_ENABLED;
    }

    /**
     * 获取当前 Provider 的最大 tokens
     */
    public static int getMaxTokens() {
        var type = getDefaultProviderType();
        return switch (type) {
            case OPENAI -> OPENAI_MAX_TOKENS;
            case QWEN -> QWEN_MAX_TOKENS;
        };
    }

    private static int getInt(String key, int defaultValue) {
        var value = Symphonys.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private AIProviderFactory() {
    }
}
