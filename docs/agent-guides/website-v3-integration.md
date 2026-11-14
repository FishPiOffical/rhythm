# website_v3 与 Rhythm 联调

本文件是 `rhythm` 会话进行摸鱼派 v3 用户站联调、检查和优化时的跨仓库约定。它只记录可复用的仓库边界、入口和验证方法，不替代两边各自的 `AGENTS.md`、README 或接口专题。

## 两个仓库

- 后端仓库：`C:\Users\陈辉\IdeaProjects\rhythm`，远端为 `git@github.com:FishPiOffical/rhythm.git`；本地开发分支以当前工作区为准，不把某次会话的分支名当作长期协议。
- 用户站仓库：`C:\Users\陈辉\IdeaProjects\fishpi_website_v3`，远端为 `https://github.com/FishPiOffical/fishpi_website_v3`；开始任务时核对当前分支，不假定分支始终不变。
- 两个目录是独立 Git 仓库。跨仓库任务可以在当前 `rhythm` 会话中同时只读核对或按授权修改，但必须分别检查、分别提交，不能把 website_v3 文件放进 Rhythm 提交。
- website_v3 自有入口 `C:\Users\陈辉\IdeaProjects\fishpi_website_v3\AGENTS.md` 和 README；开始涉及前端时必须先读它们及当前 `git diff`，保留其他协作者的修改，不得顺带提交。

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
  - **全局匿名风控**：`BeforeRequestHandler`/`AnonymousViewCheckMidware` 仍可将受限请求导向 `/test`；website_v3 的 `RiskCaptchaGate` 挂载 GeeTest 4，成功后调用现有 `POST /validateCaptcha`，再只重试原请求一次。v3 Bearer 不能替代这条 IP 风控链。
  - **匿名账号流程**：`GET /api/v3/auth/captcha-options` 返回 `provider=geetest4` 和公开组件 ID；注册、找回密码的短信申请接口要求四字段极验证明，Rhythm 服务端再次向极验校验，不能只做前端组件成功判断。
  - **第一方联系方式**：`POST /api/v3/me/settings/phone/verification` 与 `/email/verification` 要求当前证明和极验结果；website_v3 的 `OwnerSecurity` 使用同一 GeeTest 4 helper，Rhythm 仍在服务端校验。
- 宠物账号风险另有 v3 challenge/verify（`/api/v3/me/pet/risk/challenge`、`/verify`）：挑战绑定账号、操作和幂等键，后端使用独立 `ApiV3PetCaptchaService` 向 GeeTest 4 校验；它不是全局匿名盾，也不是直接复用 `/validateCaptcha`。
- v3 登录请求本身只提交用户名/邮箱、密码摘要和可选 MFA，不包含旧站图片验证码字段；不要因旧 `/captcha/login` 仍存在，就把它当成 v3 登录契约的一部分。是否出现全局 `/test` 风控挑战，仍由 IP/匿名盾链路决定。
- 仅静态接线或本地夹具不能证明真实极验可用。涉及验证码的联调必须分别验证组件加载、服务端二次校验、错误/供应商故障、黑名单解封、原请求单次重试，以及 PC/移动端；未经阿达明确允许，不修改、绕过或重建验证码盾指纹。

### 首页验证码与暂停提示的判断

- 代码和路由中实际登记的是 `/test`，没有 `/testMa`；若浏览器地址栏确实出现 `/testMa`，需要另查生产网关、浏览器扩展或输入/截图记录，不能据当前两个仓库推断该路径属于应用。
- 旧站访问 `/` 时，`BeforeRequestHandler` 会在首页处理前检查 IP；命中验证码黑名单就直接重定向 `/test`，尚未进入首页模板。
- website_v3 首页 SSR 会预取多个公开 v3 资源；服务端请求同样经过 Rhythm 的全局入口。SSR 对 `/test` 的重定向只记录为 captcha 并返回空的 SSR 载荷。浏览器端普通请求本来会由 `src/api/http.ts` 的 `ensureRiskCaptcha()` 打开 `RiskCaptchaGate`，但首页 `queueHomeRead` 当前先把 captcha 纳入 `homeReadPause`，转换成 `home_reads_paused` 后才抛给页面，因此不会自动跳转 `/test` 或弹出极验，而是显示“首页读取仍在暂停中”及重试按钮（截图所示）。重试逻辑只等待暂停窗口结束并再次读取，不能替代验证码恢复链路。
- 这属于 website_v3 首页读取队列的错误处理问题，不代表 v3 删除或绕过了极验；修复时应让 captcha 继续冒泡到既有 `ensureRiskCaptcha()`，保留 401、429、offline 等其他首页暂停策略，并在验证成功后只重试原首页读取一次。该修复涉及 website_v3 代码，须先完成开发准入确认，不能在 Rhythm 文档维护阶段直接改动验证码盾核心。
- 该判断按 IP 生效，不按“旧站/新站”区分；同一出口 IP 上任一站触发或保留黑名单，另一站访问也会受到影响。完成 GeeTest 4 后，`POST /validateCaptcha` 清理验证码盾状态；Firewall/ipset 等其他封禁不由该接口自动解除。

