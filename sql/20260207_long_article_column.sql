CREATE TABLE IF NOT EXISTS `symphony_long_article_column` (
  `oId` VARCHAR(19) NOT NULL,
  `columnTitle` VARCHAR(64) NOT NULL,
  `columnAuthorId` VARCHAR(19) NOT NULL,
  `columnArticleCount` INT NOT NULL DEFAULT 0,
  `columnStatus` INT NOT NULL DEFAULT 0,
  `columnCreateTime` BIGINT NOT NULL,
  `columnUpdateTime` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_author_status_update` (`columnAuthorId`, `columnStatus`, `columnUpdateTime`),
  UNIQUE KEY `uk_author_title` (`columnAuthorId`, `columnTitle`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_long_article_column_chapter` (
  `oId` VARCHAR(19) NOT NULL,
  `articleId` VARCHAR(19) NOT NULL,
  `columnId` VARCHAR(19) NOT NULL,
  `chapterNo` INT NOT NULL,
  `chapterCreateTime` BIGINT NOT NULL,
  `chapterUpdateTime` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_article` (`articleId`),
  UNIQUE KEY `uk_column_chapter_no` (`columnId`, `chapterNo`),
  KEY `idx_column` (`columnId`),
  KEY `idx_column_chapter_create` (`columnId`, `chapterNo`, `chapterCreateTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
