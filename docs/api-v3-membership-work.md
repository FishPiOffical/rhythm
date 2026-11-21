# v3 会员读取交付记录（2026-10-20）

## 本轮实现与接线

新增两个只读入口，保持旧接口与页面行为：

| 接口 | 现有scope | 数据来源与返回 |
| --- | --- | --- |
| GET /api/v3/me/membership | profile.read | 仅Bearer本人，重新查询当前有效账号，再查询其state=1会员记录；active/levelCode/expiresAt/config |
| GET /api/v3/membership/levels | content.read | 有界分页、price/oId升序；id/code/name/price/durationType/durationValue/benefits |

推荐在 `ApiV3Processor.register()` 的其他Processor注册处加入 `ApiV3MembershipProcessor.register();`。本子代理没有修改共享注册、API.md、UPDATE_LOG.md、AGENTS.md或progress；这些由主代理集成。

实际文件：

- `src/main/java/org/b3log/symphony/processor/ApiV3MembershipProcessor.java`：route/controller与现有Bearer scope；`registerRoutes` 是生产与隔离测试共用的实际注册函数。
- `src/main/java/org/b3log/symphony/service/ApiV3MembershipQueryService.java`：当前账号与owner范围参数化SQL，分页目录，数据库异常统一503。
- `src/main/java/org/b3log/symphony/util/ApiV3MembershipDto.java`：闭合字段白名单、有界纯文本、受限配置JSON投影及数值检查。
- `src/test/java/org/b3log/symphony/processor/ApiV3MembershipTest.java`：真实注册/路由匹配、控制器、scope中间件及SQLite SQL专项。
- `tools/api-v3-membership-test.mjs`：Java25独立编译与运行入口。
- 本文件。

## 读取语义与边界

- 本人身份仅取 `ApiV3Requests.viewer` 的已认证Bearer；忽略query里的userId/lvCode/apiKey，不接受Cookie代替Bearer。
- 查询本人前读取当前数据库账号，需userStatus=有效；账号失效/不存在404。不使用旧MembershipCache或Sessions页面状态。
- 无state=1记录、记录到期（expiresAt非0且不大于当前时间）均返回active=false、levelCode空、expiresAt=0、config空对象；expiresAt=0为永久。
- 当前owner有多条state=1记录时返回503，不任意选一条；损坏到期值返回503，不伪装为未开通。
- 目录每页默认20、最大50，标准list/pagination；无等级时正常空列表，超末页同样空列表。count与分页是两次只读查询，不宣称并发编辑下的事务快照。
- price为0..2147483647，durationValue为1..2147483647，expiresAt为非负long；损坏/溢出/小数返回503。目录price是档位标价，不是优惠券后的结算报价。
- 保留原字段durationType/durationValue。模型说明durationValue为天数，当前`MembershipMgmtService#calcExpires`确实忽略durationType并把value当天数、自次日0点起算；新读取接口不计算/变更有效期，不承诺新的日/月/年换算。
- `config`和`benefits`均为JSON对象投影：只保留Boolean类型的bold/underline/autoCheckin/metal/jointVip，非负int类型checkinCard，以及#RRGGBB或现有8种固定效果名的color。固定效果为rainbow/neon/fire/ocean/forest/sunset/metal/galaxy。不返回任意扩展键、嵌套对象或原始JSON字符串。
- 可选配置为空/畸形、重复键、超过102400 UTF-16或过深时投影为空；单个已知键类型错误或color不在展示白名单时略去。此为新只读DTO规则，不修改旧写入/展示规则；不能把略去值解读为用户设置了false。
- 等级名称/周期/代码等只输出有界纯文本，不返回HTML。未返回会员记录ID、ownerId、优惠券、激活/支付/退款记录、createdAt/updatedAt、运维扩展或用户对象。
- 不写表、不调用计费/赠包/通知/远程服务，不需要新表或迁移。两路均走ApiV3Requests私有响应头。

## 未实施的写能力与待确认规则

旧入口：`POST /api/membership/open`调用MembershipMgmtService.openMembership；`PUT /api/membership/config`依赖Controller的@Transactional更新本人配置。旧匿名`/api/memberships/configs`会投影所有激活会员配置，此轮没有改变该兼容接口，也没有为v3复刻它。

