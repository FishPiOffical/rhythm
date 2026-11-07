# v3 客户端关注动态

2026-10-26。/watch、/watch/users、/watch/breezemoons三个现有登录页面接入已有v3标签文章、用户文章和关注用户清风明月资源。原/following重定向和路由auth要求保留，后端契约/276条总数保持。全站清风明月页面/侧栏与发布删除、标签收藏订阅写入、其他内容互动、私信和聊天室继续迁移，不能把本批关注读取称为整站完成。

## 实际文件

客户端根：/Users/ling/Documents/fishpi/website_v3。

| 文件 | 本批行为 |
| --- | --- |
| src/api/v3-following-feed.ts | 固定source=tags/users文章聚合或/me/watching/breezemoons，校验分页/ID/时间/白名单作者；仅保留服务端contentHtml，不把源正文当HTML或回退全站 |
| src/api/v3-user-relations.ts | 提取既有公开用户白名单为decodeV3RelationUser，原列表行为不改；关注清风作者复用同一ID/用户名/昵称/安全头像解码 |
| src/components/breezemoons/BreezemoonList.vue | 共享只读清风列表，真实公开作者ID与昵称、头像/悬浮入口、时间和已清洗HTML；SCSS处理长文/图片/代码，未增加发布删除能力 |
| src/views/WatchView.vue | 三分类真实分页/总数/末页、错误/403/合法空与重试；读者/分类隔离、SSR准备态、正常token刷新保留页，替换原缺口提示；复用ArticleFeed和原SideBar |
| tools/v3-following-feed-test.mjs | 六组实际API/解码/Vue/文章/清风列表/SSR行为测试 |
| README.md、AGENTS.md、docs/V3_MIGRATION.md、docs/MISSING_APIS.md | 维护迁移范围和当前队列 |
| dist/client、dist/server（生成） | corepack yarn run build生成，无手改CSS/min.js |

Rhythm只更新本文件、docs/api-v3-progress.md、UPDATE_LOG.md。没有Java/FTL/SQL、classic模板、APP、后端新路由或凭据变更；Vue共用布局已检查PC/移动视口，无需改未使用它的classic皮肤。没有Maven、真实账号写入、提交推送或发布。

## 已执行验证

- 六组专项通过：固定本人用户名/类型资源、分页/重复ID/时间/作者响应验证及白名单；缺contentHtml不能回退原正文，HTTP失败不能回退全站；真实组件分类/总页/末页/普通token轮换；清风HTML只用服务端字段、昵称文本、作者ID/链接与头像悬浮元数据；503恢复/403/合法空；切类/退出丢弃旧结果并清HTML；SSR不读私有动态、原auth路由保持。
- 关系读取五组、保存内容七组回归通过。API解码、Vue/ArticleFeed/BreezemoonList和文本处理实际编译运行；HTTP/auth/router、VIP样式和SideBar为明确测试适配器，不是完整后端权限或侧栏证明。
- corepack yarn run build：类型检查、client与SSR构建通过。Rhythm tools/js-version-audit.mjs扫描68生成JS、0命中，无map文件；报告/tmp/rhythm-v3-client-watch-js-version-audit.json。不是全站历史CDN或所有库指纹审计。
- IAB本地127.0.0.1:5173→18765合成业务API：标签短页1条但total21仍可翻第二页，503后重试恢复2/2并禁下一页，不按当前数组长度猜页数。切用户页重置1页，首次有内容、重读403、再重读合法0条，未回退其他内容；切清风显示格式化strong/段落、纯文本昵称和时间，源字段样例未显示。Hydration日志为空。
- 1280×900与390×844截图已查看，主区/侧栏保持同级，分类/分页/清风换行正常；不是实际移动设备。原SideBar整体仍有旧子链，本轮未操作其签到、发布等按钮。测试业务数据全为本地合成，无真实账号操作。临时页面/服务结束清理。

日志/tmp/rhythm-v3-client-watch-{tests,build,relations-regression,saved-regression,fixture,server}.log；截图同前缀desktop-proof.png、mobile-proof.png。本轮没有实际Rhythm8080、SQL或全局风控运行验收。

## 完整人工验收

1. 客户端指向开发8080并执行corepack yarn run build后强刷。本批未改后端；若此前v3源码仍未生效，先通过IDEA重编译/重启并确认。登录专用测试账号，从顶栏“关注”或/following进入/watch；未登录应按原路由进入登录。
2. 分别准备已关注标签/用户及其当前公开文章，验证两个分类只显示对应来源、重复文章由服务端聚合契约处理；从未关注来源、不可读文章/讨论帖/隐藏内容不能因动态而越权显示。核对真实总数、下一页、末页禁用和分类切换回第1页，短页也按pageCount判断下一页。
3. 清风分类仅关注用户的公开有效内容，昵称/头像/用户名链接指向正确作者；Markdown已清洗渲染/段落/代码/图片格式保持，原正文不能作为HTML兜底。没有关注用户或无可见内容显示合法空，不回退全站清风。共享昵称样式失败只按原组件文本表现。
4. 503/断网显示错误，可重试当前页或返回第一页；403显示无权读取，均不伪造0条或全站记录。合法空才显示0条/暂无。分页过程中普通token刷新保持页，换号/退出/快速切分类并延迟旧响应，旧文章和清风HTML立即清除且不能回写。
5. 验证SSR初次准备态/恢复身份无Hydration错，PC宽屏/移动真机的分类、长文/图片/代码、侧栏排列、键盘与触屏操作；本轮只有两viewport，未做真机。侧栏旧签到/发布和全站清风页面仍另批验收，不能当本批迁移能力。
6. 实际Bearer无效/过期/权限不足和当前账号撤权；新IP、黑名单/test、Captcha解封、anonymous.viewSkips、开发模式开关完整回归。真实MySQL动态查询、内容权限、清洗恶意正文、缓存/多实例与生产代理尚未验证。

## 安全与反漏扫

本批只GET既有v3，保持登录路由，不新增匿名端点、Cookie写/CSRF例外或后端防扫描豁免。当前用户名取认证账号，固定source与有界分页，HTTP只由共享transport发送内存Bearer；无URL/图片/日志凭据。前端不以旧apiKey冒充Bearer，不缓存或持久化私有动态；读者/分类代际阻止迟到写回，SSR不共享身份。作者只保留公开白名单，昵称Vue文本、链接编码用户名，头像仅既有HTTP(S)/同源安全路径；无新服务端抓取/SSRF、上传下载地址或新CDN URL。

清风HTML的XSS边界是既有ApiV3DiscoveryQueryService#breezemoonDto的Markdowns.clean，再由本组件呈现contentHtml；本批没有新增前端清洗器，也没有将本地合成HTML当实际服务端恶意内容清洗证明。其他旧侧栏/详情子链未整体迁移。

BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor与anonymous.viewSkips未改。68JS生成产物审计通过，历史CDN版本/sourceMappingURL、目录枚举、旧上传下载路径、全局未鉴权/越权/CSRF/CORS/匿名/黑名单/验证码、真实内容权限/HTML清洗及压测没有实际验证，不默认通过。后端API契约未改，无需为本批新增API.md条目；此前API.md仍须同步网站文章。
