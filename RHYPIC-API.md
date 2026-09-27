# RhyPic 韵图上传对接文档（第三方客户端）

站点图床已切换为新的 **RhyPic（韵图）** 图床。旧的上传接口 `POST /upload` 将在不久后彻底停用，请所有第三方客户端按本文档迁移到新的两步上传流程。

> **生产环境信息**：韵图（OSS 上传/管理）与 CDN（公开访问）部署在同一台服务器，统一域名为 **`file.fishpi.cn`**（HTTPS）。即上传地址与文件公开地址均为该域名。

> **停用时间安排**：`POST /upload` 计划于 **【待公告：停用日期】** 正式停用。停用前旧接口仍可使用，但建议尽快完成适配。正式公告：【待公告：公告链接】。

---

## 对接流程总览

新流程分为两步，文件流直接上传到韵图图床，不再经过社区站点中转：

1. **获取上传票据**：`POST /api/rhypic/upload-ticket`（社区站点签发，证明当前用户身份）；
2. **直传韵图**：`POST {uploadURL}/api/v1/files`（携带票据，multipart 上传文件）。

```
客户端                社区站点(fishpi.cn)          韵图图床
  │                         │                         │
  │  ① 取票据(apiKey)        │                         │
  │ ───────────────────────>│                         │
  │  返回 ticket + uploadURL │                         │
  │ <───────────────────────│                         │
  │                         │                         │
  │  ② 直传文件(Bearer ticket)                        │
  │ ────────────────────────────────────────────────>│
  │            返回 {errFiles, succMap}               │
  │ <────────────────────────────────────────────────│
```

---

## 第一步：获取上传票据

`POST /api/rhypic/upload-ticket`

为当前登录用户签发**一次性**短期上传票据（Ed25519 签名的 JWT）。

**票据要点**

- 有效期 **180 秒**；
- `jti` **单次使用**：一张票据只能用于一次上传请求（一次请求可携带多个文件），每次上传前都要重新获取；
- 票据绑定当前登录用户，上传的文件归属该用户。

**请求（需登录）**

| 参数   | 位置 | 说明                                     | 示例         |
| ------ | ---- | ---------------------------------------- | ------------ |
| apiKey | 查询参数或请求体 | 通用密钥（与原 `/upload` 使用的相同） | YOUR_API_KEY |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/rhypic/upload-ticket?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0'
```

**响应**

| Key          | 说明             | 示例                                     |
| ------------ | ---------------- | ---------------------------------------- |
| code         | 0 成功，-1 失败  | 0                                        |
| msg          | 错误消息         | 图床通道未启用                           |
| data         | 票据信息         |                                          |
| - ticket     | 上传票据（JWT）  | eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.... |
| - uploadURL  | 韵图图床地址（生产环境为 `https://file.fishpi.cn`） | https://file.fishpi.cn                  |
| - expiresIn  | 票据有效期（秒） | 180                                      |

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "ticket": "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9....",
    "uploadURL": "https://file.fishpi.cn",
    "expiresIn": 180
  }
}
```

常见失败：站点未切换韵图通道（`图床通道未启用`）、服务端未配置票据私钥（`图床未配置上传地址或票据私钥`）、未登录（401）。

---

## 第二步：直传韵图

`POST {uploadURL}/api/v1/files`

`uploadURL` 使用第一步返回的值，**不要硬编码**。

**请求**

- 方法：`POST`，类型：`multipart/form-data`；
- 请求头：`Authorization: Bearer <ticket>`；
- 文件字段名固定为 **`file`**，可重复携带实现多文件上传。

| 项           | 说明                                  | 示例                            |
| ------------ | ------------------------------------- | ------------------------------- |
| Authorization | 请求头                                | Bearer eyJhbGciOiJFZERTQSIs.... |
| file         | 文件字段，可重复                      |                                 |

**响应（与旧 `/upload` 结构兼容，解析逻辑可直接沿用）**

| Key           | 说明                 | 示例                              |
| ------------- | -------------------- | --------------------------------- |
| code          | 0 成功，1 失败       | 0                                 |
| msg           | 错误消息             |                                   |
| data          | 上传结果             |                                   |
| - errFiles    | 上传失败的文件名列表 | `[ 'a.exe' ]`                     |
| - succMap     | 上传成功的文件地址   | `{}`                              |
| -- `<文件名>` | 文件公开地址         | https://file.fishpi.cn/f/123.png |

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "errFiles": [],
    "succMap": {
      "image.png": "https://file.fishpi.cn/f/2026/09/27/123.png"
    }
  }
}
```

