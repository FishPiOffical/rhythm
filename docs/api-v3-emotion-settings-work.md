# v3 常用表情设置写入

## 文件与行为

- `src/main/java/org/b3log/symphony/processor/ApiV3EmotionSettingsProcessor.java`：`PUT /api/v3/me/settings/emotions`，settings.write + 共享写守卫，JSON UTF-8 请求体上限 16384 字节，Idempotency-Key。
- `src/main/java/org/b3log/symphony/util/ApiV3EmotionSettingsInputs.java`：`emotions` 必须为字符串数组；当前 `ApiV3EmotionOptions` 目录唯一别名动态确定白名单与数量上限，不含 `endOfEmoji` 哨兵；保留顺序，重复、空白、未知及目录外项 422。用户已明确确认目录白名单与空数组恢复默认。
- `src/main/java/org/b3log/symphony/service/ApiV3EmotionSettingsService.java`：严格根事务，当前读锁有效 actor，回执读取及重放同样重新确认有效账号；只删除本人 `emotionType=0` 行并有序插入，其他账号/其他类型完整保留。选项替换、回执同提交，primitive 保存点失败不伤调用方其他写入，保存点回滚失败终止整笔事务。空数组物理清空，响应为默认11项，与真实 GET 一致；相同 key 重放返回原结果，不覆盖后续设置。只投影 emotions 数组，响应不返回 owner/行ID/内部回执。
- `src/test/java/org/b3log/symphony/service/ApiV3EmotionSettingsTest.java`：7组真实 SQL、Dispatcher、auth、严格提交故障专项，复用既有职业主设置的 SQLite/JDBC fixture。
- `tools/api-v3-emotion-settings-test.mjs`：Java 25 定向隔离 runner，临时目录退出即清理，未使用 Maven。

旧链路：两端 `home/settings/function.ftl` → `settings.js Settings.update` → `POST /settings/emotionList` → `SettingsProcessor#updateEmoji` → `EmotionMgmtService#setEmotionList`。旧输入为逗号分隔，跳过空白、重复和非 `Emotions.isEmoji` 项（其完整名单比页面目录大）；没有显式数量上限。旧链路保持不变。新接口采用已确认的独立严格数组契约。

EmotionQueryService 与 v3 GET 均直接查询数据库，无独立表情缓存。新v3锁 actor 保证 v3 间串行；旧 setEmotionList 不锁 actor，且删该用户所有 emotion 类型，新旧整体并发串行不属于本次承诺。没有新增表/Repository/schema、前端或生成产物变更。

## 专项结果

运行 `node tools/api-v3-emotion-settings-test.mjs`，7组通过：

1. 真实 Dispatcher PUT/Bearer/settings.write/write guard，顺序替换与真实 ApiV3EmotionQueryService GET 回读，其他owner/type保留，无积分变化。
2. 空数组删除 type0 并返回默认；后续新值设置后重放原 key 不覆盖；同key不同内容409，当前停用账号404。
3. 动态完整白名单及最大完整目录 SQL 写入；重复、空白、非字符串、EOF、完整 emoji 库但目录外别名、额外字段、超数量拒绝。
4. 无效/缺失 Bearer、错误scope、Cookie/apiKey伪造owner、重复/非对象JSON、缺幂等key、UTF-8体积超限。
5. 删除后/插入后/receipt/strict commit/savepoint建立及释放故障：列表和回执完整回滚，相同key可重试。
6. 已提交但回执确认丢失后幂等恢复；认证与事务之间账号停用失败且不写。
7. primitive必须已有事务；保存点回滚保留无关写入；保存点无法回滚时整根终止。

SQLite 仅验证真实参数化SQL、事务控制与实际 bundled Latke strict 包装，不模拟 MySQL 行锁竞争。未跑真实 MySQL、实际HTTP服务、PC/mobile浏览器、全站反漏扫；无 Maven、迁移、部署或提交推送。

## 安全与反漏扫复核

