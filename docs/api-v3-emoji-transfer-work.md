# v3 图片表情分享、导入与历史迁移

## 实际文件与行为

- `src/main/java/org/b3log/symphony/processor/ApiV3EmojiTransferProcessor.java`：三个settings.write、通用写中间件及幂等写入口。
- `src/main/java/org/b3log/symphony/service/ApiV3EmojiTransferService.java`：当前本人、快照捕获、独立导入新组、本人云源复制、回执严格同事务；复用现有group/member当前事务primitive，不调用旧独立提交。
- `src/main/java/org/b3log/symphony/util/ApiV3EmojiTransferInputs.java`：分享码、存储JSON及每条历史项严格校验。
- `src/test/java/org/b3log/symphony/service/ApiV3EmojiTransferTest.java`、`tools/api-v3-emoji-transfer-test.mjs`：真实Service/Processor/SQL/提交故障隔离专项。

无新表或Repository，沿现有emoji_share（含分享码唯一索引，先前20260318迁移）与cloud。未改旧入口、已冻结group/member行为、总register或公共API/AGENTS；主代理负责接线与公共文档。

路由：

- POST `/api/v3/me/settings/emoji-groups/{groupId}/shares`
- POST `/api/v3/me/settings/emoji-group-imports`
- POST `/api/v3/me/settings/emoji-migrations`

## 已确认规则与锁边界

分享仅本人非空自定义组；全部组/空组409，非本人/缺组404。快照只url/name/sort，捕获当时组名，SecureRandom生成12位大写数字字母分享码。分享永不过期，来源后续改名/删除不撤销快照；任意持码有效登录者（含本人）可多次导入，每个新key新建独立组，同key恢复原组。没有来源组当前存在性检查，不能添加“删源自动失效”的另一套行为。

所有share/import/cloud原始项目逐项校验，任一HTTP/不合法HTTPS、已禁图片、非法name或JSON结构，整批422且零写入；不跳过坏项目。URL完整保留query、按BINARY精确身份，名称沿64UTF16纯文本规范，成员可空。先验证全部原始项再按完整URL去重，重复项不夸大emojiCount；即使无效项与先前URL相同也不忽略其非法元数据。存储坏数据不能通过旧容错查询变成空或部分成功。

导入始终新建自定义组，并保证全部镜像；名称优先原名，已占用则`-导入`、`-导入2`…，总64UTF16，截前缀不切断代理对。新组sort按本人组最大sort+1，溢出503。快照导入计数当前读取后同根+1，不因回执重放重复计数。导入返回实际唯一图片数及新组信息，不返来源owner/group内部ID或整快照。

历史迁移直接锁本人cloud gameId=emojis数据，不调用会吞异常的CloudService.getFromCloud；保留原云源，仅复制到全部，已有成员保留name/sort，不重复创建。processedCount为有效不同URL数，addedCount按全部成员当前锁定前后真实差值计算；同key恢复原数字，新key重做时已有条目addedCount=0。缺云源或有效空数组409；结构坏/空白字符串422；重复cloud行503。新加入仍自动创建全部。

统一锁顺序：Option会员计数namespace→当前有效本人→回执→本人groups按oId→share/cloud当前行→成员/全局图片。member/group primitive重复获取已经持有的namespace/owner/组锁，不打开新根。所有新转移和成员共享namespace，完整URL跨actor创建串行。旧入口未共用namespace，不宣称新旧绝对唯一/整体串行；历史重复全部组/精确URL/member、损坏type都保留且整笔503。

技术预算：本人组完整4096、原始项目完整256、存储JSON 1MiB，越界503不截断。HTTP仅小型命令字段，请求体4096字节；不把这些内部预算写入公共文档。不会远程抓图，无SSRF请求。分享凭据/带签名URL不写错误日志。

## 实际专项状态

运行命令：`node tools/api-v3-emoji-transfer-test.mjs`，Java25，未使用Maven。主代理最终退出0，7组通过，日志`/tmp/rhythm-v3-emoji-transfer-root-final.log`。共享保存点修复后，分组7组和成员8组同时复跑通过。

编入真实Processor、路由声明、auth/write中间件、转移/分组/成员Service及SQL/strict保存点和提交；SQLite执行SQL，去FOR UPDATE并断言，BINARY转为真实UTF8 BLOB比较，列保持NOCASE。User/Option/receipt/CloudRepository连接为隔离适配；不能以SQLite测试声称MySQL锁竞争已验证。

主代理修复共享 `ApiV3TransactionSavepoint` 的嵌套中止根保护：内层primitive已回滚并关闭根后，外层不再触碰失效连接，保留原始异常；同时检测业务成功后owner仍active且TX未替换。此修复由主代理持有，group7/item8/transfer7专项均复跑通过。

7组覆盖：

