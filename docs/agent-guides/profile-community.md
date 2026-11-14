# 个人资料、表情、关注与社区

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[身份与凭据](auth-account.md)、[公开数据](security-access.md)、[上传与存档](frontend-games.md)。

## 资料设置与读取

- 本人QQ由ApiV3QqSettingsService独立第一方当前授权、settings.qq严格根与回执管理，公开/第三方profile响应不扩字段；旧Latke Strings.isNumeric实际是Double.parseDouble，禁止用于QQ纯数字验证。个人标签ApiV3ProfileTagsService按owner→统计namespace→当前目录锁，标签/creator/self/统计/设置/settings.tags回执同根，缓存边界后刷新；ApiV3ProfileTagInputs先验每个原项，不能复用旧Tag.formatTags静默丢弃/截4项。共享Tag.normalizeAlias但旧写未全共锁，不宣称新旧全局唯一或跨实例缓存强一致。
- 本人匿名创作GET由`ApiV3AnonymousContentQueryService`提供元数据，评论先用有界讨论候选/正文预算及共享`UserMentionCandidates`精确解析，再绑定当前正文SHA2与actor快照做SQL计数分页；超预算503而非截断。管理员用Java精确角色判断，不能用CI数据库字符串比较扩大权限。邀请码状态POST仅投影stored status，不读/返预留字段，不把unused当可注册保证。
- v3 MBTI由`ApiV3Personality`严格规范16类型及可选-A/-T，大小写/首尾空白规范化、空值清空、无效422（用户已确认）；GET/目录需profile.read，PUT走settings.write及settings.personality幂等事务，锁当前有效用户仅写mbti/更新时间，沿用ProfileWriteQuota。旧`SettingsProcessor.checkMBTI`仍用于旧资料/注册，禁止顺手改变其规范化行为。
- v3头像GET/PUT在`ApiV3SettingsProcessor`：GET由`ApiV3AvatarQueryService`只读本人当前有效账号URL，PUT用settings.avatar幂等事务和`UserSettingsMutation`锁用户后仅写URL/type/更新时间；允许任意合法HTTPS、255 UTF-16、保留签名query、不抓取远程内容。旧头像设置及页面展示规则保持，不能用`AvatarQueryService`处理后的缩放URL代替本人保存的地址。
- v3 设置走 `ApiV3SettingsProcessor`，资料/隐私/显示采用部分更新；旧隐私/皮肤与 v3 共用 `UserSettingsMgmtService`。事务中只按列更新，不提前写用户/会话缓存；结束后锁行读取已提交数据刷新缓存，失败则清缓存；v3 榜单快照用设置修订号失效，防止旧加载覆盖清理。资料修改与旧入口共用 `ProfileWriteQuota`，仍走原内容审核。功能/位置/语言也走同一事务体；旧功能页的功能字段与显示模式须同事务更新。首页跳转新旧入口共用 `SettingsRedirects` 校验精确 origin 与部署路径，不能用 URL 字符串前缀判断站内。
- v3 个人读取在 `ApiV3PersonalProcessor` / `ApiV3PersonalQueryService`；scope 分 profile.read、content.read、activity.read，目标用户只能来自 Bearer。草稿详情必须用 owner+ID 联合过滤，积分 DTO 不得带双边余额或内部应用请求编号。
- v3 主页评论与关注动态的查询片段在 `ApiV3ProfileQueries`：评论列表不聚合讨论帖，私密正文复用 `commentDto`；`watching` 是关注用户/标签动态，`watching/articles` 是 `ARTICLE_WATCH` 订阅，不能与 `following/articles` 收藏混淆。
- v3 搜索/标签/领域/用户文章共用 `ApiV3DiscoveryQueryService` 的可见性、计数和分页条件；`ApiV3ContentDtoService` 只做公开 DTO 映射。用户主页子列表需用 Bearer 阅读者检查对应隐私开关；禁止从 Sessions 或旧 apiKey 补身份。
- `/me.capabilities` 仅为第一方网页提示：管理撤回复用旧 level3，历史和后台入口读取当前 role_permission；第三方全部 false，isAdmin 仍仅管理员。公开资料新增 userRoleId 仅作身份标签，不能按标签决定操作授权。
- v3公开资料`ApiV3DiscoveryQueryService.profile`返回cardBg/userPoint/onlineMinute，cardBg只读目标system_settings的单字段并校验图片URL，禁止裸返settings；余额和累计分钟沿用主页公开展示，userPointStatus为流水权限、userOnlineStatus为在线标志权限，不能混为余额/累计时长。

## 关注与社区内容

