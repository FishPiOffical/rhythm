# v3 会员展示配置交接（2026-10-22）

内部实现和验证记录，不能整体复制到公开API文档。主代理统一维护总注册、API.md、UPDATE_LOG.md、AGENTS.md和汇总。

## 实际文件

- `src/main/java/org/b3log/symphony/processor/ApiV3MembershipSettingsProcessor.java`：PUT配置、settings.write/共享写门禁、4096字节前置限制、字段白名单。
- `src/main/java/org/b3log/symphony/service/ApiV3MembershipSettingsService.java`：当前actor→回执→同lvCode全部level稳定锁→当前membership，按列更新configJson/updatedAt与幂等回执同严格根保存点；事务结束后清会员缓存，失败不倒转成功。
- `src/main/java/org/b3log/symphony/util/ApiV3MembershipConfiguration.java`：开通与配置共用的纯defaults/merge/输入/展示投影；开通时由服务器模板初始化权益，配置仅保留存量权益。
- `src/test/java/org/b3log/symphony/service/ApiV3MembershipSettingsTest.java`：生产SQL/路由/鉴权/controller、实际JdbcTransaction/严格根/保存点故障与缓存适配器的10组专项。
- `tools/api-v3-membership-settings-test.mjs`：Java25临时目录编译运行，不启动实际应用，不连接业务数据库。
- 本文件：契约、证据、限制与完整人工链路。

接线：`ApiV3MembershipSettingsProcessor.register();`。无新表/Repository/SQL/配置或前端变更。不修改旧MembershipMgmtService/MembershipQueryService/Cache或原读取DTO。

## 规则与共享工具

用户已确认仅开放档位支持的bold/underline/color，其他权益只读；普通颜色仅#RRGGBB；精确VIP4_YEAR/VIP4_MONTH额外允许rainbow/neon/fire/ocean/forest/sunset/metal/galaxy，不用contains或纯VIP4示例扩权。

PUT为非空部分更新，字段须在服务器benefits模板中，bool必须真实JSON boolean，color精确HEX或允许效果，首尾空白/HTML/JS/CSS片段拒绝；未提交展示字段及服务端权益全部保持。合法稀疏存量对象不自动补缺失权益。默认模板空白解析为空对象，模板展示字段不合法503，不把red等值映射为新值。

共享静态API：

- `defaults(JSONObject level)`：解析level.benefits为完整深拷贝JSON，验证lvCode及展示schema；用于明确levelId的服务端开通初始化。
- `validateChanges(JSONObject)`：至少一项展示字段的严格输入；用于幂等哈希。
- `merge(JSONObject level,String storedConfig,JSONObject changes)`：完整保留合法stored对象，仅合并提交展示字段；空白字符串以空对象为基底，仅写显式提交展示字段；不补入其他展示值或只读权益，不合法JSON503；结果仍受字节上限。
- `displayDefinition(JSONObject level)`：仅三个展示字段是否存在及其类型schema，不比较默认值；`publicConfig(JSONObject)`仅返回typed展示字段。

同lvCode不同durationType合法，旧membership却不保存levelId/周期，因此不能LIMIT1挑模板。当前读取所有同lvCode档位（最多64项，LIMIT65探测），各自支持的展示字段存在性/类型schema须一致，合法默认bool/颜色值可以不同。允许只读福利差异；合法stored逐键保留，空白stored也不从模板补权益，因此不必判定完整福利模板等价。defaults完整初始化仅用于明确levelId的新开通。主代理最终规则审查已用此方案替代先前空配置fallback决策。

锁内先投影lvCode，锁完整level组，再当前锁membership重验lvCode/owner/state/expiry，变化失败回滚而不扩展锁集合。只有state=1且expiresAt=0或未来可新修改，过期/停用/无记录404；重复owner行/坏存储/缺档位/不等价模板503。每次和幂等重放先当前锁有效actor；重放恢复旧回执，不再次合并或覆盖后续会员数据。

结果只有`config`及展示字段，不返回raw benefits、完整configJson、激活记录、手机号/凭据/内部键。无积分、卡片、期限、档位或state写入。

## 实际验证

2026-10-22执行`node tools/api-v3-membership-settings-test.mjs`，**10组PASS、进程退出0**，日志`/tmp/rhythm-v3-membership-settings.log`。

1. 实际PUT/Bearer/scope/写门禁/controller/SQL，锁序及部分更新，所有只读字段/会员元数据/积分保持。
2. 同键恢复、不覆盖之后的配置、同键冲突、账号停用及identity到root之间变更拒绝。
3. 严格字段/boolean/HEX、两个精确VIP4代码八效果、不含MY_VIP4或纯VIP4扩权；服务器defaults深拷贝。
4. 多周期只读福利及合法展示默认值差异合法，但支持字段/类型schema差异拒绝；空白及合法稀疏配置仅写提交展示字段，不补权益或未提交展示项。
5. 过期/停用/重复/损坏会员及锁前后tier变化整笔失败；永久有效会员可修改展示。
6. Cookie/apiKey/owner/configJson伪造、缺Bearer/scope/key、重复/额外JSON、UTF-8字节超限拒绝。
7. placeholder/用户/level/membership读、业务更新、回执、真实JDBC提交及savepoint创建/释放故障，配置和回执一起回滚，同键恢复；缓存不在事务中失效。
8. 真实commit成功但确认丢失，旧回执恢复不覆盖后续更新；缓存失效抛错不反转已完成提交。
9. primitive没有根事务则拒绝，局部保存点回滚保留外层无关工作。
10. 超大空白/UTF-8模板与旧配置、非法Unicode以及超技术预算level组均失败，无任意发权益或截断。

