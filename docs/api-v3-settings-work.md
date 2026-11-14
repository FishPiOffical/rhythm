# v3 个人设置：表情读取补齐

本记录是内部实现与验证交接，不是对外 API 文档。日期：2026-10-20。

## 已完成范围

头像与 MBTI 的 GET/PUT 已存在，本轮未重复实现。现有 `ApiV3SettingsProcessor` 已覆盖 profile/privacy/display/functions/location/locale 的 GET/PUT、skins/locale-options/personality-options 目录；本轮补四个纯查询入口：

| 接口 | 返回 data | 说明 |
| --- | --- | --- |
| GET /api/v3/me/settings/emotions | `{"emotions":["smile","joy"]}` | 本人常用 ASCII 别名，按原 sort 排序；未配置/内容为空使用原默认列表 |
| GET /api/v3/me/settings/emotion-options | `{"groups":[["smile",...],...],"defaults":[...]}` | 与 PC/mobile 设置页相同分组和顺序；不把 FTL 的 endOfEmoji 哨兵当选项返回 |
| GET /api/v3/me/settings/emoji-groups | `{"list":[{"id":"...","name":"...","sort":0,"type":1}],"pagination":{"p":1,"size":20,"total":1,"pageCount":1}}` | 本人图片分组；type=0 自定义，1 全部；GET 不创建“全部”分组，无组返回空列表 |
| GET /api/v3/me/settings/emoji-groups/{groupId}/emojis | 同分页结构，list 项 `{"id":"分组项ID","emojiId":"图片ID","name":"...","sort":1,"url":"https://..."}` | 本人组中有效图片，悬空和已删除图片不计入；空组返回空列表，未知或他人组统一404 |

四条路由均明确 `auth::profileRead`，只认 v3 Bearer；两类分页沿用 `p` 默认1、`size` 默认20/最大50，sort + oId 稳定排序。目录无账号数据查询；其余三个方法查询当前有效账号。返回均沿用 v3 信封及 no-store / Vary: Authorization。

这四条均为新增入口，未修改旧接口字段或调用行为。旧 `/api/emoji/groups` 的自动建组副作用不带到 v3；旧表情上传/修改/分享/迁移继续沿用旧入口。本轮没有数据库写、SQL迁移、Repository新增、前端或构建产物修改。

## 实际文件与接线

本代理新增：

- `src/main/java/org/b3log/symphony/processor/ApiV3EmotionProcessor.java`：四条显式鉴权 GET，独立注册。
- `src/main/java/org/b3log/symphony/service/ApiV3EmotionQueryService.java`：当前有效账号、本人分组限定、参数化查询、分页、白名单DTO、可恢复503。
- `src/main/java/org/b3log/symphony/util/ApiV3EmotionOptions.java`：原二维picker目录、默认常用列表、深复制与公开JSON目录。
- `src/test/java/org/b3log/symphony/service/ApiV3EmotionQueryTest.java`：真实SQL、HTTP中间件、控制器和Dispatcher隔离测试。
- `tools/api-v3-emotion-test.mjs`：Java25隔离runner；不启动服务、不访问项目数据库、不使用Maven。
- `docs/api-v3-settings-work.md`：本记录。

主代理整合共享文件：

1. `ApiV3Processor.register()` 增加 `ApiV3EmotionProcessor.register()`。
2. `SettingsProcessor` 的原 `private static final String[][] emojiLists = {{...}};` 改为 `private static final String[][] emojiLists = org.b3log.symphony.util.ApiV3EmotionOptions.legacyGroups();`，PC/mobile继续同一FTL数据键；无需修改模板。目录抽取前已与 git HEAD 原数组逐项比较一致：159项含EOF，runner固定原有序别名SHA-256且测试深复制，抽取后仍能检查。
3. 由主代理更新根 `API.md` / `UPDATE_LOG.md`，向用户提醒同步网站API文章。
4. AGENTS 可仅补一条：v3表情读取走 ApiV3EmotionProcessor/QueryService，profile.read仅Bearer、GET不建默认组、目录由 ApiV3EmotionOptions 与旧设置页共享。

## 数据边界与安全

- SQL仅投影必要字段；不读密码、邮箱、手机号、上传者、分享码、内部创建信息。条目计数和查询均带groupId与owner，不能只依赖前置读组检查。
- 当前有效用户从Bearer身份的ID重新查库，Cookie、旧apiKey、userId参数不补身份。
- 无修改请求，不引入CSRF写入口；无远程抓图、无上传下载代理、无SSRF请求。URL仅复用既有 `ApiV3CommunityDto.safeUrl` 输出http/https或同源路径，拒绝脚本、data、协议相对、userinfo、控制字符、反斜线及超长地址。客户端仍应按图片URL/文本渲染，不能把name当HTML。
- 分组名按列上限64、分组项名按默认字符串列255有界投影；超长/控制字符脏历史以空字符串呈现，不改库、不更改正常名称规则。
- 常用别名读取LIMIT4097；超过4096拒绝503，单项沿存储列64且拒控制字符，正常别名不做新集合过滤、不改其顺序。异常非空配置过滤后可以返回空数组，不伪装成默认配置。此为响应资源边界，不是新增写入业务规则。
- RepositoryException不打印SQL/原文驱动异常，不伪装成空列表或默认常用设置，统一503。分页上限限制单次数据量；未增加会改变旧规则的业务频控。
- 无新增CDN引用、可枚举静态目录、source map、上传/下载路径。本次目录只含别名，无版本号资源地址。

