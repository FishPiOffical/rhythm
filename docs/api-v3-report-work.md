# v3 举报（2026-10-23）

## 实际范围和文件

- `src/main/java/org/b3log/symphony/processor/ApiV3ReportProcessor.java`：GET `/api/v3/report/options` content.read；POST `/api/v3/reports` content.write、共享写预算、幂等键，正文64KiB预算；只取Bearer actor。
- `service/ApiV3ReportQueryService.java`：当前有效actor读取既有四类目标/理由白名单，不返回举报历史或目标数据。
- `service/ApiV3ReportPublicationService.java`：严格根事务及保存点；当前目标授权、禁止自举报；同举报者/类型/目标未处理记录按最早ID复用，已处理后新key可再报；原key返回原ID。
- `util/ApiV3ReportInputs.java`：白名单字段和typed枚举，memo单次去HTML/trim为纯文本，再存储时单次HTML编码供PC/移动管理模板直接输出；原始及编码后的长度都限10240 UTF-16，超预算422。
- `src/test/java/org/b3log/symphony/processor/ApiV3ReportTest.java`、`src/test/java/org/b3log/symphony/service/ApiV3ReportPublicationTest.java`、`tools/api-v3-report-test.mjs`：Java25真实SQL、真实auth/Dispatcher/controller、真实幂等和strict JDBC提交专项。

没有修改旧举报入口、管理模板、Repository/table、总register及公共文档；由root统一接入。无新增SQL迁移或前端产物。

## 事务与权限

事务前只读目标hint取作者集合；hint异常暂存。固定排序锁actor和可取得的hint作者，再锁本人确定回执。重放只强制actor当前有效，不依赖目标仍存在/可读/未处理，额外hint作者缺失、停用或hint正文损坏不阻止重放。fresh才抛hint错误，目标SQL错误503；fresh当前锁目标并重验作者/父文章/作者集合与hint一致，禁止动态扩用户锁。

文章只需当前普通可读，不要求购买隐藏reward段；讨论邀请使用真实UserMentionCandidates精确解析和当前actor，私密评论需评论正文可读。评论父文章先于评论锁。聊天作者只认发布外壳userOId，不从昵称反推。目标与其作者当前有效，自举报404。全部SQL值参数化；不返回原始内容、作者对象或备注。existing pending重复历史不清理，只复用最早ID。

同actor的不同key通过当前用户行锁串行查重，v3之间可跨实例串行；旧入口未共用核心/无唯一约束，不能声称新旧并发绝无重复。

## 验证

`node tools/api-v3-report-test.mjs`：GET 3组、POST 6组通过，使用实际项目Java源、bundled Latke/JDBC strict、实际Jsoup jar、实际UserMentionCandidates、真实SQLite参数化SQL与故障注入；仅Repository命名/SQL执行与Token认证外部依赖适配。SQLite不模拟MySQL行锁/多实例互斥。

覆盖：分类精确投影；actor/目标缺失停用；Bearer范围/旧Cookie/apiKey冒充；四目标讨论/私密/自举报；chat无userOId拒绝；memo存储XSS/编码膨胀；不同请求同key冲突；pending复用/处理后新key/旧key重放；目标删除、作者删除/停用、目标超预算仍重放；hint/锁读/写/回执/提交/保存点异常回滚；丢失提交应答恢复；归属变化拒绝动态扩锁；真实POST Dispatcher及无缓存。

未运行Maven、真实MySQL或完整服务；未实际执行PC/移动管理员页面、并发多实例、防扫描黑名单验证码端到端回归。

## 人工全链路

先通过IDEA重新编译/重启服务端，确认既有v3幂等迁移已部署。

1. 使用content.read令牌GET options，核对四类targetType、reason标签；无/无效令牌401，错误scope403，不得接受Cookie或query apiKey代替Bearer。
2. 使用content.write令牌POST普通他人文章，取返回id；相同key同body重试返回同id；新key相同未处理目标仍同id；后台PC/移动举报列表看到一条记录。备注含`<b>`和实体形式脚本内容，后台仅显示文字，不执行脚本。
3. 后台将举报处理，换key再次POST得到新id；原key仍返回旧id。删除目标或停用作者后原key仍返回旧id；停用reporter后重放404。
4. 分别举报公共评论、已邀请讨论、用户、聊天消息成功。未邀请讨论/无权私密评论/不存在或停用目标/自举报404；聊天消息仅旧昵称无userOId时拒绝；article含未购买reward段仍可举报其当前公开部分。
5. 非法类型/理由/目标ID、额外owner字段、非字符串memo、超长度或编码后超存储预算422，缺幂等键422，相同key不同内容409，均不新增举报或残留回执。
6. MySQL两连接同reporter不同key并发举报同target，确认只一条pending；模拟写/commit失败无举报/回执，恢复后同key可成功。旧入口并发另作存量限制验收。
7. 新IP、黑名单IP访问以及`/validateCaptcha`解封、开发模式关闭风控，各检查既有拦截行为，不能用隔离测试代替。

## 反漏扫与安全结论（源码层）

- 两入口均Bearer明确scope，无匿名裸入口；未修改anonymous.viewSkips、BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor。真实黑名单/验证码链路未验。
- 未鉴权/越权：controller只用Bearer，根内重查actor及fresh当前目标权限；重放仅本人同body。
- CSRF：独立Bearer入口，不从Cookie/query apiKey推身份；未改旧兼容入口。
- XSS：memo先纯文本再存储HTML编码，PC/移动管理模板现有直接输出安全；页面实际显示未验。
- SQL注入：ID/枚举校验且SQL参数绑定；SSRF：不抓取URL。批量滥用：共享写预算及正文/查询预算，未做压测。
- 敏感信息：仅返回举报id及分类白名单；不回传target、原文、身份全对象、内部receipt。
- 无新增公网静态/CDN/sourceMappingURL、目录/上传下载路径；未全仓全站复扫CDN及运行目录枚举。

## 公开API片段（供root整合）

### 举报分类

`GET /api/v3/report/options`

获取可用举报目标和理由。需要Bearer Token（content.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/report/options' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.targets | 举报目标数组 | [] |
| - targetType | 0文章、1评论、2用户、3聊天室消息 | 0 |
| - label | 目标名称 | 文章 |
| - reasons | 当前目标可用理由 | [] |
| -- reason | 理由编号 | 49 |
| -- label | 理由名称 | 其他 |

### 提交举报

`POST /api/v3/reports`

举报当前可读的有效他人目标。需要Bearer Token（content.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| Idempotency-Key | 本次请求幂等键 | report-001 |
| targetType | 0文章、1评论、2用户、3聊天室消息 | 0 |
| targetId | 目标ID字符串 | 1636516552191 |
| reason | 分类接口返回的理由编号 | 49 |
| memo | 可选纯文本备注，去HTML并去首尾空白，最多10240字符；格式或存储长度约束不满足时422 | 需要核实 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/reports' -X POST -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: report-001' -H 'Content-Type: application/json' --data '{"targetType":0,"targetId":"1636516552191","reason":49,"memo":"需要核实"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.id | 举报ID | 1761000000000 |

> 不支持自举报；聊天室消息理由仅49。同一目标已有本人未处理举报时返回原ID；处理后可使用新幂等键再次举报。相同幂等键和请求内容始终返回原举报ID，不创建新举报；同键不同内容返回409。
