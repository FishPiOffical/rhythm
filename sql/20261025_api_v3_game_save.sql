-- One current private save slot per explicit game and owner; does not expose or change the legacy cloud namespace.
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

-- Bound legacy lookup/locking to the owner and registered game, without enforcing uniqueness on historical data.
SET @game_save_legacy_index = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_cloud' AND index_name='idx_cloud_owner_game_id'),
 'SELECT 1','ALTER TABLE `symphony_cloud` ADD INDEX `idx_cloud_owner_game_id` (`userId`,`gameId`,`oId`)');
PREPARE game_save_legacy_index FROM @game_save_legacy_index;
EXECUTE game_save_legacy_index;
DEALLOCATE PREPARE game_save_legacy_index;
