# v3 图片表情成员与URL登记

## 实际文件与能力

- `src/main/java/org/b3log/symphony/processor/ApiV3EmojiItemProcessor.java`：四条注册声明，覆盖旧URL上传到全部、按URL/全局图片ID入组、成员改名排序、移出五种能力。
- `src/main/java/org/b3log/symphony/service/ApiV3EmojiItemService.java`：namespace→本人→回执→有界本人组→图片/成员当前锁，默认初始化、图片池/成员/镜像/回执严格同根；独立primitive与保存点。
- `src/main/java/org/b3log/symphony/util/ApiV3EmojiItemInputs.java`：图片URL完整身份、非空名称复用冻结分组纯文本规则、可空名称、精确整数排序。
- `src/test/java/org/b3log/symphony/service/ApiV3EmojiItemTest.java`、`tools/api-v3-emoji-item-test.mjs`：真实Processor、路由、中间件、Service和SQL的Java25隔离专项。

未改冻结分组/读取、旧入口、总register、API/UPDATE_LOG/AGENTS。没有新增表或Repository，不需新迁移。

路由：POST `/api/v3/me/settings/emojis`（URL登记到全部）；POST `/api/v3/me/settings/emoji-groups/{groupId}/emojis`（url/emojiId二选一）；PUT/DELETE `/api/v3/me/settings/emoji-groups/{groupId}/emojis/{itemId}`。全部settings.write、Idempotency-Key；认证只读Bearer，Cookie/query/body apiKey不提升身份。

## 已确认与实现规则

- 名称可空，trim后非空同分组1..64UTF16纯文本，允许中文/空格/emoji，拒绝控制和HTML，不截断。空白名规范为空；name省略为空。sort省略0，新成员sort<=0沿旧max+1，空组1；溢出503整笔回滚；更新sort按给定signed32位整数保存，省略0。更新省略name设空。
- URL仅合法HTTPS，最多512UTF16，保留原始完整query及大小写；无远程下载/探测。拒绝首尾空白、控制、反斜杠、用户凭据、片段、缺host、无效端口和Unicode代理对。
- 全局有效emojiId可复用，不要求本人上传或已持有。当前状态必须0；URL同样只复用有效记录，不复活deleted。URL SQL使用BINARY精确完整比较，Java再次检查字符串一致，不能让CI数据库误复用路径或签名大小写。
- 首次新增自动造全部组(type1/name全部/sort0)，自定义组入组及全部镜像同根。已有成员重复新增返回原member id/name/sort，不用请求元数据覆盖；缺全部镜像时在同根补齐。name/sort独立属于各组成员。
- PUT按groupId+itemId绑定本人组和真实成员，图片当前有效才允许；仅改当前成员name/sort。DELETE同绑定；删除自定义组成员仅删该item，不删全部镜像。删除全部成员则按其emojiId移出本人所有组；保留全局图片、其他用户组与分享。移除已有引用不要求图片仍处于有效状态。
- 本人多个全部组、同组多份同图片成员、多个精确URL记录或未知组type均503失败，不自动清别人/历史数据。完整本人组预算4096，超预算整笔503，不只处理前N个。
- namespace复用既有Option.ID_C_STATISTIC_MEMBER_COUNT行（注册/邀请码链路同顺序），先于actor和回执；保证不同actor经新核心创建精确URL串行。此锁不得改成只锁actor。旧入口未接锁，不宣称新旧默认组/URL绝对唯一或整体串行；无新unique索引。
- 幂等回执显式响应白名单；每次及重放先重读当前有效本人。重放返回历史结果而不重建已删成员/默认、不覆盖后续改名或移除；同操作/同key不同规范化请求409。URL完整query可能是图片访问必要凭据，不在失败日志中输出。

## 实际验证

执行 `node tools/api-v3-emoji-item-test.mjs`，Java25编入真实代码，不使用Maven。沿既有group/primary夹具使用真实SQL与strict保存点/提交；用户/回执及Option锁存在性是适配，SQLite去除FOR UPDATE并断言。URL表真实COLLATE NOCASE，MySQL BINARY语句替换成SQLite UTF8 BLOB等值，实际执行路径/query大小写区分而非返回预设结果。

