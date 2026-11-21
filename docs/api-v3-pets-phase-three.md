# 宠物第三批：见习工作与活动日志

范围：六类见习工作、独立工作熟练度与完成次数、活动日志/工作履历、账号共享工作产币上限。沿用第二批单活动槽和手动领取；不增加取消、随机事件、重新领养、动画、站点积分兑换或后台自动奖励。

## 协议与兼容

目录保持食品 `version:1`，活动 `activityVersion` 升到 2；新增 `jobs` 与 `incomeRules:{dailyLimit:60}`。job 为原活动条目再加 `requirement:{skillId,experience:10}`。所有活动奖励统一为：

```json
{
  "experience": 15, "food": 0, "mood": 3,
  "skills": [], "coins": 12, "proficiency": 10
}
```

`skills` 是技能增量，`proficiency` 是当次工作自己的熟练度增量，二者不同。外出/课程不产生金币或工作熟练度，两个字段为 0。后端按旧快照版本明确补旧版不存在的零字段，客户端不能猜测未知缺字段为零。历史快照版本 1 仍严格校验原四项奖励，版本 2 校验完整六项；工作只能使用版本 2。

- `POST /api/v3/me/pet/activities` 原协议增加 `kind:"work"`，其他字段不变。服务端同时重验当前技能、产币额度、成本、真实目录版本和活动槽；不信任客户端的解锁状态或奖励值。
- `POST /api/v3/me/pet/activities/claim` 保持 `{petId,activityId}` 和原回执。成功后客户端读取当前状态，后端没有提交后的额外查询。
- `GET /api/v3/me/pet` 新增 `income:{date,earned,limit,remaining}`；有宠物时新增 `pet.jobs:[{id,experience,level,completed}]` 六项。尚未做过的工作为真实 0 经验/0 次/1 级，没有宠物也有今日 0/60 预算投影，GET 不创建预算或进度行。
- `GET /api/v3/me/pet/activities?kind=all&p=1&size=10` 读取本人活动日志。kind 为 `all/outing/course/work`，p 为 1～10000，size 为 1～50，缺省 10。只用本人第一方 Bearer 的 `profile.read`，不接受用户 ID 指定他人。

日志结构：

```json
{
  "serverNow": 1794499200000,
  "list": [{
    "id": "1794498000001", "petId": "1794497000001",
    "kind": "work", "entryId": "delivery-assistant", "name": "配送见习",
    "startedAt": 1794498000000, "endsAt": 1794499200000,
    "status": "claimed", "claimedAt": 1794499200000,
    "cost": {"coins":0,"energy":12,"hunger":4,"water":6},
    "rewards": {"experience":15,"food":0,"mood":3,"skills":[],"coins":12,"proficiency":10},
    "actualRewards": {"experience":15,"food":0,"mood":0.3333333333333286,"skills":[],"coins":5,"proficiency":10}
  }],
  "pagination": {"page":1,"pageSize":10,"total":1,"pageCount":1}
}
```

状态为 `running/claimable/claimed`，未领取 `claimedAt:null`、`actualRewards:null`。时间与费用/计划奖励取当次冻结快照，不取现行目录；实际奖励是领取事务记录。实际 mood 为底层真实状态恢复差值，可能为小数，客户端可格式化显示；其他结果为整数。没有实际结果的第一版已领旧活动返回 null，不能把计划当成实际到账。新版本已领取活动缺结果、结果损坏或结果归属不符均返回 503，不伪装为旧记录。

按 `startedAt DESC,oId DESC` 稳定排序，COUNT 和列表使用相同账号/分类条件。空列表 pageCount 为 1，超出末页仍返回真实总数与空列表。不逐条查成果，活动与结果一条 LEFT JOIN 批量读取。工作履历复用 kind=work，不增加重复的历史接口。首版日志只包含外出、课程、工作；旧喂养流水没有完整效果，不虚构回填。

## 六类见习工作

六项统一 20 分钟；成本 0 宠物币、12 精力、4 饱食、6 水分；计划成果 12 宠物币、15 宠物经验、3 心情、10 对应工作熟练度，不增加技能。完成对应入门课获得 10 技能经验即可解锁。

| ID | 工作 | 需求技能 |
| --- | --- | --- |
| delivery-assistant | 配送见习 | vitality 体能 ≥10 |
| trail-assistant | 巡游向导 | exploration 探索 ≥10 |
| library-assistant | 图书助理 | knowledge 知识 ≥10 |
| broadcast-assistant | 播报学徒 | expression 表达 ≥10 |
| craft-assistant | 手作学徒 | crafting 手作 ≥10 |
| care-assistant | 照护见习 | kindness 亲和 ≥10 |

工作熟练度属于当前宠物、按 jobId 独立；等级 `min(30,1+floor(experience/100))`，每次确认完成计数加 1。工作不绕过学校增长技能，不计入每日照顾十次成长额度。

## 共享产币规则与事务

