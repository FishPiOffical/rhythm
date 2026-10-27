# /api/v3 第三方 Web API 接口盘点与设计

## 目标

本文将 Rhythm 当前 HTTP 接口面与新的、稳定的第三方 Web API 分开整理。内容基于源码中的路由注册、现有公开文档 [API.md](../API.md)，以及 classic PC/移动端 FTL 与未压缩 JavaScript 中的接口字面量引用。

可重复生成的完整路由清单见 [api-route-inventory.md](api-route-inventory.md)，执行以下命令即可更新：

```bash
node tools/api-route-inventory.mjs
```

清单记录实际 Dispatcher 声明、处理器、声明附近的中间件与浏览器端引用。每次 Processor 路由变更后都应复查。该脚本不参与构建，也不会改动编译后的前端资源。

## 当前接口面

现有 `/api` 命名空间不是面向第三方的版本化契约，混合了四类接口：

| 类别 | 典型现有路由 | 当前访问模型 | v3 处理决定 |
| --- | --- | --- | --- |
| 公开读模型 | `/api/articles/*`、`/api/article/{id}`、`/api/tags`、`/api/domains`、`/api/breezemoons`、`/api/fish-games`、`/api/statistic`、`/api/milestones` | `AnonymousViewCheckMidware` 或未在路由中声明中间件 | 仅将经过响应字段白名单的资源作为 v3 公开读接口提供 |
| 用户 Web API | `/api/user`、`/api/checkin/status`、`/api/user/bag`、`/api/article-drafts`、`/api/emoji/*`、`/api/columns/mine` | `loginCheck`，同时接受会话或 `apiKey` | 以 v3 Bearer Token 与 scope 重建，不直接代理原有处理器 |
| 页面/AJAX 支撑接口 | `/comment/thread/*`、`/api/article/heat/*`、`/api/watch/*`、`/api/rhypic/upload-ticket` | 混合，且经常与页面响应结构耦合 | 保持内部接口；只有在定义稳定资源响应后才考虑提升为 v3 接口 |
| 运营、管理、金手指与回调 | `/api/admin/*`、`/api/profession/admin/*`、`/api/medal/admin/*`、`/api/gold-finger/*`、`/api/games/*/score`、回调 URL | 不一致，部分要求登录，部分使用独立密钥 | 不得暴露到第三方 `/api/v3`；保留内部命名空间和独立服务凭据 |

### 现有 apiKey 行为

`LoginCheckMidware` 接受会话、query 参数 `apiKey` 或 JSON body 的 `apiKey`，并将解析出的用户写入 `context.attr(User.USER)`。`ApiCheckMidware` 只接受 apiKey，未通过时返回 JSON `Unauthorized`。因此，受 `loginCheck` 保护的路由通常可以通过 apiKey 调用，但这并不等同于它已经是稳定的外部接口：不同处理器的响应结构、所有权校验、CSRF 前提和限流并不一致。

`CSRFMidware` 是为 Cookie 页面设计的。支持 Bearer 凭据的新 API 写入接口不能直接照搬：只有接受 Cookie 身份验证时才校验 CSRF，或者让 `/api/v3` 写入接口只接受 Bearer Token 并拒绝会话 Cookie。

### FTL 数据与浏览器请求

FTL 路由渲染的是服务端数据模型，本身不构成另一套 HTTP API。将模板源纳入生成清单，是因为模板会加载页面脚本，也可能直接写 AJAX URL。迁移时应明确区分以下三种契约：

1. 页面路由 -> Processor -> FTL 数据模型：继续作为内部渲染契约维护。
2. FTL/JS -> 现有 JSON 接口：只有第三方客户端确有需要时，才映射为 v3 资源。
3. 第三方 Web API：返回明确 DTO 与分页信封，禁止直接返回 FTL 数据模型或 Repository JSON。

## 建议的 v3 资源范围

v3 首个版本应以只读为主，覆盖现有已面向第三方说明的能力，但不引入管理、积分变更、游戏分数或金手指接口。

| 模块 | 建议 v3 接口 | 访问要求 | 对应现有能力 |
| --- | --- | --- | --- |
| 内容发现 | `GET /api/v3/articles`、`/articles/{articleId}`、`/articles/{articleId}/comments`、`/tags`、`/domains`、`/search` | 公开；遵从文章可见性规则 | 文章、标签、领域、搜索 API |
| 用户 | `GET /api/v3/users/{userName}`、`/users/{userName}/articles`、`/users/{userName}/breezemoons`、`/me` | 公开资料；`/me` 需要 `profile.read` | 用户资料与个人主页列表 API |
| 社区信息流 | `GET /api/v3/breezemoons`、`/fish-games`、`/milestones`、`/statistics`、`/rankings/{type}` | 当前策略允许时公开 | 现有公开列表 API |
| 专栏 | `GET /api/v3/columns`、`/columns/{columnId}`、`/columns/{columnId}/articles` | 可见内容公开 | 现有专栏 API |
| 聊天室 | `/api/v3/chat/messages`、`/online-users`、`/topic`、`/barrager/price`、`POST /barrager`及chat取票 | 读content.read，写content.write与幂等 | 普通历史/发送/本人撤回、持久话题、当前实例即时弹幕；红包仍待独立接入 |
| 个人状态 | `GET /api/v3/me/checkin`、`/me/bag`、`/me/notifications` | 带 scope 的 Bearer Token | 现有 apiKey 路由 |
| 创作，第二阶段 | `POST /api/v3/articles`、`PATCH /api/v3/articles/{id}`、`POST /api/v3/articles/{id}/comments`、reaction、follow | 带 scope 的 Bearer Token 和明确授权 | 文章、评论、投票、关注处理器，完成独立写入审计后开放 |

