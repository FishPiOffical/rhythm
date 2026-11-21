# OAuth原生协议准入（2026-10-24）

用户确认：token/revoke两个端点可使用服务端验证过的协议凭据作为端点本身的身份依据；授权码绑定应用、精确回调和PKCE，刷新/撤销绑定当前令牌。缺失/错误凭据仍走原匿名验证，全局黑名单限制保持。不增加anonymous.viewSkips，不建立普通Bearer/Cookie身份或IP白名单。

## 文件与边界

- `src/main/java/org/b3log/symphony/processor/ApiV3OAuthProcessor.java`：仅token/revoke路由在原privacy/rate之后接protocolToken/protocolRevoke。严格原始表单/当前CORS检查，通过只读credential proof才继续当前协议处理器，否则调用原AnonymousViewCheckMidware；数据库故障安全503。合法native actor也执行既有actor预算，避免仅Cookie用户受限。代码不写Sessions、IDENTITY、User.USER或白名单，最终换码/刷新/撤销仍由原严格事务核心重新校验当前状态。
- `src/main/java/org/b3log/symphony/service/ApiV3OAuthCredentialQueryService.java`：复用既有预备只读服务，本轮未改其实现。验证当前client/user、登记URI/S256、有效code或令牌；正确绑定的consumed code/rotated refresh且仍有活动原家族，只允许进入原重放撤销处理，不发新凭据。错误证明不能据此撤销他人家族。
- `src/test/java/org/b3log/symphony/repository/ApiV3OAuthProtocolGateTest.java`：真实Dispatcher/controllers/proof/PKCE/Token事务与SQLite；Cookie读取/匿名挑战响应适配，不能称真实全站匿名计数器已运行。原生HTTPS回调、反向域名scheme、开发HTTP回环均端到端经过该隔离协议链。
- `src/test/java/org/b3log/symphony/repository/ApiV3OAuthCredentialQueryTest.java`：仅更新“待用户选择”说明，原准备测试逻辑保持。
- `tools/api-v3-chat-event-test.mjs`：加入新真实测试类，保留既有API_V3_TEST_TARGET定向入口；默认全回归包含新六组，不另造公共运行开关。
- `API.md`/`UPDATE_LOG.md`、AGENTS、进度/计划/路由清单及本文：公开使用约定与内部安全证据；接口数量不增加。

源码复核：`Router.requestMapping`设置`Dispatcher.startRequestHandler = new BeforeRequestHandler()`，后者先Firewall.recordAndMaybeBan，再按既有enabled+ipBlacklistCache拦/test，随后才路由；未因协议凭据跳过全局handler。AnonymousViewCheckMidware、CaptchaProcessor与配置viewSkips未改。authorize仍原匿名门禁，consent仍Cookie-only/当前站点来源/Session CSRF，不借此扩展其他入口。

## 实际证据与限制

根最终执行：

```
API_V3_TEST_TARGET=org.b3log.symphony.repository.ApiV3OAuthProtocolGateTest node tools/api-v3-chat-event-test.mjs
```

6组退出0，日志`/tmp/rhythm-v3-oauth-protocol-gate-final.log`：合法无Cookie真实换码；错误PKCE/应用/回调/猜测/混合grant/Origin/Authorization交回匿名；刷新和正确绑定重放的家族撤销；有效access撤销、失效凭据/停用账号回匿名；预检查后用户变化最终核心拒绝；登记的native scheme与开发回环成功。预检时确认code未消费、无新Token，不写普通IDENTITY或Cookie。匿名adapter真实执行302/abort，因此证明路由委派，不证明生产验证码页面链。

新增准入之后，根已运行原chat-event回归157组退出0（`/tmp/rhythm-v3-oauth-protocol-regression.log`，当次runner尚未加入新main）；六组作为后续定向运行证据，不把它们伪称同一次完整应用运行。真实Latke/XML敏感解析日志测试仍覆盖OAuth原始和编码路径。本轮未Maven、无完整IoC/生产/8080浏览器或真实跨域native应用，也未实测真实黑名单/验证码解封；不据源码检查宣称全站通过。

## 完整人工链

1. IDEA重新编译/重启，使用管理员已登记应用，准备精确HTTPS或反向域名scheme回调；开发可用登记的HTTP回环，禁止动态端口推断或任意返回URL。保持测试IP不在黑名单以测试正常协议路径。
2. 浏览器完成authorize→Cookie登录→明确同意→回调code/state；原生客户端不携Cookie/apiKey、Origin或Authorization，提交client_id/完整redirect_uri/code/原verifier。应直接协议JSON令牌，不跳转/test；用令牌调用已授权v3资源，不能越scope。
3. 串行刷新一次换新对；重用原refresh或正确PKCE重放已用code应invalid_grant并撤对应原家族，新令牌不可再用。错误PKCE/其他client/不一致URI不能撤销原家族，仍进入匿名入口保护；原证明检查本身不消费code。
4. 用本应用有效access或refresh撤销，协议200{}，家族失效。未知/已撤销/过期、停用账号或应用、猜码/超长/混合参数、重复参数、错误Content-Type/UTF8不获得协议身份；可能先要求页面交互，不能将302/HTML当成功。测试请求不能伪造userId/IDENTITY或通过旧apiKey替代。
5. 浏览器跨域只允许当前登记HTTP(S) callback origin且credentials:omit，evil/null来源拒绝，不能读取凭据响应。Consent仍须真实Cookie、当前站点来源及CSRF，Bearer/apiKey不能审批，前后PC/mobile同意页分别回归。
6. 在预检后修改当前用户/应用/回调/scope或撤令牌，最终写核心必须拒绝；不能因为预检成功发过期权限。SQL/服务故障503且不打印原始code/token/PKCE，日志不写敏感表单/查询串。
7. 预先置黑名单IP发合法协议凭据，仍由全局handler跳/test；完成validateCaptcha解封后可重试。对没有合法协议证明的新IP复验首访/周期验证码、开发关闭风控以及anonymous.viewSkips未变，其他匿名/资源入口不能继承本端点身份。此项真实运行待做。
8. 复核无新静态/CDN版本号/sourceMappingURL、目录或上传下载路径；全站基线未在本定向测试运行。未改变资源API Bearer授权、第一方安全设置限制或导出下载权限。

公开API.md需同步网站API文章 https://fishpi.cn/article/1636516552191。
