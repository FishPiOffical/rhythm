-- Requires 20260717_external_point_source.sql.
-- Internal v3 source scenes include chat.red-packets.create (22 characters).
-- Preserve all existing source values; normalize legacy nullable metadata rows only.
START TRANSACTION;
UPDATE `symphony_pointtransfer` SET `sourceScene` = '' WHERE `sourceScene` IS NULL;
COMMIT;

ALTER TABLE `symphony_pointtransfer`
  MODIFY COLUMN `sourceScene` VARCHAR(64) NOT NULL DEFAULT '';
