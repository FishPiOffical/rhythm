# v3 官方 APP 短期扫码服务

2026-10-25。用户确认：网页登录生码→未登录APP扫码核对账号并明确确认→网页再次批准→APP一次换取独立v3令牌；从创建起两分钟有效，后续不续期，拒绝作废。用户随后明确**APP代码不用动，只需v3支持**；官方APP克隆 `/Users/ling/Documents/fishpi/fishpi-app` 的本轮改动已逐项还原，状态为空，仅作只读协议参考。没有保留APP新增解析器、测试、文档或日志修改。

七条POST已由ApiV3Processor总注册，当前271（270HTTP+1WS）：GET131/POST70/PUT45/DELETE24/WS1。API.md新增完整接入文档，更新记录已同步。网站页面没有随本批自动支持新二维码，API使用方需按协议接入；不将后端接口等同于设备端联调完成。

## 实际文件

| 文件 | 行为 |
|---|---|
| src/main/java/org/b3log/symphony/util/ApiV3AppLoginProtocol.java | 两分钟纯状态机、严格canonical QR/证明、S256 challenge、域隔离handle摘要、双确认与终态 |
| model/ApiV3AppLoginRequest.java、model/ApiV3Client.java（同Java根） | 新表常量及固定fishpi_app_v3客户端ID |
| repository/ApiV3AppLoginRequestRepository.java（同Java根） | 只读hint、严格根当前锁、管理员同根删除未完成请求 |
| service/ApiV3AppLoginService.java（同Java根） | 全客户端锁→当前网页授权/user/access/epoch→请求锁；重验不可变hint、scope，状态转移/令牌对/消费同严格根 |
| processor/ApiV3AppLoginProcessor.java、processor/ApiV3Processor.java（同Java根） | 七POST、严格JSON/no-store、网页登录Bearer与原生匿名验证入口分别接线 |
| service/ApiV3ClientUpdateService.java、ApiV3ClientDisableService.java（同Java根） | Native授权/回调实际变化或停用同根清待扫码，保留消费/拒绝终态；正常旧返回字段不变 |
| sql/20261025_api_v3_app_login.sql | 新表、expiry/owner索引和固定native登记；重复迁移不覆盖已停用或自定义scope |
| src/main/resources/repository.json | 新表全字段/类型/长度，与Repository无重复前缀 |
| src/test/java/org/b3log/symphony/util/ApiV3AppLoginProtocolTest.java | 四组真实纯核心Java执行 |
| src/test/java/org/b3log/symphony/service/ApiV3AppLoginServiceTest.java | 七组SQL/真实Token primitive/HTTP/故障执行 |
| src/test/java/org/b3log/symphony/processor/ApiV3ClientUpdateTest.java、ApiV3ClientDisableTest.java | Native待扫码撤销的新分支，旧管理回归 |
| tools/api-v3-app-login-protocol-test.mjs、api-v3-app-login-service-test.mjs | Java25隔离运行器；后者DAO/当前事务通过SQLite适配，HTTP匿名验证为显式适配器 |
| tools/api-v3-app-login-schema-mysql-test.py | 一次性离线MySQL真实DDL、元数据与条件消费/锁等待原语 |
| src/test/java/org/b3log/symphony/service/ApiV3AppLoginMysqlTest.java、tools/api-v3-app-login-mysql-test.mjs | 生产服务与精确扫码DDL在实际MySQL执行，七独立JVM竞争、五实际写入故障及全部七服务场景；MySQL保留FOR UPDATE，不用SQL sink代替真实Token发行 |
| tools/api-v3-client-update-test.mjs、api-v3-client-disable-test.mjs | 增加新仓储编译依赖，更新套件单独的可信Cookie证明适配；密码/epoch实现在安全和扫码套件单独执行 |
| API.md、UPDATE_LOG.md、AGENTS.md、docs/api-route-inventory.md、api-v3-progress.md、api-v3-development-plan.md及本记录 | 契约、清单、范围与验收 |

## 当前行为

