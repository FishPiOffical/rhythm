# 验证码盾、爬虫与访问来源

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[受保护范围](../captcha-shield.md)、[验收工具](captcha-acceptance.md)、[爬虫审计](../search-engine-verification-audit.md)。

## 受保护入口与运行模式

- 验证码盾集中在 `util/AnonymousCaptchaShield`；原中间件保留身份解析与匿名阅读限制，全局入口须在 CC 后、CORS 前调用黑名单拦截。极验共享校验、状态及开关只经此类访问；未经阿达明确允许，禁止修改、移动、删除该类及各处受保护调用，禁止增加绕过分支。修改获批后才可同步策略版本、`tools/captcha-shield-lock.json` 与回归；部署前运行 `python tools/check-captcha-shield.py`，不得自动重建指纹来消除失败。范围和验收见 `docs/captcha-shield.md`。
- `AnonymousViewCheckMidware#handle` 解析适用身份后调用 `AnonymousCaptchaShield.checkAnonymous`；验证码核心的匿名判定顺序为「适用登录态（旧入口含apiKey，V3只用已验证V3身份）→ 已验证蜘蛛 → 公开信息页（`/about` `/download` `/privacy` `/agreement`）→ `UserCache.hasUserByIP` 白名单（同IP有用户登录过即免验证，属既定设计）→ 2小时内通过验证码 → 文章页首访豁免 → 非文章页首访（受后台开关控制）→ 每5次访问」。计数窗口2小时，累计300次触发 `Firewall` 封禁；`/api/` 命中验证码规则返回401且不拉黑。`sitemap.xml` 不在豁免名单，依赖已验证蜘蛛通行。策略细节以受保护核心与 `../captcha-shield.md` 为准，本文不授予修改权限。
- `Server` 启动逻辑：`DEVELOPMENT` 模式会关闭 `Firewall` 与 `AnonymousViewCheck`（验证码盾），联调时不要误判“线上无校验”。监听前解析 `new.fishpi.cn` 并解封，加入 CC 与验证码白名单；CC 白名单不覆盖手动封禁，其他 IP 阈值不变。依赖可信代理正确设置来源 IP。

## 监控与缓存

- 安全面板约定：CC/验证码/首访验证与请求阈值均为进程内临时状态，重启恢复默认；`DEVELOPMENT` 才允许统一保存，`PRODUCTION` 只读且服务端拒绝所有开关写入。统计与自检为有界内存数据，不写数据库、不代表完整生产审计。
- 安全链路观测复用`SecurityMonitor`（2小时/200条，重启清空），`SkinRenderer`真实渲染后确认页面结果；后台/静态请求不能冒充验证后恢复。ipset须退出结果+成员核实，集合成员不代表真实丢包；监控不自动解封/加白，缺后续保持待确认。
- 爬虫运行统计在`CrawlerMonitor`，复用入口验证结果，已验证/校验未通过分组且不能把后者断言为伪造；每组1000条去查询参数路径，超出计数保留。仅adlered经原后台监控读取，重启清空；不覆盖Nginx/ipset提前拦截或独立WebSocket链路。来源明细按IP保留最近实际校验原因/PTR/正向地址/时间/缓存标志，随原正负缓存保存，不为展示再查DNS；每组最多1000来源，超量请求单列计数。
- 蜘蛛校验缓存按类型区分：DNS通信异常/服务暂不可用15秒，其余失败10分钟，成功6小时；命中不续期，到期后由下一次请求重新核验，不因临时异常自动放行，不按诊断字符串判断缓存期限。
- 爬虫失败来源的后续状态仅关联同引擎同IP的真实校验时间，成功标记恢复、再失败撤销；保留原失败详情/次数，旧缓存不得覆盖新结论。此状态为运行期观测，不代表当前缓存或最终HTTP可达，不额外查DNS或解封。

## 搜索引擎验证与来源统计

- 文章访问来源图标：`ArticleSearchVisitStatQueryService#engineIcon` 为每个 `SearchEngines.Engine` 显式映射 `symbol-defs.js` 中已存在的 symbol，新增引擎须同步补图标、PC/移动 SCSS 颜色及 `ArticleSearchVisitStatQueryServiceTest`；移动端未加载 tooltipped 样式，来源名称靠 `tabindex` 聚焦后的 `::after` 显示。
- 搜索引擎爬虫校验：`SearchEngines` 对 Google/Baidu/Bing/Sogou/Yandex 走 DNS 双向校验；正向按来源地址族单查A或AAAA，禁止JNDI合查两种类型（会变成ANY，解析器可能超时/仅回RFC8482）；DuckDuckGo 走本地 CIDR 资源 `src/main/resources/search-engines/duckduckgo-bot-prefixes.json`，该文件由 `scripts/deploy.ps1` 调用 `scripts/update-duckduckgo-bot-prefixes.ps1` 在打包前合并官方 JSON。360官方不支持反查，须命中360Spider UA及`search-engines/360-bot-prefixes.json`官方网段，不落全局白名单；网段按官方`so.com/help/spider_ip.html`人工复核维护。 Applebot同走双向DNS（applebot.apple.com）；OAI-SearchBot/Claude-SearchBot/PerplexityBot/MistralAI-Index按各自官方CIDR，打包前统一执行`scripts/update-duckduckgo-bot-prefixes.ps1`（同时更新DuckDuckGo及四角色，360仅提示人工维护），仅新增搜索角色，不混入训练/用户抓取器；维护与私有部署挂钩边界见`docs/search-engine-verification-audit.md`。
- 文章浏览器/设备来源仅由 `ClientVisitSources` 分类，经原浏览量去重后记入来源表；不可用于蜘蛛身份或风控放行。宿主优先于内核，鸿蒙仅认明确系统标识；历史 `browser:samsung`/`device:iphone` 保留，新增设备不回填，iPad桌面UA不推测；设备图标独立于Applebot。
