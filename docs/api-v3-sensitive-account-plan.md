# API v3 敏感账号设置拆分（2026-10-23）

内部开发依据，不是已开放接口清单，也不直接复制进 API.md。本轮仅新增本文件；未改实现、旧接口、公共文档、冻结的转账模块；未执行 Maven、数据库请求或浏览器操作。

## 用户已确认的实施规则

A/B/C均已确认：仅第一方、每次当前密码，已有2FA需当前TOTP，启用另验证新密钥TOTP；手机/邮箱仅验证新目标，6位码10分钟及极验，成功保存新码后废同用途旧码；MFA准备10分钟且新准备废旧，secret/otpauth仅首次返回。成功敏感修改须撤全部v3、code、pending及全部Cookie/旧apiKey/OpenID，旧客户端需重新登录授权。下方“待确认”为盘点历史，不再重复询问。持久凭据版本底座与九条敏感设置已于第十二轮总接线，普通积分改名于第十三轮接线；完整运行验收仍未完成。

## 当前入口与实际行为

以下旧路由均在 `SettingsProcessor.register`（253–267行）或 `MFAProcessor.register`（70–73行）可达。`loginCheck::handle` 同时接受页面会话和 apiKey，并不等同于新 v3 Bearer 身份升级证明。

| 能力 | 旧 METHOD /path / 方法 | 已存在的实际业务与缺口 |
| --- | --- | --- |
| 手机验证码发送 | POST `/settings/phone/vc`；`SettingsProcessor.sendPhoneVC`（551行） | `userPhone` 使用旧手机号正则、极验；禁止与当前绑定相同，检查是否其他用户占用；AccountSmsQuota；6位码，PHONE/BIND_PHONE，10分钟。先发短信再保存，短信失败不保存。若按owner+type+purpose找到任一码直接“已发送”，查询不约束本次receiver、过期或status。日志包含目标手机号和码，v3不得复制。 |
| 邮箱验证码发送 | POST `/settings/email/vc`；`sendEmailVC`（625行） | trim/lowercase与Strings.isEmail、极验；禁止相同邮箱，检查占用；6位码，EMAIL/BIND_EMAIL，10分钟。仅保存UNSENT，异步邮件worker投递。也存在旧任一码挡住新目标/到期后重发的问题；方法未见与手机对等的AccountSmsQuota。 |
| 修改手机 | POST `/settings/phone`；`updatePhone`（685行） | 入参captcha作为验证码，取本人最新PHONE/BIND_PHONE，字符串相等即把记录receiver写入手机；未在该查询/方法校验expired/status；`UserMgmtService.updateUserPhone`独立事务检查占用并更新，再独立removeByCode。未核对旧手机或密码/TOTP，消费非原子。 |
| 修改邮箱 | POST `/settings/email`；`updateEmail`（727行） | 同上，EMAIL/BIND_EMAIL，写记录receiver；`updateUserEmail`独立事务后全局按码删。没有本次请求地址的明确绑定/到期检查。 |
| 修改密码 | POST `/settings/password`；`updatePassword`（1204行） | 路由挂旧CSRF及UpdatePasswordValidationMidware；`userPassword`与Sessions用户密码值比对，`userNewPassword`写入。前端`settings.js`1395行传MD5。validation重复检查旧字段，未真正验证新字段；`UserMgmtService.updatePassword`只更新密码并普通commit，不主动撤v3令牌/授权码/待确认授权。 |
| 普通积分改名 | POST `/settings/username`；`updateUserName`（504行） | 本人Session积分预检查，ProfileWriteQuota、CensorFactory；`UserMgmtService.updateUserName`先提交名称，再调用独立积分transfer（不检查返回），可能先改名后扣费失败。费用是`Pointtransfer.TRANSFER_SUM_C_CHANGE_USERNAME = Symphonys.POINT_CHANGE_USERNAME`，本轮不猜硬编码金额。 |
| MFA状态 | GET `/mfa/enabled`；`MFAProcessor.enabledMFA`（150行） | 仅依据secret2fa是否为空，返回成功/错误和文字。读取状态可以复用布尔语义，不应返回已绑定secret。 |
| MFA准备 | GET `/mfa`；`getMFA`（83行） | 已绑定拒绝；新secret放进本实例、最多100项的MFA_CACHE，没有TTL/持久化，重复读取返同secret。返回secret、user、qrCodeLink；二维码URL指向第三方服务，包含otpauth secret，v3不能照搬远端二维码URL。 |
| MFA启用/检查 | GET `/mfa/verify?code=`；`verifyMFA`（114行）、静态`verify`（182行附近） | 初次校验待绑定secret，成功普通updateUser后删cache；已有secret只校验TOTP，并不修改。启用需区分“验证已有因子”与“消费准备请求”，不能继续同一GET多语义。 |
| MFA停用 | GET `/mfa/remove`；`removeMFA`（133行） | 仅登录/apiKey，直接清secret2fa，未要求密码或TOTP；普通updateUser，未统一撤权。v3不能复用该handler作为授权边界。 |

