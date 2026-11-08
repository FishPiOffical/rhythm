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

阶段 1 验收通过后，先安装与本机 Chrome 主版本匹配的 `chromedriver.exe`，并确保它在 `PATH` 中；也可以设置环境变量 `CHROMEDRIVER_PATH` 指向驱动文件。工具不会自动下载驱动，也不会静默使用不匹配的版本。

在项目根目录运行：

```powershell
.\tools\captcha-shield-e2e.cmd browser
```

或在菜单选择“4. 阶段 2：浏览器流程”。工具会：

- 为 L、A、B、C、D、E 分别建立独立的 Chrome 用户目录；目录位于 `%LOCALAPPDATA%\RhythmCaptchaShieldE2E\profiles`，用于保留每个源地址自己的 Cookie，不把账号密码写入日志。
- 依次打开对应地址的首页。IPv6 地址自动使用 `http://[地址]:端口/` 格式。
- 读取浏览器当前 URL；如果服务把请求重定向到 `/test`，脚本会暂停并提示你在刚打开的 Chrome 窗口中手动完成极验。脚本不会模拟拖动、伪造 `/validateCaptcha` 响应或把未完成的验证当作成功。
- 你按 Enter 后，脚本再次读取当前 URL；仍停留在 `/test` 会明确失败，离开后才继续下一个源地址。

阶段 2 只验证“真实浏览器能打开、验证码页面能人工接管、完成后能继续”这条基础链路，不代替后续的 `/admin/misc` 日志核对、每 5 次访问、黑名单和压测阶段。当前机器若未安装 ChromeDriver，运行会直接报错并给出安装或 `CHROMEDRIVER_PATH` 提示，不会伪造通过结果。
