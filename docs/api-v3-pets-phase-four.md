# 宠物第四批：进阶探索、配方、收藏与日常日志

本批延续前三批真实用户锁、本人第一方 Bearer、独立宠物币与原键幂等，不扩展到站点积分、重新领养或动画。服务器执行费用、技能、库存及活动互斥校验，客户端只展示目录与当前状态。

## 目录和状态

`GET /api/v3/pets/catalog` 的 `version=2`、`activityVersion=3`。原种类、课程、工作不删除；新增目录如下。

| 物品 | ID | 价格（宠物币） | 使用效果 |
|---|---|---|---|
| 口粮 | food | 5 | 饱食 +35 |
| 鲜果 | fruit | 6 | 饱食 +20、心情 +8 |
| 清泉饮 | spring-water | 5 | 水分 +35、精力 +5 |
| 芦苇 | reed | 6 | 材料，不可喂食/饮水 |
| 森林种子 | forest-seed | null | 只通过探索获得 |
| 海贝 | sea-shell | null | 只通过探索获得 |

物品结构为 `{id,name,description,category,price,hunger,effects:{hunger,water,mood,energy}}`。`hunger` 保留旧字段，新增 `category=food|drink|material`；价格 null 表示不可购买。状态背包列出全部已知物品，没有对应库存行时返回 0，但原 food 行缺失视为异常。新库存仅写入时创建，GET 不创建。

所有活动目录增加 `requirements:[{skillId,experience}]`，没有门槛为 []；工作保留原来的单个 `requirement` 以兼容。所有计划/实际成果增加 `items:[{itemId,quantity}]`，无材料为 []。计时活动当前状态不重复返回目录的 requirements。

| 进阶地点 | ID | 解锁要求 | 材料成果 |
|---|---|---|---|
| 荷塘采集 | lotus-pond | 探索经验 10 | 芦苇 ×2 |
| 森林寻种 | deep-forest | 体能经验 10 | 森林种子 ×2 |
| 潮汐拾贝 | tidal-flat | 知识经验 10 | 海贝 ×2 |

三处均 20 分钟，消耗 5 宠物币、12 精力、4 饱食、6 水分；领取增加 25 宠物经验、6 心情、8 对应技能经验及上述材料，不增加宠物币、工作熟练度或口粮。沿用出发冻结快照、到期手动领取、单活动位置和原键恢复。

| 配方/永久收藏 | ID | 要求与消耗 |
|---|---|---|
| 芦苇小舟 | reed-boat | 探索 10、芦苇 ×2、2 宠物币 |
| 森林风铃 | forest-chime | 体能 10、森林种子 ×2、2 宠物币 |
| 贝壳相框 | shell-frame | 知识 10、海贝 ×2、2 宠物币 |

配方为 `{id,name,description,requirements,ingredients:[{itemId,quantity}],coins,collectionId}`，collectionId 与配方 ID 相同。收藏目录为 `{id,name,description}`；状态顶层 `collections:[{id,unlockedAt}]` 为账号永久解锁，未来重新领养不能清空。制作不增加经验/技能，已拥有不可再付费制作。

## 操作及事务

- 食品通过原 `actions` 的 `feed + itemId` 使用；饮料通过 `water + itemId`，无 itemId 仍为免费饮水 +40。
- 鲜果/清泉饮任一加成仍有空间即可使用，全部效果已满拒绝消费；原口粮、免费水保留原本主数值大于 99 的限制。成长仍受原每日照顾额度限制。
- 商店沿用 `{petId,itemId,quantity,catalogVersion}`，数量 1～20；价格由服务器目录决定。不可购买材料、把材料作为食物或错误类别均拒绝。
- `POST /api/v3/me/pet/crafts {petId,recipeId,catalogVersion}` 使用 settings.write 与原 `Idempotency-Key`；宠物空闲且不在休息，重验技能/材料/余额/未拥有后，扣料、扣币、收藏、日志、流水、回执同事务。失败完整回滚，同键重放不重复扣费。
- 写回执仍只有 `{operation,petId,committedAt}`；craft 的 operation 为 craft。历史回执不代表当前余额，确认后读取聚合状态。
- 原活动快照 v1/v2 读取时只补确定为空的 requirements/items 投影，领取仍按原冻结参数；已存实际成果没有 items 的旧版本仅投影 []，不回写旧数据。新版本缺字段、错归属或坏材料明确 503。

## 统一活动日志

`GET /api/v3/me/pet/journal?kind=all&p=1&size=10`，本人第一方 Bearer/profile.read。kind 支持 all、outing、course、work、care、craft、purchase；p 1～10000、size 1～50。

返回 `{serverNow,list,pagination:{page,pageSize,total,pageCount}}`。旧 `/activities` 保持只读计时活动；新接口在数据库 UNION ALL 计时活动和瞬时事件，按时间、ID 倒序分页后联查本页详情和实际成果，不向应用层拉取全部历史、不逐条补查。计时活动按 startedAt 排序、瞬时事件按 occurredAt，正在进行/待领取/已领取均保留。UNION 合并排序仍可能扫描本人较多索引候选，长历史需要按 MySQL EXPLAIN 与真实数据量评估，不宣称常数查询成本。

