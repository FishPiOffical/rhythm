# /api/v3 当前进度与剩余工作

第八十二批（2026-11-03）：使用SQL仅植入临时已发布定义/等级、精确文章+作者条件的经验规则和来源输入，不预置进度/效果、不配置积分/勋章奖励。PID47272原Cron实际生成并应用+15，创建进度0→15/起步→进阶；来源重入队仍1效果、15/version1。实际冲正-15使15→0/回到起步；原+冲正一起重入队后仍2效果、0/version2，最高经验15保留。本人/公开API、贡献和记录及桌面/390px页面一致，正负记录由后台产生，无warn/error。临时规则/来源/效果/职业/文章均锁定事务清理，原隐私恢复，职业/积分/回执/余额指纹不变，临时详情404。源码/接口未变，不重复构建；更新NULL外部关联约定并本地提交。受控来源输入不等于v3阅读登记→自然结算→规则的一次完整联调，积分/勋章奖励、故障/多节点等仍未据此验收。见[真实消费记录](api-v3-profession-growth-live.json)。

第八十一批（2026-11-03）：沿职业成长链核对，实际开发库automation=0、user_profession=0，既有52个SUCCEEDED来源事件不能证明经验增长；未导入正式职业规则。本批在独立本地库植入明确的读取夹具（1职业/2等级/26效果，其中23条满足近90天APPLIED非零条件），真实HTTP8组覆盖本人详情/20+3分页/负经验、越权404与匿名401、公开/排行、隐私收回/损坏503及退休历史。真实5173浏览器从登录→职业设置→路线/贡献/分页、390px、公开经验PUBLIC→HIDDEN重读通过，保留等级名次不留旧经验，无控制台warn/error。全部夹具和原隐私行已事务恢复，职业表/积分/回执/余额完整指纹不变，临时详情404。无生产源码、接口契约、Java/FTL或迁移变化；只提交本地验收记录，不重复全构建；实际后台规则消费/发奖与职业增长仍待单独验证，不能将读取夹具当真实发奖。见[职业读取记录](api-v3-profession-nonempty-live.json)。

2026-11-03 提交约定更新：用户已恢复每次修复后的本地Git提交，两个仓库分别提交、不推送。以下历史联调记录的“未提交”描述记录当时状态，不能据此覆盖最新用户要求。

第八十批（2026-11-03）：用户再次确认重编，PID47272通过旧别名与原URL目标7组真实检查；桌面/390px第2页定位、受限标题隐藏、前进/后退恢复通过，两文章/23评论已事务清理，登录退出后原内容/余额/回执/账本指纹仍未变。等待重启期间复现并修LoginView可选用户名存储异常阻断成功跳转、路由失败无独立恢复：捕获读写存储失败，成功后清密码/MFA并隐藏登录表单，识别真实Vue Router导航失败，原目标重试不再次认证；已有登录仅挂载后导航。新增真实组件6组，96脚本1074条及类型/client/SSR构建通过；隔离浏览器实测阻断存储/阻断路由恢复，真实开发账号完成登录→原评论定位→退出，控制台无错误。293契约/访问、338源码和79产物检查保持通过。最新Java已完成本次联调，无待重启或迁移；其他业务链路/生产风控/真机仍未全验，未提交推送。详见[本批记录](api-v3-login-return-live.json)。

第七十九批（2026-11-03）：用户重编后PID45335通过真实旧别名6组HTTP，23条混合canonical/provisional记录合并为第2页且桌面滚到目标、受限标题未泄露；临时两文章/23评论已事务清理，原内容/余额/回执/账本指纹不变。浏览器同时确认旧短链重写到开发IPv6时生成无效href，最新修复让v3双视图保留原URL及query/fragment，普通旧Function入口仍走原重写行为；新URL保留模式额外校验配置来源字面前缀，防正则相似域名被当站内标题展开。项目Flexmark/SQLite解析迁移15组，相关18/43/66组回归通过。最新仍为LongArticleParagraphService、ShortLinkQueryService两Java，须再次IDEA重编后验原href、PC/390px和历史返回；无新迁移/客户端生产代码，未提交推送，详见[目标地址回归](api-v3-paragraph-link-target-regression.json)。

第七十八批（2026-11-03）：继续真实旧段落别名验证，发现受限文章短链的双视图在请求地址不同于配置地址时生成不同href，真实8081返回paragraph_mapping_conflict409；两份临时文章均已回滚，业务指纹未变。修ShortLinkQueryService增加原URL标签回调、LongArticleParagraphService让双视图链接属性一致而只替换受限标题，既有普通短链回调保持；目标/结构真实不一致仍409。段落runner改用pom版本实际Flexmark转换器，修正旧HTML包裹Markdown的非真实夹具，14组解析/SQLite迁移及当前内容18、评论43、文章66共141组通过。API/更新记录同步，无客户端生产代码或迁移。本批两Java待用户IDEA重编确认，修后真实HTTP、旧别名第2页及浏览器仍未验；未提交推送，见[回归记录](api-v3-paragraph-safe-link-regression.json)。

第七十七批（2026-11-03）：用户确认七Java重编，核对8081 Java PID36957及无凭据JSON后完成原始CHAR评论开关46组真实检查、评论定位13组HTTP检查；普通/active/orphaned第2页及个人回帖点击在真实桌面和390px视口通过。浏览器复现长篇滚动早于加载结束导致目标未挂载，改为结束loading并nextTick后再校验身份/目标滚动，测试改查真实渲染树；另修评论锚点直达时SSR登录回跳水合不一致，挂载后仍保留完整锚点。95客户端脚本1067条及类型/client/SSR构建通过，293契约/访问审计、338源码边界/路由及79生成JS检查保持通过。77条临时评论及计数已事务清理，内容/余额/账本/回执指纹恢复不变；本批仅客户端生产代码变化，无新增Java/迁移或重启要求。真实旧段落别名、多账号慢网导航、生产风控/CDN与其他业务闭环仍未全验，不宣称整个v3完成。未提交推送，详见[联调记录](api-v3-comment-location-live.json)。

第七十六批（2026-11-03）：按用户批准新增GET comments/{commentId}/location，当前评论/文章权限后按普通或段评oldest同筛选SQL计位置，固定20条；段评沿既有别名与active/orphaned分组，只投影ID/页/分组，不返私密正文或匿名身份。用户主页改共享commentLink，ArticleCommentLocation按目标/文章/凭据隔离读取，普通页加载后定位；长篇打开指定段评页、孤立段评只读恢复与位置变化提示，失败仅GET重查；另修恶意/损坏百分号片段触发URIError。真实服务/当前权限/SQL两库各18组、实际Latke/MySQL2组、共享评论43及访问审计7组通过；95客户端脚本1066条与类型/client/SSR构建通过，293注册文档和338源码/79生成JS局部检查通过。最新7处Java待IDEA重编确认（含上批原始CHAR修复），无需迁移；未调用旧服务冒充新接口验收，浏览器工具已可用但新接口/PC/移动真实链待重启。未提交推送，见comment-location-regression。

