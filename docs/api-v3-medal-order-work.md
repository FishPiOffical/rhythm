# v3 本人勋章相邻排序（2026-10-22）

内部工程交接；预算、锁与故障说明不能整体复制到公开API文章。

## 实际文件与接线

- `src/main/java/org/b3log/symphony/processor/ApiV3MedalOrderProcessor.java`：POST路由、Bearer settings.write、共享写门禁、严格JSON/幂等键读取。
- `src/main/java/org/b3log/symphony/service/ApiV3MedalOrderService.java`：当前有效owner锁、完整持有集合、稳定定义/持有锁、相邻交换及全量序号规范化，仅写display_order；与回执严格同事务。
- `src/main/java/org/b3log/symphony/util/ApiV3MedalOrderInputs.java`：仅direction字段、大小写规范化up/down且不trim，存量order精确有符号32位校验。
- `src/test/java/org/b3log/symphony/service/ApiV3MedalOrderTest.java`：生产SQL/控制器/路由/鉴权/幂等及实际JdbcTransaction保存点和提交故障。
- `tools/api-v3-medal-order-test.mjs`：Java25隔离编译运行，SQLite/Token/请求适配器与临时目录清理；修复未完成稿错误引用不存在的ApiV3MedalOrder工具，实际编译ApiV3MedalDisplay与ApiV3MedalOrderInputs。
- 本文：行为、公开片段、证据、人工链路和未验收项。

总接线由主代理添加`ApiV3MedalOrderProcessor.register();`；旧入口及display服务、SQL、Repository、前端、公共文档未在本工作包修改。

## 业务与实现边界

- `POST /api/v3/me/medals/{medalId}/move`，settings.write，Idempotency-Key。仅当前Bearer账号的持有；query/body/Cookie/apiKey不能替换owner。
- medalId复用display的精确字符串规则：1–128 UTF-16、非全空白、无控制或孤立代理项，允许`0`和Unicode，保留原串。direction仅up/down，忽略大小写、不删除首尾空白。
- 沿用旧相邻移动：本人全部持有都参与，包括隐藏和过期项，目标也允许过期；不执行过期删除。按`display_order`升序，再按持有行oId的Java字符串顺序稳定并列；这是现有v3读取采用的稳定并列方式，不继承旧仓库未指定的返回顺序。
- 交换一个邻居后，整份列表从0连续重编号；第一项up、最后项down也会规范化原有不连续序号。只更新不相等的display_order，保留display、expire_time、data、积分及其他owner。
- 先锁当前user，再根据有界持有提示排序锁medal定义，最后按oId锁本人持有；与v3 display的user→definition→holding相容。定义不存在/重复、重复持有、无效ID、非整数或越界order、无效display/expiry返回409；不存在或不属于本人的目标404。
- hints和当前锁定持有分别最多4097行，超过4096技术预算整请求503并回滚，不截断或假成功。budget是技术防放大边界，不作为公开业务规则发布。合法4096项实际专项完整执行，4097拒绝。
- `medal.order`独立幂等operation，规范化后的direction参与请求哈希；同key同请求恢复本次id/direction/order，旧回执不重复移动，也不覆盖后来排序；同key不同目标/方向409。重放前同样在严格根内当前锁owner，停用账号拒绝旧key。
- mutation与回执同严格根事务，保存点保护整份重编号；局部回滚失败中止根，不允许部分提交。确认丢失后用原key恢复持久回执。

## 隔离证据

执行`node tools/api-v3-medal-order-test.mjs`，Java25，最终退出0，日志`/tmp/rhythm-v3-medal-order.log`，8组PASS：

1. 实际POST/settings.write/Bearer/currentowner；相邻交换、全量规范化、仅order字段、规范化重放、不覆盖后续值、失效账号重放拒绝。
2. 未鉴权401、缺scope403、错误JSON/字段/direction/key/path；Cookie/apiKey/body owner不能提升，输入失败无SQL写。
3. 隐藏/过期邻居与过期目标、同order稳定并列、边界重编号、无过期清理或display变更。
4. 重复/缺失定义与持有、损坏order/expiry/display409且无回执；无持有目标404。
5. 实际SQL/回执/strict JdbcTransaction提交故障全回滚；提交成功但确认丢失可原key恢复，不重复移动。
6. 保存点局部回滚、调用方捕获后根提交不产生部分结果；保存点回滚失败中止根；primitive无事务拒绝。
7. 77项完整列表超过旧默认页，无邻居或规范化截断。
8. 4096技术预算完整成功，4097提示或当前持有均503，无写入/回执/截断。

