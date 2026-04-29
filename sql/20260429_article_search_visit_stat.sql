CREATE TABLE IF NOT EXISTS `symphony_article_search_visit_stat` (
  `oId` VARCHAR(19) NOT NULL,
  `articleId` VARCHAR(19) NOT NULL,
  `searchEngine` VARCHAR(32) NOT NULL,
  `crawlerVisitCount` INT NOT NULL DEFAULT 0,
  `refererVisitCount` INT NOT NULL DEFAULT 0,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_article_search_engine` (`articleId`, `searchEngine`),
  KEY `idx_search_engine` (`searchEngine`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
