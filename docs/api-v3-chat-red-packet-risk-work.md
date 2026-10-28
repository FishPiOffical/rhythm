# v3 心跳与猜拳红包扩展（2026-10-24）

## 用户已确认规则

在现有三类红包的同事务结算、持久化、幂等、无自动过期退款及未领完禁删除基础上增加heartbeat和rockPaperScissors。创建和领取都须显式布尔riskAccepted=true，不接受字符串或数字替代。

心跳money32..10240、count5..100、3%税；税后奖池P，E=floor(P/count)+10，一个大奖P+E，其他份额合计-E，全部金额之和为P，允许0。创建完整保存份额，未领份额不公开；领取前余额至少P，抽负数原子扣款，失败不占份额，发送者可参与。

猜拳money256..10240、固定1份、15%税；0石头/1剪刀/2布，发送者不能领取，领取者余额至少税后金额P。领取者胜得P；败扣P且发送者收回2P；平局领取者0、发送者收回P；税不退。发送者出拳仅本人或结算后可见。精确当前有效VIP4_YEAR/MONTH免税。

## 文件与当前状态

- `src/main/java/org/b3log/symphony/processor/ApiV3ChatRedPacketProcessor.java`：创建严格JSON白名单增加riskAccepted/gesture；领取传递严格JSON至共享四参claim，允许原安全三类空请求体。路由及权限不变：创建content.write+activity.write，详情content.read，领取activity.write；写入继续共享写预算。
- `API.md`：扩展既有三个红包接口的类型、金额份数、riskAccepted/gesture、风险结算及出拳隐私说明，增加猜拳领取curl；安全三类原请求与响应保持。猜拳结果增加gesture/outcome/senderRefundPoints，详情senderGesture仅条件可见。
- `UPDATE_LOG.md`：在261024适用日期的既有记录之前新增第三方开发者更新条目。
- `docs/api-v3-chat-red-packet-risk-work.md`：本轮规则、验收与限制。

共享核心修改：`src/main/java/org/b3log/symphony/service/ApiV3ChatRedPacketService.java`、`src/main/java/org/b3log/symphony/util/ApiV3RedPacketInputs.java`、`src/main/resources/repository.json`，新增迁移`sql/20261024_api_v3_chat_red_packet_risk.sql`。既有两张表与Repository复用，无新增Repository。扩展类型长度和出拳/退回结算字段须先部署迁移；原安全三类迁移`20261024_api_v3_chat_red_packet.sql`也必须已完成。当前源码已经完成集成；实际执行证据另列下方。

用户已于第110批确认：经典PC/mobile同步显式风险确认与出拳，发送者停用但账号存在仍可结算，删除409。实现与最终验收步骤以[经典衔接记录](api-v3-risk-legacy-bridge-work.md)为准；下文为原风险核心历史证据。

## 源码安全检查与实际验证边界

处理器三路均明确Bearer权限中间件，不新增匿名入口或anonymous.viewSkips例外，不接受Cookie/query或body apiKey替代Bearer。写路仍经ApiV3WriteMidware，严格JSON有长度预算、媒体类型/重复键/未知字段校验；riskAccepted与gesture的类型及按当前红包类型的业务要求统一由共享核心检查，避免只有HTTP校验、内部调用可绕过的差异。

CSRF判断基于该处理器仅接受Authorization Bearer，不新增依赖Cookie的身份路径；没有给兼容旧apiKey的入口直接挂页面CSRF。所有者、当前消息可读性和本人领取归属仍须共享核心当前事务复核。响应沿白名单，不把未领取分配、私有账本ID、联系方式或密钥返回；猜拳senderGesture须在当前sender或结算条件成立后单独投影。文本沿既有清洗规则，出拳/金额不拼SQL；此处理器不抓远程URL，不新增SSRF、文件路径、上传或公开静态资源。

以上为源码边界检查；本扩展隔离HTTP与真实MySQL定向证据见下方，完整IoC、PC/mobile浏览器、黑名单重定向或验证码解封仍未运行。未运行Maven；无JS/SCSS/FTL修改，也未执行前端build。无新增静态资源/CDN或sourceMappingURL；全仓静态反漏扫与真实目录枚举/返回路径运行未在本子任务执行。

## 完整人工验收

