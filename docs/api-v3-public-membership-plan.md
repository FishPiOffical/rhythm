# v3 公开会员配置资源盘点（2026-10-24）

## 结论与范围

已有v3覆盖会员档位、本人会员状态、本人展示配置修改及本人积分开通；“第三方展示他人的会员昵称样式”尚未覆盖，不能用本人GET或已存在旧HTML展示来判定完成。旧全站会员配置导出不等于已批准的新公开契约，不能直接复刻任意configJson或按其无门禁方式注册。本文只读盘点，不增加路由/字段，也不缩小剩余范围。

## 旧路由与真实响应

入口在`src/main/java/org/b3log/symphony/processor/MembershipProcessor.java#register`，经Router主注册可达。

| 旧入口 | 实际注册及行为 | 与本次能力关系 |
| --- | --- | --- |
| GET `/api/membership/levels` | 第89行，无路由门禁；listLevels调用MembershipQueryService.listLevels，按price/oId排序，返回完整档位行，并增加openedMemberCount。计数仅筛state=1，不按expiresAt排除过期，查询异常计数记0 | 商品档位已有v3对应；完整原benefits、计数与返回形状并非完全等价，不能无评审补进v3 |
| GET `/api/memberships/configs` | 第91行，无路由门禁；listActiveConfigs第183行投影每项userId和原始configJson，信封code/msg/data数组。方法注释写“Admin”，但实际未执行管理员检查 | 真实全站配置资源，未被v3复刻；注释不能作为访问控制证据 |
| GET `/api/membership/{userId}` | 第92行，无路由门禁；getUserMembershipStatus调用getStatusByUserId，会员有效时返回原会员行，无效返回state=0；不是只bold/underline/color | 旧任意用户会员状态资源；v3仅本人，不等价于公开他人能力 |
| POST `/api/membership/open` | loginCheck；本人积分开通 | v3已提供本人开通，不是公开配置读取缺口 |
| PUT `/api/membership/config` | loginCheck；本人configJson更新 | v3已提供受限展示字段修改，不是公开配置读取缺口 |

`MembershipQueryService.java#listActiveConfigs`（第160行）按state=1及updatedAt降序读取会员记录，过滤expiresAt非0且已到期记录；同时移除过期缓存、写有效缓存。无分页参数，没有在此查询当前userStatus；不应把“激活会员记录”表述成“当前有效账号且已选择公开展示”。底层getList存在框架读取行为，未验证其超大数据上限，不能将注释“一次性所有”当作无限量完整性保证。

原configJson为任意JSON字符串，这两个旧公开接口没有使用`ApiV3MembershipDto.configuration`白名单；可能含购买/后台维护的扩展权益键。本次不对存量响应作未经授权的兼容变更，也不据存量暴露推定新v3可以继续公开。

## PC、移动端与第三方调用链

1. 聊天室：`ChatroomProcessor.java`约2001行直接调用`MembershipQueryService.listActiveConfigs`，投影userId/configJson为dataModel.vipUsers；不通过`/api/memberships/configs` HTTP。PC/mobile的`chat-room.ftl`及`chat-room-2.ftl`四模板均将结果写入`Label.vipUsers`。`js/chat-room.js`约1860行与`chat-room-2.js`约1912行解析该列表，按消息userOId匹配，只实际使用bold、underline、color渲染昵称。代码还解构lvCode但该页面投影不提供它，不能据此声称消费会员档位。
2. 个人主页：`UserQueryService#getUserByName`约499行附加完整membership，PC/mobile的`home/home-side.ftl`按configJson.bold/underline/color设置昵称样式。这里没有调用旧全站配置HTTP，也不表示v3公开profile已有样式字段。
3. 会员购买页：PC/mobile `vip/index.ftl`都引用PC路径的`vip/js/vip.js`，脚本GET `/api/membership/levels`；simpleProcessVIPData使用lvCode/lvName/benefits、price/durationType/durationValue/oId构造年/月档位。该脚本未使用openedMemberCount。页面本人状态由MembershipProcessor.showVipPage服务端提供。
4. 后台：`js/vip-admin.js`与`skins/classic/pc/vip/js/admin.js`使用档位接口；`js/medal.js`管理授勋操作按任意用户查询旧会员状态，自动填入expiresAt。这是后台便利功能，不能据此决定第三方公开资源必须返回他人到期时间。
5. 第三方：仓库内源码（排除min.js/target/node_modules）未找到直接请求`/api/memberships/configs`的前端调用；不能由此推断外部客户端无人使用。`OpenIdProcessor`已有GET `/openid/user/membership`，使用membership.read读取已授权本人，buildMembershipData返回state/lvCode/expiresAt/configJson；不支持他人或全站配置。API.md未找到旧全站configs专门文档条目，不代表没有外部依赖。

## 已注册v3与公开展示差异

总入口`ApiV3Processor.register`已调用Membership、MembershipSettings、MembershipOpening三个Processor。

