# v3 邀请码存储状态查询（2026-10-20）

内部实现交接。公开片段在文末，不应把内部规则/测试适配说明复制到公开API文章。

## 实际文件与接线

新增6个文件：

- `src/main/java/org/b3log/symphony/processor/ApiV3InvitationProcessor.java`：`POST /api/v3/invitation-codes/status`，activity.read Bearer，然后接已有共享请求预算，body只有code，传输体最多1024字节。
- `src/main/java/org/b3log/symphony/service/ApiV3InvitationQueryService.java`：当前有效账号前后复核、一个已知码的参数化状态查询；仅SELECT status、LIMIT 2，缺失404，重复/异常503。
- `src/main/java/org/b3log/symphony/util/ApiV3Invitation.java`：沿既有注册规则，trim后16位ASCII字母数字；存储状态0/1/2仅投影used/unused/stopped，非整数/范围外拒绝503。
- `src/test/java/org/b3log/symphony/service/ApiV3InvitationTest.java`：生产SQL/路由/controller/auth/预算专项及SQLite显式适配。
- `tools/api-v3-invitation-test.mjs`：Java25隔离运行，结束清理临时目录。
- 本交接文档。

主代理总接线：`ApiV3InvitationProcessor.register();`。未修改旧控制器、核心服务、总注册、API.md、UPDATE_LOG.md、AGENTS.md、共享计划、SQL或Repository。不会创建表/记录，也不写幂等回执，不要求Idempotency-Key。

## 已核业务边界

现有旧查询返回0已使用、1未使用、2停用和名义期限。邀请码生成是16位字母数字，v3注册申请已经使用相同字符规则，新查询不另造字符集。新注册预留以内部userId/useTime记录，排他期一天；有效预留可超出原码年龄，系统生成码还有清理豁免。

经主代理确定，本批只返回**存储状态**，不推导available/reserved/expired/expiresAt，也不查询预留账号、手机号、期限、生成者、memo或原code列。unused仅表示存储状态，并不承诺当前注册可用，不泄露某个码是否被某人预留。缺失404包括已经被清理的记录，不用“码已过期”推断存在历史。GET/query接口、邀请码列表/购买/注册短信均不在本批开放。

使用POST将码放JSON请求体，body白名单只有code；不在响应、应用日志或业务回执中回显完整码。查询参数/Cookie/apiKey及假User attr不提供v3身份。新公开入口强制activity.read，没有给anonymous.viewSkips添加例外。为减少批量枚举，复用已有共享认证请求预算（内部具体阈值不进入公开文档）；本接口本身不做业务写入。无服务器URL抓取、上传、下载或HTML生成。

## 验证结果与限制

`node tools/api-v3-invitation-test.mjs` **5组PASS**，`node --check`通过。执行生产InvitecodeRepository、Processor、QueryService、工具、ApiV3AuthMidware、共享预算及ApiV3Requests/ApiV3WriteInputs/ApiV3Json。UserRepository连接、Token持久查找和响应信封为明确适配器，SQLite临时内存数据库执行实际带前缀参数化SQL，路由声明由实际源码抽取并经Latke Dispatcher执行。

1. 三状态精确白名单、输入trim、已知code查询，活跃预留/码年龄/系统生成者不改变投影、不返回隐私，也不修改任何字段。
2. code字符/长度、actor格式、停用/删除账号，未知码404、重复记录或坏状态503；查询不拼接code，不选凭据或预约数据。
3. 用户前读/状态读/用户后读故障503而非伪空，读取期间停用账号404。
4. 实际路由/鉴权/控制器：无Token401、缺activity.read403、假Cookie/apiKey/目标userId无效，JSON类型/字段/重复键、过大请求体及Content-Type拒绝；200为no-store/Vary，只给status、不回code。
5. 反复请求触发现有共享预算429并在SQL前拒绝；不存在GET/query-only endpoint，始终只读且无幂等表。

未使用Maven、完整应用编译/IoC、真实MySQL、8080/生产/外部短信、浏览器或Git提交。SQL方言/表前缀与绑定经过SQLite验证，不代表MySQL大小写collation、索引/并发/压力验证。日志只验证新逻辑不记录code或原驱动错误，未做全站代理/访问日志实际配置验收。

## 安全与反漏扫

- 未鉴权/越权：显式Bearer+activity.read，当前用户重查；只查询调用方已知code，不开放列表或可指定目标用户。
- CSRF：只读、无业务写入，不从Cookie授予身份，不修改旧兼容接口。
- XSS/敏感信息：响应只有固定枚举，无原code/用户/联系方式/生成者/备注/预留/时间字段。
- 注入：SQL表名仅内部Repository#getName，actor与code均绑定；code16位字符校验及JSON有界。
- SSRF/静态/下载：不接收URL，不创建公网静态或目录/上传下载路径，无新增CDN/sourceMappingURL/依赖版本暴露。
- 滥用：鉴权在共享预算前，重复查询受现有门禁，无批量查找；限额跨实例、真实高并发和记录猜测风险需运行验收。
- 匿名/验证码：原BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor和anonymous.viewSkips未改；本专项不证明实际黑名单/test、首访/第五次或验证码解封链已通过。

## 人工完整链路

1. 用户先通过IDEA重编译或重启服务；主代理接入总注册。A/B登录取得activity.read令牌，与PC/mobile旧邀请码查询作对照。
2. A使用有效已知码JSON POST：未用/已用/停用分别返回unused/used/stopped。响应只status、无code和任何关联人信息。使用B令牌同已知码可查同存储状态，不可查询邀请码列表。
3. 建立注册预留，再次查询仍为unused；过一天或超过旧年龄仍不靠本接口承诺注册可用，以实际注册接口结果为准。确认GET前后邀请码userId/useTime/status等全部不变，没有新幂等记录。
4. 假Cookie/apiKey、无Token401、缺scope403；query/body伪userId不起作用。非JSON415、重复JSON键400、非法字符/类型/字段/空码422、过大body413、未知码404，均不带原码或内部错误。
5. 停用/删除用户后（包括缓存Token身份）不能查询；模拟SQL故障503。数据重复或非法状态503，不能随机选一条或伪装不存在。反复查询触发429且SQL不再执行，检查请求码没有出现在应用/访问日志中。
6. 回归黑名单/test、匿名首访/第五次、validateCaptcha解封及开发关闭风控；全站静态/CDN版本/sourceMappingURL/目录/上传下载另验。无本批前端变更，不需yarn构建，PC/mobile原流程保持。

## 可合并API.md的公开片段

## API v3 邀请码

### 查询邀请码状态

`POST /api/v3/invitation-codes/status`

查询一个已知邀请码的存储状态，需要 activity.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| code | 邀请码，去除首尾空白后16位字母或数字 | AbCdEf0123456789 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/invitation-codes/status' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Content-Type: application/json' \
  --data '{"code":"AbCdEf0123456789"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 查询结果 | `{...}` |
| - status | 存储状态：unused 未使用、used 已使用、stopped 停用 | unused |

> 请求体最多1024字节。unused 不保证当前可用于注册，是否可用以注册接口结果为准。不存在返回 HTTP 404，非法参数返回 422；不会返回邀请码原文、关联用户或预留信息。

建议UPDATE_LOG：新增v3已知邀请码状态查询POST，需要activity.read，返回unused/used/stopped，不提供邀请码列表或注册可用性承诺。
