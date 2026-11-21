# v3 客户端聊天室历史与实时（第七十四轮）

2026-10-27。消费既有消息游标/详情/话题与chat票据/WS，284注册保持。本轮迁移主页面、浮动聊天室和首页的消息历史/实时；在线名单/禁言管理、本人撤回与特殊写入仍有旧链。无Java/FTL/SQL/APP/Maven/提交推送。

## 实际文件

客户端根 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-chat-messages.ts`：新增before/after方向互斥、size/顺序/边界/游标一致性验证及当前话题GET；空页不伪造下一游标。
- `src/api/v3-realtime.ts`：共用首帧票据生命周期新增chat精确频道、ready状态、sync/topic/message/reaction事件白名单；只投影ID/revision，忽略事件正文、他人选择与未知类型。即时chat.barrager本批尚未消费。
- 新增 `src/stores/v3ChatTimeline.ts`：当前窗口、向前历史、串行目标变更队列、同目标新旧响应隔离、当前话题、reaction失效。首次ready/重连/sync重读当前窗口；旧详情/旧页不恢复已删除消息，发送成功的当前详情在旧历史请求期间仍保留。401/403取消并清空授权状态，旧回执不得回填；退出/换号清理、正常token轮换重新取票。清屏保持空白且继续收新事件，显式当前读取可恢复窗口。
- `src/stores/chat.ts`：消息列表由v3 DTO映射为既有ChatLine，公开卡片字段明确投影；移除旧msg/revoke/online正文WS、节点凭据与旧历史分页。普通发送的当前GET并入共享timeline；旧在线/禁言、显式特殊能力和旧撤回暂保留，不能宣称全模块迁移。
- `src/views/ChatroomView.vue`：真实ready/connecting/closed显示、当前消息重读、游客登录提示；两布局不再显示空节点选择；发送后的输入清理补当前身份核对。
- `src/components/chat/WhisperConversations.vue`：浮动聊天室共用timeline，与首页/完整页并用不误断连接；SCSS内容区flex填满剩余高度，移动输入区固定在底部；输入清理按当前身份核对。
- `src/views/HomeView.vue`：聊天室消息只从共享timeline，不读旧HTTP、旧SSR或旧快照聊天正文；发送复用v3普通发送/恢复面板，成功匹配才清输入、换身份清草稿。
- `src/entry-server.ts`：首页SSR不预取旧聊天室正文，不将其写入chatFeed载荷；其余首页预取索引相应调整。
- `src/api/fishpi.ts`：删除已无消费者fetchChatHistory包装。旧online/mutes/context等仍单独待迁移。
- `src/stores/chatFilter.ts`：card参数类型改为实际只需Object.values的object，适配严格DTO映射，无过滤行为变化。
- 新增 `tools/v3-chat-timeline-test.mjs`：15组真实API/Pinia/队列和竞争专项。
- 修改 `tools/v3-article-realtime-test.mjs`：新增chat协议2组，共9；修改`tools/v3-reactions-test.mjs`第13组改测实际v3 timeline元数据失效→当前reaction GET；`tools/v3-chat-publication-test.mjs`、`tools/v3-chat-source-test.mjs`、`tools/v3-messages-test.mjs`适配真实timeline或相应隔离依赖。
- `AGENTS.md`、`docs/V3_MIGRATION.md`：长期路径及进度。

后端本轮仅本文件、AGENTS.md、UPDATE_LOG.md、docs/api-v3-progress.md。API.md既有契约不变。

## 证据

Timeline15、实时9、普通发送15、原文13、私信21、表态16、通知6、启动9，共104组通过；类型检查及client/SSR双构建通过。静态JS版本/source map及diff检查以本轮命令为准。

浏览器真实ChatroomView+合成HTTP与实际WS升级/首帧认证：Bearer恢复后初始两条HTTP消息/当前话题/ready已连接；更早before首次503保留窗口→同游标重试，三条按ID排序；chat.message.updated仅带ID/revision（故意夹带正文）→详情GET显示HTTP更新，夹带正文未显示；deleted→详情404后移除；sync_required→新当前窗口。清屏不立即重灌，手动当前GET恢复。移动390×844抽屉与主页面共窗口和已连接状态；发现并修正内容区未填满造成输入区位置不正确，复查输入在底部。切首页后实际摘要显示同一v3当前消息。浏览器warn/error日志为空，无真实消息/积分操作。

截图 `/tmp/rhythm-v3-chat-timeline-mobile-proof.png`。合成协议不证明Java节点、授权过期或实际数据库一致性。

## 人工完整链路

1. 确认此前Java和迁移已部署，Java变更须先IDEA编译/重启；本轮无Java改动。登录开发/cr，检查GET当前messages/topic、POST chat/realtime-ticket和无查询参数WS；只有收到chat ready才显示已连接，首帧只发一次票据。
2. 初始历史→查看更早记录，核对before、升序和hasMore；503不清当前窗口也不推进游标，重试同游标。空末页不继续猜页数。经典/现代、移动和抽屉同测。
3. 用另一合法会话创建/修改/撤回消息，当前页应详情GET更新，撤回404后移除；reaction只重读对应表态，topic只读当前话题，事件正文或他人选择不能直接回填。模拟同目标新事件在旧GET期间到达、删除在旧页期间到达，旧响应不得覆盖或复活消息。
4. 断线/重连与sync_required要重读当前窗口，不能只依赖事件数量；消息发送成功当前GET在旧历史期间到达仍保留。清屏保持空白、后续新事件可显示；点击重读当前消息恢复。
5. 首页、完整聊天室、浮动聊天室之间切换，核对共用连接和消息；抽屉切私信不应误断首页或/cr的连接，关闭非消费者时清理。首页普通发送与原键恢复走v3，不再旧POST。
6. 退出/换账号清正文/游标/话题，旧票据/HTTP/事件不回填；当前401/403应清空并要求重新授权，正常token轮换重取票。游客/SSR只显示登录提示，不请求鉴权消息或携带旧节点密钥。
7. 检查没有chat-room/more、chat-room-channel、旧节点取密钥请求；在线/mutes及特殊写/撤回仍旧链，另行验收，不当成新消息读取回退。

## 安全及未验

无新匿名路由/viewSkips/数据库写/CDN URL/上传下载路径。消息读/票据走已有Bearer门禁；精确频道/目标/类型/游标验证、DTO白名单与既有清洗HTML；WS只ID/revision，无密钥URL或旧正文广播。请求按当前身份、代数、目标版本取消/废弃；401/403隔离授权与迟到回执，不以失败伪造成功空窗。队列合并同目标、过载重同步，客户端无服务器限流承诺。无新增SQL、注入或远程抓取/SSRF入口，旧首页匿名消息载荷移除。

真实Java/MySQL当前可见性、授权撤销/过期/重连、多节点、服务端批量限流、黑名单/test/validateCaptcha/anonymous.viewSkips、生产CDN目录枚举未运行验收。在线/禁言、上下文跳转、撤回和红包/话题/弹幕/音乐天气写等剩余链继续；不能宣称所有聊天室读取/写入和全站v3已完成。
