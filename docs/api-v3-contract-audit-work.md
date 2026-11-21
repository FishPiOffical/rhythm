# v3 注册链与公开契约收尾检查（2026-10-28）

第109批，真实后端联调按用户要求继续暂缓。只新增源码审计工具与交接记录，无业务Java/FTL/SQL/Repository/客户端源码/APP/Maven改动，不新增HTTP入口。

## 实际文件

Rhythm：

- `tools/api-v3-contract-audit.mjs`：从Router.requestMapping出发，追踪本类registerProcessors、ApiV3处理器register及本类registerRoutes等辅助方法；忽略注释、未调用处理方法和孤立Processor。提取METHOD/path，对照API.md；重复、孤立文档、缺文档、条件注册或辅助方法重载均失败并要求复核。只识别源码中的固定注册形式，不编译或执行Java，也不能证明IoC/数据库/HTTP可用。
- `tools/api-v3-contract-audit-test.mjs`：9组实际扫描器临时工作区测试，覆盖正常Router/嵌套/本类helper，注释/孤立处理器排除，缺文档、孤立文档、重复、根失联、条件控制流及重载歧义。临时文件全部清理。
- `docs/api-v3-contract-audit.json`：当前可重复生成结果，114个被追踪方法、290条源码注册；GET147/POST72/PUT46/DELETE24/WS1，与API.md290条完全一致，无重复、缺文档、孤立文档或扫描问题。此结果不等同业务全覆盖或运行验收。
- `AGENTS.md`、`docs/api-v3-progress.md`、本记录。API.md契约及UPDATE_LOG未改（没有业务或接口变更）。

website_v3：`AGENTS.md`、`docs/V3_MIGRATION.md`、本记录；只同步当前事实及未完成项，客户端源码与产物不变。

## 验证与边界

实际执行9组工具测试、两个工具node --check、当前注册/文档审计及git diff --check均通过；全仓路由盘点另生成到临时目录，仍837声明/398经典浏览器引用，不能把该口径当作v3数量。没有执行Maven、业务Java编译、前端构建或业务库操作；客户端未改，不重复80脚本917组/认证13组的第108批全量验证。静态资源版本审计本批再次检查为77 JS/0发现，无map；不替代真实部署检查。

源码与文档维度没有发现路由契约缺项，但剩余业务决策仍包括：新风险红包在经典PC/mobile的领取衔接；发送者停用后的猜拳结算/返还资格。本轮已向用户提交两项明确选择，未答前不修改相应核心或旧请求。应用真实权限、并发完整链路、风控/验证码、外部服务、生产部署和移动设备验收仍未完成。用户已明确先不做真实联调，不重复追问部署确认。

## 安全/反漏扫

工具只读本地Java与API文档、生成指定本地JSON，不执行Java/请求网络或触碰业务数据；参数不拼SQL或Shell，不读取凭据。没有新增匿名入口、anonymous.viewSkips、CSRF边界、上传下载/CDN/目录或HTML渲染；原BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor及授权链未改。生成报告仅已公开路由和源码位置，不含用户数据或凭据。真实新IP/黑名单/验证码/解封/开发模式、权限和公网目录枚举继续列为未验，不能据静态扫描默认通过。

## 人工复验步骤

1. 在Rhythm根目录运行node tools/api-v3-contract-audit.mjs --json /tmp/api-v3-contract-audit.json，应退出0并显示290、文档290、空差集及无重复；检查报告114方法链和需要的具体路由来源。
2. 运行node tools/api-v3-contract-audit-test.mjs，应9组通过，临时工作区自动清理；负例自动验证缺文档、根失联、重复和条件注册会失败，无需修改业务文件。
3. 新增/改动接口时重跑审计，先补齐API.md及注册链；新注册形式、重载或条件流程需要人工检查并扩扫描器，不能绕过失败。
4. 扫描通过后仍需独立业务核心/权限/数据库与浏览器验证。用户恢复真实联调后再跑新IP/黑名单/验证码、合法/非法授权、PC/mobile和外部服务完整链，当前不执行这些操作。
