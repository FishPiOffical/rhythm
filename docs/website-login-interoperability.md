# 新旧网页登录互通（本地验证版）

## 行为与边界

- 旧站 `/login` 的密码验证、MFA 和既有返回字段保留，验证后同时签发旧站 `sym-ce` 和新版 `fp_v3_refresh`。来自明确第一方来源的 V3 密码登录同样签发两种 Cookie；无网页来源的已有 V3 客户端保留原流程。
- 绑定使用既有 `website_v3` 令牌家族，无新表。旧 Cookie 加密携带服务端生成的家族标识；校验时检查有效刷新令牌、客户端状态、账号状态和原凭据版本。旧 WebSocket 亦检查家族撤销。
- 两种 Cookie 均 HttpOnly、host-only，不扩大到 `.fishpi.cn`。旧 Cookie Path `/`、SameSite=Lax；刷新 Cookie Path `/api/v3/auth`、Secure、SameSite=Strict。绑定会话记住登录为 30 天，正常刷新延续同一旧 Cookie；旧版不勾选记住登录时，两种 Cookie 都保持浏览器会话级。
- 退出先撤销服务端状态，成功后清两种 Cookie；V3 退出还处理两种 Cookie 来自不同家族的残留情况。旧站退出从绑定 Cookie 定位家族，不要求读取窄路径刷新 Cookie。其他设备、第三方授权不撤销。
- 撤销失败返回 503，保留 Cookie 供重试。新版立即清本地身份，显示紧凑重试入口，未完成退出时不自动恢复登录。
- 新增 POST `/api/v3/auth/session`：匿名风控中间件 + 明确第一方来源 + 空对象正文。只返回当前绑定身份与非凭据版本摘要，no-store，不签发令牌。PC/mobile 旧站与新版都在重新聚焦/可见时核对，5 秒节流带尾部检查；无后台轮询。新版换号先建立身份边界，迟到结果不得覆盖新身份。
- 信任本站配置 origin、生产 `https://new.fishpi.cn`；本地仅在服务本身为 localhost/127.0.0.1 时接受本地 5173 和后端端口。不再用任意兄弟子域作为刷新/退出的可信来源。旧 GET 退出兼容直接导航，拒绝明确跨站及不可信来源头。

## 修改文件

- `processor/ApiV3Processor.java`、`processor/LoginProcessor.java`：双端登录/退出、可信来源与状态探测。
- `service/ApiV3TokenMgmtService.java`、`service/UserQueryService.java`：家族签发/撤销及请求级绑定传递。
- `util/Sessions.java`、`util/WebsiteLoginCookies.java`、`util/ApiV3RefreshCookies.java`：绑定验证、Cookie 交付/续期、禁止框架无安全属性回写。
- PC/mobile `footer.ftl` 与 `js/website-session.js`（及构建生成的 min.js）：旧页面聚焦同步。
- `ApiV3RefreshCookiesTest.java`、`tools/account-credential-state-test.mjs`、`tools/experiments/website-login-local-test.py`：回归与本地验证入口；实验脚本不加入默认测试入口。
- website_v3 的 `src/stores/auth.ts`、`src/api/http.ts`、`src/layouts/AppShell.vue`、`tools/v3-website-session-test.mjs`，以及两仓库 README/AGENTS/本文档：身份同步、队列、退出失败反馈与维护说明。

## 上线迁移注意

已有**未绑定**的旧 Cookie 不会静默兑换成新令牌，需在任意一端重新密码登录一次。注册、重置密码以及第三方登录的专用入口未改为双签发；本次覆盖常规密码登录与退出。不复制 Cookie 到新站，不把 Bearer 传入旧接口或图片 URL。

在本地浏览器请统一主机名：`http://localhost:5173` 与 `http://localhost:8081`，或者两者都使用 `127.0.0.1`。不要混用，Cookie 按主机隔离。生产需要前后端一起更新，浏览器仍直连 fishpi.cn；本轮未部署。

## 已执行验证