- 所有工作共享账号级 60 宠物币/上海自然日收入上限，每日补给不计入。
- 按领取日实际入账 `min(计划薪酬,当日剩余额度)`。例：当日已收入 55，计划 12，实际发 5。
- 上限已满不能开始新工作。已经开始的工作仍可领取，额度为零时发 0 币，其他成果正常，释放活动槽；不能把宠物卡在待领取状态。
- 成功原键重放先于额度、时间、版本和槽检查。不同键重复领取或幂等回执被清理后，持久 claimedAt 仍拒绝再次发奖。
- 最高结算日期与 lastSettlementAt 只前进。时钟回拨，读取保留原最高日预算，不制造新一天额度；新开工和结算发现时间回拨时返回 `clock_changed`。即使将来换宠物，账号收入账也保留。
- 仍沿真实用户行锁串行化同账号命令，领取钱包、收入预算、工作熟练度/次数、宠物经验/心情、食品、结果、claimedAt/释放槽、流水和幂等回执同一事务。任一数据库故障全部回滚；已提交回包丢失用原键恢复。
- 熟练度、履历次数和余额使用整数边界检查。溢出不截断、不部分发奖。

## 数据迁移与运行顺序

先完成一、二批迁移，再执行 `sql/20261112_api_v3_pet_work.sql`。新增表均 `CREATE TABLE IF NOT EXISTS`，同步 `repository.json`：

- `pet_job_progress`：`(petId,jobId)` 唯一，经验、完成次数、更新时间。
- `pet_income_account`：`oId=userId`，最高结算日、当日已入账、最近结算时刻；不属于宠物，未来重新领养不能删。
- `pet_activity_result`：`oId=activityId`，账号、宠物、实际成果 JSON；与活动领取同事务写入。

现有活动表增加 `(userId,startedAt,oId)` 和 `(userId,kind,startedAt,oId)` 两个日志索引，information_schema 检查后幂等添加；不修改或回填旧记录。第一次在大表添加索引需按实际规模安排维护时间。

先执行迁移、编译/重启 Rhythm，再运行新前端。未发布的新前端不能对旧 activityVersion:1 服务器硬发工作请求。当前第一、二批页面在目录变化后应明确重读目录，旧成功原键恢复仍有效。原站积分、职业系统、聊天和旧版客户端不受影响。

## 测试与人工验收

`node tools/experiments/api-v3-pets-test.mjs` 编译真实入口及当前源码，运行第一批 6 组、第二批 8 组、第三批 6 组隔离持久化专项。覆盖六工作、实际课程解锁、55 发 5/满额发 0、补给独立、跨日领取/回拨/换宠、真实分页与分类、旧快照、实际心情差值、新结果缺失/归属/损坏、各写入点及 commit/回执故障、回包丢失、同/异键并发、经验/次数溢出、鉴权和分页约束。

SQLite IMMEDIATE 双连接用于事务与唯一约束回归，不等于 MySQL 行锁运行态验收。主协调 agent 负责在已授权的本地临时账号联调和前端 PC/窄屏验证，不修改真实生产账号。测试参数不缩短正式目录计时。

人工链路：领养 → 完成一门入门课并领取 → 对应工作解锁 → 开始工作，照顾/其他活动不能覆盖 → 到时手动领取 → 钱包/工作熟练度/履历更新 → 查看活动日志的计划与实际成果 → 分类/分页切换 → 刷新或换账号不串资料。检查额度不足、时钟/弱网恢复与重复点击边界。开发者亲测前仍为待人工验收，不代表可部署或合入。

### 2026-11-12 实际执行记录

- `node tools/experiments/api-v3-pets-test.mjs`：三批共 20 组专项全部通过（6+8+6），真实生产入口/仓库/服务 javac 编译通过；未运行 Maven。
- `python3 tools/check-captcha-shield.py`：24 个受保护源码入口通过；宠物没有绕过防火墙、验证码盾或认证。
- `node tools/api-v3-route-access-audit-test.mjs`：11 组通过；本批新增日志读取后，10 个宠物路由均为 required-bearer 且有接口文档。
- `node tools/js-version-audit.mjs`：扫描 172 个文件，无版本号命中。本批没有经典前端或 CDN/source map 改动。
- 三表 SQL 与 repository.json 分别 7/4/4 字段一致，`git diff --check` 通过。
- 全仓契约审计 313 路由、305 文档；仍有此前存在的 8 个非宠物接口缺文档。全仓访问审计仍有此前存在的公开勋章图片 optional-bearer 缺匿名门禁告警。本批无新增缺口，未为清除此类既有问题扩大修改范围。
- 独立审查确认收益事务、账号上限、回拨保护、旧快照与实际结果处理无阻塞；建议的两个日志分页索引已经加入幂等迁移。
- 已执行授权本地 MySQL 三表和两索引迁移，重新编译并重启 Rhythm；临时账号真实读取目录六工作、六进度、收入预算、旧日志均 200，未解锁工作返回 409 job_locked。
- 临时账号的 10 分钟阅读课程自然到期后领取成功，原键重放回执一致；知识经验达到 10，图书助理解锁并真实开工。工作提前领取返回 409 activity_running，履历同时展示课程实际成果及进行中的工作，旧已领取外出实际成果仍为 null。未缩短正式目录计时，没有修改 admin 宠物或生产数据。
- 两账号日志隔离、游客日志 401、非法分类/分页 422 均通过；无宠物的另一账号尝试领取前一个账号的活动返回 409，不能越权领取。
- 前端 22 组 API/store 与 16 组 Vue 组件专项通过，类型检查和客户端/SSR 构建通过。浏览器隔离夹具覆盖桌面浅色/暗色、390/320px 窄屏、额度不足实发 5 币、履历分类分页；不是生产账号操作。
- 20 分钟工作完整结算使用隔离时钟事务测试验证，本轮未等待真实工作自然到期。

自动测试和本地浏览器验证不替代开发者本人 PC/移动端及完整流程验收。本轮仅本地提交，不推送、不部署。
