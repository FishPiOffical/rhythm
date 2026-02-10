## 用户关键要求
- 所有文件不要直接修改 `.css`，而是修改 `.scss`，然后执行 `yarn run build` 生成新的 CSS。js同理，请修改js，而不是min.js，然后同样是通过 `yarn run build` 编译min.js
- 在 WSL 中执行 Maven 时，优先复用 Windows 本地仓库：`-Dmaven.repo.local=/mnt/c/.m2/repository`（对应 Windows 的 `C:\.m2\repository`），可避免依赖下载时的 SSL 握手失败；Java 版本固定使用 25。
- 如果你的更改涉及到操作数据库，请注意在引用Repository类的时候不要加上包名org.b3log.latke.repository，如果没有请直接import，另外除了查询以外，增、删、改 所有写入的操作都需要用Transaction进行完整的事务commit，否则会无法正常写入。
- 涉及到前端的更改，请不要只修改classic（PC端）皮肤，mobile（移动端）皮肤也要同步修改
- 涉及到新增 Repository（新增表/集合）时，除了 SQL 建表外，必须同步更新 `src/main/resources/repository.json`，并且 Repository 的 `super("...")`/模型常量中不要写 `symphony_` 前缀（框架会根据 `jdbc.tablePrefix` 自动加前缀）。
- 除非用户提到代码编译有问题或者主动要求你进行编译，否则不要使用mvn编译java，这个过程会浪费用户的时间，除非你觉得非常有必要，并且询问用户且被同意
- 每次遇到新的项目架构关键信息，对后续项目AI使用有帮助的，请主动维护AGENTS.md，注意每次维护不要过度细节，只要让AI正确理解就可以了，防止prompt token过长
- 我和你沟通都是在开发环境（http://localhost:8080），生产环境是摸鱼派（https://fishpi.cn），如果你能调用Chrome，可以调试辅助你诊断代码，注意，如果你编译了js和css，强制刷新开发环境就会生效，如果你改了Java或者FTL，那就需要告知用户通过IDEA重新编译或者重启服务端才能生效

## 协作目标
- `AGENTS.md` 保持精简，只保留会影响决策的长期约定；新增内容优先补充“关键链路”，避免堆砌背景信息。
- 2026 春节临时视觉：全站顶栏灯笼 + 首页新春横幅；用户明确要求“节后”由 AI 直接移除，不额外做开关。

## 开发硬约束
- 前端改动只改源码：`.scss` 与非 `min.js`；执行 `yarn run build` 生成产物；`classic` 与 `mobile` 皮肤需同步修改。
- WSL 执行 Maven 优先使用：`-Dmaven.repo.local=/mnt/c/.m2/repository`；Java 固定 25。
- 未经用户明确要求，不主动执行 `mvn` 编译。
- 涉及数据库写操作（增/删/改）必须使用 `Transaction` 并完整 `commit`。
- `Repository` 相关类直接 `import`，不要在代码里写 `org.b3log.latke.repository` 全限定名。
- 新增 Repository（新表/集合）时：同步更新 `src/main/resources/repository.json`；`super("...")`/模型常量不要写 `symphony_` 前缀。
- 安全基线（已审计）：防扫描验证码链路核心在 `BeforeRequestHandler` + `AnonymousViewCheckMidware#handle` + `CaptchaProcessor#validateCaptcha`；`LoginCheckMidware#handle` 负责登录态校验（含 apiKey），两套逻辑都不可被绕过。
- 访问控制基线（已审计）：当前未登录可访问并不只首页/文章/登录/注册，还包含验证码页、`/about`、`/download`、`/privacy`、`/agreement` 及部分公开 API；匿名可访问范围还受 `symphony.properties` 的 `anonymous.viewSkips` 影响。
- 新增页面/路由默认要求登录（优先挂 `loginCheck::handle`）；若业务必须匿名访问，必须显式接入 `anonymousViewCheckMidware::handle` 并评审风险，不能裸放。
- 新增或改造登录/入口逻辑必须做回归：未登录拦截、登录后放行、黑名单 IP 跳转 `/test`、`/validateCaptcha` 解封、开发模式关闭风控后行为一致。
- `loginCheck::handle` 默认兼容 `Sessions` + `apiKey`（query 参数与 JSON body），并写入 `context.attr(User.USER)`；后续处理逻辑优先从该 attr 取当前用户。
- `apiCheck::handle` 同样支持 `apiKey` 并写入 `context.attr(User.USER)`，但未登录返回 JSON `{"result":"Unauthorized"}`；适合纯 API 场景。
- `permissionMidware::check` 只做权限判断，不负责写入 `context.attr(User.USER)`；若处理方法还需要当前用户信息，必须自行取用户（或叠加 `loginCheck`）。
- 处理方法取当前用户推荐顺序：`context.attr(User.USER)` -> `Sessions.getUser()` -> `ApiProcessor.getUserByKey(...)`；不要只依赖 `Sessions.getUser()`。
- 接口设计安全约束：前后端新增/改造接口时，必须同时评估常见漏洞（越权、未鉴权访问、CSRF、XSS、注入、SSRF、批量请求滥用、敏感信息泄露）。
- 字符串输入必须做限制与校验：长度上限、空白处理、字符白名单/黑名单、格式校验（如用户名/URL/JSON）、必要的转义或编码；禁止直接信任前端传参。
- 涉及业务规则（可用字符、最大长度、是否允许 HTML/Markdown、过滤策略）不明确时，先与用户确认规则再实现，避免误伤或放漏。