- Java 25 对改动类定向编译，使用本地现有依赖；重启本地 Rhythm 后测试。
- `node tools/api-v3-token-test.mjs`：通过；`npx --yes yarn@1.22.22 run build` 生成旧站静态产物成功（环境无全局 yarn）。
- `node tools/account-credential-state-test.mjs`：修正失配的隔离依赖后编译通过（此脚本只验证编译闭包，不称为完整行为测试）。
- `python3 tools/experiments/website-login-local-test.py --accounts <本地私有账号文件> --fault-test`：双向登录、双向退出、重放已撤销 Cookie、其他浏览器不受影响、来源拒绝、重复退出、非持久 Cookie、定向数据库撤销故障与恢复重试。固定仅允许本地地址；故障注入仅匹配临时账号，finally 移除触发器。
- 隔离 Chrome 临时配置：桌面旧登录→新版自动恢复、旧退出→新版聚焦清身份；移动视口新版登录→旧站识别、新版退出→旧站聚焦更新。没有读取用户现有 Chrome 的凭据；读取旧页面后两 Cookie 仍为 HttpOnly，退出后两 Cookie 均清除。
- website_v3：类型检查、客户端/SSR 构建、启动恢复和新增会话同步专项。
- 验证码盾指纹及 JS 版本审计通过；生产真实 IP 封禁/极验交互未实测，新增路由复用既有匿名风控链。无上传/下载接口或外部资源变更。

## 人工验收（待完成）

1. 两个站点使用同一主机名，在旧站登录；打开新版，应无需再次输密码。反向重复。
2. 任一端退出，切回另一端应更新为未登录；快速切回允许最多 5 秒合并延迟。
3. 第二个独立浏览器保持登录，第一浏览器退出不应影响它。
4. 在另一端换号，切回后应显示新账号，不能保留旧账号私有内容。
5. PC 和手机分别检查顶部身份、退出、登录页及编辑页面的行为。生产极验需要真实受限 IP 人工验证，不能由本地白名单结果代替。

状态：待开发者亲自验收；本地提交不代表允许合入或发布。

## 2026-11-12 退出清理回归与配套核查

- 修复未绑定旧 Cookie、失效 Cookie 或缺少 Cookie 时退出只清浏览器 Cookie、遗漏服务端 HTTP Session 身份的问题。所有成功退出分支统一经过 `Sessions.logout`，清理 `User.USER` 和 `accountCredentialEpoch`；已有旧 WebSocket 后续认证不再取得该身份。
- 绑定会话仍先撤销家族，撤销异常不会提前清理凭据；没有新增接口、变更返回字段或扩大退出到其他浏览器。
- 长期回归为 `src/test/java/org/b3log/latke/http/WebsiteLoginCookiesTest.java`，使用真实 Latke HTTP/Session/WebSocket 对象和会话缓存；不连接数据库、不启动服务。覆盖未绑定、无效及缺失 Cookie、已有 Socket 身份清理、重复退出、其他会话与用户缓存保留。它不替代绑定家族撤销、数据库故障与浏览器联调验收。
- 运行入口：设置 `JAVA_HOME` 为 Java 25，`API_V3_TEST_CLASSPATH` 为现有应用编译目录（含资源）、捆绑 Latke JAR 及运行依赖的 classpath，然后执行 `node tools/website-login-cookies-test.mjs`。源码和测试隔离编译到系统临时目录，编译与执行各有 60 秒硬超时，不覆盖 `target/classes`。
- 本次结果：退出回归 32 项通过；将修复前的 `WebsiteLoginCookies.java` 隔离编译后，同一用例在清理服务端用户的断言失败。令牌/Cookie/PKCE 回归通过；凭据状态脚本提供完整运行依赖后编译闭包通过（默认 IDE 依赖路径在 Windows 缺少 Caffeine，首次运行失败，不计为行为测试）。验证码盾 24 个受保护源码项通过，180 个 JS 文件版本号命中为 0。
- 本次核查的 Windows `fishpi_website_v3` 工作副本（`b636a6a`）没有上文所述的聚焦会话同步及退出失败重试；其 `auth.ts` 会忽略退出请求错误。后端撤销失败时仍保留刷新 Cookie，该前端重新加载可能恢复登录。上文配套能力与历史验证记录不能作为该副本或当前部署已完整配套的证明；本次仅修复 Rhythm 的退出清理，未修改或验证生产前端。
- 联调前必须通过 IDEA 重新编译或重启服务端。人工验收还需覆盖升级前旧 Cookie/注册后旧会话在退出后的 Socket 写入拒绝，并分别检查 PC、移动端及独立浏览器。
