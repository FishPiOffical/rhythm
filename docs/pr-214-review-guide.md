# PR #214 功能汇总、文件清单与人工审核指南

- PR：[Api v3 #214](https://github.com/FishPiOffical/rhythm/pull/214)，核对日期：2026-11-04（北京时间）。
- 对比基线：`dcccde155746a0f39aa79cd2c5aedce0dbdde6b3`；PR头提交：`928f0846e1b5b72ca41563ac729b199d45abb156`。
- GitHub显示：47个提交，1186个文件，新增133,045行、删除10,462行；本地三点差异与之相符。新增1045个文件、修改141个文件，无删除或重命名。
- 本文按上述固定提交整理；后续PR有新提交时需重扫。本文本身不计入下方1186个文件，是随后生成的审核附件。

## 1. 本次交付是什么

本次为Rhythm增加完整的版本化`/api/v3`接口层，并重构部分旧业务入口，使新旧接口共用明确的权限检查、事务、幂等及收费核心。包含账号授权、内容与长篇、社区互动、聊天/私信/红包、设置、积分、会员/职业/勋章、表情、认证材料、导出、存档及实时能力。

当前路由与API.md审计为293条：GET149、POST73、PUT46、DELETE24、WebSocket1。这个数字是PR头的注册总量，不是293个独立产品功能，也不等于每个输入组合都经过真实HTTP验证。

**这不是仅增加新路由的PR。** 旧注册/重置、评论实时推送、付费正文、签到补签/改名卡、聊天室撤回/话题/红包、私信撤回、云存档、账号凭据和长篇计分链也有行为调整，应纳入回归。

`website_v3`是独立仓库，其Vue/TypeScript源码及页面迁移不包含在本PR文件清单内；本仓库的部分文档记录了跨仓库联调结果。官方APP源码也未修改，仅提供新扫码协议；不包含真实支付订单、全量后台迁移、其他未登记游戏和dice领取。

## 2. 已实现功能

| 模块 | 本次功能 | 建议先看 |
| --- | --- | --- |
| 基础协议与授权 | 独立v3资源身份、scope、统一错误与输入校验、幂等回执；WebSocket票据和当前授权重验。 | [ApiV3Processor.java](../src/main/java/org/b3log/symphony/processor/ApiV3Processor.java)、[ApiV3AuthMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/ApiV3AuthMidware.java)、[ApiV3WriteInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3WriteInputs.java) |
| 登录、OAuth与应用管理 | 密码登录、刷新/退出；PKCE授权码、授权同意/拒绝、令牌刷新/撤销；管理员登记/维护/停用客户端，回调或权限变化撤销旧授权。 | [ApiV3TokenMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java)、[ApiV3OAuthProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3OAuthProcessor.java)、[ApiV3ClientRegistrationService.java](../src/main/java/org/b3log/symphony/service/ApiV3ClientRegistrationService.java) |
| APP扫码协议 | 2分钟、APP与网页双端确认、一次性消费；签发独立APP令牌，支持后续刷新与撤销。只实现服务端协议，不修改APP源码。 | [ApiV3AppLoginService.java](../src/main/java/org/b3log/symphony/service/ApiV3AppLoginService.java) |
| 注册、找回与敏感设置 | 邀请码预留及验证码用途/账号绑定、注册和密码找回；手机/邮箱/密码/MFA第一方证明；凭据版本更新后撤销旧凭据。 | [AccountRegistrationService.java](../src/main/java/org/b3log/symphony/service/AccountRegistrationService.java)、[AccountPasswordResetService.java](../src/main/java/org/b3log/symphony/service/AccountPasswordResetService.java)、[AccountCredentialMgmtService.java](../src/main/java/org/b3log/symphony/service/AccountCredentialMgmtService.java)、[ApiV3SecurityProofService.java](../src/main/java/org/b3log/symphony/service/ApiV3SecurityProofService.java) |
| 个人资料与偏好 | 本人资料、头像、MBTI、标签、私有QQ、常用表情、隐私、语言/时区/皮肤/首页地址等严格读写；公开资料仅投影允许字段。 | [ApiV3SettingsMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3SettingsMgmtService.java)、[ApiV3ProfileTagsService.java](../src/main/java/org/b3log/symphony/service/ApiV3ProfileTagsService.java)、[ApiV3QqSettingsService.java](../src/main/java/org/b3log/symphony/service/ApiV3QqSettingsService.java) |
| 文章、问答与评论 | 文章/草稿和评论读写删除、投票/表态/感谢、付费阅读、问答采纳、作者付费置顶；详情增加当前问答、置顶、优选、评论开关与声明状态。 | [ApiV3ContentQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ContentQueryService.java)、[ApiV3CommentMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3CommentMgmtService.java)、[ApiV3ArticleStickService.java](../src/main/java/org/b3log/symphony/service/ApiV3ArticleStickService.java) |
| 长篇与专栏 | 当前可见章节过滤、目录分页、前后章上下文、段评及历史别名迁移、评论当前页定位；保留链接原目标并隐藏受限标题。 | [ApiV3PublicColumnQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3PublicColumnQueryService.java)、[ApiV3ParagraphQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ParagraphQueryService.java)、[LongArticleParagraphService.java](../src/main/java/org/b3log/symphony/service/LongArticleParagraphService.java) |
| 长篇阅读结算 | 显式阅读登记；新旧入口共用当前资格和6小时去重，后台奖励/双账本索引/职业来源一致提交。 | [LongArticleReadCaptureService.java](../src/main/java/org/b3log/symphony/service/LongArticleReadCaptureService.java)、[LongArticleReadSettlementService.java](../src/main/java/org/b3log/symphony/service/LongArticleReadSettlementService.java) |
| 当前权限与历史版本 | 文章/评论/段落/通知等以当前记录校验资格，避免旧缓存恢复正文；历史仅对作者或有历史权限的合法读者开放。 | [ApiV3ContentSnapshots.java](../src/main/java/org/b3log/symphony/util/ApiV3ContentSnapshots.java)、[ApiV3RevisionQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3RevisionQueryService.java) |
| 通知与私信 | 本人通知列表、详情、批量状态；当前内容标题和当前actor资料；无稳定作者ID的历史聊天室通知省略actor。私信发送/已读/撤回及实时修订，撤回只清准确关联的未读。 | [ApiV3NotificationQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3NotificationQueryService.java)、[PrivateMessageWithdrawalService.java](../src/main/java/org/b3log/symphony/service/PrivateMessageWithdrawalService.java) |
| 聊天室 | 普通消息与特殊卡片分离；撤回按ID归属、每日递增费用；持久化话题、弹幕、音乐/天气卡片、消息上下文、在线名单与思过崖。 | [ApiV3ChatProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ChatProcessor.java)、[ChatMessageWithdrawalMgmtService.java](../src/main/java/org/b3log/symphony/service/ChatMessageWithdrawalMgmtService.java)、[ChatTopicMgmtService.java](../src/main/java/org/b3log/symphony/service/ChatTopicMgmtService.java) |
| 五类红包 | 拼手气、普通、专属、心跳、猜拳；份额持久化、原子结算、积分与税额守恒，未领完禁删；旧入口桥接新记录，风险类型显式确认/出拳。 | [ApiV3ChatRedPacketService.java](../src/main/java/org/b3log/symphony/service/ApiV3ChatRedPacketService.java)、[ChatroomProcessor.java](../src/main/java/org/b3log/symphony/processor/ChatroomProcessor.java) |
| 积分、签到与背包 | 原子转账、邀请码购买、签到/活跃奖励、背包卡片与积分改名；失败回滚、成功幂等；保留明确约定的旧入口差异。 | [ApiV3PointTransferService.java](../src/main/java/org/b3log/symphony/service/ApiV3PointTransferService.java)、[ApiV3InvitationPurchaseService.java](../src/main/java/org/b3log/symphony/service/ApiV3InvitationPurchaseService.java)、[CheckinMgmtService.java](../src/main/java/org/b3log/symphony/service/CheckinMgmtService.java)、[BagMgmtService.java](../src/main/java/org/b3log/symphony/service/BagMgmtService.java) |
| 会员、职业与勋章 | 会员档位/开通/展示配置及指定用户样式；职业目录/本人记录/隐私/可见排行/成长提示；勋章显示排序及权限校验后的清洗SVG。 | [ApiV3MembershipOpeningService.java](../src/main/java/org/b3log/symphony/service/ApiV3MembershipOpeningService.java)、[ApiV3ProfessionPublicQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ProfessionPublicQueryService.java)、[ApiV3MedalImageQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3MedalImageQueryService.java) |
| 图片表情 | 分组/成员、全部组同步、分享快照/导入/迁移；严格名称及HTTPS URL，坏项目整批拒绝，事务保存。 | [ApiV3EmojiGroupService.java](../src/main/java/org/b3log/symphony/service/ApiV3EmojiGroupService.java)、[ApiV3EmojiItemService.java](../src/main/java/org/b3log/symphony/service/ApiV3EmojiItemService.java)、[ApiV3EmojiTransferService.java](../src/main/java/org/b3log/symphony/service/ApiV3EmojiTransferService.java) |
| 鱼游与社区发现 | 鱼游列表/投票/评论及OAuth网址校验后的待审投稿/编辑；清风明月、大事记、排行/统计、城市、最近注册/随机发现/公开日志元数据及用户/标签联想。 | [ApiV3FishGameSubmissionService.java](../src/main/java/org/b3log/symphony/service/ApiV3FishGameSubmissionService.java)、[ApiV3DiscoveryQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3DiscoveryQueryService.java)、[ApiV3DiscoveryExtrasQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3DiscoveryExtrasQueryService.java) |
| 复读机、关注与举报 | 复读投稿、明确设置/取消点赞及旧toggle共享核心；本人关注状态与动态；仅对当前可读他人目标举报，同目标待处理举报复用。 | [RepeaterLikeMutationService.java](../src/main/java/org/b3log/symphony/service/RepeaterLikeMutationService.java)、[ApiV3FollowQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3FollowQueryService.java)、[ApiV3ReportPublicationService.java](../src/main/java/org/b3log/symphony/service/ApiV3ReportPublicationService.java) |
| 企业认证 | 第一方企业申请、私有JPEG/PNG材料、独立审核记录；指定审核员一次读取后销毁材料，必须先读图再给结论，不自动授予权限。 | [ApiV3IdentityApplicationService.java](../src/main/java/org/b3log/symphony/service/ApiV3IdentityApplicationService.java) |
| 本人内容导出 | 完整本人当前文章/评论私有ZIP，包含本人付费区；稳定数据库快照，文件就绪与收费同事务，24小时逐次授权下载。 | [ApiV3PostExportService.java](../src/main/java/org/b3log/symphony/service/ApiV3PostExportService.java)、[ApiV3PostExportSnapshotService.java](../src/main/java/org/b3log/symphony/service/ApiV3PostExportSnapshotService.java) |
| Evolve云存档与捐助链接 | 仅登记的Evolve完整存档单槽、原样字符串、版本冲突和显式迁移，新旧登记游戏共用核心；捐助仅提供现有站内页面链接。 | [ApiV3GameSaveService.java](../src/main/java/org/b3log/symphony/service/ApiV3GameSaveService.java)、[ApiV3DonationPage.java](../src/main/java/org/b3log/symphony/util/ApiV3DonationPage.java) |

## 3. 变更规模与阅读顺序

文件数量较多，其中包含大量隔离测试、测试夹具和分批验收记录。建议先审授权和共享事务，再按模块分开审核，最后核对产物、迁移与证据，不按文件列表从头顺序读。

| 文件类别 | 数量 | 审阅用途 |
| --- | ---: | --- |
| 文档与验收证据 | 235 | API/更新日志、工作说明、JSON结果与交接 |
| 构建与资源配置 | 3 | pom、repository.json、日志配置等 |
| 数据库迁移 | 20 | 20份SQL，包含新表、索引、字段与权限种子 |
| 后端生产Java | 533 | 入口、权限、业务、事务、DTO与共享基础设施 |
| 生成CSS | 3 | 源码与生成产物、PC与移动需对照 |
| 经典JS源码 | 7 | 源码与生成产物、PC与移动需对照 |
| 生成min.js | 7 | 源码与生成产物、PC与移动需对照 |
| SCSS源码 | 4 | 源码与生成产物、PC与移动需对照 |
| 经典PC与移动模板 | 12 | 源码与生成产物、PC与移动需对照 |
| 后端Java测试 | 204 | 204个Java测试文件；与生产Java分开计数 |
| 审计与回归工具/夹具 | 158 | 静态审计、隔离runner、MySQL/并发夹具 |

### 建议第一轮必读文件

| 文件/区域 | 为什么先看 |
| --- | --- |
| [pom.xml](../pom.xml) | Java source/target从1.8改为25；确认CI、打包、运行镜像与依赖支持Java25，不能仅确认IDEA能启动。 |
| [Router.java](../src/main/java/org/b3log/symphony/processor/Router.java)、[ApiV3Processor.java](../src/main/java/org/b3log/symphony/processor/ApiV3Processor.java) | v3总注册及认证入口；继续跟到每个模块register，避免漏看只靠命名推断的鉴权。 |
| [ApiV3AuthMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/ApiV3AuthMidware.java)、[BeforeRequestHandler.java](../src/main/java/org/b3log/symphony/processor/BeforeRequestHandler.java)、[AnonymousViewCheckMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/AnonymousViewCheckMidware.java) | 独立Bearer身份、scope、匿名验证、黑名单及框架Cookie处理；旧页面与apiKey兼容边界。 |
| [ApiV3WriteInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3WriteInputs.java) | 从原始Netty字节严格验JSON；框架预处理后正文不能作为原始输入。认证入口还有独立大小/空体限制。 |
| [ApiV3Transactions.java](../src/main/java/org/b3log/symphony/util/ApiV3Transactions.java)、[ApiV3TransactionSavepoint.java](../src/main/java/org/b3log/symphony/util/ApiV3TransactionSavepoint.java) | 根事务/嵌套保存点/回滚与连接生命周期；审收费、消息、回执是否一起提交。 |
| [ApiV3ContentSnapshots.java](../src/main/java/org/b3log/symphony/util/ApiV3ContentSnapshots.java)、[ApiV3NotificationQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3NotificationQueryService.java) | 当前记录授权、白名单正文/身份、缓存收权及匿名/历史actor处理。 |
| [Sessions.java](../src/main/java/org/b3log/symphony/util/Sessions.java)、[AccountCredentialVersions.java](../src/main/java/org/b3log/symphony/util/AccountCredentialVersions.java)、[AccountCredentialMgmtService.java](../src/main/java/org/b3log/symphony/service/AccountCredentialMgmtService.java) | 凭据版本影响经典Cookie、旧apiKey/OpenID、v3令牌和WebSocket，不只影响新账号页。 |
| [repository.json](../src/main/resources/repository.json)及20份SQL | 新Repository登记、类型、字段默认值、表前缀、索引、权限种子与实际库升级一致性。不能以自动建表代替迁移。 |
| [log4j2.xml](../src/main/resources/log4j2.xml) | 新OAuth日志过滤器及插件加载；检查敏感凭据不进日志，同时诊断错误不被过度屏蔽。 |
| [API.md](../API.md)、[UPDATE_LOG.md](../UPDATE_LOG.md) | 对外契约及客户端兼容变化；尤其错误码、scope、参数、幂等与旧入口行为。 |

## 4. 人工代码审核重点（按优先级）

下面是应检查的风险和判据，**不是已经确认的新漏洞列表**。本文提供差异导读及静态接线复核，不替代1186个文件的逐行安全审计。

### P0：合并/发布前优先确认

1. **身份与路由。** 新资源只用预期的v3身份，不被Cookie或旧apiKey暗中补足；第一方、管理员、scope与owner权限逐层检查。未鉴权拒绝、合法令牌放行、另一用户越权404/403分别测试。OAuth token/revoke的有效协议凭据例外只能用于对应端点，不能变成普通资源身份或匿名skip。
2. **风控和反漏扫。** Before黑名单→`/test`、匿名首访/周期验证、`/validateCaptcha`解封及`anonymous.viewSkips`逐项核对。v3公开GET、注册/找回与旧兼容入口也要检查。开发模式关闭风控不能证明生产同等设置通过；公开静态/CDN版本、sourceMappingURL、目录枚举、上传/下载错误路径另验。
3. **当前权限与敏感投影。** 讨论/私密/匿名/无效状态、作者停用和阅读资格收回后，详情、列表、段评、历史、通知、reaction及实时事件必须一致。不让旧缓存恢复正文/身份；匿名不带真实作者；无稳定userOId的历史聊天室通知不按旧用户名认领。禁返IP、UA、手机号、邮箱、QQ、密码、MFA密钥及Token。
4. **事务与幂等。** 扣积分、库存/资格、业务写入、通知/职业来源及回执在约定同一根内提交；故障不吞卡/扣款。同键同请求恢复原结果、同键不同请求拒绝；同键/异键并发分别验。确认锁顺序、保存点失败、提交后缓存失效与推送异常不伪装业务回滚。跨实例不能只靠JVM锁推定安全。
5. **账号凭据撤销。** 密码/手机/邮箱/MFA成功变更后，旧Cookie、apiKey、OpenID、v3令牌/码/待授权及已连WebSocket失效；失败不改epoch。版本必须绑定认证时证明，不能重读新版本为旧凭据续权。普通读取/坏JSON不能自动续写刷新Cookie。
6. **数据库升级与Java25。** 先确认现库版本和迁移前置，备份并在独立环境执行；DDL可能隐式提交，一次性ALTER不等于可反复执行。特别是阅读去重迁移必须暂停旧/新登记，处理历史重复后建立唯一索引。CI、运行时、镜像需实际验证Java25；本次整理未运行Maven。

### 常见漏洞逐项复核表

| 项目 | 人工应查的边界 | 本次整理能给出的结论 |
| --- | --- | --- |
| 未鉴权访问 | 全部新/改入口的身份中间件、匿名盾、后台权限规则及协议例外 | 当前293条静态接线无工具命中；生产同等风控未验，不据此判全部运行安全 |
| 越权 | owner、参与者、管理员/审核员、当前可读文章、批量ID逐项检查 | 有分批真实与隔离证据；应按所审模块追到目标锁定后的重验，不以一次scope通过替代owner检查 |
| CSRF | 先区分v3 Bearer、第一方刷新Cookie及旧Cookie/apiKey入口；同源校验与表单来源一致 | 认证/OAuth有专项记录；不能给兼容apiKey的旧入口直接统一加页面CSRF而破坏客户端，亦不能放宽null Origin |
| XSS | Markdown生成HTML、SVG、卡片文本、昵称/标题、通知及经典管理模板 | 有清洗与白名单实现/专项；模板、JSON和WebSocket必须一起核对，不能只看前端转义 |
| 注入 | SQL参数、动态标识符白名单、JSON重复键及损坏编码、日志字段 | 原始JSON专项已有；逐查询检查绑定与标识符校验，未做全站渗透测试 |
| SSRF | 图片/音频/鱼游URL、OAuth回调、导出/下载路径 | 多数新URL契约只保存/投影不抓远端；检查实际调用链，旧链接展开和外部适配器不能据URL格式直接判安全 |
| 批量与滥用 | size/ID数量/输入长度/上传预算/时间窗口/幂等及重复收费 | 相关边界有模块回归；全站容量压测及生产限流未由本次静态检查验证 |
| 敏感泄露 | DTO/日志/缓存/推送、认证材料、私有ZIP、完整签名URL与返回路径 | 已有专项白名单与私有下载证据；全站CDN/目录及生产日志仍按发布清单复查，不能暴露密钥/材料或服务器路径 |

### P1：重点业务与兼容性

| 链路 | 人工审核判据 |
| --- | --- |
| 五类红包及经典桥接 | 总扣款=奖池+税，普通/专属按份数计算；份额/领取持久化且一次结算；负数/猜拳风险显式确认、无默认拳；只查看不结算；停用发送者仍可返还、账号删除409；未领完不能删消息。旧历史红包与新核心不能双扣/双领。 |
| 付费正文、评论删除与问答采纳 | 付费正文仅合法购买者/作者可读，扣前验当前阅读权限；删除拒绝不扣款；v3新采纳仅问答且悬赏至少20，成功旧键仍可安全回放；公开qna只给金额/已采纳存在性，不泄漏隐藏回答身份。 |
| 补签、卡片、改名、会员与邀请码 | 跨月/跨年自然日计算，失败不扣卡；名称/费用/库存原子一致；无效券422不收费；会员资格、默认展示白名单与礼包同提交；邀请码余额不足不生成，有效预留不能被其他手机号覆盖。 |
| 转账 | 手续费/收款账号豁免和有效VIP4精确判断；主款/费/通知/回执同提交、双账本索引唯一；旧admin代付特例不进入新接口。 |
| 私信与通知 | 参与者权限、当前有效接收者；撤回只删准确关联未读；列表/未读/消息同提交，推送后置。通知当前标题/actor、目标失效占位与未读状态一致。 |
| 长篇、段评及结算 | 目录/前后章仅当前可见章节；旧别名正确定位第2页，保留URL目标/query/fragment；阅读只计合资格登录他人，同窗去重；奖励与职业来源来自后台。结算任务是启动后每6小时，不承诺窗口到点立即发奖。 |
| 鱼游 | 只校验授权URL参数与同站回调后待审，不证明应用归属；待审字段与已公开字段分开，旧审核合并/拒绝不能错用旧OAuth。排序按已确认的总投票优先规则核对，避免依据旧AGENTS一句话误改。 |
| 表情与云存档 | 表情坏项整批拒绝、完整URL精确身份、分享快照独立；云存档只开放登记游戏和本人单槽，Unicode/大小/CAS/旧盲写递增版本一致；不能开放任意cloud键。 |
| 导出与认证材料 | 导出RR稳定本人快照、文件就绪才收费、逐次本人当前授权、24小时失效/损坏拒绝；认证图片原字节解析，一次读取原子删除，先读后审，不产公开URL或自动发权。 |
| 公开职业/会员/勋章 | 隐私先筛再分页编号，隐藏经验不由统计反推；会员只bold/underline/color；SVG严格清洗、不能执行脚本/外链，隐藏/过期/他人私有勋章拒绝。 |
| 普通消息与音乐/天气 | 普通正文不能隐式执行旧业务语法；特殊卡片字段严格校验，天气是客户端数据而非权威查询；URL不应由后端任意抓取。 |
| 成功登录记录 | 仅密码登录/APP消费成功在签发事务记录最新登录时间；失败、刷新、读取和第三方OAuth授权不更新，不改在线/IP/地理资料；检查公告接收范围变化。 |

### P2：产物、模板与维护性

- 经典两套聊天室JS：`chat-room.js`/`chat-room-2.js`，两套channel及其min产物；PC/mobile模板、基础SCSS/CSS应同步。生成产物须由构建产生，不手改min/CSS。
- OAuth PC/mobile授权页的表单来源、CSP回调来源与精确回调校验一起看；不能为修Origin:null而放宽同源。浏览器允许/拒绝回调都验证。
- 注册第二步PC/mobile新增code，verify.js及min必须一致；旧客户端缺code的兼容影响需在变更说明中明确。
- 大事记Markdown正文在管理预览、列表与详情均清洗；不能只检查API JSON而漏经典模板。
- `repository.json`差异较大，区分必要模型变更与格式噪声，核对新增Repository不手写symphony_前缀。
- 文档中的“待重启/未验”有历史批次，不覆盖最新结果；检查最新专项JSON及当前入口，而不是按旧描述重复开发或据新摘要抹掉未验项。

## 5. 旧入口行为变化速查

| 旧入口/共享链 | 本次需要回归的变化 |
| --- | --- |
| `/register`、`/register2`、`/reset-pwd` | 邀请码排他预留；注册第二步需code；验证码绑定用途/账号/联系方式并成功原子消费；匿名访问验证同步接入。 |
| 评论实时推送 | 私密正文按接收者阅读资格过滤；匿名作者ID清空，字段保留。 |
| 本人评论删除、`/article/reward` | 删除条件失败不扣款；原付费正文不再在无合法购买状态下返回，读取前按当前身份/文章权限检查。 |
| 补签、改名卡 | 原子成功才扣卡，失败保留卡与相关记录；日期按自然日处理。 |
| `/chat-room/revoke/{oId}`、旧setdiscuss | 作者ID归属、持久每日计次/递增费用、失败不扣款；话题持久化及原子扣费。 |
| `/chat-room/red-packet/open`及消息删除 | 新核心红包桥接；心跳/猜拳显式风险/出拳；已完成只读；未领完新红包禁止删除。旧历史原规则保留。 |
| `/chat/revoke` | 原子撤回并只清准确关联未读，保留无法归属的历史未读；提交后推送。 |
| `/api/cloud/get`、`/api/cloud/sync` | 登记游戏共用新核心，旧保存要求JSON并递增版本；非登记键保持旧链路。 |
| 旧Markdown读取及经典文章浏览计分 | 共用当前长篇资格与6小时去重，匿名/本人等不合资格阅读不进入奖励；经典浏览量仍可正常增加。 |
| 旧复读点赞、账号凭据、首页跳转地址 | 旧toggle共享原子点赞核心；敏感变更全凭据失效需重新登录；旧首页地址校验精确站点。 |
| 新人报道发布/编辑事件、后台新增文章 | 晋升角色缺失时保留原角色；后台新增文章补isGoodArticle默认值，防缺必填字段失败。 |

以上概括不表示所有旧入口全部原子化：例如旧积分转账、普通改名、旧表情写入和旧邀请码购买仍存在明确保留的边界，应按各专项说明审阅，不扩大“新旧共享”结论。

## 6. 数据库迁移清单

本PR20份SQL如下。下表是升级导读，**不能不核对现库状态就整批盲跑**：同日期脚本也有依赖；自定义jdbc.tablePrefix须对应修改SQL；repository.json/自动建表不会补齐全部索引、生成列和种子。DDL隐式提交，应备份并制定逐步失败恢复方案。

| SQL | 主要用途及依赖 |
| --- | --- |
| [20261016_api_v3.sql](../sql/20261016_api_v3.sql) | v3基础：客户端/令牌/幂等请求、聊天事件、文章/通知修订及socket票据；后续脚本依赖其基础表。 |
| [20261019_account_registration.sql](../sql/20261019_account_registration.sql) | 注册/重置验证码字段及绑定升级；旧短信记录不应获得新证明资格。 |
| [20261019_api_v3_oauth.sql](../sql/20261019_api_v3_oauth.sql) | 回调登记、授权请求和授权码；先有基础客户端/令牌结构。 |
| [20261019_api_v3_private_messages.sql](../sql/20261019_api_v3_private_messages.sql) | 私信权限及参与者查询索引。 |
| [20261020_api_v3_private_message_writes.sql](../sql/20261020_api_v3_private_message_writes.sql) | 私信写权限种子，依赖前一私信读权限脚本。 |
| [20261020_api_v3_private_realtime.sql](../sql/20261020_api_v3_private_realtime.sql) | 本人私信修订标记表。 |
| [20261020_api_v3_private_withdrawal.sql](../sql/20261020_api_v3_private_withdrawal.sql) | 未读关联消息ID；历史无关联记录保持不可精确归属。 |
| [20261022_api_v3_membership.sql](../sql/20261022_api_v3_membership.sql) | 积分账本接收者查询/唯一性等会员原子收费依赖，核对历史重复与索引。 |
| [20261023_account_credential_state.sql](../sql/20261023_account_credential_state.sql) | 独立凭据版本表；旧无版本凭据仅epoch0兼容。 |
| [20261023_api_v3_contact_verification.sql](../sql/20261023_api_v3_contact_verification.sql) | 联系方式验证码表，先执行凭据版本迁移。 |
| [20261023_api_v3_fish_game_submission.sql](../sql/20261023_api_v3_fish_game_submission.sql) | 当前/待审OAuth字段；依赖既有20260908_fish_game_remove_oauth.sql，注意一次性ALTER。 |
| [20261023_api_v3_mfa.sql](../sql/20261023_api_v3_mfa.sql) | 短期MFA准备表。 |
| [20261024_api_v3_chat_red_packet.sql](../sql/20261024_api_v3_chat_red_packet.sql) | 红包主记录及持久份额表；先于风险扩展。 |
| [20261024_api_v3_chat_red_packet_risk.sql](../sql/20261024_api_v3_chat_red_packet_risk.sql) | 风险类型/出拳/返还字段，先执行红包基础脚本；一次性ALTER。 |
| [20261024_api_v3_identity_application.sql](../sql/20261024_api_v3_identity_application.sql) | 申请/私有材料/审核员表；核对审核员种子绑定实际账号。 |
| [20261024_api_v3_post_export.sql](../sql/20261024_api_v3_post_export.sql) | 私有导出元数据及分块表。 |
| [20261025_api_v3_app_login.sql](../sql/20261025_api_v3_app_login.sql) | 短期APP双端确认请求表。 |
| [20261025_api_v3_game_save.sql](../sql/20261025_api_v3_game_save.sql) | 每人每登记游戏单槽存档表。 |
| [20261029_api_v3_point_source_scene.sql](../sql/20261029_api_v3_point_source_scene.sql) | 账本scene字段扩展；依赖既有20260717_external_point_source.sql，新红包场景长度需要该迁移。 |
| [20261030_api_v3_article_reads.sql](../sql/20261030_api_v3_article_reads.sql) | 阅读记录补windowStart并变更article/user/window唯一索引；先停旧/新登记，依赖既有长篇阅读及职业可靠结算结构；历史重复必须先核对，脚本不自行删历史。 |

## 7. 人工验收建议（可按审核人拆分）

均在独立验收环境使用测试账号与限额积分，先完成迁移和Java25启动；不要向真实用户广播或替别人操作材料。

| 审核包 | 入口与核心操作 | 成功结果 | 异常/边界 |
| --- | --- | --- | --- |
| A：认证与应用 | 登录→读取本人→刷新→退出；管理员登记测试应用→浏览器授权允许/拒绝→PKCE换码→撤销；APP双确认消费 | 身份/scope正确，退出/撤销后拒绝；成功登录时间更新，刷新不更新 | 坏JSON/错误媒体/重复字段、错误PKCE/回调、令牌重放、不同账号、非管理员、过期码；失败不破坏现有会话 |
| B：内容与长篇 | 发布/编辑→评论/回复→投票/感谢/采纳→付费正文→删除；长篇章节/段评/定位/阅读登记 | 当前状态一致、正常收费一次，列表/详情/实时一致；定位实际页；阅读后台结算与记录一致 | 匿名/私密/讨论、收权/停用/删除、低悬赏、关闭或未知评论开关、同键/并发、付费后断流原键恢复 |
| C：聊天与私信 | 普通发言/撤回、话题、卡片、私信发送/读/撤回；五类红包详情/领取 | 当前身份归属正确，消息/未读/费用/回执同提交；份额/奖池/税守恒，重启可继续 | 换名/换账号、旧无消息ID未读、停用/缺失发送者、未领完删除、已领取重复、风险取消/未出拳不发送；PC/mobile两种聊天室样式 |
| D：设置与资产 | 标签/QQ/头像/隐私→改名/卡片/签到→转账/会员/邀请码→导出/认证 | 严格输入，成功才扣费/卡；QQ不公开；ZIP私有；材料一次读取后可审核 | 余额不足、无效券、跨日/月/年、错误密码/TOTP、过期/错用途验证码、异键并发、其他用户下载/审阅拒绝、损坏ZIP |
| E：公开资料与发现 | 当前资料/列表→职业排行/隐私→会员昵称→勋章图片→鱼游/发现/举报 | 可见口径一致，不返内部对象或私密身份；当前资料与完整签名头像保持 | 隐私收回/停用/改名缓存预热、隐藏经验、非本人勋章、SVG危险内容、无稳定actor、自己举报/不可读目标、非法OAuth URL |
| F：存档与浏览器 | Evolve存取/CAS/显式迁移；表情建组/分享导入；桌面及真实移动设备导航、刷新、退出 | 原文无损，版本冲突明确；表情快照独立；无横向溢出/旧账号内容残留 | 同键重试、旧保存竞争、非法Unicode/超限、重复旧存档409、坏URL整批422、断网/换号/返回与历史位置变化 |

每项留存实际版本、入口、角色、步骤、结果及清理说明；区分源码/隔离测试、真实MySQL、完整应用HTTP、浏览器和真机证据。已有记录不能替代新部署环境验收。

## 8. 已有证据与仍未验证的范围

本次生成文档时重新运行了`tools/api-v3-contract-audit.mjs`及`tools/api-v3-route-access-audit.mjs`：293条契约/注册一致，无重复或孤立文档；鉴权接线224条required-bearer、51条optional-bearer、14条anonymous-gate、3条已审阅OAuth协议及1条WebSocket协议，无工具报告问题。此为静态接线结果，不是293条HTTP逐项安全认证。

可追溯的已有记录：

- [真实联调分批记录](api-v3-live-integration-work.md)：内容、收费、账号、表情、卡片、认证、会员、勋章、职业、存档、授权等完整应用证据及各自限制。
- [最终本地回归](api-v3-final-local-regression.json)：关联website_v3的96脚本1075项及类型/client/SSR构建；这是独立客户端仓库结果，不称为Rhythm的全量Java编译结果。本次整理没有重跑这些历史套件。
- [真实阅读到职业成长](api-v3-read-growth-chain-live.json)：原阅读登记→正常后台结算→10积分/双索引→15经验→本人API/实际页面→清理；没有手工发奖或本批强制再次结算。
- [当前完成度](api-v3-current-completion-audit.md)、[当前验收入口](api-v3-remaining-acceptance.md)：用于消除旧批次“待开发/待重启”的过期描述。

以下已获用户确认单列发布前，但仍是**未验**：[完整交接](api-v3-release-acceptance.md)。包括实际短信/邮件、外部图床与内容审核、生产同等验证码/黑名单/解封、全站CDN/目录/上传下载路径、多实例与10831节点、物理移动设备，以及经典风险红包最终人工领取。桌面390px不等于真机；静态零命中不等于全站安全通过。

发布前还需检查实际SQL/运行配置/Java25与两仓库版本对应，核对工作区及生成产物；API.md应同步到[网站API文档文章](https://fishpi.cn/article/1636516552191)。本文未替用户合并、推送或发布。

## 9. 完整改动文件清单（1186个）

按Git三点差异自动列出，`A`新增、`M`修改；行数为Git文本差异的新增/删除量，不能据此判断风险。类别内部按路径排序；本文新增文件不计入本PR头快照。文件链接指向仓库文件，审查时使用上方固定头提交。

### 文档与验收证据（235个）

| 状态 | 文件 | +行 | -行 |
| --- | --- | ---: | ---: |
| M | [AGENTS.md](../AGENTS.md) | 269 | 0 |
| M | [API.md](../API.md) | 14500 | 3219 |
| M | [UPDATE_LOG.md](../UPDATE_LOG.md) | 1073 | 0 |
| A | [docs/api-route-inventory.md](../docs/api-route-inventory.md) | 1256 | 0 |
| A | [docs/api-v3-access-boundary-live.json](../docs/api-v3-access-boundary-live.json) | 4508 | 0 |
| A | [docs/api-v3-access-compound-scope-live.json](../docs/api-v3-access-compound-scope-live.json) | 115 | 0 |
| A | [docs/api-v3-access-scope-live.json](../docs/api-v3-access-scope-live.json) | 1941 | 0 |
| A | [docs/api-v3-account-credential-work.md](../docs/api-v3-account-credential-work.md) | 43 | 0 |
| A | [docs/api-v3-account-mail-work.md](../docs/api-v3-account-mail-work.md) | 30 | 0 |
| A | [docs/api-v3-admin-article-default-regression.json](../docs/api-v3-admin-article-default-regression.json) | 58 | 0 |
| A | [docs/api-v3-anonymous-content-work.md](../docs/api-v3-anonymous-content-work.md) | 173 | 0 |
| A | [docs/api-v3-app-login-work.md](../docs/api-v3-app-login-work.md) | 63 | 0 |
| A | [docs/api-v3-article-permanent-stick-live.json](../docs/api-v3-article-permanent-stick-live.json) | 95 | 0 |
| A | [docs/api-v3-article-policy-current-live.json](../docs/api-v3-article-policy-current-live.json) | 50 | 0 |
| A | [docs/api-v3-article-policy-current-regression.json](../docs/api-v3-article-policy-current-regression.json) | 119 | 0 |
| A | [docs/api-v3-article-policy-regression.json](../docs/api-v3-article-policy-regression.json) | 111 | 0 |
| A | [docs/api-v3-article-protected-removal-live.json](../docs/api-v3-article-protected-removal-live.json) | 143 | 0 |
| A | [docs/api-v3-article-removal-live.json](../docs/api-v3-article-removal-live.json) | 364 | 0 |
| A | [docs/api-v3-article-stick-work.md](../docs/api-v3-article-stick-work.md) | 51 | 0 |
| A | [docs/api-v3-article-unavailable-regression.json](../docs/api-v3-article-unavailable-regression.json) | 521 | 0 |
| A | [docs/api-v3-auth-body-live.json](../docs/api-v3-auth-body-live.json) | 356 | 0 |
| A | [docs/api-v3-auth-body-regression.json](../docs/api-v3-auth-body-regression.json) | 140 | 0 |
| A | [docs/api-v3-avatar-normal-live.json](../docs/api-v3-avatar-normal-live.json) | 113 | 0 |
| A | [docs/api-v3-chat-card-work.md](../docs/api-v3-chat-card-work.md) | 54 | 0 |
| A | [docs/api-v3-chat-mutes-work.md](../docs/api-v3-chat-mutes-work.md) | 52 | 0 |
| A | [docs/api-v3-chat-payments-work.md](../docs/api-v3-chat-payments-work.md) | 124 | 0 |
| A | [docs/api-v3-chat-red-packet-risk-work.md](../docs/api-v3-chat-red-packet-risk-work.md) | 51 | 0 |
| A | [docs/api-v3-chat-red-packet-work.md](../docs/api-v3-chat-red-packet-work.md) | 55 | 0 |
| A | [docs/api-v3-classic-packet-readonly-live.json](../docs/api-v3-classic-packet-readonly-live.json) | 61 | 0 |
| A | [docs/api-v3-client-account-access-work.md](../docs/api-v3-client-account-access-work.md) | 40 | 0 |
| A | [docs/api-v3-client-activity-work.md](../docs/api-v3-client-activity-work.md) | 53 | 0 |
| A | [docs/api-v3-client-anonymous-content-work.md](../docs/api-v3-client-anonymous-content-work.md) | 46 | 0 |
| A | [docs/api-v3-client-app-login-work.md](../docs/api-v3-client-app-login-work.md) | 42 | 0 |
| A | [docs/api-v3-client-article-binding-work.md](../docs/api-v3-client-article-binding-work.md) | 33 | 0 |
| A | [docs/api-v3-client-article-interactions-work.md](../docs/api-v3-client-article-interactions-work.md) | 47 | 0 |
| A | [docs/api-v3-client-article-list-work.md](../docs/api-v3-client-article-list-work.md) | 44 | 0 |
| A | [docs/api-v3-client-article-owner-work.md](../docs/api-v3-client-article-owner-work.md) | 38 | 0 |
| A | [docs/api-v3-client-article-reads-work.md](../docs/api-v3-client-article-reads-work.md) | 36 | 0 |
| A | [docs/api-v3-client-article-realtime-work.md](../docs/api-v3-client-article-realtime-work.md) | 40 | 0 |
| A | [docs/api-v3-client-author-columns-work.md](../docs/api-v3-client-author-columns-work.md) | 47 | 0 |
| A | [docs/api-v3-client-author-regression.json](../docs/api-v3-client-author-regression.json) | 73 | 0 |
| A | [docs/api-v3-client-boundary-audit.json](../docs/api-v3-client-boundary-audit.json) | 1129 | 0 |
| A | [docs/api-v3-client-boundary-work.md](../docs/api-v3-client-boundary-work.md) | 37 | 0 |
| A | [docs/api-v3-client-breezemoons-work.md](../docs/api-v3-client-breezemoons-work.md) | 61 | 0 |
| A | [docs/api-v3-client-chat-barrage-work.md](../docs/api-v3-client-chat-barrage-work.md) | 45 | 0 |
| A | [docs/api-v3-client-chat-context-work.md](../docs/api-v3-client-chat-context-work.md) | 52 | 0 |
| A | [docs/api-v3-client-chat-music-work.md](../docs/api-v3-client-chat-music-work.md) | 42 | 0 |
| A | [docs/api-v3-client-chat-online-work.md](../docs/api-v3-client-chat-online-work.md) | 54 | 0 |
| A | [docs/api-v3-client-chat-paint-work.md](../docs/api-v3-client-chat-paint-work.md) | 43 | 0 |
| A | [docs/api-v3-client-chat-publication-work.md](../docs/api-v3-client-chat-publication-work.md) | 42 | 0 |
| A | [docs/api-v3-client-chat-red-packets-work.md](../docs/api-v3-client-chat-red-packets-work.md) | 38 | 0 |
| A | [docs/api-v3-client-chat-revoke-work.md](../docs/api-v3-client-chat-revoke-work.md) | 56 | 0 |
| A | [docs/api-v3-client-chat-source-work.md](../docs/api-v3-client-chat-source-work.md) | 36 | 0 |
| A | [docs/api-v3-client-chat-timeline-work.md](../docs/api-v3-client-chat-timeline-work.md) | 47 | 0 |
| A | [docs/api-v3-client-chat-topic-work.md](../docs/api-v3-client-chat-topic-work.md) | 52 | 0 |
| A | [docs/api-v3-client-chat-weather-work.md](../docs/api-v3-client-chat-weather-work.md) | 41 | 0 |
| A | [docs/api-v3-client-cities-work.md](../docs/api-v3-client-cities-work.md) | 49 | 0 |
| A | [docs/api-v3-client-comment-rewards-work.md](../docs/api-v3-client-comment-rewards-work.md) | 67 | 0 |
| A | [docs/api-v3-client-comment-votes-work.md](../docs/api-v3-client-comment-votes-work.md) | 48 | 0 |
| A | [docs/api-v3-client-comments-work.md](../docs/api-v3-client-comments-work.md) | 54 | 0 |
| A | [docs/api-v3-client-content-relations-work.md](../docs/api-v3-client-content-relations-work.md) | 71 | 0 |
| A | [docs/api-v3-client-directories-work.md](../docs/api-v3-client-directories-work.md) | 36 | 0 |
| A | [docs/api-v3-client-disable-work.md](../docs/api-v3-client-disable-work.md) | 131 | 0 |
| A | [docs/api-v3-client-donation-work.md](../docs/api-v3-client-donation-work.md) | 40 | 0 |
| A | [docs/api-v3-client-editor-emojis-work.md](../docs/api-v3-client-editor-emojis-work.md) | 50 | 0 |
| A | [docs/api-v3-client-editor-upload-work.md](../docs/api-v3-client-editor-upload-work.md) | 49 | 0 |
| A | [docs/api-v3-client-emojis-work.md](../docs/api-v3-client-emojis-work.md) | 58 | 0 |
| A | [docs/api-v3-client-finance-work.md](../docs/api-v3-client-finance-work.md) | 53 | 0 |
| A | [docs/api-v3-client-fish-games-work.md](../docs/api-v3-client-fish-games-work.md) | 49 | 0 |
| A | [docs/api-v3-client-following-feed-work.md](../docs/api-v3-client-following-feed-work.md) | 46 | 0 |
| A | [docs/api-v3-client-game-saves-work.md](../docs/api-v3-client-game-saves-work.md) | 42 | 0 |
| A | [docs/api-v3-client-home-reads-work.md](../docs/api-v3-client-home-reads-work.md) | 43 | 0 |
| A | [docs/api-v3-client-identity-work.md](../docs/api-v3-client-identity-work.md) | 54 | 0 |
| A | [docs/api-v3-client-inventory-work.md](../docs/api-v3-client-inventory-work.md) | 52 | 0 |
| A | [docs/api-v3-client-management-work.md](../docs/api-v3-client-management-work.md) | 55 | 0 |
| A | [docs/api-v3-client-medals-work.md](../docs/api-v3-client-medals-work.md) | 41 | 0 |
| A | [docs/api-v3-client-member-work.md](../docs/api-v3-client-member-work.md) | 41 | 0 |
| A | [docs/api-v3-client-membership-styles-work.md](../docs/api-v3-client-membership-styles-work.md) | 51 | 0 |
| A | [docs/api-v3-client-membership-work.md](../docs/api-v3-client-membership-work.md) | 58 | 0 |
| A | [docs/api-v3-client-milestones-work.md](../docs/api-v3-client-milestones-work.md) | 45 | 0 |
| A | [docs/api-v3-client-notifications-work.md](../docs/api-v3-client-notifications-work.md) | 44 | 0 |
| A | [docs/api-v3-client-points-work.md](../docs/api-v3-client-points-work.md) | 39 | 0 |
| A | [docs/api-v3-client-post-export-work.md](../docs/api-v3-client-post-export-work.md) | 55 | 0 |
| A | [docs/api-v3-client-posts-work.md](../docs/api-v3-client-posts-work.md) | 54 | 0 |
| A | [docs/api-v3-client-preferences-work.md](../docs/api-v3-client-preferences-work.md) | 37 | 0 |
| A | [docs/api-v3-client-private-inbox-work.md](../docs/api-v3-client-private-inbox-work.md) | 41 | 0 |
| A | [docs/api-v3-client-private-messages-work.md](../docs/api-v3-client-private-messages-work.md) | 41 | 0 |
| A | [docs/api-v3-client-profession-ranking-work.md](../docs/api-v3-client-profession-ranking-work.md) | 50 | 0 |
| A | [docs/api-v3-client-professions-work.md](../docs/api-v3-client-professions-work.md) | 54 | 0 |
| A | [docs/api-v3-client-profile-work.md](../docs/api-v3-client-profile-work.md) | 36 | 0 |
| A | [docs/api-v3-client-public-medals-work.md](../docs/api-v3-client-public-medals-work.md) | 34 | 0 |
| A | [docs/api-v3-client-public-professions-work.md](../docs/api-v3-client-public-professions-work.md) | 59 | 0 |
| A | [docs/api-v3-client-rankings-work.md](../docs/api-v3-client-rankings-work.md) | 50 | 0 |
| A | [docs/api-v3-client-reactions-work.md](../docs/api-v3-client-reactions-work.md) | 59 | 0 |
| A | [docs/api-v3-client-read-work.md](../docs/api-v3-client-read-work.md) | 27 | 0 |
| A | [docs/api-v3-client-repeater-work.md](../docs/api-v3-client-repeater-work.md) | 46 | 0 |
| A | [docs/api-v3-client-reports-work.md](../docs/api-v3-client-reports-work.md) | 48 | 0 |
| A | [docs/api-v3-client-revisions-work.md](../docs/api-v3-client-revisions-work.md) | 57 | 0 |
| A | [docs/api-v3-client-saved-content-work.md](../docs/api-v3-client-saved-content-work.md) | 55 | 0 |
| A | [docs/api-v3-client-security-work.md](../docs/api-v3-client-security-work.md) | 54 | 0 |
| A | [docs/api-v3-client-startup-work.md](../docs/api-v3-client-startup-work.md) | 42 | 0 |
| A | [docs/api-v3-client-statistics-work.md](../docs/api-v3-client-statistics-work.md) | 39 | 0 |
| A | [docs/api-v3-client-update-work.md](../docs/api-v3-client-update-work.md) | 78 | 0 |
| A | [docs/api-v3-client-user-card-work.md](../docs/api-v3-client-user-card-work.md) | 38 | 0 |
| A | [docs/api-v3-client-user-follow-work.md](../docs/api-v3-client-user-follow-work.md) | 58 | 0 |
| A | [docs/api-v3-client-user-relations-work.md](../docs/api-v3-client-user-relations-work.md) | 43 | 0 |
| A | [docs/api-v3-cloud-export-plan.md](../docs/api-v3-cloud-export-plan.md) | 135 | 0 |
| A | [docs/api-v3-comment-current-live.json](../docs/api-v3-comment-current-live.json) | 61 | 0 |
| A | [docs/api-v3-comment-location-live.json](../docs/api-v3-comment-location-live.json) | 159 | 0 |
| A | [docs/api-v3-comment-location-regression.json](../docs/api-v3-comment-location-regression.json) | 134 | 0 |
| A | [docs/api-v3-contact-work.md](../docs/api-v3-contact-work.md) | 181 | 0 |
| A | [docs/api-v3-content-cache-baseline.json](../docs/api-v3-content-cache-baseline.json) | 53 | 0 |
| A | [docs/api-v3-content-current-live.json](../docs/api-v3-content-current-live.json) | 99 | 0 |
| A | [docs/api-v3-content-current-regression.json](../docs/api-v3-content-current-regression.json) | 94 | 0 |
| A | [docs/api-v3-content-current-runtime-regression.json](../docs/api-v3-content-current-runtime-regression.json) | 63 | 0 |
| A | [docs/api-v3-content-membership-style-regression.json](../docs/api-v3-content-membership-style-regression.json) | 92 | 0 |
| A | [docs/api-v3-contract-audit-work.md](../docs/api-v3-contract-audit-work.md) | 31 | 0 |
| A | [docs/api-v3-contract-audit.json](../docs/api-v3-contract-audit.json) | 2164 | 0 |
| A | [docs/api-v3-current-completion-audit.md](../docs/api-v3-current-completion-audit.md) | 142 | 0 |
| A | [docs/api-v3-development-plan.md](../docs/api-v3-development-plan.md) | 2643 | 0 |
| A | [docs/api-v3-discovery-extras-work.md](../docs/api-v3-discovery-extras-work.md) | 46 | 0 |
| A | [docs/api-v3-display-normal-live.json](../docs/api-v3-display-normal-live.json) | 130 | 0 |
| A | [docs/api-v3-donation-work.md](../docs/api-v3-donation-work.md) | 53 | 0 |
| A | [docs/api-v3-emoji-group-work.md](../docs/api-v3-emoji-group-work.md) | 160 | 0 |
| A | [docs/api-v3-emoji-item-work.md](../docs/api-v3-emoji-item-work.md) | 201 | 0 |
| A | [docs/api-v3-emoji-transfer-work.md](../docs/api-v3-emoji-transfer-work.md) | 159 | 0 |
| A | [docs/api-v3-emoji-writes-plan.md](../docs/api-v3-emoji-writes-plan.md) | 98 | 0 |
| A | [docs/api-v3-emotion-settings-work.md](../docs/api-v3-emotion-settings-work.md) | 91 | 0 |
| A | [docs/api-v3-final-local-regression.json](../docs/api-v3-final-local-regression.json) | 6979 | 0 |
| A | [docs/api-v3-first-charge-ack-live.json](../docs/api-v3-first-charge-ack-live.json) | 59 | 0 |
| A | [docs/api-v3-fish-game-oauth-plan.md](../docs/api-v3-fish-game-oauth-plan.md) | 71 | 0 |
| A | [docs/api-v3-fish-game-submission-work.md](../docs/api-v3-fish-game-submission-work.md) | 155 | 0 |
| A | [docs/api-v3-functions-normal-live.json](../docs/api-v3-functions-normal-live.json) | 112 | 0 |
| A | [docs/api-v3-game-save-work.md](../docs/api-v3-game-save-work.md) | 64 | 0 |
| A | [docs/api-v3-home-redirect-regression.json](../docs/api-v3-home-redirect-regression.json) | 87 | 0 |
| A | [docs/api-v3-identity-application-work.md](../docs/api-v3-identity-application-work.md) | 85 | 0 |
| A | [docs/api-v3-inventory-and-design.md](../docs/api-v3-inventory-and-design.md) | 288 | 0 |
| A | [docs/api-v3-invitation-purchase-work.md](../docs/api-v3-invitation-purchase-work.md) | 97 | 0 |
| A | [docs/api-v3-invitation-work.md](../docs/api-v3-invitation-work.md) | 93 | 0 |
| A | [docs/api-v3-legacy-notification-actor-live.json](../docs/api-v3-legacy-notification-actor-live.json) | 41 | 0 |
| A | [docs/api-v3-legacy-notification-actor-regression.json](../docs/api-v3-legacy-notification-actor-regression.json) | 42 | 0 |
| A | [docs/api-v3-live-integration-work.md](../docs/api-v3-live-integration-work.md) | 1466 | 0 |
| A | [docs/api-v3-locale-redirect-live.json](../docs/api-v3-locale-redirect-live.json) | 260 | 0 |
| A | [docs/api-v3-login-activity-live.json](../docs/api-v3-login-activity-live.json) | 66 | 0 |
| A | [docs/api-v3-login-activity-regression.json](../docs/api-v3-login-activity-regression.json) | 62 | 0 |
| A | [docs/api-v3-login-return-live.json](../docs/api-v3-login-return-live.json) | 135 | 0 |
| A | [docs/api-v3-long-read-gap.md](../docs/api-v3-long-read-gap.md) | 60 | 0 |
| A | [docs/api-v3-markdown-work.md](../docs/api-v3-markdown-work.md) | 49 | 0 |
| A | [docs/api-v3-medal-display-work.md](../docs/api-v3-medal-display-work.md) | 128 | 0 |
| A | [docs/api-v3-medal-images-work.md](../docs/api-v3-medal-images-work.md) | 49 | 0 |
| A | [docs/api-v3-medal-order-work.md](../docs/api-v3-medal-order-work.md) | 102 | 0 |
| A | [docs/api-v3-medal-work.md](../docs/api-v3-medal-work.md) | 89 | 0 |
| A | [docs/api-v3-membership-opening-work.md](../docs/api-v3-membership-opening-work.md) | 106 | 0 |
| A | [docs/api-v3-membership-settings-work.md](../docs/api-v3-membership-settings-work.md) | 110 | 0 |
| A | [docs/api-v3-membership-style-current-live.json](../docs/api-v3-membership-style-current-live.json) | 136 | 0 |
| A | [docs/api-v3-membership-style-expiry-live.json](../docs/api-v3-membership-style-expiry-live.json) | 34 | 0 |
| A | [docs/api-v3-membership-style-expiry-regression.json](../docs/api-v3-membership-style-expiry-regression.json) | 66 | 0 |
| A | [docs/api-v3-membership-style-work.md](../docs/api-v3-membership-style-work.md) | 34 | 0 |
| A | [docs/api-v3-membership-work.md](../docs/api-v3-membership-work.md) | 181 | 0 |
| A | [docs/api-v3-mfa-work.md](../docs/api-v3-mfa-work.md) | 165 | 0 |
| A | [docs/api-v3-migration-chain-work.md](../docs/api-v3-migration-chain-work.md) | 49 | 0 |
| A | [docs/api-v3-next-rules-work.md](../docs/api-v3-next-rules-work.md) | 63 | 0 |
| A | [docs/api-v3-notification-actor-browser-live.json](../docs/api-v3-notification-actor-browser-live.json) | 37 | 0 |
| A | [docs/api-v3-notification-actor-live.json](../docs/api-v3-notification-actor-live.json) | 49 | 0 |
| A | [docs/api-v3-notification-client-actor-regression.json](../docs/api-v3-notification-client-actor-regression.json) | 52 | 0 |
| A | [docs/api-v3-notification-current-live.json](../docs/api-v3-notification-current-live.json) | 114 | 0 |
| A | [docs/api-v3-notification-extra-live.json](../docs/api-v3-notification-extra-live.json) | 131 | 0 |
| A | [docs/api-v3-notification-page-regression.json](../docs/api-v3-notification-page-regression.json) | 438 | 0 |
| A | [docs/api-v3-notification-title-baseline.json](../docs/api-v3-notification-title-baseline.json) | 9 | 0 |
| A | [docs/api-v3-oauth-protocol-work.md](../docs/api-v3-oauth-protocol-work.md) | 39 | 0 |
| A | [docs/api-v3-offline-identity-live.json](../docs/api-v3-offline-identity-live.json) | 57 | 0 |
| A | [docs/api-v3-offline-identity-regression.json](../docs/api-v3-offline-identity-regression.json) | 63 | 0 |
| A | [docs/api-v3-paragraph-link-target-regression.json](../docs/api-v3-paragraph-link-target-regression.json) | 77 | 0 |
| A | [docs/api-v3-paragraph-safe-link-regression.json](../docs/api-v3-paragraph-safe-link-regression.json) | 90 | 0 |
| A | [docs/api-v3-perfect-flag-baseline.json](../docs/api-v3-perfect-flag-baseline.json) | 10 | 0 |
| A | [docs/api-v3-perfect-flag-regression.json](../docs/api-v3-perfect-flag-regression.json) | 97 | 0 |
| A | [docs/api-v3-point-transfer-work.md](../docs/api-v3-point-transfer-work.md) | 109 | 0 |
| A | [docs/api-v3-post-export-work.md](../docs/api-v3-post-export-work.md) | 47 | 0 |
| A | [docs/api-v3-post-metadata-regression.json](../docs/api-v3-post-metadata-regression.json) | 72 | 0 |
| A | [docs/api-v3-profession-detail-work.md](../docs/api-v3-profession-detail-work.md) | 145 | 0 |
| A | [docs/api-v3-profession-growth-live.json](../docs/api-v3-profession-growth-live.json) | 177 | 0 |
| A | [docs/api-v3-profession-guide-work.md](../docs/api-v3-profession-guide-work.md) | 86 | 0 |
| A | [docs/api-v3-profession-nonempty-live.json](../docs/api-v3-profession-nonempty-live.json) | 129 | 0 |
| A | [docs/api-v3-profession-primary-work.md](../docs/api-v3-profession-primary-work.md) | 120 | 0 |
| A | [docs/api-v3-profession-public-work.md](../docs/api-v3-profession-public-work.md) | 124 | 0 |
| A | [docs/api-v3-profession-ranking-work.md](../docs/api-v3-profession-ranking-work.md) | 83 | 0 |
| A | [docs/api-v3-profession-settings-work.md](../docs/api-v3-profession-settings-work.md) | 143 | 0 |
| A | [docs/api-v3-profession-work.md](../docs/api-v3-profession-work.md) | 181 | 0 |
| A | [docs/api-v3-profile-current-live.json](../docs/api-v3-profile-current-live.json) | 263 | 0 |
| A | [docs/api-v3-profile-current-regression.json](../docs/api-v3-profile-current-regression.json) | 64 | 0 |
| A | [docs/api-v3-profile-privacy-cache-baseline.json](../docs/api-v3-profile-privacy-cache-baseline.json) | 61 | 0 |
| A | [docs/api-v3-profile-tags-work.md](../docs/api-v3-profile-tags-work.md) | 58 | 0 |
| A | [docs/api-v3-progress.md](../docs/api-v3-progress.md) | 1440 | 0 |
| A | [docs/api-v3-public-author-baseline.json](../docs/api-v3-public-author-baseline.json) | 10 | 0 |
| A | [docs/api-v3-public-author-live.json](../docs/api-v3-public-author-live.json) | 120 | 0 |
| A | [docs/api-v3-public-author-regression.json](../docs/api-v3-public-author-regression.json) | 78 | 0 |
| A | [docs/api-v3-public-columns-work.md](../docs/api-v3-public-columns-work.md) | 44 | 0 |
| A | [docs/api-v3-public-membership-plan.md](../docs/api-v3-public-membership-plan.md) | 72 | 0 |
| A | [docs/api-v3-purchase-concurrent-live.json](../docs/api-v3-purchase-concurrent-live.json) | 143 | 0 |
| A | [docs/api-v3-qna-accept-live.json](../docs/api-v3-qna-accept-live.json) | 149 | 0 |
| A | [docs/api-v3-qna-current-live.json](../docs/api-v3-qna-current-live.json) | 161 | 0 |
| A | [docs/api-v3-qna-display-regression.json](../docs/api-v3-qna-display-regression.json) | 67 | 0 |
| A | [docs/api-v3-qna-eligibility-regression.json](../docs/api-v3-qna-eligibility-regression.json) | 41 | 0 |
| A | [docs/api-v3-qna-state-minimum-regression.json](../docs/api-v3-qna-state-minimum-regression.json) | 45 | 0 |
| A | [docs/api-v3-qq-settings-work.md](../docs/api-v3-qq-settings-work.md) | 37 | 0 |
| A | [docs/api-v3-read-growth-chain-live.json](../docs/api-v3-read-growth-chain-live.json) | 132 | 0 |
| A | [docs/api-v3-release-acceptance.md](../docs/api-v3-release-acceptance.md) | 29 | 0 |
| A | [docs/api-v3-remaining-acceptance.md](../docs/api-v3-remaining-acceptance.md) | 46 | 0 |
| A | [docs/api-v3-repeater-work.md](../docs/api-v3-repeater-work.md) | 128 | 0 |
| A | [docs/api-v3-repeater-write-work.md](../docs/api-v3-repeater-write-work.md) | 120 | 0 |
| A | [docs/api-v3-report-work.md](../docs/api-v3-report-work.md) | 111 | 0 |
| A | [docs/api-v3-response-body-live.json](../docs/api-v3-response-body-live.json) | 48 | 0 |
| A | [docs/api-v3-response-body-regression.json](../docs/api-v3-response-body-regression.json) | 60 | 0 |
| A | [docs/api-v3-retired-bridge-work.md](../docs/api-v3-retired-bridge-work.md) | 39 | 0 |
| A | [docs/api-v3-revision-browser-live.json](../docs/api-v3-revision-browser-live.json) | 51 | 0 |
| A | [docs/api-v3-revision-current-budget-live.json](../docs/api-v3-revision-current-budget-live.json) | 85 | 0 |
| A | [docs/api-v3-revision-work.md](../docs/api-v3-revision-work.md) | 196 | 0 |
| A | [docs/api-v3-risk-dialog-layout-work.md](../docs/api-v3-risk-dialog-layout-work.md) | 39 | 0 |
| A | [docs/api-v3-risk-legacy-bridge-work.md](../docs/api-v3-risk-legacy-bridge-work.md) | 37 | 0 |
| A | [docs/api-v3-risk-status-work.md](../docs/api-v3-risk-status-work.md) | 31 | 0 |
| A | [docs/api-v3-route-access-audit.json](../docs/api-v3-route-access-audit.json) | 4264 | 0 |
| A | [docs/api-v3-route-builder-probe.json](../docs/api-v3-route-builder-probe.json) | 397 | 0 |
| A | [docs/api-v3-route-parity-audit.json](../docs/api-v3-route-parity-audit.json) | 5511 | 0 |
| A | [docs/api-v3-security-work.md](../docs/api-v3-security-work.md) | 99 | 0 |
| A | [docs/api-v3-sensitive-account-plan.md](../docs/api-v3-sensitive-account-plan.md) | 94 | 0 |
| A | [docs/api-v3-settings-regression.json](../docs/api-v3-settings-regression.json) | 431 | 0 |
| A | [docs/api-v3-settings-work.md](../docs/api-v3-settings-work.md) | 84 | 0 |
| A | [docs/api-v3-shared-json-live.json](../docs/api-v3-shared-json-live.json) | 126 | 0 |
| A | [docs/api-v3-shared-json-regression.json](../docs/api-v3-shared-json-regression.json) | 388 | 0 |
| A | [docs/api-v3-shell-avatar-regression.json](../docs/api-v3-shell-avatar-regression.json) | 58 | 0 |
| A | [docs/api-v3-tag-suggestions-work.md](../docs/api-v3-tag-suggestions-work.md) | 54 | 0 |
| A | [docs/api-v3-user-name-mysql-work.md](../docs/api-v3-user-name-mysql-work.md) | 51 | 0 |
| A | [docs/api-v3-user-name-work.md](../docs/api-v3-user-name-work.md) | 54 | 0 |
| A | [docs/api-v3-user-suggestions-work.md](../docs/api-v3-user-suggestions-work.md) | 53 | 0 |
| A | [docs/local-idea-environment.md](../docs/local-idea-environment.md) | 80 | 0 |

### 构建与资源配置（3个）

| 状态 | 文件 | +行 | -行 |
| --- | --- | ---: | ---: |
| M | [pom.xml](../pom.xml) | 2 | 2 |
| M | [src/main/resources/log4j2.xml](../src/main/resources/log4j2.xml) | 4 | 1 |
| M | [src/main/resources/repository.json](../src/main/resources/repository.json) | 4714 | 2061 |

### 数据库迁移（20个）

| 状态 | 文件 | +行 | -行 |
| --- | --- | ---: | ---: |
| A | [sql/20261016_api_v3.sql](../sql/20261016_api_v3.sql) | 148 | 0 |
| A | [sql/20261019_account_registration.sql](../sql/20261019_account_registration.sql) | 9 | 0 |
| A | [sql/20261019_api_v3_oauth.sql](../sql/20261019_api_v3_oauth.sql) | 44 | 0 |
| A | [sql/20261019_api_v3_private_messages.sql](../sql/20261019_api_v3_private_messages.sql) | 27 | 0 |
| A | [sql/20261020_api_v3_private_message_writes.sql](../sql/20261020_api_v3_private_message_writes.sql) | 18 | 0 |
| A | [sql/20261020_api_v3_private_realtime.sql](../sql/20261020_api_v3_private_realtime.sql) | 8 | 0 |
| A | [sql/20261020_api_v3_private_withdrawal.sql](../sql/20261020_api_v3_private_withdrawal.sql) | 16 | 0 |
| A | [sql/20261022_api_v3_membership.sql](../sql/20261022_api_v3_membership.sql) | 5 | 0 |
| A | [sql/20261023_account_credential_state.sql](../sql/20261023_account_credential_state.sql) | 10 | 0 |
| A | [sql/20261023_api_v3_contact_verification.sql](../sql/20261023_api_v3_contact_verification.sql) | 15 | 0 |
| A | [sql/20261023_api_v3_fish_game_submission.sql](../sql/20261023_api_v3_fish_game_submission.sql) | 10 | 0 |
| A | [sql/20261023_api_v3_mfa.sql](../sql/20261023_api_v3_mfa.sql) | 14 | 0 |
| A | [sql/20261024_api_v3_chat_red_packet.sql](../sql/20261024_api_v3_chat_red_packet.sql) | 29 | 0 |
| A | [sql/20261024_api_v3_chat_red_packet_risk.sql](../sql/20261024_api_v3_chat_red_packet_risk.sql) | 10 | 0 |
| A | [sql/20261024_api_v3_identity_application.sql](../sql/20261024_api_v3_identity_application.sql) | 24 | 0 |
| A | [sql/20261024_api_v3_post_export.sql](../sql/20261024_api_v3_post_export.sql) | 14 | 0 |
| A | [sql/20261025_api_v3_app_login.sql](../sql/20261025_api_v3_app_login.sql) | 25 | 0 |
| A | [sql/20261025_api_v3_game_save.sql](../sql/20261025_api_v3_game_save.sql) | 20 | 0 |
| A | [sql/20261029_api_v3_point_source_scene.sql](../sql/20261029_api_v3_point_source_scene.sql) | 9 | 0 |
| A | [sql/20261030_api_v3_article_reads.sql](../sql/20261030_api_v3_article_reads.sql) | 33 | 0 |

### 后端生产Java（533个）

| 状态 | 文件 | +行 | -行 |
| --- | --- | ---: | ---: |
| A | [src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java](../src/main/java/org/b3log/latke/http/ApiV3GameSaveRawBody.java) | 15 | 0 |
| M | [src/main/java/org/b3log/symphony/cache/MilestoneCache.java](../src/main/java/org/b3log/symphony/cache/MilestoneCache.java) | 0 | 1 |
| M | [src/main/java/org/b3log/symphony/event/ChangeRoleHandler.java](../src/main/java/org/b3log/symphony/event/ChangeRoleHandler.java) | 14 | 5 |
| M | [src/main/java/org/b3log/symphony/event/ChangeRoleUpdateHandler.java](../src/main/java/org/b3log/symphony/event/ChangeRoleUpdateHandler.java) | 14 | 4 |
| M | [src/main/java/org/b3log/symphony/event/CommentNotifier.java](../src/main/java/org/b3log/symphony/event/CommentNotifier.java) | 36 | 30 |
| M | [src/main/java/org/b3log/symphony/event/CommentUpdateNotifier.java](../src/main/java/org/b3log/symphony/event/CommentUpdateNotifier.java) | 6 | 14 |
| A | [src/main/java/org/b3log/symphony/model/AccountContactCode.java](../src/main/java/org/b3log/symphony/model/AccountContactCode.java) | 7 | 0 |
| A | [src/main/java/org/b3log/symphony/model/AccountCredentialState.java](../src/main/java/org/b3log/symphony/model/AccountCredentialState.java) | 8 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3AppLoginRequest.java](../src/main/java/org/b3log/symphony/model/ApiV3AppLoginRequest.java) | 8 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3ArticleRevision.java](../src/main/java/org/b3log/symphony/model/ApiV3ArticleRevision.java) | 7 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3AuthorizationCode.java](../src/main/java/org/b3log/symphony/model/ApiV3AuthorizationCode.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3AuthorizationRequest.java](../src/main/java/org/b3log/symphony/model/ApiV3AuthorizationRequest.java) | 10 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3ChatEvent.java](../src/main/java/org/b3log/symphony/model/ApiV3ChatEvent.java) | 18 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3Client.java](../src/main/java/org/b3log/symphony/model/ApiV3Client.java) | 15 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3ClientRedirect.java](../src/main/java/org/b3log/symphony/model/ApiV3ClientRedirect.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3GameSave.java](../src/main/java/org/b3log/symphony/model/ApiV3GameSave.java) | 7 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3NotificationRevision.java](../src/main/java/org/b3log/symphony/model/ApiV3NotificationRevision.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3PrivateRevision.java](../src/main/java/org/b3log/symphony/model/ApiV3PrivateRevision.java) | 7 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3Request.java](../src/main/java/org/b3log/symphony/model/ApiV3Request.java) | 12 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3SocketTicket.java](../src/main/java/org/b3log/symphony/model/ApiV3SocketTicket.java) | 18 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ApiV3Token.java](../src/main/java/org/b3log/symphony/model/ApiV3Token.java) | 28 | 0 |
| A | [src/main/java/org/b3log/symphony/model/ChatRedPacket.java](../src/main/java/org/b3log/symphony/model/ChatRedPacket.java) | 9 | 0 |
| M | [src/main/java/org/b3log/symphony/model/FishGame.java](../src/main/java/org/b3log/symphony/model/FishGame.java) | 2 | 0 |
| M | [src/main/java/org/b3log/symphony/model/OpenIdRefreshToken.java](../src/main/java/org/b3log/symphony/model/OpenIdRefreshToken.java) | 1 | 0 |
| M | [src/main/java/org/b3log/symphony/model/Role.java](../src/main/java/org/b3log/symphony/model/Role.java) | 3 | 0 |
| M | [src/main/java/org/b3log/symphony/model/Tag.java](../src/main/java/org/b3log/symphony/model/Tag.java) | 13 | 14 |
| M | [src/main/java/org/b3log/symphony/model/Verifycode.java](../src/main/java/org/b3log/symphony/model/Verifycode.java) | 3 | 0 |
| M | [src/main/java/org/b3log/symphony/processor/ApiProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiProcessor.java) | 40 | 10 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3AccountProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3AccountProcessor.java) | 100 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ActivityProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ActivityProcessor.java) | 36 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3AnonymousContentProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3AnonymousContentProcessor.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3AppLoginProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3AppLoginProcessor.java) | 59 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ArticleHeatProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ArticleHeatProcessor.java) | 25 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ArticleReadProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ArticleReadProcessor.java) | 53 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ArticleRewardProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ArticleRewardProcessor.java) | 41 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ArticleStickProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ArticleStickProcessor.java) | 27 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ArticleWriteProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ArticleWriteProcessor.java) | 53 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3BagProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3BagProcessor.java) | 50 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3BreezemoonProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3BreezemoonProcessor.java) | 68 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ChatMuteProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ChatMuteProcessor.java) | 49 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ChatProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ChatProcessor.java) | 77 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ChatRedPacketProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ChatRedPacketProcessor.java) | 23 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ClientDisableProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ClientDisableProcessor.java) | 55 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ClientManagementProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ClientManagementProcessor.java) | 57 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ClientUpdateProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ClientUpdateProcessor.java) | 50 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ColumnProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ColumnProcessor.java) | 56 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3CommentProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3CommentProcessor.java) | 90 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3CommentRewardProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3CommentRewardProcessor.java) | 28 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3CommunityProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3CommunityProcessor.java) | 57 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ContactSettingsProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ContactSettingsProcessor.java) | 38 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ContentProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ContentProcessor.java) | 126 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3DiscoveryExtrasProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3DiscoveryExtrasProcessor.java) | 33 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3DiscoveryProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3DiscoveryProcessor.java) | 199 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3DonationProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3DonationProcessor.java) | 27 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3DraftProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3DraftProcessor.java) | 50 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3EmojiGroupProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3EmojiGroupProcessor.java) | 45 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3EmojiItemProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3EmojiItemProcessor.java) | 33 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3EmojiTransferProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3EmojiTransferProcessor.java) | 31 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3EmotionProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3EmotionProcessor.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3EmotionSettingsProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3EmotionSettingsProcessor.java) | 35 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3FishGameProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3FishGameProcessor.java) | 49 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3FishGameSubmissionProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3FishGameSubmissionProcessor.java) | 35 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3FollowProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3FollowProcessor.java) | 85 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3GameSaveProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3GameSaveProcessor.java) | 46 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3IdentityApplicationProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3IdentityApplicationProcessor.java) | 66 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3InvitationProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3InvitationProcessor.java) | 38 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3InvitationPurchaseProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3InvitationPurchaseProcessor.java) | 37 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3MarkdownProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3MarkdownProcessor.java) | 76 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3MedalDisplayProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3MedalDisplayProcessor.java) | 30 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3MedalImageProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3MedalImageProcessor.java) | 29 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3MedalOrderProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3MedalOrderProcessor.java) | 30 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3MedalProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3MedalProcessor.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3MembershipOpeningProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3MembershipOpeningProcessor.java) | 37 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3MembershipProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3MembershipProcessor.java) | 33 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3MembershipSettingsProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3MembershipSettingsProcessor.java) | 34 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3MembershipStyleProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3MembershipStyleProcessor.java) | 23 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3MfaProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3MfaProcessor.java) | 26 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3MilestoneProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3MilestoneProcessor.java) | 44 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3NotificationProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3NotificationProcessor.java) | 85 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3OAuthProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3OAuthProcessor.java) | 213 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ParagraphProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ParagraphProcessor.java) | 50 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3PersonalProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3PersonalProcessor.java) | 59 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3PointTransferProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3PointTransferProcessor.java) | 36 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3PostExportProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3PostExportProcessor.java) | 35 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3PrivateMessageProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3PrivateMessageProcessor.java) | 58 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3Processor.java](../src/main/java/org/b3log/symphony/processor/ApiV3Processor.java) | 345 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ProfessionDetailProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ProfessionDetailProcessor.java) | 31 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ProfessionGuideProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ProfessionGuideProcessor.java) | 26 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ProfessionPrimaryProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ProfessionPrimaryProcessor.java) | 34 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ProfessionProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ProfessionProcessor.java) | 33 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ProfessionPublicProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ProfessionPublicProcessor.java) | 31 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ProfessionRankingProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ProfessionRankingProcessor.java) | 27 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ProfessionSettingsProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ProfessionSettingsProcessor.java) | 47 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ProfileSettingsProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ProfileSettingsProcessor.java) | 50 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3RankingProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3RankingProcessor.java) | 30 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ReactionProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ReactionProcessor.java) | 58 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3RealtimeProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3RealtimeProcessor.java) | 97 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3RedPacketProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3RedPacketProcessor.java) | 24 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3RepeaterLikeProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3RepeaterLikeProcessor.java) | 33 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3RepeaterProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3RepeaterProcessor.java) | 28 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3RepeaterPublicationProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3RepeaterPublicationProcessor.java) | 29 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3ReportProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3ReportProcessor.java) | 40 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3RevisionProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3RevisionProcessor.java) | 27 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3SecurityProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3SecurityProcessor.java) | 26 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3SettingsProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3SettingsProcessor.java) | 75 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3TagSuggestionProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3TagSuggestionProcessor.java) | 43 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3UploadProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3UploadProcessor.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3UserNameProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3UserNameProcessor.java) | 22 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3UserSuggestionProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3UserSuggestionProcessor.java) | 43 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/ApiV3VoteProcessor.java](../src/main/java/org/b3log/symphony/processor/ApiV3VoteProcessor.java) | 42 | 0 |
| M | [src/main/java/org/b3log/symphony/processor/ArticleProcessor.java](../src/main/java/org/b3log/symphony/processor/ArticleProcessor.java) | 44 | 112 |
| M | [src/main/java/org/b3log/symphony/processor/BeforeRequestHandler.java](../src/main/java/org/b3log/symphony/processor/BeforeRequestHandler.java) | 7 | 0 |
| M | [src/main/java/org/b3log/symphony/processor/BreezemoonProcessor.java](../src/main/java/org/b3log/symphony/processor/BreezemoonProcessor.java) | 3 | 4 |
| M | [src/main/java/org/b3log/symphony/processor/ChatProcessor.java](../src/main/java/org/b3log/symphony/processor/ChatProcessor.java) | 26 | 52 |
| M | [src/main/java/org/b3log/symphony/processor/ChatroomProcessor.java](../src/main/java/org/b3log/symphony/processor/ChatroomProcessor.java) | 148 | 175 |
| M | [src/main/java/org/b3log/symphony/processor/CloudProcessor.java](../src/main/java/org/b3log/symphony/processor/CloudProcessor.java) | 44 | 0 |
| M | [src/main/java/org/b3log/symphony/processor/CommentProcessor.java](../src/main/java/org/b3log/symphony/processor/CommentProcessor.java) | 35 | 47 |
| M | [src/main/java/org/b3log/symphony/processor/LoginProcessor.java](../src/main/java/org/b3log/symphony/processor/LoginProcessor.java) | 141 | 284 |
| M | [src/main/java/org/b3log/symphony/processor/MilestoneProcessor.java](../src/main/java/org/b3log/symphony/processor/MilestoneProcessor.java) | 2 | 2 |
| M | [src/main/java/org/b3log/symphony/processor/OpenIdProcessor.java](../src/main/java/org/b3log/symphony/processor/OpenIdProcessor.java) | 18 | 5 |
| M | [src/main/java/org/b3log/symphony/processor/RhypicProcessor.java](../src/main/java/org/b3log/symphony/processor/RhypicProcessor.java) | 4 | 36 |
| M | [src/main/java/org/b3log/symphony/processor/Router.java](../src/main/java/org/b3log/symphony/processor/Router.java) | 2 | 0 |
| M | [src/main/java/org/b3log/symphony/processor/SettingsProcessor.java](../src/main/java/org/b3log/symphony/processor/SettingsProcessor.java) | 110 | 413 |
| M | [src/main/java/org/b3log/symphony/processor/TopProcessor.java](../src/main/java/org/b3log/symphony/processor/TopProcessor.java) | 11 | 128 |
| M | [src/main/java/org/b3log/symphony/processor/UserProcessor.java](../src/main/java/org/b3log/symphony/processor/UserProcessor.java) | 3 | 6 |
| M | [src/main/java/org/b3log/symphony/processor/VoteProcessor.java](../src/main/java/org/b3log/symphony/processor/VoteProcessor.java) | 42 | 167 |
| M | [src/main/java/org/b3log/symphony/processor/bot/ChatRoomBot.java](../src/main/java/org/b3log/symphony/processor/bot/ChatRoomBot.java) | 17 | 37 |
| A | [src/main/java/org/b3log/symphony/processor/channel/ApiV3RealtimeChannel.java](../src/main/java/org/b3log/symphony/processor/channel/ApiV3RealtimeChannel.java) | 26 | 0 |
| M | [src/main/java/org/b3log/symphony/processor/channel/ArticleChannel.java](../src/main/java/org/b3log/symphony/processor/channel/ArticleChannel.java) | 24 | 55 |
| M | [src/main/java/org/b3log/symphony/processor/channel/ChatChannel.java](../src/main/java/org/b3log/symphony/processor/channel/ChatChannel.java) | 12 | 13 |
| M | [src/main/java/org/b3log/symphony/processor/channel/ChatroomChannel.java](../src/main/java/org/b3log/symphony/processor/channel/ChatroomChannel.java) | 4 | 6 |
| M | [src/main/java/org/b3log/symphony/processor/channel/GobangChannel.java](../src/main/java/org/b3log/symphony/processor/channel/GobangChannel.java) | 3 | 1 |
| M | [src/main/java/org/b3log/symphony/processor/channel/ShopChannel.java](../src/main/java/org/b3log/symphony/processor/channel/ShopChannel.java) | 7 | 17 |
| M | [src/main/java/org/b3log/symphony/processor/channel/UserChannel.java](../src/main/java/org/b3log/symphony/processor/channel/UserChannel.java) | 8 | 27 |
| M | [src/main/java/org/b3log/symphony/processor/middleware/AnonymousViewCheckMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/AnonymousViewCheckMidware.java) | 10 | 4 |
| A | [src/main/java/org/b3log/symphony/processor/middleware/ApiV3AuthMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/ApiV3AuthMidware.java) | 96 | 0 |
| A | [src/main/java/org/b3log/symphony/processor/middleware/ApiV3WriteMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/ApiV3WriteMidware.java) | 41 | 0 |
| M | [src/main/java/org/b3log/symphony/processor/middleware/FishGameWriteSecurityMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/FishGameWriteSecurityMidware.java) | 6 | 9 |
| M | [src/main/java/org/b3log/symphony/processor/middleware/validate/ArticlePostValidationMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/validate/ArticlePostValidationMidware.java) | 18 | 290 |
| M | [src/main/java/org/b3log/symphony/processor/middleware/validate/ChatMsgAddValidationMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/validate/ChatMsgAddValidationMidware.java) | 11 | 33 |
| M | [src/main/java/org/b3log/symphony/processor/middleware/validate/CommentAddValidationMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/validate/CommentAddValidationMidware.java) | 42 | 89 |
| M | [src/main/java/org/b3log/symphony/processor/middleware/validate/CommentUpdateValidationMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/validate/CommentUpdateValidationMidware.java) | 16 | 95 |
| M | [src/main/java/org/b3log/symphony/processor/middleware/validate/UpdateProfilesValidationMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/validate/UpdateProfilesValidationMidware.java) | 1 | 1 |
| M | [src/main/java/org/b3log/symphony/processor/middleware/validate/UserRegisterValidationMidware.java](../src/main/java/org/b3log/symphony/processor/middleware/validate/UserRegisterValidationMidware.java) | 5 | 30 |
| A | [src/main/java/org/b3log/symphony/repository/AccountContactCodeRepository.java](../src/main/java/org/b3log/symphony/repository/AccountContactCodeRepository.java) | 10 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/AccountCredentialStateRepository.java](../src/main/java/org/b3log/symphony/repository/AccountCredentialStateRepository.java) | 39 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3AppLoginRequestRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3AppLoginRequestRepository.java) | 36 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3ArticleRevisionRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3ArticleRevisionRepository.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3AuthorizationCodeRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3AuthorizationCodeRepository.java) | 24 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3AuthorizationRequestRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3AuthorizationRequestRepository.java) | 30 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3ChatEventRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3ChatEventRepository.java) | 54 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3ChatStateRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3ChatStateRepository.java) | 28 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3ClientRedirectRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3ClientRedirectRepository.java) | 30 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3ClientRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3ClientRepository.java) | 35 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3GameSaveRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3GameSaveRepository.java) | 20 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3IdentityApplicationRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3IdentityApplicationRepository.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3IdentityMaterialRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3IdentityMaterialRepository.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3IdentityReviewerRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3IdentityReviewerRepository.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3MfaPreparationRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3MfaPreparationRepository.java) | 10 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3NotificationRevisionRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3NotificationRevisionRepository.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3PostExportChunkRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3PostExportChunkRepository.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3PostExportRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3PostExportRepository.java) | 10 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3PrivateRevisionRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3PrivateRevisionRepository.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3RequestRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3RequestRepository.java) | 23 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3RevisionRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3RevisionRepository.java) | 38 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3SocketTicketRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3SocketTicketRepository.java) | 24 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ApiV3TokenRepository.java](../src/main/java/org/b3log/symphony/repository/ApiV3TokenRepository.java) | 66 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/ArticleDraftRepository.java](../src/main/java/org/b3log/symphony/repository/ArticleDraftRepository.java) | 6 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/ArticleRepository.java](../src/main/java/org/b3log/symphony/repository/ArticleRepository.java) | 95 | 4 |
| M | [src/main/java/org/b3log/symphony/repository/BreezemoonRepository.java](../src/main/java/org/b3log/symphony/repository/BreezemoonRepository.java) | 43 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/ChatInfoRepository.java](../src/main/java/org/b3log/symphony/repository/ChatInfoRepository.java) | 36 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ChatRedPacketRepository.java](../src/main/java/org/b3log/symphony/repository/ChatRedPacketRepository.java) | 30 | 0 |
| A | [src/main/java/org/b3log/symphony/repository/ChatRedPacketSlotRepository.java](../src/main/java/org/b3log/symphony/repository/ChatRedPacketSlotRepository.java) | 17 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/ChatRoomRepository.java](../src/main/java/org/b3log/symphony/repository/ChatRoomRepository.java) | 25 | 1 |
| M | [src/main/java/org/b3log/symphony/repository/ChatUnreadRepository.java](../src/main/java/org/b3log/symphony/repository/ChatUnreadRepository.java) | 43 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/CloudRepository.java](../src/main/java/org/b3log/symphony/repository/CloudRepository.java) | 6 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/CommentRepository.java](../src/main/java/org/b3log/symphony/repository/CommentRepository.java) | 82 | 4 |
| M | [src/main/java/org/b3log/symphony/repository/FishGameRepository.java](../src/main/java/org/b3log/symphony/repository/FishGameRepository.java) | 8 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/FishGameVoteRepository.java](../src/main/java/org/b3log/symphony/repository/FishGameVoteRepository.java) | 10 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/FollowRepository.java](../src/main/java/org/b3log/symphony/repository/FollowRepository.java) | 12 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/LongArticleChapterRepository.java](../src/main/java/org/b3log/symphony/repository/LongArticleChapterRepository.java) | 18 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/LongArticleColumnRepository.java](../src/main/java/org/b3log/symphony/repository/LongArticleColumnRepository.java) | 13 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/LongArticleReadSettlementRepository.java](../src/main/java/org/b3log/symphony/repository/LongArticleReadSettlementRepository.java) | 1 | 1 |
| M | [src/main/java/org/b3log/symphony/repository/LongArticleReadUserRepository.java](../src/main/java/org/b3log/symphony/repository/LongArticleReadUserRepository.java) | 5 | 5 |
| M | [src/main/java/org/b3log/symphony/repository/LongArticleReadWindowRepository.java](../src/main/java/org/b3log/symphony/repository/LongArticleReadWindowRepository.java) | 12 | 2 |
| M | [src/main/java/org/b3log/symphony/repository/MilestoneRepository.java](../src/main/java/org/b3log/symphony/repository/MilestoneRepository.java) | 17 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/NotificationRepository.java](../src/main/java/org/b3log/symphony/repository/NotificationRepository.java) | 125 | 9 |
| M | [src/main/java/org/b3log/symphony/repository/OpenIdRefreshTokenRepository.java](../src/main/java/org/b3log/symphony/repository/OpenIdRefreshTokenRepository.java) | 6 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/OptionRepository.java](../src/main/java/org/b3log/symphony/repository/OptionRepository.java) | 14 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/ReactionRepository.java](../src/main/java/org/b3log/symphony/repository/ReactionRepository.java) | 23 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/RewardRepository.java](../src/main/java/org/b3log/symphony/repository/RewardRepository.java) | 21 | 4 |
| M | [src/main/java/org/b3log/symphony/repository/TagRepository.java](../src/main/java/org/b3log/symphony/repository/TagRepository.java) | 26 | 2 |
| M | [src/main/java/org/b3log/symphony/repository/UserRepository.java](../src/main/java/org/b3log/symphony/repository/UserRepository.java) | 52 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/VerifycodeRepository.java](../src/main/java/org/b3log/symphony/repository/VerifycodeRepository.java) | 40 | 0 |
| M | [src/main/java/org/b3log/symphony/repository/VoteRepository.java](../src/main/java/org/b3log/symphony/repository/VoteRepository.java) | 11 | 4 |
| A | [src/main/java/org/b3log/symphony/service/AccountCaptchaService.java](../src/main/java/org/b3log/symphony/service/AccountCaptchaService.java) | 17 | 0 |
| A | [src/main/java/org/b3log/symphony/service/AccountCredentialMgmtService.java](../src/main/java/org/b3log/symphony/service/AccountCredentialMgmtService.java) | 31 | 0 |
| A | [src/main/java/org/b3log/symphony/service/AccountPasswordResetRequestService.java](../src/main/java/org/b3log/symphony/service/AccountPasswordResetRequestService.java) | 65 | 0 |
| A | [src/main/java/org/b3log/symphony/service/AccountPasswordResetService.java](../src/main/java/org/b3log/symphony/service/AccountPasswordResetService.java) | 68 | 0 |
| A | [src/main/java/org/b3log/symphony/service/AccountPendingRegistrationService.java](../src/main/java/org/b3log/symphony/service/AccountPendingRegistrationService.java) | 95 | 0 |
| A | [src/main/java/org/b3log/symphony/service/AccountRegistrationEffectsService.java](../src/main/java/org/b3log/symphony/service/AccountRegistrationEffectsService.java) | 66 | 0 |
| A | [src/main/java/org/b3log/symphony/service/AccountRegistrationRequestService.java](../src/main/java/org/b3log/symphony/service/AccountRegistrationRequestService.java) | 195 | 0 |
| A | [src/main/java/org/b3log/symphony/service/AccountRegistrationService.java](../src/main/java/org/b3log/symphony/service/AccountRegistrationService.java) | 156 | 0 |
| A | [src/main/java/org/b3log/symphony/service/AccountVerificationService.java](../src/main/java/org/b3log/symphony/service/AccountVerificationService.java) | 58 | 0 |
| M | [src/main/java/org/b3log/symphony/service/ActivityMgmtService.java](../src/main/java/org/b3log/symphony/service/ActivityMgmtService.java) | 10 | 207 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ActivityMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3ActivityMgmtService.java) | 37 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3AnonymousContentQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3AnonymousContentQueryService.java) | 152 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3AppLoginService.java](../src/main/java/org/b3log/symphony/service/ApiV3AppLoginService.java) | 177 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ArticleHeatQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ArticleHeatQueryService.java) | 28 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ArticleMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3ArticleMgmtService.java) | 175 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ArticleRewardService.java](../src/main/java/org/b3log/symphony/service/ApiV3ArticleRewardService.java) | 89 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ArticleStickService.java](../src/main/java/org/b3log/symphony/service/ApiV3ArticleStickService.java) | 118 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ArticleVoteService.java](../src/main/java/org/b3log/symphony/service/ApiV3ArticleVoteService.java) | 67 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3AuthorizationCodeService.java](../src/main/java/org/b3log/symphony/service/ApiV3AuthorizationCodeService.java) | 114 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3AuthorizationRequestService.java](../src/main/java/org/b3log/symphony/service/ApiV3AuthorizationRequestService.java) | 81 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3AvatarQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3AvatarQueryService.java) | 29 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3BagMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3BagMgmtService.java) | 63 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3BreezemoonMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3BreezemoonMgmtService.java) | 82 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ChatBarrageService.java](../src/main/java/org/b3log/symphony/service/ApiV3ChatBarrageService.java) | 74 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ChatMuteQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ChatMuteQueryService.java) | 57 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ChatOnlineQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ChatOnlineQueryService.java) | 69 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ChatPublicationService.java](../src/main/java/org/b3log/symphony/service/ApiV3ChatPublicationService.java) | 154 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ChatQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ChatQueryService.java) | 68 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ChatRedPacketService.java](../src/main/java/org/b3log/symphony/service/ApiV3ChatRedPacketService.java) | 394 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ChatTopicService.java](../src/main/java/org/b3log/symphony/service/ApiV3ChatTopicService.java) | 58 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ClientDisableService.java](../src/main/java/org/b3log/symphony/service/ApiV3ClientDisableService.java) | 87 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ClientManagementService.java](../src/main/java/org/b3log/symphony/service/ApiV3ClientManagementService.java) | 121 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ClientRegistrationService.java](../src/main/java/org/b3log/symphony/service/ApiV3ClientRegistrationService.java) | 85 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ClientUpdateService.java](../src/main/java/org/b3log/symphony/service/ApiV3ClientUpdateService.java) | 135 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ColumnMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3ColumnMgmtService.java) | 60 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3CommentMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3CommentMgmtService.java) | 269 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3CommentRewardQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3CommentRewardQueryService.java) | 57 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3CommentVoteService.java](../src/main/java/org/b3log/symphony/service/ApiV3CommentVoteService.java) | 75 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3CommunityQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3CommunityQueryService.java) | 123 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ContactSettingsService.java](../src/main/java/org/b3log/symphony/service/ApiV3ContactSettingsService.java) | 132 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ContentDtoService.java](../src/main/java/org/b3log/symphony/service/ApiV3ContentDtoService.java) | 83 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ContentQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ContentQueryService.java) | 196 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3DiscoveryExtrasQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3DiscoveryExtrasQueryService.java) | 106 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3DiscoveryQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3DiscoveryQueryService.java) | 324 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3DraftMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3DraftMgmtService.java) | 44 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3EmojiGroupService.java](../src/main/java/org/b3log/symphony/service/ApiV3EmojiGroupService.java) | 130 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3EmojiItemService.java](../src/main/java/org/b3log/symphony/service/ApiV3EmojiItemService.java) | 164 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3EmojiTransferService.java](../src/main/java/org/b3log/symphony/service/ApiV3EmojiTransferService.java) | 163 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3EmotionQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3EmotionQueryService.java) | 103 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3EmotionSettingsService.java](../src/main/java/org/b3log/symphony/service/ApiV3EmotionSettingsService.java) | 71 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3FishGameMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3FishGameMgmtService.java) | 41 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3FishGameSubmissionService.java](../src/main/java/org/b3log/symphony/service/ApiV3FishGameSubmissionService.java) | 107 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3FollowMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3FollowMgmtService.java) | 81 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3FollowQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3FollowQueryService.java) | 84 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3GameSaveService.java](../src/main/java/org/b3log/symphony/service/ApiV3GameSaveService.java) | 154 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3IdempotencyMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3IdempotencyMgmtService.java) | 94 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3IdentityApplicationService.java](../src/main/java/org/b3log/symphony/service/ApiV3IdentityApplicationService.java) | 162 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3InvitationPurchaseService.java](../src/main/java/org/b3log/symphony/service/ApiV3InvitationPurchaseService.java) | 153 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3InvitationQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3InvitationQueryService.java) | 39 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MedalDisplayService.java](../src/main/java/org/b3log/symphony/service/ApiV3MedalDisplayService.java) | 62 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MedalImageQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3MedalImageQueryService.java) | 39 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MedalImageService.java](../src/main/java/org/b3log/symphony/service/ApiV3MedalImageService.java) | 67 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MedalOrderService.java](../src/main/java/org/b3log/symphony/service/ApiV3MedalOrderService.java) | 97 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MedalQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3MedalQueryService.java) | 63 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MembershipOpeningService.java](../src/main/java/org/b3log/symphony/service/ApiV3MembershipOpeningService.java) | 226 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MembershipQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3MembershipQueryService.java) | 49 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MembershipSettingsService.java](../src/main/java/org/b3log/symphony/service/ApiV3MembershipSettingsService.java) | 97 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MembershipStyleQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3MembershipStyleQueryService.java) | 75 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MfaService.java](../src/main/java/org/b3log/symphony/service/ApiV3MfaService.java) | 118 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MilestoneMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3MilestoneMgmtService.java) | 28 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3MilestoneQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3MilestoneQueryService.java) | 25 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3NotificationMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3NotificationMgmtService.java) | 29 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3NotificationQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3NotificationQueryService.java) | 233 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3OAuthCorsService.java](../src/main/java/org/b3log/symphony/service/ApiV3OAuthCorsService.java) | 30 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3OAuthCredentialQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3OAuthCredentialQueryService.java) | 57 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3OAuthMaintenanceService.java](../src/main/java/org/b3log/symphony/service/ApiV3OAuthMaintenanceService.java) | 38 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ParagraphQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ParagraphQueryService.java) | 124 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3PersonalQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3PersonalQueryService.java) | 139 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3PersonalityQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3PersonalityQueryService.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3PointTransferService.java](../src/main/java/org/b3log/symphony/service/ApiV3PointTransferService.java) | 225 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3PostExportService.java](../src/main/java/org/b3log/symphony/service/ApiV3PostExportService.java) | 173 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3PostExportSnapshotService.java](../src/main/java/org/b3log/symphony/service/ApiV3PostExportSnapshotService.java) | 85 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3PrivateMessagePublicationService.java](../src/main/java/org/b3log/symphony/service/ApiV3PrivateMessagePublicationService.java) | 30 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3PrivateMessageQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3PrivateMessageQueryService.java) | 123 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3PrivateMessageStateService.java](../src/main/java/org/b3log/symphony/service/ApiV3PrivateMessageStateService.java) | 39 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3PrivateMessageWithdrawalService.java](../src/main/java/org/b3log/symphony/service/ApiV3PrivateMessageWithdrawalService.java) | 29 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ProfessionDetailQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ProfessionDetailQueryService.java) | 129 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ProfessionGuideQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ProfessionGuideQueryService.java) | 111 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ProfessionPrimaryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ProfessionPrimaryService.java) | 84 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ProfessionPublicQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ProfessionPublicQueryService.java) | 270 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ProfessionQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ProfessionQueryService.java) | 138 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ProfessionRankingQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ProfessionRankingQueryService.java) | 157 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ProfessionSettingsService.java](../src/main/java/org/b3log/symphony/service/ApiV3ProfessionSettingsService.java) | 125 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ProfileTagsService.java](../src/main/java/org/b3log/symphony/service/ApiV3ProfileTagsService.java) | 166 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3PublicColumnQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3PublicColumnQueryService.java) | 178 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3QqSettingsService.java](../src/main/java/org/b3log/symphony/service/ApiV3QqSettingsService.java) | 90 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3RankingQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3RankingQueryService.java) | 91 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ReactionEventService.java](../src/main/java/org/b3log/symphony/service/ApiV3ReactionEventService.java) | 67 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ReactionService.java](../src/main/java/org/b3log/symphony/service/ApiV3ReactionService.java) | 31 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ReactionTargetService.java](../src/main/java/org/b3log/symphony/service/ApiV3ReactionTargetService.java) | 54 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3RealtimeService.java](../src/main/java/org/b3log/symphony/service/ApiV3RealtimeService.java) | 149 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3RealtimeTargetService.java](../src/main/java/org/b3log/symphony/service/ApiV3RealtimeTargetService.java) | 40 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3RegistrationPolicyService.java](../src/main/java/org/b3log/symphony/service/ApiV3RegistrationPolicyService.java) | 45 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3RepeaterLikeService.java](../src/main/java/org/b3log/symphony/service/ApiV3RepeaterLikeService.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3RepeaterPublicationService.java](../src/main/java/org/b3log/symphony/service/ApiV3RepeaterPublicationService.java) | 59 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3RepeaterQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3RepeaterQueryService.java) | 51 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ReportPublicationService.java](../src/main/java/org/b3log/symphony/service/ApiV3ReportPublicationService.java) | 133 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3ReportQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3ReportQueryService.java) | 43 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3RevisionQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3RevisionQueryService.java) | 131 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3SecurityProofService.java](../src/main/java/org/b3log/symphony/service/ApiV3SecurityProofService.java) | 77 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3SecuritySettingsService.java](../src/main/java/org/b3log/symphony/service/ApiV3SecuritySettingsService.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3SettingsMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3SettingsMgmtService.java) | 38 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3SocketTicketService.java](../src/main/java/org/b3log/symphony/service/ApiV3SocketTicketService.java) | 124 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3TagSuggestionQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3TagSuggestionQueryService.java) | 52 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java](../src/main/java/org/b3log/symphony/service/ApiV3TokenMgmtService.java) | 315 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3UploadTicketService.java](../src/main/java/org/b3log/symphony/service/ApiV3UploadTicketService.java) | 42 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3UserNameService.java](../src/main/java/org/b3log/symphony/service/ApiV3UserNameService.java) | 109 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ApiV3UserSuggestionQueryService.java](../src/main/java/org/b3log/symphony/service/ApiV3UserSuggestionQueryService.java) | 47 | 0 |
| M | [src/main/java/org/b3log/symphony/service/ArticleDraftMgmtService.java](../src/main/java/org/b3log/symphony/service/ArticleDraftMgmtService.java) | 32 | 17 |
| M | [src/main/java/org/b3log/symphony/service/ArticleMgmtService.java](../src/main/java/org/b3log/symphony/service/ArticleMgmtService.java) | 534 | 580 |
| M | [src/main/java/org/b3log/symphony/service/ArticleQueryService.java](../src/main/java/org/b3log/symphony/service/ArticleQueryService.java) | 29 | 5 |
| A | [src/main/java/org/b3log/symphony/service/ArticleRewardMgmtService.java](../src/main/java/org/b3log/symphony/service/ArticleRewardMgmtService.java) | 102 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ArticleSubmissionMgmtService.java](../src/main/java/org/b3log/symphony/service/ArticleSubmissionMgmtService.java) | 85 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ArticleValidationService.java](../src/main/java/org/b3log/symphony/service/ArticleValidationService.java) | 39 | 0 |
| A | [src/main/java/org/b3log/symphony/service/BagMgmtService.java](../src/main/java/org/b3log/symphony/service/BagMgmtService.java) | 81 | 0 |
| M | [src/main/java/org/b3log/symphony/service/BreezemoonMgmtService.java](../src/main/java/org/b3log/symphony/service/BreezemoonMgmtService.java) | 41 | 32 |
| M | [src/main/java/org/b3log/symphony/service/ChatListService.java](../src/main/java/org/b3log/symphony/service/ChatListService.java) | 11 | 44 |
| A | [src/main/java/org/b3log/symphony/service/ChatMessageMgmtService.java](../src/main/java/org/b3log/symphony/service/ChatMessageMgmtService.java) | 79 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ChatMessageNoticeService.java](../src/main/java/org/b3log/symphony/service/ChatMessageNoticeService.java) | 34 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ChatMessagePenaltyMgmtService.java](../src/main/java/org/b3log/symphony/service/ChatMessagePenaltyMgmtService.java) | 65 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ChatMessageRestrictionQueryService.java](../src/main/java/org/b3log/symphony/service/ChatMessageRestrictionQueryService.java) | 38 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ChatMessageValidationService.java](../src/main/java/org/b3log/symphony/service/ChatMessageValidationService.java) | 16 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ChatMessageWithdrawalMgmtService.java](../src/main/java/org/b3log/symphony/service/ChatMessageWithdrawalMgmtService.java) | 152 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ChatTopicDeliveryService.java](../src/main/java/org/b3log/symphony/service/ChatTopicDeliveryService.java) | 35 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ChatTopicMgmtService.java](../src/main/java/org/b3log/symphony/service/ChatTopicMgmtService.java) | 67 | 0 |
| A | [src/main/java/org/b3log/symphony/service/ChatTopicWriteService.java](../src/main/java/org/b3log/symphony/service/ChatTopicWriteService.java) | 48 | 0 |
| A | [src/main/java/org/b3log/symphony/service/CheckinMgmtService.java](../src/main/java/org/b3log/symphony/service/CheckinMgmtService.java) | 116 | 0 |
| M | [src/main/java/org/b3log/symphony/service/CloudService.java](../src/main/java/org/b3log/symphony/service/CloudService.java) | 14 | 72 |
| M | [src/main/java/org/b3log/symphony/service/ColumnCoverMgmtService.java](../src/main/java/org/b3log/symphony/service/ColumnCoverMgmtService.java) | 1 | 1 |
| A | [src/main/java/org/b3log/symphony/service/CommentAcceptMgmtService.java](../src/main/java/org/b3log/symphony/service/CommentAcceptMgmtService.java) | 113 | 0 |
| M | [src/main/java/org/b3log/symphony/service/CommentMgmtService.java](../src/main/java/org/b3log/symphony/service/CommentMgmtService.java) | 252 | 434 |
| A | [src/main/java/org/b3log/symphony/service/CommentNotificationMgmtService.java](../src/main/java/org/b3log/symphony/service/CommentNotificationMgmtService.java) | 71 | 0 |
| A | [src/main/java/org/b3log/symphony/service/CommentRemovalMgmtService.java](../src/main/java/org/b3log/symphony/service/CommentRemovalMgmtService.java) | 174 | 0 |
| A | [src/main/java/org/b3log/symphony/service/CommentThankMgmtService.java](../src/main/java/org/b3log/symphony/service/CommentThankMgmtService.java) | 114 | 0 |
| A | [src/main/java/org/b3log/symphony/service/CommentValidationService.java](../src/main/java/org/b3log/symphony/service/CommentValidationService.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/service/CommentVoteMgmtService.java](../src/main/java/org/b3log/symphony/service/CommentVoteMgmtService.java) | 109 | 0 |
| M | [src/main/java/org/b3log/symphony/service/FishGameMgmtService.java](../src/main/java/org/b3log/symphony/service/FishGameMgmtService.java) | 83 | 72 |
| M | [src/main/java/org/b3log/symphony/service/FishGameValidationService.java](../src/main/java/org/b3log/symphony/service/FishGameValidationService.java) | 4 | 0 |
| M | [src/main/java/org/b3log/symphony/service/FollowMgmtService.java](../src/main/java/org/b3log/symphony/service/FollowMgmtService.java) | 58 | 99 |
| M | [src/main/java/org/b3log/symphony/service/InvitecodeMgmtService.java](../src/main/java/org/b3log/symphony/service/InvitecodeMgmtService.java) | 4 | 29 |
| A | [src/main/java/org/b3log/symphony/service/LivenessRewardMgmtService.java](../src/main/java/org/b3log/symphony/service/LivenessRewardMgmtService.java) | 82 | 0 |
| M | [src/main/java/org/b3log/symphony/service/LogsService.java](../src/main/java/org/b3log/symphony/service/LogsService.java) | 3 | 5 |
| M | [src/main/java/org/b3log/symphony/service/LongArticleColumnMgmtService.java](../src/main/java/org/b3log/symphony/service/LongArticleColumnMgmtService.java) | 77 | 112 |
| M | [src/main/java/org/b3log/symphony/service/LongArticleColumnQueryService.java](../src/main/java/org/b3log/symphony/service/LongArticleColumnQueryService.java) | 33 | 7 |
| M | [src/main/java/org/b3log/symphony/service/LongArticleParagraphService.java](../src/main/java/org/b3log/symphony/service/LongArticleParagraphService.java) | 175 | 14 |
| M | [src/main/java/org/b3log/symphony/service/LongArticleReadCaptureService.java](../src/main/java/org/b3log/symphony/service/LongArticleReadCaptureService.java) | 71 | 73 |
| M | [src/main/java/org/b3log/symphony/service/LongArticleReadSettlementService.java](../src/main/java/org/b3log/symphony/service/LongArticleReadSettlementService.java) | 63 | 16 |
| M | [src/main/java/org/b3log/symphony/service/MembershipMgmtService.java](../src/main/java/org/b3log/symphony/service/MembershipMgmtService.java) | 18 | 0 |
| M | [src/main/java/org/b3log/symphony/service/MilestoneMgmtService.java](../src/main/java/org/b3log/symphony/service/MilestoneMgmtService.java) | 21 | 8 |
| M | [src/main/java/org/b3log/symphony/service/NotificationMgmtService.java](../src/main/java/org/b3log/symphony/service/NotificationMgmtService.java) | 119 | 13 |
| M | [src/main/java/org/b3log/symphony/service/NotificationQueryService.java](../src/main/java/org/b3log/symphony/service/NotificationQueryService.java) | 288 | 275 |
| A | [src/main/java/org/b3log/symphony/service/NotificationStateMgmtService.java](../src/main/java/org/b3log/symphony/service/NotificationStateMgmtService.java) | 78 | 0 |
| M | [src/main/java/org/b3log/symphony/service/OpenIdRefreshTokenMgmtService.java](../src/main/java/org/b3log/symphony/service/OpenIdRefreshTokenMgmtService.java) | 37 | 26 |
| M | [src/main/java/org/b3log/symphony/service/PointtransferMgmtService.java](../src/main/java/org/b3log/symphony/service/PointtransferMgmtService.java) | 10 | 46 |
| M | [src/main/java/org/b3log/symphony/service/PointtransferQueryService.java](../src/main/java/org/b3log/symphony/service/PointtransferQueryService.java) | 2 | 3 |
| A | [src/main/java/org/b3log/symphony/service/PrivateMessageListStateService.java](../src/main/java/org/b3log/symphony/service/PrivateMessageListStateService.java) | 55 | 0 |
| A | [src/main/java/org/b3log/symphony/service/PrivateMessagePublicationService.java](../src/main/java/org/b3log/symphony/service/PrivateMessagePublicationService.java) | 44 | 0 |
| A | [src/main/java/org/b3log/symphony/service/PrivateMessageReadStateService.java](../src/main/java/org/b3log/symphony/service/PrivateMessageReadStateService.java) | 36 | 0 |
| A | [src/main/java/org/b3log/symphony/service/PrivateMessageRevisionService.java](../src/main/java/org/b3log/symphony/service/PrivateMessageRevisionService.java) | 19 | 0 |
| A | [src/main/java/org/b3log/symphony/service/PrivateMessageWithdrawalService.java](../src/main/java/org/b3log/symphony/service/PrivateMessageWithdrawalService.java) | 47 | 0 |
| A | [src/main/java/org/b3log/symphony/service/RankingQueryService.java](../src/main/java/org/b3log/symphony/service/RankingQueryService.java) | 101 | 0 |
| M | [src/main/java/org/b3log/symphony/service/ReactionMgmtService.java](../src/main/java/org/b3log/symphony/service/ReactionMgmtService.java) | 52 | 33 |
| M | [src/main/java/org/b3log/symphony/service/ReactionQueryService.java](../src/main/java/org/b3log/symphony/service/ReactionQueryService.java) | 8 | 1 |
| A | [src/main/java/org/b3log/symphony/service/RepeaterLikeMutationService.java](../src/main/java/org/b3log/symphony/service/RepeaterLikeMutationService.java) | 58 | 0 |
| M | [src/main/java/org/b3log/symphony/service/RepeaterMgmtService.java](../src/main/java/org/b3log/symphony/service/RepeaterMgmtService.java) | 12 | 19 |
| M | [src/main/java/org/b3log/symphony/service/ShortLinkQueryService.java](../src/main/java/org/b3log/symphony/service/ShortLinkQueryService.java) | 35 | 16 |
| M | [src/main/java/org/b3log/symphony/service/UserMgmtService.java](../src/main/java/org/b3log/symphony/service/UserMgmtService.java) | 31 | 116 |
| A | [src/main/java/org/b3log/symphony/service/UserNameMgmtService.java](../src/main/java/org/b3log/symphony/service/UserNameMgmtService.java) | 101 | 0 |
| M | [src/main/java/org/b3log/symphony/service/UserQueryService.java](../src/main/java/org/b3log/symphony/service/UserQueryService.java) | 3 | 48 |
| A | [src/main/java/org/b3log/symphony/service/UserSettingsMgmtService.java](../src/main/java/org/b3log/symphony/service/UserSettingsMgmtService.java) | 68 | 0 |
| M | [src/main/java/org/b3log/symphony/service/VerifycodeMgmtService.java](../src/main/java/org/b3log/symphony/service/VerifycodeMgmtService.java) | 32 | 21 |
| M | [src/main/java/org/b3log/symphony/service/VoteMgmtService.java](../src/main/java/org/b3log/symphony/service/VoteMgmtService.java) | 94 | 323 |
| A | [src/main/java/org/b3log/symphony/util/AccountCaptchaProof.java](../src/main/java/org/b3log/symphony/util/AccountCaptchaProof.java) | 21 | 0 |
| A | [src/main/java/org/b3log/symphony/util/AccountCredentialVersions.java](../src/main/java/org/b3log/symphony/util/AccountCredentialVersions.java) | 46 | 0 |
| A | [src/main/java/org/b3log/symphony/util/AccountMailResponses.java](../src/main/java/org/b3log/symphony/util/AccountMailResponses.java) | 21 | 0 |
| A | [src/main/java/org/b3log/symphony/util/AccountPairLocks.java](../src/main/java/org/b3log/symphony/util/AccountPairLocks.java) | 53 | 0 |
| A | [src/main/java/org/b3log/symphony/util/AccountSmsQuota.java](../src/main/java/org/b3log/symphony/util/AccountSmsQuota.java) | 16 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3AnonymousContentDto.java](../src/main/java/org/b3log/symphony/util/ApiV3AnonymousContentDto.java) | 40 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3AppLoginProtocol.java](../src/main/java/org/b3log/symphony/util/ApiV3AppLoginProtocol.java) | 112 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ArticleCreation.java](../src/main/java/org/b3log/symphony/util/ApiV3ArticleCreation.java) | 27 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ArticleEditInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3ArticleEditInputs.java) | 127 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ArticleEditing.java](../src/main/java/org/b3log/symphony/util/ApiV3ArticleEditing.java) | 29 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ArticleEditorDto.java](../src/main/java/org/b3log/symphony/util/ApiV3ArticleEditorDto.java) | 40 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ArticleInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3ArticleInputs.java) | 67 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ArticlePolicy.java](../src/main/java/org/b3log/symphony/util/ApiV3ArticlePolicy.java) | 57 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ArticleStickState.java](../src/main/java/org/b3log/symphony/util/ApiV3ArticleStickState.java) | 30 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3AvatarUrl.java](../src/main/java/org/b3log/symphony/util/ApiV3AvatarUrl.java) | 18 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3BarrageEvent.java](../src/main/java/org/b3log/symphony/util/ApiV3BarrageEvent.java) | 29 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3BreezemoonEdit.java](../src/main/java/org/b3log/symphony/util/ApiV3BreezemoonEdit.java) | 16 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3BreezemoonInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3BreezemoonInputs.java) | 25 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ChatCardInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3ChatCardInputs.java) | 103 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ChatMessageDto.java](../src/main/java/org/b3log/symphony/util/ApiV3ChatMessageDto.java) | 84 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ChatStream.java](../src/main/java/org/b3log/symphony/util/ApiV3ChatStream.java) | 34 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ChatTransaction.java](../src/main/java/org/b3log/symphony/util/ApiV3ChatTransaction.java) | 39 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ClientManagementDto.java](../src/main/java/org/b3log/symphony/util/ApiV3ClientManagementDto.java) | 36 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ClientManagementInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3ClientManagementInputs.java) | 38 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ColumnInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3ColumnInputs.java) | 28 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3CommentInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3CommentInputs.java) | 38 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3CommentLocationQueries.java](../src/main/java/org/b3log/symphony/util/ApiV3CommentLocationQueries.java) | 29 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3CommentTarget.java](../src/main/java/org/b3log/symphony/util/ApiV3CommentTarget.java) | 42 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3CommunityDto.java](../src/main/java/org/b3log/symphony/util/ApiV3CommunityDto.java) | 50 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ConsentSecurity.java](../src/main/java/org/b3log/symphony/util/ApiV3ConsentSecurity.java) | 36 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ContactInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3ContactInputs.java) | 35 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ContentSnapshots.java](../src/main/java/org/b3log/symphony/util/ApiV3ContentSnapshots.java) | 60 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ContentUtil.java](../src/main/java/org/b3log/symphony/util/ApiV3ContentUtil.java) | 56 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3DonationPage.java](../src/main/java/org/b3log/symphony/util/ApiV3DonationPage.java) | 34 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3DraftDto.java](../src/main/java/org/b3log/symphony/util/ApiV3DraftDto.java) | 24 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3DraftInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3DraftInputs.java) | 37 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3EmojiGroupInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3EmojiGroupInputs.java) | 25 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3EmojiItemInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3EmojiItemInputs.java) | 43 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3EmojiTransferInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3EmojiTransferInputs.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3EmotionOptions.java](../src/main/java/org/b3log/symphony/util/ApiV3EmotionOptions.java) | 193 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3EmotionSettingsInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3EmotionSettingsInputs.java) | 34 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3Exception.java](../src/main/java/org/b3log/symphony/util/ApiV3Exception.java) | 12 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3FishGameSubmissionInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3FishGameSubmissionInputs.java) | 79 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3GameSaveInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3GameSaveInputs.java) | 41 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3IdentityImage.java](../src/main/java/org/b3log/symphony/util/ApiV3IdentityImage.java) | 52 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3Invitation.java](../src/main/java/org/b3log/symphony/util/ApiV3Invitation.java) | 36 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3InvitationPurchaseInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3InvitationPurchaseInputs.java) | 12 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3Json.java](../src/main/java/org/b3log/symphony/util/ApiV3Json.java) | 159 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MedalDisplay.java](../src/main/java/org/b3log/symphony/util/ApiV3MedalDisplay.java) | 36 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MedalDto.java](../src/main/java/org/b3log/symphony/util/ApiV3MedalDto.java) | 35 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MedalOrderInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3MedalOrderInputs.java) | 25 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MedalSvg.java](../src/main/java/org/b3log/symphony/util/ApiV3MedalSvg.java) | 76 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MembershipConfiguration.java](../src/main/java/org/b3log/symphony/util/ApiV3MembershipConfiguration.java) | 91 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MembershipDto.java](../src/main/java/org/b3log/symphony/util/ApiV3MembershipDto.java) | 77 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MembershipOpeningInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3MembershipOpeningInputs.java) | 26 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MfaInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3MfaInputs.java) | 19 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MfaSecrets.java](../src/main/java/org/b3log/symphony/util/ApiV3MfaSecrets.java) | 20 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MilestoneDto.java](../src/main/java/org/b3log/symphony/util/ApiV3MilestoneDto.java) | 20 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MilestoneInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3MilestoneInputs.java) | 57 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3MutationStore.java](../src/main/java/org/b3log/symphony/util/ApiV3MutationStore.java) | 14 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3NotificationDto.java](../src/main/java/org/b3log/symphony/util/ApiV3NotificationDto.java) | 22 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3NotificationTypes.java](../src/main/java/org/b3log/symphony/util/ApiV3NotificationTypes.java) | 75 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3OAuthCallbacks.java](../src/main/java/org/b3log/symphony/util/ApiV3OAuthCallbacks.java) | 35 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3OAuthForms.java](../src/main/java/org/b3log/symphony/util/ApiV3OAuthForms.java) | 50 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3OAuthRateLimit.java](../src/main/java/org/b3log/symphony/util/ApiV3OAuthRateLimit.java) | 15 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3OAuthRedirects.java](../src/main/java/org/b3log/symphony/util/ApiV3OAuthRedirects.java) | 25 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3OAuthTransportLogFilter.java](../src/main/java/org/b3log/symphony/util/ApiV3OAuthTransportLogFilter.java) | 58 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ParagraphDto.java](../src/main/java/org/b3log/symphony/util/ApiV3ParagraphDto.java) | 31 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ParagraphQueries.java](../src/main/java/org/b3log/symphony/util/ApiV3ParagraphQueries.java) | 66 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3Personality.java](../src/main/java/org/b3log/symphony/util/ApiV3Personality.java) | 25 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3Pkce.java](../src/main/java/org/b3log/symphony/util/ApiV3Pkce.java) | 31 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3PointTransferInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3PointTransferInputs.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3PostExportFiles.java](../src/main/java/org/b3log/symphony/util/ApiV3PostExportFiles.java) | 46 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3PrivateMessages.java](../src/main/java/org/b3log/symphony/util/ApiV3PrivateMessages.java) | 35 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ProfessionDetailDto.java](../src/main/java/org/b3log/symphony/util/ApiV3ProfessionDetailDto.java) | 69 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ProfessionGuideDto.java](../src/main/java/org/b3log/symphony/util/ApiV3ProfessionGuideDto.java) | 60 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ProfessionPublicDto.java](../src/main/java/org/b3log/symphony/util/ApiV3ProfessionPublicDto.java) | 44 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ProfessionRankingDto.java](../src/main/java/org/b3log/symphony/util/ApiV3ProfessionRankingDto.java) | 70 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ProfessionSettingsInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3ProfessionSettingsInputs.java) | 54 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ProfileQueries.java](../src/main/java/org/b3log/symphony/util/ApiV3ProfileQueries.java) | 53 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ProfileTagInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3ProfileTagInputs.java) | 35 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3RankingDto.java](../src/main/java/org/b3log/symphony/util/ApiV3RankingDto.java) | 92 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ReactionDto.java](../src/main/java/org/b3log/symphony/util/ApiV3ReactionDto.java) | 43 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3RealtimeSessions.java](../src/main/java/org/b3log/symphony/util/ApiV3RealtimeSessions.java) | 211 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3RedPacketInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3RedPacketInputs.java) | 92 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3RedPacketTypes.java](../src/main/java/org/b3log/symphony/util/ApiV3RedPacketTypes.java) | 24 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3RefreshCookies.java](../src/main/java/org/b3log/symphony/util/ApiV3RefreshCookies.java) | 24 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3RepeaterDto.java](../src/main/java/org/b3log/symphony/util/ApiV3RepeaterDto.java) | 31 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3RepeaterPublicationInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3RepeaterPublicationInputs.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3ReportInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3ReportInputs.java) | 24 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3Requests.java](../src/main/java/org/b3log/symphony/util/ApiV3Requests.java) | 99 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3RevisionDto.java](../src/main/java/org/b3log/symphony/util/ApiV3RevisionDto.java) | 24 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3SecurityInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3SecurityInputs.java) | 16 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3Settings.java](../src/main/java/org/b3log/symphony/util/ApiV3Settings.java) | 102 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3SocketHandshake.java](../src/main/java/org/b3log/symphony/util/ApiV3SocketHandshake.java) | 85 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3TokenUtil.java](../src/main/java/org/b3log/symphony/util/ApiV3TokenUtil.java) | 37 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3TransactionSavepoint.java](../src/main/java/org/b3log/symphony/util/ApiV3TransactionSavepoint.java) | 33 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3TransactionStatements.java](../src/main/java/org/b3log/symphony/util/ApiV3TransactionStatements.java) | 17 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3Transactions.java](../src/main/java/org/b3log/symphony/util/ApiV3Transactions.java) | 38 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3UploadTargets.java](../src/main/java/org/b3log/symphony/util/ApiV3UploadTargets.java) | 23 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3Votes.java](../src/main/java/org/b3log/symphony/util/ApiV3Votes.java) | 45 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ApiV3WriteInputs.java](../src/main/java/org/b3log/symphony/util/ApiV3WriteInputs.java) | 70 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ArticleEditPoints.java](../src/main/java/org/b3log/symphony/util/ArticleEditPoints.java) | 25 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ArticlePublicationCaches.java](../src/main/java/org/b3log/symphony/util/ArticlePublicationCaches.java) | 39 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ArticlePublishPoints.java](../src/main/java/org/b3log/symphony/util/ArticlePublishPoints.java) | 71 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ArticleRemovalPolicy.java](../src/main/java/org/b3log/symphony/util/ArticleRemovalPolicy.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ArticleSubmission.java](../src/main/java/org/b3log/symphony/util/ArticleSubmission.java) | 35 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ArticleSubmissionPolicy.java](../src/main/java/org/b3log/symphony/util/ArticleSubmissionPolicy.java) | 50 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ArticleValidation.java](../src/main/java/org/b3log/symphony/util/ArticleValidation.java) | 217 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ArticleWriteQuota.java](../src/main/java/org/b3log/symphony/util/ArticleWriteQuota.java) | 10 | 0 |
| A | [src/main/java/org/b3log/symphony/util/BagMutation.java](../src/main/java/org/b3log/symphony/util/BagMutation.java) | 39 | 0 |
| A | [src/main/java/org/b3log/symphony/util/BreezemoonWriteQuota.java](../src/main/java/org/b3log/symphony/util/BreezemoonWriteQuota.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatBarrageInputs.java](../src/main/java/org/b3log/symphony/util/ChatBarrageInputs.java) | 19 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatBarrageQuota.java](../src/main/java/org/b3log/symphony/util/ChatBarrageQuota.java) | 13 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatMessageAbuseState.java](../src/main/java/org/b3log/symphony/util/ChatMessageAbuseState.java) | 31 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatMessageClients.java](../src/main/java/org/b3log/symphony/util/ChatMessageClients.java) | 42 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatMessageRemovalPolicy.java](../src/main/java/org/b3log/symphony/util/ChatMessageRemovalPolicy.java) | 33 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatMessageRestrictions.java](../src/main/java/org/b3log/symphony/util/ChatMessageRestrictions.java) | 25 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatMessageRevokeFees.java](../src/main/java/org/b3log/symphony/util/ChatMessageRevokeFees.java) | 21 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatMessageText.java](../src/main/java/org/b3log/symphony/util/ChatMessageText.java) | 31 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatMessageValidation.java](../src/main/java/org/b3log/symphony/util/ChatMessageValidation.java) | 26 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatOnlineSnapshot.java](../src/main/java/org/b3log/symphony/util/ChatOnlineSnapshot.java) | 16 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatTopicInputs.java](../src/main/java/org/b3log/symphony/util/ChatTopicInputs.java) | 28 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ChatTopicValue.java](../src/main/java/org/b3log/symphony/util/ChatTopicValue.java) | 32 | 0 |
| A | [src/main/java/org/b3log/symphony/util/CheckinPatchMutation.java](../src/main/java/org/b3log/symphony/util/CheckinPatchMutation.java) | 77 | 0 |
| A | [src/main/java/org/b3log/symphony/util/CommentEditing.java](../src/main/java/org/b3log/symphony/util/CommentEditing.java) | 65 | 0 |
| A | [src/main/java/org/b3log/symphony/util/CommentPublishPoints.java](../src/main/java/org/b3log/symphony/util/CommentPublishPoints.java) | 56 | 0 |
| A | [src/main/java/org/b3log/symphony/util/CommentRealtimeVisibility.java](../src/main/java/org/b3log/symphony/util/CommentRealtimeVisibility.java) | 29 | 0 |
| A | [src/main/java/org/b3log/symphony/util/CommentRemoval.java](../src/main/java/org/b3log/symphony/util/CommentRemoval.java) | 53 | 0 |
| A | [src/main/java/org/b3log/symphony/util/CommentRemovalQueries.java](../src/main/java/org/b3log/symphony/util/CommentRemovalQueries.java) | 30 | 0 |
| A | [src/main/java/org/b3log/symphony/util/CommentValidation.java](../src/main/java/org/b3log/symphony/util/CommentValidation.java) | 47 | 0 |
| A | [src/main/java/org/b3log/symphony/util/CommentWriteQuota.java](../src/main/java/org/b3log/symphony/util/CommentWriteQuota.java) | 13 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ContentNotificationReferences.java](../src/main/java/org/b3log/symphony/util/ContentNotificationReferences.java) | 44 | 0 |
| A | [src/main/java/org/b3log/symphony/util/DailyCheckinMutation.java](../src/main/java/org/b3log/symphony/util/DailyCheckinMutation.java) | 44 | 0 |
| A | [src/main/java/org/b3log/symphony/util/FishGameWriteQuota.java](../src/main/java/org/b3log/symphony/util/FishGameWriteQuota.java) | 16 | 0 |
| A | [src/main/java/org/b3log/symphony/util/FollowMutation.java](../src/main/java/org/b3log/symphony/util/FollowMutation.java) | 38 | 0 |
| A | [src/main/java/org/b3log/symphony/util/LivenessRewardClaim.java](../src/main/java/org/b3log/symphony/util/LivenessRewardClaim.java) | 63 | 0 |
| M | [src/main/java/org/b3log/symphony/util/Mails.java](../src/main/java/org/b3log/symphony/util/Mails.java) | 66 | 8 |
| M | [src/main/java/org/b3log/symphony/util/Markdowns.java](../src/main/java/org/b3log/symphony/util/Markdowns.java) | 9 | 0 |
| A | [src/main/java/org/b3log/symphony/util/MilestoneSubmission.java](../src/main/java/org/b3log/symphony/util/MilestoneSubmission.java) | 22 | 0 |
| M | [src/main/java/org/b3log/symphony/util/NodeUtil.java](../src/main/java/org/b3log/symphony/util/NodeUtil.java) | 8 | 0 |
| M | [src/main/java/org/b3log/symphony/util/OpenIdUtil.java](../src/main/java/org/b3log/symphony/util/OpenIdUtil.java) | 7 | 0 |
| A | [src/main/java/org/b3log/symphony/util/PasswordResetQuota.java](../src/main/java/org/b3log/symphony/util/PasswordResetQuota.java) | 12 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ProfileWriteQuota.java](../src/main/java/org/b3log/symphony/util/ProfileWriteQuota.java) | 10 | 0 |
| A | [src/main/java/org/b3log/symphony/util/ReactionMutation.java](../src/main/java/org/b3log/symphony/util/ReactionMutation.java) | 51 | 0 |
| A | [src/main/java/org/b3log/symphony/util/RegistrationProofContext.java](../src/main/java/org/b3log/symphony/util/RegistrationProofContext.java) | 27 | 0 |
| A | [src/main/java/org/b3log/symphony/util/RegistrationVerificationQuota.java](../src/main/java/org/b3log/symphony/util/RegistrationVerificationQuota.java) | 12 | 0 |
| A | [src/main/java/org/b3log/symphony/util/RhypicUploadTickets.java](../src/main/java/org/b3log/symphony/util/RhypicUploadTickets.java) | 33 | 0 |
| M | [src/main/java/org/b3log/symphony/util/Sessions.java](../src/main/java/org/b3log/symphony/util/Sessions.java) | 49 | 6 |
| A | [src/main/java/org/b3log/symphony/util/SettingsLocaleOptions.java](../src/main/java/org/b3log/symphony/util/SettingsLocaleOptions.java) | 17 | 0 |
| A | [src/main/java/org/b3log/symphony/util/SettingsRedirects.java](../src/main/java/org/b3log/symphony/util/SettingsRedirects.java) | 39 | 0 |
| A | [src/main/java/org/b3log/symphony/util/UserMentionCandidates.java](../src/main/java/org/b3log/symphony/util/UserMentionCandidates.java) | 56 | 0 |
| A | [src/main/java/org/b3log/symphony/util/UserNamePointData.java](../src/main/java/org/b3log/symphony/util/UserNamePointData.java) | 20 | 0 |
| A | [src/main/java/org/b3log/symphony/util/UserNameRules.java](../src/main/java/org/b3log/symphony/util/UserNameRules.java) | 19 | 0 |
| A | [src/main/java/org/b3log/symphony/util/UserRegistrationDefaults.java](../src/main/java/org/b3log/symphony/util/UserRegistrationDefaults.java) | 94 | 0 |
| A | [src/main/java/org/b3log/symphony/util/UserSettingsMutation.java](../src/main/java/org/b3log/symphony/util/UserSettingsMutation.java) | 60 | 0 |

