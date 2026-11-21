# v3 客户端本人勋章（2026-10-25）

上一轮QQ/标签已实现，本轮继续迁移真实客户端尚依赖旧apiKey的本人勋章链。仅使用既有v3勋章接口，无后端路由或业务规则变化；v3仍264条（263HTTP+1WS）。不能因此称用户公开勋章、整个账号页或全部v3已迁移。

## 实际修改文件

客户端根目录：`/Users/ling/Documents/fishpi/website_v3`。

- `src/api/v3-medals.ts`：GET本人分页、PUT明确displayed、POST上下相邻move，使用通用v3 Bearer传输；保留勋章定义ID原值并编码路径，不传用户ID/apiKey。严格投影公开文本与order/expiresAt/displayed，校验分页、重复ID和操作回执，不制造空默认或用列表下标伪造顺序。
- `src/components/settings/OwnerMedals.vue`：读取隐藏在内的当前有效本人勋章，真实分页；展示/隐藏、上下移动分别提交明确命令。未知结果保留原目标/方向或展示值及key，阻止混入另一操作，重试恢复同一次动作。成功后重新读当前页，不用历史回执覆盖后来顺序；读失败单列说明，不将已提交动作改报失败或重复移动。最后一页过期缩小后读取实际末页。凭据变化/卸载清私有状态，旧读写不回填。
- `src/views/SettingsView.vue`：账号页接入OwnerMedals，移除原需apiKey才能执行的本人勋章加载/切换/排序、孤立状态与旧SCSS。背包、改名、联系方式等其他旧模块本轮未迁移。
- `tools/v3-medals-test.mjs`：实际TS契约与Vue/Pinia setup状态专项。
- `README.md`、`AGENTS.md`、`docs/V3_MIGRATION.md`：记录本批范围与边界。

后端工作区仅文档：`docs/api-v3-client-medals-work.md`（本文）、`docs/api-v3-progress.md`、`UPDATE_LOG.md`。既有API.md契约无需变更。

新组件复用现有MedalIcon；图像仍走既有`/gen`页面会话通道，无法取图时以名称文本回退，不把Bearer塞入URL或旧apiKey。v3 DTO没有原始样式JSON，不伪造同款图形。此处只完成本人列表/写操作的v3迁移，真实页面会话下的图像需后续联调。

列表仅显示有效勋章，包含隐藏；移动按后端全部持有记录排序，过期也参与。因此画面中某枚有效章可能移动后仍在同一可见相邻位置，不在前端伪装交换。空列表明确“暂无有效勋章”，读取错误不能冒充为空。

## 已执行验证

- `corepack yarn node tools/v3-medals-test.mjs`最终6组exit0，`/tmp/rhythm-v3-client-medals-tests.log`：真实分页与隐藏/永久DTO、编码ID/闭合写body/key、迟到读写及退出隔离、未知ACK同键重试与互斥、成功写后刷新失败只重读、末页缩小及卸载隔离。传输使用队列适配、Vue实际setup与Pinia、无DOM renderer；不等于真实HTTP后端权限/数据库测试。
- `corepack yarn run build`类型检查/client/SSR通过，`/tmp/rhythm-v3-client-medals-build.log`；只编辑Vue/TS/SCSS源码，不直接改生成CSS/min.js。
- IAB实际账号页使用本地HTTP/WS合成夹具：读取21枚两页、将第一枚隐藏章设为展示；下移提交后模拟503，页面保留未确认命令、其他写按钮禁用；点击重试，夹具日志仅一次move提交与同命令回放，第一/二章交换。第二页读取第21枚并隐藏成功，分页和状态可见。Hydration日志空；截图`/tmp/rhythm-v3-client-medals-proof.png`，夹具日志`/tmp/rhythm-v3-client-medals-fixture.log`。无真实账号或生产写入。临时服务与页面完成后关闭。
- 生成62个JS的js-version-audit零命中，`/tmp/rhythm-v3-client-medals-js-audit.json`，工作区diff空白检查通过。未进行真实移动设备和完整Rhythm运行验收。

## 人工完整链

1. 先确认前批Java/FTL已通过IDEA重新编译或重启并已部署既有v3迁移，不能拿旧服务行为判断新源码。本轮仅客户端源码，执行yarn run build后，开发客户端指向localhost:8080并用127.0.0.1:5173打开。
2. 本人v3登录→账号设置→“你的勋章”：读取展示和隐藏的有效勋章，真实总数/分页；有效勋章为空显示明确空态，接口失败显示错误且无可操作旧数据。超过20枚翻页，含定义ID空格/中文/斜杠时路径正确编码。
3. 展示隐藏章→成功→重新读/刷新仍展示；隐藏已展示章→刷新保持。上下移动→当前页按服务端重读结果，跨页或过期记录参与时不假设必然交换两个可见项。边界移动按原结果保持，不额外造顺序。
4. 模拟提交成功后断开响应，按钮显示未确认重试，其他写操作禁用；恢复后重试使用原目标/命令/key，排序只移动一次。模拟成功写后读取失败，应同时显示“已保存/移动已完成”和读取错误，重新读取只GET，不再POST。明确404/409/422拒绝应释放命令并显示错误。多设备后来改序后重放只取当前读结果。
5. 在读取、写入和重试期间退出/切换账号，旧勋章/待确认命令/结果清除，迟到响应不得带入新身份；无Bearer、Cookie/apiKey单独、缺profile.read/settings.write、失效授权/停用账号拒绝，不能指定他人身份。PC与移动设备均从入口完成以上流程。
6. 真实新IP/test与黑名单/验证码解封、CSRF/Bearer边界、批量请求、公开CDN版本/sourceMappingURL、目录枚举和上传下载返回路径需全链回归。本轮夹具不证明这些已通过；既有/gen会话图片与其他账号表单也仍需真实验证。

## 安全与反漏扫结论

本轮无新增匿名入口、viewSkips、后端权限规则、SQL或上传下载能力。仅本人Bearer资源，公开资料与他人勋章读取未扩展；操作不接受userId/apiKey。定义ID保留原值后编码，字段白名单投影，名称描述用Vue文本，未新增HTML执行/动态SQL/远程抓取。请求有分页与body边界，未知ACK同键，不进行全量自动翻页或连续无限重试。

页面旧/gen图片路径保持，沿已有编码和无图回退；不通过图像请求泄露Bearer。实际服务权限、CSRF、代理、黑名单/test/解封、分布式滥用限制、全CDN/目录及上传下载路径尚未验。生成JS定向扫描零命中不等于整站防扫描已验收。
