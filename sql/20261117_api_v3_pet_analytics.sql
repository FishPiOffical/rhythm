-- 随机匹配观测，不影响战报和挑战额度。
CREATE TABLE IF NOT EXISTS `symphony_pet_brawl_attempt` (
  `oId` VARCHAR(64) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `result` VARCHAR(24) NOT NULL,
  `weaponLevel` INT NOT NULL,
  `nearCount` INT NOT NULL,
  `midCount` INT NOT NULL,
  `wideCount` INT NOT NULL,
  `matchedTier` INT NOT NULL,
  `weaponGap` INT NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_pet_brawl_attempt_created` (`createdAt`,`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
