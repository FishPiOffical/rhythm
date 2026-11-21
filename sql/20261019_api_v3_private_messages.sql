-- Private-message read scopes and participant query indexes. Apply before enabling the new routes.
-- Replace symphony_ below if the deployment uses a different jdbc.tablePrefix.
-- Index DDL commits implicitly in MySQL; the client policy update owns a separate transaction.
START TRANSACTION;
UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' messages.read')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write uploads.write';
COMMIT;

SET @dm_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_chat_info' AND index_name='idx_chat_info_from_to_id'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_info` ADD INDEX `idx_chat_info_from_to_id` (`fromId`,`toId`,`oId`)');
PREPARE dm_index FROM @dm_sql;
EXECUTE dm_index;
DEALLOCATE PREPARE dm_index;

SET @dm_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_chat_info' AND index_name='idx_chat_info_to_from_id'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_info` ADD INDEX `idx_chat_info_to_from_id` (`toId`,`fromId`,`oId`)');
PREPARE dm_index FROM @dm_sql;
EXECUTE dm_index;
DEALLOCATE PREPARE dm_index;

SET @dm_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_chat_unread' AND index_name='idx_chat_unread_to_from'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_unread` ADD INDEX `idx_chat_unread_to_from` (`toId`,`fromId`)');
PREPARE dm_index FROM @dm_sql;
EXECUTE dm_index;
DEALLOCATE PREPARE dm_index;
