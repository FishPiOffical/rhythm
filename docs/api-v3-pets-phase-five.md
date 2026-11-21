# 宠物第五批：进阶课程、事件链与重新领养

范围是现有宠物家园的继续开发，保持第一方本人 Bearer、独立宠物币、服务器真实计时、原键幂等及单活动位置。动画、公开他人宠物、随机事件和正式部署不在本批。

## 目录与课程

目录 `version=3`、`activityVersion=4`。六门入门课、六处地点和六类见习工作保留原规则，新增加六门进阶课，继续使用原 `POST /api/v3/me/pet/activities` 开始和 `/activities/claim` 领取；kind 仍为 course，不增加另一套计时器。

| 课程 ID | 名称 | 对应技能 |
|---|---|---|
| fitness-advanced | 体能训练课 | vitality 体能 |
| nature-advanced | 自然研究课 | exploration 探索 |
| reading-advanced | 阅读研习课 | knowledge 知识 |
| expression-advanced | 表达进阶课 | expression 表达 |
| craft-advanced | 创作进阶课 | crafting 手作 |
| manners-advanced | 伙伴协作课 | kindness 亲和 |

统一要求对应技能经验 ≥20；30 分钟，消耗 10 宠物币、14 精力、5 饱食、7 水分；领取增加 30 宠物经验、20 对应技能经验、5 心情，不给宠物币、材料、口粮或工作熟练度。成本、要求、成果在出发时冻结。旧 v1/v2/v3 活动仍按原快照读取及结算，不能被新课价格或版本改变。

## 三条事件链

所有事件均按账号一次性完成，不随重新领养重置。只有本版上线后服务器成功的照顾动作或活动成果领取能推进当前步骤；不会通过读取页面、重复同键、旧历史回填、活动刚开始或客户端上报进度推进。

| 事件链 ID | 名称 | 第一步 → 第二步 → 第三步 | 整链奖励 |
|---|---|---|---|
| care-companion | 陪伴的日常 | 喂食 → 饮水 → 玩耍 | 口粮 ×2 |
| pond-keepsake | 荷塘见闻 | 领取自然观察课 → 领取荷塘采集 → 领取自然研究课 | 鲜果 ×2、芦苇 ×2 |
| first-career | 第一次小小工作 | 领取任意课程 → 领取任意见习工作 → 领取任意进阶课 | 清泉饮 ×2、海贝 ×2 |

- 步骤依次自动推进，每个真实业务事件对同一条链最多推进一步；先做未来步骤不累积为待补进度。不同链可以同时响应同一事件。
- 第一次真实推进时冻结该链完整步骤及奖励，之后按存储快照推进和发奖；目录变化不修改已经开始或完成的事件。存储快照损坏时明确失败，不凭当前目录覆盖历史。
- 三步全部完成后为 claimable，需要显式领取一次，领取时宠物必须空闲、不是休息、也没有待领取的活动。
- 奖励只有固定物品，无宠物币、成长或额外收藏，不影响工作共享产币上限；账号已领取标记及同账号唯一约束防止换宠、换键或并发重领。
- 业务动作、事件进度、费用与原幂等回执在同一事务；进度写失败会连同原动作回滚。领奖的全部物品、已领标记、日志、账本与回执也同事务。

### JSON 协议

目录新增：

```json
{
  "stories": [
    {
      "id": "care-companion",
      "name": "陪伴的日常",
      "description": "从一顿饭、一杯水和一次玩耍，认识彼此。",
      "steps": [
        { "id": "feed", "description": "给宠物喂一次食物" },
        { "id": "water", "description": "给宠物饮水一次" },
        { "id": "play", "description": "陪宠物玩耍一次" }
      ],
      "rewards": { "items": [{ "itemId": "food", "quantity": 2 }] }
    }
  ],
  "readoptionRules": { "coins": 512 }
}
```

steps 的 id 顺序：care-companion 为 feed/water/play，pond-keepsake 为 nature-class/lotus-trip/nature-research，first-career 为 first-class/first-work/advanced-class。公开目录不返回内部触发器，不接受客户端自报完成。

`GET /api/v3/me/pet` 顶层新增三项 `stories:[{id,completedSteps,status,completedAt,claimedAt}]`。status 为 active/claimable/claimed；未完成的 completedAt、未领取的 claimedAt 为 null，completedSteps 为 0～3。没有记录时投影 active/0，不创建进度或发奖。

