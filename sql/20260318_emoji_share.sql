CREATE TABLE IF NOT EXISTS `symphony_emoji_share` (
  `oId` VARCHAR(19) NOT NULL,
  `emojiShareCode` VARCHAR(16) NOT NULL,
  `emojiShareOwnerUserId` VARCHAR(19) NOT NULL,
  `emojiShareGroupId` VARCHAR(19) NOT NULL,
  `emojiShareGroupName` VARCHAR(64) NOT NULL,
  `emojiShareSnapshot` LONGTEXT NOT NULL,
  `emojiShareEmojiCount` INT NOT NULL DEFAULT 0,
  `emojiShareImportedCount` INT NOT NULL DEFAULT 0,
  `emojiShareCreatedTime` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_emoji_share_code` (`emojiShareCode`),
  KEY `idx_emoji_share_owner_created` (`emojiShareOwnerUserId`, `emojiShareCreatedTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
