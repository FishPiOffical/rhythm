# v3 主职业设置工作包（2026-10-20）

## 实际交付文件与总接线

- `src/main/java/org/b3log/symphony/processor/ApiV3ProfessionPrimaryProcessor.java`：独立PUT注册、settings.write、共享写门禁、JSON白名单及Idempotency-Key。
- `src/main/java/org/b3log/symphony/service/ApiV3ProfessionPrimaryService.java`：当前账号复核、目标/隐私锁、按列选择更新、首次隐私初始化、版本校验、同根保存点及统一幂等协调。
- `src/test/java/org/b3log/symphony/service/ApiV3ProfessionPrimaryTest.java`：八组真实入口/SQL/事务故障专项。
- `tools/api-v3-profession-primary-test.mjs`：Java25隔离编译及SQLite执行，临时目录自动清理。
- 本文：契约、验证、安全边界与可合入公开文档片段。

未修改旧入口、公共ApiV3Processor、API.md、UPDATE_LOG.md、AGENTS.md、progress、plan、共享runner、Repository或SQL迁移。由主代理总接线：

```java
ApiV3ProfessionPrimaryProcessor.register();
```

源码注册：`ApiV3ProfessionPrimaryProcessor.java` 的 `Dispatcher.put("/api/v3/me/professions/primary", p::set, auth::settingsWrite, writes::handle)`。

## 精确契约与行为

`PUT /api/v3/me/professions/primary`，仅settings.write Bearer，必须有Idempotency-Key；JSON只接受字符串professionId（1至19位ASCII数字），不trim或自动转换数字/空值，不接受userId、隐私、状态、费用、版本或其他字段。账号来自Bearer；Cookie/query apiKey/userId不能替代。

可选任何当前PUBLISHED职业，无需已有经验、等级或当前选择；没有费用/积分操作、不创建或改写任何user_profession进度。当前账号已失效/目标不存在、DRAFT或RETIRED为404。身份/权限失败401/403；字段/ID/key等非法422（非法JSON/重复字段按共用协议返回400）；同key不同内容409；存储故障503。

幂等operation为professions.primary，摘要只含professionId。每次进入execute前直接SQL复核账号当前有效，避免Token缓存使停用账号重放成功；首次应用事务内再次当前读锁账号。成功回执仅onboardingState=SELECTED与primaryProfessionId。

相同key/正文重放原回执且不再次更新选择或版本，不覆盖后续主职业。重放不重验目标发布状态，因此原目标后来退休/删除仍可返回原回执；当前账号失效时重放依然拒绝。新key重复选择当前职业沿用旧版本规则：每次成功应用都增加一次userProgressVersion。

现有隐私记录只更新onboardingState、primaryProfessionId、userProgressVersion与updatedAt。userPrivacyVersion、publicStatsVersion、privacyPreset、moduleVisibilityJson、createdAt、owner/ID、全部进度/经验/积分保持。缺失隐私时创建旧默认ALL_PUBLIC和十个PUBLIC模块，进度版本初始化1，隐私/公开统计版本0，选择状态SELECTED；不调用旧默认写/旧updateState。

userProgressVersion必须是非负整数且未到Long.MAX_VALUE，否则503且不保留回执或部分选择。选择只沿用旧progress版本失效规则，不主动提升其他版本、改用户/会话缓存或发布事件。

## 事务与锁序

共享 `ApiV3IdempotencyMgmtService` / `ApiV3RequestRepository` 提供严格根事务；回执与业务同提交。请求行锁/预留后，当前actor用户→目标profession→本人privacy；锁查询均使用当前读FOR UPDATE和字段投影。mutation显式要求已有事务，再通过 `ApiV3TransactionSavepoint` 包裹全部当前锁及写入；用 `ApiV3TransactionStatements` 在当前连接参数化INSERT/UPDATE，不开启/提交新根事务。

局部失败可回滚到保存点；无法回滚保存点时通用工具中止整根，禁止捕获后提交部分结果。根回执写入失败或严格commit失败由共同协调器整根回滚。幂等根在RepositoryException时重试，可处理首次隐私创建与职业增长竞争userId唯一索引的失败；不把MySQL锁行为当作已实测。

已核旧增长EffectApply为effect→user_profession→奖励/通知→privacy；退休为profession→user_profession→privacy→通知。新选择不触碰progress、奖励/积分/通知，避免引入这些资源的反向依赖。只持有用户/目标职业/隐私，不在锁后打开外部请求。

**旧入口并发限制保留**：旧ProfessionPrivacyMgmtService.updateState普通find后整行update，没有同等当前锁，旧privacy/skip等并发可能用旧快照覆盖新选择或版本。本批按明确要求不改旧入口，不能宣称新旧职业写入整体原子/跨实例竞争已解决。后续同步加固须先确认旧入口兼容方案。

## 实际验证

`node tools/api-v3-profession-primary-test.mjs` 最终八组PASS；`node --check`通过。

执行真实生产Processor/Service、ApiV3AuthMidware/hasScope、ApiV3WriteMidware、ApiV3WriteInputs/Requests、幂等协调器、保存点/当前事务Statements、严格提交适配器及随项目打包的JdbcTransaction。SQLite使用临时文件和独立observer连接；Repository SQL访问、Token查询、响应信封、连接池构造为显式适配器；仅为SQLite移除FOR UPDATE语法。JdbcTransaction只跳过池构造，commit/rollback/dispose为实际打包方法，连接proxy用于真实JDBC提交/保存点故障注入。

八组覆盖：

