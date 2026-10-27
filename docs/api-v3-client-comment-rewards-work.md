# v3 评论感谢、采纳状态与客户端接线（2026-10-26）

## 行为与边界

新增 GET /api/v3/comments/{commentId}/thanks 及 /accept，均要求当前 Bearer/content.read，总 register 可达。前者投影 id/thanked/canThank/thankCount/thankPoint，后者 id/accepted/canAccept/offerPoint。仅读取当前有效账号和评论/作者，再按共享文章可见性及私密正文规则检查；匿名评论两种费用均0。不返回真实匿名作者、正文、原始用户或其他被采纳评论ID。

感谢本人记录按 actor+comment+Reward.TYPE_C_COMMENT 的 EXISTS 读取，历史重复不重复行；采纳状态使用当前目标标志及同文章任意已采纳记录，跟随原写核心的判定范围。canThank 是非本人且未感谢；canAccept 沿客户端原问答页展示口径：问答文章作者本人、非评论作者且该文章尚无采纳。费用来自既有 Symphonys/文章悬赏，0悬赏问答保留原资格；不新增业务收费规则。两种can字段是基本资格提示，不保证权限/余额或提交时状态，现有原子写核心逐次重验。

普通评论/楼中楼与长篇段评共用ArticleInteraction的comments/thanks、comments/accept。初始GET必须成功才接受新付款；本人/已感谢不重复操作，采纳不从旧列表缺省字段推断。费用确认可取消，离页/身份变化取消本人确认；未知命令原目标/key跨SPA、正常token轮换保留，恢复不再新确认/换键。POST回执仅确认操作，随后GET当前状态；余额刷新和状态读取失败独立，重读不得重付。采纳完成重读全部挂载的采纳候选，其余资源/种类仍精确目标刷新。退出/换号清命令与迟到结果。仅内存恢复，不跨整页刷新或退出保存。

原POST接口及事务/费用/成功响应未改；旧页面端感谢/采纳入口也未修改。本批没有SQL迁移、新Repository、FTL、经典PC/mobile、APP或上传下载变化，未运行Maven。后端现281（280HTTP+1WS）：GET139/POST71/PUT46/DELETE24/WS1。reaction、文章编辑/旧Socket/上传、私信与聊天室等继续，不能称完整v3或评论模块已验收。

## 实际文件

Rhythm：

- `src/main/java/org/b3log/symphony/service/ApiV3CommentRewardQueryService.java`：生产只读SQL、资格/费用、文章/私密校验与白名单。
- `src/main/java/org/b3log/symphony/processor/ApiV3CommentRewardProcessor.java`：两个GET，content.read、共享no-store信封。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`：总注册接线。
- `src/test/java/org/b3log/symphony/service/ApiV3CommentRewardQueryTest.java`：五组查询/隐私/状态/实际路由鉴权专项。
- `tools/api-v3-comment-reward-query-test.mjs`：Java25隔离执行，SQLite或 --mysql 一次性实际MySQL。
- `API.md`、`UPDATE_LOG.md`：两个新接口及开发者更新说明。
- `docs/api-route-inventory.md`：重扫828全仓声明/398经典浏览器字面量，不作为v3数量。
- `docs/api-v3-progress.md`、`AGENTS.md`、本文件：当前281与长期链路/实际证据。

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-article-interactions.ts`：comments/thanks及accept参数、当前费用/资格与严格响应投影，articles/accept和comments/reward拒绝。
- `src/stores/v3ArticleInteractions.ts`：接受采纳命令、资源隔离及既有付款恢复/余额策略。
- `src/components/articles/ArticleInteraction.vue`：感谢/采纳状态、资格、确认、当前GET及全候选采纳刷新。
- `src/components/CommentItem.vue`：根评论/楼中楼三类互动共用组件，移除旧感谢/采纳按钮与推断资格。
- `src/components/articles/LongArticleReader.vue`：段评侧栏接共享感谢/采纳状态；非问答采纳不展示可操作按钮。
- `src/views/ArticleView.vue`、`src/api/fishpi.ts`：移除thankComment/acceptComment旧处理、事件及无消费者包装。
- `tools/v3-comment-rewards-test.mjs`：11组真实API/Pinia/完整Vue与CommentItem专项。
- `tools/v3-comment-votes-test.mjs`：赞踩专项适配新增感谢/采纳子请求，12组保留。
- `AGENTS.md`、`README.md`、`docs/MISSING_APIS.md`、`docs/V3_MIGRATION.md`：已补读取缺口与剩余迁移状态。

## 实际验证

服务端五组分别在SQLite及一次性MySQL通过：

