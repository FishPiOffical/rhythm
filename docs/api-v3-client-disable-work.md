# v3 OAuth 管理员客户端停用（2026-10-20）

本文件为内部工程交接，不能整体复制到公开 API 文档。用户已确认仅管理员登记维护；本工作包完成逻辑停用，不提供重新启用；后续回调/权限修改已在独立客户端修改工作包实现，见 api-v3-client-update-work.md。

## 实际文件及总接线

新增五个文件，未改旧 Token/OAuth/Socket/管理服务、SQL、Repository 模型或前端：

- `src/main/java/org/b3log/symphony/processor/ApiV3ClientDisableProcessor.java`：DELETE 路由、settings.write、当前管理员门禁、写门禁与空 JSON/1024 字节上限。
- `src/main/java/org/b3log/symphony/service/ApiV3ClientDisableService.java`：受管理员锁保护的严格幂等根，目标逻辑停用和不分页的客户端 Token 撤销。
- `src/test/java/org/b3log/symphony/processor/ApiV3ClientDisableTest.java`：生产路由、当前管理员、事务/回执、真实 JdbcTransaction/保存点故障及实际 SocketTicket 当前授权专项。
- `tools/api-v3-client-disable-test.mjs`：Java25 隔离编译与临时 SQLite 执行，复用 `ApiV3ClientManagementTest.Fixture`，不执行该旧 Test 的 main。
- 本文件：精确契约、测试证据、剩余风险、完整人工链路和公开文档片段。

由主代理在总 `ApiV3Processor.register()` 增加：

```java
ApiV3ClientDisableProcessor.register();
```

生产路由：

```java
Dispatcher.delete("/api/v3/admin/oauth/clients/{clientId}", p::disable,
        auth::settingsWrite, p::administrator, writes::handle);
```

## 精确契约

- `DELETE /api/v3/admin/oauth/clients/{clientId}`，仅网站第一方、当前有效管理员的 access Token；需要 settings.write、Idempotency-Key。
- clientId 为原样 `[A-Za-z0-9_-]{1,64}` 字符串。`website_v3` 不可停用，合法管理员请求它或不存在目标返回 404。
- 可无请求体，或 `Content-Type: application/json` 的空对象 `{}`；解析前请求体最多 1024 字节。超限 413、未知字段 422、重复 JSON 键 400。body/query 的 userId/clientId/apiKey 不提供身份或替换路径目标。
- 成功 200、data 仅 `{"clientId":"external_app","status":1}`。这是逻辑停用，保留 client、精确回调、授权请求和码的存储记录。
- 本次当前请求把 client.status 设为 1，并把目标 clientId 下所有用户、家族、access/refresh 及非 revoked/NULL 状态 Token 整体设为 STATE_REVOKED=2，更新这些 Token 的 updatedAt。已经 revoked 的 Token 不重写时间。没有分页、没有依赖缓存身份。
- 同键同目标只返回原回执，不更新状态/时间；同键换目标 409。若目标已逻辑停用，新键请求仍成功并清理任何残余未撤销 Token。原成功目标后续被删除或资料变化，原键只回旧回执，不重建或覆盖；新键缺目标 404。
- 当前管理员降权/停用、Token 归属变化/过期/撤销或非第一方后，原成功键也不能重放。当前管理员校验失败 403，未带有效 Bearer 为 401；受控存储故障 503，不返回驱动信息或凭据。

## 事务、授权与失效链

复用 `ApiV3ClientManagementService.requireAdministrator`，不能自己从缓存 userRole 或请求字段判断管理员。HTTP 预检之后，每次根事务的新执行/重试/重放都会重新调用 `requireAdministrator(identity,true)`；按既有账号重置顺序锁排序客户端命名空间，再当前 website client → user → token → request。目标 client 行当前读（namespace 已锁住该集合），mutation 内保存点绑定 status、完整 Token UPDATE 和外层幂等回执。目标缺失/损坏或写入故障整根回滚；没有业务提交后广播/外部请求。

原客户端状态不合法/重复行时失败 503，不猜测或随机停用。UPDATE 只改 `status`，不覆盖名称/scope/回调；Token UPDATE 只按目标 clientId，不改其他客户端或共享 admin 第一方 Token。回执不带 Token 数量、用户、家族、凭据、scope 或个人信息。

现有失效链已逐条核对：