`POST /api/v3/me/pet/stories/claim` 请求 `{petId,storyId,catalogVersion}`，settings.write 与 `Idempotency-Key`；成功回执为 `{operation:"story-claim",petId,storyId,committedAt}`。旧目录的新请求返回 catalog_changed；已成功原键先重放回执，不因报价版本变化再消费或发奖。当前状态仍由客户端成功后重新 GET 获取。

## 重新领养

费用为 **512 宠物币**，配置集中在 `ApiV3PetRules.READOPTION_COINS`，通过目录 readoptionRules 返回。四种物种可以重新选择，包括同物种；不是改名入口，必须在界面明确确认会重置成长。

`POST /api/v3/me/pet/readoption` 请求：

```json
{
  "petId": "当前宠物ID",
  "speciesId": "hexiaotun",
  "name": "小豚",
  "catalogVersion": 3,
  "resetAccepted": true
}
```

必须当前本人 petId、有效物种、通过原名字校验及内容审核、明确的布尔 true；当前宠物需要空闲，没有待领取活动且不在休息。服务器检查目录版本与余额，不能由客户端指定费用。

同一严格事务内：旧宠物结算后归档，删除 user_pet 当前行、创建新的 petId，扣 512 币，写重新领养日志/流水/回执。任一步失败全部回滚。回执为 `{operation:"readoption",petId:"新ID",previousPetId:"旧ID",committedAt}`。旧成功原键继续返回历史回执，旧 petId 的新命令拒绝，不能误操作新宠。

| 重置 | 保留 |
|---|---|
| 新宠物 ID、物种、名称与领养时间；四项状态为 100；经验、等级和亲密度从初始值开始；新宠技能、职业熟练度和次数为 0 | 钱包（仅扣费用）、全部背包、收藏、账号日成长日期/次数、补给领取日期、收入预算与最高结算日、事件链进度和已领事实 |
| 当前无活动 | 原 petAvailableAt/playAvailableAt 冷却原值，避免换宠刷互动 |

不发初始 100 币或 6 口粮，不删除旧宠物的技能、工作进度、活动、结果、日志和账本。旧成长记录仍归旧 ID，不投影为新宠成长；旧活动和日志继续可在原历史接口查询。归档目前只为留存原主体，不增加公开浏览或恢复旧宠物入口。

## 日志、迁移与安全边界

统一 journal 新增 kind=story/readoption，原 all 也包含。story 事件 action=story-claim、正向 itemChanges、coinsDelta=0；readoption 的 action=readoption、petId 为新 ID、name 含目标物种名、coinsDelta=-512、itemChanges=[]。两类 effects 全零、collectionId=null；重新领养重置不伪装为本次经验负增量。其余通用字段和旧日志保持原样。

先执行前四批迁移，再执行 `sql/20261113_api_v3_pet_stories.sql`，新增：

- pet_story_progress：账号/事件唯一、已完成步数、完成/领奖时间、冻结完整链快照及更新时间。不从历史回填。
- pet_archive：主键为旧宠物 ID、账号、归档时间、结算后的主体快照。

repository.json 与 Repository 名同步；表前缀由框架管理。两新路由仍是 required-bearer，服务内重新验证本人第一方与 settings.write，账号用户锁串行化事件、领取和换宠。不新增旧客户端接口，不改验证码、防火墙、匿名白名单、Cookie、积分或上传下载。

## 验收与交付

自动化执行 `node tools/experiments/api-v3-pets-test.mjs`（真实生产 javac + 隔离持久化）、验证码盾指纹、路由访问测试、契约审计、JS 版本检查。执行结果另补；不使用 Maven，不把隔离 SQLite 视为 MySQL 负载或真实极验验收。

本地迁移、Java 重新编译并重启后，用临时测试账号验证：

1. 十二门课程显示清晰，进阶门槛不足不扣费；达到 20 后真实开始，未到期不能领，原键恢复不重扣，旧活动仍能查看/领取。
2. 三条链按新动作顺序推进，越序动作不提前完成未来步骤；同键不多步，重复打开状态不发奖，三步完成才可领。忙碌时领奖拒绝，不消失奖励。
3. 整链奖励只发一次固定物品；账号换宠后保留进度及已领事实，不能刷奖励；坏快照、写入/提交失败不产生部分效果。
4. 512 币重新领养确认内容明确。验证不足余额、休息、进行中/待领取活动、旧 petId、无 true 确认、审核失败等拒绝；成功新 ID、原日限冷却预算与记录保留。
5. 回包丢失用原键恢复，换号清除前端私有数据；同账号两个连接并发换宠/领奖仍只一次消费/奖品，旧成功回执不能覆盖当前新宠状态。
6. PC/窄屏家园、课程、事件、重新领养窗口和日志可操作，动画仍采用原静态回退，无后台轮询。

