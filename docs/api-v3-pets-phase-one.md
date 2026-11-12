# 宠物家园第一批（pet 分支）

状态：基础养成后端已实现；未部署，真实 MySQL 及 PC/窄屏人工验收待完成。静态形象由 website_v3 提供，本批不制作动画，不开放学校、外出、工作或重新领养。

## 入口与授权

| 方法 | 路径 | 权限与请求 |
| --- | --- | --- |
| GET | `/api/v3/pets/catalog` | 第一方当前 `profile.read` |
| GET | `/api/v3/me/pet` | 第一方当前 `profile.read`，没有宠物时返回 `pet:null` |
| POST | `/api/v3/me/pet/adoption` | `settings.write`，`{speciesId,name}` |
| PUT | `/api/v3/me/pet/name` | `settings.write`，`{petId,name}` |
| POST | `/api/v3/me/pet/actions` | `settings.write`，`{petId,action,itemId?}` |
| POST | `/api/v3/me/pet/supplies` | `settings.write`，`{expectedDate}` |
| POST | `/api/v3/me/pet/purchases` | `settings.write`，`{petId,itemId,quantity,catalogVersion}` |

所有写入必须带 `Idempotency-Key`，最多 2048 字节 JSON，拒绝未登记字段及隐式类型转换。用户身份只从既有 V3 Bearer 取出，并在事务中重新验证当前第一方客户端、真实用户、当前 token 与权限；不信任输入 userId，也不兼容 Cookie、apiKey 或第三方 OAuth 令牌。不存在按用户 ID 枚举宠物的公共接口。既有全局防火墙、验证码盾未增加例外。

名字按 Java 25 `\X` 计 1～12 个字素，原输入最多 256 UTF-16 单元，首尾空白规范化；拒绝 HTML 定界符、换行、控制字符、坏代理和双向格式控制，保留组合 Emoji 所需 ZWJ。保存沿现有文本审核。客户端不要自行截断输入法组合。

## 协议

目录 `version:1` 包含四种 `species`（`momota` 摸摸獭、`hexiaotun` 荷小豚、`yubingmiao` 鱼饼喵、`paixiaoyu` 派小鱼）、当前食品与集中规则，未实现的未来目录不展示假条目。

本人聚合读取：

```json
{
  "serverNow": 1794499200000,
  "today": "2026-11-13",
  "catalogVersion": 1,
  "wallet": { "coins": 100 },
  "inventory": [{ "itemId": "food", "quantity": 6 }],
  "dailySupply": { "claimed": false, "coins": 20, "food": 2 },
  "pet": {
    "id": "1794499200001", "speciesId": "hexiaotun", "name": "小豚",
    "hunger": 100, "water": 100, "mood": 100, "energy": 100,
    "experience": 0, "level": 1, "friendship": 0, "adoptedAt": 1794499200000,
    "activity": null,
    "cooldowns": { "pet": 0, "play": 0 },
    "growthToday": { "used": 0, "limit": 10 }
  }
}
```

无宠物时 `wallet.coins:0`、`inventory:[]`、`pet:null`，GET 不创建账号或发首奖。休息活动为 `{kind:"rest",startedAt,endsAt}`；到期读取直接投影空活动，不需要后台轮询结算。

写回执仅 `{operation,petId,committedAt}`。operation 是 `adoption/name/supplies/purchase` 或 `feed/water/pet/play/rest/wake`。前端确认后再 GET 当前状态，读取失败只重读，不能重新创建消费。旧回执跨日/换名字也只确认原操作，不代表当前状态；未知结果原输入和原键保留。

## 第一批试运行数值

- 一账号免费领养一只，赠 100 宠物币、6 口粮。宠物初始四项状态 100，经验/亲密度 0。
- 饱食每小时减 2，水分每小时减 3，心情每小时减 1，最低为 0；不死亡。精力没有自然衰减。
- 底层保留小数及结算时间。读取不写回；动作将小数与结算时间一起持久化，因此连续短时操作也不吞掉离线进度。状态投影中饱食/水分/心情向上取整，精力向下取整，浮点误差只用极小容差处理。
- 喂食消耗 1 口粮、饱食 +35；饮水免费、水分 +40。至少缺少 1 点（内部值不高于 99）才允许操作，避免几毫秒自然衰减即刷成长或浪费口粮。
- 抚摸心情 +10，玩耍心情 +20/精力 -10；分别有 30 分钟冷却。满心情可抚摸或玩耍，亲密互动仍有效，但冷却与日限保持。
- 休息最长 4 小时，精力每小时 +20，可提前醒来。休息中不能喂食、饮水、抚摸、玩耍或重新开始休息；改名、购买、领取补给不受影响。醒来恰好跨过自动到期仍可成功。
- 喂食、饮水、抚摸、玩耍每日前 10 次共享成长：每次 +10 经验/+2 亲密度。休息/醒来不刷成长。每 100 经验升一级，等级最高 30，亲密度最高 100。
- 每个上海自然日补给 20 币及 2 口粮；`expectedDate` 必须与提交当下日期一致。原键已成功则先重放旧回执，不把昨日未知结果换成今日发奖；服务器时钟回拨跨日时不重复领取或重置成长日限。
- 基础口粮每份 5 币，一次 1～20 份。报价版本在首次实际提交时核对；同键恢复不因后来的报价变化重复扣款。

