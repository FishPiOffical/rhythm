# /api/v3 当前进度与剩余工作

快照：2026-10-28；以本文件生成时工作区源码为准。后续新增入口须同步重扫，本文件不替代历史批次记录。

## 判定口径

- **已注册源码**：`Router.requestMapping` 调用 `ApiV3Processor.register()`，后者能到达具体 Processor 的 Dispatcher 注册；不把孤立的处理方法、服务类或 API.md 条目计为接口。
- **隔离证据**：历史批次明确记载执行过的 runner；常含 SQLite、仓储/锁/身份/外部服务适配器，不等于真实 MySQL、完整 IoC、浏览器或端到端验证。本轮定向重跑结果见下方证据表，其余历史 runner 未全量重跑。
- **运行验收**：当前没有本次可确认的完整应用启动、迁移、真实 HTTP/Socket、PC/mobile 与第三方客户端全链路验收证据。下表所有源码接口均仍需运行验收。
- 当前范围排除管理后台、金手指、内部回调、游戏计分和支付管理；公开支付跳转仍在范围。用户侧职业、勋章、会员能力不能仅因同模块存在后台接口就整体排除。OAuth管理员登记/管理为用户明确纳入的例外，其余后台仍排除。

## 当前数量

已注册 **290 条（289 HTTP + 1 WebSocket）**；HTTP按 METHOD+完整path计数；GET/PUT/DELETE共用路径仍分别计数。方法分布：DELETE 24, GET 147, POST 72, PUT 46, WEBSOCKET 1。

连接链路：`processor/Router.java#requestMapping` → `processor/ApiV3Processor.java#register` → 各模块 `register`。以下路径均包含完整 `/api/v3` 前缀，代码位置以生成时行号为准。

注意：当前生成的“全仓库837路由、398浏览器引用”是不同口径，包含旧HTTP/WS等，不能当作v3接口数。静态注册成功也不证明完整IoC启动成功。

## 已注册接口清单

### 当前授权勋章图片（2）

`ApiV3MedalImageProcessor`总register已接，均profile.read。本人有效持有含隐藏；公开路径必须指定用户当前有效展示；SVG清洗、前后当前SQL核对、不返回原配置。核心隔离8组及真实Dispatcher/中间件/controller隔离1组，真实部署待验。

- `GET /api/v3/me/medals/image`
- `GET /api/v3/users/{userName}/medals/image`


### 最近注册、随机发现与公开日志（3）

代码：`processor/ApiV3DiscoveryExtrasProcessor.java`；总register可达。实际Java25/SQLite10组及源码接线1组，完整运行未验收，详见[本批记录](api-v3-discovery-extras-work.md)。

| 方法 | 路径 | 行号 | 门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/recent-members` | 21 | optionalProfile、匿名验证 |
| GET | `/api/v3/articles/random` | 22 | optionalContent、匿名验证 |
| GET | `/api/v3/logs` | 23 | activity.read |


### 思过崖（1）

`GET /api/v3/chat/mutes`：ApiV3ChatMuteProcessor，总register可达，content.read。按实际sys-mute和有效账号ID关联、过滤未到期、解除时间/ID排序分页；仅五公开字段，坏状态或失败503。SQLite/MySQL各5组及客户端11组通过，完整应用待验收，见[工作记录](api-v3-chat-mutes-work.md)。

### 标签前缀联想（1）

`GET /api/v3/suggestions/tags`：ApiV3TagSuggestionProcessor，总register可达，content.read。用户确认的trim后64单位/现有标签字符范围/大小写不敏感/最多7；有效且有实际文章关联，空前缀热门。四组SQLite+四组MySQL及客户端9组通过，完整服务待验收。

### 用户名前缀联想（1）

`GET /api/v3/suggestions/users`：ApiV3UserSuggestionProcessor，总register可达，profile.read。按用户确认的ASCII前缀1..64/忽略大小写/有效账号/最多10/四公开字段；空前缀空列表，不复用旧缓存。四组SQLite及四组MySQL实际SQL/HTTP专项通过；完整应用运行待验收。

### Markdown预览（1）

`POST /api/v3/markdown/preview`：`ApiV3MarkdownProcessor`，总register可达，content.read。原始UTF8/严格JSON/正文边界，独立预览预算和并发限制；仅本地私有Markdown转换及清洗，不保存/扣费。四组Java25实际HTTP与提取原私有渲染方法通过；完整IoC/真实服务验收待做。

### 评论感谢及采纳状态（2）

代码：`processor/ApiV3CommentRewardProcessor.java`，总register已接。当前有效账号/评论与文章可见性，私密评论限双方，匿名费用0；资格仅提示，提交仍重验。隔离Java/实际路由与SQLite/MySQL通过，完整应用运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/comments/{commentId}/thanks` | 20 | `processor::thanks,auth::contentRead` |
| GET | `/api/v3/comments/{commentId}/accept` | 21 | `processor::accept,auth::contentRead` |


### Evolve 游戏云存档（4）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3GameSaveProcessor.java`；总register已接。仅Evolve单槽，版本/幂等/显式迁移与登记游戏旧桥接；隔离Java/HTTP与实际MySQL通过，运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/game-save-options` | 22 | `p::options,auth::activityRead` |
| GET | `/api/v3/me/game-saves/{gameId}` | 23 | `p::get,auth::activityRead` |
| PUT | `/api/v3/me/game-saves/{gameId}` | 24 | `p::save,auth::activityWrite,writes::handle` |
| POST | `/api/v3/me/game-saves/{gameId}/migrations` | 25 | `p::migrate,auth::activityWrite,writes::handle` |


### 本人QQ与个人标签设置（4）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ProfileSettingsProcessor.java`，总register已接；源码与SQLite/HTTP隔离通过，标签另有一次性MySQL及独立JVM并发证据，真实应用运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/settings/qq` | 32 | `p::qq,auth::profileRead`；服务内当前第一方本人 |
| PUT | `/api/v3/me/settings/qq` | 33 | `p::updateQq,auth::settingsWrite,writes::handle`；服务内当前第一方本人 |
| GET | `/api/v3/me/settings/tags` | 34 | `p::tags,auth::profileRead`；本人当前有效账号 |
| PUT | `/api/v3/me/settings/tags` | 35 | `p::updateTags,auth::settingsWrite,writes::handle`；本人严格事务与回执 |

每个分组显示实际 Dispatcher 所在文件；“处理器与门禁”来自注册表达式，仅用于定位，不替代方法内授权审查。`WEBSOCKET` 是协议入口，不是普通GET资源。

### 注册与找回密码（6）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3AccountProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/auth/registration-options` | 38 | `p::registrationOptions,anonymous::handle` |
| GET | `/api/v3/auth/captcha-options` | 39 | `p::captchaOptions,anonymous::handle` |
| POST | `/api/v3/auth/password-reset/complete` | 40 | `p::completePasswordReset,anonymous::handle` |
| POST | `/api/v3/auth/password-reset/request` | 41 | `p::requestPasswordReset,anonymous::handle` |
| POST | `/api/v3/auth/registration/request` | 42 | `p::requestRegistration,anonymous::handle` |
| POST | `/api/v3/auth/registration/complete` | 43 | `p::completeRegistration,anonymous::handle` |

### 活跃奖励（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ActivityProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/activity/yesterday-reward` | 24 | ` p::status, auth::activityRead` |
| POST | `/api/v3/me/activity/yesterday-reward` | 25 | ` p::claim, auth::activityWrite, writes::handle` |

### 文章热度（本轮接线）（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ArticleHeatProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/articles/{articleId}/heat` | 20 | ` p::heat, auth::contentRead` |

### 文章感谢与打赏（4）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ArticleRewardProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/articles/{articleId}/thanks` | 23 | ` processor::thanks, auth::contentRead` |
| GET | `/api/v3/articles/{articleId}/reward` | 24 | ` processor::reward, auth::contentRead` |
| POST | `/api/v3/articles/{articleId}/thanks` | 25 | ` processor::thank, auth::contentWrite, writes::handle` |
| POST | `/api/v3/articles/{articleId}/reward` | 26 | ` processor::purchase, auth::contentWrite, writes::handle` |

### 企业认证申请与材料（6）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3IdentityApplicationProcessor.java`。状态：已注册源码，先读图再给结论；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/identity-applications/enterprise` | 25 | `p::create,auth::settingsWrite,writes::handle` |
| GET | `/api/v3/me/identity-applications` | 26 | `p::list,auth::profileRead` |
| GET | `/api/v3/me/identity-applications/{applicationId}` | 27 | `p::detail,auth::profileRead` |
| GET | `/api/v3/review/identity-applications` | 28 | `p::reviewList,auth::profileRead` |
| POST | `/api/v3/review/identity-applications/{applicationId}/material/open` | 29 | `p::open,auth::settingsWrite,writes::handle` |
| PUT | `/api/v3/review/identity-applications/{applicationId}/decision` | 30 | `p::decide,auth::settingsWrite,writes::handle` |

### 作者付费置顶（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ArticleStickProcessor.java`。已注册源码，真实运行待验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/articles/{articleId}/stick` | 19 | `processor::stick, auth::contentWrite, auth::activityWrite, writes::handle` |

### 文章创作（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ArticleWriteProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/articles` | 26 | ` processor::create, auth::contentWrite, writes::handle` |
| PUT | `/api/v3/articles/{articleId}` | 27 | ` processor::update, auth::contentWrite, writes::handle` |
| DELETE | `/api/v3/articles/{articleId}` | 28 | ` processor::remove, auth::contentWrite, writes::handle` |

### 背包使用（4）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3BagProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/bag/checkin1day/use` | 24 | ` p::oneDay, auth::activityWrite, writes::handle` |
| POST | `/api/v3/me/bag/checkin2days/use` | 25 | ` p::twoDays, auth::activityWrite, writes::handle` |
| POST | `/api/v3/me/bag/patchCheckinCard/use` | 26 | ` p::patch, auth::activityWrite, writes::handle` |
| POST | `/api/v3/me/bag/nameCard/use` | 27 | ` p::name, auth::activityWrite, auth::settingsWrite, writes::handle` |

### 清风明月详情及写入（5）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3BreezemoonProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/breezemoons/{breezemoonId}` | 33 | ` p::detail, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/me/watching/breezemoons` | 34 | ` p::following, auth::contentRead` |
| PUT | `/api/v3/breezemoons/{breezemoonId}` | 35 | ` p::update, auth::contentWrite, writes::handle` |
| POST | `/api/v3/breezemoons` | 36 | ` p::create, auth::contentWrite, writes::handle` |
| DELETE | `/api/v3/breezemoons/{breezemoonId}` | 37 | ` p::remove, auth::contentWrite, writes::handle` |

### 聊天室（12）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ChatProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/chat/messages` | 32 | `p::create,auth::contentWrite,writes::handle` |
| POST | `/api/v3/chat/music` | 33 | `p::sendMusic,auth::contentWrite,writes::handle` |
| POST | `/api/v3/chat/weather` | 34 | `p::sendWeather,auth::contentWrite,writes::handle` |
| DELETE | `/api/v3/chat/messages/{messageId}` | 35 | `p::withdraw,auth::contentWrite,writes::handle` |
| GET | `/api/v3/chat/messages` | 36 | `p::list,auth::contentRead` |
| GET | `/api/v3/chat/online-users` | 37 | `p::online,auth::contentRead` |
| GET | `/api/v3/chat/topic` | 38 | `p::topic,auth::contentRead` |
| PUT | `/api/v3/chat/topic` | 39 | `p::setTopic,auth::contentWrite,writes::handle` |
| POST | `/api/v3/chat/barrager` | 40 | `p::sendBarrage,auth::contentWrite,writes::handle` |
| GET | `/api/v3/chat/barrager/price` | 41 | `p::barragerPrice,auth::contentRead` |
| GET | `/api/v3/chat/messages/{messageId}` | 42 | `p::detail,auth::contentRead` |
| GET | `/api/v3/chat/messages/{messageId}/context` | 43 | `p::context,auth::contentRead` |

### 专栏管理（4）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ColumnProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| PUT | `/api/v3/columns/{columnId}/title` | 26 | ` p::title, auth::contentWrite, writes::handle` |
| PUT | `/api/v3/columns/{columnId}/cover` | 27 | ` p::cover, auth::contentWrite, writes::handle` |
| PUT | `/api/v3/me/columns/order` | 28 | ` p::order, auth::contentWrite, writes::handle` |
| DELETE | `/api/v3/columns/{columnId}` | 29 | ` p::remove, auth::contentWrite, writes::handle` |

### 评论写入及投票（8）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3CommentProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/articles/{articleId}/comments` | 27 | ` processor::create, auth::commentsWrite, writes::handle` |
| PUT | `/api/v3/comments/{commentId}` | 28 | ` processor::update, auth::commentsWrite, writes::handle` |
| DELETE | `/api/v3/comments/{commentId}` | 29 | ` processor::remove, auth::commentsWrite, writes::handle` |
| POST | `/api/v3/comments/{commentId}/thanks` | 30 | ` processor::thank, auth::commentsWrite, writes::handle` |
| POST | `/api/v3/comments/{commentId}/accept` | 31 | ` processor::accept, auth::commentsWrite, writes::handle` |
| GET | `/api/v3/comments/{commentId}/vote` | 32 | ` processor::vote, auth::contentRead` |
| PUT | `/api/v3/comments/{commentId}/vote` | 33 | ` processor::setVote, auth::commentsWrite, writes::handle` |
| DELETE | `/api/v3/comments/{commentId}/vote` | 34 | ` processor::clearVote, auth::commentsWrite, writes::handle` |

### 鱼游、大事记、同城与统计读取（8）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3CommunityProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/fish-games` | 23 | ` processor::games, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/fish-games/{gameId}/comments` | 24 | ` processor::gameComments, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/fish-games/{gameId}` | 25 | ` processor::game, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/milestones` | 26 | ` processor::milestones, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/milestones/{milestoneId}` | 27 | ` processor::milestone, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/statistics` | 28 | ` processor::statistics, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/cities/{cityName}/users` | 29 | ` processor::cityUsers, auth::optionalProfile, anonymous::handle` |
| GET | `/api/v3/cities/{cityName}/articles` | 30 | ` processor::cityArticles, auth::optionalContent, anonymous::handle` |

### 文章与评论读取（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ContentProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/articles` | 30 | ` processor::articles, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/articles/{articleId}/comments` | 31 | ` processor::comments, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/articles/{articleId}` | 32 | ` processor::article, auth::optionalContent, anonymous::handle` |

### 搜索、标签、领域、主页与清风明月列表（19）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3DiscoveryProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/search` | 38 | ` processor::search, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/tags` | 39 | ` processor::tags, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/tags/{tagId}/articles` | 40 | ` processor::tagArticles, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/tags/{tagId}` | 41 | ` processor::tag, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/domains` | 42 | ` processor::domains, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/domains/{domainId}/tags` | 43 | ` processor::domainTags, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/domains/{domainId}/articles` | 44 | ` processor::domainArticles, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/domains/{domainId}` | 45 | ` processor::domain, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/users/{userName}/comments` | 46 | ` processor::userComments, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/users/{userName}/watching/articles` | 47 | ` processor::watchedArticles, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/users/{userName}/watching` | 48 | ` processor::watching, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/users/{userName}/articles` | 49 | ` processor::userArticles, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/users/{userName}/breezemoons` | 50 | ` processor::userBreezemoons, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/users/{userName}/following/users` | 51 | ` processor::followingUsers, auth::optionalProfile, anonymous::handle` |
| GET | `/api/v3/users/{userName}/following/tags` | 52 | ` processor::followingTags, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/users/{userName}/following/articles` | 53 | ` processor::followingArticles, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/users/{userName}/followers` | 54 | ` processor::followers, auth::optionalProfile, anonymous::handle` |
| GET | `/api/v3/users/{userName}` | 55 | ` processor::user, auth::optionalProfile, anonymous::handle` |
| GET | `/api/v3/breezemoons` | 56 | ` processor::breezemoons, auth::optionalContent, anonymous::handle` |

### 草稿写入（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3DraftProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/drafts` | 28 | ` processor::create, auth::contentWrite, writes::handle` |
| PUT | `/api/v3/me/drafts/{draftId}` | 29 | ` processor::update, auth::contentWrite, writes::handle` |
| DELETE | `/api/v3/me/drafts/{draftId}` | 30 | ` processor::remove, auth::contentWrite, writes::handle` |

### 本人表情设置与分组读取（本轮接线，4）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3EmotionProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/settings/emotions` | 22 | ` p::emotions, auth::profileRead` |
| GET | `/api/v3/me/settings/emotion-options` | 23 | ` p::options, auth::profileRead` |
| GET | `/api/v3/me/settings/emoji-groups` | 24 | ` p::groups, auth::profileRead` |
| GET | `/api/v3/me/settings/emoji-groups/{groupId}/emojis` | 25 | ` p::items, auth::profileRead` |

### 鱼游投票及评论（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3FishGameProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| PUT | `/api/v3/fish-games/{gameId}/vote` | 26 | ` p::vote, auth::reactionsWrite, writes::handle` |
| DELETE | `/api/v3/fish-games/{gameId}/vote` | 27 | ` p::clearVote, auth::reactionsWrite, writes::handle` |
| POST | `/api/v3/fish-games/{gameId}/comments` | 28 | ` p::comment, auth::commentsWrite, writes::handle` |

