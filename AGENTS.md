## 用户关键要求
- 所有文件不要直接修改 `.css`，而是修改 `.scss`，然后执行 `yarn run build` 生成新的 CSS。js同理，请修改js，而不是min.js，然后同样是通过 `yarn run build` 编译min.js
- 在 WSL 中执行 Maven 时，优先复用 Windows 本地仓库：`-Dmaven.repo.local=/mnt/c/.m2/repository`（对应 Windows 的 `C:\.m2\repository`），可避免依赖下载时的 SSL 握手失败；Java 版本固定使用 25。
- 如果你的更改涉及到操作数据库，请注意在引用Repository类的时候不要加上包名org.b3log.latke.repository，如果没有请直接import，另外除了查询以外，增、删、改 所有写入的操作都需要用Transaction进行完整的事务commit，否则会无法正常写入。
- 涉及到前端的更改，请不要只修改classic（PC端）皮肤，mobile（移动端）皮肤也要同步修改
- 涉及到新增 Repository（新增表/集合）时，除了 SQL 建表外，必须同步更新 `src/main/resources/repository.json`，并且 Repository 的 `super("...")`/模型常量中不要写 `symphony_` 前缀（框架会根据 `jdbc.tablePrefix` 自动加前缀）。
- 除非用户提到代码编译有问题或者主动要求你进行编译，否则不要使用mvn编译java，这个过程会浪费用户的时间，除非你觉得非常有必要，并且询问用户且被同意
- 每次遇到新的项目架构关键信息，对后续项目AI使用有帮助的，请主动维护AGENTS.md，注意每次维护不要过度细节，只要让AI正确理解就可以了，防止prompt token过长
- 我和你沟通都是在开发环境（http://localhost:8080），生产环境是摸鱼派（https://fishpi.cn），如果你能调用Chrome，可以调试辅助你诊断代码，注意，如果你编译了js和css，强制刷新开发环境就会生效，如果你改了Java或者FTL，那就需要告知用户通过IDEA重新编译或者重启服务端才能生效；当前本地运行环境经常直接读取 `target/classes` 下的资源，如果源码已改但浏览器仍是旧效果，优先同步对应资源到 `target/classes` 或提醒我重新编译
- 只要本轮改动涉及 Java / FTL，联调前就要明确提醒我手动通过 IDEA 重新编译或重启服务端；不要假设我已经做了，确认前不要把浏览器里的旧行为当成代码未生效
- 在 WSL 环境中不要依赖 git 状态/差异判断工作区是否干净（可能存在异常噪声）；默认按任务目标直接改指定文件，git 清理由用户在 Windows 环境统一处理

## 协作目标
- 用户已要求每批改动完成并检查后提交Git；Rhythm与website_v3分别提交本批相关源码、文档和测试。推送、发布仍按用户明确指令执行。
- `AGENTS.md` 保持精简，只保留会影响决策的长期约定；新增内容优先补充“关键链路”，避免堆砌背景信息。

## 开发硬约束
- 前端改动只改源码：`.scss` 与非 `min.js`；执行 `yarn run build` 生成产物；PC/移动端经典皮肤当前目录分别为 `skins/classic/pc` 与 `skins/classic/mobile`，改动需同步评估两端。
- 页面分区的卡片应保持同级关系，禁止使用“卡片套卡片”的嵌套容器；外层布局容器不应再使用卡片背景、边框或阴影包裹内部卡片。
- JS 版本号暴露排查使用 `tools/js-version-audit.mjs`；清理后执行 `yarn run build` 并复查，生产静态资源不要发布可泄露依赖信息的 source map。
- 质量复盘约束：近期提交后多轮修 bug 的主要原因是只验证了局部代码，未按真实用户链路完整反查入口、模板、生成产物和浏览器行为。后续代码改动提交前必须完成对应层级闭环：读清调用链与数据形态；同步检查 PC/移动、源码/生成产物、权限/匿名/apiKey；前端改动执行 `yarn run build` 并用浏览器验证关键操作；Java/FTL 改动先提醒用户通过 IDEA 重新编译或重启服务端；无法验证的项必须在最终回复中明确列出，不能用猜测代替验证。
- 每次完成改动后的最终回复必须明确列出实际修改的文件与行为，并提供覆盖完整用户链路的人工测试步骤；测试步骤需包含入口、核心操作、成功结果及相关的异常或边界场景。
- 发布前检查基线：先看 `git status --short` 与未提交 diff，按新增/修改路由、模板、JS/CSS、SQL/Repository 分类；所有新增或改造的匿名入口必须确认会经过 `AnonymousViewCheckMidware` 或明确要求登录，不能让新 IP 直接访问新功能接口绕过 `/test` 极验；复核 `BeforeRequestHandler` 黑名单重定向、`AnonymousViewCheckMidware` 首访/每 5 次验证码、`CaptchaProcessor#validateCaptcha` 解封链路未被绕过；扫描新增公网静态资源和 CDN URL 是否暴露版本号、`sourceMappingURL` 或可枚举目录；接口按未鉴权访问、越权、CSRF、XSS、注入、SSRF、批量请求滥用、敏感信息泄露逐项给结论。
- 反漏扫底线：任何未提交代码审查、发布前检查、公开入口新增或改造都必须检查反漏扫；至少覆盖匿名访问链路、黑名单/验证码链路、`anonymous.viewSkips`、公开 API 鉴权、静态资源/CDN 版本号、`sourceMappingURL`、目录枚举、上传/下载返回路径；无法实际验证的项必须在最终回复中列出，不能跳过或默认通过。
- CDN 防漏扫排查：当用户要求“CDN 防漏扫/刷新列表/替换目录”时，扫描项目内 `https://file.fishpi.cn/...` 具体资源（排除 `.codex-tasks`、`target`、`node_modules`、`cdn-version-replacements`），下载可访问资源，检测路径/查询参数/内容中的版本号与 `sourceMappingURL`；命中资源按 CDN 路径镜像到 `cdn-version-replacements/files/`，但路径版本号目录改为稳定目录名（如 `vditor/3.11.1/dist -> vditor/latest/dist`），文件名版本号去掉（如 `jquery.color-2.1.2.min.js -> jquery.color.min.js`），源码引用也同步改成新 URL；版本目录型库必须在 CDN 侧保留原目录并复制完整目录树到稳定目录，不能只上传入口文件；生成 `report.md`、`version-findings.json`、`replacement-validation.json` 和一行一个的 `refresh-urls.txt`；替换副本需复扫并对 JS 执行 `node --check`，下载失败或动态拼接 URL 单独记录，不伪造结果。
- 鱼排扩展独立脚本需包含标准 `UserScript` 头（至少 `@name`、`@match`、`@grant`、`@run-at`）；聊天室联调脚本建议同时匹配 `https://fishpi.cn/cr*` 与 `http://localhost:8080/cr*`。
- WSL 执行 Maven 优先使用：`-Dmaven.repo.local=/mnt/c/.m2/repository`；Java 固定 25。
- 未经用户明确要求，不主动执行 `mvn` 编译。
- 涉及数据库写操作（增/删/改）必须使用 `Transaction` 并完整 `commit`。
- `Repository` 相关类直接 `import`，不要在代码里写 `org.b3log.latke.repository` 全限定名。
- 新增 Repository（新表/集合）时：同步更新 `src/main/resources/repository.json`；`super("...")`/模型常量不要写 `symphony_` 前缀。
- 安全基线（已审计）：防扫描验证码链路核心在 `BeforeRequestHandler` + `AnonymousViewCheckMidware#handle` + `CaptchaProcessor#validateCaptcha`；`LoginCheckMidware#handle` 负责登录态校验（含 apiKey），两套逻辑都不可被绕过。
- 用户脱敏基线（已审计）：对外接口用户对象统一走 `DesensitizeUtil`（移除密码/邮箱/手机/QQ/2FA/apiKey/token/IP/UA 等）或字段白名单；已知存量风险（用户知情暂不修）：`/reset-pwd?code=` 与 `/register?code=` 页面把完整 `user.userPhone` 输出到 HTML（LoginProcessor + verify/reset-pwd.ftl、verify/register2.ftl，匿名可见）；`TopProcessor#removePrivateData` 不移除签到/登录时间戳等活动字段。
- `symphony.properties` 中提交的 `upload.rhypic.ticketPrivateKey` / `upload.rhypic.internalToken` 是开发调试专用，生产环境使用独立生成的密钥，审计时不要当作泄露误报。
- 搜索引擎爬虫校验：`SearchEngines` 对 Google/Baidu/Bing/Sogou/Yandex 走 DNS 双向校验；DuckDuckGo 走本地 CIDR 资源 `src/main/resources/search-engines/duckduckgo-bot-prefixes.json`，该文件由 `scripts/deploy.ps1` 调用 `scripts/update-duckduckgo-bot-prefixes.ps1` 在打包前合并官方 JSON。
- 访问控制基线（已审计）：当前未登录可访问并不只首页/文章/登录/注册，还包含验证码页、`/about`、`/download`、`/privacy`、`/agreement` 及部分公开 API；匿名可访问范围还受 `symphony.properties` 的 `anonymous.viewSkips` 影响。
- 新增页面/路由默认要求登录（优先挂 `loginCheck::handle`）；若业务必须匿名访问，必须显式接入 `anonymousViewCheckMidware::handle` 并评审风险，不能裸放。
- 新增或改造登录/入口逻辑必须做回归：未登录拦截、登录后放行、黑名单 IP 跳转 `/test`、`/validateCaptcha` 解封、开发模式关闭风控后行为一致。
- `loginCheck::handle` 默认兼容 `Sessions` + `apiKey`（query 参数与 JSON body），并写入 `context.attr(User.USER)`；后续处理逻辑优先从该 attr 取当前用户。
- `apiCheck::handle` 同样支持 `apiKey` 并写入 `context.attr(User.USER)`，但未登录返回 JSON `{"result":"Unauthorized"}`；适合纯 API 场景。
- `permissionMidware::check` 只做权限判断，不负责写入 `context.attr(User.USER)`；若处理方法还需要当前用户信息，必须自行取用户（或叠加 `loginCheck`）。
- `permissionMidware::check` 在 `permission.rule.url.*` 缺失时会直接放行（含匿名）；凡仅挂 `permissionMidware` 的路由必须同步补齐对应权限规则，避免后台能力裸露。
- 处理方法取当前用户推荐顺序：`context.attr(User.USER)` -> `Sessions.getUser()` -> `ApiProcessor.getUserByKey(...)`；不要只依赖 `Sessions.getUser()`。
- 聊天室 `/cr` 的两套样式分别走 `chat-room.js` 与 `chat-room-2.js`，但红包消息都通过 `/chat-room/getMessage` 返回原始 JSON（`msgType=redPacket`），领取统一走 `/chat-room/red-packet/open`。
- 聊天室红包详情/打开后的弹层头部走前端通用 `Util.alert()`（`src/main/resources/js/common.js`），标题与关闭按钮布局异常优先排查这个公共弹层，而不是红包业务模板。
- v3 reaction 入口在 `ApiV3ReactionProcessor`，文章/评论/聊天室均支持 GET/PUT/DELETE；PUT 是设置而非 toggle，与旧入口共用 `ReactionMutation` 和加锁写入。目标可见性由 `ApiV3ReactionTargetService` 校验，论坛实时事件逐接收者校验，提交后才发布；不要直接复用未过滤接收者的旧广播。
- 评论与聊天室 emoji reaction 共用 `reaction` 表；历史接口直接补 `reactionSummary/currentUserReaction`，聊天室实时选中态需结合 `chatReaction` 增量事件里的 `actorUserId/actorReaction` 在前端按当前用户合并。
- 内容删除统一经`ReactionRepository#removeTargetInCurrentTransaction`按类型+ID清理reaction，调用方须先锁目标并在同事务提交；文章、管理员评论级联及本人评论删除均已接入，禁止只按ID清理。
- 聊天室红包风险约束：`heartbeat` 可能抢到负积分，`rockPaperScissors` 猜错会扣积分，`dice` 当前服务端不支持领取；自动化脚本默认只建议开启安全类型。
- 用户在线时间结算链路在 `processor/channel/UserChannel`；`onlineMinute` 是分钟数整型字段，结算毫秒差必须先用 long/TimeUnit 转分钟，不能先强转 `int`，否则单次连接超过约 24.85 天会溢出为负数。
- Evolve 游戏入口是 `/games/evolve/`，PC/移动模板都在 `skins/classic/*/games/evolve/index.ftl`；生产资源使用 `https://file.fishpi.cn/evolve/`，模板需设置 `window.fishpiEvolveAssetBase` 供语言包、Worker 与 Wiki 链接解析；Worker 跨源加载需走同源 Blob 包装后 `importScripts` CDN 脚本；鱼排集成包含鱼游入口、左下角云存档面板、`gameId=40` 排行榜同步与 `gameId=evolve-save` 云存档；不再覆盖 CDN 根 `/main.js`；Evolve CDN 依赖是长缓存，主脚本和与主脚本版本强绑定的库（如 Popper）都需要带 `${staticResourceVersion}`。
- 接口设计安全约束：前后端新增/改造接口时，必须同时评估常见漏洞（越权、未鉴权访问、CSRF、XSS、注入、SSRF、批量请求滥用、敏感信息泄露）。
- CSRF 安全判断必须先确认接口是否同时支持页面 Cookie 与 `apiKey` 客户端调用；已兼容 `apiKey` 的写接口不能直接补 `csrfMidware::check`，否则会破坏 RESTful/API 客户端。加固这类接口需先拆分页面入口与 API 入口，或设计明确兼容 `apiKey` 的 CSRF 例外。
- 评论/文章公开接口与 WebSocket 事件必须使用响应白名单；允许返回 `commentAuthorId`、`articleAuthorId` 这类公开用户 ID，但禁止返回 `commentIP`、`commentUA`、`articleIP`、`articleUA`、`userPassword`、`userLatestLoginIP`、`userPhone`、`userEmail`、`userQQ`、`secret2fa`、token/key 等高敏字段。完整 `Article` 或完整用户对象不得裸传，需先脱敏。
- 评论读取类公开接口即使不走 `/article/{id}` 路径，也必须按目标文章执行 `articleAnonymousView` 与讨论帖可见性校验；校验当前用户时必须兼容 `Sessions` 和 `apiKey`，否则会绕过文章页匿名访问限制或误拒绝合法 API 客户端。
- 公开页面模板也不得把 IP/UA/密码/联系方式/2FA/token/key 等高敏字段写入 HTML 或 `data-*`；后台管理审计页可显示 IP/UA，但必须保留登录与权限保护。
- 涉及 API 入参或返回字段变更，且可能影响第三方客户端兼容性时，必须先说明接口、字段、兼容影响和替代方案，经过用户同意后再修改；安全漏洞修复也要把破坏性影响显式说清楚。
- 对外接口文档维护：只要新增、修改或删除接口，必须直接更新项目根目录 `API.md`，并与 `UPDATE_LOG.md` 同步产出面向第三方开发者的更新记录；`UPDATE_LOG.md` 按时间倒序维护，新记录必须写在适用说明之后、既有记录之前；最终回复提醒用户把 `API.md` 内容同步到网站 API 文档文章 `https://fishpi.cn/article/1636516552191`；文档保持该文章 Markdown 风格：模块用 `## 模块名`，接口用 `### 接口名`；接口行使用反引号包裹的 `METHOD /path`；包含简短用途说明、`请求:`/`请求：`、`| Key | 说明 | 示例 |` 表格、`请求示例：` 的 `bash` curl 代码块、`响应：` 表格；嵌套字段用 `-`、`--`、`---` 前缀；注意事项用 `>` 引用块；不要改成 OpenAPI/schema 或纯文本表格风格。
- 字符串输入必须做限制与校验：长度上限、空白处理、字符白名单/黑名单、格式校验（如用户名/URL/JSON）、必要的转义或编码；禁止直接信任前端传参。
- API.md / UPDATE_LOG.md 是公开文档，禁止写入安全与实现细节：限流阈值（如"60 秒 10 次/IP"）、匿名访问校验策略、CSRF 设计、防扫描链路、"与页面态接口互相独立"等内部关系说明一律不写；鉴权描述统一为"需要 apiKey"（匿名可访问接口在请求参数表加一行 `apiKey | 通用密钥（可选）`，正文不再啰嗦说明）；分页/条数上限（size 默认值与最大值）属于接口契约可以写。
- 不要顺手把硬编码业务值改成 `symphony.properties` 配置项；需要共享取值时提取为 Java 常量即可，只有用户明确要求或确有运维调整需求才做配置化。
- 涉及业务规则（可用字符、最大长度、是否允许 HTML/Markdown、过滤策略）不明确时，先与用户确认规则再实现，避免误伤或放漏。
- 上传链路：编辑器贴图走 RhyPic 前端直传（`Util.rhypicTicket` + `pic.fishpi.cn/api/v1/files`，字段名 `file`）；头像/裁剪/涂鸦等 `/upload` 代理通道字段名必须是 `file[]`，由 `FileUploadProcessor#uploadViaRhypic` 签票转发。图床拒绝无扩展名文件：FormData 裸 Blob（cropper、聊天室涂鸦）必须带文件名（append 第三参数），后端同时按 Content-Type 为无后缀文件名补扩展名，并在 succMap 保留原名别名兼容 `succMap.blob` 类老前端。前端魔数校验 `isImage` 只认 JPEG(SOI FF D8 FF)/PNG/GIF，WebP 不在 `upload.suffix` 白名单，头像场景给明确提示。
- 鱼游投稿链路由 `FishGameProcessor` + `FishGameMgmtService` + `FishGameQueryService` 负责，数据表为 `fish_game`、`fish_game_vote`、`fish_game_comment`；投稿必须通过 OAuth 说明网址校验后进入待审，管理员导入旧清单按已审核处理并按目标网址去重。`/activities` 只展示已审核记录并按点赞数排序，PC/移动模板和资源需同步维护。

