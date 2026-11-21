# v3 本人匿名文章与评论列表（2026-10-20）

内部实现/验证交接；主代理汇总 API.md、UPDATE_LOG.md、AGENTS.md 及总注册。本文预算、SQL、鉴权及风控细节不能整体复制到公开 API 文章。

## 实际文件与接线

- `src/main/java/org/b3log/symphony/processor/ApiV3AnonymousContentProcessor.java`：两条本人读取路由，均为 `auth::contentRead`。
- `src/main/java/org/b3log/symphony/service/ApiV3AnonymousContentQueryService.java`：匿名文章 SQL 过滤/计数/分页；匿名评论当前目标权限与有界讨论邀请判定。
- `src/main/java/org/b3log/symphony/util/ApiV3AnonymousContentDto.java`：固定元数据字段，标题纯文本，时间/类型/Unicode 与长度校验。
- `src/test/java/org/b3log/symphony/service/ApiV3AnonymousContentQueryTest.java`：实际生产 SQL/控制器/路由/鉴权隔离测试。
- `tools/api-v3-anonymous-content-test.mjs`：Java25、临时 SQLite，显式适配 Token 查询/响应信封/Repository 连接；临时目录清理。
- 本文件：契约、测试、人工链路及局限。

主代理总接线增加：

```java
ApiV3AnonymousContentProcessor.register();
```

| 方法与路径 | 已实现行为 |
| --- | --- |
| GET /api/v3/me/anonymous-articles | 本人、匿名、非失效文章；包含本人锁定/讨论/长篇等合法类型，不从公开主页发现规则额外排除隐藏列表或沙盒内容 |
| GET /api/v3/me/anonymous-comments | 本人、匿名、有效评论，所属文章必须仍可读；当前文章作者被封禁/停用、文章失效/缺失、讨论未获授权均排除 |

两接口均只取 Bearer owner，不接收目标用户身份。请求中的 Cookie、旧 apiKey 或 userId 不能替换 owner。账户当前有效状态在开始/结束查询，owner/状态条件在数据与计数 SQL 重复绑定。GET 不创建、删除或修改记录，无 SQL 迁移、新 Repository 或前端资源改动。

## 返回契约

分页统一 `list/pagination`，`p` 默认1，`size` 默认20/最大50；按创建时间降序、ID降序稳定排列。

- 文章项只有 `id/title/type/createdAt/updatedAt`。
- 评论项只有 `id/articleId/articleTitle/createdAt/visibility`。
- 标题转纯文本；文章类型遵循已有0–6范围，评论 visibility 为0/1；时间为非负毫秒整数。
- 不返回真实匿名作者ID、昵称/头像、IP/UA、请求审计、HTML、文章/评论正文、打赏正文或完整实体。
- 该资源是列表摘要；正文另走现有详情接口并重新执行当前权限检查。私密评论仍需文章可读；该列表本来只给评论作者本人，返回本人私密评论摘要合理，但不借此绕过父文章权限。
- 错误页码/ID422、当前账号不存在或无效404；存储异常、损坏元数据或无法完整确认可见范围503，不能将错误吞成空列表。

## 讨论邀请精确过滤与技术预算

旧 `UserMentionCandidates.parse` 是两个独立非重叠提取加 trim/tail 的规则，不能用 `LIKE '%@name%'` 或简单正则替代。例如 `@bad@alice end` 不包含 alice 候选；但尾部 `@bad@alice ` 经 trim 后再取最后一个 @ 的尾部，会包含 alice。新接口精确复用该共享规则，不自行修订既有讨论邀请语义。

普通文章、本人所写讨论和当前管理员不需要邀请候选扫描。仅对本人有效匿名评论所引用的、他人所写有效讨论文章收集候选。当前实现预算最多512个候选（LIMIT513检测），累计正文最多2MiB；这是主代理批准的失败关闭技术预算，不是静默截断或业务条数限制。超过预算明确503，绝不返回截断列表或伪造 total；不得对外写具体预算阈值。

阶段：