- 创建/网页登录状态/决定只接受当前website第一方Bearer，发码需要settings.write+profile.read；不是Sessions或apiKey。发码将**现有网页登录access ID**和credential epoch绑定。每次claim/状态/决定/换码都在当前根中重验发码来源未失效、账号有效、原生client有效、所授scope仍被两端允许。退出发码网页登录、全撤凭据、停用账号/应用后，不可继续旧请求。
- 新建仅返回一次随机request和`fishpi-login:v3:...`文本，DB仅保存域隔离摘要；不返回长期apiKey/图片URL/网页登录Cookie。创建丢ACK可重新生码；没拿到handle的旧请求仍需显式双确认才可能登录，且两分钟到期。
- 首次APP challenge绑定不可替换；同APP重试不续期/重置。APP持有随机32字节verifier，以其ASCII文本SHA256的base64url作为challenge，所有编码严格canonical、不带填充且43字符。APP核对账号前不应确认。只确认APP不能领令牌；网页越序批准409。
- 数据库锁住原请求后，真实Token发行primitive与CONSUMED同一严格事务，失败都回滚；APP取得独立fishpi_app_v3 token家族，不转换成旧apiKey或website Cookie。真实生产primitive已在隔离JDBC运行：随机Token只存摘要、同家族对、Native refresh轮换和匹配重放撤销，与网站来源家族隔离。
- 换码丢ACK后不重新交付原始令牌，也不再次签发；新请求重新双确认。没有用通用JSON幂等回执保存raw二维码/verifier/token。状态/决定/请求均在JSON body，不进URL；输入字段/长度严格白名单。
- 当前原生入口仍显式经过AnonymousViewCheck，无IP白名单/viewSkips或协议凭据准入例外，原有全局黑名单先执行；真实原生接入需处理现有访问验证，当前没有改APP或以有效QR绕过该链。网页登录三入口要求Bearer。API私密传输日志沿已安装OAuthTransportFilter的/api/v3/auth前缀保护。
- 管理员实际修改Native权限/回调或停用Native会同根删除WAITING/CLAIMED/APP_CONFIRMED/WEB_APPROVED请求，不能变回配置后恢复旧待扫码；CONSUMED/DENIED记录保留。普通改名不撤授权。新client由迁移登记，已有管理流程可停用；部分仅网页本人可用接口并不因Native token自动开放。
- 过期逐次按当前服务器时间判断；较旧记录在新建时同根有界清理，不把进程内桶作为状态，不宣称有定时精确物理删除。source授权在两分钟内先失效时同样不可用。客户端需本地生成QR，不交第三方二维码图片服务。

## 已执行证据

1. 纯协议四组退出0，`/tmp/rhythm-v3-app-login-protocol-tests.log`：canonical payload/拒旧login与URL、独立Python hashlib复核S256向量；顺序/单APP/一次消费/同APP重试不续期；过期边界/时钟回拨/任一端拒绝；坏快照/溢出。
2. 事务/HTTP七组退出0，`/tmp/rhythm-v3-app-login-service-tests.log`：实际Repository/生产SQL/第一方与epoch授权；单对令牌/白名单、绑定/源账号/源token/两client/scope失效；半Token写/坏发行响应/真实strict commit失败回滚；提交丢ACK保持已消费；超时/拒绝；他人hint/不可变字段竞争/坏capability及begin失败统一503且不留请求行。第六组用实际ApiV3TokenMgmtService发行/保存点/随机哈希/原生刷新和家族重放撤销，DAO插入及UserQuery为明确适配器。第七组实际七POST Dispatcher中间件/严格JSON/no-store，Cookie/query apiKey不能授权网页登录路；原生匿名检查为可开关适配器，不是实际验证码通过证明。
3. 实际管理员修改12组、停用10组退出0，日志 `...app-login-client-update-regression.log`、`...app-login-client-disable-regression.log`，新增Native待扫码删除与终态保留，其余旧分支/故障保持。旧更新套件的可信Cookie发行证明是显式适配，不代替当前账号密码/epoch检查；扫码七组和安全六组执行真实当前授权。
4. 安全六组回归退出0，`...app-login-security-regression.log`；非Maven，仅本地Android Studio JBR Java25的隔离javac/java，无完整应用编译/IoC启动。
5. 一次性MySQL8.4三组退出0，`/tmp/rhythm-v3-app-login-mysql-tests.log`：生产DDL重复与Native登记不覆盖停用/custom scope；repository全部列/类型及索引；InnoDB事务条件更新和二行token sink回滚；两个独立SQL消费者，实际performance_schema.data_lock_waits观察锁等待，最终一胜一负、一次消费/二行sink。此项是**SQL原语**，不是实际Java Service的跨JVM/MySQL并发，未将sink当真实Token签发。容器已清理，无业务库/宿主端口接入。
6. 进一步完成实际Java/MySQL专项（`/tmp/rhythm-v3-app-login-java-mysql-tests.log`）：七个原服务/HTTP场景全部在MySQL8.4重跑，当前授权/epoch/原生实际发行/刷新/家族重放均执行生产方法；精确QR DDL、utf8mb4_bin、REPEATABLE-READ及STRICT_TRANS_TABLES逐进程核验。五种实际Token写/消费写/commit故障涵盖第一条、第二条、CONSUMED写、提交失败和提交后丢ACK，真实生产令牌对随根回滚或保持唯一已消费家族。另七种独立JVM竞争（同challenge、异challenge、重复换码、拒绝先/换码先、重置先/换码先重置后）逐场景观察InnoDB LOCK WAIT，独立连接核对状态、一对真实Token/同family及改密全撤；不输出测试Token原文。Ancillary用户DAO、基础账号表、HTTP匿名风控仍为明确适配边界，不是完整应用IoC/真实设备。临时容器与随机loopback端口已清理。
7. 实际总注册提取270唯一HTTP，加既有WS=271；机械全仓818路由/398浏览器引用。diff --check通过。没有前端源码/CSS/min.js/FTL变化，不需要yarn生成；没有真实APP/网页登录/相机/账号凭据操作。APP仓库状态为空。

