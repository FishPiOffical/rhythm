# v3 客户端用户公开职业资料（2026-10-25）

接续本人设置、成长提示和排行，本轮把用户主页“职业资料”面板切换到既有GET `/api/v3/users/{userName}/professions`。没有新增后端入口、修改业务规则或SQL，v3仍264条。本批不等于整个用户主页迁移：基础资料、会员/徽章及部分列表、交互仍用旧链路，见下方边界。

## 实际修改文件

客户端根目录 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-profession-public.ts`：独立公开DTO与读取，只投影primaryProfession、professions、majorContributions、contributionStats、activityFeed、levelHistory、rewards、ranking。模块/经验/rarity缺省保持缺省，绝不补0或空数组；出现的空数组保留，可与省略区分。SELECTED不得含等级/经验/排名，真实进度校验经验成对字段；正负变化、真实0保留。ID/枚举/字符串/安全整数/数组、目标用户名、重复单职业项及rank/total/百分比一致性校验，无效502不假装无资料。未知敏感/原始字段不投影。
- `src/components/profession/PublicProfessions.vue`：按实际返回模块渲染公开面板，所有字符串Vue文本。隐藏模块没有假空区块；贡献与动态经验独立省略。当前名次仅用可见榜统计，SELECTED明确尚无进度；奖励仅概要，不造金额或具体勋章。404显示“当前没有可展示的职业资料”，503/网络等显示真实错误状态。匿名可读，凭据/目标变化先清旧结果，迟到旧读者或目标资料不能回填；无缓存、无SSR读取或私有输出。未返回定义时只用“职业编号…”标记，不用前一读者的隐藏名称。
- `src/views/MemberView.vue`：移除旧fetchPublicProfession/回退读及旧对象/面板SCSS，职业标签接新组件；职业入口不再取决于可见primaryProfession，避免主职业隐藏但贡献可见时入口消失。其他主页资料链保持。
- `tools/v3-public-professions-test.mjs`：新增真实TS契约、编译后的完整Vue模板、实际Vue/Pinia状态及SSR专项。
- `README.md`、`AGENTS.md`、`docs/V3_MIGRATION.md`：本批状态与未验范围。

后端工作区仅修改文档：`docs/api-v3-client-public-professions-work.md`、`docs/api-v3-progress.md`、`UPDATE_LOG.md`。API.md原公开职业契约未改，不新增Repository或迁移。

## 当前主页边界

MemberView的基础资料仍由旧fetchUserProfile/SSR payload提供，会员与其他列表也未整体迁移。旧基础资料读取失败时，原父级profile门禁仍可能阻止职业面板挂载；本批没有伪造基础资料绕过它，也没有把只有少数字段的v3用户摘要硬填成全量主页统计。需要后续迁移基础资料及缺失公开统计契约后闭环，不能声称用户主页全量v3、无旧API或完整隐私已验收。公共职业面板即使读本人也只走公开接口，不回退本人完整职业读取。

## 实际验证

`corepack yarn node tools/v3-public-professions-test.mjs`最终7组exit0，日志`/tmp/rhythm-v3-client-public-professions-tests.log`：

1. 省略模块和经验在职业/贡献/动态中均保持省略，额外敏感/原始字段不投影，路径只用户姓名而无apiKey/owner ID。
2. SELECTED不造等级，真实0、负经验和独立模块完整投影。
3. 错误rarity数学、半组经验、重复ID、null/超长模块、空成功对象与非法目标明确拒绝。
4. 实际编译Vue模板渲染：隐藏区块/数值省略，真实0、负数、排名及奖励状态正确；含HTML描述为文本节点。
5. SELECTED模板无零级，实际SSR不请求/输出浏览器读者绑定结果。
6. 实际目标/凭据切换清结果，拒绝迟到旧用户与已退出身份资料。
7. 404与503不同状态，MemberView不再用旧职业API或主职业可见性门控标签。

模板测试用@vue/compiler-sfc编译实际SFC，Vue runtime/Pinia及记录文本的自定义renderer；传输队列适配，非浏览器DOM执行或后端可见性算法验证。`corepack yarn run build`类型/client/SSR通过，`/tmp/rhythm-v3-client-public-professions-build.log`。SCSS经构建，没有直接改CSS/min.js。生成63个JS审计零命中，`/tmp/rhythm-v3-client-public-professions-js-audit.json`；两工作区diff空白检查通过。

实际IAB本地HTTP/WS合成夹具：

- Alice登录读者看到八类模块，等级/名次保留，但职业、贡献、动态均不显示经验数值；`<b>这里按文本显示</b>`显示字面文字。
- 使用本地测试用户菜单退出，旧等级/排名清除，匿名重新读取只返回主要贡献及可见空统计；主职业隐藏时“职业资料”入口仍存在，名称不沿用前一读者的隐藏定义，显示职业编号。
- Bob仅SELECTED，明确尚无经验进度，未造等级/经验。
- Hidden返回404显示无可展示资料，Error返回503显示错误，两者不混淆。
- Hydration日志为空，无真实账号或生产写入。

截图`/tmp/rhythm-v3-client-public-professions-proof.png`、`/tmp/rhythm-v3-client-public-professions-anonymous-proof.png`；请求日志`/tmp/rhythm-v3-client-public-professions-fixture.log`。夹具为原主页基础资料提供旧JSON以满足尚未迁移的父级入口，不能据此声称旧资料/真实账号全部已验。临时页与自建服务器完成后关闭。

## 人工完整链

1. 本轮仅客户端改动；真实服务仍需先确认前批Java/FTL已IDEA重编译/重启、相应v3迁移已部署。客户端yarn run build后指向localhost:8080，使用127.0.0.1:5173打开用户主页→资料→职业资料，确认原基础资料成功加载。
2. 用专用测试用户逐一开放/隐藏十模块，至少覆盖主职业隐藏而贡献可见、只有奖励/排名可见、模块省略与可见空数组。未返回模块不显示“0/暂无记录”，公开面板对本人也不能绕过HIDDEN；本人完整资料仅设置页读取。
3. 主职业有选择但无进度时只定义/SELECTED，无等级经验排名；已有进度可显示等级，经验隐藏时职业/贡献/动态均不补0。真实0必须显示；负贡献/动态保持符号。稀有度/个人名次只用当前可见榜，无法计算时不造排名/人数/百分比。
4. 核对贡献分类/汇总、90天最多30条动态/等级变化、奖励待发放/已发放/已撤销。删除或变私密/匿名的来源应从公开动态消失，不附来源正文。奖励不凭类型猜金额/具体勋章。真实后端必须验证这些，夹具只证明客户端展示分支。
5. 登录与退出/切换读者时挂起旧请求，旧等级/名次/隐藏定义名称不能残留；快速切用户不混入另一目标。无可展示资料404与临时503/网络错误不同，重读不得从旧SWR缓存恢复隐藏字段。新增字段/额外raw config、手机号等不能进入本组件DTO或DOM。
6. PC/移动设备走入口→查看→切换隐私/读者→刷新→错误恢复。缺profile.read、过期token/epoch、黑名单及匿名新IP须遵守既有路由；实际/test、验证码解封、CSRF/Bearer、滥用、CDN版本/sourceMappingURL/目录枚举/上传下载路径需完整应用回归。
7. 基础主页仍须另行验收：SSR/旧userinfo失败、旧会员/徽章/列表缓存与读者切换等并未由本批解决。后续迁移应保留真实字段及未知状态，不把v3摘要缺失统计补0。

## 安全与反漏扫

只使用既有可匿名的公开职业GET，服务端原optional profile.read、AnonymousViewCheckMidware与全局Before链保持；没有新匿名入口/viewSkips或HTTP身份旁路。客户端用当前内存Bearer或匿名，不以apiKey/本人私有职业补身份；组件无持久缓存或SSR读者结果。

白名单投影、用户名/ID/枚举/类型/长度/条数校验，Vue文本、无v-html/动态SQL/服务端抓取；无新增上传下载或公网CDN。真实权限/来源可读性/风控及全站CDN/目录/上传下载未验，生成JS定向扫描零命中不代表整站反漏扫完成。父级旧主页缓存仍在范围外，不能把本组件隔离结论扩成整个用户主页。