1. 当前 reader 从数据库读取 `oId/userName/userRole`，状态必须0。
2. 候选头部仅取文章ID、数据库 `SHA2(articleContent,256)` 及 `OCTET_LENGTH`，先检查数量和总字节预算，不先加载全部大正文。
3. 仅按同 ID+同 hash 批量投影正文；头部之后已变更的正文不匹配，不进入授权集合。Java 调共享 parser，正文只用于服务端判定。
4. 最终 count/page SQL 均先约束当前 owner、匿名/评论有效、文章/作者当前状态、reader 当前名称/角色/有效状态；讨论只允许本人、精确管理员或已判定的 ID+同内容 hash，然后才计数/分页。
5. 管理员资格由 Java 的 `Role.ROLE_ID_C_ADMIN.equals` 精确判断后绑定0/1，而不依赖 MySQL 可能大小写不敏感的 `userRole='adminRole'`。`ADMINROLE` 不能被额外认作管理员。
6. 最后再次读取 reader，名称/角色与开始快照的 Java 精确比较不符则503，防止默认数据库 CI 比较接受大小写变化后沿用旧邀请资格。

每条 SQL 使用相同权限约束，但 **count/page 不构成跨语句原子快照**。并发撤回邀请时旧 hash 不用于返回目标，可能发生总数随并发变化的普通分页漂移。SQL 完成后发生的撤权属于普通在途读取边界，不承诺零时间窗口。MySQL 实际 SHA2/字符集/执行计划与多连接竞争仍须应用验收。

## 实际验证

实际执行 `node tools/api-v3-anonymous-content-test.mjs`：**10组 PASS**，进程退出0。生产 Service/DTO/Controller、ApiV3AuthMidware、ApiV3Requests 和 Dispatcher 声明编入；Repository 连接类型、Token 查询、信封为适配器；SQLite 用 Java UTF-8 SHA-256/OCTET_LENGTH 函数适配 MySQL 两函数。SQL 谓词/参数、原生分页及 count 真实执行，没有用 Java mock 替代最终 SQL 过滤。

1. 文章本人/匿名/非失效/当前账号条件在 count/page 前执行；锁定、讨论、长篇等本人元数据可见，公共/无效/他人项排除；创建时间并列ID顺序、空页和字段白名单。
2. 文章超过50条的真实分页，两页无重复，total真实。
3. 实际第一条 Dispatcher/auth/controller：无/错Token401、缺 content.read403且零SQL，Cookie/apiKey/伪 userId不能代替owner；no-store、Vary及HTTP503。
4. 文章4个查询阶段分别故障503，最终账号失效404；page重复owner条件，文章转归他人不输出。
5. 标题HTML/script转纯文本，超长/孤立代理项/负时间失败，正文/奖励/审计/真实作者列不进入文章查询或DTO。
6. 匿名评论当前父文章/作者/本人条件、私密摘要、精确 parser 的 space/HTML/newline/tail 与嵌套反例、分页及当前管理员免候选；作者状态1/4排除，2/3沿用当前目标可读规则。
7. 候选→正文/count/page的 hash 变化不返回旧授权目标；reader名称/角色单独变化与仅大小写变化失败503，NOCASE列模拟CI比较，固定`ADMINROLE`不能提升权限。
8. 恰好512候选成功、513失败；正文超2MiB在加载前503；本人和管理员不耗候选预算，超过50条评论仍由SQL精确分页。
9. 评论6个查询阶段分别故障503，最后账号停用404；实际第二条路由的 content.read、owner隔离、p/size及 no-store。

10. DTO枚举严格整数：文章类型0–6（保留旧4）、visibility0/1，小数/字符串/负数/越界503，不通过getInt截断。

另执行 runner 的 `node --check` 和专用文件 whitespace 检查。未执行 Maven、完整应用Java/IoC、真实 MySQL/连接池、真实 Token 签发撤销、8080/生产或浏览器；没有网络下载/短信/图床/支付操作。测试接口为本地内存 RequestContext，不是假称真实HTTP服务联调。

## 安全与反漏扫结论

- 新路由均明确 Bearer/content.read，无匿名入口，不修改 anonymous.viewSkips 或站点黑名单/验证码中间件；未经认证调用在SQL前拒绝。
- 仅 owner，目标条件在 SQL 复查，父文章授权精确过滤，私密内容/真实匿名作者不输出；读接口无写入CSRF副作用。
- 参数绑定、表名来自Repository，页码/条数/标题/ID/Unicode有界，临时讨论工作失败关闭；没有可控排序或SQL片段。
- 返回JSON元数据、纯文本标题；没有新HTML/脚本展示、URL抓取/SSRF、上传/下载、公开静态目录/CDN/source map或可枚举资源。
- 继承顶层防滥用，未证明真实分布式限流或大表执行计划；应用上线前需要测索引/慢查询及并发撤权。
- 未实际验证 BeforeRequestHandler 黑名单→/test、匿名首访/第五次、validateCaptcha 解封、开发关闭风控、全站CDN版本/sourceMappingURL/目录枚举/上传下载路径；这些仍需主代理完整运行验收，不能以无相关新增代替通过。