## 目录速览
- 后端：`src/main/java/org/b3log/symphony`（入口 `Server.java`，核心分层 `processor/service/repository/model/util`）。
- 资源：`src/main/resources`（配置、静态资源、`skins` 模板）。
- 前端编译输出：`src/main/webapp/css`、`src/main/webapp/js`。
- 构建：后端 Maven（`pom.xml`），前端 Gulp（`gulpfile.js`、`package.json`）。

## 关键链路（持续维护）
- 勋章管理页：`/admin/medal`
  - 后端：`src/main/java/org/b3log/symphony/processor/MedalProcessor.java`（`showAdminMedal`、`register`）
  - 前端：`src/main/resources/js/medal.js`
  - 模板：`src/main/resources/skins/classic/admin/medal.ftl`、`src/main/resources/skins/mobile/admin/medal.ftl`
- 管理 API：`/api/medal/admin/list`、`/api/medal/admin/search`、`/api/medal/admin/grant`、`/api/medal/admin/revoke`、`/api/medal/admin/owners`
- 会员状态 API：`GET /api/membership/{userId}`（`MembershipProcessor#getUserMembershipStatus`）
- VIP 管理页：`/admin/vip`（`MembershipProcessor#showAdminVipManagePage`，classic/mobile 同路径模板 `admin/vip.ftl`）。
- VIP 管理 API（仅 `adminRole`）：`/api/admin/vip/list`、`/api/admin/vip/add`、`/api/admin/vip/update`、`/api/admin/vip/refund`、`/api/admin/vip/extend`。
- VIP 管理服务关键方法位于 `MembershipMgmtService`：免费新增（不扣积分）、手工维护、按剩余天数退款并失效。
- VIP 按天退款成功后会复用“系统转账通知”（`Notification.DATA_TYPE_C_POINT_TRANSFER`）给用户发送站内通知。
- 积分变更统一走 `PointtransferMgmtService`：独立流程用 `transfer(...)`（方法内自带事务）；若外层已有事务，必须用 `transferInCurrentTransaction(...)` 避免事务冲突。
- 发积分/扣积分约定：发积分用 `fromId=Pointtransfer.ID_C_SYS`、`toId=userId`；扣积分用 `fromId=userId`、`toId=Pointtransfer.ID_C_SYS`（常用类型 `Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT`）。
- 需要给用户发“系统转账通知”时：在转账成功拿到 `transferId` 后，调用 `NotificationMgmtService#addPointTransferNotification`，并设置 `Notification.NOTIFICATION_USER_ID` 与 `Notification.NOTIFICATION_DATA_ID=transferId`。
- VIP 管理页配置项不再手填 JSON：前端依据等级 `benefits` 模板自动生成可视化表单，再序列化为 `configJson` 提交。
- VIP 管理页样式需注意 `home.css` 的 `.form--admin label { flex: 1; }` 会影响布局；配置项行在 `vip-admin.scss` 中需显式改为整行（label/builder 100%）并对 checkbox 使用类型选择器，避免控件被放大。
- 有效期字段：勋章 `expireTime`（毫秒，`0`=永久）；会员 `expiresAt`（可回填勋章到期）。
- 首页最新文章链路：`IndexProcessor#loadIndexData` 通过 `ArticleQueryService#getIndexRecentArticles(fetchSize, page)` 组装 classic/mobile 首页“最新文章”；第一页置顶插入与数量行为在该方法维护。
- 首页两列对齐约束：`getIndexRecentArticles` 的第一页会插入全部置顶且不截断；第二页起需按第一页“置顶占位数”补偿 `fetchSize` 与分页偏移，保证两列等高且不丢中间文章。
- 首页右侧排行补偿（无前端延迟）：由 `IndexProcessor#loadIndexData` 按两列最新文章的最大行数计算 `rankCompensateRows`，先换算“右栏总补偿行数”再分摊到 `checkinVisibleCount/onlineVisibleCount`，Freemarker 直接按该数量渲染，不再依赖 JS 运行时增删行。
- 路由总入口：`Router#requestMapping` + 各 Processor `register()`；新增路由先决定使用 `loginCheck` / `apiCheck` / `permission` / `anonymousViewCheck` 哪条链路。
- `LoginCheckMidware#handle`：未登录统一 401（特殊 URI `/gen` 返回空 SVG）；支持 `Sessions` 与 `apiKey` 两种登录态来源。
- 新接口若需“页面登录态 + apiKey 调用”双兼容，路由层优先挂 `loginCheck::handle`，处理方法再读取 `context.attr(User.USER)`；避免只挂 `permission` 导致拿不到当前用户对象。
- `AnonymousViewCheckMidware#handle`：匿名访问触发验证码（2 小时首次访问 + 每 5 次访问），并结合 `anonymous.viewSkips`、文章匿名开关、匿名访问次数 Cookie 限制。
- `Server` 启动逻辑：`DEVELOPMENT` 模式会关闭 `Firewall` 与 `AnonymousViewCheck`（验证码盾），联调时不要误判“线上无校验”。
- 历史遗留：部分接口未在路由层挂登录中间件而在方法内鉴权（如 `MedalProcessor#requireAdmin/requireLogin`、`UserProcessor` 的 goldFingerKey 系列）；新增接口不要复用该模式，优先路由层显式鉴权。