私信、管理员 API、职业管理、勋章管理、会员管理、账号恢复、支付、上传代理、内部回调和金手指接口不应直接搬进 v3。文件上传采用独立的短期票据资源 POST /api/v3/uploads/tickets：uploads.write、Bearer身份、当前用户投影及公开目标校验；只共用现有RhyPic的Ed25519底层协议，不代理旧控制器或暴露内部转发/删除能力。图床实际接收与约束仍待端到端验证。

## v3 接口契约

以已经落地的 `API.md` 与 `api-v3-development-plan.md` 为准：JSON 信封为 `{ code, msg, data }`，成功 code 为数字 0；错误 code 为字符串错误码，data 为 null。请求 ID 放在 `X-Request-Id` 响应头。列表统一为 `data.list` 和 `data.pagination`，参数使用 `p`、`size`，分页信息包含 `p`、`size`、`total`、`pageCount`。

时间字段默认使用毫秒时间戳；不可见的资源统一按不存在返回。最初讨论过的 `{ data, meta }` / 游标方案未采用，不应据此改动已经使用 v3 的客户端。

HTTP 状态码：400 请求格式错误，401 缺失或无效 Token，403 缺少 scope，404 资源不存在或不可访问，409 状态冲突，422 校验失败，429 频率限制。既有匿名盾或全局黑名单拦截仍可能返回 `/test` 重定向，不能把所有响应都假设为 JSON。

当前新增文章与评论端点由 `ApiV3ContentProcessor` / `ApiV3ContentQueryService` 提供；它们显式接收 Bearer 阅读者，不复用依赖 Sessions 的页面内容组装。文章详情和评论列表共用文章可见性检查，打赏与私密评论按当前阅读者单独授权。

## 身份验证与授权框架

### 凭据

新增独立的 `api_v3_client` 与 `api_v3_token` 数据模型。现有用户 apiKey 生命周期长、权限宽，只保留给旧接口。v3 Bearer Token 应具备 Token ID、密钥哈希、归属用户 ID、可选 client ID、命名 scope、过期时间、撤销时间、创建元数据与最后使用元数据；数据库只能保存加盐哈希后的密钥。

v3 使用 `Authorization: Bearer <token>`，不得接受 query 参数中的凭据。初期可以发放用户自管的个人 Token；浏览器集成在开放写 scope 前应迁移到带 PKCE 的授权码 OAuth。

### OAuth PKCE 目标设计（尚待实现，不作为当前发布完成证据）

第三方客户端使用授权码 + S256 PKCE：客户端提前登记精确 redirect URI，发起授权时绑定 clientId、redirect URI、scope、state 与 codeChallenge；授权确认使用已登录用户并验证页面来源/CSRF。授权码短期有效且只允许消费一次，换 Token 必须验证 codeVerifier 与原 client/redirect URI，禁止 plain PKCE、隐式授权与客户端自行指定用户。Token 与授权码数据库只保存哈希；刷新 Token 继续使用轮换与家族撤销，授权 scope 取用户批准与客户端允许范围的交集。

当前源码只为已有 website_v3 第一方登录签发 Token；草稿写接口用于这套授权链路的开发。第三方 OAuth PKCE 尚未落地，不能把写接口源码或默认 write scope 当作第三方授权发布已完成。完整目标仍需补齐上述授权流程和端到端验证。

2026-10-19 补充：HTTP仍仅开放上述第一方登录链。Token服务内部已支持同事务的指定注册客户端发行、客户端绑定刷新/撤销、当前客户端允许范围内缩减scope及S256校验；授权码、redirect URI注册、同意页与换码/第三方刷新HTTP入口尚未接入。不能把这些内部方法当作可直接传入userId发行凭据的公开接口。刷新/撤销的数据库锁序为client→token，换码须先取client再当前读授权用户与码，Token对必须与消费码同事务；源码行锁与SQLite适配测试不是MySQL跨实例并发证明。

### Scope

初始 scope：`profile.read`、`content.read`、`activity.read`、`notifications.read`、`content.write`、`comments.write`、`reactions.write`、`follows.write`。每条接口都要在路由注册中声明所需 scope。特权操作使用独立内部凭据，不能复用这些 scope。

### 路由与服务结构

