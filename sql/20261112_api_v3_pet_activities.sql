-- 宠物第二批：先执行第一批迁移，再执行本文件；按实际 jdbc.tablePrefix 调整前缀。
-- 只新增活动与技能两张表，不重建宠物、不覆盖已有休息状态、不发放奖励或扣站点积分。
-- 未领取活动使用唯一 active:用户ID 槽键；领取后改为 claimed:活动ID，历史永久防重。
CREATE TABLE IF NOT EXISTS `symphony_pet_activity` (
 `oId` VARCHAR(19) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `petId` VARCHAR(19) NOT NULL,
 `kind` VARCHAR(24) NOT NULL,
 `entryId` VARCHAR(48) NOT NULL,
 `slotKey` VARCHAR(64) NOT NULL,
 `startedAt` BIGINT NOT NULL,
 `endsAt` BIGINT NOT NULL,
 `claimedAt` BIGINT NOT NULL,
 `snapshotJson` TEXT NOT NULL,
 PRIMARY KEY (`oId`),
 UNIQUE KEY `uk_pet_activity_slot` (`slotKey`),
 KEY `idx_pet_activity_owner_claim` (`userId`,`claimedAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 技能属于宠物而非账号，未来重新领养不会把旧宠物的技能继承给新宠物。
CREATE TABLE IF NOT EXISTS `symphony_pet_skill` (
 `oId` VARCHAR(64) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `petId` VARCHAR(19) NOT NULL,
 `skillId` VARCHAR(24) NOT NULL,
 `experience` INT NOT NULL,
 `updatedAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`),
 UNIQUE KEY `uk_pet_skill_pet_kind` (`petId`,`skillId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