旧发送/修改/MFA依赖Sessions；MFA还在方法里再次读取query apiKey。新v3身份只能来自认证中间件当前Bearer上下文；不得用这些函数的session/apiKey回退替代证明。

## 已实现核心可复用范围

1. `AccountVerificationService.consumePhoneCodeInCurrentTransaction` / 私有`consume`：只接受REGISTER或RESET_PWD，严格要求调用方已有事务，当前锁本人及当前手机号，按owner/receiver/type/purpose/code/未到期/status匹配并删除同值重复记录；消费和业务一起提交。**不支持BIND_PHONE/BIND_EMAIL，不能直接传purpose=4/3或仅改枚举绕过“当前手机号”绑定假设。** 新绑定目标与旧联系方式快照需要新的清晰方法。
2. `VerifycodeRepository.phoneCodesForUpdate`、`addAccountPhoneCodeInCurrentTransaction`：同样限定手机及注册/重置用途。模型现有EMAIL=0、PHONE=1，REGISTER=0、RESET_PWD=1、BIND_EMAIL=3、BIND_PHONE=4，UNSENT=0、SENT=1。需要扩展专用绑定插入/消费，不调用全局`getVerifycode(code)`/`removeByCode(code)`。
3. `VerifycodeQueryService.getVerifycodeByUserId`仅owner/type/purpose排序取首，没有到期/status/receiver；`VerifycodeMgmtService.removeByCode`按code跨用户删除，吞异常，不能作为v3一次性消费；`addVerifycode`/`sendEmailVerifycode`旧独立事务也不是共享业务原子核心。
4. `AccountPasswordResetRequestService.request`：现有匿名重置发送先配额/极验，远程发送不持SQL锁，再严格事务重查当前有效owner+phone保存；公共返回accepted不枚举账号。现有重置码有效期为一天，与绑定旧10分钟不同；不能擅自把所有验证码统一一天。
5. `AccountPasswordResetService.complete/reset`：v3只接受小写32位十六进制密码摘要；严格根先`ApiV3ClientRepository.lockForAccountResetInCurrentTransaction`锁全client，再当前用户、手机码消费、只写password、删除pending授权请求与未消费授权码、撤销全部active用户Token，同事务提交。是敏感修改撤权的锁序模板，不能把匿名手机号重置proof直接当作已登录改邮箱proof。
6. `ApiV3TokenRepository.revokeUserInCurrentTransaction`按userId更新全部active Token，涵盖所有client和family；`ApiV3AuthorizationCodeRepository.invalidatePendingForUserInCurrentTransaction`删除consumedAt=0；`ApiV3AuthorizationRequestRepository.invalidatePendingForUserInCurrentTransaction`只删PENDING，保留已决定历史。不要只调用revokeFamily或分页取部分再循环。
7. `UserNameMgmtService.renameInCurrentTransaction`：现有命名空间锁→当前owner→目标名查询→按列更新；UserNameRules控制字符长度/保留名；`afterTransaction`提交后刷新名称缓存。普通积分改名可把它与旧费用、真实积分primitive、回执放入同严格根，账户对须事务前；不要复制旧“先改名再单独transfer”。改名卡专用逻辑已完整独立，不要改变其费用/卡片语义。
8. `MultiFactorAuthenticator.checkCode(secret,code,time)`是已有TOTP算法（30秒、相邻窗口），可抽纯校验；不含限尝试、用过时间步的防重放、准备请求TTL、身份升级或事务。`MFAProcessor.verifyLogin`在未绑定时返回true，不能作为“本次已提供二次因子”的证据；`verify`带写入，不可当纯校验调用。

