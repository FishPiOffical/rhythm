# v3 客户端本人内容私有导出

2026-10-25。接既有创建/状态/下载三路，后端仍264；没有改后端契约或Java/SQL。原旧公开导出地址跳转从客户端删除，不影响仍保留的旧服务端入口。APP扫码、企业认证及其他模块继续迁移，不能称v3整体完成。

## 实际文件

客户端 `/Users/ling/Documents/fishpi/website_v3`：

| 文件 | 行为 |
|---|---|
| `src/api/http.ts` | 增加内部ZIP响应模式，复用既有鉴权/401刷新/风控重试；区分JSON错误和ZIP，按现有服务32MiB预算限制声明及流式体，内部标志不传fetch；显式取消不再冒充离线 |
| `src/api/v3-post-export.ts` | 闭合证明/同键创建、ready/expired元数据白名单，状态绑定ID；ZIP类型/长度/头/SHA-256校验；固定文件名与本地Blob地址交付/回收，无公开下载地址 |
| `src/stores/v3PostExport.ts` | 仅内存key/本人任务元数据，跨SPA保留未知结果，恢复需新证明；确认/当前身份隔离，后置余额失败不重建；状态读取和下载逐次本人授权，离页/换号取消与回收 |
| `src/components/settings/OwnerPostExport.vue` | 范围/100分/24h确认，当前密码和TOTP每次重新填写，字段立即清理；任务ID查询、元数据/真实计数和手动校验下载，状态失败不伪造任务或文件 |
| `src/views/SettingsView.vue` | 数据页换新模块，移除旧apiKey导出与window.open公开地址 |
| `src/api/fishpi.ts` | 删除无消费者旧exportPosts包装 |
| `tools/v3-post-export-test.mjs` | 七组实际API/Pinia/交付工具及完整Vue模板SSR专项 |
| `tools/v3-private-zip-transport-test.mjs` | 四组实际HTTP二进制/刷新/错误/体预算/取消/风控适配专项 |
| `AGENTS.md`、`README.md`、`docs/V3_MIGRATION.md` | 迁移边界与后续复用约定 |

Rhythm本批仅本记录、progress与UPDATE_LOG。API.md三条既有契约保持，前批API文档仍需同步网站文章。

## 用户链路与边界

- 创建需要当前安全证明、第一方settings.write/profile.read；成功文件就绪才由后端扣100分。导出含主表全部本人内容与本人文章付费区，匿名/私密/讨论/隐藏/锁定/无效均在内；不造公开内容数量替代导出计数，不含物理删除或编辑历史。
- 确认后先保留key再发送；网络/5xx/格式错误保留key，跨SPA页面保留，正常非空令牌刷新保持。恢复必须重新输入当前密码/当前TOTP，原key但证明可更新；Pinia不存证明。已未知后的422/403等仍保留原key，避免把新证明不正确当作之前未收费；初次明确4xx释放。
- 创建成功清待确认key并安装原任务；后置余额GET失败只提示已确认后的读取失败，不能重新收费。expired重放只保留jobId/status/paidPoints，不伪造新文件；新导出需显式新确认。任务元数据仅当前登录内存，刷新后可输入已知任务ID读取，不创建或收费；未知key不跨整页刷新或退出保存。
- 下载先GET当前状态，再走本人授权的二进制GET；长度/类型/ZIP头/哈希不符不交付。ZIP头/哈希校验不是客户端完整解压或结构审计。当前读者/任务绑定，取消/换号后旧响应不能创建Blob地址或触发文件交付。声明和流式体均有限，响应JSON错误/HTML不保存成ZIP；下载失败只重读/下载，不新建收费任务。
- 文件名固定posts-{数字ID}.zip；地址仅本地Blob、不带Bearer或远程公开链接；交付后定时回收，离页/退出立即回收、取消下载，已经保存到磁盘的文件不会被远程删除。浏览器须支持WebCrypto；生产HTTPS/开发localhost可校验。期限以服务端为准，本地时间只提示，时钟偏差不会阻止重新向服务器核实。
- SSR不创建/下载，跨身份清key/元数据；离页中止确认，已确认的创建可能继续，结果只落同一当前身份的内存。JS不能保证证明字符串物理擦除，仅清输入和后续引用使用。

## 已运行证据

