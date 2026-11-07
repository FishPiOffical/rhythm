# v3 积分开通会员交接（2026-10-22）

内部工程交接，不能整体复制到公开 API 文章。用户已确认显示配置白名单/服务器默认值、无效券422不扣费，以及有效/永久会员禁止重复购买、自然日期限与礼包原子化。

## 实际文件与接线

- `src/main/java/org/b3log/symphony/processor/ApiV3MembershipOpeningProcessor.java`：POST `/api/v3/me/membership`，Bearer activity.write、共享写门禁、Idempotency-Key，解析前4096字节边界与字段白名单。
- `src/main/java/org/b3log/symphony/service/ApiV3MembershipOpeningService.java`：账户对锁与缓存边界、严格幂等根、当前用户/档位/券/会员/礼包、保存点，提交后缓存失效及可幂等修复的积分冗余索引。
- `src/main/java/org/b3log/symphony/util/ApiV3MembershipOpeningInputs.java`：levelId与可选couponCode规范化，不接受作者/价格/完整配置。
- `src/test/java/org/b3log/symphony/service/ApiV3MembershipOpeningTest.java`：生产路由/授权/控制器/SQL、实际PointtransferMgmtService/BagMgmtService、严格JDBC/保存点/确认丢失专项。
- `tools/api-v3-membership-opening-test.mjs`：Java25隔离编译与临时SQLite执行，复用已存在PrimaryTest的JDBC故障基础设施，不调用实际应用或支付。
- 本文件：契约、证据、完整人工链及风险。

总接线由主代理加入 `ApiV3MembershipOpeningProcessor.register();`。共用 `ApiV3MembershipConfiguration.defaults(level)` 由 membership_settings 代理维护；共享 Membership/Level/Activation/Coupon/Cloud/Pointtransfer/Request 既有表，无新表。主代理增加 `sql/20261022_api_v3_membership.sql` 的 pointtransfer_redc(rawId,userId) 普通索引，须先迁移再启用；它不删除既有重复索引记录。

## 精确业务与边界

1. POST仅接收levelId（档位GET的id）与可选couponCode，字符串数字ID；coupon按旧ASCII trim去首尾空白，非空时8–32 UTF-16，不统一大小写或限制ASCII，内部空格/合法Unicode保留，拒控制和孤立代理项。码匹配沿数据库存量比较语义（CI库大小写不敏感），查询LIMIT2拒绝重复，不自行Java equals改变匹配。
2. 当前账号必须有效。无会员可买；既有唯一记录state为0或1，expiresAt必须非负精确long。expiresAt=0永久无论state都409；state1且尚未到期409；state0非永久视未激活可购买，过期记录均可重新购买。此state0边界经主代理明确裁决。重复会员行或损坏字段503。
3. 档位price为非负int，durationValue为正int，durationType只保留档位标签；实际到期时间以服务器默认时区的次日零点起，加durationValue个自然日，DST/闰月不使用固定24小时算术。配置由完整benefits服务器默认值初始化，过期重购也如此；请求不能自行传configJson。存量level schema/default无效503。
4. 无券按原价。非空券不存在、非折扣、次数0或<-1、discount不在0..100均422，不扣费；数值类型损坏、重复码或仓储故障503，禁止降级原价。discount按正数四舍五入，0免费亦消费有效有限次数；-1不限，正数减少1。余额不足409且不消费券。
5. 精确年档礼包：VIP1_YEAR/2_YEAR/3_YEAR分别增加20/50/120张checkin1day，VIP4_YEAR增加366 sysCheckinRemain；其他码不送。先检查sys-bag最多唯一一行，不顺带合并/删除重复坏存量；赠送目标计数严格非负int。沿旧Integer.MAX_VALUE容量饱和，保留无关背包项；坏计数503整笔不写。
6. 完整actor/sys积分账户对在事务前获取。ArticlePublicationCaches包住根，当前有效actor在每次及重放前锁定，随后选中档位→券→本人会员→背包；同owner锁与新配置setter串行，配置读取多个同码周期的锁序不冲突。会员行仅更新对应字段，不覆盖createdAt或其他账户资料。
7. 积分转账调用当前事务primitive并indexAsync=false；其null返回必须抛错，使转账、券、会员、activation、礼包及回执整笔回滚。业务数据与保存点严格提交。旧开通仅由主代理最小补齐账户对锁在DB前，原计费/返回/礼包和配置核心未改，不能称新旧共同原子化或共享幂等。
8. 幂等内部回执为 `{result:公开四字段,indexTransferId:已提交主账ID}`，不包含券码、配置或密码。Service首发/重放只显式投影result固定四键，Processor不裸返wrapper。内部索引ID不出HTTP；损坏回执503。相同键绑定规范化完整请求，新键才新业务。
9. 提交后重复失效MembershipCache，绝不提前put事务快照。积分主表dataId为HTML编码且按完整码点/实体有界的可读开通说明；确定性billingHash仅放sourceRequestId，sourceScene=membership.open，旧积分查询会去掉内部请求号。不会把hash作为descriptionHtml显示，也不让管理员名称注入HTML。
10. 为确认丢失重放恢复索引，按回执私有主账ID进行PK/current root查账，再绑定actor/sys/type/sourceScene/sourceRequestId；父主账行锁串行同笔索引修复。对actor/sys各按userId/rawId有界查redc，已有记录不再插，缺少才插时间ID并严格提交。禁止调用旧append-only异步indexTransferAsync，否则每次重放会增加重复流水。普通索引迁移保证查找不扫描全部历史。后置故障只记录通用延迟日志，不改变已提交开通结果；重放可再次修复。