### 生成CSS（3个）

| 状态 | 文件 | +行 | -行 |
| --- | --- | ---: | ---: |
| M | [src/main/resources/css/base.css](../src/main/resources/css/base.css) | 1 | 1 |
| M | [src/main/resources/css/mobile-base.css](../src/main/resources/css/mobile-base.css) | 1 | 1 |
| A | [src/main/resources/css/oauth-consent.css](../src/main/resources/css/oauth-consent.css) | 1 | 0 |

### 经典JS源码（7个）

| 状态 | 文件 | +行 | -行 |
| --- | --- | ---: | ---: |
| M | [src/main/resources/js/article.js](../src/main/resources/js/article.js) | 25 | 10 |
| M | [src/main/resources/js/channel-2.js](../src/main/resources/js/channel-2.js) | 16 | 4 |
| M | [src/main/resources/js/channel.js](../src/main/resources/js/channel.js) | 16 | 4 |
| M | [src/main/resources/js/chat-room-2.js](../src/main/resources/js/chat-room-2.js) | 114 | 31 |
| M | [src/main/resources/js/chat-room.js](../src/main/resources/js/chat-room.js) | 114 | 31 |
| M | [src/main/resources/js/m-article.js](../src/main/resources/js/m-article.js) | 25 | 11 |
| M | [src/main/resources/js/verify.js](../src/main/resources/js/verify.js) | 42 | 10 |

