# v3 联系方式验证与修改（2026-10-23）

## 文件及实际行为

- `src/main/java/org/b3log/symphony/processor/ApiV3ContactSettingsProcessor.java`：4个新Bearer settings.write入口，共享写预算，body最多8192字节；只解析白名单字段，不取Cookie/apiKey身份。
- `src/main/java/org/b3log/symphony/service/ApiV3ContactSettingsService.java`：第一方当前密码/已有TOTP证明；先strict根验证当前owner/新目标唯一性/epoch/旧值快照，提交释放全部SQL锁后再极验及provider发送，第二strict根重验证身份/epoch/旧联系值和目标占用，成功持久新码并废同用途旧码。
- `src/main/java/org/b3log/symphony/util/ApiV3ContactInputs.java`：目标手机号沿11位大陆格式，邮箱trim/lowercase后Strings.isEmail、255 UTF-16及空白/控制字符拒绝；密码32小写hex，现有TOTP6位字符串，申请需有界极验proof JSON，修改需6位code。
- `src/main/java/org/b3log/symphony/model/AccountContactCode.java`、`repository/AccountContactCodeRepository.java`、`sql/20261023_api_v3_contact_verification.sql`及`src/main/resources/repository.json`：私有account_contact_code表，owner/purpose唯一；存新目标、client、epoch、过期时间及HMAC codeHash/previousContactHash，不存短信码或旧联系方式明文。Repository/table名无symphony_前缀，SQL有部署前缀。
- `src/test/java/org/b3log/symphony/service/ApiV3ContactTest.java`、`tools/api-v3-contact-test.mjs`：复用Security实际SQL/strict/proof/credential fixture（未修改Security源或其test），4路由及新码/消费/撤权专项。

root负责Mails.sendAccountBindingCode/AccountMailResponses真实供应商适配及公共接线，本任务未修改Mails、总register、公共API/更新日志/AGENTS、旧联系方式入口或前端。

## 关键边界

仅website_v3第一方当前有效本人，settings.write不自动给第三方安全权限。共享proof按namespace→client→user→当前access→epoch锁序，比较当前密码，已有MFA逐次纯TOTP校验。两阶段都检查proof；不持SQL锁调用远程服务。provider失败/抛错、begin或持久化/commit失败均无秘密错误详情；旧成功码保持。成功第二根才原子替换本人同用途旧码，新请求目标切换也废旧用途码。码为6位、10分钟；短信使用共享AccountSmsQuota当前用户名键，邮件/短信均受共享proof/write预算与极验。并未声称邮件新增了与SMS等价的收件人全局配额。

HMAC绑定owner/purpose/newTarget/epoch，previousContactHash另绑定旧联系方式精确值；发送前后比对旧值，消费再按当前旧值重算，即使旧兼容入口无epoch变更也不能误消费陈旧绑定。无需用户提供旧手机号/旧邮箱验证码。该快照不暴露给HTTP，也不记录旧联系值明文。坏数据hash/版本类型503；合法但错误owner/client/target/用途/epoch/旧值/过期码422。

修改时新目标仍未被占用，再锁对应owner/purpose码行；校验、按列改手机/邮箱、删除码和AccountCredentialMgmtService全部撤权同strict根。失败不吞码不改联系方式，提交后清本节点缓存。丢提交确认可能已成功；旧token已失效，不设普通幂等秘密回执，不为恢复响应绕过认证。

新v3各写入口经共享client namespace锁串行检查目标唯一性；旧联系方式入口未整体接共享核心，无数据库全局唯一索引，因此不宣称新旧并发的全局唯一/原子性。既有其他用途验证码表不共用，也不调用全局按code删除。

## 实测证据

`node tools/api-v3-contact-test.mjs`最终8组通过，日志`/tmp/contact-tests.log`。

Java25实际新源、真实SQLite参数化SQL、生产SecurityProof/AccountCredentialMgmtService/credential state及v3/OpenID/code/consent仓库撤权、真实strict JDBC提交；仅provider send/远程极验/SMS预算边界适配，实际HMAC生成及存储/比较执行。共享proof额度本身已由Security实际专项验证，本测试控制gate方便故障覆盖。Mails真实方法提取/真实AccountMailResponses供应商解析由root另有3组专项（无真实SMTP/HTTP调用），不把本任务send mock称为真实供应商联调。

