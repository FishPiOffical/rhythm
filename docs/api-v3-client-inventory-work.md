# v3 客户端背包与用户名

2026-10-25。设置账号页接既有背包GET、四个卡片POST与积分改名PUT，后端264路保持，无新增/改变接口契约。账号密码/联系方式/MFA/APP二维码仍另迁移，不能称设置页或v3整体完成。

## 实际文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

| 文件 | 实际行为 |
|---|---|
| `src/api/v3-inventory.ts` | 数量列表严格投影、重复编码/非整数拒绝；四卡与积分改名闭合请求，目标/itemCode/日期/费用回执校验；改名卡trim、积分名原样严格校验 |
| `src/stores/v3Inventory.ts` | 私有Pinia命令/key跨SPA页面保留，稳定身份版本隔离；确认、同键恢复、读与写结果分开；改名后清旧资料缓存及已验证昵称别名，重新读当前账号 |
| `src/components/settings/OwnerInventory.vue` | 真背包/剩余免签、四卡及积分改名表单/确认/回执；当前状态与本次结果分离；空库和读取失败分开，周年券只读 |
| `src/views/SettingsView.vue` | 原背包/改名表单换共享模块，去对应旧apiKey guard与旧调用，保留其他账号设置 |
| `src/api/fishpi.ts` | 删除无消费者的旧背包/四卡/普通改名方法及UserBag类型 |
| `src/api/gaps.mock.ts` | 删除旧mockUserBag，避免请求失败伪造一张卡 |
| `tools/v3-inventory-test.mjs` | 实际TS/Pinia、完整Vue模板/SSR及旧回退移除专项 |
| `AGENTS.md`、`README.md`、`docs/V3_MIGRATION.md` | 长期状态与迁移范围 |

Rhythm本批更新本记录、`docs/api-v3-progress.md`与`UPDATE_LOG.md`；未改Java/FTL/SQL/Repository或API.md契约。此前API.md改动仍需同步网站文章。

## 行为与恢复

- 单/两日卡分别增加1/2天剩余免签，并非立即签到，也不承诺固定明天执行；补签仅平台自然日补一天、现有条件/不另加奖励，由服务端重验。周年券未开放使用。
- 改名卡与积分改名分别提交，名称仅1..64 ASCII英数/连字符；前者trim，后者拒首尾空白。保留名、当前名、冲突及审核由服务端校验。积分确认说明现费用1000，成功显示实际paidPoints；客户端不伪造账号新名称。
- 未知响应冻结原命令/key，单一待确认操作阻止再消费别卡/改名；普通非空token刷新保留，同账号显式重登录/换号/退出清理。已未知后恢复403/429等不能证明此前未提交，仍保留原key；初次明确4xx释放。
- 写成功立刻记本次回执并结束恢复命令，随后读取当前背包及账号。读取/缓存刷新失败只报告已确认后的刷新失败，不重复扣卡/扣款；历史回执中的bag和userName不能覆盖当前状态。名称缓存按当前用户ID/旧名失效，不用回执强写auth.account。
- 只保留当前登录内存，可跨SPA页面；整页刷新/关闭/退出丢恢复信息，页面提示先核对背包、名称与流水。不写浏览器存储或凭据URL。

## 已运行验证

