# 新风险红包实时完成状态收尾（2026-10-28）

第111批。补齐第110批经典新风险红包领取的实时状态与目标边界；真实联调继续按用户要求暂缓。已提醒本批含Java、后续需IDEA重编/重启；未运行Maven或业务库操作。

## 实际修改文件与行为

- Rhythm `src/main/resources/js/chat-room.js`、`chat-room-2.js`：updateDurablePacketStatus只更新已经识别的新核心红包；验证数字ID/整数份数与领取数。领完标志单向保持并清未确认领取意图，较早部分事件或历史快照不覆盖完成状态；不按用户名认领归属。完成后打开仅viewOnly，不再弹风险确认。
- `src/main/resources/js/channel.js`、`channel-2.js`：实际redPacketStatus回调更新共享状态，原卡片/通知展示保持；新旧缓存脚本混用、缺少helper时保留旧处理而不报错。
- 对应四个min.js由corepack yarn run build生成；没有直接修改CSS或min文件。PC/mobile两套模板分别引用对应脚本，未改FTL或SCSS。
- `src/main/java/org/b3log/symphony/processor/ChatroomProcessor.java`：新核心参数viewOnly/riskAccepted/expectedUserId只能转交新核心目标；目标不存在时返回原错误JSON，不进入旧领取逻辑。普通旧历史请求不带这些新增字段，原流程保持。
- `src/test/java/org/b3log/symphony/repository/ApiV3ChatRedPacketRiskTest.java`：实际旧handler提取夹具补三类新参数遇不存在目标时拒绝、无转账/旧逻辑回落。
- `tools/api-v3-red-packet-status-ui-test.mjs`：源码/生成的四组脚本配对各三项，共12组；执行真实WS回调与共享状态/打开方法，DOM/模板滚动hook隔离适配，验证完成只读、旧事件不回滚、未知/非法不创建状态及缓存缺helper兼容。
- `API.md`、`UPDATE_LOG.md`、`AGENTS.md`、`docs/api-v3-progress.md`与本记录同步。website_v3只更新`docs/V3_MIGRATION.md`及本记录，源码不变。

## 验证与边界

12组状态测试、既有16组领取UI、Java25核心20组及资源build通过；8个改动JS版本审计0发现，无source map，git diff --check通过。v3仍290路由/文档290，无新入口。未重复website_v3全量构建或业务库测试。

IAB 1280×720本地夹具加载两套实际min聊天+channel脚本，模拟其他用户领完，再打开猜拳卡片，两套均仅捕获viewOnly/expectedUserId，没有风险弹窗或领取证明。使用Fake WebSocket及DOM适配，未连接真实业务服务；中途临时标签失效后在同一浏览器重建验证第二套，最终关闭临时页与服务。完整模板/真实WS投递/390px与移动触控仍待验收。

## 安全与反漏扫

没有新增匿名入口、anonymous.viewSkips、Cookie/apiKey/CSRF例外、CDN/上传下载/目录或SQL写入；原loginCheck及全局BeforeRequestHandler/AnonymousViewCheckMidware/CaptchaProcessor保持。收到领完事件只令UI更保守地只读，实际权限和结算仍由服务端判断；不从事件创建金融意图，不按姓名冒充用户。带新参数的不匹配目标拒绝，避免仅查看请求落入旧金融流程。8JS版本0/无map；实际新IP/黑名单/验证码/解封/开发模式、授权及生产目录枚举未验，不默认通过。

## 完整人工验收

1. 通过IDEA重编/重启本批Java，更新资源并强制刷新；本地若读取target/classes需同步脚本。无新SQL迁移。PC两套与mobile两套分别验证。
2. 本人页面已显示未领完的新猜拳/心跳，另一个专用账号领完；收到实时状态后点击，直接查看详情，不弹风险确认、不再发送风险领取证明。未知结果的原意图在完成后应清除。
3. 独立夹具乱序发送旧部分状态或旧历史快照，已完成不回退；错误ID/字符串份数/超限与未知消息不应新建本地状态。旧缓存脚本缺helper时原卡片更新仍继续。
4. 专用环境对不存在新核心目标发送viewOnly、riskAccepted、expectedUserId分别验证错误JSON且无扣款；旧历史正常gesture/dice字段仍按原流程。新核心合法领取、只读、重试按第110批完整步骤回归。
5. 检查普通/专属/拼手气、真实WS、账号切换/权限/验证码和PC/mobile触控；上述真实环境项目当前暂缓，不能以本地夹具代替。
