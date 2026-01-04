# AIProviderFactory 使用文档

`AIProviderFactory` 是 Rhythm 社区平台的 AI 调用工厂类，提供了简洁易用的 API 来调用 OpenAI 兼容的大语言模型服务。

## 目录

- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [API 参考](#api-参考)
  - [可用性检查](#可用性检查)
  - [同步对话](#同步对话)
  - [流式对话](#流式对话)
  - [原始流对话](#原始流对话)
  - [自定义 Provider](#自定义-provider)
- [进阶用法](#进阶用法)
- [架构说明](#架构说明)
- [异常处理](#异常处理)

---

## 快速开始

### 最简单的调用方式

```java
import org.b3log.symphony.ai.AIProviderFactory;

// 检查 AI 是否可用
if (AIProviderFactory.isAvailable()) {
    // 同步调用，获取完整响应
    String response = AIProviderFactory.chatSync(
        "你是一个友好的助手",
        "你好，请介绍一下自己"
    );
    System.out.println(response);
}
```

### 流式输出

```java
AIProviderFactory.chatStream(
    "你是一个助手",
    "写一首关于春天的诗",
    token -> System.out.print(token)  // 实时打印每个 token
);
```

---

## 配置说明

在 `symphony.properties` 中配置 AI 相关参数：

```properties
#### AI ####
# AI 功能开关
ai.enabled=false
# 默认使用的 Provider: qwen, openai
ai.provider=qwen

# OpenAI 兼容配置 (适用于 OpenAI、Azure OpenAI、以及其他兼容 API)
ai.openai.apiKey=sk-xxxx
ai.openai.baseUrl=https://api.openai.com/v1/chat/completions
ai.openai.model=gpt-4o
ai.openai.maxTokens=2048
ai.openai.stream=true

# 通义千问配置
ai.qwen.apiKey=sk-xxxx
ai.qwen.baseUrl=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
ai.qwen.model=qwen-plus
ai.qwen.maxTokens=2048
ai.qwen.stream=true
ai.qwen.enableSearch=false
```

### 配置项说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `ai.enabled` | 是否启用 AI 功能 | `false` |
| `ai.provider` | 默认 Provider 类型 (`qwen` 或 `openai`) | `qwen` |
| `ai.*.apiKey` | API 密钥 | - |
| `ai.*.baseUrl` | API 端点 URL | - |
| `ai.*.model` | 模型名称 | - |
| `ai.*.maxTokens` | 最大输出 token 数 | `2048` |
| `ai.*.stream` | 是否启用流式输出 | `true` |
| `ai.qwen.enableSearch` | 通义千问是否启用联网搜索 | `false` |

### 兼容其他 OpenAI 兼容服务

只需修改 `baseUrl` 即可接入其他 OpenAI 兼容服务：

```properties
# DeepSeek
ai.openai.baseUrl=https://api.deepseek.com/v1/chat/completions
ai.openai.model=deepseek-chat

# 本地 Ollama
ai.openai.baseUrl=http://localhost:11434/v1/chat/completions
ai.openai.model=llama2

# Azure OpenAI
ai.openai.baseUrl=https://your-resource.openai.azure.com/openai/deployments/your-deployment/chat/completions?api-version=2024-02-01
```

---

## API 参考

### 可用性检查

```java
// 检查 AI 功能是否可用（已启用且 API Key 已配置）
boolean available = AIProviderFactory.isAvailable();

// 获取默认 Provider 类型
ProviderType type = AIProviderFactory.getDefaultProviderType();

// 直接访问配置
boolean enabled = AIProviderFactory.AI_ENABLED;
String provider = AIProviderFactory.DEFAULT_PROVIDER;
```

---

### 同步对话

最简单的调用方式，阻塞等待完整响应。

```java
// 带系统提示词
String response = AIProviderFactory.chatSync(
    "你是一个专业的翻译，将用户输入翻译成英文",
    "今天天气真好"
);
// 输出: "The weather is really nice today."
```

---

### 流式对话

实时获取模型输出，适合需要即时反馈的场景。

#### StreamHandler 接口

```java
@FunctionalInterface
public interface StreamHandler {
    /**
     * @param token 当前收到的文本片段
     * @param fullContent 目前累积的完整内容
     */
    void onToken(String token, String fullContent);
}
```

#### 方式一：完整 StreamHandler

```java
String result = AIProviderFactory.chatStream(
    "你是助手",
    "讲个笑话",
    (token, fullContent) -> {
        System.out.print(token);           // 实时输出
        updateUI(fullContent);             // 更新 UI 显示完整内容
    }
);
System.out.println("\n最终结果: " + result);
```

#### 方式二：简化 Consumer

```java
String result = AIProviderFactory.chatStream(
    "你是助手",
    "你好",
    token -> System.out.print(token)
);
```

#### 方式三：带完成回调

```java
AIProviderFactory.chatStream(
    "你是助手",
    "介绍自己",
    token -> sendToWebSocket(token),           // 每个 token 发送到前端
    fullResponse -> saveToDatabase(fullResponse) // 完成后保存到数据库
);
```

#### 方式四：指定 Provider 类型

```java
import org.b3log.symphony.ai.AIProviderFactory.ProviderType;

String result = AIProviderFactory.chatStream(
    ProviderType.OPENAI,  // 或 ProviderType.QWEN
    "你是助手",
    "你好",
    (token, full) -> System.out.print(token)
);
```

#### 方式五：自定义消息列表

```java
import org.b3log.symphony.ai.OpenAIProvider.Message;
import org.b3log.symphony.ai.Provider.Content;

var messages = Message.of(
    new Message.System("你是一个翻译助手"),
    new Message.User(new Content.Text("Hello World")),
    new Message.System("请翻译成中文")  // 可以有多轮对话
);

String result = AIProviderFactory.chatStream(messages, (token, full) -> {
    System.out.print(token);
});
```

---

### 原始流对话

返回 `Stream<JSONObject>`，提供最大灵活性。

```java
import org.json.JSONObject;
import java.util.stream.Stream;

Stream<JSONObject> stream = AIProviderFactory.chat("你是助手", "你好");

stream.forEach(json -> {
    // 原始 JSON 响应
    System.out.println(json.toString(2));

    // 手动解析
    var choices = json.optJSONArray("choices");
    if (choices != null && choices.length() > 0) {
        var delta = choices.getJSONObject(0).optJSONObject("delta");
        if (delta != null) {
            String content = delta.optString("content", "");
            System.out.print(content);
        }
    }
});
```

#### 无系统提示词

```java
Stream<JSONObject> stream = AIProviderFactory.chat("你好，请问你是谁？");
```

---

### 自定义 Provider

完全控制 Provider 创建过程。

```java
import org.b3log.symphony.ai.*;
import org.b3log.symphony.ai.OpenAIProvider.*;
import org.b3log.symphony.ai.Provider.*;

// 构建消息
var messages = Message.of(
    new Message.System("你是一个代码助手"),
    new Message.User(new Content.Text("写一个快速排序"))
);

// 创建 Provider（使用默认配置）
OpenAIProvider provider = AIProviderFactory.createProvider(messages);

// 或创建指定类型的 Provider
OpenAIProvider qwenProvider = AIProviderFactory.createQwenProvider(messages);
OpenAIProvider openaiProvider = AIProviderFactory.createOpenAIProvider(messages);

// 发送请求
Stream<JSONObject> response = AIProviderFactory.send(provider);
```

---

## 进阶用法

### 多模态（图片输入）

```java
import org.b3log.symphony.ai.Provider.*;

// 构建包含图片的消息
var content = new Content.Array(List.of(
    new ContentType.Text("这张图片是什么？"),
    new ContentType.Image("data:image/png;base64,iVBORw0KGgo...", "image/png")
));

var messages = Message.of(
    new Message.User(content)
);

var provider = AIProviderFactory.createProvider(messages);
AIProviderFactory.send(provider).forEach(json -> {
    System.out.print(extractContent(json));
});
```

### WebSocket 集成示例

```java
@OnMessage
public void onMessage(Session session, String userMessage) {
    Symphonys.EXECUTOR_SERVICE.submit(() -> {
        try {
            AIProviderFactory.chatStream(
                "你是社区助手",
                userMessage,
                token -> {
                    try {
                        session.getBasicRemote().sendText(token);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                },
                fullResponse -> {
                    try {
                        session.getBasicRemote().sendText("[DONE]");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    });
}
```

### SSE (Server-Sent Events) 集成示例

```java
@GetMapping(value = "/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chat(@RequestParam String message) {
    return Flux.create(sink -> {
        try {
            AIProviderFactory.chatStream(
                "你是助手",
                message,
                token -> sink.next("data: " + token + "\n\n"),
                full -> sink.complete()
            );
        } catch (Exception e) {
            sink.error(e);
        }
    });
}
```

---

## 架构说明

```
AIProviderFactory (工厂类，读取配置，提供快捷方法)
    │
    ├── AIClient (HTTP 客户端，处理请求/响应)
    │       │
    │       ├── SSE (Server-Sent Events 流式响应处理)
    │       └── Text (普通文本响应处理)
    │
    ├── OpenAIProvider (OpenAI 兼容格式的 Provider 实现)
    │       │
    │       ├── Message (消息类型: System, User, Developer)
    │       └── Options (配置选项: Stream, MaxTokens, EnableSearch...)
    │
    ├── Provider (Provider 接口)
    │       │
    │       ├── Content (内容类型: Text, Array)
    │       ├── ContentType (具体内容: Text, Image)
    │       └── Authorize (认证方式: Token)
    │
    └── Model (模型接口)
            │
            ├── Model.Supported.Text (支持文本)
            └── Model.Supported.Image (支持图像)
```

---

## 异常处理

### 异常类型

| 异常 | 说明 |
|------|------|
| `ModelNotSupportException` | 模型不支持某种内容类型（如发送图片给纯文本模型） |
| `IOException` | 网络请求失败 |
| `InterruptedException` | 请求被中断 |

### 处理示例

```java
try {
    String response = AIProviderFactory.chatSync("助手", "你好");
    System.out.println(response);
} catch (ModelNotSupportException e) {
    System.err.println("模型不支持: " + e.toString());
} catch (IOException e) {
    System.err.println("网络错误: " + e.getMessage());
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    System.err.println("请求被中断");
}
```

### 安全检查

```java
if (!AIProviderFactory.isAvailable()) {
    System.out.println("AI 功能未启用或未配置 API Key");
    return;
}

// 安全调用
String response = AIProviderFactory.chatSync("助手", "你好");
```

---

## API 速查表

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `isAvailable()` | `boolean` | 检查 AI 是否可用 |
| `getDefaultProviderType()` | `ProviderType` | 获取默认 Provider 类型 |
| `chatSync(sys, user)` | `String` | 同步对话，返回完整响应 |
| `chat(sys, user)` | `Stream<JSONObject>` | 原始流对话 |
| `chat(user)` | `Stream<JSONObject>` | 无系统提示词的原始流 |
| `chat(type, sys, user)` | `Stream<JSONObject>` | 指定 Provider 的原始流 |
| `chatStream(sys, user, StreamHandler)` | `String` | 流式对话，带完整回调 |
| `chatStream(sys, user, Consumer)` | `String` | 流式对话，简化回调 |
| `chatStream(sys, user, onToken, onComplete)` | `void` | 流式对话，带完成回调 |
| `chatStream(type, sys, user, StreamHandler)` | `String` | 指定 Provider 的流式对话 |
| `chatStream(messages, StreamHandler)` | `String` | 自定义消息的流式对话 |
| `createProvider(messages)` | `OpenAIProvider` | 创建默认 Provider |
| `createProvider(type, messages)` | `OpenAIProvider` | 创建指定类型 Provider |
| `createOpenAIProvider(messages)` | `OpenAIProvider` | 创建 OpenAI Provider |
| `createQwenProvider(messages)` | `OpenAIProvider` | 创建通义千问 Provider |
| `send(provider)` | `Stream<JSONObject>` | 发送请求 |
| `getClient()` | `AIClient` | 获取 AIClient 实例 |

---

## 常量

| 常量 | 类型 | 说明 |
|------|------|------|
| `AI_ENABLED` | `boolean` | AI 是否启用 |
| `DEFAULT_PROVIDER` | `String` | 默认 Provider 名称 |
| `OPENAI_API_KEY` | `String` | OpenAI API Key |
| `OPENAI_BASE_URL` | `String` | OpenAI API URL |
| `OPENAI_MODEL` | `String` | OpenAI 模型名 |
| `OPENAI_MAX_TOKENS` | `int` | OpenAI 最大 tokens |
| `OPENAI_STREAM` | `boolean` | OpenAI 是否流式 |
| `QWEN_API_KEY` | `String` | 通义千问 API Key |
| `QWEN_BASE_URL` | `String` | 通义千问 API URL |
| `QWEN_MODEL` | `String` | 通义千问模型名 |
| `QWEN_MAX_TOKENS` | `int` | 通义千问最大 tokens |
| `QWEN_STREAM` | `boolean` | 通义千问是否流式 |
| `QWEN_ENABLE_SEARCH` | `boolean` | 通义千问是否启用搜索 |
