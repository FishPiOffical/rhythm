# 访问控制与公开数据

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[验证码核心](captcha-crawlers.md)、[登录与凭据](auth-account.md)、[公开接口文档](api-contracts.md)。

## 反漏扫与安全检查

- 发布前在 Windows 核对 `git status --short` 与未提交 diff，按路由、模板、JS/CSS、SQL/Repository 分类；WSL 的状态噪声按环境专题处理。按下方反漏扫底线逐项给结论，不适用项说明依据，未验证项不得默认通过。
- 未提交代码审查、发布前检查、公开入口新增或改造必须覆盖反漏扫：匿名/登录/apiKey、黑名单跳转 `/test`、`/validateCaptcha` 解封、`anonymous.viewSkips`、公开 API 鉴权、静态资源/CDN 路径与内容版本号、`sourceMappingURL`、目录枚举、上传/下载返回路径；新增或改造匿名入口须经 `AnonymousViewCheckMidware` 接入验证码盾或明确要求登录，不得绕过首访/每5次验证码。适用的验证码盾指纹和 JS 版本号检查必须执行，不得擅自更新指纹消除失败。接口同时按未鉴权、越权、CSRF、XSS、注入、SSRF、批量滥用和敏感信息泄露评估。
- 验证码策略、状态及开关核心是 `AnonymousCaptchaShield`；`BeforeRequestHandler` 执行全局拦截，`AnonymousViewCheckMidware#handle` 保留身份解析与匿名阅读限制并调用核心，`CaptchaProcessor#validateCaptcha` 负责验证/解封。`LoginCheckMidware#handle` 负责登录态（含 apiKey）；风控与登录两套链路均不可绕过，受保护边界见 `captcha-crawlers.md`。

## 路由与旧接口身份

- 访问控制基线（已审计）：当前未登录可访问并不只首页/文章/登录/注册，还包含验证码页、`/about`、`/download`、`/privacy`、`/agreement` 及部分公开 API；匿名可访问范围还受 `symphony.properties` 的 `anonymous.viewSkips` 影响。
- 新增页面/路由默认要求登录（优先挂 `loginCheck::handle`）；若业务必须匿名访问，必须显式接入 `anonymousViewCheckMidware::handle` 并评审风险，不能裸放。
- 新增或改造登录/入口逻辑必须做回归：未登录拦截、登录后放行、黑名单 IP 跳转 `/test`、`/validateCaptcha` 解封、开发模式关闭风控后行为一致。
- 旧接口的 `loginCheck::handle` 兼容 `Sessions` + `apiKey`（query参数与JSON body），并写入 `context.attr(User.USER)`；处理方法优先从该attr取当前用户。未登录统一401，特殊URI `/gen` 返回空SVG。页面登录态与apiKey双兼容的新接口优先挂该登录中间件，不只挂permission；V3身份按其独立Bearer边界。
- `apiCheck::handle` 同样支持 `apiKey` 并写入 `context.attr(User.USER)`，但未登录返回 JSON `{"result":"Unauthorized"}`；适合纯 API 场景。
- `permissionMidware::check` 只做权限判断，不负责写入 `context.attr(User.USER)`；若处理方法还需要当前用户信息，必须自行取用户（或叠加 `loginCheck`）。
- `permissionMidware::check` 在 `permission.rule.url.*` 缺失时会直接放行（含匿名）；凡仅挂 `permissionMidware` 的路由必须同步补齐对应权限规则，避免后台能力裸露。
- 处理方法取当前用户推荐顺序：`context.attr(User.USER)` -> `Sessions.getUser()` -> `ApiProcessor.getUserByKey(...)`；不要只依赖 `Sessions.getUser()`。
- 路由总入口：`Router#requestMapping` + 各 Processor `register()`；新增路由先决定使用 `loginCheck` / `apiCheck` / `permission` / `anonymousViewCheck` 哪条链路。
- 历史遗留：部分接口未在路由层挂登录中间件而在方法内鉴权（如 `MedalProcessor#requireAdmin/requireLogin`、`UserProcessor` 的 goldFingerKey 系列）；新增接口不要复用该模式，优先路由层显式鉴权。

