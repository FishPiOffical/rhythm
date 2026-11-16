-- 实装对战报名和已结算配对。战报仍保存在 pet_brawl_match。
CREATE TABLE IF NOT EXISTS `symphony_pet_brawl_profile` (
  `oId` VARCHAR(19) NOT NULL,
  `petId` VARCHAR(19) NOT NULL,
  `enabled` INT NOT NULL,
  `weapon` VARCHAR(12) NOT NULL,
  `skill` VARCHAR(12) NOT NULL,
  `revision` VARCHAR(19) NOT NULL,
  `version` INT NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_brawl_opt_in` (`enabled`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `symphony_pet_brawl_pair` (
  `oId` VARCHAR(19) NOT NULL,
  `day` VARCHAR(10) NOT NULL,
  `initiator` VARCHAR(19) NOT NULL,
  `recipient` VARCHAR(19) NOT NULL,
  `pairLow` VARCHAR(19) NOT NULL,
  `pairHigh` VARCHAR(19) NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_brawl_daily_pair` (`day`,`pairLow`,`pairHigh`),
  KEY `idx_brawl_initiator_day` (`initiator`,`day`),
  KEY `idx_brawl_recipient_day` (`recipient`,`day`),
  KEY `idx_brawl_recipient_match` (`recipient`,`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
