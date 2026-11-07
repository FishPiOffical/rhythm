# 新风险红包经典领取与停用发送者结算（2026-10-28）

第110批。用户已确认经典PC/移动端显式风险确认与出拳；猜拳发送者账号存在即可结算，删除则409且不扣款/占份额。真实联调仍按用户要求暂缓。本批涉及Java，已提前提醒用户IDEA重编/重启；未运行Maven或业务库操作。

## 实际文件与行为

Rhythm：

- `src/main/java/org/b3log/symphony/service/ApiV3ChatRedPacketService.java`：猜拳发送者在既有账户锁/同事务内只检查账号存在，停用不阻止返还、不改变账号状态；已删除抛409。legacyOpen新增接收body的重载，viewOnly仅详情，风险领取严格布尔riskAccepted和整数gesture，经同一幂等核心结算；旧重载仍拒绝缺证明的新风险领取。可选expectedUserId不符409，防旧页跨账号恢复。详情补旧头像别名，出拳仅沿当前合法条件投影。
- `src/main/java/org/b3log/symphony/processor/ChatroomProcessor.java`：只对新核心记录转交当前认证attr与请求证明；含新riskAccepted的请求须application/json，旧历史仍走原链路。旧错误继续原code/msg格式，v3删除发送者错误为HTTP409。
- `src/main/resources/js/chat-room.js`、`chat-room-2.js`：实际消息中的durable_v3标记建立领取元数据；新心跳/猜拳先风险弹窗，猜拳无默认出拳，取消零请求；只查看/本人猜拳/已领取/领完只发viewOnly。未知网络结果保留原证明、出拳和预期账号重试；已知失败清意图。原历史gesture/dice请求保持。两套PC脚本及移动模板引用链均已评估，mobile/chat-room.ftl使用chat-room脚本。
- 对应`chat-room.min.js`、`chat-room-2.min.js`由`corepack yarn run build`生成，未手改min或CSS；无SCSS/FTL改动。
- `src/test/java/org/b3log/symphony/repository/ApiV3ChatRedPacketRiskTest.java`：新增停用发送者三结果、删除回滚、legacy只读/typed证明/重放/隐私及真实旧handler body/媒体类型边界；safe测试夹具`ApiV3ChatRedPacketTest.java`增加可选body/媒体类型。
- `tools/api-v3-red-packet-bridge-ui-test.mjs`：四个源码/生成文件各四组实际方法VM验证，取消/无出拳、确认、只读、网络未知原拳/账号恢复及历史请求形态。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-v3-progress.md`、本记录。

website_v3：仅`docs/V3_MIGRATION.md`和本记录同步；现有v3领取客户端无需改字段。没有修改官方APP。

## 验证与限制

Java25.0.3定向runner20组通过（原safe10+风险10），使用真实核心/SQL/Dispatcher及提取的旧handler桥接，SQLite/认证/通知等适配边界保持，不等于完整应用。JS16组、既有卡片4组、资源build明确exit0；改动4JS版本审计0发现、无source map，git diff --check通过。注册链仍290/文档290，无新v3路由。未重复完整website_v3构建/80脚本，因为其源码未改。

IAB 1280×720本地隔离夹具加载两套实际min脚本：心跳取消零请求、猜拳未选不能发送、选布确认得到整数2/riskAccepted true；第二套只查看仅viewOnly，最终构建确认带expectedUserId。只捕获JSON，未连接业务账号、积分或真实服务；夹具手工提供消息元数据，不代表完整经典模板/历史/WS端到端验收。临时页与服务已关闭。实际MySQL并发/重启、完整PC/mobile弹层与390px、真实授权和风控仍待用户后续验收。

## 安全与反漏扫

没有新增匿名入口或anonymous.viewSkips，旧loginCheck身份来源保持，v3仍原Bearer/scope；新风险证明JSON媒体类型阻止text/plain提交，未给apiKey链直接强挂页面CSRF。旧Cookie/apiKey正常JSON兼容。风险/出拳/预期账号不信任前端，核心当前锁后重验并同事务入账；未知重试不换原拳或用户。只读不结算，停用不恢复登录，删除不半扣款。新文字静态、ID受数字校验，无新增SQL拼接、远程抓取/SSRF、上传下载或CDN路径。4JS版本0/无map；BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor不变。实际新IP、黑名单、验证码/解封、开发模式、权限和公网目录枚举未验，不默认通过。

## 完整人工验收

1. 用户通过IDEA重编/重启Java，更新生成静态资源并强制刷新；若本地读取target/classes需同步对应资源。既有红包基础/risk迁移保持，无新迁移。用专用测试账号在PC两套聊天室和移动端打开新风险红包。
2. 心跳先显示可能扣积分；取消不请求，确认才领取。猜拳未选不能提交，明确选0/1/2并确认后发送整数；只查看、发送者、已领取或领完只读，不扣款。普通/专属/拼手气及旧历史仍按原正常链路。
3. 模拟已提交应答丢失，重开恢复原拳/原账号，不再抽份额或重复扣款；换账号旧页expectedUserId不符拒绝。已知失败解除后可重新确认，纯查看不领取。无证明、字符串gesture、text/plain风险请求拒绝。
4. 分别用三笔猜拳验证输/赢/平；发送者停用后仍记原账号返还，状态不变。删除发送者后v3返回409、旧入口保留错误JSON，领取者余额/份额/消息/回执均不变。检查积分守恒及未领完禁删除。
5. 检查未结算对外不泄发送者出拳，结算后合法展示；原APIkey/query/body认证、越权、无scope、新IP/黑名单/验证码与解封回归。真实交易由用户在专用开发账号执行，当前暂缓这些联调。

将API.md同步到网站API文档文章：https://fishpi.cn/article/1636516552191。
