# 验证码盾自动化验收工具

工具按阶段开发。第一阶段只保存配置；第二阶段才打开真实浏览器并访问服务。

## 阶段 1

不需要记 Node 命令，直接双击项目里的以下文件即可：

```text
tools\captcha-shield-e2e.cmd
```

也可以在项目根目录运行 PowerShell 脚本：

```powershell
.\tools\captcha-shield-e2e.ps1
```

脚本会显示中文菜单。选择“1”进入配置向导，“2”查看已保存配置，“3”查看网卡 IP。

```powershell
node tools/captcha-shield-e2e.mjs configure
```

向导会：

- 读取 Windows 当前网卡的可用地址；自动选择 1 个回环地址 L，并自动选择 5 个额外地址 A-E：优先使用 IPv4，不足时补充可路由的全局 IPv6，不再要求手工填写编号。若 IPv4 加可用 IPv6 仍少于 5 个，工具会停止并明确列出已发现地址，不伪造 IP。后续浏览器和 HTTP 测试必须支持 IPv6 字面量方括号格式，且服务端监控必须看到不同源地址。
- 按当前测试流程固定保存 2 个账号：普通登录基线账号（例如 `adlered`）和管理员账号（必须属于 `admin/管理员` 分组）。密码会直接显示在输入行，但保存时使用当前 Windows 用户的 DPAPI 加密，配置保存在 `%LOCALAPPDATA%\\RhythmCaptchaShieldE2E\\config.json`，不会写入仓库。
- 只要求三项人工确认：环境、账号、IP。勾选内容完全信任，不额外请求服务检查。

如果已经保存过配置，进入配置向导时会先显示上次的端口、账号、权限组和 IP，并询问是否复用。选择 `y` 后不再重复填写账号或 IP，只重新进行三项确认。

查看已保存的非敏感配置：

```powershell
node tools/captcha-shield-e2e.mjs show
```

只查看网卡地址：

```powershell
node tools/captcha-shield-e2e.mjs ips
```

阶段 1 不读取或保存 `jiyan.key`，也不会启动浏览器。

## 阶段 2：浏览器基础流程

基础浏览器兼容入口仍需要与本机 Chrome 主版本匹配的 `chromedriver.exe`，可以通过菜单“4. 设置 ChromeDriver 路径”保存路径。第一阶段完整业务流程不再依赖 ChromeDriver，而是启动普通 Chrome 并通过 DevTools Protocol 控制。

在项目根目录运行：

```powershell
.\tools\captcha-shield-e2e.cmd browser
```

或在菜单中选择“6. 基础浏览器流程”进行 ChromeDriver 兼容检查。菜单“5. 打开配置文件编辑”会用记事本打开 `%LOCALAPPDATA%\RhythmCaptchaShieldE2E\config.json`；编辑时必须保留 `passwordProtected`、`ipPlan` 和账号字段结构。

工具会：

- 为 L、A、B、C、D、E 分别建立独立的 Chrome 用户目录；目录位于 `%LOCALAPPDATA%\RhythmCaptchaShieldE2E\profiles`，用于保留每个源地址自己的 Cookie，不把账号密码写入日志。
- 依次打开对应地址的首页。IPv6 地址自动使用 `http://[地址]:端口/` 格式。
- 基础流程使用 ChromeDriver 的 `eager` 页面加载策略；第一阶段业务流程使用普通 Chrome + DevTools Protocol，先等待 DOM 可用；对仍在后台下载的图片等非关键资源只记录提示、不把整页测试卡死。读取浏览器当前 URL；如果服务把请求重定向到 `/test`，脚本会暂停并提示你在刚打开的 Chrome 窗口中手动完成极验，完成后会自动检测页面跳转，不需要回终端按 Enter。脚本不会模拟拖动、伪造 `/validateCaptcha` 响应或把未完成的验证当作成功。
- 顶层页面访问之间至少间隔 1.5 秒，避免连续刷新触发 CC 或验证码盾；如果页面标题/正文出现 HTTP 503、`Service Unavailable` 或“服务不可用”，脚本立即失败并停止后续请求，不把 503 当成通过。
- 极验等待最长 180 秒；页面仍停留在 `/test` 会明确失败，离开后自动继续下一个步骤。
- 每次顶层页面切换都会输出简短证据：最终 URL、主文档 HTTP 状态、页面标题和正文前 80 个字符；状态来自 Chrome DevTools 的 `Network.responseReceived` 主文档事件，后台图片等静态资源不会覆盖主文档状态。证据只输出到终端，不记录账号密码。
- 导航完成以 Chrome 主框架实际导航事件为准，不会把上一个页面仍为 `interactive/complete` 的状态误当作本次导航完成。若页面内 API/资源请求被全局黑名单规则重定向至 `/test`、但浏览器顶层仍停留在原页面，脚本会识别重定向并在顶层打开验证码页；预期不应进验证码的步骤也会对此失败。

阶段 2 只验证兼容入口的基础链路，不代替第一阶段业务流程。若未配置 ChromeDriver，菜单 6 会直接报错；菜单 7 不需要 ChromeDriver。

## 第一阶段完整业务流程

菜单选择“7. 第一阶段完整业务流程”，或运行：

```powershell
.\tools\captcha-shield-e2e.cmd flow
```

脚本会启动不带 ChromeDriver 自动化标记的普通 Chrome，通过 DevTools Protocol 使用已保存的两个账号和 L/A/B 三个源地址执行：

- 每次完整流程开始都会为 L/A/B 建立全新的临时 Chrome 用户目录，并清除 Cookie、localStorage 和 sessionStorage，避免上一次运行的登录态跳过登录页；同一轮流程内仍会保留各自会话，用于验证源地址切换。流程结束后会尝试删除临时目录。
- L 地址登录基线账号，访问主页 10 次和 10 个不同文章页，确认没有意外进入 `/test`。
- A 地址首访必须进入 `/test`；人工完成极验后，继续访问主页和文章页，要求在 5 次内再次进入 `/test`。
- A 地址再次完成极验后登录管理员账号，重复访问确认不再跳转 `/test`。
- B 地址完成首访验证并登录管理员账号，再返回 A 地址，确认 A 再次进入 `/test`。
- B 登录后会关闭原 A 管理员会话并启动一个新的匿名 A 会话，避免已登录请求绕过匿名盾；B 会话负责最后一次 `/admin/misc` 监控核对。
- 管理员会话在各业务小节访问 `/admin/misc`，确认社区安全面板和监控记录可读；脚本只读取页面，不修改开关。

文章地址从 L 地址首页实际读取；不足 10 个不同文章链接时直接失败，不伪造文章地址。所有预期的 `/test` 均会暂停等待人工极验，脚本不会模拟或伪造验证结果。

当前版本先提供终端证据，尚未生成截图、PDF 或 Word。后续报告阶段会在不改变业务流程的前提下，为关键步骤保存截图和结构化结果，再据此生成图文报告。