| 现有v3 | 身份/字段 | 是否覆盖缺口 |
| --- | --- | --- |
| GET `/api/v3/membership/levels` | content.read；分页商品档位，ApiV3MembershipDto.level返回id/code/name/price/durationType/durationValue及清洗benefits | 已覆盖商品目录核心能力；不应为复刻旧URL再造一个同功能列表 |
| GET `/api/v3/me/membership` | profile.read；当前本人有效账号，直接SQL读取会员，返回active/levelCode/expiresAt/config | 已覆盖本人状态与展示配置；无法按其他用户查询 |
| PUT `/api/v3/me/membership/config` | settings.write及幂等；仅已支持的bold/underline/color | 已覆盖本人展示设置写入；写入后第三方仍缺读取他人样式渠道 |
| POST `/api/v3/me/membership` | activity.write及幂等；积分/优惠券开通 | 已覆盖本人开通，不是公开展示读取 |
| GET `/api/v3/users/{userName}` | optionalProfile及匿名盾；ApiV3DiscoveryQueryService.profile→author白名单，只id/userName/userNickname/userAvatarURL、userIntro/userURL | 没有membership或昵称样式，未覆盖 |
| v3文章/评论作者与chat author | ApiV3ContentUtil.authorDto、ApiV3ChatMessageDto显式白名单，均无会员配置 | 未携带展示样式；当前client无法只凭该DTO恢复他人VIP昵称效果 |

`ApiV3MembershipDto.configuration`允许已知类型的bold、underline、autoCheckin、metal、jointVip、checkinCard、color；color限定#RRGGBB及八种已知效果。这个白名单用于本人/商品权益，不能整体当作“公开他人样式”白名单：autoCheckin/权益类键是否公开未获确认，expiresAt与levelCode也不能因本人接口已有而自动公开。

真正缺能力：第三方在聊天消息/文章评论作者/个人页展示其他账号当前允许公开的会员昵称效果，是否同时展示会员状态/档位尚待明确。获取全站会员名单、任意configJson、到期时间、购买/后台字段是额外范围，不是实现这个场景的天然必需条件。单用户、按本页用户批量或全站分页方案各有性能和枚举差异，当前没有选定。

## 最少待确认问题

以下两问足以决定公开契约，不再重复询问已确认的本人设置或开通规则。

1. 他人公开展示需要哪些字段：仅bold/underline/color，还是还要有效会员标记、levelCode、expiresAt？不确定项逐个确认，不默认继承本人config或原configJson。
2. 访问对象与门禁：只查询客户端已展示的指定用户（可批量），还是确实需要全站分页会员名单？是否允许匿名访问，还是要求Bearer profile.read？当前旧公开路由的无门禁不作为新路由授权。

其他诸如分页大小、批量技术上限、SQL实现、回包命名可在确认范围后按项目现有约定设计；若需隐私开关，应另明确旧展示兼容影响，不自行新增或假设存在。

## 后续实现与验证边界（待授权范围确认后）

- 新资源只读SQL/字段白名单；先确认当前目标有效账号、有效会员和到期语义，损坏/重复会员记录不选任意一条假装正确。不要调用旧listActiveConfigs把整站任意配置读出后全量下发。
- 匿名方案必须显式anonymousViewCheckMidware；Bearer方案须显式auth scope。保持BeforeRequestHandler黑名单、/test、/validateCaptcha及anonymous.viewSkips现有约束，不为公开资源增加绕过。
- 新参数有界、去重、严格用户名/ID校验，SQL参数化；展示键严格类型和color白名单，客户端文本编码，不能把任意旧class/style直接拼HTML。GET无数据库写事务需求，不抓外部URL，响应不含订单/内部会员行ID/凭据或任意configJson；评估名单枚举及批量请求滥用。
- 设计先说明新响应字段及对第三方影响，不擅自把已批准接口加字段或改旧输出。需要扩展已有作者DTO时按项目接口兼容要求先说明并获同意；独立读取资源也要同步API.md/UPDATE_LOG.md，但本次盘点不改这些公共文档。
- 人工链路：用户IDEA重编译/重启后，PC/mobile与第三方选有效、过期、永久、非VIP及停用目标，读取→渲染→本人改样式→重新读取验证最新效果；覆盖未知/重复/损坏记录、权限不足、非法批量输入、跨账号及匿名风控。全站分页若批准另测连续性和耗时。
- 没有新增静态资源/CDN/sourceMappingURL/上传下载路径。未来实际运行仍需做目录枚举、静态基线、黑名单/验证码链以及浏览器XSS/CSRF回归，不能由本次源码审查默认通过。

## 本次实际操作与限制

只读扫描注册、服务、DTO、PC/mobile模板和未压缩JS，唯一写入为本计划文件。未改业务代码/共享文档、未新增入口、未运行Maven、未访问业务数据库或生产接口，未执行浏览器/完整HTTP/真实MySQL测试，也没有证明外部第三方调用情况。

## 2026-10-24 实施更新

用户已确认仅三展示字段、profile.read、指定ID批量1..100且重复422；现GET /api/v3/membership/styles已总接线。前述待确认问题已关闭，当前不提供全站名单或到期时间。真实文件/5组隔离测试/完整人工链及未验项见[样式工作记录](api-v3-membership-style-work.md)，旧全站接口未改造。
