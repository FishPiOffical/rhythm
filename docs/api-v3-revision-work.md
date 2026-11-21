# v3 文章与评论历史（2026-10-23）

## 规则与实际改动

用户已确认：作者本人，以及拥有既有历史查看权限且当前可读目标的读者；本人免历史角色权限；私密评论仅评论/文章作者双方，管理员无额外例外；匿名不返回真实作者ID；沿旧正文清洗格式。用户知悉存量历史没有保存时的权限/匿名快照，获准读者可能读取后续被删除的文字，不能宣称按当时权限逐版授权。

本模块仅新增以下文件，不改旧历史入口/公开文档/总register：

- `src/main/java/org/b3log/symphony/processor/ApiV3RevisionProcessor.java`：4个GET，明确content.read Bearer，列表p/size复用统一默认20、最大50。
- `src/main/java/org/b3log/symphony/service/ApiV3RevisionQueryService.java`：当前目标/读者/作者/权限直接SQL，bounded历史元数据、指定/最新正文读取、末尾授权与正文快照重验，全程只读无事务写入。
- `src/main/java/org/b3log/symphony/util/ApiV3RevisionDto.java`：元数据id/time/index/current；详情仅追加title（仅文章）和content，清洗正文沿旧Markdowns.clean并保留换行，标题沿Escapes.escapeHTML。不是原始Markdown，不渲染Markdown到HTML，也不额外返回articleRewardContent。
- `src/test/java/org/b3log/symphony/service/ApiV3RevisionTest.java`、`tools/api-v3-revision-test.mjs`：真实SQL、4路由/auth/controller、实际权限关系查询及生产清洗函数专项。

无新增表、SQL迁移、前端、静态/CDN、上传下载路径。

## 数据与权限边界

- 作者本人仍需当前目标可读和当前有效actor。其他读者除content.read外，当前角色分别需`commonViewArticleHistory`或`commonViewCommentHistory`；该链来自`RoleQueryService#getPermissions`直接role_permission关系，不凭角色名/管理员身份假定授予。SQL参数roleId/permissionId后在Java再次精确equals，避免CI数据库比较扩权；不走用户/角色缓存。
- 文章状态有效或锁定；当前作者不为无效/停用；读者必须有效。评论有效且可读、文章也可读；讨论邀请用真实`UserMentionCandidates`当前正文精确解析，并以SHA2快照绑定末尾重验。私密评论只双方，不因历史权限或管理员身份扩大。
- 历史按oId升序，所有存储版本先生成1起index，再可选追加`current`，列表分页。只有已有存储历史且当前原始标题/正文与最后存储版本忽略空白不同，才有current；详情current仅该条存在时可读。文章时间为articleUpdateTime，评论没有实际编辑时间，current.time明确null；不拿commentCreateTime冒充修改时间。
- 不读revisionAuthorId，对所有来源都不返回真实作者ID、完整Revision/Article/User、reward字段或其他额外stored JSON字段。匿名源与一般源使用同一安全白名单。
- 元数据查询最多4096条、单body 1MiB UTF-8、合计历史JSON 8MiB UTF-8；先4097行纯长度元数据完成预算，再按128条/批以CASE精确绑定ID/目标/类型/字符/字节长度后计算SHA2，再读取latest/指定正文，避免一次拉取巨量正文。超预算503，不能将截断结果假称完整总数。SQL SHA2与有界SUBSTR绑定返回数据，最后重读当前actor/作者/目标/精确授予及完整有界历史元数据，变化拒绝结果。此为读取期末重验，不宣称MySQL事务级原子快照或可彻底消除最后检查后的并发窗口。
- SHA2成本前置：每次元数据hash阶段输入合计不超过刚验证的8MiB；长度在预算后增长时CASE不调用SHA2、返回503；批次后继续核对完整长度/ID/类型快照，末尾授权重验保持。latest/指定body同样CASE绑定已验长度，current文章/评论只在<=1MiB时hash。初/末两次元数据各自验证8MiB，合计最多16MiB；含固定次数current/body重验，单次详情请求hash总输入上界文章20MiB、评论22MiB，不把“单次元数据阶段8MiB”误称整个请求8MiB。SQL每批最多128行、770个绑定参数，避免动态SQL超长。
- 历史JSON异常/错类型、无界正文或存储ID损坏503；目标/版本绑定缺失404；无历史角色权限403；无/错Bearer 401，scope不足403，输入异常422。错误不暴露SQL或正文。

