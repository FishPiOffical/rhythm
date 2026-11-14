# v3 勋章显示设置实现交接

内部实现记录，2026-10-20。此文档包含安全/实现细节，不应整体复制到公开API文章；末尾独立公开片段可用于主代理合并。

## 已完成契约

`PUT /api/v3/me/medals/{medalId}/display`

- 需要现有 `settings.write`，只从v3 Bearer读取本人；Idempotency-Key必填。
- Content-Type application/json；body严格只有必填布尔字段 `displayed`，例如 `{"displayed":false}`。不接受空body、额外字段、字符串/数字/null布尔或重复JSON键。
- 路径ID对应medal.medal_id，沿存储字符串规则保留原值，不作数字转换/trim；`0`和非数字/Unicode ID均可。非空白、最多128 UTF-16、无ISO控制字符；客户端应对路径段编码。无请求userId字段，也不会从Cookie/apiKey/query userId补身份。
- 设置而非toggle，只改本人已持有有效勋章的display字段；不收费、不改排序、不授予/续期、不改data/其他用户/定义。
- 成功data仅 `{"id":"0","displayed":false}`，沿用v3信封、Cache-Control:no-store、Vary:Authorization。
- 404：当前账号不存在/非有效，勋章定义不存在、本人未持有或已过期。本人已有读取语义保留expire_time<=0永久；正数必须晚于当前时间。
- 409 inconsistent_state：重复定义、同本人+medalId多条持有、存储布尔/到期数字损坏等。拒绝修改，不随机选一行。
- 409 idempotency_conflict：同用户、同operation下，同key改正文或目标ID。
- 503 temporarily_unavailable：存储/提交不可用，响应不输出驱动/SQL/数据原文。
- 相同key回放返回原回执，不覆盖之后新key改变的显示状态。目标之后到期/删除不破坏原成功回执；回放前仍显式重读当前账号，停用/删除账号不能用陈旧认证快照取旧回执。
- 本轮没有新增scope，没有接入勋章排序或改造旧接口。

## 实际新增文件

1. `src/main/java/org/b3log/symphony/processor/ApiV3MedalDisplayProcessor.java`：PUT路由、settingsWrite和ApiV3WriteMidware，严格body/key/路径解析。
2. `src/main/java/org/b3log/symphony/service/ApiV3MedalDisplayService.java`：当前DB账号重验、幂等严格根、事务内actor/定义/本人持有锁、savepoint和display定列更新。
3. `src/main/java/org/b3log/symphony/util/ApiV3MedalDisplay.java`：原样有界路径ID、必填严格布尔、精确存储布尔/到期数值校验。
4. `src/test/java/org/b3log/symphony/service/ApiV3MedalDisplayTest.java`：真实路由/认证/协调器/SQL/保存点/严格提交故障隔离专项。
5. `tools/api-v3-medal-display-test.mjs`：独立Java25专项runner。
6. `docs/api-v3-medal-display-work.md`：本记录与公开文档片段。

未修改总Processor、API.md/UPDATE_LOG.md、AGENTS、计划/进度、旧控制器、旧runner、模板、JS/SCSS、生成产物或数据库结构。主代理总注册加入 `ApiV3MedalDisplayProcessor.register();` 并合并公开文档/更新记录。没有git提交或推送操作。

## 事务与并发边界

`set`在execute前参数校验并显式查当前数据库actor状态0，避免全局认证缓存导致停用账号重放。`ApiV3IdempotencyMgmtService.execute`使用operation `medal.display`，规范请求包含精确medalId与displayed；回执只有目标ID及请求布尔。沿现有ApiV3RequestRepository严格根事务，回执和修改同提交。

首次执行进入 `setInCurrentTransaction`，必须已有活跃事务，在ApiV3TransactionSavepoint内：

1. actor行 `SELECT oId,userStatus ... FOR UPDATE`，当前状态必须0。
2. 定义按medal_id当前读并锁，LIMIT2识别重复，不依赖缓存。
3. 持有按user_id+medal_id当前读并锁，LIMIT2识别重复；重验owner/目标，精确检查expire_time与display。
4. 若值已相同，仍返回成功回执但不UPDATE；否则只执行 `UPDATE ... SET display=? WHERE oId=? AND user_id=? AND medal_id=?`，受影响行必须为1。
5. 保存点成功返回，外层协调器保存回执并严格commit；失败回滚，不独立提交、不收费/通知/审计推送。局部rollback失效则终止根事务。

