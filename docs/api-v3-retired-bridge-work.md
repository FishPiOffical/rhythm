# v3 旧网站登录桥接退役（2026-10-28）

第107批，用户明确批准移除网站旧会话转换桥接，统一v3登录；Rhythm经典入口保持。Rhythm 290入口数量不变。本批无Java/FTL/SQL/Repository/Maven/APP改动。

## 实际文件与行为

website_v3：

- `src/api/fishpi.ts`：基于全部src TS/Vue外部引用和模块内部引用逐轮确认后，删除47个无调用业务包装，清理其未使用import；只保留login、validateRiskCaptcha、normalizeMetals、emojiToMarkdown、fetchArticle、fetchRepeaterItems六个当前helper及仍用共享类型。删除的是旧包装实现，不是对应v3功能；v3专用模块保留。
- `src/api/pageAuth.ts`：删除。旧apiKey/CSRF在内存与sessionStorage的缓存、会话转换及页面跳转helper不再进入客户端。
- `src/layouts/AppShell.vue`：去旧helper import及apiKey调用；删除没有实际数据项的rhythm标志菜单分支，保留既有RouterLink/外链，经典功能直接导航由目标页认证。
- `server.js`：删除POST /__fp/page-auth及仅为其服务的HTML提取、旧登录/设置请求、Cookie解析和签发；旧路径由实际服务返回404。保持请求body不被全局JSON parser消费，保留SSR与现有代理。
- `vite.config.ts`、`docs/nginx.example.conf`：移除loginWebInApiKey转发规则；开发及生产模板不再提供网站侧旧Cookie转换路径。没有删除Rhythm本身入口。部署方现有nginx配置并非自动修改，需要同步应用。
- `tools/v3-retired-bridge-test.mjs`：6组，启动实际生产/开发Node服务及独立loopback上游，证明旧桥接GET/HEAD/POST均404、无Set-Cookie、零上游调用；实际Vite不再转发旧换Cookie路径；现有v3与validateCaptcha POST正文完整到达隔离上游；源码退役与保留helper校验。所有临时进程退出。
- `tools/v3-public-column-test.mjs`：既有第12组改为检查当前Home专用模块的真实共享卡片解码，删除对无调用旧fetchHomeColumns包装的依赖，既有专栏行为回归保持。
- `README.md`、`AGENTS.md`、`docs/MISSING_APIS.md`、`docs/V3_MIGRATION.md`、本记录：清过时page-auth与旧接口现状说明，保留历史盘点并标明不可按旧表恢复mock。

Rhythm：`API.md`登录兼容提示、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-v3-progress.md`、本记录。对外文档不记录内部安全机制；用户应把API.md同步到网站API文档文章。

## 实际验证与限制

80个tools脚本917组、认证transport12组通过；`corepack yarn run build`明确exit0，类型/client/SSR完成。77生成JS版本审计0，无map/sourceMappingURL；生成客户端JS无/__fp/page-auth、loginWebInApiKey、fp.pageAuth字符串；git diff --check通过。

IAB 1280×720实际生产构建+SSR本地合成首页，AppShell渲染正常，点击登录进入v3表单，用户名/密码/可选2FA输入及登录按钮保留；未输入或提交真实凭据。首页夹具部分资源预期503单列，不是整站成功验收；临时页/服务已关闭。服务端定向测试使用真实server.js生产及Vite开发模式，外部仅为自建loopback上游，不使用生产账号。

本批未执行真实登录/刷新Cookie、OAuth、经典页面认证、真实MySQL/IoC、IP风控验证码、生产nginx重载或390px触屏。website_v3部署需重启Node，并按实际部署同步nginx模板；不能把现网未更新当作代码未生效。Rhythm本批没有Java重编要求。

## 安全及反漏扫

已按用户确认停止提供网站旧内部桥接及直接转发；没有新增匿名入口、授权例外或Cookie签发。现有BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor与anonymous.viewSkips保持；实际Vite验证validateCaptcha代理body仍完整，不能代替真实验证码/黑名单/解封验收。v3原认证/CSRF边界及响应投影保持，未新增SQL、远程代理、批量接口或HTML渲染；删除旧HTML抓取链及客户端凭据缓存。无新增CDN、上传下载路径或公网目录，77JS版本0/无map；真实静态目录枚举、新IP/开发风控模式和实际授权仍待运行验收，不默认通过。

## 完整人工测试步骤

1. 构建并重启website_v3 Node；使用nginx模板的部署同步删除旧转换转发规则并检查配置再重载。Rhythm经典站服务与旧接口不受本批代码修改；此前Java联调仍需用户IDEA重编/重启确认。
2. 从首页进入登录，按正常账号与已有2FA登录，确认当前/me、刷新与退出均走v3；正常登录、自动刷新及退出后重进各验证一次。测试账号凭据由用户输入。
3. 在专用环境请求旧GET/HEAD/POST /__fp/page-auth，均应404且无Set-Cookie、不请求上游旧登录/设置页；网站/loginWebInApiKey也不再换Cookie。旧集成改为v3，不能再依赖此路径。
4. 开发代理对普通v3 POST和验证码POST应保持原始JSON正文；用新IP/黑名单分别验证/test、验证码解封与当前授权，不能因桥接退役绕过防扫描。
5. 验证设置资料/会员/职业/勋章、通知、私信、表情、目录/文章仍使用专用v3模块；错误应显示重试而非mock。点击经典游戏/捐助等链接由目标页自行认证，不自动创建旧Cookie。
6. 桌面与390px回归导航、登录表单及菜单；生产nginx和实际Cookie/凭据边界属于未完成验收项，不能以loopback代替。
