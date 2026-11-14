# 聊天室、实时订阅与在线状态

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[统一在线记录](../api-v3-unified-online-status-work.md)、[Socket身份](auth-account.md)、[原始请求与事务](environment-framework.md)。

## 旧入口与红包

- 聊天室 `/cr` 的两套样式分别走 `chat-room.js` 与 `chat-room-2.js`，但红包消息都通过 `/chat-room/getMessage` 返回原始 JSON（`msgType=redPacket`），领取统一走 `/chat-room/red-packet/open`。
- 聊天室红包详情/打开后的弹层头部走前端通用 `Util.alert()`（`src/main/resources/js/common.js`），标题与关闭按钮布局异常优先排查这个公共弹层，而不是红包业务模板。
- 聊天室红包风险约束：`heartbeat` 可能抢到负积分，`rockPaperScissors` 猜错会扣积分，`dice` 当前服务端不支持领取；自动化脚本默认只建议开启安全类型。
- v3三类红包`ApiV3ChatRedPacketService`本金/税/消息/持久份额/回执同根，ID绑定专属名单仅sender，旧open只桥接新head；历史红包原规则保持。共享删除primitive锁消息后检查新head，未领完409，须先迁移20261024_api_v3_chat_red_packet.sql及20261029_api_v3_point_source_scene.sql（账本场景列64字符）。0份额仍记领取但不造零额账；税仅精确有效VIP4免，禁止回退进程桶或把未领完消息删掉。
- 红包类型目录五类均已开放；heartbeat/rockPaperScissors创建及领取须显式风险确认，猜拳另需出拳，dice仍不支持。

## 在线状态与隐私

- 单实例在线状态/时长由 `UserOnlineTracker` + `UserOnlineService` 统一：旧 `/user-channel` 不加心跳，V3 JSON ping 60秒租约且授权到期优先；按用户连接并集计时，最终离线/停机结算，余数仅存内存。`userOnlineStatus` 仍是隐私，`userOnlineFlag` 异步兼容写库；累计分钟与职业事件在锁定用户行的同一事务提交，失败固定目标重试，不能用会话分钟替代连续累计 before/after。耗时采用单调 long，禁止强转 int 毫秒差。
- v3 聊天室在线名单由 `ApiV3ChatOnlineQueryService` 合并当前实例旧连接、已认证 chat 订阅和 `ChatOnlineSnapshot` 节点采样，按当前资料过滤在线隐私与有效状态后分页；人数只计可见用户。节点采样原子发布仅用户名，不返回节点凭据；其他实例的 v3 连接未共享，不能宣称全站实时人数。
- 聊天室进出提示和座驾独立于 `userOnlineStatus` 在线隐私；仍校验账号有效性、文案配置与座驾权益，在线名单继续遵守隐私，不能把名单过滤复用于播报。
- v3热度GET需content.read及`ApiV3RealtimeTargetService`当前授权，合计本节点旧连接与重新授权的文章订阅连接，仅近似连接数，不是全站或去重人数。
- 新旧在线状态统一由 `UserOnlineStatus` 登记当前实例的已认证连接：旧 `UserChannel` 与 V3 实时订阅独立计数，最后一个有效连接断开才离线；V3 过期票据不计在线。公开资料遵守 `userOnlineStatus`，数据库 `userOnlineFlag` 不再作为上述资料接口的在线证据；原在线时长结算保留，尚未引入跨实例在线汇总。

## 话题、弹幕与撤回

