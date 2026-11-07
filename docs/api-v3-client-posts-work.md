# v3 客户端文章发布、部分编辑与草稿（2026-10-26）

## 结果与边界

PostView新增/编辑、本人编辑源码、草稿分页/详情/保存/删除接既有v3。编辑只发变化字段，保留未改的匿名、打赏、悬赏和章节信息；本人source中的Markdown与付费正文原样填编辑器，不取公开HTML或历史文本。source失败不出假空表单，锁定/无效源码可看不能提交。

发布携带当前draftId，交原服务端事务消费，不追加DELETE。草稿回执仅确认id，不回填metadata而清空正文；新增后下次PUT，列表错误仅重读。载入其他草稿/重新source需确认替换未保存输入，失败重试原候选；删除草稿保留当前输入副本并解除已删id。真实分页与错误/合法空分列。

v3Posts固定单个未确认命令的op/id/input/key，正常token轮换及SPA保留，恢复403不丢key；退出/换号清原文和命令。发布/编辑按平台积分规则确认，离页只取消本人未批准的确认；恢复不再次确认。请求前复制输入，继续键入不改变旧请求。余额/列表/导航失败不重付，确认后防重复发布，编辑续写前重新source。当前表单未变可跳到构造的本地文章路径；其他表单恢复或有新输入时只给结果链接，不覆盖或跳走新表单。只恢复内存中的命令，不跨刷新/退出持久化。

已启用打赏的折叠仅收起配置，不误发0/空正文。长篇新专栏转换为空columnId+columnTitle，不发旧__NEW__。思绪新增关闭；既有思绪按原记录格式编辑，草稿thoughtContent单独保留。后端草稿允许的旧type4仍可读/存，不能伪装普通帖新增发布。表单分区只有分隔线，无嵌套卡片。

本批无Java/FTL/SQL/Repository、经典模板、APP、后端路由或API.md契约变化；后端281（280HTTP+1WS），GET139/POST71/PUT46/DELETE24。无Maven、提交或推送。

**未完成项**：MarkdownEditor的Vditor预览仍走旧/markdown；上传、内部emoji、@提示仍旧包装，标签联想与本人专栏目录选择待接线。文章删除/置顶客户端、匿名本人管理、私信/聊天室主体与v3 Socket等继续。不能将主表单接线称为完整编辑器或完整v3完成。

## 实际文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-posts.ts`：本人source、草稿分页/详情、文章与草稿写，typed字段/长度/Unicode、部分PATCH及metadata回执。
- `src/stores/v3Posts.ts`：原输入/key、确认所有权、单未确认命令、身份/正常轮换/迟到保护、独立余额提示。
- `src/views/PostView.vue`：主表单接线、source错误/恢复、草稿id/分页、确认后输入与导航保护、响应式SCSS同级分区。
- `src/api/fishpi.ts`：删除7个旧文章/草稿方法、queryTags、ArticleDraft/ArticlePayload及articleBody；requestText仍供聊天室原文使用。
- `tools/v3-posts-test.mjs`：27组生产TS/Pinia/完整PostView专项。
- `AGENTS.md`、`README.md`、`docs/MISSING_APIS.md`、`docs/V3_MIGRATION.md`：长期约定与迁移状态。