- 六组专项退出0：袋列表和五写契约/闭合body/日期与目标绑定；同键未知恢复、非空token轮换/路由复用、恢复403保留、回执剩余2而最新GET8不覆盖；成功后bag/account/cache故障不再扣；旧改名回执New-Name与当前Later-Name分离；取消/初次409；换身份迟到写/读丢弃、SSR不发写；完整SFC模板编译与SSR无假空袋，旧方法及mock已删除。日志`/tmp/rhythm-v3-client-inventory-tests.log`。HTTP/账号/确认/缓存边界适配，真实API与Pinia核心在内，非完整服务事务证明。
- 原财务七组、积分页五组回归退出0；日志`/tmp/rhythm-v3-client-inventory-finance-regression.log`、`/tmp/rhythm-v3-client-inventory-points-regression.log`。
- yarn run build类型/client/SSR通过，日志`/tmp/rhythm-v3-client-inventory-build.log`。生成client JS版本/map定向审计65项零命中；两仓git diff --check通过。无直接改CSS/min.js，无Maven。
- 实际IAB只连接本地合成HTTP/WS夹具：单日卡提交后503→切积分页/返回原命令仍在→同键恢复，仅一次卡；余3→4，两日卡→6；补签先409不消耗，再成功显示29天/20260201..20260301；非法下划线名拒绝，改名卡成功剩1且当前Card-Name；积分改名提交后503→同键恢复原Paid-Name/1000积分，余额10000→9000且只一次paidRenames。Hydration日志空，无真实账户/卡片/积分写入。
- 截图`/tmp/rhythm-v3-client-inventory-proof.png`，夹具日志`/tmp/rhythm-v3-client-inventory-fixture.log`。临时页与自建服务验后关闭。

## 完整人工链

1. 本批仅前端。真实联调先确认前批Java/FTL已IDEA重编译/重启及所需迁移部署；前端yarn run build，代理localhost:8080，第一方登录→设置→账号。背包真实数量、剩余天数、未知编码只读；空袋显示空，503不得显示假卡/假空。
2. 分别使用单/两日卡，确认框核一张与增加1/2天；取消无写，成功仅扣一张，GET重新读取现值。今天签到状态不因这两个调用伪造完成。无卡、余天上限失败不消耗。
3. 补签先准备无记录、未今日签到、当前最长/无卡等拒绝场景，卡和原记录不减少；可用条件下按平台自然日补一天，跨月/跨年回执日期/连续/最长与当前签到核对，无额外签到奖励。
4. 改名卡填非法/保留/已用/当前名及合法64字符边界，前者不扣卡；合法确认后扣1卡并重读用户名/名称链接/昵称缓存。积分改名不trim空白，确认当前费用，成功显示实际paidPoints，核账本仅一笔；不足额不改名或扣分。需实际消费，请用户在隔离测试账号执行；本批只用合成夹具。
5. 模拟写提交后丢响应，原表单/key冻结；离开账号SPA页再返回仍恢复同操作。正常401刷新不能丢key；恢复403/429保留；成功后bag/me GET失败只报后置读错，不再次消费。另端后来改名/用卡再回放旧键，回执显示当时结果，当前状态显示重新读取值，不覆盖后来操作。
6. 未确认/确认对话框期间退出或换号，旧命令和私有结果清空，迟到旧响应不能写新账号；整页刷新不保存恢复信息，须先核对原状态。PC与移动覆盖按钮/键盘/长编码/小屏、读写错误与跨页恢复。
7. 真实scope activity.read/write、改名双scope、凭据撤销、Cookie/Bearer，黑名单/test、验证码解封、关闭开发风控，新旧并发/缓存、后台真实签到任务和完整账款/索引仍需联调；夹具不能证明。

## 安全与反漏扫

无新增路由或anonymous.viewSkips，不改Before/AnonymousViewCheck/Captcha，认证写走既有Bearer+幂等；不往legacy apiKey传令牌，也未新加Cookie写例外。数量/日期/用户名严格类型，命令只固定端点和允许字段，无客户端数量、日期或actor控制；未知数量编码Vue文本只读、无v-html。无新增SQL/注入、远程抓取/SSRF、CDN、上传下载/目录入口或凭据URL。重复点击忙碌/确认互斥，无自动无限重试，但不替代服务端滥用限制。

只白名单投影不返原用户/回执内部字段；历史签名或账户证明不参与此模块。生成JS版本/map定向通过，未全扫全站CDN、目录枚举、上传/下载路径；真实未鉴权/越权、CSRF、XSS、批量滥用、全局验证码/黑名单及敏感信息未完成整站运行验收，不默认反漏扫全部通过。
