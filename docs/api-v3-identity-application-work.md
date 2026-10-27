# v3 企业认证申请与私有材料（2026-10-24）

## 当前阶段和业务边界

用户确认首批企业入驻、一张JPEG/PNG营业执照最多5MiB、仅第一方申请、指定审核员adlered；独立审核记录，首次读图原子删除材料，断网也不能重读；同一用户最多一份待处理申请（包含已阅待决定），新键重复409，同键同内容原结果；结论只通过/驳回与可空256字符纯文本，不自动发勋章或改权限。

第一阶段实现申请、本人列表/详情、审核列表及一次性材料读取五路，接入总register后259（258HTTP+1WS，GET129/POST63/PUT42/DELETE24/WS1）。前阶段未开放结论；本轮用户已确认必须先读图，现补齐第六路结论PUT，当前260入口。结论与回执及释放pending同根，不恢复材料、不自动发放权限或勋章。完整应用验收仍未完成。旧/user/identify继续原流程，没有迁移旧私信/图床材料，也不对旧副本承诺阅后即焚。无前端页面改动。

## 实现

- 创建请求原始二进制，Content-Type image/jpeg或image/png，Idempotency-Key必需。不接任意网址、文件路径、公司附加资料、多图或type字段。真实ImageIO识别与受限采样解码，仅MemoryCacheImageInputStream，不产生证明图片临时文件；PNG固定结束块与JPEG尾标记检查，损坏/类型不符422，超过5MiB413，不支持MIME415。技术解码资源预算（并发及像素量）超限503，不截断成功；保留原始图片字节，可能含原图片元数据，只允许指定审核员读取。没有远程抓取。
- 第一方当前授权复用ApiV3SecurityProofService的新最小投影方法，完整沿client namespace→website→当前user→当前access→credential state检查与锁序；只返回oId，不导出密码/MFA字段。settings.write用于申请/读图，profile.read用于状态/列表；HTTP也先有Bearer中间件与写预算，Cookie/apiKey不能取代身份。不是更改密码的入口，不额外要求当前密码/MFA证明，也不放宽既有敏感设置规则。
- 迁移时从BINARY精确名称adlered且当前有效的唯一用户绑定enterprise审核员ID，保存后改名或另一个账号获得该名称不会转移审核权。无有效唯一匹配不创建绑定；审核端据固定ID及当前有效第一方授权判断，不能仅靠管理员角色或运行时用户名冒领。缺失绑定须维护数据后再使用，不自动把权限给其他账号。
- 应用元数据/私有base64材料/幂等回执同严格事务；请求回执仅id/createdAt，不存图片副本、公开URL或完整材料哈希。规范化请求只以图片摘要/类型/长度生成幂等指纹；实际材料摘要仅在私有材料表。用户行锁与pendingOwnerId唯一键保证一份未处理申请；重放也重验当前授权，不重建已读材料。
- POST open不保存幂等回执。当前审核授权→锁申请→锁材料→核对归属/长度/摘要→删除材料→记materialReadAt→严格commit，之后发送原始字节。重复410；commit失败回滚材料与时间，成功后丢ACK也不能恢复材料。记录仍pending且占用唯一pending资格。响应no-store/nosniff/沙箱策略与固定附件名，无公开路径。
- 删除指在线数据库材料记录的同事务删除；不改数据库备份、复制、binlog或底层介质策略，不能称为对历史备份的物理擦除。内存响应直到发送结束仍持有本次图片，不缓存供重读。实际部署的备份与HTTP代理策略需要运行验收。
- 元数据仅id/type/status/createdAt/materialReadAt/reviewedAt/note，审核列表额外applicantId；没有图片/摘要/原始用户或联系方式/IP/UA。暂不向任何人发送私信/通知，不变更角色、勋章、奖励。读取图片不是完成审核。

## 实际文件清单

新增后端：

