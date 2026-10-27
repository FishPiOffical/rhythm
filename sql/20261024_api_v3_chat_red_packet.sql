-- Safe packets use durable allocations. Apply before enabling the new routes/legacy bridge.
CREATE TABLE IF NOT EXISTS `symphony_chat_red_packet` (
 `oId` VARCHAR(19) NOT NULL,
 `senderId` VARCHAR(19) NOT NULL,
 `type` VARCHAR(8) NOT NULL,
 `message` VARCHAR(20) NOT NULL,
 `money` INT NOT NULL,
 `count` INT NOT NULL,
 `got` INT NOT NULL,
 `principalPoints` INT NOT NULL,
 `poolPoints` INT NOT NULL,
 `taxPoints` INT NOT NULL,
 `feeRecipientId` VARCHAR(19) NOT NULL,
 `createdAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `symphony_chat_red_packet_slot` (
 `oId` VARCHAR(64) NOT NULL,
 `messageId` VARCHAR(19) NOT NULL,
 `ordinal` INT NOT NULL,
 `amount` INT NOT NULL,
 `recipientId` VARCHAR(19) NOT NULL,
 `claimUserId` VARCHAR(19) NOT NULL,
 `claimedAt` BIGINT NOT NULL,
 `transferId` VARCHAR(19) NOT NULL,
 PRIMARY KEY (`oId`),
 UNIQUE KEY `uk_chat_packet_ordinal` (`messageId`,`ordinal`),
 KEY `idx_chat_packet_claim` (`messageId`,`claimUserId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
