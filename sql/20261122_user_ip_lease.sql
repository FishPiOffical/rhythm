CREATE TABLE IF NOT EXISTS `symphony_user_ip_lease` (
    `oId` VARCHAR(32) NOT NULL,
    `userId` VARCHAR(19) NOT NULL,
    `ip` VARCHAR(45) NOT NULL,
    `lastSeenAt` BIGINT NOT NULL,
    `expiresAt` BIGINT NOT NULL,
    PRIMARY KEY (`oId`),
    UNIQUE KEY `uk_user_ip_lease_user_ip` (`userId`, `ip`),
    KEY `idx_user_ip_lease_active_ip` (`ip`, `expiresAt`),
    KEY `idx_user_ip_lease_user_recent` (`userId`, `lastSeenAt`),
    KEY `idx_user_ip_lease_expiry` (`expiresAt`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
