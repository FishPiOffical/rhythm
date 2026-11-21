-- Required before deploying name-bound registration proof consumption. No new table.
-- Replace symphony_ if the deployment uses a custom jdbc.tablePrefix.
-- MySQL DDL commits implicitly. Existing SMS records keep an empty context.
SET @registration_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='symphony_verifycode' AND column_name='accountContext'),
    'SELECT 1', 'ALTER TABLE `symphony_verifycode` ADD COLUMN `accountContext` VARCHAR(512) NOT NULL DEFAULT ''''');
PREPARE registration_migration FROM @registration_sql;
EXECUTE registration_migration;
DEALLOCATE PREPARE registration_migration;
