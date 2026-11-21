# v3 客户端敏感账号设置

2026-10-25。接既有MFA状态、密码修改、MFA准备/启停、手机及邮箱发送/修改九条路由，后端总数264保持。用户确认新密码客户端输入6..128个UTF-16字符、不trim、Unicode可用且拒控制/无效代理字符；当前密码原样兼容旧账号。API仍只收小写MD5摘要，不宣称服务端能验证明文长度。账号APP二维码、导出、企业认证及其他模块继续迁移。

## 实际文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

| 文件 | 行为 |
|---|---|
| `src/api/v3-security.ts` | 只发送当前密码摘要/已有TOTP；新密码本地规则与摘要；九入口闭合body/类型投影；MFA仅本地otpauth资料，目标/6位码/极验四字段与字节预算 |
| `src/components/settings/OwnerSecurity.vue` | 独立状态读取、四操作面板、每步重新填写证明；成功或未知敏感变更重新登录；准备10分钟、输入/密钥随模式/身份/卸载清理，旧码重发失败仍可使用原流程 |
| `src/views/SettingsView.vue` | 替换旧密码/手机/邮箱/MFA表单，移除旧apiKey依赖和自动取MFA密钥；保留未迁移APP码/导出等 |
| `src/views/LoginView.vue` | 仅两种固定reason显示已修改或未确认提示，不显示URL任意文本/凭据 |
| `src/composables/useDialog.ts` | confirm增加可选AbortSignal，取消仅自己的对话框，不关闭后来其他对话框 |
| `src/composables/useGeetest4.ts` | 拒绝已销毁/换容器后的迟到初始化与成功回调，清reset定时器；脚本失败移除后可显式重试 |
| `src/api/fishpi.ts` | 删除无调用方的旧密码/手机/邮箱/MFA包装方法，其他旧消费者不改 |
| `tools/v3-security-test.mjs` | 七组真实TS/完整Vue输入/状态/SSR专项，账号/路由/HTTP/极验边界适配 |
| `tools/v3-security-lifecycle-test.mjs` | 三组实际确认框与验证码辅助生命周期专项，未连接真实挑战 |
| `AGENTS.md`、`README.md`、`docs/V3_MIGRATION.md` | 长期约定与迁移边界 |

Rhythm本批更新本记录、progress、UPDATE_LOG，并在API.md既有密码节注明网站客户端新密码输入规则；无Java/FTL/SQL/Repository修改、无新路由。

## 关键行为

- 安全状态未知或读取失败时不能当未启用，先成功读取才可操作；已有MFA需当前6位字符串，启用还需新准备对应6位码。当前密码不trim/截断，当前与新验证器码不混用。每次已发送操作立即清除本地证明输入；未写浏览器存储。
- MFA准备显式填写密码后申请，不在GET或加载页面时生成；资料仅本地手动密钥/readonly URI，不发外部二维码服务。十分钟/换模式/换身份/卸载清除，重读已启用状态清掉旧准备；重新准备替换服务端旧准备。响应丢失须新准备，不能假恢复密钥。
- 手机只收新11位大陆号；邮箱按后端trim/小写，前端基础格式与长度校验，最终合法性/占用仍后端校验。极验结果只投影四个协议字段，不携widget附加字段。新码受理后刷新本地目标/时间与输入；发送失败保留之前目标/旧码流程，可以取消挑战继续旧码。十分钟只是本地提示，服务端时间为准。
- 密码/联系方式/MFA启停成功撤销全部既有凭据；本地立即logout并带固定reason去登录，不伪造当前资料、不拿旧凭据继续写。网络/5xx/损坏回执等未知结果也退出并提示重新登录核实，不自动重复敏感变更；MFA单次请求仍有key，但本客户端不保存证明跨重新认证盲重放。明确422等拒绝保留页面和错误，不报成功；一般401认证失效也可能由全局传输先清身份，父页会回登录提示。
- 确认期间互斥；身份/卸载取消本人确认框，不能让旧手机/邮箱目标显示给新账号。极验异步初始化/回调有代际与容器绑定，不能在新账号里继续旧回调。JS字符串不可保证物理擦除，仅清引用/输入和后续使用，不承诺内存安全擦除。

## 实际证据

