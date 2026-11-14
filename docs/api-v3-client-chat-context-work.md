# v3 客户端消息上下文（第七十七轮）

## 范围与行为

复用已有 `GET /api/v3/chat/messages/{id}/context?radius=10` 和 before/after 列表读取，无公开契约变更；后端仍284入口（283 HTTP + 1 WS），API.md不改。

完整聊天室两种布局的“上下文”按钮及 `/cr#chatroom{id}` 引用链接打开独立阅读窗口。即使定位消息不在最近历史中也按ID查询；登录恢复后保留深链接。初次最多前后各10条、含定位消息，升序显示并高亮定位消息。前后翻页各取20条并替换当前窗口，不污染首页/浮窗的最新消息列表；可随时重读定位消息或返回最新消息，后者清hash并刷新主窗口。

失败显示原因，可按原方向与游标重试；非权限类失败保留当前窗口，空边缘保留最后非空游标并禁用该方向。401/403/404清掉旧正文；关闭/切目标/身份变化取消并隔离迟到结果。正常token轮换保留当前读取身份语义；退出或账号/授权上下文变化清理。

复用共享timeline的元数据changeHint，不新增WebSocket。相关窗口成员/定位消息更新、删除、本地确认撤回、发布回填或全量同步/清授权时清旧上下文并提示重读；读取途中出现最终页成员变更时拒绝提交旧页。无关目标不清现有快照。服务端事件可丢失，因此保留显式重读，不宣称上下文实时完整性。

## 实际文件

客户端根 `/Users/ling/Documents/fishpi/website_v3`：

| 文件 | 修改行为 |
| --- | --- |
| `src/api/v3-chat-messages.ts` | 上下文GET，anchor/条数/升序/唯一性/游标严格校验 |
| `src/composables/useChatContext.ts` | 独立窗口、双向分页、原游标重试、身份/事件/迟到隔离 |
| `src/components/chat/ChatContextPanel.vue` | 当前清洗HTML/卡片摘要、定位高亮、翻页/重读/返回入口、移动布局 |
| `src/stores/v3ChatTimeline.ts` | 公开内部变更提示给上下文失效，清授权也清上下文旧数据 |
| `src/views/ChatroomView.vue` | 两套布局入口、路由hash定位及返回最新消息 |
| `src/api/fishpi.ts` | 删除无消费者的旧fetchChatAround |
| `tools/v3-chat-context-test.mjs` | 实际API/Pinia/composable/Vue共13组 |
| `AGENTS.md`、`docs/V3_MIGRATION.md`、`docs/api-v3-client-chat-context-work.md` | 架构/进度/本记录 |

后端仅文档：`AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`docs/api-v3-client-chat-context-work.md`。

## 已验证

- 上下文13、历史16、撤回11、私信21、发送15、原文13、在线13、表态16、实时9、通知6、启动9，共142组通过，无异常测试输出。实际API/Pinia/Vue运行；HTTP、外部UI框架及滚动适配为测试替身。
- `corepack yarn run build` 类型及客户端/SSR构建通过；72个生成JS版本审计0命中，无map/sourceMappingURL/sourceURL，两仓库diff空白检查通过。
- IAB真实完整页+合成HTTP/实际WS：启动 `/cr#chatroom1600000000012` 恢复登录后定位最近窗口外的消息；初次失败/同步失效后重读显示原消息及邻居，前页503保窗口、原游标重试至边缘、390px重新定位/后页、返回最新清hash并关闭窗口通过，warn/error空。
- 截图 `/tmp/rhythm-v3-chat-context-mobile-proof.png`；仅合成读取，无真实消息、积分或账号变更。测试页和临时服务验后关闭。

## 完整人工测试

1. 登录进入 `/cr`，分别经典/简约点击消息“上下文”；或者直接打开不在最近列表的 `/cr#chatroom{id}`。确认登录恢复后仍定位正确ID，窗口仅显示本次GET的清洗内容、升序且中心高亮，不把该历史窗口替换首页/浮窗消息。
2. 读取前一页/后一页，检查before/after与对应next游标、每次替换而非无界追加；重读定位消息恢复原中心。空历史边缘禁用对应方向、不丢当前窗口及非空游标。移动390px同测。
3. 模拟前页503，窗口不清、显示错误；重试仍为同方向同游标。模拟中心404或授权401/403，旧正文清除且显示原因，不假装已成功定位。
4. 慢读期间关闭、切另一消息、退出/换账号，旧页不回填。初次刷新Cookie恢复不应被当成换身份而抹掉深链接；普通同用户token轮换继续本人读取，不用旧apiKey。
5. 在上下文窗口中由另一开发会话修改/撤回可见消息，当前相关内容清空提示重读；无关ID保持当前窗口。读取期间新页成员变更也不能提交旧页。真实撤权/多节点事件和断线重同步需另行验证。
6. 点击返回最新消息，hash消失、弹窗关闭、主窗口GET刷新；现代布局滚到末尾、经典按原新消息在前布局。浏览器后退/前进与引用链接在同页也能重新定位。关闭弹窗不触发写入。

## 安全与未验

只复用既有Bearer读取，无新增匿名路由/viewSkips、Cookie写、SQL、上传下载或远程抓取。ID/固定radius与分页有界，DTO白名单，卡片纯文本，普通HTML仅用已有服务端清洗字段；不展示原始存储信封。身份/目标/请求序号/事件冲突隔离防止迟到回填，不新增订阅或全表读取。无新CDN URL，构建完成版本与source map检查。

真实Java/MySQL可见性、账号撤权、跨实例事件/采样、服务端限流、BeforeRequestHandler黑名单/test/validateCaptcha/anonymous.viewSkips、生产CDN及目录枚举未运行验收；合成测试不证明这些安全项通过。未改Java/FTL，不需要本轮IDEA重编译；未运行Maven、未动APP、未提交推送。

禁言读取仍为旧链；红包/话题/弹幕/音乐/天气写及总清单其他客户端迁移和真实服务验收继续，全站v3目标未完成。
