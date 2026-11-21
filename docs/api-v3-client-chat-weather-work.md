# v3 客户端聊天室天气卡片（第八十一轮，2026-10-27）

## 范围与实际修改

接入既有 POST `/api/v3/chat/weather`（content.write），固定五天客户端数据，不新增权威天气查询或远程抓取。HTTP契约沿API.md原定义，不修改Java/FTL/SQL/Repository，路由仍284（HTTP283、WS1）；无Maven、APP、提交推送。

客户端文件：

- `src/api/v3-chat-weather.ts`：city/description/five days白名单；文本Unicode/控制字符/HTML、strip后64/128 UTF-16；20个现有天气代码；0001..9999有效日期、连续恰好五天、整数温度-100..100且min<=max。不接受温度字符串/空值/小数/NaN/Infinity，拒空洞/缺项/数组伪装日对象，失败不截断。日历计算明确UTC与完整年份，避免Date.UTC的0..99年偏移和本地DST间隔。POST原内容/key，回执仅id/createdAt。
- `src/stores/v3ChatWeather.ts`：冻结city/description/five typed days/key，跨SPA恢复，同账号轮换保留，换人/退出清并拒迟到；首次明确4xx可修正，未知恢复4xx仍保原命令。平台二次确认明确决定，成功仅回执，独立GET当前消息后入timeline；详情失败只GET恢复，旧详情不写新操作，新未知发送不显示旧回执。
- `src/components/chat/ChatWeatherEditor.vue`：城市/说明/首日及五天天气与温度表单。日期由首日连续生成，天气代码和温度初始留空，不造预报；原命令未知时冻结全部行，确认成功且原输入仍匹配才关闭清理。移动窄屏天气选择占整行、温度两列，未新增卡片套卡片。仅SCSS源码。
- `src/components/chat/ChatWeatherRecovery.vue`：共享结果未知、原操作恢复、平台确认/取消及详情GET重试。离开/换人取消迟到确认，刷新/退出丢本地恢复的限制明确。
- `src/views/ChatroomView.vue`：经典/简约工具栏和独立表单、共享恢复接通；天气面板与音乐/红包/弹幕/涂鸦互斥，不把天气当普通消息命令。既有ChatCard根据当前DTO展示天气，本轮未改它。
- `tools/v3-chat-weather-test.mjs`：真实生产API/Pinia/store/timeline及完整Vue表单/恢复/卡片；HTTP、对话框、轻量DOM适配。20代码与当前后端Java常量提取结果逐项比对，不把源码字符检查冒充Java运行测试。
- 客户端AGENTS.md/docs/V3_MIGRATION.md及build产物，后端AGENTS.md/UPDATE_LOG.md/docs/api-v3-progress.md和两仓本文。API.md已有完整天气契约，本轮无接口变更。

## 执行证据

202组全通过、无额外警告/错误输出：天气16、音乐16、弹幕13、话题13、上下文13、timeline17、撤回11、私信21、普通发送15、原文13、在线13、表态16、实时10、通知6、启动9，日志 `/tmp/rhythm-weather-regression-*.log`。

天气覆盖精确POST/key/投影、跨月/年/闰年及0001/9999边界、20代码对应、无效文本/温度/日期/缺项/坏回执、真实表单初始空值→五行类型输入→发送→当前GET、保存后响应中断的原键恢复、详情失败GET-only、平台二次确认、重复/身份/轮换/迟到/SSR/访客、未知原输入冻结恢复、真实卡片纯文本城市说明和五日图标、旧详情隔离、新未知不冒用旧成功。隔离测试不证明真实服务写入或发言资格。

`corepack yarn run build`：类型、客户端及SSR构建成功，日志 `/tmp/rhythm-v3-chat-weather-build.log`。生成72个JS、版本发现0，无map/sourceMappingURL/sourceURL，报告 `/tmp/rhythm-v3-chat-weather-version-audit.json`；两仓diff --check。未新增CDN依赖/静态目录/上传下载路径。

当前浏览器工具不可用，本轮没有浏览器、真实日期控件/数字输入、移动视口、真实服务权限/风控/幂等/广播或生产数据验证；不能沿用前批次浏览器证据算本轮通过。

## 安全与边界

沿既有Bearer/content.write和写保护，当前owner来自认证状态，正文白名单及请求/身份隔离。无新匿名入口，不改anonymous.viewSkips、BeforeRequestHandler、AnonymousViewCheckMidware或CaptchaProcessor；SSR/访客不发送，Cookie/apiKey不冒充Bearer。实际未鉴权/越权/凭据撤销/CSRF拒绝、黑名单→test→解封、首访/周期验证码及开发关闭风控仍需服务端验收。

文本纯文本渲染，天气代码固定目录，不将用户输入拼进任意远程查询；无服务端SSRF/SQL/数据库写事务/敏感账号字段。此为用户输入的天气分享，不保证预报真实性；不自动填今日天气、零温度或晴天，空字段不能发布。真实批量滥用限制和并发/跨实例广播未测。静态资源扫描仅本轮产物，全工作区发布前审计、生产CDN/目录枚举和存量上传下载路径仍待。

## 完整人工验收

1. 确保本地已有v3后端及前置迁移运行，前端build并强刷；登录→/cr→经典/简约「天气」→城市/可空说明、首日、五天天气及整数温度→发送。确认只POST v3/weather的三个顶层字段与原key，随后GET当前消息，五日卡片正确显示且表单关闭；浮窗/历史共享当前消息。
2. 桌面和移动窄屏验证日期控件与温度两列不越界，代码/温度初始为空。选12月30日或闰年2月27日核对连续日期；过去/未来合法日期均可分享。清空任一温度/天气、min>max、温度-101/101/小数、非法日期、文本HTML/控制字符或超限，拒绝发布且保留输入。直接API补测温度字符串、四/六天和不连续日期422。
3. 测试环境模拟已提交但响应503，原表单冻结；导航离开再回，恢复原城市/说明/五天/key，只保存一条。平台要求再次确认时显式批准/取消，未知恢复403/404仍保命令。确认后详情503仅GET重试，不再次POST；当前消息删除/不可读不能用回执假造正文。
4. 发送/当前读取/二次确认期间退出或换账号，旧回执/正文/确认不能写回；同账号正常token轮换保留原恢复操作。连续发送中旧详情迟到不改新消息，新一次结果未知不能显示上次成功回执。文字尖括号/城市说明按文本展示，图标只走固定代码。
5. 真实服务另验发言资格、禁言、未鉴权/越权/撤销凭据、CSRF、黑名单/验证码、批量滥用及跨实例事件；本轮隔离测试不能替代。

天气完成客户端源码和隔离验证。思过崖口径仍待用户确认；红包及完整v3运行验收继续，目标未完成。
