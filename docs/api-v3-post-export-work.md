# v3 本人完整内容导出（2026-10-24）

用户确认：主表仍存在的本人全部文章和评论（匿名/讨论/私密/隐藏/锁定/无效状态）与本人文章付费区当前原文，排除他人正文/标题及敏感账号信息。ZIP含UTF8 JSON清单和固定ID的Markdown；100积分仅文件就绪扣一次，同键同任务。第一方创建、当前密码/已有MFA，每次下载本人当前授权，保留24小时；物理删除无法恢复，编辑历史另行读取。

## 文件及实际行为

- `src/main/java/org/b3log/symphony/processor/ApiV3PostExportProcessor.java`：POST创建、GET本人状态、GET本人下载。创建同时settings.write/profile.read；状态/下载profile.read，服务再次限定当前第一方。注册要求真实transport日志过滤已安装，启动清理worker；下载完整校验后才sendBytes和附件头。
- `src/main/java/org/b3log/symphony/service/ApiV3PostExportService.java`：初次当前证明→独立快照→事务前账户对→当前证明/绑定重验→私有块/收费/元数据/回执同严格事务。响应丢失原键恢复同job，不再次收费；已到期回执仅expired，不自动重建。主账提交后绑定核验/幂等修索引。
- `src/main/java/org/b3log/symphony/service/ApiV3PostExportSnapshotService.java`：RR或SERIALIZABLE独立事务，按本人作者ID完整计数并逐ID游标读取，不套可见性或有效状态筛选、不取他人正文。完整数目核验，原文/Unicode和技术预算异常整次503不收费，不截取成功；JSON真实时间/状态/匿名字段，付费区独立文件。
- `src/main/java/org/b3log/symphony/util/ApiV3PostExportFiles.java`：请求期私有随机临时目录/ZIP/清单，POSIX0700及finally删除；固定文件名、严格UTF8、SHA256。ZIP32MiB/正文总256MiB/单项16MiB为当前技术预算，超出503，Latke下载只能sendBytes，不能称无限流式HTTP。
- `src/main/java/org/b3log/symphony/repository/ApiV3PostExportRepository.java`、`ApiV3PostExportChunkRepository.java`，`sql/20261024_api_v3_post_export.sql`、`src/main/resources/repository.json`：私有数据库元数据和有序Base64块，支持同数据库多实例/重启读取；不持久公开URL/服务器临时路径。先执行SQL，未代用户迁移。
- `src/main/java/org/b3log/symphony/util/ApiV3OAuthTransportLogFilter.java`、`src/test/java/org/b3log/symphony/util/ApiV3OAuthTransportLogTest.java`：敏感导出路径（含编码）解析日志过滤；不代表所有应用日志自动安全。
- `src/test/java/org/b3log/symphony/service/ApiV3PostExportTest.java`、`tools/api-v3-post-export-test.mjs`：生产proof/points/ZIP/严格事务/SQL/路由与故障矩阵，SQLite、仓储外部依赖和最终下载transport适配。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`、`API.md`、`UPDATE_LOG.md`、进度/计划/AGENTS及本文：总接线/公开契约/验证边界。

每分钟清理已过期job和全部owner绑定块，同事务提交；worker finally释放线程SQL资源，最多分批扫描，下载严格到期立即拒绝，不以延迟物理清理延长授权。回执不随文件删，防原键再次收费；清理后GET404，尚未清理过期GET410。下载检查块数、每块ID/owner/序号/规范Base64/长度、总SHA256，再重查当前授权/期限和元数据，不输出半个ZIP或本地路径。

## 实际验证

根执行 `node tools/api-v3-post-export-test.mjs`，10组退出0，日志 `/tmp/rhythm-v3-post-export-root.log`：完整本人状态矩阵/付费区及排除外文、超默认分页完整枚举、实际ZIP时间/路径/UTF8、六阶段失败回滚、丢提交应答与回放、私有下载/24h清理/当前授权、损坏/缺失/串owner块、当前密码/TOTP/epoch与第一方限制、真实三路由/附件头、预算与弱隔离拒绝、Log4j真实正控无假私文/密码/临时路径泄漏。

根另以Java25编译真实transport过滤+真实Latke parser、应用log4j2.xml和test：导出明文/编码路径通过，`/tmp/rhythm-v3-export-transport-log.log`。该ZIP隔离runner没有调用生产写、短信、图床、业务数据库或浏览器；无Maven。SQLite隔离标记模拟RR不等于验证真实MySQL快照、锁竞争或长事务；Response最后sendBytes为transport适配，不是实际网路下载。独立审查已核bundled真实Response.sendBytes会立即writeResponse，生产代码在所有校验后才调用。

## 完整人工链路

1. 先通过IDEA重新编译/重启，并在专用开发库执行新SQL；使用第一方settings.write/profile.read Bearer与当前密码摘要/已有TOTP。准备本人普通/匿名/讨论/私密/隐藏/锁定/无效文章评论及本人付费区，另造他人正文、付费区与敏感IP/账号字段作为排除哨兵。
2. 客户端设置数据导出入口POST固定key，成功ready、paidPoints100，积分只少100。GET状态核对counts/期限/sha，GET下载按状态与Content-Type保存ZIP并验SHA；解压manifest和文章/付费区/评论MD逐项核对当前原文、时间、状态与完整数目。原讨论资格已失的本人评论仍只导本人原文，不附父帖他人标题/正文。物理删除不伪造恢复。
3. 原key重试仍原job且只收一次；不同key可导独立快照。提交应答丢失用原key恢复，索引不能重复。到期后410或清理404，原key回放expired不收费；新key重新生成另收费。
4. 同浏览器分别在PC/mobile发起/保存，验证下载文件名固定且无公开URL；无Bearer、Cookie/apiKey代替Bearer、第三方、缺scope、密码/TOTP错误、当前用户停用或令牌被撤销应拒绝。其他用户猜jobId不能读状态/下载；重新登录的新有效本人授权可下载尚未到期文件。
5. 注入快照、文件、块、费用、job、回执和提交故障：余额/块/job/回执均不部分落地；临时文件finally删除。长快照期间更换密码/MFA/凭据版本，最终拒绝且不收费。余额不足409，坏源/超过预算/不支持RR明确503，不产部分包收费。
6. 用多块随机正文验完整下载，破坏块/顺序/owner/长度/摘要应JSON错误而非半ZIP；跨后端实例/重启读取验证DB共享。推进时间至24h，下载拒绝，清理worker同事务删metadata/块并释放线程连接，回执保留。
7. 验新IP无身份不能导出/下载，回归黑名单/test、验证码解封及开发关闭风控；检查anonymous.viewSkips没有新增绕过。新增下载无public upload/CDN路径，浏览器不能靠可猜URL无身份下载。静态版本/sourceMappingURL/目录枚举、真实PC/mobile与整站反漏扫尚未运行，不默认通过。

## 安全结论与限制

源码限定第一方当前Bearer/本人job，当前密码/MFA证明前后复核；不依赖Cookie写授权或公开下载令牌。参数/路径/UTF8/SQL绑定有界，ZIP只固定ID路径，无远程抓取/HTML执行/SSRF输入；敏感数据只在私有artifact中，公开响应是计数/摘要/时间而非正文，服务错误统一安全错误。真实MySQL迁移/索引/锁和快照、完整IoC/HTTP、真实浏览器与并发负载/全站反漏扫未验收，不能宣称整个v3完成。

API.md需同步网站API文章 https://fishpi.cn/article/1636516552191。

补充DDL证据：`python3 tools/api-v3-feature-schema-mysql-test.py`在无网络/无公布端口的一次性MySQL8.4.11中执行两份新SQL各两遍，核四表字段/类型及唯一索引，样本插入回滚，两组退出0，容器已删；日志`/tmp/rhythm-v3-feature-schema-mysql.log`。这不验证完整Java/MySQL事务、真实数据迁移或应用启动。


## 真实MySQL专项补验（2026-10-24）

新增`tools/api-v3-post-export-mysql-test.mjs`与`src/test/java/org/b3log/symphony/service/ApiV3PostExportMysqlTest.java`。子代理执行最终退出0，根检查实际日志`/tmp/rhythm-v3-post-export-mysql.log`及runner/Java断言：MySQL8.4.11、Connector/J8.3.0、Java25、REPEATABLE-READ与STRICT，八场景通过。正常场景覆盖不可压缩多块ZIP、完整本人正文与付费原文、摘要下载、100分/任务/回执一次提交；六故障为chunks/job/charge/receipt/commit及提交已成功却丢ACK，重试恢复一次；第八场景为两个独立JVM同键创建，实际观测InnoDB LOCK WAIT后释放持锁方，最终独立连接核一份job、一份回执、一笔100积分、完整chunks、两条索引且jobId相同。

导出两表使用精确迁移DDL，仅替换夹具表别名，并断言paidPoints仍INT、data仍MEDIUMTEXT；附属旧表从SQLite夹具明确改BIGINT/MEDIUMTEXT，保留生产FOR UPDATE/OCTET_LENGTH。实际生产导出与积分核心参与，但账户/令牌/账本Repository接口、缓存及最终HTTP传输仍适配，不代表完整IoC/应用HTTP或所有生产表结构已验收。随机127.0.0.1端口的一次性容器在finally清理并复核，未接业务库、未Maven。此证据补上原文“真实MySQL事务未验证”的部分，不覆盖真实业务迁移和完整系统运行。
