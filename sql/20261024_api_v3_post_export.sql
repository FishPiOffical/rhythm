-- Private artifacts are committed with the charge; no public filesystem or object URL.
CREATE TABLE IF NOT EXISTS `symphony_api_v3_post_export` (
 `oId` VARCHAR(19) NOT NULL, `userId` VARCHAR(19) NOT NULL,
 `createdAt` BIGINT NOT NULL, `capturedAt` BIGINT NOT NULL, `expiresAt` BIGINT NOT NULL,
 `articleCount` BIGINT NOT NULL, `commentCount` BIGINT NOT NULL, `sourceBytes` BIGINT NOT NULL,
 `byteLength` BIGINT NOT NULL, `sha256` VARCHAR(64) NOT NULL, `chunkCount` INT NOT NULL, `paidPoints` INT NOT NULL,
 PRIMARY KEY (`oId`), KEY `idx_v3_post_export_owner` (`userId`,`oId`), KEY `idx_v3_post_export_expiry` (`expiresAt`,`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `symphony_api_v3_post_export_chunk` (
 `oId` VARCHAR(64) NOT NULL, `jobId` VARCHAR(19) NOT NULL, `userId` VARCHAR(19) NOT NULL,
 `sequenceNo` INT NOT NULL, `byteLength` INT NOT NULL, `data` MEDIUMTEXT NOT NULL,
 PRIMARY KEY (`oId`), UNIQUE KEY `uq_v3_post_export_chunk` (`jobId`,`sequenceNo`),
 KEY `idx_v3_post_export_chunk_owner` (`userId`,`jobId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