第七十五批（2026-11-02）：用户确认重编PID8735后，优选/声明/开关和普通/段评关闭新写、已有读取、原成功键回放共24组真实检查通过，字段及内容/余额/账本/回执指纹恢复不变。损坏CHAR开关x触发实际Latke getBoolean的500，已修v3当前快照显式列与原始别名、专用FOR UPDATE策略读取，不改变旧通用仓储；真实框架/临时MySQL新增2组先复现后通过，原当前内容两库各14及相关66组通过。客户端编辑关闭再开放丢未保存草稿已失败复现，保留编辑组件与已载入草稿、取消确认、丢弃过期原文回调；平铺段评目标ID补验通过。93脚本1050条、类型/client/SSR构建及局部审计通过。最新四Java须再次IDEA重编确认（ApiV3ArticlePolicy、ApiV3ContentSnapshots、ArticleRepository、ApiV3CommentMgmtService），无需迁移；修复后异常字符HTTP及真实PC/移动未验，浏览器被Work设置关闭。未提交推送，见article-policy-current-live/regression。

第七十四批（2026-11-02）：按用户批准补详情commentable/statement，异常旧值为null；v3新评论/回复/编辑在幂等回调内锁文章后按当前锁定开关拒绝，成功原键回放保持。普通/长篇隐藏新写入口、取消未发送确认，保留草稿及原命令恢复，长篇普通/段评抽屉均可恢复；声明未知显式提示，置顶改用既有stick而非旧分钟字段。当前内容SQLite/MySQL各14组、共享评论与删除/采纳/投票等隔离回归通过，93客户端脚本1046条及类型/client/SSR构建通过；292契约、336源码局部边界、78生成JS检查通过。累计四处Java待IDEA重编确认（含上批优选DTO），无需迁移；实际HTTP拒绝/回放与PC/移动显示仍待重启后验证，未提交推送，见article-policy-regression。

第七十三批（2026-11-02）：当前服务基线确认优选筛选已收录但摘要/详情缺标志，临时标记已恢复。补ApiV3ContentDtoService只读perfect布尔值、客户端严格解析与SSR二次投影、紧凑列表徽标，普通/长篇详情沿现有徽标消费。后端两库各13组及相关23组通过，前端新增4组、93脚本1040条与类型/client/SSR构建通过；缺字段/适配丢失负向回归已复现，292契约和局部边界/78产物检查通过。最新仅此Java待IDEA重编确认，无迁移，真实新徽标及移动正向待验；未提交推送，见perfect-flag-baseline/regression。

第七十二批（2026-11-02）：用户重编后PID8853的问答新字段/最低悬赏/普通与匿名拒绝/当前canAccept/原键回放等22项真实通过，临时文章/评论整行及余额/回执恢复不变，第69–71五处Java无待重启。继续补齐文章列表/普通详情与作者卡/评论/长篇作者及段评回复的共享VipNickname接入，匿名不挂身份组件、用户名路由保持。实际昵称SFC宿主验证新增4组、93脚本1036条及构建通过；E专用普通/长篇及两评论真实浏览器彩虹/粗体/下划线、普通用户与匿名回退、问答已采纳、390px无溢出/日志0通过，视口恢复。正常开发发布/评论费用及阅读记录保留；没有新Java/FTL、未提交推送，见qna-current-live与content-membership-style-regression。

第七十一批（2026-11-02）：用户批准问答详情公开qna后，当前文章同次SQL以EXISTS投影offerPoint/accepted，隐藏/匿名/失效回答保留已采纳存在性但不返身份，非问答null。客户端严格解析并显示悬赏/已采纳，旧后端缺字段显示未知；采纳确认经组件事件重读文章，失败仅重读恢复。新增采纳断流/轮换/跨账号及状态投影/父子联动8组，前端93脚本1032条与类型/client/SSR构建通过；后端当前内容SQLite/MySQL各12组，临时库清理，292契约及局部边界/78产物检查通过。真实浏览器仅验证旧后端未知回退及回答已采纳，无新规则部署结论；当前五处Java待IDEA重编确认，无迁移、未提交推送。见qna-display-regression。

第七十批（2026-11-02）：按已批准的v3问答/最低20积分资格，GET采纳状态的canAccept同步要求offer>=20，防止低悬赏仍显示可采纳。署名/匿名0、19均false，20为true且匿名offerPoint仍0；SQLite与临时MySQL各5组通过，测试容器已清理。当前待IDEA重编共三处Java（此前两个采纳写入类及本次ApiV3CommentRewardQueryService），没有实测新规则、无需迁移；公开qna详情字段提案待用户选择，未提前实现。文档与回归已同步，未提交推送，见qna-state-minimum-regression。

第六十九批（2026-11-02）：真实问答发布/两回答/采纳/同键与新键重复/第二回答409及实际type33通知共21项通过，一次20积分悬赏及双侧唯一索引，临时字段恢复。源码另发现v3 POST未限制问答资格，用户批准后在锁定文章回调增加type=5且悬赏>=20检查，保留旧入口与成功原键回放。移除guard对照失败，实际核心/幂等及读取共74组隔离回归通过，292契约一致、API/更新日志已同步。最新两处Java须IDEA重编确认，运行21项先于guard不能替代新代码验收。优选/系统文章公告两个真实事件及问答展示元数据缺口继续。未提交推送，见qna-accept-live与qna-eligibility-regression。

第六十八批（2026-11-02）：实际文章购买通知及新建评论感谢通知共22项HTTP验证通过：当前标题及纯文本转义、列表/详情一致、目标失效占位、他人404/游客401、评论失效和讨论阅读资格收回后清旧内容、恢复可读。评论感谢正常15积分仅扣收一次、同键不再收费、一笔转账及双侧唯一索引；临时字段恢复，读取矩阵余额/通知行与已读均不变。正常开发感谢记录保留，无新Java/FTL或构建要求、未提交推送。内容描述六类真实事件现已覆盖感谢文章/购买文章/感谢评论，优选/采纳/系统文章公告三类仍待验，浏览器及生产风控矩阵另列。见notification-extra-live。

第六十七批（2026-11-02）：专用D首次购买17积分文章，生产http/API经本地TCP代理在真实上游200且交易提交后仅转发5字节并断流；客户端明确offline且无自动重试，显式原键重试成功。首次失败后及重试后分别核对仅一次扣/收、一份购买/转账/通知/回执、双账户唯一索引，重试六类业务指纹不变；买前/游客隐藏、买后解锁。保留正常开发测试账，无SQL退款。没有业务源码改动或重启要求，未提交推送；这是实际Node传输故障，不冒充浏览器/跨实例验收。见api-v3-first-charge-ack-live.json。

第六十六批（2026-11-02）：职业/勋章/会员公开读取源码复核后，会员样式17项真实HTTP证明当前配置/到期/状态/四类停用/白名单/错误规则，字段及五余额恢复。发现客户端TTL不使已挂载computed失效，已补共享计时删除及可见批量重读/别名过期/HMR清理；资料页破图补一次默认头像回退。最终93脚本1024条、构建和局部审计通过；真实页面不刷新完成样式消失/恢复、原rainbow及默认头像加载、390px通过，完整会员行恢复。无Java重启、未提交推送。见membership-style-current-live及style-expiry两报告。

第六十五批（2026-11-02）：真实TCP复现旧请求换号后误发离线通知及旧提示遗留；http按调用时身份版本+令牌存在状态隔离（含认证队列），AppShell在身份变化清旧提示、普通refresh保持。六组专项、全92脚本1018条及构建通过。独立生产构建+真实Rhythm浏览器复验当前断流→提示→重读保持活跃→退出/换号→释放旧连接，活连接核验true，新页无旧提示/正文；原A/视口/5173恢复、临时端口关闭、无新收费。无Java重启、未提交推送，首次收费ACK/跨实例/真机另验。见offline-identity-regression/live。