这些参数统一在 `ApiV3PetRules`，目录及执行均引用同一常量。宠物币不兑换站点积分。

## 持久化、锁与兼容

先按实际表前缀执行 `sql/20261112_api_v3_pets.sql`，已同步 `repository.json`。本轮不自动对任何真实数据库执行迁移。

- `pet_account`：`oId=userId`，钱包、账号级每日补给/成长。未来重新领养必须保留此账号账本，不能重复首次奖励。
- `user_pet`：唯一 `userId`，状态、命名、单活动及冷却。后续重新领养用新宠物 ID；现在的动作/改名/购买都绑定当前 `petId`，旧请求不能误操作新宠物。
- `pet_inventory`：`(userId,itemId)` 唯一，可扩展食品和道具。
- `pet_ledger`：首奖、补给、购买、喂食的宠物币/食品变动流水，只作审计，不带凭据。

全部 SQL 参数化。锁序沿第一方授权客户端命名空间 → 用户/token → 幂等回执 → 宠物账户/宠物/背包。真实用户行锁同时串行化同账号不同幂等键；用户唯一索引再防重复领养，回执、余额、背包与宠物同严格事务提交。提交后没有附加状态读取，也没有外部广播副作用。

将来地点、课程、工作可独立增加目录并扩展 `activityKind` 白名单，沿用 `activityStartedAt/activityEndsAt` 的单活动位置。当前未知活动类型必须报错，不伪装为空闲；未来成果领取必须有独立待领取状态、原命令/回执和共享产币预算，不能复用本批照顾成长链。前端不应依据本机时钟发奖励。

不修改旧站发送、登录、积分、游戏存档或任何现有前端资源。未实现公开看宠物、重新领养及活动成果领取。

## 验证与人工联调

专项：`node tools/experiments/api-v3-pets-test.mjs`。依赖 Java 25、已编译工程 `target/classes`、SQLite JDBC 3.47.1.0、TestNG，可用 `API_V3_TEST_CLASSPATH` 指定 IDEA 输出和依赖。输出编译文件与 SQLite 均位于临时目录，结束清理；不访问真实服务。测试包含真实服务/SQL/严格事务/幂等/授权，仅文本审核和 HTTP 输出端口隔离。

反漏扫：`node tools/api-v3-route-access-audit-test.mjs`，以及动态路由审计。保护指纹：`python3 tools/check-captcha-shield.py`。版本号：`node tools/js-version-audit.mjs`（本批没有 JS/SCSS/FTL/CDN 变更）。

人工联调前，请先用 IDEA 重新编译并重启本地 Rhythm，使 Java 与 repository 配置生效，在专用测试库执行迁移。用两个本地临时账号验证：

1. 新账号 GET 空状态；四选一领养并改名，重开和换浏览器恢复同一宠物，第二次领养被拒绝。
2. 领取补给、买口粮、喂食饮水；多标签页同时领补给或消费，余额不负数、首奖与补给不重复。
3. 抚摸/玩耍冷却，休息中动作禁用，提前醒来与自然到期均保留正确精力；离线后刷新不使宠物死亡。
4. 操作成功后的 GET 故障不重新扣费；中断响应后同键恢复；换号后迟到响应不能写入新账号画面。
5. PC/窄屏家园及静态桌宠操作，旧站积分和既有游戏流程不改变。

隔离 SQLite 的双连接测试不替代真实 MySQL/InnoDB 行锁、生产负载或人工验收。人工自测未确认前只能标记待人工验收，本地提交不代表可合入发布。

### 本轮自动化结果（2026-11-12）

- 宠物专项通过六组：名字边界；领养/身份隔离；经济/跨日/提交故障恢复；离线/照顾/休息；双连接并发；七路由 HTTP 中间件与原始请求体校验。测试先编译真实 `ApiV3Processor` 注册闭包，再替换输出端口断言响应；未写入 `target/classes`。
- 四表 SQL 列与 `repository.json` 一致，`git diff --check` 通过。
- 验证码盾 24 项受保护源指纹通过；JS 版本审计扫描 172 个文件、版本号命中 0；本批无 CDN 或 source map 修改。
- 路由访问审计工具自身 11 组通过；本批新增七条路由全部可达并要求 Bearer，五项写入均登记 `settings.write` 和写中间件。
- 全仓契约盘点为 310 条路由/302 条文档，剩余 8 个文档缺口均在既有外观、小尾巴、职业概要和登录 session 接口，本批未扩大修订。全仓访问审计仍报告既有 `GET /api/v3/users/{userName}/medals/image` 的 optional Bearer 缺少显式 anonymous gate；该问题没有被本批测试当成通过，也没有自行改动旧勋章行为。
- Java 25 提示的 Unsafe/SQLite native-access 警告来自既有隔离夹具和依赖，专项退出码为 0。真实 MySQL 行锁、后端实际启动和浏览器全链路仍待人工验收，不能据此宣称已发布或已验收。
