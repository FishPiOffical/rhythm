# api_v3 分支 SQL 上线执行手册

> 适用：api_v3 分支首次上线。涉及 **20 个新增 SQL 文件**（均在 master 上不存在），无对 master 已存在 SQL 的修改。
> 执行原则：先预检 → 副本演练 → 再生产执行；按日期顺序 + 依赖关系跑；不可重跑的脚本**只跑一次**。

---

## 0. 前置准备（必做）

### 0.1 备份
```sql
-- 生产库全量备份（按你现有的备份方式）
-- 备份文件标记为"执行前干净备份"，只读留存
mysqldump -u root -p rhythm > rhythm_before_api_v3_$(date +%Y%m%d_%H%M%S).sql
```

### 0.2 预检脚本（在**生产库只读执行**）
保存为 `preflight_checks.sql`，全部 SELECT 语句，无副作用。复制结果粘贴到 `docs/sql-preflight-result.md` 保存：

```sql
-- ========== 检查 1: 20261030 article_reads 历史重复 ==========
-- 这是最高优先级：历史重复数据会直接让 UNIQUE KEY 创建失败
SELECT articleId, userId, windowStart, COUNT(*) AS cnt
FROM symphony_article_long_read_user
GROUP BY articleId, userId, windowStart
HAVING COUNT(*) > 1;
-- 若有结果：在生产执行前联系我，确定清理策略（删除/合并/保留一条）

-- ========== 检查 2: 20261022 membership 历史重复 ==========
SELECT userId, transactionId, COUNT(*) AS cnt
FROM symphony_pointtransfer_redc
GROUP BY userId, transactionId
HAVING COUNT(*) > 1;

-- ========== 检查 3: 所有不可重跑脚本的列/索引存在性 ==========
-- 若列/索引已存在（说明脚本曾被跑过），跳过对应脚本

-- 20261019_account_registration
SELECT COUNT(*) AS has_col FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_verifycode' AND COLUMN_NAME = 'accountContext';

-- 20261019_api_v3_private_messages
SELECT COUNT(*) AS has_idx FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_chat_info' AND INDEX_NAME = 'idx_chat_info_from_to_id';
SELECT COUNT(*) AS has_idx FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_chat_unread' AND INDEX_NAME = 'idx_chat_unread_to_from';

-- 20261020_api_v3_private_message_writes
SELECT DATA_TYPE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_chat_info' AND COLUMN_NAME = 'content';
-- 期望 VARCHAR(2048)；脚本改成 TEXT

-- 20261020_api_v3_private_withdrawal
SELECT COUNT(*) AS has_col FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_chat_unread' AND COLUMN_NAME = 'messageId';
SELECT COUNT(*) AS has_idx FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_chat_unread' AND INDEX_NAME = 'idx_chat_unread_message';

-- 20261022_api_v3_membership（不可重跑）
SELECT COUNT(*) AS has_idx FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_pointtransfer_redc' AND INDEX_NAME = 'idx_pointtransfer_redc_raw_user';

-- 20261023_account_credential_state（不可重跑）
SELECT COUNT(*) AS has_col FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_openid_refresh_token' AND COLUMN_NAME = 'credentialEpoch';

-- 20261023_api_v3_fish_game_submission（不可重跑）
SELECT COUNT(*) AS has_col FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_fish_game' AND COLUMN_NAME = 'fishGameOauthLoginUrl';
SELECT COUNT(*) AS has_idx FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_fish_game' AND INDEX_NAME = 'idx_fish_game_pending_url';

-- 20261024_api_v3_chat_red_packet_risk（不可重跑）
SELECT COUNT(*) AS has_col FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_chat_red_packet' AND COLUMN_NAME = 'senderGesture';
SELECT COUNT(*) AS has_col FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_chat_red_packet_slot' AND COLUMN_NAME = 'gesture';
SELECT COUNT(*) AS has_col FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_chat_red_packet_slot' AND COLUMN_NAME = 'senderRefundPoints';
SELECT COUNT(*) AS has_col FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_chat_red_packet_slot' AND COLUMN_NAME = 'senderTransferId';

-- 20261029_api_v3_point_source_scene（不可重跑）
SELECT COUNT(*) AS has_col FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_pointtransfer' AND COLUMN_NAME = 'sourceScene';
SELECT COUNT(*) AS null_scene FROM symphony_pointtransfer WHERE sourceScene IS NULL;

-- 20261030_api_v3_article_reads
SELECT COUNT(*) AS has_col FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_article_long_read_user' AND COLUMN_NAME = 'windowStart';
SELECT COUNT(*) AS has_old_idx FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'symphony_article_long_read_user' AND INDEX_NAME = 'uk_article_user';

-- ========== 检查 4: 脚本依赖的种子数据 ==========
-- 20261024_api_v3_identity_application 需存在 adlered 用户作为 identity reviewer
SELECT COUNT(*) AS has_adlered FROM symphony_user WHERE BINARY userName = 'adlered' AND userStatus = 0;
-- 若 COUNT=0：该脚本的 identity_reviewer INSERT 会静默跳过（有 NOT EXISTS 保护，不影响执行）

-- ========== 检查 5: 客户端 scope 当前值 ==========
-- 20261016 / 20261019_private_messages / 20261020_private_message_writes 都要给 website_v3 加 scope
SELECT clientId, scope FROM symphony_api_v3_client WHERE clientId = 'website_v3';
```

