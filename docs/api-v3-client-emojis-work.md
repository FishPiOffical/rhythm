# website_v3 表情迁移（2026-10-25）

客户端 /Users/ling/Documents/fishpi/website_v3；本批调用已有后端接口，无新增/修改路由，260条不变。认证审核结论规则及后端变更另见api-v3-identity-application-work.md。

## 实际文件与行为

以下源码相对客户端目录：

- 新增src/api/v3-emojis.ts：真实常用别名/目录、分组/成员分页与写入适配；严格信封/字段，私有member id用于移除，emojiId不能混用。保留完整URL查询参数，读取旧合法HTTP(S)图片但新写由后端HTTPS约束；拒危险协议/凭据及控制符，错误不伪造成功。写入生成随机Idempotency-Key；仅共享transport向本地v3资源发内存Bearer。
- src/api/fishpi.ts：旧签名转发上述helper，忽略legacy apiKey；移除mockEmotions及旧表情读写回退；Markdown别名按服务器名称，图片alt转义且用角括号包裹完整URL保留括号及签名。未把其他旧接口改成Bearer，也未完成Vditor旧hint/上传/发送迁移。
- src/components/EmojiPicker.vue：真实常用/图片目录、更多分组/图片，加载错误可重试；identity/token变化或卸载废弃旧读，关闭清理；选中插入规范别名或图片Markdown，损坏成员不伪装成别名。
- src/components/EmojiPacks.vue：常用设置/默认恢复、自定义组新建删除、图片添加移除、真实分页；失效组404移出视图；同内容结果未知的操作重试保留key，当前凭据变化清空；读写按版本/身份防旧响应。添加sort=0让服务端分配；“全部”不可删除。样式源码使用SCSS经构建，不再嵌套卡片。
- src/views/SettingsView.vue：mounted后再展示认证分支，保持SSR首屏一致。父页面其余旧功能设置不因此完成迁移。
- 新增tools/v3-emojis-test.mjs：实际TS/Vue/Pinia逻辑，显式网络适配，六组验证响应/调用、真实私有id、签名URL、无mock、旧账号延迟读、未知结果同键重试及Picker插入/身份失效。
- README.md、AGENTS.md、docs/V3_MIGRATION.md及后端docs/api-v3-progress.md、本文记录实际边界。

## 执行证据

- 专项6组退出0：/tmp/rhythm-v3-client-emojis-tests.log；通知6组与认证12组回归日志/tmp/rhythm-v3-client-emojis-notices.log、/tmp/rhythm-v3-client-emojis-auth.log。网络和服务端为显式测试替身，不是Java端到端。
- corepack yarn run build包含vue-tsc和client/SSR构建，通过；/tmp/rhythm-v3-client-emojis-build.log。无Maven，无手改生成CSS/min.js。
- js-version-audit扫描生成assets 62个JS、0命中，报告/tmp/rhythm-v3-client-emojis-js-audit.json；git diff --check通过。仅当前构建目录，不宣称整个CDN/旧编辑器无版本或map。
- 实际IAB在127.0.0.1:5173对本地合成HTTP/WS服务完成：51分组分页、新建组、带查询参数URL添加、移除成员、删除自定义组、默认恢复、重复别名拒绝；实际文章页编辑器选择smile插入正文，未发送评论。第一次SettingsView出现水合警告，修复后独立新页Hydration日志空。截图/tmp/rhythm-v3-client-emojis-proof.png。images.example为无效示例，未作为图片可访问证明；初始本地像素图片正常。临时服务和页已清理，无生产写入。

## 安全与反漏扫

本批无新后端入口、anonymous.viewSkips、BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor变更；已有表情接口须Bearer及对应profile.read/settings.write，页面Cookie/旧apiKey不代替。当前浏览器验证用夹具，真实黑名单/test、首访/每5次验证码与解封没有运行验收。

输入名称作为Vue文本，Markdown图片alt转义、协议过滤；新URL服务端校验且不抓取，客户端图片直接加载不会向图片站带Bearer。API id采用十进制编码，分页显式边界；无动态SQL/服务端远程抓取新增。批量请求按页加载、不自动遍历所有页；写入仍受原后端预算与幂等。原始响应不裸写HTML，不把私有凭据拼到URL或持久存储。旧编辑器渲染/上传及真实限额、跨账号数据库越权/CSRF仍需实际后端验收，不能以客户端版本判断代替后端鉴权。

静态assets版本/map本批定向扫描通过；CDN内容、服务器目录枚举、上传下载返回路径和反向代理配置未实际复核。没有新上传/公开下载路径；添加图片URL不代表安全图床托管或资源归属证明。

## 完整人工链

