-- 在搭子第二批迁移后执行。迁移之前的流水不补造参与关系。
CREATE TABLE IF NOT EXISTS symphony_partner_rate (
  oId VARCHAR(19) PRIMARY KEY, baseRate INT NOT NULL, stepRate INT NOT NULL,
  version INT NOT NULL, sinceAt BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT IGNORE INTO symphony_partner_rate VALUES ('rate',40,2,1,UNIX_TIMESTAMP()*1000);
CREATE TABLE IF NOT EXISTS symphony_partner_trade (
  oId VARCHAR(32) PRIMARY KEY, buyer VARCHAR(19) NOT NULL, seller VARCHAR(19) NOT NULL,
  peer VARCHAR(19) NOT NULL, createdAt BIGINT NOT NULL,
  KEY idx_partner_trade_time(createdAt,peer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @partner_index_sql = IF(EXISTS (
  SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='symphony_game_wallet_ledger' AND index_name='idx_game_wallet_reference'
), 'DO 0', 'ALTER TABLE symphony_game_wallet_ledger ADD INDEX idx_game_wallet_reference (referenceId,userId)');
PREPARE partner_index FROM @partner_index_sql;
EXECUTE partner_index;
DEALLOCATE PREPARE partner_index;

SET @partner_index_sql = IF(EXISTS (
  SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='symphony_game_wallet_ledger' AND index_name='idx_game_wallet_operation_time'
), 'DO 0', 'ALTER TABLE symphony_game_wallet_ledger ADD INDEX idx_game_wallet_operation_time (operation,createdAt)');
PREPARE partner_index FROM @partner_index_sql;
EXECUTE partner_index;
DEALLOCATE PREPARE partner_index;