- v3 聊天室在线名单由 `ApiV3ChatOnlineQueryService` 合并当前实例旧连接、已认证 chat 订阅和 `ChatOnlineSnapshot` 节点采样，按当前资料过滤在线隐私与有效状态后分页；人数只计可见用户。节点采样原子发布仅用户名，不返回节点凭据；其他实例的 v3 连接未共享，不能宣称全站实时人数。

- v3历史读取在`ApiV3RevisionQueryService`：本人或当前精确历史权限的合法读者，私密仍限双方；只投影清洗正文，不返编辑者ID，current仅已有历史且原文忽略空白不同，评论current.time=null。分组写入`ApiV3EmojiGroupService`仅本人type0，type1不可改删，删除保留全局图片/其他组/分享；旧写不共锁，不能宣称新旧全局唯一。

- v3鱼游投稿/编辑在`ApiV3FishGameSubmissionService`：只校验旧OpenID授权URL参数及游戏同站回调，不抓取/证明归属；当前与待审OAuth分列，旧审核批准时随pending内容合并，手改URL清旧OAuth。先迁移20261023鱼游SQL；新旧写入口未整体共锁。图片成员`ApiV3EmojiItemService`以内部namespace先于actor锁串行，完整URL按BINARY精确身份，自定义添加镜像全部、移除全部级联本人组，历史损坏不自动清理。

- v3会员昵称样式`ApiV3MembershipStyleQueryService`仅指定1..100个ID/profile.read，按当前有效账号/会员/档位支持投影bold/underline/color；无样式统一空对象，不能回用旧全站原configJson。捐助链接GET由`ApiV3DonationPage`固定配置站点/charge/point，不发Cookie或订单，浏览器仍需页面登录。

## 目录速览
- 后端：`src/main/java/org/b3log/symphony`（入口 `Server.java`，核心分层 `processor/service/repository/model/util`）。
- 资源：`src/main/resources`（配置、静态资源、`skins` 模板）。
- 前端编译输出：`src/main/resources/css`、`src/main/resources/js`。
- 构建：后端 Maven（`pom.xml`），前端 Gulp（`gulpfile.js`、`package.json`）。
- 本地 Latke 框架源码：`C:\Users\陈辉\IdeaProjects\rhy-latke`（非官方版本，分析框架行为优先对照这里，不要假设与官方仓库一致）。

## 关键链路（持续维护）
- 单项本人关注状态在`ApiV3FollowQueryService#userState`，当前actor/target有效及关系EXISTS同一只读SQL，仅targetId/following；不调用吞错的旧isFollowing。客户端关注命令只保存未确认的明确PUT/DELETE，回放结果后必须GET当前状态，不能把历史receipt当最新状态。
- v3游戏存档仅`ApiV3GameSaveInputs`精确目录，首批evolve-save单槽；`ApiV3GameSaveService`新旧HTTP共锁owner，CAS/metadata回执/版本同严格根，迁移只复制唯一旧记录不覆盖。旧登记游戏保存须JSON、盲写递增版本，其他cloud键保留；先迁移20261025_api_v3_game_save.sql。捆绑Latke会有损重写getBytes，存档须经同包`ApiV3GameSaveRawBody`读原Netty字节校验，不能换回框架归一化正文。
- 本人QQ由ApiV3QqSettingsService独立第一方当前授权、settings.qq严格根与回执管理，公开/第三方profile响应不扩字段；旧Latke Strings.isNumeric实际是Double.parseDouble，禁止用于QQ纯数字验证。个人标签ApiV3ProfileTagsService按owner→统计namespace→当前目录锁，标签/creator/self/统计/设置/settings.tags回执同根，缓存边界后刷新；ApiV3ProfileTagInputs先验每个原项，不能复用旧Tag.formatTags静默丢弃/截4项。共享Tag.normalizeAlias但旧写未全共锁，不宣称新旧全局唯一或跨实例缓存强一致。
- OAuth token/revoke以`ApiV3OAuthProcessor.protocolAccess`只读当前绑定凭据作为端点身份，无效仍原anonymous；不写Cookie/IDENTITY/IP白名单或viewSkips，全局Before黑名单先执行。正确绑定的已用code/轮换refresh仅可进原家族重放撤销，最终写根仍重验；不能把预检owner用作其他资源身份。
- v3三类红包`ApiV3ChatRedPacketService`本金/税/消息/持久份额/回执同根，ID绑定专属名单仅sender，旧open只桥接新head；历史红包原规则保持。共享删除primitive锁消息后检查新head，未领完409，须先迁移20261024_api_v3_chat_red_packet.sql。0份额仍记领取但不造零额账；税仅精确有效VIP4免，禁止回退进程桶或把未领完消息删掉。
- v3本人导出`ApiV3PostExportService`第一方当前密码/MFA，完整RR本人快照→账户对→证明绑定重验→私有DB块/100积分/metadata/回执同严格根；24h下载逐块/总hash及当前授权后发ZIP，每分钟同事务清理并释放连接。先迁移20261024_api_v3_post_export.sql；禁止复用旧公开图床导出或把预算超限截断成功。
- v3普通积分改名在`ApiV3UserNameService`：双activity.write/settings.write与幂等，账户对→namespace→当前有效用户，名称/现有费用/回执同严格根；新主账scene=settings.user-name分列dataId新名/memo旧名，显示经`UserNamePointData`编码，禁止连字符拼接。提交/回放后修索引及失效名称缓存，重放不覆盖后来改名；旧/settings/username扣费链本批未改。
- 敏感设置仅第一方`ApiV3SecurityProofService`当前密码/已有TOTP；client命名空间→当前user/access→独立credential state，`AccountCredentialMgmtService`同严格根递增epoch并撤OpenID/v3/code/pending。Cookie/apiKey/OpenID每次当前SQL比版本，缺版本仅epoch0兼容；发行只用认证时Proof，不可重读新epoch升级旧身份。先部署20261023三份credential/MFA/contact迁移，旧重置自动登录绑定本次提交版本。
- `ApiV3MfaService`准备10分钟、secret/otpauth仅当次，启用消费清secret；`ApiV3ContactSettingsService`先proof→无SQL锁发送→当前epoch/旧联系值快照重验后保存，HMAC码及旧值绑定，修改/消费/全撤同根。WS后续授权检查拒旧凭据；User/Shop关闭按建立时归属清理，不能因凭据失效跳过移除/在线结算。账号验证码邮件须走`Mails.sendAccountBindingCode`专用无debug/正文日志通道，不能复用旧MailSender.sendMessage。完整文件与验收见account-credential-work。
- `ApiV3TransactionSavepoint`嵌套primitive须检测原owner仍active且TX未换；内层回滚保存点失败已中止根时，外层不可再次碰已关闭连接或覆盖原错。图片分享/导入/迁移在`ApiV3EmojiTransferService`，严格校验每个原始项后去重，坏项整批422；快照独立于源组，迁移仅本人emojis云数据，不开放任意cloud键。
- v3转账`ApiV3PointTransferService`完整账户对先于严格根、用户按ID锁；主款/费/通知revision/回执同提交，精确有效VIP4及实际费收款者免。重放从本人回执取得锁提示，仅当前actor必须有效；私有fee账本元数据投影后返回，后置主账验证去重修索引。旧转账未整体改造。举报`ApiV3ReportPublicationService`预hint→排序用户→回执→当前目标，fresh重验绑定、重放不依赖目标；仅复用最早pending，旧入口未共用去重。新memo存储编码供两端管理模板直接输出，原始/编码长度均有界。
- v3复读投稿`ApiV3RepeaterPublicationService`绑定职业来源与回执同严格根；新旧点赞共用`RepeaterLikeMutationService`，用户→目标→本人likes，COUNT用当前锁定读取；只收敛本人重复，保留其他历史重复计数。旧toggle请求/成功字段保持，拒绝嵌套根，不能绕回旧分开计数写入。
- v3邀请码购买`ApiV3InvitationPurchaseService`仅新业务检查当前邀请模式；账户对先于严格根，namespace→模式→当前用户→回执，扣费/码/回执同提交，TTL以真实码oId计龄。重放仅复核本人有效性并恢复原code，不保证旧码仍可注册；码不放入账本/失败日志字段。提交后主账锁验证并修复索引，内部transferId必须投影后返回；沿既有point来源及membership索引迁移，旧先造码再扣费保持且无code唯一约束，不宣称新旧整体原子或绝无碰撞。
- v3复读机`ApiV3RepeaterQueryService`保留前N列表/前50排除后随机池语义，不假称全量分页；可选content.read与匿名盾，liked仅当前Bearer，失效作者不移除内容而author=null。常用表情PUT在`ApiV3EmotionSettingsService`：仅目录别名/有序数组，空数组恢复默认，严格根与settings.emotions回执仅替换本人type0；旧setEmotionList仍删全部类型且不共用actor锁，不宣称新旧整体串行。
- v3职业成长提示/排行/公开资料由`ApiV3ProfessionGuideQueryService`、`ApiV3ProfessionRankingQueryService`、`ApiV3ProfessionPublicQueryService`只读投影；当前展示revision和等级scheme各自发布，scheme可绑定同职业退休revision。排行先按ranking隐私筛可见人群再连续编号，隐藏experience仍保留等级名次；公开资料十模块独立判断且experience跨贡献/动态省略，rarity/ranking共用可见榜口径。公开动态必须当前PUBLIC来源及目标可读，匿名来源不关联，HIDDEN对本人也无公开路径例外；超查询预算503而非伪造完整统计。
- 本人匿名创作GET由`ApiV3AnonymousContentQueryService`提供元数据，评论先用有界讨论候选/正文预算及共享`UserMentionCandidates`精确解析，再绑定当前正文SHA2与actor快照做SQL计数分页；超预算503而非截断。管理员用Java精确角色判断，不能用CI数据库字符串比较扩大权限。邀请码状态POST仅投影stored status，不读/返预留字段，不把unused当可注册保证。
- 主职业设置`ApiV3ProfessionPrimaryService`只选当前已发布职业、免费且不造progress，按列更新privacy中的selection/userProgressVersion；旧职业隐私整行写仍有并发覆盖局限。`ApiV3MedalDisplayService`按本人/定义/持有锁设置display而非toggle，重复定义或持有409；两者重放前重查当前账号，原回执不覆盖后续设置。
- v3职业/勋章/会员读取分别在`ApiV3ProfessionQueryService`、`ApiV3MedalQueryService`、`ApiV3MembershipQueryService`。职业目录仅发布版本，本人进度按历史版本且不伪造零级；`ApiV3ProfessionDetailQueryService`提供本人真实详情及90天APPLIED非零记录，无progress为404，仅白名单概要、不返回来源正文/内部ID。勋章本人含隐藏、公开仅有效展示，禁止复用会删过期记录的旧查询；会员仅本人状态和已知权益白名单，不裸返configJson/benefits。
- 会员写入在`ApiV3MembershipSettingsService`/`ApiV3MembershipOpeningService`：仅支持的bold/underline/color可改，VIP4效果仅精确YEAR/MONTH两码，空旧配置只合并显式字段；完整defaults仅供开通。无效/用尽券422不扣费，有效/永久禁止重复开通，自然日期限与积分/券/记录/礼包同严格事务。内部回执转账ID必须投影后才HTTP返回；提交后按主账PK核验并幂等修复积分索引，先迁移20261022_api_v3_membership.sql。新旧开通均事务前取账户对锁，但旧赠包/缓存/提交策略未整体改造，不宣称新旧全部原子或缓存强一致。
- `ApiV3ProfessionSettingsService`的skip沿旧规则清空主职业，privacy沿旧六预设/十模块，仅按相关列和版本写入；`ApiV3MedalOrderService`按全部本人持有（含隐藏/过期）相邻移动后重编号，并列按order/oId，超技术预算503而非截断。两者每次及重放在严格根当前锁有效用户，回执不覆盖后续操作；旧隐私/勋章整行写未同步改造，不能宣称新旧整体原子化。
- v3完整清单与后续分工见`docs/api-v3-progress.md`；以总register可达的METHOD+path计数，源码/隔离测试/运行验收分列，用户侧职业/勋章/会员不可按后台整体排除。v3热度GET需content.read及`ApiV3RealtimeTargetService`当前授权，合计本节点旧连接与重新授权的文章订阅连接，仅近似连接数，不是全站或去重人数。
- v3表情四个GET由`ApiV3EmotionProcessor`提供本人常用/目录/图片分组与条目，均profile.read；不调用会创建默认组的旧GET，items查询重复绑定owner。PC/mobile旧目录与v3共用`ApiV3EmotionOptions`，保持EOF分隔但对外去除；红包类型目录已标写能力true，仅random/average/specify开放，heartbeat/rockPaperScissors仍未开放。