1. 生产SQL按本人/类型绑定感谢记录，重复记录仍唯一结果，本人资格与字段白名单。
2. 使用生产ApiV3ContentUtil私密判断；文章共享可见性由适配器提供。无关读者404、双方可读、匿名费用0；失效actor/author/comment和缺失记录404。
3. 问答作者/非本人资格、普通文章不可展示采纳、0悬赏问答兼容；另一评论已采纳时不可再采纳，不泄其ID。
4. 严格ID与SQL异常传播，不用false伪装数据库失败。
5. 生产Dispatcher/RouteHandler/AuthMidware/Processor/ApiV3Requests实际执行：两路Cookie/query apiKey单独401、仅comments.write403、content.read200/no-store/Vary，奖励表只读不增。

隔离编译使用Java25，无Maven。SQL由生产服务发出，Repository select经JDBC适配器执行真实SQLite/MySQL；模型/可见性工具/中间件/端点是生产实现。Token发行/完整Article可见性服务/IoC/连接池和全局BeforeRequestHandler未在此装配，不能将上述称为完整应用或真实令牌全链路。临时MySQL已清理。

客户端11组实际执行生产TS、Pinia及编译完整Vue：当前GET/POST资源与key/投影和无效费用；感谢确认取消及当前计数；匿名0费用/本人不可操作；采纳费用/当前状态/无资格不显示按钮；丢ACK重挂原键恢复且不再确认；提交成功/GET及余额失败只重读；感谢和采纳未知命令隔离与轮换/恢复403；离页取消确认；退出废弃迟到读取/SSR无私人请求；其他候选完成后当前资格重读；实际CommentItem感谢/采纳及两侧接线/旧包装移除。HTTP、确认、认证和无关子组件为受控适配，真实后端付款未执行。

回归赞踩12、评论三写16、文章互动15、认证传输12、启动9、共享生命周期3通过；类型与client/SSR构建完成。正式71生成JS版本审计0命中、无map，两个仓库diff --check通过，APP工作区干净。

本地Express/Vite整页合成HTTP SSR普通/长篇均通过：公开评论正常，隐藏正文/匿名真实ID不输出；游客不挂本人感谢/采纳资格，无私人源码/互动、Authorization或写请求。日志 `/tmp/rhythm-v3-comment-rewards-ssr.log`。浏览器工具本批返回空会话；长篇侧栏完整DOM、浏览器费用确认/焦点、双视口与真机仍未验。

## 人工完整验收

1. 先由用户通过IDEA重新编译或重启Java；未确认前不得把旧服务结果当新代码失败。启动用户站，登录具备content.read/comments.write及既有感谢权限的账号，在他人普通文章根评论/楼中楼读当前状态；感谢先取消，无POST，再确认，核对本人感谢状态、当前计数、真实余额/账本/通知。重复新键也不应重复扣款（原写核心）；余额不足/权限不足/目标失效应失败不扣款。
2. 匿名评论显示0费用；以作者、楼主和无关读者测试私密评论，第三者两个状态GET应404，不能据状态推身份或材料。本人评论不能感谢/采纳自己，不能靠用户名推归属。停用actor/author、缺失/非法ID和缺scope均检查明确错误，无真实作者ID/其他采纳ID泄露。
3. 问答作者选择他人评论采纳：显示当前悬赏费用、取消不提交、确认后GET已采纳；其他候选应失去采纳按钮。普通读者不得采纳；普通文章无采纳按钮，0悬赏问答保留0费用确认。匿名评论0费用、已有其他采纳/余额不足按原核心拒绝或幂等，无新增扣款。
4. 打开长篇段评侧栏，在当前锚点/分页执行感谢；切换段落/文章或退出，旧资格/迟到结果不得回填。普通及长篇评论赞踩/发布/编辑/删除、文章三类互动作为共享组件回归，非问答不误显采纳动作。
5. 模拟POST已提交丢响应，跨SPA重挂原评论恢复，目标/方法/key不变、不再付款确认；恢复403仍保留原操作。成功后GET503或余额刷新失败，只重读或到积分核对，不再次POST。另一候选正在确认时别处已采纳，应取消其旧确认并重读，不提交过期选择。
6. 桌面和390px移动视口、键盘/确认焦点、错误与恢复按钮和长篇侧栏无横溢出；检查实际网络、账本、通知以及服务端角色/阅读权限。需真实验证黑名单/test/解封和完整部署，不用合成页面代替。

## 反漏扫结论与未验

新GET明确contentRead，Cookie/apiKey不得提升为Bearer，未新增anonymous.viewSkips或匿名豁免，未改黑名单/验证码链。查询仅只读、参数化且ID有界，目标/作者/actor有效和正文可读后才投影；无HTML/原始用户、敏感身份、上传下载路径或远程抓取，未引入SQL注入、SSRF入口。GET不改变账款/资格，客户端POST沿已有Bearer与幂等传输，未新增Cookie写/CSRF例外。批量调用仅单目标查询，没有全站枚举或未分页目录；实际滥用压力与限流仍未验。定向生成资源/CDN版本/sourceMappingURL及无map检查通过，没有新增静态目录/外链。真实未鉴权/越权/CSRF/XSS/注入、黑名单/test/解封、多实例/连接池、原子账款与通知全链路仍需完整环境验收，不能默认通过。