### 生成min.js（7个）

| 状态 | 文件 | +行 | -行 |
| --- | --- | ---: | ---: |
| M | [src/main/resources/js/article.min.js](../src/main/resources/js/article.min.js) | 1 | 1 |
| M | [src/main/resources/js/channel-2.min.js](../src/main/resources/js/channel-2.min.js) | 1 | 1 |
| M | [src/main/resources/js/channel.min.js](../src/main/resources/js/channel.min.js) | 1 | 1 |
| M | [src/main/resources/js/chat-room-2.min.js](../src/main/resources/js/chat-room-2.min.js) | 1 | 1 |
| M | [src/main/resources/js/chat-room.min.js](../src/main/resources/js/chat-room.min.js) | 1 | 1 |
| M | [src/main/resources/js/m-article.min.js](../src/main/resources/js/m-article.min.js) | 1 | 1 |
| M | [src/main/resources/js/verify.min.js](../src/main/resources/js/verify.min.js) | 1 | 1 |

### SCSS源码（4个）

| 状态 | 文件 | +行 | -行 |
| --- | --- | ---: | ---: |
| A | [src/main/resources/scss/_red-packet-risk-dialog.scss](../src/main/resources/scss/_red-packet-risk-dialog.scss) | 35 | 0 |
| M | [src/main/resources/scss/base.scss](../src/main/resources/scss/base.scss) | 2 | 0 |
| M | [src/main/resources/scss/mobile-base.scss](../src/main/resources/scss/mobile-base.scss) | 2 | 0 |
| A | [src/main/resources/scss/oauth-consent.scss](../src/main/resources/scss/oauth-consent.scss) | 25 | 0 |

