-- 抢搭子第二批：只记录迁移后的成交和解约，旧钱包流水仍按实际收支读取。
CREATE TABLE IF NOT EXISTS symphony_partner_event (
  oId VARCHAR(32) NOT NULL PRIMARY KEY,
  userId VARCHAR(19) NOT NULL,
  summary VARCHAR(512) NOT NULL,
  price INT NOT NULL,
  amount INT NOT NULL,
  createdAt BIGINT NOT NULL,
  KEY idx_partner_event_user (userId,oId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
