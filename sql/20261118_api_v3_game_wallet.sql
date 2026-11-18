-- 停止旧版本写入后执行；按 jdbc.tablePrefix 替换前缀。DDL 在事务外，所有换算共用一个事务。
CREATE TABLE IF NOT EXISTS symphony_game_wallet (
  oId VARCHAR(19) NOT NULL PRIMARY KEY,
  coins BIGINT NOT NULL CHECK (coins >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS symphony_game_wallet_ledger (
  oId VARCHAR(32) NOT NULL PRIMARY KEY,
  userId VARCHAR(19) NOT NULL,
  operation VARCHAR(32) NOT NULL,
  referenceId VARCHAR(64) NOT NULL,
  coinDelta BIGINT NOT NULL,
  createdAt BIGINT NOT NULL,
  KEY idx_game_wallet_owner_time (userId, createdAt, oId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS symphony_game_wallet_migration (
  version INT NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP PROCEDURE IF EXISTS symphony_migrate_game_wallet_v1;
DELIMITER //
CREATE PROCEDURE symphony_migrate_game_wallet_v1()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  INSERT IGNORE INTO symphony_game_wallet_migration VALUES (1);
  IF ROW_COUNT() = 1 THEN
    INSERT INTO symphony_game_wallet (oId, coins)
      SELECT oId, coins * 10 FROM symphony_pet_account;
    INSERT INTO symphony_game_wallet_ledger (oId, userId, operation, referenceId, coinDelta, createdAt)
      SELECT CONCAT('m',oId), oId, 'pet-migration', oId, coins * 10, UNIX_TIMESTAMP() * 1000
      FROM symphony_pet_account WHERE coins <> 0;
    -- 以领取日为准，保留额度日期和时钟回拨保护。
    UPDATE symphony_pet_income_account SET earned = earned * 10;
    UPDATE symphony_pet_ledger SET coinDelta = coinDelta * 10 WHERE coinDelta <> 0;
    UPDATE symphony_pet_journal SET eventJson = JSON_REPLACE(eventJson, '$.coinsDelta',
      CAST(JSON_UNQUOTE(JSON_EXTRACT(eventJson, '$.coinsDelta')) AS SIGNED) * 10);
    -- JSON_REPLACE 保留旧版缺省字段，已领取和未领取活动均按冻结值换算。
    UPDATE symphony_pet_activity SET snapshotJson = JSON_REPLACE(snapshotJson,
      '$.entry.cost.coins', CAST(JSON_UNQUOTE(JSON_EXTRACT(snapshotJson, '$.entry.cost.coins')) AS SIGNED) * 10,
      '$.entry.rewards.coins', CAST(JSON_UNQUOTE(JSON_EXTRACT(snapshotJson, '$.entry.rewards.coins')) AS SIGNED) * 10);
    UPDATE symphony_pet_activity_result SET actualJson = JSON_REPLACE(actualJson, '$.coins',
      CAST(JSON_UNQUOTE(JSON_EXTRACT(actualJson, '$.coins')) AS SIGNED) * 10);
  END IF;
  COMMIT;
END//
DELIMITER ;
CALL symphony_migrate_game_wallet_v1();
DROP PROCEDURE symphony_migrate_game_wallet_v1;
