# 接口文档、幂等与覆盖核对

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[V3进度](../api-v3-progress.md)、[当前覆盖审计](../api-v3-current-completion-audit.md)、[开发验证](workflow.md)。

## 公开文档约定

- 对外接口文档维护：只要新增、修改或删除接口，必须直接更新项目根目录 `API.md`，并与 `UPDATE_LOG.md` 同步产出面向第三方开发者的更新记录；`UPDATE_LOG.md` 按时间倒序维护，新记录必须写在适用说明之后、既有记录之前；最终回复提醒用户把 `API.md` 内容同步到网站 API 文档文章 `https://fishpi.cn/article/1636516552191`；文档保持该文章 Markdown 风格：模块用 `## 模块名`，接口用 `### 接口名`；接口行使用反引号包裹的 `METHOD /path`；包含简短用途说明、`请求:`/`请求：`、`| Key | 说明 | 示例 |` 表格、`请求示例：` 的 `bash` curl 代码块、`响应：` 表格；嵌套字段用 `-`、`--`、`---` 前缀；注意事项用 `>` 引用块；不要改成 OpenAPI/schema 或纯文本表格风格。
- `API.md` / `UPDATE_LOG.md` 为公开文档，不写限流阈值、匿名校验策略、CSRF 设计、防扫描链路、页面态接口内部关系等安全/实现细节；分页默认值及条数上限属于接口契约，可写。旧 apiKey 接口统一描述“需要 apiKey”；其匿名可访问接口在请求表加 `apiKey | 通用密钥（可选）`，正文不重复。V3 按实际 Bearer 与 scope 契约描述；独立 OpenID/OAuth 接口同样按实际协议，不把旧 apiKey 文案套到所有接口。

## 接口覆盖与运行证据

- v3完整清单与后续分工见`docs/api-v3-progress.md`，当前跨仓库路由对照见`docs/api-v3-current-completion-audit.md`（动态候选/转发不是运行通过）；以总register可达的METHOD+path计数，源码/隔离测试/运行验收分列，用户侧职业/勋章/会员不可按后台整体排除。

## 写入幂等

- v3 写入幂等由 `ApiV3IdempotencyMgmtService` + `api_v3_request` 在同一事务内提交业务与结果；回调必须调用 `*InCurrentTransaction`，不得自行提交、嵌套事务或直接发送外部事件。草稿写入走 `ApiV3DraftProcessor` + `ApiV3DraftMgmtService`，复用 `ArticleDraftMgmtService` 的现有规则。
