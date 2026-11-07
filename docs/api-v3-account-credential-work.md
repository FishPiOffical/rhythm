# 全凭据撤销与敏感设置接线（2026-10-23）

## 规则与部署依赖

用户已确认：第一方敏感设置每次要求当前密码，已启用MFA再要求当前TOTP；手机/邮箱证明新目标，六位码10分钟；MFA准备10分钟、密钥仅当次返回。成功变更密码、手机、邮箱或MFA后，全部Cookie、旧apiKey、v3和旧OpenID失效。密码重置同样全撤，旧重置页面成功后自动登录保持，但新Cookie仅使用本次提交的新密码及确切版本。

先部署`sql/20261023_account_credential_state.sql`，再部署MFA和contact两份同日SQL。state表缺失时认证失败，不回退“没有版本”。旧无版本凭据仅在当前epoch=0时兼容。新增Repository已入repository.json，无表名前缀硬编码。未对业务数据库执行迁移。

## 实际文件与行为

- `model/AccountCredentialState.java`、`repository/AccountCredentialStateRepository.java`、`util/AccountCredentialVersions.java`：独立持久版本，避免User旧整行更新覆盖；认证每次直接查当前SQL有效用户/密码/版本，内部Proof不放进公开User DTO。HTTP起始及结束清理ThreadLocal，非HTTP认证释放线程连接，已有调用方事务不得被广播收尾回滚。
- `service/AccountCredentialMgmtService.java`：在调用方严格事务及client/user锁之后，递增版本、撤旧OpenID active refresh、v3全部Token、未消费code及PENDING同意请求；不分页截断。`AccountPasswordResetService.java`接入同一核心。
- `util/Sessions.java`、`processor/ApiProcessor.java`、`processor/LoginProcessor.java`、`processor/BeforeRequestHandler.java`：Cookie/apiKey新发行携带内部版本，长度改为有界格式，保留旧输入形式；从认证时捕获Proof发行，不能重读新版本给旧请求升级。正常Cookie→apiKey、坏key回退真实Cookie、query/body apiKey都覆盖；密码/注册/重置链显式绑定认证证明。
- `util/OpenIdUtil.java`、`service/OpenIdRefreshTokenMgmtService.java`、`repository/OpenIdRefreshTokenRepository.java`、`model/OpenIdRefreshToken.java`、`processor/OpenIdProcessor.java`：旧access、refresh及待验证授权绑定版本；不以“只撤refresh”冒充access失效。`ApiV3TokenMgmtService.java`及`ApiV3AuthorizationRequestService.java`阻止旧Cookie/密码验证中的请求在撤权后发行新的第一方Token或pending授权。
- `processor/channel/{Article,Chat,Chatroom,Gobang,Shop,User}Channel.java`：需要身份的连接当前验证；私信收发、通知/商店推送、聊天室统一发送及五子棋消息处理重验。User/Shop断线按建立时服务端记录的连接归属清理，不拿已失效凭据阻止清理；在线时间只结算一次。Article历史公开热度/计数路径没有借此改为登录资源；不声称所有旧公开推送被统一关闭。现存连接在后续检查时失效，不承诺跨实例同步踢线。
- `util/ApiV3OAuthTransportLogFilter.java`、其同名Test：新安全设置及MFA路径加入既有日志保护，包含百分号编码；完整应用XML及真实Latke错误解析测试通过，保留无关失败日志。
- `src/test/java/.../service/AccountCredentialStateTest.java`、`tools/account-credential-state-test.mjs`，`util/OAuthCookieTest.java`及`tools/api-v3-oauth-cookie-test.mjs`：真实SQL/AES/HMAC/版本与隔离缓存/IoC适配。
- `repository/ApiV3TokenLifecycleTest.java`、`repository/AccountPasswordResetTest.java`、`tools/api-v3-chat-event-test.mjs`：旧回归fixture增加真实版本表/认证Proof、reset撤权核心与快照；不stub掉版本校验，复核整笔失败回滚。

上述省略的Java路径均位于`src/main/java/org/b3log/symphony/`，测试位于`src/test/java/org/b3log/symphony/`。敏感设置九个HTTP入口及完整文件清单另外见security、mfa、contact工作记录；邮件适配见account-mail工作记录；VerifycodeMgmtService账号短信开发模式模拟受理不再输出验证码，旧普通发送入口保持。总注册、API.md、UPDATE_LOG、AGENTS及progress由主代理维护。无前端修改或构建。

