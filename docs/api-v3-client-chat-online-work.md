# v3 客户端聊天室在线名单（第七十五轮）

## 范围与行为

复用已有 `GET /api/v3/chat/online-users`，路由总数仍284（283 HTTP + 1 WS），没有新增后端契约、Java、FTL、SQL、APP改动。标签联想已在第六十七轮完成，见 [标签联想记录](api-v3-tag-suggestions-work.md)。

完整聊天室经典/简约布局、在线侧栏及首页共享本人当前授权下的可见在线名单和总数。每页20人，只按当前页用户名筛选；服务端 total 不等于当前页条数。标明非全站实时人数、节点采样时间和完整性；不宣称其他实例v3连接已全站共享。

首次未知/失败与真实0区分；第二页失败保留目标页供显式重试，错误不伪装空名单。在线人数缩减时允许请求页为空并返回上一页。退出或换用户清空旧名单、取消请求，迟到结果不回填；同用户token轮换重新读取当前页。chat ready、窗口聚焦和显式刷新合并在共享store，不另开连接或读取所有页。

首页人数与话题直接跟随当前chat状态，不从旧SSR/首页快照恢复；删除旧online wrapper与SSR载荷。普通消息、特殊能力和禁言不在该读取链中回退。

## 实际文件

客户端根目录 `/Users/ling/Documents/fishpi/website_v3`：

| 文件 | 行为 |
| --- | --- |
| `src/api/v3-chat-online.ts` | Bearer在线分页、严格分页/采样元数据校验、三字段用户投影 |
| `src/stores/v3ChatOnline.ts` | 共享分页/总数/错误/采样、身份隔离、取消与合并刷新 |
| `src/components/chat/ChatOnlineList.vue` | 本页筛选、分页、失败原页重试、范围/采样说明 |
| `src/stores/chat.ts` | facade接入online store，连接ready触发刷新 |
| `src/chat/sidebar/modules/OnlineModule.vue` | 共用在线列表组件 |
| `src/views/ChatroomView.vue` | 经典/简约显示服务端总数，展开共用列表 |
| `src/views/HomeView.vue` | 当前可见总数及话题，删除旧online读取/快照回填 |
| `src/entry-server.ts`、`src/seo/payload.ts` | 移除旧在线预载及载荷 |
| `src/api/fishpi.ts` | 删除无消费者的旧online wrapper/type |
| `tools/v3-chat-online-test.mjs` | 实际API/Pinia/Vue专项13组 |
| `tools/v3-chat-publication-test.mjs`、`tools/v3-chat-source-test.mjs`、`tools/v3-reactions-test.mjs` | facade新增依赖适配；online行为由专项覆盖 |
| `AGENTS.md`、`docs/V3_MIGRATION.md`、`docs/api-v3-client-chat-online-work.md` | 当前架构、进度和本记录 |

后端文档：`AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`docs/api-v3-client-chat-online-work.md`。API.md无本轮契约变化。

## 已验证

- 在线13、历史15、实时9、发送15、原文13、私信21、表态16、通知6、启动9，共117组通过，无异常警告/错误。
- `corepack yarn run build` 类型检查及客户端/SSR构建通过；70个生成JS版本审计0命中，无map、sourceMappingURL/sourceURL；两个仓库diff空白检查通过。
- IAB实际页面+合成HTTP/真实WS：首屏20人/total21；第二页503显示未知并重试原页，恢复为1人/total21；390×844经典展开列表、简约总数与侧栏、首页人数及当前话题通过，控制台warn/error为空。只用合成数据，没有发送真实消息或操作真实账号。
- 截图 `/tmp/rhythm-v3-chat-online-mobile-proof.png`；临时合成服务与浏览器测试页验后关闭。

## 完整人工验收

1. 已登录进入 `/cr`，经典展开在线列表与侧栏对照：总数与响应total相同，20人一页；用户名点击本人公开资料路径，不注入HTML。切简约布局、390px移动端同测。
2. 下一页→断网或503：显示未知及“重试第2页”，不显示0/空成功；恢复后原页重试，核对p=2、末页禁用下一页及total未变。真实total=0显示0；人数缩减到请求页之外时可返回上一页。
3. 输入用户名筛选只过滤本页，不修改总数或自动抓全表；清空恢复本页，翻页清查询。观察采样不完整、未采样与有时间三种提示，不将人数理解为全站实时值。
4. 从完整聊天室到首页及浮动聊天，核对当前总数一致、首页话题随后端当前读更新。刷新/聚焦会重读；网络面板不应请求旧 `/chat-room/online-users` 或旧SSR在线载荷。
5. 慢请求期间退出/换账号，旧名单立刻清空，旧响应不回填；新账号从第一页读取。同账号token轮换保留当前页并重读。游客/SSR不发本人名单请求。
6. 在真实服务切在线隐私、账号停用、自身/管理员身份，核对名单与total均按当前权限；多节点采样缺失/陈旧不应被前端改称完整全站人数。

## 安全与未验

本轮只迁移既有Bearer读取：用户响应白名单、纯文本用户名和编码路由，分页数有界，异常元数据拒绝，取消/身份版本隔离。无新匿名入口、viewSkips、Cookie写操作、数据库写、上传下载或远程抓取，不引入CSRF写入、SQL拼接、SSRF。未新增静态CDN URL；构建产物完成版本和映射检查，不把采样完整性当实时完整性。

真实Java/MySQL可见性、授权撤销、多节点/重连、服务端批量限流、BeforeRequestHandler黑名单/test/validateCaptcha/anonymous.viewSkips、生产静态目录枚举和CDN访问未运行验收；源码/合成测试不替代这些结论。禁言、上下文跳转、撤回、红包/话题/弹幕/音乐/天气写及总清单其他客户端迁移仍待，全站v3未完成。未运行Maven，未提交或推送。
