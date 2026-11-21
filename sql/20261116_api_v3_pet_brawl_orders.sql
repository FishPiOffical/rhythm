-- 订单与积分流水、武器升级在同一个事务结算。保留已完成订单用于恢复。
CREATE TABLE IF NOT EXISTS `symphony_pet_brawl_order` (
  `oId` VARCHAR(19) NOT NULL,
  `userId` VARCHAR(19) NOT NULL,
  `snapshotJson` VARCHAR(2048) NOT NULL,
  PRIMARY KEY (`oId`),
  KEY `idx_brawl_order_owner` (`userId`,`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
