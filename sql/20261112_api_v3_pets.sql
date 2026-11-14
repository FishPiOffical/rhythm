-- 宠物第一批独立存储。按实际 jdbc.tablePrefix 调整 symphony_ 前缀。
-- 新版服务启动前执行；此迁移不创建宠物、不发放补给，也不接触站点积分。
-- userId 唯一约束与服务内用户行锁同时保护领养；账号钱包不随未来重新领养删除。

-- 宠物钱包及账号级每日奖励，oId为用户ID
CREATE TABLE IF NOT EXISTS `symphony_pet_account` (
 `oId` VARCHAR(19) NOT NULL,
 `coins` INT NOT NULL,
 `supplyDate` VARCHAR(10) NOT NULL,
 `growthDate` VARCHAR(10) NOT NULL,
 `growthCount` INT NOT NULL,
 `createdAt` BIGINT NOT NULL,
 `updatedAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 本人唯一宠物及活动状态
CREATE TABLE IF NOT EXISTS `symphony_user_pet` (
 `oId` VARCHAR(19) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `speciesId` VARCHAR(24) NOT NULL,
 `name` VARCHAR(256) NOT NULL,
 `hunger` DOUBLE NOT NULL,
 `water` DOUBLE NOT NULL,
 `mood` DOUBLE NOT NULL,
 `energy` DOUBLE NOT NULL,
 `experience` INT NOT NULL,
 `friendship` INT NOT NULL,
 `lastCalculatedAt` BIGINT NOT NULL,
 `activityKind` VARCHAR(24) NOT NULL,
 `activityStartedAt` BIGINT NOT NULL,
 `activityEndsAt` BIGINT NOT NULL,
 `petAvailableAt` BIGINT NOT NULL,
 `playAvailableAt` BIGINT NOT NULL,
 `adoptedAt` BIGINT NOT NULL,
 `updatedAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`),
 UNIQUE KEY `uk_pet_owner` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 宠物独立背包，同用户同物品唯一
CREATE TABLE IF NOT EXISTS `symphony_pet_inventory` (
 `oId` VARCHAR(64) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `itemId` VARCHAR(24) NOT NULL,
 `quantity` INT NOT NULL,
 `updatedAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`),
 UNIQUE KEY `uk_pet_inventory_owner_item` (`userId`,`itemId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 宠物币与口粮流水，不参与站点积分
CREATE TABLE IF NOT EXISTS `symphony_pet_ledger` (
 `oId` VARCHAR(19) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `petId` VARCHAR(19) NOT NULL,
 `operation` VARCHAR(24) NOT NULL,
 `coinDelta` INT NOT NULL,
 `foodDelta` INT NOT NULL,
 `createdAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`),
 KEY `idx_pet_ledger_owner_time` (`userId`,`createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
