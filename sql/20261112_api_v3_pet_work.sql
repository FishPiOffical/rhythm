-- 宠物第三批：在第一、二批迁移之后执行，按实际 jdbc.tablePrefix 调整前缀。
-- 新增熟练度、账号产币预算、实际成果及日志索引，不修改或回填已开始/已领取的活动快照。
CREATE TABLE IF NOT EXISTS `symphony_pet_job_progress` (
 `oId` VARCHAR(96) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `petId` VARCHAR(19) NOT NULL,
 `jobId` VARCHAR(48) NOT NULL,
 `experience` INT NOT NULL,
 `completed` INT NOT NULL,
 `updatedAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`),
 UNIQUE KEY `uk_pet_job_pet_kind` (`petId`,`jobId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 账号级活动收入与最高结算日，不随换宠物重置；每日补给保持独立。
CREATE TABLE IF NOT EXISTS `symphony_pet_income_account` (
 `oId` VARCHAR(19) NOT NULL,
 `date` VARCHAR(10) NOT NULL,
 `earned` INT NOT NULL,
 `lastSettlementAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- oId直接使用活动ID，实际成果与claimedAt/钱包/进度同事务写入。
CREATE TABLE IF NOT EXISTS `symphony_pet_activity_result` (
 `oId` VARCHAR(19) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `petId` VARCHAR(19) NOT NULL,
 `actualJson` TEXT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 日志按本人及分类倒序分页；显式检查索引，重复执行迁移不重复创建。
SET @pet_activity_owner_time = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_pet_activity' AND index_name='idx_pet_activity_owner_time'),
 'SELECT 1', 'ALTER TABLE `symphony_pet_activity` ADD INDEX `idx_pet_activity_owner_time` (`userId`,`startedAt`,`oId`)');
PREPARE pet_activity_owner_time FROM @pet_activity_owner_time;
EXECUTE pet_activity_owner_time;
DEALLOCATE PREPARE pet_activity_owner_time;

SET @pet_activity_owner_kind_time = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_pet_activity' AND index_name='idx_pet_activity_owner_kind_time'),
 'SELECT 1', 'ALTER TABLE `symphony_pet_activity` ADD INDEX `idx_pet_activity_owner_kind_time` (`userId`,`kind`,`startedAt`,`oId`)');
PREPARE pet_activity_owner_kind_time FROM @pet_activity_owner_kind_time;
EXECUTE pet_activity_owner_kind_time;
DEALLOCATE PREPARE pet_activity_owner_kind_time;
