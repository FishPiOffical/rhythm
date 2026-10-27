-- Private first-party enterprise applications; no public image URLs.
CREATE TABLE IF NOT EXISTS `symphony_api_v3_identity_application` (
 `oId` VARCHAR(19) NOT NULL, `userId` VARCHAR(19) NOT NULL, `type` VARCHAR(32) NOT NULL,
 `status` VARCHAR(16) NOT NULL, `pendingOwnerId` VARCHAR(19) NULL,
 `createdAt` BIGINT NOT NULL, `materialReadAt` BIGINT NOT NULL, `reviewedAt` BIGINT NOT NULL,
 `note` VARCHAR(256) NOT NULL,
 PRIMARY KEY (`oId`), UNIQUE KEY `uq_v3_identity_pending_owner` (`pendingOwnerId`),
 KEY `idx_v3_identity_owner` (`userId`,`oId`), KEY `idx_v3_identity_status` (`status`,`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `symphony_api_v3_identity_material` (
 `oId` VARCHAR(19) NOT NULL, `userId` VARCHAR(19) NOT NULL, `mediaType` VARCHAR(32) NOT NULL,
 `byteLength` INT NOT NULL, `sha256` VARCHAR(64) NOT NULL, `data` LONGTEXT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `symphony_api_v3_identity_reviewer` (
 `oId` VARCHAR(32) NOT NULL, `userId` VARCHAR(19) NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
-- Resolve the designated existing account once. Renames never transfer review rights.
-- Missing/ambiguous/inactive account leaves no binding and review fails closed.
INSERT INTO `symphony_api_v3_identity_reviewer` (`oId`,`userId`)
 SELECT 'enterprise',MIN(`oId`) FROM `symphony_user`
 WHERE BINARY `userName`='adlered' AND `userStatus`=0
 HAVING COUNT(*)=1 AND NOT EXISTS (SELECT 1 FROM `symphony_api_v3_identity_reviewer` WHERE `oId`='enterprise');
