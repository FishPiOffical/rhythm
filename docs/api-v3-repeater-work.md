# v3 复读机只读交接（2026-10-22）

## 实际文件

- `src/main/java/org/b3log/symphony/processor/ApiV3RepeaterProcessor.java`：两个GET，可选content.read Bearer和显式AnonymousViewCheckMidware，身份仅从v3 identity取得；items默认12、size最大50。
- `src/main/java/org/b3log/symphony/service/ApiV3RepeaterQueryService.java`：参数化只读SQL、前N池和前50随机池、owner绑定点赞EXISTS、当前actor及完整查询池重验；没有事务或隐藏写入。
- `src/main/java/org/b3log/symphony/util/ApiV3RepeaterDto.java`：固定白名单、严格数值/Unicode及长度校验，不返回source/status/内部持久字段。
- `src/test/java/org/b3log/symphony/service/ApiV3RepeaterQueryTest.java`：真实SQL、生产Dispatcher/controller/auth专项。
- `tools/api-v3-repeater-test.mjs`：Java25隔离runner；无Maven。
- 本文：公开片段、人工测试、边界与安全证据。

根代理须在ApiV3Processor加`ApiV3RepeaterProcessor.register();`。本代理没有修改总register、API.md、UPDATE_LOG.md、AGENTS.md、旧接口、前端、配置、SQL或Repository元数据。

## 规则与边界

现有RepeaterProcessor首页固定12条；RepeaterQueryService仅status=0，likeCount DESC、createdTime DESC，next先取前50再排除excludeId随机。新接口保留该明确池语义，稳定并列补oId DESC；items不是全量分页，不返回total。type缺省所有，显式仅joke/kfc/fish且首尾空白规范化，错误422；size默认12、1–50。excludeId可缺省或空，非空仅1–32数字并规范化首尾空白，不要求该ID存在。无候选`item:null`，不回退旧项或第51条。

content是存储的纯文本字符串，客户端须当文本显示；空/短历史导入不因新投稿最小长度被拒绝，仍受实际模型500 UTF-16上限。SQL SUBSTR501防止大字段进入内存，DTO超限503，不返回截断后的成功内容。name限64、ID限32、数值Number经BigDecimal.longValueExact精确非负整型（兼容MySQL BigInteger/BigDecimal，拒绝小数/溢出）、拒绝损坏Unicode或不允许的控制字符，正文保留换行/回车/tab。author为当前有效账号最小{id,name}，seed/无作者/已失效作者均null；不因作者失效过滤内容池。匿名liked始终false，即使全局旧Cookie/apiKey产生user attr；有Bearer只按该actor查点赞，EXISTS不因历史重复点赞放大列表。

每次SELECT最多50条；这是真实产品前N池而非声称全量截断。重复作者join导致内容ID重复、异常存量或SQL异常503；错误不输出数据库消息。当前actor查询前、中、末验证，读取池两次比较，删除/改写/点赞等可见投影变化503，降低旧快照泄露风险。多SELECT没有数据库一致快照，不能保证最后重验之后没有并发变化，亦不承诺整个池读原子。

## 已执行隔离验证

`node tools/api-v3-repeater-test.mjs` Java25，6组通过，退出0；日志可由根代理复跑留存：

1. 真实SQL顺序、type/size、仅有效内容、失效/seed作者null、owner点赞隔离与重复点赞不放大、白名单和短正文存量。
2. 200次next均来自前50且排除指定ID；只剩排除项/空池null；未排除唯一项返回该项，绝不抽到第51条。
3. 同赞同时间ID稳定并列、类型/数量/注入ID422、失效actor404。
4. 超长内容/type、负数、重复join503，精确BigInteger/BigDecimal成功、小数/溢出拒绝，不成功截断或输出持久字段。
5. 各实际SELECT阶段驱动故障503且错误清洗；读取中下架503、末尾停用actor404；SELECT-only断言无写入。
6. 真实Dispatcher/controller/ApiV3AuthMidware/AnonymousViewCheckMidware；首访和第5次访问/test且业务查询不执行，Cookie/apiKey不绕盾及无个性化，坏Bearer不降级匿名、有效Bearer点赞、错误Bearer401、错scope403、停用404、参数422、no-store。

SQLite、Token查找、Repository名称、信封及匿名盾外部依赖（Option/UserCache/Sessions/ApiProcessor/SearchEngines/Firewall）是隔离适配器。生产匿名盾真实handle参与测试，Sessions/apiKey依赖若被v3调用会断言失败；本专项不能证明BeforeRequestHandler全局黑名单拦截、Captcha真实解封、MySQL排序规则/SQL类型/索引/并发、完整IoC或HTTP运行可用。没有使用Maven、8080、生产、浏览器或负载测试。

## 人工验收（先通过IDEA重新编译或重启）

