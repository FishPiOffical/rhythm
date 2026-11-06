# api_v3 分支旧入口行为调整全量清单

> 生成方式：对 `git diff master...api_v3` 全部 107 个被修改的旧 Java 文件 + 旧前端源码逐文件人工审查（8 个模块组），覆盖旧页面、旧 API、旧 WebSocket、旧客户端（APP/第三方）在并行期间会受到的全部影响。
> 用途：新旧 API 并行期间的回归测试基线。每条含证据（文件:行号）、兼容性结论与回归要点。
> 统计：**行为变化 86 条**（其中破坏性 18 条、需回归 41 条、向后兼容加固 27 条）；纯重构行为不变 50+ 条（文末仅列清单）。

---

## 一、破坏性变化（18 条，并行期间必须重点关注）

### 1.1 账号与凭据（3 条）

| # | 入口 | 变化 | 证据 |
|---|------|------|------|
| B1 | `POST /reset-pwd` | 重置密码成功后 credential epoch+1，**该用户全部旧 Cookie、旧 apiKey、OpenID token 一次性失效**（旧版只失效含密码摘要的凭据，OpenID refresh 不受影响）。注意：`/settings/password` 改密码**不**递增 epoch，两者行为分叉，需确认是否产品预期 | AccountPasswordResetService.java:41-56；AccountCredentialMgmtService.java:20；SettingsProcessor.java:1204 |
| B2 | `POST /register2` | **必须携带 6 位数字短信 code**（旧版只凭 userId 激活）；appRole 仅接受 0/1；password ≤32、referral ≤64。**旧 APP/第三方客户端不带 code 直接 POST 会失败**（报"请求字段不合法"）。旧库未过期注册短信（accountContext 为空）仍可消费。注：旧客户端因极验页面态集成本就无法使用旧注册 API，实际影响面有限；APP 注册应走 v3 两段式接口（见文末"v3 注册设计"） | LoginProcessor.java:192,635-655；AccountRegistrationService.java:38-61 |
| B3 | WebSocket 全频道 | 所有频道身份解析改走 `Sessions.currentSocketUser`（握手时捕获 epoch+密码摘要）：**用户改密/重置密码/登出后，旧长连接被主动关闭**（旧版继续推送）。涉及聊天室、私信、文章页评论推送、商店、五子棋、UserChannel。依赖前端自动重连 | Sessions.java:503,522；ChatroomChannel.java:520；ChatChannel.java:90；ArticleChannel.java:110-221；ShopChannel.java:60；GobangChannel.java:101 |

### 1.2 内容（7 条）

| # | 入口 | 变化 | 证据 |
|---|------|------|------|
| B4 | `POST /article/reward/{id}` | 付费正文可见性收紧：文章/用户失效、悬赏未开启、讨论区未受邀 → 报 transferFailLabel，**不再返回 SUCC+内容**（旧版失效场景也返回 SUCC+空内容） | ArticleProcessor.java:2222-2272；ArticleRewardMgmtService.java:42-85 |
| B5 | `POST /comment/{id}/remove` | 本人删评论**扣款顺序反转**：旧先扣 100 积分后查条件（有回复等时积分被吞）；新先查条件后扣款（失败不扣）。错误响应细化：404/403 替代旧的 ERR+消息 | CommentProcessor.java:255-300；CommentRemovalMgmtService.java:47-98 |
| B6 | `POST /comment/update` | 超 5 分钟编辑需扣费且**余额不足时编辑被拒绝**（旧版忽略转账失败，编辑照样成功） | CommentMgmtService.java:476-560 |
| B7 | `POST /article/remove` | 新增两条删除限制：**"摸鱼周报 "开头文章禁删；发布未满 24 小时禁删**。管理员强制删不受限 | ArticleMgmtService.java:267-340；ArticleRemovalPolicy.java:16-30 |
| B8 | `POST /article/update` | 新增硬校验：**锁定状态文章不可再编辑**（旧可）；**摸鱼周报标题锁死**；扣费失败 → 编辑整体回滚（旧：编辑成功仅转账失败） | ArticleMgmtService.java:911-1000 |
| B9 | `GET /article/{id}` 长文页 | 阅读记录**不再采集 ip/ua**（旧版记录真实 ip/ua 哈希）；阅读统计丢失该维度 | ArticleProcessor.java:459-465 |
| B10 | 长文阅读计分/奖励链路 | **整体改为"当前资格 + 6 小时窗口去重"新核心**：①注册用户去重从"每文每人终身一次"改为"每 6 小时一次"（**重读会重复发奖**）；②**匿名阅读彻底不计数**（旧按 ip+ua 哈希计数）；③**本人阅读不计数**；④新增资格前置校验（文章公开署名/showInList/非 sandbox/非 B3log 标签/可查看），不合资格静默不计。经典浏览量 articleViewCnt 不受影响照常 +1 | LongArticleReadCaptureService.java:65-127；ArticleProcessor.java:465 |

