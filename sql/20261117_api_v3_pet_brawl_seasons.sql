-- 赛季按北京时间开启，重复迁移保留首次启用的起点和结算进度。
CREATE TABLE IF NOT EXISTS `symphony_pet_brawl_season` (
  oId VARCHAR(19) NOT NULL,
  startsAt BIGINT NOT NULL,
  settledThrough INT NOT NULL,
  PRIMARY KEY (oId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO `symphony_pet_brawl_season` (oId, startsAt, settledThrough)
VALUES ('1', TIMESTAMPDIFF(SECOND, '1970-01-01', DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR) - INTERVAL 8 HOUR) * 1000, 0)
ON DUPLICATE KEY UPDATE startsAt = startsAt;

CREATE TABLE IF NOT EXISTS `symphony_pet_brawl_standing` (
  oId VARCHAR(32) NOT NULL,
  season INT NOT NULL,
  userId VARCHAR(19) NOT NULL,
  rating INT NOT NULL,
  wins INT NOT NULL,
  games INT NOT NULL,
  reachedAt BIGINT NOT NULL,
  eligible INT NOT NULL,
  opponents VARCHAR(128) NOT NULL,
  PRIMARY KEY (oId),
  UNIQUE KEY uk_brawl_standing_season_user (season, userId),
  KEY idx_brawl_standing_rank (season, eligible, rating DESC, wins DESC, reachedAt, userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `symphony_pet_brawl_season_reward` (
  oId VARCHAR(32) NOT NULL,
  season INT NOT NULL,
  userId VARCHAR(19) NOT NULL,
  place INT NOT NULL,
  materials INT NOT NULL,
  claimedAt BIGINT NOT NULL,
  PRIMARY KEY (oId),
  UNIQUE KEY uk_brawl_season_reward (season, userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