存储数值使用BigDecimal.longValueExact，禁止Number.intValue/longValue截断：布尔Number必须精确0或1；到期必须是signed BIGINT范围整数。正常JDBC Boolean/整数及原字符串true/false/0/1兼容；小数、越界、NaN、Infinity、null损坏拒绝409。

当前v3锁规则及MySQL范围锁需真实MySQL复验，不能声称与所有旧后台授予/撤销/清理接口均已建立相同锁序。未修改旧自提交setUserMedalDisplay或旧排序。旧路径仍有原行为，不宣称其被本轮原子化。

## 实际专项结果

运行 `node tools/api-v3-medal-display-test.mjs`：**6组PASS**。

1. 真实PUT路由/控制器/ApiV3AuthMidware/写中间件/SQL/幂等协调器：显式设置、ID0及编码非数字Unicode ID、只改display/不改积分排序他人；相同key不覆盖后续状态，目标/正文冲突409；账号停用/删除后原key404，目标到期/删除后原key只回旧回执。
2. 401/403鉴权，Cookie/apiKey/query伪身份，严格JSON字段/类型/重复键、内容类型、key、路径长度；拒绝前无mutation和持久回执；相同值也只成功写回执而不UPDATE。
3. 当前锁账号/定义/本人持有，缺失/过期/停用404，重复持有/定义409，负到期永久存量语义；存储损坏失败无随机选取/额外回执。
4. **实际打包JdbcTransaction** + ApiV3Transactions.strict + 真实协调器，注入实际display UPDATE后异常、回执UPDATE后异常、JDBC提交前拒绝，均回滚display与回执；提交成功但确认丢失时503且数据/回执均已持久，恢复后原key回放不再mutation。
5. **实际ApiV3TransactionSavepoint**：caller捕获异常后提交根也没有部分display修改；回滚保存点失败会回滚整个根；无活跃根禁止primitive。
6. 0.5/1.5/4294967297/超signed BIGINT/NaN/Infinity/null的存储布尔或expiry拒409；正常Boolean/整数及Long.MIN_VALUE精确保留。

隔离适配只有：UserRepository表投影、Token查找、v3响应信封、SQLite去FOR UPDATE方言及SQLite存储连接；实际编译Medal/UserMedal/ApiV3Request Repository、业务service/util/controller、auth/write中间件、协调器、savepoint、transaction statements、严格根，以及真实Latke JdbcTransaction commit/rollback/dispose。通过Unsafe跳过真实JdbcTransaction连接池构造函数，给它临时SQLite连接；其提交回滚方法没有mock。临时文件已由runner清理。编译出现预期Unsafe/UserExt弃用注解与Netty/SQLite运行时警告，无测试失败。

未运行Maven、全应用Java构建、真实MySQL/连接池/锁竞争、8080浏览器、生产、真实Token签发撤销、全局黑名单/验证码实站链路。

## 安全与反漏扫

- 未鉴权/越权：路由必需settings.write Bearer，无匿名写入口；从可信identity得到actor，数据库两次有效状态校验，当前锁持有按owner+目标，不能请求其他用户。
- CSRF：新入口只Bearer，不继承Cookie/apiKey，不改旧兼容入口。没有给旧API添加破坏客户端兼容的CSRF中间件。
- XSS/敏感信息：响应只id/displayed，无HTML、raw medal_attr/data、用户对象、余额、联系信息或内部持有ID。ID作为JSON数据且长度/控制字符受限，消费端须按字符串/URL编码使用。
- SQL注入：外部actor/id/布尔全部参数绑定，表名只Repository#getName。字符不做新业务白名单，非数字ID正常支持。
- SSRF/上传/下载：无远程请求、无URL输入/静态资源/上传下载能力。
- 批量滥用：沿用现有写门禁；没有批量写，定义/持有每次最多锁2条，重试受现有协调器限制。大表索引/跨旧链路争锁需MySQL复验。
- 匿名/验证码：无新匿名入口、未改anonymous.viewSkips/BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor链路；本专项只验证新PUT认证，不能声称已实测黑名单/test/validateCaptcha解封。
- CDN版本/sourceMappingURL/目录枚举/上传下载路径：本轮无新公网资源URL或静态目录，不能替代主代理整轮全仓发布审查。

