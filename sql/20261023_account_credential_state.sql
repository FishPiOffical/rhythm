-- Legacy unversioned credentials remain valid only while the independent account version is zero.
CREATE TABLE IF NOT EXISTS `symphony_account_credential_state` (
  `oId` VARCHAR(19) NOT NULL,
  `credentialEpoch` BIGINT NOT NULL DEFAULT 0,
  `updatedAt` BIGINT NOT NULL,
  PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `symphony_openid_refresh_token`
  ADD COLUMN `credentialEpoch` BIGINT NOT NULL DEFAULT 0;
