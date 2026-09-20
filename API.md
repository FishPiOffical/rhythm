# 摸鱼派 API 文档

## 注意事项

* 请一定要在请求时带上 UA，推荐使用 Chrome 的 UA，空 UA 将返回 500 状态码
* 对单个接口的访问频率必须控制在最低1次/30秒，否则IP可能进入小黑屋（WebSocket、发送消息接口除外）
* 所有WebSocket频道设计时必须有断开自动重连机制

## 鉴权

摸鱼派社区 API 引入了 `apiKey` 的概念，对 API 的请求不需要提供 Cookie，只需要在参数中带上申请的 `apiKey` 即可。

> 注意：凡是 POST 请求，请求体必须是 JSON 格式，例如：`{ "nameOrEmail":"","userPassword":"" }`

### 获取 apiKey

`POST` `/api/getKey`

用于 API 获取摸鱼派的通用密钥，`Key` 即身份，`Key` 长期有效，如果服务器重启，则需要重新获取，建议配合 `/api/user` 接口定期检测 `Key` 是否有效。

请求:

| Key          | 说明                               | 示例                             |
| ------------ | ---------------------------------- | -------------------------------- |
| nameOrEmail  | 用户名或邮箱地址                   | username                         |
| userPassword | 使用 MD5 加密后的密码*             | e10adc3949ba59abbe56e057f20f883e |
| mfaCode      | 两步认证一次性密码（如未设置留空） | 123456                           |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/getKey' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
--data-raw '{
 "nameOrEmail":"username",
 "userPassword":"e10adc3949ba59abbe56e057f20f883e",
 "mfaCode":"123456"
}'
```

> <sup>*</sup> 注意：这里不支持直接传递明文，必须是32位小写MD5加密后的密码

响应：

| Key  | 说明                           | 示例                             |
| ---- | ------------------------------ | -------------------------------- |
| Key  | API 通用密钥，用于用户身份识别 | YOUR_API_KEY |
| msg  | 错误信息                       | 密码错误                         |
| code | 0 为请求成功，-1 失败          | -1                               |

### 查询用户信息

`GET` `/api/user?apiKey=<Key>`

通过指定的 API Key 查询用户信息（可以用来定期验证 API Key 是否有效）

请求:

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/user?apiKey=YOUR_API_KEY'
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

响应：

| Key                  | 说明                                                                  | 示例                                              |
| -------------------- | --------------------------------------------------------------------- | ------------------------------------------------- |
| code                 | 为0则密钥有效，为-1则密钥无效                                         | 0                                                 |
| msg                  | 错误信息                                                              | Invalid Api Key.                                  |
| data*                | 对应用户详细信息                                                      | `{ ... }`                                         |
| - oId                | Id                                                                    | 1630512345670                                     |
| - userNo             | 用户序号                                                              | 26                                                |
| - userName           | 用户名                                                                | imlinhanchao                                      |
| - userRole           | 用户组                                                                | OP                                                |
| - userNickname       | 昵称                                                                  | 我是跳跳吧                                        |
| - userAvatarURL      | 头像地址                                                              | https://...                                       |
| - userCity           | 最近所在城市，根据用户 IP 确定                                        | 深圳                                              |
| - userOnlineFlag     | 是否在线                                                              | true                                              |
| - onlineMinute       | 在线时长，单位分钟                                                    | 85467                                             |
| - userPoint          | 积分                                                                  | 183939                                            |
| - userAppRole        | 角色                                                                  | 0 = 黑客，1 = 画家                                |
| - userIntro          | 签名                                                                  | 人生而自由，卻無往不在枷鎖之中。                  |
| - userURL            | URL                                                                   | https://...                                       |
| - cardBg             | 卡片背景                                                              | https://...                                       |
| - followingUserCount | 关注用户                                                              | 5                                                 |
| - sysMetal           | 徽章列表, JSON**字符串**                                              | `{ ... }`                                         |
| -- list              | 徽章列表数据                                                          | `[ ... ]`                                         |
| --- attr             | 徽章数据，包含徽章图地址`url`, 背景色 `backcolor`, 前景色 `fontcolor` | url=https://...&backcolor=b91c22&fontcolor=ffffff |
| --- name             | 徽章名称                                                              | Operator                                          |
| --- description      | 徽章描述                                                              | 摸鱼派官方开发组成员                              |
| --- data             | 徽章数据                                                              | 无                                                |

> <sup>*</sup> 注意：若密钥无效，无 `data` 项目

### 注册用户

以下关于注册用户接口的内容我以提供注册思路为导向进行编写，方便大家对接。

**第一步** 获取一个图形验证码，要求用户识别并填写，带入到第二步的请求中

获取图形验证码可访问 `GET /captcha`

**第二步** 向摸鱼派请求获取短信验证码

`POST /register`

请求:

| Key        | 说明                                                                                                                                             | 示例        |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ----------- |
| userName   | 用户名                                                                                                                                           | adlered     |
| userPhone  | 手机号                                                                                                                                           | 13261327290 |
| invitecode | 邀请码（选填，无则留空，必须提供让用户填写邀请人的输入框，如果用户留空，可以将作者作为邀请人，但如果用户主动填写了邀请人，则必须按用户输入请求） | 000000      |
| captcha    | 第一步的验证码（大小写不敏感）                                                                                                                   | abcd        |

**第三步** 验证短信验证码是否正确

`GET /verify?code=<code>`

请求：

| Key  | 说明       | 示例   |
| ---- | ---------- | ------ |
| code | 短信验证码 | 123456 |

返回结果后请验证返回 JSON 中 code 的值是否为 0，如为 0 则验证码正确，**并记录下返回的userId**。

**第四步** 设定密码和邮箱

`POST /register2?r=<r>`

**请注意！请将密码在本地MD5加密后再放到 userPassword 中！不接受明文密码！**

| Key          | 说明                                   | 示例                             |
| ------------ | -------------------------------------- | -------------------------------- |
| userAppRole  | 角色（0为黑客，1为画家）               | 0                                |
| userPassword | 使用 MD5 加密后的密码 *                | e10adc3949ba59abbe56e057f20f883e |
| userId       | 请填写第三步返回的userId               | 1652062402334                    |
| r            | 邀请人的用户名（选填，无邀请人则留空） | csfwff                           |

code 返回 0 则注册成功！

## 通用

### 通过API累计用户的在线时间

`wss://fishpi.cn/user-channel?apiKey=<Key>`

客户端开发者请注意，如果想让用户在使用客户端时也能累计在线时间，请将客户端在后台长期连接到此Websocket。此Websocket不会给你发送任何消息，你也不用给他发送任何消息，只需要挂着这个连接就可以了。

此Websocket在 `首次连接`时开始累计在线时间，直到 `最后一个该用户的user-channel会话断开`才会结算。期间如果你获取在线时间是不会动态变化的，你可以在客户端的前端做一个60秒的循环，每60秒把在线时间+1，这样在用户观感上在线时间就是动态的了。

注意：为确保计算准确，必须**有**断开自动重连机制，但请**不要**发送心跳包，Websocket会每15分钟断开一次。

### 查询成员信息

`GET` `/user/<username>?apiKey=<Key>`

查询某个成员的信息

请求:

| Key      | 说明      | 示例                             |
| -------- | --------- | -------------------------------- |
| username | 用户名    | taozhiyu                         |
| apiKey   | 通用密钥* | YOUR_API_KEY |

> <sup>*</sup> 选填，填写后 `canFollow` 返回值可以显示是否已经关注该用户

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/user/taozhiyu?apiKey=YOUR_API_KEY'\
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

响应：

| Key                | 说明                                                                  | 示例                                              |
| ------------------ | --------------------------------------------------------------------- | ------------------------------------------------- |
| oId                | Id                                                                    | 1630512345670                                     |
| userNo             | 用户序号                                                              | 26                                                |
| userName           | 用户名                                                                | imlinhanchao                                      |
| userRole           | 用户组                                                                | OP                                                |
| userNickname       | 昵称                                                                  | 我是跳跳吧                                        |
| userAvatarURL      | 头像地址                                                              | https://...                                       |
| userCity           | 最近所在城市，根据用户 IP 确定                                        | 深圳                                              |
| userOnlineFlag     | 是否在线                                                              | true                                              |
| onlineMinute       | 在线时长，单位分钟                                                    | 85467                                             |
| userPoint          | 积分                                                                  | 183939                                            |
| canFollow          | 是否可以关注                                                          | no/yes/hide                                       |
| userAppRole        | 角色                                                                  | 0 = 黑客，1 = 画家                                |
| userIntro          | 签名                                                                  | 人生而自由，卻無往不在枷鎖之中。                  |
| userURL            | URL                                                                   | https://...                                       |
| cardBg             | 卡片背景                                                              | https://...                                       |
| followingUserCount | 关注用户                                                              | 5                                                 |
| sysMetal           | 徽章列表, JSON**字符串**                                              | `{ ... }`                                         |
| - list             | 徽章列表数据                                                          | `[ ... ]`                                         |
| -- attr            | 徽章数据，包含徽章图地址`url`, 背景色 `backcolor`, 前景色 `fontcolor` | url=https://...&backcolor=b91c22&fontcolor=ffffff |
| -- name            | 徽章名称                                                              | Operator                                          |
| -- description     | 徽章描述                                                              | 摸鱼派官方开发组成员                              |
| -- data            | 徽章数据                                                              | 无                                                |

### 用户名联想

`POST /users/names`

通过现有的字符串推断完整的用户名（列表）

请求:

| Key  | 说明           | 示例 |
| ---- | -------------- | ---- |
| name | 不完整的用户名 | ad   |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/users/names' \
--header 'Content-Type: application/json' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
--data-raw '{
 "name":"ad"
}'
```

响应：

| Key             | 说明                          | 示例        |
| --------------- | ----------------------------- | ----------- |
| code            | 为0则密钥有效，为-1则密钥无效 | 0           |
| msg             | 错误信息                      |             |
| data            | 用户列表                      | `[ ... ]`   |
| - userAvatarURL | 头像                          | https://... |
| - userName      | 用户名                        | adlered     |

### 用户常用表情

`GET /users/emotions?apiKey=<Key>`

获取用户常用表情

请求:

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/users/emotions?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

响应：

| Key              | 说明                              | 示例              |
| ---------------- | --------------------------------- | ----------------- |
| code             | 为0则密钥有效，为-1则密钥无效     | 0                 |
| msg              | 错误信息                          |                   |
| data             | 表情列表                          | `[ ... ]`         |
| -`<<`emoji name> | Key 为 emoji 代码，值为对应 emoji | "smile":":smile:" |

### 获取活跃度

`GET /user/liveness?apiKey=<Key>`

获取用户活跃度

> **警告:warning:️️️️️️️️️️️️️️️️️️**:
> 本接口负载较大，请至少将请求间隔延长至**10 分钟**，如间隔小于 10 分钟，接口将会返回 500！

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/user/liveness?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

响应：

| Key      | 说明   | 示例  |
| -------- | ------ | ----- |
| liveness | 活跃度 | 80.20 |

### 获取签到状态

`GET /user/checkedIn?apiKey=<Key>`

获取用户是否签到

请求:

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例:

```bash
curl --location --request GET 'https://fishpi.cn/user/checkedIn?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

响应：

| Key       | 说明     | 示例 |
| --------- | -------- | ---- |
| checkedIn | 是否签到 | true |

### 领取昨日活跃奖励

`GET /activity/yesterday-liveness-reward-api?apiKey=<Key>`

领取昨日活跃奖励

请求示例:

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例:

```bash
curl --location --request GET 'https://fishpi.cn/activity/yesterday-liveness-reward-api?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

响应：

| Key | 说明                        | 示例 |
| --- | --------------------------- | ---- |
| sum | 领取到的积分，-1 表示已领取 | 300  |

### 查询昨日奖励领取状态

`GET /api/activity/is-collected-liveness`

查询昨日活跃奖励是否已被领取

请求示例:

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例:

```bash
curl --location --request GET 'https://fishpi.cn/api/activity/is-collected-liveness?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

响应：

| Key                                | 说明            | 示例 |
| ---------------------------------- | --------------- | ---- |
| isCollectedYesterdayLivenessReward | true 表示已领取 | true |

### 举报

`POST /report`

举报内容接口
**请注意！** 该接口为复用接口，请确保 `Content-Type` 为 `application/x-www-form-urlencoded` 而非JSON，否则将无法请求成功

请求：

| Key            | 说明         | 示例                                                                                              |
| -------------- | ------------ | ------------------------------------------------------------------------------------------------- |
| apiKey         | 通用密钥     | YOUR_API_KEY                                                                  |
| reportDataId   | 举报 Id      | 1651126540998                                                                                     |
| reportDataType | 举报数据类型 | 0:文章,1:评论,2:用户,3:聊天消息                                                                   |
| reportType     | 举报类型     | 0:垃圾广告,1: H,2:违规,3:侵权,4:人身攻击,5:冒充他人账号,6:垃圾广告账号,7:违规泄露个人信息,49:其他 |
| reportMemo     | 举报理由     | 该用户涉嫌发送违规内容                                                                            |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/report' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
--header 'Content-Type: application/json' \
--data-raw '{
 "apiKey":"YOUR_API_KEY",
 "reportDataId":"1651126540998",
 "reportDataType":3,
 "reportType":49,
 "reportMemo":""，
}'
```

响应：

| Key  | 说明                              | 示例 |
| ---- | --------------------------------- | ---- |
| code | 为 0 则密钥有效，为 -1 则密钥无效 | 0    |
| msg  | 错误消息                          |      |

### 查询最近注册的20个用户

`GET /api/user/recentReg`

### 转账

`POST /point/transfer`

请求：

| Key      | 说明     | 示例                             |
| -------- | -------- | -------------------------------- |
| apiKey   | 通用密钥 | YOUR_API_KEY |
| userName | 收款人   | adlered                          |
| amount   | 转账金额 | 100                              |
| memo     | 转账备注 | hello                            |

### 关注用户

`POST /follow/user`

请求：

| Key         | 说明          | 示例                             |
| ----------- | ------------- | -------------------------------- |
| apiKey      | 通用密钥      | YOUR_API_KEY |
| followingId | 关注对象的oId | 1659430635383                    |

### 取关用户

`POST /unfollow/user`

请求：

| Key         | 说明          | 示例                             |
| ----------- | ------------- | -------------------------------- |
| apiKey      | 通用密钥      | YOUR_API_KEY |
| followingId | 关注对象的oId | 1659430635383                    |

### 修改用户信息

`POST /api/settings/profiles`

请求：

| Key          | 说明                             | 示例                             |
| ------------ | -------------------------------- | -------------------------------- |
| apiKey       | 通用密钥                         | YOUR_API_KEY |
| userNickname | 用户昵称                         | 阿达                             |
| userTags     | 用户标签，多个标签用英文逗号分隔 | 标签1,标签2                      |
| userURL      | 用户URL                          | https://xxx.com                  |
| userIntro    | 个性签名                         | 这个人很懒，没有个性签名         |
| mbti         | 用户的MBTI                       | ENFJ-A                           |

### 修改用户头像

`POST /api/settings/avatar`

请求：

| Key           | 说明        | 示例                                   |
| ------------- | ----------- | -------------------------------------- |
| apiKey        | 通用密钥    | YOUR_API_KEY       |
| userAvatarURL | 用户头像URL | https://file.fishpi.cn/2025/12/xxx.png |

### 查询用户积分余额

`GET /user/{用户名}/point`

### 查询用户勋章

`GET /user/{用户名}/medal`

## OAuth 用户资源

### OAuth 授权范围

`GET /openid/login`

第三方登录时可通过 `fishpi.scope` 请求用户资源权限。用户授权后，`POST /openid/verify` 成功响应会追加 `access_token` 和 `refresh_token`。

请求：

| Key          | 说明                         | 示例                         |
| ------------ | ---------------------------- | ---------------------------- |
| fishpi.scope | 授权范围，空格或逗号分隔     | profile.read profile.detail.read membership.read |

> 可用范围：`profile.read` 基础信息，`profile.detail.read` 详细资料，`points.read` 积分信息，`articles.read` 发帖信息，`membership.read` VIP 信息。

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/openid/login?openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0&openid.mode=checkid_setup&openid.return_to=https%3A%2F%2Fexample.com%2Fcallback&openid.realm=https%3A%2F%2Fexample.com&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&fishpi.scope=profile.read%20profile.detail.read%20membership.read'
```

响应：

| Key          | 说明                         | 示例                         |
| ------------ | ---------------------------- | ---------------------------- |
| scope        | 用户最终授权范围             | profile.read profile.detail.read membership.read |
| token_type   | 令牌类型                     | Bearer                       |
| access_token | OAuth 用户资源访问令牌       | xxx                          |
| expires_in   | 有效期，单位秒               | 604800                       |
| refresh_token | OAuth 续签令牌              | xxx                          |
| refresh_expires_in | 续签令牌空闲有效期，单位秒 | 31104000                     |

> `profile.read` 为基础权限；站点请求的权限为必选，用户可额外勾选更多权限。

### 续签 OAuth 访问令牌

`POST /openid/token`

使用 `refresh_token` 续签 OAuth 访问令牌。不接受 `apiKey`。

请求：
| Key           | 说明                         | 示例          |
| ------------- | ---------------------------- | ------------- |
| grant_type    | 固定为 `refresh_token`        | refresh_token |
| refresh_token | OAuth 续签令牌                | xxx           |

请求示例：
```bash
curl --location --request POST 'https://fishpi.cn/openid/token' \
--header 'Content-Type: application/json' \
--data-raw '{
  "grant_type": "refresh_token",
  "refresh_token": "xxx"
}'
```

响应：
| Key                    | 说明                         | 示例                     |
| ---------------------- | ---------------------------- | ------------------------ |
| code                   | 0 为成功，-1 为失败           | 0                        |
| msg                    | 错误消息                     |                          |
| data                   | 响应数据                     |                          |
| - token_type           | 令牌类型                     | Bearer                   |
| - access_token         | OAuth 用户资源访问令牌       | xxx                      |
| - expires_in           | 访问令牌有效期，单位秒       | 604800                   |
| - refresh_token        | 新 OAuth 续签令牌            | xxx                      |
| - refresh_expires_in   | 续签令牌空闲有效期，单位秒   | 31104000                 |
| - scope                | 用户最终授权范围             | profile.read profile.detail.read membership.read |

> 每次续签都会返回新的 `refresh_token`，旧 `refresh_token` 会立即失效；续签令牌最长不超过首次授权后 720 天；请不要把 `refresh_token` 暴露到前端页面或日志中。

### 查询 OAuth 用户信息

`GET /openid/user/profile`

查询当前 OAuth 授权用户的基础信息。需要 `profile.read` 权限。

请求：

| Key           | 说明             | 示例         |
| ------------- | ---------------- | ------------ |
| Authorization | Bearer 访问令牌  | Bearer xxx   |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/openid/user/profile' \
--header 'Authorization: Bearer xxx'
```

响应：

| Key              | 说明                 | 示例                          |
| ---------------- | -------------------- | ----------------------------- |
| code             | 0 为成功，-1 为失败  | 0                             |
| msg              | 错误消息             |                               |
| data             | 响应数据             |                               |
| - userId         | 用户 oId             | 1659430635383                 |
| - userName       | 用户名               | admin                         |
| - userNickname   | 昵称                 | 管理员                        |
| - userAvatarURL  | 头像                 | https://file.fishpi.cn/a.png  |

### 查询 OAuth 用户详细信息

`GET /openid/user/detail`

查询当前 OAuth 授权用户的详细资料。需要 `profile.detail.read` 权限。

请求：

| Key           | 说明             | 示例         |
| ------------- | ---------------- | ------------ |
| Authorization | Bearer 访问令牌  | Bearer xxx   |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/openid/user/detail' \
--header 'Authorization: Bearer xxx'
```

响应：

| Key                    | 说明                 | 示例                         |
| ---------------------- | -------------------- | ---------------------------- |
| code                   | 0 为成功，-1 为失败  | 0                            |
| msg                    | 错误消息             |                              |
| data                   | 响应数据             |                              |
| - userName             | 用户名               | admin                        |
| - userOnlineFlag       | 是否在线             | true                         |
| - onlineMinute         | 在线分钟数           | 1000                         |
| - userURL              | 个人链接             | https://example.com          |
| - userNickname         | 昵称                 | 管理员                       |
| - userCity             | 公开城市             | 北京                         |
| - userAvatarURL        | 头像                 | https://file.fishpi.cn/a.png |
| - userPoint            | 当前积分             | 183939                       |
| - userIntro            | 简介                 | 摸鱼中                       |
| - oId                  | 用户 oId             | 1659430635383                |
| - userNo               | 用户编号             | 1                            |
| - userAppRole          | 站内身份             | 0                            |
| - sysMetal             | 展示勋章             | {"list":[]}                  |
| - followerCount        | 粉丝数               | 10                           |
| - followingUserCount   | 关注数               | 5                            |
| - userRole             | 角色名               | 普通用户                     |
| - cardBg               | 卡片背景             |                              |

> 本接口不接受 `apiKey`，只返回当前 Bearer token 所属用户；不会返回邮箱、手机号、QQ、登录 IP、密码、2FA 密钥、token/key 或完整用户对象。`userCity` 仅在用户公开地理位置时返回。

### 查询 OAuth 用户 VIP 信息

`GET /openid/user/membership`

查询当前 OAuth 授权用户的 VIP 状态。需要 `membership.read` 权限。

请求：

