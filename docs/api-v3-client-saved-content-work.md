# v3 关注标签、收藏与订阅读取

2026-10-26。迁移三个原用户路径：/member/:userName/following/tags、/following/articles、/watching/articles，并将/stars本人收藏接同一视图。后端沿用已有资源，接口契约和276条总数保持。未迁移关注动态/watch、标签关注/文章收藏订阅写入、旧基础资料、私信或聊天室；完整v3目标仍继续。

## 实际文件

客户端根：/Users/ling/Documents/fishpi/website_v3。

| 文件 | 本批行为 |
| --- | --- |
| src/api/v3-saved-content.ts | 本人/me/follows/tags、collections、subscriptions与他人三个公开资源选择；严格分页/ID/DTO，只有本人文章列表接收available占位；不投影占位正文/标题 |
| src/views/SavedContentView.vue | 三类只读列表、真实分页/总数、403/404/错误与空状态、重试/返回第一页、本人占位与按目标ID链接；身份/目标/kind隔离，SSR只准备态，SCSS共用响应布局 |
| src/views/CollectionsView.vue | /stars改为上述本人收藏视图包装，移除旧apiKey/SWR及按数组长度猜页数 |
| src/router/index.ts | 三个既有member子路径接新视图，其他用户路由不变 |
| src/views/MemberView.vue | 移除上述三项“无JSON接口”的缺口映射，原导航保留 |
| src/api/fishpi.ts | 删除无消费者fetchCollectedArticles；ArticleSummary加匿名标记类型 |
| src/api/v3-content.ts | 文章摘要保留匿名标记供共享渲染，不恢复真实作者 |
| src/components/articles/ArticleFeed.vue | 匿名摘要不再产生“匿名用户”假主页链接，不展示作者头像/简介；完整版显示纯文本匿名标识，其他文章保持原显示 |
| tools/v3-saved-content-test.mjs | 七组实际API/DTO/Vue/共享ArticleFeed/SSR测试 |
| AGENTS.md、README.md、docs/V3_MIGRATION.md、docs/MISSING_APIS.md | 更新当前迁移范围与长期本人/公开边界 |
| dist/client、dist/server（生成） | corepack yarn run build生成，未直接编辑CSS/min.js |

Rhythm只修改本文件、docs/api-v3-progress.md及UPDATE_LOG.md。没有Java/FTL/SQL、APP或classic皮肤修改；共享Vue视图检查桌面/移动宽度，不需对未调用它的classic模板同步改造。没有Maven、真实账号写入、提交、推送或发布。

## 数据与权限边界

本人按当前账号用户名和路由目标匹配选择/me，/stars强制本人；他人只读公开路径，403不回退/me或旧资料接口。公开文章列表只包含可列出文章，本人collections/subscriptions保留原关系顺序和available=false的ID占位，没有标题、作者、正文或可点击的假链接。本人公开页同样用本人集合，不能用公开筛选后的数量假装全部本人关系数。标签链接用真实ID，不用名称或slug冒充ID；描述只作纯文本。

列表无SWR/私有全局缓存，凭据稳定版本、账号/名称、目标、kind与owner模式共同隔离；退出/换号/换目标立即清空，迟到请求不能写回，正常token轮换保留页。SSR不请求这些本人/读者相关数据，导航在mounted后渲染，避免恢复身份与匿名SSR水合不一致。

## 已执行证据

