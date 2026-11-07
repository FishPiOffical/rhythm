# v3 管理员 OAuth 客户端登记（2026-10-20）

本记录为内部实现/验证交接，公开契约以API.md为准。用户已确认仅管理员登记维护，以及每客户端最多20回调、每个2048字符。

## 实际文件与行为

- `src/main/java/org/b3log/symphony/processor/ApiV3ClientManagementProcessor.java`：GET /api/v3/admin/oauth/clients、GET /{clientId}、POST同集合，settings.write及当前管理员门禁；POST另接共享写门禁。
- `src/main/java/org/b3log/symphony/service/ApiV3ClientManagementService.java`：当前数据库认证、列表/回调分页、幂等严格根登记，复用已批准登记primitive。
- `src/main/java/org/b3log/symphony/util/ApiV3ClientManagementInputs.java`：name/scope/redirectUris白名单、20/2048边界及客户端路径格式。
- `src/main/java/org/b3log/symphony/util/ApiV3ClientManagementDto.java`：存储数据也按字段/长度/状态/scope/回调规范重新投影，损坏503，不返回任意对象。
- `src/test/java/org/b3log/symphony/processor/ApiV3ClientManagementTest.java`、`tools/api-v3-client-management-test.mjs`：生产路由/控制器/中间件、SQL/登记核心/幂等和真实JdbcTransaction隔离专项。
- 主代理在ApiV3Processor总注册，并同步API.md、UPDATE_LOG.md、AGENTS.md、progress及plan。

## 授权与事务

authenticate只提供可信tokenId，不能信调用方clientId或缓存role。管理门禁重读website客户端、当前用户、token行，核对真正adminRole、用户状态、access类型/有效期/未撤销、token归属及第一方客户端、settings.write。普通用户、OP、第三方管理员Token、Cookie/apiKey均不获得管理权限。列表/详情不包含website_v3，不能通过此模块修改内置客户端。

登记使用现有ApiV3IdempotencyMgmtService的严格根与guarded MutationStore，先按账号重置相同顺序锁全部客户端命名空间（ORDER BY clientId），再website→user→token→request；新执行、重试及重放均在根内重新认证。避免持website锁插入新app时，与账号重置持app/gap锁等website造成反向锁序。真实MySQL间隙/多连接行为仍待运行确认。

登记核心服务端生成app_标识，name去首尾空白、scope合法集合规范化、回调逐条按既有HTTPS/开发回环/原生scheme政策精确保存，客户端/全部回调/回执同提交，沿用核心保存点。不做自助审批，不签发用户Token。HTTP回执仅clientId/name/scope/status/redirectUris；读取仅公开登记字段，回调分页独立，不返回凭据列/内部行ID。最多20回调为用户已确认的新HTTP契约，已有存储目录仍可分页读取。

同键同原请求恢复原clientId与回执，不重新登记或覆盖后续资料；不同原内容409。请求/数据库/提交前故障无残留，提交成功确认丢失可能503，恢复后原键重试取回持久回执。响应只允许已确认字段，未知字段、客户端自选ID/status/userId拒绝。scope/name与精确URL规则复用已有primitive；URI不抓取、不裁剪或重写query/大小写。

## 实际验证

`node tools/api-v3-client-management-test.mjs` Java25隔离10组通过：

1. 实际Dispatcher/auth/controller：普通/第三方管理员/错误scope/无Token拒绝，内置客户端隐藏，未注册危险维护路由。
2. 当前role/account/token/type/state/expiry/owner/client/scope及website状态覆盖缓存身份，门禁存储失败503和no-store。
3. 实际已批准登记核心、严格根、SQL和原回执，服务端ID、精确回调、提交前observer不可见，namespace→client/user/token/request锁序与白名单。
4. 降权后的原key及HTTP预检后降权都拒绝，不能通过重放越权。
5. 客户端/回调/回执写后故障、保存点释放错误、真实JDBC commit前拒绝均整根回滚/清资源，恢复后可安全重试。
6. JSON重复键/类型/未知字段/key/非法scope/URI凭据/片段/保留参数/重复回调无部分回执。
7. 客户端及回调各超过50行的真正分页、界限及失败503。
8. 存储DTO坏长度/控制字符/scope/status/URI失败，精确合法query/大小写保持。
9. 用户批准20/2048边界成功，21/2049拒绝；开发回环沿用原规则。
10. 真实JDBC提交成功后确认丢失保留client/callback/receipt；原key恢复且不重复创建/覆盖后续名称。

生产Repository、业务、登记primitive、strict、savepoint、idempotency和路由编入；Token查找/响应信封/UserRepository连接投影、SQLite去FOR UPDATE及连接池构造为显式适配器。实际JdbcTransaction方法用临时SQLite JDBC/故障proxy执行，不是MySQL锁证明。临时资源由runner清理。未使用Maven或真实8080/数据库/生产。

## 完整人工链路

1. 用户先通过IDEA重新编译或完整重启；迁移既有v3 OAuth及request表后，网站管理员v3登录取settings.write第一方Token。普通账号、OP、第三方管理员Token、过期/撤销Token、仅Cookie/apiKey请求应401/403，SQL错误503且无内部信息。
2. 列表→登记一个已批准应用（新key、name/scope、HTTPS及预登记native回调）→列表→详情；server app_ID、完整回调、状态和分页应一致，无内置网站客户端、Token或凭据字段。
3. 相同原key/请求重试得同clientId；改name/scope/URL或顺序409，不新增客户端。更新测试数据库中的当前名称后重放，回执保留原结果但GET显示新状态。确认丢失后原键恢复，不能换键重建。
4. 最大20地址/单URI2048；21/2049、重复/片段/用户凭据/保留OAuth响应参数/非法scheme/生产HTTP回环拒绝422。开发回环只在开发模式可登记，URI大小写/端口/query与编码保持。
5. 使用登记应用在PC/mobile完成OAuth同意→批准/拒绝→精确回调→S256换码→按scope资源读取→刷新/撤销；错误state/PKCE/回调/重放码拒绝。协议端点的实际匿名校验规则仍需既有链路联调，不添加例外。
6. 开发数据库注入每个持久化/提交故障，失败无半客户端/回调/回执；降权/停用管理员或Token后旧key也不能取回执；与重置密码、多实例登记/换码并发检查锁序/死锁恢复/连接释放。
7. 回归站点黑名单/test、匿名首访/第五次/validateCaptcha解封、开发关闭风控，以及全站CDN/version/sourceMappingURL/目录和上传下载路径。新三接口全部认证，不新增匿名例外；未实际执行上述全站验收。

## 安全与剩余范围

当前DB管理员+第一方+scope防未鉴权/越权；Bearer-only不新增Cookie授权CSRF路径；SQL参数绑定、有界字段/页码/JSON、响应白名单，无HTML渲染/URL抓取/SSRF/上传下载/新静态或CDN资源。POST使用现有共享写门禁，不能据此宣称真实分布式限额/全站压力已验收。未改BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor或anonymous.viewSkips。

停用、回调/权限/名称修改、管理员UI、全应用Java/IoC、MySQL真实迁移/并发、HTTP/WS/浏览器及全站反漏扫未完成；不把本轮登记三接口当作完整OAuth管理交付。普通后台迁移仍不在范围，本模块为用户明确批准的OAuth管理员登记例外。