| Key           | 说明             | 示例         |
| ------------- | ---------------- | ------------ |
| Authorization | Bearer 访问令牌  | Bearer xxx   |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/openid/user/membership' \
--header 'Authorization: Bearer xxx'
```

响应：

| Key          | 说明                 | 示例          |
| ------------ | -------------------- | ------------- |
| code         | 0 为成功，-1 为失败  | 0             |
| msg          | 错误消息             |               |
| data         | 响应数据             |               |
| - active     | VIP 是否有效         | true          |
| - state      | 会员状态             | 1             |
| - lvCode     | VIP 等级编码         | VIP1          |
| - expiresAt  | 到期时间，毫秒；0 为永久 | 1760000000000 |
| - configJson | 用户 VIP 配置        | {}            |

> 本接口不接受 `apiKey`，只返回当前 Bearer token 所属用户；非 VIP 或已过期时返回 `active=false`、`state=0`、`lvCode=""`、`expiresAt=0`、`configJson=""`。不会返回价格、支付、退款或后台维护记录。

### 查询 OAuth 用户积分记录

`GET /openid/user/points`

分页查询当前 OAuth 授权用户的积分余额和积分记录。需要 `points.read` 权限。

请求：

| Key           | 说明                         | 示例       |
| ------------- | ---------------------------- | ---------- |
| Authorization | Bearer 访问令牌              | Bearer xxx |
| p             | 页码，默认 1                 | 1          |
| size          | 每页数量，默认 20，最大 200  | 20         |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/openid/user/points?p=1&size=20' \
--header 'Authorization: Bearer xxx'
```

响应：

| Key                              | 说明                 | 示例          |
| -------------------------------- | -------------------- | ------------- |
| code                             | 0 为成功，-1 为失败  | 0             |
| msg                              | 错误消息             |               |
| data                             | 响应数据             |               |
| - userId                         | 用户 oId             | 1659430635383 |
| - userPoint                      | 当前积分             | 183939        |
| - records                        | 积分记录列表         |               |
| -- oId                           | 记录 oId             | 1760000000000 |
| -- fromId                        | 支出用户 oId         | 1659430635383 |
| -- toId                          | 收入用户 oId         | sys           |
| -- sum                           | 积分数量             | 20            |
| -- type                          | 记录类型             | 8             |
| -- time                          | 记录时间             | 1760000000000 |
| -- dataId                        | 关联数据             | 1760000000000 |
| -- memo                          | 备注                 | hello         |
| -- sourceAppName                 | 来源应用名称         | 小冰游戏      |
| -- sourceScene                   | 来源场景             | point_issue   |
| -- operation                     | 收支方向             | +             |
| -- balance                       | 操作后余额           | 183939        |
| -- displayType                   | 类型名称             | 活动收益      |
| -- description                   | 记录描述             | 签到奖励      |
| - pagination                     | 分页信息             |               |
| -- paginationCurrentPageNum      | 当前页               | 1             |
| -- paginationPageSize            | 每页数量             | 20            |
| -- paginationRecordCount         | 总记录数             | 100           |
| -- paginationPageCount           | 总页数               | 5             |
| -- paginationPageNums            | 页码列表             | [1,2,3,4,5]   |

### 查询 OAuth 用户发帖记录

`GET /openid/user/articles`

分页查询当前 OAuth 授权用户的公开发帖记录。需要 `articles.read` 权限。

请求：

| Key           | 说明                         | 示例       |
| ------------- | ---------------------------- | ---------- |
| Authorization | Bearer 访问令牌              | Bearer xxx |
| p             | 页码，默认 1                 | 1          |
| size          | 每页数量，默认 20，最大 100  | 20         |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/openid/user/articles?p=1&size=20' \
--header 'Authorization: Bearer xxx'
```

响应：

| Key                              | 说明                 | 示例          |
| -------------------------------- | -------------------- | ------------- |
| code                             | 0 为成功，-1 为失败  | 0             |
| msg                              | 错误消息             |               |
| data                             | 响应数据             |               |
| - userId                         | 用户 oId             | 1659430635383 |
| - articles                       | 发帖记录列表         |               |
| -- oId                           | 帖子 oId             | 1760000000000 |
| -- articleTitle                  | 标题                 | 摸鱼          |
| -- articlePermalink              | 链接                 | /article/1760000000000 |
| -- articleTags                   | 标签                 | 摸鱼,生活     |
| -- articleCreateTime             | 创建时间             | 1760000000000 |
| -- articleUpdateTime             | 更新时间             | 1760000000000 |
| -- articleCommentCount           | 回帖数               | 3             |
| -- articleViewCount              | 浏览数               | 100           |
| -- articleType                   | 帖子类型             | 0             |
| -- articlePerfect                | 是否优选             | 0             |
| - pagination                     | 分页信息             |               |
| -- paginationCurrentPageNum      | 当前页               | 1             |
| -- paginationPageSize            | 每页数量             | 20            |
| -- paginationRecordCount         | 总记录数             | 100           |
| -- paginationPageCount           | 总页数               | 5             |
| -- paginationPageNums            | 页码列表             | [1,2,3,4,5]   |

> 发帖记录只返回公开、非匿名、未删除发帖，不包含正文、草稿、IP、UA。

## 通知

### 通知计数

`GET /notifications/unread/count`

获取用户未阅读的通知计数

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例:

```bash
curl --location --request GET 'https://fishpi.cn/notifications/unread/count?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

响应：

| Key                              | 说明                              | 示例 |
| -------------------------------- | --------------------------------- | ---- |
| code                             | 为 0 则密钥有效，为 -1 则密钥无效 | 0    |
| userNotifyStatus                 | 是否启用 Web 通知                 | 0    |
| unreadNotificationCnt            | 未读通知数                        | 0    |
| unreadReplyNotificationCnt       | 未读回复通知数                    | 0    |
| unreadPointNotificationCnt       | 未读积分通知数                    | 0    |
| unreadAtNotificationCnt          | 未读 @ 通知数                     | 0    |
| unreadBroadcastNotificationCnt   | 未读同城通知数                    | 0    |
| unreadSysAnnounceNotificationCnt | 未读系统通知数                    | 0    |
| unreadNewFollowerNotificationCnt | 未读关注者通知数                  | 0    |
| unreadFollowingNotificationCnt   | 未读关注通知数                    | 0    |
| unreadCommentedNotificationCnt   | 未读评论通知数                    | 0    |

### 通知详情

`GET /api/getNotifications?apiKey=<Key>&type=<type>[&page=<page>]`

获取详细通知列表

请求：

| Key    | 说明              | 示例                             |
| ------ | ----------------- | -------------------------------- |
| apiKey | 通用密钥          | YOUR_API_KEY |
| type   | 要获取的通知类型  | point                            |
| p      | 页数可选，默认为1 | 1                                |

通知类型：

| Type         | 说明       |
| ------------ | ---------- |
| point        | 积分       |
| commented    | 收到的回帖 |
| reply        | 收到的回复 |
| at           | 提及我的   |
| following    | 我关注的   |
| broadcast    | 同城       |
| sys-announce | 系统       |

请求示例:

```bash
curl --location --request GET 'https://fishpi.cn/api/getNotifications?apiKey=YOUR_API_KEY&type=point' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

积分(point)通知响应：

| Key           | 说明                          | 示例                                                              |
| ------------- | ----------------------------- | ----------------------------------------------------------------- |
| code          | 为0则密钥有效，为-1则密钥无效 | 0                                                                 |
| msg           | 错误信息                      |                                                                   |
| data          | 通知数据列表                  | `[ ... ]`                                                         |
| - hasRead     | 是否已读                      | true                                                              |
| - createTime  | 创建时间                      | Tue Dec 21 11:33:31 CST 2021                                      |
| - description | 通知描述，格式为 HTML         | `PickerFinsh 已通过你的邀请链接注册，感谢你对社区的贡献 :hearts:` |

收到的回帖/回复(commented/reply)通知响应：

| Key                         | 说明                          | 示例                                         |
| --------------------------- | ----------------------------- | -------------------------------------------- |
| code                        | 为0则密钥有效，为-1则密钥无效 | 0                                            |
| msg                         | 错误信息                      |                                              |
| data                        | 通知数据列表                  | `[ ... ]`                                    |
| - hasRead                   | 是否已读                      | true                                         |
| - commentAuthorName         | 回帖作者                      | Tocker                                       |
| - commentAuthorThumbnailURL | 回帖作者头像缩略图            | https://...                                  |
| - commentCreateTime         | 回帖时间                      | Sun Dec 19 09:57:03 CST 2021                 |
| - commentSharpURL           | 回帖地址                      | /article/1637143985245?p=1&m=1#1639879022994 |
| - commentContent            | 回帖内容，内容为 HTML         | `牛蛙牛蛙`                                   |
| - commentArticleType        | 回帖的文章类型                | 0                                            |
| - commentArticleTitle       | 回帖文章标题                  | 摸鱼派聊天室应用                             |
| - commentArticlePerfect     | 是否精选的文章                | 1                                            |

收到的 @ (at) 通知响应：

| Key             | 说明                          | 示例                                                  |
| --------------- | ----------------------------- | ----------------------------------------------------- |
| code            | 为0则密钥有效，为-1则密钥无效 | 0                                                     |
| msg             | 错误信息                      |                                                       |
| data            | 通知数据列表                  | `[ ... ]`                                             |
| - hasRead       | 是否已读                      | true,                                                 |
| - userName      | 发起 @ 用户                   | imlinhanchao                                          |
| - userAvatarURL | 发起 @ 用户头像               | https://file.fishpi.cn/2021/09/1502581-141c6da3.png", |
| - deleted       | 是否已删除                    | false,                                                |
| - createTime"   | 创建时间                      | Mon Dec 20 10:00:56 CST 2021",                        |
| - content       | @ 的内容                      | @imlinhanchao 大佬一直在线 还可以看直播"              |

我关注的(following) 通知响应：

| Key                   | 说明                          | 示例                                    |
| --------------------- | ----------------------------- | --------------------------------------- |
| code                  | 为0则密钥有效，为-1则密钥无效 | 0                                       |
| msg                   | 错误信息                      |                                         |
| data                  | 通知数据列表                  | `[ ... ]`                               |
| - hasRead             | 是否已读                      | true                                    |
| - articleTitle        | 文章标题                      | 【社区维护日志】IP 黑名单列表           |
| - isComment           | 是否评论                      | false                                   |
| - articleTags         | 文章标签                      | 摸鱼派,熊孩子                           |
| - url                 | 文章地址                      | https://fishpi.cn/article/1639719358591 |
| - articleType         | 文章类型                      | 0                                       |
| - createTime          | 创建时间                      | Fri Dec 17 13:35:58 CST 2021            |
| - authorName          | 文章作者                      | adlered                                 |
| - articlePerfect      | 是否精选文章                  | 0                                       |
| - thumbnailURL        | 作者头像缩略图                | https://...                             |
| - articleCommentCount | 文章评论数                    | 17                                      |

| 系统(sys-announce)通知响应： |                               |                                                                                                                    |

| Key           | 说明                          | 示例                                                     |
| ------------- | ----------------------------- | -------------------------------------------------------- |
| code          | 为0则密钥有效，为-1则密钥无效 | 0                                                        |
| msg           | 错误信息                      |                                                          |
| data          | 通知数据列表                  | `[ ... ]`                                                |
| - hasRead     | 是否已读                      | true                                                     |
| - createTime  | 创建日期                      | Mon Dec 13 11:43:38 CST 2021"                            |
| - description | 通知描述                      | `【12.13 国家公祭日】南京大屠杀 - 勿忘历史，吾辈自强！`" |

### 批量已读类型的通知

`GET /notifications/make-read/<type>?apiKey=<Key>`

将制定类型的通知标记为已读

请求：

| Key    | 说明             | 示例                             |
| ------ | ---------------- | -------------------------------- |
| apiKey | 通用密钥         | YOUR_API_KEY |
| type   | 要已读的通知类型 | point                            |

| 通知类型：   |            |

| Type         | 说明       |
| ------------ | ---------- |
| point        | 积分       |
| commented    | 收到的回帖 |
| reply        | 收到的回复 |
| at           | 提及我的   |
| following    | 我关注的   |
| broadcast    | 同城       |
| sys-announce | 系统       |

请求示例:

```bash
curl --location --request GET 'https://fishpi.cn/notifications/make-read/point?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

### 已读所有消息

`GET /notifications/all-read?apiKey=<Key>`

将全部通知标记为已读

请求：

| Key    | 说明             | 示例                             |
| ------ | ---------------- | -------------------------------- |
| apiKey | 通用密钥         | YOUR_API_KEY |
| type   | 要已读的通知类型 | point                            |

请求示例:

```bash
curl --location --request GET 'https://fishpi.cn/notifications/all-read?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

## 聊天室

### 获取发送弹幕的价格

`GET /chat-room/barrager/get`

### 连接聊天室

### 请注意：连接聊天室的内容已被废弃，请跳转至：https://fishpi.cn/article/1733591297543

`WSS /chat-room-channel?apiKey=<Key>`

聊天室接收消息的WebSocket

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

事件处理：

| Event   | 说明                                       |
| ------- | ------------------------------------------ |
| open    | 连接打开，定时每 3 分钟发送`-hb-`          |
| close   | 连接关闭                                   |
| error   | 连接出错，需重新连接                       |
| message | 收到消息，内容为 JSON 字符串，需自行序列号 |

消息结构：

| Key                       | 所属类型        | 说明                                                                                                   | 示例                                              |
| ------------------------- | --------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------- |
| type                      | all             | 消息类型online(在线) / discussChanged(话题变更) / revoke(撤回) / msg(聊天) / redPacketStatus(红包领取) | msg                                               |
| 在线消息                  |                 |                                                                                                        |                                                   |
| onlineChatCnt             | online          | 在线人数                                                                                               | 35                                                |
| users                     | online          | 在线用户列表                                                                                           | `[ ... ]`                                         |
| - homePage                | online          | 用户首页                                                                                               | https://fishpi.cn/member/...                      |
| - userAvatarURL           | online          | 用户头像                                                                                               | https://...                                       |
| - userName                | online          | 用户名                                                                                                 | adlered                                           |
| 话题变更                  |                 |                                                                                                        |                                                   |
| - newDiscuss              | discussChanged  | 新的话题内容                                                                                           | 今天上班吗                                        |
| 聊天消息                  |                 |                                                                                                        |                                                   |
| oId                       | msg             | 消息 Id                                                                                                | 1640074796395                                     |
| time                      | msg             | 发布时间                                                                                               | 2021-12-21 16:19:56                               |
| client                    | msg             | 客户端来源                                                                                             | Web/Latest                                        |
| userName                  | msg             | 用户名                                                                                                 | adlered                                           |
| userNickname              | msg             | 昵称                                                                                                   | 陈辉                                              |
| userAvatarURL             | msg             | 用户头像                                                                                               | https://...                                       |
| sysMetal                  | msg             | 徽章列表, JSON**字符串**                                                                               | `{ ... }`                                         |
| - list                    | msg - sysMetal  | 徽章列表数据                                                                                           | `[ ... ]`                                         |
| -- attr                   | msg - sysMetal  | 徽章数据，包含徽章图地址`url`, 背景色 `backcolor`, 前景色 `fontcolor`                                  | url=https://...&backcolor=b91c22&fontcolor=ffffff |
| -- name                   | msg - sysMetal  | msg                                                                                                    | 徽章名称                                          |
| -- description - sysMetal | 徽章描述        | 摸鱼派官方开发组成员                                                                                   |                                                   |
| -- data                   | msg - sysMetal  | 徽章数据                                                                                               | 无                                                |
| content                   | msg             | 消息内容，HTML 格式，如果是红包，则是 JSON                                                             | `+1` 或 `{...}`                                   |
| - msg                     | msg - redPacket | 红包祝福语                                                                                             | 摸鱼者，事竟成！                                  |
| - recivers                | msg - redPacket | 红包接收者用户名，专属红包有效                                                                         | [ ... ]                                           |
| - msgType                 | msg - redPacket | 固定 redPacket                                                                                         | redPacket                                         |
| - money                   | msg - redPacket | 红包积分                                                                                               | 32                                                |
| - count                   | msg - redPacket | 红包个数                                                                                               | 2                                                 |
| - type                    | msg - redPacket | 红包类型                                                                                               | random                                            |
| - got                     | msg - redPacket | 已领取个数                                                                                             | 1                                                 |
| - who                     | msg - redPacket | 已领取者信息                                                                                           | `[ ... ]`                                         |
| -- userName               | msg - redPacket | 用户名                                                                                                 | dannio                                            |
| -- avatar                 | msg - redPacket | 用户头像                                                                                               | https://...                                       |
| -- userMoney - redPacket  | msg             | 领取到的积分                                                                                           | 23                                                |
| -- time                   | msg - redPacket | 领取时间                                                                                               | 2021-12-21 16:26:43                               |
| md                        | msg             | 消息内容，Markdown 格式，红包消息无此栏位                                                              | `![image.png](https://...)`                       |
| 撤回消息                  |                 |                                                                                                        |                                                   |
| oId                       | revoke          | 撤回消息的 Id                                                                                          | 1640076642484                                     |
| 红包领取消息              |                 |                                                                                                        |                                                   |
| oId                       | redPacketStatus | 消息 Id                                                                                                | 1640075201124                                     |
| count                     | redPacketStatus | 红包个数                                                                                               | 2                                                 |
| got                       | redPacketStatus | 已领取个数                                                                                             | 1                                                 |
| whoGive                   | redPacketStatus | 发送者用户名                                                                                           | adlered                                           |
| whoGot                    | redPacketStatus | 领取者用户名                                                                                           | hewei                                             |

### 聊天历史消息

`GET /chat-room/more?apiKey=<Key>&page=<page>`

获取更多聊天信息

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |
| page   | 頁碼     | 1                                |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/chat-room/more?page=1&apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

响应：

| Key             | 说明                                                                  | 示例                                              |
| --------------- | --------------------------------------------------------------------- | ------------------------------------------------- |
| code            | 为 0 则密钥有效，为 -1 则密钥无效                                     | 0                                                 |
| msg             | 错误信息                                                              |                                                   |
| data            | 消息列表                                                              | `[ ... ]`                                         |
| - oId           | 消息 Id                                                               | 1640074796395                                     |
| - time          | 发布时间                                                              | 2021-12-21 16:19:56                               |
| - userName      | 用户名                                                                | adlered                                           |
| - userNickname  | 昵称                                                                  | 陈辉                                              |
| - userAvatarURL | 用户头像                                                              | https://...                                       |
| - client        | 客户端来源                                                            | Web/Latest                                        |
| - sysMetal      | 徽章列表, JSON**字符串**                                              | `{ ... }`                                         |
| -- list         | 徽章列表数据                                                          | `[ ... ]`                                         |
| --- attr        | 徽章数据，包含徽章图地址`url`, 背景色 `backcolor`, 前景色 `fontcolor` | url=https://...&backcolor=b91c22&fontcolor=ffffff |
| --- name        | 徽章名称                                                              | Operator                                          |
| --- description | 徽章描述                                                              | 摸鱼派官方开发组成员                              |
| --- data        | 徽章数据                                                              | 无                                                |
| - content       | 消息内容，HTML 格式，如果是红包，则是 JSON                            | `+1` 或 `{...}`                                   |
| -- msg          | 红包祝福语                                                            | 摸鱼者，事竟成！                                  |
| -- recivers     | 红包接收者用户名，专属红包有效                                        | [ ... ]                                           |
| -- msgType      | 固定 redPacket                                                        | redPacket                                         |
| -- money        | 红包积分                                                              | 32                                                |
| -- count        | 红包个数                                                              | 2                                                 |
| -- type         | 红包类型                                                              | random                                            |
| -- got          | 已领取个数                                                            | 1                                                 |
| -- who          | 已领取者信息                                                          | `[ ... ]`                                         |
| --- userName    | 用户名                                                                | dannio                                            |
| --- avatar      | 用户头像                                                              | https://...                                       |
| --- userMoney   | 领取到的积分                                                          | 23                                                |
| --- time        | 领取时间                                                              | 2021-12-21 16:26:43                               |

`GET /chat-room/getMessage`

通过聊天消息的oId获取前后消息，避免重复的问题，也可以用于快捷跳转至某条消息

**注意：使用本接口必须已经登录或填写正确的apiKey，否则将返回401**

请求：

| Key    | 说明                                                                                          | 示例                             |
| ------ | --------------------------------------------------------------------------------------------- | -------------------------------- |
| apiKey | 通用密钥                                                                                      | YOUR_API_KEY |
| oId    | 消息oId                                                                                       | 1650609438569                    |
| size   | 显示消息个数（不包括当mode为0时实际个数乘以2）                                                | 25                               |
| mode   | mode = 0 显示本条及之前、之后的消息mode = 1 显示本条及之前的消息mode = 2 显示本条及之后的消息 | 0                                |

响应：

| Key             | 说明                                                                  | 示例                                              |
| --------------- | --------------------------------------------------------------------- | ------------------------------------------------- |
| code            | 为 0 则密钥有效，为 -1 则密钥无效                                     | 0                                                 |
| msg             | 错误信息                                                              |                                                   |
| data            | 消息列表                                                              | `[ ... ]`                                         |
| - oId           | 消息 Id                                                               | 1640074796395                                     |
| - time          | 发布时间                                                              | 2021-12-21 16:19:56                               |
| - userName      | 用户名                                                                | adlered                                           |
| - userNickname  | 昵称                                                                  | 陈辉                                              |
| - userAvatarURL | 用户头像                                                              | https://...                                       |
| - sysMetal      | 徽章列表, JSON**字符串**                                              | `{ ... }`                                         |
| -- list         | 徽章列表数据                                                          | `[ ... ]`                                         |
| --- attr        | 徽章数据，包含徽章图地址`url`, 背景色 `backcolor`, 前景色 `fontcolor` | url=https://...&backcolor=b91c22&fontcolor=ffffff |
| --- name        | 徽章名称                                                              | Operator                                          |
| --- description | 徽章描述                                                              | 摸鱼派官方开发组成员                              |
| --- data        | 徽章数据                                                              | 无                                                |
| - content       | 消息内容，HTML 格式，如果是红包，则是 JSON                            | `+1` 或 `{...}`                                   |
| -- msg          | 红包祝福语                                                            | 摸鱼者，事竟成！                                  |
| -- recivers     | 红包接收者用户名，专属红包有效                                        | [ ... ]                                           |
| -- msgType      | 固定 redPacket                                                        | redPacket                                         |
| -- money        | 红包积分                                                              | 32                                                |
| -- count        | 红包个数                                                              | 2                                                 |
| -- type         | 红包类型                                                              | random                                            |
| -- got          | 已领取个数                                                            | 1                                                 |
| -- who          | 已领取者信息                                                          | `[ ... ]`                                         |
| --- userName    | 用户名                                                                | dannio                                            |
| --- avatar      | 用户头像                                                              | https://...                                       |
| --- userMoney   | 领取到的积分                                                          | 23                                                |
| --- time        | 领取时间                                                              | 2021-12-21 16:26:43                               |

### 发送消息

`POST /chat-room/send`

发送聊天室消息

请求：

> <sup>*</sup> 如果是发送红包，则需要发送 `[redpacket]{...}[/redpacket]`的格式

| Key        | 说明                                                                                                                                                                                                                                                                                            | 示例                                     |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| apiKey     | 通用密钥                                                                                                                                                                                                                                                                                        | YOUR_API_KEY         |
| client     | 格式：`标识/版本`标识必须为：`Web` `PC` `Mobile` `Windows` `macOS` `iOS` `Android` `Extension` `IDEA` `Chrome` `Edge` `VSCode` `Python` `Golang` `Linux` `IceNet` `ElvesOnline` `Harmony` `Other`版本允许使用字符：中文、大小写英文、数字、英文句号、英文横线、空格，最大32字符，超过则自动截断 | Windows/V1.0.0                           |
| content    | 消息正文（支持Markdown格式）。                                                                                                                                                                                                                                                                  | `...` 或 `[redpacket]{...}[/redpacket]`* |
| - msg      | 红包祝福语                                                                                                                                                                                                                                                                                      | 摸鱼者，事竟成！                         |
| - money    | 红包包含积分，如果是平分红包，则是单个红包积分                                                                                                                                                                                                                                                  | 32                                       |
| - count    | 红包个数                                                                                                                                                                                                                                                                                        | 2                                        |
| - recivers | 接收者用户名列表，专属红包有效                                                                                                                                                                                                                                                                  | `[ ... ]`                                |
| - type     | 红包类型，random(拼手气红包), average(平分红包)，specify(专属红包)，heartbeat(心跳红包)，rockPaperScissors(猜拳红包)                                                                                                                                                                            | random                                   |
| - gesture  | 猜拳红包必须参数，表示发送者出招，0 = 石头，1 = 剪刀，2 = 布                                                                                                                                                                                                                                    | 0                                        |

响应：

| Key  | 说明                              | 示例 |
| ---- | --------------------------------- | ---- |
| code | 为 0 则密钥有效，为 -1 则密钥无效 | 0    |

### 撤回消息

`DELETE /chat-room/revoke/<oId>`

撤回聊天室消息（限制用户每24小时一次，管理员无限次）

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |
| oId    | 消息 Id  | 1640078407444                    |

请求示例：

```bash
curl --location --request DELETE 'https://fishpi.cn/chat-room/revoke/1640078407444' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
--header 'Content-Type: application/json' \
--data-raw '{
 "apiKey":"YOUR_API_KEY"
}'
```

响应：

| Key  | 说明                              | 示例       |
| ---- | --------------------------------- | ---------- |
| code | 为 0 则密钥有效，为 -1 则密钥无效 | 0          |
| msg  | 错误消息                          | 撤回成功。 |

### 获取消息 Markdown

`GET /cr/raw/<oId>`

查看聊天室消息的 Markdown 原文，引用时使用。

请求：

| Key | 说明    | 示例          |
| --- | ------- | ------------- |
| oId | 消息 Id | 1641290717190 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/cr/raw/1641290717190' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

响应示例：

```markdown
:trollface:

<!-- Generated by Rhythm (https://github.com/csfwff/rhythm) in 1ms, 2022/01/04 18:07:36 -->
```

### 打开红包

`POST /chat-room/red-packet/open`

拆开聊天室红包

请求：

| Key     | 说明                                 | 示例                             |
| ------- | ------------------------------------ | -------------------------------- |
| apiKey  | 通用密钥                             | YOUR_API_KEY |
| oId     | 消息 Id                              | 1640078407444                    |
| gesture | 打开猜拳红包必须参数，表示领取者出招 | 0                                |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/chat-room/red-packet/open' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
--header 'Content-Type: application/json' \
--data-raw '{
 "apiKey":"YOUR_API_KEY",
 "oId":"1640075201124"
}'
```

响应：

| Key             | 说明                                                     | 示例                |
| --------------- | -------------------------------------------------------- | ------------------- |
| recivers        | 红包接收者用户名列表，专属红包有效                       | `[ ... ]`           |
| who             | 已打开红包的用户                                         | `[ ... ]`           |
| - userName      | 用户名                                                   | dannio              |
| - avatar        | 用户头像                                                 | https://...         |
| - userMoney     | 领取到的积分                                             | 23                  |
| - time          | 领取时间                                                 | 2021-12-21 16:26:43 |
| info            | 红包信息                                                 | `{ ... }`           |
| - msg           |                                                          | 玩的就是心跳！      |
| - userName      | 发送者用户名                                             | hewei               |
| - userAvatarURL | 发送者头像                                               | https://...         |
| - count         | 红包个数                                                 | 2                   |
| - got           | 已领取个数                                               | 2                   |
| - gesture       | 猜拳红包参数，表示发送者出招，0 = 石头，1 = 剪刀，2 = 布 | 0                   |

### 获取表情包（已废弃）

> 该方法已废弃，请参阅表情包V2章节，对接新版表情包API。

`POST /api/cloud/get`

从云获取指定 Key 内容，这里用于获取用户表情包

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |
| gameId | 数据 Id  | emojis                           |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/cloud/get' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
--header 'Content-Type: application/json' \
--data-raw '{
 "apiKey":"YOUR_API_KEY",
 "gameId":"emojis"
}'
```

