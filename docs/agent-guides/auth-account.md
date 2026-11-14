# 登录、OAuth 与账号凭据

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[登录互通](../website-login-interoperability.md)、[凭据专题](../api-v3-account-credential-work.md)、[框架陷阱](environment-framework.md)。

## 第一方网页登录互通

- 密码登录由 `WebsiteLoginCookies` 绑定旧 `sym-ce` 与 V3 网页令牌家族；Cookie 保持 host-only，Bearer 仍仅在新版内存，不恢复旧 apiKey 桥接。存量未绑定 Cookie 不自动兑换，需重新登录一次。
- 任一端退出先撤销当前家族再清 Cookie，存储失败保留凭据以便重试；未绑定或失效 Cookie 退出也须清理 HTTP Session 用户和凭据版本，不能只删 Cookie，否则旧 WebSocket 会保留身份。旧 HTTP/WebSocket 同样检查绑定家族。绑定不得写入跨浏览器共享的用户缓存。`/api/v3/auth/session` 仅做可信来源状态探测，不签发令牌；前端聚焦合并检查，无定时轮询。实现与本地验收见 `docs/website-login-interoperability.md`。
- 本地额外预览来源由 JVM 属性 `rhythm.dev.websiteOrigin` 精确配置，仅 DEVELOPMENT 且前后端均为 HTTP 回环主机时可用。Cookie 按主机隔离，独立实例可以分别使用 `localhost` 和 `127.0.0.1`。刷新仅在凭据明确失效时清 Cookie；存储异常返回 503。
- v3登录时间只在ApiV3TokenMgmtService.issue与AppLoginService.exchange签发根内记录，失败同回滚；刷新/普通认证/OAuth授权不记录。只更新userLatestLoginTime，缓存提交后失效，不复用会改在线标志和地理资料的updateOnlineStatus。

## OAuth、Token 与应用管理

- OAuth授权HTML及PC/mobile模板须用Referrer-Policy same-origin：浏览器no-referrer表单会发送Origin:null而被严格同源校验拒绝。token/consent等JSON响应仍no-referrer；不得为修页面把null Origin放行。授权HTML的form-action还须包含该次已绑定回调origin/原生scheme，否则浏览器会阻断表单303外站回调；完整回调仍由核心精确匹配，不用通配符。
- OAuth token/revoke以`ApiV3OAuthProcessor.protocolAccess`只读当前绑定凭据作为端点身份，无效仍原anonymous；不写Cookie/IDENTITY/IP白名单或viewSkips，全局Before黑名单先执行。正确绑定的已用code/轮换refresh仅可进原家族重放撤销，最终写根仍重验；不能把预检owner用作其他资源身份。
- OAuth同意页使用 `ApiV3AuthorizationRequestService`：服务端保存owner/client/完整回调/scope/state/PKCE及handle/proof哈希，确认只传request/proof/明确决定，禁止用表单覆盖user/scope。hint先owner过滤供client锁序，再锁请求重验，决定/签码严格同事务，批准和拒绝均一次性。HTTP必须仅取Sessions.currentCookieUser（全局Sessions可能已被apiKey提升），并接 `ApiV3ConsentSecurity` 的配置站点精确来源+非空Session CSRF；不能拿legacy CSRFMidware的Referer前缀替代，也不能回退apiKey/Bearer。state可省略、最多1024 UTF-8字节，回调禁止预置OAuth响应参数并编码state/码；HTTP在ApiV3OAuthProcessor，PC/mobile授权模板同步；普通业务信封不套OAuth协议。
- 第三方PKCE核心在 `ApiV3AuthorizationCodeService`：签码必须加入服务端确认的授权请求事务，不能接收HTTP指定的user/scope。client→精确注册redirect→code→当前user，消费码与Token对严格同事务；匹配绑定及PKCE的重放撤销对应家族，错误证明不撤销。新表见20261019_api_v3_oauth.sql并已入repository.json，先迁移再联调。用户已确认HTTPS、仅开发HTTP回环及登记的原生专用scheme；`ApiV3OAuthRedirects`要求原生反向域名scheme+单斜线路径，完整URI（含端口/查询）精确匹配，不做动态端口例外；旧/openid凭据独立。
- v3 Bearer认证必须直接SQL读取当前用户，所有客户端刷新均锁定当前用户状态，禁止回退UserQueryService/UserRepository.get的节点缓存。v3 Token写入统一先锁客户端再当前读Token；家族撤销必须按clientId/userId/familyId完整SQL更新，禁止分页截断或用旧活动快照回写state。`issueForClientInCurrentTransaction`由PKCE换码在同一授权码事务内调用、actor由服务端授权记录取得；局部保存点保护Token对。第一方Cookie仍只用于website_v3，不能把内部通用发行方法直接暴露成可指定用户的HTTP入口。
- 已批准应用登记核心为 `ApiV3ClientRegistrationService#createApprovedInCurrentTransaction`：服务端生成client_id，客户端行和精确回调行在调用方事务内同保存点写入，scope仅允许已实现的v3权限；不做HTTP身份/审批/幂等，调用方须完整提供这些边界，用户已确认仅管理员登记和维护；`ApiV3ClientManagementProcessor/Service`提供第一方当前管理员的列表/详情/幂等登记，最多20回调、每个2048；先按账号重置顺序锁客户端命名空间，再锁user/token/request，重放也重验。停用由`ApiV3ClientDisableService`把status=1、全部目标Token撤销与回执同严格事务，不删登记/回调；票据/WS在后续当前授权检查失效，不承诺同步跨实例断连。`ApiV3ClientUpdateService`完整替换名称/scope/回调，实际权限或回调集合变更同事务撤销全部Token、删除旧code与pending请求，保留已决定历史；名称或排序变化保留授权，状态不变；禁止自助登记。
- OAuth/OpenID 用户资源链路在 `OpenIdProcessor`：`/openid/login` 通过 `fishpi.scope` 请求 `profile.read/profile.detail.read/points.read/articles.read/membership.read`，`/openid/verify` 成功后返回 Bearer `access_token` 与可轮换 `refresh_token`；资源接口只认 `Authorization: Bearer`，续签走匿名 `POST /openid/token`，不接收 `apiKey`、不支持 `userId` 代查。

