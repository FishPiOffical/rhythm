# 文章、长篇与专栏

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[评论与段评](comments-realtime.md)、[积分锁](points-notifications.md)、[页面与皮肤](frontend-games.md)。

## 可见性与详情投影

- v3 文章详情的 includeInteractions 仅为已认证读者生成互动状态，复用独立读接口的可见性判断；互动写回执的 interaction 用于更新当前状态，打赏正文仍由授权详情读取。
- v3详情commentable/statement由ApiV3ArticlePolicy严格投影，异常旧值为null；Latke按注册字段提前getBoolean，历史CHAR须经v3ContentCommentable别名原样读取，不能用SELECT *或数字强转把坏值当true；新评论/编辑在幂等回调内经getV3CommentPolicyForUpdate锁文章后按原始锁定值校验，成功原键不被当前关闭状态阻断。客户端未知/关闭禁新写但须保留普通与长篇抽屉的原命令恢复入口。
- v3问答详情的qna仅公开悬赏与采纳存在性，须随当前文章SQL快照查询；隐藏/匿名回答仍计已采纳，但不返回身份。v3采纳新键在锁定文章上要求type5且悬赏至少20，状态canAccept同步；旧入口与成功原键回放保持。
- v3论坛读取用ApiV3ContentSnapshots绑定当前文章/作者与当前读者；聊天室通知actor仅由合法userOId解析，无ID不按旧用户名关联。评论/段落/通知/表态不从实体缓存恢复资格或正文；内部作者别名只供白名单DTO投影，不能裸返。修订历史本来走当前SQL；勿改回ArticleRepository.get/UserQueryService作为权限依据。 内容型积分/文章公告说明也须用已授权当前标题，不能在校验后再调旧通知缓存描述生成器。 公开资料/列表隐私与清风明月作者由DiscoveryQueryService.currentProfileUser当前查询，不用getByName/get实体缓存授权。 列表作者随分页SQL投影，DTO未提供快照时只读当前公开作者；v3头像共用ApiV3AvatarUrl原URL投影，不能调用旧AvatarQuery显示变换丢掉签名query。
- `/api/v3` 文章/评论读取走 `ApiV3ContentProcessor` + `ApiV3ContentQueryService`，可选 Bearer 需 `content.read`；当前用户只从 `ApiV3Processor.IDENTITY` 获取，禁止回退 Sessions/apiKey 或复用依赖会话的页面内容组装。详情与评论共用文章可见性检查，匿名作者 DTO 不返回真实身份，打赏和私密评论单独授权。

## 发布、编辑与删除