响应：

| Key  | 说明                                             | 示例               |
| ---- | ------------------------------------------------ | ------------------ |
| code | 为 0 则密钥有效，为 -1 则密钥无效                | 0                  |
| data | 数据字符串，表情包内容为表情地址列表 JSON 字符串 | `["url1", "url2"]` |

### 同步表情包（已废弃）

> 该方法已废弃，请参阅表情包V2章节，对接新版表情包API。

`POST /api/cloud/sync`

同步指定 Key 数据到云，这里用于同步用户表情包

请求：

| Key    | 说明                                             | 示例                             |
| ------ | ------------------------------------------------ | -------------------------------- |
| apiKey | 通用密钥                                         | YOUR_API_KEY |
| gameId | 数据 Id                                          | emojis                           |
| data*  | 数据字符串，表情包内容为表情地址列表 JSON 字符串 | `["url1", "url2"]`               |

> *此处数据需确保符合格式，否则会影响其他客户端正常解析数据。
> *列表为覆盖式上传，请自行获取旧表情包列表，追加提交。

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/cloud/get' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
--header 'Content-Type: application/json' \
--data-raw '{
 "apiKey":"YOUR_API_KEY",
 "gameId":"emojis",
 "data":"[\"url1\", \"url2\"]"
}'
```

响应：

| Key  | 说明                              | 示例 |
| ---- | --------------------------------- | ---- |
| code | 为 0 则密钥有效，为 -1 则密钥无效 | 0    |
| msg  | 错误消息                          |      |

### 获取禁言中成员列表（思过崖）

`GET /chat-room/si-guo-list`

## 表情包 V2

### 接口列表

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

### Curl 示例

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

## 图床

### 上传图片

`POST /upload`

上传图片或视频

#### 限制

* **大小**：<=20M，
* **文件格式**：jpg, jpeg, png, gif, mp4
* **请求类型**：multipart/form-data

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| file[] | 文件     |                                  |
| apiKey | 通用密钥 | YOUR_API_KEY |

响应：

| Key          | 说明                              | 示例               |
| ------------ | --------------------------------- | ------------------ |
| code         | 为 0 则密钥有效，为 -1 则密钥无效 | 0                  |
| msg          | 错误消息                          |                    |
| data         | 上传结果                          | `{ ... }`          |
| - errFiles   | 上传失败的文件名列表              | `[ '文件名.jpg' ]` |
| - succMap    | 上传成功的文件信息                | `{ ... }`          |
| --`<文件名>` | 文件地址                          | https://...        |

## 复读机转录站

### 查询转录内容列表

`GET /api/repeater/items`

按类型查询复读机转录内容。传入 `apiKey` 时，响应会包含当前用户的点赞状态。

请求：

| Key                 | 说明                                      | 示例                             |
| ------------------- | ----------------------------------------- | -------------------------------- |
| repeaterContentType | 内容类型：`joke`/`kfc`/`fish`，不传查全部 | joke                             |
| apiKey              | 通用密钥，可选                            | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/repeater/items?repeaterContentType=joke&apiKey=YOUR_API_KEY'
```

响应：

| Key                              | 说明                         | 示例          |
| -------------------------------- | ---------------------------- | ------------- |
| code                             | 0成功/-1失败                 | 0             |
| msg                              | 错误信息                     | 类型不合法    |
| data                             | 响应数据                     | `{...}`       |
| - items                          | 转录内容列表                 | `[ ... ]`     |
| -- oId                           | 内容 Id                      | 1770000000000 |
| -- repeaterContentType           | 内容类型                     | joke          |
| -- repeaterContentTypeLabel      | 类型名称                     | 段子          |
| -- repeaterContent               | 正文                         | 转录内容      |
| -- repeaterContentAuthorId       | 作者 Id，系统种子内容为空    | 1770000000000 |
| -- repeaterContentAuthorName     | 作者用户名，系统种子内容为空 | fishpi        |
| -- repeaterContentSource         | 来源：`user`/`seed`          | user          |
| -- repeaterContentLikeCount      | 点赞数                       | 10            |
| -- repeaterContentLiked          | 当前用户是否已点赞           | true          |
| -- repeaterContentCreatedTime    | 创建时间戳                   | 1770000000000 |
| -- repeaterContentUpdatedTime    | 更新时间戳                   | 1770000000000 |

### 随机获取转录内容

`GET /api/repeater/next`

按类型随机获取一条转录内容。传入 `excludeId` 时，会尽量避开当前内容。

请求：

| Key                 | 说明                                      | 示例                             |
| ------------------- | ----------------------------------------- | -------------------------------- |
| repeaterContentType | 内容类型：`joke`/`kfc`/`fish`，不传查全部 | joke                             |
| excludeId           | 需要排除的内容 Id，可选                   | 1770000000000                    |
| apiKey              | 通用密钥，可选                            | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/repeater/next?repeaterContentType=fish&excludeId=1770000000000'
```

响应：

| Key    | 说明                         | 示例    |
| ------ | ---------------------------- | ------- |
| code   | 0成功/-1失败                 | 0       |
| msg    | 错误信息                     |         |
| data   | 响应数据                     | `{...}` |
| - item | 单条转录内容，字段同列表接口 | `{...}` |

### 上传转录内容

`POST /api/repeater`

登录用户上传一条复读机转录内容。

请求：

| Key                 | 说明                                  | 示例                             |
| ------------------- | ------------------------------------- | -------------------------------- |
| apiKey              | 通用密钥                              | YOUR_API_KEY |
| repeaterContentType | 内容类型：`joke`/`kfc`/`fish`         | kfc                              |
| repeaterContent     | 正文，2 到 500 字符，会清理 HTML 标签 | 今天疯狂星期四                   |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/repeater' \
--header 'Content-Type: application/json' \
--data-raw '{"apiKey":"YOUR_API_KEY","repeaterContentType":"kfc","repeaterContent":"今天疯狂星期四"}'
```

响应：

| Key    | 说明                         | 示例    |
| ------ | ---------------------------- | ------- |
| code   | 0成功/-1失败                 | 0       |
| msg    | 错误信息                     | 内容太短 |
| data   | 响应数据                     | `{...}` |
| - item | 已保存的转录内容，字段同列表 | `{...}` |

### 点赞转录内容

`POST /api/repeater/{id}/like`

登录用户点赞或取消点赞一条转录内容。

请求：

| Key    | 说明                       | 示例                             |
| ------ | -------------------------- | -------------------------------- |
| id     | 内容 Id，本参数为 URL 参数 | 1770000000000                    |
| apiKey | 通用密钥                   | YOUR_API_KEY |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/repeater/1770000000000/like' \
--header 'Content-Type: application/json' \
--data-raw '{"apiKey":"YOUR_API_KEY"}'
```

响应：

| Key                        | 说明                 | 示例 |
| -------------------------- | -------------------- | ---- |
| code                       | 0成功/-1失败         | 0    |
| msg                        | 错误信息             |      |
| data                       | 响应数据             | `{...}` |
| - liked                    | 操作后是否已点赞     | true |
| - repeaterContentLikeCount | 操作后的点赞数       | 11   |

## 专栏

### 更新专栏封面

`POST /api/columns/{columnId}/cover`

专栏作者更新自己专栏的封面图地址。传空字符串会清空封面，清空后页面使用默认封面。

请求：

| Key            | 说明                                             | 示例                             |
| -------------- | ------------------------------------------------ | -------------------------------- |
| columnId       | 专栏 Id，本参数为 URL 参数                       | 1770000000000                    |
| apiKey         | 通用密钥                                         | YOUR_API_KEY |
| columnCoverURL | 封面图地址，支持站内路径或 HTTP/HTTPS，最长 1024 | https://file.fishpi.cn/demo.jpg  |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/columns/1770000000000/cover' \
--header 'Content-Type: application/json' \
--data-raw '{"apiKey":"YOUR_API_KEY","columnCoverURL":"https://file.fishpi.cn/demo.jpg"}'
```

响应：

| Key                | 说明                 | 示例                       |
| ------------------ | -------------------- | -------------------------- |
| code               | 0成功/-1失败         | 0                          |
| msg                | 错误信息             | 专栏不存在或无权限操作     |
| data               | 响应数据             | `{...}`                    |
| - column           | 更新后的专栏数据     | `{...}`                    |
| -- oId             | 专栏 Id              | 1770000000000              |
| -- columnTitle     | 专栏名称             | 我的专栏                   |
| -- columnCoverURL  | 封面图地址           | https://file.fishpi.cn/... |
| -- columnHasCover  | 是否设置了自定义封面 | true                       |

## 帖子

### 查询文章草稿列表

`GET /api/article-drafts?apiKey=<Key>`

查询当前登录用户的文章草稿列表，最多返回 50 条。普通文章草稿和长文章草稿共用本接口，通过 `articleDraftType` 区分。列表项不包含正文、思绪正文和打赏区正文。

请求:

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/article-drafts?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

响应：

| Key                        | 说明                     | 示例          |
| -------------------------- | ------------------------ | ------------- |
| code                       | 0成功/-1失败             | 0             |
| msg                        | 错误信息                 | 草稿不存在    |
| data                       | 响应数据                 | `{...}`       |
| - drafts                   | 草稿列表                 | `[ ... ]`     |
| -- oId                     | 草稿 Id                  | 1770000000000 |
| -- articleDraftTitle       | 标题                     | 今天摸鱼了吗  |
| -- articleDraftSummary     | 正文摘要                 | 摘要内容      |
| -- articleDraftTags        | 标签，英文逗号分隔       | 摸鱼,日常     |
| -- articleDraftType        | 帖子类型，`6` 表示长文章 | 6             |
| -- articleDraftColumnId    | 长文章专栏 Id            | 1760000000000 |
| -- articleDraftColumnTitle | 长文章专栏名称           | 我的专栏      |
| -- articleDraftChapterNo   | 长文章章节号             | 1             |
| -- articleDraftUpdatedTime | 更新时间戳               | 1770000000000 |

### 保存文章草稿

`POST /api/article-drafts`

新建或更新当前登录用户的文章草稿。传入 `articleDraftId` 时更新已有草稿，不传则新建草稿。普通文章草稿和长文章草稿共用本接口，长文章请传 `articleType=6`。

请求:

| Key                        | 说明                             | 示例                             |
| -------------------------- | -------------------------------- | -------------------------------- |
| apiKey                     | 通用密钥                         | YOUR_API_KEY |
| articleDraftId             | 草稿 Id，更新时填写              | 1770000000000                    |
| articleTitle               | 标题，最长 255 字符              | 今天摸鱼了吗                     |
| articleContent             | 正文，最长 1024000 字符          | 正文内容                         |
| articleDraftThoughtContent | 思绪正文，最长 1024000 字符      | 思绪内容                         |
| articleTags                | 标签，最长 255 字符              | 摸鱼,日常                        |
| articleType                | 帖子类型，`6` 表示长文章         | 6                                |
| columnId                   | 长文章专栏 Id，最长 19 字符      | 1760000000000                    |
| columnTitle                | 新建长文章专栏名称，最长 64 字符 | 我的专栏                         |
| chapterNo                  | 长文章章节号，最长 32 字符       | 1                                |
| articleRewardContent       | 打赏区正文，最长 102400 字符     | 打赏可见内容                     |
| articleRewardPoint         | 打赏积分                         | 10                               |
| articleQnAOfferPoint       | 问答悬赏积分                     | 20                               |
| articleCommentable         | 是否允许评论                     | true                             |
| articleAnonymous           | 是否匿名                         | false                            |
| articleNotifyFollowers     | 是否通知关注者                   | false                            |
| articleShowInList          | 是否在列表展示，1展示/0不展示    | 1                                |
| articleStatement           | 声明类型                         | 0                                |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/article-drafts' \
--header 'Content-Type: application/json' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
--data-raw '{
  "apiKey":"YOUR_API_KEY",
  "articleTitle":"今天摸鱼了吗",
  "articleContent":"正文内容",
  "articleTags":"摸鱼,日常",
  "articleType":6,
  "columnId":"1760000000000",
  "chapterNo":"1"
}'
```

响应：

| Key                        | 说明                                                       | 示例                        |
| -------------------------- | ---------------------------------------------------------- | --------------------------- |
| code                       | 0成功/-1失败                                               | 0                           |
| msg                        | 错误信息                                                   | 标题长度不能超过 255 个字符 |
| data                       | 响应数据                                                   | `{...}`                     |
| - draft                    | 已保存的草稿信息，保存响应不包含正文、思绪正文和打赏区正文 | `{...}`                     |
| -- oId                     | 草稿 Id                                                    | 1770000000000               |
| -- articleDraftTitle       | 标题                                                       | 今天摸鱼了吗                |
| -- articleDraftSummary     | 正文摘要                                                   | 正文内容                    |
| -- articleDraftType        | 帖子类型，`6` 表示长文章                                   | 6                           |
| -- articleDraftUpdatedTime | 更新时间戳                                                 | 1770000000000               |

### 查询文章草稿详情

`GET /api/article-drafts/{id}?apiKey=<Key>`

查询当前登录用户的指定草稿详情。

请求:

| Key    | 说明                       | 示例                             |
| ------ | -------------------------- | -------------------------------- |
| id     | 草稿 Id，本参数为 URL 参数 | 1770000000000                    |
| apiKey | 通用密钥                   | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/article-drafts/1770000000000?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

响应：

| Key                            | 说明                     | 示例          |
| ------------------------------ | ------------------------ | ------------- |
| code                           | 0成功/-1失败             | 0             |
| msg                            | 错误信息                 | 草稿不存在    |
| data                           | 响应数据                 | `{...}`       |
| - draft                        | 草稿详情                 | `{...}`       |
| -- oId                         | 草稿 Id                  | 1770000000000 |
| -- articleDraftTitle           | 标题                     | 今天摸鱼了吗  |
| -- articleDraftContent         | 正文                     | 正文内容      |
| -- articleDraftThoughtContent  | 思绪正文                 | 思绪内容      |
| -- articleDraftTags            | 标签                     | 摸鱼,日常     |
| -- articleDraftType            | 帖子类型，`6` 表示长文章 | 6             |
| -- articleDraftColumnId        | 长文章专栏 Id            | 1760000000000 |
| -- articleDraftColumnTitle     | 长文章专栏名称           | 我的专栏      |
| -- articleDraftChapterNo       | 长文章章节号             | 1             |
| -- articleDraftRewardContent   | 打赏区正文               | 打赏可见内容  |
| -- articleDraftRewardPoint     | 打赏积分                 | 10            |
| -- articleDraftQnAOfferPoint   | 问答悬赏积分             | 20            |
| -- articleDraftCommentable     | 是否允许评论             | true          |
| -- articleDraftAnonymous       | 是否匿名                 | false         |
| -- articleDraftNotifyFollowers | 是否通知关注者           | false         |
| -- articleDraftShowInList      | 是否在列表展示           | 1             |
| -- articleDraftStatement       | 声明类型                 | 0             |
| -- articleDraftUpdatedTime     | 更新时间戳               | 1770000000000 |

### 删除文章草稿

`DELETE /api/article-drafts/{id}?apiKey=<Key>`

删除当前登录用户的指定草稿。

请求:

| Key    | 说明                       | 示例                             |
| ------ | -------------------------- | -------------------------------- |
| id     | 草稿 Id，本参数为 URL 参数 | 1770000000000                    |
| apiKey | 通用密钥                   | YOUR_API_KEY |

请求示例：

```bash
curl --location --request DELETE 'https://fishpi.cn/api/article-drafts/1770000000000?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36'
```

响应：

| Key  | 说明            | 示例          |
| ---- | --------------- | ------------- |
| code | 0成功/-1失败    | 0             |
| msg  | 错误信息        | 草稿不存在    |
| data | 响应数据        | `{...}`       |
| - id | 已删除的草稿 Id | 1770000000000 |

### 发贴

`POST /article`

发表帖子

请求：

| Key      | 说明         | 示例                             |
| -------- | ------------ | -------------------------------- |
| apiKey   | 通用密钥     | YOUR_API_KEY |
| articleBypassCensor | 免审发帖，布尔值；仅管理员、OP、超级会员分组可用 | true |
| 其它参数 | 自行发帖抓包 |                                  |

> `articleType=3` 的思绪新增已关闭，历史思绪仍可通过读取接口访问。
>
> `articleBypassCensor=true` 时跳过内容审核和敏感词过滤；服务端按用户分组校验，未授权用户传入该字段会返回失败。

### 更新贴子

`PUT /article/<id>`

更新帖子

请求：

| Key      | 说明         | 示例                             |
| -------- | ------------ | -------------------------------- |
| id       | 帖子 Id      | ...                              |
| apiKey   | 通用密钥     | YOUR_API_KEY |
| articleBypassCensor | 免审保存，布尔值；仅管理员、OP、超级会员分组可用 | true |
| 其它参数 | 自行发帖抓包 |                                  |

> `articleBypassCensor=true` 时跳过内容审核和敏感词过滤；服务端按用户分组校验，未授权用户传入该字段会返回失败。

### 帖子列表

#### 特别注意

所有帖子列表API都支持翻页功能，可在请求后加入参数 `?p=<Page>`自定义页码。

所有帖子列表APi都支持定义每页显示文章数量，可在请求后加入参数 `?size=<Size>`自定义数量。

#### 最近

* 最近帖子列表：`GET /api/articles/recent`
* 热门帖子列表：`GET /api/articles/recent/hot`
* 点赞帖子列表：`GET /api/articles/recent/good`
* 最近回复帖子列表：`GET /api/articles/recent/reply`
* 最新长篇帖子列表：`GET /api/articles/recent/long`

请求参数（以上接口通用）：

- `apiKey`：用户 API Key（必填）
- `p`：页码（可选，默认 1）
- `size`：每页数量（可选，默认使用系统值）

响应：

- 与“帖子列表通用响应”一致（`code`、`msg`、`data.pagination`、`data.articles`）

说明：

- `/api/articles/recent` 系列默认不再包含长篇文章（`articleType=6`）。
- 长篇文章请使用 `/api/articles/recent/long`。

#### 按标签

* 列出帖子：`GET /api/articles/tag/<标签URI>`
* 按热门程度列出帖子：`GET /api/articles/tag/<标签URI>/hot`
* 按点赞列出帖子：`GET /api/articles/tag/<标签URI>/good`
* 按最近回复列出帖子：`GET /api/articles/tag/<标签URI>/reply`
* 按优选列出帖子：`GET /api/articles/tag/<标签URI>/perfect`

注：标签URI可在点开某个标签的页面后，在URL中提取，例如[系统公告](https://fishpi.cn/tag/announcement)的标签URI为 `announcement`。

#### 按领域

* 列出帖子：`GET /api/articles/domain/<领域URI>`

注：领域URI与标签URI同理，都可以从领域的页面的URL中提取。

响应（帖子列表通用）：

| Key                   | 说明                                                 | 示例 |
| --------------------- | ---------------------------------------------------- | ---- |
| code                  | 为 0 则密钥有效，为 -1 则密钥无效                    | 0    |
| msg                   | 错误消息                                             |      |
| articleTitle          | 文章标题                                             |      |
| articleTags           | 文章标签                                             |      |
| articlePreviewContent | 文章简略文                                           |      |
| articleAuthor         | 文章作者信息                                         |      |
|                       | 还有很多参数，不难理解，大家先自己悟，我有时间再更新 |      |

### 获取指定帖子

获取指定帖子的评论、文章内容、目录、点赞数量等。

`GET /api/article/<文章ID>?apiKey=<Key>`

请求:

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/article/1636516552191?apiKey=YOUR_API_KEY'
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

响应：

| Key                             | 说明                                                           | 示例                               |
| ------------------------------- | -------------------------------------------------------------- | ---------------------------------- |
| code                            | 为 0 则密钥有效，为 -1 则密钥无效                              | 0                                  |
| msg                             | 错误消息                                                       |                                    |
| data                            | 帖子数据                                                       | `{...}`                            |
| - article                       | 帖子数据                                                       | `{...}`                            |
| -- articleTitleEmoj             | 文章标题                                                       | 摸鱼派社区开放 API 使用文档 V2.0.0 |
| -- articleTitleEmojUnicode      | 文章标题                                                       | 摸鱼派社区开放 API 使用文档 V2.0.0 |
| -- articleTitle                 | 文章标题                                                       | 摸鱼派社区开放 API 使用文档 V2.0.0 |
| -- articleCreateTimeStr         | 文章创建时间字符串                                             | 2021-11-10 11:55:52                |
| -- articleCreateTime            | 文章创建时间                                                   | Wed Nov 10 11:55:52 CST 2021       |
| -- articlePermalink             | 文章固定链接                                                   | /article/1636516552191             |
| -- timeAgo                      | 发布时间简写                                                   | 5 个月前                           |
| -- articleUpdateTimeStr         | 最后更新时间                                                   | 2022-04-28 18:11:30                |
| -- articleUpdateTime            | 文章更新时间                                                   | Thu Apr 28 18:11:30 CST 2022       |
| -- articleLatestCmtTime         | 文章最后修改时间                                               | Thu Apr 28 14:32:40 CST 2022       |
| -- articleType                  | 文章类型，0 = 帖子，1 = 机要，2 = 同城广播，3 = 思绪，5 = 问答 | 0                                  |
| -- articleStickRemains          | 置顶序号                                                       | 0                                  |
| -- articleStick                 | 是否置顶                                                       | 0                                  |
| -- articleViewCount             | 文章浏览数                                                     | 2176                               |
| -- articleThankCnt              | 文章感谢数                                                     | 7                                  |
| -- thankedCnt                   | 文章感谢数                                                     | 7                                  |
| -- articleCommentCount          | 文章评论数                                                     | 27                                 |
| -- articleBadCnt                | 反对数                                                         | 0                                  |
| -- articleGoodCnt               | 赞同数                                                         | 3                                  |
| -- articleWatchCnt              | 关注数                                                         | 1                                  |
| -- articleCollectCnt            | 收藏数                                                         | 2                                  |
| -- articleHeat                  | 文章点击数                                                     | 1                                  |
| -- articleAnonymousView         | 文章匿名浏览量                                                 | 0                                  |
| -- articleViewCntDisplayFormat  | 文章浏览量简写                                                 | 2.2K                               |
| -- articleShowInList            | 是否在列表展示                                                 | 1                                  |
| -- articlePerfect               | 文章是否优选                                                   | 0                                  |
| -- articleCommentable           | 文章是否启用评论                                               | true                               |
| -- rewarded                     | 是否开启打赏                                                   | false                              |
| -- rewardedCnt                  | 打赏人数                                                       | 0                                  |
| -- articleRewardPoint           | 文章打赏积分                                                   | 0                                  |
| -- articleQnAOfferPoint         | 悬赏积分                                                       | 0                                  |
| -- offered                      | 是否悬赏                                                       | false                              |
| -- articleAnonymous             | 是否匿名                                                       | 0                                  |
| -- articleAuthorName            | 作者用户名                                                     | adlered                            |
| -- isFollowing                  | 是否已关注                                                     | false                              |
| -- isWatching                   | 是否关注                                                       | false                              |
| -- isMyArticle                  | 是否是我的文章                                                 | false                              |
| -- thanked                      | 是否感谢                                                       | true                               |
| -- articleEditorType            | 编辑器类型                                                     | 0                                  |
| -- articleAudioURL              | 文章音频地址                                                   |                                    |
| -- articleToC                   | 文章目录渲染后的 HTML                                          | `...`                              |
| -- articlePreviewContent        | 文章预览内容                                                   | `...`                              |
| -- articleContent               | 文章内容 HTML                                                  | `<...>`                            |
| -- articleThumbnailURL          | 文章缩略图                                                     |                                    |
| -- articleImg1URL               | 第一张图片地址                                                 |                                    |
| -- articleVote                  | 文章点赞数                                                     | 0                                  |
| -- articleRandomDouble          | 文章随机数                                                     | 0.33495039072745036                |
| -- articleAuthorIntro           | 作者签名                                                       | 业余开源爱好者                     |
| -- articleCity                  | 发布地址                                                       | 北京                               |
| -- articleAuthorId              | 发布者Id                                                       | "1630399192600"                    |
| -- articleAuthorURL             | 作者首页地址                                                   | https://github.com/adlered         |
| -- articleAuthor                | 作者用户信息                                                   | `{...}`                            |
| -- articleAuthorThumbnailURL210 | 作者头像缩略图                                                 | `https:/...`                       |
| -- articleAuthorThumbnailURL48  | 作者头像缩略图                                                 | `https://...`                      |
| -- articleAuthorThumbnailURL20  | 作者头像缩略图                                                 | `https://...`                      |
| -- articlePushOrder             | 推送 Email 推送顺序                                            | 0                                  |
| -- articleTags                  | 文章标签                                                       | 系统公告,摸鱼派,API                |
| -- oId                          | 文章id                                                         | 1636516552191                      |
| -- articleTagObjs               | 文章标签信息                                                   | `[...]`                            |
| -- articleRewardContent         | 打赏内容                                                       | `<...>`                            |
| -- redditScore                  | reddit分数                                                     | 6283.477121254719                  |
| -- articleStatus                | 文章状态， 0 = 正常，1 = 封禁，2 = 锁定                        | 0                                  |
| -- pagination                   | 分页信息                                                       | `{...}`                            |
| --- paginationPageCount         | 评论分页数                                                     | 1                                  |
| --- paginationPageNums          | 建议分页页码                                                   | `[ 1 ]`                            |
| -- discussionViewable           | 评论是否可见                                                   | true                               |
| -- articleRevisionCount         | 文章修改次数                                                   | 37                                 |
| -- articleLatestCmterName       | 文章最后评论者                                                 | iwpz                               |
| -- cmtTimeAgo                   | 最后评论时间简写                                               | 5 天前                             |
| -- articleLatestCmtTimeStr      | 文章最后评论时间                                               | 2022-04-28 14:32:40                |
| -- articleComments              | 文章的评论                                                     | `[...]`                            |
| -- articleNiceComments          | 文章最佳评论                                                   | `[...]`                            |

**标签信息**

| Key               | 说明                          | 示例               |
| ----------------- | ----------------------------- | ------------------ |
| oId               | 标签 ID                       | 1630652039941      |
| tagTitle          | 标签名                        | 有趣               |
| tagDescription    | 标签描述                      |                    |
| tagStatus         | 标签状态， 0 = 正常，1 = 封禁 | 0                  |
| tagURI            | 标签地址                      | %e6%9c%89%e8%b6%a3 |
| tagIconPath       | icon 图地址                   | `https://...`      |
| tagCommentCount   | 回帖计数                      | 164                |
| tagReferenceCount | 引用计数                      | 15                 |
| tagFollowerCount  | 关注数                        | 477                |
| tagBadCnt         | 反对数                        | 0                  |
| tagGoodCnt        | 点赞数                        | 0                  |
| tagLinkCount      | 标签相关链接计数              | 0                  |
| tagSeoTitle       | 标签 SEO 标题                 | 有趣               |
| tagSeoDesc        | 标签 SEO 描述                 |                    |
| tagSeoKeywords    | 标签关键字                    | 有趣               |
| tagCSS            | 标签自定义 CSS                |                    |
| tagAd             | 标签广告内容                  |                    |
| tagShowSideAd     | 是否展示广告，0 = 是，1 = 否  | 0                  |
| tagRandomDouble   | 标签随机数                    | 0.9355077930993895 |

**评论与作者用户信息**

| Key                           | 说明                                      | 示例          |
| ----------------------------- | ----------------------------------------- | ------------- |
| userOnlineFlag                | 用户是否在线                              | false         |
| onlineMinute                  | 用户在线时长                              | 651           |
| userPointStatus               | 是否公开积分列表， 0 = 公开，1 = 不公开   | 0             |
| userFollowerStatus            | 是否公开关注者列表， 0 = 公开，1 = 不公开 | 0             |
| userCommentStatus             | 是否公开回帖列表， 0 = 公开，1 = 不公开   | 0             |
| userOnlineStatus              | 是否公开在线状态， 0 = 公开，1 = 不公开   | 0             |
| userUAStatus                  | 是否公开 UA 信息， 0 = 公开，1 = 不公开   | 0             |
| userWatchingArticleStatus     | 是否公开关注帖子列表                      | 0             |
| userFollowingTagStatus        | 是否公开关注标签列表                      | 0             |
| userJoinPointRank             | 是否加入积分排行                          | 0             |
| userJoinUsedPointRank         | 是否加入消费排行                          | 0             |
| userFollowingArticleStatus    | 是否公开收藏帖子列表                      | 0             |
| userArticleStatus             | 是否公开帖子列表                          | 0             |
| userBreezemoonStatus          | 是否公开清风明月列表                      | 0             |
| userKeyboardShortcutsStatus   | 是否启用键盘快捷键                        | 1             |
| chatRoomPictureStatus         | 是否聊天室图片自动模糊                    | 1             |
| userForwardPageStatus         | 是否启用站外链接跳转页面                  | 1             |
| userCommentViewMode           | 回帖浏览模式                              | 1             |
| userGuideStep                 | 用户完成新手指引步数                      | 0             |
| userCurrentCheckinStreakStart | 上次登录日期                              | 20220413      |
| userTags                      | 用户标签                                  | `...,...`     |
| sysMetal                      | 用户徽章                                  | `[...]`       |
| userTimezone                  | 用户时区                                  | Asia/Shanghai |
| userURL                       | 用户个人主页                              | `http://...`  |
| userIndexRedirectURL          | 自定义首页跳转地址                        |               |
| userLatestArticleTime         | 最近发帖时间                              | 1646191348504 |
| userTagCount                  | 标签计数                                  | 0             |
| userNickname                  | 昵称                                      | 大白菜        |
| userListViewMode              | 回帖浏览模式， 0 = 传统， 1 = 实时        | 1             |
| userLongestCheckinStreak      | 最长连续签到                              | 4             |
| userAvatarType                | 用户头像类型                              | 2             |
| userSubMailSendTime           | 用户确认邮件发送时间                      | 1645580075949 |
| userUpdateTime                | 用户最后更新时间                          | 1650763072011 |
| userSubMailStatus             | 用户邮箱绑定状态                          | 0             |
| userLatestLoginTime           | 用户最后登录时间                          | 1650763072011 |
| userAppRole                   | 应用角色                                  | 0             |
| userAvatarViewMode            | 头像查看模式                              | 0             |
| userStatus                    | 用户状态                                  | 0             |
| userLongestCheckinStreakEnd   | 用户上次最长连续签到日期                  | 20220226      |
| userLatestCmtTime             | 上次回帖时间                              | 1651195288787 |
| userProvince                  | 用户省份                                  | 河北省        |
| userCurrentCheckinStreak      | 用户当前连续签到计数                      | 1             |
| userNo                        | 用户编号                                  | 4611          |
| userAvatarURL                 | 用户头像                                  | `https://...` |
| userLanguage                  | 用户语言                                  | zh_CN         |
| userCurrentCheckinStreakEnd   | 上次签到日期                              | 20220413      |
| userReplyWatchArticleStatus   | 是否回帖后自动关注帖子                    | 1             |
| userCheckinTime               | 用户上次签到时间                          | 1649835297813 |
| userUsedPoint                 | 用户消费积分                              | 805           |
| userPoint                     | 用户积分                                  | 5264          |
| userCommentCount              | 用户回帖数量                              | 41            |
| userIntro                     | 用户个性签名                              |               |
| userMobileSkin                | 移动端主题                                | mobile        |
| userListPageSize              | 分页每页条目                              | 60            |
| oId                           | 用户 ID                                   | 1645580042349 |
| userName                      | 用户Id                                    | 2516484465    |
| userGeoStatus                 | 是否公开 IP 地理信息                      | 0             |
| userLongestCheckinStreakStart | 最长连续签到起始日                        | 20220223      |
| userSkin                      | 用户主题                                  | classic       |
| userNotifyStatus              | 是否启用 Web 通知                         | 0             |
| userFollowingUserStatus       | 公开关注用户列表                          | 0             |
| userArticleCount              | 文章数                                    | 3             |
| userRole                      | 用户角色                                  | 1630553360689 |

**评论信息**

| Key                       | 说明              | 示例                                       |
| ------------------------- | ----------------- | ------------------------------------------ |
| commentCreateTimeStr      | 评论日期          | 2021-12-02 22:01:15                        |
| commentAuthorId           | 评论作者 Id       | 1630586509670                              |
| commentScore              | 评论分数          | 0.549092369988321                          |
| commentCreateTime         | 评论创建时间      | Thu Dec 02 22:01:15 CST 2021               |
| commentAuthorURL          | 评论作者 URL      | https://my.hancel.org/about                |
| commentVote               | 评论点赞数        | -1                                         |
| timeAgo                   | 评论日期简写      | 4 个月前                                   |
| commentOriginalCommentId  | 评论原始oId       |                                            |
| sysMetal                  | 徽章              | `[...]`                                    |
| commentGoodCnt            | 点赞数            | 2                                          |
| commentVisible            | 评论是否可见      | 0                                          |
| commentOnArticleId        | 评论在文章中的 ID | 1638373310692                              |
| rewardedCnt               | 感谢数            | 3                                          |
| commentThankLabel         | 感谢文案          | 确定赠送 15 积分给 imlinhanchao 以表谢意？ |
| commentSharpURL           | 固定连接          | /article/1638373310692#1638453675866       |
| commentAnonymous          | 是否匿名          | 0                                          |
| commentReplyCnt           | 回复数            | 0                                          |
| oId                       | 评论ID            | 1638453675866                              |
| commentContent            | 评论内容          | `<...>`                                    |
| commentStatus             | 评论状态          | 0                                          |
| commenter                 | 评论者用户信息    | `{...}`                                    |
| paginationCurrentPageNum  | 评论所在页码      | 1                                          |
| commentAuthorName         | 评论者用户名      | imlinhanchao                               |
| commentThankCnt           | 感谢数            | 3                                          |
| commentBadCnt             | 反对数            | 0                                          |
| rewarded                  | 是否感谢了        | false                                      |
| commentAuthorThumbnailURL | 评论作者头像      | `https://...`                              |
| commentAudioURL           | 评论音频地址      |                                            |
| commentQnAOffered         | 是否被采纳        | 0                                          |

### 获取指定用户的帖子列表

`GET /api/user/{userName}/articles`

请求：

| Key      | 说明                                                                               | 示例  |
| -------- | ---------------------------------------------------------------------------------- | ----- |
| p        | 页码（第几页）                                                                     | 1     |
| size     | 每页显示多少条结果（可能由于部分用户权限设置，最终显示条数低于该结果，属正常情况） | 20    |
| userName | 用户名（本参数为URL参数，请注意）                                                  | adler |

### 给文章点赞

`POST /vote/up/article`

请求：

| Key    | 说明      | 示例                             |
| ------ | --------- | -------------------------------- |
| apiKey | 通用密钥  | YOUR_API_KEY |
| dataId | 文章的oId | 1645002736006                    |

响应：

| Key  | 说明                                                | 示例 |
| ---- | --------------------------------------------------- | ---- |
| code | 为0则成功                                           | 0    |
| type | -1为点赞成功，0代表已经点过赞了，所以帮你取消了点赞 | -1   |

### 感谢文章

`POST /article/thank?articleId={文章oId}`

**注意**：文章oId在URL上传参，apiKey还是通过POST进行哦。

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

响应：

| Key  | 说明      | 示例 |
| ---- | --------- | ---- |
| code | 为0则成功 | 0    |

### 获取帖子的评论列表（旧）

获取指定帖子的评论列表。

获取第一页时，data中会有 `articleNiceComment`，即优质回帖。

`GET /api/comment/<文章ID>?apiKey=<Key>&p=<页数>`

请求:

| Key       | 说明     | 示例                             |
| --------- | -------- | -------------------------------- |
| apiKey    | 通用密钥 | YOUR_API_KEY |

### 获取评论线程根评论列表（新）

`POST /comment/thread/parents`

获取指定帖子的线程根评论列表，并返回每条根评论的少量回复预览。

请求:

| Key                      | 说明                                      | 示例                             |
| ------------------------ | ----------------------------------------- | -------------------------------- |
| apiKey                   | 通用密钥，访问禁止匿名查看的帖子时必填    | YOUR_API_KEY |
| articleId                | 帖子 Id                                   | 1636516552191                    |
| paginationCurrentPageNum | 页码，默认 1                              | 1                                |
| userCommentViewMode      | 评论查看模式，默认传统模式                | 0                                |
| sort                     | 排序方式，`hot` 表示热门排序              | hot                              |
| author                   | 填`1` 表示只看楼主                        | 1                                |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/comment/thread/parents' \
--header 'Content-Type: application/json' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
--data-raw '{
  "articleId":"1636516552191",
  "paginationCurrentPageNum":1,
  "sort":"hot"
}'
```

响应：

| Key                        | 说明                                                   | 示例                 |
| -------------------------- | ------------------------------------------------------ | -------------------- |
| code                       | 0成功/-1失败                                           | 0                    |
| msg                        | 错误信息                                               | 文章不存在或不可查看 |
| commentThreadParents       | 线程根评论列表                                         | `[ ... ]`            |
| - oId                      | 评论 Id                                                | 1645002736006        |
| - commentId                 | 评论 Id                                                | 1645002736006        |
| - commentContent           | 评论内容，格式为 HTML                                  | `牛蛙牛蛙`           |
| - commentAuthorName        | 评论作者用户名                                         | adlered              |
| - commentAuthorId          | 评论作者 Id                                            | 1630512345670        |
| - commentAuthorNickName    | 评论作者昵称                                           | 阿达                 |
| - commentAuthorThumbnailURL | 评论作者头像                                           | `https://...`        |
| - commenter                 | 评论者公开信息，仅包含 `userName/userNickname/userUAStatus` | `{...}`              |
| - commentIsArticleAuthor   | 评论作者是否为楼主                                     | true                 |
| - commentIsCurrentUser     | 评论作者是否为当前用户                                 | false                |
| - commentVisible           | 可见范围                                               | 0                    |
| - commentThankCnt          | 感谢数                                                 | 1                    |
| - rewardedCnt              | 感谢数                                                 | 1                    |
| - rewarded                 | 当前登录用户是否已感谢                                 | false                |
| - commentVote              | 当前登录用户投票状态                                   | -1                   |
| - commentThankLabel        | 感谢确认文案                                           | 确定赠送 15 积分给 adlered 以表谢意？ |
| - commentThreadReplies     | 回复预览列表                                           | `[ ... ]`            |
| -- commentRevisionCount    | 预览回复历史版本数量                                   | 2                    |
| - commentThreadReplyCount  | 该线程回复总数                                         | 3                    |
| - commentThreadHasMore     | 是否还有更多回复                                       | true                 |
| - reactionSummary          | 该评论当前的表情汇总                                   | `[ ... ]`            |
| - currentUserReaction      | 当前登录用户在该评论上已选择的表情值，没有则为空字符串 | heart                |
| pagination                 | 分页信息                                               | `{...}`              |
| - paginationCurrentPageNum | 当前页码                                               | 1                    |
| - paginationPageCount      | 总页数                                                 | 5                    |
| - paginationPageSize       | 每页数量                                               | 20                   |
| - paginationRecordCount    | 总记录数                                               | 100                  |

> 响应允许返回公开作者 Id；不返回 `commentIP`、`commentUA`、`articleIP`、`articleUA`、密码、邮箱、手机号、QQ、2FA 密钥、token/key、完整 `Article` 或完整 `commenter` 对象。只看楼主请使用 `author=1`，不支持按任意 `commentAuthorId` 过滤。
> 该接口会按目标帖子的匿名访问设置和讨论帖权限判断可见性；无权访问时返回 `文章不存在或不可查看`。

### 获取评论线程回复列表（新）

`POST /comment/thread/replies`

获取指定评论所属线程的回复列表。传入任意子回复 Id 时，会自动定位到该线程根评论。

> 单个线程回复过多时会返回 `评论回复过多，请进入原评论查看`。

请求:

| Key                      | 说明                                      | 示例                             |
| ------------------------ | ----------------------------------------- | -------------------------------- |
| apiKey                   | 通用密钥，访问禁止匿名查看的帖子时必填    | YOUR_API_KEY |
| commentId                | 评论 Id                                   | 1645002736006                    |
| paginationCurrentPageNum | 页码，优先使用该字段                      | 1                                |
| page                     | 页码，兼容字段                            | 1                                |
| anchorCommentId          | 锚点评论 Id，填写后自动定位到该评论所在页 | 1645002736007                    |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/comment/thread/replies' \
--header 'Content-Type: application/json' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
--data-raw '{
  "commentId":"1645002736006",
  "paginationCurrentPageNum":1
}'
```

响应：

| Key                        | 说明                                                   | 示例                 |
| -------------------------- | ------------------------------------------------------ | -------------------- |
| code                       | 0成功/-1失败                                           | 0                    |
| msg                        | 错误信息                                               | 评论不存在或不可查看 |
| commentThreadReplies       | 线程回复列表                                           | `[ ... ]`            |
| - oId                      | 回复 Id                                                | 1645002736007        |
| - commentId                 | 回复 Id                                                | 1645002736007        |
| - commentThreadRootId      | 线程根评论 Id                                          | 1645002736006        |
| - commentThreadDepth       | 回复深度，从 0 开始                                    | 0                    |
| - commentAuthorName        | 回复作者用户名                                         | adlered              |
| - commentAuthorId          | 回复作者 Id                                            | 1630512345670        |
| - commentAuthorNickName    | 回复作者昵称                                           | 阿达                 |
| - commentAuthorThumbnailURL | 回复作者头像                                           | `https://...`        |
| - commenter                 | 回复者公开信息，仅包含 `userName/userNickname/userUAStatus` | `{...}`              |
| - commentIsArticleAuthor   | 回复作者是否为楼主                                     | false                |
| - commentIsCurrentUser     | 回复作者是否为当前用户                                 | true                 |
| - commentOriginalCommentId | 被回复的评论 Id                                        | 1645002736006        |
| - commentOriginalAuthorName | 被回复评论作者用户名                                   | adlered              |
| - commentOriginalAuthorNickName | 被回复评论作者昵称                                | 阿达                 |
| - commentContent           | 回复内容，格式为 HTML                                  | `牛蛙牛蛙`           |
| - commentVisible           | 可见范围                                               | 0                    |
| - commentRevisionCount     | 回复历史版本数量                                       | 2                    |
| - commentThankCnt          | 感谢数                                                 | 1                    |
| - rewardedCnt              | 感谢数                                                 | 1                    |
| - rewarded                 | 当前登录用户是否已感谢                                 | false                |
| - commentVote              | 当前登录用户投票状态                                   | 1                    |
| - commentThankLabel        | 感谢确认文案                                           | 确定赠送 15 积分给 adlered 以表谢意？ |
| - reactionSummary          | 该回复当前的表情汇总                                   | `[ ... ]`            |
| - currentUserReaction      | 当前登录用户在该回复上已选择的表情值，没有则为空字符串 | heart                |
| commentThreadReplyCount    | 线程回复总数                                           | 3                    |
| commentThreadRootId        | 线程根评论 Id                                          | 1645002736006        |
| pagination                 | 分页信息                                               | `{...}`              |
| - paginationCurrentPageNum | 当前页码                                               | 1                    |
| - paginationPageCount      | 总页数                                                 | 1                    |
| - paginationPageSize       | 每页数量                                               | 20                   |
| - paginationRecordCount    | 总记录数                                               | 3                    |

> 响应允许返回公开作者 Id；不返回 `commentIP`、`commentUA`、`articleIP`、`articleUA`、密码、邮箱、手机号、QQ、2FA 密钥、token/key、完整 `Article` 或完整 `commenter` 对象。
> 该接口会按目标帖子的匿名访问设置和讨论帖权限判断可见性；无权访问时返回 `评论不存在或不可查看`。

### 评论/回复

`POST /comment`

请求：

| Key                      | 说明                                                              | 示例                             |
| ------------------------ | ----------------------------------------------------------------- | -------------------------------- |
| apiKey                   | 通用密钥                                                          | YOUR_API_KEY |
| articleId                | 文章的oId                                                         | 1645002736006                    |
| commentAnonymous         | 是否匿名评论，是填写true，否填写false                             | false                            |
| commentVisible           | 是否仅楼主可见，是填写true，否填写false                           | false                            |
| commentContent           | 评论原文（Markdown格式）                                          | hello world                      |
| commentOriginalCommentId | （选填）如果是回复某个评论，传递此参数并填写值为要回复的评论的oId | 1646216549324                    |

### 长篇段评

`POST /comment/paragraph`

为长篇正文指定段落发表评论或回复。旧的 `POST /comment` 继续创建章评。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥，可选 | YOUR_API_KEY |
| articleId | 长篇文章 Id | 1645002736006 |
| paragraphId | 服务端生成的段落 Id | `64 位小写 SHA-256` |
| commentContent | 评论原文（Markdown 格式） | 这一段写得很好 |
| commentAnonymous | 是否匿名评论 | false |
| commentVisible | 是否仅楼主可见 | false |
| commentOriginalCommentId | 回复时填写段评 Id，可选 | 1646216549324 |

请求示例：

```bash
curl -X POST "https://fishpi.cn/comment/paragraph" \
  -H "Content-Type: application/json" \
  -d '{"apiKey":"你的 apiKey","articleId":"1645002736006","paragraphId":"64 位小写 SHA-256","commentContent":"这一段写得很好","commentAnonymous":false,"commentVisible":false}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | 0 |
| commentId | 新评论 Id | 1646216549324 |

> 仅支持 `articleType=6`。段落类型、顺序和文本快照由服务端校验并保存，客户端不能伪造。新增根评论必须关联当前有效段落；回复必须属于同一文章和同一段评线程，原段落已修改的线程仍可继续回复。

### 获取长篇段评摘要

`POST /comment/paragraph/summary`

获取长篇当前有效段落的评论数量，数量包含根评论和回复。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 访问受限文章时填写 | YOUR_API_KEY |
| articleId | 长篇文章 Id | 1645002736006 |

请求示例：

```bash
curl -X POST "https://fishpi.cn/comment/paragraph/summary" \
  -H "Content-Type: application/json" \
  -d '{"articleId":"1645002736006"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | 0 |
| paragraphComments | 段落评论数量 | `[...]` |
| - paragraphId | 段落 Id | `64 位小写 SHA-256` |
| - commentCount | 段评总数 | 3 |

### 获取长篇段评线程

`POST /comment/paragraph/thread/parents`

获取指定段落的评论线程根评论。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 访问受限文章时填写 | YOUR_API_KEY |
| articleId | 长篇文章 Id | 1645002736006 |
| paragraphId | 段落 Id | `64 位小写 SHA-256` |
| paginationCurrentPageNum | 页码，默认 1 | 1 |
| sort | `hot` 表示热门排序 | hot |

请求示例：

```bash
curl -X POST "https://fishpi.cn/comment/paragraph/thread/parents" \
  -H "Content-Type: application/json" \
  -d '{"articleId":"1645002736006","paragraphId":"64 位小写 SHA-256","paginationCurrentPageNum":1,"sort":"hot"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | 0 |
| commentThreadParents | 段评线程根评论，字段与线程接口一致 | `[...]` |
| paragraphId | 当前段落 Id | `64 位小写 SHA-256` |
| paragraphSnapshot | 当前段落摘要 | 这一段写得很好 |
| pagination | 分页信息 | `{...}` |

> 段评接口遵循文章匿名访问、讨论帖权限和评论可见性校验；响应不返回评论 IP、UA、联系方式、密钥或完整用户对象。文章编辑后无法匹配的段评会保留为“原段落已修改”，不会丢失。

### 更新评论

`PUT /comment/{评论oId}`

请求：

| Key              | 说明                                    | 示例                             |
| ---------------- | --------------------------------------- | -------------------------------- |
| apiKey           | 通用密钥                                | YOUR_API_KEY |
| articleId        | 文章的oId                               | 1645002736006                    |
| commentAnonymous | 是否匿名评论，是填写true，否填写false   | false                            |
| commentVisible   | 是否仅楼主可见，是填写true，否填写false | false                            |
| commentContent   | 评论原文（Markdown格式）                | hello world                      |

响应：

| Key                  | 说明                                      | 示例      |
| -------------------- | ----------------------------------------- | --------- |
| code                 | `0` 表示成功                              | 0         |
| commentContent       | 更新后的评论内容，格式为 HTML             | `<p>...</p>` |
| commentRevisionCount | 更新后的历史版本数量；小于 2 时没有历史记录 | 2         |

### 给评论点赞

`POST /vote/up/comment`

请求：

| Key    | 说明      | 示例                             |
| ------ | --------- | -------------------------------- |
| apiKey | 通用密钥  | YOUR_API_KEY |
| dataId | 评论的oId | 1645002736006                    |

响应：

| Key  | 说明                                                | 示例 |
| ---- | --------------------------------------------------- | ---- |
| code | 为0则成功                                           | 0    |
| type | -1为点赞成功，0代表已经点过赞了，所以帮你取消了点赞 | -1   |

### 感谢评论

`POST /comment/thank`

**注意**：不能给自己点感谢！会报错，记得做处理哦。

另外，感谢是不能取消的，会扣除15积分送给对方。

请求：

| Key       | 说明      | 示例                             |
| --------- | --------- | -------------------------------- |
| apiKey    | 通用密钥  | YOUR_API_KEY |
| commentId | 评论的oId | 1645002736006                    |

响应：

| Key  | 说明      | 示例 |
| ---- | --------- | ---- |
| code | 为0则成功 | 0    |

### 删除评论

`POST /comment/{评论的oId}/remove`

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

响应：

| Key  | 说明      | 示例 |
| ---- | --------- | ---- |
| code | 为0则成功 | 0    |

### 获取帖子当前正在阅读的人数

`POST /api/article/heat/{articleId}`

> 注意：如果你注意到了 `/article-channel` 频道中有 `{"articleId":"1631779202219","type":"articleHeat","operation":"+"}` 这种消息的时候，operation是加号代表又来了一个阅读的人，减号代表走了一个阅读的人，可以和本API进行结合，实时展示共同阅读人数

请求：

| Key       | 说明      | 示例          |
| --------- | --------- | ------------- |
| articleId | 文章的oId | 1636516552191 |

响应：

| Key         | 说明     | 示例 |
| ----------- | -------- | ---- |
| articleHeat | 阅读人数 | 1    |

### 获取帖子的Markdown原文

`GET /api/article/md/{articleId}`

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

获取文章接口返回的是渲染好的HTML，如果你需要渲染数学公式或者有更多样式要求，建议获取Markdown原文进行处理。

## 模块：帖子历史版本

### 获取帖子历史版本列表

`GET /article/<id>/revisions/list?apiKey=<Key>`

获取指定帖子的历史版本列表。该接口只返回版本 Id、时间、作者等元数据，不返回标题和正文。

请求：

| Key    | 说明                       | 示例                             |
| ------ | -------------------------- | -------------------------------- |
| id     | 帖子 Id，本参数为 URL 参数 | 1636516552191                    |
| apiKey | 通用密钥                   | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/article/1636516552191/revisions/list?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0'
```

响应：

| Key                          | 说明                                    | 示例                |
| ---------------------------- | --------------------------------------- | ------------------- |
| code                         | 为 0 则成功                             | 0                   |
| msg                          | 错误信息                                | 文章不存在          |
| revisions                    | 历史版本列表                            | `[ ... ]`           |
| revisions[].revisionId       | 历史版本 Id，`current` 表示当前帖子内容 | 1636516552191       |
| revisions[].revisionTime     | 历史版本时间戳，单位毫秒                | 1636516552191       |
| revisions[].revisionTimeStr  | 历史版本时间                            | 2021-11-10 11:55:52 |
| revisions[].revisionAuthorId | 修改者用户 Id                           | 1630512345670       |
| revisions[].revisionIndex    | 历史版本序号                            | 1                   |
| revisions[].current          | 是否为当前帖子内容                      | false               |

响应示例：

```json
{
  "code": 0,
  "revisions": [
    {
      "revisionId": "1636516552191",
      "revisionTime": 1636516552191,
      "revisionTimeStr": "2021-11-10 11:55:52",
      "revisionAuthorId": "1630512345670",
      "revisionIndex": 1,
      "current": false
    },
    {
      "revisionId": "current",
      "revisionTime": 1636519999999,
      "revisionTimeStr": "2021-11-10 12:53:19",
      "revisionAuthorId": "1630512345670",
      "revisionIndex": 2,
      "current": true
    }
  ]
}
```

### 获取帖子指定历史版本

`GET /article/<id>/revisions/<revisionId>?apiKey=<Key>`

获取指定帖子的某个历史版本标题和正文。

请求：

| Key        | 说明                                                              | 示例                             |
| ---------- | ----------------------------------------------------------------- | -------------------------------- |
| id         | 帖子 Id，本参数为 URL 参数                                        | 1636516552191                    |
| revisionId | 历史版本 Id，取自历史版本列表接口；传`current` 可读取当前帖子内容 | 1636516552191                    |
| apiKey     | 通用密钥                                                          | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/article/1636516552191/revisions/1636516552191?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0'
```

响应：

| Key                                  | 说明                     | 示例                        |
| ------------------------------------ | ------------------------ | --------------------------- |
| code                                 | 为 0 则成功              | 0                           |
| msg                                  | 错误信息                 | 历史版本不存在              |
| revision                             | 历史版本内容             | `{ ... }`                   |
| revision.revisionId                  | 历史版本 Id              | 1636516552191               |
| revision.revisionTime                | 历史版本时间戳，单位毫秒 | 1636516552191               |
| revision.revisionTimeStr             | 历史版本时间             | 2021-11-10 11:55:52         |
| revision.revisionData                | 历史版本数据             | `{ ... }`                   |
| revision.revisionData.articleTitle   | 帖子标题                 | 摸鱼派社区开放 API 使用文档 |
| revision.revisionData.articleContent | 帖子正文                 | Markdown 或 HTML 内容       |

响应示例：

```json
{
  "code": 0,
  "revision": {
    "revisionId": "1636516552191",
    "revisionTime": 1636516552191,
    "revisionTimeStr": "2021-11-10 11:55:52",
    "revisionData": {
      "articleTitle": "摸鱼派社区开放 API 使用文档",
      "articleContent": "帖子正文内容"
    }
  }
}
```

## 模块：评论历史版本

### 获取评论历史版本

`GET /comment/<id>/revisions?apiKey=<Key>`

获取指定评论的历史版本列表。该接口一次返回该评论的所有历史正文。

请求：

| Key    | 说明                       | 示例                             |
| ------ | -------------------------- | -------------------------------- |
| id     | 评论 Id，本参数为 URL 参数 | 1636516552191                    |
| apiKey | 通用密钥                   | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/comment/1636516552191/revisions?apiKey=YOUR_API_KEY' \
--header 'User-Agent: Mozilla/5.0'
```

响应：

| Key                                     | 说明                       | 示例          |
| --------------------------------------- | -------------------------- | ------------- |
| code                                    | 为 0 则成功                | 0             |
| revisions                               | 历史版本列表               | `[ ... ]`     |
| revisions[].oId                         | 历史版本 Id                | 1636516552191 |
| revisions[].revisionDataId              | 评论 Id                    | 1636516552191 |
| revisions[].revisionDataType            | 历史版本类型，`1` 表示评论 | 1             |
| revisions[].revisionAuthorId            | 修改者用户 Id              | 1630512345670 |
| revisions[].revisionData                | 历史版本数据               | `{ ... }`     |
| revisions[].revisionData.commentContent | 评论正文                   | 评论内容      |

响应示例：

```json
{
  "code": 0,
  "revisions": [
    {
      "oId": "1636516552191",
      "revisionDataId": "1636516552191",
      "revisionDataType": 1,
      "revisionAuthorId": "1630512345670",
      "revisionData": {
        "commentContent": "第一版评论内容"
      }
    },
    {
      "oId": "1636516666666",
      "revisionDataId": "1636516552191",
      "revisionDataType": 1,
      "revisionAuthorId": "1630512345670",
      "revisionData": {
        "commentContent": "第二版评论内容"
      }
    }
  ]
}
```

说明：

- 该接口需要登录权限，也支持通过 `apiKey` 调用。
- 仅目标文章和评论可见范围均允许当前用户查看时返回历史内容；讨论帖还会校验参与权限。
- 当当前评论内容与最后一个已存历史版本不一致时，响应末尾可能追加当前评论内容。

## 清风明月

### 获取清风明月列表

`GET /api/breezemoons`

请求：

| Key  | 说明                                                                               | 示例 |
| ---- | ---------------------------------------------------------------------------------- | ---- |
| p    | 页码（第几页）                                                                     | 1    |
| size | 每页显示多少条结果（可能由于部分用户权限设置，最终显示条数低于该结果，属正常情况） | 20   |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/breezemoons?p=1&size=20' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

响应：

| Key                              | 说明                               | 示例      |
| -------------------------------- | ---------------------------------- | --------- |
| code                             | 为0则获取成功                      | 0         |
| breezemoons                      | 数据列表                           | `[ ... ]` |
| - breezemoonAuthorName           | 发布者用户名                       |           |
| - breezemoonUpdated              | 最后更新时间                       |           |
| - oId                            | 清风明月ID                         |           |
| - breezemoonCreated              | 创建时间                           |           |
| - breezemoonAuthorThumbnailURL48 | 发布者头像URL                      |           |
| - timeAgo                        | 发布时间                           |           |
| - breezemoonContent              | 正文                               |           |
| - breezemoonCreateTime           | 创建时间                           |           |
| - breezemoonCity                 | 发布城市（可能为空，请注意做判断） |           |

### 发布清风明月

`POST /breezemoon`

请求：

