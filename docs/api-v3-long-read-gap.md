# 长篇阅读登记实施记录

2026-10-30；规则已获用户确认，源码与隔离测试完成；20261030开发库迁移已应用，PID40308初步HTTP已验，最新请求体修复待再次IDEA重编重启。

## 已确认范围

- 当前有效登录读者，排除作者本人。目标为当前有效、可读、可列出的署名长篇；正文成功显示后显式POST，需要content.read与activity.write。SSR、预览、失败读取不登记，匿名暂不开放。
- 按服务端epoch的6小时窗口去重。客户端不传身份、窗口、计数或奖励，不触发奖励结算；后台原公式登记读者×10 + min(匿名读者,100)×8及职业long_article.read_settled事件保持。第二十六批没有修改后台结算；后续第二十七批保持正常公式并修复负数/溢出、提交和索引原子性，历史匿名计数不清理。
- 用户批准旧Markdown GET及经典ArticleMgmtService.incArticleViewCount的奖励登记也共用资格、去重与事务核心；不合资格不计奖励，旧Markdown响应与页面浏览量保持。此前只检索到Markdown调用、误以为无经典计分入口的结论现已更正：经典浏览量异步服务也确实调用capture；不是经典JS直接请求Markdown API。

## 实现

- POST /api/v3/articles/{articleId}/reads，ApiV3ArticleReadProcessor总register可达，content.read/activity.write及写预算；空请求或空JSON，未知字段和身份/窗口参数422，响应仅articleId与recorded。首次true，同窗口已登记false；不提供长期幂等回执，跨窗口可以新增。本人409，无权/不符合资格404，存储失败503。
- LongArticleReadCaptureService：严格根或当前owner保存点，作者hint仅用于锁序；排序锁当前读者与作者，锁当前文章并重验绑定和资格。等待锁后取当前窗口；用户claim与窗口increment同提交。经典调用对不合资格只跳过登记，存储错误不吞作成功。新链路不保存匿名IP/UA哈希。
- LongArticleReadUserRepository原先实际是article+user终身唯一，现改article+user+windowStart唯一。LongArticleReadWindowRepository在原upsert后锁定读state，避免Connector/J CLIENT_FOUND_ROWS将SETTLING/SETTLED未改变行误报为增量成功。保存点保证失败不吞掉本次claim，外层仍控制浏览量事务提交。
- website_v3 ArticleReadRegistration仅在实际完整长篇正文渲染后挂载，POST失败不遮正文、可重试；缺scope明确提示，不合资格不伪造成功。SSR、游客、本人不发送；文章/身份/卸载隔离迟到反馈，普通token轮换不重复提交，不持久化正文或凭据。预览组件不接入。

## 部署与验证

1. 先在IDEA停止Rhythm Local 8081，暂停旧/新阅读登记，再执行sql/20261030_api_v3_article_reads.sql。现有读者以firstReadAt回填原窗口，不重发历史奖励。旧唯一索引和自动建表缺索引两种环境均支持，可重复执行；历史同文章/用户/窗口重复需人工核对，本迁移不会擅自删历史。repo.json已加windowStart；窗口/结算唯一约束仍以既有20260809迁移为前置。
2. 迁移前只读发现3319读者表缺原uk_article_user，窗口/结算约束存在；新迁移已兼容。第二十九批用户停服并核原PID17956退出后，已在开发库应用并验证windowStart及三列唯一，0条原记录保持；未触碰8080/3306/workphone。用户重编启动PID40308后新POST初步通过，最新请求体修复尚需再次重编，不能重复把迁移列为未做。
3. Java25实际capture/User与Window仓储SQL、保存点/strict及Dispatcher/鉴权中间件在独立MySQL8.4上10组通过：原DDL迁移/重跑/缺索引，重复与跨窗，当前账号与目标，计数后故障/提交失败/丢ack，嵌套owner，已结算状态，双scope与空体，独立连接竞争，旧wrapper共用且匿名/本人不写。User/Article仓储只适配测试连接、方法从实际源码提取；token发行是最小测试替身，不把此测试当完整应用认证/IoC通过。临时MySQL已删除。
4. 客户端实际API/Vue新增7组，正文后的挂载/首次/换章/token刷新不重发/游客本人/失败重试/缺scope/退出迟到/SSR通过。当前全部84脚本951组通过（实际本次集合与PASS计数，非历史数量累加）；build通过；334源码AST边界0发现，77生成JS版本/sourceMappingURL0、无.map。契约292条=291HTTP+WS1（GET148/POST73/PUT46/DELETE24），无重复、漏文档或不可达。
5. 独立合成API+实际生产构建/网关浏览器：已恢复合成读者的正文可读，POST503只显示重试；修复夹具后显式重试收到recorded=false，提示消失。换下一章独立POST，390px稳定width/scroll均390。此处不是8081新Java验收；其他夹具功能不足曾显示热度/表态错误，已补正确形态，不改真实接口。截图browser-read-registration-error.png及browser-read-registration-mobile.png。

