# 宠物账号保护与原操作恢复

本轮只给宠物操作增加独立账号保护，不改变养成数值、奖励上限、站点积分、旧站或第三方客户端登录。现有全站防火墙和验证码盾仍在入口执行，宠物服务不封禁 IP，也不为宠物新增白名单或绕过入口验证。

## 持久化与启用

上线前按实际表前缀执行 `sql/20261113_api_v3_pet_risk.sql`，创建 `pet_risk_state`、`pet_risk_audit`、`pet_risk_proof`。开发代码未向正在运行的本地数据库执行迁移，也没有重启服务或修改 8 小时测试使用的目录。

服务沿用现有极验四代组件 ID、服务器配置和 HMAC 签名协议。组件配置不可用、供应商非 JSON、异常 HTTP 状态、超时或断流均返回 503，不放行，也不计作用户验证失败。极验验证目标固定为官方 HTTPS 地址，禁止重定向，完整响应限时 5 秒、正文限 16 KiB。敏感配置、Cookie、Bearer、验证码证明及供应商正文不进入新日志。

## 当前阈值

| 范围 | 限制 | 处理 |
| --- | --- | --- |
| 同账号不同原命令 | 10 秒最多 12 次、1 分钟最多 30 次 | 429，冷却 60 秒；不封 IP |
| 同账号明确业务拒绝 | 5 分钟 8 个不同原命令 | 后续命令要求验证码 |
| 宠物写传输 | 同账号 1 分钟 240 次，含同键回放 | 429；仅防请求洪泛，不增加作弊评分 |
| 风险读取和挑战准备 | 同账号 1 分钟共 60 次 | 429 |
| 验证提交 | 同账号 1 分钟 10 次、单挑战 5 次 | 429；耗尽的挑战可以重新准备 |

账号额度在数据库行锁内累计，重启和多实例不会各自重新获得额度。账号之间独立；多个标签页属于同一个账号。命令身份为用户 ID、操作名、幂等键及规范化内容摘要。重复提交同一原键及内容不重复计入行为风险；同键不同内容拒绝并只记有限的冲突摘要。

明确业务拒绝包括互动冷却、饱食/水分已满、精力或资产/物品不足、工作日收入达限、活动未结束/已领取、补给已领取、休息/活动中不允许操作、已领取事件奖励和已拥有收藏。目录改变、时钟校准、网络故障、数据库错误及供应商不可用不计入。普通低频、准点领取行为没有专门评分或封禁，本版也没有宣称能判断所有脚本。

## 幂等与事务

宠物写路由移除通用 V3 写入额度中间件，改用上述宠物账号额度；原 Bearer、第一方、权限、禁用账号及外层安全链完整保留。

1. 保护短事务重新核验第一方账号，取得与业务相同的用户行锁。
2. 高传输上限之后先核对已经提交的原键回执。即使账号正处于冷却、需验证或验证码已过期，正常次数内仍能恢复成功回执，不再消费。
3. 新命令记录与风险拒绝独立提交，避免业务回滚丢失保护事实。
4. 新业务事务在同一真实用户行锁下再次验证原命令及摘要。验证码仅授权被冻结的命令，其他新命令不能借用。
5. 验证成功只产生 15 分钟的原命令授权，不消费、不发奖。只有原命令的真实业务提交成功，才解除账号验证状态；业务拒绝或事务失败保留授权及风险状态。
6. 操作结果未知时继续保留原命令和原键。不能因后来拿到 403、429 或验证过期，就认定此前没有提交。

## 接口契约

业务写入 body 与 `Idempotency-Key` 保持原协议，不混入验证码证明。

- HTTP 403 `pet_verification_required`：需要验证后继续原操作。
- HTTP 429 `pet_rate_limited`：响应 `Retry-After: 60`，宠物响应单独合并暴露此头，不修改全站 CORS 保护代码。
- `GET /api/v3/me/pet/risk`：`{serverNow,retryAt,verificationRequired}`。
- `POST /api/v3/me/pet/risk/challenge`：`{operation,idempotencyKey}`。只接受服务端已实际登记、固定摘要的原操作。冷却仍有效时返回 429。
- 挑战回执：`{serverNow,challengeId,operation,idempotencyKey,expiresAt,provider:"geetest4",captchaId,verified}`。`verified:true` 表示该原命令已有有效授权，可手动继续，解决验证码提交成功后回包丢失；不需要保存或重发验证码证明。
- `POST /api/v3/me/pet/risk/verify`：`{challengeId,captcha}`，`captcha` 为既有组件四字段 JSON 字符串。
- 验证回执：`{serverNow,verified:true,operation,idempotencyKey,expiresAt}`。
- 验证失效为 409 `pet_challenge_expired`，无实际受限命令为 409 `pet_challenge_unavailable`，错误证明为 422 `invalid_captcha`，供应商不可用为 503。