## 实际测试证据

`node tools/api-v3-membership-opening-test.mjs`：Java25 **12组通过，进程退出0**，日志 `/tmp/rhythm-v3-membership-opening.log`。

1. 实际POST路由/鉴权/控制器、points与bag保存、服务器defaults和自然日、HTTP首发及重放固定四字段；原键不重复扣费/礼包或redc。
2. 四种年档礼包及月档无赠送。
3. 大小写按存量SQL、ASCII trim、半价/0价/不限次数、次数只减一次。
4. 不存在/非折扣/用尽/非法策略券422，无资金、礼包、activation或回执变化。
5. 有效/永久409；state0非永久及到期允许；余额不足不消耗券，重购重置服务器默认配置。
6. 用户/档位/券/会员查询、积分更新/主账/券/会员/activation/礼包/回执写、保存点创建/释放及实际JDBC commit失败全部回滚，同key恢复。
7. 真实提交完成但确认丢失，原key恢复且不重复购买；当前账号停用后重放拒绝；缓存及postindex查询/写故障不反转成功，之后重放修复，redc始终两行。
8. Cookie/apiKey/错误scope、额外owner/price/configJson、非法ID/码、重复JSON、缺key及字节上限无业务写，同键换合法请求409。
9. 小数/错误周期/重复码/坏state/坏ID/重复会员/坏背包与重复背包整笔不写，容量饱和保留无关项。
10. 说明文字对HTML和emoji按完整码点编码有界、不含billingHash；SQLite EXPLAIN实际使用recipient复合索引；损坏私有回执503，不泄露索引ID或重复扣费。
11. 固定时钟验证America/New_York夏令时切换及Asia/Shanghai闰月在本地午夜到期。
12. 编译实际旧MembershipMgmtService，受控begin断言已持有actor/sys账户对锁；异常释放锁，空参数不进入DB。

实际代码：OpeningService/Controller/Auth/Write/Idempotency/PointtransferMgmtService/BagMgmtService/CloudRepository/事务保存点及严格JdbcTransaction。隔离适配：SQLite移除FOR UPDATE、用户与仓储连接、Token查找/信封/Cache/异步旧依赖；OWASP Encode依赖在runner中用Apache HTML编码适配，生产仍调用项目Escapes/OWASP。fixture故障含预期日志，不代表真实业务错误。没有Maven、真实MySQL/IoC/HTTP服务、浏览器、生产或支付调用；SQLite索引计划不能当作MySQL多连接证明。

## 完整人工链路

Java修改联调前请用户通过IDEA重新编译或重启，先迁移上述索引及已存在v3基础表。

