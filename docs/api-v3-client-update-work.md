# v3 OAuth 管理员客户端完整修改（2026-10-20）

内部工程交接，不可整体复制到公开 API 文档。用户明确批准：仅管理员维护；回调或有效 scope 变化使已有 Token、旧授权码和待确认授权全部失效，用户重新授权。保持客户端当前状态，不提供重新启用。

## 文件及接线

- `src/main/java/org/b3log/symphony/processor/ApiV3ClientUpdateProcessor.java`：PUT route、settings.write、当前第一方管理员预检、共享写预算、严格 JSON/路径/幂等键输入。
- `src/main/java/org/b3log/symphony/service/ApiV3ClientUpdateService.java`：当前管理员保护的严格幂等根、当前目标/精确回调比较、完整替换及授权全失效。
- `src/main/java/org/b3log/symphony/service/ApiV3ClientRegistrationService.java`：仅提取无副作用 `Definition/normalizeDefinition`，create 原验证/事务/保存点行为保留；管理登记回归已通过。
- `src/test/java/org/b3log/symphony/processor/ApiV3ClientUpdateTest.java`：生产路由/controller/服务/SQL、实际 Code/Consent/SocketTicket 核心、严格事务故障专项。
- `tools/api-v3-client-update-test.mjs`：Java25 + 临时 SQLite 隔离专项，不运行 Maven/服务端。
- 本文件：内部契约、证据、限制及人工链路。公共 API/日志/AGENTS/进度和总接线由主代理统一维护。

总注册为 `ApiV3ClientUpdateProcessor.register()`，真实 route：

```java
Dispatcher.put("/api/v3/admin/oauth/clients/{clientId}", p::update,
        auth::settingsWrite, p::administrator, writes::handle);
```

## 精确契约

`PUT /api/v3/admin/oauth/clients/{clientId}` 需要网站第一方当前有效管理员、settings.write 和 Idempotency-Key。完整替换 `name/scope/redirectUris`，三字段必须齐全，额外 status/owner/userId 等字段拒绝422；JSON重复键400，采用同登记的解析前请求体上限。路径clientId保持 `[A-Za-z0-9_-]{1,64}`，内置 website_v3 和不存在目标404。

name 去首尾空白，1..128 UTF-16、禁止控制字符；scope 为已实现权限，最多255 UTF-16，规范成排序去重空格列表。回调1..20个、各最多2048 UTF-16，完全精确保留URI（包括大小写/端口/query），重复URI拒绝；HTTPS、开发HTTP回环、原生反向域名专用scheme及其余限制沿登记规则。不接受HTTP远程地址，不抓取任何地址。URI数组排序后作为规范请求，因此scope或回调重排不产生幂等冲突。

200 data只 `clientId/name/scope/status/redirectUris`；status保留0或1，即使修改停用应用也不会启用。回执为当次完整定义，不含Token数量/凭据/用户。旧成功键回原结果，不覆盖后续修改、删除或状态变化；同键不同有效请求409。重放仍先重新校验当前管理员/第一方Token，失权后403；未鉴权401，受控存储错误503且不暴露数据库细节。

单纯name变化，或scope/URI集合重排，保留全部授权。仅当前有效scope集合或当前active精确URI集合实际变化时：撤销目标client下全部Token（所有用户/家族/类型；不分页），删除该client全部授权码（已用及未用），删除decision=pending的待确认请求；保留approved/denied用户决定历史。即便之后改回原定义，旧码/旧pending也无法恢复。回调定义完整替换会清理历史inactive行，但仅inactive行清理不使有效授权失效。

## 当前锁和事务

复用管理guard，严格根中按排序client namespace→website→当前user→当前token→request锁序；重试和重放同样重查，不依赖HTTP预检快照。当前目标和active回调当前读锁，metadata、全量回调删除/插入、全量Token撤销、旧码/pending删除及回执同根提交，mutation有局部保存点。任一步失败均整根回滚，无独立Token事务、外部网络调用或提交前广播。

老内部登记可能超过20回调，只取21个有效唯一URI即可证明与<=20的新定义不相等，避免将截断集合误判为相等；SQL删除完整client回调集合，不只删读到的行。返回的当前行/URI/hash/status有损坏或重复即503。namespace锁与协议的client锁串行化已有请求签码，不能在修改提交后凭旧pending签新码。

删除码而不伪造consumedAt/family，删除pending而不伪造用户denied。原码exchange仍当前查client/redirect/code；旧proof被删除，即使定义回退亦无效。Token认证/refresh/Socket父授权查询当前Token状态，scope变化后旧access不能继续使用未变的剩余scope。纯name修改则不撤销。

## 实际验证

`node tools/api-v3-client-update-test.mjs`：Java25 隔离11组通过，日志 `/tmp/rhythm-v3-client-update.log`。

