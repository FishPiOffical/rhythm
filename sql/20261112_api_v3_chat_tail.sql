-- 小尾巴设置独立存储，避免旧站整包系统设置覆盖；oId就是用户ID。
-- 按实际 jdbc.tablePrefix 调整 symphony_ 前缀；必须在新版服务启动前执行。
-- 历史消息的小尾巴快照保存在既有 chat_room.content JSON 中，无需修改消息表。
CREATE TABLE IF NOT EXISTS `symphony_user_chat_tail` (
 `oId` VARCHAR(19) NOT NULL,
 `enabled` INT NOT NULL DEFAULT 0,
 `text` VARCHAR(512) NOT NULL DEFAULT '',
 `updatedAt` BIGINT NOT NULL,
 PRIMARY KEY (`oId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
