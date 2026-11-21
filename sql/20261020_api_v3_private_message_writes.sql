-- Private-message mutation permission. Apply after 20261019_api_v3_private_messages.sql.
-- Replace symphony_ if the deployment uses another jdbc.tablePrefix.
START TRANSACTION;
UPDATE `symphony_api_v3_client`
SET `scope` = CONCAT(`scope`, ' messages.write')
WHERE `clientId` = 'website_v3'
  AND `scope` = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write notifications.read notifications.write activity.write settings.write uploads.write messages.read';
COMMIT;

-- Existing installations may still have a short VARCHAR; preserve nullable legacy content.
-- TEXT accommodates the existing 10240 UTF-16-unit private-message limit with utf8mb4.
ALTER TABLE `symphony_chat_info` MODIFY COLUMN `content` TEXT NULL;

SET @dm_write_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='symphony_chat_list' AND index_name='idx_chat_list_session_id'),
    'SELECT 1', 'ALTER TABLE `symphony_chat_list` ADD INDEX `idx_chat_list_session_id` (`sessionId`)');
PREPARE dm_write_index FROM @dm_write_sql;
EXECUTE dm_write_index;
DEALLOCATE PREPARE dm_write_index;
