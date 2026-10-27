# 聊天室红包与支付 v3 盘点（2026-10-20）

> 历史设计记录：当前五类红包已扩入既有三个v3入口；最新契约和证据见 [风险红包工作记录](api-v3-chat-red-packet-risk-work.md)，下文未开放标志不是当前状态。

当前更新（2026-10-24）：本文是旧链路历史盘点。用户已确认三类安全红包与税费/分配/隐私/旧桥接/删除规则，创建详情领取已接线，类型目录写能力为true，详见[红包工作记录](api-v3-chat-red-packet-work.md)；心跳/猜拳已扩展，见risk-work，骰子仍不支持。公开支付范围用户选择仅站内捐助页链接，GET已接线，详见[捐助工作记录](api-v3-donation-work.md)，不把真实订单/回调纳入当前已完成能力。

本文件是内部工程记录，包含待处理风险，不应直接复制到公开 API 文档。

## 已完成与本轮改动

- 已有 v3 普通消息列表/详情/上下文、发布/本人撤回、话题、弹幕、在线名单及聊天室 reaction，不属于本轮新增。
- 既有 `ApiV3ChatMessageDto` 只投影红包摘要：type/message/money/count/got，不输出 who、recivers、gesture、diceRet、内部 envelope。
- 本轮新增只读 `GET /api/v3/chat/red-packet-types`，要求 Bearer 的 `content.read`。没有改变旧红包入口、金额、税率、类型或页面行为。
- 返回 `types`（id/name/mayLosePoints）及 `creationSupported=false`、`claimSupported=false`。能力标志仅表示 **v3** 创建/领取未开放，不表示旧页面不可操作。
- 目录列出 random（拼手气）、average（普通）、specify（专属）、heartbeat（心跳）、rockPaperScissors（猜拳）；心跳与猜拳的 mayLosePoints=true。dice 在服务端明确禁用，不列入可选类型。
- 本轮没有返回领取名单、接收人、手势、余额、账号资料、订单凭据或任何真实红包记录；没有数据库/外网访问。

修改文件：

1. `src/main/java/org/b3log/symphony/processor/ApiV3RedPacketProcessor.java`：独立 Processor 与 content.read 路由。
2. `src/main/java/org/b3log/symphony/util/ApiV3RedPacketTypes.java`：白名单静态目录，每次返回新 JSON 对象。
3. `src/test/java/org/b3log/symphony/util/ApiV3RedPacketTypesTest.java`：类型/风险/响应白名单及响应对象隔离测试。
4. `tools/api-v3-red-packet-test.mjs`：Java25 独立编译运行上述真实工具类，无 Maven/服务端/数据库。
5. 本文件。

### 主代理接线与公开文档任务

在 `ApiV3Processor.register()` 的其他 Processor 注册处加入 `ApiV3RedPacketProcessor.register();`。本子代理按约定未修改该共享文件，也未修改 API.md、UPDATE_LOG.md、AGENTS.md。

公开接口契约：

```text
GET /api/v3/chat/red-packet-types
Authorization: Bearer <access_token>
scope: content.read
无业务请求参数
data.types: array
data.types[].id: string
data.types[].name: string
data.types[].mayLosePoints: boolean（领取有扣积分风险）
data.creationSupported: false（当前 v3 不提供创建）
data.claimSupported: false（当前 v3 不提供领取）
```

请主代理同步维护 API.md 与 UPDATE_LOG.md，并提醒用户将 API.md 同步到网站文章。公开文档只写契约与风险标志，不写内部事务/防扫描/旧入口关系。

## 旧红包实际链路与缺口

证据主体：`ChatroomProcessor#register/addChatRoomMsg/openRedPacket`，`chat-room.js` / `chat-room-2.js`。旧发送入口解析 `[redpacket]...[/redpacket]`，旧领取为 `POST /chat-room/red-packet/open`（loginCheck）。旧明细经 `/chat-room/getMessage` 及领取接口返回原始结构；v3 不可直接转发。

