# 宠物第二批：外出与学校

本批接在第一批基础养成之上：三处地点、六门入门课程、六项技能，静态形象。没有兼职、随机事件、取消活动、付费重新领养或新动画。活动只消耗独立宠物币，不生产宠物币，不涉及站点积分。

## 接口与状态

沿第一批本人第一方 Bearer 校验，目录/状态 `profile.read`，写入 `settings.write` + `Idempotency-Key`，输入最多 2048 字节且拒绝额外字段。

- `GET /api/v3/pets/catalog`：保留食品目录 `version:1`，新增独立的 `activityVersion:1`、`skills`、`places`、`courses`。
- `GET /api/v3/me/pet`：有宠物时增加 `pet.skills` 六项真实进度，尚未学习的项为 `{id,experience:0,level:1}`。不提前创建数据库技能行，不修改首奖或钱包。
- `POST /api/v3/me/pet/activities`：`{petId,kind,entryId,activityVersion}`，kind 只接受 `outing/course`。服务端读取真实目录、核价和状态、扣费并生成冻结快照。
- `POST /api/v3/me/pet/activities/claim`：`{petId,activityId}`，服务端计时到期后手动领取，奖励只取开始时保存的快照。

活动条目结构：

```json
{
  "id": "riverside", "name": "河岸探索", "description": "沿着河岸找找新发现，也带一点口粮回来",
  "durationMs": 900000,
  "cost": {"coins": 3, "energy": 10, "hunger": 4, "water": 6},
  "rewards": {"experience": 20, "food": 1, "mood": 10, "skills": [{"id": "exploration", "experience": 6}]}
}
```

开始后 `pet.activity` 为 `{id,kind,entryId,name,startedAt,endsAt,status,cost,rewards}`；status 用本次聚合响应同一个 `serverNow` 判定：`serverNow >= endsAt` 为 `claimable`，否则 `running`。休息保留原来的 `{kind:"rest",startedAt,endsAt}`；没有活动为 null。读时不结算奖励、不释放外出/课程活动槽。

新写入回执只含 `{operation,petId,activityId,committedAt}`，operation 是 `activity-start/activity-claim`。事务提交后没有补查 GET；客户端确认命令后自行刷新状态，读取失败不能重新付费。原键重放先于活动占用、报价和时间检查，未知结果始终使用原命令原键恢复。

## 试运行数值

成本下表依次是「宠物币 / 精力 / 饱食 / 水分」，成果依次为「宠物经验 / 口粮 / 心情 / 技能经验」。均为服务端配置，不接受客户端报价或成果。

| 地点 | 时间 | 成本 | 成果 |
| --- | --- | --- | --- |
| 广场散步 `plaza` | 5 分钟 | 0 / 5 / 2 / 3 | 10 / 0 / 8 / 探索 +3 |
| 河岸探索 `riverside` | 15 分钟 | 3 / 10 / 4 / 6 | 20 / 1 / 10 / 探索 +6 |
| 林间远足 `forest` | 30 分钟 | 5 / 15 / 6 / 10 | 30 / 1 / 12 / 体能 +8 |

六门课程都为 10 分钟，成本 `5 / 8 / 3 / 4`，成果 `15 / 0 / 3 / 对应技能 +10`：

| 课程 ID | 名称 | 技能 ID / 名称 |
| --- | --- | --- |
| fitness-basics | 活力运动课 | vitality / 体能 |
| nature-basics | 自然观察课 | exploration / 探索 |
| reading-basics | 阅读启蒙课 | knowledge / 知识 |
| expression-basics | 表达练习课 | expression / 表达 |
| craft-basics | 创意手作课 | crafting / 手作 |
| manners-basics | 友善礼仪课 | kindness / 亲和 |

技能等级 `min(30,1+floor(experience/100))`。经验为非负有界整数，溢出时整笔领取失败并回滚；不静默变成负数或丢奖励。活动成果不消耗照顾的每日前十次成长额度，活动持续时间、费用和单槽约束控制频率。心情不超过 100，第一批自然衰减继续计算，等待领取不会停止衰减。

## 单槽、并发与时钟

- 休息、外出、课程互斥。外出或课程结束后仍占槽，领取后才空闲。
- 活动中/待领取都不能喂食、饮水、抚摸、玩耍、休息或醒来；醒来不能清掉外出/课程。改名、买口粮和领取每日补给仍可用。
- 原有休息自然到期仍自动变为空闲，无需领取，可接着出发。
- 开始检查真实浮点状态满足成本。界面饱食/水分沿第一批向上取整，恰好处于阈值附近时服务端可能拒绝，客户端保留实际错误，不能偷偷补足状态。
- 服务器时钟回拨，已开始的活动不提前到期；新开始/领取发现当前时间早于持久化结算时间时返回 `clock_changed`。跨日不重复首奖/补给，也不改变已开始活动的快照。
- 锁序继续沿第一方客户端命名空间 → 用户/token → 幂等回执 → 账户/宠物 → 活动 → 技能/库存。真实用户行锁串行化同账号不同命令，独立的活动槽唯一索引再次防止两次开始。
- 领取的宠物经验、心情、口粮、技能、已领取标志、活动槽释放、流水与回执同一严格事务。数据库异常不能只发一半奖励。
- 已领取历史永久保留。即使原幂等回执被清理，另一键或旧键也会得到 `activity_claimed`，不会二次发奖。旧 activityId 不影响正在进行的新活动。
- 读取和领取快照都校验字段、类型、范围、时长、技能白名单和归属；坏快照失败，不以空活动/零奖励掩盖。