1. A/B取得activity.write/profile.read/content.read。GET会员档位→A选择levelId→POST带新key→GET本人会员，确认paidPoints、服务器defaults和正确自然日到期；PC/mobile会员页与积分/背包核对，B未改变。
2. 使用有效有限/不限券、大小写相同存量码、免费券、中文合法码；价格按规则，有限次数只减一次。不存在/非折扣/用尽422，余额不足409，会员/积分/券/礼包/activation均保持；不要在生产进行真实扣积分测试。
3. 对四年档逐一核对checkin1day20/50/120及sysCheckinRemain366，月档不赠。已有其他背包项保持，容量上限饱和；重复/坏背包返回503且不收费，不合并删除坏记录。
4. 当前有效及永久用户新key409；到期和state0非永久账号重新开通成功且重置defaults。不同账号、仅Cookie/apiKey、缺Bearer/scope、伪造owner/price/configJson均拒绝；缺字段/坏枚举/错类型/重复JSON/超4096字节/key冲突按400/413/422/409，不带出内部回执。
5. 原key请求重复、网络重试、提交确认丢失后原key恢复，会员/余额/券/礼包/activation不重复，积分列表仅一笔。后置redc失败时HTTP仍成功，原key恢复后积分列表补齐且不增加重复项；初次与重放HTTP只含四键，没有sourceRequestId/indexTransferId/券码/config。
6. 测试库注入每阶段业务/回执/保存点/commit失败，全链无半写；与新配置、旧开通/配置、背包操作、同券跨用户并发，验证MySQL行锁/死锁重试与优惠次数不超卖；检查索引EXPLAIN与大历史数据性能。固定测试时钟跨月/闰年/DST核对自然日。
7. 回归原匿名/黑名单/test/validateCaptcha解封、anonymous.viewSkips与开发关闭风控；检查公开API鉴权/日志敏感字段、CDN版本/sourceMappingURL/目录枚举/上传下载路径。本轮无新增匿名/静态/上传下载，不能据此声称全站反漏扫运行通过。

## 限制

旧MembershipQuery可在新提交后迟put旧快照，旧开通仅共享账户对前置锁，旧配置及旧计费尚未共用新列更新/严格根；保留缓存与跨旧入口竞争风险，v3 GET直接查询数据库不使用该cache。赠包默认共享Bag primitive，但新购买已拒绝重复bag，不顺手清理。真实MySQL当前读、多实例、连接释放、索引迁移及完整两端链路仍待运行验收；不以适配器/源码注册称已部署。

## 公开 API 片段

### 积分开通会员

`POST /api/v3/me/membership`

使用积分开通所选会员档位，需要 activity.write 和 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次购买的幂等键 | membership-open-001 |
| levelId | 会员档位列表返回的id，字符串 | 1700000000001 |
| couponCode | 可选优惠券，去除首尾普通空格后8–32个UTF-16单位；不传或空字符串按原价 | SAVEHALF |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/membership' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: membership-open-001' \
  --header 'Content-Type: application/json' \
  --data '{"levelId":"1700000000001","couponCode":"SAVEHALF"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 本次开通结果 | `{...}` |
| - id | 本人会员记录标识 | 1700000000002 |
| - levelCode | 所选档位编码 | VIP1_YEAR |
| - paidPoints | 本次实际支付积分 | 500 |
| - expiresAt | 到期毫秒时间戳 | 1824134400000 |

> 配置由档位默认值初始化，不接受自定义价格或完整权益配置。当前有效会员和永久会员不可重复购买；已到期或非永久未激活账号可以开通。期限按服务器时区，从次日零点起加档位durationValue个自然日。
>
> 无效、非折扣或次数用尽的优惠券返回422，不扣积分；余额不足或会员不可重复开通返回409，档位不存在404。请求体最多4096字节，超限413。开通失败不消费优惠次数或发放部分礼包。
>
> VIP1_YEAR、VIP2_YEAR、VIP3_YEAR分别赠送20、50、120张免签卡；VIP4_YEAR增加366天免签剩余天数。成功后可重新读取本人会员、积分和背包。同键同请求恢复原结果，不重复扣积分或发礼包；同键更换内容409，响应中断请使用原键和原请求重试。
