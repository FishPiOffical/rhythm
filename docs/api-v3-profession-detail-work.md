# v3 本人职业详情与经验记录（2026-10-22）

内部工程交接；主代理负责总注册、公开API/UPDATE_LOG、AGENTS及汇总。本文技术预算、SQL和安全链路不应整体复制到公开文档。

## 实际文件与总接线

- `src/main/java/org/b3log/symphony/processor/ApiV3ProfessionDetailProcessor.java`：两GET及实际`registerRoutes`，均`auth::profileRead`，只从Bearer identity取actor。
- `src/main/java/org/b3log/symphony/service/ApiV3ProfessionDetailQueryService.java`：当前owner、本人真实progress、关联历史revision/scheme/等级、路线及贡献、近90天经验记录的参数化投影查询。
- `src/main/java/org/b3log/symphony/util/ApiV3ProfessionDetailDto.java`：字段白名单、精确long/int范围、合法Unicode、固定长度及布尔值；actionCode沿自动化registry `[a-z][a-z0-9._-]{0,63}`，不返回effect/source身份或正文。
- `src/test/java/org/b3log/symphony/service/ApiV3ProfessionDetailQueryTest.java`：实际生产SQL/DTO/Dispatcher/鉴权/controller隔离专项。
- `tools/api-v3-profession-detail-test.mjs`：Java25隔离编译运行并清临时目录，不用Maven或实际服务。
- 本文：契约、专项证据、完整人工链路与限制。

总接线仅需`ApiV3ProfessionDetailProcessor.register();`。无新表、SQL、Repository、配置、前端模板或静态产物。

## 行为与边界

本人真实progress不存在时两个入口404，包括只选主职业但尚未产生真实progress的用户；不复用旧`progressOrZero`或构造零级。私人状态不受本人SELF/HIDDEN公开隐私限制，不接受外部userId/Session/apiKey替换Bearer owner。

关联历史职业revision和等级scheme都须属于该职业、状态PUBLISHED或RETIRED，路由按当前progress的scheme，非定义当前scheme；退休职业本人仍可读。字段缺失、重复progress/level/action或损坏关联503，不随机选择。读取末尾重读本人progress及关联metadata，变化503，以免多查询混合版本；最后重查账号有效性。此检查并非事务快照，不能承诺最后检查后永不变化。

详情不含rarity、raw presentation/config、sourceLabel、effect/source内部ID、用户ID或审计字段。路线与贡献各最多512条，SQL LIMIT513探测，超出整请求503，不截断、不默认分页。贡献只取eventCount>0，允许负experienceSum（合法撤销/调整）；记录只取owner+profession、APPLIED、delta非零、occurredAt>=同请求统一90天cutoff，按occurredAt/oId降序真实count/page。分页p默认1，size默认20最大50；COUNT和page是分开语句，不承诺并发插入时原子快照。

## 隔离证据

执行`node tools/api-v3-profession-detail-test.mjs`，Java25，**7组通过并退出0**，日志`/tmp/rhythm-v3-profession-detail.log`：

1. 真实历史revision/scheme等级路线与本人贡献，退休可读、负贡献、无zero构造、白名单。
2. 65条记录跨50分页、owner/profession/APPLIED/非零/近90天过滤、负调整与DTO精确字段。
3. 参数/分页/停用/不存在、错误父方案、草稿revision与重复progress失败。
4. Number精确整数、布尔值、Unicode、actionCode规范、溢出/负progress/畸形字段失败，不隐式截断转换。
5. 超512路线/贡献整响应503，无静默截断。
6. 所有6个查询阶段故障返回清洗503；读取中停用404、progress变化503。
7. 实际Dispatcher/controller/auth两路由：无Bearer401、错scope403、Cookie/apiKey/userId不能冒充；响应no-store/Vary，路径与分页422。

隔离适配器：SQLite、Token认证查找、响应信封、UserRepository名称，以及effect/contribution仓储名称（只使用getName/select，不依赖其业务方法）。其余生产service/DTO/controller/auth及相关基础Repository编译执行。未运行Maven、完整IoC、真实MySQL、实际HTTP/WS、8080、浏览器或生产。

## 完整人工链路（待用户IDEA重新编译/重启后执行）