1. 分享响应/存储白名单、随机码、同key一次；删除来源后仍能导入，每新key独立组及计次，同key原组。
2. 导入自动全部和镜像、完整签名URL；云迁移按本人、精确去重、真实addedCount、保留源/已有成员及原回执。
3. 非法历史JSON/item/name/HTTP URL/deleted图片整批422，包含先合法后非法的输入，零回执/零图片/零部分组。
4. namespace/placeholder/actor/group/item/image/shared/counter/default/mirror/receipt/保存点/release/strict commit故障完整回滚，恢复后同key成功。
5. 真实路由缺/错Bearer、scope和Cookie/apiKey伪造、他人/全部/空/缺组与缺码、字段/key边界。
6. 三种提交成功ack丢失后仅一次业务、计次，停用用户重放及鉴权到root竞态拒绝。
7. 导入64UTF16安全后缀、超预算/重复云源整笔失败；primitive要求根，保存点保留其他调用方写入，嵌套primitive根已中止时不得二次回滚覆盖原错误。

## 安全与反漏扫

全部新路由Bearer settings.write，主体只取认证本人并每次/重放锁内重查；无匿名新增，Cookie/apiKey不提升身份。share owner/type检查，cloud owner+gameId固定，不能取他人云源；分享码是用户主动发布的导入凭据，不是来源账户授权。SQL参数绑定固定Repository表名；name纯文本/URLHTTPS，无HTML输出、文件读取/路径或远端下载；结果字段白名单不返整表/内部用户资料。通用写中间件和有界集合，故障原子回滚。

未改BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor、anonymous.viewSkips、静态/CDN/sourceMappingURL和上传下载路径。完整服务黑名单/首访验证码/解封/开发关闭风控、静态版本号/目录枚举未在线验收，不能宣称全站反漏扫通过。真实MySQL并发/索引collation、PC/mobile浏览器及生产仍未验。

## 人工完整链路

先通过IDEA重编译或重启服务端，准备A/B两用户Bearer settings.write/profile.read：

1. A创建自定义组→添加HTTPS图片与可空/emoji名称→POST shares得到code；重放同key同code，空组/全部组409、B直接分享A组404。GET确认分享不改原组。
2. B输入code（可小写/首尾空白）导入→GET新组与全部镜像，确认URL签名query保留、实际emojiCount；同key同组，再新key创建后缀新组。A删来源组后B仍可新key导入；全局图片禁用后新导入422，不能复活图片。
3. 本人cloud emojis放合法URL数组及重复/已有URL→迁移→GET全部，确认processedCount按不同URL、addedCount只计真实新增、云源保留；同key原回执，新key已有项addedCount0；不可迁移另一个用户的源。
4. 在share源/快照/cloud任一项插入HTTP、坏name或JSON（包括前面正常后面错误），请求422且组/图片/成员/计次/回执全不变；超过技术预算503不部分成功。64UTF16名称碰撞后缀不切emoji代理对。
5. 测试副本注入镜像、share计次、receipt、commit失败及嵌套保存点失败，确认整笔回滚并能同key恢复；已提交ack丢失重试不重复组/计次，停用账号后回执重放也拒绝。真实MySQL多actor同URL、同码多次导入并发需实际验证。

## 公开API片段供主代理整合

### 分享图片表情分组

`POST /api/v3/me/settings/emoji-groups/{groupId}/shares`

生成本人自定义分组的分享快照。需要 Bearer Access Token（settings.write）及 Idempotency-Key，无需正文。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，本人自定义分组ID | 1790000000000 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000/shares' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-share-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 分享结果 | 对象 |
| - shareCode | 分享码 | ABCDEFGH1234 |
| - groupName | 快照分组名 | 摸鱼 |
| - emojiCount | 快照内不同图片数 | 12 |
| - createdTime | 创建时间，毫秒 | 1790000000000 |

> 仅非空自定义组可分享。快照不随来源组修改或删除失效，分享码长期有效；持码用户可导入。任一图片地址、名称或状态不符合规则时整批422，不生成分享码。

### 导入图片表情分享

`POST /api/v3/me/settings/emoji-group-imports`

从分享快照创建本人独立分组，并加入全部分组。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| shareCode | 分享码，忽略首尾空白及字母大小写 | ABCDEFGH1234 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emoji-group-imports' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-import-001' \
  -H 'Content-Type: application/json' -d '{"shareCode":"ABCDEFGH1234"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 导入结果 | 对象 |
| - groupId | 新分组ID | 1790000000001 |
| - groupName | 新分组名，重名时添加导入后缀 | 摸鱼-导入 |
| - emojiCount | 实际不同图片数 | 12 |
| - sourceGroupName | 分享时的分组名 | 摸鱼 |

> 每个新幂等键创建独立组；相同键重试返回同组。同名依次追加-导入、-导入2等，名称总长不超过64个UTF-16单位。任一地址、名称、结构或图片状态无效，整批422且不创建组。来源组删除不影响快照。

### 迁移历史图片表情

`POST /api/v3/me/settings/emoji-migrations`

将本人历史云表情复制到全部分组，保留历史云数据及已有成员。需要 Bearer Access Token（settings.write）及 Idempotency-Key，无需正文。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| 无 | 使用本人历史数据 | — |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emoji-migrations' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-migrate-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 迁移结果 | 对象 |
| - groupId | 全部分组ID | 1790000000000 |
| - processedCount | 本次历史数据中不同图片地址数 | 12 |
| - addedCount | 实际新加入全部分组的成员数 | 8 |

> 已有成员保留名称和排序。相同键重试返回原结果；使用新键再次迁移时，已存在成员不重复增加。无历史数据或空数组返回409；任一历史项无效则整批422，不部分迁移。