1. 实际PUT/auth/controller及当前第一方管理员；name-only/scope重复重排/URI重排保持全部Token/码/pending，实际原Consent.decide仍可作用户拒绝。
2. 分别scope、URI集合变化，跨75+条/多用户/类型/状态授权全量失效，保留completed决定和其他client/admin。
3. change→restore，实际Code.exchange及Consent.decide仍拒绝旧proof400，不能恢复旧授权。
4. 规范重排同键重放、不同内容409、旧键不覆盖后续新值；停用客户端修改后仍status1。
5. 三字段齐全、类型/未知字段/JSON重复键、路径、内置站点、key、20和2048边界失败不部分写。
6. 管理员降权后旧键拒绝、HTTP预检后Token撤销在根中拒绝。
7. metadata/回调删除/插入/Token/code/pending/回执写后故障、保存点release及严格commit失败全回滚，同key恢复可用。
8. 独立observer提交前看不到中间状态；实际提交成功但确认丢失，所有授权失效和回执已持久，同key恢复原结果。
9. 当前metadata/URI损坏503；仅清除inactive回调行不撤销有效授权。
10. 历史55+回调，21行比较安全证明变化、完整删除，无分页漏撤。
11. 实际SocketTicket issueChat→consume→authorized，修改后现有grant false、旧票consume401、新取票401，无真实网络。

主代理独立重跑 `api-v3-client-management-test.mjs` 10组及 `api-v3-client-disable-test.mjs` 9组通过，证明共享normalize提取未改变登记和停用。测试执行实际Repository SQL、打包Latke JdbcTransaction/保存点、strict幂等核心；适配器只处理SQLite FOR UPDATE、连接/故障注入、Token HTTP身份fixture、文章目标拒绝stub。Token真实签发/refresh并未在此runner运行，invalid-code路径若意外走到签Token会主动报错，不能宣称完整OAuth服务端验收。

没有 Maven、完整IoC/Java编译、真实MySQL、多实例、浏览器、8080或生产访问。原Socket parent JOIN FOR UPDATE具体争锁顺序受MySQL执行计划影响，namespace/client-first与JOIN可能死锁，需要实际多连接重试验收；SQLite不能证明间隙锁/索引/唯一约束/查询计划。已连接WS以现有发送/心跳检查关闭，不能保证即时跨节点断开或收回在途帧。

## 完整人工用户链路

Java变更联调前，请用户通过IDEA重新编译或重启服务端，确认总注册和既有OAuth迁移生效；本轮无新表/前端。

1. 第一方管理员取得settings.write，登记两个测试app并GET详情；两位用户分别经PC/mobile授权→S256换码，保留access/refresh、待换码code、待确认同意页、已连chat WS和未消费票；另app为隔离对照。
2. PUT完整三字段仅改name，并重排scope/回调；200只白名单，GET新名及规范scope，原access/refresh/code/同意页/WS仍可用。
3. 新key PUT更改有效scope或精确URI，所有目标用户旧access/refresh/码/未确认同意都失败，已连WS按心跳/发送检查关闭，旧票/新取票拒绝；他app和管理员保持可用。GET注册定义和status正确，completed用户决定历史保留。
4. 新key改回原定义，旧code/同意页仍失败；用户重新完整授权、换新Token后正常。单纯URI数组/scope词顺序变更不撤销。
5. 原key重试回原回执；新key写后再重试旧key不覆盖；同key改内容409。停用目标可改metadata且始终status1，不存在重新启用。
6. 普通/第三方管理员/错误scope/过期或已撤销Token、仅Cookie/apiKey拒绝；管理员降权后原key也拒绝。website/missing404，缺字段/额外status/重复回调/超20/2048/128/255和非法URI422，重复JSON400，失败后metadata/授权/回执无部分变化。
7. 测试库注入各写、保存点、提交前失败全部回滚；提交确认丢失同key恢复。并发PUT/DELETE/refresh/换码/同意/取票/密码重置验证MySQL当前权限及死锁重试；大量历史授权不得漏撤。
8. 回归匿名/登录/黑名单→test→validateCaptcha、anonymous.viewSkips及开发关闭盾；检查静态/CDN版本/sourceMappingURL/目录枚举/上传下载路径/日志敏感字段。此处为人工待验，不声称本轮已做全站扫描。

## 安全与反漏扫结论

新PUT要求Bearer/settings.write/当前第一方管理员，不新增匿名入口、anonymous.viewSkips、Cookie身份或权限缺省放行；未动黑名单和验证码链路。SQL参数化及client predicate、字段白名单、目标namespace锁、共享写预算、无URL抓取/HTML/上传下载/新增静态资源/CDN/source map，因此本轮无新增注入/SSRF/XSS/目录面；实际压测/分布式限额及全站反漏扫未执行，不公开内部阈值。

## 公开文档交接

主代理已统一更新API.md/UPDATE_LOG.md；公开要点是完整三字段、20/2048/name128/scope255、scope/URI集合变化使全部旧授权失效、name-only/重排保留、不可修改website/不可重新启用、同键不覆盖后续值。不要将本文件锁序/失效SQL/匿名或CSRF内部策略/限额阈值复制到公开文档。最终用户需把API.md同步至 https://fishpi.cn/article/1636516552191 。
