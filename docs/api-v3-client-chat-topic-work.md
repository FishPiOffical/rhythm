# v3 客户端话题修改（第七十八轮）

## 范围与行为

复用已有 `PUT /api/v3/chat/topic` 与当前话题GET，无接口契约/Java/FTL/SQL/APP变更，后端仍284入口（283 HTTP + 1 WS）。API.md本轮不改。

经典/简约和侧栏共用ChatTopicEditor及v3ChatTopic。新操作先确认16积分费用，按有效Unicode/64个UTF-16单位校验，不截断；具体话题字符过滤仍由服务端完成。原text/key在响应未知时冻结并跨SPA保留，挂起时不接第二个新操作；已知初次4xx允许改正，未知操作恢复后的4xx不证明首次未提交，仍保原命令。平台confirmation_required须明确确认原命令，可取消尚未确认的请求。同账号token轮换保留命令，退出/账号/授权上下文变化清除，迟到回执不回填。

成功回执仅白名单text/cost/revision，不拿回执text当当前话题；随后GET当前值，失败仅重读GET，不再次PUT或扣费。原输入在确认恢复成功且仍匹配本人原提交时清空/关闭。timeline.readTopic支持无聊天室订阅的主动当前读，仍校验当前身份和请求代数，不另开WS。删除旧setDiscuss特殊语法发送方法。

## 实际文件

客户端根 `/Users/ling/Documents/fishpi/website_v3`：

| 文件 | 行为 |
| --- | --- |
| `src/api/v3-chat-topic.ts` | PUT/原键/文本验证、回执白名单 |
| `src/stores/v3ChatTopic.ts` | 原命令恢复、二次确认、身份隔离、成功后当前读取 |
| `src/components/chat/ChatTopicEditor.vue` | 共用输入/费用确认、原输入冻结与匹配清理 |
| `src/components/chat/ChatTopicRecovery.vue` | 原键恢复、平台确认、当前GET失败独立重读 |
| `src/views/ChatroomView.vue` | 两套布局替换旧表单/话题发送 |
| `src/chat/sidebar/modules/TopicModule.vue` | 侧栏共用编辑/恢复/当前值 |
| `src/stores/chat.ts` | 删除旧setDiscuss及特殊命令包装 |
| `src/stores/v3ChatTimeline.ts` | 无订阅的主动话题读取仍按身份/序号取消 |
| `tools/v3-chat-topic-test.mjs` | 实际API/Pinia/timeline/Vue专项13组 |
| `AGENTS.md`、`docs/V3_MIGRATION.md`、`docs/api-v3-client-chat-topic-work.md` | 架构/进度/本记录 |

后端仅文档：`AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`docs/api-v3-client-chat-topic-work.md`。

## 已验证

- 话题13、上下文13、历史16、撤回11、私信21、发送15、原文13、在线13、表态16、实时9、通知6、启动9，共155组通过，无异常测试输出。实际API/Pinia/编辑与恢复Vue组件执行，HTTP和确认决定用测试适配。
- 类型检查及客户端/SSR双构建通过；72个生成JS版本审计0命中，无map/sourceMappingURL/sourceURL，两仓库diff空白检查通过。
- IAB实际完整页+合成HTTP/实际WS：经典新输入→16积分确认→服务端保存后503→原text/key重放成功→当前GET503→仅GET恢复为“别人随后改成的当前话题”；390px简约独立新操作成功关闭原编辑器，侧栏共用当前话题/编辑器/回执，warn/error空。HTTP记录首次/恢复为同键同正文，另一次明确新提交使用新键。
- 仅合成数据，没有真实话题或积分写入。截图 `/tmp/rhythm-v3-chat-topic-mobile-proof.png`；临时服务和浏览器页验后关闭。

## 完整人工测试

1. 登录 `/cr`，经典/简约点话题旁编辑按钮，侧栏点“修改话题”；输入合法中文/emoji及64 UTF-16边界，确认收费说明。取消不得PUT；确认后请求须为Bearer PUT/Idempotency-Key，不能旧[setdiscuss]普通消息。
2. 成功显示16积分结果，原匹配输入清空/关闭，当前话题由GET取得。用另一开发会话随后改话题，旧回执不得覆盖当前GET值；主界面/侧栏/首页当前值共用。没有chat订阅的侧栏主动操作也可读当前值。
3. 提交后模拟断线/503，原输入冻结，原text/key保留。站内导航后恢复仍同键同正文，不能产生第二次扣费。平台confirmation_required显示明确原话题确认，拒绝不提交；取消待确认清除已知未提交命令。
4. 成功后模拟当前GET失败，回执仍成功，显式重读当前话题只发GET、PUT次数不变；不得重新付费。初次422/余额409显示原因并允许改正；未知恢复4xx保原操作供核对。
5. 确认或在途时退出/换账号，取消原确认且旧回执不回填；同账号正常token轮换保原命令。挂起期间其他新话题提交禁用；整页刷新会清本地恢复信息，先核对原操作。游客/SSR不能写入。
6. 真实服务验证当前发言资格、过滤后空422、余额不足、原键并发、话题/扣款/回执同事务和重启持久化，及新旧页面读同一当前值；这些必须使用开发测试内容另行联调。

## 安全与未验

只迁移既有Bearer写入，无新增匿名入口/viewSkips或Cookie写入口。文本有界、有效Unicode、服务端规则处理，前端纯文本插值，不使用回执HTML。原键恢复、身份和序号隔离、确认信号取消，不把历史成功当当前值。无SQL拼接/远程抓取/SSRF/上传下载或CDN新URL，构建完成版本/source map检查。

真实Java/MySQL事务及过滤/资格、当前授权撤销、多节点/断线同步、服务端批量请求限制、BeforeRequestHandler黑名单/test/validateCaptcha/anonymous.viewSkips、生产CDN和目录枚举仍未运行验收；合成成功不替代真实安全结论。未运行Maven、未动APP、未提交推送。

弹幕价格/发送/即时事件、禁言旧读取、红包/音乐/天气等特殊写入及总清单继续；本轮先闭环话题，不宣称聊天室或全站v3完成。
