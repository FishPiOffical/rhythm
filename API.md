# 摸鱼派 API 文档

## API v3

`/api/v3` 提供版本化 Web API，也支持已登记的第三方应用通过 OAuth 授权接入。除下文 OAuth 授权接口外，响应使用统一信封：

```json
{ "code": 0, "msg": "", "data": {} }
```

成功时 `code` 为 `0`。失败时 `data` 为 `null`，并结合 HTTP 状态码与 `code`、`msg` 处理错误。资源 ID 均为字符串，时间字段默认使用毫秒时间戳；自然日等其他格式以各接口字段说明为准。

> 支持Idempotency-Key的写接口遇到连接中断或服务端错误时，应使用原键和原请求体重试，不要为同一次操作换新键。

> v3 JSON业务写入请使用标准UTF-8 JSON对象，不接受单引号、重复字段、损坏JSON或无效UTF-8；非法JSON返回400，正文超限返回413，错误媒体类型返回415。只有明确允许省略正文的接口可提交真正空体；正常空对象及业务字段仍按各接口约定校验。

### 查询账号验证组件

`GET /api/v3/auth/captcha-options`

获取账号流程使用的验证组件标识，用于初始化极验4组件。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥（可选） | xxx |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/auth/captcha-options'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 验证组件信息 | {} |
| - provider | 组件类型，固定 geetest4 | geetest4 |
| - captchaId | 初始化组件使用的公开标识 | your-captcha-id |

> 完成组件交互后，将返回的 lot_number、captcha_output、pass_token、gen_time 四个字符串组成 JSON，再序列化为 captcha 字符串传给短信申请接口。组件配置不可用时返回 HTTP 503。

### 查询注册选项

`GET /api/v3/auth/registration-options`

查询当前注册状态、邀请码要求及注册表单的用户名长度和角色选项。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥（可选） | xxx |
| referral | 可选，邀请人用户名，去除首尾空白后最多64个字符 | username |

请求示例：

```bash
curl --get 'https://fishpi.cn/api/v3/auth/registration-options' \
  --data-urlencode 'referral=username'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 注册选项 | {} |
| - mode | open 开放、invitation 邀请注册、closed 关闭 | invitation |
| - enabled | 是否开放注册 | true |
| - inviteCodeRequired | 是否需要邀请码；关闭注册时为 false | true |
| - referralAccepted | 提供的邀请人是否具有邀请链接资格；关闭注册时为 false | false |
| - username | 用户名长度要求 | {} |
| -- minLength | 最小长度 | 1 |
| -- maxLength | 最大长度 | 64 |
| - appRoles | 可选注册角色 | [] |
| -- value | 角色值：0 程序员、1 设计师 | 0 |
| -- label | 角色名称 | 程序员 |

> 邀请注册模式下，有效邀请链接可免填邀请码；关闭注册时邀请链接也不能注册。邀请人不存在或没有邀请资格时 referralAccepted 为 false，用户名格式不合法返回 HTTP 422。查询结果供表单展示，最终以提交时的状态为准。

### 申请注册短信

`POST /api/v3/auth/registration/request`

完成账号验证组件交互后申请注册短信，收到验证码后调用完成注册接口。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| userName | 用户名，1–64位英文字母、数字或连字符，不允许保留名称 | Fresh-Name |
| userPhone | 手机号，11位数字且以1开头 | 13800138000 |
| captcha | 极验4结果的JSON字符串，最多4096个UTF-8字节；四个字段均为非空字符串 | {"lot_number":"...","captcha_output":"...","pass_token":"...","gen_time":"..."} |
| invitecode | 可选，16位英文字母或数字；邀请注册且没有有效邀请链接时必填 | AbCdEfGh12345678 |
| referral | 可选，已激活的其他用户的用户名，最多64字符 | username |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/auth/registration/request' \
  --header 'Content-Type: application/json' \
  --data '{"userName":"Fresh-Name","userPhone":"13800138000","captcha":"{\"lot_number\":\"...\",\"captcha_output\":\"...\",\"pass_token\":\"...\",\"gen_time\":\"...\"}","invitecode":"AbCdEfGh12345678","referral":"username"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 申请结果 | {} |
| - accepted | 申请已受理，不表示短信已经到达 | true |

> 请求为JSON，最多8192字节，只接受表中字段，不接受查询参数。开放注册、邀请链接和邀请码三种方式均须提交极验结果。开放注册时忽略邀请码；邀请注册时，具有邀请链接资格的邀请人可免填邀请码，显式填写的邀请码仍须有效。显式提供的 referral 须为已激活的其他用户，是否免填邀请码仍取决于邀请链接资格。
>
> 验证码有效期一天；邀请码为该手机号预留一天，同手机号重发可续期，其他手机号在预留到期前不能使用。完成注册时请沿用申请时的邀请人；用户名或邀请信息变化后重新获取验证码。
>
> 注册关闭返回HTTP 403；字段、极验、邀请人或邀请码无效返回422；用户名已占用、手机号不可用、邀请码已被预留或注册信息变化返回409；请求过于频繁返回429；暂时无法受理返回503。发送后如果注册状态发生变化，可能收到无法用于完成注册的短信，请以接口结果为准。本接口不自动登录，也不支持 Idempotency-Key；请勿在超时后自动反复申请短信。

### 完成注册

`POST /api/v3/auth/registration/complete`

为已申请注册短信的手机号设置密码和角色，激活账号。成功后使用登录接口获取访问令牌。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| userPhone | 注册短信对应的当前手机号，11位数字 | 13800138000 |
| code | 注册短信验证码，6位数字 | 123456 |
| userPassword | 密码的32位小写MD5 | e10adc3949ba59abbe56e057f20f883e |
| userAppRole | 可选，0 程序员、1 设计师，默认0；使用JSON数字 | 0 |
| mbti | 可选，最多32字符；沿用资料页格式，转为大写，不符合格式时保存为空 | INFJ-T |
| referral | 可选，邀请人用户名，去除首尾空白后最多64字符 | username |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/auth/registration/complete' \
  --header 'Content-Type: application/json' \
  --data '{"userPhone":"13800138000","code":"123456","userPassword":"e10adc3949ba59abbe56e057f20f883e","userAppRole":0,"mbti":"INFJ-T","referral":"username"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 注册结果 | {} |
| - userId | 新注册用户ID，字符串 | 1630000000000 |
| - registered | 注册完成 | true |

> 参数仅放在 JSON 请求体中，不接受查询参数、userId 或其他未列出字段，请求体最多4096字节。referral 只能使用用户名允许的字母、数字和连字符，邀请人不存在时没有邀请链接奖励；完成时请使用申请短信时的邀请信息，名称或邀请信息变化后请重新获取验证码；MBTI支持16种类型及可选的-A、-T后缀。注册完成保留原有初始积分、邀请链接及邀请码奖励规则。已激活账号不能重复注册，验证码成功后不能重复使用。
>
> 注册关闭返回 HTTP 403；验证码无效、过期、用途不符或与注册资料不符返回422；用户名已被占用或注册信息变化返回409；请求过于频繁返回429；暂时无法完成返回503。本接口不自动登录，也不提供 Idempotency-Key 回执。响应丢失时先尝试登录，避免把已激活账号当成新注册再次提交。

### 申请找回密码短信

`POST /api/v3/auth/password-reset/request`

完成账号验证组件交互后，申请找回密码短信。手机号符合账号使用条件时发送，随后调用完成密码重置接口。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| userPhone | 当前手机号，11位数字 | 13800138000 |
| captcha | 极验4结果的JSON字符串，最多4096个UTF-8字节；四个字段均为非空字符串 | {"lot_number":"...","captcha_output":"...","pass_token":"...","gen_time":"..."} |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/auth/password-reset/request' \
  --header 'Content-Type: application/json' \
  --data '{"userPhone":"13800138000","captcha":"{\"lot_number\":\"...\",\"captcha_output\":\"...\",\"pass_token\":\"...\",\"gen_time\":\"...\"}"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 申请结果 | {} |
| - accepted | 申请已受理，不表示手机号一定对应可用账号或短信已到达 | true |

> 请求体最多8192字节，只接受表中字段，不接受查询参数。验证失败或字段不合法返回 HTTP 422；请求过于频繁返回429；申请暂不可用返回503。受理结果不包含账号可用性或服务商发送结果；请收到短信后再提交6位验证码，验证码有效期一天。本接口不支持 Idempotency-Key；每次申请独立处理，请勿在超时后自动反复申请。

### 完成密码重置

`POST /api/v3/auth/password-reset/complete`

使用已收到的找回密码短信验证码设置新密码。成功后请重新登录；已签发的 v3 访问令牌、刷新令牌及尚未完成的授权需要重新获取。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| userPhone | 接收验证码的当前手机号，11位数字 | 13800138000 |
| code | 找回密码短信验证码，6位数字 | 123456 |
| userPassword | 新密码的32位小写MD5 | e10adc3949ba59abbe56e057f20f883e |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/auth/password-reset/complete' \
  --header 'Content-Type: application/json' \
  --data '{"userPhone":"13800138000","code":"123456","userPassword":"e10adc3949ba59abbe56e057f20f883e"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 重置结果 | {} |
| - changed | 密码已修改 | true |

> 参数放在 JSON 请求体中，不接受查询参数；请求体最多4096字节，仅接受表中字段。验证码一次有效，错误、过期或用途不符返回 HTTP 422。请求过于频繁返回 HTTP 429，暂时无法完成返回 HTTP 503。本接口不自动登录，也不提供 Idempotency-Key 回执；响应丢失时可尝试使用新密码登录，或重新获取验证码。

### 登录

> 网站客户端统一使用本节登录、刷新和退出接口；旧 `POST /__fp/page-auth` 转换入口已停止提供，已有集成请迁移。

`POST /api/v3/auth/login`

用于 website_v3 登录并获取短期访问令牌。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| nameOrEmail | 用户名或邮箱 | username |
| userPassword | 密码的 32 位小写 MD5 | e10adc3949ba59abbe56e057f20f883e |
| mfaCode | 两步验证码，未启用时可不传 | 123456 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/auth/login' \
  --header 'Content-Type: application/json' \
  --data '{"nameOrEmail":"username","userPassword":"e10adc3949ba59abbe56e057f20f883e"}'
```

响应：

| Key | 说明 |
| --- | --- |
| data.accessToken | Bearer 访问令牌 |
| data.tokenType | 固定为 `Bearer` |
| data.expiresIn | 访问令牌有效秒数 |
| data.refreshExpiresIn | 刷新登录状态有效秒数 |
| data.scope | 已授予权限 |

> 启用两步认证的账号首次请求会返回 `mfa_required`，携带 `mfaCode` 后再次登录。

> 密码登录成功后记录最近登录时间；登录失败、刷新登录状态和普通读取不会更新该时间。

> 请求体最多4096字节，使用标准UTF-8 JSON对象，仅接受表中的字符串字段；非法JSON返回400，未知字段或非字符串值返回422，超限413，错误媒体类型415。

### 刷新登录状态

`POST /api/v3/auth/refresh`

用于同域 website_v3 恢复登录状态。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| 无 | 浏览器自动携带登录 Cookie | - |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/auth/refresh'
```

响应：

| Key | 说明 |
| --- | --- |
| data.accessToken | 新的 Bearer 访问令牌 |
| data.expiresIn | 访问令牌有效秒数 |
| data.refreshExpiresIn | 刷新登录状态有效秒数 |

> 可省略正文；提交正文时仅接受 `Content-Type: application/json` 的空对象 `{}`，最多4096字节。非法JSON返回400，非空对象字段返回422，超限413，错误媒体类型415。

> 刷新成功后浏览器使用新的登录 Cookie。请勿重复使用刷新前的 Cookie；重复使用会使本次登录失效，需要重新登录。 账号已停用、未激活或不存在时返回 HTTP 401，不能继续刷新。

### 退出登录

`POST /api/v3/auth/logout`

结束当前浏览器的 v3 登录状态。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| 无 | 浏览器自动携带登录 Cookie | - |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/auth/logout'
```

响应：

| Key | 说明 |
| --- | --- |
| data | 空对象 |

> 服务端完成退出处理后，本次登录下的访问令牌不再可用；其他独立登录不受影响。

> 可省略正文；提交正文时仅接受 `Content-Type: application/json` 的空对象 `{}`，最多4096字节。非法JSON返回400，非空对象字段返回422，超限413，错误媒体类型415。

### 当前用户

`GET /api/v3/me`

读取当前登录用户的基础资料。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| data.id | 用户 ID |
| data.userName | 用户名 |
| data.userNickname | 昵称 |
| data.userAvatarURL | 头像地址 |
| data.userIntro | 个人简介 |
| data.userURL | 个人主页链接 |
| data.userPoint | 积分 |
| data.userGuideStep | 新手引导进度 |
| data.isAdmin | 当前账号为管理员且使用网站第一方访问令牌时为 true |
| data.userRole / data.userRoleId | 角色显示名 / 角色 ID，仅供展示与识别 |
| data.userNo / data.userAppRole | 用户编号 / 社区角色 |
| data.userCity | 公开城市，位置未公开时为空字符串 |
| data.userOnlineFlag / data.onlineMinute | 在线标记 / 累计在线分钟数 |


> 返回当前账号资料；账号不再有效或已删除时，原访问令牌也会返回 HTTP 401。

### 最新专栏

`GET /api/v3/columns?p=1&size=12`

读取有当前可见章节的公开专栏，按可见章节最近更新时间倒序排列。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥（可选） | xxx |
| Authorization | Bearer访问令牌（可选），使用令牌时需要content.read | Bearer TOKEN |
| p | 页码，从1开始 | 1 |
| size | 每页数量，默认12，最大50 | 12 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/columns?p=1&size=12'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表结果 |
| - list | 专栏列表 |
| -- id / columnId | 专栏ID，两个字段值相同 |
| -- title / coverURL | 标题 / 封面地址 |
| -- articleCount | 当前可见章节数量 |
| -- updatedAt | 可见章节最近更新时间，毫秒时间戳 |
| -- chapters | 最近两个可见章节 |
| --- id / title / chapterNo | 文章ID / 标题 / 原章节序号 |
| --- permalink / createdAt / preview | 文章链接 / 创建时间（毫秒）/ 文本摘要 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 可见专栏数 / 总页数 |

> 仅收录当前可读、可列出的非匿名长文章及有效作者。专栏没有可见章节时不进入列表；数量、最近章节和排序按当前可见范围计算，可能与此前结果不同。游客不包含禁止访客阅读的章节。

### 热门专栏

`GET /api/v3/columns/hot?p=1&size=12`

按当前可见章节数量倒序排列，同数量时按可见章节最近更新时间排列。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥（可选） | xxx |
| Authorization | Bearer访问令牌（可选），使用令牌时需要content.read | Bearer TOKEN |
| p | 页码，从1开始 | 1 |
| size | 每页数量，默认12，最大50 | 12 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/columns/hot?p=1&size=12'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表结果，与最新专栏相同 |
| - list | 专栏列表，字段同最新专栏 |
| - pagination | p、size、total、pageCount |

> 可见范围、章节数量及最近章节规则与最新专栏相同。

### 专栏详情

`GET /api/v3/columns/{columnId}`

读取公开专栏和当前可见的章节目录。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥（可选） | xxx |
| Authorization | Bearer访问令牌（可选），使用令牌时需要content.read | Bearer TOKEN |
| columnId | 专栏ID | 2026101600000000001 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/columns/2026101600000000001'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 详情结果 |
| - column | 专栏资料，字段同最新专栏 |
| - chapters | 按原章节序号升序排列的可见目录 |
| -- id / title / chapterNo | 文章ID / 标题 / 原章节序号 |
| -- permalink / createdAt | 文章链接 / 创建时间（毫秒） |

> 不存在、不可用或没有当前可见章节时返回HTTP 404。章节被隐藏后原序号可能不连续，不重新编号；articleCount只计算当前可见章节。

### 专栏章节

`GET /api/v3/columns/{columnId}/articles?p=1&size=20`

分页读取当前可见章节，保留原章节序号并按序号升序排列。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥（可选） | xxx |
| Authorization | Bearer访问令牌（可选），使用令牌时需要content.read | Bearer TOKEN |
| columnId | 专栏ID | 2026101600000000001 |
| p | 页码，从1开始 | 1 |
| size | 每页数量，默认20，最大50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/columns/2026101600000000001/articles?p=1&size=20'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 目录结果 |
| - list | 可见章节列表 |
| -- id / title / chapterNo | 文章ID / 标题 / 原章节序号 |
| -- permalink / createdAt | 文章链接 / 创建时间（毫秒） |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 可见章节数量 / 总页数 |

> 可见范围与专栏详情相同；没有当前可见章节时返回HTTP 404。读取暂不可用时返回503，不能将失败当作空目录。


### 长篇章节导航

`GET /api/v3/articles/{articleId}/column`

读取文章当前可见的所属专栏、章号及前后章节。章节目录使用现有专栏章节分页接口。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥（可选） | xxx |
| Authorization | Bearer访问令牌（可选），使用令牌时需要content.read | Bearer TOKEN |
| articleId | 文章ID | 1770000000001 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/articles/1770000000001/column'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 当前章节导航 |
| - articleId | 请求的文章ID |
| - column | 专栏摘要；没有可见关联时为null |
| -- id / title / coverURL | 专栏ID、名称、封面地址 |
| -- articleCount / updatedAt | 当前可见章节数、更新时间（毫秒） |
| - chapterNo | 当前原章节号；没有关联时为null |
| - previous / next | 前一章 / 后一章；不存在时为null |
| -- id / title / chapterNo | 文章ID、标题、原章节号 |
| -- permalink / createdAt | 文章链接、创建时间（毫秒） |

> 不存在或不可读的文章返回HTTP 404。可读但没有可见专栏关联、未列出或非长篇文章返回column=null，chapterNo、previous、next也均为null。只按当前可见章节定位前后章，不重新编号；此响应不包含完整目录或文章正文。读取暂不可用时返回503。

### 发布文章

`POST /api/v3/articles`

发布普通帖、讨论帖、同城广播、问答或长篇文章。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.write | Bearer TOKEN |
| Idempotency-Key | 本次发布的唯一请求键 | article-publish-20261017-001 |
| title | 标题，必填，最多 255 字符 | 我的第一篇文章 |
| content | Markdown 正文，必填，4–1024000 字符 | 大家好，这是我的介绍。 |
| tags | 逗号分隔的标签，最多 255 字符，按平台标签规则整理 | 新人报道,日常 |
| type | 默认 0；0 普通、1 讨论、2 同城广播、5 问答、6 长篇 | 0 |
| anonymous | 是否匿名发表，布尔值，默认 false | false |
| notifyFollowers | 是否通知关注者，布尔值，默认 false | false |
| showInList | 是否展示在文章列表，0 否、1 是，默认 1 | 1 |
| commentable | 是否允许评论，布尔值，默认 true | true |
| statement | 声明，默认 0；0 无、1 AI、2 剧透、3 虚构 | 0 |
| goodPost | 是否标记为奖励帖，布尔值，默认 false；新人报道帖不可开启 | false |
| rewardPoint | 打赏阅读价格，非负整数，默认 0 表示关闭 | 0 |
| rewardContent | 打赏正文，最多 102400 字符；启用时至少 4 字符，关闭时留空 | |
| qnaOfferPoint | 问答悬赏积分，非负整数，默认 0；问答至少 20 | 0 |
| columnId | 本人专栏 ID，仅长篇使用；留空可通过 columnTitle 新建 | 1770000000000 |
| columnTitle | 新专栏标题，最多 64 字符，仅长篇使用 | 我的专栏 |
| chapterNo | 正整数的字符串，最大 2147483647；不传时自动分配 | 1 |
| columnCoverURL | 专栏封面 HTTP/HTTPS 地址或站内路径，最多 1024 字符 | /images/logo.png |
| draftId | 可选的本人草稿 ID；发布成功后删除该草稿 | 1770000000001 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/articles' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: article-publish-20261017-001' \
  --data '{"title":"我的第一篇文章","content":"大家好，这是我的介绍。","tags":"新人报道,日常","type":0}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 发布结果 |
| - id | 文章 ID |
| - permalink | 文章站内链接，例如 /article/1770000000002 |

> 需要满足手机绑定、首帖标签、发帖间隔、周报及积分等平台规则；正文与打赏正文需通过内容审核。思绪新增仍关闭。长篇不提供专栏 ID 和标题时不关联专栏。
>
> 相同请求键和内容重试返回原文章；同键不同内容返回 HTTP 409。提供的草稿不存在或不属于当前用户时返回 HTTP 404，整次发布失败；成功时草稿与发布一并完成。
>
> 缺少登录凭据返回 HTTP 401，缺少权限、未绑定手机或发布思绪返回 HTTP 403；字段、审核或发布条件不满足返回 HTTP 422，操作过于频繁返回 HTTP 429。

### 本人文章编辑源码

`GET /api/v3/me/articles/{articleId}/source`

读取本人已发布文章的原始内容和编辑选项，用于回填编辑器。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.read | Bearer TOKEN |
| articleId | 本人文章 ID，路径参数 | 1770000000002 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/articles/1770000000002/source' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 编辑源码及选项 |
| - id | 文章 ID |
| - title / tags | 原标题 / 逗号分隔标签 |
| - content | 原始 Markdown 正文；思绪为原始回放记录串 |
| - type | 文章类型 |
| - rewardContent / rewardPoint | 本人打赏正文源码 / 打赏价格 |
| - qnaOfferPoint | 悬赏积分 |
| - commentable | 是否允许评论，布尔值 |
| - showInList | 是否在列表展示，0 否、1 是 |
| - statement | 声明，0 无、1 AI、2 剧透、3 虚构 |
| - columnId / columnTitle | 所属本人有效专栏 ID / 标题；无有效专栏时为空串 |
| - columnCoverURL / chapterNo | 专栏封面 / 章节号字符串；无有效专栏时为空串 |
| - anonymous | 是否匿名，布尔值；只读 |
| - status | 状态，0 正常、1 不可用、2 锁定；只读 |
| - editorType | 原编辑器类型；只读 |
| - createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳；只读 |
| - permalink | 文章站内链接；只读 |

> 不存在或不属于本人的文章统一返回 HTTP 404。本人仍可读取锁定文章源码，读取成功不代表允许编辑；编辑时仍须满足权限和文章状态要求。
>
> content 与 rewardContent 为未渲染源码，适合填充编辑器；展示时需按格式渲染。调用编辑接口时仅提交需要修改的可写字段，不要把整个响应原样提交。

### 编辑本人文章

`PUT /api/v3/articles/{articleId}`

部分更新本人文章，至少提交一个字段；未提交的持久化字段和章节关系保持原值。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.write | Bearer TOKEN |
| Idempotency-Key | 本次编辑的唯一请求键 | article-edit-20261017-001 |
| articleId | 本人文章 ID，路径参数 | 1770000000002 |
| title | 标题，最多 255 字符；周报标题不可修改 | 更新后的标题 |
| content | Markdown 正文，4–1024000 字符；历史思绪使用原回放记录串 | 更新后的正文内容 |
| tags | 逗号分隔标签，最多 255 字符 | 日常,分享 |
| type | 0 普通、1 讨论、2 同城广播、5 问答、6 长篇；3 仅用于原有思绪 | 0 |
| rewardPoint | 非负打赏价格；传 0 会保留已启用的价格和正文，不会关闭打赏 | 10 |
| rewardContent | 新打赏正文，最多 102400 字符；启用时至少 4 字符 | 付费阅读正文 |
| qnaOfferPoint | 悬赏积分，只能增加；问答至少 20 | 30 |
| commentable | 是否允许评论，布尔值 | true |
| showInList | 是否展示在列表，0 否、1 是 | 1 |
| statement | 声明，0 无、1 AI、2 剧透、3 虚构 | 0 |
| notifyFollowers | 本次是否通知关注者，布尔值，默认 false | false |
| columnId | 本人专栏 ID；空串且不提供新专栏标题时取消关联 | 1770000000000 |
| columnTitle | 新专栏标题，最多 64 字符，仅 columnId 为空时使用 | 新专栏 |
| chapterNo | 正整数的字符串，最大 2147483647；变更专栏且不传时自动分配 | 2 |
| columnCoverURL | 封面 HTTP/HTTPS 地址或站内路径，最多 1024 字符 | /images/logo.png |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/articles/1770000000002' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: article-edit-20261017-001' \
  --data '{"title":"更新后的标题","content":"更新后的正文内容"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 编辑结果 |
| - id | 文章 ID |
| - permalink | 文章站内链接 |

> 作者、匿名状态、奖励帖标记及草稿 ID 不支持在此接口修改。编辑费用、五分钟规则、周报标题和悬赏规则沿用平台行为；正文与打赏正文需通过审核。
>
> 历史思绪未提交 content 时保留记录；提供 content 时必须保留原分隔记录格式。其他类型不能改成思绪，原思绪改为其他类型时必须同时提供正文。
>
> 不存在或不属于本人的文章返回 HTTP 404，锁定文章或缺权限返回 HTTP 403；字段、审核或编辑条件不满足返回 HTTP 422。相同请求键重试不重复编辑或扣款，重放不会覆盖后续编辑；同键不同内容返回 HTTP 409。长篇正文的段落标识无法唯一对应时同样返回 HTTP 409，编辑及关联变更一并取消。

### 删除本人文章

`DELETE /api/v3/articles/{articleId}`

删除满足平台删除条件的本人文章。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.write | Bearer TOKEN |
| Idempotency-Key | 本次删除的唯一请求键 | article-remove-20261017-001 |
| articleId | 本人文章 ID，路径参数 | 1770000000002 |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/articles/1770000000002' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: article-remove-20261017-001'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 删除结果 |
| - id | 已删除的文章 ID |

> 文章发布满 24 小时后才可删除，周报不可删除；有评论、订阅、收藏、赞踩、打赏或感谢的文章不可删除。
>
> 不存在或不属于本人的文章返回 HTTP 404，锁定文章或缺权限返回 HTTP 403，不满足删除条件返回 HTTP 422。相同请求键重试返回原删除结果；已删除后使用新键请求返回 HTTP 404。
>
> 删除文章时清理其 emoji reaction，并保留其他文章、评论、用户和标签的投票及奖励记录，同时处理可确认关联的通知；其他消息保留，请求和响应字段保持不变。

### 作者付费置顶

`POST /api/v3/articles/{articleId}/stick`

付费刷新本人有效且可读文章的置顶时间。每个新幂等键按现有置顶费用收费，目前为100积分；与置顶时间一起成功或失败。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer访问令牌，需要content.write、activity.write | Bearer TOKEN |
| Idempotency-Key | 幂等键，同键同文章重试返回原结果 | stick-1645002736006-1 |
| articleId | URL参数，文章ID | 1645002736006 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/articles/1645002736006/stick' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: stick-1645002736006-1' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示，成功时code为0 | 0 |
| data | 本次操作结果 | |
| - id | 文章ID | 1645002736006 |
| - stuckAt | 本次保存的置顶时间，毫秒时间戳 | 1792800000000 |
| - paidPoints | 本次支付积分 | 100 |

> 请求体为空对象，不接收作者、费用或置顶时间。余额不足或管理员永久置顶返回409；非本人、不存在、无效或锁定文章返回404。不设额外数量上限，置顶期限沿站点现有规则。新键会再次收费并刷新时间；同键返回历史结果，不保证文章目前仍在置顶，也不会恢复后来已更改、过期或删除的文章；同键换文章返回409。

### 文章列表

`GET /api/v3/articles`

分页读取文章摘要。`type=recent` 为普通文章列表，`type=long` 为长篇列表，`type=qna` 为问答，`type=perfect` 为优选（含优选长篇）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |
| sort | latest（默认）、active（最新回复）、hot（热度）、comments（评论数）、month-comments（近一个自然月内按置顶优先、评论数排序） | latest |
| type | recent（默认）、long、qna、perfect | recent |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/articles?p=1&size=20&sort=latest&type=recent'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示，成功时 code 为 0 |
| data | 列表数据 |
| - list | 文章摘要数组 |
| -- id | 文章 ID |
| -- title | 标题（文本） |
| -- preview | 摘要（文本），思绪帖为空 |
| -- tags | 逗号分隔的标签 |
| -- type | 文章类型，与旧接口 articleType 一致 |
| -- anonymous | 是否匿名发表 |
| -- perfect | 是否为优选文章，布尔值 |
| -- author | 公开作者资料 |
| --- id | 用户 ID，匿名发表时为空 |
| --- userName / userNickname | 用户名 / 昵称，匿名发表时显示匿名用户 |
| --- userAvatarURL | 头像地址，匿名发表时为空 |
| -- permalink | 文章链接 |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| -- commentCount / viewCount | 评论数 / 浏览数 |
| -- stick | 当前置顶状态 |
| --- active / permanent | 是否仍有效 / 是否管理员永久置顶 |
| --- expiresAt | 到期毫秒时间戳；未置顶为0，永久置顶为null |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> type=recent的所有排序均先排列仍有效的置顶，管理员永久置顶最先，再按原排序排列；已过期记录不再因旧置顶时间优先。列表仅返回可列出的文章，不包含讨论帖。普通文章列表不包含长篇；month-comments 的时间门槛为当前服务端时间减一个自然月，按文章ID计龄，筛选后再统计与分页；超出总页数返回空数组。非法分页和筛选参数返回 HTTP 422。

### 文章详情

`GET /api/v3/articles/{articleId}`

读取文章正文及当前阅读者可查看的打赏内容。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| articleId | 文章 ID | 1636516552191 |
| includeInteractions | 可选，登录后传 true 同时读取文章互动状态 | true |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/articles/1636516552191?includeInteractions=true' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 文章详情，包含文章列表的摘要字段及以下字段 |
| - content | 正文源内容；Markdown 或思绪记录字符串 |
| - contentFormat | markdown 或 thought |
| - contentHtml | 正文 HTML；思绪帖为空，需要由客户端播放 content 中的记录 |
| - commentable | 当前是否允许发布或编辑评论，布尔值；历史状态无法识别时为 null |
| - statement | 创作声明：0 无、1 AI、2 剧透、3 虚构；历史状态无法识别时为 null |
| - rewardPoint | 打赏所需积分 |
| - rewarded | 当前阅读者是否已打赏或为文章作者 |
| - rewardContent | 可查看的打赏内容源文本，否则为空 |
| - rewardContentHtml | 可查看的打赏内容 HTML，否则为空 |
| - interactions | 可选，仅登录且请求该字段时返回。单项缺失表示该项状态未取得 |
| -- vote | 当前投票及赞同、反对计数，格式同文章投票状态 |
| -- thanks | 当前感谢状态、计数与所需积分，格式同文章感谢状态 |
| -- reactions | 当前表态及汇总，格式同文章表态状态 |
| -- collection / subscription | 当前收藏和订阅状态，各包含 targetId 和 following |
| - qna | 问答当前公开状态；非问答为 null |
| -- offerPoint | 文章当前悬赏积分 |
| -- accepted | 是否存在已采纳回答；包括隐藏或匿名回答，不包含回答编号、作者或正文 |

> commentable 为 false 或 null 时，不应发起新评论、回复或评论编辑；已有评论仍可读取，已成功请求的原幂等键可以恢复。旧服务未提供这些字段时应展示状态暂不可用，不推定允许评论或无创作声明。

> 每次读取按文章和作者当前状态、匿名访问设置及当前讨论范围判断，并返回当前内容；失效或已不可读的内容不会沿用之前的读取结果。不存在或不可查看的文章返回 HTTP 404。原始内容与文本字段应按文本处理，展示富文本使用 contentHtml；思绪播放器需要自行安全处理记录。此接口仅执行读取，不增加浏览数。

### 文章评论列表

`GET /api/v3/articles/{articleId}/comments`

分页读取文章的普通评论，包含回复，不包含段评。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| articleId | 文章 ID | 1636516552191 |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |
| sort | oldest（默认）、latest、hot | oldest |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/articles/1636516552191/comments?p=1&size=20&sort=oldest' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 评论数组 |
| -- id / articleId | 评论 / 所属文章 ID |
| -- parentId | 被回复评论 ID，无回复目标时为空 |
| -- content | 评论 Markdown 源文本，不可查看时为空 |
| -- contentHtml | 评论 HTML，不可查看时为空 |
| -- contentVisible | 当前阅读者是否可查看正文 |
| -- createdAt | 创建时间，毫秒时间戳 |
| -- anonymous | 是否匿名发表 |
| -- author | 公开作者资料，字段与文章摘要 author 一致 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 文章和评论引用均按当前状态及可读范围返回。文章不存在或不可查看时返回 HTTP 404。仅作者可见评论的正文只对评论作者和文章作者可见，其他阅读者会得到 contentVisible=false 的占位记录。非法 ID、分页或排序返回 HTTP 422。


> 每条评论附带 `interactions`，其中 `vote` 含赞同数、反对数，`thanks` 含感谢数，`reactions` 含各表情的计数。登录读者同时获得自己的投票、感谢状态与费用、表态选择。正文不可见时 `interactions` 为 null。表态用户明细可通过单条表态接口读取。

## API v3 官方认证（企业）

首批仅企业入驻，提交一张营业执照图片。以下接口仅向第一方客户端开放；审核入口仅指定审核员可使用。提供申请、状态、审核列表、一次性材料读取和结论提交。审核结论只记录通过或驳回，不会自动发放勋章或改变账号权限，原人工发放流程保留。

### 提交企业认证申请

`POST /api/v3/me/identity-applications/enterprise`

需要 settings.write，直接上传营业执照原始图片数据。请按二进制发送完整文件，不要先转为字符串。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer访问令牌 | Bearer {accessToken} |
| Content-Type（Header） | 仅image/jpeg或image/png | image/png |
| Idempotency-Key（Header） | 本次申请唯一键，重试不变 | enterprise-20261024-1 |
| 请求体 | 一张JPEG或PNG原始图片，最多5MiB；不是JSON或multipart | license.png的二进制内容 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/identity-applications/enterprise' \
  --header 'Authorization: Bearer {accessToken}' \
  --header 'Content-Type: image/png' \
  --header 'Idempotency-Key: enterprise-20261024-1' \
  --data-binary '@license.png'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 申请结果 | |
| - id | 申请ID | 1792800000000 |
| - createdAt | 创建毫秒时间戳 | 1792800000000 |

> 同一用户最多一份待处理申请，包括证明图片已读取但尚未给结论的申请；重复新键返回409（application_pending）。同键同图片及类型重试返回原申请，同键提交另一张有效图片返回409（idempotency_conflict）。重放不会重新保存已经读取的图片，也不保证原申请当前仍待处理。
>
> 不接收图片网址、PDF、多张图片或其他认证类型。图片格式不符或无法解析返回422，不支持的Content-Type返回415，超过5MiB返回413；解析资源暂不足时返回503，不会以截断图片提交成功。图片没有公开地址。

### 本人认证申请列表

`GET /api/v3/me/identity-applications`

查询本人申请，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer访问令牌 | Bearer {accessToken} |
| p | 页码，默认1 | 1 |
| size | 每页数量，默认20、最多50 | 20 |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/me/identity-applications?p=1&size=20' \
  --header 'Authorization: Bearer {accessToken}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 分页数据 | |
| - list | 按申请ID降序排列的记录 | |
| -- id / type | 申请ID / 固定enterprise | 1792800000000 / enterprise |
| -- status | pending、approved或rejected | pending |
| -- createdAt | 创建毫秒时间戳 | 1792800000000 |
| -- materialReadAt | 首次读取材料时间，未读为0 | 0 |
| -- reviewedAt | 结论时间，未处理为0 | 0 |
| -- note | 审核说明，无说明为空 | |
| - pagination | p、size、total、pageCount | |

> 仅返回状态及时间等申请资料，不返回图片、图片地址或图片内容。读取材料不会自动把申请标记为已完成。

### 本人认证申请详情

`GET /api/v3/me/identity-applications/{applicationId}`

查询本人单份申请，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer访问令牌 | Bearer {accessToken} |
| applicationId（Path） | 本人申请ID | 1792800000000 |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/me/identity-applications/1792800000000' \
  --header 'Authorization: Bearer {accessToken}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 与本人申请列表单条记录相同 | |
| - id / type / status | 申请ID、类型与状态 | 1792800000000 / enterprise / pending |
| - createdAt / materialReadAt / reviewedAt | 毫秒时间戳，未发生为0 | |
| - note | 审核说明 | |

> 不存在或不属于本人的申请返回404。本人也不能通过此接口重新取得已提交的证明图片。

### 审核员认证申请列表

`GET /api/v3/review/identity-applications`

指定审核员查询申请，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer访问令牌 | Bearer {accessToken} |
| status | pending（默认）、approved或rejected | pending |
| p / size | 页码 / 每页数量，默认1 / 20，size最多50 | 1 / 20 |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/review/identity-applications?status=pending&p=1&size=20' \
  --header 'Authorization: Bearer {accessToken}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 分页数据 | |
| - list | 与本人列表字段相同，另外包含申请人ID | |
| -- applicantId | 申请人公开用户ID | 1630399192600 |
| - pagination | p、size、total、pageCount | |

> 不是指定审核员返回403；仅有管理员角色不代表拥有材料审核资格。列表不含证明图片。

### 审核员一次性读取证明图片

`POST /api/v3/review/identity-applications/{applicationId}/material/open`

指定审核员读取图片，需要 settings.write。这是一次性操作。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer访问令牌 | Bearer {accessToken} |
| applicationId（Path） | 申请ID | 1792800000000 |
| 请求体 | 可省略；如提交JSON，只能为空对象 | {} |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/review/identity-applications/1792800000000/material/open' \
  --header 'Authorization: Bearer {accessToken}' \
  --output enterprise-proof.png
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| HTTP 200响应体 | 图片原始二进制，不套JSON信封 | |
| Content-Type（Header） | image/jpeg或image/png | image/png |
| Content-Disposition（Header） | 图片附件名称 | enterprise-proof-1792800000000.png |
| Cache-Control（Header） | 不缓存 | no-store |

> 首次读取提交时删除服务端材料并记录读取时间，随后才发送图片。断网、响应丢失或客户端未保存都不能再次读取；重试返回410（material_consumed）。不提供可公开访问的下载地址，也不通过幂等键保留图片副本。非指定审核员403，不存在申请404，已读取或已不可用410；失败响应沿v3 JSON错误格式。

### 提交企业认证审核结论

`PUT /api/v3/review/identity-applications/{applicationId}/decision`

指定审核员提交通过或驳回，需要 settings.write，且必须先完成该申请的证明图片读取。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer访问令牌 | Bearer {accessToken} |
| Content-Type（Header） | JSON请求体 | application/json |
| Idempotency-Key（Header） | 本次结论唯一键，重试保持不变 | enterprise-review-20261024-1 |
| applicationId（Path） | 申请ID | 1792800000000 |
| status | approved（通过）或rejected（驳回） | approved |
| note | 可省略或空，去首尾空白后最多256个UTF-16字符的纯文本说明 | 材料已核对 |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/review/identity-applications/1792800000000/decision' \
  --header 'Authorization: Bearer {accessToken}' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: enterprise-review-20261024-1' \
  --data '{"status":"approved","note":"材料已核对"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 本次结论 | |
| - id | 申请ID | 1792800000000 |
| - status | approved或rejected | approved |
| - reviewedAt | 结论毫秒时间戳 | 1792800000000 |
| - note | 保存的纯文本说明 | 材料已核对 |

> 未读取图片返回409（material_unread），不保存结论；以服务端已提交的读取记录为准，读取响应丢失也不能恢复图片。结论提交后释放本人的待处理申请资格，状态和说明保留，图片不会恢复。说明拒绝HTML标签、控制字符、无效Unicode及超长内容；JSON请求体最多4096字节。
>
> 同键同申请、状态和说明重试返回原结论；同键换内容返回409（idempotency_conflict）。申请已有结论时，新键再次提交返回409（application_reviewed），不能覆盖原结论；同键重放不会影响本人后来提交的另一份申请。结论和请求回执一起成功或失败，失败不释放待处理资格。客户端应将note作为纯文本展示。

## API v3 第三方授权

应用需先登记 client_id、允许的权限及完整回调地址。支持 HTTPS；开发环境支持 HTTP 回环地址；原生应用支持预登记的反向域名专用 scheme，例如 `com.example.app:/oauth2redirect`。回调地址含端口、路径和查询参数必须精确匹配，不允许片段、用户凭据及预置 OAuth 响应参数。

本模块采用授权码与 PKCE S256，不接收 apiKey。授权页返回 HTML，授权决定返回 HTTP 303，令牌及撤销响应为直接 JSON，不使用 code/msg/data 信封。错误 JSON 包含 error、error_description；参数错误为 HTTP 400，凭据或来源不受支持为 401/403，媒体类型错误为 415，请求过频为 429，暂时不可用为 503。

### 打开授权页面

`GET /api/v3/oauth/authorize`

在浏览器打开此地址，用户登录后查看应用、账号和申请权限，再明确选择允许或拒绝。允许与拒绝均回到登记地址，并原样带回 state；拒绝时返回 error=access_denied，不生成授权码。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| response_type | 固定为 code | code |
| client_id | 已登记应用 ID | example_app |
| redirect_uri | 完整登记回调地址 | https://example.com/callback |
| scope | 空格分隔的申请权限，必须在应用允许范围内 | content.read profile.read |
| state | 可选，客户端生成并在回调中核对，最多 1024 个 UTF-8 字节 | random-state |
| code_challenge | code_verifier 的 SHA-256 摘要，Base64URL 无填充编码 | PKCE_CHALLENGE |
| code_challenge_method | 固定为 S256 | S256 |

请求示例：

```bash
curl --get 'https://fishpi.cn/api/v3/oauth/authorize' \
  --data-urlencode 'response_type=code' --data-urlencode 'client_id=example_app' \
  --data-urlencode 'redirect_uri=https://example.com/callback' \
  --data-urlencode 'scope=content.read profile.read' --data-urlencode 'state=random-state' \
  --data-urlencode 'code_challenge=PKCE_CHALLENGE' --data-urlencode 'code_challenge_method=S256'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| HTML | 授权页面；未登录时为登录引导，HTTP 401 | 应用名、账号与权限列表 |

> 上例用于构造地址，实际授权应由用户在浏览器完成。code_verifier 应由客户端随机生成并保留，长度 43–128，仅使用字母、数字和 `-._~`。

### 提交授权决定

`POST /api/v3/oauth/consent`

由授权页提交用户的明确选择，使用 application/x-www-form-urlencoded。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| request | 授权页提供的请求标识，10 分钟内有效 | REQUEST |
| proof | 授权页提供的确认凭据 | PROOF |
| decision | 允许 approve 或拒绝 deny | approve |
| csrfToken | 授权页提供的表单令牌 | FORM_TOKEN |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/oauth/consent' \
  --cookie cookies.txt --header 'Origin: https://fishpi.cn' \
  --data-urlencode 'request=REQUEST' --data-urlencode 'proof=PROOF' \
  --data-urlencode 'decision=approve' --data-urlencode 'csrfToken=FORM_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Location | HTTP 303 回调地址；允许时带 code，拒绝时带 error=access_denied；带回非空 state | https://example.com/callback?code=CODE&state=random-state |

> 此示例仅供调试表单；客户端应让用户操作授权页。决定只能提交一次，不能切换或重放。授权码有效期为 5 分钟。

### 换取或刷新令牌

`POST /api/v3/oauth/token`

使用 application/x-www-form-urlencoded。授权码换取令牌，或使用 refresh_token 轮换令牌；不发送 Authorization 请求头。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| grant_type | authorization_code 或 refresh_token | authorization_code |
| client_id | 已登记应用 ID | example_app |
| code | 换码时必填，授权回调提供 | CODE |
| redirect_uri | 换码时必填，与授权请求完全一致 | https://example.com/callback |
| code_verifier | 换码时必填，生成 challenge 时保留的原串 | PKCE_VERIFIER |
| refresh_token | 刷新时必填 | REFRESH_TOKEN |
| scope | 仅刷新时可选，空格分隔，只能缩小已有权限 | content.read |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/oauth/token' \
  --data-urlencode 'grant_type=authorization_code' --data-urlencode 'client_id=example_app' \
  --data-urlencode 'code=CODE' --data-urlencode 'redirect_uri=https://example.com/callback' \
  --data-urlencode 'code_verifier=PKCE_VERIFIER'

curl --request POST 'https://fishpi.cn/api/v3/oauth/token' \
  --data-urlencode 'grant_type=refresh_token' --data-urlencode 'client_id=example_app' \
  --data-urlencode 'refresh_token=REFRESH_TOKEN' --data-urlencode 'scope=content.read'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| access_token | 访问令牌，业务请求使用 Authorization: Bearer | ACCESS_TOKEN |
| token_type | 固定 Bearer | Bearer |
| expires_in | 访问令牌有效秒数 | 900 |
| refresh_token | 新刷新令牌，妥善保存并替换旧值 | REFRESH_TOKEN |
| refresh_expires_in | 刷新令牌有效秒数 | 2592000 |
| scope | 本次授予的权限 | content.read profile.read |

> 授权码及刷新令牌均只能消费一次。使用正确 PKCE 重放已消费授权码，或重用已轮换刷新令牌，会使该次授权的相关令牌失效。客户端应串行刷新；丢失成功响应后重新发起授权。本模块不使用 Idempotency-Key。
>
> 原生客户端可直接使用正确绑定的授权码与PKCE换码，或使用当前有效刷新令牌刷新，不需要网页登录Cookie或apiKey。浏览器跨域换码的 Origin 须匹配登记 HTTP(S) 回调的来源，fetch 使用 credentials: omit。表单使用 UTF-8 百分号编码，不接受重复、未知参数或 JSON；换码与刷新参数不可混用。

### 撤销本次授权

`POST /api/v3/oauth/revoke`

使用 access_token 或 refresh_token 撤销该次授权的令牌，使用 application/x-www-form-urlencoded。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| client_id | 已登记应用 ID | example_app |
| token | 本次授权的访问令牌或刷新令牌 | TOKEN |
| token_type_hint | 可选，提示令牌类型 | refresh_token |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/oauth/revoke' \
  --data-urlencode 'client_id=example_app' --data-urlencode 'token=TOKEN' \
  --data-urlencode 'token_type_hint=refresh_token'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| JSON | 已受理请求成功为 HTTP 200；未知或已撤销令牌的协议结果相同 | {} |

> 原生客户端可用属于当前应用的有效 access_token 或 refresh_token 直接撤销，不需要网页登录Cookie或apiKey。请检查HTTP状态和Content-Type，不要把重定向或HTML页面当作撤销成功。

## API v3 段落与评论详情

### 登记长篇阅读

`POST /api/v3/articles/{articleId}/reads`

正文成功显示后登记当前用户的一次阅读，需要 apiKey。同一用户、同一文章在同一6小时窗口内只计一次；仅支持当前有效、可读、可列出的署名长篇，作者本人不计入。SSR、预览及正文读取失败时不应调用。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| articleId（路径） | 长篇文章ID | 1645002736006 |
| Authorization（Header） | Bearer访问令牌，需要content.read、activity.write | Bearer TOKEN |
| 请求体 | 可省略，或提交空JSON对象；无其他字段 | {} |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/articles/1645002736006/reads' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 成功为0 | 0 |
| msg | 提示信息 |  |
| data | 登记结果 | {} |
| - articleId | 本次文章ID | 1645002736006 |
| - recorded | 本次是否新增计数；同窗口已登记返回false | true |

> 不使用Idempotency-Key，服务端按当前用户、文章及6小时窗口去重；超时后在同一窗口内重试不会重复计数。跨窗口调用可新增一次。登记不直接发放奖励，也不接受读者身份、窗口、计数或奖励参数；奖励沿后台结算，结算失败后由后台重试，不由客户端重复发奖。本人阅读返回409，目标不符合条件或不可读返回404，非法JSON返回400、非JSON请求体返回415，参数错误422，暂不可用503。旧Markdown接口及经典浏览量链路的长篇奖励登记采用相同资格和窗口；旧响应及页面浏览量保持。

### 长篇段落列表

`GET /api/v3/articles/{articleId}/paragraphs`

获取当前长篇正文的段落锚点、摘要和段评数量，供段评发布与定位使用。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| articleId | 长篇文章 ID，路径参数 | 1770000000001 |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |
| includeContent | 可选，true 时同时返回带段落锚点的正文 HTML，默认 false | true |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/articles/1770000000001/paragraphs?p=1&size=20&includeContent=true' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 段落结果 |
| - list | 按正文顺序排列的当前段落 |
| -- id | 64 位小写段落锚点，用作发布时的 paragraphId |
| -- kind | 段落类型，如 paragraph、heading、list-item、blockquote、code、table、media |
| -- index | 在整篇正文中的位置，从 0 开始 |
| -- snapshot | 纯文本段落摘要，按普通文本展示 |
| -- state | 当前段落为 active |
| -- commentCount | 此锚点下有效段评数量，包含回复 |
| - pagination | 分页信息 |
| -- p / size | 当前页 / 每页数量 |
| -- total / pageCount | 段落总数 / 总页数 |
| - contentHtml | 仅 includeContent=true 时返回；完整正文 HTML，元素带 data-long-paragraph-id、data-long-paragraph-kind 和 data-long-paragraph-index |

> 正文中的站内文章链接仅展示当前可公开的标题；无可公开标题时显示网址，不提供受限标题；链接保留原网址目标及查询参数、片段。

> 锚点以本接口当前结果为准，建议与 includeContent 返回的正文共同使用。正文编辑或相关标题变化后应重新获取；分页只影响 list，contentHtml 始终为整篇正文。兼容既有段落标识；标识无法唯一对应时返回 HTTP 409，不会猜测评论归属。不存在、不可查看或非长篇文章返回 HTTP 404，非法分页或 includeContent 返回 HTTP 422。

### 长篇段评列表

`GET /api/v3/articles/{articleId}/paragraph-comments`

读取段评及回复，也可读取已失效的段评关联。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| articleId | 长篇文章 ID，路径参数 | 1770000000001 |
| paragraphId | 可选，64 位小写段落锚点；不传则查询全文段评 | 64 位小写 SHA-256 |
| state | active（默认）、orphaned（段落失效）、all | active |
| sort | oldest（默认）、latest、hot | oldest |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/articles/1770000000001/paragraph-comments?state=all&sort=oldest&p=1&size=20' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 段评结果 |
| - list | 段评与回复的平铺列表，可通过 parentId 关联 |
| -- id / articleId / parentId | 评论 ID / 所属文章 ID / 原评论 ID，独立段评的 parentId 为空 |
| -- content / contentHtml | Markdown 正文 / 正文 HTML；不可读时均为空 |
| -- contentVisible | 当前阅读者是否可读正文 |
| -- createdAt / anonymous | 创建时间戳（毫秒） / 是否匿名 |
| -- author | 公开作者资料 |
| --- id / userName / userNickname / userAvatarURL | 公开 ID / 用户名 / 昵称 / 头像；匿名作者 ID 为空 |
| -- type | 段评固定为 1 |
| -- paragraph | 段落关联信息 |
| --- id / kind / index / state | 锚点 / 类型 / 位置 / active 或 orphaned |
| -- permalink | 文章内评论链接 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 当前页 / 每页数量 / 总数 / 总页数 |

> 仅楼主可见评论的正文仅评论者本人及楼主可读。当前段落摘要从长篇段落列表获取，评论对象不返回历史段落快照。段评与回复均参与分页；给定锚点下无匹配记录时返回空列表。不存在、不可查看或非长篇文章返回 HTTP 404，非法筛选、排序或分页返回 HTTP 422，段落标识无法唯一对应时返回 HTTP 409。

> 每条评论附带 `interactions`，其中 `vote` 含赞同数、反对数，`thanks` 含感谢数，`reactions` 含各表情的计数。登录读者同时获得自己的投票、感谢状态与费用、表态选择。正文不可见时 `interactions` 为 null。表态用户明细可通过单条表态接口读取。

### 评论详情

`GET /api/v3/comments/{commentId}`

读取单条普通评论或段评，也可按 parentId 读取回复目标。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| commentId | 评论 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/comments/1770000000003' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 评论对象 |
| - id / articleId / parentId | 评论 ID / 所属文章 ID / 原评论 ID |
| - content / contentHtml / contentVisible | 正文 / 正文 HTML / 是否可读；不可读时正文为空 |
| - createdAt / anonymous | 创建时间戳（毫秒） / 是否匿名 |
| - author | 公开作者资料 |
| -- id / userName / userNickname / userAvatarURL | 公开 ID / 用户名 / 昵称 / 头像；匿名作者 ID 为空 |
| - type | 0 普通评论，1 段评 |
| - paragraph | 普通评论为 null；段评为关联信息 |
| -- id / kind / index / state | 锚点 / 类型 / 位置 / active 或 orphaned |
| - permalink | 文章内评论链接 |

> 不存在、已失效的评论或所属文章不可查看时返回 HTTP 404。仅楼主可见正文及匿名作者资料遵循段评列表的规则；有效段评标识无法唯一对应时返回 HTTP 409。

### 读取评论当前位置

`GET /api/v3/comments/{commentId}/location`

查询普通评论或段评在当前列表中的位置，用于打开文章后定位评论。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| commentId | 评论 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/comments/1770000000003/location' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 当前定位信息，不包含评论正文或作者身份 |
| - id / articleId | 评论 ID / 所属文章 ID |
| - type | 0 普通评论，1 段评 |
| - page / size | 当前页码 / 固定每页20条，按评论ID升序 |
| - paragraph | 普通评论为 null；段评为当前分组信息 |
| -- id | 段落标识；有效旧标识归入当前兼容分组 |
| -- state | active 当前段评，orphaned 原段落已移除的段评 |

> 普通评论使用文章普通评论列表；段评使用返回的 paragraph.id 和 state 查询段评列表，二者均按 oldest、每页20条读取。私密正文不可见时仍可定位其列表占位，不会因此获得正文或匿名作者身份。
>
> 位置反映查询时的当前列表；删除、编辑或段落变化后可能需要重新查询，不保证页码永久不变。不存在、失效或所属文章不可查看，以及当前无法列出的评论返回 HTTP 404；非法ID返回422，位置暂不可计算返回503。

## API v3 标签联想

### 按名称联想标签

`GET /api/v3/suggestions/tags`

查询仍有关联文章的有效标签名称，最多7条。空前缀返回热门标签；非空按名称忽略大小写匹配前缀，并优先返回使用较多的标签。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer访问令牌；需要 content.read | Bearer TOKEN |
| prefix | 可选，去首尾空白后最多64个UTF-16单位；只接受中文、英文字母、数字、下划线及 & # + - .，空值返回热门标签 | C+ |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/suggestions/tags?prefix=C%2B' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 成功为0 | 0 |
| msg | 提示信息 | 空字符串 |
| data | 联想结果 | 对象 |
| - list | 标签名称数组，最多7项 | `["C++"]` |

> 无匹配时list为空数组；无效前缀返回422。本接口只查询，不创建标签。客户端选择建议时只替换当前标签，保留其他输入；文章提交规则保持。

## API v3 用户联想

### 按用户名联想

`GET /api/v3/suggestions/users`

按用户名忽略大小写匹配前缀，返回最多10个当前有效账号，供输入用户名时选择。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 profile.read | Bearer TOKEN |
| prefix | 可选，空值或不传时返回空列表；非空为1至64位英文字母、数字、下划线或连字符，不裁剪首尾空白 | al |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/suggestions/users?prefix=al' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 成功为0 | 0 |
| msg | 提示信息 | 空字符串 |
| data | 联想结果 | 对象 |
| - list | 用户数组，最多10项 | 数组 |
| -- id | 公开用户ID | 1770000000001 |
| -- userName | 用户名，保留原大小写 | Alpha |
| -- userNickname | 昵称文本，可为空 | 示例昵称 |
| -- userAvatarURL | 头像地址，可为空 | https://file.fishpi.cn/avatar.png |

> 只匹配用户名，不搜索昵称；下划线按普通字符匹配。不返回总人数或分页信息，空前缀不返回管理员名单。无匹配时list为空数组；无效前缀返回422。昵称按文本展示。

## API v3 Markdown

### 预览 Markdown

`POST /api/v3/markdown/preview`

将提交的 Markdown 转为清洗后的 HTML，供编辑器预览，不保存或发布正文。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.read | Bearer TOKEN |
| Content-Type | application/json | application/json |
| content | 必填字符串，最多1024000个UTF-16单位；保留首尾空白，可为空；控制字符只允许换行、回车和制表符 | # 标题 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/markdown/preview' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --data '{"content":"# 标题\n\n**正文**"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 成功为0 | 0 |
| msg | 提示信息 | 空字符串 |
| data | 预览结果 | 对象 |
| - html | 清洗后的HTML；空正文返回空字符串 | &lt;h1&gt;标题&lt;/h1&gt; |

> 预览不扣积分，无需Idempotency-Key。正文不合法返回422；请保留编辑内容，待预览服务恢复后重试。预览只展示受支持的Markdown格式，文章发布后的排版可能有所不同。

## API v3 评论写入

### 发表评论或回复

`POST /api/v3/articles/{articleId}/comments`

在可评论的文章下发表评论、回复或长篇段评。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 comments.write | Bearer TOKEN |
| Idempotency-Key | 本次发布的唯一请求键 | comment-post-20261017-001 |
| articleId | 文章 ID，路径参数 | 1770000000001 |
| content | 必填，Markdown 正文，1–4096 字符，不可全为空白 | 这篇文章写得很好 |
| anonymous | 可选，是否匿名，布尔值，默认 false；需要匿名评论权限 | false |
| visible | 可选，是否仅楼主可见，布尔值，默认 false；评论者本人仍可见 | false |
| parentId | 可选，当前文章内可读的原评论 ID；不传时发表独立评论 | 1770000000003 |
| paragraphId | 可选，v3 长篇段落列表提供的 64 位小写段落锚点；不传时发表普通评论，回复段评时自动继承原段落 | 64 位小写 SHA-256 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/articles/1770000000001/comments' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: comment-post-20261017-001' \
  --data '{"content":"这篇文章写得很好","anonymous":false,"visible":false}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 发布结果 |
| - id | 新评论 ID |
| - permalink | 文章内评论链接；段评链接包含 paragraph 参数 |

> 需要绑定手机、具备评论权限并通过正文审核，收费、发言间隔和匿名余额条件沿用平台规则。回复目标必须属于当前文章且可读；回复段评时可省略 paragraphId，若传入则必须与原段落一致。独立段评必须指向当前正文中存在的段落。兼容既有段落标识；无法唯一对应时返回 HTTP 409，且不发表或扣款。
>
> 相同请求键及相同内容重试返回原发布结果，不重复发布或扣款；同键不同内容返回 HTTP 409。文章或原评论不存在、不可查看、跨文章回复、段落不存在时返回 HTTP 404；缺少权限、未绑定手机、文章禁止评论或评论状态未知时返回 HTTP 403；字段、审核、余额或发言间隔条件未满足时返回 HTTP 422，操作过于频繁返回 HTTP 429。


### 编辑本人评论

`PUT /api/v3/comments/{commentId}`

编辑本人已有评论或段评，保留作者、匿名状态、回复目标和段落关联。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 comments.write | Bearer TOKEN |
| Idempotency-Key | 本次编辑的唯一请求键 | comment-edit-20261017-001 |
| commentId | 本人评论 ID，路径参数 | 1770000000003 |
| content | 必填，Markdown 正文，1–4096 字符，不可全为空白 | 更新后的评论 |
| visible | 可选，是否仅楼主可见，布尔值；不传保留原值，评论者本人仍可见 | false |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/comments/1770000000003' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: comment-edit-20261017-001' \
  --data '{"content":"更新后的评论","visible":false}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 编辑结果 |
| - id | 评论 ID |
| - permalink | 文章内评论链接 |

> 正文需通过审核。编辑费用与五分钟规则沿用平台行为；窗口外的普通公开评论编辑即使正文未变化，也按原规则收费。相同请求键重试不重复编辑、扣款或发送提醒，重放不会覆盖后续编辑；同键不同内容返回 HTTP 409。
>
> 不存在、不属于本人或所属文章不可查看时返回 HTTP 404；文章锁定、评论关闭或状态未知、评论不可编辑或缺权限返回 HTTP 403。字段、审核或余额不满足要求返回 HTTP 422，操作过于频繁返回 HTTP 429。


### 删除本人评论

`DELETE /api/v3/comments/{commentId}`

删除满足平台条件的本人评论或段评，费用沿用原删除规则。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 comments.write | Bearer TOKEN |
| Idempotency-Key | 本次删除的唯一请求键 | comment-delete-20261018-001 |
| commentId | 本人评论 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/comments/1770000000003' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: comment-delete-20261018-001' \
  --data '{}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 删除结果 |
| - id | 评论 ID |
| - removed | 是否已删除，成功时为 true |

> 请求体可省略或为 `{}`，不接受其他字段。已有回复、赞踩、感谢、已被采纳或积分不足时拒绝删除；匿名及私密评论仍沿用原删除费用，失败不扣款。
>
> 同键重试返回原结果，不重复扣费；删除后使用新键返回 HTTP 404。不存在、不属于本人或所属文章不可查看时返回 HTTP 404；缺少权限返回 HTTP 403；参数、互动或余额条件不满足时返回 HTTP 422，操作过于频繁返回 HTTP 429。
>
> 删除时清理该评论的 emoji reaction，处理可确认关联的评论通知，保留其他消息；成功字段与费用保持不变。

### 读取评论感谢状态

`GET /api/v3/comments/{commentId}/thanks`

评论感谢状态及当前费用，适用于可阅读的普通评论和段评。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.read | Bearer TOKEN |
| commentId | 评论 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/comments/1770000000003/thanks' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 当前状态 |
| - id | 评论 ID |
| - thanked | 本人是否已感谢 |
| - canThank | 是否为他人评论且本人尚未感谢 |
| - thankCount | 当前感谢次数 |
| - thankPoint | 当前感谢费用；匿名评论为 0 |

> canThank 为当前基本资格提示；权限、余额及最终费用在提交时重新校验。目标不存在、失效、作者不可用或正文不可阅读时返回 HTTP 404；缺少权限返回 HTTP 403，编号不合法返回 HTTP 422。

### 感谢评论

`POST /api/v3/comments/{commentId}/thanks`

感谢可阅读的他人评论或段评，费用沿用平台规则；同一用户对同一评论仅感谢一次。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 comments.write | Bearer TOKEN |
| Idempotency-Key | 本次感谢的唯一请求键 | comment-thank-20261018-001 |
| commentId | 评论 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/comments/1770000000003/thanks' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: comment-thank-20261018-001' \
  --data '{}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 感谢结果 |
| - id | 评论 ID |
| - thanked | 是否已感谢，成功时为 true |

> 请求体可省略或为 `{}`，不接受其他字段。相同请求键重试返回原结果；换用新键重复感谢也不重复扣费。匿名评论保留原有免转账规则。
>
> 评论不存在、不可阅读或作者不可用时返回 HTTP 404；缺少权限返回 HTTP 403；感谢自己、积分不足或参数不合法返回 HTTP 422，评论归属变化时返回 HTTP 409，操作过于频繁返回 HTTP 429。保存失败不扣款。

> 写入成功时，`data.interaction` 返回当前评论互动状态，字段同对应单条读取接口。相同幂等键保留原操作结果，当前状态可能已变化。目标已不可读时，`data.interaction` 为 null。状态补查失败时省略该字段。

### 读取评论采纳状态

`GET /api/v3/comments/{commentId}/accept`

评论采纳状态及当前费用，适用于可阅读的普通评论和段评。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.read | Bearer TOKEN |
| commentId | 评论 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/comments/1770000000003/accept' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 当前状态 |
| - id | 评论 ID |
| - accepted | 当前评论是否已被采纳 |
| - canAccept | 当前读者为问答文章作者、非评论作者，文章悬赏至少20积分且尚无采纳时为 true |
| - offerPoint | 当前采纳费用；匿名评论为 0 |

> canAccept 为当前基本资格提示；余额及最终资格在提交时重新校验。仅返回当前评论的状态，不提供其他被采纳评论的编号。目标不存在、失效、作者不可用或正文不可阅读时返回 HTTP 404；缺少权限返回 HTTP 403，编号不合法返回 HTTP 422。

### 采纳评论

`POST /api/v3/comments/{commentId}/accept`

文章作者采纳一条可阅读的他人评论，悬赏积分及匿名免转账规则沿用平台行为。一篇文章仅能采纳一条评论。

> 仅支持问答文章（type=5），文章当前悬赏至少20积分；普通文章或低于最低悬赏时返回 HTTP 422。匿名回答仍沿用免转账规则，但文章也须满足上述资格。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 comments.write | Bearer TOKEN |
| Idempotency-Key | 本次采纳的唯一请求键 | comment-accept-20261018-001 |
| commentId | 评论 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/comments/1770000000003/accept' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: comment-accept-20261018-001' \
  --data '{}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 采纳结果 |
| - id | 被采纳评论 ID |
| - accepted | 是否已采纳，成功时为 true |

> 请求体可省略或为 `{}`，不接受其他字段。相同请求键重试返回原结果；换用新键重复采纳同一评论也不重复付款。保存失败时不扣款。
> 已成功请求使用原键重试仍返回原结果；新键按文章当前类型及悬赏资格检查。
>
> 评论不存在、失效、不可阅读或作者不可用时返回 HTTP 404；非文章作者或缺少权限返回 HTTP 403；采纳自己、余额不足或参数不合法返回 HTTP 422；已有其他采纳或评论归属变化返回 HTTP 409；操作过于频繁返回 HTTP 429。

## API v3 文章感谢与打赏

### 读取文章感谢状态

`GET /api/v3/articles/{articleId}/thanks`

读取本人是否感谢过文章、当前感谢次数及感谢费用。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.read | Bearer TOKEN |
| articleId | 文章 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/articles/1770000000003/thanks' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 感谢状态 |
| - id | 文章 ID |
| - thanked | 本人是否已感谢 |
| - thankCount | 当前感谢次数 |
| - thankPoint | 当前一次感谢所需积分，匿名文章为 0；已感谢不会再次扣除 |

> 文章不存在、作者不可用或不可阅读时返回 HTTP 404。锁定但仍可阅读的文章沿用原感谢规则。

### 感谢文章

`POST /api/v3/articles/{articleId}/thanks`

感谢可阅读的他人文章，费用沿用平台当前感谢规则；匿名文章保留免转账规则。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.write | Bearer TOKEN |
| Idempotency-Key | 本次感谢的唯一请求键 | article-thank-20261018-001 |
| articleId | 文章 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/articles/1770000000003/thanks' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: article-thank-20261018-001' \
  --data '{}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 感谢结果 |
| - id | 文章 ID |
| - thanked | 成功时为 true |

> 请求体可省略或为 `{}`，不接受自定义费用等字段。同键重试返回原结果，换新键重复感谢也不再次扣款、计数或提醒；保存失败不扣款。
>
> 感谢本人文章、余额或参数条件不满足时返回 HTTP 422；目标不可用或不可阅读返回 HTTP 404，缺少权限返回 HTTP 403，文章归属变化返回 HTTP 409，操作过于频繁返回 HTTP 429。

> 成功响应可包含 `data.interaction`，表示当前感谢状态。已不可查看时为 null，旧服务或状态补查失败时可能省略该字段。


### 读取文章打赏状态

`GET /api/v3/articles/{articleId}/reward`

读取本人打赏状态和文章当前打赏价格。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.read | Bearer TOKEN |
| articleId | 文章 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/articles/1770000000003/reward' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 打赏状态 |
| - id | 文章 ID |
| - rewarded | 本人已打赏或本人是文章作者时为 true |
| - rewardPoint | 文章当前打赏积分；小于 1 表示未启用打赏 |

> 正文通过文章详情接口读取，需要 content.read。文章不存在、作者不可用或不可阅读时返回 HTTP 404。

### 打赏文章

`POST /api/v3/articles/{articleId}/reward`

按提交时文章设定的价格打赏，成功后通过文章详情读取打赏正文。匿名文章保留免转账规则。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.write | Bearer TOKEN |
| Idempotency-Key | 本次打赏的唯一请求键 | article-reward-20261018-001 |
| articleId | 文章 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/articles/1770000000003/reward' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: article-reward-20261018-001' \
  --data '{}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 打赏结果 |
| - id | 文章 ID |
| - rewarded | 成功时为 true |

> 请求体可省略或为 `{}`，不接受自定义价格。同键重试返回原结果，换新键重复打赏也不再次扣款或提醒；文章价格调整后，已有打赏记录不会再次付款。保存失败不扣款。
>
> 打赏本人文章、文章未启用打赏、余额或参数条件不满足返回 HTTP 422；目标不可用或不可阅读返回 HTTP 404，缺少权限返回 HTTP 403，文章归属变化返回 HTTP 409，操作过于频繁返回 HTTP 429。作者可直接通过文章详情读取自己的打赏正文。

## API v3 文章投票

### 读取文章投票

`GET /api/v3/articles/{articleId}/vote`

读取本人对可阅读文章的投票，以及文章当前赞踩计数。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.read | Bearer TOKEN |
| articleId | 文章 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/articles/1770000000003/vote' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 投票状态 |
| - id | 文章 ID |
| - vote | 本人投票：up、down；未投票为 null |
| - upCount | 当前赞数 |
| - downCount | 当前踩数 |

> 文章不存在、失效、作者不可用或不可阅读时返回 HTTP 404；缺少 content.read 返回 HTTP 403，ID 格式不合法返回 HTTP 422。

### 设置文章投票

`PUT /api/v3/articles/{articleId}/vote`

设置赞或踩。重复设置相同值不会取消投票，从赞改为踩或从踩改为赞会同步更新计数。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.write | Bearer TOKEN |
| Idempotency-Key | 本次设置的唯一请求键 | article-vote-20261018-001 |
| articleId | 文章 ID，路径参数 | 1770000000003 |
| vote | 必填，只接受 up 或 down | up |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/articles/1770000000003/vote' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: article-vote-20261018-001' \
  --data '{"vote":"up"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 本次操作结果 |
| - id | 文章 ID |
| - vote | 设置后的本人投票：up 或 down |
| - upCount | 本次操作后的赞数 |
| - downCount | 本次操作后的踩数 |

> 普通用户不能对本人文章投票。字段或自投条件不满足时返回 HTTP 422；目标不可用或不可阅读时返回 HTTP 404，缺少权限返回 HTTP 403，文章归属变化时返回 HTTP 409，操作过于频繁返回 HTTP 429。
>
> 相同请求键及相同 vote 重试返回原结果，不重复修改计数或发送提醒。换用新键设置相同值也不重复计数；同键改变 vote 或改为清除操作返回 HTTP 409。重放结果包含原操作时的计数，不覆盖后续投票；获取最新状态请调用读取接口。

> 成功响应可包含 `data.interaction`，表示当前投票状态。已不可查看时为 null，旧服务或状态补查失败时可能省略该字段。


### 清除文章投票

`DELETE /api/v3/articles/{articleId}/vote`

清除本人的赞或踩；本来未投票时保持不变。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.write | Bearer TOKEN |
| Idempotency-Key | 本次清除的唯一请求键 | article-vote-clear-20261018-001 |
| articleId | 文章 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/articles/1770000000003/vote' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: article-vote-clear-20261018-001' \
  --data '{}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 本次操作结果 |
| - id | 文章 ID |
| - vote | 清除后为 null |
| - upCount | 本次操作后的赞数 |
| - downCount | 本次操作后的踩数 |

> 请求体可省略或为 `{}`，不接受其他字段。同键重试返回原结果，换新键重复清除也不重复改变计数；重放不会覆盖后续投票。目标和权限限制与设置接口一致，保存失败不改变投票及计数。

## API v3 评论投票

> 成功响应可包含 `data.interaction`，表示当前投票状态。已不可查看时为 null，旧服务或状态补查失败时可能省略该字段。


### 读取评论投票

`GET /api/v3/comments/{commentId}/vote`

读取本人对可阅读评论的投票，以及评论当前赞踩计数。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.read | Bearer TOKEN |
| commentId | 评论 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/comments/1770000000003/vote' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 投票状态 |
| - id | 评论 ID |
| - vote | 本人投票：up、down；未投票为 null |
| - upCount | 当前赞数 |
| - downCount | 当前踩数 |

> 评论不存在、失效、作者不可用或不可阅读时返回 HTTP 404；缺少 content.read 返回 HTTP 403，ID 格式不合法返回 HTTP 422。

### 设置评论投票

`PUT /api/v3/comments/{commentId}/vote`

设置赞或踩。重复设置相同值不会取消投票，从赞改为踩或从踩改为赞会同步更新计数。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 comments.write | Bearer TOKEN |
| Idempotency-Key | 本次设置的唯一请求键 | comment-vote-20261018-001 |
| commentId | 评论 ID，路径参数 | 1770000000003 |
| vote | 必填，只接受 up 或 down | up |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/comments/1770000000003/vote' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: comment-vote-20261018-001' \
  --data '{"vote":"up"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 本次操作结果 |
| - id | 评论 ID |
| - vote | 设置后的本人投票：up 或 down |
| - upCount | 本次操作后的赞数 |
| - downCount | 本次操作后的踩数 |

> 普通用户不能对本人评论投票。字段或自投条件不满足时返回 HTTP 422；目标不可用或不可阅读时返回 HTTP 404，缺少权限返回 HTTP 403，评论归属变化时返回 HTTP 409，操作过于频繁返回 HTTP 429。
>
> 相同请求键及相同 vote 重试返回原结果，不重复修改计数或发送提醒。换用新键设置相同值也不重复计数；同键改变 vote 或改为清除操作返回 HTTP 409。重放结果包含原操作时的计数，不覆盖后续投票；获取最新状态请调用读取接口。

> 写入成功时，`data.interaction` 返回当前评论互动状态，字段同对应单条读取接口。相同幂等键保留原操作结果，当前状态可能已变化。目标已不可读时，`data.interaction` 为 null。状态补查失败时省略该字段。

### 清除评论投票

`DELETE /api/v3/comments/{commentId}/vote`

清除本人的赞或踩；本来未投票时保持不变。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 comments.write | Bearer TOKEN |
| Idempotency-Key | 本次清除的唯一请求键 | comment-vote-clear-20261018-001 |
| commentId | 评论 ID，路径参数 | 1770000000003 |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/comments/1770000000003/vote' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: comment-vote-clear-20261018-001' \
  --data '{}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 本次操作结果 |
| - id | 评论 ID |
| - vote | 清除后为 null |
| - upCount | 本次操作后的赞数 |
| - downCount | 本次操作后的踩数 |

> 请求体可省略或为 `{}`，不接受其他字段。同键重试返回原结果，换新键重复清除也不重复改变计数；重放不会覆盖后续投票。目标和权限限制与设置接口一致，保存失败不改变投票及计数。

> 写入成功时，`data.interaction` 返回当前评论互动状态，字段同对应单条读取接口。相同幂等键保留原操作结果，当前状态可能已变化。目标已不可读时，`data.interaction` 为 null。状态补查失败时省略该字段。

## API v3 内容发现与公开主页

### 搜索文章

`GET /api/v3/search`

按标题或正文包含的关键字查询文章。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| q | 关键字，去除首尾空白后为 1–100 字符 | Java |
| sort | latest（默认）、active、hot、comments | latest |
| type | all（默认）、recent、long | all |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/search?q=Java&p=1&size=20'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id / title | 文章 ID / 标题 |
| -- preview / tags | 文本摘要 / 逗号分隔标签 |
| -- type / anonymous | 文章类型 / 是否匿名发表 |
| -- author | 公开作者资料，包含 id、userName、userNickname、userAvatarURL；匿名发表时隐藏真实身份 |
| -- permalink | 文章链接 |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| -- commentCount / viewCount | 评论 / 浏览数 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 特殊字符按字面含义搜索；结果不包含讨论帖和不可列出的文章。非法参数返回 HTTP 422；请求过于频繁返回 HTTP 429。

### 标签目录

`GET /api/v3/tags`

分页读取正在使用的标签，按关联文章数排序。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/tags'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id | 标签 ID |
| -- title / uri | 标签名称 / 标签路径名 |
| -- description | 描述源文本 |
| -- iconURL | 图标地址 |
| -- articleCount / followerCount | 关联文章数 / 关注人数 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

### 标签详情

`GET /api/v3/tags/{tagId}`

读取标签详情。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| tagId | 标签 ID | 20261016001 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/tags/20261016001'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 详情对象 |
| - id | 标签 ID |
| - title / uri | 标签名称 / 标签路径名 |
| - description | 描述源文本 |
| - iconURL | 图标地址 |
| - articleCount / followerCount | 关联文章数 / 关注人数 |

> 标签不存在或不可用时返回 HTTP 404。

### 标签文章

`GET /api/v3/tags/{tagId}/articles`

分页读取标签关联的文章。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| tagId | 标签 ID | 20261016001 |
| sort | latest（默认）、active、hot、comments | latest |
| type | all（默认）、recent、long | all |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/tags/20261016001/articles'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id / title | 文章 ID / 标题 |
| -- preview / tags | 文本摘要 / 逗号分隔标签 |
| -- type / anonymous | 文章类型 / 是否匿名发表 |
| -- author | 公开作者资料，包含 id、userName、userNickname、userAvatarURL；匿名发表时隐藏真实身份 |
| -- permalink | 文章链接 |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| -- commentCount / viewCount | 评论 / 浏览数 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 标签不存在或不可用时返回 HTTP 404。

### 领域目录

`GET /api/v3/domains`

分页读取可用领域，按领域顺序排列。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/domains'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id | 领域 ID |
| -- title / uri | 领域名称 / 路径名 |
| -- description | 描述源文本 |
| -- iconURL | 图标地址 |
| -- tagCount | 关联标签数 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

### 领域详情

`GET /api/v3/domains/{domainId}`

读取领域详情。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| domainId | 领域 ID | 20261016002 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/domains/20261016002'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 详情对象 |
| - id | 领域 ID |
| - title / uri | 领域名称 / 路径名 |
| - description | 描述源文本 |
| - iconURL | 图标地址 |
| - tagCount | 关联标签数 |

> 领域不存在或不可用时返回 HTTP 404。

### 领域标签

`GET /api/v3/domains/{domainId}/tags`

分页读取领域关联的标签。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| domainId | 领域 ID | 20261016002 |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/domains/20261016002/tags'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id | 标签 ID |
| -- title / uri | 标签名称 / 标签路径名 |
| -- description | 描述源文本 |
| -- iconURL | 图标地址 |
| -- articleCount / followerCount | 关联文章数 / 关注人数 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 领域不存在或不可用时返回 HTTP 404。

### 领域文章

`GET /api/v3/domains/{domainId}/articles`

分页读取领域下的文章，同一文章只出现一次。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| domainId | 领域 ID | 20261016002 |
| sort | latest（默认）、active、hot、comments | latest |
| type | all（默认）、recent、long | all |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/domains/20261016002/articles'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id / title | 文章 ID / 标题 |
| -- preview / tags | 文本摘要 / 逗号分隔标签 |
| -- type / anonymous | 文章类型 / 是否匿名发表 |
| -- author | 公开作者资料，包含 id、userName、userNickname、userAvatarURL；匿名发表时隐藏真实身份 |
| -- permalink | 文章链接 |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| -- commentCount / viewCount | 评论 / 浏览数 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 领域不存在或不可用时返回 HTTP 404。

### 公开用户资料

`GET /api/v3/users/{userName}`

读取用户公开资料。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 profile.read | Bearer TOKEN |
| userName | 用户名，最多 64 字符 | Vanessa |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/users/Vanessa'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 详情对象 |
| - id / userName | 用户 ID / 用户名 |
| - userNickname / userAvatarURL | 昵称 / 头像 |
| - userIntro / userURL | 个人介绍文本 / 个人网址 |
| - cardBg | 个性卡片背景图片 URL，未设置或无有效图片地址时为空字符串 |
| - userPoint | 当前积分余额，整数 |
| - onlineMinute | 累计在线时长，单位分钟，非负整数 |

> 用户不存在或不可用时返回 HTTP 404。 资料及相关列表按当前账号状态和隐私范围读取；收回公开权限后，无权读者的相关列表返回 HTTP 403，本人和符合既有权限的管理员例外保持。

### 用户文章

`GET /api/v3/users/{userName}/articles`

分页读取用户公开发表的可列出文章。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| userName | 用户名 | Vanessa |
| sort | latest（默认）、active、hot、comments | latest |
| type | recent（默认）、long、all | recent |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/users/Vanessa/articles'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id / title | 文章 ID / 标题 |
| -- preview / tags | 文本摘要 / 逗号分隔标签 |
| -- type / anonymous | 文章类型 / 是否匿名发表 |
| -- author | 公开作者资料，包含 id、userName、userNickname、userAvatarURL；匿名发表时隐藏真实身份 |
| -- permalink | 文章链接 |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| -- commentCount / viewCount | 评论 / 浏览数 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 沿用用户文章列表隐私设置，无权查看返回 HTTP 403；不包含匿名发表文章和讨论帖。

### 用户评论

`GET /api/v3/users/{userName}/comments`

读取用户公开署名、位于可列出文章下的评论，包含普通评论和段评。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| userName | 用户名，URL 参数 | Vanessa |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/users/Vanessa/comments?p=1&size=20'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表结果 |
| - list | 记录数组 |
| -- id / articleId / parentId | 评论 ID / 文章 ID / 原评论 ID |
| -- type | 0 普通评论，1 段评 |
| -- content / contentHtml / contentVisible | 正文 / 渲染正文 / 当前阅读者是否可见正文 |
| -- createdAt / anonymous / author | 创建时间（毫秒） / 是否匿名 / 公开作者资料 |
| -- article | 文章摘要，与用户文章列表的记录字段相同 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 遵循用户的评论列表公开设置；不收录匿名评论、讨论帖或当前不可访问的文章。私密评论在无权阅读时保留记录，正文为空且 contentVisible=false。

### 用户关注动态

`GET /api/v3/users/{userName}/watching`

聚合用户关注的用户和标签的新文章，同一篇文章只出现一次。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| userName | 用户名，URL 参数 | Vanessa |
| source | all（默认）、users、tags | all |
| sort | latest（默认）、active、hot、comments | latest |
| type | all（默认）、recent、long | all |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/users/Vanessa/watching?p=1&size=20'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表结果 |
| - list | 记录数组 |
| -- id / title / preview / tags / type / anonymous | 文章 ID / 标题 / 摘要 / 标签 / 类型 / 是否匿名 |
| -- author / permalink / createdAt / updatedAt / commentCount / viewCount | 公开作者 / 链接 / 创建和更新时间 / 评论和浏览次数 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 遵循用户的关注文章列表公开设置；按关注用户查找时不收录其匿名文章。source=all 合并用户和标签两个来源，不包括文章订阅。

### 用户订阅文章

`GET /api/v3/users/{userName}/watching/articles`

分页读取用户订阅的文章。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| userName | 用户名，URL 参数 | Vanessa |
| sort | latest（默认）、active、hot、comments | latest |
| type | all（默认）、recent、long | all |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/users/Vanessa/watching/articles?p=1&size=20'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表结果 |
| - list | 记录数组 |
| -- id / title / preview / tags / type / anonymous | 文章 ID / 标题 / 摘要 / 标签 / 类型 / 是否匿名 |
| -- author / permalink / createdAt / updatedAt / commentCount / viewCount | 公开作者 / 链接 / 创建和更新时间 / 评论和浏览次数 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 遵循用户的关注文章列表公开设置；仅包含当前可列出的订阅文章，收藏文章请使用 following/articles。

### 用户清风明月

`GET /api/v3/users/{userName}/breezemoons`

分页读取用户的清风明月。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| userName | 用户名 | Vanessa |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/users/Vanessa/breezemoons'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id | 清风明月 ID |
| -- content / contentHtml | 正文 Markdown / 正文 HTML |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| -- author | 公开作者资料，包含 id、userName、userNickname、userAvatarURL |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 沿用用户清风明月列表隐私设置，无权查看返回 HTTP 403。 单条详情同样按当前作者状态及可见范围判断，不可读时返回 HTTP 404。

### 用户关注的用户

`GET /api/v3/users/{userName}/following/users`

分页读取用户关注的用户。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 profile.read | Bearer TOKEN |
| userName | 用户名 | Vanessa |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/users/Vanessa/following/users'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id / userName / userNickname / userAvatarURL | 用户 ID / 用户名 / 昵称 / 头像 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 沿用用户关注列表隐私设置，无权查看返回 HTTP 403。

### 用户关注的标签

`GET /api/v3/users/{userName}/following/tags`

分页读取用户关注的标签。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| userName | 用户名 | Vanessa |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/users/Vanessa/following/tags'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id | 标签 ID |
| -- title / uri | 标签名称 / 标签路径名 |
| -- description | 描述源文本 |
| -- iconURL | 图标地址 |
| -- articleCount / followerCount | 关联文章数 / 关注人数 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 沿用用户关注标签隐私设置，无权查看返回 HTTP 403。

### 用户收藏的文章

`GET /api/v3/users/{userName}/following/articles`

分页读取用户收藏的可列出文章。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| userName | 用户名 | Vanessa |
| sort | latest（默认）、active、hot、comments | latest |
| type | all（默认）、recent、long | all |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/users/Vanessa/following/articles'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id / title | 文章 ID / 标题 |
| -- preview / tags | 文本摘要 / 逗号分隔标签 |
| -- type / anonymous | 文章类型 / 是否匿名发表 |
| -- author | 公开作者资料，包含 id、userName、userNickname、userAvatarURL；匿名发表时隐藏真实身份 |
| -- permalink | 文章链接 |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| -- commentCount / viewCount | 评论 / 浏览数 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 沿用用户收藏文章隐私设置，无权查看返回 HTTP 403；文章字段与文章列表一致。

### 用户粉丝

`GET /api/v3/users/{userName}/followers`

分页读取用户的粉丝。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 profile.read | Bearer TOKEN |
| userName | 用户名 | Vanessa |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/users/Vanessa/followers'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id / userName / userNickname / userAvatarURL | 用户 ID / 用户名 / 昵称 / 头像 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 沿用用户粉丝列表隐私设置，无权查看返回 HTTP 403。

### 清风明月列表

`GET /api/v3/breezemoons`

按发布时间倒序读取清风明月。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/breezemoons'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 结果数组 |
| -- id | 清风明月 ID |
| -- content / contentHtml | 正文 Markdown / 正文 HTML |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| -- author | 公开作者资料，包含 id、userName、userNickname、userAvatarURL |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |


## API v3 个人资源

以下接口使用 Bearer 访问令牌读取当前账号的数据。无效或缺失令牌返回 HTTP 401，缺少所需权限返回 HTTP 403。

### 读取个人设置

`GET /api/v3/me/settings`

读取个人资料、隐私开关和显示偏好。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 profile.read | Bearer TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 设置对象 |
| - profile | 包含 userNickname、userIntro、userURL |
| - privacy | 各列表是否公开，true 表示公开 |
| -- articles / comments / breezemoons / points | 文章 / 评论 / 清风明月 / 积分 |
| -- followers / followingUsers / followingTags / followingArticles | 粉丝 / 关注用户 / 关注标签 / 收藏文章 |
| - display | 显示偏好 |
| -- commentViewMode / avatarViewMode | 评论 / 头像模式 |
| -- skin / mobileSkin | 桌面 / 移动皮肤 |

### 读取签到状态

`GET /api/v3/me/checkin`

读取今日签到与活跃状态。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 activity.read | Bearer TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/checkin' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 签到状态 |
| - checkedIn | 今日是否已签到 |
| - liveness | 当前活跃度百分比 |
| - threshold | 自动签到活跃度门槛 |
| - streak | 连续签到天数 |

> 此接口只读取状态，不执行签到。

### 读取背包

`GET /api/v3/me/bag`

读取当前用户的背包物品数量。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 activity.read | Bearer TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/bag' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 背包数据 |
| - list | 物品列表 |
| -- code / count | 物品编码 / 数量 |

### 读取积分流水

`GET /api/v3/me/points`

分页读取当前用户的积分流水。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 activity.read | Bearer TOKEN |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/points' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 流水数据 |
| - list | 积分记录数组 |
| -- time | 发生时间，毫秒时间戳 |
| -- amount / operation | 变动数量 / 收支符号（+ 或 -） |
| -- balance | 变动后的本人余额 |
| -- type / displayType | 类型编码 / 类型名称 |
| -- descriptionHtml | 记录说明 HTML |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

### 读取草稿列表

`GET /api/v3/me/drafts`

分页读取当前用户的发帖草稿，按更新时间倒序。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.read | Bearer TOKEN |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/drafts' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 草稿列表数据 |
| - list | 草稿摘要数组 |
| -- id / title / summary | 草稿 ID / 标题 / 文本摘要 |
| -- tags / type | 标签 / 文章类型 |
| -- columnId / columnTitle / chapterNo | 专栏 ID / 专栏标题 / 章节号 |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 列表不包含正文、思绪记录或打赏正文。

### 读取草稿详情

`GET /api/v3/me/drafts/{draftId}`

读取当前用户的单个草稿。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.read | Bearer TOKEN |
| draftId | 草稿 ID | 20261016003 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/drafts/20261016003' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 草稿对象，包含列表摘要字段及以下字段 |
| - content / thoughtContent | 编辑器正文 / 思绪记录，分别保留 |
| - rewardContent / rewardPoint | 打赏正文 / 打赏积分 |
| - qnaOfferPoint | 悬赏积分 |
| - commentable / anonymous / notifyFollowers | 允许评论 / 匿名发表 / 通知关注者 |
| - showInList / statement | 列表展示选项 / 文章声明 |

> 草稿不存在或不属于当前账号时返回 HTTP 404。所有正文均为编辑器源内容，按文本处理。

### 读取个人专栏

`GET /api/v3/me/columns`

分页读取当前用户的专栏，按手动顺序排列。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌；需要 content.read | Bearer TOKEN |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/columns' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 专栏列表数据 |
| - list | 专栏摘要数组 |
| -- id / title | 专栏 ID / 标题 |
| -- coverURL / order | 封面地址 / 手动排序值 |
| -- articleCount | 文章数量 |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |


## API v3 个人设置

### 读取本人 QQ

`GET /api/v3/me/settings/qq`

读取当前账号已保存的 QQ，仅第一方本人可用，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 profile.read | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/qq' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 当前设置 | `{...}` |
| - qq | 当前保存的 QQ 字符串；未设置为空 | 00123456 |

> 不在公开用户资料或现有第三方资料响应中返回此字段。读取保留当前已保存值；不符合新格式的历史值须修改或清除后才能重新保存。

### 设置本人 QQ

`PUT /api/v3/me/settings/qq`

设置或清除当前账号的 QQ，仅第一方本人可用，需要 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 settings.write | Bearer ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次操作重试使用同一个键 | qq-20261025-001 |
| qq | 字符串；去首尾空白后为空则清除，否则仅接受1至12位英文数字，保留前导0 | 00123456 |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/settings/qq' \
  --header 'Authorization: Bearer ACCESS_TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: qq-20261025-001' \
  --data '{"qq":"00123456"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 本次操作结果 | `{...}` |
| - qq | 本次保存的规范化字符串；清除后为空 | 00123456 |

> 不验证号码是否实际存在。格式错误返回422；同键同内容重试返回原结果，不覆盖后来保存的值，同键异内容返回409。第三方客户端即使持有相同 scope 也不能使用此资源。

### 读取本人个人标签

`GET /api/v3/me/settings/tags`

读取当前账号已保存的个人标签，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 profile.read | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/tags' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 当前设置 | `{...}` |
| - tags | 已保存的标签字符串；未设置为空 | JavaScript,Elasticsearch |

> 读取保留当前已保存值；保存时采用下述严格规则，不会自动修改历史值。

### 设置本人个人标签

`PUT /api/v3/me/settings/tags`

完整替换或清除当前账号的个人标签，需要 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 settings.write | Bearer ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次操作重试使用同一个键 | tags-20261025-001 |
| tags | 必填字符串，原始及规范化后均最多128个UTF-16字符；空字符串清除 | JS；ES、JavaScript |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/settings/tags' \
  --header 'Authorization: Bearer ACCESS_TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: tags-20261025-001' \
  --data '{"tags":"JS；ES、JavaScript"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 本次操作结果 | `{...}` |
| - tags | 规范化并去重后的标签字符串，英文逗号分隔；清除后为空 | JavaScript,Elasticsearch |

> 支持中英文逗号、顿号和中英文分号分隔，移除空白并沿现有标签别名及目录规范化，忽略大小写去重后最多4项。普通标签最多12个UTF-16字符；Elasticsearch、CoffeeScript、C#等现有白名单例外保留。仅接受汉字（U+4E00至U+9FA5）、英文字母、数字、下划线及`&#+-.`，不接受HTML或emoji；无效项不丢弃、超限不截断，整笔返回422。只有分隔符而无标签的非空输入也返回422。普通用户不能设置保留标签，内容须通过现有审核。
>
> 成功时个人设置与标签关联一起更新；清除个人标签不会删除全站标签或其他用户的关联。同键同内容重试返回原结果，不覆盖后来保存的值；同键异内容返回409。客户端应以响应中的规范化字符串为准。

### 读取常用表情

`GET /api/v3/me/settings/emotions`

读取当前账号的常用表情别名，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/emotions' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 当前设置 | `{...}` |
| - emotions | 有序的表情别名数组，未设置时返回默认列表 | `["smile","flushed","joy"]` |

### 设置本人常用表情

`PUT /api/v3/me/settings/emotions`

完整替换常用表情及顺序。可选别名来自表情选项目录；空数组恢复默认常用表情。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | `Bearer` 访问令牌，需要 `settings.write` | `Bearer ACCESS_TOKEN` |
| Idempotency-Key | 本次操作的幂等键 | `emotions-selection-001` |
| emotions | 不重复的表情别名数组，顺序保留；仅接受选项目录中的别名；数量最多为目录有效项数 | `["sob","+1","smile"]` |

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

### 读取常用表情可选项

`GET /api/v3/me/settings/emotion-options`

获取表情选择器的分组别名和默认列表，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/emotion-options' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 表情可选项 | `{...}` |
| - groups | 按展示顺序排列的二维别名数组 | `[["smile","laughing"],["+1","-1"]]` |
| - defaults | 默认常用表情别名数组 | `["smile","flushed","joy"]` |

### 读取本人图片表情分组

`GET /api/v3/me/settings/emoji-groups`

分页读取当前账号已有的图片表情分组，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/emoji-groups?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 分组列表和分页 | `{...}` |
| - list | 按排序值和 ID 升序排列的分组 | `[]` |
| -- id | 分组 ID | 123456789 |
| -- name | 分组名称 | 常用 |
| -- sort | 排序值 | 0 |
| -- type | 分组类型：0 自定义，1 全部 | 0 |
| - pagination | 分页信息 | `{...}` |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总数 / 页数 | 1 / 20 / 0 / 0 |

> 尚无分组时返回空列表。读取不会自动创建“全部”分组。

### 读取本人分组图片表情

`GET /api/v3/me/settings/emoji-groups/{groupId}/emojis`

分页读取本人分组中可用的图片表情，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| groupId | 路径参数，本人分组 ID | 123456789 |
| p | 页码，默认 1 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/emoji-groups/123456789/emojis?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 图片表情列表和分页 | `{...}` |
| - list | 按排序值和条目 ID 升序排列的表情 | `[]` |
| -- id | 分组条目 ID | 123456790 |
| -- emojiId | 图片表情 ID | 123456791 |
| -- name | 条目名称 | 小鱼 |
| -- sort | 排序值 | 0 |
| -- url | 可用的图片地址，地址不可用时为空字符串 | https://images.example/fish.png |
| - pagination | 分页信息 | `{...}` |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总数 / 页数 | 1 / 20 / 0 / 0 |

> 不存在或不属于本人的分组返回 HTTP 404。已删除或无法关联到图片的条目不计入列表。

### 读取 MBTI 设置

`GET /api/v3/me/settings/personality`

读取当前账号的 MBTI，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/personality' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 当前设置 | `{...}` |
| - mbti | 已保存的标准 MBTI，可带 -A/-T；未设置或不可用时为空字符串 | INTJ-A |

### 修改 MBTI 设置

`PUT /api/v3/me/settings/personality`

修改当前账号的 MBTI，需要 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次修改重试保持不变 | settings-mbti-001 |
| mbti | 必填字符串，16 种标准类型，可选 -A/-T；忽略大小写与首尾空白，空字符串清空 | intj-a |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/settings/personality' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: settings-mbti-001' \
  --header 'Content-Type: application/json' \
  --data '{"mbti":"intj-a"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 本次修改结果 | `{...}` |
| - mbti | 规范为大写的 MBTI，清空时为空字符串 | INTJ-A |

> 类型目录见下方可选项接口。无效类型、非字符串、额外后缀（如 INTJ-A-extra）返回 HTTP 422，保留原设置。同一幂等键对大小写与首尾空白规范化后的相同内容返回原结果，不覆盖后续修改；不同内容返回 HTTP 409。操作过于频繁时返回 HTTP 429。

### 读取 MBTI 可选项

`GET /api/v3/me/settings/personality-options`

获取 MBTI 类型与后缀选项，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/personality-options' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 可选项 | `{...}` |
| - types | 16 种标准类型，字符串数组 | `["INTJ","INTP","ENTJ","ENTP","INFJ","INFP","ENFJ","ENFP","ISTJ","ISFJ","ESTJ","ESFJ","ISTP","ISFP","ESTP","ESFP"]` |
| - variants | 可选后缀，字符串数组 | `["A","T"]` |

> 不带后缀也是完整的 MBTI 类型，例如 INTJ；带后缀时用连字符连接，例如 INTJ-A。

### 读取头像设置

`GET /api/v3/me/settings/avatar`

读取当前账号保存的头像地址，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/avatar' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 当前设置 | `{...}` |
| - avatarURL | 保存的头像地址；没有可用地址时为空字符串 | https://images.example/avatar.png |

> 返回保存的地址，保留查询参数，不追加缩放或静态化参数。历史 HTTP 和站内相对地址仍可读取；客户端显示头像时应按 URL 处理。 公开资料及文章、评论作者头像同样保留合法地址中的完整查询参数，避免签名失效。

### 修改头像设置

`PUT /api/v3/me/settings/avatar`

设置当前账号的头像地址，需要 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次修改重试保持不变 | settings-avatar-001 |
| avatarURL | 必填字符串，去除首尾空白后 1–255 个 UTF-16 单位；合法 HTTPS 地址，允许外部图片地址，禁止用户凭据 | https://images.example/avatar.png |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/settings/avatar' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: settings-avatar-001' \
  --header 'Content-Type: application/json' \
  --data '{"avatarURL":"https://images.example/avatar.png"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 本次修改结果 | `{...}` |
| - avatarURL | 本次保存的头像地址 | https://images.example/avatar.png |

> 可先使用上传票据上传图片，再提交公开图片 URL，也可直接使用外部 HTTPS 图片地址。接口只校验地址格式，不检查远程图片是否可访问；允许无扩展名和带查询参数的图片地址。不接受空字符串清空。重复请求返回原结果，不覆盖后来保存的新头像；同键不同内容返回 HTTP 409。旧页面的头像缩放、静态化及来源展示规则仍适用。

### 使用积分修改用户名

`PUT /api/v3/me/settings/user-name`

修改当前账号的用户名，每次收取当前改名费用（目前为1000积分），需要 activity.write 和 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次修改重试保持不变 | settings-user-name-001 |
| userName | 必填字符串，1至64位英文字母、数字或连字符 `-`；不接受空白、下划线、保留名称或已使用名称 | new-fish |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/settings/user-name' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: settings-user-name-001' \
  --header 'Content-Type: application/json' \
  --data '{"userName":"new-fish"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 本次改名结果 | `{...}` |
| - userName | 本次保存的用户名 | new-fish |
| - paidPoints | 本次支付的积分 | 1000 |

> 名称还需通过内容校验。名称与扣费一起保存，余额不足或名称已被使用返回 HTTP 409，失败不改名、不扣费。当前名称及仅大小写不同的同名也视为已被使用。同键同请求重试返回原结果，不重复扣费，也不覆盖后来设置的用户名；同键不同请求返回 HTTP 409。遇到连接中断或结果暂未确认，请保留原幂等键与请求体重试。改名卡仍使用独立的改名卡接口。

### 读取个人资料

`GET /api/v3/me/settings/profile`

读取当前账号的个人资料，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/profile' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 当前设置 | `{...}` |
| - userNickname | 昵称，最多 20 字符，可为空 | 小鱼 |
| - userIntro | 简介，最多 255 字符，可为空并保留换行 | 喜欢摸鱼 |
| - userURL | HTTP/HTTPS 个人网址，最多 100 字符；空字符串清空 | https://example.com/ |

### 修改个人资料

`PUT /api/v3/me/settings/profile`

仅修改本次提交的字段，未提交字段保留，需要 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次修改重试保持不变 | settings-profile-001 |
| userNickname | 昵称，最多 20 字符，可为空 | 小鱼 |
| userIntro | 简介，最多 255 字符，可为空并保留换行 | 喜欢摸鱼 |
| userURL | HTTP/HTTPS 个人网址，最多 100 字符；空字符串清空 | https://example.com/ |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/settings/profile' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: settings-profile-001' \
  --header 'Content-Type: application/json' \
  --data '{"userNickname":"小鱼"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 修改后的完整个人资料，字段同对应读取接口 | `{...}` |

> 昵称和简介的特殊字符可能占用更多保存长度，超出容量返回 HTTP 422。至少提交一个字段。同键重试返回原结果，同键更改内容返回 HTTP 409；重放旧请求不会覆盖之后的新设置。

### 读取隐私开关

`GET /api/v3/me/settings/privacy`

读取当前账号的隐私开关，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/privacy' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 当前设置 | `{...}` |
| - articles | 文章列表公开 | true |
| - comments | 评论列表公开 | true |
| - breezemoons | 清风明月列表公开 | true |
| - points | 积分记录公开 | true |
| - followers | 粉丝列表公开 | true |
| - followingUsers | 关注用户公开 | true |
| - followingTags | 关注标签公开 | true |
| - followingArticles | 文章收藏公开 | true |
| - watchingArticles | 文章订阅公开 | true |
| - online | 在线状态公开 | true |
| - userAgent | 设备信息公开 | true |
| - joinPointRank | 参加积分榜 | true |
| - joinUsedPointRank | 参加消费积分榜 | true |

### 修改隐私开关

`PUT /api/v3/me/settings/privacy`

仅修改本次提交的字段，未提交字段保留，需要 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次修改重试保持不变 | settings-privacy-001 |
| articles | 文章列表公开 | true |
| comments | 评论列表公开 | true |
| breezemoons | 清风明月列表公开 | true |
| points | 积分记录公开 | true |
| followers | 粉丝列表公开 | true |
| followingUsers | 关注用户公开 | true |
| followingTags | 关注标签公开 | true |
| followingArticles | 文章收藏公开 | true |
| watchingArticles | 文章订阅公开 | true |
| online | 在线状态公开 | true |
| userAgent | 设备信息公开 | true |
| joinPointRank | 参加积分榜 | true |
| joinUsedPointRank | 参加消费积分榜 | true |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/settings/privacy' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: settings-privacy-001' \
  --header 'Content-Type: application/json' \
  --data '{"articles":false}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 修改后的完整隐私开关，字段同对应读取接口 | `{...}` |

> 至少提交一个字段。同键重试返回原结果，同键更改内容返回 HTTP 409；重放旧请求不会覆盖之后的新设置。

### 读取显示设置

`GET /api/v3/me/settings/display`

读取当前账号的显示设置，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/display' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 当前设置 | `{...}` |
| - commentViewMode | 评论模式，0 传统 / 1 实时 | 0 |
| - avatarViewMode | 头像模式，0 原图 / 1 静态 | 1 |
| - skin | PC 皮肤 ID，最多 32 字符，须为有效 PC 皮肤 | classic/pc |
| - mobileSkin | 移动皮肤 ID，最多 32 字符，须为有效移动皮肤 | classic/mobile |

### 修改显示设置

`PUT /api/v3/me/settings/display`

仅修改本次提交的字段，未提交字段保留，需要 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次修改重试保持不变 | settings-display-001 |
| commentViewMode | 评论模式，0 传统 / 1 实时 | 0 |
| avatarViewMode | 头像模式，0 原图 / 1 静态 | 1 |
| skin | PC 皮肤 ID，最多 32 字符，须为有效 PC 皮肤 | classic/pc |
| mobileSkin | 移动皮肤 ID，最多 32 字符，须为有效移动皮肤 | classic/mobile |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/settings/display' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: settings-display-001' \
  --header 'Content-Type: application/json' \
  --data '{"commentViewMode":1}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 修改后的完整显示设置，字段同对应读取接口 | `{...}` |

> 至少提交一个字段。同键重试返回原结果，同键更改内容返回 HTTP 409；重放旧请求不会覆盖之后的新设置。

### 可用皮肤目录

`GET /api/v3/me/settings/skins`

读取 PC 和移动端可选皮肤，供显示设置使用，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/skins' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 皮肤目录 | `{...}` |
| - list | 皮肤数组 | `[...]` |
| -- id | 写入 skin/mobileSkin 的皮肤 ID | classic/pc |
| -- device | pc / mobile | pc |
| -- name / description | 名称 / 描述 | 经典主题 / 经典布局 |
| -- previewURL | 预览图地址，可为空 | /images/preview.png |
| -- order | 展示排序值 | 0 |

> 皮肤必须与设备匹配。原 `GET /api/v3/me/settings` 汇总接口保持原字段不变；上述子接口提供完整隐私开关和文本资料。升级后请重新登录获取 settings.write 权限。

### 读取功能设置

`GET /api/v3/me/settings/functions`

读取当前账号的功能设置，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/functions' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 当前设置 | `{...}` |
| - listPageSize | 文章列表每页条数，10–96 | 20 |
| - listViewMode | 列表显示，0 标题 / 1 标题与摘要 | 1 |
| - notifications | 通知提示开关 | true |
| - subscriptionEmail | 订阅邮件开关 | false |
| - keyboardShortcuts | 键盘快捷键开关 | true |
| - autoSubscribeOnReply | 回复后自动订阅文章 | true |
| - externalLinkConfirmation | 站外链接跳转确认开关 | true |
| - chatImageBlur | 聊天室图片自动模糊开关 | true |
| - indexRedirectURL | 首页跳转地址，最多 255 字符；空字符串清空，仅可设置当前站点及部署路径内的绝对地址 | https://fishpi.cn/recent |

### 修改功能设置

`PUT /api/v3/me/settings/functions`

仅修改提交字段，未提交字段保留，需要 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次修改重试保持不变 | settings-functions-001 |
| listPageSize | 文章列表每页条数，10–96 | 20 |
| listViewMode | 列表显示，0 标题 / 1 标题与摘要 | 1 |
| notifications | 通知提示开关 | true |
| subscriptionEmail | 订阅邮件开关 | false |
| keyboardShortcuts | 键盘快捷键开关 | true |
| autoSubscribeOnReply | 回复后自动订阅文章 | true |
| externalLinkConfirmation | 站外链接跳转确认开关 | true |
| chatImageBlur | 聊天室图片自动模糊开关 | true |
| indexRedirectURL | 首页跳转地址，最多 255 字符；空字符串清空，仅可设置当前站点及部署路径内的绝对地址 | https://fishpi.cn/recent |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/settings/functions' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: settings-functions-001' \
  --header 'Content-Type: application/json' \
  --data '{"listPageSize":20,"notifications":true}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 修改后的完整功能设置，字段同对应读取接口 | `{...}` |

> 至少提交一个字段。无效枚举、错误类型或超出范围返回 HTTP 422；同键重试返回原结果，同键更换内容返回 HTTP 409。布尔开关必须使用 true/false。

> 用户站登录后进入不带筛选或定位信息的首页时，按首页跳转设置前往已支持的站内页面。空值保持默认首页；目标未支持或设置读取失败时保留首页并提示。

### 读取位置公开设置

`GET /api/v3/me/settings/location`

读取当前账号的位置公开设置，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/location' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 当前设置 | `{...}` |
| - publicLocation | 是否公开位置；不提供城市或 IP 修改 | false |

### 修改位置公开设置

`PUT /api/v3/me/settings/location`

仅修改提交字段，未提交字段保留，需要 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次修改重试保持不变 | settings-location-001 |
| publicLocation | 是否公开位置；不提供城市或 IP 修改 | false |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/settings/location' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: settings-location-001' \
  --header 'Content-Type: application/json' \
  --data '{"publicLocation":false}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 修改后的完整位置公开设置，字段同对应读取接口 | `{...}` |

> 至少提交一个字段。无效枚举、错误类型或超出范围返回 HTTP 422；同键重试返回原结果，同键更换内容返回 HTTP 409。布尔开关必须使用 true/false。

### 读取语言和时区

`GET /api/v3/me/settings/locale`

读取当前账号的语言和时区，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/locale' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 当前设置 | `{...}` |
| - language | 语言代码，取自可选项目录 | zh_CN |
| - timezone | 时区 ID，取自可选项目录 | Asia/Shanghai |

### 修改语言和时区

`PUT /api/v3/me/settings/locale`

仅修改提交字段，未提交字段保留，需要 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次修改重试保持不变 | settings-locale-001 |
| language | 语言代码，取自可选项目录 | zh_CN |
| timezone | 时区 ID，取自可选项目录 | Asia/Shanghai |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/settings/locale' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: settings-locale-001' \
  --header 'Content-Type: application/json' \
  --data '{"language":"zh_CN","timezone":"Asia/Shanghai"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 修改后的完整语言和时区，字段同对应读取接口 | `{...}` |

> 至少提交一个字段。无效枚举、错误类型或超出范围返回 HTTP 422；同键重试返回原结果，同键更换内容返回 HTTP 409。布尔开关必须使用 true/false。

### 语言和时区可选项

`GET /api/v3/me/settings/locale-options`

读取站点支持的语言及服务器可用时区，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/settings/locale-options' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 可选项 | `{...}` |
| - languages | 语言代码数组 | ["en_US","zh_CN"] |
| - timezones | 时区数组 | `[...]` |
| -- id / name | 时区 ID / 显示名称 | Asia/Shanghai / 中国标准时间 |

> 修改位置公开设置不会更改账号记录的位置。首页跳转地址必须与当前站点协议、主机和端口一致；指向站点首页的地址归一为空字符串。旧功能设置入口同样只接受当前站点及部署路径内的首页跳转地址；中文路径、锚点和正常编码文件名保持可用。

## API v3 背包物品使用

### 使用单日免签卡

`POST /api/v3/me/bag/checkin1day/use`

消耗当前账号一张单日免签卡，增加 1 天剩余免签天数，需要 activity.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次使用的重试保持不变 | card-use-001 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/bag/checkin1day/use' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: card-use-001' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 使用结果 | `{...}` |
| - itemCode | 消耗的物品代码 | checkin1day |
| - remainingDays | 使用后的剩余免签天数 | 4 |
| - bag | 使用后的背包，结构同 `GET /api/v3/me/bag` | `{...}` |
| -- list | 物品列表 | `[...]` |
| --- code / count | 物品代码 / 数量 | checkin1day / 1 |

> 每次成功请求消耗一张卡；例如已有 3 天，用后为 4 天。同键重试返回原结果，不会再次消耗；另用一张卡须换新键。没有卡或剩余天数无法继续增加时返回 HTTP 409。不接受指定账号、数量或免签天数。

### 使用两日免签卡

`POST /api/v3/me/bag/checkin2days/use`

消耗当前账号一张两日免签卡，增加 2 天剩余免签天数，需要 activity.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次使用的重试保持不变 | card-use-002 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/bag/checkin2days/use' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: card-use-002' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 使用结果，结构同“使用单日免签卡” | `{...}` |
| - itemCode | 消耗的物品代码 | checkin2days |
| - remainingDays | 使用后的剩余免签天数 | 5 |
| - bag | 使用后的背包 | `{...}` |

> 例如已有 3 天，用后为 5 天；幂等与错误处理同单日免签卡。免签天数供系统后续自动签到使用，不代表此请求立即完成今日签到。升级后请重新登录获取 activity.write 权限。

### 使用补签卡

`POST /api/v3/me/bag/patchCheckinCard/use`

消耗当前账号一张补签卡，按平台自然日补一天；能连接已有连续记录时接回。需要 activity.write，不额外发放签到积分。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 activity.write | Bearer TOKEN |
| Idempotency-Key | 同一次补签重试时保持不变 | patch-checkin-20261018-001 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/bag/patchCheckinCard/use' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: patch-checkin-20261018-001' \
  --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 使用结果 | `{...}` |
| - itemCode | 消耗的物品代码 | patchCheckinCard |
| - streak | 补签后的连续天数 | 29 |
| - streakStart | 当前连续记录起始日，yyyyMMdd | 20260201 |
| - streakEnd | 当前连续记录结束日，yyyyMMdd | 20260301 |
| - longestStreak | 最长连续天数 | 29 |
| - bag | 使用后的背包 | `{...}` |
| -- list | 数量型物品列表 | `[]` |
| --- code | 物品代码 | patchCheckinCard |
| --- count | 剩余数量 | 1 |

> 请求体可省略或为 `{}`，不接受自定义日期、数量或用户。需要卡片、可用补签记录且今天已签到；当前连续记录本身就是最长记录时不可使用。断签超过一天时只补一日。
>
> 同键重试返回原结果，不重复扣卡；成功后使用新键通常因无补签记录返回 HTTP 409。读取最新背包请使用 `GET /api/v3/me/bag`，读取最新签到状态请使用 `GET /api/v3/me/checkin`。
>
> 无卡、无可用记录、今天未签到或其他补签条件不满足返回 HTTP 409（item_unavailable、patch_unavailable、not_checked_in 或 current_longest）；失败保留卡片和补签记录。账户不可用返回 HTTP 404，缺少权限返回 HTTP 403，缺失/非法请求键或多余字段返回 HTTP 422，请求过于频繁返回 HTTP 429。此接口不会触发今日签到。

### 使用改名卡

`POST /api/v3/me/bag/nameCard/use`

消耗当前账号一张改名卡并修改用户名，需要 activity.write 和 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；同时需要 activity.write 和 settings.write | Bearer TOKEN |
| Idempotency-Key | 同一次改名重试时保持不变 | name-card-20261018-001 |
| userName | 新用户名；去除首尾空白后为 1–64 位英文字母、数字或 `-`，规则同注册 | Fresh-Name |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/bag/nameCard/use' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: name-card-20261018-001' \
  --data '{"userName":"Fresh-Name"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 使用结果 | `{...}` |
| - itemCode | 消耗的物品代码 | nameCard |
| - userName | 本次成功修改的用户名 | Fresh-Name |
| - bag | 使用后的背包，结构同 `GET /api/v3/me/bag` | `{...}` |
| -- list | 数量型物品列表 | `[]` |
| --- code | 物品代码 | nameCard |
| --- count | 剩余数量 | 1 |

> 不接受保留用户名、已使用的用户名或当前用户名；不接受自定义用户、数量或其他字段。无卡返回 HTTP 409（item_unavailable），用户名已使用返回 HTTP 409（name_taken），格式错误返回 HTTP 422（invalid_name）。账户不可用返回 HTTP 404，缺少任一权限返回 HTTP 403。
>
> 扣卡与改名一并保存，校验或保存失败不消耗卡片。同键同内容重试返回原结果，不重复改名或扣卡；同键不同内容返回 HTTP 409。后续再次改名后重放旧请求仍返回当时的结果，当前资料与背包请重新查询。
>
> 遇到服务端错误或断线，操作可能已经保存，请使用原 Idempotency-Key 和原请求体重试确认结果。

## API v3 实时通知

### 获取实时连接票据

`POST /api/v3/realtime/tickets`

获取当前用户的一次性通知连接票据，需要 notifications.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 notifications.read | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/realtime/tickets' \
  --header 'Authorization: Bearer ACCESS_TOKEN' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 连接凭据 | `{...}` |
| - ticket | 一次性连接票据 | OPAQUE_TICKET |
| - expiresIn | 可用于认证的剩余秒数，最多60秒 | 60 |
| - path | 同一服务上的WebSocket路径，包含部署路径 | /api/v3/realtime |
| - channel | 订阅类型 | notifications |

> 请求体可省略或为 `{}`，不接受指定用户、父令牌、频道、scope或有效期。每次请求签发新票据，无需Idempotency-Key；票据最多成功使用一次。缺少权限返回HTTP 403，访问凭据不可用返回HTTP 401；实时通知服务暂不可用返回HTTP 503（realtime_unavailable）。

### 连接通知通道

`WS /api/v3/realtime`

使用取票响应的path连接同一服务。HTTPS服务使用wss，开发环境可使用ws。收到authentication.required后，立即发送authenticate消息。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | 认证消息固定为authenticate | authenticate |
| ticket | 上一步返回的一次性票据 | OPAQUE_TICKET |

请求示例：

```bash
SOCKET_TICKET='OPAQUE_TICKET' node --input-type=module <<'JS'
const socket = new WebSocket('wss://fishpi.cn/api/v3/realtime');
socket.addEventListener('message', ({ data }) => {
  const message = JSON.parse(data);
  if (message.type === 'authentication.required') {
    socket.send(JSON.stringify({ type: 'authenticate', ticket: process.env.SOCKET_TICKET }));
  } else {
    console.log(message);
  }
});
JS
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | authentication.required、ready、pong或notifications.changed | ready |
| data | 消息数据；除ready外为空对象 | `{...}` |
| - channel | ready消息中的订阅类型 | notifications |
| - expiresAt | ready消息中的连接授权到期时间，毫秒时间戳 | 1792304100000 |

> 连接地址不带查询参数；认证只接受`{"type":"authenticate","ticket":"..."}`。不要在URL中放票据、accessToken或apiKey。认证成功后可发送`{"type":"ping"}`，服务端返回pong。重复认证、无效/过期/已使用票据、非法消息或失效的连接授权会关闭连接。
>
> notifications.changed是本账号通知刷新的提示，data为空；收到后重新调用`GET /api/v3/me/notifications/unread-count`或通知列表。提示可能合并或重复，不提供持久化重放；建立、恢复连接时应主动读取HTTP状态。
>
> 连接授权不超过取票时原访问令牌的有效期。刷新访问令牌不会延长已经建立的连接；到期后使用新的访问令牌重新取票并连接。本通道用于通知刷新，不发送通知正文或聊天消息。

## API v3 个人聊天外观

`GET /api/v3/me/chat-appearance` 读取本人有效外观，需要第一方网页身份与 profile.read。

`PUT /api/v3/me/chat-appearance` 明确设置本人气泡与头像框，需要第一方网页身份、settings.write 和 Idempotency-Key。请求仅接受两个必填字符串：

```json
{"bubbleId":"paipai-orange","frameId":"fishpond-blue"}
```

气泡支持 `classic`（默认）、`paipai-orange`、`fishpond-blue`、`candy`（会员）；头像框支持 `none`（默认）、`paipai-orange`、`fishpond-blue`、`gold`（会员）。会员样式要求当前有效会员资格，不满足返回 HTTP 403（membership_required）；未知 ID 或多余字段返回 HTTP 422（invalid_request）。

两个接口成功的 data 为：

```json
{"bubbleId":"paipai-orange","frameId":"fishpond-blue","version":1}
```

同键同内容重试返回首次结果，同键不同内容返回 HTTP 409（idempotency_conflict）。更新响应是该次操作结果；请 GET 确认当前选择。会员到期时读取与新消息采用有效默认项。主题不在此接口范围。

聊天室消息查询（列表、详情、上下文）的每条消息增加 `appearance`，结构同上；它是消息实际发布时保存的外观，不是作者当前配置。修改外观只影响之后发布的消息，会员到期不剥除历史快照。旧消息无快照按 classic/none、version=1 展示。普通消息发送请求不增加外观参数，也不接受客户端提供 CSS 或资源 URL。

version 为样式目录版本；客户端应保留已支持版本的历史视觉，未知版本或 ID 按默认展示。不支持此渲染协议的客户端可忽略该字段。

## API v3 聊天室消息

### 发送普通消息

`POST /api/v3/chat/messages`

发送当前账号的普通聊天室正文，需要 content.write 和 Idempotency-Key。成功后可使用消息查询接口读取详情，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| content | 消息正文，支持 Markdown，最多4096字符，不能为空 | 大家好！ |
| client | 客户端标记，可选，格式为品牌/版本，最多128字符 | Web/MyClient |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/chat/messages' \
  --header 'Authorization: Bearer {accessToken}' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: chat-message-001' \
  --data '{"content":"大家好！","client":"Web/MyClient"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 发送结果 | |
| - id | 消息 ID | 1792304100000 |
| - createdAt | 发送时间 | 1792304100000 |

> 只接受普通正文；完整的红包、音乐、天气、话题、弹幕业务包装，执法/zf命令，以及红包/天气/音乐卡片 JSON 返回 HTTP 422（special_message_required）。解释性引用按普通正文发送，不执行业务命令或自动 AI 回复。
>
> 正文按平台规则处理并去除首尾空白。client 使用支持的品牌和格式，不能识别的标记不展示。未绑定手机或当前账号不可发言时拒绝发送；需要再次确认正文时返回 HTTP 409（confirmation_required），请使用原 Idempotency-Key 和原请求体再次发送。
>
> 同键同内容重试返回首次结果，不重复发送或扣分；同键不同内容返回 HTTP 409（idempotency_conflict）。服务端错误或断线后也请使用原键和原请求体重试确认结果。广播和提醒可能延迟或丢失，客户端应结合消息查询与实时提示同步记录。

### 撤回本人消息

`DELETE /api/v3/chat/messages/{messageId}`

撤回当前账号发布的聊天室消息，需要 content.write 和 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer 访问令牌 | Bearer {accessToken} |
| Idempotency-Key（Header） | 本次操作的幂等键 | chat-withdraw-001 |
| messageId（Path） | 本人消息 ID | 1792304100000 |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/chat/messages/1792304100000' \
  --header 'Authorization: Bearer {accessToken}' \
  --header 'Idempotency-Key: chat-withdraw-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 撤回结果 | |
| - id | 消息 ID | 1792304100000 |
| - removed | 已撤回 | true |
| - cost | 本次消耗积分 | 32 |

> 无需请求体，也可发送空 JSON 对象。普通用户每天可撤回多次，当天费用依次为 0、32、64……积分，与旧撤回接口共享计次；改名和服务重启不重置计次，平台自然日切换后重新计算。原有管理角色的免费规则保留，本接口仅撤回本人消息。
>
> 不存在、已撤回或非本人消息返回 HTTP 404；红包消息返回 HTTP 409（message_not_removable），积分不足返回 HTTP 409（insufficient_points）。未提交成功的操作不会扣分或增加计次。 拒绝撤回时保留消息及已有互动，客户端无需按撤回成功清理记录。
>
> 同键重试返回首次成功回执，不重复扣分。报错或断线后请保留原消息 ID 和幂等键重试确认，不要换键；成功后可用消息查询接口确认 HTTP 404，并清理本地记录及 reaction。实时提示可能延迟或丢失。

### 查询当前话题

`GET /api/v3/chat/topic`

读取当前聊天室话题，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer 访问令牌 | Bearer {accessToken} |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/chat/topic' \
  --header 'Authorization: Bearer {accessToken}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 当前话题 | |
| - text | 话题纯文本 | 今天摸什么鱼？ |

> text 应作为纯文本展示。话题修改后会保存，重启不会清空；后续修改会更新当前值。

### 修改聊天室话题

`PUT /api/v3/chat/topic`

设置聊天室话题，需要 content.write，每次成功修改消耗 16 积分。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer 访问令牌 | Bearer {accessToken} |
| Content-Type（Header） | JSON 请求体 | application/json |
| Idempotency-Key（Header） | 本次操作的唯一键，重试时保持不变 | topic-20261019-1 |
| text | 话题文本，最多 64 个 UTF-16 单位；按聊天室话题字符规则过滤并去除首尾空白，结果不能为空 | 今天摸什么鱼？ |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/chat/topic' \
  --header 'Authorization: Bearer {accessToken}' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: topic-20261019-1' \
  --data '{"text":"今天摸什么鱼？"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 本次修改结果 | |
| - text | 保存后的话题纯文本 | 今天摸什么鱼？ |
| - cost | 本次话题费用 | 16 |
| - revision | 字符串形式的变更版本 | 123 |

> 超长或无有效内容返回 HTTP 422；余额不足返回 HTTP 409（insufficient_points）。需要再次确认时返回 HTTP 409（confirmation_required），使用原键及原请求体再次发送。
>
> 同键同内容重试返回原结果，不重复扣费；同键换内容返回 HTTP 409（idempotency_conflict）。断线或服务端错误后也请保留原键重试。另用新键修改，即使文本相同仍按一次修改收费。原结果不代表最新话题，读取当前值请调用 GET /api/v3/chat/topic。
>
> 新旧话题修改均保存当前值，话题与费用未提交成功时不扣分；旧入口正常请求和成功字段保持。客户端应将话题作为纯文本展示。

### 发送音乐卡片

`POST /api/v3/chat/music`

发送音乐卡片，需要 content.write。标题和来源作为纯文本展示，沿聊天室普通发言资格。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer 访问令牌 | Bearer {accessToken} |
| Content-Type（Header） | JSON 请求体 | application/json |
| Idempotency-Key（Header） | 本次发送的唯一键，重试保持不变 | music-20261024-1 |
| title | 去首尾空白，1至128个UTF-16字符的纯文本 | 午后音乐 |
| from | 可省略或空，去首尾空白，最多64个UTF-16字符的纯文本 | 分享者 |
| source | 1至32位网易云歌曲ID，或最多2048字符的合法HTTPS音频地址 | 123456 |
| coverURL | 可省略或空，最多2048字符的合法HTTPS封面地址 | https://example.com/cover.png |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/chat/music' \
  --header 'Authorization: Bearer {accessToken}' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: music-20261024-1' \
  --data '{"title":"午后音乐","from":"分享者","source":"123456","coverURL":""}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 发送结果 | |
| - id | 消息ID | 1792800000000 |
| - createdAt | 创建时间，毫秒时间戳 | 1792800000000 |

> 不接受HTML标签、控制字符、无效Unicode或超长文本；URL不接受用户凭据、片段、反斜杠及无效端口，完整查询参数会保留。服务端不抓取封面或音频，不保证远程地址可播放，版权、来源服务器限制和浏览器支持会影响播放。
>
> 同键同内容重试返回原发送结果；同键换内容返回409。成功回执不保证消息当前仍存在。需要再次确认时返回409（confirmation_required），保留原键和请求体再次发送；其他发言资格与禁言规则沿聊天室规则。卡片文本不会触发执法、红包、话题等命令，也不会因标题或URL中的用户名产生提及通知。

### 发送天气卡片

`POST /api/v3/chat/weather`

分享客户端提供的五日天气卡片，需要 content.write。此接口保存客户端数据，不提供权威天气查询。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer 访问令牌 | Bearer {accessToken} |
| Content-Type（Header） | JSON 请求体 | application/json |
| Idempotency-Key（Header） | 本次发送的唯一键，重试保持不变 | weather-20261024-1 |
| city | 去首尾空白，1至64个UTF-16字符的纯文本 | 北京 |
| description | 可省略或空，去首尾空白，最多128个UTF-16字符的纯文本 | 客户端预报 |
| days | 固定5项，按日期连续递增 | |
| - date | 有效日期yyyy-MM-dd，年份0001至9999 | 2026-10-24 |
| - code | 天气代码，区分大小写 | CLEAR_DAY |
| - min / max | -100至100的整数，最低不得高于最高 | 5 / 15 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/chat/weather' \
  --header 'Authorization: Bearer {accessToken}' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: weather-20261024-1' \
  --data '{"city":"北京","description":"客户端预报","days":[{"date":"2026-10-24","code":"CLEAR_DAY","min":5,"max":15},{"date":"2026-10-25","code":"CLOUDY","min":6,"max":16},{"date":"2026-10-26","code":"WIND","min":4,"max":14},{"date":"2026-10-27","code":"LIGHT_RAIN","min":5,"max":13},{"date":"2026-10-28","code":"CLEAR_DAY","min":6,"max":17}]}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 发送结果 | |
| - id | 消息ID | 1792800000000 |
| - createdAt | 创建时间，毫秒时间戳 | 1792800000000 |

> code允许：CLEAR_DAY、CLEAR_NIGHT、PARTLY_CLOUDY_DAY、PARTLY_CLOUDY_NIGHT、CLOUDY、LIGHT_HAZE、MODERATE_HAZE、HEAVY_HAZE、LIGHT_RAIN、MODERATE_RAIN、HEAVY_RAIN、STORM_RAIN、FOG、LIGHT_SNOW、MODERATE_SNOW、HEAVY_SNOW、STORM_SNOW、DUST、SAND、WIND。
>
> 日期可跨月或跨年，但必须连续且恰好5天；不限制为今日开始。文本拒绝HTML标签、控制字符、无效Unicode及超长内容，不截断。不接收字符串或小数温度。无效字段返回422，不保存任何消息。同键重试、发言资格及409确认方式与音乐卡片一致。
>
> 两类卡片发送后可通过消息列表、详情和上下文读取。音乐card含title/from/source/coverURL；天气card沿现有date/weatherCode/max/min/t/st格式，前四项为逗号分隔的五日文本，t为城市、st为描述。新卡片文字及URL按保存值返回，客户端需将文字作为纯文本展示。

### 发送聊天室弹幕

`POST /api/v3/chat/barrager`

发送即时弹幕，需要 content.write，费用以发送时的弹幕价格为准。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer 访问令牌 | Bearer {accessToken} |
| Content-Type（Header） | JSON 请求体 | application/json |
| Idempotency-Key（Header） | 本次发送的唯一键，重试保持不变 | barrage-20261019-1 |
| content | 正文，原始输入最多4096个UTF-16单位；去除HTML后为1至32个UTF-16单位的纯文本，超长拒绝 | 今天也要开心 😀 |
| color | 可选，#RRGGBB颜色，默认#FFFFFF；返回事件统一为大写 | #66CCFF |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/chat/barrager' \
  --header 'Authorization: Bearer {accessToken}' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: barrage-20261019-1' \
  --data '{"content":"今天也要开心 😀","color":"#66CCFF"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 本次受理结果 | |
| - id | 弹幕标识，不是聊天历史消息ID | 00000000-0000-4000-8000-000000000001 |
| - createdAt | 受理时间，毫秒时间戳 | 1792304100000 |
| - cost | 本次费用，积分数 | 5 |
| - unit | 当前价格的显示单位 | 积分 |

> 空白、清洗后为空、超长或颜色无效返回 HTTP 422（invalid_barrage）；余额不足返回 HTTP 409（insufficient_points）。需要再次确认时返回 HTTP 409（confirmation_required），使用原键和原请求体再次发送；发送过于频繁返回 HTTP 429。
>
> 同键同请求重试返回原回执，不再次扣费或播放；同键换请求返回 HTTP 409（idempotency_conflict）。发送后价格变化不改变原回执；另用新键视为另一次发送。服务端错误或断线后也请保留原键重试确认结果。
>
> 弹幕为即时动画，不保存到聊天历史，不支持按标识读取、撤回或离线补播。回执表示已受理及扣费成功，不保证每个在线客户端收到动画；提交后的断线或传输失败也不会因同键重试而补播。v3 的 chat.barrager 事件发送给接收请求实例上已连接的 chat 订阅，客户端必须将正文和作者资料按纯文本展示。

### 查询弹幕价格

`GET /api/v3/chat/barrager/price`

读取当前弹幕价格，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer 访问令牌 | Bearer {accessToken} |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/chat/barrager/price' \
  --header 'Authorization: Bearer {accessToken}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 当前价格 | |
| - amount | 当前配置的积分费用 | 5 |
| - unit | 价格显示单位 | 积分 |

> 此接口不发送弹幕或扣分。unit 为显示文本，实际使用积分；价格可能调整，消耗以发送时的规则为准。

### 查询在线用户

`GET /api/v3/chat/online-users`

查询当前可见的聊天室在线用户，需要 content.read。按用户名升序排列，同一用户的多个连接只返回一次。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20，最多50 | 20 |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/chat/online-users?p=1&size=20' \
  --header 'Authorization: Bearer {accessToken}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 在线用户结果 | |
| - list | 当前页用户 | [] |
| -- id | 用户 ID | 123456 |
| -- userName | 用户名 | Fish |
| -- userAvatarURL | 头像地址 | https://file.fishpi.cn/avatar.png |
| - pagination | 分页信息 | |
| -- p | 当前页码 | 1 |
| -- size | 每页条数 | 20 |
| -- total | 本次结果中对当前阅读者可见的用户数 | 42 |
| -- pageCount | 总页数 | 3 |
| - hasMore | 是否有下一页 | true |
| - nodesSampledAt | 最近一次节点采样时间，尚未采样为0 | 1792304100000 |
| - nodesComplete | 该次采样是否包含全部配置节点 | true |

> 结果合并当前服务实例的聊天室连接及最近一次节点名单；v3 连接只有完成聊天室订阅认证才计入，通知或文章订阅不计入。其他服务实例独立建立的 v3 连接不在此统计范围内。人数为该范围内可见用户数，不代表全站实时总人数。
>
> 名单遵循在线状态公开设置；本人和管理员可查看对应用户的在线状态，停用或无效用户不展示。节点名单是采样值，nodesComplete 只描述该次采样是否完整，不保证当前仍在线；请结合 nodesSampledAt 判断时间，连接变化时分页结果可能改变。

### 查询思过崖

`GET /api/v3/chat/mutes`

查询当前尚未到期的个人聊天室禁言，按解除时间、用户ID升序分页。需要 apiKey，权限为 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌 | Bearer ACCESS_TOKEN |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20，最多50 | 20 |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/chat/mutes?p=1&size=20' \
  --header 'Authorization: Bearer {accessToken}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 结果说明 | |
| data | 查询结果 | |
| - list | 当前页记录 | [] |
| -- id | 用户ID | 123456 |
| -- userName | 当前用户名 | Fish |
| -- userNickname | 昵称，可空 | 鱼 |
| -- userAvatarURL | 头像地址，可空 | https://file.fishpi.cn/avatar.png |
| -- expiresAt | 解除时间，毫秒时间戳 | 1793116800000 |
| - pagination | 分页信息 | |
| -- p | 当前页码 | 1 |
| -- size | 每页条数 | 20 |
| -- total | 本次查询的有效记录数 | 42 |
| -- pageCount | 总页数 | 3 |
| - hasMore | 是否有下一页 | true |

> 仅包含当前有效账号的个人禁言，不含全体禁言、风控记录、禁言原因或操作者。状态改变或到期后，分页结果可能变化；服务暂不可用时返回503，请勿当作空名单。

### 查询消息列表

`GET /api/v3/chat/messages`

查询聊天室历史消息，需要 content.read。列表按消息 ID 升序排列；不传游标时返回最近一批消息。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 content.read | Bearer ACCESS_TOKEN |
| size | 条数，1–50，默认20 | 20 |
| before | 可选，只查询此 ID 之前的消息，不含本条；不能与 after 同用 | 1792304100000 |
| after | 可选，只查询此 ID 之后的消息，不含本条；不能与 before 同用 | 1792304100000 |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/chat/messages?size=20' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 消息列表 | `{...}` |
| - list | 消息对象列表，按 ID 升序 | `[]` |
| -- id | 消息 ID | 1792304100000 |
| -- createdAt | 创建时间，毫秒时间戳 | 1792304100000 |
| -- type | text、redPacket、weather、music 或 unavailable | text |
| -- content | 普通消息的原始 Markdown；卡片及 unavailable 为空 | 大家好 |
| -- contentHtml | 普通消息的清洗后 HTML；其他类型为空 | `<p>大家好</p>` |
| -- author | 消息发布时的公开作者信息 | `{...}` |
| --- id | 作者 ID；系统机器人可能为空 | 1630399192600 |
| --- userName | 用户名 | reader |
| --- userNickname | 昵称 | 摸鱼中 |
| --- userAvatarURL | 头像地址 | https://file.fishpi.cn/avatar.png |
| -- client | 客户端标记，可能为空 | Web/PC网页端 |
| -- card | 卡片数据；普通消息及 unavailable 为空对象，字段见下表 | `{}` |
| -- reactions | 可读消息的表态快照，含汇总和当前账号的选择 | `{"targetId":"1792304000000","targetType":"chat","groupType":"emoji","currentUserReaction":"","summary":[]}` |
| - hasMore | 当前查询方向是否还有更多消息；不传游标时指更早消息 | true |
| - nextBefore | 本批最早消息 ID，用于继续查询更早消息；空列表为空 | 1792304000000 |
| - nextAfter | 本批最新消息 ID，用于查询之后的新消息；空列表为空 | 1792304100000 |

卡片字段：

| Key | 说明 | 示例 |
| --- | --- | --- |
| redPacket.type | random、average、specify、heartbeat、rockPaperScissors、dice 或 unknown | random |
| redPacket.message | 红包留言，纯文本 | 一起摸鱼 |
| redPacket.money | 红包金额 | 256 |
| redPacket.count / got | 总份数 / 已领取份数 | 10 / 2 |
| weather.date | 日期文本，沿用消息中的逗号分隔格式 | 2026-10-18,2026-10-19 |
| weather.weatherCode | 天气代码文本 | CLEAR_DAY,CLOUDY |
| weather.max / min | 最高 / 最低温文本 | 25,26 / 18,19 |
| weather.t / st | 温度 / 体感文本 | 22 / 舒适 |
| music.title | 音乐标题，纯文本 | 一首歌 |
| music.source | 音乐 ID 或地址；不可用时为空 | 123456 |
| music.coverURL | 封面地址，可能为空 | https://file.fishpi.cn/cover.png |
| music.from | 音乐来源标记，可能为空 | netease |

> card 中直接使用相应类型的字段，例如 `{"type":"random","message":"一起摸鱼","money":256,"count":10,"got":2}`。所有卡片文本按纯文本展示；content 是源文本，展示 HTML 时使用 contentHtml。
>
> before/after 是数字消息 ID。向前或向后翻页时保留原查询方向，并使用对应游标；空列表时保留上次非空游标。hasMore=false 表示本次查询时该方向已无更多消息，之后仍可能产生新消息。
>
> unavailable 表示该历史消息暂不可读取；仍返回 id 以便继续翻页。详情接口也返回 reactions，需查看表态用户明细时可使用 `GET /api/v3/chat/messages/{targetId}/reactions`。读取红包摘要不会领取红包。
>
> 缺少访问凭据返回 HTTP 401，缺少 content.read 返回 HTTP 403；非法或冲突游标、条数超限返回 HTTP 422。

### 查询单条消息

`GET /api/v3/chat/messages/{messageId}`

查询一条聊天室消息，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 content.read | Bearer ACCESS_TOKEN |
| messageId | 路径参数，消息 ID | 1792304100000 |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/chat/messages/1792304100000' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 消息对象，字段同消息列表 list 中的对象 | `{...}` |
| - id | 消息 ID | 1792304100000 |
| - type | 消息类型 | text |
| - content / contentHtml | 原始 Markdown / 清洗后 HTML | 大家好 |
| - author / card / client / createdAt | 作者、卡片、客户端及时间，结构同列表 | `{...}` |

> 消息不存在或已撤回返回 HTTP 404；非法消息 ID 返回 HTTP 422。

### 查询消息上下文

`GET /api/v3/chat/messages/{messageId}/context`

查询本条消息及前后相邻消息，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 content.read | Bearer ACCESS_TOKEN |
| messageId | 路径参数，上下文中心消息 ID | 1792304100000 |
| radius | 前后各取的条数，1–25，默认10；最多返回51条 | 10 |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/chat/messages/1792304100000/context?radius=10' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 上下文 | `{...}` |
| - anchorId | 中心消息 ID | 1792304100000 |
| - list | 消息对象列表，含中心消息一次，按 ID 升序；对象结构同消息列表 | `[]` |
| - hasMoreBefore / hasMoreAfter | 前方 / 后方是否还有更多消息 | true / false |
| - nextBefore | 本批最早消息 ID；前方无消息时为中心 ID | 1792304000000 |
| - nextAfter | 本批最新消息 ID；后方无消息时为中心 ID | 1792304200000 |

> 中心消息不存在或已撤回返回 HTTP 404；非法 ID 或 radius 超限返回 HTTP 422。位于历史边缘时，仅返回实际存在的相邻消息；可用列表接口及 before/after 继续读取。

## API v3 聊天室实时订阅

### 获取聊天室连接票据

`POST /api/v3/chat/realtime-ticket`

获取公共聊天室消息变更订阅票据，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 content.read | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/chat/realtime-ticket' \
  --header 'Authorization: Bearer ACCESS_TOKEN' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 连接凭据 | `{...}` |
| - ticket | 一次性连接票据 | OPAQUE_TICKET |
| - expiresIn | 可用于认证的剩余秒数，最多60秒 | 60 |
| - path | 同一服务上的 WebSocket 路径，包含部署路径 | /api/v3/realtime |
| - channel | 订阅类型，固定为 chat | chat |

> 请求体可省略或为 {}，不接受用户、房间、频道、scope 或有效期等自定义字段。每次请求签发新票据，无需 Idempotency-Key；最多成功使用一次。
>
> 无访问凭据返回 HTTP 401，缺少 content.read 返回 HTTP 403，多余字段返回 HTTP 422；实时聊天室服务暂不可用返回 HTTP 503（realtime_unavailable）。

### 连接聊天室通道

`WS /api/v3/realtime`

使用票据响应中的 path 连接，等待 authentication.required 后发送 authenticate，认证成功返回 ready。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | 认证消息固定为 authenticate | authenticate |
| ticket | 上一步返回的一次性聊天室票据 | OPAQUE_TICKET |

请求示例：

```bash
SOCKET_TICKET='OPAQUE_TICKET' node --input-type=module <<'JS'
const socket = new WebSocket('wss://fishpi.cn/api/v3/realtime');
socket.addEventListener('message', ({ data }) => {
  const message = JSON.parse(data);
  if (message.type === 'authentication.required') {
    socket.send(JSON.stringify({ type: 'authenticate', ticket: process.env.SOCKET_TICKET }));
  } else if (message.type === 'chat.sync_required') {
    console.log('丢弃旧缓存并重新读取当前消息窗口');
  } else if (message.type.startsWith('chat.message.')) {
    console.log(message.type, message.data.messageId);
  } else if (message.type === 'chat.topic_changed') {
    console.log('重新读取 GET /api/v3/chat/topic');
  }
});
JS
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | authentication.required、ready、pong、chat.sync_required、chat.topic_changed 或下列消息变更类型 | ready |
| data | 消息数据；authentication.required、pong、chat.sync_required 为空对象 | `{...}` |
| - channel | ready 中的订阅类型 | chat |
| - expiresAt | ready 中的连接授权到期时间，毫秒时间戳 | 1792304100000 |
| - messageId | chat.message.* 中的目标消息 ID | 1792304100000 |
| - revision | chat.message.* 或 chat.topic_changed 中的变更版本，字符串 | `"123"` |

> 变更类型为 chat.message.created、chat.message.updated、chat.message.deleted、chat.message.reactions，分别表示消息创建、内容/卡片更新、撤回及 emoji reaction 变化。
>
> 创建或更新事件可携带 data.message，表态事件可携带 data.reactions。表态快照包含当前连接所属账号的选择。快照缺失时按消息 ID 补读相应接口。撤回后移除对应缓存。
>
> chat.topic_changed 的 data 仅包含 revision；收到后重新读取 GET /api/v3/chat/topic，不按消息 ID 查询。客户端应忽略无法识别的提示类型。
>
> 首次连接、重连后应主动读取 HTTP 状态。收到 chat.sync_required 时，清除旧消息缓存及版本去重状态，重新同步当前窗口。提示可能重复，不提供按版本断点续传；不能只依赖提示恢复历史状态。
>
> 地址不带查询参数，认证仅提交 type 和 ticket；成功后可发送 {"type":"ping"}，收到 pong。票据重放/过期、非法消息、令牌或账号失效时连接关闭；刷新访问令牌不会延长已有连接，需重新取票连接。
>
> 本订阅支持已保存消息、emoji reaction、话题变更提示及即时 chat.barrager 弹幕事件。发送仍使用 HTTP 接口；不提供在线人数事件。

### 接收即时弹幕

`WS /api/v3/realtime`

使用 chat 票据认证后接收当前连接所在实例的 `chat.barrager` 事件。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | 首帧认证类型 | authenticate |
| ticket | 从聊天室取票接口获得的票据 | {ticket} |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/chat/realtime-ticket' \
  --header 'Authorization: Bearer {accessToken}' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | 即时弹幕类型 | chat.barrager |
| data | 弹幕公开内容 | |
| - id | 弹幕标识 | 00000000-0000-4000-8000-000000000001 |
| - content | 纯文本正文 | 今天也要开心 😀 |
| - color | #RRGGBB颜色 | #66CCFF |
| - createdAt | 受理时间，毫秒时间戳 | 1792304100000 |
| - author | 发送时的公开作者资料 | |
| -- id | 作者ID | 123 |
| -- userName | 用户名 | Fish |
| -- userNickname | 昵称 | 摸鱼人 |
| -- userAvatarURL | 头像地址，可为空 | https://file.fishpi.cn/avatar.png |

> 沿用上节的连接及认证步骤。此事件没有 revision，不加入聊天历史游标；重连或收到 chat.sync_required 时不补播。显示昵称和正文时使用文本节点，并按 id 去重；客户端应忽略未知事件类型。

## API v3 文章实时订阅

### 获取文章连接票据

`POST /api/v3/articles/{articleId}/realtime-ticket`

订阅一篇当前可阅读文章的变更提示，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 content.read | Bearer ACCESS_TOKEN |
| articleId | 路径参数，文章 ID | 1636516552191 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/articles/1636516552191/realtime-ticket' \
  --header 'Authorization: Bearer ACCESS_TOKEN' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 连接凭据 | `{...}` |
| - ticket | 一次性连接票据 | OPAQUE_TICKET |
| - expiresIn | 可用于认证的剩余秒数，最多60秒 | 60 |
| - path | 同一服务上的 WebSocket 路径，包含部署路径 | /api/v3/realtime |
| - channel | 订阅类型，固定为 article | article |
| - articleId | 本票据绑定的文章 ID | 1636516552191 |

> 请求体可省略或为 `{}`，不接受自定义用户、频道、文章、scope 或有效期。每次请求签发新票据，无需 Idempotency-Key；票据最多成功使用一次，不能在连接中更换文章。
>
> 无访问凭据返回 HTTP 401，缺少 content.read 返回 HTTP 403，文章不存在或无权阅读返回 HTTP 404，非法文章 ID 或多余字段返回 HTTP 422；实时文章服务暂不可用返回 HTTP 503（realtime_unavailable）。

### 连接文章通道

`WS /api/v3/realtime`

使用文章票据响应的 path 连接。收到 authentication.required 后发送 authenticate，认证成功收到 ready；后续收到 article.changed 时重新读取文章、评论或 reaction。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | 认证消息固定为 authenticate | authenticate |
| ticket | 上一步取得的文章票据 | OPAQUE_TICKET |

请求示例：

```bash
SOCKET_TICKET='OPAQUE_TICKET' node --input-type=module <<'JS'
const socket = new WebSocket('wss://fishpi.cn/api/v3/realtime');
socket.addEventListener('message', ({ data }) => {
  const message = JSON.parse(data);
  if (message.type === 'authentication.required') {
    socket.send(JSON.stringify({ type: 'authenticate', ticket: process.env.SOCKET_TICKET }));
  } else if (message.type === 'article.changed') {
    console.log('请重新读取文章及评论', message.data.articleId);
  } else {
    console.log(message);
  }
});
JS
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | authentication.required、ready、pong 或 article.changed | ready |
| data | 消息数据；authentication.required 与 pong 为空对象 | `{...}` |
| - channel | ready 中的订阅类型 | article |
| - articleId | ready 与 article.changed 中的目标文章 ID | 1636516552191 |
| - expiresAt | ready 中的连接授权到期时间，毫秒时间戳 | 1792304100000 |

> 连接地址不带查询参数。认证成功后可发送 `{"type":"ping"}`，收到 pong。票据重放、过期、非法消息、访问令牌失效或失去文章阅读权限时连接关闭；文章删除后应重新请求 HTTP 接口确认状态。
>
> article.changed 提示文章、评论或 emoji reaction 已变化，不携带正文、评论作者或互动明细。请用同一访问身份重新读取相应 HTTP 接口。提示可能合并或重复，不提供持久化重放；首次连接及重连后应主动读取 HTTP 状态。
>
> 连接有效期不超过原访问令牌到期时间；刷新令牌不会延长已有连接，需重新取票连接。文章连接不接收通知提示或聊天消息；本接口不提供在线人数或热度事件。

## API v3 文件上传

### 获取上传票据

`POST /api/v3/uploads/tickets`

获取当前用户的短期文件上传票据，需要 uploads.write。文件随后直接上传到响应中的地址。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 uploads.write | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/uploads/tickets' \
  --header 'Authorization: Bearer ACCESS_TOKEN' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 上传凭据 | `{...}` |
| - ticket | 上传票据 | eyJ... |
| - tokenType | 上传票据类型 | Bearer |
| - uploadURL | 完整文件上传地址；使用实际响应值 | https://pic.fishpi.cn/api/v1/files |
| - fieldName | multipart 文件字段名 | file |
| - expiresIn | 票据有效秒数 | 180 |

> 请求体可省略或为 `{}`，不接受用户、目标网址、scope、有效期等自定义字段。每次请求签发新票据，无需 Idempotency-Key。取票后尽快上传；续取票据不会延长旧票据的有效期。
>
> 缺少权限返回 HTTP 403，账户不可用返回 HTTP 404，上传服务未启用或暂不可用返回 HTTP 503（upload_unavailable）。已有登录令牌需重新登录获取 uploads.write；刷新已有登录状态不会增加权限。

### 使用票据直传文件

`POST {uploadURL}`

向上一步返回的完整地址发送 multipart/form-data，请求头使用上传票据。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 上传票据 | Bearer UPLOAD_TICKET |
| file | 文件；必须提供文件名，多文件可重复该字段 | @example.png |

请求示例：

```bash
curl --request POST 'https://pic.fishpi.cn/api/v1/files' \
  --header 'Authorization: Bearer UPLOAD_TICKET' \
  --form 'file=@example.png'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 图床返回的结果码 / 提示 | 0 |
| data | 上传结果 | `{...}` |
| - succMap | 原文件名到文件网址的映射 | `{"example.png":"https://file.fishpi.cn/..."}` |
| - errFiles | 上传失败的文件名列表 | `[]` |

> 图床响应沿用其上传协议，文件格式、大小及失败响应以图床服务为准。直传请求使用 ticket，不能使用 Rhythm 的 accessToken；uploadURL 已包含 `/api/v1/files`，无需再追加路径。裸 Blob 也应提供文件名，例如 `form.append('file', blob, 'example.png')`。

## API v3 活动奖励

### 查询昨日活跃度奖励

`GET /api/v3/me/activity/yesterday-reward`

查询当前账号昨日活跃度可兑换的积分及今日领取状态，需要 activity.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/activity/yesterday-reward' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 奖励状态 | `{...}` |
| - date | 奖励对应的昨日日期，按服务端日期计算 | 20261016 |
| - amount | 按昨日活跃度计算的奖励积分，无可用记录时为 0 | 50 |
| - claimed | 今日是否已领取这类奖励 | false |
| - claimable | 当前是否可领取 | true |

> 查询不会领取奖励。amount 是该日计算出的奖励，即使已领取也保留此数值；请用 claimable 判断是否可领取。

### 领取昨日活跃度奖励

`POST /api/v3/me/activity/yesterday-reward`

领取当前账号的昨日活跃度奖励，需要 activity.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次领取重试保持不变 | reward-20261017-001 |
| expectedDate | 可选，查询返回的奖励日期，8位YYYYMMDD；仅校验当前可领日期 | 20261016 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/activity/yesterday-reward' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: reward-20261017-001' \
  --header 'Content-Type: application/json' \
  --data '{"expectedDate":"20261016"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 领取结果 | `{...}` |
| - date | 奖励对应的昨日日期 | 20261016 |
| - amount | 本次获得积分 | 50 |
| - claimed | 已领取 | true |

> 无可领取奖励或今日已经领取时返回 HTTP 409。成功请求同键重试返回原结果，不再发放积分；不同日期的领取请使用新的键。接口不接受指定账号或积分数量。expectedDate 不用于选择历史奖励；日期已变化且原请求尚未执行时返回 HTTP 409（reward_date_changed），请重新查询后决定。已提交请求的同键同内容重试仍返回原日期回执。省略 expectedDate 时沿服务端当前日期领取；同键不能更换 expectedDate。升级后请重新登录获取 activity.write 权限。日常签到仍由活跃度自动触发，可用 `GET /api/v3/me/checkin` 查询。

## API v3 专栏管理

### 修改专栏名称

`PUT /api/v3/columns/{columnId}/title`

修改当前账号的有效专栏，需要 content.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次操作重试保持不变 | column-title-001 |
| columnId | 路径参数，专栏 ID | 20261016001 |
| title | 新名称，去除首尾空白，1–64 字符 | 我的专栏 |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/columns/20261016001/title' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: column-title-001' \
  --header 'Content-Type: application/json' \
  --data '{"title":"我的专栏"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 修改后的专栏摘要 | `{...}` |
| - id / title | 专栏 ID / 名称 | 20261016001 / 我的专栏 |
| - order | 手动排序位置，越小越靠前 | 0 |
| - coverURL | 封面地址，可为空 | /images/cover.png |
| - articleCount | 章节数 | 3 |
| - createdAt / updatedAt | 创建 / 更新时间，毫秒 | 1792108800000 |

> 专栏不存在或不属于当前账号时返回 HTTP 404；名称冲突返回 HTTP 409。写接口支持 Idempotency-Key，同键更换请求内容返回 HTTP 409。

### 修改或清空专栏封面

`PUT /api/v3/columns/{columnId}/cover`

修改当前账号专栏封面，需要 content.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次操作重试保持不变 | column-cover-001 |
| columnId | 路径参数，专栏 ID | 20261016001 |
| coverURL | 必填，最多 1024 字符，HTTP/HTTPS 地址或以单个 / 开头的站内路径；空字符串清空 | /images/cover.png |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/columns/20261016001/cover' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: column-cover-001' \
  --header 'Content-Type: application/json' \
  --data '{"coverURL":"/images/cover.png"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 专栏摘要，字段同“修改专栏名称” | `{...}` |

> 缺少 coverURL 不等同于清空。地址格式不合法时返回 HTTP 422。

### 调整个人专栏顺序

`PUT /api/v3/me/columns/order`

按提交顺序设置专栏位置，需要 content.write。未提交的专栏保持原排序值。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次操作重试保持不变 | column-order-001 |
| columnIds | 1–200 个不重复的专栏 ID 字符串，每个为 1–19 位数字，均须属于当前账号 | ["20261016002","20261016001"] |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/columns/order' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: column-order-001' \
  --header 'Content-Type: application/json' \
  --data '{"columnIds":["20261016002","20261016001"]}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 排序结果 | `{...}` |
| - columnIds | 本次调整的 ID 顺序，对应位置从 0 开始 | ["20261016002","20261016001"] |

> 任一专栏不可操作时整次排序失败，不会部分保存。重复 ID、空数组或超出数量限制返回 HTTP 422。

### 删除空专栏

`DELETE /api/v3/columns/{columnId}`

删除当前账号的空专栏，需要 content.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次操作重试保持不变 | column-delete-001 |
| columnId | 路径参数，专栏 ID | 20261016001 |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/columns/20261016001' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: column-delete-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 删除结果 | `{...}` |
| - id | 专栏 ID | 20261016001 |
| - deleted | 已删除 | true |

> 含有章节时返回 HTTP 409，请先移出章节。重试成功删除的原请求须保持同一 Idempotency-Key；换用新键访问已删除专栏返回 HTTP 404。

## API v3 草稿写入

### 创建草稿

`POST /api/v3/me/drafts`

保存一个新草稿，支持普通文章、长篇和思绪草稿。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，需要 content.write | Bearer TOKEN |
| Idempotency-Key | 请求头；重试同一次操作保持不变，1–128 个英文字母、数字或 `._:-` | 550e8400-e29b-41d4-a716-446655440000 |
| title | 标题，最多 255 字符，默认空 | 草稿标题 |
| content | 编辑器正文，最多 1024000 字符，默认空 | 正文 |
| thoughtContent | 思绪记录，最多 1024000 字符，默认空 | [] |
| tags | 标签输入，最多 255 字符，默认空 | Java,生活 |
| type | 文章类型，默认 0，与现有 articleType 一致 | 6 |
| columnId | 专栏 ID，最多 19 位数字，可为空 | 20261016001 |
| columnTitle | 专栏标题，最多 64 字符，可为空 | 我的专栏 |
| chapterNo | 章节号输入，最多 32 字符，可为空 | 1 |
| rewardContent | 打赏正文，最多 102400 字符，默认空 | 补充内容 |
| rewardPoint | 非负整数打赏积分，默认 0 | 0 |
| qnaOfferPoint | 非负整数悬赏积分，默认 0 | 0 |
| commentable | 是否允许评论，默认 true | true |
| anonymous | 是否匿名发表，默认 false | false |
| notifyFollowers | 是否通知关注者，默认 false | false |
| showInList | 列表展示选项，0 或 1，默认 1 | 1 |
| statement | 文章声明，0 无、1 AI、2 剧透、3 虚构，默认 0 | 0 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/drafts' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000' \
  --data '{"title":"我的草稿","content":"正文","thoughtContent":"[]","type":3}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 新草稿摘要，字段与草稿列表中的单项一致 |
| - id / title / summary | 草稿 ID / 标题 / 文本摘要 |
| - tags / type | 标签 / 文章类型 |
| - columnId / columnTitle / chapterNo | 专栏 ID / 标题 / 章节号 |
| - createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |

> 使用 JSON 请求体，最大 8 MiB；仅接受表中列出的正文参数，数字和布尔字段需使用对应 JSON 类型。草稿正文是编辑器源内容，保存草稿不会发布文章。成功请求使用相同幂等键重试返回原结果；同一操作使用相同键提交不同内容返回 HTTP 409。变更内容后应使用新键。

### 完整替换草稿

`PUT /api/v3/me/drafts/{draftId}`

完整替换当前用户的草稿。未提供的正文参数恢复创建接口中的默认值，草稿 ID 和创建时间保留。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，需要 content.write | Bearer TOKEN |
| Idempotency-Key | 请求头；同一次替换的重试使用相同键 | 550e8400-e29b-41d4-a716-446655440001 |
| draftId | 路径中的草稿 ID | 20261016003 |
| title / content / thoughtContent / tags / type | 与创建草稿相同 | 正文 |
| columnId / columnTitle / chapterNo | 与创建草稿相同 | 1 |
| rewardContent / rewardPoint / qnaOfferPoint | 与创建草稿相同 | 0 |
| commentable / anonymous / notifyFollowers / showInList / statement | 与创建草稿相同 | true |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/drafts/20261016003' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440001' \
  --data '{"title":"更新后的标题","content":"完整正文","type":0}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 更新后的草稿摘要，字段与创建草稿一致 |
| - id / createdAt / updatedAt | 原草稿 ID / 原创建时间 / 本次更新时间 |

> 草稿不存在或不属于当前账号时返回 HTTP 404。此接口为完整替换，不是局部更新；请同时提交需要保留的思绪记录、专栏信息和其他选项。

### 删除草稿

`DELETE /api/v3/me/drafts/{draftId}`

删除当前用户的草稿。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，需要 content.write | Bearer TOKEN |
| Idempotency-Key | 请求头；同一次删除的重试使用相同键 | 550e8400-e29b-41d4-a716-446655440002 |
| draftId | 草稿 ID | 20261016003 |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/me/drafts/20261016003' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440002'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 成功时 code 为 0 |
| data | 空对象 |

> 无需请求体。成功删除后用相同键重试仍成功；使用新键删除不存在或不属于当前账号的草稿返回 HTTP 404。原创建请求的重试不会重新创建后来已删除的草稿。


## API v3 通知

本模块使用 Bearer 访问令牌。读取需要 `notifications.read`，状态变更需要 `notifications.write`；升级后请重新登录获取包含相应权限的令牌。

### 通知列表

`GET /api/v3/me/notifications`

按未读优先、时间倒序分页读取当前用户通知。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，需要 notifications.read | Bearer TOKEN |
| type | all（默认）、commented、reply、at、following、point、broadcast、sys-announce、other | all |
| status | all（默认）、unread、read | unread |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/me/notifications?type=all&status=unread&p=1&size=20' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 通知数组，单项字段与通知详情一致 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 读取不会自动标记已读。相关内容不存在或不可查看时返回 contentAvailable=false 的占位通知，不提供相关资源和跳转链接。 涉及文章的通知说明使用当前可读文章的标题，目标变化后不继续展示旧标题。

### 通知未读计数

`GET /api/v3/me/notifications/unread-count`

读取当前用户的未读通知总数及分类计数。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，需要 notifications.read | Bearer TOKEN |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/me/notifications/unread-count' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 计数对象 |
| - total | 未读总数 |
| - categories | 分类计数对象 |
| -- commented / reply / at / following / point / broadcast / sys-announce / other | 各分类的未读数 |

### 通知详情

`GET /api/v3/me/notifications/{notificationId}`

读取当前用户的一条通知。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，需要 notifications.read | Bearer TOKEN |
| notificationId | 通知 ID | 20261016004 |

请求示例：

```bash
curl --request GET 'https://fishpi.cn/api/v3/me/notifications/20261016004' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 通知对象 |
| - id / type / category | 通知 ID / 数字类型 / 分类 |
| - title | 通知标题文本 |
| - hasRead / createdAt | 是否已读 / 创建时间（毫秒时间戳） |
| - description / descriptionHtml | 通知说明文本 / HTML |
| - url | 相关页面链接，无可用目标时为空 |
| - contentAvailable | 相关内容当前是否可展示 |
| - article / comment / actor | 可选的公开文章摘要 / 评论 / 操作用户资料；字段与对应 v3 资源一致 |

> 通知不存在或不属于当前账号时返回 HTTP 404。

> 历史聊天室消息缺少有效作者ID时省略 `actor`；仍可读的正文和聊天室链接保留。

> 关注、文章关注/收藏及投票通知中的操作用户资料使用当前用户名、昵称和头像；聊天室通知带有作者ID时同样使用当前资料。作者已删除时省略操作用户资料，新关注者通知则显示为不可用；仍可读的文章、评论和聊天室内容不受影响。

### 标记单条通知已读

`POST /api/v3/me/notifications/{notificationId}/read`

将当前用户的一条通知标记已读。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，需要 notifications.write | Bearer TOKEN |
| Idempotency-Key | 请求头，同一次操作重试保持不变；格式与草稿写入一致 | 550e8400-e29b-41d4-a716-446655440010 |
| notificationId | 通知 ID | 20261016004 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/notifications/20261016004/read' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440010'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 操作结果 |
| - changed | 本次原操作变更的通知数；重试返回原结果 |

> 无需请求体。通知已读时 changed=0；不存在或不属于当前账号时返回 HTTP 404。

### 批量标记通知已读

`POST /api/v3/me/notifications/read`

标记当前用户全部或指定分类的通知已读。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，需要 notifications.write | Bearer TOKEN |
| Idempotency-Key | 请求头，同一次操作重试保持不变；格式与草稿写入一致 | 550e8400-e29b-41d4-a716-446655440010 |
| type | 通知分类，与列表一致；默认 all，可省略请求体 | commented |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/notifications/read' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440010' \
  --header 'Content-Type: application/json' \
  --data '{"type":"commented"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 操作结果 |
| - changed | 本次原操作变更的通知数；重试返回原结果 |

> 成功后使用原键重试返回原结果，不会标记后来新增的通知。需要处理新通知时请使用新键。

### 删除单条通知

`DELETE /api/v3/me/notifications/{notificationId}`

删除当前用户的一条通知。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，需要 notifications.write | Bearer TOKEN |
| Idempotency-Key | 请求头，同一次操作重试保持不变；格式与草稿写入一致 | 550e8400-e29b-41d4-a716-446655440010 |
| notificationId | 通知 ID | 20261016004 |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/me/notifications/20261016004' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440010'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 操作结果 |
| - changed | 本次原操作变更的通知数；重试返回原结果 |

> 无需请求体。成功删除后使用原键重试仍成功；使用新键删除不存在或不属于当前账号的通知返回 HTTP 404。

### 按分类删除通知

`DELETE /api/v3/me/notifications`

删除当前用户指定分类的通知。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，需要 notifications.write | Bearer TOKEN |
| Idempotency-Key | 请求头，同一次操作重试保持不变；格式与草稿写入一致 | 550e8400-e29b-41d4-a716-446655440010 |
| type | 必填：commented、reply、at、following、point、broadcast、sys-announce 或 other | point |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/me/notifications' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440010' \
  --header 'Content-Type: application/json' \
  --data '{"type":"point"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 操作结果 |
| - changed | 本次原操作变更的通知数；重试返回原结果 |

> 需要 JSON 请求体及明确分类，不支持 all。成功后的重试不删除后来新增的通知。


## API v3 本人大事记投稿

### 提交大事记

`POST /api/v3/milestones`

提交大事记并等待审核，需要 content.write。正文使用 Markdown，渲染为清洗后的 HTML 保存。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次投稿重试保持不变 | milestone-submit-001 |
| title | 必填，去除首尾空白，最多 255 字符 | 社区活动 |
| date | 必填，YYYY-MM-DD，有效日历日期 | 2026-10-17 |
| endDate | 可选，空字符串表示无结束日期，不得早于 date | 2026-10-18 |
| content | 必填，Markdown，最多 102400 字符，渲染后也需在容量内 | **活动详情** |
| mediaURL | 可选，媒体地址，最多 1024 字符 | https://example.com/event.png |
| mediaType | image / video；提供 mediaURL 时必填 | image |
| mediaCaption | 可选，媒体标题，最多 255 字符 | 合影 |
| link | 可选，相关链接，最多 1024 字符 | https://fishpi.cn/ |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/milestones' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: milestone-submit-001' \
  --header 'Content-Type: application/json' \
  --data '{"title":"社区活动","date":"2026-10-17","content":"**活动详情**","mediaURL":"https://example.com/event.png","mediaType":"image"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 投稿回执 | `{...}` |
| - id | 投稿 ID | 20261017001 |
| - status | 初始为 pending，等待审核 | pending |
| - createdAt / updatedAt | 创建 / 更新时间，毫秒 | 1792195200000 |

> 同键重试返回原回执，不重复投稿；同键更改内容返回 HTTP 409。地址支持 HTTP/HTTPS 或站内路径。可通过本人投稿接口查询审核状态；审核通过前不会进入公开时间线。

### 查询本人投稿列表

`GET /api/v3/me/milestones`

分页查询当前账号提交的大事记，包含待审核、已通过及已拒绝记录，需要 content.read。按投稿时间倒序排列。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| status | all / pending / approved / rejected，默认 all | pending |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/milestones?status=pending&p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 投稿列表 | `{...}` |
| - list | 本人投稿数组 | `[...]` |
| -- id / title | 投稿 ID / 标题原文 | 20261017001 / 社区活动 |
| -- date / endDate | 事件开始 / 结束日期，结束日期可为空 | 2026-10-16 / 空字符串 |
| -- content | 保存后的正文；v3 投稿为已渲染的 HTML，历史记录也可能是纯文本 | `<p>活动详情</p>` |
| -- media | 媒体信息 | `{...}` |
| --- url / type / caption | 媒体地址 / image 或 video / 标题 | https://example.com/a.png / image / 合影 |
| -- link | 相关链接，可为空 | https://fishpi.cn/ |
| -- status | pending 待审核 / approved 已通过 / rejected 已拒绝 | pending |
| -- createdAt / updatedAt | 投稿创建 / 更新时间，毫秒 | 1792195200000 |
| - pagination | p / size / total / pageCount | `{...}` |

> 只返回当前账号投稿；没有记录或超出总页数时 list 为空。公开时间线仍仅展示已通过的大事记。

### 查询本人投稿详情

`GET /api/v3/me/milestones/{milestoneId}`

读取本人指定投稿及当前状态，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| milestoneId | 路径参数，投稿 ID | 20261017001 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/milestones/20261017001' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 投稿对象，字段同列表中的单项 | `{...}` |

> 不存在或不属于当前账号的记录均返回 HTTP 404。

## API v3 社区信息

### 鱼游列表

`GET /api/v3/fish-games`

分页读取已审核的鱼游。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| sort | votes（默认，总投票数优先）、likes（点赞数优先） | votes |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/fish-games'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 鱼游数组 |
| -- id / name / description | 鱼游 ID / 名称 / 描述文本 |
| -- url / iconURL | 游戏链接 / 图标链接 |
| -- author | 公开作者资料，包含 id、userName、userNickname、userAvatarURL |
| -- upCount / downCount | 点赞数 / 点踩数 |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| -- userVote | 当前阅读者投票：like、dislike 或空字符串 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 描述为文本；需要当前投票状态时携带 Bearer。

### 鱼游详情

`GET /api/v3/fish-games/{gameId}`

读取已审核的鱼游。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| gameId | 鱼游 ID | 20261016005 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/fish-games/20261016005'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 鱼游对象，字段与列表单项一致 |

> 不存在或未审核通过的鱼游返回 HTTP 404。

### 鱼游评论列表

`GET /api/v3/fish-games/{gameId}/comments`

按时间倒序分页读取鱼游评论。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| gameId | 鱼游 ID | 20261016005 |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/fish-games/20261016005/comments'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 评论数组 |
| -- id / gameId | 评论 ID / 鱼游 ID |
| -- content | 正文文本 |
| -- contentAvailable | 正文是否可展示 |
| -- author | 公开作者资料 |
| -- createdAt | 创建时间，毫秒时间戳 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 父鱼游不存在或未审核通过时返回 HTTP 404。

### 大事记列表

`GET /api/v3/milestones`

分页读取已审核的大事记，按日期倒序。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/milestones'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 大事记数组 |
| -- id / title | 大事记 ID / 标题文本 |
| -- date / endDate | 开始 / 结束日期，YYYY-MM-DD；无结束日期时为空 |
| -- content / contentHtml | 正文文本 / HTML |
| -- link | 相关链接 |
| -- media | 媒体对象 |
| --- url / type / caption | 媒体链接 / 类型（image 或 video）/ 说明文本 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

### 大事记详情

`GET /api/v3/milestones/{milestoneId}`

读取已审核的大事记。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| milestoneId | 大事记 ID | 20261016006 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/milestones/20261016006'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 大事记对象，字段与列表单项一致 |

> 不存在或未审核通过时返回 HTTP 404。

### 社区统计

`GET /api/v3/statistics`

读取社区公开统计。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/statistics'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 统计对象 |
| - memberCount / articleCount / commentCount | 用户 / 文章 / 评论数 |
| - domainCount / tagCount / linkCount | 领域 / 标签 / 链接数 |
| - onlineVisitorCount / onlineMemberCount | 当前在线总数 / 在线成员数 |
| - peakOnlineVisitorCount | 历史最高在线总数 |

### 同城用户

`GET /api/v3/cities/{cityName}/users`

分页读取公开位置的同城用户。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 profile.read | Bearer TOKEN |
| cityName | 城市名，最多 64 字符，路径需 URL 编码 | 上海 |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/cities/%E4%B8%8A%E6%B5%B7/users'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 用户数组 |
| -- id / userName / userNickname / userAvatarURL | 用户 ID / 用户名 / 昵称 / 头像 |
| -- userIntro | 个人介绍文本 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

### 同城文章

`GET /api/v3/cities/{cityName}/articles`

分页读取同城公开署名文章，包含长篇。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选），使用令牌时需要 content.read | Bearer TOKEN |
| cityName | 城市名，最多 64 字符，路径需 URL 编码 | 上海 |
| sort | latest（默认）、active、hot、comments | latest |
| p | 页码，默认 1，最大 42949672 | 1 |
| size | 每页数量，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/cities/%E4%B8%8A%E6%B5%B7/articles'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表数据 |
| - list | 文章摘要数组，字段与文章列表一致 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 只包含作者公开位置的文章，不包含匿名发表、讨论帖或不可列出的文章。


### 设置鱼游投票

`PUT /api/v3/fish-games/{gameId}/vote`

设置当前账号对已公开鱼游的投票，需要 reactions.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | game-vote-1 |
| gameId | URL 参数，鱼游 ID | 1770000000000 |
| value | like 点赞、dislike 点踩 | like |

请求示例：

```bash
curl --location --request PUT 'https://fishpi.cn/api/v3/fish-games/1770000000000/vote' \
  --header 'Authorization: Bearer TOKEN' --header 'Idempotency-Key: game-vote-1' \
  --header 'Content-Type: application/json' --data '{"value":"like"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 投票结果 |
| - upCount / downCount | 当前点赞数 / 点踩数 |
| - userVote | 当前账号投票，like、dislike 或空字符串 |
| - changed | 本次首次执行是否改变投票状态 |

> 同值重复设置不会取消投票，changed=false；设置另一值会换票。同键重试返回原结果，不改变后续投票状态；同键使用不同值返回 HTTP 409。鱼游不存在或尚未公开返回 HTTP 404。

### 清除鱼游投票

`DELETE /api/v3/fish-games/{gameId}/vote`

清除当前账号的投票，需要 reactions.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | game-vote-clear-1 |
| gameId | URL 参数，鱼游 ID | 1770000000000 |

请求示例：

```bash
curl --location --request DELETE 'https://fishpi.cn/api/v3/fish-games/1770000000000/vote' \
  --header 'Authorization: Bearer TOKEN' --header 'Idempotency-Key: game-vote-clear-1'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 投票结果 |
| - upCount / downCount | 当前点赞数 / 点踩数 |
| - userVote | 空字符串 |
| - changed | 是否清除了已有投票 |

> 请求体可省略或为 {}。没有投票时成功返回 changed=false；设置与清除请使用不同幂等键，否则返回 HTTP 409。目标必须已经公开。

### 发表鱼游评论

`POST /api/v3/fish-games/{gameId}/comments`

对已公开鱼游发表评论，需要 comments.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | game-comment-1 |
| gameId | URL 参数，鱼游 ID | 1770000000000 |
| content | 评论正文；去除 HTML 标签和首尾空白后 1–500 字符 | 很有趣 |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/v3/fish-games/1770000000000/comments' \
  --header 'Authorization: Bearer TOKEN' --header 'Idempotency-Key: game-comment-1' \
  --header 'Content-Type: application/json' --data '{"content":"很有趣"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 已创建的评论 |
| - id / gameId | 评论 ID / 鱼游 ID |
| - content / contentAvailable | 评论文本 / 当前是否可展示 |
| - author | 公开作者资料 |
| -- id / userName / userNickname / userAvatarURL | 用户 ID / 用户名 / 昵称 / 头像 |
| - createdAt | 创建时间，毫秒时间戳 |

> 同键重试返回原评论，不会重复发布；同键提交不同正文返回 HTTP 409。目标不存在或尚未公开返回 HTTP 404；非法内容返回 HTTP 422。

## API v3 表态

### 可用表情

`GET /api/v3/reactions/emojis`

读取可用于表态的 value 与 emoji 对照。可携带 Bearer 访问令牌（content.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选） | Bearer TOKEN |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/reactions/emojis'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 结果 |
| - list | 可用表情数组 |
| -- value / emoji | 提交值 / 对应表情，如 heart / ❤️ |

### 读取文章表态

`GET /api/v3/articles/{targetId}/reactions`

读取当前可访问文章的表态汇总。可携带 Bearer 访问令牌（content.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选） | Bearer TOKEN |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/articles/1770000000000/reactions'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 表态结果 |
| - targetId / targetType / groupType | 目标 ID / article、comment 或 chat / 固定 emoji |
| - currentUserReaction | 当前账号的 value，无表态为空字符串；匿名读取为空字符串 |
| - summary | 表情汇总数组，仅包含数量大于零的表情 |
| -- value / emoji / count / selected | 提交值 / 表情 / 数量 / 当前账号是否选择 |
| -- users | 展示名称数组 |
| -- userDetails | 公开用户展示资料数组，仅含 userName、displayName、avatarURL |

> 目标不存在或当前不可访问时返回 HTTP 404。

### 设置文章表态

`PUT /api/v3/articles/{targetId}/reactions`

设置当前账号对该文章的表情，需要 reactions.write。同一账号、同一目标同时保留一个表情。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | reaction-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |
| value | 可用表情的 value，见可用表情接口 | heart |

请求示例：

```bash
curl --location --request PUT 'https://fishpi.cn/api/v3/articles/1770000000000/reactions' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: reaction-example-1' \
  --header 'Content-Type: application/json' --data '{"value":"heart"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 表态结果 |
| - targetId / targetType / groupType | 目标 ID / article、comment 或 chat / 固定 emoji |
| - currentUserReaction | 当前账号的 value，无表态为空字符串；匿名读取为空字符串 |
| - summary | 表情汇总数组，仅包含数量大于零的表情 |
| -- value / emoji / count / selected | 提交值 / 表情 / 数量 / 当前账号是否选择 |
| -- users | 展示名称数组 |
| -- userDetails | 公开用户展示资料数组，仅含 userName、displayName、avatarURL |
| - changed | 本次首次执行是否改变表态状态 |

> 目标不存在或当前不可访问时返回 HTTP 404。相同幂等键重试返回原结果，不会覆盖后来更改的状态；同一目标设置与清除请使用不同键，否则返回 HTTP 409。重复设置相同 value 不会取消表态，changed=false；设置其他 value 会替换原表情。

> 成功响应可包含 `data.interaction`，表示当前文章表态状态。已不可查看时为 null，旧服务或状态补查失败时可能省略该字段。


### 清除文章表态

`DELETE /api/v3/articles/{targetId}/reactions`

清除当前账号对该文章的表情，需要 reactions.write。同一账号、同一目标同时保留一个表情。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | reaction-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request DELETE 'https://fishpi.cn/api/v3/articles/1770000000000/reactions' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: reaction-example-1'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 表态结果 |
| - targetId / targetType / groupType | 目标 ID / article、comment 或 chat / 固定 emoji |
| - currentUserReaction | 当前账号的 value，无表态为空字符串；匿名读取为空字符串 |
| - summary | 表情汇总数组，仅包含数量大于零的表情 |
| -- value / emoji / count / selected | 提交值 / 表情 / 数量 / 当前账号是否选择 |
| -- users | 展示名称数组 |
| -- userDetails | 公开用户展示资料数组，仅含 userName、displayName、avatarURL |
| - changed | 本次首次执行是否改变表态状态 |

> 目标不存在或当前不可访问时返回 HTTP 404。相同幂等键重试返回原结果，不会覆盖后来更改的状态；同一目标设置与清除请使用不同键，否则返回 HTTP 409。请求体可省略或为 {}；没有表态时成功返回 changed=false。

> 成功响应可包含 `data.interaction`，表示当前文章表态状态。已不可查看时为 null，旧服务或状态补查失败时可能省略该字段。


### 读取评论表态

`GET /api/v3/comments/{targetId}/reactions`

读取当前可访问评论的表态汇总。可携带 Bearer 访问令牌（content.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌（可选） | Bearer TOKEN |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/comments/1770000000000/reactions'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 表态结果 |
| - targetId / targetType / groupType | 目标 ID / article、comment 或 chat / 固定 emoji |
| - currentUserReaction | 当前账号的 value，无表态为空字符串；匿名读取为空字符串 |
| - summary | 表情汇总数组，仅包含数量大于零的表情 |
| -- value / emoji / count / selected | 提交值 / 表情 / 数量 / 当前账号是否选择 |
| -- users | 展示名称数组 |
| -- userDetails | 公开用户展示资料数组，仅含 userName、displayName、avatarURL |

> 目标不存在或当前不可访问时返回 HTTP 404。评论表态同样遵循目标文章的阅读权限，私密评论仅有权阅读正文的账号可访问。

### 设置评论表态

`PUT /api/v3/comments/{targetId}/reactions`

设置当前账号对该评论的表情，需要 reactions.write。同一账号、同一目标同时保留一个表情。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | reaction-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |
| value | 可用表情的 value，见可用表情接口 | heart |

请求示例：

```bash
curl --location --request PUT 'https://fishpi.cn/api/v3/comments/1770000000000/reactions' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: reaction-example-1' \
  --header 'Content-Type: application/json' --data '{"value":"heart"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 表态结果 |
| - targetId / targetType / groupType | 目标 ID / article、comment 或 chat / 固定 emoji |
| - currentUserReaction | 当前账号的 value，无表态为空字符串；匿名读取为空字符串 |
| - summary | 表情汇总数组，仅包含数量大于零的表情 |
| -- value / emoji / count / selected | 提交值 / 表情 / 数量 / 当前账号是否选择 |
| -- users | 展示名称数组 |
| -- userDetails | 公开用户展示资料数组，仅含 userName、displayName、avatarURL |
| - changed | 本次首次执行是否改变表态状态 |

> 目标不存在或当前不可访问时返回 HTTP 404。评论表态同样遵循目标文章的阅读权限，私密评论仅有权阅读正文的账号可访问。相同幂等键重试返回原结果，不会覆盖后来更改的状态；同一目标设置与清除请使用不同键，否则返回 HTTP 409。重复设置相同 value 不会取消表态，changed=false；设置其他 value 会替换原表情。

> 写入成功时，`data.interaction` 返回当前评论互动状态，字段同对应单条读取接口。相同幂等键保留原操作结果，当前状态可能已变化。目标已不可读时，`data.interaction` 为 null。状态补查失败时省略该字段。

### 清除评论表态

`DELETE /api/v3/comments/{targetId}/reactions`

清除当前账号对该评论的表情，需要 reactions.write。同一账号、同一目标同时保留一个表情。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | reaction-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request DELETE 'https://fishpi.cn/api/v3/comments/1770000000000/reactions' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: reaction-example-1'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 表态结果 |
| - targetId / targetType / groupType | 目标 ID / article、comment 或 chat / 固定 emoji |
| - currentUserReaction | 当前账号的 value，无表态为空字符串；匿名读取为空字符串 |
| - summary | 表情汇总数组，仅包含数量大于零的表情 |
| -- value / emoji / count / selected | 提交值 / 表情 / 数量 / 当前账号是否选择 |
| -- users | 展示名称数组 |
| -- userDetails | 公开用户展示资料数组，仅含 userName、displayName、avatarURL |
| - changed | 本次首次执行是否改变表态状态 |

> 目标不存在或当前不可访问时返回 HTTP 404。评论表态同样遵循目标文章的阅读权限，私密评论仅有权阅读正文的账号可访问。相同幂等键重试返回原结果，不会覆盖后来更改的状态；同一目标设置与清除请使用不同键，否则返回 HTTP 409。请求体可省略或为 {}；没有表态时成功返回 changed=false。

> 写入成功时，`data.interaction` 返回当前评论互动状态，字段同对应单条读取接口。相同幂等键保留原操作结果，当前状态可能已变化。目标已不可读时，`data.interaction` 为 null。状态补查失败时省略该字段。

### 读取聊天室消息表态

`GET /api/v3/chat/messages/{targetId}/reactions`

读取当前可访问聊天室消息的表态汇总。需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/chat/messages/1770000000000/reactions' \
  --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 表态结果 |
| - targetId / targetType / groupType | 目标 ID / article、comment 或 chat / 固定 emoji |
| - currentUserReaction | 当前账号的 value，无表态为空字符串；匿名读取为空字符串 |
| - summary | 表情汇总数组，仅包含数量大于零的表情 |
| -- value / emoji / count / selected | 提交值 / 表情 / 数量 / 当前账号是否选择 |
| -- users | 展示名称数组 |
| -- userDetails | 公开用户展示资料数组，仅含 userName、displayName、avatarURL |

> 目标不存在或当前不可访问时返回 HTTP 404。

### 设置聊天室消息表态

`PUT /api/v3/chat/messages/{targetId}/reactions`

设置当前账号对该聊天室消息的表情，需要 reactions.write。同一账号、同一目标同时保留一个表情。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | reaction-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |
| value | 可用表情的 value，见可用表情接口 | heart |

请求示例：

```bash
curl --location --request PUT 'https://fishpi.cn/api/v3/chat/messages/1770000000000/reactions' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: reaction-example-1' \
  --header 'Content-Type: application/json' --data '{"value":"heart"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 表态结果 |
| - targetId / targetType / groupType | 目标 ID / article、comment 或 chat / 固定 emoji |
| - currentUserReaction | 当前账号的 value，无表态为空字符串；匿名读取为空字符串 |
| - summary | 表情汇总数组，仅包含数量大于零的表情 |
| -- value / emoji / count / selected | 提交值 / 表情 / 数量 / 当前账号是否选择 |
| -- users | 展示名称数组 |
| -- userDetails | 公开用户展示资料数组，仅含 userName、displayName、avatarURL |
| - changed | 本次首次执行是否改变表态状态 |

> 目标不存在或当前不可访问时返回 HTTP 404。相同幂等键重试返回原结果，不会覆盖后来更改的状态；同一目标设置与清除请使用不同键，否则返回 HTTP 409。重复设置相同 value 不会取消表态，changed=false；设置其他 value 会替换原表情。

> 成功回执可带 data.interaction，表示当前可见的表态状态。目标已不可查看时该字段为 null。状态补查失败时省略该字段。


### 清除聊天室消息表态

`DELETE /api/v3/chat/messages/{targetId}/reactions`

清除当前账号对该聊天室消息的表情，需要 reactions.write。同一账号、同一目标同时保留一个表情。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | reaction-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request DELETE 'https://fishpi.cn/api/v3/chat/messages/1770000000000/reactions' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: reaction-example-1'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 表态结果 |
| - targetId / targetType / groupType | 目标 ID / article、comment 或 chat / 固定 emoji |
| - currentUserReaction | 当前账号的 value，无表态为空字符串；匿名读取为空字符串 |
| - summary | 表情汇总数组，仅包含数量大于零的表情 |
| -- value / emoji / count / selected | 提交值 / 表情 / 数量 / 当前账号是否选择 |
| -- users | 展示名称数组 |
| -- userDetails | 公开用户展示资料数组，仅含 userName、displayName、avatarURL |
| - changed | 本次首次执行是否改变表态状态 |

> 目标不存在或当前不可访问时返回 HTTP 404。相同幂等键重试返回原结果，不会覆盖后来更改的状态；同一目标设置与清除请使用不同键，否则返回 HTTP 409。请求体可省略或为 {}；没有表态时成功返回 changed=false。

## API v3 清风明月写入与详情

> 成功回执可带 data.interaction，表示当前可见的表态状态。目标已不可查看时该字段为 null。状态补查失败时省略该字段。


### 发布清风明月

`POST /api/v3/breezemoons`

发布一条清风明月，需要 `content.write`、账号具备发布权限且已绑定手机号码。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | breezemoon-create-1 |
| content | 正文，去除首尾空白后 1–512 字符；HTML 标签会被移除 | 今日晴，适合摸鱼 |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/v3/breezemoons' \
  --header 'Authorization: Bearer TOKEN' --header 'Idempotency-Key: breezemoon-create-1' \
  --header 'Content-Type: application/json' --data '{"content":"今日晴，适合摸鱼"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 已发布记录 |
| - id / content / contentHtml | ID / 正文 / 渲染正文 |
| - createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| - author | 公开作者资料 |
| -- id / userName / userNickname / userAvatarURL | 用户 ID / 用户名 / 昵称 / 头像 |

> 审核未通过返回 HTTP 422，未绑定手机返回 HTTP 403，操作过于频繁返回 HTTP 429。相同幂等键与正文重试返回原记录，不会重复发布；同键使用不同正文返回 HTTP 409。清风明月列表设为不公开时仍可发布，但不会进入全站公开列表。

### 清风明月详情

`GET /api/v3/breezemoons/{breezemoonId}`

读取一条当前可访问的清风明月。可携带 Bearer 访问令牌（`content.read`）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| breezemoonId | URL 参数，清风明月 ID | 1770000000000 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/breezemoons/1770000000000'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 清风明月记录 |
| - id / content / contentHtml | ID / 正文 / 渲染正文 |
| - createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| - author | 公开作者资料 |
| -- id / userName / userNickname / userAvatarURL | 用户 ID / 用户名 / 昵称 / 头像 |

> 遵循作者的清风明月列表公开设置；本人或具备相应权限的账号可读取私密列表记录。不存在、不可访问或已失效的记录返回 HTTP 404。

### 编辑本人的清风明月

`PUT /api/v3/breezemoons/{breezemoonId}`

替换本人清风明月的正文，需要 `content.write` 和编辑权限。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | breezemoon-edit-1 |
| breezemoonId | URL 参数，清风明月 ID | 1770000000000 |
| content | 正文，去除首尾空白后 1–512 字符 | 更新后的正文 |

请求示例：

```bash
curl --location --request PUT 'https://fishpi.cn/api/v3/breezemoons/1770000000000' \
  --header 'Authorization: Bearer TOKEN' --header 'Idempotency-Key: breezemoon-edit-1' \
  --header 'Content-Type: application/json' --data '{"content":"更新后的正文"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 更新后的记录 |
| - id / content / contentHtml | ID / 正文源文本 / 清洗后的渲染正文 |
| - createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| - author | 公开作者资料 |
| -- id / userName / userNickname / userAvatarURL | 用户 ID / 用户名 / 昵称 / 头像 |

> 沿用原编辑规则，保留正文源标记；页面展示请使用清洗后的 contentHtml，或把 content 当作文本。审核未通过返回 HTTP 422；不存在、非本人或失效记录返回 HTTP 404。编辑不改变作者和创建时间。同键重试返回原编辑结果，不会覆盖后续编辑；同键提交不同正文返回 HTTP 409。

### 本人的清风明月关注流

`GET /api/v3/me/watching/breezemoons`

分页读取当前账号关注用户的公开清风明月，需要 `content.read`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/me/watching/breezemoons?p=1&size=20' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表结果 |
| - list | 清风明月记录，按发布时间倒序 |
| -- id / content / contentHtml | ID / 正文 / 渲染正文 |
| -- createdAt / updatedAt | 创建 / 更新时间，毫秒时间戳 |
| -- author | 公开作者资料 |
| --- id / userName / userNickname / userAvatarURL | 用户 ID / 用户名 / 昵称 / 头像 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 只包含关注用户公开且有效的内容。没有关注用户或没有可展示内容时返回空列表，不回退为全站列表。

### 删除本人的清风明月

`DELETE /api/v3/breezemoons/{breezemoonId}`

删除当前账号发布的记录，需要 `content.write` 和删除权限。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | breezemoon-delete-1 |
| breezemoonId | URL 参数，清风明月 ID | 1770000000000 |

请求示例：

```bash
curl --location --request DELETE 'https://fishpi.cn/api/v3/breezemoons/1770000000000' \
  --header 'Authorization: Bearer TOKEN' --header 'Idempotency-Key: breezemoon-delete-1'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 / 空字符串 |
| data | 空对象 | `{}` |

> 请求体可省略或为 `{}`。其他账号的记录、记录不存在时返回 HTTP 404；成功后使用原幂等键重试仍成功。删除后重试原发布请求不会重新创建内容。

## API v3 关注、收藏与订阅

### 本人的关注用户列表

`GET /api/v3/me/follows/users`

读取当前账号的关注用户列表，需要 `profile.read`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/me/follows/users?p=1&size=20' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表结果 |
| - list | 用户公开资料：id、userName、userNickname、userAvatarURL、userIntro |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

### 添加关注用户

`PUT /api/v3/me/follows/users/{targetId}`

添加当前账号与目标的关系，需要 `follows.write`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | follow-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request PUT 'https://fishpi.cn/api/v3/me/follows/users/1770000000000' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: follow-example-1' \
  --header 'Content-Type: application/json' --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 / 空字符串 |
| data | 操作结果 | `{...}` |
| - following | 本次操作要求的关系状态 | true |
| - changed | 本次首次执行是否改变关系 | true |

> 请求体可省略或为 `{}`。重复设置相同状态成功返回，`changed=false`。使用相同幂等键重试返回原操作结果，不代表当前状态；同一关系的添加与取消请使用不同键，否则返回 HTTP 409。

> 目标不存在或不可访问时返回 HTTP 404；添加收藏、订阅还需账号具备相应功能权限。

### 取消关注用户

`DELETE /api/v3/me/follows/users/{targetId}`

取消当前账号与目标的关系，需要 `follows.write`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | follow-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request DELETE 'https://fishpi.cn/api/v3/me/follows/users/1770000000000' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: follow-example-1' \
  --header 'Content-Type: application/json' --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 / 空字符串 |
| data | 操作结果 | `{...}` |
| - following | 本次操作要求的关系状态 | false |
| - changed | 本次首次执行是否改变关系 | true |

> 请求体可省略或为 `{}`。重复设置相同状态成功返回，`changed=false`。使用相同幂等键重试返回原操作结果，不代表当前状态；同一关系的添加与取消请使用不同键，否则返回 HTTP 409。

> 可以清理已删除或当前不可访问目标的关系。关系不存在时成功返回 `changed=false`。

### 本人的关注标签列表

`GET /api/v3/me/follows/tags`

读取当前账号的关注标签列表，需要 `content.read`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/me/follows/tags?p=1&size=20' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表结果 |
| - list | 标签公开资料，与标签列表一致 |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

### 添加关注标签

`PUT /api/v3/me/follows/tags/{targetId}`

添加当前账号与目标的关系，需要 `follows.write`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | follow-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request PUT 'https://fishpi.cn/api/v3/me/follows/tags/1770000000000' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: follow-example-1' \
  --header 'Content-Type: application/json' --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 / 空字符串 |
| data | 操作结果 | `{...}` |
| - following | 本次操作要求的关系状态 | true |
| - changed | 本次首次执行是否改变关系 | true |

> 请求体可省略或为 `{}`。重复设置相同状态成功返回，`changed=false`。使用相同幂等键重试返回原操作结果，不代表当前状态；同一关系的添加与取消请使用不同键，否则返回 HTTP 409。

> 目标不存在或不可访问时返回 HTTP 404；添加收藏、订阅还需账号具备相应功能权限。

### 取消关注标签

`DELETE /api/v3/me/follows/tags/{targetId}`

取消当前账号与目标的关系，需要 `follows.write`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | follow-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request DELETE 'https://fishpi.cn/api/v3/me/follows/tags/1770000000000' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: follow-example-1' \
  --header 'Content-Type: application/json' --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 / 空字符串 |
| data | 操作结果 | `{...}` |
| - following | 本次操作要求的关系状态 | false |
| - changed | 本次首次执行是否改变关系 | true |

> 请求体可省略或为 `{}`。重复设置相同状态成功返回，`changed=false`。使用相同幂等键重试返回原操作结果，不代表当前状态；同一关系的添加与取消请使用不同键，否则返回 HTTP 409。

> 可以清理已删除或当前不可访问目标的关系。关系不存在时成功返回 `changed=false`。

### 本人的收藏文章列表

`GET /api/v3/me/collections`

读取当前账号的收藏文章列表，需要 `content.read`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/me/collections?p=1&size=20' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表结果 |
| - list | 文章摘要，与用户文章列表一致；另含 available |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 按添加关系的先后倒序。当前不可阅读或已删除的目标仅返回 `id` 与 `available=false`，仍可取消该关系；允许阅读的讨论帖正常返回。

### 添加收藏文章

`PUT /api/v3/me/collections/{targetId}`

添加当前账号与目标的关系，需要 `follows.write`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | follow-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request PUT 'https://fishpi.cn/api/v3/me/collections/1770000000000' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: follow-example-1' \
  --header 'Content-Type: application/json' --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 / 空字符串 |
| data | 操作结果 | `{...}` |
| - following | 本次操作要求的关系状态 | true |
| - changed | 本次首次执行是否改变关系 | true |

> 请求体可省略或为 `{}`。重复设置相同状态成功返回，`changed=false`。使用相同幂等键重试返回原操作结果，不代表当前状态；同一关系的添加与取消请使用不同键，否则返回 HTTP 409。

> 目标不存在或不可访问时返回 HTTP 404；添加收藏、订阅还需账号具备相应功能权限。

> 成功响应可包含 `data.interaction`，表示当前收藏状态，包含 targetId 和 following。已不可查看时为 null，旧服务或状态补查失败时可能省略该字段。


### 取消收藏文章

`DELETE /api/v3/me/collections/{targetId}`

取消当前账号与目标的关系，需要 `follows.write`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | follow-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request DELETE 'https://fishpi.cn/api/v3/me/collections/1770000000000' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: follow-example-1' \
  --header 'Content-Type: application/json' --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 / 空字符串 |
| data | 操作结果 | `{...}` |
| - following | 本次操作要求的关系状态 | false |
| - changed | 本次首次执行是否改变关系 | true |

> 请求体可省略或为 `{}`。重复设置相同状态成功返回，`changed=false`。使用相同幂等键重试返回原操作结果，不代表当前状态；同一关系的添加与取消请使用不同键，否则返回 HTTP 409。

> 可以清理已删除或当前不可访问目标的关系。关系不存在时成功返回 `changed=false`。

> 成功响应可包含 `data.interaction`，表示当前收藏状态，包含 targetId 和 following。已不可查看时为 null，旧服务或状态补查失败时可能省略该字段。


### 本人的订阅文章列表

`GET /api/v3/me/subscriptions`

读取当前账号的订阅文章列表，需要 `content.read`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/me/subscriptions?p=1&size=20' --header 'Authorization: Bearer TOKEN'
```

响应：

| Key | 说明 |
| --- | --- |
| code / msg | 结果码 / 提示 |
| data | 列表结果 |
| - list | 文章摘要，与用户文章列表一致；另含 available |
| - pagination | 分页信息 |
| -- p / size / total / pageCount | 页码 / 每页数量 / 总条数 / 总页数 |

> 按添加关系的先后倒序。当前不可阅读或已删除的目标仅返回 `id` 与 `available=false`，仍可取消该关系；允许阅读的讨论帖正常返回。

### 添加订阅文章

`PUT /api/v3/me/subscriptions/{targetId}`

添加当前账号与目标的关系，需要 `follows.write`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | follow-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request PUT 'https://fishpi.cn/api/v3/me/subscriptions/1770000000000' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: follow-example-1' \
  --header 'Content-Type: application/json' --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 / 空字符串 |
| data | 操作结果 | `{...}` |
| - following | 本次操作要求的关系状态 | true |
| - changed | 本次首次执行是否改变关系 | true |

> 请求体可省略或为 `{}`。重复设置相同状态成功返回，`changed=false`。使用相同幂等键重试返回原操作结果，不代表当前状态；同一关系的添加与取消请使用不同键，否则返回 HTTP 409。

> 目标不存在或不可访问时返回 HTTP 404；添加收藏、订阅还需账号具备相应功能权限。

> 成功响应可包含 `data.interaction`，表示当前订阅状态，包含 targetId 和 following。已不可查看时为 null，旧服务或状态补查失败时可能省略该字段。


### 取消订阅文章

`DELETE /api/v3/me/subscriptions/{targetId}`

取消当前账号与目标的关系，需要 `follows.write`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌 | Bearer TOKEN |
| Idempotency-Key | 幂等键，1–128 位字母、数字或 . _ : - | follow-example-1 |
| targetId | URL 参数，目标 ID | 1770000000000 |

请求示例：

```bash
curl --location --request DELETE 'https://fishpi.cn/api/v3/me/subscriptions/1770000000000' \
  --header 'Authorization: Bearer TOKEN' \
  --header 'Idempotency-Key: follow-example-1' \
  --header 'Content-Type: application/json' --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 / 空字符串 |
| data | 操作结果 | `{...}` |
| - following | 本次操作要求的关系状态 | false |
| - changed | 本次首次执行是否改变关系 | true |

> 请求体可省略或为 `{}`。重复设置相同状态成功返回，`changed=false`。使用相同幂等键重试返回原操作结果，不代表当前状态；同一关系的添加与取消请使用不同键，否则返回 HTTP 409。

> 可以清理已删除或当前不可访问目标的关系。关系不存在时成功返回 `changed=false`。

## API v3 排行榜

> 成功响应可包含 `data.interaction`，表示当前订阅状态，包含 targetId 和 following。已不可查看时为 null，旧服务或状态补查失败时可能省略该字段。


### 获取排行榜

`GET /api/v3/rankings/{type}`

返回站点现有榜单的公开排名，按榜单快照分页。可携带 Bearer 访问令牌（`content.read`）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | URL 参数；links、balance、consumption、checkin、online、adr、mofish、smallmofish、lifeRestart、evolve、emoji、xiaoice、invite、donate、perfect | balance |
| subType | evolve 支持 achievement（默认）、know、reset、days；xiaoice 支持 0（默认）、1、2、3；其他榜单省略 | achievement |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/v3/rankings/evolve?subType=achievement&p=1&size=20'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示信息 | 0 / 空字符串 |
| data | 榜单结果 | `{...}` |
| - type / subType | 榜单类型 / 子榜单；无子榜单为空字符串 | evolve / achievement |
| - list | 排名记录 | `[...]` |
| -- rank | 当前榜单中的名次，从 1 开始，跨页连续 | 1 |
| -- score | 当前排序指标的数值 | 100 |
| -- metrics | 该榜单的公开指标，见下表 | `{...}` |
| -- user | 用户榜单的公开资料 | `{...}` |
| --- id / userName / userNickname / userAvatarURL | 用户 ID / 用户名 / 昵称 / 头像 | 123 / user / 昵称 / URL |
| -- link | links 榜单的链接资料，此类记录没有 user | `{...}` |
| --- id / title / url | 链接 ID / 标题 / 链接地址 | 123 / 标题 / URL |
| - summary | 仅 donate 返回全站赞助汇总 | `{...}` |
| -- amount / supportedDays | 总赞助金额 / 支持运营天数 | 100 / 20 |
| - pagination | 分页信息 | `{...}` |
| -- p / size / total / pageCount | 页码 / 每页数量 / 当前榜单总条数 / 总页数 | 1 / 20 / 64 / 4 |

| type | score 对应指标 | metrics 字段 |
| --- | --- | --- |
| links | 点击次数 | clickCount |
| balance / consumption | 积分余额 / 累计消费积分 | points |
| checkin | 今日连续签到天数 | streak |
| online | 累计在线分钟 | minutes |
| adr / mofish / smallmofish / emoji | 游戏得分 | points |
| lifeRestart | 重开次数 | times、achievement（成就数量） |
| evolve | subType 选中的历史最高指标 | achievement、know、reset、days、currentKnow、currentDays |
| xiaoice | 0 经验 / 1 死亡次数 / 2 历练次数 / 3 吐纳次数 | 对应 exp / dieTimes / allExTimes / allBsTimes；0 另有 ancestry、gongfa |
| invite | 邀请次数 | count |
| donate | 赞助金额 | amount、count（赞助次数） |
| perfect | 优选文章数量 | count |

> 这是现有有限榜单的分页，`total` 表示本次可展示的榜单条数，不代表全站用户数量。Evolve 与人生重开沿用现有最近 100 份游戏记录的排名范围。未知类型或子榜单返回 HTTP 422；小冰榜上游暂不可用时返回 HTTP 503，可稍后重试。

## 私信（API v3）

读取接口需要 `messages.read`，发送、撤回与标记已读接口需要 `messages.write`。已有登录令牌须重新登录取得新增权限；第三方应用须先获准申请对应权限，再让用户重新授权。刷新已有令牌不会增加权限。读取不会自动标记已读。

### 发送私信

`POST /api/v3/me/conversations/{peerId}/messages`

向指定用户发送 Markdown 私信，需要 messages.write 和 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer 访问令牌 | Bearer xxx |
| Idempotency-Key（Header） | 本次发送的唯一键，重试保持不变 | private-send-20261020-1 |
| peerId（Path） | 接收用户ID；文件传输助手为1000000000086 | 1630000000001 |
| content | Markdown正文，最多10240个UTF-16单元，去除首尾空白后不能为空 | **你好** |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/conversations/1630000000001/messages' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: private-send-20261020-1' \
  --header 'Content-Type: application/json' \
  --data '{"content":"**你好**"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 已发送回执 | {} |
| - id | 消息ID | 1792450000000 |
| - peerId | 接收用户ID | 1630000000001 |
| - sentAt | 服务器当地时间，yyyy-MM-dd HH:mm:ss | 2026-10-20 12:34:56 |

> 仅允许向当前已激活的其他用户或文件传输助手发送，不允许给自己发信。正文沿用现有保留词处理和长度规则；JSON请求体最多65536字节，只接受 content，不接受查询参数。发送者与时间由服务端确定。
>
> 同键同内容重试返回原回执，不会重复发送；同键不同内容返回HTTP 409。请求中断或暂不可用时应沿用原键和原正文重试，已保存的消息会返回原回执，不要换新键重复发送。正文不合法返回422，目标不存在或不可用返回404，暂时无法完成返回503。收到成功回执后，可用消息详情或会话历史读取正文，需要 messages.read；客户端应继续查询历史与未读数，本次回执不保证实时推送。

### 撤回本人私信

`DELETE /api/v3/me/messages/{messageId}`

撤回当前账号发送的私信，需要 messages.write 和 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer访问令牌 | Bearer xxx |
| Idempotency-Key（Header） | 本次撤回的唯一键，重试保持不变 | private-withdraw-20261020-1 |
| messageId（Path） | 本人发送的私信消息ID | 1792450000000 |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/me/messages/1792450000000' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: private-withdraw-20261020-1'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 撤回回执 | {} |
| - id | 已撤回的消息ID | 1792450000000 |
| - peerId | 原接收用户ID | 1630000000001 |
| - removed | 已撤回 | true |

> 无需请求体，也可发送空JSON对象，最多1024字节，不接受查询参数或其他字段。只能撤回本人发送的消息，接收者不能代为撤回；原接收者已删除或停用时仍可撤回本人历史消息，文件传输助手消息同样支持。
>
> 仅清理能准确关联到本条消息的未读记录，不清除同一发送者的其他未读。无法归属的历史未读可能保留，可由接收方使用标记已读接口处理。回执不提供接收者是否已读或剩余未读数。
>
> 同键重试返回原回执，不重复删除；使用新键撤回不存在、已撤回或非本人消息返回HTTP 404；非法消息ID、缺少幂等键或字段不合法返回422；消息状态变化返回409；暂时无法完成返回503。撤回后详情不再可读，会话最新消息相应回退，没有剩余历史时会话不再出现在列表。客户端应重新查询历史及列表刷新显示，本次不保证实时撤回推送。

### 标记指定会话已读

`POST /api/v3/me/conversations/{peerId}/read`

清除本人来自指定用户的未读记录，需要 messages.write 和 Idempotency-Key，不删除历史消息。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer 访问令牌 | Bearer xxx |
| Idempotency-Key（Header） | 本次操作的唯一键，重试保持不变 | private-read-20261020-1 |
| peerId（Path） | 对方用户ID；允许处理已有的失效或删除账号会话 | 1630000000001 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/conversations/1630000000001/read' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: private-read-20261020-1'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 操作结果 | {} |
| - changed | 本次清除的未读记录数，没有未读时为0 | 2 |

> 无需请求体，也可发送空JSON对象，最多1024字节，不接受查询参数或其他字段。非法用户ID或本人ID返回HTTP 422；合法ID没有未读记录时成功返回0。changed 是本次操作回执中的记录数，不是当前未读总数，也不表示逐条消息的已读状态。同键重试不会再次清除后来产生的未读记录；需要再次标记时使用新的操作键。

### 标记全部私信已读

`POST /api/v3/me/conversations/read`

清除本人收到的全部未读记录，需要 messages.write 和 Idempotency-Key，不删除会话或历史消息。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization（Header） | Bearer 访问令牌 | Bearer xxx |
| Idempotency-Key（Header） | 本次操作的唯一键，重试保持不变 | private-read-all-20261020-1 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/conversations/read' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: private-read-all-20261020-1'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 操作结果 | {} |
| - changed | 本次清除的本人未读记录数 | 5 |

> 请求体和重试规则同标记指定会话已读。并发到达的新消息仍可能产生未读，请以未读数查询结果为准；如需继续标记，应发起新的操作。

### 查询本人私信会话

`GET /api/v3/me/conversations`

按最新消息 ID 倒序分页返回当前用户已有的私信会话。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 messages.read | Bearer ACCESS_TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/conversations?p=1&size=20' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 会话分页 | {} |
| - list | 当前页会话 | [] |
| -- peerId | 对方用户 ID | 1480000000000 |
| -- peer | 对方公开资料，字段见下表 | {} |
| -- lastMessage | 最新消息，字段见消息表 | {} |
| -- unreadCount | 来自该用户的未读记录数 | 2 |
| - pagination | 分页信息 | {} |
| -- p | 当前页 | 1 |
| -- size | 每页条数 | 20 |
| -- total | 会话总数 | 3 |
| -- pageCount | 总页数 | 1 |

对方资料字段：

| Key | 说明 | 示例 |
| --- | --- | --- |
| id | 用户 ID | 1480000000000 |
| userName | 当前用户名；不可用时为空 | Alice |
| userNickname | 当前昵称；不可用时为空 | 小鱼 |
| userAvatarURL | 头像网址；不可用时为空 | https://example.com/avatar.png |
| available | 对方账号资料是否可用 | true |
| builtin | 是否为内置文件传输助手 | false |

消息字段：

| Key | 说明 | 示例 |
| --- | --- | --- |
| id | 消息 ID | 1792390000000 |
| fromId | 发送者用户 ID | 1480000000000 |
| toId | 接收者用户 ID | 1490000000000 |
| fromMe | 是否由当前用户发送 | false |
| sentAt | 既有消息的服务器当地时间，格式 yyyy-MM-dd HH:mm:ss；无法识别时为空 | 2026-10-19 12:34:56 |
| content | 原始 Markdown | 你好 |
| contentHtml | 清洗后的 HTML；渲染不可用时为空 | <p>你好</p> |
| preview | 纯文本摘要，最多 120 个 Unicode 字符 | 你好 |
| available | 消息正文是否可用；不可用时正文及摘要为空 | true |

> 用户名与昵称按文本展示。自行渲染 content 时须清洗 HTML。已删除或停用的对方保留会话及本人有权阅读的消息，其 peer.available 为 false；文件传输助手的 peerId 固定为 1000000000086。未读数是现有未读记录数量，不表示每条消息的已读状态。

### 查询本人指定私信会话

`GET /api/v3/me/conversations/{peerId}`

返回与指定用户已有会话的公开资料、最新消息和未读数。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 messages.read | Bearer ACCESS_TOKEN |
| peerId | 路径参数，对方用户 ID | 1480000000000 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/conversations/1480000000000' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 会话信息 | {} |
| - peerId | 对方用户 ID | 1480000000000 |
| - peer | 对方公开资料，同会话列表 | {} |
| - lastMessage | 最新消息，同消息字段表 | {} |
| - unreadCount | 来自该用户的未读记录数 | 2 |

> 不存在本人参与的会话时返回 HTTP 404。peerId 使用用户 ID，不使用用户名或会话字符串；不能指定本人。

### 查询本人私信历史

`GET /api/v3/me/conversations/{peerId}/messages`

初次获取最新一页，后续使用消息 ID 游标向前或向后读取；每页消息始终按 ID 升序返回。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 messages.read | Bearer ACCESS_TOKEN |
| peerId | 路径参数，对方用户 ID | 1480000000000 |
| size | 每页条数，默认 20，最大 50 | 20 |
| before | 可选，只取小于此消息 ID 的记录；与 after 互斥 | 1792390000000 |
| after | 可选，只取大于此消息 ID 的记录；与 before 互斥 | 1792380000000 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/conversations/1480000000000/messages?size=20&before=1792390000000' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 消息页 | {} |
| - peerId | 对方用户 ID | 1480000000000 |
| - peer | 对方公开资料，同会话列表 | {} |
| - list | 消息列表，同消息字段表 | [] |
| - hasMore | 本次查询方向是否还有记录 | true |
| - nextBefore | 本页最小消息 ID，供 before 使用；空页为空 | 1792380000000 |
| - nextAfter | 本页最大消息 ID，供 after 使用；空页为空 | 1792390000000 |

> 已有会话翻到尽头返回空列表；不存在本人参与的会话返回 HTTP 404。游标不包含边界记录。ID 必须是无前导零的正整数字符串，最多 19 位；非法 ID、同时传 before 与 after、超出分页范围返回 HTTP 422。

### 查询本人私信消息详情

`GET /api/v3/me/messages/{messageId}`

读取当前用户作为发送者或接收者的一条消息。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 messages.read | Bearer ACCESS_TOKEN |
| messageId | 路径参数，消息 ID | 1792390000000 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/messages/1792390000000' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 消息详情 | {} |
| - peerId | 对方用户 ID | 1480000000000 |
| - peer | 对方公开资料，同会话列表 | {} |
| - message | 消息，同消息字段表 | {} |

> 消息不存在或不属于当前用户时返回 HTTP 404。

### 查询本人私信未读数

`GET /api/v3/me/messages/unread-count`

返回当前用户收到的未读记录总数。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 messages.read | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/messages/unread-count' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 未读统计 | {} |
| - count | 未读记录数 | 2 |

> 以上读取接口凭据无效返回 HTTP 401，缺少 messages.read 返回 HTTP 403，当前账号不可用返回 HTTP 404，服务暂不可用返回 HTTP 503。发送、已读和撤回见本模块前文；实时订阅见本模块的私信连接票据与通道说明。

### 获取私信连接票据

`POST /api/v3/me/messages/realtime-ticket`

订阅本人私信状态变更，需要 messages.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer 访问令牌；需要 messages.read | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/messages/realtime-ticket' \
  --header 'Authorization: Bearer ACCESS_TOKEN' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code / msg | 结果码 / 提示 | 0 |
| data | 连接凭据 | `{...}` |
| - ticket | 一次性连接票据 | OPAQUE_TICKET |
| - expiresIn | 可用于认证的剩余秒数，最多60秒 | 60 |
| - path | 同一服务上的 WebSocket 路径，包含部署路径 | /api/v3/realtime |
| - channel | 订阅类型，固定为 messages | messages |

> 请求体可省略或为 {}，不接受 userId、peerId、channel、scope 等自定义字段。每次请求签发新票据，无需 Idempotency-Key；最多成功使用一次。
>
> 无访问凭据返回 HTTP 401，缺少 messages.read 返回 HTTP 403，多余字段返回 HTTP 422；实时私信服务暂不可用返回 HTTP 503（realtime_unavailable）。

### 连接私信通道

`WS /api/v3/realtime`

使用票据响应中的 path 连接，等待 authentication.required 后发送 authenticate，认证成功返回 ready。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | 认证消息固定为 authenticate | authenticate |
| ticket | 上一步返回的一次性私信票据 | OPAQUE_TICKET |

请求示例：

```bash
SOCKET_TICKET='OPAQUE_TICKET' node --input-type=module <<'JS'
const socket = new WebSocket('wss://fishpi.cn/api/v3/realtime');
socket.addEventListener('message', ({ data }) => {
  const message = JSON.parse(data);
  if (message.type === 'authentication.required') {
    socket.send(JSON.stringify({ type: 'authenticate', ticket: process.env.SOCKET_TICKET }));
  } else if (message.type === 'ready' || message.type === 'messages.changed') {
    console.log('重新读取本人会话、历史及未读数');
  }
});
JS
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| type | authentication.required、ready、pong 或 messages.changed | messages.changed |
| data | 消息数据；authentication.required、pong、messages.changed 为空对象 | `{}` |
| - channel | ready 中的订阅类型 | messages |
| - expiresAt | ready 中的连接授权到期时间，毫秒时间戳 | 1792304100000 |

> 本人发送、收到消息、已读、撤回或会话列表变化可能触发 messages.changed。提示不含消息 ID、正文、对方身份或未读数量；收到后使用本人会话、历史及未读数接口读取当前状态，读取历史不会自动标记已读。
>
> 首次 ready 和重连 ready 后必须主动查询当前状态。提示可能合并、重复或在断开时丢失，不提供逐条消息事件、可靠送达或断点续传；不要将提示数量当作消息数，也不要只依赖提示恢复历史。
>
> 地址不带查询参数，认证仅提交 type 和 ticket；成功后可发送 {"type":"ping"}，收到 pong。票据重放/过期、非法消息、令牌或账号失效时连接关闭；刷新访问令牌不会延长已有连接，需重新取票连接。发送、已读和撤回继续使用对应 HTTP 接口。

## 私信（兼容接口）

> 旧私信发送、已读的请求和正常响应字段保持。发生存储错误或响应中断时，先查询当前历史及未读状态，再决定是否重试。

### 撤回本人私信

`GET /chat/revoke`

撤回本人发送的私信，需要 apiKey。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥，可放在query或JSON请求体中 | YOUR_API_KEY |
| oId | query中的消息ID | 1792450000000 |

请求示例：

```bash
curl --get 'https://fishpi.cn/chat/revoke' \
  --data-urlencode 'apiKey=YOUR_API_KEY' \
  --data-urlencode 'oId=1792450000000'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| result | 0成功、-1失败；未鉴权为Unauthorized | 0 |
| msg | 失败时的提示 | 撤回暂不可用，请稍后重试 |

> 请求字段与正常成功格式保持。撤回只清理准确关联的本条消息未读，保留其他消息和无法归属的历史未读，不再清空该发送者的全部未读；依赖旧清理行为的客户端应改为明确调用已读接口。消息、相关未读及会话最新消息共同更新，未完成时不部分删除；撤回推送失败不会把已完成的撤回改报为失败。本接口没有幂等回执，响应中断时先查询消息是否仍存在，再决定是否重试。

## 注意事项

* 请一定要在请求时带上 UA，推荐使用 Chrome 的 UA，空 UA 将返回 500 状态码
* 对单个接口的访问频率必须控制在最低1次/30秒，否则IP可能进入小黑屋（WebSocket、发送消息接口除外）
* 所有WebSocket频道设计时必须有断开自动重连机制

## 鉴权

摸鱼派社区 API 引入了 `apiKey` 的概念，对 API 的请求不需要提供 Cookie，只需要在参数中带上申请的 `apiKey` 即可。

> 注意：凡是 POST 请求，请求体必须是 JSON 格式，例如：`{ "nameOrEmail":"","userPassword":"" }`

### 获取 apiKey

`POST` `/api/getKey`

用于 API 获取摸鱼派的通用密钥，`Key` 即身份。请将密钥作为不透明字符串保存，不依赖固定长度。修改安全凭据或重置密码后，既有密钥会失效，需重新获取；建议配合 `/api/user` 定期检测是否仍有效。

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
| - userGuideStep      | 新人引导步骤：0 已完成，1 上传头像，2 关注标签，3 关注用户，4 Star 项目，-1 未开始 | -1                                                |
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

### 重置密码元数据

`GET /api/reset-pwd/meta?code=<code>`

忘记密码短信验证后，用重置码换取用户 Id，供后续 `POST /reset-pwd` 提交新密码使用。

请求：

| Key  | 说明     | 示例   |
| ---- | -------- | ------ |
| code | 重置码   | xxxx   |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/reset-pwd/meta?code=xxxx'
```

响应：

| Key          | 说明                             | 示例                  |
| ------------ | -------------------------------- | --------------------- |
| code         | 0 成功，-1 失败                  | 0                     |
| msg          | 错误信息                         | 验证码已过期          |
| data         | 响应数据                         | `{ ... }`             |
| - userId     | 用户 Id                          | 1630000000000         |
| -- code      | 重置码                           | xxxx                  |

### 提交新密码

`POST /reset-pwd`

使用重置密码元数据返回的用户 ID 和找回密码短信验证码提交新密码，成功后建立登录状态。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| userId | 元数据返回的用户 ID | 1630000000000 |
| code | 当前手机号收到的找回密码验证码，6位数字 | 123456 |
| userPassword | 新密码的 MD5 | e10adc3949ba59abbe56e057f20f883e |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/reset-pwd' \
  --header 'Content-Type: application/json' \
  --cookie-jar cookies.txt \
  --data '{"userId":"1630000000000","code":"123456","userPassword":"e10adc3949ba59abbe56e057f20f883e"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 成功，-1 失败 | 0 |
| msg | 失败原因 | 验证码无效或已失效 |

> 验证码须属于该用户的当前手机号及找回密码用途，并在有效期内；仅成功后失效，不能重复使用。如收到HTTP 302，请按Location完成页面交互，再向原接口重新提交请求。成功请求字段及登录行为保持，已有 v3 访问令牌、刷新令牌及尚未完成的授权需要重新获取。

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

> 邀请码为当前手机号预留一天；同手机号可重发，其他手机号在有效预留期间申请将返回原JSON错误格式。正常字段及成功响应保持。如收到HTTP 302，请按Location完成页面交互后返回原入口重试；不要自动反复发送短信。

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
| code         | 必填，第三步验证的6位注册短信验证码；须仍在有效期内 | 123456 |
| r            | 邀请人的用户名（选填，无邀请人则留空） | csfwff                           |

code 返回 0 则注册成功！

> 旧客户端第四步必须新增 code 字段。如收到HTTP 302，请按Location完成页面交互，再向原接口重新提交请求。验证码须绑定该用户的当前手机号和注册用途，成功后一次消费。正常角色、奖励、成功字段及成功后登录保持；错误、过期或其他用途的验证码不能激活账号，失败不消费有效验证码。

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

### 获取签到综合状态

`GET /api/checkin/status?apiKey=<Key>`

一次返回签到状态、活跃度、自动签到门槛与当前连续签到天数。需要 apiKey。

请求：

| Key    | 说明     | 示例         |
| ------ | -------- | ------------ |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/checkin/status?apiKey=YOUR_API_KEY'
```

响应：

| Key          | 说明                                   | 示例     |
| ------------ | -------------------------------------- | -------- |
| code         | 0 成功，-1 失败                        | 0        |
| msg          | 错误信息                               |          |
| data         | 响应数据                               | `{ ... }`|
| - checkedIn  | 今日是否已签到                         | true     |
| - liveness   | 活跃度百分比                           | 80.20    |
| - threshold  | 活跃度达标自动签到门槛（百分比）       | 10       |
| - streak     | 当前连续签到天数                       | 12       |

### 查询用户背包

`GET /api/user/bag?apiKey=<Key>`

获取当前用户的背包数据。需要 apiKey。

请求：

| Key    | 说明     | 示例         |
| ------ | -------- | ------------ |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/user/bag?apiKey=YOUR_API_KEY'
```

响应：

| Key                | 说明                         | 示例      |
| ------------------ | ---------------------------- | --------- |
| code               | 0 成功，-1 失败              | 0         |
| data               | 背包数据                     | `{ ... }` |
| - checkin1day      | 单日免签卡数量               | 0         |
| - checkin2days     | 两日免签卡数量               | 0         |
| - nameCard         | 改名卡数量                   | 0         |
| - patchCheckinCard | 补签卡数量                   | 0         |
| - sysCheckinRemain | 系统自动签到剩余天数         | 0         |

### 使用道具（API）

`GET /api/bag/1dayCheckin`、`GET /api/bag/2dayCheckin`、`GET /api/bag/patchCheckin`、`POST /api/bag/nameCard`

通过 API 使用背包道具。需要 apiKey。

> 补签卡按平台自然日补一天，今天已签到且补签条件满足时使用；能连接已有连续记录时接回。校验或保存失败保留卡片和补签记录，不额外发放签到积分。

`POST /api/bag/nameCard` 请求：

> 改名与消耗一张改名卡一并保存，校验或保存失败不扣卡。正常成功字段保持；apiKey 支持 query 参数或 JSON body。
>
> 若返回“操作结果暂未确认”或连接中断，请先重新读取资料与背包确认结果；不要更换用户名后直接重试，以免再次消耗卡片。

| Key      | 说明                                     | 示例         |
| -------- | ---------------------------------------- | ------------ |
| apiKey   | 通用密钥                                 | YOUR_API_KEY |
| userName | 新用户名，规则同注册：1-64 位字母数字-   | mynewname    |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/bag/patchCheckin?apiKey=YOUR_API_KEY'
```

响应：

| Key          | 说明                                 | 示例                       |
| ------------ | ------------------------------------ | -------------------------- |
| code         | 0 成功，-1 失败                      | 0                          |
| msg          | 结果说明                             | 补签卡使用成功！           |
| data         | 使用道具后的最新背包快照             | `{ ... }`                  |

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

### 签到排行

获取连续签到天数排行榜。

`GET /api/top/checkin`

请求：

| Key    | 说明             | 示例         |
| ------ | ---------------- | ------------ |
| apiKey | 通用密钥（可选） | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/top/checkin' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

响应：

| Key            | 说明                            | 示例    |
| -------------- | ------------------------------- | ------- |
| code           | 0 为请求成功                    | 0       |
| msg            | 错误消息                        |         |
| data           | 榜单数据                        | `{...}` |
| - list         | 排行用户列表（固定数量）        | `[...]` |
| - totalData    | 汇总数据（无汇总时为空对象）    | `{}`    |
| - type         | 榜单类型标识                    | checkin |

> 注意：返回固定数量的榜单数据（服务端配置），不支持 `p`/`size` 分页参数。

### 在线排行

获取在线时长排行榜。

`GET /api/top/online`

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/top/online' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

响应结构与"签到排行"一致，`type` 为 `online`。

### 其他排行榜

以下排行榜接口的响应结构与"签到排行"一致（`data.list` + `data.totalData` + `data.type`），均为匿名可读，可携带 `apiKey`：

| 接口                        | 说明                                   |
| --------------------------- | -------------------------------------- |
| `GET /api/top/balance`      | 财富排行（积分余额）                   |
| `GET /api/top/consumption`  | 消费排行（积分消耗）                   |
| `GET /api/top/perfect`      | 优选帖子排行                           |
| `GET /api/top/invite`       | 邀请排行                               |
| `GET /api/top/donate`       | 捐赠排行（`totalData` 含累计捐赠金额与支撑天数） |
| `GET /api/top/adr`          | ADR 游戏排行                           |
| `GET /api/top/mofish`       | 摸鱼游戏排行                           |
| `GET /api/top/smallmofish`  | 小摸鱼游戏排行                         |
| `GET /api/top/lifeRestart`  | 人生重开游戏排行                       |
| `GET /api/top/evolve`       | Evolve 游戏排行，`?type=` 指定子榜（默认 achievement） |
| `GET /api/top/emoji`        | 表情包游戏排行                         |
| `GET /api/top/xiaoice`      | 小冰游戏排行，`?type=` 指定子榜（默认 0） |
| `GET /api/top/any?types=...` | 多榜单合并查询，`types` 逗号分隔，子榜用 `\|` 分隔（如 `evolve|knowledge`）；响应 `data` 为 `{type, data}` 数组 |

> 兼容性提示：2026-10 起以上接口的 `data` 由"数组"统一调整为对象 `{list, totalData, type}`，旧客户端请改用 `data.list` 读取榜单列表。

### 获取广告位配置

获取站点广告位配置的 HTML 内容。

`GET /api/ads?slot=<广告位>`

请求：

| Key  | 说明                                                          | 示例       |
| ---- | ------------------------------------------------------------- | ---------- |
| slot | 广告位（可选）：`sideFull` 侧栏广告，`headerBanner` 顶部横幅 | sideFull   |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/ads?slot=sideFull' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

响应：

| Key                   | 说明                                       | 示例       |
| --------------------- | ------------------------------------------ | ---------- |
| code                  | 0 为请求成功                               | 0          |
| msg                   | 错误消息                                   |            |
| data.slots            | 广告位内容 map（带 `slot` 参数时只含该位） | `{...}`    |
| - sideFull            | 侧栏广告 HTML，未配置为空字符串            | `<div>...` |
| - headerBanner        | 顶部横幅 HTML，未配置为空字符串            |            |

> 注意：返回的是管理员配置的原始 HTML 片段，第三方客户端渲染时请注意 XSS 防护与样式适配。

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

> 普通消息确认保存成功后才返回成功；保存出错或连接中断时，请先刷新聊天记录确认结果，避免直接重发造成重复消息。

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

撤回聊天室消息，需要 apiKey。普通用户每天可撤回多次，当天费用依次为 0、32、64……积分。红包消息不可撤回；本次费用与结果以服务端提示为准。

> 与 v3 撤回接口共享每日计次，首次启用共享计次时从零开始，之后改名或服务重启不再清零。归属按消息作者 ID 判断，改名后仍可撤回本人历史消息，不能因用户名相同而撤回他人的历史消息。原有管理角色的撤回权限与免费规则保留。
>
> 未提交成功的撤回不扣款、不增加计次；撤回后该消息的 emoji reaction 一并移除。操作报错或连接中断时，请先刷新记录并核对积分后再决定是否重试。

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
| code | 0 为成功，-1 为失败 | 0          |
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

> 旧接口兼容未传 riskAccepted 的领取请求。心跳可能扣除积分，猜拳需提交出拳且猜错可能扣除积分。显式传 riskAccepted 时必须为布尔值 true，并使用 application/json。只查看可传 viewOnly=true，不会领取。旧历史红包规则保持；发送者出拳只在本人查看或结算后返回。

> viewOnly、riskAccepted、expectedUserId仅适用于新核心红包，其他目标会返回错误。

请求：

| Key     | 说明                                 | 示例                             |
| ------- | ------------------------------------ | -------------------------------- |
| apiKey  | 通用密钥                             | YOUR_API_KEY |
| oId     | 消息 Id                              | 1640078407444                    |
| gesture | 猜拳领取者出招，0 石头、1 剪刀、2 布。省略 riskAccepted 时也兼容这三个值的字符串形式 | 0 |
| riskAccepted | 新核心心跳/猜拳可选，传入时必须为布尔值 true | true |
| viewOnly | 新核心红包可选，只读取详情而不领取 | true |
| expectedUserId | 新核心红包可选，预期当前用户ID；不一致拒绝操作 | 123 |

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
| - userAvatarURL48 | 新核心红包的头像兼容字段 | https://... |
| - gesture | 新核心猜拳发送者出拳，仅本人查看或结算后返回 | 0 |
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

> **【接口停用预告】** 站点图床已切换为新的 **RhyPic 韵图** 图床（OSS 与 CDN 同机部署，统一域名 `file.fishpi.cn`），本页所述旧上传接口 `POST /upload` 将在不久后**彻底停用**。请第三方客户端开发者尽快改用 RhyPic 韵图的新上传接口完成适配，接口说明见 **[《RhyPic 韵图对接文档》](https://fishpi.cn/article/1790518451759)**。

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

## 鱼游

### 获取已审核鱼游列表

`GET /api/fish-games?p=1&size=20`

获取鱼游广场中已审核通过的鱼游列表。

请求：

| Key    | 说明             | 示例         |
| ------ | ---------------- | ------------ |
| p      | 页码，默认 1     | 1            |
| size   | 每页条数，默认 20，最大 50 | 20   |
| apiKey | 通用密钥（可选） | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/fish-games?p=1&size=20'
```

响应：

| Key           | 说明                 | 示例       |
| ------------- | -------------------- | ---------- |
| code          | 0 成功，-1 失败      | 0          |
| data          | 鱼游数组             | `[ ... ]`  |
| - oId         | 鱼游 Id              | 1630...    |
| - name        | 鱼游名称             | 大鱼吃小鱼 |
| - description | 简介                 | 一款小游戏 |
| - url         | 游戏地址             | https://.. |
| - iconURL     | 图标地址             | https://.. |
| - authorName  | 作者用户名           | adler      |
| - upCount     | 点赞数               | 11         |
| - downCount   | 点踩数               | 0          |
| - createTime  | 提交时间             | 2026-10-01 |

> 鱼游的投稿、投票、评论等写接口将在后续批次补充文档。

## 专栏

### 最新专栏

`GET /api/columns/latest?size=12`

获取首页「最新专栏」货架，按专栏更新时间倒序。

请求：

| Key    | 说明                       | 示例         |
| ------ | -------------------------- | ------------ |
| size   | 返回条数，默认 12，最大 12 | 12           |
| apiKey | 通用密钥（可选）           | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/columns/latest?size=12'
```

响应：

| Key                         | 说明                       | 示例        |
| --------------------------- | -------------------------- | ----------- |
| code                        | 0 成功，-1 失败            | 0           |
| data                        | 专栏卡片数组               | `[ ... ]`   |
| - columnId                  | 专栏 Id                    | 1770000...  |
| - columnTitle               | 专栏名称                   | 我的专栏    |
| - columnCoverURL            | 封面图地址                 | https://..  |
| - columnHasCover            | 是否设置自定义封面         | true        |
| - columnAuthorName          | 专栏作者用户名             | imlinha...  |
| - columnArticleCount        | 专栏章节数                 | 19          |
| - columnUpdateTimeStr       | 最近更新时间               | 2026-10-07  |
| - latestChapter             | 最新章节                   | `{ ... }`   |
| -- articleId                | 章节帖子 Id                | 1630...     |
| -- articlePermalink         | 章节帖子链接               | /article/x  |
| -- chapterNo                | 章节序号                   | 19          |
| -- articleTitle             | 章节标题                   | 第19章      |
| - secondLatestChapter       | 第二新章节，结构同上       | `{ ... }`   |

### 热门专栏

`GET /api/columns/hot?size=12`

获取首页「热门专栏」货架，按章节数和点赞活跃度排序。参数与响应结构同最新专栏。

### 我的专栏

`GET /api/columns/mine?size=100&apiKey=<Key>`

获取当前用户自己的专栏列表，用于长文章发帖页选择专栏和专栏管理。需要 apiKey。

请求：

| Key    | 说明                         | 示例         |
| ------ | ---------------------------- | ------------ |
| size   | 返回条数，默认 100，最大 100 | 100          |
| apiKey | 通用密钥                     | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/columns/mine?apiKey=YOUR_API_KEY'
```

响应：

| Key                   | 说明                 | 示例       |
| --------------------- | -------------------- | ---------- |
| code                  | 0 成功，-1 失败      | 0          |
| data                  | 专栏数组             | `[ ... ]`  |
| - oId                 | 专栏 Id              | 1770000... |
| - columnTitle         | 专栏名称             | 我的专栏   |
| - columnArticleCount  | 章节数               | 5          |
| - columnCoverURL      | 封面图地址           | https://.. |
| - columnHasCover      | 是否设置自定义封面   | true       |

### 专栏详情

`GET /api/columns/{columnId}`

获取专栏详情：名称、简介、章节数和作者信息。

请求：

| Key      | 说明                     | 示例         |
| -------- | ------------------------ | ------------ |
| columnId | 专栏 Id，本参数为 URL 参数 | 1770000000000 |
| apiKey   | 通用密钥（可选）         | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/columns/1770000000000'
```

响应：

| Key                  | 说明                 | 示例        |
| -------------------- | -------------------- | ----------- |
| code                 | 0 成功，-1 失败      | 0           |
| msg                  | 错误信息             | 专栏不存在  |
| data                 | 专栏详情             | `{ ... }`   |
| - columnId           | 专栏 Id              | 1770000...  |
| - columnTitle        | 专栏名称             | 我的专栏    |
| - columnDescription  | 专栏简介（暂不支持） |             |
| - columnArticleCount | 章节数               | 19          |
| - author             | 作者信息             | `{ ... }`   |
| -- userName          | 用户名               | imlinha...  |
| -- userNickname      | 昵称                  | 我是跳跳    |
| -- userAvatarURL     | 头像地址             | https://..  |

### 专栏章节列表

`GET /api/columns/{columnId}/articles?p=1&size=20`

分页获取专栏下的章节帖子，按章节序号正序。

请求：

| Key      | 说明                       | 示例          |
| -------- | -------------------------- | ------------- |
| columnId | 专栏 Id，本参数为 URL 参数 | 1770000000000 |
| p        | 页码，默认 1               | 1             |
| size     | 每页条数，默认 20，最大 100 | 20           |
| apiKey   | 通用密钥（可选）           | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/columns/1770000000000/articles?p=1&size=20'
```

响应：

| Key                             | 说明                     | 示例       |
| ------------------------------- | ------------------------ | ---------- |
| code                            | 0 成功，-1 失败          | 0          |
| data                            | 章节列表与分页           | `{ ... }`  |
| - articles                      | 章节数组                 | `[ ... ]`  |
| -- articleId                    | 帖子 Id                  | 1630...    |
| -- articlePermalink             | 帖子链接                 | /article/x |
| -- chapterNo                    | 章节序号                 | 1          |
| -- articleTitle                 | 章节标题                 | 第一章     |
| -- articleCreateTime            | 发布时间                 | 2026-10-01 |
| - pagination                    | 分页信息                 | `{ ... }`  |
| -- paginationPageCount          | 总页数                   | 1          |
| -- paginationRecordCount        | 总记录数                 | 19         |

> 超出范围的页码返回空数组。

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

> 发布或编辑返回操作过于频繁时，请稍后重试，避免并发重复提交。

> 发布时会核对余额是否同时满足发帖条件和全部实际费用。正文保存或扣款失败时，不会保留部分发帖扣款；匿名发布仍沿用原积分门槛与费用规则。请求参数及成功响应字段保持不变。

> 符合周报规则的奖励随帖子发布成功后生效；发布失败不会保留该次周报奖励。周报日期、发布时间和正文要求保持不变。

### 更新贴子

`PUT /article/<id>`

更新帖子

> 编辑内容与对应费用一并保存；任一步失败时，不会保留部分编辑扣款。原五分钟编辑规则、打赏启用费用、悬赏只能增加及匿名规则保持不变。

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

帖子列表类接口均已开放匿名访问，`apiKey` 为可选参数：不传时匿名返回公开帖子；传入有效 `apiKey` 时识别用户身份并返回与该用户相关的状态。

#### 最近

* 最近帖子列表：`GET /api/articles/recent`
* 热门帖子列表：`GET /api/articles/recent/hot`
* 点赞帖子列表：`GET /api/articles/recent/good`
* 最近回复帖子列表：`GET /api/articles/recent/reply`
* 最新长篇帖子列表：`GET /api/articles/recent/long`

请求参数（以上接口通用）：

- `apiKey`：用户 API Key（可选，匿名访问可省略）
- `p`：页码（可选，默认 1）
- `size`：每页数量（可选，默认使用系统值，最大 100）

响应：

- 与“帖子列表通用响应”一致（`code`、`msg`、`data.pagination`、`data.articles`）

说明：

- `/api/articles/recent` 系列默认不再包含长篇文章（`articleType=6`）。
- 长篇文章请使用 `/api/articles/recent/long`。
- 匿名访问时返回的均为公开帖子，机要帖（讨论帖）等受限内容不会出现在列表中。

#### 问答

* 最新问答列表：`GET /api/articles/qna`
* 未回答问答列表：`GET /api/articles/qna/unanswered`
* 悬赏问答列表：`GET /api/articles/qna/reward`
* 热门问答列表：`GET /api/articles/qna/hot`

请求参数（以上接口通用）：

- `apiKey`：可选，匿名访问可省略
- `p`：页码（可选，默认 1）
- `size`：每页数量（可选，默认使用系统值，最大 100）

响应：

| Key               | 说明           | 示例   |
| ----------------- | -------------- | ------ |
| code              | 0 为请求成功   | 0      |
| msg               | 错误消息       |        |
| data.articles     | 问答帖子列表 | `[...]` |
| data.pagination   | 分页信息       | `{...}` |

#### 优选

* 优选帖子列表：`GET /api/articles/perfect`

请求参数：

- `apiKey`：可选，匿名访问可省略
- `p`：页码（可选，默认 1）
- `size`：每页数量（可选，默认使用系统值，最大 100）

响应结构与“问答”一致（`data.articles` + `data.pagination`）。

#### 按标签

* 列出帖子：`GET /api/articles/tag/<标签URI>`
* 按热门程度列出帖子：`GET /api/articles/tag/<标签URI>/hot`
* 按点赞列出帖子：`GET /api/articles/tag/<标签URI>/good`
* 按最近回复列出帖子：`GET /api/articles/tag/<标签URI>/reply`
* 按优选列出帖子：`GET /api/articles/tag/<标签URI>/perfect`

注：标签URI可在点开某个标签的页面后，在URL中提取，例如[系统公告](https://fishpi.cn/tag/announcement)的标签URI为 `announcement`。该系列接口支持匿名访问，`apiKey` 可选。

#### 按领域

* 列出帖子：`GET /api/articles/domain/<领域URI>`

注：领域URI与标签URI同理，都可以从领域的页面的URL中提取。该接口支持匿名访问，`apiKey` 可选。

### 搜索帖子

按关键字搜索公开帖子的标题与正文。

`GET /api/search?q=<关键字>&p=<Page>&size=<Size>`

请求：

| Key  | 说明                                     | 示例   |
| ---- | ---------------------------------------- | ------ |
| q    | 搜索关键字（也可用 `key` 参数，二选一） | 摸鱼   |
| p    | 页码（可选，默认 1）                     | 1      |
| size | 每页数量（可选，默认 20，最大 50）       | 20     |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/search?q=%E6%91%B8%E9%B1%BC&p=1&size=20' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

响应：

| Key                | 说明                           | 示例             |
| ------------------ | ------------------------------ | ---------------- |
| code               | 0 为请求成功，-1 为请求失败    | 0                |
| msg                | 错误消息                       |                  |
| data.key           | 实际使用的搜索关键字（已转义） | 摸鱼             |
| data.total         | 命中总数 | 128           |
| data.pagination    | 分页信息                       | `{...}`          |
| data.articles      | 命中帖子列表 | `[...]`      |
| -- oId             | 帖子 ID                        | 1636516552191    |
| -- articleTitle    | 帖子标题                       | 摸鱼派社区开放 API 使用文档 |
| -- articleTags     | 帖子标签                       | 摸鱼派,API       |
| -- articlePermalink| 帖子固定链接                   | /article/1636516552191 |
| -- articlePreviewContent | 命中内容预览             | `...`            |
| -- articleAuthor   | 作者公开信息                   | `{...}`          |

> 注意：搜索服务不可用时返回 `code=-1`。命中结果不包含机要帖、思绪帖等受限内容，返回字段为白名单内的公开字段。

### 标签目录

分页返回全站标签目录，按引用数降序排列，仅包含被帖子引用的标签。

`GET /api/tags?p=<Page>&size=<Size>`

请求：

| Key  | 说明                               | 示例 |
| ---- | ---------------------------------- | ---- |
| p    | 页码（可选，默认 1）               | 1    |
| size | 每页数量（可选，默认 50，最大 100） | 50   |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/tags?p=1&size=50' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

响应：

| Key                 | 说明                     | 示例               |
| ------------------- | ------------------------ | ------------------ |
| code                | 0 为请求成功             | 0                  |
| msg                 | 错误消息                 |                    |
| data.total          | 标签总数                 | 582                |
| data.pagination     | 分页信息                 | `{...}`            |
| data.tags           | 标签列表                 | `[...]`            |
| - tagTitle          | 标签名                   | 有趣               |
| - tagURI            | 标签 URI                 | %e6%9c%89%e8%b6%a3 |
| - tagDescription    | 标签描述                 |                    |
| - tagReferenceCount | 引用计数                 | 15                 |
| - tagIconPath       | icon 图地址              | `https://...`      |

### 领域目录

返回全站领域目录及各领域公开帖数。

`GET /api/domains`

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/domains' \
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

响应：

| Key               | 说明                     | 示例         |
| ----------------- | ------------------------ | ------------ |
| code              | 0 为请求成功             | 0            |
| msg               | 错误消息                 |              |
| data.domains      | 领域列表                 | `[...]`      |
| - domainURI       | 领域 URI                 | evildoer     |
| - domainTitle     | 领域名                   | 程序员的折腾 |
| - domainDescription | 领域简介               |              |
| - domainIconPath  | 领域 icon 图地址         | `https://...`|
| - domainTagCnt    | 领域内标签数             | 13           |
| - articleCnt      | 领域内公开帖数           | 186          |

### 获取指定帖子

获取指定帖子的评论、文章内容、目录、点赞数量等。

`GET /api/article/<文章ID>?apiKey=<Key>`

请求:

| Key    | 说明                                   | 示例         |
| ------ | -------------------------------------- | ------------ |
| apiKey | 通用密钥（可选，匿名访问可省略）       | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/article/1636516552191?apiKey=YOUR_API_KEY'
--header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36' \
```

> 注意：匿名访问（不带 `apiKey`）时返回公开内容，`isFollowing`、`isWatching`、`isMyArticle`、`thanked` 等与“我”相关的字段统一为未登录状态；作者设置为“不允许匿名浏览”的帖子匿名请求返回 JSON `code=401`，带有效 `apiKey` 后可正常访问。讨论帖（机要帖）正文与评论对未受邀用户不可见。

响应：

| Key                             | 说明                                                           | 示例                               |
| ------------------------------- | -------------------------------------------------------------- | ---------------------------------- |
| code                            | 为 0 则请求成功，为 -1 则请求失败（密钥无效或帖子不可读）      | 0                                  |
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

### 置顶帖子（API）

`POST /api/article/stick`

置顶自己的帖子，置顶成功会扣除置顶积分（余额不足返回错误）。需要 apiKey。

请求：

| Key       | 说明      | 示例                             |
| --------- | --------- | -------------------------------- |
| apiKey    | 通用密钥  | YOUR_API_KEY                     |
| articleId | 帖子 Id   | 1645002736006                    |

请求示例：

```bash
curl --location --request POST 'https://fishpi.cn/api/article/stick' \
--header 'Content-Type: application/json' \
--data-raw '{"apiKey":"YOUR_API_KEY","articleId":"1645002736006"}'
```

响应：

| Key  | 说明                 | 示例                 |
| ---- | -------------------- | -------------------- |
| code | 0 成功，-1 失败      | 0                    |
| msg  | 结果或错误信息       | 置顶成功             |

> 仅帖子作者本人可操作；置顶按次扣积分，存在置顶数量上限时会先取消最早的置顶。

### 给文章点赞

`POST /vote/up/article`

给文章点赞，再次点赞会取消。需要 apiKey；切换赞踩时同步更新计数，保存失败不改变投票结果。

请求：

| Key    | 说明      | 示例                             |
| ------ | --------- | -------------------------------- |
| apiKey | 通用密钥  | YOUR_API_KEY |
| dataId | 文章的oId | 1645002736006                    |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/vote/up/article' \
  --header 'Content-Type: application/json' \
  --data '{"apiKey":"YOUR_API_KEY","dataId":"1645002736006"}'
```

响应：

| Key  | 说明                                                | 示例 |
| ---- | --------------------------------------------------- | ---- |
| code | 为0则成功                                           | 0    |
| type | 操作前的投票：-1 为未投票，0 为赞，1 为踩；原为赞时此次取消，原为踩时改赞 | -1 |

### 给文章点踩

`POST /vote/down/article`

给文章点踩，再次点踩会取消。需要 apiKey；切换赞踩时同步更新计数，保存失败不改变投票结果。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥 | YOUR_API_KEY |
| dataId | 文章 ID | 1645002736006 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/vote/down/article' \
  --header 'Content-Type: application/json' \
  --data '{"apiKey":"YOUR_API_KEY","dataId":"1645002736006"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 为 0 则成功 | 0 |
| type | 操作前的投票：-1 为未投票，0 为赞，1 为踩；原为踩时此次取消，原为赞时改踩 | -1 |

### 感谢文章

`POST /article/thank?articleId={文章oId}`

> articleId 在 URL 中传入，apiKey 可放在 JSON 请求体或 URL 查询参数。

感谢他人文章，需要 apiKey，亦支持 URL 查询参数 apiKey。重复感谢不会再次扣费或增加次数，保存失败不扣款；费用和匿名免转账规则保持。

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/article/thank?articleId=1645002736006' \
  --header 'Content-Type: application/json' \
  --data '{"apiKey":"YOUR_API_KEY"}'
```

响应：

| Key  | 说明      | 示例 |
| ---- | --------- | ---- |
| code | 为0则成功 | 0    |

### 打赏文章（旧）

`POST /article/reward?articleId={文章oId}`

需要 apiKey，按文章当前设定的价格打赏并返回打赏正文 HTML。已有购买记录或本人读取时不再次扣款；匿名文章沿用免转账规则。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| articleId | 文章 ID，URL 查询参数，1～32 位数字 | 1645002736006 |
| apiKey | 通用密钥，支持 JSON 请求体或 URL 查询参数 | YOUR_API_KEY |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/article/reward?articleId=1645002736006' \
  --header 'Content-Type: application/json' \
  --data '{"apiKey":"YOUR_API_KEY"}'
```

响应：

| Key | 说明 |
| --- | --- |
| code | 为 0 则成功 |
| msg | 失败提示 |
| articleRewardContent | 成功时返回打赏正文 HTML |

> 仅文章当前可阅读、已启用打赏，且本次购买成功、已有购买记录或本人读取时返回正文。账户或作者不可用、未受邀讨论、余额不足等失败不返回正文；未完成的购买不扣款。正常成功字段与费用保持不变。需要编辑本人未启用打赏的原始内容时，请使用本人文章编辑源码接口。

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

> 实时新增评论消息中，匿名评论的 commentAuthorId 保留为空字符串；本人状态使用 commentIsCurrentUser，楼主状态使用 commentIsArticleAuthor。有权阅读私密正文的规则不变。

`POST /comment`

发表评论或回复，需要 apiKey；apiKey 可放在 JSON 请求体或 URL 查询参数中。

请求：

| Key                      | 说明                                                              | 示例                             |
| ------------------------ | ----------------------------------------------------------------- | -------------------------------- |
| apiKey                   | 通用密钥                                                          | YOUR_API_KEY |
| articleId                | 文章的oId                                                         | 1645002736006                    |
| commentAnonymous         | 是否匿名评论，是填写true，否填写false                             | false                            |
| commentVisible           | 是否仅楼主可见，是填写true，否填写false                           | false                            |
| commentContent           | 评论原文（Markdown格式）                                          | hello world                      |
| commentOriginalCommentId | （选填）如果是回复某个评论，传递此参数并填写值为要回复的评论的oId | 1646216549324                    |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/comment' \
  --header 'Content-Type: application/json' \
  --data '{"apiKey":"YOUR_API_KEY","articleId":"1645002736006","commentContent":"评论正文","commentAnonymous":false,"commentVisible":false}'
```

响应：

| Key | 说明 |
| --- | --- |
| code | 0 表示成功 |
| msg | 操作提示；失败时说明原因 |

> 正文、积分、回复通知已读状态和自动订阅随发布结果一并处理；保存失败会撤销本次数据库变更。积分规则及自动订阅偏好保持不变。

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

修改本人评论，需要 apiKey；apiKey 可放在 JSON 请求体或 URL 查询参数中。

请求：

| Key              | 说明                                    | 示例                             |
| ---------------- | --------------------------------------- | -------------------------------- |
| apiKey           | 通用密钥                                | YOUR_API_KEY |
| articleId        | 文章的oId                               | 1645002736006                    |
| commentAnonymous | 是否匿名评论，是填写true，否填写false   | false                            |
| commentVisible   | 是否仅楼主可见，是填写true，否填写false | false                            |
| commentContent   | 评论原文（Markdown格式）                | hello world                      |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/comment/1646216549324' \
  --header 'Content-Type: application/json' \
  --data '{"apiKey":"YOUR_API_KEY","commentContent":"更新后的评论正文","commentVisible":false}'
```

响应：

| Key                  | 说明                                      | 示例      |
| -------------------- | ----------------------------------------- | --------- |
| code                 | `0` 表示成功                              | 0         |
| commentContent       | 更新后的评论内容，格式为 HTML             | `<p>...</p>` |
| commentRevisionCount | 更新后的历史版本数量；小于 2 时没有历史记录 | 2         |

### 给评论点赞

`POST /vote/up/comment`

给评论点赞，再次点赞会取消。需要 apiKey；切换赞踩时同步更新计数，保存失败不改变投票结果。

请求：

| Key    | 说明      | 示例                             |
| ------ | --------- | -------------------------------- |
| apiKey | 通用密钥  | YOUR_API_KEY |
| dataId | 评论的oId | 1645002736006                    |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/vote/up/comment' \
  --header 'Content-Type: application/json' \
  --data '{"apiKey":"YOUR_API_KEY","dataId":"1645002736006"}'
```

响应：

| Key  | 说明                                                | 示例 |
| ---- | --------------------------------------------------- | ---- |
| code | 为0则成功                                           | 0    |
| type | 操作前的投票：-1 为未投票，0 为赞，1 为踩；原为赞时此次取消，原为踩时改赞 | -1 |

### 给评论点踩

`POST /vote/down/comment`

给评论点踩，再次点踩会取消。需要 apiKey；切换赞踩时同步更新计数，保存失败不改变投票结果。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥 | YOUR_API_KEY |
| dataId | 评论 ID | 1645002736006 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/vote/down/comment' \
  --header 'Content-Type: application/json' \
  --data '{"apiKey":"YOUR_API_KEY","dataId":"1645002736006"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 为 0 则成功 | 0 |
| type | 操作前的投票：-1 为未投票，0 为赞，1 为踩；原为踩时此次取消，原为赞时改踩 | -1 |

### 感谢评论

`POST /comment/thank`

**注意**：不能给自己点感谢！会报错，记得做处理哦。

感谢不能取消。普通评论会扣除15积分送给对方，匿名评论保留免转账规则；重复感谢不重复扣费，保存失败时不扣款。

请求：

| Key       | 说明      | 示例                             |
| --------- | --------- | -------------------------------- |
| apiKey    | 通用密钥  | YOUR_API_KEY |
| commentId | 评论的oId | 1645002736006                    |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/comment/thank' \
  --header 'Content-Type: application/json' \
  --data '{"apiKey":"YOUR_API_KEY","commentId":"1645002736006"}'
```

响应：

| Key  | 说明      | 示例 |
| ---- | --------- | ---- |
| code | 为0则成功 | 0    |

### 删除评论

`POST /comment/{评论的oId}/remove`

删除满足条件的本人评论，需要 apiKey。成功时收取原有删除费用；删除被拒绝或保存失败时不扣款。

请求：

| Key    | 说明     | 示例                             |
| ------ | -------- | -------------------------------- |
| apiKey | 通用密钥 | YOUR_API_KEY |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/comment/1636516552191/remove' \
  -H 'Content-Type: application/json' \
  -d '{"apiKey":"YOUR_API_KEY"}'
```

响应：

| Key  | 说明      | 示例 |
| ---- | --------- | ---- |
| code | 为0则成功 | 0    |
| commentId | 成功删除的评论 ID | 1636516552191 |

> 存在回复、赞踩、感谢或已被采纳的评论不能自行删除，积分不足时也会拒绝删除。

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
| - breezemoonContent              | 正文（渲染后 HTML）              |           |
| - breezemoonContentRaw           | 正文原始 Markdown（新增）        |           |
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

匿名可读；仅当作者关闭"匿名查看"时匿名请求返回错误，携带 `apiKey` 不受影响。评论排序支持 query 参数 `m`：`0` 按时间正序（默认）、`1` 按时间倒序；不传时沿用登录用户偏好。

响应中的 **data** 新增以下字段：

| Key               | 说明                                          | 示例        |
| ----------------- | --------------------------------------------- | ----------- |
| relevantArticles  | 相关帖子列表（轻量字段：oId、标题、链接等）   | **[ ... ]** |
| previousArticle   | 上一篇（`{oId, articleId, articleTitle, articlePermalink}`，无则为 null） | `{ ... }` |
| nextArticle       | 下一篇（结构同上，无则为 null）               | `{ ... }`   |

响应中的 **data.article** 新增以下字段：

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

## 用户主页子列表

以 `/api/user/{userName}/...` 形式提供个人主页各子页数据。`userName` 为路径参数。除标注"仅本人"的接口外均可匿名访问；目标用户在隐私设置中关闭对应列表时，仅本人或管理员可见（其他请求返回 `code != 0`，`msg` 为"对方设置了隐私权限"）。分页参数 `p`（默认 1）、`size`（默认 20，最大 50）；分页接口统一返回 `data.pagination = {paginationPageCount, paginationRecordCount}`。

| 接口 | 说明 |
| --- | --- |
| `GET /api/user/{userName}/comments` | 该用户的评论列表（分页，`data.comments`） |
| `GET /api/user/{userName}/comments/anonymous` | 匿名评论列表（**仅本人**，分页） |
| `GET /api/user/{userName}/articles/anonymous` | 匿名帖子列表（**仅本人**，分页，`data.articles`） |
| `GET /api/user/{userName}/long` | 长文章列表（分页，`data.articles`） |
| `GET /api/user/{userName}/watching` | 关注动态聚合，`data.followingUserArticles` + `data.followingTagArticles` |
| `GET /api/user/{userName}/following/articles` | 关注用户发布的帖子（分页，`data.articles`） |
| `GET /api/user/{userName}/following/tags` | 关注的标签（分页，`data.tags`） |
| `GET /api/user/{userName}/following/users` | 关注的用户（分页，`data.users`） |
| `GET /api/user/{userName}/followers` | 粉丝列表（分页，`data.users`） |
| `GET /api/user/{userName}/points` | 积分流水（**仅本人**，分页，`data.points`，含 `time`、`sum`、`type`、`operation`、`balance`、`displayType`、`description`） |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/user/adlered/comments?p=1&size=20&apiKey=YOUR_API_KEY'
```

响应：

| Key  | 说明 | 示例 |
| --- | --- | --- |
| code | 0 成功，-1 失败 | 0 |
| msg | 错误信息 | 用户不存在 |
| data | 列表数据与分页 | `{...}` |

> 标注"仅本人"的接口只能使用目标用户自己的 apiKey 调用，用他人 apiKey 返回错误。

## 关注动态

均需 apiKey。分页参数 `p`（默认 1）、`size`（默认 20，最大 50）。

| 接口 | 说明 |
| --- | --- |
| `GET /api/watch/tags/articles` | 我关注的标签下的帖子（分页，`data.articles`） |
| `GET /api/watch/users/articles` | 我关注的用户发布的帖子（分页，`data.articles`） |
| `GET /api/watch/breezemoons` | 我关注的用户的清风明月（分页，`data.breezemoons`，每条含 `breezemoonContentRaw` 原始 Markdown） |

> 未关注任何人或关注的人没有清风明月时，返回空列表（`data.breezemoons` 为空数组）。

## 专栏管理

均需 apiKey，仅专栏作者本人可操作。

### 重命名专栏

`POST /api/columns/{columnId}/rename`

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥 | YOUR_API_KEY |
| columnId | 专栏 Id，本参数为 URL 参数 | 1770000000000 |
| columnTitle | 新专栏名称，1-64 字符 | 我的新专栏 |

响应：`code` 为 0 时 `data.column` 为更新后的专栏。

### 调整专栏顺序

`POST /api/columns/order`

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥 | YOUR_API_KEY |
| columnIds | 本人的全部专栏 Id，按期望顺序排列 | `["1770000000001","1770000000000"]` |

响应：通用 `code/msg`。排序后 `GET /api/columns/mine` 按新顺序返回。

### 删除专栏

`POST /api/columns/{columnId}/remove`

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥 | YOUR_API_KEY |
| columnId | 专栏 Id，本参数为 URL 参数 | 1770000000000 |

响应：通用 `code/msg`。

> 专栏下还有章节时拒绝删除，需先把章节移出专栏。

## 同城

### 获取同城用户

`GET /api/city/{cityName}`

返回指定城市中公开了位置的用户，分页。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| cityName | 城市名，本参数为 URL 参数 | 北京 |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |
| apiKey | 通用密钥（可选） | YOUR_API_KEY |

请求示例：

```bash
curl --location --request GET 'https://fishpi.cn/api/city/%E5%8C%97%E4%BA%AC?p=1&size=20'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 成功 | 0 |
| data | 结果 | `{...}` |
| - city | 城市名 | 北京 |
| - users | 用户列表（`oId`、`userName`、`userNickname`、`userAvatarURL`、`userNo`、`userIntro`、`userOnlineFlag`） | `[...]` |
| - pagination | 分页信息 | `{...}` |

## 大事记与统计

### 获取大事记时间线

`GET /api/milestones`

返回站点大事记列表，字段含 `id`、`title`、`date`、`end_date`、`content`、`media`、`link` 等，与页面 `/milestones` 一致。可携带 `apiKey`（可选）。

### 获取站点统计

`GET /api/statistic`

返回站点统计：`statisticMemberCount`（用户数）、`statisticArticleCount`（帖子数）、`statisticCmtCount`（评论数）、`statisticDomainCount`、`statisticTagCount`、`statisticLinkCount`、`statisticMaxOnlineVisitorCount`（历史最高在线）、`onlineVisitorCnt`（当前在线访客）、`onlineMemberCnt`（当前在线会员）等。可携带 `apiKey`（可选）。


## API v3 文章热度

### 读取文章当前热度

`GET /api/v3/articles/{articleId}/heat`

读取可访问文章的当前连接热度，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| articleId | 路径参数，文章 ID | 123456789 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/articles/123456789/heat' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 当前热度 | `{...}` |
| - articleId | 文章 ID | 123456789 |
| - heat | 当前节点的文章连接数近似值，无连接时为 0 | 3 |

> 多个标签页或设备分别计数；该值不是去重人数、累计浏览量或全站在线人数。重新查询会得到当前值，不提供历史游标。文章不存在或不可访问时返回 HTTP 404。

## API v3 聊天室红包

### 读取红包类型目录

`GET /api/v3/chat/red-packet-types`

获取红包类型及积分风险提示，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/chat/red-packet-types' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 类型目录 | `{...}` |
| - types | 类型列表 | `[]` |
| -- id | 类型标识：random、average、specify、heartbeat、rockPaperScissors | random |
| -- name | 类型名称 | 拼手气红包 |
| -- mayLosePoints | 领取该类型是否可能损失积分，心跳和猜拳为 true | false |
| - creationSupported | 当前是否提供 v3 创建接口（支持目录中的五类） | true |
| - claimSupported | 当前是否提供 v3 领取接口（支持目录中的五类） | true |

> 当前v3支持目录中五类红包的创建和领取。心跳与猜拳的创建和领取均须显式riskAccepted=true，可能扣除领取者积分。当前未列入骰子红包。

## API v3 复读机

### 复读机列表

`GET /api/v3/repeater/items`

读取按点赞数、创建时间排列的复读机内容。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 可选请求头，Bearer accessToken，需要content.read | Bearer YOUR_ACCESS_TOKEN |
| apiKey | 通用密钥（可选） | YOUR_API_KEY |
| type | 可选，joke段子、kfc疯狂星期四、fish鱼类科普；省略查询全部类型 | joke |
| size | 返回条数，默认12，最大50 | 12 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/repeater/items?type=joke&size=12' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 列表数据 | `{...}` |
| - items | 内容数组；没有内容时为空 | `[]` |
| -- id | 内容ID | 123 |
| -- type | 内容类型，joke/kfc/fish | joke |
| -- content | 纯文本正文 | 今日笑话 |
| -- likeCount | 点赞数 | 12 |
| -- createdTime | 创建时间，毫秒时间戳 | 1792584000000 |
| -- liked | 当前用户是否点赞；未提供Bearer时为false | false |
| -- author | 作者公开资料；无可展示作者时为null | `{"id":"123","name":"Alice"}` |
| --- id | 作者公开ID | 123 |
| --- name | 作者名称，纯文本 | Alice |

> 返回排序最靠前的指定条数，不提供全量分页。内容和作者名称应按纯文本显示。非法类型或条数返回422；内容资料异常或暂不可读返回503。apiKey不会为本接口提供个人点赞状态。

### 随机下一条复读机内容

`GET /api/v3/repeater/next`

从对应类型排序前50条中排除指定内容，再随机返回一条。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 可选请求头，Bearer accessToken，需要content.read | Bearer YOUR_ACCESS_TOKEN |
| apiKey | 通用密钥（可选） | YOUR_API_KEY |
| type | 可选，joke/kfc/fish；省略查询全部类型 | joke |
| excludeId | 可选，排除的内容ID，1–32位数字 | 123 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/repeater/next?type=joke&excludeId=123' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 随机结果 | `{...}` |
| - item | 与复读机列表单项字段相同；没有候选时为null | `{"id":"124","type":"joke","content":"今日笑话","likeCount":0,"createdTime":1792584000000,"liked":false,"author":null}` |

> 排除操作在前50条候选池确定后进行，不补入第51条。没有候选时返回item:null；未传excludeId时允许返回当前已显示的内容。非法类型或ID返回422，内容资料异常或暂不可读返回503。

### 投稿复读内容

`POST /api/v3/repeater/items`

提交纯文本复读内容。需要 Bearer Access Token（content.write），并提供 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer访问令牌 | Bearer ACCESS_TOKEN |
| Idempotency-Key | 本次操作幂等键，请求头 | repeater-post-001 |
| type | joke、kfc 或 fish，首尾空白会移除 | joke |
| content | 去HTML并折叠空白后2至500个UTF-16单位 | 今天也要开心摸鱼 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/repeater/items' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: repeater-post-001' \
  -H 'Content-Type: application/json' \
  -d '{"type":"joke","content":"今天也要开心摸鱼"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 投稿结果 | 对象 |
| - id | 内容ID | 1790000000000 |
| - type | 类型 | joke |
| - createdTime | 创建时间，毫秒 | 1790000000000 |

> 同一键与相同规范化内容重试返回原结果；同一键提交不同内容返回409。

### 设置复读内容点赞

`PUT /api/v3/repeater/items/{itemId}/like`

`DELETE /api/v3/repeater/items/{itemId}/like`

PUT设置点赞，DELETE取消点赞。需要 Bearer Access Token（content.write），并提供 Idempotency-Key。请求无需正文。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer访问令牌 | Bearer ACCESS_TOKEN |
| Idempotency-Key | 本次操作幂等键，请求头 | repeater-like-001 |
| itemId | 路径参数，内容ID | 1790000000000 |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/repeater/items/1790000000000/like' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: repeater-like-001'
```

取消点赞请求示例：

```bash
curl -X DELETE 'https://fishpi.cn/api/v3/repeater/items/1790000000000/like' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: repeater-unlike-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 设置结果 | 对象 |
| - id | 内容ID | 1790000000000 |
| - liked | 本次结果 | true |
| - likeCount | 点赞数 | 12 |

> 重复设置相同状态不会重复点赞或减计数。同键重试返回原结果，不覆盖后来完成的设置。

## API v3 职业

### 读取职业目录

`GET /api/v3/professions`

分页读取已发布职业的名称和说明。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥（可选） | YOUR_API_KEY |
| Authorization | 请求头，Bearer accessToken（可选）；提供时需要 profile.read | Bearer YOUR_ACCESS_TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/professions?p=1&size=20'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 分页结果 | `{...}` |
| - list | 职业列表 | `[]` |
| -- professionId | 职业 ID | 1700000000001 |
| -- professionCode | 职业代码 | writer |
| -- displayName | 名称 | 创作者 |
| -- shortName | 简称 | 创作 |
| -- description | 说明 | 参与内容创作 |
| - pagination | 分页信息，含 p、size、total、pageCount | `{...}` |

### 读取本人职业

`GET /api/v3/me/professions`

读取当前账号的职业选择、隐私设置和分页进度，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/professions?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 本人职业结果 | `{...}` |
| - state | 选择与隐私状态 | `{...}` |
| -- onboardingState | UNDECIDED、SKIPPED 或 SELECTED | SELECTED |
| -- primaryProfessionId | 主职业 ID，未选择为空 | 1700000000001 |
| -- primaryProfessionState | NONE、ACTIVE、RETIRED 或 DELETED | ACTIVE |
| -- privacyPreset | ALL_PUBLIC、LEVEL_ONLY、PRIMARY_ONLY、SELF_ONLY、FULLY_HIDDEN 或 CUSTOM | SELF_ONLY |
| -- moduleVisibility | 各模块可见性，值为 PUBLIC、LOGGED_IN、SELF 或 HIDDEN | `{...}` |
| --- primaryProfession | 主职业模块 | PUBLIC |
| --- allProfessions | 全部职业模块 | SELF |
| --- experience | 经验模块 | SELF |
| --- rarity | 稀有度模块 | PUBLIC |
| --- majorContributions | 主要贡献模块 | PUBLIC |
| --- contributionStats | 贡献统计模块 | PUBLIC |
| --- activityFeed | 活动模块 | SELF |
| --- levelHistory | 等级历史模块 | SELF |
| --- rewards | 奖励模块 | SELF |
| --- ranking | 排行模块 | HIDDEN |
| - list | 已产生进度的职业，含目录中的名称与说明字段 | `[]` |
| -- state | ACTIVE 或 RETIRED | ACTIVE |
| -- totalExperience | 总经验 | 120 |
| -- levelCode | 当前等级代码 | novice |
| -- levelName | 当前等级名称 | 初学者 |
| -- levelDescription | 等级说明 | 开始积累经验 |
| -- requiredExperience | 当前等级所需经验 | 100 |
| - pagination | 分页信息，含 p、size、total、pageCount | `{...}` |

> 本人可以读取隐藏的职业进度。列表只包含已有进度记录的职业；已选主职业可能尚未出现在列表中，请同时读取 state。

### 本人职业详情

`GET /api/v3/me/professions/{professionId}`

读取本人已有职业进度的历史展示资料、等级路线及贡献汇总，需要profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| professionId | 路径参数，职业ID，1–19位数字 | 10 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/professions/10' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 职业详情 | `{...}` |
| - profession | 本人真实职业进度 | `{...}` |
| -- professionId | 职业ID | 10 |
| -- professionCode | 职业编码 | writer |
| -- displayName | 关联版本展示名称 | 写作者 |
| -- shortName | 简称 | 作者 |
| -- description | 职业介绍 | 记录生活 |
| -- state | ACTIVE或RETIRED | ACTIVE |
| -- totalExperience | 当前累计经验 | 50 |
| -- levelCode | 当前等级编码 | L1 |
| -- levelName | 当前等级名称 | 初级 |
| -- requiredExperience | 当前等级累计经验门槛 | 0 |
| - levels | 关联等级方案的完整路线 | `[{...}]` |
| -- levelCode | 等级编码 | L1 |
| -- sortOrder | 展示顺序 | 0 |
| -- requiredExperience | 累计经验门槛 | 0 |
| -- displayName | 等级名称 | 初级 |
| -- shortName | 等级简称 | 初 |
| -- description | 等级介绍 | 初始等级 |
| -- achievementDescription | 达成描述 | 开始创作 |
| -- topLevel | 是否最高等级 | false |
| - contributions | 按动作汇总的贡献 | `[{...}]` |
| -- actionCode | 动作编码 | article.publish |
| -- experienceSum | 累计实际经验，可为负 | 50 |
| -- eventCount | 效果次数 | 2 |
| -- lastOccurredAt | 最近发生毫秒时间戳 | 1792600000000 |

> 无本人真实职业进度返回404，即使已选为主职业；不会自动创建零级进度。退休职业的本人历史进度仍可读取。资料异常或暂时无法完整读取返回503。返回字符串请按文本展示。

### 本人职业经验记录

`GET /api/v3/me/professions/{professionId}/records`

读取本人该职业最近90天已应用的非零经验变化，需要profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| professionId | 路径参数，职业ID，1–19位数字 | 10 |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20、最大50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/professions/10/records?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 分页结果 | `{...}` |
| - list | 按发生时间倒序的经验记录 | `[{...}]` |
| -- actionCode | 动作编码 | article.publish |
| -- experienceDelta | 实际经验变化，可为负 | 2 |
| -- beforeExperience | 变化前累计经验 | 48 |
| -- afterExperience | 变化后累计经验 | 50 |
| -- beforeLevelCode | 变化前等级编码 | L1 |
| -- afterLevelCode | 变化后等级编码 | L1 |
| -- occurredAt | 发生毫秒时间戳 | 1792600000000 |
| - pagination | 分页信息，含p、size、total、pageCount | `{...}` |

> 无本人真实职业进度返回404；有进度但无匹配记录返回空list。非法ID/分页返回422，资料异常或读取暂不可用返回503。仅返回经验记录概要，不含来源内容正文。

### 设置主职业

`PUT /api/v3/me/professions/primary`

选择当前已发布的主职业，需要 settings.write 和 Idempotency-Key。无需已有职业经验，不收取积分。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次操作的幂等键 | primary-001 |
| professionId | 已发布职业 ID，字符串 | 1700000000001 |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/professions/primary' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: primary-001' \
  --header 'Content-Type: application/json' \
  --data '{"professionId":"1700000000001"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 本次选择结果 | `{...}` |
| - onboardingState | 选择完成状态 | SELECTED |
| - primaryProfessionId | 主职业 ID | 1700000000001 |

> 只修改职业选择，不创建经验记录或更改隐私设置。成功后可重新读取本人职业状态。同键同请求重试返回原结果，不覆盖后续选择；同键更换职业返回 HTTP 409。无效参数返回 422，职业不存在或未发布返回 404。

### 跳过职业引导

`PUT /api/v3/me/professions/onboarding`

跳过职业选择引导，需要 settings.write 和 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次操作的幂等键 | onboarding-001 |
| onboardingState | 仅接受 SKIPPED | SKIPPED |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/professions/onboarding' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: onboarding-001' \
  --header 'Content-Type: application/json' \
  --data '{"onboardingState":"SKIPPED"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 本次引导设置结果 | `{...}` |
| - onboardingState | 跳过状态 | SKIPPED |
| - primaryProfessionId | 清空主职业 | 空字符串 |

> 已选主职业时也会清空选择，已有经验和隐私保持。同键重试返回原回执，不清空后续重新选择的主职业。请求体最多4096字节，超限413，无效参数422。

### 设置职业隐私

`PUT /api/v3/me/professions/privacy`

替换本人职业隐私设置，需要 settings.write 和 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次操作的幂等键 | profession-privacy-001 |
| preset | ALL_PUBLIC、LEVEL_ONLY、PRIMARY_ONLY、SELF_ONLY、FULLY_HIDDEN 或 CUSTOM | SELF_ONLY |
| moduleVisibility | CUSTOM 必填，完整模块与可见范围对象；其他预设按预设生成 | `{...}` |

moduleVisibility 模块：primaryProfession、allProfessions、experience、rarity、majorContributions、contributionStats、activityFeed、levelHistory、rewards、ranking。每项仅接受 PUBLIC、LOGGED_IN、SELF、HIDDEN。

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/professions/privacy' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: profession-privacy-001' \
  --header 'Content-Type: application/json' \
  --data '{"preset":"SELF_ONLY"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 本次隐私设置结果 | `{...}` |
| - privacyPreset | 预设 | SELF_ONLY |
| - moduleVisibility | 完整十模块可见范围 | `{...}` |
| -- primaryProfession | 主职业可见范围，其他模块同结构 | SELF |

> ALL_PUBLIC 全部公开；LEVEL_ONLY 与 PRIMARY_ONLY 当前均仅公开 primaryProfession，其他模块隐藏；SELF_ONLY 全部仅本人；FULLY_HIDDEN 全部隐藏。CUSTOM 必须完整提交十个模块。其他预设附带合法 moduleVisibility 时忽略其中值。主职业、引导状态和经验保持；同键重试不覆盖后续隐私设置。请求体最多4096字节，超限413；无效参数422，同键更换内容409。

### 职业成长提示

`GET /api/v3/professions/{professionId}/guides`

读取已发布职业的成长来源与等级奖励概要，需要profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| professionId | 路径参数，职业ID，1–19位数字 | 10 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/professions/10/guides' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 成长提示 | `{...}` |
| - professionId | 职业ID | 10 |
| - triggers | 按展示顺序排列的来源，允许同类型多条 | `[{"triggerType":"article.published"}]` |
| -- triggerType | 来源类型 | article.published |
| - rewards | 按展示顺序排列的等级奖励概要 | `[{"rewardType":"POINT","amount":12}]` |
| -- rewardType | POINT或MEDAL | POINT |
| -- amount | POINT奖励积分；MEDAL不含此字段 | 12 |

> 来源类型包括long_article.read_settled、article.published、comment.published、breezemoon.published、repeater.published、chatroom.message.published、user.online.settled。此概要仅说明成长来源和奖励类型，不保证每次操作都会增加经验或发放奖励。不存在或已退休职业返回404，非法职业ID返回422；资料异常或暂时无法完整读取返回503。无需已有本人职业进度，读取不会创建进度。

### 职业排行榜

`GET /api/v3/professions/{professionId}/ranking`

读取当前读者可见的职业排行榜，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| professionId | 已发布职业 ID，路径参数 | 10 |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20，最大50 | 20 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/professions/10/ranking?p=1&size=20' \
  -H 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.list | 当前页榜单 | [] |
| - rank | 当前读者可见榜单中的连续名次 | 1 |
| - userName | 公开用户名 | fish |
| - userNickname | 公开昵称，空昵称使用用户名 | 小鱼 |
| - userAvatarURL | 头像地址 | https://example.com/avatar.png |
| - professionId | 职业 ID | 10 |
| - professionCode | 职业编码 | writer |
| - professionName | 用户进度关联版本的职业名称 | 创作者 |
| - levelCode | 当前等级编码 | L1 |
| - levelName | 当前等级名称 | 初学者 |
| - totalExperience | 经验值，仅当前读者允许读取时返回 | 100 |
| data.pagination | 页码、条数、可见总数及总页数 | {} |

> 先筛选当前读者可见用户，再按经验降序及用户ID稳定排序、分页和编号。隐藏经验但允许上榜时仍显示名次与等级，不返回经验值。无可见用户时列表为空；职业未发布或不存在返回404，临时不可用返回503。

### 读取用户公开职业资料

`GET /api/v3/users/{userName}/professions`

读取用户允许展示的职业、贡献、奖励和近期动态概要。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| userName | 路径参数，用户名 | Alice |
| apiKey | 通用密钥（可选） | YOUR_API_KEY |
| Authorization | 请求头，Bearer accessToken（可选）；提供时需要 profile.read | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/users/Alice/professions'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 结果码 | 0 |
| data | 当前读者可见的职业资料 | 对象 |
| - primaryProfession | 可选，主职业定义或真实进度 | 对象 |
| -- professionId | 职业ID | 10 |
| -- professionCode | 职业编码 | writer |
| -- displayName | 职业名称 | 创作者 |
| -- shortName | 职业简称 | 创作 |
| -- description | 职业描述 | 创作与分享 |
| -- state | ACTIVE、RETIRED；选择但尚无进度时为SELECTED | ACTIVE |
| -- levelCode | 真实进度的等级编码 | L1 |
| -- levelName | 真实进度的等级名称 | 入门 |
| -- totalExperience | 经验可见时提供的总经验 | 50 |
| -- requiredExperience | 经验可见时提供的当前等级门槛 | 0 |
| -- rarity | 可选，与当前读者可见职业榜一致的排名概要 | 对象 |
| --- rank | 可见榜单名次 | 2 |
| --- totalUsers | 可见榜单人数 | 10 |
| --- surpassedPercent | 超过的可见用户百分比 | 80.0 |
| - professions | 可选，全部真实职业进度，字段同上 | 数组 |
| - majorContributions | 可选，贡献分类概要 | 数组 |
| -- professionId | 职业ID | 10 |
| -- actionCode | 贡献动作编码 | article.publish |
| -- eventCount | 事件次数 | 2 |
| -- lastOccurredAt | 最后发生时间，毫秒 | 1792636800000 |
| -- experienceSum | 经验可见时提供的合计变化 | 50 |
| - contributionStats | 可选，按职业汇总；含professionId、eventCount、lastOccurredAt及可见时的experienceSum | 数组 |
| - activityFeed | 可选，近90天最多30条当前可读公开来源动态 | 数组 |
| -- professionId | 职业ID | 10 |
| -- actionCode | 动作编码 | article.publish |
| -- occurredAt | 发生时间，毫秒 | 1792636800000 |
| -- experienceDelta | 经验可见时提供的变化，可为负 | 5 |
| - levelHistory | 可选，近90天最多30条当前可读公开来源等级变化 | 数组 |
| -- professionId | 职业ID | 10 |
| -- beforeLevelCode | 变化前等级编码 | L1 |
| -- afterLevelCode | 变化后等级编码 | L2 |
| -- occurredAt | 发生时间，毫秒 | 1792636800000 |
| - rewards | 可选，奖励概要 | 数组 |
| -- professionId | 职业ID | 10 |
| -- levelCode | 等级编码 | L1 |
| -- rewardCode | 奖励编码 | first |
| -- rewardType | POINT或MEDAL | MEDAL |
| -- status | PENDING、GRANTED、REVOKED | GRANTED |
| -- grantedAt | 发放时间，毫秒 | 1792636800000 |
| - ranking | 可选，当前可见榜中的本人职业名次；每项含professionId、rank、totalUsers、surpassedPercent | 数组 |

> 不可见模块或字段直接省略；没有可展示资料时返回404。HIDDEN内容在此公开接口对本人也不显示，本人完整资料请使用本人职业接口。
>
> 没有真实进度的主职业仅返回定义和SELECTED状态，不提供等级、经验或排名。经验不可见时，贡献和动态也不返回经验数值。
>
> 动态仅包含当前仍可阅读的公开来源概要；匿名和私密来源不在此公开摘要中关联。本文接口不提供来源正文。无权阅读或不进入当前可见榜的职业不返回对应排名；资料暂不可用时返回503。

> 名次按经验降序、用户ID稳定排序连续编号；surpassedPercent = (totalUsers - rank) × 100 / totalUsers，统计人数仅含当前读者可见用户。

## API v3 勋章

### 读取本人勋章

`GET /api/v3/me/medals`

分页读取本人有效勋章，包含隐藏勋章，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/medals?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 分页结果 | `{...}` |
| - list | 勋章列表，按显示顺序排列 | `[]` |
| -- id | 勋章定义 ID | annual |
| -- name | 名称，纯文本 | 年度勋章 |
| -- type | 类型 | special |
| -- description | 说明，纯文本 | 感谢参与 |
| -- order | 显示顺序 | 0 |
| -- expiresAt | 到期时间，毫秒时间戳；本人记录中小于等于 0 表示永久 | 0 |
| -- displayed | 是否展示 | false |
| - pagination | 分页信息，含 p、size、total、pageCount | `{...}` |

### 读取用户公开勋章

`GET /api/v3/users/{userName}/medals`

分页读取指定用户当前公开展示的有效勋章。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥（可选） | YOUR_API_KEY |
| Authorization | 请求头，Bearer accessToken（可选）；提供时需要 profile.read | Bearer YOUR_ACCESS_TOKEN |
| userName | 路径参数，用户名 | fishpi |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/users/fishpi/medals?p=1&size=20'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 分页结果 | `{...}` |
| - list | 公开勋章列表，字段同本人读取，不含 displayed | `[]` |
| -- expiresAt | 到期时间，毫秒时间戳；0 表示永久 | 0 |
| - pagination | 分页信息，含 p、size、total、pageCount | `{...}` |

> 隐藏或过期勋章不出现在公开结果中。用户不存在或不可访问时返回 HTTP 404。

### 设置勋章显示

`PUT /api/v3/me/medals/{medalId}/display`

设置本人有效勋章是否展示，需要 settings.write 和 Idempotency-Key，不收取积分。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次操作的幂等键 | medal-display-001 |
| medalId | 路径参数，勋章定义 ID，1–128 个 UTF-16 单位；保留原值并进行 URL 编码 | 0 |
| displayed | 布尔值，true 展示、false 隐藏 | false |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/medals/0/display' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: medal-display-001' \
  --header 'Content-Type: application/json' \
  --data '{"displayed":false}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 本次设置结果 | `{...}` |
| - id | 勋章定义 ID | 0 |
| - displayed | 本次设置的展示状态 | false |

> 本接口是设置操作，重复设置相同值不会反向切换。成功后可重新读取本人及公开勋章列表。同键同请求返回原结果，不覆盖后续设置；同键更换目标或状态返回 HTTP 409。无持有记录、过期或目标不存在返回 404；状态异常暂不可修改时返回 409。勋章顺序保持。

### 移动本人勋章

`POST /api/v3/me/medals/{medalId}/move`

将本人勋章上移或下移一个位置，需要 settings.write 和 Idempotency-Key，不收取积分。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次移动的幂等键 | medal-move-001 |
| medalId | 路径参数，勋章定义ID，1–128个UTF-16单位，保留原值并URL编码 | 0 |
| direction | up 或 down，忽略大小写，不去除首尾空白 | up |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/medals/0/move' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: medal-move-001' \
  --header 'Content-Type: application/json' \
  --data '{"direction":"up"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 本次移动结果 | `{...}` |
| - id | 勋章定义ID | 0 |
| - direction | 规范化后的方向 | up |
| - order | 本次移动后在全部持有勋章中的位置，从0开始 | 0 |

> 隐藏和过期的持有记录也参与排序。按原顺序相邻交换后，全部持有记录重新编号；原顺序相同时按持有记录标识稳定排序，已在边界时位置保持。展示开关和有效期不变，不删除过期记录。
>
> 同键同请求返回原回执，不再次移动或覆盖后续排序；同键更换目标或方向返回409。无持有记录返回404，数据异常暂不可排序返回409，参数无效返回422。成功后重新读取勋章列表取得当前顺序。

## API v3 会员

### 读取会员档位

`GET /api/v3/membership/levels`

分页读取会员档位及已知权益，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/membership/levels?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 分页结果 | `{...}` |
| - list | 档位列表 | `[]` |
| -- id | 档位 ID | 1700000000001 |
| -- code | 档位代码 | vip |
| -- name | 档位名称 | 会员 |
| -- price | 积分价格 | 1000 |
| -- durationType | 时长类型 | month |
| -- durationValue | 周期值，单位天 | 30 |
| -- benefits | 已知权益，字段同本人会员 config | `{...}` |
| - pagination | 分页信息，含 p、size、total、pageCount | `{...}` |

### 读取本人会员状态

`GET /api/v3/me/membership`

读取本人当前会员状态与已知配置，需要 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/membership' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 本人状态 | `{...}` |
| - active | 当前是否有效 | true |
| - levelCode | 当前档位代码，无有效会员时为空 | vip |
| - expiresAt | 到期时间，毫秒时间戳；有效会员的 0 表示永久 | 0 |
| - config | 已知配置；未设置或无有效会员时为空对象 | `{...}` |
| -- bold | 昵称加粗，可选 | true |
| -- underline | 昵称下划线，可选 | false |
| -- color | 昵称颜色或预设名称，可选 | #FF8800 |
| -- autoCheckin | 自动签到，可选 | true |
| -- checkinCard | 签到卡数量，可选 | 1 |
| -- metal | 勋章权益，可选 | true |
| -- jointVip | 联合会员权益，可选 | false |

> 未开通或已过期返回 active=false、空 levelCode、expiresAt=0 和空 config。未识别或格式不合法的可选权益字段不返回。


### 全量会员昵称样式快照

`GET /api/v3/membership/styles`

需要 `profile.read` 与有效 Bearer。首次不带参数，返回全部当前有效会员的展示配置；不再接受 `userIds`。之后传 `version`（64位小写 SHA-256），相同版本仅确认未变化，不重复传输名单。版本由排序后全部展示内容生成，人数不变但配置修改、会员新增/失效、自然到期都会改变版本。

```bash
curl 'https://fishpi.cn/api/v3/membership/styles' --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
curl --get 'https://fishpi.cn/api/v3/membership/styles' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --data-urlencode 'version=PREVIOUS_64_CHARACTER_VERSION'
```

响应 data：

| 字段 | 说明 |
| --- | --- |
| version | 当前完整展示快照的 SHA-256，64位小写十六进制 |
| unchanged | 与请求 version 相同为 true，此时不返回 list |
| maxAgeMs | 本次可缓存毫秒数，0～30000；受最近会员自然到期时间约束 |
| list | 仅 unchanged=false 时返回，按用户 ID 字符串稳定排序的完整列表 |
| list[].userId | 用户 ID 字符串 |
| list[].configJson | 规范化 JSON 字符串，仅包含 bold、underline、color；异常/重复绑定或缺少合法档位定义时为 null，表示未知 |

`configJson="{}"` 表示没有可展示字段；`null` 不能解释为非会员。color 为 `#RRGGBB`，有效 VIP4 另外支持八种效果；只投影档位支持且本人设置有效的字段，不补默认值。不返回原始配置中的自动签到等其他权益、会员档位、到期时间或后台字段。过期/停用用户不进入正常名单；异常到期数据无法判断时保留 ID，配置为 null。

服务端单节点共享快照最长30秒，同节点会员缓存变更或档位修改后失效，自然到期会提前失效；其他节点/直接 SQL 更新由 TTL 兜底。缓存命中与 unchanged 均重新验证当前账号，HTTP仍为 `Cache-Control: no-store`；客户端仅内存缓存。没有新增匿名豁免或 Cookie/apiKey 回退。

单次输入候选（含重复关联行）最多10000条、原始配置/档位输入与规范化输出各最多2000000字节；超预算503，不以截断结果冒充完整名单。单个损坏配置为null，不拖垮其余用户；数据库不可用仍为503。参数污染、旧userIds、非法版本返回422。

### 设置本人会员展示

`PUT /api/v3/me/membership/config`

部分更新当前有效会员的展示设置，需要settings.write和Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次操作的幂等键 | membership-config-001 |
| bold | 可选，布尔值，是否加粗 | true |
| underline | 可选，布尔值，是否显示下划线 | false |
| color | 可选，#RRGGBB；VIP4_YEAR/VIP4_MONTH额外支持八种效果 | #AABBCC |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/membership/config' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: membership-config-001' \
  --header 'Content-Type: application/json' \
  --data '{"bold":true,"color":"#AABBCC"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 本次配置结果 | `{...}` |
| - config | 展示字段，仅返回已有合法值 | `{...}` |
| -- bold | 是否加粗 | true |
| -- underline | 是否显示下划线 | false |
| -- color | 颜色或效果 | #AABBCC |

> 至少提交一个字段，仅接受当前档位支持的字段。未提交项和其他会员权益保持，档位、有效期和积分不变。VIP4额外效果为rainbow、neon、fire、ocean、forest、sunset、metal、galaxy，区分大小写。
>
> 同键同请求恢复原回执，不覆盖之后的配置；同键更换内容返回409。无当前有效会员返回404，参数无效422，请求体超过4096字节413，配置暂不可用503。成功后重新读取本人会员状态取得当前结果。

### 积分开通本人会员

`POST /api/v3/me/membership`

按当前档位价格使用积分开通会员，需要activity.write和Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次开通的幂等键 | membership-open-001 |
| levelId | 会员档位列表中的id，1–19位数字字符串 | 1700000000001 |
| couponCode | 可选优惠券，去除首尾普通空白后8–32个UTF-16单位；省略或空白表示不用券 | COUPON1234 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/membership' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: membership-open-001' \
  --header 'Content-Type: application/json' \
  --data '{"levelId":"1700000000001","couponCode":"COUPON1234"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 本次开通结果 | `{...}` |
| - id | 本人会员记录ID | 1700000000002 |
| - levelCode | 开通档位代码 | VIP1_YEAR |
| - paidPoints | 实际扣除积分，优惠价格四舍五入 | 1000 |
| - expiresAt | 到期毫秒时间戳 | 1824249600000 |

> 仅当前未激活的非永久会员、尚无会员记录或已到期账号可开通；有效会员和永久会员返回409。期限按服务器时区从次日零点起加durationValue个自然日，durationType为周期名称。配置由服务端按所选档位默认值初始化，不接受用户指定权益、价格或账号。
>
> 无效、非折扣、用尽或不可用优惠券返回422，不扣积分；积分不足返回409。VIP1_YEAR、VIP2_YEAR、VIP3_YEAR分别赠送20、50、120张免签卡，VIP4_YEAR增加366天免签额度；其他档位无此礼包。背包达到数量上限时保持上限。扣费、优惠券次数、会员记录及礼包一起成功或失败。
>
> 同键同请求恢复原结果，不重复扣费、用券或赠送；同键更换内容返回409。请求体超过4096字节413，参数无效422，档位不存在404，暂不可开通503。成功后重新读取本人会员、背包与积分流水；响应中断时保留原幂等键重试。

## API v3 OAuth 客户端管理

### 读取客户端列表

`GET /api/v3/admin/oauth/clients`

管理员读取已登记的第三方客户端，需要网站第一方 Bearer accessToken 和 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，网站第一方 Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/admin/oauth/clients?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 分页结果 | `{...}` |
| - list | 第三方客户端列表 | `[]` |
| -- clientId | 客户端标识 | app_YOUR_CLIENT_ID |
| -- name | 名称 | 示例客户端 |
| -- scope | 已登记权限，空格分隔 | content.read profile.read |
| -- status | 0 启用，1 停用 | 0 |
| - pagination | 分页信息，含 p、size、total、pageCount | `{...}` |

### 读取客户端详情

`GET /api/v3/admin/oauth/clients/{clientId}`

管理员读取一个第三方客户端及其分页回调地址，需要网站第一方 Bearer accessToken 和 settings.write。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，网站第一方 Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| clientId | 路径参数，客户端标识 | app_YOUR_CLIENT_ID |
| p | 回调列表页码，默认 1 | 1 |
| size | 回调每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/admin/oauth/clients/app_YOUR_CLIENT_ID?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 客户端详情 | `{...}` |
| - clientId | 客户端标识 | app_YOUR_CLIENT_ID |
| - name | 名称 | 示例客户端 |
| - scope | 已登记权限，空格分隔 | content.read profile.read |
| - status | 0 启用，1 停用 | 0 |
| - redirects | 回调分页结果 | `{...}` |
| -- list | 回调列表 | `[]` |
| --- uri | 完整回调地址 | https://app.example/callback |
| --- status | 0 启用，1 停用 | 0 |
| -- pagination | 分页信息，含 p、size、total、pageCount | `{...}` |

> 不提供网站内置客户端的管理详情。目标不存在或不属于可管理范围时返回 HTTP 404。

### 登记第三方客户端

`POST /api/v3/admin/oauth/clients`

管理员登记已批准的第三方客户端，需要网站第一方 Bearer accessToken、settings.write 和 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，网站第一方 Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次登记的幂等键 | oauth-client-001 |
| name | 客户端名称，1–128 个 UTF-16 单位，保存时去除首尾空白 | 示例客户端 |
| scope | 已实现的权限范围，空格分隔，最多 255 字符 | content.read profile.read |
| redirectUris | 1–20 个完整回调地址，每个最多 2048 字符，不允许重复 | `["https://app.example/callback"]` |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/admin/oauth/clients' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: oauth-client-001' \
  --header 'Content-Type: application/json' \
  --data '{"name":"示例客户端","scope":"content.read profile.read","redirectUris":["https://app.example/callback"]}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 登记结果 | `{...}` |
| - clientId | 服务端分配的客户端标识 | app_YOUR_CLIENT_ID |
| - name | 登记名称 | 示例客户端 |
| - scope | 规范化后的权限范围 | content.read profile.read |
| - status | 初始为启用 | 0 |
| - redirectUris | 已登记的完整回调地址 | `["https://app.example/callback"]` |

> 回调支持 HTTPS；开发环境允许 HTTP 回环地址；原生客户端可登记专用的反向域名 scheme。回调必须精确匹配登记值，禁止用户凭据、片段和预置 OAuth 响应参数。无效字段或回调返回 HTTP 422。
>
> 普通用户或第三方应用令牌不能管理客户端。相同幂等键和请求恢复原登记结果，不重复创建；同键更换内容返回 HTTP 409。响应中断后请使用原键和原请求重试。


### 修改第三方客户端

`PUT /api/v3/admin/oauth/clients/{clientId}`

完整替换登记名称、权限和回调地址，需要网站第一方管理员 Bearer accessToken、settings.write 和 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，网站第一方 Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次修改的幂等键 | oauth-update-001 |
| clientId | 路径参数，第三方客户端标识 | app_YOUR_CLIENT_ID |
| name | 必填，名称1–128个UTF-16单位，去除首尾空白 | 示例客户端 |
| scope | 必填，已实现权限，空格分隔，最多255字符 | content.read profile.read |
| redirectUris | 必填，1–20个完整回调，每个最多2048字符，禁止重复 | `["https://app.example/callback"]` |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/admin/oauth/clients/app_YOUR_CLIENT_ID' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: oauth-update-001' \
  --header 'Content-Type: application/json' \
  --data '{"name":"示例客户端","scope":"content.read profile.read","redirectUris":["https://app.example/callback"]}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 修改结果 | `{...}` |
| - clientId | 客户端标识 | app_YOUR_CLIENT_ID |
| - name | 规范化后的名称 | 示例客户端 |
| - scope | 规范化后的权限范围 | content.read profile.read |
| - status | 保留当前状态，0启用、1停用 | 0 |
| - redirectUris | 完整回调地址集合 | `["https://app.example/callback"]` |

> 回调格式与登记接口一致。权限集合或回调地址集合改变后，该应用全部已有令牌、旧授权码和待确认授权失效，用户必须重新授权；仅名称修改、回调顺序或权限顺序变化不撤销授权。已停用客户端修改后仍停用；网站内置客户端不可修改。
>
> 同键同请求恢复原结果，不覆盖后续修改；同键更换内容返回409。无效字段或回调返回422，目标不存在或不可管理返回404。

### 停用第三方客户端

`DELETE /api/v3/admin/oauth/clients/{clientId}`

管理员停用已登记第三方客户端并撤销其已有令牌，需要网站第一方 Bearer accessToken、settings.write 和 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，网站第一方 Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，本次停用的幂等键 | oauth-disable-001 |
| clientId | 路径参数，第三方客户端标识 | app_YOUR_CLIENT_ID |

请求示例：

```bash
curl --request DELETE 'https://fishpi.cn/api/v3/admin/oauth/clients/app_YOUR_CLIENT_ID' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: oauth-disable-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 停用结果 | `{...}` |
| - clientId | 客户端标识 | app_YOUR_CLIENT_ID |
| - status | 1 表示停用 | 1 |

> 无需请求体，或提交 application/json 空对象 `{}`；请求体最多1024字节。超限返回413，额外字段返回422。客户端登记资料和回调保留，已有令牌失效，不能继续授权或换码；网站内置客户端不可停用。目标不存在或不可管理时返回404。
>
> 同键同目标恢复原结果，不重复停用；同键更换目标返回409。已停用目标使用新键仍返回成功。原键不会覆盖后续状态，如需再次执行新的停用操作请使用新键。未提供重新启用接口。

## API v3 邀请码

### 查询邀请码状态

`POST /api/v3/invitation-codes/status`

查询一个已知邀请码的存储状态，需要 activity.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| code | 邀请码，去除首尾空白后16位字母或数字 | AbCdEf0123456789 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/invitation-codes/status' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Content-Type: application/json' \
  --data '{"code":"AbCdEf0123456789"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 查询结果 | `{...}` |
| - status | 存储状态：unused 未使用、used 已使用、stopped 停用 | unused |

> 请求体最多1024字节。unused 不保证当前可用于注册，是否可用以注册接口结果为准。不存在返回 HTTP 404，非法参数返回 422。


### 购买邀请码

`POST /api/v3/me/invitation-codes`

购买一枚邀请码，当前邀请注册模式可用，每次100积分，余额不足不购买。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer访问令牌，需activity.write | Bearer ACCESS_TOKEN |
| Idempotency-Key | 本次购买唯一键；重试使用同一键 | invitation-20261022-001 |

请求体可省略或为空JSON对象。

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/invitation-codes' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: invitation-20261022-001' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 购买结果 | object |
| - code | 本次购买的16位邀请码 | AbCdEf0123456789 |
| - expiresAt | 到期时间，Unix毫秒 | 1792900000000 |
| - paidPoints | 实付积分 | 100 |

> 同一幂等键返回原邀请码和到期时间，不重复购买。即使邀请码已使用、过期或注册模式已变化，重试恢复的仍是原购买结果，不代表当前可注册。另一次购买使用新键并重新校验当前条件。

> 邀请码属于可使用凭据，请妥善保管。余额不足或当前模式不支持购买返回409；参数不合法返回422。

## API v3 本人匿名内容

### 读取本人匿名文章列表

`GET /api/v3/me/anonymous-articles`

分页读取当前账号的匿名文章概要，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/anonymous-articles?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 分页结果 | `{...}` |
| - list | 本人匿名文章概要，按创建时间、ID 降序 | `[]` |
| -- id | 文章 ID | 1700000000001 |
| -- title | 纯文本标题 | 我的文章 |
| -- type | 文章类型，取值同文章接口 | 0 |
| -- createdAt | 创建时间，毫秒时间戳 | 1700000000000 |
| -- updatedAt | 更新时间，毫秒时间戳 | 1700000000000 |
| - pagination | 分页信息，含 p、size、total、pageCount | `{...}` |

> 仅包含当前账号的有效匿名文章；文章正文请使用文章详情接口读取。

### 读取本人匿名评论列表

`GET /api/v3/me/anonymous-comments`

分页读取当前账号在当前可读文章中的匿名评论概要，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| p | 页码，默认 1 | 1 |
| size | 每页条数，默认 20，最大 50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/anonymous-comments?p=1&size=20' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 分页结果 | `{...}` |
| - list | 本人匿名评论概要，按创建时间、ID 降序 | `[]` |
| -- id | 评论 ID | 1700000000002 |
| -- articleId | 所属文章 ID | 1700000000001 |
| -- articleTitle | 所属文章纯文本标题 | 文章标题 |
| -- createdAt | 评论创建时间，毫秒时间戳 | 1700000000000 |
| -- visibility | 0 普通，1 私密 | 0 |
| - pagination | 分页信息，含 p、size、total、pageCount | `{...}` |

> 讨论帖按当前阅读权限筛选。已删除或当前无权阅读的目标不列出；评论正文请使用评论详情接口读取。暂时无法完成查询时返回 HTTP 503。


## API v3 举报

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
| - reasons | 当前目标可用理由。聊天室消息支持垃圾广告、低俗色情、违法违规、涉嫌侵权、人身攻击、其他 | [] |
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

> 不支持自举报；聊天室消息按消息ID举报，理由仅49。同一目标已有本人未处理举报时返回原ID；处理后可使用新幂等键再次举报。相同幂等键和请求内容始终返回原举报ID，不创建新举报；同键不同内容返回409。

## API v3 积分转账

### 转账积分

`POST /api/v3/me/point-transfers`

向当前有效的其他用户转账，需要 `activity.write`。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer 访问令牌，请求头 | Bearer ACCESS_TOKEN |
| Idempotency-Key | 同一次操作使用同一幂等键，请求头 | points-transfer-001 |
| userName | 接收者用户名，1至64位字母、数字、下划线或连字符，不支持自己或机器人 | receiver |
| amount | 含手续费的整数总额，至少2积分 | 100 |
| memo | 可选，trim后最多128字符，清除HTML | 谢谢 |

请求示例：

```bash
curl -X POST 'http://localhost:8080/api/v3/me/point-transfers' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: points-transfer-001' \
  -H 'Content-Type: application/json' \
  --data '{"userName":"receiver","amount":100,"memo":"谢谢"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 状态码 | 0 |
| msg | 状态说明 | 空字符串 |
| data | 转账结果 | 对象 |
| - transferId | 转账记录ID | 1760000000000 |
| - recipient | 接收者 | 对象 |
| -- userId | 接收者ID | 10001 |
| -- userName | 本次转账时用户名 | receiver |
| - amount | 总扣款积分 | 100 |
| - receivedPoints | 主转账到账积分，不含手续费款项 | 99 |
| - feePoints | 手续费积分 | 1 |
| - memo | 处理后的备注 | 谢谢 |

> 普通手续费为总额的1%向下取整，至少1积分；有效的VIP4_YEAR/VIP4_MONTH及手续费收款账号本人发起时免手续费。总额包含手续费。余额不足或接收者积分超过上限时返回409，不扣款。
>
> 同键同请求返回原转账结果，接收者后续改名或停用不改变该历史结果；同键不同请求返回409。旧转账接口保持原规则。

> 接收者同时是手续费收款账号时，会分别收到主款和手续费；receivedPoints仅表示主款。备注处理后过长或格式不合法返回422。


## API v3 文章与评论历史

### 文章历史列表

`GET /api/v3/articles/{articleId}/revisions`

分页读取文章历史元数据。需要Bearer Token（content.read）；作者本人可读，其他读者还需文章历史查看权限，并且当前可以阅读文章。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| articleId | 文章ID | 1636516552191 |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20，最多50 | 20 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/articles/1636516552191/revisions?p=1&size=20' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.items | 按时间升序的历史元数据 | [] |
| - id | 版本ID；current表示当前虚拟版本 | 1761000000000 |
| - time | 版本时间戳（毫秒） | 1761000000000 |
| - index | 从1开始的全列表序号 | 1 |
| - current | 是否当前虚拟版本 | false |
| data.pagination | 分页信息 | {} |
| - page | 当前页 | 1 |
| - size | 每页条数 | 20 |
| - total | 版本总数，包含存在的current | 3 |
| - pageCount | 总页数 | 1 |

> 只有已有存储历史且当前标题或正文与最后版本忽略空白后不同时，才会追加current。历史不包含每次编辑的完整审计；正文没有当时权限快照，当前获准读者可能读到后来删除的文字。

### 文章历史详情

`GET /api/v3/articles/{articleId}/revisions/{revisionId}`

读取清洗后的历史标题和正文。需要Bearer Token（content.read），访问范围同文章历史列表。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| articleId | 文章ID | 1636516552191 |
| revisionId | 列表中的版本ID或current | 1761000000000 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/articles/1636516552191/revisions/1761000000000' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.id | 版本ID | 1761000000000 |
| data.time | 版本时间戳（毫秒） | 1761000000000 |
| data.index | 全列表序号 | 1 |
| data.current | 是否当前虚拟版本 | false |
| data.title | HTML编码后的标题 | 历史标题 |
| data.content | 沿旧历史格式清洗后的正文，保留换行，并非原始Markdown | 历史正文 |

> 不返回编辑者真实ID或额外的打赏内容。current仅在历史列表确有该条目时存在，否则404。

### 评论历史列表

`GET /api/v3/comments/{commentId}/revisions`

分页读取评论历史元数据。需要Bearer Token（content.read）；评论作者本人可读，其他读者还需评论历史查看权限，且当前可读评论及所属文章。私密评论只允许评论作者、文章作者双方。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| commentId | 评论ID | 1761000000100 |
| p | 页码，默认1 | 1 |
| size | 每页条数，默认20，最多50 | 20 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/comments/1761000000100/revisions?p=1&size=20' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.items | 升序历史元数据，结构同文章历史列表 | [] |
| - id | 版本ID或current | current |
| - time | 存储版本时间戳；评论current修改时间未知，返回null | null |
| - index | 全列表序号 | 2 |
| - current | 是否当前虚拟版本 | true |
| data.pagination | page、size、total、pageCount | {} |

> 仅已有存储历史且当前原始正文与最后存储版本忽略空白后不同时追加current。不返回匿名作者真实ID；历史正文没有当时权限快照，当前获准读者可能读到后来删除的文字。

### 评论历史详情

`GET /api/v3/comments/{commentId}/revisions/{revisionId}`

读取清洗后的评论历史正文。需要Bearer Token（content.read），访问范围同评论历史列表。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| commentId | 评论ID | 1761000000100 |
| revisionId | 列表中的版本ID或current | current |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/comments/1761000000100/revisions/current' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.id | 版本ID或current | current |
| data.time | 存储版本时间戳；current为null | null |
| data.index | 全列表序号 | 2 |
| data.current | 是否当前虚拟版本 | true |
| data.content | 沿旧历史格式清洗后的正文，保留换行，并非原始Markdown | 评论正文 |

> current仅在列表确有该条目时存在，否则404；不返回修订者真实ID或其他内部字段。


## API v3 图片表情分组管理

### 创建图片表情分组

`POST /api/v3/me/settings/emoji-groups`

创建自定义分组。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| name | trim后1至64个UTF-16单位纯文本名称，支持emoji | 摸鱼 😀 |
| sort | 可选，32位整数排序，默认0，越小越靠前，可并列 | 0 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emoji-groups' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: emoji-group-create-001' \
  -H 'Content-Type: application/json' \
  -d '{"name":"摸鱼 😀","sort":0}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 分组 | 对象 |
| - id | 分组ID | 1790000000000 |
| - name | 分组名称 | 摸鱼 😀 |
| - sort | 排序 | 0 |
| - type | 自定义分组类型 | 0 |

> 名称不能包含控制字符或HTML标签；本人已有同名组返回409。创建自定义分组不会自动创建全部分组。

### 修改图片表情分组

`PUT /api/v3/me/settings/emoji-groups/{groupId}`

修改本人自定义分组名称与排序。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，分组ID | 1790000000000 |
| name | trim后1至64个UTF-16单位纯文本名称 | 收藏 |
| sort | 可选，32位整数排序，省略设为0 | 1 |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: emoji-group-update-001' \
  -H 'Content-Type: application/json' \
  -d '{"name":"收藏","sort":1}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 分组 | 对象 |
| - id | 分组ID | 1790000000000 |
| - name | 修改后名称 | 收藏 |
| - sort | 修改后排序 | 1 |
| - type | 自定义分组类型 | 0 |

> 全部分组不能修改，返回409；不存在或非本人分组返回404。同键重试返回原结果，不覆盖后来完成的修改。

### 删除图片表情分组

`DELETE /api/v3/me/settings/emoji-groups/{groupId}`

删除本人自定义分组及其分组成员。需要 Bearer Access Token（settings.write）及 Idempotency-Key，无需正文。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，分组ID | 1790000000000 |

请求示例：

```bash
curl -X DELETE 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: emoji-group-delete-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 删除结果 | 对象 |
| - id | 分组ID | 1790000000000 |
| - deleted | 已删除 | true |

> 全部分组不能删除，返回409。不会删除其他分组中的同图片，也不会删除已有分享快照。同键重试返回原成功结果；新键删除不存在的组返回404。


## API v3 图片表情成员管理

### 登记图片表情到全部分组

`POST /api/v3/me/settings/emojis`

登记HTTPS图片地址到本人全部分组。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| url | HTTPS图片地址，最多512个UTF-16单位，完整保留query | https://example.com/a.png?sign=AbC |
| name | 可选，trim后最多64个UTF-16单位纯文本，可空 | 摸鱼 😀 |
| sort | 可选32位整数，默认0；新增时不大于0则追加 | 0 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emojis' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-upload-001' \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/a.png?sign=AbC","name":"摸鱼 😀"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 成员 | 对象 |
| - id | 分组成员ID | 1790000000001 |
| - groupId | 全部分组ID | 1790000000000 |
| - emojiId | 全局图片ID | 1790000000002 |
| - name | 本组成员名称 | 摸鱼 😀 |
| - sort | 本组排序 | 1 |
| - url | 完整图片地址 | https://example.com/a.png?sign=AbC |

> 全部分组不存在时自动创建。此接口登记图片地址，不接收文件。地址不能包含用户凭据或片段。已存在成员返回原成员，不覆盖名称和排序。

### 添加图片表情到分组

`POST /api/v3/me/settings/emoji-groups/{groupId}/emojis`

按HTTPS地址或有效全局图片ID添加到本人分组。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，本人分组ID | 1790000000000 |
| url | 与emojiId二选一，HTTPS、最多512个UTF-16单位 | https://example.com/a.png |
| emojiId | 与url二选一，有效全局图片ID | 1790000000002 |
| name | 可选，trim后最多64个UTF-16单位纯文本，可空 | 收藏 |
| sort | 可选32位整数，默认0；新增时不大于0则追加 | 0 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000/emojis' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-add-001' \
  -H 'Content-Type: application/json' -d '{"emojiId":"1790000000002","name":"收藏"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 目标组成员 | 对象 |
| - id | 分组成员ID | 1790000000001 |
| - groupId | 目标分组ID | 1790000000000 |
| - emojiId | 全局图片ID | 1790000000002 |
| - name | 本组成员名称 | 收藏 |
| - sort | 本组排序 | 1 |
| - url | 完整图片地址 | https://example.com/a.png |

> 自定义分组添加时同时加入全部分组；缺少全部分组时自动创建。重复添加返回原成员，保留原名称/排序，并补齐缺少的全部分组副本。相同URL按完整路径与query区分大小写。名称不能包含控制字符或HTML标签。

### 修改分组图片表情

`PUT /api/v3/me/settings/emoji-groups/{groupId}/emojis/{itemId}`

修改当前分组成员名称和排序。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，本人分组ID | 1790000000000 |
| itemId | 路径参数，该组成员ID，即GET返回的id | 1790000000001 |
| name | 可选，trim后最多64个UTF-16单位纯文本，省略或空字符串设为空 | 新名称 |
| sort | 可选32位整数，省略设为0，可负数或并列 | -1 |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000/emojis/1790000000001' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-update-001' \
  -H 'Content-Type: application/json' -d '{"name":"新名称","sort":-1}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 修改后成员 | 对象 |
| - id | 分组成员ID | 1790000000001 |
| - groupId | 分组ID | 1790000000000 |
| - emojiId | 全局图片ID | 1790000000002 |
| - name | 修改后名称 | 新名称 |
| - sort | 修改后排序 | -1 |
| - url | 完整图片地址 | https://example.com/a.png |

> 仅修改该分组成员，不同步改其他组同图片的名称/排序。itemId是分组成员ID，不是emojiId。

### 移出分组图片表情

`DELETE /api/v3/me/settings/emoji-groups/{groupId}/emojis/{itemId}`

移出本人分组成员。需要 Bearer Access Token（settings.write）及 Idempotency-Key，无需正文。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，本人分组ID | 1790000000000 |
| itemId | 路径参数，该组成员ID | 1790000000001 |

请求示例：

```bash
curl -X DELETE 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000/emojis/1790000000001' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-remove-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 移除结果 | 对象 |
| - id | 成员ID | 1790000000001 |
| - groupId | 目标组ID | 1790000000000 |
| - deleted | 已移除 | true |

> 从自定义分组移出仅影响该组；从全部分组移出会同步移出本人所有分组。不会删除其他用户成员或已有分享。同键重试返回原结果；新键操作不存在成员返回404。


## API v3 鱼游投稿与编辑

### 投稿鱼游

`POST /api/v3/fish-games`

提交鱼游并进入待审，需要content.write。授权网址按[第三方接入说明](https://fishpi.cn/article/1782119519493)检查参数和同站回调；管理员审核通过后才公开。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer访问令牌，请求头 | Bearer ACCESS_TOKEN |
| Idempotency-Key | 本次申请唯一键，重试沿用 | fish-game-submit-001 |
| name | 名称，清洗后1至80字符 | 示例游戏 |
| description | 描述，清洗后1至1000字符 | 游戏说明 |
| url | HTTPS游戏地址，最多512字符 | https://game.example/play |
| iconUrl | HTTPS图标地址，最多512字符 | https://game.example/icon.png |
| oauthLoginUrl | 旧OpenID授权地址，最多4096字符 | https://fishpi.cn/openid/login?... |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/fish-games' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: fish-game-submit-001' \
  -H 'Content-Type: application/json' \
  --data '{"name":"示例游戏","description":"游戏说明","url":"https://game.example/play","iconUrl":"https://game.example/icon.png","oauthLoginUrl":"https://fishpi.cn/openid/login?openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0&openid.mode=checkid_setup&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.return_to=https%3A%2F%2Fgame.example%2Fcallback&openid.realm=https%3A%2F%2Fgame.example&fishpi.scope=profile.read"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本次申请结果 | object |
| - id | 鱼游ID | 1760000000000 |
| - status | 本次申请时状态；0待审、1已审核 | 0 |
| - editPending | 本次是否提交编辑申请 | false |

> 授权地址只允许接入说明列出的参数。return_to和realm各最多2048字符，均使用与游戏相同的HTTPS站点和有效端口；realm为return_to的前缀。拒绝重复参数、用户凭据、片段和相似域名。scope仅支持该说明中的五种权限，省略或为空按profile.read；本接口只检查地址结构，不代表已验证站点归属或实际完成授权。
>
> 当前网址或其他待审编辑已占用该网址返回409。同键同请求恢复原申请，不重复创建；同键不同内容409。响应记录申请时状态，后续审核状态请通过鱼游查询读取。

### 申请编辑本人鱼游

`PUT /api/v3/fish-games/{gameId}`

对本人已审核鱼游提交完整编辑申请，需要content.write。请求头和五个必填内容字段与投稿相同；每次使用新的幂等键。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| gameId | 路径参数，本人已审核鱼游ID | 1760000000000 |
| Authorization | Bearer访问令牌，请求头 | Bearer ACCESS_TOKEN |
| Idempotency-Key | 本次编辑唯一键 | fish-game-edit-001 |
| name | 修改后名称，最多80字符 | 新名称 |
| description | 修改后描述，最多1000字符 | 新说明 |
| url | 修改后HTTPS游戏地址，最多512字符 | https://game.example/new |
| iconUrl | 修改后HTTPS图标地址，最多512字符 | https://game.example/icon.png |
| oauthLoginUrl | 对应游戏同站回调的授权地址，最多4096字符 | https://fishpi.cn/openid/login?... |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/fish-games/1760000000000' \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -H 'Idempotency-Key: fish-game-edit-001' \
  -H 'Content-Type: application/json' \
  --data '{"name":"新名称","description":"新说明","url":"https://game.example/new","iconUrl":"https://game.example/icon.png","oauthLoginUrl":"https://fishpi.cn/openid/login?openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0&openid.mode=checkid_setup&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select&openid.return_to=https%3A%2F%2Fgame.example%2Fcallback&openid.realm=https%3A%2F%2Fgame.example&fishpi.scope=profile.read"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本次编辑申请 | object |
| - id | 鱼游ID | 1760000000000 |
| - status | 已审核作品状态 | 1 |
| - editPending | 已提交待审核编辑 | true |

> 申请期间公开内容保持原样；批准后才生效，拒绝后保留原公开内容。非本人或不存在返回404；未审核作品或已有待审核编辑返回409。


## API v3 图片表情分享与迁移

### 分享图片表情分组

`POST /api/v3/me/settings/emoji-groups/{groupId}/shares`

生成本人自定义分组的分享快照。需要 Bearer Access Token（settings.write）及 Idempotency-Key，无需正文。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| groupId | 路径参数，本人自定义分组ID | 1790000000000 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emoji-groups/1790000000000/shares' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-share-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 分享结果 | 对象 |
| - shareCode | 分享码 | ABCDEFGH1234 |
| - groupName | 快照分组名 | 摸鱼 |
| - emojiCount | 快照内不同图片数 | 12 |
| - createdTime | 创建时间，毫秒 | 1790000000000 |

> 仅非空自定义组可分享。快照不随来源组修改或删除失效，分享码长期有效；持码用户可导入。任一图片地址、名称或状态不符合规则时整批422，不生成分享码。

### 导入图片表情分享

`POST /api/v3/me/settings/emoji-group-imports`

从分享快照创建本人独立分组，并加入全部分组。需要 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| shareCode | 分享码，忽略首尾空白及字母大小写 | ABCDEFGH1234 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emoji-group-imports' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-import-001' \
  -H 'Content-Type: application/json' -d '{"shareCode":"ABCDEFGH1234"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 导入结果 | 对象 |
| - groupId | 新分组ID | 1790000000001 |
| - groupName | 新分组名，重名时添加导入后缀 | 摸鱼-导入 |
| - emojiCount | 实际不同图片数 | 12 |
| - sourceGroupName | 分享时的分组名 | 摸鱼 |

> 每个新幂等键创建独立组；相同键重试返回同组。同名依次追加-导入、-导入2等，名称总长不超过64个UTF-16单位。任一地址、名称、结构或图片状态无效，整批422且不创建组。来源组删除不影响快照。

### 迁移历史图片表情

`POST /api/v3/me/settings/emoji-migrations`

将本人历史云表情复制到全部分组，保留历史云数据及已有成员。需要 Bearer Access Token（settings.write）及 Idempotency-Key，无需正文。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| 无 | 使用本人历史数据 | — |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/settings/emoji-migrations' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: emoji-migrate-001'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 迁移结果 | 对象 |
| - groupId | 全部分组ID | 1790000000000 |
| - processedCount | 本次历史数据中不同图片地址数 | 12 |
| - addedCount | 实际新加入全部分组的成员数 | 8 |

> 已有成员保留名称和排序。相同键重试返回原结果；使用新键再次迁移时，已存在成员不重复增加。无历史数据或空数组返回409；任一历史项无效则整批422，不部分迁移。


## API v3 账号安全设置

### MFA状态

`GET /api/v3/me/security/mfa`

读取本人MFA启用状态。仅第一方客户端，需要Bearer Token（profile.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/security/mfa' -H 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.enabled | 是否已启用MFA | true |

### 修改密码

`PUT /api/v3/me/settings/password`

修改本人密码。仅第一方客户端，需要Bearer Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| currentPassword | 当前密码的小写32位MD5摘要 | 32位小写十六进制 |
| newPassword | 新密码的小写32位MD5摘要 | 32位小写十六进制 |
| currentTotp | 已启用MFA时必填，当前6位验证码字符串 | 123456 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/settings/password' -X PUT -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' --data '{"currentPassword":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","newPassword":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","currentTotp":"123456"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.changed | 是否成功 | true |
| data.reauthenticationRequired | 需要重新登录 | true |

> 成功后所有既有登录及授权凭据失效，需重新登录或授权。错误密码或MFA证明返回422。请求成功后旧凭据不能继续重试；遇网络未收到响应，请重新登录核实结果。
>
> 网站客户端的新密码输入为6至128个UTF-16字符，保留首尾空白、允许Unicode，不接受控制字符或无效代理字符；当前密码不截断或规范化。接口仍使用上述32位小写MD5摘要字段。


## API v3 MFA准备与设置

### 准备绑定 MFA

`POST /api/v3/me/security/mfa/preparations`

为当前未启用MFA的账号生成绑定资料。需要第一方 Bearer Access Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| currentPassword | 当前密码的小写32位MD5摘要 | 0123456789abcdef0123456789abcdef |
| currentTotp | 已启用MFA时需要当前6位验证码字符串 | 012345 |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/me/security/mfa/preparations' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' \
  -d '{"currentPassword":"0123456789abcdef0123456789abcdef"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 当次绑定资料 | 对象 |
| - preparationId | 本次准备标识 | PREPARATION_ID |
| - secret | 本次认证器密钥，仅当次返回 | BASE32_SECRET |
| - otpauthUri | 可供本地认证器绑定的URI，仅当次返回 | otpauth://totp/... |
| - expiresAt | 失效时间，毫秒 | 1790000600000 |

> 准备十分钟有效，新请求会废弃上一次准备。密钥及URI无法重复取回；响应丢失时请重新准备。已启用MFA需先停用后再绑定，返回409。请在本地展示或录入绑定资料。

### 启用 MFA

`PUT /api/v3/me/security/mfa`

验证准备密钥对应的新验证码并启用MFA。需要第一方 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| currentPassword | 当前密码的小写32位MD5摘要 | 0123456789abcdef0123456789abcdef |
| currentTotp | 当前已启用MFA时需要的6位字符串 | 012345 |
| preparationId | 最近一次未过期准备标识 | PREPARATION_ID |
| newTotp | 新绑定认证器的6位验证码字符串 | 012345 |

请求示例：

```bash
curl -X PUT 'https://fishpi.cn/api/v3/me/security/mfa' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: mfa-enable-001' \
  -H 'Content-Type: application/json' \
  -d '{"currentPassword":"0123456789abcdef0123456789abcdef","preparationId":"PREPARATION_ID","newTotp":"012345"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本次结果 | 对象 |
| - enabled | 已启用 | true |

> 成功会使已有登录凭据失效，请重新登录。准备过期、已替换或已使用返回409，验证码错误返回422。相同键恢复原结果也需要重新认证及当前安全证明，不会覆盖后来完成的MFA设置。

### 停用 MFA

`DELETE /api/v3/me/security/mfa`

停用当前账号MFA。需要第一方 Bearer Access Token（settings.write）及 Idempotency-Key。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| currentPassword | 当前密码的小写32位MD5摘要 | 0123456789abcdef0123456789abcdef |
| currentTotp | 已启用MFA时需要当前6位验证码字符串 | 012345 |

请求示例：

```bash
curl -X DELETE 'https://fishpi.cn/api/v3/me/security/mfa' \
  -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Idempotency-Key: mfa-disable-001' \
  -H 'Content-Type: application/json' \
  -d '{"currentPassword":"0123456789abcdef0123456789abcdef","currentTotp":"012345"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本次结果 | 对象 |
| - enabled | 已停用 | false |

> 成功会使已有登录凭据失效，请重新登录；尚未启用时也需要当前密码，完成后已有凭据同样失效。相同键重试返回原结果，但仍需当前认证与安全证明。


## API v3 联系方式验证与修改

### 申请手机绑定验证码

`POST /api/v3/me/settings/phone/verification`

向新手机号发送绑定验证码。仅第一方客户端，需要Bearer Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| target | 新手机号，11位大陆手机号 | 13900000001 |
| currentPassword | 当前密码的小写32位MD5摘要 | 32位小写十六进制 |
| currentTotp | 已启用MFA时必填，6位字符串 | 123456 |
| captcha | 极验结果JSON字符串，lot_number/captcha_output/pass_token/gen_time四字段 | 组件返回值 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/settings/phone/verification' -X POST -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' --data '{"target":"13900000001","currentPassword":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","currentTotp":"123456","captcha":"{\"lot_number\":\"LOT\",\"captcha_output\":\"OUTPUT\",\"pass_token\":\"PASS\",\"gen_time\":\"TIME\"}"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.accepted | 发送申请成功 | true |

> 验证码6位、10分钟有效；每次成功申请新码后，该用途的旧码失效。目标必须未占用且与当前不同。发送失败不会使旧有效码失效。

### 修改手机号

`PUT /api/v3/me/settings/phone`

使用新手机号验证码修改绑定。仅第一方客户端，需要Bearer Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| target | 申请验证码时的新手机号 | 13900000001 |
| currentPassword | 当前密码的小写32位MD5摘要 | 32位小写十六进制 |
| currentTotp | 已启用MFA时必填，6位字符串 | 123456 |
| code | 收到的6位验证码字符串 | 654321 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/settings/phone' -X PUT -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' --data '{"target":"13900000001","currentPassword":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","currentTotp":"123456","code":"654321"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.changed | 修改成功 | true |
| data.reauthenticationRequired | 需要重新登录 | true |

> 成功后所有既有登录及授权凭据失效，需要重新登录或授权。失败不消费验证码；账号资料已变化时需重新申请。请求未收到响应时，请重新登录核实结果。

### 申请邮箱绑定验证码

`POST /api/v3/me/settings/email/verification`

向新邮箱发送绑定验证码。仅第一方客户端，需要Bearer Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| target | 新邮箱，去首尾空白后转小写，最多255字符 | new@example.com |
| currentPassword | 当前密码的小写32位MD5摘要 | 32位小写十六进制 |
| currentTotp | 已启用MFA时必填，6位字符串 | 123456 |
| captcha | 极验结果JSON字符串，字段同手机验证码申请 | 组件返回值 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/settings/email/verification' -X POST -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' --data '{"target":"new@example.com","currentPassword":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","currentTotp":"123456","captcha":"{\"lot_number\":\"LOT\",\"captcha_output\":\"OUTPUT\",\"pass_token\":\"PASS\",\"gen_time\":\"TIME\"}"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.accepted | 发送申请成功 | true |

> 验证码6位、10分钟有效；成功申请后该用途旧码失效，发送失败保留旧有效码。只需验证新邮箱，无需旧邮箱验证码。

### 修改邮箱

`PUT /api/v3/me/settings/email`

使用新邮箱验证码修改绑定。仅第一方客户端，需要Bearer Token（settings.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer Token | Bearer ACCESS_TOKEN |
| target | 申请验证码时的新邮箱 | new@example.com |
| currentPassword | 当前密码的小写32位MD5摘要 | 32位小写十六进制 |
| currentTotp | 已启用MFA时必填，6位字符串 | 123456 |
| code | 收到的6位验证码字符串 | 654321 |

请求示例：

```bash
curl 'http://localhost:8080/api/v3/me/settings/email' -X PUT -H 'Authorization: Bearer ACCESS_TOKEN' -H 'Content-Type: application/json' --data '{"target":"new@example.com","currentPassword":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","currentTotp":"123456","code":"654321"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data.changed | 修改成功 | true |
| data.reauthenticationRequired | 需要重新登录 | true |

> 成功后所有既有登录及授权凭据失效，需重新登录或授权；失败不消费验证码，账号资料已变化时需重新申请。请求未收到响应时，请重新登录核实结果。

## API v3 捐助页面

### 读取捐助页面链接

`GET /api/v3/payments/donation-page`

获取当前站点的积分捐助页面链接，需要 activity.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/payments/donation-page' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 页面信息 | `{...}` |
| - url | 当前站点的捐助页面 | https://fishpi.cn/charge/point |

> 在浏览器打开 url 后按页面操作，浏览器未登录时需要先完成网页登录。此接口只返回页面链接，不创建支付订单。

## API v3 本人内容导出

### 创建本人内容导出

`POST /api/v3/me/exports/posts`

导出仍在主表中的全部本人文章和评论，包括匿名、讨论、私密、隐藏、锁定及无效状态，以及本人文章的付费区原文。仅第一方客户端可用，需要 settings.write、profile.read；每次收取100积分，文件就绪后扣费。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，第一方 Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次导出重试保持不变 | posts-export-001 |
| currentPassword | 当前密码的32位小写MD5摘要 | 0123456789abcdef0123456789abcdef |
| currentTotp | 已启用两步验证时必填，当前6位验证码 | 123456 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/exports/posts' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: posts-export-001' \
  --header 'Content-Type: application/json' \
  --data '{"currentPassword":"0123456789abcdef0123456789abcdef","currentTotp":"123456"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 导出结果 | `{...}` |
| - jobId | 导出任务ID | 1782119519493 |
| - status | 文件状态，ready表示可下载；同键重试过期任务时为expired | ready |
| - paidPoints | 此任务支付的积分，不重复收费 | 100 |
| - createdAt | 文件就绪时间，毫秒时间戳 | 1782119519493 |
| - capturedAt | 本次内容快照时间，毫秒时间戳 | 1782119519000 |
| - expiresAt | 下载期限，文件就绪后24小时 | 1782205919493 |
| - articleCount | 本人文章数 | 10 |
| - commentCount | 本人评论数 | 50 |
| - byteLength | ZIP字节数 | 10240 |
| - sha256 | ZIP的SHA-256摘要 | 64位小写十六进制字符串 |

> 文章与评论来自同一次完整内容快照；文件就绪后仅扣费一次。
>
> ZIP内包含UTF-8 manifest.json，以及按固定ID命名的 articles/{id}.md、articles/{id}.reward.md、comments/{id}.md。清单包含当前原文和状态、匿名标记、真实时间；不包含他人正文、他人文章标题、账号联系方式、IP或凭据。已物理删除的内容无法恢复，历次编辑版本不在此包中。
>
> 同键重试恢复同一任务，不再次扣费；每次仍需有效的当前密码和已有两步验证。文件过期后不会用原键重新生成或收费，expired响应仅保留jobId、status、paidPoints；重新导出请使用新键。余额不足返回409，失败不扣费；内容规模超过当前处理能力时返回503，不以部分导出替代完整结果。遇响应丢失请保留原键重试。

### 读取本人导出状态

`GET /api/v3/me/exports/posts/{jobId}`

读取本人的导出任务，需要第一方客户端的 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，第一方 Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| jobId | 路径参数，导出任务ID | 1782119519493 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/exports/posts/1782119519493' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 与创建成功时相同的ready任务信息 | `{...}` |

> 文件可下载时间为就绪后24小时。任务过期返回410，已清理或任务不存在返回404；不能读取他人的任务。

### 下载本人导出文件

`GET /api/v3/me/exports/posts/{jobId}/download`

下载本人未过期的ZIP，需要第一方客户端的 profile.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，第一方 Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| jobId | 路径参数，导出任务ID | 1782119519493 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/exports/posts/1782119519493/download' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --output posts.zip
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Content-Type | 成功时为ZIP二进制，不套JSON信封 | application/zip |
| Content-Disposition | 下载文件名 | attachment; filename="posts-1782119519493.zip" |
| 响应体 | 可校验SHA-256的完整ZIP字节 | 二进制 |

> 每次下载均需本人当前有效授权，不返回公开下载地址。授权失效、任务不存在/过期或文件不可用时返回JSON错误，请客户端先确认HTTP状态和Content-Type再保存文件。

## API v3 聊天室红包

### 创建聊天室红包

`POST /api/v3/chat/red-packets`

创建拼手气、普通、专属、心跳或猜拳红包，需要 content.write、activity.write，发送者需满足聊天室发言条件。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次创建重试保持不变 | red-packet-create-001 |
| type | random（拼手气）、average（普通）、specify（专属）、heartbeat（心跳）、rockPaperScissors（猜拳） | average |
| money | 整数积分；猜拳256至10240，其他类型32至10240；普通和专属为每份金额，其他类型为总额 | 100 |
| count | 拼手气和普通必填，1至100份；心跳必填，5至100份；猜拳可不传或传1；专属不传 | 2 |
| recipientIds | 专属必填，1至100个当前有效的其他用户ID，不重复；其他类型不传 | `["123","456"]` |
| message | 可选留言，沿聊天室红包字符过滤后最多20个UTF-16单位，超长返回422 | 一起摸鱼 |
| riskAccepted | 心跳和猜拳必填，必须为布尔值true，表示接受可能扣积分的风险；其他类型不传 | true |
| gesture | 猜拳发送者出拳，整数0石头、1剪刀、2布；其他类型不传 | 0 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/chat/red-packets' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: red-packet-create-001' \
  --header 'Content-Type: application/json' \
  --data '{"type":"average","money":100,"count":2,"message":"一起摸鱼"}'
```

猜拳创建示例（心跳改用heartbeat并传count，不传gesture）：

```bash
curl --request POST 'https://fishpi.cn/api/v3/chat/red-packets' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: red-packet-rps-create-001' \
  --header 'Content-Type: application/json' \
  --data '{"type":"rockPaperScissors","money":1000,"riskAccepted":true,"gesture":0}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 红包创建结果 | `{...}` |
| - id | 聊天消息ID，也是红包ID | 1782119519493 |
| - createdAt | 创建时间，毫秒时间戳 | 1782119519493 |
| - principalPoints | 发送者支付的总积分 | 200 |
| - poolPoints | 可领取的总积分 | 194 |
| - taxPoints | 红包税额 | 6 |

> 普通、拼手气和心跳税率3%，专属税率1%，猜拳税率15%；税后金额向下取整，税额为差额。普通/专属按每份计算后乘份数，专属份数为接收人数；principalPoints = poolPoints + taxPoints。精确有效的 VIP4_YEAR、VIP4_MONTH 免税，所有发送者适用相同金额和份数上限。
>
> 留言保留英文字母、数字、中文基本汉字、空白及 `,，.。！!?？《》`，不接受控制字符。拼手气允许领到0积分，分配保存后不再重抽；红包无自动过期或退款，领完为止，专属接收人发出后固定。未领完的红包消息不能撤回或删除，返回409；领完后按原删除权限处理。失败不部分扣款或创建；遇到暂不可用或响应中断，请保留原键与原参数重试。同键重试返回原结果，不重复发红包；同键不同内容返回409。

> 心跳可能领取负积分，发送者也可参与。以初始税后奖池P及份数N计算，额外金额E=floor(P/N)+10：一个大奖为P+E，其余份额合计为-E，全部份额之和仍为P。领取前余额至少为P，失败不占份额；未领取的份额不公开。
>
> 猜拳发送者不能领取，领取者余额至少为税后金额P。领取者获胜得到P；失败扣P且发送者收回2P；平局领取者不增减、发送者收回P，税额不退。发送者出拳仅本人及结算后可见。客户端必须明确告知风险后再提交riskAccepted=true，不能为用户自动确认。

### 读取红包详情

`GET /api/v3/chat/red-packets/{id}`

读取当前仍可访问的红包消息及领取记录，需要 content.read。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| id | 路径参数，红包消息ID | 1782119519493 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/chat/red-packets/1782119519493' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 当前红包详情 | `{...}` |
| - id | 红包消息ID | 1782119519493 |
| - type | 红包类型 | average |
| - message | 纯文本留言 | 一起摸鱼 |
| - money | 创建时的税前金额，含义同创建请求 | 100 |
| - count | 总份数 | 2 |
| - got | 已领取份数，含0积分份额 | 1 |
| - principalPoints | 支付的总积分 | 200 |
| - poolPoints | 初始奖池总积分 | 194 |
| - taxPoints | 红包税额 | 6 |
| - createdAt | 创建时间，毫秒时间戳 | 1782119519493 |
| - sender | 发送者公开资料 | `{...}` |
| -- id | 用户ID | 123 |
| -- userName | 用户名 | fish |
| -- userNickname | 昵称 | 摸鱼人 |
| -- userAvatarURL | 头像地址 | https://example.com/avatar.png |
| - claims | 已领取记录，按时间排序 | `[...]` |
| -- user | 领取者公开资料，字段同sender | `{...}` |
| -- amount | 领取积分，可为0；心跳和猜拳可能为负数，表示扣款 | 97 |
| -- claimedAt | 领取时间，毫秒时间戳 | 1782119519500 |
| -- gesture | 猜拳领取者出拳，仅猜拳记录存在 | 2 |
| -- outcome | 猜拳领取者结果，win、lose或tie | win |
| -- senderRefundPoints | 猜拳结算退回发送者的积分 | 0 |
| - currentUserClaim | 本人领取结果，未领取时为null | `{...}` |
| -- amount | 本人领取积分，可为0或负数 | 97 |
| -- claimedAt | 本人领取时间 | 1782119519500 |
| -- gesture | 猜拳领取者的出拳，0石头、1剪刀、2布；仅猜拳结果存在 | 2 |
| -- outcome | 猜拳领取者结果，win、lose或tie；仅猜拳结果存在 | win |
| -- senderRefundPoints | 猜拳本次结算退回发送者的积分；仅猜拳结果存在 | 0 |
| - senderGesture | 猜拳发送者出拳，仅发送者本人或已结算后可见 | 0 |
| - recipientIds | 仅专属红包发送者本人可见的接收人ID | `["456"]` |

> 新版红包沿用上表。经典站新发红包使用相同结算服务。旧经典红包返回 `legacy: true` 和已保存的发送者、类型、留言、份数、领取进度、原红包金额（可用时）及公开领取记录，省略无法可靠还原的结算字段；只可查看，不支持通过 v3 领取。消息不存在或已删除时返回404。未领取者和非专属接收人可查看当前可读红包的公开领取记录，但不能取得接收人名单。

### 领取聊天室红包

`POST /api/v3/chat/red-packets/{id}/claims`

领取一份当前可领取的红包，需要 activity.write，仍须满足当前禁言及领取资格条件。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头，Bearer accessToken | Bearer YOUR_ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次领取重试保持不变 | red-packet-claim-001 |
| id | 路径参数，红包消息ID | 1782119519493 |
| riskAccepted | 心跳和猜拳必填，必须为布尔值true；其他类型不传 | true |
| gesture | 猜拳领取者出拳，整数0石头、1剪刀、2布；其他类型不传 | 2 |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/chat/red-packets/1782119519493/claims' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: red-packet-claim-001' \
  --header 'Content-Type: application/json' \
  --data '{}'
```

猜拳领取示例（心跳只传riskAccepted，不传gesture）：

```bash
curl --request POST 'https://fishpi.cn/api/v3/chat/red-packets/1782119519493/claims' \
  --header 'Authorization: Bearer YOUR_ACCESS_TOKEN' \
  --header 'Idempotency-Key: red-packet-rps-claim-001' \
  --header 'Content-Type: application/json' \
  --data '{"riskAccepted":true,"gesture":2}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0 表示成功 | 0 |
| data | 本人领取结果 | `{...}` |
| - id | 红包消息ID | 1782119519493 |
| - amount | 本次领取积分，可为0；心跳和猜拳可能为负数，表示扣款 | 97 |
| - claimedAt | 领取时间，毫秒时间戳 | 1782119519500 |
| - gesture | 猜拳领取者出拳，仅猜拳结果存在 | 2 |
| - outcome | 猜拳领取者结果，win、lose或tie；仅猜拳结果存在 | win |
| - senderRefundPoints | 猜拳本次结算退回发送者的积分；仅猜拳结果存在 | 0 |

> 每人对同一红包只能领取一次，重复操作返回原领取结果，不重复入账。同键换红包或换出拳返回409；猜拳本人已经领取后，换新键也不能改变出拳，返回409；红包领完返回409，非专属接收人领取返回403。领取金额、领取状态及消息更新一起提交，失败不消费份额或部分入账。旧领取接口兼容省略 riskAccepted，并可传 viewOnly=true 只看详情。v3 领取接口仍要求心跳与猜拳显式传 riskAccepted=true。猜拳发送者停用但账号仍存在时继续结算返还；账号已删除返回409，不扣领取者积分或占份额。旧历史红包的原领取规则保持。


## API v3 官方 APP 扫码登录

已登录网页生成短期二维码，未登录APP扫码并核对账号后确认，网页再明确批准，APP才能一次换取独立v3令牌。有效期为创建后两分钟，后续操作不会续期。任一端拒绝后不可恢复。

`request`、`codeVerifier`及`codeChallenge`均为32字节数据的不带填充base64url编码，长度43，区分大小写。APP自行生成随机`codeVerifier`，`codeChallenge = BASE64URL(SHA256(ASCII(codeVerifier)))`。二维码只编码创建响应的`qrPayload`文本，不是登录令牌；请妥善保管。

### 创建 APP 扫码请求

`POST /api/v3/auth/app-login/requests`

需要第一方网页登录授权，且同时具有settings.write、profile.read。每次成功创建返回新的扫码请求。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer访问令牌 | Bearer WEB_ACCESS_TOKEN |
| Content-Type | application/json，请求体为空对象 | application/json |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/auth/app-login/requests' \
  --header 'Authorization: Bearer WEB_ACCESS_TOKEN' \
  --header 'Content-Type: application/json' --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 扫码请求 | `{...}` |
| - request | 本次请求标识，仅本次响应提供 | REQUEST_FROM_CREATE |
| - qrPayload | 本地生成二维码所用完整文本 | fishpi-login:v3:REQUEST_FROM_CREATE |
| - state | 初始状态 | waiting |
| - expiresAt | 到期时间，毫秒时间戳 | 1792915320000 |

> 创建响应丢失时，可重新创建新请求；没有拿到标识的请求不能继续批准。此接口不创建APP登录态或返回旧apiKey。

### 读取网页登录确认状态

`POST /api/v3/auth/app-login/status`

需要当前第一方网页登录授权及profile.read，只能读取本人请求。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer访问令牌 | Bearer WEB_ACCESS_TOKEN |
| request | 创建时取得的请求标识 | REQUEST_FROM_CREATE |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/auth/app-login/status' \
  --header 'Authorization: Bearer WEB_ACCESS_TOKEN' \
  --header 'Content-Type: application/json' --data '{"request":"REQUEST_FROM_CREATE"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 当前状态 | `{...}` |
| - state | waiting、claimed、app_confirmed、web_approved、consumed或denied | app_confirmed |
| - expiresAt | 原到期时间，不续期 | 1792915320000 |

### 网页批准或拒绝 APP 登录

`POST /api/v3/auth/app-login/decision`

需要当前第一方网页登录授权及settings.write。仅APP已经明确确认时，网页才可批准。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | Bearer访问令牌 | Bearer WEB_ACCESS_TOKEN |
| request | 本人的请求标识 | REQUEST_FROM_CREATE |
| decision | approve批准，deny拒绝 | approve |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/auth/app-login/decision' \
  --header 'Authorization: Bearer WEB_ACCESS_TOKEN' \
  --header 'Content-Type: application/json' --data '{"request":"REQUEST_FROM_CREATE","decision":"approve"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 决定后的状态 | `{...}` |
| - state | web_approved或denied | web_approved |
| - expiresAt | 原到期时间 | 1792915320000 |

### APP 绑定扫码请求

`POST /api/v3/auth/app-login/claim`

APP扫描严格格式`fishpi-login:v3:REQUEST`，提取request并提交自己的绑定challenge。此时尚未登录；应展示返回账号供用户核对。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| request | 扫码取得的请求标识 | REQUEST_FROM_CREATE |
| codeChallenge | APP本次随机证明对应的S256 challenge | APP_CODE_CHALLENGE |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/auth/app-login/claim' \
  --header 'Content-Type: application/json' \
  --data '{"request":"REQUEST_FROM_CREATE","codeChallenge":"APP_CODE_CHALLENGE"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 当前扫码状态和目标账号 | `{...}` |
| - state | 初次为claimed，同一APP重试可返回后续状态 | claimed |
| - expiresAt | 原到期时间 | 1792915320000 |
| - account | 用户核对用账号概要 | `{...}` |
| -- id | 用户ID | 1700000000001 |
| -- userName | 用户名 | Vanessa |
| -- userNickname | 昵称，可为空 | Vanessa |
| -- userAvatarURL | 头像地址，可为空 | https://images.example/avatar.png |

> 首次绑定后，其他challenge不能替换已绑定APP。同一challenge重试不延长期限，不重置已有确认。

### APP 读取等待状态

`POST /api/v3/auth/app-login/app-status`

APP使用绑定证明读取当前状态，等待网页批准；不要把等待状态视为已登录。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| request | 已绑定的请求标识 | REQUEST_FROM_CREATE |
| codeVerifier | APP最初生成并保留的随机证明 | APP_CODE_VERIFIER |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/auth/app-login/app-status' \
  --header 'Content-Type: application/json' \
  --data '{"request":"REQUEST_FROM_CREATE","codeVerifier":"APP_CODE_VERIFIER"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 当前扫码状态 | `{...}` |
| - state | claimed、app_confirmed、web_approved、consumed或denied | web_approved |
| - expiresAt | 原到期时间 | 1792915320000 |
| - account | 目标账号概要，字段同绑定响应 | `{...}` |

### APP 明确确认或拒绝

`POST /api/v3/auth/app-login/app-decision`

仅在用户核对目标账号后提交confirm；deny取消本次请求。确认成功仍需等待网页批准。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| request | 已绑定的请求标识 | REQUEST_FROM_CREATE |
| codeVerifier | APP绑定证明 | APP_CODE_VERIFIER |
| decision | confirm确认，deny拒绝 | confirm |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/auth/app-login/app-decision' \
  --header 'Content-Type: application/json' \
  --data '{"request":"REQUEST_FROM_CREATE","codeVerifier":"APP_CODE_VERIFIER","decision":"confirm"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 决定后的状态 | `{...}` |
| - state | app_confirmed、已推进的web_approved或denied | app_confirmed |
| - expiresAt | 原到期时间 | 1792915320000 |

### APP 一次换取 v3 令牌

`POST /api/v3/auth/app-login/exchange`

双端确认后，APP以原绑定证明一次换取独立令牌。请求消费与令牌签发同时成功或失败，不返回网页登录Cookie。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| request | 双端已确认的请求标识 | REQUEST_FROM_CREATE |
| codeVerifier | APP原绑定证明 | APP_CODE_VERIFIER |

请求示例：

```bash
curl -X POST 'https://fishpi.cn/api/v3/auth/app-login/exchange' \
  --header 'Content-Type: application/json' \
  --data '{"request":"REQUEST_FROM_CREATE","codeVerifier":"APP_CODE_VERIFIER"}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| code | 0表示成功 | 0 |
| data | 独立APP令牌 | `{...}` |
| - accessToken | 业务接口访问令牌 | ACCESS_TOKEN |
| - refreshToken | 本客户端刷新令牌 | REFRESH_TOKEN |
| - tokenType | Bearer | Bearer |
| - expiresIn | 访问有效期，秒 | 900 |
| - refreshExpiresIn | 刷新有效期，秒 | 2592000 |
| - scope | 实际授予权限，以此为准 | content.read profile.read |
| - clientId | 固定官方APP客户端ID | fishpi_app_v3 |

> 成功换取APP令牌时记录最近登录时间；扫码状态查询、双端确认及令牌刷新不重复记录。

> 令牌通过`Authorization: Bearer ACCESS_TOKEN`调用v3，不可用作旧apiKey。刷新和撤销走既有OAuth token/revoke接口，`client_id`使用`fishpi_app_v3`；OAuth请求/响应字段仍按该协议的下划线风格。部分仅网页登录可用的本人设置接口不会因此自动向APP开放。
>
> 越序确认或其他APP抢占返回409；尚未绑定、绑定证明错误或当前发码授权失效返回403；过期、已拒绝或重复换码返回410，标识不存在返回404，参数不合法返回422，服务暂不可用返回503。换码响应丢失后，已消费请求不会再次返回令牌，应重新生成二维码并完成双端确认。

## v3 游戏云存档

### 获取游戏存档目录

`GET /api/v3/game-save-options`

获取当前支持云存档的游戏，需要 apiKey（v3 Bearer Access Token，activity.read）。首批仅支持 Evolve，每人每游戏一个槽。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头 | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/game-save-options' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 结果对象 | {} |
| - list | 游戏目录 | [] |
| -- gameId | 游戏标识，区分大小写 | evolve-save |
| -- name | 游戏名称 | Evolve |
| -- dataFormat | 存档格式 | string |
| -- maxLength | 最大UTF-16长度 | 500000 |
| -- maxBytes | 最大UTF-8字节数 | 2097152 |
| -- legacyMigration | 是否支持显式迁移旧存档 | true |

> 目录不包含排行榜计分、图片表情或其他云数据。Evolve 的完整存档标识是 evolve-save，40 是排行榜摘要。

### 读取本人游戏存档

`GET /api/v3/me/game-saves/{gameId}`

读取本人的当前存档，需要 apiKey（v3 Bearer Access Token，activity.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头 | Bearer ACCESS_TOKEN |
| gameId | 路径参数，目录中的游戏标识 | evolve-save |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/game-saves/evolve-save' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 当前存档 | {} |
| - gameId | 游戏标识 | evolve-save |
| - revision | 当前版本，首次保存为1 | 1 |
| - updatedAt | 最近保存时间，毫秒时间戳 | 1792857600000 |
| - byteLength | 存档UTF-8字节数 | 4 |
| - sha256 | 存档UTF-8内容的SHA-256，小写十六进制 | 64位摘要 |
| - data | 原始存档字符串 | AAA= |

> 没有新存档返回404；仅存在旧存档时，请显式迁移。未知游戏返回422。内容原样返回，不去除空白或处理HTML；客户端应交给相应游戏导入，不作为HTML展示。

### 保存本人游戏存档

`PUT /api/v3/me/game-saves/{gameId}`

保存或覆盖当前存档，需要 apiKey（v3 Bearer Access Token，activity.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头 | Bearer ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次保存重试保持不变 | evolve-save-001 |
| Content-Type | 请求头 | application/json |
| gameId | 路径参数 | evolve-save |
| data | 有效Unicode字符串；可空，原样保存；最多500000个UTF-16单位，且UTF-8不超过2097152字节 | AAA= |
| expectedRevision | 必需的整数，首次保存为0；覆盖时使用最近读取的版本，范围0至9007199254740991 | 0 |

请求示例：

```bash
curl --request PUT 'https://fishpi.cn/api/v3/me/game-saves/evolve-save' \
  --header 'Authorization: Bearer ACCESS_TOKEN' \
  --header 'Idempotency-Key: evolve-save-001' \
  --header 'Content-Type: application/json' \
  --data '{"data":"AAA=","expectedRevision":0}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本次保存结果，不包含存档正文 | {} |
| - gameId | 游戏标识 | evolve-save |
| - revision | 本次提交后的版本 | 1 |
| - updatedAt | 本次保存时间，毫秒时间戳 | 1792857600000 |
| - byteLength | 存档UTF-8字节数 | 4 |
| - sha256 | 存档SHA-256，小写十六进制 | 64位摘要 |

> 每次成功保存递增版本。版本不符返回409，请重新读取并由用户决定是否覆盖；不要自动覆盖冲突。同一幂等键和相同参数返回原结果，不再次写入；不同参数复用同一键返回409。重试返回的是该次保存结果，不代表当前最新版本。错误JSON返回400，错误Content-Type返回415，字符串或版本不合法返回422；请求体最多4MiB，超过返回413。没有删除、备份版本或计分能力。

### 迁移本人旧游戏存档

`POST /api/v3/me/game-saves/{gameId}/migrations`

将本人唯一的一份旧存档复制到尚未建立的新槽，需要 apiKey（v3 Bearer Access Token，activity.write）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头 | Bearer ACCESS_TOKEN |
| Idempotency-Key | 请求头，同一次迁移重试保持不变 | evolve-migrate-001 |
| Content-Type | 有请求体时使用application/json | application/json |
| gameId | 路径参数 | evolve-save |

请求示例：

```bash
curl --request POST 'https://fishpi.cn/api/v3/me/game-saves/evolve-save/migrations' \
  --header 'Authorization: Bearer ACCESS_TOKEN' \
  --header 'Idempotency-Key: evolve-migrate-001' \
  --header 'Content-Type: application/json' --data '{}'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 迁移结果，不包含存档正文 | {} |
| - gameId | 游戏标识 | evolve-save |
| - revision | 初始版本 | 1 |
| - updatedAt | 迁移时间，毫秒时间戳 | 1792857600000 |
| - byteLength | 存档UTF-8字节数 | 4 |
| - sha256 | 存档SHA-256，小写十六进制 | 64位摘要 |
| - migrated | 已迁移 | true |

> 请求体可以为空或{}，不得传入其他字段。旧存档缺失返回404，存在多条旧记录或已有新存档返回409，不自动挑选或覆盖；旧内容不符合上述字符串规则返回422。迁移保留旧记录，同键重试返回原迁移结果，也不会覆盖后来保存的内容。

> 旧 POST /api/cloud/get、POST /api/cloud/sync 对 evolve-save 共用当前存档：旧读取优先返回新槽，无新槽时仍读取旧内容；旧保存保留无版本覆盖语义，每次成功也递增版本，正常code/msg/data字段保持。登记游戏的旧保存必须使用application/json，缺失或错误类型按旧错误格式返回；其他cloud键和旧读取保持原链路。

## v3 本人用户关注状态

### 读取对指定用户的关注状态

`GET /api/v3/me/follows/users/{targetId}`

读取当前账号是否关注目标用户，需要 apiKey（v3 Bearer Access Token，profile.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头 | Bearer ACCESS_TOKEN |
| targetId | 路径参数，目标用户ID，1至19位正整数字符串 | 1770000000000 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/follows/users/1770000000000' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本人关注状态 | {} |
| - targetId | 目标用户ID | 1770000000000 |
| - following | 是否已关注 | true |

> 目标不存在或当前不可访问返回404，无效ID返回422。该接口只返回当前账号与指定目标的关系。设置或取消关注继续使用同路径已有PUT/DELETE及follows.write；写入重试回执不一定代表最新状态，可在成功后重新GET。

## v3 本人标签关注、收藏与订阅状态

### 读取标签关注状态

`GET /api/v3/me/follows/tags/{targetId}`

读取本人对指定标签的当前状态，需要 apiKey（v3 Bearer Access Token，content.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头 | Bearer ACCESS_TOKEN |
| targetId | 路径参数，标签ID，1至19位正整数字符串 | 1770000000000 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/follows/tags/1770000000000' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本人关系状态 | {} |
| - targetId | 目标标签ID | 1770000000000 |
| - following | 是否已关注 | true |

> 目标不存在、失效或当前不可读返回404，无效ID返回422。设置或取消继续使用同路径PUT/DELETE及follows.write；写入重试回执不一定代表最新状态，可在成功后重新GET。

### 读取文章收藏状态

`GET /api/v3/me/collections/{targetId}`

读取本人对指定文章的当前状态，需要 apiKey（v3 Bearer Access Token，content.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头 | Bearer ACCESS_TOKEN |
| targetId | 路径参数，文章ID，1至19位正整数字符串 | 1770000000000 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/collections/1770000000000' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本人关系状态 | {} |
| - targetId | 目标文章ID | 1770000000000 |
| - following | 是否已收藏 | true |

> 目标不存在、失效或当前不可读返回404，无效ID返回422。设置或取消继续使用同路径PUT/DELETE及follows.write；写入重试回执不一定代表最新状态，可在成功后重新GET。

### 读取文章订阅状态

`GET /api/v3/me/subscriptions/{targetId}`

读取本人对指定文章的当前状态，需要 apiKey（v3 Bearer Access Token，content.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头 | Bearer ACCESS_TOKEN |
| targetId | 路径参数，文章ID，1至19位正整数字符串 | 1770000000000 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/subscriptions/1770000000000' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 本人关系状态 | {} |
| - targetId | 目标文章ID | 1770000000000 |
| - following | 是否已订阅 | true |

> 目标不存在、失效或当前不可读返回404，无效ID返回422。设置或取消继续使用同路径PUT/DELETE及follows.write；写入重试回执不一定代表最新状态，可在成功后重新GET。


## v3 发现与公开操作记录

### 读取最近注册用户

`GET /api/v3/recent-members`

按用户ID倒序读取最多20名当前有效用户。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥（可选） | |
| Authorization | 可选，v3 Bearer Access Token，需profile.read | Bearer ACCESS_TOKEN |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/recent-members'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 查询结果 | {} |
| - list | 用户列表，最多20条 | [] |
| -- id | 公开用户ID | 1770000000000 |
| -- userName | 用户名 | FishPi |
| -- userNickname | 昵称 | 鱼油 |
| -- userAvatarURL | 头像 | https://example.com/avatar.png |

> 不返回注册时间或其他账号资料；没有符合条件的用户时返回空列表，读取暂不可用返回503。

### 读取随机发现文章

`GET /api/v3/articles/random`

从当前可读、可列出的文章中发现内容。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| apiKey | 通用密钥（可选） | |
| Authorization | 可选，v3 Bearer Access Token，需content.read | Bearer ACCESS_TOKEN |
| size | 条数，默认8，1至20 | 8 |
| excludeId | 可选，要排除的一个文章ID，1至32位数字 | 1770000000000 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/articles/random?size=8&excludeId=1770000000000'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 查询结果 | {} |
| - list | 不重复的文章摘要列表 | [] |
| -- id | 文章ID | 1770000000001 |
| -- title | 标题 | 今日摸鱼 |
| -- preview | 纯文本摘要 | 正文摘要 |
| -- tags | 标签 | 摸鱼 |
| -- type | 文章类型，沿用文章列表 | 0 |
| -- anonymous | 是否匿名文章 | false |
| -- author | 作者公开资料；匿名时ID、头像为空，名称为匿名用户 | {} |
| --- id | 公开用户ID | 1770000000000 |
| --- userName | 用户名 | FishPi |
| --- userNickname | 昵称 | 鱼油 |
| --- userAvatarURL | 头像 | https://example.com/avatar.png |
| -- permalink | 文章链接 | /article/1770000000001 |
| -- createdAt | 创建时间，毫秒时间戳 | 1770000000001 |
| -- updatedAt | 更新时间，毫秒时间戳 | 1770000000001 |
| -- commentCount | 评论数 | 1 |
| -- viewCount | 浏览数 | 10 |
| -- stick | 置顶状态 | {} |
| --- active | 当前是否置顶 | false |
| --- permanent | 是否永久置顶 | false |
| --- expiresAt | 到期时间，毫秒时间戳；永久时为null | null |

> 按既有随机字段选择起点并回绕，再打乱结果，不保证全库等概率；可用内容不足时条数可以少于size。不包含讨论帖及不可列出内容；游客不能发现限制游客阅读的文章。参数无效返回422，读取暂不可用返回503。

### 分页读取公开操作记录

`GET /api/v3/logs`

读取公开操作的时间与类别，需要 apiKey（v3 Bearer Access Token，activity.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头 | Bearer ACCESS_TOKEN |
| p | 页码，默认1，1至42949672 | 1 |
| size | 每页条数，默认20，最多50 | 20 |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/logs?p=1&size=20' \
  --header 'Authorization: Bearer ACCESS_TOKEN'
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| data | 分页结果 | {} |
| - list | 按记录ID倒序的公开记录 | [] |
| -- id | 记录ID | 1770000000000 |
| -- time | 记录时间，yyyy-MM-dd HH:mm:ss，保留原记录文本 | 2026-10-28 12:00:00 |
| -- category | 操作类别 | 增加积分 |
| - p | 当前页码 | 1 |
| - size | 每页条数 | 20 |
| - total | 符合条件的记录数 | 21 |
| - pageCount | 总页数 | 2 |

> 首批类别：增加积分、扣除积分、发送系统通知、调整用户背包、移除勋章、移除勋章(使用UserId)、添加勋章、解封IP、封禁IP、发放工资、上传摸鱼大闯关数据、上传摸鱼小闯关数据、发送弹幕、发帖并标记为有奖励积分、应用发放积分、应用扣除积分。不返回操作正文、备注或操作对象；读取暂不可用返回503。


## v3 勋章图片

### 读取本人持有的勋章图片

`GET /api/v3/me/medals/image`

读取本人当前有效持有的勋章图片，包含隐藏勋章，需要 apiKey（v3 Bearer Access Token，profile.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头 | Bearer ACCESS_TOKEN |
| medalId | 查询参数，勋章定义ID；沿勋章列表原值，最多128字符，进行URL编码 | 7 |
| size | full或mini，默认full；mini不显示名称文字 | full |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/me/medals/image?medalId=7&size=full' \
  --header 'Authorization: Bearer ACCESS_TOKEN' --output medal.svg
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Content-Type | 成功响应类型 | image/svg+xml; charset=UTF-8 |
| body | SVG图片二进制；浏览器客户端应作为图片展示 | medal.svg |

> 沿勋章列表的当前有效期判断；勋章不存在、已失效或本人未持有返回404，无效参数返回422，图片暂不可用返回503。错误仍为通用JSON响应。图片仅保留支持的SVG图形、样式与动画，部分效果可能与经典页面不同；无需提交原始样式参数。

### 读取用户公开展示的勋章图片

`GET /api/v3/users/{userName}/medals/image`

读取指定用户当前公开展示且有效的勋章图片，需要 apiKey（v3 Bearer Access Token，profile.read）。

请求：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Authorization | 请求头 | Bearer ACCESS_TOKEN |
| userName | 路径参数，用户名，1至64位英文字母、数字、下划线或连字符 | FishPi |
| medalId | 查询参数，沿公开勋章列表的定义ID，最多128字符，URL编码 | 7 |
| size | full或mini，默认full | mini |

请求示例：

```bash
curl 'https://fishpi.cn/api/v3/users/FishPi/medals/image?medalId=7&size=mini' \
  --header 'Authorization: Bearer ACCESS_TOKEN' --output medal-mini.svg
```

响应：

| Key | 说明 | 示例 |
| --- | --- | --- |
| Content-Type | 成功响应类型 | image/svg+xml; charset=UTF-8 |
| body | SVG图片二进制 | medal-mini.svg |

> 用户、勋章不存在或当前未公开展示返回404。当前用户也不能通过此路径读取自己的隐藏勋章，请改用本人路径。公开勋章列表仍可按原接口读取；图片需要profile.read，游客客户端可继续展示名称和描述。
