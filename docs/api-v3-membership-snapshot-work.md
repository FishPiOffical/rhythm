# 全量会员昵称快照（2026-11-06）

用户明确要求将按页面用户ID查询改为一次返回全部会员ID/configJson，再按版本缓存；本记录取代此前指定ID批量样式的当前协议，不恢复网站v1支持。

## 协议及缓存

- Rhythm `GET /api/v3/membership/styles` 保留既有 profile.read/Bearer；首次无参数返回 `version / unchanged=false / maxAgeMs / list`，后续 `?version=<64位小写SHA-256>`，相同版本返回 `unchanged=true`，不返回list。旧userIds参数422，需前后端协调更新。
- list为当前有效账号/有效会员，稳定按ID排序；每项 `{userId,configJson}`。configJson是**仅bold/underline/color**的规范化JSON字符串，不是裸数据库configJson，不含自动签到、档位、费用、到期等其他权益。坏配置/不确定到期/重复绑定为null（未知），不拖垮其他人；合法无样式为"{}"。不能据空样式或名单外ID断言非会员。
- 版本对展示内容计算SHA-256，不依赖会员人数、服务重启计数或单一updatedAt。人数不变的改色/替换同样改变版本；不影响公开展示的其他字段修改可能不改变版本，这是预期。
- 单节点服务共享缓存最长30秒，按最近自然到期时间提前失效。MembershipCache在配置/档位/状态/到期变化或remove时发失效代际；相同内容的读取暖缓存不反复失效。档位增改删在原事务commit后失效。既有v3设置/开通提交后清MembershipCache，因此沿该链覆盖；其他节点/SQL直改/账号状态变更由最长30秒TTL兜底，不宣称跨节点即时广播。
- 每个HTTP请求（包括未变化/缓存命中）仍验证当前有效actor；响应no-store，不交浏览器/代理共享缓存。前端仅内存、绑定凭据和请求代际；SSR/游客普通文本。第一份完整快照可服务本页所有昵称，缺少ID者仅必要时解析用户名，不再逐个读取会员。
- 客户端按服务端lease到期清除过期CSS，保留旧版本和不可展示的数据仅供条件校验；只有可见昵称触发一次共享重读，unchanged确认后可恢复该版本。近到期响应最多补一次读；失败退避30秒起至5分钟，不无限重放或把错误当非会员。
- 读取候选含重复关联最多10000行；SQL同语句先约束配置/档位原始输入总预算2MB，再受控投影，输出也限2MB，超出503，不能成功截断。不新增表、迁移、写API或匿名豁免。

## 实际文件

website_v3：`src/api/v3-membership-styles.ts`（完整/条件响应解码）、`src/composables/useVipNickname.ts`（共享快照/可见校验/身份清理）、三个`tools/v3-membership-style*-test.mjs`及`tools/helpers/membership-style-fixture.mjs`；README/AGENTS/迁移记录同步。

Rhythm：`ApiV3MembershipStyleProcessor.java`、`ApiV3MembershipStyleQueryService.java`、`util/MembershipStyleCache.java`、`cache/MembershipCache.java`、`MembershipMgmtService.java`；`ApiV3MembershipStyleTest.java`/专项runner及API/AGENTS/本记录同步。PC/mobile皮肤不改；旧全量接口不改。

## 验证与限制

- website类型、客户端及SSR完整构建通过；完整v3专项105脚本通过。专项覆盖500昵称共享一份名单、缺省ID无额外读取、null未知、同版本确认/同人数改色、租期清CSS、屏外无轮询、身份/失效迟到、退避、HMR/退出清计时器。
- Rhythm使用Java25隔离javac + 实际Dispatcher/Auth/SQL/DTO、SQLite夹具：新快照7组通过，原会员读取7组回归通过。实际MembershipCache本地缓存put/remove的失效及相同内容不失效已运行；候选/输入预算与DB错误已覆盖。没有执行Maven或改真实数据。
- 浏览器使用实际Vue昵称组件/API/helper + 本机HTTP夹具（2秒lease用于缩短等待）：150用户/2会员只下载一份，未变化仅版本确认，同人数改色后第二次下载并显示新色。截图 `/tmp/fp-membership-snapshot-browser.png`。这是浏览器协议/组件验证，不冒称运行了新Java或真实MySQL链路。
- 鉴权/非法参数已过实际中间件隔离测试；不新增匿名路由、viewSkips、Cookie/apiKey回退、静态CDN资产、上传/下载路径、模板或JS生成产物。真实BeforeRequest黑名单/首访验证码/解封、真实MySQL/多节点及PC/移动设备仍需按下述步骤人工验收，不默认通过。

## IDEA重新编译/重启后的人工验收

1. **先更新并通过IDEA重新编译/重启Rhythm，再协调更新网站**。本轮Java需要手动生效；未重启服务的旧userIds行为不代表新代码失败。旧/新协议响应不同，不应长期混部署。
2. 正常登录网站，在首页/聊天室/文章作者列表观察Network：首个styles请求不带userIds，返回全部有效会员ID和规范化configJson；同页新增普通用户不增加逐人styles请求。游客只显示普通昵称。
3. 保持昵称可见等待最多30秒：下一次请求带version；配置未变化返回unchanged=true且没有list。滚动离开可视范围后不应持续主动轮询。
4. 用本地测试账号在会员设置修改颜色，或本地管理员开通/停用/调整到期/档位：提交成功后版本应变化，即使人数没变；相关昵称在下一次校验后更新。自然到期不写数据库也应消失。不要用生产真实账号做收费测试。
5. 在可恢复的本地夹具将一个会员配置设为损坏JSON：该ID configJson=null，其他会员仍可用；修复后新版本恢复。数据库不可用应503并退避，不应变成空名单成功。
6. 退出/换号后旧样式立即清理，迟到响应不能恢复；拒绝的凭据/无profile.read不能因版本命中得到名单。检查PC/移动、SSR水合及黑名单/验证码链路。多人/多节点修改按30秒TTL边界核对，不能据单实例即时失效宣称分布式即时同步。