### 经典PC与移动模板（12个）

| 状态 | 文件 | +行 | -行 |
| --- | --- | ---: | ---: |
| M | [src/main/resources/skins/classic/mobile/admin/milestone.ftl](../src/main/resources/skins/classic/mobile/admin/milestone.ftl) | 5 | 5 |
| M | [src/main/resources/skins/classic/mobile/admin/milestones.ftl](../src/main/resources/skins/classic/mobile/admin/milestones.ftl) | 5 | 5 |
| M | [src/main/resources/skins/classic/mobile/article.ftl](../src/main/resources/skins/classic/mobile/article.ftl) | 2 | 2 |
| M | [src/main/resources/skins/classic/mobile/milestones.ftl](../src/main/resources/skins/classic/mobile/milestones.ftl) | 6 | 6 |
| A | [src/main/resources/skins/classic/mobile/verify/oauth-consent.ftl](../src/main/resources/skins/classic/mobile/verify/oauth-consent.ftl) | 46 | 0 |
| M | [src/main/resources/skins/classic/mobile/verify/register2.ftl](../src/main/resources/skins/classic/mobile/verify/register2.ftl) | 1 | 0 |
| M | [src/main/resources/skins/classic/pc/admin/milestone.ftl](../src/main/resources/skins/classic/pc/admin/milestone.ftl) | 5 | 5 |
| M | [src/main/resources/skins/classic/pc/admin/milestones.ftl](../src/main/resources/skins/classic/pc/admin/milestones.ftl) | 5 | 5 |
| M | [src/main/resources/skins/classic/pc/article.ftl](../src/main/resources/skins/classic/pc/article.ftl) | 2 | 2 |
| M | [src/main/resources/skins/classic/pc/milestones.ftl](../src/main/resources/skins/classic/pc/milestones.ftl) | 6 | 6 |
| A | [src/main/resources/skins/classic/pc/verify/oauth-consent.ftl](../src/main/resources/skins/classic/pc/verify/oauth-consent.ftl) | 46 | 0 |
| M | [src/main/resources/skins/classic/pc/verify/register2.ftl](../src/main/resources/skins/classic/pc/verify/register2.ftl) | 1 | 0 |

