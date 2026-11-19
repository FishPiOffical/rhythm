-- 先执行游园币钱包迁移。退出后保留已购位置及当日计时。
CREATE TABLE IF NOT EXISTS symphony_partner_member (
  oId VARCHAR(19) PRIMARY KEY,
  joined INT NOT NULL,
  holder VARCHAR(19) NOT NULL,
  price INT NOT NULL,
  revision BIGINT NOT NULL,
  meters VARCHAR(4096) NOT NULL,
  KEY idx_partner_directory (joined,oId),
  KEY idx_partner_holder (holder,oId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
