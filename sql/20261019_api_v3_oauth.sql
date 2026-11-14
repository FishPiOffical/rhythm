-- Apply before enabling the OAuth authorization/consent endpoints.
-- Table prefixes here follow the deployment default; Repository names omit the prefix.
CREATE TABLE IF NOT EXISTS `symphony_api_v3_client_redirect` (
  `oId` VARCHAR(64) NOT NULL,
  `clientId` VARCHAR(64) NOT NULL,
  `redirectUri` VARCHAR(2048) NOT NULL,
  `status` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`oId`),
  KEY `idx_api_v3_redirect_client` (`clientId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `symphony_api_v3_authorization_code` (
  `oId` VARCHAR(64) NOT NULL,
  `clientId` VARCHAR(64) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `redirectUri` VARCHAR(2048) NOT NULL,
  `scope` VARCHAR(255) NOT NULL,
  `codeChallenge` VARCHAR(43) NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  `consumedAt` BIGINT NOT NULL DEFAULT 0,
  `familyId` VARCHAR(64) NOT NULL DEFAULT '',
  PRIMARY KEY (`oId`),
  KEY `idx_api_v3_code_expiry` (`expiresAt`),
  KEY `idx_api_v3_code_client_user` (`clientId`,`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `symphony_api_v3_authorization_request` (
  `oId` VARCHAR(64) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `clientId` VARCHAR(64) NOT NULL,
  `redirectUri` VARCHAR(2048) NOT NULL,
  `scope` VARCHAR(255) NOT NULL,
  `state` VARCHAR(1024) NOT NULL DEFAULT '',
  `codeChallenge` VARCHAR(43) NOT NULL,
  `consentHash` VARCHAR(64) NOT NULL,
  `createdAt` BIGINT NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  `decidedAt` BIGINT NOT NULL DEFAULT 0,
  `decision` VARCHAR(16) NOT NULL DEFAULT 'pending',
  PRIMARY KEY (`oId`),
  KEY `idx_api_v3_auth_request_expiry` (`expiresAt`),
  KEY `idx_api_v3_auth_request_owner` (`userId`,`clientId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
