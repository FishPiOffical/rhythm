# API v3 积分转账（实现冻结，2026-10-23）

内部实现与验收记录；公开文档仅提取契约，不复制锁、限额或风控细节。

## 文件与入口

- `src/main/java/org/b3log/symphony/processor/ApiV3PointTransferProcessor.java`：POST `/api/v3/me/point-transfers`，Bearer `activity.write`、通用写入检查、4096 字节 JSON 与幂等键。
- `src/main/java/org/b3log/symphony/service/ApiV3PointTransferService.java`：主款、手续费、通知及 revision、回执的严格根事务，提交后索引修复和通知刷新。
- `src/main/java/org/b3log/symphony/util/ApiV3PointTransferInputs.java`：字段白名单与类型、总金额、用户名及备注规范化、整数手续费。
- `src/test/java/org/b3log/symphony/service/ApiV3PointTransferTest.java` / `tools/api-v3-point-transfer-test.mjs`：真实积分 primitive、通知 Repository/revision SQL、严格 JDBC 提交故障及实际 Dispatcher/Auth 专项。

总注册和公共文档由主代理统一整合；本模块不修改旧 `/point/transfer`。

## 已确认契约

总金额为含手续费整数，2 至 Integer.MAX_VALUE；接收者须当前有效他人，排除旧 COM_BOT 账号。普通手续费 floor(amount/100)，至少 1，剩余到帐。精确 `VIP4_YEAR` / `VIP4_MONTH`、state=1 且永久或未到期免手续费；实际手续费收款账号当前精确 `admin` 自己发起也免手续费，主款仍从本人扣除，无 sys 代付。

请求为 `userName`、`amount`、可选 `memo`。备注与旧规则一致：trim 后最多 128 UTF-16，再使用项目声明的 Jsoup/Safelist.none 去 HTML；省略为 `""`。客户端不可提供 fromId、fee 或会员状态。余额不足、接收方积分溢出均 409，不产生部分账。

成功只返回 `transferId`、`recipient.{userId,userName}`、`amount`、`receivedPoints`、`feePoints`、`memo`。相同本人/幂等键/规范化请求返回原结果，接收者改名或停用、手续费账户变更及会员到期不使已提交回执遗失；重放仍校验当前本人有效性，变更请求 409。

## 锁、写入与恢复

前置仅查询收款账号和精确 admin 的 ID；持有 actor→receiver 和 actor→admin 完整共享账户对后才打开严格根。根内按账户 ID 字典序当前读锁用户，再锁回执；新业务另锁会员。回执提示仅供锁定位，不能越过根内的 body hash 与本人验证。

实际使用 `PointtransferMgmtService.transferInCurrentTransaction(..., indexAsync=false)`，主款或手续费任何一步 null 均回滚根；`NotificationRepository.add` 及其 revision、幂等回执同根提交。额外保存点也不能掩盖失败。admin 同时是接收者时，按主款加手续费检查总增量。免手续费不要求无关 admin 当前有效，避免会员被停用收费账号误拒绝；前置精确 admin 缺失/重复/大小写伪装仍视配置损坏 503，不能猜测替代收款者。

内部回执保存 feeRecipientId/feeTransferId，HTTP 只能显式投影公开字段。索引在已提交主账 PK 锁下校验 from/to/sum/type/scene/request hash，仅补缺失 `(userId,rawId)`；失败不逆转已完成转账，同键重试可修复。通知刷新仅提交后执行，刷新失败不重复转账或通知。需要已有 pointtransfer 来源字段迁移和 `sql/20261022_api_v3_membership.sql` 索引迁移；不新增表或 Repository。

## 实际专项与限制

执行命令：

```bash
API_V3_JSOUP_JAR=/tmp/rhythm-jsoup-1.21.1.jar node tools/api-v3-point-transfer-test.mjs
```

Jsoup 使用 pom 已声明的真实 1.21.1 依赖；默认寻找 Maven 本地对应 jar，可用环境变量覆盖。没有使用模拟 HTML 清洗实现。Java25 临时编译真实服务/processor/积分核心/通知 Repository/revision/strict/idempotency/auth/write；SQLite 双连接执行实际 SQL，去掉不支持的 FOR UPDATE 语法但保留并断言事务与锁调用。缓存、Token 认证发行和传输信封为夹具。

9 组覆盖：实际路由/Auth 与公开投影；手续费边界和精确会员免除（含无关 admin 停用）；admin 本人扣款及作为接收者的聚合；回执在改名停用后的恢复；主款、第二笔手续费、通知、revision、回执、保存点、吞提交异常的整根回滚；丢提交确认及索引修复、刷新失败；自转/机器人/无效用户/溢出/损坏会员；Bearer scope、未知字段、JSON 类型/重复键/体积/备注/幂等键；私有字段注入拒绝及账户锁释放。日志 `/tmp/rhythm-v3-point-transfer-finish.log`。

未运行 Maven、真实 MySQL、多实例并发、完整应用 HTTP、PC/移动浏览器或生产请求。SQLite 不能证明 MySQL 行锁、死锁重试和隔离级别的实际行为。

