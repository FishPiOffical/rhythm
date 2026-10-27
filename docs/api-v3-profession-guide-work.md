# v3 职业成长提示交接（2026-10-22）

## 实际文件与接线

- `src/main/java/org/b3log/symphony/processor/ApiV3ProfessionGuideProcessor.java`：GET `/api/v3/professions/{professionId}/guides`，显式`auth::profileRead`，从Bearer identity取当前actor。
- `src/main/java/org/b3log/symphony/service/ApiV3ProfessionGuideQueryService.java`：当前有效actor、已发布职业当前版本/方案、自动化版本与父关联、当前方案等级奖励的有界参数化SELECT；前后重查版本和actor，无写操作。
- `src/main/java/org/b3log/symphony/util/ApiV3ProfessionGuideDto.java`：固定trigger类型、POINT正整数amount、MEDAL类型概要，JSON/Unicode/数值严格检查，不返回raw配置或内部标识。
- `src/test/java/org/b3log/symphony/service/ApiV3ProfessionGuideQueryTest.java`：实际SQL/DTO/路由/鉴权/controller隔离专项。
- `tools/api-v3-profession-guide-test.mjs`：Java25隔离编译运行并清理临时目录，不用Maven。
- 本文：契约、证据、人工测试和限制。

根代理接线：`ApiV3ProfessionGuideProcessor.register();`；子代理未改总register、API.md、UPDATE_LOG.md、AGENTS.md或汇总清单。无新表、迁移、Repository、配置、模板或静态产物。

## 行为及依据

沿旧`ProfessionGrowthGuideQueryService`仅投影职业选择界面已展示的triggerType及rewardType/POINT amount；MEDAL不返回medalId/data/duration，不暴露condition/actions/effect/config/通知模板。来源相同的多个自动化仍分别返回triggerType，与旧展示保持；不因推测来源一定增加经验而读取动作条件。

职业必须PUBLISHED，currentRevisionId与currentLevelSchemeId分别指向属于本职业的PUBLISHED版本/方案，scheme绑定的职业版本必须存在、属于本职业且为PUBLISHED或RETIRED；发布职业展示版本不会自动替换当前等级方案，因此不要求scheme引用当前展示版本。未知/退休职业404；已发布但关联缺失/损坏/重复503，避免拼接他职或历史版本。自动化仅取当前职业PUBLISHED定义，current revision必须PUBLISHED且automationId/professionId双重精确关联，triggerType只接受现有registry的七种类型。奖励只取当前scheme，绑定同scheme等级oId及levelCode，坏关联/重复join503。空来源或空奖励合法，不合成职业进度。

两份列表各SQL LIMIT513，超过512整请求503，不截断。rewardConfigJson投影SUBSTR至4097字符，DTO超过4096拒绝；POINT amount必须Number精确整数、1至Integer.MAX_VALUE，禁止字符串/小数/溢出/缺省转零。MEDAL配置仅作类型与合法性检查，固定类型输出；不查询或暴露勋章内部资料。已发布配置应由旧定义校验器保证实际勋章存在，本只读接口不产生奖励或承诺实际发放。

读取末尾重查职业当前版本/方案及actor；中途停用404，版本切换或损坏503。多条SELECT没有事务快照，不能承诺自动化/奖励并发发布时的全局原子快照，也不保证最后重查后永不变化。未改旧读取服务或旧页面，不能宣称已迁移旧接口。

## 隔离验证

`node tools/api-v3-profession-guide-test.mjs`：Java25，8组通过并退出0，日志`/tmp/rhythm-v3-profession-guide.log`。

1. 真实SQL当前方案过滤、重复trigger保留、POINT及MEDAL最小白名单、退休/不存在404、无进度写入与空列表。
2. 职业/版本/方案精确父关系及其独立历史revision绑定、草稿/错误归属/缺失/重复定义503。
3. automation/current revision双父关系、reward同scheme等级ID/code、重复join/未知trigger503。
4. 错类型、坏JSON、未知字段、负数/零/小数/字符串/溢出、非法Unicode、超长配置、合法JSON空白；无raw配置/medal data泄露。
5. 513来源及513奖励真实limit-plus-one拒绝，无成功截断。
6. 所有六个SELECT阶段异常清洗、读取中账号停用/版本切换、非法路径ID不访问DB。
8. 模拟真实publishProfession：旧revision退休→发布新revision→只替换currentRevisionId，仍保留原currentLevelSchemeId及奖励；其历史revision变草稿则503。
7. 实际Dispatcher/controller/auth：仅Cookie/apiKey或坏Bearer401，错scope403；当前有效Bearer成功、禁用404、invalid路径422；no-store/Vary及响应白名单。

