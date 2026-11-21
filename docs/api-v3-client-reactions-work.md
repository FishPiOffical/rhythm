# v3 客户端文章、评论、聊天室表态（2026-10-26）

## 行为与边界

V3ReactionBar统一普通/长篇文章、普通评论/楼中楼/长篇段评及聊天室经典/现代两种消息布局。沿既有目录GET /api/v3/reactions/emojis与三类目标GET/PUT/DELETE，不新增后端接口。目录按Pinia/当前身份共享进行中请求，不回退硬编码表情；汇总按目录重建emoji、白名单计数/选择/展示名，忽略原始userDetails及其他字段。目标类型/ID/groupType、正计数、重复项和current/selected一致性严格验证，非法响应明确失败。

当前选中项再次点击发DELETE，其余为明确PUT value；不存在旧toggle请求。命令按article/comment/chat+ID隔离，未知结果原choice/key跨SPA/正常token轮换保留，恢复403仍不丢key，未确认前拒绝另一选择。回执只确认本次操作，之后GET当前状态；历史计数/选中态不回写页面。提交后GET失败只重读，退出/换号清命令/目录和迟到读写。仅内存恢复，整页刷新/退出会清本地恢复信息。

登录后自动读取当前状态；等待认证恢复时不发游客扇出请求。游客可在认证检查完成后明确点击“查看表态”，可重复刷新；不能提交，拒绝返回非空游客选中态，聊天室游客不调用需要认证的消息表态。SSR不请求目录/本人状态，与首次客户端占位一致。游客实时提示只丢弃过期本地汇总，不批量自动发GET；不改变服务端匿名/验证码规则。

文章与聊天室现有Socket的reaction消息仅作为当前目标失效提示；不采用事件summary/actorReaction，最终选中态来自当前读者GET。类型和ID筛选后才通知，先前在途GET会废弃；其他目标不受影响。该适配不代表旧Article/Chat连接已迁移到v3票据/订阅，文章热度/评论及聊天室历史、发送、在线等旧链路仍在迁移队列。

选择面板保持DOM，异步写/读用aria状态及处理函数拒绝重复，面板只由用户显式收起。只读优质回帖可读汇总但无写按钮。评论互动区保留正文后的同级布局，移除依赖旧字段的悬浮quiet布局；不是卡片套卡片。聊天室历史原始reaction字段已无消费者，取消其到ChatLine的投影，避免继续保留旧选中快照。

本批无Java/FTL/SQL/Repository、经典模板、CDN、APP及API.md契约修改；后端仍281（280HTTP+1WS），GET139/POST71/PUT46/DELETE24。无Maven/提交/推送。完整v3、聊天室、匿名本人管理及真实权限/部署验收尚未完成。

## 实际文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-reactions.ts`：目录和三类GET/PUT/DELETE，严格目标/目录/汇总投影，明确choice/key。
- `src/stores/v3Reactions.ts`：目录合并请求、类型命令隔离、未知恢复/身份清理、完成/失效通知。
- `src/components/V3ReactionBar.vue`：当前状态/目录、选择/清除/恢复、只读/游客按需读取、SSR与节点保留。
- `src/components/CommentItem.vue`：普通评论/楼中楼及只读评论共新组件，互动区SCSS同级布局。
- `src/components/articles/LongArticleReader.vue`：文章与段评侧栏新组件。
- `src/views/ArticleView.vue`：文章新组件、去除旧写处理/事件；旧Socket仅发失效提示。
- `src/views/ChatroomView.vue`：经典/现代两种布局共消息表态组件。
- `src/stores/chat.ts`：旧Socket仅触发表态GET失效，不采用其他actor选中态；删旧react方法及无消费者历史reaction字段。
- `src/api/fishpi.ts`：删除toggleReaction/applyReactionPayload旧无消费者包装。
- `tools/v3-reactions-test.mjs`：16组真实TS/Pinia/完整Vue/CommentItem/生产chat Socket处理专项。
- `tools/v3-comments-test.mjs`、`tools/v3-comment-votes-test.mjs`、`tools/v3-comment-rewards-test.mjs`：原专项隔离非本项reaction子组件；评论三写测试补新失效store适配。
- `AGENTS.md`、`README.md`、`docs/MISSING_APIS.md`、`docs/V3_MIGRATION.md`：架构与迁移状态。

