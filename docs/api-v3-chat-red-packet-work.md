# v3 安全三类聊天室红包（2026-10-24）

## 已确认范围

random/average/specify创建、详情、领取；money32..10240，count1..100，专属按当前有效其他用户ID去重绑定、份数取人数。随机money总额，普通/专属money每份；普通/随机3%、专属1%，税后向下取整，普通/专属乘人数，精确当前有效VIP4_YEAR/MONTH免税，无admin放宽。沿旧随机分配允许0，完整持久化并守恒；无自动过期/退款，重启继续领取。留言旧字符表过滤、20UTF16超长422。公开领取者资料/金额/时间，专属名单仅sender。旧领取桥接新head，旧历史不变；未领完消息禁止删除409，领完按原权限。

## 实际文件和行为

- `src/main/java/org/b3log/symphony/processor/ApiV3ChatRedPacketProcessor.java`：POST创建双content.write/activity.write，GET详情content.read，POST领取activity.write；严格body/key与共享写预算，身份固定Bearer本人。
- `src/main/java/org/b3log/symphony/service/ApiV3ChatRedPacketService.java`：账户对在根事务前，排序当前用户→本人回执→消息/head/份额；本金/税/消息/职业来源/有序事件/分配/通知/回执同严格根及保存点。创建沿发言手机/当前限制/复读准入；领取保留旧不要求手机的资格、当前有效性/禁言/共享风险额度，不执行发言命令。0积分仍占一份并记回执，不造0额账；同用户多键也仅一份，提交后校验主账修索引和投递。普通错误安全503，不把内部驱动异常交给HTTP记录。
- `src/main/java/org/b3log/symphony/util/ApiV3RedPacketInputs.java`：确认的输入/价格与旧分配算法，无外部抓取。当前不接受心跳/猜拳/dice。
- `src/main/java/org/b3log/symphony/model/ChatRedPacket.java`、`repository/ChatRedPacketRepository.java`、`ChatRedPacketSlotRepository.java`：新head和私有份额，head锁串行所有领取，ordinal唯一；损坏数量/分配/收款/回执类型拒绝，不把损坏状态当成功。
- `sql/20261024_api_v3_chat_red_packet.sql`、`src/main/resources/repository.json`：两新表/唯一顺序和领取查询索引，super/模型不带前缀。须在部署代码前迁移，旧领取/撤回也会查询新head，未迁移不能当兼容部署。
- `src/main/java/org/b3log/symphony/service/ChatMessageMgmtService.java`：已锁消息后、收费权限回调/实际删除前，锁新head并拒未领完；损坏got/count503。领完删除仍走reaction/职业撤销，结算表保留防重复。当前源码反查未见其他chatRoomRepository.remove或SQL直接删除绕过；这是源码结论，不是对外部署清理脚本保证。
- `src/main/java/org/b3log/symphony/processor/ChatroomProcessor.java`：旧open先识别新head，优先中间件attr本人并调用共享legacyOpen；新消息只持久公开摘要/已领取资料，不存专属名单。旧历史路径保持，风险额度抽共享同一方法；新增已提交红包广播，不在根里发布。
- `src/main/java/org/b3log/symphony/util/ApiV3RedPacketTypes.java`、`src/test/java/org/b3log/symphony/util/ApiV3RedPacketTypesTest.java`：目录根写能力true，字段形状保持，公共文档明确仅三类；其他两类尚未开放。
- `src/test/java/org/b3log/symphony/repository/ApiV3ChatRedPacketTest.java`、`tools/api-v3-chat-red-packet-test.mjs`：真实生产服务/积分核心/聊天持久事件/职业捕获/严格事务与HTTP鉴权；SQLite、依赖仓储/缓存/通知revision/最终投递边界适配。旧HTTP验证提取真实handler桥接前缀，后续旧历史部分只记录fallback，不称完整旧Controller已验。
- `src/test/java/org/b3log/symphony/repository/ChatMessageMgmtServiceTest.java`、`tools/api-v3-chat-event-test.mjs`、`tools/api-v3-chat-publication-test.mjs`：给旧fixture接无新head的依赖，新增真实Repository编译依赖，回归共享保存/删除。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`、`API.md`、`UPDATE_LOG.md`、AGENTS、进度/计划/总路由清单、本文：总接线与契约/证据。

无JS/FTL/SCSS修改。只读追踪两套chat-room JS和PC/mobile模板：旧卡片读取net money/count/got/who/msg，打开统一旧open返回who/info/recivers；新card不放recivers，sender的专属名单由受保护详情/桥接本人响应提供。实际浏览器表现仍未验，不因无前端修改而宣称两端完成。

## 执行证据

根 `node tools/api-v3-chat-red-packet-test.mjs` 最终10组退出0，`/tmp/rhythm-v3-chat-red-packet-root-final.log`：创建税/奖池守恒、积分/消息/event/职业同提交、重放，7类创建故障回滚，专属隐私/通知，400种分配边界/VIP，7类领取失败回滚/原键恢复，真实三路由与scopes/严格body，旧DTO/重复打开，实际旧handler桥接前缀attr防冒用，0份额/无手机领取/禁言，创建和领取已commit丢确认后原key恢复单次结算并修索引。根修复中断代码的compound var和hash checked exception编译错误，并补安全错误/损坏记录拒绝。

根 `node tools/api-v3-chat-event-test.mjs`157组及 `node tools/api-v3-chat-publication-test.mjs`36组退出0；目录专项通过。日志分别`/tmp/rhythm-v3-chat-event-red-packet-root.log`、`/tmp/rhythm-v3-chat-publication-red-packet-root.log`、`/tmp/rhythm-v3-red-packet-catalog-root.log`。生产notification revision实际Repository源码已查，专项只适配通知表写/读，不能称完整revision运行通过。

未Maven/完整IoC编译、应用HTTP/WS、PC/mobile浏览器、多实例或旧全Controller运行，未触生产支付/图床/业务库。共享风控额度与复读状态沿旧进程内存，SQL回滚不能撤回已消费的内存额度；提交应答丢失的旧即时广播不保证补发，v3已提交change/history可恢复。不能宣称跨节点内存额度强一致或旧历史奖池也已原子化。

## 完整人工链

1. IDEA重编译/重启前，在专用开发库迁移红包SQL；当前有效有手机发送者、普通接收者、VIP4/非VIP、管理员/停用账户及content/activity scopes分别准备好。记录所有余额和消息/通知/积分记录。
2. 从第三方红包入口POST普通100×2，成功总扣200/奖池194/税6；详情count2/got0。PC/mobile两套聊天室卡片出现，旧页面点击可领，v3另一用户领取97；详情/旧弹层显示公开资料/真实金额时间，所有用户及税账号余额守恒。原key反复创建不再扣，原key换body409。
3. 拼手气32..10240、1..100份含低奖池/多份，记录持久全部份额、允许0，逐用户领到总奖池；0也增加got且同一用户不能占第二份。重启及不同实例继续原份额，不重新抽；有效精确VIP4免税、过期/假后缀不免，管理员同上限。
4. 专属按两个有效其他ID发送，创建时通知同提交；改名后仍按ID可领。sender可看名单，其他人/非接收者仅公开领取记录、不能看名单或领取；公开消息raw JSON不含recipientIds/recivers。旧页面领取/重复打开与v3共用结果，无重复入账；旧历史红包仍走原分支。
5. 部分领取后本人/管理员通过现有撤回删除入口均409、消息/费用/reaction/职业不变化；领完按原权限允许删除，保留已结算回执，旧键回放只原结果，不重造消息或入账。损坏got/count/份额明确503，不藉此删剩余奖池。
6. 无Bearer/Cookie或apiKey冒充v3拒绝；创建缺任一scope403，详情缺content.read403，领取缺activity.write403。当前停用/禁言/风控、余额不足、非法类型/整数/越界/留言、重复或本人/停用专属接收者均拒绝。普通接收者无需新增手机资格，发送仍沿发言手机要求。
7. 开发库注入head/slot/资金/税/通知/消息/event/职业/回执/commit故障，所有SQL状态回滚；领取失败不消费份额/到账。模拟已提交应答丢失，原key重试恢复一次，索引不重复；并发不同用户/同人不同key、旧新入口竞争仅可领取应得份额。SQL实际锁竞争需真实MySQL，不用SQLite通过代替。
8. 新IP与全局黑名单/test/验证码解封、anonymous.viewSkips、开发关闭风控回归；现有getMessage匿名实际401，新API显式Bearer。检查公开响应/实时事件/HTML/日志不泄名单或联系方式，旧Cookie/apiKey桥接按中间件身份、不直接加会破API的CSRF。静态/CDN版本号/sourceMappingURL/目录枚举、真实浏览器XSS/CSRF及上传下载全站基线未验，本批没有新增静态/CDN或上传下载路径。

API.md需同步网站API文章 https://fishpi.cn/article/1636516552191。剩余心跳/猜拳规则与实现仍在v3范围，不能将本文件的“三类”说成全部红包完成。

补充DDL证据：`python3 tools/api-v3-feature-schema-mysql-test.py`在无网络/无公布端口的一次性MySQL8.4.11中执行两份新SQL各两遍，核四表字段/类型及唯一索引，样本插入回滚，两组退出0，容器已删；日志`/tmp/rhythm-v3-feature-schema-mysql.log`。这不验证完整Java/MySQL事务、真实数据迁移或应用启动。


## MySQL 独立进程竞争补验（2026-10-24）

新增 `src/test/java/org/b3log/symphony/repository/ApiV3ChatRedPacketMysqlTest.java` 和 `tools/api-v3-chat-red-packet-mysql-test.mjs`。根执行专项退出0，日志 `/tmp/rhythm-v3-chat-red-packet-mysql.log`。三组均运行实际生产红包/积分/消息事务核心，使用 Connector/J 8.3.0 与一次性 MySQL 8.4；每个竞争者为独立 JVM，实际观测 InnoDB `LOCK WAIT` 后才释放占锁者，不能靠 JVM 内账户锁掩盖 SQL 竞争。

1. 两名用户争抢一份，只有一人成功，另一人409。
2. 占锁者在份额更新故障回滚，等待者继续领取，失败方无积分、份额或事件残留。
3. 同用户不同幂等键竞争，两次返回成功但只到账一次。

各组核验发送者、税账号、两名领取者余额，以及已领数、实际资金记录与事件数量。红包两张表使用本项目实际迁移DDL（仅表名替换为夹具别名）；其余依赖表、Repository写入适配、缓存/通知revision/投递仍使用明确的测试夹具，未验证完整应用IoC、全部生产表结构或业务库迁移。保留真实 `FOR UPDATE`，未使用SQLite删锁替换。运行仅公布随机127.0.0.1端口，一次性容器已删除，未接业务数据库。这三组证据只覆盖已有安全三类的单份竞争，不替代随后心跳、猜拳的负数与返还结算验证。
