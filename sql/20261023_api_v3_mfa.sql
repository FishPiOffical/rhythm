-- Apply before exposing the v3 MFA preparation/activation endpoints.
-- Secret material exists only in this short-lived owner-bound preparation row and user.secret2fa.
CREATE TABLE IF NOT EXISTS `symphony_api_v3_mfa_preparation` (
  `oId` VARCHAR(19) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `preparationHash` VARCHAR(64) NOT NULL,
  `secret` VARCHAR(64) NOT NULL,
  `proofBinding` VARCHAR(64) NOT NULL,
  `state` INT NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_api_v3_mfa_expiry` (`state`,`expiresAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
