# v3 标签关注、文章收藏与订阅（2026-10-26）

## 结果与范围

新增三个单项 GET：`/api/v3/me/follows/tags/{targetId}`、`/api/v3/me/collections/{targetId}`、`/api/v3/me/subscriptions/{targetId}`，均 content.read，只返本人 targetId/following。当前有效本人、有效标签；文章复用 viewableArticle，缺失/不可读/作者不可用与无效目标404，格式422，数据库错误不伪装following=false。标签状态与关系同SQL查询；文章可见性及本人关系是分步读，不承诺跨这两步的全局快照或强一致。

客户端标签详情标题、普通文章尾部、长篇更多菜单共用 ContentRelationButton。明确PUT/DELETE沿既有follows.write与写核心；不读文章详情缺省isFollowing/isWatching作状态。标签名称/slug复用现有v3目录解析，当前路由身份内复用解析ID，换路由/账号清理。未知命令按kind+ID固定原key，跨SPA及正常令牌轮换保留，恢复4xx仍不能证明此前未提交。成功后GET当前状态，读失败只重读；另一组件完成时废弃在途旧GET并重读，避免旧结果覆盖，UserFollowButton同步修复。

既有PUT/DELETE及旧服务端跟随链契约保持；移除无消费者的旧前端followArticle/unfollowArticle/watchArticle/unwatchArticle包装。没有迁移文章点赞/感谢/评论/其他实时功能，也没有允许不可读收藏占位删除。没有改APP、SQL、Repository表定义或经典PC/mobile模板；用户站同一响应式实现已评估桌面/移动。

## 实际修改文件

后端根 `/Users/ling/Documents/GitHub/rhythm`：

- `src/main/java/org/b3log/symphony/processor/ApiV3FollowProcessor.java`
- `src/main/java/org/b3log/symphony/service/ApiV3FollowQueryService.java`
- `src/test/java/org/b3log/symphony/service/ApiV3FollowStateTest.java`
- `tools/api-v3-follow-state-test.mjs`
- `API.md`
- `UPDATE_LOG.md`
- `docs/api-v3-progress.md`
- `docs/api-route-inventory.md`
- `AGENTS.md`
- `docs/api-v3-client-content-relations-work.md`