- src/main/java/org/b3log/symphony/util/ApiV3IdentityImage.java
- src/main/java/org/b3log/symphony/service/ApiV3IdentityApplicationService.java
- src/main/java/org/b3log/symphony/processor/ApiV3IdentityApplicationProcessor.java
- src/main/java/org/b3log/symphony/repository/ApiV3IdentityApplicationRepository.java
- src/main/java/org/b3log/symphony/repository/ApiV3IdentityMaterialRepository.java
- src/main/java/org/b3log/symphony/repository/ApiV3IdentityReviewerRepository.java

修改后端：src/main/java/org/b3log/symphony/service/ApiV3SecurityProofService.java（新增第一方最小授权方法）、processor/ApiV3Processor.java（总注册）、util/ApiV3OAuthTransportLogFilter.java（新私有路径及编码路径的框架错误日志抑制）。没有改旧认证申请处理器或旧凭据协议。

Schema：sql/20261024_api_v3_identity_application.sql，src/main/resources/repository.json。三个新Repository不含symphony_前缀；已有基础v3/credential迁移在前。使用现有请求回执表，不新增图片公共存储。完整部署前先执行迁移并检查审核员绑定；本批未操作业务库。

测试：src/test/java/org/b3log/symphony/service/ApiV3IdentityApplicationTest.java、tools/api-v3-identity-application-test.mjs（新增），tools/api-v3-migration-chain-mysql-test.py（16份链与绑定/唯一约束扩展）。

文档：API.md、UPDATE_LOG.md、AGENTS.md、docs/api-v3-progress.md、docs/api-v3-development-plan.md、docs/api-v3-migration-chain-work.md、本文件；docs/api-route-inventory.md重新生成806全站路由/398浏览器引用，不与v3口径混用。

## 证据

`node tools/api-v3-identity-application-test.mjs`八组exit0，日志/tmp/rhythm-v3-identity-application.log：真实JPEG/PNG内存解析、MIME/损坏/大小/尾部；同根申请/材料/回执、唯一pending/重放/异图冲突；申请人不能取图、审核员一次消费、已阅仍pending及重放不恢复；实际第一方SQL与凭据检查；回执/读取/commit故障及丢ACK；实际Dispatcher+Bearer+写门禁、二进制上传、元数据/一次性图片响应；实际私有路径Log4j过滤；每个应用/材料INSERT、DELETE、readAt UPDATE之后的SQL故障完整回滚及损坏摘要拒绝。生产核心/处理器/仓储SQL/严格JDBC/幂等/证明授权参与；SQLite的FOR UPDATE剥离、基础仓储连接、token查找、外围缓存和响应发送为显式适配，不等于真实InnoDB服务并发、完整IoC或网络图片传输验收。

`python3 tools/api-v3-migration-chain-mysql-test.py`五组exit0，真实隔离MySQL8.4.11执行16份有序迁移，22张新表字段/数值类型/VARCHAR长度对repository.json；现有scope/旧安全红包/验证码/私信兼容、旧ticket升级；精确绑定与改名后重跑、不活跃时不补权、pending唯一约束与完结后释放。日志/tmp/rhythm-v3-identity-migration-chain.log；容器无网络/发布端口，结束已删除，非业务库；不当作服务并发验收。

`node tools/api-v3-security-test.mjs`六组exit0，/tmp/rhythm-v3-identity-security-regression.log，验证新增最小授权方法没有改变既有当前密码/TOTP/全凭据失效与HTTP链。未运行Maven或前端build（无前端改动）。Java25隔离编译均由专项runner完成，不是完整应用编译。

## 2026-10-24 真实MySQL服务核心与跨进程验证

