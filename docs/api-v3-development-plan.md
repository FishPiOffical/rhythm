# /api/v3 开发计划

## 已交付基础

`/api/v3` 已建立独立路由、统一响应信封、请求 ID、Bearer 身份中间件和 Token 存储。v3 不接受旧 `apiKey`、查询参数凭据或页面会话作为业务接口身份。

首批可用接口：

| 模块 | 接口 | 状态 |
| --- | --- | --- |
| 认证 | `POST /auth/login`、`/auth/refresh`、`/auth/logout` | 已实现（含认证事件审计日志） |
| 当前用户 | `GET /me` | 已实现 |
| 文章与评论 | `GET /articles`、`/articles/{articleId}`、`/articles/{articleId}/comments` | 已实现源码；待 IDEA 编译与运行回归 |
| 专栏 | `GET /columns`、`/columns/hot`、`/columns/{columnId}`、`/columns/{columnId}/articles` | 已实现（列表支持 `p`、`size`） |

所有路径均以 `/api/v3` 为前缀。响应统一为 `{ code, msg, data }`，列表统一为 `data.list` 与 `data.pagination`，页码参数为 `p`、`size`。

## 安全边界

- 公开读取路由挂 `AnonymousViewCheckMidware`，并在资源处理前执行可见性校验。
- 需要登录的资源只接受 `Authorization: Bearer <token>`，按 scope 进行授权。
- 写入操作必须使用 JSON 白名单校验、资源所有权校验、事务提交和幂等键。
- 所有响应必须使用 DTO 字段白名单；不得输出联系方式、凭据、IP、UA、会话和内部审核信息。
- 认证、刷新、退出和敏感写入均需记录不含明文凭据的审计事件。

## 迁移矩阵

> 下表为早期页面迁移快照，保留历史背景；当前接口实现、重新发现的缺口和运行证据以 [api-v3-progress.md](api-v3-progress.md) 为准。停用/回调维护、鱼游投稿、五类红包、捐助链接等已由后续批次完成源码接线，不能按此历史表重复立项。

| 优先级 | website_v3 页面能力 | v3 目标资源 | 状态 | 前置条件 |
| --- | --- | --- | --- | --- |
| P0 | 账号入口 | `auth/register-*`、`auth/password-reset-*`、`auth/login` | 登录已实现；OAuth授权码/同意页/换码/轮换撤销源码已接入；密码重置与注册完成入口已接入，v3找回短信/验证组件及注册短信申请已接入；应用登记已确认仅管理员登记维护，列表/详情/幂等登记已接入，停用/回调维护待实现 | 复用账号规则，独立 JSON 响应 |
| P0 | 文章与评论 | `articles`、`articles/{id}`、`articles/{id}/comments` | 读取与文章创建/部分编辑/删除、本人编辑源码和评论发布/编辑/删除、回复（含段评）、感谢、采纳、文章/评论投票及文章感谢/打赏读写已接入；其他管理/互动待实现；旧打赏正文返回保护已获批接入，运行待验证 | content.read/content.write/comments.write；匿名/讨论/打赏/私密评论边界 |
| P0 | 搜索、标签、领域 | `search`、`tags`、`domains` | 目录/详情/文章读取源码已实现，待运行验证 | 参数白名单和公开 DTO |
| P0 | 公开主页 | `users/{userName}` 及子页 | 资料、文章、评论、清风明月、关注用户/标签/收藏、粉丝、关注动态/订阅读取源码已实现，待运行验证 | 隐私设置与用户 DTO |
| P1 | 设置、通知、草稿 | `me/settings`、`me/notifications`、`me/drafts` | 设置/草稿读取、草稿写入、通知读写源码已实现；资料/隐私/显示设置写入源码已实现，功能/位置/语言和头像GET/PUT、MBTI读写及选项GET、常用/图片表情4个GET源码已实现；其他设置待实现 | scope、所有权、事务 |
| P1 | 私信 | `me/conversations`、`me/messages` | 会话/历史/详情/未读记录数、发送、指定/全部已读及本人撤回及私信专用实时取票/空变更提示源码已接入，待运行验收 | messages.read/messages.write、参与者权限、私有正文本地渲染 |
| P1 | 背包、签到、积分 | `me/bag`、`me/checkin`、`me/points` | 读取、昨日活跃度奖励领取及免签/补签/改名卡使用源码已实现；待运行验收 | 幂等键与积分事务 |
| P1 | 收藏、关注、订阅 | `me/follows`、`me/collections`、`me/subscriptions` | 列表/添加/取消源码已实现，待运行验证 | 所有权和重复操作处理 |
| P1 | 专栏管理 | `me/columns`、`columns/{id}` 写操作 | 公开/个人读取与管理写入源码已实现；待运行验证 | 所有权、幂等与事务 |
| P2 | 清风明月、鱼游、同城 | `breezemoons`、`fish-games`、`cities/{cityName}/articles` | 清风明月读/发布/编辑/删除/关注流、鱼游目录/详情/评论读取及投票/评论写入、同城读取源码已实现；投稿/编辑等待用户提供 OAuth 校验入口，其他写入待实现 | Markdown、投票与位置隐私 |
| P2 | 排行榜、统计、大事记 | `rankings/{type}`、`statistics`、`milestones` | 排行榜/统计/大事记公开及本人投稿读取源码已实现；投稿写入源码已实现；待运行验证 | DTO 和提交审核边界 |
| P2 | 上传、聊天室、支付跳转 | 上传票据、Socket 认证、受控跳转 | 上传票据、通知/文章Socket与聊天室历史/详情/上下文和持久化消息Socket源码已接入，待运行验收；文章热度/在线人数、聊天室在线名单、普通发送、话题/价格读取及新旧原子撤回源码已接入；话题持久化修改与弹幕发送源码已接入；红包类型目录已接入，红包写入/详情及支付跳转待实现 | 短期票据、专用 Socket 认证 |

管理后台、金手指、内部回调、游戏计分和支付管理保持在旧内部边界，不列入 v3 公网 API。

## website_v3 迁移清单

| 文件/模块 | 当前动作 | 完成条件 |
| --- | --- | --- |
| `src/api/http.ts` | 自动加 Bearer，401 单次刷新并重试 | 已完成 |
| `src/stores/auth.ts` | 内存 access token + 刷新恢复 | 已完成 |
| `src/api/fishpi.ts#login` | 改用 `/api/v3/auth/login` | 已完成 |
| `src/router/index.ts` | 首次导航恢复 v3 登录态 | 已完成 |
| 专栏首页与详情页 | 读取 `/columns*`，移除该页旧文章接口、`apiKey` 和 mock 降级 | 已完成 |
| 文章、评论、搜索页面 | 列表/搜索/目录与详情评论读取已迁移；互动及旧Socket待迁移 | 读取夹具验证，真实后端与写入待验 |
| 设置、通知、私信、聊天 | 移除 `pageAuth` 与 apiKey WebSocket | 待专用 v3 HTTP/Socket 认证上线 |
| 登录页提示 | 移除本地存储 apiKey 描述 | 已完成 |

## 验收矩阵

| 场景 | 操作 | 预期 |
| --- | --- | --- |
| 密码登录 | 以正确密码请求登录 | 返回 Bearer，随后 `/me` 成功 |
| MFA 登录 | 不带和带错误验证码登录已启用 MFA 的账号 | 分别返回 `mfa_required`、`invalid_mfa` |
| 登录恢复 | 刷新页面后调用 refresh | 恢复 `/me`，access token 不落地存储 |
| 失效刷新 | 使用已退出或失效的刷新状态 | 返回 401，前端清除本地用户状态 |
| 跨站刷新/退出 | 从非同源页面提交请求 | 返回 403 |
| Bearer 边界 | 带 Cookie 或旧 apiKey 调用 `/me` | 返回 401 |
| Scope | 使用缺 `profile.read` 的令牌调用 `/me` | 返回 403 |
| 专栏公开读取 | 匿名读取列表、详情和不存在的 ID | 返回 DTO；不存在返回 404 |
| 敏感字段 | 检查 `/me` 与专栏响应 | 不出现密码、邮箱、手机、凭据、IP、UA |

Java 代码改动后需要通过 IDEA 重新编译或重启服务端；每个后续模块合入前，还要补齐对应单元测试和 PC、移动端真实链路验证。

## 文章与评论读取验收补充

- 独立 `ApiV3ContentProcessor` / `ApiV3ContentQueryService`，DTO 不复用依赖 Sessions 的页面数据处理。
- 所有三条路由使用可选 Bearer 身份中间件，再执行匿名中间件；显式无效令牌为 401，缺 content.read 为 403；Cookie 和 apiKey 不赋予 v3 身份。
- 列表按查询条件分页计数，排除讨论帖、隐藏列表、沙盒和 B3log；匿名列表额外排除禁止匿名查看文章。普通列表排除长篇，长篇通过 type=long 获取。
- 文章与评论入口共享文章可见性，讨论帖限作者/管理员/被邀请者；失效文章、作者被封禁或永久停用返回 404。打赏正文仅作者或已打赏者可读。
- 私密评论正文限文章作者与评论作者；匿名发表不返回真实用户 ID/昵称/头像。所有内容响应禁止缓存。
- `ApiV3ContentUtilTest` 覆盖文章匿名/讨论权限、私密评论和作者 DTO 白名单；已使用 IDE 附带 Java 25 完成语法解析，权限/DTO Java 测试尚未执行。无需新增表或修改 SQL。
- IDEA 编译并重启后执行 `node tools/api-v3-content-smoke.mjs`；额外身份和文章场景见该脚本环境变量说明。生产风控开启时还须人工覆盖首访/每 5 次验证码、黑名单跳转与解封。

### 本批源码安全复核（运行验证待完成）

| 检查项 | 源码结论 / 待验证项 |
| --- | --- |
| 未鉴权 / 越权 | 三条路由均为 optionalContent + anonymous；身份仅来自 Bearer；详情/评论先校验文章、匿名开关及讨论邀请，再输出 DTO；待多账号运行验证 |
| CSRF | 本批新增资源全部为 GET，无业务写入；沿用既有 Token 使用记录事务；未改旧 Cookie/apiKey 写接口 |
| XSS | contentHtml 走 Markdowns.clean；content、title、tags 为数据文本，客户端需按契约渲染；待恶意载荷及两端展示验证 |
| 注入 | Query/PropertyFilter 参数化，排序和类型使用固定枚举；ID 仅数字，页码/条数校验并防偏移溢出；待异常参数运行验证 |
| SSRF | 未新增 URL 输入或请求任意 URL 能力；Markdown 复用站内已配置渲染服务 |
| 批量滥用 | 仍经过 BeforeRequestHandler 的 Firewall，分页最多 50 条；暂无本批独立 Token 维度限流，需按运行负载评估 |
| 敏感信息 | 全部响应手工白名单；匿名作者隐藏真实 ID/昵称/头像；付费内容与私密评论正文独立授权；待响应实测 |
| 反漏扫 / 匿名 | 未修改 anonymous.viewSkips；BeforeRequestHandler 黑名单与 CaptchaProcessor 解封链路保留；匿名首访和每 5 次触发需在开启盾的环境实测，开发模式关闭盾不能视作通过 |
| 静态资源 / CDN | 本批无前端资源、CDN URL、source map、可枚举目录新增；不涉及 PC/移动模板或编译产物 |
| 上传 / 下载 | 未新增或改动上传/下载接口及返回路径 |

人工全链路：IDEA 编译/重启 → v3 登录取 Token → `/me` → 文章 recent/long 列表 → 文章详情 → 普通评论/回复；分别验证匿名、禁止匿名、讨论作者/受邀/未受邀者、已打赏/未打赏、私密评论作者/文章作者/第三人、匿名作者、封禁作者、失效文章。再检查缺 scope、无效/过期/撤销 Token、旧 apiKey/Cookie、非法分页/ID、超总页空列表，并在盾启用时验证 `/test` 与 `/validateCaptcha` 解封。PC/移动客户端都需确认文本和 HTML 分别安全渲染；本批未改动现有两端页面。

## 内容发现与公开主页批次

- `ApiV3DiscoveryProcessor` 提供 16 条读取路由，全部经过可选 Bearer scope 校验和匿名中间件。`optionalProfile` 用于资料、关注用户、粉丝，其他路由用 `optionalContent`。
- `ApiV3DiscoveryQueryService` 共用文章 SQL 条件：先过滤不可见内容，再计数与分页；领域通过 EXISTS 避免一篇文章多个标签造成重复。SQL 表名来自 Repository，参数均绑定；排序只由固定枚举选择。搜索为标题/公开正文包含关键字，LIKE 元字符转义，不依赖第三方索引。
- 公共文章列表现同步复用该查询；进一步过滤封禁/永久停用及不存在的作者。`ApiV3ContentDtoService` 专门映射文章摘要和作者，避免服务之间循环注入。
- 用户子列表先校验已有隐私开关，私密列表仅本人或管理员可读；公开用户 DTO 只有身份、昵称、头像、介绍和个人网址。关注列表在分页前剔除不可用目标，不返回完整用户对象。
- 清风明月返回原始正文与清洗 HTML，不返回 IP、UA、城市或审核状态；未更改原站点主页隐私开关的业务含义。
- `ApiV3Requests` 集中校验页码、size、枚举、ID、字符串长度/控制字符并输出 no-store 响应。搜索增加有界并发安全的调用频率缓存。
- 新增 `ApiV3RequestsTest`、`ApiV3DiscoveryQueryServiceTest`，扩展用户隐私测试；已找到 Android Studio 附带 Java 25；语法解析通过，权限/DTO TestNG 测试和项目类型检查仍待执行。
- 重编译/重启后执行 `node tools/api-v3-content-smoke.mjs` 和 `node tools/api-v3-discovery-smoke.mjs`。后者可配置公开/私密用户及所有者 Token，覆盖目录到详情到文章、搜索、主页子列表及匿名/所有者边界。

本批源码复核：未新增业务写入，因此无 Cookie CSRF 写入面；身份不回退 apiKey/Sessions；无调用方 URL 请求，不新增 SSRF；Markdown HTML 走公共清洗，其他文本按文本交付；DTO 不含凭据/联系方式/IP/UA。所有新增路由保留匿名盾，未改 anonymous.viewSkips、黑名单、验证码解封逻辑。未新增模板/前端资源/CDN URL/source map/目录/上传下载路径，PC/移动客户端均可使用同一 JSON 契约。并发频控、SQL 运行行为、恶意 HTML 展示、两端真实操作及开启盾后的首次/每 5 次验证和解封均尚未实测，不能视作已通过。

### 本轮验证补充

- 修正刷新 Cookie 把秒数误作毫秒再次换算的问题；`ApiV3TokenUtil.cookieMaxAgeSeconds` 统一秒数边界处理。
- 通过 `node tools/api-v3-token-test.mjs` 实际编译并执行生产 Token 工具类和 `ApiV3TokenUtilTest`，5 项测试通过（1000 个随机 Token 格式与去重、非法 Token、哈希、30 天有效期、边界溢出）。只写临时目录，不启动服务或使用 Maven。
- 其余 v3 源码通过 Java 25 语法解析、模型常量引用、路由中间件/文档覆盖、IoC 非 final 与 Node 脚本语法检查；这不等同于全项目编译或服务端运行验证。
- 数据库 SQL、目录/用户真实数据、所有者与第三人、scope、验证码解封、浏览器 PC/移动链路仍待服务端重编译后验证。详细步骤保留在本文件和两个 smoke 脚本中。

## 个人读取批次

新增 `ApiV3PersonalProcessor` / `ApiV3PersonalQueryService`，共 7 条 GET 路由。设置使用 profile.read，签到状态/背包/积分使用 activity.read，草稿/个人专栏使用 content.read。全部要求有效 Bearer；不从参数取用户，不接受 Cookie/apiKey 身份。草稿详情使用 Repository 的 owner + ID 联合过滤，不存在与越权统一 404。

草稿列表使用字段投影避免加载大正文；详情按白名单保留编辑器正文、思绪记录及发帖选项。积分复用现有流水解释，但只输出本人余额、变动和清洗说明，不输出双边余额、请求编号、凭据或来源内部 ID。已用项目自带 JSON 实现实际确认 `put(Collection)` 与 `put(Object)` 的差异，流水服务返回 JSONArray 可按现有契约读取。个人专栏按 columnOrder + 更新时间排序。全部读取无业务写入，未改旧接口、模板、CDN 资源、anonymous.viewSkips 或上传下载路径。

新增 `ApiV3PersonalQueryServiceTest`（摘要/正文/所有者字段边界，尚未运行），及 `tools/api-v3-personal-smoke.mjs`（可配置本人/他人草稿、有效/缺 scope Token）。重编译后从登录 → me → 设置/签到/背包/积分/草稿/专栏逐项读，再用另一账号的草稿 ID、无凭据、旧凭据、失效 Token、缺 scope、非法分页和不存在 ID 回归。真实数据查询、两账号越权、黑名单与验证码、PC/移动端消费仍未实测。

当前完整目标仍为“实现 /api/v3 的所有接口”，未完成：账号注册/找回、个人写入、文章/评论创作、专栏管理写入、鱼游/大事记写入、OAuth PKCE、上传票据/Socket/受控支付跳转，以及各模块运行验证。源码交付状态不能替代完整目标完成验收。

## 草稿写入与幂等批次

新增 POST /me/drafts、PUT /me/drafts/{draftId}、DELETE /me/drafts/{draftId}。路由要求 content.write Bearer，并串联有界用户/IP 写入频控；不接受页面 Cookie 或 apiKey 身份。严格校验 JSON、UTF-8、深度、重复字段、大小、字段白名单和数据类型。草稿内容规则沿用原编辑器，包含未完成正文与思绪记录；PUT 是完整替换。

数据库新增 api_v3_request，结构已同步 sql/20261016_api_v3.sql 与 repository.json，Repository 名称无前缀。运行前需应用新增 CREATE TABLE，并通过 IDEA 重编译/重启。草稿管理服务新增显式 InCurrentTransaction 方法，旧入口仍自带事务；草稿 owner+ID 查询加行锁。幂等回调和结果同事务提交，审计记录用户/操作/结果/请求 ID，不记录凭据或原始幂等键。

已实际执行 node tools/api-v3-write-test.mjs：20 项测试通过，编译真实协议工具、模型、Repository 和草稿/幂等管理服务，产物只在临时目录。覆盖 1000 组 JSON 往返、真实 Latke RequestContext 的输入校验、32 个同键并发、结果保存失败回滚、提交确认丢失重试、跨用户修改/删除拒绝、删除后创建重放不重建，以及旧管理入口仍提交。并发和业务链路使用事务测试适配器，不能证明 MySQL 的行锁/隔离行为，也不包含 HTTP 路由和浏览器。

手工全链路：应用 SQL → IDEA 重编译/重启 → 登录取 content.read/content.write Token → 创建草稿 → 相同键重放/不同内容 409 → GET 核对编辑器正文与思绪记录 → PUT 全量替换与重放 → DELETE 与重放 → GET 404。另用第二个账号尝试更新/删除第一个账号的草稿，确认 404；缺凭据 401、缺 scope 403、非 JSON 415、重复字段/非法 JSON 400、非法字段/缺幂等键 422、超大体 413、频控 429。tools/api-v3-draft-smoke.mjs 会创建临时草稿并清理，仅在服务重编译后运行。

源码安全结论：新增写路由无匿名入口，Bearer-only 不新增 Cookie CSRF 写入面；所有权在事务内验证；SQL 值绑定，无任意 URL 请求；草稿正文按源文本交付，不直接渲染 HTML；DTO 白名单不输出所有者/凭据；现有 BeforeRequestHandler 黑名单和验证码解封未变，anonymous.viewSkips 未变。未新增前端模板/生成资源/CDN URL/source map/目录枚举/上传下载路径。实际 MySQL 并发、频控、过期/撤销 Token、Cookie-only、黑名单/验证码链路及 PC/移动端消费均未运行验证。OAuth PKCE、其他写入和完整目标仍未完成，不能据本批测试宣布全 API 完成。

## 通知批次

新增 7 条 /me/notifications 路由：列表、详情、未读分类计数、单条/分类标记已读、单条/分类删除。类别和状态筛选先于计数和分页，类别只由 ApiV3NotificationTypes 定义，未知类型归 other；批量删除需明确分类且不接受 all，沿用旧站按类别清理的能力。

读取只返回白名单。评论和文章先按 Bearer 阅读者校验，私密评论复用 contentVisible 规则，匿名作者继续隐藏真实身份。系统/积分说明抽取原 NotificationQueryService 的格式化逻辑，已对照 HEAD 确认两个 switch 未更改业务内容（原 continue 对应 helper 的 null 返回）。无效或不可访问的引用返回占位通知，聊天富消息只取展示文字，禁止输出消息 JSON。评论跳转复用原评论分页定位。

状态由 NotificationStateMgmtService 在幂等事务内修改，所有者与 ID 联合加锁。批量操作在事务开始确定 ID 上界后分批处理，超过单批也完整执行；幂等重试不修改新到通知。刷新通过 NotificationMgmtService 在提交后发布，刷新失败不回滚已提交的状态。旧通知创建和状态入口未改造。

新增 notifications.read / notifications.write；DEFAULT_SCOPES 已更新，sql/20261016_api_v3.sql 附带只针对原始 website_v3 默认 scope 的升级语句，不覆盖定制策略。应用 SQL、IDEA 重编译/重启后需重新登录；旧 Token/刷新链保持原 scope，不自动提权。

已运行 node tools/api-v3-notification-test.mjs：7 项通过，执行真实 NotificationRepository 生成的 SQL、状态服务和幂等协调服务，使用临时内存 SQLite。覆盖分类全量/互斥、所有者/状态/计数/分页、绑定参数、605 条跨批操作、重放隔离新增通知、真实数据库事务回滚和 DTO 脱敏。测试只移除 SQLite 不支持的 FOR UPDATE，因此不证明 MySQL 行锁或并发隔离。原 20 项写协议/草稿回归也通过。

人工链路：应用 scope 升级 SQL → IDEA 重编译/重启 → 重新登录确认两项通知 scope → 列表各分类/状态筛选 → 未读数 → 打开详情/评论（含后续页与段评） → 标记已读/重放 → 删除一次性测试通知/重放 → 跨账号 GET/标记/删除 404。另测批量分类操作后新通知不受原键重试影响、普通/思绪/讨论/匿名作者、私密评论第三人、已删除目标、系统自定义 Markdown/HTML、聊天室 @/红包、积分应用通知、过期/撤销/缺 scope Token、只带 Cookie/apiKey、频控和黑名单/验证码解封。tools/api-v3-notification-smoke.mjs 默认只读，写入只针对显式配置的测试通知 ID；批量变更需人工选测试账号。

源码安全复核：7 条路由均明确 Bearer + scope；写入口串联统一频控与幂等，无 Cookie 身份回退；状态所有权在事务内校验，SQL 参数绑定；不请求调用方 URL，说明经清洗，DTO 不含凭据/联系方式/IP/UA/原始引用。未改 anonymous.viewSkips、黑名单、CaptchaProcessor；未增模板/静态资源/CDN URL/source map/枚举目录或上传下载路径。MySQL、完整 HTTP/HTML/通知刷新、旧页面 PC/移动回归及开启盾的链路尚未实测，完整目标仍未完成。

## 社区读取批次

新增 8 条 GET：鱼游列表/详情/评论，大事记列表/详情，公开统计，同城用户/文章；统一可选 Bearer 与匿名中间件，分页 size≤50。鱼游与大事记仅查询审核通过记录，详情和评论父资源也验证审核状态。DTO 不复制待审修改、审核字段、作者内部记录或统计扩展字段，URL 仅允许 http/https 或站内绝对路径。

鱼游 getV3ApprovedPage 为真实数据库偏移分页，修正新 API 不应继承旧 getApproved 的 100 条上限和忽略 page 的行为。用户已确认 v3 默认沿用现有总投票数优先排序，次序为总投票数、点赞数、时间；保留 sort=likes，源码和公开文档已同步。

同城用户只返回 userGeoStatus 公开且状态正常的账号，公开 DTO 不输出城市/国家、IP、在线状态或登录时间。同城文章通过共用查询附加城市、作者公开位置/正常状态及非匿名条件，并继续执行文章匿名访问、隐藏列表、讨论排除等筛选；所有筛选先于计数和分页。大事记日期沿用原服务的服务器时区，正文经清洗。统计在线总数包含在线成员，峰值维护复用 OptionQueryService 既有事务，不公开 WebSocket/IP 集合。

已运行 node tools/api-v3-community-test.mjs：4 项通过，真实 approved-game SQL 在临时 SQLite 执行，覆盖审核过滤、两种排序、完整分页、字段白名单、嵌套用户秘密、URL 协议与大事记元数据。该测试不包含 HTTP、同城隐私或 HTML 清洗执行；这些仍待重编译后实测。原 7 项通知与 20 项写入测试回归通过。

人工链路：IDEA 重编译/重启 → 匿名读取鱼游/大事记列表 → 详情/评论，待审/驳回/停用 ID 均应 404 → 统计只含约定数字 → 两账号分别公开/关闭位置，在同城用户与文章中核对，仅公开且正常账号及非匿名文章出现；再测禁止匿名文章、隐藏文章、讨论帖、分页越界和排序/城市输入。用 content.read/profile.read Token 复测，检查个人投票只属于当前账号。tools/api-v3-community-smoke.mjs 为只读脚本，可配置待审资源 ID。

反漏扫/安全源码复核：8 条路由均挂匿名盾，未添加 anonymous.viewSkips；黑名单与验证码解封未改。查询参数绑定，排序固定枚举，城市文本限制 64 字符并拒绝控制字符；无任意 URL 请求或上传下载能力。页面/静态资源/CDN/source map/目录未变。审核可见性、地域隐私、JSON 文本/HTML 展示、Cookie/apiKey 不赋予 v3 身份、风控、PC/移动客户端以及 MySQL 运行均未联调，不以静态检查宣称通过。完整目标仍需排行榜、主页剩余能力、账号、创作/设置等写入、PKCE、上传/Socket/支付及全部运行验收。

## 排行榜批次

新增 GET /rankings/{type}，覆盖 links、balance、consumption、checkin、online、adr、mofish、smallmofish、lifeRestart、evolve、emoji、xiaoice、invite、donate、perfect。Evolve 子榜单 achievement/know/reset/days，小冰 0/1/2/3，其余拒绝非空 subType；统一 p/size，白名单 DTO 与公开资料。

邀请、赞助、优选、小冰从 TopProcessor 提取到 RankingQueryService，旧数据入口共用查询，复制用户后脱敏避免污染缓存。旧赞助/优选响应兼容 JSONArray 和 List，修正空榜；原响应字段保留。其余榜单复用已有服务，积分榜保留加入榜单设置，游戏排名沿用已有计算（Evolve/人生重开最近 100 记录）；此分页是有限榜单快照，不是全站排行。小冰固定 URL、固定子类型、有超时与结构检查；失败返回 503，不伪造数据。

v3 仅缓存已投影快照，键为封闭 type/subType，10 秒有效；不缓存 Bearer 或用户凭据。返回 rank/score/metrics/user 或 link，游戏不输出完整存档和登录时间；赞助 summary 仅总金额/天数。过滤无效用户后再生成连续名次和分页。

已实际执行 node tools/api-v3-ranking-test.mjs：5 项通过，真实 DTO/参数协议和真实 org.json 集合行为，覆盖全部 15 类分数映射、所有子类型、嵌套白名单/游戏存档排除、链接与汇总、旧响应容器转换。Java 25 解析改动服务/处理器无语法错误；不代表完整类型编译。未运行服务 SQL、MySQL、外部小冰、HTTP 或浏览器。

人工链路：IDEA 重编译/重启 → 15 类榜单入口 → Evolve 四类/小冰四类 → 每页 2 条翻页，核对分数、连续名次与 total → 对照 PC/移动旧榜单；赞助/优选旧 API 不应再因容器类型为空。用加入/退出积分榜、无效用户、游戏存档含秘密字段的测试数据核对隐藏/脱敏，空榜与越界页应为空列表；非法 type/subType/分页 422，非法 Bearer 401，缺 scope 403，小冰上游失败 503。运行 tools/api-v3-ranking-smoke.mjs（小冰需显式开关），随后用新匿名 IP 测首访验证码和黑名单 /test → validateCaptcha 解封。

源码反漏扫/安全复核：新 GET 路由 optionalContent + anonymous::handle；anonymous.viewSkips 与黑名单/验证码链路未改。只读接口不新增 CSRF 写面；参数封闭枚举，SQL 不拼接用户值，外部请求固定地址；DTO 不含原始用户/存档/凭据，URL 受协议限制。不增加前端/CDN/source map/公开目录/上传下载路径。批量滥用由既有访问保护和有限快照减压，真实频控与各权限/反扫描链路仍待运行验证。完整 v3 目标仍未完成，下一步为主页剩余读取、账号与业务写入、PKCE、上传/Socket/支付及整体运行验收。

## 主页评论与关注动态批次

新增 /users/{userName}/comments、/watching、/watching/articles 三条 GET，optionalContent + anonymous::handle；分别检查 userCommentStatus / userWatchingArticleStatus，本人或管理员的隐私读取沿用已有规则。公开主页评论只列公开署名、非讨论、可列出文章下的普通评论/段评，与公开文章发现范围一致；受限讨论评论仍从具体文章读取，不在公开主页聚合。私密正文复用 commentDto 权限检查，文章摘要与作者均白名单。

ApiV3ProfileQueries 提供实际绑定查询片段；主页评论过滤匿名、失效评论、无效/停用作者、隐藏/沙盒/B3log/讨论文章与匿名阅读限制，之后计数分页，输出前再执行 viewableArticle。关注动态为关注用户+有效标签文章的集合，source=users 不泄露关注用户的匿名文章，标签来源不按作者身份过滤；EXISTS 避免多标签/重复关系造成重复。订阅文章仅 followingType=ARTICLE_WATCH，收藏仍为 ARTICLE。Selection 新增可选 Following，原 4/5/6 参数调用保持兼容。

已实际运行 node tools/api-v3-profile-test.mjs：2 项临时 SQLite 测试通过，执行生产查询片段，覆盖来源分离/合并去重/所有者绑定/匿名身份保护/无效标签，以及评论过滤、匿名与 Bearer 阅读差别、私密/段评记录、计数和页偏移、缺失文章。此测试不包含真实 UserExt 常量、Latke 路由、DTO 渲染或隐私开关执行；完整类型编译和 HTTP 未运行。三个服务/处理器 Java 25 语法解析通过。

人工链路：IDEA 重编译/重启 → 用户主页评论 → 普通评论/段评/私密评论 → 对应文章；第三人看到私密正文为空，本人/文章作者可见。公开主页不出现匿名署名、讨论、禁止匿名访问、已删/隐藏文章或无效作者内容；Bearer 可见允许登录阅读的文章。切换评论/关注文章隐私，第三人应 403，本人/管理员仍可读取；Cookie/apiKey 不替代 Bearer。关注用户/标签各一组且交叉同篇，核对 source=all 去重、users 不带匿名作者文章、tags 有效标签、watching/articles 只含订阅不含收藏；翻页/总数、非法 source/sort/type/分页、缺 scope/过期令牌和匿名验证码/黑名单解封均需覆盖。tools/api-v3-discovery-smoke.mjs 增加可选活动主页 fixture 验证。

源码安全检查：三条公开 GET 均明确匿名盾；身份只由 Bearer，既有 blacklist/captcha/anonymous.viewSkips 未变；SQL 值绑定、source 固定枚举；DTO 不输出评论/文章 IP/UA 或匿名真实作者，无任意 URL 请求与写操作。未增页面/静态/CDN/source map/目录枚举/上传下载路径。HTTP、MySQL、PC/移动消费、隐私切换和完整反扫描链路仍待重编译后验证。剩余账号、个人与创作写入、PKCE、上传/Socket/支付及运行验收继续保持未完成。

## 关注、收藏与订阅批次

新增 12 条 /me/follows/users、/me/follows/tags、/me/collections、/me/subscriptions 列表及 PUT/DELETE 单目标路由。读分别 profile.read/content.read，写 follows.write + 写频控 + Idempotency-Key；默认 scope 原已含 follows.write，无新增 scope SQL。无 Cookie/apiKey 身份回退。

FollowMgmtService 提取显式事务内 set 方法，FollowMutation 是共用状态转换；FollowRepository 按操作者行锁串联旧入口/v3，再锁目标和 owner+target+type 关系。重复状态不调整计数，取消可清理无效目标，历史重复关系全部清除并按实际条数调整计数。旧八个注解事务入口改为显式 begin/commit/rollback，避免捕获异常后误提交部分写入；未改旧 API 字段。

v3 事务中校验锁定目标的状态与阅读权限，讨论邀请按 Bearer 判断；添加收藏/订阅保留角色权限，取消无需目标仍可读。关系、计数、通知记录与幂等结果同事务；通知统一走 NotificationMgmtService 的事务内方法，旧三类通知入口也重新锁定操作者后使用同一去重检查，串联旧关系提交后的通知与 v3 原子通知。刷新在 v3 提交后发布，重放仅重发刷新不新增通知；幂等响应的内部通知接收者不对外返回。仓储会在提交前写缓存，故提交/回滚后均失效目标缓存，避免失败后残留错误计数。

个人收藏/订阅按关系创建顺序分页，包含本人有权阅读的讨论帖；不能复用排除讨论的公开发现列表。已删除/失权资源以 id+available=false 占位，不输出标题、作者或正文，仍可取消。公开主页的发现列表范围维持原设计。

实际执行 node tools/api-v3-follow-test.mjs：4 项真实共享转换测试 + 2 项实际 FollowRepository SQL/SQLite 回滚检查通过，覆盖四类关系、重复设置、所有者/类型隔离、历史重复清理、缺目标/缺事务/拒绝校验、参数绑定、部分写入真实回滚。SQLite 测试去掉 FOR UPDATE，不证明 MySQL 锁；业务计数适配器、通知/缓存/角色权限尚无完整类型编译和运行验证。Java 25 语法解析和 git diff --check 通过。

人工链路：IDEA 重编译/重启 → 登录取对应 scope → 四类个人列表 → PUT 添加 → 同键重放/新键重复 → 公开主页/个人列表和旧 PC/移动按钮核对 → DELETE 取消/重复 → 重放原添加键，确认不重新创建；PUT/DELETE 同键 409。两账号并发同目标，不同键同账号及新旧入口混合并发，计数应等于真实关系数；取消不存在关系不减计数。检查目标接收的用户/收藏/订阅通知不重复，自收藏不通知，异常回滚无通知且缓存计数正确。讨论帖分别用作者/邀请者/未邀请用户测试，失权/删除目标的本人占位与清理、禁用标签/用户、角色禁用、跨账号不能改关系，以及 Cookie/apiKey-only、缺 scope、过期/撤销 Token、非法 ID/请求体/幂等键与频控均需覆盖。tools/api-v3-follow-smoke.mjs 默认只读，只有显式启用开关和一次性 fixture 才写入；跳过已有关系并清理新建关系。

源码安全/反漏扫：12 条路由全部强制 Bearer，8 写路由 follows.write + writes::handle；当前操作者无法由参数指定。SQL 值绑定，目标表名来自 Repository；没有新增 URL 请求、HTML 输出或上传下载。输出白名单且内部通知字段被移除；无 Cookie CSRF 写面。BeforeRequestHandler、AnonymousViewCheckMidware、validateCaptcha、anonymous.viewSkips 未改；未增公网静态/CDN/source map/目录。真实鉴权/越权/CSRF/注入/通知滥用/风控、MySQL 并发、完整 HTTP 和 PC/移动仍待运行验证。原 20 项写协议/幂等/草稿回归通过；全 v3 目标仍未完成。

## 清风明月发布、详情、删除批次

新增 POST /breezemoons、GET/DELETE /breezemoons/{breezemoonId}。写入 content.write + 频控 + 幂等，详情 optionalContent + 匿名盾；角色权限复用 commonAddBreezemoon/commonRemoveBreezemoon，发布保留绑定手机、512 字符、ReservedWords、Jsoup 去 HTML 与 CensorFactory 审核。BreezemoonWriteQuota 同旧 POST/PUT 共用，不另开绕过业务额度。

BreezemoonMgmtService 提取 addBreezemoonInCurrentTransaction，旧注解入口仍保留；v3 删除按 owner+ID 加锁。职业 breezemoonPublished / targetRemoved 仍随内容和幂等结果事务提交，未用裸 SQL 绕过事件捕获。ApiV3IdempotencyMgmtService 新增 Execution(result,applied)，旧 execute 返回不变；首次确认提交才尽力发 bz-update，同键重放不广播。提交确认丢失或进程退出可漏发实时事件，不承诺可靠投递；列表数据仍以已提交数据库为准。

修复此前 v3 全站清风明月读取漏校验作者列表公开设置的问题；BreezemoonRepository.getV3Page 在计数和分页前过滤公开状态、内容状态及封禁/停用作者。用户子列表仍先经隐私授权后调用 owner 查询，详情同样校验作者和隐私。发布私密记录成功但不广播、不进全站列表；DTO 只含 id/content/contentHtml/createdAt/updatedAt/公开 author，不含 IP/UA/城市/手机号/内部状态。

实际执行 node tools/api-v3-breezemoon-test.mjs：3 项分组检查通过，编译真实输入与 Repository 代码，验证长度/类型/控制字符/去标签、公开/私密/无效作者过滤和计数分页、owner+ID 绑定及事务守卫。SQLite 移除 FOR UPDATE，不证明 MySQL 锁。新增首次提交/重放/确认丢失 outcome 检查，原写协议套件现 21 项通过。未执行实际审核、职业事件、角色权限、通知频道或完整服务类型编译。

人工链路：IDEA 重编译/重启 → 有手机号且具发布/删除权限的测试账号 POST → GET 详情/全站与主页列表 → 同键重放/换正文 409 → PC/移动实时显示和刷新后列表 → 本人 DELETE/重放 → GET 404 → 重放原 POST 不重建。第二账号删除应 404；切换列表隐私，匿名/第三人不可读，作者/授权管理员可读；私密发布返回成功且无公共广播。另测 0/512/513 字符、HTML 清洗后空、控制字符、审核拒绝、未绑手机、禁用角色、缺 scope/Cookie/apiKey-only/过期撤销令牌、旧与 v3 共用额度、黑名单/验证码解封。核对发布与删除的职业来源事件只写一次且失败回滚。tools/api-v3-breezemoon-smoke.mjs 默认只读，显式开关后创建一条测试内容并清理。

源码安全反漏扫：新公开详情经过匿名盾；写接口仅 Bearer，所有权在事务中校验。SQL 参数绑定，无调用者 URL；GeoIP 沿用服务端识别 IP 和既有固定服务。正文按既有规则校验并清洗，响应白名单；无新增 Cookie 写入面。BeforeRequestHandler、validateCaptcha、anonymous.viewSkips 未改；未增加静态/CDN/source map/目录/上传下载能力。真实 HTTP、MySQL 并发、审核与职业事务、广播/隐私切换、PC/移动和完整反扫描均待联调。清风明月 PUT/关注流、账号、其他业务写入、PKCE、上传/Socket/支付与全量运行验收仍未完成。

## 清风明月编辑与关注流批次

新增 PUT /breezemoons/{breezemoonId}（content.write + 写频控）和 GET /me/watching/breezemoons（content.read）。编辑复用原 PUT 的正文源标记保留规则、ReservedWords、审核与共用业务额度；owner+ID 行锁内先检查有效状态，再执行额度和审核，避免越权请求消耗审核资源。ApiV3BreezemoonEdit 仅改变正文/IP/UA/更新时间，保留作者、创建时间、城市、审核状态；不能用编辑恢复失效内容。旧编辑和删除改为同一锁定读取，减少新旧入口竞态；旧字段与编辑规则不变。编辑不生成新的职业发布事件，不发送新发布广播；同键重试返回旧结果，不覆盖后续新编辑。

关注流由 BreezemoonRepository 以作者集合 IN 子查询筛选，重复关注关系不重复记录；公开/作者状态/内容状态条件均先于计数分页，无关注或无可见内容时不回退全站。使用了重复关注 fixture，最初 EXISTS 写法在当前 SQLite 环境未通过第二页去重检查，改为集合 IN 后实际检查通过；不能以此判断 MySQL 原写法有问题。

扩充 tools/api-v3-breezemoon-test.mjs，现 5 组实际检查通过：原输入/公开列表/所有权检查，加上编辑字段不变性、源对象不变与 UA 截断、关注流去重/类型/隐私/无回退/绑定/分页。编译真实 helper 与 Repository；不包含业务服务类型编译、审核、事务并发或完整 HTTP。鱼游默认排序按用户最新明确回复改为 votes，显式 likes 保留，文档/AGENTS 同步。

人工链路：IDEA 重编译/重启 → 发布 → PUT 编辑 → GET 核对正文与更新时间且作者/创建时间不变 → 第二次编辑 → 重放第一次 PUT，GET 应仍是第二次正文 → DELETE 后重放编辑也不重建。第二账号编辑应 404；失效记录不可编辑，不能修改 author/status/city，非法字段应 422。分别创建重复关注、私密作者、禁用作者、未关注作者，读取关注流 size=1 翻页核对计数与去重；取消关注或作者关闭公开后消失，无关注返回空。回归审核拒绝、额度、角色、缺 scope、Cookie/apiKey-only、过期令牌、PC/移动旧编辑删除、黑名单与验证码解封。鱼游不传 sort 应与 sort=votes 顺序一致，sort=likes 保持可用。

安全源码结论：两新入口均强制 Bearer，修改挂写权限/频控/幂等并在事务内校验所有权；SQL 绑定，关注者只来自 Bearer，正文渲染清洗且 DTO 白名单。无新 URL 请求、静态/CDN/source map/目录/上传下载能力，无新增 Cookie 写入面；anonymous.viewSkips 与黑名单/验证码链路未变。真实 MySQL、审核/事务、完整 HTTP、PC/移动、权限与风控尚未联调，完整 v3 目标保持未完成。

## reaction 批次

新增 10 路由：表情目录、文章/评论/聊天室消息 GET/PUT/DELETE reactions。公开读取明确 optionalContent + 匿名盾；聊天室 GET 强制 content.read；六写路由 reactions.write + 写频控 + 幂等。PUT 设置、DELETE 清空，非 toggle；原默认 scope 已含 reactions.write，无 scope SQL 变动。

ReactionMutation 为真实共享状态转换，ReactionRepository 锁 actor、目标及 owner+target+type+group，legacy toggle 和 v3 显式设置共用，重复设置不反转，重复清空不写，替换保留创建时间，重复历史记录收敛为一条。旧 toggle 仍自行提交，v3 加入统一幂等事务；旧返回字段不变。读取使用 buildOperationResultOrThrow，不把数据库失败当空汇总；DTO 白名单覆盖 tooltip userDetails 并限制头像 URL。

ApiV3ReactionTargetService 对实际目标重新校验：文章含匿名阅读/讨论邀请和有效作者，评论含目标文章、状态、作者和私密正文权限（段评同样适用）；写入持锁校验，parent article 也锁定；聊天室沿用登录者可访问公共聊天室消息的边界，只检查已存在消息，不返回内容。旧 reaction Processor 原有目标可见性检查仍未改造，不能据 v3 校验宣称旧接口权限已修复。

实时事件在首次确认提交且实际变更后尽力发送。文章/评论不直接调用旧无接收者过滤的 notify 方法，逐个论坛 session 解析现有 Cookie/apiKey 身份并重新加载用户、检查实际文章与私密评论权限，忽略客户端 articleType；频道消息只带目标 ID、汇总和公开 actor。聊天室沿用 chatReaction 节点广播。summary.selected 在广播中置 false，actorUserId/actorReaction 供前端合并。数据库提交与频道没有可靠投递保证；提交确认丢失、进程退出或广播失败可漏发，后续 GET 可恢复。

已运行 tools/api-v3-reaction-test.mjs：4 组检查通过，编译真实 ReactionMutation/DTO/ReactionRepository；临时 SQLite 执行实际 SQL，覆盖全部 value/拒绝输入、嵌套字段和 URL 白名单、三类目标 set/replace/clear/toggle、创建时间、跨 actor/目标/类型隔离、绑定参数、重复记录清理、拒绝目标以及写后异常真实回滚。FOR UPDATE 被移除，不证明 MySQL 并发；目标权限服务、频道、完整业务类型编译和 HTTP 未执行。

人工链路：IDEA 重编译/重启 → GET 表情目录和三类目标汇总 → PUT heart/同键重放/新键同值 → PUT fire → 重放旧 heart，当前仍 fire → DELETE/重放 → 重放原 PUT，不重建。PC/移动文章评论及两套聊天室观察实时数量与本人选中态；两账号并发及旧 toggle 混用，数据库每用户每目标每组只留一条。讨论作者/邀请者/未邀请者、禁止匿名阅读、私密评论本人/文章作者/第三人、删除/禁用目标、无权限 session 伪造 articleId 均需验证；无权读取和写入 404，未登录聊天室 401，缺 scope 403，非法 value/ID/body/key 拒绝，同键换操作/值 409。tools/api-v3-reaction-smoke.mjs 默认只读，显式开启后仅用指定一次性 fixture，跳过已有本人表态并清理新写入。

源码安全反漏扫：公开 GET 均匿名盾，私有路由 Bearer-only，不回退页面身份；SQL 值绑定、类型/分组/表情封闭枚举，无任意 URL 请求。响应与事件不含完整用户/文章/评论/聊天内容、IP/UA/凭据；tooltip 字段在现有 PC/移动和两个聊天室 JS 中有转义，头像 URL 有协议检查。Cookie 不授予 v3 写身份，故不新增 Cookie CSRF 面。黑名单/验证码/anonymous.viewSkips 未改；无前端源码/生成物/CDN/source map/目录/上传下载变动。MySQL 并发、实际鉴权/越权/频控/风控、XSS 展示、Socket 接收者与 PC/移动联调未验证。全 v3 目标仍未完成。

## 鱼游投票与评论批次

新增 PUT/DELETE /fish-games/{gameId}/vote（reactions.write）及 POST /fish-games/{gameId}/comments（comments.write），均串联 Bearer、写频控和幂等；默认 scope 已包含这两项，无新增 scope SQL。投稿与编辑尚未开放：当前代码只有 HTTPS/字段校验，未找到 AGENTS 所述 OAuth 校验；已向用户询问，用户已确认必须校验 OAuth，等待其提供规则或入口，不用猜测替代。

FishGameMgmtService 提供 setVoteInCurrentTransaction 和 addCommentInCurrentTransaction，锁定并检查父鱼游已审核后才执行业务额度；评论规范化由 FishGameValidationService 共用，仍为去 HTML/trim 后 1–500 字符，未擅加 AI 审核。FishGameWriteQuota 与旧三类额度入口共享；v3 幂等重放不会重新消耗业务额度。投票是显式状态：同值不反转、换值替换、空值清除；旧 vote 仍 toggle 并自行提交，旧响应移除内部 changed 字段保持原形态。vote repo 查询均绑定 owner/game，重复历史记录可收敛，投票与重新计数在同一事务提交。评论父资源校验移入事务，避免审批状态改变后的无效写入。

后台 requestEdit/review/updateByAdmin 改为只更新相应元数据字段，不覆盖旧快照中的 like/dislike 计数；投稿规则与字段未改。新旧写入失败均回滚；公开评论 DTO 从既有读取提取，复用于创建响应，不返回 owner 内部字段/待审配置。

已运行 node tools/api-v3-fish-game-test.mjs：5 组实际服务检查通过，真实 FishGameMgmtService、ValidationService、三个 Repository 和模型完整隔离编译；临时 SQLite 执行真实锁定/owner 查询与 COUNT SQL，仓储写操作经 SQLite 事务适配器执行。覆盖显式投票/换票/清票、旧 toggle 的真实提交和旧响应形态、账号隔离、写后失败回滚投票与计数、父资源未公开拒绝、评论规范化/长度/插入失败回滚，以及后台旧对象更新不覆盖新计数。SQLite 去掉 FOR UPDATE，不证明 MySQL 锁或多实例；v3 幂等协调、业务额度和 HTTP 未在此测试执行。

人工链路：IDEA 重编译/重启 → 读取已审核鱼游 → PUT like/同键重放/新键同值 → PUT dislike → 重放旧 like 但 GET 当前仍 dislike → DELETE/重放 → 旧 PUT 重放不得恢复。两账号与旧投票接口混合操作，数据库计数应与真实投票一致；同时后台编辑/审核不能丢计数。POST 评论 → 同键重放同一 ID → 列表一次出现；0/500/501 字符、HTML 清洗后空、非法控制字符/类型拒绝。待审/驳回/停用/不存在的父资源均 404，缺 Bearer 401、缺 scope 403、同键换内容 409，Cookie/apiKey-only 不赋予 v3 身份。核对旧 PC/移动鱼游页面、业务额度共用、黑名单/验证码解封。tools/api-v3-fish-game-smoke.mjs 默认只读，投票显式开关且跳过已有本人票/清理新增票；评论另设开关，明确会留下一个测试评论（当前没有 v3 删除接口）。

源码安全反漏扫：三条新写路由均明确授权，无新匿名入口或 Cookie 写身份；目标审核状态持锁校验、所有者来自 Bearer，SQL 参数绑定；文本去 HTML 并限制长度，响应白名单，无任意 URL 请求、投稿 URL 抓取、凭据/审核字段泄露。BeforeRequestHandler、anonymous.viewSkips、AnonymousViewCheckMidware 与验证码解封未改；无静态/CDN/source map/目录/上传下载变动。MySQL 并发、实际鉴权/越权/频控/CSRF/XSS展示/风控、PC/移动和完整 HTTP 尚未验证。全量目标仍未完成，OAuth 投稿规则入口待提供，但不阻止其他模块推进。


### 2026-10-17 专栏管理写入

新增 PUT /columns/{columnId}/title、PUT /columns/{columnId}/cover、PUT /me/columns/order、DELETE /columns/{columnId}，全部 content.write + 写中间件 + 幂等事务。无独立创建专栏接口；沿用文章发布时创建的业务链路。只接受本人有效专栏，摘要白名单与个人专栏读取一致；标题 trim/64、封面复用原校验（可空）、排序 1–200 唯一数字字符串。原排序入口保持去重和部分列表语义。

LongArticleColumnMgmtService 提取 rename/order/remove InCurrentTransaction；旧入口自行提交，v3 与幂等记录同事务。ColumnCoverMgmtService 与章节归属、统计共用父行锁；章节读取使用 FOR UPDATE 当前读，删除拒绝仍有章节的专栏。排序按 ID 固定顺序锁定全部目标后写入，失败整次回滚；同名已失效专栏仍受既有数据库唯一约束限制，不更改重用名称规则。

实际验证：tools/api-v3-column-test.mjs 用 Java 25 编译真实两套专栏服务、模型与 Repository，4 组 SQLite 测试通过：严格输入/URL、所有权/重名/事务守卫、部分排序/旧去重/失败回滚、章节计数/非空拒删/已删除父资源拒绝。SQLite 去掉 FOR UPDATE，不证明 MySQL 锁或并发正确性；不覆盖 HTTP 或 v3 完整集成。原 21 项写协议/幂等/草稿回归通过，新 Processor/协调服务等 4 个 Java 文件通过 Java 25 语法解析（非全项目类型检查）。

人工链路：先 IDEA 重编译/重启 → GET /me/columns → 本人专栏改名/同键重放/同键换内容 409 → HTTP/HTTPS/站内封面设置/清空 → 调整部分顺序再读取列表 → 两账号混合排序失败且无部分写入 → 含章节删除 409 → 经现有文章编辑移出章节 → 空专栏删除/同键重放成功/新键 404。并发执行旧章节发布/编辑与 v3 改名/封面/删除，核对无孤立章节、计数和元数据不丢失。核对旧 PC/移动专栏入口。tools/api-v3-column-smoke.mjs 默认只做鉴权与读取，显式开关可修改测试专栏并恢复名称/封面；排序与删除人工执行。

源码安全反漏扫：4 条路由均强制 Bearer content.write，无 Cookie/apiKey 身份回退；事务内校验所有权，SQL 参数绑定，响应白名单，封面只存储不抓取、无 SSRF 新请求；JSON 标题保持原文，现有 PC/移动页面经 LongArticleColumnQueryService 的 Escapes.escapeHTML 后渲染，封面复用既有地址校验。写中间件限制批量滥用，分页与排序有界。未改 BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor 或 anonymous.viewSkips；未增加匿名/静态/CDN/source map/目录/上传下载入口。真实鉴权/越权/CSRF/XSS 展示/注入/频控/黑名单/验证码以及 MySQL 并发和 PC/移动仍待重编译后联调。全量 v3 尚未完成，继续推进账号、创作、个人设置、PKCE、上传/Socket/支付及整体运行验收。


### 2026-10-17 昨日活跃度奖励

新增 GET/POST /me/activity/yesterday-reward，分别 activity.read/activity.write；POST 白名单空 body、写频控和幂等。新增 activity.write 默认策略并更新 sql/20261016_api_v3.sql，仅升级未自定义的 website_v3 策略，已有客户端需执行 SQL 后重新登录（refresh 不提升旧 token scope）。不新增表。日常签到入口已在旧路由关闭，实际为 LivenessMgmtService 活跃度自动触发，因此不创建绕过条件的手动签到 API；补签与背包写入仍未完成。

LivenessRewardMgmtService 被旧 ActivityMgmtService 的两个领取方法和 v3 共用。外层持有 PointtransferMgmtService.executeWithTransferLock，再开启业务/幂等事务；锁账号后读取服务器当天真实奖励转账并按原 Liveness.calcPoint 计算昨日积分。转账使用 InCurrentTransaction 且禁用提交前异步索引，失败抛出并回滚；提交后尽力索引，回复仅 date/amount/claimed。相同键跨天重放仍返回原结果；新一天用新键。日期在拿到用户锁后读取，使用服务端时区与真实日界（兼容 DST）。查询用白名单状态，无 user/balance/transferId 或内部来源编号。

实际验证：tools/api-v3-activity-test.mjs 4 组真实转移状态与绑定 SQL SQLite 检查，覆盖时区/日界/DST、锁后取时、一次领取与响应白名单、重复拒绝、失败余额/记录回滚、无奖励、用户/方向/类型/时间范围/活跃度日期隔离及注入参数。未编译完整 Activity/Pointtransfer 依赖，未运行实际积分服务、奖励公式、MySQL 行锁或 HTTP；测试不声称覆盖这些。既有 21 项写入与 5 项 Token 测试回归通过；8 个变动 Java 文件通过 Java 25 语法解析（非完整类型检查），路由/默认 scope/旧入口共用/提交后索引/保持关闭手动签到的源码检查通过，node --check 与 git diff --check 通过。

人工链路：执行默认客户端策略升级 SQL → IDEA 重编译/重启 → 重新登录获得 activity.write → GET 奖励状态 → 有昨日活跃度的测试账号 POST → GET 已领取且 claimable=false → 同键 POST 同结果 → 新键 POST 409 → 积分列表恰好一笔对应奖励。无记录/零奖励账号返回不可领并 POST 409；两账号不串数据。与旧页面领取并发、新旧入口各多次请求，确认总共只入账一次；跨服务端午夜和跨日重放核对日期与积分。检查无 Bearer 401、缺 scope 403、Cookie/apiKey-only 401、额外账号/日期/数量字段或缺幂等键 422。复核旧 PC/移动奖励入口及活跃度自动签到。tools/api-v3-activity-smoke.mjs 默认只读，显式开关才实际领取，积分不回撤。正常提交后的索引尽力刷新；进程故障/不确定提交恢复后的索引完整性仍需验收既有冗余索引机制，不能据窄测试认定可靠投递。

源码安全反漏扫：新增路由均必需 Bearer，无匿名入口，身份不能由参数指定；POST activity.write、写中间件及幂等，SQL 值绑定，积分数额只由服务端旧规则计算，DTO 白名单，无 Cookie 写身份、HTML/URL 输入或外部请求。BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor、anonymous.viewSkips 均未改；无静态/CDN/source map/目录枚举或上传下载路径新增。实际未鉴权/越权/CSRF/注入/滥用/信息泄露、黑名单跳转与验证码解封仍需运行验证；XSS/SSRF 没有本批新增输入面。全量 v3 继续保持未完成。


### 2026-10-17 背包事务与免签卡

新增 POST /me/bag/checkin1day/use 与 /me/bag/checkin2days/use，activity.write + writes + 幂等，空 body 白名单，不允许数量、天数或他人账号。原 GET /me/bag 复用 BagMutation.dto，字段不变。旧两套各 2 个使用入口共用 BagMgmtService 的事务；返回结构保留，失败有明确响应。每次使用卡只消费一张、累加对应 1/2 天，不立即签到。此前对旧代码累加结果的疑问已通过追踪 maxTake 截断确认：原来就是 3→4/5，最初问卷建立在错误判断上，不作为待定业务规则，也不据此修改规则。

CloudService save/get/remove/putBag 兼容包装委托 BagMgmtService，所有背包写入同样锁用户再锁 sys-bag 行，避免空背包插入竞争及新旧发卡/扣卡覆盖。数据库异常不再被当成可写空背包。复用 cloud 表，无新 Repository/表；CloudRepository 补参数化真实 SQL 和当前读守卫。按最新 oId 读取已有快照，写入时清理同账号遗留重复 sys-bag 行；仅作用系统背包，不涉及其他游戏存档。正数增量保留最终上限和 0/1 状态，负数不足返回 -1，long 中间加法避免整型溢出。保留不允许在外部事务调用独立包装的约束，事务内写用显式 primitive；已检查 VIP 首充赠卡在会员提交之后调用。

实际测试：tools/api-v3-bag-test.mjs 编译真实 BagMutation 与 CloudRepository，2 组 SQLite 检查包含：3→4/5 累加、扣卡/其他物品保留、输入快照不变、无卡/负剩余/上限拒绝、旧上限截断与数量溢出、DTO、用户/game 隔离、注入值、事务守卫及扣卡/天数同时回滚和提交。没有编译整套 BagMgmtService/CloudService 依赖，也没有执行真实用户锁/HTTP/定时任务，不把适配器测试当作完整业务集成验证。9 个变动 Java 文件通过 Java 25 语法解析（无类型检查）。21 项既有写入回归通过，两个新路由的注册/权限/文档、4 个旧入口共用和 CloudService 包装委托源码检查通过，node --check 和 git diff --check 通过。

人工链路：先应用 activity.write 策略升级 SQL、IDEA 重编译/重启、重新登录 → GET /me/bag → 使用单日/两日卡 → 核对卡数各减一、剩余天数分别 +1/+2，其他物品不变 → 同键重放，GET 当前仍只使用一次 → 新键再用正常消耗 → 无卡时 409。测试 0/已有剩余、最大天数、两账号隔离、无 Bearer/仅 Cookie/apiKey 401、缺 scope 403、缺幂等键/额外账号数量字段 422。两账号及旧 PC/移动接口、新 v3、发卡/会员赠卡/定时扣天数并发，检查无覆盖和负数；模拟背包更新失败应整次回滚。核对午夜自动签到扣除一天及日常活跃度签到没有被此接口绕过。tools/api-v3-bag-smoke.mjs 默认只读，显式开关实际消耗一张卡且不回撤。补签卡、改名卡和自动签到全链路仍需后续整合，当前未声称这些复合流程已原子化。

源码安全反漏扫：新路由仅 Bearer activity.write，当前账号取 IDENTITY，不接受用户/天数/数量输入；写频控/幂等、绑定 SQL，响应只有物品代码/数量/剩余天数，无用户敏感字段、HTML 或 URL 输入/任意请求。旧入口仍保持 loginCheck 与原 CSRF/API 边界。BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor、anonymous.viewSkips 未改；无新增匿名、静态/CDN/source map、目录或上传下载能力。实际鉴权/越权/CSRF/注入/批量滥用/信息泄露与黑名单/验证码仍待运行验证；XSS/SSRF 没有本批新增输入面。完整 v3 目标继续未完成。


### 2026-10-17 本人大事记投稿查询与创建事务准备

新增 GET /me/milestones 与 /me/milestones/{milestoneId}，content.read、当前账号仅来自 Bearer；按 owner+ID 联合过滤详情，列表/总数均先过滤 owner 和正常审核状态，支持 all/pending/approved/rejected 与真实分页。DTO 白名单只含投稿内容/安全地址/状态/时间，不含作者对象、账号隐私或完整原始行；content 是本人正文源文本，不返回未经规则确认的 contentHtml。公开列表/详情依然只允许已通过记录，且匿名链路未变。

MilestoneMgmtService.addMilestone 改为显式事务包装，提取 addMilestoneInCurrentTransaction 并强制字段白名单/作者/待审/服务端时间；输入对象不再原地修改，缓存清理在提交后。旧更新/删除方法的事务注解未改。MilestoneCache 移除未使用的 DomainQueryService 导入以便编译真实服务进行隔离验证。旧 MilestoneProcessor 投稿身份改取 loginCheck 的 attr，兼容 apiKey；失败统一泛化提示，日志留服务端，响应 code/msg 字段不变。投稿输入规则尚未更改。

用户已收到正文格式问题（纯文本、Markdown、原始 HTML）；按 AGENTS 业务规则不明确先确认的要求，未注册 v3 POST 提交接口，不以猜测代替。长度基线可取现有 Repository 模型（标题/媒体标题 255、正文 102400、URL 1024、媒体 image/video），仍需结合用户正文选择完成校验和展示链路。等待答复期间实现个人查询及写入事务准备，不暂停全量目标。

实际验证：tools/api-v3-milestone-test.mjs 用 Java 25 编译真实 MilestoneMgmtService、MilestoneQueryService、MilestoneCache、MilestoneRepository、DTO 与模型，4 组 SQLite 测试通过：事务守卫/强制待审与作者/未知字段剔除/回滚；旧创建方法提交后清缓存/插入失败回滚；真实所有权/审核分类/总数/跨页/越界/注入 SQL；个人 DTO 白名单、原文和不安全 URL 过滤。缓存和存储由测试适配器控制，未验证 HTTP、MySQL 或真实展示。没有伪造框架依赖或编译整个项目；后续检查结果另记。

人工链路：IDEA 重编译/重启 → 用现有投稿入口提交测试记录 → GET /me/milestones?status=pending → 本人详情成功、其他账号详情 404、公开详情 404 → 管理端通过后本人状态 approved、公开可见 → 另一个投稿拒绝后本人 rejected、公开仍 404。检查分页/总数/空页、错误 status/ID、size>50、无 Bearer/仅 Cookie/apiKey 401、缺 content.read 403；核对未知字段/账号隐私不出现在响应。旧 PC/移动提交结果与后台审核正常；模拟提交失败不得留记录或提前清缓存。tools/api-v3-milestone-smoke.mjs 全只读，可配置本人待审/拒绝记录及另一个账号，未实际运行。

源码安全反漏扫：2 条新路由强制 Bearer 无匿名，联合所有权与响应白名单阻止跨账号审核队列泄露；参数枚举与 SQL 值绑定，无新增写入口、Cookie CSRF 面或 URL 抓取。正文仅 JSON 原文，客户端须按数据渲染，未引入 HTML 展示。BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor、anonymous.viewSkips 未改；无新增公网静态/CDN/source map/目录或上传下载路径。真实鉴权/越权/注入/滥用/信息泄露、黑名单/验证码及 PC/移动展示仍待运行；新增 GET 不涉及业务写 CSRF，SSRF 无新增请求面。全部 v3 仍未完成。


用户已确认大事记 v3 正文为 Markdown，展示时清洗 HTML。本批继续完成 POST /milestones（content.write + writes + 幂等）；ApiV3MilestoneInputs 以现有存储长度约束标题/正文/媒体标题/URL，严格日历与起止顺序、媒体枚举、URL 协议/无凭据/控制字符和未知字段。URL 合法单引号规范为 %27，以兼容旧 CSS 引用。ApiV3MilestoneMgmtService 仅在幂等首次执行时渲染/清洗 Markdown，按既有 HTML 正文存储格式保存，避免破坏旧页面/API 的展示契约；回执仅 id/status/时间，不把可能很长的正文存入幂等回执。本人 content 为保存后的 HTML，公开 v3 content/contentHtml 维持原语义，无新表或字段。原始 Markdown 不另外保存，当前投稿没有作者编辑能力；管理员继续编辑既有 HTML 正文。

同时修改 PC/移动各 admin/milestone.ftl、admin/milestones.ftl、milestones.ftl：审核页标题/正文/媒体/链接按 HTML 文本和属性转义，时间线标题与媒体标题先 HTML 再 JS 编码，媒体地址按 JS 字符串编码，链接按 HTML 属性编码。没有改 JS/SCSS/CSS 或 min.js。尝试 yarn run build 失败：当前 PATH 无 yarn，且 node_modules 不存在；未生成或改写静态产物。Java/FTL 仍需要 IDEA 重编译或服务端重启，浏览器尚不能验收源码。

测试扩展至 6 组：新增真实输入校验检查（长度/无效日期/区间/字段类型/越权元数据/危险 URL/媒体类型/控制字符）及 6 个完整真实模板的 Freemarker 语法解析与变动字段表达式恶意载荷渲染验证，均通过。没有运行完整 Markdowns（其 Lute/Flexmark 和站点依赖仍需服务端），也未验证 POST HTTP、审核回调、MySQL 并发或实际浏览器，不能以模板片段单测代替页面联调。

补充人工链路：IDEA 重编译/重启后用 Markdown 标题/加粗/链接提交 → 回执 pending → 同键重试同 ID、同键换内容 409 → 本人查询 HTML 正文与待审状态、他人详情 404、公开详情 404 → 管理员在 PC/移动审核列表和详情查看带引号/闭合 textarea/HTML 的标题与媒体标题应显示文本 → 审核通过后公开 v3 contentHtml 与旧 PC/移动时间线显示排版且无脚本执行。测试无效日期/结束早于开始、255/256 标题、102400/102401 正文、超长渲染结果、非法协议/带凭据 URL、无媒体类型、未知 author/status 字段、无 Bearer/缺 scope/缺幂等键。tools/api-v3-milestone-smoke.mjs 默认只读；显式投稿开关会留下一条待审测试记录，当前没有 v3 删除入口。

安全补充：新 POST 身份仅 Bearer、固定服务端作者/状态/时间，响应白名单；HTML 统一清洗，旧审核模板两端编码，SQL 值绑定；媒体/链接只保存，不由服务器抓取，Markdown 引擎仅走已配置渲染链路；写频控与幂等避免重试重复。没有触及 Cookie/apiKey 旧路由的 CSRF 关系、匿名盾/黑名单/验证码白名单或上传下载/CDN 资源。现有历史数据中非法 URL 以及旧 HTML 投稿的整体安全性仍属于后续完整审计，本批输入只约束新 v3 投稿，不声称修复所有存量入口。


本批收尾检查：3 条新路由注册/权限/公开文档、Markdown 清洗和幂等事务接线静态检查通过；新增 4 个变动文件补做 Java 25 语法解析通过。21 项通用写入与 4 项社区 SQL/元数据回归通过。git diff --check、node --check 通过。仍未进行全项目 Java 类型检查或实际 HTTP/浏览器验收，目标保持进行中。


### 2026-10-17 个人资料、隐私与显示设置

新增 3 组 GET/PUT /me/settings/profile|privacy|display，及 GET /me/settings/skins，共 7 条路由。GET profile.read，PUT settings.write + writes + 幂等，非空 JSON 白名单且只改提交字段。原 GET /me/settings 响应不变，新 privacy 包含 13 项，profile 子接口解码旧存储的 HTML 实体为原文字（旧汇总仍保留原契约）；display 的 PC/移动皮肤由 SkinQueryService 真实目录规范化与设备校验，目录 DTO 不返回版本/内部路径。settings.write 加入默认客户端及严格原策略升级 SQL，自定义 scope 不扩大；需执行 SQL 并重新登录，refresh 不提升旧授权。

UserSettingsMutation 是实际共享事务体，UserSettingsMgmtService 对接锁定用户、按列写、审核与皮肤目录。UserRepository 新更新方法绕开原 update 的提交前 UserCache/Sessions 写入；事务结束后另开只读锁定事务刷新已提交用户到两套缓存，失败则清除缓存与会话缓存使后续重载。刷新时使 ApiV3RankingQueryService 设置修订号递增；旧快照/失效前开始加载的快照在下一次查询均因修订号不同重载，避免清理后被旧加载回填。旧 privacy/skin 页面入口共用原子方法但保留原 Cookie/CSRF 与完整替换参数语义；旧 profile 服务加载改为 FOR UPDATE，减少全行旧快照覆盖。其他旧 UserMgmt/账户操作尚未统一缓存事务策略，不能声称全站所有用户写入都已解决。

ProfileWriteQuota 提取旧 SettingsProcessor 共用的资料/用户名额度，v3 只在首次实际资料写入消耗，隐私和显示不消耗资料额度。资料保留 nickname20/intro255/URL100 的原长度限制与 Escapes.escapeHTML 存储，合并现有 tags/URL 后用原 CensorFactory 检查；不借修改昵称顺带重置 avatar/MBTI/tags/QQ。后续仍需补自选标签、MBTI、功能/地区/语言/安全/头像等设置，本批不缩减全量目标。

实际检查：tools/api-v3-settings-test.mjs 4 组真实输入/DTO/UserSettingsMutation 的 SQLite 事务适配测试通过，覆盖字段类型/长度/URL/目录边界、隐私 0=公开、部分更新与他人/积分不受影响、设备校验、存储编码与新 DTO 解码、审核调用、失败回滚/无用户及映射对照 UserExt。皮肤/审核/存储为适配器，不覆盖真实目录扫描、Censor、UserRepository/缓存/会话或 MySQL 锁，不能当作完整服务集成。全项目编译和 HTTP/浏览器仍需服务端重编译后验收。

人工链路：SQL 默认策略升级 → IDEA 重编译/重启 → 新登录 → GET 子资料/13隐私/显示/皮肤目录 → 每组 PUT 单字段 → GET 核对其他字段未变及同键重放 → 新键再改 → 旧键重放不能恢复旧状态。测试完整读返回仅白名单、昵称/简介实体往返、清空资料、内容审核、旧频控共用；13 项逐个开关并从另一账号/匿名读取相关主页/榜单检验隐私；退出积分榜后不得读旧 v3 快照。两端皮肤目录与 alias、错误设备/不存在路径，PC/移动重新进入页面生效。缺 Bearer/Cookie/apiKey-only 401、缺 scope403、未知 userId/role/password 字段、错类型/空对象/超长/无幂等键422、同键换内容409。混用旧 privacy/skin/profile 与 v3，同时积分变动，核对无丢字段、缓存/会话同步；模拟写失败/提交失败/刷新失败，不能发布未提交值。tools/api-v3-settings-smoke.mjs 默认只读，显式开关改一字段并在 finally 尝试恢复，不要同时编辑该账号。

源码安全反漏扫：新接口均 Bearer、当前账号唯一取自 IDENTITY；SQL 值绑定，列名来自闭集，按列防止 role/余额/凭据越权，DTO 无联系方式/凭据/IP/UA 原值；URL 仅 HTTP/HTTPS、不含账号密码，不发任意服务端请求；资料存储编码、子 DTO 原文按文本消费，审核复用既有服务；写频控与幂等。旧 CSRFMidware 关系未改，apiKey 旧 profile 保持原入口。匿名盾/黑名单/验证码/anonymous.viewSkips 未改，无静态/CDN/source map/目录枚举/上传下载变更。运行鉴权、越权、CSRF、XSS 展示、注入、频控、隐私缓存和完整反扫描仍待验证。


收尾补充：实际 repository.json 皮肤字段仅 32 字符，已按容量收紧输入与 canonical 结果并从可用目录排除超容量 ID；昵称/简介在编码后也检查存储容量，避免实体膨胀引发数据库异常，测试覆盖。旧 profile 控制器与标签校验中间件改从 loginCheck attr 读取当前用户，保证该链路 apiKey 客户端不会只因无 Sessions 失败；字段与现有规则不变。代码内 Latke jar 的 AbstractRepository.update 字节码已确认直接委托底层仓库，新的 super.update 不进入 UserRepository 的缓存写 override；user 表 fieldcheck 未开启，按列 JSON 可用。此检查只证明调用边界，不证明底层 SQL/缓存整体运行正确。

4 组设置专项、21 项通用写入和 5 项排行协议测试通过；13 个变动 Java 文件的语法解析与 7 路由注册/文档/scope、缓存边界、榜单修订号、默认策略 SQL、旧入口共用源码检查通过，node --check 与 git diff --check 通过。未进行 Maven/全项目编译或运行 HTTP/浏览器；手工链路及真实缓存/会话/并发是待验收项，不能据此标记完整目标完成。


### 2026-10-17 功能、位置与语言设置

在现有设置 Processor/DTO/事务服务中新增 functions/location/locale 三组 GET/PUT 和 locale-options GET，共 7 路由；profile.read / settings.write 与非空白名单/幂等/写中间件不变，无新 scope/表。functions 的 9 项为列表数10–96、列表模式0/1、6个开关和站内首页跳转；comment/avatar 已在 display，不在新 functions 重复暴露。location 只有公开布尔，不接受城市/IP/国家。locale 语言复用 Languages（en_US/zh_CN）、时区复用 TimeZone ID 集，目录用原页面 TimeZones；新 API 无效值422，旧 i18n 的默认/回退保持。

SettingsRedirects 用 URI 精确比较协议/主机/有效端口及部署 context 路径，拒绝凭据、伪装后缀主机、不同端口和越界路径，无外部请求。站点根路径归一为空，沿用原设置语义。用户已确认同步加固旧 function 入口，现已复用同一校验，保留旧无效 URL 清空及失败响应格式。逐层解码验证路径边界，保留正常中文、锚点、编码文件名、编码路径分隔、百分号及内部重复斜杠的原始表示；拒绝编码/嵌套编码越界、控制字符和反斜杠。

旧 updateI18n/updateGeoStatus 保留原合法值/回退和响应；改取 loginCheck attr，并使用按列设置事务避免覆盖同时变化的其他用户字段。旧 updateFunction 原解析、范围截断、URL判断保持，功能与 comment/avatar 显示字段通过新的多组事务一次提交；第二组失败整次回滚，缓存仍只在事务结束后刷新。chatRoomPictureStatus 注释虽提到2，但现有控制器/两端 checkbox 实际用0/1，v3 布尔沿用实际链路。

6 组设置隔离测试通过：在既有4组上新增功能/地理/语言类型与范围、精确 origin/context 重定向样本，以及新增组部分写、位置DTO不泄露坐标/城市、locale未提交字段保留、多组回滚；字段映射继续对照真实 UserExt。编译真实 Languages/SettingsRedirects/ApiV3Settings/UserSettingsMutation，存储/皮肤/审核用适配器；未覆盖完整旧处理器、UserRepository缓存或MySQL。6个变动Java文件通过Java25语法解析，其他收尾检查另记。

人工链路：IDEA 重编译/重启 → 新登录 → 读取功能/位置/语言及语言时区目录 → 各 PUT 单字段 → 同键重试/旧键重放，不改变未提交值及后续设置。每页10/96成功、9/97及非整数422；模式0/1、6个布尔逐一核对旧PC/移动功能页、聊天室图片与回复自动订阅。位置关闭后另一账号/匿名同城与主页不能展示该用户位置，打开后正常，不能通过请求填写城市/IP。语言切换en_US/zh_CN、时区Asia/Shanghai与目录项；无效值422。首页只允许当前环境同origin/context，伪装host/换端口/凭据/编码路径/跨context422，清空/根归一正确。旧 function 整组保存与多组失败回滚、旧语言回退/地理取值保持。tools/api-v3-settings-smoke.mjs已扩展，默认只读；写开关仅改单字段再恢复，无法恢复的历史语言值跳过。运行测试未执行。

源码安全反漏扫：7条新路由均只接受Bearer，3写口settings.write与writes/幂等；身份不接受参数，白名单列且无真实位置/IP/UA数据输出，URL只存储，精确站点限制新API开放重定向，参数绑定，无Cookie身份写。匿名盾/黑名单/验证码/anonymous.viewSkips及静态/CDN/source map/目录/上传下载未改。旧CSRF兼容关系未改，旧prefix风险明确未修复；新旧真实鉴权/越权/CSRF/XSS/注入/滥用/隐私与完整风控仍需运行验证。全量目标保持未完成。


补充验证：SettingsLocaleOptions 提取实际语言/时区目录生成，专项测试真实执行 Latke TimeZones 并逐个验证返回ID均可由locale入参接受；合法锚点和编码文件名保留，越界路径拒绝。旧前缀风险亦用真实 Strings.isURL 验证了伪装域名通过原URL格式检查，并非仅推测。


本批收尾：7个变动Java文件语法解析通过；全部14条设置路由（本批新增7）文档/权限检查、5个旧入口复用、多组事务提交顺序及配置origin接线源码检查通过，21项写入回归通过，node --check/git diff --check通过。未进行全项目类型检查或HTTP/浏览器/MySQL，旧URL同步加固确认仍在等待，其他模块可继续推进。


### 2026-10-17 文章创作输入与共用频控准备

开始迁移 P0 文章创作，已通读 ArticleProcessor.addArticle、ArticlePostValidationMidware、ArticleMgmtService.addArticle/tag、章节同步、积分与发布事件。发现不能直接把旧 addArticle 放进幂等回调：旧方法自行提交，正文/作者计数/章节/职业来源提交后再做独立扣积分、标签图、活跃度与 ADD_ARTICLE 事件；控制器 TGIF 奖励甚至先于文章写入。编辑/删除也有外部事件或独立事务，须分别拆分。公开思绪 POST 在旧校验中明确关闭，v3 不能恢复；Article 类型4未定义，不能仅用范围0–6放行。新 API 尚未注册，不能把准备代码当成可用发布能力。

新增 ApiV3ArticleInputs 严格白名单准备层：普通/讨论/同城广播/问答/长篇，title255/content1024000/tags255，保留 Markdown 缩进和换行，打赏非负/正文前提、QnA 至少20、专栏/草稿19位ID、章节正整数上限、原 ColumnCoverMgmtService URL 校验；拒绝 author/IP/UA/status/apiKey/bypassCensor 等注入字段。作者/权限、保留词与标签规则、手机、新人报道/宵禁/TGIF、动态余额、内容审核、发布归属与业务事件尚未接入，后续必须完整复用而不能据 typed DTO 宣称全部校验。保留新手/周报/奖励等现有规则，费用计算后续必须 long/addExact 防溢出，不改变原费率。原始正文不能 trim 掉 Markdown 的四空格缩进。

ArticleWriteQuota 提取旧文章发布/编辑共用额度，旧 ArticlePostValidationMidware 保留 Admin/OP 例外和原失败响应。真实 SimpleCurrentLimiter 内部是 map.get→计算→put 的组合，不是整体原子；现在 Article/Profile/Breezemoon 包装同步，FishGame 按各 limiter 同步。首次并发实测鱼游投稿64路放行14次（原额度10），补齐同步后严格按原额度；不公开阈值到API.md/UPDATE_LOG。修复此前 ProfileWriteQuota 缺少第三方 limiter 导入的编译问题，设置测试脚本也纳入其真实依赖编译，避免语法解析漏掉缺失类型。既有其他原生 limiter 调用未统一审计，不声称全站频控都已解决。

实际验证：tools/api-v3-article-test.mjs 用Java25编译真实 Article/ArticleDraft/LongArticleColumn、ColumnCoverMgmtService/Repository、输入层、4个Quota包装及4个本地limiter源码，无伪造框架。3组输入检查（类型/思绪关闭/身份字段隔离/Markdown缩进、类型与长度/控制字符/打赏问答、专栏封面/草稿/章节范围）及6桶各64并发检查、跨账号/业务桶隔离通过，共10组。没有文章业务事务、成本/审核/职业/通知/搜索或HTTP测试；准备层没有路由，不能提交真实文章。后续检查结果另记。

人工链路（现有入口）：IDEA重编译/重启 → 普通账号从PC/移动发文、编辑 → 正常发帖与校验消息保持 → 授权测试环境以并发请求验证只按原额度放行，Admin/OP原例外保留；不在生产批量创建测试文章。资料/改名、清风明月和鱼游投稿/评论/投票分别混用新旧入口核对共用频控、不同业务桶互不影响，恢复时间沿用旧实现。缺登录、越权、API key/Cookie/CSRF关系和黑名单/验证码仍需运行回归。文章创建/编辑/删除v3仍待核心事务拆分后补完整手工链路。

源码安全反漏扫：没有新增路由、匿名路径、URL抓取、静态/CDN/source map/目录或上传下载。输入层先限制大小/类型/控制字符，标识符和章节数字白名单、封面复用既有校验；实际数据库/权限/可见性/敏感DTO与幂等尚未接入，不把输入测试当成这些检查通过。旧匿名盾、BeforeRequestHandler、CaptchaProcessor、anonymous.viewSkips以及旧路由鉴权/CSRF未改。完整目标仍未完成，下一步为文章正文/积分/章节/职业/草稿同事务与提交后事件；鱼游投稿仍等待用户提供 OAuth 规则或入口，旧首页跳转同步加固已获确认并完成。


本批收尾：10组文章输入/真实共享额度检查、6组设置回归、6个变动Java语法解析及旧角色例外/共享额度/Profile导入/未提前开放发布路由的源码检查通过；node --check和git diff --check通过。没有执行Maven、全项目类型检查、HTTP或实际数据库业务。目标仍进行中。

### 2026-10-17：首页跳转同步加固与文章积分事务准备

按用户明确授权，SettingsProcessor.updateFunction 改为共用 SettingsRedirects。测试中发现 URI.normalize 会折叠内部重复斜杠，调整为保留原始 URL，仅对校验路径做规范化，避免改变合法站内地址含义。6组设置隔离测试通过，覆盖根站点/部署路径/localhost、默认端口、中文、锚点、编码文件名/斜杠/百分号、重复斜杠、伪装主机、协议/端口差异、凭据及直接/编码/嵌套编码越界。旧入口 loginCheck + csrfMidware 保持，v3 Bearer/scope/幂等保持；未新增匿名入口、SQL、外部请求、静态资源或上传下载路径。API.md、UPDATE_LOG.md 同步公开契约。

新增 ArticlePublishPoints：将余额门槛与实际扣款分开建模，普通发布费包含粉丝数平方根附加，启用打赏扣固定启用费而不是打赏价格，问答只预检悬赏不提前扣取，匿名发布只检查门槛不收费。Rates 必须由后续业务接线传入既有服务端常量，不能取自请求。长整型合计防溢出；charge 要求外层事务、锁作者后读取余额、逐笔检查转账结果，返回流水ID供提交后索引，不自行提交/回滚或发事件。Store 需要真实适配 PointtransferMgmtService.transferInCurrentTransaction(indexAsync=false)，并由调用方持有账户对锁直到提交。

该辅助类尚未接入旧 ArticleMgmtService 或新路由，不能视为已修复旧发布原子性。脚本新增5组实际辅助类检查，使用明确的测试费率和 SQLite 事务适配器验证费用规则/溢出/事务前提/正文占位行与余额流水一起提交/第二笔失败后由调用者完整回滚/余额不足及匿名免费；不覆盖实际文章表、业务费率配置、转账服务、MySQL锁、缓存或HTTP。连同既有文章输入和真实额度测试共15组通过。

人工链路：IDEA重编译或重启后，从PC及移动个人设置的功能页保存本站正常首页跳转、中文或锚点地址，刷新后应保留；清空后恢复首页。提交伪装域名、不同协议/端口、部署路径外或编码越界地址，应报错且原设置不变。以 v3 functions PUT/GET 验证相同地址规则、缺 Bearer 拒绝、同一幂等键重试不重复修改；旧页面入口补测无登录与缺CSRF。黑名单IP到/test、验证码解封、anonymous.viewSkips影响及真实越权/CSRF/注入/滥用仍须运行验证。未执行Maven或浏览器联调，也未新增静态/CDN/source map/目录/上传下载变化。

下一步仍是将文章正文、作者计数、章节、职业来源、草稿及积分组合到同一个幂等事务，推迟缓存与外部事件到提交之后；准备代码没有开放发布入口。鱼游投稿继续等待OAuth规则或入口。

### 2026-10-17：文章发布核心事务已接入旧入口

上一批积分准备类现已接入 ArticleMgmtService。addArticle 保留原请求和返回ID，改为 withPublicationLock 包装自有事务；新增 addArticleInCurrentTransaction，保存文章、作者计数、标签关系、章节、版本、职业来源与发帖/打赏启用/同城广播扣款。原费率常量直接传入 ArticlePublishPoints.Rates，粉丝附加只取一次；问答悬赏仍只做余额门槛，不提前扣款，匿名仍只检查门槛。最低余额同时覆盖原门槛和实际费用，防止低打赏价格低于固定启用费时部分扣款；长整型求和防溢出。非法匿名整数及负悬赏拒绝，不改变合法页面/API输入。用户更新仅写文章计数、最新时间和标签计数，避免写回整行旧值。

锁顺序为 ArticleMgmtService 实例监视器→用户/系统账户对→数据库事务；事务先锁文章总数统计行，再锁作者行，标题、标签、统计采用当前锁定读取，不取已缓存对象。ArticlePublicationCaches 是 opt-in 线程边界，UserRepository/TagRepository/OptionRepository 仅在该边界内绕过缓存并登记提交/回滚后的处理；其他调用保持原路径。用户刷新复用带行锁的 durable cache refresh，失败清理缓存；标签失效后重读，统计失效后按需重读。边界关闭会检查事务已结束，未结束则拒绝缓存回填并移除ThreadLocal；幂等重试期间按key合并处理，只在整个边界结束后执行。

afterArticlePublished 独立承接积分冗余索引、标签图、活跃度、日志和 ADD_ARTICLE，均在提交且缓存边界关闭后尽力执行；单项失败不撤销已提交正文。带 afterCommit 回调的 withPublicationLock 在仍持有服务监视器期间执行回调，保留发布/编辑的本机事件顺序。v3 接线必须依据 executeWithOutcome.applied 判断是否调用，不能在重放上发事件。Publication 内部含完整持久化数据，只可作内部结果，不得直接序列化给客户端。提交结果不确定后的事件可靠补偿仍待实现，不能宣称已有持久化发布 outbox。

实际验证：文章专项20组通过（输入3、共享额度7、积分转换6、缓存边界4），事务部分用实际辅助类与 SQLite 适配器验证；缓存覆盖延迟处理、去重、回滚路径、失败隔离、并发线程隔离和事务未结束拒绝回填。写入/幂等/草稿21组、设置6组回归通过。9个变动Java文件通过Java25语法解析，脚本语法与diff空白检查通过。另尝试用本机 Maven 缓存及真实Latke/Jsoup进行 javac 类型检查，依赖遍历因缺 Jodd、Qiniu、okio 等产生144项错误，未完成整体类型检查；本批服务/Repository没有直接诊断不能作为编译通过的证据。不调用 Maven，不下载依赖，不连接应用数据库。

人工完整链路（需用户先 IDEA 编译/重启）：
1. PC/移动发帖页用已绑定手机的普通账号发布普通帖，进入详情与本人列表，核对正文/标签/作者计数、积分记录与通知；同城广播、打赏、问答和满足门槛的匿名帖分别核对原费用规则，问答不预扣悬赏、匿名不扣公开发帖费用。
2. 长篇入口新建/选择本人专栏并发布章节，详情、专栏顺序/数量、版本、职业来源均存在；选其他人专栏或重复章节号应失败，不能留下正文或扣款。
3. 在开发测试库用IDE断点模拟第二笔转账或职业来源写入失败，确认文章、标签关系、章节、版本、职业来源、积分流水及作者计数/余额全部回滚；刷新后缓存与数据库一致，未产生发布通知。余额仅够基础费但不足固定打赏启用费时，应在写入前拒绝。
4. 两个账号并发提交同一非讨论标题，至多一篇成功；同一账号并发不同标题仍受原发帖间隔与额度约束，成功数、计数和扣款一致。模拟提交后标签图/索引失败，已保存帖子仍返回成功，错误留日志。
5. 旧POST /article分别覆盖页面Cookie与apiKey；无登录拒绝，投稿不能指定他人作者；复查黑名单IP到/test及/validateCaptcha解封。开发模式关闭风控时正常发布仍一致。没有开放v3 POST articles，不能以只读v3测试替代此链路。

反漏扫与安全检查：本批未新增路由/模板/JS/CSS/SQL表/静态资源/CDN、sourceMappingURL、目录枚举或上传下载路径。旧/article仍挂loginCheck + permission +文章校验，未给兼容apiKey的旧入口直接追加CSRF；认证、中间件和 anonymous.viewSkips本批未改，源码复查BeforeRequestHandler黑名单跳转及CaptchaProcessor解封链路。查询值参数绑定，不接收外部URL或抓取资源，不新增SSRF面；输入与响应仍经过旧入口规则且仅返回文章ID，不新增敏感DTO。XSS、未鉴权、越权、CSRF、注入、批量滥用及实际黑名单/验证码运行回归未完成。编辑/删除事务、控制器新人/宵禁/TGIF奖励、草稿删除与v3 HTTP入口仍待迁移；当前只完成发布核心，不能把旧控制器全链路称为原子提交。

### 2026-10-17：投稿策略与周报奖励同事务

ArticleProcessor.addArticle 改从 loginCheck 写入的 User.USER attr 获取操作者，保留 Cookie 与 query/body apiKey 入口，作者仍由服务端覆盖；不再自行混合 Sessions 与 JSON body apiKey。旧路由、参数、成功ID、失败消息和发布后的草稿清理/警告保持。手机绑定、新人报道、宵禁、周报日期/时间/正文规则抽为 ArticleSubmissionPolicy，经 ArticleSubmissionMgmtService.submitInCurrentTransaction 接入上一批发布核心；初始化种子文章仍用原 ArticleMgmtService.addArticle，不被用户投稿策略阻挡。

历史查询直接取 owner + public + status!=invalid，按 articleCreateTime DESC 选一条，返回oId作为旧宵禁判断基准；不能改为只看USER_ARTICLE_COUNT或匿名发帖数，也不能把比较基准悄悄改成articleCreateTime。策略按服务器默认时区计算，保留19:30整分钟和08:00边界、满一小时间隔放行、开发模式仅豁免宵禁、标签包含“新人报道/新人报到”的旧子串语义；周报仍只识别“摸鱼周报”开头且长度13的标题，日期必须当天，周五9点起，首次标题正文至少128。已有标题不再次奖励，后续重复标题能否发布仍由原文章类型规则决定，不额外禁止讨论帖同名。

ArticleSubmission 是共用事务体，要求现有事务与发布缓存边界；先锁全局发布统计，再锁作者并取真实手机/角色、归一保留标签，读取当前历史后检查策略，周报奖励用 transferInCurrentTransaction(indexAsync=false) 写入，再调用 addArticleInCurrentTransaction。任一步失败由外层统一回滚，包括“奖励已写但正文/费用/职业失败”。奖励继续为既有1000业务常量，流向sys→作者、类型与原dataId空串保持；奖励流水ID加入提交后的冗余索引，已核对indexTransferAsync仅为两边账户登记rawId，不受传入方向影响。没有新增配置、表或权限。后续v3必须检查 commonAddArticle（旧路由真实规则），不能误用管理接口的articleAddArticle。

新增8组测试：4组纯策略检查覆盖手机、新人标签、宵禁精确分钟/满一小时/开发模式、周报标题/日期/周五时间/正文边界/已有标题不重复奖励、显式服务器时区；4组执行实际ArticleSubmission事务体，SQLite适配器验证奖励先于发布且不自行提交、奖励部分写入或正文失败整体回滚、策略拒绝无写入、已有周报不重复入账，以及实际绑定SQL按作者/公开/状态/创建时间选历史。SQL状态与匿名值采用真实Article模型常量。该适配器不执行完整ArticleSubmissionMgmtService、转账服务、MySQL行锁或HTTP。连同前述文章测试共28组通过。

7个本批变动Java源码通过Java25语法解析；node --check与git diff --check通过。尝试基于本机200个Maven缓存jar、真实Latke和IDE bundled Jsoup做javac类型检查，仍因Jodd/Qiniu/okio/emoji等缺失依赖失败（146项诊断，含旧ArticleProcessor的Base64引用），不视为完整编译通过。没有执行Maven、安装依赖、请求浏览器或连接应用数据库。必须由用户IDEA编译/重启后做运行验证，不能按现有服务旧行为判断源码。

人工完整回归：
1. IDEA编译/重启后，从PC/移动普通发帖入口分别用未绑定手机、成熟但尚无公开帖、已有公开帖账号投稿；未绑定拒绝，首帖缺新人标签拒绝，合法新人帖/普通帖成功，详情和本人列表可见，积分及作者计数正确；query/body apiKey与页面登录态各测一次，不能由请求指定其他作者。
2. 在测试环境用IDE策略层断点设置now覆盖08:00、19:30、19:31及满一小时边界，确认生产模式宵禁与开发模式豁免符合旧规则；同一时刻以不同用户时区调用，规则仍按服务器时区。
3. 用策略层测试时钟覆盖周五09:00和正确当天周报标题，127/128长度分别拒绝/通过；错误日期、周五08:59或非周五拒绝。成功时奖励与发帖费用都只出现一次；模拟奖励后正文、第二笔扣款或职业来源失败，应没有正文、奖励、扣款、章节或职业记录，缓存回到持久化值。并发同标题只允许原规则的一次奖励；已有同名讨论帖不得再次获奖。
4. 提交有效本人草稿后发布成功，原草稿清理；不存在或非本人草稿沿用旧警告字段，不能删除他人草稿，也不能据此把v3的草稿要求降级。普通/长篇/打赏/问答/匿名规则继续按上一节链路回归。
5. 无登录、越权/伪造作者、原角色限制、请求滥用、黑名单/test与验证码解封必须运行回归；旧兼容apiKey的写入口未直接追加CSRF。源码复查/article仍有loginCheck、permission、ArticlePostValidation，anonymous.viewSkips无本批变化。未新增静态/CDN/sourceMappingURL、可枚举目录或上传/下载返回路径，无URL抓取或新增SSRF面；绑定SQL和ID响应未增加注入/敏感字段面，XSS渲染仍依赖既有文章链路，实际未验证项不默认通过。

下一步是把旧内容审核校验抽为可复用边界，再接v3 content.write、角色权限、幂等、草稿同事务和白名单创建结果。当前未注册POST /api/v3/articles，不能绕过内容审核或恢复思绪投稿。全量目标继续进行中。

### 2026-10-17：v3 文章创建已接线

新增 POST /api/v3/articles，ApiV3ArticleWriteProcessor 经 content.write + 写中间件注册，身份只取 ApiV3Processor.IDENTITY。ApiV3ArticleMgmtService 先校验 commonAddArticle（不误用后台articleAddArticle），在 withPublicationLock 内执行幂等事务；先锁发布统计再锁当前作者并复核角色权限，按ApiV3ArticleInputs严格白名单解析。当前创建支持普通/讨论/同城广播/问答/长篇、匿名、打赏、声明、列表/评论/通知开关、专栏章节、奖励标记及本人草稿。未知作者/IP/UA/apiKey/免审/状态字段拒绝，作者/IP/UA由服务端注入。

旧 ArticlePostValidationMidware 拆为旧响应适配器 + ArticleValidationService（真实标签、保留词、emoji、审核、配额适配）+ ArticleValidation（共用规则），旧POST/PUT继续用同一响应格式、字段、标签过滤、Admin/OP额度例外和原免审角色集合。18个普通失败分支、审核失败及思绪仅POST禁用分支均迁移；保留旧打赏正文实际按MAX_ARTICLE_CONTENT_LENGTH比较的历史行为，不擅自收紧旧API。v3使用既定102400打赏正文上限，禁止免审参数并额外审核打赏正文；新人报道奖励标记限制依据add-article.js的isGoodArticleEligible明确规则，仅用于新API，不改变旧客户端。保留词处理只读启动时初始化的缓存，TagQueryService无写事务，v3审核日志只记操作者与拒绝结果，避免在幂等回调内调用旧censorLog的额外写入。

ApiV3ArticleCreation 是共用创建事务体：锁定并授权→复制规范化请求（防止校验修改幂等输入）→审核→ArticleSubmissionMgmtService用户投稿→本人草稿删除→白名单id/permalink结果。草稿不存在或属于其他人时404，整次发布/奖励/扣款/章节/职业来源回滚；未提供draftId不碰其他草稿。发布中旧ServiceException可能包含同名文章作者（含匿名真实作者）的链接，新API统一返回无身份信息的publication_rejected；数据库异常保留cause以便幂等重试。结果和业务同事务保存，fresh applied才调用提交后事件；成功重放不再调用审核、配额、扣款、草稿删除或通知。提交结果不确定后重放的事件补偿/outbox仍未实现，不把“尽力发送”当成可靠投递。

新路由只接受Bearer，无Cookie/apiKey回退，因此没有追加破坏API客户端的页面CSRF校验。原/article路由与中间件配置不变。阅读新建文章仍走现有ContentQueryService的可见性检查，DTO正文通过Markdowns.clean渲染；创建结果不返回全文对象、作者、IP/UA、奖励正文或凭据。发布后沿用既有通知、角色、搜索、TTS事件：已核对通知按权限写入引用、TTS仅向配置的百度接口发送截取文本，Baidu/Search目标来自服务端配置，封面仅存储不抓取；没有新增由请求直接指定的服务端请求目标。外部服务和通知运行效果尚未验证，不能据此宣称全部第三方链路已验收。

类型检查发现此前ApiV3Processor两处CookieHeaderNames.SameSite.STRICT引用实际不存在；进一步javap确认本项目Latke Cookie#setSameSite接收String，不能仅改为Netty枚举Strict。现统一ApiV3RefreshCookies构造登录/刷新/清除Cookie，保留既定名称、路径、HttpOnly/Secure/host-only/Strict和max-age规则，新增真实Latke Cookie类型编译与属性测试，修复此前仅工具层测试漏掉的适配错误。认证接口字段没有变化。整体javac检查仍受缺Jodd/Qiniu/emoji/Caffeine等本机依赖阻挡，未完成完整编译。

实际验证：文章专项39组（此前28 + 共用校验7 + v3创建事务4）通过，使用真实Article/Role/CensorResult、共用验证器和创建事务体，标签/审核/持久化服务用适配器，SQLite验证正文与扣款及草稿一起提交、外人/不存在草稿整体回滚、权限/审核拒绝无写入、无草稿时不清理。共用校验覆盖Markdown缩进、保留词/标签归一、思绪新建关闭但历史编辑保留、实际免审角色、奖励前提和旧长度行为、真实CensorResult拒绝与审计回调、新人奖励标记。Token工具5组 + 真实Cookie1组、写入/幂等/草稿21组回归通过。路由清单更新为650条，浏览器引用398条；API.md及UPDATE_LOG.md同步公开契约，不写内部策略。未执行Maven、连接应用MySQL或启动浏览器。

新增tools/api-v3-article-smoke.mjs，默认只验证缺Bearer、Cookie不能替代Bearer、缺scope、未知作者/免审字段及思绪禁用；显式API_V3_ARTICLE_WRITE=1才创建测试草稿并发布一篇持久化文章，验证白名单、重放、异体409、草稿已消耗及详情可读，失败时仅清理本脚本草稿。当前v3尚无文章删除，需要测试者通过旧页面删除该测试文章；脚本没有运行，必须用户IDEA编译/重启后才执行，不用旧服务响应判断新代码。

完整人工链路：
1. IDEA编译/重启，以具content.write/content.read和普通发帖权限、已绑定手机的测试账号登录，先运行脚本默认拒绝检查；纯Cookie、旧apiKey、缺Bearer应401，缺scope/权限403，作者或免审注入字段422，type=3拒绝。生产防扫描启用时确认黑名单仍跳/test，验证码解封与anonymous.viewSkips范围保持。
2. 在开发环境显式运行写模式：创建本人草稿→POST /api/v3/articles→返回id/permalink→GET详情与本人文章列表→草稿404；同键同正文返回原结果，同键改正文409，积分/作者计数/职业来源/通知不得重复。请求更换IP/UA但正文不变时仍按原幂等业务内容重放。PC/移动网页旧POST和PUT同步核对相同校验消息、历史编辑、原免审角色与原返回字段。
3. 对不存在、他人草稿以及他人专栏/重复章节号投稿，整次发布拒绝，没有文章、奖励、扣款、版本、章节、职业来源残留，其他人草稿不变；正常长篇新建/选择本人专栏成功。先把目标账号准备成成熟但没有公开帖与已有公开帖两种状态，分别验证新人标签、手机和宵禁规则。
4. 分别发布普通/讨论/同城/问答/匿名/打赏稿，核对各自权限和原积分规则、私密阅读边界；打赏正文审核拒绝不能留下主正文。新人标签+goodPost=true拒绝，其他合法奖励标记按原业务生效；按前节测试时钟验证周报奖励整体回滚。正文含script/危险链接等样本后GET详情确认清洗，匿名文章不得泄露真实作者，重复标题错误不得泄露其他作者。
5. 模拟正文/第二笔扣款/职业/草稿失败、并发同键、并发不同键和提交后事件失败，核对事务回滚、缓存回填、幂等与日志；执行未鉴权、越权、CSRF、XSS、注入、SSRF目标控制、请求滥用和敏感字段检查。没有新增公开静态/CDN/sourceMappingURL、目录或上传/下载路径，本批无新SQL表/Repository配置。以上HTTP/MySQL/外部集成/验证码实际行为均未验证，不默认通过。

本批文件：ArticlePostValidationMidware、ArticleValidation/ArticleValidationService、ApiV3ArticleWriteProcessor/ApiV3ArticleMgmtService/ApiV3ArticleCreation/ApiV3ArticleInputs、ApiV3Processor/ApiV3RefreshCookies；ArticleValidationTest/ApiV3ArticleCreationTest/ApiV3RefreshCookiesTest；article-test/article-smoke/token-test三脚本；API.md、UPDATE_LOG.md、AGENTS.md、开发计划与路由清单。下一步继续文章编辑/删除与评论写入，账号/PKCE/上传/Socket等其余迁移及运行验收仍未完成，目标保持进行中。

收尾：12个变动Java文件语法解析、三脚本node --check与git diff --check通过。刷新Cookie迁移后补回读取Cookie循环仍需的Latke Cookie import；重新类型检查不再出现Cookie/本批新类诊断，仍有151项其他依赖缺失诊断，整体检查未通过。token脚本已直接编译并运行真实Latke Cookie工厂及测试，避免仅语法解析漏掉类型适配。article-smoke额外提供API_V3_LEGACY_KEY用来确认旧apiKey不能替代Bearer，脚本尚未运行。

### 2026-10-17：文章编辑核心同事务

开始迁移编辑链路，通读旧ArticleProcessor.updateArticle、ArticleMgmtService.updateArticle、标签差异更新与LongArticleParagraphService段评迁移。旧编辑与旧发布一样，先提交正文再独立收费；article/comment缓存还会发布未提交对象。现updateArticle保留旧签名与响应，通过withPublicationLock自带事务，复用新updateArticleInCurrentTransaction与提交后的afterArticleEdited；v3不能直接把旧updateArticle套进幂等回调。暂未注册PUT /api/v3/articles/{id}，还需编辑输入/当前内容合并/权限/审核/幂等接线，不能因核心拆完即宣称接口可用。

编辑先锁全局文章统计、认证作者，再当前读锁定目标文章；重新检查归属、文章可编辑状态与周报标题不可修改，防止只依赖控制器的缓存快照。原qnaOfferPoint只能增加、rewardPoint>0更新打赏价格与正文、rewardPoint=0不关闭既有打赏、匿名属性保持原值、类型/声明/列表开关、版本“五分钟后且非纯空白变化”及长篇段评迁移规则保持。用户行只写标签计数，避免整行旧数据覆盖积分/资料。旧控制器改用loginCheck的User.USER attr，Cookie与query/body apiKey身份一致，不让合法query apiKey被只读Sessions的逻辑误拒绝。

ArticleEditPoints按既有base + round(sqrt(followers)) + 2*(collections+watchers)计算，全部长整型检查并限制到转账int容量，防止加法/乘法溢出。免费窗口仍是now-oId严格大于五分钟后才收费，窗口内仍保留旧余额门槛；新启用打赏仍扣固定启用费，匿名不扣编辑或启用费。真正扣款沿用ArticlePublishPoints.charge + transferInCurrentTransaction(indexAsync=false)，任何失败随正文/版本/标签/章节/段评位置回滚。积分冗余索引与UPDATE_ARTICLE事件只在提交且缓存边界关闭后尽力执行，服务监视器仍保持本机发布/编辑事件顺序。

ArticleRepository和CommentRepository在ArticlePublicationCaches范围内绕过缓存并延后失效/回填，其他调用路径保持。ArticleRepository写入仍过滤articleHotScore生成列。段评迁移在该边界内按目标文章/段评类型做FOR UPDATE当前读，按oId顺序锁行；迁移只更新paragraphId/kind/index/status/sharpURL五字段，避免用整条旧评论覆盖并发正文或权限字段。服务内部Edit记录含私有持久化字段，只可内部传递，不得作API DTO。没有新表、配置或客户端字段。

实际验证：新增ArticleEditPointsTest五组，真实费用/扣款助手 + SQLite适配器覆盖费用公式、严格五分钟边界、免费但需余额门槛、匿名免扣、启用费最低余额、溢出、两笔扣款同事务、第二笔失败完整回滚；不覆盖完整ArticleMgmtService/Repository、段评算法、MySQL锁或HTTP。文章专项由39增至44组通过；6个本批Java源码语法解析通过，测试类由Java25实际编译。整体javac仍因Jodd/Qiniu/emoji等缺失依赖失败（146项诊断，含旧ArticleProcessor Base64），不视为编译通过。未运行Maven、浏览器或应用数据库。核对真实Latke Ids.genTimeMillisId自身有同步与等待，删除旧提交后额外50ms等待不替代ID保护。

完整人工链路（用户IDEA编译/重启后）：
1. PC与移动端文章详情→编辑本人普通帖→保存→详情/列表与积分记录核对；五分钟内、恰好边界及超过五分钟分别核对原编辑费与余额门槛，匿名帖无编辑费。编辑奖励正文启用打赏时核对固定启用费；已启用后传0仍保留旧打赏，不把本轮误当作关闭能力。
2. 长篇编辑正文/调整段落、专栏、章节号，核对章节归属/数量、段评锚点与孤立状态、历史版本；评论正文、私密属性和其他非位置字段不受迁移影响。悬赏降低、周报改标题、重复标题/章节、他人文章与锁定文章应拒绝。
3. 用开发断点模拟第二笔扣款、章节或段评迁移失败：正文、标签计数、章节、版本、段评位置、余额与流水全部回滚，刷新后缓存一致，不发更新事件；提交后索引/事件失败不回滚已保存正文。并发编辑/评论/发布需进一步MySQL验证，SQLite不能证明行锁行为。
4. 页面Cookie与query/body apiKey各测一次；无登录、伪造作者、缺权限/越权、CSRF兼容、XSS清洗、注入及请求滥用做运行回归；黑名单到/test与validateCaptcha解封、anonymous.viewSkips不变。v3创建与读取回归也需覆盖，因为共用Repository的缓存边界扩展到了编辑。新旧控制器路由、中间件与页面模板未改，没有新静态/CDN/sourceMappingURL、目录、上传/下载路径或服务端URL目标；所有运行项尚未验证。

本批文件：ArticleMgmtService、LongArticleParagraphService、ArticleRepository、CommentRepository、ArticleProcessor、ArticleEditPoints、ArticleEditPointsTest、article-test脚本、API.md、UPDATE_LOG.md、AGENTS.md和开发计划。下一步接v3编辑输入和HTTP边界，继续删除/评论及其他模块，全量目标保持进行中。


### 2026-10-17：v3 文章部分编辑与思绪回放

新增 `PUT /api/v3/articles/{articleId}`，沿用 content.write、commonUpdateArticle、写入中间件和幂等事务。ApiV3ArticleEditInputs 只归一化提交字段用于重放摘要；ApiV3ArticleEditing 在锁定本人文章和章节后合并当前状态，再经共用校验、审核与 updateArticleInCurrentTransaction 保存。缺失/他人文章统一404，锁定文章403；作者、匿名属性、免审、新人奖励和草稿字段不可修改，返回只含id/permalink。未提交字段保留；已启用打赏传0不关闭，悬赏只能增加。历史思绪可编辑，普通文章不可转换为思绪；离开思绪类型需显式提交普通正文。思绪记录检查分隔符、结构和安全整数，不代表已模拟所有坐标组合。

补齐实际启用打赏所需余额检查，并保留匿名免费编辑对已有负余额的兼容。正文、费用、版本、标签、章节和段评位置依旧同事务；只有首次实际提交后发送编辑事件。同键重放旧编辑不得覆盖随后新编辑。章节与专栏父子锁顺序仍需真实MySQL并发验证，不能以SQLite检查代替。

PC/移动思绪页面使用页面专用原始记录回调，FTL以?js_string编码，重建每帧后由Lute清洗HTML，避免清洗整段结构记录破坏坐标或模板字符串注入。旧API的articleContent转义不变；三单元历史记录时间读取和末尾空记录处理同步修正。自定义文章模板覆盖需同步回放链路。已执行yarn run build生成两端min.js，未直接修改产物；未改CSS、依赖清单或锁文件。

实际验证：Java25文章专项54组通过（包含部分字段合并、权限/归属门禁、保存/审核失败回滚和真实PC/移动FTL解析编码）；隔离DOM三组通过，使用实际CDN Lute与模板产出的脚本验证危险标签/属性移除、合法HTML保留及分隔符/引号/反斜线/闭合标签往返。四个源码/生成JS语法检查通过，五文件JS版本审计0发现，CDN脚本HTTP200且父目录404，未见sourceMappingURL。路由清单651条、浏览器引用398条。未执行Maven；广泛Java类型检查仍被缺少Jodd、Qiniu、emoji等依赖阻塞，不能宣称完整应用编译通过。

安全源码复查：PUT只取Bearer身份，角色和归属在事务中复核，无Cookie/apiKey回退；未向兼容旧客户端的入口追加CSRF中间件。入参白名单/类型/长度/控制字符检查、参数绑定查询和ID/permalink响应避免伪造身份、注入和敏感数据输出；没有调用者提供的服务端URL请求。新增固定CDN脚本加载和思绪HTML清洗已做静态及隔离DOM检查。未新增上传/下载路径或可枚举目录，anonymous.viewSkips、黑名单和验证码解封链路本批不改。真实未鉴权/越权/CSRF/XSS/注入/SSRF/批量滥用、匿名首访/黑名单/test/validateCaptcha及运行时敏感字段仍待联调；静态检查不等于运行通过。

人工完整链路（必须先在IDEA重新编译或重启服务端，再强制刷新浏览器）：
1. 开发环境取content.read/content.write令牌→创建测试草稿→POST发布→PUT只改正文→GET详情/本人列表，确认标题/标签/类型保留、草稿已消耗，响应只有id/permalink。同键同体重放无重复费用；同键异体409；新键再次编辑后重放旧键，正文仍为最新版本。
2. 第二账号编辑返回404，不存在ID同样404；锁定文章403。缺Bearer401、缺scope403，Cookie/apiKey不能替代Bearer；未知/只读字段、空对象、非法ID、超长/控制字符/错误类型、审核拒绝与普通帖转思绪需拒绝且原内容/余额不变。
3. PC与移动旧页面编辑和v3编辑分别覆盖五分钟内外、匿名负余额、启用打赏余额不足与成功、已启用后传0、悬赏降低、周报标题、长篇章节移动/脱离/重复号、历史版本与段评锚点。注入事务失败后正文/余额/流水/版本/章节一起回滚；真实MySQL并发编辑/章节排序/旧入口混合写入需单独验证。
4. PC/移动思绪详情覆盖历史三单元与四单元记录、增删/换行/制表符、引号/反斜线/结束script标签、危险HTML与合法粗体，核对播放/预览一致、无脚本执行；CDN加载失败显示提示，隐藏/屏蔽内容不回放。回归普通文章、讨论可见性与打赏正文。
5. 验证匿名入口首访、黑名单到/test、验证码解封与开发风控开关；核对静态资源响应无source map/版本泄露、父目录不枚举。tools/api-v3-article-smoke.mjs默认只做拒绝检查，显式启用写模式后会持久化测试文章，运行后通过旧页面删除；本轮尚未运行该脚本。

本批实际文件（相对项目根目录）：
- 后端：src/main/java/org/b3log/symphony/util/{ApiV3ArticleInputs,ApiV3ArticleEditInputs,ApiV3ArticleEditing,ArticlePublishPoints}.java；service/{ApiV3ArticleMgmtService,ArticleMgmtService,ArticleQueryService}.java；processor/{ApiV3ArticleWriteProcessor,ArticleProcessor}.java；repository/ArticleRepository.java。
- 前端：src/main/resources/js/{article.js,m-article.js,article.min.js,m-article.min.js}；src/main/resources/skins/classic/{pc,mobile}/article.ftl。
- 验证：src/test/java/org/b3log/symphony/util/{ApiV3ArticleEditInputsTest,ApiV3ArticleEditingTest,ArticleThoughtTemplateTest,ArticleEditPointsTest}.java；tools/{api-v3-article-test,api-v3-article-smoke,thought-render-test}.mjs。
- 文档：API.md、UPDATE_LOG.md、AGENTS.md、docs/api-v3-development-plan.md、docs/api-route-inventory.md。需将API.md同步到网站API文档文章。

全量目标仍进行中：文章删除/评论写入、编辑器专用元数据读取、账号/PKCE、其他设置/背包、上传/Socket/支付等和运行验收尚未完成；鱼游投稿仍等待用户提供OAuth校验规则/入口，不能绕过。


### 2026-10-17：文章删除事务与 v3 DELETE

新增DELETE /api/v3/articles/{articleId}，要求content.write、commonRemoveArticle、写入中间件和幂等键。按Bearer作者锁定当前用户和本人文章，缺失/他人统一404，锁定403；只返回id。新键删除不存在文章404，同键重放保留原结果且不再执行数据库或外部清理。旧删除控制器改为读取loginCheck写入的User.USER，兼容原Cookie和apiKey入口；没有改旧字段或响应格式。

旧普通/管理员入口改成显式事务包装，共用removeArticleInCurrentTransaction，沿用发布锁与缓存边界，按统计→作者→文章顺序加锁。正文、评论清理、用户/城市/全局/标签计数、章节、版本、职业来源撤销、标签关联、通知、打赏和投票清理同事务。评论集合使用锁定当前读，避免管理员删除使用先前快照漏读已提交评论。文章/评论remove不再提前逐出缓存，在提交或回滚后统一失效。afterArticleRemoved仅提交后尽力清理Algolia、ES和音频；单个失败不阻止其他清理，也不撤销数据库结果。没有持久化外部清理重试/outbox，进程中断或外部失败仍可能留下索引/文件，不能宣称跨系统可靠原子删除。

ArticleRemovalPolicy保留普通删除的24小时、周报、状态、评论/订阅/收藏/赞踩/打赏/感谢限制；24小时与周报也在事务内重查，管理员强制删除保持豁免。打赏/感谢通过RewardRepository.existsForUpdate锁定当前读，异常向外传播，不再把查询失败或long→int截断当成0。FOR UPDATE的间隙锁与跨旧评论/投票/打赏入口并发仍需真实MySQL验证；当前不宣称所有旧入口并发已解决。

实际验证：文章专项61组通过（此前54+删除规则5+真实RewardRepository SQL2）；新增覆盖24小时恰好边界/差1毫秒/未来时间/周报、状态与所有互动拒绝、超int打赏计数、数据库异常阻断，以及SQLite执行实际SQL的ID/type参数绑定和注入样例。SQLite适配去掉FOR UPDATE，不验证MySQL锁；没有执行真实ArticleMgmtService的整套数据库清理、HTTP或外部删除。8个相关Java文件Java25语法解析通过，两个工具脚本语法检查、git diff --check通过。广泛类型检查仍因本地缺少Caffeine/Jodd/Qiniu等依赖失败，不等于完整应用编译通过；未运行Maven。路由清单652条、浏览器引用398条。

反漏扫与安全源码检查：新增DELETE没有匿名放行，身份只取Bearer，角色和归属事务内复查，幂等按操作者和目标隔离；空请求体或空JSON，不接受额外字段，ID和请求键走通用校验。响应只有id，无正文、作者/IP/UA/凭据；删除无新增HTML渲染面。SQL绑定参数，服务端无调用者URL抓取；音频清理只沿用持久化音频URL和现有后端。未直接给兼容旧apiKey写入口追加CSRF。BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware及CaptchaProcessor解封、anonymous.viewSkips本批不改。没有JS/CSS/FTL、CDN/sourceMappingURL、目录枚举或上传下载返回路径变更；运行时未鉴权/越权/CSRF/XSS/注入/SSRF/批量请求滥用/敏感泄露和完整反扫描回归尚未验证。

完整人工测试（先IDEA重新编译或重启，未确认前不运行应用请求）：
1. 开发环境准备一个满24小时、无任何互动的本人测试文章及content.read/content.write令牌；GET详情→DELETE带唯一键→返回仅id→详情和本人列表消失、相关章节/标签/统计同步。相同键再DELETE返回原结果；换新键404；第二账号删除本人文章404，且不能读取首账号的幂等结果。
2. 新发布文章、周报、有评论/订阅/收藏/赞/踩/打赏/感谢的文章分别DELETE，应422且原数据和计数不变；锁定文章403。缺Bearer401、缺scope/角色403、Cookie/apiKey不能替代Bearer、非法ID/额外字段/缺幂等键需拒绝。24小时边界需固定时钟或预置测试数据，不能随意改生产数据。
3. PC/移动本人文章的旧删除按钮与旧apiKey调用回归相同限制和响应；管理员强制删除带多评论、版本、章节和职业来源的专用测试文章，核对相关统计与撤销。注入章节/职业撤销/关联数据清理失败，确认所有数据库删除和计数回滚、缓存不提前消失、无外部删除。
4. 提交后模拟Algolia/ES/音频失败，确认文章数据库仍已删除且其他清理继续；手动核对需补偿的索引/文件。MySQL下并发删除/编辑/评论/表态/关注收藏/打赏/章节排序，以及管理员删除和旧入口混用，检查无孤立记录、负计数、重复职业撤销或死锁后部分提交。
5. 回归黑名单/test、验证码解封、匿名首访与开发风控开关；确认公开响应无高敏字段和新静态资源泄露。tools/api-v3-article-smoke.mjs默认拒绝检查；启用发文写模式会验证新文章删除422，只有显式API_V3_ARTICLE_DELETE=1并提供一次性API_V3_ARTICLE_DELETE_ID才删除已有测试文章。脚本本轮未运行。

本批实际修改文件：src/main/java/org/b3log/symphony/service/{ArticleMgmtService,ApiV3ArticleMgmtService}.java；processor/{ArticleProcessor,ApiV3ArticleWriteProcessor}.java；repository/{ArticleRepository,CommentRepository,RewardRepository}.java；新增util/ArticleRemovalPolicy.java；新增src/test/java/org/b3log/symphony/util/{ArticleRemovalPolicyTest,ArticleRemovalSqlTest}.java；tools/{api-v3-article-test,api-v3-article-smoke}.mjs；API.md、UPDATE_LOG.md、AGENTS.md、本计划与docs/api-route-inventory.md。无新表、SQL迁移或repository.json变化。需同步网站API文档文章。

全量目标继续进行：评论写入、编辑器元数据、账号/PKCE、其他设置/背包、上传/Socket/支付等及运行验收仍待完成，鱼游投稿仍等待OAuth规则/入口。


### 2026-10-17：本人文章编辑源码读取

新增GET /api/v3/me/articles/{articleId}/source（content.read），由ApiV3PersonalProcessor→ApiV3PersonalQueryService→ArticleRepository.getOwnedEditorSource提供，用户只来自Bearer。ApiV3ArticleEditorDto.sql在同一次查询按owner+ID过滤并显式投影文章编辑字段，通过LEFT JOIN仅关联本人有效专栏；章节articleId在既有迁移中有唯一键。不复用公开文章详情或依赖Sessions的页面组装，不修改旧GET响应。不存在与他人同404；本人可读锁定/不可用文章源码，读取不赋予编辑权限，PUT仍检查角色/状态。

白名单返回id/title/content/tags/type/rewardContent/rewardPoint/qnaOfferPoint/commentable/showInList/statement、只读anonymous/status/editorType/createdAt/updatedAt/permalink及columnId/columnTitle/columnCoverURL/chapterNo。原始Markdown、思绪结构记录和打赏源码不转HTML、不做页面转义，客户端用于编辑；不能将整份响应原样PUT。commentable兼容历史数字与布尔值，无有效本人专栏时四个专栏字段为空串。IP/UA/作者身份/凭据/审核详情不投影、不返回；响应经通用no-store与Vary Authorization边界。

实际验证：文章专项66组通过（此前61+编辑源码DTO/SQL5）。新增覆盖完整返回字段白名单、原始特殊串往返、本人打赏正文、历史布尔值、真实SQLite执行SQL的owner/ID绑定与注入样例、锁定匿名文章、本人有效专栏、外人/无效/缺失专栏元数据清空、表名前缀及内部标识符校验。四个相关Java文件Java25语法解析、两个smoke脚本语法检查、git diff --check通过。SQL与DTO为真实代码，SQLite不证明MySQL或HTTP；没有执行应用请求、Maven或完整应用类型编译。此前完整类型检查缺依赖，仍待IDEA确认。路由清单653条，浏览器引用398条。

源码安全与反漏扫：新路由要求Bearer content.read，无Cookie/apiKey身份回退，没有匿名放行；所有权与ID同一参数化查询，防越权及注入。GET无写入和外部URL抓取，不新增CSRF/SSRF写面；原始内容只在本人JSON返回，前端显示需按格式渲染，不能据此声称实际客户端XSS已验证。字段白名单与专栏归属过滤避免高敏字段或他人元数据输出；普通读请求滥用和跨账号隔离仍待运行。BeforeRequestHandler黑名单/test、CaptchaProcessor解封、AnonymousViewCheckMidware首访/计数和anonymous.viewSkips本批不改；没有新增产品JS/CSS/FTL、CDN、sourceMappingURL、目录枚举或上传/下载返回路径，未执行无关前端构建。完整运行反扫描和安全回归尚未验证。

人工完整链路（先通过IDEA重新编译或重启服务端）：
1. 本人普通/匿名/讨论/问答/长篇/历史思绪文章详情→GET source→编辑器回填，确认标题、原始正文、付费正文、评论开关/声明/列表开关、悬赏及章节正确。使用需要修改的字段调用PUT→重新读取source和公开详情，确认修改成功、未提交选项保留，源码不被HTML转义；思绪分隔符/引号/反斜线应原样往返。
2. 第二账号读取第一账号source与不存在ID同404；缺Bearer401、缺content.read403、非法ID422，Cookie/apiKey不能替代Bearer。带有他人userId/userName的查询参数不能改变返回主体；检查响应no-store及无IP/UA/凭据/作者字段。
3. 本人锁定文章仍能GET source，PUT拒绝；不可用状态不会被GET恢复。无章节、失效专栏及异常指向他人专栏的测试数据只能返回空专栏字段，不得带出外人标题/封面。外人即使能读公开文章或已支付打赏，也不能读取编辑源码。
4. PC/移动旧编辑页和公开详情回归；黑名单IP到/test、验证码解封、匿名首访及开发风控开关回归。tools/api-v3-personal-smoke.mjs增加可选API_V3_OWN_ARTICLE_ID/OTHER_ARTICLE_ID只读检查，article-smoke写模式改用source正文编辑；两者均未运行应用请求。

本批实际文件：src/main/java/org/b3log/symphony/processor/ApiV3PersonalProcessor.java、service/ApiV3PersonalQueryService.java、repository/ArticleRepository.java、新增util/ApiV3ArticleEditorDto.java；新增src/test/java/org/b3log/symphony/util/ApiV3ArticleEditorDtoTest.java；tools/api-v3-article-test.mjs、api-v3-article-smoke.mjs、api-v3-personal-smoke.mjs；API.md、UPDATE_LOG.md、AGENTS.md、本计划及docs/api-route-inventory.md。没有新增表或SQL迁移。API.md需同步网站API文章。

全量目标继续进行：评论写入、账号/PKCE、其他设置/背包、上传/Socket/支付等及运行验收仍未完成；鱼游投稿仍等待用户提供OAuth校验规则/入口。


### 2026-10-17：评论写入共用校验准备

本批抽出CommentValidation与CommentValidationService，旧CommentAddValidationMidware/CommentUpdateValidationMidware改为响应适配；没有注册v3评论写路由。prepare保留原额度→保留词处理→trim后非空/4096长度检查，存储字段保留Markdown缩进及原尾空白；moderate单独调用实际CensorFactory，旧发布入口仍先检查目标文章/评论开关/原评论存在再审核，避免改变调用顺序与外部审核成本。原审核拒绝文案和日志保留；原管理员及特殊角色仅免业务额度，仍审核。CommentWriteQuota的发布与编辑桶与旧配置相同，并同步access以免并发get/put放大额度。未新增配置项。

旧评论发布、长篇段评和编辑控制器及对应校验均取loginCheck已写入的User.USER，统一Cookie与query/body apiKey身份，修复仅query apiKey时中间件通过但控制器拿不到用户的情况；所有相关注册路由均先经loginCheck。请求字段、JSON响应、审核、绑定手机、讨论邀请和目标校验规则不改，未给兼容apiKey的旧接口直接追加CSRF。

实际验证：tools/api-v3-comment-test.mjs 8组通过，编译真实Comment/Role/CensorResult、校验器及限流器源码，覆盖保留词先后、原始空白保留/审核trim、空与4096边界及历史trim长度行为、拒绝额度不继续处理、发布/编辑标记、原角色例外仍审核、真实CensorResult拒绝回调和原文案、两种额度各64并发及用户/桶隔离。审核与保留词采用适配器，不代表真实外部审核服务已联调。6个相关Java文件Java25语法解析、脚本语法和git diff --check通过；广泛类型检查仍因Caffeine/Jodd/Qiniu等本地缺依赖失败，没有新改文件直接诊断，不能宣称完整应用编译通过。未执行Maven或应用HTTP，未改前端资源，不需无关构建。

后续评论事务迁移必须处理：旧addComment先提交正文再独立转账；普通评论可能同时涉及评论者/文章作者/sys三方账户，应在开启事务前按确定顺序持有所有账户对锁，不能事务中再新增可能互等的锁。回复标记通知已读会调用旧带事务及刷新事件的方法，需事务内状态写入+提交后刷新；自动订阅须用FollowMgmtService事务体，不可在v3幂等回调直接调用旧watchArticle。原评论必须与目标文章一致且阅读者可见，文章有效状态/讨论邀请/评论开关、匿名余额门槛、私密额外费用、段评继承、职业事件、版本和缓存也需完整核对；普通输入/审核准备不等于评论写接口已完成。旧updateComment同样先提交正文再收费，待拆分。实时事件中完整内部文章/评论不得直接对外序列化。

安全源码与反漏扫：没有新增路由或匿名入口，旧路由loginCheck/permission链路保留，未改BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware、CaptchaProcessor解封或anonymous.viewSkips。共用校验不增加SQL/URL请求/HTML生成或敏感DTO，保留原内容处理，不能声称解决全部XSS/旧跨文章回复等存量问题。没有产品JS/CSS/FTL、CDN版本/sourceMappingURL、目录枚举、上传/下载返回路径变动。真实未鉴权/越权/CSRF/XSS/注入/SSRF/滥用/敏感泄露与反扫描尚未运行验证。

完整人工回归（先IDEA重新编译或重启服务端）：
1. PC/移动文章详情→普通评论/回复→成功显示；长篇选择段落→段评/回复→正确挂到段落；编辑本人评论→返回更新HTML及版本数量，正文缩进/保留词/末尾@用户与原行为一致。费用/余额/通知/自动订阅/职业来源仍需记录以供后续事务迁移比对。
2. 以Cookie、仅query apiKey、仅JSON body apiKey分别发布和编辑；应统一识别操作者。无凭据拒绝、他人编辑拒绝、未绑手机/未邀请讨论帖/关闭评论/缺失目标继续按原规则拒绝。检查三个入口均未绕过登录或权限中间件。
3. 空白/超过4096、恰好4096、含首尾空白的边界、保留词及审核拒绝用例，核对旧响应和无写入；原额度例外角色与普通用户分别测试，并发额度仅作用于相应用户/发布或编辑桶。不得用生产账号消耗额度或制造内容。
4. 回归旧评论列表、回复展开、PC/移动实时插入与敏感字段；黑名单到/test、验证码解封、匿名首访和开发风控开关。没有运行浏览器或smoke请求，8组单元检查不能代替这些链路。

实际文件：新增src/main/java/org/b3log/symphony/util/{CommentValidation,CommentWriteQuota}.java与service/CommentValidationService.java；修改processor/middleware/validate/{CommentAddValidationMidware,CommentUpdateValidationMidware}.java及processor/CommentProcessor.java；新增src/test/java/org/b3log/symphony/util/{CommentValidationTest,CommentWriteQuotaTest}.java与tools/api-v3-comment-test.mjs；API.md补充旧评论/编辑鉴权说明和curl，UPDATE_LOG.md、AGENTS.md、本计划同步。无新表或SQL迁移，路由数量仍653。需同步网站API文章。全量目标保持进行中。


### 2026-10-17：评论编辑同事务与 v3 PUT

新增PUT /api/v3/comments/{commentId}，comments.write + 写入中间件 + commonUpdateComment；输入仅content（必填、非空、最多4096、限制控制字符）和可选visible布尔值。作者、目标文章、匿名、审核、回复和段落信息不接受客户端提交。归一化请求单独用于重放摘要，事务体使用副本执行保留词处理/审核，不把IP/UA或当前数据库默认值放入摘要。角色、本人评论、目标文章可见性、文章有效状态及评论有效状态在事务里复核；他人/不存在/文章不可见同404，锁定403。缺visible保留当前可见性，结果只有id/permalink；重放不覆盖随后编辑、重复扣款或发事件。

CommentMgmtService旧编辑改为显式事务包装，updateCommentInCurrentTransaction经CommentEditing复用状态迁移，沿用ArticleMgmtService.withPublicationLock（服务锁→操作者/sys账户对锁→缓存边界→事务），统计→操作者→文章→本人评论锁序；用户状态仍须正常。先锁定读取本人评论到文章的映射，再锁文章并锁本人评论复核关联。只更新commentContent/commentVisible/commentIP/commentUA，避免整行旧快照回写回复数、状态、匿名或段评快照；归一化沿用Emotions、尾空白/@保护、上传提示去除和Runes控制符处理，UA按既有上限截断。旧接口仍将实际归一化正文送原渲染器并返回原字段。

五分钟规则严格按评论ID时间差>300000毫秒：窗口内不收费、不新增版本；窗口外评论和文章均公开时即使正文相同仍收固定编辑费，版本只在非空白正文变化时新增。评论或文章任一匿名时免编辑费，没有新增免费窗口余额门槛。余额不足、转账返回失败、版本/正文写入失败均随外层事务回滚；使用transferInCurrentTransaction(indexAsync=false)，提交后afterCommentEdited才索引和发送UPDATE_COMMENT。CommentUpdateNotifier是该事件的唯一监听器，本批补上私密评论第三人@提醒过滤，未增加公开WebSocket载荷。提醒仍是提交后尽力触发，没有持久化outbox，崩溃补发未实现。

实际验证：评论专项17组通过（此前校验/额度8+新输入2+真实CommentEditing协调器7），用Java25、实际模型/校验/限流/迁移代码及SQLite适配器；覆盖身份/字段注入、类型/空白/长度/控制符、五分钟恰好边界、匿名负余额免扣、元数据不变、可见性保留、UA上限、余额不足/转账/版本失败整体回滚、空白变化收费不记版本、owner+ID SQL真实绑定和注入样例、缺失/越权/错文章状态拒绝。SQL测试去掉FOR UPDATE，不能证明MySQL锁。8个相关Java文件语法解析通过；广泛类型检查仍因本地缺Caffeine/Jodd/Qiniu等依赖失败，新改文件未见直接诊断，不能称完整应用编译成功。脚本语法与git diff --check通过，路由清单654条、浏览器引用398条。没有Maven、应用HTTP或外部审核/通知联调。

源码安全及反漏扫：新PUT只接受Bearer，无Cookie/apiKey身份回退；API自身不依赖Sessions，所有权和当前角色复核，SQL值参数绑定。原兼容apiKey入口没有直接追加CSRF；原始内容不在新写响应返回，旧HTML仍经原Markdown清洗；输入/输出白名单阻止凭据/IP/UA/完整对象泄露。无调用者URL抓取，不新增SSRF能力。业务额度与旧编辑共用，幂等缓存边界不提前发布对象。BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware首访与计数、CaptchaProcessor解封和anonymous.viewSkips本批不改，无新增匿名入口；没有产品JS/CSS/FTL、CDN版本/sourceMappingURL、目录或上传/下载返回路径变化。运行时未鉴权/越权/CSRF/XSS/注入/SSRF/批量滥用/敏感泄露、私密通知投递及完整防扫描尚未验证。

完整人工链路（先IDEA重新编译或重启，确认前不运行应用请求）：
1. 开发环境准备一次性本人评论/段评及comments.write/content.read令牌：文章详情读取→PUT编辑→id/permalink→列表或段评线程重新读取，确认正文更新且作者/匿名/回复/段落快照/创建时间/回复数保留。省略visible应保留原私密范围，显式true/false切换符合原规则，PC/移动旧编辑按钮返回HTML和版本数量正常。
2. 同键同体重放，无重复费用/版本/通知；同键改正文或visible返回409。新键第二次编辑后重放第一次，数据库仍是第二次正文。测试五分钟内、恰好边界与超出，普通/匿名评论、匿名文章、正文不变或纯空白变化分别核对原费用和版本规则；余额不足不保存正文。
3. 以IDEA断点或开发测试适配模拟扣款、版本、正文失败，核对正文/版本/余额/流水一起回滚、缓存与旧页面一致、无UPDATE_COMMENT。失败转账的底层null返回可能丢失具体数据库异常分类，真实MySQL死锁重试仍需验证。
4. 第二账号修改返回404；不可查看讨论文章及缺失目标同404，锁定文章/失效评论403，角色或scope缺失403、无Bearer401。Cookie/apiKey不能替代Bearer，身份/匿名/段评/状态注入、空对象、visible错类型、控制符/超长和审核拒绝不得改数据。原旧Cookie/query/body apiKey编辑同步回归；评论关闭后本人历史编辑仍按原规则，不把编辑误当新投稿。
5. 私密评论编辑并@第三人：第三人不得收到正文之外的编辑提醒，评论作者/文章作者可见性保持；公开评论@和讨论邀请回归。通知接口重新读取检查无敏感字段。MySQL并发新旧编辑/新增回复/删除/投票、失权讨论及同键重试，核对无计数覆盖或部分提交；旧addComment仍是正文提交后独立转账并有整行缓存写入，跨发布/编辑的并发保护和发布迁移仍未完成，不能据本批宣称完整评论事务链已解决。
6. 黑名单IP到/test、validateCaptcha解封、匿名首访和开发风控开关回归。tools/api-v3-comment-smoke.mjs默认拒绝检查，只有显式API_V3_COMMENT_WRITE=1并指定一次性本人评论才永久编辑两次；可指定所属文章和页码执行普通评论读取核对，段评需手工检查。脚本尚未运行，测试不会自动恢复正文或费用。

实际修改文件：src/main/java/org/b3log/symphony/service/CommentMgmtService.java、repository/CommentRepository.java、event/CommentUpdateNotifier.java、processor/ApiV3Processor.java；新增processor/ApiV3CommentProcessor.java、service/ApiV3CommentMgmtService.java、util/ApiV3CommentInputs.java、util/CommentEditing.java；新增src/test/java/org/b3log/symphony/util/ApiV3CommentInputsTest.java、CommentEditingTest.java；修改tools/api-v3-comment-test.mjs、新增tools/api-v3-comment-smoke.mjs；API.md、UPDATE_LOG.md、AGENTS.md、本计划和docs/api-route-inventory.md。没有新增表/SQL迁移或repository.json变化；需同步网站API文档文章。

全量目标继续：评论发布/回复/删除/采纳/感谢等其余写入、账号/PKCE、其他设置/背包、上传/Socket/支付等及实际运行验收仍未完成，鱼游投稿仍等待用户提供OAuth校验规则/入口。


### 2026-10-17：评论发布多收款方锁与积分规则准备

评论发布可能同时向文章作者和sys付款。新增AccountPairLocks作为唯一账户对锁池，PointtransferMgmtService直接transfer/transferInCurrentTransaction和单账户对外层包装均从同一池取锁，保留原正反向规范键、重复对共用和重入语义。executeWithTransferLocks先规范化、去重、按键排序取得完整集合，回调结束后逆序释放；同一有序边界可嵌套使用已持有集合的子集，禁止新增账户对，避免事务中临时扩展锁集。新的多对服务包装拒绝已开启事务；ArticleMgmtService.withPublicationLocks保持服务监视器→全部账户对→缓存边界→事务的顺序，原withPublicationLock以actor/sys单对委托，提交后的回调仍在缓存边界关闭后且服务锁/账户对锁持有期间执行。本批没有改变转账金额、记录或HTTP契约。

CommentPublishPoints抽出并已用于旧addComment的余额预留及实际转账列表，供后续事务迁移复用。普通评论基费付文章作者、自评基费付sys，私密再独立付sys（保留两个转账类型）；任一匿名时不实际转账，仍按原规则预留普通/私密余额，只有评论本身匿名时增加匿名余额门槛。普通评论发表在匿名文章下不额外增加匿名门槛。预留使用long求和防int溢出，零金额转账仍保留，避免改变既有流水行为。旧前置匿名门槛文案与先后顺序保留，水贴通知/活跃度仍只在原公开场景触发。本批尚未把旧addComment的正文与积分改成同一事务，不能据此宣称评论发布原子提交或开放v3发布。

实际验证：tools/point-transfer-lock-test.mjs 5组通过，运行真实锁池及SQLite适配，覆盖反向账户同锁、去重、子转账重入、异常释放、嵌套子集/扩展拒绝和ThreadLocal清理、旧单对竞争者被任一相关锁拦住、无关对可并行、64线程逆序列表不会并持同对、两收款方扣款适配的提交/失败回滚及锁持续持有。此测试没有执行真正PointtransferMgmtService或MySQL；不能证明全部数据库/旧锁嵌套行为。评论专项21组通过（此前17+发布积分计划4），新增矩阵覆盖普通/自评/私密/匿名组合、余额门槛与实际扣费差别、零费用、不可变计划和超int预留。5个相关Java文件Java25语法解析、两个脚本语法和git diff --check通过。广泛类型检查仍因Caffeine/Jodd/Qiniu等依赖失败，没有完整应用编译通过结论。未执行Maven或应用HTTP。

调用链复查：现有executeWithTransferLock调用者为外部应用积分调整和活跃度奖励，均在业务事务开始前加锁；ArticleMgmtService单对边界现委托多对包装，v3文章创建/编辑/删除及评论编辑沿用原调用处。直接转账内部仍对同一个ReentrantLock重入，不再另建池。新发布接线时须先从可信文章记录确定所有目标，事务内复查文章作者/归属未变；不得在已持有的集合外调用额外转账。Java有序锁只解决本机账户对加锁顺序，MySQL用户行顺序、其他旧事务和跨实例死锁仍须独立验证，不能将多对锁等同全局账户锁。

安全及反漏扫：没有新增或修改HTTP路由、请求/响应字段、权限/匿名入口、SQL表或Repository注册。BeforeRequestHandler黑名单/test、CaptchaProcessor解封、AnonymousViewCheckMidware首访/计数及anonymous.viewSkips本批不改。工具和计划不输出用户凭据，积分计划仅服务端使用，不增加公开DTO、HTML/URL输入、外部抓取或SQL拼接，未新增CSRF/XSS/注入/SSRF/敏感泄露面；余额溢出与并发逻辑仅做隔离验证。没有产品JS/CSS/FTL、CDN/sourceMappingURL、目录枚举或上传下载路径变化；未鉴权/越权/滥用/全部安全和防扫描运行检查仍未验证。

人工完整回归（先IDEA重新编译或重启）：
1. 两个开发测试账号在PC/移动评论入口分别发普通/自评/私密评论，核对作者或sys两类积分流水、评论余额与文章作者余额，确认费用与此前规则一致；评论匿名/文章匿名及余额低于门槛分别覆盖，不应把预留余额误记为实际扣款。
2. 回归旧及v3文章发布/编辑/删除、v3评论编辑与幂等重放；确认锁包装替换后仍提交成功，失败不留下未释放锁或提前缓存更新。评论编辑的五分钟和匿名规则与发布规则不同，不得混用两个计划。
3. 用开发测试账号回归昨日活跃度奖励与credit应用积分调整的单对包装、相同请求编号重放、正反向同时转账；检查流水及余额无重复，不能用生产账号联调。
4. 并发普通转账/上述业务与两收款方测试适配，确认逆序收款方输入不会在Java层死锁；数据库死锁、断连、提交结果不确定及跨实例并发需真实MySQL另测。工具的SQLite整体回滚不代表旧addComment已具备整体回滚，真实发布迁移尚待完成。
5. 回归登录/API凭据、黑名单/test、验证码解封和开发风控开关。没有执行浏览器、应用请求或真实转账。

本批实际文件：新增src/main/java/org/b3log/symphony/util/AccountPairLocks.java、CommentPublishPoints.java；修改service/PointtransferMgmtService.java、ArticleMgmtService.java、CommentMgmtService.java；新增src/test/java/org/b3log/symphony/util/AccountPairLocksTest.java、CommentPublishPointsTest.java与tools/point-transfer-lock-test.mjs，修改tools/api-v3-comment-test.mjs；AGENTS.md及本计划同步。HTTP契约本批不变，无新增API.md/UPDATE_LOG记录；之前新增的API文档仍需同步网站文章。路由数量仍654。全量目标保持进行中，下一步继续发布事务、通知已读和自动订阅的同事务接线。


### 2026-10-17：旧评论发布同事务核心

CommentMgmtService.addComment现为显式事务包装：先从可信文章记录取得作者，借ArticleMgmtService.withPublicationLocks持有actor/sys与actor/文章作者完整账户对，再开始事务调用addCommentInCurrentTransaction；v3后续必须直接加入该核心，不能在幂等回调套旧addComment。事务按文章统计→已排序参与用户→文章→原评论锁定，复核作者未变，重新检查用户状态、原最小发言间隔及Admin/机器人例外、全站评论开关、匿名余额门槛和CommentPublishPoints预留规则。作者变更时失败，不允许在事务中扩大锁集。旧控制器手机/讨论/段评校验入口仍保留，新v3须在锁内复查其可见性和权限，不能据此认为旧跨文章回复约束已解决。

正文、原评论回复数、文章/全局/标签/用户计数及最近评论信息、tag_article统计、初始版本、职业来源、回复通知已读、自动订阅和全部扣款同事务提交，任何异常finally回滚。文章/用户/标签/原评论/关联更新改为列白名单，避免整行缓存快照覆盖积分、元数据、状态或计数；tag_article读取使用锁定当前读。段评回复继承原段落字段及旧缺省值，正文仍按旧emoji/尾空白/@保护/上传提示规则处理。CommentProcessor移除发布后的第二次watchArticle，由核心按已锁定的当前用户偏好或讨论帖规则执行FollowMgmtService.setInCurrentTransaction，保留原自动订阅不额外发关注通知的行为。

NotificationStateMgmtService新增markDataReadInCurrentTransaction，按userId+原评论dataId绑定选择，仅hasRead列更新；截止ID固定，每批200，后到通知不受影响。NotificationRepository新增对应事务守卫/锁定查询；不再调用旧makeRead的自带事务/提前刷新链路。提交后afterCommentPublished才索引每笔actor→收款方流水、刷新回复者未读数、水贴通知/活跃度和ADD_COMMENT_TO_ARTICLE。publishReadStateRefresh保留旧刷新消息count字段，避免count缺失导致前端误提示。提交后单项失败只记日志，不反转已提交正文；没有持久化outbox或可靠补发，进程中断/外部通知失败仍有补偿限制。

CommentPublishPoints.charge要求当前事务并重读已锁余额，捕获每笔转账ID/收款方；transferInCurrentTransaction(indexAsync=false)返回null/空串即失败，由外层回滚。匿名只检查预留、不造实际扣款；普通/私密、自评费用保持之前计划，正文/积分调用原常量，不新增配置。旧服务的意外异常对客户端用systemErrLabel，完整原因仅服务器日志，业务校验文案保留。

实际验证：评论专项23组通过（此前21+实际支付协调器2），SQLite适配覆盖第二笔系统扣款失败时正文/已读/自动订阅适配及双方账户/流水一起回滚，成功时统一提交；协调器事务守卫、匿名余额预留和无实际扣款也已覆盖。通知专项9组通过（此前7+回复已读2），编译真实NotificationRepository/NotificationStateMgmtService并执行SQL，覆盖actor/dataId隔离、SQL注入样例、已读不重复、随调用者回滚、401条跨批及截止之后新通知保留；SQLite去掉FOR UPDATE，不验证MySQL锁。支付适配不执行真实CommentMgmtService的全部持久化，不能把它称为整套发布集成测试。5个相关Java文件语法检查、两个工具脚本语法与git diff --check通过；广泛类型检查仍因Caffeine/Jodd/Qiniu等缺依赖失败，不能宣称完整应用编译成功。没有Maven、服务HTTP或浏览器联调。

安全源码及反漏扫：本批改造旧POST评论/段评核心，路由仍loginCheck+permission+原正文/段评中间件，当前用户仍取登录中间件的User.USER并兼容Cookie/query/body apiKey。没有给兼容客户端的入口直接追加CSRF。新SQL值均绑定，通知只改actor+dataId匹配行，自动订阅目标只能是已锁文章，写列白名单避免无关字段覆盖；没有新增客户端URL抓取/SSRF或HTML渲染面。旧成功JSON字段及公开事件组装入口不变，内部Publication含私密对象，禁止直接序列化；完整旧实时事件/模板脱敏仍待运行回归。BeforeRequestHandler黑名单/test、CaptchaProcessor解封、AnonymousViewCheckMidware首访/计数、anonymous.viewSkips不改。没有产品JS/CSS/FTL、CDN/sourceMappingURL、目录枚举、上传下载返回路径、新表或Repository注册变化。未鉴权/越权/CSRF/XSS/注入/SSRF/批量滥用/敏感泄露及完整防扫描运行检查尚未完成。

人工完整链路（先IDEA重新编译或重启服务端）：
1. 开发账号在PC/移动文章详情发表普通/自评/私密/匿名评论及段评回复，核对code/段评commentId、列表/段评线程/实时卡片、各层评论与回复计数、版本和职业来源。核对普通转账收款方为文章作者、自评及私密附加为sys，匿名只检查余额门槛不实际收费；作者/匿名/私密与段落快照不变。
2. 准备回复者未读原评论通知：回复后仅本人该dataId截止前通知已读，其他通知/其他用户及之后到达通知保留，未读数刷新正常且零未读不误弹新通知。普通贴自动订阅按当前个人偏好；讨论帖保留强制订阅、自评规则，已订阅时不重复关系/计数。取消偏好和不存在原评论分别回归。
3. 开发断点模拟第二笔收费、版本、职业来源、通知已读或订阅写入失败，核对正文/父回复数/计数/版本/来源/订阅/已读/余额/流水全部回滚，缓存未提前发布，无实时新卡片或未读刷新。成功后单独模拟索引/活跃度/通知/事件失败，正文与积分仍已提交；需要人工补偿的外部结果不能靠重复旧POST自动解决。
4. 两账号并发新旧发文/编辑/删除、评论回复、转账与关注，核对无积分/资料/计数旧快照覆盖。真实MySQL死锁、跨实例与提交结果不确定仍需验证；预先选定作者变化时应失败，不能付款给未预锁账户。既有旧跨文章/不可见原评论引用边界未擅自改规则，新v3接线必须严格检查。
5. Cookie、query/body apiKey各覆盖POST/段评与普通编辑；未登录、缺权限、未绑手机、讨论未邀请、评论开关、正文审核/空白/超长、发言间隔、余额不足继续拒绝且无数据库变化。回归黑名单到/test、验证码解封、匿名首访与开发风控开关，并核对公开响应和PC/移动HTML/WS没有IP/UA/凭据。没有执行实际请求，隔离测试不能代替这些链路。

本批实际文件：src/main/java/org/b3log/symphony/service/CommentMgmtService.java、NotificationStateMgmtService.java、NotificationMgmtService.java；repository/NotificationRepository.java；processor/CommentProcessor.java；util/CommentPublishPoints.java；src/test/java/org/b3log/symphony/repository/ApiV3NotificationSqlTest.java、util/CommentPublishPointsTest.java；API.md、UPDATE_LOG.md、AGENTS.md及本计划。无新增路由，仍654条；API.md旧评论说明和公开日志已同步，需更新网站API文章。v3发布HTTP、段评/原评论锁内校验和幂等响应仍待接线，目标保持进行中。

### 2026-10-17：v3 评论发布与旧实时推送可见性

新增 POST /api/v3/articles/{articleId}/comments，仅取 comments.write Bearer 身份。字段限 content/visible/anonymous/parentId/paragraphId，严格检查类型、长度、控制字符和ID/锚点格式。ArticleRepository 先投影可信收款作者，事务前取得完整账户对锁；事务内按统计、排序用户、文章和原评论顺序锁定，复核用户状态/权限/手机、文章作者/状态/可评论性。ApiV3CommentTarget 检查原评论属于当前文章、有效且正文可读；独立段评从服务端正文提取快照，段评回复继承原段落及失效状态，拒绝冲突锚点。复用共用审核/额度与 addCommentInCurrentTransaction，正文、积分和幂等结果同事务，只返回 id/permalink；重放不再扣费或推送。缺失目标仍允许读取已有幂等成功结果。

用户已批准同步加固旧推送私密正文与当前文章权限。ArticleChannel 重新读取用户、文章和评论可见性，不再信任连接的 articleType 或发布快照；无权访问文章/无效评论不推送。CommentRealtimeVisibility 为接收者复制数据，渲染前清空禁读的 commentContent，公开 JSON 保留原白名单。PC/mobile 模板共用此数据，无模板/前端产物改动。CommentNotifier 的回复、@用户、@participants、楼主及订阅提醒也按当前可读范围过滤；被过滤参与者不计入提醒扣费人数。段评实时卡片补齐服务端关联字段。

另发现匿名评论的旧 commentAuthorId 仍是真实作者ID，已说明兼容影响并请求用户确认，答案尚未收到，未改变旧字段。新接口匿名发布的实时身份保护仍待这一决策，不能宣称匿名链路验收通过。

实际修改文件：
- src/main/java/org/b3log/symphony/processor/ApiV3CommentProcessor.java、service/ApiV3CommentMgmtService.java、repository/ArticleRepository.java、util/ApiV3CommentInputs.java、util/ApiV3CommentTarget.java、util/CommentRealtimeVisibility.java。
- src/main/java/org/b3log/symphony/processor/channel/ArticleChannel.java、event/CommentNotifier.java。
- src/test/java/org/b3log/symphony/util/ApiV3CommentPublicationTest.java、tools/api-v3-comment-test.mjs、tools/api-v3-comment-smoke.mjs。
- API.md、UPDATE_LOG.md、AGENTS.md、docs/api-route-inventory.md及本计划。

验证：评论专项28组通过（原23+5），覆盖输入/注入、服务器段落快照、同文章/私密/无效父评论、失效段评回复/冲突锚点、逐接收者正文和对象隔离。执行真实输入/目标/可见性工具、模型、校验、额度及支付协调器；UserExt 非测试路径的保留词配置用临时 Symphonys shim，不能据此验证实际审核配置。9个Java文件语法通过。针对服务/控制器/推送的类型检查受 Caffeine/Jodd/Qiniu 等缺依赖影响失败（315个错误，变更类无直接错误诊断），修正过一次不存在的手机常量引用；不代表完整编译通过。脚本语法与diff检查通过，路由655条、浏览器引用398条。没有Maven、HTTP、浏览器或真实数据库测试。smoke增加发布鉴权/不存在/注入检查，API_V3_COMMENT_PUBLISH=1才持久化发布并验证重放/冲突和普通列表读回，段评用返回链接人工验证。

源码安全/反漏扫：新增写入显式Bearer/scope，无匿名裸入口；目标/父评论权限锁内复核，返回字段白名单。Cookie不能独立触发v3写入，未给旧apiKey兼容入口硬加CSRF。Markdown沿用清洗，SQL值绑定，无新URL抓取/SSRF。共用业务额度叠加v3额度，真实并发/跨实例未测。BeforeRequestHandler黑名单/test、CaptchaProcessor解封、AnonymousViewCheckMidware首访/计数与anonymous.viewSkips本批不改，必须运行回归。无产品静态资源/CDN/sourceMappingURL、目录枚举、上传下载返回路径、新表或Repository注册变化。匿名作者ID问题明确待办；未鉴权、越权、CSRF、XSS、注入、SSRF、批量滥用、敏感泄露及完整防扫描的运行结论尚未取得。

人工全链路（先由用户在IDEA重新编译或重启，确认后再联调）：
1. localhost:8080准备已绑手机、有comments.write及评论权限的开发账号和可评论文章，POST普通评论后GET列表读回。PC/mobile打开permalink，核对卡片、回复/文章计数、版本、积分、职业来源、自动订阅；旧Cookie/query/body apiKey发表普通/段评继续回归。
2. 同键同体只新增一次且返回同ID，同键异体409；发布后编辑再重放POST不覆盖正文。断点模拟扣费/版本/职业来源失败，应无正文、计数、余额、已读、订阅或幂等成功记录；重试只提交一次。文章删除后已有成功结果可重放。
3. 回复同文章公开和本人/楼主可读私密评论成功；他人私密、跨文章、删除/无效原评论404且无计数变化。长篇页面取data-long-paragraph-id发表段评，再只传parentId回复；链接定位正确。过期独立锚点、非长篇、冲突锚点拒绝，旧失效段评回复保留快照/状态。v3段落枚举/独立分页读取留后续接口，当前用页面验证。
4. 楼主、评论者、普通读者三个PC/mobile会话在线：私密正文放唯一标记，前两者正文正常，普通读者JSON及cmtTpl不含标记，也不收到私密@/回复/订阅提醒。改讨论邀请、文章/用户/评论状态或评论可见范围后，旧连接不得按旧快照继续接收禁读内容。匿名作者ID调整仍待用户决定，单独记录，不默认通过。
5. 无Bearer/仅Cookie/apiKey拒绝401，缺scope/权限/手机或禁止评论403，文章不可见404；空白/超长/控制字符/非布尔/伪造作者或快照/余额和间隔条件422，额度429，冲突409；均无写入或推送。回归黑名单/test/validateCaptcha、匿名首访/计数和开发风控开关，并检查HTTP/HTML/WS无IP/UA/凭据，匿名ID残留保持待办。

API.md/UPDATE_LOG.md已同步，仍需同步网站API文章。总体目标进行中：评论删除/采纳/感谢、认证补充、其他设置/背包、上传/Socket/支付和运行验收等未完成；鱼游投稿仍待OAuth规则/入口。

### 2026-10-17：段落读取、单条评论与匿名推送 ID

上一目标轮是实际进展：新增发布路由、事务接线、推送正文权限和独立验证。本轮继续新增 GET /api/v3/articles/{articleId}/paragraphs、GET /api/v3/articles/{articleId}/paragraph-comments、GET /api/v3/comments/{commentId}。ApiV3ParagraphProcessor 三路均挂 optionalContent 与 AnonymousViewCheckMidware；仅从 v3 Bearer identity 取读者。ApiV3ParagraphQueryService 在序列化前检查文章可见性，段落接口必须长篇。单条评论须有效，私密正文和匿名作者沿用 commentDto，不改变原评论列表响应。

段落目录按正文顺序分页（默认20、最大50），includeContent=true额外返回完整带data-long-paragraph-*属性的正文HTML；否则不序列化整篇HTML。LongArticleParagraphService 新增 typed RenderedContent 与 annotateMarkdown，旧 annotateRenderedContent 返回形态不变。段评列表是根评论/回复的平铺分页，paragraphId可选，state为active/orphaned/all，sort为oldest/latest/hot；未匹配的锚点返回空列表。ApiV3ParagraphQueries 对条数和列表使用同一绑定条件，排除普通评论、失效评论及未知段落状态；只投影必要评论列，不查IP/UA。段落计数只对当前页最多50个锚点做GROUP BY，包含有效回复，不复用旧100条或整篇评论加载。

发现并处理新接口的链接标题边界：ShortLinkQueryService 原默认展开不检查被链接文章的可见性。新增显式标题回调，v3 使用 ApiV3ContentQueryService.paragraphTitleLookup，只返回匿名可读文章标题；所有读者使用同一公开标题规则，受限链接保留URL，查库失败传播而不回退非受控查询。v3段落读取、附锚点正文与投稿findParagraph均用同一回调。旧默认重载保留原行为，仅修复replacement中的$与反斜杠字面处理。历史commentParagraphSnapshot可能包含受限标题，新增评论/段评读取只返回id/kind/index/state关联，不返回存储快照；当前摘要仅由已授权文章正文生成。没有更改已有公开GET响应字段。

已验证的剩余兼容问题：正文中包含受限站内裸链接时，旧页面无条件标题展开产生的锚点可能与v3公开标题规则不同，旧段评计数/定位和后续编辑迁移需要专项处理，不能宣布历史段评全面兼容。隔离测试已证明这类锚点差异，未伪造一致性。新v3锚点应从本轮GET获取；接口目录/列表可读不等于历史锚点迁移完成。后续需完成兼容映射或经用户批准统一旧规则，再验收所有历史数据链路。

用户本轮明确批准匿名实时 commentAuthorId 改为空字符串。ArticleChannel 在权限与模板完成后，将白名单结果经 CommentRealtimeVisibility.publicPayload 复制脱敏，内部真实作者仍用于私密可读判断及本人标记。正常公开ID及有权阅读者正文不变；旧PC/mobile模板只在条件中使用作者ID，channel.js/channel-2.js消费cmtTpl，article.js/m-article.js使用commentIsCurrentUser，未发现需随之改写的第一方JS调用。API.md旧评论说明和UPDATE_LOG已给客户端迁移提示，上一批的待确认问题至此关闭；实际WS仍需运行验收。

实际修改文件：
- src/main/java/org/b3log/symphony/processor/ApiV3Processor.java、ApiV3ParagraphProcessor.java、channel/ArticleChannel.java。
- src/main/java/org/b3log/symphony/service/ApiV3ParagraphQueryService.java、ApiV3ContentQueryService.java、ApiV3CommentMgmtService.java、LongArticleParagraphService.java、ShortLinkQueryService.java。
- src/main/java/org/b3log/symphony/util/ApiV3ParagraphQueries.java、ApiV3ParagraphDto.java、CommentRealtimeVisibility.java。
- src/test/java/org/b3log/symphony/util/ApiV3ParagraphReadTest.java、ApiV3CommentPublicationTest.java、service/LongArticleParagraphAnnotationTest.java。
- tools/api-v3-comment-test.mjs、api-v3-comment-smoke.mjs、api-v3-paragraph-test.mjs；AGENTS.md、API.md、UPDATE_LOG.md、docs/api-route-inventory.md、本计划。

实际验证：评论/读取专项40组通过（含新增SQL/DTO6组、复用现有可见性5组、匿名推送ID1组），SQL执行真实片段并覆盖130条跨页/无重复、active/orphaned/all与文章/锚点隔离、无IP/UA投影、计数条件一致、排序次序、最大页偏移、字段/SQL注入、隐藏正文及匿名DTO不被元数据恢复。SQLite不是线上MySQL，未测并发快照。独立段落专项5组通过，编译真实LongArticleParagraphService、ShortLinkQueryService和Jsoup，覆盖混合中文/列表/代码/表格/媒体、旧JSON与新typed HTML锚点、唯一ID/位置/快照上限、公开标题回调失败关闭与$处理、读取/投稿查找一致；Markdown/表情/媒体渲染用identity shim，仅证明解析与回调接线，不证明真实Markdown清洗或页面渲染。14个变更Java语法通过，针对新服务/控制器的真实类型检查仍因缺依赖失败（315错误，变更类无直接错误诊断），未宣称完整应用编译成功。没有Maven或服务HTTP/浏览器访问。smoke补三类读取/非法参数，API_V3_LONG_ARTICLE_ID启用只读锚点/HTML/列表/详情检查；显式发表可自动取v3锚点并读回。生成清单658路由、398浏览器引用。

源码安全与反漏扫：新增匿名读取均经匿名中间件，文章匿名开关/讨论权限用Bearer读者显式判断；Cookie/apiKey不能补成受限读者，显式无效Bearer先拒绝。GET无业务写入/CSRF副作用，SQL值绑定、筛选和排序白名单，offset受限；正文继续共用清洗，当前摘要纯文本，响应禁止原始Article/User/IP/UA及历史快照，匿名实时只保留空作者ID。标题展开只做本地查询，没有客户端URL抓取/SSRF。解析大正文的批量滥用仍需真实Firewall及匿名盾回归，不能用页大小证明CPU限流通过。BeforeRequestHandler黑名单/test、CaptchaProcessor解封、anonymous.viewSkips和首访/计数盾未改；DEVELOPMENT关闭风控时不能代替线上结论。无产品JS/CSS/FTL、静态CDN/sourceMappingURL、目录枚举、上传下载路径、表/Repository注册变化。未鉴权、越权、XSS、CSRF、注入、SSRF、批量滥用、敏感泄露和完整防扫描的运行验收均未执行。

人工全链路（用户先通过IDEA重新编译或重启并确认）：
1. localhost:8080用公开长篇和具备content.read/comments.write的开发账号GET paragraphs?includeContent=true，核对每个返回id都出现在HTML且PC/mobile定位一致，翻页无重复；不带includeContent不返回整篇HTML，空/非长篇/缺失文章及越界页结果符合文档。用取得的锚点POST段评，再GET paragraph-comments与comments/{id}读回正文、parentId、链接，回复后仍定位同一段。
2. 准备超过100条段评及回复，三种排序/分页与count对照；移除正文段落后state=orphaned/all能查到记录，active排除，非法锚点/筛选/size=51拒绝422，合法但无记录锚点空列表。普通评论不能混入段评；单条普通评论paragraph=null，失效/删除评论或不可见文章404。
3. 匿名、未邀请用户、合法邀请者、楼主/评论者、管理员分别读公开/禁止匿名/讨论文章及私密段评：文章禁读404，私密正文对无权者为空，管理员也不例外；匿名author.id对本人同样为空。Cookie/旧apiKey不能替代Bearer，显式无效Bearer401、缺scope403；检查HTTP/HTML没有IP/UA/凭据或历史快照。
4. 当前长篇包含公开和受限站内裸链接，比较不同读者的v3锚点应一致，公开标题允许展开，受限标题不得出现在摘要/HTML/新快照；标题查库异常必须失败，不能回退读出受限标题。含$与反斜杠标题展示正常。单独对照旧页面既有锚点、历史段评计数和编辑迁移；受限链接差异为明确待办，不算通过。
5. 三账号PC/mobile同时在线发表匿名私密评论：所有接收者的公开commentAuthorId为空，楼主/评论者正文正常，其他读者正文为空；本人编辑/删除操作、模板标记和普通公开作者ID仍正常。重放幂等POST不重复事件；修改讨论邀请/文章权限后旧连接不再接收禁读评论。回归黑名单/test/validateCaptcha、匿名首访/计数和开发风控开关。

本轮文档已同步，网站API文章仍需人工同步。全量目标仍进行中，除既有剩余模块与运行验收外，新增历史受限链接锚点兼容/迁移待办不能遗漏。

### 2026-10-17：历史锚点兼容映射与事务迁移

上一轮已交付段落/段评/评论详情和匿名ID脱敏，是实际进展。本轮处理旧展开标题哈希与安全视图哈希不同导致的漏计/定位问题。LongArticleParagraphService.compatibleMarkdown 捕获每个链接目标一次 TitleSnapshot，分别构建旧定位视图和公开安全视图；只用旧视图的哈希，输出数据全部来自安全视图。配对检查段落数量、DOM属性、链接目的地与普通正文，允许链接标签展示不同；不会按相近索引猜测归属。多行span注释前后保持，输出HTML带旧定位ID、安全摘要不含受限标题。CompatibleContent 为ID与反向别名建索引，避免每条记录反复扫描整篇段落。

无歧义时同时接受旧ID和此前短暂使用的公开视图ID，目录计数一次查询最多50个逻辑锚点的100个物理ID，段评count/rows使用同一IN绑定条件。有效评论详情规范到当前旧页面可定位的ID，失效段评保持历史关联且不强制重建兼容视图。段评投稿在锁内规范根锚点或确认父锚点同一归属；不修改幂等原始提交哈希。父段评提供相同旧ID或省略ID时继续继承，包括失效线程，不额外拒绝。CommentMgmtService 新增服务端 suppressReplySnapshot 重载，v3传true防止把历史受限标题快照再复制到新回复及旧广播，旧两参数调用保持原继承行为；该开关不是客户端字段。

LongArticleParagraphService 迁移明确要求调用者事务，兼容映射能识别的早期v3根/回复在正文编辑原事务中规范到旧定位体系，仅改段落定位列，不改正文、快照或回复目标。ArticleMgmtService 新增内部 strict 参数，ApiV3ArticleMgmtService 始终传true：有段评时先检查新旧视图可唯一对应，冲突抛409使正文/章节/定位/收费随外层幂等事务回滚。原旧入口/管理员仍走默认false及原迁移规则，尚未启用旧入口的冲突拦截；已向用户解释少数原可提交编辑会被拒绝的兼容影响并请求确认，答案待到达。

明确剩余边界：历史与临时锚点没有版本字段，重复裸链接与代码中相同链接等内容可能让两个ID集合重叠，或复杂标题导致渲染结构不一致。此时v3返回paragraph_mapping_conflict(409)，不返回猜测映射、不发表/扣款或提交编辑。不能把这个保护称为全面历史兼容完成；之后仍需明确的版本/来源迁移策略解决歧义数据，旧入口同等拦截也待用户决定。旧页面默认标题展开方式未改，不能声称旧页面受限标题问题已全部修复。

实际文件：
- src/main/java/org/b3log/symphony/service/LongArticleParagraphService.java、ApiV3ContentQueryService.java、ApiV3ParagraphQueryService.java、ApiV3CommentMgmtService.java、CommentMgmtService.java、ArticleMgmtService.java、ApiV3ArticleMgmtService.java。
- src/main/java/org/b3log/symphony/util/ApiV3ParagraphQueries.java。
- src/test/java/org/b3log/symphony/service/LongArticleParagraphAnnotationTest.java、LongArticleParagraphMigrationTest.java、util/ApiV3ParagraphReadTest.java；tools/api-v3-paragraph-test.mjs。
- AGENTS.md、API.md、UPDATE_LOG.md、本计划。

实际验证：评论/读取41组通过（上轮40+别名SQL1）；段落解析/迁移12组通过（上轮5+配对3+真实迁移事务4）。配对测试验证旧哈希/临时别名、安全HTML/摘要、多行位置、回调memo、目的地/结构/普通文本不符和ID重叠409。SQLite执行实际服务迁移，覆盖原始/临时根及回复、事务守卫、调用者回滚、第二笔更新失败整组回滚、成功提交、列白名单和真正删除段落转失效；strict结构冲突发生在定位更新前。这里Repository为JDBC适配器，去掉FOR UPDATE，不能证明真实MySQL锁或ArticleMgmtService完整集成；Markdown仅有测试链接适配，clean/表情/媒体是shim，不能据此声称真实Flexmark及XSS清洗通过。针对变更服务的javac仍缺依赖（315错误，变更类无直接错误诊断）；Java语法与脚本/空白检查通过，不是完整应用编译。没有Maven、HTTP或浏览器操作。无新增路由，仍658条；本轮无前端源码/产物、FTL或新表变化。

源码安全/反漏扫：原Bearerscope与匿名中间件注册不变，当前文章仍由显式读者校验；没有Sessions/apiKey身份回退。GET无数据库写入，迁移只加入现有事务、参数与排序保持绑定/白名单，快照从列表SQL也移除。旧视图全文只在内部计算ID，JSON/HTML/异常不输出受限标题或历史快照；响应仍白名单且匿名作者ID为空。无外部抓取/SSRF、上传下载路径、CDN/version/sourceMappingURL、目录枚举、新Repository变化。黑名单/test、验证码解封、首访/计数、anonymous.viewSkips及开发风控开关均需真实环境回归；新增双视图解析的CPU成本需真实批量请求验证，不能用分页上限代替限流证据。未鉴权、越权、CSRF、XSS、SQL注入、批量滥用与敏感信息泄露的运行验收均未执行。

人工全链路（先IDEA重新编译或重启并由用户确认）：
1. localhost:8080准备含公开/受限站内链接的长篇与旧段评根/回复，GET paragraphs?includeContent=true，旧段评ID、计数和HTML定位应一致；受限标题不得出现在正文HTML、摘要、错误或新快照。PC/mobile从permalink定位，再用旧ID/v3当前ID筛选，应返回同组评论，翻页/排序/total一致且不重复。
2. 用新GET锚点POST根段评，再省略或提供同一锚点回复，GET列表/详情核对归属。旧失效段评仍能回复但新回复快照为空，不通过实时卡片重新泄露旧快照；普通评论及旧页面入口的原行为另回归。
3. 准备早期临时ID记录，v3编辑长篇正文后根/回复在同一事务中迁移，正文、快照和回复关系不变；真正删除段落后记录保留为orphaned。断点模拟第二次定位写入失败，正文/章节/计数/版本/收费/幂等结果全部不提交，无缓存/实时提前发布；MySQL并发与跨实例另测。
4. 构造同一受限URL的裸链接段落与内联代码链接段落，或会改变结构的复杂标题：v3目录/有效段评/投稿/正文编辑遇到歧义应409且无猜测归属或持久写入；失效列表仍可读。旧编辑保持原方式直至用户确认是否同步拦截，不能默认测为已保护。
5. 匿名、未邀请者、合法Bearer、缺scope、仅Cookie/apiKey分别回归公开/讨论/禁止匿名文章和私密段评；检查404/401/403及匿名/私密脱敏。回归黑名单/test/validateCaptcha、匿名首访/计数、开发风控开关及PC/mobile WS，数据库/HTML/响应无IP/UA/凭据泄露。

API/UPDATE_LOG已同步，需要同步网站API文章。全量目标继续：除历史歧义最终迁移与运行验收外，评论删除/采纳/感谢、认证补充、其他设置/背包、上传/Socket/支付仍未完成；鱼游投稿仍待OAuth规则/入口。

### 2026-10-17：评论删除事务核心与旧入口原子扣费

用户明确同意旧入口同步原子删除：成功费用和字段保持，失败不扣款。`CommentProcessor#removeComment` 改从loginCheck的User.USER取当前身份，兼容query/body apiKey；本人检查保留，锁内再次核对owner，审计保存锁定评论的快照且仅提交后记录。管理员强制删除入口独立保留。

新增 `CommentRemovalMgmtService`、`CommentRemoval` 和 `CommentRemovalQueries`：外层先取文章发布锁与actor/sys账户对，再开启事务；全局统计、作者、文章、评论依序当前读。回复/赞踩/感谢同时检查存储计数与实际关联记录，采纳及余额条件先于扣款。删除、费用、作者/文章/父评论/标签计数、版本、reaction、限定类型通知和职业来源同事务；字段白名单避免旧快照覆盖余额或互动。最新匿名评论用户名保持匿名；索引、通知刷新和审计放在提交后。`ApiV3CommentMgmtService#remove` 已准备幂等回调，但没有注册HTTP DELETE，也没有在公开文档宣告可调用；须先补齐旧感谢/采纳/投票的目标锁与写入事务，以及延迟通知与删除的竞争。

实际文件：上述三个核心文件、`processor/CommentProcessor.java`、`service/ApiV3CommentMgmtService.java`、`src/test/java/org/b3log/symphony/service/CommentRemovalServiceTest.java`、`tools/api-v3-comment-removal-test.mjs`、`AGENTS.md`、`API.md`、`UPDATE_LOG.md`、本计划。API文档仅更新已接入的旧删除契约。

验证：`node tools/api-v3-comment-removal-test.mjs` 11组通过，实际执行删除核心/服务/SQL和幂等协调器，用SQLite仓储、积分、职业、通知及发布锁适配器覆盖成功、条件拒绝、越权、12处中途故障、扣费异常、事务回滚、重放及幂等结果写入失败；新增旧外层事务包装的提交/回滚/提交后效果测试。该适配器不验证实际ArticleMgmtService锁、真实积分/缓存/IoC或MySQL并发。Java25六文件语法通过；定向javac解析真实项目依赖仍有343项依赖/关联错误，变更核心及控制器未出现直接错误诊断，不能视为完整编译通过。未运行Maven、HTTP或浏览器，Java生效仍待用户确认IDEA重新编译/重启。

安全与反漏扫：没有新增路由、匿名白名单、静态资源、CDN、source map、目录、上传下载路径或外部抓取；旧删除仍经loginCheck和PermissionMidware，owner在事务内验证，预备v3仅接受显式Bearer用户。SQL值参数绑定，内部表名白名单，返回保留code/commentId，异常不返回私有对象。删除不渲染用户HTML，无新增XSS/SSRF路径；批量重复删除不会重复扣费，但旧链路并发滥用未实际验证。旧Cookie/apiKey双鉴权写入口的CSRF策略保持现状，未宣称已消除CSRF。未鉴权/越权、注入、敏感字段的运行回归，以及BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware首访/每5次、validateCaptcha解封、anonymous.viewSkips与开发风控开关均未做真实环境验收。

人工全链路（用户先IDEA重编译/重启并确认）：
1. PC和移动端文章评论区，发布无回复/赞踩/感谢/采纳的本人评论，记录积分后删除：评论消失、仅扣原费用，文章/父回复/标签计数和最新评论显示同步，成功响应仍code=0与commentId，后台仅有成功审计。
2. 分别准备有回复、赞踩、感谢、已采纳及积分不足的评论，尝试删除：显示原业务错误、评论保留、积分与计数均不变。匿名和私密的可删除评论仍按原费用扣款；删除最后一条评论后最新回复信息正确回退。
3. 分别以Cookie、仅query apiKey、仅JSON apiKey调用旧入口；合法本人成功，未登录/无效凭据/他人评论拒绝，缺失ID404，不得返回正文、IP/UA或凭据。断点制造扣款后数据库写入失败，整笔删除回滚且无成功审计。
4. 并发感谢/采纳/投票/回复/编辑与删除，核对无悬空记录、遗漏扣费和旧快照覆盖；这是尚待补齐的风险验证，不能因核心11组通过就判定通过。v3 DELETE当前应不存在，不能提前用作验收入口。
5. 回归新IP、黑名单/test、验证码解封和开发模式开关；PC/mobile刷新与通知未读数、reaction及匿名名称同步核对。此轮无前端改动，不需yarn。

继续项：先完成旧互动并发核心，再注册v3 DELETE及其scope/写频控/幂等路由并补公开文档。全量v3目标仍未完成；鱼游OAuth入口与旧编辑歧义兼容确认仍待用户补充。

### 2026-10-18：评论感谢事务核心与 v3 接口

已注册 `POST /api/v3/comments/{commentId}/thanks`，需要comments.write、commonThankComment及Idempotency-Key；请求仅空正文或空对象，响应白名单为id/thanked。显式Bearer读者重验当前文章和私密正文可读性，目标失效/不可读404，自己感谢或转账失败422，归属变化409。幂等回放不再扣款/发通知，使用新键重复感谢也不重复付款。不存在目标使用新键不保存成功记录，历史同键回放仍返回原结果。

旧 `CommentMgmtService#thankComment` 与v3共用新增 `CommentThankMgmtService`：先投影评论作者和文章ID以预锁actor/sys及actor/接收者全部账户对，事务内依序锁全局选项、排序用户、文章与评论，并重新绑定目标。感谢查重必须当前读且查询失败不能放行；转账/计数/reward/通知写入同事务。旧入口保留缺失/失效目标、重复感谢的无操作成功行为，自谢提示与费用不变；匿名评论仍不转账且无新增余额门槛。旧控制器改从loginCheck的User.USER取身份，query/body apiKey均可用。索引、通知刷新与活跃度只在首次成功提交后执行，单个提交后动作失败不回滚已完成业务。旧采纳和三处投票更新仅写各自计数字段，避免旧评论整行快照覆盖感谢计数；它们的完整锁与事务还待后续补齐。

实际文件：新增 `service/CommentThankMgmtService.java`、`src/test/java/org/b3log/symphony/service/CommentThankServiceTest.java`、`tools/api-v3-comment-thank-smoke.mjs`；修改 `processor/ApiV3CommentProcessor.java`、`processor/CommentProcessor.java`、`service/ApiV3CommentMgmtService.java`、`service/CommentMgmtService.java`、`service/NotificationMgmtService.java`、`service/VoteMgmtService.java`、共用 `CommentRemovalServiceTest.java` / `api-v3-comment-removal-test.mjs`、AGENTS/API/UPDATE_LOG与两份计划/清单文档。`NotificationMgmtService#addCommentRewardNotificationInCurrentTransaction` 仅允许感谢/采纳两种类型且不在事务内推送。

验证：共用隔离脚本19组通过（删除11+感谢8）。实际感谢服务、SQL与幂等协调器通过SQLite仓储/积分/通知/职业/活跃度/发布锁适配器，覆盖事务和完整账户对守卫、正常转账、匿名免转账、查重、7类中途失败、锁内授权拒绝、目标归属变化、余额不足、双方状态无效、两种删除/感谢串行顺序及过期投影、同键回放、新键重复、目标删除后回放、结果持久化失败整笔回滚和提交后索引故障。这里没有运行真实API聚合服务/IoC、实际积分/通知实现、缓存或MySQL并发，不能将串行测试称为跨实例锁验证。8个Java文件语法通过，两个脚本语法与diff空白检查通过；定向真实依赖javac仍342项关联/缺失依赖错误，所改服务/控制器没有直接错误诊断，不能称完整编译通过。路由清单659条、浏览器引用398条。未运行Maven、HTTP、浏览器或前端构建。

安全与反漏扫源码结论：新入口只认Bearer及comments.write，目标可见性/私密权限在锁内按当前用户再查，Cookie/apiKey不能补v3身份；共享写频控在幂等查询前执行。ID格式和请求白名单在入口限制，SQL值均绑定，JSON只返回明确字段，新增接口不渲染HTML、不接受URL或发外部请求，无新增XSS/SSRF/注入入口。新API不使用Cookie身份，旧Cookie/apiKey CSRF策略保持现状。无新增公开静态资源/CDN版本号/sourceMappingURL/目录/上传下载返回路径；anonymous.viewSkips、全局BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware首访/每5次及CaptchaProcessor解封链路未改。所有鉴权、越权、CSRF、恶意输入、限流、敏感信息和风控运行验证仍待执行；数据库锁/批量负载与旧采纳/投票的竞争亦未完成。

人工全链路（先由用户确认IDEA重编译或重启）：
1. PC/mobile文章评论区感谢他人的普通评论，核对付款人减15、接收者加15、感谢计数+1、一条感谢记录和通知；刷新两端后保持一致。再次点击或分别以query/body apiKey调用 `/comment/thank`，不重复扣款或计数。匿名评论感谢不转账，仍保存一次感谢和通知；自谢和余额不足拒绝且所有数据不变。
2. 使用具备comments.write及角色权限的Bearer调用新接口，第一次返回id/thanked=true，同键重试及新键重复不再付款、发通知或增加活跃度；数据库断点令通知或幂等结果写入失败，余额/计数/reward/通知/结果整体回滚，再使用原键应仅成功一次。
3. 未登录、无效/过期/撤销Bearer、仅Cookie、仅旧apiKey、缺scope或角色分别验证401/403；自己评论422、缺失/封禁作者/失效评论404，非法ID/多余字段/缺幂等键拒绝，余额不足422。用私密评论作者、文章作者、第三人和讨论帖受邀/未受邀者验证可读范围；拒绝响应不得含正文、真实匿名身份、IP/UA或凭据。
4. 分别先完成旧删除再感谢、先感谢再旧删除：前者不得产生付款或悬空记录，后者拒绝删除且不扣删除费。并发交错需真实MySQL验证，尤其旧采纳/投票/后台强删还未全部共享锁；不能凭当前19组测试判定这些场景已通过。v3 DELETE仍未注册。
5. 在风控开启的开发环境验证新IP、黑名单/test、验证码解封、首访/计数和开发关闭盾分支；PC/mobile通知数与感谢计数、匿名名称和私密正文均需核对。重启确认后可运行新smoke脚本，默认只测拒绝；明确设置API_V3_COMMENT_THANK=1和一次性目标才会真实扣款。

API.md与UPDATE_LOG.md已同步，仍需同步网站API文章。下一步为采纳原子事务、投票锁与延迟通知，再接v3评论DELETE；全量认证/其他设置/背包/上传/Socket/支付等仍未完成，鱼游OAuth规则入口待用户提供。

### 2026-10-18：评论采纳原子事务与 v3 接口

已注册 `POST /api/v3/comments/{commentId}/accept`，comments.write + Idempotency-Key，空正文/空JSON对象，响应仅id/accepted。当前Bearer读者先校验文章/私密正文、评论/作者有效性，再核对文章所有权；不可读目标404，非作者403，自我采纳/余额条件422，其他评论已被采纳或归属变化409。同键回放与换键重复采纳同一目标不重复支付、通知或增加活跃度。对不同候选者的冲突不保存成功幂等结果。

新增 `CommentAcceptMgmtService`，旧服务改为显式接收当前操作者，控制器从loginCheck的User.USER取身份，旧CSRF注册保留。事务前预锁actor/sys与actor/评论作者；事务内锁全局选项、排序账户、文章、目标评论，文章锁串行不同候选者，采纳查询走当前锁定读。转账、采纳标记、奖励及通知同事务，只更新采纳字段；索引/通知刷新/活跃度在提交后。沿用原当前悬赏额度、匿名免转账和零悬赏行为，未新增文章类型门槛。旧入口在已有其他采纳时仍无操作成功，v3明确冲突。管理员强制删除及旧投票仍未全部纳入该边界。

本轮文件：新增service/CommentAcceptMgmtService.java、src/test/java/org/b3log/symphony/service/CommentAcceptServiceTest.java、tools/api-v3-comment-accept-smoke.mjs；修改processor/ApiV3CommentProcessor.java、processor/CommentProcessor.java、service/ApiV3CommentMgmtService.java、service/CommentMgmtService.java、共享CommentRemovalServiceTest.java及api-v3-comment-removal-test.mjs，并更新AGENTS.md、API.md、UPDATE_LOG.md、本计划和api-route-inventory.md。没有新增表/Repository、SQL、JS/SCSS/FTL或编译产物，无需yarn。

验证：共用脚本25组通过（删除11、感谢8、采纳6）。实际采纳服务、SQL及幂等协调器使用SQLite仓储/积分/通知/活跃度/发布锁适配器，覆盖完整事务及账户对守卫、正常转账及奖励/通知、同一/不同候选者顺序重试、匿名/零悬赏、查询/计数/reward/通知/转账故障整笔回滚、自谢/非文章作者/余额/负额度/显式读拒绝/目标归属变化、两种删除与采纳顺序、幂等结果失败回滚及目标删除后回放。串行适配器测试不证明真实MySQL并发锁、IoC、实际积分/通知/缓存实现或ApiV3CommentMgmtService整合。6个Java文件语法与两脚本语法、diff空白检查通过；真实依赖定向javac仍342项依赖/关联错误，所改服务/控制器无直接错误诊断，完整应用编译未通过。路由660、浏览器引用398。没有运行Maven、HTTP或浏览器，等待用户确认IDEA重新编译/重启。

安全/反漏扫源码复核：新路由仅Bearer+scope并经过共享写频控；所有权、有效性与私密/讨论可读性在锁内验证，不回退Cookie/apiKey。参数ID格式和空体白名单，SQL值绑定，服务端确定收费与接收者，响应/错误不带正文或内部对象，无新增HTML渲染、URL抓取、上传下载路径、静态/CDN版本号、sourceMappingURL或可枚举目录。旧Cookie采纳的CSRF保持原中间件；未改anonymous.viewSkips、BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware首访/每5次、CaptchaProcessor解封。未鉴权/越权、CSRF、XSS、注入、SSRF、限流/批量滥用、敏感信息及上述风控仍需运行验证，未将源码结论视为实测通过。

人工完整链路（先由用户IDEA重编译/重启并确认）：
1. PC/mobile以文章作者进入有两条他人回答的问答文章，点击采纳并确认：仅目标显示已采纳，作者减少当前悬赏、回答作者增加相同积分，奖励及通知各一条。刷新两端仍一致；同一回答重复或另一个回答再次操作旧入口，不重复支付，旧响应不变。旧前端可能在无操作成功时临时标记另一条，刷新后以持久结果为准，该既有UI语义未改变。
2. 以comments.write Bearer对目标POST，新接口返回id/accepted=true；同键重试和新键重复同目标不再付款/通知/增加活跃度，对另一候选者409且无成功记录。匿名回答不转账，零悬赏公开回答保持原零费用，非问答类型不新增服务端限制。
3. 验证非文章作者403、文章作者自己的回答422、不可读讨论/私密或无效作者/评论404、未登录/过期/撤销Bearer401、缺scope403、Cookie/apiKey不能代替Bearer、非法ID/额外收费或作者字段/缺键拒绝。余额不足422，付款后通知或幂等结果写入失败时所有标记、积分、奖励及通知回滚。
4. 先删除目标再采纳，应拒绝且无付款或悬空记录；先采纳再尝试本人删除，应拒绝且不扣删除费。真实并发两候选采纳以及采纳/感谢/删除交错，需MySQL多连接验证，当前测试只覆盖串行两种结果。旧投票和延迟通知竞争仍待补齐，v3 DELETE尚未开放。
5. 回归旧页面CSRF缺失/错误拒绝、合法提交成功；风控开启时新IP、黑名单/test、验证码解封及开发关闭盾分支保持。检查两端HTML/JSON/通知不含匿名真实身份、私密正文或IP/UA/凭据。重启确认后可运行新smoke；默认仅拒绝检查，显式API_V3_COMMENT_ACCEPT=1及一次性回答ID才会真实采纳/付款。

API/UPDATE_LOG已同步，仍需同步网站API文章。下一步补齐旧投票和延迟通知，再开放v3评论删除；全量认证、其他设置/背包、上传/Socket/支付及运行验收仍未完成，鱼游OAuth入口和旧编辑歧义处理仍待用户补充。

### 2026-10-18：评论投票事务与删除互斥准备

旧 `/vote/up/comment`、`/vote/down/comment` 已切到新增 `CommentVoteMgmtService`。控制器从loginCheck的User.USER取身份，兼容query/body apiKey；锁内确定toggle结果，成功type仍为操作前状态，管理员自投例外保留。先取发布锁及actor/sys账户对，再事务内按全局选项→操作者→文章→评论→vote行锁定，投票增删、赞踩计数/分数和点赞通知一并保存，提交后刷新和计活跃度。只更新所属计数，感谢/采纳/正文等保持；重复通知受既有内存约束和数据库查询共同约束。set/clear核心供后续v3调用，不带toggle，但v3投票路由尚未注册。

`VoteMgmtService` 删除旧评论分支及其仓储依赖，公共包装仅接受文章类型，防止后续调用绕过新评论锁；现有文章入口仍用原实现。旧评论点赞不再在控制器中延迟插入通知。`NotificationMgmtService#addCommentVoteNotificationInCurrentTransaction` 在同一事务限定类型、查重并保存，不在事务中推送。遇到重复存量vote行、非法投票类型、负计数或计数溢出时拒绝而不猜测修复。

本轮文件：新增service/CommentVoteMgmtService.java、src/test/java/org/b3log/symphony/service/CommentVoteServiceTest.java；修改processor/VoteProcessor.java、service/VoteMgmtService.java、service/NotificationMgmtService.java、共享CommentRemovalServiceTest.java及api-v3-comment-removal-test.mjs，更新AGENTS.md、API.md、UPDATE_LOG.md、本计划及api-route-inventory.md。无新增表/Repository、SQL、FTL、JS/SCSS或生成资源。本轮没有新增v3路由，仍660条、浏览器引用398条。

验证：共享脚本31组通过（删除11/感谢8/采纳6/投票6）。实际投票服务及SQL通过SQLite仓储/通知/活跃度/发布锁适配器覆盖旧toggle、切换、取消及分数、当前set/clear幂等、事务/显式授权守卫、自投与管理员例外、5处故障整笔回滚、保留感谢/正文、重复通知、两种删除/投票顺序和取消后可删除、存量异常与int边界。不存在真实并发线程/MySQL锁证明，不包含旧文章投票或实际IoC、缓存、通知实现与HTTP聚合测试。6个Java文件语法与脚本/diff检查通过，真实依赖定向javac仍342项缺失依赖/关联错误，所改服务/控制器无直接错误诊断，完整编译未通过。未运行Maven、HTTP、浏览器；Java生效仍待用户IDEA重编译/重启确认。

源码安全/反漏扫：旧路由仍挂loginCheck和PermissionMidware，身份来自中间件，事务内复核操作者状态及自投规则；响应仍code/type，不输出内部用户或评论对象。SQL值绑定、状态枚举白名单、列白名单、异常不返回正文。没有新增HTML/URL渲染或抓取、静态/CDN版本号/sourceMappingURL、目录或上传下载路径。没有增加旧Cookie/apiKey CSRF限制，也没有悄悄改变旧文章/私密内容可读性投票策略；其现状不应被理解为全部越权/CSRF风险已消除。全局黑名单/test、验证码解封、匿名首访/每5次、anonymous.viewSkips和开发风控开关不变；鉴权、越权、CSRF、XSS、注入、SSRF、批量滥用/敏感信息及风控运行检查均待实测。

人工全链路（先由用户IDEA重新编译/重启并确认）：
1. PC/mobile以普通用户对他人评论赞→再赞取消→踩→改赞，核对每次旧响应type等于操作前状态、计数和分数持久一致，感谢/采纳标记/正文不被覆盖；点赞通知最多一次，取消不新增活跃度，点踩保持原无点踩通知行为。刷新两端再核对。
2. 分别用Cookie、query apiKey、body apiKey走两条评论路由；本人评论拒绝，管理员仍可自投，未鉴权拒绝。不存在/已删除目标不生成投票或成功通知，缺失/无效操作者不得写入。原文章赞/踩/取消另行回归，确保仍走原路径。
3. 先投票再尝试本人删除：拒绝删除且不扣费；取消投票后删除成功，投票关联通知清理。先完成删除，再让已投影目标的请求继续：拒绝且不补悬空vote或通知；实际并发多用户赞踩、切换、删除、感谢/采纳交错需MySQL验证，不能用串行适配器测试代替。
4. 断点制造vote插入/删除、计数更新或通知保存失败，检查vote/计数/通知全部回滚，没有成功活跃度或刷新；重复/非法存量行、负计数及溢出保持原数据并返回失败，不自动修复。检查失败JSON/日志不泄露正文、IP/UA或凭据。
5. 风控开启时回归新IP、黑名单/test、验证码解封、匿名首访/计数与开发关闭盾分支；旧CSRF现状未改，跨站风险另测。当前v3评论vote与DELETE仍未注册，不能提前用作运行验收入口。

继续处理CommentNotifier及CommentUpdateNotifier的延迟提醒：可读性检查和插入之间仍可能发生删除，须锁定当前目标并在提交后刷新，避免悬空通知，然后注册v3评论删除/投票。全量目标仍进行中，其他认证/设置/背包/上传/Socket/支付及鱼游OAuth入口、运行验收仍未完成。API与更新日志已同步，需同步网站API文章。

### 2026-10-18：延迟评论通知与 v3 评论删除接线

新增 `CommentNotificationMgmtService`，发布及编辑事件的评论提醒已统一接入。先投影评论作者/文章作者，取得发布锁及commenter/sys账户对；事务内锁全局选项、按ID排序的相关用户、文章及当前评论，复核归属、作者/接收者状态、当前文章和私密正文可读性，再限定类型查重和插入通知，提交后刷新。删除先完成时跳过，通知先提交时可被随后删除清理。CommentUpdateNotifier移除事务外查重，文章事件仍保留自己的旧At路径。`@participants`仅计入本次真正新建的提醒，收费仍是原事件中的独立转账；没有把整个异步事件改成单事务。

已注册 `DELETE /api/v3/comments/{commentId}`，comments.write + commonRemoveComment + Idempotency-Key，空正文或空JSON对象。复用已准备的删除核心和幂等事务，返回id/removed；仅本人、目标文章可读，回复/赞踩/感谢/采纳/余额条件不满足422，他人/不存在/不可读404，权限不足403。同键重试保留原结果且不再扣费，删除后新键404。删除核心调整为先投影文章作者、排序锁定相关用户，再锁文章/评论，避免在锁住文章后才临时锁另一位作者。原费用、匿名/私密收费和旧入口响应不变。

实际文件：新增service/CommentNotificationMgmtService.java、src/test/java/org/b3log/symphony/service/CommentNotificationServiceTest.java、tools/api-v3-comment-delete-smoke.mjs；修改event/CommentNotifier.java、event/CommentUpdateNotifier.java、service/NotificationMgmtService.java、service/CommentRemovalMgmtService.java、service/ApiV3CommentMgmtService.java、processor/ApiV3CommentProcessor.java、共享CommentRemovalServiceTest.java及api-v3-comment-removal-test.mjs；同步AGENTS.md、API.md、UPDATE_LOG.md、本计划与api-route-inventory.md。没有新表/Repository、SQL、FTL、前端源码/产物或CDN变化。

验证：共用脚本36组通过（删除11/感谢8/采纳6/投票6/延迟通知5），实际新通知服务及现有删除核心使用SQLite仓储/通知/积分/活跃度/发布锁与可读性适配器，覆盖五种提醒、查重、提交后刷新、通知后删除及删除后跳过、私密正文与讨论可见性变化、失效/移动目标及失效作者/接收者、查询/写入/写入后失败回滚、刷新故障不撤销提交、类型与事务守卫。可读性适配器使用实际ApiV3ContentUtil，提及解析及ApiV3ContentQueryService为替代实现；真实通知实现、事件调度、完整聚合服务、MySQL并发和IoC/缓存未运行。删除幂等及扣费仍由前11组覆盖，不能声称已做HTTP验收。8文件Java25语法、两脚本语法及diff检查通过；定向真实依赖javac仍342项缺失/关联错误，新改服务/事件/控制器无直接错误诊断，完整应用编译未通过。路由661、浏览器引用398。没有运行Maven、HTTP、浏览器或yarn，等待用户确认IDEA重编译/重启。

安全与反漏扫源码结论：DELETE只接受Bearer，scope和角色权限均检查，所有权联合查询、文章可读性和互动条件在业务事务内复核；不从Sessions/apiKey补身份。JSON空体白名单、数字ID与请求键校验，SQL绑定及类型/列白名单，成功与失败不带内部评论/用户或私密字段；无HTML渲染/外部URL请求，无新增XSS/SSRF输入。写频控在幂等查询前，失败不保存成功结果。新API不以Cookie鉴权，旧Cookie/apiKey CSRF策略未改。全局黑名单/test、验证码解封、匿名首访/计数、anonymous.viewSkips和开发风控开关不变；无静态/CDN版本号/sourceMappingURL、目录或上传下载返回路径变化。未鉴权、越权、CSRF、XSS、注入、SSRF、批量滥用/敏感信息及防扫描均待真实环境回归；后台强删与旧管理编辑的并发仍未纳入完整验证，不能宣称所有旧写入都已统一。

人工完整链路（先由用户通过IDEA重编译/重启并确认）：
1. PC/mobile文章发布本人无回复/赞踩/感谢/采纳的评论或段评，记录余额，使用合法Bearer DELETE：id/removed=true，原删除费用仅扣一次；同键重试返回相同结果，新键404；刷新两端，文章/作者/父回复/标签计数、最新评论匿名名称、版本、reaction及通知清理正确。本人匿名/私密评论仍按原删除费用。
2. 分别准备有回复、赞踩、感谢、采纳或余额不足的本人评论，DELETE应422且余额与数据不变；他人评论或当前不可读讨论文章404；缺Bearer、无效/过期/撤销Bearer、仅Cookie/apiKey拒绝，缺scope或角色403；非法ID、额外force/fee字段及缺幂等键拒绝。断点制造删除或结果保存失败，评论/扣款/所有关联写入均回滚。
3. 在CommentNotifier调用新send之前暂停事件，先完成DELETE，再恢复事件：不能产生悬空提醒或新通知刷新；反过来先保存提醒再DELETE，旧通知被清理。以编辑后旧事件测试私密范围收紧、讨论邀请变化、封禁作者/接收者或目标失效，只有当前仍可读者可收到提醒；重复事件不重复插入。@participants按实际新提醒计数，费用仍由原独立转账完成，另查余额不要将它误认成删除费。
4. 在提醒SQL写入后故障，通知回滚且不刷新；提交后刷新故障，已提交通知保留。并发发布/编辑/回复/赞踩/感谢/采纳/反应与删除，用真实MySQL检查无悬空数据、重复扣费或旧快照覆盖；后台强删/管理编辑另行回归，当前未证明其并发安全。
5. 检查PC/mobile HTML、WebSocket与API都不出现私密正文、匿名真实作者、IP/UA或凭据；风控开启时验证新IP、黑名单/test、验证码解封、匿名首访/每5次及开发关闭盾分支。确认重启后可执行新delete smoke：默认仅拒绝测试，显式API_V3_COMMENT_DELETE=1及一次性本人目标才会真实删除/扣款。

API/UPDATE_LOG已同步，需同步网站API文章。下一步为v3评论/文章投票及剩余认证/设置/背包/上传/Socket/支付等；鱼游OAuth规则入口、旧编辑歧义兼容确认和完整运行验收仍待用户补充。全量目标保持进行中。

### 2026-10-18：v3 评论投票读取、设置与清除

新增 `GET/PUT/DELETE /api/v3/comments/{commentId}/vote`。GET需要content.read，PUT/DELETE需要comments.write及Idempotency-Key；PUT只接受vote=up/down，DELETE空体/空对象。返回白名单id/vote/upCount/downCount，未投票为JSON null。新 `ApiV3CommentVoteService` 显式接收Bearer用户，读取用投影SQL一次关联本人、comment类型的vote与评论计数，不输出正文、作者、IP/UA或vote原记录；写入用已共用的set核心，不调用旧toggle。

设置及清除统一operation幂等事务，规范化请求只含期望vote（清除为null），回放保留原结果而不覆盖后续投票。新键重复设置/清除也不重复改计数或增加活跃度；相同键改变方向或改为清除409。失败不保留成功记录，目标删除后同键仍可回放，新键404。每次实际写入在锁内按当前用户重新校验文章/讨论/私密可读性、评论/作者有效性；普通用户自投422，管理员例外保留。核心先投影当前文章作者、按ID排序锁定操作者/评论作者/文章作者，再锁文章/评论，避免可读性校验临时扩展用户锁；角色/范围限制由入口中间件和当前用户规则共同承担。

文件：新增service/ApiV3CommentVoteService.java、util/ApiV3CommentVotes.java、src/test/java/org/b3log/symphony/service/ApiV3CommentVoteServiceTest.java、tools/api-v3-comment-vote-smoke.mjs；修改processor/ApiV3CommentProcessor.java、service/CommentVoteMgmtService.java、共用CommentNotificationServiceTest.java可读性适配器及api-v3-comment-removal-test.mjs，并更新AGENTS/API/UPDATE_LOG、本计划与路由清单。无新表/Repository、SQL、FTL、前端源码/产物或CDN变化。

验证：共用隔离脚本42组通过（原36+本批6）。本批编译并运行实际v3聚合服务、投票核心、幂等服务/请求仓储及投影SQL，使用SQLite仓储/通知/活跃度/发布锁/可读性适配器，覆盖严格输入、SQL表名/值绑定、不同读者、同ID不同资源类型、投票与清除、同/新键重复、PUT改值及PUT→DELETE同键409、旧结果不覆盖后续状态、目标删除后回放、新键缺失、4类仓储中途失败及幂等结果保存失败整笔回滚、当前私密/讨论可读性、自投/管理员例外、失效/缺失目标、重复存量投票与DTO白名单。与早期准备测试不同，本批实际执行了新聚合服务方法，但仍不是HTTP/真实IoC、缓存、实际通知实现或MySQL并发验收。可读性适配器现兼容普通读取，依旧替代真实ApiV3ContentQueryService/提及解析。5个Java文件语法、两脚本语法与diff检查通过；定向真实依赖javac仍342项依赖/关联错误，变更服务/控制器/工具无直接错误诊断，完整应用编译未通过。路由664、浏览器引用398。没有Maven、HTTP、浏览器或yarn操作。

安全/反漏扫源码结论：三条入口均要求Bearer及对应scope，无Cookie/apiKey身份回退；状态读取及写入都检查当前目标可读性，匿名作者身份不进入响应。ID格式、JSON字段/枚举、幂等键与请求体上限沿用v3统一入口，SQL参数化/表名白名单、响应白名单、写入列白名单；写入经过共享限流，无新增HTML/URL处理或外部请求，CSRF不能以页面Cookie单独触发新写入。旧Cookie/apiKey入口策略未改；无静态/CDN版本号、sourceMappingURL、目录、上传下载返回路径变化。BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware首访/每5次、CaptchaProcessor解封、anonymous.viewSkips和开发风控开关不变。未鉴权/越权、CSRF、XSS、注入、SSRF、批量滥用/敏感信息及风控运行验证仍未执行；GET无新增独立写频控，负载/并发与跨实例锁需要真实环境验证。

人工全链路（用户先通过IDEA重编译/重启并确认）：
1. 登录v3获得content.read+comments.write令牌，文章评论列表选取他人普通评论或段评。GET得到本人vote与赞踩计数；PUT up，同键及新键重复仍为up、计数仅加一次；PUT down切换，旧up请求重放返回原结果，但GET仍为最新down；同键换方向或改DELETE应409。DELETE后vote=null，再次同/新键清除计数不变。核对通知及活跃度仅实际变化时增加，响应只有四个明确字段。
2. PC/mobile通过旧入口点击赞/踩，仍可重复点击取消，type仍是操作前状态；与v3交替操作，GET应反映共享数据，感谢/采纳/正文不能被覆盖。用第二个读者GET同评论，仅看到其本人的vote和公共计数；文章与评论恰好相同ID的vote不能串读/清除。
3. 未登录、过期/撤销Bearer、仅Cookie/apiKey拒绝；content.read/ comments.write分别验证GET和写入scope。普通自投422，私密第三人、未受邀讨论、失效评论/作者404；合法文章作者可操作私密评论。vote null/布尔/数字/错误大小写/空白/额外作者字段及非法ID、缺键均拒绝，清除只接受空体/空对象，失败不改任何计数或记录。
4. 断点制造vote插入/删除、计数/通知/幂等结果保存失败，整个业务与结果回滚，没有成功通知或活跃度；同键再试应只成功一次。取消投票后用本人DELETE删评论，再重放历史PUT应仅返回历史结果，不重建vote或通知，新键及GET404。并发多用户及旧入口交错、后台强删/管理编辑、真实MySQL跨实例另测，当前隔离测试不能证明这些运行结果。
5. 两端刷新核对计数及通知，无匿名真实身份、正文或敏感字段泄露；风控开启验证新IP、黑名单/test、验证码解封、匿名首访/计数与开发关闭盾。确认重启后可运行新smoke，默认只读缺失目标/测拒绝；显式API_V3_COMMENT_VOTE_WRITE=1及一次性他人目标才会改投票，最终清除且可能创建一次通知/活跃度，不恢复原投票。

API/UPDATE_LOG已同步，需同步网站API文章。下一步为文章投票及其余互动、认证、设置/背包、上传/Socket/支付等；鱼游OAuth入口、旧编辑歧义兼容和完整运行验收仍待用户。全量目标保持进行中。

### 2026-10-18：文章投票事务核心与 v3 读写

新增 `GET/PUT/DELETE /api/v3/articles/{articleId}/vote`，读取content.read、写入content.write及Idempotency-Key，返回id/vote/upCount/downCount。PUT设置up/down而非toggle，DELETE清除；同/新键重复设置不重复计数或发提醒，旧请求重放不覆盖后续状态，同键改方向/改清除409。只允许当前可读、有效且作者可用的文章，非受邀讨论404；普通自投422，管理员例外保留。

`VoteMgmtService` 改为共享事务核心：事务前取得发布锁和actor/sys账户对，事务内锁全局选项、排序用户及当前文章，复核作者、操作者和自投条件后再处理typed vote；投票、文章计数/redditScore、所有标签关联分数和点赞通知同事务。关联仅更新redditScore，不覆盖评论计数或其他字段；保留旧排名公式的整数时间计算，非整数时间样本专门回归。刷新及活跃度在提交后。旧控制器切到toggle核心，type仍是操作前状态，query/body apiKey都从loginCheck的User.USER取身份，点赞内存去重与原无点踩通知行为保持；旧Java方法签名保留但不再带事务注解，不能在外层事务中套用。错误向外传播以回滚，不再吞掉后提交部分结果。

输入/DTO工具由ApiV3CommentVotes改名为通用 `ApiV3Votes`，评论读写逻辑仅更换引用。文章与评论SQL都绑定actor和dataType，幂等operation分别articles.vote/comments.vote，完全相同ID和请求键也能隔离。新 `ApiV3VoteProcessor` 已经由ApiV3Processor注册，原文章/评论入口URL和成功字段保持不变。

文件：新增processor/ApiV3VoteProcessor.java、service/ApiV3ArticleVoteService.java、src/test/java/org/b3log/symphony/service/ApiV3ArticleVoteServiceTest.java、tools/api-v3-article-vote-smoke.mjs；修改service/VoteMgmtService.java、processor/VoteProcessor.java、service/NotificationMgmtService.java、processor/ApiV3Processor.java；通用工具改名并更新ApiV3CommentProcessor/ApiV3CommentVoteService/ApiV3CommentVoteServiceTest及共享测试适配器/runner。同步AGENTS/API/UPDATE_LOG、本计划与路由清单。无新表/Repository、SQL、FTL、前端源码/产物或CDN变化。

验证：共享脚本49组通过（原42+文章7）。实际文章聚合服务、核心、投影SQL与幂等服务/请求仓储通过SQLite仓储/通知/活跃度/发布锁/可读性适配器，覆盖读/set/clear、同/新键重复与冲突、后续状态不被旧回放覆盖、删除目标后的回放、新键404、读者隔离、同ID同键文章/评论隔离、原toggle及type、关联分数/非投票字段保留、整数排名样本、7处仓储失败及移除/结果保存失败回滚（含第二条关联更新失败，证明前一条也回滚）、讨论邀请/失效状态/作者、自投/管理员例外、异常存量与int边界、事务/显式授权/作者变化守卫，以及刷新失败保留已提交业务。可读性、实际缓存、事件/通知实现和MySQL锁仍是替代/未执行范围；不是HTTP/应用启动证明。10文件语法、两smoke/runner语法和diff检查通过；真实依赖定向javac仍342项依赖/关联错误，变更服务/控制器/工具无直接错误诊断，完整编译未通过。路由667、浏览器引用398，没有Maven、HTTP、浏览器或yarn操作。

安全与反漏扫源码复核：新路由只接受Bearer及对应scope，不从Cookie/apiKey补身份；读取/实际写入校验当前文章可读性，作者隐私对象不进入DTO，普通自投和当前账户状态锁内复核。路径ID、JSON枚举/字段、请求键及体积受统一入口限制，SQL值绑定/表名白名单，响应及更新字段白名单；写入经过共享频控，未新增HTML/URL处理、外部请求或上传下载路径，Cookie单独不能触发新API写入。旧Cookie/apiKey CSRF和旧入口可读性策略未擅自收紧，不能据此声称所有旧越权/CSRF风险已解决。无静态/CDN版本号/sourceMappingURL或目录新增；BeforeRequestHandler黑名单/test、匿名首访/每5次、验证码解封、anonymous.viewSkips及开发风控开关未改。鉴权、越权、CSRF、XSS、注入、SSRF、批量滥用/敏感信息和风控均需运行复核；GET未增加独立频控，真实负载/跨实例并发及后台强删/管理编辑尚未验证。

人工完整链路（先由用户IDEA重编译/重启并确认）：
1. v3登录取得content.read+content.write令牌，选择他人可读文章。GET当前状态→PUT up→同键及新键重复→PUT down→重放旧up→GET→DELETE→重复清除。重复不翻转或重复计数，重放返回历史结果但GET仍是最新状态，同键改方向/改清除409；通知和活跃度只在实际变化时出现。检查文章及标签/领域列表分数一致，正文、标签、评论数和最新评论不被旧快照覆盖。
2. PC/mobile旧赞/踩按钮仍可再次点击取消，成功type仍为操作前状态；Cookie、query apiKey、body apiKey分别回归，合法调用与v3交替后读取应一致。普通自投拒绝、管理员例外和原不发点踩通知行为保留。
3. 缺/过期/撤销Bearer、仅Cookie/apiKey、缺content.read或content.write分别拒绝；非法ID、vote null/数字/布尔/大小写/空白/额外作者字段、缺键、清除带字段拒绝；私有讨论未受邀、失效文章/作者404，合法受邀者可操作。普通自投422，失败不改记录/计数。匿名文章也不得在投票响应中带出作者身份或私有正文。
4. 断点制造vote增删、文章更新、第一/第二条标签关联更新、通知或幂等结果保存失败，投票、所有计数/分数、通知及结果整体回滚；同键重试只成功一次。文章与评论相同ID及同请求键要隔离；一个读者不得读取另一个读者的vote。清除后删除文章再重放旧请求不得复活记录，新键/GET404。实际文章删除、后台强删/管理编辑、跨用户并发与MySQL锁须单独运行验证，不能用当前串行SQLite测试替代。
5. 核对两端刷新、列表排序、通知未读数以及JSON/HTML无IP/UA、联系方式、token/key、真实匿名作者等泄露。风控开启时回归新IP、黑名单/test、验证码解封、匿名首访/计数及开发关闭盾。重启确认后可执行新smoke，默认只读缺失目标/测拒绝；显式API_V3_ARTICLE_VOTE_WRITE=1及一次性他人文章才会修改，最终清除，不恢复原投票且可能产生通知/活跃度。

API/UPDATE_LOG已同步，需同步网站API文章。下一步为文章感谢/打赏等互动、剩余认证、设置/背包、上传/Socket/支付及完整运行验收；鱼游OAuth规则入口与旧编辑歧义兼容仍待用户。全量目标保持进行中。

### 2026-10-18：文章感谢/打赏核心与 v3 状态及操作

新增 `GET/POST /api/v3/articles/{articleId}/thanks`、`GET/POST /api/v3/articles/{articleId}/reward`。GET需要content.read，POST需要content.write及Idempotency-Key；感谢还复核commonThankArticle。GET返回本人状态及感谢数/费用或文章设定价格；POST仅返回id与thanked/rewarded。打赏正文通过原v3文章详情按当前读者及reward记录读取，不写入幂等响应；读权限撤销后回放旧操作只返回状态，不能带回历史私密正文。感谢和打赏独立operation，同一ID及请求键也不冲突。

新增 `ArticleRewardMgmtService`：actor/sys及actor/作者账户对在事务前完整取得，锁全局选项→排序用户→当前文章，再检查当前归属、状态、查重与费用。普通感谢按原固定费，打赏按当前articleRewardPoint；匿名文章跳过转账且不加余额门槛。感谢计数（仅所属列）、转账、reward和对应通知同事务；索引/刷新/活跃度在提交后，两种文章操作均保留原LIVENESS_REWARD类别。查重查询失败不能当未购买继续扣款。旧ArticleMgmtService.reward/thank签名保留并改用外层事务，原缺失/无效/本人/未启用的无操作语义保留；旧感谢身份改用loginCheck的User.USER，支持query/body apiKey。本人/未启用/不可用/已购买/本次成功的结果被显式区分，供后续正文返回保护使用。

旧 `/article/reward` 源码在购买方法无操作后仍继续组装articleRewardContent；未启用打赏且残留正文、或当前账户被停用但仍有会话等情况可能未经购买就返回内容。已通过异步问题请求用户确认兼容收紧：保持正常HTML与费用，只在合法购买/已有记录/本人且当前可读时返回正文，其他返回旧错误包，凭据采用当前认证读者。**尚未收到答复，不得按超时视为批准。** `ArticleMgmtService#rewardForViewer`、`Result#mayExposeRewardContent`及ArticleQueryService显式读者HTML方法仅准备；旧控制器的打赏正文路径仍未切换。eligibility标记只代表购买/所有权状态，必须同时通过当前文章可读性校验。旧HTML方法委托保留原Sessions分支；v3不调用这个页面渲染器。

一并纠正上轮文章投票的状态判断：模型LOCKED=2仍可阅读，原投票代码只拒绝INVALID，不应按VALID=0独占判断而拒绝锁定文章；v3已允许可读锁定文章，补充正向回归，感谢/打赏同样不增加该限制。

实际文件：新增service/ArticleRewardMgmtService.java、service/ApiV3ArticleRewardService.java、processor/ApiV3ArticleRewardProcessor.java、src/test/java/org/b3log/symphony/service/ApiV3ArticleRewardServiceTest.java、tools/api-v3-article-reward-smoke.mjs；修改ArticleMgmtService、ArticleQueryService、ArticleProcessor（仅感谢身份）、ApiV3Processor、NotificationMgmtService、ApiV3ArticleVoteService及投票测试、共享测试适配器/runner；同步AGENTS/API/UPDATE_LOG、本计划与路由清单。无新表/Repository、SQL、FTL、前端源码/产物或CDN变化。

验证：共享脚本55组通过（原49+本批6）。实际新v3聚合/核心和幂等服务/请求仓储使用SQLite仓储/积分/通知/活跃度/发布锁/角色/可读性适配器，覆盖精确20/文章价格转账与kind/type/dataId关联、感谢计数、两kind同键隔离、同/新键重复、价格变化后不重付、匿名零余额免转账、普通余额不足与同键补款重试、查询/中途写入/半途扣款/幂等结果失败整体回滚、自身/未启用/不可用/已购状态、正文不入响应/结果表、当前讨论权限收紧后旧回放不带正文、锁定可读状态、角色锁内撤销、非法存量/溢出及事务/显式授权守卫。旧ArticleMgmt外层包装、真实积分/通知/缓存/角色查询、页面HTML和文章详情正文渲染、IoC/HTTP及MySQL并发未执行；不能把metadata测试说成付费正文全链路验收。11文件语法、两脚本语法与diff检查通过；真实依赖定向javac仍342项错误，旧ArticleProcessor/ArticleQueryService有既有Jodd/Emoji依赖错误，新增core/v3服务与控制器未见直接错误诊断，完整编译未通过。路由671、浏览器引用398，无Maven、HTTP、浏览器或yarn。

源码安全/反漏扫结论：新入口Bearer与scope明确，不从Cookie/apiKey补身份；当前文章/讨论/作者有效性、感谢角色权限和价格在业务事务中复核，转账及通知接收者只取锁定文章作者。ID、空体白名单、幂等键/体积统一校验，SQL值绑定、字段和类型白名单；无客户端价格/作者字段，响应不返内部用户/文章/转账对象、正文或匿名作者身份，未新增HTML/URL请求或外部抓取，Cookie单独无法触发新写入。写入经过共享频控，GET未新增独立限流。旧Cookie/apiKey CSRF及旧打赏正文风险没有宣称已消除；兼容确认前保留旧返回逻辑。全局黑名单/test、验证码解封、匿名首访/每5次、anonymous.viewSkips和开发风控开关未改；无静态/CDN版本号/sourceMappingURL、目录或上传下载路径新增。未鉴权、越权、CSRF、XSS、注入、SSRF、批量滥用/敏感信息及风控仍待运行复核。

人工完整链路（用户先IDEA重编译/重启并确认）：
1. v3登录取得content.read+content.write及感谢权限，进入他人可读文章。GET thanks查本人状态/费用/总数→POST感谢→同键和新键重复→GET复核：仅一次扣款、感谢数+1、reward及通知各一条，双方余额正确。GET reward查启用价格→POST打赏→GET文章详情，只有作者及合法购买者可读打赏Markdown/清洗HTML；重复不再付费/提醒，感谢与打赏各自独立。两者活动仍记原reward类别。
2. 匿名文章零余额操作仍可成功，双方不转账；普通余额不足422且不产生记录。感谢/打赏自己422，作者可直接读自己的启用打赏正文；未启用打赏422。合法锁定文章可读时仍可操作。非法ID/自定义price或author字段/缺键拒绝；缺/过期/撤销Bearer、仅Cookie/apiKey、缺scope拒绝；缺感谢角色403，未受邀讨论或无效作者/目标404，所有拒绝不扣款。
3. 调试器制造计数/reward/通知/结果保存失败、转账返回空或写入后异常，余额、积分记录、计数、reward、通知及幂等结果都回滚；同键恢复重试仅成功一次。已购买后调高价格不应再付费，撤销讨论邀请后新读取/新操作404，重放历史请求仅返回id/状态、不含历史正文。当前价格以提交时为准，客户端不能自定义价格。
4. PC/mobile旧感谢入口Cookie及query/body apiKey保持成功字段与费用，刷新后总数正确。旧打赏正常Cookie场景、原HTML格式与当前启用价格另回归；**旧正文返回保护尚未接线，不要将不合法状态当成已修复**，待用户兼容答复后再测明确读者与资格。并发同人同文、不同人付款、价格编辑、删除/后台强删及跨实例MySQL锁需实际验证，现有串行适配器测试不证明这些结果。
5. 检查HTML/JSON/通知不露未购买正文、匿名真实身份、IP/UA/联系方式/token/key。风控开启验证新IP、黑名单/test、验证码解封、匿名首访/计数与开发关闭盾。重启后可运行新smoke，默认拒绝/缺失目标检查；分别显式THANK_WRITE/PURCHASE_WRITE及一次性他人目标才会真实付款，费用不恢复；可选MAX_REWARD_POINT只检查事前公布价格，不是服务端价格锁。

API/UPDATE_LOG已同步，需同步网站API文章。后续继续旧打赏返回兼容确认、按资源类型检查删除清理和关联通知，再补其他互动/认证/设置/背包/上传/Socket/支付与运行验收；鱼游OAuth入口、旧编辑歧义兼容仍待用户。全量目标保持进行中。


## 2026-10-18：获批接通旧打赏正文保护

用户已明确批准同步保护旧 POST /article/reward。ArticleProcessor 从 loginCheck 写入的 User.USER 取得身份，调用 ArticleMgmtService#rewardForViewer，在实际付款前按锁定文章与当前账户复核可读性。结果仅允许 APPLIED/ALREADY/OWN，UNAVAILABLE/NOT_ENABLED 不组装正文；提交后重新读取当前读者、文章、作者与启用价格/归属，再用显式读者的旧 HTML 渲染器返回原 articleRewardContent 字段。缺失、失效、未受邀及余额不足走旧错误响应，不带正文。ID 限 1～32 位数字。HTML 渲染仍沿用原 Markdown 清洗链，显式读者分支按作者 ID 判断本人，避免原始文章未附作者名称时误拒绝讨论作者；其他旧调用保持原 Sessions 分支。

实际修改 ArticleProcessor.java、ArticleQueryService.java、ArticleMgmtService.java（边界注释），新增 LegacyArticleRewardEndpointTest.java，扩展 tools/api-v3-comment-removal-test.mjs；同步 API.md、UPDATE_LOG.md、AGENTS.md 与本计划。未新增路由/表、前端/FTL/产物或 CDN 资源。上节“等待兼容确认”只反映当时状态，本节取代该项。

验证：共享脚本 60 组通过（原 55 + 旧入口 5），4 个 Java 文件通过 Java 25 语法解析，runner 的 node --check 与 git diff --check 通过。未执行 Maven 或完整 Java 编译。从真实 ArticleProcessor 提取完整 rewardArticle 及其授权方法，连同真实付款核心编译运行，HTTP/查询/HTML/发布锁仍为显式适配器。覆盖首购、重复与本人、账户/作者停用、未启用且残留正文、未受邀、失效文章、余额不足、受邀锁定讨论、付款后邀请撤销/账户停用/关闭打赏/删除/归属变化、未认证及非法 ID；不证明真实 LoginCheck 的 apiKey 解析、HTML 清洗、IoC/缓存或 MySQL 并发行为。Cookie/apiKey 的旧 CSRF 设计未改变，不声称已解决。

安全及反漏扫源码复核：旧路由仍经 loginCheck，没有新增匿名入口或修改 anonymous.viewSkips；BeforeRequestHandler 黑名单/test、CaptchaProcessor 解封和匿名验证码链保持，运行回归待确认重启后执行。未鉴权拒绝、讨论越权与敏感正文由显式读者/资格双重检查覆盖；CSRF 沿用旧 Cookie/apiKey 规则，未新增豁免或声称解决。XSS 仍走原 Markdowns.toHTML/clean；注入使用数字 ID 和既有绑定查询，无客户端金额/作者输入；SSRF 无外部请求入口；批量滥用未新增频控，付款重复由锁与记录去重，真实负载尚未验证。输出仍仅旧成功正文白名单，不输出完整文章/用户。没有静态/CDN URL、版本号、sourceMappingURL、目录或上传下载返回路径变化。HTTP/浏览器验证上述项目仍未执行。

人工验收（用户先通过 IDEA 重编译/重启并确认）：
1. PC 和移动端进入他人已启用打赏的可读文章，点打赏→确认→检查正文原 HTML、余额/积分记录、购买记录及通知。重复请求只返回正文，不再次扣款；本人可读取自己的启用打赏内容。分别用 Cookie、query apiKey 和 body apiKey 请求旧入口，对照同一账户的购买状态与讨论邀请，不能借用其他会话身份。
2. 停用读者/作者、未启用但残留正文、未受邀讨论、失效/不存在文章与余额不足：请求失败且无 articleRewardContent，不产生购买或部分扣款；合法受邀者及作者访问锁定但可读的讨论仍成功。非法 ID、未认证请求在付款前拒绝。
3. 调试器在付款后、返回正文前撤销邀请/停用账户/关闭打赏/删除文章：不得返回正文；此前已经提交的合法购买仍可能存在，不能把返回时权限变化误当作付款事务失败。仓储写入异常须整体回滚，同一请求恢复后只付一次。
4. 复核 HTML 不带未购买正文、匿名身份及私密用户字段；开启风控复核黑名单/test、验证码解封及匿名入口跳转，开发关闭风控结果保持。检查正常 PC/mobile 渲染和 v3 详情授权；本轮无静态/CDN/上传下载路径变化。

完整启动、浏览器/HTTP、真实 HTML 与跨实例锁尚未验证，不能将适配器通过当作运行验收。API.md 内容仍需同步网站 API 文章。全量 v3 目标继续进行，后续资源类型清理、其他未完成模块与 OAuth 规则等待保持原计划。

## 2026-10-18：文章删除的投票与奖励类型隔离

检查发现 VoteRepository/RewardRepository 的 removeByDataId 原来只匹配 dataId；独立表间存在同 ID 时，文章清理会误删除评论/用户/标签投票及评论/用户奖励。改为必须传入类型，并要求调用方已开启事务；使用绑定 ID 与类型的当前读取得全部记录，再逐条移除，不依赖 Query 默认分页。ArticleMgmtService 删除核心明确清理文章投票与文章购买、感谢、采纳三类奖励。旧普通删除、管理员强删与 v3 DELETE 共用此核心，删除条件/费用/字段和提交后外部清理不变；没有新增事务或提前提交。

实际修改 repository/VoteRepository.java、repository/RewardRepository.java、service/ArticleMgmtService.java，新增 src/test/java/org/b3log/symphony/repository/ContentInteractionRemovalTest.java 与 tools/api-v3-interaction-removal-test.mjs；同步 API.md、UPDATE_LOG.md、AGENTS.md 与本计划。没有新增接口/Repository/表、SQL迁移、FTL、前端源码/产物或静态/CDN路径。

验证：新脚本 7 组通过（5 组清理 + 2 组原奖励存在性读取回归）。编译运行真实 VoteRepository 与 RewardRepository，使用实际 Latke jar；仅 select/remove/事务所有权以 SQLite JDBC 适配。覆盖同 ID 各种类型隔离、不同目标保留、101 条匹配投票及101条购买/感谢完整清理、后续类型失败与部分投票删除失败的整体回滚、绑定参数、全部合法类型、未知类型及无事务拒绝；existsForUpdate 的真实 SQL 查询及异常传播回归通过。不代表实际 ArticleMgmtService 全链路、IoC、缓存、MySQL行锁、并发或HTTP已通过；ArticleMgmtService 的四个明确类型调用为源码核对。

安全/反漏扫：本轮仅内部清理，无新增公开入口、鉴权或输入/输出字段。现有 v3 DELETE 仍经 Bearer content.write、本人检查与幂等事务；旧普通删除仍要求登录/权限，管理员入口保留管理中间件。删除 SQL 按值绑定，类型白名单，失败交由调用方回滚。未新增 HTML/URL/网络抓取、上传/下载路径、批量入口或敏感对象输出，未引入 XSS/SSRF/静态版本号/sourceMappingURL/目录枚举入口；未新增频控，CSRF/批量请求和风控的既有行为没有因内部清理而宣称修复。黑名单/test、验证码解封、AnonymousViewCheckMidware、anonymous.viewSkips 未修改，运行回归仍待重启确认。

尚未完成：NotificationRepository.removeByDataId 仍未按资源语义区分；文章及评论 AT 共用同类型/ID，积分通知又引用 rewardId，投票/关注通知可能引用组合ID，需要继续核对。管理员评论级联对投票/奖励/reaction 的完整清理也尚未统一。本轮仅解决投票与奖励的误删，不能宣称删除清理已全部完成。

人工链路（先由用户 IDEA 重编译/重启并确认；仅一次性开发测试数据）：
1. 建立满足原删除条件的本人文章，进入文章页或取得 content.write 令牌，经旧 POST /article/{id}/remove 或 v3 DELETE 删除。页面/列表不再显示文章、统计正确；v3 同键重试返回原结果，新键404。非本人、缺权限/令牌、锁定、未满24小时、周报、有互动分别保持拒绝，数据不变。
2. 管理员从文章管理入口删除带投票、购买、感谢和采纳记录的测试文章；同时预置与文章相同 dataId 但类型不同的评论/用户/标签互动，以及另一篇文章的互动。确认文章匹配记录全删（含超过100条），其他资源投票/感谢/购买状态与计数不变。此步依赖开发库种子数据，不能在生产数据上直接执行。
3. 在后续奖励类型或第二条投票删除处用调试器注入异常：文章、计数、已清理的互动和职业来源均应随外层事务回滚，页面仍可读；恢复后仅删除一次。管理员强删的全部缓存、索引、通知清理与 MySQL 多实例并发另外验收，不能由当前仓储测试推断。
4. 复核匿名/黑名单/test/验证码解封链及未鉴权、越权、CSRF、敏感信息输出保持原边界；本轮没有可新增扫描的静态/CDN/上传下载资源。API.md 需同步网站 API 文章，全量 v3 目标继续进行。

## 2026-10-18：内容删除的关联通知引用隔离

上一轮已完成投票/奖励的目标类型隔离，本轮移除 NotificationRepository 仅按 dataId 删除的旧入口。新 ContentNotificationReferences 根据实际生产/消费链区分文章和评论的直接引用、投票/收藏/订阅组合引用；购买、感谢、采纳通知引用 reward 记录ID，不能拿文章ID直接清理。RewardRepository 删除方法返回本次被删的记录ID，ArticleMgmtService 在同一外层事务内清理对应类型的通知；没有嵌套事务/提前提交。管理员评论级联也接入评论通知、评论感谢记录/通知与评论投票清理，并在任何写入前要求调用方事务。原评论更新统计/版本链路没有在本轮重写。

旧 AT 只有 dataType=2/dataId，没有文章或评论的区分字段。删除前以当前锁定读取检查另一类同ID目标；不存在对方目标时清理AT，存在时保留无法确定归属的AT。本人评论原子删除也使用此规则，依然收集实际受影响的收件人，在提交后刷新；与文章/管理员旧入口不同，这条已有刷新边界不变。保留歧义记录属于数据不足下避免误删的处理，不宣称消除了历史引用歧义。

实际文件：新增 util/ContentNotificationReferences.java、src/test/java/org/b3log/symphony/repository/ContentNotificationRemovalTest.java；修改 repository/NotificationRepository.java、RewardRepository.java、CommentRepository.java，service/ArticleMgmtService.java、CommentRemovalMgmtService.java，util/CommentRemovalQueries.java，src/test/java/org/b3log/symphony/service/CommentRemovalServiceTest.java，tools/api-v3-interaction-removal-test.mjs、api-v3-comment-removal-test.mjs。同步 API.md、UPDATE_LOG.md、AGENTS.md 与本计划。无新路由/表/Repository、SQL迁移、前端/FTL/生成产物或静态资源修改。

验证：typed interaction脚本12组通过（原7+通知5），真实Notification/Reward/Vote仓储和新SQL使用Latke jar编译运行，数据库select/remove/事务由SQLite适配。覆盖所有0～42通知类别样本、直接/组合ID与前缀重叠、同ID其他类型/自定义通知、105条同类通知完整清理、文章/评论ID碰撞时AT保留、reward ID及通知类型配对、用户奖励保留、对方目标查询失败、部分通知删除失败与reward已删后通知失败的整事务回滚、无事务/非法ID/表名/奖励类型拒绝。共用评论/金融/投票脚本61组通过（原60+本人评论碰撞1组），使用真实CommentRemovalMgmtService检查碰撞AT/文章通知保留、匹配评论通知删除及收件人结果；其仓储/支付/通知/可读性/锁仍是适配器。没有执行完整ArticleMgmtService/CommentRepository级联、真实IoC/缓存/框架事务拦截器/MySQL并发、HTTP或浏览器。未执行Maven；仓储真实类型编译不是全项目编译通过。

安全与反漏扫源码结论：没有新增匿名或公开路由；v3删除仍由Bearer+scope、本人/当前文章可读性和幂等事务约束，旧页面/管理入口保留原登录权限。SQL外部值绑定，内容ID限制避免组合匹配通配符，表名来自仓储且经标识符白名单；通知和奖励类型来自明确映射，无客户端金额/作者/接收者参数。没有新增HTML/URL/外部请求、上传下载或敏感对象输出路径，未新增XSS/SSRF/静态资源版本号/sourceMappingURL/目录枚举入口；未新增频控或改动Cookie/apiKey CSRF设计，不能称这些旧风险已消除。BeforeRequestHandler黑名单/test、CaptchaProcessor解封、AnonymousViewCheckMidware和anonymous.viewSkips未变。未鉴权/越权/CSRF/批量滥用/敏感信息及风控仍需运行回归，未默认通过。

人工完整链路（先由用户IDEA重编译/重启并确认；只用一次性开发数据）：
1. 从PC/移动文章页或v3 DELETE删除满足原条件的本人文章；从评论动作区或v3 DELETE删除满足条件的本人评论/段评。列表、统计、费用及返回字段保持，同键重放不重复执行，新键404；未登录、非本人、缺scope/权限、讨论不可读和互动/时间限制仍拒绝。查看相关账户通知列表和未读数，关联通知消失，其他资源与自定义通知保留。
2. 管理员从文章/评论管理入口强删带互动的测试目标，准备购买/感谢/采纳reward通知、赞踩组合引用、文章收藏/订阅引用及超过100条通知。确认按实际目标清理；相同dataId的其他类别消息、其他文章/评论、用户奖励记录不受影响。管理员只删评论时，不把文章上的采纳奖励当成评论感谢记录清理。
3. 开发库造文章/评论同ID、对应AT及明确类型通知。删除一方时，歧义AT保留，明确属于该方的通知清理；不碰撞时关联AT正常清理。前缀10-不应匹配100-，不能删除同组合ID的自定义消息。
4. 调试器在目标查找、部分通知移除、reward删除后通知移除处制造异常：整个外层业务回滚，原文章/评论、计数、奖励/投票、职业来源和通知保持；恢复后仅执行一次。本人评论的刷新只在提交后发生。实际多实例锁/缓存与管理级联须单独联调，SQLite测试不证明这些结果。
5. 开启风控回归新IP/黑名单/test、验证码解封、匿名首访/计数与开发关闭风控模式；检查HTML/JSON/通知无私密正文、匿名身份和敏感字段泄露。本轮无新增静态/CDN或上传下载资源需要发布。

剩余：管理员评论/文章cascade的reaction、缓存、全部统计及延迟通知竞争仍需继续复核，历史歧义AT无法自动判定归属。全量v3功能、认证/设置/背包/上传/Socket/支付与运行验收仍未完成；OAuth规则与旧编辑歧义兼容保持待用户提供/确认。API.md需同步网站文章。

## 2026-10-18：删除 reaction 清理与补签自然日规则准备

上轮通知隔离已形成代码和测试，属于实际进展。本轮补齐文章删除及管理员评论级联遗漏的reaction清理。ReactionRepository新增removeTargetInCurrentTransaction，要求调用方事务，校验目标类型，绑定类型/ID，当前读取得全部行再移除。ArticleMgmtService清理文章reaction，CommentRepository级联清理评论reaction；本人评论核心也调用该方法，删除CommentRemovalQueries中的重复SQL。目标锁由原调用链持有，无新增提交点/广播，其他目标记录保留。

用户本轮已明确批准：补签按自然日补一天，能接回已有连续记录时接回；新旧入口一起原子化，失败保留卡和patchStart，成功消耗与奖励保持。新增CheckinPatchMutation纯规则：严格解析自然日，恰好断一天的生产提示恢复上一段起始日期，断更多天只延长最近一天；使用时检查卡/提示、当前是否最长、今天已签到，适用时接回相邻最长记录；天数不用毫秒除法；生成扣一卡/去提示后的新背包和明确签到字段变更，不修改原输入，不产生奖励或积分操作。

**补签尚未接生产入口，没有新增v3补签路由。** 现有dailyCheckin仍先转账、再分别写背包和用户，patchCheckin仍是旧路径；纯规则测试不证明运行入口已失败不吞卡。下一步须统一每日签到/补签的用户与背包事务边界，再接旧入口和v3幂等写入。单实例ActivityMgmtService同步不足以证明多实例正确性，不得把批准或纯规则通过当作接线完成。

实际文件：修改repository/ReactionRepository.java、CommentRepository.java，service/ArticleMgmtService.java、CommentRemovalMgmtService.java，util/CommentRemovalQueries.java；新增util/CheckinPatchMutation.java、src/test/java/org/b3log/symphony/util/CheckinPatchMutationTest.java；扩展src/test/java/org/b3log/symphony/repository/ContentInteractionRemovalTest.java、tools/api-v3-interaction-removal-test.mjs和api-v3-comment-removal-test.mjs；同步API.md、UPDATE_LOG.md、AGENTS.md及本计划。无新表/Repository、FTL、前端/产物、静态/CDN或上传下载路径。公开文档只发布已接线的reaction行为。

验证：interaction脚本15组通过（原12+reaction3），真实ReactionRepository及原仓储编译运行，SQLite适配select/remove/事务。每类600条完整清理（超过原512页大小）、同ID其他类型/其他目标保留、部分reaction失败与调用者目标删除整体回滚、无事务/非法类型拒绝及ID绑定。共用脚本66组通过（原61+补签5），本人评论使用从真实ReactionRepository提取的完整删除方法，原费用/事务/隔离回归保留。补签覆盖跨月/跨年/闰日、一日/多日生产提示、历史连接和非相邻最长保留、时区/夏令时稳定、原值/非签到字段不变、无卡/无提示/当前最长/今天未签到/非法日期或数量拒绝。未执行补签数据库服务、完整管理级联、真实IoC/缓存/MySQL并发、HTTP/浏览器、Maven或全项目编译。

安全/反漏扫：无新增公开或匿名路由，既有Bearer/scope/本人/可读性及管理权限保持。reaction按内部类型及绑定ID操作，失败由外层事务回滚。补签纯规则没有客户端入口，不接受自定作者/日期/费用。无新增HTML/URL/网络请求/敏感对象输出，不新增XSS/SSRF、静态版本号/sourceMappingURL、目录或上传下载路径；CSRF/频控、黑名单/test、验证码解封、AnonymousViewCheckMidware、anonymous.viewSkips未改。未鉴权/越权/CSRF/批量滥用/敏感信息及风控仍待重启后运行回归。

人工链路（用户先IDEA重编译/重启并确认，只用开发测试数据）：
1. PC/mobile文章/评论动作区或v3 DELETE删除满足原条件的本人内容。内容消失、费用/统计/成功字段保持，相关reaction清空；同键重试不重复，新键404。未认证、非本人、缺scope/权限、不可读及原限制仍拒绝，reaction不变。
2. 管理员从文章/评论管理入口强删600条reaction的测试目标，预置同ID文章/评论/聊天室三类与其他目标。只清理所删目标；文章级联的评论reaction也清理，其他页面汇总/选中态保持。
3. 第二条reaction移除处注入异常，确认内容、计数、职业来源、奖励/投票/通知及此前reaction移除都回滚；恢复后仅删除一次。MySQL锁、缓存、管理员完整级联和并发设置reaction/删除需真实联调。
4. 补签仅运行独立规则测试，生产入口暂不验收失败不吞卡；共享事务接线后再测跨月/跨年、同键重放和与自动签到并发。开启/关闭风控回归新IP、黑名单/test和验证码解封。

完整v3目标保持进行中。下一阶段优先补签共享事务与幂等入口，随后继续改名卡及其他模块。API.md仍需同步网站文章。

## 2026-10-18：补签共享事务、新旧接线与 v3 入口

上轮reaction修复及补签纯规则为实际进展。本轮完成接线：新增 CheckinMgmtService，ActivityMgmtService 的 dailyCheckin/patchCheckin 保留原公开方法和返回约定，委托共享服务。账户对锁在事务前取得，每日签到和补签都先当前读锁用户，再锁背包；使用 ArticlePublicationCaches 延后积分转账导致的用户缓存刷新。UserRepository 新增只按列更新且不发布缓存的通用原语，原 settings 包装委托它；BagMgmtService 增加事务内背包变更回调。核心不自行提交，也不在事务中发外部效果。

每日签到按当前用户时间戳及当天原始 sys→user/type8 转账当前读去重，基础随机奖励公式及十天奖励金额保持；DailyCheckinMutation按自然日计算连续区间/最长记录及跨月补签提示。基础/十天转账、提示和用户签到字段在一事务保存，任何步骤失败整体回滚，提交后才索引和刷新缓存。首签仍只发基础奖励。补签使用已获批 CheckinPatchMutation，扣一卡、去提示、更新连续字段同事务，不额外发签到积分。没有恢复关闭的手动签到路由，仍仅供原活跃度/自动签到调用。

新增 POST /api/v3/me/bag/patchCheckinCard/use，activity.write + 写入频控，空请求体白名单，不接受日期/用户/数量。ApiV3BagMgmtService 在锁/缓存边界内执行 bag.patchCheckin 幂等事务；响应仅 itemCode、streak/start/end、longestStreak 和数值背包DTO，不存完整用户或积分对象。相同键返回历史结果，不重扣卡或覆盖后来签到；新键成功后通常因无提示409。404用于不可用账户，409用于无卡/无提示/最长/未签到或异常提示，422用于请求/key。旧 /bag/patchCheckin、/api/bag/patchCheckin 共用同一核心；页面身份取loginCheck的User.USER，失败提示明确卡/记录未消耗，正常成功字段/费用保持。旧GET/CSRF/API鉴权形态未改。

前节“补签尚未接生产入口”已由本节取代，用户批准事项已落地。自动免签天数的定时扣减仍在 LivenessMgmtService 原链路，未在本轮擅改其经济规则；其与实际任务执行/失败和并发的完整行为仍需后续联调复核，不把补签事务通过扩张为整个调度系统完成。

实际文件：新增 service/CheckinMgmtService.java、util/DailyCheckinMutation.java、src/test/java/org/b3log/symphony/service/CheckinMgmtServiceTest.java、tools/api-v3-checkin-test.mjs、tools/api-v3-patch-checkin-smoke.mjs；修改 service/ActivityMgmtService.java、BagMgmtService.java、ApiV3BagMgmtService.java，repository/UserRepository.java，processor/SettingsProcessor.java、ApiV3BagProcessor.java，util/CheckinPatchMutation.java、src/test/java/org/b3log/symphony/util/CheckinPatchMutationTest.java（零年前导日期拒绝）。同步 API.md、UPDATE_LOG.md、AGENTS.md、本计划及 docs/api-route-inventory.md。无新表/Repository、SQL迁移、FTL、前端源码/产物或静态/CDN资源变化。

验证：新 checkin 脚本15组通过（10组实际服务+5组纯规则）；原背包脚本2组通过。编译运行真实 CheckinMgmtService、BagMgmtService、ApiV3BagMgmtService、幂等协调器/请求仓储、CloudRepository 与自然日规则，实际Latke jar；用户/转账仓储、SQL访问、支付和缓存用SQLite适配器，金额常量取当前源码/配置。覆盖每日断签提示→v3补签→GET状态来源/同键重放/新键拒绝；bag/user/placeholder/result/提交前失败回滚并可同键重试；部分奖励、后续提示/字段或十天奖励失败整体回滚；首签、十天精确金额/仅一次奖励；旧成功/错误码和拒绝不吞卡；原始转账去重、失效/缺失用户；无事务/账户锁/缓存边界拒绝及提交后缓存失败清除；缺失背包创建与创建失败回滚、重复背包归一；免签卡和补签同键不同operation独立；补签后次日签到到30天按原金额发奖励，旧补签重放不覆盖新天数；跨月/年/闰日/夏令时和异常值纯规则回归。适配器提交异常在真实提交前注入，不证明提交结果不确定的网络故障处理。未运行真实 UserRepository 缓存、积分服务、框架IoC/事务代理、MySQL多连接竞争、HTTP、浏览器或定时任务。

路由清单672条，浏览器引用398条。仅新增Java API，不需要yarn构建。未执行Maven/完整Java编译；实际服务隔离编译不等于全项目类型检查通过。新smoke尚未运行，必须先由用户IDEA重编译/重启并确认；默认只做拒绝/校验，API_V3_ALLOW_PATCH_USE=1才会对一次性测试账号真实扣卡，无法撤回。

安全及反漏扫源码复核：新路由强制Bearer activity.write，身份仅ApiV3Requests.viewer，不从Cookie/apiKey补身份，owner来自令牌；旧两入口保留loginCheck，页面保留CSRF，API旧GET及Cookie/apiKey CSRF设计未改。补签无自定义日期/数量/接收者/作者字段，JSON/key/体积由统一入口限制；SQL值绑定，列来自服务白名单，响应不含邮箱/IP/UA/token/key、双边余额或完整用户。写入经过共享频控，重复/异常不消费或发奖励；无HTML/URL渲染、外部抓取、上传下载入口，因此未新增XSS/SSRF路径。没有静态/CDN版本号/sourceMappingURL、目录或文件路径新增。BeforeRequestHandler黑名单/test、CaptchaProcessor解封、AnonymousViewCheckMidware首次/每5次和anonymous.viewSkips已源码复核未变，新增入口明确要求登录。未鉴权/越权/CSRF/注入/批量滥用/敏感信息及风控的HTTP/浏览器结论仍待运行验证，不能默认通过。

人工完整链路（用户先IDEA重编译/重启并确认，仅一次性开发数据）：
1. 准备有两张补签卡、前一段签到截至前天的账号，按原活跃度触发今日签到；核对基础奖励一笔、今天连续数1、背包提示指向前段起始日。用activity.read/write令牌GET /me/bag和/me/checkin→POST补签→再GET：卡-1、提示移除、连续日期自然接回、积分不额外增加。重试同键只返回旧结果，换新键409；补签后次日触发签到继续延长，到十天倍数沿用原奖励。旧键重放不能把新天数覆盖回去。
2. PC和移动端设置背包点击补签，分别回归Cookie页面、query/body apiKey旧API；成功code/msg及最新bag保持，刷新页面连续天数正确。无卡、无提示、今天未签到、当前记录就是最长、非法日期、停用/缺失用户分别拒绝，卡/提示/用户/积分均不变。未认证/过期或撤销Bearer、仅Cookie/apiKey、新路由缺activity.write拒绝；自定date/count/userId、非法/缺幂等键422。
3. 调试器制造背包更新、用户更新、积分部分写入/第二笔奖励、幂等结果保存、提交前失败：卡/提示/连续字段/余额/原始转账及journal全部回滚，恢复后仅成功一次。缓存或索引失败不能撤销已提交业务；缓存失败应清除，下一请求从数据库恢复。缺失背包创建和重复背包处理一起检查，其他道具/用户资料保持。
4. 在跨月/跨年、闰年、平台午夜、时区/夏令时样本核对自然日；并发旧补签/v3同键/新键与活跃度自动签到，检查不重复奖励、只扣一卡、连续记录不覆盖。必须用真实MySQL多连接/多实例验证行锁、框架事务、缓存与定时任务；当前串行SQLite测试不能证明这些结果。
5. 开启风控验证新IP未鉴权、黑名单/test、验证码解封、匿名首访/计数；关闭开发风控回归正常访问。检查JSON/页面/日志无敏感用户/积分字段泄露。本轮无新静态/CDN或上传下载文件需要发布。

全量v3目标继续进行，改名卡及其他未完模块/运行验收仍未完成；OAuth规则和旧编辑歧义兼容仍待用户提供/确认。API.md内容需同步网站API文章。

### 2026-10-18 改名卡接线与名称占用

用户已明确批准新旧改名卡一起原子化。新增 POST /api/v3/me/bag/nameCard/use，只取Bearer身份，必须同时有activity.write/settings.write；JSON仅userName，trim后沿用注册规则。成功仅itemCode/userName/白名单bag；同键不同内容409，同内容重放返回历史结果，不回写当前名字。无卡/占用409，格式422，账户不可用404。旧页面保持不trim，旧API保持trim，正常成功字段和一张卡消耗保持。

本轮实际文件与行为：

- `src/main/java/org/b3log/symphony/util/UserNameRules.java`、`processor/middleware/validate/UserRegisterValidationMidware.java`：提取既有1–64位ASCII字母数字短横线与保留名规则，注册委托共用。
- `src/main/java/org/b3log/symphony/service/UserNameMgmtService.java`：名称修改与扣卡同事务、旧入口提交边界、结束后缓存处理。
- `src/main/java/org/b3log/symphony/repository/UserRepository.java`：事务结束后移除原名称缓存并刷新已提交资料/会话。
- `src/main/java/org/b3log/symphony/service/UserMgmtService.java`：注册、普通改名及未验证名称清理共用名称空间锁和当前读；保留注册未验证账号政策。普通积分改名的扣费仍在外层旧链路，不能称整体原子化。
- `src/main/java/org/b3log/symphony/processor/ApiV3BagProcessor.java`、`service/ApiV3BagMgmtService.java`：新路由、双scope、白名单输入/响应及同事务幂等结果。
- `src/main/java/org/b3log/symphony/processor/SettingsProcessor.java`：旧/bag/nameCard、/api/bag/nameCard接入统一事务，移除独立扣卡/退款；使用中间件认证身份，结果不确定时提示刷新资料和背包。
- `src/test/java/org/b3log/symphony/service/UserNameMgmtServiceTest.java`、`CheckinMgmtServiceTest.java`、`tools/api-v3-checkin-test.mjs`：服务测试及SQL/缓存适配器；`tools/api-v3-name-card-smoke.mjs`默认仅拒绝检查，明确设置消耗开关及新名字才改名，不花第二张卡自动改回。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、本文和`docs/api-route-inventory.md`：公开契约、更新记录、关键链路及迁移进度；清单现673路由/398浏览器引用。

名称空间使用既有会员统计option行锁，先该行再用户行、最后背包，无新表。重复名称按数据库现有比较规则；注册当前读优先已占用状态，未验证名称清理先锁名称空间再当前读，避免旧快照覆盖并发更改。全局串行名称空间的生产吞吐及死锁处理需MySQL多连接验证。自动补全USER_NAMES仍沿用原定时刷新，不宣称实时刷新。

已执行：checkin/name专项21组（签到/补签15+改名6），原bag专项2组，8个生产Java文件Java25语法解析，脚本node --check与git diff --check通过。实际编译运行Name/Bag/Checkin/Idempotency服务、Cloud/请求仓储及规则；User/Option/积分/缓存使用SQLite适配器。覆盖名称格式/保留名、两账号同键隔离及占用冲突、适配器NOCASE比较、双次改名后旧键重放、用户/背包/幂等占位及结果/提交前失败全部回滚且可重试、旧核心相同行为、提交后缓存失败不撤销业务、名称空间锁顺序及注册占用优先级。SQLite移除FOR UPDATE，不能证明MySQL并发；提交异常在真实提交前注入，不证明网络故障最终结果。完整注册、旧控制器、真实UserCache/Sessions、框架IoC/事务代理、HTTP与浏览器未运行。未执行Maven。

反漏扫与安全源码检查：新路由要求双Bearer scope，匿名/Cookie/apiKey不能形成v3身份；旧入口保留loginCheck，页面保留CSRF。旧API同时接受Cookie/apiKey且原先未挂CSRF，此既有边界未改，不能宣称本轮消除其全部CSRF风险。未增加anonymous.viewSkips；BeforeRequestHandler黑名单仍跳/test，CaptchaProcessor解封与匿名首访/计次逻辑未改。越权由Bearer固定用户和所有者锁防护；SQL值参数化，严格ASCII名字不能注入HTML；没有服务端取URL，SSRF不适用；批量写经已有预算与幂等，名称空间压力仍需实测；DTO不返回完整用户或联系方式/键/IP/UA。无新增公网静态资源/CDN URL/sourceMappingURL/目录/上传下载路径，无前端源码和产物修改，不需yarn。黑名单/验证码、真实请求拒绝、CSRF、静态目录配置及生产CDN扫描均未运行验证。

人工完整链路（先通过IDEA重编译/重启服务端并告知确认，再联调）：

1. 准备两账号和各两张改名卡，登录取activity.read/activity.write/settings.write令牌；GET /me/bag与/me记录状态→POST新名字及新键→再GET并访问本人主页：姓名改变、卡减一、其他字段和物品保持。同键同内容不再扣卡，同键不同内容409；新键再次改名后重放旧键，响应是历史结果但当前名字不回退。
2. PC及移动/settings/account背包点击改名卡；分别用query/body apiKey回归旧API，操作者必须按中间件既有优先级确定。无卡、占用（包括当前名/大小写同名）、保留名、空白/超长/下划线/中文拒绝且卡不变。v3缺Bearer/仅Cookie或apiKey401，缺任一write scope403，缺键/非法或多余字段/数量/其他用户ID422。
3. A/B、新旧接口、注册与普通改名并发抢同名，只有一个占用，失败者不扣卡；并发发卡/其他道具操作最终背包正确。未验证账号注册保留原处理，已有占用不能被抢走。注入用户/背包/幂等保存/提交前失败全部回滚，恢复后同键仅成功一次；缓存失败后下次从已提交数据库恢复，核对原名查询、当前主页、会话及定时自动补全。
4. 新IP匿名不能调用登录接口；黑名单仍跳/test，/validateCaptcha解封后按正常鉴权处理；开发关闭风控仍须双scope。页面缺CSRF拒绝，旧API合法query/body apiKey可用。响应无邮箱/电话/IP/UA/凭据，错误无SQL或内部异常。

全量v3目标仍进行中，认证/其他设置/上传/Socket/支付与运行验收尚未完成；鱼游OAuth规则入口与旧编辑歧义兼容仍待用户提供/确认。API.md需同步网站API文章。

### 2026-10-18 v3短期上传票据

上轮改名卡属于实际进展。本轮新增POST /api/v3/uploads/tickets：uploads.write、Bearer身份、已有写预算；JSON空body，不接受用户/网址/scope/时长等自定义字段。数据库只按Bearer用户ID+有效状态投影oId/userName/userNickname，不从Sessions或apiKey补身份；只用已配置的公开上传地址，不用internalUrl。响应为ticket/tokenType/uploadURL/fieldName/expiresIn；uploadURL是完整/api/v1/files路径，字段名file，180秒，每次取票新签发，不要求业务幂等键，也不把短期凭据存进api_v3_request。此为凭据签发而非数据库业务写事务，无新增Repository/表。文件、目录、代理上传和内部删除能力不搬进v3。

新增RhypicUploadTickets共用现有Ed25519协议，固定iss/aud/file:upload和随机jti；旧RhypicProcessor的私有signTicket改为委托，原字段/180秒/旧基地址响应及mintUploadTicket调用形态保留。新接口额外校验公开目标URL：HTTPS或本机开发HTTP，不含userinfo/query/fragment/控制符或歧义路径，配置非法503且不输出私钥、内部目标或异常内容。公开URL只发给客户端，不发服务端HTTP请求。尚未取得RhyPic服务端源码；现有源码注释说明一次性票据，但本轮没有验证消费去重、文件限制和跨域，因此公开文档仅承诺短期取票协议，不宣称完整直传已验收。尝试读取现有图床对接文章失败，不能当成协议确认。

新增uploads.write至默认website客户端策略及SQL尾部升级，仅匹配原未自定义默认策略；不改旧token已授予scope，刷新仍沿用原scope，需执行SQL并重新登录。scope长度仍在255列约束内，重复运行升级不重复追加。未改图床密钥/内部令牌配置；仓库调试值按既有约定不误报生产泄露。

本轮实际文件：

- `src/main/java/org/b3log/symphony/processor/ApiV3UploadProcessor.java`、`service/ApiV3UploadTicketService.java`：新路由、请求/身份边界、当前用户投影和凭据白名单。
- `src/main/java/org/b3log/symphony/util/RhypicUploadTickets.java`、`util/ApiV3UploadTargets.java`：共用签名协议与新接口公开目标校验。
- `src/main/java/org/b3log/symphony/processor/RhypicProcessor.java`：旧签名委托，保持原请求响应和服务端代理辅助能力。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`、`processor/middleware/ApiV3AuthMidware.java`、`service/ApiV3TokenMgmtService.java`：注册入口、专用scope与默认策略。
- `sql/20261016_api_v3.sql`：仅原默认策略升级，未执行数据库SQL。
- `src/test/java/org/b3log/symphony/service/ApiV3UploadTicketServiceTest.java`、`tools/api-v3-upload-test.mjs`：签名/服务/旧mint/输入/策略SQL验证。
- `tools/api-v3-upload-smoke.mjs`：默认拒绝检查；显式开关才能签票，额外文件和开关才上传一个小型本地fixture；不打印票据，不把Rhythm accessToken发送到图床。
- `tools/api-route-inventory.mjs`、`docs/api-route-inventory.md`：识别uploads.write，清单674路由/398浏览器引用。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、本文和`docs/api-v3-inventory-and-design.md`：公开两阶段上传契约、更新记录、关键链路及实际设计进度。

验证：upload专项7组通过。真实编译运行签名、URL校验、v3签票服务、旧RhypicProcessor及Latke输入解析；UserRepository、Symphonys和ApiV3Requests错误构造用明确适配器，未运行完整auth/IoC/控制器路由。签名用临时生成密钥并验证Ed25519、固定claims/唯一jti/180秒、Unicode往返；真实服务验证按当前用户投影、两账号隔离、DTO无邮箱/键/内部信息、重复签发新凭据、停用/缺失账户/配置/私钥/查询失败不发票；旧mint签名协议保持；真实RequestContext拒绝可指定身份/目标/时长/scope/旧凭据的body；真实SQL升级UPDATE谓词在SQLite适配CONCAT后执行，验证旧默认逐级升级、重复执行、自定义及其他客户端不变，并对齐实际默认scope源码。令牌回归脚本通过；8个生产Java文件语法解析、node --check、git diff --check通过。未执行Maven、数据库升级、HTTP/浏览器、实际图床上传或过期/重用票据校验。

反漏扫及安全源码结论：新路由明确登录+专用scope，无新增匿名入口或anonymous.viewSkips；BeforeRequestHandler黑名单/test、匿名首访/计次、validateCaptcha未改。v3只取Authorization，不借Cookie/apiKey身份，无新增Cookie CSRF能力；旧票据入口登录及CSRF边界保持，不宣称修复其既有Cookie风险。所有者来自Bearer并当前读有效用户；SQL值绑定；无上传文件或用户网址输入，因此此入口不解析文件、不产生服务端URL请求；昵称只进JSON/JWT，不作为HTML输出。固定短期上传scope，没有内部删除授权，响应无私钥/内部令牌/邮箱/IP/UA。取票走写预算与现有全局防火墙，但签名CPU/突发多实例压力仍需实测。凭据返回no-store，审计仅记录结果/用户ID/请求ID。未增加公网静态资源、CDN URL、sourceMappingURL或可枚举目录；新返回的是配置公开上传端点，未新增下载路径；目录防枚举和图床响应/文件URL不能凭Rhythm源码证明。黑名单/验证码、真实未鉴权/越权/CSRF/XSS/注入/滥用/信息泄露及图床权限均仍待联调。

人工端到端步骤（必须先IDEA重编译/重启服务并确认）：

1. 先执行默认策略升级SQL，对原默认及自定义客户端分别核对；原令牌调用新口403，refresh后仍无新scope，重新登录获得uploads.write。Bearer取票→检查完整uploadURL/fieldName=file/180秒→以上传ticket向图床multipart上传小PNG并保留文件名→确认succMap文件URL可显示；Rhythm accessToken不可当图床票据。两账号分别上传，图床归属应与各自Bearer一致，不能由请求body指定用户。仅Cookie/apiKey、失效/撤销令牌401，缺scope403，多余字段422。
2. 过期票据、相同票据重用、格式/大小违规文件、无后缀或缺文件名Blob、部分文件失败、图床断开/CORS失败逐项按图床实际约定检查；失败后重取票再传，旧票据有效期不因重取延长。确认错误不输出签名私钥/内部令牌/完整异常。图床未启用或配置/密钥不正确返回503，不返内部地址。localhost开发允许本机HTTP，生产使用公开HTTPS。
3. PC及移动编辑器贴图走旧/api/rhypic/upload-ticket再file直传；头像裁剪/涂鸦的旧/upload仍file[]，内部再转file并带文件名；验证成功及格式失败提示保持，旧ticket/uploadURL/expiresIn和代理succMap/errFiles形态保持。使用伪造/跨用户/未经授权凭据必须拒绝，旧兼容query/body apiKey不被新scope误拦。
4. 新IP匿名不能调用登录接口；黑名单仍/test，验证码解封后按Bearer权限执行；开发关风控不能绕过scope。批量签票限流、并发及不缓存响应要实测；核查图床回传URL、可访问内容、禁止目录枚举及公网静态/CDN版本/source map情况。smoke脚本默认不上传，只有显式许可和fixture才写入图床，不自动删除文件。

全量目标继续，上传端到端、认证/PKCE、其他设置、Socket/支付及全量运行验收未完成；鱼游OAuth规则入口及旧编辑歧义兼容仍待用户。API.md需同步网站文档文章。

### 2026-10-18 Socket握手边界与一次性凭据核心（尚未开放路由）

上一轮上传为实际进展。本轮先追踪Router、User/Article/Chatroom通道与当前Latke jar API/字节码，确认WebSocket升级直接由WebSocketHandler完成，不调用Dispatcher.handle/BeforeRequestHandler；框架先写ip参数，再覆盖用户query参数，故session.getParameter("ip")单独使用不可信。生产聊天室主要走独立节点，ChatroomChannel是故障回退，不能把旧节点apiKey协议包装成v3；UserChannel也混合通知/私聊/管理指令，不能原JSON转发。

新增ApiV3SocketHandshake，隔离对当前jar的少量反射元数据访问（WebSocketSession.ctx→pipeline内WebSocketHandler.handshaker→uri）。只接受调用方给定的精确、无query请求目标后才信任框架ip，拒绝所有query（包括ip/apiKey/ticket）、路径伪装、非法IP与缺失元数据；没有Cookie或用户身份解析。反射结构变更时返回拒绝，不能fallback。该适配只解决传输元数据，不负责认证或黑名单；后续通道还必须显式执行防火墙/黑名单、无认证超时、容量上限及消息预算。

新增ApiV3SocketTicketService与独立api_v3_socket_ticket表：单独保存opaque票据哈希、owner、parentTokenId、createdAt/expiresAt/consumedAt，避免把Socket凭据混入旧access/refresh表或业务幂等结果。签发事务当前读原access令牌+用户+客户端，要求有效/未过期/notifications.read；有效期最多60秒且不能超过原令牌。消费锁票据再锁当前父令牌关联行，在同一事务将consumedAt置非零，成功返回仅供服务端连接注册的Grant。连接Grant上限为原access过期，authorized会重查撤销/过期/类型/scope/用户/客户端。拒绝或保存失败回滚，不返回可用Grant；未知提交结果仍需客户端重新取票，不能据本轮提交前故障模拟宣称网络不确定结果已验证。授权期间仍需处理父行锁与批量撤销的真实MySQL死锁，失败应拒绝连接而不能放宽认证。过期票据清理任务仍待接线阶段补齐。

实际文件：

- `src/main/java/org/b3log/symphony/util/ApiV3SocketHandshake.java`：当前Latke传输元数据适配及拒绝策略。
- `src/main/java/org/b3log/symphony/model/ApiV3SocketTicket.java`、`repository/ApiV3SocketTicketRepository.java`、`service/ApiV3SocketTicketService.java`：独立凭据模型、加锁SQL和签发/一次消费/重校验事务核心。
- `src/main/resources/repository.json`、`sql/20261016_api_v3.sql`：同步新Repository及建表，模型和super名称不带symphony_；SQL未执行。
- `src/test/java/org/b3log/symphony/util/ApiV3SocketHandshakeTest.java`、`service/ApiV3SocketTicketServiceTest.java`、`tools/api-v3-socket-test.mjs`：实际jar字段/Netty pipeline及SQLite事务验证。
- `AGENTS.md`及本文：关键边界、迁移状态及后续接线约束。

验证：socket专项9组通过，其中握手4组用真实jar的session/handler字段及Netty EmbeddedChannel，未执行真实HTTP升级；票据5组编译实际Socket服务、SocketRepository、Token/ClientRepository与TokenMgmtService（使用实际hasScope），以SQLite适配用户仓储/事务和SQL，UserQueryService明确禁止调用（未测试完整HTTP Token认证）。覆盖精确路径/部署路径、IPv4/IPv6、query覆盖IP及URL凭据拒绝、无元数据拒绝；hash-only存储、两账号隔离、一次消费、父令牌撤销/过期/类型/scope/用户/客户端状态变化同时影响待消费票据和已连接Grant；错owner/parent、未知/非法/过期票据拒绝；签发及消费的写入/提交前失败回滚后可重试；事务守卫和连接原过期时间。SQLite去掉FOR UPDATE，测试为串行，不能证明真实MySQL竞争或多节点消费；实际握手请求/代理头/反射适配在部署的可用性仍需验证。未执行Maven、数据库升级、HTTP、浏览器或节点连接。

**没有新增HTTP取票或WebSocket路由，没有事件发布接线，不能调用或宣称v3 Socket可用。** 因没有对外接口契约变更，本轮不在API.md/UPDATE_LOG发布占位接口；现有路由清单674/398保持。下一步先接通知专用取票/首帧认证/连接注册、授权失效关闭及提交后白名单事件；不能直接订阅UserChannel全量命令。文章与聊天室另需资源可见性和节点协议接入，不以通知通道替代全部Socket目标。

安全/反漏扫：本轮没有新增公开入口，anonymous.viewSkips、HTTP黑名单/test、匿名首访/计次及validateCaptcha链路保持。新增SQL全部绑定值，表名取Repository；票据只存哈希、不输出原始用户/凭据，纯服务不信Cookie/apiKey；无HTML输出、用户URL抓取或文件内容，因此无本轮新增XSS/SSRF/上传下载面。模型/JSON/SQL字段一致已静态检查，增改均显式事务commit。无前端、CDN、新公网静态资源、sourceMappingURL、可枚举目录、上传/下载返回路径修改。真正公开后仍须验证认证/越权/CSRF/注入/批量滥用/信息泄露，以及Socket显式黑名单与代理信任边界；现有页面/静态目录/线上CDN扫描未运行，不能默认为通过。

人工步骤：当前从`node tools/api-v3-socket-test.mjs`进入离线验证，应输出9组PASS；它会执行签发→仅哈希落库→消费→重放拒绝，并注入撤销/禁用/提交前错误检查回滚，不访问应用数据库。真正接口接线后，必须先IDEA重编译/重启并确认，再测登录取票→无query Socket→首帧认证→本人通知事件→HTTP重新读取；两账号/旧Cookie/apiKey/黑名单/超时/过期及重放票据应拒绝，撤销令牌应停止事件。PC/移动旧通知、在线时间结算、文章和聊天室连接必须单独回归，当前源码未改变这些入口。全量目标仍进行中，后续连接生命周期/事件/清理、其余API及完整运行验收尚未完成，OAuth规则入口仍待用户。

### 2026-10-18 通知Socket入口与生命周期接线

前一节握手/凭据核心是实际进展；本节已新增POST /api/v3/realtime/tickets（notifications.read+写预算）及WS /api/v3/realtime。取票body仅空JSON；actor和原tokenId从IDENTITY取，不使用请求字段或User.USER页面身份。返回一次票据、最多60秒expiresIn、含部署上下文的path和notifications频道。成功消费记录同事务，连接有效期不超过原access，刷新不会延长已连接授权，需新票据重连。当前接口限定通知频道；文章与聊天室实时能力仍未接入。

连接核对原始精确无query路径及可信IP元数据后，在Netty事件循环收紧仅本通道的WebSocket13 decoder及fragment aggregator，再登记有限连接。发authentication.required控制提示后才接受authenticate首帧，避免框架onConnect/onMessage异步调度次序导致正常客户端过早发送凭据；未认证阶段不发送用户信息。严格JSON/字段/字节限制，只接受一次authenticate及其后的ping；全局/IP/用户容量和消息预算、认证超时、原授权到期、后台授权重查、黑名单复查、背压与异常关闭均实现。快扫不做数据库读取，慢授权检查在有界worker中执行；timer/worker为daemon，懒初始化。raw socket不能依赖HTTP顶层handler，所以显式执行现有Firewall及黑名单；不改旧WebSocket处理器或framework jar。实际升级前后竞态、代理头信任和框架异步行为仍待真机验证。

事件仅notifications.changed+空data，不转发UserChannel命令、原始通知、正文、owner/dataId或用户对象。按owner索引投递，逐连接当前授权后发送；有界队列及合并提示，队列marker在发送前释放，避免发送过程中较晚提交的变更被旧marker吞掉。NotificationMgmtService的两个统一发布点仅在无活动事务时转发；v3通知读写/关注及共享评论提醒、投票、感谢、文章打赏等已存在的提交后发布可进入新通道。**部分旧@Transactional内部早发发布点会被跳过，尚未全部迁移到提交后；不能宣称所有历史通知变更均覆盖。** 事件为尽力刷新提示，不做持久化重放，断线或初次连接必须HTTP重读；多进程事件分发尚未建立，不能把本机registry证明成跨节点通知总线。

过期票据清理：已消费或未消费且到期一天以上的哈希分批删除；每次独立事务200条，后台每轮最多5批，并禁止重叠执行。未增加表（沿用上轮socket_ticket），实际SQL升级仍未执行。生产签发量、清理吞吐与数据库故障恢复需运行验证。

实际文件与行为：

- `src/main/java/org/b3log/symphony/processor/ApiV3RealtimeProcessor.java`、`processor/channel/ApiV3RealtimeChannel.java`：取票和Socket入口、显式握手准备及首帧认证交接。
- `src/main/java/org/b3log/symphony/util/ApiV3RealtimeSessions.java`：有限容量、owner索引、严格消息、超时/重查/关闭/白名单提示；`service/ApiV3RealtimeService.java`：实际session适配、风控、背压、有界worker/定时任务和合并发布。
- `src/main/java/org/b3log/symphony/util/ApiV3SocketHandshake.java`：仅新通道的协议帧/分片上限及可写检查，元数据或pipeline不支持时关闭。
- `src/main/java/org/b3log/symphony/repository/ApiV3SocketTicketRepository.java`、`service/ApiV3SocketTicketService.java`：清理SQL和事务、发布时无事务守卫。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`：注册新模块；`service/NotificationMgmtService.java`：仅无活动事务的发布点转发空失效提示，原通道字段不变。
- `src/test/java/org/b3log/symphony/service/ApiV3RealtimeSessionsTest.java`、`util/ApiV3RealtimeChannelTest.java`、`util/ApiV3RealtimeProcessorTest.java`：registry真实凭据核心、实际facade/Netty传输及实际身份中间件/取票控制器测试。
- `src/test/java/org/b3log/symphony/util/ApiV3SocketHandshakeTest.java`、`service/ApiV3SocketTicketServiceTest.java`和`tools/api-v3-socket-test.mjs`：补pipeline/清理验证及实际新类编译运行。
- `src/test/java/org/b3log/symphony/service/ApiV3UploadTicketServiceTest.java`：测试配置赋值改为隔离适配器反射，避免直接给实际源码final常量赋值造成测试源码编译错误；未改上传业务。`tools/api-v3-notification-test.mjs`补齐此前新增ContentNotificationReferences及Reward源码依赖，修复回归脚本编译失败。
- `tools/api-v3-realtime-smoke.mjs`：重启确认后可运行；默认只拒绝检查，显式开关才取票并连接/重放/等待未认证关闭，不修改通知，不打印凭据或原响应。
- `tools/api-route-inventory.mjs`、`docs/api-route-inventory.md`：新增WS清单提取，现685条（675 HTTP声明+10 WS声明，含禁用项）/398浏览器引用；不把WS伪标成经过HTTP中间件。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`及本文：公开协议、变更日志、关键链路及迁移状态。

验证：socket专项20组通过（握手5/票据6/registry5/实际channel2/实际取票控制器2），上传7组及通知SQL/状态/DTO9组回归通过，9个生产Java文件语法解析、node --check、git diff --check通过。实际编译运行新增channel/facade/registry/控制器、握手/凭据服务、actualAuthMidware/ApiV3Requests；事务/用户读取用SQLite适配，真实HTTP令牌认证、全局防火墙和黑名单cache实现、HTTP写预算、最终响应辅助有明确测试适配器。实际hasScope、RequestContext及输入解析未替代。Native channel测试用真实Latke Session/handler字段、Netty pipeline、实际sendText→TextWebSocketFrame和关闭回调，认证权威为受控适配器；并非完整HTTP Upgrade/TCP/浏览器。覆盖challenge→auth→ready→本人提示→ping/pong→撤销关闭、拒绝query/黑名单、跨账户不接收、事务内发布拒绝、失败回滚/一次票据、严格消息/超时/慢认证/原令牌到期/当前状态/预算/背压及清理。未运行真实NotificationMgmtService事务代理发布、所有旧生产者、MySQL锁竞争、多进程、原生TCP或浏览器；没有把源码检查或适配测试当作这些运行证据。未执行Maven或数据库升级。

安全/反漏扫结论：取票必须Bearer+scope，Socket只能一次票据、当前授权和显式风控；无Cookie/apiKey身份回退，URL凭据拒绝。HTTP匿名首访/计次、anonymous.viewSkips、BeforeRequestHandler黑名单/test及CaptchaProcessor解封均未改；WS黑名单必须显式关闭连接，不能假设HTTP重定向适用。新IP只能收到认证控制提示，不能读取通知或产生业务写入。SQL值绑定，票据只存哈希，事件及ready字段白名单无正文/引用/邮箱/IP/UA/key，无用户URL获取，XSS/注入/SSRF没有本轮新增内容或URL解析面；身份/权限/消息预算及队列限制已有代码与适配验证。实际风控/代理头/CSRF/两账号/撤销/注入/滥用/信息泄露仍待完整运行。无新增前端产物/公网静态/CDN URL/sourceMappingURL/目录/上传下载路径，未运行生产CDN或目录配置扫描；无需yarn。

人工端到端验收（必须先应用socket_ticket建表，并通过IDEA重编译/重启后确认）：

1. A/B两账号登录；A用notifications.read令牌POST取票→按响应path连接无query的WS→等待authentication.required再发authenticate→收到ready后GET本人未读数/列表。B在A的文章回复，或对A触发已接入共享提醒；A收到空notifications.changed后HTTP重读，B及未认证连接不接收A提示。A将本人通知标已读/删除，提交成功后应收到提示，HTTP状态正确。
2. ping/pong、同票据第二连接/重放、票据过期、无首帧、非法/重复字段/超大或未知消息、缺scope/旧Cookie或apiKey、parent/user/client停用或撤销均按契约拒绝/关闭。刷新access后旧连接仍受原expiresAt限制，新access取新票重连；受限连接关闭后名额应释放，慢认证不能绕过截止时间，慢客户端缓冲达到限额应断开。
3. 注入票据插入/消费/提交前失败，哈希/消费标记整体回滚；恢复后仅一次消费。造一天以前到期记录，后台分批清理且新记录保留，删除失败回滚。通知事务回滚不能发布新提示；重放可重复提示但不得重复业务；发送中再提交变更不应被合并marker遗漏。两账号、多连接、真实MySQL/多实例及断网恢复单独压测，已知多进程事件桥仍未实现。
4. 新IP匿名取票拒绝，黑名单HTTP仍/test且WS明确关闭，验证码解封后正常鉴权；开发关风控仍要求scope/票据。校验原始query覆盖ip不能绕过，代理XFF信任与路径部署需真实配置验证；WS13帧及分片限制、TCP升级竞态和背压应真实测试。
5. PC/移动旧通知、在线时长、文章、聊天室、私聊连接回归，原字段和协议保持。本通道未接入在线时长、文章/聊天室/私聊消息，不以通知连接替代这些未完成能力。smoke仅在重启确认后执行；需要真实触发通知的完整步骤见以上，脚本本身不修改通知。

全量v3目标继续，旧通知提交后发布覆盖、文章/聊天室Socket、认证/PKCE/其他设置/支付及完整运行验收仍未完成；鱼游OAuth入口及旧编辑歧义兼容仍待用户。API.md需同步网站API文章。

### 2026-10-18 严格提交、通知标记与后台资源

前节通知通道属于实际进展。本轮复核当前Latke jar的JdbcTransaction，确认commit会捕获提交异常、记录日志并dispose后正常返回，不能将“事务已结束”当成持久化证明。新增ApiV3Transactions.strict，在实际JdbcTransaction连接上执行可传播异常的commit，成功才沿用原dispose，失败保留给调用方回滚；非JDBC事务保持原对象。ApiV3RequestRepository、ApiV3TokenRepository、ApiV3SocketTicketRepository三个v3根事务统一使用它。没有替换framework jar，也没有全局改变旧@Transactional代理。正常成功字段不变；提交异常不再被这些v3根事务当成成功。确认丢失时数据库可能已经提交，客户端必须保持同一幂等键/请求重试，不能把所有服务端错误描述成“数据库一定未发生变化”。

通知不再靠NotificationMgmtService当前是否有事务判断何时转发。新增api_v3_notification_revision，oId为owner，revision为随机nonce，updatedAt仅审计、不参与排序或游标。NotificationRepository的新增、更新、按ID删除及已知owner批量删除，在原通知事务中更新marker；更新只对实际应用的owner字段额外标记新owner，正常只读状态更新不转移归属。NotificationMgmtService按类型批量删除及UserMgmt现有removeByUserId走owner-aware方法，通用remove(Query)行为保留但不自动追踪owner，后续调用不能绕过这些方法。没有从原消息JSON提取公开内容。新增表的SQL和repository.json已同步，模型/Repository名称不带symphony_，未执行迁移。

ApiV3RealtimeService按当前连接owner批量读取已提交marker，各实例保持独立seen状态，因此共享数据库的其他生产者或后端写入也能被读取，而无需把旧UserChannel全量命令暴露到v3。首次订阅存在marker会补提示，空marker建立基线；同一marker不重复提示，变化后重新授权并仅发空notifications.changed。事务内snapshot被拒绝；不依赖通知条数、最大ID、updatedAt或提交前生成ID，避免等量替换及提交顺序遗漏。移除上一节“无活动事务直接转发”的入口，旧UserChannel原有字段与调度保持，旧注解事务内部早发不再是v3的事件来源。真实多实例网络/复制/负载均衡仍未验收；仅共享主库读写的机制与隔离读取已经测试。

取票先检查marker读取可用，不可用503且不签票；运行中同步失败会关闭连接并记录不含凭据的警告，防止长期ready但无法同步。lazy registry已有实例走无锁快路径，避免慢poll持有服务monitor时阻塞新连接登记。当前后台worker、定时tick、cleanup和Socket消息回调没有HTTP收尾，新增显式JDBC线程资源释放：遗留活动写事务先回滚，再释放框架TX/CONN，避免每个消息回调或worker占用读连接；不在这些收尾中提交业务。源码复核Connections借出读连接设autoCommit=true，普通批量读marker不会读取未提交写入。真实池超时/驱动关闭失败/压力仍需运行验证。

实际修改文件：

- `src/main/java/org/b3log/symphony/util/ApiV3Transactions.java`：严格提交及Socket/后台资源收尾。
- `src/main/java/org/b3log/symphony/repository/ApiV3RequestRepository.java`、`ApiV3TokenRepository.java`、`ApiV3SocketTicketRepository.java`：v3根事务接入。
- `src/main/java/org/b3log/symphony/model/ApiV3NotificationRevision.java`、`repository/ApiV3NotificationRevisionRepository.java`：marker模型、同事务UPSERT及有界owner快照。
- `src/main/java/org/b3log/symphony/repository/NotificationRepository.java`：真实写入钩子、当前owner读取、owner-aware批量删除及marker；protected数据原语保留底层行为并支持故障适配测试。
- `src/main/java/org/b3log/symphony/service/NotificationMgmtService.java`：owner-aware按类型删除、撤销直接v3转发；`service/ApiV3RealtimeService.java`：已提交marker同步、服务异常关闭及worker收尾。
- `src/main/java/org/b3log/symphony/util/ApiV3RealtimeSessions.java`：owners快照和closeAll；`processor/channel/ApiV3RealtimeChannel.java`：消息回调收尾；`processor/ApiV3RealtimeProcessor.java`：可用性检查与503。
- `src/main/resources/repository.json`、`sql/20261016_api_v3.sql`：新表与元数据。
- `src/test/java/org/b3log/symphony/service/ApiV3CommitFailureTest.java`、`repository/ApiV3NotificationRevisionTest.java`：真实SDK/JDBC故障及实际通知钩子/marker耦合验证；`util/ApiV3RealtimeChannelTest.java`、`ApiV3RealtimeProcessorTest.java`：同步去重/归属/不可用关闭及503测试。
- `tools/api-v3-write-test.mjs`、`api-v3-socket-test.mjs`、`api-v3-notification-test.mjs`、`api-v3-checkin-test.mjs`、`api-v3-comment-removal-test.mjs`、`api-v3-interaction-removal-test.mjs`：新源码依赖及新增专项。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、本文、`docs/api-route-inventory.md`：重试契约、503、时间说明对齐实际毫秒字段、架构边界及最新源码行。没有更改实际时间字段或新增路由。

测试证据：写协议/草稿/幂等专项原21组+真实SDK提交/资源6组通过；通知原9组+marker6组通过；Socket22组通过；评论/文章互动及相关规则66组、签到/改名21组、删除引用15组回归通过。新增SDK测试只跳过会连接应用连接池的构造器，注入真实临时SQLite连接；commit/rollback/dispose与ThreadLocal为当前jar原实现。分别验证提交前拒绝和实际提交后注入确认丢失：前者钱包/receipt/marker都回滚，后者三者持久化且同键重放不重复扣款或更新marker；资源收尾回滚未完成事务并关闭框架读连接。脚本故意输出一次框架错误日志重现旧行为，最终以退出码和PASS为准。不是实际MySQL网络故障，也不是完整框架IoC启动。

通知新测试执行实际NotificationRepository写钩子与marker UPSERT，数据原语用SQLite持久化适配，UPSERT仅将MySQL方言换成ON CONFLICT，FOR UPDATE剥离。独立读连接在未提交时看不到marker/通知变更，回滚保持原marker；新增/已读/删除/批量/字段归属/写入和标记失败、相同条数替换、时间回退、205 owner批次和SQL注入值隔离均通过。调用方正确回滚的失败原子性已测；遗留吞掉RepositoryException后自行继续提交的业务没有全局重构，不能据此宣称全部旧入口的异常语义已修复。常规通知来源已走标记，直接SQL或通用Query删除若绕过owner-aware路径仍不产生标记。其他提交后外部副作用（索引/消息等）在不确定提交后的完整恢复也未由这组测试证明。

安全与反漏扫：没有新增路由或anonymous.viewSkips；Bearer/票据/owner与现有黑名单、匿名首访/计次、/test和validateCaptcha链路未改。marker只保存owner/nonce/time，batch查询仅来自认证连接owner，不出现在公开DTO；事件仍空data且逐连接当前授权。UPSERT与所有读写均绑定值、写入复用外层事务，无URL获取、HTML渲染或新增上传下载路径。新增repository.json内容属于部署资源，访问控制仍需实际检查；无新增前端静态/CDN URL/sourceMappingURL/目录资源，未跑生产CDN或目录枚举扫描。实际鉴权/越权/CSRF/XSS/注入/SSRF/滥用/敏感信息以及代理头/风控和池压力仍待端到端验证，不能用源码或适配器替代。

人工全链路（先应用新增revision及此前socket_ticket表SQL，再IDEA重编译含repository.json资源并重启，告知确认后联调）：

1. PC/移动旧入口或v3登录A/B；A取票连接认证并HTTP读取本人通知。B向A触发新评论/关注/感谢，A收到空提示后HTTP重读；A标已读、按类型删除/删除全部再重读，字段和条数正确，B不接收A的提示。对相同条数的通知替换也应收到提示；重连首次主动HTTP同步，新marker可补提示，同marker不反复发送。
2. 调试器在同一通知事务里写通知和marker后暂停：其他连接/实例不能看到新marker；回滚无新提示，提交后各自观察器可读并提示。验证两个真实后端共享主库、并发批量/单条写、死锁/锁等待及未知提交结果；高负载时旧异常吞错路径单独检查，不能据本轮串行SQLite结论直接通过。
3. 注入v3根提交拒绝应返回错误，业务/幂等/marker无部分提交，恢复后原键同内容仅执行一次。模拟真实提交后确认丢失，先查持久化记录，再同键重试，不得二次扣款/消费；marker可由独立读取者观察到真实提交。Token/Socket取票失败不得返回凭据，失败恢复按各自契约重取。
4. 删除或拒绝访问marker源时取票503，已有连接关闭；恢复后重新取票连接。反复ping/重连/后台poll/过期票据清理，检查连接池借出数会归还，异常任务不遗留TX/CONN，慢poll不阻塞既有registry的新连接登记。无Bearer、缺scope、其他账号、过期/撤销、query凭据、黑名单及验证码解封继续回归。
5. 旧PC/移动通知、在线时间、文章和聊天室连接字段/协议回归；本轮未迁移这些旧通道或全局替换注解事务。新API的MySQL/IoC/TCP/浏览器验收仍未执行；未执行Maven，未升级数据库或同步target/classes。

全量v3目标保持进行中：文章/聊天室Socket、账号与PKCE、其他设置/支付及整体运行验收未完成；鱼游OAuth规则入口和旧编辑歧义兼容仍待用户。API.md内容需同步网站API文章。


### 2026-10-18：旧改名卡严格提交补齐

用户已确认新旧改名卡一起原子化，现有 SettingsProcessor 两个旧入口及 v3 已接同一 UserNameMgmtService 事务体。本轮补齐旧 useCardLegacy 根事务的 ApiV3Transactions.strict 包装，避免当前 Latke JdbcTransaction.commit 吞掉 SQLException 后误返回成功；v3 根事务此前已使用严格提交，本轮不改字段、规则、扣卡数量或鉴权方式。提交拒绝时回滚改名与背包，再重读缓存；真实提交后确认丢失仍返回不确定结果，不能宣称所有失败都没扣卡。旧客户端须先查资料和背包，v3 则原键原请求重试。

实际修改：service/UserNameMgmtService.java；service/UserNameMgmtServiceTest.java；API.md、UPDATE_LOG.md、AGENTS.md 和本计划。测试新增实际 bundled JdbcTransaction 的提交拒绝及提交后确认丢失路径，跳过连接池构造器并注入 SQLite 连接；真实 SDK commit/rollback/dispose 保留，只适配 close 以便同一临时数据库继续断言。断言数据库与卡片、缓存、框架 ThreadLocal 及恢复后重试；不能替代 MySQL 网络/锁和完整 IoC 验证。api-v3-checkin-test.mjs 共23组通过，包含签到/补签/改名已有规则与本轮两组 SDK 故障回归；api-v3-write-test.mjs 的27组写协议/草稿/幂等及真实 SDK 故障回归通过。node --check 与 git diff --check 通过。

安全及反漏扫源码复核：旧页面仍 loginCheck+CSRF，旧 API 仍 loginCheck 兼容 query/body apiKey；v3 同时要求 activity.write/settings.write、Bearer 和写预算，不从页面用户补身份。目标仍只来自认证身份，用户名规则/绑定 SQL/DTO 白名单和名称空间锁不变。没有新增路由、anonymous.viewSkips、HTML/URL 抓取或上传下载路径，也没有更改 BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor 的黑名单/验证码链路。无产品前端、CDN URL、sourceMappingURL 或目录资源变化，无需 yarn。未鉴权/越权/CSRF/XSS/注入/SSRF/批量滥用/敏感信息的运行结论，以及匿名首访、黑名单、验证码解封、静态资源/目录防漏扫，仍须完整运行验证，不能视为本轮已通过。

人工全链路（先通过 IDEA 重新编译或重启并确认）：

1. 准备有两张改名卡的测试账号，PC/移动设置页使用改名卡，再刷新资料和背包，验证改名成功、仅扣一张、旧用户名查询不再命中。旧 /api/bag/nameCard 分别使用 query/body apiKey，核对当前认证账号被修改，成功 code/msg/data 保持。
2. v3 使用同时含 activity.write/settings.write 的 Bearer 和 Idempotency-Key 改名；同键同体重复返回原结果且不再扣卡，同键不同体409；随后再次改名并重放旧请求，当前资料仍是新名。用无卡、重复/当前名称、非法字符/超长和缺任一 scope 测试，验证失败不改变名称或背包；其他账号及伪造 userId 不能修改目标。
3. 在测试数据库注入用户更新、背包更新和提交前失败，验证两者回滚、无成功响应、缓存是已保存状态。模拟数据库已提交但确认丢失，旧入口显示结果暂未确认，刷新可见新名和一次扣卡；原名称重试不再次扣卡。v3 同键重试必须重放持久化结果，不能用新键掩盖不确定状态。
4. 未登录/仅 Cookie 或 apiKey 调 v3、缺 scope、页面缺 CSRF、黑名单跳转 /test、验证码解封与匿名首访继续回归；开发关闭风控也不能绕过鉴权。实际 MySQL 多连接同名竞争、注册与改名并发、死锁和网络断连另验。

未执行 Maven、浏览器、HTTP smoke、数据库迁移或 target/classes 同步。全量 /api/v3 目标仍进行中，文章/聊天室实时、认证/PKCE、其他设置/支付及整体验收继续保留；鱼游投稿仍等待用户提供 OAuth 校验规则/入口。API.md 仍需同步网站 API 文档文章。


### 2026-10-18：文章实时授权与订阅绑定核心

上一轮改名卡修复为实际进展；本轮继续全量目标的文章 Socket 前置能力。ApiV3SocketTicket 新增 channel/targetId，通知默认空 target，文章绑定单篇 ID；issueArticle 只在服务层提供，按 content.read 检查当前 parent，原 issue 仍只接受 notifications.read。consume 从持久化记录恢复绑定，未知频道/错误目标拒绝；Grant 保留旧三参数通知构造器。签发、消费及后续授权均检查文章权限；票据仍一次消费、哈希持久化、严格提交，连接受原 access 到期限制。

新增 ApiV3RealtimeTargetService 的单次参数化联表查询，直接读取当前文章、作者状态与读者名称/角色，不走 ArticleCache/UserCache 或 Sessions/apiKey。正文只对讨论帖读取并仅用于邀请解析，不对外传输。有效读者、作者封禁/停用、文章失效及讨论帖作者/admin/邀请规则与公开读取共用纯可见性策略。邀请词法从原 UserQueryService 提取 UserMentionCandidates；旧 getUserNames 继续逐候选验证用户存在，新检查则已由数据库确认当前读者存在，用当前用户名匹配，避免旧缓存延续改名前邀请或撤销的角色。

ApiV3RealtimeSessions 增加文章索引和 articleChanged 的仅 ID 提示；ready 依实际 Grant 返回频道，文章额外带 articleId。通知发布与 owners 快照只选择通知频道，避免 content.read 文章连接收到 notifications.changed 或触发通知源读取。按用户预算仍跨频道共享，移除连接时同步释放 owner/文章索引；每次发送重新授权，邀请丢失或查询失败先关闭再终止发送。这里只是内部发送原语，不是提交事件来源。

**未新增公开文章取票路由，尚未接入文章/评论/reaction 的已提交变更标记与后台轮询，因此公开文章实时订阅仍不可用。** 原通知 POST 与 WS 的请求/响应字段保持，不在 API.md/UPDATE_LOG 发布内部占位协议。下一步必须补齐真实写入事务的标记、消费源与可用性检查，再接文章取票入口和公开文档；不能用直接调用 articleChanged 或仅有 ready 证明业务事件闭环完成。热度/在线人数、聊天室实时和私聊仍未完成。

实际文件：

- src/main/java/org/b3log/symphony/model/ApiV3SocketTicket.java；service/ApiV3SocketTicketService.java：频道/目标持久化和分 scope 授权。
- service/ApiV3RealtimeTargetService.java；util/UserMentionCandidates.java；service/UserQueryService.java：当前权限查询与共用原邀请词法。
- util/ApiV3RealtimeSessions.java：频道/目标隔离、白名单提示与索引释放。
- src/main/resources/repository.json；sql/20261016_api_v3.sql：票据字段；新建表定义及已部署表的 information_schema 判定升级，旧记录默认 notifications/空 target。未执行 SQL，启动这版代码前须升级现有表。
- src/test/java/org/b3log/symphony/service/ApiV3SocketTicketServiceTest.java；ApiV3RealtimeSessionsTest.java；tools/api-v3-socket-test.mjs：真实权限服务/SQL与订阅路径、故障适配及依赖。
- AGENTS.md 与本计划：长期授权约束、范围、迁移和测试证据。

验证：socket 专项29组通过（原22组+票据/权限5组+注册表隔离及权限源失败2组）。实际编译执行新增 target service、共享词法、票据服务、registry 与原通知 facade/channel/controller；文章/用户 Repository 的 select 使用 SQLite 数据适配，FOR UPDATE 剥离，真实词法和可见性未替代。覆盖 content.read 与 notifications.read 隔离、目标持久化、未知频道/注入目标、作者封禁/删除/失效、当前用户名/角色和邀请撤销、权限源异常回滚未消费标记、旧记录默认值、按文章提示与索引清理，原通知生命周期继续通过。6个生产 Java 文件语法解析通过（含未独立类型编译的完整 UserQueryService），node --check、JSON 元数据及 git diff --check 通过。没有 Maven、真实 MySQL 锁/网络、完整 IoC、HTTP/TCP 或浏览器测试。

安全/反漏扫：无新增路由、anonymous.viewSkips 或旧中间件改动；取票身份仍 Bearer，文章核心不得用页面或客户端 userId 代替。目标只接受数字 ID，SQL值绑定、表名只来自固定 Repository，未引入 URL 获取或 HTML 渲染；内部内容/角色/状态不进入 DTO，articleChanged 仅 articleId。未知频道和失败权限查询拒绝，配额仍共享，不将空 data 通知发送给仅 content.read 连接。旧黑名单/test、首访/每5次验证码和 validateCaptcha 未改；无前端静态/CDN/sourceMappingURL/目录/上传下载变化。实际未鉴权/越权/CSRF/XSS/注入/SSRF/批量滥用/敏感信息、防扫描及部署目录访问仍需运行验收，不以隔离单元测试代替。

人工链路（先应用票据表两列升级，再 IDEA 重编译含 repository.json 并重启、确认后联调）：

1. A 通过原 POST /api/v3/realtime/tickets 取通知票，按 path 连接、challenge/authenticate/ready；核对 channel=notifications 且 ready 无 articleId，触发本人通知后仍收到空 notifications.changed，HTTP 重读正确。B 不接收 A 提示，票据重放/过期/撤销仍拒绝。
2. 数据库核对新通知票 channel=notifications、targetId 为空；升级前已有通知票保持默认绑定，仍须原 notifications.read。重复执行升级脚本不再添加列，生产 MySQL/自定义前缀部署需人工复核，本轮没有执行脚本。
3. 文章核心用隔离专项验证签发/消费/发送，不把它当成可调用 HTTP 接口。完整文章链路须等下一批公开入口和已提交来源接通后，再用 A/B 读者、讨论邀请、改名、角色撤销、私密/匿名评论及断网重连验收；当前无法完成这一端到端操作。
4. 回归旧讨论文章页的 @邀请（空格/换行/HTML 分隔/尾部用户名）与评论 @提醒，确认候选解析提取保持既有行为；PC/移动各走入口。黑名单 /test、验证码解封、匿名首访、Cookie/apiKey 不可替代 v3 Bearer 和缺 scope 继续回归。

目标保持 active，文章事件源及公开入口、聊天室/其他实时、账号/PKCE、其他设置/支付及全量运行验收继续；鱼游 OAuth 规则/入口和旧编辑歧义兼容仍待用户。现有 API.md 内容仍需同步网站 API 文章。


### 2026-10-18：文章已提交变更来源与公开取票入口

上一节文章授权/绑定核心是实际进展。本轮新增 POST /api/v3/articles/{articleId}/realtime-ticket（content.read+写预算），目标只取路径数字 ID，读者/parent 只取 Bearer IDENTITY，body 仅空 JSON。先检查当前文章权限及 marker 源可用性，再签发既有一次票据；权限/marker 查询异常返回503 realtime_unavailable且不签票，目标不可读404。响应 ticket/expiresIn/path/channel=article/articleId，连接复用既有无 query 首帧认证 WS。原通知入口不接受新 body 字段、原 ready 字段保持。

新增 api_v3_article_revision，oId 为文章，revision 随机 nonce，updatedAt 仅审计；SQL建表与repository.json同步，未执行迁移。抽取 ApiV3RevisionRepository 供通知和文章各自 Repository 共用同事务 UPSERT/有界已提交快照，通知原行为保持。ArticleRepository 的 add/update/remove 在原事务内标记文章；CommentRepository add/update/remove 标记父文章，更新/删除前当前锁定读取父 ID，不从部分更新对象信任未应用的父字段；内部真实移动同时标记两篇。共享 ReactionMgmtService 在实际 emoji 更改后按已锁目标标记文章或评论父文章，无操作不刷新，chat 不写文章 marker。文章/评论删除的计数、感谢/采纳/赞踩等现有入口经相同 Repository 写入，同事务自动覆盖；直接 SQL 或泛型 Query 批量写若绕过这些方法仍不追踪，今后不能绕开。

ApiV3RealtimeService 对 registry.articleIds 批量读取已提交 marker，各后端独立 seenArticles；首次有标记会提示，空建立基线，变化后逐连接重新授权仅发 article.changed/{articleId}。没有读原始评论/匿名作者或旧 ArticleChannel payload，私密正文不进入事件。失去文章权限或文章删除时关闭，客户端 HTTP 重新确认。同步源失败会关闭现有连接；后台仍复用有界worker和显式JDBC收尾，不在事务内发送事件。marker 与数据共同提交或回滚，不用时间、最大ID或评论数量判断变更；重复提示允许且文档要求初始/重连主动HTTP同步。在线人数、热度与聊天室实时仍未提供。

检查调用链时还发现 ArticleQueryService 的旧缩略图补写在 catch 中未回滚，新增 marker 查询/写入异常也可能遗留该事务；本轮补 finally 回滚未结束事务，不改变正常返回字段。其他旧缓存未延迟的调用及旧注解 commit 语义未全局改造，不能称全部旧接口已有失败原子性；此类旧入口若自行吞掉 RepositoryException 后继续提交，仍须单独整改。v3 事务核心继续严格提交，事件仅观察实际数据库已提交 marker。

实际修改文件：

- src/main/java/org/b3log/symphony/model/ApiV3ArticleRevision.java；repository/ApiV3RevisionRepository.java；repository/ApiV3ArticleRevisionRepository.java；repository/ApiV3NotificationRevisionRepository.java：文章模型、共享 marker SQL 与通知复用。
- repository/ArticleRepository.java；repository/CommentRepository.java；service/ReactionMgmtService.java：实际写入钩子和事务内 marker；protected 数据原语默认仍委托框架，便于隔离 SQL 故障验证。
- service/ArticleQueryService.java：旧缩略图写失败的事务收尾。
- service/ApiV3RealtimeService.java；processor/ApiV3RealtimeProcessor.java：文章 marker 同步、公开取票和503。
- src/main/resources/repository.json；sql/20261016_api_v3.sql：新 marker 表；上节 ticket channel/targetId 升级仍必须一起部署。
- src/test/java/org/b3log/symphony/repository/ApiV3ArticleRevisionTest.java；util/ApiV3RealtimeChannelTest.java；util/ApiV3RealtimeProcessorTest.java：数据/marker、实际通道和入口专项。
- tools/api-v3-article-realtime-test.mjs；api-v3-socket-test.mjs；api-v3-notification-test.mjs；api-v3-write-test.mjs；api-v3-interaction-removal-test.mjs：新实际源码与共享基类依赖；tools/api-v3-realtime-smoke.mjs：可选文章票据/认证/重放检查，未运行。
- API.md；UPDATE_LOG.md；AGENTS.md；docs/api-route-inventory.md；本文：新协议、更新记录和证据。路由现686声明=676 HTTP+10 WS（含禁用项），浏览器引用398。

测试：文章 marker 专项6组通过，实际编译执行 ArticleRepository、CommentRepository 写入钩子及 ReactionMgmtService；数据原语用 SQLite持久化适配，cache与旁支Repository用显式适配，FOR UPDATE剥离，UPSERT仅改SQLite方言。独立writer/两reader验证提交前不可见、回滚保持原marker、生成列过滤、文章和评论增改删、字段白名单父归属/内部移动、每类marker故障回滚、emoji设置/清除/幂等无操作/chat隔离、批量查询和注入值隔离。不是实际完整评论/文章/积分服务全链路或MySQL锁竞争。

Socket专项33组通过（原29+实际文章 facade/Netty传输2+控制器2）。真实channel/facade/controller/输入及scope代码运行，新增文章marker快照和authority为受控适配；文章当前权限SQL已由前节真实target service专项覆盖，不用本节mock授权代替。验证已订阅ID的marker变化/去重、事务内快照拒绝、授权丢失先关闭、marker故障定时关闭、路径ID/Bearer绑定、缺scope/多余body/404/503。Notification marker/SQL15组、写协议/草稿/幂等与真实SDK故障27组、关联删除15组及评论/文章互动与相关规则66组回归通过；66组的实体Repository使用原隔离适配器，新增实际写钩子由本节独立6组覆盖。10个生产Java文件语法解析通过；其中完整ArticleQueryService仅语法检查，真实IoC与缩略图故障待运行。node --check、元数据JSON与git diff --check通过。未执行Maven、SQL升级、target/classes同步或HTTP/browser smoke。

安全与反漏扫：新增HTTP必须Bearer content.read，不从Cookie/apiKey/Sessions补身份，已接写预算；未新增匿名接口/anonymous.viewSkips例外。WS仍显式IP风控/黑名单及一次票据，未认证只能收到认证控制帧。原BeforeRequestHandler黑名单/test、首访/每5次验证码与CaptchaProcessor解封未改。仅认证Bearer写凭据，不增加Cookie CSRF入口；数字路径/空body白名单、绑定SQL、内部固定表名，未新增HTML或URL抓取/SSRF面。marker只保存文章ID/随机版本/时间，公开事件仅ID，当前可见性及scope逐次检查；批量查询只来自已认证订阅集合，用户/全局预算仍共享。无产品前端、CDN URL/sourceMappingURL、新目录或上传下载返回路径变化，无需yarn。真实未鉴权/越权/CSRF/XSS/注入/SSRF/滥用/敏感信息、代理/验证码、静态资源/目录防漏扫与池压力仍待实际验收，不能按源码或隔离测试默认通过。

人工完整链路（先应用新 article_revision 建表及上一节 ticket 两列升级，再 IDEA 重编译包括repository.json并重启，明确确认后才能联调）：

1. PC/移动登录A/B。A用content.read Bearer对可读文章POST取票，按path连接、等待authentication.required后authenticate，ready应channel=article和对应articleId。初始GET文章、评论、reaction。B通过旧页面或v3发布/编辑评论、改变文章或评论emoji、赞踩/感谢/采纳，A收到仅ID的article.changed后HTTP重读，核对正文/计数/选中态。另一篇文章连接和通知频道不接收该文章提示，通知自身协议回归。私密/匿名评论事件不得含正文或作者，HTTP仍按身份返回脱敏字段。
2. 创建讨论帖邀请A，建立连接后移除邀请、改A用户名或撤销admin角色；下一次事件/授权重检应关闭，HTTP阅读拒绝。删除/失效文章、停用作者、撤销access及到期均检查关闭。不存在或不可读取票404，缺Bearer401、缺content.read403、非法路径/额外字段422；Cookie/apiKey或伪造userId不能补成A。
3. 在同一事务暂停文章/评论/reaction写入及marker：外部连接不得见新marker或提示；回滚后数据与marker都不变，提交后才提示。逐类注入marker/data故障并由外层回滚，核对正文/计数/积分/receipt无部分成功。相同幂等键重放不重写业务；未知提交结果遵循原键重试。并发多连接/真实MySQL死锁、锁等待、两后端共享主库及断连确认丢失另验。
4. 模拟marker或权限源不可用，取票503且不生成凭据，已有连接关闭；恢复后重新取票并初始HTTP读取。重放/超时/URL凭据/重复认证、消息预算、慢连接/背压与断网重连继续回归。旧缩略图补写失败时事务应回滚且连接归还；并发热点文章写入/轮询压力单独测。
5. 新IP匿名/黑名单HTTP仍走原/test与验证码解封，WS明确拒绝；开发关闭风控仍不能省Bearer/票据/scope。检查repository.json等部署资源不可公网枚举，静态/CDN版本与sourceMappingURL继续发布前检查；本轮没有实际访问生产CDN。

全量目标继续active：文章在线人数/热度、聊天室及其他实时、账号/PKCE、其他设置/支付及整体运行验收尚未完成；鱼游OAuth规则入口和旧编辑歧义兼容仍待用户。API.md必须同步网站API文档文章。

### 2026-10-18：聊天室消息列表、详情与上下文

前轮文章已提交变更来源属于实际进展，本轮继续聊天室。旧chat_room.content是用户公开快照/正文的JSON外壳，红包更新可能把内层content从String改成JSONObject；旧getMessages/getContext依赖页面/apiKey和可变原始JSON，不能直接用于v3。新增GET /api/v3/chat/messages、/{messageId}、/{messageId}/context，均要求Bearer content.read。列表before/after互斥，数字值绑定SQL，默认20最大50，size+1判断hasMore，返回升序及双向游标；上下文先确认中心，再前后各取radius+1，默认10最大25，至多51条且中心一次。撤回/不存在404，DB故障传播500，不伪装空成功列表。

ApiV3ChatMessageDto白名单返回id/createdAt/type/content/contentHtml/author/client/card。普通消息保留原Markdown，HTML复用Markdowns.cleanChat；红包仅类型/留言/金额/份数摘要，天气为date/weatherCode/max/min/t/st，音乐为title/source/coverURL/from，URL用safeUrl并支持数字音乐ID。卡片文本按纯文本处理，card不返回原始JSON，红包who/recivers及内部字段不输出。作者是发布时公开快照，机器人可无ID；不裸传User、sysMetal、联系方式或凭据。内层String/JSONObject都兼容；坏、重复、过深或超长外壳返回unavailable空占位并保留ID，避免游标停滞。未改变旧保存规则或旧接口。

**发送/撤回、红包发放领取详情及事务、话题/弹幕、在线信息、实时节点协议仍未接入。** 本轮读取不替代完整聊天室迁移；后续写入必须核对ChatRoomBot命令/禁言/额度、积分/职业来源和外部节点，禁止直接套Sessions旧控制器。

实际文件：src/main/java/org/b3log/symphony/processor/ApiV3ChatProcessor.java、processor/ApiV3Processor.java；service/ApiV3ChatQueryService.java；util/ApiV3ChatMessageDto.java；src/test/java/org/b3log/symphony/service/ApiV3ChatQueryServiceTest.java；tools/api-v3-chat-test.mjs、api-v3-chat-smoke.mjs；API.md、UPDATE_LOG.md、AGENTS.md、docs/api-route-inventory.md及本计划（含顶部矩阵修正）。路由689声明=679 HTTP+10 WS（含禁用项），浏览器引用398。无新表、数据库写入或产品前端变更。

测试8组通过：真实新控制器/服务/DTO、既有AuthMidware/Requests/Json/safeUrl及ChatRoomRepository查询调用。SQLite执行绑定SQL；Token认证和最终响应封装适配，hasScope执行抽取的生产方法，RequestContext真实。Markdown/emoji转换透传适配；cleanChat和inputSafelistChat逐字从生产源提取并以实际Jsoup执行，因此覆盖清洗函数，不证明完整Lute/Flexmark/emoji/网络渲染。覆盖游标切片/新消息下稳定旧页、增量方向、上下文/撤回404、注入/冲突/上限先拒绝、DB故障、HTML危险内容删除、外壳/作者/卡片白名单、对象/字符串红包、坏记录占位、50/51条上限、深嵌套、机器人及401/403/422/404/500链路。故意注入DB错误会打印日志，以退出码/PASS为准。4个生产Java文件语法解析、node --check及git diff --check通过。

安全/反漏扫源码复核：新路由要求Bearer content.read，Cookie/apiKey/旧User.USER不能补身份，未新增anonymous.viewSkips；历史为认证用户的公共聊天室，不等于专属红包领取授权。GET无业务写入或CSRF副作用，游标/上限限制且SQL绑定，无客户端URL服务端获取/SSRF，HTML复用清洗且DTO投影。BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware首访/计数及CaptchaProcessor解封未改。无新增产品JS/CSS/FTL、静态CDN/sourceMappingURL/目录/上传下载路径，无需yarn。运行中的未鉴权/越权/CSRF/XSS/注入/SSRF/滥用/泄露、防扫描、部署目录/CDN与渲染CPU压力仍未验证，不能按源码默认通过。

人工链路（本轮无需SQL；此前Socket/marker迁移仍须部署。先IDEA重编译或重启并确认）：

1. PC/移动旧聊天室发布普通Markdown、@提及、图片/链接、天气/音乐及测试红包。A用content.read Bearer GET列表，核对升序、公开快照、普通源文本/清洗HTML和卡片白名单；按ID读详情和现有GET /api/v3/chat/messages/{id}/reactions。列表不聚合reaction，读红包摘要不扣积分或领取。
2. nextBefore连续翻旧消息，nextAfter增量取新消息；期间另发新消息，旧方向不偏移。取中间/边缘context，核对中心一次、半径和hasMore。撤回测试消息后详情/中心404，列表继续翻；坏历史为unavailable占位。
3. 无Bearer或仅Cookie/apiKey为401，缺scope403；冲突/注入游标、size=0/51、radius=26或非法ID422。两个合法账号均可读公共记录，伪造userId不能更换身份。模拟DB故障500，恢复后正常，不当成历史清空。
4. 测试script/onerror/javascript正文，content只作源文本，contentHtml不执行，卡片文本按text展示；PC/移动均回归。黑名单/test、验证码解封、匿名首访/计数、开发风控开关及部署静态目录继续运行检查。

未执行Maven、SQL、target/classes同步、浏览器或HTTP smoke；MySQL/IoC/真实Markdown、emoji、网络响应与风控仍待验收。全量目标继续active，聊天室剩余读写/红包/Socket、文章在线/热度、账号/PKCE、其他设置/支付及整体验收未完成；鱼游OAuth规则入口和旧编辑歧义兼容仍待用户。API.md需同步网站文档文章。

### 2026-10-18：聊天室消息与已提交事件事务基础

前轮聊天室读取是实际进展。旧普通/机器人/红包/天气/音乐均经ChatRoomRepository写消息，领取更新、撤回删除；部分旧入口捕获RepositoryException后仍commit，不能只在写后添加事件并假设调用者回滚。本轮新增api_v3_chat_state/main种子及api_v3_chat_event，房间head行锁串行分配正数revision并持有到外层提交，事件仅消息ID/kind/时间，无正文或用户对象。查询绑定revision上下界、按数字revision排序，单批最多200；清理根事务严格提交并保留head，未来消费者须识别过期缺口。SQL和repository.json同步，未执行。

ChatRoomRepository的add/update/remove经ApiV3ChatTransaction在现有JDBC事务内创建保存点，不独立begin/commit。消息和事件全部成功才释放；无目标更新/删除不分配revision。数据/事件/head任一失败回滚到保存点再重抛，即使旧调用方吞异常后commit也不能保存半次操作，此前成功写入仍保留。保存点无法撤销或释放结果不确定时整笔rollback/dispose，防止再提交部分操作。正常旧字段、收费及广播入口未改，旧红包扣款/消息/领取流程没有整体原子化。

**尚未新增聊天室取票、事件消费/投递、chat reaction来源或定时清理调度，不能宣称v3聊天室Socket可用。** 这里只提供持久化来源和有界读取/清理原语；下一步接频道授权、初始HTTP同步、过期缺口提示、逐接收者授权和清理调度，非持久化话题/弹幕/在线另核对节点协议。本轮无公开接口契约变更，API.md/UPDATE_LOG不发布内部占位协议。

实际文件：

- src/main/java/org/b3log/symphony/model/ApiV3ChatEvent.java；repository/ApiV3ChatStateRepository.java；repository/ApiV3ChatEventRepository.java：模型、head锁、事务内事件及读取/清理。
- repository/ChatRoomRepository.java；util/ApiV3ChatTransaction.java：真实写钩子及保存点/失败回滚。
- src/main/resources/repository.json；sql/20261016_api_v3.sql：两张表与main种子，模型/Repository无symphony_前缀，重复迁移不能重置head。
- src/test/java/org/b3log/symphony/repository/ApiV3ChatEventRepositoryTest.java；tools/api-v3-chat-event-test.mjs：SQL/JDBC保存点/真实框架故障与独立观察者验证。
- tools/api-v3-chat-test.mjs：实际Repository依赖；AGENTS.md与本计划：关键链路和迁移边界。

验证：事件专项8组、聊天室读取8组通过。实际编译执行新增Repository、消息写钩子、保存点工具与严格提交；仅数据原语用SQLite适配，FOR UPDATE剥离。默认currentConnection从真实框架TX取连接，跳过连接池构造器并注入临时SQLite，当前jar的commit/rollback/dispose真实运行。proxy注入数据/事件执行前后、head前后、保存点创建/释放确认丢失/回滚、清理和根提交故障；两个独立读者验证未提交不可见，失败后仍commit无半次消息，同事务先前/后续成功写入保留且失败revision重用，局部撤销失败整笔回滚并清ThreadLocal，数值9到10排序、范围/清理缺口、缺种子/非法目标/事务边界及序列耗尽拒绝。5个生产Java语法解析、node --check、元数据JSON和git diff --check通过。不是MySQL并发锁或真实网络故障证明；语句确认丢失在仍可用连接上模拟，不能泛化到所有驱动状态。

安全/反漏扫：无新增路由、anonymous.viewSkips、凭据输入、HTML/URL抓取、上传下载或静态/CDN/sourceMappingURL资源。旧鉴权/CSRF及BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware首访/计数、CaptchaProcessor解封不变；旧JSON没有转发到新连接。SQL绑定，固定main和kind枚举，事件无正文/联系方式/IP/UA/key，读取与清理有界。运行中的未鉴权/越权/CSRF/XSS/注入/SSRF/滥用/泄露、防扫描及部署目录/CDN仍待验收；repository.json不能默认公网可读。head是新增写热点，MySQL吞吐/锁顺序/死锁及多实例必须另验。

人工链路（先应用chat_state/chat_event建表及main种子，再IDEA重编译含repository.json并重启，明确确认后联调）：

1. PC/移动旧聊天室发送普通、天气/音乐/测试红包及机器人消息，核对消息和created事件一起提交，更新红包记录有updated、撤回有deleted，缺失目标不新增事件。旧正常成功字段保持；红包扣款/领取仍需原业务回归。
2. 暂停于写入后/提交前，独立连接不能见消息/事件/head；提交后均可见、回滚均不变。两个事务/实例写不同消息，验证head锁及提交顺序，不能用消息ID判断；同一事务第二条失败后继续写第三条，只有成功操作和连续revision留下。
3. 注入消息、事件、head、保存点故障，调用方catch后commit也无半次消息；局部撤销失败时整笔回滚、此前同事务操作也撤销、连接归还。真实断连及未知提交结果另验，未来v3仍须幂等和严格根提交。
4. 测试过期清理批次/失败回滚，head不倒退，新事件不复用已提交序号；重复迁移保留head，缺种子必须失败。消费者/定时器尚未接线，当前无法验证WS投递或缺口恢复，不能以查表代替。

未执行Maven、SQL、target/classes同步、HTTP、浏览器或生产扫描。全量目标继续active，聊天室消费/取票/reaction/清理调度及其他写入、红包事务、账号/PKCE、文章在线/热度、其他设置/支付和整体验收未完成；鱼游OAuth规则入口与旧编辑歧义兼容仍待用户。现有API.md内容需同步网站文章。

### 2026-10-18：聊天室消息变更订阅、reaction来源与清理接线

前节事件保存点基础是实际进展，本轮已新增POST /api/v3/chat/realtime-ticket，要求content.read+写预算，body仅空JSON，actor/parent只取Bearer IDENTITY。先验证chat state/head和事件表均可读取，再签发channel=chat、空target的一次票据；缺scope403，body覆盖字段422，源不可用503。消费、心跳和发送继续检查原access/user/client当前状态，不从Cookie/apiKey补身份，不扩大通知或文章票据用途。ready字段保持channel/expiresAt，原通知/文章响应不变。

ApiV3ChatStream为每后端维护提交序列游标。首个订阅基线及head倒退先要求HTTP重新同步；随后按head上界、有界批次消费连续revision，保留未完成游标，源查询/发送失败不越过未送事件。缺口或整段过期发空chat.sync_required并重设基线，不假装可靠重放。空闲时也查询事件表空范围，防head可读但事件表已不可用时长期ready。对负数/乱序/越界/未知kind/非法ID拒绝，数据只交给逐连接当前授权的registry。chat.message.created/updated/deleted/reactions仅messageId和字符串revision，sync_required为空；通知、文章、聊天室索引隔离且用户/IP/总预算仍共享。

ApiV3RealtimeService启动注册时初始化调度，不依赖首个Socket才开始；定时有界消费，chat源故障只关闭chat并清游标，保留健康通知/文章连接。旧事件按固定七天边界每分钟最多5批200条清理，根事务严格提交，head不删/不倒退；cleanup工作共用显式JDBC线程收尾。现有消息写钩子覆盖普通/机器人/卡片、红包状态更新及撤回。共享ReactionMgmtService的chat分支在同一保存点内更改reaction和追加reactions事件，无操作不追加；论坛分支仍用原文章marker，不混入聊天室。没有改旧正常收费/字段或宣称旧红包积分整体原子化。

公开文档已给取票/握手/消息提示/重读HTTP/同步和过期规则；不提供客户端按revision续传。客户端首次/重连要HTTP同步，sync_required清旧缓存及版本判重，再读取当前窗口，撤回清对应缓存。当前只迁移持久化消息变更：聊天室发送/撤回v3写接口、红包操作、在线/话题/弹幕、其他节点事件仍未完成，不能称整个聊天室迁移完成。

实际文件：

- src/main/java/org/b3log/symphony/model/ApiV3SocketTicket.java；service/ApiV3SocketTicketService.java：chat频道、content.read、空目标与原令牌约束。
- util/ApiV3RealtimeSessions.java；util/ApiV3ChatStream.java：频道隔离、白名单帧、授权和有界提交序列消费。
- service/ApiV3RealtimeService.java；processor/ApiV3RealtimeProcessor.java：启动/消费/失败关闭/清理调度与公开取票。
- service/ReactionMgmtService.java：chat reaction与事件保存点。
- src/test/java/org/b3log/symphony/util/ApiV3ChatStreamTest.java；util/ApiV3RealtimeChannelTest.java；util/ApiV3RealtimeProcessorTest.java；service/ApiV3SocketTicketServiceTest.java；service/ApiV3RealtimeSessionsTest.java：纯序列协议、实际通道/控制器及授权测试。
- src/test/java/org/b3log/symphony/repository/ApiV3ChatEventRepositoryTest.java；ApiV3ArticleRevisionTest.java：共享reaction保存点/事件及论坛回归；后者新增真实JDBC holder只用于保存点，root仍由SQLite适配控制，并显式适配chat事件sink。
- tools/api-v3-socket-test.mjs；api-v3-chat-event-test.mjs；api-v3-article-realtime-test.mjs：新增实际源码/适配依赖；tools/api-v3-realtime-smoke.mjs：可选chat取票/重放/ping，未运行，不打印凭据或消息。
- API.md；UPDATE_LOG.md；AGENTS.md；docs/api-route-inventory.md；本计划及顶部矩阵。清单690声明=680 HTTP+10 WS（含禁用项），浏览器引用398。无新增SQL/Repository元数据，本轮依赖前节两张chat表和main种子。

测试：Socket44组通过（原33+stream5+chat票据1+registry1+实际channel/facade3+取票控制器1）；chat事务/事件9组、论坛marker6组、聊天室读取8组回归通过。序列协议验证基线/去重、1201条有界续批、head上界下新增提交、缺口/全部清理/head倒退、source/sink故障游标及坏事件拒绝。实际Latke/Netty channel验证chat握手/版本ID帧/缺口/授权关闭/清理函数与定时源故障，新增事件表空闲不可读时关闭chat但通知仍能ping。SQL来源在Native场景为显式受控adapter，不冒充真实MySQL/TCP；实际事件SQL、默认JDBC连接、保存点及共享ReactionMgmtService由独立SQLite测试执行，涵盖set/clear/toggle、无操作、事件失败后调用者仍commit也无半次reaction。控制器测试运行真实Auth/Requests/scope，认证权威和最终响应封装有适配。启动实际IoC/分钟级真实清理调度、代理/连接池/高负载及完整消息HTTP→事件DB→WS→HTTP网络链路尚未执行。7个生产Java语法、node --check和git diff --check通过。

安全与反漏扫：新POST强制Bearer content.read，无匿名例外/anonymous.viewSkips变化；Cookie CSRF不能补成Bearer，字段白名单禁止用户/房间/频道/parent覆盖。WS仍用原精确无query路径/帧限制/显式黑名单及首帧票据，不依赖HTTP中间件，逐接收者授权；新帧无正文、红包who、作者、IP/UA、联系方式或凭据。SQL值绑定、kind/ID/序列验证、事件批次和后台队列有界；无HTML/URL获取/SSRF或上传下载变化。BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware首访/计次、CaptchaProcessor解封未变；无产品JS/CSS/FTL、静态CDN/sourceMappingURL/目录变化，无需yarn。实际鉴权/越权/CSRF/XSS/注入/SSRF/滥用/泄露、防扫描和部署目录/CDN仍需完整运行检查，不能以适配测试或边界限制默认通过。

人工全链路（先确认前节chat_state/chat_event/main种子及既有ticket列迁移已应用，再IDEA重编译含repository.json并重启，明确确认后才能联调）：

1. A以content.read取chat票→按path无query连接→等待authentication.required后authenticate→ready channel=chat，主动GET最近消息。B在PC/移动发普通/天气/音乐/红包或机器人消息，A收到created后GET详情；领测试红包后updated重读摘要，旧撤回后deleted清缓存并GET404；v3/旧emoji设置/清除后reactions重读反应。帧仅ID/版本，不含正文/作者/领取明细，通知与文章连接不接收chat帧。
2. 初始、断线重连及sync_required都清旧缓存/版本判重并HTTP同步。测试过期/中途缺口、head回退、批量积压跨轮消费和发送期间新增提交，不能因时间或消息ID错序漏掉已提交更改。消息/reaction事务暂停或回滚时，不出现未提交帧；真正提交后两个后端各自观察到变更。共享主库、多实例和MySQL锁/吞吐另验。
3. 缺Bearer401、缺content.read403、指定room/user/channel或多余body422；ticket重放/过期、URL凭据、重复认证/非法消息均关闭。撤销access/user/client或原授权到期后关闭，刷新不延长老连接；黑名单HTTP仍/test、WS关闭，验证码解封后重取票。Cookie/apiKey和其他频道票据不能订阅chat。
4. 删除/禁读事件表但保留head，空闲chat连接也应关闭，健康通知仍可ping；新取票503且无凭据。恢复后重新取票同步。等待清理调度检查过期行分批删除、head保留、新事件不删、故障回滚、TX/CONN归还；真实一分钟调度和重启初始化需运行验证。

未执行Maven、SQL、target/classes同步、浏览器/HTTP smoke或生产扫描。全量目标继续active：聊天室其他读写/红包/在线/话题/弹幕、文章在线/热度、账号/PKCE、其他设置/支付及整体运行验收仍未完成；鱼游OAuth规则入口和旧编辑歧义兼容仍待用户。API.md需同步网站API文章。

### 2026-10-18：聊天发送基础校验与客户端标记共用

前轮聊天室订阅已接源码，是实际进展。本轮梳理旧发送：ChatMsgAddValidationMidware先按userLatestCmtTime校验（管理员例外），再保留词处理、trim与4096 UTF-16长度；ChatRoomBot.record还可能执行执法、扣分、禁言、重复确认等，旧muted/risksControlled在记录过期时自行begin/commit，不能直接套进v3幂等事务。

已通过异步问题向用户确认：v3普通发送是否对红包/天气/音乐/话题/弹幕及执法/zf语法返回422、改由独立资源处理，或保留非管理类内嵌语法；尚未收到答案，不能自行选定。依AGENTS业务规则不明确先确认，本轮仅完成不依赖该选择的共用校验和client解析，没有新增POST /api/v3/chat/messages或改变命令行为。

新增ChatMessageValidation纯核心和ChatMessageValidationService显式actor适配。旧middleware调用它，仍保留原Sessions/body apiKey取身份顺序、tooFrequentCmtLabel本地化、仅msg的拒绝JSON与abort；正常正文trim后交后续Bot。MIN_STEP_CHAT_TIME当前确为0，本轮没有偷偷增加限额；参数化核心仍保持现有时间/管理员条件。ChatMessageClients提取旧24种品牌及版本过滤，ChatroomProcessor调用共用方法，超过32截到31的历史行为、斜杠分割和未知品牌省略均保留。新基础校验不会过滤HTML或解释特殊语法；这不代表v3允许这些业务命令。

实际文件：src/main/java/org/b3log/symphony/util/ChatMessageValidation.java、util/ChatMessageClients.java、service/ChatMessageValidationService.java；processor/middleware/validate/ChatMsgAddValidationMidware.java、processor/ChatroomProcessor.java；src/test/java/org/b3log/symphony/util/ChatMessageValidationTest.java；tools/api-v3-chat-validation-test.mjs；AGENTS.md和本计划。未改变公开接口输入/输出契约，无新路由/表/SQL，API.md/UPDATE_LOG不发布未接线的发送接口；此前API.md仍需同步网站文章。

验证6组通过：真实核心/服务/旧middleware和Latke RequestContext/JsonRenderer运行，身份、时钟、保留词存储及配置为明确适配；MIN_STEP常量从实际Symphonys源码读取，解析后的请求JSON显式setJSON模拟已完成的框架前置解析，不冒充HTTP字节解码。覆盖时间边界/未来时间/管理员、频控先于保留词、空白/4096/4097、emoji UTF-16、保留词替换后变长/变短、特殊文本仍透传、service不读Sessions、24品牌/字符/32到33边界、多斜杠、旧msg-only错误及本地化、正常与body apiKey放行。实际保留词数据库/拼音转换、完整ChatroomProcessor、Bot/命令/积分和真实认证未运行；完整Processor仅语法解析，5个生产Java语法、node --check与git diff --check通过。

安全/反漏扫：旧/chat-room/send仍loginCheck+原校验middleware，未改变Cookie/apiKey兼容或加入会破坏旧API的CSRF。基础服务不自行解析身份、不写数据库/发消息/执行命令；client仍仅旧品牌和字符范围。当前未新增v3写入口，因此未绕过Bot的禁言/反滥用来开放发送。BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware首访/计数、CaptchaProcessor解封、anonymous.viewSkips未改；无HTML渲染、URL获取/SSRF、上传下载或前端/CDN/sourceMappingURL/目录变化，无需yarn。实际未鉴权/越权/CSRF/XSS/注入/SSRF/滥用/敏感泄露、防扫描与公开静态目录仍待运行，不能据校验单测默认通过。

人工链路（先IDEA重编译或重启并确认）：

1. PC/移动旧聊天室分别发送有首尾空白、保留词、Markdown/HTML、emoji及各客户端标记的普通消息，确认trim/显示/client与旧行为一致，正常响应字段不变。
2. 空白、超过4096及替换后超限应保留原msg-only提示且不入库/不进Bot；管理员与未来userLatestCmtTime分别验证频控例外及原本地化错误。实际MIN_STEP为0，不能把单测的可设1000毫秒描述成已部署业务限额。
3. 原session和JSON body apiKey分别走真实登录middleware，核对实际作者；无登录/停用/黑名单/test和验证码解封继续回归。旧执法/特殊业务仍走旧Bot，仅由有权限测试账号验证，测试本轮不自动执行收费或管理命令。
4. v3发送入口当前不存在，不能进行发送/幂等验收；需用户确认普通正文的语法策略，再接显式身份、无嵌套事务的禁言/风控、消息/职业/最新发言时间原子写、幂等及提交后效果。

未执行Maven、数据库操作、target/classes同步、浏览器或HTTP smoke。全量目标继续active；本轮新语法问题待用户选择，鱼游OAuth入口和旧编辑歧义兼容仍待补充，其他发送/红包/在线/话题/弹幕、账号/PKCE、文章在线/热度、设置/支付及整体验收继续保留。

### 2026-10-18：聊天室在线名单

新增 GET /api/v3/chat/online-users，content.read Bearer，p/size 默认1/20、size最多50。ApiV3ChatOnlineQueryService合并当前实例旧ChatroomChannel连接ID、已认证v3 chat订阅ID、最近配置节点采样的用户名，分批200参数化查询当前用户的五列字段；先过滤有效状态和在线隐私（沿用本人/管理员例外），按用户名与ID排序，再分页，total只计当前阅读者可见用户。头像仅安全URL，不调用依赖Sessions显示模式的AvatarQueryService。不改变旧online-users返回。

NodeUtil在完成原采样后，通过ChatOnlineSnapshot原子发布不可变用户名列表、采样时间和是否覆盖所有配置节点；失败节点的半份名单不进入新快照。快照不含节点URL/密钥/完整用户。nodesComplete只表示该次采样完整，不代表当前仍在线；初始0/false，当前实例v3订阅计入，其他实例v3连接未共享，文档明确结果不代表全站实时总数。Registry逐连接校验当前授权，去重用户ID，仅chat频道算在线，通知/文章/未认证连接排除；撤销/到期连接清理。

实际文件：util/ChatOnlineSnapshot.java、util/NodeUtil.java、util/ApiV3RealtimeSessions.java；service/ApiV3ChatOnlineQueryService.java、service/ApiV3RealtimeService.java；processor/ApiV3ChatProcessor.java；service/ApiV3ChatOnlineQueryServiceTest.java、service/ApiV3RealtimeSessionsTest.java（均在src/test/java/org/b3log/symphony）；tools/api-v3-chat-test.mjs、tools/api-v3-chat-smoke.mjs；API.md、UPDATE_LOG.md、AGENTS.md、docs/api-route-inventory.md及本计划。路由清单691声明=681 HTTP+10 WS（含禁用项），浏览器引用398。无SQL/新Repository/前端资源变化。

测试：聊天室读取原8组、在线新增4组通过，运行实际服务/查询SQL/DTO/控制器/Auth/Requests与隐私工具，SQLite隔离库；UserRepository是执行实际查询文本的适配，连接来源/最终响应封装/认证权威有显式适配，不冒充应用数据库。覆盖405人跨200批次无截断、排序/分页/越界、SQL注入式用户名绑定、状态/隐私动态变更、本人/管理员例外、样本不可变/不完整/尚无采样、无危险URL/多余字段、真实控制器scope/身份/参数及旧连接ID采集。Socket原44组回归通过，增加chatUserIds断言，覆盖多连接去重、未认证/其他频道排除及撤销后移除。NodeUtil真实网络采样、完整应用IoC和MySQL尚未运行，Java语法检查不等于完整类型编译。

安全/反漏扫：新GET强制Bearer content.read，读者仅IDENTITY，无新增匿名入口/anonymous.viewSkips；Cookie不授予身份，无写入/CSRF面。SQL列封闭枚举和值绑定，名单查询分批，页码乘法边界明确；DTO仅ID/用户名/安全头像，不输出凭据/IP/UA/完整用户/隐藏用户人数。没有HTML、上传下载路径或服务端取任意URL；仅复用既有节点轮询。BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware验证码与CaptchaProcessor解封未改；无产品JS/CSS/FTL、静态CDN/sourceMappingURL/目录变更。实际匿名/黑名单/验证码、CSRF/XSS/注入/SSRF/滥用/敏感泄露和公开部署目录/CDN检查仍需运行验证，不能由隔离测试默认通过。

人工完整链路（先IDEA重新编译或重启并明确确认）：
1. A以content.read调用列表，B分别进入PC/移动旧聊天室及认证v3 chat连接；同一B多端只一行，关闭全部连接后本地名单移除；通知/文章/未认证连接不能增加人数。配置节点名单按nodesSampledAt更新，模拟节点断线检查nodesComplete=false；初始0/false及空页正常。其他后端v3连接不应被误认成全站覆盖。
2. B关闭在线公开，A名单与total同时不含B；B本人和管理员仍可见B，重开公开后恢复。停用/无效用户排除；改名后本地连接按ID读取当前名称；真实节点改名传播需等待下一次采样。节点无效用户名不生成虚拟用户。
3. 使用超过50人的测试名单，默认20、size50、后页及超末页，排序/去重/总数正确；连接变化时允许翻页变动。p0/溢出/size51返回422，缺Bearer401、缺scope403、仅Cookie/apiKey401。撤销v3令牌后其独有在线连接移除，隐私变化立即按资料重查。
4. 数据库不可读应500，不能伪成功空名单；核对no-store与Vary Authorization、无节点地址/管理员密钥/高敏用户字段，危险头像不输出。回归旧在线接口正常字段及PC/移动聊天室；黑名单/test、验证码解封及风控关闭模式同验。tools/api-v3-chat-smoke.mjs已扩展只读在线检查，未执行。

未运行Maven、应用SQL、target/classes同步、HTTP/浏览器或真实节点网络。全量目标仍未完成：聊天发送语法选择、鱼游OAuth规则入口及旧编辑歧义兼容待用户；账号/PKCE、聊天写入/红包/话题/弹幕、文章在线/热度、其他设置/支付与整体运行验收继续保留。新旧改名卡原子化已在此前接线，本轮收到的确认不需要重复改写。API.md需同步网站API文章。

### 2026-10-18：普通消息语法决定与共享保存事务

用户已明确选择v3普通消息只发送正文，对旧红包/音乐/天气/话题/弹幕及执法/zf业务语法返回422，特殊能力另设接口；此前发送语法问题已解决，不得继续作为用户阻塞项。本轮接入该规则的纯校验ChatMessageText，按旧实际触发条件拒绝完整标签包装及管理命令前缀，严格JSON中的redPacket/weather/music卡片也拒绝。包含语法的解释性正文、未闭合标签、无关/无效/深层JSON仍按普通文本处理，不执行命令。最终v3入口须在保留词处理前后调用，防止处理后引入命令；现阶段未开放POST /api/v3/chat/messages，不能宣称发送已上线。

共享保存核心ChatMessageMgmtService已提取并接入旧ChatroomProcessor普通消息分支。saveInCurrentTransaction要求现有事务及作者与消息外壳一致，保存点覆盖ChatRoomRepository消息/变更head/event及真实ProfessionSourceEventCaptureService来源事实；职业写入即使先成功再抛异常，外层捕获后继续commit也不能保留半次发布。saveLegacy用ApiV3Transactions.strict开启根事务并在任意失败后结束事务；旧控制器只在确认提交后给原消息回填ID，正常广播/响应逻辑保持。此核心仅持久化，不代表原Bot处罚、活跃度或后续AI/通知/广播全部原子化。提交结果未知仍不能承诺绝对回滚，旧无幂等接口须先刷新记录再重发。

实际文件：src/main/java/org/b3log/symphony/util/ChatMessageText.java、service/ChatMessageMgmtService.java、processor/ChatroomProcessor.java；src/test/java/org/b3log/symphony/util/ChatMessageValidationTest.java、repository/ChatMessageMgmtServiceTest.java；tools/api-v3-chat-validation-test.mjs、tools/api-v3-chat-event-test.mjs；AGENTS.md、API.md、UPDATE_LOG.md及本计划。没有新增路由、表、Repository或前端/FTL产物；API公开文档只说明已接线旧普通消息保存行为，没有预告未接线v3发送接口。

隔离测试：基础校验由6增至7组，覆盖五种完整标签、首尾空白、执法/zf前缀、严格卡片JSON、普通Markdown/原始HTML/引用/不完整/非法/深层JSON和空白/4096边界。事务事件由原9组增至13组，新4组运行真实共享管理服务、消息/event/head保存点、完整职业捕获服务及来源Repository；连接池/消息ID分配与底层表写入为SQLite适配，真实职业源payload验证TEXT、作者、时间、codepoint长度。覆盖普通调用无事务、调用者状态不被提前修改、他人actor拒绝、所有记录提交前不可见/提交后可见、职业前/后/runtime故障及event/head故障下外层仍commit也无半次写入、显式回滚、嵌套旧root拒绝、严格commit拒绝与资源清理。未测真实MySQL、完整IoC/控制器HTTP/Bot/AI/真实职业执行器。

安全/反漏扫：没有开放v3新写路由，不会绕过尚未提取的禁言/风控/重复确认/复读处罚。旧loginCheck+原校验middleware及Cookie/apiKey兼容保持；保存核心无身份回退、URL请求、HTML渲染或外部消息，正文语法纯检查不扣分/管理。作者绑定、完整事务及故障回滚有隔离证据，正常字段保持。BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware验证码、CaptchaProcessor解封、anonymous.viewSkips未改；无静态/CDN版本号、sourceMappingURL、目录或上传下载路径变化。实际未鉴权/越权/CSRF/XSS/注入/SSRF/滥用/泄露、防扫描及部署静态目录仍待运行，不能默认通过。

人工链路（先IDEA重新编译/重启并明确确认）：
1. PC/移动旧聊天室使用session和body apiKey发送普通Markdown/emoji消息，确认一次响应、正常消息ID/内容/client/广播及职业来源；v3历史和实时订阅可读取对应ID。旧业务命令行为不应被本轮纯工具改变。
2. 一次性测试库模拟消息、event/head及职业写入失败，不能留下消息/事件/来源半次记录或成功推送；真实commit拒绝应失败且连接释放。提交确认丢失场景先刷新记录，不能靠报错推断必然未保存。
3. 回归未登录、停用、无手机、禁言/风控、黑名单/test与验证码解封；这些仍走原链路，不能以新保存测试替代。检查原普通发送记录与旧响应字段；旧红包/天气/音乐不走该普通保存包装，应分别回归，不宣称它们已原子化。
4. v3普通发送当前尚无路由，语法422仅有纯规则测试，待接完整显式Bearer、事务内最新用户/禁言/风控准入、幂等、最新发言时间及提交后活跃度/通知/广播后再验收。

未执行Maven、应用SQL、target/classes同步、浏览器/HTTP/节点或生产扫描。全量目标继续active；下一步必须推进完整发送准入和幂等接线，不能把纯校验或共享保存当成完整发送。鱼游OAuth入口、旧编辑歧义兼容仍待用户；其他账号/PKCE、红包/话题/弹幕、文章在线/热度、设置/支付及整体运行验收仍未完成。API.md需同步网站API文章。

### 2026-10-18：共享聊天准入状态与锁定限制读取

上一轮是实际源码/隔离测试进展。此轮继续普通发送前置链路，已把旧ChatRoomBot.record使用的风险二次确认池和复读限流器提取到ChatMessageAbuseState，并实际替换旧分支。确认池仍按用户+正文匹配、成功确认即消费、换正文覆盖、最多2000项；匹配/移除改为同一同步操作，避免并发两次均判首发。复读仍用实际SimpleCurrentLimiter的15分钟/5次参数、第五次频数提示及超额后重置，继续保留原redpacket任一端标记排除条件。处罚仍由旧Bot原mute/abusePoint执行，末尾才accepted更新latest；allLatestMessage机器人提示用途保留。没有改变旧命令、费用、正常字段或频控参数，没有新开放v3发送路由。

新增ChatMessageRestrictionQueryService事务内查询global mute、本人mute和本人risk，只投影userId/gameId/data，绑定所有值，以LIMIT4 FOR UPDATE获得最多三个独立键；额外记录必然重复，重复/坏时间戳/溢出/查询失败均报错，不依赖CloudService吞异常的空串。要求外层已有事务，不删除到期记录、不begin/commit。ChatMessageRestrictions用显式long到期时间评估，避免旧秒数正负哨兵在剩余1秒/0秒或int溢出时误放行；只用于待接的v3，不改变旧muted/risksControlled返回。保持风控单图片/少于5个UTF-16字符→正文再次确认→禁言顺序，以及global优先、global有效时管理员免全员（包括原个人优先级行为）。边界到期即失效，不以旧负数哨兵代表状态。

实际文件：src/main/java/org/b3log/symphony/util/ChatMessageAbuseState.java、util/ChatMessageRestrictions.java、service/ChatMessageRestrictionQueryService.java、processor/bot/ChatRoomBot.java；src/test/java/org/b3log/symphony/util/ChatMessageAdmissionTest.java；tools/api-v3-chat-admission-test.mjs；AGENTS.md和本计划。没有新增接口契约、SQL/Repository/前端产物；API.md和UPDATE_LOG本轮不宣传未接线功能，已有文档仍需同步网站文章。

测试5组通过：实际共享状态、现有SimpleCurrentLimiter完整源码、限制读取服务及CloudRepository编译并在隔离SQLite执行查询；只移除FOR UPDATE适配SQLite，不证明MySQL锁。覆盖毫秒到期边界/长整型极值、全员/个人优先与管理例外、5字符/单图/确认/禁言顺序、跨用户隔离/变更正文/消费/两并发确认/2000容量、实际旧limiter多轮警告处罚重置及两端redpacket排除、真实查询的global/本人/他人/其他game隔离、事务前置/非法actor/查询失败/损坏/溢出/重复记录拒绝。Bot全类仅语法解析，未声称完整运行其命令、积分、AI或框架IoC；4个生产Java语法与diff检查通过。

安全/反漏扫：旧路由仍loginCheck+原正文middleware，身份路径未改变；共享状态仅接内部已认证用户名/ID及已校验正文，未新增匿名/公网入口。新读取不从Sessions/apiKey推断读者，严格actor格式、值绑定和列白名单，不返回cloud其他存档、凭据/IP/UA。无外部URL获取、HTML渲染/上传下载/新写入/CSRF面，单次SQL与确认池有界。BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware计次验证码、CaptchaProcessor解封、anonymous.viewSkips未改；没有产品JS/CSS/FTL、CDN版本号/sourceMappingURL或目录变更。未鉴权/越权/CSRF/XSS/注入/SSRF/滥用/敏感泄露、防扫描与公开静态部署扫描的实际结果仍待运行，不能据纯政策测试默认通过。

人工完整链路（先IDEA重新编译或重启并明确确认）：
1. 管理员为一次性测试账号设置风控，PC/移动旧聊天室首次发送普通至少5字符正文应要求再次确认，相同正文第二次通过并生成消息/职业来源/实时提示；换正文重新确认，不同账号互不消费。单图片/不足5字符继续拦截；核对旧错误字段/文案不变。
2. 在测试聊天室用受控账号按旧频控验证相同于上条的复读累计、提醒与既有罚分/禁言。普通换文不计复读；redpacket标记规则、旧管理命令/普通消息末尾记录保持。该步骤会触发原处罚，仅用测试环境一次性账号，不自动执行。
3. 分别配置全员、个人、到期及风控状态，旧接口保持原政策；v3纯规则的显式毫秒边界不等于旧sentinel已修复。锁定查询在测试库模拟损坏/重复/不可读，必须报错且不清除限制；确认原记录没有额外写入/提交。MySQL当前读、缺行锁/管理员并发仍待验。
4. 回归session/body apiKey、无登录、停用/无手机、黑名单跳/test、验证码解封和开发关闭风控。v3普通发送目前仍无路由，不能据准入工具宣称HTTP发送/幂等通过；下一步接当前用户校验、一次幂等回调内的限制/复读处罚（积分、禁言及通知）与消息/时间写入，再仅首次确认提交后发活跃度/通知/广播。

未运行Maven、应用数据库/SQL、target/classes同步、真实HTTP/浏览器/节点或生产扫描。全量目标保持active，发送语法已获用户确认不再阻塞；发送剩余事务处罚和提交后接线是当前实现工作。鱼游OAuth规则入口与旧编辑歧义兼容仍待用户，其他账号/PKCE、红包/话题/弹幕、文章在线/热度、设置/支付及整体验收继续保留。

### 2026-10-18：v3普通聊天发送完整接线

新增POST /api/v3/chat/messages，ApiV3ChatProcessor→ApiV3ChatPublicationService，content.write+共享写预算，严格JSON只接content/client，Idempotency-Key必需；content最多4096、client最多128，结果只返id/createdAt。当前作者仅Bearer ID，事务锁当前用户复核有效状态/手机；原始和保留词处理后正文均经ChatMessageText，普通正文不执行红包/天气/音乐/话题/弹幕/执法/zf或自动AI。未知client省略，现有品牌/版本格式保留。

外层先取得actor/sys账户对，ArticlePublicationCaches延后UserRepository与积分primitive缓存，再执行同事务幂等：当前用户、显式限制读、共用风险确认与复读决策、适用处罚、消息/event/head/职业、最后发言时间及请求结果一并提交。成功重放不进入业务回调，即使账号已被禁言也可找回首次结果；其他账号同键是独立命名空间。同一次存储自动重试复用已确认的相同处理后正文和相同名字/正文的复读决策，处理后正文或用户名变化则不能沿用旧决定。首次confirmation_required的请求结果不落幂等表，原键/原体再次发送可确认。

ChatMessagePenaltyMgmtService同事务维护原15分钟禁言、思过簿、适用30积分扣分及通知；余额不足沿用原不能透支的事实，仍禁言而不伪造扣分通知。积分primitive返回空（可能部分写入）必须让整个根事务回滚，转账index与通知刷新只在确认提交后执行。新NotificationMgmtService原语不提交/不刷新。旧Bot处罚链本轮未改原子化，不应把本轮事务声明扩展到旧入口所有收费逻辑。

applied后才accepted更新共享状态并调用ChatroomProcessor的提交后适配：字段白名单、cleanChat清洗HTML、旧广播格式/反应、原活跃度额度和宵禁、去重提及提醒；正文只从保存外壳读取，不重新取会话作者。活跃度与提及通知按旧逻辑在提交后尽力执行，不属于主业务同事务。ChatMessageNoticeService为复读系统提醒独立使用严格根事务保存chat/event，成功后通过同一清洗/提及适配广播，机器人不计用户活跃/职业；结束释放JDBC线程资源。没有直接调用旧Bot异步发送器。广播/提醒失败不能撤销已保存的用户消息，原键重试也不重复外部效果；提交确认丢失时可能漏发这些效果，应由HTTP历史/持久化事件同步恢复，不承诺恰好一次外部投递。

实际文件：src/main/java/org/b3log/symphony/processor/ApiV3ChatProcessor.java、processor/ChatroomProcessor.java、service/ApiV3ChatPublicationService.java、service/ChatMessagePenaltyMgmtService.java、service/ChatMessageNoticeService.java、service/NotificationMgmtService.java；src/test/java/org/b3log/symphony/repository/ApiV3ChatPublicationTest.java；tools/api-v3-chat-publication-test.mjs、tools/api-v3-chat-send-smoke.mjs、tools/api-v3-chat-test.mjs；API.md、UPDATE_LOG.md、AGENTS.md、docs/api-route-inventory.md及本计划。清单692声明=682 HTTP+10 WS（含禁用项），浏览器引用398。无新表/Repository/SQL或前端/FTL产物，仍需此前chat state/event/main种子和v3请求表已迁移。

验证：publication runner共22组通过=原chat事件9+保存事务4+新发送9；聊天室读取/在线12组回归通过。真实新服务、幂等协议与SQL、chat保存点/event/head、完整职业来源捕获、限制SQL、实际限流器、控制器/Auth/Requests/写输入运行于隔离SQLite。UserRepository/积分/最终响应封装/Token权威/勋章为显式适配；新扣分通知原语及addNotificationRecord、提交后Processor方法和cleanChat从真实源码提取运行，整体Processor/Lute/表情转换/提及解析/活跃度发奖/网络传输不是完整应用。积分调用的真实签名与缓存边界已核对，测试用资金适配注入部分转账返回空等故障，不冒充完整MySQL转账执行。

新增覆盖：当前资料胜过伪造Bearer快照、白名单/特殊业务前后处理/手机/状态、跨账号同键隔离、同键冲突/禁言后重放、不重发活跃度/提及/广播/罚单；风险原键二次确认、存储重试无需第三次确认、保留词处理结果变化须重新确认；处罚扣分/禁言/思过簿/通知与消息同事务、穷账号不透支；cloud/部分转账/通知/职业/最后发言时间/幂等结果/commit失败无半次提交或外部成功效果；实际提交成功后注入确认丢失，原键找回一次持久化结果且不推测发送外部效果；系统提醒event/严格commit故障只回滚提醒并清理连接、不影响已提交用户消息；真实清洗与公开字段、广播异常不撤销保存；真实控制器401/403/415/400/422与无Cookie/query apiKey身份回退。6个生产Java语法、工具node语法及diff检查通过，无完整Java构建。

安全与反漏扫逐项：
- 未鉴权/越权：POST强制content.write Bearer，目标作者由IDENTITY ID且事务读当前用户；拒绝body userId/apiKey等多余字段，成功结果仅本人创建ID/时间，跨账号键隔离有证据。
- CSRF：Cookie和query/body旧密钥不授予写身份，必须Authorization；未叠加会破坏API的Cookie CSRF中间件。
- XSS：普通Markdown保留原文、旧广播HTML经cleanChat，白名单消息/通知元数据，无完整用户/联系方式/IP/UA/密钥输出；特殊类型不能伪造成卡片。实际PC/移动和两套聊天室DOM仍待验证。
- 注入/SSRF：SQL值参数化、表名由Repository、指令不执行；没有客户端URL抓取、上传下载返回路径变化。系统头像沿用项目已有robot3资源，没有新增第三方资源下载或发布静态目录。
- 滥用：共享写预算和原限制/复读状态已接，SQL和正文/客户端有界；账号对先于数据库锁，业务与幂等同事务；多实例状态共享范围、MySQL缺行/管理员并发与负载尚未验证。
- 防扫描：已重新读取BeforeRequestHandler黑名单重定向/test与validateCaptcha例外、AnonymousViewCheckMidware的v3身份/首访与周期验证码、CaptchaProcessor解封计数清理；新路由要求登录且未新增anonymous.viewSkips。没有产品JS/CSS/CDN/sourceMappingURL/目录或上传下载变更。实际黑名单/验证码/风控关闭模式与公开部署/CDN/目录枚举扫描未执行，不能默认为通过。

人工全链路（先确认既有v3/chat相关迁移完成，经IDEA重新编译或重启并明确确认）：
1. 一次性账号取得content.write与content.read，在http://localhost:8080调用POST，正文含首尾空白、Markdown、emoji及合法client，返回id/createdAt；GET详情/历史/上下文能读到相同ID，PC/移动两套旧聊天室与v3 chat订阅能观察同一条消息。检查正文清洗、作者、客户端与公开字段，职业来源一条、最后发言时间及正常时段活跃度/提及提醒符合规则。
2. 原键原体重放多次，消息/职业/扣款/通知/广播/活跃度不重复；相同键换正文或client返回409，另一账号同键互不影响；禁言后仍可重放首次成功结果，新键必须拒绝。缺Bearer401、缺content.write403、仅Cookie/apiKey401；错误媒体415、重复JSON键400、无幂等/未知字段/错误类型/空白/超限422。
3. 红包/天气/音乐/话题/弹幕完整包装、执法/zf及卡片JSON应422，无收费/改话题/弹幕/AI；一般解释性引用保留为正文。保留词变换后命令仍拒绝。风控账号单图/短正文拒绝，首次合法正文409 confirmation_required，原键原体再发送成功；切换旧入口与新入口时共享确认/复读状态。
4. 用一次性测试账号触发原复读处罚，检查禁言、思过簿、适用扣分及通知与主消息同时出现，原键不会重复扣分；穷账号仍禁言且不出现虚构转账。全员/个人、管理例外、到期边界及资料变更按当前记录检查。
5. 测试库故障注入任一存储环节、提交拒绝或确认丢失，核对根事务/连接/缓存及事件head；确认丢失后只用原键原体找回结果，不凭错误推断必然未保存。主消息提交后模拟提醒/广播失败，发送结果仍成功，客户端按HTTP/WS重新同步；无失败提醒的虚假事件。并发同键/不同键/旧入口/撤回/管理员禁言需真实MySQL、多后端和代理联调。
6. 黑名单IP仍跳/test，validateCaptcha正确解封后重试；开发关闭风控、匿名首访/每5次链路、部署静态目录/CDN/source map/上传下载路径一并回归。tools/api-v3-chat-send-smoke.mjs默认只测拒绝；实际发言必须显式ALLOW_CHAT_SEND、正文和固定幂等键，不自动撤回（可能有次数/费用）。脚本未运行。

未运行Maven、应用数据库/SQL、target/classes同步、浏览器/HTTP smoke、真实节点或部署扫描。全量目标保持active：普通发送已接源码但待运行验收；撤回/红包/话题/弹幕、账号/PKCE、文章在线/热度、其他设置/支付及整体验收仍未完成；鱼游OAuth入口和旧编辑歧义兼容仍待用户。API.md需同步网站API文章。

### 2026-10-18：删除事务核心、话题/价格读取与通知文本编码

上一轮普通发送已接源码并通过隔离测试，是实际进展。本轮核对旧撤回，发现文档“每24小时一次”与代码每天可多次、首次免费后按times*32递增不一致。用户已明确确认沿用实际代码：每天多次、0/32/64……；API.md已更正。没有开放v3 DELETE。旧撤回按用户名判断所有权及内存按名字计次、先独立扣费后删除的链路仍有风险，本轮没有声称整个旧收费链原子化。下文已准备完整共享适配器并通过隔离测试；其对旧入口的归属、失败扣费和持久计次兼容影响另发异步问题，等待用户确认后接线。

ChatMessageMgmtService新增removeInCurrentTransaction与removeLegacy：根事务或调用者保存点先查询当前消息并FOR UPDATE，再执行RemovalCheck；无目标直接false且不执行策略、不新增事实。消息/删除head/event在前，再按CHAT类型+ID清理全部reaction并捕获职业撤销，以与创建的head→职业顺序一致。外层即使捕获异常仍commit，局部失败也不能留下半次删除。旧Processor原删除包装已调用严格removeLegacy并移除废弃的职业注入/rollback辅助，正常字段、红包拦截、管理权限、现有费用/计次仍保留在旧控制器。新ChatMessageRemovalPolicy仅为待接v3准备：作者ID匹配，不回退可重用用户名；坏外壳/缺作者/非本人404，合法红包字符串和对象均409；普通不完整/非标准JSON文字仍是普通消息，不把它误当实际红包。它尚未接旧入口或v3路由，不能宣称旧所有权问题已修复。

新增GET /api/v3/chat/topic和/chat/barrager/price，统一content.read，经真实Requests返回no-store/Vary Authorization；响应分别只含text和amount/unit，不发言、不扣费。话题当前是进程内String，改volatile确保更新可见；价格原公开字段保留，新增同步get/set BarragerPrice快照，旧Bot命令改用成对setter、旧价格接口改读快照，避免金额与显示单位读成两次设置的混合值。没有新增持久化配置、时间戳、房间或节点凭据；当前值会随旧更新/重启而变化，实际发送规则不由该只读快照承诺。

检查话题的完整旧展示链时发现channel.js/channel-2.js通知直接拼接whoChanged/newDiscuss。两套源码均以真实jQuery.text().html()编码文本，encodeURIComponent编码作者路径，标题仍用text；保留旧样式/插入顺序/滚动路径。PC/移动chat-room.ftl共用channel脚本，第二套入口走channel-2，均已覆盖评估。没有改FTL/CSS。当前机器没有yarn命令，但有Corepack；已用COREPACK_ENABLE_AUTO_PIN=0 corepack yarn@1.22.22 run build成功执行Yarn构建，未安装项目依赖或改packageManager，生成对应两份min产物；其他此前article/m-article未提交变动保留，无新增CSS diff。

实际文件：src/main/java/org/b3log/symphony/service/ChatMessageMgmtService.java、util/ChatMessageRemovalPolicy.java、processor/ChatroomProcessor.java、processor/ApiV3ChatProcessor.java、service/ApiV3ChatQueryService.java、processor/bot/ChatRoomBot.java、processor/channel/ChatroomChannel.java；src/test/java/org/b3log/symphony/repository/ChatMessageRemovalTest.java、service/ApiV3ChatQueryServiceTest.java；tools/api-v3-chat-event-test.mjs、api-v3-chat-test.mjs、api-v3-chat-publication-test.mjs、api-v3-chat-smoke.mjs、chat-topic-notice-fixture.mjs；src/main/resources/js/channel.js、channel-2.js及Yarn生成channel.min.js、channel-2.min.js；API.md、UPDATE_LOG.md、AGENTS.md、docs/api-route-inventory.md与本计划。路由694声明=684 HTTP+10 WS（含禁用项），浏览器引用398，无新表/Repository/SQL。

隔离验证：事件/保存/删除共18组（原9+4+新5）通过；发送22组、读取/在线/状态14组回归通过。删除实际运行核心/当前行查询/typed reaction SQL/消息event/head/完整职业撤销捕获与实际addIfAbsent、严格JDBC/保存点，SQLite只替代底层表/ID/连接池与MySQL FOR UPDATE。覆盖600条reaction无截断、同ID文章/评论及其他chat保留、提交前两个读连接仍见旧数据、一次删除事件/一次反向来源、再次不存在无操作；策略拒绝及消息/event/head/reaction/反向来源故障下外层仍commit无半次写入，显式rollback/根commit拒绝/事务前置/嵌套禁止。addIfAbsent在反向来源写入后确认丢失时能按eventKey确认已有事实，故可以完整提交；确认查询也不可用时必须回滚，测试已区分这种已证实成功与未确认失败。

读取新增2组覆盖真实控制器/Auth/Query、纯文本/数字白名单、不触发历史SQL、无Bearer401/缺content.read403/不回退Cookie/apiKey、精确当前值；提取生产同步价格get/set，在并发10000次更新与10000次读取中验证金额/单位成对。getter/setter属于源码提取测试，全Processor/IoC与管理命令未完整运行。发送runner只增加未调用读取方法的明确适配以编译新控制器，发送业务22组继续通过。

前端验证：tools/chat-topic-notice-fixture.mjs从四份真实源码/产物提取discussChanged分支并识别Terser局部事件绑定，使用仓库原jQuery，在独立回环HTML样例通过IAB浏览器验证10项（源码/产物两套、正常/恶意文字、第二套fallback）。验证没有注入img/script等元素或执行标记，文字完整显示、作者href编码且无额外链接、标题text与通知插入顺序不变。证据/tmp/rhythm-chat-topic-fixture/dom-check.txt和dom-check.png；样例服务器49859及临时tab已关闭。这个浏览器只访问独立样例，从未连接8080或调用应用接口，不冒充PC/移动/真实WebSocket联调。

安全/反漏扫逐项：未鉴权/越权方面，两个新GET均强制Bearer content.read，无匿名入口或anonymous.viewSkips变化，响应无用户/节点凭据/内部存档；删除资格只为新入口准备，旧按名字认领未冒充已修复。CSRF方面GET无写入，原Cookie/apiKey旧撤回边界未改变。XSS方面新JSON字段为纯文本契约，旧通知插值已编码并有真实DOM/产物证据。SQL值绑定和ID/targetType校验，坏外壳不猜作者；无任意URL抓取或新增上传下载路径，SSRF面未新增。滥用方面共享鉴权/GET保护、删除先锁目标，价格快照读无数据库/网络；真实高并发/MySQL锁仍未验。

防扫描核心BeforeRequestHandler黑名单/test例外、AnonymousViewCheckMidware首访/计次和CaptchaProcessor解封未改；没有新匿名豁免。产品新增静态资源/新CDN目录为零，现有channel产物无sourceMappingURL；tools/js-version-audit.mjs扫描168个文件findingCount=0，报告/tmp/rhythm-v3-chat-topic-version-audit.json，四个改动文件无命中。这个本地源码/产物扫描不是实际CDN/部署目录枚举、验证码或上传下载路径验收，这些运行项仍未执行。无Maven、应用数据库/SQL或target/classes资源同步。

人工全链路（先IDEA重新编译或重启并明确确认）：
1. content.read Bearer在localhost GET话题/弹幕价格，分别只返回text与amount/unit；与PC/移动旧聊天室当前话题/价格一致。由测试账号走旧正常话题更新、管理员正常价格命令，再GET应立即反映成对新值，重启后按当前既有初始化行为核对。两个GET不得发消息/扣分/改变状态；无Bearer401、缺scope403、Cookie/apiKey不能授予身份。
2. PC/移动及两套聊天室使用含<、>、&、引号、emoji的测试话题，通知和标题应按文字显示、用户名链接正确，插入位置与滚动不变。受控恶意标签样例只能显示文本，不出现新元素/事件；强制刷新确保加载Yarn产物，若旧静态资源仍从target/classes读取则同步对应资源或重新编译。独立DOM测试不能代替这一步实际WebSocket链路。
3. 一次性账号通过旧撤回入口删除普通消息，确认页面卡片消失、v3详情404、实时deleted提示、目标emoji及职业反向来源正确；保留同ID其他类型/其他消息reaction。重复删除、他人消息、红包/天气/音乐、管理员等现有行为分别核对。旧费用仍在删除事务外，故障时先刷新并核对积分，不声称失败不扣款或按ID资格已生效。
4. 测试库注入消息/事件/reaction/职业撤销或提交失败，不得留下半次删除；反向事实确认丢失但能查询到时可完整成功，不能把已确认事实当失败。并发reaction/领取/撤回/旧API、多实例、真实MySQL缺行及权限变更仍需运行。v3 DELETE当前不存在；收费规则已确认，共享扣费/计次/幂等适配器见下节，旧入口兼容确认后接线，不做假验收。
5. 新IP/黑名单/test/validateCaptcha解封/风控关闭模式回归；核对生产/CDN目录、source map与上传下载响应路径。tools/api-v3-chat-smoke.mjs已补两个GET只读检查，但没有运行应用HTTP smoke。

全量目标保持active；本轮交付话题/价格读取源码及删除事务基础。撤回费用选择已确认；旧撤回完整共享兼容、鱼游OAuth规则入口和旧编辑歧义兼容仍待用户；其余撤回接线/红包/话题修改/弹幕发送、账号/PKCE、文章在线/热度、其他设置/支付及整体验收继续保留。API.md需同步网站API文章。

### 2026-10-18：已确认每日撤回费用，接入新旧原子撤回与幂等适配器

用户明确选择实际代码规则，新增ChatMessageRevokeFees：同一自然日按之前成功次数计算0/32/64……，新自然日归零，当前管理角色免计次；计次和乘法精确计算，溢出409，损坏状态不猜成零。旧控制器只提取32为共享常量，不改变现有扣款/计次执行方式。API.md/UPDATE_LOG.md更正已确认的旧费用说明，不公布尚未注册的v3删除路由。

新增ChatMessageWithdrawalMgmtService，withdrawLegacy严格根事务和withdraw显式Bearer用户的幂等适配器均已接线。事务前取得actor/sys账户对锁并打开ArticlePublicationCaches，事务结束后重读或清理用户缓存。当前锁用户并重查有效状态/管理角色，普通用户再锁cloud中userId+gameId=sys-chat-revoke记录；锁用户覆盖计次记录尚不存在时的跨实例创建。记录只含ISO自然日和次数，无新表/Repository/迁移，CloudService现有sys-前缀保护禁止普通云存档写入。记录重复/JSON损坏/日期或次数异常拒绝，不从坏数据重置。

扣款/流水（保留首次0积分流水及负余额拒绝）、持久计次、目标删除、event/head、reaction清理和职业撤销在同一保存点，外层捕获失败后再提交也不能留下局部扣款。存储目标当前读后按作者ID判断，不依赖名字；v3 owner-only传false，旧管理适配器仅当前角色具备原level3权限才允许跨作者且免费，不可凭调用参数升级。所有角色都拒绝合法红包卡片。用户随后已明确同意新旧一起接入原子撤回；旧控制器从loginCheck写入的User.USER取身份，不再二次body覆盖或依赖Sessions；保留成功code/msg、积分不足和红包提示，失败统一返回旧错误结构，审计仅提交后执行且异常不改写成功结果。旧内存revoke映射及独立扣费分支已移除。

v3准备的幂等operation按messageId隔离，只保存id/removed/cost回执，不保存正文。首次确认提交后才建立积分索引、审计并发仅type=revoke/oId旧通知；v3已提交事件由原持久来源读取，不重复直接发v3事件。相同键重放目标已删也返回原回执，不重复扣款/计次/通知。新键删除缺失目标404。通知失败只记日志，不把已提交成功伪装成失败。

新增文件：src/main/java/org/b3log/symphony/util/ChatMessageRevokeFees.java、service/ChatMessageWithdrawalMgmtService.java；src/test/java/org/b3log/symphony/util/ChatMessageRevokeFeesTest.java、repository/ChatMessageWithdrawalTest.java、repository/ChatMessageWithdrawalEndpointTest.java；tools/api-v3-chat-withdraw-smoke.mjs。修改ChatMessageRemovalPolicy以支持受信管理分支、ChatroomProcessor旧入口完整接线、ApiV3ChatProcessor注册DELETE /api/v3/chat/messages/{messageId}（content.write+write middleware）、tools/api-v3-chat-event-test.mjs与读取/发送runner适配、AGENTS/API/UPDATE_LOG/本计划及路由清单。无新SQL或前端改动；全批路由最终695声明=685 HTTP+10 WS（含禁用项），浏览器引用398。

事件/存储/撤回测试最终31组通过（原18+费用2+共享撤回8+入口3）。新测试运行真实共享撤回/幂等服务、请求哈希、严格事务/保存点、实际删除/职业捕获/reaction SQL；使用SQLite连接与用户/转账/云存档/请求记录适配，权限函数及相关模型常量从生产源提取，缓存刷新和旧广播为明确适配。覆盖独立读连接提交前不可见、0/32/64连续不同消息、重新创建服务实例仍保留计次、旧名字不影响ID归属、新旧适配器共享计次、首次0流水/负余额、账户失效、普通人伪造管理标记、管理免计次及不能撤红包、坏/重复/未来/溢出计次、新日重置、半次转账null/计次保存/删除/event/reaction/职业/回执/提交失败的整笔回滚、缺失目标不扣费、原幂等键重放、提交后索引/缓存/通知和广播失败保留回执。故意广播失败会输出ERROR日志，但最终测试exit0且31项PASS；这不是完整PointtransferMgmtService、全UserRepository缓存、IoC、MySQL并发或真实WebSocket验证。

入口新增3组使用真实v3控制器/Auth/Requests/WriteInputs、实际旧revokeMessage源码提取和Latke RequestContext/JsonRenderer，连到上述真实共享服务。无Bearer401/缺content.write403，Cookie/query/body不能补v3身份，未知cost/userId/apiKey字段422、坏JSON400/媒体415/缺键或坏ID422；空体与空JSON重试相同，成功no-store/Vary Authorization。旧入口使用中间件已确认身份，冲突body/query不再重新覆盖，旧-v3-旧三次0/32/64并保留成功/管理员/积分不足结构。Token发行/网络及LoginCheck本体依旧是适配范围，不能称端到端HTTP通过。读取14组、发送22组在新增路由后回归通过。

用户已批准旧DELETE /chat-room/revoke/{oId}兼容变化，API.md与UPDATE_LOG已公开说明新接口及旧归属/计次行为。新旧费用与管理豁免保留；首次共享计次从零开始，此后持久保存。v3删除仅content.write Bearer及幂等，无新增Cookie认证或anonymous.viewSkips；旧DELETE保留loginCheck及Cookie/apiKey支持，未强加会破坏客户端的页面CSRF中间件。本源码未发现该路由新增CORS放行；浏览器DELETE受同源/preflight边界约束，实际代理/CORS/凭据与请求伪造仍待运行验证。ID/费用/作者不可由body指定，SQL值绑定、响应只id/removed/cost，无正文或密钥，无新增URL抓取/上传下载/公网静态目录。共享write middleware约束v3批量请求；旧频率策略保持，但费用溢出拒绝。黑名单/验证码/解封与静态/CDN运行项沿用上节待验，未声称通过。

当前人工链路：IDEA重编译/重启确认后，用一次性用户从PC/移动旧入口与Bearer v3交替撤回三条普通消息，应分别0/32/64；刷新积分、记录和reaction及职业来源，原键重试不再扣，新键404；改名及重启后计次保留，自然日切换免费重置。他人/同名历史/红包/失效用户/余额不足拒绝且无扣款计次；管理员旧跨作者能力保留，v3只撤本人；故障库注入扣款/计次/存储/回执/提交失败全部不变，广播失败仍可取成功回执。并发跨实例、数据库死锁重试、同键竞争、Cookie/query/body apiKey和旧页面响应、禁扫/验证码链路仍须真实应用验证。tools/api-v3-chat-withdraw-smoke.mjs默认仅拒绝测试，实际撤回要显式开关、一次性账号消息ID及固定幂等键，错误后保留原键确认。未运行该HTTP脚本，没有应用数据库迁移或Maven。

补充提交确认丢失专项：测试让实际SQLite commit已成功后抛出SQLException，首次调用不猜成功、不提前广播；数据库仍只有一次删除/流水/计次/回执，原幂等键重试确认成功且不重复扣款或补发。当前持久化chat事件仍可供v3后台同步；旧广播和积分冗余索引不保证在此类确认丢失后补发，不能声称可靠投递。正式发布需同批升级各节点，未验证混合新旧版本的计次一致性。

全量目标继续active：本轮话题/价格读取和新旧撤回源码已交付；v3撤回已注册但待IDEA重启后的应用验收。鱼游OAuth入口与旧文章编辑歧义兼容仍待用户；红包/话题修改/弹幕发送、账号/PKCE、文章在线/热度、其他设置/支付及整体验收继续推进。API.md需同步网站API文章。

### 2026-10-18：话题持久事务、恢复投影与实时提示基础

上一轮新旧撤回已修改真实入口并得到隔离证据，属于progress。本轮继续话题/弹幕，读清旧setdiscuss和barrager分支均先扣费再过滤内容；话题只写ChatroomChannel.discussing、重启丢失，多后端各自持有状态。旧topic固定16积分，旧弹幕动态价格/显示单位；非admin弹幕在Bot.record早返回，仅走独立频控，绕过后续风控/禁言/复读处理，不能猜测v3应复制这类授权例外。话题filterDiscussString的isEmoji最后使用codePoint>=0x200D，会接受大量非emoji字符；没有悄悄收紧旧字符表。

已异步询问v3文本规则（话题沿用字符表、弹幕纯文本、超长422且不截断emoji、颜色#RRGGBB默认白色，或沿用旧截断/颜色），尚未回答。另在核心测试通过后询问旧setdiscuss兼容：新旧共用持久状态和原子16积分，正常字段不变、无效/存储失败不扣费、重启保留、各后端读同一已提交话题；备选暂保留旧行为并不开v3话题修改。均未获确认，不能当作已批准。

新增ChatTopicValue保存text/whoChanged/revision，option主键chatRoomTopic，值长度<=512并严格JSON/类型/UTF-16/版本检查。复用既有option表，无新Repository/表/SQL迁移，不是将固定收费配置化；费用仍Java常量16。新增ChatTopicMgmtService仅提供调用者事务核心：要求显式准入回调、账户对锁及缓存边界；锁当前用户重查有效状态/手机号/余额，再锁房间head与option（覆盖初次不存在时的跨实例创建），转账流水/话题和topic事件同保存点。重复设置同文仍按旧每次收费，不擅自改变业务；外层捕获异常后继续commit也不会留下半次收费。既有OptionRepository在缓存边界内按提交后失效，查询直接投影不读缓存，不使用可能提交事务的旧Bot方法。

topic事件复用现有api_v3_chat_event/state序列，kind=topic、内部target=0；共享validChange约束仅该目标。ApiV3ChatStream沿同一严格连续revision消费，不另造时间/消息ID游标；ApiV3RealtimeSessions把此标记投影为chat.topic_changed，data仅revision，不向客户端暴露内部占位ID、用户名、正文或option记录。已更新API.md的Socket提示契约与UPDATE_LOG，旧chat.message.*字段不变；**没有注册PUT话题写路由，没有改变GET话题数据源，也没有修改旧setdiscuss分支或启动恢复worker**，因此未声称正常页面话题修改已能触发新事件。

新增ChatTopicDeliveryService为待接的提交后投影。每次重新查询已提交topic而非使用延迟回调快照，串行比较revision，同版本不同值或版本回退拒绝；只恢复最新状态，跳过中间已被覆盖话题。重启后可从持久值恢复，source失败/写事务内不发布；旧广播仅type/whoChanged/newDiscuss/topicRevision，topic正文前端已具备文本编码。广播仍是尽力通知，失败不撤销已提交状态，也不保证补发；各实例广播与旧前端版本去重、GET读取持久源、后台资源收尾、准入/幂等/根事务适配仍需在获批后接好，不能把当前核心说成完整交付。

本轮实际文件：新增src/main/java/org/b3log/symphony/util/ChatTopicValue.java、service/ChatTopicMgmtService.java、service/ChatTopicDeliveryService.java与src/test/java/org/b3log/symphony/repository/ChatTopicMgmtTest.java；修改model/ApiV3ChatEvent.java、repository/ApiV3ChatEventRepository.java、util/ApiV3ChatStream.java、util/ApiV3RealtimeSessions.java，src/test/java/org/b3log/symphony/util/ApiV3ChatStreamTest.java、ApiV3RealtimeChannelTest.java，tools/api-v3-chat-event-test.mjs，AGENTS.md、API.md、UPDATE_LOG.md及本计划。没有新增HTTP路由、SQL/Repository、FTL、JS/SCSS或生成产物，不运行Yarn。

隔离测试：chat事件/撤回/话题总36组通过（原31+topic5），socket总45组通过（原44+topic游标1，真实嵌入式Channel原组新增topic帧断言）。话题执行真实新核心/值解析/事件JDBC/保存点，底层用户/积分/option写用SQLite适配，两个观察连接覆盖提交前不可见；原有请求/撤回/职业来源/emoji专项全部回归。覆盖创建及更新16积分、当前名称、没有假消息/职业来源、同文也扣费、半次转账null/option初次或更新确认丢失/event/head/根commit失败完整回滚、有效账号/手机/余额和显式准入拒绝、坏存档及溢出版本/超长/断裂代理对、未调用投影前内存不动、漏回调/重新创建投影可恢复最新值、投影同版本不重复/读取故障不广播、广播失败不撤销保存。Socket使用真实Latke握手/帧/Session和真实v3消费层，topic通知只revision，非法topic目标在sink前拒绝；权限/token、网络和数据库权威为测试适配，不是公网WS或HTTP。日志中故意故障有ERROR，以exit0与36/45 PASS为准。git diff --check通过，无Maven、应用数据库/浏览器/HTTP/真实MySQL并发验证。

安全/反漏扫结论：没有新匿名入口/anonymous.viewSkips、黑名单/test/验证码解封变化，尚未开放写路由。新增实时提示沿现有content.read票据和逐连接重新授权，仅版本白名单，不带高敏字段。持久写要求明确actor/当前资料/准入回调，fee不可由客户端提供；字符串JSON/长度/Unicode和绑定SQL约束注入及存档滥用；无HTML拼接、URL抓取或新增上传下载路径，XSS/SSRF面不新增。CSRF与批量调用将在新入口content.write/Bearer/write middleware接线时复核，不能声称未注册接口已完成鉴权；旧入口现有Cookie/apiKey与扣款先后尚未修改。没有新公网静态/CDN资源或source map；部署目录枚举、真实CDN/验证码/上传下载返回路径、代理/CORS仍未运行，不默认通过。

接线后的人工全链路（尚未执行）：先明确确认IDEA重编译/重启；一次性账号从PC/移动旧话题与Bearer v3改话题，核对每次16积分、GET当前文本、标题及两套通知只显示纯文本、WS新提示仅revision后重读；原幂等键重放不再扣费，失败/余额不足/禁言/非绑定手机不改话题不扣费，非法/超长文本按用户最终规则核对。改名用当前名字，重启与不同后端读取同一提交值；故意跳过提交后回调由worker恢复，提交后广播失败仍能GET结果，连续并发回调不能让旧值覆盖新值。事务/option/事件/回执故障、多实例、真实锁、旧客户端字段、风控/验证码完整回归，后台线程显式释放JDBC。当前公开可用写接口仍止于上轮普通发送/撤回，话题写与弹幕/红包等全量任务继续active；待上方两项选择后接线，API.md需同步网站文章。

### 2026-10-19：PKCE前置Token生命周期与客户端绑定

上轮话题基础已修改源码并通过测试，属于progress；文本规则与旧话题持久化两项兼容选择仍未回答。本轮转向全量目标中未完成的第三方OAuth授权。没有把等待话题选择视为全局blocked，也没有缩减完整目标。依据RFC 7636及RFC 9700核对方案（https://www.rfc-editor.org/rfc/rfc7636、https://www.rfc-editor.org/rfc/rfc9700），只实现所需基础，不宣称OAuth HTTP流程完成。

ApiV3TokenMgmtService原issueInternal/add固定website_v3，不适合授权码换第三方Token；新增issueForClientInCurrentTransaction，要求调用者事务，以注册客户端当前行核对启用状态、scope子集及当前数据库用户状态，生成正确clientId的access/refresh对。该方法不自己提交、不接受HTTP身份来源；ApiV3TransactionSavepoint保护两次insert，外层即使捕获失败继续commit也不留半对。未来换码须从服务端授权码记录取actor，并把码消费与Token提交放在同一根事务，不允许开放裸传userId发行接口。

refreshForClient/revokeForClient明确绑定注册client；现有refresh/revokeRefresh只代理website_v3，第一方请求/正常响应/TTL保持。所有写者先锁client，再按hash当前锁Token，跨实例序列由数据库锁而非Java synchronized负责；确保同一旧refresh只旋转一次，重用已旋转refresh按既有规则撤销整个当前登录。第三方刷新按当前数据库用户状态检查、scope取原grant与当前client允许交集，不因client新增scope提升权限；第一方现有已授予scope保留，不能把升级现有登录权限当作刷新行为。

ApiV3TokenRepository新增当前hash读取及精确clientId/userId/familyId整家族SQL撤销，移除旧getActiveByFamily最多1000条查询（无其他调用）；不跨用户/客户端碰撞familyId误删。markAccessUsed只按列且限定active/access/未过期更新，避免认证先读旧快照后将并发撤销state覆盖恢复。ApiV3ClientRepository新增clientId当前行锁；生成、刷新、撤销根事务禁止嵌套，严格提交。所有表/索引沿用既有v3迁移，不新增Repository/表/SQL。客户端级写锁会串行同client的Token写入，性能与MySQL死锁需真实负载验证；不能把SQLite去掉FOR UPDATE的测试当作并发证明。

新增ApiV3Pkce：只S256、verifier 43～128 unreserved字符、SHA-256后无填充base64url，challenge须32字节规范编码；不使用带rhythm-api-v3前缀的凭据存储hash。校验官方Appendix B向量，拒绝plain/大小写方法降级、短长/Unicode/空白/padding、非规范末尾padding bits，比较摘要用MessageDigest.isEqual。授权码存储、redirect URI精确登记、用户同意页（含PC/移动）、一次性消费、换码及第三方refresh/revoke HTTP端点、清理worker仍未实现，没有新路由或凭据响应公开扩展。

实际文件：修改src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java、repository/ApiV3ClientRepository.java、repository/ApiV3TokenRepository.java；新增util/ApiV3TransactionSavepoint.java、util/ApiV3Pkce.java；新增src/test/java/org/b3log/symphony/repository/ApiV3TokenLifecycleTest.java、util/ApiV3PkceTest.java；修改tools/api-v3-chat-event-test.mjs（加入真实Token服务/仓储及生命周期测试）、api-v3-socket-test.mjs（补保存点依赖）、api-v3-token-test.mjs（PKCE专项）；更新API.md、UPDATE_LOG.md、AGENTS.md、本计划和api-v3-inventory-and-design.md。无FTL/JS/SCSS/生成产物，无Yarn/Maven或target/classes同步；路由维持695声明，不生成虚假新路由清单。

验证：chat/撤回/话题/生命周期runner总44组（原36+Token8）exit0；Socket45组回归exit0；Token工具原5方法测试、Cookie1组及新增PKCE2组通过。Token专项执行真实服务、客户端/hash当前读及完整撤销/按列活动SQL、实际SHA-256/凭据生成、严格JDBC及保存点，用户查询/基类row写入/连接池由SQLite适配，明文仅测试内存返回。覆盖两服务实例依次刷新/重放（不是并发MySQL）、client→token顺序、新客户端绑定、Token对提交前观察连接不可见、原TTL/DTO、hash-only存储、旋转与整族失效、首次发行/旋转更新/两次insert/commit前失败回滚、调用方catch+commit不留半对、savepoint release确认丢失整根回滚、1005+活跃access完整撤销、同family跨client/user隔离、活动旧快照不能复活撤销、非法scope/current失效用户/嵌套和无事务拒绝、停用client及到期边界、第三方scope只缩减不提升。故意存储/提交错误会输出ERROR日志，以exit0/PASS为准；未验证原完整UserQueryService缓存、真实IoC、MySQL行锁/死锁/多实例并发、应用HTTP或浏览器。

公开API文档仅补已有第一方刷新Cookie一次使用与当前登录撤销说明，正常字段不变；UPDATE_LOG按本地日期261019置于261018之前，不暴露行锁/阈值/防扫描等内部细节。现有ApiV3Processor仍把刷新存储错误按原401清Cookie处理，logout仍捕获存储错误后清Cookie并返回成功，本轮未改变这些返回兼容行为；后续若改为可重试503需准备控制器结果与兼容确认，不得声称HTTP故障反馈整体已完善。提交确认丢失时也不具备可回收明文Token的幂等回执，不能用普通业务幂等表存凭据，仍需用户重新登录。

安全/反漏扫结论：无新匿名入口/anonymous.viewSkips/黑名单/test/验证码解封改变；既有登录/刷新/退出仍走匿名校验和原来源边界，未新增Cookie或query身份到Bearer业务接口。当前客户端和Token/用户绑定及scope核对防越权，SQL值绑定、输入长度/PKCE grammar防注入与异常载荷；凭据只hash落库，日志不加入原Token/verifier，公开响应字段未增敏感资料。无HTML/URL抓取/上传下载或公网静态/CDN变化，XSS/SSRF面未增。CSRF/来源和暴力尝试频控沿用旧入口，本轮没有新OAuth公网接口可被滥用；真实CORS、验证码、黑名单、目录枚举、CDN/source map及上传下载路径尚未运行验证，不默认通过。

人工全链路（先明确确认IDEA重编译/重启）：1）一次性账号从现有website_v3登录，必要时完成2FA，GET /me正常，检查原响应字段及有效期。2）正常refresh换新Cookie/Access后/me成功；专用测试客户端重放刷新前Cookie应401且本次登录所有Access失效，独立另一次登录仍可用；缺失/过期Cookie和异源请求拒绝。3）重新登录后logout，当前Access随后/me 401，其他独立登录不受影响；保留原请求字段及Cookie标记，PC/移动刷新恢复一并验。4）测试库故障注入发行/旋转/家族撤销/提交，核对无半对、无状态恢复、数据库已提交但确认丢失需重新登录；HTTP错误反馈仍有上述既有局限。5）新IP/黑名单跳/test、验证码解封/开发关风控、API无Cookie/apiKey替代、静态/CDN/上传下载路径回归。新通用client方法仅能运行隔离专项，尚无OAuth端点供人工换码；完整OAuth、账号注册/恢复与其余业务继续active。API.md需同步网站文档文章。


## 2026-10-19：新旧话题写入接线与持久投影

用户已确认：v3沿用话题字符表、超64 UTF-16拒绝，弹幕纯文本/32与#RRGGBB规则；新旧话题共同持久化并原子扣16积分。本阶段落地话题，弹幕发送继续后续实现。新增PUT /api/v3/chat/topic，content.write、显式Bearer、共享写预算、严格JSON白名单text与Idempotency-Key。原始text作为幂等指纹，当前用户/手机/有效状态、原保留词与禁言/风险确认/复读策略在锁内准入；自动存储重试保留已确认决定，不重复消耗确认。费用、option/chatRoomTopic、topic/0事件与回执同事务；回放不扣费、不发消息、不恢复旧话题。每个新键仍收费16。GET读取已提交数据库，缺记录兼容初始进程值，存储错误500，不回退陈旧缓存。

共享ChatTopicWriteService负责账户对锁→缓存边界→严格根事务、提交后索引/投影，避免v3服务与旧Processor的IoC循环。旧[setdiscuss]仍先执行旧Bot准入，再调用同核心，正常成功字段/过滤/截断不变；边界不切开emoji，失效或存储失败不扣话题费。旧Bot独立准入处罚仍沿用原边界，不能声称其整个处罚与话题原子化。旧话题三层身份统一从loginCheck写入的User.USER取，支持query/body apiKey。其他旧命令身份优先级未改。

ApiV3RealtimeService启动后的worker即使没有chat订阅也重读已提交话题，恢复重启/漏回调的进程默认投影；读取失败不关闭健康消息订阅。ChatTopicDeliveryService广播仅公开文字/作者/字符串revision。两套channel源码及Yarn产物按字符串版本去重、防延迟旧事件覆盖（含大于2^53版本），保留无版本旧事件兼容。投影恢复是最新状态恢复，不是可靠重放每次旧广播；写入成功不承诺离线客户端收到通知。

实际文件（本阶段）：生产Java util/ChatTopicInputs.java，service/ChatTopicWriteService.java、ApiV3ChatTopicService.java、ChatTopicMgmtService.java、ChatTopicDeliveryService.java、ApiV3ChatPublicationService.java、ApiV3ChatQueryService.java、ApiV3RealtimeService.java，processor/ApiV3ChatProcessor.java、ChatroomProcessor.java、bot/ChatRoomBot.java、middleware/validate/ChatMsgAddValidationMidware.java（均位于src/main/java/org/b3log/symphony）；前端src/main/resources/js/channel.js、channel-2.js及Yarn生成channel.min.js、channel-2.min.js。测试src/test/java/org/b3log/symphony/repository/ApiV3ChatTopicTest.java、ApiV3ChatPublicationTest.java，service/ApiV3ChatQueryServiceTest.java，util/ApiV3RealtimeChannelTest.java、ChatMessageValidationTest.java；tools/api-v3-chat-publication-test.mjs、api-v3-chat-event-test.mjs、api-v3-chat-test.mjs、api-v3-chat-validation-test.mjs、api-v3-socket-test.mjs、chat-topic-notice-fixture.mjs、api-v3-chat-topic-smoke.mjs；API.md、UPDATE_LOG.md、AGENTS.md、docs/api-route-inventory.md及本计划。无新增表、Repository或SQL；既有v3事件表及种子迁移仍为部署前提。路由696声明=686HTTP+10WS，浏览器引用398。

验证：Java25临时专项（非Maven）publication runner原22组及新增话题6组通过，覆盖真实控制器/服务/幂等/保存点，SQLite持久适配显式保留；新旧费/记录失败回滚、响应丢失按原键解析、后续话题不被回放覆盖、当前身份/手机/状态/禁言/确认及JSON/scope/media/key边界均通过。event44、query14及validation原专项通过；socket45含无订阅投影worker恢复通过。日志/tmp/rhythm-v3-topic-{full,event,query,validation,socket}-test.log；故意故障有ERROR输出，以exit0与PASS为准。没有据此声称真实MySQL行锁/并发、完整旧Bot或IoC已运行。Yarn1.22.22完整build成功；js-version-audit扫描168文件0命中，报告/tmp/rhythm-v3-topic-version-audit.json。离线IAB浏览器真实运行四份源码/产物discussChanged分支，18项正常/恶意文字/第二套fallback/版本重复倒序与2^53精度通过，临时tab及18763样例服务已关闭。未连接8080。

安全与反漏扫：新增写入仅Bearer/content.write，拒绝Cookie/query身份替代，已复核API路由经过现有v3鉴权与写预算；旧话题仍经loginCheck及旧Bot。无anonymous.viewSkips、BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware首访/计次或CaptchaProcessor解封变更。固定服务端actor/费用/版本、当前数据库资料防越权；严格JSON/长度/Unicode/白名单防载荷滥用，SQL参数绑定，话题纯文本及通知作者编码防XSS；无URL抓取故无新增SSRF，无高敏字段输出或上传下载路径变更。Bearer无Cookie回退，旧入口CSRF边界未变。本地静态0命中及无新CDN/public目录不是部署扫描结论；实际匿名新IP/黑名单/验证码、CDN/source map/目录枚举及上传下载路径仍未运行验证。

完整人工链路（必须先由用户确认IDEA重编译/重启，当前未确认）：
1. 一次性测试账号从PC及移动聊天室进入，正常旧[setdiscuss]修改，成功字段不变、只扣16；v3 content.read GET当前纯文本一致。重启及另一实例读取仍一致，初次无记录显示兼容默认值。
2. content.write Bearer PUT新话题，返回text/cost=16/revision；原键原体重试无额外费用/事件，后来另键改话题后重放原键仍返回原回执、GET保持新值；同键换体409，新键同文仍扣16。
3. 未登录401、仅read403、Cookie/query/body apiKey不可冒充v3；空白/过滤空/超64/损坏Unicode/未知字段422，错误媒体415/JSON400、缺键422，余额15时409且无话题/扣款；手机未绑、失效、禁言拒绝。风险确认第一次409，原体原键再次成功。
4. 新旧话题均注入存储/提交故障，费用/option/事件/回执不留半笔；提交确认丢失用原键确认，不能另键自动重试。漏投影回调或重启由worker恢复最新值。真实MySQL双实例并发、账户/事件锁顺序与回滚仍待运行验收。
5. 两套样式及PC/移动通知纯文本/作者链接安全，版本重复/倒序不重复插入通知；v3chat WS只收到topic_changed revision后GET，不查询messageId=0。无chat连接时worker也恢复旧页面话题。反扫新IP/test/验证码解封/开发关风控与静态/CDN/上传下载再回归。

未运行Maven、应用数据库迁移/HTTP smoke/真实WS、target/classes同步、真实多实例或部署扫描。完整目标保持active；话题已完成源码链路，弹幕/红包、完整PKCE与其余未完成模块继续。API.md需同步网站API文档文章 https://fishpi.cn/article/1636516552191。

## 2026-10-19：v3即时弹幕、幂等扣费与白名单事件

上一目标轮完成话题端到端源码与验证，属于实质进展；本轮继续已确认的弹幕能力，完整v3目标仍active。新增POST /api/v3/chat/barrager，content.write、共享写预算、严格JSON content/color及Idempotency-Key。ChatBarrageInputs限制原始4096 UTF-16、Unicode/控制字符，去HTML后纯文本1至32 UTF-16，超长拒绝不截emoji；颜色仅六位hex，缺省/空串白色，事件大写。显式当前用户先经原保留词/基础校验，再重验清洗规则；当前账号、手机、禁言与风险确认在事务内核对，不调用Sessions旧Bot。旧弹幕原本在独立频控分支早退，v3不额外套复读处罚或更新普通消息latestMessage。

ApiV3ChatBarrageService先取得actor/sys账户对锁，打开缓存边界和幂等根事务；当前锁定用户、确认、共享额度、成对价格快照、扣费与回执同严格提交。负价格/异常单位503，零费用不造转账，显示单位不改变积分扣费。原始content/color指纹，回执仅UUID id/createdAt/cost/unit。自动存储重试复用一次quota及确认决定；成功重放不占quota、不扣费、不发动画，价格改变不重写原回执。转账返回null时根事务回滚，提交确认丢失以原键解析结果，不推测动画已送达。

弹幕是即时动画，不写chat历史、职业来源或持久事件revision。确认本次新提交后才索引、旧广播/活跃度及有界worker发送v3事件，各提交后效果分别隔离。ApiV3BarrageEvent是不可变公开字段记录，头像只允许无凭据HTTP(S)，dto每次新建。ApiV3RealtimeSessions仅向chat索引中逐连接重验Token/scope/IP的订阅发送chat.barrager；通知/文章/未认证连接不收。worker沿用finally清理JDBC线程资源，事务内禁止发送。当前只覆盖接收请求实例的v3连接，其他实例及旧入口产生的弹幕尚不桥接为v3事件；旧广播仍走原Node分发。动画没有revision、历史读取、撤回或离线补播；回执代表扣费及受理，不保证送达。广播失败或提交确认丢失时，同键回放也不补动画。这不是持久outbox，不能声称全站可靠送达。

旧Bot弹幕额度抽到同步ChatBarrageQuota，共用原每分钟5次/用户名及admin用户名例外，避免底层get/put并发竞争；旧正常输入、费用、成功字段与早退规则保持，旧无效输入先扣款风险未顺手修改。两套channel在插件append前编码整段昵称/用户名与正文，作者路径encodeURIComponent；修改源码后Yarn生成min，未直接改vendor min。

实际文件：
- src/main/java/org/b3log/symphony/util/ChatBarrageInputs.java、ChatBarrageQuota.java、ApiV3BarrageEvent.java、ApiV3RealtimeSessions.java；service/ApiV3ChatBarrageService.java、ApiV3RealtimeService.java；processor/ApiV3ChatProcessor.java、ChatroomProcessor.java、bot/ChatRoomBot.java。
- src/main/resources/js/channel.js、channel-2.js及生成channel.min.js、channel-2.min.js。两套样式与PC/移动共用入口均评估，无FTL/SCSS改动。
- src/test/java/org/b3log/symphony/repository/ApiV3ChatPublicationTest.java、ApiV3ChatBarrageTest.java；util/ChatBarrageQuotaTest.java、ApiV3RealtimeChannelTest.java；service/ApiV3RealtimeSessionsTest.java。
- tools/api-v3-chat-publication-test.mjs、api-v3-chat-event-test.mjs、api-v3-chat-test.mjs、api-v3-socket-test.mjs、api-v3-chat-barrage-smoke.mjs、chat-barrage-notice-fixture.mjs。
- API.md、UPDATE_LOG.md、AGENTS.md、docs/api-v3-inventory-and-design.md、docs/api-route-inventory.md及本计划。

无新增Repository、表或SQL；既有v3表/种子仍为部署前提。路由697声明=687HTTP+10WS，浏览器引用398。Java25临时专项（非Maven）publication36组（旧22、话题6、弹幕7、并发quota1）、event44、query14、socket46均exit0。真实service/controller/idempotency/DTO/session/facade参与；SQLite持久、资金/缓存/传输等适配在runner明示，不能据此证明真实MySQL转账锁或完整IoC。覆盖转账部分失败、回执/严格commit失败回滚、丢失提交确认按原键解析、广播失败不重复收费或补播、存储自动重试只占一次quota/确认、变化价格与0/负价格、当前身份/手机/状态/禁言/风险、文本/颜色/Unicode边界、媒体/JSON/键/未知字段、scope及Cookie/query冒充、24并发准确5次、逐连接撤权与频道隔离、真实Netty动画帧及事务内拒绝。日志/tmp/rhythm-v3-barrage-{publication,event,query,socket}-test.log，故意故障有ERROR，依据终态exit0/PASS判断。

完整Yarn build及源码node语法检查通过。js-version-audit扫描168文件0命中，报告/tmp/rhythm-v3-barrage-version-audit.json，channel生成产物无sourceMappingURL。离线IAB浏览器运行两套真实source/min分支和仓库原barrager插件，16项正常/恶意正文、昵称/用户名与作者路径通过，无可执行元素注入；只访问独立127.0.0.1:18764样例，不访问8080，不冒充PC/移动实际页面或真实广播联调。

安全/反漏扫：新增入口明确Bearer/content.write，无Cookie/apiKey回退；费用/作者/时间/id服务端生成，当前资料和共享quota防越权与批量滥用。SQL值绑定，JSON/Unicode/长度/颜色约束；纯文本DTO与插件编码防XSS，头像不服务端抓取，无新增SSRF；无联系方式/IP/UA/密钥输出，幂等表不存凭据/作者资料。Bearer接口不使用Cookie身份，旧CSRF边界未改。BeforeRequestHandler黑名单/test、AnonymousViewCheckMidware首访/计次、CaptchaProcessor解封源码未改，anonymous.viewSkips无新增豁免。无新静态/CDN目录或上传下载路径。真实新IP/黑名单/验证码解封、开发关风控、部署目录枚举、CDN/source map及上传下载仍未实跑，静态0命中不能替代运行验证。

人工完整链路（已提醒但用户尚未确认IDEA重编译/重启，确认前不得访问8080验证新Java）：
1. 一次性账号从PC/移动两套聊天室进入；content.read取chat票据并首帧认证v3连接。GET弹幕价格后content.write POST新键content/color，回执仅id/createdAt/cost/unit、费用为当前快照；旧页面与同实例chat订阅显示一次纯文本动画，历史无弹幕。核对当前作者、尖括号不执行、链接编码、无高敏字段。
2. 原键原体重试不扣费/动画/活跃度；同键换正文/颜色409；价格变化后原键仍原费用，新键取新快照；默认白色、小写六位转大写、零价无转账。只用测试配置或管理员正常命令准备价格，脚本不自行改价。
3. 未登录401、无write403、Cookie/query/body apiKey不能授权；空白/清洗空/33 UTF-16/断开emoji/控制字符/非法颜色422，媒体415/JSON400/缺键/未知字段422。余额不足409且无费用/回执/动画；手机/状态/禁言拒绝，风险首次409再原键成功。连续新键达额度429，成功回执重放仍可用；切旧入口共用quota，admin原例外保持。
4. 测试库注入部分转账、回执及提交失败，核对费用和回执全回滚、不发动画；自动重试只占一次quota。提交确认丢失仅原键解析，不另键造第二笔。传输失败仍有已受理记录，同键不补动画；重连/同步历史不补播。真实MySQL并发/缓存/账户锁仍待运行验收。
5. chat Token撤销/过期/scope撤销/IP限制时不收动画，通知/文章/未认证不收，慢连接队列有界并返回JDBC资源。跨实例按当前局限核对，不称全站实时。PC/移动正常旧请求/响应及费用保持。
6. 新IP与黑名单/test、验证码解封、开发关风控，以及静态/CDN版本/source map/目录枚举、上传下载路径完成部署回归。

未执行Maven、应用DB/迁移/HTTP smoke/真实WS、target/classes同步、真实MySQL/IoC/多实例或部署扫描。API及更新日志已同步，需提醒API.md同步 https://fishpi.cn/article/1636516552191。完整目标active：红包、完整PKCE/账号链路、其他业务、跨实例弹幕桥接与最终验收继续；鱼游OAuth规则入口及旧编辑歧义兼容等待原用户资料，不阻断独立工作。

离线样例清理补记：IAB临时tab已不在会话中，listTabs确认空；18764样例服务器已终止。保留/tmp/rhythm-chat-barrage-fixture/index.html便于重新运行离线验证。

## 2026-10-19：第三方PKCE授权码与原子换码核心

上轮已落地弹幕接口、实测回滚/幂等/实时授权和前端，因此分类为进展。本轮继续完整OAuth前置，不把内部核心当成已开放的HTTP。用户已明确回调允许HTTPS、开发HTTP回环及原生登记的专用scheme，全部精确匹配，禁止片段和用户凭据。

新增ApiV3AuthorizationCodeService：可信服务端同意处理器加入其授权请求事务后，才可调用签码primitive；该primitive不自提交，局部保存点避免调用者catch+commit留下半个授权。当前client、注册回调和当前有效用户验证，approved scope只允许client现有范围，S256-only，5分钟短期码。授权码只落hash，绑定client/user/完整redirect/scope/challenge/时效；不存verifier/明文code，Issued默认toString也脱敏。签码接口不是可指定user/scope的公开API，未来同意入口必须从服务端授权请求和已登录身份产生参数。

换码root锁序client→registered redirect→code→current user，严格事务消费码和创建access/refresh Token对；与Token服务同client锁，禁止嵌套root或独立子提交。当前client允许scope缩减时只取原批准交集，不授予新增scope；检查当前用户/client/redirect有效性及到期边界。绑定和PKCE正确的已用码重放撤销其精确client/user/family，再提交后invalid_grant；错误client/URI/verifier不消费码、不撤销合法Token。部分Token发行、消费更新、提交或撤销失败全回滚。返回既有内部Token DTO供未来标准OAuth HTTP适配，不把凭据写进普通幂等回执。当前没有普通业务幂等重放明文Token功能；丢失成功换码响应后必须重新走授权，不能重试取回Token对。

ApiV3OAuthRedirects只做形状策略，不执行DNS或网络抓取；注册表再按hash身份及完整原串匹配，防数据库collation折叠大小写。HTTPS有有效host/port；开发仅localhost、127.0.0.1、[::1] HTTP；原生遵循RFC8252反向域名scheme、单斜线路径，禁止opaque/authority型和javascript/data/file等通用scheme。完整URI含端口/查询精确匹配，用户选择未启用动态回环端口例外。生产不会因为存在开发回调记录而允许HTTP换码。注册仍需后续管理入口可信审查；不是允许客户端自行登记任意scheme。

实际文件：src/main/java/org/b3log/symphony/model/ApiV3AuthorizationCode.java、ApiV3ClientRedirect.java；repository/ApiV3AuthorizationCodeRepository.java、ApiV3ClientRedirectRepository.java；service/ApiV3AuthorizationCodeService.java；util/ApiV3OAuthRedirects.java；sql/20261019_api_v3_oauth.sql；src/main/resources/repository.json；src/test/java/org/b3log/symphony/repository/ApiV3AuthorizationCodeTest.java；tools/api-v3-chat-event-test.mjs；AGENTS.md、docs/api-v3-inventory-and-design.md及本计划。新增两张表和模型/Repository常量不带symphony_，SQL按默认前缀，metadata同步。没有HTTP路由、FTL/前端、静态/CDN或公开接口字段改变；API.md/UPDATE_LOG.md不虚构OAuth已开放记录。原697路由声明与398浏览器引用不变，旧/openid链独立未改。

验证入口：node tools/api-v3-chat-event-test.mjs。Java25临时独立编译及51组通过（原44+授权码7），不是Maven。实际新service/repo/Token core/PKCE与SQL参与；base row持久与池由SQLite明示适配，不能当作MySQL并发/全IoC证明。覆盖hash-only与脱敏toString、锁顺序、同事务消费/Token对、精确绑定和路径大小写/查询/伪装host、错误证明不消费/撤销、合法重放只撤销原家族、独立批准不受影响；半Token/code add/update/strict commit/revoke故障回滚；owner catch+commit不留失败码；观察连接读不到未提交授权、rollback清除；当前账号/client/redirect状态、scope缩减不扩权、到期边界、腐坏生命周期/家族/user失败关闭；无事务/嵌套root、非法scope/plain PKCE拒绝；HTTPS、开发IPv4/IPv6/localhost及native实际签码换码，production HTTP拒绝。日志/tmp/rhythm-v3-oauth-code-test.log，故意故障有ERROR，以exit0和51 PASS判断。迁移与repository字段对应静态检查、JSON解析和diff空白检查通过；SQL未执行到应用数据库。

参考主源：RFC7636 S256、RFC6749 4.1.2的短期码/单次使用/可行时撤销关联Token，以及RFC8252 7.1反向域名scheme和单斜线原生回调。https://www.rfc-editor.org/rfc/rfc7636、https://www.rfc-editor.org/rfc/rfc6749、https://www.rfc-editor.org/rfc/rfc8252。这里只按本轮选择实现相应组件，不据此宣称完整OAuth流程已符合全部标准。

安全/反漏扫：无新公网入口、匿名豁免、anonymous.viewSkips、黑名单/test、验证码/解封或页面登录/CSRF变更。当前代码不能通过HTTP直接发行凭据；未来授权/同意/换码入口仍必须分别接现有反扫、当前身份、来源/CSRF或PKCE及限流。字段长度/Unicode/scope/URI/PKCE规则、SQL参数绑定与服务器批准身份防注入/越权；无HTML渲染或服务器URL抓取，无新增XSS/SSRF。只存码hash/公开challenge，未加明文凭据日志、敏感资料响应或上传下载路径。没有新静态目录、CDN或sourceMappingURL变化。真实匿名新IP、黑名单/验证码解封、目录枚举/CDN/source map及上传下载路径未实跑，不能默认通过。

人工完整链路与待验范围：
1. 当前可用验证入口只有上述CLI。运行至51 PASS/exit0：临时fixture登记client/URI，签码后确认观察连接只在commit后可见、记录不含code/verifier；正确client/redirect/verifier换Token，当前user/scope/时效正确。错误client/回调路径大小写/查询/PKCE应invalid_grant且原码可正常换；成功码的合法重放应撤销本家族，不影响独立授权。
2. 在隔离测试库注入半Token/消费更新/提交/家族撤销故障，验证无半对或已消费但无Token；同意owner catch+commit也不能留下失败码。缩减scope、停用client/回调/用户、到期与腐坏状态失败关闭。未声称两服务实例MySQL竞态、死锁与提交确认丢失已验证，后续需真实库专项。
3. 迁移前先核对部署tablePrefix，在测试数据库执行新增SQL并确认metadata表列；本轮已明确提醒Java需用户通过IDEA重编译/重启，当前未确认，未访问8080。授权请求/同意页/HTTP换码/注册管理尚未实现，不能提供或运行不存在的curl路径；完整浏览器链仍待后续接线：登记回调→授权请求→PC/移动明确同意→原state回跳→S256换码→scope业务调用→刷新/撤销，再验证篡改/重放/来源/未登录/反扫。
4. 原生回调使用预登记com.example.app:/oauth2redirect；开发回环固定登记端口，生产HTTP拒绝；无效回调不能自动重定向。真实浏览器/OS交接、state校验、CSRF及同意伪造仍未实现/运行，不能因核心测试通过而发布完整OAuth。

未执行Maven、应用迁移/HTTP/浏览器/target资源同步或真实MySQL/多实例/完整IoC。目标保持active：本轮只有PKCE授权码基础完成，授权请求记录、同意页面与来源/CSRF、注册管理、标准HTTP换码/刷新/撤销、清理/保留策略、OAuth端到端、剩余业务和全量验收继续；鱼游OAuth说明入口仍等待用户提供，不能把此v3核心自动套作鱼游校验。API.md已有业务更新仍需同步网站文档文章。

## 2026-10-19：服务端授权请求与一次性明确同意

上一轮授权码、PKCE及原子换码源码和51组验证属于实质进展。本轮继续完整第三方OAuth，新增授权请求状态，而非把签码primitive直接开放成可指定user/scope的HTTP入口。尚无新路由或同意模板，全部目标仍active。

ApiV3AuthorizationRequestService在严格root内验证当前client/精确登记回调/请求scope/当前有效Cookie actor，保存10分钟请求；client/user/redirect/scope/state/challenge固定，handle与独立consent proof仅存hash，Prepared默认toString脱敏。创建请求不产生code或Token。确认参数仅owner、handle、proof及明确批准/拒绝，不能带新scope/user/redirect覆盖。hint查询先owner+hash过滤，仅投影不可变client字段，root先锁client/redirect再锁请求、重验hint与完整记录，随后验证当前user；scope缩减、client/回调/账号失效或时效损坏均拒绝。批准调用签码primitive，决定与code同严格事务；拒绝不签码。两个决定都单次，重放不能翻转或重发code；存储失败恢复pending，原proof可重试。没有保存明文code或Token来做回执恢复，丢失已提交确认响应需重新发起授权。

ApiV3OAuthCallbacks编码code和原state，保留登记回调原有查询；state可空，规范Unicode、无控制字符，最多1024 UTF-8字节，空值按OAuth空参数规则不回传。登记URI的ASCII表示最多2048，避免合法Unicode路径展开成过长Location；生成回调转为ASCII而不改变换码时登记原URI的精确比较。登记URL禁止预置code/state/error/error_description/error_uri/iss（包括编码或大小写变体）避免响应参数碰撞。HTTPS、开发回环、原生专用scheme仍按上轮用户选择，原生/Unicode回调和state特殊字符往返已测。

新增ApiV3ConsentSecurity工具，未来Cookie同意写操作必须用配置站点的scheme/host/有效端口匹配Origin，缺Origin才用Referer；拒绝null/畸形/带用户凭据/伪装前缀主机及跨站Fetch，不信任请求Host。CSRF必须是当前Session非空有界原值并恒时比较，拒绝损坏Unicode。该工具尚未接HTTP，不替代页面身份/来源/CSRF联调；旧CSRFMidware的Referer前缀判断及旧ApiV3Processor来源函数均未顺手修改。未来同意页必须明确从Cookie Sessions取用户，不能套支持apiKey的loginCheck attr或Bearer身份来批准用户权限；还要DENY framing、no-store/no-referrer及严格表单参数处理。

实际文件：src/main/java/org/b3log/symphony/model/ApiV3AuthorizationRequest.java；repository/ApiV3AuthorizationRequestRepository.java；service/ApiV3AuthorizationRequestService.java、ApiV3AuthorizationCodeService.java；util/ApiV3OAuthCallbacks.java、ApiV3OAuthRedirects.java、ApiV3ConsentSecurity.java；sql/20261019_api_v3_oauth.sql；src/main/resources/repository.json；src/test/java/org/b3log/symphony/repository/ApiV3AuthorizationRequestTest.java、util/ApiV3ConsentSecurityTest.java；tools/api-v3-chat-event-test.mjs；AGENTS.md、docs/api-v3-inventory-and-design.md及本计划。新表api_v3_authorization_request无Repository前缀，SQL用部署默认前缀，metadata同步；OAuth迁移现含三张表。没有HTTP接口/公开响应变化，API.md/UPDATE_LOG.md不虚构已开放OAuth记录；路由仍697、浏览器引用398。没有FTL/JS/SCSS变化，不需前端build。

验证入口node tools/api-v3-chat-event-test.mjs：Java25临时独立编译（非Maven）58组通过，原51加请求5和来源/CSRF2。实际请求、签码、换码、Repo SQL和Token核心参与；SQLite/base persistence与资金等旧适配在runner明示，不能当作真实MySQL或完整IoC证明。覆盖start不签码、只存handle/proof哈希、批准一次并完成真实PKCE换Token、原查询和UTF-8 state往返；拒绝不签码、空state省略、错误owner/proof不消费、决定不能翻转/重放；请求add、code add、决定update及strict commit故障整笔恢复pending；当前用户/client/回调/scope、到期和腐坏状态失败关闭；嵌套事务拒绝；Unicode URI转ASCII、编码/大小写响应键冲突；配置来源scheme/host/port及CSRF空白/损坏输入。终态exit0及58 PASS，故意故障有ERROR，日志/tmp/rhythm-v3-oauth-consent-test.log。metadata与三表列名对应、JSON解析及diff空白检查通过，SQL未执行到应用数据库。

安全/反漏扫：无新公网路由/匿名豁免或anonymous.viewSkips/黑名单/test/验证码解封变化，当前服务不能被HTTP直接调用。owner+hash查询和锁内当前身份/许可防越权，状态/proof绑定防伪造决定；来源/CSRF工具已测但未接线，不能声称HTTP防护已完成。参数绑定、长度/Unicode/scope/URI白名单防注入；callback仅拼接经过验证的登记URI并编码响应值，无URL抓取/新增SSRF。尚无HTML渲染，未来clientName/URI/scope必须FTL转义；Prepared/Decision不日志泄露凭据，无IP/UA/联系方式/密钥对象输出。HTTP批量频控和请求清理尚未接入，因此不能发布同意入口。没有新静态/CDN/source map/目录或上传下载路径变化；真实新IP/黑名单/验证码解封、开发关风控、CDN/source map/目录枚举与上传下载未运行，不默认通过。

人工完整链路及边界：
1. 当前入口只有上述CLI，运行至58 PASS/exit0；临时fixture登记client/回调后start，检查请求只保存hash且无code/Token；批准获得保留原query与state的回调，用其中code+原verifier完成换Token。再次批准或改拒绝无新code，也不撤销已有合法Token。拒绝只回access_denied与原state。
2. 换owner、proof，篡改当前许可/停用账号/回调、到期或破坏记录，均失败且无code/决定写入；请求/签码/决定/commit故障无半批准，原proof可重试。原生与Unicode回调、特殊state、空state及预置响应参数分别核对。来源工具拒绝evil前缀/协议或端口变化/Origin null/空CSRF；这里只验证工具，不能替代浏览器。
3. 用户尚未确认IDEA重编译/重启，本轮已再次明确提醒；新表先核对tablePrefix并在测试库执行SQL，再由IDEA重编译或重启。当前未访问8080、执行应用迁移或Maven；完整MySQL竞争、锁序、提交确认丢失、IoC及连接回收仍待运行验收。
4. 下一阶段实际用户链必须接：浏览器Cookie登录→GET授权请求与PC/移动明确同意页→只提交request/proof/决定及Session CSRF→精确来源校验→303回登记URI并校验原state→标准HTTP S256换Token→scope业务读取/写入→刷新/撤销；同时测未登录、仅apiKey/Bearer不得批准、跨站/点击劫持/参数污染、scope扩张、双击与原始响应丢失、反扫验证码及清理。该HTTP/模板链尚不存在，不能给不存在的curl路径或称已完成。

完整目标active。授权请求/签码/换码核心已有，HTTP表单解析、Cookie-only同意与页面、注册管理、标准Token端点、频控/清理保留策略、端到端和剩余业务继续。鱼游说明OAuth入口仍等原用户资料，不自动复用新v3授权。已有API.md业务更新仍需同步 https://fishpi.cn/article/1636516552191。

### 2026-10-19 OAuth HTTP 与授权页

新增四个 OAuth 路由：authorize/consent/token/revoke，已在 ApiV3Processor 注册。授权只使用 Sessions.currentCookieUser；全局 Sessions 可能已由 apiKey 提升，不能作为页面授权身份。旧 currentUser 保留 query/body apiKey 优先级。表单只收 ASCII 序列化的 UTF-8 百分号编码、字段白名单；GET 拒绝 body，POST 拒绝 query。PC/mobile 授权页无脚本，逐字段转义。独立预算、精确来源/CSRF、登记回调 CORS、no-store 与禁止嵌入均已接入。Token 为 snake_case 直接 JSON，刷新只缩小权限，access/refresh 均可撤销其家族。

框架预解析错误日志可能含凭据。新增 OAuthTransportFilter 和 log4j2.xml 的 Request/ServerHandler 配置，注册路由前检查安装，必须完整重启服务加载。维护任务有界清理过保留期请求及码，仍有有效家族的已消费码保留供重放撤销。合法凭据作为 token/revoke 端点登录证明的策略尚待用户确认；当前保持 anonymous 中间件，没有新增 viewSkips。CredentialQueryService 仅准备只读证明，未接 HTTP。应用登记管理尚未实现，使用明确预登记测试应用。

实际修改文件（路径相对项目根目录）：

- processor/ApiV3OAuthProcessor.java、ApiV3Processor.java（均位于 src/main/java/org/b3log/symphony）。
- 同层 util/Sessions.java、ApiV3OAuthForms.java、ApiV3OAuthRateLimit.java、ApiV3OAuthTransportLogFilter.java。
- 同层 service/ApiV3OAuthCorsService.java、ApiV3OAuthMaintenanceService.java、ApiV3OAuthCredentialQueryService.java、ApiV3TokenMgmtService.java、ApiV3AuthorizationCodeService.java。
- 同层 repository/ApiV3ClientRedirectRepository.java。
- src/main/resources/log4j2.xml、scss/oauth-consent.scss、css/oauth-consent.css、skins/classic/pc/verify/oauth-consent.ftl、skins/classic/mobile/verify/oauth-consent.ftl。
- src/test/java/org/b3log/symphony/repository/ApiV3OAuthHttpTest.java、ApiV3OAuthMaintenanceTest.java、ApiV3OAuthCredentialQueryTest.java、ApiV3AuthorizationCodeTest.java、ApiV3AuthorizationRequestTest.java；同层 util/ApiV3OAuthTransportLogTest.java、ApiV3OAuthTemplateTest.java。
- tools/api-v3-chat-event-test.mjs、api-v3-oauth-cookie-test.mjs、api-v3-oauth-smoke.mjs；API.md、UPDATE_LOG.md、AGENTS.md、本计划和 inventory 设计说明。

验证：Java25 临时独立 runner（非 Maven）69 组通过，实际 Cookie 方法抽取专项另 1 组通过。实际控制器、Token/code/request 核心、SQL参与；SQLite 与显式框架适配不能当作真实 MySQL/完整 IoC 证明。覆盖授权换码/刷新/撤销、来源/CSRF、参数与身份、重放、scope 收缩、提交故障；真实 Latke parseForm 与 Log4j 过滤配置通过；清理回滚及家族保留、真实 FreeMarker 两端转义通过。Yarn build 成功，版本审计168文件0命中。IAB仅查看18765离线模板：PC及390px移动布局截图、恶意名称文字显示、详情展开通过，未登录真实站点或提交授权。

人工完整链路（均需重启后执行）：

1. 备份并应用 sql/20261019_api_v3_oauth.sql（三表与 repository.json 配套），确认其他 v3 迁移已完成。IDEA 重编译后完整重启，加载 Java/FTL/log4j2.xml，确认过滤器启动检查通过。登记专用测试 client、scope 和精确 URI。
2. 未登录浏览器打开 authorize，看到登录引导；登录后 PC/移动均显示正确应用、账号和权限。拒绝返回303的 access_denied/state，无 code；新请求允许返回 code/state。检查原查询、Unicode state、原生 scheme、开发回环。错误 URI/端口/片段不能重定向。
3. code+原 verifier+client+完整 URI 换码，直接 JSON 含 Bearer 和有效期；错误绑定不消费码。正确证明重放已消费码后家族失效。access 调允许业务接口成功，缺 scope 拒绝。刷新轮换且只能缩权；重用旧 refresh 家族失效；撤销 access/refresh 后资源拒绝，重复/未知撤销均200空JSON。
4. Cookie A 加 apiKey/Bearer B，授权必须仍归属 A；无 Cookie 不能同意。跨来源、错误/空 CSRF、错误 proof/owner、过期及重复决定拒绝。JSON、重复/未知参数、GET body、POST query 拒绝。恶意名称/URI 不执行脚本，HTML/网络/日志不出现敏感资料或原凭据。
5. 生产配置检查匿名新 IP、黑名单 /test、validateCaptcha 解封和 viewSkips；开发关闭风控不能作为线上证明。当前合法后端新 IP 仍可能被验证码拦截，策略待答复。CORS 只准登记 origin，伪前缀/null/错端口拒绝；生产 HTTP 回环拒绝。
6. 真实 MySQL 并发决定/换码/刷新、commit 故障、清理与重启需验证：没有半对 Token 或错误消费状态，活跃家族保留重放检测。缺日志过滤器应启动失败，畸形 query/form 不泄露测试凭据且其他日志保留。
7. tools/api-v3-oauth-smoke.mjs 需确认重启并设置 API_V3_SERVER_RESTARTED=1；默认只发非法请求、不自动授权。EXCHANGE/REFRESH/REVOKE 需显式开启并使用人工授权的临时测试码，不打印凭据，不并行消费同一码。

反漏扫：authorize/token/revoke 接匿名校验，consent 明确 Cookie 登录；未修改黑名单/Captcha解封。输入和响应白名单、无回调网络请求、模板转义、来源检查、事务防重和独立预算已实现；真实应用风控/并发尚未验证。新增自产 CSS，无新 CDN URL/sourceMappingURL 或上传下载返回路径；既有线上目录枚举/CDN/上传路径未运行验证，不能宣称全站审计通过。未执行 Maven、8080 HTTP、应用迁移或 target 同步。目标仍 active：登记管理、风控策略、端到端及其他 v3 业务继续；鱼游 OAuth 说明规则仍等用户资料。API.md 需同步 https://fishpi.cn/article/1636516552191。

### 2026-10-19 已批准应用登记事务核心

上一轮 OAuth HTTP、模板、文档和验证为实质进展。本轮不缩减完整目标，继续补齐第三方登记依赖。登记管理选择（仅管理员维护，或开发者提交后管理员审核）已提问，尚无答复；没有假定超时代表同意，没有新增公开登记路由。

实际文件：src/main/java/org/b3log/symphony/service/ApiV3ClientRegistrationService.java；src/test/java/org/b3log/symphony/repository/ApiV3ClientRegistrationTest.java；tools/api-v3-chat-event-test.mjs；AGENTS.md、本计划及inventory设计说明。内部 createApprovedInCurrentTransaction 只接收已经审批的应用信息，不负责身份、审批、幂等及根事务。服务端生成 app_ 加随机公开ID、数字行ID；应用名沿既有128单位存储/同意页边界，strip并拒绝控制字符与非法Unicode；scope限 DEFAULT_SCOPES 已实现权限、排序去重；完整回调按已确认HTTPS/开发回环/原生scheme规则校验，不做网络访问，不接受重复回调。客户端与全部回调在同一保存点写入，任何失败整体撤回。记录只含公开登记字段，回调列表不可变。无新表，无前端改动，无公开接口/响应变更，因此没有新增API.md/UPDATE_LOG发布记录。

验证：node tools/api-v3-chat-event-test.mjs 已以Java25临时独立编译、退出0，72组PASS（原69加登记3组）；未运行Maven。登记测试实际调用新服务、原授权码与Token核心，证明新登记应用可完成S256换码；覆盖名称/Unicode/scope、回调精确类型与开发/生产边界、重复回调、客户端插入后失败、回调插入后失败、保存点释放与strict commit故障、外层捕获失败后继续commit不遗留半条登记、无根事务拒绝。持久化基础显式适配SQLite，不能当作MySQL并发证据。路由清单重新生成到/tmp/rhythm-v3-registration-route-inventory.json，仍701条路由、398引用。

后续人工全链路：先迁移并由用户IDEA完整重启；未来登记入口需管理员/审批授权验证，通过入口创建包含HTTPS/原生回调的测试应用；核对应用、权限、全部回调一次保存成功，再用浏览器authorize→明确同意→S256换码→资源请求→刷新→撤销验证。异常需含未授权登记、未知scope、错误/重复URI、生产HTTP回环、存储失败及commit失败，确认没有半条登记。管理HTTP尚未接线，入口权限、CSRF、幂等、用户反馈与真实MySQL竞争仍未验证；本轮没有假装通过。Java更改仍需IDEA重编译/重启，未访问8080/应用数据库。

反漏扫：没有新增公网路由、匿名豁免、CDN或上传下载返回路径，既有黑名单/验证码链路不变；登记核心URL只解析不获取，无SSRF；SQL写入由参数绑定的Repository完成且保存点同事务；凭据未生成，public client_id不是secret。不提供HTTP身份边界的primitive不能裸露。全目标仍active：登记入口策略、OAuth合法凭据风控选择、账号注册/恢复、红包、支付跳转、其他未覆盖能力及运行验收继续；鱼游说明OAuth规则仍等用户资料。已有API.md仍需同步网站文章。

### 2026-10-19 注册选项与绑定账号的短信验证

上一轮已批准应用登记核心及72组验证为实质进展。本轮继续完整账号链路，未把登记权限选择视为全局阻塞，也没有将全量目标缩减为只读接口。

新增 GET /api/v3/auth/registration-options，由 ApiV3AccountProcessor 注册并显式经过 anonymous 中间件；处理器不继承 Cookie/apiKey 身份。ApiV3RegistrationPolicyService 直接投影查询注册开关，不从 OptionCache 推断开放状态；0/open、1/closed、2/invitation，缺失或未知配置503。邀请人按共用 UserNameRules 校验、查询既有邀请链接权限，关闭优先；响应只含 mode、enabled、inviteCodeRequired、referralAccepted、username 与 appRoles，不返回邀请人对象。权限查询沿现有角色查询服务，此读取仅展示，不是后续写入授权，最终写入必须在事务中复核注册开关/邀请码/邀请资格。无短信发送、验证、账号激活或Token发行副作用。

读清旧链路发现：POST /register2 的middleware只校验注册开关、角色及密码长度，控制器仅凭userId尝试激活，没有短信证明；POST /reset-pwd虽检查code所属userId，但没有用途/到期绑定，且改密码和删除码分开提交。已按AGENTS兼容要求提问是否让旧register2必传code并让新旧入口共用绑定校验和原子消费；未答复前没有改旧请求、响应、角色/奖励或旧控制器。公开API.md/UPDATE_LOG不描述这些安全实现问题。

新增 AccountVerificationService 与 VerifycodeRepository#phoneCodesForUpdate 供后续账号mutation核心使用：调用方先取得完整namespace/积分/客户端锁集合并开启根事务，核心当前读锁用户，再按当前手机号、PHONE类型、REGISTER/RESET_PWD用途、6位ASCII码、合法状态与expired>now锁定记录。注册仅允许NOT_VERIFIED、重置仅允许VALID。成功消费所有同用户同用途同值的有效重复码，防止残余重复记录再次使用；同保存点加入外层业务事务，不独立提交。错误账户/手机号/类型/用途/过期码不消费；任何删除失败、外层业务失败或commit失败恢复码。此primitive无HTTP入口，不含尝试预算，公开写入口须接共享预算；不支持用全局getVerifycode(code)回退，不允许只把查到码当成登录凭据。

实际文件：
- src/main/java/org/b3log/symphony/processor/ApiV3AccountProcessor.java、ApiV3Processor.java。
- src/main/java/org/b3log/symphony/service/ApiV3RegistrationPolicyService.java、AccountVerificationService.java。
- src/main/java/org/b3log/symphony/repository/VerifycodeRepository.java。
- src/test/java/org/b3log/symphony/service/ApiV3RegistrationPolicyTest.java、repository/AccountVerificationTest.java。
- tools/api-v3-account-test.mjs、api-v3-chat-event-test.mjs；AGENTS.md、API.md、UPDATE_LOG.md、本计划与inventory设计说明。

验证：tools/api-v3-account-test.mjs 以Java25临时独立编译并运行4组，通过真实策略服务/控制器、真实Request查询解码、实际用户名/模型常量及从当前ApiV3Processor抽取的原信封方法；设置/用户/权限/全局anonymous均为明示适配。覆盖三模式、关闭优先、邀请权限撤回/不存在、长度字符/保留名、缺失及未知配置、响应白名单、私密邀请人字段不外泄、Cookie/apiKey不授予注册权、异常信封及no-store/request ID。tools/api-v3-chat-event-test.mjs 终态exit0、77组PASS（原72加账号验证5），其中真实消费服务与绑定SQL参与，基础持久化适配SQLite；覆盖跨用户/手机号/用途/类型、重复码一次消费、当前账户状态、严格到期边界、第一/第二删除及保存点/strict commit故障、owner捕获失败继续commit不漏消费、owner回滚、不支持用途/输入及无根事务拒绝。未运行Maven、真实MySQL、应用HTTP或短信。路由清单重新生成/tmp/rhythm-v3-account-route-inventory.md：702条（HTTP692、WS10），398浏览器引用。没有改FTL/JS/SCSS/CSS，不需要前端build。

人工完整链路（尚未执行）：
1. IDEA重编译并重启服务端；OAuth日志配置仍需完整重启。先在开发环境GET registration-options，不带referral应得到当前开放/邀请/关闭状态、角色0/1与用户名1..64；PC/移动客户端使用同一JSON契约。
2. 管理员依次切换注册开关，分别GET并核对open无需邀请码、invitation需邀请码、closed不可注册。传入有邀请权限用户应referralAccepted=true且免邀请码；不存在/无权限为false；关闭时有效邀请人也不能放行。撤回邀请权限后再查；各后端实例配置读取也应更新，不把展示结果当写入凭据。
3. 输入非法用户名、超64长度、斜线、控制符及保留名应422/data=null，空/未传按无邀请人；缺失/未知配置503，存储失败通用错误且不返回内部信息。检查响应没有手机号、密码、apiKey、权限对象或当前Cookie用户资料。
4. 生产风控配置复查匿名新IP和每5次访问、黑名单/test、validateCaptcha解封及viewSkips；开发关闭盾不能作为线上通过证据。带Cookie或query/body apiKey不能赋予新账号入口写权限；当前新接口无写副作用，CSRF不会发送短信或激活账号。
5. 后续账号写入口接入前，须完成短信发送、共享尝试预算、邀请码占用/消费、注册开关事务复核、当前用户/手机号绑定、激活/初始积分/邀请奖励/通知原子核心，以及密码重置与凭据失效策略；通过入口→发送→验证→设置密码→登录→资源读取的真实链路，再测试错码/过期/跨用途/重复/改绑手机号/并发与commit失败。旧入口接入待用户确认，现阶段不能把内部消费测试当作公开注册/恢复已完成。

反漏扫：新唯一公网入口是GET registration-options，已显式anonymous，不改viewSkips/黑名单/Captcha链路。输入只用于用户名格式校验和既有查询，注册开关SQL参数绑定；无URL请求，无SSRF，无上传下载路径或新静态/CDN资源。DTO无HTML/敏感字段，读取无扣费及写入，Cookie/apiKey不作注册证明；消耗码primitive仅内部同事务，外部限额/真实MySQL竞争尚待接线验证。静态版本审计本轮无需重跑（没有静态资源改动），线上CDN、目录枚举、上传/下载路径与真实风控未运行，不默认通过。Java更改待用户IDEA重编译/重启；没有访问8080或同步target。

全目标保持active：账号公开写流程、登记管理策略及HTTP入口、OAuth合法凭据风控选择、鱼游说明OAuth、聊天室红包、私信专用认证/读写/实时、支付受控跳转、其他未覆盖能力及全量运行验证均不能因本批通过而删减。API.md新增注册选项契约已配套UPDATE_LOG，仍需同步 https://fishpi.cn/article/1636516552191。


### 2026-10-19 密码重置完成与旧入口接线

上一轮已实际实现重置核心与隔离测试，属于进展。本轮以工作区源码及终态测试日志复核，并完成公开文档和最新回归。用户已明确同意新旧 register2/reset-pwd 一起加固：register2 将新增 code，PC/移动同步调整，正常角色、奖励及成功字段保留。不再重复询问；本批仅 reset-pwd 已接入，register2 仍未接线，不能将同意视为实现证据。

新增 POST /api/v3/auth/password-reset/complete，由 ApiV3AccountProcessor 经 anonymous::handle 调用 AccountPasswordResetService。请求只接受有界 typed JSON 的 userPhone/code/userPassword，字段分别为11位手机号、6位ASCII数字、32位小写MD5；拒绝query及未知字段，不继承Cookie、apiKey或Bearer身份，也不自动登录。账号凭短信证明修改密码，不把可变凭据写入业务幂等表；不支持凭已消费短信回放明文或成功回执。成功只返回changed:true，错误证明统一invalid_verification，存储或提交失败以不含凭据的503返回。

新旧入口共享原有 PasswordResetQuota：复用 LoginProcessor.resetCodeLimiter 并同步计数，保留原业务预算；旧GET重置页亦使用同一对象。旧POST/reset-pwd仍接收显式userId/code/userPassword，不能回退用另一个手机号所有者；保留原正常密码长度契约和成功后的Sessions.login，仅严格提交后登录。日志不再输出注册短信验证码或注册限额分支中的密码摘要。原有注册激活及奖励链本批未宣称原子化。

AccountPasswordResetService 在根严格事务中先锁当前所有client，再当前读目标账号/手机号/状态，调用绑定RESET_PWD用途及有效期的消费核心，白名单更新密码、删除该账号待决授权请求/未消费授权码、撤销该账号全部client的ACTIVE v3 Token。已消费授权码证据保留，其他用户凭据保持。全部写入同事务，任何中途或commit失败恢复短信与密码。线程根结束后才失效用户缓存，包括失败/提交结果不明时；缓存故障不在活跃事务内重读。全client锁避免与client→账号→code/token路径倒序；MySQL锁等待、跨进程竞争及新增client竞争仍须实际验证，不能宣称无死锁或所有正在执行请求立即中止。旧apiKey/openid并非v3 Token，本批没有撤销它们。

没有新增表；旧重置现在也依赖既有v3 Token/OAuth表，部署必须先完成配套迁移。ApiV3OAuthTransportLogFilter 扩展为保护auth及旧reset-pwd/register2的框架预解析错误，避免框架先于业务处理记录凭据；路由注册检查过滤器安装。必须完整重启加载现有log4j2.xml，热替换Java不等于配置已加载。没有改变旧openid、anonymous.viewSkips或验证码解封策略。

实际修改文件：
- `src/main/java/org/b3log/symphony/processor/ApiV3AccountProcessor.java`、`LoginProcessor.java`：新完成入口、旧完成适配、共享预算、去除两处凭据日志。
- `src/main/java/org/b3log/symphony/service/AccountPasswordResetService.java`：新旧严格重置根及提交后缓存边界。
- `src/main/java/org/b3log/symphony/repository/ApiV3ClientRepository.java`、`ApiV3TokenRepository.java`、`ApiV3AuthorizationRequestRepository.java`、`ApiV3AuthorizationCodeRepository.java`：锁及绑定owner的批量清理/撤销。
- `src/main/java/org/b3log/symphony/util/ApiV3TransactionStatements.java`、`PasswordResetQuota.java`、`ApiV3OAuthTransportLogFilter.java`：当前连接写入、共享计数与日志保护。
- `src/test/java/org/b3log/symphony/repository/AccountPasswordResetTest.java`、`src/test/java/org/b3log/symphony/util/ApiV3OAuthTransportLogTest.java`、`tools/api-v3-chat-event-test.mjs`、`tools/api-v3-account-test.mjs`：SQL/事务/适配入口及过滤器回归。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-v3-inventory-and-design.md` 与本计划：契约、兼容提示、架构边界及验证记录。

验证：专用账号脚本4组已基于最新源码复跑通过；共用事件/OAuth/账号脚本82组通过（此前77+重置5组）。重置专项执行真实核心、repo SQL、严格事务和现有请求解析，以SQLite仓储/缓存/登录适配器覆盖跨client与250条额外凭据撤销、他人数据保持、全部中途及commit故障、有效期边界/错误用途/身份/手机号/重放、未知字段/非字符串/media/query/体积/预算及原有并发预算。旧reset实际方法从源码提取编译并执行；Sessions.login为替代实现，验证提交后调用但不证明Cookie发行。SQLite不证明MySQL FOR UPDATE、完整LoginProcessor的IoC/类型编译、真实缓存、短信或全局dispatcher/风控。路由盘点703（HTTP693+WS10）、浏览器引用398。未运行Maven、真实迁移、target同步、HTTP或浏览器；无本批JS/FTL/SCSS改动，不需新增前端构建。Java生效仍未收到用户IDEA重编译/重启确认。

源码安全/反漏扫结论：新匿名入口显式挂AnonymousViewCheckMidware，未加入viewSkips；BeforeRequestHandler黑名单/test、首访/周期验证码及validateCaptcha解封未修改，运行效果待重启后验收。接口须提供当前绑定短信证明，无法仅用Cookie/apiKey/userId改他人密码；并用共享预算限制尝试。重置属于凭据交换，不凭Cookie授权，不能给它追加破坏正常无登录恢复的Cookie CSRF前置。JSON字符串有界并用固定格式，SQL值绑定且内部selector固定，不获取远程URL；返回白名单、不返回账号对象/手机号/验证码/密码/Token。没有新HTML、上传下载路径、CDN/静态资源、版本号、sourceMappingURL或目录枚举入口。本批未证明分布式限额、真实负载或浏览器跨站行为通过。

人工验收（必须先迁移并经IDEA完整重启，不能用旧进程验新源码）：
1. 使用专用测试账号走旧忘记密码入口取得找回短信；PC、移动分别进入重置页，输入新密码提交，预期code=0且自动登录。退出后旧密码失败、新密码可登录，用户名/角色/积分保持。
2. 为同一测试账号预先在website_v3及两个登记应用签发access/refresh，并准备尚未消费授权码、待决同意请求；另建他人有效凭据。再申请新短信，以v3完成入口提交手机号/验证码/小写MD5，预期统一信封changed=true且没有自动登录Cookie。重置前凭据及待决授权均不能继续使用，他人凭据仍有效；重新登录/授权可正常调用本人资源。
3. 分别尝试别人的userId/手机号、注册用途码、过期码、错误码、同一成功码重试，以及签码后手机号变化；均不能修改密码。输入空字段/非字符串/超长体/错误媒体类型/查询参数应返回受控错误，不在响应及应用/框架日志出现密码摘要或短信值。旧API客户端保留userId字段，新客户端传手机号，不混用两套请求。
4. 交替调用旧页、旧POST与v3完成入口验证共享预算；并发提交同一有效码只允许一次改密，失败请求不吞码。分别注入消费、改密、授权清理、Token撤销和commit故障，检查全部回滚及缓存随后读已提交状态；取消故障后原有效码仍可使用。提交确认丢失时不能假设失败，可尝试新密码登录或重新申请码。
5. 未登录新IP、黑名单IP及通过/test验证码后分别调用新入口，确认全局拦截与解封仍有效；再验开发模式关闭风控。真实MySQL两个后端并发OAuth换码/刷新与重置，观察无部分改密/发凭据，死锁须整笔失败可重新申请证明，不把SQLite串行适配器当并发结论。
6. 注册第二步新增code、两端模板/verify.js及激活/奖励同事务仍是下一批必做；本批不要依此对外宣称注册修复完成。账号短信申请v3入口、应用管理、私信、红包、支付及其他全目标剩余项照旧保留。API.md需同步 https://fishpi.cn/article/1636516552191。


### 2026-10-19 注册完成、旧激活与双端证明字段

上一批密码重置核心/HTTP/文档均已落地，是实际进展；本批继续全量目标中的账号注册写入，没有将注册选项或内部证明消费当成注册完成。新增 POST /api/v3/auth/registration/complete，经anonymous::handle，纯JSON最多4096字节，只允许userPhone/code/userPassword/userAppRole/mbti/referral。手机号11位、code六位ASCII、密码32位小写MD5；角色JSON整数0/1默认0，MBTI最多32并沿用SettingsProcessor.checkMBTI规范化，referral最多64并按用户名规则检查。不接收query/userId/旧apiKey授权，不自动登录，成功仅userId/registered。短信申请仍需已有入口，v3申请短信尚未实现，不能宣称完整v3注册首步完成。

AccountRegistrationService 复用已准备的绑定短信消费，禁止套旧UserMgmtService.addUser（它内部自提交并随后分别奖励）。先只读投影actor/邀请人/保留邀请码生成者，取得完整sys→所有收款人账户对，再开严格根与ArticlePublicationCaches边界：namespace统计option→当前注册开关→按ID锁全部用户及同名记录→邀请码→当前手机号/REGISTER用途/有效期证明。开关关闭拒绝；邀请模式沿用旧第二步对已发证明的正常策略，不新增一套邀请资格判定。事务内重验预投影名称、邀请人和邀请码生成者/状态，变化409，不临时扩展账户对。账号只改密码/角色/状态/MBTI；已有积分及其他资料不重置。激活、待验证重名账号清名称/邮箱、初始积分、邀请链接双方奖励、邀请码消费/生成者奖励与三类通知记录同提交。转账返回空视为整笔失败；用户状态锁与已用码阻止二次发奖。没有新增表/Repository。

AccountRegistrationEffectsService 在确认提交及缓存边界结束后才索引转账、刷新通知、更新公开名字/头像列表，并启动旧天降红包匹配（昨天活跃度计算、100%条件及1..24积分保持）。后台结束显式释放JDBC资源；这些为提交后的best-effort，不把邮件/推送/后台匹配宣称成事务内或可靠outbox。delivery失败不把已激活账号伪报为事务失败。当前第一方名字缓存的全局加载/改名竞争仍未用真实多线程/多实例验证。

旧POST/register2已获用户同意新增code并共用根：显式userId、当前手机注册证明；正常角色、旧密码长度、MBTI规则、原奖励金额及成功后Cookie登录保留，Cookie仅严格提交后建立。旧verify/showRegister/register2与v3共享RegistrationVerificationQuota，直接复用原verifyCodeLimiter并同步访问，没有新增业务预算。showRegister仅把六位code送入模型，PC/mobile注册模板隐藏字段HTML编码，verify.js传code并对两处邀请人query使用encodeURIComponent；生成verify.min.js由yarn build产出，未直接修改min文件。旧注册第一步短信/待验证账号及邀请码预留仍原链，尚未原子改造，不能以本批完成入口为它背书。

实际文件：
- `src/main/java/org/b3log/symphony/service/AccountRegistrationService.java`、`AccountRegistrationEffectsService.java`：新旧共享激活严格事务及提交后效果。
- `src/main/java/org/b3log/symphony/service/NotificationMgmtService.java`：只接受注册三类通知的当前事务写入，不抢先发布刷新。
- `src/main/java/org/b3log/symphony/processor/ApiV3AccountProcessor.java`、`LoginProcessor.java`：新完成路由、旧适配、证明模型及共享预算，删除已移走的旧奖励/红包逻辑与无用依赖。
- `src/main/java/org/b3log/symphony/util/RegistrationVerificationQuota.java`：原预算并发原子访问。
- `src/main/resources/skins/classic/pc/verify/register2.ftl`、`src/main/resources/skins/classic/mobile/verify/register2.ftl`、`src/main/resources/js/verify.js`、`verify.min.js`：两端证明字段、源码请求和构建产物。
- `src/test/java/org/b3log/symphony/repository/AccountRegistrationTest.java`、`src/test/java/org/b3log/symphony/util/AccountRegistrationTemplateTest.java`、`tools/api-v3-chat-event-test.mjs`、`tools/api-v3-account-test.mjs`、`tools/account-registration-fixture.mjs`：SQL/事务/入口/效果、FTL、共享预算及真实浏览器隔离页面。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-v3-inventory-and-design.md`及本计划：新契约、旧code迁移和限制。路由盘点704（HTTP694、WS10）、398浏览器引用。

验证结果：共用Java25隔离runner91组（原82+注册8+模板1）通过，注册选项独立4组通过。注册测试实际执行核心/SQL/当前连接写入/证明/严格提交与提取的旧register2方法，以及真实v3处理器与框架JSON/query解析；数据库为SQLite，积分/缓存/全局dispatcher及部分依赖为显式适配器。覆盖正常完整奖励、无邀请奖励、sys生成者、邀请模式、保留余额/待验证重名清理；当前账户/手机/用途/有效期/注册开关/名字占用；激活/清重名/消费/每项转账/通知/邀请码UPDATE拒绝/commit故障整笔回滚；事务前后绑定变化（含原不存在邀请人随后出现）、重复提交、未知/query/apiKey/字段类型/体积/media/限额、旧缺code拒绝及提交后登录、64线程原预算，提交后效果顺序及公开名字DTO。失败重试保留证明；Cookie发行是stub，不声称实际Session已验证。模板测试解析完整两端FTL语法并实际渲染隐藏片段的正常/缺失/恶意值；没有渲染完整应用宏/页面。

COREPACK_ENABLE_AUTO_PIN=0 corepack yarn@1.22.22 run build成功（无安装），源码/生成JS及两个工具node --check与git diff --check通过。tools/js-version-audit.mjs按src/main/resources/js扫描155个文件，0项版本/sourceMappingURL命中（报告/tmp/rhythm-registration-js-audit.json）。浏览器实际打开127.0.0.1:18769的隔离fixture：PC/移动×源码/生成JS四项PASS，验证MD5、证明、角色、MBTI、邀请query编码、前端校验拦截和失败反馈，AJAX全部拦截且CSP禁止连接，没有发注册请求或更改真实密码。临时fixture服务已终止。没有访问8080、同步target/classes、执行Maven/应用迁移、真实短信、MySQL并发、完整IoC或后台活跃度红包；仍未收到用户IDEA重编译/完整重启确认。

安全与反漏扫：新增匿名入口显式接AnonymousViewCheckMidware，未加viewSkips；BeforeRequestHandler黑名单/test、验证码解封及开发风控开关均未修改，实际首访/周期验证待运行。源码复核另发现旧POST/register2和POST/reset-pwd原路由只经过全局黑名单而没有anonymous中间件；为符合改造匿名入口基线，已就新增统一验证会令未完成验证的旧客户端跳/test的兼容影响另向用户提问，未答复前维持原route，不将旧完整反漏扫链报告通过。该问题不影响已获同意的绑定证明/事务/code字段实施。

接口不依赖Cookie/apiKey当前身份授权激活，只认绑定短信；严格JSON和非查询证明避免凭环境Cookie代替凭据。SQL值参数化、内部selector限定、字段白名单；用户名和证明格式有界，MBTI按既有规则，公开响应不含完整用户、手机、密码或短信。无新URL抓取/SSRF、上传/下载路径、CDN资源或可枚举目录；新隐藏字段有编码，邀请query编码并保持旧正常请求；静态版本/source map扫描如上。尝试预算复用旧规则，不证明分布式抗滥用。旧页完整手机号输出仍为用户知情存量项，本批不宣称修复。

人工完整链路（先IDEA重编译/完整重启并确认最新Java/FTL资源；必要时同步target/classes对应资源）：
1. PC与移动分别从/register进入，填写用户名/手机号与图形验证码及所需邀请码/邀请链接，申请并收到注册短信；通过验证码进入第二步，设置密码、角色、MBTI。网络请求/register2必须带userId+code；正常code=0且登录，刷新资料核对用户名/角色/MBTI。用默认头像与正常自生成头像两种账号，确认都能激活。
2. 在专用测试数据库准备无邀请、有效邀请链接、有效邀请码、二者同时存在、sys生成者等账号。按原业务金额核对初始奖励、双方邀请奖励、生成者奖励和通知各一次；已有余额不丢失，邀请码状态仅成功后改已用。待验证重名账号被清名称/邮箱，已激活/封禁等占用用户名不能抢占；第一方名字提示可见新账号。
3. 用收到注册短信的手机号调用v3完成入口，预期统一信封userId/registered，不自动发登录Cookie。再走v3登录→本人资料/有权限资源，确认角色及积分。对Cookie/apiKey/body-userId注入均不能替代短信，跨用户/改绑电话/重置用途/过期/错码不激活；旧客户端不传code明确失败，补传有效code正常成功。
4. 在证明发出后关闭注册、改变用户名/邀请码生成者或占用状态，再提交应拒绝且不吞有效码。新旧混合并发同一码只发奖一次；中途与commit故障恢复密码/状态/短信/积分/邀请码/通知。成功响应丢失时先尝试登录；已激活/消费码再次提交不重复发奖或天降红包任务。
5. 查看新用户和邀请人的通知、转账索引与在线用户天降红包（昨天100%活跃度、1..24范围），确认仅激活提交后开始；后台故障不回滚已成功注册，日志不含密码/短信。MySQL跨后端注册/改名/转账/邀请码竞争、资源释放与负载需专门运行，SQLite不能替代。
6. 新IP/黑名单IP直接请求v3完成入口、通过/test后重试、开发风控关闭分别回归；旧两个入口统一匿名验证待兼容回复后才接线并验证，不能以本批注册核心PASS视作旧首访链已通过。恶意JSON/类型/超长/referral/XSS/批量请求必须返回受控错误，无敏感输出。旧首次短信申请的奖励预留和验证码元数据读取仍需后续复核。

全目标继续active：v3注册/恢复短信申请、应用管理策略/HTTP、OAuth凭据端点风控选择、鱼游OAuth说明校验、私信专用范围/读写/实时、红包、支付及其他能力和全量运行验收均保留。API.md/UPDATE_LOG已同步本批契约，网站文章 https://fishpi.cn/article/1636516552191 仍需用户同步。


### 2026-10-19 旧账号提交匿名盾与v3找回短信

上批注册激活严格事务、双端证明字段及91+4隔离测试/浏览器fixture均已落地，属实际进展。本轮用户明确同意旧POST/register2、POST/reset-pwd一起接统一匿名验证；LoginProcessor注册路由现从BeanManager取得AnonymousViewCheckMidware，两个提交都先anonymous::handle，再保留旧register2业务校验。没有重问、加入viewSkips、修改黑名单或验证码解封。公开文档只写客户端收到302须按Location完成页面交互再重提原请求，不写内部风控策略/阈值。前批该兼容待确认项现已关闭，但实际进程的首访/黑名单/解封回归仍未执行。

新增GET /api/v3/auth/captcha-options，经anonymous，AccountCaptchaService仅输出provider=geetest4及已有公开jiyan.id；缺失/非法配置受控503，不输出服务端key/domain或完整配置。新增POST /api/v3/auth/password-reset/request，同样anonymous，最多8192字节typed JSON，只接受userPhone和captcha字符串。手机号11位ASCII数字，captcha最多4096 UTF-8字节，严格四字段lot_number/captcha_output/pass_token/gen_time、非空字符串及单值长度/控制字符检查；拒绝未知键/重复键/查询凭据，不凭Cookie/apiKey/Bearer身份。复用现有CaptchaProcessor.jiyan，不绕过其服务端验证，不让请求控制验证URL。

AccountPasswordResetRequestService先投影最多两个当前手机号所有者，按现有可用状态判断，取得共享SMS额度，再做极验校验。验证通过后的公开回执只含accepted:true；缺失/失效/歧义账号无发送。服务商拒绝或校验后的存储异常也不通过不同错误回执暴露目标资格，记录不含号码/验证码/驱动原文的通用告警；校验前存储不可用503、限额429、校验无效422仍受控返回。accepted不是验证码或送达证明，不自动登录/改密/撤销凭据。这里没有做恒定时延或可靠异步队列，网络时间差不在隔离测试中证明消除，不能宣称完全防账号推断。

码由SecureRandom生成六位数字，远程发送期间不持数据库事务/行锁；确认厂商受理后才开严格根，当前锁目标ID重验手机号与有效状态，经VerifycodeRepository.addAccountPhoneCodeInCurrentTransaction固定字段参数化INSERT保存绑定PHONE/RESET_PWD/SENT码，沿用一天有效期，不删除旧有效码。发送期间被删除、停用或改手机的账号不落新码。物理短信与SQL不是一个原子事务：拒绝/明确失败没有新码，服务商可能受理而存储失败/回执丢失；短信可能延迟到达或代码未落库。提交确认丢失也不能保证回滚已在数据库确认的提交，不可把重试当幂等发送或自动反复耗用预算。已保存证明可接真实AccountPasswordResetService完成消费/改密/v3凭据失效。

AccountSmsQuota在旧注册/找回/绑手机和新v3申请之间共用原四个Limiter对象、预算及短路顺序，只增加同步访问，避免入口换路或并发取得另一预算；没有新配置/业务阈值。SettingsProcessor的绑手机只替换额度包装，不改变成功字段、既有码重用或验证码10分钟规则。旧register/forget-pwd本身仍有待验证账号/邀请码预留/发送与存储分离的历史链路，本批不宣称它们整体原子化。

VerifycodeMgmtService增加账号专用sendAccountVerifyCodeSMS：输入格式固定；生产腾讯发送单个+86目标，必须一项匹配号码且Code=Ok才算厂商受理；短信宝沿用原成功判定。通过requestTencentSms共用SDK请求构造，旧sendMsgTencentCloud仍保留原非空响应返回契约，不暗改旧客户端错误分支。生产日志不再输出手机号/短信/厂商完整响应，开发模式原有短信调试输出保留。腾讯字段依据官方[SendStatus源码](https://github.com/TencentCloud/tencentcloud-sdk-java/blob/master/src/main/java/com/tencentcloudapi/sms/v20210111/models/SendStatus.java)与[SendSmsResponse源码](https://github.com/TencentCloud/tencentcloud-sdk-java/blob/master/src/main/java/com/tencentcloudapi/sms/v20210111/models/SendSmsResponse.java)核对；接受不等于物理送达，真实SDK和生产服务商未调用。

实际文件：
- `src/main/java/org/b3log/symphony/processor/LoginProcessor.java`：已确认旧提交匿名中间件与共享SMS预算。
- `src/main/java/org/b3log/symphony/processor/SettingsProcessor.java`：绑手机SMS预算共用包装。
- `src/main/java/org/b3log/symphony/processor/ApiV3AccountProcessor.java`：组件选项/找回短信两条新路由与typed JSON。
- `src/main/java/org/b3log/symphony/service/AccountCaptchaService.java`、`AccountPasswordResetRequestService.java`：公开组件标识和找回申请/受理/当前绑定严格存储。
- `src/main/java/org/b3log/symphony/service/VerifycodeMgmtService.java`：专用受理检查、共享SDK构造和日志去敏。
- `src/main/java/org/b3log/symphony/repository/VerifycodeRepository.java`：固定字段的当前事务手机证明INSERT，使用参数化当前连接方法，避免框架add路径记录原始驱动异常。
- `src/main/java/org/b3log/symphony/util/AccountCaptchaProof.java`、`AccountSmsQuota.java`：有界证明、共享旧SMS额度。
- `src/test/java/org/b3log/symphony/repository/AccountPasswordResetRequestTest.java`、`src/test/java/org/b3log/symphony/processor/LegacyAccountRouteTest.java`、`tools/api-v3-chat-event-test.mjs`、`tools/api-v3-account-test.mjs`：真实SQL/事务/新入口/受理检查/路由执行顺序及适配器。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-v3-inventory-and-design.md`及本计划：新契约、旧302处理、架构边界与验证。无新表/Repository/SQL迁移/FTL/JS/SCSS/生成产物。

验证：Java25隔离共用runner100组（上批91+找回申请8+路由1）通过，注册选项独立4组以最新处理器源码复跑通过。新增真实申请服务/当前连接SQL/严格提交→真实密码重置服务串联；证明用途/手机号/状态/有效期/一次消费、旧码保留；不存在/停用/歧义回执无敏感字段；配额与极验先于发送；服务商拒绝、查询/插入/commit故障、发送期间改绑/停用/删除；证明JSON未知/重复/类型/长度/控制字符、请求query/媒体/体积/身份注入；64线程原额度、专用SDK结果判定及组件仅公开ID。SQLite、积分/缓存/外部服务与部分模型依赖均显式适配；腾讯模型是按官方接口提供的测试替身，SDK传输未编译/执行，不可称为真实提供商集成测试。完整VerifycodeMgmtService/SettingsProcessor/IoC仍待IDEA编译确认。

LegacyAccountRouteTest提取当前源码两条Dispatcher.post声明，以打包Latke Dispatcher真实注册后检查并执行handler链，覆盖阻止时仅anonymous、允许时reset、register的anonymous→旧业务校验→操作顺序；匿名策略、302发送及最终业务均为适配器，不证明实际Captcha/UserCache/黑名单已在运行进程通过。路由盘点706（696HTTP+10WS），浏览器引用398；源码清单现识别旧两条路由为匿名访问校验。两个node工具语法及git diff --check通过；API.md嵌套captcha curl按shlex和JSON两次解析通过。未运行Maven、应用SQL、真实短信/极验、8080/浏览器、全量IoC或MySQL并发，未同步target。无前端变更，不需重复yarn build；仍未收到IDEA重编译/完整重启确认。

源码安全/反漏扫：新旧四个目标声明都有anonymous::handle且未加入viewSkips。BeforeRequestHandler的黑名单/test、AnonymousViewCheckMidware首访/周期校验/白名单和CaptchaProcessor.validateCaptcha解封没有更改；运行验证未通过不能省略。凭据交换不靠环境Cookie授权，旧成功Cookie只在已授权的原有完成核心提交后建立；新申请只接受有界JSON/手机/极验，不接用户ID/权限字段/SQL片段。所有SQL值绑定、表名内部来源；码不在公开返回/幂等表/生产通用日志，公开组件只读白名单。无新HTML/XSS渲染、上传下载路径、公开静态/CDN/sourceMappingURL/版本目录或目录枚举；新远程调用仅复用配置的固定验证码/SMS服务，号码和码固定格式，不引入用户URL/SSRF。预算本地共享不等于跨节点限流，也不证明全负载安全；旧电话元数据HTML风险仍是用户知情存量项。

人工完整链路（先经IDEA重编译并完整重启以加载Java和凭据日志过滤器；本轮尚未确认，不能看旧进程下结论）：
1. 新IP直接POST/register2、POST/reset-pwd，预期先走Location页面验证且没有激活/扣积分/消费验证码；完成验证后向原接口重提相同JSON，旧正常code=0/登录行为保留。register2仍必须带注册code。关闭注册时，匿名验证放行后旧业务注册开关仍拒绝；测试已登录Cookie、旧apiKey、无Cookie三种情况，身份不能替代绑定短信。
2. GET /api/v3/auth/captcha-options获取公开captchaId，在真实极验4组件完成交互，序列化四个结果字段；POST /api/v3/auth/password-reset/request发送userPhone/captcha，预期统一信封accepted:true。观察专用有效测试账号收到短信；不可用/不存在账号得到同一回执且不发码。确认组件接口及响应HTML/日志不含验证key、短信值或完整用户资料。
3. 收到短信后POST /api/v3/auth/password-reset/complete提交手机号/码/新密码MD5，预期changed:true且无自动登录。用新密码登录→本人资料/授权资源，旧密码及重置前v3凭据失效；过期、错码、跨用途/跨手机/已用码拒绝。首次找回申请不应改密码、撤销Token或覆盖既有有效码。
4. 阻止服务商/模拟返回错误号码、失败Code、空/多项回执，受理DTO仍不表明账号存在，服务端通用告警且无新有效码；注入查询/写入/提交失败，验证无部分证明或错误手机号绑定。发送中停用/改绑/删账号，不给变化后的账号签新证明。提交确认丢失/短信超时不自动反复请求；读现状并按新码完成或由用户重新申请。
5. 交替旧注册/找回/绑手机和新申请、并发多请求及更换IP/用户名/手机维度，核对仍共用原额度与正常成功字段。实际测试Tencent/Bao配置、开发模拟发送与极验不可用行为；SDK受理和终端到达分开记录，不把测试替身当真实短信送达。
6. 黑名单IP先拦/test、validateCaptcha解封、首访/周期验证和开发关闭风控，分别回归新旧四条路由；恶意JSON/未知字段/超长/控制字符/query凭据/外站请求、敏感日志与回执逐项检查。新API凭据路径不由旧Cookie/apiKey提升。MySQL跨实例用户状态/手机修改与申请/完成竞争、死锁/连接清理及实际SDK异常另验。

全量目标继续active：v3注册短信申请/待验证账号与邀请码预留原子化、应用管理策略/HTTP、OAuth凭据端点风控选择、鱼游OAuth说明、私信、红包、支付和其他未覆盖能力及完整部署验收仍在范围；本批不将短信恢复子链替代全部API。补充源码复核：旧register2/reset-pwd仍按已批准兼容契约在证明成功后创建页面Cookie，本轮未新增Origin/CSRF约束；不能因绑定短信或匿名盾判定登录CSRF通过。LoginProcessor/BeforeRequestHandler目标链未见该来源检查，完整框架及浏览器跨站请求尚待专项验证，后续需准备兼容客户端的来源保护方案，不默认为安全。新v3申请/完成不凭环境Cookie授权且不自动登录，两者不得混作同一结论。API.md/UPDATE_LOG已同步，网站 https://fishpi.cn/article/1636516552191 仍需用户同步。


### 2026-10-19 注册短信第一步的待验证账号事务核心

上批旧两提交入口匿名盾、找回短信/组件接口及100+4组测试已形成真实代码进展。本轮继续注册短信第一步，读清旧POST/register→UserRegisterValidationMidware→UserMgmtService.addUser的待验证账号/注销手机号/会员编号/默认字段/头像与邀请码链。两项业务规则已询问用户但尚无答复：v3邀请码是否排他预留一天（同手机号重发、其他手机号409、到期可用）或保留旧覆盖预留；有效邀请码路径是否也必须极验或沿旧仅开放/邀请链接需要。未把沉默视为同意，也未先公开一个遗漏邀请模式的注册短信接口。独立于这两项选择的账号创建/复用核心与默认字段共用已完成并测试。

新增AccountPendingRegistrationService.prepareInCurrentTransaction，仅供外层已有事务和ArticlePublicationCaches使用，无HTTP路由、无自开根或提交、无短信/头像上传/奖励/通知/登录。调用方仍须负责极验、邀请码/邀请人资格与额度。核心自身当前复核注册开关，namespace/member统计option先锁，然后按ID有界当前锁手机号所有者及非pending名称占用者：WHERE phone=? OR(name=? AND非pending)，LIMIT4遇冲突拒绝，不锁定可无限增长的其他手机号同名pending行。用SQL CASE nameMatches保留数据库名称排序规则，不靠Java比较猜中文/排序等价。

同手机号pending复用ID，仅按列改名称，保留余额、资料、角色、密码、编号及统计；未验证身份仍不能作为合法登录身份。其他手机号的pending同名可并存，激活时已有共用核心处理占用。有效/封禁/登录停用/注销名称保持不可抢占；手机号非pending/非注销不可注册。注销手机号沿现有latestLoginTime+一个Calendar.MONTH边界（含月末/闰年），到边界才释放旧手机号并创建新pending，原账号状态/名字不复活；未来、负值或溢出时间拒绝，不因时间计算溢出提前放行。

新pending记录由服务端自己构造name/phone/空密码/locale，使用UserRegistrationDefaults后强制编号与默认头像，角色为原默认角色、状态为NOT_VERIFIED、积分0。会员统计按原规则加一并跳过8888/9999，坏值/负值/int溢出拒绝；新用户插入、释放旧手机号、编号统计同保存点/外层事务。调用方捕获局部异常继续提交时，局部失败的账号/手机号/统计也不可残留；保存点释放或恢复失败按既有工具整笔回滚。缓存按actor+每个名称分别登记根结束后的失效，连续两次改pending名称不会覆盖原别名的失效动作。OptionRepository的缓存边界由现有ArticlePublicationCaches保护，不能绕过边界直接用这个primitive。

UserRegistrationDefaults从旧addUser的纯默认字段块提取，旧服务已改调用同一构造器；保留原内部调用可指定role/status/password/guideStep/timezone、邮箱规范化及管理员语言优先规则，没有让HTTP新申请原样传入完整用户对象。原头像生成/上传与旧fallback分支、积分/通知/角色例外、旧独立提交及邀请码覆盖流程均未顺手改写。新pending核心先使用原默认头像；未来注册短信协调器仍需接提交后头像生成、发送确认和证明激活，不能把默认头像占位等同于已验证完整首步。

实际文件：
- `src/main/java/org/b3log/symphony/service/AccountPendingRegistrationService.java`：当前事务待验证账号/手机号释放/编号写入与保存点、缓存别名边界。
- `src/main/java/org/b3log/symphony/util/UserRegistrationDefaults.java`：从既有构造逻辑提取的共享默认字段，非HTTP白名单/鉴权服务。
- `src/main/java/org/b3log/symphony/service/UserMgmtService.java`：addUser复用共享默认块；原外部业务链保留。
- `src/test/java/org/b3log/symphony/repository/AccountPendingRegistrationTest.java`、`tools/api-v3-chat-event-test.mjs`：真实默认字段、metadata全列SQLite表、事务/保存点/缓存及边界适配器。
- `AGENTS.md`、`docs/api-v3-inventory-and-design.md`与本计划：架构约束、实测范围与未完事项。没有新增表/Repository/SQL、HTTP接口/字段、FTL/JS/SCSS/生成产物，API.md/UPDATE_LOG不虚构新注册申请接口已开放。

实际验证：共用Java25隔离runner107组（原100+待验证账号7）通过，账号注册选项专项4组通过。新增执行真实pending服务、共享默认构造器、SQL与现有保存点/严格提交工具；用户表由当前repository.json所有user keys在SQLite创建，验证写入字段兼容（不等于MySQL列约束一致）。覆盖初始inactive/defaultRole/空密码/零积分、会员编号跳号、同手机号复用与余额资料保持、同名pending并存/active等冲突、自然月边界与闰年/未来/坏时间、注册关闭/异常配置、统计坏值/负值/溢出、根/缓存边界及name/phone/locale校验；phone release、rename、insert、counter、commit每处故障；调用方捕获后提交保留无关工作但不留部分账户；保存点失败整根回滚；同根多次改名后失效全部别名。持久化/缓存/Option更新为显式适配器，真实UserRepository/OptionCache、UserMgmtService完整类型/IoC、跨实例锁及MySQL均未执行。

源码与工具node --check、git diff --check通过；路由盘点仍706（696HTTP+10WS），浏览器引用398。未运行Maven、数据库迁移、真实短信/极验、头像上传、8080或浏览器，无target/classes同步；无前端变化无需新build。Java变动已提醒用户通过IDEA重编译并完整重启，尚未收到确认，不能用旧服务行为判断新核心效果。

安全/反漏扫：没有新入口，现有登录/匿名检查、BeforeRequestHandler黑名单/test、首访/周期极验、validateCaptcha解封和anonymous.viewSkips均未改；不能把SQL primitive当可匿名调用的注册接口。名称/手机号/locale有界字符、参数化查询/固定列写入，默认用户由服务端构造，不复制apiKey、2FA、外部积分或管理字段。没有新DTO/完整用户输出、XSS HTML渲染、URL抓取/SSRF、上传下载、CDN/静态文件、版本/sourceMappingURL或目录枚举路径。外层请求scope/身份、CSRF、批量滥用与风控仍须在未来HTTP协调器接入/运行，内部事务测试不替代它们。旧addUser常规请求字段未改，不将其旧自提交链宣称为严格提交或整体原子。

后续注册请求的必要安全门：新的注册短信证明须绑定申请时的用户名。当前pending手机号复用策略会改名称，旧POST/register也可能覆盖；若只绑定phone/userId，第一次SMS仍可能激活后来被重发改写的名称，不能直接将该primitive接HTTP后忽略此问题。需要在同事务的证明上下文/登记请求保存预期名称，并在完成时核对当前名称/证明，保留老码兼容边界后再接线。排他邀请码若获选，还必须评估旧申请入口防覆盖的共享实现及兼容授权，不能只约束v3而让旧路径破坏预留；两项业务选择尚未返回，不自定租期或放弃邀请码路径。

人工完整回归（先IDEA重编译/完整重启，真实环境尚未执行）：
1. PC/移动从/register申请短信进入第二步，核对待验证账号默认语言/角色/皮肤/引导/头像、会员编号与统计；携带code完成/register2→登录→本人资料，核对原初始/邀请积分、角色、头像及隐私开关保持。原接口字段不变，本批没有新的注册短信URL可调用。
2. 新核心尚无HTTP：在IDEA受控集成测试以严格事务/缓存边界调用，对同一待验证手机号重复准备不同合法名字，核对原ID/余额/编号仍在；用active/封禁/登录停用手机号或已占用名称，确认无法覆盖。用专用注销测试账号验证不足一个月/边界一天及一月31日→闰年二月29日，原账号名字保持，满足等待期后只释放手机号、创建新ID；不使用真实生产账号。不要把该新核心的余额保持/原子性要求套到尚未迁移的旧首次短信入口。
3. 在IDEA受控集成测试中以严格事务+ArticlePublicationCaches调用pending primitive，故意在释放电话、添加新记录和更新统计后抛错，核对完整回滚；捕获局部异常并提交之前无关操作，账号/统计仍不残留。模拟commit/保存点故障及同根多次pending改名，确认所有旧名缓存只在事务结束后失效。
4. MySQL两个后端并发注册/改名/手机号复用/统计更新与管理员开关、注册语言修改，检查无重复占用/编号/部分解绑；缓存/会话读提交前后分别确认。真实锁等待、死锁回滚、头像外部失败/默认头像路径以及root提交确认丢失另验，不能用SQLite串行结果推断。
5. 注册短信公开协调器仍须在规则选定后完成极验/邀请码/配额→pending+名称绑定证明/预留→发送与确认→完成注册→登录整链，以及匿名/黑名单/解封、Cookie/apiKey不提权、错码/过期/跨名字/跨手机、重发/并发/发送失败与无敏感日志；本轮没有执行或声称这些未存在的HTTP操作通过。

全量目标保持active。注册短信首步、应用管理、OAuth凭据风控选择、鱼游OAuth说明、私信、红包、支付、旧来源/登录CSRF专项与其他剩余能力及完整运行验收继续保留。API.md已有条目仍需同步网站 https://fishpi.cn/article/1636516552191，本轮没有伪造新的公开契约。

## 2026-10-19 私信读取与独立权限

用户已明确确认注册申请规则：邀请码排他预留一天，同手机号允许重发，其他手机号在有效预留期返回409，到期可重新使用；开放注册、邀请链接和邀请码三种路径均要求极验。覆盖此前“尚未答复”的记录，后续不能再按旧邀请码跳过极验实现。注册申请协调器仍须完成名称绑定证明与新旧预留入口一致性，尚未公开。

本批新增五个GET：/me/conversations、/me/conversations/{peerId}、/me/conversations/{peerId}/messages、/me/messages/{messageId}、/me/messages/unread-count。全部挂messages.read，当前身份只取Bearer；重新投影当前数据库状态。content.read/profile.read/notifications.read均不能读取私信。会话按实际fromId/toId而不是可伪造user_session聚合，详情先owner过滤后渲染；已删除/停用对方保留已有消息，对方资料为空占位，文件助手维持固定ID。只读查询不更新旧列表缓存、已读或消息。

会话当前页批量加载公开对方资料及未读数，查询数有界，不逐条查用户。历史默认倒取再升序显示，before/after互斥且排除边界，已有会话空游标页200、无会话404。旧未读表没有messageId，因此只提供记录数，不虚构每条消息的已读状态。消息/用户按白名单，缺失/超旧上限正文available=false；sentAt保留服务器当地字符串，无法识别为空。Markdowns.toPrivateHTML使用现有本地Flexmark再清洗，禁止将私信送入公共toHTML的远程Lute、公共缓存和带原文的失败日志。渲染失败保留原始Markdown、HTML为空，不记录异常正文；数据库错误只给通用503且不记录驱动异常原文。

实际文件：

- src/main/java/org/b3log/symphony/processor/ApiV3PrivateMessageProcessor.java：五个GET路由及输入/响应适配。
- src/main/java/org/b3log/symphony/service/ApiV3PrivateMessageQueryService.java：当前用户/双参与者SQL、批量公开资料、游标与未读计数。
- src/main/java/org/b3log/symphony/util/ApiV3PrivateMessages.java：ID、消息白名单、正文/时间/摘要边界及文件助手常量。
- src/main/java/org/b3log/symphony/util/Markdowns.java：本地私信专用渲染方法。
- src/main/java/org/b3log/symphony/processor/ApiV3Processor.java、processor/middleware/ApiV3AuthMidware.java：注册及私信独立读取权限。
- src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java、processor/ApiV3OAuthProcessor.java：新网站登录权限与第三方同意页权限名称。
- sql/20261016_api_v3.sql、sql/20261019_api_v3_private_messages.sql：精确旧默认网站policy升级及三组参与者查询索引；无新表。新迁移DDL为MySQL隐式提交，policy写入独立事务；脚本尚未执行。
- src/test/java/org/b3log/symphony/service/ApiV3PrivateMessageQueryTest.java、tools/api-v3-private-message-test.mjs：真实查询/DTO/中间件/控制器、生产路由抽取与Dispatcher顺序；SQLite/令牌存取/Markdown引擎适配。
- src/test/java/org/b3log/symphony/repository/ApiV3TokenLifecycleTest.java：真实Token发放/刷新及实际迁移UPDATE的SQLite方言适配。
- API.md、UPDATE_LOG.md、AGENTS.md、docs/api-route-inventory.md、本文：公开契约、关键链路、路由清单与验收记录。

验证：私信专项7组通过；共用事件/事务/Token/OAuth/账号专项108组通过（此前107，本批增加私信scope迁移/不扩权），git diff --check通过；源码清单711路由（701HTTP+10WS）、398浏览器引用。私信专项执行真实SQL及消息DTO、真实scope中间件/控制器、五条生产路由在内置Dispatcher的次序。Token专项使用真实服务，验证policy迁移幂等、仅精确默认website、定制及第三方policy不变、token行不变，旧网站/第三方刷新均不增messages.read，新登录/重新授权才取得。测试并非完整应用编译：Markdown底层解析器与Token查找在私信HTTP测试中适配，实际Flexmark运行依赖、完整IoC/浏览器/MySQL锁与DDL/查询计划和负载未验证。未执行Maven、真实短信或应用请求。

安全与反漏扫结论：五路均明确Bearer登录，非裸匿名入口，未加anonymous.viewSkips；未改BeforeRequestHandler/匿名盾/validateCaptcha与旧登录链。静态检查SQL均参数绑定、当前用户及目标双方约束，响应无IP/UA/联系方式/key、无online隐私；HTML清洗且原始Markdown须客户端安全渲染，查询本身无网络请求/SSRF。GET只读无Cookie授权/数据库写入，不能凭环境Cookie提权；批量数和字符串有界，未宣称已部署全局速率防护。无新上传/下载返回路径、JS/CSS/CDN依赖/sourceMappingURL/目录枚举入口，头像复用既有固定图片地址。真实匿名首访/黑名单跳/test、每5次校验、解封、开发关闭风控一致性及全量静态/CDN扫描未重新运行，不能据源码检查声称运行通过。没有擅自改变旧私信接口/WS契约。

部署后人工完整链路（Java改动先由用户IDEA重新编译或重启，当前尚无确认）：

1. 开发数据库先审阅并执行私信增量迁移；检查三个索引与默认客户端权限，再重跑迁移确认可重复，定制/第三方policy和已有token未改。
2. 用两名开发账号已有的旧私信作为样本，重新登录获取messages.read，读取会话→指定会话→初始历史→before/after→消息详情，核对双方正文、顺序、游标边界及摘要，文件助手/停用或缺失对方占位；无会话404，已有翻完空页200。
3. 调用未读总数与会话未读，前后比较旧未读记录，确认查询不消除未读；不将计数解释为逐条已读。
4. 不带Bearer、仅Cookie/apiKey、旧content.read令牌分别验证401/403；第三个账号读取其他双方消息404；伪造会话字符串不影响归属；非法/前导零ID、双游标、size51和指定本人422。
5. 自有开发消息覆盖Markdown、emoji、HTML事件/脚本/iframe/object/危险链接，HTML无活动内容；故障注入渲染返回空HTML和原Markdown，SQL故障503，日志不含正文/驱动细节。刷新旧令牌不增加私信权限，第三方重新授权仅获已登记范围。
6. 独立回归匿名防扫描与黑名单/解封链，以及PC/移动原私信读取/发送/撤回，确认旧契约保留；本批无前端产物，未要求yarn构建。

整体目标继续active：私信发送/原子已读/撤回/专用实时订阅仍未实现；账号注册短信首步、鱼游OAuth说明规则、应用管理和其他矩阵缺项仍在范围。本批不能替代完整接口交付或部署验收。API.md需同步网站 https://fishpi.cn/article/1636516552191。

## 2026-10-19 注册短信协调器、绑定证明与旧入口兼容准备

上一批私信读取为真实进展，目标继续active。本批按用户已确认的“三路径均极验、邀请码一天排他预留”推进，不再重问两条规则。新增AccountRegistrationRequestService及ApiV3AccountProcessor.requestRegistration处理方法，已完成真实服务/SQL与处理方法验证；**尚未声明Dispatcher路由，不能请求POST /api/v3/auth/registration/request，公开API.md未假称该路由已上线**。旧POST/register目前仍直接覆盖邀请码归属，所以只开放v3会让旧路径破坏排他承诺；等待用户对该兼容变化的答复，未先改旧入口。

准备的申请链：限定用户名/手机号/邀请码/邀请人字符和长度，有界JSON极验证明；共用原AccountSmsQuota→所有路径极验→原用户名审核→当前注册状态/邀请人资格/账号与邀请码预检→SecureRandom六位码→短信服务商受理（此时没有SQL事务或缓存边界）→严格根内namespace/当前设置/当前邀请人及role_permission→pending核心→邀请码当前锁与租期→SENT注册证明同提交。没有会员初始积分、邀请奖励、通知或Cookie登录副作用。读写均用实际Repository名称及参数绑定；邀请权限直接投影当前有效用户角色与role_permission，不用Cookie/apiKey或缓存用户授权。

手机号相同复用pending ID、重发新码并延长预留；其他手机号有效期内409，恰好到期可认领。业务租期使用Java常量86400000ms，无新增配置。邀请码的userId/useTime承载预留，状态仍UNUSED；同一pending已有其他有效邀请码且请求切换时409，过期旧绑定在新事务内清理，异常全部回滚。既有本人有效预留在未显式传新码的允许路径继续绑定并续期，避免完成时与已有邀请归属不一致。邀请码原年龄过期任务还会提前删除临近到期的有效预留，准备的expireInvitations严格根按原全局年龄清理、额外跳过有效预留，保留sys/已使用/年轻记录；任务与旧入口都尚未接线，必须一起切换后才能开放新申请。

Verifycode新增accountContext(512)元数据/模型与幂等MySQL列迁移20261019_account_registration.sql；**部署本批已接线的注册证明消费前即须先迁移，不能因为申请路由未开放而跳过**。RegistrationProofContext是服务端JSON版本/名称/邀请人名称+ID/邀请码ID，输入白名单，不含电话或短信码；注册专用插入直接prepared statement，不走框架可能记录原始驱动错误的通用add。AccountRegistrationService已改用专用消费方法，锁内核对当前名称与完整邀请关系；同名邀请人替换也不能领用新证明。空上下文的既有证明按原phone/userId/purpose规则兼容，不能宣称所有存量旧短信已绑定名称。找回密码读取/插入不用新列，不变更其请求字段。相同六位值跨不同上下文碰撞时，有一条有效匹配的当前证明即可授权，并原子消费全部同owner/用途活码，不能让旧错误上下文导致合法新码永远失效或留下以后可重放的副本。

新handler准备的契约：JSON键仅userName、userPhone、captcha、invitecode、referral，最多8192字节，无查询参数；受理data.accepted=true。用户名与邀请人沿UserNameRules，手机号11位，邀请码16位字母数字，captcha四项字符串JSON至多4096UTF-8字节。模式0无需邀请码，模式2有权邀请链接可省略邀请码，模式2显式传邀请码时校验并预留。新pending使用已准备核心的默认头像，复用已有pending保留资料/积分/编号。未新增userId、apiKey、免审字段或任意语言/远程地址入口。远程受理与数据库不能物理原子：发送后若状态/预留/权限变化或SQL失败，可能收到不可用短信，但账号/统计/预留/证明不部分提交；不可声称所有短信投递成功，也不自动反复重发。

待确认兼容接线的具体修改（代码核心已可审阅/已测试）：

1. LoginProcessor仅将mode2邀请码写入的get→改JSONObject→updateInvitecode替换为registrationRequests.reserveLegacy(newUserId,userPhone,invitecode)，捕获ApiV3Exception沿旧JSON错误格式返回；保留旧生成头像、手机号处理、短信发送与正常成功字段。不把旧整个申请流程宣称为原子化，也不改变旧邀请码路径的表单极验规则。
2. 旧Dispatcher.post("/register",...)增加anonymous::handle在旧业务校验前，未完成访问验证可能302/test；这是AGENTS明确要求改造匿名入口接入以及先确认兼容的交集，已异步征询，尚无答复。
3. InvitecodeMgmtService.expireInvitecodes委托registrationRequests.expireInvitations(Symphonys.INVITECODE_EXPIRED)，移除该方法旧注解根，后台资源由新根结束后显式释放；保留其他生成/管理邀请码的方法与全局年龄。有效预留不会被原年龄任务提前删除，过期后照常清理。
4. 完成旧保护后才注册新路由，并把下述待接线契约正式写入API.md/UPDATE_LOG，补生产路由顺序/匿名门禁的Dispatcher回归及新旧覆盖互斥整链。没有将当前尚未发生的接线记为完成。

另一个已发起的业务确认：旧AccountRegistrationService按referral解析到的账号直接发邀请奖励，未排除本人或未激活账号。新证明已绑定请求时邀请人ID，防止名字被新账号认领后混用，但尚未擅自改变旧奖励对象规则。已询问新的v3申请是否只接受已激活且非本人的显式referral，否则422；仅限新申请，旧入口/既有证明保留。答复前不自定这条业务规则，也不声称整条注册奖励防滥用已完成。邀请链接免码资格与普通奖励对象是两个问题，不能顺手改为只有具备免码权限才可得到奖励。

本批实际文件：

- src/main/java/org/b3log/symphony/service/AccountRegistrationRequestService.java（新增）：申请协调、旧预留适配、跳过有效租期的定时清理适配；后两者尚未接线。
- src/main/java/org/b3log/symphony/util/RegistrationProofContext.java（新增）：名称、邀请人名称+ID、邀请码身份的严格版本化绑定。
- src/main/java/org/b3log/symphony/model/Verifycode.java、repository/VerifycodeRepository.java：accountContext常量、绑定插入、用途隔离读取。
- src/main/java/org/b3log/symphony/service/AccountVerificationService.java、service/AccountRegistrationService.java：专用激活证明校验与碰撞码原子消费；旧空context兼容。
- src/main/java/org/b3log/symphony/processor/ApiV3AccountProcessor.java：准备的JSON处理方法及依赖注入，无新增Dispatcher路由。
- src/main/resources/repository.json、sql/20261019_account_registration.sql：既有verifycode列元数据及列迁移，无新增表。
- src/test/java/org/b3log/symphony/repository/AccountRegistrationRequestTest.java（新增）、AccountVerificationTest.java：真实协调器/消费/完整激活、故障、日志与SQL迁移适配。
- src/test/java/org/b3log/symphony/repository/AccountPendingRegistrationTest.java、AccountRegistrationTest.java：允许预检读取/根、连续ID及共享用户名列等适配调整。
- tools/api-v3-chat-event-test.mjs、tools/api-v3-account-test.mjs：包含新增真实服务/输入工具，显式适配审核/远程SDK和账户策略独立编译依赖。
- API.md、UPDATE_LOG.md、AGENTS.md、docs/api-route-inventory.md、本文：完成注册资料一致性说明、架构、路由位置与审核/验收记录；未发布未接线路由的公开文档。

验收与安全记录（最终计数见下方收尾）：真实Java25隔离SQL跑新协调器、pending、注册插入/专用消费以及完整AccountRegistrationService激活/积分/通知，SMS/极验/审核及框架pool显式适配，不是应用集成。实际迁移ALTER字面量解码后在SQLite执行，保留旧短信值并得到空默认列；JSON元数据512一致。MySQL information_schema/PREPARE/幂等整体DDL与锁顺序/竞争并发、真实服务商/验证码/IDEA/8080/浏览器均未运行；没有Maven。

匿名/反漏扫复核：新申请尚无公共路由，现有完成入口仍anonymous::handle；旧/register仍待确认后加门禁，不视为已保护。BeforeRequestHandler先于路由、黑名单/test、CaptchaProcessor解封路径、匿名盾首访/计数及开发关闭风控的源码契约未改，anonymous.viewSkips未加例外；真实IP/验证码/解封未回归。准备的匿名申请不从Cookie/Bearer/apiKey取得用户或免验证码资格，HTTP白名单与当前状态检查已测；没有登录Cookie写入，CSRF不能使攻击者自动登录受害账号，但短信滥用仍须上线共享额度和真实匿名门禁回归。SQL参数绑定及业务输入均有界，SMS/极验目的地址不从请求接受，响应只accepted，失败日志不带电话/码/极验证明/驱动异常；实际日志采集绑定生产LOGGER的LoggerContext，不能把未采集到任何事件冒充脱敏通过。没有新增前端/静态/CDN/上传下载返回路径、版本号/sourceMappingURL/目录枚举入口，PC/移动既有完成页仍须人工回归。全项目静态/CDN全量重新扫描未做，不能声称全量发布检查通过。

人工完整链路：

1. 审阅并在开发库执行20261019_account_registration.sql，再通过IDEA重新编译/重启（当前尚无确认）。确认原短信记录未变、accountContext存在，重复迁移无错误；以PC/移动旧注册第一步→输入验证码→选择角色/密码→完成→登录验证既有空context码兼容、奖励及响应不变。错码/已用/过期/换手机号/注册关闭不激活、不重复奖励。
2. 目前新申请没有Dispatcher路由：仅用tools/api-v3-chat-event-test.mjs和tools/api-v3-account-test.mjs复验准备的服务/HTTP方法，不把不存在的curl当作线上验收。审核源码与绑定证明、账户/统计/邀请/通知故障回滚；服务商成功与SQL回滚分别观察，不能将已发送物理短信称为撤回。
3. 用户确认接线后，才从captcha-options初始化组件→真实极验→registration/request→收到短信→registration/complete（referral沿申请值）→login→me，覆盖开放/链接/邀请码三路，成功仅一次奖励，Cookie/apiKey或伪造userId不能代办；非法JSON/字段/类型/超长/双入口配额及验证码错值等失败均覆盖。
4. 同手机重发不增加会员编号，换名字后旧新绑定码不激活别名；同名邀请人被换ID、改referral或变更邀请码归属时新码不激活；不同上下文碰撞同六位值只在有当前匹配证明时消费，全部重复码随事务消费或回滚。
5. 新旧两个不同手机争同邀请码：有效期内只允许当前手机，新接口409，旧格式错误且不覆盖；同手机重发可续期，到期恰好释放；临近原年龄到期仍在有效预留的码不被定时清理，租期后恢复清理；存储/提交/过期任务异常不部分改绑。
6. 真正联调旧/register新增门禁的首访302/test→完成validateCaptcha→重试发送，黑名单及开发关闭风控一致；检查日志无短信码/手机/极验证明；全量静态/CDN/路径检查另执行发布基线。本批尚未执行这些部署操作。

本批最终验证：tools/api-v3-chat-event-test.mjs完整122组通过（此前108，新增注册上下文/碰撞消费2组、申请专项12组）；tools/api-v3-account-test.mjs 4组通过；两脚本node --check、git diff --check通过。源路由仍711条、浏览器引用398条，未注册新申请路由。实际MySQL、短信/极验提供商、应用HTTP/浏览器、完整反漏扫与IDEA编译未执行；不得以隔离测试替代。两项已异步询问的选择仍待用户答复，不把时间经过当同意；未调用目标complete/blocked/paused，完整v3目标继续active。

## 2026-10-20 注册短信申请正式接线与新旧兼容

本节覆盖上一节“待确认/未接线”的当前状态。用户已批准旧 POST /register 共用一天排他预留并接入匿名验证；新的 v3 申请显式 referral 仅接受已激活的其他用户。旧入口/已签发空上下文短信的奖励对象规则不变，免邀请码仍另按邀请链接权限判断。完整 v3 目标继续 active，私信写入、红包/支付、应用登记管理及鱼游 OAuth 规则等剩余项未完成。

### 实际文件与行为

- `src/main/java/org/b3log/symphony/processor/ApiV3AccountProcessor.java`：注册 POST /api/v3/auth/registration/request，先 anonymous 门禁，再限定 JSON/字段/长度处理；不把 Cookie/Bearer/apiKey 或 attr 用户当注册身份。
- `src/main/java/org/b3log/symphony/service/AccountRegistrationRequestService.java`：当前邀请人有效状态/非本人校验及发送前后复核；邀请码一日租期、同手机号续期、跨手机号409、严格提交与当前证明绑定。远程发送不持 SQL 事务，物理短信与 SQL 不能原子化。
- `src/main/java/org/b3log/symphony/processor/LoginProcessor.java`：旧 /register 在原业务验证前增加 anonymous 门禁，原覆盖邀请码写入改为共享 reserveLegacy，保留 code/msg 格式及正常短信流程。旧 addUser/证明写入仍先独立提交，拒绝预留时可能已产生待验证记录；本批不声称整个旧申请原子化，也不改变旧邀请码路径表单极验规则。
- `src/main/java/org/b3log/symphony/service/InvitecodeMgmtService.java`：定时清理委托共享严格根，按原邀请码年龄清理但跳过仍有效的预留；已使用/sys/年轻记录保留，任务结束释放 JDBC 线程资源。
- `src/main/resources/js/verify.js`、生成 `verify.min.js`：PC/移动共用的注册、完成注册、密码重置遇到非 JSON/网络错误保留表单，提供同站新标签手动验证链接；不自动重发。三处消息按文本插入，兼容旧校验仅含 msg 而无 code 的错误，首步 referral 查询参数编码。没有改 CSS/FTL 或其他账号操作。
- `src/test/java/org/b3log/symphony/repository/AccountRegistrationRequestTest.java`、`src/test/java/org/b3log/symphony/processor/LegacyAccountRouteTest.java`：实际新旧入口、跨入口预留争用、现行邀请人规则、旧奖励兼容、清理包装及请求到激活的 HTTP 组合链。
- `tools/api-v3-chat-event-test.mjs`：抽取实际旧控制器方法、生产 Dispatcher 声明与定时包装，显式适配远程审核/短信/极验、旧账号持久化和框架连接；不是完整应用类型检查或真实服务集成。
- `tools/account-registration-fixture.mjs`：实际 PC/移动隐藏验证码字段及源码/压缩脚本的离线 DOM 回归，AJAX 被拦截且 CSP 禁止连接，不接触真实账号或短信。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-route-inventory.md`、本文：公开申请契约/兼容更新、长期链路、路由清单与验收说明。

### 部署与人工完整链路

1. 先审阅并在开发库执行既有 `sql/20261019_account_registration.sql`，确认 verifycode.accountContext 存在、旧记录默认空字符串、重复迁移无误；再通过 IDEA 重新编译或重启。涉及旧激活消费的新列必须先迁移，不可仅刷新页面。本批未执行迁移/IDEA，也未收到用户重启确认。
2. 通过 captcha-options 初始化组件并由用户完成极验，查询 registration-options，依次覆盖开放、邀请链接、邀请码三路：request 收到 accepted→短信→complete（沿用 referral）→login→me。申请不登录/不发奖励，完成仅一次激活及原奖励；重放已用码拒绝，不重复奖励，响应丢失先尝试登录。
3. 申请错误字段、类型、超长、查询参数、错误极验、关闭注册、失效邀请码均不成功；显式不存在/未验证/停用/本人邀请人返回422，已激活其他用户无需拥有邀请链接权限也可作为 referral，但没有免码权限仍需有效邀请码。发送期间关注册/撤销权限/修改邀请人或争用预留后不部分保存账号、证明及预留。
4. 新旧不同手机号争同码：新入口409、旧入口原错误 JSON 且不覆盖；同手机号重发复用 pending ID 并续期，恰好一天后可重新预留。旧定时任务不能提前删除有效租期，租期及原年龄均过后照常清理。验证 MySQL 真实并发、存储/提交故障与缓存结束时机。
5. 注册名称/邀请人名称+身份/邀请码变更后，新上下文短信不能用于不同资料；旧空上下文证明按原规则仍可完成。相同六位值跨上下文碰撞有一条当前匹配证明才消费，全部同 owner/用途活码同时消费，失败全部保留。
6. PC/移动旧页面从 /register 发送→验证码验证→角色/密码→/register2 完成；/reset-pwd 同步回归。首次或黑名单302 /test时表单保留，手动新标签完成验证、回原页重试；页面显示恶意字符串为文本，无自动导航/重发。登录态、apiKey、错误/过期/换手机号/已用码及开发关闭风控均分别覆盖。

### 安全与验证边界

反漏扫源码复核：新申请及改造的旧 /register 均显式 anonymous；Router 全局 BeforeRequestHandler、黑名单 /test 重定向、CaptchaProcessor.validateCaptcha 解封和 AnonymousViewCheckMidware 首访/计次链未改，anonymous.viewSkips 未加例外。实际门禁/解封/IP 黑名单/开发关闭风控仍待运行测试。新入口不能凭环境身份激活账号；验证码消费与奖励同提交，写入参数绑定、有界输入、只返回 accepted，短信/审核目的地址不接受用户输入。新接口没有登录副作用，短信滥用由共享额度+极验+匿名门禁控制；旧 Cookie 登录/来源保护专项仍不能宣称已完成。

XSS：三处账号回调错误消息改为文本；输入返回或 HTML 验证页不注入 DOM，验证链接限制同源。SQL 注入采用参数绑定；SSRF 没有新用户地址入口。新注册申请生产失败日志不记录电话/码/极验/驱动错误；旧 ServiceException 日志仍是既有路径，不能宣称全部旧日志都脱敏。没有新增公网静态/CDN URL、上传/下载路径、目录路由或 source map，verify.js/min 两文件版本审计零命中。全项目静态/CDN 下载扫描、目录枚举与上传下载端到端未重新执行。

实际验证：Java25 隔离通用套件127组、账号策略4组；离线浏览器56项（PC/移动 × 实际源码/压缩产物）通过。`COREPACK_ENABLE_AUTO_PIN=0 corepack yarn run build` 成功；JS/工具 node --check、git diff --check 通过。路由清单712条、浏览器引用398条。组合 HTTP 测试包含申请→真实绑定证明→完成激活/4笔转账/3条通知→已用码重放拒绝；SQLite/框架/远程服务适配边界如上。没有运行 Maven、真实 MySQL 迁移/锁竞争、腾讯短信/极验、应用8080或服务重启后的页面联调，不以隔离结果替代部署验收。API.md仍需同步网站 https://fishpi.cn/article/1636516552191。

## 2026-10-20 私信发送、已读与混用旧会话列表

上一轮注册短信接线属于实际进展，完整目标继续 active。本批落实私信专用写入，用户已明确：发送只允许当前已激活的其他用户及固定文件传输助手；读取已有失效/删除账号历史沿用参与者权限，不受该发送限制。没有将聊天室发送、content.write 或旧 apiKey WebSocket 当作私信授权。

### 实际文件与行为

- `src/main/java/org/b3log/symphony/processor/ApiV3PrivateMessageProcessor.java`：新增 POST `/me/conversations/{peerId}/messages`、`/{peerId}/read`、`/read` 三条 `/api/v3` 路由，全部 messages.write → 公共写预算 → 控制器。发送只收 content，JSON上限65536字节；已读空体或空JSON上限1024字节；均禁止query/身份字段，要求幂等键。
- `src/main/java/org/b3log/symphony/service/ApiV3PrivateMessagePublicationService.java`、`PrivateMessagePublicationService.java`：原始正文与幂等请求绑定，实际首次 mutation 走原保留词处理→trim→10240 UTF-16，当前激活用户按ID排序锁、接收者当前有效/非本人及文件助手例外；消息/未读/list在同保存点与严格幂等根提交。异常可局部恢复，外层捕获后提交不遗留半条消息。prepared SQL不把私信正文交给框架通用add日志，回执仅id/peerId/sentAt；提交后才失效旧缓存，不转发旧完整正文WS，也不声称已有实时送达。
- `src/main/java/org/b3log/symphony/service/PrivateMessageListStateService.java`、`ChatListService.java`：新旧列表投影共享排序用户锁，当前读真实双参与者消息的最新ID，而不信任延迟事件里的session/time/body；旧工作线程自带严格根并显式释放JDBC，提交后失效缓存。已有重复session行全部保持同一head，不删除历史重复数据。旧列表读取改为实际Repository表名和绑定参数，保留原字段/排序/128条规则。旧消息及未读两个独立事务仍保留，不能称整个旧发送已原子化。
- `src/main/java/org/b3log/symphony/service/PrivateMessageReadStateService.java`、`ApiV3PrivateMessageStateService.java`：只清当前toId及指定fromId，忽略可伪造user_session；本人激活状态当前锁复核。固定上界ID，移除有效incoming记录、保留外人/self/损坏记录；上界更大的新记录不受影响。这不是可靠提交序快照：旧发送生成较早ID后延迟提交的记录，仍可能落在本次范围；不得宣称精确“请求时可见集合”。同键重放只读回执，不再次标记后来未读；严格提交后清本人旧缓存，并仅发送本人未读数刷新，无私信正文。
- `src/main/java/org/b3log/symphony/processor/middleware/ApiV3AuthMidware.java`、`service/ApiV3TokenMgmtService.java`、`processor/ApiV3OAuthProcessor.java`：独立 messages.write，默认第一方scope及同意页中文权限名称同步。读取仍单独要求 messages.read，不从write推导read，旧令牌刷新不扩权。
- `src/main/resources/repository.json`、`sql/20261020_api_v3_private_message_writes.sql`、`sql/20261016_api_v3.sql`：私信原元数据512与既有发送上限10240不一致，元数据调整10240，新迁移将现有chat_info.content扩为TEXT并保留NULL旧值，补非唯一session查询索引及精确旧默认website policy升级。没有新增表；SQL表前缀按部署替换。DDL隐式提交/锁表及真实MySQL重复执行仍需验证；先迁移再启用新发送。第一方更新只匹配完整旧默认策略，定制/第三方policy不自动扩权。
- `src/test/java/org/b3log/symphony/repository/PrivateMessageReadStateTest.java`、`PrivateMessagePublicationTest.java`：真实状态/发布/list SQL、严格幂等、保存点、故障/缓存时机/HTTP/旧列表混用及实际日志采集。SQLite、连接池、令牌查询、保留词处理和发送后效果为显式适配器；长度校验在适配的保留词前后都执行。旧getChatList的substring_index用SQLite函数适配。
- `src/test/java/org/b3log/symphony/processor/PrivateMessageRoutingTest.java`：八条实际生产声明经过当前Latke RouteHandler.mapping/resolution及真实鉴权/写中间件，验证三个POST及既有GET不会误选、peerId实际绑定；终端IoC InvokeHandler适配为已解析方法名捕获，不能宣称完成应用IoC启动。
- `src/test/java/org/b3log/symphony/repository/ApiV3TokenLifecycleTest.java`、`service/ApiV3PrivateMessageQueryTest.java`：实际迁移UPDATE的SQLite方言适配、既有访问/刷新不扩权、新授权取得write及write-only不能读取私信。
- `tools/api-v3-chat-event-test.mjs`、`tools/api-v3-private-message-test.mjs`：隔离Java25依赖/生产声明抽取及真实类接线。读取套件中的写service是禁止调用的适配器，写入套件中的查询服务是显式适配器；不把分开测试称为实际POST→GET全应用集成。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-route-inventory.md`、`docs/api-v3-inventory-and-design.md`、本文：公开三接口及权限升级契约、长期约束、盘点与验收记录。无本批前端/FTL/JS/CSS修改，不需重新生成静态产物。

### 安全与反漏扫结论

新增三入口只接受Bearer及messages.write，Cookie/apiKey/attr用户不能授予权限，匿名及read-only在进入mutation前被拒绝；非本人发送者字段不能覆盖身份，私信读取继续按实际参与者验证。公开响应只ID/时间或changed计数，原正文仅经已有read权限与本地HTML清洗读取。生产写失败日志只记固定可用性消息，不记录正文/驱动/异常原文；测试必须捕获实际静态LOGGER所属LoggerContext且断言非空，不能把日志未启用当脱敏通过。正文/JSON/ID有界、SQL参数绑定，无用户提供的远程目的地址，因此本批不增加注入/SSRF入口；XSS展示规则沿用私信本地清洗。CSRF以显式Bearer为授权，未叠加会破坏API调用的旧Cookie CSRF检查。

反漏扫源码复核：全局BeforeRequestHandler、黑名单/test、validateCaptcha解封及AnonymousViewCheckMidware原链未变，anonymous.viewSkips无例外；新功能均先明确鉴权，不裸放匿名入口。公共写预算和幂等限制批量重复写入，但旧发送仍有自身已知风险，不能声称旧接口所有滥用/WS授权均已修复。没有新增公网静态/CDN URL、sourceMappingURL、目录、上传/下载返回路径；完整CDN下载扫描、目录枚举、旧公开资源和上传下载运行回归本批未重做，不能代替发布全量检查。

### 部署与人工完整链路

1. 按既有v3迁移顺序先完成20261019_api_v3_private_messages.sql，再审阅/执行20261020_api_v3_private_message_writes.sql；确认原私信内容、NULL旧值及历史会话保留，TEXT可保存上限正文，session索引存在；重复迁移、DDL锁时长及自定义表前缀单独检查。随后通过IDEA重新编译/完整重启，本批已提醒但没有收到确认，未连接8080验证新行为。
2. 用户A重新登录取得messages.read/messages.write；第三方应用先获准write再重新授权。保留旧read-only令牌验证POST为403、仅write令牌GET为403；缺Bearer、仅Cookie/apiKey为401。刷新旧令牌不能增加权限，定制第一方/第三方策略不被迁移自动改写。
3. A通过POST /me/conversations/{B}/messages发送含中文、emoji、Markdown的正文→成功id回执→A/B分别用GET详情/历史/会话列表读取→只有A/B可见，C读详情404；A的未读不增加，B记录增加一次。旧PC/移动私信列表刷新后也显示最新消息。文件助手单独覆盖；本人、待验证/停用/不存在目标拒绝，已有失效对方历史仍可读。
4. 同键同正文重试仍为同消息ID、未读只一条；同键不同正文409，新键是独立发送。空白/超10240/非法Unicode/NUL/未知fromId或toId字段/非JSON/超65536/查询参数拒绝。审核替换扩展后超长也拒绝。消息、未读、list、回执、提交故障均不得出现半条数据或已发效果；真实MySQL多实例交叉发送及旧异步列表事件延迟竞争必须补测。
5. B读取历史不会自动清未读；POST指定会话read只清来自A的记录，C→B保留；POST全部read再清B的其他合法incoming，历史消息保留。返回changed是记录数，不是逐条已读。旧键重放不清后来新记录，新键才是再次操作；并发更大ID新记录保留，较早生成后延迟提交的旧记录不承诺精确快照。对方删除/停用时仍可处理已有未读，自己停用时拒绝。
6. 混用新旧发送并人为延迟旧PRIVATE_CHAT事件，列表不能被旧事件倒退；已有重复session行统一head，不能写入伪造事件的会话/正文/时间。旧PC/移动已读显示和缓存刷新、后台JDBC资源回收、停用/撤销权限/改名时行为均需应用联调。新发送本批只保证持久化回执，专用实时和撤回仍待后续实现，不将轮询误写为实时功能。

实际MySQL迁移/锁竞争、完整IoC应用启动、IDEA编译、8080 HTTP、旧PC/移动浏览器、真实跨实例及完整反漏扫未执行；没有Maven。完整目标仍未完成，不标complete/blocked/paused。API.md需同步网站 https://fishpi.cn/article/1636516552191。

本批最终验证：`node tools/api-v3-chat-event-test.mjs` 当前完整141组通过（上一批127，新增private.write scope迁移1、已读5、发布7、实际路由解析1）；`node tools/api-v3-private-message-test.mjs` 7组通过，包括write-only不得读取。node --check及git diff --check通过，路由清单715条、浏览器引用398条。Java25隔离执行没有启动应用，没有Maven。缓存失效/旧计数刷新是提交后尽力执行，尚无持久投影outbox，提交确认丢失/进程中断后的自动修复仍须另行验证；回执可用同键重试恢复，不承诺每次HTTP结果都已刷新旧页面。测试中的messageID与提交顺序不能替代真实MySQL竞争证明。

## 2026-10-20 私信撤回准备：未读关联及提交确认丢失恢复

上一批私信发送/已读是实际进展。当前继续完整目标，尚未注册私信DELETE撤回路由：旧撤回按fromId/toId清除该发送者全部未读，而历史未读缺少消息关联，无法准确对应某条消息。已询问v3是否只清准确关联并保留未知旧未读；本节收尾时尚未收到答复，不把时间经过当作同意，也未改旧撤回行为。以下基础改动不预设该选择。

实际文件与行为：

- `sql/20261020_api_v3_private_withdrawal.sql`、`src/main/resources/repository.json`：既有chat_unread增加messageId(19)、默认空字符串及fromId/toId/messageId查询索引。没有新表；历史不回填猜测关联。必须在部署本批新旧发送代码之前迁移，即使尚未提供撤回路由也不能省略。MySQL DDL隐式提交，需替换自定义前缀。
- `src/main/java/org/b3log/symphony/service/PrivateMessagePublicationService.java`：v3消息和未读同事务写入时，未读直接关联该消息ID。
- `src/main/java/org/b3log/symphony/processor/channel/ChatChannel.java`、`processor/UserProcessor.java`：旧WS普通/手动发送及认证申请私信三个未读构造点增加实际chatInfoOId，保留旧顺序和正常响应；旧发送仍是独立事务，不能称已原子化。
- `src/main/java/org/b3log/symphony/processor/ChatProcessor.java`：旧hasUnreadChatMessage循环中移除内部messageId再缓存/返回，避免新增表字段自动扩散至旧公开JSON；正常字段不变。DataModelService仅使用未读条数，既有v3查询采用白名单，无新增字段输出。
- `src/main/java/org/b3log/symphony/service/PrivateMessageListStateService.java`：当前消息对为空时，在调用方事务内清除该pair列表投影；相关回滚保留旧投影。这是后续撤回原子核心的准备，当前尚无新撤回调用，不将测试直接删消息当作已完成HTTP功能。
- `src/main/java/org/b3log/symphony/service/ApiV3PrivateMessagePublicationService.java`、`ApiV3PrivateMessageStateService.java`：成功提交的幂等回放也可重复执行缓存失效/绝对未读数刷新，不重复业务写入。修复原来“提交实际成功但确认丢失→回放applied=false→永远不失效缓存”的遗漏；不能据此宣称有可靠消息广播或持久outbox。发生成功提交确认丢失时，首次可返回503；同键原正文重试恢复原回执。
- `src/test/java/org/b3log/symphony/repository/PrivateMessagePublicationTest.java`、`PrivateMessageReadStateTest.java`、`tools/api-v3-chat-event-test.mjs`：实际迁移ALTER字面量转义解码后在SQLite执行，验证旧记录空默认及v3新记录关联；抽取三个真实旧构造片段及真实旧输出移除语句执行并持久化验证，整个旧ChatChannel/UserProcessor/ChatProcessor未完整编译。通过真实strict JDBC在COMMIT实际成功后抛确认异常，再同键回放：仅一条消息和回执、恢复缓存；空消息对列表删除提交/回滚均验证。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、本文：明确响应中断后的重试契约、迁移顺序和未完成的撤回规则；无新HTTP/WS路由或前端资源。

安全/反漏扫复核：路由仍715条、浏览器引用398条，新公开入口为零；既有v3写入仍messages.write/Bearer及幂等，旧入口门禁不变。新增关联在旧响应前剔除，v3白名单不扩散；没有私信正文、凭据、IP/UA的新返回或日志，关联值来自已落库服务端消息ID，SQL继续参数绑定。未改变BeforeRequestHandler黑名单、AnonymousViewCheckMidware、validateCaptcha、anonymous.viewSkips；无新增静态/CDN/sourceMappingURL/目录或上传下载路径。真实匿名/IP/黑名单/解封、全量静态/CDN/目录/上传下载并未运行验证，不能声称全量发布审计通过。旧撤回仍会清空某发送者全部未读，是本次待决规则和后续旧入口兼容加固范围，不声称已经修复。

人工完整链路：先依次迁移已有private_messages/private_message_writes和本批private_withdrawal，确认旧未读数据完整且messageId为空，新增列/索引正确、重复MySQL迁移无误；通过IDEA重编译或重启后，PC/移动旧聊天普通发送、手动服务发送、认证申请私信以及v3 POST发送各产生正确关联，旧GET has-unread正常字段不新增messageId，v3会话/历史/未读条数保持。A→B发送→B读历史不清未读→B指定/全部已读，回放旧键后新未读保留且绝对计数刷新；模拟成功提交后连接中断，原键原正文重试仍仅一条消息、相同id。切勿用换键当重试。空消息对list删除仅有保存点/事务测试，实际撤回接口尚不存在，等待规则后才能进行发送→撤回→详情404/未读/list/推送的完整联调。真实MySQL并发、完整IoC/旧Java类型检查、IDEA、8080及PC/移动浏览器尚未执行；没有Maven。

本批收尾：当前完整通用143组、私信读取7组通过（上一批141，本批增加提交确认丢失/空列表及三个旧关联构造/旧字段隐藏2组）。node --check、git diff --check通过；715路由、398浏览器引用，无新增撤回路由。实际MySQL列/索引及information_schema/PREPARE幂等、所有旧类完整编译、应用/浏览器和全量反漏扫未验证。完整目标保持active，本批是撤回前置改造与既有发送恢复修复，不是完整撤回交付。公开API.md的重试说明需同步网站 https://fishpi.cn/article/1636516552191。

## 2026-10-20 v3本人私信原子撤回

上一轮未读关联/提交确认丢失恢复为实际进展。用户本轮已明确：撤回仅清理准确关联的未读，保留无法归属的历史未读。已完成 DELETE /api/v3/me/messages/{messageId}，完整目标继续 active；旧GET /chat/revoke同步采用新核心的兼容选择已另行询问，当前尚无答复，不假定已批准或已接线。最初对实时票据/会话授权的源码检查用于下一阶段，当前没有新增私信实时频道，不能把已有通知/聊天室广播当私信实时交付。

### 实际文件与行为

- `src/main/java/org/b3log/symphony/service/PrivateMessageWithdrawalService.java`（新增）：发送者过滤投影得到可信peer/完整用户锁集合，按ID排序当前锁用户、检查当前actor激活状态，再当前锁本人消息重验peer。变化409，不临时扩展锁集合；不存在或外人404。消息、按messageId+fromId+toId匹配的未读、列表head/空列表删除同保存点，由外层严格根提交；未知旧未读和其他消息未读保留。原接收者停用/删除不影响本人撤回，文件助手同样支持。removeLegacy为已测试的兼容根，目前没有旧控制器调用，不能记作新旧已共用。
- `src/main/java/org/b3log/symphony/service/ApiV3PrivateMessageWithdrawalService.java`（新增）：messages.write下幂等撤回operation隔离，回执只有id/peerId/removed，不暴露对方已读或剩余未读数；严格提交/回放后可重复失效双方旧缓存，修复提交确认丢失。没有转发旧WS撤回事件，没有可靠投递/outbox承诺；失败日志固定可用性消息，不附原始异常。
- `src/main/java/org/b3log/symphony/processor/ApiV3PrivateMessageProcessor.java`：DELETE注册messagesWrite→公共写预算→withdraw，空体或空JSON最多1024字节，拒绝查询参数及未列出字段，需幂等键，身份只取Bearer。
- `src/test/java/org/b3log/symphony/repository/PrivateMessageWithdrawalTest.java`（新增）：真实发布→删除核心与SQL/保存点/list、精确关联、未知旧记录/伪造跨peer关联保留、不同历史状态、当前身份/对方变化、故障回滚、严格提交确认丢失恢复、HTTP和生产LOGGERContext非空采集。SQLite连接/令牌/保留词/刷新为明确适配器；没有应用GET读取整链或真实MySQL竞争。
- `src/test/java/org/b3log/symphony/processor/PrivateMessageRoutingTest.java`：实际Latke RouteHandler解析新增DELETE及既有GET/POST，绑定messageId，未鉴权/read-only在mutation前拒绝；终端IoC仍按已解析方法名捕获，不宣称完整启动。
- `tools/api-v3-chat-event-test.mjs`、`tools/api-v3-private-message-test.mjs`：真实新服务/控制器编译接线及DELETE声明解析，读套件新增写服务禁止调用适配器，GET仍不能触发撤回。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-route-inventory.md`、本文：新契约、用户确认的未读规则、路由与人工验收。没有新增表/列/静态/FTL/JS/CSS；依赖先前20261020_api_v3_private_withdrawal.sql的messageId及索引，不能跳过既有迁移。

### 安全/反漏扫与边界

新增DELETE仅messages.write/Bearer，Cookie/query或body apiKey/attr身份不能替代；正文仅空对象，不能覆盖actor/peer或指定他人消息。接收者没有代撤权，缺失与越权均404，发送者当前停用不能凭旧身份继续删除；数据只返回白名单，不返回正文/是否已读/剩余计数。SQL参数绑定及ID/JSON有界，无用户指定远程地址、新HTML模板、静态资源或下载路径；因此本批无新XSS/SSRF/目录/CDN/source map入口。没有给匿名盾添加例外，BeforeRequestHandler黑名单、AnonymousViewCheckMidware、validateCaptcha、anonymous.viewSkips未改；明确鉴权在写预算之前。接口以显式Bearer授权，不叠加不兼容API的Cookie CSRF。幂等和公共写预算保持；旧GET撤回仍保持原ApiCheck与旧清理行为，其同步加固待答复，不能声称所有旧行为均已精确/原子化。

真正HTTP全局风控、匿名IP/黑名单/解封/开发关闭风控、全项目静态/CDN下载与目录枚举、上传下载路径的运行回归未执行。实际日志测试捕获生产静态LOGGER的LoggerContext，断言非空且不含正文/驱动错误；只能支持本批写服务，不推断所有旧日志已脱敏。通用套件为Java25隔离依赖编译，不能替代完整Java/IoC/MySQL应用验证。没有Maven。

### 完整人工链路

1. 开发库先按顺序完成private_messages/private_message_writes/private_withdrawal迁移，确认messageId、默认空值与索引存在，再通过IDEA重编译/重启。当前没有收到重启确认，尚未调用8080或浏览器验收。
2. A重新登录取得messages.read/messages.write，A→B发送两条私信→A/B查询详情、历史、会话/未读→A DELETE第一条，只有该消息不可读、第二条及其未读保留、列表仍指第二条。再撤第二条，列表回退剩余旧消息；撤去最后历史时会话不出现在列表。接收方用已读接口可处理保留的未知历史未读，不把该数值当逐条消息状态。
3. B或C尝试撤A消息均404，A不能从Cookie/apiKey/伪造fromId获得权限；read-only令牌403，缺Bearer401。A改名后仍能按本人ID撤回，A停用则拒绝；B停用/删除仍可撤本人历史，文件助手单独覆盖。错误ID/缺键/未知字段/query/非JSON/超1024拒绝。
4. 同键重试返回同id/peerId/removed且不再删除；新键访问已撤回目标404。撤回前接收者已经标记已读时仍可撤，不返回其是否读过。对方变更或真实MySQL多实例的发送/已读/旧撤回/列表事件竞争，需确认不误删其他消息或未读。
5. 注入消息/未读/list/回执/提交故障，三者与幂等回执一起回滚，外层捕获后提交也不能半删；提交实际成功但响应确认丢失时原键重试恢复回执和缓存，不要换键重试。真实应用数据库故障与锁竞争仍未跑。
6. 旧PC/移动页面及API客户端刷新后检查历史/列表/未读，专用实时撤回推送本批不保证，未添加新Socket事件。若用户批准旧GET接线，再完整验证其原result格式、query/body apiKey身份、失败不半删、精准清理和推送失败不覆盖已提交成功；批准前不声称该旧入口已改造。

实际验证：当前通用149组、私信读取7组通过（前批143，新增撤回专项6）；实际路由解析包含DELETE，node --check/git diff --check通过，716条路由、398条浏览器引用。MySQL迁移/并发、完整应用IoC/旧入口接线、IDEA及PC/移动浏览器仍未验收，完整反漏扫未重做。全量目标保持active，不标complete/blocked/paused。API.md需同步网站 https://fishpi.cn/article/1636516552191。


## 2026-10-20 旧私信撤回接线与提交后变更标记

本节更新上一节“旧入口待确认”的状态。用户已明确批准旧 GET /chat/revoke 共用原子撤回：正常字段及撤回事件保留，仅清准确关联的未读；保留无法归属的历史未读，不能再依赖撤回清空该发送者全部未读。当前完整 v3 目标继续 active，私信专用实时频道尚未开放。

本批实际文件清单：

- `src/main/java/org/b3log/symphony/processor/ChatProcessor.java`：旧 revoke 身份取 ApiCheck 写入的 User.USER，支持实际 query/body apiKey 及 body 优先；调用 removeLegacy 严格根，提交后才清缓存、审计、刷新接收方绝对未读数和发送原撤回事件，各外部副作用失败不覆盖已提交的成功。原 GET 路由/参数/result 契约保留，失败只返回固定消息，不裸露驱动异常。
- `src/main/java/org/b3log/symphony/model/ApiV3PrivateRevision.java`、`repository/ApiV3PrivateRevisionRepository.java`、`service/PrivateMessageRevisionService.java`（新增）：按 owner 保存 opaque revision，排序更新，跳过固定文件助手伪用户；没有正文、对方列表或凭据，不作为历史游标。
- `src/main/java/org/b3log/symphony/service/PrivateMessagePublicationService.java`、`PrivateMessageWithdrawalService.java`：消息、精确未读、列表和双方 owner 标记同保存点；撤回包含上述旧严格根。
- `src/main/java/org/b3log/symphony/service/PrivateMessageReadStateService.java`：实际有删除才更新本人标记；删除与标记共用保存点，即使调用方捕获局部故障继续提交，也不留下半次已读。
- `sql/20261020_api_v3_private_realtime.sql`、`src/main/resources/repository.json`：新增 api_v3_private_revision 表及元数据，模型/Repository 不硬编码表前缀。必须先迁移再部署上述写入代码。
- `src/test/java/org/b3log/symphony/repository/PrivateMessageRevisionTest.java`（新增）、`PrivateMessageWithdrawalTest.java`、`PrivateMessagePublicationTest.java`、`PrivateMessageReadStateTest.java`、`tools/api-v3-chat-event-test.mjs`：真实核心 SQL/保存点、独立读连接、标记故障及旧适配器/真实 ApiCheck 和 GET query/JSON 解析测试。框架 API key 解密、传输与部分应用依赖由适配器代替；不能据此声称完整应用已编译/运行。
- `API.md`、`UPDATE_LOG.md`：补旧私信撤回兼容契约及未知旧未读保留、读取后显式标记已读、旧 GET 无幂等及丢失响应先查状态的说明。
- `AGENTS.md`、`docs/api-v3-inventory-and-design.md`、`docs/api-route-inventory.md`、本文件：维护已批准链路、当前覆盖范围、清单与验证边界。无模板/JS/SCSS或生成产物改动。

源码安全与反漏扫结论：本批没有新 HTTP/WS 路由，旧 GET 必须经过 ApiCheck，Cookie 不授予该接口身份；v3 仍只用 Bearer messages.write 和幂等。本人归属及当前账号状态由数据库重验，参数绑定和有界 ID 不扩大注入面；没有用户可指定的外部抓取 URL，不增加 SSRF。旧 GET 维持显式 apiKey 契约，没有添加破坏 API 客户端的 Cookie CSRF 限制；新代码不渲染正文，XSS 仍由原读取本地清洗负责。回执/变更标记/生产故障日志不含私信正文和凭据。新增标记同事务、重放不重复业务写入，但未新增批量入口或业务限额，整体抗滥用能力不作额外保证。BeforeRequestHandler、AnonymousViewCheckMidware、validateCaptcha 和 anonymous.viewSkips 本批未改；无新增公网静态/CDN/sourceMappingURL、目录或上传下载返回路径。真实匿名首访/黑名单/验证码解封、全站静态/CDN/目录枚举与上传下载仍未实际重验，不能算全量发布审计通过。

人工完整测试步骤：

1. 先完成此前 private_messages、private_message_writes、private_withdrawal 迁移，再审阅执行 `sql/20261020_api_v3_private_realtime.sql`（自定义表前缀按部署调整），确认新表字段/主键与 repository.json 一致、旧消息和未读记录未改。通过 IDEA 重新编译或完整重启，并确认 target/classes 配置已更新；本批未收到重启确认，不用 8080 旧服务行为判定新代码效果。
2. A、B 各持合法 apiKey，A 向 B 发送两条消息；分别用 query 和 JSON body apiKey 调旧 GET /chat/revoke?oId=... 撤回 A 的消息。成功仍 result=0，A/B 历史中目标消失，其他消息和对应未读保留，列表正确回退；正在打开的旧 PC/移动聊天收到原撤回事件，B 的未读数刷新。
3. 准备一条 messageId 为空的历史未读，再撤回同发送者其他消息；未知历史未读仍保留，可用显式已读接口处理。撤回最后一条历史消息时双方会话列表不再包含空会话。B 或 C 撤 A 消息、无 key、停用 A、无效/不存在 ID 均不得删除；query 为 B 而 JSON body 为 A 时按中间件认证的 A 操作，反向组合拒绝。
4. 注入消息/未读/list/任一 owner 标记写入或提交故障，确认整次撤回回滚，无撤回推送；提交后让旧推送/审计通道失败，仍返回 result=0 且数据库已撤回。旧 GET 无幂等回执，响应丢失先查详情确认，不能保证重试返回相同成功。
5. v3 用 messages.write + 幂等键发送、指定/全部已读、撤回；双方发送/撤回、本方实际已读分别改变对应 owner 标记，独立连接提交前不可见，回滚后标记不变，幂等重放不再推进标记。标记失败且被外层捕获后继续提交时消息/未读/list 也须恢复。文件助手只更新真实用户 owner，零变更已读不推进标记。
6. 回归 A/B/C 详情与历史可见性、read-only/write-only scope 边界，旧 PC/移动发送/列表/已读，以及匿名访问、黑名单与验证码解封。专用实时取票/连接/授权复核/worker 尚未接线，旧普通发送/已读也还未写本标记，不存在可验收的完整私信实时功能。

未验证：真实 MySQL DDL/锁/并发/提交故障、全应用 IoC 与完整旧类编译、真实 apiKey 解密及网络事件、IDEA/8080/PC/移动浏览器和全量反漏扫。本批未运行 Maven，未修改前端，无 yarn 构建需求。API.md 需同步网站 https://fishpi.cn/article/1636516552191。

实际验证结果：Java 25隔离通用专项154组、私信读取7组均通过；新增5组覆盖旧适配器身份/失败与owner标记事务，未运行Maven。node --check、git diff --check通过；路由716条，浏览器端引用398条。本批无新增公开路由，完整v3目标仍active。


## 2026-10-20 私信实时标记覆盖旧发送与已读

上一轮旧原子撤回和v3变更标记为实际进展。本批补齐旧普通发送/手动发送/认证申请私信、指定/全部已读及延迟列表更新的标记覆盖，完整 v3 目标继续 active；尚未接私信专用取票/Socket授权复核/worker，不把内部标记当实时交付。

本批实际文件：

- `src/main/java/org/b3log/symphony/repository/ChatInfoRepository.java`：旧add的消息与双方owner标记同保存点；addLegacy严格根提交并在HTTP/WS成功失败后释放JDBC资源。无效旧owner不生成可订阅标记，v3显式准备语句核心仍自行验证与标记。
- `src/main/java/org/b3log/symphony/repository/ChatUnreadRepository.java`：旧add的未读与接收者标记同保存点、同样提供严格单步根；新增removeForOwnerInCurrentTransaction叠加toId=owner防止宽泛/冲突查询清外人未读，删除与本人标记同保存点。保留旧删除过滤和正常字段，旧空操作允许无害失效标记，v3零变更仍不推进。
- `src/main/java/org/b3log/symphony/processor/channel/ChatChannel.java`、`processor/UserProcessor.java`：普通/手动WS发送及认证申请私信的三组消息/未读分别改用上述单步根；消息、未读、列表仍是三步独立提交，没有声称整个旧发送原子化。独立提交均可被后续worker观察。
- `src/main/java/org/b3log/symphony/processor/ChatProcessor.java`：旧指定/全部已读改调用owner-aware删除，保留认证和正常响应规则；没有把尚未批准的其他旧入口重构混入本批。
- `src/main/java/org/b3log/symphony/service/PrivateMessageListStateService.java`：refreshLegacy在当前用户排序锁/最新head重算后同严格根更新双方标记，避免客户端在前两步刷新之后遗漏最终列表变更；核心v3列表更新仍由其外层统一标记。
- `src/main/java/org/b3log/symphony/service/LogsService.java`：复核发现上一轮改成提交后审计时，原chatLog重读已删除消息会静默丢审计。现直接记录messageId元数据，不重读已删除记录、不把私信正文写入撤回审计；无对外字段变化。
- `src/test/java/org/b3log/symphony/repository/LegacyPrivateMessageRevisionTest.java`（新增）：真实Repository保存点/严格根/owner防护、独立观察连接及后置列表更新测试；generated-ID插入和Filter→SQLite SQL是适配器，不是假称完整Latke/MySQL已验收。
- `src/test/java/org/b3log/symphony/repository/PrivateMessagePublicationTest.java`：旧列表真实核心增加revision依赖注入；`PrivateMessageWithdrawalTest.java`：实际chatLog提取实现的提交后审计仅含messageId。
- `tools/api-v3-chat-event-test.mjs`：增加上述专项、实际chatLog方法提取及六处旧addLegacy/两处owner-aware已读接线检查。`tools/api-v3-private-message-test.mjs`：读取专项编译实际新Repository依赖，读接口不调用mutation。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-v3-inventory-and-design.md`、`docs/api-route-inventory.md`及本文件：同步兼容说明、架构覆盖和验证限制。没有新路由/表/模板/JS/SCSS或生成产物；部署仍须先执行前批20261020_api_v3_private_realtime.sql及repository.json资源同步。

安全/反漏扫源码复核：无新增匿名入口、无鉴权降级；旧WS/认证申请/已读原门禁及v3Bearer独立scope保持，owner删除额外叠加实际接收者，宽泛查询不能越权。没有新增Cookie授权、外部抓取URL、正文渲染或批量API，CSRF/SSRF/XSS没有新边界；旧API key约定保持。标记只owner/revision/time，撤回审计只messageId，不新增正文或敏感凭据输出。SQL参数绑定与现有ID边界保留，新增事务故障不得局部提交；不新增业务限额，不声称旧发送具备幂等。BeforeRequestHandler/AnonymousViewCheckMidware/validateCaptcha/anonymous.viewSkips本批未改；静态/CDN版本号、sourceMappingURL、目录枚举、上传下载路径无新增引用，未做全站联网扫描。上述真实风控、匿名首访/黑名单/验证码解封、全站静态和目录/上传下载仍未运行，不能当完整发布审计通过。

人工完整链路：

1. 审阅并先执行此前private_messages/private_message_writes/private_withdrawal/private_realtime迁移，确认api_v3_private_revision及chat_unread.messageId/索引、chat_info.TEXT和实际表前缀。通过IDEA重新编译或重启，确认target/classes元数据更新；本批已提醒，仍未收到重启确认，未测试8080旧服务。
2. A/B打开旧PC、移动私信，用普通WS与手动发送各发一条，成功格式、正文和长度上限不变；查消息/精确关联未读/列表最终head，消息提交标记双方、未读提交仅B、异步列表提交再次标记双方。认证申请私信在自有开发账号同样覆盖，不向生产管理员发送测试内容。
3. B分别指定A已读与全部已读，原result及users正常格式保留，自己的未读减少，A/C记录不受影响，只有B标记变化。配合v3发送/已读/撤回混用，详情/历史/列表/未读一致；旧发送分步失败可能留下已提交消息，应先查询再重试，不假定整体回滚或可靠实时送达。
4. 注入第一或第二owner标记失败、单步提交失败、未读写失败、列表标记失败，确认该步骤数据与标记一起回滚、先前独立提交保留；WS当前线程TX及连接最终释放。调用方局部捕获owner已读失败后提交无关工作，未读与标记仍恢复。对宽泛/冲突owner过滤的内部故障测试确认不会删除外人未读。
5. 旧撤回完成后审计仍产生仅messageId记录，不重新读取消息，推送失败不改成功；未知历史未读保留。无key/无Bearer、外人、停用用户及scope边界按对应旧/v3契约回归。PC/移动原发送、列表、已读、撤回均验收，私信专用实时接口尚不存在。

未验证：真实MySQL/完整框架生成SQL与锁/并发、整个旧类及应用IoC编译运行、IDEA/8080/真实WS和PC/移动浏览器、真实apiKey及全量反漏扫。本批没有运行Maven，没有前端改动或yarn需求。API.md需同步 https://fishpi.cn/article/1636516552191 。

实际验证：Java 25隔离通用157组、私信读取7组通过（前批154组，本批新增旧Repository事务/分步/owner隔离3组，并扩展实际撤回审计断言）；node --check、git diff --check通过。路由仍716条，浏览器端引用398条。未执行Maven或真实应用部署；完整目标保持active。


## 2026-10-20 私信专用实时订阅接线

上一轮旧发送/已读的标记覆盖为真实进展。当前新增 POST /api/v3/me/messages/realtime-ticket，私信频道 messages 复用 v3 /api/v3/realtime 首帧一次性票据协议，要求独立 messages.read，target为空。当前用户及父access token仅取Bearer身份。本人发送/收到/已读/撤回及最终列表投影写入的owner标记被后台当前读观察，逐连接授权后仅发 messages.changed/data={}，不转发旧私信原始正文或未读状态。当前源码/隔离测试已接线，不代表真实部署验收；完整v3目标继续active。

本批实际文件清单：

- `src/main/java/org/b3log/symphony/model/ApiV3SocketTicket.java`：增加messages频道常量，无新字段/表或scope扩张。
- `src/main/java/org/b3log/symphony/service/ApiV3SocketTicketService.java`：issueMessages签发本人空target票据；issue/consume/authorized均经当前父令牌、账号、客户端与messages.read验证。messages.write/content.read/notifications.read不能替代，scope撤销和账号停用使待用票据及已有连接失效。
- `src/main/java/org/b3log/symphony/processor/ApiV3RealtimeProcessor.java`：实际路由挂auth.messagesRead及共享write gate；空白名单请求，先检查owner标记存储再签票，故障503，不缓存短期票据。响应仅ticket/expiresIn/path/channel，无owner、peer、正文或消息关联。
- `src/main/java/org/b3log/symphony/util/ApiV3RealtimeSessions.java`：messagesChanged按actor及channel双筛选，和notifications共用受控owner失效循环但不混投；messageOwners只采集已认证messages连接。每次发送重新授权，失权/传输失败移除连接与所有索引，不建立私信在线状态或名单。
- `src/main/java/org/b3log/symphony/service/ApiV3RealtimeService.java`：pollMessages在写事务外读取已提交owner标记，私信seen缓存与通知/文章隔离；后台调度接线，源故障关闭连接以便重新取票同步。沿现有有界worker及finally释放线程JDBC资源。无mark/event消息游标，不声明逐条或跨节点有序送达。
- `src/test/java/org/b3log/symphony/util/ApiV3PrivateRealtimeRoutingTest.java`（新增）：真实生产路由提取经Latke mapping/RouteHandler、真实scope+write gate验证，只messages.read可到handler；token查找为适配器。
- `src/test/java/org/b3log/symphony/service/ApiV3SocketTicketServiceTest.java`：真实票据服务/SQL及SQLite验证messages专用权限、一次消费、父owner绑定、target篡改和token/user/client/expiry失效全阶段。
- `src/test/java/org/b3log/symphony/service/ApiV3RealtimeSessionsTest.java`：真实票据与registry，双方owner/同人多连接/notification/article/chat/pending隔离、空白名单事件、失权/传输失败索引清理。
- `src/test/java/org/b3log/symphony/util/ApiV3RealtimeChannelTest.java`：真实Netty管线、channel/facade/worker调度，私信owner标记隔离/去重、拒绝写事务快照、失权与源故障关闭；Authority、marker返回和全局Firewall是明确适配器。持久标记提交可见性与新旧写覆盖由通用专项独立验证。
- `src/test/java/org/b3log/symphony/util/ApiV3RealtimeProcessorTest.java`：真实HTTP middleware/输入/controller，Bearer/父ID绑定、messages.write等错误scope拒绝、自定义订阅字段拒绝、存储故障不签票和no-store回执；最终信封与token认证为适配器。
- `tools/api-v3-socket-test.mjs`：同步实际新Repository及TokenRepository先前新增的事务语句工具依赖，移除旧write-gate桩，加入真实Caffeine限额中间件、生产路由提取和新路由专项。`tools/api-v3-realtime-smoke.mjs`：增加可选messages.read取票/认证/重放/控制帧检查和空事件解析；只做语法检查，未连8080或生产。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-v3-inventory-and-design.md`、`docs/api-route-inventory.md`及本文件：新增公开取票/连接契约、首次/重连HTTP同步、合并/重复/丢失及无断点续传说明，更新当前矩阵而不抹除历史进度。

数据/部署：本批没有新表或schema变更，复用此前api_v3_private_revision和socket_ticket的channel/target列；须先完成相应私信及v3 Socket迁移，再部署Java/metadata。不自动修改客户端登记scope，已有messages.read令牌可用，messages.write不能读取。旧私信发送各步仍分开提交，多个提示最终引导读取当前状态，不承诺整个旧发送原子性。

安全与反漏扫源码结论：新增HTTP入口明确Bearer、messages.read及共享限额，Cookie/旧apiKey不能替代，无新匿名裸入口或anonymous.viewSkips豁免；WS仍精确无query路径、帧界限、显式防扫描/黑名单及首帧票据，不回退Cookie/Bearer/旧key。原BeforeRequestHandler/AnonymousViewCheckMidware/validateCaptcha未改。CSRF沿显式Bearer和票据，不增加Cookie授权；频道/owner不可由正文覆写，SQL绑定，空事件没有XSS正文/凭据/隐私或对方已读信息。没有用户远程地址、SSRF、上传下载或新批量业务写入；票据受原写预算和连接限额约束，不公开内部阈值。无新公网静态/CDN/sourceMappingURL、枚举目录或上传下载返回路径。全站静态/CDN/目录和真实匿名首访/IP黑名单/验证码解封/上传下载尚未联网重验，不能称完整发布检查通过。

人工完整链路：

1. 先审阅并完成此前v3/socket/私信迁移，尤其20261020_api_v3_private_realtime.sql及repository.json资源同步；通过IDEA重编译或重启。本轮已明确提醒，未收到确认，因此未连接本地旧服务做验收。
2. A/B/C分别持messages.read令牌；A和B POST /api/v3/me/messages/realtime-ticket（空体或{}）获得channel=messages/no-store回执；用path连接，等authentication.required再发送type=authenticate及ticket，得到ready且只有channel/expiresAt。首次ready立即查询本人会话/历史/未读，ping得到pong。同一票据第二次认证关闭，过期票据/URL凭据/非法控制字段拒绝。
3. A向B使用v3发送，A/B连接分别收到空messages.changed，C及同用户notifications/article/chat连接不收到；查询HTTP详情/历史/列表/未读正确。B标记已读只需B提示，A不能据此获知对方已读；A撤回后双方刷新，历史未知未读规则仍保留。旧普通/手动发送和认证申请私信、指定/全部已读及最终列表更新同样最终提示，旧发送分步仍可能多次提示。
4. 断开B，在断开期间发送/已读/撤回；B用新票据重连ready后主动查询当前HTTP状态，不等待历史提示补发。高频变更可以合并，多个窗口可重复，不能按提示次数计消息/推断逐条已读。父令牌过期/撤销、去掉messages.read、用户停用或客户端停用后，待用票据拒绝，已有连接在发送或定期检查时关闭；刷新后重新取票。
5. 无Bearer、Cookie/apiKey-only、messages.write/content.read/notifications.read-only取票拒绝；userId/peerId/scope/channel等额外请求字段422，标记表不可用503且不签票。提交前/回滚的标记不能触发事件，故障恢复后重新取票并HTTP同步；慢连接/写事务快照拒绝、worker线程连接释放单独确认。
6. 在可用开发多实例环境让另一节点提交同一数据库owner标记，当前订阅节点应发刷新提示；仅承诺已提交状态失效，不承诺每条跨节点有序送达。同时回归原通知/文章/chat频道、PC/移动旧私信完整操作与匿名/黑名单/验证码解封。

未验证：真实MySQL/多实例、完整IoC及Java应用编译、IDEA重启后的8080/真实网络与PC/移动浏览器、全量防漏扫。隔离fixture不替代这些运行验收；本批未运行Maven，无前端源码/产物改动，不需yarn构建。API.md需同步 https://fishpi.cn/article/1636516552191 。

实际验证结果：Java 25隔离Socket专项51组、通用事务专项157组、私信读取7组均通过。Socket新增5组覆盖私信票据/registry/facade/controller/真实路由，现有通知/文章/chat协议专项同时回归。node --check与git diff --check通过，路由717条、浏览器端引用398条。真实部署和全量目标未完成，不标complete/blocked/paused。


## 2026-10-20 头像设置读取与写入

用户确认新v3允许任意合法HTTPS图片地址，旧入口保持。新增GET/PUT /api/v3/me/settings/avatar，读profile.read、写settings.write及共享写预算；GET只查询当前有效owner的URL并经safeUrl投影，保留安全历史HTTP/相对地址。PUT只允许avatarURL，trim后非空、255 UTF-16、HTTPS且有合法host，无凭据/控制/空白/反斜线/越界端口；不要求扩展名、不请求远程图片、不改变签名query。

实际修改文件：

- `src/main/java/org/b3log/symphony/processor/ApiV3SettingsProcessor.java`：GET/PUT路由及控制器；PUT复用请求字段白名单和幂等键校验。
- `src/main/java/org/b3log/symphony/service/ApiV3AvatarQueryService.java`：仅查询URL和当前状态，读失败503且不输出驱动详情。
- `src/main/java/org/b3log/symphony/util/ApiV3Settings.java`：avatar字段映射、格式与长度校验。
- `src/main/java/org/b3log/symphony/util/UserSettingsMutation.java`：同一settings.avatar幂等事务中锁当前用户、重验有效状态，仅更新头像URL、上传类型和更新时间。沿用严格根事务与事务外缓存刷新，不改旧SettingsProcessor头像方法。
- `src/test/java/org/b3log/symphony/service/ApiV3AvatarQueryTest.java`、`ApiV3AvatarWriteTest.java`及`src/test/java/org/b3log/symphony/util/ApiV3SettingsTest.java`：真实投影/HTTP/鉴权/共享mutation/协调器专项及边界与回滚；SQLite、用户仓储、缓存、token查找为适配器，非MySQL/完整IoC。
- `tools/api-v3-private-message-test.mjs`、`tools/api-v3-settings-test.mjs`：包含头像专项并保留原私信/设置回归。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、本文件、`docs/api-v3-inventory-and-design.md`、生成路由清单：契约、变更、长期链路和人工验收记录。

人工完整链路（需先IDEA重编译/重启，本轮已提醒，尚未确认）：

1. A持profile.read/settings.write令牌，GET头像读取本人当前保存地址；B使用自己的令牌读到B头像。Cookie/apiKey-only请求401、只有profile.read写403、只有settings.write读403。query/body不能指定其他账号。
2. A可先以uploads.write取上传票据，使用file字段直传并取得公开URL，再以新幂等键PUT avatarURL；或直接提交外部HTTPS无后缀/带签名URL。成功返回URL-only，GET与数据库URL/type=2/更新时间一致，积分/其他资料/B头像不变。客户端按完整URL显示图片。旧页面仍沿用已有来源/缩放规则，外部头像在启用七牛来源限制时可能显示默认图，不能把设置回读成功当作全站展示验收。
3. 相同键同URL重试不重复写入；另一个键改成第二张头像后，重放第一键只返回原回执，GET仍为第二张。同键不同URL409。缺键、空/null/HTTP/javascript/凭据URL/反斜线/空白/非法端口/超255/额外字段422；新近停用用户不能新写。合法首尾空白被去除，255边界可保存。
4. 注入用户写入、回执保存及提交失败，头像/type/time和回执一起回滚，同键故障恢复后成功；缓存刷新在事务后执行。实际MySQL用双连接观察提交前不可见并竞争设置/停用，故障后查询当前状态再重试。回归旧头像入口、PC/mobile显示、签名URL与原图/静态模式，不宣称旧页面支持全部外部来源。
5. 反漏扫回归未登录、登录、黑名单/test、验证码解封、开发关闭风控，及静态/CDN、sourceMappingURL、目录枚举和上传下载路径。

安全源码结论：两入口明确Bearer+scope，不新增匿名入口或viewSkips；原黑名单、匿名访问验证、CaptchaProcessor链路未改。显式Bearer无Cookie回退，owner由身份固定，CSRF不依赖页面Cookie。字段白名单与参数化SQL限制注入/越权，URL按数据返回，客户端须正确编码；无远程fetch故无新增服务端SSRF路径，写操作受共享预算。只输出URL，不输出密码/手机号/token/IP等；图片URL本身可能包含调用方提供的签名query，客户端须妥善处理。无新公网静态/CDN/source map/目录/上传下载接口，上传仍走既有票据。本轮没有新表/迁移/前端改动，无需yarn构建。

未验证：真实MySQL行锁与并发、完整Java应用编译/IoC、IDEA重启后8080及PC/mobile浏览器、真实图床上传、外部图片可达性、全站反漏扫。未运行Maven。API.md需同步网站API文章 https://fishpi.cn/article/1636516552191 。全量v3仍有待实现接口与联调，不标完成。

实际验证：`node tools/api-v3-settings-test.mjs` 8组通过；`node tools/api-v3-private-message-test.mjs` 13组通过（私信7、头像读3、头像写3）；`node tools/api-v3-write-test.mjs` 21项协议/幂等/草稿测试与6组真实Latke提交故障测试通过。后者验证共享严格事务，不冒称真实MySQL头像故障。两个变更runner的node --check及git diff --check通过；生成719路由、398浏览器引用。


## 2026-10-20 MBTI 本人读取与选项目录

上轮头像GET/PUT及事务/文档/隔离测试已经完成，是实际进展。本轮继续全量设置能力，新增GET /api/v3/me/settings/personality和GET /api/v3/me/settings/personality-options，均明确Bearer+profile.read。前者只SELECT mbti及当前owner/有效状态条件，值通过16种标准形式及可选-A/-T白名单，未设置或损坏历史值返回空字符串；后者返回types/variants新数组，无数据库读取。未新增PUT或改变旧资料/注册MBTI行为：已向用户询问v3无效输入是422还是完整沿用旧清空/截取，答复前不实施依赖该规则的写入。

本轮实际文件：

- `src/main/java/org/b3log/symphony/processor/ApiV3SettingsProcessor.java`：两条GET及profile.read中间件，身份来自v3认证属性。
- `src/main/java/org/b3log/symphony/service/ApiV3PersonalityQueryService.java`：当前有效owner、字段投影、绑定SQL及503脱敏错误。
- `src/main/java/org/b3log/symphony/util/ApiV3Personality.java`：类型/后缀目录及MBTI-only DTO。
- `src/test/java/org/b3log/symphony/service/ApiV3PersonalityQueryTest.java`：SQLite SQL/路由/控制器/profile.read/账号隔离/存储故障/48种类型后缀组合及选项无需SQL测试。
- `tools/api-v3-private-message-test.mjs`：加入真实新服务/工具类/路由绑定与专项，保留私信和头像回归。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、本文件、`docs/api-v3-inventory-and-design.md`及生成路由清单：对外契约、修改清单和验收记录。

人工完整链路：

1. 先IDEA重新编译或重启（本轮已明确提醒，未收到确认），A/B分别持profile.read令牌读取/me/settings/personality，返回各自mbti且只有code/data.mbti，不返回手机号、密码、邮箱/IP/UA。已有正常类型含INTJ和INTJ-A均回读，未设置为空。
2. A调用/personality-options，返回16个不重复types及A/T，组合为无后缀或type-variant；检查PC/移动客户端选项一致。此端点不依赖用户表查询，不虚构已经支持MBTI写入。
3. 无Bearer、无效Bearer、Cookie/apiKey-only为401，settings.write-only为403；query.userId/旧Cookie不能改身份。当前停用或缺失用户的本人MBTI查询404，非法ID422；用户表读失败503，不输出驱动异常。选项读取仍由实际Token认证检查账号有效性。
4. 测试存储中含HTML、非法类型或尾随多余内容时响应为空，不把未校验字段直接输出；响应Cache-Control为no-store。回归旧PC/移动设置与注册的MBTI行为未变，旧正常来源仍可设置后由v3读取。
5. 回归全站匿名首访/登录/黑名单/test/验证码解封及开发关闭风控；重新检查静态/CDN版本、sourceMappingURL、目录枚举、上传下载路径，不能以本轮无相关改动代替全量验收。

安全与反漏扫源码结论：两个新入口均显式Bearer/profile.read，无Cookie/apiKey回退或新增viewSkips，原匿名盾/黑名单/验证码链未改。只读无Cookie授权写操作，无新增CSRF写路径；owner由认证属性固定，参数化SQL并重验当前状态，DTO只有MBTI或目录，没有联系方式/凭据/IP/UA。类型不含HTML/URL，未新增SSRF/注入/上传下载/批量写路径。无新静态/CDN/sourceMappingURL或目录，无数据库写入/新增表/前端改动，不需迁移或yarn构建。完整API仍有规则待确认及其他模块待实现。

未验证：完整应用Java编译/IoC、真实MySQL、IDEA重启后的8080与PC/移动浏览器、真实匿名/黑名单/验证码及全站静态/CDN/上传下载反漏扫。未执行Maven；隔离Token/仓储/响应适配器不替代实际HTTP和运行环境验收。请将API.md同步到网站API文章 https://fishpi.cn/article/1636516552191 。

实际验证：Java25隔离runner `node tools/api-v3-private-message-test.mjs` 17组通过（私信7、头像读3/写3、MBTI4），包含两条真实Dispatcher声明、HTTP控制器及profile.read授权、MBTI SELECT与目录无SQL断言。node --check与git diff --check通过。生成721路由、398浏览器引用；已复核PC/mobile的home/settings/profile.ftl均仍用原userMbti字段与旧链路，未改模板或产物。


## 2026-10-20 MBTI 严格枚举写入

用户已选择严格枚举、无效422，覆盖上一节的待确认状态。PUT /api/v3/me/settings/personality明确settings.write和共享写门禁，只允许mbti字符串与Idempotency-Key；16种ASCII类型，可选-A/-T，trim和Locale.ROOT大写，空字符串清空。规范化内容参与settings.personality幂等摘要；旧key重放只返回原MBTI回执，不覆盖后续修改/清空。没有修改旧SettingsProcessor.checkMBTI、资料页或注册策略。

实际修改文件与行为：

- `src/main/java/org/b3log/symphony/processor/ApiV3SettingsProcessor.java`：PUT路由、settings.write/write门禁及updatePersonality调用现有协调器。
- `src/main/java/org/b3log/symphony/util/ApiV3Personality.java`：用户确认的新规范化，先ASCII枚举检查再Locale.ROOT，拒绝尾随片段/相似Unicode字符。
- `src/main/java/org/b3log/symphony/util/ApiV3Settings.java`：personality API/存储白名单、规范化后最多6字符和MBTI-only DTO。
- `src/main/java/org/b3log/symphony/util/UserSettingsMutation.java`：已有事务内锁用户、重验有效状态、重新校验MBTI，仅写mbti和更新时间。
- `src/main/java/org/b3log/symphony/service/UserSettingsMgmtService.java`：复用原ProfileWriteQuota，失败可消耗尝试额度但不提交设置/回执，不宣称额度随SQL回滚。
- `src/test/java/org/b3log/symphony/service/ApiV3PersonalityWriteTest.java`、`ApiV3AvatarWriteTest.java`及`src/test/java/org/b3log/symphony/util/ApiV3SettingsTest.java`：真实控制器/路由/共享协调器/更新primitive与SQLite适配器，覆盖48种类型组合、土耳其区域设置、ASCII边界、清空、规范化后重放、同键冲突、额度、当前账号状态和用户写入/回执/提交故障。
- `tools/api-v3-private-message-test.mjs`、`tools/api-v3-settings-test.mjs`：编入新写入专项，保留私信/头像/设置回归。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、本文件、`docs/api-v3-inventory-and-design.md`和生成路由清单：契约、当前矩阵、文件清单与人工链路。

人工完整链路：

1. 先IDEA重编译或重启（本轮明确提醒，尚未确认），A持profile.read/settings.write，GET选项及当前值，再PUT {mbti:" intj-a "}带新key。应200且mbti=INTJ-A，GET回读一致；B的数据、头像、积分和其他资料不变，仅A的mbti及更新时间改变。PC/移动旧资料页读取相同保存值，旧表单/注册的输入兼容性不变。
2. 另一个key改为ENFP后重放第一个key（大小写/空白不同但规范化值相同），应返回INTJ-A而GET仍ENFP；同键不同类型409。新key提交空字符串清空并GET为空。连续操作耗尽原资料额度时429，重放已成功key仍可读取回执，当前设置不被覆盖。
3. 缺Bearer、Cookie/apiKey-only401，profile.read-only写403；无key、缺字段/null/数字/INTJ-A-extra/未知类型/内部空白/额外userId字段422。当前停用用户新写404。测试非法输入与重复请求没有额外业务写入或保留失败回执；mbti格式不能承载HTML/URL/SQL。
4. 在开发数据库注入用户UPDATE、幂等回执保存及提交失败，旧mbti/更新时间与回执一起回滚；恢复后同key重试成功，确认缓存失效在事务结束后。真实MySQL并发设置/停用、双连接可见性、提交结果不确定后重新GET和同key重试需实测。
5. 回归匿名首访/登录/黑名单/test/验证码解封和开发关闭风控；全站静态/CDN版本、sourceMappingURL、目录枚举、上传下载返回路径单独复查。

安全源码结论：Bearer+settings.write及共享写预算，无Cookie/apiKey回退、新匿名入口或anonymous.viewSkips；旧黑名单/AnonymousViewCheck/CaptchaProcessor未改。只认当前认证owner并锁当前用户，有界ASCII枚举及JSON白名单阻断跨用户/注入/XSS载荷；SQL参数绑定、响应仅MBTI，无凭据/手机号/IP/UA。显式Bearer不新增Cookie授权CSRF路径，无URL抓取/SSRF、新批量能力、静态/CDN/sourceMappingURL/目录或上传下载变更。本轮无新表、资源或前端改动，无需迁移或yarn构建。旧资料额度继续共用，失败不消耗业务数据但可能消耗尝试次数；测试适配器不证明MySQL锁或多实例额度。

未验证：真实MySQL并发及提交、完整Java编译/IoC、IDEA重启后的8080与PC/移动浏览器、全量匿名/黑名单/验证码和静态/CDN/目录/上传下载反漏扫。没有运行Maven，不能用隔离测试冒充运行验收。API.md需同步网站API文章 https://fishpi.cn/article/1636516552191 。文章热度的旧来源与v3订阅计数本轮已梳理，但尚未新增接口；其他设置、红包/支付、应用登记管理、鱼游OAuth等完整范围仍待完成。

实际验证：Java25 `node tools/api-v3-settings-test.mjs` 9组通过，`node tools/api-v3-private-message-test.mjs` 20组通过（原17组+MBTI写3组），总29组。写入专项真实使用ProfileWriteQuota及协调器，SQL/缓存/Token为适配器；提交故障为SQLite适配器拒绝，不代替真实MySQL。两个runner的node --check和git diff --check通过，生成722路由、398浏览器引用，所有测试进程已正常退出。


## 2026-10-20 用户要求的全量盘点与子代理并行批次

用户明确要求整理已完成/待完成并拆分子代理同时推进。本批三名子代理分别完成全量可达路由清单、表情设置读取、红包/支付链路盘点与类型目录；主代理补文章热度、总接线和公共文档。当前快照见[完整接口与剩余清单](api-v3-progress.md)：181条可达注册（180HTTP+1WS），源码实现不等同真实运行完成。历史矩阵曾把文章热度/在线人数误标为已实现，本轮新增v3热度GET后才有对应入口；仍只提供当前节点近似连接数。

本批实际文件与行为：

- `processor/ApiV3Processor.java`：接线ApiV3ArticleHeatProcessor、ApiV3EmotionProcessor及ApiV3RedPacketProcessor。
- `processor/ApiV3ArticleHeatProcessor.java`、`service/ApiV3ArticleHeatQueryService.java`：GET /articles/{articleId}/heat，content.read、当前文章/作者/读者/讨论邀请授权，失败不输出计数；返回articleId/heat白名单。
- `util/ApiV3RealtimeSessions.java`、`service/ApiV3RealtimeService.java`：仅本节点已认证文章订阅按目标计连接数，逐连接当前授权，撤销/失效关闭，无身份返回；热度与旧ArticleChannel.ARTICLE_VIEWS相加，负旧计数归0并以long避免相加溢出，不改旧热度路由或广播。
- `processor/ApiV3EmotionProcessor.java`、`service/ApiV3EmotionQueryService.java`、`util/ApiV3EmotionOptions.java`：四个本人表情GET，profile.read、当前账号状态、分组owner与分页条件、URL与字段白名单；默认目录/常用顺序保留，脏历史有界，无DB写/远程抓取。
- `processor/SettingsProcessor.java`：159项旧二维目录逐字比对等价后提取到ApiV3EmotionOptions，字段仍通过深副本带原EOF使用；PC/mobile模板引用不变，未改旧写接口或业务输入规则。
- `processor/ApiV3RedPacketProcessor.java`、`util/ApiV3RedPacketTypes.java`：content.read的红包5类型目录、心跳/猜拳风险及创建/领取未开放标志，不返回原始who/手势/凭据。
- `src/test/java/org/b3log/symphony/service/ApiV3ArticleHeatTest.java`、`ApiV3EmotionQueryTest.java`，`src/test/java/org/b3log/symphony/util/ApiV3RedPacketRoutingTest.java`、`ApiV3RedPacketTypesTest.java`，`tools/api-v3-socket-test.mjs`、`api-v3-emotion-test.mjs`、`api-v3-red-packet-test.mjs`：实际路由/控制器/授权和SQL适配器、目录专项；保留原Socket回归，隔离fixture不代替完整应用。
- `docs/api-v3-progress.md`、`docs/api-v3-settings-work.md`、`docs/api-v3-chat-payments-work.md`：完整181项、遗漏能力、任务拆分、依赖/待确认、文件清单与人工/安全验收。API.md、UPDATE_LOG.md、AGENTS.md、本计划、inventory-and-design及生成route-inventory同步当前状态。

剩余队列不缩小：账号手机号/邮箱/密码/MFA与普通积分改名；表情写入/分组/图片/分享；OAuth客户端管理及协议端点规则；鱼游OAuth投稿/编辑；红包创建/详情/领取与持久事务；支付订单/跳转；职业/勋章/会员用户能力；文章评论历史/举报、匿名创作列表；邀请/积分转账/导出、复读机及云存档命名空间。公开支付回调、后台/金手指/游戏计分仍在原内部范围外，不能将用户能力全部排除。各模块明确缺核心/入口/规则/运行证据，详见清单。

人工完整链路（先IDEA重新编译/重启，本轮已提醒，未收到确认）：

1. 审阅清单181条与总register；在已迁移v3环境启动，确认新三个Processor可被IoC解析，无重复路由。A/B持各自profile.read/content.read令牌，不带Bearer/仅Cookie或旧apiKey拒绝，只有错误scope403。
2. A读取常用别名/目录，默认账号与自定义账号各测；PC/mobile设置选择器顺序、EOF分组不变。A/B图片分组和条目只能读取本人，foreign/missing组404，分页大小和末页边界正确，无分组GET不创建数据，删除/悬空图片不返回，坏URL/超长字段不输出原危险值；DB失败503不带驱动文本。
3. 可读文章GET heat在无连接时0；同节点旧页面与v3文章订阅分别连接/关闭，连接数近似变化，多个标签页分别计数，不以之推断去重或跨节点全站人数。讨论邀请移除、文章删/无效、作者封禁、读者停用后拒绝读取；v3过期/撤销订阅不计数并关闭。热度查询不包含任何用户列表、正文、IP或UA。
4. GET红包目录为5类型，心跳/猜拳mayLosePoints=true，creationSupported/claimSupported=false，骰子不列入；无Bearer401、content.write-only403。原红包摘要、PC/mobile旧操作保持，勿以目录存在推断已支持v3领取/付款，不为验收发起真实付费操作。
5. 风控启用环境回归黑名单/test、验证码解封、匿名首访/计次、开发关闭风控；全站公网静态/CDN版本、sourceMappingURL、目录枚举与上传下载返回路径另行验证。本轮未做全站网络扫描，不把无相关新增等同完整安全验收。

安全源码结论：六个GET均显式Bearer/scope，原匿名盾/黑名单/验证码及anonymous.viewSkips未改；不新增匿名绕过或Cookie授权写路径。所有权/当前文章可见性/参数化SQL、白名单DTO及有界分页已覆盖隔离测试；文本与URL仅数据返回，客户端仍需编码。无新服务器URL抓取/SSRF或业务写入，无新增Repository/SQL迁移、JS/SCSS/FTL/静态/CDN/source map、目录、上传下载通道；故无需yarn构建。读接口仍受原顶层防滥用链，真实压力/分布式风控未验证。红包支付旧链缺核心约束已记内部work文档，未在公开文档披露实现细节，也未擅改旧兼容行为。

未验证：完整Java应用编译/IoC、真实MySQL并发与迁移、IDEA重启后的8080/真实WS及PC/mobile浏览器、全站反漏扫与第三方网络。未运行Maven或改真实数据库/生产。API.md须同步网站文章 https://fishpi.cn/article/1636516552191 。完整目标仍active，不能将本批只读进展冒称所有接口完成。

本批最终验证：Java25隔离执行 `node tools/api-v3-socket-test.mjs` 55组通过，`node tools/api-v3-emotion-test.mjs` 5组通过，`node tools/api-v3-red-packet-test.mjs` 1组通过；前者包含新增热度与红包的实际Dispatcher/控制器/授权专项。生成清单为全仓库728路由、398浏览器引用，v3独立口径181条。隔离依赖/SQLite不替代真实MySQL及应用启动验收，不能推广为181条均已运行通过。


## 2026-10-20 第二轮子代理并行：职业、勋章与会员

用户要求按剩余任务继续分代理同时实现，分工如下：inventory负责职业目录和本人职业读取；settings负责本人和公开展示勋章读取；chat_payments负责会员档位和本人状态读取。主代理独占总注册、API.md、UPDATE_LOG.md、AGENTS.md、进度及计划汇总，避免交叉覆盖。各模块使用独立SQL/DTO及隔离专项；旧勋章查询含过期删除、旧会员查询含原始配置，不直接复用这些副作用或裸返回。具体接入及最终证据在本节后续补齐。

本轮新增规则确认：OAuth客户端采用仅管理员登记和维护，用户已明确答复；不开放自助申请/登记。此前已批准应用登记核心继续保留，HTTP管理入口、管理员证明与审计、幂等、回调修改/停用撤权协调仍属于后续工作，不能将管理策略确认记成接口已完成。鱼游OAuth校验入口仍待用户提供。


本批实际修改文件与行为：

- 新增 `processor/ApiV3ProfessionProcessor.java`、`service/ApiV3ProfessionQueryService.java`：职业目录与本人状态/进度两GET，已发布目录先过滤再分页；本人按历史版本读进度、保留隐私配置，无进度不插零级，不开放他人职业资料。
- 新增 `processor/ApiV3MedalProcessor.java`、`service/ApiV3MedalQueryService.java`、`util/ApiV3MedalDto.java`：本人含隐藏与公开展示两GET，所有者/状态/过期条件在计数和分页重复绑定；不触发旧查询的过期删除，说明按模板替换后转纯文本，不输出raw data/medal_attr/持有记录ID。
- 新增 `processor/ApiV3MembershipProcessor.java`、`service/ApiV3MembershipQueryService.java`、`util/ApiV3MembershipDto.java`：本人会员状态与分页档位两GET，当前账号、状态/过期重查，配置/权益typed白名单；保留durationType/durationValue字段，过期返回inactive，畸形可选项略去、异常核心数值或重复有效记录503。
- 新增 `src/test/java/org/b3log/symphony/service/ApiV3ProfessionQueryTest.java`、`ApiV3MedalQueryTest.java`、`src/test/java/org/b3log/symphony/processor/ApiV3MembershipTest.java`，及 `tools/api-v3-profession-test.mjs`、`api-v3-medal-test.mjs`、`api-v3-membership-test.mjs`：实际生产查询/控制器/Dispatcher/鉴权专项，隔离依赖与SQLite。各runner临时目录清理，不连接实际服务。
- 修改 `processor/ApiV3Processor.java`：总接线三个Processor；修改 `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、本计划、`docs/api-v3-progress.md`、`docs/api-v3-inventory-and-design.md`、生成 `docs/api-route-inventory.md`：契约/最新187条清单/管理员OAuth决策/分工和测试记录；新增三份 `docs/api-v3-{profession,medal,membership}-work.md` 为模块完整人工与风险/缺口交接。

人工完整链路（联调前须通过IDEA重新编译或重启，本轮已明确提醒，未确认实际执行）：

1. 启动已完成既有迁移的开发环境，确认三Processor可IoC解析、六路由无冲突。A/B取得profile.read/content.read令牌；本人三个入口缺Bearer或仅Cookie/apiKey为401，错误scope403；p=0/size=51为422。无新表迁移，无JS/SCSS/FTL变更，不需yarn构建。
2. 进入PC/mobile职业页，读目录→本人状态/进度→与页面核对；目录只含已发布版本，分页稳定。SELF_ONLY/FULLY_HIDDEN本人仍可读，A/B互不串数据；选择主职业但无进度时state已选且list可空，旧版本退役后仍显示对应历史等级。非法隐私JSON/版本/等级503，不写入任何记录。
3. PC/mobile勋章页→本人list→公开用户名list：本人见有效隐藏项，公开仅展示且有效，顺序/total与分页一致；到期/隐藏/停用及他人目标不泄漏，GET不删除过期记录。公开接口匿名需先完成既有访问校验，显式无效Token401/缺profile.read403；模板变量描述只返回纯文本，无raw样式/数据。
4. PC/mobile会员页→档位分页→本人状态：档位价格/周期/支持权益一致，A/B读取各自配置，无会员/过期inactive，永久expiresAt=0；畸形可选配置不会透出，数据库失败/核心数值损坏503。读取不扣积分、送包、自动续期或写配置；不发起真实支付。
5. 两个新增匿名入口（职业目录、公开勋章）回归BeforeRequestHandler黑名单→/test、匿名首访/第五次、validateCaptcha解封、开发关闭盾差异；所有接口复核权限/白名单、XSS显示、绑定SQL、负载滥用与日志。静态/CDN版本、sourceMappingURL、目录枚举及上传下载返回路径需全站另验。

安全与反漏扫源码结论：本人接口只认Bearer/scope，owner不可从query/body/Cookie替换；公开两GET显式optionalProfile后接AnonymousViewCheckMidware，没有anonymous.viewSkips新增，原BeforeRequestHandler/CaptchaProcessor未改。只读无业务写入或Cookie授权写路径，CSRF不产生状态变更；分页/ID/JSON有界且SQL参数绑定，DTO无联系方式/凭据/IP/UA/原始配置；文本返回供客户端编码，勋章描述转纯文本；无服务器抓取URL/SSRF或上传下载/静态/CDN/source map/目录新增。读取仍依赖既有顶层防滥用，未证明真实压力或分布式限额。

主代理最终复跑：Java25 profession 6组、medal 4组、membership 7组均通过且进程退出0；node语法与git diff --check通过。勋章专项确实执行生产匿名中间件首访/计次逻辑，职业匿名钩子为适配器，不能混淆两者证据。源码可达注册187条（GET104/POST37/PUT26/DELETE19/WS1），与逐条清单相同且无重复；全仓库生成734路由/398浏览器引用。未运行Maven、完整Java应用编译/IoC、真实MySQL或8080/真实WS、PC/mobile浏览器、生产及全站反漏扫。API.md仍须同步 https://fishpi.cn/article/1636516552191 。职业/勋章/会员写入及其他未完成项保留在队列，不以本批读取宣称整模块完成。


## 2026-10-20 首次累计提交前检查

用户要求先提交并推送现有成果、再开发剩余任务。提交范围为当前api_v3分支此前累计的v3接口/事务核心、用户已批准的旧入口兼容修复、SQL/Repository、PC/mobile模板与源码/生成JS、专项和文档。已执行corepack yarn run build（环境没有独立yarn命令，未自动修改packageManager），重建产物无额外CSS或依赖文件差异；生产JS版本扫描99文件、0发现。重新执行api-v3-chat-event-test、api-v3-socket-test、api-v3-private-message-test、api-v3-write-test均退出0；四组为隔离回归证据，不代替完整应用。git diff --check、JSON结构及新Repository字面名称/registry匹配检查通过，源码路由187条的既有清单保持。

匿名/API认证边界、黑名单/验证码链及viewSkips已按源码和前述专项记录复核；本次未改风控例外或新增路径。静态变更仅已记录JS及生成物，PC/mobile模板同步已在既有批次记录；没有新增公网目录/下载或凭据文件。仍未验证完整Java/IoC、真实MySQL迁移/事务竞争、IDEA重启后的HTTP/WS及浏览器、全站CDN/目录/上传下载和真实黑名单解封，不将Git推送等同部署/发布验收。未执行Maven、未访问生产或真实支付。


## 2026-10-20 已推送后第三轮：主职业、勋章显示与OAuth管理

用户要求先提交推送再继续开发，已完成累计提交`6231c4f00352c9bbaeefbb7a32804c7aebea289d`并通过本机SSH推送`origin/api_v3`，分支追踪已设置。HTTPS终端缺凭据，推送使用仅本次命令的remote.origin.pushurl覆盖，没有永久改远端URL。此前累计595文件/187接口为该提交范围；本批新增内容保持未再次提交，不把它混入已推送成果。

本批实际新增/修改：

- `processor/ApiV3ProfessionPrimaryProcessor.java`、`service/ApiV3ProfessionPrimaryService.java`、`src/test/java/org/b3log/symphony/service/ApiV3ProfessionPrimaryTest.java`、`tools/api-v3-profession-primary-test.mjs`、`docs/api-v3-profession-primary-work.md`：PUT /api/v3/me/professions/primary，settings.write/幂等/免费/任意当前发布职业，无需经验，不插progress；当前user→profession→privacy锁，只按列改selection及userProgressVersion/时间，保留隐私。默认缺记录时建既有默认privacy。旧privacy整行写并发覆盖仍是局限，不声称新旧整体原子化。
- `processor/ApiV3MedalDisplayProcessor.java`、`service/ApiV3MedalDisplayService.java`、`util/ApiV3MedalDisplay.java`、`src/test/java/org/b3log/symphony/service/ApiV3MedalDisplayTest.java`、`tools/api-v3-medal-display-test.mjs`、`docs/api-v3-medal-display-work.md`：PUT /api/v3/me/medals/{medalId}/display，settings.write/幂等/严格布尔/原样字符串ID含0/Unicode、128 UTF-16且拒孤立代理项；actor→定义→本人持有锁仅改display。过期/无目标404，重复定义/持有或坏存量409，不随机选择或改变顺序/积分。
- `processor/ApiV3ClientManagementProcessor.java`、`service/ApiV3ClientManagementService.java`、`util/ApiV3ClientManagementInputs.java`、`util/ApiV3ClientManagementDto.java`、`src/test/java/org/b3log/symphony/processor/ApiV3ClientManagementTest.java`、`tools/api-v3-client-management-test.mjs`、`docs/api-v3-client-management-work.md`：管理员GET集合/详情、幂等POST登记，当前第一方Token/管理员DB重验；普通、OP、第三方管理员Token均不能进入。当前根按账号重置顺序锁客户端命名空间→website/user/token/request，重放也重验权限；复用已批准登记primitive，client/callback/receipt同提交。用户已确认20回调/2048字符并实际验证边界，存储metadata/回调仍有界白名单，不修改内置client，不开放停用/回调/权限维护。
- `processor/ApiV3Processor.java`总接线三个模块；API.md/UPDATE_LOG.md维护5个完整新契约，AGENTS.md记录长期链路，progress与inventory-and-design及生成api-route-inventory同步最新计数。无新增Repository/SQL/配置、FTL/JS/SCSS或生成前端资源变更。

实际验证：主代理最终复跑Java25 `api-v3-profession-primary-test.mjs`8组、`api-v3-medal-display-test.mjs`6组、`api-v3-client-management-test.mjs`10组均通过并退出0。生产路由/auth/write中间件/controller、查询/更新/注册SQL、严格幂等根及真实打包JdbcTransaction、保存点参与隔离测试；用户/Token查找及响应信封、SQLite方言/连接池构造为明确适配器。包含提交前拒绝、提交后确认丢失、局部回滚及回滚失败、失效账号/降权后的原key、20/2048边界，不能推广为真实MySQL并发或完整IoC验证。真实测试不是全应用Maven编译，未运行Maven。

人工完整链路（先用户IDEA重新编译或重启，本轮已明确提醒，未确认）：

1. A/B登录取settings.write及profile.read。职业目录GET→A PUT主职业→GET本人状态→PC/mobile职业页核对，B不受影响、无扣费或假progress。换新key选B职业，再重放旧key只回旧回执但当前选择不回退；未发布/退休/不存在404、非法字段/key/JSON422或400、缺Bearer401/缺scope403；停用后旧key也拒绝。
2. A GET本人勋章→PUT display=false→本人GET状态false→公开GET不见→PC/mobile勋章展示核对。新key设true后重放旧key不隐藏当前勋章。B持有/过期/缺失404、重复持有或损坏状态409；异常布尔/路径/控制/孤立代理项拒绝，不扣费/不改排序，数据及回执失败一起回滚。
3. 管理员第一方登录→客户端列表→带新key登记HTTPS/原生回调→列表/分页详情→PC/mobile OAuth同意页→S256换码→资源读取/轮换撤销。对普通/OP/第三方管理员、假Cookie/apiKey、旧scope/过期或撤销Token及降权后重放均拒绝；20/2048成功、21/2049/重复/片段/凭据/响应保留参数/非开发HTTP回环422。提交确认丢失原键恢复同clientId，不换键重复登记。
4. 开发数据库注入数据/回执/提交失败，与账号重置、职业增长/退役、旧privacy/勋章授予/续期/排序并发验证锁和版本；这部分真实MySQL未执行。顺序注入/SQLite不能作多连接证明。
5. 回归新接口未鉴权/越权/CSRF/XSS/SQL注入/SSRF/批量滥用/敏感字段，及原黑名单/test、匿名首访/第五次、validateCaptcha解封/开发关闭风控；全站静态/CDN版本/sourceMappingURL/目录枚举/上传下载路径另验。

安全/反漏扫源码结论：5路由均显式Bearer+scope，管理再做当前管理员及第一方校验，无新匿名入口、anonymous.viewSkips或旧防扫描中间件改动；不接受Cookie/apiKey授权写入，所以无新增Cookie写CSRF路径。owner/目标严格绑定、SQL参数化、JSON/路径/条数有界、写门禁先鉴权；DTO不返凭据/IP/UA/联系方式/完整用户或raw配置，无HTML渲染/远程URL抓取/SSRF/新增静态/CDN/source map/目录/上传下载。旧Cookie/apiKey兼容入口保持，不冒称旧写入一起原子化；分布式压力、全站门禁与公网资源仍未做真实验收。

最新源码可达192条（GET106/POST38/PUT28/DELETE19/WS1），与progress逐条清单完全相同且无重复；全仓库生成739路由、398浏览器引用。未验证完整Java/IoC、IDEA重启后HTTP/WS、真实MySQL迁移/并发、PC/mobile浏览器、OAuth第三方实联及全站反漏扫；未访问生产或真实支付。完整v3尚有停用/回调维护、会员/职业隐私/勋章排序等剩余任务。API.md须同步网站 https://fishpi.cn/article/1636516552191 。

第三轮最终静态复核：3个新runner的node --check及tracked/untracked全部文件git diff whitespace检查通过；新增接口对应9个curl代码块bash -n通过（只解析，没有请求网络）；生产JS版本复扫99文件/0发现，未改静态产物。源码192路由与progress逐项METHOD/path无重复且全等，新5契约均存在API.md。


## 2026-10-20 第四轮并行：匿名创作、邀请码与客户端停用

本批独立分工：anonymous_content负责本人匿名列表，invitation_read负责邀请码只读状态，oauth_disable负责管理员停用与后续维护；主代理维护总注册、公开文档和交叉验证。以下四接口已接线，后续客户端PUT另记录。

实际文件与行为：

- `processor/ApiV3AnonymousContentProcessor.java`、`service/ApiV3AnonymousContentQueryService.java`、`util/ApiV3AnonymousContentDto.java`、`src/test/java/org/b3log/symphony/service/ApiV3AnonymousContentQueryTest.java`、`tools/api-v3-anonymous-content-test.mjs`、`docs/api-v3-anonymous-content-work.md`：本人匿名文章/评论两GET，content.read，仅元数据；按当前文章/作者/邀请可见性过滤后计数分页。讨论邀请复用共享解析，候选有界、内容hash约束，超预算503不截断；管理员例外为Java精确角色判断，避免MySQL大小写不敏感扩大权限。
- `processor/ApiV3InvitationProcessor.java`、`service/ApiV3InvitationQueryService.java`、`util/ApiV3Invitation.java`、`src/test/java/org/b3log/symphony/service/ApiV3InvitationTest.java`、`tools/api-v3-invitation-test.mjs`、`docs/api-v3-invitation-work.md`：POST邀请码状态查询，activity.read、共享请求预算；1024字节JSON与16位字母数字码，仅返存量status，unused不保证当前可注册；不返回原码/预留/手机/用户，无写入或幂等回执。
- `processor/ApiV3ClientDisableProcessor.java`、`service/ApiV3ClientDisableService.java`、`src/test/java/org/b3log/symphony/processor/ApiV3ClientDisableTest.java`、`tools/api-v3-client-disable-test.mjs`、`docs/api-v3-client-disable-work.md`：管理员DELETE，当前第一方管理员/settings.write/幂等；客户端逻辑停用、全部Token及回执同事务，保留登记和回调；不允许website_v3或重新启用。连接按后续当前授权检查失效，不能声称即时全球断开。
- `processor/ApiV3Processor.java`总接线；`tools/api-route-inventory.mjs`补识别共享budget与管理员检查；旧管理专项移除“无DELETE”过时断言；API.md、UPDATE_LOG.md、AGENTS.md及三份汇总文档同步。无新表、SQL、Repository、配置、FTL、前端源码或生成资源。

实际验证：主代理Java25最终重跑匿名内容10组、邀请码5组、客户端停用9组、管理回归10组，均退出0。包含生产SQL/路由/鉴权/控制器、严格事务及实际SocketTicket生命周期，依赖仍有SQLite/身份/请求适配器，不代替MySQL、IoC或真实服务。未运行Maven。

人工完整链路（已提醒Java需IDEA重编译或重启，未确认用户实际执行）：

1. A/B取content.read，GET本人匿名文章和评论→分页→按ID读既有详情→PC/mobile核对。仅本人元数据，无正文和匿名真实作者。撤回讨论邀请、改名/停用作者、隐藏/删除文章后重新GET，total/list同步排除不可读评论；p=0/size=51为422，坏元数据或权限扫描超预算503，数据库无修改。
2. activity.read令牌POST已知used/unused/stopped码→只返回status；未知404，非法字符/额外字段422，超过1024字节413；缺Bearer401/缺scope403。对照注册页确认unused仅存储状态，不绕过邀请码预留或实际注册验证。
3. 当前第一方管理员登记应用→用户OAuth同意/S256换码→获取HTTP与Socket票据→DELETE应用→管理详情status=1→旧access/refresh、授权码及待确认流程不可继续使用，已签Socket票据拒绝或既有连接后续授权检查失败。重复同key仅旧回执，新key停用已停用应用也清残余Token；普通/OP/第三方管理员、website_v3、降权后原key均拒绝。开发DB注入提交/回执失败应整笔回滚，与换码/刷新/账号重置并发另验。
4. 对新增四入口复核无Bearer/越权、SQL注入/JSON边界、XSS客户端编码、批量请求与敏感响应；回归旧黑名单/test、匿名首访/计次、validateCaptcha解封和开发关闭盾。没有新匿名入口、viewSkips或静态/CDN/source map/目录/上传下载路径改动；全站公网资源与真实验证码链仍需另验。

安全源码结论：均显式Bearer+scope，owner来自认证，管理每次及幂等重放重新核对当前第一方管理员；不引入Cookie授权写入、HTML渲染或远程抓取。SQL参数绑定、输入/输出白名单、预算有界。存量防扫描链与公开资源未改，不能把无改动当作全站反漏扫已通过。未验证完整Java/IoC、真实MySQL锁/隔离/提交不确定性、IDEA后HTTP/WS、第三方OAuth与PC/mobile浏览器；未访问生产或支付。API.md须同步网站API文章。


### 第四轮续：客户端完整修改与授权失效

用户确认修改回调或权限时全部旧授权失效并重新授权。新增`processor/ApiV3ClientUpdateProcessor.java`、`service/ApiV3ClientUpdateService.java`、`src/test/java/org/b3log/symphony/processor/ApiV3ClientUpdateTest.java`、`tools/api-v3-client-update-test.mjs`及`docs/api-v3-client-update-work.md`；修改`ApiV3ClientRegistrationService`只提取原有纯字段规范化供创建/修改共用，不改变创建规则。总注册与公开文档接入PUT /api/v3/admin/oauth/clients/{clientId}。

完整替换name/scope/redirectUris，保持当前status；以权限集合和精确回调集合判断授权实质变化，同事务更新登记、替换回调、撤销全部目标Token、删除全部旧授权码和pending请求并保存回执，保留approved/denied历史。名称变化与集合重排保留授权。旧回执不会覆盖之后的修改或停用；不开放website修改、自助登记、状态或owner覆盖、重新启用。当前第一方管理员锁和重放复核沿用登记/账号重置顺序。

补充人工链路：管理员GET详情→名称PUT→用户原access/refresh和已有同意流程仍可用；换新key改变scope或一项回调→GET详情为新值→旧access/refresh、旧code换码、旧pending确认均拒绝→用户重新发起同意/S256成功。再把定义改回原值，旧码/旧pending仍不可用。新key后续修改后重放第一key只能原回执，当前资料不回退；停用后PUT仍status=1。缺字段/超20回调/2049字符/重复URI/越权/降权重放不写；开发DB故障各阶段、提交确认丢失与并发换码/刷新需实库继续验证。

主代理复跑共享登记10组/停用9组均退出0；PUT专项11组通过，实际Code.exchange/Consent.decide验证删除的旧证明在定义改回后仍拒绝；覆盖每阶段写入/保存点/严格提交失败与提交后确认丢失。源码总197条（GET108/POST39/PUT29/DELETE20/WS1），已逐项核对progress完全相等无重复；全仓库生成744路由/398浏览器引用。Java/IoC/MySQL/HTTP/WS和浏览器等运行限制保持，无前端/SQL新增，未运行Maven或部署；本轮修改未再次提交。

第四轮最终静态检查：新runner及路由工具node语法、tracked/untracked whitespace检查通过；新增5份公开契约存在，5段curl通过bash -n（未请求网络）。主代理最终PUT11组exit0，管理10/停用9回归exit0；路由197条逐项匹配。未改公网静态资源，因此本轮未重新执行全站CDN下载或浏览器扫描；这些项目保持未验收。

客户端修改专项最终补充第11组真实SocketTicket：scope变更后原live授权false、旧票消费401、新取票401。主代理再次复跑11组退出0；四功能专项为35组，另共享管理回归10组，均为隔离证据。

## 2026-10-20 第二次累计提交前检查

用户要求先提交推送再继续。提交范围为第三/第四轮累计10个新增入口（187→197）、严格事务与查询/DTO、专项、公开文档和进度清单；无模板、JS/SCSS生成物、新SQL/Repository表变更。git diff及40个untracked whitespace检查通过；再次JS扫描src/main/resources/js共155文件、0版本/sourceMappingURL发现。定向专项沿用前述最终退出0记录，源码之后无业务变更，不重复无理由全量编译。当前新增入口均Bearer，管理额外第一方当前管理员复核；anonymous.viewSkips和黑名单/验证码链未改，无新增上传下载/公网目录。源码权限/输入/DTO/事务反漏扫结论见上述批次，真实MySQL、完整IoC、HTTP/WS/PC/mobile和全站公网反漏扫仍未验收。未运行Maven，不把推送等同部署。

## 2026-10-20 第二次推送后的第五轮并行

用户要求先提交推送再继续，已完成`c09e48cc1f36d9ddcbaaffecdf750d0b7a139f69`（50文件，4068新增/35删除）并推送origin/api_v3，远端6231c4f0→c09e48cc确认成功。此提交包含第三/第四轮10入口；以下开发均在该提交之后，不能当作已推送。

分工：anonymous_content负责本人职业详情/记录，invitation_read负责职业引导/隐私幂等写入，oauth_disable负责本人勋章相邻移动；各自独立Processor/Service/Inputs或DTO/专项/work文档，主代理审查、总接线与公开文档串行维护。沿用已明确旧规则：skip会清空已选主职业；隐私LEVEL_ONLY/PRIMARY_ONLY当前同样仅公开primaryProfession；勋章隐藏/过期持有参与相邻交换、并列用既有v3读取的display_order/oId稳定顺序，边界仍全量重编号，不删除过期项。本人职业详情与记录不伪造zero进度，无真实进度404。规则外的鱼游OAuth证明等继续保留缺口，不擅自开放。

## 2026-10-22 第五轮完成：职业详情/记录、引导/隐私与勋章排序

继续时前轮代理已结束，保留并接续已有草稿；新代理profession_details、profession_settings、medal_order按独立文件完成，medal_order另只读交叉审查职业设置，无实质问题。累计提交c09e48cc已推送，此后本批新增未再次提交。

实际文件与行为：

- `processor/ApiV3ProfessionDetailProcessor.java`、`service/ApiV3ProfessionDetailQueryService.java`、`util/ApiV3ProfessionDetailDto.java`、`src/test/java/org/b3log/symphony/service/ApiV3ProfessionDetailQueryTest.java`、`tools/api-v3-profession-detail-test.mjs`、`docs/api-v3-profession-detail-work.md`：本人详情/记录两GET，profile.read，按真实progress的历史revision/scheme读取等级与贡献；无progress404，近90天APPLIED非零经验真实count/page，白名单不含来源正文/内部ID。路线与贡献有技术预算，超则503不截断；末尾重查有效owner与progress快照，不能声称完整并发快照或跨实例实时。
- `processor/ApiV3ProfessionSettingsProcessor.java`、`service/ApiV3ProfessionSettingsService.java`、`util/ApiV3ProfessionSettingsInputs.java`、`src/test/java/org/b3log/symphony/service/ApiV3ProfessionSettingsTest.java`、`tools/api-v3-profession-settings-test.mjs`、`docs/api-v3-profession-settings-work.md`：引导/隐私两PUT，settings.write+幂等；每次及重放在严格根当前锁actor，再按列更新privacy/version，不覆盖其他状态。skip只接受SKIPPED并清主；privacy六预设/CUSTOM完整十模块，原预设映射保持，4096字节前置边界，坏版本/重复行不写。
- `processor/ApiV3MedalOrderProcessor.java`、`service/ApiV3MedalOrderService.java`、`util/ApiV3MedalOrderInputs.java`、`src/test/java/org/b3log/symphony/service/ApiV3MedalOrderTest.java`、`tools/api-v3-medal-order-test.mjs`、`docs/api-v3-medal-order-work.md`：POST相邻移动，settings.write+幂等；全部本人持有含隐藏/过期，order/oId稳定并列、边界也归一。只更新order，定义/持有唯一与typed校验，超4096技术预算503，不清理过期。actor→定义稳定序→持有稳定序，仅确认严格提交后成功；旧key不重复移动。
- `processor/ApiV3Processor.java`接线三个模块；API.md五份完整契约、UPDATE_LOG.md新增261022记录、AGENTS.md简记长期链路，progress/inventory-and-design和生成api-route-inventory同步202条清单；无新表/Repository/SQL/配置/FTL/前端源码或产物改动，不需要前端构建。

实际验证：主代理最终Java25复跑detail7组、settings11组、order8组均退出0；包含生产路由/控制器/鉴权/SQL、实际严格JDBC/保存点/提交故障与提交确认丢失。Settings与实际PrimaryService交替操作验证字段及重放隔离，Order覆盖4096成功与4097整请求拒绝。SQLite去FOR UPDATE、身份/连接/信封适配器等隔离条件见work，不代替MySQL竞争、完整IoC或真实服务。未运行Maven。

完整人工链路（本轮已明确提醒IDEA重新编译或重启，未确认实际执行）：

1. A/B取得profile.read/settings.write，GET职业目录→A已有进度详情→90天记录分页→PC/mobile职业页面核对。历史退休版本仍可读，已选但无真实progress404；零变化/非APPLIED/早于90天排除，正负变化和total正确。非法ID/size51为422，缺Bearer401/缺scope403，不返回source正文或内部标识；读取不创建进度。
2. A选主职业→PUT隐私SELF_ONLY→GET本人状态及公开职业页面按A/B/匿名核对→PUT引导SKIPPED→GET确认清主但隐私/经验保持。再选主后重放旧skip只回旧回执，不清新选择；新key才再次清空。各预设/CUSTOM十模块、非法/缺字段、同键冲突、UTF-8超4096返回413、停用后原key和新key拒绝，B状态不变。
3. A读取本人勋章→对有隐藏/过期邻居的目标POST up/down→重新GET与PC/mobile顺序核对，展示和有效期不变；边界不移出且编号规范。重复原key不再次移动，新key可再移动；Unicode/0勋章ID、方向大小写、空白方向422、他人持有404、损坏/重复409、预算超限503，失败无部分排序。
4. 开发DB注入业务/回执/保存点/提交前失败，无半写；提交确认丢失以原key恢复。与实际Primary、职业成长/退役、旧隐私整行写、旧勋章授予/显示/排序并发，验证MySQL锁、版本及死锁处理。旧入口没有一起改造，旧全行更新仍可能覆盖并发新值，不能宣称新旧整体原子化。
5. 回归无Bearer/越权/CSRF/XSS/注入/SSRF/批量/敏感数据，以及原黑名单→test、匿名首访/计次、validateCaptcha解封/开发关闭盾；全站CDN/版本/sourceMappingURL/目录枚举/上传下载路径仍需另验，不把无改动当作实际验证通过。

安全与反漏扫源码结论：五入口均显式Bearer+scope，owner不可由Cookie/apiKey或body替换；写入守卫先鉴权，重放当前用户重查，SQL参数化、输入/typed输出白名单且读取有界。无Cookie授权写路径或服务端HTML渲染、远程抓取、上传下载、新静态/CDN/目录；未改anonymous.viewSkips及黑名单/验证码链。原链路源码保持不等于完整运行验收；实际压力/分布式限额、全站扫描、MySQL/IoC/HTTP/WS/PC/mobile均未验证，未访问生产或支付。

下一工作包会员开通/配置已完成只读盘点：旧配置可写权益、优惠券无效可能加价20%、永久资格与赠包独立提交存在歧义；三条明确业务问题已向用户发出，等待答案后实施，不擅自选择收费或权益规则。鱼游OAuth证明等原依赖继续保留。API.md须同步网站API文章。

第五轮最终检查：tracked diff及18个untracked文件whitespace通过，三个runner的node语法通过；5份新公开契约及各一段curl经bash -n验证（只解析未发请求）。202条可达源码与progress METHOD/path逐项全等无重复，全仓库生成749路由/398浏览器引用。详情DTO最后补动作编码grammar后，主代理再跑7组退出0；settings11/order8最终记录不变。无前端/静态资源变更，本轮未重复yarn或CDN下载；没有新增SQL或迁移，仍未验证完整应用/真实MySQL/浏览器/全站反漏扫。

## 2026-10-22 第六轮：会员展示配置与积分开通

用户已明确三项规则：仅档位支持的bold/underline/color可改，其他权益只读，VIP4真实YEAR/MONTH两码额外八效果，开通由服务端defaults初始化；无效/非折扣/用尽券422且不扣费；有效或永久会员禁止重复购买，自然日期限和原档位礼包同事务。工作由membership_settings、membership_opening独占实现，membership_review只读交叉审；主代理修改公共接线/文档/索引迁移及旧入口最小锁序修复。没有实际支付或线上调用。

实际文件与行为：

- `processor/ApiV3MembershipSettingsProcessor.java`、`service/ApiV3MembershipSettingsService.java`、`util/ApiV3MembershipConfiguration.java`、`src/test/java/org/b3log/symphony/service/ApiV3MembershipSettingsTest.java`、`tools/api-v3-membership-settings-test.mjs`、`docs/api-v3-membership-settings-work.md`：PUT展示配置，settings.write、4096字节/非空部分字段、严格根每次及重放当前锁actor；actor→同码各level→membership，展示模板歧义或坏存量503。仅合并显式字段，空旧配置以空对象为基底，不补readonly或未提交字段。完整defaults函数仅供开通，同lvCode不同周期合法，比较展示规则不把只读礼包差异误当不合法。合法旧配置保留其他权益，提交后只失效缓存。
- `processor/ApiV3MembershipOpeningProcessor.java`、`service/ApiV3MembershipOpeningService.java`、`util/ApiV3MembershipOpeningInputs.java`、`src/test/java/org/b3log/symphony/service/ApiV3MembershipOpeningTest.java`、`tools/api-v3-membership-opening-test.mjs`、`docs/api-v3-membership-opening-work.md`：POST开通，activity.write、levelId及可选券码，无price/owner/config覆盖。账户对在事务前，ArticlePublicationCaches延后用户缓存，当前用户/档位/券/会员/礼包与回执同严格根及保存点。优惠券沿旧ASCII trim与存量SQL匹配；不可用422，重复或损坏存量503，余额不足409。期限按服务器时区自然日、DST/闰月正确；state0非永久视未激活，state1未来或任意expiresAt0永久409。年档20/50/120卡或366免签额度同提交，数量typed、重复bag503、不清理坏存量，容量沿旧饱和上限。
- 开通幂等内部回执包裹公开四字段与私有transferId，Service首发/重放均显式投影，不把私有metadata裸返HTTP。账单dataId保留安全编码/有界可读说明，billingHash仅存私有sourceRequestId；提交后按主账PK+归属/类型/scene/hash校验，父行锁串行去重补齐actor/sys两方积分冗余索引。后置失败不反转已成功购买，原key可修复，禁止重复调用旧append-only异步索引函数。
- `service/MembershipMgmtService.java`：旧public开通先取得同一actor/sys账户对，再执行原完整body；仅移动锁边界，原费用、优惠券/配置/礼包、字段与成功响应保留。旧原子/缓存/提交策略未替换，不能宣称新旧整体原子。它消除同实例旧券DB锁→Java pair与新pair→券的跨域等待；跨实例数据库锁竞争仍需实际验证。
- `sql/20261022_api_v3_membership.sql`：已有pointtransfer_redc增加(rawId,userId)普通索引，先迁移再启用。无新表或Repository，repository.json已有模型列，无需新增表元数据；迁移不清理历史重复索引。
- `processor/ApiV3Processor.java`新增两模块总接线；API.md、UPDATE_LOG.md、AGENTS.md及三份汇总、生成api-route-inventory同步新契约与204条源码清单。无前端/FTL/JS/SCSS/生成产物变更，无需yarn。

实际验证：主代理最终复跑配置10组；开通专项12组包括实际PointtransferMgmtService、BagMgmtService、旧MembershipMgmtService编译和前置锁断言。生产SQL/路由/控制器/鉴权/严格JdbcTransaction/保存点/确认丢失、坏券及重复bag、private receipt HTTP白名单、索引去重/失败修复、DST/闰月均有隔离证据。SQLite去FOR UPDATE、缓存/Token/依赖适配不能证明MySQL多连接/IoC或OWASP实际jar行为；HTML编码调用生产Escapes但Encode依赖为Apache适配，详见work。未运行Maven。

完整人工链路（本轮已提醒需IDEA重新编译或重启，未确认用户实际执行）：

1. 先在开发测试库执行新增索引迁移，保留既有v3/积分来源列；IDEA重启后取activity.write/profile.read/settings.write及content.read令牌。GET档位→GET本人inactive→POST levelId→返回4键→GET本人active、积分流水与背包核对。默认权益来自所选档位，年档卡/额度正确，其他背包项保持；PC/mobile VIP页核对。
2. 使用有限券/无限券/免费券，折扣四舍五入、有限券减一次；未知/非折扣/用尽/越界券422，余额不足409，会员/余额/券/礼包/activation均无部分变化。无/已到期可买，永久和有效会员409；非永久未激活账号可重新购买，重新购买使用服务端defaults。
3. POST重复同key或响应中断后原key恢复，只有一次扣费、一次券消耗/礼包/activation和积分索引；后置索引失败仍成功，原key重试后列表补齐而非重复。初次和重放HTTP只4键，无券码/rawconfig/sourceRequestId/indexTransferId；同key换level/coupon409。
4. 有效或永久会员PUT bold/underline/颜色→GET状态→两端昵称核对，未提交字段/其他权益/期限/余额保持；普通档位效果名422，精确VIP4_YEAR/MONTH八效果成功。空旧配置只出现显式提交字段，不自动开通其他权益；body空/额外autoCheckin/configJson/伪造owner/非法类型、重复JSON及UTF8超4096分别拒绝。新key改后重放旧key不覆盖当前设置。
5. 缺Bearer/仅Cookie/apiKey401、错scope403、账号停用后原key和新key拒绝。损坏/重复会员/券/bag503，超技术预算/展示模板歧义不随机选取。服务器配置错误不能原价降级；所有失败检查余额/券/会员/礼包/回执未部分提交。
6. 测试库注入业务/回执/保存点/提交前失败与提交确认丢失，验证严格根；模拟后置索引失败后重放修复。新旧开通/配置、退款/到期和多实例并发验证MySQL锁、死锁/超时与缓存。旧锁序修复只额外做实际编译/受控异常锁断言，不冒充旧整条计费运行验收；旧缓存loader迟put及整行快照覆盖仍有局限。
7. 回归黑名单/test、匿名首访/计次、validateCaptcha解封/开发关闭盾；按未鉴权/越权/CSRF/XSS/注入/SSRF/批量请求/敏感泄漏复核，及公网静态/CDN版本/sourceMappingURL/目录枚举/上传下载路径。源码未改这些链路不等于真实全站验证通过。

安全/反漏扫源码结论：新两入口显式Bearer+scope/当前有效owner，旧开通原loginCheck保留；无新增匿名入口、anonymous.viewSkips、Cookie写例外或黑名单/验证码变更。输入有界/字段白名单、SQL绑定、返回typed投影，账单模板插值编码，优惠码不进公开回执或异常消息，无远程抓取/上传下载/静态/CDN/目录新增；批量预算沿既有共享写门禁。真实MySQL迁移/EXPLAIN/锁、完整Java应用/IoC、HTTP/WS、PC/mobile、全站反漏扫和压力/多实例缓存仍未验证，未访问生产、未执行Maven。本批及前轮5接口仍未再次提交；API.md需同步网站API文章。

第六轮最终复核：配置工具仅按字段存在/类型判断同码多周期支持，不要求合法默认值相同；空白旧配置只合并显式字段。额外补序列化后字节上限，防JSON转义扩展后超过实际存储预算。主代理最终Java25配置10组/开通12组均退出0；含实际旧方法编译/锁序和自然日时区边界。tracked diff及31个untracked whitespace、两runner node语法、两份新契约curl bash -n通过；新索引列与既有repository.json元数据一致，未执行实际DDL。204条注册与逐条清单一致，全仓库751路由/398浏览器引用；无前端产物改动，不重复构建或全站CDN下载。


## 2026-10-22 第七轮：职业成长提示、排行与公开资料

用户已确认三项口径：先筛当前读者可见用户再分页/计数/连续编号；允许ranking但隐藏experience时保留名次等级；公开资料rarity/ranking按同一可见榜计算，目标不在榜则省略。profession_guides、profession_ranking、profession_public三个子代理独占模块文件，主代理总接线、逐项审阅、复跑与共享文档。没有改旧接口契约或前端。

实际文件与行为：

- 新 `ApiV3ProfessionGuideProcessor` / `ApiV3ProfessionGuideQueryService` / `ApiV3ProfessionGuideDto`、对应 `ApiV3ProfessionGuideQueryTest`、`tools/api-v3-profession-guide-test.mjs`、`docs/api-v3-profession-guide-work.md`：GET /professions/{professionId}/guides，profile.read，当前已发布自动化来源和当前方案奖励的固定投影。仅triggerType/rewardType/POINT amount，无条件、动作、原始配置或内部勋章资料。展示revision与等级scheme独立发布；只发布新展示版本后，当前scheme仍可引用同职业RETIRED revision。路径/枚举/父级/数值严格、有界读取，异常503，不创建进度。
- 新 `ApiV3ProfessionRankingProcessor` / `ApiV3ProfessionRankingQueryService` / `ApiV3ProfessionRankingDto`、对应 `ApiV3ProfessionRankingQueryTest`、`tools/api-v3-profession-ranking-test.mjs`、`docs/api-v3-profession-ranking-work.md`：GET /professions/{professionId}/ranking，profile.read，默认20最大50。完整候选先按ranking权限筛选，再经验降序/userId稳定排序和编号；经验隐藏仍保留等级名次，不返回totalExperience。真实历史revision/scheme等级，无零级合成；当前账号/完整候选末尾重查，变化失败关闭。共享summary供公开资料，累计候选预算跨职业共用，超过503而非伪造完整人数。
- 新 `ApiV3ProfessionPublicProcessor` / `ApiV3ProfessionPublicQueryService` / `ApiV3ProfessionPublicDto`、对应 `ApiV3ProfessionPublicQueryTest`、`tools/api-v3-profession-public-test.mjs`、`docs/api-v3-profession-public-work.md`：GET /users/{userName}/professions，可选profile.read Bearer及真实AnonymousViewCheckMidware。十模块精确scope、HIDDEN无本人/admin绕过，未建privacy纯默认而不写入；经验隐藏跨总量/门槛/贡献/动态省略。主职业没有progress只返回SELECTED定义。近90天APPLIED动态/等级摘要仅当前PUBLIC来源及读者有权目标，绑定subject，匿名内容排除；文章讨论正文以长度/hash有界绑定并重验源/目标授权，清风检查作者隐私，复读用实际repeaterContentStatus，聊天须消息现存。只返回概要，无来源正文或内部ID。
- `ApiV3Processor.java`新增三模块总注册；API.md、UPDATE_LOG.md、AGENTS.md、progress、inventory-and-design、本计划和生成的api-route-inventory同步。共207条可达注册（206HTTP+1WS，GET113/POST41/PUT32/DELETE20/WS1），全仓库754路由/398浏览器引用。没有SQL/Repository/前端/静态产物变更，无需yarn或新增迁移。

完整人工链路（本轮已明确提醒IDEA重新编译/重启，未确认执行）：

1. A取得profile.read，GET职业目录→已发布职业guides→ranking第一页/第二页/末页，核对来源及POINT/MEDAL最小奖励、连续名次/可见total/稳定并列顺序。只发布新展示revision保留等级scheme后，guides仍成功；无进度者可读guides，不创建progress。未发布404、非法ID/p0/size51为422。
2. A/B分别设置ranking PUBLIC/LOGGED_IN/SELF/HIDDEN，experience HIDDEN：比较A/B榜单和匿名公开资料；经验隐藏仍在可见榜中显示等级名次，无totalExperience；ranking不允许的目标不出现在榜或公开rarity/ranking。统计百分比为(totalUsers-rank)*100/totalUsers，只按当前读者人群。没有管理员跨过职业模块隐私。
3. GET /users/{userName}/professions，分别以匿名、本人、他人和管理员验证十模块PUBLIC/LOGGED_IN/SELF/HIDDEN；全隐藏404，缺省privacy读取不新增行。已选无progress仅SELECTED，无虚构等级；真实退休历史进度正确。隐藏experience后全响应无totalExperience/requiredExperience/experienceSum/experienceDelta，次数和等级仍按各模块显示。
4. 产生文章、讨论邀请、私密/匿名评论、清风、复读与聊天动态，核对仅允许来源摘要；移除邀请、等长修改讨论正文、隐藏清风、停用作者、删除目标消息后重新请求应不再泄露对应摘要。PRIVATE来源及匿名内容不关联；无source/body/label或内部对象。范围与时间之外的历史不假造为当前完整记录。
5. 缺Bearer或只有Cookie/旧apiKey访问两专用读取401，错scope403；公开入口匿名仍经验证码，坏Bearer不降级匿名，Cookie/apiKey不冒充SELF。测试存储故障、损坏/重复定义、错误父关系/大小写levelCode、超过单榜或全请求预算，503且无截断人数/驱动错误/敏感字段。读取中撤权、切隐私和停用应失败关闭或下次读取失效，不承诺多SQL全局原子快照。
6. 回归黑名单→/test、匿名首访/计次、validateCaptcha解封、开发关闭盾及两端页面；复核静态/CDN版本、sourceMappingURL、目录枚举、上传下载返回路径。实际MySQL执行计划/CI排序/驱动类型、完整IoC/HTTP/浏览器及并发压力仍需运行验收。

安全与反漏扫源码结论：两个入口明确Bearer/profile.read，新增公开入口optionalProfile之后显式匿名盾，不改anonymous.viewSkips或BeforeRequestHandler/Captcha。纯读不创建默认记录、无Cookie授权写入；SQL参数化、路径/隐私/typed响应白名单且预算有界。公开资料按当前读者及来源权限，不裸传用户/config/正文；无远程抓取、HTML拼接、上传下载路径或新静态/CDN目录。未鉴权/越权/CSRF/XSS/注入/SSRF/滥用/泄露均已源码评估和定向隔离覆盖，但非实际全站安全验收。未运行Maven、真实MySQL、8080、浏览器、生产或第三方服务，本批及前两批未再次提交。API.md需同步网站文章。

第七轮最终证据：主代理最终Java25复跑guide8组、ranking10组、public9组均退出0，日志分别/tmp/rhythm-v3-profession-{guide,ranking,public}-root.log。包含真实AnonymousViewCheck首访/计次、实际SQL与共享排名预算，讨论等长变文hash/最终权限重验及真实复读字段回归。tracked diff --check、49个untracked文件whitespace、三个runner node语法和三段新API curl bash -n通过。207条总register可达METHOD/path与progress逐项全等无重复；未执行curl请求或Maven，真实MySQL/IoC/HTTP/PC/mobile及完整反漏扫均未验收，不能记作已通过。


## 2026-10-22 第八轮：复读机读取、常用表情设置与邀请码购买

继续按用户授权多代理推进。repeater_read负责明确只读池语义，emotion_selection负责常用表情写入，remaining_rules先只读盘点并在用户确认后负责邀请码购买；主代理审阅、总接线、复跑及共享文档。用户明确批准目录白名单/空数组恢复默认；购买仅邀请模式、每次现有100分、余额不足不生成、码/费用/回执一起提交，旧购码例外保持。转账、举报、历史正文等盘点仍不当作已获批准业务。

实际文件及行为：

- `ApiV3RepeaterProcessor` / `ApiV3RepeaterQueryService` / `ApiV3RepeaterDto`、`ApiV3RepeaterQueryTest`、`tools/api-v3-repeater-test.mjs`、`docs/api-v3-repeater-work.md`：GET /repeater/items与/next，可选content.read及显式匿名盾。列表默认12最多50，不伪称全量分页；按赞/时间/ID降序，next取前50再排除、随机，无候选null。仅status0，失效或seed作者null而不删除池成员；liked只当前Bearer，owner EXISTS避免重复点赞放大。正文存量不强加投稿最短长度，但模型上限/Unicode/精确数字检查，超长或坏数据503而非截断成功；末尾当前actor/池重验。
- `ApiV3EmotionSettingsProcessor` / `ApiV3EmotionSettingsService` / `ApiV3EmotionSettingsInputs`、`ApiV3EmotionSettingsTest`、`tools/api-v3-emotion-settings-test.mjs`、`docs/api-v3-emotion-settings-work.md`：PUT /me/settings/emotions，settings.write/key，目录动态唯一158项，保留输入顺序。重复/空白/未知422，[]只清本人type0并响应默认11项，保留其他type/owner。当前actor锁及settings.emotions严格根/保存点/回执同提交；原key恢复旧响应不覆盖之后设置，重放停用者不可成功。旧setEmotionList仍无共同actor锁且删全部type，不宣称新旧整体串行或原子。
- `ApiV3InvitationPurchaseProcessor` / `ApiV3InvitationPurchaseService` / `ApiV3InvitationPurchaseInputs`、`ApiV3InvitationPurchaseTest`、`tools/api-v3-invitation-purchase-test.mjs`、`docs/api-v3-invitation-purchase-work.md`：POST /me/invitation-codes，activity.write/key，空请求/空JSON，服务端定价/生成16位强随机码。账户对先于缓存边界与严格根，namespace→当前模式→actor→receipt；模式只在新购时检查，原key只重验有效actor恢复原code，即使模式关闭/原码已用/清理。ID+TTL用旧真实计龄规则，余额不足不调用生成器；码/积分/回执同保存点提交，primitive null必须回滚。账本dataId空、固定memo，码不进入积分失败日志打印字段；内部transferId只回执私有，HTTP投影三字段。提交后主账PK/归属/场景/hash核验后串行幂等修复冗余索引，索引故障不反转成功购买。旧独立先造码行为不变，既有code非唯一索引不能保证与旧入口跨实例绝无随机碰撞，碰撞当前读发现即503回滚。
- `docs/api-v3-next-rules-work.md`：只读盘点转账/举报/历史及邀请码原链，邀请码已标用户确认，其余待后续规则确认。不新增这些未确认的HTTP写入口。
- `ApiV3Processor.java`总接线三模块四条路由，API.md/UPDATE_LOG.md/AGENTS.md/三个汇总及生成inventory同步。211条（210HTTP+1WS，GET115/POST42/PUT33/DELETE20/WS1），全仓库758路由/398浏览器引用。没有新SQL、Repository表、FTL/JS/SCSS或生成产物，无需yarn。本批购码仍依赖已存在20260717_external_point_source.sql及20261022_api_v3_membership.sql迁移，未执行实际DDL。

实际验证：主代理最终复跑repeater6组、emotion-settings7组、invitation-purchase9组均退出0，日志/tmp/rhythm-v3-{repeater,emotion-settings,invitation-purchase}-root.log。复读真实AnonymousViewCheck首访/计次、Cookie/apiKey不能个性化、坏Bearer不匿名降级；表情实际既有GET回读与最大目录、严格提交/保存点故障/确认丢失；购码实际积分/OptionRepository/SQL/严格根、余额边界、跨actor回执隔离、主提交与索引提交各自丢失确认均有覆盖。生产Symphonys配置和Token/请求/cache/SQLite去FOR UPDATE为适配，不能推广为完整应用或MySQL行锁/多实例通过。未执行Maven、真实HTTP、浏览器或生产。

完整人工链（本轮已提醒IDEA重新编译或重启，未确认执行）：

1. PC/mobile复读机入口对照 GET items默认12、各类型/size1/50，赞/时间/ID降序，空分类为空；GET next带当前ID，只在前50池且不返回排除项，唯一排除/空池null，不补第51条。正文按纯文本、emoji/换行正常，无作者时null。坏类型/size0或51/注入ID422，存量超长/负数等503无部分成功。
2. 匿名、旧Cookie/apiKey与content.read A/B分别读复读机，匿名liked=false、A/B只各自点赞；坏Bearer401不降级、错scope403、停用404。首访/计次走/test，黑名单/validateCaptcha解封与开发关闭盾仍按原基线；修改或下架候选时不继续返回旧池快照。
3. 取得profile.read/settings.write，GET表情选项→PUT顺序数组→成功返回同顺序→GET本人列表→两端设置页/编辑器核对。其他owner/type保持、无积分变化；[]恢复默认。新key再改值，重放旧key回旧结果但GET仍新值，同key变内容409；重复/空白/目录外/EOF/非字符串422，重复JSON400，超请求体413，无Bearer401/错scope403/缺key422，停用后重放失败。MySQL故障不出现半列表；旧新竞争不作为已解决。
4. 测试库先确认已有积分来源列/索引迁移，邀请模式且余额足够，以activity.write/key POST空对象：仅code/expiresAt/paidPoints，扣100、单码/单转账/单回执；原key重试不新增。把码用掉/删除、关闭模式后原key仍只恢复旧结果，不保证可注册；新key重新验证模式。余额99为409且无新码/扣款/回执，余额100可至0。Cookie/apiKey/伪造owner/price/code不能越权，其他用户同key不能读本人的码。
5. 开发库注入积分、码、回执、保存点/commit故障，确认整体回滚；提交成功丢响应后原key恢复原码，索引失败不报购买失败且原key修复不重复，索引自身提交丢响应亦不重复。检查响应/普通账本/失败日志无内部transferId或邀请码明文。旧PC/mobile兑换成功链与原字段保持，不把旧余额不足造码当成已修复。
6. 用真实MySQL核对SQL类型/大小写/查询计划、行锁/间隙锁、代码碰撞、旧新入口/注册清理并发及缓存；完整IoC/HTTP/两端和全局验证码/黑名单链回归。全站CDN版本/sourceMappingURL、目录枚举、上传/下载返回路径另验，本批没有新增这些资源或路径。

安全/反漏扫源码结论：新增两个匿名GET显式optionalContent+AnonymousViewCheck，写入口明确Bearer scope+共享写守卫/当前用户，未改BeforeRequestHandler/Captcha/anonymous.viewSkips。纯读无隐式写，写入无Cookie授权或不兼容CSRF拦截；参数化SQL、白名单输入/DTO、技术预算明确、无远程抓取/HTML拼接/新资源或上传下载。购码凭据只本人响应与受保护回执，日志和账本不含码，私人内部回执不得裸返。未鉴权/越权/CSRF/XSS/注入/SSRF/批量滥用/泄漏逐项有源码/专项判断；真实全站反漏扫、MySQL/并发负载/IoC/HTTP/浏览器仍未验收。该批和前批未提交；API.md需同步网站文章。

第八轮最终复核：211个总register可达METHOD/path与progress逐条一致且无重复，方法统计GET115/POST42/PUT33/DELETE20/WS1。tracked diff --check、68个累计untracked文件尾随空白、三个runner node --check、四段新API curl bash -n通过（仅语法，未发请求）。全仓库生成758路由/398浏览器引用。主代理6/7/9组结果为当前最终源码；无新增迁移/前端，不重复构建，完整应用/MySQL/浏览器/全站反漏扫仍未验收。


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


## 2026-10-24 第十六轮：心跳和猜拳

三子代理分别完成结算/迁移、HTTP契约/公开文档、专项测试；根整合实际MySQL独立进程竞争。沿已有三个红包入口扩五类，不新增路由，251条保持。心跳负积分、猜拳双倍返还、已领份额与消息/事件/回执在同一严格根提交；显式风险确认和出拳指纹，未结算出拳只对发送者详情可见。先迁移20261024_api_v3_chat_red_packet_risk.sql。完整修改文件、人工步骤与验证边界见api-v3-chat-red-packet-risk-work.md。真游戏云存档两问题、独立website_v3客户端和真实服务端/PC/mobile验收仍待完成；本轮不提交推送、无Maven或前端改动。


## 2026-10-24 第十七轮：剩余能力反查与交付迁移

只读审查重新找到作者付费置顶、官方身份认证申请、音乐/天气卡片发送三个用户侧缺口，已纳入progress，业务规则已询问，不能排除或以读取DTO代替写入。游戏云存档仍待游戏范围/旧桥接与版本体积契约；外部website_v3源码缺及IDEA重启未确认仍是运行依赖。未将整体目标标为完成。

新增有序15SQL/19张新表的真实MySQL迁移链专项，四组通过；导出新增真实MySQL八场景，验证多块文件、费用/回执/文件原子性、故障/丢ACK及跨JVM同键只有一份任务和100分扣费。专用容器已清理，未操作业务库、不Maven；完整文件、人工链与夹具限制见migration-chain-work及post-export-work。本轮没有新增或修改公开接口。


## 2026-10-24 第十八轮：客户端读取

找到真实客户端/Users/ling/Documents/fishpi/website_v3，保留已有改动后接线读取helper、稳健认证及缓存旧响应隔离。后端文章GET补问答/优选和月内评论排序，原契约不变、数量251。隔离SQL4组、客户端认证12组/缓存3组/内容专项、类型与双构建通过，IAB窄视口仅用独立本地夹具验证。真实后端重启、宽屏与设备/全站风控尚未验收。详见client-read-work及客户端V3_MIGRATION。用户新确认付费置顶、音乐/天气、认证独立审核及首次读取销毁，后续继续实现。


## 2026-10-24 第十九轮：作者付费置顶

新增POST stick并总注册，当前252入口。用户批准有效置顶排序及元数据；同事务付费/时间/回执、共享过期当前读、防旧回执覆盖、提交后索引修复。隔离8组、排序SQL4组、客户端适配/双构建及本地夹具宽视口展示通过，真实服务/调度/MySQL并发/移动设备未验。完整文件与人工链见article-stick-work；音乐/天气、认证独立审核等继续开发。

### 2026-10-24 音乐/天气补齐

新增两路POST并接入总register，当前254（253HTTP+1WS）；实现与限制见`api-v3-chat-card-work.md`。官方企业认证的申请去重、审核结论和材料一次读取规则已确认，尚待实现；不能据源码计数宣告v3全部完成。

### 2026-10-24 企业认证材料阶段

新增5路源码达259入口，私有申请与材料一次读取已接线；结论提交在先读图规则答复后补齐，当前不能完成整份审核。新迁移、8组隔离检查和MySQL迁移证据见`api-v3-identity-application-work.md`。

企业认证补充真实MySQL证据：8个既有核心/HTTP边界场景及3组跨JVM竞争通过，包含观察InnoDB等待的幂等/单pending/一次性读图。259入口不变；完整应用与待确认审核结论继续保留，不据测试条数宣告全目标完成。

2026-10-24企业认证结论已补齐：先读图、同事务结论/回执/释放pending及幂等重放，6路使总数260。11组核心/HTTP与真实MySQL、5组跨JVM竞争通过。仍需完整运行验收，不将源码闭环当部署完成。


2026-10-25扫码范围更新：用户明确只需v3支持、不改APP代码。七条官方APP扫码POST已总注册，SQL/Repository元数据、两分钟/双端确认/一次消费、当前发码授权/epoch重验与独立native令牌完成；管理员变更/停用清待扫码。当前271路，源码/隔离证据与运行验收仍分列；完整链及未验见api-v3-app-login-work.md。APP克隆仅只读参考，工作区已还原干净，不以APP源码迁移作为本任务交付条件。
