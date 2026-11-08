# 验证码盾维护与验收

## 职责

`AnonymousCaptchaShield` 集中防扫描验证码盾的进程内状态、规则、全局验证码黑名单跳转、`/test` 展示条件、极验服务端验证及验证成功后的清理。沿用静态进程状态，与既有 `Firewall` 模式一致；不新增数据库、后台线程或请求期磁盘检查。

`AnonymousViewCheckMidware` 仍负责原身份解析、特定路径登录要求、`anonymous.viewSkips`、文章匿名阅读权限和匿名阅读页数/Cookie。全局 CC 与 Linux ipset 仍由 `Firewall` 负责。图片验证码仍在 `CaptchaProcessor`，PC/mobile 极验模板未改。

注册、找回密码、旧联系方式设置与 v3 联系方式设置原本共用极验验证，现在直接调用 `AnonymousCaptchaShield.verifyProof`；接口参数和响应契约没有变化。原 `CaptchaProcessor.jiyan` 与原中间件公开状态成员已删除，调用方及既有复读机测试同步迁移，不保留双份状态或兼容缓存。

## 固定顺序与调用

| 位置 | 约束 |
| --- | --- |
| `BeforeRequestHandler.handle` | 缺 UA、爬虫识别、CC 在前，验证码黑名单在后，CORS/OPTIONS 再后；登录态不越过前面的黑名单 |
| `AnonymousViewCheckMidware.handle` | 进入时读开关快照，沿原方法解析身份和 articleId；验证码检查在登录要求、viewSkips 和阅读限制之前 |
| `CaptchaProcessor.register/captcha/validateCaptcha` | 原路由不变，展示及验证委托核心；验证码状态清理只能在验证成功后发生 |
| `AdminProcessor.modifyIp` | 系统解封后清验证码状态并加白；系统封禁前撤白；原管理员鉴权不变 |
| `AdminProcessor` 开关入口 | 保留原管理员鉴权，开关仍是临时进程状态 |
| `Server.main` | 开发模式仍关闭原三个开关；生产默认值不变 |
| `ApiV3RealtimeService.registry` | 仍按同一开关和验证码黑名单拒绝受限连接，CC 调用顺序不变 |

## 版本 1 行为基线

- 首访文章豁免仍只按原路径解析，不扩大到其他读取接口；每五次验证保持。
- 已有用户 IP、白名单、核实爬虫和四个公开信息页按原顺序豁免。
- API 触发验证码返回 401，不加入验证码黑名单且不做匿名累计封禁；页面加入验证码黑名单并跳转 `/test`。
- 验证成功仅清验证码黑名单和访问计数、记录通过时间；保留首次访问时间，不加白，不解除系统封禁。
- 管理员解封另外清首次访问时间并加白，白名单只在当前进程有效。
- 关闭验证码盾不清状态；全局不强制跳转，但已有验证码黑名单 IP 直接访问 `/test` 仍可看到验证页。
- 保留两小时 expireAfterWrite、缓存容量、300 次非 API 累计封禁及原分开读写计数。该提取没有修复原同 IP 并发计数覆盖，不宣称并发计数完全准确，也不提供跨实例状态一致性。
- 极验 POST、HMAC、五秒超时及解析/网络失败均返回 false 的语义保持；不记录证明材料或密钥。日志前缀保留 `[SHIELD]`，提取部分的 logger 名改为新类。

## 修改授权与指纹

未经阿达明确允许，不得修改、移动核心类或受保护调用，不得通过新增提前放行、删挂中间件绕过防护。每个生产调用旁均标注约束，项目 AGENTS 同步登记。

部署前从当前源码仓库执行：

```bash
python tools/check-captcha-shield.py
```

`tools/captcha-shield-lock.json` 保存策略版本及 LF 归一化 SHA-256。核心类与匿名中间件按完整文件锁定；其他调用按完整所属方法锁定，因此在方法内改动或移动调用、把调用移出原方法、删除调用都会失败。新增未登记的直接引用也失败。调用方法中修改其他业务逻辑同样需要复核该指纹，不自动更新基线。

这是源码变更检测，不是文件写保护、签名发布或权限审批系统；源码、清单与检查器能被一起修改。注释不能验证谁批准修改，工具不自动安装 Git 钩子或改现有发布脚本，不执行就没有发布阻断。通过只说明登记源码符合本次基线，不能证明部署包来自这份源码，也不能证明全部路由挂载或生产外部服务正常。

## 自动验证

长期回归位于 `src/test/java/org/b3log/symphony/util/AnonymousCaptchaShieldTest.java`，TestNG 每项 60 秒超时，执行进程也应设 60 秒硬超时。使用真实提取类、匿名中间件、全局入口及 Latke/Netty 请求处理；数据库读取用明确的文章/选项夹具，极验使用本机隔离 HTTP 供应商，爬虫测试只注入已核实/已拒绝缓存结果，不证明真实 DNS 校验可用。

覆盖首访/API 分支、周期验证、文章/公开页豁免、白名单/已有用户 IP、爬虫结果、黑名单优先于 CORS、盾关闭仍保留阅读约束、验证码页面、V3 与旧身份隔离、成功/失败状态迁移、请求签名和供应商坏响应。既有复读机脚本改用真实验证码盾及运行时依赖，不再为其外围依赖叠加兼容 shim；脚本需 `API_V3_RUNTIME_CLASSPATH` 指向已构建应用类与依赖，SQLite 可用 `API_V3_SQLITE_JAR` 指定，进程超时 60 秒。