## 完整人工测试链路

**首先由用户通过IDEA重新编译或重启服务端，未确认前不要把8080旧响应当新代码失败。** 本轮无需数据库结构迁移，但需既有v3幂等表/Token基础部署前提。

1. 用现有后台给测试用户A授予永久有效勋章，记录定义medal_id（包含0及非数字样例）、持有display、order、积分与data；B也持有同勋章。通过PC/mobile本人勋章面板及公开主页确认当前显示。
2. 用A的settings.write Bearer和新Idempotency-Key，PUT `/api/v3/me/medals/0/display` + `{"displayed":false}`，返回id0/displayed false。GET `/api/v3/me/medals`显示本人隐藏持有，GET `/api/v3/users/A用户名/medals`及PC/mobile公开主页不展示；B、积分、排序、data不变。新key设true后公开恢复。非数字ID需对路径段编码。
3. K1设false、K2设true，再重放K1false：返回原false回执，实际GET仍true，不重新隐藏。K1改true或改目标ID返回409；同值新key成功但不额外修改排序/积分。
4. 无Bearer/坏Bearer401，只有profile.read403；Cookie或旧apiKey不能代替，query userId不覆盖A，body加入userId422。空body、displayed缺失/null/数字/字符串、重复JSON键、超128ID、坏key、非JSON类型均拒绝且无修改/回执。
5. 未持有/已到期/已删除定义404；停用/删除A后用旧token/key不能取旧回执。反之，A仍有效时原成功目标之后到期/删除，重放旧key应只取回执，不再改持有；首次新key对同目标仍404。存量负expiry保持本人永久规则。
6. 在本地测试数据制造同user+medalId双持有或重复定义，返回409且不随机改行；恢复后同key可再试。损坏display/expiry小数/越界应409，不被截断为true/永久。
7. 通过隔离runner验证UPDATE后故障/回执故障/提交前拒绝均无部分状态；提交后确认丢失只能靠原key恢复，不用新key盲重试。检查no-store/Vary及无敏感响应字段。真实MySQL可另做并发同key/不同key/后台授予撤销竞争。
8. 回归旧PC/mobile显示设置与读取，不影响旧排序；再按整轮要求检查未登录拦截、有效Bearer放行、黑名单/test/验证码解封、anonymous.viewSkips、公开API鉴权、静态CDN/sourceMappingURL/目录枚举/上传下载路径。未实测项在最终回复保留。

## 可供合并的公开API.md片段

### 设置勋章显示

`PUT /api/v3/me/medals/{medalId}/display`

设置本人有效勋章是否展示。需要 `settings.write`，设置操作不会改变勋章顺序。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| medalId | 路径参数，勋章ID；非数字ID也支持，路径段需URL编码 | 0 |
| Authorization | Bearer凭据 | Bearer access_token |
| Idempotency-Key | 本次操作唯一键 | medal-display-20261020-1 |
| displayed | 必填布尔值，true显示，false隐藏 | false |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/me/medals/0/display' \
  -H 'Authorization: Bearer access_token' \
  -H 'Idempotency-Key: medal-display-20261020-1' \
  -H 'Content-Type: application/json' \
  -d '{"displayed":false}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 设置结果 | object |
| - id | 勋章ID | 0 |
| - displayed | 本次设置的显示状态 | false |

> 同一幂等键及相同目标和内容返回首次结果，不覆盖后续操作；相同键用于不同目标或内容返回409。不存在、未持有或已过期的勋章返回404。

## 可供合并的UPDATE_LOG.md记录

- 新增 `PUT /api/v3/me/medals/{medalId}/display`：使用 `settings.write` 设置本人有效勋章显示状态，支持 `Idempotency-Key`；不改变勋章顺序。请求字段为必填布尔 `displayed`，返回勋章ID和本次显示设置。

主代理最终补验：路径ID用ApiV3Json.canonical拒绝孤立UTF-16代理项422，正常emoji字符串保持；仍为6组专项并入输入断言。旧MedalService显示/排序写入仍是整行快照和普通commit，本批未修改，不承诺其与新写入并发时没有覆盖；真实MySQL旧授予/续期/排序竞争须实测。
