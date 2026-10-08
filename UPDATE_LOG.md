# 适用

本文主要适用于第三方开发者，更新日志会着重偏向API的更新，以便第三方开发者能够方便、快速的更新客户端。

# 261008-2

## 批次二：排行榜、个人主页、关注动态、同城、大事记与统计

**排行榜接口结构调整（破坏性变更）**：所有 `/api/top/*` 接口的 `data` 由"数组"统一调整为对象 `{list, totalData, type}`，请改用 `data.list` 读取榜单列表。同时全部 8 类榜单（签到/在线/财富/消费/优选/邀请/捐赠/游戏）改为匿名可读，无需 apiKey。

**帖子详情增强**：

- `GET /api/article/{id}` 确认匿名可读；仅当作者关闭"匿名查看"时匿名请求返回 401，登录/apiKey 不受影响
- 返回新增 `relevantArticles`（相关帖子）、`previousArticle` / `nextArticle`（上一篇/下一篇，轻量字段）
- 评论排序新增 query 参数 `m`：`0` 按时间正序（默认）、`1` 按时间倒序；不传时沿用登录用户偏好

**新增接口**：

- 个人主页：`GET /api/user/{userName}/comments` 评论列表、`/long` 长文章、`/watching` 关注动态聚合、`/following/articles` 关注用户的帖子、`/following/tags` 关注的标签、`/following/users` 关注的用户、`/followers` 粉丝；以上匿名可读，目标用户隐私设置关闭对应列表时仅本人或管理员可见（返回 `code != 0`）。`/comments/anonymous` 匿名评论、`/articles/anonymous` 匿名帖子、`/points` 积分流水**仅本人**可调（需要 apiKey）
- 关注动态：`GET /api/watch/tags/articles`、`GET /api/watch/users/articles`、`GET /api/watch/breezemoons`（需要 apiKey）；`/api/watch/breezemoons` 无关注或关注无内容时返回空列表，不再回退为全站清风明月
- 专栏管理：`POST /api/columns/{columnId}/rename` 重命名、`POST /api/columns/order` 排序、`POST /api/columns/{columnId}/remove` 删除（有章节的专栏需先移出章节）
- 清风明月：`GET /api/breezemoons` 与 `GET /api/watch/breezemoons` 每条新增 `breezemoonContentRaw` 原始 Markdown 字段
- 同城：`GET /api/city/{cityName}` 同城用户分页（匿名，仅含公开位置的用户）
- 大事记与统计：`GET /api/milestones`、`GET /api/statistic`（匿名）

约定：

- 需登录接口均通过 `apiKey` 鉴权；分页接口参数 `p`（默认 1）/`size`（默认 20，上限 50）
- 列表统一返回 `data.xxx` + `data.pagination = {paginationPageCount, paginationRecordCount}`


# 261008

## 批次一：第三方客户端只读/轻操作 API

为方便第三方 SPA 客户端对接，新增一批 `/api` 前缀接口，并对存量 `/api` 接口做纯增量字段补充。所有现有接口形状不变，FTL 页面对应链路不受影响。

新增接口：

- 鉴权：`GET /api/reset-pwd/meta`，忘记密码时用重置码换取用户 Id（匿名）
- 背包：`GET /api/user/bag` 读取背包；`GET /api/bag/1dayCheckin`、`GET /api/bag/2dayCheckin`、`GET /api/bag/patchCheckin` 使用免签/补签卡；`POST /api/bag/nameCard` 使用改名卡
- 签到：`GET /api/checkin/status`，一次返回今日是否签到、活跃度、自动签到门槛与连续签到天数
- 专栏：`GET /api/columns/latest`、`GET /api/columns/hot` 首页货架；`GET /api/columns/mine` 我的专栏；`GET /api/columns/{columnId}` 专栏详情；`GET /api/columns/{columnId}/articles` 章节分页
- 鱼游：`GET /api/fish-games` 已审核鱼游分页列表
- 帖子：`POST /api/article/stick` 作者置顶自己的帖子（按次扣积分）

字段补充：

- `GET /api/user` 返回新增 `userGuideStep` 字段（新人引导步骤）

约定：

- 需登录接口均通过 `apiKey` 鉴权；列表接口设有 `size` 上限
- 写接口（道具使用、置顶）均需要 apiKey

# 260927

## 图床切换韵图 RhyPic：旧上传接口 `POST /upload` 即将停用（重要）

站点图床已切换为新的 **RhyPic（韵图）** 图床，旧上传接口 `POST /upload` 将在不久后**彻底停用**。请第三方客户端尽快阅读最新对接文档，按「获取上传票据 → 直传韵图」两步流程完成适配：

