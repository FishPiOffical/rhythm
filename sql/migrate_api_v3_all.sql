-- ============================================================
-- api_v3 迁移脚本（合并版，按正确顺序）
-- 执行前请：① 全量备份 ② 确认在低峰期 ③ 已跑 preflight_checks.sql
-- 执行方式：整份复制粘贴到 mysql 命令行，或 mysql < 本文件
-- ============================================================

-- ==================== 批次 A：基础架构 ====================

-- --- A1: 20261016_api_v3.sql ---
CREATE TABLE IF NOT EXISTS `symphony_api_v3_chat_state` (
  `oId` VARCHAR(19) NOT NULL,
  `revision` BIGINT NOT NULL DEFAULT 0,
  `updatedAt` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `symphony_api_v3_chat_state` (`oId`,`revision`,`updatedAt`) VALUES ('main',0,0);

CREATE TABLE IF NOT EXISTS `symphony_api_v3_chat_event` (
  `oId` VARCHAR(19) NOT NULL,
  `revision` BIGINT NOT NULL,
  `messageId` VARCHAR(32) NOT NULL,
  `kind` VARCHAR(16) NOT NULL,
  `createdAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_api_v3_chat_event_revision` (`revision`),
  KEY `idx_api_v3_chat_event_created` (`createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `symphony_api_v3_article_revision` (
  `oId` VARCHAR(19) NOT NULL,
  `revision` VARCHAR(32) NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `symphony_api_v3_notification_revision` (
  `oId` VARCHAR(19) NOT NULL,
  `revision` VARCHAR(32) NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `symphony_api_v3_socket_ticket` (
  `oId` VARCHAR(64) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `parentTokenId` VARCHAR(19) NOT NULL,
  `channel` VARCHAR(32) NOT NULL DEFAULT 'notifications',
  `targetId` VARCHAR(32) NOT NULL DEFAULT '',
  `createdAt` BIGINT NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  `consumedAt` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`oId`),
  KEY `idx_api_v3_socket_ticket_expiry` (`expiresAt`),
  KEY `idx_api_v3_socket_ticket_parent` (`parentTokenId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @api_v3_socket_channel_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='symphony_api_v3_socket_ticket' AND COLUMN_NAME='channel'),
  'SELECT 1',
  'ALTER TABLE `symphony_api_v3_socket_ticket` ADD COLUMN `channel` VARCHAR(32) NOT NULL DEFAULT ''notifications''');
PREPARE api_v3_socket_channel_stmt FROM @api_v3_socket_channel_sql;
EXECUTE api_v3_socket_channel_stmt;
DEALLOCATE PREPARE api_v3_socket_channel_stmt;
SET @api_v3_socket_target_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='symphony_api_v3_socket_ticket' AND COLUMN_NAME='targetId'),
  'SELECT 1',
  'ALTER TABLE `symphony_api_v3_socket_ticket` ADD COLUMN `targetId` VARCHAR(32) NOT NULL DEFAULT ''''');
PREPARE api_v3_socket_target_stmt FROM @api_v3_socket_target_sql;
EXECUTE api_v3_socket_target_stmt;
DEALLOCATE PREPARE api_v3_socket_target_stmt;

CREATE TABLE IF NOT EXISTS `symphony_api_v3_client` (
  `oId` VARCHAR(19) NOT NULL,
  `clientId` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `scope` VARCHAR(255) NOT NULL DEFAULT '',
  `status` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_api_v3_client_id` (`clientId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `symphony_api_v3_token` (
  `oId` VARCHAR(19) NOT NULL,
  `tokenHash` VARCHAR(128) NOT NULL,
  `tokenType` VARCHAR(16) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `clientId` VARCHAR(64) NOT NULL,
  `scope` VARCHAR(255) NOT NULL DEFAULT '',
  `familyId` VARCHAR(64) NOT NULL,
  `parentId` VARCHAR(19) NOT NULL DEFAULT '',
  `state` INT NOT NULL DEFAULT 0,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  `lastUsedAt` BIGINT NOT NULL DEFAULT 0,
  `expiresAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_api_v3_token_hash` (`tokenHash`),
  KEY `idx_api_v3_token_family_state` (`familyId`, `state`),
  KEY `idx_api_v3_token_user_client` (`userId`, `clientId`),
  KEY `idx_api_v3_token_expires` (`expiresAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `symphony_api_v3_request` (
  `oId` VARCHAR(64) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `operation` VARCHAR(128) NOT NULL,
  `requestHash` VARCHAR(64) NOT NULL,
  `responseJson` MEDIUMTEXT NOT NULL,
  `createdAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_api_v3_request_user_created` (`userId`, `createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' notifications.read notifications.write')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write';

UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' activity.write')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write';

UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' settings.write')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write';

UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' uploads.write')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write';

START TRANSACTION;
UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' messages.read')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write uploads.write';
COMMIT;

-- --- A2: 20261019_account_registration.sql ---
SET @registration_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='symphony_verifycode' AND column_name='accountContext'),
    'SELECT 1', 'ALTER TABLE `symphony_verifycode` ADD COLUMN `accountContext` VARCHAR(512) NOT NULL DEFAULT ''''');