- v3 MBTI由`ApiV3Personality`严格规范16类型及可选-A/-T，大小写/首尾空白规范化、空值清空、无效422（用户已确认）；GET/目录需profile.read，PUT走settings.write及settings.personality幂等事务，锁当前有效用户仅写mbti/更新时间，沿用ProfileWriteQuota。旧`SettingsProcessor.checkMBTI`仍用于旧资料/注册，禁止顺手改变其规范化行为。
- v3头像GET/PUT在`ApiV3SettingsProcessor`：GET由`ApiV3AvatarQueryService`只读本人当前有效账号URL，PUT用settings.avatar幂等事务和`UserSettingsMutation`锁用户后仅写URL/type/更新时间；允许任意合法HTTPS、255 UTF-16、保留签名query、不抓取远程内容。旧头像设置及页面展示规则保持，不能用`AvatarQueryService`处理后的缩放URL代替本人保存的地址。
- OAuth同意页使用 `ApiV3AuthorizationRequestService`：服务端保存owner/client/完整回调/scope/state/PKCE及handle/proof哈希，确认只传request/proof/明确决定，禁止用表单覆盖user/scope。hint先owner过滤供client锁序，再锁请求重验，决定/签码严格同事务，批准和拒绝均一次性。HTTP必须仅取Sessions.currentCookieUser（全局Sessions可能已被apiKey提升），并接 `ApiV3ConsentSecurity` 的配置站点精确来源+非空Session CSRF；不能拿legacy CSRFMidware的Referer前缀替代，也不能回退apiKey/Bearer。state可省略、最多1024 UTF-8字节，回调禁止预置OAuth响应参数并编码state/码；HTTP在ApiV3OAuthProcessor，PC/mobile授权模板同步；普通业务信封不套OAuth协议。
- 第三方PKCE核心在 `ApiV3AuthorizationCodeService`：签码必须加入服务端确认的授权请求事务，不能接收HTTP指定的user/scope。client→精确注册redirect→code→当前user，消费码与Token对严格同事务；匹配绑定及PKCE的重放撤销对应家族，错误证明不撤销。新表见20261019_api_v3_oauth.sql并已入repository.json，先迁移再联调。用户已确认HTTPS、仅开发HTTP回环及登记的原生专用scheme；`ApiV3OAuthRedirects`要求原生反向域名scheme+单斜线路径，完整URI（含端口/查询）精确匹配，不做动态端口例外；旧/openid凭据独立。
- v3 Token写入统一先锁客户端再当前读Token；家族撤销必须按clientId/userId/familyId完整SQL更新，禁止分页截断或用旧活动快照回写state。`issueForClientInCurrentTransaction`由PKCE换码在同一授权码事务内调用、actor由服务端授权记录取得；局部保存点保护Token对。第一方Cookie仍只用于website_v3，不能把内部通用发行方法直接暴露成可指定用户的HTTP入口。
- 已批准应用登记核心为 `ApiV3ClientRegistrationService#createApprovedInCurrentTransaction`：服务端生成client_id，客户端行和精确回调行在调用方事务内同保存点写入，scope仅允许已实现的v3权限；不做HTTP身份/审批/幂等，调用方须完整提供这些边界，用户已确认仅管理员登记和维护；`ApiV3ClientManagementProcessor/Service`提供第一方当前管理员的列表/详情/幂等登记，最多20回调、每个2048；先按账号重置顺序锁客户端命名空间，再锁user/token/request，重放也重验。停用由`ApiV3ClientDisableService`把status=1、全部目标Token撤销与回执同严格事务，不删登记/回调；票据/WS在后续当前授权检查失效，不承诺同步跨实例断连。`ApiV3ClientUpdateService`完整替换名称/scope/回调，实际权限或回调集合变更同事务撤销全部Token、删除旧code与pending请求，保留已决定历史；名称或排序变化保留授权，状态不变；禁止自助登记。
- v3 注册选项在 `ApiV3AccountProcessor` / `ApiV3RegistrationPolicyService`：直接查询注册开关，邀请资格沿角色权限读取，仅为表单展示，不可作为后续写入授权。账号短信验证核心 `AccountVerificationService` 在调用方事务中先当前读锁用户，再按当前手机号/type/purpose/有效期锁并消费验证码，消费与账号变更同提交；同值重复码全部消费，禁止回退全局getVerifycode(code)。核心不含尝试限额，公开入口须先接共享限额。用户已同意旧register2新增code及新旧账号入口一起加固；reset-pwd及register2已接入，PC/移动页面已传code，不再重复询问。
- 新旧话题写入共用 `ChatTopicWriteService` / `ChatTopicMgmtService`：账户对锁及缓存边界外包事务，当前用户→房间head→option/chatRoomTopic，16积分、话题及topic/0事件同保存点；v3 PUT由 `ApiV3ChatTopicService` 显式Bearer准入及幂等。v3沿用字符过滤且超64 UTF-16拒绝，旧入口保留过滤/截断；投影worker重读已提交最新状态恢复旧页面，topic WS仅发revision，不能冒充messageId=0。旧Bot准入先执行，不能称整个旧Bot处罚与话题原子化。
- v3 弹幕 POST `/chat/barrager` 走 `ApiV3ChatBarrageService`：content.write、当前用户/手机/限制、纯文本清洗后32 UTF-16及#RRGGBB，费用与回执同幂等事务；`ChatBarrageQuota`共用旧用户名额度与admin例外，自动重试只计一次。提交后才发旧广播及当前实例的 `chat.barrager` 白名单事件，v3逐连接重验授权；即时动画不入历史/提交游标、不补播，不能声称跨实例或可靠送达。旧弹幕输入/扣款链路暂保留，展示插件前必须编码正文/昵称与作者路径，两套源码/产物同步。
- 新旧聊天室撤回共用 `ChatMessageWithdrawalMgmtService`：事务前账户对锁及缓存边界，按作者ID检查、锁用户后锁cloud的sys-chat-revoke每日记录；费用0/32/64……，与消息/删除事件/reaction/职业撤销同保存点。用户已同意共享持久计次、改名/重启不清零及旧入口失败不扣款；旧管理跨作者/免计次保留，v3 DELETE仅本人、content.write及幂等，确认提交后才索引/通知。存储删除核心为 `ChatMessageMgmtService#removeInCurrentTransaction`，不得绕回独立扣费或用户名归属。
- v3 GET `/chat/topic` 读取已提交持久话题（初始无记录兼容进程默认值），`/chat/barrager/price`读取当前进程价格，均需content.read；话题是纯文本，价格经ChatroomProcessor.get/setBarragerPrice成对访问。旧话题通知在channel.js/channel-2.js中必须文本编码话题/作者并编码作者路径，两套源码和min产物同步维护。
- v3 普通消息 POST `/chat/messages` 走 `ApiV3ChatPublicationService`：content.write、白名单正文/客户端与幂等；先取得actor/sys账户对，再打开缓存边界和事务，当前用户/限制/适用处罚/消息/职业/最后发言时间同事务。仅applied后广播、活跃度与提醒；处罚由 `ChatMessagePenaltyMgmtService` 写禁言/思过簿/扣分通知，提醒走 `ChatMessageNoticeService` 独立严格提交并释放连接。普通正文不执行业务命令或AI。
- 聊天风控重复确认和复读计数由 `ChatMessageAbuseState` 供新旧入口共用；普通保存核心不能替代准入。v3 禁言/风控读取走 `ChatMessageRestrictionQueryService`，要求已有事务并锁定当前记录，只读到期时间、不自行清理或提交；损坏/重复记录须报错，不能当作未限制。`ChatMessageRestrictions` 保留风险校验→确认→禁言顺序及全员禁言时的管理例外。
- 普通聊天保存共用 `ChatMessageMgmtService`：`saveInCurrentTransaction` 用保存点绑定消息、变更序列和职业来源，外层须提交；旧入口用严格根事务 `saveLegacy`，确认提交后才回填消息ID。v3 普通正文已确认用 `ChatMessageText` 拒绝完整业务包装、执法/zf前缀及卡片JSON，须在保留词处理前后检查；该保存核心不负责禁言、重复确认、复读处罚或外部广播，不能据此直接开放发送。
- 聊天发送基础校验共用ChatMessageValidation/ChatMessageValidationService，客户端标记共用ChatMessageClients；保留旧频控→保留词→trim/4096 UTF-16长度顺序和管理员例外。MIN_STEP_CHAT_TIME当前为0，不能擅自增加业务频控；基础校验不负责禁言、命令或费用。旧ChatRoomBot.muted/risksControlled在记录过期时自行开事务，v3事务体不能直接调用；v3须显式传Bearer用户，不能复用依赖Sessions的旧middleware。
- 聊天室已提交变更经ChatRoomRepository及共享ReactionMgmtService写api_v3_chat_event和api_v3_chat_state/main；head行锁串行分配revision，不能用消息ID/时间作提交游标。ApiV3ChatTransaction以外层事务保存点绑定消息/reaction与事件，无法局部撤销时整笔回滚。取票POST /api/v3/chat/realtime-ticket要求content.read；ApiV3ChatStream有界消费已提交序列、逐连接授权，只发ID/版本，缺口或倒退发chat.sync_required。启动时初始化调度，清理只删旧事件、不重置head；两张表及种子须先迁移，非持久化在线另接，弹幕另发即时事件。
- v3聊天室读取走`ApiV3ChatProcessor`/`ApiV3ChatQueryService`，统一要求content.read；历史记录的content是消息外壳JSON，必须经`ApiV3ChatMessageDto`白名单分普通Markdown/红包摘要/天气/音乐，禁止裸返外壳或红包who/内部字段。游标列表固定按ID范围查询、返回升序；上下文按半径有界读取。作者为消息发布时公开快照，不能从页面Cookie/apiKey补身份。
- 当前Latke JDBC `commit()`会吞掉提交异常；v3幂等/Token/Socket根事务用`ApiV3Transactions.strict`提交，primitive不能绕回原提交。旧注解事务未全局替换，不能仅凭事务结束判定成功；升级jar需重跑提交故障专项。WS回调和后台worker必须显式释放JDBC线程资源，不能依赖HTTP收尾。
- v3 Socket票据以channel/targetId绑定通知或单篇文章，按频道复核scope；文章授权经`ApiV3RealtimeTargetService`直接查询当前文章/作者/读者，讨论邀请解析与旧链路共用`UserMentionCandidates`，不得用缓存身份延续已撤销权限。文章取票在`/api/v3/articles/{articleId}/realtime-ticket`；ArticleRepository/CommentRepository和共享ReactionMgmtService在原事务内写`api_v3_article_revision`，后台只轮询已提交标记，逐连接授权后发仅ID的article.changed。通知与文章订阅索引隔离，禁止直接转发旧ArticleChannel完整事件；热度/在线人数另行接入。
- 当前Latke WS升级不走HTTP顶层中间件，query还会覆盖ip。v3 realtime先由`ApiV3SocketHandshake`查精确无query路径/帧上限和显式风控，再等authentication.required后首帧票据认证；禁止Cookie/apiKey回退。通知写入与`api_v3_notification_revision`标记同事务，`ApiV3RealtimeService`只读已提交标记、逐连接重查原access后发本人空提示，不转发旧UserChannel JSON。批量通知删除用owner-aware方法，通用remove(Query)不自动追踪owner。上线前同步建表和repository.json。
- v3 上传签票走 `ApiV3UploadProcessor` / `ApiV3UploadTicketService`，要求 uploads.write，当前用户只取Bearer并从数据库投影重读；`RhypicUploadTickets`只共用Ed25519协议，旧入口字段保持。v3的uploadURL是含/api/v1/files的完整公开地址，不能用internalUrl；短期凭据不落业务幂等表，每次取票新签发。文件直传字段file，旧/upload代理仍是file[]。
- 改名卡新旧入口共用 `UserNameMgmtService`，用户名与背包同事务，旧改名卡根事务也须严格提交；v3 同时要求 activity.write/settings.write。注册、改名及未验证账号名称清理先锁会员统计 option，再当前读用户，最后锁背包，保持名称占用串行；注册保留原未验证账号策略。用户名规则共用 `UserNameRules`，缓存仅事务结束后失效并重读。普通积分改名仍有独立扣费链路，不能称其整体原子化。
- 本人评论删除共用 `CommentRemovalMgmtService`：旧入口自带事务，v3 DELETE经comments.write和幂等事务；发布锁/账户对在事务前取得，相关用户按ID排序锁定后才锁文章/评论，扣费、计数、职业来源及清理同事务，索引/通知刷新/审计在提交后。用户已同意旧入口失败不扣费；真实MySQL与后台强删竞争仍待验证。
- 评论发布/编辑延迟提醒统一走 `CommentNotificationMgmtService`，当前文章/评论锁内检查接收者可读性并保存通知，提交后刷新；不要直接用文章也在使用的旧 `addAtNotification` 写评论提醒。`@participants`仅按新建的提醒计数，收费仍沿用事件内独立转账；通知事务不等于整个异步事件事务。
- 评论投票走 `CommentVoteMgmtService`，先排序锁用户再锁文章/评论，vote行、计数和点赞通知同事务，提交后刷新/记活跃度；旧控制器保留toggle及type=操作前投票。v3 `/comments/{id}/vote` 由 `ApiV3CommentVoteService` 提供GET(content.read)/PUT/DELETE(comments.write)，PUT是设置，幂等结果不覆盖后续投票；GET投影同时按actor及dataType筛选，只返id/vote/计数。`VoteMgmtService`只允许文章，禁止套其事务包装写评论。
- 文章投票复用 `VoteMgmtService#setInCurrentTransaction`，旧toggle自行提交，v3 `/articles/{id}/vote` 经 `ApiV3ArticleVoteService` 加幂等事务（GET content.read，写 content.write）。文章计数、`tag_article`的redditScore及点赞通知同事务，关联只更新分数，保留原整数时间排名规则；刷新/活跃度在提交后。文章/评论共用 `ApiV3Votes` 输入及DTO，但查询、mutation和幂等operation均按资源类型隔离。
- 文章感谢/打赏共用 `ArticleRewardMgmtService`，先取得actor/sys与actor/作者完整账户对，再同事务转账、计数、reward及通知；匿名免转账、感谢也计入原LIVENESS_REWARD。v3 `/articles/{id}/thanks`、`/reward`提供GET状态与幂等POST，正文始终经文章详情按当前读者读取，不能存进幂等结果。锁定但可读文章仍可投票/感谢/打赏。旧`/article/reward`已获用户批准收紧正文返回：通过`rewardForViewer`在扣款前校验显式读者，提交后重查读者/文章及购买资格，仅合法启用打赏返回原HTML；不得退回Sessions身份或只凭无操作结果放行。
- 评论采纳共用 `CommentAcceptMgmtService`：显式传入当前操作者，事务内复核文章作者，并用文章行锁串行不同回答的采纳；转账/标记/reward/通知同事务，提交后索引/刷新/活跃度。旧入口已有其他采纳时保持无操作成功，v3返回409；同一目标重试不重复付款。旧Cookie入口的CSRF保留，v3走comments.write及Bearer可读性，不新增文章类型或匿名收费限制。
- 评论感谢共用 `CommentThankMgmtService`：先投影接收者并预锁actor/sys及actor/接收者账户对，事务内按当前评论重验归属、查重；转账/感谢计数/reward/通知同事务，索引/刷新/活跃度在提交后。v3 POST `/comments/{id}/thanks` 用comments.write、显式Bearer可读性与幂等事务；旧缺失/失效目标仍保持无操作。投票和采纳只更新所属计数字段，不能把旧评论整行快照覆盖其他互动。
- 评论发布核心为 `CommentMgmtService#addCommentInCurrentTransaction`，旧入口自带事务，v3须复用核心而非套旧方法。先按可信文章作者取得actor/sys和actor/作者全部账户对，事务中复核作者未变；正文/计数/版本/职业来源/回复已读/自动订阅/扣款同事务，计数写列白名单。回复已读经 `NotificationStateMgmtService#markDataReadInCurrentTransaction` 固定截止批量写，`afterCommentPublished` 提交后才刷新带未读数的消息、索引和广播；自动订阅不再由控制器二次写入。
- 积分锁统一在 `AccountPairLocks`；旧直接转账与外层事务必须共用该池。多收款方通过 `ArticleMgmtService#withPublicationLocks`→`PointtransferMgmtService#executeWithTransferLocks`，在事务前按确定顺序取得完整账户对集合；持锁期间只允许重入原集合的子集，禁止临时扩展。`CommentPublishPoints`共用旧评论预留/扣费规则：普通支付作者、自评支付sys、私密额外付sys；任一匿名不实际扣费，但仍检查原余额门槛。
- 评论编辑共用 `CommentMgmtService#updateCommentInCurrentTransaction` + `CommentEditing`，沿用文章发布锁/缓存边界，正文、版本和费用同事务；只更新正文/可见性/IP/UA，禁止整行旧快照覆盖计数或段评元数据。`afterCommentEdited` 只在提交后索引/发事件；v3 PUT 需comments.write、本人及目标文章可见性，幂等重放不发事件。评论/文章任一匿名免编辑费；普通评论超过五分钟即使正文不变仍收费，版本仅非空白变化时新增。
- v3 长篇段落/段评和评论详情走 `ApiV3ParagraphProcessor` + `ApiV3ParagraphQueryService`，挂可选content.read与匿名校验。`LongArticleParagraphService.compatibleMarkdown` 用同次标题快照配对旧标识与公开安全视图，只返回安全正文/摘要；别名计数/筛选共用SQL条件，冲突409而不猜归属。历史快照不输出，v3回复也不复制；v3文章编辑在原事务中迁移临时锚点并检查冲突，旧编辑同步拦截仍待用户确认。匿名实时作者ID已获用户同意置空，须在权限/模板处理后脱敏。
- v3 评论发布走 `ApiV3CommentProcessor` + `ApiV3CommentMgmtService`：文章作者付款目标须在事务前投影并预锁，锁内重验；`ApiV3CommentTarget` 校验同文章可读回复与服务端段落快照，幂等回放不再推送。`ArticleChannel` 按当前用户/文章/评论可见范围生成接收者独立数据；私密正文在模板渲染前清空，不能只依赖模板隐藏。评论提醒同样按当前可读范围过滤。
- 评论发布/编辑正文校验共用 `CommentValidation` + `CommentValidationService`，分prepare（额度/保留词/长度）和moderate以保留旧目标校验先后顺序；额度经 `CommentWriteQuota` 同步共用，保留原角色例外。旧CommentProcessor写入从loginCheck的User.USER取身份，query/body apiKey一致；v3不得调用依赖Sessions的旧中间件，须显式传Bearer用户。
- 文章内容校验共用 `ArticleValidation` + `ArticleValidationService`，旧 `ArticlePostValidationMidware` 只适配旧响应。v3 创建走 `ApiV3ArticleWriteProcessor` + `ApiV3ArticleMgmtService`，需要 content.write 与 commonAddArticle（不是管理权限articleAddArticle）；输入只认Bearer作者、幂等与本人草稿同事务，结果只返回id/permalink。v3不接受免审字段，思绪POST仍关闭；旧免审角色、额度例外与历史打赏长度规则保留，避免悄悄收紧旧API。
- 用户投稿必须走 `ArticleSubmissionMgmtService#submitInCurrentTransaction`，手机/新人/宵禁/周报策略共用 `ArticleSubmissionPolicy`，周报奖励与底层正文/章节/版本/职业来源/扣款同事务。外层先经 `withPublicationLock`（服务锁→账户对锁→事务），用 `ArticlePublicationCaches` 暂缓缓存，仅确认新提交后调用 `afterArticlePublished`；v3按幂等applied判断，不能在重放上发事件。旧入口自带提交，v3不得直接套旧方法。`ArticleWriteQuota`与旧校验共用额度；频控包装需同步调用，底层SimpleCurrentLimiter的get/put不原子。
- 文章编辑核心是 `ArticleMgmtService#updateArticleInCurrentTransaction`，正文/标签/章节/段评位置/版本与扣款同事务；仍用发布锁和缓存边界，提交后才调 `afterArticleEdited`。`ArticleEditPoints` 保留五分钟内免编辑费但仍需余额门槛、匿名免扣、新启用打赏固定费；旧实现rewardPoint=0不会关闭已启用打赏，不要暗改。段评迁移只写位置字段，文章和评论缓存也由该边界延后处理。v3 PUT 经 `ApiV3ArticleEditing` 在锁定本人文章后合并部分字段、校验并幂等提交；重放哈希只包含提交字段，不含当前数据库默认值。
- v3 编辑源码走 `GET /me/articles/{articleId}/source`（content.read）与 `ApiV3ArticleEditorDto`：owner+ID联合过滤、字段投影，单次查询仅关联本人有效专栏；不复用公开详情或页面转义。正文/思绪/打赏正文保留原始串，锁定文章可由本人读取，但不代表具备写权限；响应只读字段不能原样回传PUT。
- 文章删除共用 `ArticleMgmtService#removeArticleInCurrentTransaction` 与 `ArticleRemovalPolicy`，统计/作者/文章依序锁定；数据库清理和职业撤销同事务，提交后才执行 `afterArticleRemoved` 的索引/音频清理。v3 DELETE 先按 Bearer 检查本人和权限，再幂等提交；重放不重复清理。普通删除保留24小时/周报/互动限制，打赏计数查询失败不得当成0放行。投票/奖励清理必须同时匹配目标ID与类型，禁止恢复仅按dataId删除；文章奖励含购买、感谢、采纳三类，不包含评论感谢或用户奖励。
- 思绪页面回放由 `ArticleQueryService` 的页面专用回调提供原始记录，PC/移动 `article.ftl` 用 `?js_string` 编码，`article.js`/`m-article.js` 用 Lute 清洗每帧重建 HTML；不要先清洗整段结构记录而破坏坐标。旧 API 字段保持原转义，自定义文章模板覆盖时需同步此链路。
- v3 设置走 `ApiV3SettingsProcessor`，资料/隐私/显示采用部分更新；旧隐私/皮肤与 v3 共用 `UserSettingsMgmtService`。事务中只按列更新，不提前写用户/会话缓存；结束后锁行读取已提交数据刷新缓存，失败则清缓存；v3 榜单快照用设置修订号失效，防止旧加载覆盖清理。资料修改与旧入口共用 `ProfileWriteQuota`，仍走原内容审核。功能/位置/语言也走同一事务体；旧功能页的功能字段与显示模式须同事务更新。首页跳转新旧入口共用 `SettingsRedirects` 校验精确 origin 与部署路径，不能用 URL 字符串前缀判断站内。
- 大事记个人投稿查询走 `ApiV3MilestoneProcessor` + `ApiV3MilestoneQueryService`，必须 owner+ID 联合过滤，不能复用公开详情。创建的事务内方法在 `MilestoneMgmtService`，`MilestoneSubmission` 白名单强制作者/待审状态/时间；缓存只在提交后清理。v3 投稿正文已确认采用 Markdown，在入口渲染清洗为 HTML 后保存，以兼容既有时间线；本人 content 返回保存后的 HTML。
- 系统背包存在 `cloud` 的 `gameId=sys-bag`；增减物品统一经 `BagMgmtService` 锁用户再锁背包记录，`CloudService` 仅保留兼容包装。免签卡的新旧入口共用一次事务扣卡和增加天数，v3 加入幂等事务；`putBag` 正数参数是增量、`maxTake` 是最终数量上限，不要把旧累加代码误判为翻倍。
- 签到/补签统一走`CheckinMgmtService`：先账户对锁，再同事务锁用户并写积分、连续记录和背包提示，提交后刷新缓存/积分索引。自然日规则为`DailyCheckinMutation`/`CheckinPatchMutation`；v3补签经幂等事务，旧补签共用核心，失败保留卡/提示且不额外发签到奖励；关闭的手动签到入口不得恢复。
- 昨日活跃度奖励的新旧入口共用 `LivenessRewardMgmtService`；先持有积分账号对锁，再开启事务、锁用户并检查当天原始转账记录，奖励与 v3 幂等结果同事务。积分冗余索引只在提交后刷新。日常签到仍由 `LivenessMgmtService` 活跃度触发，不能通过新 API 恢复已关闭的手动签到入口。
- v3 专栏管理走 `ApiV3ColumnProcessor`，与旧入口共用 `LongArticleColumnMgmtService` / `ColumnCoverMgmtService` 事务内方法；专栏元数据写入和章节归属校验共用父行锁，章节统计使用当前读，避免删除与新增章节竞争。排序允许只提交部分专栏，未提交项保持原排序。
- v3 鱼游列表默认按总投票数、点赞数、时间排序（用户已确认），保留 `sort=likes`；不要依据旧的 `/activities` 点赞排序说明更改该默认值。
- v3 清风明月发布/编辑/删除复用 `BreezemoonMgmtService` 的事务内方法，职业发布/撤销事件与内容同事务；`BreezemoonWriteQuota` 与旧入口共用业务频控。公开读取必须检查作者的 `userBreezemoonStatus`；关注流只读取关注用户公开有效内容且不回退全站；编辑不得改变作者、创建时间或审核状态。实时广播只在发布幂等首次确认提交后尽力发送。
- v3 鱼游投票/评论复用 `FishGameMgmtService` 显式事务内方法，先锁父鱼游并验证已审核；投票与计数同事务，PUT 设置、DELETE 清除，旧入口仍 toggle。新旧业务额度共用 `FishGameWriteQuota`；后台元数据更新必须限定字段，不能用旧对象覆盖投票计数。
- v3 社区读取走 `ApiV3CommunityQueryService`：鱼游/大事记必须按审核状态过滤；同城用户和文章均需检查作者公开位置，文章还排除匿名发表。统计只输出明确数字字段，在线总数已包含成员；鱼游新 API 必须使用真实分页，不能复用旧 getApproved 的 100 条截断。
- v3 排行榜由 `ApiV3RankingProcessor` + `ApiV3RankingQueryService` 提供；邀请/赞助/优选/小冰共用 `RankingQueryService`，旧接口也调用该服务。游戏榜只投影排名指标，不得直接输出 cloud 存档；JSON 集合读取需兼容 `JSONArray` 与 `List`。
- v3 通知的分类统一在 `ApiV3NotificationTypes`，读取走 `ApiV3NotificationQueryService`，状态写入走 `NotificationStateMgmtService` + 幂等事务；刷新通知必须在提交后发布。通知里的文章/评论需按 Bearer 阅读者重新检查可见性，禁止裸返 dataId、owner 或完整消息 JSON。
- 内容删除通知引用由 `ContentNotificationReferences` 统一区分直接ID、组合ID和reward记录ID；奖励清理返回被删reward ID，同事务清理相应通知，禁止仅按dataId扫删通知。旧AT未存目标类型，文章/评论ID碰撞时保留歧义AT；不得推测归属。
- 关注/收藏/订阅统一经 `FollowMgmtService#setInCurrentTransaction` + `FollowMutation`，锁定操作者与目标后维护关系/计数；旧入口自行提交事务，v3 加入幂等事务。缓存需在提交或回滚后失效；关注通知经 `NotificationMgmtService#addFollowNotificationInCurrentTransaction` 写入，提交后再刷新。
- v3 写入幂等由 `ApiV3IdempotencyMgmtService` + `api_v3_request` 在同一事务内提交业务与结果；回调必须调用 `*InCurrentTransaction`，不得自行提交、嵌套事务或直接发送外部事件。草稿写入走 `ApiV3DraftProcessor` + `ApiV3DraftMgmtService`，复用 `ArticleDraftMgmtService` 的现有规则。
- v3 个人读取在 `ApiV3PersonalProcessor` / `ApiV3PersonalQueryService`；scope 分 profile.read、content.read、activity.read，目标用户只能来自 Bearer。草稿详情必须用 owner+ID 联合过滤，积分 DTO 不得带双边余额或内部应用请求编号。
- v3 主页评论与关注动态的查询片段在 `ApiV3ProfileQueries`：评论列表不聚合讨论帖，私密正文复用 `commentDto`；`watching` 是关注用户/标签动态，`watching/articles` 是 `ARTICLE_WATCH` 订阅，不能与 `following/articles` 收藏混淆。
- v3 搜索/标签/领域/用户文章共用 `ApiV3DiscoveryQueryService` 的可见性、计数和分页条件；`ApiV3ContentDtoService` 只做公开 DTO 映射。用户主页子列表需用 Bearer 阅读者检查对应隐私开关；禁止从 Sessions 或旧 apiKey 补身份。
- `/api/v3` 文章/评论读取走 `ApiV3ContentProcessor` + `ApiV3ContentQueryService`，可选 Bearer 需 `content.read`；当前用户只从 `ApiV3Processor.IDENTITY` 获取，禁止回退 Sessions/apiKey 或复用依赖会话的页面内容组装。详情与评论共用文章可见性检查，匿名作者 DTO 不返回真实身份，打赏和私密评论单独授权。
- 职业成长系统完全独立于 `userAppRole`：定义、等级方案、自动化、来源事件、效果、用户汇总和贡献均走 `profession_*` / `user_profession*` 新表；效果和来源事件均先独立提交领取状态，再在所有权条件下完成事务，防止回滚后的并发覆盖。等级方案 `MIGRATE_ALL` 与 `SCHEDULED_SWITCH` 由 `ProfessionLevelMigrationService` 分批切换，定时方案由 `ProfessionScheduledSchemeService` 激活。首次选择职业的升级来源由 `ProfessionGrowthGuideQueryService` 根据已发布自动化规则动态提供，禁止在前端写死职业升级方式。停用职业由 `ProfessionRetirementMgmtService` 锁定定义与受影响用户，将系统通知与停用状态同事务提交，并在提交后刷新通知，禁止直接写通知表。
- 职业管理数据读取分为 `/catalog-summary`（分页摘要）和 `/catalog-detail/{professionId}`（单职业完整配置）；导入必须先走 `/config/precheck`，排序经 `/catalog/order` 在同一事务内校验并保存全部职业顺序，二者均限管理员登录态与 CSRF。
- 职业自动化执行器包含经验调整和系统通知；系统通知配置 `notificationContent` / `notificationWhen`（`ALWAYS`、`LEVEL_UP`），由 `ProfessionAutomationNotificationService` 在职业效果事务中经 `NotificationMgmtService` 发送，不能直接写通知表。通知效果必须排在经验效果之后，正文变量为 `{职业}`、`{等级}`、`{经验}`、`{变化}`、`{奖励}`；等级方案迁移不会回放来源事件，但会对新跨越的每级分别补发允许的等级奖励，并为已发布 `LEVEL_UP` 通知创建独立幂等效果。金手指外部职业经验效果没有 `automationRevisionId`，通知判断必须先识别这种外部效果，不能查询自动化版本，否则效果会进入 `APPLY_RETRY`。
- Latke 原生仅提供轻量皮肤元信息能力（如 `Latkes#getSkinName` 读取 `/skins/<dir>/skin.properties`），不负责 Rhythm 的运行时选肤；Rhythm 当前皮肤链路在 `BeforeRequestHandler#resolveSkinDir` + `Sessions` + `processor/SkinRenderer` + `util/Templates`。
- Latke IoC 会通过 Javassist 为每个 `@Singleton` Bean 生成子类代理，因此 `@Singleton` 类不能声明为 `final`；新增或修改 Bean 后需检查这一点，否则生产启动可能只显示 `Latke init failed`。
- 项目内 Latke 的 `Cookie#setSameSite` 接收字符串（如 `"Strict"`），不是 Netty 枚举；v3 刷新 Cookie 统一经 `ApiV3RefreshCookies` 构造，真实框架类型检查在 token 专项脚本中。
- Rhythm 当前“皮肤”本质是模板目录名：默认值来自 `symphony.properties` 的 `skinDirName=classic/pc`、`mobileSkinDirName=classic/mobile`；运行时先按 UA 分流 pc/mobile 默认皮肤，再按用户字段 `userSkin` / `userMobileSkin` 覆盖，设置页通过 `/settings/skin` 保存，`SkinQueryService` 负责扫描 `/skins/**/skin.properties` 识别可选主题。
- Rhythm 侧 `SkinQueryService` 现已按 UTF-8 读取 `skin.properties`，皮肤名称/描述可直接写中文；但 `src/main/resources/lib/latke-core.jar` 内的 `Latkes#getSkinName` 仍是旧 `Properties.load`，若后续有代码依赖该方法读取皮肤元信息，需要同步改 Latke 并替换 jar。
- 模板加载已支持“同设备 fallback”：若自定义皮肤缺少某个 FTL，`Templates` 会回退到默认皮肤的同路径模板（如 `foo/pc/header.ftl -> classic/pc/header.ftl`，`foo/mobile/common/comment.ftl -> classic/mobile/common/comment.ftl`）；因此新增皮肤可以按需覆盖，但至少要提供对应目录与 `skin.properties`。
- Freemarker + `org.json` 兼容约束：顶层放进 dataModel 的 `List/JSONArray` 可直接 `#list`，但挂在 `JSONObject` 字段里的嵌套 `JSONArray/Iterable` 在 FTL 中可能被包装成非序列；这类数据优先展开成固定字段，或改为顶层列表再渲染。
- 皮肤设置页预览图来自各皮肤目录下 `skin.properties` 的 `previewUrl`；新增皮肤若希望在设置页展示预览图，需要同步配置该字段。
- 前端全局主题变量入口：`src/main/resources/scss/_variables.scss`；`$theme-primary` 会影响 `module`/首页卡片/聊天室等主区背景，深色会导致整站大面积染色；顶栏建议在 `base.scss`/`mobile-base.scss` 的 `.nav` 单独设色。
- 移动端文章页（`skins/classic/mobile/article.ftl`）存在多个 `#replyUseName`（含隐藏占位 `.fn-none`）；`m-article.js` 处理回复目标时需优先选中非 `.fn-none` 节点，避免“回复对象已记录但指示未显示”。
- 评论区交互不是单点模板：首屏评论列表由 `ArticleProcessor` + `CommentQueryService#getArticleComments` 组装，展开“原评论/回复”走 `CommentProcessor#getOriginalComment/getReplies`，实时新评论卡片由 `processor/channel/ArticleChannel` 渲染 `skins/**/common/comment.ftl`；改评论动作区时需同步评估 PC/移动模板、`article.js`/`m-article.js` 以及实时插入链路。
- 文章代码高亮由 `src/main/resources/js/common.js` 的 `Util.parseHljs` 统一加载；`vditor/latest` 的 `.min.css` 高亮样式对应 `highlight.min.js` + `third-languages.js`，旧版样式才对应 `highlight.pack.js`。
- RSS 订阅链路在 `FeedProcessor` + `model/feed/RSS*` 手写 XML；输出前必须保证 XML 1.0 字符合法并处理 CDATA 结束符，思绪正文不要作为普通 `articleContent` 放进 RSS description。
- 首页摸鱼倒计时的节假日数据在 `util/Vocation`：数据源为 holiday-cn（jsDelivr/GitHub raw 年度 JSON，国务院官方公告），本地计算 type/dayName/vName/vRest/wRest；不再依赖 timor.tech；刷新失败保留上次成功数据并按周末规则兜底，字段契约与 `/api/vocation` 不变。
- `symphony_article.articleHotScore` 是数据库生成列，只用于查询排序；文章写入统一经 `ArticleRepository` 过滤该字段，业务代码不要把它作为普通字段增改。
- 文章历史版本链路：前端通过 `/article/{id}/revisions/list` 获取轻量版本列表，再按需调用 `/article/{id}/revisions/{revisionId}` 获取单个版本正文；旧 `/article/{id}/revisions` 已删除；两个新接口都挂 `loginCheck` + `permissionMidware` 并兼容 `apiKey`。
- 文章上下篇链路：`ArticleProcessor` 统一填充时间、作者、热门三组导航数据及小窗口列表；PC/移动模板共用 `skins/classic/*/common/article-adjacent-nav.ftl`；“全部文章”排序选择由 `article.js`/`m-article.js` 写入 `localStorage.articleAdjacentSort`。
- 发帖草稿箱链路：前端入口在 classic PC/移动 `home/post.ftl`、`home/long-article-post.ftl` + `add-article.js`，后端为 `ArticleDraftProcessor` + `ArticleDraftMgmtService` + `article_draft`；接口 `/api/article-drafts*` 挂 `loginCheck` 并支持 `apiKey`，不挂 CSRF；普通文章与长文章共用同一组草稿接口，长文章通过 `articleType=6` 和 `columnId/columnTitle/chapterNo` 区分；思绪帖草稿必须同时保存编辑器正文与 `articleDraftThoughtContent`。
- 新版表情面板的入口与工具条统一由 `src/main/resources/js/emoji-groups.js` 渲染；文章页/聊天室 FTL 里旧的 `#uploadEmoji` 尾栏大多已注释，恢复“本地上传”优先改这里，不要分别恢复多套模板。
- 表情集分享链路使用新表 `emoji_share` 保存“分组快照 + 永久分享码”；分享内容是静态快照，后续源分组变更不会实时同步，导入会在目标用户下新建自定义分组并同步补入其“全部”分组。
- 首页右栏专栏列表（classic/pc）需使用 `module-list long-column-module-list`（见 `skins/classic/pc/index.ftl` 的“最新专栏/热门专栏/最近阅读”）；否则会命中 `.module-list .title` 默认 `margin-left: 30px` 产生左侧空白。
- 勋章管理页：`/admin/medal`
- 个人主页勋章由 `settings.js` 的 `Settings.initHome` 动态调用公开只读接口 `GET /user/{userName}/medal`，再渲染到 PC/移动端 `home-side.ftl` 的 `#metal`；不要依赖可选游戏组件的 `window.onload`，也不要改回 FTL 服务端渲染。
  - 后端：`src/main/java/org/b3log/symphony/processor/MedalProcessor.java`（`showAdminMedal`、`register`）
  - 前端：`src/main/resources/js/medal.js`
