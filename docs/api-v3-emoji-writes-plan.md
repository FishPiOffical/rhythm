# v3 图片表情剩余写入盘点与拆分

本文件为只读源码盘点及待确认方案，不代表接口已实现。现有四个v3 GET和常用emoji PUT不属于本次11项图片表情写入。未改既有代码、未执行Maven或数据库操作。

## 当前证据与可达范围

旧入口在 `src/main/java/org/b3log/symphony/processor/EmojiProcessor.java#register`，全部挂 `loginCheck::handle`。真实注册11个POST：

| 能力 | 旧入口 | 实际链路与重要行为 |
| --- | --- | --- |
| URL上传到全部 | `/api/emoji/upload` | `uploadEmoji`→查已有全部组→`addEmojiByUrl`→`addEmojiToGroup`；不是文件上传，缺全部组失败，两次写不在一个根 |
| 创建分组 | `/api/emoji/group/create` | 检查name→`createGroup`，type=0、自带sort，不创建全部组 |
| 修改分组 | `/api/emoji/group/update` | 入口检查归属→`updateGroup`，同时完整传name/sort，全部组拒绝修改 |
| 删除分组 | `/api/emoji/group/delete` | 入口检查归属→`deleteGroup`，全部组拒绝删除；删该组全部成员与组，保留全局图片及其他组引用/分享快照 |
| 按图片ID加入分组 | `/api/emoji/group/add-emoji` | 组归属→图片全局存在→当前组未包含；目标非全部组时，再单独提交加入全部组 |
| 按URL加入分组 | `/api/emoji/group/add-url-emoji` | 组归属→全局URL复用/创建→成员新增→另提交镜像到全部组；重复检测在全局图片写入之后 |
| 移出分组 | `/api/emoji/group/remove-emoji` | 自定义组只删本组引用；全部组则枚举本人所有组逐组删除该emojiId；不删全局图片 |
| 创建分享快照 | `/api/emoji/group/share/create` | `createShareSnapshot`独立根，当前本人自定义非空组；保存当时url/name/sort复制快照，与后续组改名/删除无关联更新 |
| 导入分享 | `/api/emoji/group/share/import` | `importShareSnapshot`独立根，凭分享码新建本人自定义组，确保全部组，两组各建成员，全局URL复用，分享导入计次+1 |
| 成员名称/排序修改 | `/api/emoji/emoji/update` | 入口组归属+itemId绑定目标组→`updateEmoji`只改该成员name/sort，不改同图片在全部组/其他组的名称 |
| 历史迁移 | `/api/emoji/emoji/migrate` | 读本人cloud gameId=emojis JSON字符串数组；要求全部组预先存在，逐URL全局创建/复用、逐成员独立提交，已有成员跳过；不删云源 |

`batchUpdateGroupSort` / `batchUpdateEmojiItemSort` 虽有Processor与Service方法，但没有Dispatcher注册；不能把它们计成已存在可达能力。可通过分组或成员修改接口更新单项sort；未来是否额外提供批量排序是新增需求。

核心来源：`EmojiMgmtService`、`EmojiQueryService`、`EmojiGroupRepository`、`EmojiGroupItemRepository`、`EmojiRepository`、`EmojiShareRepository`；模型字段在 `src/main/resources/repository.json` 的 emoji/emoji_group/emoji_group_item/emoji_share。分享现有迁移为 `sql/20260318_emoji_share.sql`，分享码有唯一索引；未查到URL、用户+分组名、分组+emojiId的数据库唯一约束，不能因模型描述“全局唯一”就宣称数据库保证。

## 旧规则的精确口径

### 名称与排序

- 旧分组create/update校验上限实际 **20 UTF-16单位**（注释写30不准确），字符表为Unicode字母/数字/`\\s`及 `_ - 。 ， ！ ？ ! ? : ： ； ; （ ） ( ) [ ] { } ·`；不接受emoji图形、引号/尖括号/斜杠。名称非空，存储create/update不trim；所以首尾空格也是旧值一部分。
- 添加成员可空名；非空同20上限/字符表。成员update没有相同名称校验，直接保存任意name；不应把它误认成统一安全规则。
- 普通分组sort来自optInt默认0，未限制负数或并列；查询只按sort排序，无稳定oId次序。
- 新成员sort>0原样保存；sort<=0取现有组max+1（空组1），未保护int溢出。成员update直接写sort（可0/负数）。复制到全部组是独立成员，可拥有不同sort/name。
- 全部组type=1、名称全部、sort0。单项update/updateSort/delete禁止；未注册batch方法没有相同全部保护。自定义type=0。
- 旧GET groups会调用createAllGroup产生默认组；v3 GET严格只读，不能靠先读v3触发初始化。

