ALTER TABLE `symphony_pointtransfer`
  ADD COLUMN `sourceAppId` VARCHAR(64) NOT NULL DEFAULT '',
  ADD COLUMN `sourceAppName` VARCHAR(40) NOT NULL DEFAULT '',
  ADD COLUMN `sourceScene` VARCHAR(20) NOT NULL DEFAULT '',
  ADD COLUMN `sourceRequestId` VARCHAR(128) NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS `symphony_external_point_request` (
  `oId` VARCHAR(19) NOT NULL,
  `requestId` VARCHAR(128) NOT NULL,
  `requestHash` VARCHAR(64) NOT NULL,
  `transferId` VARCHAR(19) NOT NULL DEFAULT '',
  `createTime` BIGINT NOT NULL,
  PRIMARY KEY (`oId`),
  UNIQUE KEY `uk_external_point_request_id` (`requestId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
