# v3 签到状态与昨日活跃奖励客户端（2026-10-27）

本批把Home/活动中心共用CheckinPanel迁移至现有v3三资源；新增可选expectedDate校验防止跨日未确认请求领取另一日奖励。旧空体/{}请求保持兼容，总注册285条不变。无FTL/SQL/新Repository/APP，无Maven。已在联调前提醒：真实Java服务须先由用户通过IDEA重新编译或重启确认。

## 实际修改文件与行为

Rhythm：

- `src/main/java/org/b3log/symphony/processor/ApiV3ActivityProcessor.java`：领取正文允许可选expectedDate，其他字段仍拒绝。
- `src/main/java/org/b3log/symphony/service/ApiV3ActivityMgmtService.java`：可选8位有效日期入规范化幂等请求；不传仍{}；重放先取原回执，fresh日期变化映射409 reward_date_changed。
- `src/main/java/org/b3log/symphony/service/LivenessRewardMgmtService.java`、`src/main/java/org/b3log/symphony/util/LivenessRewardClaim.java`：兼容旧重载，新增预期日期沿原事务传入；锁本人后取服务器时间/日期，日期不同不查领款、不转账。原旧入口传null，行为保持。
- `src/test/java/org/b3log/symphony/util/LivenessRewardClaimTest.java`：日历、锁后跨日拒绝、余额/账本未变及匹配日期成功。
- `src/test/java/org/b3log/symphony/service/ApiV3IdempotencyMgmtServiceTest.java`、`tools/api-v3-write-test.mjs`：组合实际幂等引擎与实际日期primitive，已提交跨日重放不再转账，未提交旧日期新键回滚，改日期同键冲突。
- `tools/api-v3-activity-smoke.mjs`：更新可选日期的手工授权冒烟流程，新增坏日期验证；仅语法检查，本批未向真实服务执行领取。
- `API.md`、`UPDATE_LOG.md`及本记录/进度/AGENTS维护。

website_v3：

- 新增`src/api/v3-activity.ts`：签到/昨日奖励GET及领取POST，白名单/非假0、严格日期、同日期回执绑定。签到百分比不截断原值，进度条仅视觉限制100；门槛用服务端值。
- 新增`src/stores/v3Activity.ts`：原date/key跨SPA恢复、平台二次确认、身份边界清理，旧回执不修改当前GET。恢复时reward_date_changed表示同键回执未命中且旧日期未执行，可释放该原命令；其他未知恢复4xx继续保留。
- 新增`src/composables/useActivityStatus.ts`：两GET独立状态/错误/重试；当前凭据/请求序号隔离，成功写后当前重读，退出/卸载清理，无持久缓存或假默认。
- 新增`src/components/ActivityRecovery.vue`：AppShell唯一恢复入口与原日期/实际积分回执。
- 修改`src/components/home/CheckinPanel.vue`：Home/ActivityView共用状态面板，显式领取确认、取消/离页/身份隔离；不新增手动签到能力，只有checkedIn=true才显示已签到。资源失败分别显示错误，不依赖旧apiKey。
- 修改`src/layouts/AppShell.vue`挂恢复；`src/api/fishpi.ts`删除无消费者旧签到/奖励/活跃包装及fp.liveness持久缓存代码。已有浏览器历史缓存数据不声称已删除。
- 新增`tools/v3-activity-test.mjs`十四组实际API/Pinia/Vue/SSR专项；文档及AGENTS同步。

## 实际验证与限制

