# v3 客户端读取与文章分类补齐（2026-10-24）

## 文件与行为

客户端已找到：/Users/ling/Documents/fishpi/website_v3，项目列表返回的实际Git项目。已读取其AGENTS/README及六个既有未提交文件diff，保留原有改动；客户端详细文件、人工链和边界见该仓docs/V3_MIGRATION.md。

后端修改src/main/java/org/b3log/symphony/processor/ApiV3ContentProcessor.java及service/ApiV3DiscoveryQueryService.java：现有GET /api/v3/articles增加type=qna/perfect、sort=month-comments。qna仅问答，perfect允许优选长篇；公共基础可见性过滤保持。月内热议沿旧getRecentArticles模式1，门槛为服务器DateUtils.addMonths(now,-1)，置顶优先/评论数/ID倒序；hot仍reddit热度排序，其他类型排序不改。字段与路由数量不变，无写库/Repository/SQL迁移。

API.md、UPDATE_LOG.md同步公开筛选契约；AGENTS、progress/development计划及路由清单同步真实客户端位置与进展。

## 证据与限制

新增tools/api-v3-feed-query-test.mjs及tools/fixtures/v3-feed-query.java.txt，根Java25隔离测试4组退出0，日志/tmp/rhythm-v3-feed-query.log。工具从实际Discovery源码完整移植articles方法及Selection记录执行SQLite查询，不另写同构业务SQL。测试覆盖qna分页/总数及匿名限制、隐藏/讨论/停用作者/沙盒过滤，perfect包含长篇，月内排序/置顶及过滤先于分页，原hot旧文章保持。Repository、DTO和分页包装是明确适配；不是完整服务类/IoC/HTTP/MySQL执行，不能冒充真实后端已重启。

客户端认证12组、缓存3组、内容适配专项通过，corepack yarn run build完成类型检查与客户端/SSR构建。IAB仅对独立本地夹具验证列表/详情、搜索空/错误、目录ID链路；真实Rhythm、宽屏/设备、真实认证与Socket仍待验收。临时服务已关，未Maven、未业务库操作或生产写入。

## 源码安全及反漏扫

GET仍经过optionalContent及AnonymousViewCheckMidware，未增加viewSkips例外；Before黑名单/test与Captcha解封未改。新增枚举不接SQL片段，月门槛由服务端生成绑定，基础权限先于count/page。响应仍原白名单，无新增身份、联系方式、原始审核信息或URL抓取，不引入SSRF。只读端点没有Cookie写入CSRF能力，客户端Bearer不会发往旧/外部URL；批量读取仍受既有入口限制，实际负载未测。

未改classic PC/mobile模板与资源；本轮客户端为独立Vue响应式站。生成dist定向未发现sourceMappingURL及已查Vue版本头；全站静态/CDN指纹、目录枚举、上传下载路径、匿名计次/黑名单/验证码运行仍未验证。

## 人工步骤

先IDEA重新编译/重启，独立开发库准备普通/问答/优选长篇、月内/更早、置顶/非置顶、匿名不可读及隐藏目标。由客户端列表进入详情、分类、搜索和目录，核对正确记录/总数/分页，404/无权限/错误不使用旧接口或mock回退。再用两个开发账号测试登录、刷新、登出与慢响应竞争，旧私有正文不回写；补宽屏/移动设备及全站反漏扫回归。完整操作见客户端V3_MIGRATION文档。

公开API.md需同步网站API文章https://fishpi.cn/article/1636516552191；整体v3仍未完成。
