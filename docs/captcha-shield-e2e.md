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

## 阶段 2：ChromeDriver 浏览器流程

阶段 1 验收通过后，需要使用与本机 Chrome 主版本匹配的 `chromedriver.exe`。可以通过菜单“4. 设置 ChromeDriver 路径”保存路径，也可以设置环境变量 `CHROMEDRIVER_PATH` 或把驱动放入 `PATH`。工具不会静默使用不匹配的版本。

在项目根目录运行：

```powershell
.\tools\captcha-shield-e2e.cmd browser
```

或在菜单中先选择“4. 设置 ChromeDriver 路径”，再选择“6. 阶段 2：浏览器流程”。菜单“5. 打开配置文件编辑”会用记事本打开 `%LOCALAPPDATA%\RhythmCaptchaShieldE2E\config.json`；编辑时必须保留 `passwordProtected`、`ipPlan` 和账号字段结构。

工具会：

- 为 L、A、B、C、D、E 分别建立独立的 Chrome 用户目录；目录位于 `%LOCALAPPDATA%\RhythmCaptchaShieldE2E\profiles`，用于保留每个源地址自己的 Cookie，不把账号密码写入日志。
- 依次打开对应地址的首页。IPv6 地址自动使用 `http://[地址]:端口/` 格式。
- 使用 ChromeDriver 的 `eager` 页面加载策略，先等待 DOM 可用；对仍在后台下载的图片等非关键资源只记录提示、不把整页测试卡死。读取浏览器当前 URL；如果服务把请求重定向到 `/test`，脚本会暂停并提示你在刚打开的 Chrome 窗口中手动完成极验。脚本不会模拟拖动、伪造 `/validateCaptcha` 响应或把未完成的验证当作成功。
- 你按 Enter 后，脚本再次读取当前 URL；仍停留在 `/test` 会明确失败，离开后才继续下一个源地址。

阶段 2 只验证“真实浏览器能打开、验证码页面能人工接管、完成后能继续”这条基础链路，不代替后续的 `/admin/misc` 日志核对、每 5 次访问、黑名单和压测阶段。若未配置 ChromeDriver，运行会直接报错并给出安装或 `CHROMEDRIVER_PATH` 提示，不会伪造通过结果。

## 第一阶段完整业务流程

阶段 2 基础流程通过后，菜单选择“7. 第一阶段完整业务流程”，或运行：

```powershell
.\tools\captcha-shield-e2e.cmd flow
```

脚本会使用已保存的两个账号和 L/A/B 三个源地址执行：

- 每次完整流程开始都会为 L/A/B 建立全新的临时 Chrome 用户目录，并清除 Cookie、localStorage 和 sessionStorage，避免上一次运行的登录态跳过登录页；同一轮流程内仍会保留各自会话，用于验证源地址切换。流程结束后会尝试删除临时目录。
- L 地址登录基线账号，访问主页 10 次和 10 个不同文章页，确认没有意外进入 `/test`。
- A 地址首访必须进入 `/test`；人工完成极验后，继续访问主页和文章页，要求在 5 次内再次进入 `/test`。
- A 地址再次完成极验后登录管理员账号，重复访问确认不再跳转 `/test`。
- B 地址完成首访验证并登录管理员账号，再返回 A 地址，确认 A 再次进入 `/test`。
- 管理员会话在各业务小节访问 `/admin/misc`，确认社区安全面板和监控记录可读；脚本只读取页面，不修改开关。

文章地址从 L 地址首页实际读取；不足 10 个不同文章链接时直接失败，不伪造文章地址。所有预期的 `/test` 均会暂停等待人工极验，脚本不会模拟或伪造验证结果。
