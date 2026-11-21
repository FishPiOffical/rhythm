-- Required before deploying message-linked private unread writes or withdrawal.
-- Replace symphony_ for a custom jdbc.tablePrefix. MySQL DDL commits implicitly.
-- Existing unread records intentionally keep an empty link; do not guess a historical message.
SET @private_withdraw_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='symphony_chat_unread' AND column_name='messageId'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_unread` ADD COLUMN `messageId` VARCHAR(19) NOT NULL DEFAULT ''''');
PREPARE private_withdraw_migration FROM @private_withdraw_sql;
EXECUTE private_withdraw_migration;
DEALLOCATE PREPARE private_withdraw_migration;

SET @private_withdraw_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='symphony_chat_unread' AND index_name='idx_chat_unread_message'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_unread` ADD INDEX `idx_chat_unread_message` (`fromId`,`toId`,`messageId`)');
PREPARE private_withdraw_index FROM @private_withdraw_sql;
EXECUTE private_withdraw_index;
DEALLOCATE PREPARE private_withdraw_index;
