# v3 普通积分改名（2026-10-24）

## 范围与费用

新增PUT `/api/v3/me/settings/user-name`，总register可达，双activity.write/settings.write及Idempotency-Key。沿现有UserNameRules的1至64位ASCII字母、数字、连字符和保留名称规则，不trim，不接受下划线/中文/空白；沿ProfileWriteQuota及Censor审核。费用为`Pointtransfer.TRANSFER_SUM_C_CHANGE_USERNAME` → `Symphonys.POINT_CHANGE_USERNAME` → `symphony.properties`的`pointChangeUsername=1000`。隔离测试替身设100不是实际费用，不得据此写公开契约。

旧`POST /settings/username`本批不改造，其先提交名称再独立扣费局限保持；不将此前改名卡授权扩展为旧普通入口改造。无前端、无新表或迁移，不新增配置。

## 文件清单与职责

- `src/main/java/org/b3log/symphony/processor/ApiV3UserNameProcessor.java`：真实PUT路由，双scope与共享write中间件，只取Bearer本人；4096字节上限、严格body和幂等键。
- `src/main/java/org/b3log/symphony/service/ApiV3UserNameService.java`：事务前审核/配额与账户对锁；namespace→当前有效owner→回执；改名、费用主账与回执同严格根/保存点。回放重查当前账号且不覆盖后来改名；提交后校验真实主账修复积分索引，最终刷新名称缓存。
- `src/main/java/org/b3log/symphony/service/PointtransferQueryService.java`：新改名积分记录显示调用`UserNamePointData.description`，按sourceScene区分新旧格式；旧记录解释保持。
- `src/main/java/org/b3log/symphony/util/UserNamePointData.java`：新scene使用dataId保存新名、memo保存旧名，两者最多64字符；描述用真实Escapes/OWASP编码，消除合法连字符歧义与129字符拼接溢出，旧记录保留原解释。
- `src/test/java/org/b3log/symphony/service/ApiV3UserNameTest.java`、`tools/api-v3-user-name-test.mjs`：真实改名/积分/回执/严格JDBC及Dispatcher隔离测试；SQLite、外部审核、身份、缓存及其他服务使用适配器。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`：调用新Processor.register。
- `API.md`、`UPDATE_LOG.md`：公开请求、费用与返回字段及第三方更新记录。
- `docs/api-v3-progress.md`：243条总数（242HTTP+1WS），PUT42及模块证据边界。
- `docs/api-v3-development-plan.md`、`docs/api-v3-inventory-and-design.md`、`docs/api-v3-sensitive-account-plan.md`：同步本批范围、旧入口保持和后续验收。
- `AGENTS.md`：补充普通积分改名的长期关键链路。
- 本文件：完整人工链路和隔离限制。

复用而未为本批另造的关键链路：`UserNameMgmtService`命名空间及按列改名/缓存失效、`UserNameRules`、`PointtransferMgmtService.transferInCurrentTransaction`、`ApiV3IdempotencyMgmtService`、`ArticlePublicationCaches`、`ApiV3TransactionSavepoint`与`ApiV3Transactions`。注册/改名卡共用名称namespace，但旧普通扣费仍独立，不能宣称新旧全链原子化。

## 实际验证与限制

初版`node tools/api-v3-user-name-test.mjs`5组退出0，修复后由主代理执行`API_V3_ENCODER_JAR=/tmp/rhythm-v3-encoder-1.2.2.jar node tools/api-v3-user-name-test.mjs`6组退出0：名称/积分/回执提交，名称/积分/主账/回执/commit故障回滚，重放不覆盖后来名称，输入/审核/配额/余额边界，真实PUT双scope/Bearer/body/key，提交确认丢失与可重复积分索引修复、算术溢出。

真实积分显示消费侧审查发现的连字符split越界和129字符dataId问题已修复：sourceScene=`settings.user-name`时dataId保存新名、memo保存旧名，描述由`UserNamePointData`按格式分支并HTML编码；第6组覆盖`-`、`---`、`abc-def`、双方64字符、历史旧名HTML编码及普通legacy行。实际OWASP1.2.2参与编码，不是字符串替身。主代理随后为SQLite fixture补了128/255列长触发器约束，最终带触发器复验6组退出0，日志`/tmp/rhythm-v3-user-name-root-final.log`。

测试执行了真实formatter，不执行完整`PointtransferQueryService#getUserPoints`的所有依赖和页面渲染；不能将第6组表述为真实积分页已通过。

