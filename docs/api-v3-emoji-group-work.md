# v3 图片表情分组基本写入

## 状态与实际文件

新增三个接口，复用现有GET的资源路径：

- POST `/api/v3/me/settings/emoji-groups`
- PUT `/api/v3/me/settings/emoji-groups/{groupId}`
- DELETE `/api/v3/me/settings/emoji-groups/{groupId}`

文件：

- `src/main/java/org/b3log/symphony/processor/ApiV3EmojiGroupProcessor.java`：settings.write及通用写中间件、JSON白名单、16KiB请求体、幂等键。
- `src/main/java/org/b3log/symphony/service/ApiV3EmojiGroupService.java`：严格根、当前账户锁、所属组与type精确校验、创建/更新/删除及回执原子提交；独立primitive要求当前事务。
- `src/main/java/org/b3log/symphony/util/ApiV3EmojiGroupInputs.java`：规范化名称和整数排序。
- `src/test/java/org/b3log/symphony/service/ApiV3EmojiGroupTest.java`、`tools/api-v3-emoji-group-test.mjs`：真实入口/SQL/事务故障专项。

未修改旧图片表情入口、现有v3 GET、总register、公共API/更新日志及AGENTS；主代理负责集成。没有新增Repository或表，无新SQL迁移。

## 已确认行为

名称trim后1..64个UTF-16单位，允许中文、空格、emoji等纯文本，拒绝控制字符、行段分隔符、HTML标签/注释标记及不完整代理对；超长422。名称不以HTML渲染，`1 < 2`等纯文本比较保留。JSON必须name字符串；sort可省略默认0，32位有符号整数、允许负值和并列，拒绝隐式字符串转换和小数。PUT是提交完整name及可选sort（省略恢复0），不做patch合并。

创建type0自定义组，不自动造全部组；沿旧数据库等值检查本人组重名，409，不加新唯一索引/大小写规范化。所有组类型都参与同名检查。更新只改name/sort/updateTime；type1全部组禁止改/删409，其他未知type503。目标owner由当前SQL绑定，不取请求userId。删除仅删除目标组全部成员与组，不删全局emoji、其他组成员、既有分享快照。

每次首次尝试及重放先当前读取并锁本人有效账户，再查回执；实际业务再次重验actor并锁目标组。重放仅恢复原结果，不重建已删除组、不覆盖后来改名，也不重新删除重建的数据。创建/更新返回id/name/sort/type，删除返回id/deleted；响应明确白名单。primitive保存点绑定操作，回滚保存点失败中止整个调用方根。

旧写入未接入同一actor锁，旧GET仍可能创建默认组，不能宣称新旧整体串行或全局重名绝无竞争。仅v3分组操作因共同锁本人行而串行。

## 实际验证

`node tools/api-v3-emoji-group-test.mjs` 退出0，7组PASS。未执行Maven。编入真实Processor、路由声明、auth/write中间件、业务Service、事务/idempotency及原始SQL；SQLite运行实际SQL（移除FOR UPDATE并断言当前读锁语句），用户/回执仓库连接是测试适配。

1. 真Dispatcher POST/PUT→Bearer→规范化→SQL→回执；sort默认0/负数、同键重放和冲突，原回执不覆盖后续改名。
2. 删除组及成员成功，图片/其他组/分享保留；重复同键删除成功，新键缺目标404；创建不造默认组。
3. 所属owner精确、默认组保护、缺组/他人组/损坏type、重名失败无回执。
4. placeholder/读取/业务写/回执/保存点/释放/提交故障整笔回滚，删除成员后删组中点故障也回滚；同键恢复重试成功。
5. Cookie/apiKey不能提升身份，缺Bearer401/错scope403；JSON多余/重复字段、缺key、过大请求、非法整数、控制/HTML/Unicode/64UTF16边界。
6. 提交成功ack丢失后同键只建一个组；当前停用账号新操作和重放均拦截，鉴权后停用竞态不写。
7. primitive禁止无根执行；保存点保留其他调用方写入，保存点回滚失败整笔根中止。

SQLite不能证明MySQL的行锁等待/并发/collation行为。没有实际运行完整HTTP服务、真实MySQL数据库、PC/移动浏览器或生产验证。