1. 独立开发库执行已准备的迁移，用户先通过IDEA重新编译/重启并明确确认；客户端配置该开发后端，启动127.0.0.1:5173。未确认前不要把旧服务表现当新代码故障。
2. 已登录进入/settings/function表情区，加载实际常用名称和分组；目录选择有效名称按顺序保存，刷新/重登仍一致；留空或恢复默认取得服务端默认。重复、空白项、未知名称422且旧值保持；断网显示错误而非默认假成功。
3. 新建中文/emoji自定义组→添加真实HTTPS带签名query图片→刷新仍同URL/成员；添加至自定义组同步出现于全部。移除自定义成员不删全部，从全部移除同步移出本人各组；删除自定义组保留其他组和全局图片，全部不可删除。覆盖64长度/HTML/控制字符名称、HTTP新写、非法URL和512边界。
4. 准备超过50分组及超过50成员，更多逐页加载不遗漏或错删；用private member id而非emojiId移除。无任何组时首次添加自动建立全部。另一端已删组404需报错并移出旧组。
5. 从文章/聊天编辑器打开共享选择器，常用名称插入正确别名，图片插入完整签名URL；关闭重开和切组期间延迟响应不串组；未确认当前写链已迁移前不要以插入成功宣称发送成功。
6. 断网/丢响应后重试同操作仅产生一份结果；换账号/退出/刷新令牌时清空旧列表、输入、key，延迟旧响应不得复活或链式读取他人组。无Bearer及权限不足拒绝；抓包确认外部图片无认证头、apiKey不在URL、无公开材料路径。
7. 桌面/窄屏/真实移动设备验证布局、键盘操作与SSR冷启动无水合警告；复核黑名单/test、验证码与限额/目录配置。以上真实服务和设备尚未完成。

## 剩余范围

此处为首批状态；分组/成员改名排序及分享/导入/旧云迁移UI已在下节补全。Vditor原生hint/上传/实际发送，以及其余设置/私信/聊天室等模块仍未完成客户端迁移。后端云存档及风险红包兼容规则仍等待原待答问题。

## 第二批：编辑、分享与迁移补全

实际追加文件相对客户端目录：src/api/v3-emojis.ts提供PUT成员元数据、POST分享/导入/迁移的严格DTO；src/components/EmojiPacks.vue补自定义组及成员编辑表单、分享码展示、导入表单与本人旧云迁移按钮。输入名称64、sort有符号32位整数，具体规则仍由已有服务端校验；不改URL、不传全局emojiId代替itemId。分享码输入8..16字母数字规范大写，生成结果接受12位。不自动广播/发消息/写剪贴板，凭据变化清临时码、编辑和结果状态。写入经统一mutate保留未知结果key。SCSS增加顶层按钮选择器，经构建生成。

导入/迁移响应不含完整分组资料；客户端先读第一页，再先最后页、必要时其余页定位真实id，以实际name/type/sort恢复选择，身份变化中止后续请求。超过100页明确报错；已提交但分组已删提示刷新，不发明假sort=0。目标查回不影响已提交业务/回执。分享只给非空自定义组入口，服务端仍最终校验；来源组删除后快照仍可导入。迁移没有任意用户ID、cloud键或JSON上传入口。

tools/v3-emojis-test.mjs现9组通过，新增精确HTTP字段/协议、非法码不发请求、坏计数拒绝、实际Vue编辑/导入未知ACK同键重试/凭据清理、最后页目标真实sort=51。日志/tmp/rhythm-v3-client-emoji-transfer-tests.log；corepack yarn run build类型/客户端/SSR通过，/tmp/rhythm-v3-client-emoji-transfer-build.log。生成JS审计/tmp/rhythm-v3-client-emoji-transfer-js-audit.json，不代表完整CDN扫描。本文及进度、客户端README/AGENTS/V3_MIGRATION同步。

实际IAB本地HTTP/WS夹具验证组名称/排序、成员名称/排序、生成码→删来源组→小写码导入新组、再次导入读取最后页真实sort=52、迁移计数。全程合成数据，无生产写入。Hydration日志空。images.example图片不能加载属示例，不证明真实图床可用；本地像素图正常。截图/tmp/rhythm-v3-client-emoji-transfer-proof.png。

追加人工链：先完成IDEA重编译/重启确认与开发库迁移；登录/settings/function→非空自定义组→改名称/排序→刷新保持→改成员名称（可空）与排序，URL不变；全部组不可改删。生成码→另一测试账号导入独立组→删来源后继续可导入。同键未知ACK不重复组，新操作可建独立组。超过50组导入后显示实际排序；已删目标的回执重放报告不可用。本人旧云迁移→处理/新增数准确→重复不重复图片；混入HTTP/非法URL/禁用图片/非法名/JSON整批422且无写入，空历史409。无权限/越权/停用拒绝；断网、换号及旧响应不得保留他人码或继续请求。桌面/移动与真实黑名单/验证码/目录/上传下载路径另行验收。

本批无新后端路由或anonymous.viewSkips/全局盾变更，无新SQL、服务端抓取或公开材料路径；沿前节安全与反漏扫边界。真实Rhythm、数据库回滚并发、移动设备、CDN/目录配置未验。