在资源 DTO 和安全类就绪后，再新增独立的 `ApiV3Processor`。每条 v3 路由按以下顺序处理：

```text
请求 ID -> Content-Type/请求体大小限制 -> 限流 -> Bearer 身份验证 -> scope ->
资源可见性/所有权 -> 入参校验 -> Service -> 响应 DTO 白名单 -> 审计日志
```

读取处理器应使用查询服务和可见性校验。写入处理器应调用管理服务，在服务边界再次校验所有权，所有变更使用事务，且只在提交后发布领域事件。禁止让 v3 直接操作 Repository 或返回其 `JSONObject`。

## 必做安全测试

每条 v3 路由的验收都必须包含安全审查。上线前至少验证：

| 领域 | 必测断言 |
| --- | --- |
| 身份验证 | 无 Token、无效 Token、过期 Token、已撤销 Token、错误 Authorization Header、旧 `apiKey` 都按设计失败 |
| 授权 | 缺少 scope、访问他人资源、隐藏用户/文章/评论、管理员专有动作、已停用用户均被拒绝 |
| Cookie/CSRF | 带 Cookie 的跨域写入被拒绝；仅 Bearer 的写入无需 CSRF Token；v3 不会意外回退到 session 身份验证 |
| 输入 | 空值、纯空白、超长、非法 ID/游标、重复 JSON 字段、非法枚举、非法 UTF-8/JSON、Markdown/HTML 载荷、路径穿越均被拒绝或规范化 |
| 注入与 SSRF | 搜索/排序/筛选参数有白名单；不请求调用方控制的 URL；不接受 SQL/JSON 查询片段 |
| 数据泄露 | 所有 DTO 使用字段白名单；测试断言不包含 password、email、phone、QQ、2FA、apiKey/token、IP、UA、session、内部请求 ID、审核字段 |
| 滥用 | 基于 Token、用户、IP 的限流返回 429；写入幂等键阻止重复发帖/扣费；分页与响应大小有上限 |
| 浏览器与 CORS | Origin 白名单精确匹配；通配 Origin 不携带凭据；预检仅允许需要的方法/Header；CSP 与缓存头匹配公开/私有数据 |
| 可观测性 | 返回请求 ID；身份验证失败与写入均记录审计日志但不记录 Bearer 密钥；对异常失败/滥用率设置告警 |

公开接口仍须经过 `AnonymousViewCheckMidware` 或 v3 等价中间件，保留既有黑名单与验证码防护。每次内容查询序列化前均需执行文章匿名查看、讨论可见性与用户隐私规则。现有 `DesensitizeUtil` 可作为基线，但 v3 必须使用端点 DTO 字段白名单，不能仅依赖删除已知敏感字段。

## 实施顺序

1. 执行 `node tools/api-route-inventory.mjs`，审查新建或变更的有效路由，并在本文标注其预期 v3 归属。
2. 定义 v3 DTO 和响应/错误渲染器；编写序列化测试，证明敏感字段不会出现。
3. 新增 v3 Token 存储、哈希/轮换/撤销逻辑、Bearer 中间件、scope、请求 ID 与审计日志。
4. 交付文章、用户、标签、领域、专栏和信息流公开读接口；验证匿名、隐藏内容、验证码/黑名单及响应字段白名单链路。
5. 交付带 scope 的 `/me` 读取接口；验证用户不能读取他人的私有列表、通知、草稿、背包或积分。
6. 开放任何写 scope 前，先设计 OAuth PKCE 与幂等机制；每个写接口都使用事务并完成端到端授权测试。
7. 将 v3 专属文档写入 `API.md`，并在 `UPDATE_LOG.md` 增加带日期的第三方开发者更新记录；旧 API 保持文档直到另行公告废弃。

## 复用前需要解决的审计结论

以下是基于路由注册和中间件审查得出的设计结论，不代表每个处理器都存在漏洞。

| 结论 | 影响 | v3 必须采取的措施 |
| --- | --- | --- |
| `/api` 混合公开、用户、后台、内部和回调路由 | 不能通过 URL 前缀判断安全边界或调用方 | 使用独立 Processor 和 v3 命名空间；逐接口列明 scope 与调用方 |
| `loginCheck` 接受 session 与 apiKey，而 `apiCheck` 只接受 apiKey | 表面相近的路由可能有不同的浏览器/API 行为 | v3 只接受 Bearer，并与页面 CSRF 分离 |
| 部分旧写接口使用 GET 或没有统一写入中间件 | 缓存、预加载器和浏览器行为可能触发风险 | 变更只能使用 POST/PATCH/DELETE；要求 Content-Type、Token scope、所有权、限流，必要时幂等 |
| 现有 API 使用多种旧响应结构 | 客户端需要理解每个处理器的特殊约定 | 使用一种 v3 信封，独立版本演进 |
| 页面数据模型和 Repository JSON 比外部需求更丰富 | 复用时容易泄露敏感字段 | 建立窄 DTO 映射器，并对省略字段做回归测试 |
| 公开路由逐条挂载校验 | 新匿名接口可能绕过既有反扫描逻辑 | 要求 v3 公开读中间件，并以路由测试确保它不可缺失 |

