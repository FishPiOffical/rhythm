# website_v3 与 Rhythm 联调

本文件是 `rhythm` 会话进行摸鱼派 v3 用户站联调、检查和优化时的跨仓库约定。它只记录可复用的仓库边界、入口和验证方法，不替代两边各自的 `AGENTS.md`、README 或接口专题。

## 两个仓库

- 后端仓库：`C:\Users\陈辉\IdeaProjects\rhythm`，远端为 `git@github.com:FishPiOffical/rhythm.git`；本地开发分支以当前工作区为准，不把某次会话的分支名当作长期协议。
- 用户站仓库：`C:\Users\陈辉\IdeaProjects\fishpi_website_v3`，远端为 `https://github.com/FishPiOffical/fishpi_website_v3`；开始任务时核对当前分支，不假定分支始终不变。
- 两个目录是独立 Git 仓库。跨仓库任务可以在当前 `rhythm` 会话中同时只读核对或按授权修改，但必须分别检查、分别提交，不能把 website_v3 文件放进 Rhythm 提交。
- website_v3 自有入口 `C:\Users\陈辉\IdeaProjects\fishpi_website_v3\AGENTS.md` 和 README；开始涉及前端时必须先读它们及当前 `git diff`，保留其他协作者的修改，不得顺带提交。

## 前端同步与 PR 交付

- 用户于 2026-11-15 明确要求：动 website_v3 前端之前先 pull；修改以后，须经用户同意再通过 PR 交付，不直接 push 目标分支。Rhythm 后端仍按原有约定处理。
- 开始修改前先核对分支、上游、`git status --short` 和差异，再执行适合当前分支的拉取；优先 `git pull --ff-only`，存在分叉、冲突或失败时如实暴露，先解决同步问题，不能擅自丢弃本地改动、强制重置或自动 stash 掩盖冲突。
- 拉取后重新检查受影响源码；在独立工作分支实施（新建分支默认 `codex/` 前缀），本地提交按明确授权处理。完成适用验证并向用户展示实际改动与未验项后，等待用户同意远端交付。
- 用户同意后推送工作分支并创建面向目标分支的 PR，返回真实 PR 地址；创建后附加到当前会话。不直接推 `main` 等目标分支，不自动合并；创建 PR 不代表验收通过。
- 此规则优先于前端仓库中可能存在的旧提交习惯；自动本地 commit 不等于已授权推送/建 PR。不得将跨仓库任务中的前端 PR 要求扩大成后端也必须建 PR。

## 当前运行拓扑

- Rhythm 当前开发地址以用户确认的 `http://localhost:8080` 为准；生产地址为 `https://fishpi.cn`。Java/FTL 改动联调前仍须按环境专题提醒开发者在 IDEA 重新编译或重启。
- website_v3 开发服务器固定 `http://127.0.0.1:5173`，推荐运行 `npm run dev`（Express + Vite SSR）；`npm run dev:spa` 仅用于不需要 SSR 的场景。Node.js 要求以 website_v3 `package.json` 为准（当前为 `>=24.12`）。
- 本地浏览器链路通常是 `127.0.0.1:5173` → Vite/Express 代理 → `http://localhost:8080` 的 Rhythm `/api/v3`。在 website_v3 的本地环境文件中设置 `VITE_API_TARGET=http://localhost:8080`；不要把本地目标、账号信息或凭据提交到仓库。
- 联调两端统一使用 `localhost` 或统一使用 `127.0.0.1`。不同主机名不会共享 host-only Cookie；浏览器实际访问地址、`VITE_API_TARGET` 和验证步骤必须写清楚，不能用“看起来相同”的主机名代替。
- website_v3 的 `server.js` 在 SSR 读取 `VITE_API_TARGET`，并为 `/api/v3/realtime` 转发 WebSocket；`vite.config.ts` 为 `/api/v3`、登录/刷新/退出和实时通道配置代理。涉及认证来源时不得合成或改写浏览器的 Host、Origin、Referer。
- website_v3 仓库历史材料中仍可能出现 `8081` 等旧环境描述；它们不是当前运行事实。以用户最新确认、当前监听进程和无凭据探测结果为准，不能据历史文档停止其他项目或更改端口。

## 协议边界