## 实测

```bash
API_V3_ENCODER_JAR=/tmp/rhythm-v3-encoder-1.2.2.jar node tools/api-v3-revision-test.mjs
```

9组通过，日志`/tmp/revision-tests.log`。Java25实际编译项目新源，SQLite真实参数化SQL；SHA2/CHAR_LENGTH/OCTET_LENGTH由SQLite函数适配MySQL。生产Markdowns.clean的两个重载及inputSafelist从实际源码提取编译、真实Jsoup、实际Escapes/OWASP Encode1.2.2、MdSanitizer、UserMentionCandidates，未用清洗空stub。Token仅外部认证依赖适配，真实content.read/Dispatcher/controller执行。

覆盖：文章/评论分页、索引和current一致性、null未知时间；原始空白比较及无历史不造current；跨目标/类型详情拒绝；生产标题/脚本清洗/保留换行/敏感字段白名单；本人豁免、分权限、真实CI列Java精确角色/授予判断；私密双方/管理员不扩大、讨论邀请/父文章/作者状态；数量、单条及总量UTF-8字节预算（中文/emoji边界）、缺少必需标题/正文、损坏JSON和字段类型、故障错误脱敏；授权/作者/邀请/评论隐私/正文读取过程中变化；4个HTTP路由Bearer、Cookie/apiKey不能冒充、错误scope、分页参数与no-store；SHA2函数实际输入字节计量、预算超限零历史hash、300条多批累计上界、阶段/正文/批间膨胀CASE不hash变大内容。

未使用Maven，未启动完整服务或访问生产/真实MySQL/浏览器。SQLite不证明MySQL执行计划、字符集及跨连接一致性。PC/移动页面和防扫描真实链路未验。

## 完整人工验收

先通过IDEA重新编译/重启服务端，确认v3服务和既有鉴权数据库迁移。

1. 通过原文章/评论编辑入口准备至少两个存储版本；用作者content.read令牌从文章/评论id请求历史列表、逐一请求详情。列表应升序、连续index、分页total/pageCount准确，详情只有白名单元数据/清洗title/content，换行保留，revisionAuthorId/IP/UA/reward不可出现。
2. 用有commonViewArticleHistory的其他读者读取文章；只赋评论历史权限则文章403、评论可读；没有相应权限403。作者本人即使没有这两项授予也可读本人当前可读目标；仅有content.read不自动获得他人历史权限。
3. 讨论文章邀请读者后可读，撤回邀请后列表/详情均404；评论所属讨论也同步。私密评论仅评论作者和拥有历史权限的文章作者可读，第三人即使管理员/有历史权限也404；文章/作者/评论失效后不可读。
4. 无历史目标列表为空、current详情404；仅当前内容有非空白修改才追加current。列表出现current再读详情内容一致。文章current.time为当前更新时间，评论current.time为null；仅空白变化不追加。历史正文可能包含当年已被后来删除的文字，这是已确认范围。
5. 存储历史JSON额外加入敏感字段、script或恶意链接，详情只投影title/content并清洗，PC/移动客户端展示不得执行脚本。匿名文章/评论所有历史均不返回编辑者真实ID。
6. 测无/无效Bearer401、错误scope403、Cookie/query apiKey不能代替Bearer、非法ID/页码/size422、跨文章/评论或不存在版本404。size=50可用、51拒绝，越界页items=[]且总数真实。
7. 用MySQL两连接在读取期间撤销授予/邀请、修改评论隐私或修订正文，确认无旧授权内容返回；验证SQL SHA2/CHAR_LENGTH Unicode、索引/执行计划和查询预算。超预算必须503而非截断成功。
8. 使用新IP/黑名单IP、验证码解封及开发模式关闭风控分别回归现有入口。PC/移动完整浏览器链路另行验收，不将源测试当成运行通过。

## 反漏扫与安全结论（源码层）

