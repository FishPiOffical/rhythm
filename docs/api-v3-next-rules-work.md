# 下一批接口规则盘点（2026-10-22，只读）

本轮只读源码及现有契约；未修改实现、运行 Maven、访问实际 HTTP/数据库或生产。本文为内部开发记录，不应直接复制进公开 API.md。

## 已核实链路

### 邀请码购买

- `SettingsProcessor.register` 第270行：旧 POST `/point/buy-invitecode` 挂 loginCheck、CSRF。
- `SettingsProcessor.pointBuy` 第1381–1420行：仅 `allowRegister=2`；先调用 `userGenInvitecode`，再 actor→sys 扣购买费用。第1392–1394行**明确把余额不足仍生成邀请码视为刻意保留的业务特性**，不是可直接忽略的注释。
- `InvitecodeMgmtService.userGenInvitecode` 第76–105行：独立事务写16位随机字母数字码、generatorId、unused、空使用人/使用时间；失败返回 null。不能在新的外层购买事务内直接调用。
- `Pointtransfer.TRANSFER_SUM_C_BUY_INVITECODE` 第333行复用 `Symphonys.POINT_BUY_INVITECODE`；`symphony.properties` 第126行当前100分，第228行有效期259200000毫秒（3天）。生成时间隐含在对象ID，不能从未写入的 createdAt 猜测。
- 旧失败响应不返回已生成码，成功把码与到期提示合在 msg。现有 v3 `ApiV3InvitationQueryService.status` 第24–33行只投影已有状态，不列出码、生成者或预留信息。
- v3 新购买返回的码是可用凭据，需仅本人返回并支持本人幂等重放；严禁加入日志、公开列表/更新通知或通用错误正文。购买所需幂等回执可能保存码，属于必须明确限制读者的内部存储，不等于可公开查询。

### 积分转账

- `SettingsProcessor.register` 第272行：旧 POST `/point/transfer` 挂 loginCheck 和 `PointTransferValidationMidware`，无独立 CSRF。
- `PointTransferValidationMidware.handle` 第69–150行：接收人名称非空且排除机器人名、必须当前有效；发送人当前有效；普通用户禁止转自己，金额至少2且余额扣**请求总金额**后非负；用户名恰为 admin 绕过上述金额/余额/自转限制。备注 trim、最多128个UTF-16单位，再 `Jsoup.clean(..., Safelist.none())`；没有更小日限额或业务最大金额，入参是 int。
- `SettingsProcessor.pointTransfer` 第1259–1329行：请求 amount **含手续费**，普通费为 amount<100时1分，否则 floor(amount×1%)；接收人得 amount-fee，手续费另转用户名 admin 的账号。有效会员查询见 `MembershipQueryService.getStatusByUserId` 第89–111行，旧判断是 state!=0 且 lvCode contains("VIP4") 免手续费。发送者名为 admin 时 fromId 改 sys。主转账、手续费转账与通知是分开的提交，甚至主转账失败后仍尝试手续费。
- 原子积分primitive可复用 `PointtransferMgmtService.executeWithTransferLocks` 第69–71行及 `transferInCurrentTransaction` 第204–267行。**必须事务前预取所有账户对**，actor→receiver 与 actor→feeRecipient；admin若保留sys代发需另算实际from。primitive会捕获异常返回null，调用方必须将null视为失败回滚整个严格根，且 indexAsync=false；提交后再修复索引/刷新缓存。禁止误以为调用primitive就自动实现整体原子。
- 新 v3 的自身身份只能取Bearer上下文，不能复制旧 Sessions/ApiProcessor 二次身份替换；接收者只暴露公开ID/名称，完整账户对象及内部账本来源字段不外传。

### 举报

- `Router` 第193行：旧 POST `/report` 仅 loginCheck；`ReportProcessor.report` 第72–108行直接取 targetId/type/reason、trim备注后交service，无目标存在性、枚举、长度、重复或本人限制。
- `ReportMgmtService.addReport` 第136–150行只有 @Transactional、设置 handled=0、repository.add；无查重或幂等核心，不能复用为严格根primitive。`report` 本身无 owner+target 唯一约束（现有Repository仅普通增删查）。
- `Report` 第83–161行：目标0文章、1评论、2用户、3聊天室；内容理由0–4和49，用户理由5–7和49。旧PC文章模板 `skins/classic/pc/article.ftl` 第639–651行体现内容理由，用户模板 `.../home/home-side.ftl` 第295–307行体现用户理由；两处文本框无maxlength。`chat-room.js` 第1758–1770行和另一套脚本采用聊天室目标3、reason49、空备注。
- `repository.json` 第512行 reportMemo 为String(10240)。这是存储上限，不能据此自动决定HTML/Markdown业务策略或把旧任意正文当成安全返回值；后台 `skins/classic/*/admin/reports.ftl` 第54行直接展示 reportMemo，必须评估编码边界。
- 需逐目标复核当前可见性：文章/评论走当前v3目标授权，私密评论只双方；聊天室目标必须当前存在；用户需定义有效状态及可否举报自己。不应把不存在或不可见ID写进举报表。分类信息GET可直接用现有模型与页面枚举，但不要擅自扩展聊天室理由。

### 文章与评论历史

