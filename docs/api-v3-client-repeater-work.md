# v3 客户端复读机迁移（第86轮）

## 范围与实际文件

本批只复用既有复读机接口，未增加或改变 HTTP 契约，API.md 已有完整文档。无 Java、FTL、SQL、APP 改动，未执行 Maven。首页与 /repeater 共用原命令恢复，界面适配桌面和移动宽度。

website_v3 实际修改：

- `src/api/v3-repeater.ts`：类型/公开 DTO 白名单、列表/随机下一条、投稿、明确 PUT/DELETE 点赞；写回执仅保留编号。
- `src/stores/v3Repeater.ts`：唯一未确认原命令/原键、平台明确二次确认、账号隔离及提交后读刷新信号。
- `src/composables/useRepeaterFeed.ts`：分类列表与服务端随机，GET 原参数重试、读代数/身份/凭据隔离。
- `src/components/RepeaterLikeButton.vue`、`RepeaterComposer.vue`、`RepeaterRecovery.vue`：明确点赞/取消、投稿及全局恢复。
- `src/views/RepeaterView.vue`、`src/components/home/HomeRepeaterStation.vue`：替换旧读取和 toggle，正文纯文本展示，删除本地随机与 HTML 剥除。
- `src/layouts/AppShell.vue`：唯一恢复提示；`src/api/fishpi.ts`：旧 facade 转 v3，移除旧点赞包装。
- `src/views/HomeView.vue`、`src/entry-server.ts`：首页及 SSR 明确 joke 分类；SSR 不写入或发个人化额外读。
- `tools/v3-repeater-test.mjs`：实际 API/store/composable/页面/组件及 SSR 隔离测试。
- 两仓 `AGENTS.md`、本记录、进度文档；后端 `UPDATE_LOG.md` 更新公开客户端行为。

## 行为边界

列表为前12条榜单，不宣称全量分页。随机从服务端前50候选中排除当前 ID，可能无下一条。作者无效时显示资料不可用，不冒充匿名。成功后重新 GET 当前榜单；历史点赞回执不覆盖当前计数，投稿成功也不伪造榜单新项。随机条目点赞后回到当前榜单，榜外项可能消失。

投稿保留原输入送服务端清洗，长度提示区分原文上限和清洗后2至500；客户端拒绝无效 Unicode/控制字符。正文、作者名、错误使用文本插值，无 v-html。命令仅内存保留，离开 SPA 页面可恢复，刷新或退出丢失；恢复沿原正文/明确点赞状态及原幂等键，未知失败不新建替代操作。同账号正常轮换保留命令，换人/退出丢弃，迟到结果不串账号。

## 验证与限制

18组复读机测试通过，包括真实页面/首页/表单和 SSR、写后 GET、未知响应恢复、平台确认、GET-only 重试、换分类/退出迟到隔离、原随机排除参数保留、明确初次422。类型、客户端及 SSR 构建通过。tools目录60个测试脚本均通过，共613组；生成73个JS文件版本发现0，无map/sourceMappingURL/sourceURL，两仓diff --check通过。日志 /tmp/rhythm-repeater-regressions.json、/tmp/rhythm-v3-repeater-build.log、/tmp/rhythm-v3-repeater-version-audit.json。

本轮无浏览器工具，未实际验证桌面/移动布局、控件与 clipboard、真实服务幂等/权限/职业投稿来源。测试使用 HTTP/渲染适配，不能替代浏览器及数据库验收。此前 Java 变更仍须用户在 IDEA 重编或重启，本轮未假设已完成。

## 安全及反漏扫

仅调用已有固定 v3 路径，写使用 Bearer 和明确动词，不回退旧 apiKey toggle。未改 anonymous.viewSkips、匿名验证、黑名单/验证码链，未新增匿名路由。未鉴权/越权/缺 scope、CSRF、已撤销凭据及黑名单→/test→验证码解封均需真实服务复验，不能依据客户端测试宣称通过。

参数有界、正文纯文本；无 SQL、远程抓取或任意资源 URL 入口，未新增注入/SSRF 路径。一次仅一个未知命令；批量滥用控制需服务端验收。DTO 只接受公开字段，不持久化正文或凭据。未新增 CDN、上传/下载路径、目录资源；构建扫描覆盖本轮产物，生产目录枚举/CDN与历史上传下载路径未实际扫描。

## 完整人工测试步骤

1. 启动已部署既有 v3 后端，构建前端并强刷。访客打开首页及 /repeater，切三分类、刷新、下一条；正文中的标签应显示为文本，首页初始分类为段子；空列表/无下一条正常提示，失败显示重试，不冒充空成功。
2. 登录后在首页、独立页依次点赞和取消，网络应为 PUT/DELETE 后 GET，计数与选中态以 GET 为准；刷新和分类切换结果一致。桌面/390px 都检查布局与按钮。
3. /repeater 投稿2至500清洗后字符，含 HTML/空白时按服务端规范化；成功显示编号并重读，未进前12条时不强塞条目。测试空白、单字符、清洗后过长、非法 Unicode/控制字符、缺 scope/失效账号，失败不显示成功。
4. 测试环境让已提交写响应断开，离开页面，从全局恢复，以原键重试只生效一次；新操作不能覆盖未知命令。触发平台确认，仅明确同意才重试，取消不发写；正常 token 轮换保留未知命令，退出清理。
5. 写成功后的 GET 故障只重读，不重写；随机 GET 失败保留原分类和 excludeId。模拟慢响应后换分类/退出，旧正文和 liked 状态不能回流；复制失败显示错误。
6. 真实服务补验未鉴权401/缺 scope403、目标失效、并发点赞、投稿职业来源、CSRF、撤销令牌及完整匿名/黑名单/验证码链。SSR 首屏与客户端挂载不串账号，窄屏操作和 clipboard 需浏览器验证。

整体发布时仍需将 API.md 同步到 https://fishpi.cn/article/1636516552191 。
