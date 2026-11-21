# v3 音乐与天气卡片（2026-10-24）

新增两个已接入总register的POST：`/api/v3/chat/music`、`/api/v3/chat/weather`，当前254入口（253HTTP+1WS）。本批只新增写能力与新卡片投影，不新增表或迁移，不改变旧发送端请求/成功响应。

## 用户确认的规则与实现

音乐title去首尾空白后1..128个UTF-16字符，from可空最多64；纯文本、不接收HTML标签/控制字符/损坏代理对。source为1..32位数字网易云ID或合法HTTPS音频，coverURL可空HTTPS，两URL各2048字符，无凭据/片段/无效端口/反斜杠；原串查询参数保留，不抓取远程资源。天气city1..64、description可空128，同样纯文本规则；恰好五个连续有效yyyy-MM-dd日期（可跨月年、年份0001..9999），20个当前本地图标代码；温度必须整数-100..100且min<=max。天气为客户端数据，不是服务端权威查询。

两者content.write+共享写门禁、严格JSON与幂等键；作者从当前SQL用户取得，检查有效状态/绑定手机/普通发言间隔、当前禁言/风险确认及复读处理。保留词只处理可见文本后重新校验，不改变URL/结构。账户对锁→缓存边界→严格根事务→当前actor→回执，消息/持久事件/职业来源/最新发言时间/复读相关持久副作用/回执同提交。成功仅id/createdAt；新key新增，同key规范化同内容只返原结果，异内容409；重放当前账号仍须有效，不重复风控、广播或活动，不保证消息尚存。不收client、user、任意msgType或原始JSON命令。

内部存储cardVersion=3与typed payload，另保存旧卡片消费所需字段；v3读取重新验证payload后只投影既有card字段，保留纯文本、完整签名URL，不暴露内部payload/version。历史卡片继续原投影。两套旧聊天室优先解析raw md，在HTML文本/属性及播放列表处编码；封面限制HTTP(S)后以安全属性写入；播放器数字source固定网易云iframe、HTTPS音频以DOM创建audio，禁止把source拼HTML。新卡片提及不从JSON解释，普通消息提及流程保留。

## 本批实际文件

后端：

- `src/main/java/org/b3log/symphony/util/ApiV3ChatCardInputs.java`（新增输入规范化、重验、兼容卡片结构）
- `src/main/java/org/b3log/symphony/service/ApiV3ChatPublicationService.java`（共享发布链增加typed入口/当前actor重放守卫）
- `src/main/java/org/b3log/symphony/processor/ApiV3ChatProcessor.java`（两个POST）
- `src/main/java/org/b3log/symphony/processor/ChatroomProcessor.java`（新卡片提交后投递，不解释JSON提及）
- `src/main/java/org/b3log/symphony/util/ApiV3ChatMessageDto.java`（仅新卡片精确白名单投影）

前端：`src/main/resources/js/chat-room.js`、`chat-room-2.js`，`corepack yarn run build`生成`chat-room.min.js`、`chat-room-2.min.js`。PC/mobile的chat-room.ftl与chat-room-2.ftl均引用这两个共享脚本；无模板或CSS改动。

专项/适配：`src/test/java/org/b3log/symphony/repository/ApiV3ChatCardTest.java`（新增）、`ApiV3ChatPublicationTest.java`（共享夹具接线）；`tools/api-v3-chat-card-render-test.mjs`（新增源码/产物VM及可选浏览器夹具）；`tools/api-v3-chat-publication-test.mjs`（编译新类/执行专项并移植当前投递方法）；`tools/api-v3-chat-test.mjs`、`api-v3-chat-event-test.mjs`、`api-v3-chat-red-packet-test.mjs`、`api-v3-chat-red-packet-mysql-test.mjs`（共享处理器/服务及投递方法的编译适配）。最后一个MySQL runner本批仅维护接线，未再次运行。

文档：`API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-v3-progress.md`、`docs/api-v3-development-plan.md`、本文件，以及重新生成的`docs/api-route-inventory.md`（801总路由/398浏览器引用；与v3 METHOD+path口径不同）。

## 已运行证据与限制