## 实际证据与局限

- `node tools/account-credential-state-test.mjs`：主代理最终10组退出0，`/tmp/rhythm-credential-root-final.log`。真实Sessions/OpenID、SQL及AES/HMAC、严格根；第8组编译生产ChatChannel发送方法及onMessage认证前缀，第9组编译生产聊天室发送和User/Shop清理方法，不把截取前缀说成完整私信处理集成。WebSocket/Session/cache/IoC及外部用户资料依赖适配。
- `node tools/api-v3-oauth-cookie-test.mjs`：主代理3组退出0，`/tmp/rhythm-cookie-root-final.log`。真实Cookie/APIkey当前版本、缓存陈旧及Cookie-only同意身份；曾修复尚未完成fixture中的Cookie重复import。
- `node tools/api-v3-chat-event-test.mjs`：主代理整套退出0，157条PASS，`/tmp/rhythm-v3-chat-credential-root-final.log`，含实际Token生命周期、PKCE、同意与新旧重置。最初缺新依赖/Proof fixture已修；不是只重跑工具函数。
- security6、mfa9、contact8、account-mail3组及account-smtp1组主代理最终退出0。日志过滤生产XML/Latke专项另行Java25执行通过。

未执行Maven；未启动完整IoC服务、真实MySQL、多实例、实际浏览器/扫码器/邮件短信极验。SQL读锁等待/死锁、缓存与用户级业务副作用需真实环境核查。部分生产方法提取编译、SQLite与依赖适配不能证明完整项目编译/运行成功。

## 人工全链路

1. 先执行三份SQL迁移，并通过IDEA重新编译或重启。保留PC/mobile两份旧Cookie、query/body apiKey、第一方及第三方v3 access/refresh、旧OpenID access/refresh、已打开WS、pending同意和code作为测试样本；另准备不相关用户验证隔离。
2. 第一方GET MFA状态，密码PUT成功后仅新密码能登录；手机/邮箱申请→收到码→提交新目标；MFA准备→本地扫码→启用。所有变更返回成功后，旧HTTP凭据和旧code/pending必须失败，其他用户保持可用，重新登录/授权后恢复。
3. 错误密码/TOTP、新目标已占、过期/错用途/旧码、换目标和发送中凭据/旧联系方式变化均拒绝；失败不能吞旧有效码或部分改账户。MFA新准备使旧请求失效，密钥不进日志/普通回执，不从GET找回。
4. 活跃私信WS在撤权后发送心跳/私信及接收推送均不得继续使用旧身份；聊天室下次推送拒绝，通知/商店私密推送不发给失效连接。User/Shop断线集合移除、在线时间仅结算一次；分别验证Cookie与apiKey连接、其他实例及HTTP广播中已有事务不被清理。
5. 旧Cookie无apiKey获取新key、query/body合法key、坏key加合法Cookie回退，均核对最终owner；旧凭据不能在撤权之后升级为当前epoch的新凭据。密码验证到签发之间、同意开始/批准之间注入并发撤销，必须拒绝陈旧Proof。
6. SQL/回执/版本/撤权/严格提交失败整笔回滚；提交确认丢失可能已提交，重新登录核实，不能为重放绕过当前认证。旧重置成功自动Cookie必须是本次提交的密码和版本，之后另一安全修改即失效。
7. 覆盖未登录/越权、第三方scope不足、黑名单→/test、validateCaptcha解封、首访及计次验证、开发关闭风控、PC/mobile。检查日志不得含当前/新密码、TOTP、准备密钥、验证码、完整联系方式或token。

## 反漏扫结论范围

九条新路由均Bearer且内部第一方当前授权，不新增匿名放行；BeforeRequestHandler仅增加Proof生命周期和已认证版本记录，黑名单/匿名盾处理顺序、anonymous.viewSkips及CaptchaProcessor未绕开。CSRF不借Cookie/apiKey升级新设置身份；字段有界、SQL参数绑定，URL不用于用户指定外部请求，无新SSRF、上传下载或公网静态/CDN路径。响应白名单与日志过滤覆盖秘密，不裸返User。预算为进程局部，未证明跨实例抗批量滥用。全站实际反漏扫、静态版本/sourceMappingURL/目录枚举仍未运行验收，不能用该源码结论宣称通过。
