# v3 游戏云存档与本人内容导出：只读盘点和待确认契约

当前更新（2026-10-25）：本文保留原只读盘点和历史待确认问题。本人内容导出三入口已完成，见[导出工作记录](api-v3-post-export-work.md)。游戏规则已获确认：首批仅Evolve、单槽/原样Unicode/大小限制/版本/幂等/显式迁移；四新入口及登记游戏旧桥接已接线，见[游戏存档工作记录](api-v3-game-save-work.md)。其他游戏确认来源后再扩目录，不开放任意cloud键。

本文件只盘点和提出完整方案，不新增HTTP、修改源码或公共API文档。游戏云存档、本人文章与评论导出仍在待完成范围，不能用“旧通用接口不安全”或“存量内容有权限差异”为由直接排除这些能力。

## 一、实际代码与现有公开契约

### 游戏云存档

`CloudProcessor#register`公开旧 `POST /api/cloud/get`、`POST /api/cloud/sync`，都挂LoginCheckMidware。实现却自行取Sessions/body apiKey，未优先统一attr；sync按`gameId=="40"`区分JSONObject，其余optString。旧API.md在“表情包已废弃”章节只描述gameId=emojis，不能从该文档推出cloud只用于表情。

`CloudService#sync(String)`是synchronized JVM单实例方法，检查sys-前缀就直接return，调用方仍可能报告成功；其他任意gameId先删旧row再插新row并commit，无所有者+gameId唯一约束/版本。JSONObject重载对40先删旧，再取stats；stats缺失直接return，没完整commit/rollback。两者只有RepositoryException捕获，不能直接复用为v3严格原子核心。

`CloudService#getFromCloud`按owner+gameId取第一条，吞所有异常并返回空字符串；无保留key过滤。cloud同表包含：

| 分类 | 已有key/用途 | v3游戏边界 |
| --- | --- | --- |
| 真正完整游戏存档 | `evolve-save`，Evolve压缩导出字符串 | 必须保留、可迁移 |
| Evolve排行榜摘要 | `40`，stats派生top | 不能误当完整存档覆盖；与存档操作分开保留现有链路 |
| 历史图片表情 | `emojis` | 已有专门图片表情迁移，不能游戏通用写覆盖 |
| 背包/勋章/风控/撤回 | `sys-bag`、`sys-medal`、`sys-mute`、`sys-risk`、`sys-chat-revoke`等 | 内部，游戏API不可读写 |
| AI记忆 | `ai_memory`，包含个人及特殊全局owner | 不以sys-开头，说明黑名单前缀不足以隔离 |
| 其他真游戏历史key | 旧API允许第三方任意key，源码未枚举所有用户历史数据 | 需开发数据库只读inventory归类，确认真游戏后完整纳入，不能直接忽略 |

cloud模型gameId长度19，data长度500000；SQL/JDBC存储可容许多行，不能假设getFirst是最新或唯一。没有updatedAt/revision，oId只能作为粗略生成顺序，不能当可靠提交版本。

### Evolve实际来源核验

PC与mobile `/games/evolve/index.ftl`均引用CDN `https://file.fishpi.cn/evolve/evolve/main.js`。2026-10-23仅只读下载到 `/tmp/rhythm-evolve-main-audit.js`，大小2306965字节，SHA256 `8517ef2157c040cc081d265e57ebe049a597e0dfff666b5609cb31b82736c42a`；未执行或修改CDN代码。

实际脚本：

- `window.exportGame()`返回`LZString.compressToBase64(JSON.stringify(state))`；`window.importGame`解Base64后校验游戏结构。
- `gameId="evolve-save"`同步完整exportGame字符串；恢复用旧cloud/get的data字符串交importGame。
- `gameId="40"`另提交`{stats: state.stats}`，CloudService JSONObject重载生成top；`ActivityQueryService#getEvolve`读取cloud gameId40排序并展示排行榜。
- 本地main.js不在本仓库源码；仅改Java v3入口不会让现有Evolve自动改用v3。其他真游戏脚本也可能在外部CDN，不能只凭本仓库rg未命中便认定不存在。

