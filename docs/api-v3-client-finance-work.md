# v3 客户端积分转账与邀请码

2026-10-25。接入既有三条POST，不新增/修改后端契约；源码总数保持264（263HTTP+1WS）。不是v3整体完成声明，积分流水和用户主页基础资料仍有旧读取。

## 实际文件与行为

客户端仓库 `/Users/ling/Documents/fishpi/website_v3`：

| 文件 | 行为 |
|---|---|
| `src/api/v3-finance.ts` | 转账严格输入、金额守恒/公开回执投影；空体购买及16位码POST查状态，不把邀请码放URL |
| `src/stores/v3Finance.ts` | 每个Pinia实例独立私有内存；确认时冻结请求/key，未知结果同键恢复，路由切换保留，刷新令牌保留、换号/退出清理 |
| `src/api/http.ts` | 只增加既有authVersion只读getter；普通refresh不变、显式身份切换递增，不改变认证策略 |
| `src/components/settings/PointTransfer.vue` | 设置/他人主页共享表单、原请求禁改、真实费用/到账/清洗备注回执；后置余额读错不重转 |
| `src/components/settings/OwnerInvitations.vue` | 邀请链接、100分确认购买、当前登录内存中的原码回执及复制、精确状态/404；unused不保证可注册 |
| `src/views/SettingsView.vue` | 积分/邀请面板接入上述组件，移除相应旧apiKey写链 |
| `src/views/MemberView.vue` | 快捷转账共用组件，预填当前目标；浏览器发现身份SSR水合不一致，模板身份分支改为mounted后显示 |
| `tools/v3-finance-test.mjs` | 实际TS/Pinia状态与真实HTTP刷新重试专项 |
| `AGENTS.md`、`README.md`、`docs/V3_MIGRATION.md` | 更新迁移边界与长期身份约定 |

后端本批只更新本记录、`docs/api-v3-progress.md`、`UPDATE_LOG.md`。`API.md`无新契约需要修改，既有后端文档仍需同步网站API文章。

## 结果未知与身份

普通401触发的令牌轮换不能丢掉已经发送的扣款请求。按稳定HTTP身份版本与当前账号ID绑定意图/回执；非空token轮换保留，同一账号显式重登录也视为新边界。不同账号/退出/失效清空，迟到结果不写回。确认期间换号不能发送原请求。

第一次明确4xx可释放编辑；已经未知的请求随后恢复遇到4xx仍保留原key，不能把限流或权限暂时变化当成此前没有提交。网络/5xx/格式不明不宣称失败未扣款。余额读取失败与已确认写结果分开。购买回执按码去重，仅展示服务端实际费用与到期时间，不保证仍有效。

内存状态跨SPA页面保留，不跨刷新/关闭页面、登出或账号更换；界面明确提示先核对原操作。未写localStorage/sessionStorage，不把验证码、邀请码或备注发布外链/日志。邀请链接只使用编码后的本人用户名。

## 实际验证

- `node tools/v3-finance-test.mjs` 七组退出0：请求闭合/严格边界/守恒投影、码状态、未知结果同键/路由复用/刷新保留、恢复429保留、取消/初次422/后置读错、换号迟到丢弃与购买去重/退出清私有、SSR/确认换号不提交、真实HTTP401→refresh→原body/key重试且身份版本保持。
- 测试适配传输、账号读取、确认对话框；生产适配器/Pinia在内。最后一组直接运行真实http.ts，以假fetch响应模拟401和轮换；不是实际Cookie网络或真实扣款证明。日志 `/tmp/rhythm-v3-client-finance-tests.log`。
- `corepack yarn run build` 类型、client、SSR构建通过，日志 `/tmp/rhythm-v3-client-finance-build.log`。
- `tools/js-version-audit.mjs` 定向扫描生成client JS65项，版本/map命中0；双仓`git diff --check`通过。无手改CSS/min.js，无新增CDN或上传下载入口。
- 实际IAB只连接本地合成HTTP/WS夹具。10000积分→转账100提交后503→切邀请页再返回仍原表单/key→重试原回执、余额9900；邀请购买同样提交后503→同键恢复原码、余额9800。夹具日志只有转账1次/购买1次COMMIT，各一次REPLAY。状态unused明确非注册保证，未知码404。主页Alice快捷入口预填目标，发现并修复SSR身份警告后新页Hydration日志空。
- 截图：`/tmp/rhythm-v3-client-finance-transfer-proof.png`、`/tmp/rhythm-v3-client-finance-invitation-proof.png`、`/tmp/rhythm-v3-client-finance-member-proof.png`；夹具证据 `/tmp/rhythm-v3-client-finance-fixture.log`。无真实账号转账/购买；临时页/自建服务器验后关闭。

## 完整人工链

1. 本批只改客户端。真实联调前先确认此前Java/FTL已通过IDEA重新编译或重启，迁移部署完成；本批未运行Maven。前端`yarn run build`并指向localhost:8080，以第一方账号进入`/settings/point`，以及他人主页→转账。
2. 两入口均核对当前余额和收款用户名；填100积分/带HTML备注，确认显示目标/总扣款/原备注。取消不发送；确认后按真实普通手续费核总扣款100、主款99、费1，服务端清洗后的备注只以文本显示。精确有效VIP4、费收款账号、自转、机器人、无效用户、积分不足、2分/整数上限分别测边界，不用客户端估算代替实际回执。
3. 在受控测试库模拟提交后断开响应，表单不可改；SPA切页再返回应保留原目标/金额/备注/key。普通401刷新不清命令，重试只恢复原转账；恢复429/403等不能丢key。确认成功后余额GET失败只提示读取失败，不能重新转。核真实账本主款/手续费和通知各一笔。
4. `/settings/invite`→核对购买→取消无请求；邀请模式确认100分仅扣一次并生成原码。模拟丢响应后同键返回同码；模式改变或不足初次请求拒绝不造码。保存实际码/到期回执；查询unused/used/stopped、未知404和非法输入422，unused不是当前注册资格证明；检查复制与本人邀请链接，链接注册仍走服务端资格。
5. 未确认操作时退出/换号，私有意图/码/回执立即清空，挂起旧响应不得污染新账号；确认对话框期间换号也不能提交。正常刷新令牌必须保留同会话命令。刷新整页不保留内存，需核对原流水/购买再发新操作。
6. PC和移动实际操作：设置与主页入口、键盘/小屏/对话框、长备注文本、错误恢复、复制失败、余额同步及SSR。真实服务端权限/activity scopes、Cookie/Bearer、账号撤销与并发通知仍需验收，不能用夹具证明。

## 安全及反漏扫

只使用既有v3认证POST：转账/购买activity.write+幂等、状态activity.read。无新增匿名路由/viewSkips，没有改Before黑名单、AnonymousViewCheck、Captcha解封或授权中间件。当前浏览器使用内存Bearer，不往legacy apiKey塞令牌；实际未鉴权、越权、CSRF、黑名单/test及解封必须联调。

输入有限长、ASCII用户名/邀请码、整数；金额/目标绑定回执，白名单投影，memo用Vue文本无v-html。无新SQL/SSRF/远程抓取/任意CSS、上传下载或公开凭据URL；无自动无限写重试，忙碌与确认保护重复点击，但客户端不是服务端并发/滥用限制。生成JS版本/map定向无命中，未全扫全站静态/CDN、目录枚举、存量上传下载路径；真实风控/服务端鉴权/设备/账款事务及全部旧入口未验，不能宣称整站反漏扫通过。
