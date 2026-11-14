# v3 客户端清风明月接入（2026-10-26）

## 范围与结果

全站 /breezemoons、首页清风模块及通用侧栏接既有 v3 GET/POST/PUT/DELETE。主列表真实分页，本人按作者ID显示编辑/删除；编辑先读取当前原文，正文只渲染服务端 contentHtml。创建/修改/删除未知结果冻结原命令和幂等键，可跨 SPA 组件恢复；普通令牌轮换保留，退出/换账号清理。恢复成功通知原提交编辑框清除对应草稿，不会清除其后另写的内容。成功后重新读取，读取失败仅重读，不再次提交写入。

SSR 公共预取不附读者凭据，全站与首页载荷分列；进程全局载荷改为每个 Vue app 的 provide/inject，上屏后重新按当前读者请求。客户端仍一次消费 initial state。确定性 UTC 时间避免服务端/浏览器时区差。旧全站发布/删除包装已移除；CityView 的城市读取及 MemberView 个人清风仍旧链，本批没有用缺失的城市字段伪造统计。后端276注册入口保持，API.md契约不变，无 Java/FTL/SQL/APP 或 Maven 操作。

## 实际修改文件

客户端根：/Users/ling/Documents/fishpi/website_v3。

- `src/api/v3-breezemoon-data.ts`
- `src/api/v3-breezemoons.ts`
- `src/api/v3-following-feed.ts`
- `src/api/fishpi.ts`
- `src/stores/v3Breezemoons.ts`
- `src/composables/useBreezemoonFeed.ts`
- `src/components/breezemoons/BreezemoonComposer.vue`
- `src/components/breezemoons/BreezemoonPanel.vue`
- `src/components/breezemoons/BreezemoonList.vue`
- `src/components/sidebar/BreezemoonFeed.vue`
- `src/views/BreezemoonView.vue`
- `src/views/HomeView.vue`
- `src/entry-server.ts`
- `src/seo/payload.ts`
- `tools/v3-breezemoons-test.mjs`
- `tools/v3-ssr-payload-test.mjs`
- `tools/v3-following-feed-test.mjs`
- `AGENTS.md`
- `README.md`
- `docs/V3_MIGRATION.md`
- `docs/MISSING_APIS.md`

后端仓库仅维护 `docs/api-v3-client-breezemoons-work.md`、`docs/api-v3-progress.md`、`UPDATE_LOG.md`、`AGENTS.md`。此前已有未提交修改保留，未提交/推送。

## 实际验证

- `node tools/v3-breezemoons-test.mjs`：8组真实 TS/Pinia/Vue 组件隔离测试，覆盖 Unicode/Java trim/512、固定路径/字段、未知结果同键恢复、恢复4xx、身份切换/迟到响应、确认取消与实际 DELETE、原文编辑、写成功读失败、跨组件草稿清理、SSR不发读者请求。
- `node tools/v3-ssr-payload-test.mjs`：4组，实际交错两个 Vue SSR 应用，验证隔离/消费/客户端副本，及既有文章/用户/列表/领域/标签载荷回归。
- following-feed 6组、saved-content 7组、auth-transport 12组回归通过。均隔离夹具，不能等同真实服务器权限/持久化验证。
- `corepack yarn run build`：vue-tsc、client/SSR 构建通过；正式 js-version-audit 扫描68个生成JS，0命中，无map文件；git diff --check通过。
- IAB 本地合成 API：主列表/侧栏共享一次待确认发布，从侧栏重试确认为 REPLAY POST，原输入清空；编辑后故障重读没有第二次 PUT；第二页503后重读显示2/2并禁下一页；本人ID按钮、他人无管理按钮；删除确认取消未发送DELETE。实际删除由组件隔离测试覆盖，浏览器未执行不可逆删除。
- 1280x900 与390x844检查；移动 document clientWidth=scrollWidth=390，无横溢出。截图 `/tmp/rhythm-v3-moons-desktop.png`、`/tmp/rhythm-v3-moons-mobile.png`。首页实际可见清风并读取size=8，侧栏size=6、主列表size=20；SSR日志Bearer=false、客户端true。
- 首页刷新发现既有 CheckinPanel/HomeRepeaterStation 等登录态 hydration mismatch，不能宣称整首页 SSR 已通过；清风专用页与载荷专项不等同整站。夹具首页旧排行组件还走已有公开读取，未对生产账号执行写操作。相关整页 SSR 差异留在后续队列。

## 人工完整链路

1. 本地 Rhythm 已部署既有 v3 时，前端代理指向 localhost:8080；如之前 Java/FTL 尚未生效，先由用户在 IDEA 重新编译/重启，本批不自行推定后端已更新。以有手机号及发布权限的测试账号登录。
2. 进入 /breezemoons，检查真实第一页/总数，翻到下一页；错误时页面明确失败，重读保持该页，不出现旧页内容；无内容时明确空状态。退出后只允许公开读取，发布提示登录。
3. 主页面或首页/侧栏输入含换行、中文及emoji的合法正文发布；成功后各已挂载面板重读。空白、控制字符、无效代理字符、超过512UTF16失败且不提交；补验手机号缺失/权限拒绝/审核拒绝。
4. 人为丢失一次写响应，从另一面板恢复：同键返回原操作，无重复动态，原输入清空；正常令牌轮换仍能恢复。换账号/退出清空草稿和待确认，旧慢响应不得复活内容。
5. 本人记录编辑，先读取原文，再保存；网络断在成功后列表GET时，界面应说明写已确认，重读不得再PUT。其他用户记录无按钮，仍需直接越权请求验证服务端拒绝。
6. 测试可丢弃动态点击删除，取消无请求；接受仅一次明确DELETE，结果未知同键恢复。实际服务端删除及幂等回放需人工验收。
7. 桌面/移动分别核对正文格式、作者ID、换行与分页；首页/清风页交错SSR请求不串载荷。核对标签或脚本经后端清洗，不可仅凭客户端使用contentHtml判定XSS安全。

## 安全复核与实际限制

本批无新公网路由/匿名白名单/上传下载路径/DB写入/远程抓取。Bearer由既有transport发送，SSR不带凭据，不将Bearer塞旧apiKey；身份变化清私有草稿与待确认。输入/响应白名单及长度校验、HTML仅既有后端清洗字段、作者按钮按ID、批量页size<=50。新SCSS由构建生成，不直接改CSS/min.js。

反漏扫：匿名/黑名单/验证码/anonymous.viewSkips 未修改，不能据此声称实测通过；真实 `/test`、解封、CSRF/越权/敏感数据、内容清洗、数据库写入与多账号并发、真实设备仍待部署后验收。此次正式生成物版本/sourceMappingURL/map扫描通过；全站CDN内容、目录枚举与旧上传/下载链没有全量实测。JS审计不证明部署端目录配置安全。没有运行Maven，没有修改官方APP。
