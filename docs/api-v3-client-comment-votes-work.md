# v3 客户端评论赞踩（2026-10-26）

## 行为与边界

普通评论、楼中楼的 CommentItem 及长篇段评侧栏使用共享 ArticleInteraction 的 comments/vote 资源，读取既有 GET /api/v3/comments/{id}/vote，明确 PUT up/down 与 DELETE 清除。再次点击已选择项清除；不会把旧 toggle 或旧列表计数当作当前状态。游客/SSR 保留只读评论，挂载且当前认证后才读取本人投票。长篇仍为上一批的平铺段评分页。

API/Pinia/组件增加 articles/comments 资源判别；只为 comments 开放 vote，不误拼接未实现的感谢或打赏读取。命令、完成通知、在途读取与组件身份都含资源；同数值 ID 的文章与评论完全分开。旧文章调用默认 articles，三个文章互动既有契约不改。未知命令保留原目标、choice、key；正常 token 轮换/SPA 重挂仍可恢复，确定恢复前不接受其他选择，恢复 4xx 不丢原键；第一次确定 4xx 可后续新发。退出/换号清本地命令并废弃迟到读写。

回执只确认本次操作，随后 GET 当前状态；失败只提供重读，不再写入。不同组件完成会废弃先前在途 GET，文章完成不触发同 ID 评论刷新。写入和后置读取期间保留原操作按钮 DOM，以 aria-disabled/busy 和处理函数拒绝重复操作；读取失败后撤去不可核实的状态，恢复按钮仍可用。评论不再把互动错误区放进桌面悬浮头部。未知请求仅内存恢复，刷新/退出会丢本地信息，不宣称离线持久队列。

本批无新后端路由、Java/FTL/SQL/Repository、经典皮肤或 APP 改动；总数仍279（278HTTP+1WS），API.md没有新契约。感谢/采纳/emoji reaction、文章编辑/Socket、上传、私信与聊天室等继续迁移；匿名本人编辑/删除管理证明也未在本批补齐。

## 实际文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-article-interactions.ts`：资源参数、评论投票请求/响应投影与方法校验。
- `src/stores/v3ArticleInteractions.ts`：命令及完成通知资源隔离、旧文章默认行为保持。
- `src/components/articles/ArticleInteraction.vue`：资源与读取生命周期、原按钮节点保留/重复操作门禁、评论标签。
- `src/components/CommentItem.vue`：普通评论/楼中楼使用当前赞踩状态，互动区保持正文后的同级布局。
- `src/components/articles/LongArticleReader.vue`：段评侧栏共用评论赞踩组件。
- `src/views/ArticleView.vue`：删除旧点赞处理和事件绑定。
- `src/api/fishpi.ts`：删除无消费者旧 voteComment 包装。
- `tools/v3-comment-votes-test.mjs`：12组实际生产TS、Pinia、完整共享组件及真实CommentItem专项。
- `tools/v3-comments-test.mjs`：原评论三写专项补充非本项互动子组件适配；其专项仍16组。
- `AGENTS.md`、`README.md`、`docs/MISSING_APIS.md`、`docs/V3_MIGRATION.md`：长期约定与实际迁移状态。

Rhythm：`AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、本文件。

## 验证证据

12组专项实际执行生产API/Pinia/编译完整Vue：精确资源/方法/choice/key及响应投影；文章/评论同ID未知命令隔离与轮换/恢复403；真实组件丢ACK/重挂原键恢复后当前GET覆盖历史回执；清除成功/GET503只重读；多组件通知废弃旧GET；同ID文章完成不污染评论；换号丢迟到读写；SSR/游客零私有请求；新422后新命令；实际CommentItem忽略旧99计数而读当前0并执行赞/踩；同ID切资源废弃旧读取；异步写/GET期间原按钮DOM保留且重复点击无新增请求。长篇接线静态检查与构建通过，侧栏完整DOM没有由本专项执行。

文章互动15、评论三写16、认证传输12、启动9、共享生命周期3回归通过。corepack yarn run build通过类型、client及SSR构建；71生成JS版本扫描0命中，无source map，两个仓库diff --check通过。测试的HTTP/认证/确认/无关组件是受控适配器，不能当作真实后端事务、权限或设备测试。

本地Express/Vite整页HTTP SSR分别读取合成普通/长篇文章及公开/匿名私密评论：公开内容正常、隐藏正文和匿名真实ID不进HTML，长篇带当前锚点；无私人投票/原文请求、Authorization或写请求。日志 `/tmp/rhythm-v3-comment-votes-ssr.log`。浏览器工具本批返回空会话，真实点击、双视口、焦点和真机未验；原按钮DOM由实际Vue自定义renderer断言，并非浏览器焦点实测。

## 人工完整验收

1. 先用IDEA重新编译/重启前批Java再联调，避免旧服务行为被误判；本批没有Java变更。登录后打开普通文章，根评论和楼中楼均读取当前赞踩，点击赞、切踩、再次点击清除；逐项核对GET结果和实际数据库/通知。普通用户自投、不可读/失效目标、缺scope应明确失败，不修改结果。
2. 打开长篇段评侧栏，切换段落和分页，执行同样赞踩/清除；切换期间迟到旧读不得覆盖新目标。评论与文章即使ID相同也不得共享按钮状态或未完成命令；切回普通文章不受影响。
3. 模拟PUT/DELETE已提交丢响应，跨SPA/组件恢复：必须原目标、choice和Idempotency-Key，不能换选择或走旧toggle。恢复返回原回执后必须GET当前状态，后续其他设备已改动时展示最新GET；恢复403不得丢原命令换键。
4. 写成功后GET503，只点击重读，确认无第二次写入。延迟写/读时以鼠标和键盘重复点击不重复请求，检查按钮节点和焦点保持。退出/换号或快速切文章，原状态/命令和迟到结果不得回填；正常token轮换保留同一身份未知命令。
5. 桌面及390px移动端检查操作区、长段落侧栏、错误/重试和键盘，无横溢出；游客不读取本人投票，私密/匿名评论不可显示隐藏正文或真实身份。文章赞踩、感谢/打赏费用确认、取消、未知恢复和当前付费正文作为共享组件回归。

## 反漏扫复核与未验

无新增匿名路由/viewSkips/CDN/上传下载地址，未改BeforeRequestHandler、匿名验证码或黑名单链；只使用既有Bearer HTTP，不填旧apiKey或新增Cookie例外。资源白名单、ID及响应校验沿既有适配，正文无新增HTML来源或远程抓取，命令仅内存。定向生成资源版本/map、SSR匿名、身份隔离和既有认证传输回归已通过；真实越权/CSRF/注入/黑名单/test/解封、负载与限流、多实例/账款/通知以及部署IoC完整验收仍未实际执行，不能默认通过。无Maven、APP修改、提交或推送。
