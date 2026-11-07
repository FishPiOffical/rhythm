# v3 客户端聊天室原文与引用读取（第七十二轮）

2026-10-27。消费既有GET /api/v3/chat/messages/{messageId}，284注册保持。本轮完成读取入口与数据基础，并修复实际联调暴露的本地偏好水合问题，聊天室主历史/普通发送/撤回/实时与特殊能力仍待迁移，不宣称完整引用发送链完成。无Java/FTL/SQL/APP/Maven/提交推送。

## 实际文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- 新增 `src/api/v3-chat-messages.ts`：严格消息id/时间/类型/作者/正文/卡片投影；详情精确目标、no-store。unavailable清作者正文；普通Markdown和清洗HTML分列，卡片仅已公布字段，不输出存储运输信封。只读DTO可供后续主历史使用。
- 新增 `src/composables/useChatMessageSource.ts`：原文弹窗、引用、独立错误/序号/AbortController；当前请求/当前身份才能回填，收起/换目标/退出/卸载清除，正常token轮换保持。多行Markdown逐行引用，作者文本进入引用标题前转义。卡片显示标明公开摘要，不伪称原始存储数据。
- 修改 `src/views/ChatroomView.vue`：经典/现代两处入口用accessToken，引用只传目标ID、从当前详情获取正文/预览，不采旧列表html/作者作为引用证明；原文打开状态独立，合法空文仍有弹窗，失败不混进正文且可关闭重读。显示引用读取错误。保留原发送链与其他功能待下一批迁移。
- 修改 `src/api/fishpi.ts`：删除旧fetchChatRaw及无消费者requestText导入。
- 新增 `tools/v3-chat-source-test.mjs`：13组实际API/生命周期、严格目标/敏感字段剥离、取消/迟到/身份/SSR/Markdown引用专项。
- 修改 `src/stores/chat.ts`、`src/stores/appearance.ts`、`src/layouts/AppShell.vue`：首帧使用统一SSR默认，挂载后恢复本地布局/主题偏好，修复现代布局和浅色主题导致的水合节点/类不一致；已有偏好保留。
- `AGENTS.md`、`docs/V3_MIGRATION.md`：长期数据路径及进度。

后端本轮文档：本文件、AGENTS.md、UPDATE_LOG.md、docs/api-v3-progress.md。API.md既有契约不变。

## 证据

原文13组、表态16组、启动9组回归通过；类型检查及client/SSR双构建通过。diff检查及静态JS版本/source map扫描以本轮命令结果为准。

浏览器实际完整ChatroomView+合成HTTP：旧列表展示“旧列表展示的合成消息”，只有Bearer无apiKey仍出现两处读取入口；点击原文得到“当前v3原文 **Markdown**”，引用预览显示“当前v3清洗后的引用预览”，证明没有复用旧列表正文。现代桌面与390×844经典预览目视通过；恢复测试前modern样式后关闭。初验发现本地现代布局/浅色主题水合不一致，修复后新建页面验证偏好恢复且浏览器warn/error日志为空。未点发送/红包/撤回/收费操作，没有真实账号消息。截图 `/tmp/rhythm-v3-chat-source-mobile-proof.png`。

## 人工链与边界

1. 确认此前Java已IDEA重新编译/重启，本轮无Java改动。登录开发聊天室，用Bearer打开普通消息“原文”，网络应GET对应v3详情，不再cr/raw；正文以文本节点展示Markdown，关闭后请求应取消且迟到不重开。
2. 点击“引用”，确认作者、原Markdown及预览来自当前详情，列表旧HTML不可替代；切换新引用目标后旧响应不得覆盖。多行引用每行前有>，作者特殊字符不得插入Markdown结构。
3. 卡片仅显示标明的公开摘要，无内部领取者、随机份额、凭据；unavailable/404/403/503显示读取失败而非假正文。合法空原文仍可开关弹窗；关闭后重新点击可重读。
4. 经典和现代、390px移动重复；退出/换账号/卸载清正文与引用，普通token轮换不丢已选引用。聊天室发送仍旧链，本轮不以发送成功作为已验结果；后续迁移普通发送后需补引用→发送→历史回读的完整验证。

## 安全与未验

无新增匿名路由/viewSkips/数据库写/CDN URL/上传下载路径。读取走既有Bearer，精确目标和白名单；原Markdown/卡片摘要用文本，预览仅既有v3清洗HTML，引用作者转义。控制器按身份/序号丢弃迟到结果，无本地存储/正文日志、无新增SQL或远程抓取/SSRF入口。

完整Java权限/失效/停用、黑名单/test/validateCaptcha、匿名链路、生产静态目录枚举未运行验收；主历史、旧WS/节点、普通发送/撤回/话题/弹幕及红包仍旧链，不能宣称聊天室已迁移。合成HTTP和隔离测试不证明真实服务端渲染或端到端发送。
