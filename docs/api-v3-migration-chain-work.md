# v3 迁移链核验（2026-10-24）

## 当前证据

根执行 `python3 tools/api-v3-migration-chain-mysql-test.py`，四组退出0，日志 `/tmp/rhythm-v3-migration-chain-mysql.log`。实际一次性 MySQL 8.4.11，无网络、无公布端口，最终已删除；未读取项目连接配置，也未连接开发或生产业务库。

1. 下列15份原始迁移按依赖顺序执行成功。
2. 当前全部19张新增v3 Repository表均有CREATE来源；实际字段集合、数值类型、VARCHAR长度与`src/main/resources/repository.json`一致。
3. 原默认website scope完整升级、定制client scope不变；旧注册证明/未读关联采用空值，旧可空私信正文保留NULL，鱼游内部命名空间记录存在；已有安全红包升级后保留金额并取得中性出拳/返还字段。
4. 已部署旧Socket票据缺channel/target时，实际升级分支补为notifications/空target并保留owner。

此专项没有运行完整应用。旧依赖表只建立迁移所用的最小列集合，不能证明所有真实历史库状态都可直接升级；旧OpenID表与鱼游表则使用已有原始SQL建立。这里只检查一次有序升级，不声称全部ALTER可重复执行。

## 有序迁移

前置：已有旧业务数据库、OpenID刷新表（`20260622_openid_refresh_token.sql`）、鱼游基础表及删除旧OAuth列（`20260907_fish_game.sql`、`20260908_fish_game_remove_oauth.sql`）。专项在隔离库建立这些前置；不能在已有业务库盲目重跑旧DROP COLUMN。

| 顺序 | 文件 | 用途 |
| --- | --- | --- |
| 1 | `sql/20261016_api_v3.sql` | 基础令牌、请求回执、Socket票据与持久事件 |
| 2 | `sql/20261019_api_v3_oauth.sql` | 回调、授权码、待确认授权 |
| 3 | `sql/20261019_account_registration.sql` | 注册证明上下文 |
| 4 | `sql/20261019_api_v3_private_messages.sql` | 私信读取权限与参与者索引 |
| 5 | `sql/20261020_api_v3_private_message_writes.sql` | 私信写权限、正文类型与列表索引 |
| 6 | `sql/20261020_api_v3_private_withdrawal.sql` | 未读消息精确关联 |
| 7 | `sql/20261020_api_v3_private_realtime.sql` | 私信变更标记 |
| 8 | `sql/20261022_api_v3_membership.sql` | 提交后积分索引修复所需索引 |
| 9 | `sql/20261023_account_credential_state.sql` | 独立认证版本及旧OpenID版本 |
| 10 | `sql/20261023_api_v3_mfa.sql` | 两步验证准备 |
| 11 | `sql/20261023_api_v3_contact_verification.sql` | 联系方式验证证明 |
| 12 | `sql/20261023_api_v3_fish_game_submission.sql` | 新鱼游OAuth申请及名称空间 |
| 13 | `sql/20261024_api_v3_post_export.sql` | 私有导出任务与数据块 |
| 14 | `sql/20261024_api_v3_chat_red_packet.sql` | 新红包与预分配份额 |
| 15 | `sql/20261024_api_v3_chat_red_packet_risk.sql` | 风险红包出拳、返还及类型长度 |

部署核对实际已应用状态，只执行尚缺的迁移；`membership`索引、认证版本、鱼游OAuth与风险红包中的部分ALTER为一次性语句。项目使用非`symphony_`前缀时，SQL需对应实际`jdbc.tablePrefix`；Repository名称仍不带前缀。MySQL DDL隐式提交，文件顺序不构成一个可整体回滚的事务。

## 完整人工链

1. 先在业务库的独立副本核对上述前置和已应用迁移，再执行缺少的SQL，保留旧Cookie/apiKey/OpenID、私信历史及新旧红包样本。
2. 通过IDEA重编译或重启服务端并明确确认后，在`http://localhost:8080`完成登录、第三方授权/换码、私信发送/已读/撤回、敏感设置变更、导出创建/下载、五类红包创建/领取。分别核对权限、预期扣费、重试不重复扣款与持久状态。
3. 回归旧记录的兼容性：无版本旧凭据仅epoch0有效；旧未读没有messageId不得猜测清理；旧安全红包risk列默认中性；定制OAuth scope不得自动增加。数据库缺列/缺索引、错误迁移顺序和重复一次性ALTER应在副本中明确报错，不能以脚本通过代替真实库核查。
4. PC/mobile、真实短信/邮件/图床、Socket重连、黑名单/test/验证码解封、静态/CDN版本和sourceMappingURL、目录枚举及上传下载返回路径仍须实际验收。本专项未新增公开入口、静态资源或下载地址，不证明这些全站边界已通过。

只新增核验工具与本文；不修改生产业务行为或公开API契约，因此本轮无需为此专项新增公开更新条目。

## 2026-10-24 企业认证迁移扩展

迁移链新增20261024_api_v3_identity_application.sql，当前16份、22张新表，真实隔离MySQL8.4.11五组exit0（/tmp/rhythm-v3-identity-migration-chain.log）。除原字段/类型/scope/历史记录检查，增加精确adlered ID绑定、改名后重跑不转权、无有效绑定不补权，以及唯一pendingOwnerId约束与完结释放。容器无网络无发布端口，结束已删除；未连业务库，不代表完整认证服务并发验收。
