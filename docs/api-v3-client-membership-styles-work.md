# v3 客户端共享昵称样式（2026-10-25）

本轮迁移useVipNickname/VipNickname的共享网络读取，使用既有指定ID批量样式GET与必要的用户摘要ID解析，不改后端契约或264条计数。用户主页其他会员身份判断、聊天室手写昵称样式及基础资料仍有旧链路，不据此称整站会员读取全部迁移。

## 实际修改文件

客户端根目录 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-membership-styles.ts`：最多100个不重复数字ID，按请求顺序核对每项userId，严格投影bold/underline/color；拒绝错误顺序、缺项、错误类型或非法颜色，额外原始配置/期限字段不投影。仅用户名时通过v3用户摘要取得id，确认返回用户名匹配，不读旧完整用户或会员详情。19位ID以字符串保留，不接受不安全JS数字。
- `src/composables/useVipNickname.ts`：移除fishpi旧会员/用户读取。相邻可见昵称合并、相同ID共用在途请求，批次最多100并顺序执行；只有当前凭据的结果可以入缓存。旧读者批次、已失效目标与旧姓名解析均不能写回。配置失效会取消该目标在途写入、删除ID及已验证姓名别名，重新取得当前样式。姓名绑定只信任v3摘要，不把调用方提供的ID/姓名对直接写成全局别名。
- 共享内存缓存技术边界：样式及姓名映射读取TTL为60秒，各最多2000项，待处理ID和姓名各最多1000；失败不写成无会员，合法空样式可缓存为普通文本。没有定时轮询，已挂载昵称不承诺60秒内自动同步其他用户变更；后续读取、目标变化、本人配置失效或凭据变化才触发重新取得。无localStorage持久化。
- 同一helper只在浏览器有效凭据下请求，匿名/SSR保留普通昵称文本，退出/换号清缓存、队列和别名。保留的isVip属性只是旧渲染开关，不是账号会员状态证明；样式为空不能推出用户非会员，权限和实际会员状态仍用本人状态接口。parseVipNameConfig等预览纯函数签名保持，不向网络响应绕回raw config。
- `src/components/user/VipNickname.vue`：展示只依赖当前缓存，去除本地旧config兜底；目标/凭据revision变化重新观察可见性，退出时CSS立即消失，不能在缓存已清后沿用旧样式。延续懒加载与原插槽文本，卸载断开观察。
- `tools/v3-membership-styles-test.mjs`：新API、合批/缓存/时序与实际编译昵称模板专项。
- `tools/v3-membership-test.mjs`：预览测试更新依赖端口；只跑真实昵称纯函数，任何意外样式网络读取都会抛错，会员页面回归继续执行。
- `README.md`、`AGENTS.md`、`docs/V3_MIGRATION.md`：更新共享昵称完成范围与未验项。

后端工作区仅文档：`docs/api-v3-client-membership-styles-work.md`、`docs/api-v3-progress.md`、`UPDATE_LOG.md`。API.md已有批量样式契约保持，Java/FTL、Repository/SQL未改。

## 实际验证

`corepack yarn node tools/v3-membership-styles-test.mjs`最终8组exit0，`/tmp/rhythm-v3-client-membership-styles-tests.log`：

1. 闭合三字段样式、顺序绑定与数字ID/姓名解析，错误类型/颜色/目标拒绝。
2. 匿名零请求，205个ID合并100/100/5，重复ID与缓存命中不重复请求。
3. v3验证姓名别名、显式ID不串别名、本人失效清两类缓存。
4. 旧读者批次不能覆盖新读者，退出同步清缓存。
5. 批次在途失效后丢弃旧值，只安装新读取结果。
6. 请求失败不缓存、合法空样式普通文本、后续读取TTL及SSR禁止使用浏览器样式。
7. 实际编译Vue昵称模板，退出后class/style消失，无本地config回退。
8. 共享网络源无旧会员/用户详情回退。

传输与身份发布用适配器，实际API/合批helper/Vue模板在内；不等于真实服务授权、跨实例变更或浏览器所有消费者验收。会员页面8组回归通过，`/tmp/rhythm-v3-client-membership-tests.log`。类型/client/SSR构建通过，`/tmp/rhythm-v3-client-membership-styles-build.log`；64个生成JS版本/sourceMappingURL审计零命中，`/tmp/rhythm-v3-client-membership-styles-js-audit.json`，两工作区diff空白检查通过。只改源码并构建，未直接改CSS/min.js。

实际IAB本地HTTP/WS合成夹具：Alice主页姓名经v3摘要解析到ID100，再调用批量styles，显示蓝色#1555CC与下划线；读取DOM class/style与截图一致。退出本地测试账号后class与颜色/下划线立即清空，姓名文本保留，夹具未再收到样式请求；Hydration日志空。截图`/tmp/rhythm-v3-client-membership-styles-proof.png`、`/tmp/rhythm-v3-client-membership-styles-logout-proof.png`，请求证据`/tmp/rhythm-v3-client-membership-styles-fixture.log`。无真实账号或生产写入，临时页与自建服务器结束后关闭。

## 人工完整链

1. 本轮仅客户端；真实联调仍需先确认前批Java/FTL经IDEA重编译/重启、相应v3迁移部署。客户端yarn run build，指向localhost:8080，以127.0.0.1:5173登录→用户主页/文章等实际使用VipNickname的入口。
2. 有ID直接批量读样式；只有用户名时先v3摘要解析ID。核对普通六位色、加粗、下划线与八种效果，不返回原会员configJson/期限/其他权益。无样式与匿名均普通文本，不能显示“非会员”结论；SSR也不能嵌入浏览器读者的缓存。
3. 多个同用户昵称只一项请求，超过100个可见ID分批，不漏项/串顺序；用户名称大小写别名需由v3摘要验证，显式ID不借用其他姓名缓存。不安全数字ID不误查取整后的他人；19位ID用字符串。
4. 修改本人会员展示成功后，已有昵称缓存失效并重读；挂起旧样式请求到修改后才返回，不能覆盖新配置。退出/换号时旧样式、别名和在途回写清理，懒加载离屏再进入仍取当前凭据；切目标不能短暂沿用前一用户样式。
5. 注入401/403/网络错误/坏响应，不回退旧会员详情、不缓存成无会员。新挂载或再次观察可重试；合法空样式可缓存。后续读取超过TTL会刷新，已挂载项没有自动定时刷新承诺；其他用户改配置、改名或过期后实际刷新时序需真实环境核对。
6. PC/移动完整走登录→看昵称→配置更新→退出/换号→错误恢复。真实profile.read、token/epoch撤销、黑名单/test/验证码解封、CSRF/Bearer和批量请求限制、全CDN版本/map/目录及上传下载路径仍需联调；本地夹具不是这些通过证明。
7. 用户主页会员身份徽章和聊天室手写样式不是此共享组件链路，继续单独盘点，不能把本轮无旧详情回退扩成整站无旧会员读取。

## 安全与反漏扫结论

沿既有profile.read批量GET和用户摘要GET，不新增匿名路由/viewSkips或凭据URL；样式读取无登录时不发请求，不用Cookie/apiKey补身份。ID与返回顺序绑定，字段白名单，颜色只能六位色/八种效果；文本保留Vue插槽转义，无v-html、任意CSS字符串/URL、动态SQL或服务端抓取。

批次/队列/缓存技术有界，无全站会员枚举接口；只查页面指定ID，失败无自动无限重试。凭据与目标失效检查阻止旧数据回写。共享isVip渲染标志不参与授权/会员资格。无新上传下载或CDN资源。实际权限、风控、分布式并发、负载、全站CDN/目录/上传下载及旧消费者仍未完整运行验收，不能默认反漏扫全部通过。
