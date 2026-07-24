ALTER TABLE `symphony_external_point_request`
  ADD COLUMN `sourceAppId` VARCHAR(64) NOT NULL DEFAULT '' AFTER `oId`;

UPDATE `symphony_external_point_request` e
  INNER JOIN `symphony_pointtransfer` p ON p.`oId` = e.`transferId`
SET e.`sourceAppId` = p.`sourceAppId`
WHERE e.`transferId` <> '' AND p.`sourceAppId` IS NOT NULL;

ALTER TABLE `symphony_external_point_request`
  DROP INDEX `uk_external_point_request_id`,
  ADD UNIQUE KEY `uk_external_point_request_scope` (`sourceAppId`, `requestId`);