新增`tools/api-v3-identity-application-mysql-test.mjs`及`src/test/java/org/b3log/symphony/service/ApiV3IdentityApplicationMysqlTest.java`。以实际认证服务、证明授权、幂等、SQL写primitive和严格JDBC执行；三个私有表从原迁移完整取DDL，仅将表名改为测试别名，LONGTEXT、INT及唯一pending约束保持。账号/token/凭据/回执仓储端口仍为明确夹具：基本fixture DDL将TEXT主键调整为VARCHAR、超INT时间调整BIGINT；不将这些外围表说成全部生产DDL。Reviewer fixture预置用户2，真实迁移绑定行为另由迁移链专项覆盖。所有FOR UPDATE保留，没有进程内模拟锁。

`node tools/api-v3-identity-application-mysql-test.mjs`最终exit0，日志`/tmp/rhythm-v3-identity-mysql.log`：先在真实MySQL重跑完整8组认证专项（含二进制HTTP处理器、MIME、当前授权、申请/材料/回执及逐语句故障回滚、commit失败/lostACK、传输日志），再执行3组独立JVM竞争：

1. 相同owner/key/有效图片：实际观察InnoDB LOCK WAIT，两个进程均成功且返回同一id，独立连接检查只有一申请、一材料、一回执。
2. 相同owner/不同key：实际LOCK WAIT后一个成功、另一个409，失败请求不留下回执，仍只有一份待处理申请。
3. 指定审核员并发open：实际LOCK WAIT后仅一个进程收到正确合成PNG，另一个410，独立连接检查材料已删除、readAt准确、申请仍pending且占用pending资格，原创建回执仍只有一个。

共有8个原场景与3个竞争场景，不把辅助verify的PASS行重复计为新增场景。当前第一方授权先锁共享client namespace，观察到的竞争锁等待不声称只发生在材料行；测试证明跨JVM完整核心的SQL串行结果，不是对连接级别吞吐的承诺。

环境核对MySQL8.4.x、REPEATABLE-READ、STRICT_TRANS_TABLES和实际LONGTEXT。容器使用本机随机回环端口，拒用3306，没有连接业务库；所有子进程结束，容器和临时夹具目录已清理。不是完整框架IoC、真实HTTP网络断连、代理缓存或两个实际部署后端的验收，没有运行Maven。

上述为前阶段材料读取专项；本轮结论扩展见下一节。真实服务仍须IDEA重编译/重启后验收。人工步骤5中的核心MySQL跨进程检查现已完成，仍要在真实应用环境验证两个后端、HTTP响应丢失和缓存配置，不用核心专项代替整站闭环。

## 反漏扫与接口安全

五路均需Bearer权限，服务再限当前第一方；审核两路额外绑定指定ID，所有写入有写门禁。未新增anonymous.viewSkips或匿名入口，未改BeforeRequestHandler黑名单先跳/test、AnonymousViewCheckMidware首访/计次、CaptchaProcessor.validateCaptcha成功清黑名单和计数链。真实新IP/黑名单/解封/开发风控关闭场景未运行，不能称反漏扫全链已验收。

权限与越权：本人查询SQL重复绑定owner，审核只当前指定ID；token/client/user停用不因回执复用授权。CSRF：只Bearer头，不把旧Cookie/apiKey升级，也未破坏旧写入口。XSS：二进制MIME/真实图片校验、nosniff/附件/沙箱，元数据白名单，暂无未校验审核说明写入口；没有模板。注入：所有值预编译参数，SQL标识只内部Repository名称。SSRF：无外部URL输入或服务端抓取。滥用：大小、格式、有界解码预算、分页和共享写预算、单pending；未验证真实部署代理body限制或多节点总资源。泄露：私有DB材料、响应不缓存、无公开图床路径，回执不带图，服务异常转固定503、传输日志过滤；实际应用/代理/数据库日志与备份未验收。新增无公网静态资源/CDN/sourceMappingURL/目录或文件系统返回路径；本批不需要前端build，全站CDN远程下载/版本/目录枚举未重做。

## 完整人工验收步骤

