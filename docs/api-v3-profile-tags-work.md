# v3 本人个人标签（2026-10-25）

用户确认的严格规则已实现后端与客户端源码；QQ的独立第一方设置见[QQ工作记录](api-v3-qq-settings-work.md)。本批不表示完整v3或真实应用运行验收完成。

## 修改文件与行为

后端源码：

- `src/main/java/org/b3log/symphony/service/ApiV3ProfileTagsService.java`：本人当前有效账号读取与幂等设置，owner→统计namespace→当前目录锁；新标签、creator/type0、self/type2、标签总数、userTags/更新时间与settings.tags回执同严格根提交。新标签使用Repository.add实际返回ID建立关联，防止仓储重新分配ID后出现悬空关系。清空只移除本人的type2，保留全局标签、creator/type0、关注/type1和他人关系；不改变QQ或其他资料。
- `src/main/java/org/b3log/symphony/util/ApiV3ProfileTagInputs.java`：原始字符串与结果均≤128个UTF-16字符；中英文逗号、顿号、分号与旧空白清理，逐个原项验证后再按现有规范化和大小写去重，最终最多4项。普通项≤12，现有白名单仅豁免单项长度。空白清空、纯分隔符非空输入422；无效项不丢弃、超限不截断，保留词与现有内容审核失败整笔拒绝。
- `src/main/java/org/b3log/symphony/model/Tag.java`：仅提取共用normalizeAlias，旧normalize调用同一函数，保持旧格式化行为。新服务从当前SQL读取有效图标标签、标题/URI规范化，避免依赖旧缓存；图标最长优先、同长按oId稳定处理，目录冲突409、禁用项422、超技术预算503。
- `src/main/java/org/b3log/symphony/processor/ApiV3ProfileSettingsProcessor.java`：在QQ两路基础上新增GET/PUT `/api/v3/me/settings/tags`，profile.read/settings.write与原写入门禁、闭合JSON及幂等键。总register已通过ApiV3Processor接入，不接受用户ID。没有新增表、Repository或迁移。

服务先检查现有ProfileWriteQuota再做目录工作，审核使用现有CensorFactory组合资料规则。ArticlePublicationCaches包住严格事务，Tag/Option仓储缓存更新延后到根事务结束，用户设置缓存随后刷新或失效。新增标签的referenceCount等初值沿旧逻辑为0，仅递增statisticTagCount，不伪造文章引用。旧标签写入未全部使用同一namespace，不能宣称新旧全局唯一、无死锁或跨实例缓存强一致；事务重试和失败回滚仍有必要。

客户端工作区 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-profile.ts`：独立本人tags GET/PUT、严格响应字符串；不并入旧profile/userTags请求。
- `src/components/settings/OwnerProfileSettings.vue`：独立标签表单、提示、读取/保存/错误与幂等状态；以成功响应规范化值回填，未知结果同内容保留key。凭据变化清状态并隔离迟到响应；服务器失败不假报成功或覆盖其他表单。
- `tools/v3-profile-test.mjs`：追加契约、规范化响应、同键重试及身份边界场景。
- `README.md`、`AGENTS.md`、`docs/V3_MIGRATION.md`：记录QQ/标签现状与未验范围。共享响应式组件覆盖桌面与移动布局，真实移动设备未验。

测试与文档：

- `src/test/java/org/b3log/symphony/service/ApiV3ProfileTagsTest.java`、`ApiV3ProfileTagsMysqlTest.java`；复用`ApiV3QqSettingsTest.java`及既有身份申请测试底座。
- `tools/api-v3-profile-tags-test.mjs`、`api-v3-profile-tags-mysql-test.mjs`；`api-v3-qq-settings-test.mjs`改为共用编译入口的--qq-only委托，避免QQ控制器新增tags依赖后漏编。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-route-inventory.md`、`docs/api-v3-progress.md`、`docs/api-v3-qq-settings-work.md`与本文同步。

## 实际验证与适配边界

Java25定向编译，不执行Maven。最终`node tools/api-v3-profile-tags-test.mjs`通过标签7组、QQ5组及实际Latke/XML传输错误过滤测试，日志`/tmp/rhythm-v3-profile-tags-tests.log`。覆盖原项/总长、分隔符/别名/白名单、当前目录/冲突/禁用、精确角色保留词、所有写入与提交故障回滚、短暂故障重试、丢ACK、旧key不覆盖后来值、只清本人self、缓存回调阶段及真实Dispatcher门禁。SQLite会去掉FOR UPDATE，不能单独证明行锁。

最终`node tools/api-v3-profile-tags-mysql-test.mjs`在本机已安装mysql:8.4镜像启动一次性独立数据库，通过同7组标签+5组QQ，以及same-key/shared-tag/different-key/clear/failure五组独立JVM竞争，观察实际InnoDB LOCK WAIT并验证最终设置/关联/统计/回执，日志`/tmp/rhythm-v3-profile-tags-mysql-tests.log`。断言引擎InnoDB及REPEATABLE READ，保留实际FOR UPDATE，运行后移除容器。不会连接开发或生产业务数据库。

