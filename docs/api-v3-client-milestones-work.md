# v3 大事记客户端接入（2026-10-27）

本批复用现有五个大事记接口，后端注册仍为285条。未改Java、FTL、SQL或APP，未执行Maven。API.md现有契约不变，UPDATE_LOG记录客户端变化。

## 修改文件与行为

website_v3新增：

- `src/api/v3-milestones.ts`：公开/本人分页与详情、Markdown投稿；日期、长度、媒体配对、URL和响应字段校验。
- `src/stores/v3Milestones.ts`：冻结正文和幂等键、未确认结果恢复、平台确认及身份隔离。
- `src/composables/useMilestones.ts`：双列表和详情独立查询、原页重试、当前状态读取、迟到结果隔离。
- `src/components/MilestoneBody.vue`：公开清洗HTML展示；本人历史原文经现有Markdown预览清洗后才渲染，失败只重读，不直接注入原文。
- `src/components/MilestoneEditor.vue`：标题、日期、Markdown正文及可选媒体/链接表单。
- `src/components/MilestoneRecovery.vue`：全局恢复原操作，仅显示编号和提交回执，链接查询当前审核状态。
- `src/views/MilestonesView.vue`：公开时间线、本人筛选、分页、详情及独立投稿卡片。
- `tools/v3-milestones-test.mjs`：18组真实生产模块专项。

website_v3修改：`src/layouts/AppShell.vue`挂唯一恢复组件并切换页脚入口；`src/router/index.ts`注册页面；`src/entry-server.ts`、`src/seo/payload.ts`、`src/seo/ssrRoutes.ts`提供每请求公开SSR载荷；`tools/v3-ssr-payload-test.mjs`增加隔离回归。两仓AGENTS和本记录维护关键链；迁移总表分别为后端`docs/api-v3-progress.md`及客户端`docs/V3_MIGRATION.md`，后端另更新UPDATE_LOG.md。

## 验证结果

- tools全部63脚本、663组通过；新增大事记18组、SSR载荷新增1组。最后详情滚动调整后重跑大事记18组通过。
- `corepack yarn run build`类型检查、客户端及SSR构建通过。产物74个JS，版本审计0发现，无source map。
- 真实Chrome加载生产构建及SSR合成HTTP夹具，桌面1280×720、移动390×844目视通过；仅合成账号和数据，无生产写入。
- 桌面：表单投稿已保存但返回503→切本人页→全局同键恢复→仅创建一次→待审当前详情；清洗首次503后只重试预览，原始私有HTML标记未进入DOM。两个POST键相同，创建计数1，预览计数2。
- 移动：本人深链接读取待审→公开第二页→第21项详情/刷新，正文及表单布局正常。
- 两场景无page-error/水合警告；桌面有预期投稿503和预览503控制台错误，移动无控制台错误。
- 浏览器证据：桌面`ev_8864e2b0a4f59c4e73f1d5023fc189158c5cc0007ba9b491ac8365c9d1f1fafe`；移动`ev_44d46d193d04bdbe566b0b3b4eb120a6212084f9f233e4c5f66aacc31a3df281`。

## 人工完整链路

1. 从页脚“大事记”进入`/milestones`，游客查看公开第一页、下一页、末页和详情；检查日期范围、清洗正文、媒体及外链，空页正确显示。请求失败应显示错误，重试原页，不能伪造空列表。
2. 登录后切“本人投稿”，分别选择全部/待审/通过/驳回并翻页；选中详情应读取当前状态。用其他账号访问该本人编号应404，游客不得读取本人内容。
3. 填标题、有效开始日期和Markdown，选填结束日期/媒体/链接后提交；成功显示待审回执并可查询当前状态，不声称已公开。非法日期、结束早于开始、超长、危险URL、媒体字段不配对应阻止提交或显示422，不截断正文。
4. 用开发代理模拟提交已生效但响应丢失，离开页面后恢复原操作；确认仍用原幂等键且只创建一次。成功后GET失败只重试读取，不再投稿；后续审核通过时当前GET显示通过，原回执仍仅说明当次待审。
5. 读取本人历史HTML/文本，检查清洗后的正文；预览失败不显示原始HTML，点击重试仅发预览。快速切详情、切页、退出或换账号，迟到正文和回执不得进入新身份；同账号正常令牌轮换保留待恢复命令。
6. 在桌面及390px移动视口检查分页、表单、详情滚动、错误按钮和底部恢复提示。刷新会丢失仅内存保存的未确认命令，页面应明确提示该限制。

## 安全复核与未验收范围

源码确认公开GET仍经ApiV3CommunityProcessor的optionalContent及AnonymousViewCheckMidware；本人GET需content.read，POST需content.write及统一写中间件。本批未修改anonymous.viewSkips、BeforeRequestHandler、验证码解封或鉴权机制。Bearer写入沿既有v3机制，不新增Cookie写入口。

响应字段白名单、本人身份边界及异步清除有专项覆盖；正文仅使用既有服务端清洗HTML，链接拒绝危险协议，模板使用转义文本。客户端不执行SQL、不新增服务器远程抓取；请求在途/恢复有界，不自动批量投稿。全局恢复不渲染私有正文，不写持久缓存。未新增公网库/CDN、上传/下载路径或可枚举目录，产物复扫未发现版本信息/sourceMappingURL/map。

真实Java服务的401/403/越权/CSRF拒绝、数据库审核与幂等事务、XSS清洗质量、限流、黑名单重定向、首访及周期验证码、/validateCaptcha解封、开发模式风控开关及服务端目录响应，均未在本批运行验收。浏览器夹具不代表这些真实权限和反漏扫链已通过；也未验真实视频音源或真实审核人操作。
