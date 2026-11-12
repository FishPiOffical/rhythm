-- 宠物第四批：先执行前三批迁移，按实际 jdbc.tablePrefix 调整前缀。
-- 旧活动快照、库存及已领成果保持原样；新目录和读取投影负责兼容。
CREATE TABLE IF NOT EXISTS `symphony_pet_collection` (
 `oId` VARCHAR(96) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `collectionId` VARCHAR(48) NOT NULL,
 `unlockedAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`),
 UNIQUE KEY `uk_pet_collection_owner_kind` (`userId`,`collectionId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 不回填过去未记录的日常互动；旧计时活动直接进入统一分页查询。
CREATE TABLE IF NOT EXISTS `symphony_pet_journal` (
 `oId` VARCHAR(19) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `petId` VARCHAR(19) NOT NULL,
 `kind` VARCHAR(24) NOT NULL,
 `action` VARCHAR(24) NOT NULL,
 `occurredAt` BIGINT NOT NULL,
 `eventJson` TEXT NOT NULL,
 PRIMARY KEY (`oId`),
 KEY `idx_pet_journal_owner_time` (`userId`,`occurredAt`,`oId`),
 KEY `idx_pet_journal_owner_kind_time` (`userId`,`kind`,`occurredAt`,`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
