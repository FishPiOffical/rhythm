# v3 客户端文章与评论修订历史（2026-10-26）

## 行为与边界

共享RevisionHistory接既有四个GET：article/comment的revisions列表与指定revision详情。普通/长篇文章保留原历史入口，根评论、楼中楼、长篇段评新增独立历史入口；只读优质回帖同样可申请读取，最终由服务端当前权限判定。游客/SSR无历史请求或控制；用户明确展开后才分页读取元数据，点击版本后才读取正文。不会根据缺省revisionCount=2断言存在历史，也不凭公开作者ID推断匿名本人能否读取。

列表严格验证pagination.page/size/total/pageCount、条目数、连续全列表index、ID唯一与current位于末尾；不根据短页猜分页。current只能来自服务端列表，评论current.time=null显示修改时间未知，不造时间/版本。详情绑定所选id/time/index/current，变化报409提示刷新列表；分别处理列表/正文错误，拒绝不作空历史，正文失败不留旧文本或造空正文。记录减少导致旧末页为空时仍可返回前页。

接口content是旧历史格式清洗文本，并非原始Markdown。客户端只用纯文本展示，标题/内容按已有decodeEntities还原实体后交Vue文本插值，不走v-html，不再调用旧POST /markdown进行二次解释；不能把历史接口用于原文编辑。HTML标签、Markdown符号可作为历史文本可见，无脚本/链接执行。额外编辑者ID、用户字段不投影，不返回/请求付费区内容。

每次重新展开/刷新都当前GET，不缓存历史；收起、换目标、退出/换号或卸载清内容并废弃迟到列表/正文。列表/详情各有独立序号，选择另一版本/刷新列表能废弃旧正文；正常同账号token轮换保留正在读的历史。ArticleView改为稳定viewerIdentity（认证版本、是否有token、当前用户ID）管理读版本和页面重建，避免自动刷新token时整页卸载历史。换账号仍重读，未改变权限来源。

异步保留分页/刷新节点，用aria状态及处理函数阻止旧页/重复请求；正文文本与布局同级，不添加嵌套卡片。主动打开后定位面板标题，收起/换目标/退出前发生的迟到定位无效。键盘/实际滚动焦点仍待浏览器验收。两端共响应式源，长篇“更多→历史”仍经原事件打开共享面板。

本批无Java/FTL/SQL/Repository、经典模板、APP或API.md契约变更；后端281（280HTTP+1WS），GET139/POST71/PUT46/DELETE24。未运行Maven，未提交/推送。文章编辑/发布/上传、匿名本人编辑删除证明、私信/聊天室主体与v3 Socket及完整部署验收仍在队列。

## 实际文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-revisions.ts`：两类列表/详情请求、严格分页/元数据/预算投影及列表详情绑定。
- `src/components/articles/RevisionHistory.vue`：惰性展开、分页、独立重试、文本展示、身份/目标/请求隔离、节点/定位。
- `src/views/ArticleView.vue`：原历史入口/复用槽接共享组件；删除旧方法/状态/预览及样式；稳定viewerIdentity防正常轮换卸载页面。
- `src/components/CommentItem.vue`：普通评论/楼中楼/只读评论历史入口。
- `src/components/articles/LongArticleReader.vue`：段评历史入口，文章更多菜单不伪造版本计数。
- `src/api/fishpi.ts`：删除无消费者fetchArticleRevisions/fetchArticleRevision/previewMarkdown包装及旧历史类型。
- `tools/v3-revisions-test.mjs`：14组生产TS/完整Vue专项。
- `tools/v3-comments-test.mjs`：新增第17组真实ArticleView+RevisionHistory/API整页联动，其他评论测试隔离历史子组件。
- `tools/v3-comment-votes-test.mjs`、`tools/v3-comment-rewards-test.mjs`、`tools/v3-reactions-test.mjs`：各专项隔离非本项历史子组件。
- `AGENTS.md`、`README.md`、`docs/MISSING_APIS.md`、`docs/V3_MIGRATION.md`：长期约定与状态。

