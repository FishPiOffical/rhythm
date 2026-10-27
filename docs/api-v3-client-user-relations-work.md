# v3 客户端关注用户与粉丝读取

2026-10-25。推进现有完整v3目标中的用户关系列表读取；没有新增或改变后端接口，后端总数275保持。此前关注/粉丝页面调用旧/api/user并将错误伪装成“缺JSON”，现改用已有v3公开关系分页。关注写入、悬浮卡片旧基础资料、其他用户页、标签/收藏和聊天室仍在后续范围。

## 实际文件

客户端根为/Users/ling/Documents/fishpi/website_v3。

| 文件 | 行为 |
| --- | --- |
| src/api/v3-user-relations.ts | 两条已有GET路径，严格目标/分页/白名单DTO与头像安全协议验证，错误不回退旧API/mock |
| src/views/UserRelationsView.vue | 独立关注/粉丝页面、真实分页、隐私/错误/空状态、重试及返回首页；纯文本昵称、共享头像和VIP样式、头像悬浮入口；SCSS响应布局 |
| src/router/index.ts | 两个原路径接独立页面，保留其他用户页路由 |
| src/views/MemberView.vue | 删除原people读取/旧列表和错误缺口提示，其他旧模块保留 |
| src/api/fishpi.ts | 删除无消费者旧fetchFollowers/fetchFollowingUsers/unwrapUsers |
| tools/v3-user-relations-test.mjs | 真实API解析、Vue编译/渲染与SSR专项 |
| AGENTS.md、README.md、docs/V3_MIGRATION.md、docs/MISSING_APIS.md | 明确迁移范围和余项 |
| dist/client、dist/server（构建生成） | corepack yarn run build产物，无手改CSS/min.js |

Rhythm修改docs/api-v3-progress.md、UPDATE_LOG.md及本文件；没有Java/FTL/SQL或后端API契约变化，不需为本批单独重启Java。前批Java/SQL仍须通过IDEA重编译/重启再真实联调，尚无确认。

## 实际验证

五组新专项通过：精确端点与分页、白名单投影/错误参数/重复用户/不安全头像；实际组件末页/普通token轮换保持页；页面错误恢复、403区别于0条、昵称纯文本和头像data-user-card；目标/读者切换丢弃迟到私有结果并匿名重读；SSR不读共享身份、不依赖旧userinfo。Vue测试中HTTP、router/auth与VIP组件为明确适配，实际VIP另复跑八组共享样式专项，本人匿名内容五组回归通过。不是完整后端权限证明。

corepack yarn run build类型检查/client/SSR通过。67生成JS扫描sourceMappingURL、Vue/@vue版本头零命中，未发现map文件；不是全部依赖指纹或全站CDN扫描。

IAB本地127.0.0.1:5173→只含合成用户的18765夹具：第一页21条，第二页503明确报错，同页重试得到2/2且下一页禁用；含b标签的昵称按文本显示，默认头像可见。private粉丝返回403明确无权查看，empty粉丝显示0条/暂无用户。Hydration日志为空。截图/tmp/rhythm-v3-client-relations-proof.png，测试日志/tmp/rhythm-v3-client-relations-{tests,build,fixture,server}.log及js-audit.json。窄视口435px，真实桌面宽屏/移动设备未验。没有生产请求或真实账户写入。

## 完整人工链

1. 开发环境先完成此前后端迁移及IDEA重编译/重启确认，前端指向本地8080；从用户主页关注用户/粉丝链接进入两个原路径。合法匿名公开列表应成功，当前Bearer读者隐私按服务端规则，不能由Cookie或前端假资料替代。
2. 具有超过20项的测试用户验证第一页、下一页、末页禁用、上一页、总数；列表头像/用户名跳正确目标，触屏和键盘链接可用，桌面悬浮卡片按实际userName加载。核对真实头像失败的默认图及共享VIP样式，不将空样式判断为非会员。
3. 403显示无权查看、404显示不可访问，503/断网显示错误且保留重试/返回第一页，不显示成0条。真实空列表才显示0/暂无。列表动态缩减后空的后续页可返回第一页。
4. 在第2页切换目标/关注与粉丝、快速换号/退出并延迟旧响应；旧列表立即清空且不回写，匿名重新读公共范围。普通刷新token不跳回第一页。SSR首次与水合不闪入旧私有用户。
5. 测试昵称HTML为字面文本，恶意头像协议不会进入img，超限/损坏响应显示错误且不造假用户；原标签/收藏/主页其他模块仍按既有独立链路验收。
6. 真实新IP/黑名单跳/test、Captcha解封、开发风控开关、匿名计次、Bearer scope不足和当前账号撤权回归。PC宽屏/移动真机需验证换行、分页按钮及触屏头像；本地合成夹具未覆盖真实授权、CORS、Captcha和全站风控。

## 安全与反漏扫

本批只GET既有v3，transport内存Bearer，不放URL/旧apiKey/图片/日志；无新增Cookie写、CSRF例外或匿名豁免。读者/目标代际隔离且no-store，不复用全局私有缓存；默认SSR准备态。昵称/用户名用Vue文本渲染，链接encodeURIComponent，头像限HTTP(S)/同源路径；无服务端抓取/SSRF或新上传下载路径。服务端权限、SQL和分页仍沿既有后端实现，本批不声称改进了底层统计或并发。

未改BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor/anonymous.viewSkips。新增JS/CSS由构建生成，扫描仅上述67JS；全站CDN版本、sourceMappingURL历史资源、目录枚举、旧上传下载与实际全局匿名/黑名单/验证码/CSRF/限流未验证。共享UserHoverCard基础资料仍旧链，本批只接入其入口，不宣称该子链已迁移。无真实关注/取关操作，APP未改、无Maven、无提交推送。
