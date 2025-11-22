# YUHU 接口契约（v1）

说明：所有接口返回统一结构 `{ code, msg, data }`。除标注外默认需要登录；管理员/OP 权限在接口内注明。编号用于对接与跟踪。

## 1. 书籍/分卷/章节
1.1 POST `/yuhu/book`
- 权限：作者/管理员
- 入参：
```json
{ "title":"...","intro":"...","authorProfileId":"...","coverURL":"...","tags":["aliasen"] }
```
- 出参：
```json
{ "id":"bookId" }
```

1.2 GET `/yuhu/books?tag=aliasen&q=...&sort=new|hot|best&page=1&size=20`
- 权限：匿名可访问
- 出参（示例）：
```json
{ "list":[{"id":"...","title":"...","intro":"...","author":"...","score":4.5,"status":"serializing"}],
  "pagination":{"page":1,"size":20,"total":200} }
```

1.3 GET `/yuhu/book/{bookId}`
- 权限：匿名可访问
- 出参：
```json
{ "book":{"id":"...","title":"...","intro":"...","author":"...","score":4.5,"status":"serializing"},
  "volumes":[{"id":"...","index":1,"title":"上卷"}],
  "chapters":[{"id":"...","volumeId":"...","index":1,"title":"第一章","isPaid":false,"status":"normal"}] }
```

1.4 POST `/yuhu/book/{bookId}/volume`
- 权限：作者/管理员
- 入参：
```json
{ "title":"上卷","intro":"..." }
```
- 出参：`{ "id":"volumeId","index":1 }`

1.5 GET `/yuhu/book/{bookId}/volumes`
- 权限：匿名可访问
- 出参：`{ "list":[{"id":"...","index":1,"title":"上卷"}] }`

1.6 POST `/yuhu/book/{bookId}/chapter`
- 权限：作者/管理员
- 规则：自动分配 `index=上一章+1`；初始 `status=draft`
- 入参：
```json
{ "volumeId":"...","title":"第一章","contentMD":"...","isPaid":false }
```
- 出参：`{ "id":"chapterId","status":"draft" }`

1.7 PUT `/yuhu/chapter/{chapterId}/publish`
- 权限：作者/管理员
- 行为：草稿→发布；冻结内容
- 出参：`{ "status":"normal","publishedAt":1730000000000 }`

1.8 PUT `/yuhu/chapter/{chapterId}/freeze`
- 权限：管理员/OP
- 入参：`{ "reason":"越界词汇" }`
- 出参：`{ "status":"frozen" }`

1.9 PUT `/yuhu/chapter/{chapterId}/ban`
- 权限：管理员/OP
- 入参：`{ "reason":"违规" }`
- 出参：`{ "status":"banned" }`

## 2. 草稿与自动保存
2.1 PUT `/yuhu/chapter/{chapterId}/draft`
- 权限：作者/管理员
- 行为：更新草稿字段，发布后不再允许调用
- 入参：
```json
{ "title":"第一章·更新","contentMD":"..." }
```
- 出参：`{ "status":"draft","updated":1730000000000 }`

## 3. 阅读偏好与进度/书签
3.1 GET `/yuhu/prefs`
- 权限：登录；首次访问自动创建 `YuhuUserProfile`
- 出参：`{ "theme":"light","fontSize":18,"pageWidth":800 }`

3.2 POST `/yuhu/prefs`
- 入参：`{ "theme":"night","fontSize":20,"pageWidth":900 }`
- 出参：`{ "updated":1730000000000 }`

3.3 GET `/yuhu/progress/{bookId}`
- 出参：`{ "chapterId":"...","percent":64 }`

3.4 POST `/yuhu/progress/{bookId}`
- 入参：`{ "chapterId":"...","percent":64 }`
- 出参：`{ "updated":1730000000000 }`

3.5 GET `/yuhu/bookmarks?bookId=...`
- 出参：`{ "list":[{"id":"...","chapterId":"...","paragraphId":"p-3","offset":128}] }`

3.6 POST `/yuhu/bookmarks`
- 入参：`{ "bookId":"...","chapterId":"...","paragraphId":"p-3","offset":128 }`
- 出参：`{ "id":"bookmarkId" }`

3.7 DELETE `/yuhu/bookmarks/{id}`
- 出参：`{ "deleted":true }`

## 4. 评论（独立）
4.1 POST `/yuhu/comment`
- 入参：`{ "bookId":"...","chapterId":"...","paragraphId":"p-3","content":"..." }`
- 出参：`{ "id":"commentId","created":1730000000000 }`

4.2 GET `/yuhu/comments?chapterId=...&page=1&size=20`
- 出参：`{ "list":[{"id":"...","profileId":"...","content":"...","likeCnt":3,"dislikeCnt":0}],"pagination":{...} }`

