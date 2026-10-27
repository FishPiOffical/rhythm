# v3 用户公开职业资料交接

日期：2026-10-22。仅本批文件，不覆盖其他子代理改动。

## 实际文件与行为

- `src/main/java/org/b3log/symphony/processor/ApiV3ProfessionPublicProcessor.java`：`GET /api/v3/users/{userName}/professions`；可选 `profile.read` Bearer，随后真实 `AnonymousViewCheckMidware`；请求只从 v3 identity 获取读者。
- `src/main/java/org/b3log/symphony/service/ApiV3ProfessionPublicQueryService.java`：当前目标/读者、十模块隐私、历史版本职业、贡献/奖励/公开动态概要，以及共享 `ApiV3ProfessionRankingQueryService.summary` 组成只读响应。HIDDEN 不对本人或管理员例外放行；所有模块不可见返回404；缺省隐私按 ALL_PUBLIC 解释但不创建记录。仅有 experience/rarity 可见而没有任何可承载响应时也404。
- `src/main/java/org/b3log/symphony/util/ApiV3ProfessionPublicDto.java`：类型/长度/Unicode/数值严格白名单。真实进度读取其历史 revision/scheme；选择但无进度仅返回定义及 `state=SELECTED`，不造零级/零经验。experience 不可见时，职业门槛/总量、贡献和统计经验、动态经验变更全部省略。
- `src/test/java/org/b3log/symphony/service/ApiV3ProfessionPublicQueryTest.java`：实际 SQLite SQL + 生产 shared ranking/DTO/auth/Dispatcher/controller/匿名中间件测试。
- `tools/api-v3-profession-public-test.mjs`：Java25 隔离 runner，适配仓库名/环境/Token认证；没有 Maven、应用进程或业务数据库访问。
- 本文件：交接、公开 API 片段与人工验收。

未改总 register、API.md、UPDATE_LOG.md、AGENTS.md、旧接口、Repository/schema、FTL/JS/CSS，根代理统一集成和文档。

## 关键边界

- 模块：primaryProfession、allProfessions（响应 professions）、experience、rarity、majorContributions、contributionStats、activityFeed、levelHistory、rewards、ranking。PUBLIC、LOGGED_IN、SELF、HIDDEN 按当前 v3 读者逐模块判定。
- rarity 仅嵌在已允许返回的真实 primary/professions 内；ranking 独立数组。均复用新可见用户榜单 `{rank,totalUsers,surpassedPercent}`；目标不在可见榜单就不返回对应条目/rarity。历史退役职业不进入当前榜单。
- 贡献计数/分类和奖励概要独立受其模块控制；experience 隐藏时仍可保留次数、时间和等级名。排名本身可按用户已确认规则保留，绝不返回隐藏的数值经验。
- activity/levelHistory 只从90天 APPLIED 真实效果取概要，上限30条可读摘要。仅接受 PUBLIC 来源且其当前目标仍可读，owner也不跳过。文章/评论同时检查当前作者状态、文章匿名浏览开关、讨论邀请（共享精确 UserMentionCandidates）、评论私密性；匿名发帖/匿名评论来源排除。聊天室目标必须仍存在且读者有 Bearer；清风明月/复读目标有效。私密来源的本人完整经验记录仍走已有 `/me/professions/{id}/records`。
- 来源正文只用于讨论邀请判断，从不作为响应、标签或内部 ID 输出。全视图512记录技术预算、讨论正文累计2MiB预算；整请求所有职业排行首次/重验读取共享8192真实候选行预算，超预算整请求503；不截断为伪完整结果。SQL只读白名单投影；讨论正文SHA2绑定，源subject与效果owner绑定，清风明月按当前作者公开设置判权，复读机使用真实repeaterContentStatus并检查当前作者；末尾当前owner/reader/privacy/progress及所有来源授权快照重验，变化503或404，不宣称多SQL全局原子快照。
- 无新增公网静态资源、CDN、sourceMappingURL、目录或上传/下载地址。未修改 anonymous.viewSkips、黑名单/验证码解封及 BeforeRequestHandler；生产级整链路仍待根代理验收。

## 已执行验证

`node tools/api-v3-profession-public-test.mjs`：9组通过（日志 `/tmp/rhythm-profession-public.log`）。`node --check`通过。

1. 真实历史元数据/无进度选择、退役数据、共享可见排行、响应白名单。
2. 四种scope交叉匿名/本人/他人/管理员，HIDDEN无特权，缺省隐私不写库。
3. experience在贡献、门槛、动态中不可被绕过，合法等级/次数保留，目标隐藏ranking时rarity不输出。
4. PUBLIC来源 + 当前讨论邀请、私密评论、匿名排除、失效作者、消息删除。
5. 注入/格式/长度、状态、坏JSON、重复记录、非整数失败关闭。
6. 数据/正文预算超限503。
7. 每一实际SQL阶段故障清洗，末尾隐私/账号/进度重验。
8. 实际路由/auth/匿名中间件，错误Bearer或scope前置拒绝，Cookie/apiKey不冒充SELF，首访验证码在SQL前跳/test。
9. SQL NOCASE下等级与APPLIED状态Java精确校验、来源subject与effect owner绑定、等长正文hash绑定/最终授权快照复核、清风明月隐私与复读作者状态、整请求真实排行候选预算。

