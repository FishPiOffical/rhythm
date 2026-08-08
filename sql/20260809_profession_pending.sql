-- 职业系统待部署迁移（2026-08-09）
--
-- 本文件只包含当前未提交代码对应的迁移。生产环境已部署的
-- 20260123_long_article_read.sql、20260207_long_article_column.sql 以及
-- 其他历史迁移不在本文件中，禁止重复执行。
--
-- 执行顺序：职业基础表 -> 可靠性与统计补丁 -> 长篇阅读可靠性补丁 -> 外部接口索引。
-- 长篇阅读可靠性补丁要求 symphony_article_long_read_stat 已由历史迁移创建。

-- ================================================================
-- 1. 职业基础表
-- 来源：20260731_profession_foundation.sql
-- ================================================================

CREATE TABLE IF NOT EXISTS `symphony_profession` (
  `oId` VARCHAR(19) NOT NULL,
  `professionCode` VARCHAR(64) NOT NULL,
  `currentRevisionId` VARCHAR(19) NOT NULL DEFAULT '',
  `currentLevelSchemeId` VARCHAR(19) NOT NULL DEFAULT '',
  `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  `sortOrder` INT NOT NULL DEFAULT 0,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_code` (`professionCode`),
  KEY `idx_profession_status_sort` (`status`, `sortOrder`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_profession_revision` (
  `oId` VARCHAR(19) NOT NULL,
  `professionId` VARCHAR(19) NOT NULL,
  `revisionNo` INT NOT NULL,
  `schemaVersion` INT NOT NULL DEFAULT 1,
  `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  `displayName` VARCHAR(64) NOT NULL,
  `shortName` VARCHAR(32) NOT NULL DEFAULT '',
  `description` VARCHAR(1024) NOT NULL DEFAULT '',
  `defaultPresentationJson` MEDIUMTEXT NOT NULL,
  `effectiveFrom` BIGINT NOT NULL DEFAULT 0,
  `effectiveTo` BIGINT NOT NULL DEFAULT 0,
  `createdBy` VARCHAR(19) NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `publishedBy` VARCHAR(19) NOT NULL DEFAULT '',
  `publishedAt` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_revision` (`professionId`, `revisionNo`),
  KEY `idx_profession_revision_active` (`professionId`, `status`, `effectiveFrom`, `effectiveTo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_profession_level_scheme` (
  `oId` VARCHAR(19) NOT NULL,
  `professionId` VARCHAR(19) NOT NULL,
  `professionRevisionId` VARCHAR(19) NOT NULL,
  `revisionNo` INT NOT NULL,
  `schemaVersion` INT NOT NULL DEFAULT 1,
  `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  `migrationPolicy` VARCHAR(32) NOT NULL DEFAULT 'MIGRATE_ALL',
  `rewardMigrationPolicy` VARCHAR(32) NOT NULL DEFAULT 'NO_GRANT',
  `effectiveFrom` BIGINT NOT NULL DEFAULT 0,
  `effectiveTo` BIGINT NOT NULL DEFAULT 0,
  `createdBy` VARCHAR(19) NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `publishedBy` VARCHAR(19) NOT NULL DEFAULT '',
  `publishedAt` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_level_scheme_revision` (`professionId`, `revisionNo`),
  KEY `idx_profession_level_scheme_active` (`professionId`, `status`, `effectiveFrom`, `effectiveTo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_profession_level` (
  `oId` VARCHAR(19) NOT NULL,
  `schemeId` VARCHAR(19) NOT NULL,
  `levelCode` VARCHAR(64) NOT NULL,
  `sortOrder` INT NOT NULL,
  `requiredTotalExperience` BIGINT NOT NULL,
  `displayName` VARCHAR(64) NOT NULL,
  `shortName` VARCHAR(32) NOT NULL DEFAULT '',
  `description` VARCHAR(1024) NOT NULL DEFAULT '',
  `achievementDescription` VARCHAR(1024) NOT NULL DEFAULT '',
  `isTopLevel` TINYINT(1) NOT NULL DEFAULT 0,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_level_code` (`schemeId`, `levelCode`),
  UNIQUE KEY `uk_profession_level_sort` (`schemeId`, `sortOrder`),
  KEY `idx_profession_level_threshold` (`schemeId`, `requiredTotalExperience`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_profession_level_presentation` (
  `oId` VARCHAR(19) NOT NULL,
  `levelId` VARCHAR(19) NOT NULL,
  `positionCode` VARCHAR(32) NOT NULL,
  `lightConfigJson` MEDIUMTEXT NOT NULL,
  `darkConfigJson` MEDIUMTEXT NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_level_presentation` (`levelId`, `positionCode`),
  KEY `idx_profession_level_presentation_level` (`levelId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_profession_level_reward` (
  `oId` VARCHAR(19) NOT NULL,
  `schemeId` VARCHAR(19) NOT NULL,
  `levelId` VARCHAR(19) NOT NULL,
  `levelCode` VARCHAR(64) NOT NULL,
  `rewardCode` VARCHAR(64) NOT NULL,
  `rewardType` VARCHAR(32) NOT NULL,
  `rewardConfigJson` MEDIUMTEXT NOT NULL,
  `grantPolicy` VARCHAR(32) NOT NULL DEFAULT 'FIRST_REACH',
  `downgradePolicy` VARCHAR(32) NOT NULL DEFAULT 'KEEP',
  `sortOrder` INT NOT NULL DEFAULT 0,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_level_reward_code` (`schemeId`, `rewardCode`),
  KEY `idx_profession_level_reward_level` (`levelId`, `sortOrder`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_profession_automation` (
  `oId` VARCHAR(19) NOT NULL,
  `professionId` VARCHAR(19) NOT NULL,
  `automationCode` VARCHAR(64) NOT NULL,
  `currentRevisionId` VARCHAR(19) NOT NULL DEFAULT '',
  `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  `sortOrder` INT NOT NULL DEFAULT 0,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_automation_code` (`professionId`, `automationCode`),
  KEY `idx_profession_automation_status` (`professionId`, `status`, `sortOrder`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_profession_automation_revision` (
  `oId` VARCHAR(19) NOT NULL,
  `automationId` VARCHAR(19) NOT NULL,
  `professionId` VARCHAR(19) NOT NULL,
  `revisionNo` INT NOT NULL,
  `schemaVersion` INT NOT NULL DEFAULT 1,
  `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  `triggerType` VARCHAR(64) NOT NULL,
  `triggerSchemaVersion` INT NOT NULL DEFAULT 1,
  `configurationJson` MEDIUMTEXT NOT NULL,
  `configurationHash` CHAR(64) NOT NULL,
  `effectiveFrom` BIGINT NOT NULL DEFAULT 0,
  `effectiveTo` BIGINT NOT NULL DEFAULT 0,
  `createdBy` VARCHAR(19) NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `publishedBy` VARCHAR(19) NOT NULL DEFAULT '',
  `publishedAt` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_automation_revision` (`automationId`, `revisionNo`),
  KEY `idx_profession_automation_trigger` (`triggerType`, `status`, `effectiveFrom`, `effectiveTo`),
  KEY `idx_profession_automation_profession` (`professionId`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_profession_source_event` (
  `oId` VARCHAR(19) NOT NULL,
  `eventKey` VARCHAR(191) NOT NULL,
  `eventType` VARCHAR(64) NOT NULL,
  `schemaVersion` INT NOT NULL DEFAULT 1,
  `subjectUserId` VARCHAR(19) NOT NULL,
  `actorUserId` VARCHAR(19) NOT NULL DEFAULT '',
  `targetType` VARCHAR(32) NOT NULL DEFAULT '',
  `targetId` VARCHAR(19) NOT NULL DEFAULT '',
  `occurredAt` BIGINT NOT NULL,
  `capturedAt` BIGINT NOT NULL,
  `payloadJson` MEDIUMTEXT NOT NULL,
  `visibilityClass` VARCHAR(24) NOT NULL DEFAULT 'SELF',
  `reversalOfEventKey` VARCHAR(191) NOT NULL DEFAULT '',
  `sourceSystem` VARCHAR(64) NOT NULL,
  `sourceAppId` VARCHAR(64) DEFAULT NULL,
  `externalRequestId` VARCHAR(128) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `processingAttempt` INT NOT NULL DEFAULT 0,
  `nextRetryAt` BIGINT NOT NULL DEFAULT 0,
  `lockedAt` BIGINT NOT NULL DEFAULT 0,
  `errorCode` VARCHAR(64) NOT NULL DEFAULT '',
  `errorMessage` VARCHAR(2048) NOT NULL DEFAULT '',
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_source_event_key` (`eventKey`),
  UNIQUE KEY `uk_profession_source_external_request` (`sourceAppId`, `externalRequestId`),
  KEY `idx_profession_source_subject_time` (`subjectUserId`, `occurredAt`, `oId`),
  KEY `idx_profession_source_execution` (`status`, `nextRetryAt`, `capturedAt`),
  KEY `idx_profession_source_reversal` (`reversalOfEventKey`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_profession_event_effect` (
  `oId` VARCHAR(19) NOT NULL,
  `effectKey` VARCHAR(255) NOT NULL,
  `sourceEventId` VARCHAR(19) NOT NULL DEFAULT '',
  `eventKey` VARCHAR(191) NOT NULL DEFAULT '',
  `bucketId` VARCHAR(19) NOT NULL DEFAULT '',
  `bucketKey` VARCHAR(191) NOT NULL DEFAULT '',
  `automationRevisionId` VARCHAR(19) NOT NULL,
  `actionCode` VARCHAR(64) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `professionId` VARCHAR(19) NOT NULL,
  `professionRevisionId` VARCHAR(19) NOT NULL,
  `levelSchemeId` VARCHAR(19) NOT NULL,
  `actualExperienceDelta` BIGINT NOT NULL,
  `beforeExperience` BIGINT NOT NULL,
  `afterExperience` BIGINT NOT NULL,
  `beforeLevelCode` VARCHAR(64) NOT NULL DEFAULT '',
  `afterLevelCode` VARCHAR(64) NOT NULL DEFAULT '',
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCEEDED',
  `reversesEffectId` VARCHAR(19) NOT NULL DEFAULT '',
  `reversedByEffectId` VARCHAR(19) NOT NULL DEFAULT '',
  `occurredAt` BIGINT NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_effect_key` (`effectKey`),
  KEY `idx_profession_effect_user_detail` (`userId`, `professionId`, `occurredAt`, `oId`),
  KEY `idx_profession_effect_source` (`sourceEventId`),
  KEY `idx_profession_effect_bucket` (`bucketId`, `createdAt`),
  KEY `idx_profession_effect_reversal` (`reversesEffectId`, `reversedByEffectId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_profession_event_bucket` (
  `oId` VARCHAR(19) NOT NULL,
  `bucketKey` VARCHAR(191) NOT NULL,
  `triggerType` VARCHAR(64) NOT NULL,
  `automationRevisionId` VARCHAR(19) NOT NULL,
  `subjectUserId` VARCHAR(19) NOT NULL,
  `windowStart` BIGINT NOT NULL,
  `dimensionKey` VARCHAR(512) NOT NULL DEFAULT '',
  `dimensionHash` CHAR(64) NOT NULL,
  `eventCount` BIGINT NOT NULL DEFAULT 0,
  `numericSum` BIGINT NOT NULL DEFAULT 0,
  `distinctCount` BIGINT NOT NULL DEFAULT 0,
  `settlementSequence` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `lastOccurredAt` BIGINT NOT NULL DEFAULT 0,
  `nextSettleAt` BIGINT NOT NULL DEFAULT 0,
  `lockedAt` BIGINT NOT NULL DEFAULT 0,
  `processingAttempt` INT NOT NULL DEFAULT 0,
  `nextRetryAt` BIGINT NOT NULL DEFAULT 0,
  `errorCode` VARCHAR(64) NOT NULL DEFAULT '',
  `errorMessage` VARCHAR(2048) NOT NULL DEFAULT '',
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_bucket_key` (`bucketKey`),
  UNIQUE KEY `uk_profession_bucket_dimension` (`triggerType`, `automationRevisionId`, `subjectUserId`, `windowStart`, `dimensionHash`),
  KEY `idx_profession_bucket_execution` (`status`, `nextSettleAt`, `nextRetryAt`),
  KEY `idx_profession_bucket_subject_window` (`subjectUserId`, `windowStart`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_user_profession` (
  `oId` VARCHAR(19) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `professionId` VARCHAR(19) NOT NULL,
  `professionRevisionId` VARCHAR(19) NOT NULL,
  `levelSchemeId` VARCHAR(19) NOT NULL,
  `totalExperience` BIGINT NOT NULL DEFAULT 0,
  `currentLevelCode` VARCHAR(64) NOT NULL DEFAULT '',
  `highestReachedLevelCode` VARCHAR(64) NOT NULL DEFAULT '',
  `currentLevelReachedAt` BIGINT NOT NULL DEFAULT 0,
  `highestLevelReachedAt` BIGINT NOT NULL DEFAULT 0,
  `lastEffectAt` BIGINT NOT NULL DEFAULT 0,
  `dataVersion` BIGINT NOT NULL DEFAULT 0,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_user_profession` (`userId`, `professionId`),
  KEY `idx_user_profession_ranking` (`professionId`, `totalExperience`, `userId`),
  KEY `idx_user_profession_scheme` (`levelSchemeId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_user_profession_privacy` (
  `oId` VARCHAR(19) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `onboardingState` VARCHAR(16) NOT NULL DEFAULT 'UNDECIDED',
  `primaryProfessionId` VARCHAR(19) NOT NULL DEFAULT '',
  `privacyPreset` VARCHAR(24) NOT NULL DEFAULT 'ALL_PUBLIC',
  `moduleVisibilityJson` MEDIUMTEXT NOT NULL,
  `userProgressVersion` BIGINT NOT NULL DEFAULT 0,
  `userPrivacyVersion` BIGINT NOT NULL DEFAULT 0,
  `publicStatsVersion` BIGINT NOT NULL DEFAULT 0,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_user_profession_privacy_user` (`userId`),
  KEY `idx_user_profession_privacy_primary` (`primaryProfessionId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_profession_reward_grant` (
  `oId` VARCHAR(19) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `professionId` VARCHAR(19) NOT NULL,
  `schemeId` VARCHAR(19) NOT NULL,
  `levelCode` VARCHAR(64) NOT NULL,
  `rewardCode` VARCHAR(64) NOT NULL,
  `grantCycle` VARCHAR(32) NOT NULL DEFAULT 'FIRST_REACH',
  `rewardType` VARCHAR(32) NOT NULL,
  `rewardConfigJson` MEDIUMTEXT NOT NULL,
  `sourceEffectId` VARCHAR(19) NOT NULL,
  `externalReferenceId` VARCHAR(64) NOT NULL DEFAULT '',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `grantedAt` BIGINT NOT NULL DEFAULT 0,
  `revokedAt` BIGINT NOT NULL DEFAULT 0,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_profession_reward_grant` (`userId`, `professionId`, `levelCode`, `rewardCode`, `grantCycle`),
  KEY `idx_profession_reward_grant_user` (`userId`, `professionId`, `createdAt`),
  KEY `idx_profession_reward_grant_effect` (`sourceEffectId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ================================================================
-- 2. 职业效果可靠性字段
-- 来源：20260731_profession_z_effect_apply_reliability.sql
-- ================================================================

ALTER TABLE `symphony_profession_event_effect`
  ADD COLUMN `processingAttempt` INT NOT NULL DEFAULT 0 AFTER `status`,
  ADD COLUMN `nextRetryAt` BIGINT NOT NULL DEFAULT 0 AFTER `processingAttempt`,
  ADD COLUMN `lockedAt` BIGINT NOT NULL DEFAULT 0 AFTER `nextRetryAt`,
  ADD COLUMN `errorCode` VARCHAR(64) NOT NULL DEFAULT '' AFTER `lockedAt`,
  ADD COLUMN `errorMessage` VARCHAR(2048) NOT NULL DEFAULT '' AFTER `errorCode`,
  ADD KEY `idx_profession_effect_execution` (`status`, `nextRetryAt`, `createdAt`);

-- ================================================================
-- 3. 记录历史最高经验
-- 来源：20260731_profession_z_highest_experience.sql
-- ================================================================

ALTER TABLE `symphony_user_profession`
  ADD COLUMN `highestReachedExperience` BIGINT NOT NULL DEFAULT 0 AFTER `highestReachedLevelCode`;

UPDATE `symphony_user_profession`
SET `highestReachedExperience`=`totalExperience`
WHERE `highestReachedExperience`=0 AND `totalExperience`>0;

-- ================================================================
-- 4. 等级方案迁移配置
-- 来源：20260731_profession_z_level_migration_config.sql
-- ================================================================

ALTER TABLE `symphony_profession_level_scheme`
  ADD COLUMN `migrationConfigJson` MEDIUMTEXT NULL AFTER `rewardMigrationPolicy`;

UPDATE `symphony_profession_level_scheme`
SET `migrationConfigJson`='{}'
WHERE `migrationConfigJson` IS NULL OR `migrationConfigJson`='';

ALTER TABLE `symphony_profession_level_scheme`
  MODIFY COLUMN `migrationConfigJson` MEDIUMTEXT NOT NULL;

-- ================================================================
-- 5. 职业贡献统计汇总
-- 来源：20260731_profession_z_progress_stats.sql
-- ================================================================

CREATE TABLE IF NOT EXISTS `symphony_user_profession_contribution` (
  `oId` VARCHAR(19) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `professionId` VARCHAR(19) NOT NULL,
  `actionCode` VARCHAR(64) NOT NULL,
  `experienceSum` BIGINT NOT NULL DEFAULT 0,
  `eventCount` BIGINT NOT NULL DEFAULT 0,
  `lastOccurredAt` BIGINT NOT NULL DEFAULT 0,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_user_profession_contribution` (`userId`, `professionId`, `actionCode`),
  KEY `idx_profession_contribution_top` (`professionId`, `experienceSum`, `userId`),
  KEY `idx_user_profession_contribution` (`userId`, `professionId`, `experienceSum`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ================================================================
-- 6. 长篇阅读窗口与结算可靠性
-- 来源：20260731_z_long_article_read_reliability.sql
-- 前置：symphony_article_long_read_stat 由已部署的历史迁移创建。
-- ================================================================

CREATE TABLE IF NOT EXISTS `symphony_article_long_read_window` (
  `oId` VARCHAR(19) NOT NULL,
  `articleId` VARCHAR(19) NOT NULL,
  `windowStart` BIGINT NOT NULL,
  `registeredCnt` INT NOT NULL DEFAULT 0,
  `anonymousCnt` INT NOT NULL DEFAULT 0,
  `state` VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  `settlementId` VARCHAR(19) NOT NULL DEFAULT '',
  `settledAt` BIGINT NOT NULL DEFAULT 0,
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_article_long_read_window` (`articleId`, `windowStart`),
  KEY `idx_article_long_read_window_settlement` (`state`, `windowStart`, `oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `symphony_article_long_read_settlement` (
  `oId` VARCHAR(19) NOT NULL,
  `articleId` VARCHAR(19) NOT NULL,
  `windowStart` BIGINT NOT NULL,
  `historyId` VARCHAR(19) NOT NULL DEFAULT '',
  `state` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `createdAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_article_long_read_settlement` (`articleId`, `windowStart`),
  KEY `idx_article_long_read_settlement_state` (`state`, `updatedAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `symphony_article_long_read_window` (
  `oId`, `articleId`, `windowStart`, `registeredCnt`, `anonymousCnt`, `state`, `settlementId`, `settledAt`, `createdAt`, `updatedAt`
)
SELECT `oId`, `articleId`, `windowStart`, `registeredUnsettledCnt`, `anonymousUnsettledCnt`, 'OPEN', '', 0, `createdAt`, `updatedAt`
FROM `symphony_article_long_read_stat`
WHERE `registeredUnsettledCnt` > 0 OR `anonymousUnsettledCnt` > 0;

-- ================================================================
-- 7. 金手指来源查询索引
-- 来源：20260801_profession_external_api.sql
-- ================================================================

ALTER TABLE `symphony_profession_source_event`
    ADD KEY `idx_profession_source_app_captured` (`sourceAppId`, `capturedAt`);

-- ================================================================
-- 8. 来源目标与撤销查询索引
-- 来源：20260801_profession_source_target_lookup.sql
-- ================================================================

ALTER TABLE `symphony_profession_source_event`
    ADD KEY `idx_profession_source_target_reversal` (`targetType`, `targetId`, `reversalOfEventKey`, `oId`);