## 第二十七批：结算异常与原子性

- 独立MySQL首次运行实际旧结算服务：正常2名读者＋150匿名按封顶得到820积分、历史和职业payload符合原规则；负计数场景却改变作者积分，完整回归失败，确认不是HTTP夹具或缓存误判。
- 修复LongArticleReadSettlementService：对整数计数精确验证并拒负数，奖励乘加溢出直接回滚。共享积分账户对锁先于根事务，当前作者→当前文章→结算闸门/窗口；绑定重验，窗口锁定读取。不新增当前账号状态限制，正常历史计数仍按原公式结算。
- 根使用ApiV3Transactions.strict，不让Latke原commit吞错后继续发成功通知。主账、作者余额、历史、职业事件、两侧账本索引及完成状态同根提交，取消该结算的提交前异步索引；缓存边界结束后刷新当前用户，通知只在严格提交成功后发送。后台/管理员结算完成后释放线程SQL资源；已有外层事务的调用在开始前拒绝，不能私自提交或回滚caller。
- LongArticleReadWindowRepository新增当前事务getForUpdate；LongArticleReadSettlementRepository的claim回查锁定读取。只改变内部结算实现，不新增HTTP入口或更改v3响应；正常公式及历史匿名上限保持。
- 新实际服务/点转账原语MySQL回归共9组（连登记10组共19组）通过，覆盖：公式/上限/重复、负数及int溢出、历史/余额/主账/事件/索引/闸门/commit逐阶段失败后原状态及重试、commit丢ack、通知故障、零窗口、独立连接竞争、caller根保护。另两个独立JVM实测观察到InnoDB LOCK WAIT，释放作者锁后核一次奖励/历史/事件和两条索引，非进程锁冒充数据库并发。
- 测试使用独立容器/随机回环端口，读取相关精确迁移DDL；用户/文章/事件和点索引适配测试连接，缓存/通知是显式适配，不宣称完整应用IoC或真实职业消费通过。没有把奖励写入3319运行库；该库迁移和新Java仍待停服/IDEA重编确认。没有新前端修改，不重复全客户端构建；原84脚本951组/build证据保持。

追加实际文件：service/LongArticleReadSettlementService.java、repository/LongArticleReadWindowRepository.java、LongArticleReadSettlementRepository.java；新src/test/java/org/b3log/symphony/service/ApiV3ArticleReadSettlementMysqlTest.java和tools/api-v3-article-read-settlement-mysql-test.mjs，共享原tools/api-v3-article-read-mysql-test.mjs增补结算模式。尚未再要求新的独立重启确认，当前待停服与原阅读登记一起部署；源文件完成后仍需用户手动IDEA重编确认。未提交/推送。

## 人工完整复测（需先迁移与IDEA重编重启）

- 5173用专用B打开A的可见长篇：先显示正文再POST，数据库本窗口一claim、一计数；刷新/同窗直接重复/旧Markdown或经典页面混用都不新增，下一6小时窗口重新登记只加一次。不等待6小时的验证只能用独立可控时钟测试，不能改生产时钟。
- 游客无POST；作者本人无客户端POST，直接接口409；停用/删除读者401，缺任一scope403；隐藏/匿名/无效/讨论/非长篇及无效作者目标不计数。经典页面浏览量仍走原规则递增，旧Markdown正文和字段保持，不据计分改造声称旧全文读取权限已全面修复。
- 模拟计数写或提交失败应503且claim/计数都不留；响应丢失重试不会重复。并发同用户同窗仅一次，旧入口与新入口共用去重。错误提示重试后消失、正文保持可读，换章/退出后旧反馈不回填；390px与桌面都检查。
- 登记不立即改作者积分或造职业事件；后台窗口关闭后沿原结算公式与事件。完整应用定时结算、实际积分列表索引/职业成长消费、状态变化及真实跨节点矩阵尚待验收，不能把此次注册测试代替完整结算验证。

