# v3 单项用户关注与客户端操作

2026-10-26。新增本人对单个用户的关注状态GET，接入网站主页与悬浮资料卡的明确关注/取消关注操作。后端现有PUT/DELETE语义和旧接口不改；没有新表/迁移，后台、标签/文章收藏订阅、私信和聊天室不是本批交付范围，完整v3目标仍继续。

## 实际文件

Rhythm根：/Users/ling/Documents/GitHub/rhythm。

| 文件 | 行为 |
| --- | --- |
| src/main/java/org/b3log/symphony/service/ApiV3FollowQueryService.java | 新userState：当前actor/target均有效与本人关系EXISTS在单SQL读取，仅投影targetId/following，不吞DB错误为false |
| src/main/java/org/b3log/symphony/processor/ApiV3FollowProcessor.java | 新GET /api/v3/me/follows/users/{targetId}、profile.read；抽出同一registerRoutes便于实际路由测试，其他路径/门禁保持 |
| src/test/java/org/b3log/symphony/service/ApiV3FollowStateTest.java | 真实SQL/服务/Dispatcher/Auth/Controller四组 |
| tools/api-v3-follow-state-test.mjs | Java25隔离测试及--mysql一次性MySQL同组执行，明确用户/认证IO适配 |
| API.md、UPDATE_LOG.md、docs/api-v3-progress.md、docs/api-route-inventory.md、AGENTS.md、本文件 | 单项契约、用户更新、当前总数和长期边界 |

网站客户端根：/Users/ling/Documents/fishpi/website_v3。

| 文件 | 行为 |
| --- | --- |
| src/api/v3-user-follow.ts | GET当前状态、PUT/DELETE明确设置；校验ID/幂等键及状态响应，白名单投影 |
| src/stores/v3UserFollow.ts | Pinia仅内存保存未确认命令，跨SPA同key/同intent恢复；按稳定身份边界清理，普通token刷新保留；同目标在途去重，成功发目标失效事件而不缓存历史following |
| src/components/user/UserFollowButton.vue | v3摘要解析目标ID→本人关系读取，明确设置后GET当前状态；未知结果重试、成功后读错只读重试；self/SSR不操作，读者/目标隔离；单一稳定button节点保焦点，aria-disabled/busy阻止重复操作 |
| src/views/MemberView.vue | 替换旧canFollow/toggle/API-key按钮，其他主页资料/统计仍原模块 |
| src/components/user/UserHoverCard.vue | 共享v3关注按钮、稳定读者边界清旧资料缓存；基础资料仍旧读取，不能称整张卡片已迁移 |
| src/api/fishpi.ts | 删除无消费者旧followUser/unfollowUser包装 |
| tools/v3-user-follow-test.mjs | 六组实际TS/Pinia/Vue/SSR行为测试，包含稳定按钮节点断言 |
| README.md、AGENTS.md、docs/V3_MIGRATION.md、docs/MISSING_APIS.md | 当前迁移范围和剩余项 |
| dist/client、dist/server（生成产物） | corepack yarn run build生成，未手改CSS/min.js |

没有APP、经典FTL/PC/mobile资源、后端写核心或数据库迁移变化；本Vue共享组件适配两种视口，不需要对未使用它的classic皮肤做对应改造。没有执行Maven、提交、推送或发布。

## 已执行证据

- 后端状态四组SQLite及同四组MySQL8.4通过：一条生产SELECT、本人/类型/重复关系隔离，当前actor/target有效性及ID注入拒绝，DB异常不伪造未关注，读取无写；实际Dispatcher/Auth/controller测试Cookie/query-only 401、scope403及本人白名单/no-store。Token authenticate、基础UserRepository、非本批写入/其他查询方法是明确适配；不是完整IoC或真实凭据服务证明。MySQL临时随机loopback端口，结束已移除。
- 旧tools/api-v3-follow-test.mjs回归通过：SQLite关系SQL与四组FollowMutation，事务/目标检查、owner/type隔离、历史重复及失败回滚；不是本批完整写服务或全局鉴权MySQL并发证明。
- 客户端六组通过：API路径/严格DTO、原key与原intent恢复、阻止未知结果时相反命令、普通token轮换保留、换号/退出隔离迟到结果；实际组件跨挂载恢复未知请求、历史receipt之后GET当前值、成功后GET失败只重读、SSR/self无请求。额外断言同一button节点跨状态不替换，防止资料卡失焦；HTTP/auth/router/目标摘要解析为显式夹具，组件与Pinia实际编译运行。关系读取5组、共享昵称样式8组回归通过。
- corepack yarn run build通过vue-tsc、client/SSR构建。66个生成JS定向检查sourceMappingURL及Vue/@vue版本头零命中，无map文件；另按约定执行Rhythm tools/js-version-audit.mjs扫描同一产物目录，66文件/0命中，报告/tmp/rhythm-v3-client-follow-js-version-audit.json。不是全站历史CDN或所有依赖指纹扫描。
- IAB本地合成服务验证主页：PUT已提交但503→“重试本次关注”→同key回放→当前GET已关注；DELETE成功、后置GET503→只出现重读状态→恢复未关注。夹具日志一次PUT、一次DELETE，不重复提交。1280×900与390×844两个viewport截图已人工看过，布局和按钮正常；不是实际移动设备。
- 继续真实列表→键盘聚焦→悬浮卡片操作：原按钮替换导致失焦/关闭的问题已复现并修复；修复后重试→取消→后置GET失败→重读均保持同一个聚焦按钮和打开的卡片。Hydration日志为空。卡片基础资料由本地旧userinfo夹具提供，不能因此认为该读取已迁移。