8组：

1. 实际Dispatcher、Bearer/settings.write、namespace先于actor；默认创建/镜像/回执原子、重复不覆盖与后续更新重放。
2. NOCASE schema下完整URL字节区分、同URL复用，合法全局他人上传图片ID复用。
3. 重复新增补齐全部镜像且保留名称；自定义移除/全部跨本人级联；其他用户、图片、分享保留；重复DELETE回执恢复。
4. namespace/占位回执/当前用户/组/图片/成员/镜像/回执/保存点/释放/严格commit故障，默认/新图片/两组成员同退；级联删除中点失败完整回滚。
5. 无Cookie/apiKey回退、错scope、归属/item绑定、deleted图片、类型字段、key、HTTPS/名称/Unicode边界。
6. 提交成功ack丢失恢复一次，停用后重放拒绝、鉴权至root停用竞态阻止写。
7. 重复全部/精确URL/member、4096预算、sort溢出整笔失败不做历史清理；primitive要求根。
8. 实际成员UPDATE故障回滚、primitive级联保存点保护调用方其他写入，保存点回滚失败中止整个根。

尚未进行真实MySQL BINARY/锁等待/死锁与多实例竞争、完整HTTP服务、PC/mobile浏览器及生产联调；SQLite无法证明这些语义。

## 安全与反漏扫复核

所有新增路由明确Bearer settings.write，不裸放匿名；当前用户锁内重验，group+item绑定。SQL参数绑定固定表名；图片不请求远端，无SSRF获取。名称纯文本且响应只投影id/groupId/emojiId/name/sort/url或id/groupId/deleted，无用户私密字段/全表对象。通用写中间件、有界请求/完整组集合，异常不截断成功。原始URL query只作业务数据，不写错误日志。

没有修改BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor、anonymous.viewSkips、CDN/静态资源/sourceMappingURL或上传下载返回路径。全站黑名单/验证码/解封/开发关闭风控、版本号/可枚举目录未在线回归，不能宣称全站反漏扫通过。旧入口未改造，既有Cookie/apiKey/CSRF边界保持；v3没有Cookie身份。

## 人工完整链路

先IDEA重新编译或重启服务端，准备profile.read/settings.write Bearer及另一个用户：

1. GET本人分组空→POST自定义组→POST成员URL（带签名query）→GET两组与成员：确认自动造全部、两组共emojiId但不同itemId、URL完整。无自定义组时POST `/me/settings/emojis`可直接初始化全部并入组。
2. 重复URL/emojiId新key入组，确认同id、不覆盖原name/sort；先人为移除全部镜像，再重复自定义入组确认镜像补齐。签名/路径大小写不同应产生不同emojiId。deleted图片、他人group/item、url和emojiId同时提交均失败。
3. PUT成员改名/负sort→GET当前组改变，全部镜像名称不跟改；重放原add返回原回执、GET保持新值。DELETE自定义item保留全部；再DELETE全部item移出本人所有组，其他用户/分享/全局图片保留。重放原DELETE成功，新key404；回执不能重建已删对象。
4. 测试name空/中文/32个emoji成功；控制/HTML/65UTF16失败；HTTP、带凭据/片段、超512URL、缺Bearer/错scope失败。全局有效他人上传emojiId可以添加。
5. 数据库测试副本在镜像/receipt/commit及级联删除中点故障，检查默认组/图片/全部成员/回执整笔回滚；账号停用后重放拒绝。并发不同actor同精确URL检查新核心仅一图片，重复默认/URL历史和sort溢出应503不清数据。真实MySQL及旧写并发限制需单独验证。

## 公开API片段供主代理合并

### 登记图片表情到全部分组

`POST /api/v3/me/settings/emojis`

