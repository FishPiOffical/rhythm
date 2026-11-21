# v3 客户端文章本人管理接入（第六十八轮）

2026-10-27。本轮消费既有本人原文、文章删除、付费置顶接口，不新增路由或改变契约；总注册仍284（283 HTTP+1 WS）。未改Java/FTL/SQL/APP，未执行Maven，未提交推送。

## 实际文件与行为

客户端根目录 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-article-owner.ts`：本人原文GET仅保留id/status；严格校验目标、操作、请求键和回执元数据，DELETE无正文，置顶POST空对象。不显示原文/付费正文。
- `src/stores/v3ArticleOwner.ts`：删除不可恢复确认、每次100积分置顶确认；未知结果保留原kind/id/key，跨SPA恢复；新请求明确4xx可修正，未知操作恢复4xx仍不换键。成功后缓存失效、账户刷新独立，不因读取失败重发写入。
- `src/components/articles/ArticleOwnerActions.vue`：惰性本人证明；匿名不猜真实作者，合法本人可编辑/管理；锁定/永久置顶提示；目标/身份/卸载取消确认并废弃迟到结果。恢复其他文章操作不改变当前文章。
- `src/views/ArticleView.vue`：编辑资格按公开作者ID精确判断；置顶后当前GET，失败只重读；确认删除清正文，导航失败保留已删除页面和首页链接。
- `src/components/articles/LongArticleReader.vue`：共享管理插槽，移动菜单独占一行，无新增嵌套卡片。
- `tools/v3-article-owner-test.mjs`：12组实际API/Pinia/完整Vue与SSR专项。
- `tools/v3-comments-test.mjs`：适配新父依赖，新增第18组完整ArticleView置顶读取/删除导航失败/作者ID链。
- `AGENTS.md`、`docs/V3_MIGRATION.md`：持续约定与进度。

后端仓库本轮文档：本文件、`AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`。API.md契约不变，无需重复接口。

## 已验证

12组owner、18组comments、29组posts、15组article-interactions通过；`corepack yarn run build`含类型检查与客户端/SSR构建通过。两个工作区diff检查通过。生成JS版本审计及sourceMappingURL/sourceURL/map扫描结果见本轮日志。

真实浏览器+本地合成HTTP：普通文章展开本人管理→置顶100积分确认→取消→删除不可恢复确认→取消；长文更多菜单390×844→本人证明→置顶确认→取消；调整菜单后说明整行可读。fixture请求记录仅本人证明GET，没有文章DELETE/置顶POST。截图 `/tmp/rhythm-v3-article-owner-mobile-menu-proof.png`。合成账号、原文与正文均不是真实资料。

## 人工完整链路

1. 在开发服务部署现有v3后，用本人账号打开普通文章，展开“本人文章操作”；应获得本人证明与管理按钮，页面不显示原文或付费源文。换游客/他人文章不出现管理；匿名本人可证明，匿名他人拒绝。
2. 置顶确认显示每次100积分，取消无写入；在可丢弃的开发文章确认后核对仅扣100、列表及详情当前stick更新。余额不足/永久置顶/状态变化应拒绝且不误示成功。人为阻断回执，再跨SPA恢复，核对同键且只扣一次。
3. 对满足满24h且无互动等限制的开发文章，经用户确认删除；应移除正文并返回首页。评论/周报/他人/锁定等失败保留正文。模拟删除已提交但响应丢失，恢复原DELETE键；模拟导航失败仍显示“文章已删除”和首页链接，不能误重删。
4. 置顶已确认后让详情GET失败，点击“重读文章状态”只发GET。普通token轮换保留操作，退出/换账号/改文章/卸载不得回填旧正文、旧确认或导航到错误文章。长文更多菜单与390px宽度重复以上链路。
5. 整页刷新会丢失内存恢复信息，先核对原操作，不宣称持久离线队列。

## 安全与未验项

本轮无新匿名入口、viewSkips、上传/下载、CDN URL或数据库写入；新读取/写入使用已有Bearer request边界，不新增Cookie/apiKey例外。精确目标与白名单、无动态HTML、无拼接SQL或远程抓取，未新增注入/XSS/SSRF入口；禁重复在途，未知结果不换键，后端仍最终校验所有权、scope、余额和策略。敏感原文仅已授权GET取得并即时投影，迟到身份隔离；不做日志输出。

真实服务器的未鉴权/越权、CSRF、并发限流、数据库扣费/删除事务、黑名单/test/validateCaptcha、生产匿名风控与发布静态目录枚举未在本轮运行验证；旧random/heat/article-channel请求仍在页面其他链路，未宣称全页无旧API。源码检查不能替代这些运行验收。未改变Java/FTL，本轮无需重编服务；联调先确保此前Java已通过IDEA重新编译/重启。
