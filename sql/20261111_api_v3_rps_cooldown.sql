-- 高峰期猜拳红包冷却按发送人和发送时间读取，先于更新后的 Rhythm 服务部署。
SET @packet_cooldown_ddl = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_chat_red_packet' AND INDEX_NAME='idx_packet_sender_type_time'),
  'SELECT 1', 'CREATE INDEX idx_packet_sender_type_time ON symphony_chat_red_packet (senderId, type, createdAt)');
PREPARE packet_cooldown_stmt FROM @packet_cooldown_ddl;
EXECUTE packet_cooldown_stmt;
DEALLOCATE PREPARE packet_cooldown_stmt;
