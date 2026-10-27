# v3 客户端本人积分流水

2026-10-25。接既有GET `/api/v3/me/points`及`/api/v3/me`，后端264路保持，未新增接口或修改契约。补齐上一批转账后的流水入口，不等于整站v3完成。

## 实际文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-points.ts`：严格p/size、准确总数/页数；只投影time/amount/operation/balance/type/displayType/descriptionHtml，拒绝类型或分页损坏。`/me`只取当前userPoint，不用第一页或账号缓存补余额。
- `src/views/PointsView.vue`：积分页直接读当前本人记录；金额/收支、交易后余额、类型及时间，服务端已清洗HTML说明；余额/列表独立错误、重试，真实分页和边界。稳定身份版本+账号/序号隔离迟到读，正常token轮换保留当前页；换号清空回第一页，退出清除。mounted一致SSR仅登录提示，无浏览器私有数据预读。原样式转SCSS源码并经构建。
- `src/api/fishpi.ts`：删除已无消费者的PointRecord、fetchPointRecords、parsePointNotice，避免继续从通知正文猜金额/收支。不改其他仍在迁移的旧积分查询消费者。
- `tools/v3-points-test.mjs`：实际API与完整Vue模板/状态/SSR专项。
- `AGENTS.md`、`README.md`、`docs/V3_MIGRATION.md`：迁移边界。

Rhythm本批仅本记录、`docs/api-v3-progress.md`、`UPDATE_LOG.md`；API.md无契约变更，原既有文档仍需同步网站文章。

## 证据与适配范围

1. 五组专项退出0（`/tmp/rhythm-v3-client-points-tests.log`）：准确分页、字段投影/坏数据；真实金额方向/0及余额；实际Vue下一页与同身份令牌刷新不跳页、换号回第一页只一次请求；余额/列表独立失败重试不伪造0/空；退出/换号旧结果丢弃；SSR不读私有、删除旧通知解析源。真实TS/Vue/Pinia在内，HTTP和账号源用适配，不能作为真实服务器鉴权通过证明。
2. 原财务七组回归退出0（`/tmp/rhythm-v3-client-points-finance-regression.log`），包含真实http.ts的401轮换后同body/key恢复。
3. yarn run build类型/client/SSR通过，日志`/tmp/rhythm-v3-client-points-build.log`；生成client JS65项版本/map命中0，报告`/tmp/rhythm-v3-client-points-js-audit.json`；两仓git diff --check通过。未手改CSS/min.js，未运行Maven。
4. 实际IAB本地合成HTTP/WS夹具：21条、第一页20/第二页1，0金额与-100独立显示，当前余额10000不被记录余额9900覆盖；当前余额首次503时列表仍展示，单独重试余额恢复；第二页首次503明确失败，同页重试后2/2且下一页禁用。真实退出→再开/points跳登录，旧行不显示；Hydration日志空。仅合成读取，无真实转账。截图`/tmp/rhythm-v3-client-points-proof.png`；请求证据`/tmp/rhythm-v3-client-points-fixture.log`。自建服务和临时页验后关闭。

金额、时间、总数仅服务端返回；不从说明HTML、通知、第一页余额倒推。服务端按本人from/to产生收支方向；保留历史有符号amount并按该方向展示净变化，不对历史数据做绝对值静默修复。当前余额与流水为两个独立读取，不承诺同一数据库快照。

## 完整人工链

1. 本批只改客户端；真实联调前先确认前批Java/FTL经IDEA重编译/重启及所需迁移部署。本批前端yarn run build，代理localhost:8080，登录第一方账户→设置/积分→流水，或访问`/points`。
2. 查看当前余额、总记录、金额方向、交易后余额、类型、时间和说明链接；真实0显示0，不从含多个数字的说明或其他历史余额推断当前余额。PC与移动分别看长备注/链接、换行、键盘与小屏分页。
3. 在隔离测试账号执行已批准的小额转账/购买，再进入流水并刷新，核主款、费用、码购买真实记录与当前余额。此前未知命令应回原入口同键恢复，不能因流水暂时未出现就重新创建新转账。此步骤实际扣积分需用户在测试环境执行；本批未对真实账户操作。
4. 21条或更多记录翻前后页，最后页禁下一页，合法空库显示暂无；页码超过现有总页数显示本页无记录并可返回。刷新/分页期间模拟503/坏格式，必须报错而非假空记录；余额失败不隐藏成功列表，列表失败不抹去已读余额，可分别重试。
5. 第二页普通401轮换后仍第二页；换号回第一页且不串旧行。挂起旧账号/旧页响应→退出/换号/翻页后返回，旧余额和记录不能覆盖新页面；未登录不能读本人列表，SSR HTML不能含前一读者数据。
6. 真实activity.read、账号撤权与跨设备、黑名单/test/验证码解封及开发关风控、PC/mobile完整链仍须验收；本地夹具不是MySQL账本或真实后端结果证明。

## 安全与反漏扫

无新路由、anonymous.viewSkips、URL凭据、静态/CDN、上传/下载入口；只读本人Bearer GET，未改Before黑名单/AnonymousViewCheck/Captcha链或写入CSRF行为。分页限制按现有契约，忙碌状态及显式重试无无限轮询；服务端滥用/鉴权并不能由客户端保护替代。

响应白名单排除from/to身份/余额、内部data/key；普通类型/时间/金额Vue转义，HTML只使用现有v3经Markdowns.clean生成的descriptionHtml，不把完整账本对象或普通备注当HTML。服务端HTML清洗与历史说明权限的真实运行仍需核对。本批没有SQL/注入或服务端抓取路径；SSR和迟到响应绑定当前身份。生成JS版本/map定向零命中，非全站CDN/目录枚举/上传下载路径扫描。真实未鉴权/越权、CSRF、XSS清洗、SSRF、批量负载、敏感信息/全局反漏扫未完整运行，不默认通过。