- `node tools/api-v3-chat-publication-test.mjs`最终42组exit0，其中新card6组：字段/Unicode/日期跨年/整数/图标/URL；共享事务提交、回执重放和停用actor；profession/event/latest/receipt/commit故障全部回滚、lostACK不再发；共享phone/risk/保留词；两个2048字符URL突破普通正文4096但不截断并读回原值；实际HTTP content.write/JSON/key/旧Cookie及apiKey拒绝。日志`/tmp/rhythm-v3-chat-cards.log`。实际服务/处理器/严格事务/保存点/事件/职业来源/投递方法参与；SQLite仓储、账户资金、当前权限/身份、缓存、保留词和通知端口为夹具；不是完整IoC/MySQL验收。
- `node tools/api-v3-chat-test.mjs`14组exit0（实际读取/DTO与HTTP门禁，身份/渲染等边界适配），日志`/tmp/rhythm-v3-chat-card-reads.log`；event runner及其关联专项163条PASS、exit0，日志`/tmp/rhythm-v3-chat-card-events.log`，不把关联专项条数当独立新增能力数。
- `node tools/api-v3-chat-red-packet-test.mjs`17组exit0（维护共享发布/投递依赖后回归），日志`/tmp/rhythm-v3-card-packet-regression.log`；没有重复运行真实MySQL。
- `corepack yarn run build`exit0；仅四个目标JS有差异，生成CSS无差异，日志`/tmp/rhythm-v3-chat-cards-build.log`。四个源码/产物VM渲染专项通过，node --check源码通过。标准js-version-audit对四个目标文件命中0（版本暴露/sourceMappingURL），报告`/tmp/rhythm-v3-chat-cards-js-audit.json`，不是全站或远程CDN审计。
- IAB本地独立夹具实际加载两套min.js，核对引号/ampersand/emoji/HTML样式文字保持纯文本、加入播放列表仍纯文本；第一套点击播放核对构建audio及原始签名src，天气dataset原文正确。截图`/tmp/rhythm-v3-chat-cards-proof.png`。夹具CSP禁止外部媒体；示例音频不会真实播放，未验证媒体来源、完整ECharts、真实聊天室/Socket/历史回放或设备。临时服务和浏览器页已关闭。
- v3源码254条与进度表254条METHOD+path集合一致；天气目录与20个本地SVG精确一致；两个新curl示例bash -n通过；git diff --check通过。

没有运行Maven、改业务数据库或对真实用户发布消息。Java/FTL服务端联调仍待用户IDEA重新编译/重启确认；本批已提前提醒，不能用当前旧服务行为判断新代码。未完成完整生产框架IoC与端到端验收。

## 反漏扫与接口安全复核

源码检查：两路均只Bearer content.write，Cookie/query或body apiKey不用于身份；非匿名新入口，无新增viewSkips。BeforeRequestHandler仍在路由前判断黑名单并跳/test，AnonymousViewCheckMidware首访/累计验证码链未改，CaptchaProcessor.validateCaptcha极验成功仍清黑名单/访问计数及记通过时间。没有借card或旧页面创建匿名旁路，开发风控开关逻辑未改。真实新IP/黑名单/解封链仍需运行验收。

越权：作者从锁定当前用户取得，不接外部作者；重放复核当前有效账号。CSRF：纯Bearer头，不靠页面Cookie；没有对兼容apiKey的旧写入口加不兼容CSRF。XSS：输入纯文本、两套显示编码、播放器URL不拼HTML，隔离浏览器覆盖；旧不受限历史天气的完整ECharts链未审成全面修复。注入：参数未构造SQL，复用消息仓储事务；SSRF：不服务端抓资源，客户端按用户操作访问媒体。批量滥用：两路共享写门禁/当前发言间隔及普通聊天限制，边界数组固定5/文本和URL有界，不承诺防所有跨节点流量。泄露：返回仅id/time与公开卡片白名单，无账号敏感字段/内部payload/密钥/IP/UA。没有上传/下载或返回服务器路径，无新增CDN资源目录；目标静态资源无版本/source map命中，未全站枚举目录或重下载远程CDN。

## 完整人工验收

1. 独立开发库使用既有v3请求、聊天室事件与职业来源基础迁移；通过IDEA重编译/重启并确认。进入PC/mobile两套聊天室，准备有效已绑定手机且有content.write/read令牌的账号，以及无手机/停用/禁言账号。无Authorization或只旧Cookie/apiKey调用两个POST应401，错scope403；黑名单IP应先/test，完成/validateCaptcha后可继续；开发模式关闭风控也不能越过Bearer要求。
2. 用API.md音乐curl发送数字source，再发有效HTTPS音频（含签名query）、引号/ampersand/emoji标题与from、空封面。应200/id/time、列表/详情/context读取music card原文及完整URL；两套聊天室收到、刷新历史仍在。加入列表、立即播放、列表切换：标题纯文本、数字使用网易云播放器、HTTPS使用原生audio；来源失败正常显示媒体错误，不把失败说成服务端抓取失败。核对手机实际浏览器布局、远程来源限制和音频支持。
3. 用天气curl发送5天，含跨月/年、负温、-100/100边界；消息读取weather card的date/weatherCode/min/max/t/st正确，两套聊天室五日图表/城市/描述正确。提交4/6天、跳日/重复日、非法闰日、未知代码、字符串/小数温度、min>max及超长/标签/控制字符，应422无任何新消息/事件/回执。不要把客户端天气数据当权威预报。
4. 同body同key重试一次，只一个消息/事件/职业来源，不重复活动或提及；换字段同key409，新key在发言间隔后创建新消息。title/from中业务命令仅作文字，不执行红包/弹幕/执法。风控账号按409提示原键原体再次提交；当前禁言/无手机拒绝新消息，成功回执后来账号停用也不能重放。
5. 故障注入message/event/profession/latest/receipt/commit，检查所有持久写入与复读扣分/通知一起回滚、不广播；断网丢ACK重试只有原消息。普通文本、红包、话题、弹幕、撤回、反应事件回归无倒退；旧音乐/天气正常请求与成功字段不变。实际MySQL跨实例同key/不同key竞争与完整框架回滚、实时恢复仍需补验。

官方企业认证下一批待实现；材料类型/上限/审核员/一次读取、单pending申请与纯结论规则均已确认。v3全目标仍未完成，云存档、部分风险红包政策、客户端其他模块迁移及全链验收继续保留。
