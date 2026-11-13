# 验证码盾验收工具

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[完整工具说明](../captcha-shield-e2e.md)、[受保护范围](../captcha-shield.md)、[核心与爬虫](captcha-crawlers.md)。

## 账号、地址与真实验证边界

- 验证码盾验收进度在 `.codex-tasks/captcha-shield-e2e`；固定L加5个源IP、DPAPI密码，不读写`jiyan.key`。每次浏览器测试先用两个独立localhost窗口登录baseline/admin后关闭，恢复业务账号IP；不清封禁/加白。监控/自检服务端仅允许`adlered`独立L会话；禁止为跑通工具放宽权限。
- 验收菜单8通过 `tools/captcha-shield-suite.mjs` 组合模块并复用选择，菜单7固定L/A/B；日志须匹配本轮事件及统计，解封D按服务端实际IP格式操作；`results.json` 保留结果，失败保留浏览器，隔离HTTP夹具通过不能当真实极验或Linux ipset通过。
- 验证码盾验收统一PRODUCTION，不开发DEVELOPMENT写入测试；可选`pressure`只用E真实绑定HTTP（不带Cookie、不跟跳转），按服务端固定分钟验证匿名阈值及429，监控独立L/adlered；应用封禁保留，Windows结果不证明ipset成功。
- 验收工具启动前按当前网卡修复过期/重复IP：保留有效来源标签，仅缺位选IPv4→原生IPv6→Teredo，用户确认后保存，账号密文复用；运行中绑定失败不得换源重试，不能把未发出的请求算防火墙通过或失败。
- 验收菜单7/8经`captcha-shield-report.mjs`保存逐项JPG及离线PDF/HTML；用独立Chrome后台页打印，不替换业务页、不补发E流量。截图失败必须暴露；失败保留现场，导出状态与业务结果分列，禁止补造历史截图或把隔离夹具当真实验收。