- 模板：`src/main/resources/skins/classic/pc/admin/medal.ftl`、`src/main/resources/skins/classic/mobile/admin/medal.ftl`
- 管理 API：`/api/medal/admin/list`、`/api/medal/admin/search`、`/api/medal/admin/grant`、`/api/medal/admin/grant-batch`、`/api/medal/admin/revoke`、`/api/medal/admin/owners`、`/api/medal/admin/holds`
- 会员状态 API：`GET /api/membership/{userId}`（`MembershipProcessor#getUserMembershipStatus`）
- VIP 管理页：`/admin/vip`（`MembershipProcessor#showAdminVipManagePage`，classic/pc 与 classic/mobile 同路径模板 `admin/vip.ftl`）。
- VIP 管理 API（仅 `adminRole`）：`/api/admin/vip/list`、`/api/admin/vip/add`、`/api/admin/vip/update`、`/api/admin/vip/refund`、`/api/admin/vip/extend`。
- VIP 管理服务关键方法位于 `MembershipMgmtService`：免费新增（不扣积分）、手工维护、按剩余天数退款并失效。
- VIP 按天退款成功后会复用“系统转账通知”（`Notification.DATA_TYPE_C_POINT_TRANSFER`）给用户发送站内通知。
- 积分变更统一走 `PointtransferMgmtService`：独立流程用 `transfer(...)`（方法内自带事务）；若外层已有事务，必须用 `transferInCurrentTransaction(...)` 避免事务冲突。
- credit 应用积分链路：`POST /user/edit/points` 携带完整 `sourceAppId/sourceAppName/sourceScene/requestId` 时走 `ExternalPointAdjustMgmtService`，使用 `Pointtransfer` 类型 55、通知类型 42 与 `external_point_request` 保证幂等；账户方向仍为 `sys` 与用户，应用来源只作为可信快照和展示信息，内部请求编号不得通过积分记录接口公开。
- 发积分/扣积分约定：发积分用 `fromId=Pointtransfer.ID_C_SYS`、`toId=userId`；扣积分用 `fromId=userId`、`toId=Pointtransfer.ID_C_SYS`（常用类型 `Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT`）。
- 需要给用户发“系统转账通知”时：在转账成功拿到 `transferId` 后，调用 `NotificationMgmtService#addPointTransferNotification`，并设置 `Notification.NOTIFICATION_USER_ID` 与 `Notification.NOTIFICATION_DATA_ID=transferId`。
- 通知金手指：`POST /user/edit/notification`（`UserProcessor#sendSystemNotification`），使用 `gold.finger.notification` 校验；请求体使用 `userName` + `notification` + `goldFingerKey`，新正文优先写 `notification.content`（最大 4096），老库未补 `content` 列时仅兼容旧 64 字纯文本 `dataId` 链路。
- 定制系统通知渲染：`Notification.DATA_TYPE_C_CUSTOM_SYS` 在 `NotificationQueryService#getSysAnnounceNotifications` 处理；`dataId=1` 为固定“水贴提醒”模板，水贴检测新增通知需调用 `NotificationMgmtService#addSysAnnounceShuiTieNotification`，不要把 `"1"` 作为正文传给 `addSysAnnounceCustomNotification`；`content` 支持 Markdown 链接 `[文本](http/https://...)` 与换行，原始 HTML 会被转义后再清洗。
- VIP 管理页配置项不再手填 JSON：前端依据等级 `benefits` 模板自动生成可视化表单，再序列化为 `configJson` 提交。
- VIP 管理页样式需注意 `home.css` 的 `.form--admin label { flex: 1; }` 会影响布局；配置项行在 `vip-admin.scss` 中需显式改为整行（label/builder 100%）并对 checkbox 使用类型选择器，避免控件被放大。
- 有效期字段：勋章 `expireTime`（毫秒，`0`=永久）；会员 `expiresAt`（可回填勋章到期）。
- 首页最新文章链路：`IndexProcessor#loadIndexData` 通过 `ArticleQueryService#getIndexRecentArticles(fetchSize, page)` 组装 classic/pc 与 classic/mobile 首页“最新文章”；第一页置顶插入与数量行为在该方法维护。
- 首页模块个性化链路：classic PC/移动首页模板引入 `home-personalize.js`、`home-modules.js`、`repeater-station.js`，配置状态保存在浏览器 `localStorage`；重置布局要按初始 DOM 锚点恢复，不能简单 append 到父容器末尾；移动端首页参与配置的模块需作为同一个 `home-personalize-zone` 的直接子项；复读机接口在 `RepeaterProcessor`，数据表为 `repeater_content`/`repeater_like`。
- `/recent` 与 `/api/articles/recent*` 共用 `ArticleQueryService#getRecentArticles`；该链路已明确排除长篇（`articleType=6`），长篇列表请走 `/recent/long` 页面或 `GET /api/articles/recent/long`。
- 首页两列对齐约束：`getIndexRecentArticles` 的第一页会插入全部置顶且不截断；第二页起需按第一页“置顶占位数”补偿 `fetchSize` 与分页偏移，并基于已排除置顶 ID 的普通文章序列取数，保证两列等高、不重复、不丢中间文章。
- 首页右侧排行补偿（无前端延迟）：由 `IndexProcessor#loadIndexData` 按两列最新文章的最大行数计算 `rankCompensateRows`，先换算“右栏总补偿行数”再分摊到 `checkinVisibleCount/onlineVisibleCount`，Freemarker 直接按该数量渲染，不再依赖 JS 运行时增删行。
- 专栏封面链路：封面字段是 `long_article_column.columnCoverURL`；默认封面在 `LongArticleColumnQueryService` 填充；作者管理页为 `/column/manage`，编辑长篇页的弹窗脚本是 `long-article-cover-dialog.js`，新建长篇封面随 `/article` 请求的 `columnCoverURL` 写入；封面保存接口为 `POST /api/columns/{columnId}/cover`，处理器是 `LongArticleColumnProcessor`。
- 长文阅读页以 `articleType=6` 区分，PC/移动模板均为 `skins/classic/*/article.ftl`，样式在 `index.scss`/`mobile-base.scss`，交互在 `long-article.js`/`m-long-article.js`；模板通过 `hidePageChrome` 不渲染顶部与底部视觉内容，但 footer 的公共脚本仍需加载；桌面宽度和两端字号统一保存在 `localStorage.longArticleSettings`，宽度仅在 PC 生效；PC 评论按钮控制居中的“正文 + 评论”阅读舞台，移动端使用右侧抽屉，现有评论列表、筛选、分页和编辑器均复用；专栏目录直接复用阅读页数据模型中的 `longArticleChapters`，PC/移动均需同步目录模板、样式与工具栏脚本。
- 长文段评链路：`LongArticleParagraphService` 根据服务端清洗后的正文生成段落锚点并给 HTML 添加 `data-long-paragraph-*`；段评字段存于 `comment`（`commentType=1`），新增接口位于 `CommentProcessor` 的 `/comment/paragraph*`；评论编辑事务调用段落迁移，无法匹配的段评标记为失效但保留快照。PC/移动均加载 `long-article-paragraph.js`，段评列表复用现有评论线程与编辑器能力。
- 管理员文章页编辑长文专栏时，`admin/article.ftl` 会随文章表单提交 `columnCoverURL`，后端在 `ArticleMgmtService` 的文章事务内复用 `ColumnCoverMgmtService` 校验并更新或清空专栏封面。
- 路由总入口：`Router#requestMapping` + 各 Processor `register()`；新增路由先决定使用 `loginCheck` / `apiCheck` / `permission` / `anonymousViewCheck` 哪条链路。
- Latke 路由存在静态段被相邻动态段截获的风险（如 `/article/{id}/revisions/list` 可能进入 `/article/{id}/revisions/{revisionId}`）；新增相邻路由时需避免路径歧义，或在处理方法中显式识别保留字。
- `BeforeRequestHandler` 通过 `Dispatcher.startRequestHandler` 在路由中间件前执行；该阶段 `query/form/cookie` 已由 latke-core 解析，可直接读取请求参数与 Cookie。
- `BeforeRequestHandler` 获取当前用户优先用 `UserQueryService#getCurrentUser(request)`（底层 `Sessions.currentUser(request)`）；`Sessions.getUser()` 仅读取 ThreadLocal，未 `Sessions.setUser(...)` 前通常为 `null`。
- `LoginCheckMidware#handle`：未登录统一 401（特殊 URI `/gen` 返回空 SVG）；支持 `Sessions` 与 `apiKey` 两种登录态来源。
- 新接口若需“页面登录态 + apiKey 调用”双兼容，路由层优先挂 `loginCheck::handle`，处理方法再读取 `context.attr(User.USER)`；避免只挂 `permission` 导致拿不到当前用户对象。
- OAuth/OpenID 用户资源链路在 `OpenIdProcessor`：`/openid/login` 通过 `fishpi.scope` 请求 `profile.read/profile.detail.read/points.read/articles.read/membership.read`，`/openid/verify` 成功后返回 Bearer `access_token` 与可轮换 `refresh_token`；资源接口只认 `Authorization: Bearer`，续签走匿名 `POST /openid/token`，不接收 `apiKey`、不支持 `userId` 代查。
- 文章页实时链路走 `ArticleChannel`：新增评论仍是 `type=comment`，评论 reaction 增量走 `type=commentReaction`，帖子本体 reaction 增量走 `type=articleReaction`；联调这类 Java 推送改动时，前端强刷还不够，必须让用户重启或重新编译服务端。
- 账号设置手机/邮箱验证码链路：PC/移动模板在 `skins/classic/*/home/settings/account.ftl`，前端在 `settings.js`，发送接口在 `SettingsProcessor#sendPhoneVC/sendEmailVC`；验证码形态调整需同步前端提交字段与后端校验方式。
- `AnonymousViewCheckMidware#handle`：匿名访问触发验证码（2 小时首次访问 + 每 5 次访问），并结合 `anonymous.viewSkips`、文章匿名开关、匿名访问次数 Cookie 限制。
- `Server` 启动逻辑：`DEVELOPMENT` 模式会关闭 `Firewall` 与 `AnonymousViewCheck`（验证码盾），联调时不要误判“线上无校验”。
- 历史遗留：部分接口未在路由层挂登录中间件而在方法内鉴权（如 `MedalProcessor#requireAdmin/requireLogin`、`UserProcessor` 的 goldFingerKey 系列）；新增接口不要复用该模式，优先路由层显式鉴权。
- 管理员永久停用用户走 `POST /admin/user/{userId}/deactivate`：仅 `adminRole`、要求 CSRF、禁止停用本人；`UserMgmtService#deactivateUser` 在同一事务内匿名化账号并把 `userPhone` 清为 `_`，状态 4 不允许通过普通用户编辑恢复。
- 用户分组 OID 常量：`adminRole` 为管理员，`1630552921050` 为 OP，`1630553268292` 为超级会员，`16305533360689` 为成员，`1630631382235` 为纪律委员，`defaultRole` 为 Default，`visitorRole` 为 Visitor。涉及分组权限判断时优先复用这些常量，不新增数据库权限记录；免审发帖绿色通道使用管理员、OP、超级会员、纪律委员四个分组。