1. 从PC/mobile首页复读机进入，GET `/api/v3/repeater/items`：默认12条，赞数降序、时间降序、并列ID降序；分别type=joke/kfc/fish及size=1/50对照旧内容，空分类items空。不是全量分页。
2. 展示某条后GET next带excludeId，多次刷新不返回该条，只在该分类前50内随机；开发库仅一条且已排除、零条应item:null，不偷偷回退；不传excludeId允许唯一项。
3. 匿名/Cookie/旧apiKey均liked=false；content.read Bearer A点赞过的项true、未点赞false，Bearer B不继承A；去掉作者或停用作者仍展示内容而author:null，无联系方式/IP/token/source字段。正文换行/emoji以文本展示，不把其作为HTML执行。
4. type错误、size=0/51/超长/注入、非数字或超32位excludeId422；错误Bearer401、错scope403、停用身份404。已删除内容不出现。开发库读取中下架/改写/停用应503或404；存量重复点赞不重复行。
5. 测试黑名单IP先到/test、首访/计次验证码、validateCaptcha解封后继续；无Cookie、仅apiKey、Bearer分别确认匿名盾与全局风控仍工作，开发模式关闭风控后业务一致。真实anonymous.viewSkips、代理IP环境需逐项验收。
6. 仅测试库制造超长正文/非法类型/负计数/重复异常关联，整请求503且无原始SQL/内部日志文本；恢复后可读。检查MySQL LIMIT/SUBSTR、执行计划及大表查询成本。

## 安全与反漏扫结论

- 未鉴权/越权：公开内容限status0，身份仅optionalContent Bearer，个性化按当前有效actor和owner EXISTS绑定；新匿名路由显式盾，不改anonymous.viewSkips。
- CSRF：两个GET不写，不消费或创建记录；没有Cookie态写动作。
- XSS：JSON返回纯文本，客户端按text显示；没有HTML/Markdown渲染，也不返回整用户。固定DTO拒绝坏Unicode/不合法控制字符。
- 注入：type/excludeId/size均校验，SQL参数绑定，表名仅Repository内部来源。
- SSRF：不接收或抓取远程URL。
- 批量滥用：池最大50、文本字段SQL读取有界；真实SQL仍需MySQL执行计划/负载验收，未擅加新业务限流。
- 敏感数据：只content公开文本、计数时间、作者公开ID/name、本人liked；不输出source、状态、手机号/IP/UA/密钥或原始异常。
- 新增静态资源/CDN/sourceMappingURL/可枚举目录/上传下载路径：本批均无。BeforeRequestHandler、AnonymousViewCheck、Captcha及viewSkips无修改；源码新增路由接线已检查，实际全站反漏扫、CDN与目录枚举未运行，不标记为验收通过。

## 公开 API 片段

### 复读机列表

`GET /api/v3/repeater/items`

读取按点赞数、创建时间排列的复读机内容。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 可选请求头，Bearer accessToken，需要content.read | Bearer YOUR_ACCESS_TOKEN |
| apiKey | 通用密钥（可选） | YOUR_API_KEY |
| type | 可选，joke段子、kfc疯狂星期四、fish鱼类科普；省略查询全部类型 | joke |
| size | 返回条数，默认12，最大50 | 12 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/repeater/items?type=joke&size=12' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 列表数据 | `{...}` |
| - items | 内容数组；没有内容时为空 | `[]` |
| -- id | 内容ID | 123 |
| -- type | 内容类型，joke/kfc/fish | joke |
| -- content | 纯文本正文 | 今日笑话 |
| -- likeCount | 点赞数 | 12 |
| -- createdTime | 创建时间，毫秒时间戳 | 1792584000000 |
| -- liked | 当前用户是否点赞；未提供Bearer时为false | false |
| -- author | 作者公开资料；无可展示作者时为null | `{"id":"123","name":"Alice"}` |
| --- id | 作者公开ID | 123 |
| --- name | 作者名称，纯文本 | Alice |

> 返回排序最靠前的指定条数，不提供全量分页。内容和作者名称应按纯文本显示。非法类型或条数返回422；内容资料异常或暂不可读返回503。apiKey不会为本接口提供个人点赞状态。

### 随机下一条复读机内容

`GET /api/v3/repeater/next`

从对应类型排序前50条中排除指定内容，再随机返回一条。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 可选请求头，Bearer accessToken，需要content.read | Bearer YOUR_ACCESS_TOKEN |
| apiKey | 通用密钥（可选） | YOUR_API_KEY |
| type | 可选，joke/kfc/fish；省略查询全部类型 | joke |
| excludeId | 可选，排除的内容ID，1–32位数字 | 123 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/repeater/next?type=joke&excludeId=123' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 随机结果 | `{...}` |
| - item | 与复读机列表单项字段相同；没有候选时为null | `{"id":"124","type":"joke","content":"今日笑话","likeCount":0,"createdTime":1792584000000,"liked":false,"author":null}` |

> 排除操作在前50条候选池确定后进行，不补入第51条。没有候选时返回item:null；未传excludeId时允许返回当前已显示的内容。非法类型或ID返回422，内容资料异常或暂不可读返回503。
