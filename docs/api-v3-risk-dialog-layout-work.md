# 经典风险确认弹窗窄屏修复（2026-10-28）

第112批。在真实后端联调暂缓期间，使用实际公共弹层/生成资源补充窄屏验证，发现PC样式在390px下最小宽度502px，标题及取消按钮裁切。只修新风险确认弹窗，不修改其他通用弹窗布局。本批无Java/FTL/SQL/Repository/Maven/APP改动。

## 实际修改文件与行为

- Rhythm `src/main/resources/scss/_red-packet-risk-dialog.scss`：新增限定dialog-panel--packet-risk的布局，桌面500px，视口四周至少16px，窄屏收缩、内容过长可滚动，按钮换行且保留触控高度；inset覆盖Util.alert旧的内联初始坐标，旋转窗口后仍在视口内。
- `src/main/resources/scss/base.scss`、`mobile-base.scss`：PC/移动均引入同一partial；对应`css/base.css`、`css/mobile-base.css`仅由build生成。
- `src/main/resources/js/chat-room.js`、`chat-room-2.js`：风险弹窗加专用modifier、选择区及操作区类；打开前清理旧公共弹层，避免重复ID/旧事件。确认、取消、只读及原出拳恢复逻辑保持。
- 对应`chat-room.min.js`、`chat-room-2.min.js`由build生成，未手改CSS或min文件。
- `tools/api-v3-red-packet-bridge-ui-test.mjs`：既有VM DOM适配器支持class应用，继续验证实际方法，未新增模拟实现断言代替布局检查。
- `AGENTS.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`及本记录；API.md契约未改。

website_v3仅更新`docs/V3_MIGRATION.md`及本记录，源码未改。

## 实际验证

corepack yarn run build明确exit0；既有16组领取UI、12组实时状态通过。4个改动JS版本审计0发现，无source map；两生成CSS无sourceMappingURL；git diff --check通过。没有重跑无变更的Java核心或website_v3全量构建。

IAB本地夹具使用实际jquery、common.min.js中的Util.alert、PC/mobile生成CSS和两套生成聊天室脚本，不替换弹层renderer，不连接业务服务：

- 修复前PC/390×844：panel左-29.9、右472.1、宽502，实际截图标题和操作被裁切。
- 修复后PC/390×844：panel x16..374，标题、出拳及三个操作均可见；按钮右边界不超过360。
- 从390×844旋转844×390：panel x172..672、y81.5..308.5，未越界。
- mobile第二套/390×844猜拳：panel x16..374，所有操作在视口内、无横向溢出；mobile第一套心跳同样x16..374，全部操作可见。
- 桌面PC/1280×720动画结束后panel x390..890、宽500，正常居中。浏览器临时viewport覆盖已reset，临时页与服务已关闭。

此为真实DOM/公共样式下的模拟窗口验证，不是移动实机、完整FTL/聊天室或真实积分结算验收。真实后端、设备触控、权限与风控继续暂缓。

## 安全与反漏扫

只修改局部展示类及SCSS，不新增接口/匿名入口/anonymous.viewSkips、授权或CSRF例外。原BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor保持。新增文本/ID规则不变，无SQL、HTML动态入参、远程代理/SSRF或上传下载路径；不新增CDN依赖。4JS审计0/无map，生成CSS无sourceMappingURL；实际新IP/黑名单/验证码/解封/开发模式、权限和公网目录枚举未验，不默认通过。夹具只读本地资源，阻止外部网络和账号请求。

## 完整人工验收

1. 更新两套生成JS及base/mobile-base CSS，强刷PC/mobile页面；若本地读target/classes，先同步资源或IDEA资源编译。本批无Java重编要求，此前Java改动仍须部署后才能真实联调。
2. 390px下打开心跳/猜拳风险确认，标题、关闭、选择、确认/只读/取消均在视口；未选出拳不能领取、取消零请求，只读不带风险证明。切换横屏再竖屏，保持可用。
3. 桌面宽窗口风险弹窗保持500px正常居中；只针对风险弹窗应用新class，其他公共提示/详情沿旧样式。重复打开不叠加旧对话框或重复事件。
4. 在专用开发账号按第110/111批步骤检查实际领取、原拳重试及领完只读；当前不执行真实金融操作。移动实机、完整模板/WS、当前权限/验证码待用户恢复联调后补验。