- v3 OAuth框架表单解析可能先记录含凭据的异常；log4j2.xml的Request/ServerHandler专用OAuthTransportFilter必须随服务重启加载，注册路由前requireInstalled检查。原始表单仅接收ASCII序列化的UTF-8百分号编码；维护任务保留仍有有效Token家族的已消费码供重放撤销。token/revoke当前仍经过匿名盾，凭据作为该端点登录证明的策略尚待用户确认，不能擅自加入viewSkips。

- 密码重置新旧入口共用 `AccountPasswordResetService`：先锁client集合再当前锁账号及绑定短信，密码/验证码消费/v3 Token撤销/待决授权清理严格同事务，结束后失效用户缓存。旧入口显式userId且提交后才登录；v3只认手机号+短信证明，不继承Cookie/apiKey身份。共享 `PasswordResetQuota` 复用旧限额。旧reset也依赖v3/OAuth迁移，部署须先迁移并完整重启加载凭据日志过滤器；不得把已消费短信明文存入幂等回执。

- 注册完成新旧入口共用 `AccountRegistrationService`，只激活当前待验证手机号账号，禁止套自提交addUser。完整sys→actor/referrer/邀请码生成者账户对先取锁，严格根内按namespace→注册开关→按ID当前锁用户→邀请码→绑定注册短信检查；状态/密码/角色/MBTI、初始及邀请奖励、邀请码和通知记录同事务。重验预投影关系，变化409，不扩展已持账户对。只更新需要列，保留已有余额，待验证重名账号按旧策略清名称/邮箱。缓存与AccountRegistrationEffectsService（索引/提醒/名字表/天降红包）提交后执行，后台释放JDBC；重放已用短信不能再次奖励。RegistrationVerificationQuota与旧verify/showRegister/register2共用原限额；旧POST register2/reset-pwd统一匿名盾已获用户同意并接入，先匿名校验再旧业务中间件，禁止移回裸路由。