### 关注、收藏及订阅（16）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3FollowProcessor.java`。四类单项状态GET均已接线；标签/文章三项需content.read，当前有效本人及标签/文章读取边界，隔离SQL/HTTP与一次性MySQL通过；完整IoC/真实账号仍未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/follows/users` | 34 | `p::users, auth::profileRead` |
| GET | `/api/v3/me/follows/users/{targetId}` | 35 | `p::userState, auth::profileRead` |
| GET | `/api/v3/me/follows/tags` | 36 | `p::tags, auth::contentRead` |
| GET | `/api/v3/me/follows/tags/{targetId}` | 37 | `p::tagState, auth::contentRead` |
| GET | `/api/v3/me/collections/{targetId}` | 38 | `p::collectionState, auth::contentRead` |
| GET | `/api/v3/me/subscriptions/{targetId}` | 39 | `p::subscriptionState, auth::contentRead` |
| GET | `/api/v3/me/collections` | 40 | `p::collections, auth::contentRead` |
| GET | `/api/v3/me/subscriptions` | 41 | `p::subscriptions, auth::contentRead` |
| PUT | `/api/v3/me/follows/users/{targetId}` | 42 | `p::followUser, auth::followsWrite, writes::handle` |
| DELETE | `/api/v3/me/follows/users/{targetId}` | 43 | `p::unfollowUser, auth::followsWrite, writes::handle` |
| PUT | `/api/v3/me/follows/tags/{targetId}` | 44 | `p::followTag, auth::followsWrite, writes::handle` |
| DELETE | `/api/v3/me/follows/tags/{targetId}` | 45 | `p::unfollowTag, auth::followsWrite, writes::handle` |
| PUT | `/api/v3/me/collections/{targetId}` | 46 | `p::collect, auth::followsWrite, writes::handle` |
| DELETE | `/api/v3/me/collections/{targetId}` | 47 | `p::uncollect, auth::followsWrite, writes::handle` |
| PUT | `/api/v3/me/subscriptions/{targetId}` | 48 | `p::subscribe, auth::followsWrite, writes::handle` |
| DELETE | `/api/v3/me/subscriptions/{targetId}` | 49 | `p::unsubscribe, auth::followsWrite, writes::handle` |

### 大事记投稿（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3MilestoneProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/milestones` | 27 | ` p::create, auth::contentWrite, writes::handle` |
| GET | `/api/v3/me/milestones` | 28 | ` p::list, auth::contentRead` |
| GET | `/api/v3/me/milestones/{milestoneId}` | 29 | ` p::detail, auth::contentRead` |

### 通知（7）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3NotificationProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/notifications` | 35 | ` processor::list, auth::notificationsRead` |
| GET | `/api/v3/me/notifications/unread-count` | 36 | ` processor::unread, auth::notificationsRead` |
| GET | `/api/v3/me/notifications/{notificationId}` | 37 | ` processor::get, auth::notificationsRead` |
| POST | `/api/v3/me/notifications/{notificationId}/read` | 38 | ` processor::mark, auth::notificationsWrite, writes::handle` |
| POST | `/api/v3/me/notifications/read` | 39 | ` processor::markCategory, auth::notificationsWrite, writes::handle` |
| DELETE | `/api/v3/me/notifications/{notificationId}` | 40 | ` processor::remove, auth::notificationsWrite, writes::handle` |
| DELETE | `/api/v3/me/notifications` | 41 | ` processor::removeCategory, auth::notificationsWrite, writes::handle` |

### OAuth授权协议（4）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3OAuthProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/oauth/authorize` | 42 | `p::authorize,p::privacy,p::rate,anonymous::handle` |
| POST | `/api/v3/oauth/consent` | 43 | `p::consent,p::privacy,p::rate,p::cookieOnly` |
| POST | `/api/v3/oauth/token` | 44 | `p::token,p::privacy,p::rate,p::protocolToken` |
| POST | `/api/v3/oauth/revoke` | 45 | `p::revoke,p::privacy,p::rate,p::protocolRevoke` |

### 段评、段落及评论详情（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ParagraphProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/articles/{articleId}/paragraphs` | 23 | ` processor::paragraphs, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/articles/{articleId}/paragraph-comments` | 24 | ` processor::comments, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/comments/{commentId}` | 25 | ` processor::comment, auth::optionalContent, anonymous::handle` |

### 本人设置、资产及创作读取（8）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3PersonalProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/settings` | 22 | ` processor::settings, auth::profileRead` |
| GET | `/api/v3/me/checkin` | 23 | ` processor::checkin, auth::activityRead` |
| GET | `/api/v3/me/bag` | 24 | ` processor::bag, auth::activityRead` |
| GET | `/api/v3/me/points` | 25 | ` processor::points, auth::activityRead` |
| GET | `/api/v3/me/drafts` | 26 | ` processor::drafts, auth::contentRead` |
| GET | `/api/v3/me/drafts/{draftId}` | 27 | ` processor::draft, auth::contentRead` |
| GET | `/api/v3/me/columns` | 28 | ` processor::columns, auth::contentRead` |
| GET | `/api/v3/me/articles/{articleId}/source` | 29 | ` processor::articleSource, auth::contentRead` |

### 私信（9）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3PrivateMessageProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/conversations` | 23 | `p::list,auth::messagesRead` |
| GET | `/api/v3/me/conversations/{peerId}` | 24 | `p::conversation,auth::messagesRead` |
| GET | `/api/v3/me/conversations/{peerId}/messages` | 25 | `p::history,auth::messagesRead` |
| GET | `/api/v3/me/messages/unread-count` | 26 | `p::unread,auth::messagesRead` |
| GET | `/api/v3/me/messages/{messageId}` | 27 | `p::message,auth::messagesRead` |
| POST | `/api/v3/me/conversations/{peerId}/messages` | 28 | `p::send,auth::messagesWrite,writes::handle` |
| POST | `/api/v3/me/conversations/{peerId}/read` | 29 | `p::markRead,auth::messagesWrite,writes::handle` |
| POST | `/api/v3/me/conversations/read` | 30 | `p::markAllRead,auth::messagesWrite,writes::handle` |
| DELETE | `/api/v3/me/messages/{messageId}` | 31 | `p::withdraw,auth::messagesWrite,writes::handle` |

### 认证、本人及公开专栏（8）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/auth/login` | 118 | ` processor::login, anonymous::handle` |
| POST | `/api/v3/auth/refresh` | 119 | ` processor::refresh, anonymous::handle` |
| POST | `/api/v3/auth/logout` | 120 | ` processor::logout, anonymous::handle` |
| GET | `/api/v3/me` | 121 | ` processor::me, auth::handle` |
| GET | `/api/v3/columns` | 131 | ` processor::columns, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/columns/hot` | 132 | ` processor::hotColumns, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/columns/{columnId}/articles` | 133 | ` processor::columnArticles, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/columns/{columnId}` | 134 | ` processor::column, auth::optionalContent, anonymous::handle` |

### 排行榜（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3RankingProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/rankings/{type}` | 22 | ` processor::ranking, auth::optionalContent, anonymous::handle` |

### emoji reaction（10）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ReactionProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/reactions/emojis` | 28 | ` p::emojis, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/articles/{targetId}/reactions` | 29 | ` p::article, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/comments/{targetId}/reactions` | 30 | ` p::comment, auth::optionalContent, anonymous::handle` |
| GET | `/api/v3/chat/messages/{targetId}/reactions` | 31 | ` p::chat, auth::contentRead` |
| PUT | `/api/v3/articles/{targetId}/reactions` | 32 | ` p::setArticle, auth::reactionsWrite, writes::handle` |
| DELETE | `/api/v3/articles/{targetId}/reactions` | 33 | ` p::clearArticle, auth::reactionsWrite, writes::handle` |
| PUT | `/api/v3/comments/{targetId}/reactions` | 34 | ` p::setComment, auth::reactionsWrite, writes::handle` |
| DELETE | `/api/v3/comments/{targetId}/reactions` | 35 | ` p::clearComment, auth::reactionsWrite, writes::handle` |
| PUT | `/api/v3/chat/messages/{targetId}/reactions` | 36 | ` p::setChat, auth::reactionsWrite, writes::handle` |
| DELETE | `/api/v3/chat/messages/{targetId}/reactions` | 37 | ` p::clearChat, auth::reactionsWrite, writes::handle` |

### 实时取票及Socket（5）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3RealtimeProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/realtime/tickets` | 39 | `p::ticket,auth::notificationsRead,writes::handle` |
| POST | `/api/v3/articles/{articleId}/realtime-ticket` | 40 | `p::articleTicket,auth::contentRead,writes::handle` |
| POST | `/api/v3/chat/realtime-ticket` | 41 | `p::chatTicket,auth::contentRead,writes::handle` |
| POST | `/api/v3/me/messages/realtime-ticket` | 42 | `p::messagesTicket,auth::messagesRead,writes::handle` |
| WEBSOCKET | `/api/v3/realtime` | 43 | `beans.getReference(ApiV3RealtimeChannel.class)` |

### 红包类型目录（本轮接线，1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3RedPacketProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/chat/red-packet-types` | 18 | ` processor::types, auth::contentRead` |

### 个人设置（19）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3SettingsProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/settings/personality` | 30 | ` p::personality, auth::profileRead` |
| GET | `/api/v3/me/settings/personality-options` | 31 | ` p::personalityOptions, auth::profileRead` |
| GET | `/api/v3/me/settings/avatar` | 32 | ` p::avatar, auth::profileRead` |
| GET | `/api/v3/me/settings/profile` | 33 | ` p::profile, auth::profileRead` |
| GET | `/api/v3/me/settings/privacy` | 34 | ` p::privacy, auth::profileRead` |
| GET | `/api/v3/me/settings/display` | 35 | ` p::display, auth::profileRead` |
| GET | `/api/v3/me/settings/skins` | 36 | ` p::skins, auth::profileRead` |
| GET | `/api/v3/me/settings/functions` | 37 | ` p::functions, auth::profileRead` |
| GET | `/api/v3/me/settings/location` | 38 | ` p::location, auth::profileRead` |
| GET | `/api/v3/me/settings/locale` | 39 | ` p::locale, auth::profileRead` |
| GET | `/api/v3/me/settings/locale-options` | 40 | ` p::localeOptions, auth::profileRead` |
| PUT | `/api/v3/me/settings/personality` | 41 | ` p::updatePersonality, auth::settingsWrite, writes::handle` |
| PUT | `/api/v3/me/settings/avatar` | 42 | ` p::updateAvatar, auth::settingsWrite, writes::handle` |
| PUT | `/api/v3/me/settings/functions` | 43 | ` p::updateFunctions, auth::settingsWrite, writes::handle` |
| PUT | `/api/v3/me/settings/location` | 44 | ` p::updateLocation, auth::settingsWrite, writes::handle` |
| PUT | `/api/v3/me/settings/locale` | 45 | ` p::updateLocale, auth::settingsWrite, writes::handle` |
| PUT | `/api/v3/me/settings/profile` | 46 | ` p::updateProfile, auth::settingsWrite, writes::handle` |
| PUT | `/api/v3/me/settings/privacy` | 47 | ` p::updatePrivacy, auth::settingsWrite, writes::handle` |
| PUT | `/api/v3/me/settings/display` | 48 | ` p::updateDisplay, auth::settingsWrite, writes::handle` |

### 普通积分改名（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3UserNameProcessor.java`。状态：已注册源码；运行未验收。完整文件、费用来源、人工步骤与隔离测试限制见 [改名工作记录](api-v3-user-name-work.md)。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| PUT | `/api/v3/me/settings/user-name` | 16 | `p::rename,auth::activityWrite,auth::settingsWrite,writes::handle` |

### 上传签票（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3UploadProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/uploads/tickets` | 24 | ` processor::ticket, auth::uploadsWrite, writes::handle` |

### 文章投票（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3VoteProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/articles/{articleId}/vote` | 23 | ` processor::read, auth::contentRead` |
| PUT | `/api/v3/articles/{articleId}/vote` | 24 | ` processor::set, auth::contentWrite, writes::handle` |
| DELETE | `/api/v3/articles/{articleId}/vote` | 25 | ` processor::clear, auth::contentWrite, writes::handle` |

### 职业（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ProfessionProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/professions` | 23 | ` p::catalog, auth::optionalProfile, anonymous::handle` |
| GET | `/api/v3/me/professions` | 24 | ` p::mine, auth::profileRead` |

### 勋章（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3MedalProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/medals` | 23 | ` p::mine, auth::profileRead` |
| GET | `/api/v3/users/{userName}/medals` | 24 | ` p::displayed, auth::optionalProfile, anonymous::handle` |

### 会员（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3MembershipProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/membership` | 22 | ` processor::me, auth::profileRead` |
| GET | `/api/v3/membership/levels` | 23 | ` processor::levels, auth::contentRead` |

### 主职业设置（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ProfessionPrimaryProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| PUT | `/api/v3/me/professions/primary` | 25 | ` p::set, auth::settingsWrite, writes::handle` |

### 勋章显示设置（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3MedalDisplayProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| PUT | `/api/v3/me/medals/{medalId}/display` | 23 | ` p::display, auth::settingsWrite, writes::handle` |

### OAuth客户端管理（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ClientManagementProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/admin/oauth/clients` | 28 | `p::list,auth::settingsWrite,p::administrator` |
| GET | `/api/v3/admin/oauth/clients/{clientId}` | 29 | `p::detail,auth::settingsWrite,p::administrator` |
| POST | `/api/v3/admin/oauth/clients` | 30 | `p::create,auth::settingsWrite,p::administrator,writes::handle` |

### OAuth客户端停用（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ClientDisableProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| DELETE | `/api/v3/admin/oauth/clients/{clientId}` | 32 | ` p::disable, auth::settingsWrite, p::administrator, writes::handle` |

### 邀请码状态（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3InvitationProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/invitation-codes/status` | 28 | ` p::status, auth::activityRead, budget::handle` |

### 本人匿名内容（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3AnonymousContentProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/anonymous-articles` | 21 | ` p::articles, auth::contentRead` |
| GET | `/api/v3/me/anonymous-comments` | 22 | ` p::comments, auth::contentRead` |

### OAuth客户端修改（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ClientUpdateProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| PUT | `/api/v3/admin/oauth/clients/{clientId}` | 30 | ` p::update, auth::settingsWrite, p::administrator, writes::handle` |

### 本人职业详情与记录（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ProfessionDetailProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/professions/{professionId}` | 21 | ` p::detail, auth::profileRead` |
| GET | `/api/v3/me/professions/{professionId}/records` | 22 | ` p::records, auth::profileRead` |

### 本人职业引导与隐私（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ProfessionSettingsProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| PUT | `/api/v3/me/professions/onboarding` | 29 | ` p::onboarding, auth::settingsWrite, writes::handle` |
| PUT | `/api/v3/me/professions/privacy` | 30 | ` p::privacy, auth::settingsWrite, writes::handle` |

### 本人勋章排序（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3MedalOrderProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/medals/{medalId}/move` | 23 | ` p::move, auth::settingsWrite, writes::handle` |

### 聊天室红包（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ChatRedPacketProcessor.java`。状态：已注册源码；运行未验收。先执行20261024_api_v3_chat_red_packet.sql。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/chat/red-packets` | 16 | `processor::create,auth::contentWrite,auth::activityWrite,writes::handle` |
| GET | `/api/v3/chat/red-packets/{id}` | 17 | `processor::detail,auth::contentRead` |
| POST | `/api/v3/chat/red-packets/{id}/claims` | 18 | `processor::claim,auth::activityWrite,writes::handle` |

### 本人内容导出（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3PostExportProcessor.java`。状态：已注册源码；运行未验收。先执行20261024_api_v3_post_export.sql。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/exports/posts` | 17 | `p::create,auth::settingsWrite,auth::profileRead,writes::handle` |
| GET | `/api/v3/me/exports/posts/{jobId}` | 18 | `p::status,auth::profileRead` |
| GET | `/api/v3/me/exports/posts/{jobId}/download` | 19 | `p::download,auth::profileRead` |

### 会员昵称样式与捐助页（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3MembershipStyleProcessor.java`、`ApiV3DonationProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/membership/styles` | 14 | `p::styles,auth::profileRead` |
| GET | `/api/v3/payments/donation-page` | 20 | ` processor::page, auth::activityRead` |

### 本人会员配置（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3MembershipSettingsProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| PUT | `/api/v3/me/membership/config` | 25 | `p::update,auth::settingsWrite,writes::handle` |

### 积分开通本人会员（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3MembershipOpeningProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/membership` | 26 | ` p::open, auth::activityWrite, writes::handle` |

### 职业成长提示（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ProfessionGuideProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/professions/{professionId}/guides` | 21 | ` p::guides, auth::profileRead` |

### 职业排行（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ProfessionRankingProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/professions/{professionId}/ranking` | 21 | ` p::ranking, auth::profileRead` |

### 用户公开职业资料（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ProfessionPublicProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/users/{userName}/professions` | 25 | `p::profile,auth::optionalProfile,anonymous::handle` |

### 本人常用表情设置（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3EmotionSettingsProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| PUT | `/api/v3/me/settings/emotions` | 25 | ` p::update, auth::settingsWrite, writes::handle` |

### 复读机读取（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3RepeaterProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/repeater/items` | 20 | `p::items,auth::optionalContent,anonymous::handle` |
| GET | `/api/v3/repeater/next` | 21 | `p::next,auth::optionalContent,anonymous::handle` |