### 后端Java测试（204个）

| 状态 | 文件 | +行 | -行 |
| --- | --- | ---: | ---: |
| A | [src/test/java/org/b3log/symphony/processor/ApiV3ClientDisableTest.java](../src/test/java/org/b3log/symphony/processor/ApiV3ClientDisableTest.java) | 235 | 0 |
| A | [src/test/java/org/b3log/symphony/processor/ApiV3ClientManagementTest.java](../src/test/java/org/b3log/symphony/processor/ApiV3ClientManagementTest.java) | 244 | 0 |
| A | [src/test/java/org/b3log/symphony/processor/ApiV3ClientUpdateTest.java](../src/test/java/org/b3log/symphony/processor/ApiV3ClientUpdateTest.java) | 269 | 0 |
| A | [src/test/java/org/b3log/symphony/processor/ApiV3DonationTest.java](../src/test/java/org/b3log/symphony/processor/ApiV3DonationTest.java) | 50 | 0 |
| A | [src/test/java/org/b3log/symphony/processor/ApiV3MarkdownTest.java](../src/test/java/org/b3log/symphony/processor/ApiV3MarkdownTest.java) | 54 | 0 |
| A | [src/test/java/org/b3log/symphony/processor/ApiV3MembershipStyleTest.java](../src/test/java/org/b3log/symphony/processor/ApiV3MembershipStyleTest.java) | 92 | 0 |
| A | [src/test/java/org/b3log/symphony/processor/ApiV3MembershipTest.java](../src/test/java/org/b3log/symphony/processor/ApiV3MembershipTest.java) | 183 | 0 |
| A | [src/test/java/org/b3log/symphony/processor/ApiV3ReportTest.java](../src/test/java/org/b3log/symphony/processor/ApiV3ReportTest.java) | 70 | 0 |
| A | [src/test/java/org/b3log/symphony/processor/AuthBodyTest.java](../src/test/java/org/b3log/symphony/processor/AuthBodyTest.java) | 74 | 0 |
| A | [src/test/java/org/b3log/symphony/processor/LegacyAccountRouteTest.java](../src/test/java/org/b3log/symphony/processor/LegacyAccountRouteTest.java) | 40 | 0 |
| A | [src/test/java/org/b3log/symphony/processor/PrivateMessageRoutingTest.java](../src/test/java/org/b3log/symphony/processor/PrivateMessageRoutingTest.java) | 51 | 0 |
| A | [src/test/java/org/b3log/symphony/processor/RefreshCookieEchoTest.java](../src/test/java/org/b3log/symphony/processor/RefreshCookieEchoTest.java) | 46 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/AccountPasswordResetRequestTest.java](../src/test/java/org/b3log/symphony/repository/AccountPasswordResetRequestTest.java) | 137 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/AccountPasswordResetTest.java](../src/test/java/org/b3log/symphony/repository/AccountPasswordResetTest.java) | 156 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/AccountPendingRegistrationTest.java](../src/test/java/org/b3log/symphony/repository/AccountPendingRegistrationTest.java) | 152 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/AccountRegistrationRequestTest.java](../src/test/java/org/b3log/symphony/repository/AccountRegistrationRequestTest.java) | 290 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/AccountRegistrationTest.java](../src/test/java/org/b3log/symphony/repository/AccountRegistrationTest.java) | 195 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/AccountVerificationTest.java](../src/test/java/org/b3log/symphony/repository/AccountVerificationTest.java) | 120 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3ArticleRevisionTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3ArticleRevisionTest.java) | 153 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3AuthorizationCodeTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3AuthorizationCodeTest.java) | 111 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3AuthorizationRequestTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3AuthorizationRequestTest.java) | 89 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3BagSqlTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3BagSqlTest.java) | 77 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3BreezemoonTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3BreezemoonTest.java) | 90 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3ChatBarrageTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3ChatBarrageTest.java) | 89 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3ChatCardTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3ChatCardTest.java) | 122 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3ChatEventRepositoryTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3ChatEventRepositoryTest.java) | 179 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3ChatPublicationTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3ChatPublicationTest.java) | 228 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3ChatRedPacketMysqlTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3ChatRedPacketMysqlTest.java) | 59 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3ChatRedPacketRiskTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3ChatRedPacketRiskTest.java) | 141 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3ChatRedPacketTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3ChatRedPacketTest.java) | 204 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3ChatTopicTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3ChatTopicTest.java) | 75 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3ClientRegistrationTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3ClientRegistrationTest.java) | 72 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3CommunitySqlTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3CommunitySqlTest.java) | 116 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3FollowSqlTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3FollowSqlTest.java) | 79 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3NotificationRevisionTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3NotificationRevisionTest.java) | 109 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3NotificationSqlTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3NotificationSqlTest.java) | 279 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3OAuthCredentialQueryTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3OAuthCredentialQueryTest.java) | 43 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3OAuthHttpTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3OAuthHttpTest.java) | 117 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3OAuthMaintenanceTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3OAuthMaintenanceTest.java) | 35 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3OAuthProtocolGateTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3OAuthProtocolGateTest.java) | 91 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3ReactionSqlTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3ReactionSqlTest.java) | 118 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ApiV3TokenLifecycleTest.java](../src/test/java/org/b3log/symphony/repository/ApiV3TokenLifecycleTest.java) | 207 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ChatMessageMgmtServiceTest.java](../src/test/java/org/b3log/symphony/repository/ChatMessageMgmtServiceTest.java) | 78 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ChatMessageRemovalTest.java](../src/test/java/org/b3log/symphony/repository/ChatMessageRemovalTest.java) | 110 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ChatMessageWithdrawalEndpointTest.java](../src/test/java/org/b3log/symphony/repository/ChatMessageWithdrawalEndpointTest.java) | 78 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ChatMessageWithdrawalTest.java](../src/test/java/org/b3log/symphony/repository/ChatMessageWithdrawalTest.java) | 165 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ChatTopicMgmtTest.java](../src/test/java/org/b3log/symphony/repository/ChatTopicMgmtTest.java) | 94 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ContentInteractionRemovalTest.java](../src/test/java/org/b3log/symphony/repository/ContentInteractionRemovalTest.java) | 101 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/ContentNotificationRemovalTest.java](../src/test/java/org/b3log/symphony/repository/ContentNotificationRemovalTest.java) | 88 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/LegacyPrivateMessageRevisionTest.java](../src/test/java/org/b3log/symphony/repository/LegacyPrivateMessageRevisionTest.java) | 94 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/PrivateMessagePublicationTest.java](../src/test/java/org/b3log/symphony/repository/PrivateMessagePublicationTest.java) | 168 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/PrivateMessageReadStateTest.java](../src/test/java/org/b3log/symphony/repository/PrivateMessageReadStateTest.java) | 125 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/PrivateMessageRevisionTest.java](../src/test/java/org/b3log/symphony/repository/PrivateMessageRevisionTest.java) | 38 | 0 |
| A | [src/test/java/org/b3log/symphony/repository/PrivateMessageWithdrawalTest.java](../src/test/java/org/b3log/symphony/repository/PrivateMessageWithdrawalTest.java) | 139 | 0 |
| A | [src/test/java/org/b3log/symphony/service/AccountCredentialStateTest.java](../src/test/java/org/b3log/symphony/service/AccountCredentialStateTest.java) | 193 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3AnonymousContentQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3AnonymousContentQueryTest.java) | 230 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3AppLoginMysqlTest.java](../src/test/java/org/b3log/symphony/service/ApiV3AppLoginMysqlTest.java) | 117 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3AppLoginServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3AppLoginServiceTest.java) | 132 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ArticleHeatTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ArticleHeatTest.java) | 70 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ArticlePolicyJdbcTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ArticlePolicyJdbcTest.java) | 54 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ArticleReadMysqlTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ArticleReadMysqlTest.java) | 90 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ArticleReadSettlementMysqlTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ArticleReadSettlementMysqlTest.java) | 86 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ArticleRewardServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ArticleRewardServiceTest.java) | 132 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ArticleStickStateTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ArticleStickStateTest.java) | 33 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ArticleStickTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ArticleStickTest.java) | 123 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ArticleVoteServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ArticleVoteServiceTest.java) | 134 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3AvatarQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3AvatarQueryTest.java) | 72 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3AvatarWriteTest.java](../src/test/java/org/b3log/symphony/service/ApiV3AvatarWriteTest.java) | 105 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ChatMutesTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ChatMutesTest.java) | 55 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ChatOnlineQueryServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ChatOnlineQueryServiceTest.java) | 91 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ChatQueryServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ChatQueryServiceTest.java) | 164 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ColumnMgmtServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ColumnMgmtServiceTest.java) | 142 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3CommentLocationTest.java](../src/test/java/org/b3log/symphony/service/ApiV3CommentLocationTest.java) | 51 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3CommentRewardQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3CommentRewardQueryTest.java) | 66 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3CommentVoteServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3CommentVoteServiceTest.java) | 114 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3CommitFailureTest.java](../src/test/java/org/b3log/symphony/service/ApiV3CommitFailureTest.java) | 110 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ContactTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ContactTest.java) | 87 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3CurrentContentTest.java](../src/test/java/org/b3log/symphony/service/ApiV3CurrentContentTest.java) | 251 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3DiscoveryExtrasQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3DiscoveryExtrasQueryTest.java) | 62 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3DiscoveryQueryServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3DiscoveryQueryServiceTest.java) | 38 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3DraftMgmtServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3DraftMgmtServiceTest.java) | 166 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3EmojiGroupTest.java](../src/test/java/org/b3log/symphony/service/ApiV3EmojiGroupTest.java) | 133 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3EmojiItemTest.java](../src/test/java/org/b3log/symphony/service/ApiV3EmojiItemTest.java) | 160 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3EmojiTransferTest.java](../src/test/java/org/b3log/symphony/service/ApiV3EmojiTransferTest.java) | 148 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3EmotionQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3EmotionQueryTest.java) | 128 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3EmotionSettingsTest.java](../src/test/java/org/b3log/symphony/service/ApiV3EmotionSettingsTest.java) | 123 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3FishGameMgmtServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3FishGameMgmtServiceTest.java) | 147 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3FishGameSubmissionTest.java](../src/test/java/org/b3log/symphony/service/ApiV3FishGameSubmissionTest.java) | 128 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3FollowStateTest.java](../src/test/java/org/b3log/symphony/service/ApiV3FollowStateTest.java) | 95 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3GameSaveMysqlTest.java](../src/test/java/org/b3log/symphony/service/ApiV3GameSaveMysqlTest.java) | 73 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3GameSaveTest.java](../src/test/java/org/b3log/symphony/service/ApiV3GameSaveTest.java) | 171 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3IdempotencyMgmtServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3IdempotencyMgmtServiceTest.java) | 189 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3IdentityApplicationMysqlTest.java](../src/test/java/org/b3log/symphony/service/ApiV3IdentityApplicationMysqlTest.java) | 86 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3IdentityApplicationTest.java](../src/test/java/org/b3log/symphony/service/ApiV3IdentityApplicationTest.java) | 243 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3InvitationPurchaseTest.java](../src/test/java/org/b3log/symphony/service/ApiV3InvitationPurchaseTest.java) | 152 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3InvitationTest.java](../src/test/java/org/b3log/symphony/service/ApiV3InvitationTest.java) | 99 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3MedalDisplayTest.java](../src/test/java/org/b3log/symphony/service/ApiV3MedalDisplayTest.java) | 164 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3MedalImageHttpTest.java](../src/test/java/org/b3log/symphony/service/ApiV3MedalImageHttpTest.java) | 37 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3MedalImageTest.java](../src/test/java/org/b3log/symphony/service/ApiV3MedalImageTest.java) | 59 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3MedalOrderTest.java](../src/test/java/org/b3log/symphony/service/ApiV3MedalOrderTest.java) | 170 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3MedalQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3MedalQueryTest.java) | 125 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3MembershipOpeningTest.java](../src/test/java/org/b3log/symphony/service/ApiV3MembershipOpeningTest.java) | 196 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3MembershipSettingsTest.java](../src/test/java/org/b3log/symphony/service/ApiV3MembershipSettingsTest.java) | 174 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3MfaTest.java](../src/test/java/org/b3log/symphony/service/ApiV3MfaTest.java) | 124 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3MilestoneServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3MilestoneServiceTest.java) | 101 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3PersonalQueryServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3PersonalQueryServiceTest.java) | 34 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3PersonalityQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3PersonalityQueryTest.java) | 81 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3PersonalityWriteTest.java](../src/test/java/org/b3log/symphony/service/ApiV3PersonalityWriteTest.java) | 42 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3PointTransferTest.java](../src/test/java/org/b3log/symphony/service/ApiV3PointTransferTest.java) | 185 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3PostExportMysqlTest.java](../src/test/java/org/b3log/symphony/service/ApiV3PostExportMysqlTest.java) | 61 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3PostExportTest.java](../src/test/java/org/b3log/symphony/service/ApiV3PostExportTest.java) | 150 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3PrivateMessageQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3PrivateMessageQueryTest.java) | 142 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ProfessionDetailQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ProfessionDetailQueryTest.java) | 145 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ProfessionGuideQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ProfessionGuideQueryTest.java) | 126 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ProfessionPrimaryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ProfessionPrimaryTest.java) | 169 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ProfessionPublicQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ProfessionPublicQueryTest.java) | 184 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ProfessionQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ProfessionQueryTest.java) | 135 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ProfessionRankingQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ProfessionRankingQueryTest.java) | 152 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ProfessionSettingsTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ProfessionSettingsTest.java) | 219 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ProfileTagsMysqlTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ProfileTagsMysqlTest.java) | 74 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ProfileTagsTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ProfileTagsTest.java) | 118 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3PublicColumnQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3PublicColumnQueryTest.java) | 87 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3QqSettingsTest.java](../src/test/java/org/b3log/symphony/service/ApiV3QqSettingsTest.java) | 89 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3RealtimeSessionsTest.java](../src/test/java/org/b3log/symphony/service/ApiV3RealtimeSessionsTest.java) | 154 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3RegistrationPolicyTest.java](../src/test/java/org/b3log/symphony/service/ApiV3RegistrationPolicyTest.java) | 74 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3RepeaterQueryTest.java](../src/test/java/org/b3log/symphony/service/ApiV3RepeaterQueryTest.java) | 95 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3RepeaterWriteTest.java](../src/test/java/org/b3log/symphony/service/ApiV3RepeaterWriteTest.java) | 157 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3ReportPublicationTest.java](../src/test/java/org/b3log/symphony/service/ApiV3ReportPublicationTest.java) | 115 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3RevisionTest.java](../src/test/java/org/b3log/symphony/service/ApiV3RevisionTest.java) | 158 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3SecurityTest.java](../src/test/java/org/b3log/symphony/service/ApiV3SecurityTest.java) | 90 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3SocketTicketServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3SocketTicketServiceTest.java) | 193 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3TagSuggestionsTest.java](../src/test/java/org/b3log/symphony/service/ApiV3TagSuggestionsTest.java) | 45 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3UploadTicketServiceTest.java](../src/test/java/org/b3log/symphony/service/ApiV3UploadTicketServiceTest.java) | 89 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3UserNameMysqlTest.java](../src/test/java/org/b3log/symphony/service/ApiV3UserNameMysqlTest.java) | 133 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3UserNameTest.java](../src/test/java/org/b3log/symphony/service/ApiV3UserNameTest.java) | 92 | 0 |
| A | [src/test/java/org/b3log/symphony/service/ApiV3UserSuggestionsTest.java](../src/test/java/org/b3log/symphony/service/ApiV3UserSuggestionsTest.java) | 45 | 0 |
| A | [src/test/java/org/b3log/symphony/service/CheckinMgmtServiceTest.java](../src/test/java/org/b3log/symphony/service/CheckinMgmtServiceTest.java) | 170 | 0 |
| A | [src/test/java/org/b3log/symphony/service/CommentAcceptServiceTest.java](../src/test/java/org/b3log/symphony/service/CommentAcceptServiceTest.java) | 175 | 0 |
| A | [src/test/java/org/b3log/symphony/service/CommentNotificationServiceTest.java](../src/test/java/org/b3log/symphony/service/CommentNotificationServiceTest.java) | 90 | 0 |
| A | [src/test/java/org/b3log/symphony/service/CommentRemovalServiceTest.java](../src/test/java/org/b3log/symphony/service/CommentRemovalServiceTest.java) | 327 | 0 |
| A | [src/test/java/org/b3log/symphony/service/CommentThankServiceTest.java](../src/test/java/org/b3log/symphony/service/CommentThankServiceTest.java) | 150 | 0 |
| A | [src/test/java/org/b3log/symphony/service/CommentVoteServiceTest.java](../src/test/java/org/b3log/symphony/service/CommentVoteServiceTest.java) | 104 | 0 |
| A | [src/test/java/org/b3log/symphony/service/LegacyArticleRewardEndpointTest.java](../src/test/java/org/b3log/symphony/service/LegacyArticleRewardEndpointTest.java) | 79 | 0 |
| A | [src/test/java/org/b3log/symphony/service/LongArticleParagraphAnnotationTest.java](../src/test/java/org/b3log/symphony/service/LongArticleParagraphAnnotationTest.java) | 164 | 0 |
| A | [src/test/java/org/b3log/symphony/service/LongArticleParagraphMigrationTest.java](../src/test/java/org/b3log/symphony/service/LongArticleParagraphMigrationTest.java) | 144 | 0 |
| A | [src/test/java/org/b3log/symphony/service/UserNameMgmtServiceTest.java](../src/test/java/org/b3log/symphony/service/UserNameMgmtServiceTest.java) | 149 | 0 |
| A | [src/test/java/org/b3log/symphony/util/AccountMailTest.java](../src/test/java/org/b3log/symphony/util/AccountMailTest.java) | 37 | 0 |
| A | [src/test/java/org/b3log/symphony/util/AccountPairLocksTest.java](../src/test/java/org/b3log/symphony/util/AccountPairLocksTest.java) | 120 | 0 |
| A | [src/test/java/org/b3log/symphony/util/AccountRegistrationTemplateTest.java](../src/test/java/org/b3log/symphony/util/AccountRegistrationTemplateTest.java) | 26 | 0 |
| A | [src/test/java/org/b3log/symphony/util/AccountSmtpTest.java](../src/test/java/org/b3log/symphony/util/AccountSmtpTest.java) | 54 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3AppLoginProtocolTest.java](../src/test/java/org/b3log/symphony/util/ApiV3AppLoginProtocolTest.java) | 60 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3ArticleCreationTest.java](../src/test/java/org/b3log/symphony/util/ApiV3ArticleCreationTest.java) | 102 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3ArticleEditInputsTest.java](../src/test/java/org/b3log/symphony/util/ApiV3ArticleEditInputsTest.java) | 63 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3ArticleEditingTest.java](../src/test/java/org/b3log/symphony/util/ApiV3ArticleEditingTest.java) | 80 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3ArticleEditorDtoTest.java](../src/test/java/org/b3log/symphony/util/ApiV3ArticleEditorDtoTest.java) | 80 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3ArticleInputsTest.java](../src/test/java/org/b3log/symphony/util/ApiV3ArticleInputsTest.java) | 47 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3ArticlePolicyTest.java](../src/test/java/org/b3log/symphony/util/ApiV3ArticlePolicyTest.java) | 35 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3ChatStreamTest.java](../src/test/java/org/b3log/symphony/util/ApiV3ChatStreamTest.java) | 46 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3CommentInputsTest.java](../src/test/java/org/b3log/symphony/util/ApiV3CommentInputsTest.java) | 32 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3CommentPublicationTest.java](../src/test/java/org/b3log/symphony/util/ApiV3CommentPublicationTest.java) | 83 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3ConsentSecurityTest.java](../src/test/java/org/b3log/symphony/util/ApiV3ConsentSecurityTest.java) | 24 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3ContentUtilTest.java](../src/test/java/org/b3log/symphony/util/ApiV3ContentUtilTest.java) | 85 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3DraftInputsTest.java](../src/test/java/org/b3log/symphony/util/ApiV3DraftInputsTest.java) | 57 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3JsonTest.java](../src/test/java/org/b3log/symphony/util/ApiV3JsonTest.java) | 85 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3MilestoneInputsTest.java](../src/test/java/org/b3log/symphony/util/ApiV3MilestoneInputsTest.java) | 57 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3OAuthTemplateTest.java](../src/test/java/org/b3log/symphony/util/ApiV3OAuthTemplateTest.java) | 29 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3OAuthTransportLogTest.java](../src/test/java/org/b3log/symphony/util/ApiV3OAuthTransportLogTest.java) | 44 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3ParagraphReadTest.java](../src/test/java/org/b3log/symphony/util/ApiV3ParagraphReadTest.java) | 145 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3PkceTest.java](../src/test/java/org/b3log/symphony/util/ApiV3PkceTest.java) | 26 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3PrivateRealtimeRoutingTest.java](../src/test/java/org/b3log/symphony/util/ApiV3PrivateRealtimeRoutingTest.java) | 32 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3ProfileQueriesTest.java](../src/test/java/org/b3log/symphony/util/ApiV3ProfileQueriesTest.java) | 108 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3RankingDtoTest.java](../src/test/java/org/b3log/symphony/util/ApiV3RankingDtoTest.java) | 106 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3RealtimeChannelTest.java](../src/test/java/org/b3log/symphony/util/ApiV3RealtimeChannelTest.java) | 193 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3RealtimeProcessorTest.java](../src/test/java/org/b3log/symphony/util/ApiV3RealtimeProcessorTest.java) | 98 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3RedPacketRoutingTest.java](../src/test/java/org/b3log/symphony/util/ApiV3RedPacketRoutingTest.java) | 29 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3RedPacketTypesTest.java](../src/test/java/org/b3log/symphony/util/ApiV3RedPacketTypesTest.java) | 35 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3RefreshCookiesTest.java](../src/test/java/org/b3log/symphony/util/ApiV3RefreshCookiesTest.java) | 18 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3RequestsTest.java](../src/test/java/org/b3log/symphony/util/ApiV3RequestsTest.java) | 30 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3SettingsTest.java](../src/test/java/org/b3log/symphony/util/ApiV3SettingsTest.java) | 190 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3SocketHandshakeTest.java](../src/test/java/org/b3log/symphony/util/ApiV3SocketHandshakeTest.java) | 56 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3TokenUtilTest.java](../src/test/java/org/b3log/symphony/util/ApiV3TokenUtilTest.java) | 69 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ApiV3WriteInputsTest.java](../src/test/java/org/b3log/symphony/util/ApiV3WriteInputsTest.java) | 76 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ArticleEditPointsTest.java](../src/test/java/org/b3log/symphony/util/ArticleEditPointsTest.java) | 71 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ArticlePublicationCachesTest.java](../src/test/java/org/b3log/symphony/util/ArticlePublicationCachesTest.java) | 71 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ArticlePublishPointsTest.java](../src/test/java/org/b3log/symphony/util/ArticlePublishPointsTest.java) | 96 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ArticleRemovalPolicyTest.java](../src/test/java/org/b3log/symphony/util/ArticleRemovalPolicyTest.java) | 54 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ArticleRemovalSqlTest.java](../src/test/java/org/b3log/symphony/util/ArticleRemovalSqlTest.java) | 58 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ArticleSubmissionPolicyTest.java](../src/test/java/org/b3log/symphony/util/ArticleSubmissionPolicyTest.java) | 59 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ArticleSubmissionTest.java](../src/test/java/org/b3log/symphony/util/ArticleSubmissionTest.java) | 106 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ArticleThoughtTemplateTest.java](../src/test/java/org/b3log/symphony/util/ArticleThoughtTemplateTest.java) | 38 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ArticleValidationTest.java](../src/test/java/org/b3log/symphony/util/ArticleValidationTest.java) | 97 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ChatBarrageQuotaTest.java](../src/test/java/org/b3log/symphony/util/ChatBarrageQuotaTest.java) | 19 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ChatMessageAdmissionTest.java](../src/test/java/org/b3log/symphony/util/ChatMessageAdmissionTest.java) | 89 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ChatMessageRevokeFeesTest.java](../src/test/java/org/b3log/symphony/util/ChatMessageRevokeFeesTest.java) | 31 | 0 |
| A | [src/test/java/org/b3log/symphony/util/ChatMessageValidationTest.java](../src/test/java/org/b3log/symphony/util/ChatMessageValidationTest.java) | 87 | 0 |
| A | [src/test/java/org/b3log/symphony/util/CheckinPatchMutationTest.java](../src/test/java/org/b3log/symphony/util/CheckinPatchMutationTest.java) | 49 | 0 |
| A | [src/test/java/org/b3log/symphony/util/CommentEditingTest.java](../src/test/java/org/b3log/symphony/util/CommentEditingTest.java) | 168 | 0 |
| A | [src/test/java/org/b3log/symphony/util/CommentPublishPointsTest.java](../src/test/java/org/b3log/symphony/util/CommentPublishPointsTest.java) | 97 | 0 |
| A | [src/test/java/org/b3log/symphony/util/CommentValidationTest.java](../src/test/java/org/b3log/symphony/util/CommentValidationTest.java) | 75 | 0 |
| A | [src/test/java/org/b3log/symphony/util/CommentWriteQuotaTest.java](../src/test/java/org/b3log/symphony/util/CommentWriteQuotaTest.java) | 32 | 0 |
| A | [src/test/java/org/b3log/symphony/util/FollowMutationTest.java](../src/test/java/org/b3log/symphony/util/FollowMutationTest.java) | 87 | 0 |
| A | [src/test/java/org/b3log/symphony/util/LivenessRewardClaimTest.java](../src/test/java/org/b3log/symphony/util/LivenessRewardClaimTest.java) | 108 | 0 |
| A | [src/test/java/org/b3log/symphony/util/OAuthCookieTest.java](../src/test/java/org/b3log/symphony/util/OAuthCookieTest.java) | 56 | 0 |
| A | [src/test/java/org/b3log/symphony/util/WriteQuotaTest.java](../src/test/java/org/b3log/symphony/util/WriteQuotaTest.java) | 35 | 0 |

