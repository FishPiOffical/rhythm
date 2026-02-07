CREATE TABLE IF NOT EXISTS `symphony_article_long_read_stat` (
  `oId` VARCHAR(19) NOT NULL,
  `articleId` VARCHAR(19) NOT NULL,
  `windowStart` BIGINT NOT NULL,
  `registeredUnsettledCnt` INT NOT NULL DEFAULT 0,
  `anonymousUnsettledCnt` INT NOT NULL DEFAULT 0,
  `registeredTotalCnt` INT NOT NULL DEFAULT 0,
  `anonymousTotalCnt` INT NOT NULL DEFAULT 0,
  `lastSettledAt` BIGINT NOT NULL DEFAULT 0,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_article` (`articleId`),
  KEY `idx_window` (`windowStart`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_article_long_read_history` (
  `oId` VARCHAR(19) NOT NULL,
  `articleId` VARCHAR(19) NOT NULL,
  `windowStart` BIGINT NOT NULL,
  `windowEnd` BIGINT NOT NULL,
  `registeredCnt` INT NOT NULL,
  `anonymousCnt` INT NOT NULL,
  `anonymousCappedCnt` INT NOT NULL,
  `rewardPoint` INT NOT NULL,
  `settledAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_article_window` (`articleId`, `windowStart`),
  KEY `idx_settled` (`settledAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_article_long_read_user` (
  `oId` VARCHAR(19) NOT NULL,
  `articleId` VARCHAR(19) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `firstReadAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_article_user` (`articleId`, `userId`),
  KEY `idx_user` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_article_long_read_anon` (
  `oId` VARCHAR(19) NOT NULL,
  `articleId` VARCHAR(19) NOT NULL,
  `windowStart` BIGINT NOT NULL,
  `readerHash` CHAR(64) NOT NULL,
  `firstReadAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_article_window_hash` (`articleId`, `windowStart`, `readerHash`),
  KEY `idx_article_window` (`articleId`, `windowStart`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