- `recordType=activity`：原活动历史字段、petId、claimedAt、actualRewards。
- `recordType=event`：`{id,petId,kind,action,name,occurredAt,itemChanges,coinsDelta,effects,collectionId}`。
- care 的 action 为 feed/water/pet/play/rest/wake；craft/purchase 各为同名 action。
- itemChanges 是有符号的非零实际库存变化；coinsDelta 是本次实际币变化。
- effects 的 hunger/water/mood/energy 允许小数净变化，experience/friendship 为非负整数。先离线结算，再记录操作前后差值，不把离线衰减或休息恢复混进照顾效果。
- 制作/购买 effects 为零；制作 collectionId 为真实解锁 ID，其他为 null。
- 日志与消费/状态同事务；失败和原键重放不增加重复日志。只写入本批之后发生的日常行为，不从旧账本拼装未记录的照顾历史。

## 迁移及兼容

先执行前三批迁移，再执行 `sql/20261112_api_v3_pet_exploration.sql`。新增 pet_collection（账号+收藏唯一）与 pet_journal（本人时间/分类时间索引）。repository.json 同步结构，Repository 名不含 symphony_ 前缀。新 SQL 只创建表，不修改/回填既有活动、库存和结果；应用部署需要迁移先行。

职业熟练度、共享产币预算、免费补给、旧活动读取和第一方授权链保持原样。新接口不接收作者身份、价格、奖励或任意 SQL；外部账号只能读取自己的行。新增无公网图片/CDN/上传下载入口，不涉及经典 PC/mobile 皮肤资源版本。

## 自动化与人工验收

自动化入口：`node tools/experiments/api-v3-pets-test.mjs`（真实生产入口 javac 与 SQLite 持久化）、`python3 tools/check-captcha-shield.py`、`node tools/api-v3-route-access-audit-test.mjs`、`node tools/js-version-audit.mjs`。测试结果在本轮交付记录中补充，未用 Maven。

人工验收（本地临时账号，先重新编译/重启 Rhythm）：

1. 已有宠物读取保持原余额/技能/工作进度，6 类库存和收藏正常；另一账号与游客不可访问其私有日志。
2. 未解锁进阶地点/配方不消费；上对应入门课后可进入对应地点，正式计时自然结束后领取材料一次，重复同键不再发奖。
3. 买鲜果/清泉饮并使用，核对多属性封顶及库存；材料不可作为食物，材料不足/币不足/已有收藏/忙碌时制作均不扣款。
4. 制作一件纪念物，核对 -2 材料/-2 币/收藏时间；刷新、跨设备、同键恢复、不同键重复均不可二次扣费。
5. 照顾六动作、购买、制作和旧活动在统一记录中按真实分页/分类展示，日志效果不包含离线衰减。旧已领取活动缺实际成果的情形仍显示未知。
6. 浏览器检查桌面/窄屏的家园、商店、背包和记录交互，换号清私有数据，失败恢复保留原命令，不引入后台轮询。

本地提交不等于人工验收完成；尚未得到开发者亲测反馈前保持待人工验收。

## 本轮自动化记录（2026-11-13）

- `node tools/experiments/api-v3-pets-test.mjs`：四批合计 28 组（旧 20 + 新 8）全部通过。真实生产 Processor/Service/Repository/规则工具 javac 通过，SQLite 持久化覆盖三处探索/技能门槛/材料领取、物品分类及封顶效果、制作永久收藏、统一日志真分页、旧 v1/v2 快照、坏 JSON、各消费写点与提交故障、原键重放与并发；未运行 Maven。
- `python3 tools/check-captcha-shield.py`：24 个受保护入口全部通过。
- `node tools/api-v3-route-access-audit-test.mjs`：11 组全部通过。
- `node tools/js-version-audit.mjs`：扫描 172 个 JS，版本号命中 0；本批未新增经典站脚本或样式资源，无生成产物改动。
- 全仓契约审计：315 条路由、307 条文档，仍有此前存在的 8 个非宠物接口缺文档；无重复、虚假文档路由或注册问题。12 条宠物路由全部 required-bearer 且已文档化。
- 全仓访问审计仍保留此前公开勋章图片 `/api/v3/users/{userName}/medals/image` 的 optional-bearer 缺匿名门禁告警；本批没有新增告警，不为清告警扩大修改范围。
- 反漏扫范围核对：无匿名入口/anonymous.viewSkips 改动，不改防火墙、黑名单、验证码或旧 apiKey 链路；服务内再次检查本人第一方权限，用户锁覆盖原键和所有写操作。SQL 参数化且用户归属来源只取身份；写入拒绝客户端作者/价格/奖励字段。页面文本由前端纯文本渲染，无新 HTML、SSRF、上传/下载路径、CDN URL、source map 或目录枚举入口。
- 本地 MySQL 第四批迁移重复执行两次成功，最终代码重新编译启动。使用已授权的本地临时账号验证旧工作领取、新商品购买/喂食/饮水、抚摸、玩耍、休息/醒来及同键重放；满水拒绝无效饮水。制作技能门槛先拒绝，再仅为该临时账号预置探索经验 10，真实接口成功扣两件材料与两币、解锁永久收藏；相同键不重复扣款、不同键重复制作返回 collection_owned。
- MySQL 统一日志 13 条分两页无重复，care=6、purchase=3、craft=1（另有前三批活动）；真实目录/状态/各页日志通过 website_v3 严格解码器。另实际开始潮汐拾贝并核对冻结海贝奖励；未加速本地真实活动计时，自然到期领取待人工观察，定时结算已由可控时钟专项覆盖。
- 浏览器桌面、390px、320px、浅/暗主题使用真实 Vue 组件与隔离传输夹具验证；详情见 website_v3 的 docs/pets-phase-four.md。真实极验、线上负载与完整真实账号浏览器流程未在本批执行，不能以隔离专项代替生产安全与人工验收。