## 人工验证清单

后续实现 v3 后，在 `http://localhost:8080` 完整验证以下用户链路：

1. 无凭据打开一篇公开文章并取得其 v3 表示；再访问关闭匿名查看的文章。确认公开文章仅返回文档字段，受限文章被拒绝且不泄露私有元数据。
2. 创建两个普通账号，为第一个账号创建仅有 `profile.read` 的 Token。调用 `/api/v3/me`，随后尝试读取第一个账号的私有资源与第二个账号的资源。确认 scope 与所有权失败可区分，且不出现敏感字段。
3. 从不可信 Origin 带浏览器 session Cookie 尝试 v3 写入；再以有效 Bearer Token 与幂等键发送同一写入。确认前者被拒绝，后者只成功一次，重放安全。
4. 测试非法 ID、大页大小、错误 JSON、HTML/Markdown 载荷、过期/撤销 Token、短时间突发调用。确认返回受控 4xx 和请求 ID，无服务端 500，无响应体或日志泄露。
5. 每条新增公开接口均测试匿名首次访问、黑名单 IP 跳转/验证码、验证码通过后释放、已登录访问，以及携带旧 apiKey 的访问。确认只接受设计中的 v3 凭据路径。

## 写入事务与幂等落地

`api_v3_request` 以 user + operation + Idempotency-Key 的哈希为主键，只保存规范化请求哈希和成功响应 DTO。请求行、领域写入、成功结果在一个数据库事务中提交；同键竞争通过主键及行锁串行化，存储冲突使用新事务有限重试。失败操作不留下业务写入或已占用的键；结果已提交但确认丢失时，重试读取原结果。

通用执行器的回调必须只进行当前事务内的数据库操作。后续文章、评论、通知等会产生推送/邮件/其他外部效果的业务必须另行接入事务后事件或 outbox，禁止把带独立事务或外部效果的旧服务直接包进执行器。当前草稿没有外部事件，已通过真实管理服务加事务测试适配器验证回滚、所有权和重放；MySQL 锁行为及 HTTP 仍待联调。

2026-10-19 授权码补充：已新增注册回调与hash-only授权码表、同事务签码/消费换Token对核心及重放家族撤销；回调范围已获用户确认：HTTPS、开发HTTP回环、原生登记的专用scheme。原生按RFC8252采用反向域名scheme和单斜线路径，完整地址（包括端口和查询）精确匹配，不采用动态端口例外。迁移见sql/20261019_api_v3_oauth.sql；授权请求记录、来源/CSRF/明确同意页、注册管理、HTTP换码/刷新与清理尚未接入，仍不能作为第三方OAuth上线完成证据。旧/openid链路不变，不能混用凭据。

2026-10-19 同意状态补充：授权请求表及owner-bound一次性批准/拒绝服务已实现，批准与签码同事务；Cookie同意页所需配置站点精确Origin/Referer和非空Session CSRF校验工具已隔离验证，但尚未接HTTP或模板。state可空、上限1024 UTF-8字节，回调ASCII表示上限2048且禁止预置code/state/error/iss等响应键。不能将工具测试作为浏览器同意或CSRF联调完成证据；下一步必须接Cookie-only身份、HTTP限流/清理及PC/移动页面。

### 2026-10-19 OAuth HTTP 接线

ApiV3OAuthProcessor 新增 GET authorize、POST consent/token/revoke。协议返回 HTML、303 或直接 OAuth JSON。Cookie 专用身份解析避免 apiKey 提升；两端授权模板与 SCSS 同步，日志过滤器须重启加载并在路由注册前检查。维护任务清理过期请求与码，保留活跃家族的重放检测依据。总路由701（HTTP691、WS10），浏览器引用398。69组核心/HTTP/模板/清理测试及Cookie专项1组通过；真实MySQL、完整IoC和生产匿名链路未验证。登记管理与凭据作为端点登录证明的策略尚待完成。实际文件与人工测试链路见 development-plan 最新记录。

2026-10-19 登记核心补充：ApiV3ClientRegistrationService 提供已批准应用的同事务原子登记，随机client_id、已实现scope词表、完整回调校验；无HTTP入口，身份/审批/幂等归调用方。管理员登记或自助待审的业务选择已提问，未答复前不开放登记。新增3组登记测试，合计72组通过；新应用真实S256换码参与测试，MySQL/管理入口尚未验证。源路由重新核对仍701，完整目标未完成。

### 2026-10-19 账号入口继续

新增GET /api/v3/auth/registration-options（anonymous），只供表单展示，不是注册证明。原始注册开关直查数据库投影，邀请资格复用角色读取，响应白名单不带用户对象。新增AccountVerificationService及VerifycodeRepository绑定查询：当前用户/手机号/类型/用途/有效期锁定消费，与后续账号变更同事务，重复同值码全部消费；没有公共验证/激活HTTP，旧register2/reset-pwd兼容变更尚待答复。注册选项4组与原专项扩展77组通过；源路由702（HTTP692、WS10），398浏览器引用。真实MySQL、短信、风控及IDEA重启后联调未执行。