- website_v3 `main` 仅支持 Rhythm v3；请求路径使用 `/api/v3`，不要恢复旧 `/api/`、旧 `apiKey`、版本切换或旧页面 Cookie 桥接。
- 浏览器访问令牌只在内存中使用 `Authorization: Bearer ...`；刷新凭据由 HttpOnly Cookie 管理。不得把 Bearer 放进旧 `apiKey` 参数、URL、localStorage、源码或日志。刷新/退出/登录请求的 Cookie、Origin 和 Referer 语义必须保持真实浏览器行为。
- Rhythm 旧接口的 Sessions/apiKey 身份边界与 V3 Bearer 身份边界不可混用。新增或修改 V3 接口时同步评估鉴权、scope、越权、CSRF、公开字段脱敏、幂等和错误语义，并更新 `API.md`、`UPDATE_LOG.md` 及命中的接口专题。
- website_v3 的 API 适配器位于 `src/api/`，认证与请求基础设施主要在 `src/api/http.ts`、`src/api/fishpi.ts`；页面/状态分别位于 `src/views/`、`src/components/`、`src/stores/`。先核对适配器、调用方和 SSR 入口，再决定改后端契约还是前端投影。
- SSR 读取与浏览器读取是两条验证面：服务端可读不等于浏览器凭据、CORS、Cookie 或 WebSocket 正常；浏览器夹具、静态审计和构建通过也不等于真实账号链路已验收。

## 极验链路

- 旧站存在两类不同验证码：登录失败后的 `/captcha/login` 是服务端图片验证码；匿名访问风控、注册、找回密码、绑定手机/邮箱及验证码盾页面使用 GeeTest 4（`gt4.js`），服务端统一由 `AnonymousCaptchaShield.verifyProof` 按 `jiyan.id`、`jiyan.key`、`jiyan.domain` 验证，敏感配置不进入前端或文档。
- v3 没有消除极验，而是拆成三条链：
  - **全局匿名风控**：`BeforeRequestHandler`/`AnonymousViewCheckMidware` 对 V3 返回 HTTP 403 `captcha_required`，旧页面继续跳转 `/test`；website_v3 的 `RiskCaptchaGate` 挂载 GeeTest 4，成功后调用现有 `POST /validateCaptcha`，再只重试原请求一次。v3 Bearer 不能替代这条 IP 风控链。
  - **匿名账号流程**：`GET /api/v3/auth/captcha-options` 返回 `provider=geetest4` 和公开组件 ID；注册、找回密码的短信申请接口要求四字段极验证明，Rhythm 服务端再次向极验校验，不能只做前端组件成功判断。
  - **第一方联系方式**：`POST /api/v3/me/settings/phone/verification` 与 `/email/verification` 要求当前证明和极验结果；website_v3 的 `OwnerSecurity` 使用同一 GeeTest 4 helper，Rhythm 仍在服务端校验。
- 宠物账号风险另有 v3 challenge/verify（`/api/v3/me/pet/risk/challenge`、`/verify`）：挑战绑定账号、操作和幂等键，后端使用独立 `ApiV3PetCaptchaService` 向 GeeTest 4 校验；它不是全局匿名盾，也不是直接复用 `/validateCaptcha`。
- v3 登录请求本身只提交用户名/邮箱、密码摘要和可选 MFA，不包含旧站图片验证码字段；不要因旧 `/captcha/login` 仍存在，就把它当成 v3 登录契约的一部分。是否出现全局 `/test` 风控挑战，仍由 IP/匿名盾链路决定。
- 仅静态接线或本地夹具不能证明真实极验可用。涉及验证码的联调必须分别验证组件加载、服务端二次校验、错误/供应商故障、黑名单解封、原请求单次重试，以及 PC/移动端；未经阿达明确允许，不修改、绕过或重建验证码盾指纹。

### V3 验证码盾的两层范围