Rhythm：`AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、本文件。

## 验证与限制

27组专项覆盖实际API/Pinia/完整表单：source白名单/原付费文/隐藏thought；typed部分PATCH与未改字段；精确方法/key/draftId；未知冻结/轮换/恢复403/排他；确认取消；真新帖与安全id导航；仅改title；source503/锁定无假表单；draft metadata不清正文/后置列表503/下次PUT；删除保副本；跨SPA原键不覆盖新表单；导航失败防重发；退出拒旧source；thought保存不新增；新长篇专栏；SSR/游客零私有请求；折叠已启用打赏；重试原候选；编辑后失败续编需当前source；进行中新输入保留；离页取消；真draftId发布无第二次DELETE；第二页503；跨文章原PATCH恢复；新422可改后新key；隐私/通知typed0/1和付费原文；历史type4可存不可新增。测试末尾断言无Vue渲染/指令错误。

富编辑器、Picker、HTTP、确认、认证、导航和缓存为受控适配器；Vue文本/checkbox/select指令实际运行于自定义renderer。没有运行Vditor、真实预览/上传/@、真实路由守卫、数据库/权限/扣费，不能把模拟draftId消费称为已核验真实事务。

回归评论17、历史14、reaction16、赞踩12、感谢11、文章互动15、认证12、启动9、生命周期3通过；类型与client/SSR构建通过，71生成JS版本扫描0命中、无map/sourceMappingURL/sourceURL，两个仓库diff --check通过，APP未修改。

本地Express/Vite四条完整HTTP SSR（/post、编辑、长篇、query草稿）均200，无原文/付费/思绪编辑器输出；日志无本人source/draft/Authorization或写请求。证据 `/tmp/rhythm-v3-posts-ssr.log`。浏览器无会话，真实Vditor、权限/账款/草稿事务、双视口/键盘/真机及全局风控未验。

## 人工完整验收

1. 联调前确认前批Java经IDEA重编译/重启。本批仅客户端。登录后从/post输入标题、标签和Markdown，确认取消无POST，确认后核对当前文章GET、真实积分/通知。手机、首帖标签、权限、余额、审核、超长/坏Unicode失败不得发布/扣款。
2. 普通/讨论/同城/问答/长篇逐类回归；问答至少20，匿名/列表展示/评论/通知/声明typed字段正确。打赏源文/价格及作者、未购买、已购买读者权限分别核对。独立长篇、本人/新专栏、章节号/错误关联按原核心规则执行。
3. 本人/post/{id}必须读source原文，改title不发未改价格/正文/匿名/元数据/章节。折叠打赏不关闭，悬赏不可减少。source503不出空编辑器；非本人404，锁定/无效不可收费编辑。思绪记录保持原格式，新增仍关闭。
4. 超过20份草稿翻页、第二页503重试/记录减少后返回前页。载入确认替换，失败重试同id或保留旧输入。保持隐私/付费/hidden thought/历史类型；新增取得id后PUT，metadata不清正文，后置列表失败只GET。
5. 发布已载入草稿仅一个含draftId的POST，真实核验文章/扣费/删稿同事务；失败不吞稿、不扣款。删除取消无DELETE，成功只删记录，当前输入副本仍可另存/发布且不带已删id；他人/不存在id及旧凭据拒绝。
6. POST/PUT/DELETE已提交丢响应，跨SPA恢复op/目标/输入/key不变，恢复403不换键，新422改正后才新key。另一表单不被旧回执覆盖或导航；余额/列表/导航失败不重付。进行中继续输入不丢失，发布确认后需明确新文章，编辑确认后需当前source续编。
7. 正常token刷新保输入/key；退出/换号清本文/付费/草稿/未知状态，迟到结果不回填。整页刷新丢本地恢复信息，须核对实际文章/账款再操作。桌面和390px移动端用真实Vditor回归确认、分页、章节/长行/键盘与hydration；预览/上传/@/联想另批迁移验收。

## 反漏扫

主链仅既有Bearer/content.read/write及幂等键，不把v3令牌传旧apiKey；固定路径类型/正整数id、有界typed输入，PATCH无服务器只读字段。无新匿名/viewSkips、凭据例外、CDN或上传下载路径，Before/Anonymous/Captcha链未改。无新SQL/远程抓取，source仅本人，原文不作HTML或路径，permalink不用于导航；未知全文仅内存，SSR/退出隔离。

定向资源、身份、传输及SSR通过。真实未鉴权/越权/CSRF/XSS/注入/SSRF/批量滥用、黑名单/test/解封、目录枚举/上传下载路径及事务收费未全链验收；旧/markdown和上传/@链未加固，不能称编辑器全链防漏扫通过。没有对真实账号发帖、删稿或扣费。
