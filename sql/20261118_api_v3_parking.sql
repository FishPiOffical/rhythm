-- 游园币钱包迁移完成后执行。仅已领车的玩家开放一个私人车位。
CREATE TABLE IF NOT EXISTS symphony_parking_garage (
  oId VARCHAR(19) NOT NULL PRIMARY KEY,
  cars VARCHAR(256) NOT NULL,
  driving VARCHAR(19) NOT NULL DEFAULT '',
  occupant VARCHAR(19) NOT NULL DEFAULT '',
  KEY idx_parking_free (occupant,oId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS symphony_parking_session (
  oId VARCHAR(19) NOT NULL PRIMARY KEY,
  driverId VARCHAR(19) NOT NULL,
  hostId VARCHAR(19) NOT NULL,
  car VARCHAR(24) NOT NULL,
  hourlyRate INT NOT NULL,
  startedAt BIGINT NOT NULL,
  endedAt BIGINT NOT NULL DEFAULT 0,
  ending VARCHAR(12) NOT NULL DEFAULT '',
  paid INT NOT NULL DEFAULT 0,
  restartGapMs BIGINT NOT NULL,
  verified INT NOT NULL DEFAULT 0,
  KEY idx_parking_driver (driverId,startedAt),
  KEY idx_parking_start (startedAt,driverId),
  KEY idx_parking_settlement (endedAt,driverId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
