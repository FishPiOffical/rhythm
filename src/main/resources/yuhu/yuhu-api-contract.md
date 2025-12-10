# YUHU 接口文档（v1）

说明
- 统一返回结构：`{ code, msg, data }`
- 鉴权：除标注外默认需要登录；`admin|op` 标注为管理员/运营权限
- 错误码：见“错误码”章节

## 书籍/分卷/章节

POST `/yuhu/book`
- 接口功能：创建书籍
- 权限：`author|admin`
- 入参：`{ title, intro, authorProfileId, coverURL, tags? }`
- 响应：`{ id }`
- 异常：`400` 参数非法、`401` 未登录、`403` 权限不足

GET `/yuhu/books?tag=aliasen&q=...&sort=new|hot|best&page=1&size=20`
- 接口功能：查询书籍列表
- 权限：匿名
- 入参：查询条件与分页
- 响应：`{ list: [...], pagination: { page,size,total } }`

GET `/yuhu/book/{bookId}`
- 接口功能：获取书籍详情（仅返回状态为 `normal` 的章节）
- 权限：匿名
- 响应：`{ book, volumes, chapters }`

GET `/yuhu/author/{profileId}`
- 接口功能：根据作者档案 `profileId` 获取作者信息
- 权限：匿名
- 响应：`{ profileId, nickname, intro, avatarURL, role, created, updated, creationDays, works, wordCount, levelCode, levelActive, badges:[] }`
- 异常：`10003` 资源不存在（当返回空对象时表示不存在）

GET `/yuhu/author/byBook/{bookId}`
- 接口功能：根据书籍获取作者信息（内部读取书籍的 `authorProfileId`）
- 权限：匿名
- 响应：`{ profileId, nickname, intro, avatarURL, role, created, updated, creationDays, works, wordCount, levelCode, levelActive, badges:[] }`
- 异常：`10003` 资源不存在（当返回空对象时表示不存在）

GET `/yuhu/author/{profileId}/stats`
- 接口功能：作者聚合统计信息
- 权限：匿名
- 响应：`{ works, wordCount, chaptersPublished, subscribers, comments, bookmarks, monthly, recommend, tip:{sum,count}, thumbUp, thumbDown, avgRating, ratingsCount, score }`
- 说明：
- `works` 作品数
- `wordCount` 累计字数（书籍统计）
- `chaptersPublished` 已发布章节数（状态 `normal`）
- `subscribers` 总订阅数（作者全部书籍）
- `comments` 总评论数（作者全部书籍）
- `bookmarks` 总书签数（作者全部书籍）
- `monthly|recommend|tip|thumbUp|thumbDown|avgRating|ratingsCount` 投票统计聚合
- `score` 简单综合评分（同书籍统计规则）

GET `/yuhu/author/{profileId}/me`
- 接口功能：作者本人查看信息（私域）
- 权限：登录且本人
- 响应：在公域字段基础上附加 `{ monthly, recommend, tip:{sum,count}, thumbUp, thumbDown, avgRating, ratingsCount, score }`

GET `/yuhu/author/{profileId}/books?page=1&size=20`
- 接口功能：作者作品列表分页
- 权限：匿名
- 响应：`[ { id, title, intro, coverURL, wordCount, status, created, updated }, ... ]`

POST `/yuhu/book/{bookId}/volume`
- 接口功能：新增分卷
- 权限：`author|admin`
- 入参：`{ title, intro }`
- 响应：`{ id, index }`

GET `/yuhu/book/{bookId}/volumes`
- 接口功能：查询分卷列表
- 权限：匿名
- 响应：`{ list: [...] }`

POST `/yuhu/book/{bookId}/chapter`
- 接口功能：新增章节草稿，自动分配顺序
- 权限：`author|admin`
- 入参：`{ volumeId, title, contentMD, isPaid }`
- 响应：`{ id, status:"draft" }`

PUT `/yuhu/chapter/{chapterId}/publish`
- 接口功能：提交章节审核（`draft -> pending`）
- 权限：`author|admin`
- 入参：无
- 响应：`{ status:"pending" }`
- 异常：`10009` 状态非法（非 `draft`）

PUT `/yuhu/chapter/{chapterId}/approve`
- 接口功能：审核通过并发布（生成 HTML、计字数、写发布时间、更新书籍）
- 权限：`admin|op`
- 入参：`{ note? }`
- 响应：`{ status:"normal", publishedAt }`
- 异常：`10009` 状态非法（非 `pending`）

