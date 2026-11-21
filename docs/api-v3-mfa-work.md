# v3 MFA 准备、启用和停用

## 实际文件与集成边界

新增：

- `src/main/java/org/b3log/symphony/processor/ApiV3MfaProcessor.java`：POST准备、PUT启用、DELETE停用三路由；settings.write与通用写中间件，响应no-store。
- `src/main/java/org/b3log/symphony/service/ApiV3MfaService.java`：独立严格根、当前证明、准备绑定和消费、按列secret2fa写入、全凭据撤销、启停布尔回执；prepare无普通回执。
- `src/main/java/org/b3log/symphony/util/ApiV3MfaInputs.java`、`ApiV3MfaSecrets.java`：输入白名单、强随机Base32 secret、本地otpauth URI与纯TOTP校验。
- `src/main/java/org/b3log/symphony/repository/ApiV3MfaPreparationRepository.java`：actor唯一准备行，super无symphony_前缀。
- `sql/20261023_api_v3_mfa.sql`、`src/main/resources/repository.json`对应api_v3_mfa_preparation元数据；JSON与凭据底座代理串行追加，保留其他修改。
- `src/test/java/org/b3log/symphony/service/ApiV3MfaTest.java`、`tools/api-v3-mfa-test.mjs`：真实共享安全证明/撤权/准备与回执SQL的Java25专项。

依赖其他工作包：`ApiV3SecurityProofService`和`ApiV3SecurityInputs`共享当前第一方/密码/TOTP与敏感写额度；`AccountCredentialMgmtService`及epoch底座负责全凭据撤销。旧MFAProcessor/算法生成器/二维码入口不修改；不调用会写入的旧verify/verifyLogin。总register/公共API/更新日志/AGENTS由主代理合并。

必须先应用本MFA SQL及凭据epoch底座SQL，再开放路由。repository.json已更新；尚未在真实MySQL应用迁移。

## 行为、锁序与敏感材料

第一方资格按DB当前website_v3 access/client/user/settings.write检查，不相信传入identity额外client属性；Cookie/apiKey不提升身份。每次准备、启用、停用以及普通回执重放都要当前密码小写32位MD5；当前已有MFA则另需当前secret的6位TOTP。

锁序由共享proof建立：client namespace→website client→当前user→当前access→credential state，之后才准备行/普通回执；revoke重取已持user/state，撤旧OpenID refresh、v3 pending/code/token。锁和MFA变更、准备消费/删除、回执同严格根；提交后才返回。出根后清本人settings caches；不宣称跨实例即时断开所有已有网络连接。

prepare仅MFA未启用者；已启用409（正确currentTotp仍需提供以通过证明），必须先停用再绑定。SecureRandom20字节产生32个Base32字符，不沿旧固定seed；HMAC-SHA1、6位、30秒及相邻±1窗保持原校验口径。URI本地拼成otpauth://totp/FishPi%3A{actor}，issuer=FishPi，不使用外部二维码服务。

准备有效期10分钟。oId=actor唯一行，新prepare替换旧hash/secret；绑定actor、当前credentialEpoch、当前密码与MFA状态的单向摘要。raw preparationId只首次响应，表只存hash；secret/otpauthUri仅当次成功响应，不进入普通回执。prepare没有Idempotency-Key与取回接口，重试会生成新准备并废弃旧准备；提交成功但响应丢失时也不从回执重放secret，应重新prepare。

启用需preparationId及新secret对应的newTotp；匹配本人、当前准备hash、绑定、状态、创建时间与精确TTL、未过期，验证后消费并清除准备secret/binding。写user.secret2fa及全撤同根。过期/已替换/已消费/绑定变化409，错误新码422且不消费准备；MFA已启用409。停用将secret2fa清空并删除本人准备，再全撤；已停用再次操作也会撤销旧凭据，不绕过当前密码证明。当前锁账号存在且已经disabled时兼容JDBC零changedRows。

