# v3 密码和MFA状态（2026-10-23）

## 文件和行为

- `src/main/java/org/b3log/symphony/processor/ApiV3SecurityProcessor.java`：GET `/api/v3/me/security/mfa`（profile.read），PUT `/api/v3/me/settings/password`（settings.write与共享写预算）；身份只取Bearer，body最多4096字节，无普通幂等回执。
- `service/ApiV3SecurityProofService.java`：共享第一方website_v3当前client/user/access SQL授权；caller开strict根后自己按client namespace→website client→当前user→当前access→credential state顺序锁，校验当前password32小写MD5和已启用的TOTP；返回内部用户+epoch仅供同事务使用，不作HTTP DTO。提供所有敏感写入共享attempt预算，不复用旧带写入verify/verifyLogin。
- `service/ApiV3SecuritySettingsService.java`：GET前后重验只投影enabled布尔；PUT按列改密码，与`AccountCredentialMgmtService.revokeInCurrentTransaction`在同strict根提交，返回changed/reauthenticationRequired，提交后本节点cache失效。新旧密码相同也成功撤权，避免依赖MySQL affectedRows语义。
- `util/ApiV3SecurityInputs.java`：只currentPassword/newPassword/currentTotp三字段，前两者必须小写32hex；TOTP可省略，提供时6位字符串，不接受数字或空字符串。校验后不把proof保存到普通幂等表或日志。
- `src/test/java/org/b3log/symphony/service/ApiV3SecurityTest.java`、`tools/api-v3-security-test.mjs`：真实SQL/strict/primitive/TOTP/auth/routes专项。

不改总register、公共文档、共享认证或旧密码入口；由root统一整合。epoch底座及SQL迁移由AccountCredential任务交付，本模块不另建表。

## 实际依赖和凭据语义

第一方资格不信任identity额外client/user对象，实际读取tokenId对应当前DB access，必须绑定本人+website_v3、active且未过期，当前client有效且有该scope。第三方即使有settings.write也不能操作。GET只第一方profile.read；不暴露密码/secret/epoch或其他联系方式。

password旧proof按当前DB值作恒定时间比较，已经启用MFA必须当前secret的6位TOTP。算法复用`MultiFactorAuthenticator.checkCode`纯校验，相邻时间窗沿已有规则；不调用旧MFAProcessor.verify带写入函数。损坏secret格式503，避免旧算法异常打印路径。所有caller在事务前调用共享checkAttempts，本节点有界缓存预算，不宣称跨节点统一计次或TOTP时间步一次性消费。

成功primitive同事务提高credentialEpoch、撤本人全部v3 active access/refresh及旧OpenID active refresh、删未消费OAuth code/PENDING consent，保留已决定历史。Cookie/apiKey/OpenID access的真实失效由epoch认证底座负责，不能仅凭此专项代替其跨实例认证测试。撤当前Bearer后不能用旧token获取普通幂等回执；丢提交确认可重新登录检查当前状态，不提供绕过当前认证的秘密回执。

## 验证

`node tools/api-v3-security-test.mjs`最终6组通过，日志`/tmp/security-tests.log`。Java25项目实际源、真实SQLite参数化SQL、bundled Latke严格JDBC提交与实际AccountCredentialMgmtService/StateRepository/v3/OpenID/code/consent撤权SQL；Token验证仅外部入口适配，实际Auth/Dispatcher/controller运行，真实TOTP算法并以固定时间校验。

覆盖：第一方当前资格、scope、owner/client/access状态/过期，缓存身份不升级；只enabled白名单；密码及TOTP错误/缺失；纯算法正确码；strict client→user→token→epoch锁序；按列写password与全撤同根且其他用户不变；故障发生于读、密码写、撤权写、commit时全部回滚；lost-ack已提交且旧token拒绝；缺/错Bearer、Cookie/apiKey不能冒充；真实两个路由、write middleware、no-store；typed输入、真实共享额度、损坏secret与相同密码的affectedRows边界。

