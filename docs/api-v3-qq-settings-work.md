# v3 本人私有 QQ 设置（2026-10-25）

用户已明确纳入QQ与个人标签，并确认严格规则；本批QQ源码/客户端闭环完成；随后个人标签也已完成源码及隔离验证，见[标签工作记录](api-v3-profile-tags-work.md)。真实应用运行仍待验收。

## 实际文件与行为

- 新增service/ApiV3QqSettingsService.java：只允许当前第一方SQL授权，GET需profile.read、PUT需settings.write；namespace/client/user/token/epoch沿ApiV3SecurityProofService当前规则。QQ去首尾空白、空清除、1..12 ASCII数字、保留前导0；不用Latke浮点isNumeric。只改userQQ/更新时间，值和settings.qq回执同严格根；同键重试不覆盖后来值，重放也重验当前授权。读取保留已存旧值，不能伪装旧异常值已清空；保存一律新规则。提交后刷新/失效缓存不反转已完成结果。
- 新增processor/ApiV3ProfileSettingsProcessor.java并在ApiV3Processor总register接线，GET/PUT /api/v3/me/settings/qq；Bearer及scope门禁、PUT写预算/key/严格JSON，无用户ID输入。敏感传输日志过滤器必须已安装。没有扩展原profile字段、公开DTO或第三方资料读取，无新表/SQL。
- util/ApiV3OAuthTransportLogFilter.java增加QQ路径及同样百分号解码保护，避免处理器之前的解析异常带出QQ。未改旧QQ验证/旧资料提交。
- 新增测试service/ApiV3QqSettingsTest.java、tools/api-v3-qq-settings-test.mjs，修改util/ApiV3OAuthTransportLogTest.java；QQ runner现委托profile-tags runner的--qq-only编译共用控制器，复用实际私有工作流SQL/JDBC底座，边界见下。
- 客户端 /Users/ling/Documents/fishpi/website_v3/src/api/v3-profile.ts增加QQ独立GET/PUT；OwnerProfileSettings.vue增加独立QQ表单和状态/key，仅本人GET初始化；不发到普通profile或me。凭据边界同步清QQ/结果，旧响应不回写。tools/v3-profile-test.mjs追加私有资源、清除/前导0、非法值与换号测试。
- API.md/UPDATE_LOG.md、AGENTS.md、路由清单/进度、客户端README/AGENTS/V3_MIGRATION与本文同步。总262（261HTTP+1WS）：GET130/POST63/PUT44/DELETE24/WS1，全仓机械清单809路由/398浏览器引用，口径不同。

## 已执行

Java25隔离QQ专项5组exit0，/tmp/rhythm-v3-qq-settings-tests.log：纯数字/清除/边界/隐藏userId；真实SQL第一方当前授权、第三方同scope仍403、撤销后读/重放拒绝；设置/回执及提交故障原子回滚；丢ACK重放一次；真实Dispatcher未登录/错误scope/缺key/额外字段/私有no-store；普通和编码路径日志过滤。SQLite适配去掉FOR UPDATE，未证明MySQL行锁或多个实例。

客户端专项现6组通过、corepack yarn run build类型/client/SSR通过，/tmp/rhythm-v3-client-profile-tests.log、profile-build.log。实际IAB本地合成HTTP/WS夹具：读取00123→保存000456→重读保留前导0→1e3前端拒绝→留空清除成功，Hydration日志空；/tmp/rhythm-v3-client-qq-proof.png。无真实QQ/账号数据或生产写入。生成62个JS审计零命中，/tmp/rhythm-v3-client-qq-js-audit.json；git diff --check通过。

## 人工完整链

1. 用户先在IDEA重新编译/重启并明确确认；本批无需新增SQL，但已有v3凭据/回执迁移必须已部署。客户端指向开发服务，启动127.0.0.1:5173。
2. 第一方本人登录→/settings读取QQ→输入含前导0的合法数字→保存→刷新保持；单独保存资料/MBTI不改QQ。去首尾空白、空清除、1和12位边界，13位/浮点/科学计数/全角数字/HTML均422且旧值不变。旧异常值如NaN可读取，但不能原样写回，须修正或清除。
3. 两个测试账号各保存不同值，只能读取本人；无Bearer、仅Cookie/apiKey、第三方同scope、缺scope、停用/撤销/旧epoch拒绝，不出完整用户/凭据。验证公开用户资料、既有第三方profile/me均无新增QQ。
4. 同key同内容重试只一回执；后来另key改值后重放旧key返回原结果而不改当前值；同key异内容409。故障注入覆盖用户行、回执、commit，失败不留下半提交；丢ACK后回放仍一次。换号/退出延迟旧GET/PUT不得把QQ或结果带入新账号。
5. 确认QQ响应no-store/Vary Authorization，不落浏览器localStorage/URL、未进日志；用专用测试值验证Malformed/编码URI的传输日志过滤，不能拿真实QQ试日志。PC/移动经典旧页未新增QQ输入，移动新客户端表单需实机验收。
6. 实际新IP/黑名单/test、验证码解封、限额/CSRF/批量请求、CDN版本/map/目录枚举、上传下载返回路径仍需全链验收。无完整应用启动/MySQL并发/真实设备证据；Java隔离与夹具不是这些门禁通过证明。

## 安全与反漏扫

新增两路明确要求Bearer，业务再当前SQL限制第一方本人，无anonymous.viewSkips变化，不接受指定userId；保留全局Before黑名单与原匿名验证链，Cookie/apiKey不能冒充私有身份。严格字符串长度/数字，JSON/Vue文本绑定，无HTML执行、动态SQL拼值、服务端抓取或新上传下载路径。原写预算及幂等；QQ不放入公开资料，内部回执仅在当前第一方本人校验后投影。

传输错误过滤新增QQ路径并有编码测试；完整真实日志/代理环境仍未验。生成JS定向扫描通过；全CDN、目录与上传下载路径/全局验证码仍未运行验收。本批未触碰这些路径，不能默认全站反漏扫通过。

## 个人标签原规则记录（现已实现）

独立GET/PUT /api/v3/me/settings/tags；字符串、中英文逗号/顿号/分号、移除空白、旧规范化/忽略大小写去重、最多4项/普通12 UTF-16、现有白名单例外、原始/结果128、空清除、普通用户禁保留词、现有内容审核、非法/超限422；标签/本人关联/统计/设置/回执须同根。不能复用旧Tag.formatTags静默丢弃/截4项作为严格校验，不能仅写userTags。旧保存还会创建标签与creator/type2关联，需要保留；缓存必须提交后刷新，旧写未共享新锁时不能宣称新旧全局唯一。Tag.normalize含有效图标标签子串匹配、显式别名及现存标题/URI规范化，需完整反查而不是只做逗号split。
