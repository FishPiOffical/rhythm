# v3 网页侧APP短期扫码接入（2026-10-27）

本批仅改website_v3网页侧，不改APP源码、Java、FTL、SQL，不执行Maven。复用现有requests/status/decision三POST；后端285条注册和API.md契约不变。需要支持v3短期扫码协议的APP，不能声称当前旧APP已可用。

## 文件与行为

- website_v3新增`src/api/v3-app-login.ts`：三网页POST，规范32字节base64url标识、精确qrPayload绑定、状态与到期时间白名单。标识只放JSON，不进URL。
- 新增`src/utils/appLoginQr.ts`：使用[qrcode官方模块](https://github.com/soldair/node-qrcode)本地生成矩阵，以SVG路径绘制；不使用外部二维码图片服务，不注入原始SVG字符串。
- 新增`src/components/settings/OwnerAppLogin.vue`：显式创建、手动查询、APP确认后网页明确批准、拒绝、两分钟倒计时/到期清码；提交未知先查询当前状态，不能用陈旧状态继续批准。状态不同于登录成功：只有consumed显示APP已完成。APP确认后隐藏扫码图；终态和到期允许显式创建新请求。
- 修改`src/views/SettingsView.vue`：账号页实际挂载组件，移除旧apiKey门禁和旧自动码加载；现有资料、背包、勋章及敏感设置保持。
- 修改`src/api/fishpi.ts`：删除无消费者旧fetchAppLoginQr/getApiKeyInWeb/qrserver链。
- 修改`package.json`、`package-lock.json`：增加qrcode与开发类型；沿现有npm锁，不创建yarn.lock，构建仍执行corepack yarn run build。
- 新增`tools/v3-app-login-test.mjs`：真实生产API/QR工具/完整Vue组件16组专项。
- 两仓AGENTS、本记录、迁移总表更新；Rhythm另更新UPDATE_LOG.md。

请求标识仅组件内存，退出、换身份、离开账号页时清除，不写持久存储。正常同账号token轮换保留请求并查询服务器；若原发行授权已不可用，显示请求不可继续，不能把旧扫码身份提升成新授权。无自动批准或自动创建，无后台轮询。创建响应丢失可显式创建新请求，标识未知的旧请求无法批准。

## 实际验证

- tools全部65脚本692组通过；末次授权边界/重复到期处理收紧后，重跑扫码16组及类型/客户端/SSR构建通过。
- 产物73个JS，版本审计0发现，无map/sourceMappingURL或旧qrserver调用。
- 独立临时jsQR解码器对实际appLoginQr输出SVG路径栅格化后解码，41×41矩阵（含静区）回读精确v3文本；不依赖外部图片服务。不是物理APP摄像头验证。
- npm audit新旧锁各报告既有body-parser、vite两项high；新增二维码依赖未新增审计项。本批不顺手更新既有框架依赖。
- 真实Chrome生产构建+SSR、仅本地合成账号及协议状态：桌面1280×720创建→claimed→APP确认→明确网页批准→已保存但503→仅查询状态→web_approved→consumed，创建/批准各一次；移动390×844本地二维码完整展示→明确拒绝→denied。无真实APP、生产登录或生产写入。
- 桌面证据`ev_f28b91b1955ab1942964a9709d30b95f562cdb16623c7e1958d24a0575e783f4`，移动`ev_5a55d6fab5007432a4310d9578261dfd03dbd4ebaf42437c6a3f21298a2cd94c`。无page-error/水合警告；桌面有预期decision503。账号页原有密码控件有两条Chrome verbose提示，不归称全控制台零信息；相邻安全面板夹具响应不完整显示读取错误，未将其作为该模块验收。

## 人工完整链

1. 登录website_v3进入设置→账号（`/settings/account`），点击“生成新二维码”。初始不自动请求；应本地显示二维码、目标账号与原两分钟倒计时，Network只见现有v3 POST，不应调用getApiKeyInWeb或外部二维码图片服务。
2. 用支持新协议的APP扫码并核对目标账号，在APP确认；网页点“查询扫码状态”，从等待/已扫码变为APP已确认。此前不能批准；点击批准先弹明确账号确认，取消无POST，确认才写decision。网页批准不直接声称登录完成，再查询consumed才显示完成，码只能消费一次。
3. 在扫码前或确认后点击拒绝，确认后显示作废、不再显示二维码。到期本地立即隐藏二维码/批准操作；服务端410/403/404同样要求重新生成。终态可显式生成新请求。
4. 用开发代理模拟批准已提交但返回503：显示结果未确认、批准禁用；仅点查询状态，若已批准/已完成则显示相应结果，不自动重发决定。状态查询503时也不能依据旧APP确认状态批准；恢复读取后再按当前状态操作。
5. 创建响应丢失时不展示伪造标识/二维码，允许重新生成。请求中退出/换账号或打开确认后离页，旧响应不得回写，确认不得继续POST。正常token轮换查询当前授权，原发行凭据失效时拒绝继续；不要把换令牌当成延长原码有效期。
6. 桌面/390px检查二维码留白、文字换行、倒计时、确认弹窗和拒绝成功；直接SSR访问不输出扫码标识或二维码。离页再回不保留旧码，整页刷新不能恢复内存请求。

## 安全与反漏扫复核

既有网页三POST均ApiV3AuthMidware+ApiV3WriteMidware保护，服务层第一方当前用户/issuer/epoch/owner边界保持；客户端不新增Cookie写路径或凭据例外。APP侧四匿名协议入口及其AnonymousViewCheckMidware、Before黑名单/test、验证码解封、anonymous.viewSkips均未改。组件只在当前登录账号页挂载，SSR不发扫码请求。

对白名单状态/标识/到期绑定、越权身份与迟到响应、取消确认有隔离测试。SVG路径只由本地矩阵数字坐标构造，无HTML或任意链接注入；不发送远程抓取请求/SQL，不新增上传、下载地址或目录。手动单在途请求、原码未终结不另造码，不暴露旧apiKey或APP令牌。新增依赖本地打包，公开产物已扫版本/map，未增加公网CDN。

真实服务第一方/owner越权、旧issuer轮换、PKCE及APP换码、数据库单次消费、两分钟服务端时钟、全局黑名单/首访及周期验证码、/validateCaptcha解封、开发风控关闭、限流/目录响应仍待运行验收；不能用合成浏览器状态证明真实APP登录成功。本批未修改APP，当前官方APP兼容状态未核实。