- 新旧话题写入共用 `ChatTopicWriteService` / `ChatTopicMgmtService`：账户对锁及缓存边界外包事务，当前用户→房间head→option/chatRoomTopic，16积分、话题及topic/0事件同保存点；v3 PUT由 `ApiV3ChatTopicService` 显式Bearer准入及幂等。v3沿用字符过滤且超64 UTF-16拒绝，旧入口保留过滤/截断；投影worker重读已提交最新状态恢复旧页面，topic WS仅发revision，不能冒充messageId=0。旧Bot准入先执行，不能称整个旧Bot处罚与话题原子化。
- v3 弹幕 POST `/chat/barrager` 走 `ApiV3ChatBarrageService`：content.write、当前用户/手机/限制、纯文本清洗后32 UTF-16及#RRGGBB，费用与回执同幂等事务；`ChatBarrageQuota`共用旧用户名额度与admin例外，自动重试只计一次。提交后才发旧广播及当前实例的 `chat.barrager` 白名单事件，v3逐连接重验授权；即时动画不入历史/提交游标、不补播，不能声称跨实例或可靠送达。旧弹幕输入/扣款链路暂保留，展示插件前必须编码正文/昵称与作者路径，两套源码/产物同步。
- 新旧聊天室撤回共用 `ChatMessageWithdrawalMgmtService`：事务前账户对锁及缓存边界，按作者ID检查、锁用户后锁cloud的sys-chat-revoke每日记录；费用0/32/64……，与消息/删除事件/reaction/职业撤销同保存点。用户已同意共享持久计次、改名/重启不清零及旧入口失败不扣款；旧管理跨作者/免计次保留，v3 DELETE 对第三方令牌仍仅本人；第一方 website_v3 令牌可使用事务内实时校验的旧 level3 跨作者撤回，仍需 content.write 及幂等，确认提交后才索引/通知。存储删除核心为 `ChatMessageMgmtService#removeInCurrentTransaction`，不得绕回独立扣费或用户名归属。
- v3 GET `/chat/topic` 读取已提交持久话题（初始无记录兼容进程默认值），`/chat/barrager/price`读取当前进程价格，均需content.read；话题是纯文本，价格经ChatroomProcessor.get/setBarragerPrice成对访问。旧话题通知在channel.js/channel-2.js中必须文本编码话题/作者并编码作者路径，两套源码和min产物同步维护。

## 普通消息与风控

- v3 普通消息 POST `/chat/messages` 走 `ApiV3ChatPublicationService`：content.write、白名单正文/客户端与幂等；先取得actor/sys账户对，再打开缓存边界和事务，当前用户/限制/适用处罚/消息/职业/最后发言时间同事务。仅applied后广播、活跃度与提醒；处罚由 `ChatMessagePenaltyMgmtService` 写禁言/思过簿/扣分通知，提醒走 `ChatMessageNoticeService` 独立严格提交并释放连接。具备旧版执法权限的账号可在正文中使用 `执法`/`zf`，提交成功后复用 `ChatRoomBot.handleLawCommand` 执行一次，幂等回放不重复执行；其他普通正文不执行业务命令或AI。
- 聊天风控重复确认和复读计数由 `ChatMessageAbuseState` 供新旧入口共用；普通保存核心不能替代准入。v3 禁言/风控读取走 `ChatMessageRestrictionQueryService`，要求已有事务并锁定当前记录，只读到期时间、不自行清理或提交；损坏/重复记录须报错，不能当作未限制。`ChatMessageRestrictions` 保留风险校验→确认→禁言顺序及全员禁言时的管理例外。
- 经典聊天室宠物标记指向 `https://new.fishpi.cn/pets`，他人的标记通过消息 ID 打开资料。普通发送在写事务之前限时获取可选宠物快照，超时或读取不可用时仍保存正文，写事务只持久化已取得的快照。归档宠物按原宠物和主人核对后读取成长，历史消息不关联重新领养的新宠物。
- 普通聊天保存共用 `ChatMessageMgmtService`：`saveInCurrentTransaction` 用保存点绑定消息、变更序列和职业来源，外层须提交；旧入口用严格根事务 `saveLegacy`，确认提交后才回填消息ID。v3 用 `ChatMessageText` 拒绝完整业务包装及卡片JSON；执法/zf 前缀仅在权限核验通过后按共享处理器执行。该保存核心不负责禁言、重复确认、复读处罚或外部广播，不能据此直接开放发送。
- 聊天发送基础校验共用ChatMessageValidation/ChatMessageValidationService，客户端标记共用ChatMessageClients；保留旧频控→保留词→trim/4096 UTF-16长度顺序和管理员例外。MIN_STEP_CHAT_TIME当前为0，不能擅自增加业务频控；基础校验不负责禁言、命令或费用。旧ChatRoomBot.muted/risksControlled在记录过期时自行开事务，v3事务体不能直接调用；v3须显式传Bearer用户，不能复用依赖Sessions的旧middleware。