| 类型 | 旧发送扣款基数 | 旧领取 | 关键约束 |
| --- | --- | --- | --- |
| random | money 总额 | 随机份额，最后一份取剩余；有内存桶，缺桶存在旧回退算法 | 非 VIP4 税率 3% |
| average | money × count | 每人 money（持久值已经税后） | 非 VIP4 税率 3% |
| specify | money × 接收人数组长度 | 当前用户名在 recivers 中才领取，每人 money | 非 VIP4 税率 1%；recivers 仍按名称而非稳定 ID |
| heartbeat | money 总额 | 内存预分配可为负数，缺桶失效 | 非 VIP4 税率 3%；普通账号至少5份；旧领取要求余额不少于持久 money |
| rockPaperScissors | money，强制 count=1 | 胜得 money、负得 -money、平0；负/平再给发送者结算 | 非 VIP4 税率15%；出拳只在进程桶，重启无法恢复；发送者能查看手势 |
| dice | 不支持 | 未领满时明确拒绝；领满历史分支可读旧结果 | 注释内的玩法不是有效规则 |

上表为代码观察，不是新 v3 收费承诺。普通账号发送还有32最低金额（猜拳256）、10240最大、1..100份、份数不超过money；admin 用户名例外。非admin留言有旧字符过滤及20 UTF-16截断。VIP4是否免税来自会员状态。旧收税有独立金额取整/最少1及乘数逻辑，不能简单把“税率 × 总额”当精确收费规则。

旧领取前检查禁言/全员禁言与 OP 例外、风控名单的领取额度；服务端方法 synchronized 只在单实例串行，不等于数据库锁。重复判断按 who.userId；专属资格按当前用户名。旧领取返回 who 包括 userId/userName/userMoney/time/avatar；部分路径还有 recivers、gesture、diceRet。这些不能因为旧接口可见就直接列为新的公开白名单。

**事务缺口（本轮未修）**：

- 创建：扣款 transfer 自行事务 → 税收 transfer 自行事务 → chatRoom 消息事务 → 分配内存桶/通知/广播/最后发言时间，失败可能出现已扣款无消息或状态部分落地。
- 领取：消息 who/got 先提交 → 领取者 transfer → 猜拳发送者 transfer → 通知/广播，失败可能消费领取资格却未到账；跨实例并发不能靠 synchronized 保证。
- 现有 v3 普通消息保存核心不承担红包余额、奖池、税收、随机分配或领取唯一性，不能调用旧 Controller 包装成幂等。
- v3 尚缺：创建/领取、本人领取状态、受规则保护的详情列表、奖池/报价、原子结算、与旧入口共用的锁和持久状态、已提交事件及恢复。

**需要用户确认后才能进一步实现**：

1. v3允许哪几类创建/领取；是否先只开放不会扣领取者积分的三类；心跳/猜拳是否必须显式风险确认。
2. 红包明细对未领取者、非专属接收人、发送者、已领取者分别能见哪些领取记录/接收人/手势；本轮只提供类型目录，未决定该规则。
3. 专属资格是否保留按用户名（含改名影响）、count与recivers数量不一致、重复名称及失效用户的旧行为。
4. 税收取整/乘数、VIP状态变化、admin例外是否完整保留；不能在重构时顺手纠正余额规则。
5. 心跳/猜拳重启失效、奖池恢复、结算失败/退款/过期规则，以及是否授权旧入口同步原子化；无用户授权不能调整旧兼容行为。

## 支付实际链路与缺口

- `/charge/point` 页面要求登录，PC/mobile 都有微信捐助入口，调用 `GET /pay/wechat?total_amount=...&note=...`，得到二维码后弹层展示。
- `/pay/wechat` 挂 loginCheck，当前实现同时可能读取 Cookie/apiKey；随后直接调用固定微信支付上游地址，返回 QRcode_url。支付二维码不是普通页面静态链接。
- PC/mobile 支付宝按钮处于 HTML 注释中；模板残留 doAlipay 跳转 `/pay/alipay`，当前源码未找到 AlipayProcessor 或路由注册（Index/Cron中的 checkTrades 是注释）。不能将残留 JS 判断为可用支付能力。
- `/pay/wechatCall` 是无登录回调，校验自建签名后调用 processSponsor。奖励按整数金额×80、最低1积分；积分、通知、Sponsor记录、勋章及排名不是同一事务。

**不能作为新 v3 支付基础的现状（本轮未改）**：订单号是秒时间戳，tradeMap仅进程内保存note；不持久绑定订单owner/金额/状态。回调在签名校验前移除tradeMap；签名字段集合没有用于选取入账用户的 attach。代码没有从已持久验证的订单重查owner/amount，再一次性原子入账的完整链路。回调参数当前还会打印日志。本轮未调用任何支付提供方、未发起订单、未验证真实回调或做漏洞利用。