### 1.3 聊天/私信（3 条）

| # | 入口 | 变化 | 证据 |
|---|------|------|------|
| B11 | `POST /chat-room/red-packet/open` | **新核心心跳/猜拳红包必须显式风险确认**（riskAccepted=true，猜拳必须带 gesture 0-2），否则 422。官方 chat-room.js/chat-room-2.js 已同步适配（弹确认框）；**未更新的第三方旧客户端只发 {oId} 会报错无法领取**（旧红包不受影响；average/random/specify 非 risky 可直接拆） | ApiV3ChatRedPacketService.java:222-226；chat-room.js:2141+ |
| B12 | 同上（响应字段） | 新核心红包经旧入口的响应：**recivers 名单仅发送者可见**（旧：专属红包所有人可见）；info 缺 userAvatarURL20/210，userAvatarURL48 为原图；猜拳发送者查看自己红包旧返回详情、新抛 403（新前端自动 viewOnly 规避） | ApiV3ChatRedPacketService.java:229-237,303-304 |
| B13 | `GET /chat/revoke` 私信撤回 | 未读清理从"按 fromId+toId 清空会话全部未读"改为"**只删被撤回那条的未读记录**"（messageId 为新列，仅新消息有）。**升级前历史消息撤回不再清未读数** | ChatProcessor.java:145-168；PrivateMessageWithdrawalService.java:19-38 |

### 1.4 通知与隐私（3 条）

| # | 入口 | 变化 | 证据 |
|---|------|------|------|
| B14 | 评论通知接收者过滤 | 新增 canNotify 校验：**私密/仅作者可见评论不再向无权查看者发 at/reply/commented 通知**；参与者 @ 积分扣费只在发送成功时计（旧无论成败都扣） | CommentNotifier.java:138-147,274-394 |
| B15 | ArticleChannel 实时评论推送 | 推送 JSON：①**无权查看者的 commentContent 置为 ""**；②**匿名评论 commentAuthorId 置为 ""（旧版泄露真实作者 ID）**；③新增 commentType/commentParagraph* 六个字段；④文章可见性改按接收者动态判定（事件后权限变化立即生效）。**旧前端需验证 commentAuthorId="" 时头像/链接渲染不报错** | ArticleChannel.java:110-249；CommentRealtimeVisibility.java:11-28 |
| B16 | `CommentUpdateNotifier` | 编辑仅作者可见评论时，第三方被 @ 者不再收到通知（旧会泄露） | CommentUpdateNotifier.java:108-117 |

### 1.5 前端模板（2 条）

| # | 入口 | 变化 | 证据 |
|---|------|------|------|
| B17 | 思绪帖回放 | 模板改用 `thoughtPlaybackContent`（?js_string 转义）；**回放前必须动态加载 `https://file.fishpi.cn/vditor/latest/dist/js/lute/lute.min.js` 的 Lute.Sanitize，失败则思绪无法播放**（新增对 CDN 的运行时依赖） | article.ftl（PC/移动）；article.js/m-article.js |
| B18 | verify.js 消息渲染 | 服务端 msg 从 innerHTML 改为 text() 写入（防注入）。**若服务端 msg 含 HTML 标签将按纯文本显示**；新增 error 回调 + "/test 验证页"链接提示 | verify.js（PC/移动共用） |