未跑Maven、真实MySQL、完整服务、PC/移动浏览器；epoch认证全局/多节点、短信邮箱/MFA设置模块的完整安全链需其各自测试及总集成验收。

## 完整人工链路

先通过IDEA重编译/重启服务端，先执行epoch底座SQL迁移；保留两浏览器Cookie、旧apiKey、v3多个client access/refresh、旧OpenID access/refresh用于失效验证。

1. 第一方profile.read令牌GET MFA状态，未绑定返回enabled=false、已绑定true，无任何secret/URI/联系方式/epoch。第三方相同scope也403；未/错令牌401，错scope403。
2. 第一方settings.write PUT密码，提交正确当前小写MD5及新MD5；已绑定MFA加当前6位TOTP。返回changed=true/reauthenticationRequired=true；重新登录仅新密码有效。已使用当前token及其他旧v3凭据失效，所有旧Cookie/apiKey/OpenID应由epoch底座拒绝，检查不同实例及旧缓存。
3. 当前密码错误、MFA码错误或漏码422，密码和所有凭据状态均不变；第三方/停用client/过期access/停用用户不能变更。服务端并发修改password或撤access时只允许当前锁内有效证明成功。
4. 非法新摘要、大小写不合、非字符串/空TOTP、额外userId/clientId字段422，超body413；无普通Idempotency-Key要求，也不会写保存proof的回执。请求成功后旧token重试401，不能为重放绕过当前认证。
5. 注入数据库密码写、epoch写、令牌撤销或commit失败，密码/epoch/v3/OpenID/pending/code必须全回滚。丢提交应答可能已提交，重新登录确认，不重复恢复旧token。
6. 相同新旧摘要也应成功提高epoch并撤全部凭据；GET始终只读不创建epoch行；安全验证频繁返回429。PC/移动重新登录、找回密码、Cookie/API/OAuth同测。
7. 新IP/黑名单、/test验证码与解封链路、开发模式关闭风控、目录枚举和静态/CDN基线，做完整服务回归，不能用隔离专项代替。

## 安全与反漏扫（源码层）

明确Bearer且DB第一方，匿名无入口，未改anonymous.viewSkips/防扫描链；真实BeforeRequestHandler/验证码解封未运行。权限按当前owner/token/client/epoch，密码/TOTP逐请求；GET无写入，PUT不从Cookie/apiKey补身份。CSRF不能靠旧Cookie身份执行；输入值参数化无SQL注入；不抓外链，无SSRF/上传下载路径。无public HTML或用户文字回显，响应仅白名单布尔，故障不打印秘密或driver细节；proof预算与共享write预算有界，未压测。无新增静态/CDN/sourceMappingURL/可枚举目录，全站反漏扫尚未验。

## 公开片段（供root整合）

### MFA状态

`GET /api/v3/me/security/mfa`

读取本人MFA启用状态。仅第一方客户端，需要Bearer Token（profile.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/security/mfa' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.enabled | 是否已启用MFA | true |

### 修改密码

`PUT /api/v3/me/settings/password`

修改本人密码。仅第一方客户端，需要Bearer Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| currentPassword | 当前密码的小写32位MD5摘要 | 32位小写十六进制 |
| newPassword | 新密码的小写32位MD5摘要 | 32位小写十六进制 |
| currentTotp | 已启用MFA时必填，当前6位验证码字符串 | 123456 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/settings/password' -X PUT -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' --data '{"currentPassword":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","newPassword":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","currentTotp":"123456"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.changed | 是否成功 | true |
| data.reauthenticationRequired | 需要重新登录 | true |

> 成功后所有既有登录及授权凭据失效，需重新登录或授权。错误密码或MFA证明返回422。请求成功后旧凭据不能继续重试；遇网络未收到响应，请重新登录核实结果。
