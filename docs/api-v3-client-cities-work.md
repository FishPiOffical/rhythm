# v3 客户端同城（第88轮，2026-10-27）

## 实际文件和行为

复用 GET /api/v3/cities/{cityName}/users 与 /articles，无后端接口/Java/FTL/SQL/APP 改动，无 Maven。API.md 当前契约保持。

website_v3 实际修改：

- `src/api/v3-cities.ts`：城市 trim/Unicode/64 UTF-16 校验，单次路径编码；公开用户/简介及文章白名单，真实20条分页、四种文章排序，重复/坏响应拒绝。
- `src/composables/useCityFeed.ts`：用户与文章独立数据/加载/错误/页码；原页重试，切城和身份边界隔离，正常同账号令牌轮换重读当前页。
- `src/views/CityView.vue`：移除 apiKey 门槛、近50条清风过滤、旧 SWR 和 v-html；改为同城用户和文章两个同级区块。用户昵称沿 VipNickname，头像沿 avatarUrl 与全局资料卡；文章沿 ArticleFeed。桌面/移动共用，SCSS 源码经构建。
- `tools/v3-cities-test.mjs`：真实 API/composable/页面/ArticleFeed/SSR 隔离测试。
- 两仓 AGENTS.md、本工作记录和进度文档；后端 UPDATE_LOG.md 补公开客户端记录。

访客可以按现有可选Bearer读取，不能把缺权限/读取错误伪装成登录门槛或0条结果。用户、文章分别展示服务端 total，不由当前列表长度推算全城人数。文章排序只重置文章第一页，不影响用户当前页。失败清旧条目和旧总数，原页重试；失败不影响另一资源，允许返回第一页。空结果保留真实0，不回退模拟数据。

Vue Router 已解码 path params，页面禁止再次 decodeURIComponent；城市名中 %2F 等保持字面值并在 API 路径编码一次。城市名称沿既有服务端字符规则，不新增城市目录或自定义地名白名单。用户简介纯文本，匿名文章响应拒绝，头像沿共用安全URL投影，不恢复私密/匿名身份。

## 验证

同城13组专项通过：精确路径/一次编码/分页、城市输入边界、白名单/重复/坏分页/头像方案、匿名文章拒绝、真实双列表及独立翻页/排序、各资源503原页重试、切城和退出迟到隔离、轮换保页/换人归一、访客可读、SSR不额外请求/不造0、空结果和移除近似链路。

tools目录62个脚本全部退出0，共644组。类型/客户端/SSR构建通过；73个生成JS版本发现0，无map/sourceMappingURL/sourceURL，两仓diff --check通过。日志 /tmp/rhythm-v3-cities-tests.log、/tmp/rhythm-cities-regressions.json、/tmp/rhythm-v3-cities-build.log、/tmp/rhythm-v3-cities-version-audit.json。

实际Chrome以本轮生产构建+SSR及自有本地HTTP夹具完成：

- 1280×720：21名用户/21篇文章，用户第二页503→旧用户/总数清除，文章仍可读→原p=2重试→用户21及文章21均末页，下一页禁用。请求记录证明相同用户第二页 GET 重试。Evidence `ev_cf429c2883a795b45b855eb98d14ae1768e68e009d1b55659f591a6b9d1778d1`；前置错误截图 `ev_9120e798e13f6d73b79b3616ab41dd53b69a6c5792a88e1516d115b360d6fc2f`。
- 390×844：用户末页1/21、两资源分别显示21总数、切换文章评论数排序，页面和分页无横向溢出，简介中的HTML标签按文本。Evidence `ev_0469df52df27626fab5dcf0e0ae097732f11c338a6f6b7cd318fdff39100bd9c`。
- 没有page-error或hydration warn。控制台包含夹具故意返回的访客refresh401和第二页503，不宣称所有console error为空。侧栏清风提示为本夹具未模拟对应分页协议，不属于同城功能验收。

浏览器工具本轮已可调用，以上只证明合成后端下的真实前端链路，不替代 Java IoC、位置隐私SQL、真实凭据/风控/多实例验收。没有访问生产写接口，也未假设此前后端已重编或重启。测试夹具为 /tmp/rhythm-city-browser-fixture.mjs，本轮完成后停止自有进程。

## 安全与反漏扫

已反查 ApiV3CommunityProcessor：两个GET分别 optionalProfile/optionalContent 且挂 AnonymousViewCheckMidware，总注册可达。Query用户按当前公开位置和有效账号；文章选择按文章城市及作者公开位置/有效账号，并沿公共可见性条件排除匿名与不可列内容。客户端不自行从清风/主页推断位置。不改 anonymous.viewSkips、全局黑名单或验证码解封链。

没有新写入口，无新增Cookie/apiKey例外或凭据持久化。参数有界、路径编码、固定GET和公开字段投影，简介/错误文本插值，不增加注入/SSRF/XSS入口；用户/文章身份变更拒绝迟到结果。真实未鉴权、缺scope、越权、已撤销凭据、CSRF/批量滥用及黑名单→/test→验证码解封/周期检查仍需实际服务验证，不能从夹具推断通过。

没有新增CDN、公开上传/下载地址、静态目录。产物扫描覆盖本轮构建，生产目录枚举、历史CDN和上传下载路径未实际重扫。

## 完整人工测试步骤

1. 构建前端，强刷 /city/上海。访客及登录者均可按授权读取用户和文章；只返回公开位置的有效用户及公开署名文章。用户头像/昵称跳转对应主页，悬浮卡与VIP样式按当前公开信息；简介中的HTML按文本。
2. 让两资源各至少21项：分别翻第二页，确认20+1/21及末页禁下一页；用户翻页不动文章页，文章切latest/active/hot/comments归第一页且用户页保持。不同类型文章链接均能进入当前文章阅读链。
3. 两资源分别模拟第二页503：旧条目和该资源总数不保留，另一资源可操作；重试仍同一页、同一排序，返回第一页可恢复。合法空结果显示0，超出末页可回首页，不使用假用户或清风近似数据。
4. 慢响应中从上海切北京，旧响应不回填；城市名含百分号或 %2F 字面值不得二次解码、斜杠在API参数中编码，空/超64/无效Unicode与控制字符失败。退出/换号清旧资料，同账号正常轮换重读当前页。
5. 桌面与390px检查列表、分页、排序、键盘及头像回退，SSR首屏挂载不出hydration warn。真实服务补验公开位置关闭、失效账号、匿名/讨论/不可读文章不在列表；用户与文章scope独立、401/403不伪装成0，以及黑名单、验证码解封、周期验证、开发关闭风控。禁止用生产账号随意改变位置隐私测试。

整体发布时仍需把 API.md 同步至网站API文章 https://fishpi.cn/article/1636516552191 。
