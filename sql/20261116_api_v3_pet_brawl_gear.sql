-- 账号保留已购进阶与镶嵌。首批强化数据仍位于 pet_brawl_account。
CREATE TABLE IF NOT EXISTS `symphony_pet_brawl_gear` (
  `oId` VARCHAR(19) NOT NULL,
  `snapshotJson` VARCHAR(2048) NOT NULL,
  PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
