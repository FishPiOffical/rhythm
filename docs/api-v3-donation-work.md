# v3 捐助页面链接（2026-10-24）

内部工程记录。用户确认“支付跳转”仅返回现有站内捐助页，不新增支付订单或回调。

## 实际文件与接线

- `src/main/java/org/b3log/symphony/processor/ApiV3DonationProcessor.java`：独立 GET 注册、activity.read、统一响应。
- `src/main/java/org/b3log/symphony/util/ApiV3DonationPage.java`：只从配置站点构建固定 `/charge/point` 链接，保留配置 contextPath；非法配置返回503。
- `src/test/java/org/b3log/symphony/processor/ApiV3DonationTest.java`：真实 URL 工具、Dispatcher、AuthMidware 与 Requests 响应边界。
- `tools/api-v3-donation-test.mjs`：Java25 无 Maven 隔离 runner。
- 本工作记录。

主代理需在总 `ApiV3Processor.register` 调用 `ApiV3DonationProcessor.register()`；本子任务未改总注册、API.md、UPDATE_LOG、AGENTS 或进度清单，公开文档及计数由主代理维护。

## 完整契约

`GET /api/v3/payments/donation-page`

- Authorization: Bearer，所需权限 activity.read；沿现有 Token 准入，第一方/第三方均可，不新增 scope。
- 无业务请求参数。传入 redirect、host、userId 等参数不会改变目标；不接受客户端回跳地址。
- 成功采用常规信封，`data` 仅 `{ "url": "https://fishpi.cn/charge/point" }`。开发配置对应 `http://localhost:8080/charge/point`。
- 无有效 Bearer401、缺 scope403、站点配置无效503。
- URL 是页面地址，不是订单、二维码或私有登录票据；不会发起支付、扣款、写 DB、远程请求、302 或建立页面 Cookie。
- 源码证据：`IndexProcessor.register` 的 `/charge/point` 挂 `loginCheck::handle`；PC/mobile 同走 `showChargePoint` 的 `charge-point.ftl`。浏览器打开链接需已有页面登录态，否则需按原页面流程登录；取得 API Bearer 不代表浏览器已登录。公开文档应明确此使用条件。

## 执行验证

`node tools/api-v3-donation-test.mjs` exit0，日志 `/tmp/rhythm-v3-donation.log`，3组通过：

1. 固定路径、配置 contextPath、生产 HTTPS/开发 HTTP 回环（localhost/127.0.0.1/IPv6）、新鲜 JSON 对象；原始 URL 不受调用方字段影响。
2. 非法空配置、用户凭据、query/fragment、危险协议、非回环明文、非法端口、路径回退/双斜线与控制字符失败关闭，错误不暴露配置值。
3. 真实 Dispatcher 路由→真实 AuthMidware（实际 hasScope 方法）→真实 Processor→真实 Requests：无 Bearer/Basic/损坏/过期或撤销 Token 适配结果401，缺 scope403；第一方/第三方200。伪造 Cookie/apiKey/Host/redirect 不改变授权/目标；无 Set-Cookie/Location，no-store/Vary正确。

Token 持久化 authenticate 与响应信封是明确适配器；过期/撤销测试模拟 authenticate 返回null，没有重新测试真实令牌生命周期。未编译完整服务/IoC，未运行 Maven、数据库、浏览器、真实支付、SMTP 或外网请求。没有前端修改，无需 yarn build。Java代码需要用户先 IDEA 重编译或重启服务端再联调。

## 安全与反漏扫

- 未鉴权/越权：需要 Bearer+activity.read，无用户资源代查；无 Cookie/apiKey 回退。
- CSRF：只读 GET，没有写入/支付/跳转或会话建立。
- XSS/注入/SSRF：固定 JSON 字段与配置站点，不含用户内容，不触发 SQL/网络；HTTPS 或开发回环 HTTP，禁止 userinfo/query/fragment。
- 批量滥用：固定一个 URL，零数据库/网络；沿顶层 Firewall，不新增限流配置。
- 敏感信息：只回已配置公开站点页面地址，不返凭据、订单、二维码或用户资料。
- 匿名/防扫描：本路由非匿名，没改 `anonymous.viewSkips`。源码确认 BeforeRequestHandler 黑名单检查仍先跳 `/test`；没改 AnonymousViewCheckMidware 或 CaptchaProcessor。真实新IP、黑名单、验证码/解封与开发关闭风控回归未执行。
- 静态/CDN/sourceMappingURL/枚举：未增加或修改静态资源/CDN/source map/文件服务；无全库公网扫描结论。
- 上传/下载：不产生上传/下载路径或文件公开地址；仅已有页面链接，未改旧链路。

## 人工验收（先 IDEA 重编译/重启）

1. 第一方或第三方授权取得 activity.read Bearer，GET本接口，验证200常规信封、仅data.url且目标 `/charge/point`。
2. 从已有登录态浏览器打开该 URL，PC/mobile 均应进入原捐助页面；另用无 Cookie 浏览器打开，依原页面要求登录。不要为验证链接发起真实付款。
3. 仅 Cookie/apiKey、无 Authorization、过期/撤销 Bearer均401，合法缺 scope403；附 redirect/host/query参数和伪造Host时目标不变，响应无 Cookie或302。
4. 在隔离环境设置错误站点（含凭据/query/非法协议）验证503且不回显配置；恢复配置后正常。开发localhost8080可用，生产配置HTTPS可用。
5. 风控启用隔离环境黑名单IP仍先/test，validateCaptcha解封后正常；回归匿名首访/计次与开发关闭风控。以上真实运行链路本轮未执行，不默认通过。
