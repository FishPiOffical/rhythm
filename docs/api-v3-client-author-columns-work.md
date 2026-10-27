# 长文编辑器本人专栏选择（第六十六轮，2026-10-26）

## 实际修改文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-author-columns.ts`：消费既有GET `/api/v3/me/columns?p&size=20`，投影id/title/articleCount；严格页码、页数/总量/当前页长度、ID/重复/字段边界，no-store及AbortSignal。不拿公开专栏或首页列表冒充本人目录。
- `src/components/posts/AuthorColumnPicker.vue`：新独立选择器，当前认证本人分页读取、失败重试、上一页/下一页、真实空结果，支持选择列表中的专栏。未在当前页的已有绑定单独标明，不制造其当前有效性；查询或翻页失败保留绑定，只有明确选择才emit id/title，不自动取第一项或清空章节。身份版本/owner/令牌存在性隔离旧结果，普通token轮换保留；卸载/退出取消，SSR不读取/输出本人绑定。样式为SCSS并build，不加卡片容器。
- `src/views/PostView.vue`：长文“已有专栏”改用选择器，去手填ID；选择同时更新id和对应title，章节/封面保持原字段，独立长文与新建专栏入口保持。可用于发布、编辑和草稿；绑定合法性仍由提交服务当前重验。
- `tools/v3-author-columns-test.mjs`：7组生产API与完整Vue选择器/Pinia/轻量DOM专项，包括父组件回填、分页失败恢复、禁用/加载、轮换/退出、SSR/真实空、接线检查。
- `tools/v3-posts-test.mjs`：原27组保留，新增第28组完整PostView+生产写适配器+选择器事件适配，确认更改现有文章关系只发columnId/chapterNo且不改cover，草稿保存匹配的id/title/chapter。选择器自身逻辑另由完整组件专项覆盖。
- `AGENTS.md`、`docs/V3_MIGRATION.md`及生成dist/client/server。

后端只更新`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`AGENTS.md`与本记录；既有接口契约、Java/FTL/SQL/Repository均未改，注册总283保持，API.md已有本人专栏分页契约。本轮无需为Java重新编译，无Maven/APP/提交/推送。

## 已执行验证

- 7组专项：`/tmp/rhythm-v3-author-columns-tests.log`。
- 文章/草稿28组：`/tmp/rhythm-v3-author-columns-posts.log`，新增源关系选择→PUT部分字段及草稿写回完整链验证。
- 评论17、用户联想7、编辑器表情7回归：`/tmp/rhythm-v3-author-columns-v3-*-test.log`。
- 类型/client/server构建：`/tmp/rhythm-v3-author-columns-build.log`，成功。
- 生成JS版本/map检查：`/tmp/rhythm-v3-author-columns-js-audit.json`；两仓diff --check。
- IAB实际PostView+生产API适配器+真实CDN Vditor与合成HTTP：打开/post/long→已有专栏→选第一页103→第二页503，界面保留当前绑定并显示重试→重试第二页→选120/末页专栏→填写章节7→存草稿；合成后端收到 `{id:"120",title:"末页专栏",chapter:"7"}`，UI确认草稿500。没有真实存储或发布扣费。
- 390×844移动视口重新挂载，选101，原生select宽234、无横向越界；滚动后目视选择/分页/章节区域，截图`/tmp/rhythm-v3-author-columns-mobile-proof.png`。
- 真实Java服务目录/权限变化、数据库保存/回读、发布/编辑关系事务与费用、真实多账号切换仍需联调，不能拿合成回执当真实持久化证据。

## 安全与反漏扫

- 只消费现有content.read本人GET，不接受他人owner参数，不回落Cookie/apiKey或公开列表。父PostView的ready/身份边界负责清本人source，子组件清自己读取的列表与在途请求。
- 无新匿名路由/viewSkips，未动BeforeRequestHandler/test/validateCaptcha；实际黑名单、周期验证和开发关闭风控未运行验收。
- 列表标题由Vue文本显示，无HTML/远程内容抓取；不把coverURL/任意响应字段塞入提交，只传当前列表中明确选择的id/title。分页错误不当空列表；同页list/count在并发变更下不一致会显示失败供重试。
- 无新SQL/Repository写入、上传下载路径、CDN URL或可枚举目录；无新增SSRF/注入/CSRF状态变化。选择不是授权证明，发布/编辑仍必须服务端检查当前归属和状态。
- GET每页20，不预取全量或共享私有目录缓存，加载/禁用时拒绝保留的旧选择事件。生成资源扫版本/sourceMappingURL；全工作区发布前审计未在本轮全量重跑。

## 完整人工链路

1. 前端build并强刷，登录→发布长文/post/long→已有专栏，应显示本人目录而非手输ID；选择专栏/章节→存草稿，再读取草稿确认id/title/章节一致；发布后检查归属/目录顺序。
2. 编辑本人已归栏长文，只改标题不应自动改变关系/章节/封面；明确选择另一专栏后，检查PUT只携带变更关系及章节，不意外改封面。源绑定不在当前页时保留显示，不自动选第一项。
3. 本人专栏超过20时翻到第二页；模拟503并重试，确认还在请求失败页、旧绑定保留，不出现虚假空目录。真实空目录显示空，可切新建；切独立长文时不归栏，手动章节处理继续既有规则。
4. 目录加载中切换账号/退出/载入其他草稿或离开页面，延迟旧响应不回填；正常token刷新保持当前选择。确认/提交进行中禁用选择，不改变已冻结命令。
5. 桌面/移动与键盘select、翻页、失败按钮焦点；真实服务验缺content.read、撤销令牌、删除/停用专栏、他人专栏越权及黑名单→test→解封。这些不由合成fixture证明。

## 下一步：已确认标签规则

用户已确认新v3标签联想：content.read；trim后忽略大小写前缀，最大64个UTF-16单位，字符沿现有Tag.TAG_TITLE_PATTERN_STR范围（不允许内部空白）；只选仍有关联文章的标签名，最多7条；空前缀取热门7条。选择只替换输入中的当前标签，不替用户新建/截断其他项。该新查询与客户端联想尚未实现，下一批继续，不重复询问。

全v3仍未完成。文章删除/置顶客户端、标签联想、私信/聊天室主体及真实服务全链验收等继续。
