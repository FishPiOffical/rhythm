-- 已存在字段时请跳过这一句。
ALTER TABLE `symphony_long_article_column`
  ADD COLUMN `columnCoverURL` VARCHAR(1024) NOT NULL DEFAULT '' AFTER `columnAuthorId`;

CREATE TABLE IF NOT EXISTS `symphony_repeater_content` (
  `oId` VARCHAR(19) NOT NULL,
  `repeaterContentType` VARCHAR(16) NOT NULL,
  `repeaterContent` VARCHAR(500) NOT NULL,
  `repeaterContentAuthorId` VARCHAR(19) NOT NULL DEFAULT '',
  `repeaterContentSource` VARCHAR(16) NOT NULL,
  `repeaterContentStatus` INT NOT NULL DEFAULT 0,
  `repeaterContentLikeCount` INT NOT NULL DEFAULT 0,
  `repeaterContentCreatedTime` BIGINT NOT NULL,
  `repeaterContentUpdatedTime` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_repeater_type_status_like` (`repeaterContentType`, `repeaterContentStatus`, `repeaterContentLikeCount`),
  KEY `idx_repeater_author_created` (`repeaterContentAuthorId`, `repeaterContentCreatedTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_repeater_like` (
  `oId` VARCHAR(19) NOT NULL,
  `repeaterLikeContentId` VARCHAR(19) NOT NULL,
  `repeaterLikeUserId` VARCHAR(19) NOT NULL,
  `repeaterLikeCreatedTime` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_repeater_like_user_content` (`repeaterLikeUserId`, `repeaterLikeContentId`),
  KEY `idx_repeater_like_content` (`repeaterLikeContentId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
