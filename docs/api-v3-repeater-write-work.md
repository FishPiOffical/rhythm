# v3 复读机写入工作记录

## 实际文件与状态

- `src/main/java/org/b3log/symphony/processor/ApiV3RepeaterPublicationProcessor.java`：POST `/api/v3/repeater/items`。
- `src/main/java/org/b3log/symphony/service/ApiV3RepeaterPublicationService.java`：本人有效状态、内容、真实职业来源事件与幂等回执严格同事务；回执不保存正文。
- `src/main/java/org/b3log/symphony/util/ApiV3RepeaterPublicationInputs.java`：三类 type（joke/kfc/fish）trim；正文 trim→Jsoup.clean(none)→空白折叠→2..500 UTF-16；无效Unicode/空字节拒绝。
- `src/main/java/org/b3log/symphony/processor/ApiV3RepeaterLikeProcessor.java`、`service/ApiV3RepeaterLikeService.java`：PUT/DELETE `/api/v3/repeater/items/{itemId}/like`，设置而非切换，同键重放返回原回执但不覆盖后续状态。
- `src/main/java/org/b3log/symphony/service/RepeaterLikeMutationService.java`：新旧共用的用户→目标→点赞锁序；仅本人重复点赞收敛为1或0，其他人历史重复不改，计数按全部真实行。
- `src/main/java/org/b3log/symphony/service/RepeaterMgmtService.java`：旧 toggleLike 使用共享核心及严格根事务，仍返回 liked/repeaterContentLikeCount；嵌套调用拒绝，避免提交别人的事务。旧投稿保持。
- `src/test/java/org/b3log/symphony/service/ApiV3RepeaterWriteTest.java`、`tools/api-v3-repeater-write-test.mjs`：Java 25 隔离专项。
- 总 register、公开 API 与总进度由主代理整合；本模块不修改其他批次。

## 事务与边界

新接口仅 Bearer content.write，经通用写中间件及幂等；Cookie、query/body apiKey 不提升身份。JSON仅允许业务白名单，POST请求体16384字节，点赞请求体4096字节。所有根使用 ApiV3Transactions.strict；primitive要求已有事务并用保存点，无法回滚保存点时中止整笔根。

点赞锁定当前有效用户、有效目标，再锁本人点赞；读取本人超过4096条时整笔503，不截断清理。目标锁被新旧全部可达点赞写入共用；COUNT(*) 使用 FOR UPDATE 当前读，避免在已有RR快照中读到旧总数。计数包含其他人的历史重复，负数/溢出拒绝。当前无新增表或SQL迁移。

## 实际验证

执行 `node tools/api-v3-repeater-write-test.mjs`（未使用Maven），真实 Processor、路由声明、中间件、业务Service、旧toggle方法、职业capture方法及严格提交代码被编入隔离专项。SQL在SQLite执行（去除FOR UPDATE，断言锁语句/顺序），用户与回执仓库连接及职业来源持久层为夹具。

9组覆盖：

1. POST规范化、真实职业capture、正文不进回执、同键重放/冲突。
2. PUT/DELETE显式设置、重放不覆盖后续设置、真实旧toggle成功字段。
3. 只修本人重复、他人重复保留、真实剩余总数。
4. 投稿/职业来源/点赞/计数/回执读写、保存点、提交失败无部分状态，恢复后同键成功。
5. 旧toggle严格提交失败回滚，拒绝嵌套且不提交调用方事务。
6. 缺失/错误Bearer、scope、Cookie/apiKey伪造、JSON重复/多余字段、key、Unicode/长度、无效用户/目标。
7. 提交成功但ack丢失后仅一次投稿/职业事件，重放不覆盖后续取消；鉴权后停用用户阻止写入。
8. primitive根要求、保存点保护调用方其他写入、回滚保存点失败整笔中止。
9. 本人历史重复超预算整笔失败，不写回执。