仓库article/chat-room/chat/m-article旧cloud调用均查到gameId=emojis，与游戏存档无关；本轮不应据此修改这些旧图片路径。

### 本人文章和评论导出

PC/mobile设置数据页 `skins/classic/{pc,mobile}/home/settings/data.ftl`→共用 `settings.js#exportPosts`→旧 `POST /export/posts`→SettingsProcessor→PostExportService。两端成功均直接window.open返回url。

真实旧导出行为：

- 只按articleAuthorId/commentAuthorId查询，无status/type/anonymous/visible过滤，所以会读取当前仍在主表的本人匿名、讨论、锁定、无效/隐藏等记录。
- 文章只投影id/title/tags/articleContent/createTime，**没有articleRewardContent**；评论只id/commentContent/createTime。没有他人所写正文或本人购买的他人付费区。
- 输出JSONArray，content又是JSON字符串；created误填`Article.ARTICLE_CREATE_TIME`或`Comment.COMMENT_CREATE_TIME`字段名常量，而非真实时间值。
- 查询使用Repository.getList(Query)无显式游标和全量预算，不应默认它完整覆盖任意数量；新实现必须证明完整枚举，不把默认分页当全量。
- 当前pointDataExport配置100积分，`Pointtransfer.TRANSFER_SUM_C_DATA_EXPORT`同共享值。旧先查询余额、读取posts、扣费，再写临时JSON/ZIP并上传；后续文件/图床失败会返回失败而费用已经提交。
- ZIP内只有JSON；同步内存收集、JSON stringify、zip再整个读入内存；临时文件没有完整finally删除。
- RhyPic签用户上传票据上传ZIP，或Qiniu公开域名，或本地`/upload/...`公开资源。返回可直接访问的URL，没有本人鉴权绑定/时效/撤销；日志输出临时路径及图床响应。**不应将私密/匿名完整版照搬到公开图床下载**。

### 已删除和历史版本的实际限制

`ArticleMgmtService#removeArticleDataInCurrentTransaction`物理删文章及关联评论；`CommentRepository#removeComment`物理删除评论并清对应revision。Revision作者字段是编辑者，不等同原帖作者；没有原帖时不能凭编辑历史冒充“本人已删内容”。

因此“已删”必须区分：主表还存在但status无效/锁定（可有策略地导出）与已经物理删除（不能恢复）。若用户要真正回收站/已删正文，必须另建删除前归档/保留规则，不能造空正文或假称现有系统能取回。

## 二、最少需要用户确认的业务问题

主代理可分成下面四个自包含问题，既能完整保留范围，也避免一次问十几项实现细节。以下仅建议，不代表用户已同意。

### 1. 游戏范围、存量迁移与旧客户端衔接

建议确认：v3采用已登记真游戏目录，首批包含Evolve完整`evolve-save`，其他历史真游戏经只读inventory归类后逐个纳入；内部cloud键和emojis禁止作为游戏key。允许仅这些已登记真游戏的旧get/sync接共享新存档核心，旧请求和返回字段保持；旧无版本写沿覆盖语义，新v3写校验版本。`40`排行榜摘要链保留独立，不被完整存档替换。

替代：旧入口暂不改，只提供显式一次性迁移；必须明确此时旧游戏继续读写旧cloud，会与v3分叉，不可宣称兼容完成。不要默默选这个较易实现但语义不同的方案。

数据库inventory未运行。建议仅管理员开发环境执行key/count/size统计，不输出存档正文，也不把内部key目录开放给普通客户端。需审每个候选key的代码/实际游戏来源后登记，不能把“不以sys-开头”当真游戏证明。

### 2. 存档格式、体积和覆盖规则