### 图片共享与URL

- 图片表是全局池，uploaderId只是初始上传者。旧按emojiId加入仅查存在，不要求本人上传/已有持有，甚至没有emojiStatus=0校验；故“必须本人上传”不是既有规则。
- `addEmojiByUrl` 去第一个`?`及之后内容，未trim、未验证HTTP/HTTPS、长度或凭据，也不抓取远程文件。分享normalizeUrl会先trim再去query。查询按URL数据库等值找第一条，可能受CI collation影响；相同签名query被去掉后只存基地址。
- 不能直接把全局emojiId当成成员ID。GET返回`id`为emoji_group_item主键，`emojiId`为图片主键；更新名称按itemId，旧删除按groupId+emojiId。
- 旧添加成员与全部组镜像不是一个事务；全部组不存在时可能前半已提交。旧“移出全部”可能只删一部分组后失败。

### 分享与导入

- 分享码 RandomStringUtils随机12位数字字母→大写；导入trim并大写，格式8..16 `[A-Z0-9]`。无过期、撤销、次数上限或所有者状态要求，任何已登录持码者（含本人）可重复导入，每次新组。
- 分享只禁止全部组；空快照失败。快照枚举成员并读取图片，缺失图片/空URL会跳过，但不筛emojiStatus；内容只存url/name/sort，没有文件实体复制。
- 分享分组名清洗trim、截20，字符不合法回退未命名分组；成员名非法/过长置空。导入不解析来源当前权限，完全用快照。
- 导入始终新建组。重名依次追加`-导入`、`-导入2`…至999，截原名前缀保证20；新组sort最后一个已排序组的sort+1。会确保全部组存在，不调用会自提交的公开createAllGroup。
- 导入中无效item/空URL直接跳过。全局已有URL复用图片，成员重复跳过，但emojiCount按成功处理item数自增，故可能大于实际新成员数；导入计次没有锁当前share行，现有并发存在丢计数风险。
- 分享是持有者主动发布的快照；来源之后删除/隐藏、图片置deleted是否撤销旧快照，旧代码没有答案，需要明确v3规则。

### 历史迁移与事务

- `CloudService#getFromCloud`捕获所有异常并返回空字符串，无法区分真空、无记录和数据库故障；新核心应直接按owner+gameId读取、检查重复记录/结构，禁止把故障当“无需迁移”。
- 迁移不造全部组、不删cloud原数据、无整批回执；数组有一项异常就失败但前面的写可能已完成。
- 大量普通写Service标`@Transactional`，会受AOP事务边界影响；delete同时显式begin/commit。share/import显式拥有根；旧方法不适合作为v3 primitive直接嵌套调用。v3应使用独立 `...InCurrentTransaction` + 保存点，HTTP服务严格根把变更与回执一起提交。

## 现有 v3 读取整合契约

`ApiV3EmotionProcessor` / `ApiV3EmotionQueryService`：

- `GET /api/v3/me/settings/emoji-groups`：profile.read，仅本人，分页；id/name/sort/type，sort+oId稳定排序；无默认组时空列表，不写。
- `GET /api/v3/me/settings/emoji-groups/{groupId}/emojis`：profile.read，本人归属在初查及count/data JOIN均绑定；仅emojiStatus=0，成员返回id/emojiId/name/sort/url，sort+oId稳定排序；不存在或他人组404。
- 写入结果建议沿用这些字段和ID含义。旧全局URL512上限可作为技术存储上限，GET已有safeUrl输出过滤；分组名读最多64，成员名读最多255。新写规则必须与GET可表达范围协调，不擅自扩大数据库字段。
- 新写初版建议settings.write + 通用写中间件 + Idempotency-Key，actor只取Bearer并锁内重验；这是方案，尚未注册HTTP。

## 最少待用户确认的问题

分组名称trim1..64、允许中文/空格/emoji纯文本、拒绝控制及HTML已由主代理询问，等待答复；本文件不预设回答。

建议分两批询问，避免阻塞能先完成的分组：

