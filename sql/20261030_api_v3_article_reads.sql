-- Requires 20260123_long_article_read.sql and the long-read reliability section
-- of 20260809_profession_pending.sql. Pause old/new readers during deployment.
-- Existing first-read rows retain their original six-hour window; no old rewards
-- or counts are reissued. Compatible with tables auto-created without the old index.
-- Historical duplicate article/user/window rows must be reconciled before adding
-- the unique index; this migration deliberately does not delete any reading history.
SET @read_column_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_article_long_read_user' AND COLUMN_NAME='windowStart');
SET @read_ddl = IF(@read_column_exists=0,
  'ALTER TABLE `symphony_article_long_read_user` ADD COLUMN `windowStart` BIGINT NOT NULL DEFAULT 0 AFTER `userId`', 'SELECT 1');
PREPARE read_stmt FROM @read_ddl;
EXECUTE read_stmt;
DEALLOCATE PREPARE read_stmt;

START TRANSACTION;
UPDATE `symphony_article_long_read_user`
  SET `windowStart` = FLOOR(`firstReadAt` / 21600000) * 21600000 WHERE `windowStart`=0;
COMMIT;

SET @read_old_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_article_long_read_user' AND INDEX_NAME='uk_article_user');
SET @read_new_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_article_long_read_user' AND INDEX_NAME='uk_article_user_window');
-- One atomic ALTER when replacing the old constraint, so failed duplicate validation
-- cannot leave a canonical old installation without its original unique index.
SET @read_ddl = IF(@read_new_index=0,
  IF(@read_old_index>0,
    'ALTER TABLE `symphony_article_long_read_user` DROP INDEX `uk_article_user`, ADD UNIQUE KEY `uk_article_user_window` (`articleId`,`userId`,`windowStart`)',
    'ALTER TABLE `symphony_article_long_read_user` ADD UNIQUE KEY `uk_article_user_window` (`articleId`,`userId`,`windowStart`)'),
  IF(@read_old_index>0, 'ALTER TABLE `symphony_article_long_read_user` DROP INDEX `uk_article_user`', 'SELECT 1'));
PREPARE read_stmt FROM @read_ddl;
EXECUTE read_stmt;
DEALLOCATE PREPARE read_stmt;
