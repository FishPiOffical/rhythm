# 表情包 v2 接口文档（含 apiKey 调用）

> 更新时间：2026-01-31（基于当前代码）。所有接口已在 `EmojiProcessor.register()` 中注册。

## 0. 通用说明
- **鉴权**：登录 Cookie；可选 `apiKey` 放在请求 JSON 体，提供则按 apiKey 识别用户，否则使用当前登录用户。
- **CSRF**：为方便客户端调用，新增 `/api/emoji/**` 前缀的同名接口，**不需要 csrfToken**，推荐客户端使用；旧路径 `/emoji/**` 仍需 csrfToken。
- **Content-Type**：`application/json;charset=UTF-8`（`/emoji/upload` 也接受表单）。
- **成功/失败**：`code = 0` 表示成功；`msg` 为错误信息。
- **GET + apiKey 提醒**：代码只从 `requestJSON().optString("apiKey")` 读取 apiKey；若要用 apiKey 调用 GET，请在 GET 也附带 JSON 体，否则请用登录 Cookie。

公共字段：
- 分组：`{ oId, name, sort, type }`，`type=1` 表示“全部”分组。
- 分组表情项：`{ oId, emojiId, name, sort, url }`，`oId` 为分组项 ID，`emojiId` 为表情图片 ID。

---

## 1. 获取分组列表
- **GET** `/api/emoji/groups`（无 CSRF，推荐）
- 兼容：`/emoji/groups`（需 csrfToken）
- **Body（可选）**：`{ "apiKey": "xxx" }`
- **Response**：
  ```json
  {
    "code": 0,
    "data": [
      {"oId":"g-all","name":"全部","sort":0,"type":1},
      {"oId":"g-1","name":"自定义","sort":1,"type":0}
    ]
  }
  ```
- 说明：若用户不存在“全部”分组，会自动创建。

## 2. 获取分组内表情
- **GET** `/api/emoji/group/emojis?groupId={groupId}`（无 CSRF）
- 兼容：`/emoji/group/emojis`（需 csrfToken）
- **Body（可选）**：`{ "apiKey": "xxx" }`
- **参数**：`groupId` 必填（query）
- **Response**：
  ```json
  {
    "code": 0,
    "data": [
      {"oId":"item1","emojiId":"e1","name":"别名","sort":0,"url":"https://..."}
    ]
  }
  ```

## 3. 一键上传表情到“全部”分组
- **POST** `/api/emoji/upload`（无 CSRF）
- 兼容：`/emoji/upload`（需 csrfToken）
- **Body**：`{ "apiKey": "xxx", "url": "https://file.fishpi.cn/emoji/demo.png" }`
- 行为：创建表情记录并自动加入当前用户“全部”分组。

## 4. 创建分组
- **POST** `/api/emoji/group/create`
- 兼容：`/emoji/group/create`
- **Body**：`{ "apiKey": "xxx", "name": "我的包", "sort": 0 }`

## 5. 更新分组
- **POST** `/api/emoji/group/update`
- 兼容：`/emoji/group/update`
- **Body**：`{ "apiKey": "xxx", "groupId": "g1", "name": "新名字", "sort": 5 }`

## 6. 删除分组
- **POST** `/api/emoji/group/delete`
- 兼容：`/emoji/group/delete`
- **Body**：`{ "apiKey": "xxx", "groupId": "g1" }`
- 说明：删除分组并清理其中的表情关联。

## 7. 分组添加“已存在”表情
- **POST** `/api/emoji/group/add-emoji`
- 兼容：`/emoji/group/add-emoji`
- **Body**：
  ```json
  { "apiKey": "xxx", "groupId": "g1", "emojiId": "e1", "sort": 0, "name": "可选别名" }
  ```
- 说明：若目标分组不是“全部”，会自动在“全部”分组同步一份。

## 8. 分组添加 URL 表情
- **POST** `/api/emoji/group/add-url-emoji`
- 兼容：`/emoji/group/add-url-emoji`
- **Body**：
  ```json
  { "apiKey": "xxx", "groupId": "g1", "url": "https://.../a.png", "sort": 0, "name": "可选" }
  ```
- 说明：同样会同步到“全部”分组。

## 9. 从分组移除表情
- **POST** `/api/emoji/group/remove-emoji`
- 兼容：`/emoji/group/remove-emoji`
- **Body**：`{ "apiKey": "xxx", "groupId": "g1", "emojiId": "e1" }`
- 说明：如果 `groupId` 是“全部”分组，会从所有分组一起移除该表情。

## 10. 更新表情项（重命名 / 排序）
- **POST** `/api/emoji/emoji/update`
- 兼容：`/emoji/emoji/update`
- **Body**：`{ "apiKey": "xxx", "oId": "item1", "groupId": "g1", "name": "新别名", "sort": 10 }`
- 说明：`oId` 为分组项 ID，需确认该项属于当前用户与分组。

## 11. 迁移旧表情到 v2
- **POST** `/api/emoji/emoji/migrate`
- 兼容：`/emoji/emoji/migrate`
- **Body**：`{ "apiKey": "xxx" }`
- 行为：读取云端旧数据 `gameId=emojis`，逐条写入用户“全部”分组；若旧数据为空返回错误“历史表情为空，无需迁移”。

## 12. 通用错误示例
```json
{ "code": -1, "msg": "未找到分组" }
```

## Curl 示例（使用 apiKey）
```bash
# 获取分组（GET 也带 JSON 体以便识别 apiKey）
curl -X GET \
  -H "Content-Type: application/json" \
  -H "csrfToken: ${CSRF}" \
  -d '{"apiKey":"YOUR_API_KEY"}' \
  "${SERVE_PATH}/emoji/groups"

# 分组添加 URL 表情
curl -X POST \
  -H "Content-Type: application/json" \
  -H "csrfToken: ${CSRF}" \
  -d '{"apiKey":"YOUR_API_KEY","groupId":"g1","url":"https://file.fishpi.cn/emoji/demo.png","name":"demo"}' \
  "${SERVE_PATH}/emoji/group/add-url-emoji"
```

---
若需补充批量排序等内部接口或更详细字段示例，请告知。