完整目标中私信不能因早期“不直接搬旧接口”的建议被遗漏：需要专用权限、参与者可见性、事务化发送/已读/撤回和专用实时授权；当前没有v3私信入口。账号注册/恢复同样继续保留为必做，不把注册选项或内部验证primitive当完成证据。详情见development-plan最新记录。


### 2026-10-19 密码重置完成

新增 POST /api/v3/auth/password-reset/complete，经anonymous处理后以当前手机号及找回短信证明完成严格事务；AccountPasswordResetService也接入旧POST/reset-pwd，显式userId，不回退身份，提交后才旧Cookie登录。密码、绑定短信消费、全部client的v3 Token撤销和待决OAuth清理同提交；缓存仅事务结束后失效。旧注册2/重置兼容已获用户同意，注册2激活/奖励事务及两端code字段尚未实现，无需重复确认。旧reset部署也须既有v3/OAuth迁移与完整重启日志过滤器。

最新隔离回归共用82组、注册选项4组通过；重置5组实际SQL/根事务/故障/入口解析，SQLite和登录缓存替代实现不等于真实MySQL/IoC/Cookie/短信/全局风控。盘点703路由（693HTTP、10WS），398浏览器引用；未运行Maven/应用HTTP/浏览器，重启尚未确认。具体文件、权限/反漏扫检查及完整人工路径见development-plan最新记录。全量v3目标继续，短信申请/注册激活、应用管理、鱼游OAuth、私信、红包、支付等不能由本批测试推定完成。


### 2026-10-19 注册完成接线

新增POST /api/v3/auth/registration/complete（anonymous），与旧register2共用AccountRegistrationService严格根：预投影并锁定完整sys账户对、namespace/当前开关/排序用户/邀请码及绑定注册短信，激活/初始与邀请奖励/邀请码消费/通知同提交；缓存、索引、刷新与天降红包只在提交后。旧必传code已获同意并落实，PC/mobile隐藏字段及verify.js/生成产物同步。短信申请v3尚未实现，旧首次申请仍原链。旧register2/reset-pwd缺少统一anonymous验证的兼容变化另待用户答复，未接线且不能宣称旧首访检查通过。

共用专项91组、注册选项4组通过；前端yarn build通过；实际浏览器隔离fixture四项（PC/mobile×源码/min）通过，AJAX拦截，无真实注册。FTL完整语法/隐藏字段片段渲染通过，完整应用宏未运行。JS版本审计155文件0命中；路由704（694HTTP+10WS）、398浏览器引用。没有Maven/真实短信/MySQL/8080/完整IoC，重启未确认。源码文件清单、安全结论及完整人工链见development-plan最新记录；所有未完v3模块保持原范围。


### 2026-10-19 找回短信与旧提交匿名验证

用户已批准旧POST/register2、POST/reset-pwd接入anonymous；现已在旧业务前执行，无viewSkips变更。新增GET /api/v3/auth/captcha-options只返geetest4/public ID，POST /api/v3/auth/password-reset/request接受手机号与有界四字段极验证明，复用同步后的原SMS预算和固定发送服务。通用accepted不披露账号可用性/服务商结果；服务商受理后在严格根当前锁用户再绑定保存RESET_PWD码，远程期间无SQL事务，保持既有码。物理发送/SQL不是原子提交，无恒定时间或可靠队列声明。专用腾讯受理核对一项目标/+86/Ok，旧提供商正常返回契约保持；生产日志不写手机号/码/完整回执。

共用专项100组、注册选项4组通过；新增申请→现有完成核心、故障/变更/输入/额度/受理及公开组件；真实Dispatcher校验提取的旧路由顺序，匿名策略/SDK传输/Cookie为适配器。源码路由706（696HTTP+10WS），398浏览器引用。无新增表或前端，无Maven/真实短信/极验/MySQL/8080/完整IoC；重启仍未确认。文件清单与完整手验及安全/反漏扫限制见development-plan最新记录。v3注册短信及私信/红包/支付等完整目标继续，不把恢复链当全量完成。


### 2026-10-19 注册待验证账号核心

新增AccountPendingRegistrationService当前事务primitive及共享UserRegistrationDefaults，旧addUser复用纯默认字段。外层须已有严格根/ArticlePublicationCaches及完整短信/极验/邀请授权；namespace先锁、当前开关、排序有界用户锁，pending手机号复用/自然月注销等待期/会员编号与新记录同保存点写入；缓存按全部别名根结束后失效。新注册短信HTTP尚未接入，邀请码预留与极验条件待用户选择；证明还须绑定请求时名称，不能让旧重发改名后激活不对应名称。

