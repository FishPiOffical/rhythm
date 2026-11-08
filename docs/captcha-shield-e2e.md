# 验证码盾自动化验收工具

工具按阶段开发。第一阶段只保存配置，不访问服务、不验证账号、不执行压测。

## 阶段 1

```powershell
node tools/captcha-shield-e2e.mjs configure
```

向导会：

- 读取 Windows 当前网卡的可用地址；选择 1 个回环地址 L 和严格 5 个额外地址 A-E。
- 保存 N 个测试账号的用户名、权限组和密码。密码使用当前 Windows 用户的 DPAPI 加密，配置保存在 `%LOCALAPPDATA%\\RhythmCaptchaShieldE2E\\config.json`，不会写入仓库。
- 只要求三项人工确认：环境、账号、IP。勾选内容完全信任，不额外请求服务检查。

查看已保存的非敏感配置：

```powershell
node tools/captcha-shield-e2e.mjs show
```

只查看网卡地址：

```powershell
node tools/captcha-shield-e2e.mjs ips
```

阶段 1 不读取或保存 `jiyan.key`，也不会启动浏览器。后续阶段在阶段 1 验收通过后继续开发。
