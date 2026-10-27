# v3 作者付费置顶（2026-10-24）

## 已确认与当前实现

用户确认：仅本人当前有效且可读文章，每新幂等键沿现有费用刷新置顶时间，不增加数量上限，扣费/时间/回执同提交，管理员永久置顶不能付费覆盖，旧入口保持。另已确认type=recent各排序都先排列仍有效的置顶，永久最先；可读摘要/详情增加stick(active/permanent/expiresAt)，永久到期为null。

新增POST /api/v3/articles/{articleId}/stick，需要content.write+activity.write和幂等键，空JSON。当前费用100积分（复用既有Pointtransfer常量/配置），沿原置顶期限。成功返回id/stuckAt/paidPoints。非本人、无效、锁定或不可读404，余额不足/永久置顶409；同键旧结果不再扣款、不覆盖后续变化或重建已删文章。

账户对在事务前，严格根中当前用户→本人回执→文章当前锁读取；文章时间、积分主账及回执同保存点/根提交。使用原19类积分支出、原userUsedPoint规则。内部主账ID不回给HTTP，提交后按本人/目标/source场景/请求验证主账并幂等修索引。缓存边界结束后刷新，不把未提交文章对象发布到缓存。

共享过期任务发现旧快照可能抹掉新续费：现在仅对已过期候选当前锁定读取，时间仍等于扫描值才取消；管理员永久值不取消。原期限和正常过期结果不变。旧付费入口整体仍是旧写链，不能声称新旧收费全部原子或旧缓存策略整体重做。

## 实际文件

- src/main/java/org/b3log/symphony/service/ApiV3ArticleStickService.java：费用/时间/回执与安全投影、提交后索引修复。
- processor/ApiV3ArticleStickProcessor.java、processor/ApiV3Processor.java：双scope写入口与总接线。
- repository/ArticleRepository.java、service/ArticleMgmtService.java：过期取消前锁当前文章并比对扫描时间，保留完整当前行供原缓存路径使用。
- util/ApiV3ArticleStickState.java、service/ApiV3ContentDtoService.java：置顶状态白名单；不把Long.MAX_VALUE哨兵发给客户端，永久expiresAt=null，普通毫秒值保持JS安全整数；损坏值503。
- service/ApiV3DiscoveryQueryService.java：type=recent在既有筛选后按永久、有效置顶、原排序排列；过期置顶回到普通序。数量与分页仍先做可见性过滤，其他type原排序不改。
- src/test/java/org/b3log/symphony/service/ApiV3ArticleStickTest.java、ApiV3ArticleStickStateTest.java、tools/api-v3-article-stick-test.mjs：专项。
- tools/api-v3-feed-query-test.mjs、tools/fixtures/v3-feed-query.java.txt：按批准的置顶排序更新实际SQL验证。
- API.md、UPDATE_LOG.md、AGENTS.md、docs/api-v3-progress.md、development-plan与总路由清单、本文：公开契约与内部交接。

上列processor/repository/service/util未写全前缀的路径均相对于src/main/java/org/b3log/symphony。无新增Repository、表、迁移或FTL，无classic皮肤资源改动。

独立客户端 /Users/ling/Documents/fishpi/website_v3 修改src/api/fishpi.ts的状态类型、src/api/v3-content.ts的白名单适配、src/components/articles/ArticleFeed.vue的两种列表徽标与src/views/ArticleView.vue的详情状态，以及tools/v3-content-test.mjs与迁移文档；不把新状态伪造成旧时间戳，不改CSS/min.js。

## 实际验证与边界

