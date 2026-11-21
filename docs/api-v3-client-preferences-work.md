# website_v3 功能、显示、隐私与语言偏好迁移（2026-10-25）

客户端 /Users/ling/Documents/fishpi/website_v3。本批调用既有functions/display/privacy/location/locale及两个目录接口，无后端契约、SQL、路由修改，260路保持。

## 实际文件

- 新增客户端src/api/v3-preferences.ts：五资源明确白名单及类型，严格GET/PUT DTO；布尔不从0/1猜测，错误不填默认公开值。PUT只接受当前契约键/类型/范围，带幂等key；语言时区用真实languages/timezones，皮肤按pc/mobile目录分组，只投影id和名称，不加载预览URL。
- 新增客户端src/components/settings/OwnerPreferences.vue：共用透明表单，字段语义来自既有v3资源。读取完成前不渲染默认开关，失败可重读且不能保存。只发送改动字段，显示模式与站点皮肤虽然共用display，也不会互相覆盖。各资源独立状态与key；未知结果同内容重试同key，改回原值仍提交该未确认字段，避免误报无改动。身份变化清数据/key/选项并废弃旧读写。明确无更改时不发请求；me刷新失败不反转已保存结论。SCSS源经构建，不套卡片；显式aria-label便于辅助技术识别下拉框。
- 客户端src/views/SettingsView.vue：功能页接functions与display两个模式字段，表情组件保持；隐私页接13开关与独立publicLocation；语言页接locale目录/保存；个性化保留本机AppearancePicker，另加经典站点桌面/移动皮肤。删除相应旧apiKey静默return方法、硬编码语言时区目录及从me猜默认偏好的初始化。原父样式未改生成CSS。
- 新增客户端tools/v3-preferences-test.mjs；客户端README/AGENTS/V3_MIGRATION及后端进度、本文同步。

旧UI“聊天室显示图片”实际对应chatRoomPictureStatus=0时blur；核对PC/mobile与chat-room模板后，新文案准确写“模糊处理聊天室图片”，不反转现有值。classic皮肤设置只控制经典页面，不假称改变新Vue本机主题；locale只保存站点偏好，不声称本批完成全站翻译或所有时间格式应用。

## 验证证据

- tools/v3-preferences-test.mjs 7组exit0，/tmp/rhythm-v3-client-preferences-tests.log：资源/类型白名单、失败非默认；partial PUT/边界/布尔；目录ID与设备；实际Vue/Pinia晚到旧账号读、无更改不写、未知ACK同key；未确认字段改回仍提交；display子集不覆盖皮肤/模式，读错禁保存；晚到旧账号写不覆盖新账号或假报新账号成功。网络、认证为显式测试端口，不是Java端到端。
- corepack yarn run build类型/client/SSR通过，/tmp/rhythm-v3-client-preferences-build.log；git diff --check通过；生成62个JS版本/map定向扫描0命中，/tmp/rhythm-v3-client-preferences-js-audit.json。没有Maven或真实后端启动。
- 实际IAB仅独立本地HTTP/WS夹具：functions改listPageSize/chatImageBlur，display改commentViewMode，privacy改articles，location改publicLocation，locale改language/timezone，display改skin/mobileSkin。夹具日志只见上述改动键；返回功能页保留40/模糊开启/实时评论，未被改皮肤覆盖。站外跳转模拟422显示错误，重读仍原地址，再保存恢复。最初下拉框隐式label包含选项文本，已加显式名称并重测选择成功；Hydration日志空。
- 截图/tmp/rhythm-v3-client-preferences-proof.png；无生产写入。临时页面/服务清理。夹具只证明客户端调用与交互，不证明真实隐私筛选、跳转验证或皮肤呈现。

## 完整人工链

1. 独立开发库完成已有迁移，用户先通过IDEA重编译/重启并确认；客户端指向该服务，启动127.0.0.1:5173。本批没有新Java，但不能拿未确认更新的旧服务验新链路。
2. 登录/settings/function，真实读取每页条数/开关/首页跳转→修改一两项保存→刷新保持。抓包只包含所改键，10..96整数、布尔和站内跳转规则仍后端最终校验；站外/伪装域名/超长/小数拒绝且原值保持。无更改不发PUT。读错不显示假默认/不允许保存。
3. 独立改评论/头像显示模式，保存只改这两字段；去/settings/system选择当前目录的pc/mobile皮肤→保存→回功能页模式保持。经典桌面/移动页面验证对应皮肤真实生效；本机主题/外观包行为保持。失效皮肤、设备不匹配拒绝；已有未知皮肤可显示但不把默认值覆盖保存。
4. /settings/privacy切换13项中的一项并保存→刷新保持→独立切换位置公开再保存。两测试账号核对公开列表、排行/在线/UA等当前服务端隐私效果，隐藏与恢复不影响其他项。此处只改公开开关，不请求定位权限或虚构当前城市。
5. /settings/i18n使用服务器目录选择语言/时区→保存→重读保持；非法语言/时区/目录读取失败拒绝保存。站点实际语言与时间呈现另验，不把偏好持久化当作整站翻译完成。
6. 断网/丢响应重试同内容key不变；同一未确认字段改回原值仍发明确设置，不误报无改动。部分资源成功、另一资源失败分别显示结果。退出/切换账号/令牌刷新期间延迟旧读写不得回填或假报当前账号成功，不能用旧apiKey代替Bearer。
7. 未登录、缺scope/越权/停用/CSRF、真实黑名单/test及验证码解封、预算/批量、桌面与真实移动、SSR冷启动、静态/CDN/目录与上传下载路径执行全链检查。当前未做真实后端或设备运行验收。

## 安全与反漏扫

无新增匿名入口、anonymous.viewSkips或Before/Anonymous/Captcha链修改；已有GET要求profile.read、PUT要求settings.write。本人资源无指定用户ID；对象仅白名单，Vue文本绑定无v-html，目录仅作为选项、不当作URL抓取。无新SQL/服务端远程请求/上传下载路径；首页跳转仍走既有服务端精确校验，本批夹具拒绝不代表实际加固已验收。

每次操作只读当前资源及必要目录，不遍历全站；部分PUT减少覆盖面，原服务端幂等/预算仍负责并发及滥用。共享transport只在本地v3路径带内存Bearer，无新增凭据URL/持久存储。定向生成JS审计通过；全CDN、目录枚举、真实上传下载返回路径和黑名单/验证码、Cookie/apiKey/CSRF边界仍未运行验证。UI生效不等于这些安全门全部通过。

账号敏感设置、职业/勋章/会员完整客户端、文章评论写入/互动、私信/聊天室等仍待迁移；QQ/标签规则与后端云存档/风险红包兼容问题保持待答，不据本批缩小完整v3目标。