第六十四批（2026-11-02）：真实TCP复现共享http正文半途断开抛原始terminated，已统一JSON/文本/SVG/ZIP读取为明确offline、取消保持aborted，不自动重发写入。新增5组实际socket+Pinia/API/transport回归，原键经断流与普通refresh保留并显式恢复；91脚本1012条、构建与边界/78产物检查通过。真实Rhythm旧购买原键经代理成功回放后截正文，再同键成功，六类业务指纹不变、无新收费；浏览器正常读取通过，首次收费/真实浏览器丢包/跨实例另验。无Java重启，未提交推送。见response-body-regression/live。

第六十三批（2026-11-02）：专用C对17积分测试文章以两键各重复一次、四并发购买成功，只扣/收一次，一份购买/转账/通知、两份安全回执及双账户唯一索引；同键重放不扣费，买前/游客无正文、买后正文等于作者投影。浏览器买方读取/刷新/退出及390px通过。截图发现导航头像破图，AppShell补失败占位/换URL与账号重试/拒绝旧URL错误，90脚本1007条、完整构建和局部边界/产物检查通过。保留正常17积分测试账，不SQL撤回；无新Java重启、未提交推送。见purchase-concurrent-live与shell-avatar-regression。

第六十二批（2026-11-02）：现有专用文章的有评论/有付费阅读删除拒绝16项真实通过，含同键重试、他人/游客与四并发失败；正文/互动/统计/余额/回执指纹均未变化。永久置顶11项真实通过，五种recent排序优先、详情永久标志及付费覆盖409/同键/他人/游客，原置顶恢复且余额/账本/回执不变。无新收费、业务源码或重编要求，未提交推送；余额不足与成功扣费并发、管理员界面及多实例仍另验。见两份article-protected-removal/permanent-stick-live报告。

第六十一批（2026-11-02）：客户端实际丢弃昵称且匿名普通文章生成无效主页链接，已补文章/评论昵称与用户名分列、SSR二次投影、普通/长篇/列表/段落显示及匿名链接保护；背景头像统一CSS字符串转义，保留签名URL。新回归先失败后通过，最终89脚本1003条PASS、类型/client/SSR构建及336源码/78生成JS局部检查通过。真实普通文章/列表/评论/长篇及390px通过，专用资料/匿名状态和余额恢复，无新Java或重启要求、未提交推送。见api-v3-client-author-regression.json。

第六十批（2026-11-02）：用户确认重编后，Java PID96544的作者资料及头像投影17项真实HTTP通过，覆盖五种recent排序、长篇列表、详情/评论/公开资料/本人头像、匿名身份隐藏、无效地址安全回退及旧HTTP完整query只读兼容。原昵称/头像HEX及匿名状态恢复，两账号余额不变；第59批待重启已闭环，无本批新Java/FTL或迁移。外部图片签名有效性、全部列表组合/多实例及真机加载另验，见api-v3-public-author-live.json。

第五十九批（2026-11-02）：真实基线复现列表旧作者及三处签名头像query丢失，已修同次作者JOIN/当前fallback和v3安全原URL投影；SQLite/MySQL22+相关45共67条PASS、两负向对照能复现失败、292契约保持。最新五处Java待IDEA重编确认，无迁移/前端改动，外部图片不据此通过。见public-author-regression及基线。

第五十八批（2026-11-02）：用户重编PID93962后资料隐私41项真实通过，原字段/余额恢复，第57批无待重启；首页规则获批并实际接入HomePreferenceRedirect，47条相关组件回归/双构建/静态检查及浏览器自动目标、query/hash保留、未支持提示/改后重试/清空与390px通过。原functions/余额恢复，未提交推送；其它偏好消费及外部矩阵另列。见profile-current-live和home-redirect-regression。

第五十七批（2026-11-02）：复现公开资料/列表用户缓存泄露隐私：四项收回仍200、停用资料仍200。已按当前记录授权改Discovery目标/清风作者为当前SQL，九类隐私及本人/精确admin例外、改名/状态/脱敏回归SQLite/MySQL20+相关28共48条PASS，292契约保持。最新该Java待IDEA重编，无迁移；首页跳转规则另待。见profile-current-regression与基线报告。

第五十六批（2026-11-02）：展示模式/双端皮肤规范、设备不匹配与组合失败原子性、幂等及完整恢复19项真实通过，余额不变。只证存取，不等于皮肤实际渲染/动图模式或新站消费；首页自动跳转规则仍待用户选择。无业务源码/重启/提交，见display-normal-live。

第五十五批（2026-11-02）：locale语言/时区与functions首页地址34项真实存取/规范化/幂等/边界通过，原DTO及余额恢复。追查发现首页地址仅存取、未找到新站/经典执行跳转位置，已就新站触发规则向用户提问，未擅自实现；不能把设置保存成功当首页行为已生效。无业务源码/重启/提交，详见locale-redirect-live。

第五十四批（2026-11-02）：头像合法HTTPS/完整query/255长度/幂等与失败保护16项、functions正常分页偏好/边界/部分字段及恢复16项真实通过；原头像URL/type、全部functions对象恢复，余额不变。旧HTTP头像只读兼容未放宽，清理用受限事务与现有缓存刷新。无业务源码/重启/提交，实际图片和设置UI另验。见两份normal-live报告。

第五十三批（2026-11-02）：292注册入口显式鉴权清单核对，7组工具回归通过；实际缺失/无效凭据498、空scope274及六双scope分离12共784次全部预期拒绝，原测试scope/state恢复，关键业务表行数保持。匿名14/OAuth3/WS1分列不混算，未宣称所有正向业务或有效凭据越权矩阵通过；无生产源码/重启/提交。见route-access与三份access-live报告。

第五十二批（2026-11-02）：用户重编PID91346后，通知当前标题/发送者/占位/owner/游客/停用与恢复13项HTTP及实际浏览器刷新→新标题→失效占位→恢复、390px通过，夹具/余额/未读恢复。当前无待Java重启；六类隔离证据与真实感谢事件证据分别保留，其他矩阵继续。见notification-current-live报告。

第五十一批（2026-11-02）：真实浏览器文章3版切换、私密评论历史权限错误、实际503清旧历史/恢复重试及390px通过，原历史指纹恢复、视口reset；8项观察证据见revision-browser-live，不冒充自动化全回归。无业务源码改动，通知第49批重编仍待。

第五十批（2026-11-02）：已部署且未改的历史读取12项真实通过，含当前失效/讨论范围与单条/累计/数量预算503；夹具全清、原历史指纹及文章HEX恢复。无生产源码变更，不新增重启；第49批通知单文件重编确认仍待。见revision-current-budget-live报告。

第四十九批（2026-11-02）：用户重编PID88600后，当前文章15项、评论9项真实联调通过，夹具恢复且文章矩阵余额不变；另复现通知说明仍取旧标题。已补六类当前通知说明，完整查询回归SQLite/MySQL各8及相关31共47条PASS、292契约一致；最新仅通知Java仍需再重编确认，无迁移。见联调及content-current-runtime-regression报告。

第四十八批（2026-11-02）：真实预热基线复现6个缓存权限错误；用户批准后，文章/作者/读者与评论/段落/通知/表态共享当前SQL快照，字段/旧规则保持。SQLite及临时MySQL各7组、相关128条共142条PASS，292契约一致；六处Java需IDEA重编重启后真实复验，无迁移。公开文档已更新，当前运行PID80758仍旧服务，详见联调及content-current-regression报告。

第四十六/四十七批（2026-11-02）：真实文章删除九种拒绝均不扣费/写删除回执，互动及元数据核验完成，保留1篇已感谢专用文章。补修客户端票据403/404时旧正文残留及404重连、开发IPv6 WS代理hostname；全部87专项984条PASS、构建/静态检查及实际浏览器失效清正文→恢复重读通过。后端Java无新改动，服务端缓存权限与其他运行矩阵仍需复核，见联调及两份报告。

