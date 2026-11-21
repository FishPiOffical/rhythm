# v3 客户端职业排行与成长提示（2026-10-25）

接续本人职业设置，本轮迁移既有职业排行与成长提示GET。后端接口、业务规则及264条计数保持；他人公开职业资料、其他排行榜、会员及其余客户端模块仍待后续，不能将本批称为完整v3验收。

## 实际修改文件

客户端根目录 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-professions.ts`：新增fetchV3ProfessionRanking与fetchV3ProfessionGuide，仅白名单投影；校验目标绑定、页信息、连续rank、重复用户及安全整数。totalExperience缺省时保持缺省，真实0保留0，null或坏值不当0。成长来源与奖励按原顺序保留重复项；只收支持的trigger与POINT/MEDAL概要，不造具体勋章、等级对应关系或发放保证。
- `src/components/profession/ProfessionRanking.vue`：当前读者登录后读取目录及榜单，目录/榜单分别分页，选首个实际目录项，不预造职业ID。采用服务端名次与可见总数，不以数组下标或总榜人数代替；隐藏经验显示“经验未公开”，名次/等级保留。切职业、凭据变化或卸载清旧结果、隔离迟到响应；读取失败清旧榜单并显式报错，不取旧共享SWR缓存。用户链接编码，头像复用displayAvatar过滤，以img加载，不拼CSS URL。
- `src/components/profession/ProfessionGuide.vue`：共享成长提示，真实来源、积分/勋章奖励概要和明确非保证说明；空结果与错误分开。目标/凭据变化清旧数据，打开/切换提示后聚焦定位，滚动避开固定顶栏。重复来源是不同规则，不合并成一条，也不推断经验值/触发条件。
- `src/components/settings/OwnerProfessions.vue`：目录每个职业可查看成长提示，无需已有进度；换号清选择，复用同一组件。
- `src/views/TopView.vue`：职业榜分支接新组件，移除旧fetchProfessionRanking与top:jobs共享缓存、旧名次下标/隐藏经验补0。直接进入职业页不再加载不相关的旧排行榜；从职业页切回旧榜时恢复原加载。旧榜状态/演示提示不套到v3职业榜。其余旧排行榜本轮未迁移。
- `src/components/SideBar.vue`：实际浏览器发现SSR游客与客户端已恢复身份不一致，连带资料、签到和清风明月子模块水合告警。现将依赖身份/本机布局的模块延后到mounted渲染，SSR与首次客户端均只输出相同侧栏加载态；挂载后仍读原配置，保留侧栏工具栏。不能在共享公共SSR输出浏览器私有模块状态。没有修改PersonInfoCard等业务规则。
- `tools/v3-profession-ranking-test.mjs`：新增TS契约、真实Vue/Pinia及入口接线专项。
- `README.md`、`AGENTS.md`、`docs/V3_MIGRATION.md`：更新本批完成范围与未验边界。

后端工作区仅文档：`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`docs/api-v3-client-profession-ranking-work.md`。既有API.md契约未改变，无Java/FTL、新SQL/Repository或生产数据修改。

## 已执行验证

`corepack yarn node tools/v3-profession-ranking-test.mjs`最终7组exit0，日志`/tmp/rhythm-v3-client-profession-ranking-tests.log`：缺省经验与0区分、第二页rank21及目标/页绑定；成长重复来源/奖励保留；未登录不请求私有榜单，退出清理；旧职业/旧读者迟到结果隔离；末页缩小重读及读取失败无假人数；成长目标/凭据隔离；Top与设置实际接线无旧job缓存/API/名次或经验回退。传输为队列适配，Vue实际setup/Pinia与无DOM renderer，不是后端权限/SQL测试。

本人职业7组回归也通过，`/tmp/rhythm-v3-client-professions-tests.log`。`corepack yarn run build`类型/client/SSR通过，`/tmp/rhythm-v3-client-profession-ranking-build.log`；SCSS经构建，没有直接编辑生成CSS/min.js。生成63个JS定向审计零命中，结果见`/tmp/rhythm-v3-client-profession-ranking-js-audit.json`；两工作区diff空白检查通过。

