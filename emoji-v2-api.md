# 表情包 v2 接口文档（仅 `/api/emoji/**`，无需 CSRF）

> 更新时间：2026-01-31（基于当前代码）。

## 总览
- **鉴权**：登录 Cookie 或请求 JSON 体中的 `apiKey`（如同时存在，以 `apiKey` 为准）。
- **CSRF**：已统一使用 `/api/emoji/**`，不需要 csrfToken；旧 `/emoji/**` 路径已废弃。
- **Content-Type**：`application/json;charset=UTF-8`（仅上传接口也接受表单）。
- **返回格式**：`{ code: 0, msg: "", data: ... }`，`code=0` 成功。
- **排序规则**：`sort<=0` 时自动取“当前分组最大排序 + 1”；迁移按旧顺序写入 `sort = 1,2,3...`。
- **字段**：分组 `{oId,name,sort,type}` (`type=1` 为“全部”); 分组项 `{oId,emojiId,name,sort,url}`。

## 接口列表

1. **获取分组列表**  
   `GET /api/emoji/groups`  
   Body 可选：`{"apiKey":"xxx"}`  
   说明：缺少“全部”分组会自动创建后返回。

2. **获取分组内表情**  
   `GET /api/emoji/group/emojis?groupId={groupId}`  
   Body 可选：`{"apiKey":"xxx"}`  

3. **上传 URL 到“全部”分组**  
   `POST /api/emoji/upload`  
   Body：`{"apiKey":"xxx","url":"https://.../a.png"}`  

4. **创建分组**  
   `POST /api/emoji/group/create`  
   Body：`{"apiKey":"xxx","name":"我的包","sort":0}`  

5. **更新分组**  
   `POST /api/emoji/group/update`  
   Body：`{"apiKey":"xxx","groupId":"g1","name":"新名字","sort":5}`  

6. **删除分组**  
   `POST /api/emoji/group/delete`  
   Body：`{"apiKey":"xxx","groupId":"g1"}`  

7. **分组添加已有表情**  
   `POST /api/emoji/group/add-emoji`  
   Body：`{"apiKey":"xxx","groupId":"g1","emojiId":"e1","sort":0,"name":"可选名称"}`  
   说明：若目标分组不是“全部”，会自动同步到“全部”分组。

8. **分组添加 URL 表情**  
   `POST /api/emoji/group/add-url-emoji`  
   Body：`{"apiKey":"xxx","groupId":"g1","url":"https://.../a.png","sort":0,"name":"可选名称"}`  
   说明：自动同步到“全部”，`sort<=0` 时追加到末尾。

9. **从分组移除表情**  
   `POST /api/emoji/group/remove-emoji`  
   Body：`{"apiKey":"xxx","groupId":"g1","emojiId":"e1"}`  
   说明：若 `groupId` 为“全部”，会同时从所有分组移除。

10. **更新表情项（重命名/排序）**  
    `POST /api/emoji/emoji/update`  
    Body：`{"apiKey":"xxx","oId":"item1","groupId":"g1","name":"新名称","sort":10}`  

11. **迁移旧表情到 v2**  
    `POST /api/emoji/emoji/migrate`  
    Body：`{"apiKey":"xxx"}`  

12. **错误示例**  
    ```json
    { "code": -1, "msg": "未找到分组" }
    ```

## Curl 示例
```bash
# 获取分组
curl -X GET -H "Content-Type: application/json" \
  -d '{"apiKey":"YOUR_API_KEY"}' \
  "${SERVE_PATH}/api/emoji/groups"

# 将远程图片收藏到指定分组
curl -X POST -H "Content-Type: application/json" \
  -d '{"apiKey":"YOUR_API_KEY","groupId":"g1","url":"https://file.fishpi.cn/emoji/demo.png","name":"demo","sort":0}' \
  "${SERVE_PATH}/api/emoji/group/add-url-emoji"

# 迁移旧表情
curl -X POST -H "Content-Type: application/json" \
  -d '{"apiKey":"YOUR_API_KEY"}' \
  "${SERVE_PATH}/api/emoji/emoji/migrate"
```