## 迁移与兼容

先执行第一批 `sql/20261112_api_v3_pets.sql`，再按实际表前缀执行 `sql/20261112_api_v3_pet_activities.sql`。两表均 `CREATE TABLE IF NOT EXISTS`，无需 ALTER 或重建现有宠物表；`repository.json` 同步声明字段。

- `pet_activity`：每次开始一行，保存宠物/账号、服务器开始与结束时间、冻结目录版本及完整成本成果、领取时间。未领取 `slotKey=active:用户ID`，领取后为 `claimed:活动ID`，slotKey 唯一。按用户及领取状态索引查询当前槽。
- `pet_skill`：`(petId,skillId)` 唯一，技能进度属于宠物。将来重新领养必须用新宠物 ID，保留账号钱包/补给/首次奖励账本，不能通过换宠重领首奖。当前仍没有重新领养入口。

新前端严格要求六技能和活动目录，需先完成后端及迁移再更新前端；旧版客户端不使用这些独立接口，旧站登录、积分、聊天与商城流程不改。未来工作可扩展目录和活动类型，但必须同时更新白名单、冻结快照、领取规则与经济预算；不能先返回假入口。

## 专项与联调记录

执行脚本：`node tools/experiments/api-v3-pets-test.mjs`。同时编译真实生产入口、服务、目录和仓库，随后仅用 HTTP 输出 shim 做状态码断言；运行第一批和第二批两组隔离 SQLite 持久化回归，不启动真实服务、不连接真实账号。

第二批覆盖全部九个目录条目的扣费与成果、六技能初始/升级、到期前 1 毫秒/恰好到期/离线多日、无写读取、目录不可用后仍按冻结快照领取、跨日旧键恢复、时钟回拨、活动槽与旧休息兼容、换账号/宠物/活动隔离、撤销凭据、无效参数和坏快照、各事务写入点/提交失败/回包丢失、技能与宠物经验溢出、双连接同键/异键并发开始与领取、两新增 HTTP 路由鉴权。

SQLite 测试为独立连接的 IMMEDIATE 事务，能验证事务回滚/唯一约束及幂等行为，不能替代 MySQL InnoDB 的真实行锁和运行态人工验收。实际执行结果及本地 MySQL 联调结果在完成后补录。

### 2026-11-12 实际执行结果

- `node tools/experiments/api-v3-pets-test.mjs`：第一批 6 组、第二批 8 组专项全部通过，包含生产入口 javac 编译、真实 SQL 持久化、HTTP 中间件链和故障注入；使用临时输出目录，不运行 Maven、不格式化代码。
- `python3 tools/check-captcha-shield.py`：24 个受保护源码入口通过，未为宠物开放盾例外。
- `node tools/api-v3-route-access-audit-test.mjs`：11 组反漏测试通过。
- `node tools/js-version-audit.mjs`：扫描 172 个文件，无 JS 版本号残留。
- SQL 与 repository.json 对照：pet_activity 10 字段、pet_skill 6 字段完全一致；`git diff --check` 通过。
- 全仓契约审计登记 312 个路由、304 个文档条目；本批共 9 个宠物路由全部有文档且为 required-bearer。全仓审计仍报告既有 8 个未登记文档条目（外观商城/本人外观/小尾巴/公开职业摘要/auth session 等），以及既有公开勋章图片路由的 optional-bearer 缺匿名门禁告警；本批没有新增上述缺口，不扩大修改这些链路。
- 主协调 agent 已在用户授权的本地 MySQL 执行两张新增表迁移并重启本地 Rhythm。临时测试账号真实目录/领养/5 分钟广场散步开始、原键重放、聚合状态、wake/rest/course 覆盖拒绝、提前领取拒绝、匿名写入 401 均已通过；未修改 admin 宠物或跳过服务器真实计时。真实等待 5 分钟后 GET 返回 claimable 且经验不变；两个不同键并发领取分别返回 200 和 activity_claimed 409，只发一份 10 经验与 3 探索经验，活动槽释放、金币不变；成功原键重放回执一致，再读经验/技能未增加。另一临时账号没有宠物，跨账号领取在 pet_required 409 前置拒绝。

仍需用户验收实际外出与课程的节奏和 UI，不代表已经批准上线。本批未推送或部署。
