# v3 思过崖实际禁言查询（2026-10-27，第八十三轮）

## 已确认范围和实现

新增 `GET /api/v3/chat/mutes`，content.read，p默认1、size默认20最大50。按用户ID读取cloud内实际sys-mute，INNER JOIN当前有效账号；排除全体禁言、sys-risk、旧si:guo/record用户名展示快照。单条只读SQL取得当前状态与公开资料，使用一次时钟快照筛未到期，按expiresAt及ID字典序升序后计数分页。重复、非法或溢出禁言状态、数据库失败、超只读预算均503，不拼凑部分名单或返回假空。读取有技术预算，不承诺无限量完整扫描；过量时明确错误。没有数据库写入、清理或新增表/迁移。

每项仅id、当前userName、userNickname、userAvatarURL、expiresAt；不返禁言原因、操作者、手机号、IP、风控或其他用户字段。昵称按文本，头像共享安全URL投影。新接口与旧si-guo-list独立，旧接口行为保持；客户端删除旧展示名单链。

## 本轮实际文件

后端 `/Users/ling/Documents/GitHub/rhythm`：

- `src/main/java/org/b3log/symphony/processor/ApiV3ChatMuteProcessor.java`：独立只读路由、Bearer/content.read、严格原查询参数/UTF8/重复/未知项/页码校验、并发与请求预算，统一no-store错误信封。
- `src/main/java/org/b3log/symphony/service/ApiV3ChatMuteQueryService.java`：真实ID/状态/期限查询、完整公开投影、稳定排序分页、异常显式503。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`：总register接通新Processor。
- `src/test/java/org/b3log/symphony/service/ApiV3ChatMutesTest.java`、`tools/api-v3-chat-mutes-test.mjs`：Java25实际服务/路由与SQLite及临时MySQL数据专项。
- `API.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`docs/api-route-inventory.md`、`AGENTS.md`、本文。

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-chat-mutes.ts`：精确GET/固定20分页、五字段投影/安全头像、计数/顺序/重复/时间戳校验。
- `src/stores/v3ChatMutes.ts`：按需读取、共享使用者计数、翻页/刷新/原页重试；错误清名单及total为未知，区别真实0。同账号token轮换刷新当前页，换人/退出清空并中止迟到请求；SSR/访客零请求，离开最后使用者清理，聚焦刷新。
- `src/chat/sidebar/modules/MutesModule.vue`：双布局共用模块，头像/昵称/当前用户名链接及解除时间、分页/错误/重试；明确读取时快照，状态可能改变，不声称实时名单。SCSS源码，无卡片套卡片。
- `src/stores/chat.ts`、`src/api/fishpi.ts`：移除旧fetchMutes及无错误提示的connect附带请求；禁言名单由实际挂载模块读取，不影响聊天室主连接。
- `tools/v3-chat-mutes-test.mjs`：真实生产API/Pinia/Vue模块及SSR，HTTP/轻量DOM适配。
- `AGENTS.md`、`docs/V3_MIGRATION.md`、本文及build产物。

## 执行证据与限制

后端隔离Java25实际Latke路由/Bearer/content.read/严格查询和当前服务SQL，SQLite5组及临时MySQL5组均通过；MySQL容器已删除，非用户应用数据库。覆盖有效/停用/未激活/到期边界/改名ID/全体与风控排除、20/50分页及越页、公开投影/非法头像、重复/损坏/溢出/预算/数据库失败、未鉴权/错误权限/撤销模拟凭据/旧Cookie与apiKey不替代Bearer、no-store、请求及并发保护释放。日志 `/tmp/rhythm-v3-chat-mutes-backend.log`、`/tmp/rhythm-v3-chat-mutes-mysql.log`。JVM/SQLite/Netty产生已有native/Unsafe弃用警告，测试退出0；令牌发行、全局Before链、真实IoC并未执行。

客户端新增11组及相关225组回归共236组通过，无额外警告/错误输出，日志 `/tmp/rhythm-mutes-regression-*.log`。涵盖实际模块挂载→首20/total21→翻页、文字安全、错误未知→原页重试、身份/迟到/轮换、真实0、多个挂载使用者、SSR/访客、缩小列表返回第一页及旧链移除。此隔离组件测试不能代替真实浏览器。

`corepack yarn run build`类型/客户端/SSR通过，日志 `/tmp/rhythm-v3-chat-mutes-build.log`。72个JS版本发现0，无map/sourceMappingURL/sourceURL；报告 `/tmp/rhythm-v3-chat-mutes-version-audit.json`。两仓diff --check通过。路由清单832全仓路由，其中v3 HTTP284条（GET142/POST72/PUT46/DELETE24，无重复），加既有WebSocket1条共285；新Processor总register已接。静态计数不证明整站IoC成功。

Java改动已在commentary提醒：用户必须先通过IDEA重新编译或重启服务端再联调，未获实际运行确认前不以浏览器旧行为判断代码问题。没有Maven、FTL/旧皮肤改动、SQL迁移、APP改动、提交或推送。未用浏览器，实际桌面/移动窄屏、完整服务/迁移、真实凭据和全局风控仍未验收。

## 安全与反漏扫

新增入口明确要求Bearer/content.read，匿名、旧Cookie/apiKey不能调用，不新增anonymous.viewSkips例外；已有Router.startRequestHandler→BeforeRequestHandler的黑名单/firewall处理保持。未改AnonymousViewCheckMidware或CaptchaProcessor，首访/周期验证码、黑名单→test→validateCaptcha解封、开发关闭风控真实回归仍待。只读GET无写副作用，CSRF不能用它修改禁言；不存在指定owner或任意表/SQL/远程抓取输入，SQL参数化、分页有界。五字段白名单与Vue文本展示避免敏感泄露及HTML执行，头像限制浏览器安全地址，服务端不抓取，无SSRF入口。身份与Abort/代数隔离防旧名单写回新账号，请求并发有界但真实滥用未压测。

未新增CDN、公开目录、上传下载地址，构建版本/map扫描通过；整个历史工作区、生产CDN/目录枚举、存量上传下载路径仍需发布前审计。单SQL读快照不是实时订阅，也不保证后续翻页时数据库未变化。超预算503及损坏状态显式失败不自行清理历史数据。

## 完整人工验收

1. 通过IDEA重新编译/重启本地Rhythm，前端build后强刷，登录→/cr→侧栏配置启用「思过崖」。经典与简约布局、桌面与移动窄屏，核对昵称/头像/资料链接及解除时间。只请求GET /api/v3/chat/mutes，不请求旧si-guo-list；20条页面显示实际total，翻页/上一页/刷新正常。
2. 测试环境准备个人禁言、已到期、恰好到期、停用/未激活、全体禁言、sys-risk及旧名单孤立记录；只显示当前有效账号未到期的个人禁言。被禁言账号改名后仍按ID展示新名；相同解除时间按ID稳定排序，昵称含尖括号按文字展示。
3. 至少21人验证第二页及末页；名单缩小后越页为空有返回路径。模拟503或断网：显示错误和人数未知，不能显示“无人禁言”；原第2页重试恢复第2页。重复/损坏禁言状态应503且不修改数据库。
4. 未登录、无Bearer、旧Cookie/apiKey、失效/撤销令牌应401，缺content.read应403；重复/未知参数、p=0/小数/过大、size=51及非法UTF8应422。返回仅五个公开字段与分页，不能包含原因/操作者/联系方式/风控。
5. 在途读取时退出/换人或切页，旧响应不得回填；正常token轮换刷新原页。关闭侧栏或离页取消最后使用者；焦点返回可刷新，注明结果是读取时快照。另验全局黑名单/验证码解封及开发风控关闭、真实全服务权限、窄屏布局与批量滥用。

已完成源码与隔离验证，完整v3运行验收及其余模块迁移继续。API.md需同步网站API文档文章 https://fishpi.cn/article/1636516552191 。
