# v3 用户联想（第六十五轮，2026-10-26）

## 用户确认与实际文件

用户已确认：profile.read；用户名忽略大小写前缀，1..64位英文字母/数字/下划线/连字符；最多10个当前有效用户，只有公开ID、用户名、昵称、头像；空前缀空列表，不继承旧空前缀管理员名单。

后端：

- `src/main/java/org/b3log/symphony/service/ApiV3UserSuggestionQueryService.java`：严格前缀，当前有效actor重查；非空时从user当前行按LOWER(userName)参数化前缀读取，不走旧USER_NAMES缓存，显式转义下划线，当前status=0及LIMIT10。按LOWER(name)/name/id排序，投影四字段；头像复用既有safeUrl、异常历史昵称不原样输出控制内容。数据库失败503，不能伪装无匹配。空前缀仍校验本人，但不查询候选。
- `src/main/java/org/b3log/symphony/processor/ApiV3UserSuggestionProcessor.java`：GET `/api/v3/suggestions/users`，profile.read；严格原始query解析，拒重复键/未知参数/坏UTF8；独立有界计数与并发保护，不消耗发布预算，no-store/Vary。路径避免抢占现有`/users/{userName}`公开资料。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`：总register接线。新总283=GET140+POST72+PUT46+DELETE24+WS1；HTTP282。全仓830为另一个口径。
- `src/test/java/org/b3log/symphony/service/ApiV3UserSuggestionsTest.java`、`tools/api-v3-user-suggestions-test.mjs`：实际SQL/HTTP/鉴权边界，支持SQLite和临时MySQL。没有新增Repository、表或数据库写操作，无迁移/repository.json修改。
- `API.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`docs/api-route-inventory.md`、`AGENTS.md`及本记录。

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-user-suggestions.ts`：严格prefix/count/id/字段类型/对应前缀校验，投影四字段，剔除不安全头像；空前缀不请求，no-store/AbortSignal。
- `src/utils/mentionHints.ts`：旧promise在新调用之前立即结算空数组并取消HTTP，避免Vditor迟到回调隐藏较新的结果；根据身份版本/令牌/当前请求拒过期响应。用户名/昵称/头像HTML转义，插入只用用户名+空格；失败显式状态，仍允许手工输入，不透出后台错误。
- `src/components/MarkdownEditor.vue`：替换旧searchUsers/apiKey链，移除空前缀participants特殊项；原生输入捕获阶段清旧提示与取消请求，外部正文/身份/卸载同样清理，普通token轮换保持。错误通过文本status。不能在Vditor延迟input回调清提示，否则当前列表会被过早关闭。
- 曾尝试用emoji工具栏别名兼容原生键盘，真实验收发现会隐藏自定义按钮；源码复核确认Vditor自动追加冒号hint，原生键盘本就可用，试验代码已全部移除。
- `tools/v3-user-suggestions-test.mjs`：7组生产API/异步提供器专项；`tools/v3-editor-emojis-test.mjs`适配新增mention模块与轻量DOM能力，保留7组完整Editor/Picker回归。`AGENTS.md`、`docs/V3_MIGRATION.md`更新，dist/client/server由build生成。

## 实际证据

- Java25隔离javac+SQLite真实SQL/Dispatcher/auth四组通过：`/tmp/rhythm-v3-user-suggestions-tests.log`。Token持久化/完整信封是适配器，UserRepository转真实JDBC；没有Maven/完整IoC。
- 同四组在临时MySQL8.4通过：`/tmp/rhythm-v3-user-suggestions-mysql.log`，脚本finally已移除临时容器。仅回环随机端口、独立库，不碰用户数据。
- 覆盖有效/停用/待激活、本人状态、空前缀、大小写、字面下划线、前10排序、四字段、头像、非法/超长/SQL样式prefix、数据库失败、旧Cookie/apiKey不提升、失效Bearer/缺scope、重复query/坏UTF8、no-store、限额/并发拒绝及恢复。
- 客户端7组：`/tmp/rhythm-v3-user-suggestions-client-tests.log`，包括替换promise立即结算的顺序、旧请求取消/迟到、昵称HTML、头像query转义、换人/退出/轮换/卸载及手动输入提示。
- 文章27、评论17、预览7、上传9、完整Editor表情7回归通过：`/tmp/rhythm-v3-user-suggestions-v3-*-test.log`；后续仅取消时机改为input.capture并删试验alias，相关专项与Editor表情7已再跑。
- 类型/client/server构建：`/tmp/rhythm-v3-user-suggestions-build.log`。生成JS版本/map扫描：`/tmp/rhythm-v3-user-suggestions-js-audit.json`；两仓diff --check。
- IAB真实CDN Vditor+合成HTTP：桌面键盘输入@al→出现Alpha/Alpine，延迟后仍可选，ArrowDown+Enter插入@Alpine；390×844移动按可见候选位置原生点击插入@Alpha。昵称`本地 <文本>`为可见纯文本；合成后端观测新GET带Bearer。移动语义locator会因其滚动/焦点步骤关闭原生面板，改用观察到的坐标实际点击验证，没有据此添加多余的页面事件补丁。
- 截图`/tmp/rhythm-v3-user-suggestions-mobile-proof.png`。Java真实服务尚未编译/启动；这些浏览器结果只证明客户端与真实Vditor对接，不替代真实后端授权/查询验收。

## 限制与安全复核

- API接受64位前缀、最多10条；当前CDN Vditor自身getKey只触发少于32字符、genHTML最多显示8项。客户端API提供完整契约，内置UI仍受其本地上限，未猴补第三方方法或伪称显示10条/64位。后续若要扩展编辑器上限，应在可维护的编辑器接入层实现。
- SQL用LOWER表达式，生产索引/执行计划及并发压测未验；当前有同节点独立预算和并发上限，不声称跨实例全局限额或索引命中。无候选总人数、无全量名单缓存。
- 新入口明确Bearer/profile.read，不新增viewSkips、不裸匿名访问；Router全局BeforeRequestHandler仍先执行。实际黑名单→test→validateCaptcha、匿名首访/周期验证、开发关闭风控、真实epoch撤销未运行验收。
- GET不写数据库/凭据/通知，不需要CSRF写证明；旧Cookie/apiKey不降级。响应白名单无联系方式/密码/权限/令牌/IP/UA，昵称由客户端转义。身份变更清本地候选与在途请求。
- 参数化查询+严格ASCII+下划线转义，无SQL拼输入。服务端不抓头像，无新增SSRF；客户端头像只作已验证地址展示。失败不反射prefix/SQL/凭据。
- 无新上传/下载路径、CDN URL、可枚举静态目录；资源扫描只覆盖本轮生成资源，全工作区发布前审计未在本轮全量重跑。旧`/users/names`及其他旧消费者保持，未宣称全站用户搜索完成。

## 人工完整测试

Java有改动，先通过IDEA重新编译或重启服务端，再前端build/强刷；未编译服务的404不能当源码未完成。无Maven。

1. 登录→发帖/共享编辑器→键盘输入@加实际账号名前缀；出现候选，点击或方向键+Enter插入正确用户名，源文本不带昵称/HTML。桌面与移动分别验；记录Vditor本地8项/31位限制。
2. 大小写前缀结果相同；下划线只匹配真正下划线；空@不返回管理员/participants；失效账号不出现。接口直接验64位合法前缀、65位/非法字符422、空prefix空列表、无匹配空列表。
3. 快速更换前缀/删除@，延迟旧响应不得覆盖新列表；切换账号/退出/载入另一草稿/卸载清旧列表，普通token刷新继续有效。网络503给可见提示，保留原文并可手动输入。
4. 缺profile.read403，失效/无Bearer401，重复query/坏UTF8拒绝；昵称HTML字样只显示文字，不执行。确认旧接口/apiKey不再由本编辑器联想调用。
5. 保存草稿/发布并回读@用户名；真实通知/讨论可见性、MySQL当前状态变更、执行计划与全局防扫描链需真实服务进一步验收。

标签联想、专栏选择、其他旧用户搜索消费者，以及文章删除/置顶客户端、私信/聊天室主体等总清单继续。全v3未完成；本轮无FTL/SQL迁移/APP/Maven/提交/推送。同步API.md到网站API文档文章。
