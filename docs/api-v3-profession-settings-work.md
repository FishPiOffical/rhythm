# v3 职业引导与隐私设置交接（2026-10-22）

内部工程记录，不能整体复制到公开文档。主代理统一总注册、API.md、UPDATE_LOG.md、AGENTS.md及进度文档。

## 实际文件与接线

- `src/main/java/org/b3log/symphony/processor/ApiV3ProfessionSettingsProcessor.java`：两个PUT，settings.write及共享写门禁，body在解析前最多4096字节，字段白名单。
- `src/main/java/org/b3log/symphony/service/ApiV3ProfessionSettingsService.java`：独立幂等operation、每次及重放当前锁actor，跳过/隐私列更新、默认行、版本与回执同严格根事务及保存点。
- `src/main/java/org/b3log/symphony/util/ApiV3ProfessionSettingsInputs.java`：精确SKIPPED、六预设、十模块与四范围规范化。
- `src/test/java/org/b3log/symphony/service/ApiV3ProfessionSettingsTest.java`：生产HTTP/auth/write/SQL/事务及交替PrimaryService专项，复用现有PrimaryTest的SQLite/JDBC故障fixture。
- `tools/api-v3-profession-settings-test.mjs`：Java25隔离编译与运行，不启动应用，不连接实际业务DB。
- 本文件：契约、实际证据、风险与完整人工步骤。

总接线新增：`ApiV3ProfessionSettingsProcessor.register();`。无需新SQL/Repository表、资源元数据或前端构建。未修改旧ProfessionPrivacyMgmtService、PrimaryService或原查询。

## 确认的行为与字段隔离

1. `PUT /api/v3/me/professions/onboarding`只接受`{"onboardingState":"SKIPPED"}`；沿旧规则，无前置状态限制，即使已选择也清空primaryProfessionId。只写onboardingState/primaryProfessionId/userProgressVersion/updatedAt；新行默认ALL_PUBLIC，其余版本为0。没有恢复UNDECIDED/选择SELECTED入口。
2. `PUT /api/v3/me/professions/privacy`完整替换隐私；六预设ALL_PUBLIC/LEVEL_ONLY/PRIMARY_ONLY/SELF_ONLY/FULLY_HIDDEN/CUSTOM。LEVEL_ONLY与PRIMARY_ONLY沿旧实现均仅primaryProfession为PUBLIC、其余HIDDEN，不能暗改两者差异。SELF_ONLY全SELF，FULLY_HIDDEN全HIDDEN，ALL_PUBLIC全PUBLIC。
3. CUSTOM必须完整十模块：primaryProfession、allProfessions、experience、rarity、majorContributions、contributionStats、activityFeed、levelHistory、rewards、ranking；每值精确PUBLIC/LOGGED_IN/SELF/HIDDEN。非CUSTOM可不传map，若传则校验对象/受支持键/受支持值及大小后忽略，实际仍按预设保存。规范化后参与幂等哈希，忽略map不制造不同请求。
4. 隐私只写privacyPreset/moduleVisibilityJson/userPrivacyVersion/publicStatsVersion/updatedAt，保留主职业/引导/progressVersion；新行默认UNDECIDED/空主职业及progressVersion=0。
5. 根内每次尝试和重放先当前锁有效actor，再锁回执，mutation再当前锁actor→privacy；保存点覆盖业务写。版本仅接受非负精确long且不能溢出，重复privacy行/损坏版本503，不随机选一条。无扣费、经验变化、假进度、全行用户或隐私覆盖，无提交前外部缓存/事件。
6. 同key同规范请求恢复旧结果，不覆盖之后的主职业/隐私；同key不同规范请求409。新key重复同状态仍沿旧版本递增规则。onboarding与privacy operation隔离。

## 实际测试证据

`node tools/api-v3-profession-settings-test.mjs`：2026-10-22 Java25 **11组通过，进程退出0**，日志`/tmp/rhythm-v3-profession-settings.log`。

1. 实际两PUT注册表达式、Dispatcher/RouteHandler、Bearer/scope、写门禁、控制器、SQL与默认状态；无积分/经验变更。
2. 已选后skip清主；只按列更新；旧skip回执不会清除之后通过实际PrimaryService选中的主职业。
3. 精确六预设/CUSTOM/规范化、版本隔离与旧隐私回执不覆盖新设置。
4. 首次隐私创建默认选择状态，两个actor不串数据。
5. Cookie/apiKey/owner伪造、缺Bearer/scope/key、重复JSON/多余字段/非法enum/map、ASCII与UTF-8字节上限。
6. 每次与重放在严格根当前锁actor；模拟identity→begin之间停用仍拒绝，不继承旧身份或回执。
7. 新建/已有行的两个业务：placeholder、用户/隐私读、业务写、回执写、实际JDBC提交/保存点创建/释放失败均回滚状态和回执，同key恢复成功。
8. 实际提交已完成但确认丢失，原key恢复只回旧结果，不重复加版本或覆盖后续主职业/隐私。
9. primitive要求外层事务、不自行commit；局部失败保存无关写；保存点回滚失败则根整体终止。
10. 重复privacy owner、非数/小数/负值/long溢出版本失败不写。
11. 实际PrimaryService与Settings交替：选择→隐私→skip→再选择→重放旧skip→新隐私→重放旧隐私；各自版本与新值正确保留。