第四十五批（2026-11-02）：用户重编Java PID80758后认证HTTP44次及7个Cookie路径实测通过，正常登录/坏正文无副作用/空体刷新/退出/独立会话均验；浏览器登录→整页恢复→退出→整页游客及390px通过。第42/43批已部署，无本批Java新改动；8081双栈被不同项目监听，5173临时目标明确[::1]。详见联调与auth-body-live报告，完整v3其余矩阵仍待验。

第四十四批（2026-11-02）：编辑器补反应式页面元数据，标题随表单创建/编辑/长篇模式、不带私有草稿输入和查询，noindex；52相关回归、构建、77JS版本/map检查通过，浏览器普通/长篇与390px通过，无业务写入。后端第43批Cookie修复仍等IDEA重编确认。

第四十三批（2026-11-02）：第42批真实认证联调发现Latke自动回写fp_v3_refresh为缺保护属性的根路径副本，未记全通过。用户批准全局前置仅移除响应预填，正常显式签发/退出保持；实际框架回归复现并验证修复。新增BeforeRequestHandler/ApiV3RefreshCookies需再次IDEA重编重启，无迁移。详见联调第43批。

第四十二批（2026-11-02）：用户批准登录/刷新/退出原始JSON统一严格校验；认证最多4096字节、登录三字符串、刷新退出空体/空对象，格式校验先于凭据副作用。11后端runner87条PASS、3客户端runner32条PASS，292契约一致；需IDEA重编重启后真实验收，无迁移。API/UPDATE已同步契约，详见联调及auth-body-regression报告。

第四十一批（2026-10-31）：用户确认IDEA重编后，共享JSON修复在8081的19次请求全过，拒绝无草稿/扣费/回执副作用；正常原文、同键重放、未登录/跨owner拒绝、真正空体删除与清理通过。无本批Java新改动，不需再次重启。详见联调第四十一批及shared-json-live报告。

第四十批（2026-10-31）：用户批准共享原始JSON严格校验，ApiV3WriteInputs已修；59个隔离runner最终通过（941条PASS，含8个临时MySQL专项），原失败先复现、夹具/依赖同步。当前Java需IDEA重编重启后实测，无新迁移。导出MFA旧回执补验无新增费用、临时MFA恢复；客户端全86脚本978条PASS。详见联调第四十批与共享JSON回归报告；早期“兼容待答/未改”仅历史状态。


快照：2026-10-30；以本文件生成时工作区源码为准。后续新增入口须同步重扫，本文件不替代历史批次记录。

第二十批浏览器草稿联调修复快速输入丢失：PostView与共享ChatComposer冻结命令前flush实时缓冲，保留未修改原文；草稿回读/标题单改/载入取消/390px/退出及评论快速发送真实通过。32+10+23+21组回归、build与边界/产物审计通过；具体文件和人工步骤见联调记录第二十批，私信/聊天室浏览器快速发送仍待验。未改接口计数，未提交/推送。

第二十一批（2026-10-30）已完成私信/聊天室实际快发、刷新和引用发送；新增布局切换/话题与涂鸦追加前同步缓冲，97组回归和build/产物审计通过，390px及退出清理已验。API数量仍290，抽屉/真实触屏/断网等矩阵未扩验；完整文件和人工步骤见联调记录第二十一批。

第二十二批（2026-10-30）背包四卡/积分改名真实HTTP、SQL及浏览器确认完成：原键不重复收费或覆盖后来状态、同键/不同键并发卡片守恒、跨月补签接回、失败保留、实际1000主账/双方索引和连字符展示通过。390px/取消/刷新/退出已验，真实定时签到/奖励、第三方scope与浏览器最终改名提交未扩验；文件和人工链见联调记录第二十二批。无业务代码或接口数量变化，未提交/推送。

第二十三批（2026-10-30）修复开发库既有columnOrder默认值遗漏，长篇草稿发布/专栏归属与自动章号/可见性/管理真实通过；新增已确认的独立章节导航GET和客户端分页导航，当前291条契约齐全、Java13/客户端新7及回归/build通过。新Java需用户IDEA重编重启后验HTTP及新UI；重关联原键429保留，未假称恢复成功。详见联调记录第二十三批。

第二十四批（2026-10-30）用户确认重启PID17956后，章节导航GET的三读者/隐藏/匿名/讨论/当前作者状态和实际浏览器前后章/目录/刷新/390px通过；原重关联键已成功。当前全部82前端脚本939组通过，大目录第二页故障恢复用独立生产构建夹具验证。新发现旧Markdown API阅读记录/后续结算没有v3写入，范围问题待答，已整理[缺口](api-v3-long-read-gap.md)；不是所有经典页面都调用它。291路保持，详见联调记录第二十四批。

第二十五批（2026-10-30）段评公开/私密/匿名/锚点继承、真实编辑孤立与恢复通过；修复客户端真实总数、错误提示、零分页与空末页恢复和摘要计数残留。83前端专项944组/build/产物审计通过，真实游客私密投影及390px稳定布局已验。阅读登记三项规则已确认，待实现。详见联调记录第二十五批。

第二十六批（2026-10-30）阅读登记规则及经典计分入口兼容获批，新增POST /api/v3/articles/{articleId}/reads与正文后客户端登记；新旧共用当前资格/6小时窗口/事务，原后台公式保留。独立MySQL10组、客户端新7组/当前84脚本951组、build和边界/产物/292契约审计通过。独立浏览器失败重试/换章/390px通过；新迁移尚未应用，真实8081待停服迁移及用户IDEA重编重启。详见[实施记录](api-v3-long-read-gap.md)，不宣称全部v3完成。

第二十七批（2026-10-30）原阅读结算真实MySQL回归复现负计数扣作者积分，已修复负数/溢出与严格提交、账本索引原子性；正常公式不变。登记10＋结算9组全通过，含逐写/commit/ack故障与两个独立JVM的实际InnoDB锁等待及一次结算。接口保持292，完整应用仍待停服迁移和用户IDEA重编确认，详情见联调记录第二十七批。

第二十八批（2026-10-30）按当前292注册契约核对334客户端源码：215处路径引用无已确定缺路由/方法冲突，47处动态构造/转发及2处非v3独立列出。新审计8组，实际15个TS模块97次路径/方法构造12组均匹配当前注册图，不发送网络或证明响应/权限；更正阅读登记等陈旧状态，明确部署/验收/环境与排除范围。没有业务源码/Java/新接口变化，详见当前完成度审计。

第二十九批（2026-10-30）真实会员页完成游客门禁→登录回跳→取消无扣款→坏券无扣款→测试月卡实扣400/一次激活与双索引→样式保存/非法色拒绝→刷新/390px/退出清理；专用F余额3600，保持测试VIP状态，非生产价格。用户停服后新阅读迁移已应用，重编PID40308的首次登记/去重/本人排除与身份边界通过；非法JSON实际被误当空体，已改原始字节校验并补真实parseForm/MySQL登记11＋结算9组通过。当前仅最新控制器待再次重编后继续，不能把后续新旧/并发链记为通过。见联调第二十九批。

## 判定口径

