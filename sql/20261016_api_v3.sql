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

-- Upgrade already deployed ticket tables; old grants retain their notification binding.
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

-- Upgrade only the original default website client policy. Preserve customized client scopes.
UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' notifications.read notifications.write')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write';

-- Add activity writes only to the unchanged default website policy.
UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' activity.write')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write';

-- Add settings writes only to the unchanged default website policy.
UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' settings.write')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write';

-- Add upload grants only to the unchanged default website policy; existing tokens keep their granted scopes.
UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' uploads.write')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write';

-- Add private-message reads only to the unchanged default website policy; existing tokens keep their granted scopes.
START TRANSACTION;
UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' messages.read')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write uploads.write';
COMMIT;

-- Private-message mutation permission. Apply after 20261019_api_v3_private_messages.sql.
-- Replace symphony_ if the deployment uses another jdbc.tablePrefix.
START TRANSACTION;
UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' messages.write')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write uploads.write messages.read';
COMMIT;