---

## 二、需回归验证的行为变化（41 条）

### 2.1 账号/登录/安全（7 条）

| # | 入口 | 变化摘要 | 回归要点 |
|---|------|----------|----------|
| R1 | `POST /register`、`/register2`、`/reset-pwd` | **新挂 AnonymousViewCheckMidware**：shield 开启时匿名 IP 会被计数/跳验证码/封禁——对这三个入口是收紧 | 开启护盾后全新 IP 直接 POST 这三个入口的可达性 |
| R2 | `POST /register` | 邀请码改"预约制"：新增格式校验 `[A-Za-z0-9]{16}`，非法直接 422（旧版 500/忽略）；非 ServiceException 兜底为"注册暂不可用" | 非法/过期邀请码提示；shield 下匿名注册 |
| R3 | `POST /login` | 文案变化：锁 IP 提示、MFA 提示；mfaCode 非数字旧按 0 新直接报错；密码比对改 SQL 实查 | 密码错误 3 次锁 IP、MFA 开/关、封禁账号各走一遍 |
| R4 | `/settings/password、email、phone` | 方法本体未改；绑定短信限流改 AccountSmsQuota（预算不变） | 改密后 OpenID token 保留（确认是否预期，见 B1） |
| R5 | `/settings` 改名卡/免签卡/补签卡 | 原子化（失败不扣卡）；错误码细分；免签卡文案统一变化 | 无卡报错文案；同名并发改名只有一方成功 |
| R6 | `/settings/profiles、privacy、skin、function、locale、location` | 写路径整体换实现（updateLegacy），字段集逐一对应；**限流与 v3 共用预算（v3 高频写会挤占旧入口额度）**；失败提示变模糊 | 每个子页提交后核对字段生效；非法皮肤/外链 URL 拦截 |
| R7 | 验证码（verifycode）消费 | 新增 accountContext 绑定（注册码绑用户名/referral/inviteId）；消费改"同事务删除随提交生效"；**跨用途（注册码拿去重置密码）必须失败**；旧短信记录兼容 | 升级前已发未用短信升级后完成流程；跨用途拒绝 |

### 2.2 内容（8 条）

| # | 入口 | 变化摘要 | 回归要点 |
|---|------|----------|----------|
| R8 | `POST /article` 发帖 | 规则全部保留但**原子化**：TGIF 奖励 1000 分改为与发帖同事务（旧：发帖失败照发）；发帖扣费失败 → 整体回滚（旧：异步忽略）；宵禁判定改 SQL latestPublicArticleIdForUpdate；新增 anonymous 合法性与 qnaOfferPoint<0 校验 | 宵禁连发两帖、TGIF 发文得奖、扣费余额不足时文章不落库 |
| R9 | `POST /comment` 发评论 | 回帖自动关注移入服务层事务内（规则不变）；评论积分扣费失败 → 整体回滚；回复已删评论从 NPE 500 变友好错误 | 回帖自动 watch 生效；余额不足评论不落库 |
| R10 | `POST /vote/up`、`/vote/down` | 失败路径：目标不存在/异常旧返回 SUCC(code=0)、新返回 ERR；未登录 NPE→403；**重复投票通知去重从内存 HashSet 改 DB 持久去重（重启后不再重复通知）** | 重复点赞只通知一次（含重启后）；对不存在目标投票返回 ERR |
| R11 | 关注/取关（tag/user/article/watch） | **重复取关不再把计数减穿**（旧无条件递减）；新增提交后缓存立即失效 | 连续两次取关计数只减一次；关注后页面计数立即更新 |
| R12 | `/top/*` 榜单 | 逻辑移入 RankingQueryService；下游数据源故障时从"部分榜单"变"空榜单" | 小冰接口不可达时页面空榜不报错 |
| R13 | 段评迁移（编辑长文） | 强制事务检查；缓存激活时段评查询改 FOR UPDATE 原生 SQL；回写收窄为 5 列 | 旧入口编辑长文后存量段评按原规则重锚不错位 |
| R14 | 专栏重命名/按标题建专栏 | **软删同名专栏也判重**：rename 撞软删名报错；getOrCreateColumn 命中软删专栏从"自动新建"变报错；标题未变时不再刷新 updateTime；超长报错文案变化 | 存在软删同名专栏时的重命名/发文选专栏行为 |
| R15 | 阅读窗口计数竞争 | SETTLED 窗口并发阅读从"静默丢计数"变"显式 503"；结算计数解析从严（异常窗口整窗失败重试，旧按 0 结算）；结算新增 PointtransferRedc 账本索引写入 | 结算瞬间并发阅读无异常风暴；索引表无重复写入 |