隔离适配器：SQLite JDBC、Token查找、响应信封和UserRepository名，其余相关生产Repository/service/DTO/controller/auth参与编译执行。没有运行Maven、完整IoC、真实MySQL、实际HTTP/WS、8080、浏览器或生产。SQLite不能证明MySQL索引/排序规则/驱动类型/并发与执行计划。

## 完整人工链路（待用户IDEA重新编译或重启）

1. 登录取得profile.read→GET职业目录→选PUBLISHED职业ID→GET guides，和PC/mobile旧选择界面的来源及奖励对照；POINT仅amount、MEDAL仅类型，所有列表无内部配置/ID/通知模板。A/B有效用户同职业结果相同；没有真实progress的账号也可读，查询后仍不创建progress。
2. 开发库中职业发布多个历史revision/scheme、草稿自动化、其他职业来源及奖励；新接口仅当前方案及当前职业发布来源；只发布新职业展示版本时，原currentLevelSchemeId继续有效且其旧职业revision为RETIRED也能读取。没有来源/奖励返回空数组；退休或未知职业404。
3. 测试Bearer缺失/错误、只Cookie/旧apiKey401，错scope403，停用账号404，注入/超长/非数字professionId422且不返回DB错误。读取中切换当前方案或停用账号，验证503/404，不出现混合职业版本。
4. 仅在测试库制造坏配置/错误父关联/未知trigger/重复关联、513条列表：应整请求503，不能输出部分成功、0奖励或驱动内部错误；恢复资料后再成功。真实MySQL字符集/大小写、SQL参数类型、LIMIT/SUBSTR和索引性能须另验。
5. 回归全局黑名单→`/test`、AnonymousViewCheck首访/计次、`/validateCaptcha`解封、开发模式关闭风控差异。新入口必须Bearer，不新加匿名放行，不改`anonymous.viewSkips`。全站CDN/sourceMappingURL/目录枚举/上传下载路径/匿名链及PC/mobile实际行为仍待运行验收。

## 安全及反漏扫结论

显式Bearer/profile.read并重查当前有效actor，无新匿名入口及Cookie写入CSRF面；不接受外部userId替换actor。SQL参数绑定，路径格式/查询技术预算/配置长度/数字精度及DTO白名单明确；没有远程URL抓取/SSRF、上传下载/路径输出、HTML渲染或新静态资源。trigger/reward类型为已注册枚举，不返回条件/自动化配置或用户敏感信息。未修改全局BeforeRequestHandler、AnonymousViewCheck、Captcha及viewSkips，静态检查无新增绕过；未做实际访问风控、全站CDN/source map/目录或批量压测，不能标记运行通过。

## 公开 API 片段

### 职业成长提示

`GET /api/v3/professions/{professionId}/guides`

读取已发布职业的成长来源与等级奖励概要，需要profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| professionId | 路径参数，职业ID，1–19位数字 | 10 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/professions/10/guides' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 成长提示 | `{...}` |
| - professionId | 职业ID | 10 |
| - triggers | 按展示顺序排列的来源，允许同类型多条 | `[{"triggerType":"article.published"}]` |
| -- triggerType | 来源类型 | article.published |
| - rewards | 按展示顺序排列的等级奖励概要 | `[{"rewardType":"POINT","amount":12}]` |
| -- rewardType | POINT或MEDAL | POINT |
| -- amount | POINT奖励积分；MEDAL不含此字段 | 12 |

> 来源类型包括long_article.read_settled、article.published、comment.published、breezemoon.published、repeater.published、chatroom.message.published、user.online.settled。此概要仅说明成长来源和奖励类型，不保证每次操作都会增加经验或发放奖励。不存在或已退休职业返回404，非法职业ID返回422；资料异常或暂时无法完整读取返回503。无需已有本人职业进度，读取不会创建进度。
