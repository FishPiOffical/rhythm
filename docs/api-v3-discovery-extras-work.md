# v3 最近用户、随机发现与日志元数据（2026-10-28）

第100批。用户已确认三个接口的业务范围。新增3条GET，总register可达288条（287HTTP+1WS）；GET145、POST72、PUT46、DELETE24、WS1。无新表、迁移、数据库写操作、FTL或APP改动，未执行Maven。Java联调前已提醒用户通过IDEA重新编译或重启，尚未确认生效。

## 实际修改文件与行为

Rhythm：
- `src/main/java/org/b3log/symphony/processor/ApiV3DiscoveryExtrasProcessor.java`：新增recent-members、articles/random、logs三GET。前两项可选profile/content Bearer并经过匿名验证，日志必须activity.read；统一read信封/no-store。最近用户使用根静态路径，避免与合法用户名冲突。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`：总注册接线，random静态路径在通用文章ID路由前注册。
- `src/main/java/org/b3log/symphony/service/ApiV3DiscoveryExtrasQueryService.java`：实际Repository只读SQL。最近20名仅status0、ID倒序、公开四字段；随机最多20/default8、随机字段起点两段回绕/去重/打乱、排除ID/讨论/隐藏/沙盒/同步标签，游客额外排除禁止匿名阅读内容，作者当前0/3状态。作者重新SQL白名单读取；无全响应事务快照或全库等概率保证。日志先按public/simple/16个固定类别过滤，再计数分页；MySQL BINARY精确匹配类别，不读取data/key2，只投影ID/原记录时间/类别；坏时间或查询失败503，不吞为空列表。
- `src/main/java/org/b3log/symphony/service/ApiV3ContentDtoService.java`：增加显式author重载，旧调用仍沿原查作者行为；新随机接口传当前SQL作者。沿公共清洗摘要/匿名占位/置顶元数据，不返回付费正文或IP/UA。
- `src/test/java/org/b3log/symphony/service/ApiV3DiscoveryExtrasQueryTest.java`、`tools/api-v3-discovery-extras-test.mjs`：Java25实际服务/公共DTO与SQLite10组、接线源码1组；Avatar/Markdown等环境适配。SQLite将MySQL unary BINARY转换为COLLATE BINARY，不能代替实际MySQL方言与执行计划验证。
- `API.md`、`UPDATE_LOG.md`：三个接口完整契约及倒序更新；`docs/api-route-inventory.md`835全仓路由/398浏览器引用；`docs/api-v3-progress.md`、`AGENTS.md`及本记录维护范围与证据。

website_v3：
- `src/api/v3-discovery-extras.ts`：严格白名单解码，数量/去重/排除ID/真实分页、固定类别/有效日期校验；日期校验不推断时区，原时间文本保留。匿名文章不保留真实作者字段；响应额外私有字段丢弃，无旧接口回退。
- `src/composables/useDiscoveryExtras.ts`：三种资源独立请求/错误/原页重试，token/actor/目标/卸载隔离迟到结果，轮换保页、换号退出重置；日志未登录不请求。最近用户只消费本次SSR初始白名单，授权变化重新读取。
- `src/components/discovery/RecentMembers.vue`、`RandomArticles.vue`：加载/空态/失败/重试，纯文本标题昵称，ID构造阅读链接，匿名无作者链接；19人不再按十人行截掉尾行，桌面/移动SCSS，组件不嵌套卡片背景。
- `src/views/HomeView.vue`、`ActivityView.vue`、`ArticleView.vue`：首页/活动中心最近用户、活动/普通文章推荐接入，文章排除当前ID；移除旧共享聚合缓存对最近用户的覆盖。Home其他聚合资源仍有存量缓存/错误策略，未宣称全首页改造。
- `src/views/LogsView.vue`：登录门槛、只展示三字段、20项真实分页/总数/末页、失败清旧及原页重试。
- `src/api/fishpi.ts`：删除三条无调用旧读取包装与旧日志类型；`src/entry-server.ts`、`src/seo/payload.ts`：首页公开最近用户SSR及每应用独立消费/重新投影，失败为null不造空成功。
- `tools/v3-discovery-extras-test.mjs`19组，实际API/组合函数/完整组件和活动父页；`tools/v3-ssr-payload-test.mjs`新增第8组SSR隔离/投影；`tools/v3-comments-test.mjs`完整Article父页适配新组件，其行为由新专项覆盖。
- `README.md`、`docs/BACKEND_API_REQUESTS.md`、`docs/V3_MIGRATION.md`、`AGENTS.md`及本记录同步。生成产物来自build，不手改CSS/min.js。

## 已执行验证

- Java25新专项11组exit0：`API_V3_ENCODER_JAR=/tmp/rhythm-v3-encoder-1.2.2.jar node tools/api-v3-discovery-extras-test.mjs`。既有article-stick实际DTO/置顶6+2组回归通过；medal runner实际共享Bearer/匿名中间件回归通过，包含Cookie/apiKey不能冒充Bearer、首访/第五次跳转。共享组件回归及源码接线不等于新三路由实际HTTP/完整IoC验收。
- 客户端tools74脚本831组全部通过；独立`corepack yarn run build`exit0，类型、客户端及SSR构建完成。生成75个JS版本审计0发现，无.map/sourceMappingURL；没有新增CDN、静态目录、上传下载路径。
- IAB1280×720、最终生产构建+SSR的本地合成HTTP：活动页19名真实条目、匿名作者占位/无链接、HTML字面文本；最近用户503清旧→原资源重试恢复，随机模块独立503清旧→重试恢复。日志p1二十条/总21→p2故障清旧→重试仅1条、下一页禁用，记录的日志GET均携Bearer。首页真实入口的最近用户显示完整19人，其他未提供的夹具资源404单列，不宣称首页全部成功。夹具注入的private canary未显示；没有真实账号/写操作。临时页面和服务已清理。

## 安全与反漏扫结论

- 新匿名GET显式optional auth后AnonymousViewCheckMidware；logs必须当前activity.read。Router全局BeforeRequestHandler仍先执行，anonymous.viewSkips未加入新路径，既有黑名单、验证码校验/解封及开发模式分支未改。源码及共享中间件回归已核对；真实新IP/黑名单/第五次/解封/开发模式链路待运行验收。
- 三接口仅只读，无Cookie写入或CSRF状态改变；Bearer失效/缺scope仍由现有中间件拒绝，无Session/apiKey提升。讨论/游客可读/匿名身份按SQL和公共DTO过滤，日志无正文/操作者/目标。无远程抓取/SSRF；SQL参数化、固定表名/列/类别，字符串与数量有界；每次随机最多2段+20作者读取，未声称完成生产压力验证。
- Vue文本输出及固定ID链接，不信任返回permalink作任意导航；用户/摘要/日志均响应白名单。接口失败明确，不填假0或缓存私人结果。没有新上传/下载/静态目录/CDN/外部二维码，生成JS版本与map扫描通过，目录枚举部署配置未实际验证。旧经典最近/随机/日志及旧公开日志WebSocket未同步改造，不能宣称存量原始日志已加固。

## 完整人工验收步骤（真实环境待做）

1. 在IDEA重新编译/重启Java，确认三个路径可达、random不被文章ID路由抢占；前端使用本批build。分别在1280与390宽度检查首页、/activity、普通文章页、/logs，检查同级卡片及末页滚动位置，无横向溢出。
2. 游客从首页到活动中心：最近用户按当前status0的ID倒序且最多20，19人尾行完整；冻结/停用/未激活不出现，点击成员进入正确主页。断开该请求→明确错误且旧名单清空→重试恢复，随机模块保持可用；空库显示真实空态。
3. 从活动中心换一批文章，再从普通文章侧栏阅读：1..20/default8、无重复、排除当前文章；讨论/隐藏/失效作者/限制游客文章不得泄露，匿名只显示匿名用户无真实作者ID链接。登录后按当前权限重查；不足数不伪造补齐。size0/21、非法excludeId应422，DB失败503，标题含标签按字面显示。
4. 游客打开/logs只看到登录入口；Cookie或旧apiKey不可作为v3 Bearer；缺activity.read/过期/撤销令牌被拒绝。当前授权打开p1→下一页→短末页，核对真实总数/上一页/禁用下一页；p2失败清旧→重试仍p2，退出或换号清空旧记录。只出现ID/时间/固定类别，原data/key2中的IP/正文/备注不在网络JSON/HTML/SSR中；不公开、未知类别及尾随/大小写混淆类别不计总数，坏时间明确503。
5. 新IP访问两项公开GET、首访/第五次验证、黑名单/test、validateCaptcha解封、开发关闭风控逐项回归；合法Bearer的权限范围仍正确，无匿名盾跳过配置。检查新路径未暴露私有字段、静态资源sourceMappingURL/目录枚举/上传下载路径。
6. 轮换Token、退出换号、快速切文章、离页时制造慢响应，旧身份结果不能覆盖；两个SSR请求互不共享最近用户私有状态。实际MySQL运行BINARY条件、LIMIT参数和排序，复核日志数据格式及当前状态并发变化后重读。

未完成：完整应用启动/IoC与真实三路HTTP、MySQL方言/性能/当前权限、真实生产数据、390px浏览器、验证码/黑名单/解封链、部署目录策略。无Maven、生产发布或推送。本批检查后两仓分别提交；API.md需同步网站API文档文章。