8组覆盖：

1. 手机/邮箱正常申请、provider明确无事务锁、6位10分钟、只存HMAC及owner/client/epoch/newTarget，重发废旧、修改/消费/全撤同根。
2. provider拒绝/抛错，发送后存储或commit/begin失败，保留旧成功码，异常不泄露目标/码。
3. provider窗口epoch、旧联系方式或占用变化拒绝新保存，旧码保留。
4. owner/client/purpose/target/epoch/旧值/HMAC/过期绑定，错码不吞码。
5. contact字段写、撤权或commit失败全部回滚；丢确认已提交，旧access拒绝。
6. 输入、规范化、极验/SMS/shared proof gate、恶意email头/字段/码拒绝。
7. 4个真实Dispatcher/auth/write/controller入口，Cookie/query身份不能代替Bearer、no-store及旧access失效。
8. 损坏hash/version/null字段503，旧邮箱大小写精确快照、post-provider begin失败脱敏。

未跑Maven，未执行SQL迁移、真实MySQL、完整服务、真实短信/邮件/极验、PC/移动浏览器和多实例并发。SQLite不证明MySQL锁/排序/唯一性执行计划。

## 人工全链路

先通过IDEA重编译/重启，并先部署credential epoch与contact验证SQL迁移；同步repository.json。

1. 第一方settings.write令牌申请新手机/邮箱验证码，提供当前32位MD5密码、已有MFA时的当前TOTP及极验proof；收到6位码，响应仅accepted=true，不返回目标/码/epoch。错误密码/因子/极验422，第三方同scope403，未/错令牌401，错scope403。
2. 相同用途再次申请，使用旧码修改应422，新码可用；切换新目标后旧目标码不可用。发送失败/数据库保存失败时旧有效码仍可消费，不能因失败先删旧码。极验、短信额度及共享写/证明额度分别回归429/422行为。
3. 提交target/currentPassword/currentTotp/code进行修改，成功返回changed/reauthenticationRequired，重新登录资料为新联系方式；当前及其他v3、Cookie/apiKey/OpenID旧凭据由epoch底座拒绝。没有要求旧联系方式验证码。
4. 其他用户已占用、与当前相同、错误用途、超10分钟、他人码、已消费、目标变换、MFA已变、密码已变、epoch已变，拒绝且不吞合法码。旧入口在申请后改变旧联系方式时，本新码也须拒绝，不可悄悄用于新账号状态。
5. 在provider发送期间另一连接改epoch/旧值/占用，第二根拒绝；模拟contact写、删码、epoch或撤权、commit失败，联系方式/码/凭据一起回滚。丢提交应答重新登录确认，不允许旧token恢复秘密回执。
6. 邮箱首尾空格/大小写规范化；非法/过长/控制字符/CRLF地址、错误手机号格式、数字形式code、附加owner/client字段422，过大body413。检查DB无明文code或previousContact，管理接口/日志无完整联系方式/密码/验证码。
7. 两浏览器PC/移动和不同实例缓存验证退出与重登录/找回；旧入口并发另做存量限制验收。实际SMTP/SMS供应商失败响应和极验超时需真实环境回归。
8. 新IP、黑名单、/test验证码及validateCaptcha解封、开发风控关闭、静态/CDN/目录/上传下载基线，执行完整运行反漏扫；本专项不能替代。

## 安全/反漏扫（源码层）

4入口明确当前Bearer+第一方+settings.write，无匿名裸入口；未改anonymous.viewSkips/BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor，真实防扫描链未运行。权限、当前密码/TOTP、码绑定与旧值快照防越权，Cookie/apiKey不能为此入口升级身份。CSRF依赖明确Bearer而非页面态；SQL全部值参数化、动态列仅固定phone/email二选一，无注入。邮箱控制字符拒绝防header注入；固定provider/极验地址不采纳用户URL，无SSRF。仅回布尔无用户文本回显，HMAC及错误脱敏避免敏感码泄露；共享写/proof/SMS预算和极验限制批量，未压测或证明全网收件人额度。无新增公网静态/CDN/sourceMappingURL、目录及上传下载路径，未全站运行复扫。