4.3 DELETE `/yuhu/comment/{id}`
- 权限：作者/管理员/OP（按规则）
- 出参：`{ "deleted":true }`

## 5. 标签（一级）
5.1 POST `/yuhu/tag`
- 入参：`{ "name":"奇幻","aliasEN":"fantasy","desc":"..." }`
- 约束：`aliasEN` 全小写唯一，格式 `^[a-z][a-z0-9_-]*$`
- 出参：`{ "id":"tagId" }`

5.2 GET `/yuhu/tags`
- 出参：`{ "list":[{"id":"...","name":"奇幻","aliasEN":"fantasy"}] }`

5.3 POST `/yuhu/book/{bookId}/tags`
- 入参：`{ "tags":["fantasy","romance"] }`
- 出参：`{ "updated":true }`

## 6. 订阅与聚合通知
6.1 POST `/yuhu/subscribe/{bookId}`
- 出参：`{ "subscribed":true }`

6.2 DELETE `/yuhu/subscribe/{bookId}`
- 出参：`{ "subscribed":false }`

6.3 GET `/yuhu/subscriptions`
- 出参：`{ "list":[{"bookId":"...","title":"...","created":1730000000000}] }`

## 7. 投票与积分
7.1 POST `/yuhu/vote`
- 入参：
```json
{ "targetType":"book","targetId":"...","type":"monthly","value":1 }
```
- 规则：
  - `monthly|recommend`: `value=1`，扣积分（推荐票默认 512/张；月票免费/积分兑换）
  - `tip`: `value ∈ {32,64,128,512,1024,2048}` 或自定义，`32≤value≤10240`
  - `thumbUp|thumbDown`: `value=1`（不扣积分）
  - `rating`: `value ∈ [1..5]`，同用户同书仅保留最新一次评分
- 出参：`{ "accepted":true,"pointsCost":512 }`

7.2 GET `/yuhu/vote/stats?bookId=...`
- 出参：
```json
{ "monthly":123,"recommend":456,"tip":{"sum":10240,"count":30},
  "thumbUp":320,"thumbDown":12,"avgRating":4.6,
  "score":92.4 }
```

## 8. 搜索（独立域 yuhu）
8.1 GET `/yuhu/search?q=...&scope=book|chapter|author|tag&page=1&size=20`
- 出参：`{ "list":[...],"pagination":{...} }`

8.2 POST `/yuhu/search/index/book`
- 权限：内部/管理员；发布/更新时增量索引
- 入参：`{ "id":"bookId" }`

8.3 POST `/yuhu/search/index/chapter`
- 权限：内部/管理员
- 入参：`{ "id":"chapterId" }`

## 9. 用户主页显示切换
9.1 POST `/yuhu/profile/display`
- 入参：`{ "displayOverrideEnabled":true }`
- 出参：`{ "updated":true }`

## 10. 错误码与限流
- 错误码：
  - `400` 参数非法（别名/索引/范围）
  - `401` 未登录
  - `403` 权限不足（非作者/管理员）
  - `409` 冲突（章节索引跳跃、别名重复）
  - `429` 触发限流
  - `500` 服务端错误
- 限流：
  - 同用户同目标同类型最小间隔：默认 `60s`
  - 全局滑动窗口：默认 `10min`、每类型最多 `X` 次

## 11. 错误码枚举
- 10000 参数非法
- 10001 未登录
- 10002 权限不足
- 10003 资源不存在
- 10004 冲突（索引跳跃、别名重复）
- 10005 触发限流
- 10006 积分不足
- 10007 非法票面（打赏票超范围）
- 10008 非法评分（范围之外）
- 10009 状态非法（非草稿不允许发布/草稿更新）
- 10010 内部错误

## 12. 权限矩阵
- 角色
  - `user`：普通登录用户
  - `author`：作者（拥有书籍的创建与章节发布权限）
  - `admin`：管理员
  - `op`：运营（与管理员共享部分内容管控权限）
- 接口权限概述
  - 书籍/分卷/章节
    - 创建书籍/分卷/章节草稿：`author|admin`
    - 发布章节：`author|admin`
    - 冻结/封禁章节：`admin|op`
    - 查询书籍/目录：`anonymous`
  - 草稿与自动保存：`author|admin`
  - 阅读偏好/进度/书签：`user|author|admin|op`
  - 评论新增/删除：新增 `user|author|admin|op`，删除 `admin|op` 或作者按规则
  - 标签维护：新增/绑定 `admin|op`
  - 订阅：`user|author|admin|op`
  - 投票与评分：`user|author|admin|op`（按限流与积分约束）
  - 搜索与索引：查询 `anonymous`，索引 `admin|internal`
  - 用户主页显示切换：`user|author|admin|op`

