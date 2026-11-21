-- 先执行第一阶段停车迁移。老停车单没有互动记录，按空便签、原车外观处理。
CREATE TABLE IF NOT EXISTS symphony_parking_visit (
  oId VARCHAR(19) NOT NULL PRIMARY KEY,
  note VARCHAR(16) NOT NULL DEFAULT '',
  mountStyle VARCHAR(48) NOT NULL DEFAULT '',
  pardonedAt BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS symphony_parking_catalog (
  oId VARCHAR(19) NOT NULL PRIMARY KEY,
  settings VARCHAR(4096) NOT NULL,
  version INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT IGNORE INTO symphony_parking_catalog(oId,settings,version) VALUES ('catalog','{}',1);