PUT `/yuhu/chapter/{chapterId}/reject`
- 接口功能：审核驳回（回到草稿，记录审批信息）
- 权限：`admin|op`
- 入参：`{ note? }`
- 响应：`{ status:"draft", reason }`
- 异常：`10009` 状态非法（非 `pending`）

PUT `/yuhu/chapter/{chapterId}/freeze`
- 接口功能：冻结章节
- 权限：`admin|op`
- 入参：`{ reason }`
- 响应：`{ status:"frozen" }`

PUT `/yuhu/chapter/{chapterId}/ban`
- 接口功能：封禁章节
- 权限：`admin|op`
- 入参：`{ reason }`
- 响应：`{ status:"banned" }`

PUT `/yuhu/chapter/{chapterId}/draft`
- 接口功能：更新草稿内容（发布后不可用）
- 权限：`author|admin`
- 入参：`{ title?, contentMD? }`
- 响应：`{ status:"draft", updated }`
- 异常：`10009` 状态非法（非 `draft`）

## 偏好、进度与书签

GET `/yuhu/prefs`（登录） → `{ theme, fontSize, pageWidth }`
POST `/yuhu/prefs`（登录） 入参：`{ theme?, fontSize?, pageWidth? }` → `{ updated }`
GET `/yuhu/progress/{bookId}`（登录） → `{ chapterId, percent }`
POST `/yuhu/progress/{bookId}`（登录） 入参：`{ chapterId, percent }` → `{ updated }`
GET `/yuhu/bookmarks?bookId=...`（登录） → `{ list:[...] }`
POST `/yuhu/bookmarks`（登录） 入参：`{ bookId, chapterId, paragraphId, offset }` → `{ id }`
DELETE `/yuhu/bookmarks/{id}`（登录） → `{ deleted:true }`

## 评论

POST `/yuhu/comment` 入参：`{ bookId, chapterId, paragraphId?, content }` → `{ id, created }`
GET `/yuhu/comments?chapterId=...&page=1&size=20` → `{ list:[...], pagination }`
DELETE `/yuhu/comment/{id}` 权限：`admin|op` 或作者规则 → `{ deleted:true }`

## 标签

POST `/yuhu/tag` 入参：`{ name, aliasEN, desc }` → `{ id }`
GET `/yuhu/tags` → `{ list:[...] }`
POST `/yuhu/book/{bookId}/tags` 权限：`admin|op` 入参：`{ tags:[aliasEN...] }` → `{ updated:true }`

## 订阅与通知

POST `/yuhu/subscribe/{bookId}` → `{ subscribed:true }`
DELETE `/yuhu/subscribe/{bookId}` → `{ subscribed:false }`
GET `/yuhu/subscriptions` → `{ list:[...] }`

## 投票与积分

POST `/yuhu/vote` 入参：`{ targetType, targetId, type, value }` → `{ accepted, pointsCost, oId }`
GET `/yuhu/vote/stats?bookId=...` → `{ monthly, recommend, tip:{sum,count}, thumbUp, thumbDown, avgRating, score }`

## 搜索

GET `/yuhu/search?q=...&scope=book|chapter|author|tag&page=1&size=20` → `{ list, pagination }`
POST `/yuhu/search/index/book` 权限：`admin|internal` 入参：`{ id }`
POST `/yuhu/search/index/chapter` 权限：`admin|internal` 入参：`{ id }`

## 用户主页显示切换

POST `/yuhu/profile/display` 入参：`{ displayOverrideEnabled }` → `{ updated:true }`

## 错误码

- `400` 参数非法
- `401` 未登录
- `403` 权限不足
- `409` 冲突（索引跳跃、别名重复）
- `429` 触发限流
- `500` 服务端错误

错误码枚举
- `10000` 参数非法
- `10001` 未登录
- `10002` 权限不足
- `10003` 资源不存在
- `10004` 冲突
- `10005` 触发限流
- `10006` 积分不足
- `10007` 非法票面
- `10008` 非法评分
- `10009` 状态非法
- `10010` 内部错误

权限矩阵（概要）
- 书籍/分卷/章节：创建/草稿 `author|admin`；提交审核 `author|admin`；审核通过/驳回/冻结/封禁 `admin|op`；查询 `anonymous`
- 偏好/进度/书签/评论/订阅/投票：登录
- 标签维护与索引：`admin|op`（索引 `admin|internal`）
