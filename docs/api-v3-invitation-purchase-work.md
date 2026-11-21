# v3 邀请码购买（2026-10-22）

## 状态与实际文件

用户已确认：只在邀请注册模式购买；每次现有100积分；余额不足不造码；扣分、生成码和幂等回执严格同事务；同键恢复同码；旧入口保持。重放仅重验当前有效账号，不因模式后来关闭、原码已用/过期/清理而丢失原购买结果。

- `src/main/java/org/b3log/symphony/processor/ApiV3InvitationPurchaseProcessor.java`：新 POST `/api/v3/me/invitation-codes`，activity.write + Idempotency-Key；无参数或空JSON，4096字节上限，禁止覆盖价格/本人/邀请码。
- `src/main/java/org/b3log/symphony/service/ApiV3InvitationPurchaseService.java`：同一严格事务扣费/生成16位SecureRandom字母数字码/本人回执；仅public code/expiresAt/paidPoints。存储沿用invitecode表，generatorId为本人、unused、无预留。生成id使用实际Ids.genTimeMillisId，expiresAt=Math.addExact(Long.parseLong(id),Symphonys.INVITECODE_EXPIRED)，与清理按ID计龄一致。
- `src/main/java/org/b3log/symphony/util/ApiV3InvitationPurchaseInputs.java`：严格空对象契约，服务入口再次规范化。
- `src/test/java/org/b3log/symphony/service/ApiV3InvitationPurchaseTest.java`、`tools/api-v3-invitation-purchase-test.mjs`：Java25隔离专项，临时SQLite、实际生产SQL/Pointtransfer/OptionRepository/严格JDBC/Dispatcher/auth/write middleware；仅用户Repository传输/cache和Token身份适配。未执行Maven。
- 本文及 `docs/api-v3-next-rules-work.md`：内部实现/未确认规则分界。总register、API.md、UPDATE_LOG.md、AGENTS.md和清单由主代理接线维护。

## 锁、提交及保密

先拿actor/sys账户对，打开ArticlePublicationCaches边界，再严格根。幂等取回执前按与注册/全站重置一致的 namespace(memberCount)→allowRegister→当前actor→receipt 锁序；只有新业务体验证当前模式为2，同键重放只验证当前actor有效。不依赖OptionQuery缓存。模式0/1返回409，坏配置503，余额不足409。

生成码前确认余额，码在事务内按code当前读查重；碰撞503整笔回滚，不覆盖已有记录。旧表无code唯一约束，不能宣称与旧独立生成入口的跨实例并发碰撞被数据库绝对禁止；强随机且同事务查重缩小此边界，不改旧schema/业务。

Pointtransfer primitive吞异常返回null，调用方显式失败并回滚；indexAsync=false。账本dataId空、memo固定“购买邀请码”，绝不将凭据放入失败日志会打印的字段。sourceScene=invitation.purchase/sourceRequestId仅哈希。本人幂等回执私有保存code及indexTransferId；公共结果严格校验并重建三字段，绝不返完整invitecode/user或内部转账ID。

提交后单独严格事务按主账PK核验from/to/type/sourceScene/sourceRequestId，再锁主账串行修复actor/sys的(rawId,userId)索引；索引失败不把已购结果报失败，同键可幂等修复，包括索引提交ack丢失。购码提交ack丢失先503，同键恢复已提交原code，不再次扣费/造码。没有网络副作用。

部署前置：现有 `sql/20260717_external_point_source.sql` 的pointtransfer来源列、v3幂等表已迁移；`sql/20261022_api_v3_membership.sql` 的pointtransfer_redc(rawId,userId)普通索引用于修复查询。本批不增加表/Repository注册/迁移，未实际执行SQL迁移。

## 实际验证

`node tools/api-v3-invitation-purchase-test.mjs` 最终 **9组通过**；日志 `/tmp/rhythm-v3-invitation-purchase-agent.log`。node --check通过。测试不输出真实用户凭据，所有码/用户为临时fixture。

