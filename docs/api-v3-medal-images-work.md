# v3 勋章图片与客户端接入（2026-10-28）

第101批。用户明确确认：profile.read、仅本人有效持有或指定用户当前公开展示、full/mini；初始提议误写PNG，复核旧/gen实际为SVG后用户确认保留SVG、严格清洗、仅作图片展示。新增两GET，总register可达290（289HTTP+WS1），GET147/POST72/PUT46/DELETE24/WS1；全仓837路由/398浏览器引用另计。无新表/迁移/Repository、数据库写、FTL、APP或Maven。

## 实际文件与行为

Rhythm：
- `src/main/java/org/b3log/symphony/processor/ApiV3MedalImageProcessor.java`：GET `/api/v3/me/medals/image`、`/api/v3/users/{userName}/medals/image`，均profile.read；medalId查询参数及size=full/mini。成功SVG二进制、失败通用JSON；no-store/Vary Authorization/nosniff/CSP sandbox。不是公开图片地址，不接用户自定义样式。实际路由测试发现Latke路由前解码会把编码斜杠拆为路径，因此用查询参数完整传既有128单位键，不trim/重复解码；字面%2F、加号、空格、中文仍为原键。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`：总注册增加独立图片处理器；现有勋章GET、经典/gen保持原行为。
- `src/main/java/org/b3log/symphony/service/ApiV3MedalImageQueryService.java`：只读现有User/Medal/UserMedal，参数化SQL、BINARY精确勋章键；本人当前status0/有效持有含隐藏，公开目标status0/3且display/有效期限符合既有公开列表，当前读者仍status0。只读服务器定义name/attr，不调用旧过期清理，不返用户/授予原数据。缺持有/不可公开404，查询或冲突定义503；不是同一长事务快照。
- `src/main/java/org/b3log/symphony/service/ApiV3MedalImageService.java`：只从当前服务器定义构造参数，固定配置renderer、一次编码、图片素材URL精确file.fishpi.cn HTTPS；无用户HTTP自定义render参数。bounded并发、连接/读/总时长与返回体预算、禁重定向；渲染后再核对当前授权/定义，不用缓存绕过权限。只读服务没有数据库事务或commit需求。
- `src/main/java/org/b3log/symphony/util/ApiV3MedalSvg.java`：禁DTD/实体/外部schema与stylesheet、XML深度/节点/体积预算；SVG图形/文字/渐变/部分滤镜/内联样式与SMIL白名单。去除script/foreignObject/导航/事件/CSS规则/外部资源；仅PNG/JPEG/GIF data图片支持有限嵌入。只作为img加载，不支持任意SVG文档。
- `src/test/java/org/b3log/symphony/service/ApiV3MedalImageTest.java`、`tools/api-v3-medal-image-test.mjs`：实际SQL/renderer编排/清洗/loopback HTTP及源码接线共8组，SQLite BINARY转COLLATE BINARY适配；实际MySQL未验。
- `src/test/java/org/b3log/symphony/service/ApiV3MedalImageHttpTest.java`、`tools/api-v3-medal-image-http-test.mjs`：实际Processor/Dispatcher/ApiV3AuthMidware一组，token与环境适配、实际SQLite、渲染替身；无/错Bearer401、错scope403、伪Cookie/apiKey不继承、本人/他人/隐藏/公开/mini、原始ID、响应头及失败边界。不是完整IoC或新服务真实HTTP。
- `API.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`docs/api-route-inventory.md`、`AGENTS.md`及本记录维护。

website_v3：
- `src/api/http.ts`：共享request新增受限SVG二进制模式，沿现有Bearer、401轮换及验证码处理；声明和实际流量均有预算、MIME检查，JSON成功不能当图片。原ZIP预算及授权链保持，通用文件错误文案调整。
- `src/api/v3-medal-images.ts`：保留原始ID、查询参数一次编码、本人/明确用户名两资源、full/mini，客户端最多两条并发队列，取消后不执行排队请求。
- `src/components/medal/V3MedalImage.vue`：只在已登录及明确owner或用户名上下文读取；Blob img、不使用v-html/内联SVG，读取失败保留纯文本并提供单项重试。切ID/owner/mini/token/账号/卸载清图片、取消请求、释放对象URL并拒迟到；SSR与游客不发图片请求。
- `src/components/medal/MedalIcon.vue`：接新图片组件，新增owner/userName上下文；没有已验证上下文只显示文字，不根据旧sysMetal推断归属或回退外部URL/任意CSS。保留类型与说明tooltip，样式转SCSS。当前v3文章/评论/聊天室摘要未提供公开勋章图上下文，本批不伪造此字段。
- `src/components/settings/OwnerMedals.vue`显式owner；`src/views/MedalsView.vue`传当前指定用户名，游客仍显示当前公开文字资料，列表分页不变。
- `src/layouts/AppShell.vue`：删除旧勋章页面会话初始化，退出连接清理改检查实际accessToken；`src/utils/medalSession.ts`删除；`src/api/fishpi.ts`移除无消费者旧/gen图片构造函数。
- `tools/v3-medal-images-test.mjs`：10组实际组件/API/共享二进制传输；`tools/v3-public-medals-test.mjs`给新图片子组件增加隔离适配，原公开列表10组保持，新子组件由专项覆盖。两仓AGENTS/迁移进度及本记录同步。

