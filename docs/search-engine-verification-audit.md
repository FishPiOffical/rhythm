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

## 新增的五种搜索蜘蛛

本轮仅扩充自动搜索索引角色。官方明确支持 DNS 的优先双向核验，不凭自行猜测的域名建立信任。

| 角色 | 官方依据 | 实际核验 |
| --- | --- | --- |
| OAI-SearchBot | [OpenAI 官方说明](https://developers.openai.com/api/docs/bots)、[搜索角色 IP JSON](https://openai.com/searchbot.json) | 完整 UA 产品标识 + 独立官方 CIDR 快照；未查到官方支持的 DNS 核验规则，不混入 GPTBot、ChatGPT-User、OAI-AdsBot |
| Claude-SearchBot | [Anthropic 官方说明](https://support.claude.com/en/articles/8896518-does-anthropic-crawl-data-from-the-web-and-how-can-site-owners-block-the-crawler)、[官方来源 JSON](https://claude.com/crawling/bots.json) | 完整 UA 产品标识 + 官方 CIDR 快照；官方列表共用，只接受 SearchBot 标识，不因同 IP 识别 ClaudeBot 或 Claude-User；未查到官方 DNS 规则 |
| PerplexityBot | [官方说明](https://docs.perplexity.ai/docs/resources/perplexity-crawlers)、[搜索角色 IP JSON](https://www.perplexity.ai/perplexitybot.json) | 完整 UA 产品标识 + 独立官方 CIDR 快照；官方要求角色与来源对应，不合并 Perplexity-User 列表；未查到官方 DNS 规则 |
| Applebot | [Apple 官方说明](https://support.apple.com/en-us/119829) | 完整 UA 产品标识 + PTR 为 applebot.apple.com 域内 + 按来源地址族单查 A/AAAA 回指原 IP；不下载 Apple IP 列表，不在 DNS 异常时降级放行 |
| MistralAI-Index | [Mistral 官方说明](https://docs.mistral.ai/robots)、[索引角色 IP JSON](https://mistral.ai/mistralai-index-ips.json) | 完整 UA 产品标识 + 独立官方 CIDR 快照；不合并 MistralAI-User，不增加 Training；未查到官方 DNS 规则 |

Applebot-Extended 是 robots 控制标记而非独立 HTTP 抓取器，iTMS 是播客专用角色，均不增加可信识别。Apple 官方说明 Applebot 抓取的数据也可能用于模型训练，搜索角色识别不代表承诺数据不会被二次训练；本次不更改 robots.txt 或内容使用授权。

### 列表维护与部署

- 资源在 `src/main/resources/search-engines/`，四种角色独立文件并保留官方来源及 creationTime。2026-11-11 下载分别为 39、14、13、2 条，仅为当时快照，不固定测试条数。
- 打包前统一运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/update-duckduckgo-bot-prefixes.ps1`。历史文件名保留，但现在访问六个固定官方 JSON：DuckDuckBot、DuckAssistBot、OAI-SearchBot、Claude-SearchBot、PerplexityBot、MistralAI-Index。DuckDuckGo 沿用两来源去重合并，其他四种分别保存，不合并不同搜索角色的列表。旧 `tools/update-search-crawler-prefixes.ps1` 已删除，不再维护双入口。
- 脚本校验非空、地址族及 CIDR，拒绝 /0；六来源全部下载校验成功后才逐文件替换，失败明确报错，不覆盖为假成功或空列表。写盘失败可能留下部分已更新文件，必须处理错误并重新执行成功后才可打包。`-OutputDirectory` 可指定隔离目录验证，不必改动正式资源。
- 360官网可读取但没有本次核实到的官方 JSON 接口，统一脚本明确提示人工维护，不自动抽取网页，也不改 `360-bot-prefixes.json`；人工对照官网六个网段后审查差异。DNS 引擎不下载 IP 列表，也不增加 DNS 失败后的列表放行分支。
- 指定更新脚本已单独纳入 Git；其余私有 `scripts` 文件仍忽略。本机 `scripts/deploy.ps1` 只调用统一入口并在错误时停止部署，该私有脚本不提交。其他环境须显式执行上述命令并检查退出码。未执行更新时仍用随包快照，官方范围变化需更新并重新打包、重启，不在请求线程下载。
- 新资源由现有 Maven resources 规则随包，不改依赖或数据库。进程重启重新加载列表与校验缓存。
- 新角色复用既有 CC/验证码可信爬虫判定和后台分组，记录源 IP、校验原因及 URL；不加入全局 IP 白名单，不增加后台权限或绕过登录/apiKey。相同 IP 的普通 UA 仍按普通流量处理。文章抓取统计沿现有来源键记录，使用通用搜索图标，不猜测新 Referer 归属。

## 全部已支持角色复核（2026-11-11）

本轮重新读取了上表全部官方核验说明，不将 HTTP 200 当作规则已验证，逐项核对实际正文中的域名或官方列表。共 12 个引擎分组、15 个现有 UA 标识；专用图片/视频 UA 等仍按原有识别规则，不宣称覆盖各品牌全部产品。

| 分组与现有标识 | 维护类别 | 本轮结果 |
| --- | --- | --- |
| Google：googlebot | 双向 DNS | 官方域名规则吻合；独立 DNS 样本 `66.249.66.1` 双向匹配 |
| 百度：baiduspider | 双向 DNS | 官方反查规则吻合，保留额外正向校验；独立 DNS 样本 `116.179.37.120` 双向匹配，另有先前生产修复后成功记录 |
| Bing：bingbot / msnbot / bingpreview | 双向 DNS | 官方 search.msn.com 规则吻合；从官方 bingbot JSON 选取样本 `157.55.39.0` 双向匹配；旧文档示例 IP 无 PTR 不能当作现行故障证据 |
| 搜狗：sogou web spider | 双向 DNS | 官方 sogou.com 反查规则吻合，官方也支持签名核验但本项目不实现新分支；本轮无可确认的真实来源样本，不能声称实际请求已通过 |
| Yandex：yandexbot | 双向 DNS | 官方三个后缀规则吻合；独立 DNS 样本 `77.88.5.245` 双向匹配 |
| Apple：Applebot | 双向 DNS | 官方 applebot.apple.com 规则吻合；独立 DNS 样本 `17.58.101.179` 双向匹配 |
| DuckDuckGo：DuckDuckBot / DuckAssistBot | 自动拉取 | 两个实时官方列表各 486 条，去重合集 486 条，与现有快照一致 |
| OpenAI：OAI-SearchBot | 自动拉取 | 实时官方列表 39 条，与现有快照一致 |
| Claude：Claude-SearchBot | 自动拉取 | 实时官方列表 14 条，与现有快照一致；共享来源须同时匹配搜索角色 UA |
| Perplexity：PerplexityBot | 自动拉取 | 实时官方列表 13 条，与现有快照一致，不读取 User 列表 |
| Mistral：MistralAI-Index | 自动拉取 | 实时官方列表 2 条，与现有快照一致，不读取 User 或 Training 列表 |
| 360：360Spider | 人工复核 | 实时官网仍说明不支持 nslookup，六个 /24 与现有快照逐一一致；更新脚本不写此文件 |

### 可用性与验收边界

- 开发机系统 DNS 本轮正向返回 `198.18.*`，疑似本地代理 Fake-IP，不能作为生产 DNS 成败依据。上表五个成功样本另用独立 `dns.google` HTTPS DNS 接口查询 PTR 和单一 A 记录交叉核对；这只是诊断取证，没有写入应用 DNS 配置，没有新增运行期外部解析服务。
- 规则核实、独立样本、隔离 Java 测试都不等于生产可用率。无法根据这些样本承诺 99.99%；生产实际解析器超时、缓存中的失败结果、官方列表变化、未更新部署、已有 ipset/上游拦截仍可能影响真实请求。
- 若要衡量 99.99%，需先定义观察周期与“经官方证据独立确认的真实蜘蛛请求”分母，同时记录误拒、解析异常与最终 HTTP 结果；不能把“已通过身份核验的请求”自作分母排除所有失败。现有有界内存面板不是完整持久化审计，不足以证明这个指标。
- 人工链路：先在隔离目录运行统一脚本确认五份输出且360原文件不变，再打包重启；真实蜘蛛到访后核对分组、来源IP、DNS/网段诊断及访问日志的最终正文响应；普通IP伪造UA不得变成可信来源，匿名/登录/apiKey和验证码解封保持旧行为。没有真实极验证据或Linux ipset成员及实际访问验证，不能宣布整条防火墙链路已验收。

## 仍未覆盖的其他来源

现有七家及新增五种角色仍不能称“市场主流全部支持”。以下只列缺口，不新增豁免：

| 来源 | 官方证据及核实程度 | 当前行为 |
| --- | --- | --- |
| Naver Yeti | [Naver 官方 robots 指南](https://searchadvisor.naver.com/guide/seo-basic-robots) 确认 Yeti UA；本次未取得可验证的官方身份核验正文 | 无 Yeti 识别；不能仅凭 UA 加白 |
| 神马、头条搜索 | 未识别神马/Bytespider；本次神马尝试入口 404，未确认当前官方来源核验标准。Bytespider 也不能一概当作单纯搜索索引器 | 无独立已验证分组，按普通请求防护，不采纳第三方旧 IP 表 |
| PetalBot | 官方 `webmaster.petalsearch.com/site/petalbot` 仅返回 JS 壳，本次未核实正文中的身份核验规则 | 未支持，不以页面 HTTP 200 冒充验证完成 |
| SeznamBot | 旧官方帮助链接重定向帮助首页，未得到当前身份校验正文 | 未支持 |
| Brave Search | [官方说明](https://search.brave.com/help/brave-search-crawler) 明确其爬虫不使用可区分的独立 UA | 不能用猜测 UA 自动增加可信来源；现有 Google UA 仍须通过 Google 来源核验 |

未列入新增五角色的训练、用户触发抓取器不增加身份信任。未识别为可信蜘蛛不等同于一律禁止访问，仍走现有普通请求防护。部分搜索产品使用其他引擎索引，也不能据品牌名推断会有独立爬虫。现有 DuckAssistBot 兼容规则保留，不在本轮擅自删除。

## 验证与保留事项

- DNS 临时通信异常（`CommunicationException`）与服务暂不可用（`ServiceUnavailableException`）仅缓存 15 秒，读取不续期，到期后的下一次请求重新核验；其余失败仍缓存 10 分钟，成功仍缓存 6 小时。按异常类型分类，不依赖诊断文案；失败期间不授予可信蜘蛛身份，不增加定时重试、自动解封或白名单。
- 360修复及搜索角色扩充保留原七家信任规则、公共路由、账号/apiKey权限及防扫描受保护入口；后续 DNS 临时失败缓存修订仅调整上述异常的缓存期限。
- 源码与无真实数据库测试覆盖网段边界、网段外拒绝、无标识不识别、缓存、DNS地址族、后缀伪装、异常和原验证码/CC回归；不伪装成真实搜索引擎从本地绕过防护。
- Java/资源修改需重新编译/打包并重启，确认 JSON 随包。真实360及新增角色到访、最终正文HTTP响应、极验与Linux ipset效果仍需生产人工验证；身份通过不能证明抓取成功或收录。
- 官方资料会更新，360网段与其他本地列表均有维护时效；不得自动扩大信任来掩盖官方证据不足。审查原始网页与失败抓取记录保留于本轮隔离任务目录，未加入公共API文档。
