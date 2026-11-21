# v3 客户端聊天室普通发送（第七十三轮）

2026-10-27。接既有POST /api/v3/chat/messages和当前详情GET，284注册保持。普通发送/引用发送已联通；历史、实时、撤回、在线/节点、话题/弹幕/红包等仍有旧链，不宣称完整聊天室已迁移。无Java/FTL/SQL/APP/Maven/提交推送。

## 实际文件

客户端根 `/Users/ling/Documents/fishpi/website_v3`：

- 新增 `src/api/v3-chat-publication.ts`：普通正文trim、1..4096/有效Unicode，固定支持client与原键；POST只传content/client，回执仅id/createdAt。
- 新增 `src/stores/v3ChatPublication.ts`：未知正文/client/key跨SPA保存；新明确4xx可修正、未知恢复4xx保持原键；confirmation_required单独保留并等待明确确认，未提交确认可取消。成功回执、当前详情、读取错误分开；旧GET不能覆盖新发送、成功读取失败只GET，退出清空，普通token轮换保持。
- 新增 `src/components/chat/ChatPublicationRecovery.vue`：共享恢复/平台再次确认及取消、已确认元数据、当前GET重读；确认弹窗按身份/卸载取消，恢复仍原正文与键。
- 修改 `src/stores/chat.ts`：普通send调用v3 store且返回是否确认；从当前GET投影公开ChatLine，按ID替换/去重/插入，退出清掉本批投影。旧显式特殊能力单独legacy方法保持，普通正文不再进入旧语法执行器。
- 修改 `src/views/ChatroomView.vue`：普通正文+当前引用一起冻结发送，确认且当前输入仍匹配才清草稿/引用；两套布局共享恢复面板与pending禁重发。换身份清当前输入，既有编辑器缓存按owner区分。
- 修改 `src/components/chat/WhisperConversations.vue`：聊天室分支普通发送复用v3，不依赖旧apiKey或旧Socket连接，成功才清对应草稿；紧凑/完整编辑器共享恢复入口。私信分支保持上一批v3行为。
- 新增 `tools/v3-chat-publication-test.mjs`：15组实际API/Pinia/恢复Vue与实际chat store投影专项。
- 修改 `tools/v3-chat-source-test.mjs`、`tools/v3-messages-test.mjs`、`tools/v3-reactions-test.mjs`：适配新增显式依赖，保留各自实际被测链。
- `AGENTS.md`、`docs/V3_MIGRATION.md`：当前路径及进度。

后端本轮仅本文件、AGENTS.md、UPDATE_LOG.md、docs/api-v3-progress.md。API.md契约不变。

## 证据

发送15、私信21、原文13、表态16，共65组通过；类型检查+client/SSR双构建通过。静态JS版本/source map与两工作区diff检查以本轮命令为准。

浏览器真实完整ChatroomView+CDN编辑器+本地合成HTTP：当前引用读取→输入合成正文→POST保存但503→原输入/引用仍在且禁新发→恢复同键/正文/client→确认后清输入及引用→当前详情故意503→只点击GET重读→显示当前HTTP清洗消息，不从回执拼正文。fixture保存一次、恢复retry=true，无旧普通send请求。桌面现代与390×844经典显示通过，恢复测试前modern偏好；浏览器warn/error日志为空。未向真实用户/聊天室发信或操作积分。

截图 `/tmp/rhythm-v3-chat-publication-proof.png`、`/tmp/rhythm-v3-chat-publication-mobile-proof.png`。

## 人工完整链路

1. 确认此前Java已通过IDEA编译/重启，本轮未改Java。登录开发聊天室，普通正文/Markdown/表情/图片或引用当前消息后发送；即便旧WS未连接也应POST v3，收到回执后GET当前详情，按ID显示一次。经典、现代、抽屉紧凑/完整输入都测试。
2. 模拟保存后响应丢失/503，输入与引用保留，原正文/client/key在同SPA恢复；期间编辑新草稿，原请求确认不得清掉新输入。换布局/离开再回、完整页与抽屉交替恢复，不换键重复发信。
3. 平台返回confirmation_required时不自动重发，点击再次确认后才用原键/原正文；取消弹窗无请求，取消已知未提交确认可重新编辑。特殊业务包装返回422，普通消息不得偷偷调用旧语法能力。
4. 成功后详情503/404/unavailable要保持发送已确认、读取失败分开；重读仅GET，不再POST。不要把历史回执当最新正文，旧详情慢回不能替代后一次发送。多次回读同ID不重复插入。
5. 未登录不发请求；退出/换账号丢弃迟到回执和详情并清本批当前投影，普通token轮换保留同一发送。正文空/超4096/无效Unicode应保留输入并报错，重复点击只一个在途请求。
6. 核对普通POST体只有content/client；特殊红包/话题/弹幕仍旧显式能力，非本批完成，不以这些操作验证普通正文成功。

## 安全与未验

无新匿名路由/viewSkips/数据库写/CDN URL/上传下载路径。普通发送走既有Bearer边界、本人身份由服务端决定；正文/客户端/键受限，回执白名单，当前展示只既有v3清洗HTML/公开作者。原键防重、确认请求不自动回放；引用正文使用上一批当前详情，作者转义。退出/迟到/确认取消隔离，无正文日志/凭据URL或持久未知队列；owner编辑器缓存是已有草稿机制，不包含请求键/回执。无新增SQL/远程抓取或SSRF入口。

真实Java消息保存/积分/通知/权限、风险二次确认的实际业务、黑名单/test/validateCaptcha、匿名链路和生产目录枚举未运行验证。主历史/旧WS、节点/在线、撤回及特殊能力待迁移，旧公开数据仍由旧链提供；不宣称全聊天室已具备新的鉴权与同步保证。合成HTTP与专项测试不证明生产端到端行为。
