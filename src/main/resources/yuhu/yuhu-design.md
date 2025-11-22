# YUHU 阅读后端设计说明

## 目标与边界
- 仅提供后端接口与配置说明，不产出前端模板或脚本。
- 独立域：书籍/分卷/章节/标签/评论/投票/打赏/订阅/统计全部独立于现有文章系统。
- 用户关联：仅关联现有用户 `oId`，登录与管理员/OP 权限复用现有中间件。
- 发布后不可修改：章节发布后冻结内容；仅管理员/OP允许越界词修订与状态冻结/封禁。
- 章节顺序：一本书唯一作者；章节必须按 `index` 连续递增，不允许跳章；支持分卷组织。
- 初期不启用服务端缓存，上线观察后按需逐步加入。

## 关键依赖与复用
- 路由注册：`src/main/java/org/b3log/symphony/processor/Router.java:77-170`
- 目录/ToC 渲染参考：`src/main/java/org/b3log/symphony/service/ArticleQueryService.java:1752-1833`、`1832`
- 登录与 CSRF 中间件参考：`src/main/java/org/b3log/symphony/processor/SettingsProcessor.java:219`
- 事件/定时任务：`src/main/java/org/b3log/symphony/Server.java:200-223`
- 用户通知通道：`src/main/java/org/b3log/symphony/processor/channel/UserChannel.java`

## 数据模型（建议）
- YuhuUserProfile：`id`、`linkedUserId`、`role(reader|author|both)`、`nickname`、`intro`、`avatarURL`、`displayOverrideEnabled`、`created`、`updated`
- YuhuBook：`id`、`title`、`intro`、`authorProfileId`、`status(serializing|finished|halted)`、`coverURL`、`wordCount`、`latestChapterId`、`score`、`created`、`updated`
- YuhuVolume：`id`、`bookId`、`index`、`title`、`intro`、`created`
- YuhuChapter：`id`、`bookId`、`volumeId`、`index`、`title`、`contentMD`、`contentHTML`、`wordCount`、`publishedAt`、`isPaid`、`status(draft|normal|frozen|banned)`、`toc`、`paragraphAnchors`
- YuhuTag：`id`、`name`、`aliasEN`（唯一、全小写，字符集 `a-z0-9` 与 `-`、`_`）`desc`
- YuhuBookmark：`id`、`profileId`、`bookId`、`chapterId`、`paragraphId`、`offset`、`created`
- YuhuReadingProgress：`id`、`profileId`、`bookId`、`chapterId`、`percent`、`updated`（每本仅保存最后一次进度）
- YuhuComment：`id`、`profileId`、`bookId`、`chapterId`、`paragraphId?`、`content`、`created`、`status(normal|removed|banned)`、`likeCnt`、`dislikeCnt`
- YuhuVote：`id`、`profileId`、`targetType(book|chapter)`、`targetId`、`type(monthly|recommend|tip|thumbUp|thumbDown|rating)`、`value`、`pointsCost`、`created`
- YuhuSubscription：`id`、`profileId`、`bookId`、`created`
- YuhuStats：书籍/章节聚合阅读量/评论数/打赏积分/综合评分等
- YuhuLevelConfig：`id`、`type(reading|tip)`、`levelSeq`、`name`、`xpRequired`、`iconURL`

## 发布与草稿策略
- 章节草稿记录与正式记录统一：创建 `status=draft` 的章节，反复保存草稿；发布时置 `status=normal` 并冻结内容。
- 优点：ID/URL 稳定、便于订阅/搜索/书签等引用。

## 投票与积分等价
- 推荐票：默认 `512` 积分/张（可配置）。
- 月票：每用户每月免费 `1` 张，`membership V4` 额外 `+4` 张；其余积分兑换（可配置）。
- 打赏票：面额 `{32,64,128,512,1024,2048}` 或自定义，范围 `[32..10240]`。
- 点赞/点踩：不扣积分；评分 `1..5`，同用户同书仅保留最新一次。
- 统一记账：投票成功写 `pointsCost`，后续收益与分成可据此统计。

## 限流与反刷
- 同用户同目标同类型最小间隔：默认 `60s`（可配置）。
- 全局滑动窗口：默认 `10min` 允许最多 `X` 次（类型可区分）。
- 采集 `IP/UA/DeviceId`，异常行为拒绝；参考 `Visit` 模型：`src/main/java/org/b3log/symphony/model/Visit.java:41-83`。

## 订阅与聚合通知
- 新章节发布触发 `YuhuEvents.CHAPTER_PUBLISHED` 事件，进入用户聚合队列。
- 聚合窗：默认 `10min`，批大小 `<=50`；经 `UserChannel` 与站内通知推送。
- 定时任务注册：参考 `src/main/java/org/b3log/symphony/Server.java:222-223`。

## 搜索索引（域 yuhu）
- 文档结构
  - book：`{ id,title,intro,tags,authorName,status,score,created,updated }`
  - chapter：`{ id,bookId,volumeId,index,title,contentHTML,toc,isPaid,status,publishedAt }`
- 接口：发布/更新时增量写索引，查询走 `scope=book|chapter|author|tag`。

## 配置建议
- `yuhu.recommend.points_per_ticket=512`
- `yuhu.monthly.free_tickets_per_month=1`
- `yuhu.monthly.vip_bonus_tickets.V4=4`
- `yuhu.tip.allowed_denominations=32,64,128,512,1024,2048`
- `yuhu.tip.custom.min=32`
- `yuhu.tip.custom.max=10240`
- `yuhu.rate.limit.vote.same_target.min_interval_ms=60000`
- `yuhu.rate.limit.vote.window.minutes=10`
- `yuhu.rate.limit.vote.window.max_actions=30`
- `yuhu.notify.aggregate.window.minutes=10`
- `yuhu.notify.max_items_per_batch=50`
- `yuhu.search.index_domain=yuhu`
- `yuhu.prefs.default.theme=light`
- `yuhu.prefs.default.fontSize=18`
- `yuhu.prefs.default.pageWidth=800`
- `yuhu.tag.alias.regex=^[a-z][a-z0-9_-]*$`

## 错误码约定
- 400 参数非法（别名/索引/范围）
- 401 未登录
- 403 权限不足（非作者/管理员操作）
- 409 冲突（章节索引跳跃、别名重复）
- 429 触发限流
- 500 服务端错误

