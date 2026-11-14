# v3 首页独立资源读取（2026-10-28）

第103批。沿用290入口（289 HTTP + 1 WS），外部接口路径、字段及权限契约不变。本批无Java、FTL、SQL、Repository、APP或Maven操作；此前Java部署未获IDEA重编/重启确认，本批浏览器使用本地合成HTTP。

## 实际文件与行为

website_v3：

- `src/api/v3-home-reads.ts`：最新40、热议12、最近/热门专栏各8、标签24五种固定资源及白名单解码；SSR分别保留成功数据、合法空数组和失败null，不用文章冒充专栏。
- `src/composables/useHomeRead.ts`：各资源独立加载、错误、刷新与重试；失败清旧，身份变化重新读取，序列/身份/卸载拒绝迟到结果，不进入旧SWR或持久缓存。
- `src/components/home/HomeReadStatus.vue`：加载、未读取、错误、合法空态和刷新按钮分别展示。
- `src/views/HomeView.vue`：移除聚合缓存和吞错假空；最新两栏保留完整40项，热议显示真实回帖数，匿名作者无身份链接，置顶标志及0浏览数正确展示。长篇架与紧凑专栏使用真实目录/章节数，封面失败用CSS占位。布局容器透明，卡片同级。聊天室读取状态独立；同账号普通Token轮换保留草稿，退出换号清除。
- `src/api/v3-article-list.ts`：导出共用文章摘要解码，列表行为保持。
- `src/entry-server.ts`、`src/seo/payload.ts`：五资源公开预取、按应用一次消费和再次白名单投影；其他首页模块继续独立。旧payload兼容helper仍有保留，不宣称全仓旧代码清理完成。
- `src/composables/useRepeaterFeed.ts`、`src/components/home/HomeRepeaterStation.vue`、`src/views/RepeaterView.vue`：增加ready，未知读取不冒充空结果；合法SSR空数组不重复读取。Home只消费既有复读机预取，不重复发起父级请求。
- `tools/v3-home-reads-test.mjs`：18组实际资源/组合函数/完整Home组件测试，含各资源故障隔离、重试、真实专栏、身份迟到、草稿保留/清除及卸载；无真实消息发送。
- `tools/v3-ssr-payload-test.mjs`：新增第10组，null与空数组、独立应用/一次消费及白名单。
- `tools/v3-repeater-test.mjs`：新增第19组ready及合法空数组；更新Home接线断言。
- `tools/v3-discovery-extras-test.mjs`：调整SSR预取索引断言。
- `AGENTS.md`、`docs/V3_MIGRATION.md`、本记录。

Rhythm：`AGENTS.md`、`docs/api-v3-progress.md`、`UPDATE_LOG.md`、本记录。API.md契约未变。

## 实际验证

- tools共77脚本878组通过，认证transport单独12组通过；`corepack yarn run build`明确exit0，类型、client、SSR构建通过。生成75 JS版本审计0发现，无新增source map；git diff --check通过。
- IAB 1280×720生产构建与SSR合成HTTP：首页最新503独立重试恢复40项，热议/目录不受影响；标签刷新503清旧并重试；热门目录503不回退最近目录或热议文章；紧凑专栏显示真实8章；复读机失败不显示成功空态。请求计数证明资源单项重试。
- 第二个本地夹具用同一合成账号，输入未发送草稿，下一次最新读取401，实际共享transport刷新Token后重读，草稿仍在。记录有2次refresh，其他POST仅实时票据申请，未发送消息。夹具返回预期503的相邻服务不算验收通过；临时页/服务已关闭。

## 安全与反漏扫复核

未新增匿名入口、写接口、权限例外或anonymous.viewSkips；现有GET和全局黑名单/AnonymousViewCheckMidware/Captcha链保持。只使用固定资源路径与现有Bearer，SSR公开数据按应用隔离，响应白名单及匿名链接保护，无新增HTML注入、SQL、远程抓取/SSRF代理或批量接口。未改变Cookie/API客户端CSRF边界，Token不写持久存储。未新增CDN、上传下载路径或目录资源；生成JS版本扫描0，无map/sourceMappingURL。

此为源码/隔离验证结论，未实际验证真实IP黑名单、首访/计次验证码、解封、开发模式、生产目录枚举、真实账号权限/凭据撤销或MySQL。390px、触屏拖动及完整真实后端端到端仍待人工验收，不能据此声称v3全部完成。

## 完整人工测试步骤

1. 启动最新website_v3并进入首页（桌面和390px各一轮）；若要验证此前Java接口，先由用户通过IDEA重编或重启。确认最新两栏共40上限、热议真实回帖数、匿名无作者链接、0浏览数不被覆盖。
2. 单独使最新GET失败，其他模块仍展示；点击本模块重试只恢复最新。分别测试标签、热议和两种专栏故障，失败不能显示旧数据或“暂无”成功空态；真实空数组应显示空态。
3. 在长篇专区切换最近/热门，核对真实专栏ID、章数及章节入口，空/坏封面显示占位；右侧紧凑专栏也应来自目录，无伪造作品或热度。
4. 登录后读取期间退出/换号，迟到旧响应不得恢复；同账号自动刷新Token后重读。输入聊天室草稿但不发送，普通刷新应保留，退出换号应清除。复读机未读、失败及真实空结果应有不同提示。
5. 刷新SSR首页，分别验证部分资源失败与成功空结果；成功资源不被其他失败拖住，客户端仅补读未知数据。测试桌面/移动布局、模块拖动、长篇横向滚动与离页返回。
6. 用游客、新IP、受限账号及合法Bearer回归实际匿名验证/黑名单/验证码与内容可见范围；未部署后端前不要把旧行为判断为本批前端失败。