1. 真实PUT注册/控制器/门禁→可选无progress的已发布职业、默认隐私、免费及无新增progress、锁顺序、选择版本+1、旧key不覆盖后续选择、目标退休后重放成功、账号停用后旧key拒绝。
2. 现存CUSTOM隐私、其他版本和createdAt/owner不变；新key同选择按旧规则计次；已提交增长版本保留；两账号写入隔离。
3. 无效/缺Bearer、缺scope、Cookie/apiKey/参数伪身份、非字符串/空/额外字段/重复JSON/非法ID/key、未发布/退休/不存在目标、失效账号拒绝且无回执。
4. 新/既有隐私两种情况，execute前复核、placeholder、各当前读、业务SQL执行后、回执执行后、commit前拒绝、setSavepoint及releaseSavepoint失败均无业务/回执残留；原key恢复后可成功。
5. 真实JDBC commit成功但确认丢失→503；随后新key改选，再重试旧key仍只读原回执，不多计版本、不覆盖新选择。
6. primitive无事务拒绝、不提交调用方根；业务执行后失败被捕获再提交只保留无关工作；保存点回滚失败中止整个根且释放ThreadLocal。
7. 负数/溢出版本拒绝，原选择及回执保持。
8. 模拟其他增长事务先赢得首次隐私创建后，本根重试读取新状态，保留其隐私/进度与统计版本；primitive独立锁读失效actor也拒绝。

第8组是按顺序注入另一根已提交状态，不是MySQL多连接并发证明。未执行完整应用编译/IoC、真实DB/8080/生产/浏览器/Maven或Git提交，不以隔离通过冒充运行验收。

## 安全与反漏扫

- 身份仅Bearer/settings.write；参数不可指定owner/费用/隐私，数据与回执白名单。失效账号复核在重放前、当前锁检查在首次应用内。
- 纯Bearer写入不继承Cookie授权；继续共享写预算与幂等。无新增匿名入口或anonymous.viewSkips例外，不改BeforeRequestHandler、AnonymousViewCheckMidware、validateCaptcha或黑名单链。
- SQL表名来自Repository，值参数化；ID/body/key有界。无用户URL/远程请求、HTML/Markdown渲染、文件/上传下载地址、静态资源、CDN/sourceMappingURL/目录新增；响应/警告日志不含隐私JSON、内部记录或驱动异常。
- 未实际回归全站匿名首访/计次/黑名单/test/验证码解封、生产风控开关、静态/CDN版本/source map/目录/上传下载返回路径；本批不是全量发布检查。

## 部署与人工完整链路

本批新增Java，联调前必须用户通过IDEA重新编译或完整重启，并由主代理接入总register；不假设已生效。依赖已有职业表/userId唯一索引与api_v3_request迁移，不新增表。

1. PC/mobile登录→GET /api/v3/professions取得已发布ID→GET /me/professions保存当前选择/隐私/经验/积分→携settings.write和新Idempotency-Key PUT选择一个尚无经验的职业→回执只有SELECTED/主职业ID→GET核对state已选。旧PC/mobile职业页刷新一致，progress不新增零级，积分/隐私/现有经验不变。
2. 相同key/正文重复→相同回执，选择版本不再增长；另一个key改选B→重试A旧key，回执仍A但GET与旧页面仍B。相同key换body409且不写入；新key选择当前B按旧规则增加版本一次。目标后来退役/删除后旧key仍能回放原成功，不应改写当前选择。
3. A/B分别Token请求，query userId/Cookie/apiKey不能代替Bearer；单独Cookie/apiKey401，缺settings.write403，body指定owner/额外隐私/状态、numeric/null/空白/20位ID/缺key等拒绝；DRAFT/RETIRED/不存在目标404。停用后首次/旧key重放都拒绝。
4. 对有CUSTOM/SELF_ONLY/FULLY_HIDDEN状态的测试账号改选，完整对照所有隐私模块和版本/createdAt保留，只有选择相关列及userProgressVersion/updatedAt变化；无隐私测试账号只创建标准默认记录。
5. MySQL测试环境并发选择/职业增长首次创建/增长计数/退休，核对无部分回执、版本不丢及无重复隐私；旧privacy/skip整行覆盖风险须单独复现/评估，不能要求本批未改旧入口承诺并发安全。
6. 对一次性测试环境注入业务SQL、回执、提交前和提交后确认丢失：失败不返回成功；前者无部分写入，确认丢失后按原key重试恢复。校验保存点捕获场景不提交部分选择，真实连接/锁/死锁/资源释放和两端体验需实测。
7. 继续全局匿名盾、黑名单/解封、匿名跳过配置与静态/CDN/目录/上传下载反漏扫，不因新入口要求Bearer就跳过全站发布验证。

## 可直接合入API.md的公开片段

### 设置本人主职业

`PUT /api/v3/me/professions/primary`

选择一个已发布职业作为本人主职业，需要 settings.write。无需已有职业经验，不消耗积分。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次选择重试保持不变 | profession-primary-001 |
| professionId | 职业ID字符串，1至19位数字 | 123 |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/me/professions/primary' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: profession-primary-001' \
  --header 'Content-Type: application/json' \
  --data '{"professionId":"123"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 本次选择结果 | `{...}` |
| - onboardingState | 选择状态 | SELECTED |
| - primaryProfessionId | 本次选择的主职业ID | 123 |

> 只修改主职业选择，不修改职业隐私或已有进度，也不会为所选职业创建经验记录。同一Idempotency-Key和请求内容重试返回原结果，不覆盖之后的新选择；相同键改用其他职业返回409。当前状态请通过GET /api/v3/me/professions查询。

## 可合入UPDATE_LOG的公开记录

- 新增 `PUT /api/v3/me/professions/primary`：支持免费选择已发布主职业，需要settings.write及Idempotency-Key；不要求已有经验，不改变隐私或进度，重复请求不会覆盖后续选择。
