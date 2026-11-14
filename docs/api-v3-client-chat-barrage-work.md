# v3 客户端聊天室弹幕（第七十九轮，2026-10-27）

## 范围与文件

接入既有 GET `/api/v3/chat/barrager/price`、POST `/api/v3/chat/barrager` 及认证 chat 订阅的 `chat.barrager`，未增加或改变后端接口契约，API.md 保持已有定义。后端路由仍284（HTTP283、WS1）。不改Java/FTL/SQL/Repository、APP，不执行Maven或提交推送。

客户端实际修改：

- `src/api/v3-chat-barrage.ts`：原始有效Unicode正文最多4096，保留首尾空白和HTML原文给服务器清洗并判定1..32 UTF-16；颜色严格#RRGGBB并大写。独立no-store价格查询，未知/错误不同于0；UUID/时间/费用/单位回执白名单。
- `src/stores/v3ChatBarrage.ts`：原content/color/key冻结恢复，正常token轮换保留，身份切换/退出清除并拒绝迟到。首次明确4xx允许修正，未知操作的恢复4xx仍保留原命令；confirmation_required必须明确再次确认，成功不触发补播/历史GET。新操作先移除旧回执，避免旧成功提示冒充新结果。
- `src/stores/v3ChatBarragePrice.ts`：当前身份与请求序号/AbortSignal隔离，失败价格为空，不伪造免费或缓存旧价格；轮换重读。
- `src/components/chat/ChatBarragePrice.vue`、`ChatBarrageEditor.vue`、`ChatBarrageRecovery.vue`：当前价格/失败重试，发送前重新取价及费用确认，说明实际费用和即时送达限制；原输入在匹配操作确认后清理，离开/换人取消迟到确认。恢复跨SPA共享，整页刷新/退出不保留本地命令。
- `src/views/ChatroomView.vue`：经典/简约共用发送与恢复；`src/chat/sidebar/modules/BarrageModule.vue`共用价格和恢复。
- `src/api/v3-realtime.ts`：只在已认证chat分支接收弹幕，独立UUID DTO不要求历史消息revision；内容/作者长度、有效Unicode/颜色/时间/ID校验，作者/正文仅白名单，头像仅HTTP(S)无凭据；不转发多余字段。原消息/表态/话题仍走各自元数据入口。
- `src/stores/v3ChatTimeline.ts`：独立瞬时barrage ref，不查询消息详情、不写历史、不触发上下文失效；清授权/断开清除，拒旧订阅回调。
- `src/components/chat/ChatBarrageOverlay.vue`、`src/layouts/AppShell.vue`：仅挂一次共用现有chat连接，无额外Socket；Vue文本插值、固定格式颜色，不使用v-html。最多5条、8秒自动移除，近期UUID去重最多200/两分钟；断连、身份变化与卸载清理定时器。支持reduced-motion，不读取回执补播，也不在挂载时重放已有ref。此去重有界，不宣称无限历史去重。
- `src/stores/chat.ts`、`src/api/fishpi.ts`：删除旧弹幕业务语法发送和旧价格包装；其他未迁移特殊能力保持待办。
- `tools/v3-chat-barrage-test.mjs`、`tools/v3-article-realtime-test.mjs`、`tools/v3-chat-timeline-test.mjs`：真实生产API/Pinia/Vue/传输模块专项。
- 两仓AGENTS.md、后端UPDATE_LOG.md/docs/api-v3-progress.md及客户端docs/V3_MIGRATION.md、本记录；生成dist/client/server通过构建产出，未手改CSS/min.js。

## 实际验证

170组均通过且无额外警告/错误输出：弹幕13、话题13、上下文13、timeline17、撤回11、私信21、普通发送15、原文13、在线13、表态16、实时10、通知6、启动9。日志 `/tmp/rhythm-barrage-regression-*.log`（以当前脚本清单为准；误输入不存在的auth-startup脚本未计入，已改跑client-startup）。

弹幕专项执行真实API/共享store/完整Vue组件，外部HTTP/对话框/轻量DOM适配；覆盖价格为0/异常/迟到、先确认后发送、原始正文/规范颜色、提交后丢响应同键恢复、平台二次确认、重复请求、未知恢复4xx保留、身份变化、费用确认离开取消、回执不播、文本XSS及有界展示/过期/断连清理。独立实际WebSocket传输模块验证认证前拒收/错误DTO丢弃/字段投影；timeline验证不读历史/详情、不改上下文hint及旧订阅隔离。均是隔离测试，不证明真实服务端扣费与广播。

`corepack yarn run build`：vue-tsc和客户端/SSR构建通过，日志 `/tmp/rhythm-v3-chat-barrage-build.log`。版本审计72个生成JS、0发现，报告 `/tmp/rhythm-v3-chat-barrage-version-audit.json`；无map/sourceMappingURL/sourceURL，两仓diff --check通过。无新CDN依赖或公开上传/下载路径。

本轮浏览器控制工具不可用，未进行浏览器联调；真实桌面/移动布局、真实账号积分/风控/权限、事件交付均待人工验收，不能沿用此前批次浏览器证据。

## 安全与限制

复用既有Bearer/content.read与content.write及幂等写保护，不把Bearer塞进旧apiKey，不新增匿名入口、不改anonymous.viewSkips/BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor链路。SSR/访客不查私有价格或发送；客户端不取HTTP指定owner。实际未鉴权/越权/凭据撤销/CSRF拒绝及黑名单→test→解封、首访/周期验证码、开发关闭风控的真实应用回归尚未运行。

无SQL写入或字符串查询拼接，无远程抓取/SSRF；输入边界与事件白名单、Vue纯文本降低XSS和敏感信息泄露风险；仅有界瞬时展示，不抓取未知目标。未验证真实服务的批量滥用限制/多实例路由，不能声称全站送达。生产CDN、目录枚举、其他存量上传下载路径和整个巨大未提交工作区的发布前检查仍未完成；本轮静态扫描仅覆盖新构建。

## 完整人工验收

1. 本地现有后端部署好前置迁移并运行，前端build后强刷；登录→/cr→弹幕，在经典/简约与移动窄屏分别读价格、换颜色、输入正文、取消费用确认，确认无发送。随后确认发送，核对POST为v3/Bearer/同一请求键、实际回执费用与余额；其他已认证chat连接可收到纯文本弹幕并8秒消失。
2. 用32个ASCII/16个emoji测试32 UTF-16边界；清洗后超32、空白、非法颜色在直接API调用下422且不扣费。HTML输入由后端清洗，作者或文本内尖括号不作为HTML执行；重读价格503显示未知并阻止新发送，真实0可正常显示。
3. 在测试环境模拟服务已提交但响应503：原输入冻结，导航首页/侧栏或返回聊天室，以原正文/颜色/key恢复；只恢复原回执，积分只扣一次，不补播。平台confirmation_required显式确认/取消；未知恢复403/409仍保留原操作，不能悄悄换键新发。
4. 成功后再发一条并模拟结果未知，不能显示上一条成功回执。发送期间离开、退出或换账号，迟到确认/价格/回执/事件不得写回；普通token轮换保留原恢复命令。刷新会失去本地恢复信息，先恢复/核对。
5. 两个合法浏览器连接验证实时接收与重复UUID只展示一次、超过5条淘汰最旧、断连/退出立即移除；重连/重开页面不能补播旧事件。未认证/缺scope请求、已撤销凭据、黑名单和验证码完整链需要在真实服务验证。消息历史/上下文/表态应保持正常。

弹幕完成客户端源码与隔离验证；禁言、其余特殊写入/红包及全v3真实运行验收继续，整体目标未完成。