## 实际验证

- Java25 `node tools/api-v3-medal-image-test.mjs`8组，SQL与真实SVG清洗，实际loopback HttpURLConnection读取/重定向不跟随/超大体拒绝；`node tools/api-v3-medal-image-http-test.mjs`真实Dispatcher/中间件/controller隔离1组，均exit0。SQLite/依赖与token适配、renderer替身不代替真实MySQL、完整IoC/生产renderer。框架反射/SQLite native/Netty Unsafe及旧UserExt注解警告单列。
- 前端tools75脚本841组全部通过，独立认证transport12组通过；`corepack yarn run build`独立exit0，类型与client/SSR均构建。75生成JS版本审计0发现，无map/sourceMappingURL。二进制专项使用真实http模块，覆盖Bearer、MIME、实际/声明超预算和JSON伪成功。
- IAB1280×720生产构建+SSR合成HTTP：公开勋章图片503，文字/描述/总数保留→单项重试失败仍可重试→SVG图恢复；本人/settings/account真实入口显示SVG、原操作按钮保留，未执行写操作。实际捕获本人/公开两路径图片请求均Bearer，无旧/gen。无真实用户/业务服务，其他未提供夹具资源404/私信提示单列；未宣称账号页全部成功。最终查询参数版本重新build后已复验：公开503→单项重试恢复、本人与公开请求均medalId=7&size=full且Bearer。临时页面及服务已清理。

## 安全和反漏扫结论

- 两新GET明确profile.read，无新增匿名入口/viewSkips；无Cookie/apiKey身份提升，实际共享鉴权隔离回归通过。Router BeforeRequestHandler仍全局先执行，黑名单/test/validateCaptcha/计次与开发模式未改；实际新IP/黑名单/解封需真实服务验收。
- 当前本人/目标/定义SQL前后检查，原始ID绑定参数、白名单投影，原配置只供服务器renderer；无授予私有data、用户敏感资料或凭据写入图片/HTML/日志。仅GET、无状态改变，Cookie不作此接口身份，无新增CSRF写面。不存在新数据库写操作。
- renderer仅配置端点，不跟随重定向，客户端不能指定代理目标；素材仅精确受信CDN URL。SVG清洗禁外部资源和执行内容，浏览器只img，直接打开也sandbox/nosniff。高成本渲染有并发/时长/体积/DOM预算；真实生产负载和renderer自身资源抓取策略未审计，不宣称完整压力/下游服务安全验收。
- 无新增公网静态依赖/CDN/上传/文件落盘/公开下载地址；旧/gen和经典皮肤不改，生成资源版本/map审计通过。目录枚举及生产代理部署策略未实测。

## 完整人工验收（真实服务仍待做）

1. 先在IDEA重新编译或重启Java（本批已明确提醒，尚未得到完成确认），确认配置renderer可达；前端用本批build。无本批SQL迁移。分别在1280和390宽度走/settings/account→本人勋章，以及/member/用户名/medals→公开勋章。
2. 本人已登录/有profile.read：有效展示或隐藏勋章均显示图与原名/说明/期限；点击图片重试只读图片、不扣积分或修改展示。full有文字、mini无名称；过期/无持有/无效账号404，图片获取不能删除过期数据。原来的展示/移动操作成功后重新读取当前列表及图片。
3. 另一个合法读者：仅指定用户当前有效展示勋章能读图；隐藏、过期、失效目标404。游客仍可看公开勋章文字，但图片GET必须401；Cookie或旧apiKey不能替代Bearer，缺profile.read403、过期/撤销令牌拒绝。不能用任意userId参数读取他人隐藏图。
4. 用现有目录含中文、空格、斜杠、加号或字面百分号的ID进行查询编码，确认返回相同原键；空白/控制符/无效Unicode/超128值422。不要把ID拼进路径或重复解码。size除full/mini外422，渲染/数据库失败503，不把JSON错响应存成图片。
5. 模拟图片503→文字仍在且可单项重试→恢复SVG；切页、切目标、退出换号、Token轮换及离页制造迟到响应，旧图片不能回填，对象URL释放。SSR与游客不请求私有图片，不用SVG文本插DOM，无旧/gen Cookie请求。
6. 在测试renderer返回带脚本/HTML/事件/外部URL/DTD/实体的SVG，检查响应只剩安全图片内容或503，无外部请求和执行；验证实际奖章样式、内嵌图标、渐变及SMIL效果。CSS keyframes/HTML/外部引用等不支持，需逐种生产样式对照确认，不能假称与经典页面逐像素一致。
7. 开启风控从黑名单IP→/test→validateCaptcha解封，并检查开发关闭风控行为；枚举新GET不绕过鉴权，检查静态/CDN版本、map、目录枚举及上传下载路径无新增暴露。实际MySQL BINARY/表前缀/当前状态并发再核对，renderer超时/超大/302拒绝不发原始内容。

仍待：真实IoC/MySQL/生产renderer与全部既有样式兼容、390px/真机、真实权限撤销与全站风控/部署反漏扫。无推送/发布。下批继续ArticleListView真实分页与专栏目录，Home旧聚合假空/缓存仍需处理；不能宣告v3完成。公开API.md需同步网站API文档文章。