- 鱼游投稿链路由 `FishGameProcessor` + `FishGameMgmtService` + `FishGameQueryService` 负责，数据表为 `fish_game`、`fish_game_vote`、`fish_game_comment`；投稿必须通过 OAuth 说明网址校验后进入待审，管理员导入旧清单按已审核处理并按目标网址去重。`/activities` 只展示已审核记录并按点赞数排序，PC/移动模板和资源需同步维护。
- v3鱼游投稿/编辑在`ApiV3FishGameSubmissionService`：只校验旧OpenID授权URL参数及游戏同站回调，不抓取/证明归属；当前与待审OAuth分列，旧审核批准时随pending内容合并，手改URL清旧OAuth。先迁移20261023鱼游SQL；新旧写入口未整体共锁。
- 单项本人关注状态在`ApiV3FollowQueryService#userState`，当前actor/target有效及关系EXISTS同一只读SQL，仅targetId/following；不调用吞错的旧isFollowing。客户端关注命令只保存未确认的明确PUT/DELETE，回放结果后必须GET当前状态，不能把历史receipt当最新状态。
- 举报`ApiV3ReportPublicationService`预hint→排序用户→回执→当前目标，fresh重验绑定、重放不依赖目标；仅复用最早pending，旧入口未共用去重。新memo存储编码供两端管理模板直接输出，原始/编码长度均有界。聊天室存储userOId有字符串和整数两种，归属校验须兼容整数且不能浮点化ID。
- v3复读投稿`ApiV3RepeaterPublicationService`绑定职业来源与回执同严格根；新旧点赞共用`RepeaterLikeMutationService`，用户→目标→本人likes，COUNT用当前锁定读取；只收敛本人重复，保留其他历史重复计数。旧toggle请求/成功字段保持，拒绝嵌套根，不能绕回旧分开计数写入。
- v3复读机`ApiV3RepeaterQueryService`保留前N列表/前50排除后随机池语义，不假称全量分页；可选content.read与匿名盾，liked仅当前Bearer，失效作者不移除内容而author=null。
- 大事记个人投稿查询走 `ApiV3MilestoneProcessor` + `ApiV3MilestoneQueryService`，必须 owner+ID 联合过滤，不能复用公开详情。创建的事务内方法在 `MilestoneMgmtService`，`MilestoneSubmission` 白名单强制作者/待审状态/时间；缓存只在提交后清理。v3 投稿正文已确认采用 Markdown，在入口渲染清洗为 HTML 后保存，以兼容既有时间线；本人 content 返回保存后的 HTML。
- v3 鱼游列表默认按总投票数、点赞数、时间排序（用户已确认），保留 `sort=likes`；不要依据旧的 `/activities` 点赞排序说明更改该默认值。
- v3 清风明月发布/编辑/删除复用 `BreezemoonMgmtService` 的事务内方法，职业发布/撤销事件与内容同事务；`BreezemoonWriteQuota` 与旧入口共用业务频控。公开读取必须检查作者的 `userBreezemoonStatus`；关注流只读取关注用户公开有效内容且不回退全站；编辑不得改变作者、创建时间或审核状态。实时广播只在发布幂等首次确认提交后尽力发送。
- v3 鱼游投票/评论复用 `FishGameMgmtService` 显式事务内方法，先锁父鱼游并验证已审核；投票与计数同事务，PUT 设置、DELETE 清除，旧入口仍 toggle。新旧业务额度共用 `FishGameWriteQuota`；后台元数据更新必须限定字段，不能用旧对象覆盖投票计数。
- v3 社区读取走 `ApiV3CommunityQueryService`：鱼游/大事记必须按审核状态过滤；同城用户和文章均需检查作者公开位置，文章还排除匿名发表。统计只输出明确数字字段，在线总数已包含成员；鱼游新 API 必须使用真实分页，不能复用旧 getApproved 的 100 条截断。
- v3 排行榜由 `ApiV3RankingProcessor` + `ApiV3RankingQueryService` 提供；邀请/赞助/优选/小冰共用 `RankingQueryService`，旧接口也调用该服务。游戏榜只投影排名指标，不得直接输出 cloud 存档；JSON 集合读取需兼容 `JSONArray` 与 `List`。
- 关注/收藏/订阅统一经 `FollowMgmtService#setInCurrentTransaction` + `FollowMutation`，锁定操作者与目标后维护关系/计数；旧入口自行提交事务，v3 加入幂等事务。缓存需在提交或回滚后失效；关注通知经 `NotificationMgmtService#addFollowNotificationInCurrentTransaction` 写入，提交后再刷新。
- 复读机新旧投稿共用 RepeaterContentModerationService 七牛审核（事务外，异常拒绝）；v3 新回执以 textVersion 区分保留换行规则，旧成功回执仅兼容读。举报 type=4 复用 report；管理员删除须同根软删、职业 targetRemoved 与 operation 审计，PC/mobile 首页及举报后台均有入口。

## 历史与表情

- 分组写入`ApiV3EmojiGroupService`仅本人type0，type1不可改删，删除保留全局图片/其他组/分享；旧写不共锁，不能宣称新旧全局唯一。
- 图片成员`ApiV3EmojiItemService`以内部namespace先于actor锁串行，完整URL按BINARY精确身份，自定义添加镜像全部、移除全部级联本人组，历史损坏不自动清理。
- 图片分享/导入/迁移在`ApiV3EmojiTransferService`，严格校验每个原始项后去重，坏项整批422；快照独立于源组，迁移仅本人emojis云数据，不开放任意cloud键。
- 常用表情PUT在`ApiV3EmotionSettingsService`：仅目录别名/有序数组，空数组恢复默认，严格根与settings.emotions回执仅替换本人type0；旧setEmotionList仍删全部类型且不共用actor锁，不宣称新旧整体串行。
- v3表情四个GET由`ApiV3EmotionProcessor`提供本人常用/目录/图片分组与条目，均profile.read；不调用会创建默认组的旧GET，items查询重复绑定owner。PC/mobile旧目录与v3共用`ApiV3EmotionOptions`，保持EOF分隔但对外去除；
- 新版表情面板的入口与工具条统一由 `src/main/resources/js/emoji-groups.js` 渲染；文章页/聊天室 FTL 里旧的 `#uploadEmoji` 尾栏大多已注释，恢复“本地上传”优先改这里，不要分别恢复多套模板。
- 表情集分享链路使用新表 `emoji_share` 保存“分组快照 + 永久分享码”；分享内容是静态快照，后续源分组变更不会实时同步，导入会在目标用户下新建自定义分组并同步补入其“全部”分组。
