-- Requires 20261023 account credential state migration before sensitive settings are enabled.
CREATE TABLE IF NOT EXISTS `symphony_account_contact_code` (
  `oId` VARCHAR(64) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `purpose` VARCHAR(8) NOT NULL,
  `clientId` VARCHAR(64) NOT NULL,
  `credentialEpoch` BIGINT NOT NULL,
  `target` VARCHAR(255) NOT NULL,
  `codeHash` VARCHAR(64) NOT NULL,
  `previousContactHash` VARCHAR(64) NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  `createdAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_contact_owner_purpose` (`userId`,`purpose`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