启用与停用的普通幂等回执只enabled布尔；newTotp/currentTotp/currentPassword/secret/otpauthUri不存入回执，请求指纹也不包含这些证明。enable仅以preparationHash绑定业务请求。同key重放仍先当前认证与安全证明；成功后旧token失效，不能用旧token取得回执。重新登录后当前证明齐全才可恢复历史布尔结果，不重复撤权/改MFA，也不覆盖后续状态。原启用后恢复回执时，当前MFA已启用，需提供当前因子。

准备表中的secret为仅内部可读的待绑定材料，启用消费/停用/下一次prepare会清除或替换；过期由服务端时间检查拒绝，不提供公开读取。本批没有后台物理清理器，不将“TTL有效性”误称为“数据库自动按TTL删除”。

## 实际验证

`node tools/api-v3-mfa-test.mjs`退出0，9组通过，日志`/tmp/rhythm-v3-mfa-final.log`；未使用Maven。编入真实共享ApiV3SecurityProofService、AccountCredentialMgmtService/StateRepository、v3/OpenID/consent/code撤权SQL、MFA核心和Processor/Dispatcher/auth/write中间件。SQLite实际SQL去FOR UPDATE断言；Token入口验证/Repository连接/JDBC提交故障为隔离适配。

测试复用安全包固定owner fixture；每个独立case清通用write静态quota，避免跨case固定同人累计导致假429；真实中间件仍执行，敏感proof尝试门用fixture开关确定性验证在root前拒绝，不把测试适配声称真实IP压测。

1. RFC TOTP向量、真实POST/PUT/DELETE路由、首次私有响应、handle哈希、启用消费、epoch与旧OpenID/v3/pending/code全撤、普通回执无密码/secret/handle/验证码。
2. 新prepare废旧、精确十分钟过期、credentialEpoch变更失效，失败不消费或保留回执。
3. 错新码/当前密码/现有因子、已启用prepare/enable409，错误新码后准备仍可用。
4. prepare/read/consume/user-secret/epoch/revoke/receipt/savepoint/release/strict commit故障整笔回滚，恢复后同key成功。
5. 停用清secret/prepare/revoke/receipt/提交失败保留原因子及凭据。
6. 当前第一方access/client/status/scope、非第一方拒绝、Cookie/apiKey/owner伪造、输入及root前attempt门。
7. 提交ack丢失不重放私有材料，旧token立即拒绝；重新认证当前证明可恢复历史布尔，不再增加epoch。
8. 账户/密码绑定，primitive根要求、保存点保留他人调用方写入、回滚保存点失败整根中止。
9. 已停用的当前锁账号在零changedRows模式仍原子撤权，不误报存储失败。

尚未执行真实MySQL建表/锁等待/并发、完整服务与两端浏览器、真实认证器APP扫码、跨实例旧连接/缓存回归或生产环境；不能以SQLite证明这些。

## 安全与反漏扫复核

三路由明确Bearer settings.write且服务端再查website_v3，未增加匿名能力/anonymous.viewSkips例外。越权按当前actor与access；SQL参数绑定固定表名；无外链二维码、远程请求、文件路径或上传下载，避免泄露secret至第三方。输入长度/字符严格，响应只本地私有准备DTO或布尔，HTTP no-store；日志只通用错误，不写证明/secret/URI。敏感写共享额度+普通write门，未声称跨节点全局额度或TOTP一次性时间步消费。

未改BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor、CDN版本号/sourceMappingURL/静态目录等；全站匿名首访/黑名单/验证码解封/开发关闭风控及静态目录扫描未在线验收，不能宣称全站反漏扫通过。旧MFA接口保持，不宣称旧管理行为已全部要求新增安全证明。

## 人工完整链路

先应用MFA与凭据epoch SQL，通过IDEA重新编译或重启服务端，再用第一方website_v3的profile.read/settings.write令牌联调：