- **已有验证码黑名单：所有经过全局 HTTP 入口的 V3 路由。** `BeforeRequestHandler` 在路由鉴权、CORS/OPTIONS 之前调用 `redirectIfChallenged`；盾启用且 IP 已受限时，已核实蜘蛛及 `/test`、`/validateCaptcha` 保持原豁免；V3 业务返回 HTTP 403 `captcha_required`，其他旧请求继续重定向 `/test`。因此本人资源、写入、登录/刷新及极验配置接口均受此限制，合法 Bearer 也不能越过这一层。携带 Origin 的 V3 OPTIONS 只返回 204 协商跨域，UA/CC 检查仍在前且不执行路由、不解封；`/validateCaptcha` 的预检和提交可达。实时 WebSocket 另外在接入、消息及存活检查中检查同一 IP 限制，不依赖 HTTP 跳转恢复。
- **匿名首访/每五次检查：只对挂载 `AnonymousViewCheckMidware` 的路由执行。** 下表按当前 `ApiV3*Processor` 的直接挂载归类；所有路径均以 `/api/v3` 开头，花括号表示路由参数，不能把某个分类前缀理解为其全部子路由都挂载了该中间件。

| 方法/分类 | 路径后缀 |
| --- | --- |
| POST 网页认证 | `/auth/login`、`/auth/refresh`、`/auth/logout`、`/auth/session` |
| GET 账号准备 | `/auth/registration-options`、`/auth/captcha-options` |
| POST 注册/找回 | `/auth/registration/request`、`/auth/registration/complete`、`/auth/password-reset/request`、`/auth/password-reset/complete` |
| POST 应用登录 | `/auth/app-login/claim`、`/auth/app-login/app-status`、`/auth/app-login/app-decision`、`/auth/app-login/exchange` |
| GET OAuth | `/oauth/authorize` |
| GET 文章/评论 | `/articles`、`/articles/random`、`/articles/{articleId}`、`/articles/{articleId}/comments`、`/articles/{articleId}/paragraphs`、`/articles/{articleId}/paragraph-comments`、`/comments/{commentId}`、`/comments/{commentId}/location` |
| GET 专栏 | `/columns`、`/columns/hot`、`/columns/{columnId}`、`/columns/{columnId}/articles`、`/articles/{articleId}/column` |
| GET 搜索/标签/领域 | `/search`、`/tags`、`/tags/{tagId}`、`/tags/{tagId}/articles`、`/domains`、`/domains/{domainId}`、`/domains/{domainId}/tags`、`/domains/{domainId}/articles` |
| GET 公开用户/关系/内容 | `/recent-members`、`/users/{userName}`、`/users/{userName}/comments`、`/users/{userName}/articles`、`/users/{userName}/breezemoons`、`/users/{userName}/watching`、`/users/{userName}/watching/articles`、`/users/{userName}/following/users`、`/users/{userName}/following/tags`、`/users/{userName}/following/articles`、`/users/{userName}/followers` |
| GET 清风/复读机 | `/breezemoons`、`/breezemoons/{breezemoonId}`、`/repeater/items`、`/repeater/next` |
| GET 社区/榜单 | `/fish-games`、`/fish-games/{gameId}`、`/fish-games/{gameId}/comments`、`/milestones`、`/milestones/{milestoneId}`、`/statistics`、`/cities/{cityName}/users`、`/cities/{cityName}/articles`、`/rankings/{type}`、`/pets/{petId}/profile` |
| GET 职业/勋章/外观 | `/professions`、`/users/{userName}/professions`、`/users/{userName}/professions/summary`、`/users/{userName}/medals`、`/users/{userName}/medals/image`、`/appearance-shop` |
| GET 表态 | `/reactions/emojis`、`/articles/{targetId}/reactions`、`/comments/{targetId}/reactions` |

