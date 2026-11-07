# v3 客户端聊天室红包（2026-10-27，第八十二轮）

## 范围与文件

复用已有 v3 红包创建、详情和领取契约，接通拼手气、普通、专属、心跳和猜拳。客户端位于 `/Users/ling/Documents/fishpi/website_v3`，本轮未修改 Java/FTL/SQL 或 APP，不执行 Maven、提交或推送。原有 API.md 契约不变。旧历史红包不自动迁移：新详情返回404时展示错误，不回退旧领取器。

实际修改/新增的客户端文件：

- `src/api/v3-chat-red-packets.ts`：五类输入、金额/份数/接收人/留言/明确风险与出拳校验；严格投影创建和领取回执、当前详情公开用户及隐藏字段边界。本金=奖池+税，普通/专属按每份计算，实际税费只取服务端结果；负数和0领取均保留。
- `src/stores/v3ChatRedPackets.ts`：创建/领取共享唯一待定操作，冻结目标、类型、请求和原key；跨SPA恢复、同账号轮换保留，退出/换人清理并拒迟到。首次明确4xx可修正，未知恢复失败仍保留原命令；平台要求二次确认时明确决定。成功回执与当前详情/消息GET独立，重读失败不再次收费，旧详情不能覆盖新目标。
- `src/components/chat/ChatRedPacketEditor.vue`：五类表单、专属用户ID列表、费用及风险确认，猜拳手势必须显式选；未知操作冻结输入，成功匹配原内容才清理。
- `src/components/chat/ChatRedPacketDialog.vue`：点击卡片仅GET当前详情；领取单独确认，心跳提示负积分及余额门槛，猜拳选择手势并提示胜负/退款；已领、领完及发送者本人猜拳禁止再次操作。专属名单和发送者出拳只展示服务端合法字段。
- `src/components/chat/ChatRedPacketRecovery.vue`：共享恢复、二次确认、实际本金/奖池/税或有符号领取结果；结果未知不能当作未执行而丢弃；当前消息读取失败只GET恢复。
- `src/components/chat/ChatRedPacketCard.vue`、`src/components/chat/HongbaoCard.vue`：v3卡片只打开详情，关闭旧猜拳快捷领取路径；样式修改为SCSS源码。
- `src/views/ChatroomView.vue`：经典/简约布局接新编辑器和卡片，删除旧红包表单和详情链。
- `src/components/HiddenChatConversations.vue`、`src/components/WhisperConversations.vue`：隐藏会话及浮动聊天室接同一卡片；普通消息空条件插槽仍使用原正文。
- `src/layouts/AppShell.vue`：唯一全局详情与恢复组件。
- `src/stores/chat.ts`、`src/api/fishpi.ts`：删除旧红包发送/领取包装及状态，普通发送保持独立。
- `tools/v3-chat-red-packets-test.mjs`：真实API/Pinia/timeline/Vue表单、恢复、详情与卡片；HTTP、对话框框架及轻量DOM适配。`tools/v3-messages-test.mjs`仅补新子组件测试依赖适配。
- 客户端AGENTS.md、docs/V3_MIGRATION.md及构建产物；后端AGENTS.md、UPDATE_LOG.md、docs/api-v3-progress.md及两仓本文。

## 验证与限制

红包23组及既有相关回归202组，共225组通过。23组含全五类精确请求与回执、原键创建/领取恢复、负数/0、猜拳隐私和胜负退款、明确确认/取消、禁重复、身份/迟到/SSR隔离、读取失败只GET、真实卡片禁旧快捷领取、普通ChatBubble插槽回退。其余日志 `/tmp/rhythm-red-packets-regression-*.log`，红包最终日志 `/tmp/rhythm-v3-chat-red-packets-tests.log`。模拟服务的幂等与余额结果不能证明真实数据库原子性或单次扣费。

`corepack yarn run build` 类型、客户端、SSR通过，日志 `/tmp/rhythm-v3-chat-red-packets-build.log`。静态审计72个JS、版本发现0，无map/sourceMappingURL/sourceURL，报告 `/tmp/rhythm-v3-chat-red-packets-version-audit.json`。未新增公网CDN、任意代理或上传下载路径；两仓diff --check通过。

没有可用浏览器工具，本轮未验证实际浏览器、双视口、焦点/Teleport、真实余额/事务/并发/广播。未新增匿名入口，不改viewSkips、黑名单、验证码或登录链；使用现有Bearer及对应权限/写保护。白名单公开字段、文本插值、固定接口和参数化后端契约避免客户端扩散私密数据；不抓取远程URL。真实未鉴权、越权、CSRF、凭据撤销、批量滥用、黑名单→test→验证码解封、首访/周期校验和开发风控关闭仍需运行验证。整个工作区及生产CDN/目录枚举/存量上传下载路径发布前审计仍待。

## 人工完整验收

1. 确认已有后端及红包迁移运行，构建前端并强刷；登录→/cr→经典/简约「红包」分别创建五类。普通/专属确认每份金额×数量，本金/奖池/税以回执为准；专属输入合法他人ID，心跳至少5份、猜拳至少256且手势必选。取消确认不发送，确认后当前GET出现卡片。
2. 双布局、移动窄屏、隐藏会话及浮窗点卡片，只发GET；再单独确认领取。普通/拼手气0也算已领；心跳明确负数提示和余额门槛；猜拳不选手势不可领、发送者不能领，结算前对手看不到发送者手势，结算后显示结果和退款。专属名单只发送者能看。
3. 输入超限金额/份数、重复/本人/失效接收人、过长留言或缺风险证明应422且无扣款；旧历史详情404显示错误，不偷偷调用旧领取。已领取/领完不能重复；未领完删除409，领完按现有权限删除。
4. 测试环境模拟提交后响应中断，离页再回用原目标/正文/手势/key恢复，只结算一次；平台再次确认须明确决定。未知恢复403/404仍保留命令；成功后详情/消息503仅GET重读，不重付。并发另一创建/领取不能覆盖未知命令。
5. 确认过程中离页/关详情/换目标/退出不执行迟到扣费；换人清原资料，正常同账号token轮换保留恢复。旧GET迟到不覆盖新目标；浮窗普通文字仍显示。真实风控/权限、余额不足、重启与并发守恒、匿名/敏感字段和跨实例广播需后端联调。

思过崖规则现已获确认：下一步按用户ID读取实际未到期sys-mute，排除全体禁言与风控，仅公开有效账号字段及解除时间并分页。整个v3目标尚未完成。
