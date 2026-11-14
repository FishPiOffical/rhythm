# v3 客户端抽屉私信与全局未读（第七十一轮）

2026-10-27。消费既有私信接口与messages频道，284注册保持。独立页/抽屉/顶栏改共用v3私信与未读；聊天室主体仍旧链路，不能宣称整个聊天模块完成。无Java/FTL/SQL/APP/Maven/提交推送。

## 实际文件

客户端根 `/Users/ling/Documents/fishpi/website_v3`：

- 新增 `src/stores/privateInbox.ts`：当前身份一份messages订阅和总未读，只存计数/错误/变更revision及抽屉入口，不存消息正文。当前GET合并在途与一次尾随查询，事件数量不加算未读；普通token轮换重新取票，换身份/退出清空抽屉及目标，迟到旧查询不能回填。窗口focus及明确写完成触发当前读取。
- 修改 `src/components/chat/V3PrivateMessages.vue`：改消费共享未读/revision，不再每个实例各取messages票据；保留独立页全部行为，选中用户ID发只读事件供完整页链接使用。
- 修改 `src/components/chat/WhisperConversations.vue`：私信分支复用实际v3面板，无apiKey门槛；保留拖动/缩放/移动适配、聊天室切换与其旧消息/发送分支。按ID打开完整私信；关闭/换聊天室卸载私密正文，未确认命令仍保留在同一Pinia。换身份清窗口草稿与目标。
- 修改 `src/layouts/AppShell.vue`、`src/components/settings/SettingsDrawer.vue`：顶栏与浮动按钮共用真实总未读与错误；未知无0徽标，错误辅助文字，抽屉初始用户名/peerId兼容原入口。
- 删除 `src/stores/whispers.ts`：旧按用户名拼接私信正文、自动已读、旧chat-channel与旧apiKey计数移除。
- 修改 `src/api/fishpi.ts`：删除无消费者的WhisperMsg、旧列表/历史/未读/已读/撤回包装及私有chatOk。
- 修改 `tools/v3-messages-test.mjs`：原15组继续，新增共享订阅、身份迟到、并发合并、未知/真实0、实际Drawer+Panel+ChatComposer/ID链接/关闭、旧包装移除，共21组。
- `AGENTS.md`、`docs/V3_MIGRATION.md`：更新长期关系与进度。

后端本轮文档：本文件、AGENTS.md、UPDATE_LOG.md、docs/api-v3-progress.md。API.md契约不变。

## 证据

私信21、通知6、实时7、启动9通过；类型检查+client/SSR双构建通过。评论20共享回归与生成JS版本/source map/diff检查以本轮命令结果为准。误调用一个不存在的旧auth runner已查明路径缺失，不作为通过证据；未执行Maven。

浏览器+本地合成HTTP及实际WS升级/首帧：顶栏和浮动按钮都显示真实查询2条未读；主页面与抽屉共一个messages订阅。抽屉实际CDN MarkdownEditor发送合成正文→fixture保存后503→关闭抽屉→完整页恢复同原键/正文/目标→当前历史显示一条。未读fixture503时顶栏可访问名和抽屉明确暂不可用/待查询，恢复后点击抽屉重读，所有徽标/页面同步2；390×844布局通过。夹带事件正文不显示。截图 `/tmp/rhythm-v3-private-inbox-mobile-proof.png`。

没有真实账号发送或真实撤回/已读操作；合成WS不证明Java实时服务授权、过期、跨节点或事务正确。拖动/缩放保留源码和可见按钮，本轮未实际拖动验收。

## 人工完整链路

1. 登录开发网站，查看顶栏私信与浮动聊天未读一致；网络只有共享messages票据/连接，取票不带自定义用户。游客/SSR无私信读取。普通token刷新重取票且保留当前草稿，退出/换号徽标、窗口及目标清空。
2. 从浮动“打开聊天”进入抽屉，选用户/已有会话/文件助手；核对当前历史与分页、Markdown工具及发送。无旧apiKey也能操作私信。打开完整页应保留所选用户ID，停用用户历史不依赖用户名跳转。
3. 在可丢弃的开发会话发消息，模拟提交后响应丢失；关闭抽屉，在完整页恢复，核对同键同目标同正文、不重复。也反向从完整页未知请求到抽屉恢复，切其他用户不得清新草稿。确认成功后状态读取失败只重读。
4. 抽屉显式已读/撤回成功后，完整页及徽标通过当前GET刷新，不按changed回执或事件数量修改计数；读取本身不自动已读。503未读必须显示未知，恢复重读真实0或正数。
5. 切聊天室再回私信、关闭再开、拖动及两角缩放、390px移动滚动/编辑器/全页入口。关闭应卸载正文，未知命令仍可同SPA恢复；整页刷新会丢失本地未知信息，先核对原操作。
6. 检查网络没有chat/get-list、get-message、has-unread、mark-as-read、chat/revoke或chat-channel。聊天室当前仍旧通道，不把它误记为私信回退；此模块另迁移。

## 安全与未验

无新匿名入口/viewSkips/数据库写/CDN/上传下载返回路径。共享状态不保存私信正文；读取与发送继续原Bearer边界，当前owner/peer绑定、响应白名单和HTML清洗契约保持；首帧票据不进URL/持久存储，事件只失效。删除旧客户端自动已读/正文拼接链，减少双通道与旧用户名归属；不改后端旧接口兼容契约。没有新增SQL/远程抓取/注入/SSRF入口。弹窗几何仅本地非敏感偏好；身份切换关闭私密窗口。

真实Java未鉴权/越权、messages权限撤销/到期/重连与服务端限流、私信事务/消息未读、黑名单/test/validateCaptcha、匿名链路/生产CDN目录枚举未运行验证。无本轮Java更改；真实服务联调前仍需确认此前Java已IDEA编译/重启。抽屉聊天室及聊天室主体、剩余旧接口消费者和全站验收继续，v3总目标未完成。
