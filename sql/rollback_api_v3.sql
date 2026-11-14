-- ============================================================
-- api_v3 迁移回滚脚本（仅在 v3 被放弃时使用）
-- 前置条件：① 应用已停机（否则 v3 进程读写被 DROP 的表会炸）② 迁移前的干净备份必须留存！
-- 执行方式：整份复制粘贴到 mysql 命令行
-- ⚠️ 警告：
--   1. 三个 MODIFY 类型回退（chat_info.content TEXT→VARCHAR、sourceScene VARCHAR(64)→VARCHAR(20)、chat_red_packet type VARCHAR(17)→VARCHAR(8)）
--      有数据丢失风险：v3 运行期间新产生的长私信、新的 sourceScene 值、17 字符的红包 type 会被截断或报错
--      建议：先跑预检确认 v3 生产数据的实际长度，再决定是否执行 MODIFY 回退
--   2. 所有 v3 业务数据会被永久 DROP TABLE 清除（无回退）
--   3. 此脚本不可逆！执行前必须有干净备份
-- ============================================================

-- ==================== 预检：先跑这些确认风险，再决定是否继续 ====================
SELECT MAX(CHAR_LENGTH(content)) AS max_content_len FROM symphony_chat_info;
SELECT MAX(CHAR_LENGTH(sourceScene)) AS max_source_scene_len FROM symphony_pointtransfer;
SELECT MAX(CHAR_LENGTH(type)) AS max_type_len FROM symphony_chat_red_packet;
-- 上面三个如果分别 ≤2048、≤20、≤8，MODIFY 回退安全；否则建议不要做 MODIFY，先保留新类型

-- ==================== 步骤 1: DROP 所有纯新建的 v3 表（安全） ====================

DROP TABLE IF EXISTS `symphony_api_v3_chat_state`;
DROP TABLE IF EXISTS `symphony_api_v3_chat_event`;
DROP TABLE IF EXISTS `symphony_api_v3_article_revision`;
DROP TABLE IF EXISTS `symphony_api_v3_notification_revision`;
DROP TABLE IF EXISTS `symphony_api_v3_socket_ticket`;
DROP TABLE IF EXISTS `symphony_api_v3_client_redirect`;
DROP TABLE IF EXISTS `symphony_api_v3_authorization_code`;
DROP TABLE IF EXISTS `symphony_api_v3_authorization_request`;
DROP TABLE IF EXISTS `symphony_api_v3_private_revision`;
DROP TABLE IF EXISTS `symphony_api_v3_mfa_preparation`;
DROP TABLE IF EXISTS `symphony_api_v3_identity_application`;
DROP TABLE IF EXISTS `symphony_api_v3_identity_material`;
DROP TABLE IF EXISTS `symphony_api_v3_identity_reviewer`;
DROP TABLE IF EXISTS `symphony_api_v3_post_export`;
DROP TABLE IF EXISTS `symphony_api_v3_post_export_chunk`;
DROP TABLE IF EXISTS `symphony_api_v3_app_login_request`;
DROP TABLE IF EXISTS `symphony_api_v3_game_save`;
DROP TABLE IF EXISTS `symphony_api_v3_client`;
DROP TABLE IF EXISTS `symphony_api_v3_token`;
DROP TABLE IF EXISTS `symphony_api_v3_request`;
DROP TABLE IF EXISTS `symphony_account_credential_state`;
DROP TABLE IF EXISTS `symphony_account_contact_code`;
DROP TABLE IF EXISTS `symphony_chat_red_packet`;
DROP TABLE IF EXISTS `symphony_chat_red_packet_slot`;

DELETE FROM `symphony_option` WHERE `oId` = 'apiV3FishGameSubmission';

-- ==================== 步骤 2: DROP 旧表上新增的列（安全） ====================

