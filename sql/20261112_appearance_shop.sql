-- 先执行本迁移再启动新版 Rhythm；非默认库前缀请替换 symphony_。

CREATE TABLE IF NOT EXISTS `symphony_appearance_product` (
 `oId` VARCHAR(64) NOT NULL,
 `price7` INT NOT NULL,
 `price30` INT NOT NULL,
 `enabled` INT NOT NULL,
 `revision` BIGINT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `symphony_appearance_entitlement` (
 `oId` VARCHAR(43) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `productId` VARCHAR(64) NOT NULL,
 `expiresAt` BIGINT NOT NULL,
 `equipped` INT NOT NULL,
 `updatedAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`),
 UNIQUE KEY `uk_appearance_owner_product` (`userId`,`productId`),
 KEY `idx_appearance_mount` (`userId`,`equipped`,`expiresAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `symphony_appearance_purchase` (
 `oId` VARCHAR(19) NOT NULL,
 `userId` VARCHAR(19) NOT NULL,
 `productId` VARCHAR(64) NOT NULL,
 `days` INT NOT NULL,
 `paidPoints` INT NOT NULL,
 `expiresAt` BIGINT NOT NULL,
 `transferId` VARCHAR(19) NOT NULL,
 `createdAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`),
 KEY `idx_appearance_purchase_owner` (`userId`,`createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT IGNORE INTO `symphony_appearance_product` (oId,price7,price30,enabled,revision) VALUES ('mount-paipai-scooter',1024,3584,1,1);

INSERT IGNORE INTO `symphony_appearance_product` (oId,price7,price30,enabled,revision) VALUES ('mount-paper-plane',1024,3584,1,1);

INSERT IGNORE INTO `symphony_appearance_product` (oId,price7,price30,enabled,revision) VALUES ('mount-deepsea-submarine',2048,7168,1,1);

INSERT IGNORE INTO `symphony_appearance_product` (oId,price7,price30,enabled,revision) VALUES ('bubble-sea-soda',256,896,1,1);

INSERT IGNORE INTO `symphony_appearance_product` (oId,price7,price30,enabled,revision) VALUES ('bubble-sunset-letter',256,896,1,1);

INSERT IGNORE INTO `symphony_appearance_product` (oId,price7,price30,enabled,revision) VALUES ('bubble-star-route',512,1792,1,1);

INSERT IGNORE INTO `symphony_appearance_product` (oId,price7,price30,enabled,revision) VALUES ('frame-tide',256,896,1,1);

INSERT IGNORE INTO `symphony_appearance_product` (oId,price7,price30,enabled,revision) VALUES ('frame-paipai-stamp',256,896,1,1);

INSERT IGNORE INTO `symphony_appearance_product` (oId,price7,price30,enabled,revision) VALUES ('frame-star-ring',512,1792,1,1);