开通已有扣款/会员/activation写入事务，但须先共用当前锁与账户锁、严格提交及幂等，不能直接套旧自提交方法。starter bag在提交后发放，失败恢复需单独明确。旧优惠券无效时按1.2倍惩罚，times=0按原价，读取/更新券异常又退回原价；开通前当前会员未过期会拒绝，首次与复用旧记录对configJson处理不同。v3不能在未说明时改变这些计费行为。

待用户确认：

1. v3开通是否原样保留无效券加价惩罚、赠包及配置初始化/续期规则，是否允许重构旧入口共用原子核心。
2. 可写配置的明确键/类型/长度、HTML/Markdown与VIP4 color任意类名规则；旧benefits是动态JSON模板（必须键全且类型一致），不等同本次只读展示白名单。
3. durationType的名称与实际按天计算是否保留，是否提供报价/确认流程及其有效期。
4. 开通、续期、退款、后台延长的并发/幂等语义和跨进程赠包恢复；本轮不增加收费或配置写路由。

## 已执行测试

`node tools/api-v3-membership-test.mjs`：Java25专项 **7组通过**，并通过`node --check`。runner编译真实Processor/Service/DTO/Requests/Auth/JSON及现有Membership模型/Repository，在内存SQLite执行生产SQL。生产registerRoutes经真实Latke Dispatcher.mapping与RouteHandler匹配后，反射调用匹配到的实际Controller。

适配边界：Token持久查询提供固定测试身份（hasScope执行生产函数），主ApiV3Processor信封捕获为测试属性，UserRepository只以最小AbstractRepository壳替代；真实网络HTTP监听、BeanManager应用扫描、完整Token数据库与顶层风控不在专项范围。未做Maven/真实数据库/8080/生产或支付请求。旧UserExt弃用注解及Netty/SQLite运行时会出现既有Java25警告，不是测试失败。

7组覆盖：

1. 实际路由不存在写方法；缺失/无效Bearer401、scope不符403；拒绝发生在SQL之前。
2. 实际owner SQL、Cookie/apiKey/query不能代查，切换Bearer只读各自记录，数据实时重读，嵌套白名单与no-store/Vary。
3. 永久/过期/失效/缺失会员，当前账号停用，重复记录和损坏到期字段。
4. 配置畸形/重复键/超大/过深JSON，错误类型、嵌套敏感对象、非法颜色。
5. 53档位分页/稳定排序/准确数量/越末页，剥离HTML与运维字段，畸形benefits。
6. 非法分页在SQL前422；user/membership/level任意查询失败503，空目录正常成功。
7. 免费档位、int上下界、非法/溢出/小数price和duration、到期long上界与非法值。

## 安全与反漏扫

| 关注项 | 结论与验证边界 |
| --- | --- |
| 匿名与公开鉴权 | 两个入口均显式scope Bearer，专项已验证；没有新增匿名能力 |
| 越权 | 本人只取已认证viewer并当前查有效账号；owner参数化SQL及代查攻击专项通过；档位无个人信息 |
| CSRF | 两个GET只读，不新增Cookie/apiKey写路由 |
| XSS | 任意配置/HTML不透传，颜色为闭合展示值；客户端仍应按JSON文本渲染 |
| SQL/SSRF | 只用受校验actor与有界分页参数绑定，不接受URL或远程查询 |
| 批量滥用 | 目录分页最大50、本人查询有界；走顶层Firewall，无新限流配置/远程开销；数据库扫描性能需真实规模评估 |
| 敏感数据 | 查询列与嵌套DTO双重白名单，不暴露整行/configJson/benefits原文或错误原因 |
| 黑名单/验证码 | 未修改BeforeRequestHandler、AnonymousViewCheckMidware、validateCaptcha；源码基线保持，未实际启用风控验证 |
| anonymous.viewSkips | 未修改，当前配置无新v3通配放行；无绕过该列表的匿名新入口 |
| CDN/版本/sourceMappingURL/目录枚举 | 未新增/修改公网静态资源、CDN URL、source map或文件路由；没有做全库CDN网络复扫 |
| 上传/下载返回路径 | 不涉及文件URL/上传/下载，旧链路未改；全站上传下载回归未执行 |

## 人工用户链路（待执行）

**联调前先明确提醒并确认用户已通过IDEA重新编译或重启服务端。不能把浏览器旧Java行为判为本轮实现失败。**

