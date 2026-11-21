CREATE TABLE IF NOT EXISTS `symphony_pet_coffee` (
    `oId` VARCHAR(19) NOT NULL,
    `snapshotJson` VARCHAR(256) NOT NULL,
    PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO `symphony_pet_coffee` (`oId`, `snapshotJson`)
VALUES ('settings', '{"enabled":true,"price":64,"version":1}')
ON DUPLICATE KEY UPDATE `oId` = `oId`;
