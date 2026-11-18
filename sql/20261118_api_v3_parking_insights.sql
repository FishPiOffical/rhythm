-- 第二阶段之后执行，可重复运行。既有停车单没有副驾记录。
CREATE TABLE IF NOT EXISTS symphony_parking_passenger (
  oId VARCHAR(19) NOT NULL PRIMARY KEY,
  speciesId VARCHAR(24) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS symphony_parking_observation (
  oId VARCHAR(96) NOT NULL PRIMARY KEY,
  actorId VARCHAR(19) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  observedAt BIGINT NOT NULL,
  dayNo INT NOT NULL,
  outcome INT NOT NULL,
  commandHash VARCHAR(64) NOT NULL DEFAULT '',
  KEY idx_parking_observation_period (kind,observedAt),
  KEY idx_parking_observation_actor (actorId,kind,dayNo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