## CSRF 与兼容

- CSRF 安全判断必须先确认接口是否同时支持页面 Cookie 与 `apiKey` 客户端调用；已兼容 `apiKey` 的写接口不能直接补 `csrfMidware::check`，否则会破坏 RESTful/API 客户端。加固这类接口需先拆分页面入口与 API 入口，或设计明确兼容 `apiKey` 的 CSRF 例外。
- 涉及 API 入参或返回字段变更，且可能影响第三方客户端兼容性时，必须先说明接口、字段、兼容影响和替代方案，经过用户同意后再修改；安全漏洞修复也要把破坏性影响显式说清楚。

## 脱敏与已知风险

- 用户脱敏基线（已审计）：对外接口用户对象统一走 `DesensitizeUtil`（移除密码/邮箱/手机/QQ/2FA/apiKey/token/IP/UA 等）或字段白名单；已知存量风险（用户知情暂不修）：`/reset-pwd?code=` 与 `/register?code=` 页面把完整 `user.userPhone` 输出到 HTML（LoginProcessor + verify/reset-pwd.ftl、verify/register2.ftl，匿名可见）；`TopProcessor#removePrivateData` 不移除签到/登录时间戳等活动字段。
- `symphony.properties` 中提交的 `upload.rhypic.ticketPrivateKey` / `upload.rhypic.internalToken` 是开发调试专用，生产环境使用独立生成的密钥，审计时不要当作泄露误报。
- 评论/文章公开接口与 WebSocket 事件必须使用响应白名单；允许返回 `commentAuthorId`、`articleAuthorId` 这类公开用户 ID，但禁止返回 `commentIP`、`commentUA`、`articleIP`、`articleUA`、`userPassword`、`userLatestLoginIP`、`userPhone`、`userEmail`、`userQQ`、`secret2fa`、token/key 等高敏字段。完整 `Article` 或完整用户对象不得裸传，需先脱敏。
- 评论读取类公开接口即使不走 `/article/{id}` 路径，也必须按目标文章执行 `articleAnonymousView` 与讨论帖可见性校验；校验当前用户时必须兼容 `Sessions` 和 `apiKey`，否则会绕过文章页匿名访问限制或误拒绝合法 API 客户端。
- 公开页面模板也不得把 IP/UA/密码/联系方式/2FA/token/key 等高敏字段写入 HTML 或 `data-*`；后台管理审计页可显示 IP/UA，但必须保留登录与权限保护。

## 管理权限

- 举报后台的聊天正文（现存消息与撤回快照）由 `ReportQueryService` 在展示边界统一生成安全 HTML：身份及缺失提示按纯文本转义，消息跳转参数单独编码，正文使用本地私有 Markdown 清洗。不得用未清洗原文覆盖 `reportMemo`，也不得为了展示而修改原始审计快照；PC/移动模板共用此字段并一起回归。验证与兼容范围见 [举报正文安全渲染](../admin-report-content-safety-work.md)。
- 管理员永久停用用户走 `POST /admin/user/{userId}/deactivate`：仅 `adminRole`、要求 CSRF、禁止停用本人；`UserMgmtService#deactivateUser` 在同一事务内匿名化账号并把 `userPhone` 清为 `_`，状态 4 不允许通过普通用户编辑恢复。
- 用户分组 OID 常量：`adminRole` 为管理员，`1630552921050` 为 OP，`1630553268292` 为超级会员，`16305533360689` 为成员，`1630631382235` 为纪律委员，`defaultRole` 为 Default，`visitorRole` 为 Visitor。涉及分组权限判断时优先复用这些常量，不新增数据库权限记录；免审发帖绿色通道使用管理员、OP、超级会员、纪律委员四个分组。