## 持久事件与读取

- 聊天室已提交变更经ChatRoomRepository及共享ReactionMgmtService写api_v3_chat_event和api_v3_chat_state/main；head行锁串行分配revision，不能用消息ID/时间作提交游标。ApiV3ChatTransaction以外层事务保存点绑定消息/reaction与事件，无法局部撤销时整笔回滚。取票POST /api/v3/chat/realtime-ticket要求content.read；ApiV3ChatStream有界消费已提交序列、逐连接重验content.read；新增/更新附带与消息详情相同的有界白名单快照，撤回/表态仅发标识，缺口或倒退发chat.sync_required。启动时初始化调度，清理只删旧事件、不重置head；两张表及种子须先迁移，非持久化在线另接，弹幕另发即时事件。
- v3聊天室读取走`ApiV3ChatProcessor`/`ApiV3ChatQueryService`，统一要求content.read；历史记录的content是消息外壳JSON，必须经`ApiV3ChatMessageDto`白名单分普通Markdown/红包摘要/天气/音乐，禁止裸返外壳或红包who/内部字段。游标列表固定按ID范围查询、返回升序；上下文按半径有界读取。作者为消息发布时公开快照，不能从页面Cookie/apiKey补身份。
- 聊天外观由ApiV3ChatAppearanceService管理固定v1目录，ChatMessageMgmtService共享保存原语在消息事务覆写appearance快照；DTO只读历史白名单，不按当前会员剥除。新表user_chat_appearance须先执行sql/20261107_api_v3_chat_appearance.sql；版本1目录视觉不可原地改版。

## Socket 授权

- v3 Socket票据以channel/targetId绑定通知或单篇文章，按频道复核scope；文章授权经`ApiV3RealtimeTargetService`直接查询当前文章/作者/读者，讨论邀请解析与旧链路共用`UserMentionCandidates`，不得用缓存身份延续已撤销权限。文章取票在`/api/v3/articles/{articleId}/realtime-ticket`；ArticleRepository/CommentRepository和共享ReactionMgmtService在原事务内写`api_v3_article_revision`，后台只轮询已提交标记，逐连接授权后发仅ID的article.changed。通知与文章订阅索引隔离，禁止直接转发旧ArticleChannel完整事件；热度/在线人数另行接入。
- 当前Latke WS升级不走HTTP顶层中间件，query还会覆盖ip。v3 realtime先由`ApiV3SocketHandshake`查精确无query路径/帧上限和显式风控，再等authentication.required后首帧票据认证；禁止Cookie/apiKey回退。通知写入与`api_v3_notification_revision`标记同事务，`ApiV3RealtimeService`只读已提交标记、逐连接重查原access后发本人空提示，不转发旧UserChannel JSON。批量通知删除用owner-aware方法，通用remove(Query)不自动追踪owner。上线前同步建表和repository.json。

- 宠物状态由共享消息保存链在发言时按真实发送者生成，V3 和旧网页读取同一 `petStatus` 快照。字段为昵称、活动类别、等级、品种 ID 及宠物 ID，不含体力、余额或活动详情；前端不逐条补查。旧消息无快照时省略。两版点击自己的标记打开新版 `/pets`，点击他人的标记打开 `/pets/chat/:messageId`。资料页沿用聊天室读取鉴权，对应同一宠物时展示当前资料和发言时活动状态，无法对应时保留原快照。旧版从聊天模板取新版地址，本地沿用已配置的回环预览来源。见 [聊天室接口说明](../../API.md#聊天消息宠物状态)。
