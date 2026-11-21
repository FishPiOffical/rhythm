-- 宠物账号保护独立存储；按实际 jdbc.tablePrefix 调整 symphony_ 前缀。
-- 新版服务启动前执行，不改变宠物业务数值、账号积分或站点防火墙。
CREATE TABLE IF NOT EXISTS `symphony_pet_risk_state` (
 `oId` VARCHAR(19) NOT NULL,
 `stateJson` MEDIUMTEXT NOT NULL,
 `updatedAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `symphony_pet_risk_audit` (
 `oId` VARCHAR(36) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `operation` VARCHAR(32) NOT NULL,
 `commandHash` VARCHAR(64) NOT NULL,
 `bodyHash` VARCHAR(64) NOT NULL,
 `reason` VARCHAR(64) NOT NULL,
 `occurredAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`),
 KEY `idx_pet_risk_audit_user_time` (`userId`, `occurredAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `symphony_pet_risk_proof` (
 `oId` VARCHAR(64) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `challengeId` VARCHAR(36) NOT NULL,
 `expiresAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`),
 KEY `idx_pet_risk_proof_expiry` (`expiresAt`),
 KEY `idx_pet_risk_proof_user` (`userId`, `expiresAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