本地提交不代表人工验收通过或可部署，需开发者亲自核对真实环境的核心链路与界面。

## 2026-11-13 安全及静态检查记录

- `python3 tools/check-captcha-shield.py`：24 个受保护入口通过，未增加验证码盾例外。
- `node tools/api-v3-route-access-audit-test.mjs`：11 组通过；14 个宠物路由全部 required-bearer，新写接口均有 settings.write 与 writes.handle。
- `node tools/js-version-audit.mjs`：扫描 172 个 JS，版本号命中 0。本批没有经典 PC/mobile 模板、脚本、样式、CDN URL 或 source map 修改，不需要重建这些资源。
- `git diff --check` 通过；新增两表 SQL 与 repository.json 字段逐项一致（pet_story_progress 8 字段、pet_archive 4 字段）。
- 全仓契约审计：317 条路由/309 条文档，仍是原有 8 个非宠物接口缺文档；没有新增缺口或重复路由。
- 全仓访问审计仍报告原公开勋章图片 `/api/v3/users/{userName}/medals/image` optional-bearer 缺匿名门禁，未为消除此告警擅自改动旧功能。
- 反漏扫范围复核：匿名与旧 apiKey 不能进入宠物本人资源；服务内再次校验第一方凭据/用户/权限；不改 anonymous.viewSkips、黑名单、极验或 CSRF 链路。所有外部参数在固定白名单内并通过绑定参数进入 SQL，无新可调用 URL、上传下载、目录枚举或 HTML 输出路径。真实极验、Linux 封禁链及生产负载本批未执行，不能视为已经验证。

### 后端专项实际结果

`node tools/experiments/api-v3-pets-test.mjs` 最终退出 0：五批合计 **36 组**（6+8+6+8+8）全部通过，真实生产入口和规则 Java 25 编译通过；未执行 Maven。第五批覆盖十二课程目录与进阶门槛/冻结成果、三链顺序与越序/同键防重复、领奖条件及物品一次性、冻结旧链奖励、旧 v3 活动领取推进、重新领养重置/保留边界、旧 petId 新键拒绝与旧成功键回放、各写入点/提交/回执故障完整回滚、进度插入失败回滚原 feed、双连接换宠/领奖/出发竞争、坏快照、真实 HTTP 路由及权限校验。`git diff --check` 通过。

SQLite 双连接及故障注入不替代 MySQL/InnoDB、实际浏览器或开发者自测；本地运行态联调结果由主协作者另行补录。

### 本地运行态联调

2026-11-13，已在 `rhythm-dev-mysql` 本地测试库执行第五批迁移两次，均成功；Java 25 编译并重启本地 8081 服务。只使用用户已授权创建的临时账号，不向线上写入。

- 真实 HTTP 读取返回目录版本 3、活动版本 4、六门进阶课程、三条旅程及重新领养费用 512。
- 先饮水不会跳过喂食目标；喂食、饮水、玩耍按序推进至可领奖。原键重复领奖返回相同回执且物品只增加一次，新键重复领取返回 409。
- 领取当日补给后重新领养，钱包只减少 512，生成新宠物 ID，经验/亲密度回到初始值。背包、收藏、旅程状态和补给/成长日期及次数保持；旧主体归档存在，旧 ID 新动作返回 404，统一日志可查 care/story/readoption。
- 实际目录、聚合状态和日志返回通过 website_v3 严格解码。其 SSR 首屏未输出测试账号、私人宠物或令牌。
- 前端独立浏览器夹具完成 PC、390px、320px 及暗色检查；真实 HTTP 与界面夹具分开验证，未冒充真实浏览器全链路或生产验收。

临时测试账号仅为本轮测试补充宠物币与可互动状态，不改站点积分。自然等候 30 分钟及全链课程/工作流程由隔离时钟测试覆盖，开发者真实操作和玩法平衡待人工验收。不自动部署或推送。
