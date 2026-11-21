# v3 客户端首次认证恢复（2026-10-26）

## 结果与范围

先前初始路由守卫在 mount 前恢复登录，使服务端游客 HTML 与客户端已登录首帧不同：首页签到/复读、文章操作/分享/编辑器可发生 hydration 差异。现在初始客户端路由先解析但不恢复凭据，entry 在 mount 后启动每个 Pinia 应用自己的 clientStartup；其余 SPA 路由仍经过既有认证检查。私有路由由 AppShell 保持统一 SSR/客户端占位，只有实际当前认证成立才挂载子页面。确定为游客时保留完整原路径跳登录；临时失败留在原页面明确重试，重复恢复共用一次实际操作。恢复期间新导航拥有自己的守卫，旧启动结果不得覆盖它。已登录的初始登录页仍在恢复后跳向 redirect。

本批不改变 HTTP 契约、凭据存储/签发、服务端权限或匿名访问策略。后端 register 仍为 279（278 HTTP+1 WS）；APP 未改。此修复针对首次挂载时序，不代表其余页面所有 hydration 差异都已消除。

## 实际文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/stores/clientStartup.ts`：新增每应用启动协调、状态/重试及新导航保护。
- `src/entry-client.ts`：挂载完成后启动恢复。
- `src/router/index.ts`：仅跳过初始客户端导航的提前恢复，后续导航递增版本并保留既有守卫。
- `src/layouts/AppShell.vue`：私有页面当前认证门、加载/错误/重试及 SCSS 样式。
- `tools/v3-client-startup-test.mjs`：实际 Router/Pinia 启动专项；认证和外观使用受控适配器。
- `AGENTS.md`、`README.md`、`docs/V3_MIGRATION.md`、`docs/MISSING_APIS.md`：启动约定及迁移状态。

Rhythm：`AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、本文件。`API.md` 无修改，因为无接口新增、删除或契约变化。未修改 Java、FTL、SQL、Repository 或经典模板。

## 验证证据

- 启动专项 9 组：公开/私有首次导航、确定游客、合法身份、后续 SPA 拦截、临时失败重试、并发恢复/迟到导航、SSR 不恢复、入口与模板约束、已登录登录页回跳。第8组为源码断言，不冒充完整 DOM 挂载测试。
- 认证传输 12、缓存隔离3、SSR载荷4、内容关系9、用户关注7、保存内容7回归通过；均为隔离环境，不能证明真实业务库和完整 IoC。
- `corepack yarn run build`：类型、client/SSR 构建通过；正式版本审计扫描69个生成 JS，0命中，生成目录无 map（最终复跑见 `/tmp/rhythm-v3-startup-js-version-audit.json`）。
- 本地 Express/Vite SSR 经本地合成 HTTP 服务读取首页、普通文章、长篇、通知和登录页，5个响应200；公开文章有真实预取标题/正文，所有响应无本地登录账号身份，通知页为恢复占位。日志 `/tmp/rhythm-v3-startup-ssr-http.log`。
- 前序开发浏览器观察中首页/普通文章未再报告 Hydration，确定游客私有页正确回跳，临时失败显示恢复按钮。最终复核时浏览器发现返回空列表；最终样式、点击重试后的实际私有 DOM、移动视口及长篇浏览器 hydration 未完成，不以先前观察代替最终验收。未创建替代浏览器或修改真实账号。

## 人工完整链路

1. 在用户站启动 SSR，合法账号已登录时，分别直接刷新首页、普通文章和长篇文章；应先保持服务端首帧，恢复后显示本人的导航/可用操作，无 Vue hydration 警告。检查分享链接、编辑器和收藏/订阅当前状态。
2. 已登录直接打开 `/notifications?type=reply`：恢复占位后出现本人的通知，URL保持；退出后直接打开同地址，应跳到登录且 redirect 保留原查询，登录后回到通知。
3. 使刷新凭据请求临时503后直接刷新通知页：保留原地址、错误及“重试恢复登录”，不得挂载私有内容；恢复网络点击重试应正常进入。连续点击不得形成重复轮换，过期/无效刷新凭据则正常跳登录。
4. 延迟认证请求时从初始私有页切到另一公开页，迟到的启动结果不得把新页面跳回登录。已登录直接访问带 redirect 的登录页应跳到目标；后续 SPA 进入私有页面仍须通过认证。
5. 桌面及390px移动视口检查加载、错误/重试、成功页面，无横向溢出；退出/换号后不得保留前一人的私有内容。

本批无 Java/FTL，不要求因本批重启 IDEA；真实后端联调仍须先让用户重新编译/重启前批后端改动。真实 Cookie 域/轮换、完整 IoC、多实例、真机及全局反漏扫未验收。

## 反漏扫复核与局限

本批无新增公网路由、anonymous.viewSkips、上传/下载地址或 CDN 引用；不改变 BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor。客户端私有门不替代服务器鉴权，不能据此声称全局匿名/黑名单/验证码通过。无新远端抓取、SQL、动态 HTML 或批量写入；错误文本插值，状态只驻每应用内存，构建产物版本/map定向扫描通过。未实际验证全局风控、CSRF、真实服务越权与设备网络，应沿真实服务回归，不视作默认通过。