- Java25隔离`node tools/api-v3-activity-test.mjs`五组通过，含真实SQLite账本原测试及新日期primitive；`node tools/api-v3-write-test.mjs`六个既有套件通过，其中幂等八场景含跨日组合及32并发、真实Latke提交故障用例。日志中的注入提交异常属于预期故障演练。
- 未编译整个Java工程，未加载实际修改的HTTP/Service IoC链，也未用真实业务库验证新字段。日期primitive和通用幂等引擎有执行证据，不能替代完整服务运行验收。
- website_v3全部68脚本741组通过，14组新增；类型/客户端/SSR构建通过，74JS版本审计零发现，无map/sourceMappingURL。未增加依赖或CDN。
- 真实Chrome生产构建+SSR合成HTTP：1280桌面领取→已发放但503/状态GET503→离页→原日期原键恢复→当前claimed；两POST同键、合成积分只发一次。证据`ev_3f9222520db5f93525e0a82023ca1f699ddc6224591d87a81fbebcae03d52bce`。
- 390×844移动取消确认无发放，之后明确领取→未知响应→恢复→只读刷新当前状态，布局正常。证据`ev_2fb7a69975d5dd079aa7fab712d6f40e0cf7f741de866b24d01d302f1039d602`。
- 两场景无page-error/水合警告，有预期领取和GET503控制台错误；相邻随机/最近注册仍旧链路，不算本批迁移。所有协议/积分为合成数据，无生产领取。

## 人工完整链

1. 完成IDEA重编译或重启确认后，用有activity.read/write的开发账号进入首页签到区域或活动中心`/activity`；读取真实checkedIn/liveness/threshold/streak与昨日date/amount/claimed/claimable。游客不查询私人数据，缺scope/失效Bearer明确错误，无假0或旧缓存。
2. 签到和奖励任一GET503/断网时仅该资源显示错误/重试，另一资源继续可用；失败资源不得显示旧值或领取按钮。检查高于100%的原活跃值仍如实显示，未签到不能仅因百分比达标伪称已签。
3. 可领时点领取，核对服务端日期/当前金额后确认；取消不POST。成功显示当次实际积分回执并GET当前状态；无可领或已领409不重复入账。领取不是手动签到。
4. 用代理模拟已提交但响应丢失，离页后从全局恢复；原expectedDate/键不变、只发一次。成功后GET失败只重试读取。平台要求二次确认时保留原命令，取消无写入。
5. 模拟午夜：已提交旧键跨日返回旧回执，当前新日期GET仍可显示可领；若旧请求从未执行，原expectedDate跨日返回reward_date_changed、不转账，重新读取后由用户新确认领取当日奖励。不能以新日期替换未确认键的请求体。
6. 登录/正常刷新/退出/换号及迟到请求：普通token轮换重读状态并保留已建立待恢复命令；身份变化清状态/回执，旧响应不回写。确认期间离页/换号不继续POST。SSR首屏不输出私人状态；刷新页面会丢失内存恢复命令。
7. 以既有{}请求验证兼容领取/同键重放；expectedDate坏日历、null、过长/带换行或未知字段422。同键改expectedDate 409，不创建第二笔；检查账本、余额和回执同提交。
8. 桌面与390px检查按钮、百分比/门槛/连签、错误和全局恢复；回归匿名首访/test、黑名单IP、验证码解封及开发关闭风控行为。不要用生产领取验证。

## 安全与反漏扫复核

GET仍activity.read，POST仍activity.write+统一写中间件，当前actor由既有Bearer身份取得；无指定用户、金额或历史补领能力。expectedDate仅当前日期条件，严格8位真日历，不能改变服务器选择的奖励期。日期校验在原锁和根事务内，改日期影响请求hash；新旧{}请求的规范化不变。无新SQL/表、外部抓取或上传下载路径，无新增Cookie写或CSRF例外。

私人状态/回执字段白名单、纯文本展示和身份/异步隔离有专项；点击单在途且无后台轮询，成功只重读。没有新增匿名路由或anonymous.viewSkips，Before黑名单、首访/周期验证码、AnonymousViewCheckMidware和CaptchaProcessor解封未改。构建资源版本/map复扫通过，无新目录/CDN。

真实HTTP鉴权/CSRF/越权、完整Service/Repository/积分/日期提交路径、旧入口并用、MySQL跨实例/跨日竞争、业务积分公式、全局黑名单/test/验证码解封/开发模式、限流及目录响应均未运行验收；须IDEA更新后用开发环境逐项验证。隔离测试与合成浏览器不能证明这些项已通过。
