CREATE TABLE IF NOT EXISTS `symphony_reaction` (
  `oId` VARCHAR(19) NOT NULL,
  `reactionUserId` VARCHAR(19) NOT NULL,
  `reactionTargetType` VARCHAR(16) NOT NULL,
  `reactionTargetId` VARCHAR(19) NOT NULL,
  `reactionGroup` VARCHAR(16) NOT NULL,
  `reactionValue` VARCHAR(32) NOT NULL,
  `reactionCreatedTime` BIGINT NOT NULL,
  `reactionUpdatedTime` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_reaction_user_target_group` (`reactionUserId`, `reactionTargetType`, `reactionTargetId`, `reactionGroup`),
  KEY `idx_reaction_target_group` (`reactionTargetType`, `reactionTargetId`, `reactionGroup`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
