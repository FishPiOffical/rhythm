# v3 职业排行交接（2026-10-22）

## 实际文件及行为

- `src/main/java/org/b3log/symphony/processor/ApiV3ProfessionRankingProcessor.java`：注册 `GET /api/v3/professions/{professionId}/ranking`，`profile.read`，从 v3 Bearer identity 取当前读者，标准 `p=1,size=20,max50`、no-store 响应。
- `src/main/java/org/b3log/symphony/service/ApiV3ProfessionRankingQueryService.java`：纯读 SQL，要求当前有效读者、当前已发布职业和有效当前发布版本。候选关联真实历史 revision/scheme/level，不造零级；有界完整候选最多4096，4097行则503。按 Java 精确十模块 visibility 验证并筛 ranking 可见用户后，经验降序、userId升序排序，连续编号、计数分页。不存在 privacy 行按 ALL_PUBLIC 读取，不落默认行。有效账户名为 `_` 者排除。
- `src/main/java/org/b3log/symphony/util/ApiV3ProfessionRankingDto.java`：严格数字、UTF-16/Unicode、完整十模块及枚举校验；输出固定 rank/userName/userNickname/userAvatarURL/professionId/professionCode/professionName/levelCode/levelName，experience 模块可见才增加 totalExperience。不返回 raw 用户、内部 user/progress/revision/scheme/privacy ID、presentation/配置或来源。
- `src/test/java/org/b3log/symphony/service/ApiV3ProfessionRankingQueryTest.java`：实际 SQL、历史关联、完整过滤分页、隐私、重读变更、预算、严格 DTO、实际 Dispatcher/controller/Bearer 中间件测试。
- `tools/api-v3-profession-ranking-test.mjs`：Java25 隔离测试 runner，真实 bundled Latke/SQL 与轻量 repository/table/Token/envelope adapters；SQLite 数据库。

用户已明确：经验隐藏但 ranking 可见的用户仍保留名次和等级，仅省略经验值。名次本身保留相对经验次序，这是已确认契约；不得擅自排除这些用户。`HIDDEN` 不因本人/管理员绕过；`SELF` 仅本人，`LOGGED_IN` 仅当前有效已登录读者。

共享方法 `summary(professionId, viewerId, targetUserId)` 返回 `Optional<JSONObject>`，字段 rank/totalUsers/surpassedPercent；空 viewerId 供已在 HTTP 层通过匿名门禁的公开资料读取，需登录的排行 HTTP 自身不允许匿名。target 未入当前读者可见榜返回 empty。百分比 `(total-rank)*100/total`，与当前可见人数及连续名次一致；无全量职业人数或隐藏对象推断。仅已发布职业可排名，公开资料对历史已退役职业跳过 summary。公开资料在一个请求内创建一个 `ApiV3ProfessionRankingQueryService.CandidateBudget`，通过四参 `summary(professionId, viewerId, targetUserId, budget)` 重载跨职业共享；首读及末尾重读的实际候选行累计最多8192，超预算503。SQL LIMIT按当前剩余预算加一行探测缩小，不依赖先前COUNT或在并发扩容时继续处理超量候选。单排行HTTP与三参summary仍各用独立预算。

多次 SELECT 最后重读完整候选、当前职业、当前读者并精确比较，隐私/身份/经验/历史元数据或人群变化503（当前读者已停用404）。这减少混合快照泄露，未声称数据库隔离快照或在最后一条 SELECT 后即时同步撤权。CI 数据库的状态/模块/levelCode/父级关联均由 Java 精确复核，不用管理员角色 SQL 字符串放行。

## 验证

`node tools/api-v3-profession-ranking-test.mjs`：10组专项。Java25实际编译本模块及依赖；没有运行 Maven、真实MySQL、完整服务、浏览器、生产请求。

覆盖：超过50用户先过滤再分页和连续rank、SELF/LOGGED_IN/HIDDEN、经验隐藏仍入榜、历史等级、稳定并列顺序、SQL/JSON故障和重复记录、strict数字/Unicode/枚举、超预算503、末尾候选/隐私/身份变更、实际路由认证及Cookie/apiKey不替代Bearer、共享匿名/登录summary同口径、跨职业共用实际行数预算及并发人口扩容失败关闭。