## Cookie撤权的实际边界

`Sessions.login`（311行）把密码摘要嵌入加密Cookie；`Sessions.currentCookieUser`（406行）把Cookie摘要与本节点缓存中的用户密码相等比较，cache miss会重新按Cookie尝试登录。`Sessions.invalidate`（542行）只移除本节点用户和CSRF缓存；`logout`仅清当前响应Cookie及本节点缓存/本请求Session。

因此：

- 修改密码后旧摘要Cookie在读取到当前用户时失效；其他实例若仍有旧用户缓存，不能宣称“跨实例立即失效”。`UserRepository.evictSettingsCaches`会调用本节点Sessions.invalidate，没有全节点撤销证据。
- 修改手机/邮箱/MFA若不改密码，单纯invalidate不能永久撤销现存Cookie；下次cache miss旧Cookie仍可重登。
- 若承诺这些敏感操作后全部Cookie登出，需要持久session/credential epoch或等效可撤销凭据，每次Cookie认证实际检查当前版本（含多实例缓存策略），并让登录、当前Cookie读取、重置密码与相关旧入口统一遵守；仅新增v3 endpoint无法独自兑现。
- 旧apiKey与/openid也独立于v3Token；是否轮换旧apiKey/撤旧OpenID是额外兼容决定，不能把“撤所有v3 family”表述为已撤全部历史凭据。

## 最少需要用户确认的三组业务决定

不是重复询问已有密码重置规则，也不是把技术实现细节交给用户。

### A. 谁能修改敏感凭据，以及升级证明

建议仅第一方当前有效本人可进行手机/邮箱/密码/MFA变更；普通第三方settings.write不自动拥有改安全凭据能力。密码变更要求当前密码摘要，已启用MFA再提供当前TOTP；联系方式变更/停用MFA同样要求当前密码＋已绑定MFA证明；启用MFA还必须校验新secret的TOTP。忘记旧密码走现有手机重置，无法提供MFA的恢复流程需要单独业务定义，不能给普通停用接口隐含绕过。

待确认：是否采用该能力范围和组合；是否允许“仅旧手机号短信”代替旧密码/MFA。若允许可复用的短期step-up票据，应绑定owner/client/token或Cookie会话、操作目的、目标哈希、凭据版本、到期、一次性消费，不能是一枚全局通用“已验证”布尔值。建议首版直接每次提交proof，避免再引入可转移票据。

### B. 验证码/准备请求契约

建议手机号/邮箱绑定保留旧10分钟、6位数及极验；每次码绑定本人、用途、规范化目标地址及发起时旧联系方式/凭据版本，修改时重读有效本人及目标唯一性，同事务只消费该owner+purpose+target的有效码（同值重复也一起消耗），业务失败保留。切换目标使旧目标码不能用于新请求；重发策略采用同目标有效码复用还是发送新码并使旧码失效，需要明确一种。

建议明确新目标证明即可，还是还要求旧手机号/旧邮箱证明（与A的密码/MFA不同）。MFA准备建议服务端生成secret、短期owner/client绑定handle、成功启用一次性消费；返回secret/本地绘制用otpauth URI仅这一次准备响应，no-store，永不写日志/幂等普通回执，不把secret发送给第三方二维码服务。准备TTL（建议10分钟）及重试时是否重新发secret需要确认；不沿用无TTL全局cache。

### C. 哪些成功修改撤销哪些登录

