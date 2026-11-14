# 职业、会员与勋章

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[当前昵称快照协议](../api-v3-membership-snapshot-work.md)、[职业部署](../profession-production-deployment.md)、[积分事务](points-notifications.md)。

## 职业来源与隐私

- 职业来源事件未关联外部应用/请求时保留sourceAppId/externalRequestId为NULL，不能以空字符串占位；二者有组合唯一约束。消费验证须区分受控来源输入与真实业务捕获/结算。
- v3职业成长提示/排行/公开资料由`ApiV3ProfessionGuideQueryService`、`ApiV3ProfessionRankingQueryService`、`ApiV3ProfessionPublicQueryService`只读投影；当前展示revision和等级scheme各自发布，scheme可绑定同职业退休revision。排行先按ranking隐私筛可见人群再连续编号，隐藏experience仍保留等级名次；公开资料十模块独立判断且experience跨贡献/动态省略，rarity/ranking共用可见榜口径。公开动态必须当前PUBLIC来源及目标可读，匿名来源不关联，HIDDEN对本人也无公开路径例外；超查询预算503而非伪造完整统计。
- 主职业设置`ApiV3ProfessionPrimaryService`只选当前已发布职业、免费且不造progress，按列更新privacy中的selection/userProgressVersion；旧职业隐私整行写仍有并发覆盖局限。`ApiV3MedalDisplayService`按本人/定义/持有锁设置display而非toggle，重复定义或持有409；两者重放前重查当前账号，原回执不覆盖后续设置。
- v3职业/勋章/会员读取分别在`ApiV3ProfessionQueryService`、`ApiV3MedalQueryService`、`ApiV3MembershipQueryService`。职业目录仅发布版本，本人进度按历史版本且不伪造零级；`ApiV3ProfessionDetailQueryService`提供本人真实详情及90天APPLIED非零记录，无progress为404，仅白名单概要、不返回来源正文/内部ID。本人勋章只读有效记录（含隐藏），公开仅有效展示，禁止复用会删过期记录的旧查询；会员仅本人状态和已知权益白名单，不裸返configJson/benefits。
- `ApiV3ProfessionSettingsService`的skip沿旧规则清空主职业，privacy沿旧六预设/十模块，仅按相关列和版本写入；`ApiV3MedalOrderService`按全部本人持有（含隐藏/过期）相邻移动后重编号，并列按order/oId，超技术预算503而非截断。两者每次及重放在严格根当前锁有效用户，回执不覆盖后续操作；旧隐私/勋章整行写未同步改造，不能宣称新旧整体原子化。

## 职业管理与自动化

- 职业成长系统完全独立于 `userAppRole`：定义、等级方案、自动化、来源事件、效果、用户汇总和贡献均走 `profession_*` / `user_profession*` 新表；效果和来源事件均先独立提交领取状态，再在所有权条件下完成事务，防止回滚后的并发覆盖。等级方案 `MIGRATE_ALL` 与 `SCHEDULED_SWITCH` 由 `ProfessionLevelMigrationService` 分批切换，定时方案由 `ProfessionScheduledSchemeService` 激活。首次选择职业的升级来源由 `ProfessionGrowthGuideQueryService` 根据已发布自动化规则动态提供，禁止在前端写死职业升级方式。停用职业由 `ProfessionRetirementMgmtService` 锁定定义与受影响用户，将系统通知与停用状态同事务提交，并在提交后刷新通知，禁止直接写通知表。
- 职业管理数据读取分为 `/catalog-summary`（分页摘要）和 `/catalog-detail/{professionId}`（单职业完整配置）；导入必须先走 `/config/precheck`，排序经 `/catalog/order` 在同一事务内校验并保存全部职业顺序，二者均限管理员登录态与 CSRF。
- 职业自动化执行器包含经验调整和系统通知；系统通知配置 `notificationContent` / `notificationWhen`（`ALWAYS`、`LEVEL_UP`），由 `ProfessionAutomationNotificationService` 在职业效果事务中经 `NotificationMgmtService` 发送，不能直接写通知表。通知效果必须排在经验效果之后，正文变量为 `{职业}`、`{等级}`、`{经验}`、`{变化}`、`{奖励}`；等级方案迁移不会回放来源事件，但会对新跨越的每级分别补发允许的等级奖励，并为已发布 `LEVEL_UP` 通知创建独立幂等效果。金手指外部职业经验效果没有 `automationRevisionId`，通知判断必须先识别这种外部效果，不能查询自动化版本，否则效果会进入 `APPLY_RETRY`。

