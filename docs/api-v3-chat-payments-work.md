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

## 红包与支付复用边界

本记录只交付类型目录及既有页面导航，不代表结算或支付订单能力已完成。后续实现按已确认的业务范围分别设计金额、参与者、资格、幂等与事务边界；兼容行为须重新核对当前源码并单独取得授权。公开记录不保存具体安全问题的入口或触发链。未进行真实下单、支付回调或生产验收。

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
