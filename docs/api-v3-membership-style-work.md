# v3 会员昵称样式批量读取（2026-10-24）

用户已确认只公开 bold、underline、color；需要 profile.read，按指定ID批量1至100个，重复422；不返回全站会员名单、会员档位、到期时间、原configJson或其他权益。

## 文件与行为

- `src/main/java/org/b3log/symphony/processor/ApiV3MembershipStyleProcessor.java`：GET `/api/v3/membership/styles`，profile.read；严格原始query仅userIds，拒绝重复参数/未知字段/格式错误，不依赖框架合并参数或Cookie/apiKey身份。
- `src/main/java/org/b3log/symphony/service/ApiV3MembershipStyleQueryService.java`：重查当前有效actor；单次有界关联当前有效目标、state=1会员与档位定义，按请求顺序返回userId/style；过期/非会员/未知或停用目标为空样式，无法获得档位定义也不授予展示字段。重复绑定、坏JSON/到期数据及仓储错误503，不选任意一行。
- `src/main/java/org/b3log/symphony/util/ApiV3MembershipConfiguration.java`：仅将现有colorAllowed改为public供读取复用，颜色规则不变，八种效果仍限精确VIP4_YEAR/MONTH。新读取只保留定义支持且本人有合法配置的展示字段，不生成默认值。
- `src/test/java/org/b3log/symphony/processor/ApiV3MembershipStyleTest.java`、`tools/api-v3-membership-style-test.mjs`：生产SQL、真实Dispatcher/Auth/Forms/DTO控制器链，SQLite、Token持久化和响应信封适配。复用原MembershipTest只作为隔离数据库/基础工具，不运行其main。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`：总注册；`API.md`、`UPDATE_LOG.md`及进度/计划/AGENTS：契约与证据维护。
- 本文和`docs/api-v3-public-membership-plan.md`：人工完整链路和原公开接口盘点；旧路由/模板/JS保持，未改前端、不需yarn。

## 实际验证

根执行 `node tools/api-v3-membership-style-test.mjs`，5组退出0，日志 `/tmp/rhythm-v3-membership-style.log`。覆盖真实路由/scope/无Cookie提升、raw query污染和格式上限、永久/到期边界/无效账号、100ID顺序、两条有界SELECT、只返三字段、当前档位支持/精确VIP4颜色效果、缺定义不授予、坏配置/重复绑定/仓储故障503。提取现有颜色helper后同命令复跑通过。

只在隔离SQLite执行SQL；未运行真实MySQL该关联查询、完整IoC/HTTP、浏览器、真实Token存储、并发状态变化或整站反漏扫。未Maven编译。不要用5组推断全部会员API或PC/mobile已验收。

## 人工测试完整链路

1. 先通过IDEA重编译/重启，再在开发环境获取profile.read Bearer，准备当前有效VIP4、其他档位、到期、永久、无会员及停用目标；记录本人会员展示设置。
2. 从第三方聊天/作者列表需要展示昵称的ID集请求 `GET /api/v3/membership/styles?userIds=...`，核对返回ID顺序与目标一一绑定；PC/mobile客户端分别按返回样式渲染。无会员/未知/停用目标style为空；不含到期时间、档位、autoCheckin或原始configJson。
3. 本人用既有配置PUT改bold/underline/color，再次GET立即读取新配置；修改当前档位支持或过期后，不应继续返回已失效样式。普通档位不能从损坏旧config获得galaxy等VIP4效果；永久VIP4有效，空旧配置不凭空补defaults。
4. 无Bearer/错误或过期Bearer401，缺profile.read403，只有Cookie/apiKey不能取得数据。停用当前actor拒绝。其他用户ID只影响查询目标，不能提升本人身份。
5. 空/重复/101项、含空格/前导0/空项/SQL字符、重复query键、附带userId/apiKey、非UTF8或过长query均422。最多100项完整返回；读取不创建任何会员/默认配置，只有两条有界查询。
6. 测试库造重复有效会员/档位、坏JSON/越界到期字段和仓储故障，应503且无部分错误绑定/原始配置/SQL内容。浏览器昵称必须当作样式受限数据，不能把config字符串拼HTML。
7. 新IP无身份不能访问；回归全局黑名单/test、验证码解封、开发风控关闭与匿名其他入口，无anonymous.viewSkips新增例外。全站静态/CDN版本号/sourceMappingURL/目录枚举与上传下载基线未在本专项验证，本批无新增此类资源。

## 安全源码结论

显式Bearer/profile.read，当前actor/目标状态读取，固定100项与严格参数解析限制枚举批量，SQL仅参数绑定；无写接口、Cookie授权写操作、HTML/URL输入或远程抓取，不产生CSRF写入/注入/SSRF入口。响应字段白名单不含私人账号资料或会员内部行ID；未知目标与无样式同形。全站防扫描与真实页面XSS效果仍需运行验收，不能仅凭源码通过。

公开文档更新后，请同步API.md到网站API文章 https://fishpi.cn/article/1636516552191。