- v3找回短信走AccountPasswordResetRequestService：有界极验四字段证明、原共享AccountSmsQuota，查询当前手机号/状态，服务商受理后才在严格根锁用户重验并保存RESET_PWD码；远程发送期间无事务，不删除旧有效码，也不能保证物理短信与SQL原子。缺失/失效/歧义账号统一accepted，发送后变更账号不绑定新码。AccountCaptchaService仅公开jiyan.id，不得输出key/domain配置。短信发送四个原预算在注册/找回/绑手机与v3间共用同步包装；已确认新账号专用发送方法核对腾讯单个+86接收人和Ok，旧方法正常返回契约保留，生产错误不记录手机号/验证码/原始响应。

- 待验证账号核心在AccountPendingRegistrationService.prepareInCurrentTransaction，仅供已有严格根及ArticlePublicationCaches调用：namespace→当前开关→排序/有界用户锁，复用pending手机号，按原自然月等待期释放注销手机号，并将新用户/编号计数同保存点写入；异常可局部恢复，缓存按actor+全部名称别名在根结束后失效。UserRegistrationDefaults只共用旧默认字段，不承担HTTP授权；新核心自行构造默认角色/未验证/空密码输入。注册短信调用方须校验极验/邀请码、同事务证明与预留；新证明的名称/邀请绑定已接RegistrationProofContext，旧空上下文证明保持兼容。

