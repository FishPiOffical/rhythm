# v3 职业只读工作包（2026-10-20）

## 已实现与总接线

新建以下文件，未修改旧职业控制器/服务、公共注册、scope、数据库结构、前端或公共文档：

- `src/main/java/org/b3log/symphony/processor/ApiV3ProfessionProcessor.java`：两条GET及各自门禁。
- `src/main/java/org/b3log/symphony/service/ApiV3ProfessionQueryService.java`：投影SQL、分页、当前本人状态/隐私和白名单DTO。
- `src/test/java/org/b3log/symphony/service/ApiV3ProfessionQueryTest.java`：六组真实SQL/控制器/路由专项。
- `tools/api-v3-profession-test.mjs`：Java25隔离编译与SQLite执行，临时目录自动清理。
- 本文：交付说明、公开文档片段和人工测试。

由主代理在 `ApiV3Processor.register()` 中加入：

```java
ApiV3ProfessionProcessor.register();
```

| 方法/路径 | 门禁 | 内容 |
| --- | --- | --- |
| GET /api/v3/professions | optionalProfile + anonymous | 公开已发布目录；有Bearer时必须有效并有profile.read；分页p/size（默认20，最大50） |
| GET /api/v3/me/professions | profile.read Bearer | 当前有效本人的选择/隐私状态和分页职业进度；Cookie/apiKey/请求userId不赋予身份 |

## 核对后的规则

目录沿用 `ProfessionCatalogQueryService#getPublished` 的职业发布边界，没有按经验/等级解锁职业目录的条件。当前稳定定义与当前版本必须都PUBLISHED、且版本绑定该职业后，才参加计数/分页；按sortOrder和ID稳定排序。只给职业ID、编码、名称、简称、说明；不输出presentation JSON、版本ID、创建/发布人、审核/自动化/奖励指南。

本人接口读取实际 `user_profession` 记录，按经验降序、职业ID升序分页；不接受目标用户参数。历史展示版本沿用本人记录中的professionRevisionId/levelSchemeId，而非替换成最新名称/等级方案。已核 `ProfessionDefinitionMgmtService#publishProfession` 会通过 `ProfessionDefinitionStateService#retireRevision` 将旧版本置RETIRED，新版本PUBLISHED，两种合法状态均允许本人读取；DRAFT/损坏版本失败503。

本人FULLY_HIDDEN/SELF_ONLY不禁止其读取自己的进度和隐私配置。未存隐私记录时返回旧默认UNDECIDED/空主职业/ALL_PUBLIC及十个PUBLIC模块；GET不创建记录、不更新版本/时间。输出state包含onboardingState、primaryProfessionId、primaryProfessionState、privacyPreset、moduleVisibility；不输出owner/隐私记录ID、修订号或时间。

**list只包含实际已生成的进度记录。** 已选主职业但还没有经验记录时，state.primaryProfessionId及ACTIVE状态仍正确，list可为空、也可没有该职业。此API不调用旧catalog.zeroProgress生成零级快照，不能用list缺项判断尚未选择。主职业状态为NONE/ACTIVE/RETIRED/DELETED。关联等级/历史版本损坏、隐私缺失字段/重复行/非法枚举、负经验或查询异常均失败503而不输出部分数据。当前账号在读取前后都查有效状态，途中被停用则404。

当前没有他人职业资料或职业排行新入口；不调用可能包含来源、授予记录/内部ID的旧公开聚合服务，避免绕过十模块隐私。说明/名称按JSON文本交付，客户端作为文本显示。

## 已执行验证

`node tools/api-v3-profession-test.mjs`：Java25隔离专项六组PASS。执行真实生产Processor/Service、ApiV3AuthMidware/hasScope、ApiV3Requests、Repository类型及SQL；SQLite执行带表前缀/参数绑定的查询。Token查询、用户Repository类型、响应信封和AnonymousViewCheckMidware由显式适配器提供，匿名适配器记录/拦截钩子，用于验证实际注册语句顺序及缺凭据/错误scope不越过鉴权；没有假称执行生产验证码/IP盾。

覆盖：

