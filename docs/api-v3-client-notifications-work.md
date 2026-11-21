# website_v3 通知HTTP与实时迁移（2026-10-25）

客户端目录：/Users/ling/Documents/fishpi/website_v3。后端通知原有7路HTTP与tickets/WS协议不变；本批只接客户端列表、未读数、分类/全部已读、notifications.changed失效刷新。后端本轮另新增企业认证decision，使总260，不能把客户端调用重复计成新API。

## 实际文件与行为

以下路径均相对上述客户端目录：

- 新增src/api/v3-notifications.ts：严格信封、DTO与pagination映射；不可访问的内容不回填额外正文/作者/标题/URL，匿名评论不回用文章作者；链接仅精确站点、配置后端或当前站点转本地路由，头像只HTTP(S)。未读类别固定映射并校验total；标记已读POST带Idempotency-Key，changed按数量解析。错误抛出，不用旧接口/mock/空成功兜底。
- 新增src/api/v3-realtime.ts：POST票据后连接同源无query /api/v3/realtime，票据仅首帧authenticate；ready及notifications.changed只触发HTTP重读，ping、握手期限、指数退避及新票重连；旧token/延迟签票/停止后不得创建新连接，终止清全部定时器和旧socket。没有把Bearer/ticket进URL或持久存储。
- src/api/fishpi.ts：保留旧函数参数位置但忽略旧apiKey，通知函数转发新资源；NoticeType/UnreadCount增加other，NoticeItem增加可读与通知标题字段。其他未迁移apiKey功能仍保持原状态，不把Bearer传给它们。
- src/stores/notices.ts：内存token同步边界管理；请求代次与凭据快照阻止旧账号慢响应回写，登出/换token立即清状态并关连接；窗口聚焦刷新，失效revision供页面重读。错误未读数不当作成功0。去除旧user-channel行为；私信和旧紧急广播没有因此迁移。
- src/views/NotificationsView.vue：Bearer gate，真实pageCount、不用list.length猜下一页；私人通知列表不保留成功缓存，刷新失败清旧列表并显示错误；身份变更清列表、页码和失败写key，异步旧结果不落当前视图；失败操作在当前凭据边界保留key至成功，重试原操作；错误与未读数不可用单独显示，other分类、用户名路由编码。原style块逐字未改。
- src/components/notifications/NoticeIcon.vue：增加other图标路径。
- src/layouts/AppShell.vue：通知生命周期交给store，不再watch恒为null的legacy apiKey；认证导航mounted后显示，修复router提前恢复认证导致SSR header/nav hydration不一致。旧私信/聊天室/勋章会话未被错误启用。
- server.js、vite.config.ts：开发HTTP/WS代理包含精确/api/v3/realtime；docs/nginx.example.conf增加该路径示例及长连接超时，未修改真实nginx。原全局Upgrade/header继承，不带凭据query。
- 新增tools/v3-notifications-test.mjs：实际TS适配/订阅与Pinia测试；网络/Socket/Timer为显式适配，非生产数据。
- README.md、AGENTS.md、docs/V3_MIGRATION.md：更新当前范围、SSR/订阅维护点与未迁移项。

后端本批维护本文件、docs/api-v3-progress.md等阶段记录；认证结论文件与证据另见api-v3-identity-application-work.md。没有新增依赖、直接编辑CSS/min.js/dist或自动提交/推送。

## 执行证据