### 本人邀请码购买（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3InvitationPurchaseProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/invitation-codes` | 26 | ` p::purchase, auth::activityWrite, writes::handle` |

### 复读机投稿（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3RepeaterPublicationProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/repeater/items` | 21 | `p::publish,auth::contentWrite,writes::handle` |

### 复读机点赞设置（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3RepeaterLikeProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| PUT | `/api/v3/repeater/items/{itemId}/like` | 21 | `p::like,auth::contentWrite,writes::handle` |
| DELETE | `/api/v3/repeater/items/{itemId}/like` | 22 | `p::unlike,auth::contentWrite,writes::handle` |

### 举报分类与提交（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ReportProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/report/options` | 25 | `p::options,auth::contentRead` |
| POST | `/api/v3/reports` | 26 | `p::publish,auth::contentWrite,writes::handle` |

### 积分转账（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3PointTransferProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/point-transfers` | 25 | ` p::transfer, auth::activityWrite, writes::handle` |

### 文章与评论历史（4）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3RevisionProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/articles/{articleId}/revisions` | 16 | `p::articles,auth::contentRead` |
| GET | `/api/v3/articles/{articleId}/revisions/{revisionId}` | 17 | `p::article,auth::contentRead` |
| GET | `/api/v3/comments/{commentId}/revisions` | 18 | `p::comments,auth::contentRead` |
| GET | `/api/v3/comments/{commentId}/revisions/{revisionId}` | 19 | `p::comment,auth::contentRead` |

### 图片表情分组写入（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3EmojiGroupProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/settings/emoji-groups` | 23 | ` p::create, auth::settingsWrite, writes::handle` |
| PUT | `/api/v3/me/settings/emoji-groups/{groupId}` | 24 | ` p::update, auth::settingsWrite, writes::handle` |
| DELETE | `/api/v3/me/settings/emoji-groups/{groupId}` | 25 | ` p::delete, auth::settingsWrite, writes::handle` |

### 图片表情成员写入（4）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3EmojiItemProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/settings/emojis` | 22 | ` p::upload, auth::settingsWrite, writes::handle` |
| POST | `/api/v3/me/settings/emoji-groups/{groupId}/emojis` | 23 | ` p::add, auth::settingsWrite, writes::handle` |
| PUT | `/api/v3/me/settings/emoji-groups/{groupId}/emojis/{itemId}` | 24 | ` p::update, auth::settingsWrite, writes::handle` |
| DELETE | `/api/v3/me/settings/emoji-groups/{groupId}/emojis/{itemId}` | 25 | ` p::remove, auth::settingsWrite, writes::handle` |

### 鱼游投稿与编辑（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3FishGameSubmissionProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/fish-games` | 23 | `p::submit,auth::contentWrite,writes::handle` |
| PUT | `/api/v3/fish-games/{gameId}` | 24 | `p::edit,auth::contentWrite,writes::handle` |

### 图片表情分享、导入和迁移（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3EmojiTransferProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/settings/emoji-groups/{groupId}/shares` | 22 | ` p::share, auth::settingsWrite, writes::handle` |
| POST | `/api/v3/me/settings/emoji-group-imports` | 23 | ` p::importShare, auth::settingsWrite, writes::handle` |
| POST | `/api/v3/me/settings/emoji-migrations` | 24 | ` p::migrate, auth::settingsWrite, writes::handle` |

### 账号安全读取与密码（2）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3SecurityProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/me/security/mfa` | 17 | `p::mfa,auth::profileRead` |
| PUT | `/api/v3/me/settings/password` | 18 | `p::password,auth::settingsWrite,writes::handle` |

### MFA准备与启停（3）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3MfaProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/security/mfa/preparations` | 17 | `p::prepare,auth::settingsWrite,writes::handle` |
| PUT | `/api/v3/me/security/mfa` | 18 | `p::enable,auth::settingsWrite,writes::handle` |
| DELETE | `/api/v3/me/security/mfa` | 19 | `p::disable,auth::settingsWrite,writes::handle` |

### 手机与邮箱设置（4）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ContactSettingsProcessor.java`。状态：已注册源码；运行未验收。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/me/settings/phone/verification` | 19 | `p::phoneCode,auth::settingsWrite,writes::handle` |
| POST | `/api/v3/me/settings/email/verification` | 20 | `p::emailCode,auth::settingsWrite,writes::handle` |
| PUT | `/api/v3/me/settings/phone` | 21 | `p::phone,auth::settingsWrite,writes::handle` |
| PUT | `/api/v3/me/settings/email` | 22 | `p::email,auth::settingsWrite,writes::handle` |

### 官方 APP 短期扫码（7）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3AppLoginProcessor.java`。已总接线，真实运行待验收；用户明确不改APP代码。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| POST | `/api/v3/auth/app-login/requests` | 28 | `p::create,p::privacy,auth::settingsWrite,writes::handle` |
| POST | `/api/v3/auth/app-login/status` | 29 | `p::webStatus,p::privacy,auth::profileRead,writes::handle` |
| POST | `/api/v3/auth/app-login/decision` | 30 | `p::webDecision,p::privacy,auth::settingsWrite,writes::handle` |
| POST | `/api/v3/auth/app-login/claim` | 31 | `p::claim,p::privacy,p::budget,anonymous::handle` |
| POST | `/api/v3/auth/app-login/app-status` | 32 | `p::appStatus,p::privacy,p::budget,anonymous::handle` |
| POST | `/api/v3/auth/app-login/app-decision` | 33 | `p::appDecision,p::privacy,p::budget,anonymous::handle` |
| POST | `/api/v3/auth/app-login/exchange` | 34 | `p::exchange,p::privacy,p::budget,anonymous::handle` |

## 测试证据与局限

以下引用历史记录的实际结果，不把测试文件存在等同于已经执行；各runner可能重复覆盖共享代码，禁止相加宣传“全接口通过”。完整细节见 `docs/api-v3-development-plan.md` 对应日期批次。

| 模块 | 可核查隔离证据 | 仍缺证据 |
| --- | --- | --- |
| Token/协议/草稿/幂等/严格提交 | `tools/api-v3-token-test.mjs`、`api-v3-write-test.mjs`；历史记录21项协议/草稿及6组真实Latke提交故障 | 真实MySQL提交不确定性、完整HTTP/Cookie/MFA与跨站行为 |
| OAuth/账号/聊天室/共享写事务 | `tools/api-v3-chat-event-test.mjs`；2026-10-20历史记录通用157组 | 完整IoC、真实短信/极验、OAuth浏览器同意→回调→换码、并发MySQL与匿名盾 |
| 通知/文章/chat/私信实时 | `tools/api-v3-socket-test.mjs`；私信实时批次51组；`api-v3-article-realtime-test.mjs`另有定向覆盖 | 实际WS升级、代理/重连、多账号撤权、跨实例和旧客户端并用 |
| 普通积分改名 | `tools/api-v3-user-name-test.mjs` 修复与列长触发器补充后6组隔离测试通过；见work | 真实MySQL并发/列长、完整IoC/HTTP、真实内容审核、PC/mobile及旧积分页联调 |
| 私信/头像/MBTI | `tools/api-v3-private-message-test.mjs` 20组、`api-v3-settings-test.mjs` 9组（MBTI写入批次） | 真实私有Markdown运行、MySQL行锁、客户端发送/已读/撤回/刷新及头像图床 |
| 文章/评论/互动/删除/段评 | `tools/api-v3-article-test.mjs`、`api-v3-comment-test.mjs`、`api-v3-comment-removal-test.mjs`、`api-v3-interaction-removal-test.mjs`、`api-v3-paragraph-test.mjs`、reaction专项；历史分批有通过记录 | 后台并发删改、权限变动/费用、异步通知/索引、真实两端渲染及旧编辑歧义兼容 |
| 设置/关注/专栏/清风/鱼游/签到/奖励/背包/排行/大事记/上传 | `tools/api-v3-{settings,profile,follow,column,breezemoon,fish-game,checkin,activity,bag,ranking,milestone,upload}-test.mjs`（分别的历史批次） | 不应以单个runner推广为所有模块已验收；真实DB/IoC/第三方网络/PC及mobile仍缺 |
| 内容发现/个人/社区等读取 | 对应Java Test与`tools/api-v3-*-smoke.mjs`存在；早期部分仅记录语法解析或未运行 | 不能把有smoke文件计作HTTP已通过；需要真实数据/权限分页验证 |
| 本轮文章热度及并行新增读取 | 主代理重跑`tools/api-v3-socket-test.mjs` 55组通过（含热度及红包实际路由/控制器/授权）；`tools/api-v3-emotion-test.mjs` 5组通过（真实SQL/Dispatcher/auth/controller、SQLite及依赖适配器）；`tools/api-v3-red-packet-test.mjs`纯目录1组通过 | 热度仅当前实例近似连接数；表情未验真实MySQL/浏览器/全局盾；红包已覆盖隔离HTTP/鉴权，不能推断实际领取/发送已实现 |
| 第二轮职业/勋章/会员 | 主代理重跑 profession 6组、medal 4组、membership 7组通过；均包含生产SQL/路由/控制器/授权隔离执行，勋章专项另执行真实AnonymousViewCheckMidware首访/计次逻辑 | SQLite及Token/请求依赖适配器不等于应用联调；职业匿名盾为钩子适配，真实全局黑名单/解封、MySQL/IoC和PC/mobile仍未验收 |
| 第三轮写入与客户端管理 | 主代理重跑主职业8组、勋章显示6组、OAuth管理10组 | MySQL并发、完整IoC、真实客户端与旧入口竞争 |
| 第四轮匿名创作/邀请码/客户端维护 | 主代理重跑匿名内容10组、邀请码5组、停用9组、修改11组及管理回归10组；生产SQL/鉴权/严格提交、SocketTicket与Code/Consent核心隔离执行 | SQLite不能代替MySQL；缺HTTP/WS、两端浏览器、第三方OAuth和全站反漏扫 |
| 第五轮职业详情/设置与勋章排序 | 2026-10-22主代理最终复跑detail7组、settings11组、order8组均退出0；包含实际Primary交替操作、严格提交/保存点/确认丢失及技术预算边界 | 未验真实MySQL锁/旧全行写竞争、完整IoC/HTTP/PC/mobile；26组不能推广成全接口运行通过 |
| 第六轮会员配置/积分开通 | 主代理复跑配置10组、开通12组；实际积分/背包核心、旧开通方法编译与前置锁、严格提交/确认丢失、私有回执白名单、去重索引及自然日边界 | 新普通索引迁移/MySQL EXPLAIN未执行；旧缓存迟put、跨实例DB死锁、完整应用与两端浏览器仍待验收 |
| 第七轮成长提示/排行/公开职业 | 主代理最终复跑guide8组、ranking10组、public9组均退出0；实际SQL/Dispatcher/Bearer认证、共享榜单、真实AnonymousViewCheck、历史独立版本与授权来源重验 | SQLite/Token及请求适配不能代替真实MySQL/IoC/HTTP；全站黑名单/解封、两端浏览器、并发压力仍未验收 |
| 第八轮复读/常用表情/购码 | 主代理最终复跑repeater6组、emotion-settings7组、invitation-purchase9组均退出0；真实SQL/Dispatcher/auth、真实匿名盾、现有GET回读、严格提交及购码/索引响应丢失恢复 | SQLite/Token/请求和缓存适配不代替MySQL/IoC/HTTP；旧新并发边界、真实两端及全站反漏扫仍未验收 |
| 第十轮历史/图片表情分组 | 主代理复跑revision9组、emoji-group7组、emoji-item8组、fish-game-submission7组退出0；真实SQL/清洗/权限/Dispatcher与严格写入故障 | 未验完整MySQL/IoC/HTTP/浏览器及全站反漏扫 |
| 第九轮转账/举报/复读写 | 主代理重跑转账9组、举报9组（GET3+POST6）、复读写9组均退出0；真实SQL/Jsoup/auth/Dispatcher/strict/Notification revision及旧toggle核心；独立MySQL8.4.11三项RR当前读/回滚/目标串行化SQL通过 | SQLite/依赖适配及独立SQL不代替完整Java事务MySQL并发、全IoC/HTTP、两端和全站反漏扫 |

## 历史矛盾与当前更正

1. 计划矩阵曾把“文章热度/在线人数”列为源码已接入，但截至本轮开始，只能查到旧 `GET /api/article/heat/{articleId}`（ArticleProcessor），v3文章订阅人数亦不能自动形成HTTP接口。本轮主代理已接入 `ApiV3ArticleHeatProcessor` / `ApiV3ArticleHeatQueryService`；当前清单已计入该GET。先按当前数据库读者授权，再相加当前节点旧文章连接计数及当前节点v3已认证文章订阅连接数；这是近似连接数、可能重复，不是去重人数或全站实时在线人数。
2. 早期设计称“私信、账号恢复不直接搬v3”和“PKCE尚未实现”，后续已经新增独立私信/账号/OAuth协议入口与核心；这些旧句不能覆盖当前源码事实。管理员OAuth登记管理已按用户明确范围接入，禁止自助登记。
3. 注册短信、私信撤回、私信实时的历史“未接线/待兼容批准”已由后续实际接线覆盖；当前不再把它们列为缺入口。仍需部署与运行验收。
4. “有源码”“隔离测试通过”“用户确认规则”“已运行验收”分别记录；后续阶段不能反推历史整站已经安全验收。

## 剩余能力、依赖与待确认规则

“缺入口”只表示没有对应v3资源，并不表示应该照搬旧接口。具体方法/path需设计后成为公开契约，以下旧URL仅用于追溯。