共用107组、注册选项4组通过；实际服务/默认字段/metadata全列SQLite/严格提交/保存点/故障/边界及缓存适配器。没有新路由/表/前端/公开字段，仍706路由（696HTTP+10WS）、398浏览器引用。未运行Maven/真实MySQL/短信/头像/完整IoC/8080，重启未确认；API.md不虚构未开放的申请接口。具体文件清单、安全限制及人工回归见development-plan最新记录。完整v3范围不因内部primitive测试而缩减。

### 2026-10-20 私信写入进展

私信当前已有五个messages.read读取入口，以及messages.write下的发送、指定/全部已读三入口；独立Bearer及幂等，不能照搬旧apiKey WS。用户已确认发送接收者仅当前激活的其他用户及固定文件助手，已有历史仍按参与者读取。消息/未读/旧会话列表同事务，旧异步列表改为重读实际最新消息；旧整个发送仍非原子。迁移见20261020_api_v3_private_message_writes.sql，内容TEXT、索引及精确默认权限须先更新。专用撤回/实时、真实MySQL/应用运行与其他模块仍待完成；具体文件、测试适配边界和完整手验见development-plan最新记录，不将早期“不能直接搬旧接口”的建议当作删掉私信目标。


### 2026-10-20 私信撤回兼容入口与变更标记

v3 DELETE本人私信已接线，用户批准旧GET /chat/revoke共用严格事务核心；只清messageId/fromId/toId准确关联未读，保留未知历史记录，身份取ApiCheck认证attr，提交后旧推送失败不覆盖成功。旧GET无幂等回执。新增api_v3_private_revision（20261020_api_v3_private_realtime.sql及repository.json）在v3发送/已读/撤回和旧原子撤回同事务写owner标记。当前尚未覆盖旧普通发送/已读，未接专用私信Socket票据/授权复核/worker，不能宣称实时完成；标记不是历史游标。实际文件、人工完整链路和运行验证限制见development-plan最新记录。


### 2026-10-20 旧私信标记覆盖完成

旧普通/手动/认证申请私信三组写入现经ChatInfoRepository/ChatUnreadRepository.addLegacy单步严格根，消息标记双方、未读标记接收者；旧指定/全部已读用owner-aware删除。PrivateMessageListStateService.refreshLegacy最终列表提交再标双方，防止前两步刷新后遗漏列表；各旧步骤仍独立，不能称整个旧发送原子或幂等。chatLog提交后只记messageId，不重读删除目标。此前“旧普通发送/已读未覆盖”已更新；私信专用Socket票据/连接/授权复核/worker仍待接线。新表/元数据迁移仍须先部署，完整文件、故障边界与人工链见development-plan最新记录。


### 2026-10-20 私信实时取票与空变更提示

新增POST /api/v3/me/messages/realtime-ticket，messages.read+write gate，复用现有WS首帧一次性票据。ApiV3SocketTicketService绑定actor/父令牌/messages/空target，当前token/user/client/scope逐次复核；ApiV3RealtimeService.pollMessages读取已提交owner标记，sessions只向该owner的messages频道发送messages.changed/data={}。首次/重连ready后主动HTTP同步，提示合并/重复/丢失，不当逐条消息/已读回执/可靠游标。此前“私信Socket未接线”当前已更新；真实MySQL、多实例、IDEA/应用/浏览器及完整反漏扫待验收，完整v3目标仍active。文件和完整人工链见development-plan最新记录。


2026-10-20：头像GET/PUT /api/v3/me/settings/avatar已接入，读取profile.read、写settings.write+幂等事务。新写接受合法HTTPS（外部、无后缀及带query），255 UTF-16；GET返回保存地址，旧页面显示来源和缩放规则保持。参数/身份/回滚测试及限制见development-plan最新头像记录；其他账号设置和完整运行验收仍待完成。


2026-10-20：MBTI本人读取和选项目录GET /api/v3/me/settings/personality、/personality-options已接入profile.read；仅当前有效owner的mbti和16类型/A或T目录。写入无效值规则已询问，尚未开放新PUT，不改旧资料/注册行为。测试、安全限制及完整人工步骤见development-plan最新记录。


2026-10-20：MBTI写入规则已获用户确认并接入PUT /api/v3/me/settings/personality，严格16类型/可选-A或-T、大小写与首尾空白规范化、空字符串清空/无效422。settings.write、当前用户锁、原ProfileWriteQuota、settings.personality幂等事务，仅更新mbti/时间；旧资料及注册规则保持。上一条待确认状态现已关闭，具体测试与人工链路见development-plan最新记录。


2026-10-20并行整理：当前可达v3入口181条（180HTTP+1WS），完整METHOD/path、源码/测试/运行验收状态及剩余用户能力以[api-v3-progress.md](api-v3-progress.md)为当前索引。新接文章热度、表情4GET和红包类型目录，具体实现/验证限制见development-plan本批记录及两个work文档；不能再沿用历史矩阵漏项或把声明/测试适配器计为完整应用验收。

2026-10-20第二轮子代理已接线职业、勋章、会员6个GET；当前可达v3注册187条（186 HTTP+1 WS），最新逐项清单与剩余写入范围见 [api-v3-progress.md](api-v3-progress.md)。OAuth登记已由用户确认仅管理员登记维护，管理HTTP尚待实现。