- v3私信读取在ApiV3PrivateMessageProcessor/ApiV3PrivateMessageQueryService，独立messages.read，仅Bearer、当前数据库账号及实际fromId/toId归属，不信任user_session；读取不标已读。存量chat_unread无消息关联，只能提供未读记录数。私信正文走Markdowns.toPrivateHTML本地渲染，不得套公共toHTML远程Lute/公共缓存或记录原文异常。索引/默认website权限迁移先执行20261019_api_v3_private_messages.sql；旧token刷新不增加scope，重新登录/授权。发送、指定/全部已读及本人撤回已接入messages.write，专用实时走messages.read取票及messages频道。
- v3注册短信由AccountRegistrationRequestService经ApiV3AccountProcessor接入：三路径均极验，共享原SMS额度，显式referral须当前已激活且非本人；免邀请码另查角色邀请权限。远程受理期间无事务，受理后严格根按namespace→当前开关/邀请人及权限→pending→邀请码→绑定证明提交；不能声称物理短信与SQL原子。邀请码排他预留一天，同手机重发续期、其他手机409；旧/register已获用户批准共用reserveLegacy及anonymous门禁，仍保留旧短信流程/极验表单规则/奖励对象规则，整个旧申请并非原子。定时expireInvitecodes已委托严格根跳过有效预留并释放线程资源。
- RegistrationProofContext绑定申请名称、邀请人名称+ID及邀请码ID；部署须先迁移20261019_account_registration.sql的verifycode.accountContext并同步repository.json。激活用consumeRegistrationCode，空context旧码兼容；同6位值存在当前匹配证明即可授权，并同事务消费全部同owner/用途活码。PC/移动共用verify.js，注册/完成/重置收到非JSON或网络错误应保留表单并提供同站手动验证链接，禁止自动重发短信。

- v3私信写入走ApiV3PrivateMessagePublicationService/PrivateMessagePublicationService：messages.write及幂等，当前用户按ID排序锁，须激活；接收者仅当前激活的其他用户或固定文件助手（用户已确认）。原保留词→trim→10240 UTF-16校验，消息/未读/list同保存点与严格根，回执只ID/对方ID/时间；正文不进回执或日志，不转发旧完整WS正文。旧ChatListService.update与v3共用PrivateMessageListStateService，按当前消息重算head、排序锁用户后锁list，避免延迟事件倒退；旧整个发送仍非原子。先迁移20261020_api_v3_private_message_writes.sql的TEXT正文/列表索引及精确默认scope，repository.json长度同步。
- 私信已读由PrivateMessageReadStateService只按当前toId/fromId清理合法incoming记录，忽略user_session；按固定上界ID清理而非承诺提交序快照，高于上界的新记录保留。幂等回执重放不清后来未读，缓存/本人旧未读数刷新只在严格提交后。存量未读的messageId为空，不得声称逐条已读或撤回时能精确扣减存量旧记录。

- 私信未读关联列由20261020_api_v3_private_withdrawal.sql增加，必须在部署新旧发送代码前迁移；历史保持空，不能猜测回填。v3及ChatChannel两处/UserProcessor一处后续发送均写实际chatInfo ID，旧has-unread输出前移除内部messageId，v3仍白名单。列表空消息对可在调用方事务中删除投影；用户已确认撤回只清准确关联的未读；DELETE路由已接入，旧撤回同步原子化已获用户批准并接入。私信幂等回放可重复做缓存失效/绝对未读数刷新，以修复成功提交但确认丢失；不能重复发送消息或把这类刷新当可靠实时事件。

- v3私信撤回在PrivateMessageWithdrawalService/ApiV3PrivateMessageWithdrawalService：本人消息投影取得完整用户锁集合，排序当前锁用户后再当前锁消息、重验peer；消息/准确messageId+fromId+toId未读/list同保存点，幂等严格根。未知历史未读保留（用户已确认），回执只id/peerId/removed，不泄露接收者已读状态。接收者已删除/停用仍可撤回本人历史；提交/回放后可重复失效缓存，不转发旧完整WS。旧GET/chat/revoke已获用户批准接入removeLegacy严格根，身份取ApiCheck写入attr，推送/审计/计数刷新在提交后且失败不覆盖成功；旧GET无幂等回执。

- 私信实时前置标记在PrivateMessageRevisionService/ApiV3PrivateRevisionRepository：新表api_v3_private_revision及repository.json，先迁移20261020_api_v3_private_realtime.sql；当前v3发/读/撤及旧原子撤回同事务标记受影响owner，读取标记必须在写事务外。保存点须覆盖数据与标记；读标记失败也不能让已读删除被外层捕获后部分提交。标记是opaque invalidation，不是历史游标；旧普通/手动/认证申请私信的消息和未读add已接Repository标记，旧指定/全部已读用owner-aware删除，异步列表refreshLegacy提交时再标记；旧发送各步骤仍独立事务，不能称整体原子。Socket私信频道已接ApiV3RealtimeService.pollMessages，只读已提交owner标记并逐连接复核后发messages.changed空提示；POST /api/v3/me/messages/realtime-ticket需messages.read，target必须为空。通知/文章/chat索引与scope隔离，首次/重连ready后须HTTP同步；提示可合并丢失，不是逐条消息/已读回执或可靠游标。

- v3风险红包沿`ApiV3ChatRedPacketService`同根结算；先迁移20261024红包risk SQL（type17/gesture/refund列）。创建及领取均需riskAccepted=true，心跳signed slots总和为pool；猜拳负款用正数反向转账，refund独立ledger，同一事务/完整账户对锁。senderGesture未结算仅本人详情，不能放初始card；未领完删除409。旧桥接不能代替风险同意，历史旧奖池仍走旧核心。

- v3部署有序迁移与证据见`docs/api-v3-migration-chain-work.md`：15份SQL/19张新表已隔离MySQL核验；部分ALTER仅一次，不能盲目整链重跑。progress列明真正缺口，不能用路由数量或历史迁移矩阵推定用户侧全部完成。

- website_v3实际客户端在/Users/ling/Documents/fishpi/website_v3，另读其AGENTS；迁移状态见该仓docs/V3_MIGRATION。不能按后端仓库不含客户端文件判源码不可用。主文章GET新增qna/perfect/month-comments；good页面映射既有hot热度，hot页面映射月内评论，不得用普通列表伪装分类。

- v3作者付费置顶由ApiV3ArticleStickService处理：账户对→当前用户/回执→本人文章，原19类费用/时间/回执同严格根；重放不覆盖后来状态。expireStick必须经Repository当前锁时间比对，不能用扫描快照清新续费。recent查询有效置顶优先/永久最先；公共stick白名单不返回Long.MAX_VALUE哨兵，永久expiresAt=null。

- v3音乐/天气由`ApiV3ChatProcessor`→`ApiV3ChatPublicationService.publishCard`共用普通准入/原子消息链；新cardVersion=3/payload仅内部存储，DTO重验后白名单投影，URL和文字不经历史HTML清洗。两套chat-room脚本优先解析raw md、编码卡片/播放列表；音乐source仅数字进网易云iframe，HTTPS音频进原生audio，禁止再拼接source到HTML。

- v3企业认证`ApiV3IdentityApplicationService`只第一方当前SQL授权，独立私有材料表；迁移20261024_api_v3_identity_application.sql精确绑定adlered当前ID，改名不转权。创建/唯一pending/材料/回执同根；POST open先删材料及记readAt并提交，再送原始图片，网络丢ACK也410，禁止保存在回执/日志/图床。当前6路；PUT decision必须readAt>0，仅approved/rejected和256纯文本说明，结论/回执/释放pending同根，不自动发勋章或改权限；重放不影响新申请。

- website_v3表情客户端接线与验收见docs/api-v3-client-emojis-work.md；共享Picker可插入不代表旧编辑器hint/上传/发送已迁移，前端私有成员id与全局emojiId不得混用。

- 官方Flutter APP已克隆 `/Users/ling/Documents/fishpi/fishpi-app`（FishPiOffical/fishpi-app）。用户最新要求不改APP代码，仅完成v3服务端支持；APP仓库已还原干净、只读参考。已确认网页生码/APP扫码、2分钟、APP明确确认后网页再批准、一次消费与拒绝作废；ApiV3AppLoginProcessor/Service已接七POST，先迁移sql/20261025_api_v3_app_login.sql；发码源website access/epoch逐次当前重验，APP challenge绑定/双确认/令牌对与消费同严格根，二维码只opaque payload。本地生成图形由客户端接入，不可调用第三方QR服务；原生匿名入口保留AnonymousViewCheck，无viewSkips例外。固定fishpi_app_v3仅原生Bearer，不借website Cookie刷新；管理员变更/停用清未完成请求。扫码实际Java/MySQL runner为tools/api-v3-app-login-mysql-test.mjs，保留FOR UPDATE并执行生产Token发行/撤权，勿用SQL sink竞争替代服务并发；完整IoC/部署仍另验。原fishpi Dart SDK仍是旧apiKey，禁止把v3 Bearer塞进旧token/Fishpi(token)冒充登录；事务/本地QR/APP凭据及页面迁移见app-login-work。

- website_v3清风主列表/首页/侧栏共用BreezemoonPanel与v3Breezemoons命令核心，未知结果原key跨组件恢复，完成通知原编辑框；SSR公共载荷由每app的SSR_PAYLOAD_KEY注入，禁止再用进程global供并发渲染消费。个人清风尚独立旧读，完整验收与未验项见client-breezemoons-work。

- 单项标签关注/收藏/订阅GET在ApiV3FollowQueryService，content.read、当前有效本人+有效标签/共享文章可见性，只返targetId/following；前端ContentRelationButton按kind+ID恢复原明确命令，确认后必须GET。多组件提交须废弃在途旧状态读取再重读，不能因loading跳过完成通知；UserFollowButton同规则。

- website_v3 首次路由须保留游客 SSR 首帧，由 clientStartup 在 mount 后恢复；AppShell 私有子页须当前 auth.isLoggedIn，不能只凭 startup.ready 放行。后续 SPA 保留认证守卫，初始恢复不得覆盖后来导航。

- website_v3文章三类互动共ArticleInteraction/v3ArticleInteractions，未知按kind+ID保原命令key，确认后GET而非receipt计数；付费正文仅当前详情rewarded=true的rewardContentHtml，离页/换号废弃迟到读取并取消本人费用确认。

- website_v3评论三写共CommentComposer/v3Comments，原Markdown不trim、编辑默认省visible；未知固定article/id/input/key，完成只重读且不得覆盖后来回复目标。长篇须paragraphs includeContent取锚点HTML再发段评，平铺分页不伪造根层次；匿名本人不能用用户名推断编辑归属。

- website_v3的ArticleInteraction同时承载articles与comments/vote；API、命令键、完成通知和读取身份必须含资源，不能让同ID文章/评论互相恢复或刷新。异步按钮保留节点，重复操作由aria状态及处理函数共同阻止。

- 评论感谢/采纳当前状态由ApiV3CommentRewardQueryService只读投影，私密按正文可读检查，匿名费用0；canAccept是问答页基本资格提示，写核心仍重验。website_v3的ArticleInteraction按comments资源承载vote/thanks/accept；采纳完成须刷新所有挂载候选，不能只重读被采纳项。

- website_v3表态共V3ReactionBar/v3Reactions，目录与当前GET为准，命令按article/comment/chat+ID隔离；旧Socket只失效提示，不采actorReaction/summary。认证恢复前不请求，游客显式读且事件不扇出；chat游客无读路径，未知原choice/key保留，目录迟到也须身份隔离。

- website_v3历史共RevisionHistory/v3-revisions：按当前授权惰性读，current仅列表已有、评论time=null不造时间；content是清洗文本而非原Markdown，禁止回旧preview或用于编辑。ArticleView用认证版本/owner稳定身份，不以正常轮换token字符串重建页面。

- website_v3文章/草稿主链PostView/v3Posts/v3-posts：PATCH只变更、本人source原文，metadata不回填；发布draftId交服务端消费勿追加DELETE。未知op/id/input/key保留，导航/余额失败不重付；MarkdownEditor预览/上传/@仍独立旧链，主链接线不是编辑器全迁移。

- v3编辑器预览POST由ApiV3MarkdownProcessor提供，content.read、原始字节严格JSON，走Markdowns.toPrivateHTML而非公共缓存/远程转换。客户端Vditor同步transform必须返回空以阻断本地fallback，再由parse异步写清洗HTML；正文/身份/卸载清输出并废弃迟到结果。预览预算独立于发布，勿接共享写限额。

- website_v3共享编辑器上传由v3-uploads/editorUpload接既有票据，图床只专用票据/无Cookie/禁重定向，唯一multipart名按序对应部分结果；换身份/外部正文/卸载取消旧插入。Vditor的insertValue先解析HTML，Markdown一律用insertMD，普通目的地转义括号。其他旧上传调用方尚未整体迁移。

- website_v3工具栏表情复用v3 EmojiPicker；本地Lute Unicode仅显示，插入仍服务端别名，不拼本人内容进Vditor原生hint HTML。普通token轮换保留，auth context/owner变化清除。@联想已接新suggestions/users，见user-suggestions-work。

- v3用户联想在ApiV3UserSuggestionQueryService/Processor，profile.read、ASCII用户名prefix1..64/LOWER参数查询/字面下划线/有效账号/四字段最多10，空前缀空列表，不走旧缓存。Editor捕获原生input取消旧hint，勿在Vditor延迟input回调清当前候选；旧promise须先结算防迟到覆盖。当前CDN UI仍8项/31位，接口独立10/64；勿给toolbar.elements.emoji加别名，会隐藏自定义按钮。

- website_v3长文归栏用AuthorColumnPicker+v3-author-columns分页本人目录；失败/翻页不清绑定，未在本页不伪造有效性，只明确选择发id/title，章节/封面沿source不自动改。PostView部分编辑规范化现有columnTitle，草稿保留对应标题。标签联想已实现，见tag-suggestions-work。

- v3标签联想在ApiV3TagSuggestionQueryService/Processor：content.read、trim/现有Tag字符表/64、有效标签与正引用数并EXISTS当前文章关联，热门/前缀最多7，只返回名称。TagInput仅替换当前光标token，不重写其他输入；选择需当前快照，IME组合期不发请求。标签联想不改文章提交规范化规则。

