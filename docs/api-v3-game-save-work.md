# v3 Evolve 游戏云存档

2026-10-25。已确认首批仅完整存档 evolve-save，每人单槽，不做游戏计分、删除或历史备份。有效Unicode字符串原样保存，最多500000 UTF-16单位且UTF-8最多2MiB；PUT必须提交expectedRevision，新建0，冲突409。旧cloud仅登记游戏桥接新核心，旧写不带版本但每次递增；显式迁移唯一旧记录、缺失404/重复409、不覆盖新槽、保留来源。用户另已确认登记游戏旧保存要求application/json。

## 实际文件与行为

以下Java路径除特别注明外均相对src/main/java/org/b3log/symphony/。

| 文件 | 本批行为 |
| --- | --- |
| model/ApiV3GameSave.java | 无前缀表常量 |
| repository/ApiV3GameSaveRepository.java | 当前单槽查询、数据读取预算、严格事务入口 |
| util/ApiV3GameSaveInputs.java | 精确游戏目录、原样Unicode/长度/字节/hash与安全整数版本验证 |
| service/ApiV3GameSaveService.java | 当前授权重验、CAS、旧盲写、唯一旧记录迁移、metadata幂等回执同严格根 |
| processor/ApiV3GameSaveProcessor.java、processor/ApiV3Processor.java | 四条总注册路由，Bearer activity.read/write、写门禁、严格JSON与no-store |
| processor/CloudProcessor.java | 原LoginCheck与正常响应保留，仅登记游戏使用当前认证attr及其Proof；JSON旧保存进入共享核心 |
| src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java | 针对捆绑Latke的窄适配器，保留原Netty字节，不移动readerIndex、不retain；拒绝损坏UTF-8被框架替换后静默保存 |
| util/ApiV3OAuthTransportLogFilter.java | 私有存档及旧cloud的框架解析错误不输出原始正文；既有非相关日志保留 |
| sql/20261025_api_v3_game_save.sql | utf8mb4_bin单槽表/owner+game唯一索引、可重复执行的旧cloud查询索引 |
| src/main/resources/repository.json | 新表全字段及长度，框架自动加表前缀 |
| src/test/java/org/b3log/symphony/service/ApiV3GameSaveTest.java | 七组真实服务/事务/HTTP及新旧入口测试 |
| src/test/java/org/b3log/symphony/service/ApiV3GameSaveMysqlTest.java | 七种独立JVM实际SQL并发及独立观察验证 |
| src/test/java/org/b3log/symphony/util/ApiV3OAuthTransportLogTest.java | 私有新旧存档路径日志保护专项 |
| tools/api-v3-game-save-test.mjs、tools/api-v3-game-save-mysql-test.mjs | Java25隔离及临时MySQL运行器 |
| API.md、UPDATE_LOG.md、docs/api-v3-progress.md、docs/api-route-inventory.md、docs/api-v3-cloud-export-plan.md、AGENTS.md | 接口契约、兼容变化、进度与长期边界 |

没有改APP、PC/mobile模板或JS/CSS/CDN；无前端构建要求。Evolve现有CDN脚本仍走旧接口，可经桥接读写；40排行榜链保留。没有从业务库枚举或修改历史数据。

## 数据与并发边界

写入顺序为v3客户端命名空间→当前账号/令牌→幂等回执→本人槽；旧写仅当前账号/凭据版本→本人槽，不反向请求客户端锁。新旧HTTP写共用账号行锁，版本、内容、摘要、时间及v3回执同严格提交。GET不建立默认槽，不自动迁移。回执只存metadata，重放不覆盖后续版本；实际数据仍须重新GET。

迁移采用精确区分大小写的gameId查至多两条旧记录，不靠oId推断最新。旧读取无新槽时保留旧内容读取，有新槽后优先新槽。新旧同步只限已登记游戏HTTP入口，直接SQL/CloudService调用和其他cloud键没有整体改造，不宣称所有历史写法或跨实例缓存均已统一。

捆绑Latke parseForm不按Content-Type判断，且getBytes会经过有损UTF-8转换。窄同包适配器编译/执行使用实际latke-core.jar，禁止换回getBytes来验证原始编码。旧路由仍先按框架解析结果识别登记游戏，其他键、框架未解析成登记游戏的请求继续旧链路；不能据此声称旧通用cloud已全量严格化。

## 已执行验证

