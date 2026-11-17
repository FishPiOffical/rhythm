-- 宠物友谊约战，邀请终态和对局索引随战报同事务提交。
CREATE TABLE IF NOT EXISTS `symphony_pet_brawl_invite` (
  `oId` VARCHAR(19) NOT NULL,
  `sender` VARCHAR(19) NOT NULL,
  `recipient` VARCHAR(19) NOT NULL,
  `senderPetId` VARCHAR(19) NOT NULL,
  `recipientPetId` VARCHAR(19) NOT NULL,
  `senderPetName` VARCHAR(256) NOT NULL,
  `recipientPetName` VARCHAR(256) NOT NULL,
  `status` VARCHAR(10) NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  `matchId` VARCHAR(19) NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_brawl_invite_match` (`matchId`),
  KEY `idx_brawl_invite_sender` (`sender`,`oId`),
  KEY `idx_brawl_invite_recipient` (`recipient`,`oId`),
  KEY `idx_brawl_invite_received_match` (`recipient`,`matchId`),
  KEY `idx_brawl_invite_pending` (`sender`,`recipient`,`status`,`expiresAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