客户端根 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-content-relations.ts`
- `src/api/v3-content.ts`
- `src/api/fishpi.ts`
- `src/stores/v3ContentRelations.ts`
- `src/components/articles/ContentRelationButton.vue`
- `src/components/articles/LongArticleReader.vue`
- `src/components/user/UserFollowButton.vue`
- `src/views/ArticleView.vue`
- `src/views/ArticleListView.vue`
- `tools/v3-content-relations-test.mjs`
- `tools/v3-user-follow-test.mjs`
- `README.md`
- `AGENTS.md`
- `docs/V3_MIGRATION.md`
- `docs/MISSING_APIS.md`

## 实际验证及证据边界

- `node tools/api-v3-follow-state-test.mjs`：Java25定向javac，6组 SQLite/真实Netty RequestContext+Dispatcher+生产auth/controller/query。SQL实际执行，覆盖当前本人/目标/类型、重复记录、无效ID、数据库错误；新端点匿名或旧apiKey401、缺content.read403、合法Bearer200、no-store白名单。没有Maven。
- 同runner `--mysql` 在一次性 MySQL8.4、实际JDBC执行生产查询SQL，6组通过并删除容器。仓储select与用户/标签仓储壳适配实际SQL；文章可见性依赖在此为受控适配器，不代表真实讨论邀请/缓存/完整IoC已验收。数据库只测读，不宣称新增写入原子性证据。
- `node tools/api-v3-follow-test.mjs` 既有写入primitive 4组回归通过，不能等同完整生产服务事务/通知验证。
- 前端 `tools/v3-content-relations-test.mjs` 9组，实际TS/API、Pinia与Vue组件：精确GET/PUT/DELETE、严格响应/目标、未知原命令与同key、换身份/迟到写隔离、跨组件重挂载、历史receipt后GET、读失败不重写、SSR/匿名无私有请求、关系kind隔离/恢复4xx、标签目标解析与普通/长篇模板接线、另一组件提交时在途旧GET失效。
- 用户关注7组、保存7组、现有内容DTO/目录/分页、认证12组回归通过。`corepack yarn run build`（vue-tsc/client/SSR）通过。正式js-version-audit扫描69个生成JS，0命中，无map；diff --check及新增文件空白检查通过。
- IAB本地合成API：普通文章收藏首次COMMIT PUT返回503，按钮原key恢复为REPLAY PUT后GET显示取消收藏；取消COMMIT DELETE后GET503，界面“操作已确认，状态读取失败”，重试只GET；订阅独立COMMIT PUT→GET。文章标签进入真实名称路径，目录解析到811，首次403不改状态，重新发起成功后GET。长篇更多菜单亦显示/操作同组件，合成收藏成功。
- 1280x900桌面和390x844移动检查；移动clientWidth=scrollWidth=390，标签按钮与长篇菜单可读。证据 `/tmp/rhythm-v3-content-relations-tag-desktop.png`、`/tmp/rhythm-v3-content-relations-tag-mobile.png`、`/tmp/rhythm-v3-content-relations-long-mobile.png`。没有生产账号写操作；所有关系写均本地合成记录。
- 普通文章页仍存在既有登录态、分享链接与编辑器/ReactionBar的hydration差异；本批共享按钮mounted保护/隔离SSR测试不等于整页SSR通过。旧点赞/感谢/评论等仍未迁移，部分操作依赖旧apiKey，不能宣称整文章可用。

## 完整人工验收链

1. **先用户通过IDEA重新编译/重启Rhythm**，代理指向localhost:8080，使用独立测试账号和可丢弃目标。此提醒已在真实联调前给出；本批没有假定当前服务已加载新Java。
2. 直接GET三个新入口：无Bearer、仅旧Cookie/query apiKey应401；仅profile.read应403；有content.read取得本人targetId/following。账号A/B对同目标不同关系状态应隔离，响应无他人关系/敏感字段。
3. 从标签墙/文章标签进入标签页，按钮取得真实目录ID。关注后显示取消关注标签；取消后恢复关注标签。无效/失效标签404；名称不唯一409，目录失败不伪造未关注，原页面可明确重读。
4. 普通文章分别收藏、订阅、取消；/stars及本人订阅列表重新进入后应与服务器状态一致。长篇更多菜单重复验证。服务端角色不允许/讨论无邀请/作者停用须拒绝；本人历史不可读占位读取不表示允许删除。
5. 模拟写成功丢响应，离开再回到同目标，恢复必须原方法/原key/原意图；不能反向toggle，也不能显示历史receipt当当前状态。普通token轮换保留待确认；换号/退出清理，迟到响应不能复活。
6. 写成功后令GET失败，仅按钮重读，不再PUT/DELETE。另一个同目标组件在GET尚未返回时提交，完成通知须废弃旧GET，再读；迟到旧状态不得覆盖。标签、收藏、订阅即使ID相同也不串命令。同步验证主页/悬浮卡片用户关注同类竞态。
7. 桌面/移动检查长标题、按钮换行、错误提示与焦点保留；未登录无私有状态读取。检查初始SSR与恢复认证后新控件状态，并记录整文章页既有hydration问题，不能只凭按钮验证判整页通过。

## 安全与反漏扫结论

新增入口均明确content.read Bearer鉴权；匿名/旧Cookie/apiKey拒绝有隔离HTTP证据。当前本人ID仅从认证viewer，目标ID严格长度/格式，SQL参数绑定；只投影targetId/following，不返回IP/UA/邮箱/手机/凭据。读接口无写副作用/CSRF动作；已有PUT/DELETE沿原写鉴权/幂等策略，本批未另放Cookie入口。新控件错误/标签/文本走Vue文本，未新增HTML拼接/远程抓取、上传下载或SSRF入口，页大小/请求预算沿已有目录，按钮阻止并发重复。

BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor、anonymous.viewSkips均未修改；真实黑名单跳转/test、验证码解封、实际CSRF/越权/内容权限、跨实例缓存及事务/通知/并发未实测，仍须完整部署验收。新增生成JS版本/sourceMappingURL/map定向扫描通过；全站CDN/目录枚举/旧上传下载路径未做全量实测，不据打包审计推定部署目录安全。Java完整编译/IoC及真机未验收；无Maven、无APP改动、无提交/推送。

注册清单已按总register可达METHOD+完整path重扫，并与进度文档279项逐条集合相等：GET137/POST71/PUT46/DELETE24/WEBSOCKET1。全仓库路由清单独立口径826，不能与v3数量混用。完整v3目标保持未完成。
