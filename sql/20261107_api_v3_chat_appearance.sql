-- Current choice only. Per-message appearance snapshots live in the existing chat_room content JSON.
-- Adjust symphony_ when using a different jdbc.tablePrefix. Apply before starting the new server.
CREATE TABLE IF NOT EXISTS `symphony_user_chat_appearance` (
 `oId` VARCHAR(19) NOT NULL,
 `bubbleId` VARCHAR(32) NOT NULL,
 `frameId` VARCHAR(32) NOT NULL,
 `version` INT NOT NULL,
 `updatedAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