1. 目录发布/版本状态和绑定在计数及分页前过滤；顺序、第二页、越界空页、字段白名单。
2. 只读本人状态、隐藏隐私下本人可读、他人隔离、历史名称/等级/经验快照、主职业ACTIVE/RETIRED/DELETED、未存隐私不写库、已选择无progress不伪造。
3. ID/分页边界、无效/停用账号、损坏JSON/未知模块范围和重复隐私记录失败。
4. 负经验、DRAFT历史版本、缺失等级不返回部分进度。
5. 本人六个查询阶段及目录两个阶段故障→503；最后一次账号重验拒绝途中停用。
6. 两条生产Dispatcher注册、真实auth/controller：匿名目录可读且经过钩子；钩子拦截时零SQL；显式错误Token401、缺profile.read403；Cookie/query apiKey及伪userId不替代本人Bearer；no-store/Vary；分页错误及HTTP503不暴露驱动错误。

`node --check tools/api-v3-profession-test.mjs`通过。仅涉及新Java读取，不需yarn构建，未运行Maven/完整应用编译、真实MySQL/HTTP/8080、PC/mobile浏览器、生产或Git提交。

## 安全与运行验收边界

两GET无新增写入/CSRF副作用；owner只认Bearer；ID与分页受限、SQL参数绑定、仓储表名来自框架、DTO手工白名单；不返回来源/授予/联系方式/IP/UA等，不请求用户URL、渲染HTML、生成上传/下载地址或公开静态目录。请求不提供排序SQL/用户身份覆盖能力。公开目录显式挂匿名门禁，未改变BeforeRequestHandler、CaptchaProcessor、anonymous.viewSkips、CDN、sourceMappingURL或静态资源。

这些是源码/隔离证据，**未验证**：生产匿名盾首访/计次/黑名单/test/validateCaptcha解封、开发关闭盾差异、真实MySQL约束/查询计划/并发、多实例、完整IoC/服务重启、日志采集、浏览器安全渲染、全站静态/CDN版本/source map/目录与上传下载反漏扫。不能称整个发布检查或职业模块全链路已完成。

## 人工完整链路

Java变更必须先由用户通过IDEA重新编译或完整重启服务端，再确认新路由生效；当前代码仅定义专用register，主代理须先总接线。

1. 开启匿名风控，从PC/mobile进入职业选择页面作为基线；用无Authorization读取GET /api/v3/professions：只见已发布目录；p=1/size=1→p=2→超页，排序与total一致；草稿/停用/版本错绑不出现。显式无效Bearer401、无profile.read403、p=0/size=51为422。
2. v3登录取得profile.read Token→GET /me/professions→和本人旧职业页核对选择、十模块隐私及真实进度；FULLY_HIDDEN/SELF_ONLY仍可读本人。无隐私/无进度账号返回默认state及空list，确认GET前后无新增记录；已选但未产生进度，state保持已选且list不强插零级。
3. 用A/B各自Token读本人，query/body/页面Cookie伪造对方ID不起作用；Cookie/apiKey单独调用本人入口401，停用账号Token拒绝。分页只含本人记录，公开目录不带任何用户数据。
4. 发布同职业新版本/新等级方案后，核对本人历史进度仍显示其关联的历史名称/等级；退役职业仍可读本人历史，目录消失；主职业退役/删除后对应状态正确。不从目录缺项反推本人记录丢失。
5. 测试数据环境分别损坏隐私JSON/枚举、历史版本/等级及注入查询故障，确认503且无内部错误/配置/授予信息；恢复后可读取。请求期间停用账号不得返回owner数据。真实并发与MySQL计划需实测。
6. 盾开启环境覆盖目录首次/计次验证码、黑名单→/test→validateCaptcha解封及开发关闭盾的差异；检查所有返回字段白名单和PC/mobile将名称/说明作为文本处理。

## 后续缺口与具体规则问题