1. 独立开发库先执行既有v3/credential依赖及新增SQL，确认enterprise审核员ID对应所需adlered账号；准备有效本人、其他用户、指定审核员和第三方客户端令牌，测试图片用合成JPEG/PNG。**通过IDEA重新编译/重启并确认后再联调**，不要把旧服务响应当成新代码；本轮已提前提醒，尚未获确认。
2. 从第一方客户端/API.md curl对enterprise入口直接发送一张图片二进制、MIME与key，期望200/id/time；本人GET列表和详情见pending、readAt/reviewedAt=0，没有图片/哈希/路径；他人详情404。第三方grant403，无Bearer或只Cookie/apiKey401，scope不足403。
3. 同key同图返回同申请，同key换另一张有效图片409；新key已有pending409。超过5MiB413，GIF/PDF以不支持的MIME上传415、伪装成PNG则422，伪造/损坏/类型不匹配图片422；解码暂超预算503，均无新记录。不自动发私信或勋章/角色奖励。核对真实HTTP代理接受合法5MiB范围。
4. 指定审核员GET审核列表应见applicantId及元数据，普通管理员/其他人403。POST open获得正确图片二进制和no-store/nosniff/附件头，在线材料记录已消失、readAt有值、状态仍pending；再次410，申请人也无法读材料。关闭审核员账号或撤销token后应403/401且不消费材料；改名不转权、他人改成同名也无权。
5. 首次open在commit前故障应保留材料且readAt仍0；成功commit后断网/丢响应则材料仍删除，重试410；创建同key重放不得恢复图片。两实际后端实例并发同key创建或同时open应只有一份申请/一次图像读取；独立JVM核心的真实MySQL竞争已验，完整应用与HTTP传输仍须补验。检查应用/代理/数据库日志和缓存无图片副本或公开路径；已有备份保留策略单独核实。
6. 未登录访问新路由、黑名单IP跳/test、合法/非法validateCaptcha及开发风控开关分别回归；旧认证入口与PC/mobile正常请求保持。读取后用指定审核员PUT decision提交approved/rejected及纯文本说明，期望本人GET显示结论、图片仍不可用、可再发新申请；同键重试原结果，新键改结论409。未读申请提交409；故障后应保留pending，新申请仍409。确认无自动角色/勋章/积分变更，原人工发放另行验收。

API.md内容仍需同步到网站API文档文章https://fishpi.cn/article/1636516552191；源码与隔离专项通过不代表v3全目标完成。

## 结论写入扩展（用户确认后）

新增PUT /api/v3/review/identity-applications/{applicationId}/decision。settings.write及第一方当前指定审核员，严格JSON只status/note，body4096字节；status仅approved/rejected，note可空、strip后256UTF16，拒标签/控制字符/坏代理对。未读409 material_unread，已决定新键409 application_reviewed。namespace/当前user-access→回执→当前申请→材料行确认已删除→更新status/note/reviewedAt/pendingOwnerId=NULL，同严格根。回执只id/status/time/note；成功释放pending；重放也重验当前审核资格，原记录重放不会改后来新申请。没有自动勋章、角色、积分或通知副作用。

本轮修改原service/processor、两份identity Java测试、MySQL runner、API.md/UPDATE_LOG.md/AGENTS.md/进度/计划/本记录/生成清单，没有新SQL或字段。SQLite runner当前11组exit0（/tmp/rhythm-v3-identity-decision.log）；MySQL runner重跑同11组并增加同键结论、相反结论两组跨JVM竞争，现5组竞争，最终exit0（/tmp/rhythm-v3-identity-decision-mysql.log）。竞争均观察InnoDB等待；同键只一个结论/回执，相反结论只有一方成功、另一方409，pending释放且材料不恢复。实际HTTP覆盖先读图409/正确读取/PUT决定200、越权403及MIME415；写后/回执/commit故障回滚、lostACK回放恢复、256纯文本限制、后续申请不受旧回执覆盖均通过。没有因此宣称IoC、真实网络/代理或人工发放完成。

原阶段“无结论”段落是历史状态，本节与当前源码为准；API文档已写完整申请→读图→结论契约，需要同步网站文章。