根node tools/api-v3-article-stick-test.mjs最终8组退出0，/tmp/rhythm-v3-article-stick-final.log：真实完整付费服务、积分primitive、幂等、严格JDBC、保存点、处理器/注册链与HTTP中间件参与。SQLite/仓储读写、文章缓存与最终响应为明确夹具；过期Repository两方法从实际源码移植。覆盖新键刷新、旧键重放不覆盖、跨目标冲突、文章/余额/主账/回执/commit故障回滚、当前账户/所有权/锁定/永久、严格body/scopes/key、丢ACK单次扣费/索引修复、整数溢出、过期候选保留续费/永久、真实摘要投影和元数据边界。DTO正文渲染/头像/用户查询为适配，不是完整生产渲染验收。

node tools/api-v3-feed-query-test.mjs四组退出0，/tmp/rhythm-v3-feed-stick.log；实际完整articles查询方法移植运行SQLite，覆盖各recent排序永久/有效优先、过期恢复原序，qna/perfect与过滤先于分页保持。未验证MySQL并发锁或完整IoC，不以SQLite代替InnoDB结论。

客户端内容专项通过，corepack yarn run build完成类型、客户端及SSR构建。独立本地只读夹具IAB实际宽视口验证recent列表→详情出现“永久置顶”，截图/tmp/rhythm-v3-stick-proof.png；不发送真实置顶/扣款。临时浏览器页和本地两服务已关闭。实际Rhythm服务、真实账号/付费/过期调度、真实移动设备仍需IDEA重编译/重启后验收，未运行Maven或操作业务数据库。

## 安全与反漏扫

新POST仅Bearer双scope，不接Cookie/query/body apiKey替代身份；所有权与当前有效账号/文章在写事务内检查。旧入口字段和认证模式保持，没有给apiKey写接口直接添加页面CSRF。JSON/路径/幂等键有界，标识参数绑定，无HTML/用户URL/远程抓取或上传下载路径；DTO不返回完整文章、联系方式、主账内部字段。共享写预算及幂等限制重复写，实际多实例负载仍待测。

原公开GET仍optionalContent+AnonymousViewCheckMidware，未增加anonymous.viewSkips。Before黑名单/test、匿名首访/周期及validateCaptcha解封未更改；运行未验。客户端构建定向检查、实际CDN版本/地图、sourceMappingURL、目录枚举与旧上传下载全站基线应单独验收，不能凭本轮无新增公网资源略过。

## 完整人工链

1. 独立开发库执行已有v3基础/积分索引迁移，准备作者余额充足、余额99、停用账号、他人文章、锁定/无效文章及管理员永久置顶。通过IDEA重编译/重启并确认后再联调。
2. 作者从支持v3的客户端对本人文章POST stick，空body、双scope、原key；期望200/id/stuckAt/paidPoints=100，积分少100、支出增加100、时间/主账/回执同提交。GET摘要/详情stick有效；recent各排序先永久再有效置顶，其后按原排序，隐藏/不可读内容仍不出现。
3. 同键重试不收费；新键再付100并刷新。同键换目标409；在后来续费、取消、过期或删除后重放旧键，只返回旧结果，不恢复时间/文章。并发两键及同键另做真实MySQL验证，不能用夹具顺序执行推断通过。
4. 余额不足/永久409，非本人/锁定/无效404；缺Bearer401、缺任一scope403，缺key/伪造价格作者时间/非空body422。注入文章、余额、主账、回执或提交失败，所有状态不变；提交丢ACK用原键恢复一次。
5. 过期任务先读旧时间，再续费或管理员永久置顶，任务不应清掉新值；不改变当前值的正常过期则取消。验证到期边界、重启后状态和普通未置顶/过期/permanent的JSON类型，无Long.MAX_VALUE精度丢失。
6. PC/mobile经典页面原付费流程及独立Vue宽/窄列表、详情回归；实际全站黑名单/test/验证码、匿名GET和Cookie/apiKey旧入口、CSRF/XSS/批量请求/静态/CDN/目录/上传下载仍须实测。

API.md需同步网站API文章https://fishpi.cn/article/1636516552191。音乐/天气和独立认证审核等仍待开发，整体v3目标未完成。