## 公开API片段（供root整合）

### 申请手机绑定验证码

`POST /api/v3/me/settings/phone/verification`

向新手机号发送绑定验证码。仅第一方客户端，需要Bearer Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| target | 新手机号，11位大陆手机号 | 13900000001 |
| currentPassword | 当前密码的小写32位MD5摘要 | 32位小写十六进制 |
| currentTotp | 已启用MFA时必填，6位字符串 | 123456 |
| captcha | 极验结果JSON字符串，lot_number/captcha_output/pass_token/gen_time四字段 | 组件返回值 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/settings/phone/verification' -X POST -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' --data '{"target":"13900000001","currentPassword":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","currentTotp":"123456","captcha":"{\"lot_number\":\"LOT\",\"captcha_output\":\"OUTPUT\",\"pass_token\":\"PASS\",\"gen_time\":\"TIME\"}"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.accepted | 发送申请成功 | true |

> 验证码6位、10分钟有效；每次成功申请新码后，该用途的旧码失效。目标必须未占用且与当前不同。发送失败不会使旧有效码失效。

### 修改手机号

`PUT /api/v3/me/settings/phone`

使用新手机号验证码修改绑定。仅第一方客户端，需要Bearer Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| target | 申请验证码时的新手机号 | 13900000001 |
| currentPassword | 当前密码的小写32位MD5摘要 | 32位小写十六进制 |
| currentTotp | 已启用MFA时必填，6位字符串 | 123456 |
| code | 收到的6位验证码字符串 | 654321 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/settings/phone' -X PUT -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' --data '{"target":"13900000001","currentPassword":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","currentTotp":"123456","code":"654321"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.changed | 修改成功 | true |
| data.reauthenticationRequired | 需要重新登录 | true |

> 成功后所有既有登录及授权凭据失效，需要重新登录或授权。失败不消费验证码；账号资料已变化时需重新申请。请求未收到响应时，请重新登录核实结果。

### 申请邮箱绑定验证码

`POST /api/v3/me/settings/email/verification`

向新邮箱发送绑定验证码。仅第一方客户端，需要Bearer Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| target | 新邮箱，去首尾空白后转小写，最多255字符 | new@example.com |
| currentPassword | 当前密码的小写32位MD5摘要 | 32位小写十六进制 |
| currentTotp | 已启用MFA时必填，6位字符串 | 123456 |
| captcha | 极验结果JSON字符串，字段同手机验证码申请 | 组件返回值 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/settings/email/verification' -X POST -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' --data '{"target":"new@example.com","currentPassword":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","currentTotp":"123456","captcha":"{\"lot_number\":\"LOT\",\"captcha_output\":\"OUTPUT\",\"pass_token\":\"PASS\",\"gen_time\":\"TIME\"}"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.accepted | 发送申请成功 | true |

> 验证码6位、10分钟有效；成功申请后该用途旧码失效，发送失败保留旧有效码。只需验证新邮箱，无需旧邮箱验证码。

### 修改邮箱

`PUT /api/v3/me/settings/email`

使用新邮箱验证码修改绑定。仅第一方客户端，需要Bearer Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| target | 申请验证码时的新邮箱 | new@example.com |
| currentPassword | 当前密码的小写32位MD5摘要 | 32位小写十六进制 |
| currentTotp | 已启用MFA时必填，6位字符串 | 123456 |
| code | 收到的6位验证码字符串 | 654321 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/settings/email' -X PUT -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' --data '{"target":"new@example.com","currentPassword":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","currentTotp":"123456","code":"654321"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.changed | 修改成功 | true |
| data.reauthenticationRequired | 需要重新登录 | true |

> 成功后所有既有登录及授权凭据失效，需重新登录或授权；失败不消费验证码，账号资料已变化时需重新申请。请求未收到响应时，请重新登录核实结果。