| Key               | 说明         | 示例                             |
| ----------------- | ------------ | -------------------------------- |
| apiKey            | 通用密钥     | YOUR_API_KEY |
| breezemoonContent | 清风明月正文 | helloworld                       |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/breezemoon' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
--header 'Content-Type: application/json' \
--data-raw '{
 "apiKey":"YOUR_API_KEY",
 "breezemoonContent":"helloworld"
}'
```

响应：

| Key  | 说明        | 示例 |
| ---- | ----------- | ---- |
| code | 为 0 则成功 | 0    |
| msg  | 错误消息    |      |

### 获取指定用户的清风明月列表

`GET /api/user/{userName}/breezemoons`

请求：

| Key      | 说明                                                                               | 示例    |
| -------- | ---------------------------------------------------------------------------------- | ------- |
| p        | 页码（第几页）                                                                     | 1       |
| size     | 每页显示多少条结果（可能由于部分用户权限设置，最终显示条数低于该结果，属正常情况） | 20      |
| userName | 用户名（本参数为URL参数，请注意）                                                  | adlered |

## 私信

### 私信部分已重构

新版私信系统全部采用摸鱼派开放 API 进行请求，全动态（包括 Web 端），API 已全部开发完毕，但是 API 文档还得等等（没时间写），如果你需要重构私信系统，请先自行抓包哦～

## 用户管理

### 永久停用用户

`POST /admin/user/{userId}/deactivate`

永久停用指定用户，匿名化账号资料并清空绑定手机号。仅管理员分组可操作，不支持停用当前管理员账号。

请求：

| Key       | 说明                         | 示例                |
| --------- | ---------------------------- | ------------------- |
| userId    | 用户 ID，路径参数            | 1471298152875       |
| csrfToken | CSRF 令牌，通过请求头传入     | a1B2c3D4e5F6        |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/admin/user/1471298152875/deactivate' \
  -H 'csrfToken: a1B2c3D4e5F6' \
  -H 'Referer: https://fishpi.cn/admin/user/1471298152875' \
  -H 'Cookie: sym-ce=登录凭证'
```

响应：

| Key  | 说明                  | 示例                         |
| ---- | --------------------- | ---------------------------- |
| code | `0` 表示成功          | 0                            |
| msg  | 操作结果              | 账号已永久停用，手机号已清空 |

> 本接口仅支持管理员页面登录态，不支持 `apiKey`。操作不可恢复。

## 职业成长

### 读取当前职业设置

`GET /api/profession/me`

读取当前登录用户的主职业、已累计职业、可选择职业和职业隐私设置。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥，页面登录态可省略 | `YOUR_API_KEY` |

请求示例：

```bash
curl --location 'https://fishpi.cn/api/profession/me?apiKey=YOUR_API_KEY'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 职业设置 | `{...}` |
| - primaryProfessionId | 主职业 ID，未设置时为空 | `1760000000000000000` |
| - progress | 已累计职业 | `[{"professionId":"...","totalExperience":120}]` |
| - availableProfessions | 当前可选择职业 | `[{"displayName":"著述家"}]` |
| - growthGuides | 按职业 ID 分组的已发布升级来源 | `{"1760000000000000000":["article.published"]}` |

### 查询我的职业详情

`GET /api/profession/me/{professionId}/detail`

读取指定职业的当前等级、等级路线、稀有度和贡献统计。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| professionId | 职业 ID，路径参数 | `1760000000000000000` |
| dark | 是否读取深色展示样式，默认 `false` | `true` |
| apiKey | 通用密钥，页面登录态可省略 | `YOUR_API_KEY` |

请求示例：

```bash
curl --location 'https://fishpi.cn/api/profession/me/1760000000000000000/detail?dark=true&apiKey=YOUR_API_KEY'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 职业详情 | `{...}` |
| - profession | 当前职业与等级展示 | `{"displayName":"著述家","totalExperience":120}` |
| - levels | 当前等级方案的等级路线 | `[{"displayName":"入门","requiredExperience":0}]` |
| - rarity | 稀有度与排行 | `{"rank":12,"surpassedPercent":80}` |
| - contributions | 各贡献动作的次数与经验汇总 | `[{"eventCount":12,"experienceSum":120}]` |

### 查询我的职业记录

`GET /api/profession/me/{professionId}/records`

读取指定职业最近 90 天的经验变化记录，按发生时间倒序分页。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| professionId | 职业 ID，路径参数 | `1760000000000000000` |
| beforeOccurredAt | 下一页游标中的发生时间，首次请求省略 | `1760000000000` |
| beforeEffectId | 下一页游标中的记录 ID，首次请求省略 | `1760000000000000001` |
| limit | 返回数量，`1` 至 `100`，默认 `30` | `30` |
| apiKey | 通用密钥，页面登录态可省略 | `YOUR_API_KEY` |

请求示例：

```bash
curl --location 'https://fishpi.cn/api/profession/me/1760000000000000000/records?limit=30&apiKey=YOUR_API_KEY'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 职业记录分页 | `{...}` |
| - records | 职业经验记录 | `[{"experienceDelta":20,"afterExperience":120}]` |
| -- actionCode | 贡献动作编码 | `article.publish` |
| -- sourceLabel | 经验来源说明 | 发布帖子 |
| -- experienceDelta | 本次经验变化，可为负数 | `20` |
| -- beforeExperience | 变化前累计经验 | `100` |
| -- afterExperience | 变化后累计经验 | `120` |
| -- occurredAt | 发生时间，毫秒时间戳 | `1760000000000` |
| - nextCursor | 下一页游标，没有更多记录时省略 | `{"beforeOccurredAt":1760000000000,"beforeEffectId":"..."}` |

### 更新主职业与隐私

`POST /api/profession/me/primary`

设置主职业，其他职业的经验和等级不会清除。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| professionId | 已发布职业 ID | `1760000000000000000` |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/profession/me/primary' \
--header 'Content-Type: application/json' \
--header 'csrfToken: 页面 CSRF Token' \
--header 'Cookie: sym-ce=登录凭证' \
--data-raw '{"professionId":"1760000000000000000"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 更新后的职业设置 | `{...}` |

> `POST /api/profession/me/skip` 使用相同登录和 CSRF 要求，可结束首次选择提示。`POST /api/profession/me/privacy` 请求体为 `preset`，可选值为 `ALL_PUBLIC`、`LEVEL_ONLY`、`PRIMARY_ONLY`、`SELF_ONLY`、`FULLY_HIDDEN` 或 `CUSTOM`。`CUSTOM` 必须同时提交完整的 `moduleVisibility` 对象。

### 结束首次选择提示

`POST /api/profession/me/skip`

请求体为空对象，使用登录态和页面 `csrfToken`。调用后停止首页首次选择弹窗，用户仍可在个人设置中重新打开选择页。

```bash
curl --request POST 'https://fishpi.cn/api/profession/me/skip' \
--header 'Content-Type: application/json' \
--header 'csrfToken: 页面 CSRF Token' \
--header 'Cookie: sym-ce=登录凭证' \
--data '{}'
```

### 更新职业隐私

`POST /api/profession/me/privacy`

请求体：`preset` 可选 `ALL_PUBLIC`、`LEVEL_ONLY`、`PRIMARY_ONLY`、`SELF_ONLY`、`FULLY_HIDDEN`、`CUSTOM`；使用 `CUSTOM` 时必须同时提交完整的 `moduleVisibility` 对象。

```bash
curl --request POST 'https://fishpi.cn/api/profession/me/privacy' \
--header 'Content-Type: application/json' \
--header 'csrfToken: 页面 CSRF Token' \
--header 'Cookie: sym-ce=登录凭证' \
--data-raw '{"preset":"CUSTOM","moduleVisibility":{"professions":true,"experience":false,"contributions":true,"activity":false,"ranking":false}}'
```

### 查询公开职业资料

`GET /api/user/{userName}/profession`

按用户隐私设置读取公开职业资料。完全隐藏或没有任何公开模块时返回 `404`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| userName | 用户名 | `adlered` |
| position | 展示位置：`homeProfile`、`userCard`、`professionPage`、`ranking`、`compactMobile`，默认 `homeProfile` | `professionPage` |
| dark | 是否读取深色展示样式，默认 `false` | `true` |
| apiKey | 通用密钥，页面登录态可省略 | `YOUR_API_KEY` |

请求示例：

```bash
curl --location 'https://fishpi.cn/api/user/adlered/profession?position=professionPage&dark=true'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 经隐私过滤后的职业资料 | `{...}` |
| - primaryProfession | 主职业摘要 | `{"displayName":"著述家","levelCode":"writer.2"}` |
| - professions | 已公开职业列表 | `[{...}]` |
| - majorContributions | 已公开主要贡献 | `[{"actionCode":"experience"}]` |
| - contributionStats | 贡献统计摘要 | `[{"eventCount":12}]` |
| - activityFeed | 最近 90 天职业动态摘要 | `[{"sourceLabel":"发布帖子"}]` |
| - levelHistory | 最近 90 天等级变化 | `[{"afterLevelCode":"writer.2"}]` |
| - rewards | 已发放的等级奖励摘要 | `[{"rewardType":"POINT"}]` |
| - ranking | 各职业排行摘要 | `[{"rank":12,"surpassedPercent":80}]` |

> `LEVEL_ONLY` 仅公开主职业及当前等级，不公开经验值。职业动态会再次检查来源内容是否仍可公开访问；`sourceLabel` 只说明来源类型，不包含内容标题、内部来源事件、请求编号、IP、UA 或密钥。

### 查询职业排行

`GET /api/profession/ranking`

读取指定职业的公开榜单。仅展示已允许当前查看者读取“排行信息”的用户；经验值也遵从各用户的展示范围。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| professionId | 已发布职业 ID；省略时返回首个已发布职业 | `1760000000000000000` |
| dark | 是否读取深色展示样式，默认 `false` | `true` |
| apiKey | 通用密钥，页面登录态可省略 | `YOUR_API_KEY` |

请求示例：

```bash
curl --location 'https://fishpi.cn/api/profession/ranking?professionId=1760000000000000000&apiKey=YOUR_API_KEY'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 榜单数据 | `{...}` |
| - selectedProfession | 当前职业 | `{"displayName":"著述家"}` |
| - professions | 可切换的已发布职业 | `[{"professionId":"...","displayName":"著述家"}]` |
| - entries | 可见榜单条目 | `[{"rank":1,"userName":"adlered"}]` |
| -- rank | 该职业的总排名 | `1` |
| -- userName | 用户名 | `adlered` |
| -- userNickname | 用户昵称 | `阿达` |
| -- userAvatarURL48 | 48 像素头像地址 | `https://...` |
| -- professionName | 职业名称 | `著述家` |
| -- levelName | 当前等级名称 | `成章` |
| -- totalExperience | 仅在经验可见时返回 | `101` |

> 该接口需要登录或 `apiKey`，不返回用户 ID、联系方式、IP、UA、职业流水或内部来源事件。

### 获取自动化编辑元数据

`GET /api/profession/admin/automation/metadata`

读取职业管理界面可用的触发器、字段、条件、计算器和执行器。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Cookie | 管理员登录凭证 | `sym-ce=...` |

请求示例：

```bash
curl --location 'https://fishpi.cn/api/profession/admin/automation/metadata' \
--header 'Cookie: sym-ce=管理员登录凭证'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 自动化编辑元数据 | `{"triggers":[...],"calculatorTypes":[...]}` |

> 内置触发器包含长篇阅读结算、发布帖子、发表评论、发布清风明月、发布复读机内容和聊天室发言。聊天室发言仅采集普通文本消息，条件字段为 `messageId`、`authorUserId`、`messageType`、`contentLength`、`sentAt`；不会写入消息正文、聊天室标识、IP、UA 或原始请求载荷。撤回消息后会同步回退关联效果。

> `actions` 现包含“增加职业经验”“记录贡献”和“发送系统通知”。“记录贡献”会累计职业贡献次数，不改变经验和等级；撤销来源内容后会同步回退。系统通知执行器使用 `notificationContent` 配置正文，支持 `{职业}`、`{等级}`、`{经验}`、`{变化}`、`{奖励}`；`notificationWhen` 可选 `ALWAYS` 或 `LEVEL_UP`。通知跟随职业效果事务发送，不会写入职业经验或奖励记录。

> 此接口仅管理员可访问，不提供职业管理写入能力给金手指或第三方应用。

### 获取职业展示元数据

`GET /api/profession/admin/definition/metadata`

管理员页面读取职业展示位置和可用预设。展示位置包括 `default`、`selectionCard`、`homeProfile`、`userCard`、`professionPage`、`ranking`、`levelUpDialog`、`compactMobile`；颜色、边框、阴影、进度条和升级动画字段由服务端校验。

### 读取完整职业目录

`GET /api/profession/admin/catalog`

读取完整职业目录，返回定义、等级方案和自动化配置。日常列表建议使用分页的 `catalog-summary`，编辑单个职业使用 `catalog-detail/{professionId}`。

### 读取职业库摘要

`GET /api/profession/admin/catalog-summary`

按名称或代号检索职业库摘要。此接口不返回历史版本、等级明细或自动化配置。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Cookie | 管理员登录凭证 | `sym-ce=...` |
| keyword | 可选；职业名称或代号关键词，最多 64 个字符 | `著述` |
| status | 可选；`DRAFT`、`PUBLISHED` 或 `RETIRED` | `PUBLISHED` |
| sort | 可选；`recent`、`manual` 或 `name`，默认 `recent` | `recent` |
| page | 可选；页码，最小 `1`，默认 `1` | `1` |
| pageSize | 可选；每页数量，`1` 至 `50`，默认 `24` | `24` |

请求示例：

```bash
curl --location 'https://fishpi.cn/api/profession/admin/catalog-summary?keyword=%E8%91%97%E8%BF%B0&status=PUBLISHED&sort=recent&page=1&pageSize=24' \
--header 'Cookie: sym-ce=管理员登录凭证'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 职业库分页结果 | `{...}` |
| - items | 职业摘要列表 | `[{"displayName":"著述家","levelCount":12}]` |
| -- defaultPresentationJson | 当前职业展示样式 | `{"imageUrl":"..."}` |
| -- levelCount | 当前等级方案中的等级数量 | `12` |
| -- automationCount | 经验规则数量 | `4` |
| - total | 匹配职业总数 | `18` |
| - page | 当前页码 | `1` |
| - pageSize | 每页数量 | `24` |

### 读取职业管理详情

`GET /api/profession/admin/catalog-detail/{professionId}`

按需读取单个职业的职业版本、等级方案、等级和自动化配置，供编辑和历史查看使用。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Cookie | 管理员登录凭证 | `sym-ce=...` |
| professionId | 职业 ID，路径参数 | `1760000000000000000` |

请求示例：

```bash
curl --location 'https://fishpi.cn/api/profession/admin/catalog-detail/1760000000000000000' \
--header 'Cookie: sym-ce=管理员登录凭证'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 单个职业的完整管理数据 | `{"revisions":[...],"schemes":[...],"automations":[...]}` |
| - operatorNames | 历史记录操作人 ID 与用户名映射 | `{"1630399192600":"adlered"}` |

> 两个接口只接受管理员页面登录态，不支持 `apiKey`，不返回用户职业进度、来源事件、请求编号、IP、UA 或密钥。

### 预览等级方案影响

`GET /api/profession/admin/scheme-impact/{professionId}/{schemeId}`

在启用等级方案前，统计会发生等级变化的用户数量。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Cookie | 管理员登录凭证 | `sym-ce=...` |
| professionId | 职业 ID，路径参数 | `1760000000000000000` |
| schemeId | 等级方案 ID，路径参数 | `1760000000000000001` |

请求示例：

```bash
curl --location 'https://fishpi.cn/api/profession/admin/scheme-impact/1760000000000000000/1760000000000000001' \
--header 'Cookie: sym-ce=管理员登录凭证'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 影响统计 | `{"totalCount":120,"upgradeCount":81,"downgradeCount":3}` |
| - totalCount | 会重新计算等级的用户数 | `120` |
| - upgradeCount | 等级提升的用户数 | `81` |
| - downgradeCount | 等级降低的用户数 | `3` |

### 导出职业配置

`GET /api/profession/admin/config/export`

导出单个或全部职业的定义、等级方案、展示样式、等级奖励和经验规则。导出文件不包含用户职业、经验、贡献、来源事件、奖励发放或排行数据。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Cookie | 管理员登录凭证 | `sym-ce=...` |
| professionId | 可选；指定时仅导出该职业 | `1760000000000000000` |

请求示例：

