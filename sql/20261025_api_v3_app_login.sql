-- Apply before enabling the v3 APP login endpoints. Uses the default deployment prefix.
CREATE TABLE IF NOT EXISTS `symphony_api_v3_app_login_request` (
  `oId` VARCHAR(43) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `issuerTokenId` VARCHAR(19) NOT NULL,
  `credentialEpoch` BIGINT NOT NULL,
  `scope` VARCHAR(255) NOT NULL,
  `state` VARCHAR(16) NOT NULL,
  `codeChallenge` VARCHAR(43) NOT NULL DEFAULT '',
  `createdAt` BIGINT NOT NULL,
  `expiresAt` BIGINT NOT NULL,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_v3_app_login_expiry` (`expiresAt`),
  KEY `idx_v3_app_login_owner` (`userId`,`issuerTokenId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Fixed native client, no redirect registrations and no legacy apiKey conversion.
-- Never reactivate or overwrite an existing administrator-maintained registration.
START TRANSACTION;
INSERT INTO `symphony_api_v3_client` (`oId`,`clientId`,`name`,`scope`,`status`)
SELECT 'fishpi_app_v3','fishpi_app_v3','摸鱼派官方APP',
'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write uploads.write messages.read messages.write',0
WHERE NOT EXISTS (SELECT 1 FROM `symphony_api_v3_client` WHERE `clientId`='fishpi_app_v3');
COMMIT;