隔离测试不是MySQL迁移/优化器/多实例、HTTP顶层黑名单与Captcha解封、真实Servlet/IoC、PC/mobile浏览器验收。现有声明的安全基线仍需部署环境实测。此批无前端源码与生成产物变化，无需yarn。

## 人工验收

Java改动先通过 IDEA 重新编译或重启，再执行以下用户链路：

1. 目标账号设置 ALL_PUBLIC，匿名请求公开职业资料，看到历史版本职业/合法公开摘要；仅选择尚无进度职业时只见SELECTED定义，无虚构等级经验。
2. 目标依次设置 LOGGED_IN、SELF、HIDDEN：匿名被省略或404；有profile.read的他人只见LOGGED_IN；本人仅SELF放行；HIDDEN对本人/管理员同样隐藏。Cookie/旧apiKey不能代替Bearer，坏Bearer401、缺scope403。
3. 仅隐藏experience，重新请求，检查所有职业/贡献/统计/动态中均无totalExperience、requiredExperience、experienceSum、experienceDelta；等级、次数保留。隐藏目标ranking后，对应rarity和ranking条目消失；其余可见用户榜连续编号。
4. 生成公开文章、讨论邀请、私密评论和聊天来源，检查摘要只在当前读者有权时出现；撤销讨论邀请/停用作者/删除消息后重新读取相应摘要消失；匿名内容与PRIVATE来源始终不关联。
5. 非法姓名422、失效目标404；大数据或存储损坏503，无部分成功/敏感字段。比较读取前后privacy行数确认未创建默认记录。

## 公开 API 文档片段

### 读取用户公开职业资料

`GET /api/v3/users/{userName}/professions`

读取用户允许展示的职业、贡献、奖励和近期动态概要。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| userName | 路径参数，用户名 | Alice |
| apiKey | 通用密钥（可选） | 参考通用鉴权 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/users/Alice/professions'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 结果码 | 0 |
| data | 当前读者可见的职业资料 | 对象 |
| - primaryProfession | 可选，主职业定义或真实进度 | 对象 |
| -- professionId | 职业ID | 10 |
| -- professionCode | 职业编码 | writer |
| -- displayName | 职业名称 | 创作者 |
| -- shortName | 职业简称 | 创作 |
| -- description | 职业描述 | 创作与分享 |
| -- state | ACTIVE、RETIRED；选择但尚无进度时为SELECTED | ACTIVE |
| -- levelCode | 真实进度的等级编码 | L1 |
| -- levelName | 真实进度的等级名称 | 入门 |
| -- totalExperience | 经验可见时提供的总经验 | 50 |
| -- requiredExperience | 经验可见时提供的当前等级门槛 | 0 |
| -- rarity | 可选，与当前读者可见职业榜一致的排名概要 | 对象 |
| --- rank | 可见榜单名次 | 2 |
| --- totalUsers | 可见榜单人数 | 10 |
| --- surpassedPercent | 超过的可见用户百分比 | 80.0 |
| - professions | 可选，全部真实职业进度，字段同上 | 数组 |
| - majorContributions | 可选，贡献分类概要 | 数组 |
| -- professionId | 职业ID | 10 |
| -- actionCode | 贡献动作编码 | article.publish |
| -- eventCount | 事件次数 | 2 |
| -- lastOccurredAt | 最后发生时间，毫秒 | 1792636800000 |
| -- experienceSum | 经验可见时提供的合计变化 | 50 |
| - contributionStats | 可选，按职业汇总；含professionId、eventCount、lastOccurredAt及可见时的experienceSum | 数组 |
| - activityFeed | 可选，近90天最多30条当前可读公开来源动态 | 数组 |
| -- professionId | 职业ID | 10 |
| -- actionCode | 动作编码 | article.publish |
| -- occurredAt | 发生时间，毫秒 | 1792636800000 |
| -- experienceDelta | 经验可见时提供的变化，可为负 | 5 |
| - levelHistory | 可选，近90天最多30条当前可读公开来源等级变化 | 数组 |
| -- professionId | 职业ID | 10 |
| -- beforeLevelCode | 变化前等级编码 | L1 |
| -- afterLevelCode | 变化后等级编码 | L2 |
| -- occurredAt | 发生时间，毫秒 | 1792636800000 |
| - rewards | 可选，奖励概要 | 数组 |
| -- professionId | 职业ID | 10 |
| -- levelCode | 等级编码 | L1 |
| -- rewardCode | 奖励编码 | first |
| -- rewardType | POINT或MEDAL | MEDAL |
| -- status | PENDING、GRANTED或REVOKED | GRANTED |
| -- grantedAt | 发放时间，毫秒 | 1792636800000 |
| - ranking | 可选，当前可见榜中的本人职业名次；每项含professionId、rank、totalUsers、surpassedPercent | 数组 |

> 不可见模块或字段直接省略；没有可展示资料时返回404。HIDDEN内容在此公开接口对本人也不显示，本人完整资料请使用本人职业接口。
>
> 没有真实进度的主职业仅返回定义和SELECTED状态，不提供等级、经验或排名。经验不可见时，贡献和动态也不返回经验数值。
>
> 动态仅包含当前仍可阅读的公开来源概要；匿名和私密来源不在此公开摘要中关联。本文接口不提供来源正文。无权阅读或不进入当前可见榜的职业不返回对应排名；资料暂不可用时返回503。