隔离工具编译实际服务、严格事务、幂等、证明/控制器与Tag/Option/UserTag仓储源码；DAO写入端口为可注入故障的SQL适配，沿repository.json字段建立测试DDL，非原部署全部索引。cache/censor/quota采用明确适配器，不能视作完整IoC、真实缓存发布、真实内容审核服务或实际限额运行验收。MySQL并发仅覆盖新v3写入，不证明所有旧写入串行。

客户端`corepack yarn node tools/v3-profile-test.mjs`现7组、`corepack yarn run build`类型/client/SSR通过；日志`/tmp/rhythm-v3-client-profile-tests.log`、`/tmp/rhythm-v3-client-profile-build.log`。生成62个JS的js-version-audit零命中，`/tmp/rhythm-v3-client-profile-tags-js-audit.json`。未直接编辑生成CSS/min.js。

实际IAB本地合成HTTP/WS夹具：`JS；ES、JavaScript`规范化为`JavaScript,Elasticsearch`；五项输入明确失败、重读仍是原值；清空成功，再次保存规范化结果。Hydration日志为空，截图`/tmp/rhythm-v3-client-profile-tags-proof.png`。QQ另有前导0保存/重读、非法科学计数拒绝与清空截图，见QQ记录。没有真实账号/生产写入；夹具进程和测试页已关闭。机械清单811全仓路由/398浏览器引用，v3总264（263HTTP+1WS），统计口径不同。

## 人工完整验收

1. 先由用户在IDEA重新编译或重启并明确确认，本批有新service/controller依赖与Tag helper，建议完整重启；已有v3迁移必须部署。本批不新增SQL。客户端指向localhost:8080，启动127.0.0.1:5173，确认加载新构建资源后再联调。
2. 本人登录→设置→读取资料/标签/QQ→提交`JS；ES、JavaScript`，结果按当前目录规范化、去重→刷新保持；单独修改标签不能改变QQ、昵称、简介或头像。清空标签后刷新为空，全局标签与其他人引用仍存在。
3. 验证4个唯一项成功、5项失败；普通12/13字符边界、Elasticsearch白名单例外、原始128/129边界、大小写去重、重复分隔符、仅分隔符、HTML/emoji/控制字符拒绝。普通用户保留词失败，真实管理员按角色规则；真实内容审核失败无半写，旧异常值可读但写入必须修正。
4. 检查标签不存在时新建记录/creator/self/统计与设置同提交；已有标签只关联，不重建；清空只删本人type2。用专用测试数据注入标签、关联、统计、用户、回执与commit失败，所有变化回滚；同key恢复一次，异内容409，后来新key保存后旧key只返原结果、不覆盖当前设置。两个会话/两个实例同key、同名标签与清空竞争，检查无悬空关系/统计重复，并观察旧入口混用与缓存实际刷新。
5. 无Bearer、仅Cookie/apiKey、缺scope、失效token/epoch、停用账号读写应拒绝；不能指定他人userId。QQ另须第一方，第三方相同scope仍403；公开资料/第三方既有profile不能新增QQ。切换账号时挂起旧读写，确认新账号不出现旧标签/QQ或成功提示，重试不跨身份复用key。
6. PC和移动设备从入口走读→改→保存→刷新→失败恢复；浏览器断网/丢ACK、服务端422/429/503与身份退出均要有明确结果；不得回退为假默认或使用旧apiKey空值静默成功。
7. 回归Before黑名单→/test、首访/周期验证、validateCaptcha解封、开发关闭风控、匿名viewSkips无扩大；检查CSRF/Bearer边界、批量请求、代理日志、静态CDN版本/sourceMappingURL/目录枚举及上传下载返回路径。本批没有完整应用和设备环境证据，这些不能默认为通过。

## 安全与反漏扫复核

- 未鉴权/越权：新增路由Bearer和scope先行，actor仅来自认证attr，service当前锁有效owner；没有anonymous.viewSkips变化。保留全局Before黑名单/匿名验证码核心，未新增裸匿名路由。实际新IP、黑名单、解封和Cookie/apiKey拒绝需完整应用回归。
- CSRF：沿既有v3 Bearer写入门禁，不以Cookie或旧apiKey补身份；未向兼容旧客户端的接口生硬添加页面CSRF。完整真实HTTP代理环境未验。
- XSS/注入/SSRF：每个原项与最终值均白名单，SQL值参数化，响应仅tags；Vue文本绑定、标签URI编码，不抓远程内容，不新增上传下载路径。
- 滥用：请求体/字符串/项数与目录查询预算有界，沿原写预算和ProfileWriteQuota，超技术预算明确503；真实负载/分布式限额未验。
- 敏感信息：只投影本人tags，不返回完整用户/QQ或认证字段；QQ单独第一方和日志保护保持。故障统一映射，不回传SQL异常。
- 静态防漏扫：本批客户端生成JS定向扫描零命中，无新增公网CDN资源。未做全站CDN抓取、目录枚举、实际上传/下载路径和代理日志扫描，不宣称整站防漏扫完成。