- `POST /oauth/token`、`POST /oauth/revoke` 另有条件调用：协议凭据预检未确认有效所有者时进入匿名检查；有效协议凭据走原协议核验与限流，不建立 Bearer 身份或 IP 白名单，也不能越过全局黑名单。
- 可选鉴权路由先验证显式 Bearer，建立有效 V3 身份后不进入匿名计数；旧 Cookie/apiKey 不作为 V3 身份。登录、刷新等未在匿名中间件前建立 V3 身份的入口，仍按匿名链路判断，不能仅因请求携带凭据就认定免检。
- 未获豁免时，计数按同 IP 到达匿名检查的请求累加，不按页面、接口分别计算；旧站与 V3 共用进程内状态。首个非文章请求在首访开关开启时触发，之后每 5 次触发。文章首访豁免只解析旧 `/article/{id}` 路径，**不覆盖** `/api/v3/articles/{articleId}`。白名单、同 IP 用户缓存和已验证蜘蛛等按核心既有顺序处理；通过验证码的两小时记录只豁免首访，不豁免每五次规则。
- V3 触发首访/周期规则后写入与旧站共用的验证码黑名单，返回统一 HTTP 403 JSON `{code:"captcha_required",msg:...,data:null}`，并带 no-store 与适用 CORS；后续业务持续拒绝且不继续计数，直到服务端真实验证通过。旧 `/api/` 分支仍保留普通401、不加黑的行为；非API累计300次封禁规则未变。不要把所有401或403当验证码。
- 2026-11-15 前 V3 仅拒绝首访/周期的当次请求，返回普通401、不加黑，第2～4次等可继续；这是本轮已替代的行为，不再作为现行协议。路由覆盖和按请求计数没有扩充或重设计；多实例、计数竞态及第三方完整矩阵仍见工作单待办。
- 覆盖核对须从当前 `Router` 可达注册生成清单，再区分强制 Bearer、可选 Bearer+匿名检查、匿名检查、OAuth 与 WebSocket。未挂匿名检查的强制登录路由不自动属于遗漏；静态路由归类不能替代真实 HTTP/WS 验证。`tools/api-v3-route-access-audit.mjs` 当前仅识别 `anonymous::handle` 形式，公开勋章图片使用 `beans.getReference(AnonymousViewCheckMidware.class)::handle` 会被误报缺少 gate，必须回源码复核，不得据此删除检查或把审计失败写成通过。
- `POST /validateCaptcha` 的服务端极验二次校验成功后才清验证码黑名单与访问计数，并记录通过时间；不清 Firewall/ipset、不加白。缓存为实例进程状态，重启/多实例及并发计数限制见 [验证码盾说明](../captcha-shield.md)。开发模式启动默认关盾；生产实际开关、来源 IP 与运行版本须另行核实。SSR 启动加白仅针对 `new.fishpi.cn` DNS 解析到的来源地址，不能据此推断浏览器用户 IP 也免检。

### 首页验证码与暂停提示的判断

- 验证码入口是 `/test`；用户已确认早先的 `/testMa` 为输入法误写，不另作应用路径排查。
- 旧站访问 `/` 时，`BeforeRequestHandler` 会在首页处理前检查 IP；命中验证码黑名单就直接重定向 `/test`，尚未进入首页模板。
- website_v3 首页 SSR 会预取多个公开 v3 资源。`ssrUpstreamGate` 对验证码保持上游退避原因；当前渲染遇到挑战时在载荷写 `upstreamCaptcha`，浏览器挂载 `RiskCaptchaGate` 后用自己的请求核实。只有浏览器实际收到挑战才打开验证；SSR服务器受限不代表访客受限，浏览器验证成功也不替SSR服务器解封。共享缓存的合法成功内容沿原缓存规则保留，失败字段保留未知/错误，不以空数组假成功。
- `src/api/http.ts` 将统一恢复放在首页队列任务内部，验证码不再转成 `home_reads_paused`。同页共享验证屏障，验证提交不入首页/凭据队列；原任务原地最多重试一次。成功版本防止迟到旧响应重复开窗，失败版本使取消时已经排队的一批请求结束，新的显式操作可再次验证。普通401/429/断网沿既有错误策略。
- 身份版本、AbortSignal及首页代次约束仍有效：验证期间换号或取消的原请求不重发；写操作只对明确前置挑战恢复，保留原请求体和幂等键，未知提交结果不自动重发。`RiskCaptchaGate` 隔离关闭/卸载后的迟到证明与旧组件回调；供应商加载/校验失败保留错误，用户可继续交互或取消，不假解封。共享仅限当前标签页正在进行的一次验证。
- `fp:captcha-passed` 只通知此前被验证码阻断的实时订阅重新申请票据，仍须WebSocket认证及ready；不复用旧票据，不将验证码403当文章删除。`fp:retry-public` 只重读当前公开资源，不重发写操作。自动回归不能代替真实极验、代理/IP与PC/移动验收，具体步骤及未完项见[工作单](../api-v3-captcha-flow-work.md)。
- 该判断按 IP 生效，不按“旧站/新站”区分；同一出口 IP 上任一站触发或保留黑名单，另一站访问也会受到影响。完成 GeeTest 4 后，`POST /validateCaptcha` 清理验证码盾状态；Firewall/ipset 等其他封禁不由该接口自动解除。

