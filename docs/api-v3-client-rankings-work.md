# v3 客户端排行榜迁移（2026-10-27）

本批复用GET /api/v3/rankings/{type}，接通十五类榜单与现有游戏子榜单、首页前八条预览。不改后端契约，285条注册保持；无Java/FTL/SQL/APP修改，无Maven。

## 修改文件与行为

website_v3新增：

- `src/api/v3-rankings.ts`：十五类型/子类型目录、响应白名单和真实快照分页；连续服务端名次、score与对应metrics绑定，赞助小数与原有有符号得分保留，错误不填零。links只投影链接，其他榜单只投影公开用户和已知指标。
- `src/composables/useRankingBoard.ts`：原页重试、子榜单重置第一页、身份/代次/迟到隔离；同账号token轮换保留页码重新读取，换身份回第一页。SSR投影页与HTTP嵌套分页分别校验。
- `src/components/RankingBoard.vue`：通用完整榜单/首页预览、公开昵称头像、指标、赞助汇总、子榜单选择和分页；错误清旧行/总数，翻页成功滚回标题附近，安全链接和纯文本。
- `tools/v3-rankings-test.mjs`：16组生产API/完整Vue组件专项，覆盖所有类型及子类型。

website_v3修改：

- `src/views/TopView.vue`：目录与各分类共用RankingBoard；职业仍用既有ProfessionRanking。去除旧全榜并发请求、apiKey门槛、SWR、演示榜提示及本地名次/指标兜底。
- `src/views/HomeView.vue`：连签和在线独立RankingBoard、size=8，不再进入首页总快照/错误吞并；“更多”链接进入完整榜。SSR初始只首挂复用，离页/布局重新挂载读取当前数据。
- `src/api/fishpi.ts`：删除全部无消费者旧/api/top读取包装、榜单错误吞并及mock引用。
- `src/api/catalog.ts`：删除无消费者mockCheckin/mockOnline。
- `src/entry-server.ts`、`src/seo/payload.ts`、`src/seo/ssrRoutes.ts`：类型绑定的公开SSR单榜页及首页两个独立投影快照，按请求提供并一次消费；无初始成功数据时客户端正常读取，不能把失败当空榜。
- `src/router/index.ts`：补已有v3 links能力的客户端/top/links路由。
- `tools/v3-profession-ranking-test.mjs`：适配TopView当前职业组件挂载结构，其余权限/省略经验/名次断言保持。
- `tools/v3-ssr-payload-test.mjs`：增加单榜/首页两份载荷的每应用隔离和一次消费。
- 两仓AGENTS、本记录和各迁移总表更新，Rhythm另更新UPDATE_LOG；API.md既有接口契约保持。

## 实际验证

- tools目录66脚本、709组全通过。末次翻页定位/指标文案调整后重跑排行榜16组及类型/客户端/SSR构建通过。
- `corepack yarn run build`通过；74个生成JS，版本审计0发现，无map/sourceMappingURL。未增加依赖/CDN或上传下载资源。
- 真实Chrome加载生产构建+SSR合成HTTP，无生产读写：1280桌面从连签第一页→第二页503→原页重试显示第21名→Evolve切days→赞助汇总456.78/1.25。证据`ev_bae269d1c135de455e4a6568ded39442e1dd46332e217e627a6fd2f51abff8ed`。
- 390×844移动从真实HomeView两份前8条预览→在线刷新→“更多”进入连签→第二页503→重试成功。发现短末页视口留在页脚，已加定位并复验：第21名、分页和标题都可见。修复后证据`ev_08910085240143a4ad5b803bdd33731cf0d27e6dc90fa8d5fc3454cc0b67d1fe`，最终指标/标题定位复验`ev_0a11e1cfae32a3c81b21fe1c240af246dfcbd51f59837352b7fda185f4092549`。
- 无page-error/水合警告；访客恢复会话401及故障演练榜单503是预期控制台错误，未声称全控制台零错误。夹具不验证真实业务库或榜单隐私规则。

## 完整人工链

1. 游客从总榜`/top`选择连签、在线、财富、消费、赞助、优选、邀请及各游戏/链接；目录本身不并发加载全部榜单，分类只调用对应v3资源。核对实际total/分页/指标与服务端响应，不出现演示账号或假0。
2. 构造21条快照，下一页应第21名、末页禁用下一页；断网或503清除旧名次和总数，显示错误，“重试榜单”继续原页，“返回第一页”回第一页。空快照显示真实0条和无记录，不能把失败当空。
3. Evolve依次选成就/知识/重置/天数，小冰选0..3；每次回第一页，仅展示对应公开指标，旧子榜迟到响应不得覆盖新列表。赞助显示真实小数汇总；links正常URL可安全打开，危险URL只显示标题。
4. 从首页连签/在线预览核对每份8条及各自错误/刷新，点“更多”进入完整榜。返回首页或切换布局重新挂载不复用已消费SSR旧快照；一个榜读取失败不阻止另一个榜。
5. 登录、正常token轮换、退出、换号期间延迟榜单响应；正常轮换重读当前页，身份变化回第一页，旧响应不能回写。直接SSR刷新与SPA切换都不应有水合警告，切别的榜再回来应读当前数据。
6. 390px和桌面检查长昵称/纯文本、各指标/分页/子榜单选择、成功翻页的标题定位。进入职业页继续使用既有职业目录/可见范围/经验省略规则，不能混用普通榜单排名。
7. 真实开发服务检查匿名首访/test验证、黑名单IP跳转、验证码解封、登录Bearer放行/缺scope拒绝与开发关闭风控行为；核对业务库实际榜单快照。若后续改Java/FTL，须先通过IDEA重编译/重启确认。

## 安全与反漏扫结论及限制

现有ApiV3RankingProcessor公开GET仍挂optionalContent及AnonymousViewCheckMidware；本批仅加客户端路由，不改anonymous.viewSkips、BeforeRequestHandler、首访/周期验证码、CaptchaProcessor解封或旧登录/API鉴权。客户端使用现有Bearer transport，SSR不读取共享身份。无写请求或新增CSRF边界，无数据库参数/SQL、服务端远程抓取、上传下载返回路径或新增目录；手动单资源分页，不并发拉全榜或自动后台轮询。

类型/子类型为闭集，分页有界；响应用户/指标/链接白名单且模板转义文本，无v-html或CSS拼接头像。URL仅安全HTTP(S)/站内相对导航，禁凭据、控制字符、反斜杠、协议相对地址及危险协议。原始用户资料不进入公开DOM/缓存，新增资源版本/map审计通过。

尚未运行真实匿名与Bearer/缺scope/越权链、全局黑名单/test/解封/开发模式、限流/目录响应、业务库真实快照与跨节点一致性；不能把合成页面等同全站数据/权限验收。普通榜单total是服务端有限快照条数，Evolve/人生重开仍受现有记录范围限制；不是全站用户数或全量游戏历史排名。本批没有改职业隐私算法，也没有重新验收其他首页模块。