```bash
curl --location 'https://fishpi.cn/api/profession/admin/config/export?professionId=1760000000000000000' \
--header 'Cookie: sym-ce=管理员登录凭证'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 职业配置文件 | `{...}` |
| - format | 固定为 `fishpi.profession-config` | `fishpi.profession-config` |
| - formatVersion | 文件格式版本 | `1` |
| - scope | `SINGLE` 或 `ALL` | `SINGLE` |
| - professions | 职业配置列表 | `[{"professionCode":"longform_author"}]` |

> 导出的内部编号会被移除；导入时会生成新的编号。

### 导入职业配置

`POST /api/profession/admin/config/import`

导入通过职业配置导出接口生成的文件。系统会先校验整份文件，再在同一事务内写入；任一职业代号已存在时整个导入失败，不会覆盖已有职业。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Cookie | 管理员登录凭证 | `sym-ce=...` |
| csrfToken | 请求头中的页面 CSRF 凭证 | `页面提供的 csrfToken` |
| configJson | 导出文件的完整 JSON 字符串，最大 16 MB | `{"format":"fishpi.profession-config",...}` |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/profession/admin/config/import' \
--header 'Content-Type: application/json' \
--header 'csrfToken: 页面提供的 csrfToken' \
--header 'Cookie: sym-ce=管理员登录凭证' \
--data-raw '{"configJson":"{\\"format\\":\\"fishpi.profession-config\\",\\"formatVersion\\":1,\\"scope\\":\\"SINGLE\\",\\"professions\\":[...]}"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 导入结果 | `{"importedCount":1}` |
| - importedCount | 成功写入的职业数 | `1` |

> 此接口仅管理员页面可用，不支持金手指或第三方应用调用。

### 预检查职业配置

`POST /api/profession/admin/config/precheck`

校验导入文件格式，并列出可导入职业与代号冲突。此接口不写入数据。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Cookie | 管理员登录凭证 | `sym-ce=...` |
| csrfToken | 请求头中的页面 CSRF 凭证 | `页面提供的 csrfToken` |
| configJson | 导出文件的完整 JSON 字符串，最大 16 MB | `{"format":"fishpi.profession-config",...}` |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/profession/admin/config/precheck' \
--header 'Content-Type: application/json' \
--header 'csrfToken: 页面提供的 csrfToken' \
--header 'Cookie: sym-ce=管理员登录凭证' \
--data-raw '{"configJson":"{\\"format\\":\\"fishpi.profession-config\\",\\"formatVersion\\":1,\\"scope\\":\\"SINGLE\\",\\"professions\\":[...]}"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 预检查结果 | `{"valid":true,"ready":[...],"conflicts":[]}` |
| - valid | 文件格式是否有效 | `true` |
| - ready | 可导入的职业代号 | `[{"professionCode":"longform_author"}]` |
| - conflicts | 与现有职业冲突的代号 | `[{"professionCode":"longform_author","reason":"职业代号已存在"}]` |
| - error | `valid=false` 时的格式错误说明 | `职业历史不能为空` |

### 保存职业展示顺序

`POST /api/profession/admin/catalog/order`

按完整职业 ID 列表保存职业展示顺序。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Cookie | 管理员登录凭证 | `sym-ce=...` |
| csrfToken | 请求头中的页面 CSRF 凭证 | `页面提供的 csrfToken` |
| professionIds | 当前全部职业 ID，按展示顺序排列 | `["1760000000000000000"]` |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/profession/admin/catalog/order' \
--header 'Content-Type: application/json' \
--header 'csrfToken: 页面提供的 csrfToken' \
--header 'Cookie: sym-ce=管理员登录凭证' \
--data-raw '{"professionIds":["1760000000000000000","1760000000000000001"]}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 空对象 | `{}` |

> `professionIds` 必须包含当前全部职业且不能重复；职业库在保存期间发生变化时，接口会拒绝写入并要求重新读取。

### 创建职业定义草稿

`POST /api/profession/admin/definition/draft`

请求体：`professionCode`、`displayName`、`shortName`、`description`、`defaultPresentationJson`。接口返回新草稿的 `revisionId`。

### 发布职业定义

`POST /api/profession/admin/definition/publish`

请求体：`{"revisionId":"<revisionId>"}`。发布后职业可被用户选择和累计经验。

### 停用职业定义

`POST /api/profession/admin/definition/retire`

请求体：`{"professionId":"<professionId>"}`。停用保留用户经验、贡献和历史记录，并向受影响用户发送通知。

### 复制职业定义

`POST /api/profession/admin/definition/copy`

请求体：`{"revisionId":"<revisionId>"}`。复制指定历史版本为新草稿。

### 回滚职业定义

`POST /api/profession/admin/definition/rollback`

请求体：`{"professionId":"<professionId>","revisionId":"<revisionId>"}`。回滚不清除用户经验。

### 创建等级方案草稿

`POST /api/profession/admin/scheme/draft`

请求体包含 `professionId`、`professionRevisionId`、`migrationPolicy`、`rewardMigrationPolicy`、`migrationConfigJson` 和 `levels`。每个等级至少包含 `levelCode`、`sortOrder`、`requiredTotalExperience`、`displayName`、`description`、`achievementDescription`、`isTopLevel`，可选 `presentations` 与 `rewards`。

等级奖励类型为 `POINT` 或 `MEDAL`：积分配置为 `{"amount":5000,"memo":"职业升级奖励"}`；勋章配置为 `{"medalId":"<medalId>","durationMillis":0,"data":""}`。

### 发布等级方案

`POST /api/profession/admin/scheme/publish`

请求体：`{"schemeId":"<schemeId>"}`。降低经验门槛导致跨越多个等级时，系统按等级顺序分别执行一次幂等奖励和升级通知，不回放历史来源事件。

### 停用等级方案

`POST /api/profession/admin/scheme/retire`

请求体：`{"schemeId":"<schemeId>"}`。

### 复制等级方案

`POST /api/profession/admin/scheme/copy`

请求体：`{"schemeId":"<schemeId>"}`。

### 回滚等级方案

`POST /api/profession/admin/scheme/rollback`

请求体：`{"professionId":"<professionId>","schemeId":"<schemeId>"}`。

### 创建自动化草稿

`POST /api/profession/admin/automation/draft`

请求体：`professionId`、`automationCode`、`configurationJson`。配置必须包含一个已注册 `trigger`、可选的 `condition` 和至少一个 `actions` 执行器。执行器类型为 `profession.experience.adjust`、`profession.contribution.record` 或 `profession.system.notify`。

### 发布自动化

`POST /api/profession/admin/automation/publish`

请求体：`{"revisionId":"<revisionId>"}`。

### 停用自动化

`POST /api/profession/admin/automation/retire`

请求体：`{"automationId":"<automationId>"}`。

### 复制自动化

`POST /api/profession/admin/automation/copy`

请求体：`{"revisionId":"<revisionId>"}`。

### 回滚自动化

`POST /api/profession/admin/automation/rollback`

请求体：`{"automationId":"<automationId>","revisionId":"<revisionId>"}`。

### 测试自动化

`POST /api/profession/admin/automation/test`

只计算规则，不写入经验、奖励、通知或来源事件。请求体：`{"configurationJson":"{...}","payloadJson":"{...}"}`。

## 金手指

### 注意注意注意注意注意:warning:️️️️️️️️️️️️️️️️️️️️️️️️

金手指接口用于将摸鱼派某些敏感接口提供给第三方接入，主要用于向摸鱼派上传游戏成绩、验证用户数据、给用户颁发勋章等功能。

这些接口**普通用户**无法使用！**必须有专用的密钥才可以使用**！如没有金手指使用权限，请跳过阅读此部分内容（申请金手指密钥请联系 @adlered ）。

### 上传摸鱼大闯关关卡数据

`POST /api/games/mofish/score`

请求：

| Key           | 说明                         | 示例          |
| ------------- | ---------------------------- | ------------- |
| goldFingerKey | `game`类型的金手指密钥       | 省略          |
| userName      | 用户在摸鱼派的用户名         | adlered       |
| stage         | 关卡数                       | 10            |
| time          | 通过此关时间（毫秒级时间戳） | 1654486459786 |

### 查询用户最近登录的IP地址

`POST /user/query/latest-login-ip`

请求：

| Key           | 说明                    | 示例    |
| ------------- | ----------------------- | ------- |
| goldFingerKey | `query`类型的金手指密钥 | 省略    |
| userName      | 用户在摸鱼派的用户名    | adlered |

### 添加勋章（已废弃）

此接口已废弃，请对接下方的新版勋章系统（Medal v2）

`POST /user/edit/give-metal`

请求：

| Key           | 说明                                                                       | 示例                                            |
| ------------- | -------------------------------------------------------------------------- | ----------------------------------------------- |
| goldFingerKey | `metal`类型的金手指密钥                                                    | 省略                                            |
| userName      | 用户在摸鱼派的用户名                                                       | adlered                                         |
| name          | 勋章名称                                                                   | 测试勋章                                        |
| description   | 勋章描述                                                                   | XXX活动奖励勋章                                 |
| attr          | 勋章属性（请严格按照示例填写属性，backcolor为背景色，fontcolor为文字颜色） | url=[图标URL]&backcolor=0000ff&fontcolor=ffffff |
| data          | 勋章数据（如果要发带变量的勋章，请按照下方的要求来）                       | 请留空                                          |

POST /user/edit/give-metal

`其它参数省略...`

`description`  累计捐助{var1}元，用户名{var2}，我是{var3}

`data`            50;adlered;管理员

最终效果：累计捐助50元，用户名adlered，我是管理员

### 移除勋章（已废弃）

此接口已废弃，请对接下方的新版勋章系统（Medal v2）

`POST /user/edit/remove-metal`

请求：

| Key           | 说明                    | 示例     |
| ------------- | ----------------------- | -------- |
| goldFingerKey | `metal`类型的金手指密钥 | 省略     |
| userName      | 用户在摸鱼派的用户名    | adlered  |
| name          | 勋章名称                | 测试勋章 |

### 移除勋章（通过userId）（已废弃）

此接口已废弃，请对接下方的新版勋章系统（Medal v2）

`POST /user/edit/remove-metal-by-user-id`

请求：

| Key           | 说明                    | 示例          |
| ------------- | ----------------------- | ------------- |
| goldFingerKey | `metal`类型的金手指密钥 | 省略          |
| userId        | 用户在摸鱼派的oId       | 1636516552191 |
| name          | 勋章名称                | 测试勋章      |

### 查询用户背包

`POST /user/query/items`

请求：

| Key           | 说明                   | 示例    |
| ------------- | ---------------------- | ------- |
| goldFingerKey | `item`类型的金手指密钥 | 省略    |
| userName      | 用户在摸鱼派的用户名   | adlered |

### 调整用户背包

`POST /user/edit/items`

请求：

| Key           | 说明                                                                                                            | 示例         |
| ------------- | --------------------------------------------------------------------------------------------------------------- | ------------ |
| goldFingerKey | `item`类型的金手指密钥                                                                                          | 省略         |
| userName      | 用户在摸鱼派的用户名                                                                                            | adlered      |
| item          | 物品名称                                                                                                        | checkin2days |
| sum           | 物品数量，填写正数（如10）则发放指定数量物品，填写负数（如-10）则从背包中扣除指定数量物品，如物品数量不足则归零 | 10           |

### 调整用户积分

`POST /user/edit/points`

由可信积分平台调整指定用户积分。

请求：

| Key           | 说明                                                                                 | 示例                              |
| ------------- | ------------------------------------------------------------------------------------ | --------------------------------- |
| goldFingerKey | `point` 类型的金手指密钥                                                             | 省略                              |
| userName      | 用户名，最大 64 字                                                                   | adlered                           |
| point         | 非零 32 位整数；正数增加积分，负数扣除积分                                           | 100                               |
| memo          | 交易备注，1～255 字                                                                  | 新手任务奖励                      |
| sourceAppId   | 来源应用编号，1～64 字；应用来源字段必须同时提供                                     | app_0123456789abcdef0123456789abcd |
| sourceAppName | 来源应用名称，1～40 字；由可信积分平台从应用资料中读取                               | 小冰游戏                          |
| sourceScene   | 来源场景：`payment`、`point_issue`、`refund`、`transfer`                             | point_issue                       |
| requestId     | 幂等请求编号，1～128 字；相同请求重复提交不会再次操作积分                            | order:123:point_issue_credit      |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/user/edit/points' \
--header 'Content-Type: application/json' \
--data-raw '{
  "goldFingerKey": "省略",
  "userName": "adlered",
  "point": 100,
  "memo": "新手任务奖励",
  "sourceAppId": "app_0123456789abcdef0123456789abcd",
  "sourceAppName": "小冰游戏",
  "sourceScene": "point_issue",
  "requestId": "order:123:point_issue_credit"
}'
```

响应：

| Key | 说明                          | 示例                              |
| --- | ----------------------------- | --------------------------------- |
| code | 0 为成功，-1 为失败          | 0                                 |
| msg | 操作结果，成功时包含交易 oId | 转账成功，交易oId为：1760000000000 |

> 四个应用来源字段全部不传时保持旧版行为。任一来源字段存在时必须全部提供；应用来源请求会生成独立的应用积分流水，不会将正常扣款记录为违规扣除。
>
> 相同 `requestId` 且请求参数一致时返回首次交易 oId；相同 `requestId` 携带不同用户、积分、备注或应用来源时返回“请求编号已使用”。
>
> `sourceAppId`、内部请求编号和请求摘要不会出现在 OAuth 积分记录响应中；仅公开 `sourceAppName` 与 `sourceScene`。

### 写入职业经验

`POST /api/gold-finger/profession/experience`

向指定用户的已发布职业写入经验。请求先写入可审计来源事件，再异步应用职业效果；同一来源应用和请求编号只会生效一次。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| goldFingerKey | `profession` 类型金手指密钥 | 省略 |
| sourceAppId | 来源应用编号，1～64 位小写字母、数字、`.`、`_`、`-` | `book-reader` |
| requestId | 幂等请求编号，1～128 位 | `read:20260801:42` |
| sourceScene | 来源场景，最多 64 字 | `chapter_complete` |
| userName | 用户名 | `adlered` |
| professionId | 已发布职业 ID | `1760000000000000000` |
| experienceDelta | 正整数经验值，不得超过 `profession.external.maxExperience` | `20` |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/gold-finger/profession/experience' \
--header 'Content-Type: application/json' \
--data-raw '{
  "goldFingerKey":"省略",
  "sourceAppId":"book-reader",
  "requestId":"read:20260801:42",
  "sourceScene":"chapter_complete",
  "userName":"adlered",
  "professionId":"1760000000000000000",
  "experienceDelta":20
}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示已受理 | `0` |
| data | 受理信息 | `{"eventId":"...","accepted":true,"duplicate":false}` |
| - duplicate | 相同请求是否已受理 | `false` |

> 每个 `sourceAppId` 的一分钟请求上限由 `profession.external.requestsPerMinute` 配置。金手指密钥只允许放在 JSON 请求体，不要写入查询参数或前端代码。

### 查询用户公开职业资料

`POST /api/gold-finger/profession/query`

