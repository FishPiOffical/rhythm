-- 先备份令牌表，字符串列沿用目标表排序规则。旧令牌默认为空，仅在已验证网页绑定后补记。

SET @device_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='symphony_api_v3_token' AND column_name='websiteDeviceId'
), 'DO 0', 'ALTER TABLE symphony_api_v3_token ADD COLUMN websiteDeviceId VARCHAR(32) NOT NULL DEFAULT ''''');
PREPARE device_step FROM @device_ddl;
EXECUTE device_step;
DEALLOCATE PREPARE device_step;

SET @device_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='symphony_api_v3_token' AND column_name='websiteDeviceLabel'
), 'DO 0', 'ALTER TABLE symphony_api_v3_token ADD COLUMN websiteDeviceLabel VARCHAR(80) NOT NULL DEFAULT ''''');
PREPARE device_step FROM @device_ddl;
EXECUTE device_step;
DEALLOCATE PREPARE device_step;

SET @device_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name='symphony_api_v3_token' AND index_name='idx_api_v3_website_devices'
), 'DO 0', 'ALTER TABLE symphony_api_v3_token ADD INDEX idx_api_v3_website_devices (userId,clientId,tokenType,state,websiteDeviceId,expiresAt)');
PREPARE device_step FROM @device_ddl;
EXECUTE device_step;
DEALLOCATE PREPARE device_step;
