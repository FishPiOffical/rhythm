# 积分、通知、签到与背包

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[事务框架](environment-framework.md)、[职业事件](professions-membership.md)、[文章写入](articles.md)。

## 积分锁与转账

- v3转账`ApiV3PointTransferService`完整账户对先于严格根、用户按ID锁；主款/费/通知revision/回执同提交，精确有效VIP4及实际费收款者免。重放从本人回执取得锁提示，仅当前actor必须有效；私有fee账本元数据投影后返回，后置主账验证去重修索引。旧转账未整体改造。
- 积分锁统一在 `AccountPairLocks`；旧直接转账与外层事务必须共用该池。多收款方通过 `ArticleMgmtService#withPublicationLocks`→`PointtransferMgmtService#executeWithTransferLocks`，在事务前按确定顺序取得完整账户对集合；持锁期间只允许重入原集合的子集，禁止临时扩展。`CommentPublishPoints`共用旧评论预留/扣费规则：普通支付作者、自评支付sys、私密额外付sys；任一匿名不实际扣费，但仍检查原余额门槛。
- 积分变更统一走 `PointtransferMgmtService`：独立流程用 `transfer(...)`（方法内自带事务）；若外层已有事务，必须用 `transferInCurrentTransaction(...)` 避免事务冲突。
- credit 应用积分链路：`POST /user/edit/points` 携带完整 `sourceAppId/sourceAppName/sourceScene/requestId` 时走 `ExternalPointAdjustMgmtService`，使用 `Pointtransfer` 类型 55、通知类型 42 与 `external_point_request` 保证幂等；账户方向仍为 `sys` 与用户，应用来源只作为可信快照和展示信息，内部请求编号不得通过积分记录接口公开。
- 发积分/扣积分约定：发积分用 `fromId=Pointtransfer.ID_C_SYS`、`toId=userId`；扣积分用 `fromId=userId`、`toId=Pointtransfer.ID_C_SYS`（常用类型 `Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT`）。
- 需要给用户发“系统转账通知”时：在转账成功拿到 `transferId` 后，调用 `NotificationMgmtService#addPointTransferNotification`，并设置 `Notification.NOTIFICATION_USER_ID` 与 `Notification.NOTIFICATION_DATA_ID=transferId`。

## 购买、改名与导出

- v3本人导出`ApiV3PostExportService`第一方当前密码/MFA，Latke默认RC，导出专用根须首次查询前选RR且不改全局隔离；完整RR本人快照→账户对→证明绑定重验→私有DB块/100积分/metadata/回执同严格根；24h下载逐块/总hash及当前授权后发ZIP，每分钟同事务清理并释放连接。先迁移20261024_api_v3_post_export.sql；禁止复用旧公开图床导出或把预算超限截断成功。
- v3普通积分改名在`ApiV3UserNameService`：双activity.write/settings.write与幂等，账户对→namespace→当前有效用户，名称/现有费用/回执同严格根；新主账scene=settings.user-name分列dataId新名/memo旧名，显示经`UserNamePointData`编码，禁止连字符拼接。提交/回放后修索引及失效名称缓存，重放不覆盖后来改名；旧/settings/username扣费链本批未改。
- v3邀请码购买`ApiV3InvitationPurchaseService`仅新业务检查当前邀请模式；账户对先于严格根，namespace→模式→当前用户→回执，扣费/码/回执同提交，TTL以真实码oId计龄。重放仅复核本人有效性并恢复原code，不保证旧码仍可注册；码不放入账本/失败日志字段。提交后主账锁验证并修复索引，内部transferId必须投影后返回；沿既有point来源及membership索引迁移，旧先造码再扣费保持且无code唯一约束，不宣称新旧整体原子或绝无碰撞。
- 改名卡新旧入口共用 `UserNameMgmtService`，用户名与背包同事务，旧改名卡根事务也须严格提交；v3 同时要求 activity.write/settings.write。注册、改名及未验证账号名称清理先锁会员统计 option，再当前读用户，最后锁背包，保持名称占用串行；注册保留原未验证账号策略。用户名规则共用 `UserNameRules`，缓存仅事务结束后失效并重读。普通积分改名仍有独立扣费链路，不能称其整体原子化。

## 通知与引用清理

- v3 通知的分类统一在 `ApiV3NotificationTypes`，读取走 `ApiV3NotificationQueryService`，状态写入走 `NotificationStateMgmtService` + 幂等事务；刷新通知必须在提交后发布。通知里的文章/评论需按 Bearer 阅读者重新检查可见性，禁止裸返 dataId、owner 或完整消息 JSON。
- 内容删除通知引用由 `ContentNotificationReferences` 统一区分直接ID、组合ID和reward记录ID；奖励清理返回被删reward ID，同事务清理相应通知，禁止仅按dataId扫删通知。旧AT未存目标类型，文章/评论ID碰撞时保留歧义AT；不得推测归属。
- 通知金手指：`POST /user/edit/notification`（`UserProcessor#sendSystemNotification`），使用 `gold.finger.notification` 校验；请求体使用 `userName` + `notification` + `goldFingerKey`，新正文优先写 `notification.content`（最大 4096），老库未补 `content` 列时仅兼容旧 64 字纯文本 `dataId` 链路。
- 定制系统通知渲染：`Notification.DATA_TYPE_C_CUSTOM_SYS` 在 `NotificationQueryService#getSysAnnounceNotifications` 处理；`dataId=1` 为固定“水贴提醒”模板，水贴检测新增通知需调用 `NotificationMgmtService#addSysAnnounceShuiTieNotification`，不要把 `"1"` 作为正文传给 `addSysAnnounceCustomNotification`；`content` 支持 Markdown 链接 `[文本](http/https://...)` 与换行，原始 HTML 会被转义后再清洗。

## 签到、活跃奖励与背包

- 系统背包存在 `cloud` 的 `gameId=sys-bag`；增减物品统一经 `BagMgmtService` 锁用户再锁背包记录，`CloudService` 仅保留兼容包装。免签卡的新旧入口共用一次事务扣卡和增加天数，v3 加入幂等事务；`putBag` 正数参数是增量、`maxTake` 是最终数量上限，不要把旧累加代码误判为翻倍。
- 签到/补签统一走`CheckinMgmtService`：先账户对锁，再同事务锁用户并写积分、连续记录和背包提示，提交后刷新缓存/积分索引。自然日规则为`DailyCheckinMutation`/`CheckinPatchMutation`；v3补签经幂等事务，旧补签共用核心，失败保留卡/提示且不额外发签到奖励；关闭的手动签到入口不得恢复。
- 昨日活跃度奖励的新旧入口共用 `LivenessRewardMgmtService`；先持有积分账号对锁，再开启事务、锁用户并检查当天原始转账记录，奖励与 v3 幂等结果同事务。积分冗余索引只在提交后刷新。日常签到仍由 `LivenessMgmtService` 活跃度触发，不能通过新 API 恢复已关闭的手动签到入口。
