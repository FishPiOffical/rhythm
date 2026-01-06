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
package org.b3log.symphony.censor;

import org.b3log.symphony.ai.AIProviderFactory;
import org.b3log.symphony.ai.OpenAIProvider;
import org.b3log.symphony.ai.Provider;
import org.b3log.symphony.censor.impl.AIImageCensor;
import org.b3log.symphony.censor.impl.AITextCensor;
import org.json.JSONObject;

import java.util.List;
import java.util.Scanner;

/**
 * 审核功能交互式测试类
 * 使用与生产环境相同的 SYSTEM_PROMPT
 */
public class CensorTest {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("========== AI 审核交互式测试 ==========\n");
        System.out.println("使用生产环境相同的 SYSTEM_PROMPT\n");

        while (true) {
            System.out.println("请选择测试类型：");
            System.out.println("1. 文本审核");
            System.out.println("2. 图片审核");
            System.out.println("0. 退出");
            System.out.print("\n请输入选项: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> testTextCensor();
                case "2" -> testImageCensor();
                case "0" -> {
                    System.out.println("退出测试程序");
                    return;
                }
                default -> System.out.println("无效选项，请重新输入\n");
            }
        }
    }

    /**
     * 测试文本审核
     */
    private static void testTextCensor() {
        System.out.println("\n【文本审核测试】");
        System.out.print("请输入要审核的文本内容: ");
        String text = scanner.nextLine();

        if (text.isEmpty()) {
            System.out.println("输入内容为空，取消审核\n");
            return;
        }

        System.out.println("\n正在审核...\n");

        try {
            long startTime = System.currentTimeMillis();
            String a = AITextCensor.getSystemPrompt();
            // 使用 AITextCensor 中相同的 prompt（从配置文件读取）
            String rawResponse = AIProviderFactory.chatSync(AITextCensor.getSystemPrompt(), text);

            long endTime = System.currentTimeMillis();

            System.out.println("=== 审核结果 ===");
            System.out.println("耗时: " + (endTime - startTime) + "ms");
            System.out.println("AI 原始响应: [" + rawResponse + "]");
            System.out.println("响应长度: " + rawResponse.length());

            // 检查是否包含非 JSON 内容
            checkJsonFormat(rawResponse);

            // 解析结果
            CensorResult result = CensorResult.fromAIResponse(rawResponse);
            System.out.println("\n=== 解析结果 ===");
            System.out.println("action: " + result.getAction());
            System.out.println("type: " + result.getType());
            System.out.println("doAction: " + result.getDoAction());
            System.out.println("bannedWords: " + result.getBannedWords());
            System.out.println("是否阻止: " + result.isBlocked());

        } catch (Exception e) {
            System.out.println("错误: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n" + "=".repeat(50) + "\n");
    }

    /**
     * 测试图片审核
     */
    private static void testImageCensor() {
        System.out.println("\n【图片审核测试】");
        System.out.print("请输入图片URL: ");
        String imageUrl = scanner.nextLine().trim();

        if (imageUrl.isEmpty()) {
            System.out.println("输入URL为空，取消审核\n");
            return;
        }

        // 推断 MIME 类型
        String mimeType = inferMimeType(imageUrl);
        System.out.println("推断的 MIME 类型: " + mimeType);
        System.out.println("\n正在审核...\n");

        try {
            long startTime = System.currentTimeMillis();

            // 使用 AIImageCensor 中相同的 prompt（从配置文件读取）
            var messages = OpenAIProvider.Message.of(
                    new OpenAIProvider.Message.System(AIImageCensor.getSystemPrompt()),
                    new OpenAIProvider.Message.User(new Provider.Content.Array(List.of(
                            new Provider.ContentType.Image(imageUrl, mimeType),
                            new Provider.ContentType.Text("请审核这张图片的内容")
                    )))
            );

            // 使用通义千问的视觉模型
            OpenAIProvider provider = AIProviderFactory.createProvider(
                    AIProviderFactory.ProviderType.QWEN,
                    messages
            );

            // 发送请求并收集响应
            StringBuilder responseBuilder = new StringBuilder();
            System.out.println("流式响应:");
            AIProviderFactory.send(provider).forEach(json -> {
                String content = extractContent(json);
                if (!content.isEmpty()) {
                    System.out.print(content);
                    responseBuilder.append(content);
                }
            });

            long endTime = System.currentTimeMillis();

            String rawResponse = responseBuilder.toString();
            System.out.println("\n\n=== 审核结果 ===");
            System.out.println("耗时: " + (endTime - startTime) + "ms");
            System.out.println("AI 原始响应: [" + rawResponse + "]");
            System.out.println("响应长度: " + rawResponse.length());

            // 检查是否包含非 JSON 内容
            checkJsonFormat(rawResponse);

            // 解析结果
            CensorResult result = CensorResult.fromAIResponse(rawResponse);
            System.out.println("\n=== 解析结果 ===");
            System.out.println("action: " + result.getAction());
            System.out.println("type: " + result.getType());
            System.out.println("doAction: " + result.getDoAction());
            System.out.println("bannedWords: " + result.getBannedWords());
            System.out.println("是否阻止: " + result.isBlocked());

        } catch (Exception e) {
            System.out.println("错误: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n" + "=".repeat(50) + "\n");
    }

    /**
     * 检查 JSON 格式
     */
    private static void checkJsonFormat(String response) {
        if (response == null || response.isEmpty()) {
            System.out.println("警告: 响应为空！");
            return;
        }

        String trimmed = response.trim();
        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');

        if (jsonStart < 0 || jsonEnd < 0) {
            System.out.println("警告: 响应中没有找到 JSON 对象！");
            return;
        }

        if (jsonStart > 0) {
            System.out.println("警告: JSON 前有额外内容: [" + trimmed.substring(0, jsonStart) + "]");
        }

        if (jsonEnd < trimmed.length() - 1) {
            System.out.println("警告: JSON 后有额外内容: [" + trimmed.substring(jsonEnd + 1) + "]");
        }

        // 尝试解析 JSON
        try {
            String jsonStr = trimmed.substring(jsonStart, jsonEnd + 1);
            new JSONObject(jsonStr);
            System.out.println("JSON 格式: 有效");
        } catch (Exception e) {
            System.out.println("警告: JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 从 URL 推断图片的 MIME 类型
     */
    private static String inferMimeType(String imageUrl) {
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
        return "image/jpeg";
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
            // ignore
        }
        return "";
    }
}