### 0.3 副本演练（强烈建议）
```sql
-- 在干净备份的副本上先完整跑一遍执行顺序，确认无异常
-- 演练结束后副本可丢弃，不影响干净备份
```

---

## 1. 执行顺序（严格按此顺序）

### 批次 A：基础架构（8 个）

| 序号 | 文件 | 幂等性 | 改旧表？ | 风险 |
|------|------|--------|---------|------|
| A1 | `sql/20261016_api_v3.sql` | 有条件（UPDATE scope 精确匹配，重跑安全跳过） | 否（建 v3 核心表 + 种子） | 低 |
| A2 | `sql/20261019_account_registration.sql` | 完全 | ✅ `symphony_verifycode` 加 `accountContext` 列 | 低（有 IF NOT EXISTS） |
| A3 | `sql/20261019_api_v3_oauth.sql` | 完全（纯建表 IF NOT EXISTS） | 否 | 无 |
| A4 | `sql/20261020_api_v3_private_realtime.sql` | 完全（纯建表 IF NOT EXISTS） | 否 | 无 |
| A5 | `sql/20261023_api_v3_mfa.sql` | 完全（纯建表 IF NOT EXISTS） | 否 | 无 |
| A6 | `sql/20261024_api_v3_chat_red_packet.sql` | 完全（纯建表 IF NOT EXISTS） | 否 | 无 |
| A7 | `sql/20261024_api_v3_post_export.sql` | 完全（纯建表 IF NOT EXISTS） | 否 | 无 |
| A8 | `sql/20261025_api_v3_app_login.sql` | 完全 | 否（种子） | 无 |

### 批次 B：OAuth / 凭据 / MFA 链路（4 个）

| 序号 | 文件 | 幂等性 | 改旧表？ | 风险 |
|------|------|--------|---------|------|
| B1 | `sql/20261023_account_credential_state.sql` | **不可重跑** | ✅ `symphony_openid_refresh_token` 加 `credentialEpoch` 列 | 中（无 IF EXISTS） |
| B2 | `sql/20261023_api_v3_contact_verification.sql` | 完全（纯建表 IF NOT EXISTS） | 否 | 无 |
| B3 | `sql/20261023_api_v3_fish_game_submission.sql` | **不可重跑** | ✅ `symphony_fish_game` 加 2 列 + 索引 | 中（无 IF EXISTS） |
| B4 | `sql/20261024_api_v3_identity_application.sql` | 完全（纯建表 + 带 HAVING 条件种子） | 否 | 低（种子 INSERT 有 NOT EXISTS 保护） |

### 批次 C：私信链路（4 个，有严格顺序依赖）

| 序号 | 文件 | 幂等性 | 改旧表？ | 风险 |
|------|------|--------|---------|------|
| C1 | `sql/20261019_api_v3_private_messages.sql` | 有条件 | ✅ `symphony_chat_info` / `symphony_chat_unread` 加覆盖索引 + scope UPDATE | 低（索引有 IF NOT EXISTS） |
| C2 | `sql/20261020_api_v3_private_message_writes.sql` | **不可重跑** | ✅ `symphony_chat_info` `content` VARCHAR→TEXT；`symphony_chat_list` 加索引；scope UPDATE | 中（MODIFY 无 IF EXISTS，大表锁表） |
| C3 | `sql/20261020_api_v3_private_withdrawal.sql` | 完全 | ✅ `symphony_chat_unread` 加 `messageId` 列 + 索引 | 低（有 IF NOT EXISTS） |
| C4 | `sql/20261024_api_v3_chat_red_packet_risk.sql` | **不可重跑** | ✅ `symphony_chat_red_packet` / `symphony_chat_red_packet_slot` 加 5 列 + MODIFY type | 中（无 IF EXISTS） |

### 批次 D：剩余（4 个，顺序宽松）

| 序号 | 文件 | 幂等性 | 改旧表？ | 风险 |
|------|------|--------|---------|------|
| D1 | `sql/20261022_api_v3_membership.sql` | **不可重跑** | ✅ `symphony_pointtransfer_redc` 加唯一索引 | **高**（执行前必须先跑预检 2） |
| D2 | `sql/20261025_api_v3_game_save.sql` | 完全 | ✅ `symphony_cloud` 加覆盖索引 | 低（有 IF NOT EXISTS） |
| D3 | `sql/20261029_api_v3_point_source_scene.sql` | **不可重跑** | ✅ `symphony_pointtransfer` MODIFY `sourceScene` VARCHAR→TEXT + NULL→'' UPDATE | 中（MODIFY 无 IF EXISTS） |
| D4 | `sql/20261030_api_v3_article_reads.sql` | 有条件（关键 ALTER 有信息_schema 条件保护） | ✅ `symphony_article_long_read_user` 加列 + 换唯一索引 | **高**（执行前必须先跑预检 1） |