### 审计与回归工具/夹具（158个）

| 状态 | 文件 | +行 | -行 |
| --- | --- | ---: | ---: |
| A | [tools/account-credential-state-test.mjs](../tools/account-credential-state-test.mjs) | 46 | 0 |
| A | [tools/account-registration-fixture.mjs](../tools/account-registration-fixture.mjs) | 55 | 0 |
| A | [tools/api-route-inventory.mjs](../tools/api-route-inventory.mjs) | 176 | 0 |
| A | [tools/api-v3-account-mail-test.mjs](../tools/api-v3-account-mail-test.mjs) | 32 | 0 |
| A | [tools/api-v3-account-smtp-test.mjs](../tools/api-v3-account-smtp-test.mjs) | 21 | 0 |
| A | [tools/api-v3-account-test.mjs](../tools/api-v3-account-test.mjs) | 30 | 0 |
| A | [tools/api-v3-activity-smoke.mjs](../tools/api-v3-activity-smoke.mjs) | 50 | 0 |
| A | [tools/api-v3-activity-test.mjs](../tools/api-v3-activity-test.mjs) | 33 | 0 |
| A | [tools/api-v3-anonymous-content-test.mjs](../tools/api-v3-anonymous-content-test.mjs) | 22 | 0 |
| A | [tools/api-v3-app-login-mysql-test.mjs](../tools/api-v3-app-login-mysql-test.mjs) | 80 | 0 |
| A | [tools/api-v3-app-login-protocol-test.mjs](../tools/api-v3-app-login-protocol-test.mjs) | 8 | 0 |
| A | [tools/api-v3-app-login-schema-mysql-test.py](../tools/api-v3-app-login-schema-mysql-test.py) | 94 | 0 |
| A | [tools/api-v3-app-login-service-test.mjs](../tools/api-v3-app-login-service-test.mjs) | 32 | 0 |
| A | [tools/api-v3-article-read-mysql-test.mjs](../tools/api-v3-article-read-mysql-test.mjs) | 62 | 0 |
| A | [tools/api-v3-article-read-settlement-mysql-test.mjs](../tools/api-v3-article-read-settlement-mysql-test.mjs) | 5 | 0 |
| A | [tools/api-v3-article-realtime-test.mjs](../tools/api-v3-article-realtime-test.mjs) | 19 | 0 |
| A | [tools/api-v3-article-reward-smoke.mjs](../tools/api-v3-article-reward-smoke.mjs) | 48 | 0 |
| A | [tools/api-v3-article-smoke.mjs](../tools/api-v3-article-smoke.mjs) | 93 | 0 |
| A | [tools/api-v3-article-stick-test.mjs](../tools/api-v3-article-stick-test.mjs) | 35 | 0 |
| A | [tools/api-v3-article-test.mjs](../tools/api-v3-article-test.mjs) | 25 | 0 |
| A | [tools/api-v3-article-vote-smoke.mjs](../tools/api-v3-article-vote-smoke.mjs) | 48 | 0 |
| A | [tools/api-v3-auth-body-test.mjs](../tools/api-v3-auth-body-test.mjs) | 27 | 0 |
| A | [tools/api-v3-bag-smoke.mjs](../tools/api-v3-bag-smoke.mjs) | 46 | 0 |
| A | [tools/api-v3-bag-test.mjs](../tools/api-v3-bag-test.mjs) | 33 | 0 |
| A | [tools/api-v3-breezemoon-smoke.mjs](../tools/api-v3-breezemoon-smoke.mjs) | 56 | 0 |
| A | [tools/api-v3-breezemoon-test.mjs](../tools/api-v3-breezemoon-test.mjs) | 34 | 0 |
| A | [tools/api-v3-chat-admission-test.mjs](../tools/api-v3-chat-admission-test.mjs) | 13 | 0 |
| A | [tools/api-v3-chat-barrage-smoke.mjs](../tools/api-v3-chat-barrage-smoke.mjs) | 29 | 0 |
| A | [tools/api-v3-chat-card-render-test.mjs](../tools/api-v3-chat-card-render-test.mjs) | 50 | 0 |
| A | [tools/api-v3-chat-event-test.mjs](../tools/api-v3-chat-event-test.mjs) | 141 | 0 |
| A | [tools/api-v3-chat-mutes-test.mjs](../tools/api-v3-chat-mutes-test.mjs) | 25 | 0 |
| A | [tools/api-v3-chat-publication-test.mjs](../tools/api-v3-chat-publication-test.mjs) | 51 | 0 |
| A | [tools/api-v3-chat-red-packet-mysql-test.mjs](../tools/api-v3-chat-red-packet-mysql-test.mjs) | 114 | 0 |
| A | [tools/api-v3-chat-red-packet-test.mjs](../tools/api-v3-chat-red-packet-test.mjs) | 52 | 0 |
| A | [tools/api-v3-chat-savepoint-test.mjs](../tools/api-v3-chat-savepoint-test.mjs) | 26 | 0 |
| A | [tools/api-v3-chat-send-smoke.mjs](../tools/api-v3-chat-send-smoke.mjs) | 42 | 0 |
| A | [tools/api-v3-chat-smoke.mjs](../tools/api-v3-chat-smoke.mjs) | 49 | 0 |
| A | [tools/api-v3-chat-test.mjs](../tools/api-v3-chat-test.mjs) | 33 | 0 |
| A | [tools/api-v3-chat-topic-smoke.mjs](../tools/api-v3-chat-topic-smoke.mjs) | 30 | 0 |
| A | [tools/api-v3-chat-validation-test.mjs](../tools/api-v3-chat-validation-test.mjs) | 21 | 0 |
| A | [tools/api-v3-chat-withdraw-smoke.mjs](../tools/api-v3-chat-withdraw-smoke.mjs) | 36 | 0 |
| A | [tools/api-v3-checkin-test.mjs](../tools/api-v3-checkin-test.mjs) | 37 | 0 |
| A | [tools/api-v3-client-disable-test.mjs](../tools/api-v3-client-disable-test.mjs) | 20 | 0 |
| A | [tools/api-v3-client-management-test.mjs](../tools/api-v3-client-management-test.mjs) | 19 | 0 |
| A | [tools/api-v3-client-update-test.mjs](../tools/api-v3-client-update-test.mjs) | 22 | 0 |
| A | [tools/api-v3-column-smoke.mjs](../tools/api-v3-column-smoke.mjs) | 70 | 0 |
| A | [tools/api-v3-column-test.mjs](../tools/api-v3-column-test.mjs) | 34 | 0 |
| A | [tools/api-v3-comment-accept-smoke.mjs](../tools/api-v3-comment-accept-smoke.mjs) | 41 | 0 |
| A | [tools/api-v3-comment-delete-smoke.mjs](../tools/api-v3-comment-delete-smoke.mjs) | 37 | 0 |
| A | [tools/api-v3-comment-removal-test.mjs](../tools/api-v3-comment-removal-test.mjs) | 94 | 0 |
| A | [tools/api-v3-comment-reward-query-test.mjs](../tools/api-v3-comment-reward-query-test.mjs) | 29 | 0 |
| A | [tools/api-v3-comment-smoke.mjs](../tools/api-v3-comment-smoke.mjs) | 124 | 0 |
| A | [tools/api-v3-comment-test.mjs](../tools/api-v3-comment-test.mjs) | 24 | 0 |
| A | [tools/api-v3-comment-thank-smoke.mjs](../tools/api-v3-comment-thank-smoke.mjs) | 37 | 0 |
| A | [tools/api-v3-comment-vote-smoke.mjs](../tools/api-v3-comment-vote-smoke.mjs) | 48 | 0 |
| A | [tools/api-v3-community-smoke.mjs](../tools/api-v3-community-smoke.mjs) | 74 | 0 |
| A | [tools/api-v3-community-test.mjs](../tools/api-v3-community-test.mjs) | 33 | 0 |
| A | [tools/api-v3-contact-test.mjs](../tools/api-v3-contact-test.mjs) | 23 | 0 |
| A | [tools/api-v3-content-current-test.mjs](../tools/api-v3-content-current-test.mjs) | 54 | 0 |
| A | [tools/api-v3-content-smoke.mjs](../tools/api-v3-content-smoke.mjs) | 147 | 0 |
| A | [tools/api-v3-contract-audit-test.mjs](../tools/api-v3-contract-audit-test.mjs) | 20 | 0 |
| A | [tools/api-v3-contract-audit.mjs](../tools/api-v3-contract-audit.mjs) | 29 | 0 |
| A | [tools/api-v3-discovery-extras-test.mjs](../tools/api-v3-discovery-extras-test.mjs) | 24 | 0 |
| A | [tools/api-v3-discovery-smoke.mjs](../tools/api-v3-discovery-smoke.mjs) | 135 | 0 |
| A | [tools/api-v3-donation-test.mjs](../tools/api-v3-donation-test.mjs) | 16 | 0 |
| A | [tools/api-v3-draft-smoke.mjs](../tools/api-v3-draft-smoke.mjs) | 109 | 0 |
| A | [tools/api-v3-emoji-group-test.mjs](../tools/api-v3-emoji-group-test.mjs) | 23 | 0 |
| A | [tools/api-v3-emoji-item-test.mjs](../tools/api-v3-emoji-item-test.mjs) | 27 | 0 |
| A | [tools/api-v3-emoji-transfer-test.mjs](../tools/api-v3-emoji-transfer-test.mjs) | 31 | 0 |
| A | [tools/api-v3-emotion-settings-test.mjs](../tools/api-v3-emotion-settings-test.mjs) | 23 | 0 |
| A | [tools/api-v3-emotion-test.mjs](../tools/api-v3-emotion-test.mjs) | 33 | 0 |
| A | [tools/api-v3-feature-schema-mysql-test.py](../tools/api-v3-feature-schema-mysql-test.py) | 65 | 0 |
| A | [tools/api-v3-feed-query-test.mjs](../tools/api-v3-feed-query-test.mjs) | 28 | 0 |
| A | [tools/api-v3-fish-game-smoke.mjs](../tools/api-v3-fish-game-smoke.mjs) | 57 | 0 |
| A | [tools/api-v3-fish-game-submission-test.mjs](../tools/api-v3-fish-game-submission-test.mjs) | 26 | 0 |
| A | [tools/api-v3-fish-game-test.mjs](../tools/api-v3-fish-game-test.mjs) | 34 | 0 |
| A | [tools/api-v3-follow-smoke.mjs](../tools/api-v3-follow-smoke.mjs) | 67 | 0 |
| A | [tools/api-v3-follow-state-test.mjs](../tools/api-v3-follow-state-test.mjs) | 33 | 0 |
| A | [tools/api-v3-follow-test.mjs](../tools/api-v3-follow-test.mjs) | 34 | 0 |
| A | [tools/api-v3-game-save-mysql-test.mjs](../tools/api-v3-game-save-mysql-test.mjs) | 78 | 0 |
| A | [tools/api-v3-game-save-test.mjs](../tools/api-v3-game-save-test.mjs) | 24 | 0 |
| A | [tools/api-v3-identity-application-mysql-test.mjs](../tools/api-v3-identity-application-mysql-test.mjs) | 76 | 0 |
| A | [tools/api-v3-identity-application-test.mjs](../tools/api-v3-identity-application-test.mjs) | 23 | 0 |
| A | [tools/api-v3-interaction-removal-test.mjs](../tools/api-v3-interaction-removal-test.mjs) | 21 | 0 |
| A | [tools/api-v3-invitation-purchase-test.mjs](../tools/api-v3-invitation-purchase-test.mjs) | 26 | 0 |
| A | [tools/api-v3-invitation-test.mjs](../tools/api-v3-invitation-test.mjs) | 22 | 0 |
| A | [tools/api-v3-markdown-test.mjs](../tools/api-v3-markdown-test.mjs) | 22 | 0 |
| A | [tools/api-v3-medal-display-test.mjs](../tools/api-v3-medal-display-test.mjs) | 22 | 0 |
| A | [tools/api-v3-medal-image-http-test.mjs](../tools/api-v3-medal-image-http-test.mjs) | 33 | 0 |
| A | [tools/api-v3-medal-image-test.mjs](../tools/api-v3-medal-image-test.mjs) | 18 | 0 |
| A | [tools/api-v3-medal-order-test.mjs](../tools/api-v3-medal-order-test.mjs) | 22 | 0 |
| A | [tools/api-v3-medal-test.mjs](../tools/api-v3-medal-test.mjs) | 31 | 0 |
| A | [tools/api-v3-membership-opening-test.mjs](../tools/api-v3-membership-opening-test.mjs) | 30 | 0 |
| A | [tools/api-v3-membership-settings-test.mjs](../tools/api-v3-membership-settings-test.mjs) | 24 | 0 |
| A | [tools/api-v3-membership-style-test.mjs](../tools/api-v3-membership-style-test.mjs) | 34 | 0 |
| A | [tools/api-v3-membership-test.mjs](../tools/api-v3-membership-test.mjs) | 34 | 0 |
| A | [tools/api-v3-mfa-test.mjs](../tools/api-v3-mfa-test.mjs) | 19 | 0 |
| A | [tools/api-v3-migration-chain-mysql-test.py](../tools/api-v3-migration-chain-mysql-test.py) | 149 | 0 |
| A | [tools/api-v3-milestone-smoke.mjs](../tools/api-v3-milestone-smoke.mjs) | 59 | 0 |
| A | [tools/api-v3-milestone-test.mjs](../tools/api-v3-milestone-test.mjs) | 34 | 0 |
| A | [tools/api-v3-mysql-current-read-test.py](../tools/api-v3-mysql-current-read-test.py) | 143 | 0 |
| A | [tools/api-v3-name-card-smoke.mjs](../tools/api-v3-name-card-smoke.mjs) | 35 | 0 |
| A | [tools/api-v3-notification-smoke.mjs](../tools/api-v3-notification-smoke.mjs) | 99 | 0 |
| A | [tools/api-v3-notification-test.mjs](../tools/api-v3-notification-test.mjs) | 37 | 0 |
| A | [tools/api-v3-oauth-cookie-test.mjs](../tools/api-v3-oauth-cookie-test.mjs) | 23 | 0 |
| A | [tools/api-v3-oauth-smoke.mjs](../tools/api-v3-oauth-smoke.mjs) | 25 | 0 |
| A | [tools/api-v3-oauth-template-test.mjs](../tools/api-v3-oauth-template-test.mjs) | 15 | 0 |
| A | [tools/api-v3-paragraph-test.mjs](../tools/api-v3-paragraph-test.mjs) | 55 | 0 |
| A | [tools/api-v3-patch-checkin-smoke.mjs](../tools/api-v3-patch-checkin-smoke.mjs) | 34 | 0 |
| A | [tools/api-v3-personal-smoke.mjs](../tools/api-v3-personal-smoke.mjs) | 96 | 0 |
| A | [tools/api-v3-point-transfer-test.mjs](../tools/api-v3-point-transfer-test.mjs) | 29 | 0 |
| A | [tools/api-v3-post-export-mysql-test.mjs](../tools/api-v3-post-export-mysql-test.mjs) | 62 | 0 |
| A | [tools/api-v3-post-export-test.mjs](../tools/api-v3-post-export-test.mjs) | 24 | 0 |
| A | [tools/api-v3-private-message-test.mjs](../tools/api-v3-private-message-test.mjs) | 43 | 0 |
| A | [tools/api-v3-profession-detail-test.mjs](../tools/api-v3-profession-detail-test.mjs) | 20 | 0 |
| A | [tools/api-v3-profession-guide-test.mjs](../tools/api-v3-profession-guide-test.mjs) | 20 | 0 |
| A | [tools/api-v3-profession-primary-test.mjs](../tools/api-v3-profession-primary-test.mjs) | 22 | 0 |
| A | [tools/api-v3-profession-public-test.mjs](../tools/api-v3-profession-public-test.mjs) | 29 | 0 |
| A | [tools/api-v3-profession-ranking-test.mjs](../tools/api-v3-profession-ranking-test.mjs) | 19 | 0 |
| A | [tools/api-v3-profession-settings-test.mjs](../tools/api-v3-profession-settings-test.mjs) | 23 | 0 |
| A | [tools/api-v3-profession-test.mjs](../tools/api-v3-profession-test.mjs) | 23 | 0 |
| A | [tools/api-v3-profile-tags-mysql-test.mjs](../tools/api-v3-profile-tags-mysql-test.mjs) | 75 | 0 |
| A | [tools/api-v3-profile-tags-test.mjs](../tools/api-v3-profile-tags-test.mjs) | 33 | 0 |
| A | [tools/api-v3-profile-test.mjs](../tools/api-v3-profile-test.mjs) | 33 | 0 |
| A | [tools/api-v3-public-column-test.mjs](../tools/api-v3-public-column-test.mjs) | 23 | 0 |
| A | [tools/api-v3-qq-settings-test.mjs](../tools/api-v3-qq-settings-test.mjs) | 5 | 0 |
| A | [tools/api-v3-ranking-smoke.mjs](../tools/api-v3-ranking-smoke.mjs) | 58 | 0 |
| A | [tools/api-v3-ranking-test.mjs](../tools/api-v3-ranking-test.mjs) | 31 | 0 |
| A | [tools/api-v3-reaction-smoke.mjs](../tools/api-v3-reaction-smoke.mjs) | 66 | 0 |
| A | [tools/api-v3-reaction-test.mjs](../tools/api-v3-reaction-test.mjs) | 33 | 0 |
| A | [tools/api-v3-realtime-smoke.mjs](../tools/api-v3-realtime-smoke.mjs) | 77 | 0 |
| A | [tools/api-v3-red-packet-bridge-ui-test.mjs](../tools/api-v3-red-packet-bridge-ui-test.mjs) | 11 | 0 |
| A | [tools/api-v3-red-packet-status-ui-test.mjs](../tools/api-v3-red-packet-status-ui-test.mjs) | 8 | 0 |
| A | [tools/api-v3-red-packet-test.mjs](../tools/api-v3-red-packet-test.mjs) | 27 | 0 |
| A | [tools/api-v3-repeater-test.mjs](../tools/api-v3-repeater-test.mjs) | 29 | 0 |
| A | [tools/api-v3-repeater-write-test.mjs](../tools/api-v3-repeater-write-test.mjs) | 26 | 0 |
| A | [tools/api-v3-report-test.mjs](../tools/api-v3-report-test.mjs) | 22 | 0 |
| A | [tools/api-v3-revision-test.mjs](../tools/api-v3-revision-test.mjs) | 23 | 0 |
| A | [tools/api-v3-route-access-audit-test.mjs](../tools/api-v3-route-access-audit-test.mjs) | 11 | 0 |
| A | [tools/api-v3-route-access-audit.mjs](../tools/api-v3-route-access-audit.mjs) | 52 | 0 |
| A | [tools/api-v3-security-test.mjs](../tools/api-v3-security-test.mjs) | 19 | 0 |
| A | [tools/api-v3-settings-smoke.mjs](../tools/api-v3-settings-smoke.mjs) | 52 | 0 |
| A | [tools/api-v3-settings-test.mjs](../tools/api-v3-settings-test.mjs) | 41 | 0 |
| A | [tools/api-v3-socket-test.mjs](../tools/api-v3-socket-test.mjs) | 33 | 0 |
| A | [tools/api-v3-tag-suggestions-test.mjs](../tools/api-v3-tag-suggestions-test.mjs) | 27 | 0 |
| A | [tools/api-v3-token-test.mjs](../tools/api-v3-token-test.mjs) | 39 | 0 |
| A | [tools/api-v3-upload-smoke.mjs](../tools/api-v3-upload-smoke.mjs) | 34 | 0 |
| A | [tools/api-v3-upload-test.mjs](../tools/api-v3-upload-test.mjs) | 18 | 0 |
| A | [tools/api-v3-user-name-mysql-test.mjs](../tools/api-v3-user-name-mysql-test.mjs) | 61 | 0 |
| A | [tools/api-v3-user-name-test.mjs](../tools/api-v3-user-name-test.mjs) | 30 | 0 |
| A | [tools/api-v3-user-suggestions-test.mjs](../tools/api-v3-user-suggestions-test.mjs) | 24 | 0 |
| A | [tools/api-v3-write-test.mjs](../tools/api-v3-write-test.mjs) | 37 | 0 |
| A | [tools/chat-barrage-notice-fixture.mjs](../tools/chat-barrage-notice-fixture.mjs) | 33 | 0 |
| A | [tools/chat-topic-notice-fixture.mjs](../tools/chat-topic-notice-fixture.mjs) | 53 | 0 |
| A | [tools/fixtures/v3-feed-query.java.txt](../tools/fixtures/v3-feed-query.java.txt) | 70 | 0 |
| A | [tools/newcomer-role-events-test.mjs](../tools/newcomer-role-events-test.mjs) | 50 | 0 |
| A | [tools/point-transfer-lock-test.mjs](../tools/point-transfer-lock-test.mjs) | 18 | 0 |
| A | [tools/thought-render-test.mjs](../tools/thought-render-test.mjs) | 60 | 0 |

