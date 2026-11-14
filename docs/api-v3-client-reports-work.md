# v3 客户端举报（2026-10-27，第八十五轮）

## 完成范围与实际文件

复用已有 GET /api/v3/report/options（content.read）及 POST /api/v3/reports（content.write/Idempotency-Key），后端合同和285条注册数量保持。旧ReportDialog必须apiKey且理由0..8与新分类不匹配，导致v3会话不可用；已替换为当前Bearer及服务端分类，不回退旧/report。

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-reports.ts`：精确两端点，四目标/理由目录白名单和完整性、标签Unicode/有界、目标正整数ID、理由分类、备注有效Unicode/10240 UTF16/控制字符校验。备注可空；原HTML/空白不在浏览器猜测清洗，交后端规范化并沿原请求/key恢复；成功只投影举报id，不返目录原始多余字段。
- `src/stores/v3Reports.ts`：惰性共享目录、独立读取序号/Abort和错误；单个全局未知命令冻结targetType/targetId/reason/原memo/key，跨SPA恢复，同账号轮换保留，退出/换号清理并拒迟到。首次明确4xx可修改，未知恢复4xx保留原命令；confirmation_required必须明确决定，只有平台尚待确认操作可取消；未知操作无丢弃按钮。receipt不作为目标正文或审核状态。
- `src/components/ReportDialog.vue`：取消apiKey属性门槛，访客无触发，打开才读目录，实际理由单选且没有本地兜底，备注可空/10240限制；分类失败显示错误和重试。form/radio以useId隔离多个实例；未知期间冻结编辑，关闭不丢全局命令，成功关闭及清稿，换目标清理上一个草稿/选择。无定时自动关闭，避免迟到关闭新弹窗。SCSS源码，自适应理由网格/文本换行。
- `src/components/ReportRecovery.vue`：全局原操作恢复及平台确认/取消、编号回执/手动隐藏；不展示举报备注，不把提交成功等同审核已处理。明确整页刷新或退出会丢内存恢复；离开组件/身份变化取消迟到确认。
- `src/layouts/AppShell.vue`：挂载唯一ReportRecovery，离开发起页面仍可恢复。
- `src/views/ArticleView.vue`、`src/components/articles/LongArticleReader.vue`、`src/components/CommentItem.vue`、`src/views/MemberView.vue`：四类文章/评论/用户入口删除旧api-key参数，复用同一弹窗。
- `src/views/ChatroomView.vue`：经典/简约两处按作者ID和accessToken显示他人举报入口，不再用户名比较或依赖auth.apiKey；聊天室类别来自目录，仅49。
- `src/api/fishpi.ts`：删除无消费者的reportContent旧/report调用，保留其他模块。
- `tools/v3-reports-test.mjs`：真实API/Pinia/完整Vue弹窗和Recovery，HTTP/确认/FpDialog框架/轻量DOM隔离适配；不证明真实后台创建举报或处理状态。
- `AGENTS.md`、`docs/V3_MIGRATION.md`、本文和生成build产物。

后端仓仅更新 `UPDATE_LOG.md`、`docs/api-v3-progress.md`、`AGENTS.md`及本文；API.md既有完整举报契约未改。没有Java/FTL/SQL/Maven/APP、提交或推送。

## 执行证据

举报19组通过：精确目录/公开投影，坏目录无兜底，四目标分类/旧编号8拒绝，目标ID和备注Unicode/长度/控制字符，原HTML空白请求保留和空备注，实际弹窗惰性打开/选择/成功关闭，提交后响应中断→关闭/跨SPA全局原键恢复，初次403留稿/恢复403留命令，平台显式确认/取消和离开后迟到审批拒绝，logout/换人/正常轮换及迟到目录，新目录不被旧读取覆盖，目标切换、SSR/访客零请求，五入口及双布局接线/唯一Recovery。模拟幂等服务只证明客户端重试同一请求，不证明真实DB去重。

连同既有涂鸦/上传/聊天室/私信/表态/实时/通知/启动258组、评论奖励11、评论20、文章互动15，共323组通过，无额外输出；日志 `/tmp/rhythm-reports-regression-*.log`。

`corepack yarn run build`类型/客户端/SSR通过，日志 `/tmp/rhythm-v3-reports-build.log`。正式静态审计72JS、版本发现0，无map/sourceMappingURL/sourceURL，报告 `/tmp/rhythm-v3-reports-version-audit.json`；两仓diff --check通过。没有新增CDN、资源目录、上传或下载路径。

浏览器工具不可用，没有本轮真实浏览器、双视口/焦点/Teleport/单选及跨页面完整实际UI验收。FpDialog在测试中是框架适配，不能把内部逻辑测试当作真实遮罩/focus验证；生产HTTP、DB去重、自举报/目标可读权限、真实凭据与全局风控仍待联调。无Java改动，未假设此前后端已重启。

## 安全及反漏扫

只调用已有Bearer分类/举报入口，不接旧Cookie/apiKey/HTML接口，不新增匿名入口/anonymous.viewSkips或改BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor。SSR不读目录/写入，访客不发请求；账号边界清理命令/目录，迟到响应/确认不能影响新身份。真实未鉴权/越权/CSRF/撤销令牌、黑名单→test→validateCaptcha、首访/周期验证、开发关闭风控仍未实际运行。

只发送目标/理由/备注白名单，固定URL不拼任意资源地址，输入有界，客户端无SQL/服务端抓取，未引入注入或SSRF入口。目录标签/目标ID/错误按文本，备注textarea而非HTML，不把举报私有原文显示到全局，后端负责最终规范化与转义。合法类别目录失败不得靠本地常量冒充成功；输入校验中的已确认编号只用于拒错，不生成UI目录。全局仅一个未确认命令避免被另一目标覆盖，真实批量滥用仍由服务端验证。无新增敏感字段/凭据持久化/公开上传下载地址。

构建指纹/map扫描只覆盖本轮生成文件；整个历史工作区、生产CDN/目录枚举和已有上传下载路径仍需发布前审计。

## 完整人工验收

1. 确认既有后端及迁移运行，前端build后强刷登录。普通文章、优选长文、评论、他人主页分别打开举报：先GET真实分类，文章/评论含0..4/49、用户含5/6/7/49，选择理由后空备注也可提交；响应编号显示“提交已确认”，不能声称审核完成。再测含HTML/首尾空白备注，规范化以服务端为准。
2. /cr经典/简约以及移动窄屏，自己消息无举报入口、他人按作者ID可举报；聊天室只有“其他”49。目录失败显示错误/重试、不能提交虚构分类；备注10240上限、非法编号/控制字符/Unicode和无效ID直接API验证422。多个弹窗radio/form不互相串选。
3. 测试环境模拟写已提交但响应断开：关弹窗、切到其他页面，从全局提示按原targetType/targetId/reason/memo/key恢复，核对只一条有效举报。已有同目标未处理举报按服务端返回原编号。未知恢复403/404仍保留原操作，新目标不能覆盖；平台二次确认需显式同意/取消，离开/换号后迟到同意不能提交。
4. 初次明确403/422保留备注可修改；成功后清稿/关闭。改变props目标或换人清原草稿，退出清原命令和目录，正常token轮换不丢未知操作。分类/回执迟到不得串身份；全球提示不展示举报备注，刷新/退出内存恢复限制明确。
5. 实际服务补验他人可读有效目标、自举报拒绝、私密评论/讨论文章、失效/删除目标、未鉴权401/缺scope403、CSRF、撤销凭据、真实幂等/去重及全局风控解封、桌面/移动焦点/遮罩。隔离测试不能替代。

完整v3仍继续推进。API.md当前举报契约已完整，整体发布时仍需同步网站API文档 https://fishpi.cn/article/1636516552191 。
