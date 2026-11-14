# 共享编辑器上传迁移（第六十三轮，2026-10-26）

## 实际文件与行为

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-uploads.ts`：新增通用v3上传调用。POST既有uploads/tickets，接收服务端目标与专用票据；固定生产图床走已有同源代理，其他配置目标精确直传。上传仅票据Bearer、credentials=omit、redirect=error，绝不发送v3访问令牌/Cookie。
- multipart重复字段`file`，使用upload-N.ext唯一名称绑定结果，重复原文件名可区分；裸图片按已知MIME补扩展名，不改文件内容；无扩展名且MIME无法判断时请求前提示。格式/大小最终仍由图床判断，未增加业务白名单或擅改服务端限制。
- 解析succMap/errFiles逐项返回，保留选择顺序与完整HTTPS查询串，拒绝未知名称、同名成功与失败冲突、不安全URL或异常协议。成功项插入，失败/缺结果项明确提示，不将部分成功伪装成全部成功。网络结果未知不自动重传。
- `uploadedMarkdown`转义文件名HTML/方括号/反斜杠/控制字符，链接目的地仅对括号作Markdown转义，保持签名URL内容。实际CDN Vditor将尖括号目的地截成https:，经真实浏览器发现并修正；不能退回尖括号写法。
- `src/utils/editorUpload.ts`：共享编辑器本地上传会话，阻止同编辑器同时重复选择，身份版本/当前令牌/实例generation隔离；换身份、卸载、外部重设正文取消请求，迟到链接不插入。正常token轮换仍完成。其他文件成功时只插一次；返回部分失败提示。
- `src/components/MarkdownEditor.vue`：移除旧uploadFiles导入与apiKey上传门禁，接uploads.handler与取消/卸载；桌面/移动共用，无CSS/FTL修改。
- `tools/v3-editor-upload-test.mjs`：九组生产TS API/上传会话专项，HTTP/图床为显式适配器，使用真实FormData/File/Response/AbortSignal；组件接线是源码断言。
- `AGENTS.md`、`docs/V3_MIGRATION.md`、`README.md`：当前迁移记录。dist/client和dist/server由build重新生成。

后端本轮只更新`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`AGENTS.md`及本记录，无Java/FTL/SQL/接口契约变化，注册总数282保持。既有API.md已描述本轮消费的上传票据与多文件协议，未修改协议。旧uploadFiles仍被未迁移的其他调用方使用，不能删除或宣称全站上传完成。

## 验证与证据

- 9组专项通过：`/tmp/rhythm-v3-editor-upload-tests.log`。覆盖真实FormData重复file/唯一文件名、票据与输出校验、裸Blob命名、部分成功、HTML/Markdown安全、身份变更/卸载/迟到结果/并发阻止、普通token轮换、网络未知不自动重传。
- 文章草稿27、评论17、Markdown预览7、本人资料7回归通过：`/tmp/rhythm-v3-editor-upload-{posts,comments,preview,profile}.log`。
- `corepack yarn run build`类型+双构建通过：`/tmp/rhythm-v3-editor-upload-build.log`。
- 71个生成JS版本命中0：`/tmp/rhythm-v3-editor-upload-js-audit.json`；无map/sourceMappingURL/sourceURL，两仓diff --check通过。
- IAB真实CDN Vditor+本地合成后端：默认桌面和390×844移动视口分别通过实际文件选择器选择本地合成PNG（不是个人文件），POST v3票据有Bearer，合成图床记录upload-1.png、仅专用票据且Cookie=false；编辑器DOM中完整图片地址和原文件名。首次发现尖括号目的地截断后改用普通目的地并复验。合成图床不实际保存文件，返回已公开图片地址供展示，不证明真实RhyPic格式/容量/持久化。
- 初始合成图片链接404，已将fixture改为页面已成功加载的公开图片，并复核实际图片地址。截图`/tmp/rhythm-v3-editor-upload-mobile-proof.png`用于界面记录，不当作真实图床证据。

## 安全与反漏扫

- 只消费既有v3鉴权票据，未增匿名路由/viewSkips、未修改BeforeRequestHandler/验证码/黑名单链。实际风控与真实权限错误仍需真实服务验收。
- 凭据只到其对应服务；图床无Cookie与重定向。目标为服务端已配置完整地址、拒绝用户凭据/片段/查询或错误协议；开发只HTTP回环。浏览器外传的是用户明确选择的文件。
- 不抓远程HTML、不新增服务器抓取URL/SQL/数据库写入；无新增服务端SSRF/注入/越权目标。文件URL仅HTTPS并阻止HTML边界字符，标签作HTML与Markdown转义；本轮未改其他旧上传调用方。
- 批量上传在同编辑器单飞，文件大小/格式服务端最终决定；未假称图床全局限流。取消是停止本地请求与插入，不承诺删除图床已收到的文件或回滚远程副作用。
- 无新增公共静态资源/CDN URL/可枚举目录或上传下载服务器路径；生成资源已扫版本与map。现有代理仍固定目标，未改成任意开放代理。旧接口及全工作区审计未在本轮全量重跑。

## 人工完整链路

本轮无Java/FTL变更，前端build后强刷；如尚未编译上一轮Markdown Java，仍需IDEA编译/重启后联调。

1. 登录v3、进入`/post`或现有共享编辑器，桌面/移动点击上传，选PNG/JPEG或图床允许的文件。检查先POST新票据、再仅带专用票据直传；成功在当前正文插入完整URL，不能再请求旧rhypic票据入口。保存草稿/发布后再读取正文，确认链接、文件名和图片保持。
2. 一次选择两个同名文件，确认顺序和各自结果正确；模拟一个失败，只插入成功项，提示失败数量，用户只重选失败文件。断网/图床坏响应不自动重复上传、不插入空/错误URL。
3. 上传未完时退出、切换账号、载入另一草稿或离开页面，恢复旧响应后不得插入旧图片；同账号刷新令牌仍可完成。外部正文替换应取消旧上传。
4. 上传文件名带括号/方括号/HTML字样及URL带签名查询，确认不会注入HTML或截断链接。拖拽/粘贴/录音裸Blob也需图床允许格式且有扩展名；这些入口尚未真实浏览器逐项验收。
5. 真实后端回归失效Bearer/缺uploads.write、黑名单→/test→解封、开发风控关闭；真实图床大小/格式、生产代理/CORS、部分失败、原图可访问、发布后回读仍待验收。

预览/上传共享编辑器已接v3，内部emoji、@联想/用户查询、标签联想和专栏选择继续；私信/聊天室主体及其他清单仍未完成。无Maven、APP改动、提交、推送或发布。

## 第六十四轮根因澄清

实际Vditor源码确认insertValue内部用template.innerHTML，尖括号地址被当HTML解析；共享编辑器已改用公开insertMD，普通Markdown目的地/括号转义保留。表情与上传已在真实Vditor复验源码地址完整，详见api-v3-client-editor-emojis-work.md。此前“尖括号截断”是观察结果，不应归因于Markdown解析器不支持该语法。