- 七组专项通过：精确本人/公开路径、分页/DTO/未知字段投影、占位元数据与匿名去身份；实际页面及共享Feed分页/末页、本人占位顺序、昵称/标题文本；他人标签ID/0计数；503恢复/403/合法空；退出丢弃迟到占位并公开重读；/stars与SSR；目标/kind切换拒绝旧本人结果、共享compact/full匿名视图不链接或显示作者元数据。HTTP/auth/router端口为明确夹具，生产API解析、组件、文本处理及ArticleFeed实际编译运行。
- tools/v3-content-test.mjs、v3-user-relations-test.mjs五组、v3-user-follow-test.mjs六组回归通过。没有将客户端测试当后端SQL/权限证明。
- corepack yarn run build：vue-tsc/client/SSR通过。Rhythm tools/js-version-audit.mjs扫描生成assets，68文件、0命中；未发现map文件，报告/tmp/rhythm-v3-client-saved-js-version-audit.json。不是所有库指纹或全站CDN扫描。
- IAB只连接127.0.0.1:5173→18765合成业务API：/stars显示21条含本人不可读占位，第二页503→同页重试到2/2并禁下一页；订阅切到/me/subscriptions显示匿名文本与不可读ID，无作者假链接；标签走/me/follows/tags，HTML样例作为文本、真实0篇/2关注；他人private收藏403，empty订阅0条/暂无。Hydration日志为空。
- 1280×900及390×844两viewport截图已查看，导航、文章、占位和分页正常换行；不是实际移动设备。浏览器API流仅合成数据，无真实后端账号业务请求。临时页面/服务结束清理。

日志/tmp/rhythm-v3-client-saved-{tests,build,content-regression,relations-regression,follow-regression,fixture,server}.log；截图同前缀desktop-proof.png、mobile-proof.png。没有本轮真实8080/数据库或全局风控验收。

## 完整人工验收

1. 前端指向本地Rhythm8080，执行corepack yarn run build并强刷；本批没有后端源码变化。若尚未加载此前v3改动，先通过IDEA重编译/重启并确认，不能把旧服务结果当新代码验证。登录隔离测试账号，不操作生产数据。
2. 从用户主页分别进入关注标签、收藏、订阅三个原路径，另从导航进入/stars。本人调用/me资源，标签名称/描述和真实ID链接正确，文章当前可读摘要与不可读占位按原顺序显示；不显示占位目标标题/正文/作者或假链接。匿名文章只显示匿名标识，不进入假用户页。
3. 超过20项时下一页/末页/上一页、总条数/总页数与服务端一致；第二页网络失败或503应显示错误，可重试当前页或返回第一页，不伪造0条。合法空才显示0条；列表缩减导致当前页空可返回首页。
4. 他人列表只走公开资源，公开可读、私密403和用户不可用404分别验证；不能从本人占位推断他人隐藏内容。普通token刷新保留页；退出/换号/快速换目标或kind并延迟旧响应，旧本人结果立即清除且不能回写。/stars退出显示登录入口。
5. 标签描述和文章标题含HTML时显示为文本，真实0计数不丢失，匿名作者URL/头像/简介不渲染。损坏分页、重复ID或字段类型错误应显示错误而非mock；检查SSR首屏与Hydration日志，不能闪入其他读者缓存。
6. 桌面/移动真机验证三导航、长标签/标题换行、分页按钮、文章详情跳转与不可读占位无链接；本轮只做浏览器两viewport。收藏/订阅/标签写入及详情权限在其独立模块验收，不能据此宣称它们已迁移。
7. 实际新IP/黑名单/test、Captcha解封、匿名计次、anonymous.viewSkips、开发风控开关及Bearer scope不足/撤销回归；上述服务端全局链尚未实测。

## 安全与反漏扫

只GET已有v3，内存Bearer由共享transport处理，不写URL/旧apiKey/图片/日志；无新Cookie写或CSRF例外，无前端写事务。本人/公开选择、当前读者代际与no-store阻止错误复用私有结果，但服务端授权仍是最终边界。所有文本Vue插值，文章ID/标签ID校验后构造内部链接，不使用不可信permalink；占位响应严格投影。无远程服务端抓取/SSRF、上传下载或新CDN URL，列表/分页/请求参数有界，未做生产压测。

BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor、anonymous.viewSkips未改；新浏览器页面只消费现有鉴权/匿名校验资源，不新增后端豁免。生成资源扫描范围仅68JS；历史CDN版本、sourceMappingURL、目录枚举、上传下载路径、完整全局未鉴权/越权/CSRF/CORS/风控及真实账号撤权未验证，不默认通过。现有文章详情和头像悬浮资料仍有独立旧子链，不宣称整个用户页/互动系统完成。