- `ArticleProcessor.register` 第290–291行：旧列表与详情均 loginCheck + permission；`symphony.properties` 第268–270行分别要求 commonViewArticleHistory / commonViewCommentHistory。`PermissionMidware.check` 第98–126行按当前角色授予检查，不能把这个条件替换成单纯content.read。
- `ArticleProcessor.getArticleRevisionMetas/getArticleRevision` 第1221–1265行没有读取者权限判断，`ArticleRevisionQueryService` 第71–104、125–132行只要求目标文章存在和revision类型/目标匹配。不能据此声称所有文章读者原本就获准读历史，也不能照抄现有文章目标访问漏洞到v3。
- 文章旧轻列表包含 revisionId/time/index/current/**revisionAuthorId**（`ArticleRevisionQueryService` 第160–174行），详情仅投影title/content并clean（第204–220行）；匿名文章不能直接暴露作者ID。current是虚拟版本，只有有存储历史且忽略空白后不同才追加列表（第78–82行）。列表不等于完整编辑审计。
- `CommentProcessor.getCommentRevisions` 第310–323行先确认所属文章对当前viewer可读；`RevisionQueryService.getCommentRevisions` 第84–112行确认当前评论有效，当前私密评论仅评论作者/文章作者可读。随后返回完整revision外壳且读取全部历史（第115–153行），新v3必须重投影而非裸复用。
- 文章历史存储仅title/content（`ArticleMgmtService` 第838–847、1090–1100行），**没有当时可见性/匿名状态快照**；评论历史也仅content（`CommentMgmtService` 第428–430、521–523行）。因此无法证明某旧正文保存时是否私密，不应承诺可按历史可见性精确放行。
- 历史文章正文与评论正文旧都用Markdowns.clean处理；新接口需要明确返回源码还是清洗内容，避免向第三方开发者谎称原始Markdown。不能允许通过历史current绕过当前收费/奖励/讨论/私密正文限制。

## 建议向用户一次确认的最少四组问题

1. **邀请码购买资格与原子性（用户已确认并实现）**：仅当前有效用户、邀请注册模式、余额足够才能支付现有100分并生成一枚3天邀请码；失败不生成也不扣分；返回code/expiresAt/paidPoints，同键恢复原码。重放仅重验当前有效actor，模式关闭、原码已用/过期/被清理不抹掉原购买回执。旧“余额不足仍造码”入口保持。见[购买实现记录](api-v3-invitation-purchase-work.md)，禁止重复询问。
2. **转账沿用规则及特例**：v3 是否沿用请求总额含手续费、普通至少2、备注trim/去HTML且128UTF-16、有效他人及排除机器人；精确有效VIP4_YEAR/VIP4_MONTH免手续费，其余1%向下取整且至少1；禁止所有自转、取消用户名admin的sys代发特例；主款/手续费/通知在同一严格事务，失败无扣款？建议采用。不要额外发明业务日限额；int与余额溢出作为技术校验拒绝即可。旧入口是否同步原子化需单独明确，当前先保持。
3. **历史版本范围/内容**：是否仅作者本人及有既有历史权限的当前合法读者可读，且始终满足当前文章/评论权限；匿名来源不返真实作者ID；列表分页、详情返回清洗后的标题/正文，current也受当前正文购买规则保护？若担心已删除敏感文字，替代方案为仅作者本人可读正文，读者只给轻列表（或全部历史仅本人），应由用户选定，不能凭旧文章漏洞决定。评论私密维持仅双方，管理员是否增加例外需明确，默认不扩大。
4. **举报文本和重复**：是否仅现有四类目标、理由沿现有内容/用户分组，聊天室仅49；备注trim后纯文本、最多10240UTF-16、可空；仅举报当前可读有效他人目标，禁止自举报；同一举报者+目标已有未处理举报时返回原举报成功（不重复新增），已处理后可再报，幂等重试不新增？建议采用。普通HTML清除与重复语义是新契约，需要确认；更短备注上限不能自创。

四组规则现均已获用户确认：转账包含手续费且精确有效VIP4/实际手续费收款人发起免手续费；举报当前可读有效他人/pending复用；历史作者本人免历史角色要求，其余当前合法读者仍需既有历史权限。旧入口仅复读机点赞共用原子核心获额外批准，旧转账/举报/历史保持。后续不要重复询问已确认规则。

## 无需新增业务确认可先实现的读取能力

- 邀请码购买选项GET：当前注册模式是否允许购买、现有单价与配置有效期，仅展示当前状态，不保证下一笔购买成功。不返回任何已发行码、owner或预留人。
- 举报分类GET：按既有页面可提供文章/评论的0–4/49、用户5–7/49、聊天室49，以及本地化标签；无目标数据、无需新增业务规则。
- 历史版本仅可先做内部白名单DTO/当前目标授权复用、只读SQL分页及隔离测试；用户已确认该历史范围，HTTP实现见`api-v3-revision-work.md`。不要把新接口路径和额外分页规则当成已获批准的旧兼容变更。
- 现有v3邀请码状态已完成，不应重复新增列表或猜测unused表示可注册。

## 建议分工和串行审阅点

- Agent A：邀请购买选项读取；规则确认后新增购买事务primitive，不调用独立提交userGenInvitecode。凭据返回/幂等保密专项。
- Agent B：举报分类读取；规则确认后四类目标当前授权与严格事务查重，专项防越权/存储XSS/并发重复。
- Agent C：历史版本内部DTO与绑定查询，确认读者契约后分文章/评论轻列表及按需正文；匿名、收费、讨论邀请失效、目标删除与history/current全链专项。
- Root：转账总额/手续费数值计划及当前会员查询、完整账户对锁与提交后索引的共享审阅；可随后单独委派实现，避免和邀请码同改PointtransferMgmtService。路由、API.md、UPDATE_LOG.md、AGENTS.md与进度计数统一由root收口。
- 不改旧兼容入口，除非用户明确批准；读取选项走现有适当Bearer scope和限额，不新增匿名裸路由。无新静态资源/上传下载路径；本盘点未执行黑名单、验证码、anonymous.viewSkips或浏览器实际回归。