## 安全与未验项

新POST仅Bearer与双scope，不接Cookie/apiKey替代身份，真实Dispatcher隔离拒绝这些替代。全局Before黑名单先执行，未改anonymous.viewSkips、AnonymousViewCheckMidware或CaptchaProcessor；本批无匿名新入口、CSRF例外、HTML输入、远程抓取或上传下载路径。目标数字有界/SQL绑定/请求空体/响应白名单与写预算；无任意身份、窗口或批量奖励。无新CDN/map/目录资源；全站CDN/目录枚举/上传下载返回路径、生产匿名首访/周期/黑名单/验证码解封、真实IoC/APIkey旧链和新运行服务均未据局部测试默认通过。

实际文件：Rhythm processor/ApiV3ArticleReadProcessor.java、ApiV3Processor.java、ArticleProcessor.java，service/LongArticleReadCaptureService.java、ArticleMgmtService.java，repository/LongArticleReadUserRepository.java、LongArticleReadWindowRepository.java，resources/repository.json，sql/20261030_api_v3_article_reads.sql，test/service/ApiV3ArticleReadMysqlTest.java、tools/api-v3-article-read-mysql-test.mjs；website_v3 src/api/v3-article-reads.ts、src/components/articles/ArticleReadRegistration.vue、LongArticleReader.vue、tools/v3-article-read-registration-test.mjs（原有v3-article-reads-test.mjs保持，长篇reader旧专项隔离新子组件）。API.md/UPDATE_LOG.md/进度/联调记录/AGENTS与客户端文档同步。未运行Maven，未改FTL/APP或经典前端资源，未提交/推送。

## 第二十九批真实进展与请求体修复

迁移已完成，用户确认重编后的PID40308实测：无Bearer/合法旧apiKey替代401，本人409，B首次recorded=true、重复及省略体false；伪造身份/窗口422、错误媒体415。重复字段JSON却200，框架非法JSON清空getBytes后被空体分支接受；原实现漏用既有原始字节适配器，已仅将ApiV3ArticleReadProcessor改为RawBody+ApiV3Json。新测试以真实Netty内容及parseForm预处理，新增重复/非法/数组/空白/UTF8/超长/媒体/真实空体回归。原登记10组现11组，加结算9组共20通过，包括跨JVM等待；只改测试适配的remoteAddr避免日志路径无Channel的NPE，不用setBytes伪造原始体。

真实首篇当前仅B一条claim/计数1，第二篇0；未改变作者积分。原文/状态/权限SQL场景、缺scope、旧MD query/body key、经典页面异步及混合并发尚未运行，保留同一私有状态与原首条继续；不得把脚本写好当成功。最新控制器改动后已请求用户再次IDEA重编确认，迁移无需重跑。共享ApiV3WriteInputs未整体改造，本批不以此断言其他空体入口原始请求校验全部通过。

## 第三十批运行验收

用户再次确认重编后的PID44439：原始体拒绝、新旧MD query/body、经典异步、双scope/当前目标、混合并发均通过；两篇当前窗各3读者且无匿名。实际跨6小时边界保留旧B记录，新窗B可再次登记；自然Cron旧窗仅10积分，一主账/两索引/history/完成窗口及职业事件SUCCEEDED一致。浏览器B登录回正文与下一章、无阅读错误及warn/error通过，同窗不重复。具体证据、实际文件与完整人工步骤见联调第三十批；客户端积分/职业规则实际进度、经典JS两端、完整状态/网络与跨节点矩阵仍未据此验收。本批无新Java/FTL或迁移，无需再次重启。

第三十一批补验：真实作者/points首行长文奖励+10、当前与交易后余额3206，与主账一致。开发库职业自动化为0、作者进度为0、此事件effect为0，事件成功表示无规则完成，不能认定经验增长；不擅自添加规则或重放事件，后续带已发布规则的成长仍待验。会员客户端独立修复与完整文件/人工步骤见联调第三十一批。
