# v3 客户端聊天室音乐卡片（第八十轮，2026-10-27）

## 范围与实际文件

接既有 POST `/api/v3/chat/music`，content.write、幂等写保护及当前消息GET沿后端既有规则，不变更HTTP契约，API.md无需新增定义。后端仍284（HTTP283、WS1）。无Java/FTL/SQL/Repository、Maven、APP、提交推送。

客户端实际修改：

- `src/api/v3-chat-music.ts`：四字段title/from/source/coverURL严格投影；有效Unicode、控制字符/HTML拒绝，Java strip对应空白、128/64 UTF-16文本；source为1..32位ID或合法HTTPS、封面可空HTTPS，2048边界，拒凭据/片段/反斜杠/坏转义/非法域名端口。完整签名query保持。元数据回执白名单，不返内部字段；不调用旧普通消息命令或服务器抓取远程资源。
- `src/stores/v3ChatMusic.ts`：原四字段/key冻结并共享跨SPA；未知恢复4xx保留原命令，平台二次确认必须明确决定，正常token轮换保留，身份变化/退出拒绝迟到回执。成功独立GET当前消息后投影共享timeline，GET失败可单独重读，不再POST。新命令清上一回执/当前读取，晚到旧GET不覆盖新操作，不把成功回执当当前正文。
- `src/components/chat/ChatMusicEditor.vue`：四字段受控表单，明确发布按钮，原命令未确认时冻结；确认成功且原输入仍匹配才清理/关闭，身份变化清草稿。沿普通发言资格，不造额外扣费规则。
- `src/components/chat/ChatMusicRecovery.vue`：共享未知结果/二次确认/取消、当前消息读取恢复；离开时取消迟到确认。只在第一方当前登录身份显示，不把apiKey当Bearer。整页刷新/退出丢本地恢复信息，提示先核对原操作。
- `src/views/ChatroomView.vue`：经典与简约工具栏、独立表单和全页恢复共用状态；音乐面板与红包/弹幕/涂鸦互斥，移动沿两套布局共享。未改普通消息和红包链路。
- `src/components/ChatCard.vue`：数字歌曲ID以安全新页链接打开网易云，避免当本站相对音频路径；HTTPS音频点击播放，历史安全HTTP地址仍保留原播放能力（浏览器可能阻止混合内容），不改变新写入只允许HTTPS的规则。标题/来源Vue文本输出，无v-html；拒绝危险播放协议/凭据。播放Promise序号防旧请求覆盖新选择，拒绝播放和媒体error均有明确提示，仍使用既有共享播放器。样式改为SCSS源入口，不手改CSS/min.js。
- `tools/v3-chat-music-test.mjs`：真实API/store/timeline/完整Vue编辑、恢复和卡片渲染/媒体交互专项；HTTP、对话框、轻量DOM及Audio为测试适配，不连接真实账号或外部音频。
- 两仓AGENTS.md、后端UPDATE_LOG.md/docs/api-v3-progress.md、客户端docs/V3_MIGRATION.md及本文；dist/client/server由build生成。

## 执行证据

186组通过且无额外警告/错误输出：音乐16、弹幕13、话题13、上下文13、timeline17、撤回11、私信21、普通发送15、原文13、在线13、表态16、实时10、通知6、启动9。日志 `/tmp/rhythm-music-regression-*.log`；音乐覆盖四字段/URL/文本边界、实际表单发送、提交后丢响应的原键恢复、二次确认、读取失败GET-only、轮换/换人/迟到/重复/SSR/访客、原输入恢复成功关闭、实际卡片数字链接/纯文本/显式播放/暂停/失败和乱序Promise、旧详情隔离、新未知操作不冒用旧回执。

`corepack yarn run build`：类型检查、客户端与SSR构建通过，日志 `/tmp/rhythm-v3-chat-music-build.log`。生成72个JS版本检查0发现、无map/sourceMappingURL/sourceURL，报告 `/tmp/rhythm-v3-chat-music-version-audit.json`；两仓diff --check。没有新增CDN库或公开上传/下载接口。

当前没有可用浏览器控制工具，本轮未进行真实浏览器/双视口验收。实际音频播放、网易云页面可达性/地区版权限制、真实服务发言资格/禁言/积分/幂等/广播、生产源数据及并发均未运行验收。隔离Audio验证的是客户端调用与状态，不证明远程音频可播。

## 安全与限制

沿既有Bearer端点，不新增匿名入口，不改anonymous.viewSkips、BeforeRequestHandler、AnonymousViewCheckMidware或CaptchaProcessor；当前owner来自认证store且序号/身份边界保护迟到。真实未鉴权/越权/凭据撤销/CSRF拒绝、黑名单→test→解封、首访/周期验证码及开发关闭风控仍未验；不能用本轮隔离组件替代。

新写URL严格HTTPS/有界且不发送到服务端抓取；直接浏览器媒体/封面属于已公开卡片内容，不携带transport的Bearer。数字外链仅固定站点+数字，noopener noreferrer；文本插值及字段白名单防HTML与敏感字段传播。无SQL、写事务、新表或任意下载路径。批量发送限制和当前可读性由既有后端重验，真实滥用/并发未测。静态扫描仅覆盖本轮构建；全工作区发布前审计、生产CDN/目录枚举和存量上传下载路径仍待。

共享播放器沿旧生命周期；离开卡片不会自动结束共享播放，本轮不声称完成跨页面播放列表、系统媒体会话或版权音源解析。

## 完整人工验收

1. 确保本地现有v3后端与前置迁移已部署，前端build并强刷；登录→/cr→经典/简约「音乐」→填标题、来源说明、网易云ID或HTTPS音频与封面→发送。确认仅POST v3/music四字段和请求键，随后GET当前消息；编辑器只在成功时关闭，共享历史/浮窗显示当前GET卡片。
2. 测试标题128/129 UTF-16、emoji边界、可空来源/封面；HTML、控制字符、空标题、非数字ID/坏URL/HTTP新源/凭据/片段/无效端口返回422，不发布。完整签名query不被删。桌面/移动窄屏都验证表单与卡片不越界。
3. 测试环境模拟提交已成功但响应503，导航离开再回聊天室，恢复原四字段和原key，不造第二条；平台二次确认显式批准/取消，未知恢复403/404保留原命令。确认后详情503只GET重试，不再次POST；模拟当前已被删除/无权读取时不能展示回执假正文。
4. 点击数字ID卡片应打开网易云固定歌曲页，不请求本站数字音频地址；HTTPS源仅明确点击才播放，暂停/换曲/播放拒绝或网络媒体错误提示正确，快速换曲时迟到Promise不改变新选择。版权/地区/音频格式与浏览器限制可能导致真实播放失败，应保留明确失败状态。
5. 发送/详情/确认期间退出或换账号，旧响应/旧确认不能写回；普通token轮换保留原操作。连续成功后下一次结果未知，不显示上一条成功为本次结果；保留后端未鉴权/越权/黑名单/验证码/CSRF/批量滥用及跨实例广播的真实验收。

思过崖旧展示按用户名，实际禁言按用户ID读取，两者不等价；已向用户确认v3读取口径，确认前不实现新接口。其余天气/红包和整体v3运行验收继续，目标未完成。
