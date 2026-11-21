# v3 客户端聊天室撤回（第七十六轮）

## 行为与范围

复用既有 `DELETE /api/v3/chat/messages/{messageId}`，后端仍284入口（283 HTTP + 1 WS），本轮无Java/FTL/SQL/APP及公开接口契约变更，API.md不改。

经典、简约和浮动聊天室统一按当前账号ID与消息作者ID显示本人撤回入口，改名不丢入口，同名不同ID不能认领。红包不显示撤回。点击后明确确认不可恢复及普通用户每日0/32/64…费用，实际权限、费用和扣款由服务端决定，不在前端推测当前应付数。

v3ChatRevoke保留原消息ID/幂等键；503、断网或回执损坏不清原命令，跨站内页面/关闭浮窗后可恢复；同用户正常token轮换保留，退出/身份版本/账号变化清理。初次明确4xx不保留未提交命令；结果未知后的恢复4xx不能证明首次未提交，仍保留原命令。挂起期间不接第二个撤回，不换键重试。

成功仅使用白名单id/removed/cost，显示实际费用；共享timeline移除消息并使表态失效。持久到当前授权生命周期的删除集合防止旧历史、旧详情或迟到发送投影复活消息，清屏也不恢复已撤回消息；新的账号边界清集合。独立原文弹窗/已生成引用不作为删除事件级联清理目标，本批不宣称撤销读者此前取得的内容。

## 实际修改文件

客户端根 `/Users/ling/Documents/fishpi/website_v3`：

| 文件 | 修改行为 |
| --- | --- |
| `src/api/v3-chat-revoke.ts` | DELETE、原键、严格目标/回执校验及字段投影 |
| `src/stores/v3ChatRevoke.ts` | 共享待确认命令、恢复、当前身份隔离、成功清理 |
| `src/components/chat/ChatRevokeAction.vue` | ID归属/红包过滤、绑定目标和身份的可取消确认 |
| `src/components/chat/ChatRevokeRecovery.vue` | 原操作恢复、实际成本/错误提示 |
| `src/stores/v3ChatTimeline.ts` | 确认删除、表态失效、删除集合防迟到复活 |
| `src/views/ChatroomView.vue` | 两种布局接撤回与恢复组件 |
| `src/components/chat/WhisperConversations.vue` | 浮窗本人撤回及共用恢复状态 |
| `src/stores/chat.ts`、`src/api/fishpi.ts` | 删除旧撤回方法/未用wrapper |
| `tools/v3-chat-revoke-test.mjs` | 实际API/Pinia/timeline/完整Vue专项11组 |
| `tools/v3-chat-timeline-test.mjs` | 增确认删除的迟到历史/发送及清屏回归 |
| `tools/v3-messages-test.mjs` | 私信抽屉隔离验证适配新独立聊天室组件 |
| `AGENTS.md`、`docs/V3_MIGRATION.md`、`docs/api-v3-client-chat-revoke-work.md` | 架构/进度/本记录 |

后端仅文档：`AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`docs/api-v3-client-chat-revoke-work.md`。

## 验证结果

- 撤回11、历史16、私信21、发送15、原文13、在线13、表态16、实时9、通知6、启动9，共129组通过；无异常日志。真实API解码、真实共享Pinia和Vue确认/恢复组件执行，外围HTTP及对话框决定用测试适配。
- 类型检查及客户端/SSR双构建通过；72个生成JS版本审计0命中，无map、sourceMappingURL/sourceURL；两仓库diff空白检查通过。
- IAB合成HTTP+实际WS：作者旧用户名但相同ID能显示撤回→确认说明→合成服务保存撤回后503→页面保留原恢复命令→站内到首页浮窗→原键恢复成功→完整页/移动经典均不再显示该消息。HTTP记录同一键首次replay=false、恢复replay=true；浏览器warn/error为空。没有真实账号、消息或积分写入。
- 移动截图 `/tmp/rhythm-v3-chat-revoke-mobile-proof.png`。测试页及临时服务完成后关闭。

## 完整人工测试

1. 登录进入 `/cr`，找本人普通消息；同ID改名前消息仍显示撤回，其他ID与红包不显示。分别经典、简约和390px移动端验证。点撤回→取消，不应发DELETE；再点→确认，检查Bearer和本次Idempotency-Key、无旧apiKey及旧POST。
2. 成功后原消息从完整页/首页摘要/浮动聊天室移除，显示服务端实际cost；刷新当前历史/加载更早和迟到详情/发送响应不能重新显示该ID。检查reaction不再显示于已移除消息。
3. 模拟服务端提交后断线/503：原消息可暂留，不假报成功；恢复按钮及原ID保留。站内离开页面/关浮窗再开，恢复仍为同DELETE路径/同键，成功只扣一次。整页刷新会清本地恢复信息，先在原会话核对，不随意换键。
4. 模拟初次积分不足409、目标404或权限403：展示原因且不把失败当成功；结果未知后的恢复若返回4xx仍保留原命令。挂起期间其他撤回禁用。红包由服务端409拒绝，不能绕客户端取代服务端验证。
5. 确认弹窗打开后切目标/退出/卸载，原确认取消不发请求；在途换账号不回填旧回执。普通同账号token轮换保留原命令，新账号不得恢复上一身份的命令。游客/SSR不发写入。
6. 真实Java/MySQL中复核同键重放、每日计次/免费角色/余额不足、消息/扣费/回执同事务、另一客户端实时同步及旧新共用计次。这些运行验证须使用可撤回的开发测试内容。

## 安全与未验证

本轮只接既有Bearer写入口，无新增匿名路由/权限豁免/viewSkips。目标ID、键与cost有界校验，结果白名单，身份及目标变化取消确认、迟到响应隔离，重试保原键防重复费用；UI不代替服务端ownership/当前授权检查。无HTML写入、SQL拼接、远程抓取/SSRF、上传下载或CDN新URL。构建资源完成版本号及source map检查。

真实服务未验：撤回事务/余额/旧新计次及并发、账号撤权、多节点消息同步、服务端批量请求限制、BeforeRequestHandler黑名单/test/validateCaptcha/anonymous.viewSkips、生产目录枚举及CDN访问。没有用合成成功代替真实安全验收。

禁言读取目前仅旧入口、消息上下文跳转、红包/话题/弹幕/音乐/天气写等客户端迁移及总清单仍待。未运行Maven、未提交/推送；全站v3目标仍未完成。