生产service/SQL/controller/auth/写门禁/幂等/严格JdbcTransaction为真实代码；SQLite去FOR UPDATE、Token身份、仓储连接/信封及MembershipCache为显式适配器；复用PrimaryTest的严格事务故障fixture。没有Maven、完整IoC/应用、业务MySQL、8080、浏览器或生产验证。不是实际多连接行锁/跨实例缓存一致性证据。

## 完整人工链路

联调前须通过IDEA重编译或重启服务端，不能把运行中旧Java行为当作新代码。

1. A/B取profile.read/settings.write，GET档位与本人会员；A当前有效会员PUT `{bold:true}`→200→GET本人状态→PC/mobile VIP配置页/昵称展示核对；未提交下划线/颜色保持，自动签到/礼包等权益及期限/档位保持，B不变，不扣积分。
2. 普通档位提交#RRGGBB成功、八效果422；VIP4_YEAR和VIP4_MONTH逐一八效果成功，MY_VIP4/纯VIP4不享专属效果。档位无相应字段时422；空对象/错误类型/null/额外autoCheckin或configJson拒绝。
3. 同key同请求恢复旧结果，新key修改后重放旧key不覆盖GET当前值；同key不同有效请求409。账号停用后原key和新key均拒绝；Cookie/apiKey不冒充Bearer，缺scope403、缺key422、重复JSON400、body超4096字节413。
4. 过期/停用/无会员404，永久有效会员可修改；多周期展示字段支持schema冲突、坏配置/重复owner503且不写。空白旧配置仅获得本次显式提交的展示字段，不补autoCheckin/checkinCard等权益或未提交color。配置元数据失败不清空或降级为任意模板。
5. 开发测试库故障注入配置/回执/保存点/提交失败，全体回滚；提交确认丢失以原key恢复。与开通/到期/旧配置并发验证真实MySQL锁、版本和缓存行为。
6. 回归原黑名单→test、匿名首访/计次、validateCaptcha解封/开发关闭盾，及公开API鉴权/日志/静态CDN版本/sourceMappingURL/目录枚举/上传下载路径。此处是人工待验，不称已完成全站反漏扫。

## 已知限制与安全

旧MembershipQueryService在缓存miss后SQL读取再put，没有修订号；新入口事务结束后remove仍可能被旧并发loader延迟put覆盖。v3 GET直查数据库不受这类旧缓存影响，不能宣称旧页面跨实例强一致。旧配置/会员管理链也未同步新锁和按列更新，仍存在旧整行快照覆盖并发新值的局限。

新入口Bearer+scope/当前owner重验，不增加匿名入口或anonymous.viewSkips，不修改黑名单/验证码链，不引入Cookie写CSRF路径。字段白名单/有界JSON/绑定SQL、typed响应，不渲染HTML或抓远程URL，无新增上传下载/静态/CDN/source map/目录。压力、分布式门禁、完整链路和全站扫描仍待运行验收。内部锁序/技术预算/匿名与CSRF实现不得复制到公开文档。

## 公开 API 片段

### 设置本人会员展示

`PUT /api/v3/me/membership/config`

部分更新当前有效会员的展示设置，需要settings.write和Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次操作的幂等键 | membership-config-001 |
| bold | 可选，布尔值，是否加粗 | true |
| underline | 可选，布尔值，是否显示下划线 | false |
| color | 可选，#RRGGBB；VIP4_YEAR/VIP4_MONTH额外支持八种效果 | #AABBCC |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/membership/config' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: membership-config-001' \
  --header 'Content-Type: application/json' \
  --data '{"bold":true,"color":"#AABBCC"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 本次配置结果 | `{...}` |
| - config | 展示字段，仅返回已有合法值 | `{...}` |
| -- bold | 是否加粗 | true |
| -- underline | 是否显示下划线 | false |
| -- color | 颜色或效果 | #AABBCC |

> 至少提交一个字段，仅接受当前档位支持的字段。未提交项和其他会员权益保持，档位、有效期和积分不变。VIP4额外效果为rainbow、neon、fire、ocean、forest、sunset、metal、galaxy，区分大小写。
>
> 同键同请求恢复原回执，不覆盖之后的配置；同键更换内容返回409。无当前有效会员返回404，参数无效422，请求体超过4096字节413，配置暂不可用503。成功后重新读取本人会员状态取得当前结果。