2026-10-20第三轮：提交6231c4f0已推送origin/api_v3；其后接入主职业/勋章显示PUT及管理员OAuth列表/详情/登记，共192条可达注册（191 HTTP+1WS）。已确认20回调/2048上限并落实；停用及回调/权限修改仍未完成。最新状态以progress为准。

2026-10-20第四轮：并行接线本人匿名文章/评论、邀请码状态、管理员客户端停用与完整修改5接口；当前197条（196HTTP+1WS），最新清单见progress。用户确认权限/回调集合实际变更使全部旧Token/码/pending授权失效；仅名称或集合顺序变化保留授权，不开放重新启用。匿名列表仅本人元数据，邀请码unused不等于当前可注册。实际文件、隔离证据和未完成运行验收见development-plan及各work文档；本批仍未提交。

2026-10-22：第五轮接入本人职业详情/90天记录两GET、引导/隐私两PUT和勋章相邻移动POST；当前202条（201HTTP+1WS）。主代理隔离专项7/11/8组通过，真实运行未验收，本批未再提交。会员配置/开通已发出三条规则确认，收费与权益不擅自改动。最新清单及缺口见progress，文件/人工链路见development-plan及本批三份work。

2026-10-22第六轮：会员三项规则已获确认，展示配置PUT和积分开通POST已接线，当前204条（203HTTP+1WS）。主代理专项10/12组通过，新买方主账/券/礼包/回执同事务，仅开通使用完整服务端defaults。旧开通仅补账户对前置锁，不改原收费与字段。先迁移20261022_api_v3_membership.sql积分冗余索引，真实运行与全站反漏扫仍待验；本批未提交。


2026-10-22第七轮：职业成长提示、排行及用户公开职业资料三GET已总接线，当前207条（206HTTP+1WS）。排行先筛可见人群、连续编号；隐藏经验仍显示等级名次，公开资料跨模块省略经验，rarity/ranking使用同一可见人群。成长提示承认展示revision与等级scheme独立生命周期；公开资料检查十模块和当前公开来源权限。隔离与运行验收分别以progress/交接文档为准，未执行Maven、真实MySQL或完整应用，本批未提交。


2026-10-22第八轮：复读机items/next两GET、本人常用表情PUT、本人邀请码购买POST已注册，当前211条（210HTTP+1WS）。用户确认表情严格目录/空数组恢复默认，以及邀请码仅邀请模式/余额足够才原子扣费生成。复读保留前50池语义；购码同key恢复原code，不在账本/日志字段存码，旧入口保持。主代理专项6/7/9组通过；源码与运行验收分列，无新迁移/前端，仍需既有积分来源/索引迁移和真实MySQL/完整应用验收。本批未提交。


## 2026-10-23 第九及第十轮接线

第九轮六入口达到217条，主代理专项转账9、举报9、复读写9组退出0。独立MySQL8.4.11三项RR当前读/回滚/目标锁串行SQL通过，未接触业务库，不代替完整Java事务MySQL并发。完整文件与人工链见[转账](api-v3-point-transfer-work.md)、[举报](api-v3-report-work.md)、[复读写](api-v3-repeater-write-work.md)。

第十轮先接历史四GET及分组三写，达到224条（223HTTP+1WS）；主代理复跑历史8组、分组7组退出0。实际文件、权限与失败边界、完整人工步骤见[历史](api-v3-revision-work.md)及[分组](api-v3-emoji-group-work.md)。图片成员/鱼游投稿正在实现，未计总register；敏感设置已确认第一方及密码/TOTP证明，其余策略待答。

当前未再次提交，未执行Maven，无前端修改或构建。完整IoC/HTTP、应用MySQL、PC/mobile及全站反漏扫未验收；联调前用户须通过IDEA重新编译或重启。API.md已更新，发布时还需同步网站API文章。


第十轮续：图片成员四路由及鱼游投稿/编辑两路由已总接线，当前230条（229HTTP+1WS）。主代理最终复跑历史9、分组7、成员8、鱼游7组全部退出0。历史增加真实SHA2输入计量和预算前置；鱼游先执行`sql/20261023_api_v3_fish_game_submission.sql`，旧审核只适配OAuth pending合并/清理，不宣称新旧写入整体串行。实际新增与改动文件及完整人工链见[成员](api-v3-emoji-item-work.md)、[鱼游](api-v3-fish-game-submission-work.md)，前述历史/分组work同步更新。真实运行仍未验收。


第十一轮（2026-10-23）：图片分享/导入/历史迁移三POST总接线，233条。主代理transfer7组、共享保存点修复后group7及item8组最终退出0。实际文件与完整入口/核心操作/成功及错误边界见[分享与迁移](api-v3-emoji-transfer-work.md)。修复`ApiV3TransactionSavepoint`嵌套primitive已中止根时的二次回滚错误覆盖；无新表或前端。当前credential底座和敏感设置源码仍在实施，未作为可用入口；真实应用与全站反漏扫未验收。