## 完整人工链

1. 先通过IDEA重编译/重启本批Java，部署20261025_api_v3_app_login.sql及此前v3/凭据迁移，再确认完整IoC可启动。用户未确认已重启；不能把旧服务行为当新代码结果。仅使用隔离测试账号。
2. 第一方当前网页登录Bearer调用requests，获得本地QR payload/两分钟期限；匿名、apiKey、Cookie或其他客户端身份不能生码。取消/不批准不会签令牌。不要在二维码图片服务或日志中传播request/verifier。
3. 测试客户端生成32字节随机verifier与S256 challenge；claim显示账号，APP-decison confirm后网页status显示app_confirmed，再web decision approve；exchange仅一次返回Native令牌。用新Bearer读取本人资料、现有OAuth client_id=fishpi_app_v3刷新/撤销，不能借website cookie刷新或作为旧apiKey。
4. 独立测试仅扫码/仅APP确认/网页越序/其他challenge抢占/坏证明、两分钟边界、两端拒绝与重复换码：应按API返回403/409/410，不签第二对。状态只元数据、账号白名单，任何失败不回token或内部绑定字段。
5. 发码后退出原网页登录、源token到期/撤销、全设备改密重置、停用账号/Native、管理员scope/回调修改，旧码不能兑换；重新配置回来仍不复活被清掉的待确认。当前其他账号不能读/批准该请求，假user字段拒绝。
6. 用真实MySQL实际Java服务启动两个进程，对同一批准码同时换取；应观察锁等待、只一对真实Token、一次CONSUMED。注入Token写/状态写/commit失败要同根回滚；提交成功但断网不再交付/签发第二对，新码需重新双确认。这些场景已在隔离真实Java/MySQL及独立JVM竞争执行；完整部署多实例/实际连接池/代理请求和业务库仍需联调。
7. 新IP原生四入口验证匿名访问链、全局黑名单→/test、首访/多次访问校验、Captcha解封及开发模式；本轮只实际HTTP适配验证接线，不是全局运行验收。完整网站PC/mobile与原生设备协议、刷新Cookie/跨设备撤权仍需联调。APP源码保持只读，此处可用独立协议测试客户端，无APP修改交付要求。

## 安全与反漏扫结论

源码与隔离HTTP：网页登录Bearer、当前授权/owner/epoch、Native capability/证明和双确认覆盖未鉴权及越权边界；Cookie不授权三网页登录写，不新增CSRF Cookie例外。原生四入口明确匿名验证，Before黑名单/anonymous.viewSkips/Captcha链未改；无协议准入“顺带”IP白名单。严格JSON/长度/编码，所有值参数化SQL、动态表名内部Repository；无远程抓取/SSRF、HTML渲染/XSS、任意上传下载路径或新增公网静态资源/CDN。忙碌/负载由既有写/IP预算与二分钟状态控制，但不能当真实压力验收。白名单响应不含密码/联系方式/IP/UA/epoch/issuer ID/摘要；令牌仅一次换码响应。

没有新增JS/CDN资源，未重扫全站历史CDN版本、sourceMappingURL、目录枚举/历史上传下载。真实全局未鉴权/越权/CSRF/验证码/限流、完整部署多实例/连接池/IoC、真实设备/内存/断网并未完整验收，不默认通过。网页新QR入口尚未迁移，本轮没有继续调用旧第三方QR服务。APP未改、v3总体未完成；API.md应同步网站API文章。