| 能力 | 当前可证明状态与旧来源 | 下一步与依赖 |
| --- | --- | --- |
| OAuth客户端登记/维护 | `ApiV3ClientRegistrationService#createApprovedInCurrentTransaction`已有已批准应用登记核心；列表/详情/登记HTTP已接入 | 用户已确认仅管理员登记和维护及20回调/2048字符；第一方管理员授权、幂等列表/详情/登记已接入，逻辑停用及完整Token撤销已接入；回调/权限PUT已接入，实际集合变更令全部旧Token/码/待确认失效；仅改名称保留授权，禁止自助登记 |
| OAuth token/revoke风控 | 协议入口已注册，用户已批准有效协议证明作为端点身份 | token/revoke接只读绑定凭据准入，缺失/无效委派原匿名验证；全局黑名单不变，不加viewSkips或普通身份。根定向6组，真实第三方/验证码/黑名单联调仍待做 |
| 鱼游投稿/编辑申请 | v3仅目录/详情/评论读、投票和评论写；旧 `FishGameProcessor` `/api/fish-games` POST与`/{id}/edit` | 投稿POST/编辑PUT已接线；按说明校验授权URL及同站回调后待审，旧审核合并pending OAuth字段；先执行20261023鱼游迁移 |
| 聊天红包发送/详情/领取 | 已新增GET `/api/v3/chat/red-packet-types`；`ApiV3ChatMessageDto`只给红包摘要，旧ChatroomProcessor红包链仍存在 | 五类创建/详情/领取已接源码：新增心跳负积分与猜拳出拳/返还结算；均需显式风险确认。dice不支持。真实MySQL独立进程竞争已有定向证据；完整IoC/页面仍未验；旧页面新风险红包衔接及停用发送者返还资格待答，见risk-work |
| 公开支付跳转 | 旧 `/pay/wechat` 与 `/charge/point`存在；支付回调及管理排除 | 用户确认只提供现有站内捐助页链接；GET /api/v3/payments/donation-page已接线，activity.read，不创建订单或凭浏览器链接建立Cookie；真实浏览器登录跳转待验 |
| 账号敏感设置 | 旧SettingsProcessor手机/邮箱验证码与修改、密码修改、普通积分改名；MFAProcessor四条MFA能力 | 密码/MFA状态、MFA准备启停、手机邮箱申请变更9入口已接线；全部凭据epoch底座已落；普通积分改名v3已接线，同事务改名/扣费/回执；旧/settings/username本批不改造。真实MySQL/IoC/客户端验收仍缺 |
| 邀请、转账、导出 | 旧`/invitecode/state`、`/point/buy-invitecode`、`/point/transfer`、`/export/posts` | 邀请码存储状态POST已接入（activity.read，仅used/unused/stopped）；购买POST已接入：仅邀请模式、余额足够才扣费生成、原key恢复同码；转账POST已接入原子主款/费/通知及本人回执；导出三入口已接线，第一方当前密码/MFA、完整本人私有ZIP与100分/回执同提交，24h当前本人授权下载；先执行新SQL，真实MySQL/IoC/下载未验，unused不保证可注册 |
| 自定义表情与分组 | 旧EmojiProcessor13条（列表/上传/分组/成员/分享/导入/迁移）及settings/emotionList | 已新增本人表情选择/目录/分组/组内表情4条GET，profile.read且不接收他人ID；reaction目录不能替代表情库；选择PUT已接入，仅目录数组、空数组恢复默认、仅本人type0及幂等严格事务；分组三写、成员四写已接入；分享/导入/迁移三POST已接入，生命周期与整批422规则已确认并落实，真实运行仍待验收 |
| 文章/评论版本与举报 | 旧ArticleProcessor历史版本两入口、CommentProcessor版本入口、Router `/report` | 举报GET/POST已接入；历史四GET已接线，作者及有对应历史权限的当前合法读者可读，私密仍限双方，详情仅清洗投影 |
| 本人匿名创作清单 | GET /me/anonymous-articles及/me/anonymous-comments已接入，content.read本人元数据 | 正文经已有详情读取；讨论邀请用有界候选/共享解析/hash绑定，超技术预算503，真实MySQL与并发验收仍待做 |
| 用户职业 | 目录/本人状态、本人详情/90天记录、主职业/跳过引导/隐私及排行/提示/公开资料均已注册 | 排行、成长提示与他人公开资料三GET已注册；按可见人群排行/统计，经验跨模块省略，公开动态当前来源权限；真实MySQL、完整应用及旧隐私整行写竞争仍待验收 |
| 用户勋章 | 本人/公开读取、显示PUT、相邻移动POST及两图片GET已注册 | 用户侧当前已知排序入口已补齐；隐藏/过期持有参与移动但不清理，旧整行写竞争与真实MySQL待验收，不开放后台全定义目录 |
| 用户会员 | 档位/本人状态两GET、配置PUT与积分开通POST已注册 | 三项业务规则已获确认并落实；仅展示可写，开通defaults/券/积分/礼包同严格根；部署新积分索引后仍需真实MySQL、旧入口并用与缓存验收。公开昵称样式已确认并接入按1..100个指定ID查询，仅bold/underline/color和profile.read，不返回全站名单或到期时间 |
| 复读机转录站 | RepeaterProcessor旧`GET /api/repeater/items`、`GET /api/repeater/next`、`POST /api/repeater`及`POST /api/repeater/{id}/like`；API.md独立公开模块 | items/next两个GET已接入，保留前N/前50随机池、匿名盾/可选content.read及当前用户点赞状态；投稿POST与明确点赞PUT/DELETE已接入严格事务/幂等，职业来源绑定；用户批准旧toggle共用核心，COUNT当前读，历史重复仅本人收敛 |
| 作者付费置顶 | 新POST /api/v3/articles/{articleId}/stick已接线，双scope与同事务100积分/时间/回执 | 用户批准recent有效置顶优先及摘要详情stick投影，已实现；共享过期检查当前时间，不清掉后来续费/永久置顶。隔离8组+排序SQL4组和客户端构建/宽视口夹具通过；真实MySQL/业务库/调度未验，旧付费链保持 |
| 官方身份认证申请 | 企业申请/本人列表详情/审核列表/材料一次读取/结论6路已注册，第一方及持久审核员ID边界 | 用户确认先读图再给结论；未读409、结论与回执同根、释放单pending、不自动发权。11组核心与5组真实MySQL跨JVM竞争通过；完整应用/人工发放仍需验收，见identity-application-work |
| 聊天室音乐与天气发送 | 两个POST已接入共享普通准入/消息事务和幂等；新卡片专用投影保留纯文本与签名URL | 卡片6组及共享回归、两套源码/生成JS与隔离浏览器检查；完整应用/真实音频/移动设备待验，见chat-card-work |
| 游戏云存档 | Evolve四新入口已接，旧cloud登记游戏共用核心 | 已确认单槽/原样字符串/版本/幂等/显式迁移；七组服务HTTP、完整SQL重复迁移与七独立JVM MySQL竞争通过。其他游戏仅确认来源后扩目录；真实IoC/PC/mobile/CDN及全局风控仍待验，见game-save-work。 |
| website_v3迁移 | 已找到客户端 /Users/ling/Documents/fishpi/website_v3 | 认证/本人会员及列表、分类、搜索、目录、详情评论读取已接线；追加通知HTTP/已读写/票据实时失效与SSR导航一致性。通知6组、认证12组/缓存3组、双构建和实际应用本地HTTP+WS夹具通过；追加常用表情/图片分组分页、设置与选择器迁移，旧mock版本URL已删除；其余写入/Socket/模块仍待迁移，见client-notifications-work与client-emojis-work |
| 官方APP短期扫码 | 用户已确认2分钟、APP确认后网页批准、一次消费；当前七POST已接线 | SQL/Repository/metadata、当前网页授权与账号epoch绑定、原子令牌对、Native刷新/撤销复用OAuth及管理员变更联动已实现。Java纯核心4/事务HTTP7、管理12+10/安全6回归及真实MySQL原语3组通过；追加实际生产Java/MySQL七场景、五写入故障、七独立JVM竞争且逐项观察锁等待。完整IoC/部署仍未验。用户最新范围仅v3，APP已还原干净。 |
| 公开专栏 | 四GET已改当前可见署名长篇/有效作者SQL投影，字段路径保持 | 当前范围的计数/最新章/排序先过滤再分页；9组隔离及客户端14组，完整运行仍待验，旧经典保持 |
| 最近注册、随机发现、公开日志 | 第100轮已实现三项源码与客户端，真实运行待验收 | 最近注册仅有效账号ID倒序最多20，公开四字段；随机字段起点回绕/去重/打乱，1..20默认8、当前可读可列出及可选排除ID，匿名隐藏身份；日志仅登录activity.read、ID/时间/固定白名单类别，无data/key2，后续按类别扩正文 |
| 全链路验收 | 上列已注册接口皆缺本轮真实运行确认 | 依赖数据库迁移、Java25完整类型/IoC启动、IDEA重编译/重启确认、专用测试账号/短信/图床/浏览器及第三方OAuth客户端 |

## 并行分工与合入顺序

| 工作包 | 本轮负责人/状态 | 独占区域与可并行边界 | 合入依赖 |
| --- | --- | --- | --- |
| 当前清单与缺口 | inventory代理首轮181条，主代理第二轮更新187条源码清单 | 仅`docs/api-v3-progress.md`；不改代码或公开文档 | 主代理最终重扫注册数、逐条核对新增入口 |
| 本人设置/表情读取 | settings代理，4条GET已总接线，隔离5组通过 | 专用新Processor/Service/Test；避免改公共注册/公开文档 | 主代理审阅及ApiV3Processor总接线后再计入注册数 |
| 红包/支付盘点与安全目录 | chat_payments代理，类型GET已总接线，纯目录专项通过 | 专用报告及类型目录源码；未确认的经济规则不实现写入 | 规则确认、共享积分锁与事务/幂等核心；主代理总接线 |
| 文章热度与总接线 | 主代理，热度已总接线、55组Socket专项通过 | 热度Processor/Service/专项；ApiV3Processor、API.md、UPDATE_LOG、AGENTS及共享计划由主代理维护 | 当前读者可见性、实例计数契约、专项测试；完整运行另验 |
| 鱼游OAuth投稿/编辑 | finish_transfer已完成，两接口已接线 | 专用协调器及旧审核OAuth存储适配；根复跑7组 | SQL迁移、完整服务和旧写并发运行验收 |
| OAuth登记与维护 | 第三轮主代理登记3接口；第四轮oauth_disable完成停用/修改 | 独立Processor/Service/专项，共用原登记字段校验；公共文档与总接线由主代理维护 | 第一方当前管理员、授权失效事务与真实并发验收 |
| 主职业/勋章显示写入 | inventory/settings起稿、主代理复核接线，8/6组专项通过 | 各自独占Processor/Service/专项，幂等operation隔离 | 隐私/排序及真实新旧竞争仍待完成 |
| 本人匿名内容 | anonymous_content完成两GET、主代理接线并复跑10组 | 独立Query/DTO/专项，invitation_read交叉只读审查 | 当前讨论权限与MySQL验收 |
| 邀请码状态 | invitation_read完成POST、主代理接线并复跑5组 | 只读存量状态、独立输入/DTO，不推断注册可用性 | 购买/转账/导出仍独立待实现 |
| 敏感账号 | 后续工作包，受规则依赖 | 公共auth/scope/Token撤权需串行审阅 | 明确批准的规则与兼容影响 |
| 职业读取与设置 | 前轮目录/本人/主职业；第五轮profession_details两GET、profession_settings两PUT，7/11组通过 | Detail/Settings各自文件与专项/work，主代理公共接线/文档 | 排行/公开资料/成长提示第七轮已接线；旧全行隐私写竞争待验 |
| 勋章排序 | medal_order第五轮完成POST，主代理复跑8组及设置只读交叉审 | Order专用文件与专项/work，不复用旧清理副作用 | 旧授予/显示/排序全行写竞争及真实MySQL仍待验 |
| 第七轮成长提示/排行/公开资料 | profession_guides/ranking/public独占实现，主代理复跑8/10/9组及总接线 | 三套Processor/QueryService/DTO/Test/runner/work；共享ranking summary由ranking维护，公开资料用整请求预算 | 已实现207条源码；真实MySQL、完整应用、两端与匿名盾全链仍需验收 |
| 第八轮复读/表情/购码 | repeater_read/emotion_selection/remaining_rules分别实现，主代理复跑6/7/9组与公共接线 | 三套新模块/专项/work；主代理维护API与进度，额外规则只读报告 | 211源码；新旧表情并发、旧购码例外、既有积分迁移及完整运行仍需验收 |
| 会员配置/开通 | membership_settings/opening第六轮完成PUT/POST，10/12组；membership_review交叉只读审 | 新Settings/Opening各自文件，共用Configuration纯工具，主代理总注册/文档/SQL及旧锁序包装 | 先迁移积分索引；真实MySQL、旧入口并用和缓存验收 |
| 版本/举报/邀请/转账及其他缺口 | 后续独立工作包 | 各领域分开实现，共用权限/积分锁串行审阅 | 用户侧范围/字段规则及写核心 |
| 应用/客户端联调 | 末端统一验收，未执行 | 迁移、IDEA重启、HTTP/WS/PC/mobile；用户掌控真实服务 | 所有合并及规则确认完成；不能用适配器跳过 |

主代理公共文件串行汇总，代理不要互改`API.md`、`UPDATE_LOG.md`、`AGENTS.md`、共享计划或总注册。公共scope/迁移/Repository模型有交集时先审阅锁序与部署依赖，再合入。

## 人工全链路验收

1. 先由用户按SQL依赖迁移并通过IDEA重新编译/完整重启Java服务，确认日志过滤器与IoC注册成功；检查PC/mobile所用资源是否读取target/classes。未确认生效前不把浏览器旧行为判为代码失败。
2. 从PC及mobile登录/极验、注册申请→短信→激活、找回申请→短信→完成→再登录，取得正确scope的第一方及第三方Bearer；覆盖MFA、错误/过期/重复证明、已占手机号/名称、关闭注册、邀请码竞争与奖励一次性。
3. OAuth从登记回调→同意页→明确批准/拒绝→303→S256换码→资源读取→轮换刷新→撤销，覆盖错误state/PKCE/回调、重复码、错误owner、撤权与停用；确认Cookie/apiKey不能代替业务Bearer。
4. 公开列表→文章/评论/段评详情→本人源码/草稿→发布/部分编辑/互动→删除，核对费用/计数/通知与刷新一致；两账号覆盖讨论受邀/未受邀、匿名开关、打赏正文、私密评论、匿名作者、他人资源和重复/并发幂等键。
5. 本人设置读取→修改→重新读取与旧PC/mobile页面核对；资产查询→允许的卡片/奖励使用→余额/背包/记录核对；关注/收藏/订阅→各列表及取消。非法值/越权/幂等冲突失败不得留下部分写入。
6. 私信A发送B→两方历史/详情/会话→B未读→指定/全部已读→A本人撤回，第三人拒绝；聊天室历史→发送/话题/弹幕/本人撤回；通知/文章/chat/messages按各自scope取票→首帧认证→提交后提示→HTTP同步→断线重连同步，覆盖撤权、错误channel/target、停用和跨实例限制。红包仅测试实际开放入口，不根据消息摘要推断已支持领取。
7. 鱼游/清风/同城/排行/大事记与专栏从列表→详情→已开放写操作→再次读取，检查审核、隐私、所有者、分页、排序及冲突；上传签票→图床file直传→公开URL展示，缺scope/过期票据/非法文件拒绝。
8. 所有公开入口逐项检查未鉴权、越权、CSRF、XSS、注入、SSRF、批量滥用和敏感信息；盾开启环境覆盖首次/计次验证码、黑名单→/test→validateCaptcha解封、anonymous.viewSkips，以及开发关闭盾的差异；静态/CDN版本号、sourceMappingURL、目录枚举、上传下载路径必须实际检查。当前均未作为运行通过项。

本轮盘点与新增读取接口不执行Maven，不访问8080、数据库、生产，不做Git提交；没有运行浏览器/真实HTTP/外部短信或支付。公开接口文档变更由主代理汇总后仍需同步网站API文章。

第二轮对应实现、文件、完整人工链路见 [职业](api-v3-profession-work.md)、[勋章](api-v3-medal-work.md)、[会员](api-v3-membership-work.md)。主代理已完成总接线，当前187条可达注册与表内METHOD/path完全一致，无重复及漏项；仅源码口径，不作为完整应用启动证据。

第三轮：累计提交6231c4f0已推送origin/api_v3后，新增主职业PUT、勋章显示PUT及管理员OAuth列表/详情/登记3接口。专项分别8/6/10组通过（隔离实际SQL/JDBC/路由，不代替运行验收）。当前192条可达注册与逐项表完全相等、无重复；新开发内容尚未再次提交。详情见 [主职业](api-v3-profession-primary-work.md)、[显示设置](api-v3-medal-display-work.md)、[客户端管理](api-v3-client-management-work.md)。

第四轮：匿名本人列表两GET、邀请码状态POST、OAuth客户端DELETE/PUT共5接口接线，当前197条与逐项表全等无重复。主代理专项匿名10/邀请码5/停用9/修改11、共享管理回归10组通过；invitation_read交叉审匿名/停用未发现实质问题。实际文件与人工链见 [匿名内容](api-v3-anonymous-content-work.md)、[邀请码](api-v3-invitation-work.md)、[停用](api-v3-client-disable-work.md)、[修改](api-v3-client-update-work.md)及development-plan第四轮。源码和隔离结果不代表真实MySQL/应用/浏览器验收；当前新增仍未再次提交。

第五轮（2026-10-22）：202条可达注册与逐项METHOD/path表全等无重复。三专项7/11/8组通过，具体文件/人工步骤见[职业详情](api-v3-profession-detail-work.md)、[职业设置](api-v3-profession-settings-work.md)、[勋章排序](api-v3-medal-order-work.md)。此前累计c09e48cc已推送，本批尚未再次提交；真实数据库和应用验收仍缺。

第六轮（2026-10-22）：总注册204条与逐条表全等无重复，全仓库751路由/398浏览器引用。会员专项10/12组为隔离证据，不代替运行验收。文件/完整人工链路见[会员配置](api-v3-membership-settings-work.md)、[会员开通](api-v3-membership-opening-work.md)及development-plan第六轮。新索引先迁移，本批未提交。


第七轮（2026-10-22）：207条可达注册与逐条METHOD/path表全等、无重复，全仓库754路由/398浏览器引用。主代理复跑成长提示8组、排行10组、公开资料9组全部退出0；仅本批27组专项，不代表全部接口运行通过。实际文件/完整人工链路见[成长提示](api-v3-profession-guide-work.md)、[职业排行](api-v3-profession-ranking-work.md)、[公开资料](api-v3-profession-public-work.md)和development-plan第七轮。本轮未新增迁移或前端产物；本批未提交，真实运行及全站反漏扫仍待验。


第八轮（2026-10-22）：211条可达METHOD/path与表逐项全等、无重复，全仓库758路由/398浏览器引用。主代理复跑[复读](api-v3-repeater-work.md)6组、[表情设置](api-v3-emotion-settings-work.md)7组、[邀请码购买](api-v3-invitation-purchase-work.md)9组均退出0；完整文件/人工链及未验收见各work和development-plan第八轮。其余业务规则盘点见[next-rules](api-v3-next-rules-work.md)，已确认邀请码不重复询问。无新SQL/前端，仍须既有point来源列/积分索引迁移。当前及前批均未再次提交，真实MySQL/应用/浏览器/全站反漏扫未验收。


第十轮最终接线核对（2026-10-23）：总register可达230个METHOD/path，GET120/POST49/PUT37/DELETE23/WS1，与本表逐条一致、无重复。四runner node --check及13段新增公开curl bash -n通过（仅语法，无网络请求），冻结模块空白与tracked diff --check通过。全仓生成清单含尚未注册源码，因此全仓780路由不等同v3已开放数量。表情分享等后续代理仍在实施，未计开放或验收。


第十一轮：分享/导入/迁移三POST已接线，233条（232HTTP+1WS）。root最终transfer7组退出0；修复共享保存点内层已中止后外层重复回滚覆盖原错，group7/item8重跑通过。完整文件与人工链见emoji-transfer-work。凭据版本与敏感设置仍在实现，未注册及未验收。


第十一轮最终核对：233条可达路由与表逐项一致且无重复，GET120/POST52/PUT37/DELETE23/WS1。transfer最终7组含损坏超INT导入计数拒绝退出0；三段新增curl与runner语法、tracked diff空白检查通过。以上仅当前冻结模块，后续敏感认证底座仍在工作，不能据此声明整个未提交工作区已通过发布验收。


第十二轮（2026-10-23）：安全/联系方式/MFA九路由总接线，242条。主代理最终security6、mfa9、contact8、account-mail3组退出0；credential10与Cookie3组退出0，旧chat-event综合157组退出0（含真实生命周期/同意/重置）。真实生产日志过滤专项覆盖新安全路径通过。先迁移20261023_account_credential_state.sql、20261023_api_v3_mfa.sql、20261023_api_v3_contact_verification.sql；完整文件/人工步骤见各work。源码与隔离测试不等于完整应用/真实MySQL/浏览器/全站反漏扫验收；当前未再次提交。


