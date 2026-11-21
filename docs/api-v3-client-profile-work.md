# website_v3 资料、MBTI与头像迁移（2026-10-25）

客户端目录 /Users/ling/Documents/fishpi/website_v3。本批调用已有profile/personality/avatar与upload tickets，不改后端接口契约、SQL或路由，总260保持；不是完整设置页迁移。

## 实际文件与行为

- 新增客户端src/api/v3-profile.ts：白名单读写本人昵称/简介/网址；独立MBTI当前值/目录/保存、头像当前值/保存。PUT带随机幂等key，签名图片URL保留原始query，预览过滤危险协议/凭据。只把profile三字段发给当前接口，不夹带旧QQ/标签。
- 同一文件uploadV3Avatar：取POST /api/v3/uploads/tickets后，以票据Bearer和multipart的file字段直传；不把账户accessToken送图床。检查JPEG/PNG/GIF魔数，命名avatar.jpg/png/gif并按实际格式设MIME，不信扩展名；WebP等明确报错。固定pic.fishpi.cn目标走现有同源代理，其余服务端给定目标使用完整URL；仅HTTPS或HTTP回环，禁凭据/query/fragment及错误字段名。上传fetch禁Cookie/重定向，凭据变化AbortSignal中止。外部响应只取对应文件成功URL，不输出原响应/票据日志；保存URL仍遵守服务端255限制。
- 新增客户端src/components/settings/OwnerProfileSettings.vue：profile/avatar两种透明布局，SCSS经构建；独立表单与结果，资料成功不伪装MBTI也成功。真实GET初始化，不用公开用户卡或脱敏me对象凑私有设置。支持HTTPS地址和图片文件上传。上传成功但头像保存失败保留URL，按原key重试设置无需重传文件；资料、MBTI、头像key仅同内容未知结果保留。账号/凭据变化清状态并中止上传，旧响应不得回写或触发保存。保存后me刷新失败不把已提交业务报成失败。
- 客户端src/views/SettingsView.vue：原profile/avatar表单接上述组件，删除原来apiKey为null时静默return的处理方法及旧资料初始化；资料用户名链接同认证分支挂载后显示，修复SSR冷启动水合不一致。其他账号/功能/隐私/职业等旧表单未因此迁移。旧样式未手改生成CSS/min.js。
- 新增客户端tools/v3-profile-test.mjs：实际TS适配与实际Vue/Pinia组件，显式网络/认证适配；测试边界见下。客户端README/AGENTS/V3_MIGRATION和后端进度/本文同步。

旧资料页面还有QQ与标签，但现有v3资料仅三字段；已向用户询问是否纳入及规则，当前未新增保存能力，不能把本批称为全部资料能力完成。未删除数据库值、未改旧接口，其他调用的旧uploadFiles也没有被自动迁移。

## 证据

- tools/v3-profile-test.mjs五组退出0，日志/tmp/rhythm-v3-client-profile-tests.log：严格读响应、错误不造默认；profile写白名单及独立key；完整签名URL；上传魔数/命名/MIME/file字段、只票据/无Cookie/禁重定向、非法目标/字段/中止后不上传；实际Vue旧账号延迟读隔离/未知ACK同键、MBTI独立状态；上传后设置失败保留URL/不重传，换号中止上传且不继续保存。
- corepack yarn run build类型检查、client/SSR构建通过，/tmp/rhythm-v3-client-profile-build.log；git diff --check通过；生成assets 62个JS版本扫描0命中，/tmp/rhythm-v3-client-profile-js-audit.json。没有Maven、后端运行或真实CDN全量验证。
- 实际IAB只连本地合成HTTP/WS服务，完成昵称/简介保存→新页重新读取保持、无效MBTI拒绝→小写ENFP-A规范化、签名HTTPS头像保存、合成PNG经HTTP回环票据上传→头像保存、HTTP头像被拒绝。夹具确认图床收到票据而非账户token。初次发现资料链接水合警告已修复，新页面Hydration日志空。第二次上传自动化遇控件索引过期，重新获取当前页面后最终再次上传并显示已保存；没有以超时假定上传成功。
- 截图/tmp/rhythm-v3-client-profile-proof.png、/tmp/rhythm-v3-client-avatar-proof.png。images.example为示例，图片不能加载不代表真实头像可访问；未连接真实RhyPic、未上传个人图片、无生产写入。临时服务和页清理。

## 人工完整链

1. 在独立开发库部署已有迁移，用户通过IDEA重新编译/重启并明确确认后再联调；客户端指向该开发服务，启动127.0.0.1:5173。本批无新Java/SQL，但既有后端尚未确认重新编译。
2. 登录进入/settings，实际昵称/简介/网址读取→修改保存→刷新保持。20/255/100长度、空值、控制字符与非法网址由现有服务端规则约束；返回HTML样式文本只能作为输入/文本，不执行。读取失败应禁用对应保存并显示错误，不补模拟或旧公开资料；不要误认QQ/标签已支持。
3. MBTI独立保存，16种目录及可选-A/-T；小写/空白规范、空值清除、非法后缀拒绝且旧值不变。资料成功不应掩盖MBTI失败。
4. 进入/settings/avatar读取原始地址→保存任意合法HTTPS完整签名URL→刷新仍完全一致；非HTTPS、带凭据、空值及超过255拒绝，不改变当前头像。图片加载能力另按真实地址校验。
5. 上传JPEG/PNG/GIF→POST票据→命名file直传→PUT头像→页面已保存、me刷新；确认图床Authorization只有UPLOAD_TICKET、无Cookie/accessToken，上传无重定向。WebP/假图片/过期票据/图床失败/无uploads.write应报错，无假成功；实际图床格式/大小限制、CORS和固定代理需真实验收。上传成功而保存断网，保留地址且重试不重传文件、同key不重复写入。
6. 两测试账号、登出、自动刷新令牌和慢响应覆盖：清除旧输入/错误/key，取消上传、旧上传URL不保存到新账号，旧资料不回写；页面Cookie或旧apiKey不能替代v3身份。首次未登录只登录提示，桌面/真实移动、冷启动和路由切换无水合警告。
7. 新IP/黑名单/test、验证码解封、写入预算、越权/缺scope/CSRF及实际上传下载返回路径、目录枚举按完整后端链验收；本批没有运行这些真实服务检查。

## 安全与反漏扫边界

本批无新增公开入口、anonymous.viewSkips或Before/Anonymous/Captcha链改造；已有GET需profile.read、写需settings.write、取票需uploads.write。客户端只调本人资源，无用户ID输入；服务端仍负责越权/当前有效身份。Vue文本/输入绑定而非v-html；URL协议/凭据检查，服务端不抓取头像URL，无新SQL注入/SSRF路径。按一次用户操作上传单文件，无自动批量遍历；幂等与原后端预算保持。

固定同源RhyPic代理和自定义目标的真实CORS、真实上游图片内容/大小校验未验；魔数检查不代替图床完整图片验证。票据不持久化/进URL/日志，文件名固定不泄露原本地路径。定向生成JS扫描未命中版本/map；旧编辑器外部资源、全CDN、目录枚举、真实上传下载路径和黑名单/验证码未验，不能据构建通过称全站反漏扫通过。