本轮运行状态：用户已于2026-10-29明确恢复联调；IDEA 的 Rhythm Local 实际监听 http://localhost:8081，website_v3 的5173代理显式指向它。首批修正独立开发库排序规则和客户端认证代理，76个无路径变量GET及认证/预览边界、浏览器登录→刷新恢复→聊天室实时连接→设置读取→退出已取得证据。8080/3306属于其他项目。修改继续仅留本地、不提交；范围见[实际联调记录](api-v3-live-integration-work.md)，未覆盖的业务仍待验收。

第二批已取得专用账号文章/评论创建、编辑、删除、幂等/收费与三身份隐私读取证据；评论删除仅扣一次100积分。新人报道缺角色保护12组隔离通过，重编后实际发布保留defaultRole且可继续编辑；三账号文章WebSocket在清除初始提示后收到私密回复变更的纯ID提示，无权HTTP重读仍为空。编辑事件旧监听器与当前事件形态不一致，缺角色分支未实际触发，不能扩大验收结论。详见同一联调记录；290接口数量保持。

第三批真实私信发送/未读/撤回/已读重放及通知归属/已读/删除/空实时提示通过；聊天室普通发布、0/32撤回与新消息created/deleted提示通过。联调修复聊天保存点关闭owner后覆盖404的问题（9组故障+既有红包/发布/事件回归），以及账本sourceScene过短导致红包503的问题；新增20261029列扩展迁移并同步repository.json。普通/专属/拼手气三类实际扣款、税、奖池及领取/幂等通过；其他环境须先执行新迁移，风险类型、并发和重启仍待验。

第四批实际猜拳胜负平、心跳五份含负/0、零余额拒绝不占份额、两账号争抢单份结算一次通过；停用账号测试暴露Bearer复用用户缓存，已改当前SQL认证及全客户端刷新锁当前状态，令牌与11组凭据回归通过。用户确认重编后实际停用/删除账号的Bearer与website刷新均401；停用发送者可结算返还且不恢复账号状态，缺失发送者409保留款项/份额，恢复专用账号后原键结算成功。新管理员家族重跑76个GET仍72/3/1，无5xx；结构长度只读核对109表1013字段，职业聚合预留项1024/512差异记录未改，不宣称全schema一致。

第六批真实OAuth Cookie同意/原生PKCE/拒绝/刷新/主动撤销/两种重放家族撤销、管理员改名保留授权/改权限回调全失效/停用通过；APP双端确认、一次兑换、独立令牌刷新撤销及真实两分钟过期通过；Evolve原样存档、空串/50万边界、CAS并发、旧回执不覆盖、显式迁移及经典旧JSON读写桥接通过。均为当前8081/独立开发库协议测试，无Java/FTL/SQL/产物改动；真实浏览器授权/扫码设备/游戏解码和更广矩阵仍未验，不据此声称全部290条运行验收完成。

第七批IAB真实用户站登录→本人文章原文→编辑取消不写/确认回详情→Vditor Markdown评论发表→回读/整页刷新唯一性与身份恢复→390px确认弹窗→退出/刷新清私密正文通过，本文章未观察到Hydration警告；未扩为全站SSR或更多写入。只更新记录与mysql诊断字符集约束；临时快照损坏的测试E资料已恢复。业务源码和290路由保持。

第八批实际转账普通/最低额/手续费账号双向、三方余额守恒、失败及重放无多扣通过。导出首次503未扣费，定位Latke借出默认RC与快照要求不匹配；已让快照专用根首次SQL前选RR，不改全局隔离。10组隔离及临时真实MySQL RC基线/故障/JVM同键争抢回归通过，用户确认重编后当前应用A/B导出ready，各只扣100一次；ZIP长度/hash/本人原文与匿名私密范围、他人404/无令牌401通过，过期/篡改/全状态矩阵仍待验。

第九批会员受控测试档位的开通/单次券/年费礼包/自然日期限、只读权益保护、配置旧回执不覆盖、公开样式白名单与精确VIP4月费转账免手续费通过；测试价格不代表生产规则。专用账号MFA准备替换→启用→TOTP登录→停用，以及密码修改→旧密码拒绝→恢复原密码通过，每次变更后的v3 Access/Refresh均401；账号已恢复、临时密钥清理。仅补运行证据，无源码/接口新增；其他未验收矩阵见联调记录。

第十批图片表情分组/成员/分享导入与历史云迁移全链路通过，含越权、坏项整批拒绝、旧键重放与来源删除后快照；音乐/跨年天气发送、详情/上下文与浏览器读取通过。修复用户站共享ChatCard日期拥挤及窄屏负温度折断，构建/AST边界审计通过、390px无横向溢出。未修改后端及290路由；其他未验矩阵继续保留。

第十一批文章作者置顶100及同键不再扣/有效置顶排序通过；17积分付费正文购买、双方守恒、重复新旧键不再付费及第三读者隐藏通过。本人新文章/有评论/已购买拒绝删除、满24小时夹具干净文章删除/reaction仅目标清理/重放404矩阵通过；时间门槛采用专用数据库夹具，未实际等待24小时。只补运行记录，无业务源码改动，290路由保持。

第十二批鱼游待审投稿/OAuth参数拒绝/URL占用/幂等与不可见通过，临时已审核夹具的编辑申请保持公开旧内容、pending OAuth分列与重复409通过，最后已恢复未审核；真实管理员审核未验。QQ数字/隐私、标签严格别名、MBTI枚举/清除/重放及原值恢复通过；实际写入429保留记录未绕过，头像仅非法输入和历史读取。无新业务源码或路由，详见第十二批记录。

第十三批完整应用企业认证PNG422暴露Latke parseForm有损改写二进制；改create复用有界原始Netty读取。先以真实parseForm夹具复现失败，再修复，12组隔离及临时MySQL/五类JVM争抢均通过；API/日志同步。Java改动随后经用户确认重编，第十四批已取得真实上传/阅后销毁/审核证据。290路由不变。

第十四批用户重启确认后的8081进程55341：原始PNG/JPEG/5MiB/MIME、申请归属和指定审核员权限通过；并发材料读取仅一200/一410、原字节相等，SQL确认材料消失，创建回放不复活图片；未读409、读后approved/rejected/重放及释放待审通过。临时本地指定审核员绑定/账号已清理，旧凭据401；没有生产审核员或浏览器下载UI验收。只补记录，无新Java改动。

第十五批勋章25项真实HTTP及SQL核对：本人隐藏/公开过滤、显示与排序/回执、图片隐藏/未持有/过期/匿名拒绝通过；配置本地渲染器未运行，使用仅loopback受控SVG响应验证实际清洗，不能认作真实生成器可用。过期记录参与排序并保留，其他用户不变；夹具/临时监听均已清理。只补记录，无业务源码改动。

第十六批职业发布目录/草稿排除、按读者隐私筛选排行再分页编号、隐藏经验保留等级名次、公开资料排名统计省略、主职业/跳过引导重放及无进度不伪造通过；本人详情/记录/成长提示空结果正确。开发库15张旧职业表collation对齐general_ci后联表503消失，未改生产SQL/业务源码；夹具已清理，真实经验增长/升级奖励及浏览器仍待验。

第十七批邀请码模式/100积分/失败不造码/同键与恢复模式回放/状态白名单通过，模式与测试码已恢复清理；文章/评论/用户举报权限及pending复用/已处理新键/旧键保持通过。真实聊天室举报404定位存储整数作者与服务String限制不匹配，已修复并新增18位及错误类型回归，10组报告通过；随后用户确认重编，第十九批已完成真实重试。290路由保持。

第十八批通知分类/全部已读及按分类删除真实转账通知链路通过，旧键不处理后到通知、分类/owner隔离正确；全部私信已读只清本人并保留历史、旧键不清新未读通过，四条测试消息已由发送者撤回。无新源码改动，当时不替代待用户重编确认的聊天室举报验收；后续第十九批已通过。