### 2.3 聊天/私信（5 条）

| # | 入口 | 变化摘要 | 回归要点 |
|---|------|----------|----------|
| R16 | `POST /chat-room/red-packet/open`（旧红包） | **旧历史红包（无新核心记录）完全走原代码**；差异仅：风控配额与新核心共享（30分钟1次）；异常数据（durable_v3 标记但无记录）报"红包暂不可用" | 升级前发的各类旧红包在原入口拆开结果与 master 一致 |
| R17 | `DELETE /chat-room/revoke/{oId}` | ①每日计次从内存 Map 改 Cloud 表持久化（**重启不再清零、多实例共享**）；②归属判定从 userName 改 userOId（**改名后可撤回改名前消息**）；③撤回+扣费+计数同事务；④错误文案变化；⑤未登录从 NPE 变"请先登录" | 当日第 1/2/3 次撤回扣费 0/32/64 且重启后计次延续；管理员免费不变 |
| R18 | 红包删除守卫 | 未领完的新核心红包报"红包尚未全部领取，暂不能删除"；旧红包仍报"你想干嘛？"。净效果仍禁止删除，仅文案分场景 | 两类红包的撤回提示文案 |
| R19 | `[setdiscuss]` 话题 | ①校验失败不再白扣 16 分（旧先扣后验）；②话题持久化到 option 表（**重启后恢复**，旧重启丢失）；③discussChanged 广播新增 topicRevision 字段（旧前端忽略） | 空话题不扣分；重启后话题仍在；旧客户端正常渲染 |
| R20 | WS 频道连接管理 | 见 B3；另 ChatChannel.onMessage 新增 socket 用户 == httpSession.fromId 校验；UserChannel 按建连归属结算在线时长（修复漏结算） | 改密后旧连接断开可重连；在线时长归属正确 |

### 2.4 通知/事件（4 条）

| # | 入口 | 变化摘要 | 回归要点 |
|---|------|----------|----------|
| R21 | NotificationRepository 写路径 | add/update/remove **强制要求事务**（否则抛异常）；写后 touch v3 失效标记 | 全量走旧通知产生/已读/删除入口，确认无 "transaction required" 异常 |
| R22 | 通知级联删除 | 删评论/文章时通知清理改 removeContentReferences：**同 oId 文章/评论碰撞时 AT 类通知不再误删**；评论的 rewards/votes/reactions 现在一并删除（旧只删通知） | 管理员删评论后打赏/投票/reaction 无残留，通知清理正确 |
| R23 | 关注类通知写入 | 重复关注-取关-再关注**不再产生重复通知**（DB 判重 + FOR UPDATE） | 重复关注只一条新粉通知且刷新命令正常 |
| R24 | `POST /milestone` | 失败文案不再泄露内部异常（"提交失败，请稍后重试"） | 触发失败场景前端按 code=1 正常展示 |

### 2.5 积分/活动/云存档（7 条）

