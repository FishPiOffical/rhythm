# v3 客户端捐助入口

2026-10-25。按用户已确认范围，仅提供现有站内捐助页面链接，不创建订单。后端仍264路，本批无Java/SQL或契约变化。

## 实际文件

客户端根 `/Users/ling/Documents/fishpi/website_v3`：

| 文件 | 行为 |
|---|---|
| src/api/v3-donation.ts | 读取既有GET /api/v3/payments/donation-page，仅返回合法HTTPS页面地址，开发模式额外允许HTTP回环；拒绝凭据、query、fragment、控制字符及异常路径 |
| src/views/ChargePointView.vue | 移除金额/附言/旧pay/wechat和支付二维码，展示明确的经典页面链接、完整地址及登录说明；失败重试/SSR/当前身份与迟到响应隔离；SCSS源码 |
| tools/v3-donation-test.mjs | 四组实际API/完整Vue模板及SSR专项 |
| README.md、docs/V3_MIGRATION.md、AGENTS.md | 当前迁移边界与导航约定 |

Rhythm本批更新本记录、progress、UPDATE_LOG及前批认证验证补记；API.md已有契约保持。旧服务端支付入口不随客户端删除。

## 行为与证据

页面登录后读取链接，用户显式点击新页打开，noopener/noreferrer，不附Bearer/API key或Cookie，不创建订单。经典页面可能需要独立网页登录；不能用v3会话承诺该页面已登录。服务端配置地址若正好指回SPA当前捐助路由则提示管理员检查，避免无效循环。HTTP链接只接受开发回环，生产要求HTTPS；配置站点域名可以不同于SPA，按服务端返回提供可见完整地址，不改写为SPA路由。

四组专项退出0：合法配置URL/开发回环与生产拒HTTP、异常响应/路径/凭据/脚本地址拒绝；真实Vue明确锚点和无下单输入/QR、普通token刷新不重读；失败恢复/退出换号/迟到响应和同页面循环；SSR零私有读取/订单。日志 `/tmp/rhythm-v3-client-donation-tests.log`。积分五组和认证七组回归通过。corepack yarn run build类型/client/SSR通过（系统无yarn别名，使用已有corepack）；65个生成JS版本/map定向审计零命中，无Maven或直接CSS/min.js修改。

实际IAB本地HTTP/WS合成夹具：初次503显示错误，重新读取后显示https://fishpi.cn/charge/point明确链接与完整地址；Hydration日志空。仅两次GET，未点击生产链接、未下单/支付。截图 `/tmp/rhythm-v3-client-donation-proof.png` 已目视检查。夹具日志 `/tmp/rhythm-v3-client-donation-fixture.log`。

顺带补验上一批认证分页：21条历史，点击第二页首次503，出现返回申请首页；返回第一页后再下一页成功2/2、仅1条、下一页禁用，审核说明<b>标签按字面文本显示。Hydration空，截图 `/tmp/rhythm-v3-client-identity-pagination-proof.png`。这只补充本地浏览器证据，不代替真实后端。

## 完整人工链

1. 前端构建后第一方登录→积分页捐助/导航捐助→/charge/point。真实后端联调仍需先由IDEA重新编译/重启此前Java/FTL并部署所需迁移，本批未改Java。确认没有金额/附言/支付QR，只有读取与明确页面链接。
2. 正常读取显示后台配置的经典站点完整地址；用户打开新页，未登录时走经典网页登录，再按经典页面操作。本批只验证链接，不执行真实捐助，不承诺订单/积分回调已验。
3. 模拟503/401/403及非法URL，不能残留旧链接；重试成功恢复。生产HTTP、javascript、带凭据或token query、fragment、异常路径拒绝；指回当前SPA页面提示配置问题，不无限导航。
4. 读取在途退出/换号/离页，旧结果不能显示给新身份；普通token续期保留已有地址。匿名无私有请求，SSR显示准备态，挂载后当前身份一致。PC/移动和真实浏览器Cookie/Bearer/弹窗/经典登录另验。
5. 补验认证：列表21条，第二页失败可重试或返回首页；最后一页页码/记录/按钮正确，说明仅文本。此项本地夹具通过，真实业务库仍待运行。

## 安全与未验范围

无新匿名路由/viewSkips、Cookie认证例外、SQL、文件上传下载、远程抓取或CDN。复用既有v3鉴权和HTTP风控处理，没改Before黑名单、AnonymousViewCheck或Captcha解封；固定API路径，只读无CSRF写入，链接不携凭据、不给任意输入创建支付URL。模板文本转义，无HTML插入；无新注入/SSRF通道。去除客户端可重复GET下单，忙碌态防重复读取但不替代后端滥用限制。

本批源码/生成JS定向反漏扫通过，未全扫全站CDN版本、目录枚举、历史上传下载路径；真实未鉴权/越权/CSRF、黑名单/test/解封/开发模式、支付与设备未运行验收，不默认通过。APP扫码协议待用户选择，其余内容写入/互动/私信/聊天室仍待迁移，v3未整体完成。
