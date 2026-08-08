# 职业系统生产发布清单

本文针对本项目职业系统首次上线，数据库名、用户和密钥请替换为生产环境实际值。

## 发布前备份

1. 备份生产数据库和 `symphony_pointtransfer`、`symphony_notification`。
2. 保存当前职业配置：管理员进入“职业管理 → 导入导出 → 全部导出”，保留导出的 JSON。
3. 记录当前应用版本、`repository.json` 版本和已执行 SQL 文件，避免重复执行不可重复的 `ALTER TABLE`。

## SQL 执行

生产环境已经部署所有已提交代码及历史迁移，包括 2026 年 1 月、2 月的长篇阅读基础表。本次只处理当前未提交代码对应的增量，执行账号需要建表、索引和修改表结构权限。

只执行下面一个文件一次：

`sql/20260809_profession_pending.sql`

该文件已按依赖顺序合并以下 8 个未部署迁移：职业基础表、效果重试字段、历史最高经验、等级迁移配置、贡献统计、长篇阅读可靠性窗口、金手指来源索引和来源目标索引。不要再执行已删除的拆分文件，也不要重复执行 `20260123_long_article_read.sql`、`20260207_long_article_column.sql`。

长篇阅读可靠性段会读取已存在的 `symphony_article_long_read_stat`，执行前确认该表存在；它只迁移尚未结算的窗口，不重复创建 1、2 月的基础表。

外部职业经验接口还依赖积分来源字段。生产环境已经执行过 `sql/20260717_external_point_source.sql` 和 `sql/20260723_external_point_request_scope.sql` 时不要重复执行；未执行时先按项目既有迁移顺序执行它们。

执行后检查：

```sql
SELECT COUNT(*) FROM symphony_profession;
SELECT COUNT(*) FROM symphony_article_long_read_stat;
SHOW COLUMNS FROM symphony_profession_event_effect;
SHOW COLUMNS FROM symphony_user_profession;
SHOW INDEX FROM symphony_profession_source_event;
```

## 代码、配置和资源

1. 发布完整 Java/FTL 版本，不能只替换职业页面资源。职业路由由 `Router` 注册，后端必须重新编译并重启。
2. 发布 `src/main/resources/repository.json`。职业相关 Repository 不写 `symphony_` 前缀，表前缀由 `jdbc.tablePrefix` 统一添加。
3. 前端只从源码构建：

   ```powershell
   yarn run build
   ```

   将 `src/main/resources/css`、`src/main/resources/js` 和对应 `target/classes` 产物一起纳入发布包，不要手工编辑 CSS 或 `*.min.js`。
4. 同步 `src/main/resources/images/profession/` 下所有职业图标和封面。配置中的资源路径必须在生产域名下可访问。
5. 生产 `symphony.properties` 或外部配置中心设置：

   ```properties
   gold.finger.profession=<生产环境独立密钥>
   profession.external.maxExperience=100000
   profession.external.requestsPerMinute=120
   ```

   开发配置中的职业金手指默认为空，必须在生产配置或密钥管理系统中设置独立随机密钥。密钥只放服务端配置，不写入前端、职业 JSON 或 API 文档。限流和单次经验上限按生产容量调整，但必须与 API 文档保持一致。
6. 不要把开发环境的 `local.properties`、数据库密码、`target` 临时文件或 `.codex-tasks` 发布到生产。

职业接口完整说明已收录在项目根目录 `API.md` 的 `## 职业成长` 章节。

## 预置职业

职业定义、等级、样式、奖励和自动化统一使用配置导入，不直接写入职业表：

1. 在开发环境从“职业管理 → 导入导出 → 全部导出”得到正式配置 JSON；当前仓库的审核基线为 `tools/profession-release-seed.json`，自由职业的单职业文件为 `tools/profession-freelancer-seed.json`。
2. 将配置中的图标、封面一并上传或同步到生产资源目录。
3. 生产管理员打开“职业管理 → 导入导出”，粘贴 JSON 或选择文件，先点击“检查配置”。
4. 仅在“可导入 > 0、存在冲突 = 0”时点击“开始导入”。导入不会覆盖同代号职业；要更新已有职业，使用编辑向导和历史记录。
5. 导入后重新导出一份生产快照，与开发审核版本做差异核对。

不要执行“删除全部职业”的开发清理 SQL。已经被用户使用的职业只能通过管理员停用，用户经验和历史记录保留，页面会显示停用状态。

## 上线验证

1. 未登录访问职业公开接口，确认按匿名访问策略返回；管理接口必须返回未授权。
2. 使用测试账号完成一次职业选择、跳过、隐私设置和主职业切换；确认 `userAppRole` 没有被修改。
3. 访问 `/member/{userName}`、`/member/{userName}/profession`、用户名片和 `/top/profession`，检查职业名称、等级、经验、来源和隐私开关。
4. 使用金手指发送一条正经验请求，使用相同 `sourceAppId + requestId` 重试，确认只产生一次效果；检查 `profession_event_effect.status = 'APPLIED'`。
5. 让测试账号跨过一个等级，确认积分奖励、可选勋章和系统通知各自只发放一次。
6. 使用“模拟”检查多触发器时间线，再用真实行为验证一个文章、评论、聊天室或在线时长来源。
7. 检查重试任务、数据库错误日志和慢查询；职业详单查询只允许最近 90 天，并使用 `userId, professionId, occurredAt, oId` 索引。

## 回滚边界

- 代码回滚前先停止写入职业事件，再回滚应用版本。
- 不删除职业表、不删除用户职业汇总、不回滚已发积分或勋章；错误配置使用历史版本恢复或停用职业。
- SQL 的 `ALTER TABLE` 文件没有全部提供 `IF NOT EXISTS`，必须以迁移记录为准，禁止盲目重复执行。
- 外部平台接入失败时只撤销其接入密钥，不修改职业定义和用户经验。