建议确认：存档作为不清洗、不trim的UTF-8字符串，支持现有Base64/JSON字符串/其他既有真游戏字符串；新协议上限沿现有500000 UTF-16单位且UTF-8最多2MiB。不能强制所有游戏必须JSON对象，否则Evolve现有压缩存档被排除。服务端不抓远程存档URL、不执行游戏内容。

新v3写用expectedRevision（新建0）或等价If-Match；过期版本409不覆盖，成功revision+1；相同幂等键恢复原回执。旧客户端无revision沿原覆盖语义，但统一递增revision，明确不能声称旧客户端本身也具备CAS保护。是否增DELETE/多槽位/多版本备份不应偷偷扩展；首版保持每人每登记游戏一个存档槽的旧能力。

### 3. 本人内容导出覆盖范围

建议确认：包含本人当前仍在主表的全部文章与评论，包含本人匿名、讨论/私密、隐藏/锁定/无效记录，以及**本人文章的付费区原文**；即使本人已失去某讨论帖阅读资格，也只导出自己写过的评论原文，不附他人文章/评论正文或标题资料。排除他人作品（含本人购买的他人付费区）、他人私密评论、IP/UA/联系方式/密钥/2FA等账户资料。

主表已物理删除的内容不伪造/恢复；若用户确实要求回收站和已删内容恢复，需要单列新归档业务，不因当前做不到而从需求描述中省掉。是否包含历次编辑全文也需单独选定，建议首版当前原文+状态/匿名/时间元数据，不混入不同人的编辑归属。无效/删除状态的范围必须用户明确，不能拿“只导出公开有效正文”作为不经确认的缩水默认。

### 4. 格式、费用及私有下载期限

建议确认：ZIP内UTF-8 JSON清单+按固定ID命名的Markdown源码（文章和评论分目录）；包含真实时间/状态/匿名标记、文章正文与付费区分字段，既方便程序导入也便于阅读。不是只返回JSON网页或只打包部分文章。

沿现有导出100积分，仅任务完成且文件就绪时原子扣一次；同key恢复同任务、不重复收费，失败无扣费。下载保留24小时，每次必须本人当前授权，不发布RhyPic/Qiniu/公开upload URL；任务创建建议第一方+当前密码/已有MFA证明，下载至少第一方当前Bearer+本人job/epoch。若要允许第三方导出，需明确独立scope和完整敏感内容授权，而不是笼统content.read。

如果用户选择免费、仅JSON、仅Markdown、不同过期时间或暂不允许第三方，必须按真实回答实现。同步文件生成或异步任务是技术方案；不能因异步难而取消应导出的内容。

## 三、推荐资源契约与不缩水实现路线

### 游戏存档

建议新增登记游戏目录（不列内部cloud键）及：

- `GET /api/v3/me/game-saves/{gameId}`：本人当前数据、revision、updatedAt、data字符串、格式/字节数；无存档404。建议activity.read。
- `PUT /api/v3/me/game-saves/{gameId}`：data、expectedRevision，activity.write+Idempotency-Key；只允许目录已登记gameId，不从body指定owner。
- 若采用独立迁移模式：`POST /api/v3/me/game-saves/{gameId}/migrations`，服务端登记映射到旧key，不接受任意legacyKey；幂等复制，保留源和冲突信息。纯GET不隐式写默认/迁移行。

独立表/Repository建立owner+game唯一槽与revision，避免内部混表；同步SQL与repository.json。不把统计摘要/文章/其他业务当gameId。字符串透明保存，只做类型/Unicode/字节长度，不能Jsoup清洗或trim压缩内容。

先当前锁本人，再锁该game槽，根事务CAS与回执一致；缺slot首建也以owner锁保护，跨实例由DB约束兜底；需要触发积分/排行的额外功能不悄悄放进存档通道。旧桥接时只明确登记映射分支接新核心，其他cloud内务路径不顺手改。

