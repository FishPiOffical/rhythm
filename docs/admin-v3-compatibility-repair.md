# V3 后台兼容性修复与验收

本轮在 `api_v3`，基线 `31da68c1`，针对 866ae3a4、58743715、d40187ae、67e44285、9364b230、9da71a18、31da68c1。
用户已明确授权保留并调整 V3 IP/安全开关调用，以及图片响应移除 IP/MD5；未授权运行 Maven。本轮未运行 Maven、未连接业务数据库执行迁移、未部署服务。

## 入口对照与单一来源

| 业务 | 旧入口 | V3 入口 | 共享实现 | 权限/字段 | 事务与审计 |
| --- | --- | --- | --- | --- | --- |
| 创建文章 | AdminProcessor.addArticle → ArticleMgmtService.addArticleByAdmin | ApiV3AdminService.addArticle → ArticleMgmtService.addArticleByAdminInCurrentTransaction | ArticleMgmtService | 保留各自协议适配、原菜单/创建权限；V3 DTO 白名单 | 同一文章原语更新标签/统计/职业来源；旧入口持有根事务，V3 与幂等及 Operation 同事务；提交后事件 |
| 编辑文章 | AdminProcessor.updateArticle → ArticleMgmtService.updateArticleByAdmin | ApiV3AdminService.updateArticle → ArticleMgmtService.updateArticleByAdminInCurrentTransaction | ArticleMgmtService | 原编辑权限、字段白名单；思绪正文规则保留 | 统计→作者→文章锁；标签、段评、章节和封面同事务；优选通知/积分仍在提交后 |
| 管理员置顶 | ArticleMgmtService.adminStick/adminCancelStick | ApiV3AdminService → setArticleStickInCurrentTransaction | ArticleMgmtService | 原置顶权限；统一永久置顶 Long.MAX_VALUE | 旧注解事务保留；V3 审计和回执同事务 |
| 职业配置 | ProfessionAdminProcessor | ApiV3AdminStage4Processor | 原 Profession* 领域服务 | 旧管理员会话/CSRF；V3 第一方 Bearer + settings.write + adminRole；不混用身份 | 领域服务原事务不变，V3 原始字节严格输入、写频控 |
| IP | POST /admin/ip | POST /api/v3/admin/ips | 原 Firewall/AnonymousCaptchaShield；V3 由 AdminOperationTaskService 编排 | 旧 ipManage；V3 另需第一方 settings.write、menuAdmin；查询需 profile.read 和对应管理权限 | 旧入口保留；V3 同事务保存受理+任务，外部操作后逐项独立提交结果 |
| 安全开关 | POST /admin/security/save | POST /api/v3/admin/security/switches | 原进程状态 API；V3 任务编排 | 当前：旧站有效管理员会话+路由权限+CSRF+开发模式；V3 第一方 settings.write+menuAdmin+menuAdminMisc+adminRole+开发模式 | V3 完整严格类型快照，不在幂等数据库事务中改进程状态 |
| 索引维护 | AdminProcessor 原维护入口 | /api/v3/admin/articles/*reindex | SearchMgmtService；V3 使用明确报告失败的方法 | 原索引细分权限；V3 叠加第一方、scope、menuAdminArticles/menuAdmin | V3 受理/审计同事务，全部网络操作在后台；任务可查询、明确失败原键重试 |
| 图片审核 | /admin/pic + PC/mobile pic.ftl | GET /api/v3/admin/images | Upload 查询 | V3 实际要求 adminRole，不是仅 profile.read；保留 path，移除 ip/md5 | 只读，显式 SQL 列白名单 |

唯一文章后台业务来源为 **ArticleMgmtService**。ArticleAdminDomainService 已删除；其复制的 tag/processTags/封面实现不保留，直接复用原 ArticleMgmtService helper。旧入口与 V3 只保留参数/身份/响应适配以及根事务归属的区别。

旧协议兼容例外明确保留：旧后台新建采用表单时间 ID、固定普通文章/可评论；V3 的已发布创建协议采用独立 ID 和文章类型字段。旧编辑页在审计完成后调用共享索引 helper；旧批量封号调用编辑服务时不新增搜索同步。V3 在确认新提交后统一调用共享更新后置方法，回放不重复通知或奖励。优选通知和积分仍沿旧提交后语义，不宣称已经改成可靠消息或跨系统 exactly-once。

## 外部操作的失败与恢复

- `admin_operation_task` 持久保存 actorId、操作类别、输入快照、受理时间、进度与逐项结果。受理和幂等回执同事务提交，回执只含任务编号，不谎报已完成。
- 调度锁行只用于短事务领取，任务外部调用前已经提交。当前所有外部任务串行领取，防止全站清索引任务互相覆盖；不会在 HTTP 线程先清索引。
- IP 每项先提交 RUNNING，再依旧顺序撤白/封禁或解封；通过 ipset 成员状态核实后记录结果。解封不能确认时不调用验证码盾加白。全部结果存盘后才结束批次；部分无法确认返回 PARTIAL，不自动重放已成功项。
- 单篇/全站搜索使用会传播 HTTP 失败的接口，Algolia 等待其任务完成，ES 使用同步请求；失败持久标记 FAILED。客户端原幂等键重试可重调度失败搜索任务；成功任务不会重复执行。
- 原键的旧版回执没有任务 ID 时返回 LEGACY_UNVERIFIED，不假装存在可查询任务，不重复原外部调用。
- 后台资产幂等摘要扩充后，历史回执仅在新增字段为空或默认值时兼容重放；鱼游旧摘要缺少具体 items，或其他历史回执携带无法核验的新内容时，返回 `legacy_receipt_unverifiable`，不自动重做。
- 执行完但结果提交失败、进程中断或长时间无进度时显示 UNKNOWN。此时不能证明外部执行成败，**不自动重复外部操作**。仍存储 RUNNING 的未知任务会阻止后续队列执行，必须由操作员先停止相关执行进程、核实 ipset/进程开关/搜索状态，再人工归档该记录；不要直接删回执或改成 QUEUED。当前没有自动补偿或自动判定崩溃节点的机制。
- QUEUED 已落库但调度暂失败时仍返回受理；同键提交或有权查询任务会唤醒队列。尚未提供独立启动扫描/周期补偿服务。

旧 Firewall 返回诊断文本、旧 SearchMgmtService 异步吞错入口仍为历史调用保留；V3 不据此判断成功。旧后台 IP 与开关入口的受保护方法未修改。授权后只登记新增的 V3 读取/任务执行方法指纹，原核心策略版本和既有指纹不变。

## 敏感字段与反漏扫

旧 PC/mobile 的 article/comment/user/auditlog 模板本来展示管理员可见 IP/UA；本轮不脱敏这些既有字段。旧 pic.ftl 已展示原图链接，因此保留 path；旧页面不显示的 ip/md5 从新列表移除。鱼游模型无 TAGS 字段，旧模板没有标签搜索，不能“修复”成不存在的字段；保留实际名称/描述检索并公开说明。

新增任务查询显式 Bearer 鉴权，服务层再次核对第一方、scope、对应菜单/动作权限。不能凭普通 profile.read 读取任务。V3 不接受旧 Cookie/apiKey 身份，不以缺 CSRF 中间件直接判为 Cookie-CSRF。新增 SQL 使用参数绑定；IP 仅接受字面地址，拒绝主机名/网段/区域标识，不做用户控制的 DNS 或 URL 请求；错误结果不返回供应商正文、凭据或栈信息。任务受理与批量条数有界。

本轮没有新增公网 JS/CSS/CDN 资源、上传下载路由；生成产物及 PC/mobile 模板均未改。BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor 与 anonymous.viewSkips 未改。真实新 IP 首访、黑名单、每五次验证、真实极验解封、目录枚举和生产 CDN 内容仍未实测，不能据源码检查声称通过。

## 自动验证与未覆盖项

- `node tools/api-v3-write-test.mjs`：Java25，无 Maven；真实 Latke/Netty parseForm 后校验非法 JSON/重复键/非法 UTF-8、16 MiB 边界、64 KiB/256 KiB 限制和类型，包含已有事务提交故障回归。
- `API_V3_RUNTIME_CLASSPATH=<项目依赖类路径> node tools/admin-v3-compatibility-test.mjs`：真实服务源码定向类型检查；内存仓库适配器验证新旧创建计数、锁序、标签不重复累计、永久置顶、优选状态重复编辑、段评/封面事务边界，以及任务领取、失败、结果提交故障与不盲目重放。它不等于 MySQL/外部服务通过。
- `python3 tools/check-captcha-shield.py`：授权范围登记后通过，原核心与旧后台指纹未更新。
- `node tools/js-version-audit.mjs --root src/main/resources/js`：157 个 JS，零版本/source map 命中。
- `git diff --check`：交付前检查。
- 未运行 Maven、真实 MySQL 迁移/并发故障注入、Linux ipset、Algolia/ES、真实账户 HTTP/浏览器测试；未获开发者亲测结果，状态为待人工验收，拒绝直接合入/发布。

## 人工验收

1. 在测试库备份并执行下列迁移；通过 IDEA 重新编译并重启，再分别进入旧后台 PC/mobile 和 V3 后台。没有重启确认前不以旧服务行为判断修复失败。
2. 使用普通账号、只有 profile.read 的账号、仅菜单权限账号、对应动作权限账号、adminRole、adlered 分别测试。旧会话/apiKey 不应成为 V3 凭据；缺 Bearer/缺 scope/权限不足均不得产生任务或业务写入。任务查询也必须拒绝越权。
3. 旧后台创建普通文章，核对指定时间、ID、标签、作者及全站计数；V3 创建同等文章，验证计数各增一次。两端先后编辑同篇，修改/删除标签、无变更保存、设优选后再次编辑、回收站、思绪正文保护；核对标签关联/计数、优选通知和奖励不重复。
4. 两端编辑长篇章节、换专栏、保存及清空封面、变更段落，验证章节顺序、封面和段评迁移。注入正文/标签/封面写失败，应整体回滚，不发提交后事件。两端管理员置顶应永久，取消后归零；普通用户付费置顶不受影响。
5. 职业导入先预检后导入：合法 JSON（含中文、emoji）、16 MiB 边界、超限一字节、重复键（含转义同名键）、嵌套配置重复键、非法 UTF-8、字符串冒充整数/布尔/数组、未知字段。错误输入不得改变职业数据；普通写接口超过64 KiB和对应批量接口超过256 KiB仍拒绝。
6. 仅在隔离 Linux 测试环境执行 V3 IP 批量，包含 IPv4/IPv6、重复 IP；域名、CIDR、zone ID、错误类型应拒绝。查询任务到完成；制造 ipset 失败、第二项失败和结果提交失败，核对逐项状态、UNKNOWN/PARTIAL、数据库记录和真实成员；原键重复不得重复已确认操作。切勿封禁当前管理出口。
7. 非管理员或生产模式不得改安全开关；有相应权限的管理员在开发模式提交完整布尔/整数快照应受理并最终成功。字符串布尔、1.5 阈值、缺字段应422。确认非管理员不显示社区安全面板；真实匿名首访/旧站第十次受检查请求/黑名单/test/validateCaptcha 在受控环境回归。
8. 单篇索引模拟 HTTP 非2xx、超时和数据库结果提交失败，核对任务失败/未知而非假成功；明确 FAILED 恢复供应商后原键重试。全站任务受理应快速返回，清索引也在后台；并发申请不得交叉清空，查询进度/最终状态，服务重启后 UNKNOWN 不自动重放。
9. 图片列表核对响应没有 ip/md5、原图 path仍可打开，普通读取权限账号不能访问；旧文章/评论/审计页面的既有 IP/UA保持。鱼游使用仅在名称、仅在描述出现的关键词分别验证，不承诺不存在的标签检索。

## SQL（本轮唯一新增迁移）

来源：`sql/20261115_admin_operation_task.sql`。适用 MySQL/InnoDB；默认表前缀 symphony_，部署前核对 jdbc.tablePrefix。先备份；DDL 会隐式提交，不能依赖事务回滚 DDL。CREATE IF NOT EXISTS/INSERT IGNORE 可重复执行，但不会修复既有同名错误表结构，需先核对结构；迁移失败不要启动新任务入口。没有修改现有业务表，不应在回滚应用时删除已有任务审计。

```sql
-- 后台外部操作状态。表前缀按部署 jdbc.tablePrefix 调整；先迁移再启动新代码。
CREATE TABLE IF NOT EXISTS `symphony_admin_operation_task` (
  `oId` varchar(64) NOT NULL,
  `actorId` varchar(19) NOT NULL,
  `kind` varchar(32) NOT NULL,
  `status` varchar(16) NOT NULL,
  `payload` mediumtext NOT NULL,
  `result` mediumtext NOT NULL,
  `createdAt` bigint NOT NULL,
  `updatedAt` bigint NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_admin_task_queue` (`status`, `createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 调度锁只用于短事务选取任务，不在外部调用期间持有数据库事务。
INSERT IGNORE INTO `symphony_admin_operation_task`
(`oId`,`actorId`,`kind`,`status`,`payload`,`result`,`createdAt`,`updatedAt`)
VALUES ('dispatcher','','DISPATCHER','INTERNAL','{}','{}',0,0);

```

## 实际变更文件

- `API.md`
- `UPDATE_LOG.md`
- `docs/agent-guides/articles.md`
- `docs/agent-guides/website-v3-integration.md`
- `src/main/java/org/b3log/symphony/processor/AdminProcessor.java`
- `src/main/java/org/b3log/symphony/processor/ApiV3AdminStage4Processor.java`
- `src/main/java/org/b3log/symphony/service/ApiV3AdminOpsService.java`
- `src/main/java/org/b3log/symphony/service/ApiV3AdminService.java`
- `src/main/java/org/b3log/symphony/service/ApiV3AdminSystemService.java`
- `src/main/java/org/b3log/symphony/service/ArticleAdminDomainService.java`（删除重复实现）
- `src/main/java/org/b3log/symphony/service/ArticleMgmtService.java`
- `src/main/java/org/b3log/symphony/service/OptionMgmtService.java`
- `src/main/java/org/b3log/symphony/service/SearchMgmtService.java`
- `src/main/java/org/b3log/symphony/util/ApiV3Json.java`
- `src/main/java/org/b3log/symphony/util/ApiV3WriteInputs.java`
- `src/main/java/org/b3log/symphony/util/Firewall.java`
- `src/main/resources/repository.json`
- `src/test/java/org/b3log/symphony/util/ApiV3WriteInputsTest.java`
- `tools/api-v3-write-test.mjs`
- `tools/captcha-shield-lock.json`
- `docs/admin-v3-compatibility-repair.md`
- `sql/20261115_admin_operation_task.sql`
- `src/main/java/org/b3log/symphony/repository/AdminOperationTaskRepository.java`
- `src/main/java/org/b3log/symphony/service/AdminOperationTaskService.java`
- `src/main/java/org/b3log/symphony/util/ApiV3ProfessionInputs.java`
- `src/test/java/org/b3log/symphony/service/AdminOperationTaskServiceTest.java`
- `src/test/java/org/b3log/symphony/service/ArticleAdminCompatibilityTest.java`
- `src/test/java/org/b3log/symphony/util/ApiV3ProfessionInputsTest.java`
- `tools/admin-v3-compatibility-test.mjs`


## 后续复核的恢复边界

- 历史资产占位回执保留原样。收到结果待核对时，根据原操作人、操作时间和业务对象查账，确认当前状态之后再决定后续动作。新版完整摘要不会改写旧摘要。
- 图片审核复用本文迁移的 `admin_operation_task` 表，无新增 SQL。包含待执行、删除中、删除确认、刷新中、本地待结算和结束阶段。云端异常记录 `IMAGE_UNKNOWN` 及发生阶段；重启前停在删除或刷新中的记录同样按未知处理。核对供应商删除和刷新受理记录前，不重新执行云端步骤。群公告与即时推送保留旧异步语义。
- 与旧关系型勋章管理 API 的入口鉴权仍独立。经典用户页的 Cloud 风格勋章入口通过 `CloudService` 适配到 `MedalService`，共用关系型写入；保留既有 V3 委派权限。
- 适用验收补充：同图片跨两端同时提交并验 2/128 奖励、500 罚单与负余额；确认云端超时停在未知且同键重放不会重复调用。工资对照新旧通知、流水与余额，一项格式错误与保存点失效须产生不同结果。配置用仅菜单权限、单项权限与完整组合验证合法值、未知 ID、非字符串值和混合提交原子性。