- 文章内容校验共用 `ArticleValidation` + `ArticleValidationService`，旧 `ArticlePostValidationMidware` 只适配旧响应。v3 创建走 `ApiV3ArticleWriteProcessor` + `ApiV3ArticleMgmtService`，需要 content.write 与 commonAddArticle（不是管理权限articleAddArticle）；输入只认Bearer作者、幂等与本人草稿同事务，结果只返回id/permalink。v3不接受免审字段，思绪POST仍关闭；旧免审角色、额度例外与历史打赏长度规则保留，避免悄悄收紧旧API。
- 用户投稿必须走 `ArticleSubmissionMgmtService#submitInCurrentTransaction`，手机/新人/宵禁/周报策略共用 `ArticleSubmissionPolicy`，周报奖励与底层正文/章节/版本/职业来源/扣款同事务。外层先经 `withPublicationLock`（服务锁→账户对锁→事务），用 `ArticlePublicationCaches` 暂缓缓存，仅确认新提交后调用 `afterArticlePublished`；v3按幂等applied判断，不能在重放上发事件。旧入口自带提交，v3不得直接套旧方法。`ArticleWriteQuota`与旧校验共用额度；频控包装需同步调用，底层SimpleCurrentLimiter的get/put不原子。
- 文章编辑核心是 `ArticleMgmtService#updateArticleInCurrentTransaction`，正文/标签/章节/段评位置/版本与扣款同事务；仍用发布锁和缓存边界，提交后才调 `afterArticleEdited`。`ArticleEditPoints` 保留五分钟内免编辑费但仍需余额门槛、匿名免扣、新启用打赏固定费；旧实现rewardPoint=0不会关闭已启用打赏，不要暗改。段评迁移只写位置字段，文章和评论缓存也由该边界延后处理。v3 PUT 经 `ApiV3ArticleEditing` 在锁定本人文章后合并部分字段、校验并幂等提交；重放哈希只包含提交字段，不含当前数据库默认值。
- v3 编辑源码走 `GET /me/articles/{articleId}/source`（content.read）与 `ApiV3ArticleEditorDto`：owner+ID联合过滤、字段投影，单次查询仅关联本人有效专栏；不复用公开详情或页面转义。正文/思绪/打赏正文保留原始串，锁定文章可由本人读取，但不代表具备写权限；响应只读字段不能原样回传PUT。
- 文章删除共用 `ArticleMgmtService#removeArticleInCurrentTransaction` 与 `ArticleRemovalPolicy`，统计/作者/文章依序锁定；数据库清理和职业撤销同事务，提交后才执行 `afterArticleRemoved` 的索引/音频清理。v3 DELETE 先按 Bearer 检查本人和权限，再幂等提交；重放不重复清理。普通删除保留24小时/周报/互动限制，打赏计数查询失败不得当成0放行。投票/奖励清理必须同时匹配目标ID与类型，禁止恢复仅按dataId删除；文章奖励含购买、感谢、采纳三类，不包含评论感谢或用户奖励。
- 思绪页面回放由 `ArticleQueryService` 的页面专用回调提供原始记录，PC/移动 `article.ftl` 用 `?js_string` 编码，`article.js`/`m-article.js` 用 Lute 清洗每帧重建 HTML；不要先清洗整段结构记录而破坏坐标。旧 API 字段保持原转义，自定义文章模板覆盖时需同步此链路。
- `symphony_article.articleHotScore` 是数据库生成列，只用于查询排序；文章写入统一经 `ArticleRepository` 过滤该字段，业务代码不要把它作为普通字段增改。

## 文章互动

- 文章投票复用 `VoteMgmtService#setInCurrentTransaction`，旧toggle自行提交，v3 `/articles/{id}/vote` 经 `ApiV3ArticleVoteService` 加幂等事务（GET content.read，写 content.write）。文章计数、`tag_article`的redditScore及点赞通知同事务，关联只更新分数，保留原整数时间排名规则；刷新/活跃度在提交后。文章/评论共用 `ApiV3Votes` 输入及DTO，但查询、mutation和幂等operation均按资源类型隔离。
- 文章感谢/打赏共用 `ArticleRewardMgmtService`，先取得actor/sys与actor/作者完整账户对，再同事务转账、计数、reward及通知；匿名免转账、感谢也计入原LIVENESS_REWARD。v3 `/articles/{id}/thanks`、`/reward`提供GET状态与幂等POST，正文始终经文章详情按当前读者读取，不能存进幂等结果。锁定但可读文章仍可投票/感谢/打赏。旧`/article/reward`已获用户批准收紧正文返回：通过`rewardForViewer`在扣款前校验显式读者，提交后重查读者/文章及购买资格，仅合法启用打赏返回原HTML；不得退回Sessions身份或只凭无操作结果放行。

## 长篇、阅读与专栏

