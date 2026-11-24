# 接口文档、幂等与覆盖核对

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[V3进度](../api-v3-progress.md)、[当前覆盖审计](../api-v3-current-completion-audit.md)、[开发验证](workflow.md)。

## 公开文档约定

- 对外接口文档统一维护在根目录 `API.md`。页首导航链接到「API v3」「旧版及兼容 API」两个连续区段和各自的业务分组。按协议归入版本，再按用途归入已有模块。V3 管理接口归入管理分组。调整标题时核对目录及仓库中引用的锚点。
- 版本用二级标题，业务分组用三级标题，模块和接口逐级排列。接口行用反引号包裹 `METHOD /path`，沿用网站文章的 Markdown 风格，包含简短用途、请求与响应字段表（`Key`、说明、示例）和 `bash` curl 示例。嵌套字段用 `-`、`--`、`---` 前缀，注意事项用引用块。
- 新增、修改或删除接口时同步更新 `API.md` 和 `UPDATE_LOG.md`。更新记录面向第三方开发者，按时间倒序插入适用说明之后。交付时提醒用户将 `API.md` 同步到[网站 API 文档文章](https://fishpi.cn/article/1636516552191)。
- `API.md` / `UPDATE_LOG.md` 为公开文档，不写限流阈值、匿名校验策略、CSRF 设计、防扫描链路、页面态接口内部关系等安全/实现细节；分页默认值及条数上限属于接口契约，可写。旧 apiKey 接口统一描述“需要 apiKey”；其匿名可访问接口在请求表加 `apiKey | 通用密钥（可选）`，正文不重复。V3 按实际 Bearer 与 scope 契约描述；独立 OpenID/OAuth 接口同样按实际协议，不把旧 apiKey 文案套到所有接口。

### 写完后按读者的操作复核

1. 调用方是谁？核对注册路由、鉴权和处理方法，区分游客、第三方、第一方本人及管理员。把必需或可选的凭据、传入位置和权限写清，逐项对照正文、表格和 curl。管理操作须标明权限，避免被误认作第三方能力。
2. 读者能否完成一次调用？写清方法、路径、必填字段、单位、成功结果，以及会改变下一步操作的错误。保留分页上限、费用、幂等键和重试语义等客户端契约。通用行为在模块开头写一次，单个接口只补差异。
3. 示例能否原样公开？表格、URL、请求头、JSON、回调和响应均用明确的占位凭据和虚构身份。密码摘要、一次性证明与令牌按凭据处理。禁止从日志、浏览器或生产响应粘贴样本。
4. 每句是否会改变客户端的操作？限流写返回码和等待方式，验证写交互和恢复步骤，批量操作写整批或部分成功。删除判定阈值、豁免条件、存储与锁、迁移脚本、测试进度及指向这些细节的链接。
5. 发现设计疑点时，只读核对实现，在当前对话或经认可的私密渠道反馈。公开材料遵循[文档保密要求](maintenance.md#文档保密要求)。交付前复核差异与渲染后的表格、示例，确认精简没有丢失调用条件或改变契约。

## 接口覆盖与运行证据

- v3完整清单与后续分工见`docs/api-v3-progress.md`，当前跨仓库路由对照见`docs/api-v3-current-completion-audit.md`（动态候选/转发不是运行通过）；以总register可达的METHOD+path计数，源码/隔离测试/运行验收分列，用户侧职业/勋章/会员不可按后台整体排除。

## Scalar 开发文档

- `ApiDocumentationProcessor` 提供 `/api-docs` 页面、两版 OpenAPI 及完整中文说明。页面入口先执行匿名访问校验，再执行 `LoginCheckMidware`；bootstrap、OpenAPI 和中文说明资源继续仅执行登录校验。旧版及兼容接口使用 v2，当前版本化接口使用 v3。旧版文档入口为 `/api-docs?version=v2`、`/api-docs/v2` 和 `/api-docs/v2-reference`，生成文件为 `v2.json`、`v2.md`；原 v1 文档入口已移除。接口路径沿用旧版。
- Maven 在 `generate-resources` 阶段运行 `tools/generate-api-docs.py`，将 `API.md` 的版本分区和路由清单生成到构建输出。构建机需 Python 3.9 或更高版本，若 Python 命令名不同，可传 `-Dapi-docs.python=python`。IDEA 直接启动时先执行该阶段再重启。生成文件不提交。
- Scalar 渲染脚本和字体由项目 CDN 提供，页面先通过站内 bootstrap 流式下载、显示进度并校验 SRI，再执行脚本；失败时显示重试入口。只改 `API.md` 不需要更新 CDN，文档数据继续由 Rhythm 按登录状态提供。更新 Scalar 脚本或字体时，使用新 URL 避免旧缓存，同时更新完整性哈希、CSP 和页面加载器，并验证 CDN 的跨域响应。
- 修改接口时同步维护 `API.md` 的中文参数、响应和事件说明。v3 的路由缺少说明时生成会失败。v2 按文档里明确列出的旧版和兼容接口生成。仍在 Markdown 正文中定义的字段和实时事件可点开完整说明查看。

- V3 的结构化文档从接口段落的请求与响应表生成。字段各占一行，提供可判别类型的示例，数组元素用嵌套行描述。合并字段名、空示例及同上引用可能只在中文说明中可见。对于可空、多类型、复用 DTO、缺少机器可读字段表和非 JSON 响应，用 [OpenAPI 结构补充](../api-v3-openapi-schemas.json) 明确记录服务端契约。

- 补充文件的 `schemas` 为共享 OpenAPI 3.1 Schema，`operations` 按 `METHOD /path` 定位 V3 操作。`data` 定义标准成功信封内的业务结构，`request` 定义必传的 JSON 请求体，`null` 则移除生成器从相邻说明误读出的请求体，`requestBody` 记录表单或二进制等非默认请求体，`idempotencyKey: true` 标注必填的幂等请求头，`responses` 定义实际状态码和媒体类型，`fields.request` 或 `fields.response` 精确替换既有字段结构（例如 `data.list[].time`），保留其中文说明。可选、可空和非 JSON 响应必须按实现核对。复杂结构可先补已确认的字段，勿用封闭对象掩盖尚未覆盖的字段。
- 核对写操作时，从生成 JSON 的方法和路径回查路由处理器及输入校验，再区分可完全省略的请求、必须发送的空 JSON 对象、带字段的 JSON 与非 JSON 请求体。
- 生成时会拒绝未知路由、找不到的覆盖字段以及无法解析的共享引用。修改后运行 `python3 tools/test-generate-api-docs.py`，再执行 `python3 tools/generate-api-docs.py . <输出目录>` 检查生成结果。未运行 Maven 或未重启应用时，以本地生成结果为准，不能代替 Scalar 页验收。

## 写入幂等

- v3 写入幂等由 `ApiV3IdempotencyMgmtService` + `api_v3_request` 在同一事务内提交业务与结果；回调必须调用 `*InCurrentTransaction`，不得自行提交、嵌套事务或直接发送外部事件。草稿写入走 `ApiV3DraftProcessor` + `ApiV3DraftMgmtService`，复用 `ArticleDraftMgmtService` 的现有规则。

- 后台典型资产写入先校验当前身份，再读不变的幂等命名空间。摘要扩充时，旧摘要命中的完整回执只读并标记 `legacyReceipt`。持久化的空占位回执返回 `operation_outcome_unknown`，保留原键并核对流水、会员/勋章记录，禁止把空回执当成可重放业务的授权。
