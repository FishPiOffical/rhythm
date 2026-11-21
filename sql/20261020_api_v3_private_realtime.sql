-- Required before deploying private-message revision writes.
-- Replace symphony_ if jdbc.tablePrefix differs. This marker has no history/cursor semantics.
CREATE TABLE IF NOT EXISTS `symphony_api_v3_private_revision` (
  `oId` VARCHAR(19) NOT NULL,
  `revision` VARCHAR(32) NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