1. 在独立开发库先执行安全三类及风险扩展SQL。用户通过IDEA重编译或重启服务端并明确确认后，再联调`http://localhost:8080`。准备有手机发送者、有效/停用接收者、精确有效/过期VIP4、具备不同scope的Bearer，记录系统/发送者/领取者/税费账号余额。
2. 客户端先显示风险及可能扣款，再POST创建heartbeat money100/count5/riskAccepted=true。期望本金100、奖池97、税3，持久份额总97、大奖126、其余总-29；缺失/false/字符串riskAccepted，count4或101，非法整数均422且无消息、扣款或份额。不同用户包括发送者领取前余额须至少97；低于门槛失败且不占份额。验证负数确实扣款、0也占份额、总和守恒、重启后份额不重抽。
3. 分别创建发送者gesture0的猜拳money1000，税后850/税150；其他用户gesture2应赢850，gesture1应扣850且发送者收回1700，gesture0平局0且发送者收回850。用三个独立红包验证，并核对全部账户总额。发送者领取拒绝，领取者余额849拒绝且红包未结算，gesture缺失/负数/3/字符串及riskAccepted非true失败不写入。count省略或1合法，其他份数422。
4. 结算前从他人详情、原始聊天消息、历史接口、WebSocket中检查没有senderGesture或未领份额；sender本人详情可见自己的出拳。结算后详情和领取结果显示正确gesture/outcome/senderRefundPoints与带符号amount，无私有账本标识或敏感用户字段。反查安全三类详情与响应字段没有新增猜拳字段。
5. 同键重试创建/领取只一次扣款，同键换gesture或目标409；并发双领取最多一人结算，数据库/通知/消息/回执故障均不得半扣款或占份额。模拟提交成功但应答丢失，原键恢复同结果；重启及不同实例查询保持结算，不能从内存桶重分配。
6. 未领完风险红包经本人及管理员撤回/删除409；全部领取后按原权限允许删除。旧入口对新风险红包不得隐式代为接受风险；确认兼容策略后的预期错误或显式字段按最终契约验收。旧历史风险红包、旧新安全三类领取及0金额继续回归。
7. PC/mobile两套聊天室核对卡片和弹层，不把未验证浏览器旧代码当新后端行为。无Bearer/Cookie或apiKey冒充应401，缺scope403，跨用户不能改写sender或claimUser。新IP/黑名单分别回归全局/test、验证码解封及开发关闭风控；以上实际结果由运行验收记录补齐。

最终需把`API.md`同步到网站API文档文章`https://fishpi.cn/article/1636516552191`。


## 根集成执行证据

新增测试`src/test/java/org/b3log/symphony/repository/ApiV3ChatRedPacketRiskTest.java`，修改原`ApiV3ChatRedPacketTest.java`夹具与`tools/api-v3-chat-red-packet-test.mjs`。根最终 `node tools/api-v3-chat-red-packet-test.mjs` 退出0，日志 `/tmp/rhythm-v3-chat-red-packet-risk-root-final.log`，原10组和风险7组覆盖九种猜拳、九个心跳金额/份数边界、负积分/返还同根故障回滚、换键改拳拒绝、明文隐私、当前余额、全部领取后删除和实际注册HTTP契约。这里的HTTP使用真实Dispatcher与生产处理器、身份/响应边界适配，不是运行中的完整应用。

根`node tools/api-v3-chat-red-packet-mysql-test.mjs`最终五组退出0，日志`/tmp/rhythm-v3-chat-red-packet-risk-mysql.log`：安全三类原竞争三组；新增猜拳败者扣217/发送者返434的争抢单结算，以及败局退款故障全部回滚、等待者胜领取217。money256税后floor(256×0.85)=217、税39；测试首轮期望误算218已修正，不是业务算法修改。独立JVM/Connector-J连接实际保留FOR UPDATE，并观测InnoDB锁等待后才释放阻塞；各组核余额、税、回执、主账、事件与got。使用真实两表基础DDL加risk ALTER，其他表/Repository/缓存/通知revision/运输仍有明确夹具，不能宣称完整生产链运行或真实重启。一次性MySQL8.4.11容器仅随机127.0.0.1端口，最终已删除，未接业务库。

修改`tools/api-v3-feature-schema-mysql-test.py`后根执行两组退出0，日志`/tmp/rhythm-v3-feature-risk-schema-mysql.log`：基础两迁移各两遍+risk单次ALTER，四表字段类型匹配repository.json、type长度17、唯一索引及样本回滚；无网络无公开端口容器已删除。risk迁移为一次性ALTER，不能宣称可原样重复执行。

其他同步文件：`AGENTS.md`、`docs/api-v3-progress.md`、`docs/api-v3-development-plan.md`、`docs/api-v3-chat-payments-work.md`与原safe-work补验记录；全仓静态路由清单重新生成仍798声明/398浏览器引用，v3注册仍251，无新增匿名路径。无Maven、业务库迁移或实际服务端重启。