- 对接文档：https://fishpi.cn/article/1790518451759

新流程的响应结构（`data.errFiles` / `data.succMap`）与旧接口兼容，原有解析逻辑可直接沿用，主要变更为鉴权方式（一次性票据请求头）、上传地址（韵图图床）与文件字段名（`file`）。

# 260920

## 新版勋章接口

新增 `POST /api/medal/admin/holds`，支持查询单个用户或最多 100 个用户是否持有指定勋章。接口返回当前持有状态、过期状态、到期时间和勋章数据。

新增 `POST /api/medal/admin/grant-batch`，支持向单个用户或最多 100 个用户发放指定勋章。批量写入使用同一事务，任一用户校验或写入失败时整批失败；已有勋章会更新到期时间和勋章数据。

原有单用户接口保持兼容。

# 260908

## 鱼游投稿与管理

活动页的固定鱼游列表调整为“摸鱼鱼游”内容库，支持展示已审核鱼游、点赞、点踩和评论。

用户可投稿网站，投稿内容由管理员人工审核后公开。投稿作者可以提交资料修改申请，审核通过后更新公开内容。

新增鱼游管理页，管理员可手动添加、审核、停用、编辑、导入和导出鱼游数据。旧活动页的固定鱼游清单已整理为导入文件。

第三方接口已补充到 API 文档，包括投稿、修改申请、投票、评论和管理员管理接口。

修正鱼游接口的 API key 认证示例；写入接口增加 CSRF 校验和用户频率限制。

## 发帖与优质奖励

普通发帖页改为通过“这是好帖”复选框申请优质奖励，不再在发布时弹出奖励选择窗口。获得奖励的帖子会显示“好帖”标识。

管理员、OP 和超级会员新增免审发帖与免审保存入口。该入口会跳过敏感词过滤和内容审核，服务端会按用户分组校验权限，未授权请求会被拒绝。

发帖和更新帖子接口新增可选参数 `articleBypassCensor`，接口文档已同步更新。

## 页面调整

PC 与移动端同步调整普通发帖和长文发布页的操作区布局。

专栏书城改为“最新专栏”和“热门专栏”分区展示，并调整专栏卡片样式。

职业设置页调整分区间距与预设选项样式。

# 20260519

* 新增“复读机转录站”：首页展示段子、星期四、鱼科普内容，支持随机切换、复制、点赞、登录后上传。
* 新增专栏封面能力：专栏支持自定义封面，长篇发布页可带封面，作者可在 **/column/manage** 管理封面

# 260515

## 评论变革！

评论增加新的接口，按层级模式展示评论，**原接口仍兼容保留，API文档已更新**

![PixPin_2026-05-15_17-29-45.png](https://file.fishpi.cn/2026/05/PixPin20260515172945-d1127e2e.png)

## 发帖/发长文章新增草稿功能

![PixPin_2026-05-15_17-30-27.png](https://file.fishpi.cn/2026/05/PixPin20260515173027-50305ec3.png)

同样已更新到API文档，注意普通帖子和长文章虽然API相同，但是参数（type）不同，开发时请仔细阅读。

# 260429

帖子历史版本 diff 功能重构，历史记录列表与具体版本正文改为按需读取，避免长帖多次更新后一次性返回全部历史正文。

新增两个帖子历史版本接口：

- `GET /article/{id}/revisions/list`
- `GET /article/{id}/revisions/{revisionId}`

原 `GET /article/{id}/revisions` 接口已移除，第三方客户端需改用新的列表接口和详情接口。

帖子历史版本弹窗同步优化，支持选择任意两个历史版本进行标题和正文对比，也可以只显示差异部分，复制内容时不再包含 `+` / `-` 标记。

此功能已适配 API，可参阅 https://fishpi.cn/article/1636516552191 的 【模块：帖子历史版本】章节

补充评论历史版本 API 文档：

- `GET /comment/{id}/revisions`

该接口用于获取指定评论的历史版本列表，返回评论历史正文。评论历史接口为既有接口，本次未拆分为列表接口和详情接口。

# 260422

帖子和表情新增emoji贴纸选项，该贴纸不消耗积分，可反复修改要贴的emoji贴纸，emoji为预置不可自定义。

此功能已适配API，可参阅 https://fishpi.cn/article/1636516552191 的 【模块：贴emoji】章节

![PixPin_2026-04-29_10-52-10.png](https://file.fishpi.cn/2026/04/PixPin20260429105210-725063be.png)