1. GET `/me/security/mfa`返回enabled=false→POST preparations提交当前MD5→拿到一次性的secret/otpauthUri/preparationId和expiresAt；本地认证器录入secret或本地生成QR，确认无qrserver等第三方请求。新prepare后旧handle409，十分钟边界后409；普通回执与日志无secret/URI/验证码/密码。
2. PUT MFA提交currentPassword/preparationId/newTotp→enabled=true；准备行state1且secret/binding空；GET用旧token应401，重新登录要求认证器当前码后状态true。旧Cookie/apiKey/OpenID/access/refresh都应失效，其他用户不受影响，跨实例/缓存另外回归。
3. 错密码/新码422且不消费，正确码可继续；第三方同scope403、缺Bearer401、错scope403；已启用准备/再启用409（当前因子先校验）。重新登录后DELETE提交currentPassword/currentTotp→enabled=false，全部旧凭据再次失效；新登录状态false。
4. 重放成功启用/停用需重新认证当前安全证明；返回原布尔但不覆盖后续设置或重复加epoch。prepare响应丢失只能重新准备，不能找回旧secret。
5. 测试副本注入准备/用户写/撤权/回执/commit失败，检查MFA、prepare、epoch、OpenID/v3/pending/code整体回滚；提交成功ack丢失仍已撤旧凭据，重新登录验证最终状态。旧已停用零changedRows模式操作不误报。

## 公开API片段供主代理合并

### 准备绑定 MFA

`POST /api/v3/me/security/mfa/preparations`

为当前未启用MFA的账号生成绑定资料。需要第一方 Bearer Access Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| currentPassword | 当前密码的小写32位MD5摘要 | 0123456789abcdef0123456789abcdef |
| currentTotp | 已启用MFA时需要当前6位验证码字符串 | 012345 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/security/mfa/preparations' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' \
  -d '{"currentPassword":"0123456789abcdef0123456789abcdef"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 当次绑定资料 | 对象 |
| - preparationId | 本次准备标识 | PREPARATION_ID |
| - secret | 本次认证器密钥，仅当次返回 | BASE32_SECRET |
| - otpauthUri | 可供本地认证器绑定的URI，仅当次返回 | otpauth://totp/... |
| - expiresAt | 失效时间，毫秒 | 1790000600000 |

> 准备十分钟有效，新请求会废弃上一次准备。密钥及URI无法重复取回；响应丢失时请重新准备。已启用MFA需先停用后再绑定，返回409。请在本地展示或录入绑定资料。

### 启用 MFA

`PUT /api/v3/me/security/mfa`

验证准备密钥对应的新验证码并启用MFA。需要第一方 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| currentPassword | 当前密码的小写32位MD5摘要 | 0123456789abcdef0123456789abcdef |
| currentTotp | 当前已启用MFA时需要的6位字符串 | 012345 |
| preparationId | 最近一次未过期准备标识 | PREPARATION_ID |
| newTotp | 新绑定认证器的6位验证码字符串 | 012345 |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/me/security/mfa' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: mfa-enable-001' \
  -H 'Content-Type: application/json' \
  -d '{"currentPassword":"0123456789abcdef0123456789abcdef","preparationId":"PREPARATION_ID","newTotp":"012345"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本次结果 | 对象 |
| - enabled | 已启用 | true |

> 成功会使已有登录凭据失效，请重新登录。准备过期、已替换或已使用返回409，验证码错误返回422。相同键恢复原结果也需要重新认证及当前安全证明，不会覆盖后来完成的MFA设置。

### 停用 MFA

`DELETE /api/v3/me/security/mfa`

停用当前账号MFA。需要第一方 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| currentPassword | 当前密码的小写32位MD5摘要 | 0123456789abcdef0123456789abcdef |
| currentTotp | 已启用MFA时需要当前6位验证码字符串 | 012345 |

请求示例：

```bash
curl -X DELETE 'https://fishpi.cn/api/v3/me/security/mfa' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: mfa-disable-001' \
  -H 'Content-Type: application/json' \
  -d '{"currentPassword":"0123456789abcdef0123456789abcdef","currentTotp":"012345"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本次结果 | 对象 |
| - enabled | 已停用 | false |

> 成功会使已有登录凭据失效，请重新登录；尚未启用时也需要当前密码，完成后已有凭据同样失效。相同键重试返回原结果，但仍需当前认证与安全证明。