建议密码、手机、邮箱、MFA启用/停用成功时，账号写入＋码/准备请求消费＋全部v3 access/refresh family＋未消费code＋PENDING consent在同一严格根完成；当前请求完成后要求重新登录，旧token不能用普通幂等重放入口继续认证。对网络丢确认应设计不泄露秘密的状态确认，不能为了取旧回执跳过当前认证。

Cookie如要求一并全部失效，应先实现上述持久版本契约；旧apiKey/openid是否同时轮换请明确。普通积分改名建议沿既有身份延续，不额外撤权，仍用相同幂等键防重复收费；若旧名写在历史消息快照不回写，当前profile及新消息读新名。

## 原始接口拆分建议（历史，当前已接线）

| 顺序 | 候选能力 / 路由 | 依赖及完成标准 |
| --- | --- | --- |
| 1 | GET `/api/v3/me/security/mfa` | 当前本人有效、第一方范围确定；只enabled布尔，无secret、URL、邮箱手机号。 |
| 2 | POST `/api/v3/me/settings/phone/verification`、POST `/api/v3/me/settings/email/verification` | A/B先确认；发送预算与极验、规范化和唯一性、远程调用不持事务、owner/target/purpose保存、敏感日志移除。 |
| 3 | PUT `/api/v3/me/settings/phone`、PUT `/api/v3/me/settings/email` | A/B/C、目标命名空间/唯一约束锁方案；严格同根proof+消费+按列更新+撤权。不得复用旧全局按码删除。 |
| 4 | PUT `/api/v3/me/settings/password` | A/C；当前数据库旧密码比对、新摘要格式、MFA及失败预算；密码写入与全部撤权同根，确认Cookie版本机制范围。 |
| 5 | POST `/api/v3/me/security/mfa/setup`、PUT `/api/v3/me/security/mfa`、DELETE `/api/v3/me/security/mfa` | A/B/C与准备存储/TTL/一次性用途；启用新secret证明，停用旧因子证明，避免任何GET写入及外部二维码。 |
| 可并行 | PUT `/api/v3/me/settings/user-name`（候选，避免与改名卡接口混淆） | 沿既有UserNameRules/Censor/Quota/费用；普通改名扣积分同根及幂等、提交后缓存；是否与旧普通积分改名共享核心涉及兼容变化，另列确认，不能假定过去对“改名卡”的同意覆盖本入口。 |

原候选共10个METHOD+path，现已按第十二/十三轮完成总接线；本表保留当时依赖与待确认事项，仅作历史设计，不替代API.md当前契约。

## 专项验收门槛

实施时先告知用户IDEA重编译/重启再联调。至少覆盖：当前Bearer与Cookie/apiKey冒用隔离、第三方client被拒、当前密码错误/旧缓存/旧TOTP、手机邮箱目标切换/他人占用、码过期/重复值/错用途/已消费、准备请求过期/他人/重放、SQL更新或撤权提交失败全部回滚、丢确认恢复、已有v3各client/family及pending授权确实不能再用、Cookie当前及其他实例旧缓存、PC/移动登录与找回路径、失败不泄露完整联系方式/secret/验证码/密码摘要。

本只读任务没有新增公网入口、静态资源、CDN URL、上传下载路径、anonymous.viewSkips或CSRF行为。后续新增路由仍须逐项核验匿名盾、BeforeRequestHandler黑名单、/test验证码/解封、接口授权及静态资源反漏扫；本盘点不提供任何运行通过结论。


## 2026-10-24 普通积分改名实施

用户已授权新增普通积分改名v3入口并明确旧`/settings/username`本批不修改。PUT `/api/v3/me/settings/user-name`已总接线，要求activity.write/settings.write与Idempotency-Key，规则和实际费用沿旧常量。上面的十条候选现已全部有v3入口；九条敏感设置与本条普通改名采用各自的身份/事务边界，不能将普通改名写成必须第一方密码/TOTP证明。普通改名不额外撤凭据，历史内容用户名快照不批量回写；同键恢复原结果不覆盖后来改名。完整文件、人工步骤和隔离验证局限见[工作记录](api-v3-user-name-work.md)，真实服务验收尚未完成。