第十九批历史作者/当前权限/私密边界、匿名历史无身份及本人匿名评论白名单通过；草稿创建/本人源文/完整替换默认/旧回执不覆盖/越权/删除与无扣费通过。用户明确重编后PID57526，正常整数作者消息举报/重复/自举报/理由实测通过，SQL仅一行且已清理；无新增Java改动。匿名文章只有合法空列表证据，浏览器草稿和完整状态矩阵仍待验。

- **已注册源码**：`Router.requestMapping` 调用 `ApiV3Processor.register()`，后者能到达具体 Processor 的 Dispatcher 注册；不把孤立的处理方法、服务类或 API.md 条目计为接口。
- **隔离证据**：历史批次明确记载执行过的 runner；常含 SQLite、仓储/锁/身份/外部服务适配器，不等于真实 MySQL、完整 IoC、浏览器或端到端验证。本轮定向重跑结果见下方证据表，其余历史 runner 未全量重跑。
- **运行验收**：已有完整应用启动、独立开发库迁移、首批76个GET与认证/预览边界、浏览器认证恢复及聊天室连接的证据；这不证明各业务 Bean 的全部调用路径、HTTP/Socket、PC/mobile 与第三方客户端全链路正确。下表各业务接口仍需逐项运行验收，不能因启动或读取成功就整体标为通过。
- 当前范围排除管理后台、金手指、内部回调、游戏计分和支付管理；公开支付跳转仍在范围。用户侧职业、勋章、会员能力不能仅因同模块存在后台接口就整体排除。OAuth管理员登记/管理为用户明确纳入的例外，其余后台仍排除。

## 当前数量

可重复核对：`node tools/api-v3-contract-audit.mjs --json /tmp/api-v3-contract-audit.json`。只核源码注册链和公开契约，不能替代运行验收。

已注册 **292 条（291 HTTP + 1 WebSocket）**；HTTP按 METHOD+完整path计数；GET/PUT/DELETE共用路径仍分别计数。方法分布：DELETE 24, GET 148, POST 73, PUT 46, WEBSOCKET 1。

连接链路：`processor/Router.java#requestMapping` → `processor/ApiV3Processor.java#register` → 各模块 `register`。以下路径均包含完整 `/api/v3` 前缀，代码位置以生成时行号为准。

注意：当前生成的“全仓库837路由、398浏览器引用”是不同口径，包含旧HTTP/WS等，不能当作v3接口数。静态注册成功也不证明完整IoC启动成功。

## 当前剩余工作

1. **最新部署与已闭环缺陷**：第73批新增perfect投影的ApiV3ContentDtoService待IDEA重编确认；第69–71批五处Java已由用户重编并在PID8853实测22项通过，无当前待重启；最新已确认Java PID8853，问答字段与采纳资格22项实测通过；此前已确认并实测Java PID96544，包含当前作者与完整头像URL修复；正文/评论当前记录及通知旧标题遗漏均已部署实测。第48/49批不再待重启；认证严格JSON/Cookie、OAuth回调、阅读登记和结算、实时失效清正文与历史预算/UI已有逐批证据，不重复列为未开发或未部署。
2. **当前未发现确定缺路由**：292条总register/公开契约一致只是接线证据。未完成的是各已实现能力尚未覆盖的运行/浏览器组合，不能把旧模块标题下“运行未验收”当最新全模块结论，也不能以局部通过抹掉未验范围。
3. **下一步验收重点**：已完成所有Bearer入口缺失/无效凭据、空scope及六个双scope分离拒绝；继续按现有模块证据补剩余本人设置正常写入与恢复、收费/幂等/网络失败、通知优选/系统文章公告两类真实事件、经典红包双端与跨账号浏览器。已有测试覆盖的同一分支不为增加计数重复运行。外部环境不通时保留明确未验项，不能造成功结果。
4. **外部及拓扑依赖**：短信/邮件/图床/内容审核、10831节点、物理移动设备与多实例、生产匿名首访/周期/黑名单/验证码解封及全站CDN/目录/上传下载反漏扫，需要相应环境证据；开发关闭风控不等于这些通过。职业读取/阅读事件已验，但具体职业自动化规则增长尚缺开发库规则场景。
5. **范围保持用户原约定**：仅v3支持APP扫码，不改APP源码；非OAuth客户端管理的后台不整体迁移；捐助只提供既有页面链接；游戏仅登记Evolve完整存档，dice不支持。新增不明确规则仍须先确认，这些边界不擅自扩成新接口。

最新模块证据和具体局限见[实际联调记录](api-v3-live-integration-work.md)与[当前完成度](api-v3-current-completion-audit.md)。下面保留历史生成的注册清单；历史“运行未验收”仅描述生成当时，必须与最新批次对照。

## 已注册接口清单

### 长篇阅读登记（1）

`POST /api/v3/articles/{articleId}/reads`：ApiV3ArticleReadProcessor→LongArticleReadCaptureService，content.read/activity.write，当前有效登录读者/署名可列出有效长篇、排除本人，服务端6小时去重/原窗口结算。旧Markdown与经典计分共用，需新20261030迁移；源码/独立MySQL/客户端通过，迁移及PID44439真实登记/旧入口/并发/自然结算已验，剩余矩阵见实施记录。

### 长篇章节导航（1）

`GET /api/v3/articles/{articleId}/column`：ApiV3Processor→ApiV3PublicColumnQueryService#articleContext，optionalContent与匿名验证；当前可读目标、可列出署名关联，摘要/原章号/前后章，目录另分页。源码/JavaSQL及前端专项通过；用户确认重编后，真实接口/浏览器验收见联调记录第二十四批。

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

代码：`src/main/java/org/b3log/symphony/processor/ApiV3GameSaveProcessor.java`；总register已接。仅Evolve单槽，版本/幂等/显式迁移与登记游戏旧桥接；隔离Java/HTTP与实际MySQL通过，2026-10-29当前完整应用已实测原样/边界/CAS并发/显式迁移/经典Cookie JSON桥接。真实游戏导入和设备仍未验，见实际联调第六批。

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

代码：`src/main/java/org/b3log/symphony/processor/ApiV3OAuthProcessor.java`。已注册；2026-10-29当前完整应用已实测Cookie同意/拒绝、原生PKCE、刷新/撤销及重放家族失效，完整矩阵/浏览器仍未验，见实际联调第六批。

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

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ClientManagementProcessor.java`。已注册；2026-10-29当前完整应用已实测管理员登记/重放及普通用户拒绝；列表已有首批读取，详情完整矩阵未验，完整矩阵/浏览器仍未验，见实际联调第六批。

| 方法 | 路径 | 行号 | 处理器与门禁 |
| --- | --- | --- | --- |
| GET | `/api/v3/admin/oauth/clients` | 28 | `p::list,auth::settingsWrite,p::administrator` |
| GET | `/api/v3/admin/oauth/clients/{clientId}` | 29 | `p::detail,auth::settingsWrite,p::administrator` |
| POST | `/api/v3/admin/oauth/clients` | 30 | `p::create,auth::settingsWrite,p::administrator,writes::handle` |

### OAuth客户端停用（1）

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ClientDisableProcessor.java`。已注册；2026-10-29当前完整应用已实测停用及已有令牌/刷新拒绝，完整矩阵/浏览器仍未验，见实际联调第六批。

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