- 客户端本人文章操作ArticleOwnerActions/v3ArticleOwner惰性source仅留id/status，匿名不猜ID；未知kind/id/key跨SPA、退出清空，非持久队列。置顶回执非当前stick、成功后GET失败只重读；删除清正文且导航失败不重发。普通/长文共享入口，私有source不得回显。

- ArticleView文章实时统一subscribeArticleChanges，精确票据/ready/articleId，首帧传票，article.changed仅提示与失效，HTTP显式重读；新事件revision不可被旧读清除。热度用v3 GET显示本节点近似连接，失败不造0，游客不查；普通/全屏长文共用live-status，旧article-channel正文禁止接回。

- 独立/chat页由V3PrivateMessages/v3Messages管理，用户ID绑定、历史before游标，读取不自动已读；未知原kind/target/body/key跨SPA，成功当前GET而非回执拼正文。普通token轮换保草稿，watch多目标须稳定序列化身份，不能新数组触发重置；messages.changed仅失效。独立页/抽屉共面板，privateInbox单订阅/当前未读及入口不存正文；查询合并、失败未知非0。旧whispers已删，聊天室仍独立待迁移。

- 聊天室原文/引用由useChatMessageSource→v3-chat-messages当前详情；只传id，不采旧列表HTML/作者。卡片仅公开摘要、unavailable清正文，普通Markdown/HTML分列，作者转义、多行逐行引用；取消/身份隔离与独立rawOpen。普通发送已接v3ChatPublication，主历史/实时已接v3ChatTimeline。

- ChatStyle/appearance首帧统一SSR默认，AppShell挂载后restoreStyle/restore；不要在store初始化读本地布局/主题改变服务端首帧结构或选中类，避免现代布局/浅色偏好水合不一致。

- 普通聊天室send→v3ChatPublication，原content/client/key跨SPA；confirmation_required明确确认原键，成功只metadata后当前GET，读取失败不重发。Chatroom/抽屉输入匹配才清，当前详情投影去重。旧特殊按钮走独立legacy临时链，禁止普通正文隐式回退旧语法执行器；消息历史/实时已接v3ChatTimeline；在线等剩余链另迁移。

- 聊天室消息统一v3ChatTimeline游标/首帧chat事件；WS仅ID/revision，当前GET合并，旧响应/旧页不覆盖更新或复活删除，401/403清授权并拒迟到投影。ready/sync重读窗口，清屏不自动重灌；Home/SSR不取旧chat正文，节点密钥链已从消息消费者移除。在线/mutes/特殊写等仍单独迁移。

- website_v3在线名单用v3ChatOnline/ChatOnlineList共享分页/total/采样；错误未知与0区分，本页筛选，不以本页长度当总人数，不宣称全站实时。SSR不预载旧名单；真实隐私/多节点仍需运行验收。

- website_v3撤回用v3ChatRevoke/ChatRevokeAction/Recovery；作者ID认领，确认后DELETE，未知原ID/key跨SPA、恢复4xx不清命令，换身份清理；成功经timeline.removeConfirmed保留删除集合阻止迟到历史/详情/发送复活，不能回接旧撤回wrapper。

- website_v3消息上下文用useChatContext/ChatContextPanel与/cr#chatroom{id}；窗口独立于最新timeline，前后翻页替换，失败原方向重试，权限/404清正文。timeline.changeHint仅元数据，使相关/在途成员失效；初次登录恢复不得抹掉hash，返回最新清hash并重读主窗。

- website_v3话题修改用v3ChatTopic/ChatTopicEditor/Recovery；原text/key跨SPA未知恢复，confirmation_required明确确认，当前值只GET不以回执覆盖，GET失败不再付费。timeline.readTopic可主动无订阅读取且保留身份/代数隔离；旧setDiscuss包装已删。

- v3客户端弹幕使用v3ChatBarrage/v3ChatBarragePrice，原content/color/key恢复且回执不补播；认证chat.barrager白名单→timeline独立ref→AppShell唯一ChatBarrageOverlay，纯文本/有界去重/定时清理，不进历史或上下文。详见docs/api-v3-client-chat-barrage-work.md。

- v3客户端音乐发送在v3ChatMusic：冻结四字段/key，成功仅当前详情GET后入timeline，回执不充正文；ChatCard数字ID走网易云链接，显式媒体播放有乱序和错误处理。见docs/api-v3-client-chat-music-work.md。

- v3客户端天气发送在v3ChatWeather，固定五天客户端数据，UTC完整年份日历/20代码/整数温度白名单；表单不默认伪造预报，成功仅当前GET后入timeline，原city/description/days/key恢复。见docs/api-v3-client-chat-weather-work.md。

- website_v3红包由v3ChatRedPackets统一创建/详情/领取及原键恢复；卡片只GET，风险领取须显式确认，猜拳手势独立选择。metadata不作当前正文，旧历史404不回退旧领取器；见docs/api-v3-client-chat-red-packets-work.md。

- v3思过崖GET /api/v3/chat/mutes要求content.read，按用户ID读实际未到期sys-mute及有效账号，不读si:guo用户名快照；五公开字段/分页，坏状态或预算超限503。客户端v3ChatMutes按需共享读取，失败人数未知，见docs/api-v3-chat-mutes-work.md。

- website_v3涂鸦PaintPanel共用v3-uploads，具名PNG只插草稿不自动发；上传冻结绘制，失败保画布，关闭/身份变化拒迟到结果，同账号轮换保留。旧uploadFiles已移除，见docs/api-v3-client-chat-paint-work.md。

- website_v3举报统一ReportDialog/v3Reports，目录GET无本地兜底，四目标理由分列/聊天室仅49；原type/id/reason/memo/key跨SPA恢复，AppShell唯一ReportRecovery只显编号不显备注。旧reportContent已删，见docs/api-v3-client-reports-work.md。

- website_v3复读机由v3-repeater/useRepeaterFeed/v3Repeater接列表、服务端next及原键写恢复；榜单仅前12，写回执仅编号，成功GET当前榜单，随机项可离榜。Home/SSR明确joke，纯文本不stripHTML；见docs/api-v3-client-repeater-work.md。

- website_v3鱼游在v3-fish-games/useFishGames/v3FishGames：默认votes分页、详情/评论独立读、明确三态投票，五字段投稿/编辑含OAuth必填；回执不写当前公开内容，未知原键跨SPA恢复。官方游戏只导航经典站点，见docs/api-v3-client-fish-games-work.md。

- website_v3同城用v3-cities/useCityFeed双资源真分页，访客沿现有optional scope，独立错误/原页重试；Vue Router城市参数已解码，禁止再decodeURIComponent，API仅编码一次。移除近50条清风过滤，见docs/api-v3-client-cities-work.md。

- website_v3大事记由v3-milestones/useMilestones/v3Milestones提供公开SSR时间线及本人状态；本人content是历史HTML或原文，必须经现有preview清洗后展示，失败禁止原文回退。投稿回执pending不代替当前GET审核状态，原键跨SPA恢复；见docs/api-v3-client-milestones-work.md。

- website_v3统计/statistic用v3-statistics/useStatistics仅投影九项非负安全整数，错误清快照不填零；公开SSR按请求提供并一次消费，当前登录/凭据轮换重新读，拒迟到。仅统计快照，不宣称全站去重在线；见docs/api-v3-client-statistics-work.md。

- website_v3账号页OwnerAppLogin只接既有网页扫码三POST，qrcode本地矩阵绘SVG；旧apiKey/外部QR包装已删。仅APP确认后明确批准，未知写先查状态，consumed才完成；到期/离页/身份边界清码，同账号轮换重验原issuer，不自动升级。APP源码不改；见docs/api-v3-client-app-login-work.md。

- website_v3普通榜单共用v3-rankings/useRankingBoard/RankingBoard，十五类及游戏子类均有限快照分页；首页独立size8，不进旧Home缓存或mock兜底。HTTP嵌套pagination与SSR投影平页分别校验，初始只首挂复用；职业仍独立。见docs/api-v3-client-rankings-work.md。

- website_v3游戏存档在OwnerGameSaves/v3GameSaves：仅evolve-save，原文/版本/键冻结跨SPA恢复，GET核验UTF8/hash；读取不覆盖草稿，历史metadata不回写当前版，409须重读后明确决定。正文只入textarea，全局不显原文；见docs/api-v3-client-game-saves-work.md。

- v3昨日奖励可选expectedDate进幂等请求，锁本人后服务器日期不符拒fresh，已提交旧键先回放；旧{}保持。CheckinPanel/useActivityStatus读真实状态与门槛，v3Activity原date/key跨SPA恢复，日期变化仅在回执未命中时释放原命令；不把旧回执当当前可领状态。见docs/api-v3-client-activity-work.md。

- website_v3用户悬浮卡用v3-public-user六字段白名单，重开/轮换读当前资料，不补旧角色/在线/积分。已知公开ID传UserFollowButton.userId绑定关系目标，旧仅用户名调用保持；主页/徽章页旧消费者另迁移。见docs/api-v3-client-user-card-work.md。

- v3公开勋章页使用既有用户名分页GET与usePublicMedals身份边界；历史重复授予不按定义ID去重，公开契约无图片URL，不回退旧完整用户或生成图片接口。MemberView已独立迁移公开六字段和当前栏目v3列表。

- 用户主页useMemberPage将公开资料与当前栏目独立读取；SSR仅fetchPublicUser白名单并消费时重投影，不缓存旧完整用户。公开文章/回帖需署名作者匹配，清风深链接直接GET并复核归属，禁止从最近列表推断删除。

- 注册/找回三页共用AccountAccessForm和v3-account-access；注册SMS快照绑定邀请信息，完成无幂等回执，未知结果先登录核实而非自动重发。手机号/验证码不放URL或storage；useGeetest4新增可选服务端captchaId，旧默认调用保持。构建必须单独核验退出码，不能让后续命令成功掩盖失败。

- 公开专栏四GET由ApiV3PublicColumnQueryService按当前可见署名长篇SQL投影，先过滤再计数/分页，作者绑定所有者及0/3状态；卡片最近章/数量/时间不能回用旧存储值。客户端usePublicColumn独立资料/分页及身份边界，原章号保留，空/失败封面用CSS占位；旧经典读取未同步改造。

- v3发现三GET由ApiV3DiscoveryExtrasQueryService只读SQL：recent-members仅status0公开四字段；random静态注册先于articleId，当前0/3作者/可列出/游客可读与匿名DTO；日志BINARY固定类别先过滤计数，只选oId/key1/key3，禁止回退旧data/key2。客户端最近用户SSR独立消费、不入Home旧缓存，三资源失败清旧/原页重试。

- v3勋章图片在ApiV3MedalImageQueryService/Service及ApiV3MedalSvg：profile.read、指定本人/公开owner、当前有效grant/定义前后核对，固定renderer无重定向、SVG白名单后仅图片响应。Latke路由前解码会拆开编码斜杠，图片medalId须用查询参数、保留原128单位键且不重复解码；客户端V3MedalImage只用Bearer Blob img/释放与身份隔离，不再建/gen Cookie。复杂CSS/外部资源不保留，实际renderer需联调。

- website_v3列表由v3-article-list/useArticleListing按真实分页读取：文章20/专栏12，专栏只取当前选择目录，不从文章猜作品或作者。SSR articleListing按kind/extra/order绑定且白名单一次消费；身份/目标变更清旧、失败保原页重试，不用SWR回退。Home旧feed/聚合缓存未在此批整体改造。

- Home五资源读取在`v3-home-reads`/`useHomeRead`，SSR失败null与合法[]分开，每应用消费/白名单，不能恢复聚合SWR或文章伪造专栏；复读机用ready区分未知。聊天室草稿身份边界用context/是否登录/actor，普通Token轮换须保留。

- 领域/标签目录页共用`v3-directory`/`useDirectory`，固定50项真实分页、SSR kind/第一页绑定和响应白名单；条目链接使用数字ID，不二次解码名称。不能恢复目录整库聚合SWR或按条数猜下一页。

- ArticleView正文/评论不进通用SWR；所有来源当前401/403/404清内容与引用，确认写入后503仅GET重试。普通Token轮换重读须保留挂载评论草稿/历史，卸载与页号迟到检查不可移除；v3Article须核对详情ID/逐条comment.articleId/页与size20并拒重复，参数先验后发请求；评论后页缩减为0页仍须保留返回第一页入口。

- website_v3旧page-auth及loginWebInApiKey代理已获用户批准退役；Node/Vite/nginx模板同步，经典Rhythm入口未删。fishpi.ts仅六当前helper及共享类型，禁止恢复无调用旧包装或存储旧凭据的桥接。

- 客户端边界复核用website_v3的`tools/v3-client-boundary-audit.mjs`，仅AST字面量/静态导入，动态表达式单列，不当运行覆盖。DomainItem在content-types，catalog/gaps假数据已删；离线ping只用v3，任意HTTP响应仅代表可达。

- Rhythm注册/文档核对用`tools/api-v3-contract-audit.mjs`，从Router.requestMapping追本类辅助注册；条件/重载歧义需复核。通过仅证明固定源码形式与API.md一致，不当作IoC/HTTP或业务完成。

- 新风险红包经典领取已获确认：durable_v3走显式riskAccepted/整数出拳，viewOnly不结算，未知恢复原拳/expectedUserId；JSON媒体限制只覆盖新证明。猜拳发送者停用仍按原账号返还，删除409无入账，不恢复其资格。旧历史保持。

- 经典新红包完成状态由两套channel同步到ChatRoom共享元数据，旧事件/快照不得回退；不匹配目标携带新核心参数必须拒绝，不能进入旧领取路径。

- 新风险确认弹窗布局在共享_red-packet-risk-dialog.scss，base/mobile-base均引入；仅dialog-panel--packet-risk覆盖Util.alert内联坐标，禁止恢复500px窄屏最小宽或直接改生成CSS。