未执行Maven、数据库网络、完整IoC/HTTP、真实MySQL锁序与列长、真实Censor供应商、多实例缓存、PC/mobile浏览器或整个工作区发布审查。SQLite适配FOR UPDATE不证明真实MySQL并发；缓存适配器不证明真实名字别名和Sessions在竞争下正确。公开curl为文档示例，没有向生产发送写请求。

接入与文档检查：主代理运行`node tools/api-route-inventory.mjs`生成790条全仓路由/398浏览器引用；递归register元组与进度表243条完全匹配，GET121/POST55/PUT42/DELETE24/WS1、无重复。新增curl通过`bash -n`，所改tracked文件`git diff --check`通过，均不是网络联调。

## 完整人工用户链路

先请用户通过IDEA手动重编译或重启服务端；未确认前不要将浏览器旧行为视为新代码失败。只在授权开发环境`http://localhost:8080`使用测试账号，准备含activity.write/settings.write的Bearer及余额足够账号；先记录当前名、余额和积分记录。

1. 从客户端账号设置进入普通积分改名，PUT新合法唯一名称及固定幂等键。成功返回userName和paidPoints（当前1000），读取本人资料确认名称、积分减少一次；PC/mobile个人页、积分页以及新消息均展示新名，旧名查询缓存不能继续指向旧资料；历史消息快照允许保留。
2. 原键原体重试返回完全相同结果且余额不再减少；第二个键改另一名称后，回放第一个键仍返回历史结果，当前资料保持第二次名称；原键换请求体409。响应丢失或503不换键，原键重试确认已提交结果及积分记录索引不重复。
3. 当前名称、大小写同名及他人占用名称409，余额不足409；审核拒绝422、供应商故障503、共享配额耗尽429均不改名/扣费。空白、空串、下划线、中文、保留名、65字符、非字符串、多余userId/body字段、缺键/非法键422；超请求体413。
4. 分别测试连字符普通名、单独`-`与连续连字符、旧新名称双方64字符；读取PC/mobile积分页和v3积分记录，名称完整正确，主账字段不截断，不能因为显示解析使整页失败。核对老改名记录仍按既有方式显示；历史非法HTML名称须编码显示，不能执行标签。
5. 无Bearer、只有Cookie/apiKey、错误/过期令牌401；缺任一个scope403；已停用当前用户包括回执重放不得成功。其他用户不能通过userId或借用Cookie更改目标用户。账号资料隐私、其他字段和他人余额不变化。
6. 在隔离开发库注入名称写、扣费、主账、回执、提交故障，四者一起回滚；模拟已提交但应答丢失，原键重试只恢复一次收费。并发同名争抢只能一方成功，并发新旧改名/注册/改名卡核对namespace；旧普通积分入口未改造，不把其历史扣费缺陷当作本批已解决。
7. 刷新真实缓存/Sessions后复查旧名和新名查询；模拟索引失败后原键重试修复，确保积分记录不重复。多实例另测；当前进程失效不等于跨实例同步。
8. 回归新IP未授权不能访问改名，黑名单跳/test及/validateCaptcha解封、开发模式关闭风控行为；未改anonymous.viewSkips。对新增路由测试未鉴权/越权/CSRF/注入/批量请求/敏感泄漏。没有新静态资源/CDN、sourceMappingURL、上传下载路径，但全站目录枚举与静态基线仍需完整服务验证，不能默认为通过。

## 源码安全结论与待验收项

只接受Bearer并要求两scope，Cookie/apiKey不能替代；当前SQL账号有效性重验，返回仅userName/paidPoints，不暴露回执、内部transferId或凭据。名称严格ASCII/长度/保留规则，SQL参数化；无远程URL输入或抓取，无SSRF。审核在SQL/账户锁外，避免供应商调用占锁；常见失败只输出公开错误，实际共享积分日志路径仍需完整运行确认。新积分显示分支须HTML编码旧名/新名，公开文档不披露限流阈值、风控或内部元数据。

发布时把`API.md`内容同步到网站API文章`https://fishpi.cn/article/1636516552191`。