存量迁移不能getFirst选择随机一条：读取完整候选并验证唯一；相同数据重复可做明确的收敛策略，内容不同应409并保留候选给管理员处理，不静默删数据。旧新共存需要公开选择权威来源。仅有新表且照旧无限读fallback，会反复复活旧存档，不能称迁移完成。

### 本人内容导出

建议：

- `POST /api/v3/me/exports/posts`：当前安全证明与Idempotency-Key，返回jobId/status/price；不把证明放普通回执或日志。
- `GET /api/v3/me/exports/posts/{jobId}`：本人任务状态、数量、就绪/失效时间、错误分类；不返任意文件系统路径/签名公开地址。
- `GET /api/v3/me/exports/posts/{jobId}/download`：当前本人授权后流式附件，no-store、nosniff、固定filename；24小时后410/404按最终契约。

服务必须使用字段白名单的本人原文查询，不复用公开文章DTO（会隐藏本人应有原文/付费区），也不复用旧“authorId过滤后裸对象”。评论只自身正文+引用ID，不跟随附上已无权阅读的他人标题或私密正文；匿名身份只在本人受保护导出中关联，不广播。

构造manifest：格式版本、导出时间、actor公开ID（可省略）、每条id/type/created/updated/status/anonymous/body、article rewardContent/price、tags与parent/article引用ID。真实数值时间替代旧字段名常量；Markdown文件名固定`articles/{id}.md` / `comments/{id}.md`，不用title防路径穿越。付费源码独立区域或文件；没有内容不能伪造成空成功。

大体量按ID游标完整分页写流，显式记录真实总数/摘要与snapshot边界；读完整计数与枚举一致，权限/当前账号在关键阶段重验。大数据可异步job，不一次性JSONArray装内存；技术预算超限应明确503/任务失败且不收费，不能截前N当完整导出。不能只用旧getList默认分页。

生成物先写私有随机临时路径，完整校验后形成仅owner-job可取的私有artifact；数据库严格根将扣费/账本索引来源/job READY/回执一并提交。下载必须查READY且owner当前授权；外部文件生成不能跟DB假称同一个原子资源，未提交的私有孤儿文件只能清理、不可公开访问。worker明示lease/重试、幂等收费和JDBC线程释放；失败不扣款/不能把已扣费的失败叫成功。

账户凭据epoch变化时旧访问凭据失效，旧下载授权不得绕过；下载入口不可仅凭可猜jobId/长期bearer URL放行。到期清理文件与job状态要有实际worker，不仅文档上写“有效期”。

## 四、验证与反漏扫要求

本次只读：未改Java/FTL/前端、未跑Maven、未执行数据库统计/迁移、未运行浏览器。CDN脚本只下载到/tmp并读字符串，没有执行或上传。

游戏未来专项至少：Evolve Base64透明round-trip、已有evolve-save迁移、其他已登记真游戏字符串、不合法内部key/他人owner拒绝、重复历史行不截断、并发CAS、旧桥接一致性及旧无version边界、全部写/commit/回执故障零部分状态；不能只测试假设的JSON游戏。

导出未来专项至少：本人普通/匿名/讨论/私密/锁定/无效/付费正文矩阵、他人及已失权限的引用不泄露、真实时间、物理已删不可取的明确结果、超多内容完整分页、文件名穿越、ZIP完整性、同key仅一次收费、各阶段失败无收费、本人下载/他人拒绝、任务过期、凭据撤销、响应和日志不暴露路径或公开URL。

运行前明确提醒用户IDEA重编译/重启；PC和mobile完整入口各跑。新增私有路由必须明确认证；若另做公开目录也需匿名盾。BeforeRequestHandler/AnonymousViewCheckMidware/Captcha解封/anonymous.viewSkips、公开API鉴权、静态/CDN版本号/sourceMappingURL/目录枚举、尤其导出下载路径与图床不可复用，均需实际验证并在最终结果列未验项，不从隔离测试推断整站通过。