## 会员设置与昵称样式

- 捐助链接 GET 由 `ApiV3DonationPage` 固定配置站点/charge/point，不发 Cookie 或订单，浏览器仍需页面登录。会员昵称样式以本节的全量快照规则为准；指定1..100个ID的旧协议已于2026-11-06被替代，当前不得恢复 `userIds` 或裸返全库 `configJson`。历史变化及依据见 `history.md` 和 `../api-v3-membership-snapshot-work.md`。
- 会员写入在`ApiV3MembershipSettingsService`/`ApiV3MembershipOpeningService`：仅支持的bold/underline/color可改，VIP4效果仅精确YEAR/MONTH两码，空旧配置只合并显式字段；完整defaults仅供开通。无效/用尽券422不扣费，有效/永久禁止重复开通，自然日期限与积分/券/记录/礼包同严格事务。内部回执转账ID必须投影后才HTTP返回；提交后按主账PK核验并幂等修复积分索引，先迁移20261022_api_v3_membership.sql。新旧开通均事务前取账户对锁，但旧赠包/缓存/提交策略未整体改造，不宣称新旧全部原子或缓存强一致。
- 会员昵称styles已按用户最新要求改全量ID/configJson快照（只三展示字段，异常null），version为内容SHA-256，未变化不传list；仍profile.read。MembershipStyleCache代际由MembershipCache变更与档位提交后失效，最长30秒/自然到期兜底跨节点；HTTP no-store，客户端内存条件校验，不恢复userIds或裸返全部权益。

## 勋章与 VIP 页面

- 勋章管理页：`/admin/medal`
- 个人主页勋章由 `settings.js` 的 `Settings.initHome` 动态调用公开只读接口 `GET /user/{userName}/medal`，再渲染到 PC/移动端 `home-side.ftl` 的 `#metal`；不要依赖可选游戏组件的 `window.onload`，也不要改回 FTL 服务端渲染。
- 后端：`src/main/java/org/b3log/symphony/processor/MedalProcessor.java`（`showAdminMedal`、`register`）
- 前端：`src/main/resources/js/medal.js`
- 模板：`src/main/resources/skins/classic/pc/admin/medal.ftl`、`src/main/resources/skins/classic/mobile/admin/medal.ftl`
- 管理 API：`/api/medal/admin/list`、`/api/medal/admin/search`、`/api/medal/admin/grant`、`/api/medal/admin/grant-batch`、`/api/medal/admin/revoke`、`/api/medal/admin/owners`、`/api/medal/admin/holds`
- 会员状态 API：`GET /api/membership/{userId}`（`MembershipProcessor#getUserMembershipStatus`）
- VIP 管理页：`/admin/vip`（`MembershipProcessor#showAdminVipManagePage`，classic/pc 与 classic/mobile 同路径模板 `admin/vip.ftl`）。
- VIP 管理 API（仅 `adminRole`）：`/api/admin/vip/list`、`/api/admin/vip/add`、`/api/admin/vip/update`、`/api/admin/vip/refund`、`/api/admin/vip/extend`。
- VIP 管理服务关键方法位于 `MembershipMgmtService`：免费新增（不扣积分）、手工维护、按剩余天数退款并失效。
- VIP 按天退款成功后会复用“系统转账通知”（`Notification.DATA_TYPE_C_POINT_TRANSFER`）给用户发送站内通知。
- VIP 管理页配置项不再手填 JSON：前端依据等级 `benefits` 模板自动生成可视化表单，再序列化为 `configJson` 提交。
- VIP 管理页样式需注意 `home.css` 的 `.form--admin label { flex: 1; }` 会影响布局；配置项行在 `vip-admin.scss` 中需显式改为整行（label/builder 100%）并对 checkbox 使用类型选择器，避免控件被放大。
- 有效期字段：勋章 `expireTime`（毫秒，`0`=永久）；会员 `expiresAt`（可回填勋章到期）。
- 公开职业徽牌走 /api/v3/users/{userName}/professions/summary，按主职业隐私投影，不运行完整职业详情查询。公开勋章图片沿用 gen.metal.url，复用公开列表的访客门禁，只渲染当前公开且有效的授予；/me 图片仍需 profile.read。