1. A/B各取profile.read；GET职业目录→GET本人进度→以真实professionId GET详情。应看到本人历史revision名称、原scheme等级路线和贡献，A/B各自数据，不出现source/effect/raw配置或他人身份；对照PC/mobile职业页。
2. 新用户只选主职业但无真实progress：本人进度state可已SELECTED，详情和记录均404，不写入假progress。本人退休职业可读；其他人的职业不存在于本人progress时404。
3. GET records默认页→size50→第2页→空页，和近90天已应用非零明细对照；负调整保留，其他职业/其他用户/待处理/零经验/超90天不计入total/list。同毫秒记录按内部ID稳定顺序，但ID不对外返回。
4. 缺Bearer/仅Cookie/apiKey401、缺profile.read403；非法ID、p0、size51为422；停用账号404，存储损坏503且错误不泄露驱动信息。技术预算超出503，不能表现为成功截断。
5. 开发测试库变更progress/退休/迁移等级方案、读中停用，验证重查与权限；真实MySQL大小写/字段类型、索引/执行计划、COUNT+page并发和跨节点仍需验收。
6. 回归原全局黑名单→/test、AnonymousViewCheck首访/计次、validateCaptcha解封、开发关闭盾差异；新增两入口不匿名、不改viewSkips。静态/CDN/sourceMappingURL/目录枚举/上传下载返回路径与日志敏感信息的全站检查另验，本轮没有新增这些资源。

## 安全与反漏扫结论

两路由显式Bearer/profile.read且owner/current状态重查；没有新匿名入口、权限默认放行、Cookie写CSRF面。SQL全参数绑定、ID/分页边界、DTO白名单/数值/Unicode及技术预算限制；无HTML渲染、远程抓取URL/SSRF、上传下载或新增目录。返回字符串供客户端按文本编码。无新前端资源/CDN/source map，未动BeforeRequestHandler/Captcha/anonymous.viewSkips；不把无改动当作全站已验收。查询有界但SQL压力与分布式限额未经压测。旧GET仍保留zero/detail/sourceLabel和既有行为，不宣称旧链已迁移。

## 公开 API 片段

### 本人职业详情

`GET /api/v3/me/professions/{professionId}`

读取本人已有职业进度的历史展示资料、等级路线及贡献汇总，需要profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| professionId | 路径参数，职业ID，1–19位数字 | 10 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/professions/10' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 职业详情 | `{...}` |
| - profession | 本人真实职业进度 | `{...}` |
| -- professionId | 职业ID | 10 |
| -- professionCode | 职业编码 | writer |
| -- displayName | 关联版本展示名称 | 写作者 |
| -- shortName | 简称 | 作者 |
| -- description | 职业介绍 | 记录生活 |
| -- state | ACTIVE或RETIRED | ACTIVE |
| -- totalExperience | 当前累计经验 | 50 |
| -- levelCode | 当前等级编码 | L1 |
| -- levelName | 当前等级名称 | 初级 |
| -- requiredExperience | 当前等级累计经验门槛 | 0 |
| - levels | 关联等级方案的完整路线 | `[{...}]` |
| -- levelCode | 等级编码 | L1 |
| -- sortOrder | 展示顺序 | 0 |
| -- requiredExperience | 累计经验门槛 | 0 |
| -- displayName | 等级名称 | 初级 |
| -- shortName | 等级简称 | 初 |
| -- description | 等级介绍 | 初始等级 |
| -- achievementDescription | 达成描述 | 开始创作 |
| -- topLevel | 是否最高等级 | false |
| - contributions | 按动作汇总的贡献 | `[{...}]` |
| -- actionCode | 动作编码 | article.publish |
| -- experienceSum | 累计实际经验，可为负 | 50 |
| -- eventCount | 效果次数 | 2 |
| -- lastOccurredAt | 最近发生毫秒时间戳 | 1792600000000 |

> 无本人真实职业进度返回404，即使已选为主职业；不会自动创建零级进度。退休职业的本人历史进度仍可读取。资料异常或暂时无法完整读取返回503。返回字符串请按文本展示。

### 本人职业经验记录

`GET /api/v3/me/professions/{professionId}/records`

读取本人该职业最近90天已应用的非零经验变化，需要profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| professionId | 路径参数，职业ID，1–19位数字 | 10 |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20、最大50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/professions/10/records?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 分页结果 | `{...}` |
| - list | 按发生时间倒序的经验记录 | `[{...}]` |
| -- actionCode | 动作编码 | article.publish |
| -- experienceDelta | 实际经验变化，可为负 | 2 |
| -- beforeExperience | 变化前累计经验 | 48 |
| -- afterExperience | 变化后累计经验 | 50 |
| -- beforeLevelCode | 变化前等级编码 | L1 |
| -- afterLevelCode | 变化后等级编码 | L1 |
| -- occurredAt | 发生毫秒时间戳 | 1792600000000 |
| - pagination | 分页信息，含p、size、total、pageCount | `{...}` |

> 无本人真实职业进度返回404；有进度但无匹配记录返回空list。非法ID/分页返回422，资料异常或读取暂不可用返回503。仅返回经验记录概要，不含来源内容正文。
