# v3 客户端假数据退役与边界复核（2026-10-28）

第108批，290入口及外部契约保持，无Java/FTL/SQL/Repository/Maven/APP。本轮用户确认已重编重启与迁移，但本执行环境连接IPv4/IPv6 localhost:8080均拒绝且无Java监听；用户随后选择先不做真实后端联调，本批只报告源码/隔离结果，不再等待地址或把连接拒绝当接口失败。

## 实际修改文件

website_v3：

- `src/api/content-types.ts`：移入唯一仍用的DomainItem类型，不依赖假数据模块。
- `src/api/fishpi.ts`：类型改为纯type import；六当前helper和共享类型保持。
- `src/api/catalog.ts`、`src/api/catalog.mock.json`、`src/api/gaps.mock.ts`：删除无运行调用方的假文章/目录/专栏及mock构造。当前页面专用v3数据读取保持。
- `src/api/http.ts`：删除已无调用方的withKey helper；离线pingApi从旧文章接口改用现有v3 captcha-options。仍只判断HTTP可达，4xx/5xx也视为收到响应，不签发凭据或代用户解验证码，不把此结果当业务健康。
- `scripts/tests/v3-auth-transport.mjs`：新增第13组，实际ping函数路径、无Bearer/无刷新、4xx/5xx可达、网络失败false及SSR无请求；均mock fetch。
- `tools/v3-inventory-test.mjs`：存量断言改为检查假数据文件已移除，背包原真实接口回归保持。
- `tools/v3-client-boundary-audit.mjs`：AST扫描src TS/Vue script的API字面量、退役静态导入和登录桥接字符串，动态请求表达式单列；识别RhyPic pathname.endsWith校验为服务路径，不误判成旧Rhythm API。
- `docs/api-v3-client-boundary-audit.json`：当前确定性审计结果，330源码文件、165 API字面量、47动态调用表达式、2服务路径校验、0发现。动态列表包含包装/回调，不能当作47个独立HTTP入口，也不能以静态0发现声明运行全覆盖。
- `README.md`、`docs/MISSING_APIS.md`、`AGENTS.md`、`docs/V3_MIGRATION.md`及本记录。

Rhythm：`AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、本记录及审计JSON镜像。API.md契约未变。

## 实际验证

80个tools脚本917组、独立认证transport13组通过；类型/client/SSR构建明确exit0；77生成JS版本审计0，无map/sourceMappingURL；git diff --check通过。边界审计最初找出旧ping路径和两个RhyPic校验，修正ping并按AST上下文分类后0发现；该结果只涵盖报告声明的静态范围。

IAB 1280×720实际生产构建+SSR合成HTTP：/offline?from=/login展示重连按钮，点击后仅一次/api/v3/auth/captcha-options（夹具503仍代表HTTP可达）并返回登录表单；夹具记录旧/api业务调用0。未填或提交账号凭据，临时页/服务关闭。这不等同真实后端在线或权限验收。

## 安全/反漏扫与限制

不新增匿名入口、anonymous.viewSkips、CSRF写边界或授权例外；原BeforeRequestHandler黑名单/AnonymousViewCheckMidware/CaptchaProcessor保持。重连GET仍走现有全局链，直接探测不自动解决验证码或登录。无新增SQL、HTML渲染、远程代理/SSRF或批量能力；删除假数据和旧apiKey构造helper，不改变v3令牌存储。无新增CDN、上传下载路径或目录，77JS版本0且无map。真实黑名单/新IP/验证码/解封/开发模式、MySQL/IoC/完整权限、生产目录枚举及390px未验。用户已选择暂缓真实联调；不把静态与夹具结果当作v3整体完成。

## 完整人工测试

1. 构建并启动website_v3，进入首页/领域/标签/文章/专栏，失败应明确报错重试，不能出现假帖子/专栏；类型迁移不应影响目录ID/名称/图标。
2. 打开/offline?from=/login，点重新连接；网络拒绝时保留错误，收到任意HTTP响应时回原登录页；记录只使用v3探测，不请求旧文章接口。这只是可达探测，不保证业务健康。
3. 正常登录/自动刷新/退出及内容与文件上传回归；RhyPic校验路径仍按独立服务协议，不能被静态审计误认为旧业务入口。真实凭据由用户输入。
4. 执行node tools/v3-client-boundary-audit.mjs --json /tmp/v3-client-boundary.json，复核报告的API字面量及动态表达式；动态项需结合实际调用链/测试人工审查，0发现不是完整授权证明。
5. 桌面及390px各验导航/目录和离线重连。用户恢复真实联调后再验新IP、黑名单、验证码、当前权限及外部上传；本批这些项目明确未执行。