登记HTTPS图片地址到本人全部分组。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| url | HTTPS图片地址，最多512个UTF-16单位，完整保留query | https://example.com/a.png?sign=AbC |
| name | 可选，trim后最多64个UTF-16单位纯文本，可空 | 摸鱼 😀 |
| sort | 可选32位整数，默认0；新增时不大于0则追加 | 0 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emojis' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-upload-001' \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/a.png?sign=AbC","name":"摸鱼 😀"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 成员 | 对象 |
| - id | 分组成员ID | 1790000000001 |
| - groupId | 全部分组ID | 1790000000000 |
| - emojiId | 全局图片ID | 1790000000002 |
| - name | 本组成员名称 | 摸鱼 😀 |
| - sort | 本组排序 | 1 |
| - url | 完整图片地址 | https://example.com/a.png?sign=AbC |

> 全部分组不存在时自动创建。此接口登记图片地址，不接收文件。地址不能包含用户凭据或片段。已存在成员返回原成员，不覆盖名称和排序。

### 添加图片表情到分组

`POST /api/v3/me/settings/emoji-groups/{groupId}/emojis`

按HTTPS地址或有效全局图片ID添加到本人分组。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，本人分组ID | 1790000000000 |
| url | 与emojiId二选一，HTTPS、最多512个UTF-16单位 | https://example.com/a.png |
| emojiId | 与url二选一，有效全局图片ID | 1790000000002 |
| name | 可选，trim后最多64个UTF-16单位纯文本，可空 | 收藏 |
| sort | 可选32位整数，默认0；新增时不大于0则追加 | 0 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000/emojis' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-add-001' \
  -H 'Content-Type: application/json' -d '{"emojiId":"1790000000002","name":"收藏"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 目标组成员 | 对象 |
| - id | 分组成员ID | 1790000000001 |
| - groupId | 目标分组ID | 1790000000000 |
| - emojiId | 全局图片ID | 1790000000002 |
| - name | 本组成员名称 | 收藏 |
| - sort | 本组排序 | 1 |
| - url | 完整图片地址 | https://example.com/a.png |

> 自定义分组添加时同时加入全部分组；缺少全部分组时自动创建。重复添加返回原成员，保留原名称/排序，并补齐缺少的全部分组副本。相同URL按完整路径与query区分大小写。名称不能包含控制字符或HTML标签。

### 修改分组图片表情

`PUT /api/v3/me/settings/emoji-groups/{groupId}/emojis/{itemId}`

修改当前分组成员名称和排序。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，本人分组ID | 1790000000000 |
| itemId | 路径参数，该组成员ID，即GET返回的id | 1790000000001 |
| name | 可选，trim后最多64个UTF-16单位纯文本，省略或空字符串设为空 | 新名称 |
| sort | 可选32位整数，省略设为0，可负数或并列 | -1 |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000/emojis/1790000000001' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-update-001' \
  -H 'Content-Type: application/json' -d '{"name":"新名称","sort":-1}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 修改后成员 | 对象 |
| - id | 分组成员ID | 1790000000001 |
| - groupId | 分组ID | 1790000000000 |
| - emojiId | 全局图片ID | 1790000000002 |
| - name | 修改后名称 | 新名称 |
| - sort | 修改后排序 | -1 |
| - url | 完整图片地址 | https://example.com/a.png |

> 仅修改该分组成员，不同步改其他组同图片的名称/排序。itemId是分组成员ID，不是emojiId。

### 移出分组图片表情

`DELETE /api/v3/me/settings/emoji-groups/{groupId}/emojis/{itemId}`

移出本人分组成员。需要 Bearer Access Token（settings.write）及 Idempotency-Key，无需正文。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，本人分组ID | 1790000000000 |
| itemId | 路径参数，该组成员ID | 1790000000001 |

请求示例：

```bash
curl -X DELETE 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000/emojis/1790000000001' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-remove-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 移除结果 | 对象 |
| - id | 成员ID | 1790000000001 |
| - groupId | 目标组ID | 1790000000000 |
| - deleted | 已移除 | true |

> 从自定义分组移出仅影响该组；从全部分组移出会同步移出本人所有分组。不会删除其他用户成员或已有分享。同键重试返回原结果；新键操作不存在成员返回404。