1. 实际Dispatcher路由/Auth、SQL扣100、余额/usedPoint/码/回执/索引提交；实际ID+TTL；三字段白名单及重放。
2. 模式0/1拒绝、坏配置503；99/0/负余额拒绝且生成器未调用，余额正好100成功。
3. 同键模式关闭/已用/记录清理仍恢复原码；账号停用不能重放。
4. option/user/receipt/code读取、积分/账本/码/回执写入、保存点/释放和严格commit故障全回滚；同键重试可恢复。
5. 购码提交ack丢失恢复原码；索引读写/父行不匹配不反转购买；索引自身提交ack丢失不重复索引或扣费。
6. CI比较已有码碰撞不覆盖；坏ID/到期溢出/坏生成器无扣款。
7. 缺/坏Bearer、scope、幂等键、body身份/费用/凭据注入、重复JSON、错误根类型及字节边界；空body可用。
8. 积分账本不含邀请码；私有回执污染不泄漏，白名单失败关闭。
9. 不同本人使用同key分别生成，不串回执；账户锁释放。

SQLite适配FOR UPDATE，不能证明真实MySQL行锁/间隙锁或跨实例并发；实际Token签发撤销、全服务HTTP/浏览器均未运行。Symphonys按当前配置快照适配100与3天，生产读取原常量；未声称编译完整服务。

## 反漏扫与风险复核

- 新入口必须Bearer activity.write，当前actor严格重读，Cookie/query/body不能提升身份；无匿名公开入口，不改anonymous.viewSkips/黑名单/验证码链路。实际全站匿名盾和解封未回归。
- 只写本人、服务端费用/生成码，不能指定owner；幂等和现有write middleware覆盖重试/基础滥用。真实并发/多实例未验。
- 仅Authorization身份，无Cookie副作用；未给新写接口套不兼容CSRF中间件。真实Cookie/Bearer混用服务联调未验。
- 参数化SQL、表名来自固定Repository，不接受URL、不抓远端，未新增SSRF/上传/下载。输出无HTML字段，日志不写码；新模块没有静态资源/CDN、sourceMappingURL或目录枚举路径。全站存量资源/下载链路未重扫。

## 人工完整链路

先通过IDEA重新编译/重启，确认上述已有迁移，准备有效activity.write Bearer与开启邀请注册模式。

1. POST空JSON或无body至新入口，带新Idempotency-Key；应仅返回code/expiresAt/paidPoints=100；本人余额扣100，数据库仅一条unused本人生成码及购买转账。
2. 原key再次请求，三字段相同、余额/码/账本数量不变；切换为关闭注册、将原码用掉/过期或清理，再重放仍只恢复原购买结果。新key重新校验模式/余额，不表示旧码可注册。
3. 将余额设99且邀请模式，换新key，409且无新码/扣款/回执；余额100可购至0。关闭模式新key409，损坏配置503。
4. 缺Bearer401，profile.read令牌403，停用本人原key不可重放；Cookie/旧apiKey/伪造body本人或价格均不能绕过。检查错误/成功无user、memo、预留字段或转账ID。
5. 数据库故障演练分别中断码写/回执写/commit，确认积分/码/回执一致；模拟提交成功响应丢失后同key恢复码。观察积分历史索引失败后仍购买成功，同key修复而不重复账本。
6. PC/mobile旧兑换正常路径保持旧响应与行为；本批无页面修改，不宣称旧余额不足仍造码已关闭。

## 供主代理合并的公开API片段

### 购买邀请码

`POST /api/v3/me/invitation-codes`

购买一枚邀请码，当前邀请注册模式可用，每次100积分，余额不足不购买。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer访问令牌，需activity.write | Bearer ACCESS_TOKEN |
| Idempotency-Key | 本次购买唯一键；重试使用同一键 | invitation-20261022-001 |

请求体可省略或为空JSON对象。

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/invitation-codes' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: invitation-20261022-001' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 购买结果 | object |
| - code | 本次购买的16位邀请码 | AbCdEf0123456789 |
| - expiresAt | 到期时间，Unix毫秒 | 1792900000000 |
| - paidPoints | 实付积分 | 100 |

> 同一幂等键返回原邀请码和到期时间，不重复购买。即使邀请码已使用、过期或注册模式已变化，重试恢复的仍是原购买结果，不代表当前可注册。另一次购买使用新键并重新校验当前条件。

> 邀请码属于可使用凭据，请妥善保管。余额不足或当前模式不支持购买返回409；参数不合法返回422。