第十二轮补充：账号邮件SMTP专用路径强制禁止调试/正文/收件人日志；完整生产Mails/MailSender与真实JavaMail Session/MimeMessage假transport专项退出0（1组），旧邮件debug对照有效且无真实投递。账号短信开发模式仅模拟受理、不打印验证码。公开API的九段curl与7个原runner语法、242条逐项路由核对、repository.json去重均通过；新增SMTP runner同步语法复核。

第十三轮（2026-10-24）：普通积分改名PUT接入总register，当前243条（242HTTP+1WS），PUT42。费用沿Pointtransfer常量与当前配置1000积分，双activity.write/settings.write及幂等；旧/settings/username本批不改。真实消费侧审查发现的连字符解析与129字符积分元数据问题已修复，带列长触发器的最终隔离6组通过，最终证据以[改名工作记录](api-v3-user-name-work.md)为准；不代表真实MySQL/完整服务/浏览器/全站反漏扫验收。

第十三轮最终核对：主代理带128/255列长触发器复跑改名6组退出0（`/tmp/rhythm-v3-user-name-root-final.log`）；真实OWASP编码与formatter在内，完整getUserPoints/真实积分页/MySQL未验。`node tools/api-route-inventory.mjs`生成790条全仓路由/398浏览器引用；总register递归243个METHOD/path与本表完全匹配，无重复。新增公开curl已通过bash -n（仅语法）。

第十四轮进行中（2026-10-24）：会员昵称样式GET与捐助页链接GET已接入，当前245条（244HTTP+1WS），GET123。根复跑style5组、donation3组；root真实Latke/XML日志过滤专项包含导出敏感路径通过。普通改名另增真实MySQL8.4.11/Connector-J专项5组，231条原样FOR UPDATE，实际费用1000及RR/STRICT事务、重放和同名竞争通过；不是整站E2E，详见user-name-mysql-work。导出完整内容/ZIP/100分/24h私有下载已获确认，三类红包税费/分配/桥接已获确认，两者仍在实现，尚未注册计数。对应完整文件与人工链见membership-style-work和donation-work。

第十四轮补充：导出三路由已总接线，当前248条（247HTTP+1WS），GET125/POST56/PUT42/DELETE24/WS1。根实际复跑post-export10组退出0，覆盖完整本人矩阵、真实ZIP/当前proof/原子收费与故障回滚/块验证/24h清理/HTTP门禁和日志正控；SQLite与依赖/下载transport适配不等于完整MySQL/IoC/浏览器验收。红包代理中断，代码已保留但仍未总注册，不计已完成。

第十四轮红包收尾：三类红包创建/详情/领取已总注册，当前251条（250HTTP+1WS），GET126/POST58/PUT42/DELETE24/WS1。根新增实际核心专项最终10组exit0：资金/消息/事件/职业/领取/税/通知适配与回执故障、真实三路由及旧handler桥接前缀、0积分/禁言/名单及删除限制；聊天event回归157组和publication回归36组通过，类型目录能力标志同步true。范围与未验项详见chat-red-packet-work；心跳/猜拳和游戏存档仍保留后续范围。

第十五轮（2026-10-24）：用户批准OAuth端点协议身份后，token/revoke在原privacy/rate后验证当前凭据，无效仍匿名；路由数保持251。根原回归157组、最终新定向6组退出0，真实global shield/原生客户端/浏览器未验，见oauth-protocol-work。两新增SQL另以一次性MySQL8.4.11执行两遍并核四表字段/类型/唯一索引和样本回滚，两组通过，不代表业务迁移或Java/MySQL全事务验收。

最终静态核对：总register251个METHOD/path与逐项表完全一致且无重复；生成总清单798路由/398浏览器引用。新增8个接口的公开curl bash -n均过，Repository JSON/唯一表名及新增源码尾空白检查、node --check与git diff --check通过。全站运行/业务迁移/浏览器及反漏扫仍未验。


第十六轮（2026-10-24）：用户确认心跳/猜拳规则后扩展既有红包三路，数量仍251。先部署20261024_api_v3_chat_red_packet_risk.sql（在安全三类迁移之后）：type扩17及出拳/返还列，既有安全记录取中性默认值。创建/领取须riskAccepted=true；心跳保存有正有负的完整分配，猜拳领取出拳绑定幂等且领取扣款/发送者返还同提交。停用发送者策略待答前仅允许当前有效发送者结算；旧入口缺显式风险证明拒绝新核心风险红包，历史旧记录仍原路径。实现、证据、文件与人工链见chat-red-packet-risk-work；接口数量不因扩充类型增加。


第十七轮（2026-10-24）：只读反查旧公开能力发现三项漏列待办（作者付费置顶、官方认证申请、音乐/天气发送），已补上表并询问业务规则；不得把“现有251入口”当作全部能力完成。根新增有序迁移链专项：真实隔离MySQL执行15份迁移、核19张新表完整元数据及默认/定制scope与旧记录中性兼容，四组exit0，见migration-chain-work。导出新增真实MySQL八场景（含跨JVM同键锁等待与一次100积分），见post-export-work；完整应用与PC/mobile仍未验收。源码业务接口数不变。

第十八轮：已找到实际客户端并接线读取与稳健认证，后端文章GET新增qna/perfect/month-comments，接口数251保持。Java25 SQL专项4组、客户端认证12组/缓存3组/内容适配及双构建通过；IAB窄视口仅对本地夹具验证，真实服务端、宽屏及设备未验。文件/人工链见client-read-work。付费置顶、音乐/天气与认证申请规则已确认，下一批待实现。

第十九轮：付费置顶POST及当前状态/排序已完成源码，新增1路达252（251HTTP+1WS），GET126/POST59/PUT42/DELETE24/WS1。费用/时间/回执严格同根，共享过期任务当前锁读取防抹新续费；用户批准recent各排序有效置顶优先、永久最先。8组付费/元数据、4组实际SQL及客户端内容适配/双构建通过；IAB宽视口仅本地夹具验证列表徽标与详情状态。真实MySQL、业务库、服务端/调度与移动设备未验。文件/人工链见article-stick-work。

第二十轮：新增音乐、天气两路POST，源码总数254（253HTTP+1WS），POST61。共享普通发言准入、持久事件、职业来源、最新发言时间和幂等事务；新卡片typed payload只对新记录做精确投影，历史读规则保持。两套聊天室编码卡片与播放列表文本，HTTPS音频以原生播放器承载。认证申请去重及审核记录规则已确认，仍待开发。证据与人工步骤见api-v3-chat-card-work.md。

第二十一轮：企业认证申请及私有材料5路接入，总259（258HTTP+1WS），GET129/POST63/PUT42/DELETE24/WS1。仅第一方，迁移时精确绑定adlered账号ID，申请/材料/回执同根，首次读图删除材料并记时间，网络丢ACK不可恢复图片。8组隔离核心/实际HTTP/日志检查与16迁移22表MySQL检查通过。结论提交仍等待先读图规则答复；不能宣告认证模块或v3整体完成。

第二十二轮：v3仍259，无新业务入口；企业认证真实MySQL专项补齐8个完整既有场景与3个独立JVM竞争，观察真实InnoDB等待，核对同key一申请、异key单pending、读图仅一方取得且另一方410，材料删除/readAt/回执一致。容器已清理，证据见identity-application-work。完整应用网络/两实例与审核结论仍未完成。

第二十三轮（2026-10-25）：用户确认企业认证必须先读图；新增结论PUT，源码总260（259HTTP+1WS），GET129/POST63/PUT43/DELETE24/WS1。结论/回执/释放pending严格同根，仅approved/rejected与256纯文本说明；不恢复材料、不自动发勋章或改权限。11组SQLite/实际HTTP及相同11组MySQL场景、5组独立JVM竞争通过（包含同键结论与冲突结论）。客户端通知迁移另见client-notifications-work，完整应用与移动设备仍未验收。

第二十四轮（2026-10-25）：客户端表情目录/常用设置、图片分组/成员分页与增删、共享选择器接v3；后端接口数260不变。六组专项、通知/认证回归和双构建通过，生成62个JS版本扫描零命中；IAB仅本地夹具验证常用设置、组/图片增删与编辑器插入，修复SettingsView水合不一致后无警告。真实服务端/移动/上传发送及分享导入未验，文件/完整人工链见client-emojis-work。

第二十五轮（2026-10-25）：客户端表情组/成员名称排序编辑、快照分享码、导入独立组及本人旧云迁移已接线，260路保持；导入/迁移分页重读真实元数据，不伪造排序。9组专项、类型/双构建通过，62个生成JS审计零命中；IAB本地夹具走通编辑→分享→删来源→导入及旧云迁移，最后页目标读取真实排序。真实业务库、移动、旧编辑器hint/上传/发送仍缺；文件/验收见client-emojis-work补全节。

第二十六轮（2026-10-25）：客户端昵称/简介/网址、独立MBTI、头像读取/URL保存/票据图片上传接既有v3，260路保持；上传只票据、无Cookie，设置失败保留URL/同key，身份变化中止旧上传。5组专项及类型/双构建通过，62个生成JS审计零命中；IAB本地夹具验证保存/新页保持、MBTI规范与拒绝、票据上传及非法HTTP头像；修资料用户名链接SSR水合。QQ/标签规则待确认，真实后端/图床/移动及其余设置未验；文件/完整人工链见client-profile-work。

第二十七轮（2026-10-25）：客户端functions/display/privacy/location/locale与语言时区/设备皮肤目录接线，260路保持；只写改动字段，display模式与皮肤互不覆盖，读错不补默认。核旧模板后准确标chatImageBlur为模糊开关。7组专项、类型/双构建、62个生成JS审计通过；IAB本地夹具验证六表单及独立partial键、模式保持、错误恢复，无水合警告。真实后端/隐私效果/皮肤/语言呈现/移动仍未验；见client-preferences-work，其他模块与待答规则保持。

第二十八轮（2026-10-25）：用户确认QQ/标签严格规则。QQ独立第一方GET/PUT及客户端表单完成，总262（261HTTP+1WS），GET130/POST63/PUT44/DELETE24/WS1；现有profile/公开第三方响应不扩QQ。五组SQL/HTTP专项、实际Latke解析日志回归、客户端现六组/双构建、62个JS审计通过；IAB本地夹具走通前导0/重读/非法/清除，无水合警告。个人标签已确认尚待同根关联实现，真实后端/MySQL锁/设备仍缺，见qq-settings-work。


第二十九轮（2026-10-25）：个人标签GET/PUT已总接线，当前264条（263HTTP+1WS），GET131/POST63/PUT45/DELETE24/WS1。逐项严格校验、当前目录规范化，标签/creator/self/统计/设置/回执同根；仅提取旧别名函数，不改变旧格式化丢弃/截断行为。最终SQLite七组、QQ五组及实际Latke日志专项通过；一次性MySQL8.4同样七组+QQ五组、五组独立JVM竞争且观察到InnoDB锁等待通过，容器已清理。DAO/cache/censor/quota适配范围与完整链见[标签工作记录](api-v3-profile-tags-work.md)，不是完整应用IoC或新旧全局串行证明。客户端资料专项七组、类型/client/SSR构建与62个生成JS审计零命中；本地夹具验证规范化/超限拒绝/清除，Hydration日志空。机械清单811全仓路由/398浏览器引用。真实服务、设备、全局风控与全站反漏扫仍未验收，v3总体未完成。


第三十轮（2026-10-25）：客户端本人勋章从旧apiKey空值静默跳过迁移至既有v3 GET/PUT/POST，账号页真实分页、明确展示和相邻移动，未知结果保留同命令/key、成功写后读错不重复移动，凭据边界隔离。实际TS/Vue专项6组、类型/client/SSR构建、本地HTTP/WS夹具21枚两页+单次移动/同键回放+隐藏成功及Hydration空通过；62个生成JS审计零命中。后端仍264路。完整文件/人工链/未验范围见[客户端勋章工作记录](api-v3-client-medals-work.md)，/gen图片仍既有会话通道，真实服务/移动设备/全局风控未验，其他账号及公开用户模块仍待迁移。


第三十一轮（2026-10-25）：客户端本人职业页迁移既有七类资源：目录/本人分页/详情/经验记录/主职业/跳过/隐私。完整十模块CUSTOM、六预设、无进度不造等级、独立目录与记录分页，未知结果冻结原命令/key，当前读取不被旧回执覆盖；草稿/详情/凭据隔离。7组实际TS/Vue专项、类型/client/SSR构建、62个生成JS审计零命中，本地HTTP/WS夹具两页目录/同键回放/选择无进度/跳过保留经验/等级与正负经验/记录第二页及Hydration空通过。后端仍264条。完整文件/人工链见[本人职业客户端工作记录](api-v3-client-professions-work.md)。真实服务/移动/公开隐私效果/全局反漏扫未验；成长提示、排行、他人公开资料和其余客户端模块仍待迁移。


第三十二轮（2026-10-25）：客户端职业榜与成长提示迁移到既有v3，目录/榜单分页、可见人数/真实连续rank，隐藏经验保留缺省而非补0；共享提示接排行与本人设置，来源/奖励不去重或造等级对应。旧job SWR缓存/API与经验/名次回退已移除，身份/目标隔离；真实夹具发现并修复SideBar身份/本机布局SSR水合，挂载后渲染浏览器模块。新专项7组+本人职业回归7组、类型/client/SSR构建、生成JS定向审计通过；实际IAB验证隐私省略/真0/rank21/目录与空榜/重复提示/设置入口，修复后新页Hydration日志空。后端仍264条，完整文件/人工链/适配范围见[职业榜客户端记录](api-v3-client-profession-ranking-work.md)。他人公开资料及其他模块、真实服务/移动/隐私算法/全局反漏扫仍待迁移或验收。

第三十三轮（2026-10-25）：用户主页职业面板改用既有公开v3，结果模块按存在性渲染，experience/rarity独立省略；SELECTED不造等级、贡献/动态不补经验、可见榜名次不推算隐藏人数。MemberView移除旧职业读取，标签不再依赖primary可见。7组实际TS/完整SFC模板/Vue状态/SSR专项、类型/client/SSR构建、63个生成JS审计零命中；IAB夹具验证登录八模块/退出匿名贡献、无主职业入口、SELECTED/404/503/文本HTML及Hydration空。后端仍264条。见[公开职业客户端记录](api-v3-client-public-professions-work.md)。基础主页仍旧userinfo/SSR门禁及会员/徽章/列表，未全迁v3；真实权限/来源可读性/设备/全局反漏扫仍未验。

第三十四轮（2026-10-25）：/vips本人会员页面迁移既有四类v3资源，完整有界目录/状态、支持字段的部分展示配置、当前价格与优惠券确认开通，未知结果冻结原命令/key，实际paidPoints与当前状态分开，后置读错不重买。去除旧apiKey链与固定分组/推算优惠，不补空配置或改只读权益。8组实际TS/Vue/确认专项、类型/client/SSR构建、64个生成JS版本/map审计零命中；IAB本地夹具验证无效券不扣、800积分一次COMMIT/REPLAY、配置单字段布尔/颜色及Hydration空。后端仍264条。完整文件/人工链见[会员页客户端记录](api-v3-client-membership-work.md)；共享昵称与他人会员判断仍旧链，真实服务/账款礼包/设备/全局反漏扫未验。

第三十五轮（2026-10-25）：共享VipNickname/useVipNickname网络源切到既有指定ID v3 styles，姓名仅用v3摘要解析；205 ID专项合并100/100/5，严格三字段/顺序投影。ID/姓名缓存、队列有界并绑定当前凭据，目标失效/换号阻止旧回写，去本地config回退，匿名/SSR普通文本不推断非会员。8组API/真实helper/编译Vue模板专项+会员8组回归、双构建、64 JS版本/map审计零命中；IAB夹具验证摘要→ID100→样式蓝色/下划线→退出清CSS且无后续样式请求、Hydration空。后端仍264条。见[共享昵称记录](api-v3-client-membership-styles-work.md)。已挂载项不承诺TTL自动同步；他人会员徽章、聊天室手写样式等仍另迁移，真实服务/设备/全局反漏扫未验。


第三十六轮（2026-10-25）：客户端设置/他人主页转账与邀请码购买/状态接入既有三条v3 POST，后端仍264条。稳定身份版本区分正常令牌刷新与换号，私有Pinia内存跨SPA路由保留未知命令/key；同键恢复、后置余额读错不重扣、实际费用/码回执，不用unused保证注册。7组实际TS/Pinia及真实HTTP401轮换专项、类型/client/SSR构建、65个生成JS版本/map审计零命中；IAB合成余额10000→9900→9800各一次COMMIT/REPLAY、跨页恢复/状态404/主页目标预填。发现并修复主页身份SSR警告，修复后新页Hydration空。完整文件/人工链见[客户端积分与邀请码记录](api-v3-client-finance-work.md)。无真实转账，真实服务/账款通知/设备/全局反漏扫未验，流水与其他客户端模块继续迁移。


第三十七轮（2026-10-25）：客户端本人积分页接既有v3 points/me，只读真实金额/收支/交易后余额/总分页，删除旧通知解析；当前余额与列表独立读错重试、不补假0/空。稳定身份隔离、正常token轮换保留页，换号回首页、SSR不读私有。5组实际TS/Vue/SSR专项、财务7组回归、类型/client/SSR构建与65 JS版本/map审计零命中；IAB21条两页、余额503独立恢复/第二页503恢复、末页/0金额/退出跳登录及Hydration空。后端264保持；文件/人工链见[积分流水客户端记录](api-v3-client-points-work.md)。真实账本/后端权限/设备/全局反漏扫未验，其他客户端模块继续迁移。


