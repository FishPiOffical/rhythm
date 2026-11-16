-- 宠物乱斗免费养成。按实际 jdbc.tablePrefix 调整前缀。
CREATE TABLE IF NOT EXISTS `symphony_pet_brawl_account` (
  `oId` VARCHAR(19) NOT NULL,
  `materials` INT NOT NULL,
  `rewardDate` VARCHAR(10) NOT NULL,
  `rewardCount` INT NOT NULL,
  `unlocked` INT NOT NULL,
  `levelsJson` VARCHAR(128) NOT NULL,
  PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `symphony_pet_brawl_match` (
  `oId` VARCHAR(19) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `snapshotJson` VARCHAR(8192) NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_brawl_owner_match` (`userId`, `oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
