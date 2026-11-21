# v3 客户端独立私信页（第七十轮）

2026-10-27。接已有私信接口，后端注册284保持。当前独立/chat页面主链完成；全局抽屉/旧whispers、旧未读提示消费者仍需后续迁移。无Java/FTL/SQL/APP/Maven/提交推送。

## 实际文件

客户端根 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-messages.ts`：会话分页、当前/更早历史、未读、公开用户名解析、发送/撤回/两种显式已读。严格owner/peer/fromMe参与者绑定、ID/游标/升序/分页验证，响应白名单；不可用正文全部投影空，不拿Markdown当HTML。写入正文trim、10240/有效Unicode，回执仅元数据。
- `src/stores/v3Messages.ts`：单个未知命令kind/target/content/key跨SPA保存；新明确4xx允许修正，未知恢复4xx不换键。成功仅revision/完成命令，不乐观拼正文；退出/换身份清空，正常令牌轮换保留。非持久离线队列。
- `src/components/chat/V3PrivateMessages.vue`：会话分页、当前/更早历史、错误/空状态、联系人查询/文件助手、不可用对方历史、Markdown发送、本人确认撤回、明确会话/全部已读。写确认后HTTP重读，账户/目标/请求序号取消及迟到隔离。实时只更新提示/当前列表/未读，历史显式读取，无读取自动已读；同方向在途合并。普通token轮换保留输入并重新取票。
- `src/views/WhisperView.vue`：旧私信页换新模块，兼容/chat/用户名、?toUser=用户名及?peerId=用户ID入口，不再依赖旧apiKey或chat-channel。无法获取名字的历史用户用ID绑定。
- `src/api/v3-realtime.ts`：复用原通知/文章生命周期新增messages精确频道、首帧票据认证、messages.changed提示。
- `tools/v3-messages-test.mjs`：15组实际API/Pinia/完整面板+真实ChatComposer、底层编辑器与传输适配测试。
- `tools/v3-article-realtime-test.mjs`：新增第7组messages协议/跨频道忽略，原文章协议继续回归。
- `AGENTS.md`、`docs/V3_MIGRATION.md`：长期约定、当前范围。

后端本轮仅本文件、AGENTS.md、UPDATE_LOG.md、docs/api-v3-progress.md；API.md已有契约保持。

## 证据

私信15、实时7、通知6、评论20通过；类型检查+client/SSR双构建通过。生成静态JS版本/source map、diff检查以本轮命令结果为准。

浏览器+本地合成HTTP：/chat/other解析公开用户ID、会话/历史/未读；第一次发送合成Markdown在fixture保存后返回503→页面保留原文与原target→恢复同key得到原回执→当前HTTP正文显示且输入清空，fixture仅生成一条。取消撤回未发DELETE。390×844移动共享真实CDN MarkdownEditor输入→发送→清空及回读通过，保留格式/表情/图片工具。没有向真实账号发消息，没有真实撤回/已读业务操作。messages WS fixture返回不可用，浏览器发送仍成功证明不依赖WS；messages协议本轮隔离模拟，非真实Java服务证据。

截图：`/tmp/rhythm-v3-private-messages-proof.png`、`/tmp/rhythm-v3-private-messages-mobile-proof.png`。

## 人工完整用户链

1. 部署此前v3后登录开发网站，从顶栏私信进入/chat，查看会话分页及总未读；点联系人或通过用户名发起，核对目标ID、最新历史、加载更早的before游标。本人/非法名拒绝，不可用对方仍可读已有历史但不能新发。
2. 在明确可丢弃的开发会话输入Markdown/表情/图片，发送后仅HTTP当前历史显示消息、正文成功才清除；断开WS仍能发送。模拟已保存但503/响应丢失，跨SPA进入别页再回私信恢复原键/正文/目标，只一条消息。确认后历史503保留成功提示并可重读，不重新发送。
3. 本人消息撤回→取消无写；用户确认开发测试消息撤回后重读历史及列表；非本人无撤回按钮。切换目标时待确认操作取消。模拟撤回未知，用原DELETE键恢复。
4. 读取不自动标已读；明确点击会话/全部已读后查询当前未读，不把changed当当前总数；新到消息可继续未读。测试列表/历史/未读503、重试与空页，不用失败伪造空结果。
5. 切换账号/退出/换会话时，迟到历史、列表、票据、回执不得恢复旧正文或清除新草稿。正常token轮换保留草稿、重新取票；WS ready/changed后重读当前状态，不按事件数量推算未读，不直接拼事件正文。
6. 同步测试390px移动页面和编辑器。整页刷新会清除内存未知队列，应先核对原操作。

## 安全及未验

无新增匿名路由/viewSkips、数据库写、CDN地址、上传下载返回路径。已有Bearer及当前服务器权限负责身份，前端精确owner/peer/target与fromMe验证；不把Cookie或旧apiKey作为新页面身份。用户名/昵称/Markdown回退纯文本，HTML只消费既有v3清洗HTML，未新增远程抓取/SQL/注入入口；首帧票据不进URL/持久存储，身份/迟到隔离，单在途命令及同键恢复约束重复提交。敏感正文只在当前挂载页面内存，不记录日志或本地存储。

真实Java/MySQL发送/已读/撤回事务、messages WS过期/权限/断线、PC/mobile真实附件直传与发布回读、全局黑名单/test/validateCaptcha、匿名链路和生产目录枚举未运行验收。无本轮Java变更；真实联调前仍须确认此前Java已通过IDEA重新编译/重启。抽屉私信/旧whispers、旧全局未读计数、聊天室主体等继续，不宣称全站私信或v3已完成。