Rhythm：`AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、本文件。

## 实际证据

16组专项执行生产TS API/Pinia/编译完整Vue：三类目标精确目录/GET/PUT/DELETE/key和字段投影；坏目录/目标/重复/计数/选择失败；同数值ID三类型独立命令/轮换/恢复403；真实组件设置/切换/清除及当前GET；丢ACK重挂原choice/key并GET覆盖旧receipt；已提交/GET503只重读；失效通知和旧读隔离；退出清理及迟到读写；游客不可写/不能接收选中态，chat游客和SSR无请求；目录失败可重试/多实例目录请求合并；本人只读无写按钮；真实CommentItem忽略旧99计数并正确v3设置；生产chat store的真实onmessage处理其他actor的summary/选择只触发GET；面板原按钮节点跨异步保留且拒重复；旧身份迟到目录不能替换新目录；恢复认证前零请求，游客仅显式读且事件不扇出。

测试使用生产模块和自定义Vue renderer，HTTP/认证/无关子组件为受控适配；chat的历史/节点返回与WebSocket是测试假件，生产onmessage执行但没有建立真实Socket或调用真实账号。长篇侧栏、聊天室两完整页面接线有静态检查与构建，完整DOM及真机未在本专项执行。按钮节点断言不能等同浏览器焦点验收。

回归评论三写16、赞踩12、感谢采纳11、文章互动15、认证传输12、启动9、共享生命周期3通过。corepack yarn run build完成类型、client/SSR；71生成JS正式版本扫描0命中，无map/sourceMappingURL/sourceURL；两个仓库diff --check通过。APP工作区未修改。

本地Express/Vite合成整页HTTP SSR普通/长篇通过：公开评论正常，私密正文/匿名真实ID不输出，长篇有当前锚点；SSR日志无目录/reaction/私人源码/Authorization或写请求。证据 `/tmp/rhythm-v3-reactions-ssr.log`。浏览器工具返回空会话，没有浏览器点击、双视口/水合/焦点、真实服务权限/事务/Socket/节点验收证据。

## 人工完整验收链

1. 联调前先用IDEA重新编译/重启前批Java（本批无Java变更），再启动用户站。游客进入普通/长篇文章，认证检查完成后点击“查看表态”：只读当前汇总，可刷新，无选择/写入。等待认证恢复期间、未点击的多条评论及游客Socket提示不应扇出请求；新IP/黑名单仍按平台验证流程处理，不能绕/test。
2. 登录具有content.read/reactions.write的账号，文章、根评论、楼中楼和长篇段评均显示当前状态；打开目录选择heart，再选fire，再点已选fire清除。逐项核对PUT/DELETE及当前GET、实际reaction记录和其他读者汇总，重复设置不得toggle。用本人只读优质回帖入口核对不能修改，私密无关读者/失效目标/缺scope应明确失败不展示旧状态。
3. 聊天室经典/现代布局对合法当前消息执行同样设置/切换/清除；他人Socket事件不得把其选择当成本人。文章及评论也应GET当前选中。切文章、段落、消息或同数值ID不同类型，不得串状态；当前服务不可读的远端节点/历史消息应明确失败，不能回退旧POST或借用节点key。
4. 模拟PUT/DELETE已提交丢响应，跨SPA重挂同目标恢复，目标/choice/key不变；恢复403保留原键。随后其他设备改选，再恢复旧回执，页面须展示最新GET。成功后GET503只重读，不重复写；目录失败不提供假表情，重试后可恢复。
5. 正常token轮换保留未确认命令，退出/换号清除；延迟目录/读/写后离页或换号不得回填。并发另一个组件/Socket完成使旧GET过期。异步期间重复点击不得新增写入，目录按钮节点与键盘焦点保持；面板按用户操作收起。
6. 1280px桌面与390px移动端检查普通评论、长篇侧栏、聊天室两布局和浮窗的错误/恢复/选择面板，不横溢出、不新增hydration警告。需真实验证访问控制、黑名单/验证码解封、节点、通知和v3 Socket；既有其他聊天室能力未在本批完成迁移。

## 反漏扫与限制

无新公开路由、anonymous.viewSkips、凭据例外、CDN、上传下载或可枚举目录；既有匿名GET仍通过原匿名验证，chat GET和写入仍Bearer scope。组件恢复后才自动认证读、游客显式读，不放宽首访/验证码/Before链。输入仅受限类型、正整型ID、目录值及原key，固定同源路径与JSON，不新增SQL/远程抓取；响应只使用目录emoji、计数、本人选择和公开展示名，不输出userDetails/HTML/URL或敏感作者。未知命令仅内存；事件payload不作权限或本人状态证明，最终仍当前GET。CSRF/认证沿现有Bearer HTTP，无Cookie/apiKey兜底。

定向生成资源版本/map/sourceURL检查、身份隔离/SSR及既有传输回归通过。真实未鉴权/越权/CSRF/XSS/注入/SSRF、批量压力、全局黑名单/test/解封、目录枚举与部署Socket/连接池/节点未实际验收，不能默认通过。没有把合成HTTP、假Socket或renderer测试写作真实浏览器/事务证据。