## 10. 本PR提交索引（47个，最新在前）

- [`928f0846`](https://github.com/FishPiOffical/rhythm/commit/928f0846) docs: verify real read settlement to profession growth chain
- [`276102f6`](https://github.com/FishPiOffical/rhythm/commit/276102f6) docs: separate release acceptance and record final local regression
- [`3088850c`](https://github.com/FishPiOffical/rhythm/commit/3088850c) docs: verify live historical notification actor boundaries
- [`aff19442`](https://github.com/FishPiOffical/rhythm/commit/aff19442) fix: omit legacy chat notification actors without stable IDs
- [`bb4a5431`](https://github.com/FishPiOffical/rhythm/commit/bb4a5431) docs: verify live v3 login activity and announcement delivery
- [`8e9f9515`](https://github.com/FishPiOffical/rhythm/commit/8e9f9515) fix: record successful v3 login time atomically
- [`4f0884dd`](https://github.com/FishPiOffical/rhythm/commit/4f0884dd) docs: verify admin creation and identify v3 login activity gap
- [`c4f7e993`](https://github.com/FishPiOffical/rhythm/commit/c4f7e993) fix: initialize good-post marker for admin article creation
- [`da253c66`](https://github.com/FishPiOffical/rhythm/commit/da253c66) docs: track actual read to profession natural settlement check
- [`c00a0f43`](https://github.com/FishPiOffical/rhythm/commit/c00a0f43) docs: verify authenticated notification actor navigation
- [`c51bc0d7`](https://github.com/FishPiOffical/rhythm/commit/c51bc0d7) docs: record notification client actor attribution fix
- [`b1af6022`](https://github.com/FishPiOffical/rhythm/commit/b1af6022) docs: verify current notification actors against live caches
- [`a100a8d4`](https://github.com/FishPiOffical/rhythm/commit/a100a8d4) docs: reconcile current v3 remaining acceptance tasks
- [`51206bd0`](https://github.com/FishPiOffical/rhythm/commit/51206bd0) fix: read current actor profiles in v3 notifications
- [`64096334`](https://github.com/FishPiOffical/rhythm/commit/64096334) docs: verify v3 profession consumption replay and reversal
- [`b54b46d1`](https://github.com/FishPiOffical/rhythm/commit/b54b46d1) docs: verify nonempty v3 profession reads and privacy
- [`68a142b0`](https://github.com/FishPiOffical/rhythm/commit/68a142b0) fix: harden v3 authorization and content workflows
- [`8df96d3e`](https://github.com/FishPiOffical/rhythm/commit/8df96d3e) fix: keep classic risk confirmation dialogs within narrow viewports
- [`9dd19343`](https://github.com/FishPiOffical/rhythm/commit/9dd19343) fix: synchronize completed risk packet state across classic channels
- [`6a3d663e`](https://github.com/FishPiOffical/rhythm/commit/6a3d663e) feat: support explicit legacy risk packet claims and inactive sender settlement
- [`3a079f3c`](https://github.com/FishPiOffical/rhythm/commit/3a079f3c) tools: audit reachable v3 registrations against API contracts
- [`0cb22019`](https://github.com/FishPiOffical/rhythm/commit/0cb22019) docs: record v3 client boundary audit and deferred runtime checks
- [`f84c8d37`](https://github.com/FishPiOffical/rhythm/commit/f84c8d37) docs: record approved retirement of website legacy login bridge
- [`add92b49`](https://github.com/FishPiOffical/rhythm/commit/add92b49) docs: record article response binding and empty-page recovery
- [`1603da03`](https://github.com/FishPiOffical/rhythm/commit/1603da03) docs: record current v3 article read verification
- [`8f980497`](https://github.com/FishPiOffical/rhythm/commit/8f980497) docs: record v3 directory pagination migration
- [`d7a2d3d2`](https://github.com/FishPiOffical/rhythm/commit/d7a2d3d2) docs: record v3 home resource migration and verification
- [`04772425`](https://github.com/FishPiOffical/rhythm/commit/04772425) docs(v3): record real article and column pagination migration
- [`32d3e2d6`](https://github.com/FishPiOffical/rhythm/commit/32d3e2d6) feat(api-v3): add authorized sanitized medal images
- [`1d769aa4`](https://github.com/FishPiOffical/rhythm/commit/1d769aa4) feat(api-v3): add recent members discovery and log metadata
- [`4f09475b`](https://github.com/FishPiOffical/rhythm/commit/4f09475b) fix: filter v3 public columns by current visible chapters
- [`9a90fda3`](https://github.com/FishPiOffical/rhythm/commit/9a90fda3) docs: record v3 registration and recovery client migration
- [`0820a4d0`](https://github.com/FishPiOffical/rhythm/commit/0820a4d0) docs: record v3 member page migration and validation
- [`eda3c9f8`](https://github.com/FishPiOffical/rhythm/commit/eda3c9f8) docs: record v3 public medals migration and validation
- [`7f5ce73b`](https://github.com/FishPiOffical/rhythm/commit/7f5ce73b) docs: record v3 public user card migration and coverage
- [`0c4d27cd`](https://github.com/FishPiOffical/rhythm/commit/0c4d27cd) feat: guard optional reward date before fresh atomic claims
- [`18939bb6`](https://github.com/FishPiOffical/rhythm/commit/18939bb6) docs: record v3 game save client migration and verification
- [`c58ac618`](https://github.com/FishPiOffical/rhythm/commit/c58ac618) docs: record v3 rankings migration and full client checks
- [`1653a68a`](https://github.com/FishPiOffical/rhythm/commit/1653a68a) docs: record v3 web APP login migration and verification
- [`44730951`](https://github.com/FishPiOffical/rhythm/commit/44730951) docs: record v3 statistics client validation
- [`c7c62207`](https://github.com/FishPiOffical/rhythm/commit/c7c62207) docs: record v3 milestones client migration and validation
- [`a966bfc9`](https://github.com/FishPiOffical/rhythm/commit/a966bfc9) docs: record v3 city client migration and browser validation
- [`bd733a74`](https://github.com/FishPiOffical/rhythm/commit/bd733a74) docs: record v3 fish game client migration and checks
- [`882de6dc`](https://github.com/FishPiOffical/rhythm/commit/882de6dc) docs: record v3 repeater client migration and verification
- [`74bd1a72`](https://github.com/FishPiOffical/rhythm/commit/74bd1a72) feat: extend v3 APIs and transactional account and community flows
- [`c09e48cc`](https://github.com/FishPiOffical/rhythm/commit/c09e48cc) feat(api): add OAuth client management and remaining v3 queries
- [`6231c4f0`](https://github.com/FishPiOffical/rhythm/commit/6231c4f0) feat(api): implement v3 endpoints and transactional compatibility fixes