第三十八轮（2026-10-25）：客户端账号背包及五写（四卡/积分改名）接既有v3，历史回执与当前GET分开；稳定身份保留未知命令/key、同键恢复、后置读取失败不再消费。删无消费者旧背包/改名API与mockUserBag兜底。6组实际TS/Pinia/完整Vue专项、财务7/积分5回归、类型/client/SSR构建及65 JS版本/map审计零命中；IAB四卡、补签409不消耗/成功、非法名、卡改名与积分1000单次COMMIT/REPLAY及跨SPA恢复通过，Hydration空。后端264保持，完整文件/人工链见[背包与改名客户端记录](api-v3-client-inventory-work.md)。真实事务/签到任务/缓存/设备/反漏扫未验；敏感账号、导出及其他客户端模块继续迁移。


第三十九轮（2026-10-25）：账号安全客户端接既有九入口，真实状态/当前密码与TOTP、MFA显式本地准备/启停、手机邮箱发码/修改；敏感成功或未知结果重新登录核实，不盲重放。用户批准新密码6..128 UTF16，API文档注明客户端规则；旧敏感包装移除。确认加可选AbortSignal只取消本人，验证码helper拒迟到回调且脚本失败可重试。7组实际TS/完整Vue专项、3组实际共享生命周期、财务7/背包6回归、双构建及65 JS版本/map定向通过。IAB合成准备/切换清密钥/非法手机/422清证明/未知改密→重登录及Hydration空；未做真实凭据、极验/SMS/邮箱/TOTP操作。后端仍264，完整文件/人工链见[账号安全客户端记录](api-v3-client-security-work.md)。真实撤权全链/设备/反漏扫仍未验，APP码/导出等继续迁移。


第四十轮（2026-10-25）：客户端本人导出接既有创建/状态/私有ZIP三路；100分确认、未知key跨SPA保留且恢复需新证明、ready/expired和当前余额分开。HTTP增受预算ZIP分支，复用401/风控并区分主动取消；长度/头/hash后本地Blob交付，离页/换号取消回收，删旧公开跳转。7组真实API/Pinia/模板专项、4组真实HTTP ZIP专项、安全7/财务7回归、双构建与65 JS版本/map审计通过；IAB合成ZIP703字节、一次100积分COMMIT/REPLAY、跨页新证明恢复/状态503/私有下载/未知ID/退出及Hydration空。后端仍264，完整文件/人工链见[私有导出客户端记录](api-v3-client-post-export-work.md)。未验真实快照/账款/设备磁盘保存/全局反漏扫，APP码/认证等继续迁移。


第四十一轮（2026-10-25）：企业认证申请客户端接既有本人创建/列表/详情，原始JPEG/PNG私有发送，本地预览不上传，未知同图同key跨SPA恢复，材料读取与审核结论分列；删旧公开图床提交。七组实际API/Pinia/完整Vue专项、导出7/安全7回归、双构建与65 JS版本/map审计通过。IAB合成536字节PNG一次CREATE/REPLAY、读后仍待审/详情503恢复及Hydration空；分页第二页503已触发，浏览器恢复尚未验完。后端264保持，文件/人工链/未验见[认证申请客户端记录](api-v3-client-identity-work.md)。审核后台未在本批重建，真实权限/材料/设备/反漏扫仍缺，APP扫码及其余模块继续迁移。


第四十二轮（2026-10-25）：捐助客户端改用既有只读页面链接，删旧GET下单/金额/附言/支付二维码；显式经典站点导航、失败重读、身份/SSR隔离及同页循环提示。四组真实API/Vue专项、积分5/认证7回归、双构建与65 JS定向审计通过；IAB503恢复显示明确链接，无订单/支付操作。补齐前批认证分页503→返回首页→末页2/2/禁下一页、HTML说明纯文本及Hydration空。后端264保持，见[捐助客户端记录](api-v3-client-donation-work.md)。真实服务/经典登录/设备/全局风控未验，APP扫码待协议选择，其他内容/互动/私信/聊天室继续迁移。


第四十三轮（2026-10-25）：本人匿名文章/评论客户端从旧“无JSON”占位迁移至既有两GET，保留原/member路径，独立列表不依赖旧公开主页；设置数据页直达、公开主页只向本人显示匿贴/匿回。真实元数据/分页、私密评论标记与当前身份/目标/SSR隔离。五组实际API/Vue专项、公开职业7/导出7回归、双构建及66 JS定向审计通过；IAB21条两页/503重试/末页/HTML纯文本/普通私密/他人路径零请求及Hydration空。后端264保持，完整文件/人工链见[匿名创作客户端记录](api-v3-client-anonymous-content-work.md)。真实阅读权限/业务库/设备/全局风控未验；其他内容互动/私信聊天室仍待迁移。


第四十四轮（2026-10-25）：用户确认新增两分钟/APP与网页双确认/一次消费扫码协议，并提供官方Flutter源fishpi-app，已克隆本地。新增未接线纯Java状态机与四组真实Java25专项（通过），APP严格opaque payload解析器及两项未运行Flutter测试，删除旧扫码/登录凭据控制台输出。当前无新HTTP/SQL/持久化接线，总264保持；Flutter/Dart不可用，不能称APP已支持新登录。规则/实际文件与剩余事务/网页/APP迁移见[APP扫码开发记录](api-v3-app-login-work.md)。关注粉丝客户端迁移尚未写入，继续保留待办。


第四十五轮（2026-10-25）：用户明确APP代码不动，已逐项还原APP仓库并确认干净；仅实现Rhythm扫码服务端七POST，新增表/迁移/Repository元数据，当前271路（270HTTP+1WS），GET131/POST70/PUT45/DELETE24/WS1。发码来源网站授权和epoch逐次重验、APP challenge绑定/双确认/严格同根令牌对消费；独立native client复用现有OAuth刷新撤销，管理员权限/回调变化与停用清待扫码。纯核心4组、事务/实际HTTP7组（含实际Token primitive/轮换家族重放）、管理12+10/安全6回归通过。真实离线MySQL3组覆盖DDL重复/元数据、回滚及两SQL进程观察锁等待/一次消费sink；不冒称完整Java服务并发。清单818旧新路由/398浏览器引用。完整文件/人工链见[扫码服务记录](api-v3-app-login-work.md)，真实IoC/全局风控/网页设备与Java-MySQL并发仍未验，v3总体未完成。


第四十六轮（2026-10-25）：扫码验证从SQL sink推进至实际生产Java/MySQL：复跑全部七服务/HTTP场景、五种真实生产Token/消费/提交故障、七独立JVM竞争（claim同/异、consume重试、deny先/consume先、reset先/consume-reset），逐项观察InnoDB LOCK WAIT并独立JDBC核验真实Token哈希/唯一family及改密全撤；临时容器已移除。补正create的begin失败映射503，不留请求行，SQLite七组和同一完整MySQL专项复跑通过；API.md/更新记录同步。后端271保持，APP未改，完整文件与适配范围见app-login-work；不是完整IoC、真实风控、部署多实例/连接池或设备验收。


第四十七轮（2026-10-25）：用户确认Evolve单槽、原样Unicode/大小上限、CAS与显式旧迁移，另确认登记游戏旧保存仅application/json。四新入口接总register，当前275（274HTTP+1WS），GET133/POST71/PUT46/DELETE24/WS1；旧Cookie/query/body身份共用当前槽，旧盲写也递增版本，其他cloud键保留。新增表/Repository元数据/旧查询索引，原始字节适配避免Latke有损UTF8变换。七组隔离Java/HTTP与日志专项、实际MySQL完整迁移两次/元数据/同七组及七独立JVM竞争通过，逐项观察InnoDB锁等待，容器清理。全仓822路由/398浏览器引用。完整文件/人工链与未验项见[游戏存档工作记录](api-v3-game-save-work.md)；APP未改，无前端构建/Maven，真实IoC/全局风控/PC/mobile实际Evolve仍未验收，v3总体未完成。


第四十八轮（2026-10-25）：关注用户/粉丝两个原客户端路由独立接既有v3分页，清除旧MemberView people及fishpi旧读取包装；真实总数/分页、403/错误/合法空结果分列，身份/目标代际隔离且普通token刷新保留页。五组新专项、匿名5/昵称样式8回归、类型及client/SSR构建通过；67生成JS版本/map定向扫描零命中。IAB合成21条/第二页503恢复至2/2/禁下一页、昵称标签文本、隐私403和0条空列表，Hydration空；真实后端/全局风控/宽屏真机未验，关注写入/基础资料/其余用户列表仍待迁移。后端275保持，见[客户端关系读取记录](api-v3-client-user-relations-work.md)；目标继续active。


第四十九轮（2026-10-26）：新增本人→指定用户关注状态GET，当前276（275HTTP+1WS），GET134/POST71/PUT46/DELETE24/WS1；单SQL当前有效actor/target及EXISTS，不读其他人关系、不中途写库。客户端共享UserFollowButton与v3UserFollow内存命令接主页/悬浮卡片，明确PUT/DELETE，未知原key跨SPA保留、普通刷新不清，成功后GET不以历史回执当现状；删旧followUser/unfollowUser包装。状态SQL/真实HTTP四组SQLite与MySQL通过、旧关系回归通过，客户端六组与关系5/样式8回归、类型/client/SSR构建通过。IAB本地合成PUT丢响应→原key回放、DELETE成功后GET503→仅重读，日志一次PUT一次DELETE；1280/390两视口布局及Hydration空。完整文件/人工链见[关注操作记录](api-v3-client-user-follow-work.md)；真实Java IoC/账号/全局风控、真实服务悬浮卡片基础资料链与其余模块仍待验，目标保持active。

同批收尾补证：资料卡内点击后重试按钮DOM替换会丢焦点并关闭弹层，浏览器已复现；改为单一稳定按钮与aria-disabled/busy，实际卡片“重试→取消→后置GET失败→重读”保留焦点和弹层，组件专项追加节点稳定断言后六组通过，构建复跑通过。仅本地夹具，未替代真实服务验收。


第五十轮（2026-10-26）：客户端关注标签/收藏/订阅三个既有member路径及/stars接SavedContentView/v3-saved-content，本人/me保留不可读占位、他人公开接口遵守隐私；真实分页/错误/空状态、身份与目标/kind隔离，不依赖旧userinfo。删旧collected helper与三项缺口提示；共享摘要保留匿名标记，ArticleFeed不再生成匿名用户假主页链接或作者简介/头像。七组专项、内容/关系5/关注6回归，类型/client/SSR构建、68生成JS正式版本审计零命中。IAB合成21条/第二页503恢复2/2、订阅占位/匿名、标签文本与0计数、403/合法空、1280/390两viewport及Hydration空。后端276保持，无Java/FTL/SQL/APP/Maven；真实全局风控/业务权限与真机仍待验，详见[保存内容读取记录](api-v3-client-saved-content-work.md)，其余动态/互动/私信/聊天室继续。


第五十一轮（2026-10-26）：/watch三个客户端分类接既有用户/标签文章聚合及本人关注清风明月，固定source/严格分页、当前读者/分类隔离、正常token刷新保留页、SSR准备态，无全站/旧API兜底。新增BreezemoonList复用公开作者白名单，仅用服务端已清洗contentHtml；原SideBar仍单独旧链。六组新专项、关系5/保存7回归，类型/client/SSR构建通过，68生成JS正式版本审计0命中/无map。IAB合成短页按total翻页、第二页503恢复末页、用户403/合法空、清风格式/昵称文本及1280/390视口、Hydration空；真实服务内容权限/清洗/全局风控/真机未验，详见[关注动态记录](api-v3-client-following-feed-work.md)。后端276保持，无Java/FTL/SQL/APP/Maven；完整v3目标继续，关系/内容写入、全站清风、私信/聊天室等仍在队列。

第五十二轮（2026-10-26）：全站清风明月、首页及通用侧栏接既有v3读写；明确POST/PUT/DELETE与同键未知恢复共Pinia核心，成功后重读，编辑先取原文、管理按作者ID。修正跨入口恢复后原草稿未清问题。SSR载荷改per-app注入，公共预取分scope，挂载后当前读者重读；既有消费者回归。8组专项、SSR4/关注6/保存7/认证12回归、类型及client/SSR构建通过；68生成JS版本审计0命中/无map。IAB合成响应丢失侧栏恢复、编辑后GET失败只重读、分页503恢复、取消删除、1280/390无横溢出；删除实际提交仅隔离测试。首页既有签到/复读组件hydration差异仍待修，不能称整页SSR通过。城市/个人清风旧读取、文章评论写入/其他关系/私信/聊天室等继续。后端276保持，无Java/FTL/SQL/APP/Maven，见[清风客户端记录](api-v3-client-breezemoons-work.md)。

第五十三轮（2026-10-26）：新增本人标签关注/文章收藏/订阅三个单项状态GET，content.read，严格owner/type与有效目标、文章共享可见性，白名单只targetId/following。客户端标签页、普通/长篇文章共ContentRelationButton/v3ContentRelations，明确PUT/DELETE，未知原命令key跨SPA保留，成功后GET，读失败不重写；修正另一组件提交时旧GET覆盖的并发问题，同步用户关注按钮。6组Java/实际HTTP与一次性MySQL、旧写核心4组、前端9组及用户关注7/保存7/内容适配/认证12回归通过，类型/client/SSR构建完成。IAB合成丢ACK恢复、取消后读失败、订阅、标签403→成功、长篇菜单及1280/390无横溢出。正式静态审计结果见work；既有文章页登录/分享/编辑器hydration差异仍待修，非完整页面验收。注册279（278HTTP+1WS），GET137/POST71/PUT46/DELETE24；APP未动，无SQL/Maven；详见[内容关系写入记录](api-v3-client-content-relations-work.md)。


第五十四轮（2026-10-26）：修复客户端首路由 mount 前恢复认证导致的 SSR 首帧差异。每应用 clientStartup 在挂载后恢复，AppShell 私有页等当前认证并提供临时失败重试；保留游客原路径、已登录登录页回跳和后续 SPA 守卫，迟到初始恢复不覆盖新导航。启动9组、认证12/缓存3/SSR4/内容关系9/关注7/保存7回归、类型及 client/SSR 构建通过；5路真实本地 HTTP SSR 合成响应通过，69生成JS版本审计0命中/无map。最终浏览器会话不可用，重试按钮完整点击恢复、移动与长篇 hydration 尚待人工复核；不宣称整站 SSR 验收。后端279保持，无Java/FTL/SQL/APP/Maven。实际文件/完整人工链/反漏扫限制见[首次认证恢复记录](api-v3-client-startup-work.md)，其余文章评论写入/城市个人清风/私信/聊天室等继续。


第五十五轮（2026-10-26）：普通/长篇文章投票、感谢及打赏迁移至既有v3，ArticleInteraction共用当前状态/费用确认/明确赞踩与清除；原key未知恢复、成功后GET、另一组件提交废弃旧读，付费正文只从当前合法详情读取。收费后余额错误独立，离页取消本人确认/跨身份丢弃迟到正文；删除三个无消费者旧包装。15组实际TS/Pinia/完整Vue专项、启动9/内容关系9/内容适配/认证12/ZIP4/生命周期3回归，类型及client/SSR构建通过；两路真实本地HTTP SSR游客付费区与请求日志通过，69生成JS版本审计0命中/无map。浏览器会话不可用，真实点击/双视口、服务器权限/原子账款/全局风控待验。后端279保持，无Java/FTL/SQL/APP/Maven；实际文件/完整人工链见[文章互动记录](api-v3-client-article-interactions-work.md)。评论写入/reaction/文章编辑/私信聊天室等仍继续。


第五十六轮（2026-10-26）：客户端评论POST/PUT/DELETE、原文与长篇段落/段评读取接既有v3；共享CommentComposer/v3Comments原输入/key未知恢复，默认编辑省略visible，作者ID控制，离页/换号/切回复目标取消本人确认。成功清缓存并强制当前GET，读失败只重读，余额错误独立；长篇取当前锚点HTML、统计完整校验、平铺段评及本人编辑/删除/恢复，未知统计不作0。CommentItem不泄隐藏HTML/匿名假链接；删六旧包装。16组实际TS/Pinia/完整Vue/普通文章全组件联动，互动15/关系9/内容/SSR4/认证12/启动9/生命周期3回归、类型/client/SSR构建通过；两路真实本地合成HTTP SSR与请求日志通过，71生成JS版本审计0命中/无map。浏览器无会话，长篇侧栏DOM/双视口/真实权限账款/风控未验；匿名本人编辑删除入口证明、评论其他互动/文章编辑/上传/私信聊天室仍待迁移。后端279保持，无Java/FTL/SQL/APP/Maven；完整文件/人工链见[评论客户端记录](api-v3-client-comments-work.md)。


第五十七轮（2026-10-26）：普通评论/楼中楼与长篇段评接既有v3赞踩GET/PUT/DELETE，复用ArticleInteraction并让API/命令/完成通知/在途身份均按articles/comments隔离；删除旧toggle消费者及包装。未知固定choice/key跨SPA和轮换恢复，成功GET当前状态，读失败不重写；异步保留按钮DOM并阻止重复，换号/切资源废弃迟到结果。12组真实TS/Pinia/完整组件与CommentItem专项、文章互动15/评论16/认证12/启动9/生命周期3回归，类型与双构建、71 JS版本/map审计通过。普通/长篇整页合成HTTP SSR含公开/匿名私密评论及零私有请求日志通过；浏览器无会话，长篇侧栏完整DOM/双视口/真机/真实权限与通知未验。后端279保持，无Java/FTL/SQL/APP/Maven；感谢/采纳/reaction、文章编辑/上传/私信聊天室等继续。完整文件/人工链见[评论赞踩记录](api-v3-client-comment-votes-work.md)。