---

## 完整示例

```bash
# 1. 取票据（以 jq 提取，也可用任意 JSON 库）
TICKET=$(curl -s -X POST 'https://fishpi.cn/api/rhypic/upload-ticket?apiKey=YOUR_API_KEY' \
  -H 'User-Agent: Mozilla/5.0' | jq -r '.data.ticket')

# 2. 直传韵图（单文件）
curl -X POST 'https://file.fishpi.cn/api/v1/files' \
  -H "Authorization: Bearer $TICKET" \
  -F 'file=@/path/to/image.png'

# 多文件：重复 -F file 即可（仍只使用同一张票据）
curl -X POST 'https://file.fishpi.cn/api/v1/files' \
  -H "Authorization: Bearer $TICKET" \
  -F 'file=@/path/to/a.png' \
  -F 'file=@/path/to/b.jpg'
```

JavaScript 示例（浏览器/Node 通用思路）：

```javascript
// 1. 取票据
const ticketResp = await fetch(
  'https://fishpi.cn/api/rhypic/upload-ticket?apiKey=YOUR_API_KEY',
  { method: 'POST' }
).then(r => r.json());
if (ticketResp.code !== 0) {
  throw new Error(ticketResp.msg || '获取上传票据失败');
}
const { ticket, uploadURL } = ticketResp.data;

// 2. 直传
const form = new FormData();
form.append('file', fileInput.files[0]);
const upResp = await fetch(`${uploadURL}/api/v1/files`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${ticket}` }, // 不要手动设 Content-Type，浏览器会带 boundary
  body: form
}).then(r => r.json());

// upResp.data.succMap / upResp.data.errFiles 结构与旧 /upload 相同
```

---

## 错误处理与重试建议

- 票据是一次性的，**不要缓存复用**；将「取票据 → 直传」封装为单次上传函数。
- 票据过期（错误信息 `上传票据已过期`）或已被使用（`上传票据已被使用`）时：重新取一次票据并重传，建议最多自动重试 1 次。
- 多文件上传为**部分成功**模式：成功的文件在 `succMap`，失败的在 `errFiles`，互不影响。
- 浏览器内直传需要韵图允许你的页面来源（CORS）；非浏览器环境（服务端/原生客户端）不受跨域限制。

---

## 与旧 `POST /upload` 的差异对照

| 变更点   | 旧 /upload                         | 新韵图直传                                      |
| -------- | ---------------------------------- | ----------------------------------------------- |
| 鉴权     | `apiKey` 请求参数                  | `Authorization: Bearer <票据>` 请求头，票据一次性 |
| 上传地址 | 社区站点 `/upload`                 | 韵图图床 `{uploadURL}/api/v1/files`             |
| 文件字段 | `file[]`                           | `file`                                          |
| 文件归属 | 站点本地/七牛                      | 韵图中对应的社区登录用户                        |
| 多文件   | 部分失败整体处理                   | 成功进 `succMap`，失败进 `errFiles`             |
| 限制策略 | 社区站点配置                       | 韵图用户组策略（扩展名/MIME/大小/审核）         |
| 响应结构 | `code/msg/data.errFiles/succMap`   | **完全相同**，可直接沿用解析逻辑                |
| 跨域     | 无                                 | 浏览器直传需韵图允许来源；非浏览器不受影响      |