实际IAB本地HTTP/WS合成夹具：

- 职业1可见21人，首位隐藏经验仍有rank/等级，第二位真实0显示“0经验”；第二页首项rank21，未重置为1。
- 成长提示保留两条重复“发表文章”和在线来源，显示12积分及勋章概要，不补名称/等级/触发频率。
- 目录第二页选择职业21得到明确零可见用户，旧榜单及已展开旧提示消失。
- 设置→职业→查看职业1/2成长提示均读取正确绑定结果，切换提示后聚焦且标题不被顶栏遮挡。
- 初次浏览发现SideBar水合告警，修复后新开职业榜与设置页的Hydration日志为空。只证明这些本地夹具页面，其他使用SideBar的真实路线及本机自定义布局仍须回归。

截图：`/tmp/rhythm-v3-client-profession-ranking-proof.png`、`/tmp/rhythm-v3-client-profession-guide-proof.png`、`/tmp/rhythm-v3-client-settings-profession-guide-proof.png`；夹具日志`/tmp/rhythm-v3-client-profession-ranking-fixture.log`。无真实账号/生产写入，测试页与自建服务完成后关闭。实际移动设备、真实服务、可见性筛选与权限撤销时序未验。

## 人工完整链

1. 本轮只改客户端；真实联调仍须先确认前批Java/FTL通过IDEA重编译/重启、相关迁移部署，不能用旧进程判断源码。客户端yarn run build后指向localhost:8080，从127.0.0.1:5173打开总榜→职业。
2. 未登录应只给登录入口、不请求私人榜单；登录后加载真实目录与首个职业。超过20职业翻目录，保留当前选择；选择另一项重新从榜单第一页读取。职业被停用/不存在404明确报错，目录读取失败不伪造职业。
3. 用测试用户设置不同ranking和experience可见性，换不同读者核对先过滤再分页、名次连续和人数只算可见用户。隐藏经验仍显示真实等级/名次、“经验未公开”，真实0显示0，绝不能用0代替缺省经验。超过20人第二页从21开始；末页消失时读实际末页。此项必须用真实后端证明，夹具不能代替可见性算法。
4. 查看成长提示→来源/奖励按序展示，重复规则仍重复；只显示概要，无具体奖励等级映射、勋章ID或每次必给承诺。没有来源/奖励与404/503错误需区分；无本人进度也可查看。设置→职业目录也能打开同样提示，并自动定位到标题。
5. 快速切职业/刷新/退出/换账号及离页期间挂起旧请求，不能回填旧榜单、可见人数、提示或错误；错误不能用旧SWR缓存掩盖。缺profile.read、失效token/epoch、停用账号拒绝，不以旧apiKey或Cookie补身份。
6. 在职业榜与其他榜间往返，旧榜加载恢复；旧榜错误/财富提示或演示警告不能出现在v3职业榜。重新打开浏览器直达职业榜，控制台无水合错；登录/退出及本机侧栏排序、折叠和隐藏设置保持，挂载后资料/签到/清风明月显示正确身份。PC与移动设备均走完整链。
7. 回归全局Before黑名单→/test、匿名验证/解封、CSRF/Bearer、批量请求、CDN版本/sourceMappingURL、目录枚举与上传下载路径。本批无新后台/匿名入口，但未做真实环境全链反漏扫，不能默认通过。

## 安全与反漏扫结论

沿既有需要profile.read的GET，新增客户端没有userId/apiKey输入或凭据URL；当前读者绑定状态只在组件内存，凭据边界清除，不写持久缓存。SSR不会输出私有职业榜，侧栏依赖浏览器状态的模块延后挂载。服务端当前鉴权与可见性仍是最终依据，本地夹具不证明其运行。

响应字段白名单、分页/字符串/数组有界、ID和枚举校验；Vue文本展示，链接编码，头像URL过滤，无v-html或动态CSS URL。无新远程抓取、SQL、上传下载、anonymous.viewSkips或CDN地址。真实风控、撤销/跨实例竞争、负载、代理日志、全站CDN/目录/上传下载仍未验。生成JS定向扫描不能替代这些。