## 跨仓库工作顺序

1. **确认范围**：先读本文件、两边根 `AGENTS.md`、相关 README 和命中专题；检查两个仓库的分支、远端、`git status --short` 与当前 diff，保留无关改动。修改前端前按上节先 pull；验证码流程恢复另读 [已确认方案与待办](../api-v3-captcha-flow-work.md)。
2. **追调用链**：在 Rhythm 从 `Router#requestMapping`、Processor/Service、`API.md` 和 V3 访问/契约专题确认真实路由、鉴权、响应白名单、事务和幂等；在 website_v3 找对应 `src/api/v3-*`、store/composable、视图、SSR 预取和实时订阅。不要只改一侧或以旧接口名称猜测契约。
3. **开发准备**：后端代码或 FTL 变化先由开发者在 IDEA 按约定重新编译/重启；前端环境目标只放本地未跟踪环境文件，启动 5173，并确认端口归属，不擅自结束其他进程。
4. **真实链路验证**：先用无凭据请求确认服务、路由和非敏感响应，再按授权准备登录状态验证 Bearer/刷新 Cookie、失败/权限边界、幂等回放和 WebSocket。涉及写操作、支付、账号设置、短信或验证码时不得用假成功或随意修改真实账号。
5. **前端验收**：执行与改动范围匹配的 website_v3 专项脚本、`npx vue-tsc -b` 或 `npm run build`；涉及页面时开发者必须亲测桌面和窄屏，检查 SSR 首帧、水合、加载/空态/错误/重试、身份切换和断网边界。前端构建后强刷。
6. **交付与提交**：记录实际改动、命令和结果、未覆盖风险及人工步骤；Rhythm 与 website_v3 分别提交，中文提交消息。前端经用户同意后推工作分支并建 PR，不直接推目标分支；Rhythm 仍按原有授权推送。未完成开发者亲测只能标记为“待人工验收”，不能宣称可合入或发布。

## 常用核对入口

- Rhythm：`API.md`、`UPDATE_LOG.md`、`docs/agent-guides/api-contracts.md`、`docs/agent-guides/security-access.md`、`docs/api-v3-current-completion-audit.md`、`tools/api-v3-contract-audit.mjs`。
- website_v3：`README.md`、`AGENTS.md`、`src/api/http.ts`、`src/api/fishpi.ts`、`vite.config.ts`、`server.js`、`tools/test-v3.mjs`、`tools/v3-client-boundary-audit.mjs`、`docs/V3_MIGRATION.md`。
- 跨仓库路由对照：先由 Rhythm 生成当前 contract audit JSON，再在 website_v3 使用 `tools/v3-route-parity-audit.mjs` 与 `tools/v3-route-builder-probe.mjs` 的 `--routes` 读取；静态候选、未知转发或只截停 transport 不算运行通过。
- 认证代理回归：优先使用 website_v3 已有的 `tools/v3-credential-proxy-test.mjs` 及对应会话专项；不在规则文档、日志或测试材料中保存任何密码、Cookie、Token、验证码或其片段。

## 维护边界

- 本文件记录的是跨仓库长期入口和验证原则；单次联调结果、临时端口、账号安排、日志和截图放 `.codex-tasks/` 或对应专项记录，不写成当前事实。
- 发现 website_v3 自有规则与本文件冲突时，先以用户最新明确要求和当前真实实现为准，再在相应仓库的专题文档中修正，不在 Rhythm 文档复制整份前端规则。
- 仅维护本文件、根 `AGENTS.md` 和专题索引不代表已完成任何 API、前端或生产验收；代码行为变化仍须执行开发准入、跨仓库真实链路和人工验收门禁。

## 后台外部操作

- V3 IP、安全开关、搜索维护经 `AdminOperationTaskService` 持久受理后后台执行，客户端查询 `/api/v3/admin/tasks/{taskId}`；数据库提交不能代表外部成功。明确失败索引可原键重试，未知 IP/开关结果禁止自动重放。部署先执行 `sql/20261115_admin_operation_task.sql`；进程中断后的未知任务需人工核对，见 [恢复与验收](../admin-v3-compatibility-repair.md)。职业配置原始字节严格解析上限16 MiB，普通及批量写入不能沿用该上限。