## 账号凭据与验证码

- 敏感设置仅第一方`ApiV3SecurityProofService`当前密码/已有TOTP；client命名空间→当前user/access→独立credential state，`AccountCredentialMgmtService`同严格根递增epoch并撤OpenID/v3/code/pending。Cookie/apiKey/OpenID每次当前SQL比版本，缺版本仅epoch0兼容；发行只用认证时Proof，不可重读新epoch升级旧身份。先部署20261023三份credential/MFA/contact迁移，旧重置自动登录绑定本次提交版本。
- `ApiV3MfaService`准备10分钟、secret/otpauth仅当次，启用消费清secret；`ApiV3ContactSettingsService`先proof→无SQL锁发送→当前epoch/旧联系值快照重验后保存，HMAC码及旧值绑定，修改/消费/全撤同根。WS后续授权检查拒旧凭据；User/Shop关闭按建立时归属清理，不能因凭据失效跳过移除/在线结算。账号验证码邮件须走`Mails.sendAccountBindingCode`专用无debug/正文日志通道，不能复用旧MailSender.sendMessage。完整文件与验收见account-credential-work。
- v3 注册选项在 `ApiV3AccountProcessor` / `ApiV3RegistrationPolicyService`：直接查询注册开关，邀请资格沿角色权限读取，仅为表单展示，不可作为后续写入授权。账号短信验证核心 `AccountVerificationService` 在调用方事务中先当前读锁用户，再按当前手机号/type/purpose/有效期锁并消费验证码，消费与账号变更同提交；同值重复码全部消费，禁止回退全局getVerifycode(code)。核心不含尝试限额，公开入口须先接共享限额。用户已同意旧register2新增code及新旧账号入口一起加固；reset-pwd及register2已接入，PC/移动页面已传code，不再重复询问。
- 账号设置手机/邮箱验证码链路：PC/移动模板在 `skins/classic/*/home/settings/account.ftl`，前端在 `settings.js`，发送接口在 `SettingsProcessor#sendPhoneVC/sendEmailVC`；验证码形态调整需同步前端提交字段与后端校验方式。