## 跨仓库工作顺序

1. **确认范围**：先读本文件、两边根 `AGENTS.md`、相关 README 和命中专题；检查两个仓库的分支、远端、`git status --short` 与当前 diff，保留无关改动。
2. **追调用链**：在 Rhythm 从 `Router#requestMapping`、Processor/Service、`API.md` 和 V3 访问/契约专题确认真实路由、鉴权、响应白名单、事务和幂等；在 website_v3 找对应 `src/api/v3-*`、store/composable、视图、SSR 预取和实时订阅。不要只改一侧或以旧接口名称猜测契约。
3. **开发准备**：后端代码或 FTL 变化先由开发者在 IDEA 按约定重新编译/重启；前端环境目标只放本地未跟踪环境文件，启动 5173，并确认端口归属，不擅自结束其他进程。
4. **真实链路验证**：先用无凭据请求确认服务、路由和非敏感响应，再按授权准备登录状态验证 Bearer/刷新 Cookie、失败/权限边界、幂等回放和 WebSocket。涉及写操作、支付、账号设置、短信或验证码时不得用假成功或随意修改真实账号。
5. **前端验收**：执行与改动范围匹配的 website_v3 专项脚本、`npx vue-tsc -b` 或 `npm run build`；涉及页面时开发者必须亲测桌面和窄屏，检查 SSR 首帧、水合、加载/空态/错误/重试、身份切换和断网边界。前端构建后强刷。
6. **交付与提交**：记录实际改动、命令和结果、未覆盖风险及人工步骤；Rhythm 与 website_v3 分别提交，中文提交消息，未经明确要求不推送。未完成开发者亲测只能标记为“待人工验收”，不能宣称可合入或发布。

## 常用核对入口

- Rhythm：`API.md`、`UPDATE_LOG.md`、`docs/agent-guides/api-contracts.md`、`docs/agent-guides/security-access.md`、`docs/api-v3-current-completion-audit.md`、`tools/api-v3-contract-audit.mjs`。
- website_v3：`README.md`、`AGENTS.md`、`src/api/http.ts`、`src/api/fishpi.ts`、`vite.config.ts`、`server.js`、`tools/test-v3.mjs`、`tools/v3-client-boundary-audit.mjs`、`docs/V3_MIGRATION.md`。
- 跨仓库路由对照：先由 Rhythm 生成当前 contract audit JSON，再在 website_v3 使用 `tools/v3-route-parity-audit.mjs` 与 `tools/v3-route-builder-probe.mjs` 的 `--routes` 读取；静态候选、未知转发或只截停 transport 不算运行通过。
- 认证代理回归：优先使用 website_v3 已有的 `tools/v3-credential-proxy-test.mjs` 及对应会话专项；不在规则文档、日志或测试材料中保存任何密码、Cookie、Token、验证码或其片段。

## 维护边界

- 本文件记录的是跨仓库长期入口和验证原则；单次联调结果、临时端口、账号安排、日志和截图放 `.codex-tasks/` 或对应专项记录，不写成当前事实。
- 发现 website_v3 自有规则与本文件冲突时，先以用户最新明确要求和当前真实实现为准，再在相应仓库的专题文档中修正，不在 Rhythm 文档复制整份前端规则。
- 仅维护本文件、根 `AGENTS.md` 和专题索引不代表已完成任何 API、前端或生产验收；代码行为变化仍须执行开发准入、跨仓库真实链路和人工验收门禁。