代码：`src/main/java/org/b3log/symphony/processor/ApiV3ClientUpdateProcessor.java`。已注册；2026-10-29当前完整应用已实测仅名称修改保留授权、权限回调改变撤销令牌/码/待确认请求，完整矩阵/浏览器仍未验，见实际联调第六批。

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

代码：`src/main/java/org/b3log/symphony/processor/ApiV3AppLoginProcessor.java`。已总接线，2026-10-29当前完整应用已实测双端确认/拒绝/一次消费/APP刷新撤销/真实两分钟过期；真实设备与全部异常矩阵仍未验。用户明确不改APP代码。

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
| 2026-10-29 本地环境恢复 | Java25完整 Maven 编译与 IDEA 实际构建/启动通过；独立 MySQL3319 新库按当前迁移修复；真实首页与 captcha-options 200、无凭据 me 401；红包专项20组通过，见local-idea-environment | 仅启动及三个探针，不是全业务验收；10831节点、图床、短信、邮件、真实授权/积分与移动设备仍未验 |
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

## 已批准能力的实施与验收依赖

本表按能力归档源码及依赖，不把已注册能力重新列为待开发。当前阻塞和优先级见上方“当前剩余工作”；旧URL仅用于追溯，不能照搬未确认的业务规则。

| 能力 | 当前可证明状态与旧来源 | 下一步与依赖 |
| --- | --- | --- |
| OAuth客户端登记/维护 | `ApiV3ClientRegistrationService#createApprovedInCurrentTransaction`已有已批准应用登记核心；列表/详情/登记HTTP已接入 | 用户已确认仅管理员登记和维护及20回调/2048字符；第一方管理员授权、幂等列表/详情/登记已接入，逻辑停用及完整Token撤销已接入；回调/权限PUT已接入，实际集合变更令全部旧Token/码/待确认失效；仅改名称保留授权，禁止自助登记 |
| OAuth token/revoke风控 | 协议入口已注册，用户已批准有效协议证明作为端点身份 | token/revoke接只读绑定凭据准入，缺失/无效委派原匿名验证；全局黑名单不变，不加viewSkips或普通身份。根定向6组，真实第三方/验证码/黑名单联调仍待做 |
| 鱼游投稿/编辑申请 | 目录/详情/评论、投票及投稿/编辑已接线；旧来源为FishGameProcessor | 投稿POST/编辑PUT已接线；按说明校验授权URL及同站回调后待审，旧审核合并pending OAuth字段；先执行20261023鱼游迁移 |
| 聊天红包发送/详情/领取 | 已新增GET `/api/v3/chat/red-packet-types`；`ApiV3ChatMessageDto`只给红包摘要，旧ChatroomProcessor红包链仍存在 | 五类创建/详情/领取已接源码：新增心跳负积分与猜拳出拳/返还结算；均需显式风险确认。dice不支持。真实MySQL独立进程竞争已有定向证据；完整IoC/页面仍未验；两项规则已确认并在第110批实现经典显式证明/只读及停用账号结算；真实完整链路待验，见risk-legacy-bridge-work |
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
| 游戏云存档 | Evolve四新入口已接，旧cloud登记游戏共用核心 | 已确认单槽/原样字符串/版本/幂等/显式迁移；七组服务HTTP、完整SQL重复迁移与七独立JVM MySQL竞争通过。2026-10-29当前应用实际原样/CAS并发/50万边界/显式迁移/经典JSON桥接通过；其他游戏仅确认来源后扩目录，PC/mobile游戏导入/CDN及全局风控仍待验，见game-save-work及实际联调第六批。 |
| website_v3迁移 | 用户主要页面与写入已分批接专用v3模块，实际逐批证据见第35–107轮 | 首页/目录/文章当前读取、账号/设置/内容/社交/聊天室链已落源码与隔离验证；旧page-auth及47个无调用包装已批准清理。仍需全量功能覆盖复核、真实部署/权限/风控/外部服务与PC/移动验收，不能按源码注册判定完成 |
| 官方APP短期扫码 | 用户已确认2分钟、APP确认后网页批准、一次消费；当前七POST已接线 | SQL/Repository/metadata、当前网页授权与账号epoch绑定、原子令牌对、Native刷新/撤销复用OAuth及管理员变更联动已实现。Java纯核心4/事务HTTP7、管理12+10/安全6回归及真实MySQL原语3组通过；追加实际生产Java/MySQL七场景、五写入故障、七独立JVM竞争且逐项观察锁等待。2026-10-29当前应用双端确认/消费/刷新撤销/真实过期已通过，真机与全部异常矩阵未验。用户最新范围仅v3，APP代码未改。 |
| 公开专栏与长篇导航 | 四GET及新增文章column GET均已接入，当前可见署名长篇/有效作者投影 | 第23–24批当前服务验证专栏发布/管理/可见过滤及独立上下文，浏览器前后章/目录/刷新/390px通过；完整并发与生产风控仍未验，旧经典保持 |
| 长篇阅读记录/结算 | 新POST /api/v3/articles/{articleId}/reads及正文后客户端登记已接线；旧Markdown/经典计分共用当前资格与窗口 | 用户规则均确认，20261030迁移/严格登记与结算、负数/溢出保护及原子双索引已实现；登记11＋结算9组、跨JVM一次结算通过；迁移已应用，PID44439原始体/新旧入口/并发去重与自然旧窗10积分结算通过，客户端积分回读已验，职业具体规则等矩阵继续，见api-v3-long-read-gap.md |
| 最近注册、随机发现、公开日志 | 第100轮已实现三项源码与客户端，真实运行待验收 | 最近注册仅有效账号ID倒序最多20，公开四字段；随机字段起点回绕/去重/打乱，1..20默认8、当前可读可列出及可选排除ID，匿名隐藏身份；日志仅登录activity.read、ID/时间/固定白名单类别，无data/key2，后续按类别扩正文 |
| 全链路验收 | 已恢复首批真实联调；76个GET读取/拒绝、认证与预览边界、浏览器登录恢复/聊天室连接/本人设置读取/退出已有证据 | 第二至六批已补文章/评论/私信/通知/聊天室/五类红包/OAuth/扫码/云存档部分真实链路；外部服务、其他收费/设置/职业/表情、浏览器完整写入、移动端和跨实例等仍待逐项验收，不能按当前结果判全量通过 |

## 并行分工与合入顺序

下表为早期历史分工，不能作为当前缺口清单；当前状态以上方能力表及最新批次为准。

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


第102轮（2026-10-28）：ArticleListView所有文章分类/搜索/目录采用真实v3分页（20），专栏采用当前可见目录（12）和单选排序，不再猜下一页或由文章拼造作品数/作者/章号。独立错误清旧/原页重试/收缩回首页、凭据与目标迟到隔离，公开SSR按kind/extra/order绑定/白名单/一次消费，Home旧feed保持。新增16专项与SSR第9组、76脚本858组、认证12组、独立exit0双构建、75JS版本0/无map通过；IAB1280合成文章与专栏p2故障→末页/禁下一页、专栏hot重置p1和C++目录通过。290入口保持，无Java/FTL/SQL/Maven/APP/外部接口契约变更。真实权限/风控/390px及Home聚合缓存假空仍待，见[列表记录](api-v3-client-article-list-work.md)。


第103轮（2026-10-28）：Home五资源独立读取/错误/重试，最新40、热议12、专栏最近/热门各8、标签24；移除聚合缓存假空与文章拼造专栏，SSR区分null/[]，当前身份迟到隔离，布局卡片同级。复读机ready及同账号Token轮换草稿保留。77脚本878组、认证12组、exit0类型双构建、75JS版本0/无map；IAB1280合成局部503重试/真实目录/401刷新草稿通过。290入口保持，无Java/FTL/SQL/Maven/APP/接口契约变更；真实权限/风控/390px待验，见[首页记录](api-v3-client-home-reads-work.md)。