- `ApiV3TokenMgmtService.authenticate` 同时查 Token 活跃/到期和 client.status；停用后的 access 不再鉴权。
- `refreshForClient` 当前锁客户端并检查 active，禁用时拒绝 refresh；`issueForClientInCurrentTransaction` 也查 active。
- `ApiV3AuthorizationCodeService.registered` 在批准/签码/换码链上当前读 active 客户端和精确登记回调；停用后拒绝仍未消费的码。
- `ApiV3SocketTicketService.parent` JOIN 当前 Token/client/user 状态。issue、consume、每次 `authorized` 都复用它；已认证连接会在原有数据发送/心跳授权检查中失效，旧未消费票也不能继续认证。

没有直接转发旧广播或声明跨实例立即断开。已有在途数据不能收回，实际网络关闭时机由现有 worker/心跳决定。未物理销毁 pending authorization code/request；如果未来增加重新启用，必须单独设计旧码/请求失效规则，不能直接把 status 改回 0。

锁序限制：旧 `SocketTicket.parent(lock=true)` 使用 token/client/user JOIN FOR UPDATE，具体锁顺序受 MySQL 查询计划影响。与停用/密码重置/换码竞争可能发生数据库死锁并由根协调器重试；本轮不能据 SQLite 证明全链没有死锁。真实 MySQL 间隙锁、约束、索引计划、连接池/多实例仍需运行验收。

## 实际验证

已运行 `node tools/api-v3-client-disable-test.mjs`，Java25 隔离 **9 组 PASS**：

1. 真实 DELETE route/auth/controller/current-admin，假 Cookie/query 身份和第三方管理员/普通用户/错误 scope 拒绝；182 个目标 Token 跨用户/类型/状态全部撤销，保留其他 client、admin Token 和回调。
2. 同键回执不再次更新、换目标 409；目标删除后原键不重建，新键缺目标 404 且无失败回执。
3. 已停用目标清理残余授权；新键重复停用不重写 revoked 时间，1024 字节合法空对象接受。
4. 内置网站/缺目标 404，路径/字段/JSON/key 边界；1025 空白及 UTF-8 多字节超限 body 在解析前 413，状态损坏 503，不留回执。
5. 当前管理员降权后的重放、HTTP 预检后 Token 撤销都在根内拒绝，没有停用目标或取回回执。
6. 实际 client/token/receipt 写后故障、保存点释放错误、真实 JdbcTransaction 严格 commit 前拒绝均全部回滚、释放根并可同键重试。
7. 独立 observer 连接在提交前看不到部分状态；真实 JDBC commit 已成功但确认丢失时，status/Token/receipt 已持久，原键恢复而不重写时间。
8. user/token/client/request 存储读取故障全部 fail closed 503，无状态或回执残留。
9. 实际 `ApiV3SocketTicketService` issueChat→consume→authorized：停用前可认证，停用后现有 grant 授权 false、旧未消费票 consume401、新取票401；无实际 Socket/网络。

执行生产业务、路由、auth/write 门禁、当前管理员 guard、Repository SQL、严格幂等协调、实际打包 JdbcTransaction 和保存点。fixture 显式适配：Token 查找/响应信封/UserRepository 连接投影、SQLite 去 FOR UPDATE/连接池构造及临时 JDBC fault proxy。Socket 的文章目标服务为不调用即不生效的拒绝适配器，本专项只测 chat，不冒称文章权限/真实 WS 测试。

access/refresh/authorization-code 的停用拒绝为源码调用链复核，本专项未执行真实 Token 签发、refresh/PKCE 换码端到端。实际旧 guard 复用测试包含第一方当前 DB 身份，不能替代生产 Token 生命周期联调。

`node --check tools/api-v3-client-disable-test.mjs` 与本批新文件 diff whitespace 检查通过。没有 Maven、完整 Java/IoC、8080、真实 MySQL、PC/mobile 浏览器或生产访问；没有 Git 提交或修改共享文档。

## 完整人工测试链路

联调前由用户通过 IDEA 重新编译或完整重启，确认主代理总接线生效。先迁移既有 v3 OAuth/request/Token/ticket 表，本轮不新增表。