| # | 入口 | 变化摘要 | 回归要点 |
|---|------|----------|----------|
| R25 | 补签 patchCheckin | **失败不再吞卡**：先全部校验后扣卡（旧先删 patchStart+扣卡后校验，失败卡被吞）；接续条件在异常数据下可能不同 | 无卡/-2/-3 失败后卡数与 patchStart 不变 |
| R26 | `/api/cloud/sync`、`/api/cloud/get`（Evolve） | gameId=="evolve-save" 走新核心：Content-Type 必须 JSON、body≤4MB、**data 必须是可解析为 JSON 的字符串**、≤50万字符；写盲写（**不需要客户端带版本号**）；get 先读新表无则回退旧表（**超大/非 JSON 存量存档读取报错**）；其他 gameId 完全落回旧链路 | Evolve 旧客户端 sync/get 正常；非登记键不受影响；存量异常存档读取行为 |
| R27 | reaction 表态 | 对不存在目标表态从**静默成功变报错**；新增锁与去重；chat 目标追加实时事件 | 帖子/评论/聊天 toggle 不变；不存在目标报错符合预期 |
| R28 | 复读机点赞 | 原子化+用户状态校验+重复点赞行清理；likeCount 改 COUNT(*) 重算（int→long）；已开事务的调用方直接报错 | 重复点赞行清理后计数正确；封禁用户报错 |
| R29 | 鱼游投稿/编辑/审核 | 新增 OAuth 网址字段流转：**旧入口 requestEdit 会把 PENDING_OAUTH_LOGIN_URL 置 ""，审核通过后 OAUTH_LOGIN_URL 被清空**（仅影响混用 v3 设置过 OAuth 网址的游戏）；update 改按列部分更新 | 纯旧流程无感；混用游戏批准旧编辑后 OAuth 网址被清空 |
| R30 | 邀请码过期清理（Cron） | 已绑定用户的预留邀请码有 1 天宽限期不再被误删；异常降级 warn 不再抛错 | 过期未用仍被清理；预留中 1 天内不删 |
| R31 | 文章内链转标题 | 修复标题含 `$`/`\` 时替换错位/异常（quoteReplacement） | 标题含 $ 的文章内链渲染正常 |

### 2.6 Repository 底层（6 条）

| # | 入口 | 变化摘要 | 回归要点 |
|---|------|----------|----------|
| R32 | ArticleRepository 写 | add/update/remove **强制事务**；每次写多一条 revision 写 | 旧发文/改文/删文/浏览计数/置顶路径无 "transaction required" 异常 |
| R33 | CommentRepository 写 | 同上（add/update/remove 强制事务 + 文章 revision 触碰） | 旧评论发布/编辑/删除路径无异常 |
| R34 | publication scope 缓存 | scope 激活时（发文等流程）get 改 FOR UPDATE 绕过缓存，缓存失效延迟到提交后；未激活时与 master 一致（涉及 Article/Tag/Option/User 四个 Repository） | 发文流程提交前读取与提交后缓存重建正确 |
| R35 | RewardRepository.removeByDataId | 签名改 (dataId,type)：强制事务+类型白名单+FOR UPDATE（旧无类型无锁删全部） | 删文章/评论后各类打赏无残留 |
| R36 | VoteRepository.removeByDataId | 同上模式 | 删文章/评论后投票无残留 |
| R37 | 结算奖励发放 | 转账新增 PointtransferRedc 索引写入；计数从严（见 R15） | 结算后索引表有 SYS 与作者两条记录 |

### 2.7 前端（4 条）

| # | 入口 | 变化摘要 | 回归要点 |
|---|------|----------|----------|
| R38 | 红包前端（chat-room.js/chat-room-2.js） | durable 红包风险确认弹窗、viewOnly 只读、pendingProof 断网恢复；**PC/移动已同步** | 心跳须确认后扣分、猜拳必须先出拳、断网重开恢复、领完只读；普通红包不受影响 |
| R39 | channel.js/channel-2.js | 弹幕/话题 HTML 转义；discussChanged 带 topicRevision 时校验单调递增（旧事件直接丢弃）；红包抢完即时只读 | 话题频繁编辑无重复/乱序通知 |
| R40 | 音乐/天气卡片渲染 | 卡片字段转义、封面 URL 白名单、非数字 source 仅接受 https 用 `<audio>` 播放 | 历史网易云音乐消息仍可播放；特殊字符不破版 |
| R41 | register2 前端 + 大事记模板 | register2 新增隐藏 code 域（旧缓存页面无该域时自动省略）；大事记时间轴/后台全面补 ?html 转义（XSS 修复，历史含 HTML 的数据将显示为纯文本） | 无 code 访问第二步不报 400；大事记页正常渲染 |

---

## 三、向后兼容的行为变化（27 条，加固/修复类，知悉即可）

### 账号安全
- **S1 旧 Cookie/apiKey 兼容**：旧 224 字符 Cookie、192 字符 apiKey 继续接受（长度校验放宽 128..1024）；未迁移 epoch0 账号旧凭据照常有效；每次请求实时 SQL 校验（改密即时生效）〔Sessions.java:447；ApiProcessor.java:368〕
- **S2 OpenID token**：存量 refresh token 在 epoch0 期间可继续刷新；授权页要求有效 credential proof；授权中途改密 → 授权失败〔OpenIdProcessor.java:247,304；OpenIdRefreshTokenMgmtService.java:86,104〕
- **S3 OpenID access token**：新增 credentialEpoch claim；存量 token 不失效，凭据变更后即时失效（新增安全语义）〔OpenIdUtil.java:81-126〕
- **S4 验证码绑定收紧**：消费强制 userId+receiver+type+bizType+code+status+expired 全匹配 FOR UPDATE；旧短信记录兼容〔AccountVerificationService.java:31-56〕
- **S5 用户写并发收紧**：updateUser/addUser 改行锁；清理未验证用户改 FOR UPDATE〔UserMgmtService.java:295,387,746〕
- **S6 BeforeRequestHandler**：每请求开头建凭据快照；异常分支不再写 httpSession 用户态〔BeforeRequestHandler.java:82,161-164〕

### 内容
- **S7 文章打赏/感谢**：静默 no-op 语义保留为状态返回；匿名文章不转账只记 reward〔ArticleMgmtService.java:1277-1300〕
- **S8 采纳答案**：已采纳时幂等返回 SUCC 不重复转账；新增自采纳拒绝〔CommentProcessor.java:211-245〕
- **S9 关注目标不存在**：follow 不落数据（旧记日志返回），结果等价〔FollowMgmtService.java:206-265〕
- **S10 浏览量**：匿名/本人/不合资格访问 articleViewCnt 照常 +1（record 吞掉资格类异常）〔ArticleMgmtService.java:489-518〕

### 聊天
- **S11 发言校验/客户端标识**：规则逐项等价（间隔限频、4096 字、保留字）〔ChatMsgAddValidationMidware.java:52-74〕
- **S12 ChatRoomBot**：弹幕限流/风控二次确认/复读机处罚等价；边缘：单边带 redpacket 标记的畸形文本不再参与复读判定〔ChatRoomBot.java:91-660〕
- **S13 私信已读**：mark-as-read 强制叠加 owner 守卫，删除结果集相同〔ChatUnreadRepository.java:40-49〕
- **S14 私信列表**：getChatList SQL 参数化（结果相同）；发消息后列表缓存立即失效（旧依赖自然过期）〔ChatListService.java:46-58〕

### 通知
- **S15 未读数刷新命令**：refreshNotification 命令新增 count 字段（旧前端忽略）〔NotificationMgmtService.java:809-817〕
- **S16 新人晋升角色**：目标角色缺失时保留原角色+错误日志（旧写入悬空 ID）；正常数据行为相同〔ChangeRoleHandler.java:77-88〕

### 积分/活动
- **S17 每日签到**：原子化（失败不再半入账）；**跨月/跨年断签日期计算修复**（旧 int 算术会产生非法日期）；返回码/10 连签奖励不变〔ActivityMgmtService.java:286；CheckinMgmtService.java:58-98〕
- **S18 昨日活跃度奖励**：原子化判重，并发不可重复领取〔LivenessRewardMgmtService.java:39-68〕
- **S19 背包卡片**：原子化（并发不丢数量）；多条 bag 行合并去重；返回码不变〔CloudService.java:180-227；BagMgmtService.java:20-63〕
- **S20 开通会员**：新增账户对锁（避免与 v3 互等死锁），业务逻辑未动〔MembershipMgmtService.java:165-183〕
- **S21 鱼游投票**：重复投票行清理+计数口径修正；toggle 语义不变〔FishGameMgmtService.java:186-236〕
- **S22 鱼游评论**：校验移入事务+锁 game 行，规则与文案逐字相同〔FishGameMgmtService.java:238-258〕
- **S23 撤回日志**：chat-revoke 日志只记 messageId 不再记消息正文（隐私收敛），且不再漏记〔LogsService.java:154-161〕
- **S24 邀请码购买**：无变化（余额不足仍可生成的既有特性明确保留）〔InvitecodeMgmtService〕

### 前端（安全加固）
- **S25 聊天室 XSS 修复**：弹幕/音乐卡片/天气卡片字段全面转义、封面 URL 白名单〔channel.js、chat-room.js、chat-room-2.js〕
- **S26 大事记 XSS 修复**：时间轴与后台模板全面 ?html（PC/移动已同步）〔milestones.ftl、admin/milestone*.ftl〕
- **S27 红包弹窗样式**：新增 `.dialog-panel--packet-risk` 作用域样式，不影响既有元素〔_red-packet-risk-dialog.scss〕

---

## 四、纯重构行为不变（50+ 条，抽查即可）

仅列清单，详细证据在各组审查底稿：

- **A组**：/logout；用户名注册校验（UserNameRules 等价抽取）；短信/邮件发送基础设施（日志脱敏、契约不变）；UserMgmtService 注册默认值拼装/@提及解析抽取；OpenID refresh 属主校验
- **B组**：ArticlePostValidationMidware（308行→39行，规则逐条一致：免审/标题/类型/标签/正文长度/频率/敏感词，含沿用旧的 MAX_ARTICLE_CONTENT_LENGTH 比较 bug）；CommentAdd/UpdateValidationMidware（频率/保留词/敏感词等价）；采纳答案事务合并；感谢评论；思绪增删改；草稿箱；processArticleContent 默认路径（新增 thoughtPlaybackContent 增量字段）
- **C组**：/chat-room/send 信封用户解析；三个聊天 Repository 事务边界（addLegacy/saveLegacy/removeLegacy）；/chat/mark-as-read；has-unread/get-list 字段剔除
- **D组**：通知列表描述渲染提取（逐 case 一致）；MilestoneMgmtService 手动事务+白名单字段；MilestoneRepository/Cache；removeNotifications 委托
- **E组**：段评锚点生成规则**完全未改**（存量段评不会错位）；专栏目录分页重载（旧签名等价）；专栏排序锁序；删除专栏/章节号分配；专栏封面；结算窗口认领
- **F组**：积分转账底层（TRANSFER_LOCKS 抽取，key 规则逐字保留）；改名积分记录描述（历史数据渲染一致）；鱼游写限流（参数逐字相同）；RhypicProcessor 签名（36 行平移，claims 逐字一致）；NodeUtil；Markdowns.toPrivateHTML（纯新增）
- **G组**：articleHotScore 生成列过滤未变；全部 Repository 新增 v3 锁读/投影方法（既有方法未改）；Tag.normalizeAlias 等价抽取；ChatRedPacket 为全新模型文件不影响旧红包；Verifycode 新增常量
- **H组**：oauth-consent.ftl/scss 纯新增 v3 用，旧页面不加载

---

## 五、并行期间回归测试总清单（按优先级）

### P0 — 会破坏现有用户/客户端的（先验）
1. ~~旧 APP/第三方客户端 `POST /register2` 不带 code~~ → 已澄清：旧客户端因极验页面态集成本就用不了旧注册 API，无过渡负担；APP 走 v3 注册接口（见文末）
2. 重置密码后旧 Cookie/apiKey/OpenID token 全部 401（B1）；设置页改密后 OpenID token 保留（确认预期）
3. 第三方旧客户端拆新核心心跳/猜拳红包收到明确错误而非扣分（B11）
4. 旧前端 ArticleChannel 推送：匿名评论 commentAuthorId="" 时头像/链接不报错（B15）
5. 改密/登出后各 WS 频道旧连接断开且前端能自动重连（B3）
6. 长文阅读：匿名/本人不再计数发奖、6 小时后重读重复发奖——确认运营预期（B10）
7. 锁定文章编辑、24 小时内删文、摸鱼周报删文/改标题被拒（B7、B8）
8. 私信撤回一条后同会话其他未读保留（B13，与旧行为相反，确认产品预期）

### P1 — 语义变化需对照验证
9. 付费正文六类场景（未购买/已购/作者/锁定/悬赏关/讨论区未受邀）返回码与内容不泄露（B4）
10. 删除评论四类条件失败不扣款（B5）；余额不足编辑评论被拒（B6）
11. 发帖原子化：宵禁、TGIF 奖励、扣费失败回滚（R8）；评论扣费失败回滚（R9）
12. 聊天撤回计次重启延续、改名后撤回旧消息（R17）；话题重启恢复（R19）
13. 旧红包（升级前发的五类）原入口拆开结果与 master 一致（R16）
14. Evolve 旧客户端云存档 sync/get（R26）；非登记 gameId 不受影响
15. 补签/改名卡失败不扣卡（R25、R5）；签到跨月断签日期正确（S17）
16. 通知：私密评论不再通知无权者（B14）；重复投票通知重启后不重发（R10）；删评论级联清理无残留（R22）
17. 匿名 shield 开启时 /register、/reset-pwd 匿名可达性（R1）
18. 思绪帖回放（CDN 可用/不可用两种场景）（B17）

### P2 — 抽查
19. 重复取关计数（R11）；榜单下游故障空榜（R12）；专栏软删同名（R14）
20. 旧 Cookie/旧 apiKey 直接请求照常登录（S1）；升级前短信升级后可用（S4）
21. 签到/活跃度/背包并发原子性（S17-S19）；reaction 不存在目标报错（R27）
22. 各 settings 子页提交后字段生效（R6）；大事记/里程碑页渲染（R41）
23. 音乐/天气卡片、弹幕转义后正常渲染（R40、S25）

---

## 附：v3 注册/找回密码设计（APP 场景的答案）

**结论：v3 没有绕过极验，而是把极验从 Web 页面耦合中解耦成纯 API 契约。** 新旧共用同一套极验 4.0（旧页面用 gt4.js + 同一 `jiyan.id`，服务端共用 `CaptchaProcessor.jiyan` 二次验证）。

接口（全部匿名 + AnonymousViewCheckMidware）：

| 步骤 | 接口 | 说明 |
|------|------|------|
| 0 | `GET /api/v3/auth/captcha-options` | 下发 `{provider:"geetest4", captchaId}`（只暴露 widget ID，不暴露服务端 key） |
| 0 | `GET /api/v3/auth/registration-options?referral=` | 注册政策（开放模式/邀请码模式、推荐人校验） |
| 1 | `POST /api/v3/auth/registration/request` | `{userName,userPhone,captcha,invitecode,referral}` → 发短信码。captcha 为极验四要素 JSON（严格形状校验：恰好 lot_number/captcha_output/pass_token/gen_time 四键）；**三条注册路径全部强制极验，邀请码也不例外**；IP+用户名+手机号三维度 SMS 限流（AccountSmsQuota） |
| 2 | `POST /api/v3/auth/registration/complete` | `{userPhone,code,userPassword,userAppRole,mbti,referral}` 激活；IP 限流（RegistrationVerificationQuota） |
| 找回 | `POST /api/v3/auth/password-reset/request` → `/complete` | 同构两段式，PasswordResetQuota 限流 |

**对 APP 的意义**：旧注册 API 假定客户端在网页里跑 gt4.js（页面态），APP 无法复用；v3 下 APP 集成极验官方 4.0 Android/iOS SDK 拿同样四要素，直接调 JSON API 即可走通注册。

**待验证点**：极验 4.0 后台 captchaId 按平台建应用（Web/iOS/Android），需确认现有 `jiyan.id` 在极验后台已开通 APP 端（或需为 APP 单独建 ID、v3 captcha-options 是否需要按客户端类型下发不同 ID）。
