# 验证码盾自动化验收工具

工具按阶段开发。第一阶段只保存配置，不访问服务、不验证账号、不执行压测。

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

- 读取 Windows 当前网卡的可用地址；自动选择 1 个回环地址 L，并从可用 IPv4 网卡地址中自动选择前 5 个额外地址 A-E，不再要求手工填写编号。若实际只有少于 5 个 IPv4 地址，工具会停止并明确列出已发现地址，不伪造 IP。
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

阶段 1 不读取或保存 `jiyan.key`，也不会启动浏览器。后续阶段在阶段 1 验收通过后继续开发。