- 七组专项退出0，日志`/tmp/rhythm-v3-client-security-tests.log`：真实API摘要/新密码边界、otpauth白名单/重参拒绝、九路body、contact四字段；完整Vue密码成功/未知/422、MFA准备/启停、手机/邮箱两步证明、身份变化迟到密钥/确认拒绝、SSR、状态失败阻断、重发失败保留原码流程、已启用状态清准备。
- 三组生命周期专项退出0，日志`/tmp/rhythm-v3-client-security-lifecycle-tests.log`：真实AbortSignal只取消本人确认；真实验证码helper拒迟到init/success/容器替换并清实例/定时器；vendor脚本失败移除及新挂载重试。文档/对象/transport采用假边界，无真实极验挑战或服务授权。
- 财务七组、背包六组回归通过，日志`/tmp/rhythm-v3-client-security-finance-regression.log`与`...inventory-regression.log`；这些接口回归有HTTP/确认适配，真实共享对话框由生命周期专项与浏览器覆盖。
- yarn run build类型/client/SSR通过（`/tmp/rhythm-v3-client-security-build.log`），65生成client JS版本/map审计零命中（`...security-js-audit.json`），两仓diff --check通过。没有直接改CSS/min.js，没有Maven。
- 实际IAB仅本地合成HTTP/WS夹具：状态未启用→显式合成密码准备→本地手动密钥/URI→切手机清掉旧密钥、非法手机号不启动挑战/发送；密码首次422清输入，第二次合成提交后503→结束当前登录→login固定security-unconfirmed提示核实。Hydration日志空；截图`/tmp/rhythm-v3-client-security-proof.png`，夹具日志`/tmp/rhythm-v3-client-security-fixture.log`无证明正文。无真实改密、发码、TOTP启停或极验完成。临时页/服务验后关闭。

## 完整人工链

1. 本批前端；真实联调前确认前批Java/FTL已IDEA重编译/重启及credential/MFA/contact迁移部署。前端yarn run build，代理localhost:8080，第一方登录→设置→账号→账号安全。profile.read/settings.write缺失、状态503不能当未启用或允许写；重试读取后恢复。
2. 修改密码：用户亲自填写当前密码/已有TOTP、新密码两次（6/128边界、Unicode/emoji/首尾空白、拒控制/无效字符）。确认取消不写；错误证明不改、不退出；成功全部设备/旧apiKey/OpenID/v3等失效，登录页有固定提示，用户用新密码重新登录。此类真实凭据操作由用户接管，不由代理输入/提交。
3. MFA：用户填写当前证明→显式准备→本地验证器手动录入资料；离开/切模式/换号不留旧资料；十分钟过期及新准备废旧。启用再次填写当前密码及新验证码，错误/过期拒绝；成功重新登录需新MFA。停用要求当前密码及当前MFA，成功后重新登录；不提供忘密码绕过。记录或备份密钥由用户本地完成，不向外部QR服务发送。
4. 手机/邮箱：用户填写新目标及当前证明→真实极验（由用户接管）→受理后收6位码→重新填写当前证明/旧MFA及收到的码→确认。目标绑定、占用/旧目标/错误码/过期/发送失败/重新发送逐项核对；失败不消费旧码，成功消费并撤全部凭据。取消挑战/绑定清输入；重发失败仍可核对原有效码，未送达不冒充已改绑定。
5. 在隔离环境模拟提交后响应丢失：本地退出、未确认提示，不自动重改；重新登录读取当前MFA或验证新/旧密码、联系方式结果，再决定后续。状态确认前不要假报成功或把旧证明当有效恢复凭据。
6. 挂起MFA准备、验证码初始化/发送、确认/写请求期间换号或离页，旧密钥/联系方式/确认框/成功回调不得污染新身份；正常非空token刷新仍由当前身份版本判断，不因新token升级旧证明。验证极验脚本失败可取消后重试，旧实例不复位新实例。
7. PC/移动、键盘、表单自动填充、readonly密钥/URI、密码字段清理与MFA/联系流程完整走通。真实Cookie/epoch撤销、所有设备/WS/apiKey/OpenID、极验/SMS/邮箱/TOTP算法、跨实例并发与权限、黑名单/test/解封及开发模式仍需验收；上述夹具不证明这些通过。

## 安全及反漏扫

只接既有第一方认证GET/POST/PUT/DELETE，不新增匿名路由/viewSkips或Cookie例外，未改Before/AnonymousViewCheck/Captcha解封链。新密码/验证码/目标/URI与body严格限制，响应白名单；证明只body摘要和TOTP，无token/密钥/联系方式URL参数或日志，MFA无外部QR。普通文本Vue转义、无v-html/任意CSS/新SQL/SSRF抓取；captcha供应商脚本为既有固定URL，不允许入参控制地址。

captcha/确认旧回调隔离，重复提交互斥，无自动无限写重试；不替代服务端尝试/发送限制。旧APP扫码模块仍是后续范围，不能宣称整页无外部二维码或旧凭据消费者。65 JS版本/map定向通过，未下载全站CDN资源或全扫目录/上传下载路径；真实未鉴权/越权、CSRF、XSS、SSRF、滥用、敏感信息及全局风控未整站运行验证，不能默认全站反漏扫通过。