## 人工用户链路

1. 先通过 IDEA 重新编译或重启服务端；有 profile.read Bearer 的当前有效账号请求已发布职业排行，核对第一页、size50、第二页，结果 rank 连续且 total 只含当前读者可见用户，末页及超页为空。
2. 为几名上榜用户分别设置 ranking PUBLIC、LOGGED_IN、SELF、HIDDEN，另设 experience HIDDEN/SELF，重新请求。ranking HIDDEN 不出现；SELF 只在该本人读榜出现；经验不可见仍显示名次/等级但没有 totalExperience；管理员没有绕过。
3. 两用户经验相同核对 userId 稳定次序；持旧历史方案进度应显示当时职业名/级别；没有进度不伪造零级用户。停用或注销用户不计人数。
4. 访问公开职业资料，匿名与登录读者核对 ranking/rarity 派生值与各自可见排行一致；目标未入榜省略，退役历史职业不取得当前榜名次。
5. 缺失/错误Bearer应401，缺profile.read应403，legacy Cookie/query/body apiKey不能替代本接口；非法ID、p0/size51应422，无职业/未发布职业404。SQL异常、损坏隐私/重复持有/超候选预算503，不能返回截断总数或敏感异常。运行中切换隐私/停用账号验证不继续返回旧缓存数据。

## 安全与反漏扫结论及限制

- 未鉴权访问：路由显式 profile.read，当前用户重新数据库查询；不靠 Sessions 或旧 apiKey 提升。
- 越权：十模块精确 scope、当前读者限定、无admin例外；共享匿名summary仅给PUBLIC，HTTP外层门禁由公开资料模块负责。
- CSRF：纯读Bearer接口，不写入默认记录；Cookie不作为身份。
- XSS：JSON固定投影，无HTML拼接或raw配置；消费端需正常文本渲染昵称/职业名。
- 注入：ID和分页校验、参数化SQL，表名仅来自注入 Repository。
- SSRF：不抓取头像或其他远程地址。
- 滥用：标准分页及完整候选技术预算；不承诺DB全表扫描成本已被真实MySQL压测。
- 信息泄露：不返回内部ID、高敏用户字段、隐私JSON、presentation、驱动错误；隐藏经验仅保留用户已同意的相对名次。
- 本模块无新增匿名入口、viewSkips、全局黑名单/验证码链路、静态/CDN资源、sourceMappingURL、上传/下载路径或目录路由。认证隔离测试已覆盖；全站匿名盾、黑名单/test/validateCaptcha、开发风控关闭、PC/移动端、CDN目录枚举及真实MySQL并发/查询计划仍需主代理在运行环境验收，不能记为通过。

## 公共 API 文档片段（主代理汇总）

### 职业排行榜

`GET /api/v3/professions/{professionId}/ranking`

读取当前读者可见的职业排行榜，需要 apiKey。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| professionId | 已发布职业 ID，路径参数 | 10 |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20，最大50 | 20 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/professions/10/ranking?p=1&size=20' \
  -H 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.list | 当前页榜单 | [] |
| - rank | 当前读者可见榜单中的连续名次 | 1 |
| - userName | 公开用户名 | fish |
| - userNickname | 公开昵称，空昵称使用用户名 | 小鱼 |
| - userAvatarURL | 头像地址 | https://example.com/avatar.png |
| - professionId | 职业 ID | 10 |
| - professionCode | 职业编码 | writer |
| - professionName | 用户进度关联版本的职业名称 | 创作者 |
| - levelCode | 当前等级编码 | L1 |
| - levelName | 当前等级名称 | 初学者 |
| - totalExperience | 经验值，仅当前读者允许读取时返回 | 100 |
| data.pagination | 页码、条数、可见总数及总页数 | {} |

> 先筛选当前读者可见用户，再按经验降序及用户ID稳定排序、分页和编号。隐藏经验但允许上榜时仍显示名次与等级，不返回经验值。无可见用户时列表为空；职业未发布或不存在返回404，临时不可用返回503。