v3支付还缺订单存储与迁移、owner/金额/币种绑定、支付渠道资格、幂等创建/查询、验证回调和去重原子入账、关闭/过期/失败/退款、客户端回跳安全规则。需要先确认“支付跳转”究竟是已有捐助页面链接还是要求后台新建支付订单；不能对 GET 包一层跳转就宣称完成支付API。

## 已执行验证与边界

2026-10-20 执行 `node tools/api-v3-red-packet-test.mjs`，通过。runner使用 Android Studio 随带 Java25、真实项目 latke-core 中的 JSON，仅隔离编译目录工具类/测试：

- 精确5种类型，dice不列出，心跳/猜拳扣分风险标志正确。
- 目录与每个类型只含约定白名单，v3创建/领取明确false。
- 改变一份返回对象不能污染后续请求目录/能力标志。

未执行 Maven、未修改前端、无需 yarn build。未启动/访问8080、真实数据库或生产，没有支付网络调用。本runner未编译完整服务、未覆盖真实路由/Token/黑名单/验证码行为；不能把单元通过等同端到端通过。路由接线由主代理完成。

## 安全与反漏扫检查结论

| 项目 | 本轮结论 |
| --- | --- |
| 未鉴权/越权 | 新GET明确 auth.contentRead；不接受代查userId，完全静态数据。源码确认，真实401/403待联调 |
| Cookie/apiKey/Bearer | 复用v3 Bearer中间件，不增加Cookie/apiKey身份回退；待实测无Bearer仅Cookie/apiKey被拒 |
| CSRF | 只读GET不写状态，不添加写入口；旧Cookie/apiKey写接口未改 |
| XSS/注入/SSRF | 响应是固定中文名与ID/布尔值，无用户正文、SQL或网络访问，无HTML字段 |
| 批量滥用 | 固定5条响应、无DB/远程请求；沿用顶层Firewall，不新增业务限流配置 |
| 高敏返回 | 没有原始红包/用户/支付字段，不返回who、recivers、gesture、diceRet或凭据 |
| 匿名访问与anonymous.viewSkips | 不新增匿名入口、不改跳过项；查当前配置无v3通配跳过 |
| 黑名单/验证码 | BeforeRequestHandler在路由前查黑名单并跳转/test；未修改首次/每5次匿名校验或validateCaptcha。DEVELOPMENT关闭风控，须由用户在正确模式专门回归 |
| 静态/CDN版本、sourceMappingURL、目录枚举 | 未增加或修改公网静态资源/CDN URL/source map/文件服务，仅固定JSON路由；未进行全库公网/CDN下载复扫 |
| 上传/下载路径 | 本轮不新增上传/下载或文件URL字段，旧链路未改；未实测既有全链路 |

## 人工测试步骤（用户手动IDEA重新编译/重启后）

**先提醒并确认用户已通过 IDEA 重新编译或重启服务端，再做联调，不能把旧行为当作新代码失效。**

1. 从v3登录/授权取得有content.read的Bearer，GET `/api/v3/chat/red-packet-types`；成功信封中的data包含上方5种类型，两个写能力false，心跳/猜拳风险true；重复请求内容一致，无余额或聊天变更。
2. 不带Authorization、仅页面Cookie、仅query/body apiKey、损坏/过期/撤销Bearer均应拒绝；合法但缺content.read应403。Cookie身份与Bearer身份不同时也不应改变目录或通过Cookie绕过scope。
3. 请求原有 `/api/v3/chat/messages` 中红包摘要，确认仍不输出who/recivers/gesture；本轮没有开放创建/领取路由，不能用目录标志推断可向普通消息入口发送红包包装。
4. PC/mobile `/cr` 及两套聊天室外观原红包按钮仍展示原5种类型；仅在隔离测试账号和明确风险知情下人工核对旧行为，不自动领取心跳/猜拳、不向生产发包。此步骤本轮未执行。
5. 风控启用的隔离环境使用黑名单测试IP请求目录，应先跳/test；完成validateCaptcha后恢复正常认证链。另测匿名首访/计次验证码与开发模式关闭风控行为；本轮未执行，不默认通过。
6. 支付仅检查源码/页面入口可用性；在未完成订单绑定与回调修复前，不把旧支付包装成v3，也不为验证本次目录发起真实付款。