第五十八轮（2026-10-26）：补评论感谢/采纳两状态GET并接总register，现281（280HTTP+1WS），GET139/POST71/PUT46/DELETE24；生产SQL绑定当前有效actor/author/comment，文章可读及私密正文门禁后投影状态/基本资格/费用，匿名0费，不返作者或其他采纳ID。普通/楼中楼/长篇段评共ArticleInteraction接感谢/采纳，费用确认、原键恢复及成功当前GET；采纳完成刷新全部挂载候选，删两旧包装/处理。Java25五组实际SQL/Dispatcher/Auth专项分别SQLite及临时MySQL通过；客户端11/赞踩12/评论16/文章互动15/认证12/启动9/生命周期3、类型与双构建通过，71 JS版本审计0/无map及合成普通长篇SSR通过。浏览器无会话，真实费用/账本/通知/权限风控、长篇侧栏完整DOM与双视口未验；APP未动，无SQL迁移/Maven/提交推送。详见[感谢采纳记录](api-v3-client-comment-rewards-work.md)，reaction/文章编辑/上传/私信聊天室等继续。


第五十九轮（2026-10-26）：文章/评论/长篇段评/聊天室两布局共V3ReactionBar，既有目录/三类GET/PUT/DELETE，按类型+ID保原choice/key未知恢复，当前GET覆盖历史回执。旧Article/Chat事件仅失效提示，不采他人summary/actor选择；删旧toggle/应用快照函数、chat.react及无消费者历史reaction投影。登录后自动读，游客等认证完成后显式读，实时不替游客扇出；面板保DOM且重复操作受控。16组真实TS/Pinia/完整Vue/CommentItem及生产chat处理专项、评论16/赞踩12/感谢11/文章互动15/认证12/启动9/生命周期3回归，类型与双构建、71 JS版本审计0/无map/sourceURL、普通长篇合成HTTP SSR通过。浏览器无会话，长篇/聊天整页DOM、双视口/焦点/真机、真实权限及Socket节点未验；不宣称v3 Socket或聊天室主体已迁移。后端281保持，无Java/FTL/SQL/APP/Maven/提交推送；文章编辑/历史/上传、匿名本人管理、私信/聊天室主体等继续。完整文件/人工链见[表态客户端记录](api-v3-client-reactions-work.md)。


第六十轮（2026-10-26）：文章/评论历史共RevisionHistory，普通/长篇原入口、根评论/楼中楼/段评接既有四GET；惰性读取、严格真实分页/列表详情绑定、两层重试、清洗文本纯文字展示，移除旧预览POST和三个无消费者包装/类型。current只取列表，评论未知时间不伪造；收起/换身份/目标/版本丢迟到结果。ArticleView以稳定viewerIdentity代替token字符串，正常轮换不卸载历史。14组生产API/完整Vue专项、评论17含真实ArticleView+History/API/ref联动、reaction16/赞踩12/感谢11/文章互动15/认证12/启动9/生命周期3回归、类型与双构建、71 JS版本0/无map/sourceURL及普通长篇合成HTTP SSR通过。浏览器无会话，实际历史权限、长篇菜单/侧栏完整DOM、双视口/焦点/真机与全局风控未验。后端281保持，无Java/FTL/SQL/APP/Maven/提交推送；文章编辑/发布/上传、匿名本人管理、私信/聊天主体等继续。完整文件/人工链见[历史客户端记录](api-v3-client-revisions-work.md)。


第六十一轮（2026-10-26）：PostView本人source、发布/typed部分编辑、草稿分页/详情/POST/PUT/DELETE接既有v3；source失败无假表单，私有打赏原文、未改元数据/章节保持，草稿hidden thought/type4可存，新思绪/不支持类型仍拒。v3Posts固定op/id/input/key确认及恢复，发布传draftId交原核心消费不追加DELETE；metadata不清输入，列表/余额/导航失败不重付，确认后防重发/续编需当前source。27组实际API/Pinia/完整PostView、评论17/历史14/reaction16/赞踩12/感谢11/互动15/认证12/启动9/生命周期3回归、类型双构建、71 JS版本0/无map/sourceURL及四条私有作者入口HTTP SSR通过。浏览器无会话，真实Vditor/权限/账款/草稿事务/双视口/风控未验；预览仍旧markdown、上传/@/内部emoji/标签联想/专栏选择待迁移，不能称整个编辑器完成。后端281，无Java/FTL/SQL/APP/Maven/提交推送；文章删除/置顶客户端、匿名本人管理及私信/聊天主体继续。见[文章草稿记录](api-v3-client-posts-work.md)。

第六十二轮（2026-10-26）：新增Markdown预览POST，后端总282（281HTTP+1WS）；原始字节JSON/有效Unicode、content.read、本地私有渲染、独立预算及并发保护。共享MarkdownEditor以同步transform清空本地fallback，再异步parse接v3；修改/身份变化/卸载取消并清除旧输出，错误纯文本+显式重试。四组Java25实际HTTP/真实Flexmark与Jsoup清洗、七组客户端API/适配器专项，文章27/评论17回归、类型双构建、71JS版本0及无map/sourceURL通过。IAB真实CDN Vditor在合成后端完成输入→预览、503重试，观察到Bearer请求；不是Java真实服务/完整双视口验收。接口/文件/人工测试与限制见[Markdown预览记录](api-v3-markdown-work.md)。上传/@/内置表情/标签联想、专栏选择及文章删除/置顶客户端等继续，未提交推送。

第六十三轮（2026-10-26）：共享MarkdownEditor上传接既有v3 tickets/专用票据直传，去旧apiKey门禁；唯一multipart名与逐项succMap/errFiles绑定，部分成功仅插成功项并提示，网络未知不自动重传，正文替换/身份/卸载取消旧输出。真实CDN Vditor发现尖括号URL截成https:，修正后桌面/390×844移动选择合成PNG复验完整目的地。九组专项、文章27/评论17/预览7/资料7回归，类型双构建与71JS版本0/无map通过。真实RhyPic/生产代理与上传后发布回读尚未验收；其他旧上传调用方、@与内部表情继续。后端282保持，无Java/FTL/SQL/Maven/APP/提交推送。文件与人工链见[编辑器上传记录](api-v3-client-editor-upload-work.md)。

第六十四轮（2026-10-26）：Editor原生表情HTTP改自定义工具栏→既有v3 EmojiPicker；移除旧apiKey缓存/包装/链接，惰性常用与图片组，正常token轮换保留，换身份清除。Vditor本地Unicode仅显示、服务端别名仍为插入值；无本地映射显示完整文本。源码复核明确insertValue走HTML template是前轮尖括号截断根因，共享表情/上传/公开insert统一insertMD。7组完整Editor+Picker/TS专项、表情9/上传9/文章27/评论17/预览7/资料7回归及类型双构建通过；真实CDN Vditor桌面别名/图片→源码和上传、390×844移动图标/弹层目视通过，合成HTTP不代表真实权限/图床/发布回读。后端282保持；已确认@联想profile.read、用户名ASCII前缀1..64/忽略大小写/当前有效/最多10/公开四字段、空前缀空列表，下一批实现。见[编辑器表情记录](api-v3-client-editor-emojis-work.md)。

第六十五轮（2026-10-26）：新增用户前缀联想GET并接共享Editor @，总283（282HTTP+1WS）。当前账号/目标status、LOWER参数查询与下划线转义，空前缀不读候选，四字段投影、独立预算/并发保护。客户端立即结算被替换promise再取消旧HTTP，输入捕获阶段清旧候选，避免Vditor延迟正文回调关闭当前列表；原生键盘无需toolbar别名，移除试验兼容代码；当前Vditor自身最多显示8项/触发31位前缀，API独立支持10项/64位。4组SQLite+4组MySQL、7组API/异步专项及相关回归/双构建通过；实际CDN Vditor合成HTTP桌面方向键回车和390×844移动原生点击通过。真实服务/权限/生产查询计划仍待编译后验；Vditor本地触发/显示限制见[用户联想记录](api-v3-user-suggestions-work.md)。旧入口/其他旧消费者不改，标签联想/专栏选择等继续。

第六十六轮（2026-10-26）：PostView已有专栏去手填ID，接既有me/columns本人分页选择，失败重试/保留未在本页绑定，只明确选择回填id/title，章节/封面不自动改。7组真实API/完整Vue、文章28含父选择→部分PUT/草稿字段链，评论17/联想7/表情7回归及类型双构建通过。IAB真实长文表单+合成HTTP验证第二页503→重试→选末页→存草稿，后端收到匹配id/title/chapter；390×844移动select/分页目视通过，非真实保存/权限证据。后端283保持，无Java/FTL/SQL/Maven/APP/提交推送。标签新联想规则已获确认，下一批实现；见[本人专栏选择记录](api-v3-client-author-columns-work.md)。

第六十七轮（2026-10-26）：新增标签联想GET，总284（283HTTP+1WS）；现有字符表/trim/64、当前有效本人、Tag状态/正引用数并EXISTS实际文章关联，热门/前缀最多7、只投影名称、独立预算/并发保护。TagInput按光标token替换，其他分隔符/空白/标签保留，不自动新增/去重/截断；debounce/IME/键盘、身份与迟到隔离，失败可手输。4组SQLite+4组MySQL、9组生产API/完整Vue、文章29含父TagInput→冻结POST及相关回归/双构建通过；IAB桌面中间C+→C++并草稿收到完整原串、键盘选择和390×844移动中文点击通过，合成HTTP非真实服务。文件/人工链见[标签联想记录](api-v3-tag-suggestions-work.md)。其余总清单与真实服务验收继续，无FTL/新表/Maven/APP/提交推送。

第六十八轮（2026-10-27）：客户端本人文章管理接既有source/DELETE/stick，惰性私有证明仅留id/status，匿名不猜作者；100积分与不可恢复确认、未知原目标/键跨SPA恢复、身份取消隔离。成功回执与当前GET分开，读失败只GET，删除先清正文且导航失败不重删；长文移动菜单独占行。12组owner/18组comments/29组posts/15组interactions与类型双构建通过，浏览器合成HTTP桌面/390px菜单和取消确认通过，无真实扣费/删除/权限或全局防扫描运行验收。284注册不变，无Java/FTL/SQL/Maven/APP/提交推送；见[本人文章管理记录](api-v3-client-article-owner-work.md)。

第六十九轮（2026-10-27）：文章页移除旧article-channel正文追加及旧热度GET，改已有v3票据/绑定ready和article.changed；明确读取当前HTTP、失败可重试、权限丢失清正文，读取中较新事件保留，正常token轮换重新取票。热度精确目标/非负整数/失败非0、本节点近似连接口径，游客不查鉴权热度或订阅。固定全屏长文live-status同步移动。实时6/通知6/评论20/owner12/reaction16与类型双构建通过；真实浏览器+合成WS/HTTP验证首帧、提示→HTTP正文及390px长文，无真实Java权限/过期/风控运行验收。284保持，无Java/SQL/Maven/APP/提交推送；随机文章旧链仍保留，见[文章实时记录](api-v3-client-article-realtime-work.md)。

第七十轮（2026-10-27）：独立/chat页接V3PrivateMessages/v3Messages/v3-messages，严格用户ID/参与者/游标、会话分页/历史/未读/解析、发送/撤回/显式已读全主链。未知kind/target/body/key跨SPA恢复、成功后当前GET、身份清除及正常轮换保留草稿，既有ChatComposer/MarkdownEditor工具保留。messages实时共用首帧协议，只提示/重新查询不拼正文。私信15/实时7/通知6/评论20及类型双构建通过；合成HTTP浏览器保存后503→同键恢复→当前正文和390px真实编辑器发送通过，无真实账号消息。284保持，无Java/SQL/Maven/APP/提交推送。抽屉私信/全局旧未读与聊天室仍待迁移；详见[独立私信记录](api-v3-client-private-messages-work.md)。

第七十一轮（2026-10-27）：抽屉复用V3PrivateMessages，共享privateInbox管理messages订阅/未读/入口，不存正文；主页面/抽屉/顶栏共当前计数，查询在途+尾随合并、失败未知非0，换身份清目标与窗口、正常轮换保草稿并重取票。删旧whispers及私信API包装/用户名正文拼接/自动已读。21组私信（含实际Drawer→Panel→ChatComposer）、通知6/实时7/启动9及类型双构建通过；浏览器合成HTTP+WS验证抽屉503→关闭→全页同键恢复、未读503与全局恢复、390px移动。真实Java权限/事务/风控和拖动缩放仍未验；284保持，无Java/SQL/Maven/APP/提交推送，聊天室主链仍待迁移。见[抽屉与未读记录](api-v3-client-private-inbox-work.md)。

第七十二轮（2026-10-27）：聊天室原文/引用读取接既有v3详情，严格DTO/目标与卡片公开投影，Bearer入口，当前清洗预览不采旧列表HTML；独立弹窗打开/错误、请求序号/取消及身份清理，多行引用/作者转义。13组专项+表态16/启动9与类型双构建通过；布局/外观统一SSR默认并挂载后恢复偏好，复验warn/error空。完整ChatroomView合成HTTP桌面现代原文、当前预览及390px经典通过，无发送操作。主历史/普通发送/撤回/实时/特殊能力仍旧链，完整引用发送未验；284保持，无Java/SQL/Maven/APP/提交推送。见[聊天室原文记录](api-v3-client-chat-source-work.md)。

第七十三轮（2026-10-27）：普通聊天室发送/引用→v3 POST→当前详情GET联通，v3ChatPublication原content/client/key跨SPA，平台confirmation_required明确确认原键，未知恢复4xx保原命令；成功/当前读取分开，GET失败不重发。主页面与抽屉成功且输入仍匹配才清草稿，当前GET投影去重，普通发送不依赖旧apiKey/WS；旧特殊能力显式分开待迁移。发送15/私信21/原文13/表态16与类型双构建通过；完整浏览器合成HTTP验证引用+正文保存后503→原键恢复→详情503→GET恢复及390px经典，warn/error空，无真实发信。284保持，无Java/SQL/Maven/APP/提交推送；历史/实时/撤回/在线/特殊能力继续，见[普通发送记录](api-v3-client-chat-publication-work.md)。

第七十四轮（2026-10-27）：v3ChatTimeline接消息游标/详情/topic与chat首帧实时，ID/revision白名单、ready状态、同步重读、串行目标队列/迟到与删除竞争隔离、401/403清授权。主chat facade删除旧正文WS/节点凭据/历史页数，Home/SSR删除旧聊天正文与快照来源，首页发送复用v3；浮动/完整/首页共享，SCSS移动内容填满、输入置底。Timeline15/实时9/发送15/原文13/私信21/表态16/通知6/启动9，共104组及类型双构建通过；浏览器合成HTTP+实际WS验证before503重试、更新GET、删除404、sync当前窗口、清屏恢复、390px抽屉与首页摘要，warn/error空。284保持，无Java/SQL/Maven/APP/提交推送；在线/mutes/上下文/撤回/特殊能力与真实全局风控等仍待，见[历史实时记录](api-v3-client-chat-timeline-work.md)。


第七十五轮（2026-10-27）：聊天室在线名单接已有v3可见用户分页，完整页双布局/侧栏/Home共用；total区别本页条数，失败未知/原页重试、采样范围说明、身份迟到隔离和同账号轮换当前页刷新。删除旧online wrapper/SSR载荷，首页当前话题响应共享状态。在线13及相关回归共117组、类型双构建、70JS版本0/无map通过；IAB合成HTTP+实际WS完成20/21→第二页503→原页1/21重试、390px展开、简约和首页，warn/error空。284保持，无Java/SQL/Maven/APP/提交推送；真实隐私/多节点/全局风控及禁言/上下文/撤回/特殊写继续，见[在线名单记录](api-v3-client-chat-online-work.md)。


第七十六轮（2026-10-27）：聊天室本人撤回接既有v3 DELETE，经典/简约/浮窗按作者ID认领，确认费用后提交，原id/key跨SPA恢复、未知重试4xx不丢命令，退出隔离。成功清共享timeline及表态，删除集合阻止旧历史/详情/发送投影复活。撤回11及相关共129组、类型双构建、72JS版本0/无map通过；IAB合成服务提交后503→首页浮窗同键恢复→完整/390px经典成功成本32，warn/error空，无真实撤回扣款。284保持，无Java/SQL/Maven/APP/提交推送；禁言/上下文/特殊写及真实事务/全局风控仍待，见[撤回记录](api-v3-client-chat-revoke-work.md)。


第七十七轮（2026-10-27）：消息上下文接既有v3 GET，独立窗口/前后游标/定位高亮，经典/简约按钮与hash引用深链接、登录恢复及返回最新消息联通。失败原方向重试，权限/404清正文，changeHint使相关窗口/在途页失效，不另开WS或污染最新窗口。上下文13及相关共142组、类型双构建、72JS版本0/无map通过；IAB合成HTTP+实际WS验证最近窗口外深链接、前页503保留/重试/边缘、390px后页与返回最新清hash，warn/error空。284保持，无Java/SQL/Maven/APP/提交推送；禁言/特殊写及真实可见性/全局风控仍待，见[上下文记录](api-v3-client-chat-context-work.md)。


