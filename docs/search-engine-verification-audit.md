# 搜索爬虫核验审查

核对时间：2026-11-11。范围为现有源码与可读取的官方资料，不代表生产每个来源都已通过验证，也不是搜索市场份额排名。

## 已覆盖的七家

| 引擎 | 当前识别 | 身份核验及官方依据 | 结论与边界 |
| --- | --- | --- | --- |
| Google | `googlebot` | [官方核验文档](https://developers.google.com/crawling/docs/crawlers-fetchers/verify-google-requests)：PTR 为 googlebot.com / google.com / googleusercontent.com，再正向匹配原 IP | 方法及后缀相符；未覆盖所有特殊抓取器、用户触发工具的独立 UA，不能宣称全部 Google 产品已覆盖 |
| 百度 | `baiduspider` | [官方 Baiduspider FAQ](https://help.baidu.com/question?prod_id=99&class=0&id=3001)：反查 baidu.com / baidu.jp | 后缀相符；项目额外要求正向匹配，是已有加固而非官方 FAQ 明确要求；生产已有同源修复前失败、修复后通过证据 |
| Bing | `bingbot` / `msnbot` / `bingpreview` | [微软官方双向核验说明](https://blogs.bing.com/webmaster/2012/8/How-to-Verify-that-Bingbot-is-Bingbot/)：PTR search.msn.com，正向匹配原 IP；[官方 IP JSON](https://www.bing.com/toolbox/bingbot.json) 也已可用 | 双向方法相符；旧博客“不发布 IP 列表”的历史描述不再适用，不能照抄；项目未切换至 IP 列表 |
| 搜狗 | `sogou web spider` | [官方 Spider 说明](https://zhanzhang.sogou.com/index.php/help/spider)：PTR 为 sogou.com，或 Web Bot Auth 验签，任一种通过即可 | DNS 路径是官方支持方式；现有额外正向匹配保留。官方说 UA 区分大小写，项目忽略大小写但仍校验来源，非单凭 UA 放行；其他搜狗 UA 不保证识别。未实现签名验证，不逐请求调用官方事后验签接口 |
| Yandex | `yandexbot` | [官方核验说明](https://yandex.com/support/webmaster/en/robot-workings/check-yandex-robots)：PTR yandex.ru / yandex.net / yandex.com，正向匹配原 IP | 方法相符；其他专用机器人 UA 不全部覆盖 |
| DuckDuckGo | `duckduckbot` / `duckassistbot` | [DuckDuckBot 官方说明](https://duckduckgo.com/duckduckgo-help-pages/results/duckduckbot)、[DuckAssistBot 官方说明](https://duckduckgo.com/duckduckgo-help-pages/results/duckassistbot) 分别指向官方 IP JSON | 使用本地 CIDR 合集；本次与两个实时官方列表核对，合集 486 条，无缺失/过期条目。两个 UA 共用两份列表的并集是已有规则，非逐产品绑定；更新依赖现有部署脚本 |
| 360 | `360spider` | [官方蜘蛛 IP 说明](https://www.so.com/help/spider_ip.html)：明确暂不支持 nslookup，公布六个 IPv4 网段 | 本轮改为 UA + 官方六个 /24，本地 JSON；移除不适用的 DNS 校验，不加全局白名单。不自动更新网页，不自行推测新增网段 |

所有保留 DNS 核验的引擎，正向均按来源地址族单查 A 或 AAAA，禁止合查触发 JNDI ANY。异常不放行；有记录不等于地址匹配。后缀检查按域名边界，不能接受 `googlebot.com.fake.test` 一类伪装。

## 尚未全部覆盖

当前七家包含多家常用引擎，但不能称“市场主流全部支持”。尚缺以下独立机器人或无法按 UA 区分的来源，本次只列缺口，不新增豁免：

| 来源 | 官方证据及核实程度 | 当前行为 |
| --- | --- | --- |
| Applebot（Siri/Spotlight/Safari 等） | [Apple 官方说明](https://support.apple.com/en-us/119829) 支持 applebot.apple.com 反向/正向核验或官方 CIDR JSON | 未识别为专门引擎，按普通请求防护；不能假装已支持 |
| Naver Yeti | [Naver 官方 robots 指南](https://searchadvisor.naver.com/guide/seo-basic-robots) 确认 Yeti UA；本次未取得可验证的官方身份核验正文 | 无 Yeti 识别；不能仅凭 UA 加白 |
| 神马、头条搜索 | 未识别神马/Bytespider；本次神马尝试入口 404，未确认当前官方来源核验标准。Bytespider 也不能一概当作单纯搜索索引器 | 无独立已验证分组，按普通请求防护，不采纳第三方旧 IP 表 |
| PetalBot | 官方 `webmaster.petalsearch.com/site/petalbot` 仅返回 JS 壳，本次未核实正文中的身份核验规则 | 未支持，不以页面 HTTP 200 冒充验证完成 |
| SeznamBot | 旧官方帮助链接重定向帮助首页，未得到当前身份校验正文 | 未支持 |
| Brave Search | [官方说明](https://search.brave.com/help/brave-search-crawler) 明确其爬虫不使用可区分的独立 UA | 不能用猜测 UA 自动增加可信来源；现有 Google UA 仍须通过 Google 来源核验 |

ChatGPT 搜索、Perplexity 等 AI 搜索及训练/用户触发抓取器不等同于传统搜索机器人，本轮未建立身份信任或全面审计，不据此扩大白名单。部分搜索产品使用其他引擎索引，也不能据品牌名推断会有独立爬虫。

## 验证与保留事项

- 本次只改360核验方式及共用CIDR资源加载，不改其他引擎UA、信任规则、缓存期限、公共路由、账号/apiKey权限或防扫描入口。
- 源码与无真实数据库测试覆盖网段边界、网段外拒绝、无标识不识别、缓存、DNS地址族、后缀伪装、异常和原验证码/CC回归；不伪装成真实搜索引擎从本地绕过防护。
- Java/资源修改需重新编译/打包并重启，确认 JSON 随包。真实360到访、最终正文HTTP响应、极验与Linux ipset效果仍需生产人工验证；身份通过不能证明抓取成功或收录。
- 官方资料会更新，360网段与其他本地列表均有维护时效；不得自动扩大信任来掩盖官方证据不足。审查原始网页与失败抓取记录保留于本轮隔离任务目录，未加入公共API文档。