1. 网站管理员登录取得第一方 settings.write；登记测试第三方 app，GET 列表/详情确认 active。两个测试用户分别完成 PC/mobile OAuth 同意→S256 换码，保留 access/refresh、一个待换码授权码、一个已认证 chat WS 和一张未消费票；另一个 app 保持可用作隔离对照。
2. 管理员用新 Idempotency-Key DELETE 目标，无 body 或空 JSON；成功 data 只 clientId/status=1，GET 详情仍在且回调/名称/scope 保持。核对所有家族/类型 Token 已撤销，其他 app/第一方 admin 未受影响，不发生物理删除。
3. 目标 access 读取资源失败、refresh 与待换码码被拒绝；已连 WS 在现有数据/心跳当前检查关闭，旧票不能 consume，新票不能 issue。对照 app 的 HTTP/WS 仍正常。不得把接收中的在途帧或其他节点关闭延迟误当作原子提交失败。
4. 原 key/原目标重复得到同回执、无额外 Token 时间变化/回执；换目标同 key409。已停用新 key200且幂等。普通、OP、第三方管理员 Token、过期/已撤销 Token 或仅 Cookie/apiKey 无权停用；管理员降权后原键也不能恢复回执。
5. website_v3/缺目标404；非法/超64路径、额外字段422，重复 JSON400，缺 key422；合法1024字节空对象接受，1025空白/多字节 body413。状态损坏503，失败前后数据库无部分停用或失败回执。
6. 开发测试库注入 UPDATE、回执、保存点及提交前失败，status/Token/receipt 一起回滚；注入提交后响应确认丢失，用原键重试恢复同结果，不换键重复操作。
7. 与 Token refresh/PKCE/取票/consume/密码重置及多个管理员并发停用做真实 MySQL 多连接测试，核对死锁重试/连接资源、全量撤销、当前管理员撤权边界。不要用 SQLite 适配器代替此项。
8. 回归站点匿名/登录/黑名单→/test→validateCaptcha 解封、首访/第五次/开发关闭盾差异；检查静态/CDN 版本、sourceMappingURL、目录枚举、上传下载路径与日志敏感字段。本轮无相关新增，但不等于已全站检查通过。

## 安全与反漏扫结论

新增路由全程 Bearer/settings.write/current-admin/first-party，不新增匿名入口、anonymous.viewSkips 或旧权限放行；未改 BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor。owner/目标固定、SQL 全部参数绑定，Token 撤销有目标 clientId predicate 且无分页；无新增 Cookie 写 CSRF 路径、HTML 渲染、URL 抓取/SSRF、上传下载或公网静态/CDN/source map/目录。沿既有写预算而不公开阈值；实际压力/分布式限额及全站反漏扫仍未执行。

## 可合入 API.md 的公开片段

### 停用第三方客户端

`DELETE /api/v3/admin/oauth/clients/{clientId}`

管理员停用已登记第三方客户端并撤销其已有令牌，需要网站第一方 Bearer accessToken、settings.write 和 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，网站第一方 Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次停用的幂等键 | oauth-disable-001 |
| clientId | 路径参数，第三方客户端标识 | app_YOUR_CLIENT_ID |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/admin/oauth/clients/app_YOUR_CLIENT_ID' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: oauth-disable-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 停用结果 | `{...}` |
| - clientId | 客户端标识 | app_YOUR_CLIENT_ID |
| - status | 1 表示停用 | 1 |

> 无需请求体，或提交 application/json 空对象 `{}`；请求体最多1024字节。超限返回413，额外字段返回422。客户端登记资料和回调保留，已有令牌失效，不能继续授权或换码；网站内置客户端不可停用。目标不存在或不可管理时返回404。
>
> 同键同目标恢复原结果，不重复停用；同键更换目标返回409。已停用目标使用新键仍返回成功。原键不会覆盖后续状态，如需再次执行新的停用操作请使用新键。未提供重新启用接口。

建议 UPDATE_LOG：新增管理员 `DELETE /api/v3/admin/oauth/clients/{clientId}`，需要第一方管理员 settings.write 和 Idempotency-Key，逻辑停用并撤销其已有令牌，保留登记/回调；相同键重试恢复原结果。body 可省略或空对象、最多1024字节；不提供重新启用。

建议 AGENTS 精简项：v3客户端DELETE走ApiV3ClientDisableService，复用管理guard在每次严格幂等根含重放内重验并先锁客户端命名空间；status=1与完整clientId绑定Token撤销同保存点/回执，不分页，不删登记/回调或停用website_v3。票据/WS依当前client+Token授权逐次失效，不声称同步跨实例断连；未来重新启用需设计旧码/请求处理。