主代理另执行 `python3 tools/api-v3-mysql-current-read-test.py`，隔离MySQL 8.4.11三项SQL实测通过：旧RR快照普通COUNT仍旧、锁定COUNT取得新提交；本人删除计入当前计数并可整体回滚；两个目标写者被同一行锁串行化且计数等于真实行数。临时容器无网络/公开端口且已移除，未连接业务库。它只证明该SQL锁/计数语义，不是Java API完整事务的MySQL验收；完整HTTP、旧新实际并发、PC/移动和生产仍未验证。SQLite本身不证明MySQL锁行为。

## 反漏扫与安全复核

新增三个路由均明确 content.write Bearer，不新增匿名开放；旧点赞路由既有登录中间件保持，处理方法通过中间件写入的当前用户。未修改 BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor、anonymous.viewSkips、静态CDN资源、上传下载返回路径；未进行全站在线反漏扫，不能宣称全站通过。

越权：actor只取认证用户并在锁内重查；目标仅有效公开复读内容。CSRF：v3无Cookie/apiKey身份回退；旧入口边界保持。XSS：服务端纯文本清洗，返回投稿白名单不含正文。注入：SQL参数绑定，表名来自固定Repository。SSRF：不请求远端URL。滥用：通用写中间件、有限请求与本人重复上限，未改变旧入口业务频控。保密：仅id/type/createdTime或id/liked/likeCount，失败通用错误；不返整用户/来源事件/内部回执。旧点赞行为改变已经用户批准。

## 人工完整链路

先在IDEA重新编译或重启服务端，再使用新Bearer运行：

1. 从复读目录取得类型，POST提交包含HTML、换行的正文，成功得到id/type/createdTime；GET列表确认纯文本归属与职业记录最终只增加一次。同key重试同正文返回同id，不同正文409；1/501字符、未知类型、伪造owner422。
2. GET列表→PUT点赞→重复PUT新key，计数仅加1；DELETE→重复DELETE，计数仅减1。旧POST toggle同一目标，确认新GET及旧显示计数一致。缺Bearer401、只读scope403、停用账号/无效目标失败。
3. 同key PUT后新key DELETE，再重放原PUT，返回原回执但GET仍未点赞。并发旧toggle与新PUT/DELETE，验证最终表计数与真实行一致；用已有RR快照的数据库事务配合另一用户已提交点赞，验证COUNT当前读不会写回旧值。
4. 数据库测试副本故障注入职业事件/回执/提交错误，确认内容、来源事件、点赞与计数原子回滚；仅本人历史重复收敛，他人的不清理；超预算失败不改数据。

## 主代理可用公开 API 片段

### 投稿复读内容

`POST /api/v3/repeater/items`

提交纯文本复读内容。需要 Bearer Access Token（content.write），并提供 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | joke、kfc 或 fish，首尾空白会移除 | joke |
| content | 去HTML并折叠空白后2至500个UTF-16单位 | 今天也要开心摸鱼 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/repeater/items' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: repeater-post-001' \
  -H 'Content-Type: application/json' \
  -d '{"type":"joke","content":"今天也要开心摸鱼"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 投稿结果 | 对象 |
| - id | 内容ID | 1790000000000 |
| - type | 类型 | joke |
| - createdTime | 创建时间，毫秒 | 1790000000000 |

> 同一键与相同规范化内容重试返回原结果；同一键提交不同内容返回409。

### 设置复读内容点赞

`PUT /api/v3/repeater/items/{itemId}/like`

`DELETE /api/v3/repeater/items/{itemId}/like`

PUT设置点赞，DELETE取消点赞。需要 Bearer Access Token（content.write），并提供 Idempotency-Key。请求无需正文。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| itemId | 路径参数，内容ID | 1790000000000 |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/repeater/items/1790000000000/like' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: repeater-like-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 设置结果 | 对象 |
| - id | 内容ID | 1790000000000 |
| - liked | 本次结果 | true |
| - likeCount | 点赞数 | 12 |

> 重复设置相同状态不会重复点赞或减计数。同键重试返回原结果，不覆盖后来完成的设置。
