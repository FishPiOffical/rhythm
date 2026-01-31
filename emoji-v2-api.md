# 表情包 v2 接口文档（支持 apiKey）

> 更新时间：2026-01-31（基于当前代码，后续改动请同步本文件）。

## 总览
- 鉴权：登录 Cookie 或在请求 JSON 体中传 `apiKey`。如果同时存在，以 `apiKey` 用户为准。
- CSRF：`/api/emoji/**` 前缀的接口 **不需要 csrfToken**；兼容旧路径 `/emoji/**` 仍需要 csrfToken。
- Content-Type：`application/json;charset=UTF-8`（仅 `/emoji/upload` 额外支持表单上传）。
- 返回格式：`{ code: 0, msg: "", data: ... }`，`code=0` 成功。
- 排序规则：当 `sort<=0` 时，后端自动取“当前分组最大排序值 + 1”；迁移会按旧数据原顺序写入 `sort = 1,2,3...`。
- 关键字段：
  - 分组：`{ oId, name, sort, type }`，`type=1` 表示系统“全部”分组。
  - 分组表情项：`{ oId, emojiId, name, sort, url }`（`oId` 为分组内关联记录的 ID）。

## 错误码
| code | 说明 |
| --- | --- |
| 0 | 成功 |
| -1 | 失败，`msg` 提示原因（如“未找到分组”“历史表情为空，无需迁移”） |

## 接口清单

### 1. 获取分组列表
- `GET /api/emoji/groups`（推荐，无 CSRF）  
  兼容：`/emoji/groups`
- Body（可选）：`{ "apiKey": "xxx" }`
- Response `data`：分组数组；若用户缺少“全部”分组会自动创建后返回。

### 2. 获取分组内表情
- `GET /api/emoji/group/emojis?groupId={groupId}`
- Body（可选）：`{ "apiKey": "xxx" }`
- Response `data`：分组表情项数组，字段见“关键字段”。

### 3. 上传 URL 到“全部”分组
- `POST /api/emoji/upload`
- Body：`{ "apiKey": "xxx", "url": "https://.../a.png" }`
- 行为：新建表情并放入“全部”分组（`sort` 自动递增）。

### 4. 创建分组
- `POST /api/emoji/group/create`
- Body：`{ "apiKey": "xxx", "name": "我的包", "sort": 0 }`

### 5. 更新分组
- `POST /api/emoji/group/update`
- Body：`{ "apiKey": "xxx", "groupId": "g1", "name": "新名字", "sort": 5 }`

### 6. 删除分组
- `POST /api/emoji/group/delete`
- Body：`{ "apiKey": "xxx", "groupId": "g1" }`
- 说明：删除分组并清理该分组内的关联关系。

### 7. 向分组添加“已有表情”
- `POST /api/emoji/group/add-emoji`
- Body：
  ```json
  { "apiKey": "xxx", "groupId": "g1", "emojiId": "e1", "sort": 0, "name": "可选别名" }
  ```
- 说明：若目标分组不是“全部”，会自动在“全部”分组同步一份。

### 8. 向分组添加“URL 表情”
- `POST /api/emoji/group/add-url-emoji`
- Body：
  ```json
  { "apiKey": "xxx", "groupId": "g1", "url": "https://.../a.png", "sort": 0, "name": "可选" }
  ```
- 说明：同样会同步到“全部”；`sort<=0` 时自动追加到分组末尾。

### 9. 从分组移除表情
- `POST /api/emoji/group/remove-emoji`
- Body：`{ "apiKey": "xxx", "groupId": "g1", "emojiId": "e1" }`
- 说明：若 `groupId` 是“全部”，会同时从所有分组移除此表情。

### 10. 更新表情项（重命名 / 排序）
- `POST /api/emoji/emoji/update`
- Body：`{ "apiKey": "xxx", "oId": "item1", "groupId": "g1", "name": "新别名", "sort": 10 }`
- 说明：`oId` 为分组项 ID，需属于当前用户与指定分组。

### 11. 迁移旧表情到 v2
- `POST /api/emoji/emoji/migrate`
- Body：`{ "apiKey": "xxx" }`
- 行为：从云端 `gameId=emojis` 读取旧数据，按原顺序写入“全部”分组；空数据返回错误提示。

### 12. 通用错误示例
```json
{ "code": -1, "msg": "未找到分组" }
```

## 常用调用示例（curl）
```bash
# 使用 apiKey 获取分组（GET 也带 JSON 体）
curl -X GET -H "Content-Type: application/json" \
  -d '{"apiKey":"YOUR_API_KEY"}' \
  "${SERVE_PATH}/api/emoji/groups"

# 将远程图片收藏到指定分组并自动追加排序
curl -X POST -H "Content-Type: application/json" \
  -d '{"apiKey":"YOUR_API_KEY","groupId":"g1","url":"https://file.fishpi.cn/emoji/demo.png","name":"demo","sort":0}' \
  "${SERVE_PATH}/api/emoji/group/add-url-emoji"

# 迁移旧表情到 v2
curl -X POST -H "Content-Type: application/json" \
  -d '{"apiKey":"YOUR_API_KEY"}' \
  "${SERVE_PATH}/api/emoji/emoji/migrate"
```

如需补充批量排序/批量删除等内部接口示例，请告知。***
