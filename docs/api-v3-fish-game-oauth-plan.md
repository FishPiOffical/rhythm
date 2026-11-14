# 鱼游投稿 OAuth 前置校验（只读盘点，2026-10-23）

用户指定依据：[摸鱼派第三方登录（OAuth / OpenID）接入说明](https://fishpi.cn/article/1782119519493)，文章版本2026-07-06，本轮通过只读网页工具读取。该文是旧OpenID2兼容接入流程，不能替换为v3 PKCE或把两个体系的Bearer混用。本文件不新增接口或改变投稿规则。

## 结论

**当前记录不足以可靠证明“当前投稿者控制该鱼游站点，且该站点已正确接入指定OAuth流程”。** 可以识别旧授权中的userId和自报realm，不能据此认定game URL归属或站点已完成回调处理。文章讲解用户授权和回跳验证，没有规定鱼游投稿归属证明或对应登记入口。[接入说明](https://fishpi.cn/article/1782119519493)

这不是把强制OAuth要求降为客户端声明。投稿继续缺少可执行的证明契约；需要用户明确证明标准/来源，再开放v3投稿与编辑。

## 现有鱼游链路

- `FishGameProcessor.register`：POST `/api/fish-games`→`submit`，POST `/api/fish-games/{id}/edit`→`requestEdit`；登录、写请求安全和投稿限额。
- `FishGameMgmtService.submit`（54行）：`FishGameValidationService.build`→目标URL查重→生成ID→普通事务保存PENDING。没有OpenID或OAuth调用。
- `FishGameMgmtService.requestEdit`（68行）：只准本人已审核作品，已有待审编辑拒绝；调用同一build，URL查重，pending字段保存。也没有OAuth调用。
- `FishGameValidationService.build`：名称80、描述1000、目标/图标URL512；去HTML/trim，网址HTTPS并有host。没有OAuth地址、验证标记、站点归属字段或远程验证。
- `ApiV3FishGameProcessor`当前只注册投票PUT/DELETE和评论POST；`ApiV3FishGameMgmtService`只提供vote/comment。不能在这里调用旧submit就宣称必需OAuth已实现。
- `sql/20260907_fish_game.sql`最初有`fishGameOauthUrl`、`fishGameOauthVerified`、`fishGamePendingOauthUrl`；**`sql/20260908_fish_game_remove_oauth.sql`显式删除三列**。当前`FishGame`模型与`repository.json`字段也已移除，不能依赖初始建表SQL假定证明仍存在。

## 指定旧OpenID链路的可信数据和边界

| 阶段 | 当前方法 / 存储 | 能证明什么 | 缺失什么 |
| --- | --- | --- | --- |
| 请求授权 | `OpenIdProcessor.showLoginForm`（180行），内存authRequestMap/AuthRequest | 服务端暂存输入realm、完整returnTo、scope | realm由请求方声明；仅`return_to.startsWith(realm)`，无注册站点或owner；不能用此前缀规则作鱼游站点匹配 |
| 用户确认 | `OpenIdProcessor.confirm`（268行），内存respNonceMap/AuthResult | 当前登录用户同意该请求scope，签名包含完整return_to | AuthResult仅userId/realm/scopes，不存完整returnTo，未绑定fishGame URL或投稿请求；确认并非站点服务器身份认证 |
| 校验回跳 | `OpenIdProcessor.verify`（354行） | 短期nonce、签名、OpenID字段与用户匹配后调用issueToken | public POST没有客户端密钥、站点私钥或站点控制挑战；不要求调用方是return_to服务器。它验证摸鱼派签名，不能证明请求来自该站 |
| 发Token | `OpenIdRefreshTokenMgmtService.issueToken/issueTokenInternal` | 真实verify成功后保存userId、realm、scope、family等refresh记录；access签名payload含userId/realm/scope/iat/exp/jti | realm最多512字符且截断；没有完整return_to、站点登记、game URL、game author绑定或单独“站点完成集成”事件 |
| 持久表 | `OpenIdRefreshToken`、`OpenIdRefreshTokenRepository`、`sql/20260622_openid_refresh_token.sql` | 可按userId、realm索引检索旧refresh授权记录 | 该用户是在该realm登录的用户，不是域名管理员身份；一条授权不证明其拥有该站，也不证明站点消费了结果 |
| access验签 | `OpenIdUtil.parseAccessToken`（97行） | 验HMAC和exp，能恢复旧userId/realm | 任意有效token不等于站点拥有权；该函数也不把签名token与当前refresh撤销状态或game提交绑定 |

具体反例：用户本人可以构造任意HTTPS realm/return_to授权链接并同意。签名回跳参数出现在其自己浏览器的跳转URL中，该用户可自行取得参数并向`/openid/verify`提交；现有verify不认证“接收结果的站点服务器”。因此会得到真实userId+realm授权记录，但目标站点可以完全没有部署OAuth代码。把game origin与这个realm比较得再精确，也只能修复字符串匹配，不能弥补证明含义。

无需生产试攻击来得出此结论；它直接来自上述参数生成和校验边界。本轮未发起登录、授权、verify、Token或鱼游写请求。

## 不可混用的 v3 记录

`ApiV3ClientRegistrationService.createApprovedInCurrentTransaction`登记管理员批准的client和精确redirect；`ApiV3AuthorizationRequestService`、`ApiV3AuthorizationCodeService`能绑定当前授权用户、client、redirect、scope、PKCE。它们属于新v3协议，不是文章里的旧OpenID接入。

当前`ApiV3Client`只含clientId/name/scope/status，没有游戏作者/站点owner属性。批准了一个回调，或有用户给该client授权，仍不能推断投稿者拥有该站。不能让客户端提供任意clientId、旧access_token或v3Bearer就绕过原强制规则。

## 最小需要确认的问题

建议只问一次，把语义和证明来源合并：

> 鱼游“通过指定OAuth校验”是否要求核验站点实际接入并由投稿者控制？若要求，采用哪种证明来源：①管理员按该文章人工验收后，登记“投稿者＋鱼游网址＋回调网址”的有效证明，再允许投稿；②已有站点验证/登记入口，请提供其名称和规则；③需要新建自动站点控制挑战，另确定协议。

①是当前没有额外站点验证协议时最小可实施的可靠方案，且完全沿指定旧OpenID流程人工验收。它不以“随便登录过该realm”替代站点接入。若用户只要求“本人完成一次该realm授权”而非站点实际接入/控制，需要明确接受该较弱含义后才可按授权记录做新规则；不能在没有确认时擅自降低AGENTS中的必需校验。

需要同时明确的URL范围可以用一个具体例子解决：证明是单个精确game URL，还是同HTTPS origin内全部路径；建议先精确game URL并绑定完整callback，编辑URL时重新验证。单纯图标或描述变更是否复用同一有效证明也应写进规则。

## 在①获确认后可实施的最小边界

1. 受当前管理员身份及权限保护的登记能力，保存独立证明：actorUserId、精确规范化game URL、完整旧OpenID return_to、审核状态/审核人/审核时间、失效状态；不保存access/refresh token、nonce或签名回跳参数。管理员按指定文章完成站点实际登录/回跳/profile验证，而不是由客户端声明已接入。
2. 用户投稿只传proofId及现有字段，v3活动写scope和Bearer actor；根内按本人、URL、有效证明当前读锁检查，证明不属他人、不跨站。写PENDING与proof关联在同严格事务；证明可复用或单次消费依业务明确，不能随便以一次用户登录记录代替。幂等重试恢复原结果，不能重复消费/创建。
3. 编辑新URL必须对应新证明；审核合并pending时保留正确证明对应关系。原管理员导入旧清单按已审核的例外仍需明确沿用，不让旧未验证提交成为绕过路径。
4. 证明表/关联字段需要SQL迁移和repository.json同步；模型/Repository不加symphony_前缀。没有迁移前不能读已被DROP的旧字段。
5. 新旧普通投稿都应通过同证明核心，否则旧入口绕过；但旧请求新增proof字段是客户端兼容变化，须说明影响和替代办法并获同意后才改。仅v3开启新校验不能宣称整体强制已完成。

上述为待确认实现方案，不是当前已存在能力；不能先注册一个仅校验URL或OAuth文档URL的空壳入口充数。

## 若选择自动证明

必须新增双方可验证的控制协议（例如服务端登记的站点密钥签名挑战或可核验的域名控制挑战）并绑定actor、URL、用途、短期随机数、一次性状态；随后仍按指定文章证明实际OpenID回调集成。只保存成功verify审计并不足够。站点持有私钥/预共享凭据的登记归属也必须有明确来源，否则把“自报realm”换成“自报密钥”仍未解决。

不使用后端抓取任意game/callback/icon URL、跟随重定向、读取用户输入验证地址来猜是否部署OAuth；这既不是文章定义的协议，也引入SSRF、DNS重绑定、内网/云元数据及响应体预算问题。若将来业务明确要求HTTP挑战，需要独立URL/DNS/出站网络隔离设计与验收，不能把普通URI格式校验当作完整SSRF防护。

## 后续验收

先告知用户IDEA重新编译/重启，再在开发环境验证。至少覆盖本人/他人证明、精确URL和相似域名、子域与路径边界、编辑换站、证明停用/过期、同键重放、并发投稿/审核、DB提交失败不留孤立证明关联、新旧提交不绕过、匿名与错误scope、黑名单/验证码链路和敏感字段不泄露。声明、随意Bearer、任意realm登录记录均不得被误判为站点控制。

本只读盘点未修改公开入口、匿名盾、anonymous.viewSkips、静态资源/CDN或上传下载链路，也未执行全站反漏扫，不提供运行通过结论。