ALTER TABLE `symphony_verifycode` DROP COLUMN `accountContext`;
ALTER TABLE `symphony_openid_refresh_token` DROP COLUMN `credentialEpoch`;
ALTER TABLE `symphony_chat_unread` DROP COLUMN `messageId`;
ALTER TABLE `symphony_fish_game`
  DROP COLUMN `fishGameOauthLoginUrl`,
  DROP COLUMN `fishGamePendingOauthLoginUrl`;
ALTER TABLE `symphony_chat_red_packet_slot`
  DROP COLUMN `gesture`,
  DROP COLUMN `senderRefundPoints`,
  DROP COLUMN `senderTransferId`;
-- senderGesture 也在 chat_red_packet（在 MODIFY 那步一起处理，见下）
ALTER TABLE `symphony_article_long_read_user` DROP COLUMN `windowStart`;

-- ==================== 步骤 3: DROP 旧表上新增的索引（安全） ====================

ALTER TABLE `symphony_chat_info`
  DROP INDEX `idx_chat_info_from_to_id`,
  DROP INDEX `idx_chat_info_to_from_id`;
ALTER TABLE `symphony_chat_unread`
  DROP INDEX `idx_chat_unread_to_from`,
  DROP INDEX `idx_chat_unread_message`;
ALTER TABLE `symphony_chat_list` DROP INDEX `idx_chat_list_session_id`;
ALTER TABLE `symphony_pointtransfer_redc` DROP INDEX `idx_pointtransfer_redc_raw_user`;
ALTER TABLE `symphony_cloud` DROP INDEX `idx_cloud_owner_game_id`;
ALTER TABLE `symphony_fish_game` DROP INDEX `idx_fish_game_pending_url`;
ALTER TABLE `symphony_article_long_read_user`
  DROP INDEX `uk_article_user_window`,
  ADD UNIQUE KEY `uk_article_user` (`articleId`,`userId`);  -- ← 加回 master 原有的旧唯一索引

-- ==================== 步骤 4: MODIFY 回退旧表类型（⚠️ 预检确认安全后再执行） ====================
-- 如果预检 max_content_len > 2048 或 max_source_scene_len > 20 或 max_type_len > 8
-- 请注释掉对应的 MODIFY 语句，先确认旧消息/旧红包是否引用了这些数据

-- ⚠️ chat_red_packet：DROP 表时整体删了，下面三条 ALTER 会因表不存在报错——没关系，用 IF EXISTS 保护
SET @rp_drop_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='symphony_chat_red_packet'),
  'ALTER TABLE `symphony_chat_red_packet` MODIFY COLUMN `type` VARCHAR(8) NOT NULL, DROP COLUMN `senderGesture`',
  'SELECT 1');
PREPARE rp_drop FROM @rp_drop_sql; EXECUTE rp_drop; DEALLOCATE PREPARE rp_drop;

-- ⚠️ chat_info.content：TEXT → VARCHAR(2048) NULL（旧 master 的原始定义）
ALTER TABLE `symphony_chat_info` MODIFY COLUMN `content` VARCHAR(2048) NULL;

-- ⚠️ pointtransfer.sourceScene：VARCHAR(64) NOT NULL DEFAULT '' → VARCHAR(20)（旧 master 的原始定义）
-- 注意：执行前确认没有值会被截断（预检 max_source_scene_len ≤ 20）
ALTER TABLE `symphony_pointtransfer` MODIFY COLUMN `sourceScene` VARCHAR(20) NOT NULL DEFAULT '';

-- ==================== 步骤 5: scope 回退（可选，不做也不影响旧业务） ====================
-- website_v3 客户端在 A1/C1/C2 脚本中被追加了 v3 scope 前缀
-- 如果要把它还原到纯旧 scope，可以执行下面三条
-- 如果 v3 表已经 DROP 了，这三条执行时 scope 表会找不到，直接跳过不影响

-- ALTER TABLE `symphony_api_v3_client` 表已 DROP，步骤 1 已删除，下面 UPDATE 会报错（正常现象，忽略即可）