- Java25隔离脚本：七组通过，覆盖目录/Unicode/NUL/HTML/空白/空串/1.5MB往返、CAS、receipt不含正文、本人隔离、迁移缺失/重复/大小写/已有新槽、当前用户/client/token/scope/epoch、读后撤权、写/回执/commit失败及提交后丢ACK、损坏存储拒绝。
- 实际Request.parseForm、Dispatcher、Auth/Write门禁、旧LoginCheck与CloudProcessor执行：Cookie/query/body身份、新GET不接受Cookie，伪造owner拒绝，旧写JSON MIME限制、非法UTF-8不写入、旧GET和40不受该限制。新入口另测重复JSON键、百分号编码JSON、非法代理字符、错误MIME、超请求体、合法U+FFFD原样保存；raw读取不移动buffer索引。实际Latke日志过滤专项通过。
- 临时MySQL8.4：完整迁移连续两次、旧查询索引/单槽唯一索引、repository元数据；七组服务/HTTP原测试全部执行生产SQL及精确新表DDL。逐进程确认REPEATABLE-READ、STRICT_TRANS_TABLES及utf8mb4_bin。
- MySQL七场景由独立JVM竞争：same-key、cas-conflict、old-first、v3-first、migrate-first、old-before-migrate、migrate-create；逐项实际观察InnoDB LOCK WAIT，提交后独立连接核对版本/正文/回执数与来源未删。临时Docker只随机回环端口、无业务库，运行后清理。
- 实际代码可达清单275条（274 HTTP+1WS）：GET133/POST71/PUT46/DELETE24；机械全仓822路由、398浏览器引用是另一口径。没有执行Maven。

隔离边界：基础用户/client/token表与Repository IO适配、Token认证/旧apiKey解析端口为夹具；不是完整IoC启动、实际密码解密、全局风控或真实部署。SQLite去FOR UPDATE只用于功能夹具；MySQL版本保留锁并观察真实等待，不用内存锁或sink替代生产存档SQL。

日志：/tmp/rhythm-v3-game-save-tests.log、/tmp/rhythm-v3-game-save-mysql-tests.log。完整应用尚无本轮重启确认，不能用旧服务浏览器结果判定本批生效。

## 完整人工验收

1. 先部署20261025_api_v3_game_save.sql及此前v3凭据/回执基础迁移，再由IDEA重新编译或重启Java；确认IoC无缺类/新Repository错误。不要先清理或批量迁移业务旧数据。
2. 用隔离账号当前Bearer调用目录，仅Evolve；GET新槽未建立为404。PUT expectedRevision=0成功revision=1；GET逐字符比对原文、UTF-8摘要及长度。前后空白、加号/斜杠/等号、中文/emoji、空串应保留；非法Unicode/超限/错误类型拒绝且原内容不变。
3. 同键同参数重试返回原metadata且不再递增；同键异参数409。两个客户端用同一个旧revision不同键并发保存，只有一个成功，另一个409；重新读取后由用户决定保存，不做自动冲突覆盖。
4. PC和移动Evolve页面各自手动保存/重新加载，旧返回code/msg/data保持；旧同步与v3 GET为同一内容且每次旧写递增版本。旧Cookie、query/body apiKey均核对身份，不可跨用户。登记游戏旧保存缺失/错误MIME返回旧错误格式；40计分和其他cloud键仍旧链，不用于本批v3存档测试。
5. 用专用测试账号准备唯一旧evolve-save，v3 GET仍404，显式迁移成功rev1且旧记录保留；缺失404、多条409、已有新槽409，均不写入。迁移同键重放在后来保存之后仍不回写旧内容。
6. 两实例同时执行旧盲写/v3 CAS、迁移/旧写、迁移/新建，确认结果顺序及版本如API；注入数据、回执或提交失败，内容/版本/回执均不产生半写。模拟提交成功但响应丢失，同key重试不重写。
7. 未鉴权401、scope不足403、他人凭据仅见本人槽；撤销token/停用client或账号/全设备凭据更新后拒绝旧授权。Cookie不能单独调用v3，错误JSON/重复键400、未知game422、wrong MIME415、超请求体413；响应no-store且不带账号高敏字段。
8. 新IP/黑名单跳/test、Captcha解封、anonymous.viewSkips与开发模式完整回归；本批没有新增匿名豁免。检查代理CORS/跨域JSON写受既有策略限制，PC/mobile真实CDN脚本保存加载正常。全局运行链未在隔离夹具证明。

## 安全与反漏扫

源码/隔离结论：四新入口均要求明确Bearer权限，旧入口LoginCheck保留并从认证attr取actor；当前账号/授权及凭据epoch在事务根重验，不接受传入owner。v3不用Cookie授权；旧登记游戏保存仅JSON，未直接挂破坏apiKey客户端的legacy CSRF门禁。动态SQL只有内部表名，值参数绑定；无远程抓取/SSRF。存档有意不清洗HTML，作为JSON字符串返回，不在服务器模板执行。体积/条目/版本与既有写门禁有界，但未做生产压测。响应白名单、metadata回执及私有日志保护避免额外敏感数据暴露。

BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor、anonymous.viewSkips均未改；没有新增静态资源/CDN/sourceMappingURL、上传下载地址或公开存档路径。未重扫全站历史CDN版本/目录枚举/上传下载，未实际验证全局风控、CORS/CSRF、完整IoC、代理/连接池、多实例部署、PC/mobile真实游戏和第三方客户端；上述项不能默认通过。API.md须同步网站API文档文章。