1. 从v3登录/授权获取profile.read/content.read，GET `/api/v3/me/membership`，与PC/mobile `/vips`当前本人有效等级/到期时间核对；有配置时返回已知typed键，不返回configJson原文或他人资料。
2. 换用另一测试账号Bearer并附原账号userId/apiKey/Cookie，结果必须仅对应Bearer账号；缺Bearer/损坏/过期/撤销token401，缺scope403；服务端停用账号后拒绝本人读取。
3. 分别使用无会员、永久、到期、后台失效测试记录核对active/levelCode/expiresAt/config。隔离库注入畸形配置应只略去可选显示值；重复active记录或坏expiry503。不能在生产造数据。
4. GET `/api/v3/membership/levels?p=1&size=50`，与旧档位的标价/周期核对；第二页无重复/漏项，非法size/p422；模拟数据库不可用503，不能成功返回空目录或未开通。
5. 同时回归PC/mobile `/vips`原配置/开通入口未变。本轮不经v3开通、不扣费、不操作后台会员写入。
6. 风控开启的隔离环境从黑名单IP访问应先跳/test，验证码解封后再按Bearer链路认证；检查匿名首访/计次验证码以及开发模式关闭风控行为。此项未实际执行。

## 可合并至公开API.md的片段

### 查询本人会员状态

`GET /api/v3/me/membership`

读取当前授权用户的会员状态。需要`profile.read`权限。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer访问令牌 | Bearer xxx |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/me/membership' \
  --header 'Authorization: Bearer xxx'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0为成功 | 0 |
| msg | 提示信息 | success |
| data | 会员状态 | {} |
| - active | 会员是否有效 | true |
| - levelCode | 等级编码 | VIP4 |
| - expiresAt | 到期毫秒时间戳，0表示永久 | 1792454400000 |
| - config | 可显示的本人会员配置 | {} |
| -- bold | 昵称加粗，可选布尔值 | true |
| -- underline | 昵称下划线，可选布尔值 | false |
| -- color | 昵称颜色或效果，可选 | #ff0000 |
| -- autoCheckin | 自动签到，可选布尔值 | true |
| -- checkinCard | 免签卡配置数量，可选非负整数 | 3 |
| -- metal | DIY勋章配置，可选布尔值 | true |
| -- jointVip | 联合会员配置，可选布尔值 | false |

> 未开通、已失效或已过期时返回active=false、levelCode为空、expiresAt=0、config为空对象。配置只包含支持显示且类型有效的字段，字段缺失不等同于false。颜色支持#RRGGBB及rainbow/neon/fire/ocean/forest/sunset/metal/galaxy效果名。

### 查询会员档位

`GET /api/v3/membership/levels`

分页读取会员档位及标价。需要`content.read`权限。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer访问令牌 | Bearer xxx |
| p | 页码，默认1 | 1 |
| size | 每页数量，默认20，最大50 | 20 |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/membership/levels?p=1&size=20' \
  --header 'Authorization: Bearer xxx'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0为成功 | 0 |
| msg | 提示信息 | success |
| data | 档位分页 | {} |
| - list | 依标价、ID升序排列的档位列表 | [] |
| -- id | 档位ID | 1792454400001 |
| -- code | 等级编码 | VIP4 |
| -- name | 等级名称 | VIP至尊版 |
| -- price | 标价，单位积分 | 10240 |
| -- durationType | 周期名称 | 月卡 |
| -- durationValue | 周期值，单位天 | 30 |
| -- benefits | 支持显示的权益配置，字段同本人config | {} |
| - pagination | 分页信息 | {} |
| -- p | 当前页码 | 1 |
| -- size | 每页数量 | 20 |
| -- total | 档位总数 | 12 |
| -- pageCount | 总页数 | 1 |

> 标价不包含优惠券等结算调整。本接口仅提供读取，不会开通会员或扣除积分。

建议UPDATE_LOG条目：新增v3本人会员状态与分页会员档位读取，提供有效状态、到期时间、标价/周期及支持显示的配置字段；暂不提供会员开通或配置修改。

建议AGENTS简短链路（由主代理决定是否合并）：v3会员只读在ApiV3MembershipProcessor/QueryService，me需profile.read且当前DB有效本人，levels需content.read；配置只返回typed白名单，不裸返configJson/benefits，畸形可选配置略去、核心数值/重复状态/DB失败503；开通/配置写入未开放。
