# 评论、段评与内容实时事件

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[文章授权](articles.md)、[实时票据](chat-online.md)、[积分锁](points-notifications.md)。

## 表态与删除清理

- v3 reaction 入口在 `ApiV3ReactionProcessor`，文章/评论/聊天室均支持 GET/PUT/DELETE；PUT 是设置而非 toggle，与旧入口共用 `ReactionMutation` 和加锁写入。目标可见性由 `ApiV3ReactionTargetService` 校验，论坛实时事件逐接收者校验，提交后才发布；不要直接复用未过滤接收者的旧广播。
- 评论与聊天室 emoji reaction 共用 `reaction` 表；历史接口直接补 `reactionSummary/currentUserReaction`，聊天室实时选中态需结合 `chatReaction` 增量事件里的 `actorUserId/actorReaction` 在前端按当前用户合并。
- 内容删除统一经`ReactionRepository#removeTargetInCurrentTransaction`按类型+ID清理reaction，调用方须先锁目标并在同事务提交；文章、管理员评论级联及本人评论删除均已接入，禁止只按ID清理。

## 评论定位与段评

- v3评论定位在ApiV3ParagraphQueryService.location，按与普通/段评oldest相同筛选SQL计当前位置，每页20；段评沿现有别名分组和active/orphaned，不能用旧缓存、创建时间或扫描客户端分页猜位置。只返ID/分页/分组，不给私密正文或匿名身份；客户端目标须绑定当前文章/读者，真实当前页挂载后再滚动，位置变化显式重查。
- v3 长篇段落/段评和评论详情走 `ApiV3ParagraphProcessor` + `ApiV3ParagraphQueryService`，挂可选content.read与匿名校验。`LongArticleParagraphService.compatibleMarkdown` 用同次标题快照配对旧标识与公开安全视图，只返回安全正文/摘要；别名计数/筛选共用SQL条件，冲突409而不猜归属。历史快照不输出，v3回复也不复制；v3文章编辑在原事务中迁移临时锚点并检查冲突，旧编辑同步拦截仍待用户确认。匿名实时作者ID已获用户同意置空，须在权限/模板处理后脱敏。
- 长文段评链路：`LongArticleParagraphService` 根据服务端清洗后的正文生成段落锚点并给 HTML 添加 `data-long-paragraph-*`；段评字段存于 `comment`（`commentType=1`），新增接口位于 `CommentProcessor` 的 `/comment/paragraph*`；评论编辑事务调用段落迁移，无法匹配的段评标记为失效但保留快照。PC/移动均加载 `long-article-paragraph.js`，段评列表复用现有评论线程与编辑器能力。

## 评论写入与互动

