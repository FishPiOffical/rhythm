# v3 社区统计客户端（2026-10-27）

本批将页脚“数据统计”从经典站点跳转改为客户端`/statistic`，复用`GET /api/v3/statistics`，不改接口契约。后端仍285条注册；无Java、FTL、SQL、APP修改，未执行Maven。

## 实际文件与行为

website_v3：

- 新增`src/api/v3-statistics.ts`：仅投影九项现有公开统计，要求非负安全整数；错误和缺字段不填零，未知字段不传播。
- 新增`src/composables/useStatistics.ts`：加载、刷新、错误重试，退出/切换身份/正常token轮换重新读取；序号和当前授权边界拒绝旧响应，卸载后不回写。
- 新增`src/views/StatisticsView.vue`：用户、文章、评论、领域、标签、链接、当前在线总数/成员及最高在线九项。明确统计快照，桌面三列/移动两列，同级卡片，SCSS源经构建。
- 修改`src/router/index.ts`、`src/layouts/AppShell.vue`：公开路由与实际SPA页脚入口。
- 修改`src/entry-server.ts`、`src/seo/payload.ts`、`src/seo/ssrRoutes.ts`：每请求公开SSR快照，首屏数据校验、一次消费；访客有效载荷不重复读取，已登录则查当前数据。
- 新增`tools/v3-statistics-test.mjs`十二组专项；修改`tools/v3-ssr-payload-test.mjs`补每应用隔离与一次消费。
- 两仓AGENTS、本记录及迁移总表更新；Rhythm另更新UPDATE_LOG.md，API.md既有契约不变。

## 实际验证

`corepack yarn run build`通过类型、客户端与SSR构建。tools目录64个测试脚本全部通过，共676组；其中统计12组、SSR新增1组。产物75个JS版本审计零发现，无map/sourceMappingURL。

真实Chrome使用生产构建和SSR合成HTTP夹具，未连接生产或写业务数据：

- 1280×720：首屏显示九项真实夹具值，刷新503清除旧数字、显示错误，点击“重试统计”恢复；检查错误态旧12,345消失、成功态恢复。证据`ev_aeab9df83740f270583f86a22587678f8eba9dfa48c8a4124f46397d299b33bd`。
- 390×844：九项两列展示，无横向溢出，0链接正确展示，刷新成功。证据`ev_7636615f30d2544495b19cc5f7cf57b906fb3fbc59d8392789fa43d4d17250d3`。
- 两场景无page-error或水合警告；访客恢复会话有预期401，桌面故障演练有预期统计503，不把这些控制台错误说成零。

## 人工完整链

1. 从页脚“数据统计”进入`/statistic`，核对SSR首屏和九个字段；游客无需先登录，浏览器Network应使用现有v3统计URL。核对用户/文章/评论等与后端响应一致，包括真实0值。
2. 点击“刷新统计”，加载时按钮禁用；返回成功后显示新快照。用开发代理模拟503、断网和缺字段/负值响应，应显示错误而非旧数字或全零；点击“重试统计”仅重发GET并恢复当前数据。
3. 发起慢请求后登录、退出或切换账号，再放行旧请求；旧响应不得覆盖新状态。正常token轮换仍应重新读取。离开页面后迟到响应不应修改已卸载页面。
4. 分别直接访问/刷新与SPA页脚导航，检查首屏无水合警告；390px和桌面检查九项完整、长数字换行、刷新/错误按钮及布局无嵌套卡片。
5. 在真实开发服务验证匿名首访、黑名单跳转/test、验证码完成与解封、登录Bearer权限、缺scope/无效token响应；相关服务变更须先由IDEA重编译/重启确认。本批未修改Java，无需为本批单独编译Java。

## 安全复核与限制

源码复核ApiV3CommunityProcessor现有公开GET仍挂optionalContent和AnonymousViewCheckMidware；没有新增后端入口或anonymous.viewSkips。BeforeRequestHandler、首访/周期验证码、/validateCaptcha解封与登录验证未改。请求沿既有transport，SSR不使用共享身份；客户端无Cookie写操作/新增CSRF边界，无数据库写入、SQL输入或服务器远程抓取。白名单数字投影不包含用户资料/凭据，不使用HTML注入；刷新在途禁用，不进行后台轮询或自动批量请求。

未增加外部CDN/库、上传下载返回路径或目录入口；生成资源复扫无版本命中/map。真实服务的匿名风控、黑名单/验证码/开发关闭风控行为、Bearer缺scope/失效、限流与目录响应仍未运行验收；统计数据本身及跨节点口径未通过业务库核验。页面仅称统计快照，不扩大为全站去重在线人数。浏览器合成数据不能替代上述真实链路验收。