## 完整人工用户链路

Java改动联调前，须由用户通过IDEA重新编译或重启服务端，并由主代理总接线。没有新增迁移或JS/SCSS/FTL，不需yarn构建。

1. A/B各自v3登录取得content.read；旧PC/mobile本人匿名文章/评论页作为基线，GET两列表只含各自作品。无Token或仅Cookie/apiKey为401，profile.read/content.write-only为403；伪造userId不改变owner。
2. A发布普通/锁定/讨论/长篇等匿名文章→列表第一页/第二页→按ID读取文章详情；非匿名/失效/他人作品不出现，标题纯文本，返回无作者真实ID、正文、IP/UA。删除/更改匿名状态后新读取更新。
3. A在普通、本人讨论、受邀他人讨论发表匿名评论→GET评论列表→按commentId读取现有详情。撤销邀请、文章失效/作者封禁、缺失父文章后对应项不再可见；本人私密评论摘要仅在文章仍可读时出现。
4. 用测试数据覆盖共享parser尾部/空格/HTML/newline/嵌套@行为，与现有文章页权限逐一比对；改名/角色变化、仅大小写变化、邀请正文在各查询阶段改变时不沿用过时授权。管理员与本人讨论按既有规则允许。
5. 分页size1/50、下一页/末页/空页；p=0/size=51拒绝。开发数据触发预算或存储/元数据异常时503且没有部分列表，不显示虚假total；恢复后正常。实际MySQL并发期间允许普通跨语句分页变化，不承诺原子快照。
6. PC/mobile使用纯文本标题展示，再通过现有详情重新取正文；确认点击后权限仍按当前读者执行。回归站点黑名单/test、验证码解封和关闭风控行为，并单独完成全站静态/CDN/目录/上传下载反漏扫。

## 可由主代理合入公开API.md的片段

## API v3 本人匿名内容

### 读取本人匿名文章

`GET /api/v3/me/anonymous-articles`

分页读取本人匿名文章摘要，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20，最大50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/anonymous-articles?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 分页结果 | `{...}` |
| - list | 文章摘要，按创建时间倒序 | `[]` |
| -- id | 文章ID | 1700000000001 |
| -- title | 纯文本标题 | 示例标题 |
| -- type | 文章类型，含义同文章详情 | 0 |
| -- createdAt | 创建时间，毫秒时间戳 | 1700000000000 |
| -- updatedAt | 更新时间，毫秒时间戳 | 1700000000000 |
| - pagination | 分页信息，含p、size、total、pageCount | `{...}` |

> 仅返回当前账号的匿名文章摘要，不含正文或作者资料。失效文章不出现在结果中。

### 读取本人匿名评论

`GET /api/v3/me/anonymous-comments`

分页读取本人匿名评论摘要，需要 content.read；所属文章必须对当前账号可读。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20，最大50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/anonymous-comments?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 分页结果 | `{...}` |
| - list | 评论摘要，按创建时间倒序 | `[]` |
| -- id | 评论ID | 1700000000002 |
| -- articleId | 所属文章ID | 1700000000001 |
| -- articleTitle | 所属文章纯文本标题 | 示例标题 |
| -- createdAt | 创建时间，毫秒时间戳 | 1700000000000 |
| -- visibility | 0普通，1仅评论作者与文章作者可见 | 1 |
| - pagination | 分页信息，含p、size、total、pageCount | `{...}` |

> 仅返回摘要，不含评论正文或作者资料。失效评论或不可读文章下的评论不出现在结果中；正文另通过详情读取。服务暂不可用时返回HTTP503，请稍后重试。

建议UPDATE_LOG条目：新增v3本人匿名文章/评论分页摘要读取，均需content.read，仅当前账号且按当前文章可见性返回，不含正文、作者资料或请求信息。

建议AGENTS简短链路：本人匿名列表由ApiV3AnonymousContentProcessor/QueryService提供content.read元数据；评论讨论邀请有界候选/正文预算、共享parser和内容hash绑定后最终SQL count/page，预算失败503不截断；最后精确复核actor名/角色，管理员用Java精确判定，不能依赖SQL CI角色等号。