PREPARE registration_migration FROM @registration_sql;
EXECUTE registration_migration;
DEALLOCATE PREPARE registration_migration;

-- --- A3: 20261019_api_v3_oauth.sql ---
CREATE TABLE IF NOT EXISTS `symphony_api_v3_client_redirect` (
  `oId` VARCHAR(64) NOT NULL,
  `clientId` VARCHAR(64) NOT NULL,
  `redirectUri` VARCHAR(2048) NOT NULL,
  `status` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`oId`),
  KEY `idx_api_v3_redirect_client` (`clientId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `symphony_api_v3_authorization_code` (
  `oId` VARCHAR(64) NOT NULL,
  `clientId` VARCHAR(64) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `redirectUri` VARCHAR(2048) NOT NULL,
  `scope` VARCHAR(255) NOT NULL,
  `codeChallenge` VARCHAR(43) NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  `consumedAt` BIGINT NOT NULL DEFAULT 0,
  `familyId` VARCHAR(64) NOT NULL DEFAULT '',
  PRIMARY KEY (`oId`),
  KEY `idx_api_v3_code_expiry` (`expiresAt`),
  KEY `idx_api_v3_code_client_user` (`clientId`,`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `symphony_api_v3_authorization_request` (
  `oId` VARCHAR(64) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `clientId` VARCHAR(64) NOT NULL,
  `redirectUri` VARCHAR(2048) NOT NULL,
  `scope` VARCHAR(255) NOT NULL,
  `state` VARCHAR(1024) NOT NULL DEFAULT '',
  `codeChallenge` VARCHAR(43) NOT NULL,
  `consentHash` VARCHAR(64) NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  `decidedAt` BIGINT NOT NULL DEFAULT 0,
  `decision` VARCHAR(16) NOT NULL DEFAULT 'pending',
  PRIMARY KEY (`oId`),
  KEY `idx_api_v3_auth_request_expiry` (`expiresAt`),
  KEY `idx_api_v3_auth_request_owner` (`userId`,`clientId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- --- A4: 20261020_api_v3_private_realtime.sql ---
CREATE TABLE IF NOT EXISTS `symphony_api_v3_private_revision` (
  `oId` VARCHAR(19) NOT NULL,
  `revision` VARCHAR(32) NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --- A5: 20261023_api_v3_mfa.sql ---
CREATE TABLE IF NOT EXISTS `symphony_api_v3_mfa_preparation` (
  `oId` VARCHAR(19) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `preparationHash` VARCHAR(64) NOT NULL,
  `secret` VARCHAR(64) NOT NULL,
  `proofBinding` VARCHAR(64) NOT NULL,
  `state` INT NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_api_v3_mfa_expiry` (`state`,`expiresAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- --- A6: 20261024_api_v3_chat_red_packet.sql ---
CREATE TABLE IF NOT EXISTS `symphony_chat_red_packet` (
 `oId` VARCHAR(19) NOT NULL,
 `senderId` VARCHAR(19) NOT NULL,
 `type` VARCHAR(8) NOT NULL,
 `message` VARCHAR(20) NOT NULL,
 `money` INT NOT NULL,
 `count` INT NOT NULL,
 `got` INT NOT NULL,
 `principalPoints` INT NOT NULL,
 `poolPoints` INT NOT NULL,
 `taxPoints` INT NOT NULL,
 `feeRecipientId` VARCHAR(19) NOT NULL,
 `createdAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `symphony_chat_red_packet_slot` (
 `oId` VARCHAR(64) NOT NULL,
 `messageId` VARCHAR(19) NOT NULL,
 `ordinal` INT NOT NULL,
 `amount` INT NOT NULL,
 `recipientId` VARCHAR(19) NOT NULL,
 `claimUserId` VARCHAR(19) NOT NULL,
 `claimedAt` BIGINT NOT NULL,
 `transferId` VARCHAR(19) NOT NULL,
 PRIMARY KEY (`oId`),
 UNIQUE KEY `uk_chat_packet_ordinal` (`messageId`,`ordinal`),
 KEY `idx_chat_packet_claim` (`messageId`,`claimUserId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --- A7: 20261024_api_v3_post_export.sql ---
CREATE TABLE IF NOT EXISTS `symphony_api_v3_post_export` (
 `oId` VARCHAR(19) NOT NULL, `userId` VARCHAR(19) NOT NULL,
 `createdAt` BIGINT NOT NULL, `capturedAt` BIGINT NOT NULL, `expiresAt` BIGINT NOT NULL,
 `articleCount` BIGINT NOT NULL, `commentCount` BIGINT NOT NULL, `sourceBytes` BIGINT NOT NULL,
 `byteLength` BIGINT NOT NULL, `sha256` VARCHAR(64) NOT NULL, `chunkCount` INT NOT NULL, `paidPoints` INT NOT NULL,
 PRIMARY KEY (`oId`), KEY `idx_v3_post_export_owner` (`userId`,`oId`), KEY `idx_v3_post_export_expiry` (`expiresAt`,`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `symphony_api_v3_post_export_chunk` (
 `oId` VARCHAR(64) NOT NULL, `jobId` VARCHAR(19) NOT NULL, `userId` VARCHAR(19) NOT NULL,
 `sequenceNo` INT NOT NULL, `byteLength` INT NOT NULL, `data` MEDIUMTEXT NOT NULL,
 PRIMARY KEY (`oId`), UNIQUE KEY `uq_v3_post_export_chunk` (`jobId`,`sequenceNo`),
 KEY `idx_v3_post_export_chunk_owner` (`userId`,`jobId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- --- A8: 20261025_api_v3_app_login.sql ---
CREATE TABLE IF NOT EXISTS `symphony_api_v3_app_login_request` (
  `oId` VARCHAR(43) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `issuerTokenId` VARCHAR(19) NOT NULL,
  `credentialEpoch` BIGINT NOT NULL,
  `scope` VARCHAR(255) NOT NULL,
  `state` VARCHAR(16) NOT NULL,
  `codeChallenge` VARCHAR(43) NOT NULL DEFAULT '',
  `createdAt` BIGINT NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_v3_app_login_expiry` (`expiresAt`),
  KEY `idx_v3_app_login_owner` (`userId`,`issuerTokenId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

START TRANSACTION;
INSERT INTO `symphony_api_v3_client` (`oId`,`clientId`,`name`,`scope`,`status`)
SELECT 'fishpi_app_v3','fishpi_app_v3','摸鱼派官方APP',
'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write uploads.write messages.read messages.write',0
WHERE NOT EXISTS (SELECT 1 FROM `symphony_api_v3_client` WHERE `clientId`='fishpi_app_v3');
COMMIT;

-- ==================== 批次 B：OAuth / 凭据 / MFA ====================

-- --- B1: 20261023_account_credential_state.sql ---
CREATE TABLE IF NOT EXISTS `symphony_account_credential_state` (
  `oId` VARCHAR(19) NOT NULL,
  `credentialEpoch` BIGINT NOT NULL DEFAULT 0,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `symphony_openid_refresh_token`
  ADD COLUMN `credentialEpoch` BIGINT NOT NULL DEFAULT 0;

-- --- B2: 20261023_api_v3_contact_verification.sql ---
CREATE TABLE IF NOT EXISTS `symphony_account_contact_code` (
  `oId` VARCHAR(64) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `purpose` VARCHAR(8) NOT NULL,
  `clientId` VARCHAR(64) NOT NULL,
  `credentialEpoch` BIGINT NOT NULL,
  `target` VARCHAR(255) NOT NULL,
  `codeHash` VARCHAR(64) NOT NULL,
  `previousContactHash` VARCHAR(64) NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  `createdAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_contact_owner_purpose` (`userId`,`purpose`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --- B3: 20261023_api_v3_fish_game_submission.sql ---
ALTER TABLE `symphony_fish_game`
  ADD COLUMN `fishGameOauthLoginUrl` VARCHAR(4096) NOT NULL DEFAULT '',
  ADD COLUMN `fishGamePendingOauthLoginUrl` VARCHAR(4096) NOT NULL DEFAULT '',
  ADD KEY `idx_fish_game_pending_url` (`fishGamePendingUrl`(191),`fishGameEditPending`);

INSERT INTO `symphony_option` (`oId`,`optionValue`,`optionCategory`)
VALUES ('apiV3FishGameSubmission','','internal')
ON DUPLICATE KEY UPDATE `oId`=`oId`;

-- --- B4: 20261024_api_v3_identity_application.sql ---
CREATE TABLE IF NOT EXISTS `symphony_api_v3_identity_application` (
 `oId` VARCHAR(19) NOT NULL, `userId` VARCHAR(19) NOT NULL, `type` VARCHAR(32) NOT NULL,
 `status` VARCHAR(16) NOT NULL, `pendingOwnerId` VARCHAR(19) NULL,
 `createdAt` BIGINT NOT NULL, `materialReadAt` BIGINT NOT NULL, `reviewedAt` BIGINT NOT NULL,
 `note` VARCHAR(256) NOT NULL,
 PRIMARY KEY (`oId`), UNIQUE KEY `uq_v3_identity_pending_owner` (`pendingOwnerId`),
 KEY `idx_v3_identity_owner` (`userId`,`oId`), KEY `idx_v3_identity_status` (`status`,`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `symphony_api_v3_identity_material` (
 `oId` VARCHAR(19) NOT NULL, `userId` VARCHAR(19) NOT NULL, `mediaType` VARCHAR(32) NOT NULL,
 `byteLength` INT NOT NULL, `sha256` VARCHAR(64) NOT NULL, `data` LONGTEXT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `symphony_api_v3_identity_reviewer` (
 `oId` VARCHAR(32) NOT NULL, `userId` VARCHAR(19) NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO `symphony_api_v3_identity_reviewer` (`oId`,`userId`)
 SELECT 'enterprise',MIN(`oId`) FROM `symphony_user`
 WHERE BINARY `userName`='adlered' AND `userStatus`=0
 HAVING COUNT(*)=1 AND NOT EXISTS (SELECT 1 FROM `symphony_api_v3_identity_reviewer` WHERE `oId`='enterprise');

-- ==================== 批次 C：私信链路 ====================

-- --- C1: 20261019_api_v3_private_messages.sql ---
START TRANSACTION;
UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' messages.read')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write uploads.write';
COMMIT;

SET @dm_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_chat_info' AND index_name='idx_chat_info_from_to_id'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_info` ADD INDEX `idx_chat_info_from_to_id` (`fromId`,`toId`,`oId`)');
PREPARE dm_index FROM @dm_sql;
EXECUTE dm_index;
DEALLOCATE PREPARE dm_index;

SET @dm_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_chat_info' AND index_name='idx_chat_info_to_from_id'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_info` ADD INDEX `idx_chat_info_to_from_id` (`toId`,`fromId`,`oId`)');
PREPARE dm_index FROM @dm_sql;
EXECUTE dm_index;
DEALLOCATE PREPARE dm_index;

SET @dm_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_chat_unread' AND index_name='idx_chat_unread_to_from'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_unread` ADD INDEX `idx_chat_unread_to_from` (`toId`,`fromId`)');
PREPARE dm_index FROM @dm_sql;
EXECUTE dm_index;
DEALLOCATE PREPARE dm_index;

-- --- C2: 20261020_api_v3_private_message_writes.sql ---
START TRANSACTION;
UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' messages.write')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write uploads.write messages.read';
COMMIT;

ALTER TABLE `symphony_chat_info` MODIFY COLUMN `content` TEXT NULL;

SET @dm_write_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_chat_list' AND index_name='idx_chat_list_session_id'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_list` ADD INDEX `idx_chat_list_session_id` (`sessionId`)');
PREPARE dm_write_index FROM @dm_write_sql;
EXECUTE dm_write_index;
DEALLOCATE PREPARE dm_write_index;

-- --- C3: 20261020_api_v3_private_withdrawal.sql ---
SET @private_withdraw_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='symphony_chat_unread' AND column_name='messageId'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_unread` ADD COLUMN `messageId` VARCHAR(19) NOT NULL DEFAULT ''''');
PREPARE private_withdraw_migration FROM @private_withdraw_sql;
EXECUTE private_withdraw_migration;
DEALLOCATE PREPARE private_withdraw_migration;

SET @private_withdraw_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='symphony_chat_unread' AND index_name='idx_chat_unread_message'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_unread` ADD INDEX `idx_chat_unread_message` (`fromId`,`toId`,`messageId`)');
PREPARE private_withdraw_index FROM @private_withdraw_sql;
EXECUTE private_withdraw_index;
DEALLOCATE PREPARE private_withdraw_index;

-- --- C4: 20261024_api_v3_chat_red_packet_risk.sql ---
ALTER TABLE `symphony_chat_red_packet`
 MODIFY COLUMN `type` VARCHAR(17) NOT NULL,
 ADD COLUMN `senderGesture` INT NOT NULL DEFAULT -1;
ALTER TABLE `symphony_chat_red_packet_slot`
 ADD COLUMN `gesture` INT NOT NULL DEFAULT -1,
 ADD COLUMN `senderRefundPoints` INT NOT NULL DEFAULT 0,
 ADD COLUMN `senderTransferId` VARCHAR(19) NOT NULL DEFAULT '';

-- ==================== 批次 D：剩余 ====================

-- --- D1: 20261022_api_v3_membership.sql ---
ALTER TABLE `symphony_pointtransfer_redc`
  ADD INDEX `idx_pointtransfer_redc_raw_user` (`rawId`, `userId`);

-- --- D2: 20261025_api_v3_game_save.sql ---
CREATE TABLE IF NOT EXISTS `symphony_api_v3_game_save` (
  `oId` VARCHAR(43) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `gameId` VARCHAR(19) NOT NULL,
  `revision` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  `byteLength` INT NOT NULL,
  `sha256` VARCHAR(64) NOT NULL,
  `data` MEDIUMTEXT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_v3_game_save_owner_game` (`userId`,`gameId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

SET @game_save_legacy_index = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_cloud' AND index_name='idx_cloud_owner_game_id'),
 'SELECT 1','ALTER TABLE `symphony_cloud` ADD INDEX `idx_cloud_owner_game_id` (`userId`,`gameId`,`oId`)');
PREPARE game_save_legacy_index FROM @game_save_legacy_index;
EXECUTE game_save_legacy_index;
DEALLOCATE PREPARE game_save_legacy_index;

-- --- D3: 20261029_api_v3_point_source_scene.sql ---
START TRANSACTION;
UPDATE `symphony_pointtransfer` SET `sourceScene` = '' WHERE `sourceScene` IS NULL;
COMMIT;

ALTER TABLE `symphony_pointtransfer`
  MODIFY COLUMN `sourceScene` VARCHAR(64) NOT NULL DEFAULT '';

-- --- D4: 20261030_api_v3_article_reads.sql ---
SET @read_column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_article_long_read_user' AND COLUMN_NAME='windowStart');
SET @read_ddl = IF(@read_column_exists=0,
  'ALTER TABLE `symphony_article_long_read_user` ADD COLUMN `windowStart` BIGINT NOT NULL DEFAULT 0 AFTER `userId`', 'SELECT 1');
PREPARE read_stmt FROM @read_ddl;
EXECUTE read_stmt;
DEALLOCATE PREPARE read_stmt;

START TRANSACTION;
UPDATE `symphony_article_long_read_user`
  SET `windowStart` = FLOOR(`firstReadAt` / 21600000) * 21600000 WHERE `windowStart`=0;
COMMIT;

SET @read_old_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_article_long_read_user' AND INDEX_NAME='uk_article_user');
SET @read_new_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_article_long_read_user' AND INDEX_NAME='uk_article_user_window');
SET @read_ddl = IF(@read_new_index=0,
  IF(@read_old_index>0,
    'ALTER TABLE `symphony_article_long_read_user` DROP INDEX `uk_article_user`, ADD UNIQUE KEY `uk_article_user_window` (`articleId`,`userId`,`windowStart`)',
    'ALTER TABLE `symphony_article_long_read_user` ADD UNIQUE KEY `uk_article_user_window` (`articleId`,`userId`,`windowStart`)'),
  IF(@read_old_index>0, 'ALTER TABLE `symphony_article_long_read_user` DROP INDEX `uk_article_user`', 'SELECT 1'));
PREPARE read_stmt FROM @read_ddl;
EXECUTE read_stmt;
DEALLOCATE PREPARE read_stmt;

-- --- 20261111_api_v3_rps_cooldown.sql ---
-- 高峰期猜拳红包冷却按发送人和发送时间读取，先于更新后的 Rhythm 服务部署。
SET @packet_cooldown_ddl = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_chat_red_packet' AND INDEX_NAME='idx_packet_sender_type_time'),
  'SELECT 1', 'CREATE INDEX idx_packet_sender_type_time ON symphony_chat_red_packet (senderId, type, createdAt)');
PREPARE packet_cooldown_stmt FROM @packet_cooldown_ddl;
EXECUTE packet_cooldown_stmt;
DEALLOCATE PREPARE packet_cooldown_stmt;