- 匿名：4入口明确content.read Bearer，无匿名放行；未修改anonymous.viewSkips或防扫描中间件。BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor真实行为尚未运行验证。
- 未鉴权/越权：当前actor、目标可见性、文章/评论作者、私密正文及历史role授予重复检查；不从旧Sessions/Cookie/apiKey补身份，anonymous revisionAuthorId全部省略。
- CSRF：只读Bearer GET，不执行写入。XSS：使用旧生产清洗格式，标题HTML编码，未返回raw源码；客户端仍需按契约处理清洗内容。注入：所有值参数绑定且ID/页码枚举限制。SSRF：只读文本，不抓外链。
- 批量：metadata/单条/总正文和分页有界；未压测或证明全站抗扫描。敏感：正文已获用户明确授权的历史范围，除此不暴露源对象/身份/内部字段，失败无正文。
- 无新增公网静态资源、CDN URL、sourceMappingURL、目录或上传下载路径；未做全仓全站CDN/目录枚举复扫。

## 公开API片段（供root整合）

### 文章历史列表

`GET /api/v3/articles/{articleId}/revisions`

分页读取文章历史元数据。需要Bearer Token（content.read）；作者本人可读，其他读者还需文章历史查看权限，并且当前可以阅读文章。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| articleId | 文章ID | 1636516552191 |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20，最多50 | 20 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/articles/1636516552191/revisions?p=1&size=20' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.items | 按时间升序的历史元数据 | [] |
| - id | 版本ID；current表示当前虚拟版本 | 1761000000000 |
| - time | 版本时间戳（毫秒） | 1761000000000 |
| - index | 从1开始的全列表序号 | 1 |
| - current | 是否当前虚拟版本 | false |
| data.pagination | 分页信息 | {} |
| - page | 当前页 | 1 |
| - size | 每页条数 | 20 |
| - total | 版本总数，包含存在的current | 3 |
| - pageCount | 总页数 | 1 |

> 只有已有存储历史且当前标题或正文与最后版本忽略空白后不同时，才会追加current。历史不包含每次编辑的完整审计；正文没有当时权限快照，当前获准读者可能读到后来删除的文字。

### 文章历史详情

`GET /api/v3/articles/{articleId}/revisions/{revisionId}`

读取清洗后的历史标题和正文。需要Bearer Token（content.read），访问范围同文章历史列表。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| articleId | 文章ID | 1636516552191 |
| revisionId | 列表中的版本ID或current | 1761000000000 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/articles/1636516552191/revisions/1761000000000' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.id | 版本ID | 1761000000000 |
| data.time | 版本时间戳（毫秒） | 1761000000000 |
| data.index | 全列表序号 | 1 |
| data.current | 是否当前虚拟版本 | false |
| data.title | HTML编码后的标题 | 历史标题 |
| data.content | 沿旧历史格式清洗后的正文，保留换行，并非原始Markdown | 历史正文 |

> 不返回编辑者真实ID或额外的打赏内容。current仅在历史列表确有该条目时存在，否则404。

### 评论历史列表

`GET /api/v3/comments/{commentId}/revisions`

分页读取评论历史元数据。需要Bearer Token（content.read）；评论作者本人可读，其他读者还需评论历史查看权限，且当前可读评论及所属文章。私密评论只允许评论作者、文章作者双方。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| commentId | 评论ID | 1761000000100 |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20，最多50 | 20 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/comments/1761000000100/revisions?p=1&size=20' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.items | 升序历史元数据，结构同文章历史列表 | [] |
| - id | 版本ID或current | current |
| - time | 存储版本时间戳；评论current修改时间未知，返回null | null |
| - index | 全列表序号 | 2 |
| - current | 是否当前虚拟版本 | true |
| data.pagination | page、size、total、pageCount | {} |

> 仅已有存储历史且当前原始正文与最后存储版本忽略空白后不同时追加current。不返回匿名作者真实ID；历史正文没有当时权限快照，当前获准读者可能读到后来删除的文字。

### 评论历史详情

`GET /api/v3/comments/{commentId}/revisions/{revisionId}`

读取清洗后的评论历史正文。需要Bearer Token（content.read），访问范围同评论历史列表。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| commentId | 评论ID | 1761000000100 |
| revisionId | 列表中的版本ID或current | current |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/comments/1761000000100/revisions/current' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.id | 版本ID或current | current |
| data.time | 存储版本时间戳；current为null | null |
| data.index | 全列表序号 | 2 |
| data.current | 是否当前虚拟版本 | true |
| data.content | 沿旧历史格式清洗后的正文，保留换行，并非原始Markdown | 评论正文 |

> current仅在列表确有该条目时存在，否则404；不返回修订者真实ID或其他内部字段。