- 本人评论删除共用 `CommentRemovalMgmtService`：旧入口自带事务，v3 DELETE经comments.write和幂等事务；发布锁/账户对在事务前取得，相关用户按ID排序锁定后才锁文章/评论，扣费、计数、职业来源及清理同事务，索引/通知刷新/审计在提交后。用户已同意旧入口失败不扣费；真实MySQL与后台强删竞争仍待验证。
- 评论发布/编辑延迟提醒统一走 `CommentNotificationMgmtService`，当前文章/评论锁内检查接收者可读性并保存通知，提交后刷新；不要直接用文章也在使用的旧 `addAtNotification` 写评论提醒。`@participants`仅按新建的提醒计数，收费仍沿用事件内独立转账；通知事务不等于整个异步事件事务。
- 评论投票走 `CommentVoteMgmtService`，先排序锁用户再锁文章/评论，vote行、计数和点赞通知同事务，提交后刷新/记活跃度；旧控制器保留toggle及type=操作前投票。v3 `/comments/{id}/vote` 由 `ApiV3CommentVoteService` 提供GET(content.read)/PUT/DELETE(comments.write)，PUT是设置，幂等结果不覆盖后续投票；GET投影同时按actor及dataType筛选，只返id/vote/计数。`VoteMgmtService`只允许文章，禁止套其事务包装写评论。
- 评论采纳共用 `CommentAcceptMgmtService`：显式传入当前操作者，事务内复核文章作者，并用文章行锁串行不同回答的采纳；转账/标记/reward/通知同事务，提交后索引/刷新/活跃度。旧入口已有其他采纳时保持无操作成功，v3返回409；同一目标重试不重复付款。旧Cookie入口的CSRF保留，v3走comments.write及Bearer可读性，不新增文章类型或匿名收费限制。
- 评论感谢共用 `CommentThankMgmtService`：先投影接收者并预锁actor/sys及actor/接收者账户对，事务内按当前评论重验归属、查重；转账/感谢计数/reward/通知同事务，索引/刷新/活跃度在提交后。v3 POST `/comments/{id}/thanks` 用comments.write、显式Bearer可读性与幂等事务；旧缺失/失效目标仍保持无操作。投票和采纳只更新所属计数字段，不能把旧评论整行快照覆盖其他互动。
- 评论发布核心为 `CommentMgmtService#addCommentInCurrentTransaction`，旧入口自带事务，v3须复用核心而非套旧方法。先按可信文章作者取得actor/sys和actor/作者全部账户对，事务中复核作者未变；正文/计数/版本/职业来源/回复已读/自动订阅/扣款同事务，计数写列白名单。回复已读经 `NotificationStateMgmtService#markDataReadInCurrentTransaction` 固定截止批量写，`afterCommentPublished` 提交后才刷新带未读数的消息、索引和广播；自动订阅不再由控制器二次写入。
- 评论编辑共用 `CommentMgmtService#updateCommentInCurrentTransaction` + `CommentEditing`，沿用文章发布锁/缓存边界，正文、版本和费用同事务；只更新正文/可见性/IP/UA，禁止整行旧快照覆盖计数或段评元数据。`afterCommentEdited` 只在提交后索引/发事件；v3 PUT 需comments.write、本人及目标文章可见性，幂等重放不发事件。评论/文章任一匿名免编辑费；普通评论超过五分钟即使正文不变仍收费，版本仅非空白变化时新增。
- v3 评论发布走 `ApiV3CommentProcessor` + `ApiV3CommentMgmtService`：文章作者付款目标须在事务前投影并预锁，锁内重验；`ApiV3CommentTarget` 校验同文章可读回复与服务端段落快照，幂等回放不再推送。`ArticleChannel` 按当前用户/文章/评论可见范围生成接收者独立数据；私密正文在模板渲染前清空，不能只依赖模板隐藏。评论提醒同样按当前可读范围过滤。
- 评论发布/编辑正文校验共用 `CommentValidation` + `CommentValidationService`，分prepare（额度/保留词/长度）和moderate以保留旧目标校验先后顺序；额度经 `CommentWriteQuota` 同步共用，保留原角色例外。旧CommentProcessor写入从loginCheck的User.USER取身份，query/body apiKey一致；v3不得调用依赖Sessions的旧中间件，须显式传Bearer用户。

## 页面渲染与实时事件

- 移动端文章页（`skins/classic/mobile/article.ftl`）存在多个 `#replyUseName`（含隐藏占位 `.fn-none`）；`m-article.js` 处理回复目标时需优先选中非 `.fn-none` 节点，避免“回复对象已记录但指示未显示”。
- 评论区交互不是单点模板：首屏评论列表由 `ArticleProcessor` + `CommentQueryService#getArticleComments` 组装，展开“原评论/回复”走 `CommentProcessor#getOriginalComment/getReplies`，实时新评论卡片由 `processor/channel/ArticleChannel` 渲染 `skins/**/common/comment.ftl`；改评论动作区时需同步评估 PC/移动模板、`article.js`/`m-article.js` 以及实时插入链路。
- 文章页实时链路走 `ArticleChannel`：新增评论仍是 `type=comment`，评论 reaction 增量走 `type=commentReaction`，帖子本体 reaction 增量走 `type=articleReaction`；联调这类 Java 推送改动时，前端强刷还不够，必须让用户重启或重新编译服务端。