## 部署后的人工验收

先在 IDEA 重新编译或重启修改后的 Java 服务。使用确认无用户 IP 豁免、未加白的测试来源，不能用已登录过的同 IP 无痕窗口代替新 IP。

1. 管理员进入其他设置，打开验证码盾和首访验证。新匿名来源访问主页应进入 `/test`；普通路径及不存在的路径仍跳转验证页。退出登录不能自行解除该 IP 的验证码限制。
2. 提交无效验证应保持限制；完成真实极验后应回到主页，后续访问放行，累计至五次再次要求验证。
3. 新匿名来源直达允许匿名的文章，首访应阅读正常；禁止匿名文章仍受原阅读限制，不存在文章仍按原规则返回错误。
4. 独立匿名 API 来源触发规则应收到 401，不应由该次 API 验证分支加验证码黑名单；合法旧 apiKey 与 V3 Bearer 仍在各自入口放行，旧 Cookie/apiKey 不升级为 V3 身份。
5. 已触发验证的 IP 在盾关闭后访问普通页面不再强制进入 `/test`；直接访问 `/test` 仍能显示验证页；重开盾后未清理的限制恢复。
6. 管理员 IP 解封后，该来源按原策略本次启动免验证码；再次系统封禁应撤销豁免。Linux ipset 的真实封禁/解封仅在隔离测试环境验证，避免封禁生产用户。
7. PC/mobile 检查原极验页面和后台开关。开发模式重启仍默认关闭原开关，生产模式使用原默认值。

未完成真实服务重启、极验浏览器交互、Linux ipset 或数据库鉴权时应明确记为未验，不能以隔离回归替代。Nginx/CDN、目录枚举、静态版本/source map、上传下载路径未纳入本次提取验收，不宣称全站防漏扫通过。

## 本次文件清单

| 文件（项目相对路径） | 实际变更 |
| --- | --- |
| `src/main/java/org/b3log/symphony/util/AnonymousCaptchaShield.java` | 新增统一验证码盾，私有状态、规则、页面、验证、解封与版本注释 |
| `src/main/java/org/b3log/symphony/processor/middleware/AnonymousViewCheckMidware.java` | 原位置委托验证码检查，保留身份与匿名阅读规则，移除原状态和未用注入 |
| `src/main/java/org/b3log/symphony/processor/BeforeRequestHandler.java` | 委托原位置验证码黑名单检查 |
| `src/main/java/org/b3log/symphony/processor/CaptchaProcessor.java` | 保留路由与图片验证码，极验盾入口委托新类，删除旧极验方法 |
| `src/main/java/org/b3log/symphony/processor/AdminProcessor.java` | 开关、状态、封禁撤白和解封统一调用 |
| `src/main/java/org/b3log/symphony/Server.java` | 开发模式初始化调用新类 |
| `src/main/java/org/b3log/symphony/service/ApiV3RealtimeService.java` | 读取统一的实时验证码限制 |
| `src/main/java/org/b3log/symphony/processor/SettingsProcessor.java` | 两个旧联系方式发码入口迁移极验验证调用 |
| `src/main/java/org/b3log/symphony/processor/middleware/validate/UserForgetPwdValidationMidware.java` | 找回密码迁移共享极验验证调用 |
| `src/main/java/org/b3log/symphony/processor/middleware/validate/UserRegisterValidationMidware.java` | 注册迁移共享极验验证调用 |
| `src/main/java/org/b3log/symphony/service/AccountPasswordResetRequestService.java` | 账户找回发码迁移极验验证调用 |
| `src/main/java/org/b3log/symphony/service/AccountRegistrationRequestService.java` | 账户注册发码迁移极验验证调用 |
| `src/main/java/org/b3log/symphony/service/ApiV3ContactSettingsService.java` | v3 联系方式发码迁移极验验证调用 |
| `src/test/java/org/b3log/symphony/util/AnonymousCaptchaShieldTest.java` | 新增长期回归，覆盖实际提取类、原入口与本地供应商协议 |
| `src/test/java/org/b3log/symphony/service/ApiV3RepeaterQueryTest.java` | 迁移私有状态观察；纠正既有 API 401 预期及 Response 夹具 |
| `tools/api-v3-repeater-test.mjs` | 引用真实盾与运行时依赖、减少旧 shim，增加超时与临时目录清理检查 |
| `tools/check-captcha-shield.py` | 只读指纹与调用覆盖检查，无自动更新功能 |
| `tools/captcha-shield-lock.json` | 初始策略版本与 20 项保护指纹 |
| `AGENTS.md` | 登记授权限制、核心边界和检查入口 |
| `docs/captcha-shield.md` | 规则、锁定范围、验证边界和人工验收 |

## 接口与反漏扫复核

本次不新增或删除 HTTP 入口，不修改参数/返回契约、路由挂载、权限中间件和 `anonymous.viewSkips`；`API.md`/`UPDATE_LOG.md` 无接口变化需要记录。未鉴权、越权与 CSRF 仍沿原鉴权和证明入口，不扩大权限；无新增 HTML 插值、用户输入 SQL、外部 URL 入参或上传下载能力。供应商地址仍来自原服务端配置，失败不放行，证明和密钥不新增到响应/日志；批量请求仍沿原计数/CC 策略，没有宣称解决原并发计数或全站滥用问题。无 FTL/JS/SCSS、静态/CDN 资源变更，因此没有前端构建产物。本节为源码范围结论，生产外部链路和真实鉴权仍须按人工验收核实。