日志：/tmp/rhythm-v3-follow-state-tests.log、follow-state-mysql-tests.log、follow-state-regression.log；/tmp/rhythm-v3-client-follow-{tests,build,fixture,card-fixture,server}.log及js-audit.json。截图同前缀desktop-proof.png、mobile-proof.png、card-proof.png。浏览器/临时服务由本轮创建，结束清理；没有真实账号写入。

## 完整人工链

1. 本批涉及Java。先由IDEA重新编译或重启Rhythm，确认新读取路由及完整IoC可启动；没有本轮真实重启确认，不能把旧8080行为当成本批结果。无需新增SQL，但此前v3基础迁移必须就绪。前端指向开发8080并使用构建产物强刷。
2. 登录隔离测试账号，进入另一用户主页；状态读取后按真实状态显示关注/取消。依次关注、重新GET、取消、重新GET，核对本人关注/对方粉丝真实记录及既有通知；不依赖旧canFollow或把Bearer塞apiKey。自身或未登录不显示操作按钮。
3. 从关注/粉丝列表以头像或用户名打开悬浮卡片，鼠标移入/键盘聚焦均可操作；提交中保持焦点并阻止双击。状态、错误、重试不能让卡片自动消失。Escape、移出/失焦按原约定关闭；触屏不误弹卡片。
4. 代理模拟PUT提交成功但响应丢失：页面显示待确认，离开/返回或关闭/重开卡片仍只重试原intent/key，不能反向切换；重放后GET显示当前状态。如果期间另设备已改关系，历史回执不得覆盖GET现状。整页刷新不承诺恢复内存命令。
5. DELETE成功后让GET失败，提示“操作已确认，状态读取失败”，按钮仅重读，不再DELETE。首次状态读取失败也不伪造未关注；scope不足、失效目标、坏ID/响应须显示错误。关闭/换目标/退出/换号后，迟到读写响应不写回旧页面；普通刷新token保留未确认命令。
6. GET未鉴权401、错误scope403、目标不可用404、无效ID422；其他身份只能查自己的关系。PUT/DELETE继续既有follows.write/幂等/写门禁与事务。新IP、黑名单/test、Captcha解封、anonymous.viewSkips及开发开关需完整运行回归。
7. PC宽屏/移动设备验证主页布局、按钮可点击、未知结果文案换行、资料卡位置/键盘/触屏；已做两种浏览器viewport但未做实际设备/生产代理。其他标签/收藏/订阅、旧资料与统计、私信/聊天室仍按独立队列继续。

## 安全与反漏扫结论

新增GET明确Bearer/profile.read，无匿名例外；actor仅从认证身份取，SQL绑定actor/target/type/status，单SQL只返回targetId/boolean，不暴露其他人的关注或用户高敏字段。只读查询的实际部署索引与大数据量性能尚未核对，无生产压测。无写库、抓取远程地址/SSRF、上传下载或HTML渲染。新客户端写入只用Authorization及固定v3路径，不新增Cookie写/CSRF例外，不向URL/图片/日志写Bearer；目标摘要与状态分开验证。未确认命令只在Pinia内存，按读者代际清理，SSR不读共享身份。长度/ID/响应类型有界，写端仍既有预算，未做真实压测。

BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor和anonymous.viewSkips未改。原旧关注写后端和FTL未改，不宣称修复了存量写核心所有并发/鉴权问题。静态生成产物检查只限上述66JS；全站CDN版本/目录枚举、旧sourceMappingURL与上传下载路径、完整全局匿名/黑名单/验证码/CSRF/CORS、实际账号撤权、真实IoC与部署MySQL写并发均未验证，不默认通过。API.md须同步网站API文章。
