# 普通积分改名真实MySQL专项（2026-10-24）

## 新增文件

- `tools/api-v3-user-name-mysql-test.mjs`：独立Java25编译、一次性Docker MySQL启动/临时schema、真实Connector/J执行与finally清理，不运行Maven。
- `src/test/java/org/b3log/symphony/service/ApiV3UserNameMysqlTest.java`：直接实例化生产改名/积分/幂等服务；repository、缓存、审核依赖适配，JDBC连接和SQL锁真实执行，故障及双连接并发编排。
- 本工作记录。没有修改生产Java、既有测试或其他共享文档。

## 运行与隔离

从项目根运行：

```bash
node tools/api-v3-user-name-mysql-test.mjs
```

可覆盖`JAVA_HOME`、`API_V3_MYSQL_DRIVER`、`API_V3_ENCODER_JAR`、`API_V3_CAFFEINE_JAR`、`API_V3_TESTNG_JAR`及`API_V3_MYSQL_IMAGE`。默认复用本地`mysql:8.4`缓存镜像，`--pull never`，不安装或下载依赖。Java默认Android Studio自带JBR25；Connector/J默认本地Maven仓库8.3.0文件；OWASP Encoder默认`/tmp/rhythm-v3-encoder-1.2.2.jar`。

容器名、schema和独立验证label使用随机12位hex，每次均新建。Docker `--rm`、`--memory 768m`，只发布`127.0.0.1`随机端口，runner核对实际绑定且拒绝3306；JDBC URL必须匹配该loopback/专用schema格式。没有读取应用数据库连接配置，也不接受业务JDBC URL。MySQL空密码root只用于该一次性容器。finally按本次容器名删除并检查唯一label无遗留，临时编译目录与schema源文件同时删除。

测试schema只在该容器中创建，无卷挂载，无已有3306连接，不对生产发请求。除部署开发文档列明的操作外，不执行任何业务迁移。中断或宿主进程强杀可能使finally无法执行，应按日志给出的精确容器名/唯一label检查；不要批量清理其他验证任务容器。

## 实际生产核心与适配边界

编译并运行工作区生产`ApiV3UserNameService`、`UserNameMgmtService`、`PointtransferMgmtService`、`ApiV3IdempotencyMgmtService`、`ApiV3Transactions`、`ApiV3TransactionSavepoint`、`ApiV3TransactionStatements`、`AccountPairLocks`、`ArticlePublicationCaches`及`UserNamePointData`/真实Escapes。生产方法负责名称检查、namespace/当前用户/回执顺序、扣费、主账、保存点、严格提交、回放投影与提交后索引修复，不复制整个业务SQL流程。

使用真实MySQL Connector/J连接、InnoDB和REPEATABLE READ。每次严格根安装实际bundled Latke `JdbcTransaction`，通过与既有专项相同的反射/Unsafe初始化连接和active状态；commit/rollback/dispose及保存点仍执行实际生产实现。故障代理仅在真实提交前/后抛错，不改写SQL。所有生产`FOR UPDATE`完整送到MySQL，未使用SQLite，也没有移除锁子句。

repository适配器将实际核心传来的select原样参数化执行；add/update仅做通用字段CRUD，不实现业务决策。真实`ApiV3RequestRepository#getForUpdate`和`OptionRepository#getForUpdate`方法在内；UserRepository的当前读和按列更新用最小JDBC适配。缓存仅断言事务外调用，不实际维护UserCache/Sessions；allowed/inspect只断言没有持有事务，故本专项不覆盖真实审核供应商或真实ProfileWriteQuota。

schema从`src/main/resources/repository.json`读取涉及的列类型/长度：user只取改名/积分所需字段，option只取namespace所需字段，pointtransfer和pointtransfer_redc取完整字段；名称VARCHAR64，dataId VARCHAR128，memo VARCHAR255，sourceScene VARCHAR20。回执DDL直接取`sql/20261016_api_v3.sql`的实际建表语句，responseJson为MEDIUMTEXT；积分索引使用既有迁移的rawId/userId普通索引。用户名称使用普通查找索引，没有人为添加唯一约束替生产namespace兜底。该最小schema不等于完整业务库/全部历史索引。

费用从实际`symphony.properties`的pointChangeUsername读取并注入测试Symphonys配置适配；实际Pointtransfer常量仍是生产代码引用，首跑1000积分，非既有SQLite fixture100。其余无关积分常量由编译适配器提供0，不参与本专项流程。保留名配置仅最小admin适配，完整站点保留名目录不在专项验证范围。

## 已执行证据

命令`node tools/api-v3-user-name-mysql-test.mjs > /tmp/rhythm-v3-user-name-mysql-test.log 2>&1`退出0，5组通过。实际MySQL8.4.11、Connector/J8.3.0，SQL mode含STRICT_TRANS_TABLES，REPEATABLE-READ，共执行231条未改写FOR UPDATE。容器`rhythm-v3-username-8befe881acc4`使用`127.0.0.1:63610`，最后删除并按唯一label确认无剩余容器。

1. 真实1000费用、名称/余额/主账/回执一次提交、双方积分索引；随后再改名，重放原键恢复原结果而不覆盖后来名称或二次扣分，同键异体409。
2. 在名称写入、扣费更新、主账插入、回执更新之后以及真实commit之前注入故障；生产保存点/严格根恢复旧名称与余额，无主账/回执/索引残留；清故障后原键可成功。
3. 实际Connection.commit完成后抛出模拟确认丢失；名称和收费已持久化，原键重试恢复单一主账并修复索引。这里只模拟应用侧提交应答丢失，不声称覆盖MySQL断网/重启的全部不确定性。
4. `-`、`---`、`abc-def`及64字符新名称，旧名称均为64字符；真实VARCHAR列成功保存分列dataId/memo，真实formatter完整输出，无分隔符歧义或129字符溢出。
5. 两个不同用户/独立JDBC连接争用同名：A取得真实namespace行锁后阻塞，B到达实际锁查询并等待；A释放提交后B409，只有A名称/余额/主账/回执改变。没有依赖数据库名称唯一约束制造结果。

编译出现与既有专项相同的Unsafe/弃用注解警告；故障注入产生生产PointtransferMgmtService预期错误日志。没有新增生产缺陷发现。`node --check`及新增文件尾部空白检查通过。

## 未验证与人工验收

这是生产事务核心的真实MySQL适配器专项，不能称完整站点端到端。未启动完整BeanManager/应用服务/HTTP/WS；未跑真实Bearer持久认证、全局黑名单/验证码、Censor网络、UserCache/Sessions、多实例缓存、完整PointtransferQueryService#getUserPoints、PC/mobile积分页或外部第三方客户端。普通旧`/settings/username`与注册/改名卡之间的完整并发、生产库真实索引/执行计划及历史损坏数据也未覆盖。

后续人工链路：用户先通过IDEA重编译或重启开发服务，再用双scope有效Bearer从客户端设置入口改名→读取本人资料/余额/积分页→同键重试→再改名后回放，PC/mobile均核对完整用户名和一次扣费；覆盖余额不足/占名、连字符/双方64、缺scope/失效身份及提交确认丢失。真实缓存旧名失效、多实例、新IP/黑名单跳/test与/validateCaptcha解封另按普通改名work执行。本批没有新增静态/CDN/sourceMappingURL/上传下载资源，未做全站目录枚举和反漏扫运行验收。
