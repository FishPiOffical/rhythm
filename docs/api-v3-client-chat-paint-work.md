# v3 客户端聊天室涂鸦上传（2026-10-27，第八十四轮）

## 实际改动文件与行为

本轮仅客户端接既有上传票据及普通消息契约，未新增/修改HTTP接口。后端数量保持285（284HTTP+1WS），API.md已有上传票据契约，本轮无需更改。没有Java/FTL/SQL/Maven/APP、提交或推送。

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/components/chat/PaintPanel.vue`：旧apiKey门槛改为当前内存Bearer/owner，使用共享uploadV3Files及uploadedMarkdown。实际canvas.toBlob导出非空PNG→有.png名称的File→v3票据→图床file字段上传→安全Markdown插入当前草稿→清画布/关闭；不会自动发消息。上传前结束笔画，期间冻结颜色/粗细/撤销/清空/绘制及重复提交，防止导出中新增内容被成功清理。错误保持原画布并明确提示，手动重试才再次上传。
- 同一组件以AbortController、owner、认证context及卸载标志隔离在途blob/票据/图床结果；关闭/卸载取消，退出或换号同时清画布和错误；同账号正常token轮换不丢当前上传。迟到成功不能写入其他草稿。样式源码转SCSS，去外层卡片背景/边框，画布自身边框保留；适配既有两套布局，未修改生成CSS/min.js。
- `src/api/fishpi.ts`：删除已无消费者的旧uploadFiles及extractUploadUrls，移除旧/api/rhypic/upload-ticket请求链，避免其他功能误接旧凭据；共享v3-uploads.ts未改。
- `tools/v3-chat-paint-test.mjs`：真实Vue组件、Pinia、上传adapter及Markdown输出，画布导出/像素、轻量DOM、HTTP/图床均有隔离适配；不是实际浏览器PNG图像测试。
- `AGENTS.md`、`docs/V3_MIGRATION.md`、本文及build产物。

后端仓仅维护 `UPDATE_LOG.md`、`AGENTS.md`、`docs/api-v3-progress.md`及本文。客户端父级`src/views/ChatroomView.vue`未改，已复核经典/简约均挂同一PaintPanel，insertPaint仅追加draft而非发消息；后续由已有v3普通发送链提交，未知发送仍按原正文/key恢复。

## 验证证据

13组涂鸦专项覆盖真实组件pointer绘制/PNG导出/票据/具名multipart/安全Markdown/插入关闭、忙碌冻结/重复触发、空或非PNG导出、未知上传保留及明确重试、缺结果/非法URL、关窗/卸载/退出/换人及迟到blob/票据/图床URL、同账号轮换、访客/撤销/清空、双布局父级接线及实际SSR惰性空画布无取票。图片像素和pointer capture是适配器，不能据此宣称触屏或PNG像素正确。

共享上传9组及既有聊天室/私信/表态/实时/通知/启动回归236组，合计258组全通过、无额外输出；日志 `/tmp/rhythm-paint-regression-*.log`。既有上传专项明确验证图床仅upload ticket、credentials omit、redirect error、专用file字段和有扩展名、签名query不丢及Markdown转义、非法票据/URL/结果拒绝、未知网络不自动重传。

`corepack yarn run build`类型/客户端/SSR通过，日志 `/tmp/rhythm-v3-chat-paint-build.log`。正式js-version-audit扫描72个JS、发现0，无map/sourceMappingURL/sourceURL，报告 `/tmp/rhythm-v3-chat-paint-version-audit.json`；两仓diff --check通过。无新增CDN、目录或服务端上传下载地址。

没有可用浏览器工具，本轮未运行浏览器、真实画布像素/触屏/指针捕获、双视口、真实票据/图床/发送回读及全局风控；不能以隔离adapter替代。上轮Java思过崖仍须用户通过IDEA重新编译/重启后联调，本轮未假设已完成。

## 安全及反漏扫

复用现有登录/upload权限票据入口、Bearer transport及图床白名单响应，不新增匿名入口、不改viewSkips/BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor。SSR只挂空画布不自动上传，访客不发请求，旧apiKey不提升为Bearer；真实未鉴权、越权、CSRF、撤销凭据、黑名单→test→validateCaptcha解封、首访/周期及开发关闭风控仍待运行验收。

图片由浏览器本地PNG画布导出，上传仅专用ticket且omit Cookie/禁redirect，不给图床站点Bearer/密码/个人资料。响应URL沿既有HTTPS校验、名称和Markdown目的地编码，服务端无新抓取或SQL，XSS/注入/SSRF未引入新入口。关闭、身份与代数隔离防旧图片串号，期间冻结避免误删新内容；图床已收文件而响应丢失时无法保证没有孤立图片，取消也不等于服务端回滚，明确重试可能产生另一文件，本功能不承诺上传幂等。当前请求并发保护不代替服务端滥用控制，真实限流/存储限制未压测。

无新增公网站点资源/可枚举目录/上传下载路径，生成产物版本和map扫描通过；整个历史工作区、生产CDN、目录枚举及存量上传/下载路径仍需发布前完整审计。

## 完整人工测试步骤

1. 先确认既有后端v3和图床票据链已运行；前端build并强刷，登录→/cr→经典「涂鸦」→画两笔→插入。检查POST /api/v3/uploads/tickets→专用图床file上传（PNG且有扩展名）→草稿出现图片Markdown，画板关闭；此时不能自动发消息。点击普通发送→当前GET→图片卡片，再从浮窗/历史核对。
2. 简约布局和移动窄屏重复，确认颜色、粗细、撤销/清空、指针缩放、触屏/取消绘制和画布不越界；画板外层不再卡片套卡片。上传期间不能继续画/改颜色/撤销/清空或重复提交，关闭仍有效。
3. 模拟导出失败、票据503、图床断网/403、无对应结果/非法URL，显示错误且保留笔画、不插入、不关闭、不自动重传；明确手动重试后才能重新请求。已插入但普通发送失败时恢复原消息操作，不为同一图片重新上传。
4. 导出/取票/图床阶段分别关闭或离页，随后放行迟到响应，不能插入；退出/换号清画布，旧成功不能写回新草稿。同账号正常token轮换仍完成当前上传。未登录不能取票；图床请求只有upload ticket，无站点Bearer/Cookie。
5. 真实服务另验uploads.write资格/禁言发言、未鉴权/越权/凭据撤销/CSRF、全局黑名单和验证码解封、批量请求与文件限制、PNG实际像素和浏览器图片加载。最终发送仍须现有普通消息权限，不能因已上传而自动放行。

完整v3目标仍在推进。本轮无HTTP契约变化，既有API.md需随整体发布同步网站API文章 https://fishpi.cn/article/1636516552191 。