---

## 2. 执行方式

```bash
# 方式一：MySQL client（生产推荐，批次间可观察）
cd rhythm
mysql -u <user> -p <database> < sql/20261016_api_v3.sql
mysql -u <user> -p <database> < sql/20261019_account_registration.sql
# ... 逐批次执行

# 方式二：用 cat 拼接（一次跑全量，适合演练副本）
cat sql/20261016_api_v3.sql sql/20261019_account_registration.sql ... | mysql -u <user> -p <database>
```

**每执行完一个批次，观察输出**：
- `Query OK, N rows affected` 正常
- `Duplicate column name 'xxx'` 或 `Duplicate index` → 该脚本是不可重跑的且已被跑过，标记跳过继续下一个
- `Error Code: 1022 Can't write, duplicate entry` → 历史数据重复（见预检 1/2），**立即停止**，联系我

---

## 3. 不可重跑脚本清单（执行前必须看预检结果）

这 6 个脚本**没有 IF EXISTS 保护**，重跑必炸：

| 文件 | 被跑过的标志 | 预检 SQL |
|------|-------------|----------|
| `20261023_account_credential_state.sql` | `credentialEpoch` 列已存在 | `SHOW COLUMNS FROM symphony_openid_refresh_token LIKE 'credentialEpoch'` |
| `20261020_api_v3_private_message_writes.sql` | `content` 已是 TEXT | `SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE ... COLUMN_NAME='content'` |
| `20261024_api_v3_chat_red_packet_risk.sql` | `senderGesture`/`gesture`/`senderRefundPoints`/`senderTransferId` 任一已存在 | `SHOW COLUMNS FROM ...` |
| `20261022_api_v3_membership.sql` | `idx_pointtransfer_redc_raw_user` 索引已存在 | `SHOW INDEX FROM symphony_pointtransfer_redc WHERE Key_name='idx_pointtransfer_redc_raw_user'` |
| `20261029_api_v3_point_source_scene.sql` | `sourceScene` 已是 TEXT | `SHOW COLUMNS FROM symphony_pointtransfer LIKE 'sourceScene'` |
| `20261023_api_v3_fish_game_submission.sql` | `fishGameOauthLoginUrl` 列已存在 | `SHOW COLUMNS FROM symphony_fish_game LIKE 'fishGameOauthLoginUrl'` |

---

## 4. 执行失败处理

| 场景 | 处理 |
|------|------|
| 纯建表脚本失败（如 `Table already exists`） | 跳过继续（完全幂等，已存在说明没问题） |
| 不可重跑脚本失败（如 `Duplicate column`） | 跳过继续（说明已执行过，不影响） |
| `Error Code: 1022 Duplicate entry` 加唯一索引时 | **立即停止** → 收集预检 1/2 的 GROUP BY 重复数据 → 联系我定清理策略 → 清理完重跑该脚本 |
| 应用启动因 SQL 失败报 `Table doesn't exist` | SQL 执行顺序有问题，核对批次依赖；必要时从备份恢复重新执行 |
| 大表加索引锁表超时 | MySQL `lock_wait_timeout` 调大；或在低峰期执行；或用 `pt-online-schema-change` / `gh-ost` |

---

## 5. 执行后验证

```sql
-- 全部新表都存在
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'symphony_api_v3_%'
ORDER BY TABLE_NAME;
-- 期望：约 20+ 张表（具体数量以 20261016 为主脚本的建表列表为准）

-- 客户端 scope 正确（v3 全量 scope 已注入）
SELECT clientId, scope FROM symphony_api_v3_client;

-- 旧表新增列存在
SHOW COLUMNS FROM symphony_verifycode LIKE 'accountContext';
SHOW COLUMNS FROM symphony_openid_refresh_token LIKE 'credentialEpoch';
SHOW COLUMNS FROM symphony_pointtransfer LIKE 'sourceScene';

-- 新增索引存在
SHOW INDEX FROM symphony_pointtransfer_redc WHERE Key_name = 'idx_pointtransfer_redc_raw_user';
SHOW INDEX FROM symphony_chat_info WHERE Key_name = 'idx_chat_info_from_to_id';
SHOW INDEX FROM symphony_chat_info WHERE Key_name = 'idx_chat_info_to_from_id';
SHOW INDEX FROM symphony_chat_unread WHERE Key_name = 'idx_chat_unread_message';
SHOW INDEX FROM symphony_article_long_read_user WHERE Key_name = 'uk_article_user_window';

-- 应用健康检查（启动后）
curl http://localhost:8080/                 # 首页 200
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v3/auth/captcha-options  # 401（匿名无权限，但接口存在）
```