查询用户已公开的职业资料，返回字段和公开职业资料接口一致。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| goldFingerKey | `profession` 类型金手指密钥 | 省略 |
| userName | 用户名 | `adlered` |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/gold-finger/profession/query' \
--header 'Content-Type: application/json' \
--data-raw '{"goldFingerKey":"省略","userName":"adlered"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | `0` 表示成功 | `0` |
| data | 经隐私过滤后的职业资料 | `{...}` |

> 用户未公开职业资料时请求失败，不返回隐藏职业的存在状态、经验或贡献。

### 获取用户活跃度

`POST /user/liveness`

获取指定用户的活跃度

请求：

| Key           | 说明                       | 示例    |
| ------------- | -------------------------- | ------- |
| goldFingerKey | `liveness`类型的金手指密钥 | 省略    |
| userName      | 用户名                     | adlered |

响应：

| Key      | 说明   | 示例  |
| -------- | ------ | ----- |
| liveness | 活跃度 | 80.20 |

### 领取指定用户的昨日活跃奖励

`POST /activity/yesterday-liveness-reward-api`

为指定用户领取他的昨日活跃奖励

请求：

| Key           | 说明                       | 示例    |
| ------------- | -------------------------- | ------- |
| goldFingerKey | `liveness`类型的金手指密钥 | 省略    |
| userName      | 用户名                     | adlered |

响应：

| Key | 说明           | 示例 |
| --- | -------------- | ---- |
| sum | 领取了多少积分 | 80   |

### 发送指定用户系统通知

`POST /user/edit/notification`

给指定用户发送“系统通知”，适合扩展审核通过、人工处理结果通知等场景。

请求：

| Key           | 说明                           | 示例                                                          |
| ------------- | ------------------------------ | ------------------------------------------------------------- |
| goldFingerKey | `notification`类型的金手指密钥 | 省略                                                          |
| userName      | 用户名                         | adlered                                                       |
| notification  | 通知内容，支持markdown插入链接 | 请查看[这篇帖子](https://fishpi.cn/article/123)\n有问题再回复 |

## 新版勋章系统（Medal v2）

新版勋章系统全部为 `POST` JSON 请求。

**注意：** 管理侧接口需要管理员权限。普通用户可用 `apiKey` 调用用户侧接口。
**金手指：** 新版勋章系统也支持金手指方式鉴权（分为 `medal-admin-read`类型金手指，用于读取勋章信息操作类API，开放申请；`medal-admin-write`类型金手指，用于勋章的写入、修改、删除操作类API），该密钥**必须申请才有**。

### 用户侧 API（需要登录）

> 路径以 `/api/medal/my/*`、`/api/medal/user/*` 开头

#### 用户侧：读取当前登录用户的所有勋章列表

`POST /api/medal/my/list`

请求:

| Key    | 说明     | 示例 |
| ------ | -------- | ---- |
| apiKey | 通用密钥 | 省略 |

响应：

| Key  | 说明                          | 示例      |
| ---- | ----------------------------- | --------- |
| code | 0成功/-1失败                  | 0         |
| msg  | 错误信息                      |           |
| data | 勋章列表（已做描述-变量渲染） | `[ ... ]` |

---

#### 用户侧：调整单个勋章顺序（上移/下移）

`POST /api/medal/my/reorder`

请求:

| Key       | 说明                | 示例   |
| --------- | ------------------- | ------ |
| apiKey    | 通用密钥            | 省略   |
| medalId   | 勋章ID（medal\_id） | `"0"`  |
| direction | `"up"` 或 `"down"`  | `"up"` |

响应：通用 `code/msg`

---

#### 用户侧：调整当前登录用户隐藏/显示指定勋章

`POST /api/medal/my/display`

请求:

| Key     | 说明                | 示例   |
| ------- | ------------------- | ------ |
| apiKey  | 通用密钥            | 省略   |
| medalId | 勋章ID（medal\_id） | `"0"`  |
| display | 是否展示            | `true` |

响应：通用 `code/msg`

---

#### 用户侧：读取指定用户当前展示中的勋章列表（用于主页展示）

`POST /api/medal/user/list`

请求（二选一）：

| Key      | 说明             | 示例              |
| -------- | ---------------- | ----------------- |
| apiKey   | 通用密钥         | 省略              |
| userId   | 用户 oId（可选） | `"1630512345670"` |
| userName | 用户名（可选）   | `"adlered"`       |

响应：

| Key  | 说明                                                                     | 示例      |
| ---- | ------------------------------------------------------------------------ | --------- |
| code | 0成功/-1失败                                                             | 0         |
| msg  | 错误信息                                                                 |           |
| data | 勋章列表（仅 display=true 且未过期，按 display\_order 排序，已渲染变量） | `[ ... ]` |

### 管理侧 API（需要管理员/金手指权限）

> 路径统一以 `/api/medal/admin/*` 开头

#### 管理侧：分页读取全部勋章列表

` POST /api/medal/admin/list`

请求:

| Key                   | 说明                                           | 示例 |
| --------------------- | ---------------------------------------------- | ---- |
| apiKey或goldFingerKey | 管理员的apikey或`medal-admin-read`类型的金手指 | 省略 |
| page                  | 页码，从1开始                                  | 1    |
| pageSize              | 每页条数                                       | 20   |

请求示例：

<pre node="[object Object]"><div class="CodeBlockWrapper-gyXzFg FcwJh code-block"><div class="CodeHeader-kOSpft jgkqBC"><BASH></div><div class="StickyWrapper-eCgLUr cqxvZD"><div class="Box-hYgUQy Stack-cgmguR HStack-dsNZQB ToolbarWrapper-bbbaaS ennsCW kMXibF iUMlyp cVIYwQ code-toolbar"></div></div><div class="SplitViewWrapper-beRizf kNYYkF split-view-wrapper"><div class="shiki houston code-viewer source-view shiki-dark" tabindex="0"><div class="ScrollContainer-kgAkoh lfEYGP shiki-scroller"><div class="shiki-list"><div><div data-index="0"><div class="line"><span class="line-content"><span>curl</span><span> </span><span>--location</span><span> </span><span>--request</span><span> </span><span>POST</span><span> </span><span>'https://fishpi.cn/api/medal/admin/list'</span><span> \</span></span></div></div><div data-index="1"><div class="line"><span class="line-content"><span>--header </span><span>'Content-Type: application/json'</span><span> \</span></span></div></div><div data-index="2"><div class="line"><span class="line-content"><span>--header </span><span>'User-Agent: Mozilla/5.0 ... Chrome/69.0.3497.100 Safari/537.36'</span><span> \</span></span></div></div><div data-index="3"><div class="line"><span class="line-content"><span>--data-raw </span><span>'{</span></span></div></div><div data-index="4"><div class="line"><span class="line-content"><span>  "apiKey":"<Key>",</span></span></div></div><div data-index="5"><div class="line"><span class="line-content"><span>  "page":1,</span></span></div></div><div data-index="6"><div class="line"><span class="line-content"><span>  "pageSize":20</span></span></div></div><div data-index="7"><div class="line"><span class="line-content"><span>}'</span></span></div></div></div></div></div></div></div></div></pre>

响应：

| Key  | 说明         | 示例      |
| ---- | ------------ | --------- |
| code | 0成功/-1失败 | 0         |
| msg  | 错误信息     |           |
| data | 勋章列表     | `[ ... ]` |

---

#### 管理侧：按关键字搜索勋章列表

`POST /api/medal/admin/search`

说明：在 `medal_id、medal_name、medal_description` 中模糊匹配。

请求:

| Key                   | 说明                                               | 示例         |
| --------------------- | -------------------------------------------------- | ------------ |
| apiKey或goldFingerKey | 通用密钥（管理员）或`medal-admin-read`类型的金手指 | 省略         |
| keyword               | 关键词                                             | `"Operator"` |

响应：

| Key  | 说明         | 示例      |
| ---- | ------------ | --------- |
| code | 0成功/-1失败 | 0         |
| msg  | 错误信息     |           |
| data | 勋章列表     | `[ ... ]` |

---

#### 管理侧：读取指定勋章详细信息

`POST /api/medal/admin/detail`

请求:

| Key                   | 说明                                               | 示例  |
| --------------------- | -------------------------------------------------- | ----- |
| apiKey或goldFingerKey | 通用密钥（管理员）或`medal-admin-read`类型的金手指 | 省略  |
| medalId               | 勋章ID（medal\_id）                                | `"0"` |

响应：

| Key  | 说明         | 示例      |
| ---- | ------------ | --------- |
| code | 0成功/-1失败 | 0         |
| msg  | 错误信息     |           |
| data | 勋章信息     | `{ ... }` |

---

#### 管理侧：删除指定勋章

`POST /api/medal/admin/delete`

请求:

| Key                   | 说明                                                | 示例  |
| --------------------- | --------------------------------------------------- | ----- |
| apiKey或goldFingerKey | 通用密钥（管理员）或`metal-admin-write`类型的金手指 | 省略  |
| medalId               | 勋章ID（medal\_id）                                 | `"0"` |

响应：通用 `code/msg`

---

#### 管理侧：编辑指定勋章

`POST /api/medal/admin/edit`

请求:

| Key                   | 说明                                                               | 示例                                          |
| --------------------- | ------------------------------------------------------------------ | --------------------------------------------- |
| apiKey或goldFingerKey | 通用密钥（管理员）或`metal-admin-write`类型的金手指                | 省略                                          |
| medalId               | 勋章ID（medal\_id）                                                | `"0"`                                         |
| name                  | 勋章名称                                                           | `"测试勋章"`                                  |
| type                  | 勋章类型（默认普通，支持普通、精良、稀有、史诗、传说、神话、限定） | `"普通"`                                      |
| description           | 勋章描述（支持变量）                                               | `"累计捐助{var1}元"`                          |
| attr                  | 勋章属性串                                                         | `"url=...&backcolor=0000ff&fontcolor=ffffff"` |

响应：通用 `code/msg`

---

#### 管理侧：新建勋章

`POST /api/medal/admin/create`

请求:

| Key                   | 说明                                                               | 示例                                          |
| --------------------- | ------------------------------------------------------------------ | --------------------------------------------- |
| apiKey或goldFingerKey | 通用密钥（管理员）或`metal-admin-write`类型的金手指                | 省略                                          |
| name                  | 勋章名称                                                           | `"测试勋章"`                                  |
| type                  | 勋章类型（默认普通，支持普通、精良、稀有、史诗、传说、神话、限定） | `"普通"`                                      |
| description           | 勋章描述                                                           | `"XXX活动奖励勋章"`                           |
| attr                  | 勋章属性串                                                         | `"url=...&backcolor=0000ff&fontcolor=ffffff"` |

响应：

| Key  | 说明         | 示例            |
| ---- | ------------ | --------------- |
| code | 0成功/-1失败 | 0               |
| msg  | 文案         | `"created"`     |
| data | 创建结果     | `{"oId":"..."}` |

---

#### 管理侧：给指定用户发指定勋章

`POST /api/medal/admin/grant`

请求:

| Key                   | 说明                                                | 示例                  |
| --------------------- | --------------------------------------------------- | --------------------- |
| apiKey或goldFingerKey | 通用密钥（管理员）或`metal-admin-write`类型的金手指 | 省略                  |
| goldFingerKey         | `metal`金手指密钥（可选）                           | 省略                  |
| userId                | 用户 oId                                            | `"1630512345670"`     |
| medalId               | 勋章ID（medal\_id）                                 | `"0"`                 |
| expireTime            | 过期时间戳（毫秒），0=永久                          | `0`                   |
| data                  | 勋章数据（用于渲染变量）                            | `"50;adlered;管理员"` |

响应：通用 `code/msg`

---

#### 管理侧：查询用户是否持有指定勋章

`POST /api/medal/admin/holds`

说明：`userId` 适用于单个用户，`userIds` 适用于用户列表；两个字段可以同时传入，重复用户 ID 会去重并保持首次出现的顺序。单次最多查询 100 个用户。已过期记录返回 `held=false`、`expired=true`。

请求：

| Key                   | 说明                                               | 示例                                  |
| --------------------- | -------------------------------------------------- | ------------------------------------- |
| apiKey或goldFingerKey | 通用密钥（管理员）或`medal-admin-read`类型的金手指 | 省略                                  |
| userId                | 单个用户 oId，与 `userIds` 至少填写一个            | `"1630512345670"`                     |
| userIds               | 用户 oId 列表，与 `userId` 至少填写一个            | `["1630512345670", "1630512345671"]` |
| medalId               | 勋章 ID（medal\_id）                               | `"0"`                                 |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/medal/admin/holds' \
--header 'Content-Type: application/json' \
--data-raw '{
  "apiKey":"<Key>",
  "userIds":["1630512345670","1630512345671"],
  "medalId":"0"
}'
```

响应：

| Key          | 说明                    | 示例              |
| ------------ | ----------------------- | ----------------- |
| code         | 0成功/-1失败            | 0                 |
| msg          | 错误信息                | `""`              |
| data         | 持有状态列表            | `[...]`           |
| - userId     | 用户 oId                | `"1630512345670"` |
| - held       | 当前是否持有            | true              |
| - expired    | 是否存在已过期记录      | false             |
| - expireTime | 过期时间戳，0 表示永久  | 0                 |
| - permanent  | 是否永久有效            | true              |
| - data       | 勋章数据                | `"50;adlered"`    |

---

#### 管理侧：批量发放指定勋章

`POST /api/medal/admin/grant-batch`

说明：`userId` 适用于单个用户，`userIds` 适用于用户列表；两个字段可以同时传入，重复用户 ID 会去重。单次最多处理 100 个用户。全部用户在同一事务中写入；勋章不存在、任一用户不存在或任一写入失败时，整批操作失败。用户已经持有该勋章时更新 `expireTime` 和 `data`。

请求：

| Key                   | 说明                                                | 示例                                  |
| --------------------- | --------------------------------------------------- | ------------------------------------- |
| apiKey或goldFingerKey | 通用密钥（管理员）或`medal-admin-write`类型的金手指 | 省略                                  |
| userId                | 单个用户 oId，与 `userIds` 至少填写一个             | `"1630512345670"`                     |
| userIds               | 用户 oId 列表，与 `userId` 至少填写一个             | `["1630512345670", "1630512345671"]` |
| medalId               | 勋章 ID（medal\_id）                                | `"0"`                                 |
| expireTime            | 未来的毫秒时间戳，0 表示永久                        | 0                                     |
| data                  | 勋章数据，最多 128 个字符                           | `"50;adlered;管理员"`                 |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/medal/admin/grant-batch' \
--header 'Content-Type: application/json' \
--data-raw '{
  "apiKey":"<Key>",
  "userIds":["1630512345670","1630512345671"],
  "medalId":"0",
  "expireTime":0,
  "data":"50;adlered;管理员"
}'
```

响应：

| Key                 | 说明             | 示例               |
| ------------------- | ---------------- | ------------------ |
| code                | 0成功/-1失败     | 0                  |
| msg                 | 错误信息         | `""`               |
| data                | 发放结果         | `{...}`            |
| - grantedCount      | 实际处理用户数量 | 2                  |

---

#### 管理侧：给指定用户移除指定勋章

`POST /api/medal/admin/revoke`

请求:

| Key                   | 说明                                                | 示例              |
| --------------------- | --------------------------------------------------- | ----------------- |
| apiKey或goldFingerKey | 通用密钥（管理员）或`metal-admin-write`类型的金手指 | 省略              |
| userId                | 用户 oId                                            | `"1630512345670"` |
| medalId               | 勋章ID（medal\_id）                                 | `"0"`             |

响应：通用 `code/msg`

---

#### 管理侧：读取指定勋章拥有的用户和拥有总数（分页）

`POST /api/medal/admin/owners`

说明：接口会自动清理已过期的 `user_medal` 记录。

请求:

| Key                   | 说明                                               | 示例  |
| --------------------- | -------------------------------------------------- | ----- |
| apiKey或goldFingerKey | 通用密钥（管理员）或`medal-admin-read`类型的金手指 | 省略  |
| medalId               | 勋章ID（medal\_id）                                | `"0"` |
| page                  | 页码，从1开始                                      | 1     |
| pageSize              | 每页条数                                           | 20    |

响应：

| Key               | 说明               | 示例        |
| ----------------- | ------------------ | ----------- |
| code              | 0成功/-1失败       | 0           |
| msg               | 错误信息           |             |
| data              | 数据               | `{...}`     |
| - total           | 总数（已过滤过期） | 123         |
| - items           | 拥有者列表         | `[ ... ]`   |
| -- user\_id       | 用户 oId           | `"..."`     |
| -- medal\_id      | 勋章ID             | `"0"`       |
| -- expire\_time   | 过期时间           | 0           |
| -- display        | 是否展示           | true        |
| -- display\_order | 顺序               | 0           |
| -- data           | 勋章数据           | `""`        |
| -- userName       | 用户名（附带）     | `"adlered"` |

### description / data（变量渲染说明补充）

`medal_description（勋章描述）` 支持 `{var1} {var2} ...` 零个或多个变量占位符；

`data（用户勋章数据）` 使用英文分号 `;` 分隔，每一段依次替换 `{var1}`、`{var2}`……

示例：

`description`：累计捐助{var1}元，用户名{var2}，我是{var3}
`data`：50;adlered;管理员
最终效果：累计捐助50元，用户名adlered，我是管理员

### 勋章生成页（SVG）

> 该接口为页面渲染，用于“显示某个勋章”的展示页（非 JSON API）。

#### 通过勋章 ID 显示勋章

`GET /gen?id={medalId}`

请求：

| Key     | 说明                                         | 示例 |
| ------- | -------------------------------------------- | ---- |
| medalId | 勋章 ID（medal\_id）                         | 0    |
| apiKey  | 填写当前登录用户的apiKey，否则勋章将返回空白 | 省略 |

示例：

`https://fishpi.cn/gen?id=0`

---

#### 通过勋章名称显示勋章

`GET /gen?name={medalName}`

请求：

| Key       | 说明                                         | 示例     |
| --------- | -------------------------------------------- | -------- |
| medalName | 勋章名称（medal\_name）                      | Operator |
| apiKey    | 填写当前登录用户的apiKey，否则勋章将返回空白 | 省略     |

示例：

`https://fishpi.cn/gen?name=Operator`

# 模块：贴emoji

20260421更新功能：你可以在帖子、评论、聊天室消息里面贴emoji，该功能同时支持第三方客户端，相关的API全部在此分类中。

### Emoji Reaction 返回结构说明

以下涉及 reaction 的读取接口和写入接口，都会用到同一套返回结构。

响应中的 **reactionSummary** 每一项结构如下：

| Key           | 说明                         | 示例                           |
| ------------- | ---------------------------- | ------------------------------ |
| value         | 表情值                       | **thumbsup**                   |
| emoji         | 表情字符                     | **:+1:**                       |
| count         | 该表情当前总数               | **1**                          |
| selected      | 当前登录用户是否已选中该表情 | **true**                       |
| users         | 点过该表情的用户显示名列表   | **["阿达 (adlered)"]**         |
| userDetails   | 点过该表情的用户详情列表     | **[{...}]**                    |
| - userName    | 用户名                       | **adlered**                    |
| - displayName | 显示名                       | **阿达 (adlered)**             |
| - avatarURL   | 头像地址                     | **https://file.fishpi.cn/...** |

**currentUserReaction** 表示当前登录用户自己选中的表情值，没有则为空字符串 **""**。

注意：再次发送相同的 **value** 表示取消该表情；发送不同的 **value** 表示切换到新的表情。

### 帖子：获取指定帖子（补充）

**GET /api/article/{articleId}**

接口本身不变，响应中的 **data.article** 新增以下字段：

| Key                 | 说明                                                   | 示例         |
| ------------------- | ------------------------------------------------------ | ------------ |
| reactionSummary     | 帖子当前的表情汇总                                     | **[ ... ]**  |
| currentUserReaction | 当前登录用户在该帖子上已选择的表情值，没有则为空字符串 | **thumbsup** |

### 帖子：获取帖子的评论列表（补充）

**GET /api/comment/{articleId}?p=<page>**

接口本身不变，响应中的每个评论对象新增以下字段：

| Key                 | 说明                                                   | 示例        |
| ------------------- | ------------------------------------------------------ | ----------- |
| reactionSummary     | 该评论当前的表情汇总                                   | **[ ... ]** |
| currentUserReaction | 当前登录用户在该评论上已选择的表情值，没有则为空字符串 | **heart**   |

### 帖子：给帖子添加表情

**POST /article/reaction**

给指定帖子添加一个表情 reaction。

请求：

| Key       | 说明                               | 示例                                 |
| --------- | ---------------------------------- | ------------------------------------ |
| apiKey    | 通用密钥                           | **YOUR_API_KEY** |
| articleId | 帖子的 oId                         | **1636516552191**                    |
| groupType | reaction 分组，目前固定为**emoji** | **emoji**                            |
| value     | 表情值                             | **thumbsup**                         |

响应：

| Key                   | 说明                                           | 示例                 |
| --------------------- | ---------------------------------------------- | -------------------- |
| code                  | 为**0** 则成功，为 **-1** 则失败               | **0**                |
| msg                   | 错误信息                                       | **暂不支持该 emoji** |
| data                  | 操作结果                                       | **{ ... }**          |
| - targetId            | 目标对象 oId                                   | **1636516552191**    |
| - targetType          | 目标类型                                       | **article**          |
| - groupType           | reaction 分组                                  | **emoji**            |
| - currentUserReaction | 当前登录用户最终选中的表情值，没有则为空字符串 | **thumbsup**         |
| - summary             | 当前帖子最新的表情汇总                         | **[ ... ]**          |

### 帖子：给评论添加表情

**POST /comment/reaction**

给指定评论添加一个表情 reaction。

请求：

| Key       | 说明                               | 示例                                 |
| --------- | ---------------------------------- | ------------------------------------ |
| apiKey    | 通用密钥                           | **YOUR_API_KEY** |
| commentId | 评论的 oId                         | **1645002736006**                    |
| groupType | reaction 分组，目前固定为**emoji** | **emoji**                            |
| value     | 表情值                             | **heart**                            |

响应：

| Key                   | 说明                                           | 示例              |
| --------------------- | ---------------------------------------------- | ----------------- |
| code                  | 为**0** 则成功，为 **-1** 则失败               | **0**             |
| msg                   | 错误信息                                       | **评论不存在**    |
| data                  | 操作结果                                       | **{ ... }**       |
| - targetId            | 目标对象 oId                                   | **1645002736006** |
| - targetType          | 目标类型                                       | **comment**       |
| - groupType           | reaction 分组                                  | **emoji**         |
| - currentUserReaction | 当前登录用户最终选中的表情值，没有则为空字符串 | **heart**         |
| - summary             | 当前评论最新的表情汇总                         | **[ ... ]**       |

### 聊天室：聊天历史消息（补充）

**GET /chat-room/more?page=<page>&type=<type>**

接口本身不变，响应中的每条聊天消息对象新增以下字段：

| Key                 | 说明                                                       | 示例        |
| ------------------- | ---------------------------------------------------------- | ----------- |
| reactionSummary     | 该聊天消息当前的表情汇总                                   | **[ ... ]** |
| currentUserReaction | 当前登录用户在该聊天消息上已选择的表情值，没有则为空字符串 | **hundred** |

### 聊天室：给聊天室消息添加表情

**POST /chat-room/reaction**

给指定聊天室消息添加一个表情 reaction。

请求：

| Key       | 说明                               | 示例                                 |
| --------- | ---------------------------------- | ------------------------------------ |
| apiKey    | 通用密钥                           | **YOUR_API_KEY** |
| oId       | 聊天消息的 oId                     | **1776756136925**                    |
| groupType | reaction 分组，目前固定为**emoji** | **emoji**                            |
| value     | 表情值                             | **fire**                             |

响应：

| Key                   | 说明                                           | 示例               |
| --------------------- | ---------------------------------------------- | ------------------ |
| code                  | 为**0** 则成功，为 **-1** 则失败               | **0**              |
| msg                   | 错误信息                                       | **聊天消息不存在** |
| data                  | 操作结果                                       | **{ ... }**        |
| - targetId            | 目标对象 oId                                   | **1776756136925**  |
| - targetType          | 目标类型                                       | **chat**           |
| - groupType           | reaction 分组                                  | **emoji**          |
| - currentUserReaction | 当前登录用户最终选中的表情值，没有则为空字符串 | **fire**           |
| - summary             | 当前聊天消息最新的表情汇总                     | **[ ... ]**        |

### 可用 emoji 值

当前服务端支持以下 **value**：

| value         | emoji                  | 含义               |
| ------------- | ---------------------- | ------------------ |
| `thumbsup`    | :+1:                   | 赞                 |
| `plus`        | :heavy_plus_sign:1️⃣ | +1 / 同意 / 我也是 |
| `thumbsdown`  | :-1:                   | 反对 / 不喜欢      |
| `check`       | :white_check_mark:     | 对 / 正确 / 通过   |
| `cross`       | :x:                    | 错 / 否 / 不通过   |
| `star`        | :star:️               | 收藏 / 亮眼 / 很棒 |
| `heart`       | :heart:️️            | 喜欢 / 支持        |
| `fire`        | :fire:                 | 热门 / 很强        |
| `party`       | :tada:                 | 庆祝               |
| `laugh`       | :joy:                  | 好笑               |
| `wow`         | :open_mouth:           | 惊讶               |
| `clap`        | :clap:                 | 鼓掌 / 认可        |
| `hundred`     | :100:                  | 满分 / 非常认可    |
| `rocket`      | :rocket:               | 起飞 / 冲          |
| `salute`      | :vulcan_salute:        | 致意 / 打招呼      |
| `handshake`   | :handshake:            | 握手 / 达成一致    |
| `raisedhands` | :raised_hands:         | 赞成 / 欢呼        |
| `mindblown`   | :exploding_head:       | 震惊 / 太强了      |
| `thinking`    | :thinking:             | 思考 / 存疑        |
| `eyes`        | :eyes:                 | 围观 / 关注        |
| `cry`         | :cry:                  | 难过 / 共情        |
| `angry`       | :rage:                 | 生气 / 不满        |
| `pray`        | :pray:                 | 祈祷 / 拜托 / 感谢 |
| `brokenheart` | :broken_heart:         | 破防 / 难受        |
| `heartonfire` | :heart:️️‍:fire:    | 上头 / 强烈喜欢    |
| `skull`       | :skull:                | 笑死 / 绝了        |
| `clown`       | :clown_face:           | 小丑 / 离谱        |
| `poop`        | :hankey:               | 拉胯 / 吐槽        |

### 聊天室 WebSocket 新增消息类型

已连接聊天室 WebSocket 的客户端，除了现有消息外，还会收到一个新的消息类型：

**type = chatReaction**

响应：

| Key           | 说明                                               | 示例              |
| ------------- | -------------------------------------------------- | ----------------- |
| type          | 固定为**chatReaction**                             | **chatReaction**  |
| oId           | 聊天消息的 oId                                     | **1776756136925** |
| targetType    | 目标类型                                           | **chat**          |
| groupType     | reaction 分组                                      | **emoji**         |
| summary       | 该聊天消息最新的表情汇总                           | **[ ... ]**       |
| actorUserId   | 本次触发操作的用户 id                              | **1776745143000** |
| actorReaction | 该用户本次操作后最终选中的表情值，没有则为空字符串 | **fire**          |

### 帖子页 **/article-channel** 新增消息类型

如果客户端已经接入文章页 WebSocket 频道 **/article-channel**，还需要额外处理以下两种增量消息。

第一种：

**type = articleReaction**

响应：

| Key           | 说明                                               | 示例                |
| ------------- | -------------------------------------------------- | ------------------- |
| type          | 固定为**articleReaction**                          | **articleReaction** |
| articleId     | 帖子的 oId                                         | **1636516552191**   |
| targetId      | 目标对象 oId                                       | **1636516552191**   |
| targetType    | 目标类型                                           | **article**         |
| groupType     | reaction 分组                                      | **emoji**           |
| summary       | 当前帖子最新的表情汇总                             | **[ ... ]**         |
| actorUserId   | 本次触发操作的用户 id                              | **1776745143000**   |
| actorReaction | 该用户本次操作后最终选中的表情值，没有则为空字符串 | **thumbsup**        |

第二种：

**type = commentReaction**

响应：

| Key           | 说明                                               | 示例                |
| ------------- | -------------------------------------------------- | ------------------- |
| type          | 固定为**commentReaction**                          | **commentReaction** |
| articleId     | 所属帖子的 oId                                     | **1636516552191**   |
| commentId     | 评论的 oId                                         | **1645002736006**   |
| targetId      | 目标对象 oId                                       | **1645002736006**   |
| targetType    | 目标类型                                           | **comment**         |
| groupType     | reaction 分组                                      | **emoji**           |
| summary       | 当前评论最新的表情汇总                             | **[ ... ]**         |
| actorUserId   | 本次触发操作的用户 id                              | **1776745143000**   |
| actorReaction | 该用户本次操作后最终选中的表情值，没有则为空字符串 | **heart**           |

未适配的旧客户端可以直接忽略 **chatReaction**、**articleReaction**、**commentReaction** 这三种新增消息类型，不会影响现有消息收发。

## 鱼游

### 获取鱼游列表

`GET /activities`

展示通过审核的鱼游，按点赞数与踩数之和降序排列；点赞数用于并列时排序，踩数只记录不在公开页面显示。

### 投稿鱼游

`POST /api/fish-games`

提交一条待审核鱼游。投稿需登录，不需要提交 OAuth 说明材料；管理员人工审核后公开。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| fishGameName | 名称，1-80 字符 | `元素纪元` |
| fishGameDescription | 描述，1-1000 字符 | `元素主题放置游戏` |
| fishGameUrl | 目标网址，HTTPS | `https://example.com/game` |
| fishGameIconUrl | 图标网址，HTTPS | `https://example.com/icon.png` |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/fish-games' \
  -H 'Content-Type: application/json' \
  -d '{"apiKey":"YOUR_API_KEY","fishGameName":"元素纪元","fishGameDescription":"元素主题放置游戏","fishGameUrl":"https://example.com/game","fishGameIconUrl":"https://example.com/icon.png"}'
```

响应：返回 `code`、`msg` 和新建鱼游 `data`，新投稿状态为待审核。

### 申请编辑鱼游

`POST /api/fish-games/{id}/edit`

作者提交与投稿相同字段，审核通过后替换公开内容。已有待审核修改时不能重复提交。

请求：字段同 `POST /api/fish-games`。仅鱼游作者可以提交。

### 鱼游投票

`POST /api/fish-games/{id}/vote`

请求：`fishGameVoteValue` 取 `like` 或 `dislike`。每位用户每个鱼游保留一个投票，可再次点击取消或切换；踩会记录并参与排名，但公开页面不展示踩数量。

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/fish-games/1770000000000/vote' \
  -H 'Content-Type: application/json' \
  -d '{"apiKey":"YOUR_API_KEY","fishGameVoteValue":"like"}'
```

### 鱼游评论

`GET /api/fish-games/{id}/comments` 获取评论；`POST /api/fish-games/{id}/comments` 新增评论，请求字段 `content`，最多 500 字符。

响应：评论接口返回 `comments` 数组；评论内容为纯文本并经过清洗。

### 管理鱼游

`GET /admin/fish-games`、`GET /api/admin/fish-games`、`POST /api/admin/fish-games`、`GET /api/admin/fish-games/export`、`POST /api/admin/fish-games/import`、`POST /api/admin/fish-games/{id}/review`、`POST /api/admin/fish-games/{id}/edit`

仅管理员可用。管理员可手动添加、编辑资料、审核通过/拒绝、停用投稿，并导入导出 JSON。

`GET /api/admin/fish-games`

请求：管理员登录态，无请求体。

响应：`data.games` 为全部记录，包含待审核与已停用状态。

`POST /api/admin/fish-games`

请求：字段同 `POST /api/fish-games`。管理员添加的记录直接进入已审核状态。

响应：返回已创建鱼游对象。

`GET /api/admin/fish-games/export`

请求：管理员登录态，无请求体。

响应：

```json
{"version":1,"games":[{"fishGameName":"元素纪元","fishGameDescription":"元素主题放置游戏","fishGameUrl":"https://example.com/game","fishGameIconUrl":"https://example.com/icon.png","fishGameStatus":1}]}
```

`POST /api/admin/fish-games/import`

请求：提交导出格式 JSON，`games` 数组最多 200 条。导入记录直接按已审核处理。

```bash
curl -X POST 'https://fishpi.cn/api/admin/fish-games/import?apiKey=YOUR_API_KEY' \
  -H 'Content-Type: application/json' \
  --data-binary @activities-legacy.json
```

响应：返回 `data.imported`（新增数量）和 `data.skipped`（因目标网址重复而跳过的数量）。

`POST /api/admin/fish-games/{id}/review`

请求：`fishGameStatus` 取 `1`（通过）、`2`（拒绝）或 `3`（停用）。

响应：返回更新后的状态。

`POST /api/admin/fish-games/{id}/edit`

请求：字段同 `POST /api/fish-games`，保存后沿用当前审核状态。

响应：返回更新后的鱼游对象。

> 投稿不要求提交 OAuth 资格材料，由管理员人工审核目标网站。接口变更后请同步更新网站 API 文档文章。
