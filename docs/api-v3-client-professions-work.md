# v3 客户端本人职业（2026-10-25）

本轮迁移此前依赖旧apiKey的职业设置页，使用既有七类入口：目录、本人分页、详情、经验记录、主职业、跳过引导和隐私。没有新增后端接口或改变业务规则，v3总264条保持。成长提示、职业排行和他人公开资料的客户端迁移不在本批，不能称全部职业模块完成。

## 实际修改文件

客户端根目录 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-professions.ts`：严格投影目录、本人状态/分页、历史进度详情/等级路线/贡献与90天经验分页；三种明确写命令，只提交各自字段。CUSTOM必须完整十模块，其他预设仅发preset，不能把局部map合并成公开默认。本人主职业与已有进度分别读取，未产生进度的选择不会伪造零级。IDs按契约校验，分页及重复ID/枚举/不安全整数/错误详情绑定拒绝，不吞错误为假空结果。
- `src/components/settings/OwnerProfessions.vue`：本人进度和当前已发布目录分别分页；从目录选择主职业、明确说明跳过会清除主职业、六种隐私预设与完整十模块。只有本人状态读成功才允许写，失败没有假公开默认。主职业或分页刷新不覆盖未提交的隐私草稿；成功写入后重读当前状态，不用旧回执覆盖后来选择。当前名称未在已加载页时明确展示“已选职业（编号…）”，不把编号当名称。
- 同一组件还提供真实本人详情、等级路线、贡献汇总及最近90天正负经验记录/分页；详情404明确报错，不造等级。切换目标/凭据或卸载隔离迟到结果，打开详情会聚焦并滚动定位，scroll-margin避开固定顶栏。纯文本展示，无嵌套背景卡片。
- `src/views/SettingsView.vue`：职业标签接入OwnerProfessions，移除原fetchProfessionMe/setProfessionPrimary/setProfessionPrivacy调用与孤立状态/SCSS。其他账号/私信/聊天室和用户公开职业界面没有顺手改造。
- `tools/v3-professions-test.mjs`：实际TS契约及Vue/Pinia setup专项。
- `README.md`、`AGENTS.md`、`docs/V3_MIGRATION.md`：记录本批状态、私有资料和未验边界。

后端工作区仅修改文档：`docs/api-v3-client-professions-work.md`、`docs/api-v3-progress.md`、`UPDATE_LOG.md`。API.md已有契约保持，无新SQL/Repository或Java/FTL修改。

## 重试与数据边界

写入在本组件内互斥。未知结果保留原命令的快照和key，不让后续表单编辑或另一写命令替换待恢复动作；明确4xx拒绝释放操作，网络/5xx或响应异常保留原键。成功写后列表读取失败分列反馈，重试只GET、不再次执行原写操作。重放后重新读当前状态，不把历史回执当当前选择。凭据变化/离开组件会清私有状态与待确认命令；尚未提供跨路由、刷新或设备间的命令恢复存储。

隐私按后端实际规则：LEVEL_ONLY和PRIMARY_ONLY均只公开primaryProfession，其余隐藏；不依据名称自行发明“等级可见”。CUSTOM从当前真实十模块初始化，空/坏响应不允许保存。跳过清除选择但不删经验、不改隐私；选择已发布职业不要求已有进度，也不造进度。

## 已执行验证

`corepack yarn node tools/v3-professions-test.mjs`最终7组exit0，日志`/tmp/rhythm-v3-client-professions-tests.log`：

1. 目录与本人分页、已选但无进度、退休进度、完整CUSTOM映射、重复/坏响应与错误拒绝。
2. 主职业/跳过/隐私三个闭合请求、全CUSTOM与preset-only、独立key，无apiKey/userId输入。
3. 本人详情绑定、完整路线、贡献与负经验记录、无进度404不合成零级。
4. 实际Vue/Pinia换号清状态、忽略迟到本人资料，不回退公开默认。
5. 未知CUSTOM结果冻结完整原命令/key，互斥并精确重试。
6. 写成功而读失败保留成功、无可操作旧状态、只GET重试。
7. 分页保留隐私草稿、明确拒绝释放命令、切换详情/记录和迟到写隔离。

测试传输队列适配、Vue实际setup及无DOM renderer，不是后端SQL/权限/完整HTTP测试。`corepack yarn run build`类型检查/client/SSR通过，`/tmp/rhythm-v3-client-professions-build.log`；SCSS源码经构建，没有直接改生成CSS/min.js。生成62个JS审计零命中，`/tmp/rhythm-v3-client-professions-js-audit.json`，两工作区diff空白检查通过。

实际IAB本地合成HTTP/WS夹具：目录21项分两页，初始主职业与已有经验不是同一职业；CUSTOM十字段可见，经验改HIDDEN后服务模拟提交成功/503，页面保留原命令，重试日志只有一个privacy提交与同命令回放。改SELF_ONLY、从第二页选择无进度职业21，本人仍只有职业1的50经验；跳过清除选择而原经验/隐私保留，再重新选择。详情显示两级路线、最高级、贡献、正负经验和记录第二页；标题聚焦并避开固定顶栏。Hydration日志空，截图`/tmp/rhythm-v3-client-professions-proof.png`、`/tmp/rhythm-v3-client-profession-detail-proof.png`，请求证据`/tmp/rhythm-v3-client-professions-fixture.log`。无真实账号/生产写入。临时页面与服务器完成后关闭。

## 人工完整链

1. 本轮只改客户端；联调前仍要确认前批Java/FTL已通过IDEA重新编译或重启、相关v3迁移已部署，不以旧进程行为否定新源码。客户端yarn run build，指向localhost:8080，通过127.0.0.1:5173本人登录→设置→职业。
2. 核对主职业与真实已有进度分别呈现；未选、已跳过、已选但无进度、退休/已删主职业、空目录和目录分页都不伪造零级或默认选项。读取失败要显示错误并禁止写，重试后恢复真实设置。
3. 目录第二页选择已发布职业→成功→刷新保持，已有经验不增加；目录在提交前被停用应404且不改原选择。跳过引导明确清除主职业，刷新确认经验/隐私仍保持；再选择能恢复。
4. 六预设逐一保存/刷新；LEVEL_ONLY和PRIMARY_ONLY按现有相同映射。CUSTOM完整十模块分别选PUBLIC/LOGGED_IN/SELF/HIDDEN，保存后重读一致；缺项/非法值422且原隐私不变。分页或保存主职业不得丢失尚未提交的隐私草稿。实际公开页另测相应读者权限，不用本人的私有状态读来证明隐私已生效。
5. 本人已有进度→查看详情：焦点/滚动到标题且顶栏不遮挡，展示关联历史路线、贡献与90天记录，含负数与经验/等级前后值；退休进度可读，无进度404不造记录。超过20条分页，末页缩小时读实际末页。切换目标时挂起旧请求，不得混入另一职业；服务503不显示假空汇总。
6. 用测试账号模拟写已提交但响应丢失，其他写禁用，同键同命令恢复一次；修改草稿也不能改变待恢复的CUSTOM快照。成功写后GET失败保持成功提示，只重读；旧key回放后读取当前最新选择/隐私。切换账号/退出/离页期间的读写结果不带入新身份，不留下私有记录。
7. PC/移动设备完整走读→改→保存→刷新→异常恢复。无Bearer、仅Cookie/apiKey、缺profile.read/settings.write、失效凭据和停用账号拒绝，不能指定他人身份。真实新IP/test、Before黑名单、验证码解封、CSRF/Bearer、批量请求与全CDN版本/sourceMappingURL/目录枚举/上传下载返回路径仍需完整应用回归。

## 安全与反漏扫复核

无新增匿名路由、anonymous.viewSkips、后台或上传下载路径，现有Bearer/scope及全局黑名单/匿名验证保持。客户端不接受用户ID或把Bearer传旧apiKey；服务端权限仍为最终依据。响应仅字段白名单、ID数字、枚举/长度/页大小有界，Vue文本展示，无v-html、动态SQL或远程抓取。目录按页查询，不无界遍历；写入同键且没有无限自动重试。

静态生成JS定向扫描零命中，无新增公网CDN URL。真实后端/权限撤销时序、完整IoC、移动设备、旧隐私写竞争、实际公开隐私效果、全局风控及全站CDN/目录/上传下载反漏扫尚未验；本地夹具和专项不能替代这些证据。职业排行、成长提示、他人公开资料以及其他v3模块继续保留在后续范围。