## 2026-10-23 第十二轮：敏感设置及全凭据撤销

安全/MFA/联系方式九路由已总接线，当前242条（241HTTP+1WS）：GET121/POST55/PUT41/DELETE24/WS1。主代理最终security6、mfa9、contact8、mail3、credential10、Cookie3组及既有chat-event综合157条PASS退出0；生产日志过滤XML/真实Latke解析专项通过。文件、部署顺序、完整人工用户链及未验证项见[凭据底座](api-v3-account-credential-work.md)、[安全](api-v3-security-work.md)、[MFA](api-v3-mfa-work.md)、[联系方式](api-v3-contact-work.md)、[邮件](api-v3-account-mail-work.md)。

独立epoch避免旧User整行写回退撤权；敏感变更与密码重置同时撤Cookie/apiKey/旧OpenID/v3/code/pending。WS认证与资源释放经独立检查，关闭清理不重新鉴权。三份SQL和repository.json已齐备，未执行业务迁移；需用户IDEA重编译/重启后实际MySQL/IoC/PC/mobile/供应商/多实例和全站反漏扫验收。无前端修改，无Maven，不作整个未提交工作区发布验收通过声明。

本批结束时普通积分改名、游戏云存档、导出、红包及公开支付继续保留在范围（后续进展见下一批）；云存档两项规则已向用户发出，其他待确认建议见cloud-export-plan及chat-payments-work。


## 2026-10-24 第十三轮：普通积分改名

PUT `/api/v3/me/settings/user-name`已接入总register，当前243条（242HTTP+1WS）：GET121/POST55/PUT42/DELETE24/WS1。沿既有UserNameRules、ProfileWriteQuota、Censor与Pointtransfer费用，双activity.write/settings.write及幂等，改名/扣费/回执同严格事务；旧`/settings/username`本批不改造，不宣称新旧整个扣费链原子化。积分记录新格式与旧格式兼容读取另作专项验证。

当前配置`pointChangeUsername=1000`，隔离fixture的100仅为测试值。文件清单、带列长触发器的最终隔离6组及证据边界、完整人工用户链和运行限制见[普通积分改名工作记录](api-v3-user-name-work.md)。仍需IDEA重编译/重启后真实MySQL/IoC/PC/mobile及全站反漏扫验收。游戏云存档、导出、红包及公开支付继续保留在后续范围。

第十三轮最终核对：主代理改名runner带列长触发器6组退出0（`/tmp/rhythm-v3-user-name-root-final.log`）；全仓生成790路由/398浏览器引用，总register243条与进度表逐项一致、无重复。真实完整getUserPoints/MySQL/浏览器未验收，细节见user-name-work。

## 2026-10-24 第十四轮：样式、捐助页、私有导出与三类红包

已总接线8入口：会员样式GET、捐助页GET、本人导出POST/GET/下载GET、红包创建POST/详情GET/领取POST。当前251条（250HTTP+1WS），GET126/POST58/PUT42/DELETE24/WS1，最终递归register校验另记progress。会员样式仅指定ID批量/三展示字段；捐助仅当前站点页面链接；完整本人导出100积分/24h私有DB块，先迁移20261024_api_v3_post_export.sql；三类红包持久分配/原子资金及旧领取桥接，先迁移20261024_api_v3_chat_red_packet.sql，未领完禁删除，旧历史不变。无前端改动，无Maven/服务端或业务库联调。

真实执行证据：style5组、donation3组、export10组、红包9组；真实Latke/XML敏感解析日志过滤含export通过；普通改名另增真实MySQL8.4.11专项5组/231条FOR UPDATE，费用实际1000。红包触及共享聊天删除，chat-event/publication回归通过；最终重复数以根日志与各work为准，不把隔离测试等同全服务。完整文件/人工链分别见membership-style-work、donation-work、post-export-work、chat-red-packet-work与user-name-mysql-work。

子代理因配额停止后根接手导出测试/接线/文档及红包编译修复/事务专项/接线。未完成整体v3目标：真游戏存档规则及旧客户端桥接待答，心跳/猜拳另行设计，OAuth非浏览器风控凭据契约、独立website_v3客户端仓库迁移和真实部署全链验收仍在范围。不提交/推送；公开API.md需同步网站文章。

## 2026-10-24 第十五轮：原生OAuth协议准入

用户批准两个协议端点以有效绑定凭据证明身份。ApiV3OAuthProcessor接只读CredentialQuery，原privacy/rate/global Before黑名单保持；无效仍原anonymous，不设通用身份或IP白名单。正确绑定重放仍进原核心撤家族，最终写根重验。251条不变，原回归157组与新定向6组退出0；真实浏览器/native/完整匿名解封未验，完整文件及人工链见oauth-protocol-work。第十四轮两SQL已在一次性MySQL8.4.11执行/复执行、核四表元数据/索引及样本回滚两组通过，专用容器已删，不代用户执行业务迁移。