第104轮（2026-10-28）：领域/标签目录接现有GET真实50项分页，去SWR/apiKey，原页失败重试/末页禁用/收缩回首页/身份迟到隔离；SSR kind绑定/白名单/一次消费，数字ID链接避免二次解码，透明外层及SCSS滚动留白。11专项+SSR第11组、78脚本890组、认证12组、exit0双构建、76JS版本0/无map通过。IAB1280合成两页50/51→p2故障→重试末页及固定顶栏修复复验通过；真实权限/风控/390px待验。290入口保持，无Java/FTL/SQL/Maven/APP/外部契约变更，见[目录记录](api-v3-client-directories-work.md)。


第105轮（2026-10-28）：ArticleView移除通用正文/评论SWR，当前GET失败不回退旧正文；全部刷新来源401/403/404清正文/回复/图片/历史，确认操作后503保留明确重读提示。普通Token轮换重读且保留实际评论草稿/历史，分页在途提示/禁按钮与卸载迟到保护。10函数专项+完整评论组件新增2组、79脚本902组、认证12组、exit0双构建、76JS版本0/无map通过；IAB1280合成p2故障→原页重试末页→403清正文通过。290保持，无Java/FTL/SQL/Maven/APP/外部契约变化；真实权限/风控/长篇/付费/390px待验，见[文章读取记录](api-v3-client-article-reads-work.md)。


第106轮（2026-10-28）：v3Article详情ID/评论articleId/请求页与size20绑定及重复评论拒绝，参数先验避免半发请求，长篇坏响应不继续取段落正文。新增8组及完整组件0页返回第23组、79脚本911组、认证12组、exit0类型双构建、76JS版本0/无map通过；IAB1280合成错误归属p2→明确错误→原页重试正确末项及收缩0页返回第一页通过。290保持，无Java/FTL/SQL/Maven/APP/外部契约变更，真实权限/风控/长篇/390px待验，见[响应绑定记录](api-v3-client-article-binding-work.md)。


第107轮（2026-10-28）：用户批准网站旧page-auth桥接退役；删除Node转换/HTML抓取/Cookie签发、前端sessionStorage helper及Vite/nginx旧登录转发，Rhythm经典入口保持。fishpi删除47个无调用包装，仅六当前helper。实际生产/开发Node六组桥接404/零上游/无Cookie、现有v3与验证码POST正文保留；80脚本917组、认证12组、exit0双构建、77JS版本0/无map，IAB1280首页→v3登录表单通过。290保持，无Java/FTL/SQL/Maven/APP；需部署重启Node/同步nginx，真实登录/风控/390px待验，见[桥接退役记录](api-v3-retired-bridge-work.md)。


第108轮（2026-10-28）：删除孤立catalog/gaps假数据、移纯DomainItem类型、删除无用withKey；静态AST复核找出离线旧ping并改既有v3 captcha-options。330文件165 API字面量/47动态表达式/2 RhyPic校验单列、0发现，报告不等于运行覆盖。80脚本917组、认证13组、exit0双构建、77JS版本0/无map；IAB1280合成离线一次v3探测→原登录页通过。用户确认部署后本机8080仍拒绝，随后明确暂不做真实联调；继续源码/隔离，不等待地址。290保持，无Java/FTL/SQL/Maven/APP/外部契约变化，真实权限/风控/390px待验，见[边界记录](api-v3-client-boundary-work.md)。


第109轮（2026-10-28）：新增Router.requestMapping→registerProcessors→ApiV3/helper注册链审计，与API.md按METHOD/path核对；114方法/290路由/文档290，差集/重复0。9组正反例与node --check通过；全仓837/398口径保持。仅工具/文档，无业务Java/FTL/SQL/客户端/Maven/APP改动，未重复构建或全量客户端测试。真实联调按用户要求暂缓；风险红包经典衔接/停用发送者结算两规则待答，不据源码契约齐全宣称业务完成，见[收尾检查](api-v3-contract-audit-work.md)。


第110轮（2026-10-28）：用户确认风险红包经典两端显式领取、停用发送者存在即可结算/删除409。共享legacy证明/只读/预期账号、JSON媒体类型及PC两JS/生成产物接入，未知重试保留原拳/账号；旧历史保持。Java25核心20组、JS16组、卡片4组、资源build、4JS版本0/无map、290契约一致通过；IAB1280本地捕获两min脚本确认/取消/出拳/只读通过。无新SQL/FTL/Maven/APP；已提醒IDEA重编/重启，真实联调继续暂缓，见[风险红包衔接](api-v3-risk-legacy-bridge-work.md)。


第111轮（2026-10-28）：经典两channel实时领完同步durable状态，完成单向/清待恢复证明，旧事件与快照不回退，完成后只读。新核心参数遇非新核心目标明确拒绝，旧历史正常请求保持。12状态+16领取UI+Java25核心20组、资源build、8JS版本0/无map通过；IAB1280两套实际min与模拟WS→只读请求通过。290保持，无新SQL/FTL/Maven/APP，已提醒IDEA重编/重启；真实联调/移动仍暂缓，见[实时状态收尾](api-v3-risk-status-work.md)。


第112轮（2026-10-28）：真实Util.alert/jquery/生成样式的390px检查发现PC风险弹窗502px越界；新增专用SCSS modifier并PC/mobile共享import，按钮换行、视口留白/旋转与旧弹层清理；仅新风险弹窗。16领取UI+12状态、资源build、4JS版本0/无map、CSS无sourceMappingURL通过；IAB实际DOM390 PC/mobile两脚本、844横屏及1280桌面几何/截图通过。无Java/FTL/SQL/Maven/APP，真实联调/实机仍暂缓，见[窄屏记录](api-v3-risk-dialog-layout-work.md)。

第三十五批真实补验：导出自然旧任务清理与同键expired不扣费、四种损坏503不发附件、四种非有效账号401及恢复原ZIP一致，浏览器下载事件/hash和到期禁用通过。仅文档更新，大包/全内容状态/MFA等仍未验收，见联调记录。

第三十六批：导出多块/本人特殊状态内容快照与浏览器实际下载已验（5块/206207字节）；中间/末块损坏拒附件及恢复原ZIP一致，原文/状态恢复，一次100主账/双索引。诊断mysql JSON转义问题已修本地脚本并维护raw/HEX约定。余MFA、边界预算/网络/跨实例等，见联调记录，不据此完成v3整体。

第三十七批进行中：共享ApiV3WriteInputs框架预处理缺陷已由真实parseForm测试复现，原单引号JSON拒绝断言失败；50个processor/86调用需复核。用户兼容选择待答，生产共享helper未改，测试暂失败作为证据，不应据上批全绿认定当前全部通过。详见联调记录。

第三十八批：独立修客户端通知批量已读普通token轮换恢复，真实正常已读/计数与移动布局通过，全86脚本974组及最终页专项4组通过。后端共享JSON缺陷的兼容决定仍未到达，ApiV3WriteInputs未改、相应真实parseForm回归仍失败；不据客户端全绿取消该待办。

第三十九批：客户端职业排行普通token刷新保留职业/提示/目标请求页（含p2尚未返回），当前GET重新授权且换身份清旧。41相关组与构建通过、真实空目录/390px通过；有数据浏览器未验。共享JSON兼容选择仍是待确认的明确缺陷，生产helper未改。