框架JDBC/事务、生产service/SQL/auth/controller为真实代码；SQLite移除FOR UPDATE、身份Token查找、仓储连接、信封及路由绑定为隔离适配。不同fixture使用不同测试IP，避免批量故障用例误触共享生产预算；并未移除生产预算。未运行Maven、完整IoC/业务MySQL、8080、浏览器或生产。

## 完整人工链路

联调前须用户通过IDEA重新编译或重启，不能将旧服务行为当作本次代码结果。

1. A/B登录获取settings.write和profile.read；GET本人职业状态→A通过主职业PUT选择已发布职业→PUT privacy SELF_ONLY→GET状态与PC/mobile页面核对，选择/经验不变、隐私生效、B不受影响。
2. PUT onboarding SKIPPED→返回SKIPPED/空primary，GET和两端页面均不再选主；隐私仍SELF_ONLY。再选择职业→重放旧skip key→旧结果可返回但GET/页面保留新主职业。新key skip则按规则清空。
3. 逐一设置五个预设及完整CUSTOM，GET对照十模块。LEVEL_ONLY/PRIMARY_ONLY均只有primary公开；CUSTOM四种范围在已有公开职业页面按当前身份生效。设置新隐私后重放旧key不覆盖；同key不同有效请求409。
4. 缺Bearer/仅Cookie/apiKey401、缺scope403；缺key/额外owner/state字段/错枚举/缺CUSTOM模块422，重复JSON400、超过4096字节413；新/旧key在账号停用后均404，无写入。非CUSTOM附带合法部分map按预设而非map保存，非法map仍422。
5. 开发测试库注入数据/回执/保存点/提交失败，无部分设置；提交确认丢失后以原key恢复。与主职业、职业增长、旧页面privacy并发验证MySQL锁/版本，核对不吞并其他已提交值。
6. 回归黑名单/test→validateCaptcha、匿名首访/计次、anonymous.viewSkips及开发关闭盾；复核公开API鉴权、日志、静态/CDN/sourceMappingURL/目录/上传下载路径。此处为待验步骤，本轮未做全站网络扫描。

## 已知限制与反漏扫

旧ProfessionPrivacyMgmtService仍以快照全行update且未共用本次锁边界，可能覆盖并发新值；本次只保证两个新入口与Primary的按列事务行为，不能声称新旧设置整体原子化。原PrimaryService的重放只事务前重查actor，本工作包未顺手更改。SQLite故障注入不证明MySQL多连接竞争、死锁处理或完整IoC启动。

两个新入口均Bearer+scope，无新增匿名入口/anonymous.viewSkips/Cookie身份写入，不修改黑名单与验证码链。owner只来自认证，输入/响应白名单、参数绑定SQL，无HTML渲染、远程抓取、SSRF、上传下载或新增静态/CDN/目录。共享预算保留，实际压力/分布式门禁和全站反漏扫仍未验收。

## 公开文档片段

### 跳过职业引导

`PUT /api/v3/me/professions/onboarding`

需要Bearer accessToken、settings.write与Idempotency-Key。跳过引导并清空已选择的主职业。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，幂等键 | profession-skip-001 |
| onboardingState | 必填，仅SKIPPED | SKIPPED |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/professions/onboarding' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: profession-skip-001' \
  --header 'Content-Type: application/json' \
  --data '{"onboardingState":"SKIPPED"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 设置结果 | `{...}` |
| - onboardingState | SKIPPED | SKIPPED |
| - primaryProfessionId | 已清空主职业 | 空字符串 |

> 已选择职业时同样会清空选择，不改变经验或隐私。同键重试恢复原结果，不覆盖之后的新选择；无效字段或状态返回422。请求体最多4096字节。

### 设置职业隐私

`PUT /api/v3/me/professions/privacy`

需要Bearer accessToken、settings.write与Idempotency-Key，完整替换职业隐私。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，幂等键 | profession-privacy-001 |
| preset | ALL_PUBLIC、LEVEL_ONLY、PRIMARY_ONLY、SELF_ONLY、FULLY_HIDDEN、CUSTOM | SELF_ONLY |
| moduleVisibility | CUSTOM时必填完整十模块对象；其他预设可省略 | `{...}` |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/professions/privacy' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: profession-privacy-001' \
  --header 'Content-Type: application/json' \
  --data '{"preset":"SELF_ONLY"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 设置结果 | `{...}` |
| - privacyPreset | 保存的预设 | SELF_ONLY |
| - moduleVisibility | 规范化后的完整模块范围 | `{...}` |
| -- primaryProfession | 主职业 | SELF |
| -- allProfessions | 所有职业 | SELF |
| -- experience | 经验 | SELF |
| -- rarity | 稀有度 | SELF |
| -- majorContributions | 主要贡献 | SELF |
| -- contributionStats | 贡献统计 | SELF |
| -- activityFeed | 活动记录 | SELF |
| -- levelHistory | 等级历史 | SELF |
| -- rewards | 奖励 | SELF |
| -- ranking | 排行榜 | SELF |

> 范围仅接受PUBLIC、LOGGED_IN、SELF、HIDDEN。CUSTOM须完整提交上述十模块；其他预设按预设保存，附带合法moduleVisibility不会覆盖预设。LEVEL_ONLY和PRIMARY_ONLY目前均只公开主职业、隐藏其余模块。无效字段/枚举或不完整CUSTOM返回422，请求体最多4096字节。
>
> 同键同规范请求恢复原结果，不覆盖后续隐私设置；同键不同有效请求返回409。该接口不改变主职业、引导状态或经验。