- 本人详情、贡献/活动/等级历史/奖励及他人职业资料/排行未开放；后者须显式接十模块隐私、当前目标有效性及来源内容可见性，不能裸复用旧JSON。
- 本轮遵循只读范围，不调用旧 `ProfessionPrivacyMgmtService` 的自提交写入。后续需独立InCurrentTransaction/严格提交/幂等核心，不把旧普通commit当成功证据。
- 旧skip会把状态改SKIPPED并清空主职业，即使已选择；v3是保留此行为、还是只允许未决定时跳过，需要确定契约。
- 旧selectPrimary允许任何已发布职业、无需既有经验且无收费；若v3沿用该行为应明确记录，不自行加费用/经验/次数门槛。
- 旧CUSTOM隐私要求十模块完整配置；v3是完整替换还是部分更新需先确定，不能擅自重置未提交模块。

## 可由主代理直接合入API.md的公开片段

以下只有公开契约，不含内部风控/SQL/审计细节。

## API v3 职业成长

### 获取职业目录

`GET /api/v3/professions`

读取已发布职业目录。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken（可选）；提供时需要 profile.read | Bearer YOUR_ACCESS_TOKEN |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20，最大50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/professions?p=1&size=20'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 目录分页 | `{...}` |
| - list | 职业列表 | `[{...}]` |
| -- professionId | 职业ID | 123 |
| -- professionCode | 职业编码 | writer |
| -- displayName | 名称 | 写作者 |
| -- shortName | 简称 | 写作 |
| -- description | 说明文本 | 通过创作成长 |
| - pagination | 分页信息 | `{...}` |
| -- p | 当前页码 | 1 |
| -- size | 每页条数 | 20 |
| -- total | 总条数 | 8 |
| -- pageCount | 总页数 | 1 |

### 获取本人职业状态与进度

`GET /api/v3/me/professions`

读取本人职业选择、隐私设置及已有职业进度，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| p | 进度页码，默认1 | 1 |
| size | 进度每页条数，默认20，最大50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/professions?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 本人状态及分页进度 | `{...}` |
| - state | 本人选择及隐私 | `{...}` |
| -- onboardingState | UNDECIDED、SKIPPED或SELECTED | SELECTED |
| -- primaryProfessionId | 已选主职业ID，未选择为空 | 123 |
| -- primaryProfessionState | NONE、ACTIVE、RETIRED或DELETED | ACTIVE |
| -- privacyPreset | ALL_PUBLIC、LEVEL_ONLY、PRIMARY_ONLY、SELF_ONLY、FULLY_HIDDEN或CUSTOM | ALL_PUBLIC |
| -- moduleVisibility | 十个职业模块的可见范围 | `{...}` |
| --- primaryProfession | 主职业可见范围 | PUBLIC |
| --- allProfessions | 全部职业可见范围 | PUBLIC |
| --- experience | 经验可见范围 | PUBLIC |
| --- rarity | 稀有度可见范围 | PUBLIC |
| --- majorContributions | 主要贡献可见范围 | PUBLIC |
| --- contributionStats | 贡献统计可见范围 | PUBLIC |
| --- activityFeed | 活动记录可见范围 | PUBLIC |
| --- levelHistory | 等级历史可见范围 | PUBLIC |
| --- rewards | 奖励可见范围 | PUBLIC |
| --- ranking | 排名可见范围 | PUBLIC |
| - list | 已有进度，按经验降序、职业ID升序排列 | `[{...}]` |
| -- professionId | 职业ID | 123 |
| -- professionCode | 职业编码 | writer |
| -- displayName | 进度对应版本的职业名称 | 写作者 |
| -- shortName | 简称 | 写作 |
| -- description | 说明文本 | 通过创作成长 |
| -- state | ACTIVE或RETIRED | ACTIVE |
| -- totalExperience | 当前累计经验 | 150 |
| -- levelCode | 当前等级编码 | L2 |
| -- levelName | 当前等级名称 | 熟练 |
| -- levelDescription | 当前等级说明文本 | 持续创作 |
| -- requiredExperience | 当前等级所需累计经验 | 100 |
| - pagination | 进度分页 | `{...}` |
| -- p | 当前页码 | 1 |
| -- size | 每页条数 | 20 |
| -- total | 已有进度总条数 | 2 |
| -- pageCount | 总页数 | 1 |

> 可见范围为PUBLIC、LOGGED_IN、SELF或HIDDEN。未配置时state返回默认状态；list仅包含已有进度，已选但尚未产生进度的主职业可能不在list中，应以state判断选择状态。说明与名称字段作为文本显示。