1. 七组退出0（`/tmp/rhythm-v3-client-post-export-tests.log`）：真实API闭合请求/白名单/ready-expired/坏ID→502不丢收费key、期限/大小/哈希与ZIP校验；本地交付工具固定名/只交一次/回收幂等；真实Pinia未知同键+新证明/轮换/路由复用/恢复422保留且不存proof；成功后读错不重建、初次409/取消/过期、ID去重；状态410/离页/换号下载与迟到创建防回写，SSR/完整Vue模板与旧公开通道删除。
2. 四组真实http.ts退出0（`/tmp/rhythm-v3-client-private-zip-tests.log`）：401→refresh→原下载、token不进URL/内部标志不进fetch；HTTP JSON错误原状态、JSON成功/HTML/空体拒绝；声明及流式体均拦超预算；既有/test风控适配重试、JSON/ZIP主动取消不报离线。HTTP/挑战用适配，不是真实Cookie或验证码验证。
3. 安全七组、财务七组回归通过，日志`/tmp/rhythm-v3-client-post-export-security-regression.log`与`...finance-regression.log`。
4. yarn run build类型/client/SSR通过，日志`/tmp/rhythm-v3-client-post-export-build.log`；生成65个client JS版本/map定向零命中（`...post-export-js-audit.json`），两仓git diff --check通过。无直接改CSS/min.js，无Maven。
5. 实际IAB只连接本地合成HTTP/WS夹具：完整范围/100分确认→提交后503→切积分页/返回不留密码、原key仍在→新填写证明恢复任务10001，实际1篇/1评/703字节/24h/哈希；状态首次503不重建，重试当前状态+鉴权ZIP成功，客户端校验后交浏览器；未知任务99999错误，退出私有任务清理。夹具日志仅1次CREATE、1次REPLAY，余额10000→9900，1次鉴权下载。Hydration日志空。
6. 夹具ZIP用Python zipfile生成manifest与文章/付费区/评论四文件，unzip -t通过；没有真实本人内容。截图`/tmp/rhythm-v3-client-post-export-proof.png`；证据`/tmp/rhythm-v3-client-post-export-fixture.log`。观察到浏览器交付提示，未另核OS最终保存路径/设备下载管理器。临时页/自建服务验后关闭。

## 完整人工链

1. 本批只客户端，真实联调先确认此前Java/FTL经IDEA重编译/重启、20261024导出迁移及credential/MFA迁移部署。前端yarn run build，代理localhost:8080；第一方登录→设置→数据导出。检查导出范围、100分、24h，不用me公开计数代替实际快照数。
2. 用户在隔离测试账号填写当前密码与已有TOTP，取消无写；确认文件就绪仅扣100，取得jobId/快照时间/实际文章评论数/ZIP长度/hash。密码/TOTP错误、余额不足/规模预算失败不得扣款或伪造部分成功；安全状态读取失败不能当未启用。
3. 模拟创建提交后丢响应，原key保留；SPA离页再返回密码已空，重新填写当前证明恢复同任务，一次扣费。普通401轮换保留key；恢复422/403不丢key；后置余额失败不重复创建。整页刷新/退出丢本地未知key，先核对原导出与流水；已知jobId可只读查询。
4. 下载点击前重新读取当前任务、以当前授权取ZIP；真实文件长度/hash与回执一致，解压核manifest、articles/{id}.md、reward.md、comments/{id}.md。覆盖匿名、私密、讨论、隐藏、锁定、无效及本人付费区，不出现他人正文/标题或高敏账号资料。此处需真实测试库内容矩阵及实际ZIP核查，本批没有替代这些为真实结果。
5. 模拟JSON401/403/404/410、HTML、损坏体、长度/hash不符、读状态503：不能保存错误文件，更不能为下载重新扣100；任务404/410标当前不可用，可显式重读。超过24h不能下载，原key只返回expired、不重新生成；新任务再确认才新收费。本地时钟偏差只作提示，最终状态由服务器核实。
6. 创建/确认/状态/下载在途离页、退出或换号，旧证明/元数据不能污染新身份，旧下载不得触发交付，临时Blob地址需回收。普通token刷新、凭据全撤、跨设备及第一方限制逐项核验。
7. PC/移动、键盘/自动填充、浏览器文件管理器、磁盘文件大小/hash和解压、网络中断重试、复制任务ID、真实Cookie/Bearer/权限、黑名单/test/验证码解封及开发模式仍需联调；不把本地夹具当真实服务/数据库/设备验收。

## 安全与反漏扫

既有第一方本人资源，无新匿名路由或anonymous.viewSkips、无Cookie例外，未改Before黑名单/AnonymousViewCheck/Captcha解封。创建/下载需当前授权；body证明不进URL/日志/Pinia，ID严格数字，元数据白名单无内部转账键/公开地址/账号资料。固定同源API路径，不抓任意远程URL，无新SQL/注入/SSRF/CDN；普通文本Vue转义、ZIP不作为HTML执行，不生成公开下载地址或发布上传路径。

HTTP新增ZIP分支复用401和原风控处理，坏响应不下载，忙碌/确认避免重复点击且不自动无限重试；不替代服务端滥用限制。65个生成JS版本/map定向通过，未全扫全站CDN/目录枚举/旧上传下载路径。真实未鉴权/越权、CSRF、XSS、SSRF、负载/内存、敏感信息、跨实例与风控/全站反漏扫尚未完整运行验收，不能默认通过。
