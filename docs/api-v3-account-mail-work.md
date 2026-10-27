# v3 绑定邮箱发送确认（2026-10-23）

## 文件与行为

- `src/main/java/org/b3log/symphony/util/Mails.java`：新增同步 `sendAccountBindingCode`，SMTP通过专用sendAccountMessage等待结果，强制关闭JavaMail debug及正文/收件人日志，普通邮件保留原行为；SendCloud使用固定HTTPS端点；阿里云沿现有签名及配置。新方法不记录目标、验证码、正文或原始异常，供应商未明确接收时返回false。现有邮件入口及模板保持调用语义，阿里云请求共用签名函数。
- `src/main/java/org/b3log/symphony/util/AccountMailResponses.java`：只认可HTTP200及供应商明确成功响应；类型错误、重复JSON字段、错误码、缺字段与过大正文均拒绝。确认接收不代表邮件已进入收件箱。
- `src/test/java/org/b3log/symphony/util/AccountMailTest.java`、`tools/api-v3-account-mail-test.mjs`：直接提取生产方法编译，SMTP/HTTP传输为假对象，不发送任何邮件。

成功判定依据官方接口：[阿里云SingleSendMail](https://www.alibabacloud.com/help/en/direct-mail/singlesendmail)的EnvId/RequestId，以及[SendCloud API规则](https://web.sendcloud.net/doc/email_v2/)的result/statusCode；HTTP端点沿[SendCloud发送接口](https://www.sendcloud.net/doc/email_v2/send_email)。2026-10-23读取，未调用供应商或使用业务账号。

## 实际验证

`node tools/api-v3-account-mail-test.mjs`最终退出0，日志`/tmp/rhythm-v3-account-mail-root.log`，3组：

1. 严格供应商响应、布尔/数字类型、错误HTTP、重复字段、坏JSON及大小边界。
2. 实际SMTP分支等到返回、配置缺失/抛错/非法目标或CRLF拒绝，失败不泄漏原异常。
3. 实际SendCloud/阿里云分支、域名路由、固定HTTPS、阿里签名、响应关闭，日志不含目标/验证码/错误正文。

另已用Java25编译完整生产Mails/MailSender及AccountMailResponses/ApiV3Json，使用真实Jodd HTTP/Core5.0.13、JavaMail1.6.2和Activation1.2.0；仅配置、Common及Templates版本常量采用适配，首次缺Templates常量后补齐适配，最终退出0。外部传输专项仍是假对象，不是完整项目编译、真实SMTP/API投递或邮件到达验收。未执行Maven。联系方式写入的完整SQL/权限/回滚另由contact专项验证；此方法本身不写数据库。账号短信sendAccountVerifyCodeSMS在开发模式仅模拟受理，不再调用会打印完整号码/码的旧发送方法；旧普通短信入口保持。开发联调取码需IDEA断点或专用测试供应商，不能期待本模块日志输出验证码。

## 人工完整链路

先应用对应账号迁移并由用户通过IDEA重编译/重启。使用第一方本人令牌和当前密码（已有MFA再加TOTP），从邮箱验证码申请入口提交新邮箱与极验：收到accepted后在本人邮箱查看邮件，提交目标邮箱和验证码完成变更，应返回changed且旧凭据失效；重新登录确认新邮箱生效。验证错误/过期码、重发后旧码、其他账号目标被拒；关闭或模拟失败供应商时应503，原有效码保留。应逐一在专用测试环境覆盖实际启用的SMTP/阿里云/SendCloud，以及域名路由；生产账号和用户邮箱未用于自动测试。

## 安全与未验项

固定服务端供应商端点，不从用户地址拼HTTP URL；不下载图片/文件或返回存储路径，无新增CDN/静态资源。目标禁止空白/控制字符，正文仅固定文字及六位码；不把验证码交给公共日志或第三方二维码服务。公开路由的第一方鉴权、证明及配额由contact模块负责，本方法不提供独立HTTP入口。BeforeRequestHandler、匿名盾、anonymous.viewSkips、黑名单解封、PC/mobile、静态版本/sourceMappingURL/目录枚举仍需完整应用验收，本专项不能证明全站反漏扫通过。


补充最终真实SMTP证据：`node tools/api-v3-account-smtp-test.mjs`退出0，`/tmp/rhythm-account-smtp-root.log`。编译完整生产Mails/MailSender、真实JavaMail Session/MimeMessage/Jodd依赖；通过JavaMail本地Provider机制注入不打开网络的FakeTransport。显式开启旧配置SMTP debug及DEBUG日志后，账号邮件无debug/目标/验证码日志，发送和失败均关闭transport；旧普通邮件对照确实产生调试/正文日志，避免空日志的假阳性。新工具及AccountSmtpTest为实际新增文件。配置常量和外部SMTP transport适配，未进行实际投递或完整应用运行。