SQL由生产Service原样执行，仅SQLite移除FOR UPDATE；Token识别、UserRepository、HTTP响应封装、数据库连接和事务装配是测试适配器。Dispatcher绑定从生产路由声明提取并检查门禁，auth/write/controller和idempotency/strict commit/savepoint均为生产代码。真实JDBC行锁、MySQL隔离/排序规则及多连接竞争仍未证明。

## 完整人工链路与限制

联调前由用户通过IDEA重新编译或重启服务端，不能把旧Java行为当新实现失败；本包没有FTL/JS/SCSS，无需前端重建。

1. A/B取得settings.write与profile.read，A读`GET /api/v3/me/medals`及PC/mobile勋章页，记录当前顺序、显示、期限和积分；A对一项POST move/down，200包含id/direction/order，重新GET及页面顺序一致；B的排序和积分、A显示/期限均不变。
2. 使用新key up移回，再重放最初down key：只返回旧回执，当前顺序不回退。同key改direction/目标409；`DOWN`接受并规范化down，` down`拒绝422。边界up/down仍规范化全序号，不报越界错误。
3. 测试库准备同order、隐藏/过期邻居和过期目标，确认它们仍参与；GET有效勋章可看不到过期项，因此相邻移动后可见子集顺序可能没有变化。不删除任何持有。准备77项及不连续/负序号，移动后全表从0连续，不能只查第一页。
4. 缺Bearer或仅Cookie/apiKey401，缺scope403；query/body userId不能代人操作；不属于本人的目标404。注销/停用A后使用原key也拒绝；错误字段、非JSON、重复JSON键、缺key、无效/超长ID和direction分别拒绝且数据库不变。
5. 开发测试库准备重复定义/持有、邻居定义缺失、损坏数字409；超技术预算503，整列表无变化。注入某一order写、回执、提交失败，全表/回执一起回滚；提交后确认丢失原key恢复。与新display/其他move、旧排序/授予/撤销/显示并发验证锁及死锁重试。
6. 回归黑名单→`/test`、匿名首访/计次、`validateCaptcha`解封、开发关闭盾；检查全站静态/CDN版本、sourceMappingURL、目录及上传下载返回路径。这些真实链路与公网扫描本轮未运行。

旧MedalService排序、授予、过期清理、显示等链路仍有独立事务/整行更新，并未统一取得新actor锁；不能宣称新旧整体原子化或消除旧快照覆盖新排序的竞争。新路径只写order并严格提交，但旧全行写仍可能覆盖order或其他字段。没有未经用户确认改变旧入口失败行为。

未运行Maven、完整IoC、真实MySQL/8080/WS、PC/mobile浏览器、生产或全站反漏扫。无新表/迁移/Repository、上传下载、静态/CDN、HTML渲染或服务器URL抓取。

## 安全与反漏扫源码结论

新增入口显式Bearer+settings.write，不新增匿名入口、anonymous.viewSkips或Cookie授权写路径；当前owner、目标集合和回执绑定，不接受请求指定他人。参数化SQL、精确字段与字符串/数值校验、输出仅id/direction/order；无HTML或内容渲染、远程抓取，因此本变更无新增SQL注入/SSRF/HTML执行路径，客户端仍应文本显示ID。共享写门禁及有界持有预算控制单请求放大，真实分布式压力未验。存量防扫描/黑名单/验证码链未改，不能据此声称整站反漏扫通过。

## 可并入API.md的公开片段

### 移动本人勋章顺序

`POST /api/v3/me/medals/{medalId}/move`

将本人勋章上移或下移一位，需要Bearer accessToken、settings.write与Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次移动的幂等键 | medal-move-001 |
| medalId | 路径参数，本人勋章ID，1–128个UTF-16单位，保留原值 | 0 |
| direction | up上移、down下移，忽略大小写，不接受首尾空白 | down |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/medals/0/move' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: medal-move-001' \
  --header 'Content-Type: application/json' \
  --data '{"direction":"down"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 本次移动结果 | `{...}` |
| - id | 勋章ID | 0 |
| - direction | 规范化方向 | down |
| - order | 本次移动后的序号，从0开始 | 1 |

> 隐藏和过期勋章也参与排序，不会因此被删除。每次按当前顺序与相邻勋章交换，再整理全部序号；边界移动仍成功。同序号按持有记录标识稳定排列。
>
> 同键同请求恢复原结果，不重复移动或覆盖后续排序；同键改变请求返回409。目标不存在或不属于本人返回404，存量状态冲突409，暂时无法排序503，非法字段422。

主代理最终需同步API.md及UPDATE_LOG.md，并提醒用户同步网站API文章https://fishpi.cn/article/1636516552191。