第七十八轮（2026-10-27）：话题修改接既有v3 PUT，经典/简约/侧栏共用编辑与恢复，16积分确认、原text/key未知恢复、平台二次确认及身份隔离；成功GET当前值，失败只重读不重付，匹配原输入清理。timeline主动话题读不依赖chat订阅，删除旧setDiscuss包装。话题13及相关共155组、类型双构建、72JS版本0/无map通过；IAB合成HTTP验证提交后503同键恢复→GET503→当前值后改、390px简约成功关闭及侧栏共用，warn/error空，无真实话题/扣款。284保持，无Java/SQL/Maven/APP/提交推送；弹幕/禁言/其他特殊写和真实风控仍待，见[话题记录](api-v3-client-chat-topic-work.md)。


第七十九轮（2026-10-27）：弹幕价格/独立发送接既有v3，经典/简约/侧栏共用费用确认与原content/color/key恢复；未知价格阻止新发，成功回执不补播，匹配原输入才清理。认证chat.barrager白名单DTO经timeline独立ref→AppShell唯一纯文本瞬时层，最多5条/8秒/有界去重，身份/断连清理，不改历史/上下文。弹幕13及相关共170组、类型双构建、72JS版本0/无map通过；当前无浏览器工具，双视口及真实费用/广播/风控未验，不能算运行验收。284保持，无Java/SQL/Maven/APP/提交推送；禁言/其他特殊写和真实全链继续，见[弹幕记录](api-v3-client-chat-barrage-work.md)。


第八十轮（2026-10-27）：音乐卡片接既有v3独立POST，经典/简约共享四字段编辑/原key恢复/二次确认，成功GET当前消息后合并timeline，读取失败只GET重试，身份及旧详情隔离。数字ID安全跳转网易云，HTTPS显式播放、保留历史安全HTTP，失败提示和Promise乱序保护；无普通消息命令。音乐16及相关共186组、类型双构建、72JS版本0/无map通过；无浏览器工具，双视口/真实发信与音源/风控未验。284保持，无Java/SQL/Maven/APP/提交推送；思过崖新口径待确认，天气/红包及真实全链继续，见[音乐卡片记录](api-v3-client-chat-music-work.md)。


第八十一轮（2026-10-27）：天气卡片接既有v3独立POST，经典/简约共享首日生成五天、空天气/温度必填、严格代码/日历/整数范围；原city/description/days/key恢复与二次确认，成功GET当前消息后入timeline，失败只重读，身份/旧详情隔离。天气16及相关共202组、类型双构建、72JS版本0/无map通过；无浏览器工具，真实控件/双视口/发言资格/风控未验。284保持，无Java/SQL/Maven/APP/提交推送；思过崖口径待确认，红包和真实全链继续，见[天气卡片记录](api-v3-client-chat-weather-work.md)。


第八十二轮（2026-10-27）：五类红包创建/详情/领取接既有v3，经典/简约/隐藏会话/浮窗共享状态；明确费用及风险确认，猜拳手势必选，原命令/key跨SPA恢复，成功回执与当前GET独立，旧历史不自动转换。红包23及相关共225组、类型双构建、72JS版本0/无map通过；无浏览器工具，真实扣款/守恒/并发/权限/风控及双视口未验。284保持，无Java/SQL/Maven/APP/提交推送；思过崖口径已确认待实施，见[红包记录](api-v3-client-chat-red-packets-work.md)。


第八十三轮（2026-10-27）：思过崖新增GET /api/v3/chat/mutes/content.read，实际ID禁言与当前有效账号、未到期筛选、五公开字段及分页，不读旧用户名快照。独立侧栏按需共享读取、错误未知/原页重试/身份隔离，双布局通用；旧fetchMutes删除。SQLite/MySQL各5组、客户端11及相关共236组、类型双构建、72JS版本0/无map通过。285条（284HTTP+1WS）；Java已提醒IDEA重编/重启，无Maven/迁移/APP/提交推送；真实服务/浏览器/全局风控待验，见[思过崖记录](api-v3-chat-mutes-work.md)。


第八十四轮（2026-10-27）：聊天室涂鸦接既有v3票据与共享具名PNG上传，经典/简约同组件；只插入草稿，不自动发布。上传冻结编辑，失败保画布，关闭/离页/换人/退出取消迟到结果，同账号轮换保留；删无消费者旧uploadFiles链。涂鸦13、上传9及相关236共258组、类型双构建、72JS版本0/无map通过；无浏览器工具，真实像素/触屏/图床/发送与风控待验。285保持，无Java/SQL/Maven/APP/提交推送，见[涂鸦记录](api-v3-client-chat-paint-work.md)。


第八十五轮（2026-10-27）：举报四目标接既有v3目录/提交，文章/长文/评论/用户及聊天室两布局共用；真实分类无兜底、备注可空、按ID识别他人，原target/type/reason/memo/key跨SPA未知恢复，平台明确确认、身份隔离、全局仅编号回执。删旧reportContent。举报19及相关共323组、类型双构建、72JS版本0/无map通过；无浏览器工具，真实权限/DB去重/风控及双视口待验。285保持，无Java/SQL/Maven/APP/提交推送，见[举报记录](api-v3-client-reports-work.md)。


第八十六轮（2026-10-27）：复读机首页/独立页迁移既有v3榜单、分类、服务端next、投稿及明确PUT/DELETE点赞，原命令/key全局恢复与平台确认，成功只GET当前榜单不回写历史计数；Home/SSR明确joke，纯文本不剥HTML。新增18组，tools目录60脚本全部通过共613组，类型/客户端/SSR构建、73JS版本0/无map通过。285路由保持，无Java/FTL/SQL/Maven/APP改动；浏览器双视口、真实权限/DB/风控仍待验。本批按用户要求两仓提交，见[复读机记录](api-v3-client-repeater-work.md)。


第八十七轮（2026-10-27）：/games鱼游迁移既有v3目录/两排序/分页/详情/评论、明确赞踩清除、五字段投稿及本人编辑（OAuth必填），原命令/key全局恢复，成功独立GET不写历史回执正文/计数，待审不等于公开批准。旧submitFishGame与暂无JSON提示删除，官方游戏直接导航经典站点。61脚本回归630组全通过，最终鱼游专项18组、类型/客户端/SSR构建、73JS版本0/无map通过；两仓diff检查通过。285路由保持，无Java/FTL/SQL/Maven/APP，浏览器双视口/真实审核OAuth/事务权限/反漏扫链待验。本批两仓提交，见[鱼游客户端记录](api-v3-client-fish-games-work.md)。


第八十八轮（2026-10-27）：同城移除近50清风过滤/旧apiKey门槛/SWR/v-html，改已有v3公开用户和署名文章独立分页/total/排序/原页错误重试；单次城市编码，切城/凭据/迟到隔离，头像/昵称/ArticleFeed共用。13专项、62脚本644组、类型双构建、73JS版本0/无map通过；真实Chrome生产构建+SSR夹具完成1280用户p2故障→原页恢复/文章末页与390排序/末页，page-error/hydration warn无，预期401/503单列。285路由保持，无Java/FTL/SQL/Maven/APP；真实位置隐私/风控/后端运行待验。按用户要求两仓提交，见[同城记录](api-v3-client-cities-work.md)。


第八十九轮（2026-10-27）：大事记页接既有五接口，公开SSR时间线/本人筛选与独立分页详情、Markdown投稿及原键跨SPA恢复；本人历史原文经当前preview清洗，失败无原HTML回退，提交回执不代替当前审核状态。18专项、63脚本663组、类型双构建、74JS版本0/无map通过。真实Chrome生产构建+SSR合成HTTP完成1280已提交503→同键仅创建一次→待审GET/预览503重试及390本人深链接/公开末页详情；无page-error/水合警告，预期503单列。285路由保持，无Java/FTL/SQL/Maven/APP；真实权限/审核事务/风控仍待验。按用户要求两仓提交，见[大事记记录](api-v3-client-milestones-work.md)。


第九十轮（2026-10-27）：/statistic页脚改SPA统计，复用现有GET九项数字白名单与每请求公开SSR；错误清旧快照/不填零、原资源重试、凭据/迟到隔离，桌面三列/移动两列。统计12专项、64脚本676组、类型双构建、75JS版本0/无map通过；真实Chrome生产构建+SSR合成HTTP完成1280刷新503→旧数字清除→重试恢复和390九项/零值/刷新，无page-error/水合警告，预期401/503单列。285保持，无Java/FTL/SQL/Maven/APP；真实业务统计、权限与反漏扫链未验。本批两仓提交，见[统计记录](api-v3-client-statistics-work.md)。


第九十一轮（2026-10-27）：账号页APP码改既有v3三网页POST，本地矩阵SVG/两分钟/手动状态/APP确认后网页明确批准或拒绝，未知决定只查当前状态，consumed才显示完成；清除旧apiKey/外部QR链，APP源码不动。扫码16专项、65脚本692组、类型双构建、73JS版本0/无map通过，独立jsQR解码回读一致。真实Chrome合成协议1280批准已保存503→只GET语义的状态POST恢复→consumed，390本地码/拒绝通过，无page-error/水合警告，预期503及原密码verbose提示单列。285保持，无Java/FTL/SQL/Maven；真实APP/issuer/单次换码/风控未验。本批两仓提交，见[网页扫码记录](api-v3-client-app-login-work.md)。


第九十二轮（2026-10-27）：普通十五榜/全部游戏子类接既有v3有限快照分页，原页重试/子类重置/身份迟到隔离、赞助汇总/安全链接；Top目录只导航，职业独立。Home连签/在线size8独立预览/错误/SSR，不入旧缓存，删除旧top包装和mock导出。16专项、66脚本709组、类型双构建、74JS版本0/无map通过。Chrome生产构建+SSR合成HTTP完成1280连签p2故障→rank21/子榜/赞助与390Home前8→更多→p2重试；修复短末页停页脚并复验定位，无page-error/水合警告，预期401/503单列。285保持，无Java/FTL/SQL/Maven/APP；真实快照/权限/反漏扫未验。两仓提交，见[排行榜记录](api-v3-client-rankings-work.md)。


第九十三轮（2026-10-27）：设置数据页接既有Evolve目录/本人原文GET/CAS保存/显式迁移，透明Unicode与字节/hash核验；原文/版本/key跨SPA未知恢复、冲突不自动覆盖、读取不盖草稿、回执不盖最新版本。18专项、67脚本727组、类型双构建、74JS版本0/无map通过。Chrome生产构建+SSR合成HTTP完成1280已写503→离页同键恢复仅一写→GET503只读重试/原文及390迁移/编辑框纯文本；无page-error/水合警告，预期404/503及相邻夹具限制单列。285保持，无Java/FTL/SQL/Maven/APP；真实DB/游戏导入/权限与风控待验。两仓提交，见[存档客户端记录](api-v3-client-game-saves-work.md)。


第九十四轮（2026-10-27）：Home/活动中心签到面板接既有两GET/奖励POST，真实门槛/状态、独立失败/非假0、原date/key全局恢复，清旧apiKey/持久活跃缓存链。奖励POST加可选expectedDate绑定规范化请求，锁后fresh跨日409、原回执先重放，旧{}兼容。Java25日期五组/通用写六套件（幂等8场景）通过；前端14专项、68脚本741组、类型双构建、74JS版本0/无map通过。Chrome合成1280/390明确领取/取消、已发503→同键恢复仅一笔/当前GET，无page-error/水合警告，预期503单列。285保持，无FTL/SQL/Maven/APP；Java已提醒IDEA重编/重启，真实HTTP/积分/日期/风控未验。两仓提交，见[活动记录](api-v3-client-activity-work.md)。


第九十五轮（2026-10-27）：全局用户卡片改既有v3公开六字段与明确重试，删除无限期LRU/旧角色与在线假默认，ID昵称样式和关注目标、私信保持。UserFollowButton增可选ID绑定，旧仅name调用保持。12专项+关注新增1、69脚本754组、类型双构建、73JS版本0/无map通过。Chrome桌面合成键盘打开→503→重试/纯文本通过，无page-error/水合警告；ID补充后专项回归通过，窄屏采集未完成，真实鼠标/触控/390px仍待。285保持，无Java/FTL/SQL/Maven/APP；主页/徽章页旧公开资料链、真实权限/风控继续。两仓提交，见[用户卡片记录](api-v3-client-user-card-work.md)。


第九十六轮（2026-10-27）：公开勋章页接既有v3用户名分页GET，移除旧完整用户合并/SWR/apiKey，真实总数/原页失败重试、身份迟到隔离和纯文本元数据；保留重复授予，图片URL契约缺口仍待。10专项、70脚本764组、类型双构建、74JS版本0/无map通过。IAB生产构建+SSR合成HTTP完成1280第一页20/总21→p2失败清旧→重试末页/按钮禁用。285保持，无Java/FTL/SQL/Maven/APP；真实窄屏/权限/过滤/风控未验，MemberView继续。两仓提交，见[公开勋章记录](api-v3-client-public-medals-work.md)。


第九十七轮（2026-10-27）：MemberView/SSR改公开六字段，普通/长文章/回帖/清风v3真实分页，错误独立/原页重试/身份迟到隔离；清风详情精确ID与归属、私密评论置空，删除四个无调用旧用户包装。不补缺失角色/积分/在线等字段。13专项、71脚本777组、最终构建证据见第九十八轮更正、73JS版本0/无map。IAB1280合成生产SSR完成20/总21→p2故障→末页重试/私密回帖/清风栏目，最终重载确认详情链接。285保持，无Java/FTL/SQL/Maven/APP；真实隐私/授权/风控/窄屏/写操作待验，公开画像/勋章图片缺口仍在。两仓提交，见[用户主页记录](api-v3-client-member-work.md)。


第九十八轮（2026-10-27）：注册/找回/重置三入口共用既有六v3资源，当前注册选项/服务端captchaId/SMS快照绑定/手机号验证码直提，删旧verify/meta与六包装及假userId。新密码复用6–128/Unicode规则，未知完成先登录核实不自动重发，取消拒迟到、秘密不进URL/storage。20专项、72脚本797组、独立exit0类型双构建、73JS版本0/无map通过。IAB1280合成选项故障→重试、组件故障不发短信及最终重置表单布局通过；未操作真实验证码/账号凭据。更正上轮构建：旧包装删除留下两unused辅助函数，本批清理并独立实核成功。285保持，无Java/FTL/SQL/Maven/APP；真实短信/极验/凭据撤销/风控/390px待验。两仓提交，见[账号入口记录](api-v3-client-account-access-work.md)。


第九十九轮（2026-10-27）：用户批准专栏四GET按当前可读/可列出署名长篇与有效作者过滤，SQL先过滤再数量/最新/热门/分页，无可见章404、SQL失败503，optionalContent/原匿名盾及no-store。旧经典保持。客户端资料/章节独立GET/错误/原页重试、20项分页、原章号/首最新阅读、身份迟到隔离和封面CSS占位；首页架共用严格卡片解码。Java25/SQLite8+接线源码1及既有写核心4通过；客户端14专项、73脚本811组、独立exit0双构建、72JS版本0/无map通过。IAB1280合成20/总21→p2故障→末页重试/占位最终重载通过。285保持，无SQL迁移/FTL/Maven/APP；Java已提醒IDEA重编/重启，真实IoC/MySQL/权限/风控/390px待验。最近注册/随机发现/仅时间类别日志规则已确认、待下一批实现。两仓提交，见[公开专栏记录](api-v3-public-columns-work.md)。


第100轮（2026-10-28）：最近注册/随机发现/日志元数据三GET总接线，288（287HTTP+WS1），GET145/POST72/PUT46/DELETE24。当前有效四字段/随机起点回绕及匿名保护/固定日志类别先过滤再分页，绝不返回data/key2；客户端Home/Activity/Article/Logs及SSR迁移，独立错误与原页重试，删三旧包装。Java25实际服务/SQLite10+源码接线1组及DTO/共享门禁回归通过；客户端74脚本831组、独立exit0双构建、75JS版本0/无map通过。IAB1280合成19人完整/两模块503独立重试、日志p2故障→末页和首页真实入口通过。无SQL/FTL/Maven/APP；Java需IDEA重编重启，真实IoC/MySQL/新路HTTP/权限/反漏扫/390px待验。两仓分别提交，见[本批记录](api-v3-discovery-extras-work.md)。


第101轮（2026-10-28）：用户确认勋章图片profile.read、本人有效持有或指定用户当前公开展示，格式纠正为清洗SVG。两GET总接线290（289HTTP+WS1），GET147。当前SQL权限/定义→固定renderer→SVG白名单→再次SQL核对，no-store/图片响应，保留原始128单位ID，查询参数完整传递，不trim或重复解码；旧/gen保持。客户端Bearer二进制/两并发/Blob img及释放、身份迟到隔离，OwnerMedals与公开页接入，删旧medalSession/gen包装。Java25核心SQL/清洗/实际loopback渲染8组及实际Dispatcher/auth/controller隔离1组通过；75前端脚本841组、认证12组、独立exit0双构建、75JS版本0/无map通过。IAB1280合成公开图片503→单项重试恢复、本人与公开入口实际Bearer通过。无SQL/FTL/Maven/APP；IDEA重编重启、真实MySQL/renderer样式/IoC/风控/390px待验。列表真分页/专栏伪造回退列下批，见[本批记录](api-v3-medal-images-work.md)。
