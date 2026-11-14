# 编辑器工具栏表情迁移（第六十四轮，2026-10-26）

## 实际文件与行为

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/components/MarkdownEditor.vue`：移除旧fetchVditorEmoji、按apiKey的共享缓存、旧设置链接与原生hint.emoji的HTTP数据。桌面/移动工具栏的表情键改为Vditor公开自定义菜单回调，打开已有v3 EmojiPicker；点击时才读本人常用与图片分组。其他Vditor本地冒号补全仍为其本地能力，不声称迁移所有Lute功能。
- 通过Vditor公开vditor/lute.GetEmojis读取本地Unicode显示标签，只保留短Unicode纯文本，不加载映射中的远程图片/HTML；只有服务端提供的常用别名成为菜单项，插入仍是该别名。缺本地Unicode的别名显示完整文本，未制造新常用项。
- 所有共享包装器的Markdown插入（表情、上传和对外insert）改用Vditor公开insertMD；前两轮insertValue内部先走template.innerHTML，尖括号被当HTML解析，才是地址截断的直接原因。普通目的地与括号转义继续保持；不能再把纯Markdown交insertValue。
- `src/components/EmojiPicker.vue`：暴露toggle，支持隐藏重复触发器及工具栏下方弹层、可选Unicode显示映射；别名文本提供足够网格宽度，图片显示沿原分组。身份用auth context/有无令牌/owner判断，普通token轮换保持，换账号或退出清内容/取消迟到结果；关闭/加载中不响应保留的旧选择按钮。样式改SCSS源码并build。
- `src/api/fishpi.ts`：删除无消费者的旧fetchVditorEmoji；图片Markdown对HTML字样/控制/方括号/反斜杠转义，目的地使用普通链接并转义括号，不再套尖括号。其他旧searchUsers仍待新联想接口后迁移，未删有消费者的旧方法。
- `src/api/v3-emojis.ts`：图片读取拒绝原始HTML边界字符/DEL，避免不安全地址进入选择器；保留合法历史HTTP/相对图片的既有展示范围与完整签名query，没有改后端接口或缩小新图片写规则。
- `tools/v3-editor-emojis-test.mjs`：新增7组，实际TS API、完整Editor+Picker Vue组件/公开expose、Pinia、轻量DOM；Vditor、上传/预览及HTTP为明确适配器。涵盖工具栏点击到真实picker、别名/图片插入、Unicode标签、below样式、失败文本/重试、正常轮换/换人/退出/旧按钮/SSR。
- `tools/v3-emojis-test.mjs`：更新生产Markdown目的地预期、脚本测试宏/身份适配器；保留原9组设置/分页/迁移/分享回归。
- `AGENTS.md`、`docs/V3_MIGRATION.md`：当前边界。dist/client/server重新生成，无直接改CSS/min.js。

后端本轮更新`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`AGENTS.md`、本记录及上一轮上传记录的根因澄清；无Java/FTL/SQL/API契约变化。总282保持，未提交推送。

## 实际验证

- 7组完整Editor/Picker专项：`/tmp/rhythm-v3-editor-emojis-tests.log`。
- 原表情9组：`/tmp/rhythm-v3-editor-emojis-regression.log`；上传9组：`/tmp/rhythm-v3-editor-emojis-upload.log`。
- 文章27/评论17/预览7/资料7回归：`/tmp/rhythm-v3-editor-emojis-{posts,comments,preview,profile}.log`。最后的Unicode显示与别名网格改动后已重跑表情两套；纯显示未重跑其余已通过且无源码关联变化的回归。
- 类型/client/server构建：`/tmp/rhythm-v3-editor-emojis-build.log`，成功。生成JS版本/map扫描见`/tmp/rhythm-v3-editor-emojis-js-audit.json`；两仓diff --check。
- IAB真实生产CDN Vditor+本地合成HTTP：桌面从表情工具栏→常用smile→图片分组→合成图片，图片naturalWidth=32；切到分屏源码，原文为一次`:smile:`和完整图片Markdown。源码模式实际文件选择器再上传合成PNG，Markdown目的地完整。没有发布/保存真实内容。
- 390×844移动视口实际工具栏打开、插入heart、再次打开，Unicode显示和弹层位置已目视；截图`/tmp/rhythm-v3-editor-emojis-mobile-proof.png`。图片为fixture返回的已有公开资源，不是本次上传持久化。
- 待真实服务验证：本人表情设置变化、权限/撤销、图片分页和失败、发布/保存后回读、所有Vditor编辑模式/触屏真机与焦点恢复、黑名单验证码链。

## 安全与反漏扫

- 本轮只消费既有profile.read的本人GET，未新增/改造匿名路由或anonymous.viewSkips；全局BeforeRequestHandler、/test和validateCaptcha未改。真实风控/权限未运行验收。
- 不使用Cookie/apiKey恢复本人数据，不把accessToken放URL或外部服务。SSR不读取本人表情，不输出编辑原文；按身份清面板并拒迟到结果。
- 别名/分组/错误均由Vue文本渲染，无服务端内容直接拼进原生Vditor HTML。固定toolbar SVG为本地代码；本地Unicode映射过滤远程图片/HTML，只用于显示。图片URL与Markdown标签有独立边界。
- 无SQL/Repository写入、无任意服务端抓取URL，不新增SSRF/注入/数据库事务；无上传/下载路径或CDN网址变化。GET惰性按需，分页沿现有上限，无跨用户共享缓存。
- 现有图像读取保持历史HTTP/相对地址范围；未承诺旧数据整体符合新写规则。没有改旧页面API行为。
- 整个未提交工作区的发布前全量审计未在本轮重跑，不能把本批资源扫描当作全项目安全验收。

## 完整人工测试

前端build后强刷；本轮无Java/FTL，不需为本轮改动重启Java。上一轮新增预览若尚未编译，仍须IDEA编译/重启。

1. 登录→`/post`→工具栏表情键；桌面/移动都能看到本人常用和图片分组，选择后在当前光标处插入，关闭面板。进入源码模式，确认别名只出现一次、图片URL完整；保存草稿/发布后回读保持原文。
2. 修改常用表情后重新打开，确认当前列表而非旧缓存；切图片组、翻页、清空组，检查成功/空结果。断网或503时只显示错误，不制造默认项；重试后恢复。
3. 面板读取中切换账号/退出/离开页面，恢复旧响应后不出现原账号内容；同账号正常token刷新保留面板；关闭后保留的旧按钮不得插入内容。
4. 图片名含括号/方括号/HTML字样及签名URL时，不注入HTML、不截断URL；共享上传继续可插入。测试SV/IR/WYSIWYG、键盘快捷键、触屏、焦点与发布后格式。
5. 真实服务验证缺profile.read/失效Bearer，以及黑名单→/test→validateCaptcha与开发关闭风控链；这些未以合成fixture代替。

## 下一步已确认规则

用户已确认新的@联想：需要profile.read；用户名忽略大小写前缀，1..64位ASCII英文字母/数字/下划线/连字符，最多10个当前有效用户；只返公开ID/用户名/昵称/头像。空前缀返回空列表，不继承旧空前缀管理员名单。尚未实现新查询与客户端联想，下一批按此规则继续；不要重复询问。标签联想/专栏选择及其他总清单仍待完成。
