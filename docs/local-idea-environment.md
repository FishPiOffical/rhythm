# 本机 IDEA 开发环境恢复记录

2026-10-29。本记录主要描述环境恢复，不代表整个 v3 已完成运行验收。用户随后已明确恢复联调，最新证据见[首批实际联调记录](api-v3-live-integration-work.md)。用户要求保留本地修改，不提交、不推送。

## 当前启动方式

- IDEA 选择 **Rhythm Local**，主类 `org.b3log.symphony.Server`，模块 `symphony`。
- SDK / Maven 导入器：`jbr-25`，路径 `/Applications/Android Studio.app/Contents/jbr/Contents/Home`，Java 25.0.3；项目语言级别 25。
- 参数：`--listen_port 8081 --server_port 8081 --static_server_port 8081`。
- VM 参数：`--add-opens=java.base/java.lang=ALL-UNNAMED`。
- 环境变量：`LATKE_LOCAL_PROPS=$PROJECT_DIR$/.idea/rhythm-local/local.properties`。该文件保存本机数据库账号，不提交。
- 入口：[本地 Rhythm](http://localhost:8081/)。8080 是另一个项目的页面，不作为 Rhythm 验收入口。

## 独立数据库

容器 `rhythm-dev-mysql` 使用本机已有 MySQL 8.4 镜像，仅绑定 `127.0.0.1:3319`；数据库 `b3log_symphony`，应用用户 `rhythm`，数据卷 `rhythm-dev-mysql-data`。容器设置 `unless-stopped`，可用 `docker start rhythm-dev-mysql` 恢复。未停止、修改或迁移 workphone 及其他既有容器。

私有 Docker 初始化参数在 `.idea/rhythm-local/mysql.env`，目录权限 700、配置文件 600。运行配置在 `.idea/runConfigurations/Rhythm_Local.xml`；这些文件均被既有 `.idea/` 规则忽略。不要把私有配置复制进公开文档或 Git。

首次自动建表与迁移有区别：框架将 `articleHotScore` 建成了普通列，导致系统欢迎文章写入失败。本次在新建开发库按 `20260515_article_comment_indexes.sql` 转为真正的生成列并建立文章/评论索引。初次引导已创建系统账号、选项及标签，欢迎文章没有成功写入，未伪造补写成功。

本次对新建库的空 v3 表使用对应 SQL 的正式定义重建，保留非空通知版本记录；按顺序执行 `20261016_api_v3.sql` 至 `20261025_api_v3_game_save.sql` 的 18 份迁移。自动建表已存在的 `ADD COLUMN` 在该新库按现有列逐项改为 `MODIFY COLUMN`，没有直接忽略迁移错误。以上是本机恢复过程，不是可照搬到已有业务数据库的迁移脚本；已有库必须单独比较列、索引和种子，禁止删表重建。

## 实际修改文件

| 文件 | 行为 |
| --- | --- |
| `pom.xml` | 编译属性由 1.8 统一为 25，与既有 compiler 插件一致，避免 IDE 导入得到冲突级别 |
| `src/main/java/org/b3log/symphony/processor/ChatroomProcessor.java` | 红包服务改为请求时获取，解除处理器→红包服务→处理器的 IoC 依赖环；领取与授权规则不变 |
| `tools/api-v3-chat-red-packet-test.mjs` | 旧入口测试适配器提供相同服务获取边界，继续执行实际入口逻辑 |
| `AGENTS.md` | 记录独立开发端口、私有配置入口、迁移及 IoC 依赖环约束 |
| `docs/local-idea-environment.md` | 本记录及验收步骤 |

此外修改了被忽略的本机 IDEA SDK、语言级别、Maven 导入器及运行配置。未改 website_v3、APP、前端资源、公开路由或公开 API 契约。

## 验证结果

- Java 25 完整 Maven `compile` 两次通过；第二次使用 `-Dlicense.skip=true -DskipTests`。第一次既有 license 插件追加的源码头已逐文件确认并撤销，未保留格式化噪声。
- IDEA Maven 重新加载成功；使用 **Rhythm Local** 完成实际构建并启动，Java 25 进程实际监听 8081。
- 无诊断 agent 的实际启动不再出现原 IoC `StackOverflowError`；已进入 `Everything is ready` / `Quick boot mode requirements is ready`。
- 实际首页 HTTP 200，浏览器显示 Rhythm 页面与 8081 的站内链接；`GET /api/v3/auth/captcha-options` HTTP 200 / code 0；无凭据 `GET /api/v3/me` HTTP 401 / unauthorized。
- 红包专项 20 组通过，涵盖五类核心、原子结算、风险证明、停用/删除发送者、旧领取入口的只读/类型/身份校验。SQLite/适配器证据不替代真实账号领取验收。
- 注册契约审计 290 路，文档数量一致，无缺项、重复或注册问题；`git diff --check` 通过。

## 人工回归步骤

1. 在 Docker 确认 `rhythm-dev-mysql` 运行；IDEA 选择 **Rhythm Local** 并重新编译/启动。成功应无缺包、IoC 栈溢出、数据库拒绝连接或端口占用，并监听 8081。不要同时启动另一个本地诊断 Java 进程占用该端口。
2. PC 打开 `http://localhost:8081/`，确认首页标题、登录入口和站内链接仍为 8081；移动浏览器打开相同地址核对移动模板。不要使用 8080 的其他项目页面判断修复效果。
3. 无 Cookie/Bearer 请求 `/api/v3/auth/captcha-options` 应返回 200；请求 `/api/v3/me` 应为 401，携带无效 Bearer 也应拒绝，不能因为开发模式关闭验证码盾而放行账号接口。
4. 在专用测试账号完成 v3 登录后验证本人读取；再验证旧 PC/移动聊天室红包：仅查看不领取，风险未确认不扣分，猜拳未选出拳不得提交，同键重试不重复结算，账号切换不得沿用他人的确认。涉及积分的操作本轮未在真实账号执行。
5. 停止 Rhythm 后，原 workphone 的 8080 与 MySQL 3306 仍应正常；重新运行 **Rhythm Local** 应继续读取同一个独立库，不执行第二次新库重建。

## 尚未验证和环境限制

本地 `10831` 聊天室节点未启动，后台会记录连接失败；本机已有连接仍不等于跨节点功能可用。首次初始化的外部 RhyPic 头像上传返回 404 并回退默认头像；短信、邮件、图床、第三方 OAuth、真实积分写入、移动设备和完整 v3 运行验收未在本轮完成。

当前为 DEVELOPMENT：既有启动逻辑会关闭验证码盾及 CC 防火墙。此次未修改黑名单、匿名访问跳过名单、验证码链或公开鉴权；不能用当前开发模式验证生产风控。无新增静态/CDN/上传下载地址、source map 或公开目录，未执行全站线上反漏扫验收。


## 后续诊断的字符集约束

Docker内mysql CLI当前默认client/connection/results均为latin1；后端JDBC本身不是这个配置。手工SQL、迁移和快照诊断必须显式使用 `mysql --default-character-set=utf8mb4`，不能把CLI读出的问号再写回数据库。此次仅专用测试E的快照恢复受影响：已通过本人v3资料接口将已知昵称/简介恢复，并刷新既有缓存。未据此修改JDBC、公开API或生产数据；本机诊断脚本已加该参数。

职业联调补充：首批general_ci修复未涵盖15张既有profession/user_profession表，实际带进度排行与用户表联表时出现排序冲突。本次仅在独立开发库将这些非binary、原0900表转换为general_ci，与当前user表一致；后续恢复开发库时须按实际列/表核对，不能把空目录GET200当联表已验，也不自动把此操作应用到生产。

- 长篇新建专栏依赖既有 `sql/20261009_long_article_column_order.sql` 的 `columnOrder INT NOT NULL DEFAULT 0`。自动建表可能只有NOT NULL而没有DEFAULT，发布会500且事务回滚；字段已存在时按原迁移含义修复默认值，勿重复ADD，也勿把遗漏迁移当成新业务缺陷。

## 阅读登记迁移（2026-10-30）

用户确认IDEA停止8081且原Java PID17956退出后，仅对rhythm-dev-mysql/3319/b3log_symphony执行`sql/20261030_api_v3_article_reads.sql`。核对读者表windowStart为NOT NULL BIGINT DEFAULT0，以及唯一(articleId,userId,windowStart)；迁移前后均0条读者记录，窗口/结算原唯一约束保持。未触碰workphone/生产，私有前后DDL与hash在article-reads-migration.json。

用户随后确认重编启动PID40308，新阅读POST首次/重复/本人排除及鉴权已实测；非法JSON在Latke预处理后清空getBytes导致误接受，ApiV3ArticleReadProcessor已改原始Netty校验，真实parseForm/MySQL共20组通过。最新这一控制器修改仍须再次IDEA重编重启，迁移不需重跑；不要把40308中的旧请求体行为当修复后结果。

用户再次确认重编后PID44439，原始请求体修复已真实通过，新旧入口/并发同窗去重与自然后台旧窗结算核对一致；20261030迁移无需重跑。本批只更新验收文档，不要求再次重启。详见联调第三十批。

正文/JSON快照注意：mysql -B 默认还会转义反斜杠，直接把JSON_OBJECT输出json.loads会把正文换行误读为字面\n。源文备份/恢复必须用--raw或SELECT HEX(JSON_OBJECT(...))再解码，并对原文字节/hash复核；utf8mb4只解决字符集，不能代替此项。本轮仅两篇专用测试长篇受影响，已从保存快照反转该转义并用HEX逐字节恢复，详见联调第三十六批。

## 本地双栈监听冲突（2026-11-02）

8081可能由不同项目分别监听IPv4和IPv6；本次Rhythm Java在IPv6、无关服务在127.0.0.1。验收前用lsof核对进程，再用无凭据的v3错误响应确认目标，禁止仅凭端口号发送测试凭据或停止其他项目。当前5173由本任务临时以`VITE_API_TARGET='http://[::1]:8081' node server.js`启动；不改生产默认目标。若监听变化，重新核验后再选地址，不能把此临时IPv6设置当所有机器的通用配置。
