-- 宠物第五批：先执行第一至第四批迁移，按实际 jdbc.tablePrefix 调整前缀。
-- 事件不从旧活动或日志回填，只有本版上线后的成功动作推进；领奖事实随账号保留。
CREATE TABLE IF NOT EXISTS `symphony_pet_story_progress` (
 `oId` VARCHAR(96) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `storyId` VARCHAR(48) NOT NULL,
 `completedSteps` INT NOT NULL,
 `completedAt` BIGINT NOT NULL,
 `claimedAt` BIGINT NOT NULL,
 `snapshotJson` TEXT NOT NULL,
 `updatedAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`),
 UNIQUE KEY `uk_pet_story_owner_kind` (`userId`,`storyId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 保留旧宠物主体快照；重新领养仅替换user_pet当前行，不删除技能、职业或历史活动。
CREATE TABLE IF NOT EXISTS `symphony_pet_archive` (
 `oId` VARCHAR(19) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `retiredAt` BIGINT NOT NULL,
 `snapshotJson` TEXT NOT NULL,
 PRIMARY KEY (`oId`),
 KEY `idx_pet_archive_owner_time` (`userId`,`retiredAt`,`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