- 未鉴权：真实 middleware 专项确认仅 Bearer settings.write 准入，Cookie/query/body apiKey 不成为该API身份；不存在新增匿名入口。
- 越权：actor仅由 v3 identity 提取，拒绝 body owner字段；SQL owner/type 双谓词；重放前当前读账号。
- CSRF：Bearer-only middleware 不接受页面 Cookie 身份，无新增Cookie写入入口。
- XSS：正文只允许固定目录别名，无HTML/URL/自由文本；EOF排除。
- 注入：全部入参绑定，表名来自已有 Repository。
- SSRF：不接URL、不抓取远程内容。
- 滥用：共享写守卫、请求体与目录数组自然上限；发布公开文档不披露内部阈值。
- 敏感信息：固定数组投影，异常仅通用信息；不返回完整user、内部row/receipt。
- 匿名/黑名单/验证码：没有修改 BeforeRequestHandler、AnonymousViewCheckMidware、CaptchaProcessor 或 anonymous.viewSkips；真实IP跳转/解封仍需完整服务回归，不宣称隔离测试覆盖。
- 静态资源/CDN/版本号/sourceMappingURL/目录枚举/上传下载：本模块未新增资源、CDN URL、路径返回或相关入口；全站实际扫描未执行。

## 人工完整链路

先通过 IDEA 重新编译或重启服务端，确认读到当前类；不要用旧运行行为评判源码。

1. 取得带 profile.read/settings.write 的 v3 token，GET `/api/v3/me/settings/emotion-options`，从目录挑 `sob,+1,smile`。
2. 带 `Authorization: Bearer ...` 和新 `Idempotency-Key` PUT `{"emotions":["sob","+1","smile"]}`；成功响应 emotions 保持该顺序，GET `/api/v3/me/settings/emotions` 应完全一致；PC与mobile设置页/编辑器读取本人列表时也应按原链路显示该顺序。
3. 新key PUT `{"emotions":[]}`，成功响应和GET恢复默认11项；检查数据库仅该owner type0清空，预置其他owner/type无变化。
4. 新key写 `["joy"]`，重放步骤2原key，返回原数组但GET仍 `["joy"]`；原key改请求内容应409。
5. 分别测试重复/空白/未知/EOF/非字符串/目录外 `huaji`，422且原列表不变；重复JSON或非对象400；无Bearer401、只有profile.read403、缺key422、超体积413。
6. 两个账号分别改自己的列表互不影响；带Cookie/apiKey但不带Bearer仍401；停用账号后重放旧key也不可成功。
7. 在真实MySQL做事务故障与并发新v3替换检查：不能看到半列表或重复提交；旧新并发覆盖语义另行观察，不能宣称已改造旧链路。

## API.md 片段

### 设置本人常用表情

`PUT /api/v3/me/settings/emotions`

完整替换常用表情及顺序。可选别名来自表情选项目录；空数组恢复默认常用表情。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | `Bearer` 访问令牌，需要 `settings.write` | `Bearer ACCESS_TOKEN` |
| Idempotency-Key | 本次操作的幂等键 | `emotions-selection-001` |
| emotions | 不重复的表情别名数组，顺序保留；仅接受选项目录中的别名 | `["sob","+1","smile"]` |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/me/settings/emotions' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: emotions-selection-001' \
  -H 'Content-Type: application/json' \
  -d '{"emotions":["sob","+1","smile"]}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 设置结果 | — |
| - emotions | 本次设置后的有效常用表情；空数组请求时返回默认列表 | `["sob","+1","smile"]` |

> 重复、空白、未知或目录外别名返回 422。相同幂等键与内容返回原操作结果，不覆盖之后的新设置。

## UPDATE_LOG.md 片段

- 新增 `PUT /api/v3/me/settings/emotions`：按选项目录完整设置本人常用表情并保留顺序，空数组恢复默认；支持幂等请求，拒绝重复、空白及目录外别名。