## 安全与反漏扫复核

新增路由全部明确Bearer settings.write，不增加匿名入口；当前用户仅认证身份，query/body用户/apiKey和旧Cookie不覆盖。SQL参数绑定、表名固定Repository。纯文本名称拒绝标签，响应白名单无私人账户/分享凭据/图片内部资料。无远程请求、文件路径、上传下载或CDN变更。通用写中间件和有界请求控制批量请求；不新增计费或外部推送。

未改BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor及anonymous.viewSkips；全站黑名单/首访验证码/解封/开发模式、静态版本号/sourceMappingURL/目录枚举未在线验证，不能宣称全站反漏扫通过。旧Cookie/apiKey写入CSRF边界未改。

## 人工完整链路

先通过IDEA重新编译或重启服务端，再以Bearer（profile.read和settings.write）测试：

1. GET本人分组→POST创建` 中文 😀 `，返回trim后的名称、sort0、type0；GET确认可见且未偷偷创建全部组。POST同键相同规范化内容返回同id，不同内容409；已有同名409。
2. PUT目标groupId改名并传负sort→GET确认排序和值；再修改为另一名称后重放第一次key，返回旧回执而GET保持最新名称。64UTF16成功，65/控制/HTML/字符串sort/小数sort422；缺token401，只有profile.read403。
3. 旧页面或数据库测试副本给该组建立图片成员、其他组引用和分享后DELETE；GET组/成员404，图片及其他组/分享仍在。同键DELETE成功，新键404；全部组PUT/DELETE409；其他用户groupId404。
4. 在数据库测试副本模拟删除成员后或receipt/commit失败，确认组和所有成员原样、无成功回执。同key恢复重试；账号停用后重放也拒绝。并发同人创建同名应仅一个成功；与旧未改造写入并发的限制须单独观察，不声称全局唯一。

## 供主代理合并的公开 API 片段

### 创建图片表情分组

`POST /api/v3/me/settings/emoji-groups`

创建自定义分组。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| name | trim后1至64个UTF-16单位纯文本名称，支持emoji | 摸鱼 😀 |
| sort | 可选，32位整数排序，默认0，越小越靠前，可并列 | 0 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emoji-groups' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: emoji-group-create-001' \
  -H 'Content-Type: application/json' \
  -d '{"name":"摸鱼 😀","sort":0}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 分组 | 对象 |
| - id | 分组ID | 1790000000000 |
| - name | 分组名称 | 摸鱼 😀 |
| - sort | 排序 | 0 |
| - type | 自定义分组类型 | 0 |

> 名称不能包含控制字符或HTML标签；本人已有同名组返回409。创建自定义分组不会自动创建全部分组。

### 修改图片表情分组

`PUT /api/v3/me/settings/emoji-groups/{groupId}`

修改本人自定义分组名称与排序。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，分组ID | 1790000000000 |
| name | trim后1至64个UTF-16单位纯文本名称 | 收藏 |
| sort | 可选，32位整数排序，省略设为0 | 1 |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: emoji-group-update-001' \
  -H 'Content-Type: application/json' \
  -d '{"name":"收藏","sort":1}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 分组 | 对象 |
| - id | 分组ID | 1790000000000 |
| - name | 修改后名称 | 收藏 |
| - sort | 修改后排序 | 1 |
| - type | 自定义分组类型 | 0 |

> 全部分组不能修改，返回409；不存在或非本人分组返回404。同键重试返回原结果，不覆盖后来完成的修改。

### 删除图片表情分组

`DELETE /api/v3/me/settings/emoji-groups/{groupId}`

删除本人自定义分组及其分组成员。需要 Bearer Access Token（settings.write）及 Idempotency-Key，无需正文。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，分组ID | 1790000000000 |

请求示例：

```bash
curl -X DELETE 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: emoji-group-delete-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 删除结果 | 对象 |
| - id | 分组ID | 1790000000000 |
| - deleted | 已删除 | true |

> 全部分组不能删除，返回409。不会删除其他分组中的同图片，也不会删除已有分享快照。同键重试返回原成功结果；新键删除不存在的组返回404。