Rhythm：`AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、本文件。

## 证据与限制

14组专项实际执行生产API和编译完整RevisionHistory：两类型精确GET/无缓存/字段白名单；错误分页/缺项/索引/current/详情绑定变化拒绝；真实面板惰性展开、正文作为字面文本而不调用预览；403与合法空不同/可重试；第二页503恢复真实页码；详情503不造正文/重试；快速选择拒迟到正文；收起/退出丢旧正文；换目标关闭；评论current未知时间/无伪标题或编辑者；SSR/游客零读取；同读者token轮换保持面板；记录减少后的前页恢复/入口与旧包装移除；异步分页保留原按钮DOM、aria禁用旧条目并阻止请求。

评论专项17组通过；第17组运行实际ArticleView、RevisionHistory和API，经真正外部按钮/ref/复用模板打开并发GET，读取中同账号轮换token不重读/卸载主文章，当前正文成功显示；退出清正文。其他子组件/HTTP/确认/认证等为受控适配器，不能当成真实浏览器或后端数据库/权限验收。

回归reaction16、评论赞踩12、感谢采纳11、文章互动15、认证传输12、启动9、共享生命周期3通过。corepack yarn run build完成类型、client与SSR构建；71生成JS版本审计0命中、无map/sourceMappingURL/sourceURL，两个仓库diff --check通过。APP未修改，后端专项本批未重跑。

本地Express/Vite合成HTTP SSR普通/长篇通过：公开评论/隐藏正文/匿名身份投影及当前段落锚点保持，SSR日志没有历史/markdown预览、Authorization或写请求。记录 `/tmp/rhythm-v3-revisions-ssr.log`。浏览器工具返回空会话；实际权限/历史数据、长篇菜单和段评侧栏完整DOM、双视口/滚动/焦点/真机与全局风控未验，不能写成已通过。

## 人工完整验收

1. 联调先确认前批Java已通过IDEA重新编译或重启；本批仅用户站无需因本批改动重启Java。登录作者账号，普通文章点击修订历史，先见分页列表再点具体版本。另以有历史权限且当前可读的其他账号验证；无历史权限403与真正空历史明确区分。游客不显示历史控件、不得请求本人历史。
2. 检查普通历史/current、真实total与翻页，评论current时间为未知而非当前时间。超过20条时第二页503后重试仍第二页；列表缩减导致旧末页为空可以上一页回有效页。刷新列表后旧正文清除；当前已变化、版本不存在404/409不显示旧文本。
3. 文章标题/正文包含HTML、实体、Markdown和多行文本时作为文字可见，不执行标签/事件、不再请求POST /markdown，不把历史文本带入编辑器冒充原文。正文读取503只重试正文、列表仍可选择；缺失/失效目标或当前阅读权限撤销不得继续保留旧版本正文。
4. 根评论、楼中楼和长篇段评打开各自历史；私密评论仅双方且仍满足历史权限，无关账号404；匿名本人由服务端本人ID判断，不暴露真实作者/编辑者ID。长篇更多→历史应定位到共享文章历史面板，而不是将段评或别的文章版本混入。
5. 延迟详情后快速切另一个版本、收起、换文章/段落、退出/换号，旧正文不得回填。正常token刷新保持历史；刷新/翻页中重复点击无额外详情，按钮节点/键盘焦点可用。401/验证码流程结束后仍需当前权限重新GET。
6. 1280px桌面与390px移动端，检查普通/长篇更多菜单、段评侧栏、历史分页/错误/长行文本不横溢出，无新增hydration告警。真实数据库、角色/讨论/私密权限、验证码/黑名单/解封和长篇滚动焦点须实际环境验收。

## 反漏扫结论

无新增公网路由、viewSkips、CDN、上传下载、目录或凭据例外；旧历史页面/API与匿名/Before链未改。新客户端只调用已有Bearer content.read历史GET；不依赖apiKey/用户名或前端按钮作权限证明。路径类型/ID及分页有界，响应元数据/正文白名单，不取编辑者资料；文本不进HTML/URL，不新增远程抓取或数据库写。SSR/游客无私人读取；稳定认证版本+owner与请求序号阻止跨身份/目标迟到响应，正常轮换不会升级旧身份。

生成资源版本/map/sourceURL与既有鉴权回归、组件/整页SSR和身份隔离通过。真实未鉴权/越权/CSRF/XSS/注入/SSRF/批量压力、全局黑名单/test/解封、公开目录与历史授权数据库全链路没有实际执行，不能默认通过。没有把合成接口/renderer结果当作真实浏览器或生产权限证据。