- 长篇段落双视图须只改变受限链接的标签，链接目标/属性保持一致；ShortLinkQueryService.linkArticleWithUrlTitles字面绑定配置来源并保留原URL为目标/安全标签，缺失标题仍不展开；旧Function入口保持原重写语义。正文/目标真正不对应继续409，不可忽略href差异放宽结构校验。段落测试用项目版本Flexmark，清洗/媒体仍为夹具，不能宣称完整渲染HTTP已验。
- 长篇阅读登记统一在LongArticleReadCaptureService：v3显式正文后POST双scope，旧Markdown/经典浏览量仅计当前有效登录他人、当前可列出署名有效长篇；排序当前用户锁→文章→claim/窗口同根或owner保存点。读者表已改article/user/window唯一，先停登记迁移20261030；Connector/J匹配未改行不代表计数成功，须锁读窗口OPEN。正常结算公式与历史匿名计数保留，不从SSR/预览或客户端发奖。结算先point pair→当前作者→文章/窗口，积分/历史/职业事件/两侧账本索引同严格根；负数或溢出回滚，缓存边界后刷新、通知提交后发、后台结束释放连接。
- v3 专栏管理走 `ApiV3ColumnProcessor`，与旧入口共用 `LongArticleColumnMgmtService` / `ColumnCoverMgmtService` 事务内方法；专栏元数据写入和章节归属校验共用父行锁，章节统计使用当前读，避免删除与新增章节竞争。排序允许只提交部分专栏，未提交项保持原排序。
- 专栏封面链路：封面字段是 `long_article_column.columnCoverURL`；默认封面在 `LongArticleColumnQueryService` 填充；作者管理页为 `/column/manage`，编辑长篇页的弹窗脚本是 `long-article-cover-dialog.js`，新建长篇封面随 `/article` 请求的 `columnCoverURL` 写入；封面保存接口为 `POST /api/columns/{columnId}/cover`，处理器是 `LongArticleColumnProcessor`。
- 长文阅读页以 `articleType=6` 区分，PC/移动模板均为 `skins/classic/*/article.ftl`，样式在 `index.scss`/`mobile-base.scss`，交互在 `long-article.js`/`m-long-article.js`；模板通过 `hidePageChrome` 不渲染顶部与底部视觉内容，但 footer 的公共脚本仍需加载；桌面宽度和两端字号统一保存在 `localStorage.longArticleSettings`，宽度仅在 PC 生效；PC 评论按钮控制居中的“正文 + 评论”阅读舞台，移动端使用右侧抽屉，现有评论列表、筛选、分页和编辑器均复用；专栏目录直接复用阅读页数据模型中的 `longArticleChapters`，PC/移动均需同步目录模板、样式与工具栏脚本。
- 管理员文章页编辑长文专栏时，`admin/article.ftl` 会随文章表单提交 `columnCoverURL`，后端在 `ArticleMgmtService` 的文章事务内复用 `ColumnCoverMgmtService` 校验并更新或清空专栏封面。

## 历史、草稿与文章导航

- v3历史读取在`ApiV3RevisionQueryService`：本人或当前精确历史权限的合法读者，私密仍限双方；只投影清洗正文，不返编辑者ID，current仅已有历史且原文忽略空白不同，评论current.time=null。
- RSS 订阅链路在 `FeedProcessor` + `model/feed/RSS*` 手写 XML；输出前必须保证 XML 1.0 字符合法并处理 CDATA 结束符，思绪正文不要作为普通 `articleContent` 放进 RSS description。
- 文章历史版本链路：前端通过 `/article/{id}/revisions/list` 获取轻量版本列表，再按需调用 `/article/{id}/revisions/{revisionId}` 获取单个版本正文；旧 `/article/{id}/revisions` 已删除；两个新接口都挂 `loginCheck` + `permissionMidware` 并兼容 `apiKey`。
- 文章上下篇链路：`ArticleProcessor` 统一填充时间、作者、热门三组导航数据及小窗口列表；PC/移动模板共用 `skins/classic/*/common/article-adjacent-nav.ftl`；“全部文章”排序选择由 `article.js`/`m-article.js` 写入 `localStorage.articleAdjacentSort`。
- 发帖草稿箱链路：前端入口在 classic PC/移动 `home/post.ftl`、`home/long-article-post.ftl` + `add-article.js`，后端为 `ArticleDraftProcessor` + `ArticleDraftMgmtService` + `article_draft`；接口 `/api/article-drafts*` 挂 `loginCheck` 并支持 `apiKey`，不挂 CSRF；普通文章与长文章共用同一组草稿接口，长文章通过 `articleType=6` 和 `columnId/columnTitle/chapterNo` 区分；思绪帖草稿必须同时保存编辑器正文与 `articleDraftThoughtContent`。
- `/recent` 与 `/api/articles/recent*` 共用 `ArticleQueryService#getRecentArticles`；该链路已明确排除长篇（`articleType=6`），长篇列表请走 `/recent/long` 页面或 `GET /api/articles/recent/long`。