## 实际验证

执行 `node tools/api-v3-emotion-test.mjs`，Java25 + 临时SQLite，5组PASS：

1. 真实prefixed SELECT的当前用户、默认常用、无副作用空分组、分页、稳定排序、外组/不存在组404、悬空/删除图片与字段白名单。
2. 异常存储URL、过长和含控制字符名称/别名、常用条数超限fail closed。
3. group owner在前置校验后变化时count/data不泄露；count阶段失败503。
4. 四个真实Dispatcher/auth/controller路由均验证无Bearer401、错误token401、错scope403；伪Cookie/apiKey/userId不越权；验证响应no-store、Vary和当前本人数据。
5. picker 159项原始顺序哈希（runner独立检查）、旧默认列表一致、目录无EOF、深复制隔离。

测试仅替换用户/Token查找、JSON信封和数据库连接适配；查询、四个路由、中间件、控制器、URL投影和模型/既有表情Repository均实际编译。测试环境出现现有UserExt弃用注解及Netty/SQLite运行时警告，无测试失败。

未执行：完整应用编译、Maven、真实MySQL、多请求一致性/性能压力、8080浏览器、真实OAuth Token签发/撤销、全局BeforeRequestHandler/黑名单/验证码链路；不能据此声称生产或完整入口行为已通过。

## 完整人工测试步骤

**联调前必须由用户通过 IDEA 重新编译或重启服务端。未确认前不要根据8080旧响应判断新代码失败。** 不需要数据库迁移；仍须遵守整个v3/OAuth既有部署迁移前提。

1. 通过原PC和移动端 `/settings/function` 配置常用表情；通过两端表情面板创建自定义图片分组、加入两张图片，并记录组与条目排序。重新编译/重启后，用拥有profile.read的Bearer依次调用四条新接口。常用数组应与已保存顺序一致，目录应与原设置页一致且无EOF，组/图片应只返回当前本人及公开投影字段，签名URL查询参数保持。
2. 使用新账号或从未创建图片组的账号：GET groups应为空，连续调用后再看旧面板，无额外创建副作用；无常用配置返回原11项默认列表。已有空组items返回空list与total0。
3. `?p=1&size=1`、第二页和超出末页检查稳定顺序、不丢/重项、total/pageCount；`size=51`、`p=0`、非数字返回422；groupId注入串/过长返回422；合法不存在组和他人组统一404。
4. 去掉Bearer仅带Cookie/旧apiKey；无效Bearer；仅settings.write的Bearer；伪造userId为他人。预期分别401、401、403；有效profile.read始终为本人。账号停用或Token撤销后重新请求应不可读（实际状态由现有Token链路决定）。检查无敏感字段及响应Cache-Control/Vary。
5. 开发测试数据中包含已删除图片/悬空关联的组不应计入items；非法图片URL输出空URL；异常过长名称/含控制字符不下发。正常URL、名称、别名不更改。模拟查询不可用返回503，不能变成空列表/默认常用或输出驱动细节。
6. 回归PC/mobile旧设置页、旧表情面板GET/上传/分组操作，目录与顺序不变，旧GET仍按原逻辑建“全部”组。
7. 在**启用风控的本地配置**分别走未登录入口、有效Bearer入口、黑名单IP进入/test、验证码解封后重试；核对anonymous.viewSkips未新增例外，新增四GET不能让匿名绕过既有链路。开发模式关闭风控时不据此推断生产验证通过。
8. 反漏扫补验：检查四路由鉴权、BeforeRequestHandler、AnonymousViewCheckMidware首访/每5次、CaptchaProcessor解封；公开API/静态目录/CDN版本与sourceMappingURL/上传下载返回路径均纳入整轮发布检查。此代理只静态确认未引入这类新资源或例外，未发真实网络请求，必须由主代理最终说明尚未实测项。

## 剩余设置能力与需确认问题

已有普通设置GET/PUT不重复；当前缺少v3常用表情写入、图片分组/图片条目增改删、URL添加、历史迁移、分享创建/导入。旧流程许多步骤各自提交，不能直接包旧Mgmt方法声称整个动作原子。建议下一轮先做可组合的当前事务核心，再接settings.write和幂等，不开放可指定owner的HTTP入口。

需用户确认再确定写入契约：

- 常用表情PUT使用数组还是旧逗号字符串、空数组是否恢复默认（旧读取为空自动默认，无法表示长期空面板）、非法/重复别名是拒绝还是保留旧静默丢弃、数量上限。现有旧实现只有合法别名去重，无明确API最大数量。
- 分组/条目名称规则旧实现不一致：创建/添加有20字符白名单，部分更新及存储列允许更多；新接口应统一哪组规则、空名语义、排序范围与批量排序上限。不要擅自把旧兼容值当作新默认规则。
- URL添加应允许的协议、来源、长度、是否保留HTTP/签名查询/原URL，是否只是登记或搬运；分享永久快照/导入重复处理/过期和旧迁移再试语义。不得因新增入口隐式扩展远程抓取或把旧多事务包装为原子事务。
- 账号安全设置还存在登录态改密、绑定/修改手机与邮箱、二步验证等其他旧入口；本代理未重复账号分工的注册/找回实现，未创建这些新入口。需要具体产品规则及凭据/Token撤销策略后继续。