1. **成员及URL规则（成员批前）**：成员名称是否可空、同分组纯文本字符规则但最多255；URL是否只允许合法HTTPS、完整保留query、最多512，不下载/不验证远端图片？按emojiId是否仅允许“本人已持有的有效图片”，或延续“任意全局有效图片可复用”？后者贴近旧行为，前者影响复制使用方式，需要明确。
2. **全部组语义（成员/迁移批前）**：首次写图片是否在同事务自动创建唯一全部组；自定义组新增同步入全部、移出全部同步清本人各组、移出自定义只删该组、删组保留图片/其他组/既有快照是否沿用？新增已在组内时建议返回已有成员不改其name/sort，以设置语义支持重复，不走旧错误。
3. **分享复制规则（分享批前）**：是否延续登录持码导入、无过期、每个新key新建独立组、来源删除不撤销快照；同key恢复同组；导入无效或已禁图片是整批422/409、还是跳过并准确返回导入/跳过数量？需同时确认跨用户分享用途；不能只从上传者属性决定可访问。
4. **迁移规则（迁移批前，可并入第2问）**：保留云源、仅复制到全部且已有项跳过；异常URL/数组项整批拒绝还是跳过报告；首次自动造全部组？整批原子、同key重放是技术保证，不需反复征求。

分组sort可以直接沿旧整数语义（32位有符号、默认0、允许并列，不重编号），全部组保护与删除级联沿已明确旧行为；若改为自动追加、禁负数、完整重排，属于新业务规则，需要另问。分组重名延续数据库等值，锁本人防止v3自身并发重名；旧写未共锁，不能宣称新旧串行。

## 可落地的模块划分与顺序

A. **分组基本写入（先行3项）**：独立 `ApiV3EmojiGroupProcessor/Service/Inputs`；创建、更新、删除。锁本人当前有效账户→回执→本人组（目标按ID）→成员。只有创建/更新需要待确认名称规则。删除不调用旧自提交Service，原子删本组成员与组，默认组409；回执只白名单元数据。重放重验本人但不重做删除/覆盖后续改名。可先设计严格根/primitive/错误与回执测试骨架，不实现未确认规则。

B. **成员与URL登记（5项）**：upload-to-all、add-by-ID、add-by-URL、remove、update-item。共用所有权/默认组/绑定校验primitive；owner行把本人不同组操作串行，组按ID锁，图片池复用需有跨用户统一锁命名空间或数据库唯一约束，不能只锁actor就宣称全局URL唯一。锁序要在URL/shared资料前统一，不在持有数据库锁时再取反向Java锁。对涉及全部组的批量删除以有界完整集合处理，超预算失败，不截断。

C. **分享与导入（2项）**：分享owner与源组一致当前读、有效成员白名单快照；新码应SecureRandom、唯一插入重试；导入actor/shared namespace/share/group顺序统一，与B共用当前事务图片/成员primitive；分享行导入计数当前读、保存点和回执同根。凭据不进入公开日志、积分账本；回执可存本人分享code用于幂等恢复，但禁止公开他人快照内部ID。

D. **迁移（1项）**：直接锁本人cloud源，完整结构/URL预算校验后在同根造全部/复制/去重；保留或消费源遵照用户选择，回执带真实处理统计及目标组ID；复用B的primitive，不调用现有CloudService容错读取或旧自提交方法。

这些只是工程拆分；并发开发时A/B/C都会涉及共享锁/primitive，必须先由一人固定接口与锁顺序再分工，不分别造三套默认组初始化。

## 后续验收边界

每个写接口均需实际Java25隔离编入真实Processor/Service/SQL，故障注入写/回执/commit/保存点、归属伪造、停用actor重放、重复key冲突、后续修改后重放不覆盖。至少一条端到端：已有v3 GET空列表→创建/重命名→URL入组及全部镜像→成员改名排序→分享→另一用户导入→迁移→删除，错误场景覆盖他人group/item、默认保护、非法URL/HTML/Unicode、重复组、坏快照、超预算整批失败。

联调前提醒用户IDEA重编译或重启；真实MySQL并发/数据库collation、PC/移动页面、首访验证码/黑名单/解封/开发模式、静态资源与路径反漏扫均需另外实际验收。本次只读盘点没有执行这些运行检查，也没有新增匿名入口或改anonymous.viewSkips。
