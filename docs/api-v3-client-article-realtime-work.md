# v3 客户端文章实时订阅与热度（第六十九轮）

2026-10-27。消费既有文章票据、WS和热度GET，无新增接口契约，284注册保持。无Java/FTL/SQL/APP/Maven/提交推送。

## 实际文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-realtime.ts`：共用原通知连接生命周期，新增绑定文章的票据/ready/article.changed校验；只同源固定路径，凭据仅首帧，保留通知行为。
- `src/api/v3-article-heat.ts`：当前热度GET，精确articleId与非负安全整数验证，失败不造0。
- `src/views/ArticleView.vue`：移除旧article-channel和旧评论正文拼接。登录后取票订阅，首次ready/重连和变更显示更新提示、失效当前article/comment表态并刷新热度；用户明确读取当前文章。重复提示合并、读取期间新事件保留；请求按身份/目标/序号隔离，正常令牌轮换重新取票，退出/路由切换/卸载清理。权限读取401/403/404清除旧正文。热度独立不阻塞正文与更新读取，无后台轮询；游客不取票、不读鉴权热度。
- `src/components/articles/LongArticleReader.vue`：live-status插槽让固定全屏长文也显示更新提示和热度，普通文章/长文都支持移动。
- `src/api/fishpi.ts`：删除已无消费者的旧fetchArticleHeat；随机文章仍保留旧请求，不能宣称文章页全部旧接口已迁移。
- `tools/v3-article-realtime-test.mjs`：6组实际TS协议/热度验证，模拟WS与定时器。
- `tools/v3-comments-test.mjs`：完整生产ArticleView新增19/20组，验证无Socket正文、明确GET、权限丢失清正文和新事件不被旧读取清除。
- `AGENTS.md`、`docs/V3_MIGRATION.md`：长期约定及进度。

后端本轮仅本文件、AGENTS.md、UPDATE_LOG.md、docs/api-v3-progress.md。API.md已有完整契约保持。

## 验证证据

实时6、通知6、评论20、owner12、reaction16组测试通过；类型检查及客户端/SSR双构建通过。两工作区diff检查、生成静态JS版本与source map扫描见本轮结果。

浏览器实际WebSocket与本地合成HTTP：普通文章恢复登录→文章取票→同源无查询WS→首帧认证→ready/article.changed→更新提示及“本节点约3个连接”→点击读取→显示修改后的HTTP正文二。夹带的SOCKET_CONTENT_MUST_NOT_APPEAR未显示；移动390×844长文同样显示提示，点击后提示清除。合成WS自行实现测试握手与消息，不代表Java实时服务或真实权限成功。

截图：`/tmp/rhythm-v3-article-realtime-proof.png`、`/tmp/rhythm-v3-article-realtime-mobile-proof.png`。

## 人工完整链路

1. 确保此前Java/迁移已部署，Java更新须先通过IDEA重新编译或重启；本轮未改后端。登录开发网站打开文章，网络应POST对应文章realtime-ticket，WS路径不带票据/apiKey，authenticate首帧仅发一次；显示本节点近似连接数，不当全站人数。
2. 用另一合法会话评论/编辑/表态。当前页出现更新提示；点击“读取最新内容”，HTTP成功后显示本次授权可见内容并清提示，不直接消费WS正文。评论输入未提交时核对草稿保留；长文与移动重复操作。
3. 模拟HTTP503，保留更新提示与明确读取错误，再次点击只读HTTP；热度503显示暂不可用，重读成功可显示真实0。读取期间再发变更，新提示应继续保留。
4. 撤销目标权限或删除文章，再重读应清除旧正文。换账号/退出/换文章，迟到票据/HTTP/旧连接事件不得回填；正常token轮换应重新取票，不能延长旧连接授权。断开WS后重连重新取票、重新提示读取。
5. 游客/SSR不取文章票据、无鉴权热度请求；匿名访问文章仍由既有HTTP和匿名盾处理。观察没有旧article-channel或api/article/heat请求。

## 安全与限制

无新增匿名路由/viewSkips/上传下载/CDN地址/数据库写。首帧凭据不进URL或持久存储；精确频道/目标/路径验证，不把WS任何正文写入DOM，HTTP原有清洗白名单继续适用；不新增SQL/远程抓取/XSS入口。取票/热度走既有Bearer身份；拒绝401/403/Captcha停止该次连接重试，旧身份/迟到结果丢弃；变更提示合并、同一更新读取禁重入，热度在途时不扇出额外请求。

真实Java票据一次性、授权过期/权限撤销、跨节点热度与重连、后端限流及黑名单/test/validateCaptcha运行回归尚未验；无新增公网静态资源地址，扫描不能替代生产目录枚举与匿名链路验收。随机文章等其他旧链路仍待迁移，不虚称全页或整个v3已完成。