## 接口安全与反漏扫

入口只用 Bearer activity.write，无 Cookie/query/body apiKey 提升，故页面 Cookie 不能单独触发转账；匿名没有新放行通道。服务锁后重读当前 actor/收款人/会员，禁止客户端选择扣款人和费用。SQL 参数绑定；JSON 成功响应白名单；备注去 HTML 且不拼网页；不抓远程 URL，无新增 SSRF、静态资源、CDN、下载路径、sourceMappingURL 或目录。幂等及已有写入中间件控制重试/请求，未扩充公开 anonymous.viewSkips。

BeforeRequestHandler 黑名单、AnonymousViewCheckMidware `/test`/验证码、CaptchaProcessor 解封和开发风控关闭链路未改；专项验证 Bearer 路由未鉴权拒绝，但整站黑名单跳转、验证码解封、CDN、上传下载及枚举仍需主代理/环境人工反漏扫，不据本专项宣称通过。

## 完整人工验收

Java 改动生效前，用户必须先通过 IDEA 重新编译或重启服务端；执行既有积分来源字段、通知 revision、幂等及会员索引迁移。

1. 从 v3 登录/授权取得带 activity.write 的 Bearer，查询本人积分；POST `/api/v3/me/point-transfers` 给有效他人 amount=100、memo 含简单 HTML。成功应本人 -100、对方 +99、admin +1，响应只有公开六项（recipient 为对象），对方通知/未读刷新和积分历史对应。
2. 用同键同体重试，包括模拟响应丢失后重试：账和通知都只有一份，返回原 transferId；同键改 amount 得 409。购买后让对方改名/停用或本人会员到期，再用原请求重试仍恢复原回执；本人停用后不能恢复。
3. 分别用未到期/永久精确 VIP4_YEAR、VIP4_MONTH 及 admin 本人发起：feePoints=0、对方得到全额、本人实际扣全额；到期、state=0、相似或大小写会员码不免除。有效 VIP4 在无关 admin 停用后仍可免手续费；普通用户此时不扣款。
4. 自转、机器人、未激活/停用/不存在接收者、余额不足、收款溢出、amount=1/浮点/字符串/超范围、备注超长、未知字段、缺幂等键分别失败，积分、主账、通知和回执不变。admin 作为收款者检查总入账，不能因拆两笔越过整数上限。
5. 无 Bearer、仅 Cookie/apiKey、错误 scope 不可转账；查看响应和 HTML/日志确认没有内部 fee 元数据或用户私密字段。PC/移动各打开对方通知与积分历史确认显示和跳转。
6. 真实 MySQL 下并发同键、不同键同收款者、相互转账及与旧入口并发，核实金额守恒和锁释放；故障注入第二笔手续费、通知/revision/回执/提交确认时，验证整笔失败或丢确认后同键可恢复，索引延迟不会重复扣款。

## 公开文档可用片段

### 转账积分

`POST /api/v3/me/point-transfers`

向当前有效的其他用户转账，需要 `activity.write`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，请求头 | Bearer ACCESS_TOKEN |
| Idempotency-Key | 同一次操作使用同一幂等键，请求头 | points-transfer-001 |
| userName | 接收者用户名，不支持自己或机器人 | receiver |
| amount | 含手续费的整数总额，至少2积分 | 100 |
| memo | 可选，trim后最多128字符，清除HTML | 谢谢 |

请求示例：

```bash
curl -X POST 'http://localhost:8080/api/v3/me/point-transfers' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: points-transfer-001' \
  -H 'Content-Type: application/json' \
  --data '{"userName":"receiver","amount":100,"memo":"谢谢"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 状态说明 | 空字符串 |
| data | 转账结果 | 对象 |
| - transferId | 转账记录ID | 1760000000000 |
| - recipient | 接收者 | 对象 |
| -- userId | 接收者ID | 10001 |
| -- userName | 本次转账时用户名 | receiver |
| - amount | 总扣款积分 | 100 |
| - receivedPoints | 对方实际到账积分 | 99 |
| - feePoints | 手续费积分 | 1 |
| - memo | 处理后的备注 | 谢谢 |

> 普通手续费为总额的1%向下取整，至少1积分；有效的VIP4_YEAR/VIP4_MONTH及手续费收款账号本人发起时免手续费。总额包含手续费。余额不足或接收者积分超过上限时返回409，不扣款。
>
> 同键同请求返回原转账结果，接收者后续改名或停用不改变该历史结果；同键不同请求返回409。旧转账接口保持原规则。

主代理最终输入复核：接收者用户名按1至64位字母/数字/下划线/连字符校验；备注原始trim后128 UTF16，拒绝孤立代理项/非文本控制字符，清洗后须符合已有memo列255 UTF16预算，超出422，避免返回与实际截断存储不一致。新增边界仍包含在第8组，主代理最终9组重跑退出0。receivedPoints仅主款，若接收者亦手续费收款者，其实际总入账还包含独立手续费款。