1. tools/v3-notifications-test.mjs 六组exit0：DTO不可读/匿名和精确URL、真实分页/计数及异常非空成功；POST及稳定key/数字回执；实际订阅TS的无凭据URL、首帧仅一次/ready/changed/heartbeat/清理；延迟旧票和外站path拒绝；真实Pinia late-owner/登出/销毁。日志/tmp/rhythm-v3-client-notifications-tests.log。fetch/socket/timer为显式测试端口，不是Java Socket端到端。
2. scripts/tests/v3-auth-transport.mjs12组、scripts/tests/v3-cache-identity.mjs3组均exit0，日志/tmp/rhythm-v3-client-notifications-auth.log及cache.log。
3. corepack yarn run build完成vue-tsc、客户端及SSR构建，/tmp/rhythm-v3-client-notifications-build.log。NotificationView原style与HEAD相同，没有修改样式源或直接改编译产物；其他已有工作区改动保留。
4. IAB实际SSR应用127.0.0.1:5173，后端为独立合成HTTP+WS夹具，不连接Rhythm或生产。验证12条未读/10+2分页及尾页禁用、不可访问通知不带PRIVATE标记、分类已读后计数及列表更新、reply503显示错误、退出清空导航；最终版追加验证成功列表→空积分分类→返回回帖503，旧成功内容被清除而非静默沿用缓存；fixture记录Socket经过实际server.js开发代理认证，握手/首帧格式符合现有协议。首次观察hydration告警后修复AppShell挂载边界，重新构建并新页验证Hydration日志[]及服务日志无告警，截图/tmp/rhythm-v3-client-notifications-proof.png。没有验证真实Java、短信/图床或移动设备。
5. 自建IPv4:5173服务、两个夹具服务及IAB页均已关闭；本机另一项目hot/apps/client的IPv6::1:5173进程未停止。临时夹具文件在/tmp，未加入产品模拟数据。
6. 两repo git diff --check、Node语法检查通过；当前后端260 METHOD+path与清单匹配。标准tools/js-version-audit扫描新dist/client/assets命中一项既有旧mockEmotions图片URL vditor@3.8.7，报告/tmp/rhythm-v3-client-notifications-js-audit.json。该未迁移常用表情兜底仍在src/api/fishpi.ts，不能称全客户端静态版本清理通过；下一批应接真实v3表情读取并去旧mock。没有新增远程CDN依赖或sourceMappingURL。

## 安全与反漏扫复核

只调用既有Bearer notifications.read/write及签票端点；参数和响应白名单适配，不以Cookie/apiKey恢复普通接口身份；POST改状态带key，不用旧GET写入口。后端身份/归属、Before黑名单先/test、匿名验证码和validateCaptcha链未改；真实新IP/黑名单/解封/开发风控开关未联调。客户端只在当前token发送请求，延迟请求/票据/Socket受生命周期保护，SSR没有共享身份请求。

未授权/越权：后端当前Bearer保护，前端仅辅助不替代权限；未知/不可读目标不回退旧内容。CSRF：写入Bearer头；只使用既有刷新Cookie流程，不改旧页面CSRF设计。XSS：描述HTML使用服务端已清洗descriptionHtml，Vue插值显示标题/用户名，不解析额外legacy正文；不可读强制固定占位，匿名不复原；普通描述的全面HTML清洗仍由既有后端保障，未宣称客户端任意HTML消毒器。链接精确origin+安全协议；用户名路由编码。注入/SSRF：客户端不拼SQL或服务端抓远程内容；API路径固定，query编码、页码有界。滥用：签票受原写门禁，重连退避、帧/心跳有界；新客户端没有HTTP轮询循环，聚焦/事件重读；未验证生产多节点频率。泄露：凭据不进WS URL、日志、缓存key或localStorage，断开旧socket，清私人响应；fixture只用合成数据。没有上传/下载或服务器路径返回。Nginx目录/源图路径和旧静态CDN版本问题未完成整站验收，不能推定反漏扫全通过。

## 完整人工链

1. 独立开发库完成已有v3/通知/凭据迁移，IDEA重编译或重启并确认后再联调真实服务。客户端127.0.0.1:5173目标设localhost:8080，生产代理参照示例并自行nginx -t。没有收到真实后端重启确认，不据当前旧行为判代码失效。
2. 登录有notifications.read/write账号，进入/notifications；未读数与分类一致，翻到第二/尾页，核对pagination总数和按钮。受限/已删通知只有固定提示，没有私密正文、匿名真实作者或失效链接；站内点击保留页码/锚点，不接受fishpi.cn.evil。
3. 点击本类已读/全部已读，检查POST/key、数量回执、刷新后的计数和read标志；断网或lostACK保留原操作重试，不误报空成功。503、错误信封/分页、未读数失败应显示错误或不可用，不能显示假通知。切tab/页码、快速请求返回顺序交错，旧结果不覆盖当前。
4. 收到新通知时验证HTTP签票→无query WS→首帧认证→ready/changed→HTTP重读；断开后新票重连并补读，失效token刷新或403停止旧连接。退出/换账号/旧请求延迟返回时立即清旧内容和计数，旧票不建连接；不把私信/紧急广播未迁移功能说成已支持。
5. SSR冷开/notifications、公开页登录恢复、退出与再登录，在桌面/窄屏及实际移动设备核对没有hydration错误/闪旧身份。已做宽视口夹具，真实后端、移动设备和生产代理仍未验。
6. 未登录、错scope、过期token、黑名单IP/test、validateCaptcha成功/失败及风控关闭回归；新通知没有绕过原后端规则。继续补其他未迁移模块、旧表情版本URL和通知删除/单条已读UI，完成后才可宣告客户端与v3全目标达成。
