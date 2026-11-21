# API v3 鱼游投稿与编辑申请（2026-10-23）

内部实现与验收记录，公开文档仅提取接口契约。用户已确认按[指定旧OpenID接入文章](https://fishpi.cn/article/1782119519493)校验授权URL参数及游戏同站回调后进入待审；这只验证地址结构，**不证明网站归属或实际完成接入**。授权URL4096、return_to/realm各2048，沿旧名称80、描述1000、网址/图标512，顶层只允许文章列出参数，均已确认。

## 实际文件

- `src/main/java/org/b3log/symphony/processor/ApiV3FishGameSubmissionProcessor.java`：POST `/api/v3/fish-games`及PUT `/api/v3/fish-games/{gameId}`，Bearer content.write、写入中间件、幂等键及16KiB请求体。
- `src/main/java/org/b3log/symphony/util/ApiV3FishGameSubmissionInputs.java`：现有四个字段的去HTML/trim和长度规则，专用旧OpenID URL解析与同源校验，没有网络请求。
- `src/main/java/org/b3log/symphony/service/ApiV3FishGameSubmissionService.java`：当前有效本人、待审投稿或已审核本人作品的待审编辑，URL冲突/持久OAuth地址/幂等回执同严格根。
- `src/main/java/org/b3log/symphony/model/FishGame.java`：新增当前/待审OAuth登录地址常量；`src/main/resources/repository.json`仅fish_game两字段。
- `sql/20261023_api_v3_fish_game_submission.sql`：两VARCHAR(4096)列，默认空兼容旧记录/旧写入；待审URL查询索引；内部namespace option种子。须在原20260908删除旧OAuth列的迁移之后应用，不复活旧Verified布尔。
- `src/main/java/org/b3log/symphony/service/FishGameMgmtService.java`：经主代理允许的必要存储适配：批准编辑连同URL合入pending OAuth；清pending同时清OAuth；管理员手动更换当前URL时清不再匹配的OAuth；旧编辑申请存空pending OAuth。不增加旧入口必填字段，不改旧页面、费用、审核状态规则或正常请求格式。
- `src/test/java/org/b3log/symphony/service/ApiV3FishGameSubmissionTest.java`、`tools/api-v3-fish-game-submission-test.mjs`：真实SQL/strict/Dispatcher/Auth、真实旧审核服务存储专项。

总register、API.md/UPDATE_LOG.md和进度清单由主代理整合，本模块不修改。

## 输入与OAuth规则

请求完整替换申请内容，必填 `name`、`description`、`url`、`iconUrl`、`oauthLoginUrl`。未知字段422；name/description/url/iconUrl沿旧Jsoup.none后trim再检查既有最大长度。game/icon必须HTTPS完整地址，拒绝userinfo与fragment。网址不抓取、图标不下载。

`oauthLoginUrl`仅精确`https://fishpi.cn/openid/login`；开发运行模式额外接受当前配置的HTTP/HTTPS回环站点精确`/openid/login`，不接受任意配置域或前缀相似站点。顶层参数白名单：

- `openid.ns`固定`http://specs.openid.net/auth/2.0`
- `openid.mode`固定`checkid_setup`
- `openid.identity`与`openid.claimed_id`固定旧identifier_select
- `openid.return_to`、`openid.realm`必填，HTTPS，无userinfo/fragment，规范origin（scheme/host/有效port）与game相同；realm还须为return_to的有效字符串前缀，既遵循旧协议又避免仅前缀误判域名
- `fishpi.scope`可选；缺省/空白按旧协议profile.read；非空项仅profile.read、profile.detail.read、points.read、articles.read、membership.read。显式points.read等不强迫追加profile.read；空格/逗号分隔

重复顶层参数（含编码同名）/未知参数/格式错误422；不允许顶层state，callback query可以包含业务state。callback/realm query中明确凭据名token/password/userPassword/access_token/refresh_token/apiKey/secret拒绝，防止把凭据保存为审核材料；不臆测一般业务state值。URL原始编码串保留作管理员审阅，不重建或篡改签名参数。旧OpenID不是v3 PKCE，不能用v3 token替代oauthLoginUrl。

## 写入、审核和兼容边界

v3所有投稿/编辑先锁专用namespace，再锁当前actor和回执，新业务才锁目标game、检查URL冲突。namespace种子缺失503，不在请求中临时造配置。当前URL与其他待审编辑URL冲突409；DB已有current URL唯一约束仍有效。新稿以PENDING、计数0保存，当前OAuth与记录/回执同commit。

编辑只允许本人APPROVED且没有pending；其他人/不存在404，非已审核或已有待审409。只写pending名称/描述/URL/图标/OAuth及editPending=1/更新时间，当前公开内容不变。旧管理员review批准才整体合入，拒绝编辑保留已公开APPROVED内容，禁用等原状态规则保留。管理员受保护GET `/api/admin/fish-games`返回完整记录，能检查当前/待审OAuth地址；PC/移动后台模板未新增展示控件，暂通过该现有管理API审阅。公开旧shapePublicGame与v3DTO仍白名单，无新增OAuth字段。

同键同规范化内容恢复原 `{id,status,editPending}`，当前actor仍须有效；原回执是提交当时状态，不覆盖管理员后续审核结果。改变内容同键409。共享旧投稿额度仅新执行业务消耗，重放不再计；存储故障的内部重试仍可能计入调用次数，不承诺全局额度精确一次。

旧普通投稿仍允许不提供OAuth；用户明确本批不改旧输入行为。旧投稿/编辑/管理员写入未整体接入新namespace，不能宣称新旧全站并发URL预留或审核串行化。旧review仍原普通提交策略，专项证明的是必要字段合并和v3严格根，不是旧全部写入已原子改造。真实MySQL下与旧管理员并发需验收其旧整行/快照覆盖局限。

## 实际验证

```bash
API_V3_JSOUP_JAR=/tmp/rhythm-jsoup-1.21.1.jar node tools/api-v3-fish-game-submission-test.mjs
```

Java25临时编译真实新Processor/Service/Inputs、旧FishGameMgmt/Validation及Repositories、真实Auth/Write/strict/idempotency；Jsoup为pom现有真实1.21.1。SQLite双连接执行SQL，适配FOR UPDATE语法且断言根内锁顺序；Token发行、传输信封与repository表名/读写适配是夹具。7组覆盖：

1. 实际POST/PUT路由、Bearer、规范化PENDING记录/OAuth/回执、namespace/actor锁、有效actor重放及body冲突。
2. 旧文五种scope及默认/空白scope、显式非profile权限、精确开发provider、callback业务state和HTTPS默认端口。
3. evil域/端口/userinfo/fragment、固定协议值、未知和重复参数、realm、凭据字段与未知scope拒绝。
4. 当前内容不提前发布，真实旧审核批准合并OAuth、拒绝清pending并保留当前内容；管理员同URL保留OAuth，换URL清空（review与updateByAdmin两条）。
5. 非作者/非已审核/已有pending、当前与待审URL冲突，不留孤立回执。
6. 读/业务/回执/吞commit故障整根回滚；编辑写入后回执/commit故障不留下pending；丢commit确认后同键恢复不重复造稿。
7. 缺/错Bearer、错scope、Cookie/apiKey冒用、停用actor、体积/长度/未知字段/幂等键与quota，失败无持久副作用。

日志`/tmp/rhythm-v3-fish-game-submission.log`；同时执行runner node语法、JSON metadata解析与文件空白检查。无Maven，未执行真实MySQL迁移/多实例并发/完整应用HTTP/PC移动浏览器或生产写请求。SQLite不能证明MySQL锁和DDL。

## 安全与反漏扫结论

仅Bearer content.write，Cookie/query/body apiKey不能替代身份；本人当前DB锁检查及作者绑定限制越权；URL按结构验证，无后端远程抓取/SSRF。SQL参数绑定；响应仅三字段；HTML清洗正文、OAuth审核URL以字符串保存，管理端展示必须文本/属性编码，不能直接插HTML。本模块未新增前端或公开静态/CDN、source map、上传下载路径、目录或anonymous.viewSkips。

匿名新增入口不放行，沿现有Auth/Write；BeforeRequestHandler黑名单、AnonymousViewCheckMidware首访验证码、CaptchaProcessor解封未改。专项验证无Bearer拒绝，整站黑名单/验证码/开发风控关闭行为、静态资源及下载路径反漏扫尚未运行，不据此宣布通过。

## 完整人工链路

1. 通过IDEA重新编译或重启服务端，再执行迁移；获取content.write Bearer和有效旧OpenID登录URL。POST创建后查询公开列表应看不到PENDING；管理API能看到OAuth地址。
2. 管理员审核通过后，公开列表/PC移动鱼游页显示游戏。作者PUT新名称/URL/OAuth，公开旧内容保持；管理API pending内容和OAuth一起出现。
3. 批准编辑：新URL/OAuth同进当前且pending清空；再发一份编辑后拒绝：旧公开内容和状态仍保持。管理员手改URL后OAuth应为空，不能把旧URL说明当新URL证明。
4. 同键重发不重复生成记录；管理员审核后重发返回原回执但不回滚审核；同键不同内容409。未登录/仅Cookie-apiKey/错scope/非作者/停用作者/未批准稿/已有编辑/目标冲突均不产生写入。
5. 校验相似域、子域、端口、userinfo、fragment、realm不匹配、重复参数、未知scope、超长、空字段；合规callback state和显式非profile scope可通过。没有任何后端访问目标网站的网络请求。
6. 真实MySQL并发同键/同URL/不同作者编辑，以及与旧审核竞争；故障注入回执与commit，确认无重复稿、没有公开半份编辑或丢失OAuth关联。PC/移动分别检查列表与详情、旧提交未新增必填字段。

## 公开接口片段

### 投稿鱼游

`POST /api/v3/fish-games`

提交鱼游并进入待审，需要content.write。授权网址按[第三方接入说明](https://fishpi.cn/article/1782119519493)检查参数和同站回调；管理员审核通过后才公开。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer访问令牌，请求头 | Bearer ACCESS_TOKEN |
| Idempotency-Key | 本次申请唯一键，重试沿用 | fish-game-submit-001 |
| name | 名称，清洗后1至80字符 | 示例游戏 |
| description | 描述，清洗后1至1000字符 | 游戏说明 |
| url | HTTPS游戏地址，最多512字符 | https://game.example/play |
| iconUrl | HTTPS图标地址，最多512字符 | https://game.example/icon.png |
| oauthLoginUrl | 旧OpenID授权地址，最多4096字符 | https://fishpi.cn/openid/login?... |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/fish-games' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: fish-game-submit-001' \
  -H 'Content-Type: application/json' \
  --data '{"name":"示例游戏","description":"游戏说明","url":"https://game.example/play","iconUrl":"https://game.example/icon.png","oauthLoginUrl":"https://fishpi.cn/openid/login?openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0&openid.mode=checkid_setup&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.return_to=https%3A%2F%2Fgame.example%2Fcallback&openid.realm=https%3A%2F%2Fgame.example&fishpi.scope=profile.read"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本次申请结果 | object |
| - id | 鱼游ID | 1760000000000 |
| - status | 本次申请时状态；0待审、1已审核 | 0 |
| - editPending | 本次是否提交编辑申请 | false |

> 授权地址只允许接入说明列出的参数。return_to和realm各最多2048字符，均使用与游戏相同的HTTPS站点和有效端口；realm为return_to的前缀。拒绝重复参数、用户凭据、片段和相似域名。scope仅支持该说明中的五种权限，省略或为空按profile.read；本接口只检查地址结构，不代表已验证站点归属或实际完成授权。
>
> 当前网址或其他待审编辑已占用该网址返回409。同键同请求恢复原申请，不重复创建；同键不同内容409。响应记录申请时状态，后续审核状态请通过鱼游查询读取。

### 申请编辑本人鱼游

`PUT /api/v3/fish-games/{gameId}`

对本人已审核鱼游提交完整编辑申请，需要content.write。请求头和五个必填内容字段与投稿相同；每次使用新的幂等键。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| gameId | 路径参数，本人已审核鱼游ID | 1760000000000 |
| Authorization | Bearer访问令牌，请求头 | Bearer ACCESS_TOKEN |
| Idempotency-Key | 本次编辑唯一键 | fish-game-edit-001 |
| name | 修改后名称，最多80字符 | 新名称 |
| description | 修改后描述，最多1000字符 | 新说明 |
| url | 修改后HTTPS游戏地址，最多512字符 | https://game.example/new |
| iconUrl | 修改后HTTPS图标地址，最多512字符 | https://game.example/icon.png |
| oauthLoginUrl | 对应游戏同站回调的授权地址，最多4096字符 | https://fishpi.cn/openid/login?... |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/fish-games/1760000000000' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: fish-game-edit-001' \
  -H 'Content-Type: application/json' \
  --data '{"name":"新名称","description":"新说明","url":"https://game.example/new","iconUrl":"https://game.example/icon.png","oauthLoginUrl":"https://fishpi.cn/openid/login?openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0&openid.mode=checkid_setup&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.return_to=https%3A%2F%2Fgame.example%2Fcallback&openid.realm=https%3A%2F%2Fgame.example&fishpi.scope=profile.read"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本次编辑申请 | object |
| - id | 鱼游ID | 1760000000000 |
| - status | 已审核作品状态 | 1 |
| - editPending | 已提交待审核编辑 | true |

> 申请期间公开内容保持原样；批准后才生效，拒绝后保留原公开内容。非本人或不存在返回404；未审核作品或已有待审核编辑返回409。