操作名：`adoption`、`readoption`、`story-claim`、`name`、`action`、`supplies`、`purchase`、`craft`、`activity-start`、`activity-claim`。

挑战有效期 5 分钟，服务器只接受 10 分钟以内且没有明显未来时钟偏差的供应商证明。极验批次摘要全局唯一，不能跨挑战或账号重用。单个验证在途不同时调用供应商，供应商故障不增加失败次数。所有时间均为服务器毫秒时间戳。

聚合宠物资料额外返回 `pet.careAvailableAt.feed/water`，依据实际小数状态和原有阈值、衰减速率计算；可操作时为本次服务端时间。它只帮助界面安排一次状态刷新，不替代业务权限判断。

## 异常记录与保留

`pet_risk_audit` 只保存账号 ID、动作、命令摘要、内容摘要、原因和时间，不保存原键、昵称、原文、设备指纹或证明。原键仅在当前账号的私有保护状态中用于恢复操作，不能从公开接口读取。

已记录原因：`rate_limited`、`verification_required`、`challenge_issued`、`verification_passed`、`verification_failed`、`verification_unavailable`、`idempotency_conflict`、`business_具体拒绝码`。纯准点行为不记为异常。审计每账号最多 200 条；命令状态最多 128 项且下次访问淘汰超过 24 小时记录；冲突去重最多 32 项。证明摘要保存 24 小时，当前账号发生审计写入时清理过期证明。

运维可只读查询近期事件（请替换表前缀和绑定实际用户 ID）：

```sql
SELECT userId, operation, reason, occurredAt, commandHash
FROM symphony_pet_risk_audit
WHERE userId = ? ORDER BY occurredAt DESC LIMIT 100;
```

停止访问的账号不会触发惰性清理，运维应在维护窗口以小批次定期清理全局过期证明；可按隐私保留策略另清理超过 30 天的审计记录。下面为维护示例，不由应用请求或本轮脚本自动执行：

```sql
DELETE FROM symphony_pet_risk_proof
WHERE expiresAt < UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 LIMIT 1000;
DELETE FROM symphony_pet_risk_audit
WHERE occurredAt < (UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) - 30 * 86400) * 1000 LIMIT 1000;
```

## 验证与限制

隔离专项使用真实事务、SQLite 双连接和真实认证/路由代码，验证码网络部分使用供应商夹具。业务专项的密集加速时钟默认提高行为额度，风险专项显式启用正式阈值，不靠改生产常量跑通。测试结果不代表真实极验、MySQL 行锁或 Linux ipset 的人工验收。

验证覆盖：同键恢复与改内容冲突、先冷却后挑战、验证回执丢失、跨账号证明重放、验证码失败/过期/供应商 503、业务失败授权回滚、并发额度、日志上限、真实路由认证和可用时间边界。完整业务专项继续检查养成、活动、兼职、探索、进阶课程、重新领养与经济参数。

2026-11-13 已完成隔离 Java 25 编译、全部 8 个宠物专项和种子 `20261113` 的 80 步操作序列。风险短事务与业务事务分别注入提交失败、已提交丢回包，确认恢复后只消费一次。修正旧活动夹具复用不同内容的操作键；没有放宽生产校验。

入口复核：三个保护端点均拒绝匿名、旧 Cookie/apiKey、缺少权限和第三方令牌；跨账号不能使用挑战或证明。仅接受 Bearer，不增加 Cookie 写入口；JSON 有大小与字段限制，输入不进入 HTML 或动态 SQL。验证码目标为固定 HTTPS 地址且禁重定向，不接受客户端 URL。新增日志不保存认证材料或验证码证明，无新增公网静态目录、上传或下载入口。`python3 tools/check-captcha-shield.py` 的 24 个受保护源码检查通过；`node tools/js-version-audit.mjs` 扫描 170 个 JS 文件，版本号命中 0。运行态 Linux 黑名单、真实跨域部署与极验供应商没有在本轮隔离环境中验证。

待人工验收：完成迁移并重启隔离服务后，用 PC/窄屏正常互动、双标签页操作、断网恢复原命令，触发异常后使用真实极验解锁，再核对只扣一次币。8 小时测试使用的服务和数据库必须等运行结束后再迁移。未提供人工结果前状态为待人工验收，不表示可以发布。
